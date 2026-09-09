# AGENT.md — JDiskMark Agent Guidelines

This file configures AI agent behavior for the `jdm-java` repository.
All rules in this file apply to every AI agent working in this codebase.

---

## Agent Workflow Rules

### Git Discipline

The default position is: **do nothing with git unless explicitly told to.**

- **Do NOT `git add` (stage)** any files without explicit user instruction.
  After completing code changes, summarize what was changed and why, then stop.
  The user will decide when and what to stage.

- **Do NOT `git commit`** without explicit user instruction.

- **Do NOT `git push`** without explicit user instruction.

- **Do NOT `git merge`, `git rebase`, `git reset`, `git amend`, or force-push**
  without explicit user instruction.

- **Merge conflict exception:** When the user explicitly asks you to resolve a
  merge conflict, you may stage the resolved files with `git add` after resolving.
  Do not commit the merge. Summarize each conflict resolved and wait for approval.

- **Branch:** Do not create, switch, or delete branches without explicit instruction.

---

### Design Documentation

The `jdm-core/docs/` folder contains authoritative design documents:

| File | Covers |
|---|---|
| [`design.md`](jdm-core/docs/design.md) | GUI layout decisions, active design trades, package organisation |
| [`theme.md`](jdm-core/docs/theme.md) | Theme and palette architecture, unlinked palette constraints, per-theme colour reference |

- **Read before coding.** Before making any change to theming, chart palettes,
  LAF configuration, or UI layout, read the relevant doc(s) in `jdm-core/docs/`.
  This prevents violating established constraints (e.g. unlinked palettes must
  not override LAF-owned chrome — see `theme.md`).

- **Update after coding.** If a code change affects something documented in
  `jdm-core/docs/` — a new palette, a renamed constant, a changed architecture
  decision, a new design constraint — update the relevant doc in the same session.
  Do not leave docs stale.

---

### Coding Practices & Conventions

- **No unrequested features.** Implement only what is asked.
- **No unrequested refactoring.** Do not restructure or rename unless told to.
- **Minimal comments.** Only add comments where the intent would otherwise be
  unclear. Do not add explanatory prose or redundant inline comments.
- **No new Maven dependencies** in any `pom.xml` without explicit instruction.
- **No new source files** without explicit instruction. When creating a file,
  confirm the target module and package first.
- **Java 25 preview features** are enabled and may be used where appropriate.
- **Commit message format:** `#N short description` (one line), blank line, then
  a body with context. Example: `#15 port thin deb to Maven`
- **Issue references:** Use `#N` to reference GitHub issue numbers in commits.
- **IDE:** NetBeans is the primary IDE. Do not reformat or reorganize files in
  ways that would conflict with NetBeans project settings.
- **Keep design docs current.** When a code change touches an area covered by
  `jdm-core/docs/`, update the relevant doc in the same session — do not leave
  design documentation out of sync with the code.
- Write robust, production-ready code that follows existing project conventions and remains easy to understand and maintain.

---

### NetBeans GUI Files

Three files in this project contain NetBeans GUI Builder generated code:
- `jdm-core/src/main/java/jdiskmark/MainFrame.java`
- `jdm-core/src/main/java/jdiskmark/BenchmarkPanel.java`
- `jdm-core/src/main/java/jdiskmark/SelectDriveFrame.java`

Each of those files has a corresponding `.form` file in the same directory.
**The `.java` and `.form` files are tightly coupled.** NetBeans regenerates
the Java from the `.form` on every GUI editor save. Editing one without
understanding the other will break the GUI editor.

#### Protected regions — NEVER edit

The following regions are entirely owned by the NetBeans GUI Builder.
**Do not read these as examples, do not modify them, do not write code that
spans their boundaries.**

| Marker pair | What it protects |
|---|---|
| `//GEN-BEGIN:initComponents` … `//GEN-END:initComponents` | All widget construction and layout code inside `initComponents()` |
| `// Variables declaration - do not modify//GEN-BEGIN:variables` … `//GEN-END:variables` | All GUI field declarations |

The `editor-fold` comments that wrap `initComponents` are cosmetic IDE markers
and are also part of the generated block.

#### Conditionally editable regions — body only

Event handler methods look like this:

```java
private void someActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_someActionPerformed
    // ← agent may edit the body here
}//GEN-LAST:event_someActionPerformed
```

- The **method signature line** (containing `//GEN-FIRST:`) and the **closing
  brace line** (containing `//GEN-LAST:`) are generated. Do not alter them.
- The **body between those two lines** is where user logic lives and may be
  edited by an agent when explicitly instructed to change handler behavior.
- **Never add, remove, or rename** an event handler method. That change must
  be made through the NetBeans GUI editor by a human developer, after which
  NetBeans will regenerate the `.java` from the `.form`.

#### `.form` files

Do not edit `.form` files unless explicitly instructed and you are certain of
the XML schema. A malformed `.form` silently breaks the GUI editor for all
team members.

---

### Build Verification

- **Always run the build after making code changes** before reporting success.
  Minimum check: `mvn clean install -pl jdm-core -am --no-transfer-progress`
  If any `jdm-dist` module was also modified, run the full reactor instead:
  `mvn clean install --no-transfer-progress`

- **Do not stage or report success if the build introduces new errors.**
  Pre-existing compiler warnings (e.g. `com.sun.nio.file.ExtendedOpenOption`)
  are known and may be ignored.

- **If the build fails due to your changes:** report the error, do not stage
  anything, and attempt to fix only if the error is clearly caused by your
  edit. Do not attempt to fix pre-existing unrelated errors.

---

## Project Reference

### Overview

**JDiskMark** is a cross-platform Java disk benchmark utility.

| Property | Value |
|---|---|
| Language | Java 25 (preview features enabled) |
| Build system | Maven (migrated from Ant; `build.xml` retained for reference only) |
| Active development branch | `dev` |
| Entry point | `jdiskmark.App` |

### Module Structure

```
jdm-java/                    ← root POM (aggregator)
├── jdm-core/                ← application source (fat jar via maven-shade-plugin)
└── jdm-dist/                ← packaging aggregator
    ├── jdm-deb/             ← Linux .deb — fat pkg, bundled JRE  (-Plinux-deb)
    ├── jdm-deb-slim/        ← Linux .deb — slim pkg, system JRE  (-Plinux-deb-slim)
    ├── jdm-msi/             ← Windows .msi — bundled JRE         (-Pwindows-msi)
    ├── jdm-rpm/             ← Linux .rpm — bundled JRE           (-Plinux-rpm)
    ├── jdm-flatpak/         ← Linux Flatpak                      (-Plinux-flatpak)
    ├── jdm-pkg/             ← macOS .pkg                         (-Pmacos-pkg)
    └── jdm-zip/             ← legacy zip packager (disabled, kept for reference)
```

### Key Build Commands

| Goal | Command |
|---|---|
| Core only (fastest check) | `mvn clean install -pl jdm-core -am --no-transfer-progress` |
| Full reactor | `mvn clean install --no-transfer-progress` |
| Fat DEB (Linux only) | `mvn clean install -pl jdm-core,jdm-dist/jdm-deb -am -Plinux-deb` |
| Slim DEB (Linux only) | `mvn clean install -pl jdm-core,jdm-dist/jdm-deb-slim -am -Plinux-deb-slim` |
| Windows MSI (Windows only) | `mvn clean install -pl jdm-core,jdm-dist/jdm-msi -am -Pwindows-msi` |

### Version Properties

When bumping the project version, also update these properties in the root `pom.xml`:

| Property | Constraint |
|---|---|
| `msi.version` | Purely numeric `Major.Minor.Build` |
| `rpm.version` | No hyphens |
| `dmg.version` | Must be ≥ `1.0.0` |

### CI/CD

GitHub Actions workflows are in `.github/workflows/`. Each platform has its own
workflow file (e.g. `linux-deb.yml`, `windows-msi.yml`). Do not modify workflow
files without explicit instruction.

---


