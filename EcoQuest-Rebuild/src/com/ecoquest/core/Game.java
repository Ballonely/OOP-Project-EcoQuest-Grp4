package com.ecoquest.core;

import com.ecoquest.entities.Collectible;
import com.ecoquest.entities.Enemy;
import com.ecoquest.entities.GameObject;
import com.ecoquest.entities.Player;
import com.ecoquest.level1.GamePanel;
import com.ecoquest.ui.MenuManager;
import com.ecoquest.ui.LevelSelectManager;
import com.ecoquest.ui.UIManager;
import com.ecoquest.ui.ShopManager;
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
        LevelSelectManager.LevelSelectListener, ShopManager.Listener {

    private static final int HI_SCORE = 98450;
    private static final String[] LEVEL_NAMES = {
        "Residential", "Public Park", "Town Market", "Coastal Beach", "City Center"
    };

    private Thread gameThread;
    private volatile boolean running = false;

    private final InputHandler input = new InputHandler();
    private final UIManager uiManager = new UIManager();
    private final ProgressStore profile = new ProgressStore();
    private final Soundtrack soundtrack = new Soundtrack();
    private final ShopManager shopManager = new ShopManager();
    private final MenuManager menuManager;
    private final LevelSelectManager levelSelectManager;
    private final Random rng = new Random();

    private GameState state = GameState.MAIN_MENU;
    private GameState shopReturnState = GameState.MAIN_MENU;
    private GameState settingsReturnState = GameState.MAIN_MENU;
    private int settingsPage;
    private int settingsSelection = -1;
    private static final int AUDIO_VIEW = 0, AUDIO_EDIT = 1, AUDIO_CONFIRM = 2;
    private int audioEditState = AUDIO_VIEW;
    private int stagedMusicVolume;
    private int audioSelection;
    private boolean audioSliderDragging;
    private boolean settingsKeyboardNavigation;
    private int settingsKeyboardAnchorX;
    private int settingsKeyboardAnchorY;
    private Runnable fullscreenToggle = () -> { };
    private volatile boolean fullscreenMode;
    private boolean restartConfirm;
    private int restartSelection = 1;

    private Player player;
    private GamePanel level1Game;
    private String currentLevelName;
    private boolean runWon;
    private int resultSelection;
    private int runMoneyEarned;
    private int runLevelBonus;
    private int runStageProgress;
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

    public void setFullscreenToggle(Runnable toggle) {
        fullscreenToggle = toggle == null ? () -> { } : toggle;
    }

    public void setFullscreenMode(boolean enabled) { fullscreenMode = enabled; }

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
        boolean beachActive = "Coastal Beach".equals(currentLevelName)
                && (state == GameState.PLAYING || state == GameState.PAUSED || state == GameState.GAME_OVER
                || (state == GameState.SHOP && shopReturnState == GameState.PLAYING)
                || (state == GameState.SETTINGS && (settingsReturnState == GameState.PLAYING
                || settingsReturnState == GameState.PAUSED || settingsReturnState == GameState.GAME_OVER
                || (settingsReturnState == GameState.SHOP && shopReturnState == GameState.PLAYING))));
        boolean soundtrackPaused = restartConfirm || state == GameState.SETTINGS || state == GameState.PAUSED;
        boolean bossWarning = beachActive && level1Game != null && level1Game.isBossWarningActive();
        boolean bossBattle = beachActive && level1Game != null && level1Game.isBossBattleActive();
        soundtrack.update(beachActive, true, profile.getMusicVolume(), profile.isGameMuted(),
                soundtrackPaused, bossWarning, bossBattle);
        if (restartConfirm) {
            updateRestartConfirmation();
            input.endFrame();
            return;
        }
        if (input.isKeyJustPressed(KeyEvent.VK_R)
                && (state == GameState.PLAYING || state == GameState.PAUSED || state == GameState.GAME_OVER)) {
            requestRestart();
            input.endFrame();
            return;
        }
        if (input.isKeyJustPressed(KeyEvent.VK_F11)
                || (input.isKeyJustPressed(KeyEvent.VK_ENTER) && input.isKeyDown(KeyEvent.VK_ALT))) {
            fullscreenToggle.run();
            input.endFrame();
            return;
        }
        java.awt.Point pendingClick = input.peekMouseClick();
        if (pendingClick != null) {
            if (audioButton(getWidth(), getHeight()).contains(pendingClick)) {
                input.consumeMouseClick();
                profile.setGameMuted(!profile.isGameMuted());
            } else if (state != GameState.SETTINGS && hasGearButton()
                    && gearButton(getWidth(), getHeight()).contains(pendingClick)) {
                input.consumeMouseClick();
                openSettings();
            }
        }
        boolean mainMenuShop = state == GameState.SHOP && shopReturnState == GameState.MAIN_MENU;
        if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE) && state != GameState.SETTINGS
                && state != GameState.MAIN_MENU && !mainMenuShop) {
            openSettings();
            input.endFrame();
            return;
        }
        switch (state) {
            case MAIN_MENU -> menuManager.handleInput(input, getWidth(), getHeight(), fullscreenMode);
            case LEVEL_SELECT -> levelSelectManager.handleInput(input, getWidth(), getHeight(), fullscreenMode);
            case PLAYING -> updateGameplay(dt);
            case PAUSED -> updatePaused();
            case GAME_OVER -> updateGameOver();
            case SHOP -> shopManager.handleInput(input, getWidth(), getHeight(), this);
            case SETTINGS -> updateSettings();
        }
        input.endFrame();
    }

    private void updatePaused() {
        if (input.isKeyJustPressed(KeyEvent.VK_R)) requestRestart();
        else if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) state = GameState.LEVEL_SELECT;
        java.awt.Point click = input.consumeMouseClick();
        if (click != null) {
            java.awt.Rectangle resume = new java.awt.Rectangle((getWidth() - 250) / 2, getHeight() / 2 + 42, 250, 46);
            java.awt.Rectangle menu = new java.awt.Rectangle((getWidth() - 250) / 2, getHeight() / 2 + 100, 250, 46);
            if (resume.contains(click)) state = GameState.PLAYING;
            else if (menu.contains(click)) {
                if (level1Game != null) profile.setProgress(currentLevelName, level1Game.getStageProgress());
                state = GameState.LEVEL_SELECT;
            }
        }
    }

    private void updateSettings() {
        int mouseDx = input.getMouseX() - settingsKeyboardAnchorX;
        int mouseDy = input.getMouseY() - settingsKeyboardAnchorY;
        if (settingsKeyboardNavigation && mouseDx * (long) mouseDx + mouseDy * (long) mouseDy >= 400)
            settingsKeyboardNavigation = false;
        if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
            if (settingsPage == 2) {
                if (audioEditState != AUDIO_VIEW) cancelAudioEdit();
                else settingsPage = 0;
            } else if (settingsPage != 0) settingsPage = 0;
            else state = settingsReturnState;
            return;
        }
        if (settingsPage == 0) {
            int count = settingsChoiceCount();
            if (input.isKeyJustPressed(KeyEvent.VK_UP) || input.isKeyJustPressed(KeyEvent.VK_LEFT)) {
                noteSettingsKeyboardNavigation();
                settingsSelection = settingsSelection < 0 ? count - 1 : (settingsSelection + count - 1) % count;
            }
            if (input.isKeyJustPressed(KeyEvent.VK_DOWN) || input.isKeyJustPressed(KeyEvent.VK_RIGHT)) {
                noteSettingsKeyboardNavigation();
                settingsSelection = settingsSelection < 0 ? 0 : (settingsSelection + 1) % count;
            }
            if (input.isKeyJustPressed(KeyEvent.VK_ENTER) || input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
                if (settingsSelection < 0) settingsSelection = 0;
                activateSettingsChoice();
            }
        } else if (settingsPage == 2) {
            updateAudioSettings();
            return;
        }

        java.awt.Point click = input.consumeMouseClick();
        if (click == null) return;
        settingsKeyboardNavigation = false;
        if (settingsPage == 0) {
            java.awt.Rectangle[] buttons = settingsButtons(getWidth(), getHeight());
            for (int i = 0; i < buttons.length; i++) if (buttons[i].contains(click)) {
                settingsSelection = i;
                activateSettingsChoice();
                break;
            }
        } else if (settingsPage == 1 && settingsBackButton(getWidth(), getHeight()).contains(click)) {
            settingsPage = 0;
        }
    }

    private void updateAudioSettings() {
        java.awt.Rectangle slider = musicSlider(getWidth(), getHeight());
        if (audioEditState == AUDIO_EDIT) {
            if (input.isKeyJustPressed(KeyEvent.VK_LEFT)) {
                noteSettingsKeyboardNavigation();
                stagedMusicVolume = Math.max(0, stagedMusicVolume - 1);
            }
            if (input.isKeyJustPressed(KeyEvent.VK_RIGHT)) {
                noteSettingsKeyboardNavigation();
                stagedMusicVolume = Math.min(100, stagedMusicVolume + 1);
            }
            if (audioSliderDragging) {
                if (input.isMousePressed()) setStagedVolumeFromMouse(input.getMouseX(), slider);
                else audioSliderDragging = false;
            }
        }

        if (input.isKeyJustPressed(KeyEvent.VK_UP) || input.isKeyJustPressed(KeyEvent.VK_DOWN)
                || (audioEditState != AUDIO_EDIT && (input.isKeyJustPressed(KeyEvent.VK_LEFT)
                || input.isKeyJustPressed(KeyEvent.VK_RIGHT)))) {
            noteSettingsKeyboardNavigation();
            audioSelection = 1 - audioSelection;
        }
        if (input.isKeyJustPressed(KeyEvent.VK_ENTER) || input.isKeyJustPressed(KeyEvent.VK_SPACE))
            activateAudioAction();

        java.awt.Point click = input.consumeMouseClick();
        if (click == null) return;
        settingsKeyboardNavigation = false;
        if (audioEditState == AUDIO_EDIT && slider.contains(click)) {
            audioSliderDragging = true;
            setStagedVolumeFromMouse(click.x, slider);
            return;
        }
        java.awt.Rectangle[] buttons = audioActionButtons(getWidth(), getHeight());
        for (int i = 0; i < buttons.length; i++) if (buttons[i].contains(click)) {
            audioSelection = i;
            activateAudioAction();
            return;
        }
    }

    private void activateAudioAction() {
        audioSliderDragging = false;
        if (audioEditState == AUDIO_VIEW) {
            if (audioSelection == 0) {
                stagedMusicVolume = profile.getMusicVolume();
                audioEditState = AUDIO_EDIT;
                audioSelection = 0;
            } else settingsPage = 0;
        } else if (audioEditState == AUDIO_EDIT) {
            if (audioSelection == 0) {
                audioEditState = AUDIO_CONFIRM;
                audioSelection = 1;
                noteSettingsKeyboardNavigation();
            } else cancelAudioEdit();
        } else {
            if (audioSelection == 0) profile.setMusicVolume(stagedMusicVolume);
            cancelAudioEdit();
        }
    }

    private void cancelAudioEdit() {
        stagedMusicVolume = profile.getMusicVolume();
        audioEditState = AUDIO_VIEW;
        audioSelection = 0;
        audioSliderDragging = false;
    }

    private void noteSettingsKeyboardNavigation() {
        settingsKeyboardNavigation = true;
        settingsKeyboardAnchorX = input.getMouseX();
        settingsKeyboardAnchorY = input.getMouseY();
    }

    private void openAudioSettings() {
        settingsPage = 2;
        stagedMusicVolume = profile.getMusicVolume();
        audioEditState = AUDIO_VIEW;
        audioSelection = 0;
        audioSliderDragging = false;
    }

    private void openSettings() {
        settingsReturnState = state;
        settingsPage = 0;
        settingsSelection = -1;
        settingsKeyboardNavigation = false;
        state = GameState.SETTINGS;
    }
    private void activateSettingsChoice() {
        if (settingsSelection == 0) settingsPage = 1;
        else if (settingsSelection == 1) openAudioSettings();
        else if (settingsSelection == 2 && isInGameSettingsContext()) exitSettingsToMap();
        else if (settingsSelection == 2) state = GameState.MAIN_MENU;
        else if (settingsSelection == 3 && settingsReturnState != GameState.MAIN_MENU) state = GameState.MAIN_MENU;
    }
    private boolean isInGameSettingsContext() {
        return settingsReturnState == GameState.PLAYING || settingsReturnState == GameState.PAUSED
                || settingsReturnState == GameState.GAME_OVER
                || (settingsReturnState == GameState.SHOP && shopReturnState == GameState.PLAYING);
    }
    private String[] settingsChoiceLabels() {
        if (isInGameSettingsContext()) return new String[] {"CONTROLS", "AUDIO", "EXIT TO MAP SELECT", "RETURN MAIN MENU"};
        if (settingsReturnState == GameState.LEVEL_SELECT) return new String[] {"CONTROLS", "AUDIO", "RETURN MAIN MENU"};
        return new String[] {"CONTROLS", "AUDIO", "RETURN"};
    }
    private int settingsChoiceCount() { return settingsChoiceLabels().length; }
    private int settingsButtonY() { return settingsChoiceCount() == 4 ? 88 : 116; }
    private boolean isLargeViewport(int w, int h) {
        return fullscreenMode || w > Constants.WINDOW_WIDTH + 48 || h > Constants.WINDOW_HEIGHT + 48;
    }
    private double responsiveScale(int w, int h) {
        return Math.min(isLargeViewport(w, h) ? 1.5 : 1.0, Math.min(w / 1024.0, h / 576.0));
    }
    private double settingsScale(int w, int h) {
        if (isLargeViewport(w, h)) return Math.max(0.65, responsiveScale(w, h));
        return Math.max(0.65, Math.min(1.0, Math.min((w - 24) / 520.0, (h - 24) / 390.0)));
    }
    private double settingsLeft(int w, int h) { return (w - 520 * settingsScale(w, h)) / 2.0; }
    private double settingsTop(int w, int h) { return (h - 390 * settingsScale(w, h)) / 2.0; }
    private java.awt.Rectangle settingsRect(int w, int h, int x, int y, int rw, int rh) {
        double scale = settingsScale(w, h);
        return new java.awt.Rectangle((int) Math.round(settingsLeft(w, h) + x * scale),
                (int) Math.round(settingsTop(w, h) + y * scale),
                (int) Math.round(rw * scale), (int) Math.round(rh * scale));
    }
    private java.awt.Rectangle[] settingsButtons(int w, int h) {
        int count = settingsChoiceCount();
        java.awt.Rectangle[] buttons = new java.awt.Rectangle[count];
        int startY = settingsButtonY();
        for (int i = 0; i < count; i++) buttons[i] = settingsRect(w, h, 80, startY + i * 58, 360, 48);
        return buttons;
    }
    private void exitSettingsToMap() {
        if (settingsReturnState == GameState.PLAYING || settingsReturnState == GameState.PAUSED
                || settingsReturnState == GameState.GAME_OVER
                || (settingsReturnState == GameState.SHOP && shopReturnState == GameState.PLAYING)) {
            if (level1Game != null) profile.setProgress(currentLevelName, level1Game.getStageProgress());
            state = GameState.LEVEL_SELECT;
        } else state = settingsReturnState;
    }
    private java.awt.Rectangle settingsBackButton(int w, int h) { return settingsRect(w, h, 160, 322, 200, 42); }
    private java.awt.Rectangle musicSlider(int w, int h) { return settingsRect(w, h, 104, 151, 312, 18); }
    private java.awt.Rectangle[] audioActionButtons(int w, int h) {
        return new java.awt.Rectangle[] {settingsRect(w, h, 58, 258, 190, 46),
                settingsRect(w, h, 272, 258, 190, 46)};
    }
    private double toolbarScale(int w, int h) {
        return Math.max(0.65, responsiveScale(w, h));
    }
    private int toolbarSize(int w, int h) { return (int) Math.round(48 * toolbarScale(w, h)); }
    private int toolbarY(int w, int h) {
        boolean gameplay = state == GameState.PLAYING || state == GameState.PAUSED || state == GameState.GAME_OVER
                || (state == GameState.SHOP && shopReturnState == GameState.PLAYING);
        int y = gameplay ? Math.max(102, (int) Math.round(102 * toolbarScale(w, h)))
                : (int) Math.round((state == GameState.MAIN_MENU ? 58 : 12) * toolbarScale(w, h));
        return Math.max(6, Math.min(y, h - toolbarSize(w, h) - 8));
    }
    private boolean hasGearButton() {
        return state != GameState.MAIN_MENU && state != GameState.SETTINGS
                && !(state == GameState.SHOP && shopReturnState == GameState.MAIN_MENU);
    }
    private java.awt.Rectangle gearButton(int w, int h) {
        int size = toolbarSize(w, h), y = toolbarY(w, h);
        return new java.awt.Rectangle(w - size - 12, y, size, size);
    }
    private java.awt.Rectangle audioButton(int w, int h) {
        int size = toolbarSize(w, h), x = w - size - 12, y = toolbarY(w, h);
        if (hasGearButton()) x -= size + 7;
        return new java.awt.Rectangle(x, y, size, size);
    }
    private void setStagedVolumeFromMouse(int x, java.awt.Rectangle slider) {
        stagedMusicVolume = (int) Math.round(100.0 * Math.max(0, Math.min(slider.width, x - slider.x)) / slider.width);
    }

    private void updateGameOver() {
        int hovered = resultButtonAt(input.getMouseX(), input.getMouseY());
        if (hovered >= 0) resultSelection = hovered;
        java.awt.Point click = input.consumeMouseClick();
        if (click != null) {
            int chosen = resultButtonAt(click.x, click.y);
            if (chosen >= 0) { resultSelection = chosen; selectResultAction(); return; }
        }
        int actionCount = runWon ? 3 : 2;
        if (input.isKeyJustPressed(KeyEvent.VK_LEFT) || input.isKeyJustPressed(KeyEvent.VK_UP)) {
            resultSelection = (resultSelection + actionCount - 1) % actionCount;
        } else if (input.isKeyJustPressed(KeyEvent.VK_RIGHT) || input.isKeyJustPressed(KeyEvent.VK_DOWN)) {
            resultSelection = (resultSelection + 1) % actionCount;
        } else if (input.isKeyJustPressed(KeyEvent.VK_R)) {
            requestRestart();
        } else if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
            state = GameState.LEVEL_SELECT;
        } else if (input.isKeyJustPressed(KeyEvent.VK_ENTER) || input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            selectResultAction();
        }
    }

    private void selectResultAction() {
        if (runWon && resultSelection == 0) {
            int currentIndex = levelIndex(currentLevelName);
            if (currentIndex + 1 < LEVEL_NAMES.length) beginNewRun(LEVEL_NAMES[currentIndex + 1]);
            else state = GameState.LEVEL_SELECT;
            return;
        }

        int retryAction = runWon ? 1 : 0;
        if (resultSelection == retryAction) {
            requestRestart();
        } else {
            state = GameState.LEVEL_SELECT;
        }
    }

    private void restartRun() {
        profile.resetProgress(currentLevelName);
        beginNewRun(currentLevelName);
    }

    private void requestRestart() { restartSelection = 1; restartConfirm = true; }

    private void updateRestartConfirmation() {
        if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) { restartConfirm = false; return; }
        if (input.isKeyJustPressed(KeyEvent.VK_LEFT) || input.isKeyJustPressed(KeyEvent.VK_UP)
                || input.isKeyJustPressed(KeyEvent.VK_RIGHT) || input.isKeyJustPressed(KeyEvent.VK_DOWN))
            restartSelection = 1 - restartSelection;
        if (input.isKeyJustPressed(KeyEvent.VK_ENTER) || input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            if (restartSelection == 0) { restartConfirm = false; restartRun(); }
            else restartConfirm = false;
            return;
        }
        java.awt.Point click = input.consumeMouseClick();
        if (click == null) return;
        java.awt.Rectangle[] buttons = restartButtons(getWidth(), getHeight());
        for (int i = 0; i < buttons.length; i++) if (buttons[i].contains(click)) {
            restartSelection = i;
            if (i == 0) { restartConfirm = false; restartRun(); }
            else restartConfirm = false;
            break;
        }
    }

    private java.awt.Rectangle[] restartButtons(int w, int h) {
        double scale = Math.max(0.65, responsiveScale(w, h));
        int buttonW = (int) Math.round(138 * scale), buttonH = (int) Math.round(42 * scale);
        int gap = (int) Math.round(22 * scale), y = h / 2 + (int) Math.round(20 * scale);
        int x = (w - buttonW * 2 - gap) / 2;
        return new java.awt.Rectangle[] {new java.awt.Rectangle(x, y, buttonW, buttonH),
                new java.awt.Rectangle(x + buttonW + gap, y, buttonW, buttonH)};
    }

    private void drawRestartConfirmation(Graphics2D g) {
        int w = getWidth(), h = getHeight();
        double scale = Math.max(0.65, responsiveScale(w, h));
        int boxW = (int) Math.round(440 * scale), boxH = (int) Math.round(190 * scale);
        int x = (w - boxW) / 2, y = (h - boxH) / 2;
        g.setColor(new Color(0, 0, 0, 175)); g.fillRect(0, 0, w, h);
        g.setColor(new Color(5, 10, 17, 248)); g.fillRoundRect(x, y, boxW, boxH, 16, 16);
        g.setColor(Constants.COLOR_PANEL_BORDER); g.drawRoundRect(x, y, boxW, boxH, 16, 16);
        g.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, Math.max(18, (int) Math.round(26 * scale))));
        g.setColor(Constants.COLOR_CREAM); centerText(g, "ARE YOU SURE?", w, y + (int) Math.round(54 * scale));
        g.setFont(Constants.FONT_SMALL.deriveFont(Math.max(11f, (float) (13 * scale))));
        g.setColor(Constants.COLOR_TEXT_DIM); centerText(g, "Restarting clears this stage's saved progress.", w, y + (int) Math.round(88 * scale));
        java.awt.Rectangle[] buttons = restartButtons(w, h);
        drawRestartButton(g, buttons[0], "YES", restartSelection == 0);
        drawRestartButton(g, buttons[1], "NO", restartSelection == 1);
    }

    private void drawRestartButton(Graphics2D g, java.awt.Rectangle r, String label, boolean selected) {
        boolean hover = r.contains(input.getMouseX(), input.getMouseY());
        g.setColor(hover ? Constants.COLOR_TEAL_DARK : Constants.COLOR_PANEL_NAVY);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
        g.setColor(hover ? Constants.COLOR_GOLD : selected ? Constants.COLOR_TEAL : Constants.COLOR_PANEL_BORDER);
        g.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);
        g.setFont(Constants.FONT_MENU.deriveFont(Math.max(12f, (float) (18 * r.height / 42.0))));
        g.setColor(Constants.COLOR_TEXT_LIGHT);
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(label, r.x + (r.width - fm.stringWidth(label)) / 2,
                r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2);
    }

    private void centerText(Graphics2D g, String text, int width, int y) {
        g.drawString(text, (width - g.getFontMetrics().stringWidth(text)) / 2, y);
    }

    private int resultButtonAt(int x, int y) {
        double scale = responsiveScale(getWidth(), getHeight());
        double ox = (getWidth() - 1024 * scale) / 2, oy = (getHeight() - 576 * scale) / 2;
        int vx = (int) ((x - ox) / scale), vy = (int) ((y - oy) / scale);
        if (vy < 432 || vy > 475) return -1;
        int start = runWon ? 258 : 345, buttonW = runWon ? 154 : 158, gap = runWon ? 22 : 24;
        int index = (vx - start) / (buttonW + gap);
        return vx >= start && index < (runWon ? 3 : 2)
                && vx <= start + index * (buttonW + gap) + buttonW ? index : -1;
    }

    private int levelIndex(String levelName) {
        for (int i = 0; i < LEVEL_NAMES.length; i++) {
            if (LEVEL_NAMES[i].equals(levelName)) return i;
        }
        return 0;
    }

    private void updateGameplay(double dt) {
        if (input.isKeyJustPressed(KeyEvent.VK_M)) {
            shopReturnState = GameState.PLAYING;
            state = GameState.SHOP;
            return;
        }
        if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
            profile.setProgress(currentLevelName, level1Game.getStageProgress());
            state = GameState.LEVEL_SELECT;
            return;
        }

        if (level1Game != null) {
            level1Game.updateFrame(dt, input);
            profile.setProgress(currentLevelName, level1Game.getStageProgress());
            if (level1Game.isFinished()) {
                runWon = level1Game.didWin();
                runMoneyEarned = runWon ? Math.max(0, level1Game.getMoney() - (level1Game.wasCompletedBeforeRun() ? 0 : 100)) : level1Game.getMoney();
                runLevelBonus = runWon && !level1Game.wasCompletedBeforeRun() ? 100 : 0;
                runStageProgress = level1Game.getStageProgress();
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
            runStageProgress = player.getCleanlinessLevel();
            runFailureMessage = "You ran out of health!";
            resultSelection = 0;
            state = GameState.GAME_OVER;
        } else if (player.getCleanlinessLevel() >= 100) {
            runWon = true;
            runMoneyEarned = 0;
            runLevelBonus = 0;
            runStageProgress = player.getCleanlinessLevel();
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
        soundtrack.restart();
        currentLevelName = levelName;
        runWon = false;
        resultSelection = 0;
        level1Game = new GamePanel(levelName, profile);
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

        for (int i = 0; i < LEVEL_NAMES.length; i++) levelSelectManager.setProgress(i, profile.getProgress(LEVEL_NAMES[i]));

        switch (state) {
            case MAIN_MENU -> menuManager.render(g2, getWidth(), getHeight(), fullscreenMode);
            case LEVEL_SELECT -> levelSelectManager.render(g2, getWidth(), getHeight(), fullscreenMode);
            case PLAYING -> renderGameplay(g2);
            case PAUSED -> {
                renderGameplay(g2);
                uiManager.renderPauseOverlay(g2, getWidth(), getHeight(), input.getMouseX(), input.getMouseY());
            }
            case GAME_OVER -> {
                renderGameplay(g2);
                int finalScore = level1Game != null ? level1Game.getScore() : player.getScore();
                uiManager.renderOutcome(g2, getWidth(), getHeight(), runWon,
                        displayLevelName(currentLevelName), finalScore, runMoneyEarned,
                        runLevelBonus, runStageProgress, runFailureMessage, resultSelection, isLargeViewport(getWidth(), getHeight()));
            }
            case SHOP -> {
                if (shopReturnState == GameState.PLAYING && level1Game != null) renderGameplay(g2);
                else menuManager.render(g2, getWidth(), getHeight(), fullscreenMode);
                shopManager.render(g2, getWidth(), getHeight(), profile);
            }
            case SETTINGS -> {
                renderSettingsBackground(g2);
                renderSettings(g2);
                renderToolbar(g2);
            }
        }
        if (state != GameState.SETTINGS) renderToolbar(g2);
        if (restartConfirm) drawRestartConfirmation(g2);
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

    private void renderSettings(Graphics2D g) {
        int w = getWidth(), h = getHeight();
        double scale = settingsScale(w, h);
        Graphics2D panel = (Graphics2D) g.create();
        panel.translate(settingsLeft(w, h), settingsTop(w, h));
        panel.scale(scale, scale);
        panel.setColor(new Color(5, 10, 17, 245)); panel.fillRoundRect(0, 0, 520, 390, 14, 14);
        panel.setColor(Constants.COLOR_PANEL_BORDER); panel.drawRoundRect(0, 0, 520, 390, 14, 14);
        panel.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 28)); panel.setColor(Constants.COLOR_CREAM);
        panel.drawString(settingsPage == 0 ? "SETTINGS" : settingsPage == 1 ? "CONTROLS" : "AUDIO", 30, 46);
        panel.setFont(Constants.FONT_SMALL); panel.setColor(Constants.COLOR_TEXT_DIM);
        panel.drawString("Choose with mouse or arrows + ENTER. ESC goes back.", 30, 72);
        if (settingsPage == 0) {
            String[] labels = settingsChoiceLabels();
            int startY = settingsButtonY();
            for (int i = 0; i < labels.length; i++) {
                drawSettingsButton(panel, new java.awt.Rectangle(80, startY + i * 58, 360, 48), labels[i], i == settingsSelection);
            }
        } else if (settingsPage == 1) {
            panel.setFont(Constants.FONT_HUD); panel.setColor(Constants.COLOR_TEXT_LIGHT);
            panel.drawString("W A S D / Arrow keys   Move", 35, 125);
            panel.drawString("E / Space             Pick up or sort litter", 35, 160);
            panel.drawString("M                     Open shared market", 35, 195);
            panel.drawString("ESC                   Back / close settings", 35, 230);
            panel.drawString("F11 / Alt + Enter     Toggle full screen", 35, 265);
            drawSettingsButton(panel, new java.awt.Rectangle(160, 322, 200, 42), "BACK", false);
        } else {
            int volume = audioEditState == AUDIO_VIEW ? profile.getMusicVolume() : stagedMusicVolume;
            panel.setFont(Constants.FONT_HUD);
            panel.setColor(Constants.COLOR_TEXT_LIGHT);
            panel.drawString("Music Volume: " + volume + "%", 35, 118);
            java.awt.Rectangle slider = new java.awt.Rectangle(104, 151, 312, 18);
            boolean editable = audioEditState == AUDIO_EDIT;
            panel.setColor(editable ? new Color(20, 25, 30) : new Color(42, 47, 54));
            panel.fillRoundRect(slider.x, slider.y, slider.width, slider.height, 8, 8);
            int fill = slider.width * volume / 100;
            panel.setColor(editable ? Constants.COLOR_TEAL : new Color(93, 112, 122));
            panel.fillRoundRect(slider.x, slider.y, fill, slider.height, 8, 8);
            int knobX = slider.x + fill;
            panel.setColor(editable ? Color.WHITE : new Color(180, 188, 194));
            panel.fillOval(knobX - 8, slider.y - 5, 16, 28);
            panel.setColor(Constants.COLOR_TEXT_DIM);
            panel.setFont(Constants.FONT_SMALL);
            panel.drawString("0% (silent)", slider.x, slider.y + 38);
            panel.drawString("100%", slider.x + slider.width - 34, slider.y + 38);

            panel.setFont(Constants.FONT_SMALL);
            if (audioEditState == AUDIO_CONFIRM) {
                panel.setColor(Constants.COLOR_CREAM);
                panel.drawString("Do you want to keep these changes?", 80, 225);
            } else {
                panel.setColor(Constants.COLOR_TEXT_DIM);
                panel.drawString(audioEditState == AUDIO_EDIT
                        ? "Drag the slider or use LEFT / RIGHT to adjust."
                        : "Stage music only. Select EDIT to change the volume.", 55, 225);
            }

            String[] actionLabels = switch (audioEditState) {
                case AUDIO_EDIT -> new String[] {"SAVE", "CANCEL"};
                case AUDIO_CONFIRM -> new String[] {"YES", "NO"};
                default -> new String[] {"EDIT", "BACK"};
            };
            java.awt.Rectangle[] actionRects = {
                new java.awt.Rectangle(58, 258, 190, 46),
                new java.awt.Rectangle(272, 258, 190, 46)
            };
            for (int i = 0; i < actionLabels.length; i++)
                drawSettingsButton(panel, actionRects[i], actionLabels[i], i == audioSelection);
        }
        panel.dispose();
    }

    private void renderSettingsBackground(Graphics2D g) {
        switch (settingsReturnState) {
            case PLAYING -> renderGameplay(g);
            case PAUSED -> { renderGameplay(g); uiManager.renderPauseOverlay(g, getWidth(), getHeight(), input.getMouseX(), input.getMouseY()); }
            case GAME_OVER -> { renderGameplay(g); uiManager.renderOutcome(g, getWidth(), getHeight(), runWon, displayLevelName(currentLevelName), level1Game.getScore(), runMoneyEarned, runLevelBonus, runStageProgress, runFailureMessage, resultSelection, isLargeViewport(getWidth(), getHeight())); }
            case LEVEL_SELECT -> levelSelectManager.render(g, getWidth(), getHeight(), fullscreenMode);
            case SHOP -> { if (shopReturnState == GameState.PLAYING && level1Game != null) renderGameplay(g); else menuManager.render(g, getWidth(), getHeight(), fullscreenMode); shopManager.render(g, getWidth(), getHeight(), profile); }
            default -> menuManager.render(g, getWidth(), getHeight(), fullscreenMode);
        }
        g.setColor(new Color(0, 0, 0, 115)); g.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawSettingsButton(Graphics2D g, java.awt.Rectangle r, String label, boolean selected) {
        int w = getWidth(), h = getHeight();
        java.awt.Rectangle screen = settingsRect(w, h, r.x, r.y, r.width, r.height);
        boolean hover = !settingsKeyboardNavigation && screen.contains(input.getMouseX(), input.getMouseY());
        boolean active = settingsKeyboardNavigation ? selected : hover;
        g.setColor(active ? Constants.COLOR_TEAL_DARK : Constants.COLOR_PANEL_NAVY);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
        g.setColor(active ? Constants.COLOR_GOLD : Constants.COLOR_PANEL_BORDER);
        g.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);
        g.setFont(Constants.FONT_MENU); g.setColor(Constants.COLOR_TEXT_LIGHT);
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(label, r.x + (r.width - fm.stringWidth(label)) / 2, r.y + 31);
    }

    private void renderToolbar(Graphics2D g) {
        int w = getWidth(), h = getHeight();
        java.awt.Rectangle audio = audioButton(w, h);
        drawAudioButton(g, audio, profile.isGameMuted());
        if (hasGearButton()) {
            java.awt.Rectangle gear = gearButton(w, h);
            boolean hover = gear.contains(input.getMouseX(), input.getMouseY());
            g.setColor(hover ? Constants.COLOR_TEAL_DARK : new Color(15, 20, 25, 225));
            g.fillRoundRect(gear.x, gear.y, gear.width, gear.height, 10, 10);
            g.setColor(hover ? Constants.COLOR_GOLD : Constants.COLOR_CREAM);
            g.drawRoundRect(gear.x, gear.y, gear.width, gear.height, 10, 10);
            g.setColor(Constants.COLOR_CREAM);
            g.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, Math.max(20, gear.height * 2 / 3)));
            String icon = "\u2699";
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.drawString(icon, gear.x + (gear.width - fm.stringWidth(icon)) / 2,
                    gear.y + (gear.height + fm.getAscent() - fm.getDescent()) / 2);
            if (hover) drawToolbarTip(g, gear, "Settings (ESC)");
        }
    }

    private void drawAudioButton(Graphics2D g, java.awt.Rectangle r, boolean muted) {
        int unit = Math.max(2, r.width / 12), cx = r.x + r.width / 2, cy = r.y + r.height / 2;
        g.setColor(Color.WHITE);
        g.fillPolygon(new int[]{cx - unit * 4, cx - unit * 2, cx - unit * 2, cx - unit * 4},
                new int[]{cy - unit, cy - unit * 2, cy + unit * 2, cy + unit}, 4);
        g.fillRect(cx - unit * 2, cy - unit * 2, unit, unit * 4);
        if (muted) {
            g.setColor(new Color(255, 92, 85));
            g.setStroke(new java.awt.BasicStroke(Math.max(2, unit)));
            g.drawLine(cx + unit, cy - unit * 3, cx + unit * 6, cy + unit * 3);
            g.drawLine(cx + unit * 6, cy - unit * 3, cx + unit, cy + unit * 3);
            g.setStroke(new java.awt.BasicStroke(1));
        } else {
            g.setColor(Color.WHITE);
            g.setStroke(new java.awt.BasicStroke(Math.max(2, unit / 2f)));
            g.drawArc(cx - unit * 2, cy - unit * 3, unit * 5, unit * 6, -65, 130);
            g.drawArc(cx - unit * 3, cy - unit * 4, unit * 7, unit * 8, -65, 130);
            g.setStroke(new java.awt.BasicStroke(1));
        }
    }

    private void drawToolbarTip(Graphics2D g, java.awt.Rectangle button, String text) {
        g.setFont(Constants.FONT_SMALL);
        int tw = g.getFontMetrics().stringWidth(text) + 18, th = 28;
        int x = Math.max(8, Math.min(getWidth() - tw - 8, button.x + button.width - tw));
        int y = button.y + button.height + 5;
        if (y + th > getHeight() - 5) y = button.y - th - 5;
        g.setColor(new Color(7, 12, 18, 235)); g.fillRoundRect(x, y, tw, th, 7, 7);
        g.setColor(Constants.COLOR_TEAL); g.drawRoundRect(x, y, tw, th, 7, 7);
        g.setColor(Constants.COLOR_TEXT_LIGHT); g.drawString(text, x + 9, y + 19);
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
        shopReturnState = state;
        state = GameState.SHOP;
    }

    @Override
    public void onOpenSettings() {
        openSettings();
    }

    @Override
    public void onQuit() {
        soundtrack.stop();
        System.exit(0);
    }

    @Override public String purchaseCleaner(int tier) { return profile.buyCleaner(tier); }
    @Override public void closeShop() { state = shopReturnState; }
}
