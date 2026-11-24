package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.observability.PrivacyProfile;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What session replay masks: the rules the privacy options set, and the components the application
 * has marked itself. Shared by the Swing and JavaFX masks.
 * <p>
 * A component is masked, in order of precedence, when:
 * <ol>
 *   <li>it or an ancestor is marked to mask, by {@link #mark}, by name or by class;</li>
 *   <li>otherwise, unless it or an ancestor is marked to unmask, when the rules for its kind say so,
 *   or, with {@link #maskSensitive}, when its text names something sensitive.</li>
 * </ol>
 * Password fields are masked whatever the marks say. These are the Android plugin's rules.
 */
public final class MaskRules {
  /** The property a Swing component or JavaFX node can carry: {@code Boolean.TRUE} masks, {@code FALSE} unmasks. */
  public static final String PROPERTY = "launchdarkly.replay.mask";
  /** Android's default {@code minimumAlpha}: fainter nodes draw nothing worth masking. */
  static final double DEFAULT_MINIMUM_ALPHA = PrivacyProfile.DEFAULT_MINIMUM_ALPHA;
  /** Android's keywords for {@code maskBySemanticsKeywords}, matched anywhere in a text, ignoring case. */
  static final String[] SENSITIVE_KEYWORDS = {"sensitive", "private", "name", "email", "username", "cell",
      "mobile", "phone", "address", "street", "dob", "birth", "password", "account", "ssn", "social",
      "security", "credit", "debit", "card", "cvv", "mm/yy", "pin"};
  /** Only the start of a longer text is searched for keywords, so a large document costs little. */
  static final int SENSITIVE_SEARCH_LIMIT = 4096;

  final boolean maskTextInputs;
  final boolean maskText;
  final boolean maskImages;
  final boolean maskWebViews;
  /** Masks a component whose text, hint, tooltip or accessible name has a {@link #SENSITIVE_KEYWORDS keyword}. */
  final boolean maskSensitive;
  /** JavaFX nodes less opaque than this, with their subtrees, are not masked: they show nothing. */
  final double minimumAlpha;
  final Set<String> maskNames;
  final Set<String> unmaskNames;
  final Set<String> maskClasses;
  final Set<String> unmaskClasses;
  // Marks made at run time, for the whole process, held weakly so a marked component can still be
  // collected. Compared by identity of the key's equals, which components and nodes do not override.
  private static final Map<Object, Boolean> MARKS = Collections.synchronizedMap(new WeakHashMap<>());

  MaskRules(boolean maskTextInputs, boolean maskText, boolean maskImages, Set<String> maskNames,
      Set<String> unmaskNames, Set<String> maskClasses, Set<String> unmaskClasses) {
    this(maskTextInputs, maskText, maskImages, false, false, DEFAULT_MINIMUM_ALPHA, maskNames, unmaskNames,
        maskClasses, unmaskClasses);
  }

  MaskRules(boolean maskTextInputs, boolean maskText, boolean maskImages, boolean maskWebViews,
      boolean maskSensitive, double minimumAlpha, Set<String> maskNames, Set<String> unmaskNames,
      Set<String> maskClasses, Set<String> unmaskClasses) {
    this.maskTextInputs = maskTextInputs;
    this.maskText = maskText;
    this.maskImages = maskImages;
    this.maskWebViews = maskWebViews;
    this.maskSensitive = maskSensitive;
    this.minimumAlpha = minimumAlpha;
    this.maskNames = Collections.unmodifiableSet(new HashSet<>(maskNames));
    this.unmaskNames = Collections.unmodifiableSet(new HashSet<>(unmaskNames));
    this.maskClasses = Collections.unmodifiableSet(new HashSet<>(maskClasses));
    this.unmaskClasses = Collections.unmodifiableSet(new HashSet<>(unmaskClasses));
  }

  /**
   * Whether any of the texts has a sensitive keyword, ignoring case. Read only to decide what to
   * mask: the text itself is never recorded.
   */
  static boolean sensitive(String... texts) {
    for (String text : texts) {
      if (text == null) {
        continue;
      }
      int end = Math.min(text.length(), SENSITIVE_SEARCH_LIMIT);
      for (String keyword : SENSITIVE_KEYWORDS) {
        for (int i = 0; i + keyword.length() <= end; i++) {
          if (text.regionMatches(true, i, keyword, 0, keyword.length())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /** The default, as on Android: text inputs are masked; labels, lists and images are shown. */
  static MaskRules defaults() {
    Set<String> none = Collections.emptySet();
    return new MaskRules(true, false, false, none, none, none, none);
  }

  /** The strict preset: every input and every piece of text is masked; images are shown. */
  static MaskRules strict() {
    Set<String> none = Collections.emptySet();
    return new MaskRules(true, true, false, none, none, none, none);
  }

  /** Marks a component or node to mask ({@code true}), to unmask ({@code false}), or neither ({@code null}). */
  public static void mark(Object component, Boolean mask) {
    if (mask == null) {
      MARKS.remove(component);
    } else {
      MARKS.put(component, mask);
    }
  }

  /**
   * The mark a component carries itself, from {@link #mark}, its property, its name or its class,
   * or null. {@code property} and {@code name} are the toolkit's.
   */
  Boolean markOf(Object component, Object property, String name) {
    Boolean marked = MARKS.get(component);
    if (marked != null) {
      return marked;
    }
    if (property instanceof Boolean) {
      return (Boolean) property;
    }
    String type = component.getClass().getName();
    if ((name != null && maskNames.contains(name)) || maskClasses.contains(type)) {
      return Boolean.TRUE;
    }
    if ((name != null && unmaskNames.contains(name)) || unmaskClasses.contains(type)) {
      return Boolean.FALSE;
    }
    return null;
  }

  /** How a component's kind is masked by the rules alone. */
  enum Kind {
    /** Never masked by the rules, such as a panel. */
    NONE,
    /** Always masked, whatever the marks: a password field. */
    SECRET,
    /** Masked by {@link #maskTextInputs}. */
    INPUT,
    /** Masked by {@link #maskText}. */
    TEXT,
    /** Masked by {@link #maskImages}. */
    IMAGE,
    /** Masked by {@link #maskWebViews}: a web view that paints offscreen, such as JavaFX's. */
    WEB,
    /** Always masked unless unmarked: content that cannot be painted offscreen, such as a native view. */
    OPAQUE
  }

  boolean masks(Kind kind) {
    switch (kind) {
      case INPUT:
        return maskTextInputs;
      case TEXT:
        return maskText;
      case IMAGE:
        return maskImages;
      case WEB:
        return maskWebViews;
      case OPAQUE:
        return true;
      default:
        return false;
    }
  }
}
