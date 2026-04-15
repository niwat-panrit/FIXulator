package com.npsoftdev.fixsimulator.service;

import java.util.List;
import java.util.Map;

/**
 * Sends and tracks FIX order messages for a session.
 */
public interface OrderService {

    /**
     * Sends a New Order Single (D) message.
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
}
