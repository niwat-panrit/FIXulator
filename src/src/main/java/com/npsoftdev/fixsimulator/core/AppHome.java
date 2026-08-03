package com.npsoftdev.fixsimulator.core;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the directory that holds everything FIXulator writes at runtime —
 * {@code data/}, {@code logs/}, and {@code fix-gateway.cfg}.
 *
 * <p>Run from a source checkout, that is simply the working directory, which is
 * how the app has always behaved. Run from an installed package it cannot be:
 * jpackage installs into {@code /Applications}, {@code C:\Program Files}, or
 * {@code /opt}, none of which a normal user may write to. Installed builds
 * therefore pass {@code -Dfixulator.packaged=true} and get a per-user directory.</p>
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>{@code -Dfixulator.home=<path>} — wins everywhere; use it to point a
 *       packaged build at a fixed location such as a server's data volume.</li>
 *   <li>{@code FIXULATOR_HOME} environment variable — the same, for service
 *       managers that find it easier to set an env var than a JVM flag.</li>
 *   <li>{@code -Dfixulator.packaged=true} — per-user application data:
 *       {@code %LOCALAPPDATA%\FIXulator}, {@code ~/Library/Application Support/FIXulator},
 *       or {@code $XDG_DATA_HOME/fixulator} (default {@code ~/.local/share/fixulator}).</li>
 *   <li>Otherwise the working directory — unchanged development behaviour.</li>
 * </ol>
 */
public final class AppHome {

    /** Explicit override; absolute or relative to the working directory. */
    public static final String HOME_PROPERTY = "fixulator.home";
    /** Environment-variable form of {@link #HOME_PROPERTY}. */
    public static final String HOME_ENV = "FIXULATOR_HOME";
    /** Set by the jpackage launchers to mark an installed build. */
    public static final String PACKAGED_PROPERTY = "fixulator.packaged";

    private AppHome() {}

    /** The runtime home directory. Never null; not guaranteed to exist yet. */
    public static Path resolve() {
        String explicit = System.getProperty(HOME_PROPERTY);
        if (isSet(explicit)) return Paths.get(explicit).toAbsolutePath();

        String fromEnv = System.getenv(HOME_ENV);
        if (isSet(fromEnv)) return Paths.get(fromEnv).toAbsolutePath();

        if (Boolean.getBoolean(PACKAGED_PROPERTY)) return perUserDirectory();

        return Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    }

    /**
     * The per-user application-data directory for the current OS. Package-private
     * rather than private so the tests can exercise each platform's branch by
     * setting {@code os.name}.
     */
    static Path perUserDirectory() {
        String os   = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            return Paths.get(isSet(localAppData) ? localAppData : home, "FIXulator");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return Paths.get(home, "Library", "Application Support", "FIXulator");
        }
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        return isSet(xdgDataHome)
                ? Paths.get(xdgDataHome, "fixulator")
                : Paths.get(home, ".local", "share", "fixulator");
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }
}
