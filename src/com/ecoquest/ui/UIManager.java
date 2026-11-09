package com.ecoquest.ui;

import com.ecoquest.entities.Player;
import com.ecoquest.util.Constants;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

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

    public void renderOutcome(Graphics2D g, int w, int h, boolean won,
            String levelName, int finalScore, int moneyEarned, int levelBonus,
            int cityHealth, String message, int selectedAction) {
        double scale = Math.min(w / 1024.0, h / 576.0);
        Graphics2D overlay = (Graphics2D) g.create();
        overlay.setColor(new Color(0, 0, 0, 190));
        overlay.fillRect(0, 0, w, h);
        overlay.translate((w - 1024 * scale) / 2, (h - 576 * scale) / 2);
        overlay.scale(scale, scale);

        int panelX = 216, panelY = 72, panelW = 592, panelH = 432;
        Color border = won ? new Color(255, 205, 48) : new Color(255, 82, 99);
        Color panel = won ? new Color(27, 73, 52) : new Color(67, 37, 43);
        overlay.setColor(new Color(0, 0, 0, 150));
        overlay.fillRect(panelX + 6, panelY + 6, panelW, panelH);
        overlay.setColor(border);
        overlay.fillRect(panelX, panelY, panelW, panelH);
        overlay.setColor(new Color(20, 23, 24));
        overlay.fillRect(panelX + 5, panelY + 5, panelW - 10, panelH - 10);
        overlay.setColor(panel);
        overlay.fillRect(panelX + 10, panelY + 10, panelW - 20, panelH - 20);
        overlay.setColor(border);
        overlay.fillRect(panelX + 18, panelY + 18, panelW - 36, 3);

        overlay.setFont(new Font(Font.MONOSPACED, Font.BOLD, 58));
        overlay.setColor(new Color(20, 20, 20));
        centerText(overlay, won ? "YOU WIN!" : "FAILED", 1024, 163);
        overlay.setColor(won ? new Color(255, 205, 48) : new Color(255, 85, 100));
        centerText(overlay, won ? "YOU WIN!" : "FAILED", 1024, 157);

        if (won) {
            overlay.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 43));
            overlay.setColor(new Color(255, 204, 56));
            centerText(overlay, "\u2605  \u2605  \u2605", 1024, 231);
            drawStatRow(overlay, "Level", levelName, 272);
            drawStatRow(overlay, "Score", String.valueOf(finalScore), 303);
            drawStatRow(overlay, "Money earned", "\u20B1" + moneyEarned, 333);
            drawStatRow(overlay, "Level bonus", "+\u20B1" + levelBonus, 363);
            drawStatRow(overlay, "City Health", cityHealth + "%", 393);
            drawHealthBar(overlay, cityHealth, 281, 400, 462);
            String[] buttons = {"NEXT LEVEL", "RETRY", "MENU"};
            drawResultButtons(overlay, buttons, selectedAction, 258, 435);
        } else {
            overlay.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
            overlay.setColor(Color.WHITE);
            centerText(overlay, message, 1024, 207);
            drawStatRow(overlay, "Level", levelName, 257);
            drawStatRow(overlay, "Score", String.valueOf(finalScore), 287);
            drawStatRow(overlay, "City Health", cityHealth + "%", 317);
            drawHealthBar(overlay, cityHealth, 281, 325, 462);

            overlay.setColor(new Color(48, 25, 31));
            overlay.fillRect(272, 360, 480, 45);
            overlay.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
            overlay.setColor(new Color(255, 205, 48));
            overlay.drawString("TIP", 283, 378);
            overlay.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            overlay.setColor(new Color(238, 230, 227));
            overlay.drawString("Blue bin = recyclable (cans, plastic bottles).", 283, 397);
            drawResultButtons(overlay, new String[] {"TRY AGAIN", "MENU"}, selectedAction, 345, 435);
        }

        overlay.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        overlay.setColor(new Color(205, 211, 199));
        centerText(overlay, "< > SELECT    ENTER CONFIRM    R RETRY    ESC MENU", 1024, 494);
        overlay.dispose();
    }

    private void drawStatRow(Graphics2D g, String label, String value, int y) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        g.setColor(new Color(228, 233, 223));
        g.drawString(label, 282, y);
        int labelEnd = 282 + g.getFontMetrics().stringWidth(label);
        int valueWidth = g.getFontMetrics().stringWidth(value);
        int valueX = 742 - valueWidth;
        g.setColor(new Color(155, 176, 157));
        for (int x = labelEnd + 12; x < valueX - 10; x += 8) g.fillRect(x, y - 5, 2, 2);
        g.setColor(label.equals("City Health") ? Color.WHITE : new Color(228, 233, 223));
        g.drawString(value, valueX, y);
    }

    private void drawHealthBar(Graphics2D g, int cityHealth, int x, int y, int width) {
        g.setColor(new Color(37, 43, 48));
        g.fillRect(x, y, width, 15);
        g.setColor(new Color(38, 42, 43));
        g.drawRect(x, y, width, 15);
        g.setColor(new Color(66, 196, 101));
        g.fillRect(x + 2, y + 2, (width - 3) * Math.max(0, Math.min(100, cityHealth)) / 100, 11);
    }

    private void drawResultButtons(Graphics2D g, String[] buttons, int selected, int x, int y) {
        int buttonW = buttons.length == 3 ? 154 : 158;
        int gap = buttons.length == 3 ? 22 : 24;
        for (int i = 0; i < buttons.length; i++) {
            int bx = x + i * (buttonW + gap);
            Color fill = buttonColor(buttons[i]);
            g.setColor(new Color(12, 14, 17));
            g.fillRect(bx - 3, y - 3, buttonW + 6, 43);
            g.setColor(i == selected ? Color.WHITE : new Color(115, 119, 124));
            g.drawRect(bx - 3, y - 3, buttonW + 6, 43);
            g.setColor(fill);
            g.fillRect(bx, y, buttonW, 37);
            g.setColor(new Color(30, 30, 30));
            g.drawRect(bx, y, buttonW, 37);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
            g.setColor(Color.WHITE);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(buttons[i], bx + (buttonW - metrics.stringWidth(buttons[i])) / 2, y + 24);
            if (i == selected) {
                g.setColor(Color.WHITE);
                g.fillPolygon(new int[] {bx - 13, bx - 4, bx - 13},
                        new int[] {y + 13, y + 19, y + 25}, 3);
            }
        }
    }

    private Color buttonColor(String label) {
        if (label.equals("NEXT LEVEL")) return new Color(39, 186, 98);
        if (label.equals("RETRY") || label.equals("TRY AGAIN")) {
            return label.equals("RETRY") ? new Color(45, 99, 190) : new Color(239, 79, 76);
        }
        return new Color(94, 99, 109);
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
