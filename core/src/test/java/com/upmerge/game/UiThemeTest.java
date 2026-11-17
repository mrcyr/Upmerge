package com.upmerge.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class UiThemeTest {
    @Test public void everyReferenceThemeHasDistinctIdentityAndValidPalette() {
        assertEquals(20, UiTheme.NAMES.length);
        for (int i = 0; i < UiTheme.NAMES.length; i++) {
            UiTheme theme = UiTheme.fromIndex(i);
            assertEquals(i, theme.index);
            assertEquals(UiTheme.NAMES[i], theme.name);
            assertNotEquals(0, theme.surface);
            assertNotEquals(0, theme.panel);
            assertNotEquals(0, theme.background);
            assertNotEquals(0, theme.accent);
            assertEquals(10, theme.cubes.length);
        }
    }

    @Test public void ultravioletPaletteMatchesHtmlReference() {
        UiTheme theme = UiTheme.fromIndex(16);
        assertEquals(0x39205c, theme.surface);
        assertEquals(0x24133f, theme.panel);
        assertEquals(0x100d25, theme.background);
        assertEquals(0xf43fff, theme.cubeColor(2));
        assertEquals(0xb84cff, theme.cubeColor(4));
        assertEquals(0x785cff, theme.cubeColor(8));
    }

    @Test public void themesWrapAndHigherCubeValuesRemainDeterministic() {
        assertEquals(0, UiTheme.fromIndex(20).index);
        assertEquals(19, UiTheme.fromIndex(-1).index);
        UiTheme theme = UiTheme.fromIndex(16);
        assertEquals(theme.cubeColor(2048), theme.cubeColor(2048));
        assertTrue(theme.cubeColor(2048) >= 0 && theme.cubeColor(2048) <= 0xffffff);
        assertEquals(0xf5f5ff, theme.cubeColor(3));
    }

    @Test public void colorMixInterpolatesRgbChannels() {
        assertEquals(0x808080, UiTheme.mix(0x000000, 0xffffff, .5f));
        assertEquals(0x123456, UiTheme.mix(0x123456, 0xabcdef, 0f));
        assertEquals(0xabcdef, UiTheme.mix(0x123456, 0xabcdef, 1f));
    }
}
