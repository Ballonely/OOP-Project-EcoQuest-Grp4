package com.ecoquest.ui;

import com.ecoquest.data.LevelProgress;
import com.ecoquest.entities.Player;
import com.ecoquest.util.Constants;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws every in-game HUD element (score, health, cleanliness) plus the
 * Pause and Game Over overlays. Knows nothing about game rules or update
 * logic; it only reads state off the entities it is given.
 */
public class UIManager {

    public void renderHud(Graphics2D g, Player player, int panelWidth, String levelName) {
        // Score panel (top-left)
        drawPanel(g, 20, 16, 200, 36);
        g.setFont(Constants.FONT_HUD);
        g.setColor(Constants.COLOR_GOLD);
        g.drawString("SCORE", 34, 40);
        g.setColor(Color.WHITE);
        g.drawString(String.valueOf(player.getScore()), 120, 40);

        g.setFont(Constants.FONT_SMALL);
        g.setColor(Constants.COLOR_TEXT_LIGHT);
        String levelLabel = "AREA: " + levelName;
        int levelLabelWidth = g.getFontMetrics().stringWidth(levelLabel);
        g.drawString(levelLabel, (panelWidth - levelLabelWidth) / 2, 40);

        // Health bar (top-right)
        int barW = 220;
        int barH = 22;
        int barX = panelWidth - barW - 20;
        int barY = 16;
        drawPanel(g, barX - 10, barY - 6, barW + 20, barH + 12);
        g.setColor(new Color(10, 15, 20));
        g.fillRect(barX, barY, barW, barH);
        double pct = player.getHealth() / (double) player.getMaxHealth();
        g.setColor(Constants.COLOR_HEALTH_RED);
        g.fillRect(barX, barY, (int) (barW * pct), barH);
        g.setColor(Constants.COLOR_PANEL_BORDER);
        g.drawRect(barX, barY, barW, barH);
        g.setColor(Color.WHITE);
        g.setFont(Constants.FONT_SMALL);
        g.drawString("HP " + player.getHealth() + "/" + player.getMaxHealth(), barX + 6, barY + 16);

        // Cleanliness meter (bottom-left, mirrors the menu screen's bar)
        int by = 720;
        g.setColor(Constants.COLOR_TEXT_DIM);
        g.drawString("CLEANLINESS LEVEL:", 40, by);
        int cbW = 200;
        int cbH = 14;
        g.setColor(new Color(10, 15, 20));
        g.fillRect(230, by - 12, cbW, cbH);
        g.setColor(Constants.COLOR_TEAL);
        g.fillRect(230, by - 12, (int) (cbW * (player.getCleanlinessLevel() / 100.0)), cbH);
        g.setColor(Constants.COLOR_PANEL_BORDER);
        g.drawRect(230, by - 12, cbW, cbH);
    }

    public void renderPauseOverlay(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, w, h);
        g.setFont(Constants.FONT_TITLE.deriveFont(48f));
        g.setColor(Constants.COLOR_TEAL);
        centerText(g, "PAUSED", w, h / 2 - 20);
        g.setFont(Constants.FONT_MENU);
        g.setColor(Constants.COLOR_TEXT_LIGHT);
        centerText(g, "PRESS [P] TO RESUME  \u2022  [ESC] MAIN MENU", w, h / 2 + 30);
    }

    // ------------------------------------------------------------------ results screen
    // Laid out on a 1024x576 design canvas (scaled to fit) in the same pixel type as the main menu.

    private static final int DESIGN_W = 1024, DESIGN_H = 576;
    private static final int PANEL_X = 222, PANEL_Y = 22, PANEL_W = 580, PANEL_H = 476;
    private static final int ROW_X = PANEL_X + 40, ROW_W = PANEL_W - 80;
    private static final int S = 2;                                   // pixel-font scale for body text
    private static final int TEXT_H = PixelFont.GLYPH_H * S;
    private static final int MAX_CHARS = (ROW_W + 2) / (6 * S);       // chars that fit one body row

    private static final Color WIN_ACCENT = new Color(255, 204, 40);
    private static final Color LOSE_ACCENT = new Color(255, 86, 100);
    private static final Color PANEL_FILL = new Color(22, 31, 46);
    private static final Color SHADOW = new Color(10, 16, 26);

    private static final PixelFont.Style TITLE_WIN = new PixelFont.Style(
            new Color(255, 238, 118), new Color(244, 146, 36), new Color(255, 252, 200),
            new Color(140, 72, 22), 2, new Color(40, 20, 8), true);
    private static final PixelFont.Style TITLE_LOSE = new PixelFont.Style(
            new Color(255, 150, 150), new Color(220, 48, 66), new Color(255, 214, 214),
            new Color(110, 20, 32), 2, new Color(36, 8, 12), true);
    private static final PixelFont.Style TXT_LIGHT = flat(Constants.COLOR_TEXT_LIGHT, false);
    private static final PixelFont.Style TXT_DIM = flat(Constants.COLOR_TEXT_DIM, false);
    private static final PixelFont.Style TXT_GOLD = flat(Constants.COLOR_GOLD, false);
    private static final PixelFont.Style TXT_TEAL = flat(Constants.COLOR_TEAL, false);
    private static final PixelFont.Style TXT_WHITE = flat(Color.WHITE, false);
    private static final PixelFont.Style TXT_RED = flat(new Color(255, 140, 150), false);
    private static final PixelFont.Style BTN = flat(new Color(236, 240, 228), true);
    private static final PixelFont.Style BTN_SELECTED = flat(new Color(255, 255, 240), true);

    private static PixelFont.Style flat(Color c, boolean bold) {
        return new PixelFont.Style(c, c, null, SHADOW, 1, null, bold);
    }

    /**
     * Win / lose screen shown at the end of every level.
     *
     * @param coinsEarned all coins gained this run (sorting rewards plus the level bonus)
     * @param levelBonus  the clear bonus included in {@code coinsEarned} (0 on a loss)
     */
    public void renderOutcome(Graphics2D g, int w, int h, boolean won,
            String levelName, int finalScore, int coinsEarned, int levelBonus,
            int cleanliness, String message, int selectedAction) {
        double scale = Math.min(w / (double) DESIGN_W, h / (double) DESIGN_H);
        Graphics2D o = (Graphics2D) g.create();
        o.setColor(new Color(0, 0, 0, 190));
        o.fillRect(0, 0, w, h);
        o.translate((w - DESIGN_W * scale) / 2, (h - DESIGN_H * scale) / 2);
        o.scale(scale, scale);

        Color accent = won ? WIN_ACCENT : LOSE_ACCENT;
        drawResultPanel(o, accent);

        // Title
        String title = won ? "YOU WIN!" : "YOU LOSE!";
        int titleScale = 6;
        PixelFont.draw(o, title, (DESIGN_W - PixelFont.width(title, titleScale, true)) / 2,
                PANEL_Y + 40, titleScale, won ? TITLE_WIN : TITLE_LOSE);

        int clean = Math.max(0, Math.min(100, cleanliness));
        int y;
        if (won) {
            drawStars(o, LevelProgress.starsFor(clean), PANEL_Y + 122);
            y = PANEL_Y + 204;
        } else {
            y = PANEL_Y + 116;
            for (String line : wrap(message.toUpperCase(), MAX_CHARS)) {
                centerPixel(o, line, y, TXT_RED);
                y += TEXT_H + 8;
            }
            y = Math.max(y + 14, PANEL_Y + 160);
        }

        int collected = Math.max(0, coinsEarned - levelBonus);
        drawStatRow(o, "LEVEL", levelName.toUpperCase(), y, TXT_LIGHT);
        drawStatRow(o, "SCORE", String.valueOf(finalScore), y += 28, TXT_LIGHT);
        drawStatRow(o, "COINS COLLECTED", "\u20B1" + collected, y += 28, TXT_GOLD);
        if (won) {
            drawStatRow(o, "LEVEL BONUS", "+\u20B1" + levelBonus, y += 28, TXT_TEAL);
            drawStatRow(o, "TOTAL EARNED", "\u20B1" + coinsEarned, y += 28, TXT_GOLD);
        }
        drawStatRow(o, "CLEANLINESS", clean + "%", y += 28, TXT_WHITE);
        drawCleanlinessBar(o, clean, y + TEXT_H + 8);

        if (!won) drawTip(o, tipFor(levelName), y + TEXT_H + 34);

        String[] buttons = won ? new String[] {"NEXT LEVEL", "RETRY", "MENU"} : new String[] {"TRY AGAIN", "MENU"};
        drawResultButtons(o, buttons, selectedAction, PANEL_Y + PANEL_H - 66);

        String hint = "< > SELECT   ENTER CONFIRM   R RETRY   ESC MENU";
        centerPixel(o, hint, PANEL_Y + PANEL_H + 16, TXT_DIM);
        o.dispose();
    }

    private void drawResultPanel(Graphics2D g, Color accent) {
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(PANEL_X + 6, PANEL_Y + 6, PANEL_W, PANEL_H, 12, 12);
        g.setColor(PANEL_FILL);
        g.fillRoundRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H, 12, 12);
        g.setStroke(new BasicStroke(3));
        g.setColor(accent);
        g.drawRoundRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H, 12, 12);
        g.setStroke(new BasicStroke(1));
        g.setColor(Constants.COLOR_PANEL_BORDER);
        g.drawRoundRect(PANEL_X + 7, PANEL_Y + 7, PANEL_W - 14, PANEL_H - 14, 8, 8);
        g.setColor(accent);
        g.fillRect(PANEL_X + 24, PANEL_Y + 18, PANEL_W - 48, 3);
    }

    /** Earned stars in gold, the rest greyed out (1 star = clear, 2 = 75%+, 3 = 95%+ clean). */
    private void drawStars(Graphics2D g, int earned, int y) {
        int sc = 5;
        int starW = PixelFont.STAR.w() * sc, gap = 18;
        int x = (DESIGN_W - (3 * starW + 2 * gap)) / 2;
        for (int i = 0; i < 3; i++) {
            int sx = x + i * (starW + gap);
            PixelFont.drawIcon(g, PixelFont.STAR_SHADOW, sx + sc, y + sc, sc);
            PixelFont.drawIcon(g, i < earned ? PixelFont.STAR : PixelFont.STAR_EMPTY, sx, y, sc);
        }
    }

    private void drawStatRow(Graphics2D g, String label, String value, int y, PixelFont.Style valueStyle) {
        int labelEnd = ROW_X + PixelFont.width(label, S);
        int valueX = ROW_X + ROW_W - PixelFont.width(value, S);
        PixelFont.draw(g, label, ROW_X, y, S, TXT_LIGHT);
        g.setColor(Constants.COLOR_PANEL_BORDER);
        for (int x = labelEnd + 10; x < valueX - 10; x += 8) g.fillRect(x, y + TEXT_H - 4, 2, 2);
        PixelFont.draw(g, value, valueX, y, S, valueStyle);
    }

    private void drawCleanlinessBar(Graphics2D g, int cleanliness, int y) {
        int h = 12;
        g.setColor(new Color(10, 15, 20));
        g.fillRect(ROW_X, y, ROW_W, h);
        g.setColor(cleanliness >= LevelProgress.TWO_STAR_CLEANLINESS ? Constants.COLOR_TEAL
                : cleanliness >= 40 ? Constants.COLOR_GOLD : Constants.COLOR_HEALTH_RED);
        g.fillRect(ROW_X + 2, y + 2, (ROW_W - 4) * cleanliness / 100, h - 4);
        g.setColor(Constants.COLOR_PANEL_BORDER);
        g.drawRect(ROW_X, y, ROW_W, h);
    }

    private void drawTip(Graphics2D g, String tip, int y) {
        List<String> lines = wrap(tip, MAX_CHARS - 2);
        int boxH = 16 + TEXT_H + 8 + lines.size() * (TEXT_H + 6);
        g.setColor(new Color(48, 25, 31));
        g.fillRoundRect(ROW_X, y, ROW_W, boxH, 6, 6);
        g.setColor(new Color(110, 48, 58));
        g.drawRoundRect(ROW_X, y, ROW_W, boxH, 6, 6);
        PixelFont.draw(g, "TIP", ROW_X + 12, y + 10, S, TXT_GOLD);
        int ly = y + 10 + TEXT_H + 8;
        for (String line : lines) {
            PixelFont.draw(g, line, ROW_X + 12, ly, S, TXT_LIGHT);
            ly += TEXT_H + 6;
        }
    }

    /** Menu-style buttons: navy, the selected one teal with the sprout bullet. */
    private void drawResultButtons(Graphics2D g, String[] buttons, int selected, int y) {
        int gap = 14, bh = 44;
        int bw = (ROW_W - gap * (buttons.length - 1)) / buttons.length;
        for (int i = 0; i < buttons.length; i++) {
            int bx = ROW_X + i * (bw + gap);
            boolean sel = i == selected;
            g.setColor(sel ? Constants.COLOR_TEAL_DARK : Constants.COLOR_PANEL_NAVY);
            g.fillRoundRect(bx, y, bw, bh, 6, 6);
            g.setStroke(new BasicStroke(2));
            g.setColor(sel ? Constants.COLOR_TEAL : Constants.COLOR_PANEL_BORDER);
            g.drawRoundRect(bx, y, bw, bh, 6, 6);
            g.setStroke(new BasicStroke(1));

            int textW = PixelFont.width(buttons[i], S, true);
            int sproutW = PixelFont.SPROUT.w() * S;
            boolean sprout = sel && sproutW + 8 + textW + 20 <= bw;   // bullet only when it fits
            int contentW = sprout ? sproutW + 8 + textW : textW;
            int cx = bx + (bw - contentW) / 2;
            int ty = y + (bh - TEXT_H) / 2;
            if (sprout) {
                PixelFont.drawIcon(g, PixelFont.SPROUT, cx, y + (bh - PixelFont.SPROUT.h() * S) / 2, S);
                cx += sproutW + 8;
            }
            PixelFont.draw(g, buttons[i], cx, ty, S, sel ? BTN_SELECTED : BTN);
        }
    }

    private void centerPixel(Graphics2D g, String text, int y, PixelFont.Style style) {
        PixelFont.draw(g, text, (DESIGN_W - PixelFont.width(text, S)) / 2, y, S, style);
    }

    /** Word-wraps upper-case pixel text to at most {@code maxChars} per line. */
    private static List<String> wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > maxChars) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /** A sorting tip that fits the level the player just failed. */
    private static String tipFor(String levelName) {
        String n = levelName.toUpperCase();
        if (n.contains("PARK")) return "GREEN BIN = BIODEGRADABLE: LEAVES, FRUIT PEELS AND FOOD SCRAPS.";
        if (n.contains("MARKET")) return "GRAY BIN = NON-RECYCLABLE: WRAPPERS, STYROFOAM AND CHIP BAGS.";
        if (n.contains("BEACH")) return "RED BIN = HAZARDOUS: BATTERIES, BROKEN GLASS AND CHEMICALS.";
        if (n.contains("CITY")) return "A WRONG BIN COSTS 1 HP. READ THE ITEM TIP BEFORE YOU SORT.";
        return "BLUE BIN = RECYCLABLE: CANS, PLASTIC BOTTLES AND PAPER.";
    }

    private void centerText(Graphics2D g, String text, int w, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (w - fm.stringWidth(text)) / 2, y);
    }

    private void drawPanel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Constants.COLOR_PANEL_NAVY);
        g.fillRoundRect(x, y, w, h, 4, 4);
        g.setColor(Constants.COLOR_PANEL_BORDER);
        g.drawRoundRect(x, y, w, h, 4, 4);
    }
}
