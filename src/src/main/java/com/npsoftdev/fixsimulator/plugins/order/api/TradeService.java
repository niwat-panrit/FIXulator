package com.npsoftdev.fixsimulator.plugins.order.api;

import java.util.List;
import java.util.Map;

/**
 * Provides access to Execution Report (8) fill/trade data received on a session.
 */
public interface TradeService {

    /**
     * Returns all trade executions received on the session.
     * Each entry is a map of FIX tag numbers to their string values.
     */
    List<Map<Integer, String>> listTrades(String sessionId);

    /**
     * Returns the execution details for a single trade by ExecID (tag 17).
     *
     * @return a map of tag-value pairs, or an empty map if not found
     */
    Map<Integer, String> getTradeByExecId(String sessionId, String execId);
}
