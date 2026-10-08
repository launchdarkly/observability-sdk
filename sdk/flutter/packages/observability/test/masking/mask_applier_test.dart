import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/masking/mask_applier.dart';

void main() {
  group('convexHull8', () {
    test('returns the input unchanged for fewer than four points', () {
      const points = [Offset.zero, Offset(10, 0), Offset(0, 10)];
      expect(convexHull8(points), points);
    });

    test('drops a point that sits inside the hull', () {
      const points = [
        Offset(0, 0),
        Offset(10, 0),
        Offset(10, 10),
        Offset(0, 10),
        Offset(5, 5), // interior point
      ];
      final hull = convexHull8(points);
      expect(hull, isNot(contains(const Offset(5, 5))));
      expect(hull.toSet(), {
        const Offset(0, 0),
        const Offset(10, 0),
        const Offset(10, 10),
        const Offset(0, 10),
      });
    });

    test('spans two offset squares (the before/after mask case)', () {
      // A 10x10 mask that slid right by 20px: its before+after corners.
      const before = [
        Offset(0, 0),
        Offset(10, 0),
        Offset(10, 10),
        Offset(0, 10),
      ];
      const after = [
        Offset(20, 0),
        Offset(30, 0),
        Offset(30, 10),
        Offset(20, 10),
      ];
      final hull = convexHull8([...before, ...after]);

      // The outer corners of the union are all on the hull; the inner edges
      // (x == 10 and x == 20) are absorbed.
      expect(hull, contains(const Offset(0, 0)));
      expect(hull, contains(const Offset(30, 0)));
      expect(hull, contains(const Offset(30, 10)));
      expect(hull, contains(const Offset(0, 10)));
      expect(hull, isNot(contains(const Offset(10, 0))));
      expect(hull, isNot(contains(const Offset(20, 10))));

      // The hull is a closed polygon covering the full swept strip.
      final xs = hull.map((p) => p.dx);
      final ys = hull.map((p) => p.dy);
      expect(xs.reduce((a, b) => a < b ? a : b), 0);
      expect(xs.reduce((a, b) => a > b ? a : b), 30);
      expect(ys.reduce((a, b) => a < b ? a : b), 0);
      expect(ys.reduce((a, b) => a > b ? a : b), 10);
    });
  });
}
