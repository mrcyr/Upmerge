package com.upmerge.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class BoardEngineToolsTest {
    private BoardEngine settledBoard() {
        BoardEngine engine = new BoardEngine();
        assertTrue(engine.loadState("UPM1|0|UP|1|1.7|5|3"
                + "|1,0,0,2|2,1,0,4|3,2,0,8"));
        return engine;
    }

    @Test public void moveToolMovesCubeAndKeepsLanesPacked() {
        BoardEngine engine = settledBoard();
        assertTrue(engine.moveCube(1L, 4, 0));
        assertEquals(0, engine.cubesInLane(0));
        assertEquals(1, engine.cubesInLane(4));
        assertFalse(engine.moveCube(1L, 1, 0));
    }

    @Test public void swapToolExchangesSelectedCubes() {
        BoardEngine engine = settledBoard();
        assertTrue(engine.swapCubes(1L, 2L));
        assertEquals(1, engine.cubesInLane(0));
        assertEquals(1, engine.cubesInLane(1));
        assertFalse(engine.swapCubes(1L, 1L));
    }

    @Test public void bombRemovesThreeByThreeNeighbourhood() {
        BoardEngine engine = settledBoard();
        assertEquals(3, engine.bombAt(2L));
        assertEquals(0, engine.cubesInLane(0));
        assertEquals(0, engine.cubesInLane(1));
        assertEquals(0, engine.cubesInLane(2));
        assertEquals(0, engine.bombAt(2L));
    }
}
