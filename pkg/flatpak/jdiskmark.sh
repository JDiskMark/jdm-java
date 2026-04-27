#!/bin/sh
# Launcher script for JDiskMark Flatpak (using shaded JAR)
exec /app/jre/bin/java -XX:+UseZGC -jar /app/lib/jdiskmark/jdiskmark.jar "$@"
