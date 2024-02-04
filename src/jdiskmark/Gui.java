   
package jdiskmark;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
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
    
    public static enum Palette { CLASSIC, BLUE_GREEN, BARD_COOL, BARD_WARM };
    public static Palette palette = Palette.CLASSIC;
    
    public static ChartPanel chartPanel = null;
    public static MainFrame mainFrame = null;
    public static SelectDriveFrame selFrame = null;
    public static XYSeries wSeries, wAvgSeries, wMaxSeries, wMinSeries, wDrvAccess;
    public static XYSeries rSeries, rAvgSeries, rMaxSeries, rMinSeries, rDrvAccess;
    public static NumberAxis msAxis;
    public static JFreeChart chart;
    public static JProgressBar progressBar = null;
    public static BenchmarkPanel runPanel = null;
    
    public static XYLineAndShapeRenderer bwRenderer;
    public static XYLineAndShapeRenderer msRenderer;
    
    /**
     * Setup the look and feel
     */
    public static void configureLaf() {
        try {
            if (App.os.contains("Windows")) {
                UIManager.setLookAndFeel(new FlatLightLaf()); // Light theme
                // Or: UIManager.setLookAndFeel(new FlatDarkLaf()); // Dark theme
            } else if (App.os.contains("Mac OS")) {
                UIManager.setLookAndFeel("apple.laf.AquaLookAndFeel");
//                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
//                    if ("Nimbus".equals(info.getName())) {
//                        UIManager.setLookAndFeel(info.getClassName());
//                        break;
//                    }
//                }
            } else if (App.os.contains("Linux")) {
                
            } else {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
            /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
             * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
             */
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
                java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
            //</editor-fold>
        }
    }
    
    public static ChartPanel createChartPanel() {
        
        wSeries = new XYSeries("Write");
        wAvgSeries = new XYSeries("Write Avg");
        wMaxSeries = new XYSeries("Write Max");
        wMinSeries = new XYSeries("Write Min");
        wDrvAccess = new XYSeries("Write Access");
        
        rSeries = new XYSeries("Read");
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
        
        // setup plot
        XYPlot plot = new XYPlot();
        plot.setBackgroundPaint(Color.DARK_GRAY.darker());
        plot.setOutlinePaint(Color.WHITE);
        plot.setDataset(0, bwDataset);
        plot.setDataset(1, msDataset);
        
        //customize the plot with renderers and axis
        bwRenderer = new XYLineAndShapeRenderer(true, false);
        msRenderer = new XYLineAndShapeRenderer(true, false);
        
        // disable lines and enable shapes
        msRenderer.setSeriesLinesVisible(0, false);
        msRenderer.setSeriesLinesVisible(1, false);
        Shape s0 = new Rectangle2D.Double(-2.0, -2.0, 4.0, 4.0);
        Shape s1 = new Rectangle2D.Double(-2.0, -2.0, 4.0, 4.0);
        msRenderer.setSeriesShape(0, s0);
        msRenderer.setSeriesShape(1, s1);
        msRenderer.setSeriesShapesVisible(0, true);
        msRenderer.setSeriesShapesVisible(1, true);
        
        // link renderers to the plot
        plot.setRenderer(0, bwRenderer);
        plot.setRenderer(1, msRenderer);
        
        // y axis on the left
        NumberAxis bwAxis = new NumberAxis("Bandwidth MB/s");
        bwAxis.setAutoRangeIncludesZero(false);
        
        // y axis on the right
        msAxis = new NumberAxis("Average Access Time (ms)");
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
        plot.setRangeAxisLocation(1, AxisLocation.BOTTOM_OR_RIGHT);
        
        // add gap between the plot area and axis so they are detached
        plot.setAxisOffset(new RectangleInsets(3, 3, 3, 3));
        
        // Map the data to the appropriate axis
        plot.mapDatasetToRangeAxis(0, 0);
        plot.mapDatasetToRangeAxis(1, 1);
        
        chart = new JFreeChart("", null , plot, true);
        
        // correct the parenthesis from being below vertical centering
        chart.getTitle().setFont(new Font("Verdana", Font.BOLD, 17));
        
        chartPanel = new ChartPanel(chart) {
            // Only way to set the size of chart panel
            // ref: http://www.jfree.org/phpBB2/viewtopic.php?p=75516
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(500, 325);
            }
        };
        
        updateLegendAndAxis();
        return chartPanel;
    }
    
    public static void addWriteSample(Sample s) {
        wSeries.add(s.sampleNum, s.bwMbSec);
        wAvgSeries.add(s.sampleNum, s.cumAvg);
        if (App.showMaxMin) {
            wMaxSeries.add(s.sampleNum, s.cumMax);
            wMinSeries.add(s.sampleNum, s.cumMin);
        }
        if (App.showDriveAccess) {
            wDrvAccess.add(s.sampleNum, s.accessTimeMs);
        }
        Gui.mainFrame.refreshWriteMetrics();
        System.out.println(s.toString());
    }
    public static void addReadSample(Sample s) {
        rSeries.add(s.sampleNum, s.bwMbSec);
        rAvgSeries.add(s.sampleNum, s.cumAvg);
        if (App.showMaxMin) {
            rMaxSeries.add(s.sampleNum, s.cumMax);
            rMinSeries.add(s.sampleNum, s.cumMin);
        }
        if (App.showDriveAccess) {
            rDrvAccess.add(s.sampleNum, s.accessTimeMs);
        }
        Gui.mainFrame.refreshReadMetrics();
        System.out.println(s.toString());
    }
    
    public static void resetBenchmarkData() {
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
    
    public static void updateLegendAndAxis() {
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
        
        msAxis.setVisible(App.showDriveAccess);
    }
    
    public static void updateLegendAndAxis(Benchmark b) {
        boolean isWriteTest = b.ioMode == Benchmark.IOMode.WRITE;
        boolean isReadTest = b.ioMode == Benchmark.IOMode.READ;
        bwRenderer.setSeriesVisibleInLegend(0, isWriteTest);
        bwRenderer.setSeriesVisibleInLegend(1, isWriteTest);
        bwRenderer.setSeriesVisibleInLegend(2, isWriteTest && App.showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(3, isWriteTest && App.showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(4, isReadTest);
        bwRenderer.setSeriesVisibleInLegend(5, isReadTest);
        bwRenderer.setSeriesVisibleInLegend(6, isReadTest && App.showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(7, isReadTest && App.showMaxMin);
        
        msRenderer.setSeriesVisibleInLegend(0, isWriteTest && App.showDriveAccess);
        msRenderer.setSeriesVisibleInLegend(1, isReadTest && App.showDriveAccess);
        
        msAxis.setVisible(App.showDriveAccess);
    }
    
    /**
     * GH-2 need solution for dropping catch
     */
    static public void dropCache() {
        String osName = System.getProperty("os.name");
        if (osName.contains("Linux")) {
            if (App.isRoot) {
                // GH-2 automate catch dropping
                UtilOs.flushDataToDriveLinux();
                UtilOs.dropWriteCacheLinux();
            } else {
                JOptionPane.showMessageDialog(Gui.mainFrame, 
                        """
                        Run jDiskMark with sudo to automatically clear the disk cache.
                        
                        For a valid READ benchmark please clear the disk cache now 
                        by using \"sudo sync; echo 1 > /proc/sys/vm/drop_caches\".
                        
                        Press OK to continue when disk cache has been dropped.""",
                        "Clear Disk Cache Now",
                        JOptionPane.PLAIN_MESSAGE);
            }
        } else if (osName.contains("Mac OS")) {
            if (App.isRoot) {
                // GH-2 automate catch dropping
                UtilOs.flushDataToDriveMacOs();
                UtilOs.dropWriteCacheMacOs();
            } else {
                JOptionPane.showMessageDialog(Gui.mainFrame, 
                        """
                        For valid READ benchmarks please clear the disk cache.
                        
                        Removable drives can be disconnected and reconnected.
                        
                        For system drives perform a WRITE benchmark, restart 
                        the OS and then perform a READ benchmark.
                        
                        Press OK to continue when disk cache has been cleared.""",
                        "Clear Disk Cache Now",
                        JOptionPane.PLAIN_MESSAGE);
            }
        } else if (osName.contains("Windows")) {
            boolean emptyStandbyListExist = Files.exists(Paths.get(".\\EmptyStandbyList.exe"));
            System.out.println("emptyStandbyListExist=" + emptyStandbyListExist);
            if (App.isAdmin && emptyStandbyListExist) {
                // GH-2 drop cahe, delays in place of flushing cache
                try { Thread.sleep(1300); } catch (InterruptedException ex) {}
                UtilOs.emptyStandbyListWindows();
                try { Thread.sleep(700); } catch (InterruptedException ex) {}
            } else  if (App.isAdmin && !emptyStandbyListExist) {
                JOptionPane.showMessageDialog(Gui.mainFrame, 
                        """
                        Unable to find EmptyStandbyList.exe. This must be
                        present in the install directory for the disk cache
                        to be automatically cleared.
                        
                        For valid READ benchmarks please clear the disk cache by
                        using EmptyStandbyList.exe or RAMMap.exe utilities.

                        For system drives perform a WRITE benchmark, restart 
                        the OS and then perform a READ benchmark.

                        Press OK to continue when disk cache has been cleared.
                        """,
                        "Missing Disk Cache Utility",
                        JOptionPane.WARNING_MESSAGE);
            } else if (!App.isAdmin) {
                JOptionPane.showMessageDialog(Gui.mainFrame, 
                        """
                        Run jDiskMark as admin to automatically clear the disk cache.

                        For valid READ benchmarks please clear the disk cache by
                        using EmptyStandbyList.exe or RAMMap.exe utilities.

                        For system drives perform a WRITE benchmark, restart 
                        the OS and then perform a READ benchmark.

                        Press OK to continue when disk cache has been cleared.""",
                        "Clear Disk Cache Now",
                        JOptionPane.PLAIN_MESSAGE);
            }
        } else {
            String messagePrompt = "Unrecognized OS: " + osName + "\n" +
                    """
                    For valid READ benchmarks please clear the disk cache now.
                    
                    Removable drives can be disconnected and reconnected.
                    
                    For system drives perform a WRITE benchmark, restart 
                    the OS and then perform a READ benchmarks benchmark.
                    
                    Press OK to continue when disk cache has been cleared.""";
            JOptionPane.showMessageDialog(Gui.mainFrame, 
                    messagePrompt,
                    "Clear Disk Cache Now",
                    JOptionPane.PLAIN_MESSAGE);
        }
    }
    
    static public void loadBenchmark(Benchmark benchmark) {
        resetBenchmarkData();
        updateLegendAndAxis(benchmark);
        chart.getTitle().setText(benchmark.getDriveInfo());
        ArrayList<Sample> samples = benchmark.samples;
        System.out.println("samples=" + samples.size());
        for (Sample s : samples) {
            System.out.println(s);
            if (benchmark.ioMode == Benchmark.IOMode.READ) {
                addReadSample(s);
            } else {
                addWriteSample(s);
            }
        }
        
        App.numOfBlocks = benchmark.numBlocks;
        App.numOfSamples = benchmark.numSamples;
        App.blockSizeKb = benchmark.blockSize;
        App.blockSequence = benchmark.blockOrder;
        Gui.mainFrame.loadSettings();
        
        if (benchmark.ioMode ==  Benchmark.IOMode.READ) {
            App.readTest = true;
            App.rAvg = benchmark.bwAvg;
            App.rMax = benchmark.bwMax;
            App.rMin = benchmark.bwMin;
            App.rAcc = benchmark.accAvg;
            Gui.mainFrame.refreshReadMetrics();
        } else {
            App.writeTest = true;
            App.wAvg = benchmark.bwAvg;
            App.wMax = benchmark.bwMax;
            App.wMin = benchmark.bwMin;
            App.wAcc = benchmark.accAvg;
            Gui.mainFrame.refreshWriteMetrics();
        }
    }

    /**
     * The original color scheme.
     */
    static void setClassicColorScheme() {
        System.out.println("setting classic palette");
        palette = Palette.CLASSIC;
        
        // configure the bw series colors
        bwRenderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
        bwRenderer.setSeriesPaint(0, Color.YELLOW);     // write
        bwRenderer.setSeriesPaint(1, Color.WHITE);      // w avg
        bwRenderer.setSeriesPaint(2, Color.GREEN);      // w max
        bwRenderer.setSeriesPaint(3, Color.RED);        // w min
        bwRenderer.setSeriesPaint(4, Color.LIGHT_GRAY); // read
        bwRenderer.setSeriesPaint(5, Color.ORANGE);     // r avg
        bwRenderer.setSeriesPaint(6, Color.GREEN.darker()); // r max
        bwRenderer.setSeriesPaint(7, Color.RED.darker());   // r min
        
        // configure the access time ms colors
        msRenderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
        msRenderer.setSeriesPaint(0, Color.CYAN);       // w acc
        msRenderer.setSeriesPaint(1, Color.MAGENTA);    // r acc
    }

    /**
     * Here is my blue green scheme. can be improved.
     */
    static void setBlueGreenScheme() {
        System.out.println("setting blue green palette");
        palette = Palette.BLUE_GREEN;
        
        // configure the bw series colors
        
        // these are bluish
        bwRenderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
        bwRenderer.setSeriesPaint(0, new Color(0x7C9CDC)); // write
        bwRenderer.setSeriesPaint(1, new Color(0x2A5CB0)); // w avg
        bwRenderer.setSeriesPaint(2, new Color(0xBCD2EF)); // w max
        bwRenderer.setSeriesPaint(3, new Color(0xBFD5EA)); // w min
        
        // these are green
        bwRenderer.setSeriesPaint(4, new Color(0xAACC00)); // read
        bwRenderer.setSeriesPaint(5, new Color(0x008080)); // r avg
        bwRenderer.setSeriesPaint(6, new Color(0x6B8E23)); // r max
        bwRenderer.setSeriesPaint(7, new Color(0x228B22)); // r min
        
        // configure the access time ms colors
        msRenderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
        msRenderer.setSeriesPaint(0, new Color(0x7C9CDC)); // w acc
        msRenderer.setSeriesPaint(1, new Color(0xAACC00)); // r acc
    }
    
    /**
     * Cool color scheme proposed by Bard
     */    
    static void setCoolColorScheme() {
        System.out.println("setting cool palette");
        palette = Palette.BARD_COOL;
        
        // configure the bw series colors
        bwRenderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
        bwRenderer.setSeriesPaint(0, new Color(0x54a0ff)); // write
        bwRenderer.setSeriesPaint(1, new Color(0x808080)); // w avg
        bwRenderer.setSeriesPaint(2, new Color(0x4CAF50)); // w max
        bwRenderer.setSeriesPaint(3, new Color(0xFF5722)); // w min
        bwRenderer.setSeriesPaint(4, new Color(0x00BCD4)); // read
        bwRenderer.setSeriesPaint(5, new Color(0x9E9E9E)); // r avg
        bwRenderer.setSeriesPaint(6, new Color(0x66BB6A)); // r max
        bwRenderer.setSeriesPaint(7, new Color(0xF44336)); // r min
        
        // configure the access time ms colors
        msRenderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
        msRenderer.setSeriesPaint(0, new Color(0x54a0ff)); // w acc
        msRenderer.setSeriesPaint(1, new Color(0x00BCD4)); // r acc
    }
    

    static void setWarmColorScheme() {
        System.out.println("setting warm palette");
        palette = Palette.BARD_WARM;
        
        // configure the bw series colors
        bwRenderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
        bwRenderer.setSeriesPaint(0, new Color(0xFFC107)); // write
        bwRenderer.setSeriesPaint(1, new Color(0xEBEBEB)); // w avg
        bwRenderer.setSeriesPaint(2, new Color(0x4CAF50)); // w max
        bwRenderer.setSeriesPaint(3, new Color(0xFF5722)); // w min
        bwRenderer.setSeriesPaint(4, new Color(0xE91E63)); // read
        bwRenderer.setSeriesPaint(5, new Color(0xD3D3D3)); // r avg
        bwRenderer.setSeriesPaint(6, new Color(0x66BB6A)); // r max
        bwRenderer.setSeriesPaint(7, new Color(0xF44336)); // r min
        
        // configure the access time ms colors
        msRenderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
        msRenderer.setSeriesPaint(0, new Color(0xFFC107)); // w acc
        msRenderer.setSeriesPaint(1, new Color(0xE91E63)); // r acc
    }
}
