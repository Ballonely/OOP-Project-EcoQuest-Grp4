package com.ecoquest.core;

import com.ecoquest.entities.Collectible;
import com.ecoquest.entities.Enemy;
import com.ecoquest.entities.GameObject;
import com.ecoquest.entities.Player;
import com.ecoquest.data.LevelProgress;
import com.ecoquest.data.ShopInventory;
import com.ecoquest.data.UserDatabase;
import com.ecoquest.data.UserDatabase.Account;
import com.ecoquest.level1.GamePanel;
import com.ecoquest.level1.Level3Market;
import com.ecoquest.level1.Level4Beach;
import com.ecoquest.level1.Level5City;
import com.ecoquest.level2.Level2Park;
import com.ecoquest.ui.MenuManager;
import com.ecoquest.ui.MusicPlayer;
import com.ecoquest.ui.LevelSelectManager;
import com.ecoquest.ui.ShopManager;
import com.ecoquest.ui.UIManager;
import com.ecoquest.util.Constants;

import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.io.IOException;
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
        LevelSelectManager.LevelSelectListener, ShopManager.ShopListener {

    private static final String[] LEVEL_NAMES = {
        "Residential", "Public Park", "Town Market", "Coastal Beach", "City Center"
    };

    private Thread gameThread;
    private volatile boolean running = false;

    private final InputHandler input = new InputHandler();
    private final UIManager uiManager = new UIManager();
    private final MenuManager menuManager;
    private final LevelSelectManager levelSelectManager;
    private final ShopManager shopManager;
    private final Random rng = new Random();
    private final UserDatabase database;
    private Account account;
    private final LevelProgress progress;

    private GameState state = GameState.MAIN_MENU;
    private boolean devBossMode;

    private Player player;
    private GamePanel     level1Game;
    private String currentLevelName;
    private boolean runWon;
    private int resultSelection;
    private int runMoneyEarned;
    private int runLevelBonus;
    private int runCleanliness;
    private int runStartMoney;
    private String runFailureMessage = "You ran out of health!";
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Collectible> collectibles = new ArrayList<>();
    private double spawnTimer = 0;

    public Game(UserDatabase database, Account account) {
        this.database = database;
        this.account = account;
        setPreferredSize(new Dimension(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT));
        setBackground(Constants.COLOR_BG_DARK);
        setFocusable(true);
        addKeyListener(input);
        addMouseListener(input);
        addMouseMotionListener(input);
        menuManager = new MenuManager(this, account.getScore());
        progress = LevelProgress.load(account);
        levelSelectManager = new LevelSelectManager(this, progress);
        shopManager = new ShopManager(this, ShopInventory.load(account));
        shopManager.setMoney(account.getMoney());
    }

    /**
     * Developer shortcut (launch with {@code boss.bat [level]} or {@code --boss [level]}): skips the
     * menus and drops straight into a level's boss fight, with auto-respawn on. Every run in this
     * session (retry included) starts at the boss. {@code levelKey}: 1-5 or street, park, market,
     * beach, city (anything else means Level 1). Call before {@link #start()}.
     */
    public void startDevBossFight(String levelKey) {
        devBossMode = true;
        beginNewRun(devLevelName(levelKey));
    }

    /** Maps a boss.bat level argument to the level's name. */
    public static String devLevelName(String key) {
        if (key == null) return "Residential";
        return switch (key.trim().toLowerCase()) {
            case "2", "park", "publicpark", "public park" -> "Public Park";
            case "3", "market", "townmarket", "town market" -> "Town Market";
            case "4", "beach", "coastal", "coastalbeach", "coastal beach" -> "Coastal Beach";
            case "5", "city", "citycenter", "city center" -> "City Center";
            default -> "Residential";
        };
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
                try {
                    update(nsPerUpdate / 1_000_000_000.0);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                lag -= nsPerUpdate;
            }

            try {
                repaint();
            } catch (Throwable t) {
                t.printStackTrace();
            }

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
        MusicPlayer.play(musicFor(state));
        switch (state) {
            case MAIN_MENU -> menuManager.handleInput(input, viewWidth());
            case LEVEL_SELECT -> levelSelectManager.handleInput(input, viewWidth(), viewHeight());
            case SHOP -> shopManager.handleInput(input, viewWidth(), viewHeight());
            case PLAYING -> updateGameplay(dt);
            case PAUSED -> updatePaused();
            case GAME_OVER -> updateGameOver();
        }
        input.endFrame();
    }

    /** Menu screens get light background music; each level plays its own theme, bosses their own. */
    private MusicPlayer.Track musicFor(GameState state) {
        return switch (state) {
            case MAIN_MENU, LEVEL_SELECT -> MusicPlayer.Track.HOME;
            case SHOP -> MusicPlayer.Track.SHOP;
            case PLAYING, PAUSED -> levelMusic();
            default -> null;
        };
    }

    private MusicPlayer.Track levelMusic() {
        if (level1Game != null && level1Game.isBossMusicActive()) {
            // Level 1's Trash Monster has its own theme; the other bosses share one.
            return "Residential".equals(currentLevelName) ? MusicPlayer.Track.TRASH_MONSTER : MusicPlayer.Track.BOSS;
        }
        if (currentLevelName == null) return null;
        return switch (currentLevelName) {
            case "Public Park" -> MusicPlayer.Track.PARK;
            case "Town Market" -> MusicPlayer.Track.MARKET;
            case "Coastal Beach" -> MusicPlayer.Track.BEACH;
            case "City Center" -> MusicPlayer.Track.CITY;
            default -> MusicPlayer.Track.RESIDENTIAL;
        };
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
            if (level1Game.consumeQuitRequest()) {      // "Main menu" in the in-game menu
                state = GameState.MAIN_MENU;
                return;
            }
            if (level1Game.isFinished()) {
                int endMoney = withRoleCoinBonus(level1Game.getMoney());
                shopManager.setMoney(endMoney);
                persistCompletedRun(level1Game.getScore(), endMoney);
                runWon = level1Game.didWin();
                runMoneyEarned = Math.max(0, endMoney - runStartMoney);   // coins kept from this run
                runLevelBonus = runWon ? level1Game.getLevelBonus() : 0;
                runCleanliness = level1Game.getCleanliness();
                progress.record(levelIndex(currentLevelName), runWon, runCleanliness);
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
            persistCompletedRun(player.getScore(), shopManager.getMoney());
            runWon = false;
            runMoneyEarned = 0;
            runLevelBonus = 0;
            runCleanliness = player.getCleanlinessLevel();
            runFailureMessage = "You ran out of health!";
            resultSelection = 0;
            state = GameState.GAME_OVER;
        } else if (player.getCleanlinessLevel() >= 100) {
            persistCompletedRun(player.getScore(), shopManager.getMoney());
            runWon = true;
            runMoneyEarned = 0;
            runLevelBonus = 0;
            runCleanliness = player.getCleanlinessLevel();
            runFailureMessage = "";
            resultSelection = 0;
            state = GameState.GAME_OVER;
        }
    }

    private void persistCompletedRun(int scoreEarned, int money) {
        if (account.isGuest()) {
            account = account.withGuestProgress(
                    (int) Math.min(Integer.MAX_VALUE, (long) account.getScore() + scoreEarned), money);
            menuManager.setHiScore(account.getScore());
            return;
        }
        try {
            account = database.updateProgress(account.getUsername(), scoreEarned, money);
            menuManager.setHiScore(account.getScore());
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Your run could not be saved. Check that the EcoQuest account file is writable.\n"
                            + e.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE));
        }
    }

    private boolean persistBalance(int money) {
        if (account.isGuest()) {
            account = account.withGuestProgress(account.getScore(), money);
            return true;
        }
        try {
            account = database.updateBalance(account.getUsername(), money);
            return true;
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "The purchase was cancelled because your balance could not be saved.\n"
                            + e.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE));
            return false;
        }
    }

    /** Builds a GamePanel level with shop upgrades and the cleaner's starter-role perks. */
    private GamePanel newLevelPanel(com.ecoquest.level1.Level level) {
        GamePanel panel = new GamePanel(shopManager.getMoney(), shopManager.getCapacityBonus(),
                shopManager.hasRunningShoes(), shopManager.hasGloves(), shopManager, level,
                account.getRole(), true);
        if (account.isDeveloper()) panel.enableDevTools();   // F1 key guide, F8/F9 boss...
        if (devBossMode) panel.enableDevBossMode();          // boss.bat: start every run at the boss
        return panel;
    }

    /** Applies the role coin bonus to coins earned since the run started. */
    private int withRoleCoinBonus(int endMoney) {
        return runStartMoney + account.getRole().applyCoinBonus(endMoney - runStartMoney);
    }

    private void spawnCollectible() {
        int x = 20 + rng.nextInt(Math.max(1, viewWidth() - 60));
        int y = 90 + rng.nextInt(Math.max(1, viewHeight() - 180));
        collectibles.add(new Collectible(x, y));
    }

    private void beginNewRun(String levelName) {
        currentLevelName = levelName;
        runWon = false;
        resultSelection = 0;
        level1Game = null;
        runStartMoney = shopManager.getMoney();

        if ("Residential".equals(levelName)) {
            level1Game = newLevelPanel(null);
            state = GameState.PLAYING;
            return;
        }

        if ("Public Park".equals(levelName)) {
            level1Game = newLevelPanel(new Level2Park());
            state = GameState.PLAYING;
            return;
        }

        if ("Coastal Beach".equals(levelName)) {
            level1Game = newLevelPanel(new Level4Beach());
            state = GameState.PLAYING;
            return;
        }

        if ("City Center".equals(levelName)) {
            level1Game = newLevelPanel(new Level5City());
            state = GameState.PLAYING;
            return;
        }

        if ("Town Market".equals(levelName)) {
            level1Game = newLevelPanel(new Level3Market());
            state = GameState.PLAYING;
            return;
        }

        // Other levels (placeholder generic gameplay)
        player = new Player(viewWidth() / 2.0, viewHeight() / 2.0, input, viewWidth(), viewHeight());
        enemies.clear();
        collectibles.clear();
        for (int i = 0; i < 4; i++) {
            enemies.add(new Enemy(rng.nextInt(viewWidth()), rng.nextInt(viewHeight()), viewWidth(), viewHeight()));
        }
        for (int i = 0; i < 5; i++) spawnCollectible();
        state = GameState.PLAYING;
    }

    // ------------------------------------------------------------------
    // Render pass
    // ------------------------------------------------------------------
    /**
     * Logical canvas width. The canvas is at least WINDOW_WIDTH x WINDOW_HEIGHT and grows along
     * one side to match the window's shape, so a maximized or full-screen window is filled edge to
     * edge (no black bars); every screen lays out against this size, not the window's pixels.
     */
    private int viewWidth() {
        int pw = getWidth(), ph = getHeight();
        if (pw <= 0 || ph <= 0) return Constants.WINDOW_WIDTH;
        return Math.max(Constants.WINDOW_WIDTH, (int) Math.round(Constants.WINDOW_HEIGHT * pw / (double) ph));
    }

    /** Logical canvas height (see {@link #viewWidth()}). */
    private int viewHeight() {
        int pw = getWidth(), ph = getHeight();
        if (pw <= 0 || ph <= 0) return Constants.WINDOW_HEIGHT;
        return Math.max(Constants.WINDOW_HEIGHT, (int) Math.round(Constants.WINDOW_WIDTH * ph / (double) pw));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // Scale the logical canvas to the window. Its shape follows the window's
            // (see viewWidth), so it fills the window with no letterbox bars.
            int panelW = super.getWidth();
            int panelH = super.getHeight();
            double scale = Math.min(panelW / (double) viewWidth(), panelH / (double) viewHeight());
            if (scale <= 0) return;
            double offsetX = (panelW - viewWidth() * scale) / 2.0;
            double offsetY = (panelH - viewHeight() * scale) / 2.0;
            input.setViewTransform(scale, offsetX, offsetY);

            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, panelW, panelH);
            g2.translate(offsetX, offsetY);
            g2.scale(scale, scale);
            g2.clipRect(0, 0, viewWidth(), viewHeight());
            g2.setColor(getBackground());
            g2.fillRect(0, 0, viewWidth(), viewHeight());
            renderScreen(g2);
        } finally {
            g2.dispose();
        }
    }

    private void renderScreen(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        switch (state) {
            case MAIN_MENU -> menuManager.render(g2, viewWidth(), viewHeight());
            case LEVEL_SELECT -> levelSelectManager.render(g2, viewWidth(), viewHeight());
            case SHOP -> shopManager.render(g2, viewWidth(), viewHeight());
            case PLAYING -> renderGameplay(g2);
            case PAUSED -> {
                renderGameplay(g2);
                uiManager.renderPauseOverlay(g2, viewWidth(), viewHeight());
            }
            case GAME_OVER -> {
                renderGameplay(g2);
                int finalScore = level1Game != null ? level1Game.getScore() : player.getScore();
                uiManager.renderOutcome(g2, viewWidth(), viewHeight(), runWon,
                        displayLevelName(currentLevelName), finalScore, runMoneyEarned,
                        runLevelBonus, runCleanliness, runFailureMessage, resultSelection);
            }
        }
    }

    private String displayLevelName(String levelName) {
        int index = levelIndex(levelName);
        return "Lv " + (index + 1) + ": " + (index == 0 ? "Residential Street" : levelName);
    }

    private void renderGameplay(Graphics2D g2) {
        if (level1Game != null) {
            level1Game.renderFrame(g2, viewWidth(), viewHeight());
            return;
        }

        g2.setPaint(new GradientPaint(0, 0, Constants.COLOR_SKY_BLUE, 0, viewHeight(), new Color(120, 200, 120)));
        g2.fillRect(0, 0, viewWidth(), viewHeight());

        List<GameObject> renderables = new ArrayList<>();
        renderables.addAll(collectibles);
        renderables.addAll(enemies);
        for (GameObject obj : renderables) obj.render(g2);
        player.render(g2);

        uiManager.renderHud(g2, player, viewWidth(), currentLevelName);
    }

    // ------------------------------------------------------------------
    // MenuManager.MenuListener callbacks
    // ------------------------------------------------------------------
    @Override
    public void onStartGame() { state = GameState.LEVEL_SELECT; }

    @Override
    public void onBackToMenu() { state = GameState.MAIN_MENU; }

    @Override
    public boolean onMoneyChanged(int money) { return persistBalance(money); }

    @Override
    public void onStartLevel(String levelName) { beginNewRun(levelName); }

    @Override
    public void onOpenShop() {
        state = GameState.SHOP;
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
