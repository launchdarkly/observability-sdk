import 'package:flutter/widgets.dart';

/// Names its [child] subtree for click tracking, so taps inside it report a
/// stable [id] instead of whatever the widget tree happens to look like.
///
/// This is the Flutter equivalent of the React Native `<LDClick>` wrapper and the
/// Swift `.ldClick(_:)` modifier. It is a passive marker: it emits nothing on its
/// own and renders [child] unchanged, so wrapping a button cannot double-count a
/// tap. Automatic click capture reads the marker while resolving the tapped
/// widget and uses [id] for `event.id` / the replay `clickSelector`.
///
/// ```dart
/// LDClick(
///   id: 'checkout.pay',
///   child: ElevatedButton(onPressed: _pay, child: const Text('Pay')),
/// )
/// ```
///
/// Reach for this when a widget has no `Key` or `Semantics.identifier`, or when
/// the automatic name is too generic to group on. To name every instance of a
/// custom widget type at once — a design-system button, say — register a
/// [LDClickTargetResolver] instead of tagging each call site.
///
/// [properties] are attached to the `click` span for taps on this subtree, at
/// lower precedence than the reserved `event.*` fields.
///
/// Requires the widget tree to be wrapped in `SessionReplayCapture`, which hosts
/// the click detector. Without it the marker is inert.
class LDClick extends StatelessWidget {
  /// Stable identifier reported as `event.id` for taps inside [child].
  final String id;

  /// Additional attributes attached to the `click` span for taps inside [child].
  final Map<String, Object?>? properties;

  /// The subtree this marker names.
  final Widget child;

  const LDClick({
    super.key,
    required this.id,
    this.properties,
    required this.child,
  });

  @override
  Widget build(BuildContext context) => child;
}

/// A description of a widget that automatic click capture recognizes as a click
/// target, returned by an [LDClickTargetResolver].
class LDClickTargetInfo {
  /// The widget type name reported as `event.tag` and the replay `clickTarget`,
  /// e.g. `PrimaryButton`.
  ///
  /// Supply a string literal rather than `runtimeType.toString()`: release builds
  /// may be compiled with `--obfuscate`, under which runtime type names are
  /// mangled and would group differently in every build.
  final String tag;

  /// Stable identifier for this instance, reported as `event.id`. Optional — an
  /// [LDClick] marker, `Semantics.identifier`, or a `ValueKey` supplies one too.
  final String? id;

  /// The element's visible label, reported as `event.text`. When null, capture
  /// falls back to its usual text extraction (semantics label, then contained
  /// text for button-like widgets).
  final String? text;

  /// Whether a more specific target nested inside this widget should win.
  ///
  /// Set this for containers that merely make a region tappable (a card, a row)
  /// so a real button inside them is reported instead. Leave it `false` for
  /// widgets that are themselves the thing being pressed.
  final bool preferInnerTarget;

  const LDClickTargetInfo({
    required this.tag,
    this.id,
    this.text,
    this.preferInnerTarget = false,
  });
}

/// Recognizes an application's own widget types as click targets.
///
/// Automatic capture already recognizes the Material and Cupertino widgets, but
/// a design system's `PrimaryButton` looks like an anonymous composition of them.
/// Return an [LDClickTargetInfo] for the types you want named, and `null` for
/// everything else so the built-in rules apply.
///
/// ```dart
/// LDClickTargetInfo? myResolver(Widget widget) => switch (widget) {
///   PrimaryButton(:final label) =>
///     LDClickTargetInfo(tag: 'PrimaryButton', text: label),
///   _ => null,
/// };
/// ```
///
/// Runs for every widget above the tap on the way down, so keep it cheap and
/// free of side effects. An exception is caught and logged, and resolution
/// continues with the built-in rules.
typedef LDClickTargetResolver = LDClickTargetInfo? Function(Widget widget);
