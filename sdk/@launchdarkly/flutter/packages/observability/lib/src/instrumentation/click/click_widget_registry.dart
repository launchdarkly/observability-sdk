import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import 'ld_click.dart';

/// How a recognized widget competes with others above and below it in the tree.
enum ClickTargetPrecedence {
  /// The widget is the thing being pressed, so it wins over anything nested
  /// inside it. `ElevatedButton` beats the `InkWell` it is built from.
  specific,

  /// The widget only makes a region tappable, so a [specific] target nested
  /// inside it wins instead. A bare `GestureDetector` or `ListTile` describes a
  /// tap only when nothing more precise sits underneath.
  container,
}

/// A widget recognized as a click target, with the precedence that decides
/// whether it or a nested match describes the tap.
class RecognizedClickTarget {
  /// Widget type name for `event.tag` and the replay `clickTarget`.
  final String tag;

  final ClickTargetPrecedence precedence;

  /// Whether the widget currently responds to a press. A disabled button is
  /// still painted and still contains the tap point, but pressing it does
  /// nothing, so it must not be reported as a click.
  final bool enabled;

  /// Whether text found inside the widget may describe it.
  ///
  /// True for button-like widgets, whose whole content is their label. False for
  /// containers, where harvesting inner text would report an arbitrary fragment
  /// of a row rather than what was pressed — such widgets fall back to their
  /// semantic label. Both competitors draw the same line.
  final bool allowsInnerText;

  /// A caller-supplied label that replaces every extracted source. Set only by
  /// an [LDClickTargetResolver], whose author knows what their widget's label is
  /// better than any tree walk does.
  final String? overrideText;

  /// A label used only when no text is found in the widget at all — a `Radio`'s
  /// value, which is the only thing telling one option in a group from another.
  final String? fallbackText;

  /// Identifier read from the widget itself, supplied by an
  /// [LDClickTargetResolver].
  final String? id;

  const RecognizedClickTarget({
    required this.tag,
    required this.precedence,
    this.enabled = true,
    this.allowsInnerText = false,
    this.overrideText,
    this.fallbackText,
    this.id,
  });
}

/// Recognizes the framework widgets that can be described as click targets.
///
/// Every name is a **string literal** rather than `runtimeType.toString()`.
/// Flutter release builds may be compiled with `--obfuscate`, under which runtime
/// type names are mangled; both Sentry and Datadog identify widgets by `is` tests
/// against concrete classes for exactly this reason. Type tests survive
/// obfuscation, so `event.tag` stays stable across builds.
abstract final class ClickWidgetRegistry {
  /// Describes [widget] as a click target, or returns null when it is not one.
  ///
  /// [customResolver] is consulted first so an application's own widget types win
  /// over the framework widgets they are composed from.
  static RecognizedClickTarget? recognize(
    Widget widget, {
    LDClickTargetResolver? customResolver,
  }) {
    if (customResolver != null) {
      final custom = customResolver(widget);
      if (custom != null) {
        return RecognizedClickTarget(
          tag: custom.tag,
          precedence: custom.preferInnerTarget
              ? ClickTargetPrecedence.container
              : ClickTargetPrecedence.specific,
          // A custom type is described by its author; without a label from them,
          // fall back to reading the widget's content the way a button is read,
          // since that is what such a type usually is.
          allowsInnerText: custom.text == null,
          overrideText: custom.text,
          id: custom.id,
        );
      }
    }

    // Material buttons. `ButtonStyleButton` is the shared base of the modern
    // buttons and already folds `onPressed`/`onLongPress` into `enabled`, but the
    // subtypes are tested individually so each reports its own name.
    if (widget is ElevatedButton) {
      return _button('ElevatedButton', widget.enabled);
    }
    if (widget is FilledButton) {
      return _button('FilledButton', widget.enabled);
    }
    if (widget is OutlinedButton) {
      return _button('OutlinedButton', widget.enabled);
    }
    if (widget is TextButton) {
      return _button('TextButton', widget.enabled);
    }
    if (widget is ButtonStyleButton) {
      return _button('Button', widget.enabled);
    }
    if (widget is MaterialButton) {
      return _button('MaterialButton', widget.enabled);
    }
    if (widget is CupertinoButton) {
      return _button('CupertinoButton', widget.enabled);
    }
    if (widget is FloatingActionButton) {
      return _button('FloatingActionButton', widget.onPressed != null);
    }
    if (widget is IconButton) {
      // An icon has no text of its own, so only the semantic label can name it.
      return RecognizedClickTarget(
        tag: 'IconButton',
        precedence: ClickTargetPrecedence.specific,
        enabled: widget.onPressed != null,
      );
    }

    // Selection controls. Their value is state, not a label, so none of them
    // harvest inner text.
    if (widget is Switch) {
      return _control('Switch', widget.onChanged != null);
    }
    if (widget is CupertinoSwitch) {
      return _control('CupertinoSwitch', widget.onChanged != null);
    }
    if (widget is Checkbox) {
      return _control('Checkbox', widget.onChanged != null);
    }
    if (widget is Radio) {
      // A radio's identity is its value, which is the only thing distinguishing
      // one option from another in a group.
      return RecognizedClickTarget(
        tag: 'Radio',
        precedence: ClickTargetPrecedence.specific,
        fallbackText: widget.value?.toString(),
      );
    }
    if (widget is Slider) {
      return _control('Slider', widget.onChanged != null);
    }

    // Menus and tabs.
    if (widget is PopupMenuButton) {
      return _button('PopupMenuButton', widget.enabled);
    }
    if (widget is PopupMenuItem) {
      return _button('PopupMenuItem', widget.enabled);
    }
    if (widget is DropdownButton) {
      return _button('DropdownButton', widget.onChanged != null);
    }
    if (widget is DropdownMenuItem) {
      return _button('DropdownMenuItem', widget.enabled);
    }
    if (widget is Tab) {
      return _button('Tab', true);
    }

    // Chips. The base `Chip` is decorative; the interactive variants carry a
    // callback, so a null one means the chip is disabled rather than static.
    if (widget is ActionChip) {
      return _button('ActionChip', widget.onPressed != null);
    }
    if (widget is InputChip) {
      return _button('InputChip', widget.isEnabled);
    }
    if (widget is FilterChip) {
      return _button('FilterChip', widget.onSelected != null);
    }
    if (widget is ChoiceChip) {
      return _button('ChoiceChip', widget.onSelected != null);
    }

    // Containers: tappable regions that a more specific target may sit inside.
    // A `ListTile` with a trailing `IconButton` should report the icon button
    // when that is what was pressed, and the tile otherwise.
    if (widget is ListTile) {
      return RecognizedClickTarget(
        tag: 'ListTile',
        precedence: ClickTargetPrecedence.container,
        enabled:
            widget.enabled &&
            (widget.onTap != null || widget.onLongPress != null),
      );
    }
    if (widget is BottomNavigationBar) {
      return const RecognizedClickTarget(
        tag: 'BottomNavigationBar',
        precedence: ClickTargetPrecedence.container,
      );
    }
    if (widget is NavigationBar) {
      return const RecognizedClickTarget(
        tag: 'NavigationBar',
        precedence: ClickTargetPrecedence.container,
      );
    }

    // The generic tap primitives, last so a recognized widget built from them
    // reports its own name. Both are containers: `InkWell` is what a custom
    // button is usually made of, and a bare `GestureDetector` often wraps
    // something more descriptive.
    if (widget is InkResponse) {
      // Covers `InkWell`, which extends `InkResponse`.
      final tag = widget is InkWell ? 'InkWell' : 'InkResponse';
      return RecognizedClickTarget(
        tag: tag,
        precedence: ClickTargetPrecedence.container,
        enabled:
            widget.onTap != null ||
            widget.onDoubleTap != null ||
            widget.onLongPress != null,
      );
    }
    if (widget is GestureDetector) {
      return RecognizedClickTarget(
        tag: 'GestureDetector',
        precedence: ClickTargetPrecedence.container,
        enabled:
            widget.onTap != null ||
            widget.onTapUp != null ||
            widget.onDoubleTap != null ||
            widget.onLongPress != null,
      );
    }

    return null;
  }

  /// A widget whose visible content is its label, so text inside it names it.
  static RecognizedClickTarget _button(String tag, bool enabled) =>
      RecognizedClickTarget(
        tag: tag,
        precedence: ClickTargetPrecedence.specific,
        enabled: enabled,
        allowsInnerText: true,
      );

  /// A widget whose state is not a label, so inner text must not describe it.
  static RecognizedClickTarget _control(String tag, bool enabled) =>
      RecognizedClickTarget(
        tag: tag,
        precedence: ClickTargetPrecedence.specific,
        enabled: enabled,
      );
}
