package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorSession;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 * Full-page form for creating or editing a FIX Message Template.
 *
 * <p>Accepts optional page parameters:
 * <ul>
 *   <li>{@code templateId} — edit mode, pre-populated from the existing template.</li>
 *   <li>{@code fromParsed=true} — create mode pre-populated by parsing a raw FIX message
 *       stored in the session by the template-list page.</li>
 *   <li>Neither — blank create mode.</li>
 * </ul>
 *
 * <p>The actual form logic lives in the shared {@link TemplateFormPanel} component.
 */
public class FixMessageTemplateFormPage extends BasePage {

    public FixMessageTemplateFormPage(PageParameters params) {
        super();
        String templateId   = params.get("templateId").toOptionalString();
        String duplicateId  = params.get("duplicateId").toOptionalString();
        boolean fromParsed  = params.get("fromParsed").toBoolean(false);
        boolean isEdit      = templateId != null;

        add(new Label("pageAction", isEdit ? "Edit Template" : "New Template"));

        if (fromParsed) {
            // Consume (and clear) the raw FIX message stored by the list page
            String raw = FixSimulatorSession.get().takePendingFixMessage();
            add(new TemplateFormPanel("templateFormPanel", null, raw));
        } else if (duplicateId != null) {
            add(new TemplateFormPanel("templateFormPanel", duplicateId, true));
        } else {
            add(new TemplateFormPanel("templateFormPanel", templateId));
        }
    }
}
