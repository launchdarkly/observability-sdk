package com.launchdarkly.example.clientdemo.replay;

import com.launchdarkly.observability.LDReplay;

import org.apache.pivot.collections.ArrayList;
import org.apache.pivot.collections.Map;
import org.apache.pivot.wtk.Application;
import org.apache.pivot.wtk.BoxPane;
import org.apache.pivot.wtk.DesktopApplicationContext;
import org.apache.pivot.wtk.Display;
import org.apache.pivot.wtk.Label;
import org.apache.pivot.wtk.ListView;
import org.apache.pivot.wtk.Orientation;
import org.apache.pivot.wtk.PushButton;
import org.apache.pivot.wtk.TextInput;
import org.apache.pivot.wtk.Window;

/**
 * An Apache Pivot window recorded for session replay. With the default privacy profile the text
 * input and the password are masked; the note is masked by its user data under
 * {@link LDReplay#MASK_PROPERTY}; the rest is shown. The Save button is named {@code save}.
 */
public final class PivotDemo implements Application {

    private static ReplayDemo replay;

    public static void main(String[] args) {
        replay = ReplayDemo.start("launchdarkly-java-client-demo-replay-pivot");
        DesktopApplicationContext.main(PivotDemo.class, new String[] {"--width=640", "--height=520"});
    }

    @Override
    public void startup(Display display, Map<String, String> properties) {
        Label title = new Label("Account settings (Apache Pivot)");
        title.getStyles().put("font", "Arial BOLD 22");

        TextInput email = new TextInput();
        email.setText("jane@example.com");
        TextInput password = new TextInput();
        password.setPassword(true);
        password.setText("hunter2");
        PushButton save = new PushButton("Save");
        save.setName("save");
        save.getButtonPressListeners().add(button -> replay.save());

        Label note = new Label(ReplayDemo.NOTE);
        note.getUserData().put(LDReplay.MASK_PROPERTY, true);

        ListView flags = new ListView(new ArrayList<>(ReplayDemo.FLAGS.toArray(new String[0])));

        BoxPane form = new BoxPane(Orientation.VERTICAL);
        form.getStyles().put("padding", 16);
        form.getStyles().put("spacing", 10);
        form.add(title);
        form.add(row("Email", email));
        form.add(row("Password", password));
        form.add(save);
        form.add(note);
        form.add(new Label("Flags"));
        form.add(flags);
        form.add(new Label(replay.status()));

        Window window = new Window(form);
        window.setTitle("Session replay demo: Apache Pivot");
        window.setMaximized(true);
        window.open(display);
    }

    private static BoxPane row(String label, TextInput input) {
        BoxPane row = new BoxPane(Orientation.HORIZONTAL);
        Label name = new Label(label);
        name.setPreferredWidth(80);
        input.setTextSize(28);
        row.add(name);
        row.add(input);
        return row;
    }

    @Override
    public boolean shutdown(boolean optional) {
        replay.stop();
        return false;
    }

    @Override
    public void suspend() {
    }

    @Override
    public void resume() {
    }
}
