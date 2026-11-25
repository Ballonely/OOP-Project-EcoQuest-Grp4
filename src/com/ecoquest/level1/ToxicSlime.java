package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Slowing slime left behind by the enraged Level 1 Trash Monster; fades after a few seconds. */
public final class ToxicSlime extends Prop implements TimedProp {
    private static final double LIFETIME = 4.0;
    private static final double SLOW = 0.55;
    private static final int W = 14, H = 7;
    private double age;

    public ToxicSlime(double centerX, double centerY) {
        super((int) Math.round(centerX) - W / 2, (int) Math.round(centerY) - H / 2, W, H, false);
    }

    @Override public boolean update(double dt) {
        age += dt;
        return age < LIFETIME;
    }

    @Override public double speedFactor() { return SLOW; }
    @Override public boolean isGroundDecal() { return true; }
    @Override public Rectangle2D.Double collisionBox() { return null; }

    @Override public void draw(Graphics2D g) {
        double fade = Math.min(1, Math.min(age / 0.15, (LIFETIME - age) / 0.6));
        int alpha = (int) (170 * Math.max(0, fade));
        int X = (int) x, Y = (int) y;
        g.setColor(new Color(96, 150, 40, alpha));
        g.fillOval(X, Y, W, H);
        g.setColor(new Color(160, 210, 70, Math.max(0, alpha - 40)));
        g.fillOval(X + 3, Y + 1, 5, 2);
        int bubble = (int) (age * 3) % 3;
        g.fillRect(X + 4 + bubble * 3, Y + 3, 1, 1);
    }
}
