
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// One embedded Android Maps SDK panorama at an authoritative virtual-route
/// coordinate. The caller owns request-token and Quest-identity validation.
class QuestrStreetViewAndroid extends StatefulWidget {
  const QuestrStreetViewAndroid({
    super.key,
    required this.requestToken,
    required this.latitude,
    required this.longitude,
    required this.onState,
  });

  final String requestToken;
  final double latitude;
  final double longitude;
  final ValueChanged<String> onState;

  @override
  State<QuestrStreetViewAndroid> createState() => _QuestrStreetViewAndroidState();
}

class _QuestrStreetViewAndroidState extends State<QuestrStreetViewAndroid> {
  MethodChannel? _channel;

  Future<void> _created(int id) async {
    if (!mounted) return;
    final channel = MethodChannel('com.questrlife/street_view/$id');
    _channel = channel;
    channel.setMethodCallHandler((call) async {
      if (mounted && call.method == 'state') {
        final state = call.arguments;
        if (state is String) widget.onState(state);
      }
    });
    try {
      await channel.invokeMethod<void>('start');
    } on PlatformException {
      if (mounted) widget.onState('provider_error');
    } on MissingPluginException {
      if (mounted) widget.onState('provider_error');
    }
  }

  @override
  void dispose() {
    _channel?.setMethodCallHandler(null);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AndroidView(
        viewType: 'com.questrlife/street_view',
        creationParams: <String, double>{
          'latitude': widget.latitude,
          'longitude': widget.longitude,
        },
        creationParamsCodec: const StandardMessageCodec(),
        onPlatformViewCreated: _created,
      );
}
