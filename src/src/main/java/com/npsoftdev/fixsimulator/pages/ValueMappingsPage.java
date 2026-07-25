package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.template.ValueMappingService;
import org.apache.wicket.Application;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxCallListener;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ValueMappingsPage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(ValueMappingsPage.class);

    public ValueMappingsPage() {
        super();

        // ── Shared state ──────────────────────────────────────────────────────
        Model<String> activeName = Model.of("symbol-to-isin");

        LoadableDetachableModel<List<String>> namesModel = new LoadableDetachableModel<>() {
            @Override
            protected List<String> load() {
                ValueMappingService svc = svc();
                if (svc == null) return Collections.emptyList();
                return svc.mappingNames().stream().sorted().toList();
            }
        };

        LoadableDetachableModel<List<MappingEntry>> entriesModel = new LoadableDetachableModel<>() {
            @Override
            protected List<MappingEntry> load() {
                ValueMappingService svc = svc();
                String name = activeName.getObject();
                if (svc == null || name == null) return Collections.emptyList();
                return svc.entries(name).entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> new MappingEntry(e.getKey(), e.getValue()))
                        .toList();
            }
        };

        // ── Containers ────────────────────────────────────────────────────────
        WebMarkupContainer mappingListContainer = new WebMarkupContainer("mappingListContainer");
        mappingListContainer.setOutputMarkupId(true);

        WebMarkupContainer entriesContainer = new WebMarkupContainer("entriesContainer");
        entriesContainer.setOutputMarkupId(true);

        // Entry form (declared early so row-level AJAX callbacks can target it)
        EntryFormModel entryFormModel = new EntryFormModel();
        Model<String> editingKey = Model.of((String) null);

        Form<EntryFormModel> entryForm = new Form<>("entryForm",
                new CompoundPropertyModel<>(entryFormModel));
        entryForm.setOutputMarkupId(true);

        // ── Mapping list ──────────────────────────────────────────────────────
        mappingListContainer.add(new WebMarkupContainer("emptyMappingsRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(namesModel.getObject().isEmpty());
            }
        });

        mappingListContainer.add(new ListView<String>("mappingRows", namesModel) {
            @Override
            protected void populateItem(ListItem<String> item) {
                String name = item.getModelObject();
                boolean isActive = name.equals(activeName.getObject());

                item.add(AttributeModifier.replace("class", isActive ? "table-primary" : ""));
                item.add(new Label("mappingName", name));
                item.add(new Label("entryCount", () -> {
                    ValueMappingService svc = svc();
                    return svc != null ? svc.entries(name).size() : 0;
                }));

                // "View" button — selects this mapping as the active one
                item.add(new AjaxLink<Void>("viewBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        activeName.setObject(name);
                        namesModel.detach();
                        entriesModel.detach();
                        entryFormModel.reset();
                        editingKey.setObject(null);
                        target.add(mappingListContainer, entriesContainer, entryForm);
                    }

                    @Override
                    protected void onConfigure() {
                        super.onConfigure();
                        setVisible(!name.equals(activeName.getObject()));
                    }
                });

                item.add(new WebMarkupContainer("activeBadge") {
                    @Override
                    protected void onConfigure() {
                        super.onConfigure();
                        setVisible(name.equals(activeName.getObject()));
                    }
                });

                // Delete entire mapping with confirmation
                item.add(new AjaxLink<Void>("deleteMappingBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        ValueMappingService svc = svc();
                        if (svc != null) {
                            svc.deleteMapping(name);
                            log.info("Value Mapping deleted: name='{}'", name);
                        }
                        namesModel.detach();
                        if (name.equals(activeName.getObject())) {
                            // Select first remaining mapping, or none
                            List<String> remaining = namesModel.getObject();
                            activeName.setObject(remaining.isEmpty() ? null : remaining.get(0));
                            entriesModel.detach();
                            entryFormModel.reset();
                            editingKey.setObject(null);
                            target.add(entryForm);
                        }
                        target.add(mappingListContainer, entriesContainer);
                    }

                    @Override
                    protected void updateAjaxAttributes(AjaxRequestAttributes attrs) {
                        super.updateAjaxAttributes(attrs);
                        String safe = JsEscape.forSingleQuotedLiteral(name);
                        attrs.getAjaxCallListeners().add(new AjaxCallListener()
                                .onPrecondition("return confirm('Delete mapping \\'" + safe
                                        + "\\' and all its entries?');"));
                    }
                });
            }
        });

        // New-mapping inline form
        NewMappingModel newMappingModel = new NewMappingModel();
        Form<NewMappingModel> newMappingForm = new Form<>("newMappingForm",
                new CompoundPropertyModel<>(newMappingModel));
        newMappingForm.setOutputMarkupId(true);

        FeedbackPanel newMappingFeedback = new FeedbackPanel("newMappingFeedback");
        newMappingFeedback.setOutputMarkupId(true);
        newMappingForm.add(newMappingFeedback);
        newMappingForm.add(new TextField<String>("newMappingName"));
        newMappingForm.add(new AjaxButton("createMappingBtn", newMappingForm) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                String raw = newMappingModel.newMappingName == null
                        ? "" : newMappingModel.newMappingName.trim();
                if (raw.isBlank()) {
                    error("Mapping name is required.");
                    target.add(newMappingFeedback);
                    return;
                }
                // Normalise: lowercase, non-alphanumeric → hyphen
                String name = raw.toLowerCase()
                        .replaceAll("[^a-z0-9_-]", "-")
                        .replaceAll("-{2,}", "-")
                        .replaceAll("^-|-$", "");
                ValueMappingService svc = svc();
                if (svc == null) {
                    error("Service unavailable.");
                    target.add(newMappingFeedback);
                    return;
                }
                svc.createMapping(name);
                log.info("Value Mapping created: name='{}'", name);
                activeName.setObject(name);
                namesModel.detach();
                entriesModel.detach();
                entryFormModel.reset();
                editingKey.setObject(null);
                newMappingModel.newMappingName = "";
                target.add(mappingListContainer, entriesContainer, entryForm, newMappingForm);
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(newMappingFeedback);
            }
        });

        mappingListContainer.add(newMappingForm);
        add(mappingListContainer);

        // ── Entries section ───────────────────────────────────────────────────
        entriesContainer.add(new Label("activeNameLabel",
                (IModel<String>) () -> activeName.getObject() != null
                        ? activeName.getObject() : "none selected"));

        entriesContainer.add(new WebMarkupContainer("noMappingMsg") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(activeName.getObject() == null);
            }
        });

        WebMarkupContainer entriesSection = new WebMarkupContainer("entriesSection") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(activeName.getObject() != null);
            }
        };

        WebMarkupContainer entriesTableBody = new WebMarkupContainer("entriesTableBody");

        entriesTableBody.add(new WebMarkupContainer("emptyEntriesRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(entriesModel.getObject().isEmpty());
            }
        });

        entriesTableBody.add(new ListView<MappingEntry>("entryRows", entriesModel) {
            @Override
            protected void populateItem(ListItem<MappingEntry> item) {
                MappingEntry e = item.getModelObject();
                item.add(new Label("entryKey",   e.key));
                item.add(new Label("entryValue", e.value));

                item.add(new AjaxLink<Void>("editEntryBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        entryFormModel.key   = e.key;
                        entryFormModel.value = e.value;
                        editingKey.setObject(e.key);
                        target.add(entryForm);
                        target.appendJavaScript(
                                "document.getElementById('entryFormCard')" +
                                ".scrollIntoView({behavior:'smooth'});");
                    }
                });

                item.add(new AjaxLink<Void>("deleteEntryBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        ValueMappingService svc = svc();
                        String name = activeName.getObject();
                        if (svc != null && name != null) {
                            svc.remove(name, e.key);
                            log.info("Value Mapping entry deleted: mapping='{}' key='{}'", name, e.key);
                        }
                        entriesModel.detach();
                        if (e.key.equals(editingKey.getObject())) {
                            entryFormModel.reset();
                            editingKey.setObject(null);
                            target.add(entryForm);
                        }
                        target.add(entriesContainer);
                    }

                    @Override
                    protected void updateAjaxAttributes(AjaxRequestAttributes attrs) {
                        super.updateAjaxAttributes(attrs);
                        String safe = JsEscape.forSingleQuotedLiteral(e.key);
                        attrs.getAjaxCallListeners().add(new AjaxCallListener()
                                .onPrecondition("return confirm('Remove entry \\'" + safe + "\\'?');"));
                    }
                });
            }
        });

        entriesSection.add(entriesTableBody);
        entriesContainer.add(entriesSection);
        add(entriesContainer);

        // ── Entry add / edit form ─────────────────────────────────────────────
        entryForm.add(new Label("entryFormTitle",
                (IModel<String>) () -> editingKey.getObject() == null
                        ? "Add Entry" : "Edit Entry — " + editingKey.getObject()));

        FeedbackPanel entryFeedback = new FeedbackPanel("entryFeedback");
        entryFeedback.setOutputMarkupId(true);
        entryForm.add(entryFeedback);

        entryForm.add(new TextField<String>("key"));
        entryForm.add(new TextField<String>("value"));

        AjaxButton saveEntryBtn = new AjaxButton("saveEntryBtn", entryForm) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                String name = activeName.getObject();
                if (name == null) {
                    error("Select a mapping first.");
                    target.add(entryFeedback);
                    return;
                }
                if (entryFormModel.key == null || entryFormModel.key.isBlank()) {
                    error("Key is required.");
                    target.add(entryFeedback);
                    return;
                }
                if (entryFormModel.value == null || entryFormModel.value.isBlank()) {
                    error("Value is required.");
                    target.add(entryFeedback);
                    return;
                }
                ValueMappingService svc = svc();
                if (svc == null) {
                    error("Service unavailable.");
                    target.add(entryFeedback);
                    return;
                }
                String newKey = entryFormModel.key.trim();
                String newVal = entryFormModel.value.trim();
                String oldKey = editingKey.getObject();
                if (oldKey != null && !oldKey.equals(newKey)) {
                    svc.remove(name, oldKey);
                }
                svc.put(name, newKey, newVal);
                log.info("Value Mapping entry {}: mapping='{}' key='{}' value='{}'",
                        oldKey == null ? "added" : "updated", name, newKey, newVal);
                entriesModel.detach();
                entryFormModel.reset();
                editingKey.setObject(null);
                // Refresh entry count in mapping list too
                namesModel.detach();
                target.add(mappingListContainer, entriesContainer, entryForm);
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(entryFeedback);
            }
        };
        saveEntryBtn.add(new Label("saveEntryBtnLabel",
                (IModel<String>) () -> editingKey.getObject() == null ? "Add" : "Update"));
        entryForm.add(saveEntryBtn);

        entryForm.add(new AjaxLink<Void>("cancelEntryBtn") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                entryFormModel.reset();
                editingKey.setObject(null);
                target.add(entryForm);
            }

            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(editingKey.getObject() != null);
            }
        });

        add(entryForm);
    }

    private static ValueMappingService svc() {
        return ((FixSimulatorApplication) Application.get()).getValueMappingService();
    }

    /** Serializable key/value pair for the entries list view. */
    static class MappingEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        final String key;
        final String value;
        MappingEntry(String key, String value) { this.key = key; this.value = value; }
    }

    static class EntryFormModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String key   = "";
        String value = "";

        void reset() { key = ""; value = ""; }

        public String getKey()           { return key; }
        public void   setKey(String v)   { key = v; }
        public String getValue()         { return value; }
        public void   setValue(String v) { value = v; }
    }

    static class NewMappingModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String newMappingName = "";

        public String getNewMappingName()          { return newMappingName; }
        public void   setNewMappingName(String v)  { newMappingName = v; }
    }
}
