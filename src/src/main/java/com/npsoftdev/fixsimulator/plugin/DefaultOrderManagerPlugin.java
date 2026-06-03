package com.npsoftdev.fixsimulator.plugin;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.gateway.FixMessageListener;
import com.npsoftdev.fixsimulator.gateway.GatewayOrderService;
import com.npsoftdev.fixsimulator.gateway.GatewayTradeService;
import com.npsoftdev.fixsimulator.gateway.LiveSessionFacade;
import com.npsoftdev.fixsimulator.pages.BasePage;
import com.npsoftdev.fixsimulator.template.DefaultFixMessageBuilder;
import com.npsoftdev.fixsimulator.template.DefaultPlaceholderResolver;
import com.npsoftdev.fixsimulator.template.DefaultTemplateService;
import com.npsoftdev.fixsimulator.template.DynamicValueRegistry;
import com.npsoftdev.fixsimulator.template.FieldSpec;
import com.npsoftdev.fixsimulator.template.FixMessageBuilder;
import com.npsoftdev.fixsimulator.template.FixMessageTemplate;
import com.npsoftdev.fixsimulator.template.InMemoryDynamicValueRegistry;
import com.npsoftdev.fixsimulator.template.InMemoryTemplateRepository;
import com.npsoftdev.fixsimulator.template.InMemoryValueMappingService;
import com.npsoftdev.fixsimulator.template.PlaceholderResolver;
import com.npsoftdev.fixsimulator.template.PlaceholderType;
import com.npsoftdev.fixsimulator.template.TemplateRepository;
import com.npsoftdev.fixsimulator.template.TemplateScope;
import com.npsoftdev.fixsimulator.template.TemplateService;
import com.npsoftdev.fixsimulator.template.ValueMappingService;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;
import quickfix.field.ClOrdID;
import quickfix.field.HandlInst;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.OrigClOrdID;
import quickfix.field.Price;
import quickfix.field.SecurityID;
import quickfix.field.SecurityIDSource;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.TimeInForce;
import quickfix.field.TransactTime;

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

    // ── Template stack ────────────────────────────────────────────────────────
    private TemplateRepository    templateRepository;
    private PlaceholderResolver   placeholderResolver;
    private ValueMappingService   valueMappingService;
    private DynamicValueRegistry  dynamicValueRegistry;
    private FixMessageBuilder     messageBuilder;
    private TemplateService       templateService;

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
        LiveSessionFacade facade = new LiveSessionFacade();

        orderService = new GatewayOrderService(gateway.getSessionIDs(), facade);
        tradeService = new GatewayTradeService();

        // ── Template stack ────────────────────────────────────────────────────
        // Swap any of these for a persistent implementation later; the wiring
        // is the only thing that has to change.
        templateRepository   = new InMemoryTemplateRepository();
        placeholderResolver  = new DefaultPlaceholderResolver();
        valueMappingService  = new InMemoryValueMappingService();
        dynamicValueRegistry = new InMemoryDynamicValueRegistry();
        messageBuilder       = new DefaultFixMessageBuilder(
                placeholderResolver, valueMappingService, dynamicValueRegistry);
        templateService      = new DefaultTemplateService(
                templateRepository, messageBuilder,
                facade::sendToTarget, gateway.getSessionIDs());

        seedBuiltInTemplates(templateRepository);

        gateway.addMessageListener(new OrderManagerListener());

        app.setOrderService(orderService);
        app.setTradeService(tradeService);
        app.setTemplateService(templateService);
        app.setValueMappingService(valueMappingService);
        app.setDynamicValueRegistry(dynamicValueRegistry);
    }

    /**
     * Seeds a starter "New Order Single" template that exercises every
     * {@link com.npsoftdev.fixsimulator.template.FieldValue} variant:
     * <ul>
     *   <li>{@link com.npsoftdev.fixsimulator.template.FieldValue.Placeholder Placeholder}
     *       for ClOrdID and TransactTime;</li>
     *   <li>{@link com.npsoftdev.fixsimulator.template.FieldValue.UserInput UserInput}
     *       for Symbol, Side, OrderQty, Price, OrdType, TimeInForce;</li>
     *   <li>{@link com.npsoftdev.fixsimulator.template.FieldValue.Derived Derived}
     *       for SecurityID (looks up the ISIN for the user-entered Symbol);</li>
     *   <li>{@link com.npsoftdev.fixsimulator.template.FieldValue.Literal Literal}
     *       for SecurityIDSource ("4" = ISIN) and HandlInst ("1" = automated).</li>
     * </ul>
     * Override / delete from the UI later — this exists only so the template
     * engine has something to validate against on first run.
     */
    private static void seedBuiltInTemplates(TemplateRepository repo) {
        repo.save(FixMessageTemplate.builder()
                .id("built-in.nos.default")
                .name("New Order Single — default")
                .deletionProtected(true)
                .description("Auto-fills ClOrdID, TransactTime, and ISIN. "
                           + "Edit overrides per request.")
                .msgType(MsgType.ORDER_SINGLE)            // "D"
                .scope(TemplateScope.global())
                .addField(FieldSpec.placeholder(ClOrdID.FIELD,      PlaceholderType.ORDER_ID))
                .addField(FieldSpec.placeholder(TransactTime.FIELD, PlaceholderType.TRANSACT_TIME))
                .addField(FieldSpec.userInput  (Symbol.FIELD,       "symbol"))
                .addField(FieldSpec.derived    (SecurityID.FIELD,   Symbol.FIELD, "symbol-to-isin"))
                .addField(FieldSpec.literal    (SecurityIDSource.FIELD, "4"))   // ISIN
                .addField(FieldSpec.userInput  (Side.FIELD,         "side"))
                .addField(FieldSpec.userInput  (OrderQty.FIELD,     "quantity"))
                .addField(FieldSpec.userInput  (Price.FIELD,        "price"))
                .addField(FieldSpec.userInput  (OrdType.FIELD,      "ordType",     "2"))   // Limit
                .addField(FieldSpec.userInput  (TimeInForce.FIELD,  "timeInForce", "0"))   // Day
                .addField(FieldSpec.literal    (HandlInst.FIELD,    "1"))   // Automated
                .build());

        repo.save(FixMessageTemplate.builder()
                .id("built-in.ocr.default")
                .name("Order Cancel/Replace — default")
                .deletionProtected(true)
                .description("Amend an open order. OrigClOrdID is pre-filled from the selected row.")
                .msgType(MsgType.ORDER_CANCEL_REPLACE_REQUEST)   // "G"
                .scope(TemplateScope.global())
                .addField(FieldSpec.placeholder(ClOrdID.FIELD,      PlaceholderType.ORDER_ID))
                .addField(FieldSpec.placeholder(TransactTime.FIELD, PlaceholderType.TRANSACT_TIME))
                .addField(FieldSpec.userInput  (OrigClOrdID.FIELD,  "origClOrdId"))
                .addField(FieldSpec.userInput  (Symbol.FIELD,       "symbol"))
                .addField(FieldSpec.derived    (SecurityID.FIELD,   Symbol.FIELD, "symbol-to-isin"))
                .addField(FieldSpec.literal    (SecurityIDSource.FIELD, "4"))   // ISIN
                .addField(FieldSpec.userInput  (Side.FIELD,         "side"))
                .addField(FieldSpec.userInput  (OrderQty.FIELD,     "quantity"))
                .addField(FieldSpec.userInput  (Price.FIELD,        "price"))
                .addField(FieldSpec.userInput  (OrdType.FIELD,      "ordType",     "2"))   // Limit
                .addField(FieldSpec.userInput  (TimeInForce.FIELD,  "timeInForce", "0"))   // Day
                .addField(FieldSpec.literal    (HandlInst.FIELD,    "1"))   // Automated
                .build());
    }

    // ── Service accessors ─────────────────────────────────────────────────────

    public GatewayOrderService  getOrderService()          { return orderService; }
    public GatewayTradeService  getTradeService()          { return tradeService; }
    public TemplateService      getTemplateService()       { return templateService; }
    public TemplateRepository   getTemplateRepository()    { return templateRepository; }
    public ValueMappingService  getValueMappingService()   { return valueMappingService; }
    public DynamicValueRegistry getDynamicValueRegistry()  { return dynamicValueRegistry; }

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
