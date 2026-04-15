package com.npsoftdev.fixsimulator.pages;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.head.CssReferenceHeaderItem;
import org.apache.wicket.markup.head.CssUrlReferenceHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptUrlReferenceHeaderItem;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.request.resource.PackageResourceReference;

public abstract class BasePage extends WebPage {

    public BasePage() {
        super();

        // --- Navbar (dummy values — wired up later) ---
        add(new Label("connName", "OrderRouter-01"));
        add(new Label("connStatus", "CONNECTED")
                .add(AttributeModifier.replace("class", "badge bg-success")));
        add(new Label("txSeq", "42"));
        add(new Label("rxSeq", "38"));
        add(new Link<Void>("connectBtn") {
            @Override public void onClick() { /* TODO: connect/disconnect */ }
        });
        add(new Link<Void>("manageSeqBtn") {
            @Override public void onClick() { /* TODO: manage sequence numbers */ }
        });

        // --- Sidebar navigation links ---
        add(navLink("lnkDashboard",   HomePage.class));
        add(navLink("lnkOrders",      OrdersPage.class));
        add(navLink("lnkTrades",      TradesPage.class));
        add(navLink("lnkRawMessages", RawMessagesPage.class));
        add(navLink("lnkMessageLog",  MessageLogPage.class));
        add(navLink("lnkConnections", ConnectionManagementPage.class));
        add(navLink("lnkUsers",       UserManagementPage.class));
        add(navLink("lnkSystemLogs",  SystemLogsPage.class));
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);

        // Bootstrap 5 CSS — Wicket registers the CDN origin in the CSP style-src
        response.render(CssUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"));

        // Bootstrap Icons CSS
        response.render(CssUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"));

        // App custom styles (co-located with BasePage, served through Wicket's resource pipeline)
        response.render(CssReferenceHeaderItem.forReference(
                new PackageResourceReference(BasePage.class, "app.css")));

        // Bootstrap 5 JS bundle (includes Popper)
        response.render(JavaScriptUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"));
    }

    private BookmarkablePageLink<Void> navLink(String id, Class<? extends WebPage> pageClass) {
        BookmarkablePageLink<Void> link = new BookmarkablePageLink<>(id, pageClass);
        link.add(AttributeModifier.replace("class",
                getClass().equals(pageClass) ? "nav-link active" : "nav-link"));
        return link;
    }
}
