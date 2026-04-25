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
 * FIX 4.4 Execution Reports (ExecType = '1' partial fill, '2' fill).
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
            if (execType == ExecType.PARTIAL_FILL || execType == ExecType.FILL) {
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
