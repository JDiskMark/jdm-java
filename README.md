# JDiskMark v0.6.2 beta (Windows/Mac/Linux)

Java Disk Benchmark Utility

## Features

- Java cross platform solution
- Benchmark IO read/write performance
- Graphs: sample bw, max, min, cum bw, latency (access time)
- Adjustable block size, block qty and sample qty
- Single or multi file option
- Sequential or random option
- Detect drive model, capacity and processor
- Save and load benchmark
- Auto clear disk cache (when sudo or admin)
- multi threaded benchmarks

## Releases

https://sourceforge.net/projects/jdiskmark/

## Installation

Java 21 needs to be installed to run jdiskmark.

1. Download and install [java 21](https://www.oracle.com/java/technologies/downloads/) from Oracle.

2. Verify java 21 is installed:
   ```
   C:\Users\username>java --version
   java 21.0.1 2023-10-17 LTS
   Java(TM) SE Runtime Environment (build 21.0.1+12-LTS-29)
   Java HotSpot(TM) 64-Bit Server VM (build 21.0.1+12-LTS-29, mixed mode, sharing)
   ```

3. Extract release zip archive into desired location.
   ```
   Examples:  
   /Users/username/jdiskmark-v0.6.0  
   /opt/jdiskmark-v0.6.0
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

## Launching with elevated privileges

Note: Take advantage of automatic clearing of the disk cache for write read 
benchmarks start with sudo or an administrator windows shell.

- Linux: `sudo java -jar jdiskmark.jar`
- Mac OS: `sudo java -jar jdiskmark.jar`
- Windows: start powershell as administrator then `java -jar jdiskmark.jar`

## Development Environment

jdiskmark client is developed with [NetBeans 21](https://netbeans.apache.org/front/main/download/) and [Java 21](https://www.oracle.com/java/technologies/downloads/)

## Source

Source code is available on our [github repo](https://github.com/JDiskMark/jdm-java/)

## Release Notes

### v1.0.0 planned
- TODO: #16 MacOS installer - tyler
- TODO: #15 Ubuntu installer - jeff
- TODO: #33 maven build - lane
- TODO: #40 gui presentation issues - james
- #84 processor info resolved for (sp) installs

### v0.6.2 linux optimized ui
- #64 persist IOPS, write sync - val
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
