package com.ecoquest.ui;

import com.ecoquest.core.CleanerEconomy;
import com.ecoquest.core.InputHandler;
import com.ecoquest.core.ProgressStore;
import com.ecoquest.util.Constants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;

public final class ShopManager {
    public interface Listener { String purchaseCleaner(int tier); void closeShop(); }
    private int selected;
    private String status = "";
    private double statusTime;
    private boolean closeHovered;

    public void handleInput(InputHandler input, int width, int height, Listener listener) {
        statusTime = Math.max(0, statusTime - 1.0 / 60.0);
        if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE) || input.isKeyJustPressed(KeyEvent.VK_BACK_SPACE)) { listener.closeShop(); return; }
        if (input.isKeyJustPressed(KeyEvent.VK_UP) || input.isKeyJustPressed(KeyEvent.VK_LEFT)) selected = 0;
        if (input.isKeyJustPressed(KeyEvent.VK_DOWN) || input.isKeyJustPressed(KeyEvent.VK_RIGHT)) selected = 1;
        if (input.isKeyJustPressed(KeyEvent.VK_1) || input.isKeyJustPressed(KeyEvent.VK_2)) {
            selected = input.isKeyJustPressed(KeyEvent.VK_1) ? 0 : 1;
            buy(listener);
        } else if (input.isKeyJustPressed(KeyEvent.VK_ENTER) || input.isKeyJustPressed(KeyEvent.VK_SPACE)) buy(listener);
        Rectangle[] cards = cards(width, height);
        int mx = input.getMouseX(), my = input.getMouseY();
        closeHovered = closeButton(width, height).contains(mx, my);
        for (int i = 0; i < cards.length; i++) if (cards[i].contains(mx, my)) selected = i;
        Point click = input.consumeMouseClick();
        if (click != null) {
            if (closeButton(width, height).contains(click)) listener.closeShop();
            for (int i = 0; i < cards.length; i++) if (cards[i].contains(click)) { selected = i; buy(listener); }
        }
    }

    private void buy(Listener listener) {
        status = listener.purchaseCleaner(selected);
        statusTime = 3.2;
    }

    public void render(Graphics2D g, int width, int height, ProgressStore profile) {
        g.setColor(new Color(4, 8, 15, 210)); g.fillRect(0, 0, width, height);
        int panelW = 790, panelH = 500, x = (width - panelW) / 2, y = (height - panelH) / 2;
        panel(g, x, y, panelW, panelH);
        g.setFont(new Font("Monospaced", Font.BOLD, 32)); g.setColor(Constants.COLOR_CREAM);
        g.drawString("ECO MARKET", x + 32, y + 48);
        g.setFont(Constants.FONT_HUD); g.setColor(Constants.COLOR_GOLD);
        g.drawString("SHARED COINS: " + profile.getCoins(), x + 34, y + 82);
        Rectangle[] cards = cards(width, height);
        drawCard(g, cards[0], selected == 0, "SUN GRABBER", "Wider pickup + quicker movement", "Capacity: 7   Pickup: 19px", CleanerEconomy.SUN_GRABBER_COST, profile.hasSunGrabber());
        drawCard(g, cards[1], selected == 1, "SUN GRABBER + VACUUM PACK", "Combined high-power cleaner", "Capacity: 10   Pickup: 28px", CleanerEconomy.vacuumPackCost(), profile.hasVacuumPack());
        g.setFont(Constants.FONT_SMALL); g.setColor(Constants.COLOR_TEXT_LIGHT);
        centered(g, "1 / 2 or arrows + ENTER to buy     |     Click a card     |     BACKSPACE or click BACK", width, y + 450);
        if (statusTime > 0 && !status.isBlank()) {
            g.setFont(Constants.FONT_HUD); g.setColor(Constants.COLOR_GOLD);
            centered(g, status, width, y + panelH + 24);
        }
        Rectangle close = closeButton(width, height);
        g.setColor(closeHovered ? Constants.COLOR_TEAL_DARK : Constants.COLOR_PANEL_NAVY); g.fillRoundRect(close.x, close.y, close.width, close.height, 8, 8);
        g.setColor(closeHovered ? Constants.COLOR_TEAL : Constants.COLOR_CREAM); g.drawRoundRect(close.x, close.y, close.width, close.height, 8, 8);
        g.drawString("BACK  [BACKSPACE]", close.x + 15, close.y + 25);
    }

    private void drawCard(Graphics2D g, Rectangle r, boolean active, String name, String detail, String buff, int price, boolean owned) {
        g.setColor(active ? new Color(29, 90, 83) : new Color(26, 36, 48)); g.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);
        g.setColor(active ? Constants.COLOR_TEAL : Constants.COLOR_PANEL_BORDER); g.setStroke(new BasicStroke(active ? 3 : 1));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 12, 12); g.setStroke(new BasicStroke(1));
        g.setFont(new Font("Monospaced", Font.BOLD, 16)); g.setColor(Constants.COLOR_CREAM); g.drawString(name, r.x + 20, r.y + 38);
        g.setFont(Constants.FONT_SMALL); g.setColor(Constants.COLOR_TEXT_LIGHT); g.drawString(detail, r.x + 20, r.y + 78);
        g.drawString(buff, r.x + 20, r.y + 106);
        g.setFont(Constants.FONT_HUD); g.setColor(Constants.COLOR_GOLD); g.drawString(owned ? "OWNED" : "₱ " + price, r.x + 20, r.y + 150);
        g.setFont(Constants.FONT_SMALL); g.setColor(Constants.COLOR_TEXT_DIM); g.drawString(owned ? "Already in your shared inventory" : "Available in every stage", r.x + 20, r.y + 176);
        // Anthony's note: reserved cleaner-art slot, ready for his supplied icon.
        int ix = r.x + r.width - 76, iy = r.y + 50;
        g.setColor(new Color(13, 22, 29)); g.fillRoundRect(ix, iy, 60, 60, 8, 8);
        g.setColor(new Color(87, 113, 120)); g.drawRoundRect(ix, iy, 60, 60, 8, 8);
        g.setFont(new Font("Monospaced", Font.PLAIN, 8)); g.setColor(Constants.COLOR_TEXT_DIM);
        g.drawString("ADD", ix + 18, iy + 26); g.drawString("ICON", ix + 15, iy + 39);
    }

    private Rectangle[] cards(int width, int height) {
        int x = (width - 790) / 2, y = (height - 500) / 2;
        return new Rectangle[]{new Rectangle(x + 28, y + 108, 350, 205), new Rectangle(x + 412, y + 108, 350, 205)};
    }

    private Rectangle closeButton(int width, int height) { return new Rectangle((width - 150) / 2, (height + 500) / 2 + 28, 150, 36); }
    private void panel(Graphics2D g, int x, int y, int w, int h) { g.setColor(Constants.COLOR_PANEL_NAVY); g.fillRoundRect(x, y, w, h, 14, 14); g.setColor(Constants.COLOR_PANEL_BORDER); g.drawRoundRect(x, y, w, h, 14, 14); }
    private void centered(Graphics2D g, String text, int width, int y) { FontMetrics fm = g.getFontMetrics(); g.drawString(text, (width - fm.stringWidth(text)) / 2, y); }
}
