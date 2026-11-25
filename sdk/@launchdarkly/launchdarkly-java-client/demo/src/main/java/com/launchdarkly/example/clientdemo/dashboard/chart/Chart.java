package com.launchdarkly.example.clientdemo.dashboard.chart;

import com.launchdarkly.example.clientdemo.dashboard.chart.blankchart.BlankPlotChart;
import com.launchdarkly.example.clientdemo.dashboard.chart.blankchart.BlankPlotChatRender;
import com.launchdarkly.example.clientdemo.dashboard.chart.blankchart.SeriesSize;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Timer;

@SuppressWarnings("serial") // Swing components are never serialized here
public final class Chart extends javax.swing.JPanel {

    DecimalFormat df = new DecimalFormat("#,##0.##");
    private List<ModelLegend> legends = new ArrayList<>();
    
    public List<ModelLegend> getLegends() {
        return legends;
    }
    private List<ModelChart> model = new ArrayList<>();
    private List<ModelChart> previousModel = new ArrayList<>();
    private final int seriesSize = 18;
    private final int seriesSpace = 10;
    private Timer timer;
    private float animate;
    private long startTime;
    private static final int ANIMATION_DURATION = 1200; // Longer duration for smoother effect
    private String showLabel;
    private Point labelLocation = new Point();

    public Chart() {
        initComponents();
        setOpaque(false);
        // Initialize animation timer
        timer = new Timer(16, e -> { // ~60fps
            long elapsed = System.currentTimeMillis() - startTime;
            float progress = Math.min(1f, (float) elapsed / ANIMATION_DURATION);
            
            // Smooth easing function
            animate = easeInOutQuad(progress);
            blankPlotChart.setAnimateProgress(animate);
            
            if (progress >= 1f) {
                animate = 1f;
                ((Timer)e.getSource()).stop();
            }
            repaint();
        });
        blankPlotChart.setBlankPlotChatRender(new BlankPlotChatRender() {
            @Override
            public int getMaxLegend() {
                return legends.size();
            }

            @Override
            public String getLabelText(int index) {
                return model.get(index).getLabel();
            }

            @Override
            public void renderSeries(BlankPlotChart chart, Graphics2D g2, SeriesSize size, int index) {
                double totalSeriesWidth = (seriesSize * legends.size()) + (seriesSpace * (legends.size() - 1));
                double x = (size.getWidth() - totalSeriesWidth) / 2;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
                double[] currentValues = model.get(index).getValues();
                double[] prevValues = !previousModel.isEmpty() ? previousModel.get(index).getValues() : new double[currentValues.length];
                for (int i = 0; i < Math.min(legends.size(), currentValues.length); i++) {
                    ModelLegend legend = legends.get(i);
                    // Interpolate between previous and current values
                    double prevValue = i < prevValues.length ? prevValues[i] : 0;
                    double value = prevValue + (currentValues[i] - prevValue) * animate;
                    double seriesValues = chart.getSeriesValuesOf(value, size.getHeight());
                    int s = seriesSize / 2;
                    int sy = seriesSize / 3;
                    int px[] = {(int) (size.getX() + x), (int) (size.getX() + x + s), (int) (size.getX() + x + s), (int) (size.getX() + x)};
                    int py[] = {(int) (size.getY() + size.getHeight() - seriesValues), (int) (size.getY() + size.getHeight() - seriesValues + sy), (int) (size.getY() + size.getHeight() + sy), (int) (size.getY() + size.getHeight())};
                    GradientPaint gra = new GradientPaint((int) (size.getX() + x) - s, 0, legend.getColorLight(), (int) (size.getX() + x + s), 0, legend.getColor());
                    g2.setPaint(gra);
                    g2.fillPolygon(px, py, px.length);
                    int px1[] = {(int) (size.getX() + x + s), (int) (size.getX() + x + seriesSize), (int) (size.getX() + x + seriesSize), (int) (size.getX() + x + s)};
                    int py1[] = {(int) (size.getY() + size.getHeight() - seriesValues + sy), (int) (size.getY() + size.getHeight() - seriesValues), (int) (size.getY() + size.getHeight()), (int) (size.getY() + size.getHeight() + sy)};
                    g2.setColor(legend.getColorLight());
                    g2.fillPolygon(px1, py1, px1.length);
                    int px2[] = {(int) (size.getX() + x), (int) (size.getX() + x + s), (int) (size.getX() + x + seriesSize), (int) (size.getX() + x + s)};
                    int py2[] = {(int) (size.getY() + size.getHeight() - seriesValues), (int) (size.getY() + size.getHeight() - seriesValues - sy), (int) (size.getY() + size.getHeight() - seriesValues), (int) (size.getY() + size.getHeight() - seriesValues + sy)};
                    g2.fillPolygon(px2, py2, px2.length);
                    x += seriesSpace + seriesSize;
                }
                if (showLabel != null) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
                    Dimension s = getLabelWidth(showLabel, g2);
                    int space = 3;
                    int spaceTop = 5;
                    g2.setColor(new Color(30, 30, 30));
                    g2.fillRoundRect(labelLocation.x - s.width / 2 - 3, labelLocation.y - s.height - space * 2 - spaceTop, s.width + space * 2, s.height + space * 2, 10, 10);
                    g2.setColor(new Color(200, 200, 200));
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                    g2.drawString(showLabel, labelLocation.x - s.width / 2, labelLocation.y - spaceTop - space * 2);
                }
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            }

            @Override
            public void renderSeries(BlankPlotChart chart, Graphics2D g2, SeriesSize size, int index, List<Path2D.Double> gra) {
            }

            @Override
            public void renderGraphics(Graphics2D g2, List<Path2D.Double> gra) {

            }

            @Override
            public boolean mouseMoving(BlankPlotChart chart, MouseEvent evt, Graphics2D g2, SeriesSize size, int index) {
                double totalSeriesWidth = (seriesSize * legends.size()) + (seriesSpace * (legends.size() - 1));
                double x = (size.getWidth() - totalSeriesWidth) / 2;
                double[] currentValues = model.get(index).getValues();
                double[] prevValues = !previousModel.isEmpty() ? previousModel.get(index).getValues() : new double[currentValues.length];
                for (int i = 0; i < Math.min(legends.size(), currentValues.length); i++) {
                    // Interpolate between previous and current values
                    double prevValue = i < prevValues.length ? prevValues[i] : 0;
                    double value = prevValue + (currentValues[i] - prevValue) * animate;
                    double seriesValues = chart.getSeriesValuesOf(value, size.getHeight());
                    int s = seriesSize / 2;
                    int sy = seriesSize / 3;
                    int px[] = {(int) (size.getX() + x), (int) (size.getX() + x + s), (int) (size.getX() + x + seriesSize), (int) (size.getX() + x + seriesSize), (int) (size.getX() + x + s), (int) (size.getX() + x)};
                    int py[] = {(int) (size.getY() + size.getHeight() - seriesValues), (int) (size.getY() + size.getHeight() - seriesValues - sy), (int) (size.getY() + size.getHeight() - seriesValues), (int) (size.getY() + size.getHeight()), (int) (size.getY() + size.getHeight() + sy), (int) (size.getY() + size.getHeight())};
                    if (new Polygon(px, py, px.length).contains(evt.getPoint())) {
                        showLabel = value == 0 ? "0" : df.format(value);
                        labelLocation.setLocation((int) (size.getX() + x + s), (int) (size.getY() + size.getHeight() - seriesValues - sy));
                        chart.repaint();
                        return true;
                    }
                    x += seriesSpace + seriesSize;
                }
                return false;
            }
        });
    }

    public void addLegend(String name, Color color, Color color1) {
        ModelLegend data = new ModelLegend(name, color, color1);
        legends.add(data);
        panelLegend.add(new LegendItem(data));
        panelLegend.repaint();
        panelLegend.revalidate();
    }

    public void addData(ModelChart data) {
        // Store empty state as previous for initial animation
        if (model.isEmpty()) {
            ModelChart emptyData = new ModelChart();
            emptyData.setLabel(data.getLabel());
            double[] emptyValues = new double[data.getValues().length];
            emptyData.setValues(emptyValues);
            previousModel.add(emptyData);
        }
        
        model.add(data);
        blankPlotChart.setLabelCount(model.size());
        double max = data.getMaxValues();
        if (max > blankPlotChart.getMaxValues()) {
            blankPlotChart.setMaxValues(max);
        }
        
        // Start animation from zero for initial data
        animate = 0;
        if (model.size() == 1) {
            start();
        }
    }

    public void clear() {
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
        animate = 0;
        showLabel = null;
        model.clear();
        repaint();
    }

    public void clearAll() {
        clear();
        blankPlotChart.setLabelCount(0);
        panelLegend.removeAll();
        panelLegend.revalidate();
        panelLegend.repaint();
    }

    public void start() {
        showLabel = null;
        animate = 0;
        if (timer != null) {
            if (timer.isRunning()) {
                timer.stop();
            }
            startTime = System.currentTimeMillis();
            timer.start();
        }
    }

    public void updateData(List<ModelChart> newData) {
        // Keep existing data for smooth transition
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
        
        // Update max values for proper scaling
        double maxValue = 0.01; // Minimum value to avoid division by zero
        for (ModelChart chart : newData) {
            double chartMax = chart.getMaxValues();
            if (chartMax > maxValue) {
                maxValue = chartMax;
            }
        }
        blankPlotChart.setMaxValues(maxValue);
        
        // Store previous model and update with new data
        previousModel = new ArrayList<>(model);
        model = new ArrayList<>(newData);
        blankPlotChart.setLabelCount(model.size());
        
        // Start animation
        start();
    }

    // Smooth easing function for animation
    // Smoother easing function with cubic interpolation
    private float easeInOutQuad(float t) {
        return t < 0.5f ? 4 * t * t * t : 1 - (float)Math.pow(-2 * t + 2, 3) / 2;
    }

    private Dimension getLabelWidth(String text, Graphics2D g2) {
        FontMetrics ft = g2.getFontMetrics();
        Rectangle2D r2 = ft.getStringBounds(text, g2);
        return new Dimension((int) r2.getWidth(), (int) r2.getHeight());
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        blankPlotChart = new com.launchdarkly.example.clientdemo.dashboard.chart.blankchart.BlankPlotChart();
        panelLegend = new javax.swing.JPanel();

        setBackground(new java.awt.Color(255, 255, 255));

        panelLegend.setOpaque(false);
        panelLegend.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 5, 0));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(panelLegend, javax.swing.GroupLayout.DEFAULT_SIZE, 573, Short.MAX_VALUE)
                    .addComponent(blankPlotChart, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(blankPlotChart, javax.swing.GroupLayout.DEFAULT_SIZE, 342, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(panelLegend, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.launchdarkly.example.clientdemo.dashboard.chart.blankchart.BlankPlotChart blankPlotChart;
    private javax.swing.JPanel panelLegend;
    // End of variables declaration//GEN-END:variables
}
