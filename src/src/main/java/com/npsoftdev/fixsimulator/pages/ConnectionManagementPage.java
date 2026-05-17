package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.service.ConnectionService;
import com.npsoftdev.fixsimulator.service.ConnectionService.SessionDetails;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.LoadableDetachableModel;

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String statusBadgeCss(String status) {
        return switch (status) {
            case "CONNECTED"    -> "badge bg-success";
            case "DISCONNECTED" -> "badge bg-danger";
            default             -> "badge bg-warning text-dark";
        };
    }
}
