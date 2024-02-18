:: GH-14 This script uses jpackage to create an msi installer
:: make sure to set the version number

set VERSION=0.5.1
set JPKG_EXE="C:\Program Files\java\jdk-21\bin\jpackage"

del jDiskMark-*.msi
%JPKG_EXE% --type msi --input jdiskmark-v%VERSION% --main-jar jDiskMark.jar ^
    --name jDiskMark-%VERSION% --app-version %VERSION% ^
    --vendor "jdiskmark.net" --win-console --win-menu

ren jDiskMark-%VERSION%-%VERSION%.msi jDiskMark-%VERSION%.msi