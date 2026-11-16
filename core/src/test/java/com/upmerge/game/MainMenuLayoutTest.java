package com.upmerge.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class MainMenuLayoutTest {
    @Test public void modeAndDirectionCardsAreCenteredAndDoNotOverlap() {
        for (float[] size : new float[][] {{320, 568}, {360, 800}, {430, 932}, {1080, 2400}}) {
            MainMenuLayout layout = new MainMenuLayout(size[0], size[1], false);
            assertEquals(size[0] * .5f,
                    (layout.modes[0].centerX() + layout.modes[2].centerX()) * .5f, .01f);
            assertEquals(layout.modes[1].centerX(), size[0] * .5f, .01f);
            for (MainMenuLayout.Rect mode : layout.modes)
                assertEquals(40f, mode.height, .01f);
            assertFalse(layout.modes[0].overlaps(layout.modes[1]));
            assertFalse(layout.modes[1].overlaps(layout.modes[2]));
            assertFalse(layout.directions[0].overlaps(layout.directions[1]));
            assertEquals(size[0], layout.directions[0].centerX()
                    + layout.directions[1].centerX(), .01f);
        }
    }

    @Test public void everyVisibleMainMenuButtonHasItsOwnTouchTarget() {
        MainMenuLayout layout = new MainMenuLayout(360, 800, false);
        assertEquals(MainMenuLayout.Target.MODE_CLASSIC,
                layout.hit(layout.modes[0].centerX(), layout.modes[0].centerY()));
        assertEquals(MainMenuLayout.Target.MODE_TIMED,
                layout.hit(layout.modes[1].centerX(), layout.modes[1].centerY()));
        assertEquals(MainMenuLayout.Target.MODE_CHALLENGE,
                layout.hit(layout.modes[2].centerX(), layout.modes[2].centerY()));
        assertEquals(MainMenuLayout.Target.DIRECTION_UP,
                layout.hit(layout.directions[0].centerX(), layout.directions[0].centerY()));
        assertEquals(MainMenuLayout.Target.DIRECTION_DOWN,
                layout.hit(layout.directions[1].centerX(), layout.directions[1].centerY()));
        assertEquals(MainMenuLayout.Target.NEW_GAME,
                layout.hit(layout.newGameButton.centerX(), layout.newGameButton.centerY()));
        assertEquals(MainMenuLayout.Target.THEMES,
                layout.hit(layout.menuActions[0].centerX(), layout.menuActions[0].centerY()));
        assertEquals(MainMenuLayout.Target.DAILY,
                layout.hit(layout.menuActions[1].centerX(), layout.menuActions[1].centerY()));
        assertEquals(MainMenuLayout.Target.SHOP,
                layout.hit(layout.utilities[0][0].centerX(), layout.utilities[0][0].centerY()));
        assertEquals(MainMenuLayout.Target.ACHIEVEMENTS,
                layout.hit(layout.utilities[0][1].centerX(), layout.utilities[0][1].centerY()));
        assertEquals(MainMenuLayout.Target.STATISTICS,
                layout.hit(layout.utilities[1][0].centerX(), layout.utilities[1][0].centerY()));
        assertEquals(MainMenuLayout.Target.TASKS,
                layout.hit(layout.utilities[1][1].centerX(), layout.utilities[1][1].centerY()));
    }

    @Test public void continueButtonExistsOnlyWhenThereIsASavedRun() {
        MainMenuLayout fresh = new MainMenuLayout(360, 800, false);
        MainMenuLayout saved = new MainMenuLayout(360, 800, true);
        assertNull(fresh.continueButton);
        assertNotNull(saved.continueButton);
        assertEquals(MainMenuLayout.Target.CONTINUE,
                saved.hit(saved.continueButton.centerX(), saved.continueButton.centerY()));
        assertEquals(MainMenuLayout.Target.NEW_GAME,
                saved.hit(saved.newGameButton.centerX(), saved.newGameButton.centerY()));
    }

    @Test public void menuButtonsStayInsideScreenAtCompactAndTallSizes() {
        for (float[] size : new float[][] {{320, 568}, {360, 640}, {393, 873}, {432, 960}}) {
            MainMenuLayout layout = new MainMenuLayout(size[0], size[1], true);
            for (MainMenuLayout.Rect rect : layout.modes) assertInside(rect, size[0], size[1]);
            for (MainMenuLayout.Rect rect : layout.directions) assertInside(rect, size[0], size[1]);
            assertInside(layout.continueButton, size[0], size[1]);
            assertInside(layout.newGameButton, size[0], size[1]);
            for (MainMenuLayout.Rect[] row : layout.utilities)
                for (MainMenuLayout.Rect rect : row) assertInside(rect, size[0], size[1]);
            for (MainMenuLayout.Rect rect : layout.menuActions) assertInside(rect, size[0], size[1]);
        }
    }

    @Test public void adjacentUtilityRowsAndPrimaryActionsDoNotOverlap() {
        MainMenuLayout layout = new MainMenuLayout(360, 800, true);
        assertFalse(layout.utilities[0][0].overlaps(layout.utilities[1][0]));
        assertFalse(layout.utilities[0][1].overlaps(layout.utilities[1][1]));
        assertFalse(layout.newGameButton.overlaps(layout.menuActions[0]));
        assertFalse(layout.newGameButton.overlaps(layout.menuActions[1]));
        assertFalse(layout.newGameButton.overlaps(layout.utilities[0][0]));
        assertFalse(layout.newGameButton.overlaps(layout.utilities[0][1]));
        assertFalse(layout.menuActions[0].overlaps(layout.menuActions[1]));
    }

    private static void assertInside(MainMenuLayout.Rect rect, float width, float height) {
        assertTrue("left edge", rect.x >= 0f);
        assertTrue("bottom edge", rect.y >= 0f);
        assertTrue("right edge", rect.x + rect.width <= width);
        assertTrue("top edge", rect.y + rect.height <= height);
    }
}
