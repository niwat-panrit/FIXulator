#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# FIXulator – post-create.sh
# Runs once after the devcontainer is first created (postCreateCommand).
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " FIXulator – Container post-create setup"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── Repair ownership of mounted volumes and toolchains ───────────────────────
# Docker creates a named volume's mount point as root:root when that path does
# not exist in the image, which left ~/.m2/repository unwritable and broke
# `mvn` for the vscode user. The Dockerfile now pre-creates those paths so new
# volumes inherit the right owner — but a volume is seeded only once, at
# creation, and volumes survive rebuilds. Anything already root-owned has to be
# repaired here or it stays broken forever.
#
# Compared against the *current* uid rather than a hardcoded 1000 because
# updateRemoteUserUID can remap the vscode user to match the host account.
# Looks INSIDE the tree, not just at the top directory. ~/.m2 is created by the
# Dockerfile and is correctly owned, while the volume mounted at
# ~/.m2/repository beneath it is the part that comes up root-owned — a check of
# the top directory alone reports "fine" and repairs nothing.
# -quit stops at the first offender, so only the healthy case walks the tree.
ensure_owned() {
    local target="$1"
    [ -e "$target" ] || return 0

    local foreign
    foreign="$(find "$target" ! -user "$(id -u)" -print -quit 2>/dev/null || true)"
    [ -z "$foreign" ] && return 0

    if command -v sudo >/dev/null 2>&1; then
        echo "  repairing owner of $target (found $foreign)"
        sudo chown -R "$(id -u):$(id -g)" "$target"
    else
        echo "  WARNING: $foreign is not owned by uid $(id -u) and sudo is" >&2
        echo "           unavailable to fix it. Builds writing there will fail." >&2
    fi
}

echo ""
echo "▸ Ownership:"
ensure_owned "$HOME/.m2"
ensure_owned "$HOME/.claude"
ensure_owned "${SDKMAN_DIR:-/usr/local/sdkman}"
echo "  ok"

# ── Load SDKMAN ──────────────────────────────────────────────────────────────
source /usr/local/sdkman/bin/sdkman-init.sh

# ── Print Java versions ──────────────────────────────────────────────────────
echo ""
echo "▸ Installed JDKs:"
sdk list java | grep -E "(17\.|21\.)" | grep installed || true

echo ""
echo "▸ Default Java:"
java -version

echo ""
echo "▸ Maven:"
mvn -version

echo ""
echo "▸ Git:"
git --version

# ── Configure Git (safe directory inside workspace) ──────────────────────────
git config --global --add safe.directory /workspace

# ── Create .m2/settings.xml if not present (for local repo volume) ───────────
mkdir -p ~/.m2
if [ ! -f ~/.m2/settings.xml ]; then
cat > ~/.m2/settings.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0
                              https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <localRepository>/home/vscode/.m2/repository</localRepository>
</settings>
EOF
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " ✅  FIXulator container is ready!"
echo ""
echo "  Switch JDK : sdk use java 21-tem  |  sdk use java 17-tem"
echo "  Run Claude : claude"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
