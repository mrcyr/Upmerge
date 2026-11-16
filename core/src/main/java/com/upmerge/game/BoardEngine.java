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
        public final long value;
        public int lane;
        public int row;
        public float visualRow;
        public boolean moving;
        public boolean mergePending;
        /** True only for an incoming cube that hits a full lane's entry-edge cube. */
        public boolean forcedMergeIncoming;
        private Cube forcedMergeTarget;

        private Cube(long id, int lane, int row, long value, float visualRow) {
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
        public final long value;

        private MergeSource(Cube cube) {
            lane = cube.lane;
            row = cube.row;
            value = cube.value;
        }
    }

    public static final class MergeEvent {
        public final long value;
        public final int count;
        public final int lane;
        public final int row;
        public final int combo;
        public final long points;
        public final List<MergeSource> sources;

        private MergeEvent(long value, int count, int lane, int row, int combo, long points, List<MergeSource> sources) {
            this.value = value;
            this.count = count;
            this.lane = lane;
            this.row = row;
            this.combo = combo;
            this.points = points;
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
    private long score;
    private int combo = 1;
    private float secondsSinceLastMerge = Float.MAX_VALUE;
    private int pendingLandingHaptics;
    private int pendingMovementHaptics;

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
    public long score() { return score; }
    public int combo() { return combo; }
    public boolean isMergePending() { return !pendingGroup.isEmpty(); }
    public float mergeElapsedSeconds() { return pendingTime; }

    /** Number of cubes that reached their target cell since the previous poll. */
    public int consumeLandingHaptics() {
        int count = pendingLandingHaptics;
        pendingLandingHaptics = 0;
        return count;
    }

    /** Number of cubes that started a gravity/compaction movement since the previous poll. */
    public int consumeMovementHaptics() {
        int count = pendingMovementHaptics;
        pendingMovementHaptics = 0;
        return count;
    }

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
        combo = 1;
        secondsSinceLastMerge = Float.MAX_VALUE;
        lastMerge = null;
        pendingLandingHaptics = 0;
        pendingMovementHaptics = 0;
        nextId = 1;
    }

    /**
     * Launches into the first free cell from the active gravity edge.
     * A completely full lane accepts only a cube matching the entry-edge cube;
     * it falls into that occupied cell and merges after the normal anticipation.
     */
    public boolean launch(int lane, long value) {
        if (lane < 0 || lane >= COLUMNS || !isPowerOfTwo(value)
                || isMergePending() || nextId == Long.MAX_VALUE) return false;
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
        incoming.forcedMergeIncoming = forcedTarget != null;
        cubes.add(incoming);
        pendingMovementHaptics++;
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
        secondsSinceLastMerge = Math.min(Float.MAX_VALUE, secondsSinceLastMerge + dt);

        for (Cube cube : new ArrayList<>(cubes)) {
            if (!cube.moving) continue;
            float distance = cube.row - cube.visualRow;
            float step = FALL_CELLS_PER_SECOND * dt;
            if (Math.abs(distance) <= step) {
                cube.visualRow = cube.row;
                cube.moving = false;
                pendingLandingHaptics++;
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
        long baseValue = pendingGroup.get(0).value;
        long result = mergeValue(baseValue, count);
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
            // 4.11 chains merges made within 1.7 seconds, up to x9.
            combo = secondsSinceLastMerge < 1.7f ? Math.min(9, combo + 1) : 1;
            secondsSinceLastMerge = 0f;
            long points = result > Long.MAX_VALUE / combo ? Long.MAX_VALUE : result * combo;
            score = score > Long.MAX_VALUE - points ? Long.MAX_VALUE : score + points;
            lastMerge = new MergeEvent(result, count, lane, row, combo, points, sources);
        }
        pendingGroup = Collections.emptyList();
        pendingAnchor = null;
        pendingTime = 0;
        compactAllLanes();
    }

    /** Returns the smallest power of two greater than or equal to value * count. */
    public static long mergeValue(long value, int count) {
        if (!isPowerOfTwo(value) || count < 2) return 0;
        if (value > Long.MAX_VALUE / count) return 0L;
        long total = value * count;
        if (total > (1L << 62)) return 0L;
        long result = 1;
        while (result < total) result <<= 1;
        return result > (1L << 62) ? 0L : result;
    }

    private static boolean isPowerOfTwo(long value) {
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
                pendingMovementHaptics++;
            }
        }
    }

    /** Versioned stable snapshot. Saves only settled boards; transient animations are replayed safely. */
    public String saveState() {
        if (isMergePending() || hasMovingCubes()) return null;
        StringBuilder out = new StringBuilder("UPM1|");
        out.append(score).append('|').append(direction.name()).append('|')
                .append(combo).append('|').append(Math.min(1.7f, secondsSinceLastMerge)).append('|')
                .append(nextId).append('|').append(cubes.size());
        for (Cube cube : cubes) {
            out.append('|').append(cube.id).append(',')
                    .append(cube.lane).append(',').append(cube.row).append(',')
                    .append(cube.value);
        }
        return out.toString();
    }

    public boolean loadState(String state) {
        if (state == null) return false;
        try {
            String[] parts = state.split("\\|");
            if (parts.length < 7 || !"UPM1".equals(parts[0])) return false;
            long restoredScore = Long.parseLong(parts[1]);
            Direction restoredDirection = Direction.valueOf(parts[2]);
            int restoredCombo = Integer.parseInt(parts[3]);
            float restoredComboAge = Float.parseFloat(parts[4]);
            long restoredNextId = Long.parseLong(parts[5]);
            int count = Integer.parseInt(parts[6]);
            if (restoredScore < 0 || restoredCombo < 1 || restoredCombo > 9
                    || count < 0 || count > COLUMNS * ROWS || parts.length != 7 + count
                    || restoredNextId < 1 || !Float.isFinite(restoredComboAge)
                    || restoredComboAge < 0 || restoredComboAge > 1.7f) return false;

            ArrayList<Cube> restored = new ArrayList<>();
            Set<String> occupied = new HashSet<>();
            Set<Long> restoredIds = new HashSet<>();
            int[] laneCounts = new int[COLUMNS];
            long maxId = 0;
            for (int i = 0; i < count; i++) {
                String[] fields = parts[7 + i].split(",");
                if (fields.length != 4) return false;
                long id = Long.parseLong(fields[0]);
                int lane = Integer.parseInt(fields[1]);
                int row = Integer.parseInt(fields[2]);
                long value = Long.parseLong(fields[3]);
                String cell = lane + ":" + row;
                if (id < 1 || !restoredIds.add(id)
                        || lane < 0 || lane >= COLUMNS || row < 0 || row >= ROWS
                        || !isPowerOfTwo(value) || !occupied.add(cell)) return false;
                Cube cube = new Cube(id, lane, row, value, row);
                cube.moving = false;
                restored.add(cube);
                laneCounts[lane]++;
                maxId = Math.max(maxId, id);
            }
            if (restoredNextId <= maxId) return false;

            // Snapshots are written only for settled boards: every lane must be
            // packed tightly against its active gravity edge, with no hidden holes.
            for (Cube cube : restored) {
                int countInLane = laneCounts[cube.lane];
                int expectedRow = restoredDirection == Direction.UP
                        ? countInLane - 1 : ROWS - countInLane;
                if (restoredDirection == Direction.UP) {
                    int below = 0;
                    for (Cube other : restored) {
                        if (other.lane == cube.lane && other.row < cube.row) below++;
                    }
                    expectedRow = below;
                } else {
                    int above = 0;
                    for (Cube other : restored) {
                        if (other.lane == cube.lane && other.row > cube.row) above++;
                    }
                    expectedRow = ROWS - 1 - above;
                }
                if (cube.row != expectedRow) return false;
            }

            cubes.clear();
            cubes.addAll(restored);
            score = restoredScore;
            direction = restoredDirection;
            requestedDirection = null;
            combo = restoredCombo;
            secondsSinceLastMerge = restoredComboAge;
            nextId = restoredNextId;
            pendingGroup = Collections.emptyList();
            pendingAnchor = null;
            pendingTime = 0;
            lastMerge = null;
            pendingLandingHaptics = 0;
            pendingMovementHaptics = 0;
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    /** Removes a settled cube selected by its stable id (emergency split/tool). */
    public boolean removeCube(long cubeId) {
        if (isMergePending() || hasMovingCubes()) return false;
        for (Cube cube : cubes) {
            if (cube.id == cubeId) {
                cubes.remove(cube);
                compactAllLanes();
                return true;
            }
        }
        return false;
    }

    /** Moves a settled cube to an empty cell and repacks affected lanes. */
    public boolean moveCube(long cubeId, int targetLane, int targetRow) {
        if (isMergePending() || hasMovingCubes() || targetLane < 0 || targetLane >= COLUMNS
                || targetRow < 0 || targetRow >= ROWS) return false;
        Cube selected = findCube(cubeId);
        if (selected == null) return false;
        for (Cube cube : cubes) {
            if (cube != selected && cube.lane == targetLane && cube.row == targetRow) return false;
        }
        selected.lane = targetLane;
        selected.row = targetRow;
        compactAllLanes();
        return true;
    }

    /** Swaps two settled cubes, then restores gravity packing in both lanes. */
    public boolean swapCubes(long firstId, long secondId) {
        if (firstId == secondId || isMergePending() || hasMovingCubes()) return false;
        Cube first = findCube(firstId), second = findCube(secondId);
        if (first == null || second == null) return false;
        int lane = first.lane, row = first.row;
        first.lane = second.lane;
        first.row = second.row;
        second.lane = lane;
        second.row = row;
        compactAllLanes();
        return true;
    }

    /** Removes cubes in the selected cell and its eight neighbouring cells. */
    public int bombAt(long cubeId) {
        if (isMergePending() || hasMovingCubes()) return 0;
        Cube center = findCube(cubeId);
        if (center == null) return 0;
        int lane = center.lane, row = center.row, removed = 0;
        for (int i = cubes.size() - 1; i >= 0; i--) {
            Cube cube = cubes.get(i);
            if (Math.abs(cube.lane - lane) <= 1 && Math.abs(cube.row - row) <= 1) {
                cubes.remove(i);
                removed++;
            }
        }
        if (removed > 0) compactAllLanes();
        return removed;
    }

    private Cube findCube(long cubeId) {
        for (Cube cube : cubes) if (cube.id == cubeId) return cube;
        return null;
    }

    public int cubesInLane(int lane) {
        if (lane < 0 || lane >= COLUMNS) return 0;
        return lane(lane).size();
    }

    public long entryEdgeValue(int lane) {
        if (lane < 0 || lane >= COLUMNS) return 0;
        Cube edge = entryEdge(lane(lane));
        return edge == null ? 0 : edge.value;
    }

    /** True if at least one lane can accept this exact queued value right now. */
    public boolean hasLegalLaunch(long value) {
        if (!isPowerOfTwo(value) || isMergePending() || hasMovingCubes()) return false;
        for (int lane = 0; lane < COLUMNS; lane++) {
            List<Cube> stack = lane(lane);
            if (firstEmptyRow(stack) >= 0) return true;
            Cube edge = entryEdge(stack);
            if (edge != null && edge.value == value) return true;
        }
        return false;
    }

    public boolean isFull() {
        for (int lane = 0; lane < COLUMNS; lane++) {
            if (firstEmptyRow(lane(lane)) >= 0) return false;
        }
        return true;
    }
}
