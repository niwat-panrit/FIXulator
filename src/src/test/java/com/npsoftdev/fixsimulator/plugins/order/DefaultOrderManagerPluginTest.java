package com.npsoftdev.fixsimulator.plugins.order;

import com.npsoftdev.fixsimulator.core.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.plugins.connection.api.FixMessageListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.npsoftdev.fixsimulator.core.plugin.NavSection;
import com.npsoftdev.fixsimulator.plugins.connection.DefaultFixGatewayPlugin;

/**
 * Verifies that {@link DefaultOrderManagerPlugin} correctly routes FIX messages
 * from the gateway to the order and trade services.
 *
 * <p>No real FIX engine is needed — the gateway is created in nav-only mode and
 * its registered listener is driven directly via
 * {@link DefaultFixGatewayPlugin#getMessageListeners()}.</p>
 */
@ExtendWith(MockitoExtension.class)
class DefaultOrderManagerPluginTest {

    @Mock
    private FixSimulatorApplication app;

    /** Nav-only gateway — no FIX engine, just a listener registry. */
    private DefaultFixGatewayPlugin gateway;
    private DefaultOrderManagerPlugin orderManager;

    private static final SessionID SESSION = new SessionID("FIX.4.4", "SIMULATOR", "EXCHANGE");

    @BeforeEach
    void setUp() {
        gateway = new DefaultFixGatewayPlugin(
                "connections", "FIX Connections", "bi-hdd-network",
                NavSection.ADMIN, null);         // nav-only: no SessionSettings

        orderManager = new DefaultOrderManagerPlugin(
                "orders", "Orders", "bi-card-list",
                NavSection.MONITORING, null, gateway,
                Path.of(System.getProperty("java.io.tmpdir"), "fix-simulator-test"));

        orderManager.initialize(app);
    }

    // ── initialize ────────────────────────────────────────────────────────────

    @Test
    void initialize_registersExactlyOneListenerOnGateway() {
        assertEquals(1, gateway.getMessageListeners().size());
    }

    @Test
    void initialize_exposesOrderServiceOnApp() {
        verify(app).setOrderService(orderManager.getOrderService());
    }

    @Test
    void initialize_exposesTradeServiceOnApp() {
        verify(app).setTradeService(orderManager.getTradeService());
    }

    @Test
    void initialize_createsNonNullServices() {
        assertNotNull(orderManager.getOrderService());
        assertNotNull(orderManager.getTradeService());
    }

    // ── onInbound — Execution Report (fill) ───────────────────────────────────

    @Test
    void onInbound_fillExecutionReport_isRoutedToTradeService() {
        fireInbound(executionReport("ORD001", ExecType.FILL, OrdStatus.FILLED));

        assertFalse(orderManager.getTradeService().listTrades(SESSION.toString()).isEmpty(),
                "fill execution report should be captured by trade service");
    }

    @Test
    void onInbound_partialFillExecutionReport_isRoutedToTradeService() {
        fireInbound(executionReport("ORD001", ExecType.PARTIAL_FILL, OrdStatus.PARTIALLY_FILLED));

        assertFalse(orderManager.getTradeService().listTrades(SESSION.toString()).isEmpty());
    }

    @Test
    void onInbound_fillExecutionReport_isAlsoRoutedToOrderService() {
        // Record the original order first so onInboundMessage has something to update
        fireOutbound(outboundOrder("ORD001"));
        fireInbound(executionReport("ORD001", ExecType.FILL, OrdStatus.FILLED));

        Map<Integer, String> order = orderManager.getOrderService()
                .listOrders(SESSION.toString()).get(0);
        assertEquals(String.valueOf(OrdStatus.FILLED), order.get(OrdStatus.FIELD));
    }

    @Test
    void onInbound_newExecutionReport_isRoutedToOrderService_butNotTradeService() {
        fireOutbound(outboundOrder("ORD001"));
        fireInbound(executionReport("ORD001", ExecType.NEW, OrdStatus.NEW));

        // Order status updated
        Map<Integer, String> order = orderManager.getOrderService()
                .listOrders(SESSION.toString()).get(0);
        assertEquals(String.valueOf(OrdStatus.NEW), order.get(OrdStatus.FIELD));

        // No trade recorded (ExecType = NEW is not a fill)
        assertTrue(orderManager.getTradeService().listTrades(SESSION.toString()).isEmpty());
    }

    // ── onInbound — Order Cancel Reject ───────────────────────────────────────

    @Test
    void onInbound_cancelReject_isRoutedToOrderService() {
        fireOutbound(outboundOrder("ORD001"));
        fireInbound(cancelReject("ORD001"));

        // cancelReject updates OrdStatus to REJECTED
        Map<Integer, String> order = orderManager.getOrderService()
                .listOrders(SESSION.toString()).get(0);
        assertEquals(String.valueOf(OrdStatus.REJECTED), order.get(OrdStatus.FIELD));
    }

    @Test
    void onInbound_cancelReject_doesNotRouteToTradeService() {
        fireInbound(cancelReject("ORD001"));
        assertTrue(orderManager.getTradeService().listTrades(SESSION.toString()).isEmpty());
    }

    // ── onInbound — unrelated message type ───────────────────────────────────

    @Test
    void onInbound_heartbeat_isIgnoredByBothServices() {
        Message hb = new Message();
        hb.getHeader().setString(MsgType.FIELD, MsgType.HEARTBEAT);
        fireInbound(hb);

        assertTrue(orderManager.getOrderService().listOrders(SESSION.toString()).isEmpty());
        assertTrue(orderManager.getTradeService().listTrades(SESSION.toString()).isEmpty());
    }

    @Test
    void onInbound_missingMsgType_doesNotThrow() {
        assertDoesNotThrow(() -> fireInbound(new Message()));
    }

    // ── onOutbound ────────────────────────────────────────────────────────────

    @Test
    void onOutbound_newOrderSingle_isRecordedByOrderService() {
        fireOutbound(outboundOrder("ORD001"));

        List<Map<Integer, String>> orders = orderManager.getOrderService()
                .listOrders(SESSION.toString());
        assertEquals(1, orders.size());
        assertEquals("ORD001", orders.get(0).get(ClOrdID.FIELD));
    }

    @Test
    void onOutbound_heartbeat_isIgnoredByOrderService() {
        Message hb = new Message();
        hb.getHeader().setString(MsgType.FIELD, MsgType.HEARTBEAT);
        fireOutbound(hb);

        assertTrue(orderManager.getOrderService().listOrders(SESSION.toString()).isEmpty());
    }

    // ── multiple calls ────────────────────────────────────────────────────────

    @Test
    void multipleInboundFills_allCapturedByTradeService() {
        fireInbound(executionReport("ORD001", ExecType.FILL, OrdStatus.FILLED));
        fireInbound(executionReport("ORD002", ExecType.FILL, OrdStatus.FILLED));
        fireInbound(executionReport("ORD003", ExecType.PARTIAL_FILL, OrdStatus.PARTIALLY_FILLED));

        assertEquals(3, orderManager.getTradeService().listTrades(SESSION.toString()).size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Drives the registered listener's {@code onInbound} as if the gateway received a message. */
    private void fireInbound(Message message) {
        FixMessageListener listener = gateway.getMessageListeners().get(0);
        listener.onInbound(SESSION, message);
    }

    /** Drives the registered listener's {@code onOutbound} as if the gateway sent a message. */
    private void fireOutbound(Message message) {
        FixMessageListener listener = gateway.getMessageListeners().get(0);
        listener.onOutbound(SESSION, message);
    }

    private static Message outboundOrder(String clOrdId) {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, MsgType.ORDER_SINGLE);
        msg.setString(ClOrdID.FIELD, clOrdId);
        msg.setString(Symbol.FIELD,  "AAPL");
        msg.setString(Side.FIELD,    "1");
        return msg;
    }

    private static Message executionReport(String clOrdId, char execType, char ordStatus) {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, MsgType.EXECUTION_REPORT);
        msg.setString(ClOrdID.FIELD,  clOrdId);
        msg.setChar(ExecType.FIELD,   execType);
        msg.setChar(OrdStatus.FIELD,  ordStatus);
        msg.setString(ExecID.FIELD,   "EXEC-" + clOrdId);
        msg.setString(Symbol.FIELD,   "AAPL");
        msg.setDouble(LastPx.FIELD,   150.0);
        msg.setDouble(LastQty.FIELD,  100.0);
        return msg;
    }

    private static Message cancelReject(String clOrdId) {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, MsgType.ORDER_CANCEL_REJECT);
        msg.setString(ClOrdID.FIELD,  clOrdId);
        msg.setChar(OrdStatus.FIELD,  OrdStatus.REJECTED);
        return msg;
    }
}
