package com.npsoftdev.fixsimulator.user;

import com.npsoftdev.fixsimulator.persistence.YamlPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class YamlUserPreferencesServiceTest {

    @TempDir
    Path tempDir;

    private YamlUserPreferencesService service;

    @BeforeEach
    void setUp() {
        service = new YamlUserPreferencesService(new YamlPersistenceService(tempDir));
    }

    // ── getLastActiveSession ──────────────────────────────────────────────────

    @Test
    void getLastActiveSession_noPrefsExist_returnsEmpty() {
        assertTrue(service.getLastActiveSession("alice").isEmpty());
    }

    @Test
    void getLastActiveSession_afterSet_returnsSessionId() {
        service.setLastActiveSession("alice", "FIX.4.4:SIM->EXCH");
        Optional<String> result = service.getLastActiveSession("alice");
        assertTrue(result.isPresent());
        assertEquals("FIX.4.4:SIM->EXCH", result.get());
    }

    @Test
    void getLastActiveSession_nullUsername_returnsEmpty() {
        assertTrue(service.getLastActiveSession(null).isEmpty());
    }

    // ── setLastActiveSession ──────────────────────────────────────────────────

    @Test
    void setLastActiveSession_updatesExistingEntry() {
        service.setLastActiveSession("alice", "session-old");
        service.setLastActiveSession("alice", "session-new");

        assertEquals("session-new", service.getLastActiveSession("alice").orElseThrow());
    }

    @Test
    void setLastActiveSession_nullSessionId_clearsPreference() {
        service.setLastActiveSession("alice", "some-session");
        service.setLastActiveSession("alice", null);

        assertTrue(service.getLastActiveSession("alice").isEmpty());
    }

    @Test
    void setLastActiveSession_blankSessionId_clearsPreference() {
        service.setLastActiveSession("alice", "some-session");
        service.setLastActiveSession("alice", "   ");

        assertTrue(service.getLastActiveSession("alice").isEmpty());
    }

    @Test
    void setLastActiveSession_nullUsername_doesNotThrow() {
        assertDoesNotThrow(() -> service.setLastActiveSession(null, "session-id"));
    }

    // ── Multiple users ────────────────────────────────────────────────────────

    @Test
    void multipleUsers_preferencesAreIndependent() {
        service.setLastActiveSession("alice", "sess-A");
        service.setLastActiveSession("bob",   "sess-B");

        assertEquals("sess-A", service.getLastActiveSession("alice").orElseThrow());
        assertEquals("sess-B", service.getLastActiveSession("bob").orElseThrow());
    }

    // ── Persistence across service instances ──────────────────────────────────

    @Test
    void preference_survivesServiceRestart() {
        service.setLastActiveSession("alice", "FIX.4.4:SIM->EXCH");

        YamlUserPreferencesService service2 =
                new YamlUserPreferencesService(new YamlPersistenceService(tempDir));

        Optional<String> result = service2.getLastActiveSession("alice");
        assertTrue(result.isPresent());
        assertEquals("FIX.4.4:SIM->EXCH", result.get());
    }

    @Test
    void cleared_preference_persistsAsClearedAcrossRestarts() {
        service.setLastActiveSession("alice", "old-session");
        service.setLastActiveSession("alice", null);

        YamlUserPreferencesService service2 =
                new YamlUserPreferencesService(new YamlPersistenceService(tempDir));

        assertTrue(service2.getLastActiveSession("alice").isEmpty());
    }

    // ── Unknown user returns empty ─────────────────────────────────────────────

    @Test
    void getLastActiveSession_unknownUser_returnsEmpty() {
        service.setLastActiveSession("alice", "session-a");
        assertTrue(service.getLastActiveSession("nobody").isEmpty());
    }
}
