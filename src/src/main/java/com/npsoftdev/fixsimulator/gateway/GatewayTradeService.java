package com.npsoftdev.fixsimulator.gateway;

import com.npsoftdev.fixsimulator.service.TradeService;
import quickfix.Field;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.ExecID;
import quickfix.field.ExecType;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link TradeService} implementation that captures fills from inbound
 * FIX Execution Reports where ExecType indicates a trade:
 * <ul>
 *   <li>'1' (PartialFill) — FIX 4.x standard</li>
 *   <li>'2' (Fill)        — FIX 4.x standard</li>
 *   <li>'F' (Trade)       — used by many counterparties in place of '1'/'2'</li>
 * </ul>
 */
public class GatewayTradeService implements TradeService, Serializable {

    private static final long serialVersionUID = 1L;

    /** keyed by session-ID string; inner list is newest-first */
    private final Map<String, CopyOnWriteArrayList<Map<Integer, String>>> trades =
            new ConcurrentHashMap<>();

    // ── Callback from DefaultFixGatewayPlugin ─────────────────────────────────

    public void onExecutionReport(SessionID sessionID, Message message) {
        try {
            char execType = message.getChar(ExecType.FIELD);
            if (execType == ExecType.PARTIAL_FILL || execType == ExecType.FILL
                    || execType == ExecType.TRADE) {
                Map<Integer, String> fields = new LinkedHashMap<>();
                java.util.Iterator<Field<?>> headerIt = message.getHeader().iterator();
                while (headerIt.hasNext()) { Field<?> f = headerIt.next(); fields.put(f.getTag(), f.getObject().toString()); }
                java.util.Iterator<Field<?>> bodyIt = message.iterator();
                while (bodyIt.hasNext())   { Field<?> f = bodyIt.next();   fields.put(f.getTag(), f.getObject().toString()); }

                trades.computeIfAbsent(sessionID.toString(), k -> new CopyOnWriteArrayList<>())
                      .add(0, fields);
            }
        } catch (FieldNotFound ignored) {}
    }

    // ── TradeService ──────────────────────────────────────────────────────────

    @Override
    public List<Map<Integer, String>> listTrades(String sessionId) {
        List<Map<Integer, String>> list = trades.get(sessionId);
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }

    @Override
    public Map<Integer, String> getTradeByExecId(String sessionId, String execId) {
        return listTrades(sessionId).stream()
                .filter(m -> execId.equals(m.get(ExecID.FIELD)))
                .findFirst()
                .orElse(Map.of());
    }
}
