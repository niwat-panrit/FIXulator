# FIXulator

> Java application developed inside a sandboxed VS Code Dev Container.

## Prerequisites (host machine)

| Tool | Notes |
|---|---|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | Must be running |
| [VS Code](https://code.visualstudio.com/) | Any recent version |
| [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) | `ms-vscode-remote.remote-containers` |
| Anthropic account | For Claude Code (Pro / Max / API) |

---

## Quick Start

```bash
# 1. Clone the repo
git clone <repo-url> FIXulator
cd FIXulator

# 2. Open in VS Code
code .

# 3. VS Code will prompt "Reopen in Container" — click it
#    (or: Cmd+Shift+P → "Dev Containers: Reopen in Container")

# 4. First build takes ~3–5 min (downloads JDK 17 + 21, Maven)
#    Subsequent starts are instant.

# 5. Inside the container terminal, start Claude Code:
claude
```

---

## Java Versions

The container ships with **JDK 17** (default) and **JDK 21** installed side-by-side via SDKMAN.

```bash
# Check current version
java -version

# Switch to JDK 21 for this session
sdk use java 21-tem

# Switch to JDK 17 for this session
sdk use java 17-tem

# Make a version the permanent default
sdk default java 21-tem
```

To pin a version per-project, add a `.sdkmanrc` file to the project root:

```
java=17.0.15-tem
```

---

## Project Structure

```
FIXulator/
├── .devcontainer/
│   ├── devcontainer.json   # Container configuration
│   ├── Dockerfile          # Image definition (JDK 17 + 21, Maven)
│   └── post-create.sh      # One-time setup script
├── src/                    # Java source (added by Claude Code)
├── .gitignore
└── README.md
```

---

## Claude Code in the Sandbox

Claude Code runs **inside** the container. It can only access:
- `/workspace` — your project files
- The Maven volume (`~/.m2`) — cached dependencies

It **cannot** reach your host filesystem, host credentials, or unwhitelisted network endpoints.

```bash
# Run Claude Code (standard — confirms every action)
claude

# Run Claude Code (skip permission prompts — safe inside the container)
claude --dangerously-skip-permissions
```

---

## Ports

| Port | Purpose |
|---|---|
| `8080` | Application server |
| `5005` | JVM remote debug |

---

## Volumes (persistent across rebuilds)

| Volume | Mounted at | Purpose |
|---|---|---|
| `fixulator-claude-config-*` | `~/.claude` | Claude Code auth & config |
| `fixulator-m2` | `~/.m2/repository` | Maven dependency cache |

# to enter shell inside container, either

docker exec -it <container_name_or_id> /bin/bash
docker exec -it <container_name_or_id> sh
