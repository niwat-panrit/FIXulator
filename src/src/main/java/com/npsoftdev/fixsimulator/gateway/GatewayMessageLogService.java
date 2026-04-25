package com.npsoftdev.fixsimulator.gateway;

import com.npsoftdev.fixsimulator.service.MessageLogService;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link MessageLogService} implementation that records every FIX message
 * passing through the QuickFIX/J {@code Application} callbacks.
 *
 * <p>Messages are stored in memory, newest first, per session.</p>
 */
public class GatewayMessageLogService implements MessageLogService, Serializable {

    private static final long serialVersionUID = 1L;

    /** keyed by session-ID string; inner list is newest-first */
    private final Map<String, CopyOnWriteArrayList<LogEntry>> log = new ConcurrentHashMap<>();

    // ── Called by DefaultFixGatewayPlugin ─────────────────────────────────────

    public void record(SessionID sessionID, Direction direction, Message message) {
        String msgType = "UNKNOWN";
        try { msgType = message.getHeader().getString(MsgType.FIELD); } catch (Exception ignored) {}

        LogEntry entry = new LogEntry(direction, msgType, message.toString(), Instant.now());
        log.computeIfAbsent(sessionID.toString(), k -> new CopyOnWriteArrayList<>()).add(0, entry);
    }

    // ── MessageLogService ─────────────────────────────────────────────────────

    @Override
    public List<LogEntry> getMessages(String sessionId) {
        List<LogEntry> entries = log.get(sessionId);
        return entries != null ? Collections.unmodifiableList(entries) : List.of();
    }

    @Override
    public List<LogEntry> getMessages(String sessionId, Direction direction) {
        return getMessages(sessionId).stream()
                .filter(e -> e.direction() == direction)
                .toList();
    }

    @Override
    public void clearLog(String sessionId) {
        log.remove(sessionId);
    }
}
