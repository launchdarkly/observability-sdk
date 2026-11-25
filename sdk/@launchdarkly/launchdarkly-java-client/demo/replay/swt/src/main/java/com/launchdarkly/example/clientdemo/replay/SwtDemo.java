package com.launchdarkly.example.clientdemo.replay;

import com.launchdarkly.observability.LDReplay;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * An SWT shell recorded for session replay. With the default privacy profile the text field and the
 * password are masked; the note is masked by its data under {@link LDReplay#MASK_PROPERTY}; the rest
 * is shown. The Save button is named {@code save}, which its clicks are reported by.
 */
public final class SwtDemo {

    private SwtDemo() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] args) {
        ReplayDemo replay = ReplayDemo.start("launchdarkly-java-client-demo-replay-swt");
        Display display = new Display();
        Shell shell = new Shell(display);
        shell.setText("Session replay demo: SWT");
        shell.setLayout(new GridLayout(2, false));

        Label title = new Label(shell, SWT.NONE);
        title.setText("Account settings (SWT)");
        Font big = new Font(display, title.getFont().getFontData()[0].getName(), 20, SWT.BOLD);
        title.setFont(big);
        title.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));

        new Label(shell, SWT.NONE).setText("Email");
        Text email = new Text(shell, SWT.BORDER);
        email.setText("jane@example.com");
        email.setLayoutData(new GridData(240, SWT.DEFAULT));
        new Label(shell, SWT.NONE).setText("Password");
        Text password = new Text(shell, SWT.BORDER | SWT.PASSWORD);
        password.setText("hunter2");
        password.setLayoutData(new GridData(240, SWT.DEFAULT));
        new Label(shell, SWT.NONE);
        Button save = new Button(shell, SWT.PUSH);
        save.setText("Save");
        save.setData("name", "save");
        save.addListener(SWT.Selection, event -> replay.save());

        Composite note = new Composite(shell, SWT.NONE);
        note.setLayout(new GridLayout());
        note.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
        new Label(note, SWT.NONE).setText(ReplayDemo.NOTE);
        note.setData(LDReplay.MASK_PROPERTY, true);

        Label flagsLabel = new Label(shell, SWT.NONE);
        flagsLabel.setText("Flags");
        flagsLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
        List flags = new List(shell, SWT.BORDER);
        ReplayDemo.FLAGS.forEach(flags::add);
        flags.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

        Label status = new Label(shell, SWT.NONE);
        status.setText(replay.status());
        status.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));

        shell.setSize(640, 520);
        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        big.dispose();
        display.dispose();
        replay.stop();
    }
}
