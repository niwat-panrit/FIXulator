package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.service.ConnectionService;
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
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBoxMultipleChoice;
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

public class OrderTemplatesPage extends BasePage {

    private static final List<String> VALUE_TYPES =
            List.of("Literal", "UserInput", "Placeholder", "Derived");
    private static final List<String> PLACEHOLDER_TYPES =
            Arrays.stream(PlaceholderType.values()).map(Enum::name).toList();

    public OrderTemplatesPage() {
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

        // ── Fields container for modal (must be declared before the form so we
        // can reference it in Add/Remove button closures) ─────────────────────
        WebMarkupContainer fieldsContainer = new WebMarkupContainer("fieldsContainer");
        fieldsContainer.setOutputMarkupId(true);

        // ── Modal form ────────────────────────────────────────────────────────
        Form<TemplateFormModel> modalForm = new Form<>("modalForm",
                new CompoundPropertyModel<>(formModel));
        modalForm.setOutputMarkupId(true);

        FeedbackPanel modalFeedback = new FeedbackPanel("modalFeedback");
        modalFeedback.setOutputMarkupId(true);
        modalForm.add(modalFeedback);

        // Modal title changes between Create / Edit
        modalForm.add(new Label("modalTitle",
                (IModel<String>) () -> editingId.getObject() == null
                        ? "Create Template" : "Edit Template"));

        // Metadata fields (bound via CompoundPropertyModel → formModel fields)
        modalForm.add(new Label("idDisplay",
                (IModel<String>) () -> formModel.id));
        modalForm.add(new TextField<String>("name"));
        modalForm.add(new TextField<String>("description"));
        modalForm.add(new TextField<String>("beginString"));
        modalForm.add(new TextField<String>("msgType"));
        WebMarkupContainer sessionPickerContainer = new WebMarkupContainer("sessionPickerContainer") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible("Sessions".equals(formModel.scopeType));
            }
        };
        sessionPickerContainer.setOutputMarkupPlaceholderTag(true);

        CheckBoxMultipleChoice<String> sessionPicker = new CheckBoxMultipleChoice<>(
                "scopeSessionIds",
                new PropertyModel<>(formModel, "scopeSessionIds"),
                new LoadableDetachableModel<>() {
                    @Override protected List<String> load() { return availableSessionIds(); }
                });
        sessionPicker.setPrefix("<div class=\"form-check\">");
        sessionPicker.setSuffix("</div>");
        sessionPickerContainer.add(sessionPicker);
        modalForm.add(sessionPickerContainer);

        DropDownChoice<String> scopeTypeChoice = new DropDownChoice<>("scopeType",
                List.of("Global", "Sessions"));
        scopeTypeChoice.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                target.add(sessionPickerContainer);
            }
        });
        modalForm.add(scopeTypeChoice);

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

                // Tag number
                item.add(new NumberTextField<>("tag",
                        new PropertyModel<>(row, "tag"), Integer.class));

                // Value type selector
                item.add(new DropDownChoice<>("valueType",
                        new PropertyModel<>(row, "valueType"), VALUE_TYPES));

                // ── Literal ────────────────────────────────────────────────
                item.add(new TextField<>("literalValue",
                        new PropertyModel<>(row, "literalValue")));

                // ── UserInput ──────────────────────────────────────────────
                item.add(new TextField<>("uiName",
                        new PropertyModel<>(row, "uiName")));
                item.add(new TextField<>("uiDefault",
                        new PropertyModel<>(row, "uiDefault")));

                // ── Placeholder ────────────────────────────────────────────
                item.add(new DropDownChoice<>("placeholderType",
                        new PropertyModel<>(row, "placeholderType"), PLACEHOLDER_TYPES));

                // ── Derived ────────────────────────────────────────────────
                item.add(new NumberTextField<>("derivedSourceTag",
                        new PropertyModel<>(row, "derivedSourceTag"), Integer.class));
                item.add(new TextField<>("derivedMappingName",
                        new PropertyModel<>(row, "derivedMappingName")));

                // Remove row button
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

        // ── Save template button ──────────────────────────────────────────────
        modalForm.add(new AjaxButton("saveBtn", modalForm) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                // Manual validation (no Wicket validators on metadata fields to
                // avoid interference with Add/Remove buttons above)
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

                TemplateScope scope = "Sessions".equals(formModel.scopeType)
                        && formModel.scopeSessionIds != null
                        && !formModel.scopeSessionIds.isEmpty()
                        ? TemplateScope.sessions(formModel.scopeSessionIds)
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
                        default -> // Literal
                                FieldSpec.literal(row.tag,
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

                // Edit button — populate modal form and show it
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

                // Delete button — confirm then delete
                item.add(new AjaxLink<Void>("deleteBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        TemplateService ts = templateSvc();
                        if (ts != null) ts.delete(t.id());
                        templatesModel.detach();
                        target.add(tableBody);
                    }

                    @Override
                    protected void updateAjaxAttributes(AjaxRequestAttributes attrs) {
                        super.updateAjaxAttributes(attrs);
                        String safe = t.name().replace("'", "\\'");
                        attrs.getAjaxCallListeners().add(new AjaxCallListener()
                                .onPrecondition("return confirm('Delete template \\'" + safe + "\\'?');"));
                    }
                });
            }
        });

        add(tableBody);

        // Create button — resets form and opens modal
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

    private static TemplateService templateSvc() {
        return ((FixSimulatorApplication) Application.get()).getTemplateService();
    }

    private static List<String> availableSessionIds() {
        ConnectionService cs = ((FixSimulatorApplication) Application.get()).getConnectionService();
        if (cs == null) return List.of();
        List<String> ids = new ArrayList<>(cs.listSessionIds());
        Collections.sort(ids);
        return ids;
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
        List<String> scopeSessionIds = new ArrayList<>();
        List<FieldFormRow> fields = new ArrayList<>();

        void reset() {
            id = UUID.randomUUID().toString();
            name = "";
            description = "";
            beginString = "FIX.4.4";
            msgType = "";
            scopeType = "Global";
            scopeSessionIds = new ArrayList<>();
            fields = new ArrayList<>();
        }

        void loadFrom(FixMessageTemplate t) {
            id = t.id();
            name = t.name();
            description = t.description();
            beginString = t.beginString();
            msgType = t.msgType();
            if (t.scope() instanceof TemplateScope.Sessions s) {
                scopeType = "Sessions";
                scopeSessionIds = new ArrayList<>(s.sessionIds());
            } else {
                scopeType = "Global";
                scopeSessionIds = new ArrayList<>();
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
        // Placeholder
        String placeholderType = PlaceholderType.ORDER_ID.name();
        // Derived
        int derivedSourceTag = 0;
        String derivedMappingName = "";

        // PropertyModel needs getters for fields accessed via CompoundPropertyModel
        public int getTag()                    { return tag; }
        public void setTag(int v)              { tag = v; }
        public String getValueType()           { return valueType; }
        public void setValueType(String v)     { valueType = v; }
        public String getLiteralValue()        { return literalValue; }
        public void setLiteralValue(String v)  { literalValue = v; }
        public String getUiName()              { return uiName; }
        public void setUiName(String v)        { uiName = v; }
        public String getUiDefault()           { return uiDefault; }
        public void setUiDefault(String v)     { uiDefault = v; }
        public String getPlaceholderType()     { return placeholderType; }
        public void setPlaceholderType(String v){ placeholderType = v; }
        public int getDerivedSourceTag()       { return derivedSourceTag; }
        public void setDerivedSourceTag(int v) { derivedSourceTag = v; }
        public String getDerivedMappingName()  { return derivedMappingName; }
        public void setDerivedMappingName(String v){ derivedMappingName = v; }
    }
}
