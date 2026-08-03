#!/usr/bin/env bash
#
# Builds a native FIXulator installer for the CURRENT platform.
#
# jpackage cannot cross-compile: run this on macOS for a .dmg, on Debian or
# Ubuntu for a .deb, and on a Red Hat-family host for an .rpm. Windows uses
# the PowerShell sibling of this script, jpackage.ps1.
#
#   ./packaging/jpackage.sh              # native installer for this platform
#   ./packaging/jpackage.sh app-image    # unpacked app directory, no installer
#   ./packaging/jpackage.sh deb rpm      # explicit types (Linux only)
#
set -euo pipefail

APP_NAME="FIXulator"
APP_VERSION="${APP_VERSION:-1.0.0}"
VENDOR="Niwat Panrit"
DESCRIPTION="FIX protocol simulator for testing — not for production trading"
MAIN_CLASS="com.npsoftdev.fixsimulator.Main"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$REPO_ROOT/src"
JAR_NAME="fix-simulator.jar"
STAGE_DIR="$SRC_DIR/target/jpackage-input"
OUT_DIR="$SRC_DIR/target/installers"

command -v jpackage >/dev/null || {
    echo "jpackage not found — needs JDK 14 or newer on PATH" >&2; exit 1; }

# ── Build the fat JAR ────────────────────────────────────────────────────────
# Tests are deliberately skipped: packaging builds an artefact from source that
# CI has already tested, and Mockito's inline mock maker fails on JDK 21+, which
# is what a developer packaging locally is likely to have on PATH. Run
# `mvn test` (JDK 17) before packaging if you want the suite.
echo "==> Building $JAR_NAME"
(cd "$SRC_DIR" && mvn -q clean package -DskipTests)

# jpackage copies the whole --input directory into the image, so stage only
# the JAR. Anything else here would be shipped to users.
rm -rf "$STAGE_DIR" && mkdir -p "$STAGE_DIR" "$OUT_DIR"
cp "$SRC_DIR/target/$JAR_NAME" "$STAGE_DIR/"

# ── Decide what to build ─────────────────────────────────────────────────────
if [ $# -gt 0 ]; then
    TYPES=("$@")
else
    case "$(uname -s)" in
        Darwin) TYPES=(dmg) ;;
        Linux)  if   command -v dpkg-deb >/dev/null; then TYPES=(deb)
                elif command -v rpmbuild >/dev/null; then TYPES=(rpm)
                else echo "Neither dpkg-deb nor rpmbuild found" >&2; exit 1; fi ;;
        *)      echo "Unsupported platform $(uname -s); use jpackage.ps1 on Windows" >&2
                exit 1 ;;
    esac
fi

# ── Common options ───────────────────────────────────────────────────────────
# fixulator.packaged tells AppHome to keep runtime data in the per-user
# application-data directory: the install location is not user-writable.
COMMON=(
    --name             "$APP_NAME"
    --app-version      "$APP_VERSION"
    --vendor           "$VENDOR"
    --description      "$DESCRIPTION"
    --input            "$STAGE_DIR"
    --main-jar         "$JAR_NAME"
    --main-class       "$MAIN_CLASS"
    --dest             "$OUT_DIR"
    --java-options     "-Dfixulator.packaged=true"
    --java-options     "-Xmx512m"
)

for TYPE in "${TYPES[@]}"; do
    echo "==> jpackage --type $TYPE"
    EXTRA=()
    # --license-file is rejected for app-image, which produces no installer.
    [ "$TYPE" != "app-image" ] && [ -f "$REPO_ROOT/LICENSE" ] \
        && EXTRA+=(--license-file "$REPO_ROOT/LICENSE")
    case "$TYPE" in
        dmg|pkg)
            EXTRA+=(--mac-package-name "$APP_NAME") ;;
        deb)
            EXTRA+=(--linux-shortcut
                    --linux-menu-group "Development"
                    --linux-deb-maintainer "niwat.panrit@gmail.com"
                    --linux-app-category "devel") ;;
        rpm)
            EXTRA+=(--linux-shortcut
                    --linux-menu-group "Development"
                    --linux-rpm-license-type "ASL 2.0"
                    --linux-app-category "Development") ;;
    esac
    # ${EXTRA[@]+…} guards the empty-array case: bash 3.2, which macOS still
    # ships, treats "${EXTRA[@]}" as unbound under `set -u` when EXTRA is empty.
    jpackage --type "$TYPE" "${COMMON[@]}" ${EXTRA[@]+"${EXTRA[@]}"}
done

echo
echo "==> Installers in $OUT_DIR"
ls -la "$OUT_DIR"
