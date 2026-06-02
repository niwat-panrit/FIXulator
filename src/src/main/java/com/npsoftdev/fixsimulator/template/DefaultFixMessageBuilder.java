package com.npsoftdev.fixsimulator.template;

import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link FixMessageBuilder}. Resolves {@link FieldSpec}s in two passes
 * so that {@link FieldValue.Derived} fields can reference values produced by
 * earlier field specs regardless of declaration order:
 *
 * <ol>
 *   <li><b>Pass 1</b> — resolve Literal / UserInput / Placeholder specs into
 *       a working map. Derived specs are deferred.</li>
 *   <li><b>Pass 2</b> — resolve Derived specs against the working map.</li>
 *   <li><b>Write phase</b> — walk the template's field list in original order
 *       and write resolved values onto the {@link Message} body.</li>
 * </ol>
 *
 * <p>If a Derived spec's source tag has not been resolved (because either it
 * isn't in the template or the user didn't override it), the derived field is
 * silently skipped — callers can detect missing fields by inspecting the
 * returned message.</p>
 *
 * <p>Engine-owned tags ({@link FixHeaderFields#ENGINE_OWNED}) appearing in a
 * template are skipped during the write phase to avoid stomping on QuickFIX/J.
 * The template's {@link FixMessageTemplate#msgType() msgType} is written to
 * header tag 35 (MsgType) directly by the builder.</p>
 */
public class DefaultFixMessageBuilder implements FixMessageBuilder {

    private static final long serialVersionUID = 1L;

    private final PlaceholderResolver placeholderResolver;
    private final ValueMappingService mappingService;

    public DefaultFixMessageBuilder(PlaceholderResolver placeholderResolver,
                                    ValueMappingService mappingService) {
        this.placeholderResolver = Objects.requireNonNull(placeholderResolver, "placeholderResolver");
        this.mappingService      = Objects.requireNonNull(mappingService, "mappingService");
    }

    @Override
    public Message build(FixMessageTemplate template,
                         Map<String, String> userOverrides,
                         SessionID sessionID) {
        Objects.requireNonNull(template, "template");
        Map<String, String> overrides = userOverrides == null ? Map.of() : userOverrides;

        // ── Pass 1: resolve everything except Derived ─────────────────────────
        // Uses instanceof pattern matching (stable since Java 16) rather than
        // type-pattern switch — the latter is preview-only until Java 21 and
        // this project targets Java 17.
        Map<Integer, String> resolved = new LinkedHashMap<>();
        List<FieldSpec> deferred = new ArrayList<>();

        for (FieldSpec spec : template.fields()) {
            if (FixHeaderFields.isEngineOwned(spec.tag())) continue;
            FieldValue value = spec.value();

            if (value instanceof FieldValue.Literal l) {
                resolved.put(spec.tag(), l.value());

            } else if (value instanceof FieldValue.UserInput ui) {
                String v = overrides.get(ui.name());
                if (v == null || v.isBlank()) v = ui.defaultValue();
                if (v != null) resolved.put(spec.tag(), v);

            } else if (value instanceof FieldValue.Placeholder p) {
                String v = placeholderResolver.resolve(
                        p.type(),
                        new PlaceholderResolver.ResolutionContext(sessionID, resolved));
                if (v != null) resolved.put(spec.tag(), v);

            } else if (value instanceof FieldValue.Derived) {
                deferred.add(spec);
            }
        }

        // ── Pass 2: resolve Derived now that all upstream values are known ───
        for (FieldSpec spec : deferred) {
            FieldValue.Derived d = (FieldValue.Derived) spec.value();
            String key = resolved.get(d.sourceTag());
            if (key == null) continue;          // upstream tag was never resolved
            mappingService.lookup(d.mappingName(), key)
                          .ifPresent(v -> resolved.put(spec.tag(), v));
        }

        // ── Write phase: build the Message preserving header/body routing ─────
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, template.msgType());

        for (FieldSpec spec : template.fields()) {
            if (FixHeaderFields.isEngineOwned(spec.tag())) continue;
            String v = resolved.get(spec.tag());
            if (v == null) continue;

            msg.setString(spec.tag(), v);
        }

        return msg;
    }
}
