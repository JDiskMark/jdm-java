#!/bin/bash

# #15 Jeff's script to build a .deb package for JDiskMark from source.
# note it builds the client but does not include the `build.properties` in the META-INF folder,
# so the resulting jar will not show version info in the about dialog.
# is it believed this should be ported into the ant or maven builds to simplify maintenance.

# --- Configuration ---
REPO_URL="https://github.com/jDiskMark/jdm-java"
REPO_DIR="jdm-java"
DEB_DIR="jdiskmark-deb"
APP_NAME="jdiskmark"
VERSION="0.6.3-dev"
DEB_VERSION="${VERSION}-1"
JAR_FILE="jdiskmark.jar"
BUILT_DIR="${REPO_DIR}/dist/${APP_NAME}-${VERSION}"
INSTALL_DIR="/usr/share/${APP_NAME}"
WRAPPER_PATH="${DEB_DIR}/usr/bin/${APP_NAME}"
CONTROL_PATH="${DEB_DIR}/DEBIAN/control"
LIB_DIR="${REPO_DIR}/libs"

# --- Functions ---

# Function to check for and install required packages
install_dependencies() {
    echo "Installing build dependencies: git, ant, openjdk-21-jdk, dpkg-dev..."
    sudo apt update > /dev/null 2>&1
    if ! sudo apt install -y git ant openjdk-21-jdk dpkg-dev; then
        echo "Error: Failed to install required dependencies."
        exit 1
    fi
}

# Function to clone the repository
clone_repo() {
    echo "Cloning repository from ${REPO_URL}..."
    if [ -d "${REPO_DIR}" ]; then
        echo "Repository directory already exists. Removing and re-cloning."
        rm -rf "${REPO_DIR}"
    fi
    if ! git clone "${REPO_URL}"; then
        echo "Error: Failed to clone the repository."
        exit 1
    fi
}

# Function to build the application
build_app() {
    echo "Building jDiskMark with Ant..."
    if ! (cd "${REPO_DIR}" && ant); then
        echo "Error: Ant build failed."
        exit 1
    fi
    if [ ! -f "${BUILT_DIR}/${JAR_FILE}" ]; then
        echo "Error: Built JAR file not found at ${BUILT_DIR}/${JAR_FILE}"
        exit 1
    fi
}

# Function to create the Debian package structure and files
create_deb_structure() {
    echo "Creating Debian package structure..."
    rm -rf "${DEB_DIR}"
    mkdir -p "${DEB_DIR}/DEBIAN"
    mkdir -p "${DEB_DIR}${INSTALL_DIR}"
    mkdir -p "${DEB_DIR}${INSTALL_DIR}/libs"
    mkdir -p "${DEB_DIR}/usr/bin"

    # Copy the built JAR file and all libraries
    echo "Copying built JAR and libraries to ${INSTALL_DIR}/"
    cp "${BUILT_DIR}/${JAR_FILE}" "${DEB_DIR}${INSTALL_DIR}/"
    cp "${LIB_DIR}"/*.jar "${DEB_DIR}${INSTALL_DIR}/libs/"

    # Create the control file
    echo "Creating DEBIAN/control file..."
    cat > "${CONTROL_PATH}" << EOF
Package: ${APP_NAME}
Version: ${DEB_VERSION}
Section: utils
Priority: optional
Architecture: all
Depends: default-jre | openjdk-21-jre | openjdk-17-jre
Maintainer: Manus AI <ai@manus.im>
Description: jDiskMark - Disk Benchmark Utility
 jDiskMark is a cross-platform disk benchmark utility written in Java.
 It provides a simple way to measure the read and write performance of
 your storage devices.
EOF

    # Create the application wrapper script
    echo "Creating wrapper script /usr/bin/${APP_NAME}..."
    # The classpath must include the main jar and all jars in the libs directory
    cat > "${WRAPPER_PATH}" << EOF
#!/bin/sh
# Wrapper script for jDiskMark

# Set the classpath to include the main jar and all jars in the libs directory
CLASSPATH="${INSTALL_DIR}/${JAR_FILE}"
for jar in ${INSTALL_DIR}/libs/*.jar; do
    CLASSPATH="\$CLASSPATH:\$jar"
done

# Execute the application with the correct main class and classpath
# The main class is jdiskmark.App, but the jar is a runnable jar, so we use -jar
# However, since we need the external libs, we must use -cp and specify the main class.
exec java -cp "\$CLASSPATH" jdiskmark.App "\$@"
EOF

    # Set permissions
    echo "Setting permissions on wrapper script..."
    chmod 755 "${WRAPPER_PATH}"
}

# Function to build the .deb file
build_deb() {
    echo "Building the .deb package..."
    if ! dpkg-deb --build "${DEB_DIR}"; then
        echo "Error: dpkg-deb failed to build the package."
        exit 1
    fi
    mv "${DEB_DIR}.deb" "${APP_NAME}_${DEB_VERSION}_all.deb"
    echo "Successfully created package: ${APP_NAME}_${DEB_VERSION}_all.deb"
}

# Function for cleanup
cleanup() {
    echo "Cleaning up intermediate files..."
    rm -rf "${REPO_DIR}"
    rm -rf "${DEB_DIR}"
}

# --- Main Execution ---
install_dependencies
clone_repo
build_app
create_deb_structure
build_deb
cleanup

echo "Script finished successfully."
