import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

import 'getx_scenarios.dart';
import 'my_home_page.dart';

/// Whether this build navigates with GetX instead of a plain [MaterialApp].
///
/// Chosen at build time with `--dart-define=USE_GETX=true`, which swaps the root
/// widget for [GetXApp] and adds the GetX smoothie flow to the home page. The
/// home page itself is unchanged, so the two modes can be compared signal for
/// signal.
const useGetX = bool.fromEnvironment('USE_GETX');

/// The example rooted at [GetMaterialApp] with `getPages`, the way most GetX
/// apps are wired.
///
/// Clicks need nothing GetX-specific. `SessionReplayCapture` in `main.dart`
/// still wraps the root, and GetX pushes ordinary routes onto the [MaterialApp]
/// navigator, so taps resolve exactly as they do without GetX — including inside
/// `Get.dialog`, `Get.bottomSheet`, and `Get.snackbar`, which all render within
/// the app.
///
/// Screen views need the same [LDNavigatorObserver] as anywhere else, with two
/// GetX details to know about:
///
/// - `GetMaterialApp` merges the observers passed here with its own
///   `GetObserver`. `GetMaterialApp.router` is the exception: it accepts
///   `navigatorObservers` and then builds its delegate without them, so that
///   constructor needs
///   `routerDelegate: GetDelegate(navigatorObservers: [LDNavigatorObserver()])`
///   instead.
/// - GetX names a route after the URL it was pushed with. [_screenName] maps a
///   concrete smoothie id back to its registered route pattern.
class GetXApp extends StatelessWidget {
  const GetXApp({super.key});

  @override
  Widget build(BuildContext context) {
    return GetMaterialApp(
      title: 'Flutter Demo (GetX)',
      navigatorObservers: [
        LDNavigatorObserver(screenNameExtractor: _screenName),
      ],
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      ),
      initialRoute: '/',
      // Named pages instead of `home:` plus `Get.to(() => Page())`: a page name
      // is a string literal and survives `--obfuscate`, while the name GetX
      // derives from a widget type does not. Passing `home:` would also stop
      // GetX from generating the initial route from `getPages`.
      getPages: [
        // The same home page as the MaterialApp build, so both modes start from
        // the same screen and the smoothie flow is pushed on top of it.
        GetPage(name: '/', page: () => const MyHomePage()),
        GetPage(name: '/smoothies', page: () => const GetXSmoothieMenuView()),
        GetPage(name: '/smoothies/:id', page: () => const GetXSmoothieView()),
        GetPage(
          name: '/smoothies/:id/purchase',
          page: () => const GetXPurchaseView(),
        ),
      ],
    );
  }
}

/// Reports the `GetPage` pattern a route matched rather than the URL it was
/// pushed with, so smoothie ids collapse into one screen instead of one screen
/// per product.
String? _screenName(Route<dynamic> route) {
  final name = route.settings.name;
  if (name == null) return null;
  final path = Uri.parse(name).path;
  for (final page in Get.routeTree.routes) {
    if (page.path.regex.hasMatch(path)) return page.name;
  }
  return name;
}
