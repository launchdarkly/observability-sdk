package com.launchdarkly.observability.client

import android.content.Context
import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.api.ObservabilityOptions
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.resources.Resource

/**
 * Default Highlight/OTel "distro" attributes attached to every observability [Resource].
 *
 * Exposed (internally) so the [com.launchdarkly.observability.plugin.Observability] LDClient
 * plugin can seed its mutable `distroAttributes` field with the same defaults the standalone
 * [com.launchdarkly.observability.sdk.LDObserve.init] path uses, keeping the two init paths
 * in sync without duplicating string literals.
 */
internal val DEFAULT_DISTRO_ATTRIBUTES: Map<String, String> = mapOf(
    "telemetry.distro.name" to "launchdarkly-observability-android",
    "telemetry.distro.version" to BuildConfig.OBSERVABILITY_SDK_VERSION,
)

/**
 * Resource attribute carrying the artifact's deterministic symbols id — the
 * `htlhash` of the R8/ProGuard `mapping.txt`. The app build embeds it as an
 * asset ([SYMBOLS_ID_ASSET_NAME]); reporting it lets the backend resolve the
 * mapping by symbols id (the "Symbols Id Lane"), independent of the app version.
 */
internal const val SYMBOLS_ID_HTLHASH_KEY = "launchdarkly.symbols_id.htlhash"

/** Asset (packaged under `assets/`) the build writes the 32-hex-char symbols id to. */
internal const val SYMBOLS_ID_ASSET_NAME = "ld_symbols_id.txt"

// The symbols id is the first 16 bytes of a SHA-256 rendered as 32 lowercase hex
// chars. Anything else (missing asset, an all-zero placeholder) is treated as
// "no symbols id" so reporting cleanly falls back to no Symbols Id Lane attribute.
private val SYMBOLS_ID_PATTERN = Regex("^[0-9a-f]{32}$")

/**
 * Reads the symbols id the app build embedded at [SYMBOLS_ID_ASSET_NAME], or
 * returns `null` when absent/invalid (e.g. a non-obfuscated or older build).
 * Best-effort: any failure yields `null` so it can never break initialization.
 */
internal fun readInjectedSymbolsId(context: Context): String? {
    return try {
        val raw = context.assets.open(SYMBOLS_ID_ASSET_NAME).use { it.readBytes() }
            .toString(Charsets.UTF_8)
            .trim()
        if (SYMBOLS_ID_PATTERN.matches(raw) && raw.any { it != '0' }) raw else null
    } catch (_: Exception) {
        null
    }
}

/**
 * Builds the OpenTelemetry [Resource] attached to every observability signal emitted by this SDK.
 *
 * Single source of truth for resource shape, used by both initialization paths:
 *  - [com.launchdarkly.observability.plugin.Observability.onPluginsReady] (LDClient plugin path),
 *    which flattens its [com.launchdarkly.sdk.android.integrations.EnvironmentMetadata] into the
 *    [applicationId], [applicationVersion], and [sdkVersion] params.
 *  - [com.launchdarkly.observability.sdk.LDObserve.init] (standalone path), which has no
 *    LDClient metadata and simply omits those params.
 *
 * Attribute precedence (later wins for duplicate keys):
 *  1. OpenTelemetry default resource attributes, minus `service.name`.
 *  2. `highlight.project_id` = [sdkKey].
 *  3. [distroAttributes] (defaults to [DEFAULT_DISTRO_ATTRIBUTES] — `telemetry.distro.{name,version}`).
 *  4. Caller-supplied [ObservabilityOptions.resourceAttributes].
 *  5. `service.name` = [ObservabilityOptions.serviceName] and `service.version` =
 *     [ObservabilityOptions.serviceVersion], so the configured service identity always wins.
 *  6. `launchdarkly.application.id`, `launchdarkly.application.version`, `launchdarkly.sdk.version`
 *     when provided (LDClient plugin path only).
 *
 * The metadata-derived attributes (5) come *after* the user's [ObservabilityOptions.resourceAttributes]
 * so user overrides cannot accidentally clobber LDClient identity. If a user genuinely wants to
 * override `launchdarkly.*`, they shouldn't be using these attribute names anyway.
 *
 * @param sdkKey            LaunchDarkly mobile key; written as `highlight.project_id`.
 * @param options           Observability options; [ObservabilityOptions.resourceAttributes] is appended.
 * @param distroAttributes  Distro identification, defaults to [DEFAULT_DISTRO_ATTRIBUTES].
 *                          The LDClient plugin passes its mutable `distroAttributes` field here
 *                          so callers that customize the field still see their changes.
 * @param applicationId     `launchdarkly.application.id`, or `null` to omit.
 * @param applicationVersion `launchdarkly.application.version`, or `null` to omit.
 * @param sdkVersion        `launchdarkly.sdk.version`, or `null` to omit. Already formatted
 *                          (typically `"$sdkName/$sdkVersion"`) — this helper does not compose it.
 * @param symbolsId         `launchdarkly.symbols_id.htlhash` (the R8 mapping's htlhash), or
 *                          `null` to omit. Read from the app via [readInjectedSymbolsId].
 */
internal fun buildObservabilityResource(
    sdkKey: String,
    options: ObservabilityOptions,
    distroAttributes: Map<String, String> = DEFAULT_DISTRO_ATTRIBUTES,
    applicationId: String? = null,
    applicationVersion: String? = null,
    sdkVersion: String? = null,
    symbolsId: String? = null,
): Resource {
    val builder = Attributes.builder()

    Resource.getDefault().attributes.forEach { key, value ->
        if (key.key != "service.name") {
            @Suppress("UNCHECKED_CAST")
            builder.put(key as AttributeKey<Any>, value)
        }
    }

    builder.put("highlight.project_id", sdkKey)

    distroAttributes.forEach { (key, value) ->
        builder.put(AttributeKey.stringKey(key), value)
    }

    builder.putAll(options.resourceAttributes)

    builder.put("service.name", options.serviceName)
    builder.put("service.version", options.serviceVersion)

    // Symbols identity: keep it authoritative (after user resourceAttributes) so a
    // stray user attribute can't shadow the value the backend matches maps by.
    symbolsId?.let { builder.put(AttributeKey.stringKey(SYMBOLS_ID_HTLHASH_KEY), it) }

    applicationId?.let { builder.put("launchdarkly.application.id", it) }
    applicationVersion?.let { builder.put("launchdarkly.application.version", it) }
    sdkVersion?.let { builder.put("launchdarkly.sdk.version", it) }

    return Resource.create(builder.build())
}
