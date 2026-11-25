package com.ecoquest.ui;

import com.ecoquest.core.InputHandler;
import com.ecoquest.util.Constants;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders and drives the Main Menu screen. Layout, proportions and text
 * placement are reverse-engineered from the reference mockup: a top HUD
 * strip (hi-score / online-world / version badges), a two-tone
 * polluted-to-restored backdrop, an outlined two-color title lockup, a
 * cream tagline ribbon, four stacked navy menu buttons, and
 * a bottom status bar (cleanliness meter, control hints, BGM status).
 */
public class MenuManager {

    /** Callback surface the owning {@code Game} implements to react to menu choices. */
    public interface MenuListener {
        void onStartGame();
        void onOpenShop();
        void onOpenSettings();
        void onQuit();
    }

    private static final class MenuItem {
        final String label;

        MenuItem(String label) {
            this.label = label;
        }
    }

    private static final int BTN_WIDTH = 390;
    private static final int BTN_HEIGHT = 56;
    private static final int BTN_GAP = 14;
    private static final int MENU_START_Y = 300;

    private final List<MenuItem> items = new ArrayList<>();
    private final MenuListener listener;
    private int hiScore;
    private int selectedIndex = 0;
    private final EcoLoopBackground background = new EcoLoopBackground();

    public MenuManager(MenuListener listener, int hiScore) {
        this.listener = listener;
        this.hiScore = hiScore;
        items.add(new MenuItem("START GAME"));
        items.add(new MenuItem("SHOP"));
        items.add(new MenuItem("SETTINGS & AUDIO"));
        items.add(new MenuItem("QUIT TO DESKTOP"));
    }

    public void setHiScore(int score) {
        hiScore = Math.max(0, score);
    }

    /** Polls the shared InputHandler for keyboard/mouse menu navigation. */
    public void handleInput(InputHandler input, int panelWidth) {
        if (input.isKeyJustPressed(KeyEvent.VK_DOWN)) {
            selectedIndex = (selectedIndex + 1) % items.size();
        }
        if (input.isKeyJustPressed(KeyEvent.VK_UP)) {
            selectedIndex = (selectedIndex - 1 + items.size()) % items.size();
        }
        if (input.isKeyJustPressed(KeyEvent.VK_SPACE) || input.isKeyJustPressed(KeyEvent.VK_ENTER)) {
            activate(selectedIndex);
        }
        Point click = input.consumeMouseClick();
        if (click != null) {
            int startX = (panelWidth - BTN_WIDTH) / 2;
            int mx = click.x;
            int my = click.y;
            for (int i = 0; i < items.size(); i++) {
                int by = MENU_START_Y + i * (BTN_HEIGHT + BTN_GAP);
                if (mx >= startX && mx <= startX + BTN_WIDTH && my >= by && my <= by + BTN_HEIGHT) {
                    selectedIndex = i;
                    activate(i);
                }
            }
        }
    }

    private void activate(int index) {
        switch (index) {
            case 0 -> listener.onStartGame();
            case 1 -> listener.onOpenShop();
            case 2 -> listener.onOpenSettings();
            case 3 -> listener.onQuit();
            default -> { /* no-op */ }
        }
    }

    public void render(Graphics2D g, int panelWidth, int panelHeight) {
        drawBackdrop(g, panelWidth, panelHeight);
        drawTopHud(g, panelWidth);
        drawTitle(g, panelWidth);
        drawTagline(g, panelWidth);
        drawMenuButtons(g, panelWidth);
    }

    private void drawBackdrop(Graphics2D g, int w, int h) {
        if (background.isLoaded()) {
            background.render(g, w, h);
        } else {
            // Left half: smog-choked industrial skyline. Right half: restored green world.
            g.setPaint(new GradientPaint(0, 0, new Color(60, 60, 65), 0, h, new Color(20, 40, 35)));
            g.fillRect(0, 0, w / 2, h);
            g.setPaint(new GradientPaint(w / 2f, 0, Constants.COLOR_SKY_BLUE, w / 2f, h, new Color(120, 200, 120)));
            g.fillRect(w / 2, 0, w / 2, h);
        }
    }

    private void drawTopHud(Graphics2D g, int w) {
        String label = "HI-SCORE";
        String score = String.format("%03d,%03d", hiScore / 1000, hiScore % 1000);
        int labelW = PixelFont.width(label, SMALL_SCALE, true);
        int scoreW = PixelFont.width(score, SMALL_SCALE, true);
        int textY = 20 + (40 - TEXT_H) / 2;
        drawPanel(g, 40, 20, 16 + labelW + 16 + scoreW + 16, 40);
        PixelFont.draw(g, label, 56, textY, SMALL_SCALE, HUD_GOLD);
        PixelFont.draw(g, score, 56 + labelW + 16, textY, SMALL_SCALE, HUD_WHITE);

        // Right-aligned info panels, sized to their pixel text.
        String version = Constants.VERSION;
        int versionW = PixelFont.width(version, SMALL_SCALE) + 30;
        int versionX = w - 40 - versionW;
        textY = 20 + (32 - TEXT_H) / 2;
        drawPanel(g, versionX, 20, versionW, 32);
        PixelFont.draw(g, version, versionX + 15, textY, SMALL_SCALE, HUD_LIGHT);

        String onlineText = "ONLINE WORLD \u2022 SERVER: ECO-01";
        int onlineW = PixelFont.width(onlineText, SMALL_SCALE) + 30;
        int onlineX = versionX - 8 - onlineW;
        drawPanel(g, onlineX, 20, onlineW, 32);
        PixelFont.draw(g, onlineText, onlineX + 15, textY, SMALL_SCALE, HUD_TEAL);
    }

    // ------------------------------------------------------------------ themed pixel type

    private static final int TITLE_SCALE = 8;
    private static final int LABEL_SCALE = 3;
    private static final int SMALL_SCALE = 2;

    /** "ECO": leafy green, fresh lime on top fading to forest green, with mossy depth. */
    private static final PixelFont.Style TITLE_ECO = new PixelFont.Style(
            new Color(178, 244, 120), new Color(36, 148, 82), new Color(220, 255, 170),
            new Color(22, 84, 52), 2, new Color(10, 28, 18), true);
    /** "QUEST": sun gold, warm yellow fading to orange, with earthy depth. */
    private static final PixelFont.Style TITLE_QUEST = new PixelFont.Style(
            new Color(255, 238, 118), new Color(244, 146, 36), new Color(255, 252, 200),
            new Color(140, 72, 22), 2, new Color(40, 20, 8), true);
    private static final PixelFont.Style TAGLINE = new PixelFont.Style(
            new Color(52, 96, 40), new Color(40, 74, 30), null, new Color(196, 182, 130), 1, null);
    private static final PixelFont.Style LABEL = new PixelFont.Style(
            new Color(236, 240, 228), new Color(196, 214, 200), null, new Color(10, 16, 26), 1, null);
    private static final PixelFont.Style LABEL_SELECTED = new PixelFont.Style(
            new Color(255, 255, 240), new Color(214, 255, 196), null, new Color(8, 60, 44), 1, null);

    /** Flat HUD text in the menu's pixel font, with the same one-pixel dark drop as the buttons. */
    private static final int TEXT_H = PixelFont.GLYPH_H * SMALL_SCALE;
    private static final Color HUD_SHADOW = new Color(10, 16, 26);
    private static final PixelFont.Style HUD_GOLD = hudStyle(Constants.COLOR_GOLD, true);
    private static final PixelFont.Style HUD_WHITE = hudStyle(Color.WHITE, true);
    private static final PixelFont.Style HUD_TEAL = hudStyle(Constants.COLOR_TEAL, false);
    private static final PixelFont.Style HUD_LIGHT = hudStyle(Constants.COLOR_TEXT_LIGHT, false);

    private static PixelFont.Style hudStyle(Color c, boolean bold) {
        return new PixelFont.Style(c, c, null, HUD_SHADOW, 1, null, bold);
    }

    private static double seconds() {
        return System.nanoTime() / 1e9;
    }

    private void drawTitle(Graphics2D g, int w) {
        String eco = "ECO";
        String quest = "QUEST";
        int space = 5 * TITLE_SCALE;
        int totalWidth = PixelFont.width(eco, TITLE_SCALE, true) + space + PixelFont.width(quest, TITLE_SCALE, true);
        int startX = (w - totalWidth) / 2;
        int top = 140;
        int questX = startX + PixelFont.width(eco, TITLE_SCALE, true) + space;

        PixelFont.draw(g, eco, startX, top, TITLE_SCALE, TITLE_ECO);
        PixelFont.draw(g, quest, questX, top, TITLE_SCALE, TITLE_QUEST);

        double t = seconds();
        // a sprout growing out of the "O", swaying gently
        int oX = startX + 2 * 7 * TITLE_SCALE;              // third glyph
        int sway = (int) Math.round(Math.sin(t * 2.2) * 2);
        int sproutScale = 5;
        PixelFont.drawIcon(g, PixelFont.SPROUT,
                oX + (6 * TITLE_SCALE - 7 * sproutScale) / 2 + sway,
                top - 6 * sproutScale, sproutScale);
        // a sun glint twinkling on the "Q"
        double tw = 0.5 + 0.5 * Math.sin(t * 3.1);
        if (tw > 0.25) {
            int sc = tw > 0.7 ? 3 : 2;
            PixelFont.drawIcon(g, PixelFont.SPARKLE, questX - 2 * sc, top - 2 * sc, sc);
        }
    }

    private void drawTagline(Graphics2D g, int w) {
        String tagline = "RESTORE THE ENVIRONMENTAL ODYSSEY";
        int textW = PixelFont.width(tagline, SMALL_SCALE);
        int leafW = PixelFont.LEAF.w() * SMALL_SCALE;
        int gap = 10;
        int tw = textW + 2 * (leafW + gap) + 32;
        int tx = (w - tw) / 2;
        int ty = 225;
        int th = 32;
        g.setColor(Constants.COLOR_CREAM);
        g.fillRoundRect(tx, ty, tw, th, 4, 4);
        g.setColor(new Color(214, 204, 160));
        g.fillRect(tx + 2, ty + th - 5, tw - 4, 3);
        g.setColor(Constants.COLOR_TEAL_DARK);
        g.drawRoundRect(tx, ty, tw, th, 4, 4);
        int textY = ty + (th - PixelFont.GLYPH_H * SMALL_SCALE) / 2 - 1;
        int leafY = ty + (th - PixelFont.LEAF.h() * SMALL_SCALE) / 2;
        PixelFont.drawIcon(g, PixelFont.LEAF, tx + 16, leafY, SMALL_SCALE);
        PixelFont.draw(g, tagline, tx + 16 + leafW + gap, textY, SMALL_SCALE, TAGLINE);
        // mirrored leaf on the right
        Graphics2D m = (Graphics2D) g.create();
        int rx = tx + tw - 16;
        m.translate(rx, 0);
        m.scale(-1, 1);
        PixelFont.drawIcon(m, PixelFont.LEAF, 0, leafY, SMALL_SCALE);
        m.dispose();
    }

    private void drawMenuButtons(Graphics2D g, int w) {
        int startX = (w - BTN_WIDTH) / 2;

        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            int by = MENU_START_Y + i * (BTN_HEIGHT + BTN_GAP);
            boolean selected = i == selectedIndex;

            g.setColor(selected ? Constants.COLOR_TEAL_DARK : Constants.COLOR_PANEL_NAVY);
            g.fillRoundRect(startX, by, BTN_WIDTH, BTN_HEIGHT, 6, 6);
            g.setColor(selected ? Constants.COLOR_TEAL : Constants.COLOR_PANEL_BORDER);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(startX, by, BTN_WIDTH, BTN_HEIGHT, 6, 6);

            int labelY = by + (BTN_HEIGHT - PixelFont.GLYPH_H * LABEL_SCALE) / 2 - 1;
            if (selected) { // sprout bullet marks the chosen option
                PixelFont.drawIcon(g, PixelFont.SPROUT, startX + 14,
                        by + (BTN_HEIGHT - 7 * LABEL_SCALE) / 2, LABEL_SCALE);
            }
            PixelFont.draw(g, item.label, startX + 46, labelY, LABEL_SCALE, selected ? LABEL_SELECTED : LABEL);
        }
    }

    private void drawPanel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Constants.COLOR_PANEL_NAVY);
        g.fillRoundRect(x, y, w, h, 4, 4);
        g.setColor(Constants.COLOR_PANEL_BORDER);
        g.drawRoundRect(x, y, w, h, 4, 4);
    }

    public int getSelectedIndex() { return selectedIndex; }
}
