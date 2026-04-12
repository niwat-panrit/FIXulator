#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# FIXulator – post-create.sh
# Runs once after the devcontainer is first created (postCreateCommand).
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " FIXulator – Container post-create setup"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

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
