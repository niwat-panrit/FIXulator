package com.npsoftdev.fixsimulator.template;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link PlaceholderResolver} covering the six built-in placeholder
 * kinds. Stateless except for the {@link PlaceholderType#ORDER_ID} sequence
 * counter, which is monotonic per resolver instance and seeded from the
 * current epoch millis so IDs are unique across restarts in practice.
 */
public class DefaultPlaceholderResolver implements PlaceholderResolver {

    private static final long serialVersionUID = 1L;

    /** FIX UTCTimestamp format: YYYYMMDD-HH:MM:SS.sss */
    private static final DateTimeFormatter UTC_TS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS");

    private final AtomicLong orderIdSeq = new AtomicLong(System.currentTimeMillis());

    @Override
    public String resolve(PlaceholderType type, ResolutionContext ctx) {
        return switch (type) {
            case ORDER_ID       -> String.valueOf(orderIdSeq.incrementAndGet());
            case TRANSACT_TIME,
                 SENDING_TIME   -> utcNow();
            case UUID           -> UUID.randomUUID().toString();
            case SESSION_SENDER -> ctx.sessionID() == null ? "" : ctx.sessionID().getSenderCompID();
            case SESSION_TARGET -> ctx.sessionID() == null ? "" : ctx.sessionID().getTargetCompID();
        };
    }

    private static String utcNow() {
        return UTC_TS_FMT.format(LocalDateTime.now(ZoneOffset.UTC));
    }
}
