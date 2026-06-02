package com.npsoftdev.fixsimulator.template;

import quickfix.Message;
import quickfix.SessionID;

import java.io.Serializable;
import java.util.Map;

/**
 * Constructs a {@link Message} from a {@link FixMessageTemplate}, a map of
 * per-request user inputs, and the target session.
 *
 * <p>The builder is the single place that knows how to translate the abstract
 * {@link FieldValue} variants into FIX wire-level tag/value pairs and how to
 * route fields into the message header vs body.</p>
 *
 * <p>Engine-owned header fields ({@link FixHeaderFields#ENGINE_OWNED}) are
 * never written by the builder — the QuickFIX/J engine will populate them at
 * send-time. If a template field spec references such a tag, the builder
 * silently skips it.</p>
 */
public interface FixMessageBuilder extends Serializable {

    /**
     * @param template      the template to apply
     * @param userOverrides values for {@link FieldValue.UserInput} fields keyed by their name
     * @param sessionID     target session — used for placeholder resolution and routing
     * @return a fully-populated, not-yet-sent {@link Message}
     */
    Message build(FixMessageTemplate template,
                  Map<String, String> userOverrides,
                  SessionID sessionID);
}
