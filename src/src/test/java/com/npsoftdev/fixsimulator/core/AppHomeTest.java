package com.npsoftdev.fixsimulator.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class AppHomeTest {

    private final String originalOs   = System.getProperty("os.name");
    private final String originalHome = System.getProperty("user.home");

    @AfterEach
    void restoreSystemProperties() {
        System.clearProperty(AppHome.HOME_PROPERTY);
        System.clearProperty(AppHome.PACKAGED_PROPERTY);
        System.setProperty("os.name", originalOs);
        System.setProperty("user.home", originalHome);
    }

    // ── resolve ───────────────────────────────────────────────────────────────

    @Test
    void resolve_defaultsToWorkingDirectory() {
        assertEquals(Paths.get(System.getProperty("user.dir")).toAbsolutePath(),
                AppHome.resolve(),
                "a source checkout must keep writing to ./data and ./logs as before");
    }

    @Test
    void resolve_explicitPropertyWins() {
        System.setProperty(AppHome.HOME_PROPERTY, "/srv/fixulator");
        assertEquals(Paths.get("/srv/fixulator"), AppHome.resolve());
    }

    @Test
    void resolve_explicitPropertyWinsOverPackagedFlag() {
        System.setProperty(AppHome.PACKAGED_PROPERTY, "true");
        System.setProperty(AppHome.HOME_PROPERTY, "/srv/fixulator");
        assertEquals(Paths.get("/srv/fixulator"), AppHome.resolve(),
                "an operator pinning a data volume must override the packaged default");
    }

    @Test
    void resolve_relativeOverrideIsMadeAbsolute() {
        System.setProperty(AppHome.HOME_PROPERTY, "relative-home");
        assertTrue(AppHome.resolve().isAbsolute());
    }

    @Test
    void resolve_blankPropertyIsIgnored() {
        System.setProperty(AppHome.HOME_PROPERTY, "   ");
        assertEquals(Paths.get(System.getProperty("user.dir")).toAbsolutePath(),
                AppHome.resolve());
    }

    @Test
    void resolve_packagedFlagMovesHomeOffTheWorkingDirectory() {
        System.setProperty(AppHome.PACKAGED_PROPERTY, "true");
        assertNotEquals(Paths.get(System.getProperty("user.dir")).toAbsolutePath(),
                AppHome.resolve(),
                "an installed build must never write next to its read-only install location");
    }

    // ── per-OS application data directory ─────────────────────────────────────

    @Test
    void perUserDirectory_macOsUsesApplicationSupport() {
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("user.home", "/Users/tester");
        assertEquals(Paths.get("/Users/tester/Library/Application Support/FIXulator"),
                AppHome.perUserDirectory());
    }

    @Test
    void perUserDirectory_windowsUsesFixulatorFolder() {
        System.setProperty("os.name", "Windows 11");
        System.setProperty("user.home", "C:\\Users\\tester");
        Path resolved = AppHome.perUserDirectory();
        assertTrue(resolved.endsWith("FIXulator"),
                "expected a FIXulator folder, got " + resolved);
    }

    @Test
    void perUserDirectory_linuxFallsBackToLocalShare() {
        System.setProperty("os.name", "Linux");
        System.setProperty("user.home", "/home/tester");
        Path resolved = AppHome.perUserDirectory();
        // XDG_DATA_HOME is honoured when set; the environment cannot be changed
        // from a test, so accept either the XDG path or the documented default.
        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg == null || xdg.isBlank()) {
            assertEquals(Paths.get("/home/tester/.local/share/fixulator"), resolved);
        } else {
            assertEquals(Paths.get(xdg, "fixulator"), resolved);
        }
    }

    @Test
    void perUserDirectory_unknownOsIsTreatedAsUnixLike() {
        System.setProperty("os.name", "SomeFutureOS");
        System.setProperty("user.home", "/home/tester");
        assertTrue(AppHome.perUserDirectory().endsWith("fixulator"));
    }
}
