package org.metricus.jdm.ui.palette;

import java.awt.Color;
import org.metricus.jdm.ui.PaletteDefinition;

public final class ClassicPalette implements PaletteDefinition {
    @Override public Color bwWriteSample()  { return Color.YELLOW; }
    @Override public Color bwWriteTrend()   { return Color.WHITE; }
    @Override public Color bwWriteMax()     { return Color.GREEN; }
    @Override public Color bwWriteMin()     { return Color.RED; }
    @Override public Color bwReadSample()   { return Color.ORANGE; }
    @Override public Color bwReadTrend()    { return Color.LIGHT_GRAY; }
    @Override public Color bwReadMax()      { return Color.GREEN.darker(); }
    @Override public Color bwReadMin()      { return Color.RED.darker(); }
    @Override public Color msWriteLatency() { return Color.CYAN; }
    @Override public Color msReadLatency()  { return Color.MAGENTA; }
}
