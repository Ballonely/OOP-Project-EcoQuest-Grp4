package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/**
 * A squelchy pile of rotten produce left where one of the Waste King's projectiles lands. Walking
 * through it slows the player; it rots away after {@value #LIFETIME} seconds. It is not litter, so
 * it does not heal the king.
 */
public final class RottenPile extends Prop implements TimedProp {
    public static final double LIFETIME = 6.0;
    private static final double SLOW = 0.5;
    private static final int W = 22, H = 11;
    private static final int[][] BITS = {          // dx, dy, colour: tomato, fish, mango, cabbage bits
        {4, 4, 0xC8402E}, {9, 3, 0x9AA8B4}, {14, 4, 0xF0A030}, {7, 6, 0x7CC46A},
        {12, 6, 0xA8302A}, {16, 6, 0x7CC46A}, {3, 7, 0xF0A030}, {10, 8, 0xC8402E}, {15, 8, 0x9AA8B4}
    };
    private final int kind;
    private double age;

    public RottenPile(double centerX, double centerY, int kind) {
        super((int) Math.round(centerX) - W / 2, (int) Math.round(centerY) - H / 2, W, H, false);
        this.kind = kind;
    }

    @Override public boolean update(double dt) {
        age += dt;
        return age < LIFETIME;
    }

    @Override public double speedFactor() { return SLOW; }
    @Override public boolean isGroundDecal() { return true; }
    @Override public Rectangle2D.Double collisionBox() { return null; }

    @Override public void draw(Graphics2D g) {
        double fade = Math.max(0, Math.min(1, Math.min(age / 0.12, (LIFETIME - age) / 0.8)));
        double pop = Math.min(1, age / 0.12);
        int X = (int) x, Y = (int) y;
        int a = (int) (200 * fade);
        // slimy juice puddle
        g.setColor(new Color(92, 110, 50, a * 3 / 4));
        int pw = (int) Math.round(W * pop), ph = (int) Math.round(H * pop);
        g.fillOval(X + (W - pw) / 2, Y + (H - ph) / 2, pw, ph);
        g.setColor(new Color(60, 74, 34, a));
        g.fillOval(X + 4, Y + 3, W - 8, H - 5);
        // chunks of rotten produce, the projectile's own kind on top
        for (int i = 0; i < BITS.length; i++) {
            int col = BITS[(i + kind) % BITS.length][2];
            int[] b = BITS[i];
            g.setColor(new Color((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, a));
            g.fillRect(X + b[0], Y + b[1] - 1, 2, 2);
        }
        // flies buzzing over it
        g.setColor(new Color(20, 20, 20, a));
        for (int i = 0; i < 2; i++) {
            double t = age * 4 + i * 2.1;
            g.fillRect(X + W / 2 + (int) (Math.cos(t) * 6), Y - 2 + (int) (Math.sin(t * 1.7) * 2), 1, 1);
        }
    }
}
