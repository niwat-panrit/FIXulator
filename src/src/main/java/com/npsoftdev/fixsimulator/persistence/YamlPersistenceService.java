package com.npsoftdev.fixsimulator.persistence;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Low-level YAML read/write helper used by the persistent repository
 * implementations.
 *
 * <p>All writes are atomic: the payload is first written to a {@code .tmp}
 * sibling file, then renamed over the target in one OS-level operation so
 * a crash mid-write never produces a half-written file.</p>
 *
 * <p>The {@link ObjectMapper} is configured to:
 * <ul>
 *   <li>Omit {@code null} values from output (cleaner YAML).</li>
 *   <li>Suppress the leading {@code ---} document-start marker.</li>
 *   <li>Minimise unnecessary quoting while still quoting strings that
 *       look like YAML scalars (numbers, booleans, etc.).</li>
 *   <li>Ignore unknown properties on deserialization so that a newer
 *       file version degrades gracefully on an older binary.</li>
 * </ul>
 */
public class YamlPersistenceService implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(YamlPersistenceService.class);

    private final Path dataDir;

    /**
     * Transient: reconstructed after Java serialization or on first use.
     * Marked transient because {@link ObjectMapper} is not serializable.
     */
    private transient ObjectMapper mapper;

    public YamlPersistenceService(Path dataDir) {
        this.dataDir = dataDir;
        this.mapper  = buildMapper();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code filename} already exists inside the
     * configured data directory.
     */
    public boolean exists(String filename) {
        return Files.exists(dataDir.resolve(filename));
    }

    /**
     * Deserializes {@code filename} from the data directory into an instance of
     * {@code type}.
     *
     * @throws IOException if the file cannot be read or the YAML is malformed
     */
    public <T> T load(String filename, Class<T> type) throws IOException {
        return mapper().readValue(dataDir.resolve(filename).toFile(), type);
    }

    /**
     * Atomically serializes {@code data} to {@code filename} inside the data
     * directory, creating the directory if necessary.
     *
     * <p>The write sequence is:
     * <ol>
     *   <li>Create parent directories.</li>
     *   <li>Write to {@code <filename>.tmp}.</li>
     *   <li>Rename the tmp file over the target (atomic on POSIX; best-effort
     *       on Windows via {@link StandardCopyOption#REPLACE_EXISTING}).</li>
     * </ol>
     *
     * @throws IOException if the write or rename fails
     */
    public void save(String filename, Object data) throws IOException {
        Path target = dataDir.resolve(filename);
        Path tmp    = target.resolveSibling(target.getFileName() + ".tmp");

        Files.createDirectories(target.getParent());
        try {
            mapper().writeValue(tmp.toFile(), data);
            atomicMove(tmp, target);
        } catch (IOException e) {
            silentDelete(tmp);
            throw e;
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private ObjectMapper mapper() {
        if (mapper == null) mapper = buildMapper();
        return mapper;
    }

    private static ObjectMapper buildMapper() {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                // Emit polymorphic type as a regular `type:` property rather than
                // a native YAML type tag (`!<typename>`), which is harder to edit by hand.
                .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID)
                // MINIMIZE_QUOTES removed: it causes type confusion where string values
                // like "true", "null", or "1234" are written as unquoted YAML scalars
                // and then deserialized as booleans / null / integers instead of strings.
                .build();
        return new ObjectMapper(factory)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    private static void atomicMove(Path src, Path dst) throws IOException {
        try {
            Files.move(src, dst,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Fall back to a non-atomic replace (acceptable on Windows / some FS)
            log.debug("Atomic move not supported on this filesystem; falling back to replace: {}", e.getMessage());
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void silentDelete(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
    }

    // Restore transient mapper after Java deserialization
    private Object readResolve() {
        this.mapper = buildMapper();
        return this;
    }
}
