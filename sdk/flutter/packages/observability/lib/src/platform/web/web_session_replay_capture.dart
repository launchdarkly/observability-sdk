import 'package:flutter/widgets.dart';

/// Web session-replay capture wrapper.
///
/// Session replay is not supported on web, so there is nothing to capture and
/// this widget simply renders [child].
class WebSessionReplayCapture extends StatelessWidget {
  /// The subtree to render.
  final Widget child;

  /// Creates a wrapper that renders [child] unchanged.
  const WebSessionReplayCapture({super.key, required this.child});

  @override
  Widget build(BuildContext context) => child;
}
