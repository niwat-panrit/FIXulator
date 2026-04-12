package com.example.bean;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.primefaces.model.charts.bar.BarChartModel;
import org.primefaces.model.charts.bar.BarChartDataSet;
import org.primefaces.model.charts.ChartData;
import org.primefaces.model.charts.pie.PieChartModel;
import org.primefaces.model.charts.pie.PieChartDataSet;

import java.util.Arrays;
import java.util.List;

/**
 * Provides chart models for the dashboard page.
 *
 * PrimeFaces charts are built entirely in Java — the library serialises the
 * model to Chart.js–compatible JSON on the server side, so there is zero
 * hand-written JavaScript in the application.
 */
@Named
@RequestScoped
public class ChartBean {

    @Inject
    private TaskBean taskBean;

    private BarChartModel barModel;
    private PieChartModel pieModel;

    @PostConstruct
    public void init() {
        buildBarChart();
        buildPieChart();
    }

    // ── Bar chart: tasks per priority ──────────────────────────────────────

    private void buildBarChart() {
        barModel = new BarChartModel();
        ChartData data = new ChartData();

        BarChartDataSet ds = new BarChartDataSet();
        ds.setLabel("Tasks by Priority");

        long high   = taskBean.getTasks().stream().filter(t -> "High".equals(t.getPriority())).count();
        long medium = taskBean.getTasks().stream().filter(t -> "Medium".equals(t.getPriority())).count();
        long low    = taskBean.getTasks().stream().filter(t -> "Low".equals(t.getPriority())).count();

        ds.setData(Arrays.asList((Number) high, (Number) medium, (Number) low));
        ds.setBackgroundColor(Arrays.asList(
            "rgba(239,68,68,0.7)",    // red  – High
            "rgba(234,179,8,0.7)",    // amber – Medium
            "rgba(34,197,94,0.7)"     // green – Low
        ));
        ds.setBorderColor(Arrays.asList(
            "rgba(239,68,68,1)",
            "rgba(234,179,8,1)",
            "rgba(34,197,94,1)"
        ));

        data.addChartDataSet(ds);
        data.setLabels(Arrays.asList("High", "Medium", "Low"));
        barModel.setData(data);
    }

    // ── Pie chart: done vs pending ─────────────────────────────────────────

    private void buildPieChart() {
        pieModel = new PieChartModel();
        ChartData data = new ChartData();

        PieChartDataSet ds = new PieChartDataSet();

        long done    = taskBean.getDoneCount();
        long pending = taskBean.getPendingCount();

        ds.setData(Arrays.asList((Number) done, (Number) pending));
        ds.setBackgroundColor(Arrays.asList(
            "rgba(99,102,241,0.8)",   // indigo – Done
            "rgba(156,163,175,0.8)"   // gray   – Pending
        ));

        data.addChartDataSet(ds);
        data.setLabels(Arrays.asList("Done", "Pending"));
        pieModel.setData(data);
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public BarChartModel getBarModel() { return barModel; }
    public PieChartModel getPieModel() { return pieModel; }
}
