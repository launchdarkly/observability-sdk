import 'package:flutter/widgets.dart';

/// Web session-replay capture wrapper.
///
/// On web the session replay records the rendered DOM/canvas directly through
/// the browser SR SDK, so there is no Flutter-side screenshot pump. This widget
/// is therefore a pass-through that simply renders [child].
class WebSessionReplayCapture extends StatelessWidget {
  final Widget child;

  const WebSessionReplayCapture({super.key, required this.child});

  @override
  Widget build(BuildContext context) => child;
}
