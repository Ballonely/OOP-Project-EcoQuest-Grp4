package ecoquest;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Main menu: top badges, outlined title, tagline ribbon, four buttons, bottom status bar. */
final class MenuScreen {
    interface Listener {
        void onStartGame();

        void onOpenShop();

        void onOpenSettings();

        void onQuit();
    }

    private record Item(String label, String badge, Color badgeColor) {
    }

    private static final int BTN_WIDTH = 390, BTN_HEIGHT = 56, BTN_GAP = 14, MENU_START_Y = 300;

    private final List<Item> items = new ArrayList<>();
    private final Listener listener;
    private int hiScore;
    private int selected;
    private final BufferedImage backdrop = loadBackdrop();
    private String notice = "";
    private double noticeTimer;

    MenuScreen(Listener listener, int hiScore) {
        this.listener = listener;
        this.hiScore = hiScore;
        items.add(new Item("START GAME", "READY", Theme.TEAL));
        items.add(new Item("SHOP", "3 NEW", Theme.GOLD));
        items.add(new Item("SETTINGS & AUDIO", null, null));
        items.add(new Item("QUIT TO DESKTOP", null, null));
    }

    private static BufferedImage loadBackdrop() {
        try {
            return ImageIO.read(Res.find("assets/menu_bg.jpg").toFile());
        } catch (IOException | RuntimeException e) {
            return null; // falls back to the gradient backdrop
        }
    }

    void setHiScore(int v) {
        hiScore = v;
    }

    void showNotice(String text) {
        notice = text;
        noticeTimer = 2.5;
    }

    void update(double dt) {
        noticeTimer = Math.max(0, noticeTimer - dt);
    }

    void handleInput(InputHandler in, int panelWidth) {
        if (in.isKeyJustPressed(KeyEvent.VK_DOWN)) selected = (selected + 1) % items.size();
        if (in.isKeyJustPressed(KeyEvent.VK_UP)) selected = (selected - 1 + items.size()) % items.size();
        if (in.isKeyJustPressed(KeyEvent.VK_SPACE) || in.isKeyJustPressed(KeyEvent.VK_ENTER)) activate(selected);
        Point click = in.consumeMouseClick();
        if (click != null) {
            int sx = (panelWidth - BTN_WIDTH) / 2;
            for (int i = 0; i < items.size(); i++) {
                int by = MENU_START_Y + i * (BTN_HEIGHT + BTN_GAP);
                if (click.x >= sx && click.x <= sx + BTN_WIDTH && click.y >= by && click.y <= by + BTN_HEIGHT) {
                    selected = i;
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
            default -> {
            }
        }
    }

    void render(Graphics2D g, int w, int h) {
        drawBackdrop(g, w, h);
        drawTopHud(g, w);
        drawTitle(g, w);
        drawTagline(g, w);
        drawButtons(g, w);
        if (noticeTimer > 0) {
            g.setFont(Theme.FONT_HUD);
            FontMetrics fm = g.getFontMetrics();
            int ty = MENU_START_Y + items.size() * (BTN_HEIGHT + BTN_GAP) + 4;
            int tw = fm.stringWidth(notice) + 30;
            panel(g, (w - tw) / 2, ty, tw, 32);
            g.setColor(Theme.GOLD);
            g.drawString(notice, (w - fm.stringWidth(notice)) / 2, ty + 22);
        }
        drawBottomBar(g, w, h);
    }

    private void drawBackdrop(Graphics2D g, int w, int h) {
        if (backdrop != null) {
            double scale = Math.max((double) w / backdrop.getWidth(), (double) h / backdrop.getHeight());
            int dw = (int) Math.round(backdrop.getWidth() * scale), dh = (int) Math.round(backdrop.getHeight() * scale);
            g.drawImage(backdrop, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        } else {
            g.setPaint(new GradientPaint(0, 0, new Color(60, 60, 65), 0, h, new Color(20, 40, 35)));
            g.fillRect(0, 0, w / 2, h);
            g.setPaint(new GradientPaint(w / 2f, 0, Theme.SKY_BLUE, w / 2f, h, new Color(120, 200, 120)));
            g.fillRect(w / 2, 0, w / 2, h);
        }
    }

    private void drawTopHud(Graphics2D g, int w) {
        panel(g, 40, 20, 240, 40);
        g.setFont(Theme.FONT_HUD);
        g.setColor(Theme.GOLD);
        g.drawString("HI-SCORE", 56, 46);
        g.setColor(Color.WHITE);
        g.drawString(String.format("%03d,%03d", hiScore / 1000, hiScore % 1000), 156, 46);

        String online = "ONLINE WORLD  •  SERVER: ECO-01";
        g.setFont(Theme.FONT_SMALL);
        int ow = g.getFontMetrics().stringWidth(online) + 30;
        panel(g, w - 470, 20, ow, 32);
        g.setColor(Theme.TEAL);
        g.drawString(online, w - 455, 41);

        panel(g, w - 190, 20, 150, 32);
        g.setColor(Theme.TEXT_LIGHT);
        g.drawString(Theme.VERSION, w - 175, 41);
    }

    private void drawTitle(Graphics2D g, int w) {
        g.setFont(Theme.FONT_TITLE);
        FontMetrics fm = g.getFontMetrics();
        String eco = "ECO ", quest = "QUEST";
        int startX = (w - fm.stringWidth(eco + quest)) / 2, baseY = 195;
        outlined(g, eco, startX, baseY, Theme.TEAL);
        outlined(g, quest, startX + fm.stringWidth(eco), baseY, Theme.GOLD);
    }

    private void outlined(Graphics2D g, String text, int x, int y, Color fill) {
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
        String tagline = "★ RESTORE THE ENVIRONMENTAL ODYSSEY ★";
        g.setFont(Theme.FONT_TAGLINE);
        int tw = g.getFontMetrics().stringWidth(tagline) + 40, tx = (w - tw) / 2, ty = 225;
        g.setColor(Theme.CREAM);
        g.fillRoundRect(tx, ty, tw, 32, 4, 4);
        g.setColor(Theme.TEAL_DARK);
        g.drawRoundRect(tx, ty, tw, 32, 4, 4);
        g.setColor(new Color(60, 50, 10));
        g.drawString(tagline, tx + 20, ty + 22);
    }

    private void drawButtons(Graphics2D g, int w) {
        int sx = (w - BTN_WIDTH) / 2;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int by = MENU_START_Y + i * (BTN_HEIGHT + BTN_GAP);
            boolean sel = i == selected;
            g.setColor(sel ? Theme.TEAL_DARK : Theme.PANEL_NAVY);
            g.fillRoundRect(sx, by, BTN_WIDTH, BTN_HEIGHT, 6, 6);
            g.setColor(sel ? Theme.TEAL : Theme.PANEL_BORDER);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(sx, by, BTN_WIDTH, BTN_HEIGHT, 6, 6);
            g.setStroke(new BasicStroke(1));

            g.setFont(Theme.FONT_MENU);
            g.setColor(Theme.TEXT_LIGHT);
            g.drawString(item.label(), sx + 30, by + 34);

            if (item.badge() != null) {
                g.setFont(Theme.FONT_SMALL);
                int bw = g.getFontMetrics().stringWidth(item.badge()) + 20;
                int bx = sx + BTN_WIDTH - bw - 16, byy = by + 14;
                g.setColor(item.badgeColor());
                g.fillRoundRect(bx, byy, bw, 26, 4, 4);
                g.setColor(Color.BLACK);
                g.drawString(item.badge(), bx + 10, byy + 18);
            }
        }
    }

    private void drawBottomBar(Graphics2D g, int w, int h) {
        int barY = h - 50;
        g.setColor(Theme.PANEL_NAVY);
        g.fillRect(0, barY, w, 50);
        g.setFont(Theme.FONT_SMALL);
        g.setColor(Theme.TEXT_DIM);
        g.drawString("CLEANLINESS LEVEL:", 40, barY + 30);
        g.setColor(new Color(10, 15, 20));
        g.fillRect(230, barY + 18, 180, 14);
        g.setColor(Theme.GOLD);
        g.fillRect(230, barY + 18, (int) (180 * 0.35), 14);
        g.setColor(Theme.PANEL_BORDER);
        g.drawRect(230, barY + 18, 180, 14);

        FontMetrics fm = g.getFontMetrics();
        String hint = "● PRESS [SPACE] OR CLICK TO SELECT  •  [ESC] BACK";
        g.setColor(Theme.TEXT_DIM);
        g.drawString(hint, (w - fm.stringWidth(hint)) / 2, barY + 30);
        String bgm = "♪ BGM: ON [16-BIT CHIPTUNE]";
        g.setColor(Theme.TEAL);
        g.drawString(bgm, w - fm.stringWidth(bgm) - 40, barY + 30);
    }

    static void panel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Theme.PANEL_NAVY);
        g.fillRoundRect(x, y, w, h, 4, 4);
        g.setColor(Theme.PANEL_BORDER);
        g.drawRoundRect(x, y, w, h, 4, 4);
    }
}
