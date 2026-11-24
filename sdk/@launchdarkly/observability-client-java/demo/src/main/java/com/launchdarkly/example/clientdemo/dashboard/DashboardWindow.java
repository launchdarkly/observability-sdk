package com.launchdarkly.example.clientdemo.dashboard;

import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.client.ConnectionInformation;
import com.launchdarkly.sdk.client.IdentifyResult;
import com.launchdarkly.sdk.client.LDClient;
import com.launchdarkly.sdk.client.LDFailure;
import com.launchdarkly.sdk.client.LDStatusListener;
import com.launchdarkly.example.clientdemo.dashboard.swing.RoundPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * The dashboard window.
 *
 * <p>Each flag has a change listener. The SDK calls it on a background thread
 * when the value changes (a flag flipped in LaunchDarkly, a context switch, or
 * an offline switch), and the window re-reads the flag on the Swing thread and
 * updates the matching component.
 */
@SuppressWarnings("serial") // Swing components are never serialized here
final class DashboardWindow extends JFrame {

    private final LDClient client;
    private final DashboardSettings settings;

    private final DashboardPanel dashboard = new DashboardPanel();
    private final Map<String, JLabel> chips = new LinkedHashMap<>();
    /** The last value each flag was rendered with, so repeats don't re-randomize charts. */
    private final Map<String, Boolean> rendered = new HashMap<>();

    private final JLabel connection = new JLabel();
    private final JLabel user = new JLabel();
    private final JButton switchUser = new JButton("Switch user…");

    DashboardWindow(LDClient client, DashboardSettings settings, Optional<OfflineFlags> offline) {
        super("LaunchDarkly Dashboard");
        this.client = client;
        this.settings = settings;

        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutDown();
            }
        });

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBackground(Theme.WINDOW);
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(header(), BorderLayout.NORTH);
        content.add(dashboard, BorderLayout.CENTER);
        offline.ifPresent(flags -> content.add(offlineSwitches(flags), BorderLayout.EAST));
        setContentPane(content);

        listen();
        renderAll();
        updateConnection(client.getConnectionInformation());
        updateUser(client.getContext());

        pack();
        setLocationRelativeTo(null);
    }

    private void listen() {
        for (String key : DashboardFlags.ALL) {
            client.registerFeatureFlagListener(key,
                    changed -> SwingUtilities.invokeLater(() -> render(changed)));
        }
        client.registerStatusListener(new LDStatusListener() {
            @Override
            public void onConnectionModeChanged(ConnectionInformation info) {
                SwingUtilities.invokeLater(() -> updateConnection(info));
            }

            @Override
            public void onInternalFailure(LDFailure failure) {
                System.out.printf(">> SDK failure %s: %s%n",
                        failure.getFailureType(), failure.getMessage());
                SwingUtilities.invokeLater(() ->
                        updateConnection(client.getConnectionInformation()));
            }
        });
    }

    private void renderAll() {
        DashboardFlags.ALL.forEach(this::render);
    }

    /** Reads one flag and updates its chip and component, if the value changed. */
    private void render(String key) {
        boolean on = client.boolVariation(key, false);
        Boolean previous = rendered.put(key, on);
        if (previous != null && previous == on) {
            return;
        }
        if (previous != null) {
            LDContext person = userOf(client.getContext());
            System.out.printf(">> %s is now %s for %s%n", key, on,
                    person == null ? "(no user)" : person.getKey());
        }

        JLabel chip = chips.get(key);
        chip.setText(key + "  " + (on ? "ON" : "OFF"));
        chip.setForeground(on ? Theme.ON : Theme.OFF);

        switch (key) {
            case DashboardFlags.PROGRESS_METERS -> dashboard.showProgressMeters(on);
            case DashboardFlags.LINE_CHART -> dashboard.showLineChart(on);
            case DashboardFlags.BAR_CHART -> dashboard.showBarChart(on);
            default -> { /* the keystone only has a chip */ }
        }
    }

    private JComponent header() {
        RoundPanel header = new RoundPanel();
        header.setBackground(Theme.PANEL);
        header.setLayout(new BorderLayout(0, 8));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("LaunchDarkly client-side Java SDK  ·  Dashboard");
        title.setForeground(Theme.TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));

        JPanel status = row();
        JLabel mode = new JLabel(settings.isLive()
                ? "live (" + settings.credentialDescription() + ")"
                : "offline (use the switches on the right)");
        mode.setForeground(Theme.MUTED);
        connection.setForeground(Theme.MUTED);
        user.setForeground(Theme.TEXT);
        switchUser.addActionListener(e -> promptForUser());
        status.add(mode);
        status.add(separator());
        status.add(connection);
        status.add(separator());
        status.add(user);
        status.add(switchUser);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);
        top.add(status, BorderLayout.EAST);

        JPanel flags = row();
        for (String key : DashboardFlags.ALL) {
            JLabel chip = new JLabel(key);
            chip.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            chip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)));
            chips.put(key, chip);
            flags.add(chip);
        }

        header.add(top, BorderLayout.NORTH);
        header.add(flags, BorderLayout.SOUTH);
        return header;
    }

    /** Offline only: a switch per flag, standing in for the LaunchDarkly UI. */
    private JComponent offlineSwitches(OfflineFlags flags) {
        RoundPanel panel = new RoundPanel();
        panel.setBackground(Theme.PANEL);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JLabel heading = new JLabel("Flags (offline)");
        heading.setForeground(Theme.TEXT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        panel.add(heading);
        panel.add(Box.createVerticalStrut(4));
        JLabel note = new JLabel("<html>Components need<br><b>dashboard</b> on, as<br>"
                + "with the prerequisite<br>in LaunchDarkly.</html>");
        note.setForeground(Theme.MUTED);
        panel.add(note);
        panel.add(Box.createVerticalStrut(10));

        for (String key : DashboardFlags.ALL) {
            JCheckBox box = new JCheckBox(key, flags.isSwitchedOn(key));
            box.setOpaque(false);
            box.setForeground(Theme.TEXT);
            box.addActionListener(e -> flags.set(key, box.isSelected()));
            panel.add(box);
        }
        return panel;
    }

    private void updateConnection(ConnectionInformation info) {
        ConnectionInformation.ConnectionMode mode = info.getConnectionMode();
        if (!settings.isLive()) {
            // TestData reports itself as a stream; say what it really is.
            connection.setText("flags: local TestData");
            connection.setForeground(Theme.MUTED);
            return;
        }
        LDFailure failure = info.getLastFailure();
        // STREAMING is reported while retrying too; a failure newer than the
        // last success means it isn't currently receiving data.
        Long success = info.getLastSuccessfulConnection();
        Long failed = info.getLastFailedConnection();
        boolean failing = failed != null && (success == null || failed > success);

        connection.setText("connection: " + mode + (failing ? " (retrying)" : ""));
        connection.setToolTipText(failure == null ? null
                : failure.getFailureType() + ": " + failure.getMessage());
        Color color = switch (mode) {
            case STREAMING, POLLING -> failing ? Theme.WARN : Theme.ON;
            case SET_OFFLINE, SHUTDOWN -> Theme.MUTED;
        };
        connection.setForeground(color);
    }

    private void updateUser(LDContext context) {
        LDContext person = userOf(context);
        user.setText(person == null ? "(no user)"
                : person.getName() + " <" + person.getKey() + ">");
    }

    /**
     * The {@code user} part of the client's context. With automatic environment
     * attributes on, the SDK wraps the user in a multi-context alongside
     * {@code ld_application} and {@code ld_device}, and the multi-context itself
     * has no key or name.
     */
    private static LDContext userOf(LDContext context) {
        if (context == null) {
            return null;
        }
        if (!context.isMultiple()) {
            return context;
        }
        return context.getIndividualContext(ContextKind.DEFAULT);
    }

    /** Asks for another user and identifies as them, off the Swing thread. */
    private void promptForUser() {
        LDContext current = userOf(client.getContext());
        JTextField email = new JTextField(current == null ? "" : current.getKey(), 24);
        JTextField name = new JTextField(current == null || current.getName() == null
                ? "" : current.getName(), 24);
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(new JLabel("Email (the context key)"));
        form.add(email);
        form.add(Box.createVerticalStrut(8));
        form.add(new JLabel("Name"));
        form.add(name);

        int result = JOptionPane.showConfirmDialog(this, form, "Switch user",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        String key = email.getText().trim();
        if (result != JOptionPane.OK_OPTION || key.isEmpty()) {
            return;
        }
        String display = name.getText().trim().isEmpty() ? key : name.getText().trim();
        LDContext next = DashboardSettings.userContext(key, display);

        switchUser.setEnabled(false);
        user.setText("identifying " + key + " …");
        Thread identify = new Thread(() -> {
            String outcome;
            try {
                IdentifyResult identified = client.identify(next).get(10, TimeUnit.SECONDS);
                outcome = identified.getStatus()
                        + (identified.getError() == null ? "" : ": " + identified.getError().getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                outcome = "interrupted";
            } catch (ExecutionException | TimeoutException e) {
                outcome = "failed: " + e;
            }
            System.out.printf(">> identify %s as %s%n", outcome, key);
            SwingUtilities.invokeLater(() -> {
                switchUser.setEnabled(true);
                updateUser(client.getContext());
                // Listeners fire for flags whose value changed; this catches any
                // that changed while identify was still running.
                renderAll();
            });
        }, "dashboard-identify");
        identify.setDaemon(true);
        identify.start();
    }

    /** Closes the client, which sends pending analytics events, then exits. */
    private void shutDown() {
        setVisible(false);
        dispose();
        Thread closer = new Thread(() -> {
            try {
                client.close();
            } catch (IOException e) {
                System.out.println("  ! error closing the client: " + e.getMessage());
            }
            System.exit(0);
        }, "dashboard-shutdown");
        closer.start();
    }

    private static JPanel row() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        return row;
    }

    private static JLabel separator() {
        JLabel dot = new JLabel("·");
        dot.setForeground(Theme.MUTED);
        return dot;
    }
}
