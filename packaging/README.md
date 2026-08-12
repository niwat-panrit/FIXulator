# Packaging

Native installers built with [`jpackage`](https://docs.oracle.com/en/java/javase/17/jpackage/),
each bundling its own Java runtime — users do **not** need a JDK installed.

## The one rule

**`jpackage` cannot cross-compile.** Each installer must be built on the OS it
targets:

| Target | Build on | Command | Extra tools needed |
|---|---|---|---|
| `.dmg` / `.pkg` | macOS | `./packaging/jpackage.sh dmg` | — |
| `.deb` | Debian / Ubuntu | `./packaging/jpackage.sh deb` | `fakeroot`, `binutils` |
| `.rpm` | Fedora / RHEL / Rocky | `./packaging/jpackage.sh rpm` | `rpm-build` |
| `.msi` | Windows | `.\packaging\jpackage.ps1` | [WiX Toolset **v3**](https://wixtoolset.org) |
| `.exe` | Windows | `.\packaging\jpackage.ps1 -Type exe` | [WiX Toolset **v3**](https://wixtoolset.org) |

If you don't have all four platforms to hand, push a `v*` tag and let
`.github/workflows/release.yml` build them on GitHub's runners — that is what
the matrix is for.

**Windows needs WiX for both installer types**, not just the MSI. `jpackage`
does not write an MSI itself: it generates WiX source and invokes `candle.exe`
and `light.exe`, and `-Type exe` is a WiX bundle wrapping that same MSI. It must
be the **v3** line — v4 and v5 replaced those two tools with a single `wix.exe`,
so a v4 install leaves `jpackage` reporting the v3 tools as missing.

`app-image` is accepted on every platform — `./packaging/jpackage.sh app-image`,
or `.\packaging\jpackage.ps1 -Type app-image` — and produces an unpacked
application directory instead of an installer. It needs no WiX, and is both the
fastest way to check a packaging change and the easiest way to test
installed-build behaviour: the launcher it produces
(`src/target/installers/FIXulator/FIXulator.exe` on Windows) still passes
`-Dfixulator.packaged=true`, so it uses the per-user data directory exactly as
an installed copy would.

## Version

Defaults to `1.0.0`. Override with the `APP_VERSION` environment variable:

```bash
APP_VERSION=1.2.0 ./packaging/jpackage.sh dmg
```

In CI a `v*` tag sets it automatically (`v1.2.0` → `1.2.0`).

## How an installed build is stopped

An installed build has no console and no application window — it starts a web
server and sits there — so it puts an icon in the system tray (the notification
area on Windows, the menu bar on macOS). Right-click it for:

- **Open FIXulator** — opens the UI in your browser
- **Exit FIXulator** — stops the server and ends the process

The first run also shows a dialog explaining that closing the browser does not
stop the simulator, with a **Don't show this message again** checkbox. The
choice is stored in `desktop.yaml` in the data directory below; delete that file
to see the notice again.

On a host with no desktop the tray is skipped silently and the app runs exactly
as it always has — stop it with `Ctrl-C` or your service manager. Pass
`-Dfixulator.tray=false` to skip it on a desktop too.

## Where an installed build keeps its data

This is the part that differs from running the JAR. The install location is
read-only for a normal user — `/Applications`, `C:\Program Files`, `/opt/fixulator`
— so the launchers pass `-Dfixulator.packaged=true` and
`com.npsoftdev.fixsimulator.core.AppHome` puts everything writable
(`data/`, `logs/`, `fix-gateway.cfg`) under the per-user application-data directory:

| OS | Location |
|---|---|
| Windows | `%LOCALAPPDATA%\FIXulator` |
| macOS | `~/Library/Application Support/FIXulator` |
| Linux | `$XDG_DATA_HOME/fixulator`, else `~/.local/share/fixulator` |

Override it anywhere — a server data volume, a shared drive — with either:

```bash
-Dfixulator.home=/srv/fixulator      # JVM flag, wins over everything
FIXULATOR_HOME=/srv/fixulator        # environment variable
```

Running from a source checkout is unaffected: with neither set, the app home is
the working directory, exactly as before.

## Tests are not run by these scripts

Both scripts pass `-DskipTests`. Packaging builds an artefact from source that
has already been tested, and Mockito's inline mock maker fails on JDK 21+, which
is what you are likely to have on `PATH` when packaging locally. Run the suite
separately on JDK 17:

```bash
cd src && mvn test
```

The CI workflow does exactly this — a `test` job on JDK 17 gates the whole
matrix.

## Running on a server

The installers are desktop-oriented (they add menu entries and shortcuts). For a
headless server the fat JAR plus a service unit is simpler and easier to
automate:

```ini
# /etc/systemd/system/fixulator.service
[Unit]
Description=FIXulator
After=network.target

[Service]
User=fixulator
Environment=FIXULATOR_HOME=/srv/fixulator
ExecStart=/usr/bin/java -jar /opt/fixulator/fix-simulator.jar 8080
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

The `.deb`/`.rpm` installed launcher works too — `/opt/fixulator/bin/FIXulator 8080`
— if you prefer a single managed package.
