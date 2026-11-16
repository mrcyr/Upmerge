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
    private int goalTarget = BASE_GOAL;
    private int current;
    private int upcoming;

    public CubeQueue() {
        this(new Random());
    }

    CubeQueue(Random random) {
        if (random == null) throw new IllegalArgumentException("random is required");
        this.random = random;
        reset(BASE_GOAL);
    }

    public int current() { return current; }
    public int upcoming() { return upcoming; }
    public int goalTarget() { return goalTarget; }

    public boolean restore(int target, int currentValue, int upcomingValue) {
        if (!isAllowed(target, currentValue) || !isAllowed(target, upcomingValue)) return false;
        goalTarget = target;
        current = currentValue;
        upcoming = upcomingValue;
        return true;
    }

    private static boolean isAllowed(int target, int value) {
        for (int candidate : valuesForGoal(target)) if (candidate == value) return true;
        return false;
    }

    public void reset(int target) {
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
    public boolean onMergedValue(int value) {
        if (value < goalTarget) return false;
        goalTarget = Math.multiplyExact(goalTarget, 2);
        current = roll();
        upcoming = roll();
        return true;
    }

    public static int[] valuesForGoal(int target) {
        int step = levelStep(target);
        int min = 2 * (1 << step);
        int max = min * 32;
        int count = 0;
        for (int v = min; v <= max; v *= 2) count++;
        int[] values = new int[count];
        int index = 0;
        for (int v = min; v <= max; v *= 2) values[index++] = v;
        return values;
    }

    public static int weightForIndex(int index) {
        return Math.max(1, 5 - index);
    }

    private static int levelStep(int target) {
        int safeTarget = Math.max(BASE_GOAL, target);
        return Math.max(0, Math.round((float) (Math.log((double) safeTarget / BASE_GOAL) / Math.log(2.0))));
    }

    private int roll() {
        int[] values = valuesForGoal(goalTarget);
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
