package com.npsoftdev.fixsimulator.template;

import quickfix.Message;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * High-level façade for template operations used by UI pages.
 *
 * <p>This is the single entry-point the UI talks to for template-based
 * message sending and template authoring; it composes
 * {@link TemplateRepository}, {@link FixMessageBuilder}, and
 * {@link MessageDispatcher} so that callers don't need to wire those pieces
 * themselves.</p>
 */
public interface TemplateService extends Serializable {

    /**
     * Builds a message from the template and dispatches it on the session.
     *
     * @param sessionId   target FIX session ID string
     * @param templateId  ID of a template known to the repository
     * @param overrides   values for {@link FieldValue.UserInput} fields, by name
     * @return the dispatched message (post-build, pre-engine-enrichment)
     * @throws IllegalArgumentException if the session or template is unknown
     * @throws RuntimeException         if dispatch fails
     */
    Message send(String sessionId, String templateId, Map<String, String> overrides);

    /**
     * Creates a new template from a captured message snapshot.
     *
     * <p>Engine-owned header tags ({@link FixHeaderFields#ENGINE_OWNED}) are
     * stripped; remaining fields become {@link FieldValue.Literal} entries.
     * The caller is expected to promote individual entries to UserInput /
     * Placeholder / Derived later via the template editor UI.</p>
     */
    FixMessageTemplate captureFromMessage(String id, String name,
                                          MessageSnapshot snapshot,
                                          TemplateScope scope);

    // ── Repository pass-throughs (UI convenience) ─────────────────────────────

    List<FixMessageTemplate> findVisibleTo(String sessionId);

    java.util.Optional<FixMessageTemplate> findById(String templateId);

    void save(FixMessageTemplate template);

    void delete(String templateId);
}
