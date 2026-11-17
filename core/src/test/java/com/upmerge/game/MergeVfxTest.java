package com.upmerge.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class MergeVfxTest {
    @Test public void mergeStartsWithAFullParticleBurstAndShockwaveState() {
        MergeVfx effect = new MergeVfx();
        effect.start(120f, 240f, 48f, 8L, 3);
        assertTrue(effect.isActive());
        assertEquals(72, effect.particleCount());
        assertEquals(120f, effect.centerX(), .001f);
        assertEquals(240f, effect.centerY(), .001f);
        assertEquals(0f, effect.progress(), .001f);
        assertTrue(effect.particleAlpha(0) > 0f);
    }

    @Test public void impactRunsThenExpiresWithoutChangingBoardValues() {
        MergeVfx effect = new MergeVfx();
        effect.start(20f, 30f, 40f, 16L, 4);
        effect.update(.25f);
        assertTrue(effect.isActive());
        assertTrue(effect.progress() > 0f);
        effect.update(.50f);
        assertTrue(effect.isActive());
        effect.update(.20f);
        assertFalse(effect.isActive());
        assertEquals(1f, effect.progress(), .001f);
    }


    @Test public void particlesUseTheSelectedThemeCubeColor() {
        MergeVfx effect = new MergeVfx();
        effect.start(0f, 0f, 39f, 8L, 2, 0x785cff);
        assertEquals(0x78 / 255f, effect.red(), .001f);
        assertEquals(0x5c / 255f, effect.green(), .001f);
        assertEquals(0xff / 255f, effect.blue(), .001f);
        assertEquals(62, effect.particleCount());
    }

    @Test public void particleBurstIsDeterministicForSameMerge() {
        MergeVfx a = new MergeVfx(), b = new MergeVfx();
        a.start(0f, 0f, 50f, 32L, 3);
        b.start(0f, 0f, 50f, 32L, 3);
        for (int i = 0; i < a.particleCount(); i++) {
            assertEquals(a.particleX(i), b.particleX(i), .0001f);
            assertEquals(a.particleY(i), b.particleY(i), .0001f);
        }
    }
}
