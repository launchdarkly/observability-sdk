package com.launchdarkly.observability.replay

/**
 * Options for Session Replay plugin.
 *
 * @property debug enables verbose logging if true as well as other debug functionality. Defaults to false.
 * @property privacyProfile privacy profile that controls masking behavior
 * @property frameRate target capture rate in frames per second
 * @property scale optional replay scale override. When null, no additional scaling is applied. Usually from 1-4. 1 = 160DPI
 * @property sampleRate probability from `0.0` to `1.0` that session replay starts when [enabled] is
 *   true. Values less than or equal to zero never start; values greater than or equal to one always
 *   start. The decision is made once per enable cycle and reset when replay is stopped.
 * @property enabled controls whether session replay starts capturing immediately on initialization
 * @property compression compression strategy for frame export
 */
data class ReplayOptions(
    val enabled: Boolean = true,
    val debug: Boolean = false,
    val privacyProfile: PrivacyProfile = PrivacyProfile(),
    val sampleRate: Double = 1.0,
    val frameRate: Double = 1.0,
    /** Optional replay scale. Null disables scaling override. */
    val scale: Float? = 1.0f,
    val compression: CompressionMethod = CompressionMethod.OverlayTiles(),
    // TODO O11Y-623 - Add storage options
) {
    sealed class CompressionMethod {
        data object ScreenImage : CompressionMethod()
        data class OverlayTiles(
            val layers: Int = 15,
            val backtracking: Boolean = true,
        ) : CompressionMethod()

        companion object {
            /** Java-friendly accessor for the [ScreenImage] singleton. */
            @JvmStatic
            fun screenImage(): CompressionMethod = ScreenImage

            /** Java-friendly factory for [OverlayTiles] that fills in default values. */
            @JvmStatic
            @JvmOverloads
            fun overlayTiles(
                layers: Int = 15,
                backtracking: Boolean = true,
            ): CompressionMethod = OverlayTiles(layers = layers, backtracking = backtracking)
        }
    }

    /**
     * Java-friendly fluent builder for [ReplayOptions].
     *
     * Kotlin callers can keep using the [ReplayOptions] constructor with named/default arguments.
     * Every setter defaults to the same value as the [ReplayOptions] primary constructor.
     *
     * ```java
     * ReplayOptions options = ReplayOptions.builder()
     *     .enabled(false)
     *     .frameRate(1.0)
     *     .privacyProfile(PrivacyProfile.builder().maskWebViews(true).build())
     *     .build();
     * ```
     */
    class Builder {
        private var options = ReplayOptions()

        fun enabled(enabled: Boolean) = apply { options = options.copy(enabled = enabled) }
        fun debug(debug: Boolean) = apply { options = options.copy(debug = debug) }
        fun privacyProfile(privacyProfile: PrivacyProfile) = apply { options = options.copy(privacyProfile = privacyProfile) }
        fun sampleRate(sampleRate: Double) = apply { options = options.copy(sampleRate = sampleRate) }
        fun frameRate(frameRate: Double) = apply { options = options.copy(frameRate = frameRate) }
        fun scale(scale: Float?) = apply { options = options.copy(scale = scale) }
        fun compression(compression: CompressionMethod) = apply { options = options.copy(compression = compression) }

        fun build() = options
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}
