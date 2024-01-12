# jDiskMark v0.5 (Windows/Mac/Linux)

Cross platform disk benchmark utility written in java.

## Features

- Disk IO read/write performance
- Java cross platform solution
- Saves previous run information
- Single or multi file option
- Sequential or random option
- Detects drive model info
- Adjustable block size

## Builds

https://sourceforge.net/projects/jdiskmark/

## Usage

1. Requires java 21.

2. to run:
   ```
   $ java -jar jDiskMark.jar
   ```
   In windows double click executable jar file.

## Release Notes

### v0.5
 - update for java 21 LTS w NetBeans 20 environment
   - eclipselink 4.0, jpa 3.1, modelgen 5.6, annotations 3.1, xml.bind 4.0
 - increased drive information default col width to 170
 - time format updated to "yyyy-MM-dd HH:mm:ss"
 - default to 200 marks

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
 - upload benchmarks to jdiskmark.net portal (anonymous/w login)
 - store benchmark data for each run and load when selected
 - update windows drive model parsing script to adapt to differing script output
 - disk access time (ms)
 - disk capacity and drive letter (available on windows)
 - auto clear disk cache windows, linux, osx
   - linux: sync && echo 1 > /proc/sys/vm/drop_caches
   - windows: unknown
   - osx: unknown
