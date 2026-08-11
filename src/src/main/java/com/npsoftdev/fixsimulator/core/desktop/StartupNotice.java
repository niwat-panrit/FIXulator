package com.npsoftdev.fixsimulator.core.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

/**
 * The one-time dialog that explains where FIXulator went.
 *
 * <p>Launching a desktop install produces no window — the app is a web server
 * with a tray icon — which on a first run looks indistinguishable from a
 * failure to start. This says what happened, where the UI is, and how to stop
 * it, and then gets out of the way: the "Don't show this message again"
 * checkbox is recorded in {@link DesktopSettings} and honoured from then on.</p>
 *
 * <p>Only shown when a tray icon actually exists — without one the advice to
 * quit from the tray would be wrong.</p>
 */
public final class StartupNotice {

    private static final Logger log = LoggerFactory.getLogger(StartupNotice.class);

    private static final String OPEN  = "Open FIXulator";
    private static final String CLOSE = "Close";

    /** The checkbox the user ticks to never see this dialog again. */
    static final String SUPPRESS_LABEL = "Don't show this message again";

    private StartupNotice() {}

    /**
     * Shows the notice unless the user has already dismissed it for good.
     * Returns immediately — the dialog is built and shown on the AWT event
     * thread so that it never delays server startup.
     *
     * @param url      the address the UI is served on
     * @param settings where the "don't show again" choice is read and written
     */
    public static void showIfNeeded(String url, DesktopSettings settings) {
        if (GraphicsEnvironment.isHeadless()) return;
        if (settings.isStartupNoticeSuppressed()) {
            log.debug("Startup notice suppressed by a previous run");
            return;
        }

        EventQueue.invokeLater(() -> {
            try {
                log.info("Showing the first-run startup notice");
                show(url, settings);
            } catch (RuntimeException e) {
                // The app is already serving; a dialog that will not paint is
                // not a reason to take it down.
                log.warn("Could not show the startup notice: {}", e.toString());
            }
        });
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static void show(String url, DesktopSettings settings) {
        applySystemLookAndFeel();

        JCheckBox dontShowAgain = new JCheckBox(SUPPRESS_LABEL);
        JPanel panel = noticePanel(url, dontShowAgain);

        // An owner window that is itself always-on-top is what brings the dialog
        // to the front: a dialog owned by null opens behind whatever the user is
        // working in, which for a first-run explanation defeats the point.
        JFrame owner = alwaysOnTopOwner();
        try {
            Object[] options = { OPEN, CLOSE };
            // The app's own icon rather than the message-type default: it ties
            // the dialog to the tray icon the text is telling the user to find.
            int choice = JOptionPane.showOptionDialog(
                    owner, panel, "FIXulator is running",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    new ImageIcon(AppIcon.render(48)), options, OPEN);

            // Saved whichever way the dialog was closed, including the window's
            // X button — the checkbox is an instruction, not part of the answer.
            if (dontShowAgain.isSelected()) {
                log.info("User opted out of the startup notice");
                settings.suppressStartupNotice();
            }

            if (choice == 0) TrayIntegration.openInBrowser(url);
        } finally {
            owner.dispose();
        }
    }

    /**
     * Builds the dialog's body. Package-private and free of any {@code Window},
     * so it can be constructed — and its wording asserted — in a headless test.
     */
    static JPanel noticePanel(String url, JCheckBox dontShowAgain) {
        dontShowAgain.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel message = new JLabel(messageHtml(url));
        message.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(message);
        panel.add(Box.createVerticalStrut(14));
        panel.add(dontShowAgain);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return panel;
    }

    /**
     * Paragraphs are separated with explicit {@code <br>} rather than
     * {@code <p>}: Swing's HTML 3.2 renderer collapses the margins between
     * block elements inside a {@code JLabel}, which runs the three points
     * together into one wall of text.
     */
    static String messageHtml(String url) {
        return "<html><body style='width:380px'>"
             + "<b>FIXulator is running.</b><br><br>"
             + "Open <b>" + url + "</b> in your browser to use it.<br><br>"
             + "It keeps running after you close the browser — closing the page "
             + "does not stop the simulator, so your FIX sessions stay up.<br><br>"
             + "To stop it, find the FIXulator icon in " + trayLocation()
             + ", right-click it and choose <b>Exit FIXulator</b>."
             + "</body></html>";
    }

    /** Names the tray the way the host OS's own documentation does. */
    private static String trayLocation() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "the notification area at the right-hand end of the taskbar "
                 + "(click the <b>^</b> arrow if you do not see it)";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "the menu bar at the top of the screen";
        }
        return "the system tray";
    }

    private static JFrame alwaysOnTopOwner() {
        JFrame owner = new JFrame();
        owner.setUndecorated(true);
        // UTILITY keeps this helper window out of the taskbar and Alt-Tab.
        owner.setType(Window.Type.UTILITY);
        owner.setAlwaysOnTop(true);
        owner.setIconImage(AppIcon.render(32));
        owner.setSize(0, 0);
        owner.setLocationRelativeTo(null);
        owner.setVisible(true);
        return owner;
    }

    private static void applySystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Metal instead of native chrome — cosmetic, not worth failing over.
            log.debug("System look and feel unavailable: {}", e.toString());
        }
    }
}
