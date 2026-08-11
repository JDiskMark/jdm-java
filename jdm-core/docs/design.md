# Design Decisions

## Established GUI behavior
 
- the top section is the active benchmark being run or loaded from history
- the top-left section of the benchmark tab is called the settings controls
- the top-right section of the benchmark is the chart
- the bottom section is the historical list of runs and common status output

# Active design trades

- loading a benchmark 
    - updates:
        - active control settings
        - options/advanced options
    - running a benchmark immediately after a load will cause the next run to use the loaded benchmark's settings, options, and advanced options

- Benchmark Current/Results View Separation Option: an alternate approach is to separate the current benchmark view from the historical benchmark view so that changing the settings does not affect the loaded benchmark. This way on the active view highlights could be used to indicate deviation from the selected profile instead of indicating they differ from the saved benchmark. the loaded historical benchmark view would be immutable. this alternate approach could split the active benchmark view from a loaded/saved benchmark view tab

if expanded to SMART benchmark view:

Top Panel Tabs
- Drive - active configurable
- Benchmark - active configurable
- Benchmark Results - historical immutable
- SMART - active configurable
- SMART Results - historical immutable

Bottom Panel Tabs (common to all):
- Benchmarks -- linked to benchmark results view
- SMART Reports -- linked to SMART results view
- All Drives -- a unified view of all drives and their status
- Events
- Sharing -- controls to share benchmarks to community portal

# Package organization

New classes should be introduced in this structure:

org.metricus.jdm
  - core - benchmark algorithms
  - io - abstract api
  - io.win
  - io.mac
  - io.linux
  - cli - command line interface
  - ui - user interface, themes, palettes
  - util - sharing, logging, export, translation

## Proposed Mapping

**root** — App, EM

**core** — Benchmark, BenchmarkCallable, BenchmarkConfig, BenchmarkDriveInfo,
BenchmarkOperation, BenchmarkProfile, BenchmarkRunner, BenchmarkSystemInfo,
BenchmarkWorker, DiskUsageInfo, GcDetector, RenderFrequencyMode,
Sample, Smart, SmartSnapshot,
GcRetriedSamplesConverter, SampleAttributeConverter, LocalDateTimeAttributeConverter

**io** — DriveChecker, UtilOs (abstract interface)
**io.win / io.mac / io.linux** — platform implementations (split from UtilOs)

**cli** — Cli, RunBenchmarkCommand, VersionProvider

**ui** — Gui, MainFrame, BenchmarkPanel, BenchmarkControlPanel, DrivePanel,
SelectDriveFrame, AdvancedOptionsFrame, SmartPanel, SmartReportsPanel,
SharingPanel, PortalEnableDialog, OperationTableSelectionListener,
CenterTableCellRenderer, RightTableCellRenderer,
ChartPalette, ThemeColors, GraphPaletteMenu, GraphThemeMenu, AppIcon

**util** — Portal, Exporter, Util, RoundingSerializer


# 