import 'dart:ui' as ui;

/// How a [MaskOperation] should be painted over the captured frame.
///
/// Mirrors the native Swift `MaskOperation.Kind`:
/// - [fill] is a mask collected for the captured ("before") frame.
/// - [fillDuplicate] is an extra copy added by [MaskStabilizer] to cover the
///   area a mask drifted into between the two collection passes, so a sliver
///   of sensitive content can't leak during scrolling / animation.
enum MaskKind { fill, fillDuplicate }

/// A single rectangle to redact, expressed in the capture boundary's *logical*
/// coordinate space (device-independent pixels).
///
/// Geometry is kept logical (not multiplied by the device pixel ratio) so the
/// movement tolerances in [MaskStabilizer] stay in the same units as the native
/// implementation's points. [MaskApplier] scales to physical pixels when it
/// paints onto the captured image.
class MaskOperation {
  const MaskOperation({required this.rect, this.kind = MaskKind.fill});

  /// Bounds of the masked region in the boundary's logical coordinates.
  final ui.Rect rect;

  /// Whether this is a primary mask or a stabilizer-added duplicate.
  final MaskKind kind;

  MaskOperation copyWith({MaskKind? kind}) =>
      MaskOperation(rect: rect, kind: kind ?? this.kind);
}
