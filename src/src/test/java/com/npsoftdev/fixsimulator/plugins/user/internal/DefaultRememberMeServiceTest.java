package com.npsoftdev.fixsimulator.plugins.user.internal;

import com.npsoftdev.fixsimulator.plugins.persistence.api.YamlPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultRememberMeServiceTest {

    @TempDir
    Path tempDir;

    private DefaultRememberMeService service;

    @BeforeEach
    void setUp() {
        service = new DefaultRememberMeService(new YamlPersistenceService(tempDir));
    }

    // ── createToken ───────────────────────────────────────────────────────────

    @Test
    void createToken_returnsNonBlankString() {
        String token = service.createToken("alice");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void createToken_eachCallProducesUniqueToken() {
        String t1 = service.createToken("alice");
        String t2 = service.createToken("alice");
        assertNotEquals(t1, t2);
    }

    @Test
    void createToken_isUrlSafeBase64() {
        String token = service.createToken("alice");
        // URL-safe Base64 uses A-Z, a-z, 0-9, - and _ (no + or / or =)
        assertTrue(token.matches("[A-Za-z0-9_-]+"),
                "Token should be URL-safe Base64 without padding: " + token);
    }

    // ── resolveToken ──────────────────────────────────────────────────────────

    @Test
    void resolveToken_validToken_returnsUsername() {
        String token = service.createToken("alice");
        Optional<String> resolved = service.resolveToken(token);
        assertTrue(resolved.isPresent());
        assertEquals("alice", resolved.get());
    }

    @Test
    void resolveToken_unknownToken_returnsEmpty() {
        Optional<String> result = service.resolveToken("no-such-token");
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveToken_nullToken_returnsEmpty() {
        assertTrue(service.resolveToken(null).isEmpty());
    }

    @Test
    void resolveToken_blankToken_returnsEmpty() {
        assertTrue(service.resolveToken("   ").isEmpty());
    }

    // ── deleteToken ───────────────────────────────────────────────────────────

    @Test
    void deleteToken_removesToken() {
        String token = service.createToken("alice");
        service.deleteToken(token);
        assertTrue(service.resolveToken(token).isEmpty());
    }

    @Test
    void deleteToken_unknownToken_doesNotThrow() {
        assertDoesNotThrow(() -> service.deleteToken("no-such-token"));
    }

    @Test
    void deleteToken_doesNotAffectOtherTokens() {
        String t1 = service.createToken("alice");
        String t2 = service.createToken("alice");
        service.deleteToken(t1);

        assertTrue(service.resolveToken(t1).isEmpty());
        assertTrue(service.resolveToken(t2).isPresent());
    }

    // ── purgeExpired ──────────────────────────────────────────────────────────

    @Test
    void purgeExpired_validTokenSurvives() {
        String token = service.createToken("alice");
        service.purgeExpired();
        assertTrue(service.resolveToken(token).isPresent());
    }

    // ── Persistence across service instances ──────────────────────────────────

    @Test
    void token_survivesServiceRestart() {
        String token = service.createToken("alice");

        // Create a new service instance pointing to the same data directory
        DefaultRememberMeService service2 =
                new DefaultRememberMeService(new YamlPersistenceService(tempDir));

        Optional<String> resolved = service2.resolveToken(token);
        assertTrue(resolved.isPresent());
        assertEquals("alice", resolved.get());
    }

    @Test
    void delete_persistedAcrossServiceInstances() {
        String token = service.createToken("alice");
        service.deleteToken(token);

        DefaultRememberMeService service2 =
                new DefaultRememberMeService(new YamlPersistenceService(tempDir));

        assertTrue(service2.resolveToken(token).isEmpty());
    }

    // ── Multiple users ────────────────────────────────────────────────────────

    @Test
    void multipleUsers_tokensAreIndependent() {
        String tokenAlice = service.createToken("alice");
        String tokenBob   = service.createToken("bob");

        assertEquals("alice", service.resolveToken(tokenAlice).orElseThrow());
        assertEquals("bob",   service.resolveToken(tokenBob).orElseThrow());
    }
}
