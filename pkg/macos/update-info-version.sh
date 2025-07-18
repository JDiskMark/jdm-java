#!/bin/bash

#Version 
source ./version-utils.sh

echo "Setting Info.plist version to $VERSION"

# Update Info.plist (using PlistBuddy)
if command -v /usr/libexec/PlistBuddy &> /dev/null; then
  /usr/libexec/PlistBuddy -c "Set :CFBundleVersion $VERSION" JDiskMark.app/Contents/Info.plist
  /usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $VERSION" JDiskMark.app/Contents/Info.plist
else
  # Fallback to sed if PlistBuddy is not available
  sed -i '' "s|<key>CFBundleVersion</key>[[:space:]]*<string>.*</string>|<key>CFBundleVersion</key><string>$VERSION</string>|" JDiskMark.app/Contents/Info.plist
fi
echo "CFBundleVersion is now: $(/usr/libexec/PlistBuddy -c "Print :CFBundleVersion" JDiskMark.app/Contents/Info.plist)"
echo "CFBundleShortVersionString is now: $(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" JDiskMark.app/Contents/Info.plist)"