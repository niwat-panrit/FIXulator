package com.npsoftdev.fixsimulator.plugins.template.ui;

import com.npsoftdev.fixsimulator.core.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.plugins.template.api.DynamicValueDefinition;
import com.npsoftdev.fixsimulator.plugins.template.api.DynamicValueRegistry;
import org.apache.wicket.Application;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxCallListener;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import com.npsoftdev.fixsimulator.core.ui.BasePage;
import com.npsoftdev.fixsimulator.core.ui.JsEscape;

public class DynamicValuesPage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(DynamicValuesPage.class);

    public DynamicValuesPage() {
        super();

        // ── Built-ins table (read-only) ───────────────────────────────────────
        LoadableDetachableModel<List<DynamicValueDefinition>> builtInsModel =
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<DynamicValueDefinition> load() {
                        DynamicValueRegistry reg = registry();
                        if (reg == null) return Collections.emptyList();
                        return reg.listAll().stream().filter(DynamicValueDefinition::builtIn).toList();
                    }
                };

        WebMarkupContainer builtInBody = new WebMarkupContainer("builtInBody");
        builtInBody.setOutputMarkupId(true);

        builtInBody.add(new ListView<DynamicValueDefinition>("builtInRows", builtInsModel) {
            @Override
            protected void populateItem(ListItem<DynamicValueDefinition> item) {
                DynamicValueDefinition def = item.getModelObject();
                item.add(new Label("biUsage", def.exampleUsage()));
                item.add(new Label("biName", def.name()));
                item.add(new Label("biDescription", def.description()));
            }
        });

        add(builtInBody);

        // ── Custom constants table + CRUD ─────────────────────────────────────
        LoadableDetachableModel<List<DynamicValueDefinition>> customModel =
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<DynamicValueDefinition> load() {
                        DynamicValueRegistry reg = registry();
                        if (reg == null) return Collections.emptyList();
                        return reg.listAll().stream().filter(d -> !d.builtIn()).toList();
                    }
                };

        WebMarkupContainer customBody = new WebMarkupContainer("customBody");
        customBody.setOutputMarkupId(true);

        customBody.add(new WebMarkupContainer("emptyCustomRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(customModel.getObject().isEmpty());
            }
        });

        customBody.add(new ListView<DynamicValueDefinition>("customRows", customModel) {
            @Override
            protected void populateItem(ListItem<DynamicValueDefinition> item) {
                DynamicValueDefinition def = item.getModelObject();
                item.add(new Label("cvUsage", "$("+def.name()+")"));
                item.add(new Label("cvName", def.name()));
                item.add(new Label("cvDescription", def.description()));
                item.add(new Label("cvValue", def.constantValue()));

                item.add(new AjaxLink<Void>("deleteCustomBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        DynamicValueRegistry reg = registry();
                        if (reg != null) {
                            reg.removeCustom(def.name());
                            log.info("Dynamic Value deleted: name='{}'", def.name());
                        }
                        customModel.detach();
                        target.add(customBody);
                    }

                    @Override
                    protected void updateAjaxAttributes(AjaxRequestAttributes attrs) {
                        super.updateAjaxAttributes(attrs);
                        String safe = JsEscape.forSingleQuotedLiteral(def.name());
                        attrs.getAjaxCallListeners().add(new AjaxCallListener()
                                .onPrecondition("return confirm('Remove custom value \\'" + safe + "\\'?');"));
                    }
                });
            }
        });

        add(customBody);

        // ── Add custom constant form ───────────────────────────────────────────
        CustomFormModel formModel = new CustomFormModel();
        Form<CustomFormModel> addForm = new Form<>("addForm",
                new CompoundPropertyModel<>(formModel));
        addForm.setOutputMarkupId(true);

        FeedbackPanel feedback = new FeedbackPanel("feedback");
        feedback.setOutputMarkupId(true);
        addForm.add(feedback);

        addForm.add(new TextField<String>("name"));
        addForm.add(new TextField<String>("description"));
        addForm.add(new TextArea<String>("constantValue"));

        addForm.add(new AjaxButton("addBtn", addForm) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                if (formModel.name == null || formModel.name.isBlank()) {
                    error("Token name is required.");
                    target.add(feedback);
                    return;
                }
                String tokenName = formModel.name.trim().toLowerCase()
                        .replaceAll("[^a-z0-9_]", "_");
                if (formModel.constantValue == null || formModel.constantValue.isBlank()) {
                    error("Value is required.");
                    target.add(feedback);
                    return;
                }
                DynamicValueRegistry reg = registry();
                if (reg == null) {
                    error("Dynamic value registry not available.");
                    target.add(feedback);
                    return;
                }
                try {
                    reg.define(DynamicValueDefinition.constant(
                            tokenName,
                            formModel.description != null ? formModel.description.trim() : "",
                            formModel.constantValue.trim()));
                    log.info("Dynamic Value created: name='{}'", tokenName);
                    customModel.detach();
                    formModel.reset();
                    target.add(customBody, addForm);
                } catch (IllegalArgumentException ex) {
                    error(ex.getMessage());
                    target.add(feedback);
                }
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(feedback);
            }
        });

        add(addForm);
    }

    private static DynamicValueRegistry registry() {
        return ((FixSimulatorApplication) Application.get()).getDynamicValueRegistry();
    }

    static class CustomFormModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String name = "";
        String description = "";
        String constantValue = "";

        void reset() { name = ""; description = ""; constantValue = ""; }

        public String getName()                  { return name; }
        public void setName(String v)            { name = v; }
        public String getDescription()           { return description; }
        public void setDescription(String v)     { description = v; }
        public String getConstantValue()         { return constantValue; }
        public void setConstantValue(String v)   { constantValue = v; }
    }
}
