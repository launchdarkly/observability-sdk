import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:launchdarkly_flutter_observability/src/instrumentation/click/click_target_resolver.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/click/ld_click.dart';
import 'package:launchdarkly_flutter_observability/src/masking.dart';

/// Resolves the widget under the center of the widget matched by [finder],
/// exactly as a tap on it would.
ClickTarget? resolveAt(
  WidgetTester tester,
  Finder finder, {
  LDClickTargetResolver? customResolver,
  bool captureText = true,
}) {
  final resolver = ClickTargetResolver(
    customResolver: customResolver,
    captureText: captureText,
  );
  return resolver.resolve(
    tester.element(find.byType(MaterialApp)),
    tester.getCenter(finder),
  );
}

Future<void> pumpApp(WidgetTester tester, Widget body) => tester.pumpWidget(
  MaterialApp(
    home: Scaffold(body: Center(child: body)),
  ),
);

void main() {
  group('target selection', () {
    testWidgets('reports the button rather than the ink it is built from', (
      tester,
    ) async {
      await pumpApp(
        tester,
        ElevatedButton(onPressed: () {}, child: const Text('Pay')),
      );

      final target = resolveAt(tester, find.byType(ElevatedButton));

      expect(target?.tag, equals('ElevatedButton'));
      expect(target?.text, equals('Pay'));
    });

    testWidgets('describes a bare GestureDetector, which Sentry cannot', (
      tester,
    ) async {
      await pumpApp(
        tester,
        GestureDetector(
          onTap: () {},
          child: const SizedBox(width: 100, height: 100),
        ),
      );

      final target = resolveAt(tester, find.byType(GestureDetector));

      expect(target?.tag, equals('GestureDetector'));
    });

    testWidgets('prefers a nested button over the container holding it', (
      tester,
    ) async {
      await pumpApp(
        tester,
        ListTile(
          onTap: () {},
          title: const Text('Notifications'),
          trailing: IconButton(
            onPressed: () {},
            tooltip: 'Mute',
            icon: const Icon(Icons.volume_off),
          ),
        ),
      );

      expect(
        resolveAt(tester, find.byType(IconButton))?.tag,
        equals('IconButton'),
      );
      // The tile itself still describes a tap on its body.
      expect(
        resolveAt(tester, find.text('Notifications'))?.tag,
        equals('ListTile'),
      );
    });

    testWidgets('a disabled button is not a click', (tester) async {
      await pumpApp(
        tester,
        const ElevatedButton(onPressed: null, child: Text('Pay')),
      );

      expect(resolveAt(tester, find.byType(ElevatedButton)), isNull);
    });

    testWidgets('a GestureDetector with no tap handler is not a click', (
      tester,
    ) async {
      await pumpApp(
        tester,
        GestureDetector(
          onVerticalDragStart: (_) {},
          child: const SizedBox(width: 100, height: 100),
        ),
      );

      expect(resolveAt(tester, find.byType(GestureDetector)), isNull);
    });

    testWidgets('empty space is not a click', (tester) async {
      await pumpApp(tester, const SizedBox(width: 100, height: 100));

      expect(resolveAt(tester, find.byType(Scaffold)), isNull);
    });

    testWidgets('a tap on a dialog does not resolve to the route beneath it', (
      tester,
    ) async {
      // The regression the `debugVisitOnstageChildren` walk exists for: a pushed
      // route does not remove the route underneath from the element tree, so a
      // plain `visitChildren` walk would find the button behind the dialog.
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: ElevatedButton(
                  onPressed: () => showDialog<void>(
                    context: context,
                    builder: (_) => AlertDialog(
                      content: SizedBox(
                        width: 300,
                        height: 300,
                        child: Center(
                          child: TextButton(
                            onPressed: () {},
                            child: const Text('Confirm'),
                          ),
                        ),
                      ),
                    ),
                  ),
                  child: const Text('Underneath'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Underneath'));
      await tester.pumpAndSettle();

      // A point inside the dialog but away from its button: it must resolve to
      // nothing at all rather than to the button on the route below.
      final resolver = const ClickTargetResolver();
      final dialog = tester.getRect(find.byType(AlertDialog));
      final target = resolver.resolve(
        tester.element(find.byType(MaterialApp)),
        Offset(dialog.center.dx, dialog.top + 8),
      );

      expect(target?.tag, isNot(equals('ElevatedButton')));
    });

    testWidgets('an IgnorePointer subtree is transparent to clicks', (
      tester,
    ) async {
      await pumpApp(
        tester,
        IgnorePointer(
          child: ElevatedButton(onPressed: () {}, child: const Text('Pay')),
        ),
      );

      expect(resolveAt(tester, find.byType(ElevatedButton)), isNull);
    });

    testWidgets('an AbsorbPointer swallows clicks on its subtree', (
      tester,
    ) async {
      await pumpApp(
        tester,
        AbsorbPointer(
          child: ElevatedButton(onPressed: () {}, child: const Text('Pay')),
        ),
      );

      expect(resolveAt(tester, find.byType(ElevatedButton)), isNull);
    });

    testWidgets('the topmost of two overlapping widgets wins', (tester) async {
      await pumpApp(
        tester,
        SizedBox(
          key: const ValueKey('overlap'),
          width: 200,
          height: 200,
          child: Stack(
            children: [
              Positioned.fill(
                child: GestureDetector(onTap: () {}, child: const SizedBox()),
              ),
              Positioned.fill(
                child: TextButton(
                  onPressed: () {},
                  child: const Text('On top'),
                ),
              ),
            ],
          ),
        ),
      );

      expect(
        resolveAt(tester, find.byKey(const ValueKey('overlap')))?.tag,
        equals('TextButton'),
      );
    });
  });

  group('identifier', () {
    testWidgets('an LDClick marker wins over everything else', (tester) async {
      await pumpApp(
        tester,
        LDClick(
          id: 'checkout.pay',
          properties: const {'flow': 'express'},
          child: Semantics(
            identifier: 'semantic-id',
            child: ElevatedButton(
              key: const ValueKey('key-id'),
              onPressed: () {},
              child: const Text('Pay'),
            ),
          ),
        ),
      );

      final target = resolveAt(tester, find.byType(ElevatedButton));

      expect(target?.id, equals('checkout.pay'));
      expect(target?.properties, equals({'flow': 'express'}));
    });

    testWidgets('falls back to Semantics.identifier, then to a ValueKey', (
      tester,
    ) async {
      await pumpApp(
        tester,
        Column(
          children: [
            Semantics(
              identifier: 'semantic-id',
              child: ElevatedButton(
                key: const ValueKey('key-id'),
                onPressed: () {},
                child: const Text('Semantic'),
              ),
            ),
            ElevatedButton(
              key: const ValueKey('key-id'),
              onPressed: () {},
              child: const Text('Keyed'),
            ),
          ],
        ),
      );

      expect(
        resolveAt(tester, find.text('Semantic'))?.id,
        equals('semantic-id'),
      );
      expect(resolveAt(tester, find.text('Keyed'))?.id, equals('key-id'));
    });

    testWidgets('ignores key types whose value changes between runs', (
      tester,
    ) async {
      await pumpApp(
        tester,
        ElevatedButton(
          key: GlobalKey(),
          onPressed: () {},
          child: const Text('Pay'),
        ),
      );

      expect(resolveAt(tester, find.byType(ElevatedButton))?.id, isNull);
    });

    testWidgets('the innermost marker describes the target', (tester) async {
      await pumpApp(
        tester,
        LDClick(
          id: 'outer',
          child: LDClick(
            id: 'inner',
            child: ElevatedButton(onPressed: () {}, child: const Text('Pay')),
          ),
        ),
      );

      expect(
        resolveAt(tester, find.byType(ElevatedButton))?.id,
        equals('inner'),
      );
    });
  });

  group('text', () {
    testWidgets('reads a semantic label when the target has no text', (
      tester,
    ) async {
      await pumpApp(
        tester,
        IconButton(
          onPressed: () {},
          tooltip: 'Delete item',
          icon: const Icon(Icons.delete),
        ),
      );

      expect(
        resolveAt(tester, find.byType(IconButton))?.text,
        equals('Delete item'),
      );
    });

    testWidgets('does not harvest text out of a generic container', (
      tester,
    ) async {
      // A container's content is an arbitrary fragment of a row, not its label.
      await pumpApp(
        tester,
        GestureDetector(
          onTap: () {},
          child: const Text('Some paragraph of body copy'),
        ),
      );

      final target = resolveAt(tester, find.byType(GestureDetector));

      expect(target?.tag, equals('GestureDetector'));
      expect(target?.text, isNull);
    });

    testWidgets('never reads what a user typed into a field', (tester) async {
      await pumpApp(
        tester,
        SizedBox(
          width: 300,
          child: LDClickTestButton(
            child: TextField(
              controller: TextEditingController(text: 'secret@example.com'),
              decoration: const InputDecoration(hintText: 'Email'),
            ),
          ),
        ),
      );

      final target = resolveAt(
        tester,
        find.byType(LDClickTestButton),
        customResolver: (widget) => widget is LDClickTestButton
            ? const LDClickTargetInfo(tag: 'TestButton')
            : null,
      );

      expect(target?.text, equals('Email'));
    });

    testWidgets('skips an obscured field entirely', (tester) async {
      await pumpApp(
        tester,
        SizedBox(
          width: 300,
          child: LDClickTestButton(
            child: TextField(
              obscureText: true,
              controller: TextEditingController(text: 'hunter2'),
              decoration: const InputDecoration(hintText: 'Password'),
            ),
          ),
        ),
      );

      final target = resolveAt(
        tester,
        find.byType(LDClickTestButton),
        customResolver: (widget) => widget is LDClickTestButton
            ? const LDClickTargetInfo(tag: 'TestButton')
            : null,
      );

      expect(target?.tag, equals('TestButton'));
      expect(target?.text, isNull);
    });

    testWidgets('honors the masking widgets that drive replay redaction', (
      tester,
    ) async {
      await pumpApp(
        tester,
        LDMask(
          child: ElevatedButton(
            onPressed: () {},
            child: const Text('Dr. Smith'),
          ),
        ),
      );

      final target = resolveAt(tester, find.byType(ElevatedButton));

      expect(target?.tag, equals('ElevatedButton'));
      expect(target?.text, isNull);
    });

    testWidgets('captureText: false drops the label but keeps the click', (
      tester,
    ) async {
      await pumpApp(
        tester,
        ElevatedButton(onPressed: () {}, child: const Text('Pay')),
      );

      final target = resolveAt(
        tester,
        find.byType(ElevatedButton),
        captureText: false,
      );

      expect(target?.tag, equals('ElevatedButton'));
      expect(target?.text, isNull);
    });
  });

  group('custom resolver', () {
    testWidgets('names an application widget type and supplies its label', (
      tester,
    ) async {
      await pumpApp(tester, const LDClickTestButton(child: Text('Pay now')));

      final target = resolveAt(
        tester,
        find.byType(LDClickTestButton),
        customResolver: (widget) => widget is LDClickTestButton
            ? const LDClickTargetInfo(
                tag: 'PrimaryButton',
                id: 'primary',
                text: 'Pay',
              )
            : null,
      );

      expect(target?.tag, equals('PrimaryButton'));
      expect(target?.id, equals('primary'));
      expect(target?.text, equals('Pay'));
    });

    testWidgets('a resolver that throws does not break the click', (
      tester,
    ) async {
      await pumpApp(
        tester,
        ElevatedButton(onPressed: () {}, child: const Text('Pay')),
      );

      final target = resolveAt(
        tester,
        find.byType(ElevatedButton),
        customResolver: (widget) => throw StateError('boom'),
      );

      // Reported, then resolution continues with the built-in rules.
      expect(tester.takeException(), isStateError);
      expect(target?.tag, equals('ElevatedButton'));
    });
  });

  group('path', () {
    testWidgets('describes where the target sits, ending with the target', (
      tester,
    ) async {
      await pumpApp(
        tester,
        ElevatedButton(
          key: const ValueKey('checkout'),
          onPressed: () {},
          child: const Text('Pay'),
        ),
      );

      final path = resolveAt(tester, find.byType(ElevatedButton))?.path;

      expect(path, endsWith('ElevatedButton#checkout'));
      expect(path, contains('Scaffold'));
    });

    testWidgets('omits private framework types', (tester) async {
      await pumpApp(
        tester,
        ElevatedButton(onPressed: () {}, child: const Text('Pay')),
      );

      final path = resolveAt(tester, find.byType(ElevatedButton))?.path;

      expect(path!.split('/'), everyElement(isNot(startsWith('_'))));
    });
  });
}

/// Stands in for an application's own button type, which the built-in rules know
/// nothing about.
class LDClickTestButton extends StatelessWidget {
  final Widget child;

  const LDClickTestButton({super.key, required this.child});

  @override
  Widget build(BuildContext context) =>
      GestureDetector(onTap: () {}, child: child);
}
