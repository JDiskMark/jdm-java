package jdiskmark;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import jdiskmark.Benchmark.IOMode;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker.StateValue;
import static javax.swing.SwingWorker.StateValue.DONE;
import static javax.swing.SwingWorker.StateValue.STARTED;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import static jdiskmark.Benchmark.IOMode.READ;
import static jdiskmark.Benchmark.IOMode.WRITE;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.block.BlockBorder;
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
    
    public enum Palette {
        CLASSIC("Classic"),
        BLUE_GREEN("Blue Green"),
        BARD_COOL("Bard Cool"),
        BARD_WARM("Bard Warm"),
        BETA("Beta");

        private final String displayName;

        Palette(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() { return displayName; }

        /** Applies this palette's colour scheme to the chart renderers. */
        public void apply() {
            switch (this) {
                case CLASSIC    -> setClassicColorScheme();
                case BLUE_GREEN -> setBlueGreenScheme();
                case BARD_COOL  -> setCoolColorScheme();
                case BARD_WARM  -> setWarmColorScheme();
                case BETA       -> setBetaColorScheme();
            }
        }
    }
    
    public enum Theme {
        DARK("Dark"),
        LIGHT("Light"),
        DARCULA("Darcula");

        private final String displayName;

        Theme(String displayName) {
            this.displayName = displayName;
        }

        public String getLafClassName() {
            boolean isMac = App.isMacOs();

            return switch (this) {
                case DARK -> isMac ? "com.formdev.flatlaf.themes.FlatMacDarkLaf" 
                                   : "com.formdev.flatlaf.FlatDarkLaf";
                case LIGHT -> isMac ? "com.formdev.flatlaf.themes.FlatMacLightLaf" 
                                    : "com.formdev.flatlaf.FlatLightLaf";
                case DARCULA -> "com.formdev.flatlaf.FlatDarculaLaf";
            };
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
        
    // display settings
    public static Theme theme = Theme.DARK;
    public static Palette palette = Palette.CLASSIC;
    public static boolean showBadges = true;
    public static boolean showMaxMin = true;
    public static boolean showDriveAccess = true;
    public static boolean showSingleOp = false;
    // form components
    public static ChartPanel chartPanel = null;
    public static MainFrame mainFrame = null;
    public static BenchmarkControlPanel controlPanel = null;
    public static SelectDriveFrame selFrame = null;
    public static BenchmarkPanel runPanel = null;
    public static SmartPanel smartPanel = null;
    public static DrivesPanel drivesPanel = null;
    public static SmartReportsPanel smartReportsPanel = null;
    public static javax.swing.JTabbedPane mainTabPane = null;
    public static JProgressBar progressBar = null;
    // chart badge strip — declared null until createChartPanel() wires them up
    public static javax.swing.JLabel directIoLabel = null;
    public static javax.swing.JLabel writeSyncLabel = null;
    public static javax.swing.JLabel sectorLabel = null;
    public static javax.swing.JLabel renderModeLabel = null;
    public static javax.swing.JLabel ioEngineLabel = null;
    public static javax.swing.JLabel multiFileLabel = null;
    /** Priority-ordered list used by the single-row truncation listener. */
    private static List<javax.swing.JLabel> chartBadgeList = null;
    /** The badge strip panel — held so setChartBadgesVisible() can show/hide it. */
    private static javax.swing.JPanel chartBadgeTopPanel = null;
    // lazy-init singleton — created on first access after the LAF is applied
    private static AdvancedOptionsFrame advancedFrame = null;

    /**
     * Returns the Advanced Options dialog, creating it on the first call.
     * The singleton is initialised lazily so the Look-and-Feel is fully applied
     * before any Swing components are constructed.
     * @return reference to the Advanced Options Frame
     */
    public static AdvancedOptionsFrame getAdvancedFrame() {
        if (advancedFrame == null) {
            advancedFrame = new AdvancedOptionsFrame();
        }
        return advancedFrame;
    }

    // last SMART data captured via runSmart() — used by Save Snapshot button
    public static Smart lastSmartData = null;
    public static String lastSmartDeviceName = null;
    /**
     * {@code true} while the SMART tab is displaying a stored {@link SmartSnapshot}
     * rather than freshly fetched live data.  Cleared when {@link #runSmart()}
     * starts a new live query; set by {@link #loadSnapshot(SmartSnapshot)}.
     */
    public static boolean viewingSnapshot = false;
    // graph component
    public static JFreeChart chart;
    public static NumberAxis msAxis, bwAxis, sampleAxis;
    public static XYSeries wSeries, wAvgSeries, wMaxSeries, wMinSeries, wDrvAccess;
    public static XYSeries rSeries, rAvgSeries, rMaxSeries, rMinSeries, rDrvAccess;
    public static XYLineAndShapeRenderer bwRenderer;
    public static XYLineAndShapeRenderer msRenderer;
    static Color foregroundColor;
    
    public static void configureDarkLaf() {
        try {
            if (App.isWindows()) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else if (App.isMacOs()) {
                UIManager.setLookAndFeel(new FlatMacDarkLaf());
            } else if (App.isLinux()) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            }
            // Use FlatLaf custom window decorations (unified title bar + menu bar) on
            // non-macOS only. On macOS the native title bar is kept so the system menu
            // bar at the top of the screen works correctly.
            if (!App.isMacOs()) {
                javax.swing.JFrame.setDefaultLookAndFeelDecorated(true);
                javax.swing.JDialog.setDefaultLookAndFeelDecorated(true);
            }
        } catch (UnsupportedLookAndFeelException e) {
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
    
    public static void configureDarculaLaf() {
        try {
            UIManager.setLookAndFeel(new FlatDarculaLaf());
            if (!App.isMacOs()) {
                javax.swing.JFrame.setDefaultLookAndFeelDecorated(true);
                javax.swing.JDialog.setDefaultLookAndFeelDecorated(true);
            }
        } catch (UnsupportedLookAndFeelException e) {
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
    
    public static void configureLightLaf() {
        try {
            if (App.isWindows()) {
                UIManager.setLookAndFeel(new FlatLightLaf());
            } else if (App.isMacOs()) {
                UIManager.setLookAndFeel(new FlatMacLightLaf());
            } else if (App.isLinux()) {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
            if (!App.isMacOs()) {
                javax.swing.JFrame.setDefaultLookAndFeelDecorated(true);
                javax.swing.JDialog.setDefaultLookAndFeelDecorated(true);
            }
        } catch (UnsupportedLookAndFeelException e) {
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
    
    // switch to dark theme
    public static void goDarkTheme() {
        configureDarkLaf();
        FlatLaf.updateUI();
        updateChartPanelStyle();
    }
    
    // switch to darcula theme
    public static void goDarculaTheme() {
        configureDarculaLaf();
        FlatLaf.updateUI();
        updateChartPanelStyle();
    }
    
    // switch to light theme
    public static void goLightTheme() {
        configureLightLaf();
        FlatLaf.updateUI();
        updateChartPanelStyle();
    }
    
    /**
     * Handles progress and state updates from the SwingWorker 
     * and updates the progress bar accordingly.
     */
    public static class WorkerProgressListener implements PropertyChangeListener {
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            long targetSize = App.targetBenchmarkTxSizeKb();
            
            switch (event.getPropertyName()) {
                case "progress" -> {
                    int value = (Integer) event.getNewValue();
                    long kbProcessed = value * targetSize / 100;
                    String progressText = String.format("%d / %d", kbProcessed, targetSize);
                    progressBar.setValue(value);
                    progressBar.setString(progressText);
                }
                case "state" -> {
                    String targetTxSize = String.valueOf(App.targetBenchmarkTxSizeKb());
                    switch ((StateValue)event.getNewValue()) {
                        case STARTED -> Gui.progressBar.setString("0 / " + targetTxSize);
                        case DONE -> { 
                            progressBar.setString(targetTxSize);
                            progressBar.setValue(100);
                        }
                    } // end inner switch
                }
            }
        }
    }
    
    public static void resetProgressBar() {
        progressBar.setString(String.valueOf(App.targetBenchmarkTxSizeKb()));
        progressBar.setValue(0);
    }
    
    public static void init() {
        switch (Gui.theme) {
            case DARK -> configureDarkLaf();
            case LIGHT -> configureLightLaf();
            case DARCULA -> configureDarculaLaf();
        }
        
        mainFrame = new MainFrame();

        // Embed the menu bar into the FlatLaf custom title bar (VS Code style) on
        // non-macOS only. On macOS the menu bar lives in the native system menu bar
        // at the top of the screen; embedding it here would conflict.
        if (!App.isMacOs()) {
            mainFrame.getRootPane().putClientProperty(
                    com.formdev.flatlaf.FlatClientProperties.MENU_BAR_EMBEDDED, true);
        }

        // Apply branding icon to the window title bar and taskbar.
        // setIconImages supplies all available sizes so Java picks the best
        // fit per display context (16px title bar, 32/48px taskbar, etc.).
        java.util.List<java.awt.Image> icons = App.activeIcon.loadAll();
        if (!icons.isEmpty()) {
            mainFrame.setIconImages(icons);
        }

        if (runPanel != null) {
            runPanel.hideFirstColumn();
        }
        selFrame = new SelectDriveFrame();
        mainFrame.loadPropertiesConfig();
        mainFrame.setLocationRelativeTo(null);
        progressBar = mainFrame.getProgressBar();

        // On macOS, replace the default system-provided About dialog (which shows
        // the Java runtime info) with our own branded dialog.
        if (App.isMacOs() && java.awt.Desktop.isDesktopSupported()) {
            var desktop = java.awt.Desktop.getDesktop();
            if (desktop.isSupported(java.awt.Desktop.Action.APP_ABOUT)) {
                desktop.setAboutHandler(e ->
                        javax.swing.SwingUtilities.invokeLater(Gui::showAboutDialog));
            }
        }
    }

    /**
     * Shows the JDiskMark About dialog.
     * Called from the Help menu on all platforms, and from the macOS
     * system menu bar About handler registered in {@link #init()}.
     */
    public static void showAboutDialog() {
        javax.swing.ImageIcon icon = App.activeIcon.loadSize(128);

        // Build an HTML panel so the website URL is a clickable hyperlink.
        String url = "https://www.jdiskmark.net";
        String html = "<html><body style='font-family:sans-serif;font-size:11px'>"
                + "<b>" + App.APP_NAME + " " + App.VERSION + "</b><br>"
                + "JVM: " + App.jdk + "<br>"
                + "OS:&nbsp; " + App.os + "<br><br>"
                + "<a href='" + url + "'>" + url + "</a>"
                + "</body></html>";

        javax.swing.JEditorPane msgPane = new javax.swing.JEditorPane("text/html", html);
        msgPane.setEditable(false);
        msgPane.setOpaque(false);
        msgPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                } catch (Exception ex) {
                    App.msg("Could not open browser: " + ex.getMessage());
                }
            }
        });

        javax.swing.JOptionPane.showMessageDialog(
                mainFrame, msgPane, "About " + App.APP_NAME,
                javax.swing.JOptionPane.PLAIN_MESSAGE, icon);
    }

    /**
     * #117 Shows the one-time first-run consent dialog for portal sharing.
     * Fires when {@link App#portalConsentAsked} is {@code false}. After the user
     * responds the flag is set to {@code true} and persisted so the dialog
     * never appears again.
     */
    public static void promptFirstRunPortalConsent() {
        String message = "<html><body style='width:380px'>"
                + "<b>Help the community make smarter hardware decisions!</b><br><br>"
                + "Your benchmark data, combined with others', help users compare real-world storage "
                + "performance and identify reliability trends across drives and platforms.<br><br>"
                + "Would you like to share your results with the jdiskmark.net community portal?<br><br>"
                + "<ul>"
                + "<li>Performance metrics (speeds, IOPS, latency) and hardware context (CPU, drive, OS).</li>"
                + "<li>A non-reversible system identifier — no name or account required.</li>"
                + "<li>You can change this at any time via the <i>Sharing</i> tab.</li>"
                + "</ul>"
                + "</body></html>";
        int choice = javax.swing.JOptionPane.showConfirmDialog(
                mainFrame,
                new javax.swing.JLabel(message),
                "Share Benchmark Results?",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE);
        App.portalConsentAsked = true; // mark as answered regardless of choice
        if (choice == javax.swing.JOptionPane.YES_OPTION) {
            App.sharePortal = true;
            App.msg("Portal upload enabled — thank you for sharing!");
        } else {
            App.sharePortal = false;
            App.msg("Portal upload declined. You can enable it later via the Sharing tab.");
        }
        App.saveConfig(); // persist consent flag and choice immediately
        if (mainFrame != null) {
            mainFrame.loadPropertiesConfig();
        }
    }

    /**
     * Offers a one-click prompt to re-enable portal upload when it was active
     * in the previous session. Called after the main window is visible so the
     * dialog has a proper parent.
     */
    public static void promptResumePortalUpload() {
        int choice = javax.swing.JOptionPane.showConfirmDialog(
                mainFrame,
                "Portal upload was enabled in your last session.\nResume uploading benchmarks to "
                        + Portal.getUploadUrl() + "?",
                "Resume Portal Upload?",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE);
        if (choice == javax.swing.JOptionPane.YES_OPTION) {
            App.sharePortal = true;
            App.msg("Portal upload resumed.");
        } else {
            App.sharePortal = false;
            App.sharePortalPreviouslyEnabled = false;
            App.msg("Portal upload not resumed.");
            App.saveConfig();
        }
        if (mainFrame != null) {
            mainFrame.loadPropertiesConfig();
        }
    }

    public static void updateChartPanelStyle() {
        // correct the parenthesis from being below vertical centering
        chart.getTitle().setFont(new Font("Verdana", Font.BOLD, 17));
        
        foregroundColor = UIManager.getColor("Label.foreground");
        if (foregroundColor == null) foregroundColor = Color.LIGHT_GRAY;
        
        chart.getTitle().setPaint(foregroundColor);
        Color bgColor = UIManager.getColor("Panel.background");
        chart.setBackgroundPaint(bgColor);
        
        // Style the Axis (Labels and Tick Marks)
        
        // Bandwidth Axis (Left)
        bwAxis.setLabelPaint(foregroundColor);
        bwAxis.setTickLabelPaint(foregroundColor);
        bwAxis.setTickMarkPaint(foregroundColor);
        // Latency Axis (Right)
        msAxis.setLabelPaint(foregroundColor);
        msAxis.setTickLabelPaint(foregroundColor);
        msAxis.setTickMarkPaint(foregroundColor);
        // Sample Axis (Bottom)
        sampleAxis.setLabelPaint(foregroundColor);
        sampleAxis.setTickLabelPaint(foregroundColor);
        sampleAxis.setTickMarkPaint(foregroundColor);

        // Style the Legend
        if (chart.getLegend() != null) {
            chart.getLegend().setItemPaint(foregroundColor);
            // Set legend background with slight transparency (macos like look)
            Color panelBg = UIManager.getColor("Panel.background");
            if (panelBg != null) {
                Color legendBg = new Color(panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue(), 200);
                chart.getLegend().setBackgroundPaint(legendBg);
            }
            // Remove the border or set it to a subtle gray
            chart.getLegend().setFrame(new BlockBorder(new Color(80, 80, 80)));
        }
    }
    
    public static javax.swing.JPanel createChartPanel() {
        
        wSeries = new XYSeries("Write Sample");
        wAvgSeries = new XYSeries("Write Trend");
        wMaxSeries = new XYSeries("Write Max");
        wMinSeries = new XYSeries("Write Min");
        wDrvAccess = new XYSeries("Write Latency");
        
        rSeries = new XYSeries("Read Sample");
        rAvgSeries = new XYSeries("Read Trend");
        rMaxSeries = new XYSeries("Read Max");
        rMinSeries = new XYSeries("Read Min");
        rDrvAccess = new XYSeries("Read Latency");
        
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
        bwAxis = new NumberAxis("Bandwidth (MB/s)");
        bwAxis.setAutoRangeIncludesZero(false);
        
        // y axis on the right
        msAxis = new NumberAxis("Latency (ms)");
        msAxis.setAutoRange(true);
        msAxis.setAutoRangeIncludesZero(false);
        
        // x axis on the bottom
        sampleAxis = new NumberAxis();
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
        
        updateChartPanelStyle();
        
        ChartPanel rawChartPanel = new ChartPanel(chart) {
            // Only way to set the size of chart panel
            // ref: http://www.jfree.org/phpBB2/viewtopic.php?p=75516
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(500, 325);
            }
        };
        
        rawChartPanel.addChartMouseListener(new ChartMouseListener() {
            private long lastClickTime = 0;
            @Override
            public void chartMouseClicked(ChartMouseEvent event) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime < 500) { // Check for double click
                    /* TODO: implement Detect click on chart title area */
                    //Rectangle2D titleArea = chart.getTitle().getBounds();
                    //boolean isInTitleArea = titleArea.contains(event.getTrigger().getX(), event.getTrigger().getY());
                    //if (isInTitleArea) {
                    // open selection dialog
                        browseLocation();
                    //}
                }
                lastClickTime = currentTime;
            }

            @Override
            public void chartMouseMoved(ChartMouseEvent cme) {
                // no action
            }
        });
        updateLegendAndAxis();

        // chart badge strip (priority order: most → least interpretively important)
        directIoLabel   = makeBadge();
        writeSyncLabel  = makeBadge();
        sectorLabel     = makeBadge();
        ioEngineLabel   = makeBadge();
        multiFileLabel  = makeBadge();
        renderModeLabel = makeBadge();
        refreshChartBadges();

        // ordered list: priority order (most → least interpretively important)
        chartBadgeList = new ArrayList<>(List.of(
                directIoLabel, writeSyncLabel, sectorLabel,
                ioEngineLabel, multiFileLabel, renderModeLabel));

        // topPanel: override getPreferredSize() so the badge strip never drives the container
        // wider than the chart panel below it. Width=0 means BorderLayout NORTH takes the
        // container's width (set by CENTER) rather than expanding to fit all badges.
        javax.swing.JPanel topPanel = new javax.swing.JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2)) {
            @Override
            public java.awt.Dimension getPreferredSize() {
                return new java.awt.Dimension(0, super.getPreferredSize().height);
            }
        };
        topPanel.setOpaque(false);
        for (javax.swing.JLabel badge : chartBadgeList) topPanel.add(badge);
        chartBadgeTopPanel = topPanel;
        topPanel.setVisible(showBadges); // apply persisted setting

        // re-evaluate on every window resize
        topPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                fitBadgesToOneRow(topPanel);
            }
        });

        javax.swing.JPanel chartWrapper = new javax.swing.JPanel(new BorderLayout());
        chartWrapper.setOpaque(false);
        chartWrapper.add(topPanel, BorderLayout.NORTH);
        chartWrapper.add(rawChartPanel, BorderLayout.CENTER);

        chartPanel = (ChartPanel) rawChartPanel;
        return chartWrapper;
    }

    /**
     * Shows or hides the chart badge strip.When hidden, BorderLayout reclaims
     * the NORTH slot and the chart panel expands to fill the full height.
     * @param visible show on UI
     */
    public static void setChartBadgesVisible(boolean visible) {
        if (chartBadgeTopPanel == null) return;
        chartBadgeTopPanel.setVisible(visible);
        // revalidate the parent so BorderLayout recalculates slot sizes
        java.awt.Container parent = chartBadgeTopPanel.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    /**
     * Shows badges left-to-right until the next one would overflow the panel width,
     * then hides all remaining. Uses Toolkit FontMetrics so badge widths are correct
     * even before the components have been painted for the first time.
     */
    private static void fitBadgesToOneRow(javax.swing.JPanel topPanel) {
        if (chartBadgeList == null) return;
        FlowLayout fl = (FlowLayout) topPanel.getLayout();
        int hgap = fl.getHgap();
        java.awt.Insets ins = topPanel.getInsets();
        // Subtract panel insets AND FlowLayout's own leading margin (one hgap on the left)
        int available = topPanel.getWidth() - ins.left - ins.right - hgap;
        if (available <= 0) return; // not yet laid out — skip until a real width arrives
        int used = 0;
        boolean overflowed = false;
        for (javax.swing.JLabel badge : chartBadgeList) {
            if (overflowed) {
                badge.setVisible(false);
                continue;
            }
            // Measure text width without relying on a rendered peer.
            // TextLayout uses the font's own metrics via a scratch FontRenderContext.
            java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, false, false);
            int textWidth = (badge.getText().isEmpty()) ? 0
                    : (int) Math.ceil(new java.awt.font.TextLayout(badge.getText(), badge.getFont(), frc).getAdvance());
            java.awt.Insets bi = badge.getInsets();
            int badgeWidth = textWidth + bi.left + bi.right;
            int needed = badgeWidth + (used > 0 ? hgap : 0);
            if (used + needed <= available) {
                badge.setVisible(true);
                used += needed;
            } else {
                badge.setVisible(false);
                overflowed = true;
            }
        }
    }

    /** Creates a badge label with shared styling. */
    private static javax.swing.JLabel makeBadge() {
        javax.swing.JLabel lbl = new javax.swing.JLabel();
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        lbl.setForeground(new Color(200, 200, 200));
        lbl.setOpaque(true);
        lbl.setBackground(new Color(40, 40, 40, 180));
        lbl.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return lbl;
    }

    /**
     * Refreshes all chart badge labels to reflect the current App settings.
     * Safe to call from any thread — posts to the EDT if needed.
     */
    public static void refreshChartBadges() {
        if (directIoLabel == null) return;
        Runnable update = () -> {
            directIoLabel.setText("Direct IO: "   + (App.directEnable    ? "On" : "Off"));
            writeSyncLabel.setText("Write Sync: " + (App.writeSyncEnable  ? "On" : "Off"));
            sectorLabel.setText("Sector: "        + App.sectorAlignment.display.split(" \\(")[0]);
            ioEngineLabel.setText("Engine: "      + App.ioEngine.toString().split(" ")[0]);
            multiFileLabel.setText("Multi-File: " + (App.multiFile ? "On" : "Off"));
            renderModeLabel.setText("Render: "     + App.rmOption.toString());
        };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            javax.swing.SwingUtilities.invokeLater(update);
        }
    }

    /**
     * Refreshes badge labels from nullable stored values (e.g. a loaded benchmark).
     * Any null value is rendered as "—" to indicate the data was not recorded.
     * Safe to call from any thread.
     */
    public static void refreshChartBadges(Boolean directIo, Boolean writeSync,
                                          App.SectorAlignment sector, RenderFrequencyMode renderMode,
                                          App.IoEngine ioEngine, Boolean multiFile) {
        if (directIoLabel == null) return;
        Runnable update = () -> {
            directIoLabel.setText("Direct IO: "   + (directIo  != null ? (directIo  ? "On" : "Off") : "—"));
            writeSyncLabel.setText("Write Sync: " + (writeSync != null ? (writeSync ? "On" : "Off") : "—"));
            String sectorText = sector != null ? sector.display.split(" \\(")[0] : "—";
            sectorLabel.setText("Sector: " + sectorText);
            String engineText = ioEngine != null ? ioEngine.toString().split(" ")[0] : "—";
            ioEngineLabel.setText("Engine: " + engineText);
            multiFileLabel.setText("Multi-File: " + (multiFile != null ? (multiFile ? "On" : "Off") : "—"));
            renderModeLabel.setText("Render: " + (renderMode != null ? renderMode.toString() : "—"));
        };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            javax.swing.SwingUtilities.invokeLater(update);
        }
    }
    
    public static BenchmarkControlPanel createControlPanel() {
        controlPanel = new BenchmarkControlPanel();
        return controlPanel;
    }
    
    public static void addWriteSample(Sample s) {
        wSeries.add(s.sampleNum, s.bwMbSec);
        wAvgSeries.add(s.sampleNum, s.cumAvg);
        if (showMaxMin) {
            wMaxSeries.add(s.sampleNum, s.cumMax);
            wMinSeries.add(s.sampleNum, s.cumMin);
        }
        if (showDriveAccess) {
            wDrvAccess.add(s.sampleNum, s.accessTimeMs);
        }
        controlPanel.refreshWriteMetrics();
    }
    
    public static void addReadSample(Sample s) {
        rSeries.add(s.sampleNum, s.bwMbSec);
        rAvgSeries.add(s.sampleNum, s.cumAvg);
        if (showMaxMin) {
            rMaxSeries.add(s.sampleNum, s.cumMax);
            rMinSeries.add(s.sampleNum, s.cumMin);
        }
        if (showDriveAccess) {
            rDrvAccess.add(s.sampleNum, s.accessTimeMs);
        }
        controlPanel.refreshReadMetrics();
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
        controlPanel.refreshReadMetrics();
        controlPanel.refreshWriteMetrics();
    }
    
    public static void updateLegendAndAxis() {
        bwRenderer.setSeriesVisibleInLegend(0, App.hasWriteOperation());
        bwRenderer.setSeriesVisibleInLegend(1, App.hasWriteOperation());
        bwRenderer.setSeriesVisibleInLegend(2, App.hasWriteOperation() && showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(3, App.hasWriteOperation() && showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(4, App.hasReadOperation());
        bwRenderer.setSeriesVisibleInLegend(5, App.hasReadOperation());
        bwRenderer.setSeriesVisibleInLegend(6, App.hasReadOperation() && showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(7, App.hasReadOperation() && showMaxMin);

        msRenderer.setSeriesVisibleInLegend(0, App.hasWriteOperation() && showDriveAccess);
        msRenderer.setSeriesVisibleInLegend(1, App.hasReadOperation() && showDriveAccess);
        
        msAxis.setVisible(showDriveAccess);
    }

    // update for a specific benchmark
    public static void updateLegendAndAxis(Benchmark b) {
        boolean hasWrite = b.config.hasWriteOperation();
        boolean hasRead = b.config.hasReadOperation();
        bwRenderer.setSeriesVisibleInLegend(0, hasWrite);
        bwRenderer.setSeriesVisibleInLegend(1, hasWrite);
        bwRenderer.setSeriesVisibleInLegend(2, hasWrite && showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(3, hasWrite && showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(4, hasRead);
        bwRenderer.setSeriesVisibleInLegend(5, hasRead);
        bwRenderer.setSeriesVisibleInLegend(6, hasRead && showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(7, hasRead && showMaxMin);
        
        msRenderer.setSeriesVisibleInLegend(0, hasWrite && showDriveAccess);
        msRenderer.setSeriesVisibleInLegend(1, hasRead && showDriveAccess);
        
        msAxis.setVisible(showDriveAccess);
    }
    
    // update for a specific operation
    public static void updateLegendAndAxis(BenchmarkOperation o) {
        boolean isWriteTest = o.ioMode == IOMode.WRITE;
        boolean isReadTest = o.ioMode == IOMode.READ;
        bwRenderer.setSeriesVisibleInLegend(0, isWriteTest);
        bwRenderer.setSeriesVisibleInLegend(1, isWriteTest);
        bwRenderer.setSeriesVisibleInLegend(2, isWriteTest && showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(3, isWriteTest && showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(4, isReadTest);
        bwRenderer.setSeriesVisibleInLegend(5, isReadTest);
        bwRenderer.setSeriesVisibleInLegend(6, isReadTest && showMaxMin);
        bwRenderer.setSeriesVisibleInLegend(7, isReadTest && showMaxMin);
        
        msRenderer.setSeriesVisibleInLegend(0, isWriteTest && showDriveAccess);
        msRenderer.setSeriesVisibleInLegend(1, isReadTest && showDriveAccess);
        
        msAxis.setVisible(showDriveAccess);
    }
    
    private static final Logger SMART_LOG = Logger.getLogger(Gui.class.getName());

    static public void updateDiskInfo() {
        mainFrame.setLocation(App.locationDir.getAbsolutePath());
        chart.getTitle().setText(App.getDriveInfo());
        if (drivesPanel != null) {
            drivesPanel.refresh();
        }
        // SMART data is fetched lazily via runSmart(), which is called
        // by the "Run SMART" button in SmartPanel and optionally after each
        // benchmark when "Run SMART with Benchmark" is enabled.
    }

    /**
     * Selects the first tab in {@link #mainTabPane} whose title equals
     * {@code tabTitle}.  No-op if the pane is null or no matching tab exists.
     *
     * @param tabTitle the exact tab label to select, e.g. {@code "Benchmark"}
     */
    public static void selectMainTab(String tabTitle) {
        if (mainTabPane == null) return;
        for (int i = 0; i < mainTabPane.getTabCount(); i++) {
            if (tabTitle.equals(mainTabPane.getTitleAt(i))) {
                mainTabPane.setSelectedIndex(i);
                return;
            }
        }
    }

    /**
     * Fetches fresh SMART data for the current drive in a background thread
     * and populates the SMART panel when done. Triggers the pkexec password
     * prompt on the very first call (or after the privileged shell dies).
     *
     * <p>Safe to call from the EDT; the privileged I/O runs off-thread.
     * Called by the "Run SMART" button in {@link SmartPanel} and by
     * {@link jdiskmark.BenchmarkRunner} when "Run SMART with Benchmark" is enabled.
     */
    static public void runSmart() {
        
        if (!App.isLinux()) { 
            App.msg("SMART is only available in linux");
            return;
        }
        
        if (smartPanel == null || App.locationDir == null) {
            App.msg("smartPanel and locationDir must first be initialized");
            return;
        }
        // A live query is starting — we are no longer viewing a stored snapshot.
        viewingSnapshot = false;
        final File locDir = App.locationDir;
        // Single-element array so doInBackground() can share the device name
        // with done() without a field (anonymous SwingWorker limitation).
        final String[] deviceRef = {null};
        new javax.swing.SwingWorker<Smart, Void>() {
            @Override
            protected Smart doInBackground() {
                try {
                    Path path = locDir.toPath();
                    String partition = UtilOs.getPartitionFromFilePathLinux(path);
                    List<String> devices =
                            UtilOs.getDeviceNamesFromPartitionLinux(partition);
                    if (devices == null || devices.isEmpty()) {
                        SMART_LOG.log(Level.WARNING, "runSmart: no device for {0}", locDir);
                        return null;
                    }
                    deviceRef[0] = devices.get(0);
                    if (Smart.process == null || !Smart.process.isAlive()) {
                        Smart.startPrivilegedShell();
                        Smart.startHeartbeat();
                    }
                    return Smart.getSmart(deviceRef[0]);
                } catch (IOException ex) {
                    SMART_LOG.log(Level.WARNING, "runSmart: SMART fetch failed", ex);
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    Smart data = get();
                    String device = deviceRef[0];
                    if (data != null) {
                        // Store for the Save Snapshot button
                        lastSmartData = data;
                        lastSmartDeviceName = device;
                        smartPanel.populate(data);
                        smartPanel.onDataLoaded(device != null ? device : "unknown");
                    } else {
                        lastSmartData = null;
                        lastSmartDeviceName = null;
                        smartPanel.clear();
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    SMART_LOG.log(Level.WARNING, "runSmart: panel update failed", ex);
                }
            }
        }.execute();
    }

    /**
     * Loads a stored {@link SmartSnapshot} into the SMART tab and switches
     * focus to it.  Sets {@link #viewingSnapshot} to {@code true}.
     *
     * <p>If the snapshot contains a {@code rawJson} CLOB (saved with the
     * current schema), the full {@link Smart} object is re-parsed so that
     * every section of the SMART tab (ATA attributes, NVMe device details,
     * endurance, etc.) is replayed exactly as it appeared live.  For older
     * snapshots without {@code rawJson}, the scalar-only fallback path is
     * used instead.
     *
     * <p>Always disables the Save button and stamps the status label with the
     * snapshot's capture timestamp via {@link SmartPanel#onSnapshotLoaded}.
     *
     * @param snap the snapshot to display (must not be null)
     */
    static public void loadSnapshot(SmartSnapshot snap) {
        if (snap == null || smartPanel == null) return;
        viewingSnapshot = true;

        String rawJson = snap.getRawJson();
        if (rawJson != null) {
            // Full replay — re-parse the original smartctl JSON
            try {
                Smart smart = Smart.fromJson(rawJson);
                smartPanel.populate(smart);
            } catch (Exception ex) {
                SMART_LOG.log(Level.WARNING,
                        "loadSnapshot: rawJson parse failed, falling back to scalars", ex);
                smartPanel.populateFromSnapshot(snap);
            }
        } else {
            // Legacy snapshot — scalar fields only
            smartPanel.populateFromSnapshot(snap);
        }

        // Always override toolbar state: read-only view, show capture time
        smartPanel.onSnapshotLoaded(snap);

        // Switch focus to the SMART tab
        if (mainTabPane != null) {
            for (int i = 0; i < mainTabPane.getTabCount(); i++) {
                if ("SMART".equals(mainTabPane.getTitleAt(i))) {
                    mainTabPane.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    /**
     * Saves the most recently fetched SMART data as a {@link SmartSnapshot}
     * in the Derby database. Called by the "Save Snapshot" button in
     * {@link SmartPanel}. No-op if no data has been loaded yet.
     */
    static public void saveCurrentSmartData() {
        if (lastSmartData == null || lastSmartDeviceName == null) {
            App.msg("No SMART data to save — run a SMART session first.");
            return;
        }
        try {
            SmartSnapshot.save(lastSmartData, lastSmartDeviceName);
            if (smartPanel != null) smartPanel.onDataSaved();
            if (smartReportsPanel != null) smartReportsPanel.refresh();
            App.msg("SMART snapshot saved for /dev/" + lastSmartDeviceName + ".");
        } catch (Exception ex) {
            SMART_LOG.log(Level.WARNING, "saveCurrentSmartData: failed", ex);
            App.err("Failed to save SMART snapshot: " + ex.getMessage());
        }
    }
    
    /**
     * GH-2 need solution for dropping catch
     */
    static public void dropCache() {
        if (App.isLinux()) {
            if (App.isRoot) {
                // GH-2 automate catch dropping
                UtilOs.flushDataToDriveLinux();
                UtilOs.dropWriteCacheLinux();
            } else {
                /* Revised the drop_caches command so it works. - JSL 2024-01-16 */
                String message = """
                        Run JDiskMark with sudo to automatically clear the disk cache.
                        
                        For a valid READ benchmark please clear the disk cache now 
                        by using: \"sudo sh -c \'sync; echo 1 > /proc/sys/vm/drop_caches\'\".
                        
                        Press OK to continue when disk cache has been dropped.""";
                JOptionPane.showMessageDialog(mainFrame, 
                        message, "Clear Disk Cache Now",
                        JOptionPane.PLAIN_MESSAGE);
            }
        } else if (App.isMacOs()) {
            if (App.isRoot) {
                // GH-2 automate catch dropping
                UtilOs.flushDataToDriveMacOs();
                UtilOs.dropWriteCacheMacOs();
            } else {
                String message = """
                        For valid READ benchmarks please clear the disk cache.

                        Removable drives can be disconnected and reconnected.

                        sudo purge

                        Press OK to continue when disk cache has been cleared.""";
                JOptionPane.showMessageDialog(mainFrame, 
                        message, "Clear Disk Cache Now",
                        JOptionPane.PLAIN_MESSAGE);
            }
        } else if (App.isWindows()) {
            File emptyStandbyListExe = new File(".\\" + App.ESBL_EXE);
            if (!emptyStandbyListExe.exists()) {
                // jpackage windows relative environment
                emptyStandbyListExe = new File(".\\app\\" + App.ESBL_EXE);
            }
            if (App.verbose) {
                System.out.println("emptyStandbyListExe.exist=" + emptyStandbyListExe.exists());
            }
            if (App.isAdmin && emptyStandbyListExe.exists()) {
                // GH-2 drop cahe, delays in place of flushing cache
                try { Thread.sleep(1300); } catch (InterruptedException ex) {}
                UtilOs.emptyStandbyListWindows(emptyStandbyListExe);
                try { Thread.sleep(700); } catch (InterruptedException ex) {}
            } else  if (App.isAdmin && !emptyStandbyListExe.exists()) {
                String message = """
                        Unable to find EmptyStandbyList.exe. This must be
                        present in the install directory for the disk cache
                        to be automatically cleared.
                        
                        For valid READ benchmarks please clear the disk cache by
                        using EmptyStandbyList.exe or RAMMap.exe utilities.

                        Press OK to continue when disk cache has been cleared.
                        """;
                JOptionPane.showMessageDialog(mainFrame, 
                        message, "Missing Disk Cache Utility",
                        JOptionPane.WARNING_MESSAGE);
                
            } else if (!App.isAdmin) {
                String message = """
                        Run JDiskMark as admin to automatically clear the disk cache.

                        For valid READ benchmarks please clear the disk cache by
                        using EmptyStandbyList.exe or RAMMap.exe utilities.

                        Press OK to continue when disk cache has been cleared.""";
                JOptionPane.showMessageDialog(mainFrame, 
                        message, "Clear Disk Cache Now",
                        JOptionPane.PLAIN_MESSAGE);
            }
        } else {
            String message = "Unrecognized OS: " + App.osName() + "\n" +
                    """
                    For valid READ benchmarks please clear the disk cache now.

                    Removable drives can be disconnected and reconnected.

                    Press OK to continue when disk cache has been cleared.""";
            JOptionPane.showMessageDialog(mainFrame,
                    message, "Clear Disk Cache Now",
                    JOptionPane.PLAIN_MESSAGE);
        }
    }
    
    static public void loadBenchmark(Benchmark benchmark) {
        resetBenchmarkData();
        chart.getTitle().setText(benchmark.getDriveInfoDisplay());

        updateLegendAndAxis(benchmark);
        
        // benchmark data
        App.benchmarkType = benchmark.config.benchmarkType;
        App.numOfBlocks = benchmark.config.numBlocks;
        App.numOfSamples = benchmark.config.numSamples;
        App.blockSizeKb = (int)(benchmark.config.blockSize / App.KILOBYTE);
        App.blockSequence = benchmark.config.blockOrder;
        App.numOfThreads = benchmark.config.numThreads;
        App.activeProfile = benchmark.config.profile;
        App.profileModified = benchmark.config.profileModified;
        mainFrame.refreshConfig();
        // override badges with the values recorded at run time (— if absent in older records)
        refreshChartBadges(benchmark.config.directIoEnabled, benchmark.config.writeSyncEnabled,
                           benchmark.config.sectorAlignment, benchmark.getRenderMode(),
                           benchmark.config.ioEngine, benchmark.config.multiFileEnabled);
        
        // operation data
        for (BenchmarkOperation operation : benchmark.operations) {
            
            ArrayList<Sample> samples = operation.getSamples();
            for (Sample s : samples) {
                switch (operation.ioMode) {
                    case READ -> addReadSample(s);
                    case WRITE -> addWriteSample(s);
                }
            }
            switch (operation.ioMode) {
                case READ -> {
                    App.rAvg = operation.bwAvg;
                    App.rMax = operation.bwMax;
                    App.rMin = operation.bwMin;
                    App.rAcc = operation.accAvg;
                    App.rIops = operation.iops;
                    controlPanel.refreshReadMetrics();                
                }
                case WRITE -> {
                    App.wAvg = operation.bwAvg;
                    App.wMax = operation.bwMax;
                    App.wMin = operation.bwMin;
                    App.wAcc = operation.accAvg;
                    App.wIops = operation.iops;
                    controlPanel.refreshWriteMetrics();
                }
            }
        }
        resetProgressBar();
    }
    
    static public void loadOperation(BenchmarkOperation operation) {        
        Benchmark benchmark = operation.getBenchmark();
        resetBenchmarkData();
        updateLegendAndAxis(operation);
        chart.getTitle().setText(benchmark.getDriveInfoDisplay());
        ArrayList<Sample> samples = operation.getSamples();
        for (Sample s : samples) {
            switch (operation.ioMode) {
                case READ -> addReadSample(s);
                case WRITE -> addWriteSample(s);
            }
        }
        App.benchmarkType = benchmark.config.benchmarkType;
        App.numOfBlocks = operation.numBlocks;
        App.numOfSamples = operation.numSamples;
        App.blockSizeKb = (int)(operation.blockSize / App.KILOBYTE);
        App.blockSequence = operation.blockOrder;
        App.numOfThreads = operation.numThreads;
        mainFrame.refreshConfig();
        // override badges with the values recorded at run time (— if absent in older records)
        refreshChartBadges(benchmark.config.directIoEnabled, benchmark.config.writeSyncEnabled,
                           benchmark.config.sectorAlignment, benchmark.getRenderMode(),
                           benchmark.config.ioEngine, benchmark.config.multiFileEnabled);
        switch (operation.ioMode) {
            case READ -> {
                App.rAvg = operation.bwAvg;
                App.rMax = operation.bwMax;
                App.rMin = operation.bwMin;
                App.rAcc = operation.accAvg;
                App.rIops = operation.iops;
                controlPanel.refreshReadMetrics();                
            }
            case WRITE -> {
                App.wAvg = operation.bwAvg;
                App.wMax = operation.bwMax;
                App.wMin = operation.bwMin;
                App.wAcc = operation.accAvg;
                App.wIops = operation.iops;
                controlPanel.refreshWriteMetrics();
            }
        }
        resetProgressBar();
    }

    /**
     * The original color scheme.
     */
    static void setClassicColorScheme() {
        palette = Palette.CLASSIC;
        restoreDefaultPlotBackground();
        
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
        restoreDefaultPlotBackground();
        
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
        restoreDefaultPlotBackground();
        
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
        restoreDefaultPlotBackground();
        
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

    /**
     * Beta palette — matches the Python/matplotlib dark-background look.
     * Dark plot area (#1c1c1c), orange write series, cyan read series.
     */
    static void setBetaColorScheme() {
        System.out.println("setting beta palette");
        palette = Palette.BETA;

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(new Color(0x1C1C1C));
        plot.setOutlinePaint(new Color(0x555555));
        plot.setDomainGridlinePaint(new Color(0x3A3A3A));
        plot.setRangeGridlinePaint(new Color(0x3A3A3A));

        // JFreeChart 1.0.x resets the BasicStroke dash phase per segment (each segment is a
        // separate Line2D draw call). At high sample density (~2.5 px/segment when 200 samples
        // fill ~500 px) a long "8 on / 4 off" dash appears solid because the segment ends before
        // the first gap. A short "on" phase (2 px) shorter than the segment length forces a
        // visible break at the tail of every segment, producing a dotted appearance at any density.
        Stroke avgDot = new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10.0f, new float[]{2.0f, 6.0f}, 0.0f);

        // configure the bw series colors
        bwRenderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
        bwRenderer.setSeriesPaint(0, new Color(0xE07B39));            // write BW
        bwRenderer.setSeriesPaint(1, new Color(189, 176, 138, 200)); // w avg — alpha-softened, dotted
        bwRenderer.setSeriesStroke(1, avgDot);
        bwRenderer.setSeriesPaint(2, new Color(0xF5A623));            // w max
        bwRenderer.setSeriesPaint(3, new Color(0xC0623A));            // w min
        bwRenderer.setSeriesPaint(4, new Color(0x4FC3F7));            // read BW
        bwRenderer.setSeriesPaint(5, new Color(160, 216, 239, 200)); // r avg — alpha-softened, dotted
        bwRenderer.setSeriesStroke(5, avgDot);
        bwRenderer.setSeriesPaint(6, new Color(0x81D4FA));            // r max
        bwRenderer.setSeriesPaint(7, new Color(0x0288D1));            // r min

        // configure the access time ms colors
        msRenderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator());
        msRenderer.setSeriesPaint(0, new Color(0xE07B39)); // w acc
        msRenderer.setSeriesPaint(1, new Color(0x4FC3F7)); // r acc
    }

    /**
     * Restores plot background to the default LAF-driven style.
     * Called when switching away from the Beta palette.
     */
    static void restoreDefaultPlotBackground() {
        if (chart == null) return;
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.DARK_GRAY.darker());
        plot.setOutlinePaint(Color.WHITE);
        // JFreeChart 1.x does not accept null paint — restore to a neutral grid color
        plot.setDomainGridlinePaint(new Color(80, 80, 80));
        plot.setRangeGridlinePaint(new Color(80, 80, 80));
        // clear any Beta-specific per-series dashed strokes on the avg lines
        if (bwRenderer != null) {
            bwRenderer.setSeriesStroke(1, null); // w avg — back to renderer default (solid)
            bwRenderer.setSeriesStroke(5, null); // r avg — back to renderer default (solid)
        }
    }
    
    public static void browseLocation() {
        selFrame = new SelectDriveFrame();
        if (App.locationDir != null && App.locationDir.exists()) {
            selFrame.setInitDir(App.locationDir);
        }
        selFrame.setLocationRelativeTo(mainFrame);
        selFrame.setVisible(true);
    }
    
    // reload the graph
    public static void singleOpTrigReloadGraph() {
        if (Gui.showSingleOp && App.operation != null) {
            Gui.loadOperation(App.operation);
        } else {
            if (App.benchmark != null) {
                Gui.loadBenchmark(App.benchmark);
            }
        }
    }
}