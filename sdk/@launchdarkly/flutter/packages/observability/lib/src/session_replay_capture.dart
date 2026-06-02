import 'package:flutter/widgets.dart';

import 'platform/ld_observe_platform.dart';

/// Wrap the app, or the subtree that should appear in session replay, with this
/// widget.
///
/// The capture mechanism is platform specific and resolved at compile time:
/// on native (iOS/Android) it provides Flutter-rendered screenshots to the
/// native session replay SDK; on web it is a pass-through because the browser
/// session replay records the DOM directly.
class SessionReplayCapture extends StatelessWidget {
  final Widget child;

  const SessionReplayCapture({super.key, required this.child});

  @override
  Widget build(BuildContext context) =>
      LDObservePlatform.instance.wrapForCapture(child);
}
