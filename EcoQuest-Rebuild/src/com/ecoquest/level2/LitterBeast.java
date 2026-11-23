package com.ecoquest.level2;

import com.ecoquest.level1.Config;
import com.ecoquest.level1.Entity;
import com.ecoquest.level1.Level;
import com.ecoquest.level1.Player;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.Random;

public class LitterBeast extends Entity {
    private static final double SPEED = 16;
    private static final double LITTER_INTERVAL = 8;

    private final Random rng = new Random();
    private double tx, ty, litterTimer = LITTER_INTERVAL, pulse;
    private boolean hasTarget, defeated;

    public LitterBeast(double x, double y) { super(x, y, 28, 24); }

    public void update(double dt, Level level) {
        if (defeated) return;
        pulse = Math.max(0, pulse - dt);
        litterTimer -= dt;
        if (!hasTarget) {
            for (int i = 0; i < 40 && !hasTarget; i++) {
                tx = rng.nextInt(Config.WORLD_W - w);
                ty = rng.nextInt(Config.WORLD_H - h);
                hasTarget = !level.isBlocked(new Rectangle2D.Double(tx, ty, w, h));
            }
            return;
        }
        double dx = tx - x, dy = ty - y, d = Math.hypot(dx, dy), step = SPEED * dt;
        if (d < 2) { hasTarget = false; return; }
        double nx = x + dx / d * Math.min(step, d), ny = y + dy / d * Math.min(step, d);
        if (level.isBlocked(new Rectangle2D.Double(nx, ny, w, h))) hasTarget = false;
        else { x = nx; y = ny; }
    }

    public boolean readyToSpawnLitter() {
        if (defeated || litterTimer > 0) return false;
        litterTimer = LITTER_INTERVAL;
        pulse = 0.5;
        return true;
    }

    public boolean isTouching(Player p) {
        return !defeated && Math.hypot(centerX() - p.centerX(), centerY() - p.centerY()) < 16;
    }

    public void defeat() { defeated = true; }

    @Override
    public void draw(Graphics2D g) {
        if (defeated) return;
        int X = (int) x, Y = (int) y;
        long t = System.currentTimeMillis();
        int wob = (int) Math.round(Math.sin(t / 200.0));

        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(X + 1, Y + h - 5, w - 2, 6);
        if (pulse > 0) {
            g.setColor(new Color(150, 120, 40, (int) (pulse * 200)));
            g.fillOval(X - 6, Y - 4, w + 12, h + 10);
        }
        g.setColor(new Color(0x3B4A1F));
        g.fillOval(X, Y + 3 + wob, w, h - 3);
        g.setColor(new Color(0x56692B));
        g.fillOval(X + 2, Y + 4 + wob, w - 4, h - 8);
        g.setColor(new Color(0x6E8436));
        g.fillOval(X + 6, Y + 5 + wob, 12, 6);

        rect(g, X + 3, Y + 12 + wob, 4, 6, 0xCF3B30);
        rect(g, X + 3, Y + 12 + wob, 4, 1, 0xDADADA);
        rect(g, X + 20, Y + 10 + wob, 6, 3, 0x5AA7E0);
        rect(g, X + 12, Y + 2 + wob, 5, 3, 0xE9953A);
        rect(g, X + 22, Y + 17 + wob, 3, 3, 0xE7CF4A);

        rect(g, X + 8, Y + 9 + wob, 4, 3, 0xFFF4C2);
        rect(g, X + 16, Y + 9 + wob, 4, 3, 0xFFF4C2);
        rect(g, X + 10, Y + 10 + wob, 2, 2, 0xC0201A);
        rect(g, X + 16, Y + 10 + wob, 2, 2, 0xC0201A);
        rect(g, X + 9, Y + 15 + wob, 10, 2, 0x1A1A12);
        rect(g, X + 11, Y + 15 + wob, 1, 1, 0xEEEEEE);
        rect(g, X + 16, Y + 15 + wob, 1, 1, 0xEEEEEE);

        g.setColor(new Color(20, 20, 20));
        for (int i = 0; i < 3; i++) {
            double a = t / 300.0 + i * 2.1;
            g.fillRect(X + 14 + (int) (Math.cos(a) * 14), Y + 2 + (int) (Math.sin(a * 1.3) * 5), 1, 1);
        }
    }
}
