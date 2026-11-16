package com.upmerge.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Native UpMerge play scene. The board engine owns all gameplay rules;
 * this class is responsible only for responsive layout, rendering and input.
 */
public final class UpMergeGame extends ApplicationAdapter {
    private final BoardEngine engine = new BoardEngine();
    private final CubeQueue cubeQueue = new CubeQueue();
    private final GlyphLayout textLayout = new GlyphLayout();
    private com.badlogic.gdx.Preferences preferences;
    private float saveTimer;
    private int bestScore;
    private int splitCharges = 2;
    private boolean emergencyDialog;
    private boolean splitSelecting;
    private boolean gameOver;
    private String statusMessage = "";
    private float statusMessageTime;

    private OrthographicCamera camera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;

    private float screenW, screenH;
    private float cell, boardLeft, boardBottom, boardTop;
    private float headerHeight, footerHeight;
    private float uiScale = 1f;
    private float mergeFlashTime;
    private int mergeFlashLane = -1;
    private int mergeFlashRow = -1;
    private int mergeFlashValue;
    private float mergeFlightTime;
    private int mergeFlightTargetLane;
    private int mergeFlightTargetRow;
    private final List<BoardEngine.MergeSource> mergeFlightSources = new ArrayList<>();

    @Override public void create() {
        camera = new OrthographicCamera();
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = createCyrillicFont();
        preferences = Gdx.app.getPreferences("UpMergeNative");
        bestScore = preferences.getInteger("bestScore", 0);
        splitCharges = preferences.getInteger("splitCharges", 2);
        restoreGame();
        font.setColor(0.94f, 0.97f, 1f, 1f);
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    /**
     * Android's bundled Roboto has full Cyrillic coverage. Generate one atlas
     * at startup so every native dialog and HUD label can render Russian text.
     */
    private BitmapFont createCyrillicFont() {
        String[] candidates = {
                "/system/fonts/Roboto-Regular.ttf",
                "/system/fonts/NotoSans-Regular.ttf"
        };
        for (String path : candidates) {
            FreeTypeFontGenerator generator = null;
            try {
                generator = new FreeTypeFontGenerator(Gdx.files.absolute(path));
                FreeTypeFontGenerator.FreeTypeFontParameter parameter =
                        new FreeTypeFontGenerator.FreeTypeFontParameter();
                parameter.size = 24;
                parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS
                        + "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"
                        + "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
                        + "·—№";
                parameter.incremental = false;
                BitmapFont generated = generator.generateFont(parameter);
                generated.setColor(0.94f, 0.97f, 1f, 1f);
                return generated;
            } catch (Exception ignored) {
                // Try the next Android system font; retain a safe fallback below.
            } finally {
                if (generator != null) generator.dispose();
            }
        }
        return new BitmapFont();
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
        saveTimer += dt;
        if (saveTimer >= 0.8f) {
            saveTimer = 0f;
            saveGame();
        }
        if (mergeFlashTime > 0f) mergeFlashTime = Math.max(0f, mergeFlashTime - dt);
        if (statusMessageTime > 0f) statusMessageTime = Math.max(0f, statusMessageTime - dt);
        if (mergeFlightTime > 0f) mergeFlightTime = Math.max(0f, mergeFlightTime - dt);
        BoardEngine.MergeEvent mergeEvent = engine.consumeMergeEvent();
        if (mergeEvent != null) {
            mergeFlashTime = 0.42f;
            mergeFlashLane = mergeEvent.lane;
            mergeFlashRow = mergeEvent.row;
            mergeFlashValue = mergeEvent.value;
            mergeFlightTime = 0.30f;
            mergeFlightTargetLane = mergeEvent.lane;
            mergeFlightTargetRow = mergeEvent.row;
            mergeFlightSources.clear();
            mergeFlightSources.addAll(mergeEvent.sources);
            cubeQueue.onMergedValue(mergeEvent.value);
            bestScore = Math.max(bestScore, engine.score());
            preferences.putInteger("bestScore", bestScore);
        preferences.putInteger("splitCharges", splitCharges);
            preferences.flush();
            saveGame();
        }
        checkBoardCapacity();
        handleInput();

        ScreenUtils.clear(0.025f, 0.038f, 0.060f, 1f);
        drawPanels();
        drawBoard();
        drawCubes();
        drawMergeFlights();
        drawText();
        drawOverlays();
    }

    private void handleInput() {
        if (!Gdx.input.justTouched()) return;
        float x = Gdx.input.getX();
        float y = screenH - Gdx.input.getY();

        if (gameOver) {
            if (y >= screenH * 0.36f && y <= screenH * 0.58f) startNewRun();
            return;
        }
        if (emergencyDialog) {
            if (y >= screenH * 0.42f && y <= screenH * 0.54f && splitCharges > 0) {
                emergencyDialog = false;
                splitSelecting = true;
            } else if (y < screenH * 0.42f) {
                emergencyDialog = false;
                gameOver = true;
                saveGame();
            }
            return;
        }
        if (splitSelecting) {
            BoardEngine.Cube selected = cubeAt(x, y);
            if (selected != null && engine.removeCube(selected.id)) {
                splitCharges--;
                preferences.putInteger("splitCharges", splitCharges);
                preferences.flush();
                splitSelecting = false;
                saveGame();
            }
            return;
        }

        // The direction control is an intentional, large hit target in the header.
        if (y >= screenH - headerHeight) {
            engine.setDirection(engine.direction() == BoardEngine.Direction.UP
                    ? BoardEngine.Direction.DOWN : BoardEngine.Direction.UP);
            return;
        }

        if (y >= boardBottom && y <= boardTop) {
            int lane = MathUtils.clamp((int) ((x - boardLeft) / cell), 0, BoardEngine.COLUMNS - 1);
            if (engine.launch(lane, cubeQueue.current())) {
                cubeQueue.advance();
                statusMessage = "";
                statusMessageTime = 0f;
            } else if (engine.cubesInLane(lane) >= BoardEngine.ROWS) {
                int required = engine.entryEdgeValue(lane);
                statusMessage = required > 0
                        ? "МАКСИМУМ 7 — НУЖЕН " + required
                        : "СТОЛБЕЦ ЗАПОЛНЕН";
                statusMessageTime = 1.8f;
            } else {
                statusMessage = "ДОЖДИСЬ ОСТАНОВКИ КУБИКА";
                statusMessageTime = 1.2f;
            }
        }
    }


    private BoardEngine.Cube cubeAt(float x, float y) {
        for (BoardEngine.Cube cube : engine.cubes()) {
            float cx = boardLeft + (cube.lane + 0.5f) * cell;
            float cy = boardBottom + (BoardEngine.ROWS - 0.5f - cube.visualRow) * cell;
            if (Math.abs(x - cx) <= cell * 0.44f && Math.abs(y - cy) <= cell * 0.44f) return cube;
        }
        return null;
    }

    private void checkBoardCapacity() {
        if (gameOver || emergencyDialog || splitSelecting || engine.isMergePending()) return;
        for (BoardEngine.Cube cube : engine.cubes()) if (cube.moving) return;
        if (!engine.hasLegalLaunch(cubeQueue.current())) emergencyDialog = true;
    }

    private void drawOverlays() {
        // Cube selection is a gameplay mode, not a blocking dialog: keep the
        // board fully visible and tappable after the player chooses SPLIT.
        if (splitSelecting) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.04f, 0.42f, 0.52f, 0.96f);
            shapes.rect(screenW * 0.08f, 5f, screenW * 0.84f, Math.max(30f, footerHeight - 10f));
            shapes.end();
            batch.begin();
            font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.72f, 0.92f));
            font.setColor(1f, 1f, 1f, 1f);
            drawCentered("ВЫБЕРИ КУБИК", screenW * 0.5f, Math.max(24f, footerHeight * 0.58f));
            batch.end();
            return;
        }
        if (!emergencyDialog && !gameOver) return;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.78f);
        shapes.rect(0, 0, screenW, screenH);
        shapes.setColor(0.075f, 0.105f, 0.145f, 1f);
        shapes.rect(screenW * 0.08f, screenH * 0.34f, screenW * 0.84f, screenH * 0.32f);
        if (gameOver) {
            shapes.setColor(0.12f, 0.36f, 0.58f, 1f);
            shapes.rect(screenW * 0.16f, screenH * 0.40f, screenW * 0.68f, 0.14f);
        } else if (emergencyDialog && splitCharges > 0) {
            shapes.setColor(0.12f, 0.48f, 0.34f, 1f);
            shapes.rect(screenW * 0.16f, screenH * 0.42f, screenW * 0.68f, screenH * 0.10f);
        }
        shapes.end();
        batch.begin();
        font.setColor(1f, 1f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 300f, 0.95f, 1.25f));
        drawCentered(gameOver ? "ИГРА ОКОНЧЕНА" : emergencyDialog ? "ПОЛЕ ЗАПОЛНЕНО" : "ВЫБЕРИ КУБИК",
                screenW * 0.5f, screenH * 0.59f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.78f, 1f));
        if (gameOver) {
            drawCentered("СЧЁТ: " + engine.score() + "   РЕКОРД: " + bestScore, screenW * 0.5f, screenH * 0.49f);
            drawCentered("НОВАЯ ИГРА", screenW * 0.5f, screenH * 0.435f);
        } else if (emergencyDialog) {
            drawCentered(splitCharges > 0 ? "РАЗБИТЬ КУБИК · ЗАРЯД " + splitCharges
                    : "НЕТ ЗАРЯДОВ", screenW * 0.5f, screenH * 0.47f);
            drawCentered("НАЖМИ НИЖЕ ДЛЯ ЗАВЕРШЕНИЯ", screenW * 0.5f, screenH * 0.38f);
        } else {
            drawCentered("ВЫБЕРИ ЛЮБОЙ КУБИК НА ПОЛЕ", screenW * 0.5f, screenH * 0.45f);
        }
        batch.end();
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
            if (cube.mergePending) {
                float mergeProgress = MathUtils.clamp(
                        engine.mergeElapsedSeconds() / BoardEngine.MERGE_DELAY_SECONDS, 0f, 1f);
                if (cube.forcedMergeIncoming) {
                    // The incoming cube is entering an already occupied edge cell.
                    // Shrink it into the resident cube instead of drawing two
                    // identical squares directly on top of each other.
                    size *= Math.max(0.04f, 1f - mergeProgress * 0.96f);
                } else {
                    float pulse = 0.5f + 0.5f * MathUtils.sin(engine.mergeElapsedSeconds() * 18f);
                    size *= 1f + pulse * 0.11f;
                }
            } else if (mergeFlashTime > 0f && cube.value == mergeFlashValue
                    && cube.lane == mergeFlashLane && cube.row == mergeFlashRow) {
                float progress = 1f - mergeFlashTime / 0.42f;
                size *= 1f + (1f - progress) * 0.18f;
            }
            // Keep the animated cube centered in its cell while it pulses.
            float centeredOffset = (cell - size) * 0.5f;
            x = boardLeft + cube.lane * cell + centeredOffset;
            y = boardBottom + (BoardEngine.ROWS - 1 - cube.visualRow) * cell + centeredOffset;
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

        // In emergency split mode outline every selectable cube. This makes
        // the tap target obvious without dimming or covering the board.
        if (splitSelecting) {
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(0.25f, 0.95f, 1f, 1f);
            for (BoardEngine.Cube cube : engine.cubes()) {
                float x = boardLeft + cube.lane * cell + cell * 0.045f;
                float y = boardBottom + (BoardEngine.ROWS - 1 - cube.visualRow) * cell + cell * 0.045f;
                shapes.rect(x, y, cell * 0.91f, cell * 0.91f);
            }
            shapes.end();
        }

        // A short expanding outline marks the result of a completed merge.
        if (mergeFlashTime > 0f && mergeFlashLane >= 0 && mergeFlashRow >= 0) {
            float progress = 1f - mergeFlashTime / 0.42f;
            float centerX = boardLeft + (mergeFlashLane + 0.5f) * cell;
            float centerY = boardBottom + (BoardEngine.ROWS - 0.5f - mergeFlashRow) * cell;
            float radius = cell * (0.28f + progress * 0.48f);
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(0.45f, 0.90f, 1f, 1f - progress);
            shapes.circle(centerX, centerY, radius, 32);
            shapes.end();
        }
    }

    /** Draws the source cubes flying into the merge anchor, as in 4.11. */
    private void drawMergeFlights() {
        if (mergeFlightTime <= 0f || mergeFlightSources.isEmpty()) return;
        float progress = 1f - mergeFlightTime / 0.30f;
        float targetX = boardLeft + (mergeFlightTargetLane + 0.5f) * cell;
        float targetY = boardBottom + (BoardEngine.ROWS - 0.5f - mergeFlightTargetRow) * cell;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (BoardEngine.MergeSource source : mergeFlightSources) {
            if (source.lane == mergeFlightTargetLane && source.row == mergeFlightTargetRow) continue;
            float startX = boardLeft + (source.lane + 0.5f) * cell;
            float startY = boardBottom + (BoardEngine.ROWS - 0.5f - source.row) * cell;
            float x = MathUtils.lerp(startX, targetX, progress);
            float y = MathUtils.lerp(startY, targetY, progress);
            float size = cell * 0.72f * (1f - progress * 0.38f);
            shapes.setColor(0.20f, 0.72f, 0.82f, 1f - progress * 0.25f);
            shapes.rect(x - size * 0.5f, y - size * 0.5f, size, size);
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
        font.draw(batch, "DROP", pad + cardW + cardGap + 10f, cardY + cardH - 12f);

        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 245f, 1.25f, 1.75f));
        font.setColor(0.96f, 0.98f, 1f, 1f);
        font.draw(batch, Integer.toString(engine.score()), pad + 10f, cardY + cardH * 0.48f);
        font.draw(batch, Integer.toString(cubeQueue.current()), pad + cardW + cardGap + 10f,
                cardY + cardH * 0.48f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.78f, 0.95f));
        font.setColor(0.58f, 0.70f, 0.82f, 1f);
        font.draw(batch, "NEXT: " + cubeQueue.upcoming(), pad + cardW + cardGap + 10f,
                cardY + 12f);

        font.getData().setScale(uiScale * MathUtils.clamp(cell / 39f, 0.90f, 1.35f));
        font.setColor(0.68f, 0.80f, 0.91f, 1f);
        String direction = engine.direction() == BoardEngine.Direction.UP ? "GRAVITY: UP" : "GRAVITY: DOWN";
        drawCentered(direction, screenW * 0.5f, screenH - headerHeight + 5f);

        font.setColor(0.58f, 0.68f, 0.78f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 330f, 0.90f, 1.10f));
        drawCentered("TAP A COLUMN TO DROP", screenW * 0.5f, footerHeight * 0.62f);
        drawCentered("TAP THE TOP PANEL TO CHANGE DIRECTION",
                screenW * 0.5f, footerHeight * 0.28f);
        if (statusMessageTime > 0f && !splitSelecting && !emergencyDialog && !gameOver) {
            font.getData().setScale(uiScale * MathUtils.clamp(screenW / 360f, 0.82f, 1.08f));
            font.setColor(1f, 0.78f, 0.34f, Math.min(1f, statusMessageTime * 2f));
            drawCentered(statusMessage, screenW * 0.5f, footerHeight * 0.94f);
        }

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


    private void startNewRun() {
        engine.clear();
        engine.setDirection(BoardEngine.Direction.UP);
        cubeQueue.reset(2048);
        splitCharges = 2;
        emergencyDialog = false;
        splitSelecting = false;
        gameOver = false;
        mergeFlashTime = 0f;
        mergeFlightTime = 0f;
        mergeFlightSources.clear();
        preferences.putInteger("splitCharges", splitCharges);
        saveGame();
    }

    private void saveGame() {
        if (preferences == null) return;
        String board = engine.saveState();
        if (board == null) return; // Keep the last complete board while animations are in flight.
        preferences.putString("boardV1", board);
        preferences.putInteger("queueGoal", cubeQueue.goalTarget());
        preferences.putInteger("queueCurrent", cubeQueue.current());
        preferences.putInteger("queueUpcoming", cubeQueue.upcoming());
        preferences.putInteger("bestScore", bestScore);
        preferences.flush();
    }

    private void restoreGame() {
        if (preferences == null) return;
        String board = preferences.getString("boardV1", "");
        if (board.isEmpty() || !engine.loadState(board)) return;
        int goal = preferences.getInteger("queueGoal", 2048);
        int current = preferences.getInteger("queueCurrent", 2);
        int upcoming = preferences.getInteger("queueUpcoming", 2);
        splitCharges = preferences.getInteger("splitCharges", 2);
        if (!cubeQueue.restore(goal, current, upcoming)) {
            engine.clear();
            preferences.remove("boardV1");
            preferences.flush();
        }
    }

    @Override public void pause() {
        saveGame();
    }

    @Override public void dispose() {
        saveGame();
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
