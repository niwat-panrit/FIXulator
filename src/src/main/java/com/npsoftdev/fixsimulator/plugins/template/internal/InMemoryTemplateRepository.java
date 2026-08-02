package com.npsoftdev.fixsimulator.plugins.template.internal;

import quickfix.SessionID;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import com.npsoftdev.fixsimulator.plugins.template.api.FixMessageTemplate;
import com.npsoftdev.fixsimulator.plugins.template.api.TemplateRepository;

/**
 * Process-local {@link TemplateRepository}. Templates live only for the
 * lifetime of the JVM; restart loses them.
 *
 * <p>Suitable for the first iteration where the goal is to validate the
 * template engine end-to-end. Replace with a persistent implementation
 * (file-system or DB) once the feature design has settled.</p>
 */
public class InMemoryTemplateRepository implements TemplateRepository {

    private static final long serialVersionUID = 1L;

    private final Map<String, FixMessageTemplate> byId = new ConcurrentHashMap<>();

    @Override
    public void save(FixMessageTemplate template) {
        byId.put(template.id(), template);
    }

    @Override
    public Optional<FixMessageTemplate> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<FixMessageTemplate> findAll() {
        // Stable ordering: by priority ascending (lower number = higher priority),
        // then by name alphabetically for predictable UI rendering.
        List<FixMessageTemplate> all = new ArrayList<>(byId.values());
        all.sort(Comparator.comparingInt(FixMessageTemplate::priority)
                           .thenComparing(FixMessageTemplate::name));
        return all;
    }

    @Override
    public List<FixMessageTemplate> findVisibleTo(SessionID sessionID) {
        List<FixMessageTemplate> visible = new ArrayList<>();
        for (FixMessageTemplate t : findAll()) {
            if (t.scope().appliesTo(sessionID)) visible.add(t);
        }
        return visible;
    }

    @Override
    public void delete(String id) {
        FixMessageTemplate t = byId.get(id);
        if (t != null && t.isDeletionProtected()) {
            throw new IllegalStateException(
                    "Template '" + t.name() + "' is a built-in template and cannot be deleted.");
        }
        byId.remove(id);
    }
}
