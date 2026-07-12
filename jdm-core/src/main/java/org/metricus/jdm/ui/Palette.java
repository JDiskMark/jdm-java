package org.metricus.jdm.ui;

import java.awt.Color;
import jdiskmark.Gui;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;
import org.metricus.jdm.ui.palette.BetaPalette;
import org.metricus.jdm.ui.palette.ClassicPalette;
import org.metricus.jdm.ui.palette.EmberPalette;
import org.metricus.jdm.ui.palette.LagoonPalette;
import org.metricus.jdm.ui.palette.MarinePalette;

/**
 * Available chart colour palettes.
 * <p>
 * Each constant holds a {@link PaletteDefinition} that supplies the series
 * colours and optional canvas/stroke/text customisations.  The generic
 * {@link #apply(PaletteDefinition)} method reads these values and configures
 * the chart — palette classes never touch {@link Gui} fields directly.
 * </p>
 * <p>
 * {@link jdiskmark.GraphPaletteMenu} iterates {@code values()} to build the
 * menu automatically.
 * </p>
 */
public enum Palette {
    CLASSIC("Classic",  new ClassicPalette()),
    LAGOON("Lagoon",    new LagoonPalette()),
    MARINE("Marine",    new MarinePalette()),
    EMBER("Ember",      new EmberPalette()),
    BETA("Beta",        new BetaPalette());

    private final String displayName;
    private final PaletteDefinition definition;

    Palette(String displayName, PaletteDefinition definition) {
        this.displayName = displayName;
        this.definition = definition;
    }

    public String displayName() { return displayName; }

    public PaletteDefinition definition() { return definition; }

    /** Applies this palette's colour scheme to the chart renderers. */
    public void apply() {
        Gui.palette = this;
        apply(definition);
    }

    // -----------------------------------------------------------------------
    // Generic palette application
    // -----------------------------------------------------------------------

    /**
     * Configures the chart renderers, plot canvas, legend, and axis text
     * from the given {@link PaletteDefinition}.
     * <p>
     * Called by {@link #apply()} for user-selected palettes and by
     * {@link Theme#applyLinkedPalette()} for theme-linked palettes.
     * Theme-linked palettes bypass the enum so {@link Gui#palette} is
     * not overwritten.
     * </p>
     */
    public static void apply(PaletteDefinition def) {
        if (Gui.chart == null) return;

        // 1. Reset to defaults (clears old custom bg + strokes)
        restoreDefaultPlotBackground();

        // 2. Custom chart outer background
        if (def.chartBackground() != null) {
            Gui.chart.setBackgroundPaint(def.chartBackground());
        }

        // 3. Custom plot canvas
        if (def.plotBackground() != null) {
            XYPlot plot = (XYPlot) Gui.chart.getPlot();
            plot.setBackgroundPaint(def.plotBackground());
            if (def.plotOutline() != null) plot.setOutlinePaint(def.plotOutline());
            if (def.gridColor() != null) {
                plot.setDomainGridlinePaint(def.gridColor());
                plot.setRangeGridlinePaint(def.gridColor());
            }
        }

        // 4. Legend customisation (Write + Read rows)
        if (def.legendBackground() != null) {
            for (org.jfree.chart.title.LegendTitle leg :
                    new org.jfree.chart.title.LegendTitle[]{Gui.writeLegend, Gui.readLegend}) {
                if (leg == null) continue;
                leg.setBackgroundPaint(def.legendBackground());
                if (def.legendBorderColor() != null) {
                    leg.setFrame(new BlockBorder(def.legendBorderColor()));
                }
            }
        }

        // 5. Series paints
        Gui.bwRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.bwRenderer.setSeriesPaint(0, def.bwWriteSample());
        Gui.bwRenderer.setSeriesPaint(1, def.bwWriteTrend());
        Gui.bwRenderer.setSeriesPaint(2, def.bwWriteMax());
        Gui.bwRenderer.setSeriesPaint(3, def.bwWriteMin());
        Gui.bwRenderer.setSeriesPaint(4, def.bwReadSample());
        Gui.bwRenderer.setSeriesPaint(5, def.bwReadTrend());
        Gui.bwRenderer.setSeriesPaint(6, def.bwReadMax());
        Gui.bwRenderer.setSeriesPaint(7, def.bwReadMin());

        Gui.msRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
        Gui.msRenderer.setSeriesPaint(0, def.msWriteLatency());
        Gui.msRenderer.setSeriesPaint(1, def.msReadLatency());

        // 6. Custom strokes (null = keep default from restoreDefaultPlotBackground)
        if (def.bwWriteSampleStroke() != null)
            Gui.bwRenderer.setSeriesStroke(0, def.bwWriteSampleStroke());
        if (def.bwWriteTrendStroke() != null)
            Gui.bwRenderer.setSeriesStroke(1, def.bwWriteTrendStroke());
        if (def.bwReadSampleStroke() != null)
            Gui.bwRenderer.setSeriesStroke(4, def.bwReadSampleStroke());
        if (def.bwReadTrendStroke() != null)
            Gui.bwRenderer.setSeriesStroke(5, def.bwReadTrendStroke());

        // 7. Text paint (axes, title, legend items)
        Color tp = def.textPaint();
        if (tp != null) {
            Gui.chart.getTitle().setPaint(tp);
            if (Gui.bwAxis != null) {
                Gui.bwAxis.setLabelPaint(tp);
                Gui.bwAxis.setTickLabelPaint(tp);
                Gui.bwAxis.setTickMarkPaint(tp);
            }
            if (Gui.msAxis != null) {
                Gui.msAxis.setLabelPaint(tp);
                Gui.msAxis.setTickLabelPaint(tp);
                Gui.msAxis.setTickMarkPaint(tp);
            }
            if (Gui.sampleAxis != null) {
                Gui.sampleAxis.setLabelPaint(tp);
                Gui.sampleAxis.setTickLabelPaint(tp);
                Gui.sampleAxis.setTickMarkPaint(tp);
            }
            for (org.jfree.chart.title.LegendTitle leg :
                    new org.jfree.chart.title.LegendTitle[]{Gui.writeLegend, Gui.readLegend}) {
                if (leg != null) leg.setItemPaint(tp);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    /**
     * Restores the plot background to the default dark LAF-driven style.
     * Called by {@link #apply(PaletteDefinition)} before applying any
     * palette-specific customisations, and by
     * {@link Gui#updateChartPanelStyle()} when the LAF changes.
     */
    public static void restoreDefaultPlotBackground() {
        if (Gui.chart == null) return;
        XYPlot plot = (XYPlot) Gui.chart.getPlot();
        plot.setBackgroundPaint(Color.DARK_GRAY.darker());
        plot.setOutlinePaint(Color.WHITE);
        plot.setDomainGridlinePaint(new Color(80, 80, 80));
        plot.setRangeGridlinePaint(new Color(80, 80, 80));
        if (Gui.bwRenderer != null) {
            Gui.bwRenderer.setSeriesStroke(0, null);
            Gui.bwRenderer.setSeriesStroke(1, null);
            Gui.bwRenderer.setSeriesStroke(4, null);
            Gui.bwRenderer.setSeriesStroke(5, null);
        }
    }
}
