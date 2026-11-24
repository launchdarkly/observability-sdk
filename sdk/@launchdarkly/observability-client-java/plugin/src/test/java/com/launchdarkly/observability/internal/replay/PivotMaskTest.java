package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.observability.internal.PivotFixture;

import org.apache.pivot.wtk.ApplicationContext;
import org.apache.pivot.wtk.BoxPane;
import org.apache.pivot.wtk.Display;
import org.apache.pivot.wtk.ImageView;
import org.apache.pivot.wtk.Label;
import org.apache.pivot.wtk.ListButton;
import org.apache.pivot.wtk.ListView;
import org.apache.pivot.wtk.Panel;
import org.apache.pivot.wtk.PushButton;
import org.apache.pivot.wtk.Spinner;
import org.apache.pivot.wtk.TableView;
import org.apache.pivot.wtk.TableViewHeader;
import org.apache.pivot.wtk.TextArea;
import org.apache.pivot.wtk.TextInput;
import org.apache.pivot.wtk.TextPane;
import org.apache.pivot.wtk.TreeView;
import org.apache.pivot.wtk.content.ButtonData;
import org.apache.pivot.wtk.media.Image;
import org.junit.Before;
import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;

import static com.launchdarkly.observability.internal.PivotFixture.at;
import static com.launchdarkly.observability.internal.PivotFixture.onEdt;
import static com.launchdarkly.observability.internal.replay.MaskTest.covered;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** What replay masks inside an Apache Pivot display. */
public class PivotMaskTest {
  private ApplicationContext.DisplayHost host;
  private Panel content;

  @Before
  public void setUp() throws Exception {
    host = onEdt(ApplicationContext.DisplayHost::new);
    content = onEdt(() -> PivotFixture.content(host, 400, 300));
  }

  /** The masks of the host, placed at this point of a Swing panel. */
  private List<Rectangle> masks(MaskRules rules, int hostX, int hostY) throws Exception {
    return onEdt(() -> {
      JPanel swing = MaskTest.panel(600, 600);
      MaskTest.at(swing, host, hostX, hostY, 400, 300);
      return MaskTest.masks(rules, swing);
    });
  }

  private List<Rectangle> masks(MaskRules rules) throws Exception {
    return masks(rules, 0, 0);
  }

  @Test
  public void theDefaultMasksInputsAndPasswordsWhereTheyAre() throws Exception {
    onEdt(() -> {
      at(content, new Label("Name"), 0, 0, 80, 20);
      at(content, new TextInput(), 100, 0, 80, 20);
      TextInput password = at(content, new TextInput(), 100, 30, 80, 20);
      password.setPassword(true);
      return null;
    });
    assertThat(masks(MaskRules.defaults(), 10, 20), contains(covered(110, 20, 80, 20), covered(110, 50, 80, 20)));
    assertThat("strict masks the label too", masks(MaskRules.strict()), contains(covered(0, 0, 80, 20),
        covered(100, 0, 80, 20), covered(100, 30, 80, 20)));
    MaskRules none = MaskTest.rules(false, false, false);
    assertThat("a password whatever the rules", masks(none), contains(covered(100, 30, 80, 20)));
  }

  @Test
  public void aScaledDisplayIsMaskedAtItsScale() throws Exception {
    onEdt(() -> {
      at(content, new TextInput(), 10, 10, 20, 10);
      host.setScale(2);
      return null;
    });
    assertThat(masks(MaskRules.defaults()), contains(covered(20, 20, 40, 20)));
  }

  @Test
  public void marksComeFromCallsNamesClassesAndUserData() throws Exception {
    BoxPane account = onEdt(() -> {
      BoxPane box = at(content, new BoxPane(), 0, 0, 100, 100);
      at(box, new Label("plain"), 0, 0, 50, 20);
      Label byName = at(content, new Label("named"), 0, 120, 50, 20);
      byName.setName("secret");
      Label byData = at(content, new Label("data"), 0, 150, 50, 20);
      byData.getUserData().put(MaskRules.PROPERTY, true);
      at(content, new ImageView(), 0, 180, 50, 20);
      return box;
    });
    MaskRules.mark(account, Boolean.TRUE);
    try {
      MaskRules rules = new MaskRules(false, false, false, Collections.singleton("secret"), Collections.emptySet(),
          Collections.singleton(ImageView.class.getName()), Collections.emptySet());
      assertThat(masks(rules), contains(covered(0, 0, 100, 100), covered(0, 120, 50, 20), covered(0, 150, 50, 20),
          covered(0, 180, 50, 20)));
    } finally {
      MaskRules.mark(account, null);
    }
  }

  @Test
  public void anUnmaskShowsWhatIsInsideButNotAPassword() throws Exception {
    BoxPane help = onEdt(() -> {
      BoxPane box = at(content, new BoxPane(), 0, 0, 200, 100);
      at(box, new TextInput(), 0, 0, 80, 20);
      at(box, new TextInput(), 0, 30, 80, 20).setPassword(true);
      at(box, new TextInput(), 0, 200, 80, 20); // outside the box: cut away
      return box;
    });
    MaskRules.mark(help, Boolean.FALSE);
    try {
      assertThat(masks(MaskRules.defaults()), contains(covered(0, 30, 80, 20)));
    } finally {
      MaskRules.mark(help, null);
    }
  }

  @Test
  public void hiddenComponentsAreSkipped() throws Exception {
    onEdt(() -> {
      at(content, new TextInput(), 0, 0, 80, 20).setVisible(false);
      return null;
    });
    assertThat(masks(MaskRules.defaults()), empty());
  }

  @Test
  public void eachPivotComponentHasItsKind() throws Exception {
    onEdt(() -> {
      assertEquals(MaskRules.Kind.INPUT, PivotMasks.kind(new TextInput()));
      assertEquals(MaskRules.Kind.INPUT, PivotMasks.kind(new TextArea()));
      assertEquals(MaskRules.Kind.INPUT, PivotMasks.kind(new TextPane()));
      assertEquals(MaskRules.Kind.INPUT, PivotMasks.kind(new Spinner()));
      assertEquals(MaskRules.Kind.TEXT, PivotMasks.kind(new ListButton()));
      assertEquals(MaskRules.Kind.TEXT, PivotMasks.kind(new ListView()));
      assertEquals(MaskRules.Kind.TEXT, PivotMasks.kind(new TableView()));
      assertEquals(MaskRules.Kind.TEXT, PivotMasks.kind(new TableViewHeader()));
      assertEquals(MaskRules.Kind.TEXT, PivotMasks.kind(new TreeView()));
      assertEquals(MaskRules.Kind.TEXT, PivotMasks.kind(new PushButton("Go")));
      assertEquals(MaskRules.Kind.TEXT, PivotMasks.kind(new PushButton(new ButtonData("Go"))));
      assertEquals("other data is drawn as text", MaskRules.Kind.TEXT, PivotMasks.kind(new PushButton(42)));
      assertEquals(MaskRules.Kind.IMAGE, PivotMasks.kind(new PushButton(new ButtonData(icon()))));
      assertEquals(MaskRules.Kind.NONE, PivotMasks.kind(new PushButton(new ButtonData())));
      assertEquals(MaskRules.Kind.NONE, PivotMasks.kind(new PushButton()));
      assertEquals(MaskRules.Kind.NONE, PivotMasks.kind(new PushButton(" ")));
      assertEquals(MaskRules.Kind.TEXT, PivotMasks.kind(new Label("Name")));
      assertEquals(MaskRules.Kind.NONE, PivotMasks.kind(new Label(" ")));
      assertEquals(MaskRules.Kind.NONE, PivotMasks.kind(new Label()));
      assertEquals(MaskRules.Kind.IMAGE, PivotMasks.kind(new ImageView()));
      assertEquals(MaskRules.Kind.NONE, PivotMasks.kind(new BoxPane()));
      return null;
    });
  }

  private static Image icon() {
    return new Image() {
      @Override
      public int getWidth() {
        return 8;
      }

      @Override
      public int getHeight() {
        return 8;
      }

      @Override
      public void paint(Graphics2D graphics) {}
    };
  }

  @Test
  public void keywordsMaskPivotComponentsToo() throws Exception {
    onEdt(() -> {
      at(content, new Label("Email"), 0, 0, 50, 20);
      at(content, new Label("Welcome"), 0, 30, 50, 20);
      at(content, new TextInput(), 0, 60, 50, 20).setPrompt("Phone");
      TextInput typed = at(content, new TextInput(), 0, 90, 50, 20);
      typed.setText("my address");
      TextArea area = at(content, new TextArea(), 0, 120, 50, 20);
      area.setText("card");
      PushButton tip = at(content, new PushButton("Go"), 0, 150, 50, 20);
      tip.setTooltipText("Account");
      return null;
    });
    MaskRules rules = new MaskRules(false, false, false, false, true, MaskRules.DEFAULT_MINIMUM_ALPHA,
        Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    assertThat(masks(rules), contains(covered(0, 0, 50, 20), covered(0, 60, 50, 20), covered(0, 90, 50, 20),
        covered(0, 120, 50, 20), covered(0, 150, 50, 20)));
    assertFalse(onEdt(() -> PivotMasks.sensitive(new BoxPane())));
    assertTrue(onEdt(() -> PivotMasks.sensitive(new PushButton(new ButtonData("Credit")))));
    assertFalse("other data is not read", onEdt(() -> PivotMasks.sensitive(new PushButton("email".length()))));
    assertTrue(onEdt(() -> PivotMasks.sensitive(new Label("PIN"))));
  }

  @Test
  public void aPivotTheMasksCannotReadIsCoveredWhole() throws Exception {
    ApplicationContext.DisplayHost broken = onEdt(() -> new ApplicationContext.DisplayHost() {
      private static final long serialVersionUID = 1L;

      @Override
      public Display getDisplay() {
        throw new IllegalStateException("not this Pivot");
      }
    });
    List<Rectangle> masked = onEdt(() -> {
      JPanel swing = MaskTest.panel(600, 600);
      MaskTest.at(swing, broken, 5, 5, 100, 50);
      return MaskTest.masks(MaskRules.defaults(), swing);
    });
    assertThat(masked, contains(covered(5, 5, 100, 50)));
  }

  @Test
  public void theSameMasksWalkPivotAgain() throws Exception {
    onEdt(() -> {
      at(content, new TextInput(), 0, 0, 80, 20);
      return null;
    });
    List<List<Rectangle>> twice = onEdt(() -> {
      JPanel swing = MaskTest.panel(600, 600);
      MaskTest.at(swing, host, 0, 0, 400, 300);
      SwingMasks masks = new SwingMasks(MaskRules.defaults());
      List<Rectangle> first = new java.util.ArrayList<>();
      masks.collect(swing, 0, 0, 1, first);
      List<Rectangle> second = new java.util.ArrayList<>();
      masks.collect(swing, 0, 0, 1, second);
      return java.util.Arrays.asList(first, second);
    });
    assertEquals(twice.get(0), twice.get(1));
    assertThat(twice.get(0), contains(covered(0, 0, 80, 20)));
  }

  @Test
  public void aHostMarkedToMaskIsCoveredWhole() throws Exception {
    MaskRules.mark(host, Boolean.TRUE);
    try {
      assertThat(masks(MaskRules.defaults()), contains(covered(0, 0, 400, 300)));
    } finally {
      MaskRules.mark(host, null);
    }
  }
}
