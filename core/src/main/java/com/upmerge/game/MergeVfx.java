package com.upmerge.game;

import java.util.Random;

/**
 * Deterministic, theme-tinted merge impact animation.
 * Pure timing/particle state is separated from rendering so it can be tested.
 */
final class MergeVfx {
    private static final int MAX_PARTICLES = 96;
    private static final float DURATION = .90f;
    private static final float RING_DURATION = .82f;
    private final float[] x = new float[MAX_PARTICLES];
    private final float[] y = new float[MAX_PARTICLES];
    private final float[] previousX = new float[MAX_PARTICLES];
    private final float[] previousY = new float[MAX_PARTICLES];
    private final float[] vx = new float[MAX_PARTICLES];
    private final float[] vy = new float[MAX_PARTICLES];
    private final float[] sizes = new float[MAX_PARTICLES];
    private final float[] ages = new float[MAX_PARTICLES];
    private final float[] lifetimes = new float[MAX_PARTICLES];
    private int count;
    private float elapsed;
    private float centerX, centerY, cellSize;
    private float red, green, blue;
    private boolean active;

    void start(float cx, float cy, float cell, long value, int mergedCount) {
        start(cx, cy, cell, value, mergedCount, CubePalette.rgb(value));
    }

    void start(float cx, float cy, float cell, long value, int mergedCount, int rgb) {
        centerX = cx;
        centerY = cy;
        cellSize = Math.max(1f, cell);
        red = ((rgb >>> 16) & 255) / 255f;
        green = ((rgb >>> 8) & 255) / 255f;
        blue = (rgb & 255) / 255f;
        count = Math.min(MAX_PARTICLES, 62 + Math.max(0, mergedCount - 2) * 10);
        elapsed = 0f;
        active = true;
        Random random = new Random(value * 31L + mergedCount * 997L);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2d * i / count + (random.nextFloat() - .5f) * .20f;
            float scale = cellSize / 39f;
            float speed = (50f + random.nextFloat() * 210f) * scale;
            x[i] = previousX[i] = cx;
            y[i] = previousY[i] = cy;
            vx[i] = (float) Math.cos(angle) * speed;
            vy[i] = (float) Math.sin(angle) * speed;
            sizes[i] = (2f + random.nextFloat() * 4f) * scale;
            ages[i] = 0f;
            lifetimes[i] = .55f + random.nextFloat() * .35f;
        }
    }

    void update(float delta) {
        if (!active) return;
        float dt = Math.max(0f, delta);
        elapsed += dt;
        for (int i = 0; i < count; i++) {
            previousX[i] = x[i];
            previousY[i] = y[i];
            ages[i] += dt;
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            float drag = (float) Math.pow(.98f, dt * 60f);
            vx[i] *= drag;
            vy[i] *= drag;
            vy[i] -= 260f * (cellSize / 39f) * dt;
        }
        if (elapsed >= DURATION) active = false;
    }

    boolean isActive() { return active; }
    float progress() { return Math.min(1f, elapsed / RING_DURATION); }
    float centerX() { return centerX; }
    float centerY() { return centerY; }
    float cellSize() { return cellSize; }
    int particleCount() { return count; }
    float particleX(int i) { return x[i]; }
    float particleY(int i) { return y[i]; }
    float previousX(int i) { return previousX[i]; }
    float previousY(int i) { return previousY[i]; }
    float particleSize(int i) { return sizes[i] * Math.max(.1f, 1f - ages[i] / lifetimes[i]); }
    float particleAlpha(int i) {
        return Math.max(0f, 1f - ages[i] / lifetimes[i]) * (active ? 1f : 0f);
    }
    float red() { return red; }
    float green() { return green; }
    float blue() { return blue; }
}
