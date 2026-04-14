package com.example;

import com.example.model.Task;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Wicket session that stores the task list for the duration of the
 * browser session — the same behaviour as the JSF @SessionScoped TaskBean.
 */
public class WicketSession extends WebSession {

    private final List<Task> tasks = new ArrayList<>();

    public WicketSession(Request request) {
        super(request);
        tasks.add(new Task("Design database schema",   "High",   LocalDate.now().plusDays(1)));
        tasks.add(new Task("Write unit tests",         "Medium", LocalDate.now().plusDays(4)));
        tasks.add(new Task("Update project README",    "Low",    LocalDate.now().plusDays(7)));
        tasks.add(new Task("Deploy to staging server", "High",   LocalDate.now().plusDays(2)));
        tasks.add(new Task("Code review PR #42",       "Medium", LocalDate.now().plusDays(3)));
    }

    public List<Task> getTasks() { return tasks; }

    /** Convenience accessor — mirrors Session.get() pattern. */
    public static WicketSession get() {
        return (WicketSession) Session.get();
    }
}
