package com.example.pages;

import org.apache.wicket.markup.head.CssReferenceHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptUrlReferenceHeaderItem;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.request.resource.PackageResourceReference;

/**
 * Master layout page.
 *
 * Wicket page inheritance mirrors JSF Facelets template composition:
 *   - BasePage.html contains the chrome (navbar, footer) and a <wicket:child/>
 *     placeholder where subclass HTML is injected.
 *   - Each concrete page extends this class and its HTML wraps its content in
 *     <wicket:extend>.
 *
 * Chart.js is loaded once here so every page that embeds charts can use it.
 */
public abstract class BasePage extends WebPage {

    protected final FeedbackPanel feedback;

    public BasePage() {
        add(new Label("pageTitle", "Wicket Demo — " + getPageTitle()));
        add(new BookmarkablePageLink<>("navDashboard", DashboardPage.class));
        add(new BookmarkablePageLink<>("navTasks",     TaskManagerPage.class));
        add(new BookmarkablePageLink<>("navAbout",     AboutPage.class));

        feedback = new FeedbackPanel("feedback");
        feedback.setOutputMarkupId(true);
        add(feedback);
    }

    /** Subclasses return the browser-tab / topbar title. */
    protected abstract String getPageTitle();

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        // App stylesheet co-located with BasePage
        response.render(CssReferenceHeaderItem.forReference(
                new PackageResourceReference(BasePage.class, "app.css")));
        // Chart.js — loaded once in the base so DashboardPage can use it
        response.render(JavaScriptUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/chart.js@4.4.3/dist/chart.umd.min.js"));
    }
}
