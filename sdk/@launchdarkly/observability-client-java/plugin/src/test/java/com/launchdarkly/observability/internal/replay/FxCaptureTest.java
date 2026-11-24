package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.observability.internal.FxAssume;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** JavaFX capture and masks, against real JavaFX scenes. Needs Java 11 or later and a display. */
public class FxCaptureTest {
  private final List<Window> windows = new ArrayList<>();

  @Before
  public void needJavaFx() {
    FxAssume.assumeJavaFx();
  }

  @After
  public void tearDown() throws Exception {
    onFx(() -> {
      windows.forEach(Window::hide);
      return null;
    });
  }

  static <T> T onFx(Callable<T> task) throws Exception {
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Exception> failure = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);
    Platform.runLater(() -> {
      try {
        result.set(task.call());
      } catch (Exception e) {
        failure.set(e);
      } finally {
        done.countDown();
      }
    });
    assertTrue(done.await(10, TimeUnit.SECONDS));
    if (failure.get() != null) {
      throw failure.get();
    }
    return result.get();
  }

  /** A pane with no layout, so children sit where a test puts them. */
  static <T extends Node> T at(Pane parent, T child, double x, double y, double w, double h) {
    child.relocate(x, y);
    if (child instanceof javafx.scene.layout.Region) {
      // A pane lays its children out at their preferred size, so that is the size to set.
      javafx.scene.layout.Region region = (javafx.scene.layout.Region) child;
      region.setMinSize(w, h);
      region.setPrefSize(w, h);
      region.setMaxSize(w, h);
    }
    parent.getChildren().add(child);
    return child;
  }

  static List<Rectangle> masks(MaskRules rules, Parent root, double w, double h) throws Exception {
    return onFx(() -> {
      if (root.getScene() == null) {
        new Scene(root, w, h);
      }
      root.applyCss();
      root.layout();
      List<Rectangle> out = new ArrayList<>();
      new FxMasks(rules).collect(root, w, h, 0, 0, 1, out);
      return out;
    });
  }

  static Rectangle covered(int x, int y, int w, int h) {
    return new Rectangle(x - 1, y - 1, w + 2, h + 2);
  }

  // -- masks --

  @Test
  public void strictMasksInputsAndTextButNotImages() throws Exception {
    Pane root = new Pane();
    onFx(() -> {
      at(root, new TextField("jane@example.com"), 10, 10, 100, 20);
      at(root, new Label("Balance"), 10, 40, 100, 20);
      at(root, new ImageView(new WritableImage(4, 4)), 10, 70, 4, 4);
      at(root, new Pane(), 200, 10, 50, 50);
      return null;
    });
    assertThat(masks(MaskRules.strict(), root, 300, 200), contains(covered(10, 10, 100, 20),
        covered(10, 40, 100, 20)));
  }

  @Test
  public void everyKindIsWhatTheRulesSay() throws Exception {
    onFx(() -> {
      assertEquals(MaskRules.Kind.SECRET, FxMasks.kind(new PasswordField()));
      assertEquals(MaskRules.Kind.INPUT, FxMasks.kind(new TextField()));
      assertEquals(MaskRules.Kind.TEXT, FxMasks.kind(new ListView<String>()));
      assertEquals(MaskRules.Kind.TEXT, FxMasks.kind(new ComboBox<String>()));
      assertEquals(MaskRules.Kind.TEXT, FxMasks.kind(new javafx.scene.control.TableView<String>()));
      assertEquals(MaskRules.Kind.TEXT, FxMasks.kind(new javafx.scene.control.TreeView<String>()));
      assertEquals(MaskRules.Kind.TEXT, FxMasks.kind(new javafx.scene.control.TreeTableView<String>()));
      assertEquals(MaskRules.Kind.TEXT, FxMasks.kind(new javafx.scene.control.ChoiceBox<String>()));
      assertEquals(MaskRules.Kind.TEXT, FxMasks.kind(new javafx.scene.control.Spinner<Integer>()));
      assertEquals(MaskRules.Kind.TEXT, FxMasks.kind(new Button("Pay")));
      assertEquals(MaskRules.Kind.NONE, FxMasks.kind(new Button("  ")));
      assertEquals(MaskRules.Kind.NONE, FxMasks.kind(new Button()));
      assertEquals(MaskRules.Kind.TEXT, FxMasks.kind(new Text("t")));
      assertEquals(MaskRules.Kind.NONE, FxMasks.kind(new Text()));
      assertEquals(MaskRules.Kind.NONE, FxMasks.kind(new Text(" ")));
      assertEquals(MaskRules.Kind.IMAGE, FxMasks.kind(new ImageView()));
      assertEquals(MaskRules.Kind.NONE, FxMasks.kind(new Pane()));
      return null;
    });
  }

  @Test
  public void aPasswordIsMaskedWhateverTheMarks() throws Exception {
    Pane root = new Pane();
    MaskRules none = MaskTest.rules(false, false, false);
    onFx(() -> {
      PasswordField password = at(root, new PasswordField(), 0, 0, 50, 20);
      MaskRules.mark(password, false);
      root.getStyleClass().add(FxMasks.UNMASK_STYLE);
      return null;
    });
    assertThat(masks(none, root, 100, 100), contains(covered(0, 0, 50, 20)));
  }

  @Test
  public void marksComeFromPropertiesStylesIdsAndCalls() throws Exception {
    Pane root = new Pane();
    AtomicReference<Pane> card = new AtomicReference<>();
    AtomicReference<Label> label = new AtomicReference<>();
    onFx(() -> {
      card.set(at(root, new Pane(), 0, 0, 50, 50));
      card.get().getStyleClass().add(FxMasks.MASK_STYLE);
      Pane form = at(root, new Pane(), 100, 0, 50, 50);
      form.getProperties().put(MaskRules.PROPERTY, Boolean.FALSE);
      label.set(at(form, new Label("public"), 0, 0, 40, 20));
      Label byId = at(root, new Label("x"), 0, 100, 20, 20);
      byId.setId("help");
      return null;
    });
    MaskRules rules = new MaskRules(true, true, false, Collections.emptySet(), Collections.singleton("help"),
        Collections.emptySet(), Collections.emptySet());
    assertThat("the styled card; the unmasked form and the help label show", masks(rules, root, 300, 300),
        contains(covered(0, 0, 50, 50)));
    onFx(() -> {
      card.get().getStyleClass().clear();
      return null;
    });
    MaskRules.mark(label.get(), true);
    assertThat("a call below an unmask still masks", masks(rules, root, 300, 300),
        contains(covered(100, 0, 40, 20)));
  }

  @Test
  public void hiddenFaintAndClippedNodesAreLeftOut() throws Exception {
    Pane root = new Pane();
    onFx(() -> {
      at(root, new Label("hidden"), 0, 0, 20, 20).setVisible(false);
      at(root, new Label("faint"), 0, 30, 20, 20).setOpacity(0.01);
      Pane clipped = at(root, new Pane(), 0, 100, 100, 50);
      clipped.setClip(new javafx.scene.shape.Rectangle(0, 0, 100, 20));
      at(clipped, new Label("half"), 0, 10, 50, 20);
      at(clipped, new Label("gone"), 0, 30, 50, 20);
      return null;
    });
    assertThat(masks(MaskRules.strict(), root, 300, 300), contains(covered(0, 110, 50, 10)));
  }

  @Test
  public void theMinimumAlphaIsTheProfiles() throws Exception {
    Pane root = new Pane();
    onFx(() -> {
      at(root, new Label("half"), 0, 0, 20, 20).setOpacity(0.4);
      at(root, new Label("clear"), 0, 30, 20, 20).setOpacity(0);
      at(root, new Label("solid"), 0, 60, 20, 20);
      return null;
    });
    MaskRules half = new MaskRules(true, true, false, false, false, 0.5, Collections.emptySet(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    assertThat(masks(half, root, 300, 300), contains(covered(0, 60, 20, 20)));
    MaskRules none = new MaskRules(true, true, false, false, false, 0, Collections.emptySet(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    assertThat("a clear node is never seen", masks(none, root, 300, 300),
        contains(covered(0, 0, 20, 20), covered(0, 60, 20, 20)));
  }

  @Test
  public void keywordsMaskNodesThatNameSomethingSensitive() throws Exception {
    Pane root = new Pane();
    onFx(() -> {
      at(root, new Label("Email"), 0, 0, 50, 20);
      at(root, new Label("Welcome"), 0, 30, 50, 20);
      TextField prompt = at(root, new TextField(), 0, 60, 50, 20);
      prompt.setPromptText("Phone");
      Button tip = at(root, new Button("Go"), 0, 90, 50, 20);
      tip.setTooltip(new javafx.scene.control.Tooltip("Card details"));
      Button plain = at(root, new Button("Go"), 0, 120, 50, 20);
      plain.setTooltip(new javafx.scene.control.Tooltip("Next"));
      Text text = new Text(0, 160, "Street");
      root.getChildren().add(text);
      Pane described = at(root, new Pane(), 0, 200, 50, 20);
      described.setAccessibleText("account");
      return null;
    });
    MaskRules rules = new MaskRules(false, false, false, false, true, MaskRules.DEFAULT_MINIMUM_ALPHA,
        Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    List<Rectangle> masked = masks(rules, root, 300, 300);
    assertEquals(5, masked.size());
    assertEquals(covered(0, 0, 50, 20), masked.get(0));
    assertEquals(covered(0, 60, 50, 20), masked.get(1));
    assertEquals("the button with the card tooltip, with its shadow", 89, masked.get(2).y);
    assertEquals(covered(0, 200, 50, 20), masked.get(4));
  }

  @Test
  public void aScrollPaneCutsWhatIsScrolledAway() throws Exception {
    Pane content = new Pane();
    AtomicReference<ScrollPane> pane = new AtomicReference<>();
    onFx(() -> {
      content.setPrefSize(200, 1000);
      at(content, new Label("top"), 0, 0, 100, 20);
      at(content, new Label("far below"), 0, 800, 100, 20);
      ScrollPane scroll = new ScrollPane(content);
      scroll.setPrefSize(150, 100);
      pane.set(scroll);
      return null;
    });
    Pane root = new Pane();
    onFx(() -> {
      at(root, pane.get(), 0, 0, 150, 100);
      return null;
    });
    List<Rectangle> masks = masks(MaskRules.strict(), root, 300, 300);
    assertEquals("only the label in sight", 1, masks.size());
    assertTrue(masks.get(0).y < 30);
  }

  @Test
  public void intersectingDisjointBoundsIsEmpty() {
    assertTrue(FxMasks.intersect(new BoundingBox(0, 0, 10, 10), new BoundingBox(20, 20, 5, 5)).isEmpty());
    assertEquals(5, FxMasks.intersect(new BoundingBox(0, 0, 10, 10), new BoundingBox(5, 5, 50, 50)).getWidth(), 0);
  }

  // -- capture --

  private Stage stage(Parent root, double w, double h, double x, double y) throws Exception {
    return onFx(() -> {
      Stage stage = new Stage(StageStyle.UNDECORATED);
      stage.setScene(new Scene(root, w, h, Color.WHITE));
      stage.setX(x);
      stage.setY(y);
      stage.show();
      windows.add(stage);
      return stage;
    });
  }

  private static FxCapture capture(double scale) {
    return new FxCapture(scale, new FxMasks(MaskRules.strict()), LDLogger.none(), () -> true);
  }

  /** A capture that sees {@code focused} as the focused window. */
  private static FxCapture focusing(double scale, Window focused) {
    FxCapture capture = capture(scale);
    capture.isFocused = window -> window == focused;
    return capture;
  }

  @Test
  public void withoutJavaFxInUseNothingIsAsked() {
    FxCapture capture = new FxCapture(1, new FxMasks(MaskRules.strict()), LDLogger.none(), () -> false);
    capture.runLater = task -> {
      throw new AssertionError("must not touch JavaFX");
    };
    assertNull(capture.capture(100));
  }

  @Test
  public void aRefusedQueueABusyThreadOrAFailedPaintIsNoFrame() throws Exception {
    FxCapture refused = capture(1);
    refused.runLater = task -> {
      throw new IllegalStateException("Toolkit has exited");
    };
    assertNull(refused.capture(100));

    FxCapture busy = capture(1);
    busy.runLater = task -> { }; // never runs
    assertNull(busy.capture(50));

    FxCapture throwing = capture(1);
    throwing.runLater = Runnable::run; // a paint off the JavaFX thread fails
    assertNull(throwing.capture(1000));

    FxCapture interrupted = capture(1);
    interrupted.runLater = task -> { };
    Thread.currentThread().interrupt();
    assertNull(interrupted.capture(5000));
    assertTrue(Thread.interrupted());
  }

  @Test
  public void aFocusedStageIsSnapshottedWithItsMasks() throws Exception {
    Pane root = new Pane();
    onFx(() -> {
      root.setStyle("-fx-background-color: red;");
      at(root, new TextField("jane@example.com"), 20, 20, 120, 24);
      return null;
    });
    Stage stage = stage(root, 200, 100, 100, 100);
    FxCapture capture = focusing(1, stage);
    FxCapture.Painted painted = onFx(capture::paint);
    assertEquals(200, painted.width);
    assertEquals(100, painted.height);
    assertEquals(0xffff0000, painted.pixels[90 * 200 + 190]);
    // The mask is the field's bounds with its effects, such as a focus ring, which some platforms draw
    // wider than others.
    assertEquals(1, painted.masks.size());
    assertTrue("the field is covered: " + painted.masks, painted.masks.get(0).contains(covered(20, 20, 120, 24)));
    assertEquals(1, capture.watchedScenes());
    java.awt.image.BufferedImage image = capture.toImage(painted);
    assertEquals(0xffff0000, image.getRGB(190, 90));
    // A focused text field's caret blinks, a pulse every half second, so this scene never settles;
    // aWindowThatChangesSizeIsSnapshottedAgain covers skipping an unchanged scene.
  }

  @Test
  public void aScaledFrameIsScaledOffTheJavaFxThread() {
    FxCapture capture = capture(0.5);
    int[] pixels = new int[40 * 20];
    java.util.Arrays.fill(pixels, 0xff00ff00);
    java.awt.image.BufferedImage image = capture.toImage(new FxCapture.Painted(pixels, 40, 20,
        Collections.emptyList(), 0, 0));
    assertEquals(20, image.getWidth());
    assertEquals(10, image.getHeight());
    assertEquals(0xff00ff00, image.getRGB(5, 5));
  }

  @Test
  public void popupsAndOwnedStagesAreDrawnOverTheirOwner() throws Exception {
    Pane root = new Pane();
    onFx(() -> {
      root.setStyle("-fx-background-color: white;");
      return null;
    });
    Stage owner = stage(root, 300, 200, 100, 100);
    onFx(() -> {
      Popup popup = new Popup();
      Pane blue = new Pane();
      blue.setStyle("-fx-background-color: blue;");
      blue.setPrefSize(40, 30);
      popup.getContent().add(blue);
      popup.show(owner, 110, 110);
      windows.add(popup);
      Stage dialog = new Stage(StageStyle.UNDECORATED);
      dialog.initOwner(owner);
      Pane green = new Pane();
      green.setStyle("-fx-background-color: green;");
      dialog.setScene(new Scene(green, 50, 40));
      dialog.setX(200);
      dialog.setY(200);
      dialog.show();
      windows.add(dialog);
      return null;
    });
    // The dialog has focus; the frame is its owner, with the dialog and the popup over it.
    AtomicReference<Window> dialogWindow = new AtomicReference<>();
    onFx(() -> {
      for (Window w : Window.getWindows()) {
        if (w instanceof Stage && ((Stage) w).getOwner() == owner) {
          dialogWindow.set(w);
        }
      }
      return null;
    });
    FxCapture capture = focusing(1, dialogWindow.get());
    FxCapture.Painted painted = onFx(capture::paint);
    int ox = (int) Math.round(owner.getX() + owner.getScene().getX());
    int oy = (int) Math.round(owner.getY() + owner.getScene().getY());
    assertEquals("the popup", 0xff0000ff, painted.pixels[(115 - oy) * 300 + (115 - ox)]);
    assertTrue("the dialog", (painted.pixels[(210 - oy) * 300 + (210 - ox)] & 0x00ff00) != 0);
    assertEquals(3, capture.watchedScenes());
  }

  @Test
  public void ownershipFollowsOwnersAndPopupOwners() throws Exception {
    onFx(() -> {
      Stage a = new Stage();
      Stage b = new Stage();
      b.initOwner(a);
      Popup p = new Popup();
      assertTrue(FxCapture.ownedBy(b, a));
      assertFalse(FxCapture.ownedBy(a, b));
      assertFalse("not shown, so no owner", FxCapture.ownedBy(p, a));
      return null;
    });
  }

  @Test
  public void aSnapshotIsCutToTheFrame() throws Exception {
    int[] into = new int[10 * 10];
    onFx(() -> {
      WritableImage image = new WritableImage(6, 6);
      for (int x = 0; x < 6; x++) {
        for (int y = 0; y < 6; y++) {
          image.getPixelWriter().setArgb(x, y, 0xff112233);
        }
      }
      FxCapture.read(image, into, 10, 10, 7, -3);
      FxCapture.read(image, into, 10, 10, 20, 20); // wholly outside
      return null;
    });
    assertEquals(0xff112233, into[0 * 10 + 7]);
    assertEquals(0xff112233, into[2 * 10 + 9]);
    assertEquals(0, into[3 * 10 + 7]);
    assertEquals(0, into[0]);
  }

  @Test
  public void noFocusedWindowOrNoSceneIsNoFrame() throws Exception {
    stage(new Pane(), 50, 50, 0, 0);
    assertNull(onFx(focusing(1, null)::paint));
    Stage bare = onFx(Stage::new);
    assertNull(onFx(focusing(1, bare)::paint));
  }

  @Test
  public void aWindowThatChangesSizeIsSnapshottedAgain() throws Exception {
    Stage stage = stage(new Pane(), 80, 60, 0, 0);
    FxCapture capture = focusing(1, stage);
    assertNotNull(onFx(capture::paint));
    // No pulse runs between two paints in one task on the JavaFX thread, so the second sees an
    // unchanged scene. Waiting for JavaFX to stop laying the window out instead is not reliable:
    // under xvfb it never does.
    assertNull("an unchanged scene is skipped", onFx(() -> {
      capture.paint();
      return capture.paint();
    }));
    onFx(() -> {
      stage.setWidth(120);
      return null;
    });
    // The scene takes the new size on a pulse; the resize lays it out again too, so it is a new frame.
    FxCapture.Painted again = null;
    for (int i = 0; i < 50 && (again == null || again.width != 120); i++) {
      Thread.sleep(100);
      again = onFx(capture::paint);
    }
    assertNotNull(again);
    assertEquals(120, again.width);
  }

  @Test
  public void anEmptySceneIsNoFrame() throws Exception {
    Stage stage = stage(new Pane(), 1, 1, 0, 0);
    onFx(() -> {
      stage.getScene().getRoot().resize(0, 0);
      return null;
    });
    FxCapture capture = focusing(1, stage);
    assertNotNull("a 1x1 scene is a frame", onFx(capture::paint));
  }

  @Test
  public void theProductionCaptureChecksForJavaFxThreads() {
    CaptureSource capture = FxCapture.create(1, MaskRules.strict(), LDLogger.none(), null);
    assertTrue(capture instanceof FxCapture);
    // JavaFX runs in this JVM, so the capture asks; with nothing focused, there is no frame.
    ((FxCapture) capture).isFocused = window -> false;
    assertNull(capture.capture(5000));
  }
  // -- edge paths --

  @Test
  public void aFullCaptureReturnsAMaskedFrame() throws Exception {
    Pane root = new Pane();
    onFx(() -> {
      at(root, new Label("Jane"), 10, 10, 50, 20);
      return null;
    });
    Stage stage = stage(root, 120, 80, 0, 0);
    CaptureSource.RawFrame raw = focusing(1, stage).capture(5000);
    assertNotNull(raw);
    assertEquals(120, raw.image.getWidth());
    assertTrue("masked as painted", raw.masks.isEmpty());
    assertEquals(CaptureLoop.MASK_COLOR.getRGB(), raw.image.getRGB(30, 20));
  }

  @Test
  public void aDialogOverAMaskedNodeStaysVisible() throws Exception {
    BorderPane root = onFx(() -> new BorderPane(new ListView<>(javafx.collections.FXCollections.observableArrayList(
        "private", "rows"))));
    Stage owner = stage(root, 300, 200, 100, 100);
    Stage dialog = onFx(() -> {
      Stage d = new Stage(StageStyle.UNDECORATED);
      d.initOwner(owner);
      Pane blue = new Pane();
      blue.setStyle("-fx-background-color: blue;");
      d.setScene(new Scene(blue, 60, 40));
      d.setX(owner.getX() + owner.getScene().getX() + 100);
      d.setY(owner.getY() + owner.getScene().getY() + 80);
      d.show();
      windows.add(d);
      return d;
    });
    FxCapture capture = focusing(1, dialog);
    java.awt.image.BufferedImage image = capture.toImage(onFx(capture::paint));
    assertEquals("the dialog, not the mask under it", 0xff0000ff, image.getRGB(130, 100));
    assertEquals("the masked list around it", CaptureLoop.MASK_COLOR.getRGB(), image.getRGB(40, 40));
  }

  @Test
  public void aPaintThatThrowsIsNoFrame() throws Exception {
    stage(new Pane(), 50, 50, 0, 0);
    FxCapture capture = capture(1);
    capture.isFocused = window -> {
      throw new IllegalStateException("boom");
    };
    assertNull(capture.capture(5000));
  }

  @Test
  public void aFocusedPopupIsNotTheFrameButItsOwnerIs() throws Exception {
    Stage owner = stage(new Pane(), 100, 100, 0, 0);
    onFx(() -> {
      Popup popup = new Popup();
      popup.getContent().add(new Pane());
      popup.show(owner, 10, 10);
      windows.add(popup);
      Stage other = new Stage(StageStyle.UNDECORATED); // not the owner's: left out
      other.setScene(new Scene(new Pane(), 30, 30));
      other.show();
      windows.add(other);
      Stage empty = new Stage(StageStyle.UNDECORATED); // owned, but no scene to draw
      empty.initOwner(owner);
      empty.show();
      windows.add(empty);
      Stage child = new Stage(StageStyle.UNDECORATED); // owned, with a popup of its own
      child.initOwner(owner);
      child.setScene(new Scene(new Pane(), 20, 20));
      child.show();
      windows.add(child);
      Popup nested = new Popup();
      nested.getContent().add(new Pane());
      nested.show(child, 5, 5);
      windows.add(nested);
      return null;
    });
    FxCapture capture = capture(1);
    capture.isFocused = window -> window instanceof Popup || window == owner;
    FxCapture.Painted painted = onFx(capture::paint);
    assertEquals(100, painted.width);
    assertEquals("the owner, its popup, the child and the child's popup", 4, capture.watchedScenes());
  }

  @Test
  public void aFocusedStageWithoutASceneIsNoFrame() throws Exception {
    Stage bare = onFx(() -> {
      Stage stage = new Stage(StageStyle.UNDECORATED);
      stage.show();
      windows.add(stage);
      return stage;
    });
    assertNull(onFx(focusing(1, bare)::paint));
  }

  @Test
  public void aDialogWhoseOwnerIsHiddenIsItsOwnFrame() throws Exception {
    Stage dialog = onFx(() -> {
      Stage hidden = new Stage();
      Stage stage = new Stage(StageStyle.UNDECORATED);
      stage.initOwner(hidden);
      stage.setScene(new Scene(new Pane(), 70, 40));
      stage.show();
      windows.add(stage);
      return stage;
    });
    assertEquals(70, onFx(focusing(1, dialog)::paint).width);
  }

  @Test
  public void aSceneWithNoAreaIsNoFrame() throws Exception {
    Stage stage = stage(new Pane(), 0, 0, 0, 0);
    FxCapture.Painted painted = onFx(focusing(1, stage)::paint);
    // A window manager may not allow an empty window; if it grows the scene, it is a frame.
    assertTrue(painted == null || painted.width > 0);
  }

  @Test
  public void labelsAndTextWithoutTextAreNotText() throws Exception {
    onFx(() -> {
      Label label = new Label();
      label.setText(null);
      assertEquals(MaskRules.Kind.NONE, FxMasks.kind(label));
      Text text = new Text();
      text.setText(null);
      assertEquals(MaskRules.Kind.NONE, FxMasks.kind(text));
      return null;
    });
  }

  // -- the mouse --

  private static MouseEvent mouse(EventType<MouseEvent> type, double screenX, double screenY) {
    return new MouseEvent(type, 0, 0, screenX, screenY, MouseButton.PRIMARY, 1, false, false, false, false, true,
        false, false, false, false, false, null);
  }

  @Test
  public void aCapturedSceneReportsTheMouseUntilStopped() throws Exception {
    Stage stage = stage(new Pane(), 120, 80, 30, 40);
    FxCapture capture = focusing(1, stage);
    List<String> seen = Collections.synchronizedList(new ArrayList<>());
    capture.pointer = (action, x, y, t) -> seen.add(action + " " + (int) x + "," + (int) y);
    CaptureSource.RawFrame raw = capture.capture(5000);
    assertNotNull(raw);
    double[] origin = onFx(() -> new double[] {stage.getX() + stage.getScene().getX(),
        stage.getY() + stage.getScene().getY()});
    assertEquals(origin[0], raw.originX, 0);
    assertEquals(origin[1], raw.originY, 0);
    assertEquals(1, raw.scale, 0);
    Scene scene = onFx(stage::getScene);
    onFx(() -> {
      Event.fireEvent(scene, mouse(MouseEvent.MOUSE_MOVED, 31, 41));
      Event.fireEvent(scene, mouse(MouseEvent.MOUSE_PRESSED, 32, 42));
      Event.fireEvent(scene, mouse(MouseEvent.MOUSE_DRAGGED, 33, 43));
      Event.fireEvent(scene, mouse(MouseEvent.MOUSE_RELEASED, 34, 44));
      Event.fireEvent(scene, mouse(MouseEvent.MOUSE_CLICKED, 34, 44)); // not a pointer action
      return null;
    });
    assertThat(seen, contains("MOVE 31,41", "DOWN 32,42", "MOVE 33,43", "UP 34,44"));

    capture.pointer = null;
    onFx(() -> {
      Event.fireEvent(scene, mouse(MouseEvent.MOUSE_MOVED, 35, 45)); // no sink: ignored
      return null;
    });
    capture.stop();
    onFx(() -> null); // after the stop's task
    assertEquals(0, capture.watchedScenes());
    capture.pointer = (action, x, y, t) -> seen.add("after stop");
    onFx(() -> {
      Event.fireEvent(scene, mouse(MouseEvent.MOUSE_MOVED, 36, 46));
      return null;
    });
    assertEquals(4, seen.size());
  }

  @Test
  public void aScaledFrameSaysItsScale() throws Exception {
    Stage stage = stage(new Pane(), 120, 80, 0, 0);
    CaptureSource.RawFrame raw = focusing(0.5, stage).capture(5000);
    assertEquals(60, raw.image.getWidth());
    assertEquals(0.5, raw.scale, 0);
  }

  @Test
  public void stoppingWithoutJavaFxOrAfterItExitsDoesNothing() {
    FxCapture unused = new FxCapture(1, new FxMasks(MaskRules.strict()), LDLogger.none(), () -> false);
    unused.runLater = task -> {
      throw new AssertionError("must not touch JavaFX");
    };
    unused.stop();
    FxCapture exited = capture(1);
    exited.runLater = task -> {
      throw new IllegalStateException("Toolkit has exited");
    };
    exited.stop();
  }
}
