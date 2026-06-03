package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.template.FieldSpec;
import com.npsoftdev.fixsimulator.template.FieldValue;
import com.npsoftdev.fixsimulator.template.FixMessageTemplate;
import com.npsoftdev.fixsimulator.template.PlaceholderType;
import com.npsoftdev.fixsimulator.template.TemplateScope;
import com.npsoftdev.fixsimulator.template.TemplateService;
import org.apache.wicket.Application;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxCallListener;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Administration page for managing FIX Message Templates.
 *
 * <p>Supports full CRUD for {@link FixMessageTemplate} entries, including the
 * five field-value types (Literal, UserInput, Enumeration, Placeholder, Derived)
 * and template scope (Global / Session).
 *
 * <p>Deletion-protected templates (e.g. the built-in default) show the delete
 * button as disabled and display a tooltip explaining why.
 */
public class FixMessageTemplatesPage extends BasePage {

    private static final List<String> VALUE_TYPES =
            List.of("Literal", "UserInput", "Enumeration", "Placeholder", "Derived");
    private static final List<String> PLACEHOLDER_TYPES =
            Arrays.stream(PlaceholderType.values()).map(Enum::name).toList();

    public FixMessageTemplatesPage() {
        super();

        TemplateFormModel formModel = new TemplateFormModel();
        Model<String> editingId = Model.of((String) null);

        // ── Templates table ───────────────────────────────────────────────────

        WebMarkupContainer tableBody = new WebMarkupContainer("tableBody");
        tableBody.setOutputMarkupId(true);

        LoadableDetachableModel<List<FixMessageTemplate>> templatesModel =
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<FixMessageTemplate> load() {
                        TemplateService ts = templateSvc();
                        if (ts == null) return Collections.emptyList();
                        return ts.findAll();
                    }
                };

        tableBody.add(new WebMarkupContainer("emptyRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(templatesModel.getObject().isEmpty());
            }
        });

        // ── Fields container for modal ────────────────────────────────────────
        WebMarkupContainer fieldsContainer = new WebMarkupContainer("fieldsContainer");
        fieldsContainer.setOutputMarkupId(true);

        // ── Modal form ────────────────────────────────────────────────────────
        Form<TemplateFormModel> modalForm = new Form<>("modalForm",
                new CompoundPropertyModel<>(formModel));
        modalForm.setOutputMarkupId(true);

        FeedbackPanel modalFeedback = new FeedbackPanel("modalFeedback");
        modalFeedback.setOutputMarkupId(true);
        modalForm.add(modalFeedback);

        modalForm.add(new Label("modalTitle",
                (IModel<String>) () -> editingId.getObject() == null
                        ? "Create FIX Message Template" : "Edit FIX Message Template"));

        modalForm.add(new Label("idDisplay", (IModel<String>) () -> formModel.id));
        modalForm.add(new TextField<String>("name"));
        modalForm.add(new TextField<String>("description"));
        modalForm.add(new TextField<String>("beginString"));
        modalForm.add(new TextField<String>("msgType"));
        modalForm.add(new DropDownChoice<>("scopeType", List.of("Global", "Session")));
        modalForm.add(new TextField<String>("scopeSessionId"));

        // ── Empty fields row ──────────────────────────────────────────────────
        fieldsContainer.add(new WebMarkupContainer("emptyFieldsRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(formModel.fields.isEmpty());
            }
        });

        // ── Field rows ListView ───────────────────────────────────────────────
        fieldsContainer.add(new ListView<FieldFormRow>("fieldRows",
                new PropertyModel<>(formModel, "fields")) {
            @Override
            protected void populateItem(ListItem<FieldFormRow> item) {
                FieldFormRow row = item.getModelObject();

                item.add(new NumberTextField<>("tag",
                        new PropertyModel<>(row, "tag"), Integer.class));

                item.add(new DropDownChoice<>("valueType",
                        new PropertyModel<>(row, "valueType"), VALUE_TYPES));

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
                removeBtn.setDefaultFormProcessing(true);
                item.add(removeBtn);
            }
        });

        modalForm.add(fieldsContainer);

        // ── Add field button ──────────────────────────────────────────────────
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
        addFieldBtn.setDefaultFormProcessing(true);
        modalForm.add(addFieldBtn);

        // ── Save button ───────────────────────────────────────────────────────
        modalForm.add(new AjaxButton("saveBtn", modalForm) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                if (formModel.name == null || formModel.name.isBlank()) {
                    error("Name is required.");
                    target.add(modalFeedback);
                    return;
                }
                if (formModel.msgType == null || formModel.msgType.isBlank()) {
                    error("MsgType is required.");
                    target.add(modalFeedback);
                    return;
                }
                if (formModel.beginString == null || formModel.beginString.isBlank()) {
                    error("BeginString is required.");
                    target.add(modalFeedback);
                    return;
                }

                TemplateService ts = templateSvc();
                if (ts == null) {
                    error("Template service not available.");
                    target.add(modalFeedback);
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
                        .scope(scope);

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

                ts.save(builder.build());
                templatesModel.detach();

                target.appendJavaScript(
                        "bootstrap.Modal.getInstance(document.getElementById('templateModal')).hide();");
                target.add(tableBody);
                formModel.reset();
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(modalFeedback);
            }
        });

        // ── Template rows ─────────────────────────────────────────────────────
        tableBody.add(new ListView<FixMessageTemplate>("templateRows", templatesModel) {
            @Override
            protected void populateItem(ListItem<FixMessageTemplate> item) {
                FixMessageTemplate t = item.getModelObject();

                item.add(new Label("tName", t.name()));
                item.add(new Label("tMsgType", t.msgType()));

                Label scopeLabel = new Label("tScope", t.scope().toString());
                scopeLabel.add(AttributeModifier.replace("class",
                        t.scope() instanceof TemplateScope.Global
                                ? "badge bg-secondary" : "badge bg-info text-dark"));
                item.add(scopeLabel);

                item.add(new Label("tFieldCount", String.valueOf(t.fields().size())));
                item.add(new Label("tDescription", t.description()));

                // Protected badge — shown only for deletion-protected templates
                WebMarkupContainer protectedBadge = new WebMarkupContainer("protectedBadge");
                protectedBadge.setVisible(t.isDeletionProtected());
                item.add(protectedBadge);

                // Edit button
                item.add(new AjaxLink<Void>("editBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        formModel.loadFrom(t);
                        editingId.setObject(t.id());
                        target.add(modalForm);
                        target.appendJavaScript(
                                "new bootstrap.Modal(document.getElementById('templateModal')).show();" +
                                "setTimeout(function(){ initFieldInputs(); }, 100);");
                    }
                });

                // Delete button — disabled for protected templates
                AjaxLink<Void> deleteBtn = new AjaxLink<>("deleteBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        TemplateService ts = templateSvc();
                        if (ts == null) return;
                        try {
                            ts.delete(t.id());
                        } catch (IllegalStateException ex) {
                            // Protection guard fired — should not happen since the button
                            // is disabled in the UI, but guard defensively.
                            return;
                        }
                        templatesModel.detach();
                        target.add(tableBody);
                    }

                    @Override
                    protected void updateAjaxAttributes(AjaxRequestAttributes attrs) {
                        super.updateAjaxAttributes(attrs);
                        if (!t.isDeletionProtected()) {
                            String safe = t.name().replace("'", "\\'");
                            attrs.getAjaxCallListeners().add(new AjaxCallListener()
                                    .onPrecondition("return confirm('Delete template \\'" + safe + "\\'?');"));
                        } else {
                            // Built-in: block the click entirely
                            attrs.getAjaxCallListeners().add(new AjaxCallListener()
                                    .onPrecondition("return false;"));
                        }
                    }
                };
                if (t.isDeletionProtected()) {
                    deleteBtn.add(AttributeModifier.replace("class",
                            "btn btn-sm btn-outline-secondary py-0 px-2 disabled"));
                    deleteBtn.add(AttributeModifier.replace("title",
                            "Built-in template — cannot be deleted"));
                }
                item.add(deleteBtn);
            }
        });

        add(tableBody);

        // Create button
        add(new AjaxLink<Void>("createBtn") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                formModel.reset();
                editingId.setObject(null);
                target.add(modalForm);
                target.appendJavaScript(
                        "new bootstrap.Modal(document.getElementById('templateModal')).show();" +
                        "setTimeout(function(){ initFieldInputs(); }, 100);");
            }
        });

        add(modalForm);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static TemplateService templateSvc() {
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
        String scopeType = "Global";
        String scopeSessionId = "";
        List<FieldFormRow> fields = new ArrayList<>();

        void reset() {
            id = UUID.randomUUID().toString();
            name = "";
            description = "";
            beginString = "FIX.4.4";
            msgType = "";
            scopeType = "Global";
            scopeSessionId = "";
            fields = new ArrayList<>();
        }

        void loadFrom(FixMessageTemplate t) {
            id = t.id();
            name = t.name();
            description = t.description();
            beginString = t.beginString();
            msgType = t.msgType();
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

        // PropertyModel getters/setters
        public String getId()                 { return id; }
        public void setId(String v)           { id = v; }
        public String getName()               { return name; }
        public void setName(String v)         { name = v; }
        public String getDescription()        { return description; }
        public void setDescription(String v)  { description = v; }
        public String getBeginString()        { return beginString; }
        public void setBeginString(String v)  { beginString = v; }
        public String getMsgType()            { return msgType; }
        public void setMsgType(String v)      { msgType = v; }
        public String getScopeType()          { return scopeType; }
        public void setScopeType(String v)    { scopeType = v; }
        public String getScopeSessionId()     { return scopeSessionId; }
        public void setScopeSessionId(String v){ scopeSessionId = v; }
        public List<FieldFormRow> getFields() { return fields; }
        public void setFields(List<FieldFormRow> v){ fields = v; }
    }

    static class FieldFormRow implements Serializable {
        private static final long serialVersionUID = 1L;

        int tag = 0;
        String valueType = "Literal";
        // Literal
        String literalValue = "";
        // UserInput
        String uiName = "";
        String uiDefault = "";
        // Enumeration
        String enumName = "";
        String enumOptions = "";  // comma-separated
        String enumDefault = "";
        // Placeholder
        String placeholderType = PlaceholderType.ORDER_ID.name();
        // Derived
        int derivedSourceTag = 0;
        String derivedMappingName = "";

        public int getTag()                      { return tag; }
        public void setTag(int v)                { tag = v; }
        public String getValueType()             { return valueType; }
        public void setValueType(String v)       { valueType = v; }
        public String getLiteralValue()          { return literalValue; }
        public void setLiteralValue(String v)    { literalValue = v; }
        public String getUiName()                { return uiName; }
        public void setUiName(String v)          { uiName = v; }
        public String getUiDefault()             { return uiDefault; }
        public void setUiDefault(String v)       { uiDefault = v; }
        public String getEnumName()              { return enumName; }
        public void setEnumName(String v)        { enumName = v; }
        public String getEnumOptions()           { return enumOptions; }
        public void setEnumOptions(String v)     { enumOptions = v; }
        public String getEnumDefault()           { return enumDefault; }
        public void setEnumDefault(String v)     { enumDefault = v; }
        public String getPlaceholderType()       { return placeholderType; }
        public void setPlaceholderType(String v) { placeholderType = v; }
        public int getDerivedSourceTag()         { return derivedSourceTag; }
        public void setDerivedSourceTag(int v)   { derivedSourceTag = v; }
        public String getDerivedMappingName()    { return derivedMappingName; }
        public void setDerivedMappingName(String v){ derivedMappingName = v; }
    }
}
