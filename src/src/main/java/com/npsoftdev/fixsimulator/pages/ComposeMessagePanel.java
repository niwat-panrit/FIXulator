package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.FixSimulatorSession;
import com.npsoftdev.fixsimulator.service.ConnectionService;
import org.apache.wicket.Application;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptReferenceHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.PackageResourceReference;

import java.io.Serializable;
import java.time.Duration;

/**
 * Reusable offcanvas panel for composing and sending raw FIX messages.
 *
 * <p>Add an instance to any page and place a trigger button in the page header:
 * <pre>
 *   &lt;button data-bs-toggle="offcanvas" data-bs-target="#composeOffcanvas"&gt;
 *     Compose Message
 *   &lt;/button&gt;
 * </pre>
 *
 * <p>After a successful send the panel calls {@code target.add(refreshOnSend)} so
 * the host page can keep its table view current.  The active-session info holder
 * (used by the JS to show field annotations) refreshes itself every 5 seconds.</p>
 */
public class ComposeMessagePanel extends Panel {

    // ── Inner model ───────────────────────────────────────────────────────────

    static final class ComposeModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String rawMessage = "";
        String delimiter  = "|";
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param id            Wicket component ID
     * @param refreshOnSend Components to refresh via AJAX after a successful send
     */
    public ComposeMessagePanel(String id, Component... refreshOnSend) {
        super(id);

        // ── Session info holder — JS reads active-session comp IDs from here ──
        WebMarkupContainer sessionInfoHolder = new WebMarkupContainer("sessionInfoHolder") {
            @Override
            protected void onComponentTag(ComponentTag tag) {
                super.onComponentTag(tag);
                String sid = FixSimulatorSession.get().getActiveSessionId();
                tag.put("data-begin-string", "");
                tag.put("data-sender", "");
                tag.put("data-target", "");
                ConnectionService cs = connSvc();
                if (sid != null && cs != null) {
                    cs.listSessions().stream()
                      .filter(s -> s.sessionId().equals(sid))
                      .findFirst()
                      .ifPresent(s -> {
                          tag.put("data-begin-string", s.fixVersion());
                          tag.put("data-sender", s.senderCompID());
                          tag.put("data-target", s.targetCompID());
                      });
                }
            }
        };
        sessionInfoHolder.setOutputMarkupId(true);

        // Keep session info current without relying on the host page's timer
        sessionInfoHolder.add(new AbstractAjaxTimerBehavior(Duration.ofSeconds(5)) {
            @Override
            protected void onTimer(AjaxRequestTarget target) {
                target.add(sessionInfoHolder);
            }
        });
        add(sessionInfoHolder);

        // ── Compose form ──────────────────────────────────────────────────────
        ComposeModel composeModel = new ComposeModel();
        Form<ComposeModel> composeForm = new Form<>("composeForm",
                new CompoundPropertyModel<>(composeModel));

        TextArea<String> rawMsgArea = new TextArea<>("rawMessage");
        rawMsgArea.setMarkupId("composeTextarea").setOutputMarkupId(true);
        composeForm.add(rawMsgArea);

        TextField<String> delimField = new TextField<>("delimiter");
        delimField.setMarkupId("composeDelimiter").setOutputMarkupId(true);
        composeForm.add(delimField);

        // Feedback area
        Model<String> resultClass = Model.of("d-none");
        Model<String> resultMsg   = Model.of("");
        WebMarkupContainer composeResult = new WebMarkupContainer("composeResult");
        composeResult.add(AttributeModifier.replace("class", resultClass));
        composeResult.setOutputMarkupId(true);
        composeResult.add(new Label("composeResultMsg", resultMsg));
        composeForm.add(composeResult);

        composeForm.add(new AjaxButton("composeSendBtn", composeForm) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                String sessionId = FixSimulatorSession.get().getActiveSessionId();
                if (sessionId == null) {
                    showFeedback(target, false, "No active session selected.");
                    return;
                }
                ConnectionService cs = connSvc();
                if (cs == null) {
                    showFeedback(target, false, "Connection service unavailable.");
                    return;
                }
                String raw = composeModel.rawMessage;
                if (raw == null || raw.isBlank()) {
                    showFeedback(target, false, "Raw FIX message is empty.");
                    return;
                }
                String delim = (composeModel.delimiter == null || composeModel.delimiter.isEmpty())
                        ? "|" : composeModel.delimiter;
                try {
                    cs.sendRaw(sessionId, raw, delim);
                    showFeedback(target, true, "Message sent successfully.");
                    target.add(refreshOnSend);
                } catch (Exception e) {
                    showFeedback(target, false, "Send failed: " + e.getMessage());
                }
            }

            @Override
            protected void onError(AjaxRequestTarget target) { /* no validators wired */ }

            private void showFeedback(AjaxRequestTarget target, boolean success, String msg) {
                resultClass.setObject(
                        "alert py-1 px-2 mt-2 mb-1 " + (success ? "alert-success" : "alert-danger"));
                resultMsg.setObject(msg);
                target.add(composeResult);
            }
        });

        add(composeForm);
    }

    // ── Header resources ──────────────────────────────────────────────────────

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(JavaScriptReferenceHeaderItem.forReference(
                new PackageResourceReference(ComposeMessagePanel.class, "ComposeMessagePanel.js")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ConnectionService connSvc() {
        return ((FixSimulatorApplication) Application.get()).getConnectionService();
    }
}
