// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get/get.dart';

import 'package:example/getx_app.dart';
import 'package:example/my_app.dart';

void main() {
  tearDown(() => Get.reset());

  // Covers all three levels of the GetX example. Each concrete URL is matched
  // to a parameterized `getPages` entry by the screen-name extractor.
  testWidgets('GetX smoothie flow pushes through purchase', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const GetXApp());

    // The home page is the root in both modes; the smoothie flow is pushed on
    // top of it. The entry button itself only renders when the app is built with
    // USE_GETX, so the test pushes the route directly.
    expect(
      find.text('You have pushed the button this many times:'),
      findsOneWidget,
    );

    Get.toNamed('/smoothies');
    await tester.pumpAndSettle();
    expect(find.text('Smoothies'), findsOneWidget);

    await tester.tap(find.text('Berry Blue'));
    await tester.pumpAndSettle();
    expect(Get.currentRoute, '/smoothies/berry-blue');

    final purchaseButton = find.text('Purchase Berry Blue');
    await tester.scrollUntilVisible(purchaseButton, 300);
    await tester.tap(purchaseButton);
    await tester.pumpAndSettle();
    expect(Get.currentRoute, '/smoothies/berry-blue/purchase');
    expect(find.text('Confirm purchase'), findsOneWidget);
  });

  // The MaterialApp build reaches the same three screens through `Navigator`.
  testWidgets('Material smoothie flow pushes through purchase', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    final listButton = find.text('Smoothie List');
    await tester.ensureVisible(listButton);
    await tester.tap(listButton);
    await tester.pumpAndSettle();
    expect(find.text('Smoothies'), findsOneWidget);

    await tester.tap(find.text('Berry Blue'));
    await tester.pumpAndSettle();
    final purchaseButton = find.text('Purchase Berry Blue');
    await tester.scrollUntilVisible(purchaseButton, 300);
    await tester.tap(purchaseButton);
    await tester.pumpAndSettle();
    expect(find.text('Confirm purchase'), findsOneWidget);
  });

  testWidgets('Counter increments smoke test', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(const MyApp());

    // Verify that our counter starts at 0.
    expect(find.text('0'), findsOneWidget);
    expect(find.text('1'), findsNothing);

    // Tap the '+' icon and trigger a frame.
    await tester.tap(find.byIcon(Icons.add));
    await tester.pump();

    // Verify that our counter has incremented.
    expect(find.text('0'), findsNothing);
    expect(find.text('1'), findsOneWidget);
  });
}
