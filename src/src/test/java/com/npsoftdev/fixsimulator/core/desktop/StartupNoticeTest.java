package com.npsoftdev.fixsimulator.core.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JCheckBox;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what can be checked without a display: the wording the user is given
 * and the fact that a suppressed notice is not shown again. Swing components
 * construct fine headless — only windows do not — so the panel itself is built
 * here, just never made visible.
 */
class StartupNoticeTest {

    @TempDir
    Path dataDir;

    @Test
    void tellsTheUserTheAddressToOpen() {
        assertTrue(StartupNotice.messageHtml("http://localhost:8080").contains("http://localhost:8080"));
    }

    @Test
    void tellsTheUserHowToStopTheApp() {
        String html = StartupNotice.messageHtml("http://localhost:8080");

        assertTrue(html.contains("Exit FIXulator"),
                "the notice must name the menu item that stops the app");
        assertTrue(html.contains("right-click"),
                "the notice must say how to reach that menu item");
    }

    @Test
    void saysThatClosingTheBrowserLeavesItRunning() {
        assertTrue(StartupNotice.messageHtml("http://localhost:8080").contains("does not stop"));
    }

    @Test
    void namesTheTrayTheWayTheHostOsDoes() {
        String os = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Windows 11");
            assertTrue(StartupNotice.messageHtml("http://x").contains("notification area"));

            System.setProperty("os.name", "Mac OS X");
            assertTrue(StartupNotice.messageHtml("http://x").contains("menu bar"));

            System.setProperty("os.name", "Linux");
            assertTrue(StartupNotice.messageHtml("http://x").contains("system tray"));
        } finally {
            System.setProperty("os.name", os);
        }
    }

    /**
     * Deliberately does not build the panel. {@code noticePanel} puts the
     * message in a JLabel as HTML, and Swing's HTML renderer initialises the
     * font manager, which needs native libfreetype — absent from slim
     * containers and CI images, where this failed with an UnsatisfiedLinkError.
     * The checkbox's default state is the part worth asserting, and it needs no
     * layout, so build only that.
     */
    @Test
    void theCheckboxStartsUnticked() {
        JCheckBox dontShowAgain = new JCheckBox(StartupNotice.SUPPRESS_LABEL);

        assertFalse(dontShowAgain.isSelected(),
                "the user must opt in to suppressing the notice, never out");
        assertEquals("Don't show this message again", dontShowAgain.getText());
    }

    @Test
    void aSuppressedNoticeIsNotShownAgain() {
        DesktopSettings settings = new DesktopSettings(dataDir);
        settings.suppressStartupNotice();

        // showIfNeeded returns without touching AWT when suppressed, which is
        // what makes this safe to call on a headless build agent.
        StartupNotice.showIfNeeded("http://localhost:8080", settings);

        assertTrue(settings.isStartupNoticeSuppressed());
    }
}
