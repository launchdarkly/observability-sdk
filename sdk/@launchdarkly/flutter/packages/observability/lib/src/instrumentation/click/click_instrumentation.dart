import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart' show kLongPressTimeout, kTouchSlop;
import 'package:flutter/widgets.dart';

import '../../otel/setup.dart';
import '../instrumentation.dart';
import 'click_target_resolver.dart';
import 'ld_click.dart';

/// Turns taps into `click` events, resolving the pressed widget in Dart.
///
/// Two halves, deliberately split: this object owns the policy (which widget was
/// pressed, what to report, whether native should stand down) while
/// [LDClickDetector] — installed by `SessionReplayCapture` — owns the pointers.
/// The detector lives and dies with the widget tree, and there may be no tree at
/// all when the plugin boots, so the two are connected through
/// [ClickInstrumentation.active] rather than by construction.
///
/// That split is also what makes the native handshake honest. Native suppresses
/// its own Flutter-view taps only while a detector is mounted, so an app that
/// never wraps its tree keeps the coarse native clicks it has today instead of
/// silently reporting none.
final class ClickInstrumentation implements Instrumentation {
  /// The installed instrumentation, or null when clicks are not being captured
  /// (`analytics.taps` is off, or the pipeline has not booted).
  static ClickInstrumentation? _active;

  /// The installed instrumentation, or null when clicks are not being captured.
  static ClickInstrumentation? get active => _active;

  /// Mounted detectors.
  ///
  /// Static, and deliberately not owned by the instrumentation: the widget tree
  /// is usually built by `runApp` before `LDObserve.init` finishes booting, so
  /// the detector mounts first and there is nothing to register with yet. Losing
  /// that would be the worst of both worlds — Dart reporting rich clicks while
  /// native, never told to stand down, reports the same taps as `FlutterView`.
  ///
  /// A count rather than a flag because a rebuild can mount the replacement
  /// detector before unmounting the old one.
  static int _detectors = 0;

  final ClickTargetResolver _resolver;

  /// Whether a target's visible text may be reported.
  @visibleForTesting
  bool get captureText => _resolver.captureText;

  bool _disposed = false;

  /// Creates the instrumentation and installs it as [active].
  ///
  /// [customResolver] recognizes application widget types, and [captureText]
  /// controls whether a target's visible text may be reported.
  ClickInstrumentation({
    LDClickTargetResolver? customResolver,
    bool captureText = true,
  }) : _resolver = ClickTargetResolver(
         customResolver: customResolver,
         captureText: captureText,
       ) {
    _active = this;
    if (_detectors > 0) {
      // A detector is already live, from a tree built before the plugin booted.
      _announce(true);
    }
  }

  /// Reports that a click detector has mounted, so native can stop describing
  /// taps that land on the Flutter view.
  static void attachDetector() {
    _detectors++;
    if (_detectors == 1) {
      _active?._announce(true);
    }
  }

  /// Reports that a detector has unmounted. When it was the last one, native
  /// resumes its own tap reporting: coarse, but better than nothing.
  static void detachDetector() {
    if (_detectors == 0) {
      return;
    }
    _detectors--;
    if (_detectors == 0) {
      _active?._announce(false);
    }
  }

  void _announce(bool handlingClicks) {
    if (!_disposed) {
      Otel.clickRecorder?.setEmbedderClickHandling(handlingClicks);
    }
  }

  /// Resolves and reports a completed tap at [globalPosition], searching the
  /// element tree under [root].
  ///
  /// [timestampMillis] is captured at pointer-up rather than here: the native
  /// bridge is asynchronous, and a replay click marker stamped on arrival would
  /// drift away from the pointer trail it belongs to.
  void reportTap({
    required Element root,
    required Offset globalPosition,
    required double devicePixelRatio,
    required int timestampMillis,
  }) {
    final recorder = Otel.clickRecorder;
    if (_disposed || recorder == null) {
      return;
    }

    final target = _resolver.resolve(root, globalPosition);
    if (target == null) {
      // Empty space, a disabled control, or a widget nothing can describe.
      // Reporting a click here would be indistinguishable from a real one.
      return;
    }

    final coordinates = _coordinates(globalPosition, devicePixelRatio);

    recorder.trackClick(
      id: target.id,
      tag: target.tag,
      // `event.classname` stays null. Native fills it with a fully-qualified
      // class name, which Flutter has no equivalent of: the only candidate is the
      // runtime type, which `--obfuscate` mangles and which would just repeat
      // `event.tag` when it doesn't. `event.xpath` carries the disambiguating
      // detail instead.
      text: target.text,
      xpath: target.path,
      x: coordinates.dx.round(),
      y: coordinates.dy.round(),
      timestampMillis: timestampMillis,
      properties: target.properties,
    );
  }

  static Offset _coordinates(Offset logical, double devicePixelRatio) =>
      logical * platformScale(devicePixelRatio);

  /// The factor converting Flutter logical pixels into the units each platform
  /// reports `event.x`/`event.y` in, so a Flutter click sits in the same
  /// coordinate space as a native one on the same device. Shared by automatic
  /// capture and `LDObserve.trackClick`.
  ///
  /// Android's native taps come from `MotionEvent`, in physical pixels; iOS uses
  /// UIKit points, which are Flutter logical pixels already, as is web.
  static double platformScale(double devicePixelRatio) =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android
      ? devicePixelRatio
      : 1.0;

  @override
  void dispose() {
    if (_disposed) {
      return;
    }
    if (_detectors > 0) {
      // Hand tap reporting back to native: Dart is about to stop describing
      // clicks, and the detector may well outlive this instrumentation.
      _announce(false);
    }
    _disposed = true;
    if (_active == this) {
      _active = null;
    }
  }
}

/// Watches pointers over its subtree and hands completed taps to
/// [ClickInstrumentation].
///
/// Installed by `SessionReplayCapture` rather than by the app, so click capture
/// costs no extra wrapper — the same packaging Datadog settled on, whose docs
/// tell users to *remove* their standalone detector in favor of the one the
/// capture widget embeds.
///
/// The [Listener] is translucent and registers no gesture recognizers, so it
/// observes pointers without entering the gesture arena: it cannot win, lose, or
/// delay a tap the app is handling.
class LDClickDetector extends StatefulWidget {
  /// The subtree whose taps are observed.
  final Widget child;

  /// Creates a detector observing taps on [child].
  const LDClickDetector({super.key, required this.child});

  @override
  State<LDClickDetector> createState() => _LDClickDetectorState();
}

class _LDClickDetectorState extends State<LDClickDetector> {
  /// The pointer that may still turn out to be a tap.
  ///
  /// Single, not a map: a second finger going down means a pinch or a two-finger
  /// scroll, and neither is a click.
  int? _pointer;
  Offset? _downPosition;
  Duration? _downTime;

  @override
  void initState() {
    super.initState();
    ClickInstrumentation.attachDetector();
  }

  @override
  void dispose() {
    ClickInstrumentation.detachDetector();
    super.dispose();
  }

  void _handlePointerDown(PointerDownEvent event) {
    if (_pointer != null) {
      // Multi-touch. Abandon the first pointer instead of resolving it on the
      // way up, since whatever gesture this is, it is not a click.
      _pointer = null;
      _downPosition = null;
      _downTime = null;
      return;
    }
    _pointer = event.pointer;
    _downPosition = event.position;
    _downTime = event.timeStamp;
  }

  void _handlePointerUp(PointerUpEvent event) {
    final pointer = _pointer;
    final downPosition = _downPosition;
    final downTime = _downTime;
    _pointer = null;
    _downPosition = null;
    _downTime = null;

    if (pointer != event.pointer || downPosition == null || downTime == null) {
      return;
    }

    final instrumentation = ClickInstrumentation.active;
    if (instrumentation == null) {
      return;
    }

    // A drag is not a click. `kTouchSlop` is the same threshold the framework's
    // own tap recognizer uses to give up on a tap.
    if ((event.position - downPosition).distance > kTouchSlop) {
      return;
    }
    // Nor is a long press. Neither Sentry nor Datadog check duration, so a
    // motionless five-second press reports as a click for them; the native LD
    // SDKs do require the press to complete inside the long-press timeout, and
    // matching native matters more than matching them.
    if (event.timeStamp - downTime > kLongPressTimeout) {
      return;
    }

    instrumentation.reportTap(
      root: context as Element,
      globalPosition: event.position,
      devicePixelRatio: View.of(context).devicePixelRatio,
      // Wall-clock now, at pointer-up: see `reportTap`.
      timestampMillis: DateTime.now().millisecondsSinceEpoch,
    );
  }

  void _handlePointerCancel(PointerCancelEvent event) {
    if (_pointer == event.pointer) {
      _pointer = null;
      _downPosition = null;
      _downTime = null;
    }
  }

  @override
  Widget build(BuildContext context) => Listener(
    behavior: HitTestBehavior.translucent,
    onPointerDown: _handlePointerDown,
    onPointerUp: _handlePointerUp,
    onPointerCancel: _handlePointerCancel,
    child: widget.child,
  );
}
