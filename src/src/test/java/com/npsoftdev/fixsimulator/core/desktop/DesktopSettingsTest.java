package com.npsoftdev.fixsimulator.core.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopSettingsTest {

    @TempDir
    Path dataDir;

    @Test
    void startupNoticeIsShownOnAFreshInstall() {
        assertFalse(new DesktopSettings(dataDir).isStartupNoticeSuppressed());
    }

    @Test
    void suppressingTheNoticeSurvivesARestart() {
        new DesktopSettings(dataDir).suppressStartupNotice();

        // A new instance reads the file rather than any in-memory state.
        assertTrue(new DesktopSettings(dataDir).isStartupNoticeSuppressed());
    }

    @Test
    void writesTheSettingsFileIntoTheDataDirectory() {
        new DesktopSettings(dataDir).suppressStartupNotice();

        assertTrue(Files.exists(dataDir.resolve(DesktopSettings.FILE_NAME)));
    }

    @Test
    void createsTheDataDirectoryIfItDoesNotExistYet() {
        Path missing = dataDir.resolve("not-created-yet");

        new DesktopSettings(missing).suppressStartupNotice();

        assertTrue(new DesktopSettings(missing).isStartupNoticeSuppressed());
    }

    @Test
    void aCorruptFileFallsBackToDefaultsRatherThanThrowing() throws IOException {
        Files.writeString(dataDir.resolve(DesktopSettings.FILE_NAME), "\t: not: valid: yaml: [");

        assertFalse(new DesktopSettings(dataDir).isStartupNoticeSuppressed());
    }

    @Test
    void anUnknownKeyFromANewerVersionIsIgnored() throws IOException {
        Files.writeString(dataDir.resolve(DesktopSettings.FILE_NAME),
                "startupNoticeSuppressed: true\nsomeFutureSetting: 42\n");

        assertTrue(new DesktopSettings(dataDir).isStartupNoticeSuppressed());
    }
}
