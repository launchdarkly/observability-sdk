package com.launchdarkly.observability.internal.replay;

import org.junit.Test;

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Component;
import java.awt.Label;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.JTree;
import javax.swing.table.JTableHeader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** What the Swing masks cover. The component trees need no display: they are never shown. */
public class MaskTest {
  /** A panel with no layout, so children sit where a test puts them. */
  static JPanel panel(int width, int height) {
    JPanel panel = new JPanel(null);
    panel.setBounds(0, 0, width, height);
    return panel;
  }

  static <T extends Component> T at(JComponent parent, T child, int x, int y, int w, int h) {
    child.setBounds(x, y, w, h);
    parent.add(child);
    return child;
  }

  static List<Rectangle> masks(MaskRules rules, Component content) {
    return masks(rules, content, 0, 0, 1);
  }

  static List<Rectangle> masks(MaskRules rules, Component content, int dx, int dy, double scale) {
    List<Rectangle> out = new ArrayList<>();
    new SwingMasks(rules).collect(content, dx, dy, scale, out);
    return out;
  }

  /** The rectangle a component at this place is masked with: one pixel bigger on each side. */
  static Rectangle covered(int x, int y, int w, int h) {
    return new Rectangle(x - 1, y - 1, w + 2, h + 2);
  }

  static MaskRules rules(boolean inputs, boolean text, boolean images) {
    return new MaskRules(inputs, text, images, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
        Collections.emptySet());
  }

  @Test
  public void strictMasksInputsAndTextButNotImagesOrPanels() {
    JPanel root = panel(400, 300);
    at(root, new JTextField("jane@example.com"), 10, 10, 100, 20);
    at(root, new JLabel("Balance: $1,204"), 10, 40, 100, 20);
    at(root, new JButton("Pay Jane"), 10, 70, 100, 20);
    at(root, new JLabel(new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB))), 10, 100, 20, 20);
    at(root, new JButton(), 10, 130, 20, 20); // an icon button with no text
    at(root, new JLabel(" "), 10, 160, 20, 20);
    at(root, panel(50, 50), 200, 10, 50, 50);
    assertThat(masks(MaskRules.strict(), root), contains(covered(10, 10, 100, 20), covered(10, 40, 100, 20),
        covered(10, 70, 100, 20)));
  }

  @Test
  public void theDefaultMasksOnlyInputs() {
    JPanel root = panel(400, 300);
    at(root, new JTextField("jane@example.com"), 10, 10, 100, 20);
    at(root, new JLabel("Balance: $1,204"), 10, 40, 100, 20);
    at(root, new JButton("Pay Jane"), 10, 70, 100, 20);
    at(root, new JList<>(new String[] {"flag-1"}), 10, 100, 100, 20);
    at(root, new JLabel(new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB))), 10, 130, 20, 20);
    at(root, new JPasswordField("hunter2"), 200, 10, 100, 20);
    assertThat(masks(MaskRules.defaults(), root), contains(covered(10, 10, 100, 20), covered(200, 10, 100, 20)));
  }

  @Test
  public void eachKindHasItsOwnSwitch() {
    JPanel root = panel(400, 300);
    at(root, new JTextArea("notes"), 0, 0, 10, 10);
    at(root, new JLabel("text"), 20, 0, 10, 10);
    at(root, new JLabel(new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB))), 40, 0, 10, 10);
    assertThat(masks(rules(true, false, false), root), contains(covered(0, 0, 10, 10)));
    assertThat(masks(rules(false, true, false), root), contains(covered(20, 0, 10, 10)));
    assertThat(masks(rules(false, false, true), root), contains(covered(40, 0, 10, 10)));
    assertThat(masks(rules(false, false, false), root), empty());
  }

  @Test
  public void aPasswordIsAlwaysMasked() {
    JPanel root = panel(100, 100);
    JPasswordField password = at(root, new JPasswordField("hunter2"), 0, 0, 50, 20);
    MaskRules none = rules(false, false, false);
    MaskRules.mark(password, false);
    MaskRules.mark(root, false);
    assertThat(masks(none, root), contains(covered(0, 0, 50, 20)));
  }

  @Test
  public void everyKindOfTextComponentIsText() {
    Component[] text = {new JTable(), new JTableHeader(), new JList<String>(), new JTree(), new JComboBox<String>(),
        new JSpinner(), new JToolTip(), new Label("a"), new Button("b"), new Checkbox("c"), new Choice(),
        new java.awt.List()};
    for (Component c : text) {
      assertEquals(c.getClass().getName(), MaskRules.Kind.TEXT, SwingMasks.kind(c));
    }
    assertEquals(MaskRules.Kind.INPUT, SwingMasks.kind(new TextField("awt")));
    assertEquals(MaskRules.Kind.OPAQUE, SwingMasks.kind(new Canvas()));
    assertEquals(MaskRules.Kind.NONE, SwingMasks.kind(new JLabel()));
    assertEquals(MaskRules.Kind.NONE, SwingMasks.kind(new JPanel()));
  }

  /** Stands in for JavaFX's JFXPanel, which needs JavaFX: a class by that name, or one extending it. */
  @Test
  public void anEmbeddedJavaFxPanelIsOpaqueByName() {
    assertTrue(SwingMasks.isA(JPanel.class, "javax.swing.JComponent"));
    assertFalse(SwingMasks.isA(JPanel.class, "javafx.embed.swing.JFXPanel"));
  }

  @Test
  public void anApachePivotHostIsOpaqueWhenItsComponentsCannotBeWalked() throws Exception {
    // Pivot builds its components on the event dispatch thread only.
    java.util.concurrent.atomic.AtomicReference<Component> made = new java.util.concurrent.atomic.AtomicReference<>();
    java.awt.EventQueue.invokeAndWait(() -> made.set(new org.apache.pivot.wtk.ApplicationContext.DisplayHost()));
    // Covered whole then, even by the default; PivotMaskTest covers the walk and its failure.
    assertEquals(MaskRules.Kind.OPAQUE, SwingMasks.kind(made.get()));
  }

  @Test
  public void nativeContentIsMaskedUnlessUnmarked() {
    JPanel root = panel(100, 100);
    Canvas canvas = at(root, new Canvas(), 0, 0, 40, 40);
    MaskRules rules = rules(false, false, false);
    assertThat(masks(rules, root), contains(covered(0, 0, 40, 40)));
    MaskRules.mark(canvas, false);
    assertThat(masks(rules, root), empty());
  }

  // -- marks --

  @Test
  public void aMaskOnAnAncestorWinsOverAnUnmaskBelowIt() {
    JPanel root = panel(200, 200);
    JPanel card = at(root, panel(100, 100), 50, 50, 100, 100);
    JLabel label = at(card, new JLabel("name"), 10, 10, 20, 20);
    MaskRules rules = rules(false, false, false);
    MaskRules.mark(card, true);
    MaskRules.mark(label, false);
    assertThat("the whole card", masks(rules, root), contains(covered(50, 50, 100, 100)));
    MaskRules.mark(card, null);
    assertThat(masks(rules, root), empty());
  }

  @Test
  public void anUnmaskOnAnAncestorLetsItsChildrenShow() {
    JPanel root = panel(200, 200);
    JPanel form = at(root, panel(100, 100), 0, 0, 100, 100);
    at(form, new JLabel("Total"), 0, 0, 20, 20);
    JTextField field = at(form, new JTextField("public"), 0, 30, 20, 20);
    at(root, new JLabel("Elsewhere"), 150, 0, 20, 20);
    MaskRules rules = MaskRules.strict();
    form.putClientProperty(MaskRules.PROPERTY, Boolean.FALSE);
    assertThat("only what is outside the form", masks(rules, root), contains(covered(150, 0, 20, 20)));
    field.putClientProperty(MaskRules.PROPERTY, Boolean.TRUE);
    assertThat("a mask below an unmask still masks", masks(rules, root), contains(covered(0, 30, 20, 20),
        covered(150, 0, 20, 20)));
    form.putClientProperty(MaskRules.PROPERTY, "not a boolean");
    assertEquals("not a mark: the label, the marked field and the label elsewhere", 3, masks(rules, root).size());
  }

  @Test
  public void namesAndClassesMarkToo() {
    JPanel root = panel(200, 200);
    JPanel secret = at(root, panel(50, 50), 0, 0, 50, 50);
    secret.setName("account");
    JLabel safe = at(root, new JLabel("Help"), 100, 0, 20, 20);
    safe.setName("help");
    at(root, new JTextField("x"), 100, 100, 20, 20);
    HashSet<String> maskNames = new HashSet<>(Collections.singletonList("account"));
    HashSet<String> unmaskNames = new HashSet<>(Collections.singletonList("help"));
    HashSet<String> unmaskClasses = new HashSet<>(Collections.singletonList(JTextField.class.getName()));
    MaskRules rules = new MaskRules(true, true, false, maskNames, unmaskNames, Collections.emptySet(), unmaskClasses);
    assertThat(masks(rules, root), contains(covered(0, 0, 50, 50)));
    HashSet<String> maskClasses = new HashSet<>(Collections.singletonList(JPanel.class.getName()));
    MaskRules byClass = new MaskRules(false, false, false, Collections.emptySet(), Collections.emptySet(), maskClasses,
        Collections.emptySet());
    assertThat("the root is a JPanel", masks(byClass, root), contains(covered(0, 0, 200, 200)));
  }

  @Test
  public void markOfReadsEverySource() {
    MaskRules rules = new MaskRules(true, true, false, Collections.singleton("m"), Collections.singleton("u"),
        Collections.emptySet(), Collections.emptySet());
    Object thing = new Object();
    assertNull(rules.markOf(thing, null, null));
    assertTrue(rules.markOf(thing, null, "m"));
    assertFalse(rules.markOf(thing, null, "u"));
    assertNull(rules.markOf(thing, null, "other"));
    assertFalse(rules.markOf(thing, Boolean.FALSE, "m"));
    MaskRules.mark(thing, true);
    assertTrue(rules.markOf(thing, Boolean.FALSE, "u"));
  }

  @Test
  public void theKindsMaskAsTheRulesSay() {
    MaskRules strict = MaskRules.strict();
    assertFalse(strict.masks(MaskRules.Kind.NONE));
    assertFalse(strict.masks(MaskRules.Kind.SECRET)); // masked by the walk, not the switch
    assertTrue(strict.masks(MaskRules.Kind.INPUT));
    assertTrue(strict.masks(MaskRules.Kind.TEXT));
    assertFalse(strict.masks(MaskRules.Kind.IMAGE));
    assertTrue(strict.masks(MaskRules.Kind.OPAQUE));
  }

  // -- where the masks go --

  @Test
  public void whatIsScrolledOutOfSightIsCutAway() {
    JPanel tall = panel(100, 1000);
    at(tall, new JLabel("row 1"), 0, 0, 100, 20);
    at(tall, new JLabel("row 40"), 0, 800, 100, 20);
    at(tall, new JTextArea("notes"), 0, 90, 100, 40);
    tall.setPreferredSize(new java.awt.Dimension(100, 1000));
    JScrollPane scroll = new JScrollPane(tall);
    scroll.setBorder(null);
    scroll.setBounds(0, 0, 120, 100);
    scroll.doLayout();
    JPanel root = panel(200, 200);
    root.add(scroll);
    assertThat("row 40 is out of sight, and only the top of the notes shows", masks(MaskRules.strict(), root),
        contains(covered(0, 0, 100, 20), covered(0, 90, 100, 10)));
  }

  @Test
  public void hiddenAndEmptyComponentsAreSkipped() {
    JPanel root = panel(100, 100);
    at(root, new JLabel("hidden"), 0, 0, 20, 20).setVisible(false);
    at(root, new JLabel("empty"), 0, 30, 0, 20);
    assertThat(masks(MaskRules.strict(), root), empty());
  }

  @Test
  public void masksAreInFramePixels() {
    JPanel root = panel(200, 200);
    at(root, new JLabel("x"), 10, 20, 30, 40);
    assertThat("offset, then scaled, then grown", masks(MaskRules.strict(), root, 5, 0, 0.5),
        contains(new Rectangle(6, 9, 18, 22)));
  }

  @Test
  public void tabTitlesAreMaskedButNotTheTabsContent() {
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Jane's account", panel(10, 10));
    tabs.addTab("Settings", panel(10, 10));
    tabs.setBounds(0, 0, 300, 200);
    tabs.doLayout();
    // Without a UI laid out, the tab bounds come from the look and feel.
    tabs.getUI().getTabBounds(tabs, 0);
    List<Rectangle> masks = masks(MaskRules.strict(), tabs);
    assertEquals(2, masks.size());
    assertTrue("a tab, not the pane", masks.get(0).height < 100);
    assertThat(masks(rules(true, false, false), tabs), empty());
    tabs.putClientProperty(MaskRules.PROPERTY, Boolean.FALSE);
    assertThat(masks(MaskRules.strict(), tabs), empty());
  }

  @Test
  public void ldReplayMarksForEveryRule() {
    Object panel = new Object();
    com.launchdarkly.observability.LDReplay.mask(panel);
    assertEquals(Boolean.TRUE, MaskRules.defaults().markOf(panel, null, null));
    com.launchdarkly.observability.LDReplay.unmask(panel);
    assertEquals(Boolean.FALSE, MaskRules.strict().markOf(panel, null, null));
    com.launchdarkly.observability.LDReplay.clearMask(panel);
    assertNull(MaskRules.defaults().markOf(panel, null, null));
    assertEquals(FxMasks.MASK_STYLE, com.launchdarkly.observability.LDReplay.MASK_STYLE_CLASS);
    assertEquals(FxMasks.UNMASK_STYLE, com.launchdarkly.observability.LDReplay.UNMASK_STYLE_CLASS);
  }

  // -- Android's other switches --

  static MaskRules sensitiveRules() {
    return new MaskRules(false, false, false, false, true, MaskRules.DEFAULT_MINIMUM_ALPHA, Collections.emptySet(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
  }

  @Test
  public void keywordsMaskWhatNamesSomethingSensitive() {
    JPanel content = panel(400, 400);
    at(content, new JLabel("Your EMAIL address"), 0, 0, 100, 20);
    at(content, new JLabel("Welcome"), 0, 30, 100, 20);
    at(content, new JButton("Add card"), 0, 60, 100, 20);
    at(content, new JTextField("hello"), 0, 90, 100, 20).setToolTipText("Phone number");
    at(content, new JTextField(), 0, 120, 100, 20).putClientProperty(SwingMasks.PLACEHOLDER, "Street");
    at(content, new JTextField(), 0, 150, 100, 20).getAccessibleContext().setAccessibleName("Account id");
    at(content, new JTextField(), 0, 180, 100, 20).getAccessibleContext().setAccessibleDescription("your pin");
    at(content, new JTextArea("my ssn is"), 0, 210, 100, 20);
    at(content, new Label("Date of birth"), 0, 240, 100, 20);
    at(content, new Button("Credit"), 0, 270, 100, 20);
    at(content, new TextField("cvv"), 0, 300, 100, 20);
    at(content, new JTextField(), 0, 330, 100, 20).putClientProperty(SwingMasks.PLACEHOLDER, 42);
    assertThat(masks(sensitiveRules(), content), contains(covered(0, 0, 100, 20), covered(0, 60, 100, 20),
        covered(0, 90, 100, 20), covered(0, 120, 100, 20), covered(0, 150, 100, 20), covered(0, 180, 100, 20),
        covered(0, 210, 100, 20), covered(0, 240, 100, 20), covered(0, 270, 100, 20), covered(0, 300, 100, 20)));
    assertThat("off by default", masks(rules(false, false, false), content), empty());
  }

  @Test
  public void anUnmaskWinsOverAKeyword() {
    JPanel content = panel(200, 100);
    JLabel email = at(content, new JLabel("Email"), 0, 0, 100, 20);
    MaskRules.mark(email, Boolean.FALSE);
    try {
      assertThat(masks(sensitiveRules(), content), empty());
    } finally {
      MaskRules.mark(email, null);
    }
  }

  @Test
  public void keywordsAreFoundAnywhereInTheStartOfAText() {
    assertTrue(MaskRules.sensitive(null, "Mobile"));
    assertTrue(MaskRules.sensitive("expires MM/YY"));
    assertFalse(MaskRules.sensitive(null, "", "hello", "pi"));
    StringBuilder long_ = new StringBuilder();
    for (int i = 0; i < MaskRules.SENSITIVE_SEARCH_LIMIT; i++) {
      long_.append('x');
    }
    assertFalse("past the limit, not searched", MaskRules.sensitive(long_ + "password"));
    assertTrue(MaskRules.sensitive("password" + long_));
  }

  @Test
  public void webViewsAreMaskedOnlyWhenAsked() {
    MaskRules shown = rules(true, true, true);
    MaskRules masked = new MaskRules(false, false, false, true, false, MaskRules.DEFAULT_MINIMUM_ALPHA,
        Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    assertFalse(shown.masks(MaskRules.Kind.WEB));
    assertTrue(masked.masks(MaskRules.Kind.WEB));
    assertTrue("a heavyweight browser stays opaque", shown.masks(SwingMasks.kind(new Canvas())));
  }

  @Test
  public void anUnmaskedClassShowsItsSubtree() {
    JPanel content = panel(200, 100);
    JPanel help = at(content, new JPanel(null), 0, 0, 200, 50);
    at(help, new JTextField("shown"), 0, 0, 100, 20);
    MaskRules rules = new MaskRules(true, false, false, Collections.emptySet(), Collections.emptySet(),
        Collections.emptySet(), Collections.singleton(JPanel.class.getName()));
    assertThat(masks(rules, help), empty());
  }
}
