package com.launchdarkly.observability.plugin

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.sdk.LDValue
import io.mockk.mockk
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * End-to-end test for the `launchdarkly.track` span path on
 * [ObservabilityHookExporter].
 *
 * Builds a real OTel [SdkTracerProvider], routes the span produced by
 * [ObservabilityHookExporter.track] through an [InMemorySpanExporter], protobuf-marshals
 * the captured [io.opentelemetry.sdk.trace.data.SpanData] using OTel's own
 * `TraceRequestMarshaler` (via the Java-side [OtlpProtoHelper] shim — Kotlin reserves
 * `internal` and can't import the OTel package whose path contains it), and POSTs it via
 * [HttpURLConnection] to the devbox observability backend's `/otel/v1/traces` endpoint.
 * Then queries ClickHouse to verify the `launchdarkly.track` row landed with the expected
 * attributes.
 *
 * Why we don't use [io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter]
 * directly: the OTel SDK's OkHttp-based HTTP sender is reliably broken in this Gradle
 * unit-test JVM on macOS (every cold-JVM call gets `Canceled` before the request goes
 * out the wire and the OTel RetryInterceptor treats `Canceled` as non-retryable, so the
 * 30s call timeout fires and the export future resolves to failure). Using
 * [HttpURLConnection] sidesteps the issue while exercising the exact same on-the-wire
 * protobuf payload — which is what we want to validate for the end-to-end contract.
 *
 * Gated on the `O11Y_E2E` env var so it doesn't run in CI / general developer workflows
 * where the devbox forwards aren't up. To run locally:
 *
 *   export O11Y_E2E=1
 *   ./gradlew :lib:testDebugUnitTest --tests '*E2ETest*'
 *
 * Environment expectations (defaults match the standard devbox setup):
 *   - OTLP traces endpoint: http://localhost:9096/otel/v1/traces  (override via O11Y_OTLP_ENDPOINT)
 *   - ClickHouse HTTP:      http://localhost:8123                  (override via O11Y_CH_URL)
 *   - Project ID:           1                                      (override via O11Y_PROJECT_ID)
 */
@EnabledIfEnvironmentVariable(named = "O11Y_E2E", matches = "1|true")
class ObservabilityHookExporterE2ETest {

    private val otlpEndpoint: String =
        System.getenv("O11Y_OTLP_ENDPOINT") ?: "http://localhost:9096/otel/v1/traces"
    private val clickhouseUrl: String =
        System.getenv("O11Y_CH_URL") ?: "http://localhost:8123"
    private val projectId: String =
        System.getenv("O11Y_PROJECT_ID") ?: "1"

    private lateinit var spanExporter: InMemorySpanExporter
    private lateinit var tracerProvider: SdkTracerProvider

    @BeforeEach
    fun setUp() {
        spanExporter = InMemorySpanExporter.create()
        tracerProvider = SdkTracerProvider.builder()
            .setResource(
                Resource.getDefault().toBuilder()
                    .put("service.name", "observability-android-e2e")
                    .build()
            )
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
    }

    @AfterEach
    fun tearDown() {
        tracerProvider.shutdown().join(5, TimeUnit.SECONDS)
    }

    @Test
    fun `track span lands in ClickHouse with required attributes`() {
        val eventKey = "test-event-android-" + UUID.randomUUID().toString().substring(0, 8)
        val testTracer = tracerProvider.get(ObservabilityHookExporter.INSTRUMENTATION_NAME)

        val exporter = ObservabilityHookExporter(
            withSpans = true,
            withValue = true,
            tracerProvider = { testTracer },
            logger = mockk<ObserveLogger>(relaxed = true),
        )

        // Seed meta attributes the way `Observability.onPluginsReady` does.
        // `feature_flag.set.id` is what the backend's extractor uses as the project ID
        // (see observability/backend/otel/extract.go:278-281).
        val meta = Attributes.builder()
            .put(ObservabilityHookExporter.ATTR_TELEMETRY_SDK_NAME, "android-client-sdk")
            .put(ObservabilityHookExporter.ATTR_TELEMETRY_SDK_VERSION, "5.12.0")
            .put(ObservabilityHookExporter.ATTR_FEATURE_FLAG_SET_ID, projectId)
            .put(ObservabilityHookExporter.ATTR_FEATURE_FLAG_PROVIDER_NAME, "LaunchDarkly")
            .put(ObservabilityHookExporter.ATTR_LD_APPLICATION_ID, "com.example.e2e")
            .put(ObservabilityHookExporter.ATTR_LD_APPLICATION_VERSION, "1.2.3")
            .build()
        exporter.setMetaAttributes(meta)

        // Populate the context-key cache as `Observability.afterIdentify` would.
        exporter.afterIdentify(
            contextKeys = mapOf("user" to "alice-e2e"),
            canonicalKey = "user:alice-e2e",
            completed = true,
        )

        // Trigger the launchdarkly.track span — lands synchronously in spanExporter via
        // SimpleSpanProcessor.
        exporter.track(
            key = eventKey,
            data = LDValue.buildObject().put("foo", "bar").build(),
            metricValue = 42.0,
        )

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size, "expected exactly one span to be captured by InMemorySpanExporter")
        val span = spans.single()
        assertEquals(ObservabilityHookExporter.LD_TRACK_SPAN_NAME, span.name)

        // Marshal to OTLP/HTTP+protobuf via OTel's own TraceRequestMarshaler (the same
        // path OtlpHttpSpanExporter uses internally) and POST it.
        val payload = OtlpProtoHelper.marshalSpansToProto(spans)
        val otlpStatus = postOtlp(payload)
        assertEquals(
            200, otlpStatus,
            "OTLP backend did not accept the protobuf payload — see backend logs for parse errors"
        )

        // Poll ClickHouse for the row. Polling is on the test side rather than relying on
        // synchronous ingestion because the backend buffers OTLP writes through a Kafka-like
        // batched write-buffer before ClickHouse.
        //
        // Backend extractor (observability/backend/otel/extract.go) consumes a few
        // attributes off the incoming span and promotes them to dedicated CH columns:
        //   - `feature_flag.set.id` → `ProjectId` (number-parsed) and deleted from TraceAttributes
        //   - `telemetry.sdk.*`     → `TelemetryAttributes` map
        //   - `launchdarkly.application.*` (and other LD app/operation attrs) → `TraceAttributes`
        // So we project the columns we care about and assert against the post-extraction shape.
        val deadline = System.currentTimeMillis() + 30_000
        var row: String? = null
        val startedPollingAt = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            val query =
                "SELECT SpanName, ProjectId, " +
                    "TraceAttributes['key'] AS k, " +
                    "TraceAttributes['value'] AS v, " +
                    "TraceAttributes['foo'] AS foo, " +
                    "TraceAttributes['user'] AS u, " +
                    "TelemetryAttributes['telemetry.sdk.name'] AS tsdk, " +
                    "TraceAttributes['feature_flag.provider.name'] AS ffprov, " +
                    "TraceAttributes['launchdarkly.application.id'] AS ldapp " +
                    "FROM default.traces " +
                    "WHERE TraceAttributes['key'] = '$eventKey' " +
                    "AND SpanName = 'launchdarkly.track' " +
                    "LIMIT 1 FORMAT TabSeparated"
            val resp = chQuery(query)
            if (resp.isNotBlank()) {
                row = resp.trim()
                break
            }
            Thread.sleep(500)
        }
        val ttlMs = System.currentTimeMillis() - startedPollingAt
        println("[E2E] poll loop exited after ${ttlMs}ms; row=$row")

        assertNotNull(row, "no launchdarkly.track row landed in ClickHouse for $eventKey within 30s")
        // FORMAT TabSeparated returns columns in the order requested:
        //   SpanName, ProjectId, k, v, foo, u, tsdk, ffprov, ldapp
        val cols = row!!.split('\t')
        assertEquals("launchdarkly.track", cols[0], "SpanName mismatch (row=$row)")
        assertEquals(projectId, cols[1], "ProjectId mismatch — feature_flag.set.id should map to ProjectId (row=$row)")
        assertEquals(eventKey, cols[2], "key attribute mismatch (row=$row)")
        // ClickHouse stores attribute values as String; OTel serializes the Double 42.0 as
        // "42" once it loses the trailing decimal through the marshaling path.
        assertEquals("42", cols[3], "value attribute mismatch (row=$row)")
        assertEquals("bar", cols[4], "foo attribute (from LDValue data spread) mismatch (row=$row)")
        assertEquals("alice-e2e", cols[5], "user attribute (from afterIdentify cache) mismatch (row=$row)")
        assertEquals("android-client-sdk", cols[6], "telemetry.sdk.name (in TelemetryAttributes) mismatch (row=$row)")
        assertEquals("LaunchDarkly", cols[7], "feature_flag.provider.name mismatch (row=$row)")
        assertEquals("com.example.e2e", cols[8], "launchdarkly.application.id mismatch (row=$row)")
    }

    private fun postOtlp(payload: ByteArray): Int {
        val url = URL(otlpEndpoint)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5_000
            // The devbox backend is slow on the first OTLP request after startup (PG
            // project lookup + workspace settings + sampling config all warm up on the
            // first request, easily 15-20s). Subsequent requests are fast (<200ms). Keep
            // the read timeout generous so a cold devbox doesn't false-fail the test.
            conn.readTimeout = 60_000
            conn.setRequestProperty("Content-Type", "application/x-protobuf")
            conn.setFixedLengthStreamingMode(payload.size)
            conn.outputStream.use { it.write(payload) }
            val status = conn.responseCode
            if (status / 100 != 2) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                println("[E2E] OTLP POST returned $status: $err")
            } else {
                conn.inputStream.use { it.readBytes() }
            }
            return status
        } finally {
            conn.disconnect()
        }
    }

    private fun chQuery(query: String): String {
        val url = URL(
            clickhouseUrl + "/?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            val status = conn.responseCode
            if (status / 100 != 2) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw AssertionError("ClickHouse query failed: $status $err")
            }
            return conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
