#!/usr/bin/env bash
# build-local.sh — Build jdm-core with a branch-derived pre-release label.
# Run with Git Bash on Windows, or native bash on Linux/macOS.
# Usage: bash scripts/build-local.sh [extra maven args...]
#
# Produces Maven coordinate:  jdm-core-<base>-a.<slug>
#   e.g. on feature/cleanup:  jdm-core-0.8.0-a.f.cleanup
#
# Also sets display.version with a +MMDD.HHMM timestamp for the title bar/About.
set -euo pipefail

# Read the authoritative numeric base directly from the root POM — fast and
# requires no Maven invocation. Update msi.version in pom.xml when bumping.
BASE=$(grep -m1 '<msi.version>' pom.xml | sed 's/[[:space:]]*<msi\.version>\([^<]*\)<\/msi\.version>.*/\1/')

# Detect branch; fall back to "local" on detached HEAD or missing git.
BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "local")
[ "$BRANCH" = "HEAD" ] && BRANCH="local"
LEAF="${BRANCH##*/}"

# Abbreviate — mirrors the table in .github/actions/ci-version/action.yml.
B="${BRANCH,,}"
case "$B" in
  dev)              SLUG="d"          ;;
  dev/*)            SLUG="d.${LEAF}"  ;;
  release)          SLUG="r"          ;;
  release/*)        SLUG="r.${LEAF}"  ;;
  bug/*|bugfix/*)   SLUG="b.${LEAF}"  ;;
  feature/*|feat/*) SLUG="f.${LEAF}"  ;;
  *)                SLUG="${LEAF:0:10}" ;;
esac
SLUG=$(printf '%s' "$SLUG" | sed 's/[^a-z0-9.-]//g; s/[.-]\{2,\}/./g; s/^[.-]*//; s/[.-]*$//')
SLUG="${SLUG:0:20}"

TIME=$(date +%m%d.%H%M)
INSTALL="${BASE}-a.${SLUG}"
DISPLAY="${BASE}-a.${SLUG}+${TIME}"

echo "branch:          ${BRANCH}"
echo "install version: ${INSTALL}   (Maven coordinate + .jdm/ path)"
echo "display version: ${DISPLAY}  (title bar / About / jdm.properties)"
echo ""

mvn clean install -pl jdm-core -am \
  -Drevision="${INSTALL}" \
  -Ddisplay.version="${DISPLAY}" \
  --no-transfer-progress \
  "$@"
