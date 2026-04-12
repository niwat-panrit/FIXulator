# ──────────────────────────────────────────────────────────────────────────────
# FIXulator – Dev Container Image
# Base : Microsoft devcontainers/java (JDK 17, Debian Bookworm)
# Extra : JDK 21 (Temurin) installed in parallel via SDKMAN
#         Maven 3.9.x, Git, common dev tools
# ──────────────────────────────────────────────────────────────────────────────
FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm

# Run all RUN steps as root; the container user will be 'vscode' (set in devcontainer.json)
USER root

# ── System packages ────────────────────────────────────────────────────────────
# ROOT CAUSE FIX: The base image ships with a Yarn apt source whose GPG key has
# expired (NO_PUBKEY 62D54FD4003F6525). Remove it before running apt-get update,
# otherwise the whole update step fails with exit code 100.
#
# The base image already includes: curl, wget, git, unzip, zip, ca-certificates
# so we only install the tools that are genuinely missing.
RUN rm -f /etc/apt/sources.list.d/yarn.list \
          /usr/share/keyrings/yarnkey.gpg \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        jq \
        tree \
        htop \
        procps \
        lsof \
        net-tools \
    && rm -rf /var/lib/apt/lists/*

# ── Install JDK 21 alongside JDK 17 via SDKMAN ────────────────────────────────
# SDKMAN is already installed by the base image at /usr/local/sdkman
# We install JDK 21 (Temurin) and keep JDK 17 (Temurin) as the *current* default.
# Inside the container you can switch with:
#   sdk use java 21-tem      (session)
#   sdk default java 21-tem  (persistent)
RUN bash -c " \
    source /usr/local/sdkman/bin/sdkman-init.sh \
    && sdk install java 21.0.7-tem \
    && sdk install java 17.0.15-tem \
    && sdk default java 17.0.15-tem \
    && ln -sfn /usr/local/sdkman/candidates/java/21.0.7-tem  /usr/local/sdkman/candidates/java/21-tem \
    && ln -sfn /usr/local/sdkman/candidates/java/17.0.15-tem /usr/local/sdkman/candidates/java/17-tem \
"

# ── Install Maven (latest 3.9.x) ───────────────────────────────────────────────
RUN bash -c " \
    source /usr/local/sdkman/bin/sdkman-init.sh \
    && sdk install maven \
"

# ── SDKMAN init for all interactive shells ──────────────────────────────────────
RUN echo 'source /usr/local/sdkman/bin/sdkman-init.sh' >> /etc/bash.bashrc

# ── Ownership so vscode user can manage SDKMAN ────────────────────────────────
RUN chown -R vscode:vscode /usr/local/sdkman

USER vscode