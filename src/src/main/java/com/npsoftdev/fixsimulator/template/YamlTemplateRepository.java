package com.npsoftdev.fixsimulator.template;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.npsoftdev.fixsimulator.persistence.YamlPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.SessionID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * YAML-backed {@link TemplateRepository}.
 *
 * <p>Templates are persisted to {@code templates.yaml} inside the configured
 * data directory. The file is written atomically after every mutation so
 * a crash mid-write never corrupts it.</p>
 *
 * <p>On construction the repository eagerly loads whatever templates are
 * already in the file. If the file is absent (first run), the repository
 * starts empty; built-in templates are seeded by
 * {@link com.npsoftdev.fixsimulator.plugin.DefaultOrderManagerPlugin}
 * via the normal {@link #save} path, so they appear in the file after the
 * first startup.</p>
 *
 * <h3>YAML format</h3>
 * <pre>{@code
 * templates:
 *   - id: my-template
 *     name: My Template
 *     beginString: FIX.4.4
 *     msgType: D
 *     scope:
 *       type: global
 *     deletionProtected: false
 *     priority: 100
 *     fields:
 *       - tag: 11
 *         value:
 *           type: placeholder
 *           placeholderType: ORDER_ID
 *       - tag: 55
 *         value:
 *           type: userInput
 *           name: symbol
 * }</pre>
 */
public class YamlTemplateRepository implements TemplateRepository {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(YamlTemplateRepository.class);
    private static final String FILENAME = "templates.yaml";

    private final YamlPersistenceService yaml;

    /** In-memory view; the file is the authoritative store. */
    private final Map<String, FixMessageTemplate> byId = new LinkedHashMap<>();

    public YamlTemplateRepository(YamlPersistenceService yaml) {
        this.yaml = yaml;
        load();
    }

    // ── TemplateRepository ────────────────────────────────────────────────────

    @Override
    public synchronized void save(FixMessageTemplate template) {
        byId.put(template.id(), template);
        persist();
    }

    @Override
    public synchronized Optional<FixMessageTemplate> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public synchronized List<FixMessageTemplate> findAll() {
        List<FixMessageTemplate> all = new ArrayList<>(byId.values());
        all.sort(Comparator.comparingInt(FixMessageTemplate::priority)
                           .thenComparing(FixMessageTemplate::name));
        return all;
    }

    @Override
    public synchronized List<FixMessageTemplate> findVisibleTo(SessionID sessionID) {
        List<FixMessageTemplate> visible = new ArrayList<>();
        for (FixMessageTemplate t : findAll()) {
            if (t.scope().appliesTo(sessionID)) visible.add(t);
        }
        return visible;
    }

    @Override
    public synchronized void delete(String id) {
        FixMessageTemplate t = byId.get(id);
        if (t != null && t.isDeletionProtected()) {
            throw new IllegalStateException(
                    "Template '" + t.name() + "' is a built-in template and cannot be deleted.");
        }
        byId.remove(id);
        persist();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void load() {
        if (!yaml.exists(FILENAME)) return;
        try {
            TemplateListDto dto = yaml.load(FILENAME, TemplateListDto.class);
            if (dto.templates != null) {
                for (TemplateDto t : dto.templates) {
                    try {
                        byId.put(t.id, fromDto(t));
                    } catch (Exception e) {
                        log.warn("Skipping malformed template entry '{}': {}", t.id, e.getMessage());
                    }
                }
            }
            log.info("Loaded {} FIX message template(s) from {}", byId.size(), FILENAME);
        } catch (IOException e) {
            log.error("Failed to load {}; starting with empty template repository", FILENAME, e);
        }
    }

    private void persist() {
        TemplateListDto dto = new TemplateListDto();
        dto.templates = new ArrayList<>();
        for (FixMessageTemplate t : findAll()) {
            dto.templates.add(toDto(t));
        }
        try {
            yaml.save(FILENAME, dto);
        } catch (IOException e) {
            log.error("Failed to save templates to {}", FILENAME, e);
        }
    }

    // ── DTO ↔ domain conversions ──────────────────────────────────────────────

    private static FixMessageTemplate fromDto(TemplateDto d) {
        FixMessageTemplate.Builder b = FixMessageTemplate.builder()
                .id(d.id)
                .name(d.name)
                .description(d.description != null ? d.description : "")
                .beginString(d.beginString)
                .msgType(d.msgType)
                .scope(scopeFromDto(d.scope))
                .deletionProtected(d.deletionProtected)
                .priority(d.priority > 0 ? d.priority : 100);
        if (d.fields != null) {
            for (FieldSpecDto fs : d.fields) {
                b.addField(new FieldSpec(fs.tag, fieldValueFromDto(fs.value)));
            }
        }
        return b.build();
    }

    private static TemplateScope scopeFromDto(ScopeDto s) {
        if (s == null || !"session".equalsIgnoreCase(s.type)) return TemplateScope.global();
        return TemplateScope.session(s.sessionId);
    }

    private static FieldValue fieldValueFromDto(FieldValueDto v) {
        if (v instanceof LiteralDto d)     return new FieldValue.Literal(d.value);
        if (v instanceof UserInputDto d)   return new FieldValue.UserInput(d.name, d.defaultValue);
        if (v instanceof EnumerationDto d) return new FieldValue.Enumeration(d.name,
                d.options != null ? d.options : List.of(), d.defaultOption);
        if (v instanceof PlaceholderDto d) return new FieldValue.Placeholder(d.placeholderType);
        if (v instanceof DerivedDto d)     return new FieldValue.Derived(d.sourceTag, d.mappingName);
        throw new IllegalArgumentException("Unknown FieldValueDto type: " + v.getClass().getSimpleName());
    }

    private static TemplateDto toDto(FixMessageTemplate t) {
        TemplateDto d = new TemplateDto();
        d.id                = t.id();
        d.name              = t.name();
        d.description       = t.description().isEmpty() ? null : t.description();
        d.beginString       = t.beginString();
        d.msgType           = t.msgType();
        d.scope             = scopeToDto(t.scope());
        d.deletionProtected = t.isDeletionProtected() ? true : null; // omit false (NON_NULL)
        d.priority          = t.priority();
        d.fields            = new ArrayList<>();
        for (FieldSpec fs : t.fields()) {
            FieldSpecDto fsd = new FieldSpecDto();
            fsd.tag   = fs.tag();
            fsd.value = fieldValueToDto(fs.value());
            d.fields.add(fsd);
        }
        return d;
    }

    private static ScopeDto scopeToDto(TemplateScope s) {
        ScopeDto d = new ScopeDto();
        if (s instanceof TemplateScope.Session sess) {
            d.type      = "session";
            d.sessionId = sess.sessionId();
        } else {
            d.type = "global";
        }
        return d;
    }

    private static FieldValueDto fieldValueToDto(FieldValue v) {
        if (v instanceof FieldValue.Literal l) {
            LiteralDto d = new LiteralDto();
            d.value = l.value();
            return d;
        }
        if (v instanceof FieldValue.UserInput u) {
            UserInputDto d = new UserInputDto();
            d.name         = u.name();
            d.defaultValue = u.defaultValue();
            return d;
        }
        if (v instanceof FieldValue.Enumeration e) {
            EnumerationDto d   = new EnumerationDto();
            d.name             = e.name();
            d.options          = new ArrayList<>(e.options());
            d.defaultOption    = e.defaultOption();
            return d;
        }
        if (v instanceof FieldValue.Placeholder p) {
            PlaceholderDto d   = new PlaceholderDto();
            d.placeholderType  = p.type();
            return d;
        }
        if (v instanceof FieldValue.Derived dr) {
            DerivedDto d    = new DerivedDto();
            d.sourceTag     = dr.sourceTag();
            d.mappingName   = dr.mappingName();
            return d;
        }
        throw new IllegalArgumentException("Unknown FieldValue type: " + v.getClass().getSimpleName());
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    static class TemplateListDto {
        public List<TemplateDto> templates;
    }

    static class TemplateDto {
        public String           id;
        public String           name;
        public String           description;
        public String           beginString;
        public String           msgType;
        public ScopeDto         scope;
        public Boolean          deletionProtected;
        public int              priority;
        public List<FieldSpecDto> fields;
    }

    static class ScopeDto {
        public String type;
        public String sessionId;
    }

    static class FieldSpecDto {
        public int          tag;
        public FieldValueDto value;
    }

    /**
     * Abstract base for field-value DTOs. Jackson uses the {@code type}
     * property as a discriminator to select the correct subclass during
     * deserialization.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = LiteralDto.class,     name = "literal"),
        @JsonSubTypes.Type(value = UserInputDto.class,   name = "userInput"),
        @JsonSubTypes.Type(value = EnumerationDto.class, name = "enumeration"),
        @JsonSubTypes.Type(value = PlaceholderDto.class, name = "placeholder"),
        @JsonSubTypes.Type(value = DerivedDto.class,     name = "derived"),
    })
    abstract static class FieldValueDto {}

    static class LiteralDto extends FieldValueDto {
        public String value;
    }

    static class UserInputDto extends FieldValueDto {
        public String name;
        public String defaultValue;
    }

    static class EnumerationDto extends FieldValueDto {
        public String       name;
        public List<String> options;
        public String       defaultOption;
    }

    static class PlaceholderDto extends FieldValueDto {
        public PlaceholderType placeholderType;
    }

    static class DerivedDto extends FieldValueDto {
        public int    sourceTag;
        public String mappingName;
    }
}
