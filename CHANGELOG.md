# Changelog

All notable changes to JDiskMark are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased] — v0.8.0

### Added
- [#16](https://github.com/JDiskMark/jdm-java/issues/16) macOS PKG installer — tyler

### Changed
- [#33](https://github.com/JDiskMark/jdm-java/issues/33) Maven build — lane/james

### In Progress
- [#70](https://github.com/JDiskMark/jdm-java/issues/70) app icon — ian
- [#78](https://github.com/JDiskMark/jdm-java/issues/78) throttle graphics render — val
- [#95](https://github.com/JDiskMark/jdm-java/issues/95) disk cache purging — val
- [#67](https://github.com/JDiskMark/jdm-java/issues/67) test portal uploads (no auth)
- [#117](https://github.com/JDiskMark/jdm-java/issues/117) user portal upload acknowledgement
- [#118](https://github.com/JDiskMark/jdm-java/issues/118) test interlock or OAuth upload

---

## [0.7.0] — 2026-03-06

### Added
- [#115](https://github.com/JDiskMark/jdm-java/issues/115) Flatpak installer
- [#44](https://github.com/JDiskMark/jdm-java/issues/44) GUI benchmark export — JSON, YAML, CSV
- [#130](https://github.com/JDiskMark/jdm-java/issues/130) Dark FlatLaf theme
- [#121](https://github.com/JDiskMark/jdm-java/issues/121) Common benchmark runner for CLI and GUI
  - CLI options for: profile, direct I/O, alignment
  - New profiles: Media Playback, Video Export, Photo Library
- [#111](https://github.com/JDiskMark/jdm-java/issues/111) Extract config model from benchmark

### Changed
- [#134](https://github.com/JDiskMark/jdm-java/issues/134) ZGC optimization
- [#131](https://github.com/JDiskMark/jdm-java/issues/131) Direct I/O enabled by default
- [#40](https://github.com/JDiskMark/jdm-java/issues/40) Resolve cross-platform GUI look and feel
- [#42](https://github.com/JDiskMark/jdm-java/issues/42) Replace `Custom Test` option with `profileModified` flag

### Fixed
- [#132](https://github.com/JDiskMark/jdm-java/issues/132) Fix read-only benchmarks

### Renamed
- [#67](https://github.com/JDiskMark/jdm-java/issues/67) Rename sample fields `bwt` → `bt`, `lat` → `lt`

---

## [0.6.3] — 2026-01-05

### Added
- [#15](https://github.com/JDiskMark/jdm-java/issues/15) Debian installer (Ubuntu)
- [#98](https://github.com/JDiskMark/jdm-java/issues/98) RPM installer (Red Hat)
- [#42](https://github.com/JDiskMark/jdm-java/issues/42) Default benchmark profiles
- [#69](https://github.com/JDiskMark/jdm-java/issues/69) Command line interface
- [#107](https://github.com/JDiskMark/jdm-java/issues/107) Sector-aligned / Direct I/O
- [#82](https://github.com/JDiskMark/jdm-java/issues/82) Drive access notification

### Fixed
- [#84](https://github.com/JDiskMark/jdm-java/issues/84) Processor info resolved for SP installs

### Changed
- [#73](https://github.com/JDiskMark/jdm-java/issues/73) Refactor benchmark data model; keyboard operation selection

---

## [0.6.2] — 2025-09-01

### Added
- [#64](https://github.com/JDiskMark/jdm-java/issues/64) Persist IOPS, write sync
- Allow concurrent version runs

### Changed
- Control panel moved to left
- Event tab swapped with disk location tab

---

## [0.6.1] — 2025-08-23 — Microsoft App Store Candidate

### Changed
- JDiskMark in title and MSI vendor name
- Remove "Average" from "Access Time" label

---

## [0.6.0] — 2025-08-10

### Added
- [#13](https://github.com/JDiskMark/jdm-java/issues/13) Detect drive info on startup
- [#10](https://github.com/JDiskMark/jdm-java/issues/10) IOPS reporting
- [#20](https://github.com/JDiskMark/jdm-java/issues/20) Threading and queue depth
- [#23](https://github.com/JDiskMark/jdm-java/issues/23) Delete selected benchmarks

### Fixed
- [#22](https://github.com/JDiskMark/jdm-java/issues/22) Foreign capacity reporting
- [#25](https://github.com/JDiskMark/jdm-java/issues/25) Linux crash; capacity with terabytes and exabytes

### Changed
- [#12](https://github.com/JDiskMark/jdm-java/issues/12) Updated look and feel (Windows)
- [#26](https://github.com/JDiskMark/jdm-java/issues/26) Lowercase project and jar name
- [#36](https://github.com/JDiskMark/jdm-java/issues/36) I/O Mode dropdown uses enum values for type safety
- Write sync default off

---

## [0.5.1]

### Added
- MSI installer available

### Fixed
- [#17](https://github.com/JDiskMark/jdm-java/issues/17) Invalid disk usage reported on Windows 10

---

## [0.5] — 2024-02-03

### Added
- Disk access time (ms) plotting
- Initial color palette options
- Admin/root indicator, architecture indicator
- [GH-2](https://github.com/JDiskMark/jdm-java/issues/2) Auto clear disk cache for combined write/read benchmarks
- [GH-6](https://github.com/JDiskMark/jdm-java/issues/6) Save and load benchmarks and graph series
- [GH-8](https://github.com/JDiskMark/jdm-java/issues/8) Used capacity and total capacity
- Report processor name

### Changed
- Updated for Java 21 LTS with NetBeans 20 environment
  (EclipseLink 4.0, JPA 3.1, ModelGen 5.6, Annotations 3.1, xml.bind 4.0)
- Default to 200 marks
- Time format updated to `yyyy-MM-dd HH:mm:ss`
- Increased drive information default column width to 170
- Replace display of transfer size with access time in run panel
- Break out actions into separate menu

### Fixed
- Replace `Date` with `LocalDateTime` to avoid deprecated `@Temporal`

---

## [0.4] — 2017-03-06

### Added
- Platform disk model info:
  - Windows: via PowerShell query
  - Linux: via `df /data/path` and `lsblk /dev/path --output MODEL`
  - macOS: via `df /data/path` and `diskutil info /dev/disk1`

### Changed
- Updated EclipseLink to 2.6 (allows auto schema update)
- Improved GUI initialization

---

## [0.3] — 2016-12-17

### Added
- Persist recent run with embedded Derby DB

### Changed
- Remove "transfer mark number" from graph
- Changed graph background to dark gray
- Resizing main frame stretches tabbed pane instead of empty panel

---

## [0.2] — 2016-10-24

### Added
- Auto-generate ZIP release (e.g. `jdiskmark-v0.2.zip`)
- Show recent runs (not persisted)
- Added tabbed pane near bottom to organize new controls

### Changed
- Format excessive decimal places
- Default to Nimbus look and feel

---

## [0.1] — 2016-03-21

- Initial release
