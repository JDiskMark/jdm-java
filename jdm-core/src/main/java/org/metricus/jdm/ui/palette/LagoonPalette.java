package org.metricus.jdm.ui.palette;

import java.awt.Color;
import org.metricus.jdm.ui.PaletteDefinition;

public final class LagoonPalette implements PaletteDefinition {
    @Override public Color bwWriteSample()  { return new Color(0x7C9CDC); }
    @Override public Color bwWriteTrend()   { return new Color(0x2A5CB0); }
    @Override public Color bwWriteMax()     { return new Color(0xBCD2EF); }
    @Override public Color bwWriteMin()     { return new Color(0xBFD5EA); }
    @Override public Color bwReadSample()   { return new Color(0xAACC00); }
    @Override public Color bwReadTrend()    { return new Color(0x008080); }
    @Override public Color bwReadMax()      { return new Color(0x6B8E23); }
    @Override public Color bwReadMin()      { return new Color(0x228B22); }
    @Override public Color msWriteLatency() { return new Color(0x7C9CDC); }
    @Override public Color msReadLatency()  { return new Color(0xAACC00); }
}
