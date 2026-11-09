package com.ecoquest.level1;

import com.ecoquest.core.InputHandler;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

import static com.ecoquest.level1.Config.*;

/** Level 1 world, interaction rules and HUD, driven by the main game loop. */
public class GamePanel {
    private static final double PICKUP_RANGE = 13, BIN_RANGE = 17;
    private static final int BOSS_SPAWN_CITY_HEALTH = 75;

    private final BufferedImage frame = new BufferedImage(WORLD_W, WORLD_H, BufferedImage.TYPE_INT_ARGB);

    private Level level;
    private Player player;
    private TrashMonster trashMonster;
    private Garbage nearGarbage;
    private TrashBin nearBin;

    private boolean cleared;
    private String toast = "";
    private double toastTime;

    public GamePanel() {
        startLevel();
    }

    private void startLevel() {
        level = new Level1Residential().load();
        player = new Player(level.getSpawnX(), level.getSpawnY());
        trashMonster = null;
        cleared = false;
        showToast("Clean the street! Sort litter into the matching bins.", 4);
    }

    // ------------------------------------------------------------ update
    public void updateFrame(double dt, InputHandler input) {
        toastTime = Math.max(0, toastTime - dt);
        if (input.isKeyJustPressed(KeyEvent.VK_R)) {
            startLevel();
            return;
        }
        if (cleared) return;

        int dx = (isDown(input, KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1 : 0)
                - (isDown(input, KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 1 : 0);
        int dy = (isDown(input, KeyEvent.VK_S, KeyEvent.VK_DOWN) ? 1 : 0)
                - (isDown(input, KeyEvent.VK_W, KeyEvent.VK_UP) ? 1 : 0);
        player.update(dt, dx, dy, level);

        nearGarbage = level.nearestGarbage(player.centerX(), player.centerY(), PICKUP_RANGE);
        nearBin = level.nearestBin(player.centerX(), player.centerY(), BIN_RANGE);
        for (Garbage g : level.getGarbage()) g.setHighlighted(g == nearGarbage);
        for (TrashBin b : level.getBins()) b.setHighlighted(b == nearBin && !player.bagEmpty());

        if (input.isKeyJustPressed(KeyEvent.VK_Q) || input.isKeyJustPressed(KeyEvent.VK_TAB)) {
            player.cycleSelected();
        }

        if (input.isKeyJustPressed(KeyEvent.VK_E) || input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            if (nearBin != null && !player.bagEmpty()) throwInBin(nearBin);
            else if (nearGarbage != null) pickUp(nearGarbage);
        }

        if (trashMonster != null) {
            trashMonster.update(dt, player, level);
            int garbageDrops = trashMonster.consumeGarbageDrops();
            if (garbageDrops > 0) {
                for (int i = 0; i < garbageDrops; i++) {
                    level.dropGarbageFromBoss(trashMonster.centerX(), trashMonster.centerY());
                }
                showToast("The Trash Monster dumped more litter!", 2);
            }
        }

        if (level.garbageLeft() == 0 && player.bagEmpty()) {
            cleared = true;
            player.addMoney(100);                       // "complete the entire level +P100"
            showToast("Street cleaned! +\u20B1100", 6);
        }
    }

    private void pickUp(Garbage g) {
        if (player.bagFull()) { showToast("Bag is full! Empty it in a bin.", 2); return; }
        player.pickUp(g.getType());
        level.removeGarbage(g);
        showToast("Picked up " + g.getType().getLabel().toLowerCase() + " +\u20B12", 1.5);
    }

    private void throwInBin(TrashBin bin) {
        GarbageType item = player.removeSelected();
        if (item == bin.getType()) {
            int reward = item == GarbageType.HAZARDOUS ? 10 : 5;
            player.addMoney(reward);
            player.addScore(10);
            level.registerCorrectSort();
            showToast("Correct! +\u20B1" + reward + "  +10 pts", 1.5);
            spawnBossWhenCityHealthReachesThreshold();
        } else {
            player.addScore(-5);
            player.hurt(1);
            level.dropGarbage(item, player.centerX(), player.centerY());
            showToast("Wrong bin! " + item.getLabel() + " goes elsewhere.  -1 HP", 2);
            if (player.isDead()) { startLevel(); showToast("Out of health - level restarted.", 3); }
        }
    }

    private void spawnBossWhenCityHealthReachesThreshold() {
        if (trashMonster != null || level.cleanliness() < BOSS_SPAWN_CITY_HEALTH) return;

        double x = 24 * TILE + (TILE - 32) / 2.0;
        double y = 8 * TILE - 8;
        trashMonster = new TrashMonster(x, y);
        level.addNpc(trashMonster);
        showToast("The Trash Monster appeared!", 3);
    }

    private void showToast(String t, double secs) { toast = t; toastTime = secs; }

    private boolean isDown(InputHandler input, int a, int b) {
        return input.isKeyDown(a) || input.isKeyDown(b);
    }

    // ------------------------------------------------------------ render
    public void renderFrame(Graphics2D g, int width, int height) {
        Graphics2D fg = frame.createGraphics();
        level.draw(fg, player);
        fg.dispose();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(frame, 0, 0, width, height, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawHud(g, width, height);
    }

    private void panel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(15, 20, 25, 190));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(new Color(255, 255, 255, 60));
        g.drawRoundRect(x, y, w, h, 12, 12);
    }

    private void drawHud(Graphics2D g, int W, int H) {
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 15));

        // top-left stats
        panel(g, 12, 12, 270, 112);
        g.setColor(Color.WHITE);
        g.drawString("ECO QUEST - " + level.getName(), 24, 34);
        g.setColor(new Color(255, 214, 90));
        g.drawString("\u20B1 " + player.getMoney(), 24, 56);
        g.setColor(Color.WHITE);
        g.drawString("Score " + player.getScore(), 120, 56);
        for (int i = 0; i < player.getMaxHealth(); i++) {
            g.setColor(i < player.getHealth() ? new Color(230, 60, 70) : new Color(90, 90, 95));
            g.drawString("\u2665", 24 + i * 20, 80);
        }
        int pct = level.cleanliness();
        g.setColor(Color.WHITE);
        g.drawString("City Health " + pct + "%", 24, 100);
        g.setColor(new Color(60, 60, 65));
        g.fillRect(24, 106, 240, 9);
        g.setColor(pct < 50 ? new Color(220, 150, 50) : new Color(80, 200, 90));
        g.fillRect(24, 106, 240 * pct / 100, 9);

        // top-right garbage counter
        panel(g, W - 182, 12, 170, 36);
        g.setColor(Color.WHITE);
        g.drawString("Litter left: " + level.garbageLeft(), W - 168, 36);

        // bottom-left bag
        panel(g, 12, H - 74, 24 + player.getCapacity() * 36, 62);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        g.drawString("BAG  (Q = switch item)", 24, H - 56);
        for (int i = 0; i < player.getCapacity(); i++) {
            int sx = 24 + i * 36, sy = H - 48;
            g.setColor(new Color(50, 55, 60));
            g.fillRect(sx, sy, 30, 26);
            if (i < player.getBag().size()) {
                g.setColor(new Color(player.getBag().get(i).getColor()));
                g.fillRect(sx + 3, sy + 3, 24, 20);
            }
            if (i == player.getSelectedIndex() && !player.bagEmpty()) {
                g.setColor(Color.WHITE);
                g.drawRect(sx - 1, sy - 1, 31, 27);
            }
        }

        // bottom-right controls
        g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        g.setColor(new Color(255, 255, 255, 220));
        g.drawString("WASD / Arrows: move   E: pick up / throw   Q: switch item   R: restart", W - 470, H - 14);

        // context hint
        String hint = null;
        if (nearBin != null && !player.bagEmpty())
            hint = "E: throw " + player.selectedItem().getLabel() + " into the " + nearBin.getType().getLabel() + " bin";
        else if (nearGarbage != null) hint = "E: pick up " + nearGarbage.getType().getLabel();
        if (hint != null) centered(g, hint, W, H - 96, 15);

        if (toastTime > 0) centered(g, toast, W, 70, 16);

        if (cleared) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, W, H);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.DIALOG, Font.BOLD, 34));
            String s = "Residential Street Cleaned!";
            g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, H / 2 - 20);
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 18));
            String s2 = "Next: Boss Challenge - the Trash Monster   (R to replay)";
            g.drawString(s2, (W - g.getFontMetrics().stringWidth(s2)) / 2, H / 2 + 20);
        }
    }

    private void centered(Graphics2D g, String text, int width, int y, int size) {
        g.setFont(new Font(Font.DIALOG, Font.BOLD, size));
        int tw = g.getFontMetrics().stringWidth(text);
        int x = (width - tw) / 2;
        g.setColor(new Color(15, 20, 25, 200));
        g.fillRoundRect(x - 12, y - size - 2, tw + 24, size + 12, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString(text, x, y);
    }
}
