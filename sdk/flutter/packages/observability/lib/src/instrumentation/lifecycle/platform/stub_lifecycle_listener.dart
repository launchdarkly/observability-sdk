import 'package:flutter/widgets.dart';

/// Placeholder lifecycle listener for platforms without `dart:io` or
/// `dart:js_interop`; every member throws.
class LDAppLifecycleListener {
  /// Broadcast stream of application lifecycle state changes.
  Stream<AppLifecycleState> get stream =>
      throw Exception('Stub implementation');

  /// Closes [stream] and stops listening for lifecycle changes.
  void close() {
    throw Exception('Stub implementation');
  }
}
