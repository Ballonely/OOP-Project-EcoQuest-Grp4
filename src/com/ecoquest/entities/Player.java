package com.ecoquest.entities;

import com.ecoquest.core.InputHandler;
import com.ecoquest.util.Constants;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

/** The player-controlled eco-hero: movement, health, score and cleanliness progress. */
public class Player extends GameObject {

    private static final double SPEED = 260; // pixels / second

    private final InputHandler input;
    private final int worldWidth;
    private final int worldHeight;

    private int health = 100;
    private final int maxHealth = 100;
    private int score = 0;
    private int cleanlinessLevel = 35; // percent, mirrors the HUD bar in the reference art
    private int hurtCooldownMs = 0;

    public Player(double x, double y, InputHandler input, int worldWidth, int worldHeight) {
        super(x, y, 36, 44);
        this.input = input;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
    }

    @Override
    public void update(double deltaTime) {
        velX = 0;
        velY = 0;
        if (input.isKeyDown(KeyEvent.VK_LEFT) || input.isKeyDown(KeyEvent.VK_A)) velX -= SPEED;
        if (input.isKeyDown(KeyEvent.VK_RIGHT) || input.isKeyDown(KeyEvent.VK_D)) velX += SPEED;
        if (input.isKeyDown(KeyEvent.VK_UP) || input.isKeyDown(KeyEvent.VK_W)) velY -= SPEED;
        if (input.isKeyDown(KeyEvent.VK_DOWN) || input.isKeyDown(KeyEvent.VK_S)) velY += SPEED;

        x += velX * deltaTime;
        y += velY * deltaTime;

        x = Math.max(0, Math.min(worldWidth - width, x));
        y = Math.max(0, Math.min(worldHeight - height, y));

        if (hurtCooldownMs > 0) {
            hurtCooldownMs -= (int) (deltaTime * 1000);
        }
    }

    @Override
    public void render(Graphics2D g) {
        boolean flashing = hurtCooldownMs > 0 && (hurtCooldownMs / 100) % 2 == 0;

        g.setColor(flashing ? Constants.COLOR_HEALTH_RED : Constants.COLOR_TEAL_DARK);
        g.fillRoundRect((int) x, (int) y, width, height, 8, 8);
        g.setColor(flashing ? Color.WHITE : Constants.COLOR_TEAL);
        g.fillRoundRect((int) x + 4, (int) y + 4, width - 8, height - 16, 6, 6);
        g.setColor(Color.WHITE);
        g.fillOval((int) x + 8, (int) y + 6, 8, 8);
        g.fillOval((int) x + width - 16, (int) y + 6, 8, 8);
    }

    public void collect(int points, int cleanlinessGain) {
        score += points;
        cleanlinessLevel = Math.min(100, cleanlinessLevel + cleanlinessGain);
    }

    public void damage(int amount) {
        if (hurtCooldownMs <= 0) {
            health = Math.max(0, health - amount);
            hurtCooldownMs = 900;
        }
    }

    public boolean isDead() { return health <= 0; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public int getScore() { return score; }
    public int getCleanlinessLevel() { return cleanlinessLevel; }
}
