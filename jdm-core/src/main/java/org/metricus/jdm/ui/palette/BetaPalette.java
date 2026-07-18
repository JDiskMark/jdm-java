package org.metricus.jdm.ui.palette;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import org.metricus.jdm.ui.PaletteDefinition;

public final class BetaPalette implements PaletteDefinition {

    private static final Stroke AVG_DASH = new BasicStroke(
            1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            10.0f, new float[]{6.0f, 6.0f}, 0.0f);

    @Override public Color plotBackground() { return new Color(0x1C1C1C); }
    @Override public Color plotOutline()     { return new Color(0x555555); }
    @Override public Color gridColor()       { return new Color(0x3A3A3A); }

    @Override public Color bwWriteSample()  { return new Color(0xE07B39); }
    @Override public Color bwWriteTrend()   { return new Color(189, 176, 138, 200); }
    @Override public Color bwWriteMax()     { return new Color(0xF5A623); }
    @Override public Color bwWriteMin()     { return new Color(0xC0623A); }
    @Override public Color bwReadSample()   { return new Color(0x4FC3F7); }
    @Override public Color bwReadTrend()    { return new Color(160, 216, 239, 200); }
    @Override public Color bwReadMax()      { return new Color(0x81D4FA); }
    @Override public Color bwReadMin()      { return new Color(0x0288D1); }
    @Override public Color msWriteLatency() { return new Color(0xE07B39); }
    @Override public Color msReadLatency()  { return new Color(0x4FC3F7); }

    @Override public Stroke bwWriteTrendStroke() { return AVG_DASH; }
    @Override public Stroke bwReadTrendStroke()  { return AVG_DASH; }
}
