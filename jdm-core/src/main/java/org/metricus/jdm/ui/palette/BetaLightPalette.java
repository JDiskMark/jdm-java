package org.metricus.jdm.ui.palette;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import org.metricus.jdm.ui.PaletteDefinition;

public final class BetaLightPalette implements PaletteDefinition {

    private static final Stroke AVG_DASH = new BasicStroke(
            1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            10.0f, new float[]{6.0f, 6.0f}, 0.0f);

    @Override public Color plotBackground() { return new Color(0xF5F5F0); }
    @Override public Color plotOutline()     { return new Color(0xBBBBBB); }
    @Override public Color gridColor()       { return new Color(0xD8D8D0); }

    @Override public Color bwWriteSample()  { return new Color(0xE86000); }
    @Override public Color bwWriteTrend()   { return new Color(0xE8, 0x60, 0x00, 200); }
    @Override public Color bwWriteMax()     { return new Color(0xD4860A); }
    @Override public Color bwWriteMin()     { return new Color(0x9E3A18); }
    @Override public Color bwReadSample()   { return new Color(0x0277BD); }
    @Override public Color bwReadTrend()    { return new Color(0x02, 0x77, 0xBD, 200); }
    @Override public Color bwReadMax()      { return new Color(0x0288D1); }
    @Override public Color bwReadMin()      { return new Color(0x01579B); }
    @Override public Color msWriteLatency() { return new Color(0xE86000); }
    @Override public Color msReadLatency()  { return new Color(0x0277BD); }


    @Override public Stroke bwWriteTrendStroke() { return AVG_DASH; }
    @Override public Stroke bwReadTrendStroke()  { return AVG_DASH; }
}
