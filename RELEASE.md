# Notes to release

## Use engineering builds to verify all installers

1. build and verify the artifact on windows: 
2. build and verify the artifact on linux
3. build and verify the artifact on mac

## Mark the release

create the `release` branch from `dev`

```
git checkout dev
git pull
git checkout -b release/<version>
```

## Update pom to target release

update `pom.xml` version to reflect the release version

## Create release artifacts on GitHub

```
git add pom.xml
git commit -m "#N cut release <version>"
git tag -a <version> -m "Release <version>"
git push origin <version>
```

## create final signed build for windows

`mvn clean install -pl jdm-core,jdm-dist/jdm-msi -am -Pwindows-msi -Psign`

## Upload release artifacts to sourceforge

create new <version> folder on sourceforge
copy rpm, deb and flatpak builds from github to sourceforge release folder
upload signed msi to release folder
trigger signed macos pkg to build on github, copy to sourceforge release folder when ready

## merge release branch to main

## merge main back to dev