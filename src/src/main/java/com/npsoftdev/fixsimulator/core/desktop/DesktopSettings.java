package com.npsoftdev.fixsimulator.core.desktop;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Preferences for the desktop shell — the tray icon and the startup notice —
 * stored in {@code <app home>/data/desktop.yaml}.
 *
 * <p>These are per <em>installation</em>, not per application user: they answer
 * "has whoever runs this machine's copy been told the app lives in the tray?",
 * which is settled before anybody logs in. That is why this does not go through
 * {@code UserPreferencesService}.</p>
 *
 * <p>Reads and writes are deliberately forgiving. A missing or unreadable file
 * yields defaults, and a failed write is logged rather than thrown: neither is
 * worth interrupting startup over, and the worst case is that the startup
 * notice appears once more.</p>
 *
 * <p>Jackson is used directly rather than {@code YamlPersistenceService} to keep
 * {@code core} from depending on a plugin — the dependency runs the other way.</p>
 */
public class DesktopSettings {

    private static final Logger log = LoggerFactory.getLogger(DesktopSettings.class);

    /** File name inside the data directory. */
    static final String FILE_NAME = "desktop.yaml";

    private final Path file;

    /**
     * @param dataDir the app home's {@code data/} directory; it need not exist
     *                yet — it is created on the first write
     */
    public DesktopSettings(Path dataDir) {
        this.file = dataDir.resolve(FILE_NAME);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Whether the user has ticked "Don't show this message again" on the
     * startup notice. Defaults to {@code false}, so a fresh install shows it.
     */
    public boolean isStartupNoticeSuppressed() {
        return load().startupNoticeSuppressed;
    }

    /** Records that the startup notice must not be shown again. */
    public void suppressStartupNotice() {
        State state = load();
        state.startupNoticeSuppressed = true;
        save(state);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private State load() {
        if (!Files.exists(file)) return new State();
        try {
            State state = mapper().readValue(file.toFile(), State.class);
            return state != null ? state : new State();
        } catch (IOException e) {
            // A hand-edited or truncated file must not stop the app starting.
            log.warn("Could not read {} — using defaults: {}", file, e.getMessage());
            return new State();
        }
    }

    private void save(State state) {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            mapper().writeValue(tmp.toFile(), state);
            move(tmp, file);
        } catch (IOException e) {
            log.warn("Could not write {}: {}", file, e.getMessage());
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    private static void move(Path src, Path dst) throws IOException {
        try {
            Files.move(src, dst,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static ObjectMapper mapper() {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build();
        return new ObjectMapper(factory)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** The file's shape. Public fields so Jackson needs no accessors. */
    public static class State {
        /** Set by the "Don't show this message again" checkbox. */
        public boolean startupNoticeSuppressed;
    }
}
