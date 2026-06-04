import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/masking/mask_operation.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/masking/mask_stabilizer.dart';

void main() {
  const stabilizer = MaskStabilizer();

  MaskOperation op(double left, double top, [double size = 100]) =>
      MaskOperation(rect: Rect.fromLTWH(left, top, size, size));

  group('MaskStabilizer.duplicateUnsimilar', () {
    test('drops the frame when the two passes have different counts', () {
      final result = stabilizer.duplicateUnsimilar(
        operationsBefore: [op(0, 0)],
        operationsAfter: [op(0, 0), op(10, 10)],
      );
      expect(result, isNull);
    });

    test('returns the before list unchanged when nothing moved', () {
      final before = [op(0, 0), op(200, 200)];
      final result = stabilizer.duplicateUnsimilar(
        operationsBefore: before,
        operationsAfter: [op(0, 0), op(200, 200)],
      );
      expect(result, isNotNull);
      expect(result!.length, 2);
      expect(result.every((o) => o.kind == MaskKind.fill), isTrue);
    });

    test('movement within tolerance is treated as the same position', () {
      // 1.0px is exactly the move tolerance, so no duplicate is added.
      final result = stabilizer.duplicateUnsimilar(
        operationsBefore: [op(0, 0)],
        operationsAfter: [op(1, 1)],
      );
      expect(result, isNotNull);
      expect(result!.length, 1);
      expect(result.single.kind, MaskKind.fill);
    });

    test('a mask that moved within its own size gets a fillDuplicate', () {
      final result = stabilizer.duplicateUnsimilar(
        operationsBefore: [op(0, 0)],
        operationsAfter: [op(20, 5)],
      );
      expect(result, isNotNull);
      expect(result!.length, 2);
      expect(result[0].kind, MaskKind.fill);
      expect(result[1].kind, MaskKind.fillDuplicate);
      // The duplicate covers the "after" position so the gap is masked.
      expect(result[1].rect, const Rect.fromLTWH(20, 5, 100, 100));
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

    test('only the ops that drifted get duplicated', () {
      final result = stabilizer.duplicateUnsimilar(
        operationsBefore: [op(0, 0), op(300, 300)],
        operationsAfter: [op(0, 0), op(320, 300)],
      );
      expect(result, isNotNull);
      expect(result!.length, 3);
      expect(result.where((o) => o.kind == MaskKind.fillDuplicate).length, 1);
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
