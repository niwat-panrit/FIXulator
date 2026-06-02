package com.npsoftdev.fixsimulator.template;

import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionNotFound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link TemplateService}.
 *
 * <p>Holds references to the four collaborators it composes:
 * {@link TemplateRepository} (where templates live),
 * {@link FixMessageBuilder} (how a template becomes a Message),
 * {@link MessageDispatcher} (how a Message reaches the wire), and the
 * session-ID registry shared with the gateway (for string→SessionID lookup).</p>
 */
public class DefaultTemplateService implements TemplateService {

    private static final long serialVersionUID = 1L;

    private final TemplateRepository repository;
    private final FixMessageBuilder  builder;
    private final MessageDispatcher  dispatcher;
    private final Map<String, SessionID> sessionIDs;

    public DefaultTemplateService(TemplateRepository repository,
                                  FixMessageBuilder builder,
                                  MessageDispatcher dispatcher,
                                  Map<String, SessionID> sessionIDs) {
        this.repository  = Objects.requireNonNull(repository, "repository");
        this.builder     = Objects.requireNonNull(builder, "builder");
        this.dispatcher  = Objects.requireNonNull(dispatcher, "dispatcher");
        this.sessionIDs  = Objects.requireNonNull(sessionIDs, "sessionIDs");
    }

    // ── Send path ─────────────────────────────────────────────────────────────

    @Override
    public Message send(String sessionId, String templateId, Map<String, String> overrides) {
        SessionID sid = sessionIDs.get(sessionId);
        if (sid == null) throw new IllegalArgumentException("Unknown FIX session: " + sessionId);

        FixMessageTemplate template = repository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown template: " + templateId));

        Message msg = builder.build(template, overrides, sid);
        try {
            dispatcher.dispatch(msg, sid);
        } catch (SessionNotFound e) {
            throw new RuntimeException("FIX session not found: " + sid, e);
        }
        return msg;
    }

    // ── Capture path ──────────────────────────────────────────────────────────

    @Override
    public FixMessageTemplate captureFromMessage(String id, String name,
                                                 MessageSnapshot snapshot,
                                                 TemplateScope scope) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(scope, "scope");

        FixMessageTemplate.Builder b = FixMessageTemplate.builder()
                .id(id).name(name)
                .beginString(snapshot.beginString().isEmpty() ? "FIX.4.4" : snapshot.beginString())
                .msgType(snapshot.msgType())
                .scope(scope);

        // Header fields first (skipping engine-owned), then body fields, both as Literal.
        List<FieldSpec> specs = new ArrayList<>();
        snapshot.headerFields().forEach((tag, value) -> {
            if (!FixHeaderFields.isEngineOwned(tag)) {
                specs.add(FieldSpec.literal(tag, value));
            }
        });
        snapshot.bodyFields().forEach((tag, value) ->
                specs.add(FieldSpec.literal(tag, value)));

        return b.fields(specs).build();
    }

    // ── Repository pass-throughs ─────────────────────────────────────────────

    @Override
    public List<FixMessageTemplate> findVisibleTo(String sessionId) {
        SessionID sid = sessionIDs.get(sessionId);
        if (sid == null) return List.of();
        return repository.findVisibleTo(sid);
    }

    @Override
    public Optional<FixMessageTemplate> findById(String templateId) {
        return repository.findById(templateId);
    }

    @Override
    public void save(FixMessageTemplate template) {
        repository.save(template);
    }

    @Override
    public void delete(String templateId) {
        repository.delete(templateId);
    }
}
