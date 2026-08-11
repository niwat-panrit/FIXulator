package com.npsoftdev.fixsimulator.core.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Draws the FIXulator icon at whatever size the platform asks for.
 *
 * <p>The icon is painted rather than loaded from a file so that it is always
 * rendered at the exact pixel size the system tray reports — tray sizes differ
 * per OS and per display scale, and rescaling a fixed bitmap down to 16&nbsp;px
 * is what makes tray icons look muddy.</p>
 *
 * <p>The artwork mirrors the web UI's brand mark: the navbar's near-black
 * ({@code #0d1117}) rounded square with the Bootstrap {@code bi-activity} pulse
 * across it in the primary blue. An opaque badge is used deliberately — a
 * transparent glyph would have to pick one colour and would then disappear
 * against either a light or a dark taskbar.</p>
 */
public final class AppIcon {

    /** Navbar background — {@code .navbar-top} in {@code core/ui/app.css}. */
    private static final Color BADGE = new Color(0x0D1117);
    /** Bootstrap primary, lightened so it holds up against the dark badge. */
    private static final Color PULSE = new Color(0x3B82F6);

    /**
     * The pulse polyline in unit coordinates (0..1 of the icon box), scaled to
     * the requested size at paint time. Keeping it relative is what lets the
     * same shape render cleanly at 16 px and at 64 px.
     */
    private static final double[][] PULSE_POINTS = {
            {0.13, 0.55}, {0.32, 0.55}, {0.43, 0.23},
            {0.58, 0.79}, {0.69, 0.45}, {0.87, 0.45}
    };

    private AppIcon() {}

    /**
     * Renders the icon into a new ARGB image {@code size} pixels square.
     *
     * @param size edge length in pixels; values below 1 are treated as 1
     */
    public static BufferedImage render(int size) {
        int edge = Math.max(1, size);
        BufferedImage image = new BufferedImage(edge, edge, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);

            double corner = edge * 0.28;
            g.setColor(BADGE);
            g.fill(new RoundRectangle2D.Double(0, 0, edge, edge, corner, corner));

            // Round caps/joins keep the spikes from breaking up once the stroke
            // is only a couple of pixels wide.
            g.setColor(PULSE);
            g.setStroke(new BasicStroke((float) Math.max(1.0, edge * 0.13),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(pulsePath(edge));
        } finally {
            g.dispose();
        }
        return image;
    }

    private static GeneralPath pulsePath(int edge) {
        GeneralPath path = new GeneralPath();
        for (int i = 0; i < PULSE_POINTS.length; i++) {
            float x = (float) (PULSE_POINTS[i][0] * edge);
            float y = (float) (PULSE_POINTS[i][1] * edge);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        return path;
    }
}
