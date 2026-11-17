package com.upmerge.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class CubePaletteTest {
    @Test public void everyBaseCubeTierHasItsExpectedFlatColor() {
        assertEquals(0x64D9FF, CubePalette.rgb(2));
        assertEquals(0x60E5C4, CubePalette.rgb(4));
        assertEquals(0x8FE36E, CubePalette.rgb(8));
        assertEquals(0xFFDB69, CubePalette.rgb(16));
        assertEquals(0xFF9B57, CubePalette.rgb(32));
        assertEquals(0xFF657D, CubePalette.rgb(64));
        assertEquals(0xBB86FF, CubePalette.rgb(128));
        assertEquals(0x8292FF, CubePalette.rgb(256));
        assertEquals(0xFF75C8, CubePalette.rgb(512));
        assertEquals(0xF5F5FF, CubePalette.rgb(1024));
    }

    @Test public void unsupportedAndLargeTiersUseReadableFallbackColor() {
        assertEquals(0xF5F5FF, CubePalette.rgb(2048));
        assertEquals(0xF5F5FF, CubePalette.rgb(1L << 40));
    }

    @Test public void paletteValuesAreOpaqueRgbWithoutAlphaBits() {
        for (long value = 2; value <= 1024; value <<= 1) {
            int rgb = CubePalette.rgb(value);
            assertTrue("RGB must fit 24 bits for cube " + value, (rgb & 0xFF000000) == 0);
            assertTrue("RGB must not be black for cube " + value, rgb != 0);
        }
    }
}
