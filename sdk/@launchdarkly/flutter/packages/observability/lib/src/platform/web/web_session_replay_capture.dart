import 'package:flutter/widgets.dart';

/// Web session-replay capture wrapper.
///
/// Session replay is not supported on web, so there is nothing to capture and
/// this widget simply renders [child].
class WebSessionReplayCapture extends StatelessWidget {
  final Widget child;

  const WebSessionReplayCapture({super.key, required this.child});

  @override
  Widget build(BuildContext context) => child;
}
