package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.FixSimulatorSession;
import com.npsoftdev.fixsimulator.service.ConnectionService;
import com.npsoftdev.fixsimulator.service.MessageLogService;
import com.npsoftdev.fixsimulator.service.MessageLogService.LogEntry;
import org.apache.wicket.Application;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptReferenceHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.PageableListView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.request.resource.PackageResourceReference;

import java.io.Serializable;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FixActivityPage extends BasePage {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final int PAGE_SIZE = 50;

    // ── Entry type ────────────────────────────────────────────────────────────

    enum EntryType { FIX, APP }

    record ActivityRow(
            EntryType type,
            String    timeStr,
            String    direction,    // "OUT" | "IN" | "" (APP rows)
            String    sessionName,
            String    seqNum,
            String    msgType,
            String    msgTypeName,
            String    sender,
            String    target,
            String    summary,
            String    rawMessage,
            String    appIconClass, // full "bi-xxx" class (APP rows only)
            String    appIconColor  // "text-*" class (APP rows only)
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    // ── Filter model ──────────────────────────────────────────────────────────

    static final class FilterModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String  direction      = "All";
        boolean hideHeartbeats = false;
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public FixActivityPage() {
        super();
        FixSimulatorSession sess = FixSimulatorSession.get();
        FilterModel filter = new FilterModel();
        filter.direction      = sess.getActivityDirection();
        filter.hideHeartbeats = sess.isActivityHideHeartbeats();

        // ── Data model ─────────────────────────────────────────────────────────
        LoadableDetachableModel<List<ActivityRow>> rowsModel =
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<ActivityRow> load() {
                        String sessionId = FixSimulatorSession.get().getActiveSessionId();
                        if (sessionId == null) return Collections.emptyList();
                        MessageLogService mls = msgLogSvc();
                        if (mls == null) return Collections.emptyList();

                        String connName = connName(sessionId);
                        List<LogEntry> messages = mls.getMessages(sessionId); // newest-first

                        List<ActivityRow> rows = new ArrayList<>(messages.size() * 2);
                        for (LogEntry entry : messages) {
                            String timeStr = TIME_FMT.format(entry.timestamp());

                            // System annotations (cache restore, etc.) — APP row only
                            if (entry.direction() == MessageLogService.Direction.SYSTEM) {
                                if ("All".equals(filter.direction)) {
                                    rows.add(new ActivityRow(
                                            EntryType.APP, timeStr, "", connName,
                                            "\u2014", "", entry.msgType(), "\u2014", "\u2014",
                                            entry.rawMessage(), entry.rawMessage(),
                                            "bi-arrow-repeat", "text-info"));
                                }
                                continue;
                            }

                            Map<String, String> tags = parseTags(entry.rawMessage());
                            String msgType = entry.msgType();
                            String dir = entry.direction() == MessageLogService.Direction.SENT
                                    ? "OUT" : "IN";

                            // Apply filters
                            if (filter.hideHeartbeats && "0".equals(msgType)) continue;
                            if ("Sent".equals(filter.direction)     && !"OUT".equals(dir)) continue;
                            if ("Received".equals(filter.direction) && !"IN".equals(dir))  continue;

                            // FIX message row
                            rows.add(new ActivityRow(
                                    EntryType.FIX, timeStr, dir, connName,
                                    tags.getOrDefault("34", ""),
                                    msgType, msgTypeName(msgType),
                                    tags.getOrDefault("49", ""),
                                    tags.getOrDefault("56", ""),
                                    buildSummary(msgType, tags),
                                    entry.rawMessage(),
                                    null, null));

                            // App-level annotation row (only in "All" direction mode)
                            if ("All".equals(filter.direction)) {
                                ActivityRow app = buildAppRow(
                                        msgType, tags, timeStr, connName, entry.rawMessage());
                                if (app != null) rows.add(app);
                            }
                        }

                        // Service returns newest-first; reverse to oldest-at-top for reading
                        Collections.reverse(rows);
                        return rows;
                    }
                };

        // ── Pagination footer ──────────────────────────────────────────────────
        WebMarkupContainer pagingFooter = new WebMarkupContainer("pagingFooter");
        pagingFooter.setOutputMarkupId(true);

        // ── Pageable list view ─────────────────────────────────────────────────
        PageableListView<ActivityRow> activityList =
                new PageableListView<ActivityRow>("activityRows", rowsModel, PAGE_SIZE) {
                    @Override
                    protected void populateItem(ListItem<ActivityRow> item) {
                        ActivityRow row = item.getModelObject();
                        boolean isApp = row.type() == EntryType.APP;

                        // Row background
                        item.add(AttributeModifier.replace("class",
                                isApp ? "activity-app-row" : ""));

                        // Time
                        item.add(new Label("time", row.timeStr()));

                        // Direction label / APP badge
                        Label dirLabel = new Label("dirLabel",
                                isApp ? "APP"
                                      : ("OUT".equals(row.direction()) ? "\u25B6 OUT" : "\u25C4 IN"));
                        dirLabel.add(AttributeModifier.replace("class",
                                isApp ? "badge bg-secondary"
                                      : ("OUT".equals(row.direction()) ? "dir-out" : "dir-in")));
                        item.add(dirLabel);

                        // Connection
                        item.add(new Label("connection", isApp ? "" : row.sessionName()));

                        // Sequence number
                        item.add(new Label("seqNum", row.seqNum()));

                        // Type — FIX badge+name container OR app category container
                        WebMarkupContainer fixTypeBadge = new WebMarkupContainer("fixTypeBadge");
                        fixTypeBadge.setVisible(!isApp);
                        fixTypeBadge.add(new Label("typeBadge", row.msgType()));
                        fixTypeBadge.add(new Label("typeName",  row.msgTypeName()));
                        item.add(fixTypeBadge);

                        WebMarkupContainer appTypeBadge = new WebMarkupContainer("appTypeBadge");
                        appTypeBadge.setVisible(isApp);
                        appTypeBadge.add(new Label("appTypeName", row.msgTypeName()));
                        item.add(appTypeBadge);

                        // Sender / Target
                        item.add(new Label("sender", isApp ? "" : row.sender()));
                        item.add(new Label("target", isApp ? "" : row.target()));

                        // App icon (APP rows only)
                        WebMarkupContainer appIcon = new WebMarkupContainer("appIcon");
                        appIcon.setVisible(isApp && row.appIconClass() != null);
                        if (isApp && row.appIconClass() != null) {
                            appIcon.add(AttributeModifier.replace("class",
                                    "bi " + row.appIconClass() + " me-1 " + row.appIconColor()));
                        }
                        item.add(appIcon);

                        // Summary text
                        item.add(new Label("summaryText", row.summary())
                                .add(AttributeModifier.replace("class",
                                        isApp ? "text-muted fst-italic" : "")));

                        // View button — carries raw FIX message via data-raw for the JS modal
                        WebMarkupContainer viewBtn = new WebMarkupContainer("viewBtn") {
                            @Override
                            protected void onComponentTag(ComponentTag tag) {
                                super.onComponentTag(tag);
                                // Replace SOH (0x01) with pipe for safe embedding in HTML attribute
                                tag.put("data-raw", row.rawMessage().replace("\u0001", "|"));
                            }
                        };
                        viewBtn.setVisible(!isApp);
                        item.add(viewBtn);
                    }
                };

        // ── Table body (<tbody>) ───────────────────────────────────────────────
        WebMarkupContainer tableBody = new WebMarkupContainer("tableBody");
        tableBody.setOutputMarkupId(true);

        tableBody.add(new WebMarkupContainer("emptyRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(rowsModel.getObject().isEmpty());
            }
        });
        tableBody.add(activityList);

        // Auto-refresh every 3 s
        tableBody.add(new AbstractAjaxTimerBehavior(Duration.ofSeconds(3)) {
            @Override
            protected void onTimer(AjaxRequestTarget target) {
                target.add(tableBody, pagingFooter);
            }
        });

        // ── Paging footer ──────────────────────────────────────────────────────
        pagingFooter.add(new Label("summary", (IModel<String>) () -> {
            int total = rowsModel.getObject().size();
            if (total == 0) return "No messages for this session";
            long cur  = activityList.getCurrentPage();
            long from = cur * PAGE_SIZE + 1;
            long to   = Math.min((cur + 1L) * PAGE_SIZE, total);
            return "Showing " + from + "\u2013" + to + " of " + total + " entries";
        }));

        WebMarkupContainer prevItem = new WebMarkupContainer("prevItem") {
            @Override
            protected void onComponentTag(ComponentTag tag) {
                super.onComponentTag(tag);
                tag.put("class",
                        "page-item" + (activityList.getCurrentPage() <= 0 ? " disabled" : ""));
            }
        };
        prevItem.add(new AjaxLink<Void>("prevPage") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                if (activityList.getCurrentPage() > 0)
                    activityList.setCurrentPage(activityList.getCurrentPage() - 1);
                target.add(tableBody, pagingFooter);
            }
        });
        pagingFooter.add(prevItem);

        WebMarkupContainer nextItem = new WebMarkupContainer("nextItem") {
            @Override
            protected void onComponentTag(ComponentTag tag) {
                super.onComponentTag(tag);
                boolean atEnd = activityList.getCurrentPage() >= activityList.getPageCount() - 1;
                tag.put("class", "page-item" + (atEnd ? " disabled" : ""));
            }
        };
        nextItem.add(new AjaxLink<Void>("nextPage") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                if (activityList.getCurrentPage() < activityList.getPageCount() - 1)
                    activityList.setCurrentPage(activityList.getCurrentPage() + 1);
                target.add(tableBody, pagingFooter);
            }
        });
        pagingFooter.add(nextItem);

        // ── Filter form ────────────────────────────────────────────────────────
        Form<FilterModel> filterForm = new Form<>("filterForm",
                new CompoundPropertyModel<>(filter));

        DropDownChoice<String> dirDD = new DropDownChoice<>("direction",
                List.of("All", "Sent", "Received"));
        dirDD.setNullValid(false);
        dirDD.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                FixSimulatorSession.get().setActivityDirection(filter.direction);
                activityList.setCurrentPage(0);
                rowsModel.detach();
                target.add(tableBody, pagingFooter);
            }
        });
        filterForm.add(dirDD);

        CheckBox hideHbCb = new CheckBox("hideHeartbeats");
        hideHbCb.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                FixSimulatorSession.get().setActivityHideHeartbeats(filter.hideHeartbeats);
                activityList.setCurrentPage(0);
                rowsModel.detach();
                target.add(tableBody, pagingFooter);
            }
        });
        filterForm.add(hideHbCb);

        add(filterForm);
        add(tableBody);
        add(pagingFooter);

        // ── Clear log ──────────────────────────────────────────────────────────
        add(new AjaxLink<Void>("clearLogBtn") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                String sessionId = FixSimulatorSession.get().getActiveSessionId();
                MessageLogService mls = msgLogSvc();
                if (sessionId != null && mls != null) mls.clearLog(sessionId);
                activityList.setCurrentPage(0);
                rowsModel.detach();
                target.add(tableBody, pagingFooter);
            }
        });
    }

    // ── Header resources ──────────────────────────────────────────────────────

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(JavaScriptReferenceHeaderItem.forReference(
                new PackageResourceReference(FixActivityPage.class, "FixActivityPage.js")));
    }

    // ── Service helpers ───────────────────────────────────────────────────────

    private static MessageLogService msgLogSvc() {
        return ((FixSimulatorApplication) Application.get()).getMessageLogService();
    }

    private static String connName(String sessionId) {
        ConnectionService cs = ((FixSimulatorApplication) Application.get()).getConnectionService();
        return cs != null ? cs.getSessionName(sessionId) : sessionId;
    }

    // ── FIX message parsing ───────────────────────────────────────────────────

    /** Parse a SOH-delimited raw FIX message into an ordered tag→value map. */
    static Map<String, String> parseTags(String raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : raw.split("\u0001")) {
            int eq = pair.indexOf('=');
            if (eq > 0) map.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return map;
    }

    static String msgTypeName(String type) {
        if (type == null) return "";
        return switch (type) {
            case "0" -> "Heartbeat";
            case "1" -> "TestRequest";
            case "2" -> "ResendRequest";
            case "3" -> "Reject";
            case "4" -> "SequenceReset";
            case "5" -> "Logout";
            case "8" -> "ExecutionReport";
            case "9" -> "OrderCancelReject";
            case "A" -> "Logon";
            case "D" -> "NewOrderSingle";
            case "F" -> "OrderCancelRequest";
            case "G" -> "OrderCancelReplaceRequest";
            case "V" -> "MarketDataRequest";
            case "W" -> "MarketDataSnapshot";
            case "X" -> "MarketDataIncrementalRefresh";
            default  -> type;
        };
    }

    static String buildSummary(String msgType, Map<String, String> tags) {
        if (msgType == null) return "";
        return switch (msgType) {
            case "D" -> {
                String sym   = tags.getOrDefault("55", "?");
                String side  = sideLabel(tags.getOrDefault("54", "?"));
                String qty   = tags.getOrDefault("38", "?");
                String price = tags.getOrDefault("44", "");
                String type  = ordTypeLabel(tags.getOrDefault("40", "?"));
                String clOrd = tags.getOrDefault("11", "");
                yield sym + " " + side + " " + qty
                        + (price.isEmpty() ? "" : " @ " + price)
                        + " (" + type + ")"
                        + (clOrd.isEmpty() ? "" : "  \u2014  ClOrdID: " + clOrd);
            }
            case "8" -> {
                String exec      = execTypeLabel(tags.getOrDefault("150", "?"));
                String sym       = tags.getOrDefault("55", "");
                String lastQty   = tags.getOrDefault("32", "");
                String lastPx    = tags.getOrDefault("31", "");
                String cumQty    = tags.getOrDefault("14", "");
                String leavesQty = tags.getOrDefault("151", "");
                String text      = tags.getOrDefault("58", "");
                String clOrd     = tags.getOrDefault("11", "");
                StringBuilder sb = new StringBuilder("ExecType=").append(exec);
                if (!sym.isEmpty())       sb.append("  ").append(sym);
                if (!lastQty.isEmpty() && !lastPx.isEmpty())
                    sb.append("  LastQty=").append(lastQty).append(" @ ").append(lastPx);
                if (!cumQty.isEmpty())    sb.append("  CumQty=").append(cumQty);
                if (!leavesQty.isEmpty()) sb.append("  LeavesQty=").append(leavesQty);
                if (!text.isEmpty())      sb.append("  ").append(text);
                if (!clOrd.isEmpty())     sb.append("  \u2014  ClOrdID: ").append(clOrd);
                yield sb.toString();
            }
            case "F" -> {
                String sym  = tags.getOrDefault("55", "");
                String orig = tags.getOrDefault("41", "");
                String clOrd = tags.getOrDefault("11", "");
                yield "Cancel request"
                        + (sym.isEmpty()   ? "" : " for " + sym)
                        + (orig.isEmpty()  ? "" : "  \u2014  OrigClOrdID: " + orig)
                        + (clOrd.isEmpty() ? "" : "  ClOrdID: " + clOrd);
            }
            case "G" -> {
                String sym   = tags.getOrDefault("55", "");
                String qty   = tags.getOrDefault("38", "");
                String price = tags.getOrDefault("44", "");
                String orig  = tags.getOrDefault("41", "");
                yield "Amend request"
                        + (sym.isEmpty()   ? "" : " for " + sym)
                        + (qty.isEmpty()   ? "" : "  Qty=" + qty)
                        + (price.isEmpty() ? "" : " @ " + price)
                        + (orig.isEmpty()  ? "" : "  \u2014  OrigClOrdID: " + orig);
            }
            case "9" -> {
                String clOrd  = tags.getOrDefault("11", "");
                String reason = tags.getOrDefault("102", "");
                String text   = tags.getOrDefault("58", "");
                yield "Cancel rejected"
                        + (clOrd.isEmpty()  ? "" : "  ClOrdID: " + clOrd)
                        + (reason.isEmpty() ? "" : "  Reason=" + reason)
                        + (text.isEmpty()   ? "" : "  \u2014  " + text);
            }
            case "A" -> "HeartBtInt=" + tags.getOrDefault("108", "?");
            case "5" -> {
                String t = tags.getOrDefault("58", "");
                yield t.isEmpty() ? "Session logout" : t;
            }
            case "3" -> {
                String ref  = tags.getOrDefault("371", "");
                String text = tags.getOrDefault("58", "");
                yield "Reject" + (ref.isEmpty() ? "" : " (tag " + ref + ")")
                        + (text.isEmpty() ? "" : ": " + text);
            }
            case "0" -> tags.containsKey("112") ? "TestReqID=" + tags.get("112") : "\u2014";
            default  -> "";
        };
    }

    // ── App-level annotation row ──────────────────────────────────────────────

    private static ActivityRow buildAppRow(String msgType, Map<String, String> tags,
                                           String timeStr, String connName, String rawMsg) {
        String clOrd = tags.getOrDefault("11", "");
        return switch (msgType) {
            case "A" -> appRow(timeStr, connName, "Session", rawMsg,
                    "Session established \u2014 ready to send and receive FIX messages",
                    "bi-check2-circle", "text-success");
            case "5" -> appRow(timeStr, connName, "Session", rawMsg,
                    "Session disconnected",
                    "bi-x-circle", "text-danger");
            case "D" -> appRow(timeStr, connName, "Order", rawMsg,
                    "Order submitted \u2014 ClOrdID: " + clOrd + " \u2014 awaiting acknowledgement",
                    "bi-box-arrow-right", "text-primary");
            case "F" -> appRow(timeStr, connName, "Order", rawMsg,
                    "Cancel request sent \u2014 OrigClOrdID: " + tags.getOrDefault("41", clOrd),
                    "bi-x-square", "text-warning");
            case "G" -> appRow(timeStr, connName, "Order", rawMsg,
                    "Amend request sent \u2014 OrigClOrdID: " + tags.getOrDefault("41", clOrd),
                    "bi-pencil", "text-info");
            case "8" -> {
                String execType = tags.getOrDefault("150", "");
                String sym      = tags.getOrDefault("55", "");
                String lastQty  = tags.getOrDefault("32", "");
                String lastPx   = tags.getOrDefault("31", "");
                String cumQty   = tags.getOrDefault("14", "");
                String text     = tags.getOrDefault("58", "");
                yield switch (execType) {
                    case "0" -> appRow(timeStr, connName, "Order", rawMsg,
                            "Order " + clOrd + " acknowledged \u2014 status: New",
                            "bi-check-circle", "text-success");
                    case "1" -> appRow(timeStr, connName, "Trade", rawMsg,
                            "Trade: " + sym + " " + lastQty + " @ " + lastPx
                                    + "  CumQty=" + cumQty + " (partial fill)",
                            "bi-arrow-left-right", "text-warning");
                    case "2", "F" -> appRow(timeStr, connName, "Trade", rawMsg,
                            "Trade: " + sym + " " + lastQty + " @ " + lastPx
                                    + "  Fully filled (CumQty=" + cumQty + ")",
                            "bi-check-all", "text-success");
                    case "4" -> appRow(timeStr, connName, "Order", rawMsg,
                            "Order " + clOrd + " cancelled"
                                    + (text.isEmpty() ? "" : " \u2014 " + text),
                            "bi-dash-circle", "text-secondary");
                    case "5" -> appRow(timeStr, connName, "Order", rawMsg,
                            "Order " + clOrd + " replaced successfully",
                            "bi-pencil-square", "text-info");
                    case "8" -> appRow(timeStr, connName, "Order", rawMsg,
                            "Order " + clOrd + " rejected"
                                    + (text.isEmpty() ? "" : " \u2014 " + text),
                            "bi-exclamation-circle", "text-danger");
                    default -> null;
                };
            }
            case "9" -> {
                String text = tags.getOrDefault("58", "");
                yield appRow(timeStr, connName, "Order", rawMsg,
                        "Cancel rejected for ClOrdID: " + clOrd
                                + (text.isEmpty() ? "" : " \u2014 " + text),
                        "bi-exclamation-triangle", "text-danger");
            }
            default -> null;
        };
    }

    private static ActivityRow appRow(String timeStr, String connName, String category,
                                      String rawMsg, String summary,
                                      String iconClass, String iconColor) {
        return new ActivityRow(EntryType.APP, timeStr, "", connName,
                "\u2014", "", category, "\u2014", "\u2014",
                summary, rawMsg, iconClass, iconColor);
    }

    // ── Label helpers ─────────────────────────────────────────────────────────

    private static String sideLabel(String s) {
        return switch (s) {
            case "1" -> "BUY"; case "2" -> "SELL"; case "5" -> "SELL SHORT"; default -> s;
        };
    }

    private static String ordTypeLabel(String t) {
        return switch (t) {
            case "1" -> "Market"; case "2" -> "Limit";
            case "3" -> "Stop";   case "4" -> "Stop Limit"; default -> t;
        };
    }

    private static String execTypeLabel(String t) {
        return switch (t) {
            case "0" -> "New";       case "1" -> "PartFill";
            case "2" -> "Fill";      case "4" -> "Cancelled";
            case "5" -> "Replaced";  case "6" -> "PendingCancel";
            case "8" -> "Rejected";  case "C" -> "Expired";
            case "F" -> "Trade";     default  -> t;
        };
    }
}
