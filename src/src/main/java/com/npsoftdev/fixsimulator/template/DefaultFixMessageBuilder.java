package com.npsoftdev.fixsimulator.template;

import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default {@link FixMessageBuilder}. Resolves {@link FieldSpec}s in two passes
 * so that {@link FieldValue.Derived} fields can reference values produced by
 * earlier field specs regardless of declaration order:
 *
 * <ol>
 *   <li><b>Pass 1</b> — resolve Literal / UserInput / Enumeration / Placeholder specs into
 *       a working map. Literal values are additionally scanned for
 *       {@code $(name)} / {@code $(name:param)} dynamic tokens and resolved
 *       via the {@link DynamicValueRegistry}. Derived specs are deferred.</li>
 *   <li><b>Pass 2</b> — resolve Derived specs against the working map.</li>
 *   <li><b>Write phase</b> — walk the template's field list in original order
 *       and write resolved values onto the {@link Message} body.</li>
 * </ol>
 *
 * <p>If a Derived spec's source tag has not been resolved the derived field is
 * silently skipped. Engine-owned tags ({@link FixHeaderFields#ENGINE_OWNED})
 * are skipped during the write phase.</p>
 *
 * <h3>Dynamic token syntax</h3>
 * <p>Any {@link FieldValue.Literal} value may embed one or more tokens of the
 * form {@code $(name)} or {@code $(name:param)}. Built-in token names are:
 * {@code order_id}, {@code timestamp}, {@code sending_time}, {@code uuid},
 * {@code sender}, {@code target}. Custom constant tokens are looked up via
 * the {@link DynamicValueRegistry}.</p>
 */
public class DefaultFixMessageBuilder implements FixMessageBuilder {

    private static final long serialVersionUID = 1L;

    /** Matches $(name) or $(name:param) — name is alphanumeric + underscores. */
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\\$\\(([a-zA-Z0-9_]+)(?::([^)]*))?\\)");

    private final PlaceholderResolver  placeholderResolver;
    private final ValueMappingService  mappingService;
    private final DynamicValueRegistry dynamicValueRegistry;

    public DefaultFixMessageBuilder(PlaceholderResolver placeholderResolver,
                                    ValueMappingService mappingService,
                                    DynamicValueRegistry dynamicValueRegistry) {
        this.placeholderResolver  = Objects.requireNonNull(placeholderResolver, "placeholderResolver");
        this.mappingService       = Objects.requireNonNull(mappingService, "mappingService");
        this.dynamicValueRegistry = dynamicValueRegistry; // nullable — gracefully skipped when absent
    }

    @Override
    public Message build(FixMessageTemplate template,
                         Map<String, String> userOverrides,
                         SessionID sessionID) {
        Objects.requireNonNull(template, "template");
        Map<String, String> overrides = userOverrides == null ? Map.of() : userOverrides;

        // ── Pass 1: resolve everything except Derived ─────────────────────────
        Map<Integer, String> resolved = new LinkedHashMap<>();
        List<FieldSpec> deferred = new ArrayList<>();

        for (FieldSpec spec : template.fields()) {
            if (FixHeaderFields.isEngineOwned(spec.tag())) continue;
            FieldValue value = spec.value();

            if (value instanceof FieldValue.Literal l) {
                String v = resolveTokens(l.value(), sessionID, resolved);
                resolved.put(spec.tag(), v);

            } else if (value instanceof FieldValue.UserInput ui) {
                String v = overrides.get(ui.name());
                if (v == null || v.isBlank()) v = ui.defaultValue();
                if (v != null) resolved.put(spec.tag(), v);

            } else if (value instanceof FieldValue.Enumeration en) {
                String v = overrides.get(en.name());
                if (v == null || v.isBlank()) v = en.defaultOption();
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
            if (key == null) continue;
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

    // ── Dynamic token resolution ──────────────────────────────────────────────

    /**
     * Scans {@code value} for {@code $(name)} / {@code $(name:param)} tokens and
     * substitutes each with its resolved string. Tokens that cannot be resolved
     * are left as-is so they remain visible in the sent message.
     */
    private String resolveTokens(String value, SessionID sessionID,
                                 Map<Integer, String> resolvedSoFar) {
        if (value == null || !value.contains("$(")) return value;

        Matcher m = TOKEN_PATTERN.matcher(value);
        if (!m.find()) return value;

        StringBuffer sb = new StringBuffer();
        m.reset();
        PlaceholderResolver.ResolutionContext ctx =
                new PlaceholderResolver.ResolutionContext(sessionID, resolvedSoFar);

        while (m.find()) {
            String name  = m.group(1);
            String param = m.group(2); // may be null
            String replacement = resolveToken(name, param, ctx);
            m.appendReplacement(sb,
                    Matcher.quoteReplacement(replacement != null ? replacement : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves a single token by name (+ optional param).
     * Priority: custom constant registry → built-in placeholder names.
     */
    private String resolveToken(String name, String param,
                                PlaceholderResolver.ResolutionContext ctx) {
        // 1. Custom constant from registry
        if (dynamicValueRegistry != null) {
            String custom = dynamicValueRegistry.resolveConstant(name).orElse(null);
            if (custom != null) return custom;
        }

        // 2. Built-in token names (mirrors PlaceholderType enum)
        return switch (name.toLowerCase()) {
            case "order_id"      -> placeholderResolver.resolve(PlaceholderType.ORDER_ID,      ctx);
            case "timestamp"     -> placeholderResolver.resolve(PlaceholderType.TRANSACT_TIME, ctx);
            case "sending_time"  -> placeholderResolver.resolve(PlaceholderType.SENDING_TIME,  ctx);
            case "uuid"          -> placeholderResolver.resolve(PlaceholderType.UUID,          ctx);
            case "sender"        -> placeholderResolver.resolve(PlaceholderType.SESSION_SENDER,ctx);
            case "target"        -> placeholderResolver.resolve(PlaceholderType.SESSION_TARGET,ctx);
            default              -> null; // unknown — leave token as-is
        };
    }
}
