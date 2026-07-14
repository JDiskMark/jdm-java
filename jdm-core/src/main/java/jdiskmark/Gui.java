package jdiskmark;

import com.formdev.flatlaf.FlatLaf;

import jdiskmark.Benchmark.IOMode;

import org.metricus.jdm.ui.AppIcon;
import org.metricus.jdm.ui.ButtonStyles;
import org.metricus.jdm.ui.Palette;
import org.metricus.jdm.ui.Theme;
import org.metricus.jdm.ui.ThemeDefinition;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
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
import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.LegendItemSource;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.ui.HorizontalAlignment;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;

/**
 * Store GUI references for easy access
 */
public final class Gui {
    
    // display settings
    public static Theme theme = Theme.DARK;
    public static Palette palette = Palette.BETA;
    public static boolean showBadges = true;
    public static boolean showMaxMin = false;
    public static boolean showDriveAccess = true;
    public static boolean showSingleOp = false;
    // form components
    public static ChartPanel chartPanel = null;
    public static MainFrame mainFrame = null;
    public static BenchmarkControlPanel controlPanel = null;
    public static SelectDriveFrame selFrame = null;
    public static BenchmarkPanel runPanel = null;
    public static SmartPanel smartPanel = null;
    public static DrivePanel drivePanel = null;
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

    // --- Stale-badge tracking ---
    /** Amber used when a badge value differs from the last-run config. */
    static Color BADGE_STALE_BG   = new Color(0xC8, 0x78, 0x00); // deep amber
    static Color BADGE_DEFAULT_BG = new Color(40, 40, 40, 180);
    static Color BADGE_DEFAULT_FG = new Color(200, 200, 200);
    /** Foreground to use when a badge is stale (set together with BADGE_STALE_BG). */
    static Color BADGE_STALE_FG   = Color.WHITE;

    /** Chart subtitle shown when current settings diverge from the displayed benchmark. */
    private static TextTitle modifiedSubtitle = null;

    /**
     * Shows or hides the "⚠ Settings modified" subtitle on the chart. Safe to 
     * call from any thread.
     * @param visible
     */
    public static void setChartModifiedIndicator(boolean visible) {
        if (chart == null) return;
        Runnable r = () -> {
            if (visible) {
                if (modifiedSubtitle == null) {
                    modifiedSubtitle = new TextTitle(
                            "⚠  Settings modified — results shown reflect prior configuration",
                            new Font("SansSerif", Font.BOLD, 11));
                    modifiedSubtitle.setPosition(RectangleEdge.BOTTOM);
                    modifiedSubtitle.setHorizontalAlignment(HorizontalAlignment.CENTER);
                    modifiedSubtitle.setPadding(new RectangleInsets(0, 0, 4, 0));
                }
                // Refresh paint from themed accent — may have changed since creation
                modifiedSubtitle.setPaint(BADGE_STALE_BG);
                // Only add if not already present
                if (!chart.getSubtitles().contains(modifiedSubtitle)) {
                    chart.addSubtitle(modifiedSubtitle);
                }
            } else {
                if (modifiedSubtitle != null) {
                    chart.removeSubtitle(modifiedSubtitle);
                }
            }
        };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) r.run();
        else javax.swing.SwingUtilities.invokeLater(r);
    }
    
    public static void updateProgress() {
        if (progressBar == null) return;
        progressBar.setString(String.valueOf(App.targetBenchmarkTxSizeKb()));
    }

    /**
     * Clears all badge and control-panel highlights at the start of a new run or load.
     * The comparison baseline is App.benchmark.config (set at end of prior run / on load).
     */
    public static void clearAllStaleHighlights() {
        clearBadgeHighlights();
        setChartModifiedIndicator(false);
        if (controlPanel != null) controlPanel.clearRowHighlights();
    }

    /** Resets all badge backgrounds and foregrounds to the default style. */
    public static void clearBadgeHighlights() {
        if (chartBadgeList == null) return;
        ThemeDefinition def = theme.definition();
        for (int i = 0; i < chartBadgeList.size(); i++) {
            javax.swing.JLabel b = chartBadgeList.get(i);
            b.setBackground(BADGE_DEFAULT_BG);
            if (def.cycleBadgeColors()) {
                b.setForeground(i % 2 == 0 ? def.badgeEvenFg() : def.badgeOddFg());
            } else {
                b.setForeground(BADGE_DEFAULT_FG);
            }
        }
    }

    /** Updates badge colors to match the current window theme. */
    static void updateBadgeThemeColors() {
        ThemeDefinition def = theme.definition();
        BADGE_DEFAULT_BG = def.badgeDefaultBg();
        BADGE_DEFAULT_FG = def.badgeDefaultFg();
        BADGE_STALE_BG   = def.badgeStaleBg();
        BADGE_STALE_FG   = def.badgeStaleFg();

        if (chartBadgeList != null) {
            Color borderColor = def.badgeBorderColor();
            javax.swing.border.Border badge_border = (borderColor != null)
                    ? javax.swing.BorderFactory.createCompoundBorder(
                            javax.swing.BorderFactory.createLineBorder(borderColor, 1),
                            javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5))
                    : javax.swing.BorderFactory.createEmptyBorder(2, 6, 2, 6);

            for (int i = 0; i < chartBadgeList.size(); i++) {
                javax.swing.JLabel b = chartBadgeList.get(i);
                b.setBackground(BADGE_DEFAULT_BG);
                if (def.cycleBadgeColors()) {
                    b.setForeground(i % 2 == 0 ? def.badgeEvenFg() : def.badgeOddFg());
                } else {
                    b.setForeground(BADGE_DEFAULT_FG);
                }
                b.setBorder(badge_border);
            }
        }
        applyBadgeHighlights();
        if (controlPanel != null) controlPanel.showSettingsDrift();
        if (modifiedSubtitle != null && chart != null
                && chart.getSubtitles().contains(modifiedSubtitle)) {
            modifiedSubtitle.setPaint(BADGE_STALE_BG);
        }
    }
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
    public static LegendTitle writeLegend;
    public static LegendTitle readLegend;
    public static LegendTitle combinedLegend;
    static Color foregroundColor;

    /**
     * Removes UIManager overrides set by custom theme LAF configurations.
     * Called before each LAF installation so the new LAF's own defaults take over.
     */
    private static void clearThemeOverrides() {
        FlatLaf.setGlobalExtraDefaults(null);
        UIManager.put("Table.selectionBackground",  null);
        UIManager.put("Table.selectionForeground",  null);
        UIManager.put("List.selectionBackground",   null);
        UIManager.put("List.selectionForeground",   null);
        UIManager.put("Tree.selectionBackground",   null);
        UIManager.put("Tree.selectionForeground",   null);
        UIManager.put("TabbedPane.selectedBackground",     null);
        UIManager.put("TabbedPane.selectedForeground",     null);
        UIManager.put("TabbedPane.underlineColor",         null);
        UIManager.put("TabbedPane.inactiveUnderlineColor", null);
        UIManager.put("TabbedPane.focusColor",             null);
        UIManager.put("TabbedPane.hoverColor",             null);
        UIManager.put("ScrollBar.thumb",            null);
        UIManager.put("ScrollBar.thumbHover",       null);
        UIManager.put("ScrollBar.thumbPressed",     null);
        UIManager.put("TitlePane.foreground",       null);
    }

    /**
     * Forces every open window to re-read the current UIManager defaults.
     * <p>
     * {@code FlatLaf.updateUI()} alone does not always propagate themed
     * overrides (e.g.&nbsp;{@code Table.selectionBackground}) to components
     * whose colors were explicitly set by a previous theme's
     * {@code updateUI()} pass.  Walking all {@link java.awt.Window}s with
     * {@code updateComponentTreeUI()} ensures every component picks up
     * the new UIManager values.
     * </p>
     */
    private static void refreshAllWindows() {
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            javax.swing.SwingUtilities.updateComponentTreeUI(w);
        }
    }

    /**
     * Installs the FlatLaf look-and-feel described by the given definition.
     * Called at startup and by {@link #applyTheme(Theme)} on theme switch.
     */
    public static void configureLaf(ThemeDefinition def) {
        try {
            clearThemeOverrides();
            java.util.Map<String, String> extras = def.flatLafExtras();
            FlatLaf.setGlobalExtraDefaults(extras);
            UIManager.setLookAndFeel(def.lafClassName());
            java.util.Map<String, Object> overrides = def.uiManagerOverrides();
            if (overrides != null) {
                overrides.forEach(UIManager::put);
            }
            if (!App.isMacOs()) {
                javax.swing.JFrame.setDefaultLookAndFeelDecorated(true);
                javax.swing.JDialog.setDefaultLookAndFeelDecorated(true);
            }
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException | UnsupportedLookAndFeelException e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException
                    | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
                java.util.logging.Logger.getLogger(MainFrame.class.getName())
                        .log(java.util.logging.Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Switches to a theme at runtime: installs LAF, refreshes all windows,
     * updates chart style, title bar, progress bar, and linked chart palette.
     */
    public static void applyTheme(Theme t) {
        ThemeDefinition def = t.definition();
        configureLaf(def);
        FlatLaf.updateUI();
        refreshAllWindows();
        updateChartPanelStyle();
        if (mainFrame != null) {
            mainFrame.getRootPane().putClientProperty(
                "JRootPane.titleBarForeground", def.titleBarForeground());
        }
        Color pbFg = def.progressBarForeground();
        if (progressBar != null) {
            progressBar.setForeground(pbFg != null
                    ? pbFg
                    : UIManager.getColor("ProgressBar.foreground"));
        }
        if (t.hasLinkedPalette() && chart != null) {
            t.applyLinkedPalette();
        } else if (chart != null) {
            palette.apply();
        }
        if (mainFrame != null) {
            mainFrame.getGraphPaletteMenu().setAllItemsEnabled(!t.hasLinkedPalette());
        }
        applyStartButtonStyle(t);
        applyIconToWindow(t);
    }

    /**
     * Applies the Start button style for the given theme.
     * Themes can override {@link ThemeDefinition#startButtonStyle()} to supply
     * their own accent; the default falls back to GitHub green.
     */
    public static void applyStartButtonStyle(Theme t) {
        if (controlPanel == null) return;
        String style = t.definition().startButtonStyle();
        if (style == null) style = ButtonStyles.DEFAULT_START;
        controlPanel.startButton.putClientProperty("FlatLaf.style", style);
    }

    /**
     * Loads and applies the branding icon to the main window, tinting it for
     * the given theme when the theme provides icon tint colors.
     */
    private static void applyIconToWindow(Theme t) {
        if (mainFrame == null) return;
        java.awt.Color primary   = t.definition().iconPrimaryTint();
        java.awt.Color secondary = t.definition().iconSecondaryTint();
        java.util.List<java.awt.Image> icons =
                (primary != null && secondary != null)
                ? AppIcon.active.loadAllTinted(primary, secondary)
                : AppIcon.active.loadAll();
        if (!icons.isEmpty()) {
            mainFrame.setIconImages(icons);
        }
    }

    /**
     * Returns the usable screen bounds (excluding the macOS menu bar, Dock,
     * and other OS-reserved areas) for the default screen.
     * <p>
     * Uses {@link java.awt.GraphicsEnvironment#getMaximumWindowBounds()} which
     * is always non-null and already accounts for all OS-reserved insets.
     * This avoids the NPE risk of {@code MouseInfo.getPointerInfo()} which
     * returns {@code null} when the app does not yet have screen access
     * (common during jpackage startup on macOS before the first window appears).
     * </p>
     */
    private static java.awt.Rectangle getUsableScreenBounds() {
        return java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
    }

    /**
     * Applies the Cancel button style for the given theme.
     * Themes can override {@link ThemeDefinition#cancelButtonStyle()} to supply
     * a theme-coherent "stop" colour; the default falls back to amber.
     */
    public static void applyCancelButtonStyle(Theme t) {
        if (controlPanel == null) return;
        String style = t.definition().cancelButtonStyle();
        if (style == null) style = ButtonStyles.CANCEL;
        controlPanel.startButton.putClientProperty("FlatLaf.style", style);
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
        theme.configureLaf();
        
        mainFrame = new MainFrame();

        // Embed the menu bar into the FlatLaf custom title bar (VS Code style) on
        // non-macOS only. On macOS the menu bar lives in the native system menu bar
        // at the top of the screen; embedding it here would conflict.
        if (!App.isMacOs()) {
            mainFrame.getRootPane().putClientProperty(
                    com.formdev.flatlaf.FlatClientProperties.MENU_BAR_EMBEDDED, true);
        }

        // Apply branding icon to the window title bar and taskbar, tinted for the
        // current theme if the theme supplies tint colors.
        applyIconToWindow(theme);

        if (runPanel != null) {
            runPanel.hideFirstColumn();
        }
        selFrame = new SelectDriveFrame();

        // Establish chart base style for the current LAF *before* loading the saved palette.
        // Without this, any palette applied in loadPropertiesConfig() (e.g. Old Glory
        // in Dark mode) would run against an uninitialized chart outer background and
        // a null foregroundColor, producing invisible or clashing colors on startup.
        updateChartPanelStyle();

        // Themes with hard-linked chart palettes apply their own chart
        // colors first; loadPropertiesConfig() will skip palette.apply()
        // for these themes (see GraphPaletteMenu.syncFromModel()).
        theme.applyLinkedPalette();

        mainFrame.loadPropertiesConfig();
        if (theme.hasLinkedPalette()) {
            mainFrame.getGraphPaletteMenu().setAllItemsEnabled(false);
        }

        // Theme-specific title bar text (FlatLaf client property).
        Color titleFg = theme.definition().titleBarForeground();
        if (titleFg != null) {
            mainFrame.getRootPane().putClientProperty(
                "JRootPane.titleBarForeground", titleFg);
        }
        // Position the window at the center of the usable screen area.
        // NOTE: do NOT call pack() here — initComponents() already called
        // pack() with the original GroupLayout, and calling it again after
        // the constructor replaced the content pane disrupts native NSWindow
        // peer creation on macOS, causing the window to disappear from the
        // Dock and Accessibility even though setVisible(true) was called.
        mainFrame.setLocationRelativeTo(null);
        // Guard: clamp the window to the usable screen area so it can never
        // land off-screen regardless of macOS version or display configuration.
        java.awt.Rectangle usable = getUsableScreenBounds();
        int wx = Math.max(usable.x, Math.min(mainFrame.getX(), usable.x + usable.width  - mainFrame.getWidth()));
        int wy = Math.max(usable.y, Math.min(mainFrame.getY(), usable.y + usable.height - mainFrame.getHeight()));
        mainFrame.setLocation(wx, wy);
        progressBar = mainFrame.getProgressBar();
        // Apply theme-specific progress bar color directly on startup.
        Color pbFg = theme.definition().progressBarForeground();
        if (progressBar != null && pbFg != null) {
            progressBar.setForeground(pbFg);
        }
        // Seed the progress bar string with the initial target KB total.
        updateProgress();
        // Apply the saved theme's Start button colour now that the control
        // panel exists. Without this, the button always opens GitHub-green
        // because BenchmarkControlPanel seeds DEFAULT_START in its constructor.
        applyStartButtonStyle(theme);

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
        ThemeDefinition def = theme.definition();
        java.awt.Color primary   = def.iconPrimaryTint();
        java.awt.Color secondary = def.iconSecondaryTint();
        javax.swing.ImageIcon icon = (primary != null && secondary != null && def.tintAboutIcon())
                ? AppIcon.active.loadSizeTinted(128, primary, secondary)
                : AppIcon.active.loadSize(128);

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
                } catch (IOException | URISyntaxException | RuntimeException ex) {
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

        // Style all legend variants (combined single-row + split Write/Read rows)
        Color panelBg = UIManager.getColor("Panel.background");
        Color legendBg = (panelBg != null)
                ? new Color(panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue(), 200)
                : null;
        BlockBorder legendBorder = new BlockBorder(new Color(80, 80, 80));
        for (LegendTitle leg : new LegendTitle[]{combinedLegend, writeLegend, readLegend}) {
            if (leg == null) continue;
            leg.setItemPaint(foregroundColor);
            if (legendBg != null) leg.setBackgroundPaint(legendBg);
            leg.setFrame(legendBorder);
        }
        updateBadgeThemeColors();
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
        
        chart = new JFreeChart("", null , plot, false);

        // Build a two-row legend: Write items on one line, Read items below.
        // Each LegendTitle uses a filtered LegendItemSource that picks items
        // whose series key starts with "Write" or "Read" respectively.
        LegendItemSource writeSource = () -> {
            LegendItemCollection col = new LegendItemCollection();
            for (int ds = 0; ds < plot.getDatasetCount(); ds++) {
                var r = plot.getRenderer(ds);
                if (r == null) continue;
                var items = r.getLegendItems();
                for (int i = 0; i < items.getItemCount(); i++) {
                    LegendItem it = items.get(i);
                    if (it.getLabel().startsWith("Write")) col.add(it);
                }
            }
            return col;
        };
        LegendItemSource readSource = () -> {
            LegendItemCollection col = new LegendItemCollection();
            for (int ds = 0; ds < plot.getDatasetCount(); ds++) {
                var r = plot.getRenderer(ds);
                if (r == null) continue;
                var items = r.getLegendItems();
                for (int i = 0; i < items.getItemCount(); i++) {
                    LegendItem it = items.get(i);
                    if (it.getLabel().startsWith("Read")) col.add(it);
                }
            }
            return col;
        };
        combinedLegend = new LegendTitle(plot);
        combinedLegend.setPosition(RectangleEdge.BOTTOM);
        combinedLegend.setMargin(new RectangleInsets(0, 0, 0, 0));
        writeLegend = new LegendTitle(writeSource);
        writeLegend.setPosition(RectangleEdge.BOTTOM);
        writeLegend.setMargin(new RectangleInsets(0, 0, 0, 0));
        writeLegend.setVisible(false);
        readLegend = new LegendTitle(readSource);
        readLegend.setPosition(RectangleEdge.BOTTOM);
        readLegend.setMargin(new RectangleInsets(0, 0, 0, 0));
        readLegend.setVisible(false);
        chart.addSubtitle(combinedLegend);
        chart.addSubtitle(writeLegend);
        chart.addSubtitle(readLegend);
        
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
     * Shows or hides the chart badge strip. When hidden, BorderLayout reclaims
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
        lbl.setForeground(BADGE_DEFAULT_FG);
        lbl.setOpaque(true);
        lbl.setBackground(BADGE_DEFAULT_BG);
        lbl.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return lbl;
    }

    /**
     * Refreshes all chart badge labels to reflect the current App settings.
     * Badges that differ from the last-run config are highlighted amber.
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
            applyBadgeHighlights();
        };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            javax.swing.SwingUtilities.invokeLater(update);
        }
    }

    /**
     * Compares each badge field against App.benchmark.config (the last completed run)
     * and sets amber background on any badge whose current value differs.
     * Also drives the chart subtitle and control-panel row highlights.
     * Must be called on the EDT.
     */
    private static void applyBadgeHighlights() {
        BenchmarkConfig lr = (App.benchmark != null) ? App.benchmark.config : null;
        if (lr == null) return; // no run yet — nothing to compare
        boolean anyStale = false;
        anyStale |= setBadgeStaleReturn(directIoLabel,   App.directEnable    != Boolean.TRUE.equals(lr.directIoEnabled));
        anyStale |= setBadgeStaleReturn(writeSyncLabel,  App.writeSyncEnable != Boolean.TRUE.equals(lr.writeSyncEnabled));
        anyStale |= setBadgeStaleReturn(sectorLabel,     App.sectorAlignment != lr.sectorAlignment);
        anyStale |= setBadgeStaleReturn(ioEngineLabel,   App.ioEngine        != lr.ioEngine);
        anyStale |= setBadgeStaleReturn(multiFileLabel,  App.multiFile       != Boolean.TRUE.equals(lr.multiFileEnabled));
        anyStale |= setBadgeStaleReturn(renderModeLabel, App.rmOption        != App.benchmark.getRenderMode());
        setChartModifiedIndicator(anyStale);
        // sync control-panel row highlights too
        if (controlPanel != null) controlPanel.showSettingsDrift();
    }

    private static boolean setBadgeStaleReturn(javax.swing.JLabel badge, boolean stale) {
        badge.setBackground(stale ? BADGE_STALE_BG : BADGE_DEFAULT_BG);
        if (stale) {
            badge.setForeground(BADGE_STALE_FG);
        } else {
            ThemeDefinition def = theme.definition();
            if (def.cycleBadgeColors() && chartBadgeList != null) {
                int idx = chartBadgeList.indexOf(badge);
                badge.setForeground(idx % 2 == 0 ? def.badgeEvenFg() : def.badgeOddFg());
            } else {
                badge.setForeground(BADGE_DEFAULT_FG);
            }
        }
        return stale;
    }

    /**
     * Returns true if any badge setting differs from App.benchmark.config.
     * Used by BenchmarkControlPanel to combine badge + row staleness for the subtitle.
     */
    static boolean isAnyBadgeStale() {
        if (App.benchmark == null) return false;
        BenchmarkConfig lr = App.benchmark.config;
        return App.directEnable    != Boolean.TRUE.equals(lr.directIoEnabled)
            || App.writeSyncEnable != Boolean.TRUE.equals(lr.writeSyncEnabled)
            || App.sectorAlignment != lr.sectorAlignment
            || App.ioEngine        != lr.ioEngine
            || App.multiFile       != Boolean.TRUE.equals(lr.multiFileEnabled)
            || App.rmOption        != App.benchmark.getRenderMode();
    }

    /**
     * Refreshes badge labels from nullable stored values (e.g. a loaded benchmark).
     * Any null value is rendered as "—" to indicate the data was not recorded.
     * Safe to call from any thread.
     */
    private static void refreshChartBadges(Boolean directIo, Boolean writeSync,
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
        
        boolean splitLegend = showMaxMin && App.hasWriteOperation() && App.hasReadOperation();
        combinedLegend.setVisible(!splitLegend);
        writeLegend.setVisible(splitLegend && App.hasWriteOperation());
        readLegend.setVisible(splitLegend && App.hasReadOperation());

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
        
        boolean splitLegend = showMaxMin && hasWrite && hasRead;
        combinedLegend.setVisible(!splitLegend);
        writeLegend.setVisible(splitLegend && hasWrite);
        readLegend.setVisible(splitLegend && hasRead);

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
        
        boolean splitLegend = showMaxMin && isWriteTest && isReadTest;
        combinedLegend.setVisible(!splitLegend);
        writeLegend.setVisible(splitLegend && isWriteTest);
        readLegend.setVisible(splitLegend && isReadTest);

        msAxis.setVisible(showDriveAccess);
    }
    
    private static final Logger SMART_LOG = Logger.getLogger(Gui.class.getName());

    static public void updateDiskInfo() {
        mainFrame.setLocation(App.locationDir.getAbsolutePath());
        chart.getTitle().setText(App.getDriveInfo());
        if (drivePanel != null) {
            drivePanel.refresh();
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
            } catch (IOException | RuntimeException ex) {
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
        // IO engine / options settings (mirrors what the badges already display)
        if (benchmark.config.ioEngine != null) App.ioEngine = benchmark.config.ioEngine;
        if (benchmark.config.sectorAlignment != null) App.sectorAlignment = benchmark.config.sectorAlignment;
        if (benchmark.config.writeSyncEnabled != null) App.writeSyncEnable = Boolean.TRUE.equals(benchmark.config.writeSyncEnabled);
        if (benchmark.config.directIoEnabled != null) App.directEnable = Boolean.TRUE.equals(benchmark.config.directIoEnabled);
        if (benchmark.config.multiFileEnabled != null) App.multiFile = Boolean.TRUE.equals(benchmark.config.multiFileEnabled);
        // render mode is stored on the Benchmark itself, not in BenchmarkConfig
        App.rmOption = benchmark.getRenderMode();
        mainFrame.syncFromModel();
        // sync AdvancedOptionsFrame if it has already been opened (preserve lazy-init)
        if (advancedFrame != null) advancedFrame.syncFromModel();
        // set as the active benchmark so stale-badge comparisons have a baseline
        App.benchmark = benchmark;
        // clear any stale highlights — the newly loaded benchmark IS the current baseline
        clearAllStaleHighlights();
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
        // IO engine / options settings (mirrors what the badges already display)
        if (benchmark.config.ioEngine != null) App.ioEngine = benchmark.config.ioEngine;
        if (benchmark.config.sectorAlignment != null) App.sectorAlignment = benchmark.config.sectorAlignment;
        if (benchmark.config.writeSyncEnabled != null) App.writeSyncEnable = Boolean.TRUE.equals(benchmark.config.writeSyncEnabled);
        if (benchmark.config.directIoEnabled != null) App.directEnable = Boolean.TRUE.equals(benchmark.config.directIoEnabled);
        if (benchmark.config.multiFileEnabled != null) App.multiFile = Boolean.TRUE.equals(benchmark.config.multiFileEnabled);
        // render mode is stored on the Benchmark itself, not in BenchmarkConfig
        App.rmOption = benchmark.getRenderMode();
        mainFrame.syncFromModel();
        // sync AdvancedOptionsFrame if it has already been opened (preserve lazy-init)
        if (advancedFrame != null) advancedFrame.syncFromModel();
        // set as the active benchmark so stale-badge comparisons have a baseline
        App.benchmark = benchmark;
        // clear any stale highlights — the newly loaded benchmark IS the current baseline
        clearAllStaleHighlights();
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
