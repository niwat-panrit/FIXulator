package com.example;

import com.example.pages.AboutPage;
import com.example.pages.DashboardPage;
import com.example.pages.TaskManagerPage;
import org.apache.wicket.Page;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;

/**
 * Entry point for the Wicket application.
 *
 * getHomePage() declares the default page; mountPage() gives clean URLs so
 * the browser shows /dashboard, /tasks and /about instead of Wicket's default
 * bookmarkable-page query strings.
 */
public class WicketApplication extends WebApplication {

    @Override
    public Class<? extends Page> getHomePage() {
        return DashboardPage.class;
    }

    @Override
    public void init() {
        super.init();
        mountPage("/dashboard", DashboardPage.class);
        mountPage("/tasks",     TaskManagerPage.class);
        mountPage("/about",     AboutPage.class);
    }

    @Override
    public Session newSession(Request request, Response response) {
        return new WicketSession(request);
    }
}
