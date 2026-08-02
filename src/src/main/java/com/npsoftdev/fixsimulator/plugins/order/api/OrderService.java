package com.npsoftdev.fixsimulator.plugins.order.api;

import com.npsoftdev.fixsimulator.plugins.template.api.MessageSnapshot;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sends and tracks FIX order messages for a session.
 *
 * <p>This interface stays focused on the order-domain operations the UI
 * needs directly. Template-driven sending of arbitrary FIX messages lives on
 * {@link com.npsoftdev.fixsimulator.plugins.template.api.TemplateService}; this service
 * still records outbound messages so that orders dispatched via templates
 * appear in {@link #listOrders} alongside legacy direct sends.</p>
 */
public interface OrderService {

    /**
     * Sends a New Order Single (D) message.
     *
     * <p>Retained for backward compatibility with the existing UI form.
     * Prefer
     * {@link com.npsoftdev.fixsimulator.plugins.template.api.TemplateService#send(String, String, Map)}
     * once a template-based form is in place.</p>
     *
     * @param sessionId target FIX session
     * @param fields    FIX tag-value pairs (e.g. tag 55 → symbol, tag 54 → side)
     */
    void sendNewOrder(String sessionId, Map<Integer, String> fields);

    /**
     * Sends an Order Cancel Request (F) for an existing order.
     *
     * @param sessionId target FIX session
     * @param clOrdId   ClOrdID of the order to cancel
     */
    void cancelOrder(String sessionId, String clOrdId);

    /**
     * Returns a summary of all orders seen on the session.
     * Each entry is a map of FIX tag numbers to their string values.
     */
    List<Map<Integer, String>> listOrders(String sessionId);

    /**
     * Returns the structured {@link MessageSnapshot} for the order with the
     * given {@code clOrdId} on the given session, if recorded. Used by the
     * "save as template" flow — the snapshot preserves header / body
     * separation that the flat map returned by {@link #listOrders} loses.
     */
    Optional<MessageSnapshot> findSnapshot(String sessionId, String clOrdId);
}
