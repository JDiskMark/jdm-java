# jDiskMark v0.5 (Windows/Mac/Linux)

Cross platform disk benchmark utility written in java.

## Features

- Benchmark IO read/write performance
- Intuitive graphs for: sample bw, max, min, cum avg, access time
- Adjustable block size, block qty and sample qty
- Single or multi file option
- Sequential or random option
- Detects drive model info
- Benchmarks saved to embedded DB
- Java cross platform solution
- Auto clear disk cache (when sudo or admin)

## Releases

https://sourceforge.net/projects/jdiskmark/

## Installation

1. Install [java 21](https://www.oracle.com/java/technologies/downloads/)

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
   /Users/username/jdiskmark-v0.5  
   /opt/jdiskmark-v0.5
   ```

## Launching as normal process

Note: Running without sudo or a windows administrator will require manually 
clearing the disk write cache before performing read benchmarks.

1. Open a terminal or shell in the extracted directory.

2. run command:
   ```
   $ java -jar jDiskMark.jar
   ```
   In windows double click executable jar file.

3. Drop cache manually:
 - Linux:
   ```
   sudo sh -c "sync; 1 > /proc/sys/vm/drop_caches"
   ```
 - Mac OS:
   ```
   sudo sh -c "sync; purge"
   ```
 - Windows: Run included EmptyStandbyList.exe or [RAMMap64.exe](https://learn.microsoft.com/en-us/sysinternals/downloads/rammap)

## Launching with elevated privileges

Note: Take advantage of automatic clearing of the disk cache for write read 
benchmarks start with sudo or an administrator windows shell.

 - Linux: sudo java -jar jDiskMark.jar
 - Mac OS: sudo java -jar jDiskMark.jar
 - Windows: start powershell as administrator then "java -jar jDiskMark"

## Development Environment

jdiskmark client is developed with [NetBeans 20](https://netbeans.apache.org/front/main/download/) and [Java 21](https://www.oracle.com/java/technologies/downloads/)

## Source

Source code is available on our [github repo](https://github.com/jDiskMark/jdm-java/)

## Release Notes

### v0.5 beta 2
 - update for java 21 LTS w NetBeans 20 environment: eclipselink 4.0, jpa 3.1, 
   modelgen 5.6, annotations 3.1, xml.bind 4.0
 - increased drive information default col width to 170
 - time format updated to "yyyy-MM-dd HH:mm:ss"
 - default to 200 marks
 - replace Date w LocalDateTime to avoid deprecated @Temporal
 - disk access time (ms) - plotting disabled by default
 - replace display of transfer size with access time in run panel
 - auto clear disk cache for combined write read benchmarks

### v0.4
 - updated eclipselink to 2.6 allows auto schema update
 - improved gui initialization
 - platform disk model info:
    - windows: via powershell query
    - linux:   via "df /data/path" & "lsblk /dev/path --output MODEL"
    - osx:     via "df /data/path" & "diskutil info /dev/disk1"

### v0.3
 - persist recent run with embedded derby db
 - remove "transfer mark number" from graph
 - changed graph background to dark gray
 - resizing main frame stretches tabbed pane instead of empty panel

### v0.2
 - auto generate zip release ie. jdiskmark-v0.2.zip
 - added tabbed pane near bottom to organize new controls
 - format excessive decimal places
 - show recent runs (not persisted)
 - default to nimbus look and feel

### v0.1
 - initial release

### Proposed Features
 - store benchmark data for each run and load when selected
 - upload benchmarks to jdiskmark.net portal (anonymous/w login)
 - local app log for remote diagnostics
 - detect and display os processor info
 - selecting a drive location displays detected drive information below
 - disk capacity and drive letter (available on windows)
