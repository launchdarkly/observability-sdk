package com.launchdarkly.example.clientdemo.dashboard.swing.progress;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

@SuppressWarnings("serial") // Swing components are never serialized here
public final class Progress extends JProgressBar {

    private ProgressCircleUI ui;

    public Progress() {
        setMinimum(0);
        setMaximum(100);
        
        // Configure appearance
        setOpaque(false);
        setStringPainted(true);
        setFont(getFont().deriveFont(24f).deriveFont(java.awt.Font.BOLD)); // Increased font size
        
        // Set modern colors
        setForeground(new Color(0, 122, 255)); // Bright blue
        setBackground(new Color(45, 45, 45));  // Darker gray for better contrast
        
        // Initialize UI last to ensure proper setup
        ui = new ProgressCircleUI();
        setUI(ui);
        
        // Initialize with 0
        setValue(0);
    }

    @Override
    public void updateUI() {
        // Preserve the ProgressCircleUI instead of letting Swing replace it
        if (ui == null) {
            ui = new ProgressCircleUI();
        }
        setUI(ui);
    }

    @Override
    public void setForeground(Color fg) {
        super.setForeground(fg);
        if (ui != null) {
            SwingUtilities.invokeLater(this::repaint);
        }
    }

    @Override
    public void setBackground(Color bg) {
        super.setBackground(bg);
        if (ui != null) {
            SwingUtilities.invokeLater(this::repaint);
        }
    }

    @Override
    public String getString() {
        if (ui != null) {
            double currentValue = ui.getStartValue() + (ui.getEndValue() - ui.getStartValue()) * ui.getAnimate();
            return String.format("%d%%", Math.round(currentValue * 100));
        }
        return String.format("%d%%", Math.round(getPercentComplete() * 100));
    }

    @Override
    public void setValue(int n) {
        if (n < getMinimum()) {
            n = getMinimum();
        }
        if (n > getMaximum()) {
            n = getMaximum();
        }
        if (n != getValue()) {
            final int value = n;
            SwingUtilities.invokeLater(() -> {
                // Set the value first
                super.setValue(value);
                // Then start animation if UI is available
                if (ui != null) {
                    ui.start();
                }
            });
        }
    }

    @Override
    public Dimension getPreferredSize() {
        // Ensure square aspect ratio
        Dimension size = super.getPreferredSize();
        int max = Math.max(size.width, size.height);
        return new Dimension(max, max);
    }

    @Override
    public Dimension getMinimumSize() {
        // Ensure minimum square size
        return new Dimension(100, 100);
    }

    /**
     * Manually start the progress animation.
     * Note: setValue() automatically starts the animation, so this is only needed
     * for special cases where manual control is desired.
     */
    public void start() {
        if (ui != null) {
            SwingUtilities.invokeLater(() -> ui.start());
        }
    }
}
