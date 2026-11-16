package com.upmerge.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameScreenLayoutTest {
    @Test public void fiveBySevenBoardFitsBetweenHeaderAndFooter() {
        for (float[] device : new float[][] {{320,568,1}, {349,749,1},
                {384,828,1}, {432,960,1}, {1080,2400,3}}) {
            GameScreenLayout layout = new GameScreenLayout(device[0], device[1], device[2]);
            assertEquals(layout.cell * BoardEngine.COLUMNS,
                    layout.boardRight - layout.boardLeft, .02f);
            assertEquals(layout.cell * BoardEngine.ROWS,
                    layout.boardTop - layout.boardBottom, .02f);
            assertTrue(layout.boardLeft >= 0f);
            assertTrue(layout.boardRight <= layout.screenW);
            assertTrue(layout.boardBottom >= layout.footerHeight);
            assertTrue(layout.boardTop <= layout.screenH - layout.headerHeight);
        }
    }

    @Test public void toolbarAndBoardHitZonesDoNotOverlap() {
        for (float[] device : new float[][] {{320,568,1}, {384,828,1}, {1080,2400,3}}) {
            GameScreenLayout layout = new GameScreenLayout(device[0], device[1], device[2]);
            assertTrue(layout.toolbarHitHeight < layout.boardBottom);
            assertEquals(-1, layout.toolbarIndex(layout.screenW * .5f, layout.toolbarHitBottom - 1f));
            assertEquals(0, layout.toolbarIndex(layout.screenW * .1f,
                    (layout.toolbarHitBottom + layout.toolbarHitHeight) * .5f));
            assertEquals(4, layout.toolbarIndex(layout.screenW * .9f, layout.toolbarHitHeight * .5f));
            assertEquals(-1, layout.toolbarIndex(layout.screenW * .5f, layout.toolbarHitHeight + 1f));
        }
    }

    @Test public void pauseHitTargetIsOnlyTheTopRightHeaderCorner() {
        GameScreenLayout layout = new GameScreenLayout(384, 828, 1);
        assertTrue(layout.pauseContains(380, 810));
        assertFalse(layout.pauseContains(20, 810));
        assertFalse(layout.pauseContains(380, 700));
    }

    @Test public void boardTapCoordinatesMapToValidColumns() {
        GameScreenLayout layout = new GameScreenLayout(384, 828, 1);
        for (int column = 0; column < BoardEngine.COLUMNS; column++) {
            float x = layout.boardLeft + (column + .5f) * layout.cell;
            assertTrue(layout.boardContains(x, (layout.boardBottom + layout.boardTop) * .5f));
        }
        assertFalse(layout.boardContains(layout.boardLeft - 1f,
                (layout.boardBottom + layout.boardTop) * .5f));
    }
}
