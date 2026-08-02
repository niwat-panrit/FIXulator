package com.npsoftdev.fixsimulator.plugins.user.internal;

import com.npsoftdev.fixsimulator.plugins.persistence.api.YamlPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import com.npsoftdev.fixsimulator.plugins.user.api.RememberMeService;

/**
 * YAML-backed {@link RememberMeService} implementation.
 *
 * <p>Tokens are persisted to {@code remember-me-tokens.yaml} in the application
 * data directory so they survive process restarts.  Each token carries an expiry
 * timestamp (epoch-millis); expired tokens are removed by {@link #purgeExpired()}
 * which is called once at startup.</p>
 *
 * <p>All public methods are {@code synchronized} because the Wicket timer threads
 * and HTTP request threads may call them concurrently.</p>
 */
public class DefaultRememberMeService implements RememberMeService {

    private static final Logger log = LoggerFactory.getLogger(DefaultRememberMeService.class);

    /** Cookie / token lifetime: 30 days in milliseconds. */
    private static final long TOKEN_TTL_MS = 30L * 24 * 60 * 60 * 1000;

    private static final String FILENAME = "remember-me-tokens.yaml";

    private final YamlPersistenceService yaml;
    private final SecureRandom           rng = new SecureRandom();

    public DefaultRememberMeService(YamlPersistenceService yaml) {
        this.yaml = yaml;
    }

    // ── RememberMeService ─────────────────────────────────────────────────────

    @Override
    public synchronized String createToken(String username) {
        byte[] bytes = new byte[32];
        rng.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        TokenFile file = load();
        file.tokens.add(new TokenEntry(token, username, System.currentTimeMillis() + TOKEN_TTL_MS));
        save(file);

        log.debug("Remember-me token created for user '{}'", username);
        return token;
    }

    @Override
    public synchronized Optional<String> resolveToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        TokenFile file = load();
        long now = System.currentTimeMillis();
        for (TokenEntry entry : file.tokens) {
            if (token.equals(entry.token)) {
                if (entry.expiresAt > now) {
                    return Optional.of(entry.username);
                }
                // Token found but expired — clean it up
                file.tokens.remove(entry);
                save(file);
                log.debug("Remember-me token expired for user '{}'", entry.username);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized void deleteToken(String token) {
        if (token == null || token.isBlank()) return;
        TokenFile file = load();
        boolean removed = file.tokens.removeIf(e -> token.equals(e.token));
        if (removed) {
            save(file);
            log.debug("Remember-me token deleted");
        }
    }

    @Override
    public synchronized void purgeExpired() {
        TokenFile file = load();
        long now = System.currentTimeMillis();
        int before = file.tokens.size();
        file.tokens.removeIf(e -> e.expiresAt <= now);
        int removed = before - file.tokens.size();
        if (removed > 0) {
            save(file);
            log.info("Purged {} expired remember-me token(s)", removed);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private TokenFile load() {
        if (!yaml.exists(FILENAME)) return new TokenFile();
        try {
            TokenFile f = yaml.load(FILENAME, TokenFile.class);
            return f != null && f.tokens != null ? f : new TokenFile();
        } catch (IOException e) {
            log.warn("Could not read {}: {}", FILENAME, e.getMessage());
            return new TokenFile();
        }
    }

    private void save(TokenFile file) {
        try {
            yaml.save(FILENAME, file);
        } catch (IOException e) {
            log.error("Could not save {}: {}", FILENAME, e.getMessage());
        }
    }

    // ── YAML DTOs ─────────────────────────────────────────────────────────────

    public static class TokenFile {
        public List<TokenEntry> tokens = new ArrayList<>();
    }

    public static class TokenEntry {
        public String token;
        public String username;
        public long   expiresAt; // epoch-millis

        /** No-arg constructor required by Jackson. */
        public TokenEntry() {}

        public TokenEntry(String token, String username, long expiresAt) {
            this.token     = token;
            this.username  = username;
            this.expiresAt = expiresAt;
        }
    }
}
