package com.npsoftdev.fixsimulator.plugins.template.api;

import quickfix.SessionID;

import java.io.Serializable;
import java.util.Map;

/**
 * Resolves {@link PlaceholderType placeholders} to their concrete string
 * values at message-build time.
 *
 * <p>Resolvers see a {@link ResolutionContext} containing the session
 * the message is being sent on and the fields already resolved so far on
 * the message. Resolvers must be side-effect free for testable values; the
 * default implementation is a deliberate exception for {@link PlaceholderType#ORDER_ID}
 * (it must increment a counter).</p>
 *
 * <p>To register a new placeholder kind: add it to {@link PlaceholderType},
 * then handle it inside the implementation. The {@link FixMessageBuilder}
 * never needs to know about new placeholder kinds.</p>
 */
public interface PlaceholderResolver extends Serializable {

    /**
     * @param type the placeholder kind to resolve
     * @param ctx  resolution context (session, already-resolved fields)
     * @return the concrete string value to write
     * @throws IllegalArgumentException if {@code type} is unsupported by this resolver
     */
    String resolve(PlaceholderType type, ResolutionContext ctx);

    /**
     * Read-only view of the state available to a resolver.
     *
     * @param sessionID         active FIX session
     * @param resolvedFields    fields resolved so far on the message (header + body merged)
     */
    record ResolutionContext(SessionID sessionID, Map<Integer, String> resolvedFields)
            implements Serializable {

        private static final long serialVersionUID = 1L;
    }
}
