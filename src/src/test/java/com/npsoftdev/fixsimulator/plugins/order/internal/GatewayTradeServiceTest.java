package com.npsoftdev.fixsimulator.plugins.order.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GatewayTradeServiceTest {

    private GatewayTradeService service;

    private static final SessionID SESSION = new SessionID("FIX.4.4", "SIMULATOR", "EXCHANGE");

    @BeforeEach
    void setUp() {
        service = new GatewayTradeService();
    }

    // ── onExecutionReport — fill classification ───────────────────────────────

    @Test
    void onExecutionReport_fill_isRecorded() {
        service.onExecutionReport(SESSION, executionReport("EXEC001", ExecType.FILL));

        List<Map<Integer, String>> trades = service.listTrades(SESSION.toString());
        assertEquals(1, trades.size());
        assertEquals("EXEC001", trades.get(0).get(ExecID.FIELD));
    }

    @Test
    void onExecutionReport_partialFill_isRecorded() {
        service.onExecutionReport(SESSION, executionReport("EXEC002", ExecType.PARTIAL_FILL));

        assertEquals(1, service.listTrades(SESSION.toString()).size());
    }

    @Test
    void onExecutionReport_new_isIgnored() {
        service.onExecutionReport(SESSION, executionReport("EXEC003", ExecType.NEW));

        assertTrue(service.listTrades(SESSION.toString()).isEmpty());
    }

    @Test
    void onExecutionReport_canceled_isIgnored() {
        service.onExecutionReport(SESSION, executionReport("EXEC004", ExecType.CANCELED));

        assertTrue(service.listTrades(SESSION.toString()).isEmpty());
    }

    @Test
    void onExecutionReport_missingExecTypeField_isIgnoredGracefully() {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, MsgType.EXECUTION_REPORT);
        // ExecType deliberately omitted

        assertDoesNotThrow(() -> service.onExecutionReport(SESSION, msg));
        assertTrue(service.listTrades(SESSION.toString()).isEmpty());
    }

    // ── ordering ─────────────────────────────────────────────────────────────

    @Test
    void listTrades_multipleEntries_areNewestFirst() {
        service.onExecutionReport(SESSION, executionReport("EXEC001", ExecType.FILL));
        service.onExecutionReport(SESSION, executionReport("EXEC002", ExecType.FILL));
        service.onExecutionReport(SESSION, executionReport("EXEC003", ExecType.FILL));

        List<Map<Integer, String>> trades = service.listTrades(SESSION.toString());
        assertEquals("EXEC003", trades.get(0).get(ExecID.FIELD));
        assertEquals("EXEC001", trades.get(2).get(ExecID.FIELD));
    }

    // ── session isolation ────────────────────────────────────────────────────

    @Test
    void listTrades_differentSessions_areKeptSeparate() {
        SessionID other = new SessionID("FIX.4.4", "SIMULATOR", "BROKER");
        service.onExecutionReport(SESSION, executionReport("EXEC001", ExecType.FILL));
        service.onExecutionReport(other,   executionReport("EXEC002", ExecType.FILL));

        assertEquals(1, service.listTrades(SESSION.toString()).size());
        assertEquals(1, service.listTrades(other.toString()).size());
    }

    @Test
    void listTrades_unknownSession_returnsEmptyList() {
        assertTrue(service.listTrades("GHOST:SESSION").isEmpty());
    }

    // ── getTradeByExecId ─────────────────────────────────────────────────────

    @Test
    void getTradeByExecId_found_returnsEntry() {
        service.onExecutionReport(SESSION, executionReport("EXEC001", ExecType.FILL));
        service.onExecutionReport(SESSION, executionReport("EXEC002", ExecType.FILL));

        Map<Integer, String> trade = service.getTradeByExecId(SESSION.toString(), "EXEC001");
        assertFalse(trade.isEmpty());
        assertEquals("EXEC001", trade.get(ExecID.FIELD));
    }

    @Test
    void getTradeByExecId_notFound_returnsEmptyMap() {
        Map<Integer, String> result = service.getTradeByExecId(SESSION.toString(), "EXEC_UNKNOWN");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── field extraction completeness ─────────────────────────────────────────

    @Test
    void onExecutionReport_fill_capturesBothHeaderAndBodyFields() {
        service.onExecutionReport(SESSION, executionReport("EXEC001", ExecType.FILL));

        Map<Integer, String> fields = service.listTrades(SESSION.toString()).get(0);
        // MsgType is a header field (tag 35), ExecID is a body field (tag 17)
        assertTrue(fields.containsKey(MsgType.FIELD),  "header field MsgType should be captured");
        assertTrue(fields.containsKey(ExecID.FIELD),   "body field ExecID should be captured");
        assertTrue(fields.containsKey(ExecType.FIELD), "body field ExecType should be captured");
        assertTrue(fields.containsKey(Symbol.FIELD),   "body field Symbol should be captured");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Message executionReport(String execId, char execType) {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, MsgType.EXECUTION_REPORT);
        msg.setString(ExecID.FIELD,   execId);
        msg.setChar(ExecType.FIELD,   execType);
        msg.setString(Symbol.FIELD,   "AAPL");
        msg.setString(ClOrdID.FIELD,  "ORD001");
        msg.setDouble(LastPx.FIELD,   150.0);
        msg.setDouble(LastQty.FIELD,  100.0);
        return msg;
    }
}
