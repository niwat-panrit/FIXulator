package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.service.ConnectionService;
import com.npsoftdev.fixsimulator.service.ConnectionService.NewSessionRequest;
import com.npsoftdev.fixsimulator.service.ConnectionService.SessionDetails;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.LoadableDetachableModel;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Displays all configured FIX sessions with their live status and sequence numbers,
 * and provides connect / disconnect / reset-sequence actions.
 */
public class ConnectionManagementPage extends BasePage {

    public ConnectionManagementPage() {
        super();

        ConnectionService connectionService =
                ((FixSimulatorApplication) getApplication()).getConnectionService();

        // Model that re-queries the service on each render
        LoadableDetachableModel<List<SessionDetails>> sessionsModel =
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<SessionDetails> load() {
                        if (connectionService == null) return Collections.emptyList();
                        return connectionService.listSessions();
                    }
                };

        // Auto-refreshing container wrapping the whole table body
        WebMarkupContainer tableBody = new WebMarkupContainer("tableBody");
        tableBody.setOutputMarkupId(true);
        tableBody.add(new AjaxSelfUpdatingTimerBehavior(Duration.ofSeconds(3)));
        add(tableBody);

        // Empty-state row — visible only when there are no sessions
        WebMarkupContainer noSessionsRow = new WebMarkupContainer("noSessionsRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(sessionsModel.getObject().isEmpty());
            }
        };
        tableBody.add(noSessionsRow);

        // Add-session form (modal)
        addSessionForm(connectionService, tableBody);

        // Session rows
        tableBody.add(new ListView<>("sessionRows", sessionsModel) {
            @Override
            protected void populateItem(ListItem<SessionDetails> item) {
                SessionDetails s = item.getModelObject();

                item.add(new Label("name",           s.name()));
                item.add(new Label("fixVersion",     s.fixVersion()));
                item.add(new Label("senderCompID",   s.senderCompID()));
                item.add(new Label("targetCompID",   s.targetCompID()));
                item.add(new Label("hostPort",       s.hostPort()));
                item.add(new Label("heartbeat",      s.heartbeatSecs() + "s"));
                item.add(new Label("txSeq",          String.valueOf(s.txSeq())));
                item.add(new Label("rxSeq",          String.valueOf(s.rxSeq())));

                // Connection-type badge
                Label typeBadge = new Label("connectionTypeBadge", s.connectionType());
                typeBadge.add(AttributeModifier.replace("class",
                        "Acceptor".equalsIgnoreCase(s.connectionType())
                                ? "badge bg-dark"
                                : "badge bg-secondary"));
                item.add(typeBadge);

                // Status badge
                Label statusBadge = new Label("statusBadge", s.status());
                statusBadge.add(AttributeModifier.replace("class", statusBadgeCss(s.status())));
                item.add(statusBadge);

                // Connect link — visible only when not connected
                Link<Void> connectLink = new Link<>("connectLink") {
                    @Override
                    public void onClick() {
                        if (connectionService != null)
                            connectionService.connect(s.sessionId());
                    }
                    @Override
                    protected void onConfigure() {
                        super.onConfigure();
                        setVisible(!"CONNECTED".equals(s.status()));
                    }
                };
                item.add(connectLink);

                // Disconnect link — visible only when connected
                Link<Void> disconnectLink = new Link<>("disconnectLink") {
                    @Override
                    public void onClick() {
                        if (connectionService != null)
                            connectionService.disconnect(s.sessionId());
                    }
                    @Override
                    protected void onConfigure() {
                        super.onConfigure();
                        setVisible("CONNECTED".equals(s.status()));
                    }
                };
                item.add(disconnectLink);

                // Reset sequence link
                item.add(new Link<Void>("resetSeqLink") {
                    @Override
                    public void onClick() {
                        if (connectionService != null)
                            connectionService.resetSequence(s.sessionId());
                    }
                });
            }
        });
    }

    // ── Add-session form ──────────────────────────────────────────────────────

    private void addSessionForm(ConnectionService connectionService,
                                WebMarkupContainer tableBody) {
        NewSessionModel model = new NewSessionModel();
        Form<NewSessionModel> form = new Form<>("addSessionForm",
                new CompoundPropertyModel<>(model));

        form.add(new DropDownChoice<>("connectionType",
                List.of("Initiator", "Acceptor")).setRequired(true));

        // BeginString field — auto-updated when fixVersion changes
        TextField<String> beginStringField = new TextField<>("beginString");
        beginStringField.setOutputMarkupId(true);
        beginStringField.setRequired(true);
        form.add(beginStringField);

        // fixVersion dropdown — updates beginString on change
        DropDownChoice<String> fixVersionChoice = new DropDownChoice<>("fixVersion",
                List.of("FIX.4.2", "FIX.4.4", "FIX.5.0", "FIX.5.0SP1", "FIX.5.0SP2"));
        fixVersionChoice.setRequired(true);
        fixVersionChoice.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                model.beginString = deriveBeginString(model.fixVersion);
                target.add(beginStringField);
            }
        });
        form.add(fixVersionChoice);

        form.add(new TextField<String>("senderCompID").setRequired(true));
        form.add(new TextField<String>("targetCompID").setRequired(true));
        form.add(new TextField<String>("host").setRequired(true));
        form.add(new NumberTextField<>("port", Integer.class).setMinimum(1).setMaximum(65535).setRequired(true));
        form.add(new NumberTextField<>("heartbeatSecs", Integer.class).setMinimum(1));
        form.add(new CheckBox("resetOnLogon"));

        form.add(new AjaxButton("saveBtn", form) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                if (connectionService != null) {
                    connectionService.addSession(new NewSessionRequest(
                            model.connectionType,
                            model.fixVersion,
                            model.beginString,
                            model.senderCompID,
                            model.targetCompID,
                            model.host,
                            model.port,
                            model.heartbeatSecs,
                            model.resetOnLogon
                    ));
                }
                target.add(tableBody);
                target.appendJavaScript(
                        "bootstrap.Modal.getInstance(document.getElementById('connModal')).hide();");
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                // Wicket renders validation feedback inline; nothing extra needed
            }
        });

        add(form);
    }

    // ── Form model ────────────────────────────────────────────────────────────

    private static final class NewSessionModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String  connectionType = "Initiator";
        String  fixVersion     = "FIX.4.4";
        String  beginString    = "FIX.4.4";   // same as fixVersion for FIX 4.x; "FIXT.1.1" for 5.0+
        String  senderCompID   = "";
        String  targetCompID   = "";
        String  host           = "";
        Integer port           = 9876;
        Integer heartbeatSecs  = 30;
        boolean resetOnLogon   = true;
    }

    private static String deriveBeginString(String fixVersion) {
        if (fixVersion != null && fixVersion.startsWith("FIX.5")) return "FIXT.1.1";
        return fixVersion != null ? fixVersion : "FIX.4.4";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String statusBadgeCss(String status) {
        return switch (status) {
            case "CONNECTED"    -> "badge bg-success";
            case "DISCONNECTED" -> "badge bg-danger";
            default             -> "badge bg-warning text-dark";
        };
    }
}
