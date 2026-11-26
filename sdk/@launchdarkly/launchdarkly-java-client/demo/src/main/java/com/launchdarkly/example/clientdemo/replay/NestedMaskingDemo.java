package com.launchdarkly.example.clientdemo.replay;

import com.launchdarkly.observability.LDReplay;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * A Swing window for checking that a mask or unmask set on a container reaches what is inside it.
 * Sections 1 to 4 are the iOS test app's {@code NestedMaskingPropagationView}, also ported to the
 * Android e2e app; 5 and 6 add the two conflicting cases, a mark inside the opposite mark.
 *
 * <p>The window records with the default privacy profile, so text fields are masked unless
 * unmasked. Containers are marked with {@link LDReplay#mask} and {@link LDReplay#unmask}, the
 * desktop counterparts of {@code ldMask()} and {@code ldUnmask()}. Each section says what the
 * recording should show under the desktop rules, which are Android's: a mask on a component or any
 * ancestor wins and covers everything inside it; an unmask reaches everything inside it that is
 * not marked to mask.
 */
public final class NestedMaskingDemo {

    private static final Color GREEN = new Color(52, 199, 89);
    private static final Color RED = new Color(255, 59, 48);

    private NestedMaskingDemo() {
    }

    public static void main(String[] args) {
        ReplayDemo replay = ReplayDemo.start("launchdarkly-java-client-demo-replay-nested-masking");
        SwingUtilities.invokeLater(() -> show(replay));
    }

    private static void show(ReplayDemo replay) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        content.add(section("1. Baseline (no mark)",
                "Masked by the default profile, which masks text inputs.",
                new JTextField("type here")));

        JPanel unmasked = tinted(GREEN, 0.15, new JTextField("visible inside unmasked ancestor"));
        LDReplay.unmask(unmasked);
        content.add(section("2. Ancestor unmask",
                "The panel is unmasked, so the field in it shows despite the default profile.",
                unmasked));

        JPanel masked = tinted(RED, 0.15,
                new JLabel("plain label that would normally be visible"),
                new JTextField("text field inside masked ancestor"));
        LDReplay.mask(masked);
        content.add(section("3. Ancestor mask",
                "The panel is masked, so all of it is covered, the plain label too.",
                masked));

        JPanel deep = tinted(GREEN, 0.15,
                tinted(GREEN, 0.05, new JTextField("deeply nested, still unmasked")));
        LDReplay.unmask(deep);
        content.add(section("4. Deep unmask through nesting",
                "Two panels deep under an unmask, the field still shows.",
                deep));

        JPanel unmaskInside = tinted(GREEN, 0.15, new JTextField("unmasked, but inside a masked panel"));
        LDReplay.unmask(unmaskInside);
        JPanel maskOutside = tinted(RED, 0.15, new JLabel("masked outer panel"), unmaskInside);
        LDReplay.mask(maskOutside);
        content.add(section("5. Unmask inside a mask",
                "The outer mask wins: the whole outer panel is covered, the unmasked field too.",
                maskOutside));

        JPanel maskInside = tinted(RED, 0.15, new JTextField("masked, inside an unmasked panel"));
        LDReplay.mask(maskInside);
        JPanel unmaskOutside = tinted(GREEN, 0.15,
                new JLabel("unmasked outer panel"),
                new JTextField("visible, beside the masked panel"),
                maskInside);
        LDReplay.unmask(unmaskOutside);
        content.add(section("6. Mask inside an unmask",
                "The inner mask wins inside it: the inner panel is covered, the rest of the outer panel shows.",
                unmaskOutside));

        content.add(Box.createVerticalStrut(8));
        content.add(leftAligned(new JLabel(replay.status())));

        JFrame frame = new JFrame("Session replay demo: nested masking (Swing)");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                // Off the event dispatch thread, as the dashboard does: replay captures Swing
                // frames on it, and closing the client sends what is still queued.
                Thread closer = new Thread(() -> {
                    replay.stop();
                    System.exit(0);
                }, "nested-masking-shutdown");
                closer.start();
            }
        });
        frame.getContentPane().add(new JScrollPane(content), BorderLayout.CENTER);
        frame.setSize(560, 820);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /** A titled section: its heading, a note on what the recording should show, then its content. */
    private static JPanel section(String title, String note, JComponent body) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 15f));
        JLabel caption = new JLabel("<html>" + note + "</html>");
        caption.setFont(caption.getFont().deriveFont(11f));
        caption.setForeground(Color.GRAY);
        section.add(leftAligned(heading));
        section.add(Box.createVerticalStrut(4));
        section.add(leftAligned(caption));
        section.add(Box.createVerticalStrut(6));
        section.add(leftAligned(body));
        return leftAligned(section);
    }

    /** A padded panel tinted with {@code color} at {@code alpha}, holding {@code children} top to bottom. */
    private static JPanel tinted(Color color, double alpha, JComponent... children) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.setBackground(blend(color, alpha, panel.getBackground()));
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                panel.add(Box.createVerticalStrut(6));
            }
            panel.add(leftAligned(children[i]));
        }
        return leftAligned(panel);
    }

    /**
     * {@code color} at {@code alpha} over {@code base}, as an opaque color, so the panel paints
     * cleanly without translucency.
     */
    private static Color blend(Color color, double alpha, Color base) {
        return new Color(
                (int) Math.round(color.getRed() * alpha + base.getRed() * (1 - alpha)),
                (int) Math.round(color.getGreen() * alpha + base.getGreen() * (1 - alpha)),
                (int) Math.round(color.getBlue() * alpha + base.getBlue() * (1 - alpha)));
    }

    private static <T extends JComponent> T leftAligned(T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (component instanceof JTextField) {
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        }
        return component;
    }
}
