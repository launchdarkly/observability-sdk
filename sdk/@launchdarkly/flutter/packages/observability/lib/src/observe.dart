import 'dart:async';

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:opentelemetry/api.dart' as otel;

import 'api/attribute.dart';
import 'api/span.dart';
import 'api/span_kind.dart';
import 'otel/conversions.dart';
import 'otel/log_convention.dart';
import 'otel/setup.dart';
import 'plugin/observability_plugin.dart';
import 'plugin/observability_config.dart';

const _launchDarklyTracerName = 'launchdarkly-observability';
const _launchDarklyErrorSpanName = 'launchdarkly.error';
const _launchDarklyTrackSpanName = 'launchdarkly.track';
const _defaultLogLevel = 'info';

const _trackKeyAttr = 'key';
const _trackValueAttr = 'value';

/// Coerce a dynamic value (typically pulled out of `LDValue.toDynamic()`) into
/// an attribute. Returns `null` for types that cannot be represented as an
/// OpenTelemetry attribute (which the caller should skip rather than emit as
/// an [InvalidAttribute]).
Attribute? _attributeFromDynamic(dynamic value) {
  if (value is bool) {
    return BooleanAttribute(value);
  }
  if (value is int) {
    return IntAttribute(value);
  }
  if (value is double) {
    return DoubleAttribute(value);
  }
  if (value is String) {
    return StringAttribute(value);
  }
  // Typed-list checks first. In practice `LDValue.toDynamic()` always returns
  // `List<dynamic>` for arrays so these branches almost never match in
  // production, but keep them for direct callers that may pass typed lists.
  if (value is List<String>) {
    return StringListAttribute(value);
  }
  if (value is List<double>) {
    return DoubleListAttribute(value);
  }
  if (value is List<int>) {
    return IntListAttribute(value);
  }
  if (value is List<bool>) {
    return BooleanListAttribute(value);
  }
  // `LDValue.toDynamic()` always returns `List<dynamic>` for arrays. Inspect
  // element types to pick the appropriate typed-list attribute.
  if (value is List<dynamic>) {
    if (value.isEmpty) {
      // Can't infer element type from an empty list; skip rather than emit a
      // potentially mistyped attribute.
      return null;
    }
    if (value.every((e) => e is String)) {
      return StringListAttribute(value.cast<String>());
    }
    if (value.every((e) => e is bool)) {
      return BooleanListAttribute(value.cast<bool>());
    }
    if (value.every((e) => e is int)) {
      return IntListAttribute(value.cast<int>());
    }
    if (value.every((e) => e is double)) {
      return DoubleListAttribute(value.cast<double>());
    }
    // Mixed-numeric (int + double): coerce all to double.
    if (value.every((e) => e is num)) {
      return DoubleListAttribute(
        value.map((e) => (e as num).toDouble()).toList(),
      );
    }
    // Mixed or unsupported element types — skip.
    return null;
  }
  return null;
}

/// Singleton used to access observability features.
final class Observe {
  static bool _shutdown = false;
  static final List<ObservabilityPlugin> _pluginInstances = [];

  // ---------------------------------------------------------------------------
  // Process-wide mutable state shared between the observability hook's
  // `afterIdentify`/`afterTrack` callbacks and direct callers of
  // [Observe.track]. Lives here (rather than on a per-plugin instance) so that
  // both code paths see the same context-key + SDK-metadata attribute bags
  // without threading the plugin handle through every call site. A single LD
  // plugin instance per process is the expected use; if multiple plugins
  // register, the last writer wins. [Observe.shutdown] resets these caches to
  // empty, after which subsequent `track` calls emit minimal spans (just the
  // track key and any provided data/numericValue, with no context/SDK
  // metadata).
  // ---------------------------------------------------------------------------

  /// Cached bare context-key attributes (e.g. `{user: 'alice', org: 'team-a'}`)
  /// populated by the observability hook's `afterIdentify`. Used as a base
  /// attribute set on every `launchdarkly.track` span emitted via
  /// [Observe.track].
  static Map<String, Attribute> _contextKeyAttributes = <String, Attribute>{};

  /// Cached LaunchDarkly SDK metadata attributes (telemetry.sdk.*,
  /// launchdarkly.application.*, feature_flag.set.id,
  /// feature_flag.provider.name). Populated when the observability plugin is
  /// registered with the LD client.
  static Map<String, Attribute> _sdkMetadataAttributes = <String, Attribute>{};

  /// Whether product analytics track-event emission is enabled. Populated
  /// during plugin registration from [ProductAnalyticsConfig.trackEvents].
  static bool _productAnalyticsTrackEvents = true;

  /// Start a span with the given name and optional attributes.
  static Span startSpan(
    String name, {
    SpanKind kind = SpanKind.internal,
    Map<String, Attribute>? attributes,
  }) {
    final tracer = otel.globalTracerProvider.getTracer(_launchDarklyTracerName);
    final span = tracer.startSpan(
      name,
      kind: convertKind(kind),
      attributes: convertAttributes(attributes),
    );
    final token = otel.Context.attach(
      otel.contextWithSpan(otel.Context.current, span),
    );

    return wrapSpan(span, token);
  }

  /// Record an exception with an optional stack trace and attributes.
  ///
  /// In dart the stack trace is independent of the exception object and can
  /// be caught at the same time as an exception.
  /// ```dart
  /// try {
  ///   // thing that throws
  /// catch(err, stack) {
  ///   Observe.record(err, stacktrace: stack);
  /// }
  /// ```
  ///
  /// In order to capture a stack trace that isn't at the origin of the catch
  /// the `StackTrace.current` method can be used.
  ///
  /// The value of the [exception] object will be incorporated into traces
  /// using its `toString` method.
  static void recordException(
    dynamic exception, {
    StackTrace? stackTrace,
    Map<String, Attribute>? attributes,
  }) {
    // The OTEL library currently doesn't have a way to differentiate if there
    // is an active span or not. So currently we always create a span for
    // exceptions.
    final span = startSpan(_launchDarklyErrorSpanName);
    span.recordException(
      exception,
      attributes: attributes,
      stackTrace: stackTrace ?? StackTrace.empty,
    );
    span.end();
  }

  /// Record a log with optional attributes.
  ///
  /// If [severity] is not provided, then it will default to 'info'.
  /// An optional [stackTrace] can be provided.
  ///
  /// The `StackTrace.current` property can be used to capture a stack trace.
  static void recordLog(
    String message, {
    String severity = _defaultLogLevel,
    StackTrace? stackTrace,
    Map<String, Attribute>? attributes,
  }) {
    final combinedAttributes = LogConvention.getEventAttributes(
      message,
      severity,
      stackTrace,
    );
    if (attributes != null) {
      combinedAttributes.addAll(attributes);
    }
    final span = startSpan(LogConvention.spanName);
    span.addEvent(LogConvention.eventName, attributes: combinedAttributes);
    span.end();
  }

  /// Emit a `launchdarkly.track` span describing a custom track event.
  ///
  /// This is the public surface invoked by the observability plugin's
  /// `afterTrack` hook each time `ldClient.track(...)` is called. It may also
  /// be called directly for ad-hoc track-event telemetry without going
  /// through the LD client.
  ///
  /// The span's attributes are the union of:
  ///   - cached LD context-key attributes from the most recent `afterIdentify`
  ///     (e.g. `{user: 'alice', org: 'team-a'}`),
  ///   - cached SDK/application metadata (telemetry.sdk.*,
  ///     launchdarkly.application.*, feature_flag.set.id,
  ///     feature_flag.provider.name),
  ///   - the track event `key`,
  ///   - `value` when [numericValue] is non-null,
  ///   - any string/int/double/bool entries from [data] when
  ///     `data.toDynamic()` resolves to a `Map<String, dynamic>`.
  ///
  /// If the configured [ProductAnalyticsConfig.trackEvents] is `false`, this
  /// method is a no-op. All other failure modes (tracer not initialized,
  /// throw during span construction) are caught internally and swallowed,
  /// so this method never throws.
  static void track(String key, {LDValue? data, num? numericValue}) {
    if (!_productAnalyticsTrackEvents) {
      return;
    }

    try {
      final attributes = <String, Attribute>{
        ..._contextKeyAttributes,
        ..._sdkMetadataAttributes,
        _trackKeyAttr: StringAttribute(key),
      };

      if (numericValue != null) {
        attributes[_trackValueAttr] = DoubleAttribute(numericValue.toDouble());
      }

      if (data != null) {
        final dynamicValue = data.toDynamic();
        if (dynamicValue is Map<String, dynamic>) {
          dynamicValue.forEach((dataKey, dataValue) {
            final attribute = _attributeFromDynamic(dataValue);
            if (attribute != null) {
              attributes[dataKey] = attribute;
            }
          });
        }
      }

      final span = startSpan(
        _launchDarklyTrackSpanName,
        attributes: attributes,
      );
      span.end();
    } catch (_) {
      // Hook safety: `ldClient.track(...)` must always return normally.
      // Swallow any internal failure (tracer not initialized, exporter
      // misconfigured, etc.) so the hook can be a trivial pass-through.
    }
  }

  /// Shutdown observability. Once shutdown observability cannot be restarted.
  static void shutdown() {
    if (!_shutdown) {
      Otel.shutdown();
      for (final plugin in _pluginInstances) {
        plugin.dispose();
      }
      _contextKeyAttributes = <String, Attribute>{};
      _sdkMetadataAttributes = <String, Attribute>{};
      _productAnalyticsTrackEvents = true;
      _shutdown = true;
    }
  }

  /// Get a zone specification which intercepts print statements.
  static ZoneSpecification zoneSpecification() {
    return ZoneSpecification(
      print: (Zone self, ZoneDelegate parent, Zone zone, String line) {
        parent.print(zone, line);
        Observe.recordLog(line);
      },
    );
  }
}

/// Not for export.
/// Registers a plugin with the singleton and sets up otel.
registerPlugin(
  ObservabilityPlugin plugin,
  String credential,
  ObservabilityConfig config,
) {
  Otel.setup(credential, config);
  Observe._pluginInstances.add(plugin);
  Observe._productAnalyticsTrackEvents =
      config.productAnalyticsConfig.trackEvents;
}

/// Not for export.
/// Populate the cached bare context-key attributes used by [Observe.track].
/// Called by the observability hook's `afterIdentify`.
void setLDContextKeyAttributes(Map<String, Attribute> attributes) {
  Observe._contextKeyAttributes = Map<String, Attribute>.unmodifiable(
    attributes,
  );
}

/// Not for export.
/// Populate the cached SDK metadata attributes used by [Observe.track].
/// Called when the observability plugin is registered with the LD client.
void setLDSdkMetadataAttributes(Map<String, Attribute> attributes) {
  Observe._sdkMetadataAttributes = Map<String, Attribute>.unmodifiable(
    attributes,
  );
}

/// Not for export.
/// Helper exposed for testing — coerce a dynamic value (typically pulled out
/// of `LDValue.toDynamic()`) into an attribute, returning `null` for types
/// that cannot be represented.
Attribute? attributeFromDynamicForTest(dynamic value) =>
    _attributeFromDynamic(value);

/// Not for export.
/// Helper exposed for testing — directly set the product-analytics gate
/// without spinning up a full LD client + plugin registration. Production
/// callers should use [registerPlugin] which seeds this from
/// [ProductAnalyticsConfig.trackEvents].
void setProductAnalyticsTrackEventsForTest(bool enabled) {
  Observe._productAnalyticsTrackEvents = enabled;
}
