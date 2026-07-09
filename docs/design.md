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

# proposed Archived mode
Feature to archive a benchmark so it does not show up in the benchmark history but can be unarchived and brought back into history. Entering view archive mode allows seeing exclusively archived report and allows selecting and unarchiving or deleting them. Exiting archive mode returns to normal benchmark history view