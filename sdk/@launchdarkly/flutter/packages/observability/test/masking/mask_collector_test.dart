import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/masking/mask_collector.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/masking/mask_operation.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/masking/masking_policy.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/masking/widget_masking_config.dart';

void main() {
  Future<List<MaskOperation>> collect(
    WidgetTester tester,
    Widget subtree, {
    required bool maskTextInputs,
    bool maskLabels = false,
    bool maskImages = false,
    bool maskWebViews = false,
    double minimumAlpha = 0.02,
    WidgetMaskingConfig widgetConfig = WidgetMaskingConfig.empty,
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
        minimumAlpha: minimumAlpha,
        widgetConfig: widgetConfig,
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

    testWidgets('LDIgnore covers its subtree like LDMask', (tester) async {
      final ops = await collect(
        tester,
        const LDIgnore(child: Text('secret')),
        maskTextInputs: false,
      );
      expect(ops, hasLength(1));
    });

    testWidgets('LDIgnore collapses its subtree into a single op',
        (tester) async {
      final ops = await collect(
        tester,
        const LDIgnore(
          child: Column(
            children: [TextField(), TextField()],
          ),
        ),
        maskTextInputs: true,
      );
      expect(ops, hasLength(1));
    });

    testWidgets('config maskWidgetTypes masks a matching widget',
        (tester) async {
      final ops = await collect(
        tester,
        const Text('secret'),
        maskTextInputs: false,
        widgetConfig: const WidgetMaskingConfig(maskTypes: {Text}),
      );
      expect(ops, hasLength(1));
    });

    testWidgets('config maskWidgetKeys masks a keyed widget', (tester) async {
      const key = ValueKey('mask-me');
      final ops = await collect(
        tester,
        const Text('secret', key: key),
        maskTextInputs: false,
        widgetConfig: WidgetMaskingConfig(maskKeys: {key}),
      );
      expect(ops, hasLength(1));
    });

    testWidgets('config ignoreWidgetTypes covers a matching widget',
        (tester) async {
      final ops = await collect(
        tester,
        const Text('secret'),
        maskTextInputs: false,
        widgetConfig: const WidgetMaskingConfig(ignoreTypes: {Text}),
      );
      expect(ops, hasLength(1));
    });

    testWidgets('config unmaskWidgetKeys reveals a globally-masked input',
        (tester) async {
      const key = ValueKey('reveal-me');
      final ops = await collect(
        tester,
        const TextField(key: key),
        maskTextInputs: true,
        widgetConfig: WidgetMaskingConfig(unmaskKeys: {key}),
      );
      expect(ops, isEmpty);
    });

    testWidgets('config mask wins over config unmask on the same widget',
        (tester) async {
      const key = ValueKey('conflict');
      final ops = await collect(
        tester,
        const TextField(key: key),
        maskTextInputs: false,
        widgetConfig: WidgetMaskingConfig(
          maskKeys: {key},
          unmaskKeys: {key},
        ),
      );
      // Explicit mask is evaluated before unmask, so it wins.
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

    testWidgets('LDUnmask propagates through deep nesting', (tester) async {
      // Mirrors NestedMaskingPropagationView's "deep unmask through nesting"
      // section: a text input two levels of non-masking widgets below an
      // LDUnmask ancestor should still be revealed.
      final ops = await collect(
        tester,
        const LDUnmask(
          child: Padding(
            padding: EdgeInsets.all(8),
            child: ColoredBox(
              color: Color(0x2600FF00),
              child: Padding(
                padding: EdgeInsets.all(8),
                child: ColoredBox(
                  color: Color(0x0D00FF00),
                  child: TextField(),
                ),
              ),
            ),
          ),
        ),
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

    testWidgets('an offstage subtree is pruned (not masked)', (tester) async {
      final ops = await collect(
        tester,
        const Offstage(
          offstage: true,
          child: TextField(),
        ),
        maskTextInputs: true,
      );
      expect(ops, isEmpty);
    });

    testWidgets('a fully transparent subtree is pruned (not masked)',
        (tester) async {
      final ops = await collect(
        tester,
        const Opacity(
          opacity: 0,
          child: TextField(),
        ),
        maskTextInputs: true,
      );
      expect(ops, isEmpty);
    });

    testWidgets('an Opacity below minimumAlpha is pruned (not masked)',
        (tester) async {
      final ops = await collect(
        tester,
        const Opacity(
          opacity: 0.01,
          child: TextField(),
        ),
        maskTextInputs: true,
        minimumAlpha: 0.02,
      );
      expect(ops, isEmpty);
    });

    testWidgets('an Opacity at or above minimumAlpha is still masked',
        (tester) async {
      final ops = await collect(
        tester,
        const Opacity(
          opacity: 0.5,
          child: TextField(),
        ),
        maskTextInputs: true,
        minimumAlpha: 0.02,
      );
      expect(ops, hasLength(1));
    });

    testWidgets('an invisible Visibility subtree is pruned (not masked)',
        (tester) async {
      final ops = await collect(
        tester,
        const Visibility(
          visible: false,
          maintainState: true,
          child: TextField(),
        ),
        maskTextInputs: true,
      );
      expect(ops, isEmpty);
    });

    testWidgets('a visible Offstage child is still masked', (tester) async {
      final ops = await collect(
        tester,
        const Offstage(
          offstage: false,
          child: TextField(),
        ),
        maskTextInputs: true,
      );
      expect(ops, hasLength(1));
    });

    testWidgets('a 3D-transformed text input is masked as a non-affine quad',
        (tester) async {
      // A perspective tilt (non-affine transform) must still be masked, and the
      // op must report itself as non-affine so the applier draws it as a quad
      // polygon rather than a parallelogram. Mirrors the native createMask 3D
      // branch returning Mask.quad.
      final ops = await collect(
        tester,
        Center(
          child: Transform(
            transform: Matrix4.identity()
              ..setEntry(3, 2, 0.001)
              ..rotateX(0.9),
            child: const SizedBox(
              width: 200,
              height: 60,
              child: TextField(),
            ),
          ),
        ),
        maskTextInputs: true,
      );
      expect(ops, hasLength(1));
      expect(ops.single.isAffine, isFalse);
    });

    testWidgets('an axis-aligned text input is masked as an affine op',
        (tester) async {
      final ops = await collect(
        tester,
        const SizedBox(width: 200, height: 60, child: TextField()),
        maskTextInputs: true,
      );
      expect(ops, hasLength(1));
      expect(ops.single.isAffine, isTrue);
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
