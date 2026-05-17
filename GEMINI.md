# GEMINI.md — JDiskMark Agent Guidelines

This file configures AI agent behavior for the `jdm-java` repository.

---

## Agent Workflow Rules

These rules apply to all AI agents working in this repository.

### Git — Commit & Push Discipline

- **Do NOT run `git commit` without explicit user instruction.**
  After completing changes, stage with `git add` only, then stop and summarize
  what is staged and why. Let the user decide when to commit.

- **Do NOT run `git push` without explicit user instruction.**

- **Do NOT amend, rebase, or force-push** without explicit instruction.

- When resolving merge conflicts, stage the resolution with `git add` but do
  not commit the merge. Summarize what was resolved and wait for approval.

### Build Verification

- **Always verify the build locally before staging changes.**
  For this project: `mvn clean install -pl jdm-core -am --no-transfer-progress`
  is the minimum check. If dist modules are modified, run the full reactor.

- Do not stage or report success until the build passes without new errors.
  Pre-existing warnings (e.g. `com.sun.nio.file.ExtendedOpenOption`) are known
  and can be ignored.

---

## Project Overview

**JDiskMark** is a cross-platform Java disk benchmark utility.

- **Language:** Java 25 (preview features enabled)
- **Build system:** Maven (migrated from Ant; `build.xml` retained for legacy reference)
- **Main branch for active development:** `maven`
- **Entry point:** `jdiskmark.App`

### Module Structure

```
jdm-java/                    ← root POM (aggregator)
├── jdm-core/                ← application source (shade fat jar via maven-shade-plugin)
└── jdm-dist/                ← packaging aggregator
    ├── jdm-deb/             ← Linux .deb — fat pkg, bundled JRE (jpackage, -Plinux-deb)
    ├── jdm-deb-slim/        ← Linux .deb — slim pkg, system JRE (jdeb plugin, -Plinux-deb-slim)
    ├── jdm-msi/             ← Windows .msi — bundled JRE (jpackage, -Pwindows-msi)
    ├── jdm-rpm/             ← Linux .rpm — bundled JRE (jpackage, -Plinux-rpm)
    ├── jdm-flatpak/         ← Linux Flatpak (-Plinux-flatpak)
    ├── jdm-pkg/             ← macOS .pkg (-Pmacos-pkg)
    └── jdm-zip/             ← legacy zip packager (disabled, kept for reference)
```

### Key Build Commands

| Goal | Command |
|---|---|
| Build core only (fastest check) | `mvn clean install -pl jdm-core -am --no-transfer-progress` |
| Full reactor (all modules) | `mvn clean install --no-transfer-progress` |
| Fat DEB (Linux only) | `mvn clean install -pl jdm-core,jdm-dist/jdm-deb -am -Plinux-deb` |
| Slim DEB (Linux only) | `mvn clean install -pl jdm-core,jdm-dist/jdm-deb-slim -am -Plinux-deb-slim` |
| Windows MSI (Windows only) | `mvn clean install -pl jdm-core,jdm-dist/jdm-msi -am -Pwindows-msi` |

### Version Properties

When bumping the project version, also update these in the root `pom.xml`:
- `msi.version` — MSI format requires purely numeric `Major.Minor.Build`
- `rpm.version` — RPM format, no hyphens
- `dmg.version` — macOS, must be ≥ `1.0.0`

### CI/CD

GitHub Actions workflows in `.github/workflows/`:
- `linux-deb.yml` — builds both fat DEB (`build-deb-fat`) and slim DEB (`build-deb-slim`)
- Other workflows exist per platform

---

## Code Conventions

- Issue numbers are referenced in commit messages as `#N` (e.g. `#15 port thin deb to Maven`)
- Commit message format: `#N short description` with a blank line and body for context
- NetBeans is the primary IDE; `.form` files are NetBeans GUI builder format — do not  edit layout code inside `initComponents()` or NetBeans generated files or .form files.
