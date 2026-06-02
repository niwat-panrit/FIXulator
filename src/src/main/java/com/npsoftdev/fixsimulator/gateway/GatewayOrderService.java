package com.npsoftdev.fixsimulator.gateway;

import com.npsoftdev.fixsimulator.service.OrderService;
import com.npsoftdev.fixsimulator.template.MessageSnapshot;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.OrderCancelRequest;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link OrderService} implementation that sends FIX 4.4 orders via QuickFIX/J
 * and tracks their state from inbound Execution Reports.
 *
 * <p>All outbound sends are delegated to a {@link SessionFacade} so the class
 * can be fully exercised in unit tests without a running FIX engine.</p>
 */
public class GatewayOrderService implements OrderService, Serializable {

    private static final long serialVersionUID = 1L;

    /** Shared session-ID registry populated by {@link com.npsoftdev.fixsimulator.plugin.DefaultFixGatewayPlugin}. */
    private final Map<String, SessionID> sessionIDs;

    /** keyed by session-ID string; inner list is newest-first */
    private final Map<String, CopyOnWriteArrayList<Map<Integer, String>>> orders =
            new ConcurrentHashMap<>();

    /**
     * Structured snapshots of each outbound order, keyed by
     * {@code sessionIdString -> clOrdId -> snapshot}. Preserves header / body
     * separation that the flat {@link #orders} map loses; consumed by the
     * "save as template" flow via {@link #findSnapshot(String, String)}.
     */
    private final Map<String, Map<String, MessageSnapshot>> snapshots =
            new ConcurrentHashMap<>();

    private final AtomicLong clOrdIdSeq = new AtomicLong(System.currentTimeMillis());
    private final SessionFacade session;

    public GatewayOrderService(Map<String, SessionID> sessionIDs, SessionFacade session) {
        this.sessionIDs = sessionIDs;
        this.session    = session;
    }

    // ── Callbacks from DefaultFixGatewayPlugin ────────────────────────────────

    /** Records an outbound New Order Single (D) or Order Cancel Request (F). */
    public void onOutboundMessage(SessionID sessionID, Message message) {
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            if (MsgType.ORDER_SINGLE.equals(msgType)
                    || MsgType.ORDER_CANCEL_REQUEST.equals(msgType)) {
                Map<Integer, String> flat = extractFields(message);
                orders.computeIfAbsent(sessionID.toString(), k -> new CopyOnWriteArrayList<>())
                      .add(0, flat);

                // Parallel structured snapshot — used by "save as template".
                String clOrdId = flat.get(ClOrdID.FIELD);
                if (clOrdId != null) {
                    snapshots.computeIfAbsent(sessionID.toString(), k -> new ConcurrentHashMap<>())
                             .put(clOrdId, MessageSnapshot.capture(message));
                }
            }
        } catch (FieldNotFound ignored) {}
    }

    /** Updates order status from an inbound Execution Report (8) or Order Cancel Reject (9). */
    public void onInboundMessage(SessionID sessionID, Message message) {
        try {
            String clOrdId = message.getString(ClOrdID.FIELD);
            String ordStatus;
            try { ordStatus = message.getString(OrdStatus.FIELD); }
            catch (FieldNotFound e) { ordStatus = null; }

            if (ordStatus != null) {
                final String status = ordStatus;
                orders.getOrDefault(sessionID.toString(), new CopyOnWriteArrayList<>())
                      .stream()
                      .filter(m -> clOrdId.equals(m.get(ClOrdID.FIELD)))
                      .findFirst()
                      .ifPresent(m -> m.put(OrdStatus.FIELD, status));
            }
        } catch (FieldNotFound ignored) {}
    }

    // ── OrderService ──────────────────────────────────────────────────────────

    @Override
    public void sendNewOrder(String sessionId, Map<Integer, String> fields) {
        SessionID sid = resolve(sessionId);
        NewOrderSingle order = new NewOrderSingle();

        // Sensible defaults — callers may override via the fields map
        order.set(new ClOrdID(String.valueOf(clOrdIdSeq.incrementAndGet())));
        order.set(new TransactTime(LocalDateTime.now(ZoneOffset.UTC)));
        order.set(new OrdType(OrdType.LIMIT));

        fields.forEach((tag, value) -> order.setString(tag, value));

        send(order, sid);
    }

    @Override
    public void cancelOrder(String sessionId, String clOrdId) {
        SessionID sid = resolve(sessionId);
        OrderCancelRequest cancel = new OrderCancelRequest();

        cancel.set(new ClOrdID(String.valueOf(clOrdIdSeq.incrementAndGet())));
        cancel.set(new OrigClOrdID(clOrdId));
        cancel.set(new TransactTime(LocalDateTime.now(ZoneOffset.UTC)));

        // Copy Symbol and Side from the original order when available
        orders.getOrDefault(sessionId, new CopyOnWriteArrayList<>())
              .stream()
              .filter(m -> clOrdId.equals(m.get(ClOrdID.FIELD)))
              .findFirst()
              .ifPresent(orig -> {
                  String sym  = orig.get(Symbol.FIELD);
                  String side = orig.get(Side.FIELD);
                  if (sym  != null) cancel.setString(Symbol.FIELD, sym);
                  if (side != null) cancel.setString(Side.FIELD, side);
              });

        send(cancel, sid);
    }

    @Override
    public List<Map<Integer, String>> listOrders(String sessionId) {
        List<Map<Integer, String>> list = orders.get(sessionId);
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }

    @Override
    public Optional<MessageSnapshot> findSnapshot(String sessionId, String clOrdId) {
        Map<String, MessageSnapshot> bySession = snapshots.get(sessionId);
        if (bySession == null) return Optional.empty();
        return Optional.ofNullable(bySession.get(clOrdId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void send(Message message, SessionID sid) {
        try {
            session.sendToTarget(message, sid);
        } catch (SessionNotFound e) {
            throw new RuntimeException("FIX session not found: " + sid, e);
        }
    }

    private SessionID resolve(String sessionId) {
        SessionID sid = sessionIDs.get(sessionId);
        if (sid == null) throw new IllegalArgumentException("Unknown FIX session: " + sessionId);
        return sid;
    }

    private Map<Integer, String> extractFields(Message message) {
        Map<Integer, String> result = new LinkedHashMap<>();
        Iterator<Field<?>> headerIt = message.getHeader().iterator();
        while (headerIt.hasNext()) { Field<?> f = headerIt.next(); result.put(f.getTag(), f.getObject().toString()); }
        Iterator<Field<?>> bodyIt = message.iterator();
        while (bodyIt.hasNext())   { Field<?> f = bodyIt.next();   result.put(f.getTag(), f.getObject().toString()); }
        return result;
    }
}
