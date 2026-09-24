import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';

const _launchDarklyLogSpanName = 'launchdarkly.flutter.log';
const _logEventName = 'log';
const _logSeverityAttributeName = 'log.severity';
const _logMessageAttributeName = 'log.message';
const _logStackTrace = 'code.stacktrace';

/// Semantic convention for logs emitted as span events on the Dart pipeline.
class LogConvention {
  /// Builds the log event attributes: `log.message`, `log.severity`, and, when
  /// [stack] is provided, `code.stacktrace`.
  static Map<String, Attribute> getEventAttributes(
    String message,
    String severity,
    StackTrace? stack,
  ) {
    final attributes = <String, Attribute>{
      _logSeverityAttributeName: StringAttribute(severity),
      _logMessageAttributeName: StringAttribute(message),
    };

    if (stack != null) {
      attributes[_logStackTrace] = StringAttribute(stack.toString());
    }

    return attributes;
  }

  /// Name of the span carrying the log event: `launchdarkly.flutter.log`.
  static const spanName = _launchDarklyLogSpanName;

  /// Name of the span event representing the log: `log`.
  static const eventName = _logEventName;
}
