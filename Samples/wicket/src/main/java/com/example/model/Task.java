package com.example.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple task model — identical in structure to the JSF TaskBean.Task inner
 * class, but extracted to a top-level class for cleaner Wicket model handling.
 */
public class Task implements Serializable {

    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private final int    id;
    private String       title;
    private String       priority;   // High / Medium / Low
    private boolean      done;
    private LocalDate    dueDate;

    public Task(String title, String priority, LocalDate dueDate) {
        this.id       = COUNTER.getAndIncrement();
        this.title    = title;
        this.priority = priority;
        this.dueDate  = dueDate;
    }

    /** Copy constructor — used by the edit dialog to avoid mutating the live object. */
    public Task(Task src) {
        this.id       = src.id;
        this.title    = src.title;
        this.priority = src.priority;
        this.done     = src.done;
        this.dueDate  = src.dueDate;
    }

    public int       getId()                        { return id; }
    public String    getTitle()                     { return title; }
    public void      setTitle(String title)         { this.title = title; }
    public String    getPriority()                  { return priority; }
    public void      setPriority(String priority)   { this.priority = priority; }
    public boolean   isDone()                       { return done; }
    public void      setDone(boolean done)          { this.done = done; }
    public LocalDate getDueDate()                   { return dueDate; }
    public void      setDueDate(LocalDate dueDate)  { this.dueDate = dueDate; }
}
