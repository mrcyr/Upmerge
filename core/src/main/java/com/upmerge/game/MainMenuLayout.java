package com.upmerge.game;

/** Shared responsive geometry for the main menu. Coordinates use libGDX's
 * bottom-left origin so rendering and touch hit-testing use identical bounds. */
final class MainMenuLayout {
    enum Target { MODE_CLASSIC, MODE_TIMED, MODE_CHALLENGE, DIRECTION_UP, DIRECTION_DOWN,
        THEMES, DAILY, CONTINUE, NEW_GAME, SHOP, ACHIEVEMENTS, STATISTICS, TASKS, NONE }

    static final class Rect {
        final float x, y, width, height;
        Rect(float x, float y, float width, float height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
        float centerX() { return x + width * .5f; }
        float centerY() { return y + height * .5f; }
        boolean contains(float px, float py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
        boolean overlaps(Rect other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }

    final float screenW, screenH, density, contentWidth;
    final Rect[] modes = new Rect[3];
    final Rect[] directions = new Rect[2];
    final Rect[] menuActions = new Rect[2];
    final Rect continueButton, newGameButton;
    final Rect[][] utilities = new Rect[2][2];

    MainMenuLayout(float width, float height, boolean hasSavedGame) {
        this(width, height, hasSavedGame, 1f);
    }

    MainMenuLayout(float width, float height, boolean hasSavedGame, float density) {
        screenW = Math.max(1f, width);
        screenH = Math.max(1f, height);
        this.density = Math.max(.75f, density);
        float panelWidth = Math.min(390f * this.density, screenW - 44f * this.density);
        contentWidth = Math.max(120f * this.density, panelWidth - 44f * this.density);
        float center = screenW * .5f;
        float modeGap = 7f * this.density;
        float modeW = (contentWidth - modeGap * 2f) / 3f;
        float modeH = 40f * this.density;
        float modeY = screenH * .64f - modeH * .5f;
        for (int i = 0; i < 3; i++) {
            float cx = center + (i - 1) * (modeW + modeGap);
            modes[i] = new Rect(cx - modeW * .5f, modeY, modeW, modeH);
        }
        float dirGap = 7f * this.density;
        float dirW = (contentWidth - dirGap) * .5f, dirH = 40f * this.density;
        for (int i = 0; i < 2; i++) {
            float cx = center + (i == 0 ? -1f : 1f) * (dirW + dirGap) * .5f;
            directions[i] = new Rect(cx - dirW * .5f,
                    screenH * (compact ? .565f : .57f) - dirH * .5f, dirW, dirH);
        }
        boolean compact = screenH / this.density < 680f;
        float actionGap = 8f * this.density;
        float actionW = (contentWidth - actionGap) * .5f, actionH = 40f * this.density;
        float actionsY = screenH * (compact ? .49f : .50f);
        menuActions[0] = centered(center - (actionW + actionGap) * .5f,
                actionsY, actionW, actionH);
        menuActions[1] = centered(center + (actionW + actionGap) * .5f,
                actionsY, actionW, actionH);
        continueButton = hasSavedGame
                ? centered(center, screenH * (compact ? .405f : .43f), contentWidth, 48f * this.density) : null;
        newGameButton = centered(center, screenH * (hasSavedGame ? (compact ? .315f : .36f) : (compact ? .405f : .43f)),
                contentWidth, 48f * this.density);
        float utilityGap = 8f * this.density;
        float utilityW = (contentWidth - utilityGap) * .5f, utilityH = 40f * this.density;
        float[] ys = compact ? new float[]{screenH * .235f, screenH * .16f}
                : new float[]{screenH * .28f, screenH * .20f};
        for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++) {
            float cx = center + (col == 0 ? -1f : 1f) * (utilityW + utilityGap) * .5f;
            utilities[row][col] = centered(cx, ys[row], utilityW, utilityH);
        }
    }

    private static Rect centered(float cx, float cy, float width, float height) {
        return new Rect(cx - width * .5f, cy - height * .5f, width, height);
    }

    Target hit(float x, float y) {
        for (int i = 0; i < modes.length; i++) if (modes[i].contains(x, y))
            return Target.values()[i];
        for (int i = 0; i < directions.length; i++) if (directions[i].contains(x, y))
            return i == 0 ? Target.DIRECTION_UP : Target.DIRECTION_DOWN;
        for (int i = 0; i < menuActions.length; i++) if (menuActions[i].contains(x, y)) return i == 0 ? Target.THEMES : Target.DAILY;
        if (continueButton != null && continueButton.contains(x, y)) return Target.CONTINUE;
        if (newGameButton.contains(x, y)) return Target.NEW_GAME;
        for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++)
            if (utilities[row][col].contains(x, y)) {
                if (row == 0) return col == 0 ? Target.SHOP : Target.ACHIEVEMENTS;
                return col == 0 ? Target.STATISTICS : Target.TASKS;
            }
        return Target.NONE;
    }
}
