package com.ecoquest.level1;

import com.ecoquest.core.InputHandler;
import com.ecoquest.ui.SoundFx;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.prefs.Preferences;

/**
 * The in-game pause menu, opened with the menu button in the HUD or with Esc. The level is frozen
 * while it is open. It looks like the main menu: a column of buttons (Resume, Guide, Settings,
 * Restart, Quit). Guide and Settings open their own page with a Back button. Restart and Quit ask
 * for a second press to confirm. Works with the mouse or with W/S (arrows) + Enter.
 */
final class PauseMenu {
    enum Action { NONE, RESTART, QUIT }

    private enum Page { MAIN, GUIDE, SETTINGS }

    private static final String[] ITEMS = {"Resume", "Guide", "Settings", "Restart", "Quit to menu"};
    private static final int RESUME = 0, GUIDE = 1, SETTINGS = 2, RESTART = 3, QUIT = 4;
    private static final int BTN_W = 390, BTN_H = 56, BTN_GAP = 14, LABEL_SCALE = 3;
    private static final int PAGE_W = 820, PAGE_H = 440, SETTINGS_H = 40 + 3 * 84;
    private static final double CONFIRM_SECONDS = 3;

    private static final String PREF_SHAKE = "screenShake", PREF_KEY_HINTS = "keyHints";
    private static boolean screenShake = loadPref(PREF_SHAKE, true);
    private static boolean keyHints = loadPref(PREF_KEY_HINTS, true);

    private boolean open;
    private Page page = Page.MAIN;
    private int selected;
    private int confirming = -1;
    private double confirmTimer;
    private int lastMouseX = -1, lastMouseY = -1;

    // Layout, rebuilt each frame for the current screen size.
    private final Rectangle[] buttons = new Rectangle[ITEMS.length];
    private final Rectangle back = new Rectangle();
    private final Rectangle[] toggles = {new Rectangle(), new Rectangle(), new Rectangle()};
    private final Rectangle pageBox = new Rectangle();

    PauseMenu() {
        for (int i = 0; i < buttons.length; i++) buttons[i] = new Rectangle();
    }

    static boolean screenShake() { return screenShake; }
    static boolean keyHints() { return keyHints; }

    boolean isOpen() { return open; }

    void open() {
        open = true;
        page = Page.MAIN;
        selected = RESUME;
        confirming = -1;
        SoundFx.play(SoundFx.Sound.TAB);
    }

    void close() {
        open = false;
        confirming = -1;
    }

    /** Handles this frame's keys and click; returns what the game should do. */
    Action update(double dt, InputHandler input, Point click, int width, int height) {
        layout(width, height);
        confirmTimer = Math.max(0, confirmTimer - dt);
        if (confirmTimer == 0) confirming = -1;

        if (page != Page.MAIN) {
            if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE) || input.isKeyJustPressed(KeyEvent.VK_BACK_SPACE)
                    || (click != null && back.contains(click))) {
                page = Page.MAIN;
                SoundFx.play(SoundFx.Sound.TAB);
                return Action.NONE;
            }
            if (page == Page.SETTINGS && click != null) {
                if (toggles[0].contains(click)) SoundFx.setEnabled(!SoundFx.isEnabled());
                if (toggles[1].contains(click)) screenShake = savePref(PREF_SHAKE, !screenShake);
                if (toggles[2].contains(click)) keyHints = savePref(PREF_KEY_HINTS, !keyHints);
                for (Rectangle t : toggles) if (t.contains(click)) SoundFx.play(SoundFx.Sound.BLIP);
            }
            return Action.NONE;
        }

        if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
            close();
            return Action.NONE;
        }
        if (input.isKeyJustPressed(KeyEvent.VK_W) || input.isKeyJustPressed(KeyEvent.VK_UP)) move(-1);
        if (input.isKeyJustPressed(KeyEvent.VK_S) || input.isKeyJustPressed(KeyEvent.VK_DOWN)) move(1);
        int mx = input.getMouseX(), my = input.getMouseY();
        if (mx != lastMouseX || my != lastMouseY) {          // hovering selects, like the main menu
            for (int i = 0; i < buttons.length; i++) if (buttons[i].contains(mx, my)) selected = i;
            lastMouseX = mx;
            lastMouseY = my;
        }
        if (input.isKeyJustPressed(KeyEvent.VK_ENTER) || input.isKeyJustPressed(KeyEvent.VK_SPACE))
            return activate(selected);
        if (click != null)
            for (int i = 0; i < buttons.length; i++) if (buttons[i].contains(click)) return activate(i);
        return Action.NONE;
    }

    private void move(int step) {
        selected = Math.floorMod(selected + step, ITEMS.length);
        SoundFx.play(SoundFx.Sound.BLIP);
    }

    private Action activate(int index) {
        selected = index;
        switch (index) {
            case RESUME -> close();
            case GUIDE -> page = Page.GUIDE;
            case SETTINGS -> page = Page.SETTINGS;
            default -> {
                if (confirming == index) {
                    close();
                    return index == RESTART ? Action.RESTART : Action.QUIT;
                }
                confirming = index;
                confirmTimer = CONFIRM_SECONDS;
            }
        }
        SoundFx.play(SoundFx.Sound.TAB);
        return Action.NONE;
    }

    private void layout(int width, int height) {
        int total = ITEMS.length * BTN_H + (ITEMS.length - 1) * BTN_GAP;
        int top = Math.max(160, (height - total) / 2 + 40);
        for (int i = 0; i < buttons.length; i++)
            buttons[i].setBounds((width - BTN_W) / 2, top + i * (BTN_H + BTN_GAP), BTN_W, BTN_H);
        int pw = Math.min(PAGE_W, width - 32), ph = Math.min(PAGE_H, height - 150);
        pageBox.setBounds((width - pw) / 2, 146, pw, ph);
        int pageBottom = page == Page.SETTINGS ? pageBox.y + SETTINGS_H : pageBox.y + pageBox.height;
        back.setBounds((width - 240) / 2, pageBottom + 16, 240, 48);
        for (int i = 0; i < toggles.length; i++)
            toggles[i].setBounds(pageBox.x + pageBox.width - 40 - 108, pageBox.y + 40 + i * 84 + 14, 108, 34);
    }

    // ------------------------------------------------------------ drawing
    /**
     * @param levelName shown under the title
     * @param gear      what the player is wearing and carrying (one line)
     */
    void render(Graphics2D g, int width, int height, InputHandler input, String levelName, String gear) {
        layout(width, height);
        Graphics2D c = (Graphics2D) g.create();
        c.setColor(new Color(6, 12, 18, 185));
        c.fillRect(0, 0, width, height);
        Point mouse = input == null ? new Point(-1, -1) : new Point(input.getMouseX(), input.getMouseY());

        String title = page == Page.MAIN ? "Paused" : page == Page.GUIDE ? "Guide" : "Settings";
        int tw = HudArt.width(title, 7, HudArt.TITLE);
        HudArt.text(c, title, (width - tw) / 2, 24, 7, HudArt.TITLE);
        int sub = HudArt.width(levelName, 2, HudArt.TAGLINE) + 60, sx = (width - sub) / 2;
        HudArt.creamPlate(c, sx, 104, sub, 26);
        HudArt.leaf(c, sx + 12, 110, 2);
        HudArt.text(c, levelName, sx + 36, 110, 2, HudArt.TAGLINE);

        switch (page) {
            case MAIN -> drawMain(c, width, height, gear);
            case GUIDE -> drawGuide(c);
            case SETTINGS -> drawSettings(c, mouse);
        }
        if (page != Page.MAIN) {
            boolean hover = back.contains(mouse);
            HudArt.plate(c, back.x, back.y, back.width, back.height, hover);
            String label = "Back";
            HudArt.text(c, label, back.x + (back.width - HudArt.width(label, 3, HudArt.BUTTON)) / 2,
                    back.y + (back.height - HudArt.textH(3)) / 2, 3, hover ? HudArt.BUTTON_SELECTED : HudArt.BUTTON);
            String hint = "ESC: BACK";
            HudArt.text(c, hint, (width - HudArt.width(hint, 2, HudArt.DIM)) / 2, back.y + back.height + 14, 2, HudArt.DIM);
        }
        c.dispose();
    }

    private void drawMain(Graphics2D c, int width, int height, String gear) {
        for (int i = 0; i < ITEMS.length; i++) {
            Rectangle r = buttons[i];
            boolean sel = i == selected;
            boolean warn = confirming == i;
            HudArt.plate(c, r.x, r.y, r.width, r.height, sel);
            if (warn) {
                c.setColor(new Color(150, 40, 40, 200));
                c.fillRoundRect(r.x + 2, r.y + 2, r.width - 3, r.height - 3, 6, 6);
            }
            if (sel) HudArt.sprout(c, r.x + 14, r.y + (BTN_H - 7 * LABEL_SCALE) / 2, LABEL_SCALE);
            String label = warn ? (i == RESTART ? "Sure? Restart" : "Sure? Quit") : ITEMS[i];
            HudArt.text(c, label, r.x + 46, r.y + (BTN_H - HudArt.textH(LABEL_SCALE)) / 2 - 1, LABEL_SCALE,
                    sel ? HudArt.BUTTON_SELECTED : HudArt.BUTTON);
        }
        Rectangle last = buttons[ITEMS.length - 1];
        String gearLine = "Gear: " + gear;
        HudArt.text(c, gearLine, (width - HudArt.width(gearLine, 2, HudArt.DIM)) / 2, last.y + last.height + 22, 2, HudArt.DIM);
        String hint = "W/S: SELECT   ENTER: OK   ESC: RESUME";
        HudArt.text(c, hint, (width - HudArt.width(hint, 2, HudArt.DIM)) / 2, last.y + last.height + 44, 2, HudArt.DIM);
    }

    private void drawGuide(Graphics2D c) {
        Rectangle p = pageBox;
        HudArt.plate(c, p.x, p.y, p.width, p.height, false);
        int colW = (p.width - 80) / 2, left = p.x + 30, right = left + colW + 20, top = p.y + 26;
        String[][] foot = {
            {"WASD", "Move"}, {"E", "Pick up / throw"}, {"Q", "Switch item"}, {"H", "Drink potion"},
            {"F", "Scanner"}, {"R", "Restart level"}, {"ESC", "Pause menu"},
        };
        String[][] boat = {{"W/S", "Throttle"}, {"A/D", "Steer"}, {"E", "Pick up"}};
        int y = keyList(c, "On foot", foot, left, top);
        keyList(c, "In the boat", boat, right, top);

        int by = top + 30 + boat.length * 34 + 18;
        HudArt.text(c, "Sorting bins", right, by, 2, HudArt.TEAL_TEXT);
        GarbageType[] types = GarbageType.values();
        String[] examples = {"Food, plants", "Bottles, cans", "Wrappers, foam", "Batteries"};
        for (int i = 0; i < types.length; i++) {
            int ry = by + 26 + i * 44;
            c.setColor(new Color(10, 16, 26));
            c.fillRect(right + 2, ry + 2, 30, 30);
            c.setColor(new Color(types[i].getColor()));
            c.fillRect(right, ry, 30, 30);
            c.setColor(new Color(255, 255, 255, 70));
            c.fillRect(right + 3, ry + 3, 24, 4);
            HudArt.text(c, types[i].getLabel(), right + 42, ry + 1, 2, HudArt.WHITE);
            HudArt.text(c, examples[i], right + 42, ry + 19, 2, HudArt.DIM);
        }
        int ny = Math.max(y, by + 26 + types.length * 44) + 10;
        HudArt.text(c, "Wrong bin costs 1 life. Walk into the sea to use the boat.", left, ny, 2, HudArt.LIGHT);
    }

    private int keyList(Graphics2D c, String title, String[][] rows, int x, int y) {
        HudArt.text(c, title, x, y, 2, HudArt.TEAL_TEXT);
        for (int i = 0; i < rows.length; i++) {
            int ry = y + 26 + i * 34;
            HudArt.key(c, rows[i][0], x, ry);
            HudArt.text(c, rows[i][1], x + 84, ry + 5, 2, HudArt.WHITE);
        }
        return y + 26 + rows.length * 34;
    }

    private void drawSettings(Graphics2D c, Point mouse) {
        Rectangle p = pageBox;
        String[][] rows = {
            {"Sound & music", "Sound effects and background music"},
            {"Screen shake", "Shake the screen on big hits"},
            {"Key hints", "Control reminder in the corner"},
        };
        boolean[] on = {SoundFx.isEnabled(), screenShake, keyHints};
        int rowsH = SETTINGS_H;
        HudArt.plate(c, p.x, p.y, p.width, rowsH, false);
        for (int i = 0; i < rows.length; i++) {
            Rectangle t = toggles[i];
            int ry = p.y + 40 + i * 84;
            if (i > 0) {
                c.setColor(HudArt.BORDER);
                c.fillRect(p.x + 30, ry - 2, p.width - 60, 2);
            }
            HudArt.text(c, rows[i][0], p.x + 36, ry + 14, 3, HudArt.WHITE);
            HudArt.text(c, rows[i][1], p.x + 36, ry + 44, 2, HudArt.DIM);
            boolean hover = t.contains(mouse);
            HudArt.plate(c, t.x, t.y, t.width, t.height, on[i]);
            if (hover) {
                c.setColor(new Color(255, 255, 255, 30));
                c.fillRoundRect(t.x, t.y, t.width, t.height, 6, 6);
            }
            String label = on[i] ? "On" : "Off";
            HudArt.text(c, label, t.x + (t.width - HudArt.width(label, 3, HudArt.BUTTON)) / 2,
                    t.y + (t.height - HudArt.textH(3)) / 2, 3, on[i] ? HudArt.BUTTON_SELECTED : HudArt.DIM);
        }
    }

    private static boolean loadPref(String key, boolean fallback) {
        try { return Preferences.userNodeForPackage(PauseMenu.class).getBoolean(key, fallback); }
        catch (RuntimeException e) { return fallback; }
    }

    private static boolean savePref(String key, boolean value) {
        try { Preferences.userNodeForPackage(PauseMenu.class).putBoolean(key, value); }
        catch (RuntimeException ignored) { }
        return value;
    }
}
