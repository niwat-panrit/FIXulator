package com.npsoftdev.fixsimulator.plugins.order.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Persists order and trade state to per-session-per-day JSON files so that
 * in-memory data survives an application restart within the same FIX session period.
 *
 * <h3>File naming</h3>
 * <pre>data/cache/{sanitizedSessionId}-{YYYY-MM-DD}.json</pre>
 * The date component is the UTC date captured <em>once at construction time</em>
 * (i.e. when the application starts).  This means:
 * <ul>
 *   <li>A restart within the same calendar day reloads the existing file.</li>
 *   <li>If the app runs past midnight without a restart it keeps writing to the
 *       same file — matching the requirement that session state is continuous
 *       across the session-end boundary until the next restart.</li>
 *   <li>A restart after midnight creates a new file for the new date, giving a
 *       clean slate for the new session period.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * Writes are atomic: the payload is first written to a {@code .tmp} sidecar and
 * then moved into place, so a crash mid-write never corrupts the live file.
 */
public class OrderTradeCacheService implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(OrderTradeCacheService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Path cacheDir;

    /**
     * UTC date captured once at startup — fixed for the entire process lifetime.
     * Determines which cache file is read/written for this run.
     */
    private final LocalDate sessionDate;

    /** Transient: recreated after deserialization. */
    private transient ObjectMapper mapper;

    public OrderTradeCacheService(Path cacheDir) {
        this.cacheDir    = cacheDir;
        this.sessionDate = LocalDate.now(ZoneOffset.UTC);
        log.info("Order/trade cache initialised — session date={}, dir={}", sessionDate, cacheDir);
    }

    // ── File path ─────────────────────────────────────────────────────────────

    Path cachePath(String sessionId) {
        String safe     = sessionId.replaceAll("[:/\\\\*?\"<>|]", "-");
        String filename = safe + "-" + DATE_FMT.format(sessionDate) + ".json";
        return cacheDir.resolve(filename);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /**
     * Serialises the current order and trade lists for {@code sessionId} to disk.
     * The write is atomic: a temporary file is written first, then renamed.
     */
    public void save(String sessionId,
                     List<Map<Integer, String>> orders,
                     List<Map<Integer, String>> trades) {
        try {
            Files.createDirectories(cacheDir);
            Path target = cachePath(sessionId);
            Path tmp    = cacheDir.resolve(target.getFileName() + ".tmp");

            Map<String, Object> payload = Map.of(
                    "sessionId",   sessionId,
                    "sessionDate", DATE_FMT.format(sessionDate),
                    "savedAt",     Instant.now().toString(),
                    "orders",      orders,
                    "trades",      trades
            );

            mapper().writeValue(tmp.toFile(), payload);
            Files.move(tmp, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save order/trade cache for session {}: {}",
                    sessionId, e.getMessage(), e);
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    /** Holds the deserialized orders and trades from a cache file. */
    public record CacheData(List<Map<Integer, String>> orders,
                            List<Map<Integer, String>> trades) {}

    /**
     * Reads the cache file for {@code sessionId} and the current session date.
     * Returns {@code null} if no file exists or if it cannot be parsed.
     */
    public CacheData load(String sessionId) {
        Path path = cachePath(sessionId);
        if (!Files.exists(path)) {
            log.info("No order/trade cache for session {} on {} — starting fresh",
                    sessionId, sessionDate);
            return null;
        }
        try {
            Map<String, Object> raw = mapper().readValue(
                    path.toFile(), new TypeReference<Map<String, Object>>() {});

            List<Map<Integer, String>> orders = mapper().convertValue(
                    raw.get("orders"), new TypeReference<List<Map<Integer, String>>>() {});
            List<Map<Integer, String>> trades = mapper().convertValue(
                    raw.get("trades"), new TypeReference<List<Map<Integer, String>>>() {});

            int orderCount = orders != null ? orders.size() : 0;
            int tradeCount = trades != null ? trades.size() : 0;
            log.info("Loaded cache for session {} — {} order(s), {} trade(s) from {}",
                    sessionId, orderCount, tradeCount, path.getFileName());

            return new CacheData(
                    orders != null ? orders : List.of(),
                    trades != null ? trades : List.of());
        } catch (IOException e) {
            log.error("Failed to read order/trade cache for session {} ({}): {}",
                    sessionId, path.getFileName(), e.getMessage(), e);
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ObjectMapper mapper() {
        if (mapper == null) {
            mapper = new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);
        }
        return mapper;
    }
}
