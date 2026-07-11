package jdiskmark;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;

/**
 * Static factory for chart colour schemes.
 * <p>
 * Each method configures the JFreeChart plot, renderers, and axes to match a
 * specific visual palette.  Adding a new palette requires:
 * <ol>
 *   <li>A {@code static void setXxxColorScheme()} method in this class.</li>
 *   <li>A new constant in {@link Gui.Palette} with a delegation call to the new method.</li>
 * </ol>
 * All methods reference the {@code public static} chart fields in {@link Gui}
 * (e.g. {@code Gui.chart}, {@code Gui.bwRenderer}) which are set during
 * {@link Gui#createChartPanel()}.
 * </p>
 */
public final class ChartPalette {

    private ChartPalette() { /* static utility class */ }

    // -----------------------------------------------------------------------
    // Palette implementations
    // -----------------------------------------------------------------------

    /** The original color scheme. */
    static void setClassicColorScheme() {
        Gui.palette = Gui.Palette.CLASSIC;
        restoreDefaultPlotBackground();

        Gui.bwRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.bwRenderer.setSeriesPaint(0, Color.YELLOW);          // write sample
        Gui.bwRenderer.setSeriesPaint(1, Color.WHITE);           // write trend
        Gui.bwRenderer.setSeriesPaint(2, Color.GREEN);           // write max
        Gui.bwRenderer.setSeriesPaint(3, Color.RED);             // write min
        Gui.bwRenderer.setSeriesPaint(4, Color.LIGHT_GRAY);      // read sample
        Gui.bwRenderer.setSeriesPaint(5, Color.ORANGE);          // read trend
        Gui.bwRenderer.setSeriesPaint(6, Color.GREEN.darker());  // read max
        Gui.bwRenderer.setSeriesPaint(7, Color.RED.darker());    // read min

        Gui.msRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.msRenderer.setSeriesPaint(0, Color.CYAN);            // write latency
        Gui.msRenderer.setSeriesPaint(1, Color.MAGENTA);         // read latency
    }

    /** Blue/green scheme. */
    static void setBlueGreenScheme() {
        System.out.println("setting blue green palette");
        Gui.palette = Gui.Palette.BLUE_GREEN;
        restoreDefaultPlotBackground();

        Gui.bwRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.bwRenderer.setSeriesPaint(0, new Color(0x7C9CDC));   // write sample
        Gui.bwRenderer.setSeriesPaint(1, new Color(0x2A5CB0));   // write trend
        Gui.bwRenderer.setSeriesPaint(2, new Color(0xBCD2EF));   // write max
        Gui.bwRenderer.setSeriesPaint(3, new Color(0xBFD5EA));   // write min
        Gui.bwRenderer.setSeriesPaint(4, new Color(0xAACC00));   // read sample
        Gui.bwRenderer.setSeriesPaint(5, new Color(0x008080));   // read trend
        Gui.bwRenderer.setSeriesPaint(6, new Color(0x6B8E23));   // read max
        Gui.bwRenderer.setSeriesPaint(7, new Color(0x228B22));   // read min

        Gui.msRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.msRenderer.setSeriesPaint(0, new Color(0x7C9CDC));   // write latency
        Gui.msRenderer.setSeriesPaint(1, new Color(0xAACC00));   // read latency
    }

    /** Cool color scheme proposed by Bard. */
    static void setCoolColorScheme() {
        System.out.println("setting cool palette");
        Gui.palette = Gui.Palette.BARD_COOL;
        restoreDefaultPlotBackground();

        Gui.bwRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.bwRenderer.setSeriesPaint(0, new Color(0x54a0ff));   // write sample
        Gui.bwRenderer.setSeriesPaint(1, new Color(0x808080));   // write trend
        Gui.bwRenderer.setSeriesPaint(2, new Color(0x4CAF50));   // write max
        Gui.bwRenderer.setSeriesPaint(3, new Color(0xFF5722));   // write min
        Gui.bwRenderer.setSeriesPaint(4, new Color(0x00BCD4));   // read sample
        Gui.bwRenderer.setSeriesPaint(5, new Color(0x9E9E9E));   // read trend
        Gui.bwRenderer.setSeriesPaint(6, new Color(0x66BB6A));   // read max
        Gui.bwRenderer.setSeriesPaint(7, new Color(0xF44336));   // read min

        Gui.msRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.msRenderer.setSeriesPaint(0, new Color(0x54a0ff));   // write latency
        Gui.msRenderer.setSeriesPaint(1, new Color(0x00BCD4));   // read latency
    }

    /** Warm color scheme proposed by Bard. */
    static void setWarmColorScheme() {
        System.out.println("setting warm palette");
        Gui.palette = Gui.Palette.BARD_WARM;
        restoreDefaultPlotBackground();

        Gui.bwRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.bwRenderer.setSeriesPaint(0, new Color(0xFFC107));   // write sample
        Gui.bwRenderer.setSeriesPaint(1, new Color(0xEBEBEB));   // write trend
        Gui.bwRenderer.setSeriesPaint(2, new Color(0x4CAF50));   // write max
        Gui.bwRenderer.setSeriesPaint(3, new Color(0xFF5722));   // write min
        Gui.bwRenderer.setSeriesPaint(4, new Color(0xE91E63));   // read sample
        Gui.bwRenderer.setSeriesPaint(5, new Color(0xD3D3D3));   // read trend
        Gui.bwRenderer.setSeriesPaint(6, new Color(0x66BB6A));   // read max
        Gui.bwRenderer.setSeriesPaint(7, new Color(0xF44336));   // read min

        Gui.msRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.msRenderer.setSeriesPaint(0, new Color(0xFFC107));   // write latency
        Gui.msRenderer.setSeriesPaint(1, new Color(0xE91E63));   // read latency
    }

    /**
     * Beta palette - matches the Python/matplotlib dark-background look.
     * Dark plot area (#1c1c1c), orange write series, cyan read series.
     */
    static void setBetaColorScheme() {
        System.out.println("setting beta palette");
        Gui.palette = Gui.Palette.BETA;

        XYPlot plot = (XYPlot) Gui.chart.getPlot();
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

        Gui.bwRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.bwRenderer.setSeriesPaint(0, new Color(0xE07B39));             // write sample
        Gui.bwRenderer.setSeriesPaint(1, new Color(189, 176, 138, 200));   // write trend - alpha-softened, dotted
        Gui.bwRenderer.setSeriesStroke(1, avgDot);
        Gui.bwRenderer.setSeriesPaint(2, new Color(0xF5A623));             // write max
        Gui.bwRenderer.setSeriesPaint(3, new Color(0xC0623A));             // write min
        Gui.bwRenderer.setSeriesPaint(4, new Color(0x4FC3F7));             // read sample
        Gui.bwRenderer.setSeriesPaint(5, new Color(160, 216, 239, 200));   // read trend - alpha-softened, dotted
        Gui.bwRenderer.setSeriesStroke(5, avgDot);
        Gui.bwRenderer.setSeriesPaint(6, new Color(0x81D4FA));             // read max
        Gui.bwRenderer.setSeriesPaint(7, new Color(0x0288D1));             // read min

        Gui.msRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.msRenderer.setSeriesPaint(0, new Color(0xE07B39));  // write latency
        Gui.msRenderer.setSeriesPaint(1, new Color(0x4FC3F7));  // read latency
    }

    /**
     * Old Glory palette - red, white and blue on a clean white canvas.
     * Write series in reds; trend lines match their sample line (dashed);
     * read series in Old Glory Blue (Pantone 282, #3C3B6E).
     * <p>
     * The entire chart canvas (outer background and inner plot) is white so
     * that flag-blue title/axes text is always readable regardless of the
     * surrounding LAF.  {@link Gui#updateChartPanelStyle()} restores the
     * LAF-derived background when switching to another palette.
     * </p>
     */
    static void setOldGloryColorScheme() {
        System.out.println("Setting Old Glory palette");
        Gui.palette = Gui.Palette.OLD_GLORY;

        // White canvas: ensures flagBlue text is readable in any surrounding LAF
        Gui.chart.setBackgroundPaint(Color.WHITE);
        XYPlot plot = (XYPlot) Gui.chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(new Color(0x999999));
        plot.setDomainGridlinePaint(new Color(0xE0E0E0)); // subtle light gray grid
        plot.setRangeGridlinePaint(new Color(0xE0E0E0));
        if (Gui.chart.getLegend() != null) {
            Gui.chart.getLegend().setBackgroundPaint(Color.WHITE);
            Gui.chart.getLegend().setFrame(new BlockBorder(new Color(0xCCCCCC)));
        }

        Stroke bold = new BasicStroke(1.5f);
        Stroke dash = new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10.0f, new float[]{2.0f, 6.0f}, 0.0f);

        Gui.bwRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.bwRenderer.setSeriesPaint(0,  ThemeColors.CRIMSON);              // write sample
        Gui.bwRenderer.setSeriesStroke(0, bold);
        Gui.bwRenderer.setSeriesPaint(1,  ThemeColors.CRIMSON_FADE);         // write trend (dashed)
        Gui.bwRenderer.setSeriesStroke(1, dash);
        Gui.bwRenderer.setSeriesPaint(2,  ThemeColors.CRIMSON_LIGHT);        // write max
        Gui.bwRenderer.setSeriesPaint(3,  ThemeColors.CRIMSON_DARK);         // write min
        Gui.bwRenderer.setSeriesPaint(4,  ThemeColors.OLD_GLORY_BLUE);       // read sample
        Gui.bwRenderer.setSeriesStroke(4, bold);
        Gui.bwRenderer.setSeriesPaint(5,  ThemeColors.OLD_GLORY_BLUE_FADE);  // read trend (dashed)
        Gui.bwRenderer.setSeriesStroke(5, dash);
        Gui.bwRenderer.setSeriesPaint(6,  ThemeColors.OLD_GLORY_BLUE_LIGHT); // read max
        Gui.bwRenderer.setSeriesPaint(7,  ThemeColors.OLD_GLORY_BLUE_DARK);  // read min

        Gui.msRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.msRenderer.setSeriesPaint(0, ThemeColors.CRIMSON);               // write latency
        Gui.msRenderer.setSeriesPaint(1, ThemeColors.OLD_GLORY_BLUE);        // read latency

        // Title, axes, and legend text in flagBlue (readable on white background)
        if (Gui.chart != null) Gui.chart.getTitle().setPaint(ThemeColors.OLD_GLORY_BLUE);
        if (Gui.bwAxis != null) {
            Gui.bwAxis.setLabelPaint(ThemeColors.OLD_GLORY_BLUE);
            Gui.bwAxis.setTickLabelPaint(ThemeColors.OLD_GLORY_BLUE);
            Gui.bwAxis.setTickMarkPaint(ThemeColors.OLD_GLORY_BLUE);
        }
        if (Gui.msAxis != null) {
            Gui.msAxis.setLabelPaint(ThemeColors.OLD_GLORY_BLUE);
            Gui.msAxis.setTickLabelPaint(ThemeColors.OLD_GLORY_BLUE);
            Gui.msAxis.setTickMarkPaint(ThemeColors.OLD_GLORY_BLUE);
        }
        if (Gui.sampleAxis != null) {
            Gui.sampleAxis.setLabelPaint(ThemeColors.OLD_GLORY_BLUE);
            Gui.sampleAxis.setTickLabelPaint(ThemeColors.OLD_GLORY_BLUE);
            Gui.sampleAxis.setTickMarkPaint(ThemeColors.OLD_GLORY_BLUE);
        }
        if (Gui.chart != null && Gui.chart.getLegend() != null) {
            Gui.chart.getLegend().setItemPaint(ThemeColors.OLD_GLORY_BLUE);
        }
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    /**
     * Restores the plot background to the default dark LAF-driven style.
     * Called by every palette that does not manage its own plot background
     * (i.e. all palettes except Beta and Old Glory), and also when switching
     * away from the Beta palette.
     */
    static void restoreDefaultPlotBackground() {
        if (Gui.chart == null) return;
        XYPlot plot = (XYPlot) Gui.chart.getPlot();
        plot.setBackgroundPaint(Color.DARK_GRAY.darker());
        plot.setOutlinePaint(Color.WHITE);
        // JFreeChart 1.x does not accept null paint; restore to a neutral grid color
        plot.setDomainGridlinePaint(new Color(80, 80, 80));
        plot.setRangeGridlinePaint(new Color(80, 80, 80));
        // Clear any per-series custom strokes (Beta dashed avg, Old Glory bold sample)
        if (Gui.bwRenderer != null) {
            Gui.bwRenderer.setSeriesStroke(0, null); // write sample - back to default
            Gui.bwRenderer.setSeriesStroke(1, null); // write trend
            Gui.bwRenderer.setSeriesStroke(4, null); // read sample - back to default
            Gui.bwRenderer.setSeriesStroke(5, null); // read trend
        }
    }
}