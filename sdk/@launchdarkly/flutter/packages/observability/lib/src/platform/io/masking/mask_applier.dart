import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';

import 'mask_operation.dart';

/// Paints the resolved [MaskPair]s onto the captured frame, producing a new
/// redacted image.
///
/// Flutter counterpart of the native `MaskApplier`. For each pair:
///   - when the mask drifted between the before/after passes ([MaskPair.after]
///     is non-null), the convex hull spanning *both* positions is filled in the
///     standard grey, so the strip the mask swept across is fully covered; and
///   - the precise "before" quad is always drawn on top in a slightly different
///     grey, mirroring the native renderer so a merged/animated region is
///     visibly distinguishable when reviewing a recording.
///
/// Operations carry *logical* geometry; the applier scales by [pixelRatio] to
/// land on the captured (physical-resolution) image.
class MaskApplier {
  const MaskApplier();

  // Mirrors the native `MaskApplier`:
  //   standard  = UIColor(white: 0.50) -> round(0.50 * 255) = 128 = 0x80
  //   duplicate = UIColor(white: 0.52) -> round(0.52 * 255) = 133 = 0x85
  static const ui.Color _standardMaskColor = ui.Color(0xFF808080);
  static const ui.Color _duplicateMaskColor = ui.Color(0xFF858585);
  static const double _cornerRadius = 2.0;

  /// Draws [pairs] over [image] and returns the redacted image. The caller owns
  /// both [image] and the returned image and must dispose them.
  Future<ui.Image> apply(
    ui.Image image,
    List<MaskPair> pairs,
    double pixelRatio,
  ) async {
    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder);

    canvas.drawImage(image, ui.Offset.zero, ui.Paint());

    final fillPaint = ui.Paint()..color = _standardMaskColor;
    final duplicatePaint = ui.Paint()..color = _duplicateMaskColor;

    // Scales logical geometry into the captured image's physical pixels.
    final scale = Matrix4.diagonal3Values(pixelRatio, pixelRatio, 1);

    for (final pair in pairs) {
      final after = pair.after;
      if (after != null) {
        // Merge the two transformed positions via their convex hull so the
        // area the mask travelled across between the passes is covered.
        _drawHull(canvas, pair.before.quad, after.quad, pixelRatio, fillPaint);
      }
      // Always stamp the precise "before" position on top. An affine mask is a
      // rounded rect transformed into place (matching the native `drawRect`); a
      // perspective-projected (3D) mask is drawn as its raw quad (matching the
      // native `drawQuad`).
      if (pair.before.isAffine) {
        _drawRect(canvas, pair.before, scale, duplicatePaint);
      } else {
        _drawQuad(canvas, pair.before.quad, pixelRatio, duplicatePaint);
      }
    }

    final picture = recorder.endRecording();
    try {
      return await picture.toImage(image.width, image.height);
    } finally {
      picture.dispose();
    }
  }

  /// Fills the rounded-rect mask transformed into physical pixels. The corners
  /// are rounded in the mask's *local* space (radius [_cornerRadius]) before the
  /// affine transform is applied, matching the native `drawRect`.
  void _drawRect(
    ui.Canvas canvas,
    MaskOperation operation,
    Matrix4 scale,
    ui.Paint paint,
  ) {
    final rrect = ui.RRect.fromRectXY(
      operation.localRect,
      _cornerRadius,
      _cornerRadius,
    );
    final localPath = ui.Path()..addRRect(rrect);
    final matrix = scale.multiplied(operation.transform);
    canvas.drawPath(localPath.transform(matrix.storage), paint);
  }

  /// Fills the quad polygon, scaled into physical pixels. Used for masks whose
  /// transform has a perspective component, so the projection isn't a
  /// parallelogram a single rounded rect could represent. Mirrors the native
  /// `drawQuad`.
  void _drawQuad(
    ui.Canvas canvas,
    Quad quad,
    double pixelRatio,
    ui.Paint paint,
  ) {
    final points = quad.points;
    final path = ui.Path()
      ..moveTo(points[0].dx * pixelRatio, points[0].dy * pixelRatio);
    for (var i = 1; i < points.length; i++) {
      path.lineTo(points[i].dx * pixelRatio, points[i].dy * pixelRatio);
    }
    path.close();
    canvas.drawPath(path, paint);
  }

  /// Fills the convex hull of the two quads (8 corner points), scaled into
  /// physical pixels. Mirrors the native `drawHull`.
  void _drawHull(
    ui.Canvas canvas,
    Quad quad1,
    Quad quad2,
    double pixelRatio,
    ui.Paint paint,
  ) {
    final hull = convexHull8(<ui.Offset>[...quad1.points, ...quad2.points]);
    if (hull.length < 3) {
      return;
    }
    final path = ui.Path()
      ..moveTo(hull[0].dx * pixelRatio, hull[0].dy * pixelRatio);
    for (var i = 1; i < hull.length; i++) {
      path.lineTo(hull[i].dx * pixelRatio, hull[i].dy * pixelRatio);
    }
    path.close();
    canvas.drawPath(path, paint);
  }
}

/// Z-component of the cross product of `OA` x `OB`. Negative when `O -> A -> B`
/// makes a clockwise turn in Flutter's y-down coordinate space.
double _cross(ui.Offset o, ui.Offset a, ui.Offset b) =>
    (a.dx - o.dx) * (b.dy - o.dy) - (a.dy - o.dy) * (b.dx - o.dx);

/// Convex hull of a small point cloud (~8 points) via a gift-wrapping search,
/// matching the native `convexHull8`. Returns the input unchanged for fewer than
/// four points.
///
/// Used to wrap the eight corners of a before/after mask pair into a single
/// polygon that covers the strip the mask travelled across.
List<ui.Offset> convexHull8(List<ui.Offset> points) {
  if (points.length < 4) {
    return points;
  }

  final hull = <ui.Offset>[];

  var startPoint = points[0];
  for (final point in points) {
    if (point.dx < startPoint.dx) {
      startPoint = point;
    }
  }

  var currentPoint = startPoint;
  do {
    hull.add(currentPoint);
    var nextPoint = points[0];

    for (final candidate in points) {
      if (nextPoint == currentPoint) {
        nextPoint = candidate;
        continue;
      }
      if (_cross(currentPoint, candidate, nextPoint) < 0) {
        nextPoint = candidate;
      }
    }
    currentPoint = nextPoint;

    // Safety valve against a non-terminating wrap from degenerate / coincident
    // points (the native version relies on exact CGPoint equality; floating
    // point input could otherwise loop).
    if (hull.length > points.length) {
      break;
    }
  } while (currentPoint != startPoint);

  return hull;
}
