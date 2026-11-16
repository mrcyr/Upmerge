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

    final float screenW, screenH;
    final Rect[] modes = new Rect[3];
    final Rect[] directions = new Rect[2];
    final Rect[] menuActions = new Rect[2];
    final Rect continueButton, newGameButton;
    final Rect[][] utilities = new Rect[2][2];
    final Rect statistics;

    MainMenuLayout(float width, float height, boolean hasSavedGame) {
        screenW = Math.max(1f, width);
        screenH = Math.max(1f, height);
        float center = screenW * .5f;
        float modeW = screenW * .255f, modeGap = screenW * .025f;
        float modeY = screenH * .615f - screenH * .075f * .5f;
        for (int i = 0; i < 3; i++) {
            float cx = center + (i - 1) * (modeW + modeGap);
            modes[i] = new Rect(cx - modeW * .5f, modeY, modeW, screenH * .075f);
        }
        float dirW = screenW * .37f, dirH = screenH * .064f;
        for (int i = 0; i < 2; i++) {
            float cx = center + (i == 0 ? -1f : 1f) * dirW * .52f;
            directions[i] = new Rect(cx - dirW * .5f, screenH * .49f - dirH * .5f, dirW, dirH);
        }
        float actionW = screenW * .385f, actionH = screenH * .060f;
        menuActions[0] = centered(screenW * .295f, screenH * .425f, actionW, actionH);
        menuActions[1] = centered(screenW * .705f, screenH * .425f, actionW, actionH);
        continueButton = hasSavedGame
                ? centered(screenW * .5f, screenH * .355f, screenW * .76f, screenH * .070f) : null;
        newGameButton = centered(screenW * .5f, screenH * (hasSavedGame ? .275f : .355f),
                screenW * .76f, screenH * .075f);
        float utilityW = screenW * .385f, utilityH = screenH * .060f;
        float[] ys = {screenH * .185f, screenH * .105f};
        for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++) {
            float cx = center + (col == 0 ? -1f : 1f) * screenW * .205f;
            utilities[row][col] = centered(cx, ys[row], utilityW, utilityH);
        }
        statistics = centered(center, screenH * .075f, screenW * .9f, screenH * .04f);
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
        if (statistics.contains(x, y)) return Target.STATISTICS;
        return Target.NONE;
    }
}
