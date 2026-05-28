import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

/// Provides Flutter-rendered screenshots to the native session replay SDK.
///
/// Wrap the app, or the subtree that should appear in replay, with this widget.
/// The native iOS and Android capture services call back into it over a method
/// channel and receive PNG bytes that they convert into their platform RawFrame
/// types.
class SessionReplayCapture extends StatefulWidget {
  final Widget child;

  const SessionReplayCapture({super.key, required this.child});

  @override
  State<SessionReplayCapture> createState() => _SessionReplayCaptureState();
}

class _SessionReplayCaptureState extends State<SessionReplayCapture> {
  static const MethodChannel _channel = MethodChannel(
    'launchdarkly_flutter_session_replay/capture',
  );
  static _SessionReplayCaptureState? _activeState;

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
    final maskRects = maskTextInputs
        ? _collectTextInputRects(context, renderObject, pixelRatio)
        : const <ui.Rect>[];
    final orientation = _orientationForView(context);

    final image = await renderObject.toImage(pixelRatio: pixelRatio);
    final maskedImage = maskRects.isEmpty
        ? image
        : await _drawMasks(image, maskRects);
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

  List<ui.Rect> _collectTextInputRects(
    BuildContext rootContext,
    RenderRepaintBoundary boundary,
    double pixelRatio,
  ) {
    final rects = <ui.Rect>[];

    void visit(Element element) {
      if (element.widget is EditableText) {
        final renderObject = element.findRenderObject();
        if (renderObject is RenderBox && renderObject.attached) {
          final topLeft = renderObject.localToGlobal(
            ui.Offset.zero,
            ancestor: boundary,
          );
          rects.add(
            ui.Rect.fromLTWH(
              topLeft.dx * pixelRatio,
              topLeft.dy * pixelRatio,
              renderObject.size.width * pixelRatio,
              renderObject.size.height * pixelRatio,
            ),
          );
        }
      }
      element.visitChildren(visit);
    }

    rootContext.visitChildElements(visit);
    return rects;
  }

  Future<ui.Image> _drawMasks(ui.Image image, List<ui.Rect> maskRects) async {
    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder);
    final paint = ui.Paint()..color = const ui.Color(0xFF808080);

    canvas.drawImage(image, ui.Offset.zero, ui.Paint());
    for (final rect in maskRects) {
      canvas.drawRect(rect, paint);
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
