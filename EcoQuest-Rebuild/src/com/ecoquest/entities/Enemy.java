package com.ecoquest.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Random;

/** A roaming "pollution cloud" hazard that damages the player on contact. */
public class Enemy extends GameObject {

    private static final Random RNG = new Random();

    private final double speed;
    private final int worldWidth;
    private final int worldHeight;
    private double directionChangeTimer;

    public Enemy(double x, double y, int worldWidth, int worldHeight) {
        super(x, y, 34, 34);
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.speed = 60 + RNG.nextInt(60);
        randomizeDirection();
    }

    private void randomizeDirection() {
        double angle = RNG.nextDouble() * Math.PI * 2;
        velX = Math.cos(angle) * speed;
        velY = Math.sin(angle) * speed;
        directionChangeTimer = 1.5 + RNG.nextDouble() * 2.0;
    }

    @Override
    public void update(double deltaTime) {
        directionChangeTimer -= deltaTime;
        if (directionChangeTimer <= 0) {
            randomizeDirection();
        }

        x += velX * deltaTime;
        y += velY * deltaTime;

        if (x < 0 || x > worldWidth - width) velX *= -1;
        if (y < 0 || y > worldHeight - height) velY *= -1;
        x = Math.max(0, Math.min(worldWidth - width, x));
        y = Math.max(0, Math.min(worldHeight - height, y));
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(new Color(90, 90, 90, 200));
        g.fillOval((int) x, (int) y, width, height);
        g.setColor(new Color(50, 50, 50, 220));
        g.fillOval((int) x + 6, (int) y + 6, width - 14, height - 14);
    }
}
