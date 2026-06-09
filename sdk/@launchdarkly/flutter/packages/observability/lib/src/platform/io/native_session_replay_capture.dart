import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'masking/mask_applier.dart';
import 'masking/mask_collector.dart';
import 'masking/mask_stabilizer.dart';
import 'masking/masking_policy.dart';
import 'masking/widget_masking_config.dart';

/// Provides Flutter-rendered screenshots to the native session replay SDK.
///
/// Wrap the app, or the subtree that should appear in replay, with this widget.
/// The native iOS and Android capture services call back into it over a method
/// channel and receive raw RGBA bytes that they wrap into their platform
/// RawFrame types (the native pipeline re-encodes/compresses from there, so we
/// avoid a redundant PNG round trip on the wire).
///
/// Masking mirrors the native pipeline and is split into focused collaborators:
///   - [MaskingPolicy] — per-widget rule decisions.
///   - [MaskCollector] — walks the element tree into resolved fill rectangles.
///   - [MaskStabilizer] — reconciles a "before"/"after" pass to cover masks that
///     drift mid-capture (and drops frames it can't safely cover).
///   - [MaskApplier] — paints the rectangles onto the captured frame.
class NativeSessionReplayCapture extends StatefulWidget {
  final Widget child;

  const NativeSessionReplayCapture({super.key, required this.child});

  @override
  State<NativeSessionReplayCapture> createState() =>
      _NativeSessionReplayCaptureState();
}

class _NativeSessionReplayCaptureState
    extends State<NativeSessionReplayCapture> {
  static const MethodChannel _channel = MethodChannel(
    'launchdarkly_flutter_session_replay/capture',
  );
  static _NativeSessionReplayCaptureState? _activeState;

  final GlobalKey _boundaryKey = GlobalKey();

  final MaskStabilizer _stabilizer = const MaskStabilizer();
  final MaskApplier _applier = const MaskApplier();

  @override
  void initState() {
    super.initState();
    _activeState = this;
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  @override
  void dispose() {
    if (_activeState == this) {
      _activeState = null;
      _channel.setMethodCallHandler(null);
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(key: _boundaryKey, child: widget.child);
  }

  static Future<Object?> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'captureFrame':
        return _activeState?._captureFrame(call.arguments);
      default:
        throw PlatformException(
          code: 'unimplemented',
          message: 'Unknown session replay capture method: ${call.method}',
        );
    }
  }

  Future<Map<String, Object?>?> _captureFrame(Object? arguments) async {
    final context = _boundaryKey.currentContext;
    if (context == null || !mounted) {
      return null;
    }

    final renderObject = context.findRenderObject();
    if (renderObject is! RenderRepaintBoundary) {
      return null;
    }

    final pixelRatio = View.of(context).devicePixelRatio;
    final args = arguments is Map ? arguments : const <Object?, Object?>{};
    final minimumAlphaArg = args['minimumAlpha'];
    final collector = MaskCollector(
      MaskingPolicy(
        maskTextInputs: args['maskTextInputs'] == true,
        maskLabels: args['maskLabels'] == true,
        maskImages: args['maskImages'] == true,
        maskWebViews: args['maskWebViews'] == true,
        minimumAlpha: minimumAlphaArg is num
            ? minimumAlphaArg.toDouble()
            : 0.02,
        widgetConfig: activeWidgetMaskingConfig,
      ),
    );

    // "Before" pass: mask geometry and the screenshot are sampled in the same
    // synchronous slice, so they share one frame's layout (no coordinate drift
    // between the rectangles and the captured pixels). The timestamp is taken
    // here, at capture time, rather than after encoding — so the frame lands on
    // the replay timeline where its content actually was.
    final before = collector.collect(context, renderObject);
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final orientation = _orientationForView(context);
    final image = await renderObject.toImage(pixelRatio: pixelRatio);

    // "After" pass: let one frame elapse, then re-measure against a freshly
    // resolved boundary. This is also the safety net for content that appears
    // during the async screenshot — e.g. an instantly-shown dialog. Such a mask
    // is absent from `before` but present in `after`, so the counts differ and
    // the stabilizer drops the frame rather than letting it leak unmasked. It is
    // therefore NOT safe to skip this pass when `before` is empty.
    await _waitForNextFrame();
    final afterContext = _boundaryKey.currentContext;
    if (!mounted || afterContext == null || !afterContext.mounted) {
      image.dispose();
      return null;
    }
    final afterRenderObject = afterContext.findRenderObject();
    if (afterRenderObject is! RenderRepaintBoundary) {
      image.dispose();
      return null;
    }
    final after = collector.collect(afterContext, afterRenderObject);

    final pairs = _stabilizer.duplicateUnsimilar(
      operationsBefore: before,
      operationsAfter: after,
    );
    if (pairs == null) {
      // Masks drifted or appeared/disappeared between the passes — drop the
      // frame rather than risk exposing content the screenshot may contain.
      image.dispose();
      return null;
    }

    final maskedImage = pairs.isEmpty
        ? image
        : await _applier.apply(image, pairs, pixelRatio);

    final byteData = await maskedImage.toByteData(
      format: ui.ImageByteFormat.rawRgba,
    );
    final width = maskedImage.width;
    final height = maskedImage.height;

    if (maskedImage != image) {
      maskedImage.dispose();
    }
    image.dispose();

    final bytes = byteData?.buffer.asUint8List();
    if (bytes == null) {
      return null;
    }

    return <String, Object?>{
      'bytes': Uint8List.fromList(bytes),
      'width': width,
      'height': height,
      'timestamp': timestamp,
      'orientation': orientation,
    };
  }

  /// Completes after the next frame has been built, laid out and painted, so a
  /// second mask-collection pass observes up-to-date geometry.
  Future<void> _waitForNextFrame() {
    final binding = WidgetsBinding.instance;
    final completer = Completer<void>();
    binding.addPostFrameCallback((_) {
      if (!completer.isCompleted) {
        completer.complete();
      }
    });
    binding.scheduleFrame();
    return completer.future;
  }

  int _orientationForView(BuildContext context) {
    final size = View.of(context).physicalSize;
    return size.width > size.height ? 1 : 0;
  }
}
