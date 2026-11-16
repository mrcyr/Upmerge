package com.upmerge.game;

/** Responsive geometry shared by the native game renderer and input handling. */
final class GameScreenLayout {
    final float screenW, screenH, density;
    final float headerHeight, footerHeight, cell;
    final float boardLeft, boardBottom, boardTop, boardRight;
    final float toolbarHitHeight;

    GameScreenLayout(float width, float height, float density) {
        screenW = Math.max(1f, width);
        screenH = Math.max(1f, height);
        this.density = Math.max(.75f, density);
        headerHeight = Math.max(174f * this.density, screenH * .223f);
        footerHeight = Math.max(146f * this.density, screenH * .19f);
        float sideMargin = Math.max(10f * this.density, screenW * .035f);
        float availableHeight = Math.max(7f * this.density,
                screenH - headerHeight - footerHeight - 20f * this.density);
        cell = Math.max(1f, Math.min((screenW - sideMargin * 2f) / BoardEngine.COLUMNS,
                availableHeight / BoardEngine.ROWS));
        boardLeft = (screenW - cell * BoardEngine.COLUMNS) * .5f;
        boardRight = boardLeft + cell * BoardEngine.COLUMNS;
        boardBottom = footerHeight + 10f * this.density
                + (availableHeight - cell * BoardEngine.ROWS) * .5f;
        boardTop = boardBottom + cell * BoardEngine.ROWS;
        toolbarHitHeight = 70f * this.density;
    }

    boolean boardContains(float x, float y) {
        return x >= boardLeft && x <= boardRight && y >= boardBottom && y <= boardTop;
    }

    int toolbarIndex(float x, float y) {
        if (y < 0f || y > toolbarHitHeight) return -1;
        return Math.max(0, Math.min(4, (int) (x / (screenW / 5f))));
    }

    boolean pauseContains(float x, float y) {
        return x >= screenW * .78f && y >= screenH - 48f * density;
    }
}
