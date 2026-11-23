package com.ecoquest.level2;

import com.ecoquest.level1.Entity;
import com.ecoquest.level1.Player;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Random;

public class LitterBot extends Entity {
    private static final double DROP_INTERVAL = 20;
    private static final int MAX_DROPS = 2;

    private final double ax, ay, bx, by;
    private boolean toB = true;
    private double speed = 18;
    private int facing = 1;
    private double dropTimer = 6 + new Random().nextInt(12);
    private int drops;
    private boolean dropReady;

    public LitterBot(double ax, double ay, double bx, double by) {
        super(ax, ay, 14, 14);
        this.ax = ax;
        this.ay = ay;
        this.bx = bx;
        this.by = by;
    }

    public void update(double dt) {
        double tx = toB ? bx : ax, ty = toB ? by : ay;
        double dx = tx - x, dy = ty - y, d = Math.hypot(dx, dy), step = speed * dt;
        if (dx != 0) facing = dx > 0 ? 1 : -1;
        if (d <= step) {
            x = tx;
            y = ty;
            toB = !toB;
        } else {
            x += dx / d * step;
            y += dy / d * step;
        }
        if (drops < MAX_DROPS && (dropTimer -= dt) <= 0) {
            dropTimer = DROP_INTERVAL;
            drops++;
            dropReady = true;
        }
    }

    public boolean readyToDrop() {
        boolean ready = dropReady;
        dropReady = false;
        return ready;
    }

    public void activate() { speed = 34; }

    public boolean isTouching(Player p) {
        return Math.hypot(centerX() - p.centerX(), centerY() - p.centerY()) < 11;
    }

    public double dropX() { return centerX() - 4; }
    public double dropY() { return y + h - 6; }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        long t = System.currentTimeMillis();

        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(X + 1, Y + h - 3, w - 2, 4);
        rect(g, X + 1, Y + 10, 4, 4, 0x2B2B2B);
        rect(g, X + 9, Y + 10, 4, 4, 0x2B2B2B);
        rect(g, X + 1, Y + 3, 12, 8, 0x8A3A14);
        rect(g, X + 2, Y + 3, 10, 7, 0xE36A2C);
        rect(g, X + 2, Y + 3, 10, 2, 0xF59A5A);
        rect(g, X + 4, Y + 5, 6, 3, 0x2A2A2A);
        rect(g, X + 5, Y + 6, 1, 1, 0x9BE7FF);
        rect(g, X + 8, Y + 6, 1, 1, 0x9BE7FF);
        rect(g, X + 6, Y + 1, 1, 2, 0x2A2A2A);
        rect(g, X + 5, Y - 1, 3, 2, (t / 400) % 2 == 0 ? 0xFFD23A : 0xA06A10);

        int nx = facing > 0 ? X + 13 : X - 1;
        rect(g, nx, Y + 7, 2, 2, 0x555555);
        for (int i = 0; i < 4; i++) {
            int k = (int) ((t / 90 + i * 3) % 8);
            g.setColor(new Color(170, 220, 255, 200 - k * 20));
            g.fillRect(nx + facing * (2 + k), Y + 7 + (i % 2 == 0 ? -k / 3 : k / 3), 1, 1);
        }
    }
}
