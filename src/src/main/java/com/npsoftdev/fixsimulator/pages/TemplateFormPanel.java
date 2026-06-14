package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.template.FieldSpec;
import com.npsoftdev.fixsimulator.template.FieldValue;
import com.npsoftdev.fixsimulator.template.FixMessageTemplate;
import com.npsoftdev.fixsimulator.template.PlaceholderType;
import com.npsoftdev.fixsimulator.template.TemplateScope;
import com.npsoftdev.fixsimulator.template.TemplateService;
import org.apache.wicket.Application;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.resource.PackageResourceReference;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Reusable panel containing the full create/edit form for a {@link FixMessageTemplate}.
 *
 * <p>Handles both modes:
 * <ul>
 *   <li><b>Create</b> — pass {@code null} as {@code templateId}; the form starts blank.</li>
 *   <li><b>Edit</b>   — pass the existing template's ID; the form is pre-populated.</li>
 * </ul>
 *
 * <p>On save the user is redirected to {@link FixMessageTemplatesPage}.
 * On cancel the same redirect happens without saving.
 */
public class TemplateFormPanel extends Panel {

    private static final Logger log = LoggerFactory.getLogger(TemplateFormPanel.class);

    static final List<String> VALUE_TYPES =
            List.of("Literal", "UserInput", "Enumeration", "Placeholder", "Derived");
    static final List<String> PLACEHOLDER_TYPES =
            Arrays.stream(PlaceholderType.values()).map(Enum::name).toList();

    /** Renders String choices using the string itself as the HTML option value. */
    private static final IChoiceRenderer<String> STRING_VALUE_RENDERER = new IChoiceRenderer<>() {
        @Override public Object getDisplayValue(String o) { return o; }
        @Override public String getIdValue(String o, int index) { return o; }
        @Override public String getObject(String id, IModel<? extends List<? extends String>> choices) { return id; }
    };

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(JavaScriptHeaderItem.forReference(
                new PackageResourceReference(TemplateFormPanel.class, "TemplateFormPanel.js")));
    }

    /** Creates the panel in create mode (blank form) or edit mode (pre-populated from templateId). */
    public TemplateFormPanel(String id, String templateId) {
        this(id, buildModel(templateId, null));
    }

    /**
     * Creates the panel pre-populated by parsing {@code rawFixMessage}.
     * All fields default to {@code Literal}; the user can adjust types before saving.
     */
    public TemplateFormPanel(String id, String templateId, String rawFixMessage) {
        this(id, buildModel(templateId, rawFixMessage));
    }

    private TemplateFormPanel(String id, TemplateFormModel formModel) {
        super(id);
        // Capture whether this is an edit (template pre-loaded from storage) or a create
        final boolean isEdit = templateSvc() != null
                && templateSvc().findById(formModel.id).isPresent();

        Form<TemplateFormModel> form = new Form<>("form",
                new CompoundPropertyModel<>(formModel));
        form.setOutputMarkupId(true);

        FeedbackPanel feedback = new FeedbackPanel("feedback");
        feedback.setOutputMarkupId(true);
        form.add(feedback);

        // ── Metadata fields ───────────────────────────────────────────────────
        form.add(new Label("idDisplay", (IModel<String>) () -> formModel.id));
        form.add(new TextField<String>("name").setRequired(true));
        form.add(new TextField<String>("description"));
        form.add(new TextField<String>("beginString").setRequired(true));
        form.add(new TextField<String>("msgType").setRequired(true));
        form.add(new NumberTextField<>("priority", new PropertyModel<>(formModel, "priority"), Integer.class)
                .setMinimum(1).setRequired(true));
        form.add(new DropDownChoice<>("scopeType", List.of("Global", "Session")));
        form.add(new TextField<String>("scopeSessionId"));

        // ── Fields container ──────────────────────────────────────────────────
        WebMarkupContainer fieldsContainer = new WebMarkupContainer("fieldsContainer");
        fieldsContainer.setOutputMarkupId(true);

        fieldsContainer.add(new WebMarkupContainer("emptyFieldsRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(formModel.fields.isEmpty());
            }
        });

        fieldsContainer.add(new ListView<FieldFormRow>("fieldRows",
                new PropertyModel<>(formModel, "fields")) {
            @Override
            protected void populateItem(ListItem<FieldFormRow> item) {
                FieldFormRow row = item.getModelObject();

                item.add(new NumberTextField<>("tag",
                        new PropertyModel<>(row, "tag"), Integer.class));
                item.add(new DropDownChoice<>("valueType",
                        new PropertyModel<>(row, "valueType"), VALUE_TYPES,
                        STRING_VALUE_RENDERER));

                // Literal
                item.add(new TextField<>("literalValue",
                        new PropertyModel<>(row, "literalValue")));
                // UserInput
                item.add(new TextField<>("uiName",
                        new PropertyModel<>(row, "uiName")));
                item.add(new TextField<>("uiDefault",
                        new PropertyModel<>(row, "uiDefault")));
                // Enumeration
                item.add(new TextField<>("enumName",
                        new PropertyModel<>(row, "enumName")));
                item.add(new TextField<>("enumOptions",
                        new PropertyModel<>(row, "enumOptions")));
                item.add(new TextField<>("enumDefault",
                        new PropertyModel<>(row, "enumDefault")));
                // Placeholder
                item.add(new DropDownChoice<>("placeholderType",
                        new PropertyModel<>(row, "placeholderType"), PLACEHOLDER_TYPES));
                // Derived
                item.add(new NumberTextField<>("derivedSourceTag",
                        new PropertyModel<>(row, "derivedSourceTag"), Integer.class));
                item.add(new TextField<>("derivedMappingName",
                        new PropertyModel<>(row, "derivedMappingName")));

                // Remove row button — skips form validation intentionally
                AjaxButton removeBtn = new AjaxButton("removeFieldBtn") {
                    @Override
                    protected void onSubmit(AjaxRequestTarget target) {
                        formModel.fields.remove(row);
                        target.add(fieldsContainer);
                        target.appendJavaScript("initFieldInputs()");
                    }
                    @Override
                    protected void onError(AjaxRequestTarget target) {
                        formModel.fields.remove(row);
                        target.add(fieldsContainer);
                        target.appendJavaScript("initFieldInputs()");
                    }
                };
                removeBtn.setDefaultFormProcessing(false);
                item.add(removeBtn);
            }
        });

        form.add(fieldsContainer);

        // ── Add field button — skips validation ───────────────────────────────
        AjaxButton addFieldBtn = new AjaxButton("addFieldBtn") {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                formModel.fields.add(new FieldFormRow());
                target.add(fieldsContainer);
                target.appendJavaScript("initFieldInputs()");
            }
            @Override
            protected void onError(AjaxRequestTarget target) {
                formModel.fields.add(new FieldFormRow());
                target.add(fieldsContainer);
                target.appendJavaScript("initFieldInputs()");
            }
        };
        addFieldBtn.setDefaultFormProcessing(false);
        form.add(addFieldBtn);

        // ── Save button ───────────────────────────────────────────────────────
        form.add(new Button("saveBtn") {
            @Override
            public void onSubmit() {
                TemplateService ts = templateSvc();
                if (ts == null) {
                    error("Template service not available.");
                    return;
                }

                TemplateScope scope = "Session".equals(formModel.scopeType)
                        && formModel.scopeSessionId != null
                        && !formModel.scopeSessionId.isBlank()
                        ? TemplateScope.session(formModel.scopeSessionId)
                        : TemplateScope.global();

                FixMessageTemplate.Builder builder = FixMessageTemplate.builder()
                        .id(formModel.id)
                        .name(formModel.name.trim())
                        .description(formModel.description != null ? formModel.description.trim() : "")
                        .beginString(formModel.beginString.trim())
                        .msgType(formModel.msgType.trim())
                        .scope(scope)
                        .priority(formModel.priority > 0 ? formModel.priority : 100);

                for (FieldFormRow row : formModel.fields) {
                    if (row.tag <= 0) continue;
                    FieldSpec spec = switch (row.valueType) {
                        case "UserInput" -> {
                            String name = row.uiName != null ? row.uiName.trim() : "";
                            if (name.isBlank()) yield FieldSpec.literal(row.tag, "");
                            yield row.uiDefault != null && !row.uiDefault.isBlank()
                                    ? FieldSpec.userInput(row.tag, name, row.uiDefault.trim())
                                    : FieldSpec.userInput(row.tag, name);
                        }
                        case "Enumeration" -> {
                            String eName = row.enumName != null ? row.enumName.trim() : "";
                            if (eName.isBlank()) yield FieldSpec.literal(row.tag, "");
                            List<String> opts = parseOptions(row.enumOptions);
                            if (opts.isEmpty()) yield FieldSpec.literal(row.tag, "");
                            String def = row.enumDefault != null && !row.enumDefault.isBlank()
                                    ? row.enumDefault.trim() : null;
                            yield FieldSpec.enumeration(row.tag, eName, opts, def);
                        }
                        case "Placeholder" -> {
                            try {
                                yield FieldSpec.placeholder(row.tag,
                                        PlaceholderType.valueOf(row.placeholderType));
                            } catch (Exception e) {
                                yield FieldSpec.placeholder(row.tag, PlaceholderType.ORDER_ID);
                            }
                        }
                        case "Derived" -> {
                            String mapping = row.derivedMappingName != null
                                    ? row.derivedMappingName.trim() : "";
                            if (mapping.isBlank() || row.derivedSourceTag <= 0)
                                yield FieldSpec.literal(row.tag, "");
                            yield FieldSpec.derived(row.tag, row.derivedSourceTag, mapping);
                        }
                        default -> FieldSpec.literal(row.tag,
                                row.literalValue != null ? row.literalValue : "");
                    };
                    builder.addField(spec);
                }

                FixMessageTemplate saved = builder.build();
                ts.save(saved);
                log.info("FIX Message Template {}: id={} name='{}' msgType={} scope={} priority={} fields={}",
                        isEdit ? "updated" : "created",
                        saved.id(), saved.name(), saved.msgType(),
                        saved.scope(), saved.priority(), saved.fields().size());
                setResponsePage(FixMessageTemplatesPage.class);
            }
        });

        // ── Cancel link ───────────────────────────────────────────────────────
        form.add(new BookmarkablePageLink<>("cancelBtn", FixMessageTemplatesPage.class));

        add(form);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static TemplateFormModel buildModel(String templateId, String rawFixMessage) {
        TemplateFormModel model = new TemplateFormModel();
        if (templateId != null) {
            TemplateService ts = templateSvc();
            if (ts != null) ts.findById(templateId).ifPresent(model::loadFrom);
        } else if (rawFixMessage != null && !rawFixMessage.isBlank()) {
            parseFixMessage(rawFixMessage, model);
        }
        return model;
    }

    /**
     * Parses a raw FIX message string into a {@link TemplateFormModel}.
     *
     * <p>Accepts common display formats: fields delimited by SOH (0x01), pipe {@code |},
     * or newlines. Tag 8 (BeginString) and tag 35 (MsgType) populate the template
     * metadata fields. Tags 9 (BodyLength) and 10 (CheckSum) are skipped because they
     * are auto-calculated. All remaining tags become {@code Literal} field rows.
     */
    static void parseFixMessage(String raw, TemplateFormModel model) {
        if (raw == null || raw.isBlank()) return;

        // Normalize all common delimiters to a single pipe
        String normalized = raw
                .replace('\u0001', '|')          // SOH → pipe
                .replaceAll("[\r\n]+", "|")       // newlines → pipe
                .replaceAll("[ \t]*\\|[ \t]*", "|") // strip surrounding whitespace
                .replaceAll("\\|{2,}", "|")        // collapse repeated pipes
                .trim();
        if (normalized.startsWith("|")) normalized = normalized.substring(1);
        if (normalized.endsWith("|"))   normalized = normalized.substring(0, normalized.length() - 1);

        List<FieldFormRow> fields = new ArrayList<>();
        for (String part : normalized.split("\\|")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            if (eq <= 0) continue;

            String tagStr = part.substring(0, eq).trim();
            String value  = part.substring(eq + 1);
            int tag;
            try {
                tag = Integer.parseInt(tagStr);
            } catch (NumberFormatException e) {
                continue;
            }

            switch (tag) {
                case 8  -> model.beginString = value;   // BeginString
                case 35 -> model.msgType     = value;   // MsgType
                case 9, 10 -> { /* BodyLength / CheckSum — skip */ }
                default -> {
                    FieldFormRow row = new FieldFormRow();
                    row.tag          = tag;
                    row.valueType    = "Literal";
                    row.literalValue = value;
                    fields.add(row);
                }
            }
        }

        fields.sort(Comparator.comparingInt(r -> r.tag));
        model.fields = fields;
    }

    static TemplateService templateSvc() {
        return ((FixSimulatorApplication) Application.get()).getTemplateService();
    }

    /** Parses a comma-or-newline-separated string into a trimmed, non-empty list. */
    static List<String> parseOptions(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[,\n]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // ── Form models ───────────────────────────────────────────────────────────

    static class TemplateFormModel implements Serializable {
        private static final long serialVersionUID = 1L;

        String id = UUID.randomUUID().toString();
        String name = "";
        String description = "";
        String beginString = "FIX.4.4";
        String msgType = "";
        int priority = 100;
        String scopeType = "Global";
        String scopeSessionId = "";
        List<FieldFormRow> fields = new ArrayList<>();

        void loadFrom(FixMessageTemplate t) {
            id = t.id();
            name = t.name();
            description = t.description();
            beginString = t.beginString();
            msgType = t.msgType();
            priority = t.priority();
            if (t.scope() instanceof TemplateScope.Session s) {
                scopeType = "Session";
                scopeSessionId = s.sessionId();
            } else {
                scopeType = "Global";
                scopeSessionId = "";
            }
            fields = new ArrayList<>();
            for (FieldSpec spec : t.fields()) {
                FieldFormRow row = new FieldFormRow();
                row.tag = spec.tag();
                FieldValue fv = spec.value();
                if (fv instanceof FieldValue.Literal lit) {
                    row.valueType = "Literal";
                    row.literalValue = lit.value();
                } else if (fv instanceof FieldValue.UserInput ui) {
                    row.valueType = "UserInput";
                    row.uiName = ui.name();
                    row.uiDefault = ui.defaultValue() != null ? ui.defaultValue() : "";
                } else if (fv instanceof FieldValue.Enumeration en) {
                    row.valueType = "Enumeration";
                    row.enumName = en.name();
                    row.enumOptions = String.join(", ", en.options());
                    row.enumDefault = en.defaultOption() != null ? en.defaultOption() : "";
                } else if (fv instanceof FieldValue.Placeholder ph) {
                    row.valueType = "Placeholder";
                    row.placeholderType = ph.type().name();
                } else if (fv instanceof FieldValue.Derived d) {
                    row.valueType = "Derived";
                    row.derivedSourceTag = d.sourceTag();
                    row.derivedMappingName = d.mappingName();
                } else {
                    row.valueType = "Literal";
                }
                fields.add(row);
            }
        }

        public String getId()                  { return id; }
        public void setId(String v)            { id = v; }
        public String getName()                { return name; }
        public void setName(String v)          { name = v; }
        public String getDescription()         { return description; }
        public void setDescription(String v)   { description = v; }
        public String getBeginString()         { return beginString; }
        public void setBeginString(String v)   { beginString = v; }
        public String getMsgType()             { return msgType; }
        public void setMsgType(String v)       { msgType = v; }
        public int getPriority()               { return priority; }
        public void setPriority(int v)         { priority = v; }
        public String getScopeType()           { return scopeType; }
        public void setScopeType(String v)     { scopeType = v; }
        public String getScopeSessionId()      { return scopeSessionId; }
        public void setScopeSessionId(String v){ scopeSessionId = v; }
        public List<FieldFormRow> getFields()  { return fields; }
        public void setFields(List<FieldFormRow> v){ fields = v; }
    }

    static class FieldFormRow implements Serializable {
        private static final long serialVersionUID = 1L;

        int tag = 0;
        String valueType = "Literal";
        String literalValue = "";
        String uiName = "";
        String uiDefault = "";
        String enumName = "";
        String enumOptions = "";
        String enumDefault = "";
        String placeholderType = PlaceholderType.ORDER_ID.name();
        int derivedSourceTag = 0;
        String derivedMappingName = "";

        public int getTag()                       { return tag; }
        public void setTag(int v)                 { tag = v; }
        public String getValueType()              { return valueType; }
        public void setValueType(String v)        { valueType = v; }
        public String getLiteralValue()           { return literalValue; }
        public void setLiteralValue(String v)     { literalValue = v; }
        public String getUiName()                 { return uiName; }
        public void setUiName(String v)           { uiName = v; }
        public String getUiDefault()              { return uiDefault; }
        public void setUiDefault(String v)        { uiDefault = v; }
        public String getEnumName()               { return enumName; }
        public void setEnumName(String v)         { enumName = v; }
        public String getEnumOptions()            { return enumOptions; }
        public void setEnumOptions(String v)      { enumOptions = v; }
        public String getEnumDefault()            { return enumDefault; }
        public void setEnumDefault(String v)      { enumDefault = v; }
        public String getPlaceholderType()        { return placeholderType; }
        public void setPlaceholderType(String v)  { placeholderType = v; }
        public int getDerivedSourceTag()          { return derivedSourceTag; }
        public void setDerivedSourceTag(int v)    { derivedSourceTag = v; }
        public String getDerivedMappingName()     { return derivedMappingName; }
        public void setDerivedMappingName(String v){ derivedMappingName = v; }
    }
}
