package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.FixSimulatorSession;
import com.npsoftdev.fixsimulator.service.OrderService;
import org.apache.wicket.Application;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrdersPage extends BasePage {

    public OrdersPage() {
        super();
        add(buildNewOrderForm(new NewOrderModel()));
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
                if (model.text != null && !model.text.isBlank()) {
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static OrderService orderSvc() {
        return ((FixSimulatorApplication) Application.get()).getOrderService();
    }

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

    // ── Model ─────────────────────────────────────────────────────────────────

    static class NewOrderModel implements Serializable {
        private static final long serialVersionUID = 1L;

        String symbol = "";
        String side = "BUY";
        BigDecimal quantity = BigDecimal.valueOf(100);
        BigDecimal price;
        String ordType = "Limit";
        String timeInForce = "Day";
        String text = "";

        void reset() {
            symbol = "";
            side = "BUY";
            quantity = BigDecimal.valueOf(100);
            price = null;
            ordType = "Limit";
            timeInForce = "Day";
            text = "";
        }
    }
}
