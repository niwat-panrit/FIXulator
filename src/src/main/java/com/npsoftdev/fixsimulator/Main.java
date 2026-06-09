package com.npsoftdev.fixsimulator;

import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.server.Server;

import java.net.URL;

/**
 * Embedded-Jetty launcher for the FIX Simulator.
 *
 * <p>Usage: {@code java -jar fix-simulator.jar [port]}  (default port: 8080)</p>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        for (String arg : args) {
            try { port = Integer.parseInt(arg); } catch (NumberFormatException ignored) {}
        }

        Server server = new Server(port);

        WebAppContext context = new WebAppContext();
        context.setContextPath("/");
        // Parent-first so Wicket / QuickFIX/J classes resolve from the flat fat-JAR classpath
        context.setParentLoaderPriority(true);

        // Locate the webapp root: WEB-INF/web.xml is included in the fat JAR via
        // the <resources> entry in pom.xml that copies src/main/webapp onto the classpath.
        URL webXml = Main.class.getResource("/WEB-INF/web.xml");
        if (webXml == null) {
            throw new IllegalStateException(
                    "WEB-INF/web.xml not found on classpath — " +
                    "please rebuild with 'mvn clean package'.");
        }
        // Strip "WEB-INF/web.xml" to get the root URI (works for both file: and jar: URLs)
        String base = webXml.toExternalForm();
        base = base.substring(0, base.length() - "WEB-INF/web.xml".length());
        context.setBaseResourceAsString(base);

        server.setHandler(context);
        server.start();

        System.out.printf("%n  FIX Simulator  →  http://localhost:%d%n%n", port);

        server.join();
    }
}
