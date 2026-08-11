# JDiskMark Benchmark Profiles

This document specifies the benchmark profiles supported by JDiskMark: the
existing JDiskMark built-in profiles (Section 1) and the proposed
CrystalDiskMark-compatible profiles (Section 2). It also covers data model
changes, execution model, queue depth emulation, and UI/CLI presentation.

---

## 1. JDiskMark Built-in Profiles

> **TODO** — Populate this section with the specification for the eight existing
> `BenchmarkProfile` entries. For now the authoritative source is
> `BenchmarkProfile.java` and each profile's constructor parameters.

| Profile enum           | Display name            | Type       | Seq/Rand   | Threads | Samples | Blocks | Block KB | Direct | wSync | MultiFile |
|------------------------|-------------------------|------------|------------|---------|---------|--------|----------|--------|-------|-----------|
| `QUICK_TEST`           | Quick Test              | Read+Write | Sequential | 1       | 50      | 32     | 1024     | ✓      |       |           |
| `MAX_THROUGHPUT`       | Max Throughput          | Read+Write | Sequential | 1       | 100     | 256    | 1024     | ✓      |       |           |
| `HIGH_LOAD_RANDOM_T32` | Random 4K (T32)         | Read+Write | Random     | 32      | 200     | 128    | 4        | ✓      |       | ✓         |
| `LOW_LOAD_RANDOM_T1`  | Random 4K (T1)          | Read+Write | Random     | 1       | 150     | 64     | 4        | ✓      |       |           |
| `MAX_WRITE_STRESS`    | Max Write Stress (T4)   | Write      | Sequential | 4       | 250     | 512    | 512      | ✓      | ✓     | ✓         |
| `MEDIA_PLAYBACK`      | Media Playback          | Read       | Sequential | 1       | 160     | 64     | 2048     | ✓      |       |           |
| `VIDEO_EXPORTING`     | Video Exporting         | Write      | Sequential | 4       | 500     | 128    | 1024     | ✓      |       |           |
| `PHOTO_LIBRARY`       | Photo Library           | Read       | Random     | 8       | 1000    | 8      | 128      | ✓      |       | ✓         |

---

## 2. CrystalDiskMark-Compatible Profiles

### 2.1 Background — What CrystalDiskMark Tests

CrystalDiskMark (CDM) is a de-facto standard for consumer and enthusiast
storage benchmarking. Its default NVMe SSD preset runs **four named tests**,
each for both Read and Write (producing **8 operations** total):

| Row | CDM Label      | Pattern    | Queue Depth (QD) | Threads (T) | Block Size | Test File |
|-----|----------------|------------|------------------|-------------|------------|-----------|
| 1   | SEQ1M Q8T1     | Sequential | 8                | 1           | 1 MiB      | 1 GiB     |
| 2   | SEQ1M Q1T1     | Sequential | 1                | 1           | 1 MiB      | 1 GiB     |
| 3   | RND4K Q32T1    | Random     | 32               | 1           | 4 KiB      | 1 GiB     |
| 4   | RND4K Q1T1     | Random     | 1                | 1           | 4 KiB      | 1 GiB     |

**Primary metric:** MB/s (megabytes per second throughput).  
**Secondary metric:** IOPS (derived: `MB/s × 1,000,000 / blockSizeBytes`).

Each test runs for **5 passes** (CDM default); the reported value is the
**best** (maximum) of the 5 passes.

CDM also offers extended presets that add additional rows:

| Row | CDM Label            | Pattern    | QD | T  | Block Size | Test File | Notes |
|-----|----------------------|------------|----|----|------------|-----------|-------|
| 5   | RND4K Q1T1 (SB)     | Random     | 1  | 1  | 4 KiB      | 16 MiB    | Software Buffer — cached path |
| 6   | SEQ128K Q32T1        | Sequential | 32 | 1  | 128 KiB    | 1 GiB     | |
| 7   | RND4K Q32T16         | Random     | 32 | 16 | 4 KiB      | 1 GiB     | Multi-threaded saturated IOPS |

> **Row 5 ("SB")** uses a tiny 16 MiB test file that fits in the OS page
> cache with buffered I/O (no Direct I/O), intentionally measuring the
> **cached** read/write path rather than direct drive performance.

### 2.2 Mapping CDM Concepts to JDiskMark

| CDM concept           | JDiskMark equivalent                                       | Status  |
|-----------------------|------------------------------------------------------------|---------| 
| Block size            | `BenchmarkOperation.blockSize` (bytes)                     | Exists  |
| Threads (T)           | `BenchmarkOperation.numThreads`                            | Exists  |
| Queue Depth (QD)      | `BenchmarkOperation.queueDepth` (NEW)                      | Proposed|
| Sequential / Random   | `BenchmarkOperation.blockOrder`                            | Exists  |
| Direct I/O (no cache) | `BenchmarkConfig.directIoEnabled`                          | Exists  |
| Test file size        | `BenchmarkConfig.testFileSizeMb` (NEW), or `numBlocks × blockSize` | Proposed|
| MB/s throughput       | `BenchmarkOperation.bwAvg`                                 | Exists  |
| IOPS                  | `BenchmarkOperation.iops`                                  | Exists  |
| Access time / latency | `BenchmarkOperation.accAvg` (ms)                           | Exists  |

### 2.3 Queue Depth — Emulation Without Native Code

CrystalDiskMark uses OS-level async I/O (`OVERLAPPED` on Windows,
`io_submit`/`io_uring` on Linux) to issue multiple I/O requests simultaneously
from a single thread to the storage controller. This is what CDM calls
"queue depth".

JDiskMark can emulate queue depth in pure Java using two approaches:

#### Strategy A — `AsynchronousFileChannel` (recommended)

`java.nio.channels.AsynchronousFileChannel` is a standard JDK API (since Java 7)
that provides true OS-level async I/O without any native code:

- On **Windows**, it is backed by I/O Completion Ports (IOCP) — the same kernel
  mechanism that CDM's `OVERLAPPED` I/O uses.
- On **Linux**, the JDK uses an internal thread pool to dispatch concurrent
  reads/writes, which still results in multiple in-flight I/O requests to the
  block device.

To emulate QD=N from T=1:

```java
AsynchronousFileChannel afc = AsynchronousFileChannel.open(path, options);
List<Future<Integer>> pending = new ArrayList<>(queueDepth);

for (int b = 0; b < numOfBlocks; b++) {
    // Submit an async I/O operation
    long offset = computeOffset(b, blockSize, blockOrder, numOfBlocks);
    Future<Integer> f = afc.write(buffer.duplicate(), offset);
    pending.add(f);

    // When queue is full, wait for the oldest request to complete
    if (pending.size() >= queueDepth) {
        pending.removeFirst().get();   // blocks until the oldest I/O completes
    }
}
// Drain remaining
for (Future<Integer> f : pending) { f.get(); }
```

This approach:
- Uses standard JDK APIs — no JNI, no native libraries.
- Is compatible with `ExtendedOpenOption.DIRECT` for O_DIRECT / unbuffered I/O.
- Correctly models the CDM concept: N in-flight I/O operations on one file from
  one thread.
- On Windows, the kernel dispatches all N requests to the NVMe driver's
  submission queue, giving true hardware-level queue depth.

**Compatibility with existing I/O code:** The current `Sample.measureWrite()`
and `Sample.measureRead()` methods use synchronous `FileChannel`. For QD=1 the
behavior is identical. For QD>1 a parallel code path using
`AsynchronousFileChannel` would be added, gated by `config.queueDepth > 1`.

#### Strategy B — Virtual Threads (supplementary)

Java 21+ virtual threads can simulate high thread counts with minimal overhead:

```java
try (var scope = StructuredTaskScope.open()) {
    for (int i = 0; i < queueDepth; i++) {
        scope.fork(() -> { /* synchronous I/O at offset_i */ });
    }
    scope.join();
}
```

This is conceptually simpler but does not guarantee that I/O requests hit the
NVMe submission queue simultaneously. It is suitable as a fallback but is
**not the recommended primary strategy**.

#### Recommendation

Use `AsynchronousFileChannel` for QD emulation. It provides the closest
behavioral match to CDM's `OVERLAPPED` API without any native code dependency.

### 2.4 CDM as a Single Benchmark Entity

A CDM run should be modelled as **one `Benchmark` entity** containing multiple
`BenchmarkOperation` children — not as separate `Benchmark` rows per test. This
preserves the relationship between the tests (they were run together on the
same drive at the same time) and enables the CDM grid display.

#### CDM Default — 8 operations (4 rows × Read + Write)

The `CDM_DEFAULT` profile runs the standard 4 CDM tests. Each test produces
a Write operation and a Read operation, for **8 `BenchmarkOperation` rows**:

| Op # | CDM Row | IOMode | blockOrder | blockSize | numThreads | queueDepth | directIo | Notes          |
|------|---------|--------|------------|-----------|------------|------------|----------|----------------|
| 1    | 1       | WRITE  | SEQ        | 1 MiB     | 1          | 8          | true     | SEQ1M Q8T1     |
| 2    | 1       | READ   | SEQ        | 1 MiB     | 1          | 8          | true     | SEQ1M Q8T1     |
| 3    | 2       | WRITE  | SEQ        | 1 MiB     | 1          | 1          | true     | SEQ1M Q1T1     |
| 4    | 2       | READ   | SEQ        | 1 MiB     | 1          | 1          | true     | SEQ1M Q1T1     |
| 5    | 3       | WRITE  | RANDOM     | 4 KiB     | 1          | 32         | true     | RND4K Q32T1    |
| 6    | 3       | READ   | RANDOM     | 4 KiB     | 1          | 32         | true     | RND4K Q32T1    |
| 7    | 4       | WRITE  | RANDOM     | 4 KiB     | 1          | 1          | true     | RND4K Q1T1     |
| 8    | 4       | READ   | RANDOM     | 4 KiB     | 1          | 1          | true     | RND4K Q1T1     |

> Note: All default CDM tests use T=1 (single thread). Queue depth is the
> sole parallelism mechanism. This is the distinguishing characteristic of CDM
> vs. JDiskMark's existing multi-thread profiles.

#### CDM Extended — 10+ operations

The `CDM_EXTENDED` profile adds rows from CDM's extended presets. At minimum
it adds the SB test (row 5), producing **10 operations**. It may also include
the SEQ128K Q32T1 and RND4K Q32T16 tests from CDM's "All" preset:

| Op # | CDM Row | IOMode | blockOrder | blockSize | numThreads | queueDepth | directIo | Notes            |
|------|---------|--------|------------|-----------|------------|------------|----------|------------------|
| 1–8  | 1–4     |        |            |           |            |            |          | (same as default)|
| 9    | 5       | WRITE  | RANDOM     | 4 KiB     | 1          | 1          | false    | RND4K Q1T1 (SB)  |
| 10   | 5       | READ   | RANDOM     | 4 KiB     | 1          | 1          | false    | RND4K Q1T1 (SB)  |

Each operation stores its own complete configuration (`blockOrder`, `blockSize`,
`numThreads`, `queueDepth`, etc.) so the varying parameters across CDM rows
are fully captured at the operation level even though they share a single parent
`Benchmark`.

#### Per-operation configuration capture

The `BenchmarkOperation` entity already stores per-operation parameters. With
the addition of `queueDepth` and `directIoEnabled` on the operation (see §2.6),
each operation is fully self-describing:

```
BenchmarkOperation
├── ioMode           (READ / WRITE)
├── blockOrder       (SEQUENTIAL / RANDOM)
├── blockSize        (bytes)
├── numBlocks        (blocks per sample — controls test file region)
├── numSamples       (passes — CDM default is 5)
├── numThreads       (T value)
├── queueDepth       (QD value) ← NEW
├── directIoEnabled  (true for direct, false for SB) ← NEW
├── writeSyncEnabled (true for rwd mode)
├── txSize           (total bytes per sample)
├── bwAvg / bwMin / bwMax  (results — MB/s)
├── accAvg           (result — latency ms)
├── iops             (result — I/O operations per second)
└── samples[]        (per-sample measurement data)
```

### 2.5 CDM Profile Definition

Add two CDM-compatible entries to `BenchmarkProfile`: a default and an extended.
These are meta-profiles — their primary purpose is to carry the `CdmRow` list
that defines the multi-row test suite.

```java
/**
 * Defines one row (test) within a CDM-style multi-row benchmark run.
 * Each row produces a Write and a Read BenchmarkOperation.
 */
public record CdmRow(
    String label,              // "SEQ1M Q8T1"
    BlockSequence blockOrder,  // SEQUENTIAL or RANDOM
    int blockSizeKb,           // 1024 (1 MiB) or 4 (4 KiB)
    int numThreads,            // T value
    int queueDepth,            // QD value
    int numSamples,            // passes (default 5)
    int numBlocks,             // blocks per sample
    boolean directIo,          // true = O_DIRECT, false = buffered (SB)
    boolean multiFile          // one file per thread or single file
) {}
```

The CDM Default rows (4 tests, 8 operations):

```java
static final List<CdmRow> CDM_DEFAULT_ROWS = List.of(
    new CdmRow("SEQ1M Q8T1",   SEQUENTIAL, 1024, 1, 8,  5, 1024, true,  false),
    new CdmRow("SEQ1M Q1T1",   SEQUENTIAL, 1024, 1, 1,  5, 1024, true,  false),
    new CdmRow("RND4K Q32T1",  RANDOM,     4,    1, 32, 5, 256,  true,  false),
    new CdmRow("RND4K Q1T1",   RANDOM,     4,    1, 1,  5, 64,   true,  false)
);
```

The CDM Extended rows (adds SB test for 5 tests, 10 operations):

```java
static final List<CdmRow> CDM_EXTENDED_ROWS = List.of(
    new CdmRow("SEQ1M Q8T1",       SEQUENTIAL, 1024, 1, 8,  5, 1024, true,  false),
    new CdmRow("SEQ1M Q1T1",       SEQUENTIAL, 1024, 1, 1,  5, 1024, true,  false),
    new CdmRow("RND4K Q32T1",      RANDOM,     4,    1, 32, 5, 256,  true,  false),
    new CdmRow("RND4K Q1T1",       RANDOM,     4,    1, 1,  5, 64,   true,  false),
    new CdmRow("RND4K Q1T1 (SB)",  RANDOM,     4,    1, 1,  5, 4096, false, false)
);
```

### 2.6 Data Model Changes

#### New columns on `BenchmarkOperation`

| Column             | Type      | Default | Purpose |
|--------------------|-----------|---------|---------|
| `queueDepth`       | `int`     | `1`     | Async I/O queue depth per thread. QD=1 means synchronous (current behavior). |
| `directIoEnabled`  | `Boolean` | `null`  | Whether O_DIRECT was used for this specific operation. Currently only stored on `BenchmarkConfig`; needed per-operation for CDM since the SB test differs from the other rows. |
| `cdmRowLabel`      | `String`  | `null`  | CDM row label (e.g. "SEQ1M Q8T1"). `null` for non-CDM benchmarks. Used by the UI to reconstruct the CDM grid. |

```java
// In BenchmarkOperation.java

@Column
int queueDepth = 1;
public int getQueueDepth() { return queueDepth; }
public void setQueueDepth(int qd) { this.queueDepth = qd; }

@Column
Boolean directIoEnabled;
public Boolean getDirectIoEnabled() { return directIoEnabled; }
public void setDirectIoEnabled(Boolean b) { this.directIoEnabled = b; }

@Column
String cdmRowLabel;
public String getCdmRowLabel() { return cdmRowLabel; }
public void setCdmRowLabel(String label) { this.cdmRowLabel = label; }
```

#### New field on `BenchmarkConfig`

| Column              | Type      | Default | Purpose |
|---------------------|-----------|---------|---------|
| `queueDepth`        | `int`     | `1`     | Propagated to operations at creation time. |
| `testFileSizeMb`    | `int`     | `0`     | Optional. Explicit test file size; 0 = derive from `numBlocks × blockSize`. |

#### `BenchmarkProfile` constructor changes

Add `queueDepth` parameter after `numThreads`. Existing profiles pass `1`
(synchronous — no behavior change). The profile category field is also added:

```java
public enum ProfileCategory { JDM_BUILT_IN, CDM_COMPATIBLE }
```

#### Derby schema impact

All new columns have defaults, so existing Derby databases auto-migrate via
Hibernate's `update` strategy. No manual DDL or migration script is needed.

### 2.7 Execution Model Changes

#### Multi-row CDM execution in `BenchmarkRunner`

Currently `BenchmarkRunner.execute()` runs a single Write and/or Read operation
pair from one `BenchmarkConfig`. For CDM, the runner needs to iterate over
multiple configurations (one per CDM row).

Proposed approach — add a new method alongside `execute()`:

```java
/**
 * Executes a multi-row CDM-style benchmark.
 * Creates one BenchmarkOperation per (row × ioMode) and runs them sequentially.
 */
public Benchmark executeCdm(List<CdmRow> rows) throws Exception {
    Benchmark benchmark = new Benchmark(config);  // parent config
    mapEnvironment(benchmark, ...);
    benchmark.recordStartTime();

    for (CdmRow row : rows) {
        // Apply row-specific config
        BenchmarkConfig rowConfig = row.toConfig(config);  // inherits testDir, etc.

        // Write phase
        runOperation(benchmark, IOMode.WRITE, rowConfig);
        listener.onOperationComplete();

        // Cache drop between write and read
        if (rowConfig.directIo) { listener.attemptCacheDrop(); }

        // Read phase
        runOperation(benchmark, IOMode.READ, rowConfig);
        listener.onOperationComplete();
    }

    benchmark.recordEndTime();
    return benchmark;
}
```

Each call to `runOperation` creates and populates one `BenchmarkOperation` with
that row's specific parameters (block size, threads, QD, direct I/O flag, etc.).

### 2.8 Result Display and Historical Benchmark Tab

#### CDM results grid (primary display)

When a CDM benchmark is loaded (either after running or from history), the UI
should display the signature CDM grid.

CDM Default (4 rows):

```
+--------------------------------------------------------+
| JDiskMark — CrystalDiskMark Compatible Results         |
| Drive: Samsung 990 Pro 2TB     Test file: 1 GiB        |
| CPU: AMD Ryzen 9 7950X         OS: Windows 11           |
+-------------------+------------------+-----------------+
| Test              | Read (MB/s)      | Write (MB/s)    |
+-------------------+------------------+-----------------+
| SEQ1M  Q8  T1     |       7,321.45   |      6,892.10   |
| SEQ1M  Q1  T1     |       6,108.77   |      6,301.44   |
| RND4K  Q32 T1     |         912.33   |        799.88   |
| RND4K  Q1  T1     |          72.44   |        214.67   |
+-------------------+------------------+-----------------+
```

CDM Extended (5 rows — adds SB):

```
| RND4K  Q1T1 (SB)  |      11,042.00   |      4,788.00   |
```

An IOPS toggle shows the secondary view:

```
| Test              | Read (IOPS)      | Write (IOPS)    |
| RND4K  Q32 T1     |       222,754    |      195,288    |
| RND4K  Q1  T1     |        17,685    |       52,410    |
```

#### Separate tab for CDM results

The CDM grid should be displayed in a **dedicated sub-tab within the Benchmark
tab area**, separate from the existing chart view. Recommended tab layout:

```
┌─────────┬────────────────┬──────────────┐
│ Chart   │ CDM Results    │              │
└─────────┴────────────────┴──────────────┘
```

- **Chart tab** — the existing time-series chart for JDiskMark built-in profiles.
  Also used to display individual CDM operations if the user clicks a row in the
  CDM grid.
- **CDM Results tab** — the 4×2 (or 5×2) grid view. Shown when a CDM benchmark
  is selected from history. Contains the grid, drive info, and metadata. Each
  cell is clickable to drill down into the per-sample chart on the Chart tab.

When a user selects a historical benchmark from the bottom panel:
- If the benchmark's profile is `CDM_DEFAULT` or `CDM_EXTENDED` → switch to the
  CDM Results tab and populate the grid by matching operation pairs via
  `cdmRowLabel`.
- If the benchmark's profile is a built-in JDM profile → switch to the Chart
  tab and display the time-series as today.

#### Reconstructing the CDM grid from stored operations

To build the grid from a loaded `Benchmark`, group its operations by
`cdmRowLabel` and pair them by `ioMode`:

```java
Map<String, Map<IOMode, BenchmarkOperation>> grid = new LinkedHashMap<>();
for (BenchmarkOperation op : benchmark.getOperations()) {
    grid.computeIfAbsent(op.getCdmRowLabel(), k -> new EnumMap<>(IOMode.class))
        .put(op.getIoMode(), op);
}
// Grid is now: { "SEQ1M Q8T1" → { WRITE → op1, READ → op2 }, ... }
```

The display order follows `CDM_DEFAULT_ROWS` label ordering.

### 2.9 CLI Support

Extend `RunBenchmarkCommand` with:

```
jdm bench --cdm              # run CDM Default (4 rows, 8 operations), print grid
jdm bench --cdm-extended     # run CDM Extended (5 rows, 10 operations), print grid
jdm bench --cdm --type READ  # read-only CDM Default (4 operations)
```

After execution, print the CDM grid to stdout (same format as §2.8).

### 2.10 Accuracy Notes and Known Limitations

| CDM feature                 | JDiskMark status                             | Mitigation |
|-----------------------------|----------------------------------------------|------------|
| Async I/O (OVERLAPPED)      | `AsynchronousFileChannel` — standard JDK API | Use for QD>1; gated by `queueDepth` config |
| Precise 1 GiB test file     | Approximated by `numBlocks × blockSize`      | `testFileSizeMb` config pin |
| Best-of-5 scoring           | Current: reports average. CDM: reports best   | Add reporting mode toggle  |
| Per-sample IOPS histogram   | Not stored (aggregate IOPS only)             | Future: per-sample IOPS in `Sample` |
| Write durability flush      | `writeSyncEnabled` covers `rwd`; CDM uses `FlushFileBuffers` | `FileChannel.force(true)` |
| Platform O_DIRECT           | `ExtendedOpenOption.DIRECT` works on Linux and Windows with `FileChannel` | Verify `AsynchronousFileChannel` + `DIRECT` combo |
| `AsynchronousFileChannel` + DIRECT | Not tested yet                        | Must verify on Windows NVMe and Linux NVMe |

Results from `AsynchronousFileChannel` should be within **5–10%** of CDM on the
same hardware, since the underlying kernel mechanism (IOCP on Windows) is
identical. The main source of remaining variance is JVM overhead on the
completion handler path and the small-block allocation cost of `MemorySegment`
vs. CDM's pre-pinned buffer pool.

---

## 3. Summary of Proposed Changes

### Data model

- **Add** `queueDepth` (int, default 1) to `BenchmarkOperation` and `BenchmarkConfig`.
- **Add** `directIoEnabled` (Boolean) to `BenchmarkOperation` for per-operation
  direct I/O tracking (needed because CDM Extended's SB test disables it while
  the other tests keep it enabled).
- **Add** `cdmRowLabel` (String, nullable) to `BenchmarkOperation` for CDM grid
  reconstruction from history.
- **Add** `testFileSizeMb` (int, default 0) to `BenchmarkConfig` (optional).
- **Add** `ProfileCategory` enum and field to `BenchmarkProfile`.
- **Add** `CdmRow` record for CDM row definitions.
- All new columns default to safe values; existing Derby databases auto-migrate.

### Execution

- `BenchmarkRunner` gains `executeCdm(List<CdmRow>)` for multi-row execution.
- For QD>1, a new I/O path using `AsynchronousFileChannel` is added to `Sample`.
- QD=1 continues to use the existing synchronous `FileChannel` path (no change).

### Profiles

- **`CDM_DEFAULT`** — 4 rows (SEQ1M Q8T1, SEQ1M Q1T1, RND4K Q32T1, RND4K Q1T1)
  producing 8 operations per run. All tests use T=1 with queue depth as the
  sole parallelism mechanism.
- **`CDM_EXTENDED`** — 5 rows (default + RND4K Q1T1 SB) producing 10 operations.
  The SB test uses buffered I/O to measure the cached path.
- Add `ProfileCategory` (`JDM_BUILT_IN` / `CDM_COMPATIBLE`) for GUI grouping.
- Existing 8 JDM profiles are unchanged.

### UI

- New **"CDM Results"** sub-tab in the Benchmark tab area for the grid display.
- Selecting a CDM benchmark from history opens the CDM Results tab.
- Selecting a JDM built-in benchmark from history opens the Chart tab (unchanged).
- CDM grid cells are clickable → drill into per-sample chart on Chart tab.

### CLI

- `--cdm` flag runs CDM Default (4 rows, 8 operations) and prints the grid.
- `--cdm-extended` flag runs CDM Extended (5 rows, 10 operations).

### Queue Depth

- Emulated via `AsynchronousFileChannel` (standard JDK, no native code).
- IOCP on Windows, thread pool on Linux — same kernel paths as CDM.
- Expected accuracy within 5–10% of CDM on NVMe drives.

---

## Related Source Files

- `jdm-core/src/main/java/jdiskmark/BenchmarkProfile.java` — CDM profile entries + `CdmRow`
- `jdm-core/src/main/java/jdiskmark/BenchmarkOperation.java` — new columns
- `jdm-core/src/main/java/jdiskmark/BenchmarkConfig.java` — propagate `queueDepth`
- `jdm-core/src/main/java/jdiskmark/BenchmarkRunner.java` — `executeCdm()` method
- `jdm-core/src/main/java/jdiskmark/Sample.java` — `AsynchronousFileChannel` I/O path
- `jdm-core/src/main/java/jdiskmark/Benchmark.java` — CDM grid in `toResultString()`
- `jdm-core/src/main/java/jdiskmark/RunBenchmarkCommand.java` — `--cdm` flag
- `jdm-core/src/main/java/jdiskmark/BenchmarkCallable.java` — CLI CDM execution

---

## 4. USB Drive Benchmarking (Linux / Ubuntu)

USB flash drives present several constraints not encountered with NVMe or SATA
drives. The following findings were verified on Ubuntu with a Lexar USB 3.0
flash drive formatted as vfat (FAT32).

### 4.1 Direct IO Constraints

Java's `ExtendedOpenOption.DIRECT` enforces alignment and I/O size requirements
based on `FileStore.getBlockSize()`. On vfat, this returns the **filesystem
cluster size** (e.g. 32 KB) rather than the device's logical sector size
(512 B). The Linux kernel itself only requires 512-byte sector alignment for
Direct IO — verified with `dd oflag=direct` at 4 KB and 512 B block sizes.

Consequences of the Java NIO restriction:

- **Block size ≥ cluster size**: Direct IO works when the benchmark block size
  matches or exceeds the cluster size (e.g. 32 KB blocks on a 32 KB cluster
  vfat volume).
- **Block size < cluster size**: Java rejects the I/O with
  `"Number of remaining bytes is not a multiple of the block size"`.
  The application auto-disables Direct IO for the run and notifies the user.
- **Buffer alignment**: The native memory address of the `ByteBuffer` must also
  be aligned to the cluster size. `Arena.allocate(size, alignment)` with the
  cluster size as alignment satisfies this.

#### Future Improvement

Bypassing Java's NIO restriction via the Foreign Function & Memory API (FFI) to call `open()` with `O_DIRECT` at the syscall level would allow Direct IO with the device's true 512-byte sector alignment, matching `dd` behaviour.

### 4.2 Sector Alignment Auto-Adjustment

When Direct IO is enabled and the filesystem cluster size exceeds the user-selected sector alignment, `BenchmarkRunner.resolveAlignment()` adjusts the effective alignment upward and updates the GUI badge to reflect the value actually used during the run.

### 4.3 SMART Diagnostics

USB flash drives do not support SMART. `smartctl` reports `"Unknown USB bridge"` and cannot query device health data. The application detects the USB bus type via `lsblk TRAN` and shows a message instead of launching the privileged `smartctl` shell.

### 4.4 Drive Model Detection

USB drives report VENDOR and MODEL as separate `lsblk` columns (e.g. VENDOR=`Lexar`, MODEL=`USB Flash Drive`), unlike NVMe drives where MODEL includes the manufacturer. `getVendorModelLinux()` combines both columns for display, while `getDeviceModelLinux()` returns MODEL only.

### 4.5 Write Performance Characteristics

With Direct IO enabled and per-sample file creation (the default benchmark pattern), write throughput on vfat is significantly lower than raw device capability. Each new file requires synchronous FAT table metadata updates that bypass the OS write cache. Verified with `dd oflag=direct` writing to a single file at 47 MB/s versus the benchmark pattern at ~0.5 MB/s.
