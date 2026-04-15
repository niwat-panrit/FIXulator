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

    private ListView<SimulatorPlugin> buildNavList(String id, List<SimulatorPlugin> plugins) {
        return new ListView<>(id, plugins) {
            @Override
            protected void populateItem(ListItem<SimulatorPlugin> item) {
                SimulatorPlugin plugin = item.getModelObject();

                BookmarkablePageLink<Void> link =
                        new BookmarkablePageLink<>("navLink", plugin.getPageClass());
                link.add(AttributeModifier.replace("class",
                        BasePage.this.getClass().equals(plugin.getPageClass())
                                ? "nav-link active"
                                : "nav-link"));

                WebMarkupContainer icon = new WebMarkupContainer("navIcon");
                icon.add(AttributeModifier.replace("class", "bi " + plugin.getIconClass()));
                link.add(icon);

                link.add(new Label("navLabel", plugin.getLabel()));
                item.add(link);
            }
        };
    }
}
