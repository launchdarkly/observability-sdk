package com.launchdarkly.example.clientdemo.dashboard;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;

/** The startup dialog: credential, then the user to evaluate flags for. */
final class SetupDialog {

    private SetupDialog() {
        throw new AssertionError("No instances");
    }

    /**
     * Asks for the settings, pre-filled from the environment. Call on the Swing
     * event thread.
     *
     * @return the settings, or empty if the user cancelled
     */
    static Optional<DashboardSettings> ask(DashboardSettings defaults) {
        // A password field, so the key isn't on screen when the demo is shared.
        JPasswordField credential = new JPasswordField(defaults.credential().orElse(""), 28);
        JTextField email = new JTextField(defaults.email(), 28);
        JTextField name = new JTextField(defaults.name(), 28);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        panel.add(label("Mobile key or client-side ID"), gbc);
        panel.add(credential, gbc);
        panel.add(hint("Leave blank to run offline, with switches for each flag."), gbc);
        panel.add(Box.createVerticalStrut(8), gbc);
        panel.add(label("Email (the context key)"), gbc);
        panel.add(email, gbc);
        panel.add(Box.createVerticalStrut(8), gbc);
        panel.add(label("Name"), gbc);
        panel.add(name, gbc);
        style(panel);

        while (true) {
            int result = JOptionPane.showConfirmDialog(null, panel, "LaunchDarkly Dashboard",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return Optional.empty();
            }
            String emailValue = email.getText().trim();
            String nameValue = name.getText().trim();
            if (emailValue.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Enter an email; it is the context key.",
                        "LaunchDarkly Dashboard", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            String credentialValue = new String(credential.getPassword()).trim();
            return Optional.of(new DashboardSettings(
                    credentialValue.isEmpty() ? Optional.empty() : Optional.of(credentialValue),
                    emailValue,
                    nameValue.isEmpty() ? emailValue : nameValue));
        }
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.TEXT);
        return label;
    }

    private static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.MUTED);
        label.setFont(label.getFont().deriveFont(11f));
        return label;
    }

    private static void style(JPanel panel) {
        panel.setBackground(Theme.DIALOG);
        for (Component component : panel.getComponents()) {
            component.setBackground(Theme.DIALOG);
            if (component instanceof JTextComponent field) {
                field.setForeground(Theme.TEXT);
                field.setCaretColor(Theme.TEXT);
                field.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.BORDER),
                        BorderFactory.createEmptyBorder(4, 4, 4, 4)));
            }
        }
    }
}
