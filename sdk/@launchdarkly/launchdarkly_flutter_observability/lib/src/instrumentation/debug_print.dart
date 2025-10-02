import 'package:flutter/foundation.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/instrumentation.dart';

import '../plugin/observability_config.dart';
import '../observe.dart';

class DebugPrintInstrumentation implements Instrumentation {
  DebugPrintCallback? _originalCallback;

  DebugPrintInstrumentation(InstrumentationConfig config) {
    switch (config.debugPrint) {
      case DebugPrintReleaseOnly():
        if (!kReleaseMode) {
          return;
        }
      case DebugPrintAlways():
        break;
      case DebugPrintDisabled():
        return;
    }
    _instrument();
  }

  void _instrument() {
    _originalCallback = debugPrint;

    debugPrint = (String? message, {int? wrapWidth}) {
      if (message != null) {
        Observe.recordLog(message, severity: 'debug');
      }
    };
  }

  @override
  void dispose() {
    if (_originalCallback != null) {
      debugPrint = _originalCallback!;
      _originalCallback = null;
    }
  }
}
