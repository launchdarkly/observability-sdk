// Process-terminating crashes, plus the cross-isolate error case. They need
// `dart:ffi` / `dart:isolate`, which web builds do not have, so the
// implementation is selected at compile time the same way the SDK selects its
// platform code. On web the stub throws and the scenarios are hidden from the
// picker (see `CrashScenario.availableOnWeb`).
export 'native_crash_stub.dart' if (dart.library.io) 'native_crash_io.dart';
