package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.FixSimulatorSession;
import com.npsoftdev.fixsimulator.service.OrderService;
import com.npsoftdev.fixsimulator.template.FieldSpec;
import com.npsoftdev.fixsimulator.template.FieldValue;
import com.npsoftdev.fixsimulator.template.FixMessageTemplate;
import com.npsoftdev.fixsimulator.template.TemplateService;
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
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OrdersPage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(OrdersPage.class);

    /** FIX tags rendered via the structured form controls — excluded from extra-fields list. */
    private static final Set<Integer> STANDARD_TAGS = Set.of(
            55,  // Symbol
            54,  // Side
            44,  // Price
            38,  // OrderQty
            40,  // OrdType
            59,  // TimeInForce
            41   // OrigClOrdID — handled via amend model's origClOrdId field
    );

    // ── Page-level state (fields so onSessionSwitched can access them) ────────
    private final NewOrderModel newOrderModel = new NewOrderModel();
    private final AmendModel    amendModel    = new AmendModel();
    private Form<NewOrderModel> newOrderForm;
    private Form<AmendModel>    amendOrderForm;

    public OrdersPage() {
        super();

        FilterModel filter = new FilterModel();

        // ── Pre-load templates so extra-fields are available at construction time
        FixMessageTemplate nosTmpl = findTemplate("D");
        if (nosTmpl != null) {
            newOrderModel.templateId   = nosTmpl.id();
            newOrderModel.extraFields  = loadExtraFields(nosTmpl);
        }
        FixMessageTemplate ocrTmpl = findTemplate("G");
        if (ocrTmpl != null) {
            amendModel.templateId  = ocrTmpl.id();
            amendModel.extraFields = loadExtraFields(ocrTmpl);
        }

        // ── Build amend form first so the amend button can reference it ─────────
        amendOrderForm = buildAmendOrderForm(amendModel);
        amendOrderForm.setOutputMarkupId(true);

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

        WebMarkupContainer emptyRow = new WebMarkupContainer("emptyRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(ordersModel.getObject().isEmpty());
            }
        };
        tableBody.add(emptyRow);

        tableBody.add(new ListView<Map<Integer, String>>("orderRows", ordersModel) {
            @Override
            protected void populateItem(ListItem<Map<Integer, String>> item) {
                Map<Integer, String> o = item.getModelObject();
                final String clOrdId   = o.getOrDefault(11, "");
                final String side      = o.getOrDefault(54, "");
                final String ordStatus = o.getOrDefault(39, "");

                item.add(new Label("clOrdId", clOrdId));
                item.add(new Label("symbol", o.getOrDefault(55, "")));

                Label sideLabel = new Label("side", displaySide(side));
                sideLabel.add(AttributeModifier.replace("class", "badge " + sideBadgeClass(side)));
                item.add(sideLabel);

                item.add(new Label("quantity",    o.getOrDefault(38, "")));
                item.add(new Label("price",       nvl(o.get(44))));
                item.add(new Label("ordType",     displayOrdType(o.getOrDefault(40, ""))));
                item.add(new Label("timeInForce", displayTif(o.getOrDefault(59, ""))));

                Label statusLabel = new Label("status", displayStatus(ordStatus));
                statusLabel.add(AttributeModifier.replace("class",
                        "badge " + statusBadgeClass(ordStatus)));
                item.add(statusLabel);

                item.add(new Label("cumQty",    o.getOrDefault(14, "0")));
                item.add(new Label("leavesQty", o.getOrDefault(151, "—")));
                item.add(new Label("avgPx",     nvl(o.get(6))));
                item.add(new Label("sendTime",  formatSendTime(o.getOrDefault(60, ""))));

                // Row-level CSS to convey order state at a glance
                item.add(AttributeModifier.replace("class", rowClass(ordStatus)));

                // ── Amend button ───────────────────────────────────────────────
                item.add(new AjaxLink<Void>("amendBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        amendModel.origClOrdId  = clOrdId;
                        amendModel.symbol       = o.getOrDefault(55, "");
                        amendModel.side         = displaySide(o.getOrDefault(54, "1"));
                        String rawQty = o.getOrDefault(38, "");
                        amendModel.quantity     = blank(rawQty) ? null : new BigDecimal(rawQty);
                        String rawPx = o.getOrDefault(44, "");
                        amendModel.price        = blank(rawPx)  ? null : new BigDecimal(rawPx);
                        amendModel.ordType      = displayOrdType(o.getOrDefault(40, "2"));
                        amendModel.timeInForce  = displayTif(o.getOrDefault(59, "0"));
                        amendModel.extraFields.forEach(
                                e -> e.value = e.defaultValue != null ? e.defaultValue : "");
                        target.add(amendOrderForm);
                        target.appendJavaScript(
                                "new bootstrap.Modal(document.getElementById('amendOrderModal')).show();");
                    }
                });

                // ── Cancel button ──────────────────────────────────────────────
                item.add(new AjaxLink<Void>("cancelBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        String sessionId = FixSimulatorSession.get().getActiveSessionId();
                        if (sessionId != null) {
                            OrderService os = orderSvc();
                            if (os != null) {
                                os.cancelOrder(sessionId, clOrdId);
                                log.info("Cancel Order sent: session='{}' clOrdId='{}' (no template — direct cancel)",
                                        sessionId, clOrdId);
                            }
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

        // ── Filter form (wraps table) ─────────────────────────────────────────
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
                List.of("New", "PartFilled", "Filled", "DoneForDay", "Cancelled",
                        "Stopped", "Rejected", "Suspended", "Expired",
                        "PendCxl", "PendReplace", "PendingNew"), tableBody);

        add(filterForm);
        newOrderForm = buildNewOrderForm(newOrderModel);
        newOrderForm.setOutputMarkupId(true);
        add(newOrderForm);
        add(amendOrderForm);
    }

    @Override
    protected void onSessionSwitched(AjaxRequestTarget target) {
        // Re-associate templates with the new active session
        FixMessageTemplate nosTmpl = findTemplate("D");
        newOrderModel.templateId  = nosTmpl != null ? nosTmpl.id() : null;
        newOrderModel.extraFields = loadExtraFields(nosTmpl);

        FixMessageTemplate ocrTmpl = findTemplate("G");
        amendModel.templateId  = ocrTmpl != null ? ocrTmpl.id() : null;
        amendModel.extraFields = loadExtraFields(ocrTmpl);

        target.add(newOrderForm);
        target.add(amendOrderForm);
    }

    // ── Filter helpers ────────────────────────────────────────────────────────

    private static void addTextFilter(Form<?> form, String id, WebMarkupContainer tableBody) {
        TextField<String> tf = new TextField<>(id);
        tf.add(new AjaxFormComponentUpdatingBehavior("input") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) { target.add(tableBody); }
        });
        form.add(tf);
    }

    private static void addDropFilter(Form<?> form, String id,
                                      List<String> choices, WebMarkupContainer tableBody) {
        DropDownChoice<String> dd = new DropDownChoice<>(id, choices);
        dd.setNullValid(true);
        dd.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) { target.add(tableBody); }
        });
        form.add(dd);
    }

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

    // ── New Order form ────────────────────────────────────────────────────────

    private Form<NewOrderModel> buildNewOrderForm(NewOrderModel model) {
        Form<NewOrderModel> form = new Form<>("newOrderForm",
                new CompoundPropertyModel<>(model));

        FeedbackPanel feedback = new FeedbackPanel("feedback");
        feedback.setOutputMarkupId(true);
        form.add(feedback);

        form.add(new TextField<String>("symbol").setRequired(true));
        form.add(new DropDownChoice<>("side", List.of("BUY", "SELL")).setRequired(true));
        form.add(new NumberTextField<BigDecimal>("quantity", BigDecimal.class)
                .setMinimum(BigDecimal.ONE).setRequired(true));
        form.add(new NumberTextField<BigDecimal>("price", BigDecimal.class)
                .setMinimum(BigDecimal.ZERO));
        form.add(new DropDownChoice<>("ordType",
                List.of("Limit", "Market", "Stop", "StopLimit")).setRequired(true));
        form.add(new DropDownChoice<>("timeInForce",
                List.of("Day", "GTC", "IOC", "FOK")).setRequired(true));

        // Extra fields from template (non-standard UserInput / Enumeration)
        WebMarkupContainer extraSection = new WebMarkupContainer("newOrderExtraSection") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(!model.extraFields.isEmpty());
            }
        };
        extraSection.add(new ListView<ExtraEntry>("newOrderExtraRows",
                new PropertyModel<>(model, "extraFields")) {
            @Override
            protected void populateItem(ListItem<ExtraEntry> item) {
                renderExtraFieldRow(item);
            }
        });
        form.add(extraSection);

        form.add(new AjaxButton("sendBtn", form) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                String sessionId = FixSimulatorSession.get().getActiveSessionId();
                if (sessionId == null) {
                    error("No active FIX session selected. Please select a session from the top bar.");
                    target.add(feedback);
                    return;
                }
                try {
                    if (model.templateId != null) {
                        TemplateService ts = templateSvc();
                        if (ts == null) throw new IllegalStateException("Template service not available.");
                        String tmplName = ts.findById(model.templateId)
                                .map(t -> t.name()).orElse(model.templateId);
                        log.info("New Order sent: session='{}' template='{}' (id={}) symbol='{}' side={} qty={} price={}",
                                sessionId, tmplName, model.templateId,
                                model.symbol, model.side, model.quantity, model.price);
                        ts.send(sessionId, model.templateId, buildNewOverrides(model));
                    } else {
                        // Fallback: direct OrderService send (no template configured)
                        OrderService os = orderSvc();
                        if (os == null) throw new IllegalStateException("Order service not available.");
                        log.info("New Order sent (no template): session='{}' symbol='{}' side={} qty={} price={}",
                                sessionId, model.symbol, model.side, model.quantity, model.price);
                        Map<Integer, String> fields = new HashMap<>();
                        fields.put(55, model.symbol);
                        fields.put(54, sideCode(model.side));
                        fields.put(38, model.quantity.toPlainString());
                        if (model.price != null) fields.put(44, model.price.toPlainString());
                        fields.put(40, ordTypeCode(model.ordType));
                        fields.put(59, tifCode(model.timeInForce));
                        os.sendNewOrder(sessionId, fields);
                    }
                    model.reset();
                    target.appendJavaScript(
                            "bootstrap.Modal.getInstance(document.getElementById('newOrderModal')).hide();");
                } catch (Exception e) {
                    error("Failed to send order: " + e.getMessage());
                    target.add(feedback);
                }
            }

            @Override
            protected void onError(AjaxRequestTarget target) { target.add(feedback); }
        });

        return form;
    }

    // ── Amend Order form ──────────────────────────────────────────────────────

    private Form<AmendModel> buildAmendOrderForm(AmendModel model) {
        Form<AmendModel> form = new Form<>("amendOrderForm",
                new CompoundPropertyModel<>(model));

        FeedbackPanel feedback = new FeedbackPanel("amendFeedback");
        feedback.setOutputMarkupId(true);
        form.add(feedback);

        form.add(new Label("origClOrdIdDisplay",
                new PropertyModel<String>(model, "origClOrdId")));
        form.add(new TextField<String>("symbol").setRequired(true));
        form.add(new DropDownChoice<>("side", List.of("BUY", "SELL")).setRequired(true));
        form.add(new NumberTextField<BigDecimal>("quantity", BigDecimal.class)
                .setMinimum(BigDecimal.ONE).setRequired(true));
        form.add(new NumberTextField<BigDecimal>("price", BigDecimal.class)
                .setMinimum(BigDecimal.ZERO));
        form.add(new DropDownChoice<>("ordType",
                List.of("Limit", "Market", "Stop", "StopLimit")).setRequired(true));
        form.add(new DropDownChoice<>("timeInForce",
                List.of("Day", "GTC", "IOC", "FOK")).setRequired(true));

        WebMarkupContainer extraSection = new WebMarkupContainer("amendExtraSection") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(!model.extraFields.isEmpty());
            }
        };
        extraSection.add(new ListView<ExtraEntry>("amendExtraRows",
                new PropertyModel<>(model, "extraFields")) {
            @Override
            protected void populateItem(ListItem<ExtraEntry> item) {
                renderExtraFieldRow(item);
            }
        });
        form.add(extraSection);

        form.add(new AjaxButton("amendSendBtn", form) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                String sessionId = FixSimulatorSession.get().getActiveSessionId();
                if (sessionId == null) {
                    error("No active FIX session selected.");
                    target.add(feedback);
                    return;
                }
                if (blank(model.origClOrdId)) {
                    error("OrigClOrdID is required — open this dialog from the order table.");
                    target.add(feedback);
                    return;
                }
                TemplateService ts = templateSvc();
                if (ts == null || model.templateId == null) {
                    error("No Amend Order template found (MsgType=G). "
                            + "Create one in FIX Message Templates.");
                    target.add(feedback);
                    return;
                }
                try {
                    String tmplName = ts.findById(model.templateId)
                            .map(t -> t.name()).orElse(model.templateId);
                    log.info("Amend Order sent: session='{}' template='{}' (id={}) origClOrdId='{}' symbol='{}' side={} qty={} price={}",
                            sessionId, tmplName, model.templateId,
                            model.origClOrdId, model.symbol, model.side, model.quantity, model.price);
                    ts.send(sessionId, model.templateId, buildAmendOverrides(model));
                    target.appendJavaScript(
                            "bootstrap.Modal.getInstance(document.getElementById('amendOrderModal')).hide();");
                } catch (Exception e) {
                    error("Failed to send amend: " + e.getMessage());
                    target.add(feedback);
                }
            }

            @Override
            protected void onError(AjaxRequestTarget target) { target.add(feedback); }
        });

        return form;
    }

    // ── Extra field row rendering ─────────────────────────────────────────────

    private static void renderExtraFieldRow(ListItem<ExtraEntry> item) {
        ExtraEntry entry = item.getModelObject();
        item.add(new Label("extraFieldLabel", entry.displayName));

        WebMarkupContainer textDiv = new WebMarkupContainer("extraTextDiv");
        textDiv.setVisible(entry.type == ExtraEntry.Type.TEXT);
        textDiv.add(new TextField<>("extraFieldValue", new PropertyModel<>(entry, "value")));
        item.add(textDiv);

        WebMarkupContainer enumDiv = new WebMarkupContainer("extraEnumDiv");
        enumDiv.setVisible(entry.type == ExtraEntry.Type.ENUM);
        enumDiv.add(new DropDownChoice<>("extraFieldEnum",
                new PropertyModel<>(entry, "value"),
                entry.options != null ? entry.options : List.of()));
        item.add(enumDiv);
    }

    // ── Template helpers ──────────────────────────────────────────────────────

    private static FixMessageTemplate findTemplate(String msgType) {
        TemplateService ts = templateSvc();
        if (ts == null) return null;
        String sid = FixSimulatorSession.get().getActiveSessionId();
        List<FixMessageTemplate> candidates = sid != null
                ? ts.findVisibleTo(sid)
                : ts.findAll();
        return candidates.stream()
                .filter(t -> msgType.equals(t.msgType()))
                .findFirst().orElse(null);
    }

    private static List<ExtraEntry> loadExtraFields(FixMessageTemplate tmpl) {
        List<ExtraEntry> extras = new ArrayList<>();
        if (tmpl == null) return extras;
        for (FieldSpec spec : tmpl.fields()) {
            if (STANDARD_TAGS.contains(spec.tag())) continue;
            if (spec.value() instanceof FieldValue.UserInput ui) {
                String label = ui.name() + "  (tag " + spec.tag() + ")";
                extras.add(ExtraEntry.text(ui.name(), label, ui.defaultValue()));
            } else if (spec.value() instanceof FieldValue.Enumeration en) {
                String label = en.name() + "  (tag " + spec.tag() + ")";
                extras.add(ExtraEntry.enumeration(en.name(), label, en.options(), en.defaultOption()));
            }
        }
        return extras;
    }

    private static Map<String, String> buildNewOverrides(NewOrderModel model) {
        Map<String, String> overrides = new HashMap<>();
        if (!blank(model.symbol))      overrides.put("symbol",      model.symbol);
        if (!blank(model.side))        overrides.put("side",        sideCode(model.side));
        if (model.quantity != null)    overrides.put("quantity",    model.quantity.toPlainString());
        if (model.price != null)       overrides.put("price",       model.price.toPlainString());
        if (!blank(model.ordType))     overrides.put("ordType",     ordTypeCode(model.ordType));
        if (!blank(model.timeInForce)) overrides.put("timeInForce", tifCode(model.timeInForce));
        for (ExtraEntry e : model.extraFields) {
            if (!blank(e.value)) overrides.put(e.name, e.value);
        }
        return overrides;
    }

    private static Map<String, String> buildAmendOverrides(AmendModel model) {
        Map<String, String> overrides = new HashMap<>();
        if (!blank(model.origClOrdId))  overrides.put("origClOrdId",  model.origClOrdId);
        if (!blank(model.symbol))       overrides.put("symbol",       model.symbol);
        if (!blank(model.side))         overrides.put("side",         sideCode(model.side));
        if (model.quantity != null)     overrides.put("quantity",     model.quantity.toPlainString());
        if (model.price != null)        overrides.put("price",        model.price.toPlainString());
        if (!blank(model.ordType))      overrides.put("ordType",      ordTypeCode(model.ordType));
        if (!blank(model.timeInForce))  overrides.put("timeInForce",  tifCode(model.timeInForce));
        for (ExtraEntry e : model.extraFields) {
            if (!blank(e.value)) overrides.put(e.name, e.value);
        }
        return overrides;
    }

    // ── Service lookups ───────────────────────────────────────────────────────

    private static OrderService orderSvc() {
        return ((FixSimulatorApplication) Application.get()).getOrderService();
    }

    private static TemplateService templateSvc() {
        return ((FixSimulatorApplication) Application.get()).getTemplateService();
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
            case "3" -> "DoneForDay";
            case "4" -> "Cancelled";
            case "5" -> "Replaced";
            case "6" -> "PendCxl";
            case "7" -> "Stopped";
            case "8" -> "Rejected";
            case "9" -> "Suspended";
            case "A" -> "PendingNew";
            case "C" -> "Expired";
            case "E" -> "PendReplace";
            default  -> blank(code) ? "Sent" : code;
        };
    }

    private static String statusBadgeClass(String code) {
        return switch (code) {
            case "0" -> "bg-primary";           // New — blue
            case "1" -> "bg-warning text-dark"; // PartFilled — amber
            case "2" -> "bg-success";           // Filled — green
            case "3" -> "bg-secondary";         // DoneForDay — grey
            case "4" -> "bg-secondary";         // Cancelled — grey
            case "5" -> "bg-info text-dark";    // Replaced — teal
            case "6" -> "bg-warning text-dark"; // PendCxl — amber
            case "7" -> "bg-secondary";         // Stopped — grey
            case "8" -> "bg-danger";            // Rejected — red
            case "9" -> "bg-secondary";         // Suspended — grey
            case "A" -> "bg-info text-dark";    // PendingNew — teal
            case "C" -> "bg-secondary";         // Expired — grey
            case "E" -> "bg-warning text-dark"; // PendReplace — amber
            default  -> "bg-light text-dark border";
        };
    }

    /**
     * Returns a Bootstrap table-row class that colours the row by lifecycle state.
     */
    private static String rowClass(String ordStatus) {
        return switch (ordStatus) {
            case "2"      -> "table-success";          // Filled
            case "3", "C" -> "table-secondary";        // DoneForDay / Expired
            case "4"      -> "table-secondary";        // Cancelled
            case "8"      -> "table-danger";           // Rejected
            case "1"      -> "table-warning";          // PartFilled
            case "6", "E" -> "table-info";             // PendCxl / PendReplace
            default       -> "";
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

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    // ── FIX code mapping ──────────────────────────────────────────────────────

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
        String filterSide;
        String filterOrdType;
        String filterTimeInForce;
        String filterStatus;
    }

    static class NewOrderModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String     templateId;
        String     symbol      = "";
        String     side        = "BUY";
        BigDecimal quantity    = BigDecimal.valueOf(100);
        BigDecimal price;
        String     ordType     = "Limit";
        String     timeInForce = "Day";
        List<ExtraEntry> extraFields = new ArrayList<>();

        void reset() {
            symbol = ""; side = "BUY";
            quantity = BigDecimal.valueOf(100); price = null;
            ordType = "Limit"; timeInForce = "Day";
            extraFields.forEach(e -> e.value = e.defaultValue != null ? e.defaultValue : "");
        }
    }

    static class AmendModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String     templateId;
        String     origClOrdId;
        String     symbol      = "";
        String     side        = "BUY";
        BigDecimal quantity;
        BigDecimal price;
        String     ordType     = "Limit";
        String     timeInForce = "Day";
        List<ExtraEntry> extraFields = new ArrayList<>();
    }

    static class ExtraEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        enum Type { TEXT, ENUM }

        final String name;
        final String displayName;
        final Type   type;
        String       value;
        final String defaultValue;
        final List<String> options;

        private ExtraEntry(String name, String displayName, Type type,
                           String defaultValue, List<String> options) {
            this.name         = name;
            this.displayName  = displayName;
            this.type         = type;
            this.defaultValue = defaultValue;
            this.value        = defaultValue != null ? defaultValue : "";
            this.options      = options != null ? options : List.of();
        }

        static ExtraEntry text(String name, String displayName, String defaultValue) {
            return new ExtraEntry(name, displayName, Type.TEXT, defaultValue, null);
        }

        static ExtraEntry enumeration(String name, String displayName,
                                      List<String> options, String defaultOption) {
            return new ExtraEntry(name, displayName, Type.ENUM, defaultOption, options);
        }

        public String getValue()        { return value; }
        public void setValue(String v)  { value = v; }
    }
}
