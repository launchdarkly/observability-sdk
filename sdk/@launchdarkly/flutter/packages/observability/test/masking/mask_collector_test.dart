import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/masking/mask_collector.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/masking/mask_operation.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/masking/masking_policy.dart';

void main() {
  Future<List<MaskOperation>> collect(
    WidgetTester tester,
    Widget subtree, {
    required bool maskTextInputs,
    bool maskLabels = false,
    bool maskImages = false,
    bool maskWebViews = false,
  }) async {
    final boundaryKey = GlobalKey();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: RepaintBoundary(key: boundaryKey, child: subtree),
        ),
      ),
    );

    final context = boundaryKey.currentContext!;
    final boundary = context.findRenderObject()! as RenderRepaintBoundary;
    final collector = MaskCollector(
      MaskingPolicy(
        maskTextInputs: maskTextInputs,
        maskLabels: maskLabels,
        maskImages: maskImages,
        maskWebViews: maskWebViews,
      ),
    );
    return collector.collect(context, boundary);
  }

  group('MaskCollector precedence', () {
    testWidgets('plain content is not masked', (tester) async {
      final ops = await collect(
        tester,
        const Text('hello'),
        maskTextInputs: false,
      );
      expect(ops, isEmpty);
    });

    testWidgets('maskTextInputs masks an EditableText', (tester) async {
      final ops = await collect(
        tester,
        const TextField(),
        maskTextInputs: true,
      );
      expect(ops, hasLength(1));
    });

    testWidgets('disabling maskTextInputs leaves text inputs unmasked',
        (tester) async {
      final ops = await collect(
        tester,
        const TextField(),
        maskTextInputs: false,
      );
      expect(ops, isEmpty);
    });

    testWidgets('LDMask masks its subtree regardless of global config',
        (tester) async {
      final ops = await collect(
        tester,
        const LDMask(child: Text('secret')),
        maskTextInputs: false,
      );
      expect(ops, hasLength(1));
    });

    testWidgets('LDMask collapses its subtree into a single op',
        (tester) async {
      final ops = await collect(
        tester,
        const LDMask(
          child: Column(
            children: [TextField(), TextField()],
          ),
        ),
        maskTextInputs: true,
      );
      // The walk stops at LDMask, so the two inner fields don't add ops.
      expect(ops, hasLength(1));
    });

    testWidgets('LDUnmask reveals a globally-masked text input',
        (tester) async {
      final ops = await collect(
        tester,
        const LDUnmask(child: TextField()),
        maskTextInputs: true,
      );
      expect(ops, isEmpty);
    });

    testWidgets('LDUnmask nested inside LDMask stays masked (mask wins)',
        (tester) async {
      final ops = await collect(
        tester,
        const LDMask(child: LDUnmask(child: TextField())),
        maskTextInputs: true,
      );
      expect(ops, hasLength(1));
    });

    testWidgets('LDMask nested inside LDUnmask still masks (explicit wins)',
        (tester) async {
      final ops = await collect(
        tester,
        const LDUnmask(child: LDMask(child: Text('secret'))),
        maskTextInputs: false,
      );
      expect(ops, hasLength(1));
    });

    testWidgets('a revealed field next to a masked field masks only one',
        (tester) async {
      final ops = await collect(
        tester,
        const Column(
          children: [
            TextField(),
            LDUnmask(child: TextField()),
          ],
        ),
        maskTextInputs: true,
      );
      expect(ops, hasLength(1));
    });

    testWidgets('maskLabels masks a Text label', (tester) async {
      final ops = await collect(
        tester,
        const Text('account balance'),
        maskTextInputs: false,
        maskLabels: true,
      );
      expect(ops, hasLength(1));
    });

    testWidgets('disabling maskLabels leaves text labels unmasked',
        (tester) async {
      final ops = await collect(
        tester,
        const Text('account balance'),
        maskTextInputs: false,
        maskLabels: false,
      );
      expect(ops, isEmpty);
    });

    testWidgets('maskImages masks a raster image', (tester) async {
      final ops = await collect(
        tester,
        const SizedBox(width: 64, height: 64, child: RawImage()),
        maskTextInputs: false,
        maskImages: true,
      );
      expect(ops, hasLength(1));
    });

    testWidgets('LDUnmask reveals a globally-masked label', (tester) async {
      final ops = await collect(
        tester,
        const LDUnmask(child: Text('public')),
        maskTextInputs: false,
        maskLabels: true,
      );
      expect(ops, isEmpty);
    });

    testWidgets('an opaque widget painted on top drops the covered mask',
        (tester) async {
      final ops = await collect(
        tester,
        const SizedBox(
          width: 300,
          height: 300,
          child: Stack(
            children: [
              Positioned(
                left: 50,
                top: 50,
                width: 120,
                height: 40,
                child: TextField(),
              ),
              // Painted last (on top) and fully opaque -> hides the field.
              Positioned.fill(child: ColoredBox(color: Color(0xFF112233))),
            ],
          ),
        ),
        maskTextInputs: true,
      );
      expect(ops, isEmpty);
    });

    testWidgets('a translucent widget on top keeps the mask', (tester) async {
      final ops = await collect(
        tester,
        const SizedBox(
          width: 300,
          height: 300,
          child: Stack(
            children: [
              Positioned(
                left: 50,
                top: 50,
                width: 120,
                height: 40,
                child: TextField(),
              ),
              // Semi-transparent -> the field may still be visible, keep it.
              Positioned.fill(child: ColoredBox(color: Color(0x80112233))),
            ],
          ),
        ),
        maskTextInputs: true,
      );
      expect(ops, hasLength(1));
    });

    testWidgets('an opaque background painted underneath keeps the mask',
        (tester) async {
      final ops = await collect(
        tester,
        const SizedBox(
          width: 300,
          height: 300,
          child: Stack(
            children: [
              // Painted first (underneath) -> nothing collected yet to drop.
              Positioned.fill(child: ColoredBox(color: Color(0xFF112233))),
              Positioned(
                left: 50,
                top: 50,
                width: 120,
                height: 40,
                child: TextField(),
              ),
            ],
          ),
        ),
        maskTextInputs: true,
      );
      expect(ops, hasLength(1));
    });
  });
}
