// Native implementation of the fatal and cross-isolate crash scenarios.
//
// The three fatal entry points below raise real signals, so the Dart runtime is
// never involved: the process dies and the report comes from the native crash
// reporter (KSCrash on iOS, the Android SDK's handler) on the next launch. This
// is the only way an example app can exercise that path from Dart.

import 'dart:ffi';
import 'dart:isolate';

import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

/// `SIGILL` on both Darwin and Linux/Android.
const int _sigill = 4;

final DynamicLibrary _process = DynamicLibrary.process();

final void Function() _abort = _process
    .lookupFunction<Void Function(), void Function()>('abort');

final int Function(int) _raise = _process
    .lookupFunction<Int Function(Int), int Function(int)>('raise');

/// Writes through a non-null but unmapped address, faulting with SIGSEGV.
void crashWithBadMemoryAccess() {
  Pointer<Int32>.fromAddress(0x1).value = 42;
}

/// Calls libc `abort()`, raising SIGABRT.
void crashWithAbort() => _abort();

/// Raises SIGILL, the signal an illegal instruction produces.
void crashWithIllegalInstruction() => _raise(_sigill);

/// Fails inside a spawned isolate and forwards the failure.
///
/// An error in another isolate never reaches `FlutterError.onError` or the
/// guarded zone in `main.dart`, so an app that spawns isolates has to listen on
/// an error port and report the failure itself, as this does. The trace arrives
/// as the isolate's own stack trace string, which the backend symbolicates like
/// any other Dart trace.
Future<void> crashInIsolate() async {
  final errors = ReceivePort();
  await Isolate.spawn(
    _throwInIsolate,
    'Uncaught error in a spawned isolate',
    onError: errors.sendPort,
    errorsAreFatal: true,
    debugName: 'ld-crash-scenario',
  );

  final failure = await errors.first as List<Object?>;
  errors.close();

  LDObserve.recordException(
    failure.first,
    stackTrace: StackTrace.fromString(failure.last as String? ?? ''),
    properties: const <String, Object?>{'scenario': 'isolate'},
  );
}

void _throwInIsolate(String message) => throw StateError(message);
