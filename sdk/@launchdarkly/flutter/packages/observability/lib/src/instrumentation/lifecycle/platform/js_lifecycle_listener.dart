import 'dart:async';
import 'dart:js_interop';
import 'package:web/web.dart' as web;

import 'package:flutter/widgets.dart';

/// Lifecycle listener that uses the underlying visibility of the html web
/// document to emit events.
class LDAppLifecycleListener {
  late final StreamController<AppLifecycleState> _streamController;

  /// Creates a listener whose [stream] emits lifecycle state changes.
  LDAppLifecycleListener() {
    _streamController = StreamController.broadcast();

    void listenerFunc(web.Event event) => _streamController.add(
      web.document.hidden == true
          ? AppLifecycleState.hidden
          : AppLifecycleState.resumed,
    );

    /// Use a stable reference for the JS listener.
    final listenerJS = listenerFunc.toJS;

    _streamController.onListen = () {
      web.document.addEventListener('visibilitychange', listenerJS);
    };

    _streamController.onCancel = () {
      web.document.removeEventListener('visibilitychange', listenerJS);
    };
  }

  /// Broadcast stream of application lifecycle state changes.
  Stream<AppLifecycleState> get stream => _streamController.stream;

  /// Closes [stream] and stops listening for lifecycle changes.
  void close() {
    _streamController.close();
  }
}
