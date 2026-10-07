import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/masking/mask_operation.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/masking/mask_stabilizer.dart';

void main() {
  const stabilizer = MaskStabilizer();

  // An axis-aligned mask at (left, top): local bounds translated into place, so
  // its effectiveFrame is Rect.fromLTWH(left, top, size, size).
  MaskOperation op(double left, double top, [double size = 100]) =>
      MaskOperation(
        localRect: Rect.fromLTWH(0, 0, size, size),
        transform: Matrix4.translationValues(left, top, 0),
      );

  group('MaskStabilizer.duplicateUnsimilar', () {
    test('drops the frame when the two passes have different counts', () {
      final result = stabilizer.duplicateUnsimilar(
        operationsBefore: [op(0, 0)],
        operationsAfter: [op(0, 0), op(10, 10)],
      );
      expect(result, isNull);
    });

    test('pairs every op with a null "after" when nothing moved', () {
      final result = stabilizer.duplicateUnsimilar(
        operationsBefore: [op(0, 0), op(200, 200)],
        operationsAfter: [op(0, 0), op(200, 200)],
      );
      expect(result, isNotNull);
      expect(result!.length, 2);
      expect(result.every((pair) => pair.after == null), isTrue);
    });

    test('movement within tolerance is treated as the same position', () {
      // 1.0px is exactly the move tolerance, so no "after" is paired.
      final result = stabilizer.duplicateUnsimilar(
        operationsBefore: [op(0, 0)],
        operationsAfter: [op(1, 1)],
      );
      expect(result, isNotNull);
      expect(result!.length, 1);
      expect(result.single.after, isNull);
    });

    test('a mask that moved within its own size pairs before with after', () {
      final result = stabilizer.duplicateUnsimilar(
        operationsBefore: [op(0, 0)],
        operationsAfter: [op(20, 5)],
      );
      expect(result, isNotNull);
      expect(result!.length, 1);
      final pair = result.single;
      expect(pair.after, isNotNull);
      // The "after" position is preserved so the hull can span the gap.
      expect(pair.after!.effectiveFrame, const Rect.fromLTWH(20, 5, 100, 100));
    });

    test('drops the frame when a mask moved further than its own size', () {
      // 100px-wide mask shifted by 120px: the in-between strip can't be
      // safely covered, so the whole frame is dropped.
      final result = stabilizer.duplicateUnsimilar(
        operationsBefore: [op(0, 0)],
        operationsAfter: [op(120, 0)],
      );
      expect(result, isNull);
    });

    test('only the ops that drifted get an "after"', () {
      final result = stabilizer.duplicateUnsimilar(
        operationsBefore: [op(0, 0), op(300, 300)],
        operationsAfter: [op(0, 0), op(320, 300)],
      );
      expect(result, isNotNull);
      expect(result!.length, 2);
      expect(result.where((pair) => pair.after != null).length, 1);
    });

    test('empty input yields an empty (non-null) result', () {
      final result = stabilizer.duplicateUnsimilar(
        operationsBefore: const [],
        operationsAfter: const [],
      );
      expect(result, isNotNull);
      expect(result, isEmpty);
    });
  });
}
