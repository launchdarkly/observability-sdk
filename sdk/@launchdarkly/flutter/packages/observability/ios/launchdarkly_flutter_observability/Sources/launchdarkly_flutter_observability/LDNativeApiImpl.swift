import Flutter
import Foundation
import LaunchDarkly
import LaunchDarklyObservability
import LaunchDarklySessionReplay

/// Pigeon `LDNativeApi` implementation. Ported (line-for-line where it makes
/// sense) from `ObservabilityBridge.swift` in
/// `sdk/@launchdarkly/mobile-dotnet/macios/native/LDObserve/Sources/ObservabilityBridge.swift`,
/// with the Pigeon-generated wire DTOs taking the place of the .NET-side
/// `ObjcObservabilityOptions` / `ObjcSessionReplayOptions` types.
final class LDNativeApiImpl: NSObject, LDNativeApi {
    private let captureChannel: FlutterMethodChannel

    init(captureChannel: FlutterMethodChannel) {
        self.captureChannel = captureChannel
        super.init()
    }

    func start(
        mobileKey: String,
        observability: LDObservabilityOptions,
        replay: LDSessionReplayOptions,
        observabilityVersion: String,
        completion: @escaping (Result<LDStartResult, Error>) -> Void
    ) {
        do {
            let config = try makeConfig(
                mobileKey: mobileKey,
                observability: observability,
                replay: replay,
                observabilityVersion: observabilityVersion
            )

            let context = try makeAnonymousContext()

            LDClient.start(
                config: config,
                context: context,
                startWaitSeconds: 15.0,
                completion: { _ in }
            )

            completion(.success(LDStartResult(nativeVersion: observabilityVersion)))
        } catch {
            completion(.failure(error))
        }
    }

    // Re-creates each Dart span on the native tracer so the native pipeline
    // stamps `session.id` (and applies sampling/batching). Mirrors MAUI's
    // `TraceBuilderAdapter`.
    func exportSpans(spans: [LDSpanData]) throws {
        guard let tracer = ObjcLDObserveBridge.getObjcTracer() else { return }
        for span in spans {
            let builder = tracer.spanBuilder(
                name: span.name ?? "",
                startTime: span.startTimeSeconds ?? 0,
                traceId: span.traceId ?? "",
                spanId: span.spanId ?? "",
                parentSpanId: span.parentSpanId ?? ""
            )

            builder.setAttributes(cleanAttributes(span.attributes) as NSDictionary)

            for case let event? in span.events ?? [] {
                let name = event.name ?? ""
                let eventAttrs = cleanAttributes(event.attributes)
                if eventAttrs.isEmpty {
                    builder.addEvent(name: name)
                } else {
                    builder.addEvent(name: name, attributes: eventAttrs as NSDictionary)
                }
            }

            if let status = span.statusCode, status != 0 {
                builder.setStatus(code: Int(status))
            }

            builder.end(time: span.endTimeSeconds ?? span.startTimeSeconds ?? 0)
        }
    }

    // Forwards the log to the native customer logger so it is emitted as a real
    // `LogRecord` with `session.id` and trace/span correlation.
    func recordLog(log: LDLogRecord) throws {
        guard let logger = ObjcLDObserveBridge.getObjcLogger() else { return }
        logger.recordLog(
            message: log.message ?? "",
            severity: Int(log.severityNumber ?? 9),
            traceId: log.traceId,
            spanId: log.spanId,
            isInternal: false,
            attributes: cleanAttributes(log.attributes)
        )
    }

    /// Drops `nil` values so the native bridge receives a `[String: Any]`.
    private func cleanAttributes(_ attributes: [String: Any?]?) -> [String: Any] {
        guard let attributes = attributes else { return [:] }
        var result = [String: Any](minimumCapacity: attributes.count)
        for (key, value) in attributes {
            if let value = value {
                result[key] = value
            }
        }
        return result
    }

    private func makeConfig(
        mobileKey: String,
        observability: LDObservabilityOptions,
        replay: LDSessionReplayOptions,
        observabilityVersion: String
    ) throws -> LDConfig {
        var config = LDConfig(
            mobileKey: mobileKey,
            autoEnvAttributes: .enabled
        )
        config.startOnline = false

        let crashReportingEnabled: Bool = observability.instrumentation?.crashReporting ?? true
        // `analytics.taps` is the publish gate for tap `click` spans; tap detection itself is
        // driven by the native `instrumentation.userTaps` default. pageViews and trackEvents are
        // Android-only and ignored here.
        let tapsEnabled: Bool = observability.analytics?.taps ?? true
        let launchTimesEnabled: Bool = observability.instrumentation?.launchTimes ?? true
        let metricsEnabled: Bool = observability.metricsEnabled ?? true

        let serviceName: String = observability.serviceName ?? Defaults.serviceName
        let serviceVersion: String = observability.serviceVersion ?? Defaults.serviceVersion
        let otlpEndpoint: String = observability.otlpEndpoint ?? Defaults.otlpEndpoint
        let backendUrl: String = observability.backendUrl ?? Defaults.backendUrl
        let resourceAttributes: [String: AttributeValue] =
            buildResourceAttributes(observability.attributes)
        let customHeaders: [String: String] = observability.customHeaders ?? [:]
        let sessionBackgroundTimeout: TimeInterval = observability.sessionBackgroundTimeoutMillis
            .map { TimeInterval($0) / 1000.0 } ?? 15 * 60
        let logsApiLevel: ObservabilityOptions.LogLevel = mapLogLevel(observability.logsApiLevel)

        let tracesApi = ObservabilityOptions.AppTracing(
            includeErrors: observability.traces?.includeErrors ?? true,
            includeSpans: observability.traces?.includeSpans ?? true
        )
        let metricsApi: ObservabilityOptions.AppMetrics = metricsEnabled ? .enabled : .disabled
        let crashReporting = ObservabilityOptions.CrashReporting(
            source: crashReportingEnabled ? .KSCrash : .none
        )
        let instrumentation = ObservabilityOptions.Instrumentation(
            urlSession: .disabled,
            memory: .disabled,
            memoryWarnings: .disabled,
            cpu: .disabled,
            launchTimes: launchTimesEnabled ? .enabled : .disabled
        )
        // iOS exposes taps via Analytics; track events are Android-only.
        let analytics = ObservabilityOptions.Analytics(
            taps: tapsEnabled ? .enabled : .disabled,
            trackEvents: .disabled
        )

        let observabilityOptions = ObservabilityOptions(
            serviceName: serviceName,
            serviceVersion: serviceVersion,
            otlpEndpoint: otlpEndpoint,
            backendUrl: backendUrl,
            resourceAttributes: resourceAttributes,
            customHeaders: customHeaders,
            sessionBackgroundTimeout: sessionBackgroundTimeout,
            logsApiLevel: logsApiLevel,
            tracesApi: tracesApi,
            metricsApi: metricsApi,
            crashReporting: crashReporting,
            instrumentation: instrumentation,
            analytics: analytics
        )
        let observabilityPlugin = Observability(options: observabilityOptions)
        observabilityPlugin.distroAttributes = [
            "telemetry.distro.name": "observability-flutter-ios",
            "telemetry.distro.version": observabilityVersion,
        ]

        let sessionReplayOptions = makeSessionReplayOptions(replay: replay)
        let privacy = replay.privacy
        let maskTextInputs = privacy?.maskTextInputs ?? true
        let flutterCaptureService = FlutterImageCaptureService(
            channel: captureChannel,
            maskTextInputs: maskTextInputs
        )
        config.plugins = [
            observabilityPlugin,
            SessionReplay(
                options: sessionReplayOptions,
                imageCaptureService: flutterCaptureService
            ),
        ]

        return config
    }

    private func makeSessionReplayOptions(
        replay: LDSessionReplayOptions
    ) -> LaunchDarklySessionReplay.SessionReplayOptions {
        let privacy = replay.privacy

        return LaunchDarklySessionReplay.SessionReplayOptions(
            isEnabled: replay.isEnabled ?? true,
            privacy: .init(
                maskTextInputs: privacy?.maskTextInputs ?? true,
                maskWebViews: privacy?.maskWebViews ?? false,
                maskLabels: privacy?.maskLabels ?? false,
                maskImages: privacy?.maskImages ?? false
            ),
            frameRate: replay.frameRate ?? 1.0
        )
    }

    /// Maps the OpenTelemetry log-severity number sent across the bridge onto the
    /// native `ObservabilityOptions.LogLevel`. The `none` sentinel
    /// (`Int.max` / `0x7fffffff`) maps to `.none`; otherwise the severity number
    /// is the matching raw value. Defaults to `.info` when nil or unrecognized.
    private func mapLogLevel(_ severity: Int64?) -> ObservabilityOptions.LogLevel {
        guard let severity = severity else { return .info }
        if severity >= Int64(Int32.max) { return .none }
        return ObservabilityOptions.LogLevel(rawValue: Int(severity)) ?? .info
    }

    private func makeAnonymousContext() throws -> LDContext {
        var contextBuilder = LDContextBuilder(key: "flutter-user-key")
        contextBuilder.kind("user")
        switch contextBuilder.build() {
        case .success(let context):
            return context
        case .failure(let error):
            throw error
        }
    }

    private func buildResourceAttributes(_ source: [String: Any?]?) -> [String: AttributeValue] {
        guard let source = source, !source.isEmpty else { return [:] }
        var result = [String: AttributeValue](minimumCapacity: source.count)
        for (key, value) in source {
            guard let value = value else { continue }
            if let av = AttributeValue(value) {
                result[key] = av
            } else {
                result[key] = .string(String(describing: value))
            }
        }
        return result
    }

    private enum Defaults {
        static let serviceName = "observability-flutter"
        static let serviceVersion = "0.1.0"
        static let otlpEndpoint = "https://otel.observability.app.launchdarkly.com:4318"
        static let backendUrl = "https://pub.observability.app.launchdarkly.com"
    }
}
