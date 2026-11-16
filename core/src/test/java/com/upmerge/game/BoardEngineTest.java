package com.upmerge.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class BoardEngineTest {
    private static void tick(BoardEngine engine, int frames) {
        for (int i = 0; i < frames; i++) engine.update(0.05f);
    }

    private static void settle(BoardEngine engine) {
        for (int i = 0; i < 100; i++) {
            engine.update(0.05f);
            boolean moving = false;
            for (BoardEngine.Cube cube : engine.cubes()) moving |= cube.moving;
            if (!moving) return;
        }
        fail("Board did not settle within 5 seconds");
    }

    private static void finishMerge(BoardEngine engine) {
        assertTrue(engine.isMergePending());
        tick(engine, 10); // 500 ms, beyond the 460 ms anticipation lock
        assertFalse(engine.isMergePending());
    }

    @Test public void boardHasFiveColumnsAndSevenRows() {
        assertEquals(5, BoardEngine.COLUMNS);
        assertEquals(7, BoardEngine.ROWS);
    }

    @Test public void mergeArithmeticUsesNextPowerOfTwo() {
        assertEquals(4, BoardEngine.mergeValue(2, 2));
        assertEquals(8, BoardEngine.mergeValue(2, 3));
        assertEquals(16, BoardEngine.mergeValue(2, 5));
        assertEquals(8, BoardEngine.mergeValue(4, 2));
        assertEquals(0, BoardEngine.mergeValue(3, 2));
        assertEquals(0, BoardEngine.mergeValue(2, 1));
        assertEquals(0, BoardEngine.mergeValue(1, 2));
    }

    @Test public void rejectsInvalidLaunches() {
        BoardEngine engine = new BoardEngine();
        assertFalse(engine.launch(-1, 2));
        assertFalse(engine.launch(5, 2));
        assertFalse(engine.launch(0, 0));
        assertFalse(engine.launch(0, 3));
    }

    @Test public void twoFaceAdjacentCubesMergeAfter460Milliseconds() {
        BoardEngine engine = new BoardEngine();
        assertTrue(engine.launch(2, 2));
        settle(engine);
        assertTrue(engine.launch(2, 2));
        settle(engine);
        assertTrue(engine.isMergePending());
        tick(engine, 8);
        assertTrue("Merge must not resolve before 460 ms", engine.isMergePending());
        tick(engine, 2);
        assertFalse(engine.isMergePending());
        assertEquals(1, engine.cubes().size());
        assertEquals(4, engine.cubes().get(0).value);
        assertEquals(4, engine.score());
        assertNotNull(engine.consumeMergeEvent());
    }

    @Test public void differentValuesDoNotMerge() {
        BoardEngine engine = new BoardEngine();
        assertTrue(engine.launch(0, 2));
        settle(engine);
        assertTrue(engine.launch(0, 4));
        settle(engine);
        assertFalse(engine.isMergePending());
        assertEquals(2, engine.cubes().size());
    }

    @Test public void downDirectionFillsFromBottomEntryEdge() {
        BoardEngine engine = new BoardEngine();
        engine.setDirection(BoardEngine.Direction.DOWN);
        assertTrue(engine.launch(1, 2));
        settle(engine);
        assertEquals(6, engine.cubes().get(0).row);
        assertTrue(engine.launch(1, 4));
        settle(engine);
        assertEquals(5, engine.cubes().stream().filter(c -> c.value == 4).findFirst().get().row);
    }

    @Test public void fullLaneRejectsWrongEdgeValueAndAcceptsMatchingForcedMerge() {
        BoardEngine engine = new BoardEngine();
        int[] values = {2, 4, 2, 4, 2, 4, 2};
        for (int value : values) {
            assertTrue(engine.launch(0, value));
            settle(engine);
        }
        // isFull() describes the whole board, not a single full lane.
        assertFalse(engine.isFull());
        assertFalse(engine.launch(0, 4));
        assertTrue(engine.launch(0, 2));
        settle(engine);
        assertTrue(engine.isMergePending());
        finishMerge(engine);
        assertEquals(7, engine.cubes().size());
        assertTrue(engine.cubes().stream().anyMatch(c -> c.value == 4));
    }

    @Test public void sequentialMergeChainProducesNextTierAndAccumulatesScore() {
        BoardEngine engine = new BoardEngine();

        assertTrue(engine.launch(0, 2));
        settle(engine);
        assertTrue(engine.launch(1, 2));
        settle(engine);
        assertTrue(engine.isMergePending());
        finishMerge(engine);

        assertEquals(1, engine.cubes().size());
        assertEquals(4, engine.cubes().get(0).value);
        assertEquals(4, engine.score());

        // A new 4 lands beside the previous result and starts the next merge.
        assertTrue(engine.launch(0, 4));
        settle(engine);
        assertTrue(engine.isMergePending());
        finishMerge(engine);

        assertEquals(1, engine.cubes().size());
        assertEquals(8, engine.cubes().get(0).value);
        assertEquals(20, engine.score());
    }

    @Test public void changingGravityChangesTheNextLaunchEdge() {
        BoardEngine engine = new BoardEngine();

        assertTrue(engine.launch(2, 2));
        settle(engine);
        assertEquals(0, engine.cubes().get(0).row);

        engine.setDirection(BoardEngine.Direction.DOWN);
        assertEquals(BoardEngine.Direction.DOWN, engine.direction());
        settle(engine); // Existing cubes must finish moving before another launch.
        assertEquals(6, engine.cubes().get(0).row);
        assertTrue(engine.launch(2, 4));
        settle(engine);
        assertEquals(5, engine.cubes().stream()
                .filter(c -> c.value == 4).findFirst().get().row);
    }

    @Test public void mergeEventIsConsumedOnlyOnce() {
        BoardEngine engine = new BoardEngine();
        assertTrue(engine.launch(0, 2));
        settle(engine);
        assertTrue(engine.launch(0, 2));
        settle(engine);
        finishMerge(engine);

        assertNotNull(engine.consumeMergeEvent());
        assertNull(engine.consumeMergeEvent());
    }

    @Test public void gravitySwitchRepositionsEveryExistingCubeAcrossTheBoard() {
        BoardEngine engine = new BoardEngine();
        assertTrue(engine.launch(0, 2));
        settle(engine);
        assertTrue(engine.launch(0, 4));
        settle(engine);

        assertEquals(0, engine.cubes().stream()
                .filter(c -> c.value == 2).findFirst().get().row);
        assertEquals(1, engine.cubes().stream()
                .filter(c -> c.value == 4).findFirst().get().row);

        engine.setDirection(BoardEngine.Direction.DOWN);
        assertEquals(BoardEngine.Direction.DOWN, engine.direction());
        assertTrue("All existing cubes must animate to the opposite edge",
                engine.cubes().stream().allMatch(c -> c.moving));

        settle(engine);
        // 4.11 preserves the original top-to-bottom order after reversing gravity.
        assertEquals(5, engine.cubes().stream()
                .filter(c -> c.value == 2).findFirst().get().row);
        assertEquals(6, engine.cubes().stream()
                .filter(c -> c.value == 4).findFirst().get().row);
    }

    @Test public void gravityChangeWaitsForCurrentCubeToLand() {
        BoardEngine engine = new BoardEngine();
        assertTrue(engine.launch(2, 2));
        engine.setDirection(BoardEngine.Direction.DOWN);

        // Direction is queued while a launched cube is still travelling.
        assertEquals(BoardEngine.Direction.UP, engine.direction());
        settle(engine);
        assertEquals(BoardEngine.Direction.DOWN, engine.direction());
        assertEquals(6, engine.cubes().get(0).row);
    }

    @Test public void automaticCascadeStartsAfterFirstMergeCompletes() {
        BoardEngine engine = new BoardEngine();

        // A 4 waits beside the cell where two 2s will merge into another 4.
        assertTrue(engine.launch(2, 4));
        settle(engine);
        assertTrue(engine.launch(0, 2));
        settle(engine);
        assertTrue(engine.launch(1, 2));
        settle(engine);

        assertTrue("First pair of 2s should begin merging", engine.isMergePending());
        finishMerge(engine);

        // The new 4 is now face-adjacent to the pre-existing 4.
        engine.update(0.05f);
        assertTrue("Second merge should be discovered after the first transaction",
                engine.isMergePending());
        finishMerge(engine);

        assertEquals(1, engine.cubes().size());
        assertEquals(8, engine.cubes().get(0).value);
        assertEquals(20, engine.score()); // second merge receives the x2 chain bonus
    }

    @Test public void mergeCompactionMovesRemainingCubesWithoutChangingTheirValues() {
        BoardEngine engine = new BoardEngine();

        assertTrue(engine.launch(0, 2));
        settle(engine);
        assertTrue(engine.launch(0, 4));
        settle(engine);
        assertTrue(engine.launch(0, 8));
        settle(engine);

        // Merge the bottom 2 with a 2 in the adjacent lane.
        assertTrue(engine.launch(1, 2));
        settle(engine);
        assertTrue(engine.isMergePending());
        finishMerge(engine);

        assertTrue(engine.cubes().stream().anyMatch(c -> c.value == 4));
        assertTrue(engine.cubes().stream().anyMatch(c -> c.value == 8));
        assertTrue(engine.cubes().stream().allMatch(c -> c.row >= 0 && c.row < BoardEngine.ROWS));
        settle(engine);
        for (int lane = 0; lane < BoardEngine.COLUMNS; lane++) {
            final int currentLane = lane;
            java.util.List<BoardEngine.Cube> stack = engine.cubes().stream()
                    .filter(c -> c.lane == currentLane)
                    .sorted(java.util.Comparator.comparingInt(c -> c.row))
                    .collect(java.util.stream.Collectors.toList());
            for (int i = 0; i < stack.size(); i++) {
                assertEquals("Packed lane must have no gaps", i, stack.get(i).row);
            }
        }
    }

    @Test public void mergeEventContainsEverySourcePositionAndTheAnchorResult() {
        BoardEngine engine = new BoardEngine();
        assertTrue(engine.launch(0, 2));
        settle(engine);
        assertTrue(engine.launch(1, 2));
        settle(engine);
        assertTrue(engine.isMergePending());
        finishMerge(engine);

        BoardEngine.MergeEvent event = engine.consumeMergeEvent();
        assertNotNull(event);
        assertEquals(4, event.value);
        assertEquals(2, event.sources.size());
        assertTrue(event.sources.stream().anyMatch(s -> s.lane == 0 && s.value == 2));
        assertTrue(event.sources.stream().anyMatch(s -> s.lane == 1 && s.value == 2));
        assertEquals(1, engine.cubes().size());
        assertEquals(event.lane, engine.cubes().get(0).lane);
        assertEquals(event.row, engine.cubes().get(0).row);
    }

    @Test public void mergesWithinOnePointSevenSecondsIncreaseComboMultiplier() {
        BoardEngine engine = new BoardEngine();

        assertTrue(engine.launch(0, 2));
        settle(engine);
        assertTrue(engine.launch(0, 2));
        settle(engine);
        finishMerge(engine);
        BoardEngine.MergeEvent first = engine.consumeMergeEvent();
        assertNotNull(first);
        assertEquals(1, first.combo);
        assertEquals(4, first.points);

        assertTrue(engine.launch(1, 2));
        settle(engine);
        assertTrue(engine.launch(1, 2));
        settle(engine);
        finishMerge(engine);
        BoardEngine.MergeEvent second = engine.consumeMergeEvent();
        assertNotNull(second);
        assertEquals(2, second.combo);
        assertEquals(8, second.points);
        assertEquals(12, engine.score());
    }

    @Test public void comboResetsAfterMoreThanOnePointSevenSecondsWithoutMerge() {
        BoardEngine engine = new BoardEngine();
        assertTrue(engine.launch(0, 2));
        settle(engine);
        assertTrue(engine.launch(0, 2));
        settle(engine);
        finishMerge(engine);
        assertEquals(1, engine.combo());

        tick(engine, 40); // 2 seconds without a merge
        assertTrue(engine.launch(1, 2));
        settle(engine);
        assertTrue(engine.launch(1, 2));
        settle(engine);
        finishMerge(engine);
        BoardEngine.MergeEvent event = engine.consumeMergeEvent();
        assertNotNull(event);
        assertEquals(1, event.combo);
    }

    @Test public void clearResetsBoardAndScore() {
        BoardEngine engine = new BoardEngine();
        assertTrue(engine.launch(0, 2));
        settle(engine);
        engine.clear();
        assertTrue(engine.cubes().isEmpty());
        assertEquals(0, engine.score());
        assertFalse(engine.isMergePending());
    }
}
