package org.metricus.jdm.ui.theme;

import java.awt.Color;
import jdiskmark.App;
import org.metricus.jdm.ui.ThemeDefinition;

public final class LightTheme implements ThemeDefinition {

    @Override
    public String lafClassName() {
        return App.isMacOs()
                ? "com.formdev.flatlaf.themes.FlatMacLightLaf"
                : "com.formdev.flatlaf.FlatLightLaf";
    }

    @Override public Color badgeDefaultBg() { return new Color(220, 220, 220, 200); }
    @Override public Color badgeDefaultFg() { return new Color(50, 50, 50); }
    @Override public Color badgeStaleBg()   { return new Color(0xE6, 0xA0, 0x1E); }
}
