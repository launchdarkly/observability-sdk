import 'package:flutter/widgets.dart';

/// Marks its [child] subtree as sensitive so it is redacted from session
/// replay recordings.
///
/// This is the Flutter equivalent of the MAUI `view.LDMask()` API, expressed
/// as a wrapper widget instead of a method call: wrap any widget you don't
/// want to appear in a recorded session, and its on-screen bounds are painted
/// over in every captured frame.
///
/// ```dart
/// LDMask(
///   child: Text(creditCardNumber),
/// )
/// ```
///
/// Masking follows the widget as it lays out and scrolls — there is nothing to
/// register or tear down. Nesting an [LDUnmask] inside reveals that inner
/// subtree again.
///
/// Platform behavior: active on iOS/Android (the native screenshot pipeline
/// redacts the region). On web it renders [child] unchanged for now — web
/// session replay is not yet available.
class LDMask extends StatelessWidget {
  /// The subtree to redact from session replay.
  final Widget child;

  const LDMask({super.key, required this.child});

  @override
  Widget build(BuildContext context) => child;
}

/// Excludes its [child] subtree from session replay so it never appears in a
/// recording.
///
/// This is the Flutter equivalent of the native `view.LDIgnore()` API. On the
/// native SDKs an ignored view is dropped from the screenshot entirely; Flutter
/// captures the whole window as a single raster, so it cannot omit individual
/// pixels — instead the region is **painted over** in every frame, exactly like
/// [LDMask]. The privacy outcome is the same (the content is never visible in a
/// recording); only the mechanism differs.
///
/// ```dart
/// LDIgnore(
///   child: VideoPlayer(controller),
/// )
/// ```
///
/// Like [LDMask], an explicit ignore covers its whole subtree and always wins
/// over global masking and over a nested [LDUnmask].
class LDIgnore extends StatelessWidget {
  /// The subtree to exclude from session replay.
  final Widget child;

  const LDIgnore({super.key, required this.child});

  @override
  Widget build(BuildContext context) => child;
}

/// Exempts its [child] subtree from *global*, screen-wide session-replay
/// masking (the `PrivacyOptions` rules such as `maskTextInputs`), revealing it
/// in the recording.
///
/// This is the Flutter equivalent of the MAUI `view.LDUnmask()` API. Use it to
/// reveal a field that the screen-wide policy would otherwise redact — for
/// example, a non-sensitive input on a page where `maskTextInputs` is on:
///
/// ```dart
/// // maskTextInputs masks every field; reveal just this one.
/// LDUnmask(
///   child: TextField(controller: searchController),
/// )
/// ```
///
/// Precedence: [LDUnmask] only overrides global masking — it does **not**
/// override an explicit [LDMask]. An [LDUnmask] nested inside an [LDMask] stays
/// masked, because an explicit per-widget mask always wins.
///
/// Platform behavior: active on iOS/Android. On web it renders [child]
/// unchanged for now — web session replay is not yet available.
class LDUnmask extends StatelessWidget {
  /// The subtree to reveal in session replay.
  final Widget child;

  const LDUnmask({super.key, required this.child});

  @override
  Widget build(BuildContext context) => child;
}
