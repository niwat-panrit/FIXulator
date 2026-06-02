package com.npsoftdev.fixsimulator.template;

import quickfix.field.BeginString;
import quickfix.field.BodyLength;
import quickfix.field.CheckSum;
import quickfix.field.MsgSeqNum;
import quickfix.field.MsgType;
import quickfix.field.SenderCompID;
import quickfix.field.SendingTime;
import quickfix.field.TargetCompID;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Centralised list of FIX header / envelope tags that the QuickFIX/J engine
 * owns and that templates must never carry literally.
 *
 * <p>When extracting a template from a captured message, these tags are
 * stripped — the engine will re-generate them at send-time based on the
 * session, sequence number, and clock. Templating them would either be a no-op
 * (the engine overrides) or actively break the session (e.g. a stale seq num).</p>
 *
 * <p>{@link MsgType} is excluded from templates as a <em>field</em> because
 * it's carried as a top-level template attribute ({@link FixMessageTemplate#msgType()}).</p>
 */
public final class FixHeaderFields {

    private FixHeaderFields() {}

    /** Tags the engine owns; must not appear in a template's field list. */
    public static final Set<Integer> ENGINE_OWNED = Collections.unmodifiableSet(
            new LinkedHashSet<>(java.util.List.of(
                    BeginString.FIELD,    //   8
                    BodyLength.FIELD,     //   9
                    MsgType.FIELD,        //  35
                    MsgSeqNum.FIELD,      //  34
                    SenderCompID.FIELD,   //  49
                    TargetCompID.FIELD,   //  56
                    SendingTime.FIELD,    //  52
                    CheckSum.FIELD        //  10
            )));

    /** True when the tag belongs to the engine and should be excluded from templates. */
    public static boolean isEngineOwned(int tag) {
        return ENGINE_OWNED.contains(tag);
    }
}
