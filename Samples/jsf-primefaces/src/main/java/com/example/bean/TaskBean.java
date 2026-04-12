package com.example.bean;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing bean for the Task Manager page.
 *
 * Scope  : SessionScoped — the task list persists across requests in the
 *          same browser session, which is typical for small in-memory demos.
 * No JS  : All interactivity (AJAX updates, dialogs, table ops) is driven
 *          by PrimeFaces components talking to this bean via EL expressions.
 */
@Named
@SessionScoped
public class TaskBean implements Serializable {

    // ── Inner model ────────────────────────────────────────────────────────

    public static class Task implements Serializable {
        private static int counter = 1;

        private final int id;
        private String title;
        private String priority;   // High / Medium / Low
        private boolean done;
        private LocalDate dueDate;

        public Task(String title, String priority, LocalDate dueDate) {
            this.id       = counter++;
            this.title    = title;
            this.priority = priority;
            this.dueDate  = dueDate;
        }

        // Getters & setters
        public int       getId()                       { return id; }
        public String    getTitle()                    { return title; }
        public void      setTitle(String title)        { this.title = title; }
        public String    getPriority()                 { return priority; }
        public void      setPriority(String priority)  { this.priority = priority; }
        public boolean   isDone()                      { return done; }
        public void      setDone(boolean done)         { this.done = done; }
        public LocalDate getDueDate()                  { return dueDate; }
        public void      setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    }

    // ── State ──────────────────────────────────────────────────────────────

    private List<Task>   tasks;
    private Task         newTask  = new Task("", "Medium", LocalDate.now().plusDays(3));
    private Task         selected;

    private String filterPriority;   // bound to the filter dropdown

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        tasks = new ArrayList<>();
        tasks.add(new Task("Design database schema",    "High",   LocalDate.now().plusDays(1)));
        tasks.add(new Task("Write unit tests",          "Medium", LocalDate.now().plusDays(4)));
        tasks.add(new Task("Update project README",     "Low",    LocalDate.now().plusDays(7)));
        tasks.add(new Task("Deploy to staging server",  "High",   LocalDate.now().plusDays(2)));
        tasks.add(new Task("Code review PR #42",        "Medium", LocalDate.now().plusDays(3)));
    }

    // ── Actions ────────────────────────────────────────────────────────────

    public void addTask() {
        if (newTask.getTitle() == null || newTask.getTitle().isBlank()) return;
        tasks.add(new Task(newTask.getTitle(), newTask.getPriority(), newTask.getDueDate()));
        newTask = new Task("", "Medium", LocalDate.now().plusDays(3)); // reset form
    }

    public void deleteTask(Task task) {
        tasks.remove(task);
    }

    public void selectForEdit(Task task) {
        this.selected = task;
    }

    public void saveEdit() {
        // selected is already the same object in the list — nothing extra needed.
        selected = null;
    }

    public void cancelEdit() {
        selected = null;
    }

    // ── Statistics helpers (used in dashboard cards) ───────────────────────

    public long getTotalCount()    { return tasks.size(); }
    public long getDoneCount()     { return tasks.stream().filter(Task::isDone).count(); }
    public long getPendingCount()  { return tasks.stream().filter(t -> !t.isDone()).count(); }
    public long getHighCount()     { return tasks.stream().filter(t -> "High".equals(t.getPriority()) && !t.isDone()).count(); }

    // ── Accessors ──────────────────────────────────────────────────────────

    public List<Task> getTasks()          { return tasks; }
    public Task       getNewTask()        { return newTask; }
    public Task       getSelected()       { return selected; }
    public String     getFilterPriority() { return filterPriority; }
    public void       setFilterPriority(String f) { this.filterPriority = f; }

    public List<String> getPriorityOptions() {
        return List.of("High", "Medium", "Low");
    }
}
