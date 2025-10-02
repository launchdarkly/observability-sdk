import 'package:flutter/scheduler.dart';

import '../../api/span_status_code.dart';
import '../../observe.dart';
import '../instrumentation.dart';
import './lifecycle_conventions.dart';

import 'platform/stub_lifecycle_listener.dart'
    if (dart.library.io) 'platform/io_lifecycle_listener.dart'
    if (dart.library.js_interop) 'platform/js_lifecycle_listener.dart';

final class LifecycleInstrumentation implements Instrumentation {
  late final LDAppLifecycleListener _lifecycleListener;

  LifecycleInstrumentation() {
    final initialState = SchedulerBinding.instance.lifecycleState;
    if (initialState != null) {
      _handleApplicationLifecycle(initialState);
    }

    _lifecycleListener = LDAppLifecycleListener();
    _lifecycleListener.stream.listen(_handleApplicationLifecycle);
  }

  /// The application lifecycle is as follows.
  /// Diagram based on: https://api.flutter.dev/flutter/widgets/AppLifecycleListener-class.html
  /// +-----------+       onStart             +-----------+
  /// |           +--------------------------->           |
  /// | Detached  |                           | Resumed   |
  /// |           |                           |           |
  /// +--------^--+                           +-^-------+-+
  ///          |                                |       |
  ///          |onDetach              onInactive|       |onResume
  ///          |                                |       |
  ///          |  onPause                       |       |
  /// +--------+--+       +-----------+onHide +-+-------v-+
  /// |           <-------+           <-------+           |
  /// | Paused    |       | Hidden    |       | Inactive  |
  /// |           +------->           +------->           |
  /// +-----------+       +-----------+onShow +-----------+
  ///             onRestart
  ///
  /// On iOS/Android the hidden state is synthesized in the process of pausing,
  /// so it will always hide before being paused. On desktop/web platforms
  /// hidden may happen when the app is covered.
  void _handleApplicationLifecycle(AppLifecycleState state) {
    Observe.startSpan(
        LifecycleConventions.spanName,
        attributes: LifecycleConventions.getAttributes(state: state),
      )
      ..setStatus(SpanStatusCode.ok)
      ..end();
  }

  void dispose() {
    _lifecycleListener.close();
  }
}
