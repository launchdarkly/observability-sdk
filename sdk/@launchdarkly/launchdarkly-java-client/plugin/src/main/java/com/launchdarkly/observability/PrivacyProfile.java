package com.launchdarkly.observability;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * What session replay masks: the parts of each window covered with a grey box before the frame
 * leaves the application. Masking happens in the application, so masked pixels are never sent.
 * <p>
 * The default, as on Android, masks text inputs and leaves labels, lists and images visible;
 * {@link #strict()} masks every piece of text as well. A password field is always masked, whatever
 * the profile says. Web views are shown unless {@link Builder#maskWebViews} masks them; content that
 * cannot be drawn offscreen, such as a heavyweight AWT {@code Canvas} or a JavaFX {@code MediaView},
 * is masked unless explicitly unmasked.
 * <p>
 * Beyond the profile, a component or node can be marked to mask or unmask, with its subtree: by
 * {@link LDReplay#mask} and {@link LDReplay#unmask}, by the {@link LDReplay#MASK_PROPERTY} client
 * property or node property, or on JavaFX by the {@link LDReplay#MASK_STYLE_CLASS} and
 * {@link LDReplay#UNMASK_STYLE_CLASS} style classes. A mark to mask wins over a mark to unmask on an
 * ancestor, and either wins over the profile's rules, as on Android.
 *
 * <pre><code>
 *     PrivacyProfile profile = PrivacyProfile.builder()
 *         .maskText(true)
 *         .addMaskName("accountPanel")
 *         .addUnmaskName("helpText")
 *         .build();
 * </code></pre>
 */
public final class PrivacyProfile {
  /** The default {@link Builder#minimumAlpha}, Android's and iOS's. */
  public static final double DEFAULT_MINIMUM_ALPHA = 0.02;

  private final boolean maskTextInputs;
  private final boolean maskText;
  private final boolean maskImages;
  private final boolean maskWebViews;
  private final boolean maskBySemanticsKeywords;
  private final double minimumAlpha;
  private final Set<String> maskNames;
  private final Set<String> unmaskNames;
  private final Set<String> maskClasses;
  private final Set<String> unmaskClasses;

  private PrivacyProfile(Builder builder) {
    this.maskTextInputs = builder.maskTextInputs;
    this.maskText = builder.maskText;
    this.maskImages = builder.maskImages;
    this.maskWebViews = builder.maskWebViews;
    this.maskBySemanticsKeywords = builder.maskBySemanticsKeywords;
    this.minimumAlpha = builder.minimumAlpha;
    this.maskNames = Collections.unmodifiableSet(new LinkedHashSet<>(builder.maskNames));
    this.unmaskNames = Collections.unmodifiableSet(new LinkedHashSet<>(builder.unmaskNames));
    this.maskClasses = Collections.unmodifiableSet(new LinkedHashSet<>(builder.maskClasses));
    this.unmaskClasses = Collections.unmodifiableSet(new LinkedHashSet<>(builder.unmaskClasses));
  }

  /** The default profile: text inputs are masked. */
  public static PrivacyProfile defaults() {
    return builder().build();
  }

  /** A strict profile: text inputs and every piece of text are masked. Images are shown. */
  public static PrivacyProfile strict() {
    return builder().maskText(true).build();
  }

  /** A builder, starting from the default profile. */
  public static Builder builder() {
    return new Builder();
  }

  /** Whether text inputs are masked: text fields and areas, in every toolkit, and SWT's editable combo boxes and spinners. */
  public boolean isMaskTextInputs() {
    return maskTextInputs;
  }

  /** Whether text is masked: labels, buttons, lists, tables, trees, tab titles. */
  public boolean isMaskText() {
    return maskText;
  }

  /** Whether images are masked: Swing labels with an icon, JavaFX image views. */
  public boolean isMaskImages() {
    return maskImages;
  }

  /** Whether web views are masked: JavaFX's {@code WebView} and JxBrowser's Swing {@code BrowserView}. */
  public boolean isMaskWebViews() {
    return maskWebViews;
  }

  /**
   * Whether a component is masked when its text, hint, tooltip or accessible name has a word such as
   * "email", "phone", "card" or "password", Android's list.
   */
  public boolean isMaskBySemanticsKeywords() {
    return maskBySemanticsKeywords;
  }

  /** The opacity below which a JavaFX node, with its subtree, is treated as not shown. */
  public double getMinimumAlpha() {
    return minimumAlpha;
  }

  /** The names of the components and ids of the nodes masked with their subtree. */
  public Set<String> getMaskNames() {
    return maskNames;
  }

  /** The names of the components and ids of the nodes unmasked with their subtree. */
  public Set<String> getUnmaskNames() {
    return unmaskNames;
  }

  /** The classes whose components or nodes are masked with their subtree, by exact class name. */
  public Set<String> getMaskClasses() {
    return maskClasses;
  }

  /** The classes whose components or nodes are unmasked with their subtree, by exact class name. */
  public Set<String> getUnmaskClasses() {
    return unmaskClasses;
  }

  @Override
  public String toString() {
    return "PrivacyProfile{maskTextInputs=" + maskTextInputs + ", maskText=" + maskText + ", maskImages="
        + maskImages + ", maskWebViews=" + maskWebViews + ", maskBySemanticsKeywords=" + maskBySemanticsKeywords
        + ", minimumAlpha=" + minimumAlpha + ", maskNames=" + maskNames + ", unmaskNames=" + unmaskNames
        + ", maskClasses=" + maskClasses + ", unmaskClasses=" + unmaskClasses + "}";
  }

  /** Builds a {@link PrivacyProfile}. */
  public static final class Builder {
    private boolean maskTextInputs = true;
    private boolean maskText;
    private boolean maskImages;
    private boolean maskWebViews;
    private boolean maskBySemanticsKeywords;
    private double minimumAlpha = DEFAULT_MINIMUM_ALPHA;
    private final Set<String> maskNames = new LinkedHashSet<>();
    private final Set<String> unmaskNames = new LinkedHashSet<>();
    private final Set<String> maskClasses = new LinkedHashSet<>();
    private final Set<String> unmaskClasses = new LinkedHashSet<>();

    private Builder() {}

    /**
     * Whether to mask text inputs. True by default.
     *
     * @param maskTextInputs whether to mask them
     * @return the builder
     */
    public Builder maskTextInputs(boolean maskTextInputs) {
      this.maskTextInputs = maskTextInputs;
      return this;
    }

    /**
     * Whether to mask all text. False by default.
     *
     * @param maskText whether to mask it
     * @return the builder
     */
    public Builder maskText(boolean maskText) {
      this.maskText = maskText;
      return this;
    }

    /**
     * Whether to mask images. False by default.
     *
     * @param maskImages whether to mask them
     * @return the builder
     */
    public Builder maskImages(boolean maskImages) {
      this.maskImages = maskImages;
      return this;
    }

    /**
     * Whether to mask web views: JavaFX's {@code WebView} and JxBrowser's Swing {@code BrowserView}.
     * False by default, as on Android: a web view is recorded like the rest of the window.
     *
     * @param maskWebViews whether to mask them
     * @return the builder
     */
    public Builder maskWebViews(boolean maskWebViews) {
      this.maskWebViews = maskWebViews;
      return this;
    }

    /**
     * Whether to mask a component or node whose text, hint (a JavaFX prompt text or a Swing
     * {@code JTextField.placeholderText}), tooltip or accessible name or description has one of
     * Android's sensitive words, such as "email", "phone", "address", "card", "account" or "password",
     * ignoring case. False by default. The text is read in the application to decide what to mask;
     * it is never recorded. The match covers only the component itself, and an unmask wins over it.
     *
     * @param maskBySemanticsKeywords whether to mask them
     * @return the builder
     */
    public Builder maskBySemanticsKeywords(boolean maskBySemanticsKeywords) {
      this.maskBySemanticsKeywords = maskBySemanticsKeywords;
      return this;
    }

    /**
     * The opacity, from 0 to 1, below which a JavaFX node and its subtree are treated as not shown,
     * so nothing in them is masked; 0.02 by default, as on Android. A fully transparent node is never
     * shown. Swing and AWT components have no opacity of their own. Values outside 0 to 1 are taken
     * as the nearest; NaN as the default.
     *
     * @param minimumAlpha the opacity
     * @return the builder
     */
    public Builder minimumAlpha(double minimumAlpha) {
      this.minimumAlpha = Double.isNaN(minimumAlpha) ? DEFAULT_MINIMUM_ALPHA : Math.max(0, Math.min(1, minimumAlpha));
      return this;
    }

    /**
     * Masks the component with this name ({@code Component.setName}), or the node with this id
     * ({@code Node.setId}), and everything in it.
     *
     * @param name the name or id
     * @return the builder
     */
    public Builder addMaskName(String name) {
      maskNames.add(Objects.requireNonNull(name, "name"));
      return this;
    }

    /**
     * Unmasks the component with this name, or the node with this id, and everything in it, unless
     * a mark to mask says otherwise. A password field stays masked.
     *
     * @param name the name or id
     * @return the builder
     */
    public Builder addUnmaskName(String name) {
      unmaskNames.add(Objects.requireNonNull(name, "name"));
      return this;
    }

    /**
     * Masks every component or node of exactly this class, and everything in it. Subclasses are not
     * matched, as on Android.
     *
     * @param type the class
     * @return the builder
     */
    public Builder addMaskClass(Class<?> type) {
      return addMaskClass(Objects.requireNonNull(type, "type").getName());
    }

    /**
     * Masks every component or node of the class with exactly this name, which need not be on the
     * class path when the profile is built.
     *
     * @param className the class's binary name, as {@link Class#getName()} gives it
     * @return the builder
     */
    public Builder addMaskClass(String className) {
      maskClasses.add(Objects.requireNonNull(className, "className"));
      return this;
    }

    /**
     * Unmasks every component or node of exactly this class, and everything in it, unless a mark to
     * mask says otherwise. Subclasses are not matched. A password field stays masked.
     *
     * @param type the class
     * @return the builder
     */
    public Builder addUnmaskClass(Class<?> type) {
      return addUnmaskClass(Objects.requireNonNull(type, "type").getName());
    }

    /**
     * Unmasks every component or node of the class with exactly this name, which need not be on the
     * class path when the profile is built.
     *
     * @param className the class's binary name, as {@link Class#getName()} gives it
     * @return the builder
     */
    public Builder addUnmaskClass(String className) {
      unmaskClasses.add(Objects.requireNonNull(className, "className"));
      return this;
    }

    /** Builds the profile. */
    public PrivacyProfile build() {
      return new PrivacyProfile(this);
    }
  }
}
