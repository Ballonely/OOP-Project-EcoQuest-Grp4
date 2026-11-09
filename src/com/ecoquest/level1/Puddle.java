package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Dirty puddle: walkable, but it slows the player down (an "obstacle" from the design doc). */
public class Puddle extends Prop {
    private static final double SLOW = 0.5;

    public Puddle(int px, int py, int w, int h) { super(px, py, w, h, false); }

    @Override public boolean isGroundDecal() { return true; }
    @Override public double speedFactor() { return SLOW; }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        g.setColor(Palette.c(0x2B3A44)); g.fillOval(X, Y, w, h);
        g.setColor(Palette.c(0x3B5666)); g.fillOval(X + 1, Y + 1, w - 3, h - 3);
        g.setColor(new Color(255, 255, 255, 90)); g.fillRect(X + 4, Y + 2, w / 3, 1);
    }

    @Override
    public Rectangle2D.Double collisionBox() { return null; }
}
