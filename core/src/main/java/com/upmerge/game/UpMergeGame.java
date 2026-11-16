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
    private long bestScore;
    private int splitCharges = 2;
    private GameEconomy economy;
    private boolean shopVisible;
    private boolean emergencyDialog;
    private boolean splitSelecting;
    private boolean gameOver;
    private boolean menuVisible = true;
    private boolean modeSelectionVisible;
    private boolean settingsVisible;
    private boolean hapticsEnabled = true;
    private boolean hasSavedGame;
    private long[] activeHapticPattern = new long[0];
    private int activeHapticIndex;
    private float hapticTimeRemaining;
    private boolean paused;
    private GameMode gameMode = GameMode.CLASSIC;
    private float timeLeft = 90f;
    private long challengeGoal = 2048;
    private enum GameMode { CLASSIC, TIMED, CHALLENGE }

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
    private long mergeFlashValue;
    private float mergeFlightTime;
    private float levelUpTime;
    private int mergeFlightTargetLane;
    private int mergeFlightTargetRow;
    private final List<BoardEngine.MergeSource> mergeFlightSources = new ArrayList<>();

    @Override public void create() {
        camera = new OrthographicCamera();
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = createCyrillicFont();
        preferences = Gdx.app.getPreferences("UpMergeNative");
        bestScore = preferences.getLong("bestScore", 0L);
        splitCharges = preferences.getInteger("splitCharges", 2);
        economy = new GameEconomy(preferences.getLong("coins", 0L));
        hapticsEnabled = preferences.getBoolean("hapticsEnabled", true);
        restoreGame();
        menuVisible = true;
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
        if (!menuVisible && !paused && !gameOver && !emergencyDialog) {
            if (gameMode == GameMode.TIMED) {
                timeLeft = Math.max(0f, timeLeft - dt);
                if (timeLeft <= 0f) gameOver = true;
            }
            engine.update(dt);
        }
        saveTimer += dt;
        if (saveTimer >= 0.8f) {
            saveTimer = 0f;
            saveGame();
        }
        if (mergeFlashTime > 0f) mergeFlashTime = Math.max(0f, mergeFlashTime - dt);
        if (levelUpTime > 0f) levelUpTime = Math.max(0f, levelUpTime - dt);
        if (mergeFlightTime > 0f) mergeFlightTime = Math.max(0f, mergeFlightTime - dt);
        int movementHaptics = engine.consumeMovementHaptics();
        int landingHaptics = engine.consumeLandingHaptics();
        BoardEngine.MergeEvent mergeEvent = engine.consumeMergeEvent();
        enqueueHapticFeedback(movementHaptics, landingHaptics, mergeEvent != null);
        updateHapticFeedback(dt);
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
            boolean reachedChallenge = gameMode == GameMode.CHALLENGE && mergeEvent.value >= challengeGoal;
            boolean advancedLevel = cubeQueue.onMergedValue(mergeEvent.value);
            if (advancedLevel) levelUpTime = 2.0f;
            if (reachedChallenge) gameOver = true;
            bestScore = Math.max(bestScore, engine.score());
            economy.rewardForMerge(mergeEvent.points);
            preferences.putLong("bestScore", bestScore);
            preferences.putLong("coins", economy.coins());
        preferences.putInteger("splitCharges", splitCharges);
            preferences.flush();
            saveGame();
        }
        if (!menuVisible && !paused) checkBoardCapacity();
        handleInput();

        ScreenUtils.clear(0.025f, 0.038f, 0.060f, 1f);
        drawPanels();
        drawBoard();
        drawCubes();
        drawMergeFlights();
        drawText();
        drawOverlays();
        if (menuVisible) {
            if (settingsVisible) drawSettingsMenu();
            else if (shopVisible) drawShopMenu();
            else if (modeSelectionVisible) drawModeMenu();
            else drawMainMenu();
        } else if (paused) drawPauseMenu();
    }

    /**
     * Queue one Android-compatible libGDX vibration per cube movement/landing,
     * followed by a stronger pulse for each completed merge. Input.vibrate()
     * accepts a single duration; waveform arrays are Android Vibrator API
     * details and are not part of the cross-platform libGDX Input interface.
     */
    private void enqueueHapticFeedback(int movementCount, int landingCount, boolean merged) {
        if (!hapticsEnabled) return;
        int movement = Math.min(12, Math.max(0, movementCount));
        int landing = Math.min(12, Math.max(0, landingCount));
        int pulseCount = movement + landing + (merged ? 1 : 0);
        if (pulseCount == 0) return;

        long[] next = new long[pulseCount * 2 - 1];
        int index = 0;
        for (int i = 0; i < movement; i++) {
            if (index > 0) next[index++] = 28L;
            next[index++] = 14L;
        }
        for (int i = 0; i < landing; i++) {
            if (index > 0) next[index++] = 34L;
            next[index++] = 22L;
        }
        if (merged) {
            if (index > 0) next[index++] = 42L;
            next[index++] = 78L;
        }

        // Preserve an in-progress sequence and append this frame's events.
        int remaining = activeHapticPattern.length - activeHapticIndex;
        long[] combined = new long[remaining + index];
        if (remaining > 0) {
            System.arraycopy(activeHapticPattern, activeHapticIndex, combined, 0, remaining);
        }
        System.arraycopy(next, 0, combined, remaining, index);
        activeHapticPattern = combined;
        activeHapticIndex = 0;
    }

    private void updateHapticFeedback(float deltaSeconds) {
        if (!hapticsEnabled) {
            activeHapticPattern = new long[0];
            activeHapticIndex = 0;
            hapticTimeRemaining = 0f;
            return;
        }
        hapticTimeRemaining -= deltaSeconds;
        if (hapticTimeRemaining > 0f) return;
        if (activeHapticIndex >= activeHapticPattern.length) {
            activeHapticPattern = new long[0];
            activeHapticIndex = 0;
            return;
        }
        long segment = activeHapticPattern[activeHapticIndex++];
        if ((activeHapticIndex & 1) == 1) {
            Gdx.input.vibrate((int) segment);
        }
        hapticTimeRemaining = segment / 1000f;
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            if (menuVisible) {
                if (settingsVisible) settingsVisible = false;
                else if (shopVisible) shopVisible = false;
                else if (modeSelectionVisible) modeSelectionVisible = false;
            } else if (paused) {
                paused = false;
            } else if (!gameOver && !emergencyDialog && !splitSelecting) {
                paused = true;
                saveGame();
            } else if (splitSelecting) {
                splitSelecting = false;
            } else if (emergencyDialog) {
                emergencyDialog = false;
                gameOver = true;
                saveGame();
            } else {
                menuVisible = true;
                gameOver = false;
                saveGame();
            }
            return;
        }
        if (!Gdx.input.justTouched()) return;
        float x = Gdx.input.getX();
        float y = screenH - Gdx.input.getY();

        if (menuVisible) {
            if (shopVisible) {
                if (y > screenH * 0.36f && y <= screenH * 0.56f) {
                    if (economy.buySplitChargePack()) {
                        splitCharges += GameEconomy.SPLIT_CHARGE_PACK_SIZE;
                        preferences.putLong("coins", economy.coins());
                        preferences.putInteger("splitCharges", splitCharges);
                        preferences.flush();
                    }
                } else if (y <= screenH * 0.30f) shopVisible = false;
                return;
            }
            if (settingsVisible) {
                if (y > screenH * 0.40f && y <= screenH * 0.62f) {
                    hapticsEnabled = !hapticsEnabled;
                    preferences.putBoolean("hapticsEnabled", hapticsEnabled);
                    preferences.flush();
                } else if (y <= screenH * 0.30f) {
                    settingsVisible = false;
                }
                return;
            }
            if (!modeSelectionVisible) {
                if (hasSavedGame && y > screenH * 0.48f && y <= screenH * 0.61f) {
                    menuVisible = false;
                    return;
                }
                if (y > screenH * 0.32f && y <= screenH * 0.47f) modeSelectionVisible = true;
                else if (y > screenH * 0.17f && y <= screenH * 0.29f) settingsVisible = true;
                else if (y > screenH * 0.05f && y <= screenH * 0.16f) shopVisible = true;
            } else {
                if (y > screenH * 0.51f && y <= screenH * 0.68f) startMode(GameMode.CLASSIC, true);
                else if (y > screenH * 0.34f && y <= screenH * 0.51f) startMode(GameMode.TIMED, true);
                else if (y > screenH * 0.17f && y <= screenH * 0.34f) startMode(GameMode.CHALLENGE, true);
                else if (y <= screenH * 0.16f) modeSelectionVisible = false;
            }
            return;
        }
        if (paused) {
            if (y > screenH * 0.56f) paused = false;
            else if (y > screenH * 0.36f) startMode(gameMode, false);
            else { paused = false; menuVisible = true; saveGame(); }
            return;
        }
        if (gameOver) {
            if (y >= screenH * 0.36f && y <= screenH * 0.58f) startMode(gameMode, true);
            else if (y < screenH * 0.36f) { menuVisible = true; gameOver = false; }
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
        if (y >= screenH - headerHeight && x >= screenW * 0.78f) {
            paused = true;
            saveGame();
            return;
        }
        if (y >= screenH - headerHeight) {
            engine.setDirection(engine.direction() == BoardEngine.Direction.UP
                    ? BoardEngine.Direction.DOWN : BoardEngine.Direction.UP);
            return;
        }

        if (y >= boardBottom && y <= boardTop) {
            int lane = MathUtils.clamp((int) ((x - boardLeft) / cell), 0, BoardEngine.COLUMNS - 1);
            if (engine.launch(lane, cubeQueue.current())) {
                cubeQueue.advance();
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
            drawCentered("В ГЛАВНОЕ МЕНЮ", screenW * 0.5f, screenH * 0.365f);
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
        font.draw(batch, "СЧЁТ", pad + 10f, cardY + cardH - 12f);
        font.draw(batch, "КУБИК", pad + cardW + cardGap + 10f, cardY + cardH - 12f);

        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 245f, 1.25f, 1.75f));
        font.setColor(0.96f, 0.98f, 1f, 1f);
        font.draw(batch, NumberFormatter.compact(engine.score()), pad + 10f, cardY + cardH * 0.48f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.72f, 0.90f));
        font.setColor(0.45f, 0.82f, 1f, 1f);
        font.draw(batch, "ЦЕЛЬ: " + NumberFormatter.compact(cubeQueue.goalTarget()),
                pad + 10f, cardY + 12f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 245f, 1.25f, 1.75f));
        font.setColor(0.96f, 0.98f, 1f, 1f);
        font.draw(batch, NumberFormatter.compact(cubeQueue.current()), pad + cardW + cardGap + 10f,
                cardY + cardH * 0.48f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.78f, 0.95f));
        font.setColor(0.58f, 0.70f, 0.82f, 1f);
        font.draw(batch, "ДАЛЕЕ: " + NumberFormatter.compact(cubeQueue.upcoming()), pad + cardW + cardGap + 10f,
                cardY + 12f);

        font.getData().setScale(uiScale * MathUtils.clamp(cell / 39f, 0.90f, 1.35f));
        font.setColor(0.68f, 0.80f, 0.91f, 1f);
        String direction = engine.direction() == BoardEngine.Direction.UP
                ? "ГРАВИТАЦИЯ: ВВЕРХ" : "ГРАВИТАЦИЯ: ВНИЗ";
        if (gameMode == GameMode.TIMED) {
            direction += " · " + Math.max(0, (int) Math.ceil(timeLeft)) + " СЕК.";
        }
        drawCenteredFit(direction, screenW * 0.43f, screenH - headerHeight + 5f,
                screenW * 0.68f, uiScale * MathUtils.clamp(cell / 39f, 0.90f, 1.35f));
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.72f, 0.9f));
        font.setColor(0.9f, 0.94f, 1f, 1f);
        drawCentered("Ⅱ", screenW * 0.91f, screenH - headerHeight + 5f);

        font.setColor(0.58f, 0.68f, 0.78f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 330f, 0.90f, 1.10f));
        drawCenteredFit("НАЖМИ НА СТОЛБЕЦ, ЧТОБЫ БРОСИТЬ КУБИК",
                screenW * 0.5f, footerHeight * 0.62f, screenW * 0.94f,
                uiScale * MathUtils.clamp(screenW / 330f, 0.55f, 0.78f));
        drawCenteredFit("МОНЕТЫ: " + NumberFormatter.compact(economy.coins())
                        + "  ·  ВЕРХНЯЯ ПАНЕЛЬ МЕНЯЕТ НАПРАВЛЕНИЕ",
                screenW * 0.5f, footerHeight * 0.28f, screenW * 0.94f,
                uiScale * MathUtils.clamp(screenW / 330f, 0.50f, 0.72f));
        // Center each value in its cube, with a scale that remains readable for large numbers.
        for (BoardEngine.Cube cube : engine.cubes()) {
            String value = NumberFormatter.compact(cube.value);
            float baseScale = uiScale * MathUtils.clamp(cell / 42f, 0.95f, 1.50f);
            font.getData().setScale(baseScale);
            textLayout.setText(font, value);
            float maxLabelWidth = cell * 0.76f;
            if (textLayout.width > maxLabelWidth && textLayout.width > 0f) {
                baseScale *= maxLabelWidth / textLayout.width;
                font.getData().setScale(baseScale);
            }
            font.setColor(1f, 1f, 1f, 1f);
            float centerX = boardLeft + (cube.lane + 0.5f) * cell;
            float centerY = boardBottom + (BoardEngine.ROWS - 0.5f - cube.visualRow) * cell;
            textLayout.setText(font, value);
            drawCentered(value, centerX, centerY + font.getCapHeight() * 0.35f);
        }
        batch.end();
        if (levelUpTime > 0f && !menuVisible && !paused) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.08f, 0.38f, 0.48f, 0.94f);
            float bannerW = screenW * 0.72f;
            shapes.rect((screenW - bannerW) * 0.5f, boardTop - cell * 0.68f,
                    bannerW, cell * 0.52f);
            shapes.end();
            batch.begin();
            font.getData().setScale(uiScale * MathUtils.clamp(screenW / 360f, 0.85f, 1.05f));
            font.setColor(1f, 1f, 1f, 1f);
            drawCentered("НОВАЯ ЦЕЛЬ: " + NumberFormatter.compact(cubeQueue.goalTarget()),
                    screenW * 0.5f, boardTop - cell * 0.34f);
            batch.end();
        }
    }

    private void drawCentered(String value, float centerX, float baselineY) {
        textLayout.setText(font, value);
        font.draw(batch, value, centerX - textLayout.width * 0.5f, baselineY);
    }

    private void drawCenteredFit(String value, float centerX, float baselineY,
                                 float maxWidth, float preferredScale) {
        font.getData().setScale(preferredScale);
        textLayout.setText(font, value);
        if (textLayout.width > maxWidth && textLayout.width > 0f) {
            font.getData().setScale(preferredScale * maxWidth / textLayout.width);
        }
        drawCentered(value, centerX, baselineY);
    }



    private void startMode(GameMode mode, boolean resetProgress) {
        gameMode = mode;
        timeLeft = 90f;
        menuVisible = false;
        modeSelectionVisible = false;
        settingsVisible = false;
        paused = false;
        startNewRun(resetProgress);
    }

    private void drawMainMenu() {
        drawMenuBackdrop();
        batch.begin();
        font.setColor(0.35f, 0.82f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 210f, 1.45f, 2.0f));
        drawCentered("СЛИЯНИЕ", screenW * 0.5f, screenH * 0.78f);
        font.setColor(0.72f, 0.82f, 0.92f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.78f, 1.0f));
        drawCenteredFit("СОБИРАЙ КУБИКИ. ДОСТИГАЙ НОВЫХ ЦЕЛЕЙ.",
                screenW * 0.5f, screenH * 0.68f, screenW * 0.80f,
                uiScale * MathUtils.clamp(screenW / 390f, 0.62f, 0.82f));
        batch.end();

        if (hasSavedGame) drawMenuButton("ПРОДОЛЖИТЬ", screenH * 0.535f, 0.12f, true);
        drawMenuButton("НОВАЯ ИГРА", screenH * 0.385f, 0.13f, true);
        drawMenuButton("НАСТРОЙКИ", screenH * 0.23f, 0.11f, false);
        drawMenuButton("МАГАЗИН · " + NumberFormatter.compact(economy.coins()) + " МОНЕТ",
                screenH * 0.105f, 0.09f, false);

        batch.begin();
        font.setColor(0.60f, 0.70f, 0.82f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 420f, 0.75f, 0.92f));
        drawCentered("РЕКОРД: " + NumberFormatter.compact(bestScore),
                screenW * 0.5f, screenH * 0.035f);
        batch.end();
    }

    private void drawModeMenu() {
        drawMenuBackdrop();
        batch.begin();
        font.setColor(0.35f, 0.82f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 240f, 1.25f, 1.65f));
        drawCentered("НОВАЯ ИГРА", screenW * 0.5f, screenH * 0.79f);
        font.setColor(1f, 1f, 1f, 1f);
        batch.end();

        drawMenuButton("КЛАССИЧЕСКИЙ", screenH * 0.595f, 0.14f, true);
        drawMenuButton("НА ВРЕМЯ · 90 СЕКУНД", screenH * 0.425f, 0.14f, true);
        drawMenuButton("ИСПЫТАНИЕ", screenH * 0.255f, 0.14f, true);
        drawMenuButton("НАЗАД", screenH * 0.105f, 0.10f, false);
    }

    private void drawShopMenu() {
        drawMenuBackdrop();
        batch.begin();
        font.setColor(0.35f, 0.82f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 240f, 1.25f, 1.65f));
        drawCentered("МАГАЗИН", screenW * 0.5f, screenH * 0.79f);
        font.setColor(1f, 0.82f, 0.38f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 330f, 0.95f, 1.2f));
        drawCentered("МОНЕТЫ: " + NumberFormatter.compact(economy.coins()),
                screenW * 0.5f, screenH * 0.65f);
        font.setColor(0.72f, 0.82f, 0.92f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.75f, 0.92f));
        drawCenteredFit("ЗАРЯДЫ ПОМОГУТ СПАСТИ ПОЛЕ, КОГДА НЕ ОСТАНЕТСЯ ХОДОВ.",
                screenW * 0.5f, screenH * 0.56f, screenW * 0.82f,
                uiScale * MathUtils.clamp(screenW / 390f, 0.62f, 0.82f));
        batch.end();
        drawMenuButton("КУПИТЬ 3 ЗАРЯДА · 35 МОНЕТ",
                screenH * 0.43f, 0.15f, economy.coins() >= GameEconomy.SPLIT_CHARGE_PACK_PRICE);
        drawMenuButton("НАЗАД", screenH * 0.20f, 0.11f, false);
    }

    private void drawSettingsMenu() {
        drawMenuBackdrop();
        batch.begin();
        font.setColor(0.35f, 0.82f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 240f, 1.25f, 1.65f));
        drawCentered("НАСТРОЙКИ", screenW * 0.5f, screenH * 0.79f);
        font.setColor(0.72f, 0.82f, 0.92f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.78f, 0.95f));
        drawCenteredFit("ТАКТИЛЬНЫЙ ОТКЛИК ПРИ СЛИЯНИИ",
                screenW * 0.5f, screenH * 0.66f, screenW * 0.82f,
                uiScale * MathUtils.clamp(screenW / 390f, 0.65f, 0.86f));
        batch.end();
        drawMenuButton("ВИБРАЦИЯ: " + (hapticsEnabled ? "ВКЛ" : "ВЫКЛ"),
                screenH * 0.51f, 0.14f, hapticsEnabled);
        drawMenuButton("НАЗАД", screenH * 0.25f, 0.11f, false);
    }

    private void drawMenuBackdrop() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.018f, 0.030f, 0.050f, 0.97f);
        shapes.rect(0, 0, screenW, screenH);
        shapes.setColor(0.055f, 0.095f, 0.145f, 1f);
        shapes.rect(screenW * 0.07f, screenH * 0.12f,
                screenW * 0.86f, screenH * 0.76f);
        shapes.end();
    }

    private void drawMenuButton(String label, float centerY, float heightRatio, boolean highlighted) {
        float width = screenW * 0.76f;
        float height = screenH * heightRatio;
        float left = (screenW - width) * 0.5f;
        float bottom = centerY - height * 0.5f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(highlighted ? 0.10f : 0.075f,
                highlighted ? 0.30f : 0.12f,
                highlighted ? 0.43f : 0.18f, 1f);
        shapes.rect(left, bottom, width, height);
        shapes.end();
        batch.begin();
        font.setColor(1f, 1f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.82f, 1.08f));
        drawCenteredFit(label, screenW * 0.5f, centerY - font.getCapHeight() * 0.35f,
                width * 0.92f, uiScale * MathUtils.clamp(screenW / 390f, 0.72f, 0.92f));
        batch.end();
    }

    private void drawPauseMenu() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.86f);
        shapes.rect(0, 0, screenW, screenH);
        shapes.end();
        batch.begin();
        font.setColor(1f, 1f, 1f, 1f);
        font.getData().setScale(uiScale * 1.4f);
        drawCentered("ПАУЗА", screenW * 0.5f, screenH * 0.72f);
        font.getData().setScale(uiScale);
        drawCentered("ПРОДОЛЖИТЬ", screenW * 0.5f, screenH * 0.58f);
        drawCentered("НАЧАТЬ ЗАНОВО", screenW * 0.5f, screenH * 0.42f);
        drawCentered("В ГЛАВНОЕ МЕНЮ", screenW * 0.5f, screenH * 0.25f);
        batch.end();
    }

    private void startNewRun(boolean resetProgress) {
        long savedGoal = resetProgress ? 2048L : cubeQueue.goalTarget();
        engine.clear();
        engine.setDirection(BoardEngine.Direction.UP);
        cubeQueue.reset(savedGoal);
        challengeGoal = cubeQueue.goalTarget();
        timeLeft = 90f;
        splitCharges = 2;
        emergencyDialog = false;
        splitSelecting = false;
        gameOver = false;
        paused = false;
        menuVisible = false;
        modeSelectionVisible = false;
        hasSavedGame = true;
        mergeFlashTime = 0f;
        mergeFlightTime = 0f;
        mergeFlightSources.clear();
        preferences.putInteger("splitCharges", splitCharges);
        preferences.putLong("coins", economy.coins());
        saveGame();
    }

    private void saveGame() {
        if (preferences == null) return;
        String board = engine.saveState();
        if (board == null) return; // Keep the last complete board while animations are in flight.
        preferences.putString("boardV1", board);
        preferences.putLong("queueGoal", cubeQueue.goalTarget());
        preferences.putLong("queueCurrent", cubeQueue.current());
        preferences.putLong("queueUpcoming", cubeQueue.upcoming());
        preferences.putString("gameMode", gameMode.name());
        preferences.putFloat("timeLeft", timeLeft);
        preferences.putBoolean("gameOver", gameOver);
        preferences.putLong("bestScore", bestScore);
        preferences.putLong("coins", economy.coins());
        preferences.flush();
    }

    private void restoreGame() {
        if (preferences == null) return;
        String board = preferences.getString("boardV1", "");
        if (board.isEmpty() || !engine.loadState(board)) return;
        try { gameMode = GameMode.valueOf(preferences.getString("gameMode", "CLASSIC")); }
        catch (IllegalArgumentException ignored) { gameMode = GameMode.CLASSIC; }
        timeLeft = MathUtils.clamp(preferences.getFloat("timeLeft", 90f), 0f, 90f);
        gameOver = preferences.getBoolean("gameOver", false);
        long goal = preferences.getLong("queueGoal", 2048L);
        long current = preferences.getLong("queueCurrent", 2L);
        long upcoming = preferences.getLong("queueUpcoming", 2L);
        splitCharges = MathUtils.clamp(preferences.getInteger("splitCharges", 2), 0, 2);
        if (!cubeQueue.restore(goal, current, upcoming)) {
            engine.clear();
            preferences.remove("boardV1");
            preferences.flush();
            hasSavedGame = false;
            return;
        }
        challengeGoal = cubeQueue.goalTarget();
        if (gameMode == GameMode.TIMED && timeLeft <= 0f) gameOver = true;
        hasSavedGame = true;
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
