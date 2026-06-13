package com.npsoftdev.fixsimulator.gateway;

import com.npsoftdev.fixsimulator.service.OrderService;
import com.npsoftdev.fixsimulator.template.MessageSnapshot;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.OrderCancelRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link OrderService} implementation that sends FIX 4.4 orders via QuickFIX/J
 * and tracks their full lifecycle from inbound Execution Reports.
 *
 * <h3>Order identity</h3>
 * <p>Orders are keyed by their current {@code ClOrdID} (tag 11).  When an
 * OrderCancelReplaceRequest (G) is sent, the order's key transitions from
 * {@code OrigClOrdID} to the new {@code ClOrdID} immediately, so the
 * subsequent ExecutionReport (ExecType=Replaced) finds the row via the new key.
 * The same re-keying applies when an OrderCancelRequest (F) is sent.</p>
 *
 * <h3>Thread safety</h3>
 * <p>The outer list uses {@link CopyOnWriteArrayList} for safe structural
 * mutations.  Each order's field map is a {@link ConcurrentHashMap} so FIX-thread
 * writes and UI-thread reads never race on individual field updates.</p>
 */
public class GatewayOrderService implements OrderService, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(GatewayOrderService.class);

    /** Shared session-ID registry populated by the gateway plugin. */
    private final Map<String, SessionID> sessionIDs;

    /** Newest-first list of all orders, keyed by session-ID string. */
    private final Map<String, CopyOnWriteArrayList<Map<Integer, String>>> orders =
            new ConcurrentHashMap<>();

    /**
     * O(1) index: sessionId → currentClOrdId → order map (same object as in {@link #orders}).
     * Re-keyed whenever a Replace or Cancel request is sent.
     */
    private final Map<String, ConcurrentHashMap<String, Map<Integer, String>>> orderIndex =
            new ConcurrentHashMap<>();

    /**
     * Structured snapshots of outbound NewOrderSingle messages, used by the
     * "save as template" flow.  Keyed by {@code sessionId → clOrdId → snapshot}.
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

    /**
     * Handles outbound FIX messages.
     *
     * <ul>
     *   <li><b>D (NewOrderSingle)</b> — adds a new row to the order list.</li>
     *   <li><b>G (OrderCancelReplaceRequest)</b> — finds the existing order by
     *       {@code OrigClOrdID}, updates its fields (Price, OrderQty, etc.), sets
     *       {@code OrdStatus=E} (PendingReplace), and re-keys the index to the new
     *       {@code ClOrdID}.</li>
     *   <li><b>F (OrderCancelRequest)</b> — finds the existing order by
     *       {@code OrigClOrdID}, sets {@code OrdStatus=6} (PendingCancel), and
     *       re-keys the index to the cancel's {@code ClOrdID}.</li>
     * </ul>
     */
    public void onOutboundMessage(SessionID sessionID, Message message) {
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            String key     = sessionID.toString();

            if (MsgType.ORDER_SINGLE.equals(msgType)) {
                Map<Integer, String> flat = extractFields(message);
                orders.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(0, flat);
                String clOrdId = flat.get(ClOrdID.FIELD);
                if (clOrdId != null) {
                    orderIndex.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                              .put(clOrdId, flat);
                    snapshots.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                             .put(clOrdId, MessageSnapshot.capture(message));
                    log.info("→ NewOrderSingle [{}] clOrdId={} symbol={} side={} qty={} price={}",
                            sessionID, clOrdId,
                            flat.get(Symbol.FIELD), flat.get(Side.FIELD),
                            flat.get(OrderQty.FIELD), flat.get(Price.FIELD));
                }

            } else if (MsgType.ORDER_CANCEL_REPLACE_REQUEST.equals(msgType)) {
                String newClOrdId  = message.getString(ClOrdID.FIELD);
                String origClOrdId = message.getString(OrigClOrdID.FIELD);
                log.info("→ OrderCancelReplaceRequest [{}] clOrdId={} origClOrdId={}",
                        sessionID, newClOrdId, origClOrdId);
                Map<Integer, String> existing = findByClOrdId(key, origClOrdId);
                if (existing != null) {
                    existing.put(ClOrdID.FIELD,  newClOrdId);
                    existing.put(OrdStatus.FIELD, "E"); // PendingReplace
                    copyIfPresent(message, existing, Price.FIELD);
                    copyIfPresent(message, existing, OrderQty.FIELD);
                    copyIfPresent(message, existing, Symbol.FIELD);
                    copyIfPresent(message, existing, Side.FIELD);
                    copyIfPresent(message, existing, OrdType.FIELD);
                    copyIfPresent(message, existing, TimeInForce.FIELD);
                    rekey(key, origClOrdId, newClOrdId, existing);
                } else {
                    // Fallback: originator not found — record as standalone row
                    Map<Integer, String> flat = extractFields(message);
                    orders.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(0, flat);
                    orderIndex.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                              .put(newClOrdId, flat);
                }

            } else if (MsgType.ORDER_CANCEL_REQUEST.equals(msgType)) {
                String newClOrdId  = message.getString(ClOrdID.FIELD);
                String origClOrdId = message.getString(OrigClOrdID.FIELD);
                log.info("→ OrderCancelRequest [{}] clOrdId={} origClOrdId={}",
                        sessionID, newClOrdId, origClOrdId);
                Map<Integer, String> existing = findByClOrdId(key, origClOrdId);
                if (existing != null) {
                    existing.put(ClOrdID.FIELD,  newClOrdId);
                    existing.put(OrdStatus.FIELD, "6"); // PendingCancel
                    rekey(key, origClOrdId, newClOrdId, existing);
                }
                // If not found, silently ignore (cancel for an unknown order)
            }
        } catch (FieldNotFound ignored) {}
    }

    /**
     * Handles inbound Execution Reports and Order Cancel Rejects.
     *
     * <p>For ExecutionReports, updates: {@code OrdStatus}, {@code ExecType},
     * {@code CumQty}, {@code LeavesQty}, {@code AvgPx}, {@code LastPx},
     * {@code LastQty}, {@code Text}, and {@code TransactTime}.
     * For Replaced reports ({@code ExecType=5}), also refreshes {@code Price}
     * and {@code OrderQty} from the report.</p>
     *
     * <p>For Order Cancel Rejects, restores the order status from the reject's
     * {@code OrdStatus} field (the exchange's current view of the order).</p>
     */
    public void onInboundMessage(SessionID sessionID, Message message) {
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            String clOrdId = message.getString(ClOrdID.FIELD);
            String key     = sessionID.toString();

            Map<Integer, String> order = findByClOrdId(key, clOrdId);
            if (order == null) return;

            if (MsgType.EXECUTION_REPORT.equals(msgType)) {
                // Core status and execution fields
                copyIfPresent(message, order, OrdStatus.FIELD);
                copyIfPresent(message, order, ExecType.FIELD);
                copyIfPresent(message, order, CumQty.FIELD);
                copyIfPresent(message, order, LeavesQty.FIELD);
                copyIfPresent(message, order, AvgPx.FIELD);
                copyIfPresent(message, order, LastPx.FIELD);
                copyIfPresent(message, order, LastQty.FIELD);
                copyIfPresent(message, order, Text.FIELD);
                copyIfPresent(message, order, TransactTime.FIELD);

                // On Replaced: also sync the amended order terms from the report
                String execType = order.get(ExecType.FIELD);
                if ("5".equals(execType)) {
                    copyIfPresent(message, order, Price.FIELD);
                    copyIfPresent(message, order, OrderQty.FIELD);
                    copyIfPresent(message, order, Symbol.FIELD);
                    copyIfPresent(message, order, Side.FIELD);
                }
                log.info("← ExecutionReport [{}] clOrdId={} ordStatus={} execType={} cumQty={} leavesQty={}",
                        sessionID, clOrdId,
                        order.get(OrdStatus.FIELD), execType,
                        order.get(CumQty.FIELD), order.get(LeavesQty.FIELD));

            } else if (MsgType.ORDER_CANCEL_REJECT.equals(msgType)) {
                // Restore the exchange's current status (overrides our optimistic PendingCancel/Replace)
                copyIfPresent(message, order, OrdStatus.FIELD);
                copyIfPresent(message, order, Text.FIELD);
                log.info("← OrderCancelReject [{}] clOrdId={} ordStatus={} text={}",
                        sessionID, clOrdId, order.get(OrdStatus.FIELD), order.get(Text.FIELD));
            }
        } catch (FieldNotFound ignored) {}
    }

    // ── OrderService ──────────────────────────────────────────────────────────

    @Override
    public void sendNewOrder(String sessionId, Map<Integer, String> fields) {
        SessionID sid = resolve(sessionId);
        NewOrderSingle order = new NewOrderSingle();

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

        // Copy Symbol and Side from the original order
        Map<Integer, String> orig = findByClOrdId(sessionId, clOrdId);
        if (orig != null) {
            String sym  = orig.get(Symbol.FIELD);
            String side = orig.get(Side.FIELD);
            if (sym  != null) cancel.setString(Symbol.FIELD, sym);
            if (side != null) cancel.setString(Side.FIELD,   side);
        }

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

    /** O(1) index lookup by current {@code ClOrdID}. Returns {@code null} if not found. */
    private Map<Integer, String> findByClOrdId(String sessionKey, String clOrdId) {
        if (clOrdId == null) return null;
        ConcurrentHashMap<String, Map<Integer, String>> idx = orderIndex.get(sessionKey);
        return idx != null ? idx.get(clOrdId) : null;
    }

    /** Updates the index when an order's active {@code ClOrdID} changes. */
    private void rekey(String sessionKey, String oldClOrdId, String newClOrdId,
                       Map<Integer, String> order) {
        ConcurrentHashMap<String, Map<Integer, String>> idx =
                orderIndex.computeIfAbsent(sessionKey, k -> new ConcurrentHashMap<>());
        idx.remove(oldClOrdId);
        idx.put(newClOrdId, order);
    }

    /** Copies a field from a FIX {@link Message} into the order map if it is set. */
    private static void copyIfPresent(Message src, Map<Integer, String> dst, int tag) {
        try {
            if (src.isSetField(tag)) dst.put(tag, src.getString(tag));
        } catch (FieldNotFound ignored) {}
    }

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

    /** Extracts all header and body fields from a message into a thread-safe map. */
    private static Map<Integer, String> extractFields(Message message) {
        Map<Integer, String> result = new ConcurrentHashMap<>();
        Iterator<Field<?>> headerIt = message.getHeader().iterator();
        while (headerIt.hasNext()) { Field<?> f = headerIt.next(); result.put(f.getTag(), f.getObject().toString()); }
        Iterator<Field<?>> bodyIt = message.iterator();
        while (bodyIt.hasNext())   { Field<?> f = bodyIt.next();   result.put(f.getTag(), f.getObject().toString()); }
        return result;
    }
}
