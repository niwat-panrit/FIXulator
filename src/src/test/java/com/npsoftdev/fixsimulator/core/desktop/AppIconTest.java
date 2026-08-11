package com.npsoftdev.fixsimulator.core.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppIconTest {

    /** 16 is the usual Windows tray size; the rest cover HiDPI and the dialog icon. */
    @ParameterizedTest
    @ValueSource(ints = {16, 20, 24, 32, 64})
    void rendersAtExactlyTheRequestedSize(int size) {
        BufferedImage image = AppIcon.render(size);

        assertEquals(size, image.getWidth());
        assertEquals(size, image.getHeight());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void degeneratesToASinglePixelRatherThanThrowing(int size) {
        assertEquals(1, AppIcon.render(size).getWidth());
    }

    @Test
    void theBadgeIsOpaqueSoItReadsOnAnyTaskbarColour() {
        BufferedImage image = AppIcon.render(32);

        int centre = image.getRGB(16, 16);
        assertEquals(0xFF, (centre >>> 24), "centre pixel should be fully opaque");
    }

    @Test
    void theCornersAreRounded() {
        BufferedImage image = AppIcon.render(32);

        assertEquals(0, image.getRGB(0, 0) >>> 24, "top-left corner should be transparent");
        assertEquals(0, image.getRGB(31, 31) >>> 24, "bottom-right corner should be transparent");
    }

    @Test
    void thePulseIsDrawnOnTopOfTheBadge() {
        BufferedImage image = AppIcon.render(64);

        // The badge is a single flat colour, so any second opaque colour in the
        // image can only be the pulse stroke. Sampled along the top edge, which
        // the pulse polyline never reaches.
        int badge = image.getRGB(32, 3);
        boolean foundStroke = false;
        for (int x = 0; x < 64 && !foundStroke; x++) {
            for (int y = 0; y < 64; y++) {
                int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) == 0xFF && pixel != badge) { foundStroke = true; break; }
            }
        }
        assertTrue(foundStroke, "expected the pulse stroke to be painted over the badge");
    }
}
