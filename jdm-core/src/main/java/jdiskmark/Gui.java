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
import org.jfree.chart.labels.StandardXYToolTipGenerator;
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
    
    public enum Palette {
        CLASSIC("Classic"),
        BLUE_GREEN("Blue Green"),
        BARD_COOL("Bard Cool"),
        BARD_WARM("Bard Warm"),
        BETA("Beta"),
        OLD_GLORY("Old Glory"),
        SAKURA("Sakura");

        private final String displayName;

        Palette(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() { return displayName; }

        /** Applies this palette's colour scheme to the chart renderers. */
        public void apply() {
            switch (this) {
                case CLASSIC    -> ChartPalette.setClassicColorScheme();
                case BLUE_GREEN -> ChartPalette.setBlueGreenScheme();
                case BARD_COOL  -> ChartPalette.setCoolColorScheme();
                case BARD_WARM  -> ChartPalette.setWarmColorScheme();
                case BETA       -> ChartPalette.setBetaColorScheme();
                case OLD_GLORY  -> ChartPalette.setOldGloryColorScheme();
                case SAKURA     -> ChartPalette.setSakuraColorScheme();
            }
        }
    }
    
    public enum Theme {
        DARK("Dark"),
        LIGHT("Light"),
        DARCULA("Darcula"),
        OLD_GLORY("Old Glory"),
        SAKURA("Sakura");

        private final String displayName;

        Theme(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() { return displayName; }

        /** Applies this theme's look-and-feel and updates the UI. */
        public void apply() {
            switch (this) {
                case DARK      -> goDarkTheme();
                case LIGHT     -> goLightTheme();
                case DARCULA   -> goDarculaTheme();
                case OLD_GLORY -> goOldGloryTheme();
                case SAKURA    -> goSakuraTheme();
            }
        }

        public String getLafClassName() {
            boolean isMac = App.isMacOs();

            return switch (this) {
                case DARK    -> isMac ? "com.formdev.flatlaf.themes.FlatMacDarkLaf"
                                     : "com.formdev.flatlaf.FlatDarkLaf";
                case LIGHT   -> isMac ? "com.formdev.flatlaf.themes.FlatMacLightLaf"
                                     : "com.formdev.flatlaf.FlatLightLaf";
                case DARCULA -> "com.formdev.flatlaf.FlatDarculaLaf";
                case OLD_GLORY -> isMac ? "com.formdev.flatlaf.themes.FlatMacDarkLaf"
                                     : "com.formdev.flatlaf.FlatDarkLaf";
                case SAKURA  -> isMac ? "com.formdev.flatlaf.themes.FlatMacLightLaf"
                                     : "com.formdev.flatlaf.FlatLightLaf";
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
    static Color BADGE_AMBER_BG   = new Color(0xC8, 0x78, 0x00); // deep amber
    static Color BADGE_DEFAULT_BG = new Color(40, 40, 40, 180);
    static Color BADGE_DEFAULT_FG = new Color(200, 200, 200);
    /** Foreground to use when a badge is stale (set together with BADGE_AMBER_BG). */
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
                    modifiedSubtitle.setPaint(new Color(0xC8, 0x78, 0x00));
                    modifiedSubtitle.setPosition(RectangleEdge.BOTTOM);
                    modifiedSubtitle.setHorizontalAlignment(HorizontalAlignment.CENTER);
                    modifiedSubtitle.setPadding(new RectangleInsets(0, 0, 4, 0));
                }
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

    /** Resets all badge backgrounds (and foregrounds in Old Glory) to the default style. */
    public static void clearBadgeHighlights() {
        if (chartBadgeList == null) return;
        for (int i = 0; i < chartBadgeList.size(); i++) {
            javax.swing.JLabel b = chartBadgeList.get(i);
            b.setBackground(BADGE_DEFAULT_BG);
            if (theme == Theme.OLD_GLORY) {
                b.setForeground(i % 2 == 0 ? ThemeColors.OLD_GLORY_RED : ThemeColors.OLD_GLORY_BLUE);
            } else if (theme == Theme.SAKURA) {
                b.setForeground(i % 2 == 0 ? ThemeColors.SAKURA_ROSE : ThemeColors.SAKURA_DARK);
            } else {
                b.setForeground(BADGE_DEFAULT_FG);
            }
        }
    }

    /** Updates badge colors to match the current window theme. */
    static void updateBadgeThemeColors() {
        if (theme == Theme.OLD_GLORY) {
            // Old Glory: white background, alternating crimson / flag-blue text
            BADGE_AMBER_BG   = ThemeColors.OLD_GLORY_RED;  // stale → crimson background
            BADGE_STALE_FG   = Color.WHITE;                // stale foreground on crimson
            BADGE_DEFAULT_BG = ThemeColors.OLD_GLORY_BADGE_BG;
            BADGE_DEFAULT_FG = ThemeColors.OLD_GLORY_BLUE; // used for newly created badges
            javax.swing.border.Border outerBorder =
                    javax.swing.BorderFactory.createLineBorder(ThemeColors.OLD_GLORY_BLUE, 1);
            javax.swing.border.Border innerBorder =
                    javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5);
            javax.swing.border.Border badgeBorder =
                    javax.swing.BorderFactory.createCompoundBorder(outerBorder, innerBorder);
            if (chartBadgeList != null) {
                for (int i = 0; i < chartBadgeList.size(); i++) {
                    chartBadgeList.get(i).setBackground(BADGE_DEFAULT_BG);
                    // alternate crimson / flag-blue across the badge strip
                    chartBadgeList.get(i).setForeground(
                            i % 2 == 0 ? ThemeColors.OLD_GLORY_RED : ThemeColors.OLD_GLORY_BLUE);
                    chartBadgeList.get(i).setBorder(badgeBorder);
                }
            }
            return;
        }
        if (theme == Theme.SAKURA) {
            // Sakura: blush white background, alternating rose / cherry-bark text
            BADGE_AMBER_BG   = ThemeColors.SAKURA_DARK;    // stale → deep rose background
            BADGE_STALE_FG   = Color.WHITE;
            BADGE_DEFAULT_BG = ThemeColors.SAKURA_BADGE_BG;
            BADGE_DEFAULT_FG = ThemeColors.SAKURA_ROSE;    // used for newly created badges
            javax.swing.border.Border outerBorder =
                    javax.swing.BorderFactory.createLineBorder(ThemeColors.SAKURA_ROSE, 1);
            javax.swing.border.Border innerBorder =
                    javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5);
            javax.swing.border.Border badgeBorder =
                    javax.swing.BorderFactory.createCompoundBorder(outerBorder, innerBorder);
            if (chartBadgeList != null) {
                for (int i = 0; i < chartBadgeList.size(); i++) {
                    chartBadgeList.get(i).setBackground(BADGE_DEFAULT_BG);
                    // alternate sakura rose / deep rose (bark) across the badge strip
                    chartBadgeList.get(i).setForeground(
                            i % 2 == 0 ? ThemeColors.SAKURA_ROSE : ThemeColors.SAKURA_DARK);
                    chartBadgeList.get(i).setBorder(badgeBorder);
                }
            }
            return;
        }
        if (theme == Theme.LIGHT) {
            BADGE_DEFAULT_BG = new Color(220, 220, 220, 200);
            BADGE_DEFAULT_FG = new Color(50, 50, 50);
            BADGE_AMBER_BG   = new Color(0xE6, 0xA0, 0x1E);
            BADGE_STALE_FG   = Color.WHITE;
        } else {
            BADGE_DEFAULT_BG = new Color(40, 40, 40, 180);
            BADGE_DEFAULT_FG = new Color(200, 200, 200);
            BADGE_AMBER_BG   = new Color(0xC8, 0x78, 0x00);
            BADGE_STALE_FG   = Color.WHITE;
        }
        if (chartBadgeList == null) return;
        javax.swing.border.Border resetBorder =
                javax.swing.BorderFactory.createEmptyBorder(2, 6, 2, 6);
        for (javax.swing.JLabel b : chartBadgeList) {
            b.setForeground(BADGE_DEFAULT_FG);
            b.setBackground(BADGE_DEFAULT_BG);
            b.setBorder(resetBorder);
        }
        applyBadgeHighlights();
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
    static Color foregroundColor;
    
    /**
     * Removes UIManager overrides set by {@link #configureOldGloryLaf()}.
     * Call this AFTER {@code UIManager.setLookAndFeel()} in every non-OldGlory
     * configure method so the new LAF's own defaults take over.
     */
    private static void clearOldGloryOverrides() {
        // Clear the global FlatLaf variable overrides (@accentColor, @background,
        // @foreground, TitlePane.foreground) that configureOldGloryLaf() injects.
        // Without this, FlatDarkLaf/FlatLightLaf re-use the crimson @accentColor
        // and all @accentColor-derived components (progress bar, sliders, etc.) stay red.
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
        // scrollbar
        UIManager.put("ScrollBar.thumb",            null);
        UIManager.put("ScrollBar.thumbHover",       null);
        UIManager.put("ScrollBar.thumbPressed",     null);
        // title pane
        UIManager.put("TitlePane.foreground",       null);
        // Note: progress bar foreground is reset via direct setForeground() in each go*Theme()
        // rather than via UIManager because FlatProgressBarUI may cache the color independently.
    }

    public static void configureDarkLaf() {
        try {
            FlatLaf.setGlobalExtraDefaults(null); // clear any accent override from Old Glory theme
            if (App.isWindows()) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else if (App.isMacOs()) {
                UIManager.setLookAndFeel(new FlatMacDarkLaf());
            } else if (App.isLinux()) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            }
            clearOldGloryOverrides();
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
            FlatLaf.setGlobalExtraDefaults(null); // clear any accent override from Old Glory theme
            UIManager.setLookAndFeel(new FlatDarculaLaf());
            clearOldGloryOverrides();
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
            FlatLaf.setGlobalExtraDefaults(null); // clear any accent override from Old Glory theme
            if (App.isWindows()) {
                UIManager.setLookAndFeel(new FlatLightLaf());
            } else if (App.isMacOs()) {
                UIManager.setLookAndFeel(new FlatMacLightLaf());
            } else if (App.isLinux()) {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
            clearOldGloryOverrides();
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
    
    /**
     * Old Glory theme — FlatLight base with patriotic red + blue accents:
     * <ul>
     *   <li>Old Glory Red (#B22234) as {@code @accentColor}: checkboxes,
     *       radio buttons, slider thumb, focus rings.</li>
     *   <li>Pure white background + Old Glory Navy text via FlatLaf
     *       {@code @background}/{@code @foreground} so all component
     *       colors are derived correctly by the LAF.</li>
     *   <li>Royal Blue (#1F4DA0): table / list / tree row selections
     *       (white text) + scrollbar thumb.</li>
     *   <li>Red tab underline and progress bar complete the palette.</li>
     * </ul>
     */
    public static void configureOldGloryLaf() {
        try {
            // All three global vars must be set before UIManager.setLookAndFeel so
            // FlatLaf can derive every computed component color from them correctly.
            java.util.Map<String, String> extras = new java.util.HashMap<>();
            extras.put("@accentColor", ThemeColors.HEX_OLD_GLORY_RED);  // checkboxes, focus rings
            extras.put("@background",  "#FFFFFF"); // pure white panels
            extras.put("@foreground",  ThemeColors.HEX_OLD_GLORY_BLUE); // all UI text
            extras.put("TitlePane.foreground", ThemeColors.HEX_OLD_GLORY_BLUE); // title bar text
            FlatLaf.setGlobalExtraDefaults(extras);
            if (App.isMacOs()) {
                UIManager.setLookAndFeel(new FlatMacLightLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
            if (!App.isMacOs()) {
                javax.swing.JFrame.setDefaultLookAndFeelDecorated(true);
                javax.swing.JDialog.setDefaultLookAndFeelDecorated(true);
            }
            // Post-install: flag blue row/tab selections + scrollbar; red accent + title.
            UIManager.put("Table.selectionBackground",     ThemeColors.OLD_GLORY_BLUE);
            UIManager.put("Table.selectionForeground",     Color.WHITE);
            UIManager.put("List.selectionBackground",      ThemeColors.OLD_GLORY_BLUE);
            UIManager.put("List.selectionForeground",      Color.WHITE);
            UIManager.put("Tree.selectionBackground",      ThemeColors.OLD_GLORY_BLUE);
            UIManager.put("Tree.selectionForeground",      Color.WHITE);
            // Selected tab: flag blue bg + white text (same treatment as table/list selections)
            UIManager.put("TabbedPane.selectedBackground",      ThemeColors.OLD_GLORY_BLUE);
            UIManager.put("TabbedPane.selectedForeground",      Color.WHITE);
            // Underline: red whether the pane is focused or not
            UIManager.put("TabbedPane.underlineColor",          ThemeColors.OLD_GLORY_RED);
            UIManager.put("TabbedPane.inactiveUnderlineColor",  ThemeColors.OLD_GLORY_RED);
            // Match focusColor to selectedBackground so the tab looks identical in all states;
            // use a light tint only for hover to keep it subtle over white.
            UIManager.put("TabbedPane.focusColor",              ThemeColors.OLD_GLORY_BLUE);
            UIManager.put("TabbedPane.hoverColor",              ThemeColors.OLD_GLORY_TAB_HOVER); // very light blue hover
            UIManager.put("ScrollBar.thumb",               ThemeColors.OLD_GLORY_BLUE);
            UIManager.put("ScrollBar.thumbHover",          ThemeColors.OLD_GLORY_BLUE_HOVER);
            UIManager.put("ScrollBar.thumbPressed",        ThemeColors.OLD_GLORY_BLUE_PRESS);
            UIManager.put("TitlePane.foreground",          ThemeColors.OLD_GLORY_BLUE);
        } catch (UnsupportedLookAndFeelException e) {
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
     * Applies the Sakura (Cherry Blossom) Look-and-Feel to the application.
     * <p>
     * Uses FlatLightLaf as the base, with:
     * <ul>
     *   <li>Sakura Rose (#D4607C) as {@code @accentColor}: checkboxes, radio
     *       buttons, slider thumb, focus rings.</li>
     *   <li>Near-white (#FFFBFC) background + Cherry Bark (#2D1B22) foreground
     *       so all component colours derive correctly from the LAF.</li>
     *   <li>Deep rose selections (white text) + matching scrollbar thumb.</li>
     *   <li>Rose tab underline; very light pink hover tint.</li>
     * </ul>
     */
    public static void configureSakuraLaf() {
        try {
            java.util.Map<String, String> extras = new java.util.HashMap<>();
            extras.put("@accentColor", ThemeColors.HEX_SAKURA_ROSE);
            extras.put("@background",  "#FFFBFC"); // near-white with faintest pink tint
            extras.put("@foreground",  ThemeColors.HEX_SAKURA_BARK);
            extras.put("TitlePane.foreground", ThemeColors.HEX_SAKURA_BARK);
            FlatLaf.setGlobalExtraDefaults(extras);
            if (App.isMacOs()) {
                UIManager.setLookAndFeel(new FlatMacLightLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
            if (!App.isMacOs()) {
                javax.swing.JFrame.setDefaultLookAndFeelDecorated(true);
                javax.swing.JDialog.setDefaultLookAndFeelDecorated(true);
            }
            // Selections: deep sakura rose bg + white text
            UIManager.put("Table.selectionBackground",     ThemeColors.SAKURA_ROSE);
            UIManager.put("Table.selectionForeground",     Color.WHITE);
            UIManager.put("List.selectionBackground",      ThemeColors.SAKURA_ROSE);
            UIManager.put("List.selectionForeground",      Color.WHITE);
            UIManager.put("Tree.selectionBackground",      ThemeColors.SAKURA_ROSE);
            UIManager.put("Tree.selectionForeground",      Color.WHITE);
            // Tabs: pale petal selected bg + dark text; rose underline; hover goes darker (rose)
            UIManager.put("TabbedPane.selectedBackground",     ThemeColors.SAKURA_LIGHT);
            UIManager.put("TabbedPane.selectedForeground",     Color.BLACK); // this color is sensitive and can break LAF - do not change without testing
            UIManager.put("TabbedPane.underlineColor",         ThemeColors.SAKURA_ROSE);
            UIManager.put("TabbedPane.inactiveUnderlineColor", ThemeColors.SAKURA_ROSE);
            UIManager.put("TabbedPane.focusColor",             ThemeColors.SAKURA_LIGHT);
            UIManager.put("TabbedPane.hoverColor",             ThemeColors.SAKURA_ROSE);
            // Scrollbar: medium pink thumb
            UIManager.put("ScrollBar.thumb",               ThemeColors.SAKURA_PINK);
            UIManager.put("ScrollBar.thumbHover",          ThemeColors.SAKURA_SCROLL_HOVER);
            UIManager.put("ScrollBar.thumbPressed",        ThemeColors.SAKURA_SCROLL_PRESS);
            UIManager.put("TitlePane.foreground",          ThemeColors.SAKURA_BARK);
        } catch (UnsupportedLookAndFeelException e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException
                    | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
                java.util.logging.Logger.getLogger(MainFrame.class.getName())
                        .log(java.util.logging.Level.SEVERE, null, ex);
            }
        }
    }

    // switch to dark theme
    public static void goDarkTheme() {
        configureDarkLaf();
        FlatLaf.updateUI();
        updateChartPanelStyle();
        if (mainFrame != null) mainFrame.getRootPane().putClientProperty("JRootPane.titleBarForeground", null);
        // Reset progress bar to current LAF default (direct component call, bypasses UIManager caching)
        if (progressBar != null) progressBar.setForeground(UIManager.getColor("ProgressBar.foreground"));
    }
    
    // switch to darcula theme
    public static void goDarculaTheme() {
        configureDarculaLaf();
        FlatLaf.updateUI();
        updateChartPanelStyle();
        if (mainFrame != null) mainFrame.getRootPane().putClientProperty("JRootPane.titleBarForeground", null);
        if (progressBar != null) progressBar.setForeground(UIManager.getColor("ProgressBar.foreground"));
    }
    
    // switch to light theme
    public static void goLightTheme() {
        configureLightLaf();
        FlatLaf.updateUI();
        updateChartPanelStyle();
        if (mainFrame != null) mainFrame.getRootPane().putClientProperty("JRootPane.titleBarForeground", null);
        if (progressBar != null) progressBar.setForeground(UIManager.getColor("ProgressBar.foreground"));
    }

    /** Old Glory theme: crimson-accented FlatLight + auto-applies Old Glory graph palette. */
    public static void goOldGloryTheme() {
        configureOldGloryLaf();
        FlatLaf.updateUI();
        updateChartPanelStyle();
        // Force title bar text to navy via root-pane client property
        if (mainFrame != null) {
            mainFrame.getRootPane().putClientProperty(
                "JRootPane.titleBarForeground", ThemeColors.OLD_GLORY_BLUE);
        }
        if (progressBar != null) progressBar.setForeground(ThemeColors.OLD_GLORY_RED);
        // Auto-apply the matching Old Glory graph palette
        if (chart != null) {
            palette = Palette.OLD_GLORY;
            Palette.OLD_GLORY.apply();
        }
    }

    /** Sakura (Cherry Blossom) theme: rose-accented FlatLight + auto-applies Sakura graph palette. */
    public static void goSakuraTheme() {
        configureSakuraLaf();
        FlatLaf.updateUI();
        updateChartPanelStyle();
        // Force title bar text to cherry bark via root-pane client property
        if (mainFrame != null) {
            mainFrame.getRootPane().putClientProperty(
                "JRootPane.titleBarForeground", ThemeColors.SAKURA_BARK);
        }
        if (progressBar != null) progressBar.setForeground(ThemeColors.SAKURA_ROSE);
        // Auto-apply the matching Sakura graph palette
        if (chart != null) {
            palette = Palette.SAKURA;
            Palette.SAKURA.apply();
        }
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
            case DARK    -> configureDarkLaf();
            case LIGHT   -> configureLightLaf();
            case DARCULA -> configureDarculaLaf();
            case OLD_GLORY -> configureOldGloryLaf();
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
        java.util.List<java.awt.Image> icons = AppIcon.active.loadAll();
        if (!icons.isEmpty()) {
            mainFrame.setIconImages(icons);
        }

        if (runPanel != null) {
            runPanel.hideFirstColumn();
        }
        selFrame = new SelectDriveFrame();

        // Establish chart base style for the current LAF *before* loading the saved palette.
        // Without this, any palette applied in loadPropertiesConfig() (e.g. Old Glory
        // in Dark mode) would run against an uninitialized chart outer background and
        // a null foregroundColor, producing invisible or clashing colors on startup.
        updateChartPanelStyle();

        mainFrame.loadPropertiesConfig();

        // OLD_GLORY-only: title bar text must be navy blue (FlatLaf client property).
        if (theme == Theme.OLD_GLORY) {
            mainFrame.getRootPane().putClientProperty(
                "JRootPane.titleBarForeground", ThemeColors.OLD_GLORY_BLUE);
        }
        mainFrame.setLocationRelativeTo(null);
        progressBar = mainFrame.getProgressBar();
        // Apply OLD_GLORY progress bar color directly on startup (cannot be set before this line
        // because progressBar is null until mainFrame.getProgressBar() is called above).
        if (theme == Theme.OLD_GLORY && progressBar != null) {
            progressBar.setForeground(ThemeColors.OLD_GLORY_RED);
        }

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
        javax.swing.ImageIcon icon = AppIcon.active.loadSize(128);

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
        badge.setBackground(stale ? BADGE_AMBER_BG : BADGE_DEFAULT_BG);
        if (stale) {
            badge.setForeground(BADGE_STALE_FG);
        } else if (theme == Theme.OLD_GLORY && chartBadgeList != null) {
            int idx = chartBadgeList.indexOf(badge);
            badge.setForeground(idx % 2 == 0 ? ThemeColors.OLD_GLORY_RED : ThemeColors.OLD_GLORY_BLUE);
        } else if (theme == Theme.SAKURA && chartBadgeList != null) {
            int idx = chartBadgeList.indexOf(badge);
            badge.setForeground(idx % 2 == 0 ? ThemeColors.SAKURA_ROSE : ThemeColors.SAKURA_DARK);
        } else {
            badge.setForeground(BADGE_DEFAULT_FG);
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
