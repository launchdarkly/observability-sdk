import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

import 'native_crash.dart';

/// Catalog of error / crash scenarios the example app can produce on demand,
/// mirroring the Swift TestApp's `CrashScenarios.swift`.
///
/// Each scenario is triggered in one of two modes (see [CrashMode]):
/// - [CrashMode.error] catches the failure and reports it via
///   `LDObserve.recordException`. Only available for scenarios that are
///   catchable — see [CrashScenario.supportsHandled].
/// - [CrashMode.crash] lets the failure go unhandled by the app.
///
/// What "unhandled" means differs from Apple. A Dart throw does not terminate a
/// Flutter app: the framework catches it and routes it to `FlutterError.onError`
/// (widget/framework callbacks) or to the `runZonedGuarded` handler (async
/// errors), both wired up in `main.dart`. Only the `Native: …` scenarios really
/// terminate the process ([CrashScenario.isFatal]); those are captured by the
/// native crash reporter and delivered on the next launch.
///
/// Note: `assert` is compiled out of release builds, so the assertion scenario
/// does nothing there. When validating symbolication (an obfuscated release
/// build, see SYMBOLICATION.md), prefer any of the other scenarios.
enum CrashScenario {
  // Dual-mode: catchable Dart failures, so both Error and Crash are meaningful.
  exception('Exception', supportsHandled: true),
  stateError('StateError', supportsHandled: true),
  argumentError('ArgumentError', supportsHandled: true),
  typeError('Type cast failure (TypeError)', supportsHandled: true),
  nullCheck('Null check operator on null (!)', supportsHandled: true),
  rangeError('List index out of range (RangeError)', supportsHandled: true),
  formatException(
    'JSON decoding failure (FormatException)',
    supportsHandled: true,
  ),
  noSuchMethodError('NoSuchMethodError (dynamic call)', supportsHandled: true),
  assertionError('assert() (debug only)', supportsHandled: true),
  stackOverflow('Stack overflow (recursion)', supportsHandled: true),
  asyncError('Async: error in an awaited Future', supportsHandled: true),
  platformChannel(
    'Platform channel: MissingPluginException',
    supportsHandled: true,
  ),

  // Crash-only: the failure is raised where the app cannot catch it, so it is
  // reported by the framework/zone handlers instead.
  unawaitedFuture('Unawaited Future error'),
  timerCallback('Timer callback error'),
  widgetBuild('Exception in a widget build()'),
  isolate('Isolate: uncaught error', availableOnWeb: false),

  // Crash-only and fatal: these terminate the process, so the report comes from
  // the native crash reporter on the next launch.
  nativeBadAccess(
    'Native: bad memory access (SIGSEGV)',
    availableOnWeb: false,
    isFatal: true,
  ),
  nativeAbort(
    'Native: abort() (SIGABRT)',
    availableOnWeb: false,
    isFatal: true,
  ),
  nativeIllegalInstruction(
    'Native: raise(SIGILL)',
    availableOnWeb: false,
    isFatal: true,
  );

  const CrashScenario(
    this.label, {
    this.supportsHandled = false,
    this.isFatal = false,
    this.availableOnWeb = true,
  });

  /// Human readable name shown in the scenario picker.
  final String label;

  /// Whether the failure can be caught with `try`/`catch` and reported as a
  /// handled error. Failures raised inside framework callbacks, other isolates
  /// or the native runtime cannot be, so they are crash-only.
  final bool supportsHandled;

  /// Whether the scenario terminates the process.
  final bool isFatal;

  /// Whether the scenario can run on web. `dart:ffi` and `dart:isolate` are not
  /// available there.
  final bool availableOnWeb;

  bool get isAvailable => availableOnWeb || !kIsWeb;
}

/// How a [CrashScenario] is triggered.
enum CrashMode {
  /// Catch the failure and report it through `LDObserve.recordException`.
  error,

  /// Let the failure go unhandled by the app.
  crash,
}

/// Consumes the result of a failing expression, so neither the analyzer nor the
/// AOT compiler discards it as dead code. Only runs if a scenario unexpectedly
/// succeeded.
void _use(Object? value) => debugPrint('Scenario did not fail, got: $value');

/// Produces [scenario]. In [CrashMode.error] a catchable scenario is caught and
/// reported (this returns normally); otherwise the failure is left to the
/// framework handlers, or terminates the process for a fatal scenario.
///
/// Callers should not await the returned future: in [CrashMode.crash] the error
/// must escape to the `runZonedGuarded` handler in `main.dart`.
Future<void> triggerScenario(
  CrashScenario scenario, {
  required CrashMode mode,
  required BuildContext context,
}) async {
  if (mode == CrashMode.error && scenario.supportsHandled) {
    try {
      await _runCatchable(scenario);
    } catch (error, stackTrace) {
      LDObserve.recordException(
        error,
        stackTrace: stackTrace,
        properties: <String, Object?>{'scenario': scenario.name},
      );
      debugPrint('Recorded ${error.runtimeType} for "${scenario.label}"');
    }
    return;
  }

  switch (scenario) {
    // Thrown with nothing in the way: the error leaves the unawaited future
    // returned by this function and reaches the guarded zone in `main.dart`.
    case CrashScenario.exception:
    case CrashScenario.stateError:
    case CrashScenario.argumentError:
    case CrashScenario.typeError:
    case CrashScenario.nullCheck:
    case CrashScenario.rangeError:
    case CrashScenario.formatException:
    case CrashScenario.noSuchMethodError:
    case CrashScenario.assertionError:
    case CrashScenario.stackOverflow:
    case CrashScenario.asyncError:
    case CrashScenario.platformChannel:
      await _runCatchable(scenario);

    case CrashScenario.unawaitedFuture:
      // `unawaited` documents that nobody handles this future; its error still
      // surfaces as an unhandled async error in the guarded zone.
      unawaited(
        Future<void>.delayed(const Duration(milliseconds: 50), () {
          throw StateError('Unawaited future failed');
        }),
      );

    case CrashScenario.timerCallback:
      // A throw from a timer callback runs on the event loop, outside of any
      // `try`/`catch`, and is reported by the zone handler.
      Timer(const Duration(milliseconds: 50), () {
        throw StateError('Timer callback failed');
      });

    case CrashScenario.widgetBuild:
      // A build failure is caught by the framework and reported through
      // `FlutterError.onError`; the route shows Flutter's error widget.
      Navigator.of(
        context,
      ).push(MaterialPageRoute<void>(builder: (_) => const _BrokenPage()));

    case CrashScenario.isolate:
      await crashInIsolate();

    case CrashScenario.nativeBadAccess:
      crashWithBadMemoryAccess();

    case CrashScenario.nativeAbort:
      crashWithAbort();

    case CrashScenario.nativeIllegalInstruction:
      crashWithIllegalInstruction();
  }
}

/// The throwing form of every catchable scenario, shared by both modes: in
/// [CrashMode.error] the caller catches and reports the failure, in
/// [CrashMode.crash] it is left to propagate.
Future<void> _runCatchable(CrashScenario scenario) async {
  switch (scenario) {
    case CrashScenario.exception:
      final inner = StateError('The error that caused the other error.');
      throw Exception('Manual error womp womp: $inner');

    case CrashScenario.stateError:
      throw StateError('Failed to connect to bogus server.');

    case CrashScenario.argumentError:
      _use(_parsePositive('-1'));

    case CrashScenario.typeError:
      final Object value = 'not an int';
      _use(value as int);

    case CrashScenario.nullCheck:
      const config = <String, String>{};
      _use(config['missing-key']!);

    case CrashScenario.rangeError:
      final numbers = <int>[1, 2, 3];
      _use(numbers[numbers.length + 5]);

    case CrashScenario.formatException:
      _use(jsonDecode('not json'));

    case CrashScenario.noSuchMethodError:
      final dynamic value = 'not a widget';
      // ignore: avoid_dynamic_calls
      _use(value.missingMethod());

    case CrashScenario.assertionError:
      assert(false, 'Intentional assertion failure');

    case CrashScenario.stackOverflow:
      _use(_infiniteRecursion(0));

    case CrashScenario.asyncError:
      _use(await _failingAsyncWork());

    case CrashScenario.platformChannel:
      // No plugin is registered for this channel, so the invocation fails with
      // a `MissingPluginException` from the platform messenger.
      const channel = MethodChannel('com.example.example/missing');
      _use(await channel.invokeMethod<String>('doesNotExist'));

    // Crash-only scenarios are triggered by `triggerScenario` directly.
    default:
      break;
  }
}

int _parsePositive(String raw) {
  final value = int.parse(raw);
  if (value <= 0) {
    throw ArgumentError.value(value, 'raw', 'must be greater than zero');
  }
  return value;
}

/// Fails after suspending, so the reported stack trace covers an async gap.
Future<int> _failingAsyncWork() async {
  await Future<void>.delayed(const Duration(milliseconds: 50));
  throw TimeoutException('Async work failed after a delay');
}

// Dart does not eliminate tail calls, so this grows the stack until it
// overflows. The `+ depth` also keeps the recursion from being folded away.
int _infiniteRecursion(int depth) => _infiniteRecursion(depth + 1) + depth;

/// A page whose `build` always throws, exercising the `FlutterError.onError`
/// path that reports framework errors.
class _BrokenPage extends StatelessWidget {
  const _BrokenPage();

  @override
  Widget build(BuildContext context) {
    throw StateError('Exception thrown while building BrokenPage');
  }
}
