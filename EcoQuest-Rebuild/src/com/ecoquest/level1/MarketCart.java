package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

final class MarketCart extends Entity {
    private final double minCenterX;
    private final double maxCenterX;
    private final double groundY;
    private final double speed;
    private int direction;

    MarketCart(double x1, double x2, double groundY, double speed, double lanePosition, int direction) {
        super(x1 + (x2 - x1) * lanePosition - 17, groundY - 20, 34, 22);
        this.minCenterX = x1 + 17;
        this.maxCenterX = x2 - 17;
        this.groundY = groundY;
        this.speed = speed;
        this.direction = direction;
    }

    void update(double dt) {
        x += direction * speed * dt;
        if (x + 17 >= maxCenterX) { x = maxCenterX - 17; direction = -1; }
        if (x + 17 <= minCenterX) { x = minCenterX - 17; direction = 1; }
    }

    Rectangle2D hitBounds() { return new Rectangle2D.Double(x + 2, groundY - 7, 30, 12); }

    @Override public void draw(Graphics2D g) {
        ecoquest.Sprites.cart(g, (int) Math.round(x + 17), (int) Math.round(groundY), direction);
    }
}
