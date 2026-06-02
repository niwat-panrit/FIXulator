package com.npsoftdev.fixsimulator.template;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A reusable FIX message template.
 *
 * <p>A template carries enough metadata to construct a {@link quickfix.Message}
 * for a given session: the FIX dialect ({@link #beginString()}), the message
 * type ({@link #msgType()} — e.g. {@code "D"}, {@code "G"}, {@code "F"}), an
 * ordered list of {@link FieldSpec} entries describing how each body / header
 * tag is populated, and a {@link TemplateScope} controlling visibility.</p>
 *
 * <p>Templates are immutable. Use {@link #toBuilder()} to derive a new instance
 * with modifications.</p>
 *
 * <p>Construction is via {@link #builder()}.</p>
 */
public final class FixMessageTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String description;
    private final String beginString;
    private final String msgType;
    private final TemplateScope scope;
    private final List<FieldSpec> fields;

    private FixMessageTemplate(Builder b) {
        this.id          = Objects.requireNonNull(b.id, "id");
        this.name        = Objects.requireNonNull(b.name, "name");
        this.description = b.description == null ? "" : b.description;
        this.beginString = Objects.requireNonNull(b.beginString, "beginString");
        this.msgType     = Objects.requireNonNull(b.msgType, "msgType");
        this.scope       = Objects.requireNonNull(b.scope, "scope");
        this.fields      = List.copyOf(b.fields);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String id()              { return id; }
    public String name()            { return name; }
    public String description()     { return description; }
    public String beginString()     { return beginString; }
    public String msgType()         { return msgType; }
    public TemplateScope scope()    { return scope; }

    /** Unmodifiable ordered list of field specs. */
    public List<FieldSpec> fields() { return fields; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        return new Builder()
                .id(id).name(name).description(description)
                .beginString(beginString).msgType(msgType).scope(scope)
                .fields(fields);
    }

    public static final class Builder {
        private String id;
        private String name;
        private String description;
        private String beginString = "FIX.4.4";
        private String msgType;
        private TemplateScope scope = TemplateScope.global();
        private final List<FieldSpec> fields = new ArrayList<>();

        public Builder id(String id)                     { this.id = id; return this; }
        public Builder name(String name)                 { this.name = name; return this; }
        public Builder description(String description)   { this.description = description; return this; }
        public Builder beginString(String beginString)   { this.beginString = beginString; return this; }
        public Builder msgType(String msgType)           { this.msgType = msgType; return this; }
        public Builder scope(TemplateScope scope)        { this.scope = scope; return this; }

        public Builder addField(FieldSpec spec) {
            fields.add(Objects.requireNonNull(spec, "spec"));
            return this;
        }

        public Builder fields(List<FieldSpec> specs) {
            this.fields.clear();
            this.fields.addAll(Objects.requireNonNull(specs, "specs"));
            return this;
        }

        public FixMessageTemplate build() { return new FixMessageTemplate(this); }
    }

    // ── Object ────────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FixMessageTemplate that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "FixMessageTemplate{id=" + id + ", name=" + name + ", msgType=" + msgType
                + ", scope=" + scope + ", fields=" + fields.size() + "}";
    }

    /** Defensive: prevent external mutation of the fields list after deserialisation. */
    private Object readResolve() {
        return new Builder()
                .id(id).name(name).description(description)
                .beginString(beginString).msgType(msgType).scope(scope)
                .fields(Collections.unmodifiableList(new ArrayList<>(fields)))
                .build();
    }
}
