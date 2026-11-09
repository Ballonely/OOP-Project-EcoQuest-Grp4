package com.ecoquest.core;

import com.ecoquest.entities.Collectible;
import com.ecoquest.entities.Enemy;
import com.ecoquest.entities.GameObject;
import com.ecoquest.entities.Player;
import com.ecoquest.level1.GamePanel;
import com.ecoquest.ui.MenuManager;
import com.ecoquest.ui.LevelSelectManager;
import com.ecoquest.ui.UIManager;
import com.ecoquest.util.Constants;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Core game controller. Owns the rendering surface, the dedicated
 * fixed-timestep game loop thread (60 FPS), and top-level transitions
 * between menu / playing / paused / game-over states. update() and
 * render() are fully decoupled passes.
 */
public class Game extends JPanel implements Runnable, MenuManager.MenuListener,
        LevelSelectManager.LevelSelectListener {

    private static final int HI_SCORE = 98450;
    private static final String[] LEVEL_NAMES = {
        "Residential", "Public Park", "Town Market", "Coastal Beach", "City Center"
    };

    private Thread gameThread;
    private volatile boolean running = false;

    private final InputHandler input = new InputHandler();
    private final UIManager uiManager = new UIManager();
    private final MenuManager menuManager;
    private final LevelSelectManager levelSelectManager;
    private final Random rng = new Random();

    private GameState state = GameState.MAIN_MENU;

    private Player player;
    private GamePanel level1Game;
    private String currentLevelName;
    private boolean runWon;
    private int resultSelection;
    private int runMoneyEarned;
    private int runLevelBonus;
    private int runCityHealth;
    private String runFailureMessage = "You ran out of health!";
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Collectible> collectibles = new ArrayList<>();
    private double spawnTimer = 0;

    public Game() {
        setPreferredSize(new Dimension(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT));
        setBackground(Constants.COLOR_BG_DARK);
        setFocusable(true);
        addKeyListener(input);
        addMouseListener(input);
        addMouseMotionListener(input);
        menuManager = new MenuManager(this, HI_SCORE);
        levelSelectManager = new LevelSelectManager(this);
    }

    /** Starts the dedicated game-loop thread. Safe to call once. */
    public void start() {
        if (running) return;
        running = true;
        gameThread = new Thread(this, "GameLoopThread");
        gameThread.start();
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        final double nsPerUpdate = 1_000_000_000.0 / Constants.FPS;
        long previousTime = System.nanoTime();
        double lag = 0;

        while (running) {
            long currentTime = System.nanoTime();
            lag += (currentTime - previousTime);
            previousTime = currentTime;

            // Fixed-timestep update: catch up on however many ticks elapsed.
            while (lag >= nsPerUpdate) {
                update(nsPerUpdate / 1_000_000_000.0);
                lag -= nsPerUpdate;
            }

            repaint();

            long frameTime = System.nanoTime() - currentTime;
            long sleepMs = (long) ((nsPerUpdate - frameTime) / 1_000_000.0);
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Update pass
    // ------------------------------------------------------------------
    private void update(double dt) {
        switch (state) {
            case MAIN_MENU -> menuManager.handleInput(input, getWidth());
            case LEVEL_SELECT -> levelSelectManager.handleInput(input, getWidth(), getHeight());
            case PLAYING -> updateGameplay(dt);
            case PAUSED -> updatePaused();
            case GAME_OVER -> updateGameOver();
        }
        input.endFrame();
    }

    private void updatePaused() {
        if (input.isKeyJustPressed(KeyEvent.VK_P)) state = GameState.PLAYING;
        if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) state = GameState.MAIN_MENU;
    }

    private void updateGameOver() {
        int actionCount = runWon ? 3 : 2;
        if (input.isKeyJustPressed(KeyEvent.VK_LEFT) || input.isKeyJustPressed(KeyEvent.VK_UP)) {
            resultSelection = (resultSelection + actionCount - 1) % actionCount;
        } else if (input.isKeyJustPressed(KeyEvent.VK_RIGHT) || input.isKeyJustPressed(KeyEvent.VK_DOWN)) {
            resultSelection = (resultSelection + 1) % actionCount;
        } else if (input.isKeyJustPressed(KeyEvent.VK_R)) {
            beginNewRun(currentLevelName);
        } else if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
            state = GameState.MAIN_MENU;
        } else if (input.isKeyJustPressed(KeyEvent.VK_ENTER) || input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            selectResultAction();
        }
    }

    private void selectResultAction() {
        if (runWon && resultSelection == 0) {
            int currentIndex = levelIndex(currentLevelName);
            if (currentIndex + 1 < LEVEL_NAMES.length) beginNewRun(LEVEL_NAMES[currentIndex + 1]);
            else state = GameState.MAIN_MENU;
            return;
        }

        int retryAction = runWon ? 1 : 0;
        if (resultSelection == retryAction) {
            beginNewRun(currentLevelName);
        } else {
            state = GameState.MAIN_MENU;
        }
    }

    private int levelIndex(String levelName) {
        for (int i = 0; i < LEVEL_NAMES.length; i++) {
            if (LEVEL_NAMES[i].equals(levelName)) return i;
        }
        return 0;
    }

    private void updateGameplay(double dt) {
        if (input.isKeyJustPressed(KeyEvent.VK_P)) {
            state = GameState.PAUSED;
            return;
        }

        if (level1Game != null) {
            level1Game.updateFrame(dt, input);
            if (level1Game.isFinished()) {
                runWon = level1Game.didWin();
                runMoneyEarned = runWon ? Math.max(0, level1Game.getMoney() - 100) : 0;
                runLevelBonus = runWon ? 100 : 0;
                runCityHealth = level1Game.getCityHealth();
                runFailureMessage = level1Game.getFailureMessage();
                resultSelection = 0;
                state = GameState.GAME_OVER;
            }
            return;
        }

        player.update(dt);
        for (Enemy e : enemies) e.update(dt);
        for (Collectible c : collectibles) c.update(dt);

        for (Enemy e : enemies) {
            if (player.intersects(e)) player.damage(10);
        }

        collectibles.removeIf(c -> {
            if (player.intersects(c)) {
                player.collect(c.getPoints(), c.getCleanlinessGain());
                return true;
            }
            return false;
        });

        spawnTimer += dt;
        if (spawnTimer > 2.0 && collectibles.size() < 8) {
            spawnTimer = 0;
            spawnCollectible();
        }

        if (player.isDead()) {
            runWon = false;
            runMoneyEarned = 0;
            runLevelBonus = 0;
            runCityHealth = player.getCleanlinessLevel();
            runFailureMessage = "You ran out of health!";
            resultSelection = 0;
            state = GameState.GAME_OVER;
        } else if (player.getCleanlinessLevel() >= 100) {
            runWon = true;
            runMoneyEarned = 0;
            runLevelBonus = 0;
            runCityHealth = player.getCleanlinessLevel();
            runFailureMessage = "";
            resultSelection = 0;
            state = GameState.GAME_OVER;
        }
    }

    private void spawnCollectible() {
        int x = 20 + rng.nextInt(Math.max(1, getWidth() - 60));
        int y = 90 + rng.nextInt(Math.max(1, getHeight() - 180));
        collectibles.add(new Collectible(x, y));
    }

    private void beginNewRun(String levelName) {
        currentLevelName = levelName;
        runWon = false;
        resultSelection = 0;
        if ("Residential".equals(levelName)) {
            level1Game = new GamePanel();
            state = GameState.PLAYING;
            return;
        }

        level1Game = null;
        player = new Player(getWidth() / 2.0, getHeight() / 2.0, input, getWidth(), getHeight());
        enemies.clear();
        collectibles.clear();
        for (int i = 0; i < 4; i++) {
            enemies.add(new Enemy(rng.nextInt(getWidth()), rng.nextInt(getHeight()), getWidth(), getHeight()));
        }
        for (int i = 0; i < 5; i++) spawnCollectible();
        state = GameState.PLAYING;
    }

    // ------------------------------------------------------------------
    // Render pass
    // ------------------------------------------------------------------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        switch (state) {
            case MAIN_MENU -> menuManager.render(g2, getWidth(), getHeight());
            case LEVEL_SELECT -> levelSelectManager.render(g2, getWidth(), getHeight());
            case PLAYING -> renderGameplay(g2);
            case PAUSED -> {
                renderGameplay(g2);
                uiManager.renderPauseOverlay(g2, getWidth(), getHeight());
            }
            case GAME_OVER -> {
                renderGameplay(g2);
                int finalScore = level1Game != null ? level1Game.getScore() : player.getScore();
                uiManager.renderOutcome(g2, getWidth(), getHeight(), runWon,
                        displayLevelName(currentLevelName), finalScore, runMoneyEarned,
                        runLevelBonus, runCityHealth, runFailureMessage, resultSelection);
            }
        }
    }

    private String displayLevelName(String levelName) {
        int index = levelIndex(levelName);
        return "Lv " + (index + 1) + ": " + (index == 0 ? "Residential Street" : levelName);
    }

    private void renderGameplay(Graphics2D g2) {
        if (level1Game != null) {
            level1Game.renderFrame(g2, getWidth(), getHeight());
            return;
        }

        g2.setPaint(new GradientPaint(0, 0, Constants.COLOR_SKY_BLUE, 0, getHeight(), new Color(120, 200, 120)));
        g2.fillRect(0, 0, getWidth(), getHeight());

        List<GameObject> renderables = new ArrayList<>();
        renderables.addAll(collectibles);
        renderables.addAll(enemies);
        for (GameObject obj : renderables) obj.render(g2);
        player.render(g2);

        uiManager.renderHud(g2, player, getWidth(), currentLevelName);
    }

    // ------------------------------------------------------------------
    // MenuManager.MenuListener callbacks
    // ------------------------------------------------------------------
    @Override
    public void onStartGame() { state = GameState.LEVEL_SELECT; }

    @Override
    public void onBackToMenu() { state = GameState.MAIN_MENU; }

    @Override
    public void onStartLevel(String levelName) { beginNewRun(levelName); }

    @Override
    public void onOpenShop() {
        // Placeholder hook: extend with a ShopManager + ui.ShopScreen following
        // the same isolation pattern as MenuManager/UIManager.
    }

    @Override
    public void onOpenSettings() {
        // Placeholder hook: extend with a SettingsManager + ui.SettingsScreen.
    }

    @Override
    public void onQuit() {
        System.exit(0);
    }
}
