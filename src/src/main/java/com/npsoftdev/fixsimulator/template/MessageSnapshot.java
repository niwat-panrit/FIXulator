package com.npsoftdev.fixsimulator.template;

import quickfix.Field;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.field.MsgType;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured snapshot of a FIX {@link Message} captured for later inspection,
 * audit, or template extraction.
 *
 * <p>Unlike the flat {@code Map<Integer,String>} the legacy order store uses,
 * a snapshot preserves the header vs body distinction so that
 * {@link TemplateService#captureFromMessage} can correctly purge
 * session/transport-level header fields when promoting the snapshot to a template.</p>
 *
 * <p>Repeating groups are intentionally not modeled in this first iteration.
 * When the first template that needs groups arrives, add a {@code groups}
 * field here and extend {@link FixMessageBuilder} to round-trip it.</p>
 */
public record MessageSnapshot(
        String msgType,
        String beginString,
        Map<Integer, String> headerFields,
        Map<Integer, String> bodyFields,
        Instant capturedAt
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public MessageSnapshot {
        headerFields = Collections.unmodifiableMap(new LinkedHashMap<>(headerFields));
        bodyFields   = Collections.unmodifiableMap(new LinkedHashMap<>(bodyFields));
    }

    /**
     * Builds a snapshot from a live QuickFIX/J message. Header iteration yields
     * header fields; body iteration yields body fields. {@code msgType} and
     * {@code beginString} are read off the header when present.
     */
    public static MessageSnapshot capture(Message message) {
        Map<Integer, String> header = new LinkedHashMap<>();
        Iterator<Field<?>> hIt = message.getHeader().iterator();
        while (hIt.hasNext()) {
            Field<?> f = hIt.next();
            header.put(f.getTag(), f.getObject().toString());
        }

        Map<Integer, String> body = new LinkedHashMap<>();
        Iterator<Field<?>> bIt = message.iterator();
        while (bIt.hasNext()) {
            Field<?> f = bIt.next();
            body.put(f.getTag(), f.getObject().toString());
        }

        String msgType = header.getOrDefault(MsgType.FIELD, "");
        String beginString = header.getOrDefault(8, "");

        return new MessageSnapshot(msgType, beginString, header, body, Instant.now());
    }

    /**
     * Backwards-compatible flat view (header + body merged) for callers that
     * still want the legacy shape. Header entries appear first.
     */
    public Map<Integer, String> flatFields() {
        Map<Integer, String> flat = new LinkedHashMap<>(headerFields);
        flat.putAll(bodyFields);
        return Collections.unmodifiableMap(flat);
    }
}
