# Drive Direct Read

## Overview

When running as admin (elevated on Windows, root on Linux/macOS), JDiskMark can
read directly from the raw physical device, bypassing the filesystem entirely.
This eliminates the need to generate test files for read benchmarks.

**Status:** Planned  
**Platforms:** Windows, Linux, macOS

## Motivation

Current read benchmarks require a preparation phase that writes test files to
disk before measuring reads. This has several downsides:

1. **Disk wear** — test file writes consume write cycles (relevant for SSDs)
2. **Time** — the preparation phase doubles the total benchmark time for
   read-only runs
3. **Free-space dependency** — the target filesystem must have enough free space
   for the test data
4. **Filesystem caching** — even with cache drops, data just written may still
   be warm in page cache, skewing read results

Direct device reads avoid all of these problems by reading existing sectors
straight from the drive. The content is ignored (discarded immediately after
the read call); only throughput and latency are measured.

## Constraints

- **Admin-only.** Opening a raw device requires elevated privileges on all
  platforms. The feature is gated behind a runtime admin check — the
  `DRIVE_READ` radio button in Advanced Options is **disabled (greyed out)**
  when the app is not running as admin. Privilege escalation (e.g. launching a
  helper via `sudo` or UAC) is out of scope for v1.

- **Read-only.** This mode never writes to the device. Writes in
  `DRIVE_READ` mode use the normal file-based path (i.e. a `READ_WRITE`
  benchmark would write test files then read from the device, but in practice
  the primary use case is `READ`-only).

- **Random read boundary.** Random seeks are bounded to the first 90% of the
  reported device capacity to avoid any risk of reading beyond the end of the
  physical media (some drives report slightly different geometry to the OS).

## Design

### Architecture — DriveReader class

All raw-device logic is encapsulated in a single `DriveReader.java` class.
This keeps the existing `Sample` and `BenchmarkRunner` classes clean and
makes the feature easy to maintain, test, or disable.

```
DriveReader.java
├── isRunningAsAdmin()         — admin/root privilege check
├── resolveDevicePath(Path)    — test dir → raw device path
├── getDeviceSizeBytes(String) — device capacity query
└── measureRead(Sample, ...)   — timed raw-device read loop
```

Touch points in existing files are minimal:
- `App.IoEngine` — new enum value (1 line)
- `BenchmarkConfig` — new `devicePath` field (3 lines)
- `App.getConfig()` — populate `devicePath` (2 lines)
- `BenchmarkRunner` — delegate to `DriveReader` in switch, skip read prep (5 lines)
- `AdvancedOptionsFrame` — radio button gated by admin check

### IoEngine Enum

A new enum value `DRIVE_READ` is added to `App.IoEngine`:

```java
public enum IoEngine {
    MODERN("Modern (FFM API)"),
    LEGACY("Legacy (RandomAccessFile)"),
    DRIVE_READ("Drive Read (Raw Device)");
    ...
}
```

### BenchmarkConfig Field

A new `devicePath` field on `BenchmarkConfig` stores the resolved raw device
path (e.g. `\\.\PhysicalDrive0`, `/dev/sda`, `/dev/rdisk0`). It is `null`
when not using `DRIVE_READ` mode.

### DriveReader — Admin Detection

`DriveReader.isRunningAsAdmin()` checks if the JVM is running with elevated
privileges:

- **Windows:** Runs `net session` and checks for a zero exit code (succeeds
  only with admin rights)
- **Linux/macOS:** Checks `System.getProperty("user.name").equals("root")`

The result is cached at first call since privileges don't change during a
JVM session.

### DriveReader — Device Path Resolution

`DriveReader.resolveDevicePath(Path dataDir)` resolves the test directory
to the underlying raw device path:

| Platform | Resolution | Device path format |
|---|---|---|
| Windows | `getDriveLetterWindows()` → `getPhysicalDriveNumberWindows()` | `\\.\PhysicalDriveN` |
| Linux | `getPartitionFromFilePathLinux()` → strip partition suffix | `/dev/sdX` or `/dev/nvmeNnN` |
| macOS | `getPartitionFromFilePathLinux()` (same `df` approach) → replace `disk` with `rdisk` | `/dev/rdiskN` |

### DriveReader — Device Size Query

`DriveReader.getDeviceSizeBytes(String devicePath)` returns the total byte
size of the device:

- **Linux/macOS:** `blockdev --getsize64 <devicePath>` (requires root)
- **Windows:** Opens `FileChannel` on `\\.\PhysicalDriveN` and calls `size()`,
  falls back to PowerShell `(Get-Disk -Number N).Size`

The usable range for random reads is `deviceSize * 0.9` (90%).

### DriveReader — measureRead()

`DriveReader.measureRead(Sample sample, long blockSize, int numBlocks, BenchmarkRunner bRunner)`:

1. Opens the raw device path via `FileChannel.open(Path.of(devicePath), READ)`
   with optional `ExtendedOpenOption.DIRECT` if direct IO is enabled
2. Allocates a sector-aligned `MemorySegment` via the FFM Arena API
3. For each block in the sample:
   - Computes byte offset (sequential or random within 90% of device size)
   - Reads `blockSize` bytes from the device channel
   - Discards the data (the segment is reused)
   - Updates read progress
4. Computes bandwidth and access time
5. Populates `sample.bwMbSec` and `sample.accessTimeMs`

Includes fallback logic: if direct IO open/read fails, retries without
`ExtendedOpenOption.DIRECT`.

### BenchmarkRunner Integration

In `BenchmarkRunner.execute()`:

- When `config.isDriveRead()` and the benchmark type is `READ`:
  - Skip `runReadPreparation()` entirely (no `wUnitsTotal` for prep)
  - Skip `listener.attemptCacheDrop()` (direct device reads bypass cache)
- In `runOperation()`, the `ioAction` switch adds a `DRIVE_READ` arm for
  `READ` mode that delegates to `DriveReader.measureRead()`
- For `WRITE` mode with `DRIVE_READ` engine: falls back to the `MODERN`
  engine write path (never writes to raw devices)

### GUI — AdvancedOptionsFrame

- A third `JRadioButton` is added: "Drive Read (Raw Device)"
- **When not admin:** the radio is disabled and its tooltip reads
  "Requires admin/root privileges"
- **When admin:** the radio is fully enabled and selectable
- Selecting it calls `applyIoEngine(App.IoEngine.DRIVE_READ)`
- `syncFromModel()` is updated to handle the new enum value

### Progress Bar

For `DRIVE_READ` + `READ`-only benchmarks, there is no write preparation phase.
The progress denominator is simply `blocksPerPhase` (just the read phase),
making the progress bar linear from 0-100% during the read.

## File Change Summary

| File | Change |
|---|---|
| **[NEW]** `DriveReader.java` | Encapsulates admin detection, device path resolution, device size query, and raw-device read measurement |
| `App.java` | Add `DRIVE_READ` to `IoEngine` enum; populate `devicePath` in `getConfig()` |
| `BenchmarkConfig.java` | Add `String devicePath` field with getter/setter; add `isDriveRead()` |
| `BenchmarkRunner.java` | Wire `DRIVE_READ` into IO action switch; skip read prep and cache drop |
| `AdvancedOptionsFrame.java` | Add `DRIVE_READ` radio button, disabled when not admin |

## Verification

1. Build: `mvn clean install -pl jdm-core -am --no-transfer-progress`
2. Manual test on Windows (elevated cmd): run a READ benchmark with DRIVE_READ
   engine, confirm no test files are created, confirm bandwidth/latency reported
3. Non-admin: confirm the DRIVE_READ radio is disabled/greyed out
4. READ_WRITE mode with DRIVE_READ: confirm writes use file-based path, reads
   use raw device

## Future Work

- Privilege escalation (UAC / pkexec / sudo helper) to enable drive read for
  non-admin users
- Direct device writes (destructive — would need strong warnings)
- SMART correlation: auto-fetch SMART before/after drive-read benchmarks
