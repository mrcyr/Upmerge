package com.upmerge.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class NumberFormatterTest {
    @Test public void keepsSmallValuesUnabridged() {
        assertEquals("16383", NumberFormatter.compact(16383));
        assertEquals("16K", NumberFormatter.compact(16384));
    }

    @Test public void advancesSuffixAtPowersOf1024() {
        assertEquals("512T", NumberFormatter.compact(512L << 40));
        assertEquals("1Qa", NumberFormatter.compact(1L << 50));
        assertEquals("512Qa", NumberFormatter.compact(512L << 50));
        assertEquals("1Qi", NumberFormatter.compact(1L << 60));
        assertEquals("7Qi", NumberFormatter.compact(Long.MAX_VALUE));
    }

    @Test public void suffixBoundariesAdvanceOnlyAtNextPowerOf1024() {
        assertEquals("512T", NumberFormatter.compact((512L << 40) + (1L << 39)));
        assertEquals("1Qa", NumberFormatter.compact(1L << 50));
        assertEquals("512Qa", NumberFormatter.compact(512L << 50));
        assertEquals("1Qi", NumberFormatter.compact(1L << 60));
    }

    @Test public void fitsLongLabelsWithoutExceedingCubeWidth() {
        float scale = NumberFormatter.fitScale("512Qi", 1.2f, 60f, 80f);
        assertEquals(0.75f, scale, 0.0001f);
    }
}
