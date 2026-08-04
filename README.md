# FIXulator

> A FIX protocol initiator simulator — connect sessions, send orders, and watch
> the message flow, without a counterparty exchange.

## ⚠️ Disclaimer — testing tool, not for production trading

FIXulator exists to **test and develop** FIX integrations. It is not, and is not
intended to be, production trading software. Do not point it at a live venue or
use it to send real orders.

It is provided **"AS IS", without warranty or condition of any kind**, and the
authors accept no liability for any loss arising from its use — financial or
otherwise. See [LICENSE](LICENSE) sections 7 and 8 for the binding text.

## Install

Native installers bundle their own Java runtime, so **no JDK is required**.
Download the one for your platform from the
[latest release](https://github.com/niwat-panrit/FIXulator/releases):

| Platform | File |
|---|---|
| Windows | `.msi` |
| Debian / Ubuntu | `.deb` |
| Red Hat / Fedora | `.rpm` |
| macOS | `.dmg` |

An installed build keeps its data in your user profile, not next to the
application — see [packaging/README.md](packaging/README.md) for the exact
locations and how to override them. To build the installers yourself, see the
same document.

## Documentation

| Document | For |
|---|---|
| [User Guide](docs/index.html) | Using the app — install, connect a session, send orders, build templates, manage users |
| [Application Specification](src/APPLICATION_SPEC.md) | Developers — architecture, plugin layout, design decisions |
| [Packaging](packaging/README.md) | Building the native installers |

## Prerequisites (development, on the host machine)

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

## Running the Application

The application is a Maven-based Java project (Apache Wicket + embedded Jetty), located in the `src/` directory. Maven Shade builds a self-contained fat JAR whose `Main` class starts Jetty — there is no servlet container to deploy to and no `jetty:run` goal.

```bash
cd src

# Build the fat JAR → target/fix-simulator.jar
mvn package -q

# Run it (default port 8080)
java -jar target/fix-simulator.jar

# Run it on a different port
java -jar target/fix-simulator.jar 9090
```

The app will be available at **http://localhost:8080**.

Run the test suite on its own with:

```bash
cd src && mvn test
```

Logs are written to `src/logs/`. See [`src/APPLICATION_SPEC.md`](src/APPLICATION_SPEC.md) for the full application specification.

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
├── src/                    # Maven project root
│   ├── pom.xml
│   ├── src/main/java/      # Java source
│   ├── data/               # YAML config: users, templates, value mappings
│   ├── logs/               # Runtime logs
│   ├── target/             # Build output — fix-simulator.jar
│   └── APPLICATION_SPEC.md # Full application specification
├── Samples/                # Reference Wicket / JSF sample apps
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
| `8080` | Application server (override with `java -jar target/fix-simulator.jar <port>`) |

---

## Volumes (persistent across rebuilds)

| Volume | Mounted at | Purpose |
|---|---|---|
| `fixulator-claude-config-*` | `~/.claude` | Claude Code auth & config |
| `fixulator-m2` | `~/.m2/repository` | Maven dependency cache |

To enter a shell inside the container:

```bash
docker exec -it fix_simulator_dev /bin/bash
```

---

## Licence

Licensed under the [Apache License 2.0](LICENSE) — free to use, modify, and
distribute, for **personal and commercial** purposes alike, including in closed-
source products. Your obligations are essentially: keep the licence and
copyright notice, state significant changes, and don't use the authors' names to
endorse your derivative.

The licence also disclaims all warranties and limits the authors' liability;
[NOTICE](NOTICE) lists the third-party components and their licences.

## Credits

Created by **Niwat Panrit** — <https://github.com/niwat-panrit>.

Built with **Claude** (Anthropic), which contributed to the implementation,
tests, and documentation.

Attribution is welcome but not required beyond what the licence asks for.
