import 'dart:math' as math;

import 'mask_operation.dart';

/// Reconciles two mask collections captured around the screenshot — a "before"
/// pass (taken with the captured frame) and an "after" pass (taken one frame
/// later). When views shift between the two passes (typical while scrolling or
/// during keyboard/dialog animations) the same logical mask occupies two nearby
/// positions; we pair the "before" op with its shifted "after" counterpart so
/// [MaskApplier] can span the transition area with a convex hull instead of
/// leaving a sliver of unmasked content.
///
/// Faithful port of the native Swift `MaskStabilizer`. The reconciliation is
/// purely geometric: it reads only the [MaskOperation.effectiveFrame] of the two
/// lists, never any privacy settings or tree state.
class MaskStabilizer {
  const MaskStabilizer();

  /// Movement under this many logical pixels (on either axis) is treated as the
  /// same position; the "after" op is discarded as a duplicate of "before".
  static const double _moveTolerance = 1.0;

  /// Required slack between the observed delta and the mask's own width/height:
  /// if a mask drifted further than itself between the two passes the gap can't
  /// be safely covered, so the whole frame is dropped.
  static const double _overlapTolerance = 1.1;

  /// Returns one [MaskPair] per "before" operation, paired with its shifted
  /// "after" counterpart when the mask moved enough to expose previously-masked
  /// content (the renderer spans the gap with the convex hull of both
  /// positions). [MaskPair.after] is `null` when movement is within tolerance
  /// and the "before" mask already covers the area.
  ///
  /// Returns `null` (the caller should drop the frame) when the two passes don't
  /// line up — either the counts differ (widgets appeared/disappeared) or an op
  /// moved further than its own size, so the in-between area can't be guaranteed
  /// covered.
  List<MaskPair>? duplicateUnsimilar({
    required List<MaskOperation> operationsBefore,
    required List<MaskOperation> operationsAfter,
  }) {
    if (operationsBefore.length != operationsAfter.length) {
      return null;
    }

    final result = <MaskPair>[];
    for (var i = 0; i < operationsBefore.length; i++) {
      final before = operationsBefore[i];
      final after = operationsAfter[i];
      final beforeFrame = before.effectiveFrame;
      final afterFrame = after.effectiveFrame;

      final diffX = (beforeFrame.left - afterFrame.left).abs();
      final diffY = (beforeFrame.top - afterFrame.top).abs();

      if (math.max(diffX, diffY) <= _moveTolerance) {
        // Movement is within tolerance; "before" already covers the area.
        result.add(MaskPair(before));
        continue;
      }

      final coversX =
          diffX * _overlapTolerance < beforeFrame.width - _moveTolerance;
      final coversY =
          diffY * _overlapTolerance < beforeFrame.height - _moveTolerance;
      if (!coversX || !coversY) {
        // Moved further than its own size; the gap can't be safely covered.
        return null;
      }

      result.add(MaskPair(before, after));
    }

    return result;
  }
}
