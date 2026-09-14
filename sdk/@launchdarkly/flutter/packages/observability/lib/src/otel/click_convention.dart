import '../api/attribute.dart';
import 'conversions.dart';

/// Semantic convention for `click` events.
///
/// Mirrors the native iOS/Android observability SDKs, which emit a span named
/// `click` carrying the analytics taxonomy `event.*` fields. User-supplied
/// `properties` are attached as additional attributes, with the reserved
/// `event.*` keys applied last so they can never be clobbered.
///
/// `event.screen_id` and `event.screen_name` are omitted here: on mobile the
/// native SDK fills them from the screen stack that Dart keeps current through
/// `trackScreenView`, and the Dart web pipeline maintains no equivalent.
class ClickConvention {
  /// The span name. Matches the native exporters
  /// (`SemanticConvention.clickSpanName` / `CLICK_SPAN_NAME`).
  static const spanName = 'click';

  static const typeAttr = 'event.type';
  static const tagAttr = 'event.tag';
  static const classnameAttr = 'event.classname';
  static const idAttr = 'event.id';
  static const textAttr = 'event.text';

  /// Path of the element within its UI hierarchy. Taxonomy §4.1 defines this as
  /// "XPath (web) / view path (mobile)"; on Flutter it is the widget ancestry
  /// path, e.g. `Scaffold/Column/Center/ElevatedButton#checkout`.
  static const xpathAttr = 'event.xpath';

  static const xAttr = 'event.x';
  static const yAttr = 'event.y';

  /// Builds the span attributes for a click.
  ///
  /// User [properties] are added first so the reserved `event.*` keys win.
  static Map<String, Attribute> getSpanAttributes({
    String? id,
    String? tag,
    String? classname,
    String? text,
    String? xpath,
    int? x,
    int? y,
    Map<String, Object?>? properties,
  }) {
    final attributes = <String, Attribute>{
      ...attributesFromProperties(properties),
    };

    attributes[typeAttr] = StringAttribute(spanName);
    if (tag != null) {
      attributes[tagAttr] = StringAttribute(tag);
    }
    if (classname != null) {
      attributes[classnameAttr] = StringAttribute(classname);
    }
    if (id != null) {
      attributes[idAttr] = StringAttribute(id);
    }
    if (text != null) {
      attributes[textAttr] = StringAttribute(text);
    }
    if (xpath != null) {
      attributes[xpathAttr] = StringAttribute(xpath);
    }
    if (x != null) {
      attributes[xAttr] = IntAttribute(x);
    }
    if (y != null) {
      attributes[yAttr] = IntAttribute(y);
    }

    return attributes;
  }
}
