# JDiskMark Database Storage

Local persistence of benchmark results and SMART snapshots, and how it relates to JSON export and portal upload.

## 1. Stack

| Item | Value |
|---|---|
| Database | Embedded Apache Derby, `jdbc:derby:derbyDB;create=true` |
| Location | `derby.system.home` = `~/.jdm/<install version>/` (`App.APP_CACHE_DIR_NAME`) |
| ORM | Hibernate via JPA, persistence unit `JDiskMarkPU` (`META-INF/persistence.xml`) |
| Entities | `Benchmark`, `BenchmarkOperation`, `SmartSnapshot` (unlisted classes excluded) |
| Schema | `hibernate.hbm2ddl.auto=update`, no migration scripts |

The database directory is per install version, so a new version starts with an empty history.

## 2. Tables

| Table | Entity | Rows | Contents |
|---|---|---|---|
| `Benchmark` | `Benchmark` | one per run | UUID id (stored as VARCHAR), `systemId`, start/end time, `archived`, batch id. `BenchmarkConfig`, `BenchmarkSystemInfo` (CPU, motherboard vendor and model, OS) and `BenchmarkDriveInfo` are `@Embedded`, so their fields are columns in this row |
| `BenchmarkOperation` | `BenchmarkOperation` | one per operation (WRITE, READ) | `@OneToMany` from `Benchmark` (cascade all, orphan removal). Metrics (`bwAvg/Max/Min`, `accAvg`, `iops`), settings used, `status`, `failReason`, samples lob |
| `SmartSnapshot` | `SmartSnapshot` | one per SMART capture | Identity id, `capturedAt`, device/model/serial/firmware, health summary columns, full `smartctl` output in `rawJson` |

SMART data captured at benchmark start (`Benchmark.smartData`) is `@Transient`: it is uploaded with the run but not stored in the `Benchmark` table. Persisted SMART history lives only in `SmartSnapshot`.

## 3. Lob Columns

| Column | Format | Converter |
|---|---|---|
| `BenchmarkOperation.samples` | JSON array of `Sample` (Jackson), stored as bytes | `SampleAttributeConverter` |
| `BenchmarkOperation.gc_retried_samples` | CSV of sample numbers, e.g. `3,17,42` | `GcRetriedSamplesConverter` |
| `SmartSnapshot.rawJson` | `smartctl --json` output (CLOB) | none |

Samples are not queryable in SQL; per-operation aggregates are stored as columns for listing and sorting.

## 4. Schema Evolution

`update` adds new tables and columns but never drops, renames or retypes them.

- New fields must be nullable or have a safe default; rows saved earlier read the default. Example: `status` is `null` on older operations and `getStatus()` returns `COMPLETED`.
- Renaming a field creates a new column and orphans the old one. Keep the column name with `@Column(name = ...)` instead.
- Changing a type (e.g. Hibernate 6 UUID to VARBINARY) breaks existing databases; pin it with `@JdbcTypeCode` as `Benchmark.id` does.
- Enums are stored with `@Enumerated(EnumType.STRING)` so reordering constants is safe.
- Avoid Derby reserved words as column names (`user` is why the field is `username`).

## 5. JSON Export and Portal Upload

No JSON copy of a benchmark is stored. Export and `Portal.upload` serialise the `Benchmark` object graph with Jackson each time, including `operations` and their `samples`; a run reloaded from Derby serialises the same way.

- Every public getter is part of the payload unless `@JsonIgnore`d; display helpers (`getBwAvgDisplay()`, `isFailed()`) are ignored.
- Adding a getter changes the portal payload. Confirm the portal tolerates the new field before release.
- `Benchmark.id` is sent as `_id` (`UuidToMongoIdSerializer`); upload adds `startTimeString` and `timeZone`.

## 6. Guidelines

- Store what the run produced; derive display values in getters.
- Use a column when a value must survive reload, export and re-upload of a saved run. Use `@Transient` only for runtime state.
- Keep per-sample data in the samples lob; promote a value to a column only when it is listed, sorted or filtered.
- Failed operations are stored with `status = FAILED`, a `failReason`, no samples and `-1` metrics (see `benchmarks.md` §4.3).
