import 'dart:ui' as ui;

import 'mask_operation.dart';

/// Paints the resolved [MaskOperation]s onto the captured frame, producing a
/// new redacted image.
///
/// Flutter counterpart of the native `MaskApplier`. Operations carry *logical*
/// rectangles; the applier scales them by [pixelRatio] to land on the captured
/// (physical-resolution) image. Primary masks and stabilizer duplicates use
/// slightly different greys, matching the native renderer, so the two can be
/// told apart when debugging a recording.
class MaskApplier {
  const MaskApplier();

  // Mirrors the native `MaskApplier`: a primary mask and a stabilizer
  // duplicate are drawn in slightly different greys so an overlapping/merged
  // region is visibly distinguishable when reviewing a recording.
  //   standard  = UIColor(white: 0.50) -> round(0.50 * 255) = 128 = 0x80
  //   duplicate = UIColor(white: 0.52) -> round(0.52 * 255) = 133 = 0x85
  static const ui.Color _standardMaskColor = ui.Color(0xFF808080);
  static const ui.Color _duplicateMaskColor = ui.Color(0xFF858585);
  static const double _cornerRadius = 2.0;

  /// Draws [operations] over [image] and returns the redacted image. The caller
  /// owns both [image] and the returned image and must dispose them.
  Future<ui.Image> apply(
    ui.Image image,
    List<MaskOperation> operations,
    double pixelRatio,
  ) async {
    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder);

    canvas.drawImage(image, ui.Offset.zero, ui.Paint());

    final fillPaint = ui.Paint()..color = _standardMaskColor;
    final duplicatePaint = ui.Paint()..color = _duplicateMaskColor;

    for (final operation in operations) {
      final rect = ui.Rect.fromLTWH(
        operation.rect.left * pixelRatio,
        operation.rect.top * pixelRatio,
        operation.rect.width * pixelRatio,
        operation.rect.height * pixelRatio,
      );
      final rrect = ui.RRect.fromRectXY(
        rect,
        _cornerRadius * pixelRatio,
        _cornerRadius * pixelRatio,
      );
      canvas.drawRRect(
        rrect,
        operation.kind == MaskKind.fillDuplicate ? duplicatePaint : fillPaint,
      );
    }

    final picture = recorder.endRecording();
    try {
      return await picture.toImage(image.width, image.height);
    } finally {
      picture.dispose();
    }
  }
}
