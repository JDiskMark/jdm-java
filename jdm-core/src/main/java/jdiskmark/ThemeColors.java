package jdiskmark;

import java.awt.Color;

/**
 * Named color constants shared across application themes and chart palettes.
 * <p>
 * Using named constants prevents color drift when the same logical color is
 * referenced in multiple methods or files, and makes the intent of each color
 * self-documenting without needing inline comments everywhere.
 * </p>
 * <p>
 * Hex-string variants (prefixed with {@code HEX_}) are provided for FlatLaf's
 * {@code setGlobalExtraDefaults()} which requires strings, not Color objects.
 * </p>
 */
public final class ThemeColors {

    // -----------------------------------------------------------------------
    // Old Glory (US Flag) palette
    // -----------------------------------------------------------------------

    /** Old Glory Blue (Pantone 282) - UI text, read series, selections, scrollbar. */
    public static final Color OLD_GLORY_BLUE       = new Color(0x3C3B6E);

    /** Old Glory Red (Pantone 193) - progress bar, tab underline, accent (checkboxes, focus). */
    public static final Color OLD_GLORY_RED        = new Color(0xB22234);

    /** Crimson - vivid red for write-sample and write-latency series. */
    public static final Color CRIMSON              = new Color(0xDC143C);

    /** Crimson at 170/255 alpha - write-trend dashed line. */
    public static final Color CRIMSON_FADE         = new Color(0xDC, 0x14, 0x3C, 170);

    /** Old Glory Blue at 170/255 alpha - read-trend dashed line. */
    public static final Color OLD_GLORY_BLUE_FADE  = new Color(0x3C, 0x3B, 0x6E, 170);

    /** Light crimson (#FF6B6B) - write-max series. */
    public static final Color CRIMSON_LIGHT        = new Color(0xFF6B6B);

    /** Dark red (#8B0000) - write-min series. */
    public static final Color CRIMSON_DARK         = new Color(0x8B0000);

    /** Periwinkle blue (#7878B4) - read-max series. */
    public static final Color OLD_GLORY_BLUE_LIGHT = new Color(0x7878B4);

    /** Deep navy (#1A1940) - read-min series. */
    public static final Color OLD_GLORY_BLUE_DARK  = new Color(0x1A1940);

    /** Badge background - pale lavender-blue (#EEF2FF). */
    public static final Color OLD_GLORY_BADGE_BG   = new Color(0xEEF2FF);

    /** Scrollbar thumb hover - slightly darker navy (#2B2A52). */
    public static final Color OLD_GLORY_BLUE_HOVER = new Color(0x2B2A52);

    /** Scrollbar thumb pressed - darkest navy (#1A1A38). */
    public static final Color OLD_GLORY_BLUE_PRESS = new Color(0x1A1A38);

    /** Tab hover highlight - very light blue (#ECEDF8); subtle tint over white background. */
    public static final Color OLD_GLORY_TAB_HOVER  = new Color(0xECEDF8);

    // ---- Hex-string variants for FlatLaf setGlobalExtraDefaults() ----------

    /** Hex string form of {@link #OLD_GLORY_BLUE} for FlatLaf key values. */
    public static final String HEX_OLD_GLORY_BLUE  = "#3C3B6E";

    /** Hex string form of {@link #OLD_GLORY_RED} for FlatLaf key values. */
    public static final String HEX_OLD_GLORY_RED   = "#B22234";

    // -----------------------------------------------------------------------

    private ThemeColors() { /* static constants only */ }
}