package com.upmerge.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.Texture;
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
    private int splitCharges = 2, moveCharges = 2, swapCharges = 2, bombCharges = 1, undoCharges;
    private GameEconomy economy;
    private boolean shopVisible;
    private boolean achievementsVisible;
    private boolean statsVisible;
    private boolean tasksVisible;
    private long taskDay = -1L, taskProgress, taskScore, taskMerges;
    private boolean taskClaimed;
    private boolean statsRecorded;
    private long lifetimeGames, lifetimeMerges, lifetimeShots, lifetimeBestCombo = 1L, lifetimeBestBlock = 2L;
    private int runMerges, runMaxCombo = 1;
    private long runMaxBlock = 2L;
    private Tool activeTool = Tool.NONE;
    private long selectedToolCubeId = -1L;
    private String undoBoardState;
    private long undoQueueGoal, undoQueueCurrent, undoQueueUpcoming;
    private float undoTimeLeft;
    private enum Tool { NONE, SPLIT, MOVE, SWAP, BOMB }
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
    private GameMode menuSelectedMode = GameMode.CLASSIC;
    private BoardEngine.Direction menuSelectedDirection = BoardEngine.Direction.UP;
    private float timeLeft = 90f;
    private long challengeGoal = 2048;
    private enum GameMode { CLASSIC, TIMED, CHALLENGE }

    private OrthographicCamera camera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private BitmapFont logoFont;

    private float screenW, screenH;
    private MainMenuLayout mainMenuLayout;
    private GameScreenLayout gameLayout;
    private float cell, boardLeft, boardBottom, boardTop;
    private float headerHeight, footerHeight;
    private float uiScale = 1f;
    private float mergeFlashTime;
    private int mergeFlashLane = -1;
    private int mergeFlashRow = -1;
    private long mergeFlashValue;
    private float mergeFlightTime;
    private float levelUpTime;
    private boolean levelUpVisible;
    private long levelUpGoal = 2048L;
    private int mergeFlightTargetLane;
    private int mergeFlightTargetRow;
    private final List<BoardEngine.MergeSource> mergeFlightSources = new ArrayList<>();

    @Override public void create() {
        camera = new OrthographicCamera();
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = createCyrillicFont();
        logoFont = createLogoFont();
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : font.getRegions()) {
            region.getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        }
        preferences = Gdx.app.getPreferences("UpMergeNative");
        bestScore = preferences.getLong("bestScore", 0L);
        lifetimeGames = preferences.getLong("statsGames", 0L);
        lifetimeMerges = preferences.getLong("statsMerges", 0L);
        lifetimeShots = preferences.getLong("statsShots", 0L);
        lifetimeBestCombo = preferences.getLong("statsBestCombo", 1L);
        lifetimeBestBlock = preferences.getLong("statsBestBlock", 2L);
        restoreDailyTask();
        splitCharges = preferences.getInteger("splitCharges", 2);
        moveCharges = preferences.getInteger("moveCharges", 2);
        swapCharges = preferences.getInteger("swapCharges", 2);
        bombCharges = preferences.getInteger("bombCharges", 1);
        undoCharges = preferences.getInteger("undoCharges", 0);
        economy = new GameEconomy(preferences.getLong("coins", 0L));
        hapticsEnabled = preferences.getBoolean("hapticsEnabled", true);
        menuSelectedDirection = preferences.getString("menuDirection", "UP").equals("DOWN")
                ? BoardEngine.Direction.DOWN : BoardEngine.Direction.UP;
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
            } catch (Exception error) {
                Gdx.app.error("UpMergeFont", "Could not load system font " + path, error);
            } finally {
                if (generator != null) generator.dispose();
            }
        }
        return new BitmapFont();
    }

    private BitmapFont createLogoFont() {
        FreeTypeFontGenerator generator = null;
        try {
            generator = new FreeTypeFontGenerator(Gdx.files.absolute("/system/fonts/Roboto-Bold.ttf"));
            FreeTypeFontGenerator.FreeTypeFontParameter parameter =
                    new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = 24;
            parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS
                    + "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"
                    + "абвгдеёжзийклмнопрстуфхцчшщъыьэюя·—№";
            parameter.incremental = false;
            BitmapFont generated = generator.generateFont(parameter);
            for (com.badlogic.gdx.graphics.g2d.TextureRegion region : generated.getRegions()) {
                region.getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            }
            return generated;
        } catch (Exception error) {
            Gdx.app.error("UpMergeFont", "Could not load bold system font", error);
            return createCyrillicFont();
        } finally {
            if (generator != null) generator.dispose();
        }
    }

    @Override public void resize(int width, int height) {
        screenW = Math.max(1, width);
        screenH = Math.max(1, height);
        // libGDX renders in physical pixels on Android; the built-in BitmapFont
        // is small in pixel terms, so scale UI typography with device density.
        uiScale = Math.max(1f, Gdx.graphics.getDensity());
        mainMenuLayout = new MainMenuLayout(screenW, screenH, hasSavedGame, uiScale);
        camera.setToOrtho(false, screenW, screenH);
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        gameLayout = new GameScreenLayout(screenW, screenH, uiScale);
        headerHeight = gameLayout.headerHeight;
        footerHeight = gameLayout.footerHeight;
        cell = gameLayout.cell;
        boardLeft = gameLayout.boardLeft;
        boardBottom = gameLayout.boardBottom;
        boardTop = gameLayout.boardTop;
        font.getData().setScale(uiScale * MathUtils.clamp(cell / 43f, 0.90f, 1.55f));
    }

    @Override public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
        if (!menuVisible && !paused && !gameOver && !emergencyDialog && !levelUpVisible) {
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
            runMerges += Math.max(1, mergeEvent.count - 1);
            lifetimeMerges += Math.max(1, mergeEvent.count - 1);
            updateDailyTaskMerge(Math.max(1, mergeEvent.count - 1), mergeEvent.points);
            lifetimeBestCombo = Math.max(lifetimeBestCombo, mergeEvent.combo);
            lifetimeBestBlock = Math.max(lifetimeBestBlock, mergeEvent.value);
            runMaxCombo = Math.max(runMaxCombo, mergeEvent.combo);
            runMaxBlock = Math.max(runMaxBlock, mergeEvent.value);
            awardAchievements();
            boolean reachedChallenge = gameMode == GameMode.CHALLENGE && mergeEvent.value >= challengeGoal;
            boolean advancedLevel = cubeQueue.onMergedValue(mergeEvent.value);
            if (advancedLevel) {
                levelUpGoal = cubeQueue.goalTarget();
                levelUpVisible = true;
                levelUpTime = 3.2f;
                saveGame();
            }
            if (reachedChallenge) gameOver = true;
            bestScore = Math.max(bestScore, engine.score());
            economy.rewardForMerge(mergeEvent.points);
            preferences.putLong("bestScore", bestScore);
            persistLifetimeStats();
            preferences.putLong("coins", economy.coins());
        preferences.putInteger("splitCharges", splitCharges);
            preferences.flush();
            saveGame();
        }
        if (gameOver && !statsRecorded) recordCompletedRun();
        if (!menuVisible && !paused) checkBoardCapacity();
        handleInput();

        ScreenUtils.clear(0.035f, 0.055f, 0.090f, 1f);
        drawPanels();
        drawBoard();
        drawCubes();
        drawMergeFlights();
        drawText();
        drawOverlays();
        if (menuVisible) {
            if (settingsVisible) drawSettingsMenu();
            else if (shopVisible) drawShopMenu();
            else if (achievementsVisible) drawAchievementsMenu();
            else if (statsVisible) drawStatsMenu();
            else if (tasksVisible) drawTasksMenu();
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
            if (levelUpVisible) {
                levelUpVisible = false;
                levelUpTime = 0f;
                saveGame();
            } else if (menuVisible) {
                if (settingsVisible) settingsVisible = false;
                else if (shopVisible) shopVisible = false;
                else if (achievementsVisible) achievementsVisible = false;
                else if (statsVisible) statsVisible = false;
                else if (tasksVisible) tasksVisible = false;
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

        if (levelUpVisible) {
            levelUpVisible = false;
            levelUpTime = 0f;
            saveGame();
            return;
        }
        if (menuVisible) {
            mainMenuLayout = new MainMenuLayout(screenW, screenH, hasSavedGame, uiScale);
            if (tasksVisible) {
                if (y <= screenH * 0.14f) tasksVisible = false;
                else if (y >= screenH * 0.24f && y <= screenH * 0.33f) claimDailyTask();
                return;
            }
            if (statsVisible) {
                if (y <= screenH * 0.14f) statsVisible = false;
                return;
            }
            if (achievementsVisible) {
                if (y <= screenH * 0.14f) achievementsVisible = false;
                return;
            }
            if (shopVisible) {
                int item = shopItemAt(x, y);
                if (item >= 0) buyToolPack(item);
                else if (y <= screenH * 0.30f) shopVisible = false;
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
            MainMenuLayout.Target target = mainMenuLayout.hit(x, y);
            switch (target) {
                case MODE_CLASSIC: menuSelectedMode = GameMode.CLASSIC; break;
                case MODE_TIMED: menuSelectedMode = GameMode.TIMED; break;
                case MODE_CHALLENGE: menuSelectedMode = GameMode.CHALLENGE; break;
                case DIRECTION_UP:
                    menuSelectedDirection = BoardEngine.Direction.UP;
                    preferences.putString("menuDirection", menuSelectedDirection.name());
                    preferences.flush();
                    break;
                case DIRECTION_DOWN:
                    menuSelectedDirection = BoardEngine.Direction.DOWN;
                    preferences.putString("menuDirection", menuSelectedDirection.name());
                    preferences.flush();
                    break;
                case THEMES: settingsVisible = true; break;
                case DAILY: tasksVisible = true; break;
                case CONTINUE: menuVisible = false; paused = false; break;
                case NEW_GAME: startMode(menuSelectedMode, true); break;
                case SHOP: shopVisible = true; break;
                case ACHIEVEMENTS: achievementsVisible = true; break;
                case STATISTICS: statsVisible = true; break;
                case TASKS: tasksVisible = true; break;
                case NONE: break;
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
        if (gameLayout.toolbarIndex(x, y) >= 0) { handleToolBarInput(x, y); return; }
        if (gameLayout.pauseContains(x, y)) {
            paused = true;
            saveGame();
            return;
        }
        // The score cards and level pills are informational, not gravity controls.
        if (y >= screenH - headerHeight) return;

        if (y >= boardBottom && y <= boardTop) {
            if (activeTool != Tool.NONE) { handleToolBoardInput(x, y); return; }
            int lane = MathUtils.clamp((int) ((x - boardLeft) / cell), 0, BoardEngine.COLUMNS - 1);
            captureUndoSnapshot();
            if (engine.launch(lane, cubeQueue.current())) {
                cubeQueue.advance();
                lifetimeShots++;
                updateDailyTaskShot();
                persistLifetimeStats();
            }
            else undoBoardState = null;
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
        if (levelUpVisible) {
            float pulse = 0.5f + 0.5f * MathUtils.sin((3.2f - levelUpTime) * 5.5f);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.015f, 0.025f, 0.055f, 0.90f);
            shapes.rect(0, 0, screenW, screenH);
            // Layered cyan/gold halos create a celebratory glow without external assets.
            shapes.setColor(0.10f, 0.62f, 0.86f, 0.08f + pulse * 0.06f);
            shapes.circle(screenW * 0.5f, screenH * 0.56f, screenW * 0.48f, 48);
            shapes.setColor(0.98f, 0.72f, 0.24f, 0.10f + pulse * 0.08f);
            shapes.circle(screenW * 0.5f, screenH * 0.56f, screenW * 0.31f, 48);
            shapes.setColor(0.045f, 0.105f, 0.18f, 0.98f);
            shapes.rect(screenW * 0.07f, screenH * 0.34f, screenW * 0.86f, screenH * 0.38f);
            shapes.setColor(0.20f, 0.78f, 0.98f, 0.85f);
            shapes.rect(screenW * 0.10f, screenH * 0.34f, screenW * 0.80f, 2.5f);
            shapes.rect(screenW * 0.10f, screenH * 0.72f, screenW * 0.80f, 2.5f);
            // Small celebratory rays around the new level value.
            shapes.setColor(1f, 0.80f, 0.35f, 0.75f);
            float cx = screenW * 0.5f, cy = screenH * 0.535f;
            for (int i = 0; i < 12; i++) {
                float a = i * MathUtils.PI2 / 12f;
                float r1 = screenW * 0.20f, r2 = r1 + screenW * 0.035f;
                shapes.rectLine(cx + MathUtils.cos(a) * r1, cy + MathUtils.sin(a) * r1,
                        cx + MathUtils.cos(a) * r2, cy + MathUtils.sin(a) * r2, 2.5f);
            }
            shapes.end();
            batch.begin();
            font.setColor(0.48f, 0.88f, 1f, 1f);
            font.getData().setScale(uiScale * MathUtils.clamp(screenW / 330f, 0.95f, 1.25f));
            drawCentered("НОВЫЙ УРОВЕНЬ!", screenW * 0.5f, screenH * 0.665f);
            font.setColor(1f, 0.86f, 0.48f, 1f);
            font.getData().setScale(uiScale * MathUtils.clamp(screenW / 170f, 2.0f, 3.1f));
            drawCentered(NumberFormatter.compact(levelUpGoal), screenW * 0.5f, screenH * 0.535f);
            font.setColor(0.80f, 0.89f, 0.98f, 1f);
            font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.72f, 0.95f));
            drawCentered("ЦЕЛЬ ДОСТИГНУТА · ТЕПЕРЬ СЛЕДУЮЩАЯ", screenW * 0.5f, screenH * 0.435f);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.20f, 0.72f, 0.96f, 1f);
            shapes.rect(screenW * 0.17f, screenH * 0.365f, screenW * 0.66f, screenH * 0.065f);
            shapes.end();
            font.setColor(0.02f, 0.09f, 0.16f, 1f);
            font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.82f, 1.05f));
            drawCentered("ПРОДОЛЖИТЬ", screenW * 0.5f, screenH * 0.388f);
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

        float topPad = Math.max(10f, screenW * .04f);
        float statGap = 7f * uiScale;
        float statY = screenH - 112f * uiScale;
        float statH = 58f * uiScale;
        float statW = (screenW - topPad * 2f - statGap * 2f) / 3f;
        for (int i = 0; i < 3; i++) {
            shapes.setColor(.075f, .105f, .145f, 1f);
            roundedRectInBatch(topPad + i * (statW + statGap), statY, statW, statH, 14f * uiScale);
        }
        float pillY = screenH - 165f * uiScale;
        float pillGap = 7f * uiScale;
        float pillW = (screenW - topPad * 2f - pillGap) * .5f;
        shapes.setColor(.065f, .095f, .14f, 1f);
        roundedRectInBatch(topPad, pillY, pillW, 38f * uiScale, 11f * uiScale);
        shapes.setColor(.065f, .095f, .14f, 1f);
        roundedRectInBatch(topPad + pillW + pillGap, pillY, pillW, 38f * uiScale, 11f * uiScale);

        float toolW = screenW / 5f;
        for (int i = 0; i < 5; i++) {
            boolean selected = (i == 2 && activeTool == Tool.SPLIT)
                    || (i == 3 && activeTool == Tool.MOVE)
                    || (i == 4 && activeTool == Tool.SWAP)
                    || (i == 1 && activeTool == Tool.BOMB);
            boolean available = i == 0 ? undoCharges > 0 && undoBoardState != null
                    : i == 1 ? bombCharges > 0 : i == 2 ? splitCharges > 0
                    : i == 3 ? moveCharges > 0 : swapCharges > 0;
            shapes.setColor(selected ? .12f : available ? .075f : .045f,
                    selected ? .38f : available ? .12f : .065f,
                    selected ? .48f : available ? .17f : .09f, 1f);
            roundedRectInBatch(i * toolW + 2f, 8f, toolW - 4f, 55f * uiScale, 11f * uiScale);
        }
        // Current and upcoming cube previews are grouped above the hint.
        shapes.setColor(.055f, .085f, .12f, 1f);
        roundedRectInBatch(screenW * .5f - 91f * uiScale, footerHeight - 49f * uiScale, 86f * uiScale, 35f * uiScale, 10f * uiScale);
        shapes.setColor(.055f, .085f, .12f, 1f);
        roundedRectInBatch(screenW * .5f + 5f * uiScale, footerHeight - 49f * uiScale, 86f * uiScale, 35f * uiScale, 10f * uiScale);
        shapes.end();
    }

    private void drawBoard() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.035f, .055f, .085f, 1f);
        shapes.rect(boardLeft, boardBottom, cell * BoardEngine.COLUMNS, cell * BoardEngine.ROWS);
        for (int row = 0; row < BoardEngine.ROWS; row++) {
            for (int column = 0; column < BoardEngine.COLUMNS; column++) {
                boolean alternate = ((row + column) & 1) == 0;
                shapes.setColor(alternate ? .055f : .045f,
                        alternate ? .095f : .075f, alternate ? .135f : .11f, 1f);
                shapes.rect(boardLeft + column * cell + .5f,
                        boardBottom + row * cell + .5f, cell - 1f, cell - 1f);
            }
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(.39f, .85f, 1f, .30f);
        shapes.rect(boardLeft, boardBottom, cell * BoardEngine.COLUMNS, cell * BoardEngine.ROWS);
        shapes.end();
    }

    private void drawCubes() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (BoardEngine.Cube cube : engine.cubes()) {
            float size = cell;
            if (cube.mergePending) {
                float progress = MathUtils.clamp(
                        engine.mergeElapsedSeconds() / BoardEngine.MERGE_DELAY_SECONDS, 0f, 1f);
                if (cube.forcedMergeIncoming) size *= Math.max(.04f, 1f - progress * .96f);
                else size *= 1f + (0.5f + 0.5f * MathUtils.sin(engine.mergeElapsedSeconds() * 18f)) * .08f;
            } else if (mergeFlashTime > 0f && cube.value == mergeFlashValue
                    && cube.lane == mergeFlashLane && cube.row == mergeFlashRow) {
                size *= 1f + (mergeFlashTime / .42f) * .10f;
            }
            float x = boardLeft + (cube.lane + .5f) * cell - size * .5f;
            float y = boardBottom + (BoardEngine.ROWS - .5f - cube.visualRow) * cell - size * .5f;
            float radius = Math.max(7f, Math.min(14f, size * .16f));
            float red, green, blue;
            switch ((int) cube.value) {
                case 2: red=.392f; green=.851f; blue=1f; break;       // #64d9ff
                case 4: red=.376f; green=.898f; blue=.769f; break;   // #60e5c4
                case 8: red=.561f; green=.890f; blue=.431f; break;   // #8fe36e
                case 16: red=1f; green=.859f; blue=.412f; break;     // #ffdb69
                case 32: red=1f; green=.608f; blue=.341f; break;     // #ff9b57
                case 64: red=1f; green=.396f; blue=.490f; break;     // #ff657d
                case 128: red=.733f; green=.525f; blue=1f; break;    // #bb86ff
                case 256: red=.510f; green=.573f; blue=1f; break;    // #8292ff
                case 512: red=1f; green=.459f; blue=.784f; break;   // #ff75c8
                case 1024: red=.96f; green=.96f; blue=1f; break;    // #f5f5ff
                default:
                    float hue = ((float) (Math.log(cube.value) / Math.log(2.0)) * .105f) % 1f;
                    red = .45f + .45f * hue; green = .80f - .20f * hue; blue = .95f;
                    break;
            }
            shapes.setColor(.005f, .012f, .025f, .55f);
            roundedRectInBatch(x + 1.5f, y - 3f, size, size, radius);
            shapes.setColor(red * .70f, green * .70f, blue * .72f, 1f);
            roundedRectInBatch(x, y, size, size, radius);
            shapes.setColor(red, green, blue, 1f);
            roundedRectInBatch(x + 1.5f, y + 1.5f, size - 3f, size - 3f, radius - 1.5f);
            // Convex finish used by the original default neon theme.
            shapes.setColor(1f, 1f, 1f, .18f);
            roundedRectInBatch(x + size * .10f, y + size * .82f,
                    size * .80f, Math.max(2f, size * .035f), 2f);
            shapes.setColor(.02f, .07f, .13f, .13f);
            shapes.rect(x + size * .08f, y + size * .07f, size * .84f, Math.max(1f, size * .025f));
        }
        shapes.end();

        if (splitSelecting) {
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(.25f, .95f, 1f, 1f);
            for (BoardEngine.Cube cube : engine.cubes()) {
                float x = boardLeft + (cube.lane + .5f) * cell;
                float y = boardBottom + (BoardEngine.ROWS - .5f - cube.visualRow) * cell;
                shapes.rect(x - cell * .46f, y - cell * .46f, cell * .92f, cell * .92f);
            }
            shapes.end();
        }
        if (mergeFlashTime > 0f && mergeFlashLane >= 0 && mergeFlashRow >= 0) {
            float progress = 1f - mergeFlashTime / .42f;
            float centerX = boardLeft + (mergeFlashLane + .5f) * cell;
            float centerY = boardBottom + (BoardEngine.ROWS - .5f - mergeFlashRow) * cell;
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(.45f, .90f, 1f, 1f - progress);
            shapes.circle(centerX, centerY, cell * (.28f + progress * .48f), 32);
            shapes.end();
        }
    }

    /** Rounded rectangle primitive that can be drawn inside an active ShapeRenderer batch. */
    private void roundedRectInBatch(float x, float y, float width, float height, float radius) {
        float r = Math.max(0f, Math.min(radius, Math.min(width, height) * .5f));
        if (r <= .5f) { shapes.rect(x, y, width, height); return; }
        shapes.rect(x + r, y, width - 2f * r, height);
        shapes.rect(x, y + r, width, height - 2f * r);
        shapes.circle(x + r, y + r, r, 12);
        shapes.circle(x + width - r, y + r, r, 12);
        shapes.circle(x + r, y + height - r, r, 12);
        shapes.circle(x + width - r, y + height - r, r, 12);
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
        float pad = Math.max(10f, screenW * .04f);
        float gap = 7f * uiScale;
        float statW = (screenW - pad * 2f - gap * 2f) / 3f;
        float statY = screenH - 112f * uiScale, statH = 58f * uiScale;

        // Brand row, matching the reference's compact header.
        font.setColor(.91f, .96f, 1f, 1f);
        font.getData().setScale(uiScale * (19f / 24f));
        font.draw(batch, "UP", pad, screenH - 27f * uiScale);
        font.setColor(.42f, .86f, 1f, 1f);
        font.draw(batch, "MERGE", pad + 27f * uiScale, screenH - 27f * uiScale);
        font.setColor(.86f, .93f, 1f, 1f);
        font.getData().setScale(uiScale * .92f);
        drawCentered("Ⅱ", screenW - pad - 12f * uiScale, screenH - 27f * uiScale);

        String[] labels = {"СЧЁТ", "РЕКОРД", "КОМБО"};
        String[] values = {NumberFormatter.compact(engine.score()),
                NumberFormatter.compact(bestScore), "×" + runMaxCombo};
        for (int i = 0; i < 3; i++) {
            float x = pad + i * (statW + gap);
            font.setColor(.58f, .70f, .82f, 1f);
            font.getData().setScale(uiScale * (10f / 24f));
            font.draw(batch, labels[i], x + 8f * uiScale, statY + statH - 10f * uiScale);
            font.setColor(.96f, .98f, 1f, 1f);
            font.getData().setScale(uiScale * (22f / 24f));
            font.draw(batch, values[i], x + 8f * uiScale, statY + 25f * uiScale);
        }

        float pillY = screenH - 165f * uiScale, pillW = (screenW - pad * 2f - gap) * .5f;
        font.setColor(.58f, .70f, .82f, 1f);
        font.getData().setScale(uiScale * (10f / 24f));
        font.draw(batch, "МОНЕТЫ", pad + 8f * uiScale, pillY + 25f * uiScale);
        font.setColor(.94f, .97f, 1f, 1f);
        font.getData().setScale(uiScale * (16f / 24f));
        font.draw(batch, NumberFormatter.compact(economy.coins()), pad + pillW - 42f * uiScale, pillY + 13f * uiScale);
        font.setColor(.58f, .70f, .82f, 1f);
        font.getData().setScale(uiScale * (10f / 24f));
        font.draw(batch, "УРОВЕНЬ", pad + pillW + gap + 8f * uiScale, pillY + 25f * uiScale);
        font.setColor(.45f, .82f, 1f, 1f);
        font.getData().setScale(uiScale * (16f / 24f));
        font.draw(batch, NumberFormatter.compact(cubeQueue.goalTarget()),
                pad + pillW + gap + pillW - 52f * uiScale, pillY + 13f * uiScale);

        // Queue preview, instruction, then the five equal-width power cells.
        float queueY = footerHeight - 25f * uiScale;
        font.setColor(.62f, .72f, .84f, 1f);
        font.getData().setScale(uiScale * .48f);
        drawCentered("СЕЙЧАС", screenW * .5f - 62f * uiScale, queueY + 6f * uiScale);
        drawCentered("СЛЕДУЮЩИЙ", screenW * .5f + 48f * uiScale, queueY + 6f * uiScale);
        font.setColor(.42f, .86f, 1f, 1f);
        font.getData().setScale(uiScale * .95f);
        drawCentered(NumberFormatter.compact(cubeQueue.current()), screenW * .5f - 20f * uiScale, queueY - 12f * uiScale);
        drawCentered(NumberFormatter.compact(cubeQueue.upcoming()), screenW * .5f + 91f * uiScale, queueY - 12f * uiScale);

        font.setColor(.58f, .68f, .78f, 1f);
        font.getData().setScale(uiScale * (11f / 24f));
        drawCenteredFit("НАЖМИ НА СТОЛБЕЦ, ЧТОБЫ БРОСИТЬ КУБИК",
                screenW * .5f, footerHeight - 69f * uiScale, screenW * .94f,
                uiScale * (11f / 24f));
        String[] tools = {"↶ ОТМЕНА", "БОМБА", "✂ УДАЛИТЬ", "↗ ПЕРЕМЕСТИТЬ", "⇄ ОБМЕН"};
        int[] charges = {undoCharges, bombCharges, splitCharges, moveCharges, swapCharges};
        float toolW = screenW / 5f;
        for (int i = 0; i < tools.length; i++) {
            font.setColor(.88f, .94f, 1f, 1f);
            font.getData().setScale(uiScale * (8f / 24f));
            drawCenteredFit(tools[i] + "\n" + charges[i], (i + .5f) * toolW,
                    39f * uiScale, toolW - 4f, uiScale * (8f / 24f));
        }

        for (BoardEngine.Cube cube : engine.cubes()) {
            String value = NumberFormatter.compact(cube.value);
            float fontPixels = cube.value >= 1000 ? cell * .29f : cube.value >= 100 ? cell * .34f : cell * .42f;
            float baseScale = fontPixels / 24f;
            font.getData().setScale(baseScale);
            textLayout.setText(font, value);
            float maxLabelWidth = cell * 0.76f;
            if (textLayout.width > maxLabelWidth && textLayout.width > 0f) {
                baseScale *= maxLabelWidth / textLayout.width;
                font.getData().setScale(baseScale);
            }
            font.setColor(.027f, .09f, .17f, 1f);
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
        float center = screenW * .5f;
        batch.begin();
        float logoScale = uiScale * (38f / 24f);
        logoFont.getData().setScale(logoScale);
        textLayout.setText(logoFont, "UP");
        float upWidth = textLayout.width;
        textLayout.setText(logoFont, "MERGE");
        float mergeWidth = textLayout.width;
        float logoLeft = center - (upWidth + mergeWidth) * .5f;
        logoFont.setColor(.91f, .96f, 1f, 1f);
        logoFont.draw(batch, "UP", logoLeft, screenH * .855f);
        logoFont.setColor(.42f, .86f, 1f, 1f);
        logoFont.draw(batch, "MERGE", logoLeft + upWidth, screenH * .855f);
        font.setColor(.66f, .76f, .89f, 1f);
        font.getData().setScale(uiScale * (13f / 24f));
        drawCentered("ЗАПУСКАЙ КУБИКИ ВВЕРХ.", center, screenH * .79f);
        drawCentered("ОБЪЕДИНЯЙ ОДИНАКОВЫЕ ЧИСЛА", center, screenH * .765f);
        drawCentered("И СОЗДАВАЙ ЦЕПНЫЕ РЕАКЦИИ.", center, screenH * .74f);
        font.setColor(.53f, .67f, .82f, 1f);
        font.getData().setScale(uiScale * (13f / 24f));
        drawCentered("ВЫБЕРИ РЕЖИМ", center, screenH * .69f);
        batch.end();

        MainMenuLayout.Rect[] modeCards = mainMenuLayout.modes;
        drawMenuChoice("КЛАССИКА", modeCards[0].centerX(), modeCards[0].centerY(), modeCards[0].width, modeCards[0].height,
                menuSelectedMode == GameMode.CLASSIC);
        drawMenuChoice("НА ВРЕМЯ", modeCards[1].centerX(), modeCards[1].centerY(), modeCards[1].width, modeCards[1].height,
                menuSelectedMode == GameMode.TIMED);
        drawMenuChoice("ВЫЗОВ", modeCards[2].centerX(), modeCards[2].centerY(), modeCards[2].width, modeCards[2].height,
                menuSelectedMode == GameMode.CHALLENGE);

        batch.begin();
        font.setColor(.53f, .67f, .82f, 1f);
        font.getData().setScale(uiScale * (13f / 24f));
        drawCentered("НАПРАВЛЕНИЕ", center, screenH * .535f);
        batch.end();
        MainMenuLayout.Rect[] directionCards = mainMenuLayout.directions;
        drawMenuChoice("СНИЗУ ВВЕРХ ↑", directionCards[0].centerX(), directionCards[0].centerY(), directionCards[0].width, directionCards[0].height,
                menuSelectedDirection == BoardEngine.Direction.UP);
        drawMenuChoice("СВЕРХУ ВНИЗ ↓", directionCards[1].centerX(), directionCards[1].centerY(), directionCards[1].width, directionCards[1].height,
                menuSelectedDirection == BoardEngine.Direction.DOWN);

        MainMenuLayout.Rect[] actionCards = mainMenuLayout.menuActions;
        drawMenuChoice("ТЕМЫ", actionCards[0].centerX(), actionCards[0].centerY(),
                actionCards[0].width, actionCards[0].height, false);
        drawMenuChoice("ЕЖЕДНЕВНО", actionCards[1].centerX(), actionCards[1].centerY(),
                actionCards[1].width, actionCards[1].height, false);

        if (hasSavedGame) {
            drawMenuButton("ПРОДОЛЖИТЬ ИГРУ", mainMenuLayout.continueButton.centerY(), .070f, true);
            drawMenuButton("НОВАЯ ИГРА", mainMenuLayout.newGameButton.centerY(), .075f, false);
        } else {
            drawMenuButton("НОВАЯ ИГРА", mainMenuLayout.newGameButton.centerY(), .075f, false);
        }

        MainMenuLayout.Rect[][] utilityCards = mainMenuLayout.utilities;
        drawMenuChoice("МАГАЗИН · " + NumberFormatter.compact(economy.coins()),
                utilityCards[0][0].centerX(), utilityCards[0][0].centerY(),
                utilityCards[0][0].width, utilityCards[0][0].height, false);
        drawMenuChoice("ДОСТИЖЕНИЯ", utilityCards[0][1].centerX(), utilityCards[0][1].centerY(),
                utilityCards[0][1].width, utilityCards[0][1].height, false);
        drawMenuChoice("СТАТИСТИКА", utilityCards[1][0].centerX(), utilityCards[1][0].centerY(),
                utilityCards[1][0].width, utilityCards[1][0].height, false);
        drawMenuChoice("ЗАДАНИЯ", utilityCards[1][1].centerX(), utilityCards[1][1].centerY(),
                utilityCards[1][1].width, utilityCards[1][1].height, false);

        batch.begin();
        font.setColor(.58f, .69f, .82f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 450f, .60f, .76f));
        drawCenteredFit("Цель растёт вдвое после каждого достижения · K / M / B / T",
                center, screenH * (screenH / uiScale < 680f ? .035f : .075f), mainMenuLayout.contentWidth * .92f, uiScale * (11f / 24f));
        batch.end();
    }

    private int menuModeAt(float x) {
        float center = screenW * .5f, gap = screenW * .025f, cardW = screenW * .255f;
        if (x < center - cardW * .5f - gap) return 0;
        if (x > center + cardW * .5f + gap) return 2;
        return 1;
    }

    private int menuUtilityAt(float x, float y) {
        return x < screenW * .5f ? 0 : 1;
    }

    private void drawMenuChoice(String label, float centerX, float centerY,
                                float width, float height, boolean selected) {
        float left = centerX - width * .5f, bottom = centerY - height * .5f;
        float radius = Math.min(12f, height * .30f);
        drawRoundedFill(left, bottom, width, height, radius,
                .055f, .085f, .13f, 1f);
        if (selected) {
            drawRoundedFill(left + 1f, bottom + 1f, width - 2f, height - 2f,
                    Math.max(4f, radius - 1f), .10f, .32f, .43f, 1f);
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(.36f, .82f, .98f, .92f);
            shapes.rect(left + 1f, bottom + 1f, width - 2f, height - 2f);
            shapes.end();
        } else {
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(.45f, .62f, .78f, .22f);
            shapes.rect(left + 1f, bottom + 1f, width - 2f, height - 2f);
            shapes.end();
        }
        batch.begin();
        font.setColor(selected ? .48f : .88f, selected ? .88f : .93f,
                selected ? 1f : .98f, 1f);
        float scale = uiScale * (11.5f / 24f);
        font.getData().setScale(scale);
        textLayout.setText(font, label);
        if (textLayout.width > width * .86f && textLayout.width > 0f) {
            font.getData().setScale(scale * width * .86f / textLayout.width);
            textLayout.setText(font, label);
        }
        font.draw(batch, label, centerX - textLayout.width * .5f,
                centerY + textLayout.height * .5f);
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

    private static final String[] ACHIEVEMENT_KEYS = {
            "first_merge", "combo3", "score1000", "block128", "merge10", "score5000"
    };
    private static final String[] ACHIEVEMENT_NAMES = {
            "ПЕРВОЕ СЛИЯНИЕ", "КОМБО ×3", "ТЫСЯЧА ОЧКОВ",
            "БОЛЬШОЙ КУБ 128", "МАСТЕР СЛИЯНИЯ", "ПЯТЬ ТЫСЯЧ"
    };
    private static final String[] ACHIEVEMENT_DETAILS = {
            "Объедини любые два кубика · 20 монет",
            "Добейся комбо ×3 · 35 монет",
            "Набери 1 000 очков за партию · 40 монет",
            "Создай кубик 128 или больше · 60 монет",
            "Сделай 10 объединений за партию · 75 монет",
            "Набери 5 000 очков за партию · 100 монет"
    };
    private static final long[] ACHIEVEMENT_REWARDS = {20, 35, 40, 60, 75, 100};

    private void awardAchievements() {
        boolean changed = false;
        for (int i = 0; i < ACHIEVEMENT_KEYS.length; i++) {
            String key = "achievement_" + ACHIEVEMENT_KEYS[i];
            if (preferences.getBoolean(key, false)) continue;
            boolean earned = i == 0 ? runMerges >= 1 : i == 1 ? runMaxCombo >= 3
                    : i == 2 ? engine.score() >= 1000 : i == 3 ? runMaxBlock >= 128
                    : i == 4 ? runMerges >= 10 : engine.score() >= 5000;
            if (earned) {
                preferences.putBoolean(key, true);
                economy.grantCoins(ACHIEVEMENT_REWARDS[i]);
                changed = true;
            }
        }
        if (changed) persistToolBalances();
    }

    private void persistLifetimeStats() {
        preferences.putLong("statsGames", lifetimeGames);
        preferences.putLong("statsMerges", lifetimeMerges);
        preferences.putLong("statsShots", lifetimeShots);
        preferences.putLong("statsBestCombo", lifetimeBestCombo);
        preferences.putLong("statsBestBlock", lifetimeBestBlock);
        preferences.flush();
    }

    private void recordCompletedRun() {
        statsRecorded = true;
        int count = Math.min(6, preferences.getInteger("statsHistoryCount", 0));
        for (int i = Math.min(5, count); i > 0; i--) {
            preferences.putLong("statsHistoryScore" + i,
                    preferences.getLong("statsHistoryScore" + (i - 1), 0L));
        }
        preferences.putLong("statsHistoryScore0", engine.score());
        preferences.putInteger("statsHistoryCount", Math.min(6, count + 1));
        persistLifetimeStats();
    }

    private long currentTaskDay() {
        return System.currentTimeMillis() / 86400000L;
    }

    private void restoreDailyTask() {
        taskDay = preferences.getLong("taskDay", -1L);
        if (taskDay != currentTaskDay()) {
            taskDay = currentTaskDay();
            taskProgress = taskScore = taskMerges = 0L;
            taskClaimed = false;
            persistDailyTask();
        } else {
            taskProgress = preferences.getLong("taskProgress", 0L);
            taskScore = preferences.getLong("taskScore", 0L);
            taskMerges = preferences.getLong("taskMerges", 0L);
            taskClaimed = preferences.getBoolean("taskClaimed", false);
        }
    }

    private void persistDailyTask() {
        preferences.putLong("taskDay", taskDay);
        preferences.putLong("taskProgress", taskProgress);
        preferences.putLong("taskScore", taskScore);
        preferences.putLong("taskMerges", taskMerges);
        preferences.putBoolean("taskClaimed", taskClaimed);
        preferences.flush();
    }

    private int dailyTaskType() {
        return (int) Math.floorMod(taskDay, 3L);
    }

    private long dailyTaskGoal() {
        int type = dailyTaskType();
        return type == 0 ? 10L : type == 1 ? 5L : 2000L;
    }

    private long dailyTaskCurrent() {
        int type = dailyTaskType();
        return type == 0 ? taskProgress : type == 1 ? taskMerges : taskScore;
    }

    private long dailyTaskReward() {
        int type = dailyTaskType();
        return type == 0 ? 35L : type == 1 ? 50L : 60L;
    }

    private void updateDailyTaskShot() {
        if (taskDay != currentTaskDay()) restoreDailyTask();
        taskProgress++;
        persistDailyTask();
    }

    private void updateDailyTaskMerge(long count, long points) {
        if (taskDay != currentTaskDay()) restoreDailyTask();
        taskMerges += count;
        taskScore += Math.max(0L, points);
        persistDailyTask();
    }

    private void claimDailyTask() {
        if (taskClaimed || dailyTaskCurrent() < dailyTaskGoal()) return;
        taskClaimed = true;
        economy.grantCoins(dailyTaskReward());
        persistDailyTask();
        persistToolBalances();
    }

    private void drawTasksMenu() {
        drawMenuBackdrop();
        int type = dailyTaskType();
        String task = type == 0 ? "СДЕЛАЙ 10 ЗАПУСКОВ"
                : type == 1 ? "СЛЕЙ 5 ГРУПП КУБИКОВ" : "НАБЕРИ 2 000 ОЧКОВ";
        long current = dailyTaskCurrent(), goal = dailyTaskGoal();
        float progress = MathUtils.clamp((float) current / goal, 0f, 1f);
        batch.begin();
        font.setColor(.94f, .97f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 245f, 1.25f, 1.65f));
        drawCentered("ЗАДАНИЯ", screenW * .5f, screenH * .76f);
        font.setColor(.66f, .76f, .89f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, .78f, .98f));
        drawCentered("ЕЖЕДНЕВНОЕ ИСПЫТАНИЕ", screenW * .5f, screenH * .66f);
        font.setColor(1f, 1f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 330f, .88f, 1.1f));
        drawCenteredFit(task, screenW * .5f, screenH * .55f, screenW * .86f,
                uiScale * MathUtils.clamp(screenW / 360f, .72f, .92f));
        font.setColor(.48f, .88f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 300f, .95f, 1.2f));
        drawCentered(current + " / " + goal, screenW * .5f, screenH * .46f);
        font.setColor(1f, .82f, .38f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 360f, .78f, .95f));
        drawCentered("НАГРАДА: " + dailyTaskReward() + " МОНЕТ", screenW * .5f, screenH * .40f);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.075f, .12f, .18f, 1f);
        shapes.rect(screenW * .12f, screenH * .35f, screenW * .76f, screenH * .025f);
        shapes.setColor(.30f, .78f, .96f, 1f);
        shapes.rect(screenW * .12f, screenH * .35f, screenW * .76f * progress, screenH * .025f);
        shapes.end();
        drawMenuButton(taskClaimed ? "НАГРАДА ПОЛУЧЕНА"
                : current >= goal ? "ЗАБРАТЬ НАГРАДУ" : "ВЫПОЛНИ ЗАДАНИЕ",
                screenH * .285f, .075f, current >= goal && !taskClaimed);
        drawMenuButton("ЗАКРЫТЬ", screenH * .10f, .07f, false);
    }

    private void drawStatsMenu() {
        drawMenuBackdrop();
        batch.begin();
        font.setColor(0.94f, 0.97f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 245f, 1.25f, 1.65f));
        drawCentered("СТАТИСТИКА", screenW * .5f, screenH * .84f);
        batch.end();

        String[] labels = {"СЫГРАНО ПАРТИЙ", "РЕКОРД", "ВСЕГО СЛИЯНИЙ",
                "ВСЕГО ЗАПУСКОВ", "ЛУЧШЕЕ КОМБО", "МАКСИМАЛЬНЫЙ КУБИК"};
        String[] values = {String.valueOf(lifetimeGames), NumberFormatter.compact(bestScore),
                String.valueOf(lifetimeMerges), String.valueOf(lifetimeShots),
                "×" + lifetimeBestCombo, NumberFormatter.compact(lifetimeBestBlock)};
        float gap = screenH * .012f;
        float cardW = screenW * .40f;
        float cardH = screenH * .105f;
        float left = screenW * .08f;
        float top = screenH * .70f;
        for (int i = 0; i < 6; i++) {
            int col = i % 2, row = i / 2;
            float x = left + col * (cardW + screenW * .04f);
            float y = top - row * (cardH + gap);
            drawRoundedFill(x, y, cardW, cardH, Math.min(15f, cardH * .22f),
                    .075f, .115f, .17f, 1f);
            batch.begin();
            font.setColor(.58f, .68f, .80f, 1f);
            font.getData().setScale(uiScale * MathUtils.clamp(screenW / 520f, .48f, .62f));
            drawCenteredFit(labels[i], x + cardW * .5f, y + cardH * .72f,
                    cardW * .90f, uiScale * MathUtils.clamp(screenW / 520f, .45f, .58f));
            font.setColor(.94f, .97f, 1f, 1f);
            font.getData().setScale(uiScale * MathUtils.clamp(screenW / 300f, .90f, 1.2f));
            drawCentered(values[i], x + cardW * .5f, y + cardH * .25f);
            batch.end();
        }
        batch.begin();
        font.setColor(.86f, .92f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, .72f, .9f));
        drawCentered("ПОСЛЕДНИЕ РЕЗУЛЬТАТЫ", screenW * .5f, screenH * .265f);
        int count = Math.min(6, preferences.getInteger("statsHistoryCount", 0));
        for (int i = 0; i < count; i++) {
            long score = preferences.getLong("statsHistoryScore" + i, 0L);
            drawCentered((i + 1) + ".  " + NumberFormatter.compact(score),
                    screenW * .5f, screenH * (.225f - i * .027f));
        }
        if (count == 0) {
            font.setColor(.58f, .68f, .80f, 1f);
            drawCenteredFit("СЫГРАЙ ПАРТИЮ, ЧТОБЫ ПОЯВИЛАСЬ ИСТОРИЯ",
                    screenW * .5f, screenH * .20f, screenW * .84f,
                    uiScale * MathUtils.clamp(screenW / 500f, .50f, .62f));
        }
        batch.end();
        drawMenuButton("ЗАКРЫТЬ", screenH * .07f, .07f, true);
    }

    private void drawAchievementsMenu() {
        drawMenuBackdrop();
        batch.begin();
        font.setColor(.35f, .82f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 250f, 1.15f, 1.45f));
        drawCentered("ДОСТИЖЕНИЯ", screenW * .5f, screenH * .84f);
        font.setColor(1f, .82f, .38f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 350f, .82f, 1f));
        drawCentered("НАГРАДЫ ЗА ИГРОВЫЕ ЦЕЛИ", screenW * .5f, screenH * .75f);
        batch.end();
        for (int i = 0; i < ACHIEVEMENT_KEYS.length; i++) {
            boolean done = preferences.getBoolean("achievement_" + ACHIEVEMENT_KEYS[i], false);
            batch.begin();
            font.setColor(done ? .35f : .72f, done ? .88f : .82f, done ? .62f : .92f, 1f);
            font.getData().setScale(uiScale * MathUtils.clamp(screenW / 420f, .62f, .78f));
            drawCenteredFit((done ? "✓ " : "◇ ") + ACHIEVEMENT_NAMES[i],
                    screenW * .5f, screenH * (.65f - i * .085f), screenW * .84f,
                    uiScale * MathUtils.clamp(screenW / 460f, .55f, .68f));
            font.setColor(.58f, .68f, .80f, 1f);
            font.getData().setScale(uiScale * MathUtils.clamp(screenW / 500f, .50f, .62f));
            drawCentered(ACHIEVEMENT_DETAILS[i], screenW * .5f, screenH * (.62f - i * .085f));
            batch.end();
        }
        drawMenuButton("НАЗАД", screenH * .08f, .07f, false);
    }

    private void drawShopMenu() {
        drawMenuBackdrop();
        batch.begin();
        font.setColor(.35f, .82f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 240f, 1.2f, 1.55f));
        drawCentered("МАГАЗИН", screenW * .5f, screenH * .84f);
        font.setColor(1f, .82f, .38f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 330f, .9f, 1.1f));
        drawCentered("МОНЕТЫ: " + NumberFormatter.compact(economy.coins()), screenW * .5f, screenH * .75f);
        font.setColor(.72f, .82f, .92f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, .68f, .84f));
        drawCentered("ПОКУПАЙ ЗАРЯДЫ ДЛЯ ИНСТРУМЕНТОВ", screenW * .5f, screenH * .68f);
        batch.end();
        String[] names = {"РАЗБИТЬ", "ПЕРЕМЕСТИТЬ", "ПОМЕНЯТЬ МЕСТАМИ", "БОМБА", "ОТМЕНА"};
        GameEconomy.Tool[] tools = GameEconomy.Tool.values();
        float top = screenH * .61f, gap = screenH * .105f;
        for (int i = 0; i < names.length; i++) {
            drawMenuButton(names[i] + " · " + toolCharges(i) + "  |  +3 · "
                    + tools[i].packPrice + " МОНЕТ", top - i * gap, .082f,
                    economy.coins() >= tools[i].packPrice);
        }
        drawMenuButton("НАЗАД", screenH * .045f, .075f, false);
    }

    private int shopItemAt(float x, float y) {
        float top = screenH * .61f, gap = screenH * .105f;
        for (int i = 0; i < 5; i++) if (Math.abs(y - (top - i * gap)) <= screenH * .041f) return i;
        return -1;
    }
    private int toolCharges(int i) {
        switch (i) { case 0: return splitCharges; case 1: return moveCharges;
            case 2: return swapCharges; case 3: return bombCharges; default: return undoCharges; }
    }
    private void buyToolPack(int i) {
        GameEconomy.Tool[] tools = GameEconomy.Tool.values();
        if (i < 0 || i >= tools.length || !economy.buyPack(tools[i])) return;
        switch (i) { case 0: splitCharges += 3; break; case 1: moveCharges += 3; break;
            case 2: swapCharges += 3; break; case 3: bombCharges += 3; break; default: undoCharges += 3; }
        persistToolBalances();
    }
    private void persistToolBalances() {
        preferences.putLong("coins", economy.coins());
        preferences.putInteger("splitCharges", splitCharges);
        preferences.putInteger("moveCharges", moveCharges);
        preferences.putInteger("swapCharges", swapCharges);
        preferences.putInteger("bombCharges", bombCharges);
        preferences.putInteger("undoCharges", undoCharges);
        preferences.flush();
    }
    private void captureUndoSnapshot() {
        String state = engine.saveState();
        if (state == null) return;
        undoBoardState = state; undoQueueGoal = cubeQueue.goalTarget();
        undoQueueCurrent = cubeQueue.current(); undoQueueUpcoming = cubeQueue.upcoming();
        undoTimeLeft = timeLeft;
    }
    private void handleToolBarInput(float x, float y) {
        int i = MathUtils.clamp((int) (x / (screenW / 5f)), 0, 4);
        if (i == 0) {
            if (undoCharges > 0 && undoBoardState != null
                    && engine.loadState(undoBoardState)
                    && cubeQueue.restore(undoQueueGoal, undoQueueCurrent, undoQueueUpcoming)) {
                undoCharges--; timeLeft = undoTimeLeft; undoBoardState = null; persistToolBalances();
            }
            return;
        }
        Tool[] toolbarTools = {Tool.NONE, Tool.BOMB, Tool.SPLIT, Tool.MOVE, Tool.SWAP};
        if (i == 0 || (i == 1 ? bombCharges : i == 2 ? splitCharges
                : i == 3 ? moveCharges : swapCharges) <= 0) return;
        activeTool = toolbarTools[i];
        selectedToolCubeId = -1L;
    }
    private void handleToolBoardInput(float x, float y) {
        BoardEngine.Cube cube = cubeAt(x, y);
        if (activeTool == Tool.SPLIT || activeTool == Tool.BOMB) {
            if (cube == null) return;
            boolean success = activeTool == Tool.SPLIT
                    ? engine.removeCube(cube.id) : engine.bombAt(cube.id) > 0;
            if (success) {
                if (activeTool == Tool.SPLIT) splitCharges--; else bombCharges--;
                activeTool = Tool.NONE; persistToolBalances(); saveGame();
            }
        } else if (activeTool == Tool.SWAP) {
            if (cube == null) return;
            if (selectedToolCubeId < 0L) selectedToolCubeId = cube.id;
            else if (engine.swapCubes(selectedToolCubeId, cube.id)) {
                swapCharges--; activeTool = Tool.NONE; selectedToolCubeId = -1L; persistToolBalances();
            }
        } else if (activeTool == Tool.MOVE) {
            if (selectedToolCubeId < 0L) { if (cube != null) selectedToolCubeId = cube.id; return; }
            if (cube != null) return;
            int lane = MathUtils.clamp((int) ((x - boardLeft) / cell), 0, BoardEngine.COLUMNS - 1);
            int row = MathUtils.clamp(BoardEngine.ROWS - 1 - (int) ((y - boardBottom) / cell), 0, BoardEngine.ROWS - 1);
            if (engine.moveCube(selectedToolCubeId, lane, row)) {
                moveCharges--; activeTool = Tool.NONE; selectedToolCubeId = -1L; persistToolBalances();
            }
        }
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

    private void drawRoundedFill(float x, float y, float width, float height, float radius,
                                 float red, float green, float blue, float alpha) {
        float r = Math.min(radius, Math.min(width, height) * 0.5f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(red, green, blue, alpha);
        shapes.rect(x + r, y, width - 2f * r, height);
        shapes.rect(x, y + r, width, height - 2f * r);
        shapes.circle(x + r, y + r, r, 20);
        shapes.circle(x + width - r, y + r, r, 20);
        shapes.circle(x + r, y + height - r, r, 20);
        shapes.circle(x + width - r, y + height - r, r, 20);
        shapes.end();
    }

    private void drawMenuBackdrop() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.012f, 0.022f, 0.040f, 0.96f);
        shapes.rect(0, 0, screenW, screenH);
        shapes.setColor(0.025f, 0.050f, 0.085f, 1f);
        shapes.rect(0, screenH * .72f, screenW, screenH * .28f);
        shapes.end();
        float panelWidth = Math.min(390f * uiScale, screenW - 44f * uiScale);
        boolean compact = screenH / uiScale < 680f;
        float panelHeight = Math.min(screenH - 24f * uiScale, screenH * (compact ? .97f : .89f));
        float panelX = (screenW - panelWidth) * .5f;
        float panelY = (screenH - panelHeight) * .5f;
        drawRoundedFill(panelX, panelY, panelWidth, panelHeight,
                28f * uiScale, .045f, .085f, .145f, 1f);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(.25f, .55f, .76f, .30f);
        shapes.rect(panelX, panelY, panelWidth, panelHeight);
        shapes.end();
    }

    private void drawMenuButton(String label, float centerY, float heightRatio, boolean highlighted) {
        float width = mainMenuLayout != null ? mainMenuLayout.contentWidth : screenW * 0.76f;
        float height = Math.min(screenH * heightRatio, 48f * uiScale);
        float left = (screenW - width) * 0.5f;
        float bottom = centerY - height * 0.5f;
        float radius = Math.min(16f, height * .30f);
        drawRoundedFill(left, bottom - 2f, width, height, radius, 0f, 0f, 0f, .24f);
        if (highlighted) {
            // CSS reference primary button: cyan-to-blue horizontal gradient.
            int strips = 18;
            for (int i = 0; i < strips; i++) {
                float t = i / (float) (strips - 1);
                float red = MathUtils.lerp(.41f, .43f, t);
                float green = MathUtils.lerp(.88f, .66f, t);
                float blue = MathUtils.lerp(1f, 1f, t);
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                shapes.setColor(red, green, blue, 1f);
                float stripX = left + width * i / strips;
                float stripW = width / strips + 1f;
                float stripCenter = stripX + stripW * .5f;
                float inset = 0f;
                if (stripCenter < left + radius) {
                    float dx = radius - (stripCenter - left);
                    inset = radius - (float) Math.sqrt(Math.max(0f, radius * radius - dx * dx));
                } else if (stripCenter > left + width - radius) {
                    float dx = radius - (left + width - stripCenter);
                    inset = radius - (float) Math.sqrt(Math.max(0f, radius * radius - dx * dx));
                }
                shapes.rect(stripX, bottom + inset, stripW, height - inset * 2f);
                shapes.end();
            }
            // Rounded outer silhouette over the strip gradient.
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(.45f, .84f, 1f, .65f);
            shapes.rect(left + 1f, bottom + 1f, width - 2f, height - 2f);
            shapes.end();
        } else {
            drawRoundedFill(left, bottom, width, height, radius, .065f, .10f, .15f, 1f);
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(.55f, .70f, .86f, .18f);
            shapes.rect(left + 1f, bottom + 1f, width - 2f, height - 2f);
            shapes.end();
        }
        batch.begin();
        font.setColor(highlighted ? .025f : .91f, highlighted ? .09f : .95f,
                highlighted ? .15f : 1f, 1f);
        float scale = uiScale * (15f / 24f);
        font.getData().setScale(scale);
        textLayout.setText(font, label);
        if (textLayout.width > width * .88f && textLayout.width > 0f) {
            font.getData().setScale(scale * width * .88f / textLayout.width);
            textLayout.setText(font, label);
        }
        font.draw(batch, label, screenW * .5f - textLayout.width * .5f,
                centerY + textLayout.height * .5f);
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
        engine.setDirection(menuSelectedDirection);
        cubeQueue.reset(savedGoal);
        challengeGoal = cubeQueue.goalTarget();
        timeLeft = 90f;
        runMerges = 0;
        runMaxCombo = 1;
        statsRecorded = false;
        if (resetProgress) {
            lifetimeGames++;
            persistLifetimeStats();
        }
        runMaxBlock = 2L;
        undoBoardState = null;
        activeTool = Tool.NONE;
        emergencyDialog = false;
        splitSelecting = false;
        gameOver = false;
        paused = false;
        menuVisible = false;
        modeSelectionVisible = false;
        hasSavedGame = true;
        mergeFlashTime = 0f;
        mergeFlightTime = 0f;
        levelUpVisible = false;
        levelUpTime = 0f;
        mergeFlightSources.clear();
        persistToolBalances();
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
        preferences.putInteger("splitCharges", splitCharges);
        preferences.putInteger("moveCharges", moveCharges);
        preferences.putInteger("swapCharges", swapCharges);
        preferences.putInteger("bombCharges", bombCharges);
        preferences.putInteger("undoCharges", undoCharges);
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
        splitCharges = Math.max(0, preferences.getInteger("splitCharges", 2));
        moveCharges = Math.max(0, preferences.getInteger("moveCharges", 2));
        swapCharges = Math.max(0, preferences.getInteger("swapCharges", 2));
        bombCharges = Math.max(0, preferences.getInteger("bombCharges", 1));
        undoCharges = Math.max(0, preferences.getInteger("undoCharges", 0));
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
        if (logoFont != null) logoFont.dispose();
    }
}
