package org.metricus.jdm.ui;

/**
 * Central repository for Start/Cancel button FlatLaf style strings.
 * <p>
 * Themes can override {@link ThemeDefinition#startButtonStyle()} to return
 * their own style string; callers that receive {@code null} should fall back
 * to {@link #DEFAULT_START}.
 * </p>
 */
public final class ButtonStyles {

    // -------------------------------------------------------------------------
    // Start button — GitHub green (default)
    // -------------------------------------------------------------------------
    private static final String GH_GREEN        = "#1f883d";
    private static final String GH_GREEN_HOVER  = "#1a7f37";
    private static final String GH_GREEN_PRESS  = "#196c2e";
    private static final String GH_GREEN_FOCUS  = "#1f883d88";

    /**
     * Default Start-button style: GitHub-inspired green, white bold text,
     * green-tinted focus ring so the blue OS default ring doesn't appear.
     */
    public static final String DEFAULT_START = buildStyle(
            GH_GREEN, GH_GREEN_HOVER, GH_GREEN_PRESS, GH_GREEN_FOCUS, "#ffffff");

    // -------------------------------------------------------------------------
    // Cancel button — amber/orange (universal "in-progress, click to stop")
    // -------------------------------------------------------------------------
    private static final String AMBER        = "#e67e22";
    private static final String AMBER_HOVER  = "#d35400";
    private static final String AMBER_PRESS  = "#ba4a00";
    private static final String AMBER_FOCUS  = "#e67e2288";

    /**
     * Cancel-button style: amber/orange, white bold text.
     * Applied whenever the button text changes to "Cancel".
     */
    public static final String CANCEL = buildStyle(
            AMBER, AMBER_HOVER, AMBER_PRESS, AMBER_FOCUS, "#ffffff");

    // -------------------------------------------------------------------------
    // Factory helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a FlatLaf {@code FlatLaf.style} string for a solid-colour button.
     *
     * @param bg         normal background hex
     * @param hover      hover background hex
     * @param press      pressed background hex
     * @param focusColor semi-transparent focus-ring hex (e.g. {@code "#1f883d88"})
     * @param fg         foreground (text) hex
     */
    public static String buildStyle(
            String bg, String hover, String press, String focusColor, String fg) {
        return "background: " + bg + "; " +
               "foreground: " + fg + "; " +
               "hoverBackground: " + hover + "; " +
               "pressedBackground: " + press + "; " +
               "focusedBackground: " + bg + "; " +
               "focusColor: " + focusColor + "; " +
               "font: bold +1";
    }

    private ButtonStyles() {}
}
