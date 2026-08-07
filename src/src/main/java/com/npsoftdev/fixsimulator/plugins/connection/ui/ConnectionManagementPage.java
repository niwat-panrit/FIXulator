package com.npsoftdev.fixsimulator.plugins.connection.ui;

import com.npsoftdev.fixsimulator.core.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.core.FixSimulatorSession;
import com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService;
import com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest;
import com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.SessionActivity;
import com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.SessionDetails;
import com.npsoftdev.fixsimulator.plugins.connection.api.SessionStartException;
import org.apache.wicket.Application;
import org.apache.wicket.AttributeModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.ajax.attributes.AjaxCallListener;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
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
import com.npsoftdev.fixsimulator.core.ui.BasePage;
import com.npsoftdev.fixsimulator.core.ui.JsEscape;
import com.npsoftdev.fixsimulator.plugins.user.api.User;

/**
 * Displays all configured FIX sessions with their live status and sequence numbers,
 * and provides connect / disconnect / reset-sequence / edit / delete actions.
 *
 * <p>No reference to {@link ConnectionService} is ever captured inside a closure.
 * Every handler looks the service up fresh via {@link #connSvc()} so that Wicket's
 * page store never tries to serialise the service (or the QuickFIX/J objects it
 * transitively holds).</p>
 */
public class ConnectionManagementPage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManagementPage.class);

    public ConnectionManagementPage() {
        super();

        // Model that re-queries the service on each render — uses Application.get()
        // so no service reference is captured in the closure.
        LoadableDetachableModel<List<SessionDetails>> sessionsModel =
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<SessionDetails> load() {
                        ConnectionService cs =
                                ((FixSimulatorApplication) Application.get()).getConnectionService();
                        if (cs == null) return Collections.emptyList();
                        return cs.listSessions();
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

        // Shared model for the add/edit form
        NewSessionModel model = new NewSessionModel();

        // Dynamic modal title label (sits outside the <form> in the modal header)
        Label modalTitle = new Label("modalTitle", () -> model.modalTitle);
        modalTitle.setOutputMarkupId(true);
        add(modalTitle);

        // "Add Connection" button — resets form to create-mode then opens modal
        add(new AjaxLink<Void>("addConnectionBtn") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                model.resetToDefaults();
                target.add(modalTitle);
            }
        });

        // Add / edit session form (modal)
        Form<NewSessionModel> form = buildSessionForm(model, tableBody, modalTitle);
        add(form);

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

                // One control for both pre-established states: it starts the session
                // when idle, and stops it while it is connecting or listening. The
                // separate Disconnect link takes over once the session is up. An
                // initiator dials out and an acceptor waits, so the wording follows
                // the session type rather than saying "Connect" for both.
                boolean isAcceptor = "Acceptor".equalsIgnoreCase(s.connectionType());
                SessionActivity activity = activityOf(s.sessionId());
                boolean pending = activity == SessionActivity.PENDING;

                Link<Void> connectLink = new Link<>("connectLink") {
                    @Override
                    public void onClick() {
                        ConnectionService cs = connSvc();
                        if (cs == null) return;
                        // Re-read: the table refreshes on a timer, so the state may
                        // have moved on since this row was rendered.
                        boolean stopping = cs.getSessionActivity(s.sessionId()) == SessionActivity.PENDING;
                        log.info("User '{}' clicked {} for session: {}", currentUser(),
                                stopping ? (isAcceptor ? "Stop Listening" : "Stop Connecting")
                                         : (isAcceptor ? "Listen" : "Connect"),
                                s.sessionId());
                        try {
                            if (stopping) cs.disconnect(s.sessionId());
                            else          cs.connect(s.sessionId());
                        } catch (SessionStartException e) {
                            // Retrying a busy port is expected to fail until it is
                            // free; report it and leave the session configured.
                            log.warn("Could not start session {}: {}", s.sessionId(), e.getMessage());
                            error(e.getMessage());
                        }
                    }
                    @Override
                    protected void onConfigure() {
                        super.onConfigure();
                        setVisible(!"CONNECTED".equals(s.status()));
                    }
                };
                connectLink.add(new Label("connectLinkLabel",
                        pending ? (isAcceptor ? "Stop Listening" : "Stop Connecting")
                                : (isAcceptor ? "Listen" : "Connect")));
                connectLink.add(AttributeModifier.replace("class",
                        pending ? "btn btn-outline-warning" : "btn btn-outline-success"));
                item.add(connectLink);

                // Disconnect link — visible only when connected
                item.add(new Link<Void>("disconnectLink") {
                    @Override
                    public void onClick() {
                        log.info("User '{}' clicked Disconnect for session: {}",
                                currentUser(), s.sessionId());
                        ConnectionService cs = connSvc();
                        if (cs != null) cs.disconnect(s.sessionId());
                    }
                    @Override
                    protected void onConfigure() {
                        super.onConfigure();
                        setVisible("CONNECTED".equals(s.status()));
                    }
                });

                // Reset sequence link
                item.add(new Link<Void>("resetSeqLink") {
                    @Override
                    public void onClick() {
                        log.info("User '{}' reset sequence numbers for session: {}",
                                currentUser(), s.sessionId());
                        ConnectionService cs = connSvc();
                        if (cs != null) cs.resetSequence(s.sessionId());
                    }
                });

                // Edit link — pre-populates the form and opens the modal
                item.add(new AjaxLink<Void>("editLink") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        model.populateFrom(s);
                        target.add(form, modalTitle);
                        target.appendJavaScript(
                                "new bootstrap.Modal(document.getElementById('connModal')).show();");
                    }
                });

                // Delete link — confirms, disconnects if needed, then permanently removes
                item.add(new AjaxLink<Void>("deleteLink") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        ConnectionService cs = connSvc();
                        if (cs != null) {
                            log.info("User '{}' deleted session: {}", currentUser(), s.sessionId());
                            cs.deleteSession(s.sessionId());
                        }
                        target.add(tableBody);
                    }

                    @Override
                    protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
                        super.updateAjaxAttributes(attributes);
                        String safeName = JsEscape.forSingleQuotedLiteral(s.name());
                        attributes.getAjaxCallListeners().add(new AjaxCallListener()
                                .onPrecondition("return confirm('Delete connection \\'" + safeName
                                        + "\\'?\\nIf currently connected it will be disconnected first."
                                        + "\\nThis cannot be undone.');"));
                    }
                });
            }
        });
    }

    // ── Add / edit session form ───────────────────────────────────────────────

    private Form<NewSessionModel> buildSessionForm(NewSessionModel model,
                                                   WebMarkupContainer tableBody,
                                                   Label modalTitle) {
        Form<NewSessionModel> form = new Form<>("addSessionForm",
                new CompoundPropertyModel<>(model));
        form.setOutputMarkupId(true);

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
                ConnectionService cs = connSvc();
                if (cs != null) {
                    NewSessionRequest req = new NewSessionRequest(
                            model.connectionType,
                            model.fixVersion,
                            model.beginString,
                            model.senderCompID,
                            model.targetCompID,
                            model.host,
                            model.port,
                            model.heartbeatSecs,
                            model.resetOnLogon
                    );
                    try {
                        if (model.editingSessionId != null) {
                            log.info("User '{}' updated session: {} → {}→{} {}:{}",
                                    currentUser(), model.editingSessionId,
                                    req.senderCompID(), req.targetCompID(),
                                    req.host(), req.port());
                            cs.updateSession(model.editingSessionId, req);
                        } else {
                            log.info("User '{}' added session: {}→{} {}:{} hb={}s",
                                    currentUser(),
                                    req.senderCompID(), req.targetCompID(),
                                    req.host(), req.port(), req.heartbeatSecs());
                            cs.addSession(req);
                        }
                    } catch (SessionStartException e) {
                        // The session was saved; only the start failed, and the
                        // usual reason is a port the user can free or change. Show
                        // it rather than letting Wicket render an internal error.
                        log.warn("Session saved but did not start: {}", e.getMessage());
                        error(e.getMessage());
                        target.add(getFeedbackArea());
                    }
                    model.resetToDefaults();
                    target.add(modalTitle);
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

        return form;
    }

    // ── Head contributions ────────────────────────────────────────────────────

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        // Countdown progress bar: drains from 100 % → 0 % over the auto-refresh interval
        // and restarts on every Wicket AJAX completion (including the timer tick itself).
        response.render(OnDomReadyHeaderItem.forScript("""
            (function () {
                var INTERVAL_MS = 3000;
                var bar   = document.getElementById('refreshProgress');
                var label = document.getElementById('refreshCountdown');
                if (!bar || !label) return;
                var raf = null, t0 = Date.now();
                function tick() {
                    var elapsed   = Date.now() - t0;
                    var pct       = Math.max(0, 100 - (elapsed / INTERVAL_MS * 100));
                    var remaining = Math.ceil(Math.max(0, INTERVAL_MS - elapsed) / 1000);
                    bar.style.width     = pct + '%';
                    label.textContent   = remaining + 's';
                    if (elapsed < INTERVAL_MS) raf = requestAnimationFrame(tick);
                }
                function start() {
                    if (raf) cancelAnimationFrame(raf);
                    t0  = Date.now();
                    raf = requestAnimationFrame(tick);
                }
                start();
                Wicket.Event.subscribe('/ajax/call/complete', start);
            })();
        """));
    }

    // ── Service lookup ────────────────────────────────────────────────────────

    /**
     * Looks up the {@link ConnectionService} fresh from the application on every
     * call so that no service reference is ever stored in the page's component
     * tree (which Wicket serialises to its page store).
     */
    private ConnectionService connSvc() {
        return ((FixSimulatorApplication) getApplication()).getConnectionService();
    }

    private String currentUser() {
        var session = FixSimulatorSession.get();
        if (session == null) return "?";
        var user = session.getAuthenticatedUser();
        return user != null ? user.username() : "?";
    }

    // ── Form model ────────────────────────────────────────────────────────────

    private static final class NewSessionModel implements Serializable {
        private static final long serialVersionUID = 1L;

        /** Non-null when editing an existing session; null when creating a new one. */
        String  editingSessionId = null;
        String  modalTitle       = "Add FIX Connection";

        String  connectionType = "Initiator";
        String  fixVersion     = "FIX.4.4";
        String  beginString    = "FIX.4.4";
        String  senderCompID   = "";
        String  targetCompID   = "";
        String  host           = "";
        Integer port           = 9876;
        Integer heartbeatSecs  = 30;
        boolean resetOnLogon   = true;

        void resetToDefaults() {
            editingSessionId = null;
            modalTitle       = "Add FIX Connection";
            connectionType   = "Initiator";
            fixVersion       = "FIX.4.4";
            beginString      = "FIX.4.4";
            senderCompID     = "";
            targetCompID     = "";
            host             = "";
            port             = 9876;
            heartbeatSecs    = 30;
            resetOnLogon     = true;
        }

        void populateFrom(SessionDetails s) {
            editingSessionId = s.sessionId();
            modalTitle       = "Edit FIX Connection";
            connectionType   = s.connectionType();
            senderCompID     = s.senderCompID();
            targetCompID     = s.targetCompID();
            heartbeatSecs    = s.heartbeatSecs();
            resetOnLogon     = true;

            // s.fixVersion() is the QuickFIX/J BeginString (e.g. "FIX.4.4" or "FIXT.1.1")
            if (s.fixVersion().startsWith("FIXT")) {
                beginString = "FIXT.1.1";
                fixVersion  = "FIX.5.0SP2";   // best guess; exact app version not stored
            } else {
                beginString = s.fixVersion();
                fixVersion  = s.fixVersion();
            }

            // Parse "host:port" or "0.0.0.0:port"
            String hp = s.hostPort();
            int colonIdx = hp.lastIndexOf(':');
            if (colonIdx >= 0) {
                host = hp.substring(0, colonIdx);
                try { port = Integer.parseInt(hp.substring(colonIdx + 1)); }
                catch (NumberFormatException e) { port = 9876; }
            } else {
                host = hp;
                port = 9876;
            }
        }
    }

    /** Current activity of a session, defaulting to IDLE when the service is absent. */
    private SessionActivity activityOf(String sessionId) {
        ConnectionService cs = connSvc();
        return cs != null ? cs.getSessionActivity(sessionId) : SessionActivity.IDLE;
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
