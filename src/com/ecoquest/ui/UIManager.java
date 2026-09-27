package com.ecoquest.ui;

import com.ecoquest.entities.Player;
import com.ecoquest.util.Constants;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * Draws every in-game HUD element (score, health, cleanliness) plus the
 * Pause and Game Over overlays. Knows nothing about game rules or update
 * logic; it only reads state off the entities it is given.
 */
public class UIManager {

    public void renderHud(Graphics2D g, Player player, int panelWidth) {
        // Score panel (top-left)
        drawPanel(g, 20, 16, 200, 36);
        g.setFont(Constants.FONT_HUD);
        g.setColor(Constants.COLOR_GOLD);
        g.drawString("SCORE", 34, 40);
        g.setColor(Color.WHITE);
        g.drawString(String.valueOf(player.getScore()), 120, 40);

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

    public void renderGameOver(Graphics2D g, int w, int h, int finalScore) {
        g.setColor(new Color(10, 5, 5, 200));
        g.fillRect(0, 0, w, h);
        g.setFont(Constants.FONT_TITLE.deriveFont(52f));
        g.setColor(Constants.COLOR_HEALTH_RED);
        centerText(g, "GAME OVER", w, h / 2 - 40);
        g.setFont(Constants.FONT_MENU);
        g.setColor(Constants.COLOR_GOLD);
        centerText(g, "FINAL SCORE: " + finalScore, w, h / 2 + 10);
        g.setColor(Constants.COLOR_TEXT_LIGHT);
        centerText(g, "PRESS [SPACE] TO RETURN TO MENU", w, h / 2 + 50);
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
