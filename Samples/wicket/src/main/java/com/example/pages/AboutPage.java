package com.example.pages;

/**
 * Static about page — replicates JSF about.xhtml.
 * No dynamic components needed; the page just extends BasePage for the layout.
 */
public class AboutPage extends BasePage {

    public AboutPage() {
        // no dynamic Wicket components on this page
    }

    @Override
    protected String getPageTitle() { return "About"; }
}
