package com.npsoftdev.fixsimulator.plugin;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.gateway.FixMessageListener;
import com.npsoftdev.fixsimulator.gateway.GatewayOrderService;
import com.npsoftdev.fixsimulator.gateway.GatewayTradeService;
import com.npsoftdev.fixsimulator.gateway.LiveSessionFacade;
import com.npsoftdev.fixsimulator.pages.BasePage;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;

/**
 * Plugin that owns the order and trade domain.
 *
 * <p>On {@link #initialize} it:
 * <ol>
 *   <li>Creates a {@link GatewayOrderService} and a {@link GatewayTradeService},
 *       both backed by the session-ID map shared with the gateway.</li>
 *   <li>Registers a {@link FixMessageListener} on the provided
 *       {@link DefaultFixGatewayPlugin} to receive every application-level
 *       FIX message.</li>
 *   <li>Exposes the two services on {@link FixSimulatorApplication} so that
 *       UI pages can reach them.</li>
 * </ol>
 *
 * <p>The gateway is intentionally <em>not</em> started here — transport is
 * solely {@link DefaultFixGatewayPlugin}'s concern.  This plugin only
 * consumes messages that have already passed through the engine.</p>
 */
public class DefaultOrderManagerPlugin implements SimulatorPlugin {

    private static final long serialVersionUID = 1L;

    // ── Nav ───────────────────────────────────────────────────────────────────
    private final String id;
    private final String label;
    private final String iconClass;
    private final NavSection section;
    private final Class<? extends BasePage> pageClass;

    /** Gateway this plugin subscribes to for FIX message events. */
    private final DefaultFixGatewayPlugin gateway;

    private GatewayOrderService orderService;
    private GatewayTradeService tradeService;

    // ── Constructor ───────────────────────────────────────────────────────────

    public DefaultOrderManagerPlugin(String id, String label, String iconClass,
                                      NavSection section, Class<? extends BasePage> pageClass,
                                      DefaultFixGatewayPlugin gateway) {
        this.id        = id;
        this.label     = label;
        this.iconClass = iconClass;
        this.section   = section;
        this.pageClass = pageClass;
        this.gateway   = gateway;
    }

    // ── SimulatorPlugin ───────────────────────────────────────────────────────

    @Override public String getId()                           { return id; }
    @Override public String getLabel()                        { return label; }
    @Override public String getIconClass()                    { return iconClass; }
    @Override public NavSection getSection()                  { return section; }
    @Override public Class<? extends BasePage> getPageClass() { return pageClass; }

    @Override
    public void initialize(FixSimulatorApplication app) {
        orderService = new GatewayOrderService(gateway.getSessionIDs(), new LiveSessionFacade());
        tradeService = new GatewayTradeService();

        gateway.addMessageListener(new OrderManagerListener());

        app.setOrderService(orderService);
        app.setTradeService(tradeService);
    }

    // ── Service accessors ─────────────────────────────────────────────────────

    public GatewayOrderService getOrderService() { return orderService; }
    public GatewayTradeService getTradeService() { return tradeService; }

    // ── Message routing ───────────────────────────────────────────────────────

    /**
     * Routes FIX application messages from the gateway to the order and trade
     * services.  Using a named inner class keeps it serialisable and testable.
     */
    private class OrderManagerListener implements FixMessageListener {

        @Override
        public void onOutbound(SessionID sessionID, Message message) {
            orderService.onOutboundMessage(sessionID, message);
        }

        @Override
        public void onInbound(SessionID sessionID, Message message) {
            try {
                String msgType = message.getHeader().getString(MsgType.FIELD);

                if (MsgType.EXECUTION_REPORT.equals(msgType)) {
                    tradeService.onExecutionReport(sessionID, message);
                    orderService.onInboundMessage(sessionID, message);
                } else if (MsgType.ORDER_CANCEL_REJECT.equals(msgType)) {
                    orderService.onInboundMessage(sessionID, message);
                }
            } catch (FieldNotFound ignored) {}
        }
    }
}
