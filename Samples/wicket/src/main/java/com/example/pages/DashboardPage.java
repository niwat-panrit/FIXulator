package com.example.pages;

import com.example.WicketSession;
import com.example.model.Task;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxCheckBox;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.PageableListView;
import org.apache.wicket.markup.html.navigation.paging.PagingNavigator;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LambdaModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;

import java.util.List;

/**
 * Dashboard page — replicates JSF index.xhtml.
 *
 * Features:
 *  - Four stat-cards (Total, Done, Pending, High-Priority Open)
 *  - Bar chart (tasks by priority) and Pie chart (Done vs Pending) via Chart.js
 *  - Paginated task table (5 rows / page) with AJAX done-checkbox
 *  - Toggling a checkbox updates stat-cards, charts, and the table via AJAX
 */
public class DashboardPage extends BasePage {

    private final WebMarkupContainer statsContainer;
    private final WebMarkupContainer tableContainer;

    public DashboardPage() {

        // ── Stat cards ──────────────────────────────────────────────────────
        statsContainer = new WebMarkupContainer("statsContainer");
        statsContainer.setOutputMarkupId(true);
        statsContainer.add(new Label("totalCount",   () -> WicketSession.get().getTasks().size()));
        statsContainer.add(new Label("doneCount",    () -> WicketSession.get().getTasks().stream().filter(Task::isDone).count()));
        statsContainer.add(new Label("pendingCount", () -> WicketSession.get().getTasks().stream().filter(t -> !t.isDone()).count()));
        statsContainer.add(new Label("highCount",    () -> WicketSession.get().getTasks().stream()
                .filter(t -> "High".equals(t.getPriority()) && !t.isDone()).count()));
        add(statsContainer);

        // ── Task table (paginated, 5 per page) ──────────────────────────────
        tableContainer = new WebMarkupContainer("tableContainer");
        tableContainer.setOutputMarkupId(true);

        IModel<List<Task>> tasksModel = new LoadableDetachableModel<>() {
            @Override
            protected List<Task> load() {
                return WicketSession.get().getTasks();
            }
        };

        PageableListView<Task> listView = new PageableListView<>("tasks", tasksModel, 5) {
            @Override
            protected void populateItem(ListItem<Task> item) {
                Task task = item.getModelObject();

                if (task.isDone()) {
                    item.add(org.apache.wicket.behavior.AttributeAppender.append("class", " row-done"));
                }

                // Done checkbox — AJAX updates stats + charts + table
                IModel<Boolean> doneModel = LambdaModel.of(
                        item.getModel(), Task::isDone, Task::setDone);

                item.add(new AjaxCheckBox("doneCheck", doneModel) {
                    @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        target.add(statsContainer);
                        target.add(tableContainer);
                        // Re-initialize Chart.js with fresh data
                        target.appendJavaScript(buildChartUpdateScript());
                    }
                });

                item.add(new Label("title",    task.getTitle()));
                item.add(new Label("priority", task.getPriority()));
                item.add(new Label("dueDate",  task.getDueDate() != null ? task.getDueDate().toString() : ""));
            }
        };

        tableContainer.add(listView);
        tableContainer.add(new PagingNavigator("pager", listView));
        add(tableContainer);

        add(new BookmarkablePageLink<>("manageTasksLink", TaskManagerPage.class));
    }

    @Override
    protected String getPageTitle() { return "Dashboard"; }

    // ── Chart rendering ──────────────────────────────────────────────────────

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        // Initial chart init on page load
        response.render(OnDomReadyHeaderItem.forScript(buildChartInitScript()));
    }

    /** Builds the JS that creates both Chart.js charts on page load. */
    private String buildChartInitScript() {
        return "window._barChartInst = null; window._pieChartInst = null;\n" +
               buildChartUpdateScript();
    }

    /**
     * Builds the JS that (re-)initialises both charts from current session data.
     * Called both on first load and after every AJAX checkbox toggle.
     */
    private String buildChartUpdateScript() {
        List<Task> tasks = WicketSession.get().getTasks();

        long high   = tasks.stream().filter(t -> "High".equals(t.getPriority())).count();
        long medium = tasks.stream().filter(t -> "Medium".equals(t.getPriority())).count();
        long low    = tasks.stream().filter(t -> "Low".equals(t.getPriority())).count();
        long done    = tasks.stream().filter(Task::isDone).count();
        long pending = tasks.stream().filter(t -> !t.isDone()).count();

        return "(function() {\n" +
               "  if (window._barChartInst)  { window._barChartInst.destroy();  }\n" +
               "  if (window._pieChartInst)  { window._pieChartInst.destroy();  }\n" +
               "  var bCtx = document.getElementById('barChart');\n" +
               "  var pCtx = document.getElementById('pieChart');\n" +
               "  if (!bCtx || !pCtx) return;\n" +
               "  window._barChartInst = new Chart(bCtx, {\n" +
               "    type: 'bar',\n" +
               "    data: {\n" +
               "      labels: ['High','Medium','Low'],\n" +
               "      datasets: [{\n" +
               "        label: 'Tasks by Priority',\n" +
               "        data: [" + high + "," + medium + "," + low + "],\n" +
               "        backgroundColor: ['rgba(239,68,68,.7)','rgba(234,179,8,.7)','rgba(34,197,94,.7)'],\n" +
               "        borderColor:     ['rgba(239,68,68,1)', 'rgba(234,179,8,1)', 'rgba(34,197,94,1)'],\n" +
               "        borderWidth: 1\n" +
               "      }]\n" +
               "    },\n" +
               "    options: { responsive:true, plugins:{ legend:{ display:false } } }\n" +
               "  });\n" +
               "  window._pieChartInst = new Chart(pCtx, {\n" +
               "    type: 'pie',\n" +
               "    data: {\n" +
               "      labels: ['Done','Pending'],\n" +
               "      datasets: [{\n" +
               "        data: [" + done + "," + pending + "],\n" +
               "        backgroundColor: ['rgba(99,102,241,.8)','rgba(156,163,175,.8)']\n" +
               "      }]\n" +
               "    },\n" +
               "    options: { responsive:true }\n" +
               "  });\n" +
               "})();\n";
    }
}
