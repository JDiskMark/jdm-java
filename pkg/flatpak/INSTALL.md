# Installing JDiskMark Flatpak

## Prerequisites

Flatpak must be installed on your system. If you are on a gaming-oriented distro such
as Bazzite or SteamOS, Flatpak and Flathub are already configured by default.

### 1. Add Flathub (if not already configured)

On Ubuntu / Debian:

```sh
flatpak remote-add --user --if-not-exists flathub https://flathub.org/repo/flathub.flatpakrepo
```

### 2. Install the required runtime

The bundle requires the `org.freedesktop.Platform 24.08` runtime from Flathub.
Install it before installing the bundle:

```sh
flatpak install --user flathub org.freedesktop.Platform//24.08
```

If prompted to install other dependencies, accept them.

## Install

Download `jdiskmark-<version>.flatpak` from the GitHub Actions artifact or release page, then run:

```sh
flatpak install --user ./jdiskmark-<version>.flatpak
```

> **Note:** Do **not** use `flatpak install --from` — that flag is for `.flatpakref`
> reference files, not `.flatpak` bundles, and will produce an error.

## Run

After installation, launch JDiskMark from your application menu or from the terminal:

```sh
flatpak run net.jdiskmark.JDiskMark
```

## Uninstall

```sh
flatpak uninstall net.jdiskmark.JDiskMark
```
