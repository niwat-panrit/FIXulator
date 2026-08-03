package com.npsoftdev.fixsimulator;

import com.npsoftdev.fixsimulator.core.AppHome;

import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.SessionHandler;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Embedded-Jetty launcher for the FIX Simulator.
 *
 * <p>Usage: {@code java -jar fix-simulator.jar [port]}  (default port: 8080)</p>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // All three calls must happen before any SLF4J logger is first accessed,
        // so that Logback picks up the system properties when it initialises.
        applyOsTimezone();
        applyStartupLogName();
        applyLogDirectory();

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

        // Harden the JSESSIONID cookie: HttpOnly prevents JS access;
        // SameSite=Strict blocks cross-site requests from carrying the cookie.
        // (Secure flag is omitted here — enable it once TLS is configured.)
        SessionHandler sessionHandler = new SessionHandler();
        sessionHandler.getSessionCookieConfig().setHttpOnly(true);
        sessionHandler.getSessionCookieConfig().setAttribute("SameSite", "Strict");
        context.setSessionHandler(sessionHandler);

        server.setHandler(context);
        server.start();

        System.out.printf("%n  FIX Simulator  →  http://localhost:%d%n%n", port);

        server.join();
    }

    /**
     * Sets the {@code app.log.name} system property to {@code app-YYYYMMDD-HHmmss}
     * using the current local time (after {@link #applyOsTimezone()} has run).
     * Logback reads this property when it initialises its file appender.
     * No-op if the property is already set externally.
     */
    private static void applyStartupLogName() {
        if (System.getProperty("app.log.name") != null) return;
        String ts = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        System.setProperty("app.log.name", "app-" + ts);
    }

    /**
     * Points {@code app.log.dir} — which {@code logback.xml} reads when it
     * initialises — at {@code <app home>/logs}, and creates the directory.
     *
     * <p>In a source checkout the app home is the working directory, so this
     * resolves to the same {@code logs/} used before. In an installed build it
     * moves logging under the per-user application-data directory, because the
     * install location is not writable. See {@link AppHome}.</p>
     *
     * <p>No-op if the property is already set externally.</p>
     */
    private static void applyLogDirectory() {
        if (System.getProperty("app.log.dir") != null) return;
        Path logDir = AppHome.resolve().resolve("logs");
        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            // Leave the property unset and let Logback fall back to ./logs —
            // failing to start over a log directory would be worse.
            System.err.println("Could not create log directory " + logDir + ": " + e.getMessage());
            return;
        }
        System.setProperty("app.log.dir", logDir.toString());
    }

    /**
     * Detects the host OS timezone and applies it as the JVM default so that
     * Logback (and all other date/time formatting) uses local time rather than UTC.
     *
     * <p>Detection order:
     * <ol>
     *   <li>{@code -Duser.timezone} — already honoured by the JVM; skip if set.</li>
     *   <li>{@code TZ} environment variable (works on all platforms).</li>
     *   <li>Linux / macOS — {@code /etc/timezone} (plain text IANA name).</li>
     *   <li>Linux / macOS — {@code /etc/localtime} symlink → extract IANA name
     *       from the path (e.g. {@code …/zoneinfo/Asia/Bangkok}).</li>
     *   <li>Windows — {@code HKLM\SYSTEM\CurrentControlSet\Control\TimeZoneInformation}
     *       via {@code reg query}; maps Windows zone name to IANA via
     *       {@link TimeZone#getTimeZone}.</li>
     * </ol>
     * Falls back silently to the existing JVM default if nothing is found.
     */
    private static void applyOsTimezone() {
        // 1. Explicit JVM property takes priority — nothing to do
        if (System.getProperty("user.timezone") != null) return;

        String tzId = null;

        // 2. TZ environment variable
        tzId = nonBlank(System.getenv("TZ"));

        // 3. Linux/macOS — /etc/timezone
        if (tzId == null) {
            tzId = readFile(Paths.get("/etc/timezone"));
        }

        // 4. Linux/macOS — /etc/localtime symlink
        if (tzId == null) {
            try {
                Path lt = Paths.get("/etc/localtime");
                if (Files.isSymbolicLink(lt)) {
                    String target = Files.readSymbolicLink(lt).toString()
                            .replace('\\', '/');
                    int zi = target.indexOf("zoneinfo/");
                    if (zi >= 0) tzId = target.substring(zi + "zoneinfo/".length());
                }
            } catch (Exception ignored) {}
        }

        // 5. Windows — query registry
        if (tzId == null && System.getProperty("os.name", "").toLowerCase().contains("win")) {
            tzId = windowsTimezone();
        }

        if (tzId != null) {
            tzId = tzId.trim();
            TimeZone tz = TimeZone.getTimeZone(tzId);
            // getTimeZone returns GMT for unknown IDs — skip silently in that case
            if (!tzId.equals("GMT") && tz.getID().equals("GMT") && !tzId.startsWith("GMT")) {
                return; // unrecognised ID — leave JVM default alone
            }
            TimeZone.setDefault(tz);
        }
    }

    /** Reads the first non-blank line of a text file, or returns {@code null}. */
    private static String readFile(Path p) {
        try {
            if (!Files.exists(p)) return null;
            return Files.readAllLines(p).stream()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .findFirst().orElse(null);
        } catch (Exception ignored) { return null; }
    }

    /** Queries the Windows registry for the current timezone name. */
    private static String windowsTimezone() {
        try {
            Process p = new ProcessBuilder(
                    "reg", "query",
                    "HKLM\\SYSTEM\\CurrentControlSet\\Control\\TimeZoneInformation",
                    "/v", "TimeZoneKeyName")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            // Output line: "    TimeZoneKeyName    REG_SZ    SE Asia Standard Time"
            for (String line : out.split("\r?\n")) {
                if (line.contains("TimeZoneKeyName")) {
                    String[] parts = line.trim().split("\\s{2,}");
                    if (parts.length >= 3) return parts[parts.length - 1].trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String nonBlank(String s) {
        return (s != null && !s.isBlank()) ? s.trim() : null;
    }
}
