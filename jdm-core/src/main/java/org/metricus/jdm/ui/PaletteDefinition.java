package org.metricus.jdm.ui;

import java.awt.Color;
import java.awt.Stroke;

/**
 * Contract for a chart colour palette.
 * <p>
 * Each implementation supplies the series colours for the bandwidth and
 * latency renderers, and optionally custom strokes, plot canvas styling,
 * and axis/title text paint.  {@link Palette#apply(PaletteDefinition)}
 * reads these values to configure the chart.
 * </p>
 */
public interface PaletteDefinition {

    // --- Required: bandwidth renderer series colours (8 series) ---
    Color bwWriteSample();
    Color bwWriteTrend();
    Color bwWriteMax();
    Color bwWriteMin();
    Color bwReadSample();
    Color bwReadTrend();
    Color bwReadMax();
    Color bwReadMin();

    // --- Required: latency renderer series colours (2 series) ---
    Color msWriteLatency();
    Color msReadLatency();

    // --- Optional: custom strokes (null = LAF default) ---
    default Stroke bwWriteSampleStroke() { return null; }
    default Stroke bwWriteTrendStroke()  { return null; }
    default Stroke bwReadSampleStroke()  { return null; }
    default Stroke bwReadTrendStroke()   { return null; }

    // --- Optional: plot canvas (null = restore default dark bg) ---
    default Color chartBackground()  { return null; }
    default Color plotBackground()   { return null; }
    default Color plotOutline()      { return null; }
    default Color gridColor()        { return null; }

    // --- Optional: legend (null = LAF default) ---
    default Color legendBackground() { return null; }
    default Color legendBorderColor(){ return null; }

    // --- Optional: text paint for axes, title, legend items ---
    default Color textPaint()        { return null; }
}
