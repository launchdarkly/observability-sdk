import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';

const _attributeServiceName = 'service.name';
const _attributeServiceVersion = 'service.version';

/// OTel semantic conventions for the `service.*` Resource attributes.
class ServiceConvention {
  /// The `service.name` and `service.version` attributes, omitting any that
  /// are `null`.
  static Map<String, Attribute> getAttributes({
    String? serviceName,
    String? serviceVersion,
  }) {
    final attributes = <String, Attribute>{};
    if (serviceName != null) {
      attributes[_attributeServiceName] = StringAttribute(serviceName);
    }
    if (serviceVersion != null) {
      attributes[_attributeServiceVersion] = StringAttribute(serviceVersion);
    }
    return attributes;
  }
}
