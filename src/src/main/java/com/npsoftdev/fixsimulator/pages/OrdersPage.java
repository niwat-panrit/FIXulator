package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.FixSimulatorSession;
import com.npsoftdev.fixsimulator.service.OrderService;
import org.apache.wicket.Application;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.ajax.attributes.AjaxCallListener;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.LoadableDetachableModel;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OrdersPage extends BasePage {

    public OrdersPage() {
        super();

        FilterModel filter = new FilterModel();

        // ── Orders table body (auto-refreshed every 3 s) ─────────────────────

        WebMarkupContainer tableBody = new WebMarkupContainer("tableBody");
        tableBody.setOutputMarkupId(true);
        tableBody.add(new AjaxSelfUpdatingTimerBehavior(Duration.ofSeconds(3)));

        LoadableDetachableModel<List<Map<Integer, String>>> ordersModel =
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<Map<Integer, String>> load() {
                        String sessionId = FixSimulatorSession.get().getActiveSessionId();
                        if (sessionId == null) return Collections.emptyList();
                        OrderService os = orderSvc();
                        if (os == null) return Collections.emptyList();
                        return os.listOrders(sessionId).stream()
                                .filter(m -> matchesFilter(m, filter))
                                .collect(Collectors.toList());
                    }
                };

        // Empty-state row — shown when no orders match
        WebMarkupContainer emptyRow = new WebMarkupContainer("emptyRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(ordersModel.getObject().isEmpty());
            }
        };
        tableBody.add(emptyRow);

        // One row per order
        tableBody.add(new ListView<Map<Integer, String>>("orderRows", ordersModel) {
            @Override
            protected void populateItem(ListItem<Map<Integer, String>> item) {
                Map<Integer, String> o = item.getModelObject();
                String clOrdId   = o.getOrDefault(11, "");
                String side      = o.getOrDefault(54, "");
                String ordStatus = o.getOrDefault(39, "");

                item.add(new Label("clOrdId", clOrdId));
                item.add(new Label("symbol", o.getOrDefault(55, "")));

                Label sideLabel = new Label("side", displaySide(side));
                sideLabel.add(AttributeModifier.replace("class", "badge " + sideBadgeClass(side)));
                item.add(sideLabel);

                item.add(new Label("quantity", o.getOrDefault(38, "")));
                item.add(new Label("price",    nvl(o.get(44))));
                item.add(new Label("ordType",  displayOrdType(o.getOrDefault(40, ""))));
                item.add(new Label("timeInForce", displayTif(o.getOrDefault(59, ""))));

                Label statusLabel = new Label("status", displayStatus(ordStatus));
                statusLabel.add(AttributeModifier.replace("class",
                        "badge " + statusBadgeClass(ordStatus)));
                item.add(statusLabel);

                item.add(new Label("cumQty",   o.getOrDefault(14, "0")));
                item.add(new Label("avgPx",    nvl(o.get(6))));
                item.add(new Label("sendTime", formatSendTime(o.getOrDefault(60, ""))));

                // ── Amend icon button (placeholder — no amend service method yet) ──
                item.add(new AjaxLink<Void>("amendBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        // TODO: open amend (OrderCancelReplaceRequest) dialog
                    }
                });

                // ── Cancel icon button ────────────────────────────────────────
                item.add(new AjaxLink<Void>("cancelBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        String sessionId = FixSimulatorSession.get().getActiveSessionId();
                        if (sessionId != null) {
                            OrderService os = orderSvc();
                            if (os != null) os.cancelOrder(sessionId, clOrdId);
                        }
                        target.add(tableBody);
                    }

                    @Override
                    protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
                        super.updateAjaxAttributes(attributes);
                        String safe = clOrdId.replace("'", "\\'");
                        attributes.getAjaxCallListeners().add(new AjaxCallListener()
                                .onPrecondition("return confirm('Cancel order \\'" + safe + "\\'?');"));
                    }
                });
            }
        });

        // ── Filter form (wraps the table; AJAX-updates tableBody on change) ───

        Form<FilterModel> filterForm = new Form<>("filterForm",
                new CompoundPropertyModel<>(filter));
        filterForm.add(tableBody);

        addTextFilter(filterForm, "filterClOrdId", tableBody);
        addTextFilter(filterForm, "filterSymbol",  tableBody);

        addDropFilter(filterForm, "filterSide",
                List.of("BUY", "SELL"), tableBody);
        addDropFilter(filterForm, "filterOrdType",
                List.of("Limit", "Market", "Stop", "StopLimit"), tableBody);
        addDropFilter(filterForm, "filterTimeInForce",
                List.of("Day", "GTC", "IOC", "FOK"), tableBody);
        addDropFilter(filterForm, "filterStatus",
                List.of("New", "PartFilled", "Filled", "Cancelled", "Rejected", "Pending"), tableBody);

        add(filterForm);

        // ── New Order modal form ──────────────────────────────────────────────
        add(buildNewOrderForm(new NewOrderModel()));
    }

    // ── Filter helper builders ────────────────────────────────────────────────

    private static void addTextFilter(Form<?> form, String id, WebMarkupContainer tableBody) {
        TextField<String> tf = new TextField<>(id);
        tf.add(new AjaxFormComponentUpdatingBehavior("input") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                target.add(tableBody);
            }
        });
        form.add(tf);
    }

    private static void addDropFilter(Form<?> form, String id,
                                      List<String> choices, WebMarkupContainer tableBody) {
        DropDownChoice<String> dd = new DropDownChoice<>(id, choices);
        dd.setNullValid(true);
        dd.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                target.add(tableBody);
            }
        });
        form.add(dd);
    }

    // ── Filter matching ───────────────────────────────────────────────────────

    private static boolean matchesFilter(Map<Integer, String> o, FilterModel f) {
        if (!blank(f.filterClOrdId) &&
                !o.getOrDefault(11, "").toLowerCase().contains(f.filterClOrdId.toLowerCase()))
            return false;
        if (!blank(f.filterSymbol) &&
                !o.getOrDefault(55, "").toLowerCase().contains(f.filterSymbol.toLowerCase()))
            return false;
        if (!blank(f.filterSide) &&
                !displaySide(o.getOrDefault(54, "")).equals(f.filterSide))
            return false;
        if (!blank(f.filterOrdType) &&
                !displayOrdType(o.getOrDefault(40, "")).equals(f.filterOrdType))
            return false;
        if (!blank(f.filterTimeInForce) &&
                !displayTif(o.getOrDefault(59, "")).equals(f.filterTimeInForce))
            return false;
        if (!blank(f.filterStatus) &&
                !displayStatus(o.getOrDefault(39, "")).equals(f.filterStatus))
            return false;
        return true;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    // ── New Order form ────────────────────────────────────────────────────────

    private Form<NewOrderModel> buildNewOrderForm(NewOrderModel model) {
        Form<NewOrderModel> form = new Form<>("newOrderForm",
                new CompoundPropertyModel<>(model));

        FeedbackPanel feedback = new FeedbackPanel("feedback");
        feedback.setOutputMarkupId(true);
        form.add(feedback);

        form.add(new TextField<String>("symbol").setRequired(true));
        form.add(new DropDownChoice<>("side",
                List.of("BUY", "SELL")).setRequired(true));
        form.add(new NumberTextField<BigDecimal>("quantity", BigDecimal.class)
                .setMinimum(BigDecimal.ONE).setRequired(true));
        form.add(new NumberTextField<BigDecimal>("price", BigDecimal.class)
                .setMinimum(BigDecimal.ZERO));
        form.add(new DropDownChoice<>("ordType",
                List.of("Limit", "Market", "Stop", "StopLimit")).setRequired(true));
        form.add(new DropDownChoice<>("timeInForce",
                List.of("Day", "GTC", "IOC", "FOK")).setRequired(true));
        form.add(new TextArea<String>("text"));

        form.add(new AjaxButton("sendBtn", form) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                String sessionId = FixSimulatorSession.get().getActiveSessionId();
                if (sessionId == null) {
                    error("No active FIX session selected. Please select a session from the top bar.");
                    target.add(feedback);
                    return;
                }
                OrderService os = orderSvc();
                if (os == null) {
                    error("Order service is not available.");
                    target.add(feedback);
                    return;
                }

                Map<Integer, String> fields = new HashMap<>();
                fields.put(55, model.symbol);                        // Symbol
                fields.put(54, sideCode(model.side));                // Side
                fields.put(38, model.quantity.toPlainString());      // OrderQty
                if (model.price != null) {
                    fields.put(44, model.price.toPlainString());     // Price
                }
                fields.put(40, ordTypeCode(model.ordType));          // OrdType
                fields.put(59, tifCode(model.timeInForce));          // TimeInForce
                if (!blank(model.text)) {
                    fields.put(58, model.text);                      // Text
                }

                try {
                    os.sendNewOrder(sessionId, fields);
                    model.reset();
                    target.appendJavaScript(
                            "bootstrap.Modal.getInstance(document.getElementById('newOrderModal')).hide();");
                } catch (Exception e) {
                    error("Failed to send order: " + e.getMessage());
                    target.add(feedback);
                }
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(feedback);
            }
        });

        return form;
    }

    // ── Service lookup ────────────────────────────────────────────────────────

    private static OrderService orderSvc() {
        return ((FixSimulatorApplication) Application.get()).getOrderService();
    }

    // ── Display helpers ───────────────────────────────────────────────────────

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

    private static String displayOrdType(String code) {
        return switch (code) {
            case "1" -> "Market";
            case "2" -> "Limit";
            case "3" -> "Stop";
            case "4" -> "StopLimit";
            default  -> code;
        };
    }

    private static String displayTif(String code) {
        return switch (code) {
            case "0" -> "Day";
            case "1" -> "GTC";
            case "3" -> "IOC";
            case "4" -> "FOK";
            default  -> code;
        };
    }

    private static String displayStatus(String code) {
        return switch (code) {
            case "0" -> "New";
            case "1" -> "PartFilled";
            case "2" -> "Filled";
            case "4" -> "Cancelled";
            case "8" -> "Rejected";
            case "A" -> "Pending";
            default  -> blank(code) ? "Sent" : code;
        };
    }

    private static String statusBadgeClass(String code) {
        return switch (code) {
            case "0" -> "bg-primary";
            case "1" -> "bg-warning text-dark";
            case "2" -> "bg-success";
            case "4" -> "bg-secondary";
            case "8" -> "bg-danger";
            default  -> "bg-info text-dark";
        };
    }

    private static String formatSendTime(String raw) {
        if (blank(raw)) return "—";
        int dash = raw.indexOf('-');
        return dash >= 0 ? raw.substring(dash + 1) : raw;
    }

    private static String nvl(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }

    // ── FIX code mapping (for New Order submission) ───────────────────────────

    private static String sideCode(String side) {
        return "SELL".equals(side) ? "2" : "1";
    }

    private static String ordTypeCode(String ordType) {
        return switch (ordType) {
            case "Market"    -> "1";
            case "Stop"      -> "3";
            case "StopLimit" -> "4";
            default          -> "2"; // Limit
        };
    }

    private static String tifCode(String tif) {
        return switch (tif) {
            case "GTC" -> "1";
            case "IOC" -> "3";
            case "FOK" -> "4";
            default    -> "0"; // Day
        };
    }

    // ── Models ────────────────────────────────────────────────────────────────

    static class FilterModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String filterClOrdId    = "";
        String filterSymbol     = "";
        String filterSide;        // null = all
        String filterOrdType;
        String filterTimeInForce;
        String filterStatus;
    }

    static class NewOrderModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String symbol      = "";
        String side        = "BUY";
        BigDecimal quantity = BigDecimal.valueOf(100);
        BigDecimal price;
        String ordType     = "Limit";
        String timeInForce = "Day";
        String text        = "";

        void reset() {
            symbol = ""; side = "BUY";
            quantity = BigDecimal.valueOf(100); price = null;
            ordType = "Limit"; timeInForce = "Day"; text = "";
        }
    }
}
