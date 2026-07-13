package org.metricus.jdm.ui.palette;

import java.awt.Color;
import org.metricus.jdm.ui.PaletteDefinition;

public final class EmberPalette implements PaletteDefinition {
    @Override public Color bwWriteSample()  { return new Color(0xFFC107); }
    @Override public Color bwWriteTrend()   { return new Color(0xEBEBEB); }
    @Override public Color bwWriteMax()     { return new Color(0x4CAF50); }
    @Override public Color bwWriteMin()     { return new Color(0xFF5722); }
    @Override public Color bwReadSample()   { return new Color(0xE91E63); }
    @Override public Color bwReadTrend()    { return new Color(0xD3D3D3); }
    @Override public Color bwReadMax()      { return new Color(0x66BB6A); }
    @Override public Color bwReadMin()      { return new Color(0xF44336); }
    @Override public Color msWriteLatency() { return new Color(0xFFC107); }
    @Override public Color msReadLatency()  { return new Color(0xE91E63); }
}
