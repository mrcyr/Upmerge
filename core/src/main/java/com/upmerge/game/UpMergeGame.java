package com.upmerge.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;

/**
 * Native UpMerge play scene. The board engine owns all gameplay rules;
 * this class is responsible only for responsive layout, rendering and input.
 */
public final class UpMergeGame extends ApplicationAdapter {
    private final BoardEngine engine = new BoardEngine();
    private final GlyphLayout textLayout = new GlyphLayout();

    private OrthographicCamera camera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;

    private float screenW, screenH;
    private float cell, boardLeft, boardBottom, boardTop;
    private float headerHeight, footerHeight;
    private float uiScale = 1f;
    private int nextValue = 2;

    @Override public void create() {
        camera = new OrthographicCamera();
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(0.94f, 0.97f, 1f, 1f);
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override public void resize(int width, int height) {
        screenW = Math.max(1, width);
        screenH = Math.max(1, height);
        // libGDX renders in physical pixels on Android; the built-in BitmapFont
        // is small in pixel terms, so scale UI typography with device density.
        uiScale = Math.max(1f, Gdx.graphics.getDensity());
        camera.setToOrtho(false, screenW, screenH);
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        headerHeight = Math.max(82f, screenH * 0.145f);
        footerHeight = Math.max(54f, screenH * 0.095f);
        float sideMargin = Math.max(10f, screenW * 0.035f);
        float availableHeight = Math.max(7f, screenH - headerHeight - footerHeight - 20f);
        cell = Math.max(1f, Math.min((screenW - sideMargin * 2f) / BoardEngine.COLUMNS,
                availableHeight / BoardEngine.ROWS));
        boardLeft = (screenW - cell * BoardEngine.COLUMNS) * 0.5f;
        boardBottom = footerHeight + 10f + (availableHeight - cell * BoardEngine.ROWS) * 0.5f;
        boardTop = boardBottom + cell * BoardEngine.ROWS;
        font.getData().setScale(uiScale * MathUtils.clamp(cell / 43f, 0.90f, 1.55f));
    }

    @Override public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
        engine.update(dt);
        handleInput();

        ScreenUtils.clear(0.025f, 0.038f, 0.060f, 1f);
        drawPanels();
        drawBoard();
        drawCubes();
        drawText();
    }

    private void handleInput() {
        if (!Gdx.input.justTouched()) return;
        float x = Gdx.input.getX();
        float y = screenH - Gdx.input.getY();

        // The direction control is an intentional, large hit target in the header.
        if (y >= screenH - headerHeight) {
            engine.setDirection(engine.direction() == BoardEngine.Direction.UP
                    ? BoardEngine.Direction.DOWN : BoardEngine.Direction.UP);
            return;
        }

        if (y >= boardBottom && y <= boardTop) {
            int lane = MathUtils.clamp((int) ((x - boardLeft) / cell), 0, BoardEngine.COLUMNS - 1);
            if (engine.launch(lane, nextValue)) {
                nextValue = nextValue >= 128 ? 2 : nextValue * 2;
            }
        }
    }

    private void drawPanels() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.045f, 0.065f, 0.095f, 1f);
        shapes.rect(0, screenH - headerHeight, screenW, headerHeight);
        shapes.setColor(0.035f, 0.050f, 0.075f, 1f);
        shapes.rect(0, 0, screenW, footerHeight);

        // Header information cards.
        float pad = Math.max(10f, screenW * 0.04f);
        float cardGap = 8f;
        float cardY = screenH - headerHeight + 12f;
        float cardH = headerHeight - 24f;
        float cardW = (screenW - pad * 2f - cardGap) * 0.5f;
        shapes.setColor(0.075f, 0.105f, 0.145f, 1f);
        shapes.rect(pad, cardY, cardW, cardH);
        shapes.setColor(0.075f, 0.105f, 0.145f, 1f);
        shapes.rect(pad + cardW + cardGap, cardY, cardW, cardH);

        shapes.end();
    }

    private void drawBoard() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.055f, 0.075f, 0.105f, 1f);
        shapes.rect(boardLeft - 2f, boardBottom - 2f,
                cell * BoardEngine.COLUMNS + 4f, cell * BoardEngine.ROWS + 4f);

        for (int row = 0; row < BoardEngine.ROWS; row++) {
            for (int column = 0; column < BoardEngine.COLUMNS; column++) {
                boolean alternate = ((row + column) & 1) == 0;
                shapes.setColor(alternate ? 0.105f : 0.085f,
                        alternate ? 0.135f : 0.112f,
                        alternate ? 0.178f : 0.150f, 1f);
                shapes.rect(boardLeft + column * cell + 1f,
                        boardBottom + row * cell + 1f, cell - 2f, cell - 2f);
            }
        }
        shapes.end();
    }

    private void drawCubes() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (BoardEngine.Cube cube : engine.cubes()) {
            float x = boardLeft + cube.lane * cell + cell * 0.075f;
            float y = boardBottom + (BoardEngine.ROWS - 1 - cube.visualRow) * cell + cell * 0.075f;
            float size = cell * 0.85f;
            float hue = ((float) (Math.log(cube.value) / Math.log(2.0)) * 0.105f) % 1f;
            float red = 0.16f + 0.42f * hue;
            float green = 0.48f + 0.20f * (1f - hue);
            float blue = 0.72f - 0.22f * hue;

            // Soft offset shadow, dark body, then a narrow top highlight.
            shapes.setColor(0.012f, 0.020f, 0.032f, 1f);
            shapes.rect(x + 1.5f, y - 2f, size, size);
            shapes.setColor(red * 0.58f, green * 0.58f, blue * 0.62f, 1f);
            shapes.rect(x, y, size, size);
            shapes.setColor(red, green, blue, 1f);
            shapes.rect(x + 2f, y + 2f, size - 4f, size - 4f);
            shapes.setColor(Math.min(1f, red + 0.18f), Math.min(1f, green + 0.16f),
                    Math.min(1f, blue + 0.14f), 1f);
            shapes.rect(x + 3f, y + size - 5f, size - 6f, 2f);
        }
        shapes.end();
    }

    private void drawText() {
        batch.begin();
        float pad = Math.max(10f, screenW * 0.04f);
        float cardGap = 8f;
        float cardW = (screenW - pad * 2f - cardGap) * 0.5f;
        float cardY = screenH - headerHeight + 12f;
        float cardH = headerHeight - 24f;
        float labelScale = uiScale * MathUtils.clamp(screenW / 360f, 0.90f, 1.15f);

        font.getData().setScale(labelScale);
        font.setColor(0.58f, 0.70f, 0.82f, 1f);
        font.draw(batch, "SCORE", pad + 10f, cardY + cardH - 12f);
        font.draw(batch, "NEXT", pad + cardW + cardGap + 10f, cardY + cardH - 12f);

        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 245f, 1.25f, 1.75f));
        font.setColor(0.96f, 0.98f, 1f, 1f);
        font.draw(batch, Integer.toString(engine.score()), pad + 10f, cardY + cardH * 0.48f);
        font.draw(batch, Integer.toString(nextValue), pad + cardW + cardGap + 10f,
                cardY + cardH * 0.48f);

        font.getData().setScale(uiScale * MathUtils.clamp(cell / 39f, 0.90f, 1.35f));
        font.setColor(0.68f, 0.80f, 0.91f, 1f);
        String direction = engine.direction() == BoardEngine.Direction.UP ? "GRAVITY  ↑" : "GRAVITY  ↓";
        drawCentered(direction, screenW * 0.5f, screenH - headerHeight + 5f);

        font.setColor(0.58f, 0.68f, 0.78f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 330f, 0.90f, 1.10f));
        drawCentered("TAP A COLUMN TO DROP", screenW * 0.5f, footerHeight * 0.62f);
        drawCentered("TAP THE TOP PANEL TO CHANGE DIRECTION",
                screenW * 0.5f, footerHeight * 0.28f);

        // Center each value in its cube, with a scale that remains readable for large numbers.
        for (BoardEngine.Cube cube : engine.cubes()) {
            String value = Integer.toString(cube.value);
            float sizeScale = value.length() >= 5 ? 0.58f : value.length() >= 4 ? 0.68f
                    : value.length() >= 3 ? 0.78f : 0.94f;
            font.getData().setScale(uiScale * MathUtils.clamp(cell / 42f, 0.95f, 1.50f) * sizeScale);
            font.setColor(1f, 1f, 1f, 1f);
            float centerX = boardLeft + (cube.lane + 0.5f) * cell;
            float centerY = boardBottom + (BoardEngine.ROWS - 0.5f - cube.visualRow) * cell;
            drawCentered(value, centerX, centerY + font.getCapHeight() * 0.35f);
        }
        batch.end();
    }

    private void drawCentered(String value, float centerX, float baselineY) {
        textLayout.setText(font, value);
        font.draw(batch, value, centerX - textLayout.width * 0.5f, baselineY);
    }

    @Override public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
