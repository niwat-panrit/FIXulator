package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.FixSimulatorSession;
import com.npsoftdev.fixsimulator.plugin.NavSection;
import com.npsoftdev.fixsimulator.plugin.PluginRegistry;
import com.npsoftdev.fixsimulator.plugin.SimulatorPlugin;
import com.npsoftdev.fixsimulator.service.ConnectionService;
import com.npsoftdev.fixsimulator.service.ConnectionService.SessionDetails;
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
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.request.resource.PackageResourceReference;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public abstract class BasePage extends WebPage {

    public BasePage() {
        super();

        // ── Seq management model + form (added directly to page) ──────────────
        SeqMgmtModel seqModel = new SeqMgmtModel();

        // ── Topbar container — refreshed only when the active session switches ─
        // (avoids tearing down Bootstrap dropdown state on every poll tick)
        WebMarkupContainer topbarNav = new WebMarkupContainer("topbarNav");
        topbarNav.setOutputMarkupId(true);
        add(topbarNav);

        // ── Status area — polled every 2 s to pick up async connect/disconnect
        // handshakes and live sequence-number changes without a manual refresh ──
        WebMarkupContainer connStatusArea = new WebMarkupContainer("connStatusArea");
        connStatusArea.setOutputMarkupId(true);
        connStatusArea.add(new AjaxSelfUpdatingTimerBehavior(Duration.ofSeconds(2)));
        topbarNav.add(connStatusArea);

        // No-session placeholder — visible when no active session is selected
        WebMarkupContainer noSessionBox = new WebMarkupContainer("noSessionBox") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(!isActiveSessionValid());
            }
        };
        connStatusArea.add(noSessionBox);

        // Conn-info box — visible when an active session is selected and known
        WebMarkupContainer connInfoBox = new WebMarkupContainer("connInfoBox") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(isActiveSessionValid());
            }
        };
        connStatusArea.add(connInfoBox);

        // Connection name
        connInfoBox.add(new Label("connName", (IModel<String>) () -> {
            String sid = activeId();
            ConnectionService cs = connSvc();
            return sid != null && cs != null ? cs.getSessionName(sid) : "";
        }));

        // Status badge — text + dynamic CSS class
        Label statusLabel = new Label("connStatus", (IModel<String>) () -> {
            String sid = activeId();
            ConnectionService cs = connSvc();
            return sid != null && cs != null ? cs.getStatus(sid) : "";
        });
        statusLabel.add(new AttributeModifier("class", (IModel<String>) () ->
                statusBadgeCss(activeStatus())));
        connInfoBox.add(statusLabel);

        // Sequence numbers — updated every 2 s by the timer on connStatusArea
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

        // Connect button — visible when session is NOT connected.
        // Refreshes connStatusArea immediately; the timer then picks up the new
        // CONNECTED state once the FIX Logon handshake completes (~1–2 s).
        connInfoBox.add(new AjaxLink<Void>("connectBtn") {
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
        });

        // Disconnect button — visible when session IS connected
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

        // Seq Mgmt button — only available when disconnected
        Form<SeqMgmtModel> seqForm = buildSeqMgmtForm(seqModel, connStatusArea);
        add(seqForm);

        connInfoBox.add(new AjaxLink<Void>("manageSeqBtn") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                // Pre-populate form with current sequence numbers
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
        // Lives in topbarNav (outside connStatusArea) so the Bootstrap dropdown
        // is not disrupted by the 2-second polling timer.
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
                        FixSimulatorSession.get().setActiveSessionId(sd.sessionId());
                        // Refresh whole topbar so the active indicator in the dropdown
                        // and the status area both update together
                        target.add(topbarNav);
                        // Close Bootstrap dropdown
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

        // ── Sidebar navigation (registry-driven) ──────────────────────────────
        PluginRegistry registry =
                ((FixSimulatorApplication) getApplication()).getPluginRegistry();
        add(buildNavList("overviewNav",   registry.getPluginsBySection(NavSection.OVERVIEW)));
        add(buildNavList("monitoringNav", registry.getPluginsBySection(NavSection.MONITORING)));
        add(buildNavList("adminNav",      registry.getPluginsBySection(NavSection.ADMIN)));
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);

        // Bootstrap 5 CSS
        response.render(CssUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"));

        // Bootstrap Icons CSS
        response.render(CssUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"));

        // App custom styles (co-located with BasePage)
        response.render(CssReferenceHeaderItem.forReference(
                new PackageResourceReference(BasePage.class, "app.css")));

        // Bootstrap 5 JS bundle (includes Popper)
        response.render(JavaScriptUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"));
    }

    // ── Seq management form (inside modal) ───────────────────────────────────

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

    /**
     * Lightweight, fully-serializable snapshot of a plugin's nav properties.
     */
    private record NavEntry(
            String label,
            String iconClass,
            Class<? extends BasePage> pageClass
    ) implements Serializable {}

    private ListView<NavEntry> buildNavList(String id, List<SimulatorPlugin> plugins) {
        List<NavEntry> entries = plugins.stream()
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns the {@link ConnectionService} fresh from the application on every call.
     * Never capture this in a closure — always call it inline.
     */
    private ConnectionService connSvc() {
        return ((FixSimulatorApplication) Application.get()).getConnectionService();
    }

    /** Returns the active session ID from the Wicket session. */
    private static String activeId() {
        return FixSimulatorSession.get().getActiveSessionId();
    }

    /** Returns the connection status of the active session, or an empty string if none. */
    private String activeStatus() {
        String sid = activeId();
        ConnectionService cs = connSvc();
        return (sid != null && cs != null) ? cs.getStatus(sid) : "";
    }

    /**
     * Returns {@code true} if an active session is selected and the service knows about it
     * (i.e. it has not been deleted).
     */
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
