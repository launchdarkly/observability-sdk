package com.launchdarkly.observability.internal;

import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.semantics.SemanticsActions;
import androidx.compose.ui.semantics.SemanticsConfiguration;
import androidx.compose.ui.semantics.SemanticsConfigurationKt;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsProperties;
import androidx.compose.ui.semantics.SemanticsPropertyKey;
import androidx.compose.ui.text.AnnotatedString;

import org.jetbrains.skia.Bitmap;
import org.jetbrains.skiko.Convertors_jvmKt;
import org.jetbrains.skiko.SkiaLayer;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;

/**
 * Compose for Desktop, which draws on a GPU canvas inside one AWT component, skiko's
 * {@code SkiaLayer}. AWT can paint neither the canvas nor what is on it, so the plugin asks skiko for
 * the last frame it drew, and Compose's semantics tree, the one Android's plugin reads, for what is
 * where.
 * <p>
 * Compose is never a dependency of the plugin. This class is loaded only for a component whose class
 * is the layer's, by name ({@link #LAYER}), so only when the application has Compose; callers catch
 * the {@link LinkageError} of a Compose too different from the one the plugin is built against.
 * Called on the event dispatch thread, which is Compose's on the desktop.
 */
public final class ComposeSupport {
  /** The AWT component that Compose draws on, a window's or a {@code ComposePanel}'s. */
  public static final String LAYER = "org.jetbrains.skiko.SkiaLayer";
  /**
   * The semantics property that masks a composable, {@code true}, or unmasks it, {@code false}: set by
   * {@code Modifier.ldMask()} and {@code Modifier.ldUnmask()}, as on Android.
   */
  public static final String MASK_KEY = "ld_mask";
  /** The semantics property that names a composable for clicks: set by {@code Modifier.ldId()}, as on Android. */
  public static final String ID_KEY = "ld_id";
  /** Each accessible class's method to its semantics node, or none: Compose's accessible objects have one. */
  private static final Map<Class<?>, Optional<Method>> SEMANTICS_NODE =
      Collections.synchronizedMap(new IdentityHashMap<>());

  private ComposeSupport() {}

  /**
   * The roots of the semantics trees on a layer, its content's and each popup's, topmost first, as
   * Compose lists them. Compose's accessibility tree, which leads to them, is updated after the frame, so only
   * its roots are taken from it: below them, {@link SemanticsNode#getChildren} is read from the
   * layout as it is now.
   */
  public static List<SemanticsNode> roots(Component layer) {
    List<SemanticsNode> out = new ArrayList<>();
    Set<Object> trees = Collections.newSetFromMap(new IdentityHashMap<>());
    collectRoots(((Accessible) layer).getAccessibleContext(), trees, out);
    return out;
  }

  private static void collectRoots(AccessibleContext context, Set<Object> trees, List<SemanticsNode> out) {
    if (context == null) {
      return;
    }
    for (int i = 0; i < context.getAccessibleChildrenCount(); i++) {
      Accessible child = context.getAccessibleChild(i);
      if (child == null) {
        continue;
      }
      SemanticsNode node = semanticsNode(child);
      if (node == null) {
        collectRoots(child.getAccessibleContext(), trees, out); // Compose's are two down
        continue;
      }
      while (node.getParent() != null) {
        node = node.getParent();
      }
      if (trees.add(node.getLayoutInfo())) { // a tree's root layout node is the same every time
        out.add(node);
      }
    }
  }

  /** The semantics node of one of Compose's accessible objects, or null for any other. */
  static SemanticsNode semanticsNode(Accessible accessible) {
    Optional<Method> method = SEMANTICS_NODE.computeIfAbsent(accessible.getClass(), type -> {
      try {
        return Optional.of(type.getMethod("getSemanticsNode"));
      } catch (NoSuchMethodException e) {
        return Optional.empty();
      }
    });
    if (!method.isPresent()) {
      return null;
    }
    try {
      return (SemanticsNode) method.get().invoke(accessible);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Layer pixels per Compose pixel: Compose lays out in the screen's pixels. */
  public static double scale(Component layer) {
    return 1 / (double) ((SkiaLayer) layer).getContentScale();
  }

  /** A node's bounds in the layer, in its pixels, cut to what its ancestors show. */
  public static Rectangle bounds(SemanticsNode node, double scale) {
    Rect r = node.getBoundsInWindow();
    int left = (int) Math.floor(r.getLeft() * scale);
    int top = (int) Math.floor(r.getTop() * scale);
    int right = (int) Math.ceil(r.getRight() * scale);
    int bottom = (int) Math.ceil(r.getBottom() * scale);
    return new Rectangle(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
  }

  /** Whether the node is laid out where it is drawn. */
  public static boolean placed(SemanticsNode node) {
    return node.getLayoutInfo().isPlaced();
  }

  /** The value of the property of this name, which may be another library's key, or null. */
  public static Object property(SemanticsConfiguration config, String name) {
    for (Map.Entry<? extends SemanticsPropertyKey<?>, ?> entry : config) {
      if (name.equals(entry.getKey().getName())) {
        return entry.getValue();
      }
    }
    return null;
  }

  /** The node's role, such as {@code Button}, or null. */
  public static String role(SemanticsConfiguration config) {
    Object role = SemanticsConfigurationKt.getOrNull(config, SemanticsProperties.INSTANCE.getRole());
    return role != null ? role.toString() : null;
  }

  /** The node's test tag, or null. */
  public static String testTag(SemanticsConfiguration config) {
    return nonEmpty(SemanticsConfigurationKt.getOrNull(config, SemanticsProperties.INSTANCE.getTestTag()));
  }

  public static boolean isPassword(SemanticsConfiguration config) {
    return config.contains(SemanticsProperties.INSTANCE.getPassword());
  }

  public static boolean isInput(SemanticsConfiguration config) {
    return config.contains(SemanticsProperties.INSTANCE.getEditableText());
  }

  public static boolean hasText(SemanticsConfiguration config) {
    return config.contains(SemanticsProperties.INSTANCE.getText());
  }

  /** The node's text, its pieces joined with spaces, or null. */
  public static String text(SemanticsConfiguration config) {
    List<AnnotatedString> text = SemanticsConfigurationKt.getOrNull(config, SemanticsProperties.INSTANCE.getText());
    return text == null ? null : nonEmpty(text.stream().map(AnnotatedString::getText).collect(Collectors.joining(" ")));
  }

  /** A text field's text, or null. Never a password's: callers check {@link #isPassword} first. */
  public static String editableText(SemanticsConfiguration config) {
    AnnotatedString text = SemanticsConfigurationKt.getOrNull(config, SemanticsProperties.INSTANCE.getEditableText());
    return text != null ? nonEmpty(text.getText()) : null;
  }

  /** The node's content description, its pieces joined with spaces, or null. */
  public static String contentDescription(SemanticsConfiguration config) {
    List<String> description = SemanticsConfigurationKt.getOrNull(config,
        SemanticsProperties.INSTANCE.getContentDescription());
    return description == null ? null : nonEmpty(String.join(" ", description));
  }

  /**
   * A click at this point of the layer, in its pixels: the composable there, placed below the
   * layer's own path. That is the innermost clickable node under the mouse, such as a button, rather
   * than the text inside it, or the deepest node when none is clickable. Its id is the deepest
   * {@link #ID_KEY} on the way there, as on Android, else its test tag. Null when no node is there.
   */
  static Click click(Component layer, int x, int y, boolean recordText, String layerPath, long screenX, long screenY,
      long start, long end) {
    double scale = scale(layer);
    List<SemanticsNode> hit = new ArrayList<>();
    for (SemanticsNode root : roots(layer)) {
      if (hitPath(root, x, y, scale, hit)) {
        break; // the topmost: a popup over the content is listed before it
      }
    }
    if (hit.isEmpty()) {
      return null;
    }
    int target = hit.size() - 1;
    while (target > 0 && !clickable(hit.get(target).getConfig())) {
      target--;
    }
    if (!clickable(hit.get(target).getConfig())) {
      target = hit.size() - 1;
    }
    String id = null;
    for (SemanticsNode node : hit) {
      Object ldId = property(node.getConfig(), ID_KEY);
      if (ldId instanceof String && !((String) ldId).isEmpty()) {
        id = (String) ldId;
      }
    }
    if (id == null) {
      id = testTag(hit.get(target).getConfig());
    }
    String text = null;
    for (int i = target; recordText && text == null && i < hit.size(); i++) {
      text = label(hit.get(i).getConfig());
    }
    List<String> segments = new ArrayList<>();
    for (int i = Math.max(0, target + 1 - DesktopInstrumentation.MAX_PATH_DEPTH); i <= target; i++) {
      SemanticsConfiguration config = hit.get(i).getConfig();
      String tag = testTag(config);
      segments.add(tag(config) + (tag != null ? "#" + tag : ""));
    }
    String tag = tag(hit.get(target).getConfig());
    return new Click(tag, tag, id, DesktopInstrumentation.clip(text), layerPath + "/" + String.join("/", segments),
        screenX, screenY, start, end);
  }

  /**
   * Adds to the path the node at the point and, below it, the topmost of its descendants there, and
   * so on down. False, adding nothing, when the node is not at the point.
   */
  private static boolean hitPath(SemanticsNode node, int x, int y, double scale, List<SemanticsNode> path) {
    if (!placed(node) || !bounds(node, scale).contains(x, y)) {
      return false;
    }
    path.add(node);
    List<SemanticsNode> children = node.getChildren();
    for (int i = children.size() - 1; i >= 0; i--) { // later children are drawn over earlier ones
      if (hitPath(children.get(i), x, y, scale, path)) {
        break;
      }
    }
    return true;
  }

  /** What a click names a node: its role, such as {@code Button}, or {@code Composable}. */
  private static String tag(SemanticsConfiguration config) {
    String role = role(config);
    return role != null ? role : "Composable";
  }

  private static boolean clickable(SemanticsConfiguration config) {
    return config.contains(SemanticsActions.INSTANCE.getOnClick());
  }

  /** What a click on the node says, as on Android: its text, else its description. Never a password's. */
  static String label(SemanticsConfiguration config) {
    if (isPassword(config)) {
      return null;
    }
    String text = text(config);
    return text != null ? text : contentDescription(config);
  }

  /**
   * The last frame skiko drew on the layer, in its pixels times its content scale, or null when it
   * has none. Skiko replays the frame's drawing commands on the CPU, whatever the GPU, so this costs
   * about a millisecond for a window.
   */
  public static BufferedImage screenshot(Component layer) {
    Bitmap bitmap = ((SkiaLayer) layer).screenshot();
    if (bitmap == null) {
      return null;
    }
    try {
      return Convertors_jvmKt.toBufferedImage(bitmap);
    } finally {
      bitmap.close();
    }
  }

  private static String nonEmpty(String s) {
    return s == null || s.isEmpty() ? null : s;
  }
}
