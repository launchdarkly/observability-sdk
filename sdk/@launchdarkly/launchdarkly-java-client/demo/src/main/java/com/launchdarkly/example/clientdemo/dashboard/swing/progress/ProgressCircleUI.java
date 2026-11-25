package com.launchdarkly.example.clientdemo.dashboard.swing.progress;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.Timer;

public final class ProgressCircleUI extends BasicProgressBarUI {

    private Timer timer;
    private float animate;
    private double startValue;
    private double endValue;
    private long startTime;
    private static final int ANIMATION_DURATION = 1000; // Longer duration for smoother animation
    private static final int FRAME_RATE = 60;
    private static final int FRAME_DELAY = 1000 / FRAME_RATE;

    public float getAnimate() {
        return animate;
    }

    public void setAnimate(float animate) {
        this.animate = animate;
    }

    public double getStartValue() {
        return startValue;
    }

    public double getEndValue() {
        return endValue;
    }

    public synchronized void start() {
        if (progressBar == null) {
            return;
        }

        // Calculate new end value
        double newEndValue = progressBar.getPercentComplete();
        
        // If timer hasn't been created yet, create it
        if (timer == null) {
            createTimer();
        }

        // If animation is in progress, use current position as start
        if (timer.isRunning()) {
            startValue = startValue + (endValue - startValue) * animate;
            timer.stop();
        } else {
            startValue = endValue;
        }

        // Setup new animation
        endValue = newEndValue;
        animate = 0;
        startTime = System.currentTimeMillis();

        // Start new animation
        SwingUtilities.invokeLater(() -> {
            if (progressBar != null) {
                progressBar.repaint();
                if (timer != null) {
                    timer.restart();
                }
            }
        });
    }

    private synchronized void createTimer() {
        if (timer != null) {
            timer.stop();
        }
        
        timer = new Timer(FRAME_DELAY, e -> {
            if (progressBar == null) {
                timer.stop();
                return;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            float progress = Math.min(1f, (float) elapsed / ANIMATION_DURATION);
            
            // Smooth easing function
            animate = easeInOutCubic(progress);
            
            if (progress >= 1f) {
                animate = 1f;
                timer.stop();
            }
            
            SwingUtilities.invokeLater(() -> {
                if (progressBar != null) {
                    progressBar.repaint();
                }
            });
        });
        timer.setInitialDelay(0);
        timer.setCoalesce(true);
    }

    @Override
    public void installUI(JComponent jc) {
        super.installUI(jc);
        startValue = 0;
        endValue = 0;
        animate = 0;
        
        // Stop any existing timer
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        
        // Create new timer
        createTimer();
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        if (!(g instanceof Graphics2D)) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // Enable antialiasing for smoother rendering
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            
            int width = c.getWidth();
            int height = c.getHeight();
            int size = Math.min(width, height);
            int x = (width - size) / 2;
            int y = (height - size) / 2;
            int stroke = Math.max(size / 12, 8); // Increased minimum stroke width
            int padding = stroke;
            
            // Calculate dimensions for smoother appearance
            int arcSize = size - (padding * 2);
            int arcX = x + padding;
            int arcY = y + padding;
            
            // Draw background track with alpha
            g2.setStroke(new java.awt.BasicStroke(stroke, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            Color bgColor = progressBar.getBackground();
            g2.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 128));
            g2.drawArc(arcX, arcY, arcSize, arcSize, 0, 360);
            
            // Draw progress with glow effect
            double currentValue = startValue + (endValue - startValue) * animate;
            int angle = -(int)(currentValue * 360);
            
            // Draw glow
            g2.setStroke(new java.awt.BasicStroke(stroke + 4, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            Color fgColor = progressBar.getForeground();
            g2.setColor(new Color(fgColor.getRed(), fgColor.getGreen(), fgColor.getBlue(), 50));
            g2.drawArc(arcX, arcY, arcSize, arcSize, 90, angle);
            
            // Draw main progress
            g2.setStroke(new java.awt.BasicStroke(stroke, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.setColor(fgColor);
            g2.drawArc(arcX, arcY, arcSize, arcSize, 90, angle);
            
            if (progressBar.isStringPainted()) {
                paintString(g2, x, y, size, size, 0, progressBar.getInsets());
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintString(Graphics g, int x, int y, int width, int height, int fillStart, Insets b) {
        if (!progressBar.isStringPainted()) {
            return;
        }
        
        String text = progressBar.getString();
        if (text == null || text.isEmpty()) {
            return;
        }
        
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // Calculate text position for center alignment
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            x = (progressBar.getWidth() - textWidth) / 2;
            y = (progressBar.getHeight() - textHeight) / 2 + fm.getAscent();
            
            // Draw text with shadow for better visibility
            g2.setColor(new Color(0, 0, 0, 128));
            g2.drawString(text, x + 1, y + 1);
            g2.setColor(Color.WHITE);
            g2.drawString(text, x, y);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void uninstallUI(JComponent c) {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        super.uninstallUI(c);
    }

    // Smoother easing function for animation
    private float easeInOutCubic(float t) {
        return t < 0.5f ? 4 * t * t * t : 1 - (float)Math.pow(-2 * t + 2, 3) / 2;
    }
}
