package com.npsoftdev.fixsimulator.plugins.order.ui;

import com.npsoftdev.fixsimulator.core.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.core.FixSimulatorSession;
import com.npsoftdev.fixsimulator.plugins.template.api.FieldSpec;
import com.npsoftdev.fixsimulator.plugins.template.api.FieldValue;
import com.npsoftdev.fixsimulator.plugins.template.api.FixMessageTemplate;
import com.npsoftdev.fixsimulator.plugins.template.api.TemplateService;
import org.apache.wicket.Application;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.npsoftdev.fixsimulator.core.ui.BasePage;

/**
 * Send FIX messages using a selected template.
 *
 * <p>After a template is chosen the dialog splits into two sections:
 * <ol>
 *   <li><b>Standard order fields</b> — Symbol (55), Side (54), Price (44),
 *       Quantity/OrderQty (38) and OrigClOrdID (41).  These are rendered in a
 *       structured two-column layout when the template exposes them as
 *       {@link FieldValue.UserInput} or {@link FieldValue.Enumeration}.</li>
 *   <li><b>Additional fields</b> — any remaining UserInput / Enumeration
 *       fields from the template, rendered as key-value pairs (one per line).</li>
 * </ol>
 *
 * <p>The dialog title reflects the template's MsgType:
 * {@code "D"} → "New Order", {@code "G"} → "Update Order",
 * otherwise → "Send Message".</p>
 */
public class DynamicOrdersPage extends BasePage {

    private Form<Void>   sendForm;
    private SendModel    sendModel;

    /** Tags that appear in the "standard fields" structured layout. */
    private static final Set<Integer> STANDARD_TAGS = Set.of(
            55,  // Symbol
            54,  // Side
            44,  // Price
            38,  // OrderQty
            41   // OrigClOrdID (for cancel/replace)
    );

    /** Human-readable labels for standard tags. */
    private static String standardLabel(int tag) {
        return switch (tag) {
            case 55 -> "Symbol";
            case 54 -> "Side";
            case 44 -> "Price";
            case 38 -> "Quantity";
            case 41 -> "Orig. ClOrdID";
            default -> "Tag " + tag;
        };
    }

    public DynamicOrdersPage() {
        super();

        sendModel = new SendModel();
        SendModel model = sendModel;
        sendForm = new Form<>("sendForm");
        Form<Void> form = sendForm;
        form.setOutputMarkupId(true);

        FeedbackPanel feedback = new FeedbackPanel("feedback");
        feedback.setOutputMarkupId(true);
        form.add(feedback);

        // ── Template choices ──────────────────────────────────────────────────
        LoadableDetachableModel<List<FixMessageTemplate>> templateChoices =
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<FixMessageTemplate> load() {
                        TemplateService ts = templateSvc();
                        if (ts == null) return Collections.emptyList();
                        String sid = FixSimulatorSession.get().getActiveSessionId();
                        return ts.findVisibleTo(sid);
                    }
                };

        // ── Dialog panel (AJAX-refreshed on template selection) ───────────────
        WebMarkupContainer dialogPanel = new WebMarkupContainer("dialogPanel");
        dialogPanel.setOutputMarkupId(true);

        // Placeholder shown before any template is selected
        dialogPanel.add(new WebMarkupContainer("noTemplateMsg") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(model.selectedTemplate == null);
            }
        });

        // Main dialog container
        WebMarkupContainer dialogBody = new WebMarkupContainer("dialogBody") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(model.selectedTemplate != null);
            }
        };

        // Dialog title based on MsgType
        dialogBody.add(new Label("dialogTitle", (IModel<String>) () -> {
            if (model.selectedTemplate == null) return "";
            return switch (model.selectedTemplate.msgType()) {
                case "D" -> "New Order";
                case "G" -> "Update Order";
                default  -> "Send Message";
            };
        }));

        // Template metadata strip
        dialogBody.add(new Label("tmplMsgType",
                (IModel<String>) () -> model.selectedTemplate != null
                        ? model.selectedTemplate.msgType() : ""));
        dialogBody.add(new Label("tmplDescription",
                (IModel<String>) () -> model.selectedTemplate != null
                        ? model.selectedTemplate.description() : ""));
        dialogBody.add(new Label("tmplScope",
                (IModel<String>) () -> model.selectedTemplate != null
                        ? model.selectedTemplate.scope().toString() : ""));

        // ── Standard fields section ───────────────────────────────────────────
        WebMarkupContainer standardSection = new WebMarkupContainer("standardSection") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(!model.standardFields.isEmpty());
            }
        };

        standardSection.add(new ListView<SendEntry>("standardFieldRows",
                new PropertyModel<>(model, "standardFields")) {
            @Override
            protected void populateItem(ListItem<SendEntry> item) {
                renderFieldRow(item);
            }
        });

        dialogBody.add(standardSection);

        // ── Additional fields section ─────────────────────────────────────────
        WebMarkupContainer additionalSection = new WebMarkupContainer("additionalSection") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(!model.additionalFields.isEmpty());
            }
        };

        additionalSection.add(new ListView<SendEntry>("additionalFieldRows",
                new PropertyModel<>(model, "additionalFields")) {
            @Override
            protected void populateItem(ListItem<SendEntry> item) {
                renderFieldRow(item);
            }
        });

        dialogBody.add(additionalSection);

        // "No inputs needed" notice
        dialogBody.add(new WebMarkupContainer("noInputsMsg") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(model.selectedTemplate != null
                        && model.standardFields.isEmpty()
                        && model.additionalFields.isEmpty());
            }
        });

        dialogPanel.add(dialogBody);
        form.add(dialogPanel);

        // ── Template selector ─────────────────────────────────────────────────
        DropDownChoice<FixMessageTemplate> templateSelector = new DropDownChoice<>(
                "templateSelector",
                new PropertyModel<>(model, "selectedTemplate"),
                templateChoices,
                new IChoiceRenderer<>() {
                    @Override
                    public Object getDisplayValue(FixMessageTemplate t) {
                        return t != null ? t.name() + "  [" + t.msgType() + "]" : "";
                    }
                    @Override
                    public String getIdValue(FixMessageTemplate t, int idx) {
                        return t != null ? t.id() : "";
                    }
                    @Override
                    public FixMessageTemplate getObject(String id,
                            IModel<? extends List<? extends FixMessageTemplate>> choices) {
                        if (id == null || id.isEmpty()) return null;
                        return choices.getObject().stream()
                                .filter(t -> t.id().equals(id)).findFirst().orElse(null);
                    }
                });
        templateSelector.setNullValid(true);
        templateSelector.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                model.standardFields.clear();
                model.additionalFields.clear();
                if (model.selectedTemplate != null) {
                    populateSendEntries(model);
                }
                target.add(dialogPanel, feedback);
            }
        });
        form.add(templateSelector);

        // ── Send button ───────────────────────────────────────────────────────
        form.add(new AjaxButton("sendBtn", form) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                if (model.selectedTemplate == null) {
                    error("Please select a template.");
                    target.add(feedback);
                    return;
                }
                String sid = FixSimulatorSession.get().getActiveSessionId();
                if (sid == null) {
                    error("No active FIX session selected.");
                    target.add(feedback);
                    return;
                }
                TemplateService ts = templateSvc();
                if (ts == null) {
                    error("Template service not available.");
                    target.add(feedback);
                    return;
                }
                Map<String, String> overrides = new HashMap<>();
                for (SendEntry e : model.allFields()) {
                    if (e.value != null && !e.value.isBlank()) {
                        overrides.put(e.name, e.value);
                    }
                }
                try {
                    ts.send(sid, model.selectedTemplate.id(), overrides);
                    success("Message sent via template '" + model.selectedTemplate.name() + "'.");
                    // Reset input values to defaults
                    for (SendEntry e : model.allFields()) {
                        e.value = e.defaultValue != null ? e.defaultValue : "";
                    }
                } catch (Exception ex) {
                    error("Send failed: " + ex.getMessage());
                }
                target.add(feedback, dialogPanel);
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(feedback);
            }
        });

        add(form);
    }

    @Override
    protected void onSessionSwitched(AjaxRequestTarget target) {
        // Clear selection — the chosen template may not apply to the new session
        sendModel.selectedTemplate = null;
        sendModel.standardFields.clear();
        sendModel.additionalFields.clear();
        target.add(sendForm);
    }

    // ── Field row rendering (shared by standard + additional ListViews) ────────

    private static void renderFieldRow(ListItem<SendEntry> item) {
        SendEntry entry = item.getModelObject();

        item.add(new Label("fieldLabel", entry.displayName));

        String hintText = entry.defaultValue != null && !entry.defaultValue.isBlank()
                ? "default: " + entry.defaultValue : "";
        item.add(new Label("fieldHint", hintText));

        // Text input — shown for TEXT entries
        WebMarkupContainer textDiv = new WebMarkupContainer("textInputDiv");
        textDiv.setVisible(entry.type == SendEntry.Type.TEXT);
        textDiv.add(new TextField<>("fieldValue", new PropertyModel<>(entry, "value")));
        item.add(textDiv);

        // Dropdown — shown for ENUM entries
        WebMarkupContainer enumDiv = new WebMarkupContainer("enumInputDiv");
        enumDiv.setVisible(entry.type == SendEntry.Type.ENUM);
        enumDiv.add(new DropDownChoice<>("fieldEnum",
                new PropertyModel<>(entry, "value"),
                entry.options != null ? entry.options : List.of()));
        item.add(enumDiv);
    }

    // ── Entry population ──────────────────────────────────────────────────────

    private static void populateSendEntries(SendModel model) {
        FixMessageTemplate t = model.selectedTemplate;
        if (t == null) return;
        for (FieldSpec spec : t.fields()) {
            SendEntry entry = null;
            if (spec.value() instanceof FieldValue.UserInput ui) {
                String label = standardLabel(spec.tag()) + "  (tag " + spec.tag() + ")";
                entry = SendEntry.text(spec.tag(), ui.name(), label, ui.defaultValue());
            } else if (spec.value() instanceof FieldValue.Enumeration en) {
                String label = standardLabel(spec.tag()) + "  (tag " + spec.tag() + ")";
                entry = SendEntry.enumeration(spec.tag(), en.name(), label,
                        en.options(), en.defaultOption());
            }
            if (entry == null) continue;
            if (STANDARD_TAGS.contains(spec.tag())) {
                model.standardFields.add(entry);
            } else {
                model.additionalFields.add(entry);
            }
        }
    }

    private static TemplateService templateSvc() {
        return ((FixSimulatorApplication) Application.get()).getTemplateService();
    }

    // ── Models ────────────────────────────────────────────────────────────────

    static class SendModel implements Serializable {
        private static final long serialVersionUID = 1L;
        FixMessageTemplate selectedTemplate;
        List<SendEntry> standardFields   = new ArrayList<>();
        List<SendEntry> additionalFields = new ArrayList<>();

        List<SendEntry> allFields() {
            List<SendEntry> all = new ArrayList<>();
            all.addAll(standardFields);
            all.addAll(additionalFields);
            return all;
        }
    }

    static class SendEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        enum Type { TEXT, ENUM }

        final int    tag;
        final String name;
        final String displayName;
        final Type   type;
        String       value;
        final String defaultValue;
        final List<String> options;

        private SendEntry(int tag, String name, String displayName, Type type,
                          String defaultValue, List<String> options) {
            this.tag          = tag;
            this.name         = name;
            this.displayName  = displayName;
            this.type         = type;
            this.defaultValue = defaultValue;
            this.value        = defaultValue != null ? defaultValue : "";
            this.options      = options != null ? options : List.of();
        }

        static SendEntry text(int tag, String name, String displayName, String defaultValue) {
            return new SendEntry(tag, name, displayName, Type.TEXT, defaultValue, null);
        }

        static SendEntry enumeration(int tag, String name, String displayName,
                                     List<String> options, String defaultOption) {
            return new SendEntry(tag, name, displayName, Type.ENUM, defaultOption, options);
        }

        // PropertyModel accessors
        public String getValue()         { return value; }
        public void setValue(String v)   { value = v; }
    }
}
