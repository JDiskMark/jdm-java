package org.metricus.jdm.ui;

/**
 * Canonical tab title strings for both the top ({@code mainTabPane}) and
 * bottom ({@code bottomTabPane}) tabbed panes.
 *
 * <p>Use these constants everywhere a tab title is compared or passed
 * programmatically so that a rename only ever requires changing one place.
 *
 * <p><b>Note:</b> The GEN-BEGIN/GEN-END block in {@code MainFrame.java}
 * contains hard-coded {@code addTab} calls for {@link #BOTTOM_BENCHMARKS} and
 * {@link #BOTTOM_EVENTS} that cannot reference these constants directly
 * (NetBeans owns those lines). Those two strings must match the literals in
 * the generated block — keep them in sync if either is ever renamed.
 */
public final class Tabs {

    private Tabs() {} // utility class — no instances

    // ── Top pane (mainTabPane) ────────────────────────────────────────────────

    /** Left-side top pane: drive info panel. */
    public static final String TOP_DRIVE     = "Drive";

    /** Left-side top pane: benchmark control + chart. */
    public static final String TOP_BENCHMARK = "Benchmark";

    /** Left-side top pane: SMART attribute display (Linux / macOS only). */
    public static final String TOP_SMART     = "SMART";

    // ── Bottom pane (bottomTabPane) ───────────────────────────────────────────

    /**
     * Bottom pane: benchmark history table.
     * <p><b>Must match</b> the hard-coded {@code addTab("Benchmarks", ...)}
     * call in the NetBeans GEN block of {@code MainFrame.java}.
     */
    public static final String BOTTOM_BENCHMARKS         = "Benchmarks";

    /** Bottom pane: benchmark history table when archive view is active. */
    public static final String BOTTOM_BENCHMARKS_ARCHIVE = "Archived Benchmarks";

    /**
     * Bottom pane: application event log.
     * <p><b>Must match</b> the hard-coded {@code addTab("Events", ...)}
     * call in the NetBeans GEN block of {@code MainFrame.java}.
     */
    public static final String BOTTOM_EVENTS             = "Events";

    /** Bottom pane: all-drives summary table. */
    public static final String BOTTOM_ALL_DRIVES         = "All Drives";

    /** Bottom pane: saved SMART snapshot reports (Linux / macOS only). */
    public static final String BOTTOM_SMART_REPORTS      = "SMART Reports";

    /** Bottom pane: community portal sharing settings. */
    public static final String BOTTOM_SHARING            = "Sharing";
}
