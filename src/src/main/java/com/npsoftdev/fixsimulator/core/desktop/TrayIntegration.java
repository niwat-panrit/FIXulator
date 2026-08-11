package com.npsoftdev.fixsimulator.core.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.net.URI;

/**
 * Puts FIXulator in the system tray so a desktop install can be opened and,
 * more importantly, stopped.
 *
 * <p>A packaged build has no console window and no application window: it
 * starts a web server and then simply sits there. Before this existed the only
 * way to stop it on Windows was to kill {@code FIXulator.exe} from Task
 * Manager. The tray icon gives it the two controls it was missing — <em>Open
 * FIXulator</em> and <em>Exit FIXulator</em>.</p>
 *
 * <p>Everything here is best-effort. A headless host (a container, a systemd
 * unit, a CI runner) has no tray, and the app must run there exactly as it did
 * before, so every failure path returns {@code false} and leaves the server
 * running untouched. Pass {@code -Dfixulator.tray=false} to opt out on a
 * desktop that does have one.</p>
 */
public final class TrayIntegration {

    private static final Logger log = LoggerFactory.getLogger(TrayIntegration.class);

    /** Set to {@code false} to run without a tray icon on a desktop host. */
    public static final String ENABLED_PROPERTY = "fixulator.tray";

    private TrayIntegration() {}

    /**
     * Installs the tray icon.
     *
     * @param url     the address the UI is served on; shown in the tooltip and
     *                opened by the menu
     * @param onExit  invoked when the user chooses Exit; run on its own thread,
     *                so it may block while it shuts the server down
     * @return {@code true} if an icon is now in the tray — the caller should
     *         only tell the user about the tray if this says so
     */
    public static boolean install(String url, Runnable onExit) {
        // Must be set before the first AWT class initialises, and the headless
        // check below is that first touch. A tray-resident app has no window to
        // switch to, so a Dock icon and a Cmd-Tab entry would both be dead ends.
        if (isMac()) System.setProperty("apple.awt.UIElement", "true");

        if (!Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"))) {
            log.info("Tray icon disabled by -D{}=false", ENABLED_PROPERTY);
            return false;
        }
        if (GraphicsEnvironment.isHeadless()) {
            log.info("Headless environment — running without a tray icon");
            return false;
        }
        if (!SystemTray.isSupported()) {
            log.info("No system tray on this desktop — running without a tray icon");
            return false;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();

            // Render at the size the tray asks for rather than letting AWT
            // downscale a fixed bitmap; setImageAutoSize covers displays whose
            // real size differs from the reported one (Windows HiDPI).
            Dimension size = tray.getTrayIconSize();
            TrayIcon icon = new TrayIcon(
                    AppIcon.render(Math.max(size.width, size.height)),
                    "FIXulator — " + url);
            icon.setImageAutoSize(true);
            icon.setPopupMenu(menu(tray, icon, url, onExit));

            // Double-click (Windows) / click (Linux) on the icon itself.
            icon.addActionListener(e -> openInBrowser(url));

            tray.add(icon);
            log.info("Tray icon installed; Exit is on its right-click menu");
            return true;
        } catch (AWTException | RuntimeException e) {
            log.warn("Could not install the tray icon: {}", e.toString());
            return false;
        }
    }

    /**
     * Opens {@code url} in the user's default browser, on a background thread —
     * launching a browser can block for seconds, and this is called from the
     * AWT event thread, which must stay responsive.
     */
    public static void openInBrowser(String url) {
        Thread t = new Thread(() -> browse(url), "fixulator-open-browser");
        t.setDaemon(true);
        t.start();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static PopupMenu menu(SystemTray tray, TrayIcon icon, String url, Runnable onExit) {
        PopupMenu menu = new PopupMenu();

        MenuItem open = new MenuItem("Open FIXulator");
        open.addActionListener(e -> openInBrowser(url));
        menu.add(open);

        menu.addSeparator();

        MenuItem exit = new MenuItem("Exit FIXulator");
        exit.addActionListener(e -> {
            // Remove the icon first: shutting down takes a moment, and an icon
            // that lingers after Exit reads as "nothing happened".
            tray.remove(icon);
            Thread t = new Thread(onExit, "fixulator-shutdown");
            t.start();
        });
        menu.add(exit);

        return menu;
    }

    private static void browse(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception e) {
            log.debug("Desktop.browse failed, falling back to the OS handler: {}", e.toString());
        }

        // Fallback for desktops where java.awt.Desktop is unavailable —
        // common on Linux, where BROWSE needs gnome-open/xdg-open anyway.
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (isMac()) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            log.warn("Could not open {} in a browser: {}", url, e.toString());
        }
    }

    private static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") || os.contains("darwin");
    }
}
