package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.plugin.NavSection;
import com.npsoftdev.fixsimulator.plugin.PluginRegistry;
import com.npsoftdev.fixsimulator.plugin.SimulatorPlugin;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.head.CssReferenceHeaderItem;
import org.apache.wicket.markup.head.CssUrlReferenceHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptUrlReferenceHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.request.resource.PackageResourceReference;

import java.io.Serializable;
import java.util.List;

public abstract class BasePage extends WebPage {

    public BasePage() {
        super();

        // --- Navbar (dummy values — wired up later via ConnectionService) ---
        add(new Label("connName", "OrderRouter-01"));
        add(new Label("connStatus", "CONNECTED")
                .add(AttributeModifier.replace("class", "badge bg-success")));
        add(new Label("txSeq", "42"));
        add(new Label("rxSeq", "38"));
        add(new Link<Void>("connectBtn") {
            @Override public void onClick() { /* TODO: delegate to ConnectionService */ }
        });
        add(new Link<Void>("manageSeqBtn") {
            @Override public void onClick() { /* TODO: open sequence management dialog */ }
        });

        // --- Sidebar navigation (registry-driven) ---
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

    // -------------------------------------------------------------------------

    /**
     * Lightweight, fully-serializable snapshot of a plugin's nav properties.
     * Keeping this separate from {@link SimulatorPlugin} ensures the ListView
     * model never holds a reference to live plugin objects (which in turn hold
     * QuickFIX/J state that is not serializable).
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
}
