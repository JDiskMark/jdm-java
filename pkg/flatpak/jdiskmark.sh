#!/bin/sh
# Launcher script for JDiskMark Flatpak
CLASSPATH="/app/lib/jdiskmark/jdiskmark.jar:/app/lib/jdiskmark/libs/*"
exec /app/jre/bin/java -XX:+UseZGC -cp "$CLASSPATH" jdiskmark.App "$@"
