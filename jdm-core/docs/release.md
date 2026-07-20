# CI Pre-Release Versioning

JDiskMark uses two version concepts for branch builds so that CI artifacts are
uniquely identifiable without polluting the install directory with build metadata.

---

## Two Version Concepts

| Concept | Property key | Used for | Example |
|---|---|---|---|
| **`display_version`** | `display.version` | About dialog, `jdm.properties` header, export headers | `0.8.0-ci.f.cleanup.47+175426` |
| **`install_version`** | `install.version` | Title bar, `.jdm/<version>/` directory, DB path | `0.8.0-ci.f.cleanup.47` |

Both are written into `META-INF/build.properties` at build time and read by
`App.DISPLAY_VERSION` / `App.INSTALL_VERSION` at runtime.  `App.VERSION` is
a back-compat alias for `DISPLAY_VERSION`.

---

## Three Build Modes

| Mode | How | `display_version` | `install_version` |
|---|---|---|---|
| **Local default** | `mvn install` | `0.8.0-SNAPSHOT` | `0.8.0-SNAPSHOT` |
| **Local opt-in** | `bash scripts/build-local.sh` (Git Bash) | `0.8.0-a.f.cleanup+175426` | `0.8.0-a.f.cleanup` |
| **CI pipeline** | GitHub Actions (automatic) | `0.8.0-ci.f.cleanup.47+175426` | `0.8.0-ci.f.cleanup.47` |

### Label anatomy

```
0.8.0 - ci . f.cleanup . 47  + 175426
  │      │       │         │     │
  │      │       │         │     └─ HHmmss timestamp (24-hour, America/Los_Angeles)
  │      │       │         └─────── run_number (CI) — not present locally
  │      │       └───────────────── abbreviated branch slug
  │      └───────────────────────── "ci" = pipeline build, "a" = adhoc local
  └──────────────────────────────── numeric base (msi.version, no SNAPSHOT)
```

`+175426` is SemVer build metadata — ignored for version ordering but visible
in filenames and display strings.

---

## Branch Abbreviation

| Branch | Abbreviation |
|---|---|
| `dev` | `d` |
| `dev/foo` | `d.foo` |
| `bug/crash-fix` | `b.crash-fix` |
| `bugfix/login` | `b.login` |
| `feature/dark-mode` | `f.dark-mode` |
| `feat/cleanup` | `f.cleanup` |
| anything else | leaf segment (part after last `/`) |

---

## Auto-Label Exclusion Branches

Auto-label generation is **skipped** on `main`, `release`, and
`release/**`.  On those branches both `display_version` and `install_version`
are set to the raw POM version (e.g. `0.8.0-SNAPSHOT`).  Engineers control
their own pre-release label by editing `<version>` in the root `pom.xml`.

---

## Local Opt-In (`build-local.sh`)

Engineers who want a branch-scoped Maven coordinate and install directory can
opt in by running the helper script from a **Git Bash** terminal:

```bash
# Open Git Bash, cd to the repo root, then:
bash scripts/build-local.sh
```

The script:
1. Reads `msi.version` from `pom.xml` via `grep`/`sed` (no Maven invocation).
2. Detects the current branch via `git rev-parse --abbrev-ref HEAD`.
3. Abbreviates it using the same rules as the CI composite action.
4. Stamps the time with `date +%H%M%S`.
5. Calls `mvn clean install -pl jdm-core -am -Drevision=... -Ddisplay.version=...`.

Result: the Maven coordinate **and** `.jdm/` install path both use
`0.8.0-a.<slug>`, matching what you see in the title bar.

> **Note:** Run from Git Bash (not PowerShell). Git Bash launched from
> PowerShell may not inherit `JAVA_HOME`, causing the Maven step to fail.
> Opening Git Bash directly always works.

To pass extra Maven flags (e.g. `-X` for debug):
```bash
bash scripts/build-local.sh -X
```

---

## CI Pipeline (GitHub Actions)

The composite action `.github/actions/ci-version` runs after `Set up JDK 25`
in every build job.  It:

1. Reads the POM version via `mvn help:evaluate` and strips `-SNAPSHOT`.
2. Checks whether the branch is excluded (`main`, `dev`, `release`, `release/**`).
3. Abbreviates the branch name using the same rules as the Ant profile.
4. Assembles `display_version` and `install_version` using `github.run_number`.
5. Outputs them as step outputs consumed by the `mvn` build step via:

```
-Ddisplay.version=<display_version>
-Dinstall.version=<install_version>
```

Package-specific extras:
- **MSI**: also passes `-Dmsi.version=<base_version>` (WiX requires purely numeric).
- **RPM**: also passes `-Drpm.version=<base_version>` (no hyphens allowed).

### Disabling for a manual run

In the GitHub Actions UI, trigger a workflow with:

```
disable_ci_label: true
```

This passes the raw POM version through unchanged — useful for producing a
plain build from a feature branch without the CI label.

---

## Per-Package Version Constraints

| Package | Constraint | Handled by |
|---|---|---|
| DEB fat / DEB slim | Accepts pre-release strings with `-` | No extra flag needed |
| RPM | No hyphens in version | `-Drpm.version=<base>` |
| MSI (WiX) | Purely numeric `Major.Minor.Build` | `-Dmsi.version=<base>` |
| macOS PKG | Must be ≥ `1.0.0` | `dmg.version` stays `1.0.0` in `pom.xml` |

---

## Maintenance

### Bumping the base version

Update `<version>`, `msi.version`, `rpm.version`, and `dmg.version` in the
root `pom.xml` as described in `AGENT.md`.  The CI label generation derives
its numeric base from `msi.version` automatically.

### Adding a new branch prefix

**In the CI action** (`.github/actions/ci-version`): add a new `case` entry
in the bash `case "$B" in` block.

**In the local-label profile** (`jdm-core/pom.xml`): add a new pair of
`<condition property="branch.abbrev" value="...">` + `<not><isset .../></not>`
blocks after the existing `f.*` block and before the fallback.

Both must use the same abbreviation for consistency.

### Timezone

The timestamp in both local and CI versions is in `America/Los_Angeles`.  To
change it: update `timezone="America/Los_Angeles"` in the `local-label` Ant
`<tstamp>` block and `date +%H%M%S` uses the runner's local clock in CI
(which is UTC on GitHub-hosted runners — acceptable since the timestamp is
informational only).

---

## Version Tags (Release Workflow)

Pushing a Git tag that matches `v*` triggers the unified release workflow
(`.github/workflows/release.yml`).  The tag name **overrides** the POM version:

```
git tag v1.0.0 && git push origin v1.0.0
```

The workflow strips the `v` prefix (`VERSION=${GITHUB_REF_NAME#v}`) and calls
`mvn versions:set -DnewVersion=$VERSION` in every build job.  This replaces
the POM `<version>` before the build runs, so the tag value becomes the
version embedded in all packages.

### Pre-release detection

The `create-release` job inspects the version to decide whether the GitHub
Release should be marked as a pre-release:

| Tag | Detected as | Why |
|---|---|---|
| `v1.0.0` | **release** | No hyphen suffix |
| `v2.0.0-beta` | **pre-release** | `x.y.z-label` pattern |
| `v0.8.0-rc1` | **pre-release** | `x.y.z-label` pattern |
| `v0.8.0-SNAPSHOT` | **pre-release** | matches `snapshot` keyword |

### Package-specific fixups

Some packages have format restrictions.  The release workflow handles these
automatically per job:

- **MSI**: extracts the numeric `Major.Minor.Build` from the tag version and
  writes it into `<msi.version>` via `sed` (WiX requires purely numeric).
- **RPM**: extracts the numeric base and writes it into `<rpm.version>` via
  `sed` (no hyphens allowed).
- **Flatpak**: also patches the `build.xml` `version` property.
