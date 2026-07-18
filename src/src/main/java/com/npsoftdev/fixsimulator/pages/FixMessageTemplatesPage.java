package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.FixSimulatorSession;
import com.npsoftdev.fixsimulator.template.FixMessageTemplate;
import com.npsoftdev.fixsimulator.template.TemplateScope;
import com.npsoftdev.fixsimulator.template.TemplateService;
import org.apache.wicket.Application;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxCallListener;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Lists all FIX Message Templates with create, edit, and delete actions.
 *
 * <p>Create and edit navigate to the dedicated {@link FixMessageTemplateFormPage}.
 * Delete is handled inline with AJAX so the table refreshes without a full page load.
 */
public class FixMessageTemplatesPage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(FixMessageTemplatesPage.class);

    public FixMessageTemplatesPage() {
        super();

        // ── Parse-from-FIX-message form ───────────────────────────────────────
        ParseFormModel parseModel = new ParseFormModel();
        Form<ParseFormModel> parseForm = new Form<>("parseForm",
                new CompoundPropertyModel<>(parseModel));
        parseForm.setOutputMarkupId(true);

        FeedbackPanel parseFeedback = new FeedbackPanel("parseFeedback");
        parseFeedback.setOutputMarkupId(true);
        parseForm.add(parseFeedback);
        parseForm.add(new TextArea<String>("rawFixMessage"));

        parseForm.add(new Button("parseBtn") {
            @Override
            public void onSubmit() {
                String raw = parseModel.rawFixMessage;
                if (raw == null || raw.isBlank()) {
                    error("Please paste a FIX message.");
                    return;
                }
                // Light validation: must contain tag 35
                TemplateFormPanel.TemplateFormModel preview = new TemplateFormPanel.TemplateFormModel();
                TemplateFormPanel.parseFixMessage(raw.trim(), preview);
                if (preview.msgType == null || preview.msgType.isBlank()) {
                    error("Could not find MsgType (tag 35) — please check the message format.");
                    return;
                }
                FixSimulatorSession.get().setPendingFixMessage(raw.trim());
                setResponsePage(FixMessageTemplateFormPage.class,
                        new PageParameters().add("fromParsed", "true"));
            }
        });

        add(parseForm);

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

                item.add(new Label("tPriority", String.valueOf(t.priority())));
                item.add(new Label("tFieldCount", String.valueOf(t.fields().size())));
                item.add(new Label("tDescription", t.description()));

                // Protected badge — shown only for deletion-protected templates
                WebMarkupContainer protectedBadge = new WebMarkupContainer("protectedBadge");
                protectedBadge.setVisible(t.isDeletionProtected());
                item.add(protectedBadge);

                // Edit button — navigates to the form page with this template's ID
                PageParameters editParams = new PageParameters();
                editParams.add("templateId", t.id());
                item.add(new BookmarkablePageLink<>("editBtn",
                        FixMessageTemplateFormPage.class, editParams));

                // Duplicate button — opens the form page pre-populated as a copy
                PageParameters dupParams = new PageParameters();
                dupParams.add("duplicateId", t.id());
                item.add(new BookmarkablePageLink<>("duplicateBtn",
                        FixMessageTemplateFormPage.class, dupParams));

                // Delete button — AJAX in-place; disabled for protected templates
                AjaxLink<Void> deleteBtn = new AjaxLink<>("deleteBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        TemplateService ts = templateSvc();
                        if (ts == null) return;
                        try {
                            ts.delete(t.id());
                            log.info("FIX Message Template deleted: id={} name='{}' msgType={}",
                                    t.id(), t.name(), t.msgType());
                        } catch (IllegalStateException ex) {
                            // Deletion-protected guard — should not be reached since the
                            // button is disabled in the UI, but guard defensively.
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

        // "New Template" button — navigates to the form page in create mode
        add(new BookmarkablePageLink<>("createBtn", FixMessageTemplateFormPage.class));
    }

    private static TemplateService templateSvc() {
        return ((FixSimulatorApplication) Application.get()).getTemplateService();
    }

    static class ParseFormModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String rawFixMessage = "";
        public String getRawFixMessage()          { return rawFixMessage; }
        public void   setRawFixMessage(String v)  { rawFixMessage = v; }
    }
}
