package com.upmerge.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic 5x7 lane engine. Cells are integer grid positions; there is no
 * free-body physics or collision solver. The rules mirror the direct-board
 * implementation in the 4.11 reference.
 */
public final class BoardEngine {
    public static final int COLUMNS = 5;
    public static final int ROWS = 7;
    public static final float MERGE_DELAY_SECONDS = 0.460f;
    private static final float FALL_CELLS_PER_SECOND = 19.6f;

    public enum Direction { UP, DOWN }

    public static final class Cube {
        public final long id;
        public final int value;
        public int lane;
        public int row;
        public float visualRow;
        public boolean moving;
        public boolean mergePending;
        private Cube forcedMergeTarget;

        private Cube(long id, int lane, int row, int value, float visualRow) {
            this.id = id;
            this.lane = lane;
            this.row = row;
            this.value = value;
            this.visualRow = visualRow;
            this.moving = true;
        }
    }

    public static final class MergeSource {
        public final int lane;
        public final int row;
        public final int value;

        private MergeSource(Cube cube) {
            lane = cube.lane;
            row = cube.row;
            value = cube.value;
        }
    }

    public static final class MergeEvent {
        public final int value;
        public final int count;
        public final int lane;
        public final int row;
        public final List<MergeSource> sources;

        private MergeEvent(int value, int count, int lane, int row, List<MergeSource> sources) {
            this.value = value;
            this.count = count;
            this.lane = lane;
            this.row = row;
            this.sources = Collections.unmodifiableList(sources);
        }
    }

    private final ArrayList<Cube> cubes = new ArrayList<>();
    private long nextId = 1;
    private Direction direction = Direction.UP;
    private Direction requestedDirection;
    private float pendingTime;
    private List<Cube> pendingGroup = Collections.emptyList();
    private Cube pendingAnchor;
    private MergeEvent lastMerge;
    private int score;

    public List<Cube> cubes() { return Collections.unmodifiableList(cubes); }
    public Direction direction() { return direction; }
    public void setDirection(Direction value) {
        requestedDirection = Objects.requireNonNull(value);
        applyRequestedDirectionIfReady();
    }

    private boolean hasMovingCubes() {
        for (Cube cube : cubes) if (cube.moving) return true;
        return false;
    }

    private void applyRequestedDirectionIfReady() {
        if (requestedDirection == null || isMergePending() || hasMovingCubes()) return;
        Direction next = requestedDirection;
        requestedDirection = null;
        if (next == direction) return;
        direction = next;
        // Repack every lane toward the newly selected gravity edge. The logical
        // target rows change now; visualRow animates every cube across the board.
        compactAllLanes();
    }
    public int score() { return score; }
    public boolean isMergePending() { return !pendingGroup.isEmpty(); }
    public float mergeElapsedSeconds() { return pendingTime; }

    public MergeEvent consumeMergeEvent() {
        MergeEvent event = lastMerge;
        lastMerge = null;
        return event;
    }

    public void clear() {
        cubes.clear();
        pendingGroup = Collections.emptyList();
        pendingAnchor = null;
        pendingTime = 0;
        requestedDirection = null;
        direction = Direction.UP;
        score = 0;
        lastMerge = null;
        nextId = 1;
    }

    /**
     * Launches into the first free cell from the active gravity edge.
     * A completely full lane accepts only a cube matching the entry-edge cube;
     * it falls into that occupied cell and merges after the normal anticipation.
     */
    public boolean launch(int lane, int value) {
        if (lane < 0 || lane >= COLUMNS || !isPowerOfTwo(value) || isMergePending()) return false;
        List<Cube> stack = lane(lane);
        for (Cube cube : stack) if (cube.moving) return false;

        int targetRow = firstEmptyRow(stack);
        Cube forcedTarget = null;
        if (targetRow < 0) {
            forcedTarget = entryEdge(stack);
            if (forcedTarget == null || forcedTarget.value != value) return false;
            targetRow = forcedTarget.row;
        }

        float spawnRow = direction == Direction.UP ? ROWS + 0.62f : -0.62f;
        Cube incoming = new Cube(nextId++, lane, targetRow, value, spawnRow);
        incoming.forcedMergeTarget = forcedTarget;
        cubes.add(incoming);
        return true;
    }

    private int firstEmptyRow(List<Cube> stack) {
        boolean[] occupied = new boolean[ROWS];
        for (Cube cube : stack) if (cube.row >= 0 && cube.row < ROWS) occupied[cube.row] = true;
        if (direction == Direction.UP) {
            for (int row = 0; row < ROWS; row++) if (!occupied[row]) return row;
        } else {
            for (int row = ROWS - 1; row >= 0; row--) if (!occupied[row]) return row;
        }
        return -1;
    }

    private Cube entryEdge(List<Cube> stack) {
        int edgeRow = direction == Direction.UP ? ROWS - 1 : 0;
        for (Cube cube : stack) if (cube.row == edgeRow) return cube;
        return null;
    }

    private List<Cube> lane(int lane) {
        ArrayList<Cube> result = new ArrayList<>();
        for (Cube cube : cubes) if (cube.lane == lane) result.add(cube);
        return result;
    }

    public void update(float deltaSeconds) {
        if (deltaSeconds <= 0) return;
        applyRequestedDirectionIfReady();
        float dt = Math.min(deltaSeconds, 0.05f);

        for (Cube cube : new ArrayList<>(cubes)) {
            if (!cube.moving) continue;
            float distance = cube.row - cube.visualRow;
            float step = FALL_CELLS_PER_SECOND * dt;
            if (Math.abs(distance) <= step) {
                cube.visualRow = cube.row;
                cube.moving = false;
                if (cube.forcedMergeTarget != null && cubes.contains(cube.forcedMergeTarget)) {
                    List<Cube> group = connectedGroup(cube.forcedMergeTarget);
                    if (!group.contains(cube)) group.add(0, cube);
                    beginMerge(group, cube);
                    return;
                }
                cube.forcedMergeTarget = null;
            } else {
                cube.visualRow += Math.signum(distance) * step;
            }
        }

        if (isMergePending()) {
            pendingTime += dt;
            if (pendingTime + 0.000001f >= MERGE_DELAY_SECONDS) finishMerge();
            return;
        }

        // Never resolve a contact or reverse gravity while any cube is still moving.
        for (Cube cube : cubes) if (cube.moving) return;
        applyRequestedDirectionIfReady();
        if (hasMovingCubes()) return;

        // The most recently landed cube is the merge anchor, as in 4.11.
        ArrayList<Cube> candidates = new ArrayList<>(cubes);
        candidates.sort((a, b) -> Long.compare(b.id, a.id));
        for (Cube candidate : candidates) {
            List<Cube> group = connectedGroup(candidate);
            if (group.size() > 1) {
                beginMerge(group, candidate);
                return;
            }
        }
    }

    private List<Cube> connectedGroup(Cube anchor) {
        ArrayList<Cube> group = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        group.add(anchor);
        seen.add(anchor.id);
        for (int cursor = 0; cursor < group.size(); cursor++) {
            Cube current = group.get(cursor);
            for (Cube other : cubes) {
                if (seen.contains(other.id) || other.moving || other.value != anchor.value) continue;
                if (Math.abs(current.lane - other.lane) + Math.abs(current.row - other.row) == 1) {
                    seen.add(other.id);
                    group.add(other);
                }
            }
        }
        return group;
    }

    private void beginMerge(List<Cube> group, Cube anchor) {
        if (group.size() < 2 || isMergePending()) return;
        pendingGroup = Collections.unmodifiableList(new ArrayList<>(group));
        pendingAnchor = anchor;
        pendingTime = 0;
        for (Cube cube : pendingGroup) cube.mergePending = true;
    }

    private void finishMerge() {
        int count = pendingGroup.size();
        int baseValue = pendingGroup.get(0).value;
        int result = mergeValue(baseValue, count);
        if (result > 0) {
            int lane = pendingAnchor.lane;
            int row = pendingAnchor.row;
            ArrayList<MergeSource> sources = new ArrayList<>();
            for (Cube cube : pendingGroup) {
                sources.add(new MergeSource(cube));
                cubes.remove(cube);
            }
            Cube made = new Cube(nextId++, lane, row, result, row);
            made.moving = false;
            cubes.add(made);
            score += result;
            lastMerge = new MergeEvent(result, count, lane, row, sources);
        }
        pendingGroup = Collections.emptyList();
        pendingAnchor = null;
        pendingTime = 0;
        compactAllLanes();
    }

    /** Returns the smallest power of two greater than or equal to value * count. */
    public static int mergeValue(int value, int count) {
        if (!isPowerOfTwo(value) || count < 2) return 0;
        long total = (long) value * count;
        if (total > Integer.MAX_VALUE) return 0;
        long result = 1;
        while (result < total) result <<= 1;
        return result > Integer.MAX_VALUE ? 0 : (int) result;
    }

    private static boolean isPowerOfTwo(int value) {
        return value >= 2 && (value & (value - 1)) == 0;
    }

    private void compactAllLanes() {
        for (int lane = 0; lane < COLUMNS; lane++) compactLane(lane);
    }

    private void compactLane(int lane) {
        List<Cube> stack = lane(lane);
        // Preserve the same top-to-bottom visual order when gravity flips.
        // For DOWN, pack the reversed list from the bottom edge, as in 4.11.
        stack.sort(Comparator.comparingInt(cube -> cube.row));
        if (direction == Direction.DOWN) Collections.reverse(stack);
        for (int index = 0; index < stack.size(); index++) {
            Cube cube = stack.get(index);
            int targetRow = direction == Direction.UP ? index : ROWS - 1 - index;
            if (cube.row != targetRow) {
                cube.row = targetRow;
                cube.moving = true;
            }
        }
    }

    public boolean isFull() {
        for (int lane = 0; lane < COLUMNS; lane++) {
            if (firstEmptyRow(lane(lane)) >= 0) return false;
        }
        return true;
    }
}
