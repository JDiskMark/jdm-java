# JDiskMark v0.6.4 beta (Windows/Mac/Linux)

Java Disk Benchmark Utility

## Features

- Java cross platform solution
- Benchmark IO read/write performance
- Graphs: sample bw, max, min, cum bw, latency (access time)
- Configure block size, blocks, samples and other benchmark parameters
- Detect drive model, capacity and processor
- Save and load benchmark
- Auto clear disk cache (when sudo or admin)
- multi threaded benchmarks
- Default profiles
- Command line interface
- available in msi, deb, rpm and zip releases

## Releases

https://sourceforge.net/projects/jdiskmark/

### Windows Installer (.msi)

A signed windows installer is available for windows environment and can be installed as an administrator.

To install launch the `jdiskmark-<version>.msi`.

### Deb Installer (.deb)

The deb installer is used on Debian linux distributions like ubuntu.

To install use `sudo dpkg -i jdiskmark_<version>_amd64.deb` and to remove `sudo dpkg -r jdiskmark`

### RPM Installer (.rpm)

The rpm installer is used on RHEL, CENTOS, SUSELinux and Fedora distributions.

To install use `sudo rpm -i jdiskmark-<rpm.version>.x86_64.rpm` and to remove use `sudo rpm -e jdiskmark`

Note: the `rpm.version` is similar to the `version` but replaces hyphens with periods.

### Zip Archive (.zip)

The zip distribution does not require admin for installing but does require 
Java 25 to be installed seperately.

1. Download and install [java 25](https://www.oracle.com/java/technologies/downloads/) from Oracle.

2. Verify java 25 is installed:
   ```
   C:\Users\username>java --version
   java 25.0.1 2025-10-21 LTS
   Java(TM) SE Runtime Environment (build 25.0.1+8-LTS-27)
   Java HotSpot(TM) 64-Bit Server VM (build 25.0.1+8-LTS-27, mixed mode, sharing)
   ```

3. Extract release zip archive into desired location.
   ```
   Examples:  
   /Users/username/jdiskmark-v0.6.3
   /opt/jdiskmark-v0.6.3
   ```

## Launching as normal process

Note: Running without sudo or a windows administrator will require manually 
clearing the disk write cache before performing read benchmarks.

1. Open a terminal or shell in the extracted directory.

2. run command:
   ```
   $ java -jar jdiskmark.jar
   ```
   In windows double click executable jar file.

3. Drop cache manually:
   - Linux: `sudo sh -c "sync; 1 > /proc/sys/vm/drop_caches"`
   - Mac OS: `sudo sh -c "sync; purge"`
   - Windows: Run included EmptyStandbyList.exe or [RAMMap64.exe](https://learn.microsoft.com/en-us/sysinternals/downloads/rammap)
     - With RAMMap64 invalidate disk cache with Empty > Empty Standby List

## Launching gui with elevated privileges

Note: Take advantage of automatic clearing of the disk cache for write read 
benchmarks start with sudo or an administrator windows shell.

- Linux: `sudo java -jar jdiskmark.jar`
- Mac OS: `sudo java -jar jdiskmark.jar`
- Windows: start powershell as administrator then `java -jar jdiskmark.jar`

## command line examples

display version

```
java -jar jdiskmark.jar -v
```

display top level help

```
java -jar jdiskmark.jar -h
```

display benchmark options

```
java -jar .\jdiskmark.jar run -h
Usage: jdiskmark run [-cdhmsvy] [-a=<sectorAlignment>] [-b=<numOfBlocks>] [-e=<exportPath>]
                     [-i=<ioEngine>] [-l=<locationDir>] [-n=<numOfSamples>] [-o=<blockSequence>]
                     [-p=<profile>] [-t=<benchmarkType>] [-T=<numOfThreads>] [-z=<blockSizeKb>]
Starts a disk benchmark test with specified parameters.
  -a, --alignment=<sectorAlignment>
                            Sector alignment: NONE, ALIGN_512, ALIGN_4K, ALIGN_8K, ALIGN_16K,
                              ALIGN_64K. (Profile default used if not specified)
  -b, --blocks=<numOfBlocks>
                            Number of blocks/chunks per sample. (Profile default used if not
                              specified)
  -c, --clean               Remove existing JDiskMark data directory before starting.
  -d, --direct              Enable Direct I/O (bypass OS cache). Only works with MODERN engine.
  -e, --export=<exportPath> The output file to export benchmark results in json format.
  -h, --help                Display this help and exit.
  -i, --io-engine=<ioEngine>
                            I/O Engine: MODERN, LEGACY. (Profile default used if not specified)
  -l, --location=<locationDir>
                            The directory path where test files will be created.
  -m, --multi-file          Create a new file for every sample instead of using one large file.
  -n, --samples=<numOfSamples>
                            Total number of samples/files to write/read. (Profile default used if
                              not specified)
  -o, --order=<blockSequence>
                            Block order: SEQUENTIAL, RANDOM. (Profile default used if not specified)
  -p, --profile=<profile>   Profile: QUICK_TEST, MAX_THROUGHPUT, HIGH_LOAD_RANDOM_T32,
                              LOW_LOAD_RANDOM_T1, MAX_WRITE_STRESS, MEDIA_PLAYBACK,
                              VIDEO_EXPORTING, PHOTO_LIBRARY. (Default: QUICK_TEST)
  -s, --save                Enable saving the benchmark results to the database.
  -t, --type=<benchmarkType>
                            Benchmark type: READ, WRITE, READ_WRITE. (Profile default used if not
                              specified)
  -T, --threads=<numOfThreads>
                            Number of threads to use for testing. (Profile default used if not
                              specified)
  -v, --verbose             Enable detailed logging.
  -y, --write-sync          Enable Write Sync (flush to disk).
  -z, --block-size=<blockSizeKb>
                            Size of a block/chunk in Kilobytes (KB). (Profile default used if not
                              specified)
```

run benchmarks example syntax

```
java -jar jdiskmark.jar run -n 25 -t "Write"
java -jar jdiskmark.jar run -l D:\ -n 25 -t "Read"
java -jar jdiskmark.jar run -n 25 -t "Read & Write"
java -jar jdiskmark.jar run -p MAX_WRITE_STRESS
```
run example benchmark
```
java -jar jdiskmark.jar run -n 25 -o Random -t "Write" -T 4
...
-------------------------------------------
JDiskMark Benchmark Results (v0.6.3-dev)
-------------------------------------------
Benchmark: Write
Drive: Samsung SSD 990 PRO 4TB
Capacity: 32% (1178/3725 GB)
Timestamp: 2025-10-26T18:17:37.529141200
CPU: 13th Gen Intel(R) Core(TM) i9-13900K
System: Windows 11 / amd64
Java: Java(TM) SE Runtime Environment 21.0.3
Path: C:\Users\james
-------------------------------------------
Order: Random
IOMode: Write
Thread(s): 4
Blocks(size): 25(512)
Samples: 25
TxSize(KB): 409600
Speed(MB/s): 3952.64
SpeedMin(MB/s): 3397.24
SpeedMax(MB/s): 4243.47
Latency(ms): 0.13
IOPS: 28892857
-------------------------------------------
```

## Development Environment

jdiskmark client is developed with [NetBeans 25](https://netbeans.apache.org/front/main/download/) and [Java 25](https://www.oracle.com/java/technologies/downloads/)

## Source

Source is available on our [github repo](https://github.com/JDiskMark/jdm-java/)

## Release Notes

### v1.0.0 planned
- TODO: #16 pkg installer (MacOS) - tyler
- TODO: #70 app icon - ian
- TODO: #33 maven build - lane
- TODO: #78 throttle graphics render - val
- TODO: #95 disk cache purging - val
- TODO: #44 gui benchmark export
- #40 resolve cross platform gui laf
- #121 common benchmark runner for cli and gui
    - cli options for: profile, direct io, alignment
    - new profiles: media playback, video export, photo library
- #67 portal uploads
    - TODO: #117 user portal upload acknowledgement
    - TODO: #118 test interlock or OAuth upload
    - #111 extract cfg model from benchmark

### v0.6.3
- #82 drive access notification
- #107 sector aligned / direct io
- #15 deb installer (Ubuntu)
- #98 rpm installer (Redhat)
- #42 default profiles
- #69 command line interface
- #84 processor info resolved for (SP) installs
- #73 refactor benchmark data model, keyboard op sel

### v0.6.2 linux optimized ui
- #64 persist IOPS, write sync
- control panel on left
- allow concurrent version runs
- event tab swapped w disk location

### v0.6.1 ms app store release
- JDiskMark in title and msi vendor name
- Remove "Average" from "Access Time" label

### v0.6.0
- #13 Detect drive info on startup
- #12 update look and feel (windows)
- #22 foreign capacity reporting
- #23 delete selected benchmarks
- #10 IOPS reporting
- #25 linux crash, capacity w terabytes and exabytes
- write sync default off
- #26 lowercase project and jar
- #20 threading and queue depth
- #36 I/O Mode dropdown uses enum values for type safety

### v0.5.1
- resolve #17 invalid disk usage reported win 10
- msi installer available

### v0.5
- update for java 21 LTS w NetBeans 20 environment: eclipselink 4.0, jpa 3.1, 
  modelgen 5.6, annotations 3.1, xml.bind 4.0
- increased drive information default col width to 170
- time format updated to `yyyy-MM-dd HH:mm:ss`
- default to 200 marks
- replace Date w LocalDateTime to avoid deprecated @Temporal
- disk access time (ms) - plotting disabled by default
- replace display of transfer size with access time in run panel
- GH-2 auto clear disk cache for combined write read benchmarks
- GH-6 save and load benchmarks and graph series
- break out actions into seperate menu
- admin or root indicator, architecture indicator
- GH-8 used capacity and total capacity
- initial color palette options
- report processor name

### v0.4
- updated eclipselink to 2.6 allows auto schema update
- improved gui initialization
- platform disk model info:
  - windows: via powershell query
  - linux:   via `df /data/path` & `lsblk /dev/path --output MODEL`
  - osx:     via `df /data/path` & `diskutil info /dev/disk1`

### v0.3
- persist recent run with embedded derby db
- remove "transfer mark number" from graph
- changed graph background to dark gray
- resizing main frame stretches tabbed pane instead of empty panel

### v0.2
- auto generate zip release ie. `jdiskmark-v0.2.zip`
- added tabbed pane near bottom to organize new controls
- format excessive decimal places
- show recent runs (not persisted)
- default to nimbus look and feel

### v0.1
- initial release

### Proposed Features
- upload benchmarks to jdiskmark.net portal (anonymous/w login)
- local app log for remote diagnostics
- selecting a drive location displays detected drive information below
- speed curves w rw at different tx sizes
- response time histogram > distribution of IO
- IOPS charts, review potential charts
- help that describes features and controls

## issues
- read&write not consistant with order caps
- bottom margins between table to bar to window edge should be the same

## Windows Paths Examples for Building

For ant builds

`C:\apache-ant-1.10.15\bin`

For maven builds

`C:\apache-maven-3.9.10\bin`

For code signing

`C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x86\`
