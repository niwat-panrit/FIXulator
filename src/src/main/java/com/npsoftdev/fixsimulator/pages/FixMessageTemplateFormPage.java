package com.npsoftdev.fixsimulator.pages;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 * Full-page form for creating or editing a FIX Message Template.
 *
 * <p>Accepts an optional {@code templateId} page parameter:
 * <ul>
 *   <li>Present → edit mode (form pre-populated from the existing template).</li>
 *   <li>Absent  → create mode (blank form, new UUID assigned).</li>
 * </ul>
 *
 * <p>The actual form logic lives in the shared {@link TemplateFormPanel} component.
 */
public class FixMessageTemplateFormPage extends BasePage {

    public FixMessageTemplateFormPage(PageParameters params) {
        super();
        String templateId = params.get("templateId").toOptionalString();
        boolean isEdit = templateId != null;

        add(new Label("pageAction", isEdit ? "Edit Template" : "New Template"));
        add(new TemplateFormPanel("templateFormPanel", templateId));
    }
}
