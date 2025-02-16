:: GH-14 This script uses jpackage w wix 3.14 to create a msi installer
:: wix toolset 3.14 is available here:
:: https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm

:: make sure to set the version number
set VERSION=0.6.0-dev.e
set MSI_VER=0.6.0

set JPKG_EXE="C:\Program Files\java\jdk-21\bin\jpackage"

del jDiskMark-*.msi
%JPKG_EXE% --type msi --input jdiskmark-%VERSION% --main-jar jDiskMark.jar ^
    --name jDiskMark-%VERSION% --app-version %MSI_VER% ^
    --vendor "jdiskmark.net" --win-console --win-menu

ren jDiskMark-%VERSION%-%MSI_VER%.msi jDiskMark-%VERSION%.msi