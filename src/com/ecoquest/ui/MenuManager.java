package com.ecoquest.ui;

import com.ecoquest.core.InputHandler;
import com.ecoquest.util.Constants;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Renders and drives the Main Menu screen. Layout, proportions and text
 * placement are reverse-engineered from the reference mockup: a top HUD
 * strip (hi-score / online-world / version badges), a two-tone
 * polluted-to-restored backdrop, an outlined two-color title lockup, a
 * cream tagline ribbon, four stacked navy menu buttons with badges, and
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
        final String badge;
        final Color badgeColor;

        MenuItem(String label, String badge, Color badgeColor) {
            this.label = label;
            this.badge = badge;
            this.badgeColor = badgeColor;
        }
    }

    private static final int BTN_WIDTH = 390;
    private static final int BTN_HEIGHT = 56;
    private static final int BTN_GAP = 14;
    private static final int MENU_START_Y = 300;

    private final List<MenuItem> items = new ArrayList<>();
    private final MenuListener listener;
    private final int hiScore;
    private int selectedIndex = 0;
    private BufferedImage backgroundImage;

    public MenuManager(MenuListener listener, int hiScore) {
        this.listener = listener;
        this.hiScore = hiScore;
        loadBackgroundImage();
        items.add(new MenuItem("START GAME", "READY", Constants.COLOR_TEAL));
        items.add(new MenuItem("SHOP", "3 NEW", Constants.COLOR_GOLD));
        items.add(new MenuItem("SETTINGS & AUDIO", null, null));
        items.add(new MenuItem("QUIT TO DESKTOP", null, null));
    }

    private void loadBackgroundImage() {
        try {
            InputStream in = getClass().getResourceAsStream("/assets/menu_bg.jpg");
            if (in != null) {
                backgroundImage = ImageIO.read(in);
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            File file = new File("assets/menu_bg.jpg");
            if (file.exists()) {
                backgroundImage = ImageIO.read(file);
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            File file = new File("bin/assets/menu_bg.jpg");
            if (file.exists()) {
                backgroundImage = ImageIO.read(file);
            }
        } catch (Exception ignored) {
        }
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
        drawBottomBar(g, panelWidth, panelHeight);
    }

    private void drawBackdrop(Graphics2D g, int w, int h) {
        if (backgroundImage != null) {
            int imgW = backgroundImage.getWidth();
            int imgH = backgroundImage.getHeight();
            double scale = Math.max((double) w / imgW, (double) h / imgH);
            int drawW = (int) Math.round(imgW * scale);
            int drawH = (int) Math.round(imgH * scale);
            int drawX = (w - drawW) / 2;
            int drawY = (h - drawH) / 2;
            g.drawImage(backgroundImage, drawX, drawY, drawW, drawH, null);
        } else {
            // Left half: smog-choked industrial skyline. Right half: restored green world.
            g.setPaint(new GradientPaint(0, 0, new Color(60, 60, 65), 0, h, new Color(20, 40, 35)));
            g.fillRect(0, 0, w / 2, h);
            g.setPaint(new GradientPaint(w / 2f, 0, Constants.COLOR_SKY_BLUE, w / 2f, h, new Color(120, 200, 120)));
            g.fillRect(w / 2, 0, w / 2, h);
        }
    }

    private void drawTopHud(Graphics2D g, int w) {
        drawPanel(g, 40, 20, 240, 40);
        g.setFont(Constants.FONT_HUD);
        g.setColor(Constants.COLOR_GOLD);
        g.drawString("HI-SCORE", 56, 46);
        g.setColor(Color.WHITE);
        g.drawString(String.format("%03d,%03d", hiScore / 1000, hiScore % 1000), 156, 46);

        String onlineText = "ONLINE WORLD  \u2022  SERVER: ECO-01";
        g.setFont(Constants.FONT_SMALL);
        FontMetrics fmSmall = g.getFontMetrics();
        int onlineWidth = fmSmall.stringWidth(onlineText) + 30;
        drawPanel(g, w - 470, 20, onlineWidth, 32);
        g.setColor(Constants.COLOR_TEAL);
        g.drawString(onlineText, w - 455, 41);

        drawPanel(g, w - 190, 20, 150, 32);
        g.setColor(Constants.COLOR_TEXT_LIGHT);
        g.drawString(Constants.VERSION, w - 175, 41);
    }

    private void drawTitle(Graphics2D g, int w) {
        g.setFont(Constants.FONT_TITLE);
        FontMetrics fm = g.getFontMetrics();
        String eco = "ECO ";
        String quest = "QUEST";
        int totalWidth = fm.stringWidth(eco + quest);
        int startX = (w - totalWidth) / 2;
        int baseY = 195;

        drawOutlinedText(g, eco, startX, baseY, Constants.COLOR_TEAL);
        drawOutlinedText(g, quest, startX + fm.stringWidth(eco), baseY, Constants.COLOR_GOLD);
    }

    private void drawOutlinedText(Graphics2D g, String text, int x, int y, Color fill) {
        g.setColor(Color.BLACK);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                if (dx != 0 || dy != 0) g.drawString(text, x + dx, y + dy);
            }
        }
        g.setColor(fill);
        g.drawString(text, x, y);
    }

    private void drawTagline(Graphics2D g, int w) {
        String tagline = "\u2605 RESTORE THE ENVIRONMENTAL ODYSSEY \u2605";
        g.setFont(Constants.FONT_TAGLINE);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(tagline) + 40;
        int tx = (w - tw) / 2;
        int ty = 225;
        g.setColor(Constants.COLOR_CREAM);
        g.fillRoundRect(tx, ty, tw, 32, 4, 4);
        g.setColor(Constants.COLOR_TEAL_DARK);
        g.drawRoundRect(tx, ty, tw, 32, 4, 4);
        g.setColor(new Color(60, 50, 10));
        g.drawString(tagline, tx + 20, ty + 22);
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

            g.setFont(Constants.FONT_MENU);
            g.setColor(Constants.COLOR_TEXT_LIGHT);
            g.drawString(item.label, startX + 30, by + 34);

            if (item.badge != null) {
                g.setFont(Constants.FONT_SMALL);
                FontMetrics fm = g.getFontMetrics();
                int bw = fm.stringWidth(item.badge) + 20;
                int bx = startX + BTN_WIDTH - bw - 16;
                int badgeY = by + 14;
                g.setColor(item.badgeColor);
                g.fillRoundRect(bx, badgeY, bw, 26, 4, 4);
                g.setColor(Color.BLACK);
                g.drawString(item.badge, bx + 10, badgeY + 18);
            }
        }
    }

    private void drawBottomBar(Graphics2D g, int w, int h) {
        int barY = h - 50;
        g.setColor(Constants.COLOR_PANEL_NAVY);
        g.fillRect(0, barY, w, 50);
        g.setFont(Constants.FONT_SMALL);

        g.setColor(Constants.COLOR_TEXT_DIM);
        g.drawString("CLEANLINESS LEVEL:", 40, barY + 30);
        int barX = 230;
        int barW = 180;
        int barH = 14;
        g.setColor(new Color(10, 15, 20));
        g.fillRect(barX, barY + 18, barW, barH);
        g.setColor(Constants.COLOR_GOLD);
        g.fillRect(barX, barY + 18, (int) (barW * 0.35), barH);
        g.setColor(Constants.COLOR_PANEL_BORDER);
        g.drawRect(barX, barY + 18, barW, barH);

        String hint = "\u25CF PRESS [SPACE] OR CLICK TO SELECT  \u2022  [ESC] BACK";
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Constants.COLOR_TEXT_DIM);
        g.drawString(hint, (w - fm.stringWidth(hint)) / 2, barY + 30);

        String bgm = "\u266A BGM: ON [16-BIT CHIPTUNE]";
        g.setColor(Constants.COLOR_TEAL);
        g.drawString(bgm, w - fm.stringWidth(bgm) - 40, barY + 30);
    }

    private void drawPanel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Constants.COLOR_PANEL_NAVY);
        g.fillRoundRect(x, y, w, h, 4, 4);
        g.setColor(Constants.COLOR_PANEL_BORDER);
        g.drawRoundRect(x, y, w, h, 4, 4);
    }

    public int getSelectedIndex() { return selectedIndex; }
}
