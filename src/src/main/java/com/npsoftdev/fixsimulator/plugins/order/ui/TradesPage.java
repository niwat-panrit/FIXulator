package com.npsoftdev.fixsimulator.plugins.order.ui;

import com.npsoftdev.fixsimulator.core.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.core.FixSimulatorSession;
import com.npsoftdev.fixsimulator.plugins.order.api.TradeService;
import org.apache.wicket.Application;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.PageableListView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.npsoftdev.fixsimulator.core.ui.BasePage;
import com.npsoftdev.fixsimulator.plugins.connection.ui.ComposeMessagePanel;

public class TradesPage extends BasePage {

    private static final int PAGE_SIZE = 20;

    public TradesPage() {
        super();

        FilterModel filter = new FilterModel();

        // ── Full filtered list (shared between table and pagination footer) ───
        LoadableDetachableModel<List<Map<Integer, String>>> tradesModel =
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<Map<Integer, String>> load() {
                        String sessionId = FixSimulatorSession.get().getActiveSessionId();
                        if (sessionId == null) return Collections.emptyList();
                        TradeService ts = tradeSvc();
                        if (ts == null) return Collections.emptyList();
                        return ts.listTrades(sessionId).stream()
                                .filter(m -> matchesFilter(m, filter))
                                .collect(Collectors.toList());
                    }
                };

        // ── Pagination footer — declared early so the timer can reference it ─
        WebMarkupContainer pagingFooter = new WebMarkupContainer("pagingFooter");
        pagingFooter.setOutputMarkupId(true);

        // ── Pageable list view ────────────────────────────────────────────────
        PageableListView<Map<Integer, String>> tradeList =
                new PageableListView<Map<Integer, String>>("tradeRows", tradesModel, PAGE_SIZE) {
                    @Override
                    protected void populateItem(ListItem<Map<Integer, String>> item) {
                        Map<Integer, String> t = item.getModelObject();

                        final String execType  = t.getOrDefault(150, "");
                        final String ordStatus = t.getOrDefault(39, "");
                        final String side      = t.getOrDefault(54, "");

                        item.add(new Label("execId",  t.getOrDefault(17, "")));
                        item.add(new Label("clOrdId", t.getOrDefault(11, "")));

                        Label execTypeLabel = new Label("execType", displayExecType(execType));
                        execTypeLabel.add(AttributeModifier.replace("class",
                                "badge " + execTypeBadgeClass(execType)));
                        item.add(execTypeLabel);

                        item.add(new Label("symbol", t.getOrDefault(55, "")));

                        Label sideLabel = new Label("side", displaySide(side));
                        sideLabel.add(AttributeModifier.replace("class", "badge " + sideBadgeClass(side)));
                        item.add(sideLabel);

                        item.add(new Label("lastQty", nvl(t.get(32))));
                        item.add(new Label("lastPx",  nvl(t.get(31))));
                        item.add(new Label("cumQty",  t.getOrDefault(14, "0")));
                        item.add(new Label("avgPx",   nvl(t.get(6))));

                        Label statusLabel = new Label("ordStatus", displayOrdStatus(ordStatus));
                        statusLabel.add(AttributeModifier.replace("class",
                                "badge " + statusBadgeClass(ordStatus)));
                        item.add(statusLabel);

                        item.add(new Label("transactTime", formatFixTime(t.getOrDefault(60, ""), userZoneId())));

                        item.add(AttributeModifier.replace("class", rowClass(execType)));
                    }
                };

        // ── Table body (<tbody>) ──────────────────────────────────────────────
        WebMarkupContainer tableBody = new WebMarkupContainer("tableBody");
        tableBody.setOutputMarkupId(true);

        WebMarkupContainer emptyRow = new WebMarkupContainer("emptyRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(tradesModel.getObject().isEmpty());
            }
        };
        tableBody.add(emptyRow);
        tableBody.add(tradeList);

        // Timer refreshes both the table rows AND the pagination footer
        tableBody.add(new AbstractAjaxTimerBehavior(Duration.ofSeconds(3)) {
            @Override
            protected void onTimer(AjaxRequestTarget target) {
                target.add(tableBody, pagingFooter);
            }
        });

        // ── Pagination footer components ──────────────────────────────────────

        pagingFooter.add(new Label("summary", (IModel<String>) () -> {
            int total = tradesModel.getObject().size();
            if (total == 0) return "No execution reports yet";
            long cur  = tradeList.getCurrentPage();
            long from = cur * PAGE_SIZE + 1;
            long to   = Math.min((cur + 1L) * PAGE_SIZE, total);
            return "Showing " + from + "–" + to + " of " + total + " execution reports";
        }));

        pagingFooter.add(new Label("pageInfo", (IModel<String>) () -> {
            long pageCount = Math.max(1L, tradeList.getPageCount());
            return (tradeList.getCurrentPage() + 1) + " / " + pageCount;
        }));

        // Prev page <li> — adds Bootstrap "disabled" class at the tag level
        WebMarkupContainer prevItem = new WebMarkupContainer("prevItem") {
            @Override
            protected void onComponentTag(ComponentTag tag) {
                super.onComponentTag(tag);
                tag.put("class", "page-item" + (tradeList.getCurrentPage() <= 0 ? " disabled" : ""));
            }
        };
        prevItem.add(new AjaxLink<Void>("prevPage") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                long cur = tradeList.getCurrentPage();
                if (cur > 0) tradeList.setCurrentPage(cur - 1);
                target.add(tableBody, pagingFooter);
            }
        });
        pagingFooter.add(prevItem);

        // Next page <li>
        WebMarkupContainer nextItem = new WebMarkupContainer("nextItem") {
            @Override
            protected void onComponentTag(ComponentTag tag) {
                super.onComponentTag(tag);
                boolean atEnd = tradeList.getCurrentPage() >= tradeList.getPageCount() - 1;
                tag.put("class", "page-item" + (atEnd ? " disabled" : ""));
            }
        };
        nextItem.add(new AjaxLink<Void>("nextPage") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                long cur = tradeList.getCurrentPage();
                if (cur < tradeList.getPageCount() - 1) tradeList.setCurrentPage(cur + 1);
                target.add(tableBody, pagingFooter);
            }
        });
        pagingFooter.add(nextItem);

        // ── Filter form ───────────────────────────────────────────────────────
        Form<FilterModel> filterForm = new Form<>("filterForm",
                new CompoundPropertyModel<>(filter));
        filterForm.add(tableBody);

        addTextFilter(filterForm, "filterExecId",  tradeList, tableBody, pagingFooter);
        addTextFilter(filterForm, "filterClOrdId", tradeList, tableBody, pagingFooter);
        addTextFilter(filterForm, "filterSymbol",  tradeList, tableBody, pagingFooter);
        addDropFilter(filterForm, "filterSide",
                List.of("BUY", "SELL"), tradeList, tableBody, pagingFooter);
        addDropFilter(filterForm, "filterExecType",
                List.of("PartFill", "Fill", "Trade"), tradeList, tableBody, pagingFooter);

        add(filterForm);
        add(pagingFooter);

        // ── Compose panel (offcanvas) ──────────────────────────────────────────
        add(new ComposeMessagePanel("composePanel"));
    }

    // ── Filter helpers ────────────────────────────────────────────────────────

    private static void addTextFilter(Form<?> form, String id, PageableListView<?> list,
                                      WebMarkupContainer tableBody, WebMarkupContainer pagingFooter) {
        TextField<String> tf = new TextField<>(id);
        tf.add(new AjaxFormComponentUpdatingBehavior("input") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                list.setCurrentPage(0);
                target.add(tableBody, pagingFooter);
            }
        });
        form.add(tf);
    }

    private static void addDropFilter(Form<?> form, String id, List<String> choices,
                                      PageableListView<?> list,
                                      WebMarkupContainer tableBody, WebMarkupContainer pagingFooter) {
        DropDownChoice<String> dd = new DropDownChoice<>(id, choices);
        dd.setNullValid(true);
        dd.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                list.setCurrentPage(0);
                target.add(tableBody, pagingFooter);
            }
        });
        form.add(dd);
    }

    private static boolean matchesFilter(Map<Integer, String> t, FilterModel f) {
        if (!blank(f.filterExecId) &&
                !t.getOrDefault(17, "").toLowerCase().contains(f.filterExecId.toLowerCase()))
            return false;
        if (!blank(f.filterClOrdId) &&
                !t.getOrDefault(11, "").toLowerCase().contains(f.filterClOrdId.toLowerCase()))
            return false;
        if (!blank(f.filterSymbol) &&
                !t.getOrDefault(55, "").toLowerCase().contains(f.filterSymbol.toLowerCase()))
            return false;
        if (!blank(f.filterSide) &&
                !displaySide(t.getOrDefault(54, "")).equals(f.filterSide))
            return false;
        if (!blank(f.filterExecType) &&
                !displayExecType(t.getOrDefault(150, "")).equals(f.filterExecType))
            return false;
        return true;
    }

    // ── Service lookup ────────────────────────────────────────────────────────

    private static TradeService tradeSvc() {
        return ((FixSimulatorApplication) Application.get()).getTradeService();
    }

    // ── Display helpers ───────────────────────────────────────────────────────

    private static String displayExecType(String code) {
        return switch (code) {
            case "1" -> "PartFill";
            case "2" -> "Fill";
            case "F" -> "Trade";
            default  -> code;
        };
    }

    private static String execTypeBadgeClass(String code) {
        return switch (code) {
            case "1" -> "bg-warning text-dark";
            case "2", "F" -> "bg-success";
            default  -> "bg-light text-dark border";
        };
    }

    private static String displaySide(String code) {
        return switch (code) {
            case "1" -> "BUY";
            case "2" -> "SELL";
            default  -> code;
        };
    }

    private static String sideBadgeClass(String code) {
        return "2".equals(code) ? "bg-danger" : "bg-success";
    }

    private static String displayOrdStatus(String code) {
        return switch (code) {
            case "0" -> "New";
            case "1" -> "PartFilled";
            case "2" -> "Filled";
            case "3" -> "DoneForDay";
            case "4" -> "Cancelled";
            case "5" -> "Replaced";
            case "8" -> "Rejected";
            default  -> blank(code) ? "—" : code;
        };
    }

    private static String statusBadgeClass(String code) {
        return switch (code) {
            case "0" -> "bg-primary";
            case "1" -> "bg-warning text-dark";
            case "2" -> "bg-success";
            case "3", "4" -> "bg-secondary";
            case "5" -> "bg-info text-dark";
            case "8" -> "bg-danger";
            default  -> "bg-light text-dark border";
        };
    }

    private static String rowClass(String execType) {
        return switch (execType) {
            case "2", "F" -> "table-success";
            case "1"      -> "table-warning";
            default       -> "";
        };
    }

    private static String formatFixTime(String raw, ZoneId tz) {
        if (blank(raw)) return "—";
        try {
            // FIX TransactTime: yyyyMMdd-HH:mm:ss or yyyyMMdd-HH:mm:ss.SSS (UTC)
            DateTimeFormatter fixFmt = raw.length() > 17
                    ? DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC)
                    : DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss").withZone(ZoneOffset.UTC);
            Instant instant = fixFmt.parse(raw, Instant::from);
            return DateTimeFormatter.ofPattern("HH:mm:ss").withZone(tz).format(instant);
        } catch (Exception e) {
            // Fallback: strip date prefix
            int dash = raw.indexOf('-');
            return dash >= 0 ? raw.substring(dash + 1) : raw;
        }
    }

    private static String nvl(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    // ── Model ─────────────────────────────────────────────────────────────────

    static class FilterModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String filterExecId  = "";
        String filterClOrdId = "";
        String filterSymbol  = "";
        String filterSide;
        String filterExecType;
    }
}
