import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';

/// Four corners of a (possibly rotated / skewed / scaled) masked region, in the
/// capture boundary's *logical* coordinate space.
///
/// Mirrors the native Swift `Quad`. Corners are stored in the local winding
/// order top-left -> top-right -> bottom-right -> bottom-left so the polygon can
/// be stroked directly, and so the convex-hull merge in [MaskApplier] can treat
/// two quads as a single 8-point cloud.
class Quad {
  const Quad(this.p0, this.p1, this.p2, this.p3);

  /// Top-left corner of the source rect, transformed into boundary coords.
  final ui.Offset p0;

  /// Top-right corner of the source rect, transformed into boundary coords.
  final ui.Offset p1;

  /// Bottom-right corner of the source rect, transformed into boundary coords.
  final ui.Offset p2;

  /// Bottom-left corner of the source rect, transformed into boundary coords.
  final ui.Offset p3;

  List<ui.Offset> get points => <ui.Offset>[p0, p1, p2, p3];

  /// Axis-aligned bounding box enclosing all four corners.
  ui.Rect get boundingBox {
    final minX = [p0.dx, p1.dx, p2.dx, p3.dx].reduce(_min);
    final maxX = [p0.dx, p1.dx, p2.dx, p3.dx].reduce(_max);
    final minY = [p0.dy, p1.dy, p2.dy, p3.dy].reduce(_min);
    final maxY = [p0.dy, p1.dy, p2.dy, p3.dy].reduce(_max);
    return ui.Rect.fromLTRB(minX, minY, maxX, maxY);
  }

  static double _min(double a, double b) => a < b ? a : b;
  static double _max(double a, double b) => a > b ? a : b;
}

/// A single region to redact, expressed as a transform applied to a local
/// [localRect].
///
/// This is the Flutter counterpart of the native `MaskOperation`, which stores
/// either `Mask.affine(rect:transform:)` or `Mask.quad(_:)`. Keeping the
/// transform (rather than a baked axis-aligned rectangle) is what lets masks
/// track *animating* widgets: a widget that is rotating, scaling, sliding, or
/// tilting in 3D is captured as the quadrilateral it actually occupies on screen
/// this frame, instead of a stale upright box.
///
/// The transform may include a perspective component (a 3D flip/tilt). [quad] is
/// always the perspective-correct projection of [localRect]'s corners;
/// [isAffine] reports whether the projection is a plain parallelogram so
/// [MaskApplier] can pick the matching native draw path (rounded rect vs. raw
/// quad polygon).
///
/// Geometry is kept in *logical* pixels (device-independent), matching the
/// native implementation's points, so the movement tolerances in
/// [MaskStabilizer] stay unit-compatible. [MaskApplier] scales to physical
/// pixels when it paints onto the captured image.
class MaskOperation {
  MaskOperation({required this.localRect, required this.transform})
    : quad = _quadFrom(localRect, transform),
      effectiveFrame = _quadFrom(localRect, transform).boundingBox;

  /// The masked widget's bounds in its own (untransformed) coordinate space,
  /// i.e. `Rect.fromLTWH(0, 0, width, height)`. Mirrors the native `lBounds`.
  final ui.Rect localRect;

  /// Maps [localRect] into the capture boundary's logical coordinates. Encodes
  /// translation, scale, rotation, skew and perspective accumulated from every
  /// ancestor.
  final Matrix4 transform;

  /// The four transformed corners of [localRect], perspective-corrected; used
  /// for painting and for the convex-hull merge when a mask drifts between the
  /// before/after passes.
  final Quad quad;

  /// Axis-aligned bounding box of [quad], in boundary logical coords. Used by
  /// [MaskStabilizer] for movement detection (mirrors the native
  /// `effectiveFrame`).
  final ui.Rect effectiveFrame;

  /// Whether [transform] has no perspective component, so [localRect] projects
  /// to a parallelogram. This is the Flutter analogue of the native
  /// `CATransform3DIsAffine` check that picks between `Mask.affine` and
  /// `Mask.quad`. The tested entries match the ones [MatrixUtils.transformPoint]
  /// uses to compute the homogeneous `w` divisor.
  bool get isAffine =>
      transform.storage[3] == 0.0 &&
      transform.storage[7] == 0.0 &&
      transform.storage[15] == 1.0;

  static Quad _quadFrom(ui.Rect rect, Matrix4 transform) => Quad(
    MatrixUtils.transformPoint(transform, rect.topLeft),
    MatrixUtils.transformPoint(transform, rect.topRight),
    MatrixUtils.transformPoint(transform, rect.bottomRight),
    MatrixUtils.transformPoint(transform, rect.bottomLeft),
  );
}

/// A "before" mask paired with its shifted "after" counterpart.
///
/// Mirrors the native `(MaskOperation, MaskOperation?)` tuple produced by
/// `MaskStabilizer.duplicateUnsimilar`. [after] is non-null only when the mask
/// moved far enough between the two collection passes that the gap must be
/// bridged — [MaskApplier] then fills the convex hull spanning both positions.
class MaskPair {
  const MaskPair(this.before, [this.after]);

  final MaskOperation before;
  final MaskOperation? after;
}
