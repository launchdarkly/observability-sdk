package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.observability.internal.SwtFixture;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.launchdarkly.observability.internal.SwtFixture.at;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;

/** What replay masks in an SWT shell. */
public class SwtMaskTest {
  private Shell shell;
  private Image image;

  @Before
  public void setUp() {
    shell = SwtFixture.shell(400, 300);
    image = new Image(SwtFixture.display(), 8, 8);
  }

  @After
  public void tearDown() {
    shell.dispose();
    image.dispose();
  }

  static Rectangle covered(int x, int y, int w, int h) {
    return new Rectangle(x - 1, y - 1, w + 2, h + 2);
  }

  static MaskRules rules(boolean inputs, boolean text, boolean images) {
    return new MaskRules(inputs, text, images, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
        Collections.emptySet());
  }

  private List<Rectangle> masks(MaskRules rules, int x, int y) {
    List<Rectangle> out = new ArrayList<>();
    new SwtMasks(rules).collect(shell, x, y, out);
    return out;
  }

  private List<Rectangle> masks(MaskRules rules) {
    return masks(rules, 0, 0);
  }

  private void form() {
    Label name = at(new Label(shell, SWT.NONE), 0, 0, 80, 20);
    name.setText("Name");
    at(new Text(shell, SWT.NONE), 100, 0, 100, 25).setText("jane@example.com");
    at(new Text(shell, SWT.PASSWORD), 100, 30, 100, 25).setText("hunter2");
    at(new Label(shell, SWT.NONE), 0, 40, 30, 30).setImage(image);
  }

  @Test
  public void theDefaultMasksInputsAndPasswordsWhereTheyAre() {
    form();
    assertThat(masks(MaskRules.defaults(), 10, 20), contains(covered(110, 20, 100, 25), covered(110, 50, 100, 25)));
    assertThat("strict masks the label too", masks(MaskRules.strict()), contains(covered(0, 0, 80, 20),
        covered(100, 0, 100, 25), covered(100, 30, 100, 25)));
    assertThat("images when asked", masks(rules(false, false, true)), contains(covered(100, 30, 100, 25),
        covered(0, 40, 30, 30)));
    assertThat("a password whatever the rules", masks(rules(false, false, false)), contains(covered(100, 30, 100, 25)));
  }

  @Test
  public void eachKindOfControl() {
    assertEquals(MaskRules.Kind.INPUT, SwtMasks.kind(new StyledText(shell, SWT.NONE)));
    StyledText readOnly = new StyledText(shell, SWT.READ_ONLY);
    assertEquals(MaskRules.Kind.TEXT, SwtMasks.kind(readOnly));
    assertEquals(MaskRules.Kind.INPUT, SwtMasks.kind(new Combo(shell, SWT.DROP_DOWN)));
    assertEquals(MaskRules.Kind.TEXT, SwtMasks.kind(new Combo(shell, SWT.READ_ONLY)));
    assertEquals(MaskRules.Kind.INPUT, SwtMasks.kind(new CCombo(shell, SWT.NONE)));
    assertEquals(MaskRules.Kind.INPUT, SwtMasks.kind(new Spinner(shell, SWT.NONE)));
    assertEquals(MaskRules.Kind.INPUT, SwtMasks.kind(new DateTime(shell, SWT.DATE)));
    assertEquals(MaskRules.Kind.TEXT, SwtMasks.kind(new org.eclipse.swt.widgets.List(shell, SWT.NONE)));
    assertEquals(MaskRules.Kind.TEXT, SwtMasks.kind(new Table(shell, SWT.NONE)));
    assertEquals(MaskRules.Kind.TEXT, SwtMasks.kind(new Tree(shell, SWT.NONE)));
    assertEquals(MaskRules.Kind.TEXT, SwtMasks.kind(new Link(shell, SWT.NONE)));
    assertEquals("a separator", MaskRules.Kind.NONE, SwtMasks.kind(new Label(shell, SWT.SEPARATOR)));
    CLabel cLabel = new CLabel(shell, SWT.NONE);
    assertEquals(MaskRules.Kind.NONE, SwtMasks.kind(cLabel));
    cLabel.setImage(image);
    assertEquals(MaskRules.Kind.IMAGE, SwtMasks.kind(cLabel));
    cLabel.setText("Account");
    assertEquals(MaskRules.Kind.TEXT, SwtMasks.kind(cLabel));
    Button button = new Button(shell, SWT.PUSH);
    assertEquals(MaskRules.Kind.NONE, SwtMasks.kind(button));
    button.setImage(image);
    assertEquals(MaskRules.Kind.IMAGE, SwtMasks.kind(button));
    button.setText("Save");
    assertEquals(MaskRules.Kind.TEXT, SwtMasks.kind(button));
    assertEquals(MaskRules.Kind.NONE, SwtMasks.kind(new Composite(shell, SWT.NONE)));
  }

  @Test
  public void aBrowserIsAWebView() {
    Control browser;
    try {
      browser = new org.eclipse.swt.browser.Browser(shell, SWT.NONE);
    } catch (org.eclipse.swt.SWTError e) {
      org.junit.Assume.assumeNoException("no browser here", e);
      return;
    }
    assertEquals(MaskRules.Kind.WEB, SwtMasks.kind(browser));
  }

  @Test
  public void anOpenGlCanvasIsCoveredWhole() {
    Control canvas;
    try {
      org.eclipse.swt.opengl.GLData data = new org.eclipse.swt.opengl.GLData();
      data.doubleBuffer = true;
      canvas = at(new org.eclipse.swt.opengl.GLCanvas(shell, SWT.NONE, data), 10, 10, 50, 50);
    } catch (org.eclipse.swt.SWTException | org.eclipse.swt.SWTError | IllegalArgumentException
        | UnsatisfiedLinkError e) {
      org.junit.Assume.assumeNoException("no OpenGL here", e);
      return;
    }
    assertEquals(MaskRules.Kind.OPAQUE, SwtMasks.kind(canvas));
    assertThat(masks(rules(false, false, false)), contains(covered(10, 10, 50, 50)));
  }

  @Test
  public void marksComeFirst() {
    Composite account = at(new Composite(shell, SWT.NONE), 0, 100, 300, 100);
    Label balance = at(new Label(account, SWT.NONE), 10, 10, 100, 20);
    balance.setText("Balance");
    account.setData(MaskRules.PROPERTY, true);
    assertThat("masked, all of it", masks(MaskRules.defaults()), contains(covered(0, 100, 300, 100)));

    account.setData(MaskRules.PROPERTY, false);
    Text password = at(new Text(account, SWT.PASSWORD), 10, 40, 100, 25);
    Text field = at(new Text(account, SWT.NONE), 10, 70, 100, 25);
    assertThat("unmasked, all but the password", masks(MaskRules.strict()), contains(covered(10, 140, 100, 25)));

    field.setData("name", "secret");
    MaskRules byName = new MaskRules(false, false, false, Collections.singleton("secret"), Collections.emptySet(),
        Collections.emptySet(), Collections.emptySet());
    account.setData(MaskRules.PROPERTY, null);
    assertThat("by its name", masks(byName), contains(covered(10, 140, 100, 25), covered(10, 170, 100, 25)));
    password.dispose();
  }

  @Test
  public void theShellItselfCanBeMarked() {
    form();
    shell.setData(MaskRules.PROPERTY, true);
    assertThat(masks(MaskRules.defaults()), contains(covered(0, 0, 400, 300)));
    shell.setData(MaskRules.PROPERTY, false);
    assertThat(masks(MaskRules.strict()), contains(covered(100, 30, 100, 25)));
  }

  @Test
  public void onlyWhatShowsIsMasked() {
    Composite small = at(new Composite(shell, SWT.NONE), 0, 0, 100, 50);
    at(new Text(small, SWT.NONE), 50, 10, 100, 25);
    at(new Text(small, SWT.NONE), 200, 10, 100, 25);
    at(new Text(shell, SWT.NONE), 500, 500, 100, 25);
    at(new Text(shell, SWT.NONE), 0, 100, 100, 25).setVisible(false);
    assertThat(masks(MaskRules.defaults()), contains(covered(50, 10, 50, 25)));
  }

  @Test
  public void controlsInAGroupAreWhereTheGroupDrawsThem() {
    Group group = at(new Group(shell, SWT.NONE), 20, 20, 200, 100);
    group.setText("Login");
    Text field = at(new Text(group, SWT.NONE), 10, 10, 100, 25);
    SwtFixture.pump(200); // GTK places a group's content below its title once it is laid out
    org.eclipse.swt.graphics.Rectangle expected = shell.getDisplay().map(group, shell, field.getBounds());
    assertThat(masks(MaskRules.defaults()), contains(covered(expected.x, expected.y, 100, 25)));
  }

  @Test
  public void tabAndToolTitlesAreText() {
    TabFolder tabs = at(new TabFolder(shell, SWT.NONE), 0, 0, 200, 100);
    new TabItem(tabs, SWT.NONE).setText("Accounts");
    CTabFolder cTabs = at(new CTabFolder(shell, SWT.NONE), 0, 120, 200, 80);
    new CTabItem(cTabs, SWT.NONE).setText("Billing");
    ToolBar tools = at(new ToolBar(shell, SWT.NONE), 220, 0, 150, 30);
    new ToolItem(tools, SWT.PUSH).setText("Export");
    new ToolItem(tools, SWT.PUSH).setImage(image);
    ToolBar cut = at(new ToolBar(shell, SWT.NONE), 220, 40, 1, 30);
    new ToolItem(cut, SWT.PUSH).setText("Nothing of it shows");
    new ToolItem(cut, SWT.PUSH).setText("Nor of this");
    shell.layout(true, true);
    SwtFixture.pump(100);
    assertThat("the default shows them", masks(MaskRules.defaults()), empty());
    // The cut tool bar shows at most a pixel of its first item, as platforms lay it out, and nothing
    // of its second.
    assertThat("strict masks each title that shows", masks(MaskRules.strict()).size(),
        org.hamcrest.Matchers.either(org.hamcrest.Matchers.is(3)).or(org.hamcrest.Matchers.is(4)));
  }

  @Test
  public void sensitiveKeywordsMaskWhatMentionsThem() {
    Text hinted = at(new Text(shell, SWT.NONE), 0, 0, 100, 25);
    hinted.setMessage("Your email");
    Label label = at(new Label(shell, SWT.NONE), 0, 30, 100, 20);
    label.setText("Password:");
    Label plain = at(new Label(shell, SWT.NONE), 0, 60, 100, 20);
    plain.setText("Hello");
    plain.setToolTipText("card number");
    Combo combo = at(new Combo(shell, SWT.DROP_DOWN), 0, 90, 100, 25);
    combo.setText("ssn");
    at(new Label(shell, SWT.NONE), 0, 130, 100, 20).setText("Nothing here");
    MaskRules sensitive = new MaskRules(false, false, false, false, true, 0.02, Collections.emptySet(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    assertThat(masks(sensitive), hasSize(4));
  }
}
