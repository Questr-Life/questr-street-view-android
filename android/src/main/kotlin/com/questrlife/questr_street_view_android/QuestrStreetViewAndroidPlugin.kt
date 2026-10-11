package com.questrlife.questr_street_view_android

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.StreetViewPanoramaView
import com.google.android.gms.maps.model.LatLng
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

/** One native Google panorama at a time, embedded in the existing Flutter pane. */
class QuestrStreetViewAndroidPlugin : FlutterPlugin, ActivityAware,
    Application.ActivityLifecycleCallbacks {
    private var application: Application? = null
    private var activity: Activity? = null
    private var activeView: PanoramaPlatformView? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        application = binding.applicationContext.applicationContext as Application
        binding.platformViewRegistry.registerViewFactory(
            "com.questrlife/street_view",
            PanoramaFactory(binding.binaryMessenger, this)
        )
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        releaseView()
        detachActivity()
        application = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        application?.registerActivityLifecycleCallbacks(this)
    }

    override fun onDetachedFromActivityForConfigChanges() = detachActivity()
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) =
        onAttachedToActivity(binding)
    override fun onDetachedFromActivity() = detachActivity()

    private fun detachActivity() {
        application?.unregisterActivityLifecycleCallbacks(this)
        releaseView()
        activity = null
    }

    private fun releaseView() {
        activeView?.dispose()
        activeView = null
    }

    private fun createView(context: Context, id: Int, args: Any?, messenger: BinaryMessenger): PlatformView {
        // Google's Maps SDK does not support simultaneous StreetViewPanoramaView
        // instances in one Activity. The Flutter caller also unmounts inline before
        // opening its expanded pane.
        releaseView()
        val params = args as? Map<*, *>
        val latitude = (params?.get("latitude") as? Number)?.toDouble()
        val longitude = (params?.get("longitude") as? Number)?.toDouble()
        val view = PanoramaPlatformView(context, id, latitude, longitude, messenger) {
            if (activeView === it) activeView = null
        }
        activeView = view
        return view
    }

    override fun onActivityResumed(resumed: Activity) {
        if (resumed === activity) activeView?.resume()
    }

    override fun onActivityPaused(paused: Activity) {
        if (paused === activity) activeView?.pause()
    }

    override fun onActivityStarted(started: Activity) {
        if (started === activity) activeView?.startLifecycle()
    }

    override fun onActivityStopped(stopped: Activity) {
        if (stopped === activity) activeView?.stopLifecycle()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (activity === this.activity) detachActivity()
    }

    private class PanoramaFactory(
        private val messenger: BinaryMessenger,
        private val owner: QuestrStreetViewAndroidPlugin
    ) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
        override fun create(context: Context, viewId: Int, args: Any?): PlatformView =
            owner.createView(context, viewId, args, messenger)
    }

    private class PanoramaPlatformView(
        context: Context,
        id: Int,
        private val latitude: Double?,
        private val longitude: Double?,
        messenger: BinaryMessenger,
        private val onDispose: (PanoramaPlatformView) -> Unit
    ) : PlatformView, MethodChannel.MethodCallHandler {
        private val channel = MethodChannel(messenger, "com.questrlife/street_view/$id")
        private val panoramaView = StreetViewPanoramaView(context)
        private val handler = Handler(Looper.getMainLooper())
        private var disposed = false
        private var started = false
        private var resumed = false
        private var resultDelivered = false
        private val timeout = Runnable { report("provider_error") }

        init {
            channel.setMethodCallHandler(this)
            panoramaView.onCreate(null)
            startLifecycle()
            resume()
        }

        override fun getView(): View = panoramaView

        override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
            if (call.method != "start") {
                result.notImplemented()
                return
            }
            if (disposed || latitude == null || longitude == null ||
                !latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                report("provider_error")
                result.success(null)
                return
            }
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                    panoramaView.context
                ) != ConnectionResult.SUCCESS
            ) {
                report("provider_error")
                result.success(null)
                return
            }
            handler.postDelayed(timeout, 20_000)
            try {
                panoramaView.getStreetViewPanoramaAsync { panorama ->
                    if (disposed) return@getStreetViewPanoramaAsync
                    panorama.setOnStreetViewPanoramaChangeListener { location ->
                        if (disposed || resultDelivered) return@setOnStreetViewPanoramaChangeListener
                        report(if (location == null) "zero_results" else "available")
                    }
                    panorama.setPosition(LatLng(latitude, longitude), 50)
                }
            } catch (_: RuntimeException) {
                report("provider_error")
            }
            result.success(null)
        }

        private fun report(state: String) {
            if (disposed || resultDelivered) return
            resultDelivered = true
            handler.removeCallbacks(timeout)
            channel.invokeMethod("state", state)
        }

        fun startLifecycle() {
            if (!disposed && !started) {
                panoramaView.onStart()
                started = true
            }
        }

        fun stopLifecycle() {
            if (!disposed && started) {
                pause()
                panoramaView.onStop()
                started = false
            }
        }

        fun resume() {
            if (!disposed && !resumed) {
                startLifecycle()
                panoramaView.onResume()
                resumed = true
            }
        }

        fun pause() {
            if (!disposed && resumed) {
                panoramaView.onPause()
                resumed = false
            }
        }

        override fun dispose() {
            if (disposed) return
            handler.removeCallbacks(timeout)
            stopLifecycle()
            disposed = true
            channel.setMethodCallHandler(null)
            panoramaView.onDestroy()
            onDispose(this)
        }
    }
}
