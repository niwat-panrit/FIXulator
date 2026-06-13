package com.npsoftdev.fixsimulator.pages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.npsoftdev.fixsimulator.service.LogFileService;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.head.CssReferenceHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.handler.resource.ResourceStreamRequestHandler;
import org.apache.wicket.request.resource.ContentDisposition;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.util.resource.FileResourceStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SystemLogsPage extends BasePage {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int INITIAL_TAIL_LINES = 500;

    private final AbstractDefaultAjaxBehavior tailBehavior;

    public SystemLogsPage() {
        super();

        // ── AJAX tail endpoint ────────────────────────────────────────────────
        tailBehavior = new AbstractDefaultAjaxBehavior() {
            @Override
            protected void respond(AjaxRequestTarget target) {
                long since = RequestCycle.get().getRequest()
                        .getRequestParameters().getParameterValue("since").toLong(0L);

                LogFileService svc = app().getLogFileService();
                List<String> lines  = svc != null ? svc.readFrom(since) : List.of();
                long nextOffset     = svc != null ? svc.fileSizeBytes() : 0L;

                try {
                    String js = "SystemLogs.onData({lines:"
                            + JSON.writeValueAsString(lines)
                            + ",nextOffset:" + nextOffset + "});";
                    target.appendJavaScript(js);
                } catch (JsonProcessingException e) {
                    target.appendJavaScript(
                            "SystemLogs.onData({lines:[],nextOffset:" + nextOffset + "});");
                }
            }
        };
        add(tailBehavior);

        // ── Download link ─────────────────────────────────────────────────────
        add(new Link<Void>("downloadLink") {
            @Override
            public void onClick() {
                LogFileService svc = app().getLogFileService();
                if (svc == null) return;
                Path logFile = svc.getActiveLogFile();
                if (logFile == null || !Files.exists(logFile)) return;

                FileResourceStream stream = new FileResourceStream(
                        new org.apache.wicket.util.file.File(logFile.toFile()));
                getRequestCycle().scheduleRequestHandlerAfterCurrent(
                        new ResourceStreamRequestHandler(stream)
                                .setFileName(logFile.getFileName().toString())
                                .setContentDisposition(ContentDisposition.ATTACHMENT));
            }
        });
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);

        response.render(CssReferenceHeaderItem.forReference(
                new PackageResourceReference(SystemLogsPage.class, "SystemLogsPage.css")));
        response.render(JavaScriptHeaderItem.forReference(
                new PackageResourceReference(SystemLogsPage.class, "SystemLogsPage.js")));

        // Bootstrap initial data into the viewer
        LogFileService svc = app().getLogFileService();
        List<String> initialLines = svc != null ? svc.readTail(INITIAL_TAIL_LINES) : List.of();
        long initialOffset        = svc != null ? svc.fileSizeBytes() : 0L;

        try {
            String initScript = "SystemLogs.init({"
                    + "tailUrl:"    + JSON.writeValueAsString(
                            tailBehavior.getCallbackUrl().toString()) + ","
                    + "nextOffset:" + initialOffset + ","
                    + "lines:"      + JSON.writeValueAsString(initialLines)
                    + "});";
            response.render(OnDomReadyHeaderItem.forScript(initScript));
        } catch (JsonProcessingException e) {
            response.render(OnDomReadyHeaderItem.forScript(
                    "SystemLogs.init({tailUrl:'',nextOffset:0,lines:[]});"));
        }
    }
}
