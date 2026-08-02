package com.npsoftdev.fixsimulator.plugins.template.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quickfix.SessionID;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import com.npsoftdev.fixsimulator.plugins.template.api.PlaceholderResolver;
import com.npsoftdev.fixsimulator.plugins.template.api.PlaceholderType;

class DefaultPlaceholderResolverTest {

    private DefaultPlaceholderResolver resolver;
    private static final SessionID SESSION = new SessionID("FIX.4.4", "SIMULATOR", "EXCHANGE");

    @BeforeEach
    void setUp() {
        resolver = new DefaultPlaceholderResolver();
    }

    private PlaceholderResolver.ResolutionContext ctx() {
        return new PlaceholderResolver.ResolutionContext(SESSION, Map.of());
    }

    private PlaceholderResolver.ResolutionContext noSession() {
        return new PlaceholderResolver.ResolutionContext(null, Map.of());
    }

    // ── ORDER_ID ──────────────────────────────────────────────────────────────

    @Test
    void orderId_isNumericString() {
        String id = resolver.resolve(PlaceholderType.ORDER_ID, ctx());
        assertNotNull(id);
        assertDoesNotThrow(() -> Long.parseLong(id));
    }

    @Test
    void orderId_isMonotonicallyIncreasing() {
        long first  = Long.parseLong(resolver.resolve(PlaceholderType.ORDER_ID, ctx()));
        long second = Long.parseLong(resolver.resolve(PlaceholderType.ORDER_ID, ctx()));
        long third  = Long.parseLong(resolver.resolve(PlaceholderType.ORDER_ID, ctx()));
        assertTrue(second > first,  "second ID must be greater than first");
        assertTrue(third  > second, "third ID must be greater than second");
    }

    // ── TRANSACT_TIME / SENDING_TIME ─────────────────────────────────────────

    @Test
    void transactTime_matchesFIXUtcTimestampFormat() {
        String ts = resolver.resolve(PlaceholderType.TRANSACT_TIME, ctx());
        assertNotNull(ts);
        // FIX UTCTimestamp: yyyyMMdd-HH:mm:ss.SSS (exactly 21 chars)
        assertEquals(21, ts.length(), "Expected yyyyMMdd-HH:mm:ss.SSS but got: " + ts);
        assertTrue(ts.matches("\\d{8}-\\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
                "Format mismatch: " + ts);
    }

    @Test
    void sendingTime_matchesFIXUtcTimestampFormat() {
        String ts = resolver.resolve(PlaceholderType.SENDING_TIME, ctx());
        assertNotNull(ts);
        assertEquals(21, ts.length());
        assertTrue(ts.matches("\\d{8}-\\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
                "Format mismatch: " + ts);
    }

    // ── UUID ─────────────────────────────────────────────────────────────────

    @Test
    void uuid_matchesStandardUuidFormat() {
        String uuid = resolver.resolve(PlaceholderType.UUID, ctx());
        assertNotNull(uuid);
        assertTrue(uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "Not a UUID: " + uuid);
    }

    @Test
    void uuid_isUniqueEachCall() {
        String a = resolver.resolve(PlaceholderType.UUID, ctx());
        String b = resolver.resolve(PlaceholderType.UUID, ctx());
        assertNotEquals(a, b);
    }

    // ── SESSION_SENDER / SESSION_TARGET ───────────────────────────────────────

    @Test
    void sessionSender_returnsSenderCompId() {
        String sender = resolver.resolve(PlaceholderType.SESSION_SENDER, ctx());
        assertEquals("SIMULATOR", sender);
    }

    @Test
    void sessionTarget_returnsTargetCompId() {
        String target = resolver.resolve(PlaceholderType.SESSION_TARGET, ctx());
        assertEquals("EXCHANGE", target);
    }

    @Test
    void sessionSender_returnsEmptyStringWhenNoSession() {
        String sender = resolver.resolve(PlaceholderType.SESSION_SENDER, noSession());
        assertEquals("", sender);
    }

    @Test
    void sessionTarget_returnsEmptyStringWhenNoSession() {
        String target = resolver.resolve(PlaceholderType.SESSION_TARGET, noSession());
        assertEquals("", target);
    }
}
