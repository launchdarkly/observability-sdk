import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../../masking.dart';

/// Provides Flutter-rendered screenshots to the native session replay SDK.
///
/// Wrap the app, or the subtree that should appear in replay, with this widget.
/// The native iOS and Android capture services call back into it over a method
/// channel and receive PNG bytes that they convert into their platform RawFrame
/// types.
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
    final maskTextInputs =
        arguments is Map && arguments['maskTextInputs'] == true;
    final regions = _collectMaskRegions(
      context,
      renderObject,
      pixelRatio,
      maskTextInputs: maskTextInputs,
    );
    final orientation = _orientationForView(context);

    final image = await renderObject.toImage(pixelRatio: pixelRatio);
    final maskedImage = regions.isEmpty
        ? image
        : await _drawMasks(
            image,
            regions.globalMask,
            regions.unmask,
            regions.explicitMask,
          );
    final byteData = await maskedImage.toByteData(
      format: ui.ImageByteFormat.png,
    );

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
      'timestamp': DateTime.now().millisecondsSinceEpoch,
      'orientation': orientation,
    };
  }

  /// Walks the element tree once, collecting three kinds of region:
  ///
  /// - `explicitMask`: every [LDMask] subtree. Always redacted — highest
  ///   priority.
  /// - `globalMask`: every [EditableText] when screen-wide `maskTextInputs`
  ///   is enabled. Redacted unless revealed by an [LDUnmask].
  /// - `unmask`: every [LDUnmask] subtree. Reveals `globalMask` regions only;
  ///   it never overrides an explicit [LDMask].
  _MaskRegions _collectMaskRegions(
    BuildContext rootContext,
    RenderRepaintBoundary boundary,
    double pixelRatio, {
    required bool maskTextInputs,
  }) {
    final explicitMaskRects = <ui.Rect>[];
    final globalMaskRects = <ui.Rect>[];
    final unmaskRects = <ui.Rect>[];

    ui.Rect? rectFor(Element element) {
      final renderObject = element.findRenderObject();
      if (renderObject is! RenderBox || !renderObject.attached) {
        return null;
      }
      final topLeft = renderObject.localToGlobal(
        ui.Offset.zero,
        ancestor: boundary,
      );
      return ui.Rect.fromLTWH(
        topLeft.dx * pixelRatio,
        topLeft.dy * pixelRatio,
        renderObject.size.width * pixelRatio,
        renderObject.size.height * pixelRatio,
      );
    }

    void visit(Element element) {
      final widget = element.widget;
      if (widget is LDMask) {
        final rect = rectFor(element);
        if (rect != null) explicitMaskRects.add(rect);
      } else if (widget is LDUnmask) {
        final rect = rectFor(element);
        if (rect != null) unmaskRects.add(rect);
      } else if (maskTextInputs && widget is EditableText) {
        final rect = rectFor(element);
        if (rect != null) globalMaskRects.add(rect);
      }
      element.visitChildren(visit);
    }

    rootContext.visitChildElements(visit);
    return _MaskRegions(
      explicitMask: explicitMaskRects,
      globalMask: globalMaskRects,
      unmask: unmaskRects,
    );
  }

  Future<ui.Image> _drawMasks(
    ui.Image image,
    List<ui.Rect> globalMaskRects,
    List<ui.Rect> unmaskRects,
    List<ui.Rect> explicitMaskRects,
  ) async {
    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder);
    final maskPaint = ui.Paint()..color = const ui.Color(0xFF808080);

    canvas.drawImage(image, ui.Offset.zero, ui.Paint());

    // 1. Global (screen-wide) masking, e.g. maskTextInputs.
    for (final rect in globalMaskRects) {
      canvas.drawRect(rect, maskPaint);
    }

    // 2. LDUnmask reveals the original pixels — but only over global masking,
    //    which is why it is applied before the explicit masks below.
    for (final rect in unmaskRects) {
      canvas.save();
      canvas.clipRect(rect);
      canvas.drawImage(image, ui.Offset.zero, ui.Paint());
      canvas.restore();
    }

    // 3. Explicit LDMask is painted last so it always wins — an LDUnmask
    //    nested inside an LDMask cannot reveal it.
    for (final rect in explicitMaskRects) {
      canvas.drawRect(rect, maskPaint);
    }

    final picture = recorder.endRecording();
    final maskedImage = await picture.toImage(image.width, image.height);
    picture.dispose();
    return maskedImage;
  }

  int _orientationForView(BuildContext context) {
    final size = View.of(context).physicalSize;
    return size.width > size.height ? 1 : 0;
  }
}

/// Regions collected from one capture pass.
///
/// [explicitMask] (LDMask) always wins; [globalMask] (screen-wide rules like
/// maskTextInputs) is redacted unless an [unmask] (LDUnmask) reveals it.
class _MaskRegions {
  const _MaskRegions({
    required this.explicitMask,
    required this.globalMask,
    required this.unmask,
  });

  final List<ui.Rect> explicitMask;
  final List<ui.Rect> globalMask;
  final List<ui.Rect> unmask;

  bool get isEmpty =>
      explicitMask.isEmpty && globalMask.isEmpty && unmask.isEmpty;
}
