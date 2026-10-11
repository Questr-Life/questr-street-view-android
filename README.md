# Questr Street View Android

An Android-only Flutter platform view for embedding Google Maps SDK for Android
Street View in Questr Life's existing Virtual Route pane. It renders a panorama
at a caller-provided virtual-route coordinate and reports `available`,
`zero_results`, or `provider_error` to the Dart caller.

This repository contains no Google key, APK, customer data, or location records.
The host app supplies its own Android-restricted Maps SDK key. Dynamic Street
View usage is billed by Google Maps Platform. Only one panorama view is active
in an Android Activity at a time.

The FlutterFlow app should pin this dependency to an exact Git commit. The
current build contract is Flutter 3.47.6, Android Gradle Plugin 8.11.1,
Kotlin 2.3.10, compile SDK 36, and minimum API 26. The plugin uses the
already-present `play-services-maps:20.0.0` artifact.

Native visual acceptance requires a covered virtual coordinate, a genuine
no-coverage coordinate, provider failure handling, full-screen return, and
preservation of the Web implementation. A successful build alone is not
acceptance.

