import 'package:flutter/widgets.dart';

import 'instrumentation/click/click_instrumentation.dart';
import 'platform/ld_observe_platform.dart';

/// Wrap the app, or the subtree that should appear in session replay, with this
/// widget.
///
/// The capture mechanism is platform specific and resolved at compile time:
/// on native (iOS/Android) it provides Flutter-rendered screenshots to the
/// native session replay SDK. Session replay is not supported on web, where
/// the capture part is a pass-through.
///
/// Click capture also lives here, on every platform. Flutter renders its whole
/// UI into one native view, so the tapped widget can only be identified in Dart,
/// which needs a pointer observer and an element to anchor the search on — both
/// of which this widget already provides. Bundling them means click tracking
/// costs no second wrapper, the packaging Datadog also settled on.
///
/// Wrap as high as possible, ideally around `MaterialApp`. Dialogs, bottom
/// sheets, and other routes are children of the app's `Navigator`, so a wrap
/// further down excludes taps on anything the app pushes above it.
class SessionReplayCapture extends StatelessWidget {
  final Widget child;

  const SessionReplayCapture({super.key, required this.child});

  @override
  Widget build(BuildContext context) =>
      LDClickDetector(child: LDObservePlatform.instance.wrapForCapture(child));
}
