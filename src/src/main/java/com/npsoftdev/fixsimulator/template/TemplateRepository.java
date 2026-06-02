package com.npsoftdev.fixsimulator.template;

import quickfix.SessionID;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link FixMessageTemplate}s.
 *
 * <p>The first cut ships with {@link InMemoryTemplateRepository}. A future
 * {@code FileTemplateRepository} (JSON-per-template in a configurable directory)
 * can drop in without other code changes — repository ownership is centralised
 * in {@link com.npsoftdev.fixsimulator.plugin.DefaultOrderManagerPlugin#initialize}.</p>
 *
 * <p>{@link #findVisibleTo(SessionID)} is the canonical query the UI uses
 * when populating the template picker for the active session — it returns
 * the union of {@link TemplateScope.Global} templates and {@link TemplateScope.Session}
 * templates whose scope matches.</p>
 */
public interface TemplateRepository extends Serializable {

    /** Insert or replace by {@link FixMessageTemplate#id()}. */
    void save(FixMessageTemplate template);

    Optional<FixMessageTemplate> findById(String id);

    /** All templates in registration order. */
    List<FixMessageTemplate> findAll();

    /**
     * Templates with {@link TemplateScope.Global} scope plus those whose
     * {@link TemplateScope.Session} scope matches the given session.
     */
    List<FixMessageTemplate> findVisibleTo(SessionID sessionID);

    /** No-op when absent. */
    void delete(String id);
}
