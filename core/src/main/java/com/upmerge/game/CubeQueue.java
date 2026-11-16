package com.upmerge.game;

import java.util.Random;

/**
 * 4.11 launch queue: a weighted random draw from the current level's six
 * consecutive powers of two. The launch value and the preview are both
 * generated up front; after a successful launch the preview becomes current.
 */
public final class CubeQueue {
    private static final int BASE_GOAL = 2048;
    private final Random random;
    private long goalTarget = BASE_GOAL;
    private long current;
    private long upcoming;

    public CubeQueue() {
        this(new Random());
    }

    CubeQueue(Random random) {
        if (random == null) throw new IllegalArgumentException("random is required");
        this.random = random;
        reset(BASE_GOAL);
    }

    public long current() { return current; }
    public long upcoming() { return upcoming; }
    public long goalTarget() { return goalTarget; }

    public boolean restore(long target, long currentValue, long upcomingValue) {
        if (!isAllowed(target, currentValue) || !isAllowed(target, upcomingValue)) return false;
        goalTarget = target;
        current = currentValue;
        upcoming = upcomingValue;
        return true;
    }

    private static boolean isAllowed(long target, long value) {
        for (long candidate : valuesForGoal(target)) if (candidate == value) return true;
        return false;
    }

    public void reset(long target) {
        goalTarget = Math.max(BASE_GOAL, target);
        current = roll();
        upcoming = roll();
    }

    /** Called only after the current cube was successfully launched. */
    public void advance() {
        current = upcoming;
        upcoming = roll();
    }

    /** Mirrors 4.11: reaching the goal doubles it and rerolls both queue slots. */
    public boolean onMergedValue(long value) {
        if (value < goalTarget) return false;
        if (goalTarget > Long.MAX_VALUE / 2L) return false;
        goalTarget *= 2L;
        current = roll();
        upcoming = roll();
        return true;
    }

    public static long[] valuesForGoal(long target) {
        int step = levelStep(target);
        long min = 2L << step;
        long max = min * 32L;
        int count = 0;
        for (long v = min; v <= max; v *= 2L) count++;
        long[] values = new long[count];
        int index = 0;
        for (long v = min; v <= max; v *= 2L) values[index++] = v;
        return values;
    }

    public static int weightForIndex(int index) {
        return Math.max(1, 5 - index);
    }

    private static int levelStep(long target) {
        long safeTarget = Math.max((long) BASE_GOAL, target);
        return Math.min(56, Math.max(0, (int) Math.round(Math.log((double) safeTarget / BASE_GOAL) / Math.log(2.0))));
    }

    private int roll() {
        long[] values = valuesForGoal(goalTarget);
        int totalWeight = 0;
        for (int i = 0; i < values.length; i++) totalWeight += weightForIndex(i);
        int roll = random.nextInt(totalWeight);
        for (int i = 0; i < values.length; i++) {
            roll -= weightForIndex(i);
            if (roll < 0) return values[i];
        }
        return values[values.length - 1];
    }
}
