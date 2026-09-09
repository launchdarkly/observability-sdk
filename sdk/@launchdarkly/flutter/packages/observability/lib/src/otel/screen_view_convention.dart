import '../api/attribute.dart';
import 'conversions.dart';

/// Semantic convention for `screen_view` events.
///
/// Mirrors the native iOS/Android observability SDKs, which emit a span named
/// `screen_view` carrying the analytics taxonomy `event.*` fields. User-supplied
/// `properties` are attached as additional attributes, with the reserved
/// `event.*` keys applied last so they can never be clobbered.
///
/// The native `event.previous_screen` field is resolved from a navigation stack
/// the native SDK maintains; the Dart web pipeline does not track one, so it is
/// omitted here.
class ScreenViewConvention {
  /// The span name. Matches the native exporters
  /// (`SemanticConvention.screenViewSpanName` / `SCREEN_VIEW_SPAN_NAME`).
  static const spanName = 'screen_view';

  static const nameAttr = 'event.name';
  static const screenClassAttr = 'event.screen_class';
  static const screenIdAttr = 'event.screen_id';
  static const categoryAttr = 'event.category';

  /// Builds the span attributes for a screen view.
  ///
  /// User [properties] are added first so the reserved `event.*` keys win.
  static Map<String, Attribute> getSpanAttributes({
    required String name,
    String? screenClass,
    String? screenId,
    String? category,
    Map<String, Object?>? properties,
  }) {
    final attributes = <String, Attribute>{
      ...attributesFromProperties(properties),
    };

    attributes[nameAttr] = StringAttribute(name);
    if (screenClass != null) {
      attributes[screenClassAttr] = StringAttribute(screenClass);
    }
    if (screenId != null) {
      attributes[screenIdAttr] = StringAttribute(screenId);
    }
    if (category != null) {
      attributes[categoryAttr] = StringAttribute(category);
    }

    return attributes;
  }
}
