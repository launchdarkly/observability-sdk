package com.launchdarkly.example.clientdemo.dashboard;

import java.awt.Color;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/** The dark look carried over from the original dashboard. */
final class Theme {

    static final Color WINDOW = new Color(25, 25, 25);
    static final Color PANEL = new Color(51, 51, 51);
    static final Color DIALOG = new Color(45, 45, 45);
    static final Color BORDER = new Color(60, 60, 60);
    static final Color TEXT = new Color(229, 229, 229);
    static final Color MUTED = new Color(150, 150, 150);
    static final Color ON = new Color(66, 200, 110);
    static final Color OFF = new Color(220, 80, 80);
    static final Color WARN = new Color(230, 180, 60);

    private Theme() {
        throw new AssertionError("No instances");
    }

    /** Call before creating any Swing component. */
    static void install() {
        System.setProperty("apple.awt.application.name", "LaunchDarkly Dashboard");
        System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            System.out.println("  ! could not set the system look and feel: " + e.getMessage());
        }
        UIManager.put("Panel.background", DIALOG);
        UIManager.put("Panel.foreground", TEXT);
        UIManager.put("OptionPane.background", DIALOG);
        UIManager.put("OptionPane.messageBackground", DIALOG);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.questionIcon", null);
    }
}
