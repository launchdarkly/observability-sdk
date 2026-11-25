package com.launchdarkly.observability;

import java.util.Objects;

/**
 * Options for {@link SessionReplay}. The defaults are Android's: replay starts at once for every
 * session, takes one frame a second at the window's logical size, and encodes it at JPEG quality 0.3.
 *
 * <pre><code>
 *     ReplayOptions options = ReplayOptions.builder()
 *         .sampleRate(0.25)
 *         .privacyProfile(PrivacyProfile.strict())
 *         .build();
 * </code></pre>
 */
public final class ReplayOptions {
  static final double MIN_FRAME_RATE = 0.1;
  static final double MAX_FRAME_RATE = 10;
  static final double MIN_SCALE = 0.1;
  static final double MAX_SCALE = 4;

  private final boolean enabled;
  private final PrivacyProfile privacyProfile;
  private final double sampleRate;
  private final double frameRate;
  private final double scale;
  private final double imageQuality;
  private final CompressionMethod compression;
  private final boolean debug;

  private ReplayOptions(Builder builder) {
    this.enabled = builder.enabled;
    this.privacyProfile = builder.privacyProfile;
    this.sampleRate = builder.sampleRate;
    this.frameRate = builder.frameRate;
    this.scale = builder.scale;
    this.imageQuality = builder.imageQuality;
    this.compression = builder.compression;
    this.debug = builder.debug;
  }

  /** A builder, starting from the defaults. */
  public static Builder builder() {
    return new Builder();
  }

  /** Whether replay starts recording as soon as it is installed. */
  public boolean isEnabled() {
    return enabled;
  }

  /** What is masked. */
  public PrivacyProfile getPrivacyProfile() {
    return privacyProfile;
  }

  /** The share of sessions recorded, from 0 to 1. */
  public double getSampleRate() {
    return sampleRate;
  }

  /** Frames taken a second, at most. */
  public double getFrameRate() {
    return frameRate;
  }

  /** Frame pixels per logical pixel of the window. */
  public double getScale() {
    return scale;
  }

  /** The frames' JPEG quality, from 0 to 1. */
  public double getImageQuality() {
    return imageQuality;
  }

  /** How frames are sent. */
  public CompressionMethod getCompression() {
    return compression;
  }

  /** Whether replay logs its own activity in detail. */
  public boolean isDebug() {
    return debug;
  }

  @Override
  public String toString() {
    return "ReplayOptions{enabled=" + enabled + ", sampleRate=" + sampleRate + ", frameRate=" + frameRate
        + ", scale=" + scale + ", imageQuality=" + imageQuality + ", compression=" + compression + ", debug="
        + debug + ", privacyProfile=" + privacyProfile + "}";
  }

  /**
   * How frames are sent, as on Android: {@link #overlayTiles()}, the default, or {@link #screenImage()}.
   */
  public static final class CompressionMethod {
    /** Android's default number of overlays between whole frames. */
    public static final int DEFAULT_LAYERS = 15;

    private static final CompressionMethod SCREEN_IMAGE = new CompressionMethod(true, 0, false);
    private static final CompressionMethod OVERLAY_TILES = new CompressionMethod(false, DEFAULT_LAYERS, true);

    private final boolean screenImage;
    private final int layers;
    private final boolean backtracking;

    private CompressionMethod(boolean screenImage, int layers, boolean backtracking) {
      this.screenImage = screenImage;
      this.layers = layers;
      this.backtracking = backtracking;
    }

    /** Every frame that changed is sent whole. Simple, and the most data. */
    public static CompressionMethod screenImage() {
      return SCREEN_IMAGE;
    }

    /** The default: {@code overlayTiles(15, true)}. */
    public static CompressionMethod overlayTiles() {
      return OVERLAY_TILES;
    }

    /**
     * Only the part of the window that changed is sent, as an image over what the player shows, and
     * after {@code layers} of those, the whole frame again.
     *
     * @param layers overlays between whole frames; 0 or less sends every frame whole
     * @param backtracking whether a frame the player still shows under later overlays, as when a
     *     dialog closes, is sent as the removal of those overlays rather than as a new image
     * @return the method
     */
    public static CompressionMethod overlayTiles(int layers, boolean backtracking) {
      return new CompressionMethod(false, layers, backtracking);
    }

    /** Whether every frame is sent whole, with {@link #screenImage()}. */
    public boolean isScreenImage() {
      return screenImage;
    }

    /** Overlays between whole frames, for {@link #overlayTiles(int, boolean)}; 0 for {@link #screenImage()}. */
    public int getLayers() {
      return layers;
    }

    /** Whether a return to an earlier frame removes overlays instead of sending an image. */
    public boolean isBacktracking() {
      return backtracking;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof CompressionMethod)) {
        return false;
      }
      CompressionMethod that = (CompressionMethod) other;
      return screenImage == that.screenImage && layers == that.layers && backtracking == that.backtracking;
    }

    @Override
    public int hashCode() {
      return (screenImage ? 1 : 0) + 31 * layers + (backtracking ? 961 : 0);
    }

    @Override
    public String toString() {
      return screenImage ? "screenImage" : "overlayTiles(" + layers + ", " + backtracking + ")";
    }
  }

  /** Builds {@link ReplayOptions}. */
  public static final class Builder {
    private boolean enabled = true;
    private PrivacyProfile privacyProfile = PrivacyProfile.defaults();
    private double sampleRate = 1;
    private double frameRate = 1;
    private double scale = 1;
    private double imageQuality = 0.3;
    private CompressionMethod compression = CompressionMethod.overlayTiles();
    private boolean debug;

    private Builder() {}

    /**
     * Whether replay starts recording as soon as it is installed. True by default; when false,
     * recording starts at {@link LDReplay#start()}.
     *
     * @param enabled whether to start at once
     * @return the builder
     */
    public Builder enabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    /**
     * What is masked. {@link PrivacyProfile#defaults()} by default.
     *
     * @param privacyProfile the profile
     * @return the builder
     */
    public Builder privacyProfile(PrivacyProfile privacyProfile) {
      this.privacyProfile = Objects.requireNonNull(privacyProfile, "privacyProfile");
      return this;
    }

    /**
     * The share of sessions recorded: 1, the default, records every one; 0 none. Values outside 0
     * to 1 are taken as the nearest. The choice is made once each time replay is enabled.
     *
     * @param sampleRate the share
     * @return the builder
     */
    public Builder sampleRate(double sampleRate) {
      this.sampleRate = clamp(sampleRate, 0, 1, 1);
      return this;
    }

    /**
     * Frames taken a second, at most; 1 by default. Only what changed since the last frame is
     * sent. Values are kept between 0.1 and 10.
     *
     * @param frameRate frames a second
     * @return the builder
     */
    public Builder frameRate(double frameRate) {
      this.frameRate = clamp(frameRate, MIN_FRAME_RATE, MAX_FRAME_RATE, 1);
      return this;
    }

    /**
     * Frame pixels per logical pixel of the window; 1 by default, which on a HiDPI screen is fewer
     * pixels than the screen shows. Values are kept between 0.1 and 4.
     *
     * @param scale the scale
     * @return the builder
     */
    public Builder scale(double scale) {
      this.scale = clamp(scale, MIN_SCALE, MAX_SCALE, 1);
      return this;
    }

    /**
     * The frames' JPEG quality, from 0 to 1; 0.3 by default. Values outside are taken as the nearest.
     *
     * @param imageQuality the quality
     * @return the builder
     */
    public Builder imageQuality(double imageQuality) {
      this.imageQuality = clamp(imageQuality, 0, 1, 0.3);
      return this;
    }

    /**
     * How frames are sent; {@link CompressionMethod#overlayTiles()} by default.
     *
     * @param compression the method
     * @return the builder
     */
    public Builder compression(CompressionMethod compression) {
      this.compression = Objects.requireNonNull(compression, "compression");
      return this;
    }

    /**
     * Logs replay's own activity in detail, such as each batch it sends, at debug level, even when
     * {@code ObservabilityOptions.debug} is off. False by default.
     *
     * @param debug whether to log in detail
     * @return the builder
     */
    public Builder debug(boolean debug) {
      this.debug = debug;
      return this;
    }

    /** Builds the options. */
    public ReplayOptions build() {
      return new ReplayOptions(this);
    }

    /** The value kept in range; NaN, which no range holds, becomes the default. */
    private static double clamp(double value, double min, double max, double fallback) {
      if (Double.isNaN(value)) {
        return fallback;
      }
      return Math.max(min, Math.min(max, value));
    }
  }
}
