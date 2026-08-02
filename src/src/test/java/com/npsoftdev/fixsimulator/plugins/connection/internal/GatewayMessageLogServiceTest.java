package com.npsoftdev.fixsimulator.plugins.connection.internal;

import com.npsoftdev.fixsimulator.plugins.connection.api.MessageLogService.Direction;
import com.npsoftdev.fixsimulator.plugins.connection.api.MessageLogService.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GatewayMessageLogServiceTest {

    private GatewayMessageLogService service;

    private static final SessionID SESSION_A = new SessionID("FIX.4.4", "SIMULATOR", "EXCHANGE");
    private static final SessionID SESSION_B = new SessionID("FIX.4.4", "SIMULATOR", "BROKER");

    @BeforeEach
    void setUp() {
        service = new GatewayMessageLogService();
    }

    // ── record / getMessages ──────────────────────────────────────────────────

    @Test
    void record_singleMessage_isStoredAndRetrievable() {
        Message msg = heartbeat();
        service.record(SESSION_A, Direction.SENT, msg);

        List<LogEntry> entries = service.getMessages(SESSION_A.toString());
        assertEquals(1, entries.size());
        assertEquals(Direction.SENT, entries.get(0).direction());
        assertEquals(MsgType.HEARTBEAT, entries.get(0).msgType());
        assertNotNull(entries.get(0).rawMessage());
        assertNotNull(entries.get(0).timestamp());
    }

    @Test
    void record_multipleMessages_areStoredNewestFirst() {
        service.record(SESSION_A, Direction.SENT,     heartbeat());
        service.record(SESSION_A, Direction.RECEIVED, newOrderSingle());
        service.record(SESSION_A, Direction.SENT,     heartbeat());

        List<LogEntry> entries = service.getMessages(SESSION_A.toString());
        assertEquals(3, entries.size());
        // newest is at index 0
        assertEquals(Direction.SENT, entries.get(0).direction());
    }

    @Test
    void record_differentSessions_areKeptSeparate() {
        service.record(SESSION_A, Direction.SENT, heartbeat());
        service.record(SESSION_B, Direction.SENT, heartbeat());
        service.record(SESSION_B, Direction.SENT, heartbeat());

        assertEquals(1, service.getMessages(SESSION_A.toString()).size());
        assertEquals(2, service.getMessages(SESSION_B.toString()).size());
    }

    @Test
    void getMessages_unknownSession_returnsEmptyList() {
        List<LogEntry> result = service.getMessages("UNKNOWN:SESSION");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── getMessages(sessionId, direction) ────────────────────────────────────

    @Test
    void getMessagesByDirection_filtersCorrectly() {
        service.record(SESSION_A, Direction.SENT,     heartbeat());
        service.record(SESSION_A, Direction.RECEIVED, newOrderSingle());
        service.record(SESSION_A, Direction.SENT,     heartbeat());

        List<LogEntry> sent     = service.getMessages(SESSION_A.toString(), Direction.SENT);
        List<LogEntry> received = service.getMessages(SESSION_A.toString(), Direction.RECEIVED);

        assertEquals(2, sent.size());
        assertTrue(sent.stream().allMatch(e -> e.direction() == Direction.SENT));

        assertEquals(1, received.size());
        assertEquals(Direction.RECEIVED, received.get(0).direction());
    }

    @Test
    void getMessagesByDirection_unknownSession_returnsEmptyList() {
        assertTrue(service.getMessages("NO_SESSION", Direction.SENT).isEmpty());
    }

    // ── clearLog ─────────────────────────────────────────────────────────────

    @Test
    void clearLog_removesAllEntriesForSession() {
        service.record(SESSION_A, Direction.SENT, heartbeat());
        service.record(SESSION_A, Direction.SENT, heartbeat());

        service.clearLog(SESSION_A.toString());

        assertTrue(service.getMessages(SESSION_A.toString()).isEmpty());
    }

    @Test
    void clearLog_doesNotAffectOtherSessions() {
        service.record(SESSION_A, Direction.SENT, heartbeat());
        service.record(SESSION_B, Direction.SENT, heartbeat());

        service.clearLog(SESSION_A.toString());

        assertTrue(service.getMessages(SESSION_A.toString()).isEmpty());
        assertEquals(1, service.getMessages(SESSION_B.toString()).size());
    }

    @Test
    void clearLog_nonExistentSession_isNoOp() {
        assertDoesNotThrow(() -> service.clearLog("GHOST:SESSION"));
    }

    // ── msgType extraction ────────────────────────────────────────────────────

    @Test
    void record_messageWithNoMsgType_storesUnknown() {
        service.record(SESSION_A, Direction.SENT, new Message());

        LogEntry entry = service.getMessages(SESSION_A.toString()).get(0);
        assertEquals("UNKNOWN", entry.msgType());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Message heartbeat() {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, MsgType.HEARTBEAT);
        return msg;
    }

    private static Message newOrderSingle() {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, MsgType.ORDER_SINGLE);
        return msg;
    }
}
