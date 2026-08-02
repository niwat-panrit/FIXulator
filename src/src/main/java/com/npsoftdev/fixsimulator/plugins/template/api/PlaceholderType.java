package com.npsoftdev.fixsimulator.plugins.template.api;

/**
 * Catalog of supported placeholder types that {@link PlaceholderResolver}
 * knows how to resolve at send-time.
 *
 * <p>Templates reference placeholders via {@link FieldValue.Placeholder}. The
 * resolver consults the current send context (session, already-populated
 * fields) to produce the concrete string value written to the FIX message.</p>
 *
 * <p>To add a new placeholder kind: append a new enum constant here, then
 * extend the default resolver. No template or builder code needs to change.</p>
 */
public enum PlaceholderType {

    /** Monotonic client order ID, suitable for ClOrdID (tag 11) / OrigClOrdID (tag 41). */
    ORDER_ID,

    /** Current UTC timestamp formatted per FIX UTCTimestamp (tag 60 — TransactTime). */
    TRANSACT_TIME,

    /** Current UTC timestamp for header tag 52 (SendingTime). Same format as TRANSACT_TIME. */
    SENDING_TIME,

    /** Random UUID — useful for ExecID, QuoteReqID, etc. */
    UUID,

    /** SenderCompID derived from the active SessionID (tag 49). */
    SESSION_SENDER,

    /** TargetCompID derived from the active SessionID (tag 56). */
    SESSION_TARGET
}
