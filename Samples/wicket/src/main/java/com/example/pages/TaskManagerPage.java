package com.example.pages;

import com.example.WicketSession;
import com.example.components.LocalDateTextField;
import com.example.model.Task;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.ajax.markup.html.form.AjaxCheckBox;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.RequiredTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.PageableListView;
import org.apache.wicket.markup.html.navigation.paging.PagingNavigator;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LambdaModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Task Manager page — replicates JSF tasks.xhtml.
 *
 * Features:
 *  - Add new task form (title required, priority dropdown, due date)
 *  - Sortable columns (click header → cycle ASC / DESC)
 *  - Priority filter dropdown
 *  - Title search filter
 *  - Paginated table (8 rows / page)
 *  - Inline AJAX done-checkbox
 *  - Edit dialog (modal overlay, AJAX)
 *  - Delete with JS confirmation prompt
 */
public class TaskManagerPage extends BasePage {

    // ── Sort state ───────────────────────────────────────────────────────────
    private String  sortField  = "dueDate";
    private boolean sortAsc    = true;

    // ── Filter state ─────────────────────────────────────────────────────────
    private String filterPriority = "";
    private String filterTitle    = "";

    // ── Edit state ───────────────────────────────────────────────────────────
    private Task editCopy = null;   // working copy while dialog is open

    // ── Component references for AJAX targeting ──────────────────────────────
    private WebMarkupContainer tableContainer;
    private WebMarkupContainer editModal;

    public TaskManagerPage() {

        // ── Add-task form ────────────────────────────────────────────────────
        Task newTask = new Task("", "Medium", LocalDate.now().plusDays(3));
        Form<Task> addForm = new Form<>("addForm", Model.of(newTask));

        RequiredTextField<String> titleField = new RequiredTextField<>("newTitle",
                new PropertyModel<>(newTask, "title"));
        titleField.setLabel(Model.of("Title"));
        addForm.add(titleField);

        DropDownChoice<String> priorityChoice = new DropDownChoice<>("newPriority",
                new PropertyModel<>(newTask, "priority"),
                Arrays.asList("High", "Medium", "Low"));
        addForm.add(priorityChoice);

        addForm.add(new LocalDateTextField("newDueDate", new PropertyModel<>(newTask, "dueDate")));

        tableContainer = buildTableContainer();
        add(tableContainer);

        addForm.add(new AjaxButton("addBtn", addForm) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                WicketSession.get().getTasks().add(
                        new Task(newTask.getTitle(), newTask.getPriority(), newTask.getDueDate()));
                // Reset form
                newTask.setTitle("");
                newTask.setPriority("Medium");
                newTask.setDueDate(LocalDate.now().plusDays(3));
                target.add(tableContainer);
                success("Task '" + newTask.getTitle() + "' added.");
                target.add(feedback);
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(feedback);
            }
        });

        add(addForm);

        // ── Filter bar ───────────────────────────────────────────────────────
        Form<Void> filterForm = new Form<>("filterForm");

        DropDownChoice<String> priorityFilter = new DropDownChoice<>("filterPriority",
                new PropertyModel<>(this, "filterPriority"),
                Arrays.asList("", "High", "Medium", "Low"),
                new IChoiceRenderer<>() {
                    @Override public Object getDisplayValue(String s) {
                        return s.isEmpty() ? "All Priorities" : s;
                    }
                    @Override public String getIdValue(String s, int i) { return s; }
                    @Override public String getObject(String id,
                            org.apache.wicket.model.IModel<? extends java.util.List<? extends String>> choices) {
                        return id == null ? "" : id;
                    }
                });
        priorityFilter.setNullValid(false);
        priorityFilter.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override protected void onUpdate(AjaxRequestTarget target) {
                target.add(tableContainer);
            }
        });
        filterForm.add(priorityFilter);

        TextField<String> titleFilter = new TextField<>("filterTitle",
                new PropertyModel<>(this, "filterTitle"));
        titleFilter.add(new AjaxFormComponentUpdatingBehavior("input") {
            @Override protected void onUpdate(AjaxRequestTarget target) {
                target.add(tableContainer);
            }
        });
        filterForm.add(titleFilter);

        add(filterForm);

        // ── Edit modal ───────────────────────────────────────────────────────
        editModal = buildEditModal();
        add(editModal);
    }

    // ── Table container (rebuilt on filter/sort/add/delete) ──────────────────

    private WebMarkupContainer buildTableContainer() {
        WebMarkupContainer container = new WebMarkupContainer("tableContainer");
        container.setOutputMarkupId(true);

        IModel<List<Task>> filteredModel = new LoadableDetachableModel<>() {
            @Override
            protected List<Task> load() {
                return getFilteredSortedTasks();
            }
        };

        PageableListView<Task> listView = new PageableListView<>("tasks", filteredModel, 8) {
            @Override
            protected void populateItem(ListItem<Task> item) {
                Task task = item.getModelObject();

                if (task.isDone()) {
                    item.add(AttributeAppender.append("class", " row-done"));
                }

                // Done checkbox
                IModel<Boolean> doneModel = LambdaModel.of(
                        item.getModel(), Task::isDone, Task::setDone);
                item.add(new AjaxCheckBox("doneCheck", doneModel) {
                    @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        target.add(tableContainer);
                    }
                });

                item.add(new Label("title",    task.getTitle()));
                item.add(new Label("priority", task.getPriority()));
                item.add(new Label("dueDate",  task.getDueDate() != null ? task.getDueDate().toString() : ""));

                // Edit button
                item.add(new AjaxLink<Void>("editBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        editCopy = new Task(task);   // work on a copy
                        target.add(editModal);
                        target.appendJavaScript("document.getElementById('editModalOverlay').classList.add('open');");
                    }
                });

                // Delete link — JS confirm before firing AJAX
                AjaxLink<Void> deleteBtn = new AjaxLink<Void>("deleteBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        WicketSession.get().getTasks().removeIf(t -> t.getId() == task.getId());
                        target.add(tableContainer);
                    }
                };
                deleteBtn.add(AttributeAppender.replace("onclick",
                        "if (!confirm('Delete task \\'" + escapeJs(task.getTitle()) + "\\'?')) return false;"));
                item.add(deleteBtn);
            }
        };

        container.add(listView);
        container.add(new PagingNavigator("pager", listView));

        // Sort-header links
        container.add(sortLink("sortByTitle",    "title",    container));
        container.add(sortLink("sortByPriority", "priority", container));
        container.add(sortLink("sortByDueDate",  "dueDate",  container));

        return container;
    }

    // ── Edit modal ────────────────────────────────────────────────────────────

    private WebMarkupContainer buildEditModal() {
        WebMarkupContainer modal = new WebMarkupContainer("editModal");
        modal.setOutputMarkupId(true);

        // The form inside the modal
        Form<Void> editForm = new Form<>("editForm");

        TextField<String> editTitle = new TextField<>("editTitle",
                new PropertyModel<>(this, "editCopy.title"));
        editForm.add(editTitle);

        DropDownChoice<String> editPriority = new DropDownChoice<>("editPriority",
                new PropertyModel<>(this, "editCopy.priority"),
                Arrays.asList("High", "Medium", "Low"));
        editForm.add(editPriority);

        editForm.add(new LocalDateTextField("editDueDate",
                LambdaModel.of(
                        () -> editCopy != null ? editCopy.getDueDate() : null,
                        date -> { if (editCopy != null) editCopy.setDueDate(date); })));

        // Save button
        editForm.add(new AjaxButton("saveEditBtn", editForm) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                if (editCopy != null) {
                    // Apply copy back to the real task in the session list
                    WicketSession.get().getTasks().stream()
                            .filter(t -> t.getId() == editCopy.getId())
                            .findFirst()
                            .ifPresent(t -> {
                                t.setTitle(editCopy.getTitle());
                                t.setPriority(editCopy.getPriority());
                                t.setDueDate(editCopy.getDueDate());
                            });
                    editCopy = null;
                }
                target.add(editModal);
                target.add(tableContainer);
                target.appendJavaScript("document.getElementById('editModalOverlay').classList.remove('open');");
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(feedback);
            }
        });

        // Cancel button
        editForm.add(new AjaxLink<Void>("cancelEditBtn") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                editCopy = null;
                target.add(editModal);
                target.appendJavaScript("document.getElementById('editModalOverlay').classList.remove('open');");
            }
        });

        modal.add(editForm);
        return modal;
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

    private AjaxLink<Void> sortLink(String wicketId, String field, WebMarkupContainer container) {
        return new AjaxLink<Void>(wicketId) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                if (field.equals(sortField)) {
                    sortAsc = !sortAsc;
                } else {
                    sortField = field;
                    sortAsc   = true;
                }
                target.add(container);
            }
        };
    }

    private List<Task> getFilteredSortedTasks() {
        var stream = WicketSession.get().getTasks().stream();

        if (filterPriority != null && !filterPriority.isBlank()) {
            stream = stream.filter(t -> filterPriority.equals(t.getPriority()));
        }
        if (filterTitle != null && !filterTitle.isBlank()) {
            String q = filterTitle.toLowerCase();
            stream = stream.filter(t -> t.getTitle().toLowerCase().contains(q));
        }

        Comparator<Task> cmp = switch (sortField) {
            case "title"    -> Comparator.comparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER);
            case "priority" -> Comparator.comparingInt(t -> priorityRank(t.getPriority()));
            default          -> Comparator.comparing(t -> t.getDueDate() != null ? t.getDueDate()
                                                          : LocalDate.MAX);
        };

        if (!sortAsc) cmp = cmp.reversed();
        return stream.sorted(cmp).collect(Collectors.toList());
    }

    private static int priorityRank(String p) {
        return switch (p) { case "High" -> 0; case "Medium" -> 1; default -> 2; };
    }

    private static String escapeJs(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'");
    }

    @Override
    protected String getPageTitle() { return "Task Manager"; }
}
