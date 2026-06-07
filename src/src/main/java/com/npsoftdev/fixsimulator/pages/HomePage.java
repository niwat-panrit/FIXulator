package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.FixSimulatorSession;
import com.npsoftdev.fixsimulator.service.ConnectionService;
import com.npsoftdev.fixsimulator.service.ConnectionService.SessionDetails;
import com.npsoftdev.fixsimulator.service.MessageLogService;
import com.npsoftdev.fixsimulator.service.MessageLogService.LogEntry;
import com.npsoftdev.fixsimulator.service.OrderService;
import com.npsoftdev.fixsimulator.service.TradeService;
import org.apache.wicket.Application;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HomePage extends BasePage {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter MSG_TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public HomePage() {
        super();

        // ── Single refreshable container for the whole dashboard ───────────────
        WebMarkupContainer dashboard = new WebMarkupContainer("dashboard");
        dashboard.setOutputMarkupId(true);
        dashboard.add(new AjaxSelfUpdatingTimerBehavior(Duration.ofSeconds(3)));
        add(dashboard);

        // ── Last updated timestamp ─────────────────────────────────────────────
        dashboard.add(new Label("lastUpdated",
                (IModel<String>) () -> TIME_FMT.format(Instant.now())));

        // ── Stat cards ─────────────────────────────────────────────────────────

        // Active connections — count across ALL sessions
        dashboard.add(new Label("activeConnections", (IModel<String>) () -> {
            ConnectionService cs = connSvc();
            if (cs == null) return "0";
            return String.valueOf(cs.listSessions().stream()
                    .filter(s -> "CONNECTED".equals(s.status())).count());
        }));

        // Total orders — aggregated across all sessions
        dashboard.add(new Label("ordersTotal", (IModel<String>) () -> {
            ConnectionService cs = connSvc();
            OrderService os = orderSvc();
            if (cs == null || os == null) return "0";
            return String.valueOf(cs.listSessionIds().stream()
                    .mapToInt(sid -> os.listOrders(sid).size())
                    .sum());
        }));

        // Total trades filled — aggregated across all sessions
        dashboard.add(new Label("tradesFilled", (IModel<String>) () -> {
            ConnectionService cs = connSvc();
            TradeService ts = tradeSvc();
            if (cs == null || ts == null) return "0";
            return String.valueOf(cs.listSessionIds().stream()
                    .mapToInt(sid -> ts.listTrades(sid).size())
                    .sum());
        }));

        // FIX messages in the last hour — aggregated across all sessions
        dashboard.add(new Label("messagesLastHour", (IModel<String>) () -> {
            ConnectionService cs = connSvc();
            MessageLogService mls = msgLogSvc();
            if (cs == null || mls == null) return "0";
            Instant cutoff = Instant.now().minus(Duration.ofHours(1));
            return String.valueOf(cs.listSessionIds().stream()
                    .flatMap(sid -> mls.getMessages(sid).stream())
                    .filter(e -> e.timestamp().isAfter(cutoff))
                    .count());
        }));

        // ── Session status table ───────────────────────────────────────────────
        LoadableDetachableModel<List<SessionDetails>> sessionsModel =
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<SessionDetails> load() {
                        ConnectionService cs = connSvc();
                        return cs != null ? cs.listSessions() : Collections.emptyList();
                    }
                };

        dashboard.add(new WebMarkupContainer("noSessions") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(sessionsModel.getObject().isEmpty());
            }
        });

        dashboard.add(new ListView<SessionDetails>("sessionRows", sessionsModel) {
            @Override
            protected void populateItem(ListItem<SessionDetails> item) {
                SessionDetails sd = item.getModelObject();

                item.add(new Label("connName", sd.name()));

                Label connType = new Label("connType",
                        "initiator".equalsIgnoreCase(sd.connectionType()) ? "Initiator" : "Acceptor");
                connType.add(AttributeModifier.replace("class",
                        "badge " + ("initiator".equalsIgnoreCase(sd.connectionType())
                                ? "bg-secondary" : "bg-dark")));
                item.add(connType);

                Label statusBadge = new Label("connStatus", sd.status());
                statusBadge.add(AttributeModifier.replace("class",
                        "badge " + statusBadgeCss(sd.status())));
                item.add(statusBadge);

                item.add(new Label("txSeq", String.valueOf(sd.txSeq())));
                item.add(new Label("rxSeq", String.valueOf(sd.rxSeq())));
            }
        });

        // ── Recent messages (last 10, newest first, from active session) ───────
        LoadableDetachableModel<List<LogEntry>> recentMsgsModel =
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<LogEntry> load() {
                        String sid = FixSimulatorSession.get().getActiveSessionId();
                        MessageLogService mls = msgLogSvc();
                        if (sid == null || mls == null) return Collections.emptyList();
                        List<LogEntry> all = mls.getMessages(sid); // newest-first
                        return all.subList(0, Math.min(10, all.size()));
                    }
                };

        dashboard.add(new WebMarkupContainer("noMessages") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(recentMsgsModel.getObject().isEmpty());
            }
        });

        dashboard.add(new ListView<LogEntry>("recentMsgRows", recentMsgsModel) {
            @Override
            protected void populateItem(ListItem<LogEntry> item) {
                LogEntry entry = item.getModelObject();
                Map<String, String> tags = FixActivityPage.parseTags(entry.rawMessage());
                boolean sent = entry.direction() == MessageLogService.Direction.SENT;

                item.add(new Label("msgTime", MSG_TIME_FMT.format(entry.timestamp())));

                Label dirLabel = new Label("msgDir", sent ? "\u25B6 OUT" : "\u25C4 IN");
                dirLabel.add(AttributeModifier.replace("class", sent ? "dir-out" : "dir-in"));
                item.add(dirLabel);

                item.add(new Label("msgType", entry.msgType()));

                String typeName = FixActivityPage.msgTypeName(entry.msgType());
                String details  = FixActivityPage.buildSummary(entry.msgType(), tags);
                item.add(new Label("msgSummary",
                        details.isEmpty() ? typeName : typeName + " \u2014 " + details));
            }
        });

        // ── "View All" link → FIX Activity page ───────────────────────────────
        dashboard.add(new BookmarkablePageLink<>("viewAllLink", FixActivityPage.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ConnectionService connSvc() {
        return ((FixSimulatorApplication) Application.get()).getConnectionService();
    }

    private static OrderService orderSvc() {
        return ((FixSimulatorApplication) Application.get()).getOrderService();
    }

    private static TradeService tradeSvc() {
        return ((FixSimulatorApplication) Application.get()).getTradeService();
    }

    private static MessageLogService msgLogSvc() {
        return ((FixSimulatorApplication) Application.get()).getMessageLogService();
    }

    private static String statusBadgeCss(String status) {
        return switch (status) {
            case "CONNECTED"    -> "bg-success";
            case "DISCONNECTED" -> "bg-danger";
            default             -> "bg-warning text-dark";
        };
    }
}
