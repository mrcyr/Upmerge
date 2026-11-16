package com.upmerge.game;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class CubeQueueTest {
    @Test public void baseLevelUsesSixConsecutivePowersWithOriginalWeights() {
        assertArrayEquals(new long[]{2, 4, 8, 16, 32, 64}, CubeQueue.valuesForGoal(2048));
        assertEquals(5, CubeQueue.weightForIndex(0));
        assertEquals(4, CubeQueue.weightForIndex(1));
        assertEquals(3, CubeQueue.weightForIndex(2));
        assertEquals(2, CubeQueue.weightForIndex(3));
        assertEquals(1, CubeQueue.weightForIndex(4));
        assertEquals(1, CubeQueue.weightForIndex(5));
    }

    @Test public void nextLevelShiftsTheRangeUpByOnePower() {
        assertArrayEquals(new long[]{4, 8, 16, 32, 64, 128}, CubeQueue.valuesForGoal(4096));
        assertArrayEquals(new long[]{8, 16, 32, 64, 128, 256}, CubeQueue.valuesForGoal(8192));
    }

    @Test public void queueAdvancesOnlyWhenSuccessfulLaunchConsumesCurrent() {
        CubeQueue queue = new CubeQueue(new Random(1234));
        long current = queue.current();
        long upcoming = queue.upcoming();
        queue.advance();
        assertEquals(upcoming, queue.current());
        assertTrue(isAllowed(queue.upcoming(), 2048));
        assertTrue(isAllowed(current, 2048));
    }

    @Test public void reachingGoalDoublesTargetAndRerollsQueueForNewLevel() {
        CubeQueue queue = new CubeQueue(new Random(7));
        assertFalse(queue.onMergedValue(1024));
        assertEquals(2048, queue.goalTarget());
        assertTrue(queue.onMergedValue(2048));
        assertEquals(4096, queue.goalTarget());
        assertTrue(isAllowed(queue.current(), 4096));
        assertTrue(isAllowed(queue.upcoming(), 4096));
    }

    @Test public void progressionMatchesSuccessiveOriginalGoalTiers() {
        CubeQueue queue = new CubeQueue(new Random(44));
        long[] goals = {2048L, 4096L, 8192L, 16384L, 32768L, 65536L};
        for (int i = 1; i < goals.length; i++) {
            assertTrue(queue.onMergedValue(goals[i - 1]));
            assertEquals(goals[i], queue.goalTarget());
            assertTrue(isAllowed(queue.current(), goals[i]));
            assertTrue(isAllowed(queue.upcoming(), goals[i]));
        }
    }

    @Test public void resetPreservesRequestedPersistentGoalTier() {
        CubeQueue queue = new CubeQueue(new Random(12));
        assertTrue(queue.onMergedValue(2048));
        queue.reset(queue.goalTarget());
        assertEquals(4096L, queue.goalTarget());
        assertTrue(isAllowed(queue.current(), 4096));
        assertTrue(isAllowed(queue.upcoming(), 4096));
    }

    @Test public void randomDrawsNeverLeaveCurrentLevelRange() {
        CubeQueue queue = new CubeQueue(new Random(99));
        for (int i = 0; i < 500; i++) {
            assertTrue(isAllowed(queue.current(), queue.goalTarget()));
            assertTrue(isAllowed(queue.upcoming(), queue.goalTarget()));
            queue.advance();
        }
    }

    @Test public void restorePreservesBothVisibleQueueSlotsAndRejectsInvalidValues() {
        CubeQueue queue = new CubeQueue(new Random(123));
        assertTrue(queue.restore(4096, 8, 64));
        assertEquals(4096, queue.goalTarget());
        assertEquals(8, queue.current());
        assertEquals(64, queue.upcoming());
        assertFalse(queue.restore(4096, 2, 64));
        assertFalse(queue.restore(4096, 8, 256));
    }


    @Test public void restoreRejectsNonPowerOfTwoAndOutOfRangeGoalTiers() {
        CubeQueue queue = new CubeQueue(new Random(321));
        long originalGoal = queue.goalTarget();
        long originalCurrent = queue.current();
        long originalUpcoming = queue.upcoming();

        assertFalse(queue.restore(3000L, 4L, 32L));
        assertFalse(queue.restore(1024L, 2L, 16L));
        assertFalse(queue.restore(Long.MAX_VALUE, 2L, 16L));
        assertFalse(queue.restore(1L << 63, 2L, 16L));

        assertEquals(originalGoal, queue.goalTarget());
        assertEquals(originalCurrent, queue.current());
        assertEquals(originalUpcoming, queue.upcoming());
    }

    @Test public void resetFallsBackToBaseTierForInvalidGoal() {
        CubeQueue queue = new CubeQueue(new Random(654));
        queue.reset(3000L);
        assertEquals(2048L, queue.goalTarget());
        assertTrue(isAllowed(queue.current(), 2048L));
        assertTrue(isAllowed(queue.upcoming(), 2048L));
    }

    @Test public void highestSupportedGoalTierKeepsSixValidQueueValues() {
        long highestGoal = 1L << 62;
        assertArrayEquals(new long[]{1L << 52, 1L << 53, 1L << 54,
                1L << 55, 1L << 56, 1L << 57}, CubeQueue.valuesForGoal(highestGoal));

        CubeQueue queue = new CubeQueue(new Random(987));
        assertTrue(queue.restore(highestGoal, 1L << 52, 1L << 57));
        assertEquals(highestGoal, queue.goalTarget());
        assertFalse(queue.onMergedValue(highestGoal));
        assertEquals(highestGoal, queue.goalTarget());
    }

    private static boolean isAllowed(long value, long goal) {
        for (long candidate : CubeQueue.valuesForGoal(goal)) if (candidate == value) return true;
        return false;
    }
}
