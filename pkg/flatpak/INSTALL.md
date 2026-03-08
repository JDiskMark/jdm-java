# Installing JDiskMark Flatpak

## Prerequisites

Flatpak must be installed on your system. If you are on a gaming-oriented distro such
as Bazzite or SteamOS, Flatpak and Flathub are already configured by default.

On Ubuntu / Debian you can add Flathub with:

```sh
flatpak remote-add --user --if-not-exists flathub https://flathub.org/repo/flathub.flatpakrepo
```

## Install

Download `jdiskmark-<version>.flatpak` from the GitHub Actions artifact or release page, then run:

```sh
flatpak install --user ./jdiskmark-<version>.flatpak
```

> **Note:** Do **not** use `flatpak install --from` — that flag is for `.flatpakref`
> reference files, not `.flatpak` bundles, and will produce an error.

If the `org.freedesktop.Platform` runtime is not yet present, Flatpak will offer to
download it from Flathub automatically during the install step.

## Run

After installation, launch JDiskMark from your application menu or from the terminal:

```sh
flatpak run net.jdiskmark.JDiskMark
```

## Uninstall

```sh
flatpak uninstall net.jdiskmark.JDiskMark
```
