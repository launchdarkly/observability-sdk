import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';

const _launchDarklyLogSpanName = 'launchdarkly.flutter.log';
const _logEventName = 'log';
const _logSeverityAttributeName = 'log.severity';
const _logMessageAttributeName = 'log.message';
const _logStackTrace = 'code.stacktrace';

class LogConvention {
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

  static const spanName = _launchDarklyLogSpanName;
  static const eventName = _logEventName;
}
