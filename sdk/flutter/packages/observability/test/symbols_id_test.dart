import 'package:flutter_test/flutter_test.dart';

import 'package:launchdarkly_flutter_observability/src/otel/symbols_id.dart';

/// A stack trace whose string form carries an obfuscation header, standing in
/// for what StackTrace.current produces in a release AOT build.
class _FakeStackTrace implements StackTrace {
  _FakeStackTrace(this._value);
  final String _value;
  @override
  String toString() => _value;
}

void main() {
  group('readSymbolsId', () {
    test('extracts the build id from an obfuscated header', () {
      final trace = _FakeStackTrace('''
*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
pid: 1234, tid: 5678, name io.flutter.ui
os: android arch: arm64 comp: no sim: no
build_id: '0f8a1b2c3d4e5f60718293a4b5c6d7e8'
isolate_dso_base: 7b0000, isolate_instructions: 7b1000
    #00 abs 0000007b8a1e2050 virt 0000000000001050 _kDartIsolateSnapshotInstructions+0x1050
''');
      expect(readSymbolsId(trace), '0f8a1b2c3d4e5f60718293a4b5c6d7e8');
    });

    test('lowercases the id', () {
      final trace = _FakeStackTrace("build_id: 'ABCDEF0123'");
      expect(readSymbolsId(trace), 'abcdef0123');
    });

    test('tolerates an = separator without quotes', () {
      final trace = _FakeStackTrace('build_id = deadbeef');
      expect(readSymbolsId(trace), 'deadbeef');
    });

    test('returns null when there is no obfuscation header', () {
      final trace = _FakeStackTrace(
        '#0      main (package:app/main.dart:10:3)\n'
        '#1      _run (dart:async:100:5)',
      );
      expect(readSymbolsId(trace), isNull);
    });
  });
}
