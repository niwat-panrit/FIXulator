package com.npsoftdev.fixsimulator.core.ui;

import com.npsoftdev.fixsimulator.core.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.core.FixSimulatorSession;
import com.npsoftdev.fixsimulator.core.plugin.NavSection;
import com.npsoftdev.fixsimulator.core.plugin.PluginRegistry;
import com.npsoftdev.fixsimulator.core.plugin.SimulatorPlugin;
import com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService;
import com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.SessionDetails;
import com.npsoftdev.fixsimulator.plugins.user.api.AuthService;
import com.npsoftdev.fixsimulator.plugins.user.api.Permission;
import com.npsoftdev.fixsimulator.plugins.user.api.RememberMeService;
import com.npsoftdev.fixsimulator.plugins.user.api.User;
import com.npsoftdev.fixsimulator.plugins.user.api.UserPreferencesService;
import jakarta.servlet.http.Cookie;
import org.apache.wicket.request.http.WebRequest;
import org.apache.wicket.request.http.WebResponse;
import java.time.ZoneId;
import org.apache.wicket.Application;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.head.CssReferenceHeaderItem;
import org.apache.wicket.markup.head.CssUrlReferenceHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptUrlReferenceHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.request.resource.PackageResourceReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import com.npsoftdev.fixsimulator.plugins.user.ui.LoginPage;

public abstract class BasePage extends WebPage {

    private static final Logger log = LoggerFactory.getLogger(BasePage.class);

    public BasePage() {
        super();

        // ── Seq management model + form (added directly to page) ──────────────
        SeqMgmtModel seqModel = new SeqMgmtModel();

        // ── Topbar container — refreshed only when the active session switches ─
        WebMarkupContainer topbarNav = new WebMarkupContainer("topbarNav");
        topbarNav.setOutputMarkupId(true);
        add(topbarNav);

        // ── User menu (must be children of topbarNav to match HTML hierarchy) ──
        topbarNav.add(new Label("currentUsername", (IModel<String>) () -> {
            User u = FixSimulatorSession.get().getAuthenticatedUser();
            return u != null ? u.username() : "?";
        }));
        topbarNav.add(new Label("currentUserDisplayName", (IModel<String>) () -> {
            User u = FixSimulatorSession.get().getAuthenticatedUser();
            return u != null ? u.displayName() : "";
        }));
        topbarNav.add(new Label("userRolesBadge", (IModel<String>) () -> {
            User u = FixSimulatorSession.get().getAuthenticatedUser();
            return u != null && !u.roles().isEmpty() ? String.join(", ", u.roles()) : "No roles";
        }));
        topbarNav.add(new Link<Void>("signOutLink") {
            @Override
            public void onClick() {
                FixSimulatorSession sess = FixSimulatorSession.get();
                User user = sess.getAuthenticatedUser();
                if (user != null) {
                    AuthService auth = app().getAuthService();
                    if (auth != null) {
                        auth.unregisterSession(user.username(), sess.getId());
                    }
                }

                // Delete the persistent remember-me token so the cookie cannot be replayed
                RememberMeService rms = app().getRememberMeService();
                if (rms != null) {
                    WebRequest req = (WebRequest) getRequestCycle().getRequest();
                    Cookie rmCookie = req.getCookie(FixSimulatorApplication.REMEMBER_ME_COOKIE);
                    if (rmCookie != null) {
                        rms.deleteToken(rmCookie.getValue());
                        Cookie clear = new Cookie(FixSimulatorApplication.REMEMBER_ME_COOKIE, "");
                        clear.setMaxAge(0);
                        clear.setPath("/");
                        ((WebResponse) getRequestCycle().getResponse()).addCookie(clear);
                    }
                }

                sess.signOut();
                sess.invalidate();
                setResponsePage(LoginPage.class);
            }
        });

        // ── Status area — polled every 2 s ────────────────────────────────────
        WebMarkupContainer connStatusArea = new WebMarkupContainer("connStatusArea");
        connStatusArea.setOutputMarkupId(true);
        connStatusArea.add(new AjaxSelfUpdatingTimerBehavior(Duration.ofSeconds(2)));
        topbarNav.add(connStatusArea);

        WebMarkupContainer noSessionBox = new WebMarkupContainer("noSessionBox") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(!isActiveSessionValid());
            }
        };
        connStatusArea.add(noSessionBox);

        WebMarkupContainer connInfoBox = new WebMarkupContainer("connInfoBox") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(isActiveSessionValid());
            }
        };
        connStatusArea.add(connInfoBox);

        connInfoBox.add(new Label("connName", (IModel<String>) () -> {
            String sid = activeId();
            ConnectionService cs = connSvc();
            return sid != null && cs != null ? cs.getSessionName(sid) : "";
        }));

        Label statusLabel = new Label("connStatus", (IModel<String>) () -> {
            String sid = activeId();
            ConnectionService cs = connSvc();
            return sid != null && cs != null ? cs.getStatus(sid) : "";
        });
        statusLabel.add(new AttributeModifier("class", (IModel<String>) () ->
                statusBadgeCss(activeStatus())));
        connInfoBox.add(statusLabel);

        connInfoBox.add(new Label("txSeq", (IModel<String>) () -> {
            String sid = activeId();
            ConnectionService cs = connSvc();
            return sid != null && cs != null ? String.valueOf(cs.getTxSequence(sid)) : "-";
        }));
        connInfoBox.add(new Label("rxSeq", (IModel<String>) () -> {
            String sid = activeId();
            ConnectionService cs = connSvc();
            return sid != null && cs != null ? String.valueOf(cs.getRxSequence(sid)) : "-";
        }));

        AjaxLink<Void> connectBtn = new AjaxLink<>("connectBtn") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                String sid = activeId();
                ConnectionService cs = connSvc();
                if (sid != null && cs != null) cs.connect(sid);
                target.add(connStatusArea);
            }

            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(isActiveSessionValid() && !"CONNECTED".equals(activeStatus()));
            }
        };
        // An initiator dials out; an acceptor waits for the counterparty. Label the
        // button after what it actually does for this session.
        connectBtn.add(new Label("connectBtnLabel",
                (IModel<String>) () -> activeSessionIsAcceptor() ? "Listen" : "Connect"));
        connectBtn.add(new WebMarkupContainer("connectBtnIcon")
                .add(new AttributeModifier("class", (IModel<String>) () ->
                        activeSessionIsAcceptor()
                                ? "bi bi-broadcast-pin me-1"
                                : "bi bi-plug-fill me-1")));
        connInfoBox.add(connectBtn);

        connInfoBox.add(new AjaxLink<Void>("disconnectBtn") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                String sid = activeId();
                ConnectionService cs = connSvc();
                if (sid != null && cs != null) cs.disconnect(sid);
                target.add(connStatusArea);
            }

            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible("CONNECTED".equals(activeStatus()));
            }
        });

        Form<SeqMgmtModel> seqForm = buildSeqMgmtForm(seqModel, connStatusArea);
        add(seqForm);

        connInfoBox.add(new AjaxLink<Void>("manageSeqBtn") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                String sid = activeId();
                ConnectionService cs = connSvc();
                if (sid != null && cs != null) {
                    seqModel.txSeq = cs.getTxSequence(sid);
                    seqModel.rxSeq = cs.getRxSequence(sid);
                }
                target.add(seqForm);
                target.appendJavaScript(
                        "new bootstrap.Modal(document.getElementById('seqModal')).show();");
            }

            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(isActiveSessionValid() && !"CONNECTED".equals(activeStatus()));
            }
        });

        // ── Switch Connection dropdown ─────────────────────────────────────────
        topbarNav.add(new ListView<SessionDetails>("switchConnectionList",
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<SessionDetails> load() {
                        ConnectionService cs = connSvc();
                        return cs != null ? cs.listSessions() : Collections.emptyList();
                    }
                }) {
            @Override
            protected void populateItem(ListItem<SessionDetails> item) {
                SessionDetails sd = item.getModelObject();
                String currentActive = FixSimulatorSession.get().getActiveSessionId();
                boolean isActive = sd.sessionId().equals(currentActive);

                AjaxLink<Void> link = new AjaxLink<>("switchLink") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        String previousSessionId = FixSimulatorSession.get().getActiveSessionId();
                        FixSimulatorSession.get().setActiveSessionId(sd.sessionId());
                        User u = FixSimulatorSession.get().getAuthenticatedUser();
                        log.info("Active FIX session switched: user='{}' from='{}' to='{}'",
                                u != null ? u.username() : "unknown", previousSessionId, sd.sessionId());

                        // Persist the selection so it survives app restarts
                        if (u != null) {
                            UserPreferencesService prefs = app().getUserPreferencesService();
                            if (prefs != null) prefs.setLastActiveSession(u.username(), sd.sessionId());
                        }
                        target.add(topbarNav);
                        onSessionSwitched(target);
                        target.appendJavaScript(
                                "document.querySelectorAll('.dropdown-menu.show').forEach(function(m){" +
                                "  m.classList.remove('show');" +
                                "});" +
                                "document.querySelectorAll('[data-bs-toggle=\"dropdown\"]').forEach(function(b){" +
                                "  b.classList.remove('show'); b.setAttribute('aria-expanded','false');" +
                                "});");
                    }
                };

                String dotCss = switch (sd.status()) {
                    case "CONNECTED"    -> "bi bi-circle-fill text-success me-2";
                    case "DISCONNECTED" -> "bi bi-circle-fill text-danger me-2";
                    default             -> "bi bi-circle-fill text-warning me-2";
                };
                WebMarkupContainer dot = new WebMarkupContainer("statusDot");
                dot.add(AttributeModifier.replace("class", dotCss + " status-dot"));
                link.add(dot);
                link.add(new Label("sessionName", sd.name()));
                link.add(AttributeModifier.replace("class",
                        isActive ? "dropdown-item active" : "dropdown-item"));
                item.add(link);
            }
        });

        // ── "Manage Connections" footer link — hidden when user lacks permission ─
        topbarNav.add(new WebMarkupContainer("manageConnectionsItem") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                User u = FixSimulatorSession.get().getAuthenticatedUser();
                AuthService auth = app().getAuthService();
                setVisible(auth != null
                        && auth.hasPermission(u, Permission.VIEW_MANAGE_FIX_CONNECTIONS));
            }
        });

        // ── Sidebar navigation (registry-driven, permission-filtered) ──────────
        PluginRegistry registry = app().getPluginRegistry();
        add(buildNavList("overviewNav",   registry.getPluginsBySection(NavSection.OVERVIEW)));
        add(buildNavList("monitoringNav", registry.getPluginsBySection(NavSection.MONITORING)));
        add(buildNavList("adminNav",      registry.getPluginsBySection(NavSection.ADMIN)));
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);

        response.render(CssUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"));
        response.render(CssUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"));
        response.render(CssReferenceHeaderItem.forReference(
                new PackageResourceReference(BasePage.class, "app.css")));
        response.render(JavaScriptUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"));
    }

    // ── Seq management form ───────────────────────────────────────────────────

    private Form<SeqMgmtModel> buildSeqMgmtForm(SeqMgmtModel model,
                                                  WebMarkupContainer connStatusArea) {
        Form<SeqMgmtModel> form = new Form<>("seqForm", new CompoundPropertyModel<>(model));
        form.setOutputMarkupId(true);

        form.add(new NumberTextField<>("txSeq", Integer.class).setMinimum(1).setRequired(true));
        form.add(new NumberTextField<>("rxSeq", Integer.class).setMinimum(1).setRequired(true));

        form.add(new AjaxButton("applySeqBtn", form) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                String sid = activeId();
                ConnectionService cs = connSvc();
                if (sid != null && cs != null) {
                    if (model.txSeq != null && model.txSeq >= 1) cs.setTxSequence(sid, model.txSeq);
                    if (model.rxSeq != null && model.rxSeq >= 1) cs.setRxSequence(sid, model.rxSeq);
                }
                target.add(connStatusArea);
                target.appendJavaScript(
                        "bootstrap.Modal.getInstance(document.getElementById('seqModal')).hide();");
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(form);
            }
        });

        return form;
    }

    // ── Nav list builder ─────────────────────────────────────────────────────

    private record NavEntry(
            String label,
            String iconClass,
            Class<? extends BasePage> pageClass
    ) implements Serializable {}

    private ListView<NavEntry> buildNavList(String id, List<SimulatorPlugin> plugins) {
        User currentUser  = FixSimulatorSession.get().getAuthenticatedUser();
        AuthService auth  = app().getAuthService();

        List<NavEntry> entries = plugins.stream()
                .filter(p -> p.getPageClass() != null)
                .filter(p -> {
                    Permission req = PagePermissions.forPage(p.getPageClass());
                    if (req == null) return true;   // no restriction
                    if (auth == null) return false;  // auth not yet ready
                    return auth.hasPermission(currentUser, req);
                })
                .map(p -> new NavEntry(p.getLabel(), p.getIconClass(), p.getPageClass()))
                .toList();

        return new ListView<>(id, entries) {
            @Override
            protected void populateItem(ListItem<NavEntry> item) {
                NavEntry entry = item.getModelObject();

                BookmarkablePageLink<Void> link =
                        new BookmarkablePageLink<>("navLink", entry.pageClass());
                link.add(AttributeModifier.replace("class",
                        BasePage.this.getClass().equals(entry.pageClass())
                                ? "nav-link active"
                                : "nav-link"));

                WebMarkupContainer icon = new WebMarkupContainer("navIcon");
                icon.add(AttributeModifier.replace("class", "bi " + entry.iconClass()));
                link.add(icon);

                link.add(new Label("navLabel", entry.label()));
                item.add(link);
            }
        };
    }

    /**
     * Called after the active FIX session has been changed via the topbar switcher.
     * Subclasses can override to refresh session-dependent components via AJAX.
     */
    protected void onSessionSwitched(AjaxRequestTarget target) {
        // no-op by default
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    protected FixSimulatorApplication app() {
        return (FixSimulatorApplication) Application.get();
    }

    /**
     * Returns the IANA {@link ZoneId} for the currently authenticated user.
     * Falls back to UTC when no user is signed in or the stored timezone is
     * absent / unrecognised.
     */
    protected ZoneId userZoneId() {
        User u = FixSimulatorSession.get().getAuthenticatedUser();
        if (u != null && u.timezone() != null && !u.timezone().isBlank()) {
            try { return ZoneId.of(u.timezone()); } catch (Exception ignored) {}
        }
        return ZoneId.of("UTC");
    }

    private ConnectionService connSvc() {
        return app().getConnectionService();
    }

    private static String activeId() {
        return FixSimulatorSession.get().getActiveSessionId();
    }

    private String activeStatus() {
        String sid = activeId();
        ConnectionService cs = connSvc();
        return (sid != null && cs != null) ? cs.getStatus(sid) : "";
    }

    /** Whether the active session listens for a counterparty rather than dialling out. */
    private boolean activeSessionIsAcceptor() {
        String sid = activeId();
        ConnectionService cs = connSvc();
        return sid != null && cs != null && "Acceptor".equalsIgnoreCase(cs.getConnectionType(sid));
    }

    private boolean isActiveSessionValid() {
        String sid = activeId();
        if (sid == null) return false;
        ConnectionService cs = connSvc();
        if (cs == null) return false;
        return !"UNKNOWN".equals(cs.getStatus(sid));
    }

    private static String statusBadgeCss(String status) {
        return switch (status) {
            case "CONNECTED"    -> "badge bg-success";
            case "DISCONNECTED" -> "badge bg-danger";
            default             -> "badge bg-warning text-dark";
        };
    }

    // ── Seq mgmt form model ───────────────────────────────────────────────────

    private static final class SeqMgmtModel implements Serializable {
        private static final long serialVersionUID = 1L;
        Integer txSeq = 1;
        Integer rxSeq = 1;
    }
}
