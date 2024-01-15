
package jdiskmark;

import java.awt.Color;
import java.awt.Dimension;
import java.text.NumberFormat;
import javax.swing.JProgressBar;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleInsets;

/**
 * Store GUI references for easy access
 */
public final class Gui {
    
    public static ChartPanel chartPanel = null;
    public static MainFrame mainFrame = null;
    public static SelectFrame selFrame = null;
    public static XYSeries wSeries, wAvgSeries, wMaxSeries, wMinSeries, wDrvAccess;
    public static XYSeries rSeries, rAvgSeries, rMaxSeries, rMinSeries, rDrvAccess;
    public static JFreeChart chart;
    public static JProgressBar progressBar = null;
    public static RunPanel runPanel = null;
    
    public static XYLineAndShapeRenderer bwRenderer;
    public static XYLineAndShapeRenderer msRenderer;
    
    public static ChartPanel createChartPanel() {
        
        wSeries = new XYSeries("Writes");
        wAvgSeries = new XYSeries("Write Avg");
        wMaxSeries = new XYSeries("Write Max");
        wMinSeries = new XYSeries("Write Min");
        wDrvAccess = new XYSeries("Write Access");
        
        rSeries = new XYSeries("Reads");
        rAvgSeries = new XYSeries("Read Avg");
        rMaxSeries = new XYSeries("Read Max");
        rMinSeries = new XYSeries("Read Min");
        rDrvAccess = new XYSeries("Read Access");
        
        // primary dataset mapped against the bw axis
        XYSeriesCollection bwDataset = new XYSeriesCollection();
        bwDataset.addSeries(wSeries);
        bwDataset.addSeries(wAvgSeries);
        bwDataset.addSeries(wMaxSeries);
        bwDataset.addSeries(wMinSeries);
        bwDataset.addSeries(rSeries);
        bwDataset.addSeries(rAvgSeries);
        bwDataset.addSeries(rMaxSeries);
        bwDataset.addSeries(rMinSeries);
        
        // secondary dataset mapped against ns to show disk access time
        XYSeriesCollection msDataset = new XYSeriesCollection();
        msDataset.addSeries(wDrvAccess);
        msDataset.addSeries(rDrvAccess);
        
        XYPlot plot = new XYPlot();
        plot.setBackgroundPaint(Color.DARK_GRAY);
        plot.setOutlinePaint(Color.WHITE);
        plot.setDataset(0, bwDataset);
        plot.setDataset(1, msDataset);
        
        //customize the plot with renderers and axis
        bwRenderer = new XYLineAndShapeRenderer(true, false);
        msRenderer = new XYLineAndShapeRenderer(true, false);
        
        // configure the bw series colors
        bwRenderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
        bwRenderer.setSeriesPaint(0, Color.YELLOW);     // write
        bwRenderer.setSeriesPaint(1, Color.WHITE);      // w avg
        bwRenderer.setSeriesPaint(2, Color.GREEN);      // w max
        bwRenderer.setSeriesPaint(3, Color.RED);        // w min
        bwRenderer.setSeriesPaint(4, Color.LIGHT_GRAY); // read
        bwRenderer.setSeriesPaint(5, Color.ORANGE);     // r avg
        bwRenderer.setSeriesPaint(6, Color.GREEN);      // r max
        bwRenderer.setSeriesPaint(7, Color.RED);        // r min
        
        // configure the access time ms colors
        msRenderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
        msRenderer.setSeriesPaint(0, Color.CYAN);       // w acc
        msRenderer.setSeriesPaint(1, Color.MAGENTA);    // r acc
        
        plot.setRenderer(0, bwRenderer);
        plot.setRenderer(1, msRenderer);
        
        // y axis on the left
        NumberAxis bwAxis = new NumberAxis("Bandwidth MB/s");
        bwAxis.setAutoRangeIncludesZero(false);
        
        // y axis on the right
        NumberAxis msAxis = new NumberAxis("Drive Access (ms)");
        msAxis.setAutoRange(true);
        msAxis.setAutoRangeIncludesZero(false);
        
        // x axis on the bottom
        NumberAxis sampleAxis = new NumberAxis();
        sampleAxis.setNumberFormatOverride(NumberFormat.getNumberInstance());
        sampleAxis.setAutoRangeIncludesZero(false);
        
        // link the axis to the plot
        plot.setRangeAxis(0, bwAxis);
        plot.setRangeAxis(1, msAxis);
        plot.setDomainAxis(sampleAxis);
        
        // configure the locations
        plot.setDomainAxisLocation(AxisLocation.BOTTOM_OR_RIGHT);
        plot.setRangeAxisLocation(0, AxisLocation.TOP_OR_LEFT);
        plot.setDomainAxisLocation(1, AxisLocation.BOTTOM_OR_RIGHT);
        
        // add gap between the plot area and axis so they are detached
        plot.setAxisOffset(new RectangleInsets(3, 3, 3, 3));
        
        // Map the data to the appropriate axis
        plot.mapDatasetToRangeAxis(0, 0);
        plot.mapDatasetToRangeAxis(1, 1);
        
        chart = new JFreeChart("", null , plot, true);
        
        chartPanel = new ChartPanel(chart) {
            // Only way to set the size of chart panel
            // ref: http://www.jfree.org/phpBB2/viewtopic.php?p=75516
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(500, 325);
            }
        };
        
        updateLegend();
        return chartPanel;
    }
    
    public static void addWriteMark(DiskMark mark) {
        wSeries.add(mark.markNum, mark.bwMbSec);
        wAvgSeries.add(mark.markNum, mark.cumAvg);
        if (App.showMaxMin) {
            wMaxSeries.add(mark.markNum, mark.cumMax);
            wMinSeries.add(mark.markNum, mark.cumMin);
        }
        if (App.showDriveAccess) {
            wDrvAccess.add(mark.markNum, mark.elapsedTimeMs);
        }
        Gui.mainFrame.refreshWriteMetrics();
        System.out.println(mark.toString());
    }
    public static void addReadMark(DiskMark mark) {
        rSeries.add(mark.markNum, mark.bwMbSec);
        rAvgSeries.add(mark.markNum, mark.cumAvg);
        if (App.showMaxMin) {
            rMaxSeries.add(mark.markNum, mark.cumMax);
            rMinSeries.add(mark.markNum, mark.cumMin);
        }
        if (App.showDriveAccess) {
            rDrvAccess.add(mark.markNum, mark.elapsedTimeMs);
        }
        Gui.mainFrame.refreshReadMetrics();
        System.out.println(mark.toString());
    }
    
    public static void resetTestData() {
        wSeries.clear();
        rSeries.clear();
        wAvgSeries.clear();
        rAvgSeries.clear();
        wMaxSeries.clear();
        rMaxSeries.clear();
        wMinSeries.clear();
        rMinSeries.clear();
        wDrvAccess.clear();
        rDrvAccess.clear();
        progressBar.setValue(0);
        Gui.mainFrame.refreshReadMetrics();
        Gui.mainFrame.refreshWriteMetrics();
    }
    
    public static void updateLegend() {
        bwRenderer.setSeriesVisibleInLegend(0, App.writeTest);
        bwRenderer.setSeriesVisibleInLegend(1, App.writeTest);
        bwRenderer.setSeriesVisibleInLegend(2, App.writeTest && App.showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(3, App.writeTest && App.showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(4, App.readTest);
        bwRenderer.setSeriesVisibleInLegend(5, App.readTest);
        bwRenderer.setSeriesVisibleInLegend(6, App.readTest && App.showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(7, App.readTest && App.showMaxMin);
        
        msRenderer.setSeriesVisibleInLegend(0, App.writeTest && App.showDriveAccess);
        msRenderer.setSeriesVisibleInLegend(1, App.readTest && App.showDriveAccess);
    }
}
