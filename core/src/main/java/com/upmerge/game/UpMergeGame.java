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
import com.badlogic.gdx.graphics.GL20;
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
    private UiTheme uiTheme;
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
    private final MergeVfx mergeVfx = new MergeVfx();
    private float levelUpTime;
    private boolean levelUpVisible;
    private long levelUpGoal = 2048L;
    private int mergeFlightTargetLane;
    private int mergeFlightTargetRow;
    private final List<BoardEngine.MergeSource> mergeFlightSources = new ArrayList<>();

    @Override public void create() {
        camera = new OrthographicCamera();
        shapes = new ShapeRenderer();
        // ShapeRenderer does not enable alpha blending like SpriteBatch does.
        // The HTML reference relies on translucent surfaces and subtle grid tints.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch = new SpriteBatch();
        font = createCyrillicFont();
        logoFont = createLogoFont();
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : font.getRegions()) {
            region.getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        }
        preferences = Gdx.app.getPreferences("UpMergeNative");
        int savedTheme = preferences.getInteger("uiTheme", 0);
        // Earlier native previews accidentally defaulted to palette 16
        // (ultraviolet). The HTML reference starts with the Neon palette.
        if (savedTheme == 16) {
            savedTheme = 0;
            preferences.putInteger("uiTheme", savedTheme);
            preferences.flush();
        }
        uiTheme = UiTheme.fromIndex(savedTheme);
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
                "/system/fonts/Roboto-Medium.ttf",
                "/system/fonts/NotoSans-Medium.ttf",
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
        String[] candidates = {
                "/system/fonts/Roboto-Black.ttf",
                "/system/fonts/Roboto-Bold.ttf",
                "/system/fonts/RobotoCondensed-Bold.ttf",
                "/system/fonts/NotoSans-Bold.ttf"
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
                        + "абвгдеёжзийклмнопрстуфхцчшщъыьэюя·—№";
                parameter.incremental = false;
                BitmapFont generated = generator.generateFont(parameter);
                for (com.badlogic.gdx.graphics.g2d.TextureRegion region : generated.getRegions()) {
                    region.getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                }
                return generated;
            } catch (Exception error) {
                Gdx.app.error("UpMergeFont", "Could not load logo font " + path, error);
            } finally {
                if (generator != null) generator.dispose();
            }
        }
        return createCyrillicFont();
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
        mergeVfx.update(dt);
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
            mergeVfx.start(boardLeft + (mergeEvent.lane + .5f) * cell,
                    boardBottom + (BoardEngine.ROWS - .5f - mergeEvent.row) * cell,
                    cell, mergeEvent.value, mergeEvent.count, uiTheme.cubeColor(mergeEvent.value));
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

        ScreenUtils.clear(((uiTheme.background >>> 16) & 255) / 255f,
                ((uiTheme.background >>> 8) & 255) / 255f,
                (uiTheme.background & 255) / 255f, 1f);
        drawGameBackdrop();
        drawPanels();
        drawBoard();
        drawCubes();
        drawMergeFlights();
        drawMergeVfx();
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
                if (settingsVisible) settingsVisible = false;
                else paused = false;
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
                if (y > screenH * 0.62f && y <= screenH * 0.74f) {
                    cycleUiTheme();
                } else if (y > screenH * 0.38f && y <= screenH * 0.54f) {
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
            if (settingsVisible) {
                if (y > screenH * .62f && y <= screenH * .76f) cycleUiTheme();
                else if (y > screenH * .40f && y <= screenH * .56f) {
                    hapticsEnabled = !hapticsEnabled;
                    preferences.putBoolean("hapticsEnabled", hapticsEnabled);
                    preferences.flush();
                } else if (y <= screenH * .30f) settingsVisible = false;
            } else if (y > screenH * .55f) paused = false;
            else if (y > screenH * .43f) settingsVisible = true;
            else if (y > screenH * .29f) startMode(gameMode, false);
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
            beginShapes(ShapeRenderer.ShapeType.Filled);
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
            beginShapes(ShapeRenderer.ShapeType.Filled);
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
            beginShapes(ShapeRenderer.ShapeType.Filled);
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
        beginShapes(ShapeRenderer.ShapeType.Filled);
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

    /** Theme-driven radial backdrop matching the HTML game's 50% / 25% gradient. */
    private void drawGameBackdrop() {
        float centerX = screenW * .5f;
        float centerY = screenH * .75f;
        float radiusX = screenW * .85f;
        float radiusY = screenH * .85f;
        beginShapes(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < 36; i++) {
            float t = i / 35f;
            int color = t < .52f
                    ? UiTheme.mix(uiTheme.background, uiTheme.panel, t / .52f)
                    : UiTheme.mix(uiTheme.panel, uiTheme.surface, (t - .52f) / .48f);
            float scale = 1f - t;
            if (scale <= .001f) continue;
            setShapeHex(color, 1f);
            shapes.ellipse(centerX - radiusX * scale, centerY - radiusY * scale,
                    radiusX * 2f * scale, radiusY * 2f * scale);
        }
        shapes.end();
    }

    private void drawPanels() {
        // The HTML reference keeps the header, arena and footer on one continuous
        // background. Only HUD cards and controls receive their own surfaces.
        beginShapes(ShapeRenderer.ShapeType.Filled);
        float topPad = Math.max(10f, screenW * .04f);
        float statGap = 7f * uiScale;
        float statY = gameLayout.statBottom;
        float statH = gameLayout.statHeight;
        float statW = (screenW - topPad * 2f - statGap * 2f) / 3f;
        for (int i = 0; i < 3; i++) {
            setShapeHex(UiTheme.mix(uiTheme.surface, uiTheme.panel, .45f), 1f);
            roundedRectInBatch(topPad + i * (statW + statGap), statY, statW, statH, 16f * uiScale);
        }
        float pillY = gameLayout.pillBottom;
        float pillGap = 7f * uiScale;
        float pillW = (screenW - topPad * 2f - pillGap) * .5f;
        setShapeHex(uiTheme.panel, 1f);
        roundedRectInBatch(topPad, pillY, pillW, gameLayout.pillHeight, 12f * uiScale);
        setShapeHex(uiTheme.panel, 1f);
        roundedRectInBatch(topPad + pillW + pillGap, pillY, pillW, gameLayout.pillHeight, 12f * uiScale);

        float toolW = gameLayout.toolbarCardWidth;
        float toolGap = gameLayout.toolbarGap;
        float toolLeft = gameLayout.toolbarLeft;
        for (int i = 0; i < 5; i++) {
            boolean selected = (i == 2 && activeTool == Tool.SPLIT)
                    || (i == 3 && activeTool == Tool.MOVE)
                    || (i == 4 && activeTool == Tool.SWAP)
                    || (i == 1 && activeTool == Tool.BOMB);
            boolean available = i == 0 ? undoCharges > 0 && undoBoardState != null
                    : i == 1 ? bombCharges > 0 : i == 2 ? splitCharges > 0
                    : i == 3 ? moveCharges > 0 : swapCharges > 0;
            setShapeHex(selected ? UiTheme.mix(uiTheme.accent, uiTheme.panel, .42f)
                    : available ? UiTheme.mix(uiTheme.surface, uiTheme.panel, .35f) : uiTheme.panel, 1f);
            roundedRectInBatch(toolLeft + i * (toolW + toolGap), gameLayout.toolbarBottom,
                    toolW, gameLayout.toolbarHeight, 13f * uiScale);
        }
        // Queue cards mirror the HTML flex row: label on the left, colored cube on the right.
        float queueY = gameLayout.queueBottom;
        float queueCurrentW = gameLayout.queueCurrentWidth;
        float queueNextW = gameLayout.queueNextWidth;
        float queueH = gameLayout.queueHeight, queueGap = gameLayout.queueGap;
        float queueLeft = gameLayout.queueLeft;
        setShapeHex(uiTheme.panel, 1f);
        roundedRectInBatch(queueLeft, queueY, queueCurrentW, queueH, 12f * uiScale);
        setShapeHex(uiTheme.panel, 1f);
        roundedRectInBatch(queueLeft + queueCurrentW + queueGap, queueY,
                queueNextW, queueH, 12f * uiScale);
        shapes.end();

        // Repaint the HUD surfaces with subtle vertical gradients and crisp edges.
        for (int i = 0; i < 3; i++) {
            float x = topPad + i * (statW + statGap);
            drawRoundedGradientHex(x + 1f, statY + 1f, statW - 2f, statH - 2f,
                    16f * uiScale, UiTheme.mix(uiTheme.surface, 0xffffff, .04f), uiTheme.panel);
            drawRoundedOutlineHex(x + 1f, statY + 1f, statW - 2f, statH - 2f,
                    16f * uiScale, uiTheme.accentSecondary, .16f);
        }
        float[] pillX = {topPad, topPad + pillW + pillGap};
        for (float x : pillX) {
            drawRoundedGradientHex(x + 1f, pillY + 1f, pillW - 2f, gameLayout.pillHeight - 2f,
                    12f * uiScale, UiTheme.mix(uiTheme.surface, 0xffffff, .03f), uiTheme.panel);
            drawRoundedOutlineHex(x + 1f, pillY + 1f, pillW - 2f, gameLayout.pillHeight - 2f,
                    12f * uiScale, uiTheme.accentSecondary, .17f);
        }
        for (int i = 0; i < 5; i++) {
            float x = toolLeft + i * (toolW + toolGap);
            boolean selected = (i == 2 && activeTool == Tool.SPLIT)
                    || (i == 3 && activeTool == Tool.MOVE)
                    || (i == 4 && activeTool == Tool.SWAP)
                    || (i == 1 && activeTool == Tool.BOMB);
            drawRoundedGradientHex(x + 1f, gameLayout.toolbarBottom + 1f, toolW - 2f,
                    gameLayout.toolbarHeight - 2f, 13f * uiScale,
                    selected ? UiTheme.mix(uiTheme.accent, uiTheme.panel, .30f)
                            : UiTheme.mix(uiTheme.surface, uiTheme.panel, .35f), uiTheme.panel);
            drawRoundedOutlineHex(x + 1f, gameLayout.toolbarBottom + 1f, toolW - 2f,
                    gameLayout.toolbarHeight - 2f, 11f * uiScale,
                    selected ? uiTheme.accent : uiTheme.accentSecondary, selected ? .90f : .17f);
        }
        float[] queueWidths = {queueCurrentW, queueNextW};
        float[] queueXs = {queueLeft, queueLeft + queueCurrentW + queueGap};
        for (int i = 0; i < 2; i++) {
            float x = queueXs[i], width = queueWidths[i];
            int queueFill = i == 0
                    ? UiTheme.mix(uiTheme.panel, uiTheme.accent, .07f)
                    : UiTheme.mix(uiTheme.panel, 0xffffff, .04f);
            drawRoundedFillHex(x + 1f, queueY + 1f, width - 2f, queueH - 2f,
                    12f * uiScale, queueFill, 1f);
            drawRoundedOutlineHex(x + 1f, queueY + 1f, width - 2f, queueH - 2f,
                    10f * uiScale, i == 0 ? uiTheme.accent : 0xffffff, i == 0 ? .34f : .07f);
        }

        beginShapes(ShapeRenderer.ShapeType.Filled);
        float cubePreview = Math.min(34f * uiScale,
                Math.min(queueH - 6f * uiScale, queueCurrentW * .37f));
        setCubeFaceColor(cubeQueue.current(), 1f);
        roundedRectInBatch(queueLeft + queueCurrentW - cubePreview - 5f * uiScale,
                queueY + (queueH - cubePreview) * .5f, cubePreview, cubePreview, 6f * uiScale);
        float nextPreview = Math.min(32f * uiScale,
                Math.min(queueH - 7f * uiScale, queueNextW * .30f));
        setCubeFaceColor(cubeQueue.upcoming(), 1f);
        roundedRectInBatch(queueLeft + queueCurrentW + queueGap + queueNextW - nextPreview - 5f * uiScale,
                queueY + (queueH - nextPreview) * .5f, nextPreview, nextPreview, 6f * uiScale);
        shapes.end();
    }

    private void drawBoard() {
        // Port of the HTML canvas: a vertical arena gradient, then a centered
        // 5x7 square-cell board with subtle alternating tint and 1px cell lines.
        float arenaLeft = 10f * uiScale;
        float arenaRight = screenW - arenaLeft;
        float arenaBottom = gameLayout.arenaBottom;
        float arenaTop = gameLayout.arenaTop;
        float arenaHeight = Math.max(1f, arenaTop - arenaBottom);
        beginShapes(ShapeRenderer.ShapeType.Filled);
        for (int band = 0; band < 36; band++) {
            float t = 1f - band / 35f; // HTML canvas is top-to-bottom.
            int color = t < .55f
                    ? UiTheme.mix(uiTheme.panel, uiTheme.background, t / .55f)
                    : UiTheme.mix(uiTheme.surface, uiTheme.panel, (t - .55f) / .45f);
            setShapeHex(color, 1f);
            float y = arenaBottom + arenaHeight * band / 36f;
            shapes.rect(arenaLeft, y, arenaRight - arenaLeft, arenaHeight / 36f + 1f);
        }
        for (int row = 0; row < BoardEngine.ROWS; row++) {
            for (int column = 0; column < BoardEngine.COLUMNS; column++) {
                float alpha = ((row + column) & 1) == 0 ? .075f : .035f;
                setShapeHex(uiTheme.accent, alpha);
                shapes.rect(boardLeft + column * cell,
                        boardBottom + (BoardEngine.ROWS - 1 - row) * cell,
                        cell, cell);
            }
        }
        // HTML renderer lightly marks the column under the launch pointer.
        int activeLane = MathUtils.clamp((int)((Gdx.input.getX() - boardLeft) / cell),
                0, BoardEngine.COLUMNS - 1);
        setShapeHex(uiTheme.accent, .07f);
        shapes.rect(boardLeft + activeLane * cell, boardBottom, cell,
                cell * BoardEngine.ROWS);
        shapes.end();

        beginShapes(ShapeRenderer.ShapeType.Line);
        setShapeHex(uiTheme.accent, .27f);
        for (int row = 0; row < BoardEngine.ROWS; row++) {
            for (int column = 0; column < BoardEngine.COLUMNS; column++) {
                float x = boardLeft + column * cell;
                float y = boardBottom + (BoardEngine.ROWS - 1 - row) * cell;
                shapes.rect(x + .5f, y + .5f, cell - 1f, cell - 1f);
            }
        }
        shapes.end();

        // Only the board's own fine border; no rings, circles, texture marks,
        // or extra lane decorations are drawn over the HTML grid.
        beginShapes(ShapeRenderer.ShapeType.Line);
        setShapeHex(uiTheme.accent, .42f);
        shapes.rect(boardLeft - .5f, boardBottom - .5f,
                cell * BoardEngine.COLUMNS + 1f, cell * BoardEngine.ROWS + 1f);
        shapes.end();
    }

    private void drawCubes() {
        beginShapes(ShapeRenderer.ShapeType.Filled);
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
            int rgb = uiTheme.cubeColor(cube.value);
            float red = ((rgb >>> 16) & 0xff) / 255f;
            float green = ((rgb >>> 8) & 0xff) / 255f;
            float blue = (rgb & 0xff) / 255f;
            // Base theme: deliberately flat solid-color tiles.
            shapes.setColor(red, green, blue, 1f);
            roundedRectInBatch(x, y, size, size, radius);
        }
        shapes.end();

        if (splitSelecting) {
            beginShapes(ShapeRenderer.ShapeType.Line);
            shapes.setColor(.25f, .95f, 1f, 1f);
            for (BoardEngine.Cube cube : engine.cubes()) {
                float x = boardLeft + (cube.lane + .5f) * cell;
                float y = boardBottom + (BoardEngine.ROWS - .5f - cube.visualRow) * cell;
                shapes.rect(x - cell * .46f, y - cell * .46f, cell * .92f, cell * .92f);
            }
            shapes.end();
        }
    }

    private void drawMergeVfx() {
        if (!mergeVfx.isActive()) return;
        float progress = mergeVfx.progress();
        float fade = 1f - progress;
        float cx = mergeVfx.centerX(), cy = mergeVfx.centerY();
        float radius = mergeVfx.cellSize();

        // Expanding impact rings: a bright contact shockwave followed by a softer halo.
        // HTML reference: one colored impact ring and a softer white inner ring.
        beginShapes(ShapeRenderer.ShapeType.Line);
        setShapeRgb(mergeVfx.red(), mergeVfx.green(), mergeVfx.blue(), fade * .72f);
        shapes.circle(cx, cy, radius * (.30f + progress * 1.05f), 48);
        shapes.setColor(1f, 1f, 1f, fade * .42f);
        shapes.circle(cx, cy, radius * (.12f + progress * .72f), 48);
        shapes.end();

        // Radial contact flash, followed by the same glowing round particles as the HTML build.
        beginShapes(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(1f, 1f, 1f, fade * .82f);
        shapes.circle(cx, cy, radius * (.08f + fade * .20f), 32);
        setShapeRgb(mergeVfx.red(), mergeVfx.green(), mergeVfx.blue(), fade * .32f);
        shapes.circle(cx, cy, radius * (.18f + fade * .25f), 32);
        for (int i = 0; i < mergeVfx.particleCount(); i++) {
            float alpha = mergeVfx.particleAlpha(i);
            if (alpha <= 0f) continue;
            float x = mergeVfx.particleX(i), y = mergeVfx.particleY(i);
            float size = mergeVfx.particleSize(i);
            setShapeRgb(mergeVfx.red(), mergeVfx.green(), mergeVfx.blue(), alpha * .22f);
            shapes.circle(x, y, size * 2.1f, 12);
            setShapeRgb(mergeVfx.red(), mergeVfx.green(), mergeVfx.blue(), alpha);
            shapes.circle(x, y, size, 12);
        }
        shapes.end();
    }

    /** Rounded rectangle primitive that can be drawn inside an active ShapeRenderer batch. */
    private void setCubeFaceColor(long value, float alpha) {
        int rgb = uiTheme.cubeColor(value);
        shapes.setColor(((rgb >>> 16) & 0xff) / 255f,
                ((rgb >>> 8) & 0xff) / 255f, (rgb & 0xff) / 255f, alpha);
    }

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
        beginShapes(ShapeRenderer.ShapeType.Filled);
        for (BoardEngine.MergeSource source : mergeFlightSources) {
            if (source.lane == mergeFlightTargetLane && source.row == mergeFlightTargetRow) continue;
            float startX = boardLeft + (source.lane + 0.5f) * cell;
            float startY = boardBottom + (BoardEngine.ROWS - 0.5f - source.row) * cell;
            float x = MathUtils.lerp(startX, targetX, progress);
            float y = MathUtils.lerp(startY, targetY, progress);
            float size = cell * 0.72f * (1f - progress * 0.38f);
            float alpha = 1f - progress * .25f;
            float left = x - size * .5f, bottom = y - size * .5f;
            float radius = Math.max(4f, size * .16f);
            // Merge-flight tiles use the same flat palette as the base theme.
            setCubeFaceColor(source.value, alpha);
            roundedRectInBatch(left, bottom, size, size, radius);
        }
        shapes.end();
    }

    private void drawText() {
        float viewportDp = screenH / uiScale;
        float headerButton = (viewportDp <= 650f ? 36f : 42f) * uiScale;
        float headerCenterY = screenH - gameLayout.headerHeight * .5f;
        float headerButtonY = headerCenterY - headerButton * .5f;
        float headerPad = (viewportDp <= 740f ? 14f : 18f) * uiScale;
        float headerGap = 8f * uiScale;
        float pauseCenterX = screenW - headerPad - headerButton * .5f;
        float soundCenterX = pauseCenterX - headerButton - headerGap;
        drawRoundedFillHex(soundCenterX - headerButton * .5f, headerButtonY,
                headerButton, headerButton, 12f * uiScale, UiTheme.mix(uiTheme.surface, uiTheme.panel, .45f), .92f);
        drawRoundedOutlineHex(soundCenterX - headerButton * .5f, headerButtonY,
                headerButton, headerButton, 12f * uiScale, uiTheme.accentSecondary, .48f);
        drawRoundedFillHex(pauseCenterX - headerButton * .5f, headerButtonY,
                headerButton, headerButton, 12f * uiScale, UiTheme.mix(uiTheme.surface, uiTheme.panel, .45f), .92f);
        drawRoundedOutlineHex(pauseCenterX - headerButton * .5f, headerButtonY,
                headerButton, headerButton, 12f * uiScale, uiTheme.accentSecondary, .48f);
        batch.begin();
        float pad = (viewportDp <= 740f ? 12f : 16f) * uiScale;
        float gap = 7f * uiScale;
        float statW = (screenW - pad * 2f - gap * 2f) / 3f;
        float statY = gameLayout.statBottom, statH = gameLayout.statHeight;

        // Brand row, matching the reference's compact header.
        font.setColor(.91f, .96f, 1f, 1f);
        font.getData().setScale(uiScale * (19f / 24f));
        font.draw(batch, "UP", pad, screenH - (gameLayout.headerHeight * .5f - 7f * uiScale));
        setFontHex(uiTheme.accent);
        font.draw(batch, "MERGE", pad + 24f * uiScale, screenH - (gameLayout.headerHeight * .5f - 7f * uiScale));
        font.setColor(.86f, .93f, 1f, 1f);
        font.getData().setScale(uiScale * .72f);
        textLayout.setText(font, "♫");
        drawCentered("♫", soundCenterX, headerButtonY + headerButton * .5f - textLayout.height * .5f);
        textLayout.setText(font, "Ⅱ");
        drawCentered("Ⅱ", pauseCenterX, headerButtonY + headerButton * .5f - textLayout.height * .5f);

        String[] labels = {"СЧЁТ", "РЕКОРД", "КОМБО"};
        String[] values = {NumberFormatter.compact(engine.score()),
                NumberFormatter.compact(bestScore), "×" + combo};
        for (int i = 0; i < 3; i++) {
            float x = pad + i * (statW + gap);
            font.setColor(.62f, .76f, .89f, 1f);
            font.getData().setScale(uiScale * (10f / 24f));
            drawCentered(labels[i], x + statW * .5f, statY + statH - 15f * uiScale);
            font.setColor(.97f, .99f, 1f, 1f);
            font.getData().setScale(uiScale * (19f / 24f));
            drawCenteredFit(values[i], x + statW * .5f, statY + 8f * uiScale,
                    statW * .86f, uiScale);
        }

        float pillY = gameLayout.pillBottom, pillW = (screenW - pad * 2f - gap) * .5f;
        font.setColor(.58f, .70f, .82f, 1f);
        font.getData().setScale(uiScale * (10f / 24f));
        font.draw(batch, "МОНЕТЫ", pad + 8f * uiScale, pillY + 15f * uiScale);
        font.setColor(.94f, .97f, 1f, 1f);
        font.getData().setScale(uiScale * (16f / 24f));
        drawCenteredFit(NumberFormatter.compact(economy.coins()),
                pad + pillW * .5f, pillY + 2f * uiScale, pillW - 12f * uiScale,
                uiScale * (16f / 24f));
        font.setColor(.58f, .70f, .82f, 1f);
        font.getData().setScale(uiScale * (10f / 24f));
        font.draw(batch, "УРОВЕНЬ", pad + pillW + gap + 8f * uiScale, pillY + 15f * uiScale);
        font.setColor(.94f, .97f, 1f, 1f);
        font.getData().setScale(uiScale * (16f / 24f));
        drawCenteredFit(NumberFormatter.compact(cubeQueue.goalTarget()),
                pad + pillW + gap + pillW * .5f, pillY + 2f * uiScale,
                pillW - 12f * uiScale, uiScale * (16f / 24f));

        // Queue labels sit beside their cube previews, not above them.
        float queueY = gameLayout.queueBottom;
        float queueCurrentW = gameLayout.queueCurrentWidth;
        float queueNextW = gameLayout.queueNextWidth, queueGap = gameLayout.queueGap;
        float queueH = gameLayout.queueHeight;
        float previewW = Math.min(34f * uiScale,
                Math.min(gameLayout.queueHeight - 6f * uiScale, queueCurrentW * .37f));
        float nextW = Math.min(32f * uiScale,
                Math.min(gameLayout.queueHeight - 7f * uiScale, queueNextW * .30f));
        float queueLeft = gameLayout.queueLeft;
        font.setColor(.62f, .72f, .84f, 1f);
        font.getData().setScale(uiScale * (9f / 24f));
        drawCenteredFit("СЕЙЧАС", queueLeft + (queueCurrentW - previewW) * .5f,
                queueY + 13f * uiScale,
                queueCurrentW - previewW - 10f * uiScale, uiScale * (9f / 24f));
        drawCenteredFit("СЛЕДУЮЩИЙ", queueLeft + queueCurrentW + queueGap
                        + (queueNextW - nextW) * .5f, queueY + 13f * uiScale,
                queueNextW - nextW - 10f * uiScale, uiScale * (9f / 24f));
        font.setColor(.035f, .10f, .17f, 1f);
        font.getData().setScale(uiScale * (11f / 24f));
        drawCentered(NumberFormatter.compact(cubeQueue.current()),
                queueLeft + queueCurrentW - 5f * uiScale - previewW * .5f,
                queueY + queueH * .5f - 5f * uiScale);
        drawCentered(NumberFormatter.compact(cubeQueue.upcoming()),
                queueLeft + queueCurrentW + queueGap + queueNextW - 5f * uiScale - nextW * .5f,
                queueY + queueH * .5f - 5f * uiScale);

        // Footer follows the reference order: queue, power tools, instruction.
        String[] toolLabels = {"ОТМЕНА", "БОМБА ×" + bombCharges,
                "УДАЛИТЬ ×" + splitCharges, "ПЕРЕМЕСТИТЬ ×" + moveCharges,
                "ПОМЕНЯТЬ 2 ×" + swapCharges};
        float toolW = gameLayout.toolbarCardWidth;
        float toolGap = gameLayout.toolbarGap;
        float toolLeft = gameLayout.toolbarLeft;
        for (int i = 0; i < toolLabels.length; i++) {
            font.setColor(.88f, .94f, 1f, 1f);
            font.getData().setScale(uiScale * (7f / 24f));
            drawCenteredFit(toolLabels[i], toolLeft + i * (toolW + toolGap) + toolW * .5f,
                    gameLayout.toolbarBottom + 4f * uiScale,
                    toolW - 4f * uiScale, uiScale * (7f / 24f));
        }

        font.setColor(.47f, .56f, .71f, 1f);
        float hintScale = uiScale * (11f / 24f);
        String hint = gameMode == GameMode.TIMED
                ? "ВРЕМЯ " + Math.max(0, (int)Math.ceil(timeLeft)) + " СЕКУНД · МАКСИМУМ ОЧКОВ"
                : gameMode == GameMode.CHALLENGE
                ? "СОБЕРИ КУБИК " + NumberFormatter.compact(challengeGoal)
                : "НАЖМИ НА ПОЛЕ, ЧТОБЫ ЗАПУСТИТЬ КУБИК "
                    + (engine.direction() == BoardEngine.Direction.UP ? "ВВЕРХ" : "ВНИЗ");
        if (activeTool == Tool.SPLIT) hint = "ВЫБЕРИ КУБИК ДЛЯ УДАЛЕНИЯ";
        else if (activeTool == Tool.MOVE) hint = "ВЫБЕРИ КУБИК, ЗАТЕМ МЕСТО ДЛЯ ПЕРЕМЕЩЕНИЯ";
        else if (activeTool == Tool.SWAP) hint = "ВЫБЕРИ ДВА КУБИКА ДЛЯ ОБМЕНА";
        else if (activeTool == Tool.BOMB) hint = "ВЫБЕРИ ЦЕНТР ВЗРЫВА 3×3";
        drawCenteredFit(hint, screenW * .5f,
                gameLayout.hintBottom + gameLayout.hintHeight * .82f,
                screenW * .94f, hintScale);

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
        drawToolIcons();
    }

    /** Crisp vector equivalents of the five compact HTML toolbar glyphs. */
    private void drawToolIcons() {
        float width = gameLayout.toolbarCardWidth;
        float step = width + gameLayout.toolbarGap;
        float left = gameLayout.toolbarLeft;
        float cy = gameLayout.toolbarBottom + gameLayout.toolbarHeight * .66f;
        float s = Math.min(13f * uiScale, gameLayout.toolbarHeight * .34f);
        beginShapes(ShapeRenderer.ShapeType.Line);
        // Undo: curved return arrow.
        setShapeHex(uiTheme.accent, .98f);
        float ux = left + width * .5f;
        shapes.arc(ux, cy, s * .48f, 35f, 285f, 18);
        shapes.line(ux - s * .42f, cy + s * .22f, ux - s * .48f, cy - s * .12f);
        shapes.line(ux - s * .42f, cy + s * .22f, ux - s * .08f, cy + s * .20f);

        // Scissors: two loops and crossed blades.
        float sx = left + 2f * step + width * .5f;
        setShapeHex(uiTheme.accent, .98f);
        shapes.circle(sx - s * .25f, cy - s * .22f, s * .16f, 14);
        shapes.circle(sx + s * .25f, cy - s * .22f, s * .16f, 14);
        shapes.line(sx - s * .14f, cy - s * .10f, sx + s * .43f, cy + s * .40f);
        shapes.line(sx + s * .14f, cy - s * .10f, sx - s * .43f, cy + s * .40f);

        // Move: diagonal arrow.
        float mx = left + 3f * step + width * .5f;
        shapes.line(mx - s * .38f, cy - s * .34f, mx + s * .34f, cy + s * .38f);
        shapes.line(mx + s * .02f, cy + s * .38f, mx + s * .34f, cy + s * .38f);
        shapes.line(mx + s * .34f, cy + s * .38f, mx + s * .34f, cy + s * .06f);

        // Swap: two opposing horizontal arrows.
        float wx = left + 4f * step + width * .5f;
        shapes.line(wx - s * .42f, cy + s * .18f, wx + s * .42f, cy + s * .18f);
        shapes.line(wx + s * .42f, cy + s * .18f, wx + s * .20f, cy + s * .38f);
        shapes.line(wx + s * .42f, cy + s * .18f, wx + s * .20f, cy - s * .02f);
        shapes.line(wx + s * .42f, cy - s * .20f, wx - s * .42f, cy - s * .20f);
        shapes.line(wx - s * .42f, cy - s * .20f, wx - s * .20f, cy + s * .02f);
        shapes.line(wx - s * .42f, cy - s * .20f, wx - s * .20f, cy - s * .40f);
        shapes.end();

        // Bomb uses a warm fuse/body color, like the emoji in the reference.
        float bx = left + step + width * .5f;
        beginShapes(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(1f, .55f, .22f, 1f);
        shapes.circle(bx, cy - s * .04f, s * .31f, 20);
        shapes.setColor(.96f, .76f, .38f, 1f);
        shapes.circle(bx - s * .10f, cy + s * .08f, s * .08f, 12);
        shapes.end();
        beginShapes(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, .70f, .30f, 1f);
        shapes.line(bx + s * .16f, cy + s * .22f, bx + s * .34f, cy + s * .43f);
        shapes.line(bx + s * .34f, cy + s * .43f, bx + s * .46f, cy + s * .36f);
        shapes.end();
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
        float panelTop = mainMenuLayout.panelBottom + mainMenuLayout.panelHeight;
        float verticalPad = Math.max(16f, Math.min(25f, (screenW / uiScale) * .045f)) * uiScale;
        float logoBaseline = panelTop - verticalPad - 39f * uiScale;
        batch.begin();
        float logoScale = uiScale * (30f / 24f);
        logoFont.getData().setScale(logoScale);
        textLayout.setText(logoFont, "UP");
        float upWidth = textLayout.width;
        textLayout.setText(logoFont, "MERGE");
        float mergeWidth = textLayout.width;
        float logoLeft = center - (upWidth + mergeWidth) * .5f;
        // Soft offset shadow keeps the wordmark legible against the navy panel.
        logoFont.setColor(.005f, .018f, .035f, .92f);
        logoFont.draw(batch, "UP", logoLeft + 1.5f * uiScale, logoBaseline - 2f * uiScale);
        logoFont.draw(batch, "MERGE", logoLeft + upWidth + 1.5f * uiScale,
                logoBaseline - 2f * uiScale);
        logoFont.setColor(.93f, .97f, 1f, 1f);
        logoFont.draw(batch, "UP", logoLeft, logoBaseline);
        setLogoFontHex(uiTheme.accent);
        logoFont.draw(batch, "MERGE", logoLeft + upWidth, logoBaseline);
        font.setColor(.66f, .76f, .89f, 1f);
        font.getData().setScale(uiScale * (13f / 24f));
        drawCenteredFit("Запускай кубики вверх. Объединяй одинаковые", center,
                logoBaseline - 50f * uiScale, screenW * .84f, uiScale * (13f / 24f));
        drawCenteredFit("числа и создавай цепные реакции.", center,
                logoBaseline - 70f * uiScale, screenW * .84f, uiScale * (13f / 24f));
        batch.end();

        MainMenuLayout.Rect[] modeCards = mainMenuLayout.modes;
        drawMenuChoice("КЛАССИКА", modeCards[0].centerX(), modeCards[0].centerY(), modeCards[0].width, modeCards[0].height,
                menuSelectedMode == GameMode.CLASSIC);
        drawMenuChoice("НА ВРЕМЯ", modeCards[1].centerX(), modeCards[1].centerY(), modeCards[1].width, modeCards[1].height,
                menuSelectedMode == GameMode.TIMED);
        drawMenuChoice("ВЫЗОВ", modeCards[2].centerX(), modeCards[2].centerY(), modeCards[2].width, modeCards[2].height,
                menuSelectedMode == GameMode.CHALLENGE);

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
            drawMenuButton("ПРОДОЛЖИТЬ ИГРУ", mainMenuLayout.continueButton, true);
            drawMenuButton("НОВАЯ ИГРА", mainMenuLayout.newGameButton, false);
        } else {
            drawMenuButton("НОВАЯ ИГРА", mainMenuLayout.newGameButton, false);
        }

        MainMenuLayout.Rect[][] utilityCards = mainMenuLayout.utilities;
        // Match the HTML grid order exactly: shop/achievements first row,
        // statistics/tasks second row.
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
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 410f, .68f, .82f));
        drawCenteredFit("Цель растёт вдвое после каждого достижения · K / M / B / T",
                center, mainMenuLayout.footer.centerY(), mainMenuLayout.contentWidth * .94f, uiScale * (12f / 24f));
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
        drawMenuChoice(label, centerX, centerY, width, height, selected, 0);
    }

    private void drawMenuChoice(String label, float centerX, float centerY,
                                float width, float height, boolean selected, int icon) {
        float left = centerX - width * .5f, bottom = centerY - height * .5f;
        float radius = Math.min(16f * uiScale, height * .5f);
        drawRoundedFillHex(left, bottom, width, height, radius, uiTheme.panel, 1f);
        if (selected) {
            drawRoundedFillHex(left + 1f, bottom + 1f, width - 2f, height - 2f,
                    radius - 1f, uiTheme.accent, .13f);
            drawRoundedOutlineHex(left + 1f, bottom + 1f, width - 2f, height - 2f,
                    radius - 1f, uiTheme.accent, .62f);
        } else {
            drawRoundedFillHex(left + 1f, bottom + 1f, width - 2f, height - 2f,
                    radius - 1f, UiTheme.mix(uiTheme.panel, 0xffffff, .05f), 1f);
            drawRoundedOutlineHex(left + 1f, bottom + 1f, width - 2f, height - 2f,
                    radius - 1f, 0xffffff, .09f);
        }
        batch.begin();
        font.setColor(selected ? .92f : .86f, selected ? .99f : .92f,
                selected ? 1f : .99f, 1f);
        float scale = uiScale * (11f / 24f);
        font.getData().setScale(scale);
        textLayout.setText(font, label);
        if (textLayout.width > width * .86f && textLayout.width > 0f) {
            font.getData().setScale(scale * width * .86f / textLayout.width);
            textLayout.setText(font, label);
        }
        float labelCenterX = centerX + (icon == 0 ? 0f : height * .13f);
        font.draw(batch, label, labelCenterX - textLayout.width * .5f,
                centerY + textLayout.height * .5f);
        batch.end();
        if (icon != 0) drawMenuIcon(icon, left + height * .52f, centerY, height * .48f);
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

        beginShapes(ShapeRenderer.ShapeType.Filled);
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
        drawCentered("ПОКУПАЙ ЗАРЯДЫ ДЛЯ ИНСТРУМЕНТОВ", screenW * .5f, screenH * .78f);
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
        int i = gameLayout.toolbarIndex(x, y);
        if (i < 0) return;
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
        setFontHex(uiTheme.accent);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 240f, 1.25f, 1.65f));
        drawCentered("ТЕМЫ", screenW * 0.5f, screenH * 0.82f);
        font.setColor(0.72f, 0.82f, 0.92f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 390f, 0.78f, 0.95f));
        drawCenteredFit("ЦВЕТА И ОФОРМЛЕНИЕ ИГРЫ",
                screenW * 0.5f, screenH * 0.72f, screenW * 0.82f,
                uiScale * MathUtils.clamp(screenW / 390f, 0.65f, 0.86f));
        batch.end();
        drawMenuButton("ТЕМА: " + uiTheme.name, screenH * 0.66f, 0.10f, true);
        drawMenuButton("ВИБРАЦИЯ: " + (hapticsEnabled ? "ВКЛ" : "ВЫКЛ"),
                screenH * 0.46f, 0.10f, hapticsEnabled);
        drawMenuButton("НАЗАД", screenH * 0.25f, 0.11f, false);
    }

    private void cycleUiTheme() {
        uiTheme = UiTheme.fromIndex(uiTheme.index + 1);
        preferences.putInteger("uiTheme", uiTheme.index);
        preferences.flush();
    }

    private void setFontHex(int hex) {
        font.setColor(((hex >>> 16) & 255) / 255f,
                ((hex >>> 8) & 255) / 255f, (hex & 255) / 255f, 1f);
    }

    private void setLogoFontHex(int hex) {
        logoFont.setColor(((hex >>> 16) & 255) / 255f,
                ((hex >>> 8) & 255) / 255f, (hex & 255) / 255f, 1f);
    }

    private void setShapeRgb(float red, float green, float blue, float alpha) {
        shapes.setColor(red, green, blue, alpha);
    }

    private void beginShapes(ShapeRenderer.ShapeType type) {
        // SpriteBatch.end() disables GL_BLEND. Re-enable it before every
        // ShapeRenderer pass so translucent HTML-style surfaces stay translucent.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(type);
    }

    private void setShapeHex(int hex, float alpha) {
        shapes.setColor(((hex >>> 16) & 255) / 255f,
                ((hex >>> 8) & 255) / 255f, (hex & 255) / 255f, alpha);
    }

    private void drawRoundedFillHex(float x, float y, float width, float height,
                                    float radius, int hex, float alpha) {
        drawRoundedFill(x, y, width, height, radius,
                ((hex >>> 16) & 255) / 255f, ((hex >>> 8) & 255) / 255f,
                (hex & 255) / 255f, alpha);
    }

    private void drawRoundedOutlineHex(float x, float y, float width, float height,
                                       float radius, int hex, float alpha) {
        drawRoundedOutline(x, y, width, height, radius,
                ((hex >>> 16) & 255) / 255f, ((hex >>> 8) & 255) / 255f,
                (hex & 255) / 255f, alpha);
    }

    private void drawRoundedGradientHex(float x, float y, float width, float height,
                                        float radius, int top, int bottom) {
        drawRoundedGradient(x, y, width, height, radius,
                ((top >>> 16) & 255) / 255f, ((top >>> 8) & 255) / 255f, (top & 255) / 255f,
                ((bottom >>> 16) & 255) / 255f, ((bottom >>> 8) & 255) / 255f,
                (bottom & 255) / 255f);
    }

    private void drawRoundedFill(float x, float y, float width, float height, float radius,
                                 float red, float green, float blue, float alpha) {
        float r = Math.min(radius, Math.min(width, height) * 0.5f);
        beginShapes(ShapeRenderer.ShapeType.Filled);
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
        // Use the same radial background as the HTML body; no decorative
        // circles, rings, textures or extra shapes are present in the reference.
        drawGameBackdrop();

        boolean mainMenu = !settingsVisible && !shopVisible && !achievementsVisible
                && !statsVisible && !tasksVisible && !modeSelectionVisible;
        boolean referenceMenu = mainMenu && mainMenuLayout != null;
        float panelWidth = referenceMenu ? mainMenuLayout.panelWidth
                : Math.min(390f * uiScale, screenW - 44f * uiScale);
        float panelHeight = referenceMenu ? mainMenuLayout.panelHeight
                : Math.min(screenH - 24f * uiScale, screenH * .78f);
        float panelX = referenceMenu ? mainMenuLayout.panelLeft : (screenW - panelWidth) * .5f;
        float panelY = referenceMenu ? mainMenuLayout.panelBottom : (screenH - panelHeight) * .5f;
        float radius = 27f * uiScale;
        drawRoundedFillHex(panelX - 2f * uiScale, panelY - 5f * uiScale,
                panelWidth + 4f * uiScale, panelHeight + 10f * uiScale,
                radius + 2f * uiScale, 0x000000, .34f);
        drawRoundedGradientHex(panelX + 1f * uiScale, panelY + 1f * uiScale,
                panelWidth - 2f * uiScale, panelHeight - 2f * uiScale,
                radius - 1f * uiScale, uiTheme.surface, uiTheme.panel);
        drawRoundedOutlineHex(panelX, panelY, panelWidth, panelHeight, radius,
                0x9cc7ff, .21f);
    }

    /** Small cyan vector icons keep the menu crisp without relying on missing Unicode glyphs. */
    private void drawMenuIcon(int icon, float cx, float cy, float size) {
        float r = Math.max(2f * uiScale, size * .10f);
        setShapeHex(uiTheme.accent, .96f);
        if (icon == 1) {
            beginShapes(ShapeRenderer.ShapeType.Filled);
            shapes.circle(cx - size * .16f, cy + size * .10f, r, 12);
            shapes.circle(cx + size * .12f, cy + size * .18f, r, 12);
            shapes.circle(cx + size * .22f, cy - size * .10f, r, 12);
            shapes.circle(cx - size * .08f, cy - size * .20f, r, 12);
            shapes.circle(cx - size * .28f, cy - size * .04f, r, 12);
            shapes.end();
        } else if (icon == 2) {
            beginShapes(ShapeRenderer.ShapeType.Line);
            shapes.rect(cx - size * .30f, cy - size * .27f, size * .60f, size * .54f);
            shapes.line(cx - size * .18f, cy + size * .37f, cx - size * .18f, cy + size * .19f);
            shapes.line(cx + size * .18f, cy + size * .37f, cx + size * .18f, cy + size * .19f);
            shapes.end();
            beginShapes(ShapeRenderer.ShapeType.Filled);
            for (int row = 0; row < 2; row++)
                for (int col = 0; col < 3; col++)
                    shapes.circle(cx - size * .17f + col * size * .17f,
                            cy + size * .06f - row * size * .19f, r * .48f, 8);
            shapes.end();
        } else if (icon == 3) {
            beginShapes(ShapeRenderer.ShapeType.Filled);
            shapes.rect(cx - size * .30f, cy - size * .28f, size * .13f, size * .34f);
            shapes.rect(cx - size * .06f, cy - size * .28f, size * .13f, size * .56f);
            shapes.rect(cx + size * .18f, cy - size * .28f, size * .13f, size * .78f);
            shapes.end();
        } else if (icon == 4) {
            beginShapes(ShapeRenderer.ShapeType.Line);
            shapes.rect(cx - size * .25f, cy - size * .32f, size * .50f, size * .62f);
            shapes.rect(cx - size * .12f, cy + size * .25f, size * .24f, size * .13f);
            shapes.line(cx - size * .14f, cy + size * .08f, cx + size * .14f, cy + size * .08f);
            shapes.line(cx - size * .14f, cy - size * .06f, cx + size * .14f, cy - size * .06f);
            shapes.line(cx - size * .14f, cy - size * .20f, cx + size * .06f, cy - size * .20f);
            shapes.end();
        } else if (icon == 5) {
            beginShapes(ShapeRenderer.ShapeType.Line);
            shapes.line(cx - size * .34f, cy + size * .18f, cx + size * .30f, cy + size * .18f);
            shapes.line(cx - size * .34f, cy + size * .18f, cx - size * .22f, cy - size * .12f);
            shapes.line(cx - size * .22f, cy - size * .12f, cx + size * .26f, cy - size * .12f);
            shapes.line(cx + size * .26f, cy - size * .12f, cx + size * .34f, cy + size * .18f);
            shapes.line(cx - size * .18f, cy + size * .18f, cx - size * .12f, cy + size * .30f);
            shapes.line(cx + size * .18f, cy + size * .18f, cx + size * .12f, cy + size * .30f);
            shapes.end();
            beginShapes(ShapeRenderer.ShapeType.Filled);
            shapes.circle(cx - size * .18f, cy - size * .25f, r * .72f, 12);
            shapes.circle(cx + size * .20f, cy - size * .25f, r * .72f, 12);
            shapes.end();
        } else if (icon == 6) {
            beginShapes(ShapeRenderer.ShapeType.Line);
            shapes.line(cx - size * .22f, cy + size * .28f, cx + size * .22f, cy + size * .28f);
            shapes.line(cx - size * .22f, cy + size * .28f, cx - size * .15f, cy - size * .02f);
            shapes.line(cx + size * .22f, cy + size * .28f, cx + size * .15f, cy - size * .02f);
            shapes.line(cx - size * .15f, cy - size * .02f, cx + size * .15f, cy - size * .02f);
            shapes.line(cx, cy - size * .02f, cx, cy - size * .18f);
            shapes.line(cx - size * .16f, cy - size * .18f, cx + size * .16f, cy - size * .18f);
            shapes.line(cx - size * .22f, cy + size * .20f, cx - size * .36f, cy + size * .20f);
            shapes.line(cx + size * .22f, cy + size * .20f, cx + size * .36f, cy + size * .20f);
            shapes.end();
        }
    }

    private void drawMenuButton(String label, MainMenuLayout.Rect bounds, boolean highlighted) {
        drawMenuButton(label, bounds.centerY(), bounds.height / screenH, highlighted);
    }

    private void drawMenuButton(String label, float centerY, float heightRatio, boolean highlighted) {
        boolean referenceMenu = menuVisible && !settingsVisible && !shopVisible
                && !achievementsVisible && !statsVisible && !tasksVisible && !modeSelectionVisible;
        float centerX = screenW * .5f;
        float width = mainMenuLayout != null ? mainMenuLayout.contentWidth : screenW * .76f;
        float height = Math.min(screenH * heightRatio, 54f * uiScale);
        float left = centerX - width * .5f;
        float bottom = centerY - height * .5f;
        float radius = Math.min(16f * uiScale, height * .5f);
        if (highlighted) {
            drawRoundedFillHex(left, bottom - 3f * uiScale, width, height + 3f * uiScale,
                    radius, uiTheme.background, .22f);
            drawRoundedGradientHex(left, bottom, width, height, radius,
                    0x69e2ff, 0x6ea8ff);
            drawRoundedOutlineHex(left + 1f, bottom + 1f, width - 2f, height - 2f,
                    radius - 1f, 0xffffff, .18f);
        } else {
            drawRoundedFillHex(left, bottom, width, height, radius,
                    UiTheme.mix(uiTheme.panel, 0xffffff, .05f), 1f);
            drawRoundedOutlineHex(left + 1f, bottom + 1f, width - 2f, height - 2f,
                    radius - 1f, 0xffffff, .12f);
        }
        batch.begin();
        font.setColor(highlighted ? .018f : .91f, highlighted ? .075f : .95f,
                highlighted ? .12f : 1f, 1f);
        float scale = uiScale * (15f / 24f);
        font.getData().setScale(scale);
        textLayout.setText(font, label);
        if (textLayout.width > width * .88f && textLayout.width > 0f) {
            font.getData().setScale(scale * width * .88f / textLayout.width);
            textLayout.setText(font, label);
        }
        font.draw(batch, label, centerX - textLayout.width * .5f,
                centerY + textLayout.height * .5f);
        batch.end();
    }

    private void drawRoundedGradient(float x, float y, float width, float height, float radius,
                                     float r1, float g1, float b1,
                                     float r2, float g2, float b2) {
        float r = Math.max(0f, Math.min(radius, Math.min(width, height) * .5f));
        int strips = 24;
        beginShapes(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < strips; i++) {
            float t = (i + .5f) / strips;
            float sx = x + width * i / strips;
            float sw = width / strips + .75f;
            float centerX = sx + sw * .5f;
            float inset = 0f;
            if (centerX < x + r) {
                float dx = r - (centerX - x);
                inset = r - (float) Math.sqrt(Math.max(0f, r * r - dx * dx));
            } else if (centerX > x + width - r) {
                float dx = r - (x + width - centerX);
                inset = r - (float) Math.sqrt(Math.max(0f, r * r - dx * dx));
            }
            shapes.setColor(MathUtils.lerp(r1, r2, t),
                    MathUtils.lerp(g1, g2, t), MathUtils.lerp(b1, b2, t), 1f);
            shapes.rect(sx, y + inset, sw, height - inset * 2f);
        }
        shapes.end();
    }

    private void drawRoundedOutline(float x, float y, float width, float height, float radius,
                                    float red, float green, float blue, float alpha) {
        float r = Math.max(0f, Math.min(radius, Math.min(width, height) * .5f));
        beginShapes(ShapeRenderer.ShapeType.Line);
        shapes.setColor(red, green, blue, alpha);
        // Draw only the four straight edges and quarter-circle corners.
        // Full circles here caused the visible "target rings" on every card.
        shapes.line(x + r, y, x + width - r, y);
        shapes.line(x + r, y + height, x + width - r, y + height);
        shapes.line(x, y + r, x, y + height - r);
        shapes.line(x + width, y + r, x + width, y + height - r);
        shapes.arc(x + r, y + r, r, 180f, 90f, 8);
        shapes.arc(x + width - r, y + r, r, 270f, 90f, 8);
        shapes.arc(x + width - r, y + height - r, r, 0f, 90f, 8);
        shapes.arc(x + r, y + height - r, r, 90f, 90f, 8);
        shapes.end();
    }

    private void drawPauseMenu() {
        if (settingsVisible) {
            drawSettingsMenu();
            return;
        }
        drawMenuBackdrop();
        batch.begin();
        font.setColor(1f, 1f, 1f, 1f);
        font.getData().setScale(uiScale * MathUtils.clamp(screenW / 240f, 1.25f, 1.65f));
        drawCentered("ПАУЗА", screenW * .5f, screenH * .72f);
        font.setColor(.62f, .70f, .82f, 1f);
        font.getData().setScale(uiScale * (13f / 24f));
        drawCenteredFit("Передохни. Твои кубики никуда не денутся.",
                screenW * .5f, screenH * .665f, screenW * .82f, uiScale * (13f / 24f));
        batch.end();

        float buttonHeight = 48f * uiScale;
        float ratio = buttonHeight / screenH;
        drawMenuButton("ПРОДОЛЖИТЬ", screenH * .57f, ratio, true);
        drawMenuButton("НАСТРОЙКИ", screenH * .44f, ratio, false);
        drawMenuButton("НАЧАТЬ ЗАНОВО", screenH * .31f, ratio, false);
        drawMenuButton("В ГЛАВНОЕ МЕНЮ", screenH * .18f, ratio, false);
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
