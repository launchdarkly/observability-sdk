import 'package:flutter/material.dart' show TextField, Tooltip;
import 'package:flutter/widgets.dart';

import '../../masking.dart';
import 'click_path.dart';
import 'click_widget_registry.dart';
import 'ld_click.dart';

/// The widget a tap resolved to, ready to be reported as a click.
class ClickTarget {
  /// Widget type name for `event.tag` and the replay `clickTarget`.
  final String tag;

  /// Stable identifier for `event.id` and the replay `clickSelector`, when one
  /// could be determined.
  final String? id;

  /// Visible label for `event.text` and the replay `clickTextContent`.
  final String? text;

  /// Widget ancestry path for `event.xpath`.
  final String? path;

  /// Attributes contributed by an enclosing [LDClick].
  final Map<String, Object?>? properties;

  const ClickTarget({
    required this.tag,
    this.id,
    this.text,
    this.path,
    this.properties,
  });
}

/// Resolves a tap coordinate to the widget that was pressed.
///
/// Only Dart can answer this question: Flutter paints its whole UI into one
/// native view, so a native hit-test names that view and nothing else. And only
/// the *element* tree can answer it in release builds — `RenderObject`'s single
/// link back to its widget, `debugCreator`, is debug-only, so a render-tree hit
/// test cannot recover widget types where it matters most. This walks elements
/// and tests the tap point against each `RenderBox`, which yields real types,
/// keys, and marker widgets in every build mode.
///
/// Three details of the walk are load-bearing:
///
/// **Descend with `debugVisitOnstageChildren`.** A pushed route does not remove
/// the routes underneath it from the element tree; it only stops painting them.
/// Their paint bounds still contain the tap point, so a plain `visitChildren`
/// walk would resolve a tap on a dialog to whatever button sits behind it.
/// Despite its name that method is plain release-mode code whose entire purpose
/// is that `Navigator` and `Overlay` elements override it to skip buried routes.
///
/// **No second hit test.** Matching against a fresh `BoxHitTestResult` would get
/// pointer semantics exactly right, but re-running `hitTest` propagates into
/// descendant render objects and overwrites state they recorded during the real
/// pointer-down — the bug that made Sentry remove their hit test after it broke
/// `flutter_map`. Paint bounds ignore pointer semantics, so [IgnorePointer] and
/// [AbsorbPointer] are honored explicitly instead.
///
/// **Topmost first, outermost wins, then refine.** Children are visited in
/// reverse paint order so a widget drawn on top of an overlapping sibling is
/// considered first. Going down, the first recognized widget wins, which yields
/// `ElevatedButton` rather than the `InkWell` it is built from. Widgets that only
/// make a region tappable ([ClickTargetPrecedence.container]) are held as a
/// fallback so a real button nested inside them — an `IconButton` trailing a
/// `ListTile` — is reported instead when there is one.
class ClickTargetResolver {
  const ClickTargetResolver({this.customResolver, this.captureText = true});

  /// Application hook for recognizing its own widget types.
  final LDClickTargetResolver? customResolver;

  /// Whether the target's visible text may be read, from
  /// `PrivacyOptions.maskClickText`.
  final bool captureText;

  /// Depth at which the walk gives up.
  ///
  /// Real trees are deep — a Material screen easily nests a hundred elements —
  /// but not unboundedly so, and a tap must never become an expensive frame. Far
  /// past any legitimate depth, so hitting it means something pathological.
  static const _maxDepth = 500;

  /// Longest reported text, matching Android's `MAX_TEXT_LENGTH`.
  static const _maxTextLength = 2000;

  /// Resolves the widget at [globalPosition], searching under [root].
  ///
  /// Returns null when nothing there can be described as a click: empty space, a
  /// disabled button, a widget type nobody recognizes. Reporting a click for
  /// those would be worse than reporting none, since a disabled button that
  /// "clicks" is indistinguishable in analytics from one that works.
  ///
  /// Never throws. This runs from the app's real gesture path, so an exception
  /// escaping here would surface to the user as a broken tap; failures are
  /// reported to [FlutterError] and the tap is simply not described.
  ClickTarget? resolve(Element root, Offset globalPosition) {
    try {
      return _resolve(root, globalPosition);
    } catch (error, stackTrace) {
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          stack: stackTrace,
          library: 'launchdarkly_flutter_observability',
          context: ErrorDescription('resolving the widget under a tap'),
        ),
      );
      return null;
    }
  }

  ClickTarget? _resolve(Element root, Offset globalPosition) {
    _Candidate? specific;
    _Candidate? container;
    final ancestry = <String>[];
    var done = false;

    // The hook is application code, called for every widget above the tap. One
    // that throws would otherwise throw dozens of times per tap, so it is
    // reported once and stood down for the rest of this resolution.
    var hook = customResolver;
    RecognizedClickTarget? recognize(Widget widget) {
      final resolver = hook;
      if (resolver == null) {
        return ClickWidgetRegistry.recognize(widget);
      }
      try {
        return ClickWidgetRegistry.recognize(widget, customResolver: resolver);
      } catch (error, stackTrace) {
        hook = null;
        FlutterError.reportError(
          FlutterErrorDetails(
            exception: error,
            stack: stackTrace,
            library: 'launchdarkly_flutter_observability',
            context: ErrorDescription(
              'calling the custom click target resolver for '
              '${widget.runtimeType}',
            ),
          ),
        );
        return ClickWidgetRegistry.recognize(widget);
      }
    }

    void visit(Element element, _Descent descent) {
      if (done || descent.depth > _maxDepth) {
        return;
      }
      final widget = element.widget;

      // Nothing here is painted, so nothing here was pressed.
      if (_paintsNothing(widget)) {
        return;
      }
      // The subtree is transparent to pointers: the tap went past it, and paint
      // bounds alone would not know that.
      if (widget is IgnorePointer && widget.ignoring) {
        return;
      }

      if (!_containsPoint(element, globalPosition)) {
        return;
      }

      final recognized = recognize(widget);
      final segment = _pathSegment(widget, recognized);
      if (segment != null) {
        ancestry.add(segment);
      }

      final inherited = descent.inherit(widget);

      if (recognized != null && recognized.enabled) {
        final candidate = _Candidate(
          element: element,
          recognized: recognized,
          descent: inherited,
          ancestry: List.of(ancestry),
        );
        switch (recognized.precedence) {
          case ClickTargetPrecedence.specific:
            specific = candidate;
            done = true;
          case ClickTargetPrecedence.container:
            // Keep the outermost one: it is the widget the author made tappable,
            // whereas a deeper container is usually what it is built from.
            container ??= candidate;
        }
      }

      // An absorbing AbsorbPointer swallows the tap, so nothing inside it — or
      // behind it — was pressed. Its own subtree is where the walk stops.
      if (!done && !(widget is AbsorbPointer && widget.absorbing)) {
        final children = <Element>[];
        element.debugVisitOnstageChildren(children.add);
        // Reverse paint order: later siblings are drawn over earlier ones, so the
        // last child is the topmost and gets the tap.
        for (final child in children.reversed) {
          visit(child, inherited);
          if (done) {
            break;
          }
        }
      }

      if (segment != null) {
        ancestry.removeLast();
      }
    }

    visit(root, const _Descent());

    final winner = specific ?? container;
    if (winner == null) {
      return null;
    }
    return _describe(winner);
  }

  ClickTarget _describe(_Candidate candidate) {
    final recognized = candidate.recognized;
    final descent = candidate.descent;
    final extracted = captureText && !descent.masked
        ? _extractText(candidate.element)
        : const _ExtractedText();

    final id =
        descent.markerId ??
        recognized.id ??
        descent.semanticsIdentifier ??
        _valueKey(candidate.element.widget);

    final text =
        recognized.overrideText ??
        (recognized.allowsInnerText ? extracted.text : null) ??
        extracted.semanticsLabel ??
        descent.semanticsLabel ??
        extracted.iconLabel ??
        extracted.tooltip ??
        descent.tooltip ??
        recognized.fallbackText;

    return ClickTarget(
      tag: recognized.tag,
      id: id,
      text: _clampText(text),
      path: ClickPath.build(candidate.ancestry, id: id),
      properties: descent.markerProperties,
    );
  }

  /// Reads the label describing [target] from its own subtree.
  ///
  /// Sources are collected rather than returned on sight because which one is
  /// preferred depends on the target: a button's content *is* its label, while a
  /// container's content is an arbitrary fragment of a row, so only semantic
  /// sources may describe it. That distinction is what stops a tapped `ListTile`
  /// from reporting whichever word happened to be laid out under the finger.
  _ExtractedText _extractText(Element target) {
    String? text;
    String? semanticsLabel;
    String? iconLabel;
    String? tooltip;

    void visit(Element element, int depth) {
      if (depth > _maxDepth) {
        return;
      }
      final widget = element.widget;

      if (_paintsNothing(widget)) {
        return;
      }
      // Redacted in replay, so redacted here: the whole point of the marker is
      // that this content never leaves the device.
      if (widget is LDMask || widget is LDIgnore) {
        return;
      }
      if (widget is EditableText) {
        // A field's contents are the user's, never a label. `TextField` below
        // supplies the placeholder instead, which is the widget's own text.
        return;
      }
      if (widget is TextField) {
        if (widget.obscureText) {
          // A password field: even its decoration sits in a context we should
          // not describe.
          return;
        }
        final decoration = widget.decoration;
        text ??= decoration?.hintText ?? decoration?.labelText;
        return;
      }
      if (widget is Text) {
        text ??= widget.data ?? widget.textSpan?.toPlainText();
      } else if (widget is Semantics) {
        semanticsLabel ??= widget.properties.label;
      } else if (widget is Icon) {
        iconLabel ??= widget.semanticLabel;
      } else if (widget is Tooltip) {
        tooltip ??= widget.message;
      }

      element.debugVisitOnstageChildren((child) => visit(child, depth + 1));
    }

    visit(target, 0);
    return _ExtractedText(
      text: text,
      semanticsLabel: semanticsLabel,
      iconLabel: iconLabel,
      tooltip: tooltip,
    );
  }

  /// Whether [element] paints over [globalPosition].
  ///
  /// An element without a box of its own (a `StatelessWidget`, a sliver) cannot
  /// be tested, so the walk descends into it instead of pruning — pruning on an
  /// untestable element would drop everything below it.
  static bool _containsPoint(Element element, Offset globalPosition) {
    final renderObject = element.renderObject;
    if (renderObject is! RenderBox) {
      return true;
    }
    if (!renderObject.attached || !renderObject.hasSize) {
      return false;
    }
    return renderObject.size.contains(
      renderObject.globalToLocal(globalPosition),
    );
  }

  /// `true` when [widget] contributes nothing to the frame, so neither it nor
  /// its subtree can have been pressed. Mirrors the equivalent prune in the
  /// session-replay mask collector.
  static bool _paintsNothing(Widget widget) {
    if (widget is Offstage) return widget.offstage;
    if (widget is Opacity) return widget.opacity <= 0.0;
    if (widget is Visibility) return !widget.visible;
    return false;
  }

  /// The name [widget] contributes to `event.xpath`, or null when it should not
  /// appear in the path.
  static String? _pathSegment(Widget widget, RecognizedClickTarget? recognized) {
    if (recognized != null) {
      return recognized.tag;
    }
    final name = widget.runtimeType.toString();
    // Framework internals. Their names are also the first casualty of
    // `--obfuscate`, so they would be noise either way.
    if (name.startsWith('_') || _pathNoise.contains(name)) {
      return null;
    }
    return name;
  }

  /// Widgets left out of `event.xpath` because they say nothing about *where* a
  /// tap landed.
  ///
  /// Every Flutter tree is mostly plumbing — theme and media-query providers,
  /// builders, focus and semantics wrappers, single-child layout and painting
  /// boxes. Left in, they crowd out the handful of segments that actually locate
  /// the target: a path reads `AnimatedBuilder/Actions/MediaQuery/KeyedSubtree/…`
  /// and the screen and list it came from have been pushed past the segment cap.
  ///
  /// Deliberately a denylist of framework types rather than an allowlist, so an
  /// application's own widgets — `CheckoutPage`, `ProductRow`, the names worth
  /// having — always survive. Missing an entry only leaves noise in, never wrong
  /// data, and multi-child widgets that describe structure (`Row`, `Column`,
  /// `Stack`, `ListView`, `Scaffold`) are kept on purpose.
  static const _pathNoise = <String>{
    // Inherited providers and configuration.
    'MediaQuery', 'Theme', 'AnimatedTheme', 'IconTheme', 'Directionality',
    'Localizations', 'DefaultTextStyle', 'AnimatedDefaultTextStyle',
    'DefaultSelectionStyle', 'ScrollConfiguration', 'PrimaryScrollController',
    'TickerMode', 'HeroControllerScope', 'DefaultTextEditingShortcuts',
    // Builders: their child is the interesting part.
    'Builder', 'LayoutBuilder', 'AnimatedBuilder', 'ValueListenableBuilder',
    'StreamBuilder', 'FutureBuilder', 'OrientationBuilder',
    // Focus, shortcuts, semantics, notifications.
    'Focus', 'FocusScope', 'FocusTraversalGroup', 'Actions', 'Shortcuts',
    'Semantics', 'MergeSemantics', 'ExcludeSemantics', 'BlockSemantics',
    'NotificationListener', 'Listener', 'MouseRegion', 'AbsorbPointer',
    'IgnorePointer', 'KeyedSubtree', 'RepaintBoundary',
    // Single-child layout, sizing, and painting.
    'Padding', 'Align', 'Center', 'SizedBox', 'ConstrainedBox', 'LimitedBox',
    'UnconstrainedBox', 'FractionallySizedBox', 'AspectRatio', 'IntrinsicWidth',
    'IntrinsicHeight', 'Baseline', 'Offstage', 'Visibility', 'SafeArea',
    'Container', 'DecoratedBox', 'ColoredBox', 'ClipRect', 'ClipRRect',
    'ClipOval', 'ClipPath', 'PhysicalModel', 'PhysicalShape', 'Opacity',
    'Transform', 'FractionalTranslation', 'CustomPaint',
    'CustomSingleChildLayout', 'CustomMultiChildLayout', 'LayoutId',
    'Positioned', 'Flexible', 'Expanded', 'Spacer', 'Material',
    // This SDK's own wrappers, which sit above every path by construction.
    'SessionReplayCapture', 'LDClickDetector', 'NativeSessionReplayCapture',
    'LDMask', 'LDUnmask', 'LDIgnore', 'LDClick',
  };

  /// A [ValueKey]'s value as an identifier. Other key types are deliberately
  /// ignored: a `GlobalKey`, `UniqueKey`, or `ObjectKey` stringifies to
  /// something that changes between runs, which would fragment grouping instead
  /// of enabling it.
  static String? _valueKey(Widget widget) {
    final key = widget.key;
    return key is ValueKey ? key.value?.toString() : null;
  }

  static String? _clampText(String? text) {
    if (text == null) {
      return null;
    }
    final trimmed = text.trim();
    if (trimmed.isEmpty) {
      return null;
    }
    return trimmed.length > _maxTextLength
        ? trimmed.substring(0, _maxTextLength)
        : trimmed;
  }
}

/// A recognized widget the tap landed on, with the context it was found in.
class _Candidate {
  final Element element;
  final RecognizedClickTarget recognized;
  final _Descent descent;
  final List<String> ancestry;

  const _Candidate({
    required this.element,
    required this.recognized,
    required this.descent,
    required this.ancestry,
  });
}

/// Context accumulated on the way down to a candidate: everything an *ancestor*
/// contributes to describing the tap.
///
/// Immutable and passed by value, so a sibling branch can never inherit
/// something picked up in the branch beside it.
class _Descent {
  final int depth;

  /// Nearest enclosing [LDClick]'s id, the most explicit identifier there is.
  final String? markerId;

  final Map<String, Object?>? markerProperties;

  /// Nearest enclosing `Semantics.identifier` — the same accessibility
  /// identifier concept native uses for `event.id` on iOS and Compose.
  final String? semanticsIdentifier;

  final String? semanticsLabel;

  final String? tooltip;

  /// Whether the tap landed inside an [LDMask]/[LDIgnore] subtree, whose content
  /// must not be described.
  final bool masked;

  const _Descent({
    this.depth = 0,
    this.markerId,
    this.markerProperties,
    this.semanticsIdentifier,
    this.semanticsLabel,
    this.tooltip,
    this.masked = false,
  });

  /// The context seen by [widget]'s children. Inner values win over outer ones:
  /// the marker or label closest to the target describes it most precisely.
  _Descent inherit(Widget widget) {
    if (widget is LDClick) {
      return _copyWith(
        markerId: widget.id,
        markerProperties: widget.properties,
      );
    }
    if (widget is LDMask || widget is LDIgnore) {
      return _copyWith(masked: true);
    }
    if (widget is Semantics) {
      final properties = widget.properties;
      return _copyWith(
        semanticsIdentifier: properties.identifier,
        semanticsLabel: properties.label,
      );
    }
    if (widget is Tooltip) {
      return _copyWith(tooltip: widget.message);
    }
    return _copyWith();
  }

  _Descent _copyWith({
    String? markerId,
    Map<String, Object?>? markerProperties,
    String? semanticsIdentifier,
    String? semanticsLabel,
    String? tooltip,
    bool? masked,
  }) => _Descent(
    depth: depth + 1,
    markerId: markerId ?? this.markerId,
    markerProperties: markerProperties ?? this.markerProperties,
    semanticsIdentifier: semanticsIdentifier ?? this.semanticsIdentifier,
    semanticsLabel: semanticsLabel ?? this.semanticsLabel,
    tooltip: tooltip ?? this.tooltip,
    masked: masked ?? this.masked,
  );
}

/// Label sources found inside a target, ranked by the caller.
class _ExtractedText {
  final String? text;
  final String? semanticsLabel;
  final String? iconLabel;
  final String? tooltip;

  const _ExtractedText({
    this.text,
    this.semanticsLabel,
    this.iconLabel,
    this.tooltip,
  });
}
