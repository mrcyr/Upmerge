package com.upmerge.game;

/**
 * Responsive layout port of UpMerge_iOS_Local.html.
 * Dimensions below are CSS pixels multiplied by device density. The arena
 * receives the remaining flex height exactly as #app's column flex layout does.
 * This class is presentation-only; board coordinates and game rules stay owned
 * by BoardEngine.
 */
final class GameScreenLayout {
    final float screenW, screenH, density;
    final float headerHeight, footerHeight, cell;
    final float boardLeft, boardBottom, boardTop, boardRight;
    final float statBottom, statHeight, pillBottom, pillHeight;
    final float queueBottom, queueHeight, queueLeft, queueCurrentWidth, queueNextWidth, queueGap;
    final float toolbarBottom, toolbarHeight, toolbarLeft, toolbarCardWidth, toolbarGap, hintBottom, hintHeight, pauseSize;
    final float arenaTop, arenaBottom;

    GameScreenLayout(float width, float height, float density) {
        screenW = Math.max(1f, width);
        screenH = Math.max(1f, height);
        this.density = Math.max(.75f, density);
        float d = this.density;
        float w = screenW / d;
        float h = screenH / d;
        boolean compact = h <= 740f;
        boolean veryCompact = h <= 650f;

        float headerDp = veryCompact ? 50f : compact ? 58f : 72f;
        headerHeight = headerDp * d;

        // CSS .hud: 10px bottom padding (6px on compact screens), 10px gap,
        // and .stat padding with the responsive typography from the media rules.
        float statSmall = veryCompact ? 8f : compact ? 8f : 10f;
        float statStrong = veryCompact ? 17f : compact ? 19f : 22f;
        float statH = (veryCompact ? 6f : compact ? 6f : 9f) * 2f
                + statSmall * 1.25f + statStrong * 1.25f
                + (compact ? 6f : 10f);
        float hudTop = h - headerDp;
        float hudBottom = hudTop - statH;
        statHeight = statH * d;
        statBottom = (hudBottom) * d;
        float hudPad = (compact ? 12f : 16f) * d;
        float statGap = (compact ? 7f : 10f) * d;

        float pillH = (veryCompact ? 5f : compact ? 5f : 7f) * 2f
                + (veryCompact ? 8f : compact ? 8f : 10f) * 1.2f
                + (veryCompact ? 13f : 16f) * 1.2f;
        float pillTop = hudBottom - (compact ? 5f : 8f);
        pillHeight = pillH * d;
        pillBottom = (pillTop - pillH) * d;

        // #arena is the flex:1 region between the header/HUD/pills and .bottom.
        float bottomPadTop = (compact ? 5f : 7f);
        float bottomPadBottom = (compact ? 7f : 9f);
        float queueCube = veryCompact ? 28f : compact ? 32f : 38f;
        float queuePad = 8f;
        float queueRowH = Math.max(queueCube, veryCompact ? 28f : compact ? 32f : 38f) + queuePad + 2f;
        float queueMargin = compact ? 5f : 8f;
        float powerH = (veryCompact ? 5f : compact ? 5f : 7f) * 2f
                + (veryCompact ? 16f : compact ? 17f : 20f)
                + (veryCompact ? 6f : compact ? 7f : 8f) * 1.2f + 2f;
        float powerMargin = compact ? 4f : 7f;
        float hintH = veryCompact ? 9f : compact ? 9f : 11f;
        float bottomDp = bottomPadTop + queueRowH + queueMargin + powerH + powerMargin
                + hintH + bottomPadBottom;
        footerHeight = bottomDp * d;

        float arenaTopDp = pillTop - pillH;
        float arenaBottomDp = bottomDp;
        arenaTop = (arenaTopDp - (compact ? 5f : 8f)) * d;
        arenaBottom = arenaBottomDp * d;

        // HTML canvas bounds(): largest square cell fitting inside the actual
        // arena inner box (10px horizontal margin on both sides).
        float arenaW = Math.max(1f, w - 20f);
        float arenaH = Math.max(1f, arenaTopDp - arenaBottomDp);
        float cellDp = Math.min(arenaW / BoardEngine.COLUMNS, arenaH / BoardEngine.ROWS);
        cell = Math.max(1f, cellDp * d);
        float boardW = cell * BoardEngine.COLUMNS;
        float boardH = cell * BoardEngine.ROWS;
        boardLeft = (screenW - boardW) * .5f;
        boardRight = boardLeft + boardW;
        boardBottom = arenaBottom + (arenaTop - arenaBottom - boardH) * .5f;
        boardTop = boardBottom + boardH;

        queueHeight = queueRowH * d;
        queueGap = 12f * d;
        queueCurrentWidth = 108f * d;
        queueNextWidth = 116f * d;
        queueLeft = (screenW - queueCurrentWidth - queueGap - queueNextWidth) * .5f;
        queueBottom = Math.max(0f, (bottomPadBottom + hintH + powerMargin + powerH
                + queueMargin) * d);

        toolbarHeight = powerH * d;
        toolbarBottom = (bottomPadBottom + hintH + powerMargin) * d;
        toolbarLeft = 14f * d;
        toolbarGap = 7f * d;
        toolbarCardWidth = (screenW - 2f * toolbarLeft - 4f * toolbarGap) / 5f;
        hintHeight = hintH * d;
        hintBottom = bottomPadBottom * d;
        pauseSize = 42f * d;
    }

    boolean boardContains(float x, float y) {
        return x >= boardLeft && x <= boardRight && y >= boardBottom && y <= boardTop;
    }

    int toolbarIndex(float x, float y) {
        if (y < toolbarBottom || y > toolbarBottom + toolbarHeight) return -1;
        if (x < toolbarLeft || x > toolbarLeft + 5f * toolbarCardWidth + 4f * toolbarGap) return -1;
        int index = (int) ((x - toolbarLeft) / (toolbarCardWidth + toolbarGap));
        if (index < 0 || index > 4) return -1;
        float cardX = toolbarLeft + index * (toolbarCardWidth + toolbarGap);
        return x <= cardX + toolbarCardWidth ? index : -1;
    }

    boolean pauseContains(float x, float y) {
        float left = screenW - 18f * density - pauseSize;
        float bottom = screenH - 10f * density - pauseSize;
        return x >= left && x <= left + pauseSize && y >= bottom && y <= bottom + pauseSize;
    }
}
