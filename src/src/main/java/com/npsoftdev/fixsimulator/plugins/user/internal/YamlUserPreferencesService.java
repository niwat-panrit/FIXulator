package com.npsoftdev.fixsimulator.plugins.user.internal;

import com.npsoftdev.fixsimulator.plugins.persistence.api.YamlPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.npsoftdev.fixsimulator.plugins.user.api.UserPreferencesService;

/**
 * YAML-backed {@link UserPreferencesService}.
 *
 * <p>Preferences are stored in {@code user-preferences.yaml} inside the
 * application data directory.  The file is small (one entry per user) and is
 * written atomically via {@link YamlPersistenceService}.</p>
 */
public class YamlUserPreferencesService implements UserPreferencesService {

    private static final Logger log = LoggerFactory.getLogger(YamlUserPreferencesService.class);
    private static final String FILENAME = "user-preferences.yaml";

    private final YamlPersistenceService yaml;

    public YamlUserPreferencesService(YamlPersistenceService yaml) {
        this.yaml = yaml;
    }

    // ── UserPreferencesService ────────────────────────────────────────────────

    @Override
    public synchronized Optional<String> getLastActiveSession(String username) {
        if (username == null) return Optional.empty();
        return load().entries.stream()
                .filter(e -> username.equals(e.username))
                .map(e -> e.lastActiveSessionId)
                .filter(s -> s != null && !s.isBlank())
                .findFirst();
    }

    @Override
    public synchronized void setLastActiveSession(String username, String sessionId) {
        if (username == null) return;
        PrefsFile file = load();
        PrefsEntry entry = file.entries.stream()
                .filter(e -> username.equals(e.username))
                .findFirst()
                .orElseGet(() -> {
                    PrefsEntry e = new PrefsEntry();
                    e.username = username;
                    file.entries.add(e);
                    return e;
                });
        entry.lastActiveSessionId = (sessionId != null && !sessionId.isBlank()) ? sessionId : null;
        save(file);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private PrefsFile load() {
        if (!yaml.exists(FILENAME)) return new PrefsFile();
        try {
            PrefsFile f = yaml.load(FILENAME, PrefsFile.class);
            return (f != null && f.entries != null) ? f : new PrefsFile();
        } catch (IOException e) {
            log.warn("Could not read {}: {}", FILENAME, e.getMessage());
            return new PrefsFile();
        }
    }

    private void save(PrefsFile file) {
        try {
            yaml.save(FILENAME, file);
        } catch (IOException e) {
            log.error("Could not save {}: {}", FILENAME, e.getMessage());
        }
    }

    // ── YAML DTOs ─────────────────────────────────────────────────────────────

    public static class PrefsFile {
        public List<PrefsEntry> entries = new ArrayList<>();
    }

    public static class PrefsEntry {
        public String username;
        public String lastActiveSessionId;
    }
}
