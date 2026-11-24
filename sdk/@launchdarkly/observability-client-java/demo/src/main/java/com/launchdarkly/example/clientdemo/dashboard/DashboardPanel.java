package com.launchdarkly.example.clientdemo.dashboard;

import com.launchdarkly.example.clientdemo.dashboard.chart.Chart;
import com.launchdarkly.example.clientdemo.dashboard.chart.LineChart;
import com.launchdarkly.example.clientdemo.dashboard.chart.ModelChart;
import com.launchdarkly.example.clientdemo.dashboard.swing.RoundPanel;
import com.launchdarkly.example.clientdemo.dashboard.swing.progress.Progress;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.LayoutStyle;
import javax.swing.SwingConstants;

/**
 * The dashboard: three progress meters, a line chart and a bar chart, each
 * driven by one flag. A component that is switched off drains to zero rather
 * than disappearing, so the layout stays put while you flip flags.
 *
 * <p>This class knows nothing about LaunchDarkly; {@link DashboardWindow} calls
 * the {@code show} methods when flag values change. Call them on the Swing
 * event thread.
 */
@SuppressWarnings("serial") // Swing components are never serialized here
final class DashboardPanel extends JPanel {

    private static final String[] MONTHS = {
        "January", "February", "March", "April", "May", "June"
    };

    private static final Color[] CHART_COLORS = {
        new Color(255, 0, 0),     // Income primary
        new Color(255, 127, 127), // Income secondary
        new Color(0, 0, 255),     // Expense primary
        new Color(127, 127, 255), // Expense secondary
        new Color(0, 255, 0),     // Profit primary
        new Color(127, 255, 127), // Profit secondary
        new Color(255, 255, 0),   // Cost primary
        new Color(255, 255, 127)  // Cost secondary
    };

    private static final String[] LEGEND_LABELS = {"Income", "Expense", "Profit", "Cost"};

    private static final Color PANEL = new Color(51, 51, 51);
    private static final Color TEXT = new Color(220, 220, 220);

    private final Random random = new Random();

    DashboardPanel() {
        initComponents();
        setOpaque(false);
        styleCharts();
        styleProgressMeters();
    }

    /** Fills the progress meters with sample values, or drains them. */
    void showProgressMeters(boolean on) {
        for (Progress meter : List.of(progress1, progress2, progress3)) {
            if (on) {
                meter.setValue(0);
                meter.start();
                meter.setValue(random.nextInt(101));
            } else {
                meter.setValue(0);
            }
        }
    }

    /** Plots sample data on the line chart, or flattens it. */
    void showLineChart(boolean on) {
        lineChart.updateData(sampleData(on));
    }

    /** Plots sample data on the bar chart, or flattens it. */
    void showBarChart(boolean on) {
        chart.updateData(sampleData(on));
    }

    private List<ModelChart> sampleData(boolean on) {
        List<ModelChart> data = new ArrayList<>();
        for (String month : MONTHS) {
            double[] values = on
                    ? new double[] {
                        random.nextInt(1001), random.nextInt(1001),
                        random.nextInt(1001), random.nextInt(1001)}
                    : new double[] {0, 0, 0, 0};
            data.add(new ModelChart(month, values));
        }
        return data;
    }

    private void styleCharts() {
        chart.setOpaque(true);
        chart.setBackground(PANEL);
        chart.setForeground(TEXT);
        chart.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        lineChart.setOpaque(true);
        lineChart.setBackground(PANEL);
        lineChart.setForeground(TEXT);
        lineChart.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        for (int i = 0; i < LEGEND_LABELS.length; i++) {
            chart.addLegend(LEGEND_LABELS[i], CHART_COLORS[i * 2], CHART_COLORS[i * 2 + 1]);
            lineChart.addLegend(LEGEND_LABELS[i], CHART_COLORS[i * 2], CHART_COLORS[i * 2 + 1]);
        }
        chart.updateData(sampleData(false));
        lineChart.updateData(sampleData(false));
    }

    private void styleProgressMeters() {
        progress1.setBackground(Color.BLACK);
        progress1.setForeground(new Color(255, 31, 31));
        progress1.setValue(0);

        progress2.setBackground(Color.BLACK);
        progress2.setForeground(new Color(31, 255, 31));
        progress2.setValue(0);

        progress3.setBackground(Color.BLACK);
        progress3.setForeground(new Color(31, 31, 255));
        progress3.setValue(0);
    }

    // Layout carried over from the original dashboard's NetBeans-generated form.
    private void initComponents() {
        roundPanel1 = new RoundPanel();
        jPanel1 = new JPanel();
        progress1 = new Progress();
        jLabel1 = new JLabel();
        jLabel2 = new JLabel();
        jPanel2 = new JPanel();
        progress2 = new Progress();
        jLabel3 = new JLabel();
        jPanel3 = new JPanel();
        progress3 = new Progress();
        jLabel4 = new JLabel();
        roundPanel2 = new RoundPanel();
        chart = new Chart();
        roundPanel3 = new RoundPanel();
        lineChart = new LineChart();

        roundPanel1.setBackground(new Color(51, 51, 51));

        jPanel1.setOpaque(false);

        progress1.setBackground(new Color(66, 246, 84));
        progress1.setForeground(new Color(19, 153, 32));

        jLabel1.setFont(new java.awt.Font("sansserif", 0, 14));
        jLabel1.setForeground(new Color(220, 220, 220));
        jLabel1.setHorizontalAlignment(SwingConstants.CENTER);
        jLabel1.setText("Total Income Sold");

        GroupLayout jPanel1Layout = new GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
                    .addComponent(progress1, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel1, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addGap(18, 18, 18)
                .addComponent(progress1, GroupLayout.PREFERRED_SIZE, 142, GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jLabel2.setFont(new java.awt.Font("sansserif", 1, 15));
        jLabel2.setForeground(new Color(220, 220, 220));
        jLabel2.setText("Report Monthly");
        jLabel2.setBorder(BorderFactory.createEmptyBorder(1, 10, 1, 1));

        jPanel2.setOpaque(false);

        progress2.setBackground(new Color(132, 66, 246));
        progress2.setForeground(new Color(64, 18, 153));

        jLabel3.setFont(new java.awt.Font("sansserif", 0, 14));
        jLabel3.setForeground(new Color(220, 220, 220));
        jLabel3.setHorizontalAlignment(SwingConstants.CENTER);
        jLabel3.setText("Total Income Profit");

        GroupLayout jPanel2Layout = new GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
                    .addComponent(progress2, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel3, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel3)
                .addGap(18, 18, 18)
                .addComponent(progress2, GroupLayout.PREFERRED_SIZE, 142, GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel3.setOpaque(false);

        progress3.setBackground(new Color(66, 193, 246));
        progress3.setForeground(new Color(26, 132, 181));

        jLabel4.setFont(new java.awt.Font("sansserif", 0, 14));
        jLabel4.setForeground(new Color(220, 220, 220));
        jLabel4.setHorizontalAlignment(SwingConstants.CENTER);
        jLabel4.setText("Total Expense");

        GroupLayout jPanel3Layout = new GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
                    .addComponent(progress3, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel4, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel4)
                .addGap(18, 18, 18)
                .addComponent(progress3, GroupLayout.PREFERRED_SIZE, 142, GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        GroupLayout roundPanel1Layout = new GroupLayout(roundPanel1);
        roundPanel1.setLayout(roundPanel1Layout);
        roundPanel1Layout.setHorizontalGroup(
            roundPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(roundPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(roundPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addGroup(roundPanel1Layout.createSequentialGroup()
                        .addComponent(jPanel1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel2, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel3, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jLabel2, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        roundPanel1Layout.setVerticalGroup(
            roundPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(roundPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2)
                .addGap(20, 20, 20)
                .addGroup(roundPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, GroupLayout.Alignment.TRAILING, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel2, GroupLayout.Alignment.TRAILING, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel3, GroupLayout.Alignment.TRAILING, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        roundPanel2.setBackground(new Color(51, 51, 51));
        
        // Set minimum size for main chart to ensure it's visible
        chart.setMinimumSize(new Dimension(200, 300));
        chart.setPreferredSize(new Dimension(400, 400));

        GroupLayout roundPanel2Layout = new GroupLayout(roundPanel2);
        roundPanel2.setLayout(roundPanel2Layout);
        roundPanel2Layout.setHorizontalGroup(
            roundPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(roundPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(chart, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        roundPanel2Layout.setVerticalGroup(
            roundPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(roundPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(chart, GroupLayout.DEFAULT_SIZE, 384, Short.MAX_VALUE)
                .addContainerGap())
        );

        roundPanel3.setBackground(new Color(51, 51, 51));

        // Set minimum size for line chart to ensure it's visible
        lineChart.setMinimumSize(new Dimension(100, 200));
        lineChart.setPreferredSize(new Dimension(200, 300));

        GroupLayout roundPanel3Layout = new GroupLayout(roundPanel3);
        roundPanel3.setLayout(roundPanel3Layout);
        roundPanel3Layout.setHorizontalGroup(
            roundPanel3Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(roundPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lineChart, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        roundPanel3Layout.setVerticalGroup(
            roundPanel3Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(roundPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lineChart, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(roundPanel1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(roundPanel3, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(roundPanel2, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
                    .addComponent(roundPanel1, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(roundPanel3, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(10, 10, 10)
                .addComponent(roundPanel2, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }

    private Chart chart;
    private JLabel jLabel1;
    private JLabel jLabel2;
    private JLabel jLabel3;
    private JLabel jLabel4;
    private JPanel jPanel1;
    private JPanel jPanel2;
    private JPanel jPanel3;
    private LineChart lineChart;
    private Progress progress1;
    private Progress progress2;
    private Progress progress3;
    private RoundPanel roundPanel1;
    private RoundPanel roundPanel2;
    private RoundPanel roundPanel3;
}
