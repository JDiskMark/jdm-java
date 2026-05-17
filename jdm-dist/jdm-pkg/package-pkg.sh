#!/bin/bash
set -e

# Arguments
VERSION=$1
APP_NAME=$2
PKG_NAME=$3
DIST_DIR=$4
INPUT_DIR=$5
SIGNING_IDENTITY=$6
INSTALLER_IDENTITY=$7
APPLE_ID=$8
APPLE_PASSWORD=$9
APPLE_TEAM_ID=${10}
IDENTIFIER=${11}

echo "Building macOS PKG for $APP_NAME version $VERSION..."

APP_IMAGE_DIR="${DIST_DIR}/${PKG_NAME}-${VERSION}-app-image"
# APP_NAME (e.g. "JDiskMark") is used as the .app bundle name so that
# /Applications/JDiskMark.app is the install path and pkgutil registers
# the receipt under $IDENTIFIER for clean uninstalls.
APP_BUNDLE="${APP_IMAGE_DIR}/${APP_NAME}.app"
UNSIGNED_PKG="${DIST_DIR}/${PKG_NAME}-${VERSION}-unsigned.pkg"
FINAL_PKG="${DIST_DIR}/${PKG_NAME}-${VERSION}.pkg"

# Step 1: Sign FlatLaf native dylibs (Optional)
if [ -n "$SIGNING_IDENTITY" ]; then
    echo "Step 1: Signing FlatLaf dylibs..."
    FLATLAF_JAR=$(find "$INPUT_DIR/lib" -name "flatlaf-*.jar" | head -n 1)
    if [ -n "$FLATLAF_JAR" ]; then
        TEMP_DIR=$(mktemp -d)
        unzip -q "$FLATLAF_JAR" -d "$TEMP_DIR"
        
        # Sign dylibs
        find "$TEMP_DIR" -name "*.dylib" | while read f; do
            codesign --force --options runtime --entitlements entitlements.plist --timestamp --sign "$SIGNING_IDENTITY" "$f"
        done
        
        # Repack
        jar uf "$FLATLAF_JAR" -C "$TEMP_DIR" .
        rm -rf "$TEMP_DIR"
    fi
fi

# Step 2: Build UNSIGNED app-image
echo "Step 2: Building unsigned app-image..."
rm -rf "$APP_IMAGE_DIR"
jpackage --type app-image \
         --input "$INPUT_DIR" \
         --main-jar "jdm-core-$VERSION.jar" \
         --main-class "jdiskmark.App" \
         --name "$APP_NAME" \
         --app-version "1.0.0" \
         --vendor "jdiskmark" \
         --dest "$APP_IMAGE_DIR" \
         --resource-dir "images" \
         --mac-package-identifier "$IDENTIFIER" \
         --mac-package-name "$APP_NAME" \
         --java-options "-XX:+UseZGC" \
         --add-modules "java.base,java.desktop,java.logging,java.prefs,java.management,java.instrument,java.sql,java.rmi,java.naming,jdk.unsupported,java.net.http"

# Step 3: Sign app bundle (Optional)
if [ -n "$SIGNING_IDENTITY" ]; then
    echo "Step 3: Signing app bundle..."
    # Sign runtime components
    find "$APP_BUNDLE/Contents/runtime" \( -name '*.dylib' -o -name '*.so' \) | while read f; do
        codesign --force --options runtime --entitlements entitlements.plist --timestamp --sign "$SIGNING_IDENTITY" "$f"
    done
    
    find "$APP_BUNDLE/Contents/runtime" -type f -perm +111 ! -name '*.dylib' ! -name '*.so' | while read f; do
        if file "$f" | grep -q Mach-O; then
            codesign --force --options runtime --entitlements entitlements.plist --timestamp --sign "$SIGNING_IDENTITY" "$f"
        fi
    done
    
    # Seal runtime sub-bundle
    codesign --force --options runtime --entitlements entitlements.plist --timestamp --sign "$SIGNING_IDENTITY" "$APP_BUNDLE/Contents/runtime"
    
    # Sign main launcher and app bundle
    codesign --force --options runtime --entitlements entitlements.plist --timestamp --sign "$SIGNING_IDENTITY" "$APP_BUNDLE/Contents/MacOS/$APP_NAME"
    codesign --force --options runtime --entitlements entitlements.plist --timestamp --sign "$SIGNING_IDENTITY" "$APP_BUNDLE"
fi

# Step 4: Build PKG
echo "Step 4: Building PKG..."
COMPONENT_PKG="${DIST_DIR}/${PKG_NAME}-${VERSION}-component.pkg"
pkgbuild --component "$APP_BUNDLE" \
         --install-location "/Applications" \
         --identifier "$IDENTIFIER" \
         --version "1.0.0" \
         --scripts "scripts" \
         "$COMPONENT_PKG"

productbuild --package "$COMPONENT_PKG" "$UNSIGNED_PKG"
rm "$COMPONENT_PKG"

# Step 5: Sign the PKG installer (Optional)
if [ -n "$INSTALLER_IDENTITY" ]; then
    echo "Step 5: Signing PKG installer..."
    productsign --sign "$INSTALLER_IDENTITY" --timestamp "$UNSIGNED_PKG" "$FINAL_PKG"
    rm "$UNSIGNED_PKG"
else
    mv "$UNSIGNED_PKG" "$FINAL_PKG"
fi

# Step 6: Notarize (Optional)
if [ -n "$APPLE_ID" ]; then
    echo "Step 6: Notarizing PKG..."
    xcrun notarytool submit "$FINAL_PKG" \
                     --apple-id "$APPLE_ID" \
                     --password "$APPLE_PASSWORD" \
                     --team-id "$APPLE_TEAM_ID" \
                     --wait
    xcrun stapler staple "$FINAL_PKG"
fi

echo "Successfully built macOS PKG: $FINAL_PKG"
