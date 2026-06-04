package com.npsoftdev.fixsimulator.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.field.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayOrderServiceTest {

    @Mock
    private SessionFacade facade;

    private Map<String, SessionID> sessionIDs;
    private GatewayOrderService service;

    private static final SessionID SID = new SessionID("FIX.4.4", "SIMULATOR", "EXCHANGE");

    @BeforeEach
    void setUp() {
        sessionIDs = new ConcurrentHashMap<>();
        sessionIDs.put(SID.toString(), SID);
        service = new GatewayOrderService(sessionIDs, facade);
    }

    // ── onOutboundMessage ─────────────────────────────────────────────────────

    @Test
    void onOutboundMessage_newOrderSingle_isRecorded() {
        service.onOutboundMessage(SID, outboundOrder("ORD001", MsgType.ORDER_SINGLE));

        List<Map<Integer, String>> orders = service.listOrders(SID.toString());
        assertEquals(1, orders.size());
        assertEquals("ORD001", orders.get(0).get(ClOrdID.FIELD));
    }

    @Test
    void onOutboundMessage_orderCancelRequest_setsPendingCancelOnExistingOrder() {
        // Record the original order first
        Message original = outboundOrderWithOrig("ORD001", null, MsgType.ORDER_SINGLE);
        service.onOutboundMessage(SID, original);

        // Send a cancel for it
        Message cancel = outboundOrderWithOrig("CXL001", "ORD001", MsgType.ORDER_CANCEL_REQUEST);
        service.onOutboundMessage(SID, cancel);

        // Still exactly ONE row (no new row added)
        assertEquals(1, service.listOrders(SID.toString()).size());
        // OrdStatus = 6 (PendingCancel)
        assertEquals("6", service.listOrders(SID.toString()).get(0).get(OrdStatus.FIELD));
    }

    @Test
    void onOutboundMessage_orderCancelRequest_forUnknownOrder_isNoOp() {
        // Cancel for an order we never sent — no new row, no exception
        Message cancel = outboundOrderWithOrig("CXL001", "GHOST", MsgType.ORDER_CANCEL_REQUEST);
        assertDoesNotThrow(() -> service.onOutboundMessage(SID, cancel));
        assertTrue(service.listOrders(SID.toString()).isEmpty());
    }

    @Test
    void onOutboundMessage_orderCancelReplaceRequest_updatesFieldsAndRekeys() {
        // Record original order
        Message original = outboundOrderWithOrig("ORD001", null, MsgType.ORDER_SINGLE);
        original.setString(Symbol.FIELD, "AAPL");
        service.onOutboundMessage(SID, original);

        // Send replace with new ClOrdID
        Message replace = outboundOrderWithOrig("REP001", "ORD001",
                MsgType.ORDER_CANCEL_REPLACE_REQUEST);
        replace.setString(Price.FIELD,    "155.00");
        replace.setString(OrderQty.FIELD, "200");
        service.onOutboundMessage(SID, replace);

        // Still exactly ONE row
        assertEquals(1, service.listOrders(SID.toString()).size());
        Map<Integer, String> order = service.listOrders(SID.toString()).get(0);

        // ClOrdID re-keyed to new value, status = PendingReplace
        assertEquals("REP001", order.get(ClOrdID.FIELD));
        assertEquals("E",      order.get(OrdStatus.FIELD));
        assertEquals("155.00", order.get(Price.FIELD));
        assertEquals("200",    order.get(OrderQty.FIELD));
    }

    @Test
    void onOutboundMessage_heartbeat_isIgnored() {
        Message hb = new Message();
        hb.getHeader().setString(MsgType.FIELD, MsgType.HEARTBEAT);
        service.onOutboundMessage(SID, hb);

        assertTrue(service.listOrders(SID.toString()).isEmpty());
    }

    @Test
    void onOutboundMessage_multipleOrders_areStoredNewestFirst() {
        service.onOutboundMessage(SID, outboundOrder("ORD001", MsgType.ORDER_SINGLE));
        service.onOutboundMessage(SID, outboundOrder("ORD002", MsgType.ORDER_SINGLE));
        service.onOutboundMessage(SID, outboundOrder("ORD003", MsgType.ORDER_SINGLE));

        List<Map<Integer, String>> orders = service.listOrders(SID.toString());
        assertEquals("ORD003", orders.get(0).get(ClOrdID.FIELD));
        assertEquals("ORD001", orders.get(2).get(ClOrdID.FIELD));
    }

    @Test
    void onOutboundMessage_missingMsgType_isIgnoredGracefully() {
        assertDoesNotThrow(() -> service.onOutboundMessage(SID, new Message()));
        assertTrue(service.listOrders(SID.toString()).isEmpty());
    }

    // ── onInboundMessage ──────────────────────────────────────────────────────

    @Test
    void onInboundMessage_executionReport_updatesOrderStatus() {
        service.onOutboundMessage(SID, outboundOrder("ORD001", MsgType.ORDER_SINGLE));
        service.onInboundMessage(SID, executionReport("ORD001", OrdStatus.FILLED));

        Map<Integer, String> order = service.listOrders(SID.toString()).get(0);
        assertEquals(String.valueOf(OrdStatus.FILLED), order.get(OrdStatus.FIELD));
    }

    @Test
    void onInboundMessage_partialFill_updatesOrderStatus() {
        service.onOutboundMessage(SID, outboundOrder("ORD001", MsgType.ORDER_SINGLE));
        service.onInboundMessage(SID, executionReport("ORD001", OrdStatus.PARTIALLY_FILLED));

        Map<Integer, String> order = service.listOrders(SID.toString()).get(0);
        assertEquals(String.valueOf(OrdStatus.PARTIALLY_FILLED), order.get(OrdStatus.FIELD));
    }

    @Test
    void onInboundMessage_unknownClOrdId_isNoOp() {
        service.onOutboundMessage(SID, outboundOrder("ORD001", MsgType.ORDER_SINGLE));
        // Report for an order we never sent
        assertDoesNotThrow(() ->
            service.onInboundMessage(SID, executionReport("GHOST", OrdStatus.FILLED)));

        // Original order status is unchanged (no OrdStatus field yet)
        assertNull(service.listOrders(SID.toString()).get(0).get(OrdStatus.FIELD));
    }

    @Test
    void onInboundMessage_missingClOrdId_isIgnoredGracefully() {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, MsgType.EXECUTION_REPORT);
        // ClOrdID deliberately omitted
        assertDoesNotThrow(() -> service.onInboundMessage(SID, msg));
    }

    @Test
    void onInboundMessage_executionReport_updatesExecutionFields() {
        service.onOutboundMessage(SID, outboundOrder("ORD001", MsgType.ORDER_SINGLE));

        Message er = executionReport("ORD001", OrdStatus.PARTIALLY_FILLED);
        er.setString(CumQty.FIELD,   "50");
        er.setString(LeavesQty.FIELD, "50");
        er.setString(AvgPx.FIELD,    "150.00");
        er.setString(LastPx.FIELD,   "150.00");
        er.setString(LastQty.FIELD,  "50");
        service.onInboundMessage(SID, er);

        Map<Integer, String> order = service.listOrders(SID.toString()).get(0);
        assertEquals("50",     order.get(CumQty.FIELD));
        assertEquals("50",     order.get(LeavesQty.FIELD));
        assertEquals("150.00", order.get(AvgPx.FIELD));
        assertEquals("150.00", order.get(LastPx.FIELD));
        assertEquals("50",     order.get(LastQty.FIELD));
    }

    @Test
    void onInboundMessage_replacedExecType_syncsPriceAndQty() {
        service.onOutboundMessage(SID, outboundOrder("ORD001", MsgType.ORDER_SINGLE));

        // Simulate PendingReplace in-flight
        Message replace = outboundOrderWithOrig("REP001", "ORD001",
                MsgType.ORDER_CANCEL_REPLACE_REQUEST);
        replace.setString(Price.FIELD,    "160.00");
        replace.setString(OrderQty.FIELD, "300");
        service.onOutboundMessage(SID, replace);

        // ExecType=5 (Replaced) execution report arrives on new ClOrdID
        Message er = executionReportWithExecType("REP001", OrdStatus.NEW, ExecType.REPLACED);
        er.setString(Price.FIELD,    "160.00");
        er.setString(OrderQty.FIELD, "300");
        service.onInboundMessage(SID, er);

        Map<Integer, String> order = service.listOrders(SID.toString()).get(0);
        assertEquals("160.00", order.get(Price.FIELD));
        assertEquals("300",    order.get(OrderQty.FIELD));
    }

    @Test
    void onInboundMessage_orderCancelReject_restoresOrdStatus() {
        service.onOutboundMessage(SID, outboundOrder("ORD001", MsgType.ORDER_SINGLE));

        // Simulate PendingCancel
        Message cancel = outboundOrderWithOrig("CXL001", "ORD001", MsgType.ORDER_CANCEL_REQUEST);
        service.onOutboundMessage(SID, cancel);
        assertEquals("6", service.listOrders(SID.toString()).get(0).get(OrdStatus.FIELD));

        // Cancel rejected — exchange restores status to New
        Message reject = new Message();
        reject.getHeader().setString(MsgType.FIELD, MsgType.ORDER_CANCEL_REJECT);
        reject.setString(ClOrdID.FIELD,  "CXL001");
        reject.setChar(OrdStatus.FIELD,  OrdStatus.NEW);
        service.onInboundMessage(SID, reject);

        assertEquals(String.valueOf(OrdStatus.NEW),
                service.listOrders(SID.toString()).get(0).get(OrdStatus.FIELD));
    }

    // ── sendNewOrder ──────────────────────────────────────────────────────────

    @Test
    void sendNewOrder_delegatesMessageToFacade() throws SessionNotFound {
        service.sendNewOrder(SID.toString(), Map.of(Symbol.FIELD, "AAPL", Side.FIELD, "1"));
        verify(facade).sendToTarget(any(Message.class), eq(SID));
    }

    @Test
    void sendNewOrder_sentMessageContainsCallerFields() throws Exception {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        service.sendNewOrder(SID.toString(), Map.of(Symbol.FIELD, "AAPL", Side.FIELD, "1"));
        verify(facade).sendToTarget(captor.capture(), eq(SID));

        Message sent = captor.getValue();
        assertEquals("AAPL", sent.getString(Symbol.FIELD));
        assertEquals("1",    sent.getString(Side.FIELD));
    }

    @Test
    void sendNewOrder_sentMessageHasDefaultClOrdIdAndOrdType() throws Exception {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        service.sendNewOrder(SID.toString(), Map.of());
        verify(facade).sendToTarget(captor.capture(), eq(SID));

        Message sent = captor.getValue();
        assertFalse(sent.getString(ClOrdID.FIELD).isBlank(), "ClOrdID should be generated");
        assertEquals(String.valueOf(OrdType.LIMIT), sent.getString(OrdType.FIELD));
    }

    @Test
    void sendNewOrder_unknownSession_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> service.sendNewOrder("GHOST:SESSION", Map.of()));
    }

    @Test
    void sendNewOrder_facadeThrows_wrapsInRuntimeException() throws SessionNotFound {
        doThrow(new SessionNotFound("gone")).when(facade).sendToTarget(any(), eq(SID));
        assertThrows(RuntimeException.class,
            () -> service.sendNewOrder(SID.toString(), Map.of()));
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Test
    void cancelOrder_delegatesMessageToFacade() throws SessionNotFound {
        service.cancelOrder(SID.toString(), "ORD001");
        verify(facade).sendToTarget(any(Message.class), eq(SID));
    }

    @Test
    void cancelOrder_sentMessageContainsOrigClOrdId() throws Exception {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        service.cancelOrder(SID.toString(), "ORD001");
        verify(facade).sendToTarget(captor.capture(), eq(SID));

        assertEquals("ORD001", captor.getValue().getString(OrigClOrdID.FIELD));
    }

    @Test
    void cancelOrder_copiesSymbolAndSideFromOriginalOrder() throws Exception {
        // First, record the original order via onOutboundMessage
        Message original = outboundOrder("ORD001", MsgType.ORDER_SINGLE);
        original.setString(Symbol.FIELD, "AAPL");
        original.setString(Side.FIELD,   "1");
        service.onOutboundMessage(SID, original);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        service.cancelOrder(SID.toString(), "ORD001");
        verify(facade).sendToTarget(captor.capture(), eq(SID));

        Message cancel = captor.getValue();
        assertEquals("AAPL", cancel.getString(Symbol.FIELD));
        assertEquals("1",    cancel.getString(Side.FIELD));
    }

    @Test
    void cancelOrder_originalOrderNotFound_sendsCancelWithoutSymbolOrSide() throws SessionNotFound {
        // Cancel for an order we never recorded — should not throw
        assertDoesNotThrow(() -> service.cancelOrder(SID.toString(), "GHOST_ORDER"));
        verify(facade).sendToTarget(any(), eq(SID));
    }

    // ── listOrders ────────────────────────────────────────────────────────────

    @Test
    void listOrders_unknownSession_returnsEmptyList() {
        assertTrue(service.listOrders("GHOST:SESSION").isEmpty());
    }

    @Test
    void listOrders_returnsUnmodifiableView() {
        service.onOutboundMessage(SID, outboundOrder("ORD001", MsgType.ORDER_SINGLE));
        List<Map<Integer, String>> orders = service.listOrders(SID.toString());
        assertThrows(UnsupportedOperationException.class, () -> orders.add(Map.of()));
    }

    // ── field extraction ─────────────────────────────────────────────────────

    @Test
    void onOutboundMessage_capturesBothHeaderAndBodyFields() {
        service.onOutboundMessage(SID, outboundOrder("ORD001", MsgType.ORDER_SINGLE));

        Map<Integer, String> fields = service.listOrders(SID.toString()).get(0);
        assertTrue(fields.containsKey(MsgType.FIELD),  "header field MsgType should be captured");
        assertTrue(fields.containsKey(ClOrdID.FIELD),  "body field ClOrdID should be captured");
        assertTrue(fields.containsKey(Symbol.FIELD),   "body field Symbol should be captured");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Message outboundOrder(String clOrdId, String msgType) {
        return outboundOrderWithOrig(clOrdId, null, msgType);
    }

    private static Message outboundOrderWithOrig(String clOrdId, String origClOrdId,
                                                  String msgType) {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, msgType);
        msg.setString(ClOrdID.FIELD, clOrdId);
        if (origClOrdId != null) msg.setString(OrigClOrdID.FIELD, origClOrdId);
        msg.setString(Symbol.FIELD, "AAPL");
        msg.setString(Side.FIELD,   "1");
        return msg;
    }

    private static Message executionReport(String clOrdId, char ordStatus) {
        return executionReportWithExecType(clOrdId, ordStatus, ExecType.NEW);
    }

    private static Message executionReportWithExecType(String clOrdId, char ordStatus,
                                                        char execType) {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, MsgType.EXECUTION_REPORT);
        msg.setString(ClOrdID.FIELD,  clOrdId);
        msg.setChar(OrdStatus.FIELD,  ordStatus);
        msg.setChar(ExecType.FIELD,   execType);
        return msg;
    }
}
