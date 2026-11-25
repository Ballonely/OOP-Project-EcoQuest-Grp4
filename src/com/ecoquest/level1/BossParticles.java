package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Lightweight world-space particles for boss effects: fluttering leaves, tumbling trash bits
 * that bounce, soft dust puffs and twinkling sparkles. Positions are on the ground plane plus a
 * height {@code z}, so things arc through the air and land where they should.
 */
public final class BossParticles {
    public static final int LEAF = 0, BIT = 1, PUFF = 2, SPARK = 3;

    private static final class P {
        double x, y, z, vx, vy, vz, age, life, spin;
        int kind, rgb, size;
        boolean landed;
    }

    private final List<P> list = new ArrayList<>();
    private final Random rng = new Random();

    public Random rng() { return rng; }

    public boolean isEmpty() { return list.isEmpty(); }

    public void clear() { list.clear(); }

    /** Adds one particle at ground point (x, y), height z, with a velocity (px/s). */
    public void add(int kind, double x, double y, double z, double vx, double vy, double vz, double life, int rgb, int size) {
        P p = new P();
        p.kind = kind;
        p.x = x; p.y = y; p.z = z;
        p.vx = vx; p.vy = vy; p.vz = vz;
        p.life = life;
        p.rgb = rgb;
        p.size = size;
        p.spin = rng.nextDouble() * Math.PI * 2;
        list.add(p);
    }

    /** A ring of particles thrown out from (x, y, z). */
    public void burst(int kind, double x, double y, double z, int count, double speed, double up, double life, int[] colors) {
        for (int i = 0; i < count; i++) {
            double a = rng.nextDouble() * Math.PI * 2, s = speed * (0.4 + rng.nextDouble() * 0.8);
            add(kind, x, y, z, Math.cos(a) * s, Math.sin(a) * s * 0.55, up * (0.6 + rng.nextDouble() * 0.7),
                    life * (0.7 + rng.nextDouble() * 0.6), colors[rng.nextInt(colors.length)],
                    kind == PUFF ? 3 + rng.nextInt(3) : 1 + rng.nextInt(2));
        }
    }

    public void update(double dt) {
        for (Iterator<P> it = list.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.age += dt;
            if (p.age >= p.life) { it.remove(); continue; }
            switch (p.kind) {
                case LEAF -> {
                    p.spin += dt * 7;
                    if (!p.landed) {
                        p.vz = Math.max(p.vz - 70 * dt, -14);
                        p.vx *= Math.pow(0.35, dt);
                        p.vy *= Math.pow(0.35, dt);
                        p.x += (p.vx + Math.sin(p.spin) * 9) * dt;
                        p.y += p.vy * dt;
                    }
                }
                case BIT -> {
                    if (!p.landed) {
                        p.vz -= 220 * dt;
                        p.x += p.vx * dt;
                        p.y += p.vy * dt;
                        p.spin += dt * 12;
                    }
                }
                case PUFF -> {
                    p.x += p.vx * dt;
                    p.y += p.vy * dt;
                    p.vx *= Math.pow(0.15, dt);
                    p.vy *= Math.pow(0.15, dt);
                    p.z += p.vz * dt;
                    continue;
                }
                case SPARK -> {
                    p.z += p.vz * dt;
                    p.x += p.vx * dt;
                    p.vz *= Math.pow(0.4, dt);
                    continue;
                }
                default -> { }
            }
            if (!p.landed) {
                p.z += p.vz * dt;
                if (p.z <= 0) {
                    p.z = 0;
                    if (p.kind == BIT && p.vz < -40) {   // one little bounce
                        p.vz = -p.vz * 0.35;
                        p.vx *= 0.5;
                        p.vy *= 0.5;
                    } else {
                        p.landed = true;
                        p.age = Math.max(p.age, p.life - 0.9);   // linger briefly, then fade
                    }
                }
            }
        }
    }

    public void draw(Graphics2D g) {
        for (P p : list) {
            double k = p.age / p.life;
            int sx = (int) Math.round(p.x), sy = (int) Math.round(p.y - p.z);
            switch (p.kind) {
                case LEAF -> {
                    int a = alpha(k, 0.75);
                    g.setColor(withAlpha(p.rgb, a));
                    boolean flat = Math.cos(p.spin) > 0;
                    if (flat) g.fillRect(sx - 1, sy, 2 + p.size - 1, 1);
                    else g.fillRect(sx, sy - 1, 1, 2);
                    if (!p.landed && p.z > 2) {
                        g.setColor(new Color(0, 0, 0, a / 5));
                        g.fillRect((int) Math.round(p.x), (int) Math.round(p.y), 2, 1);
                    }
                }
                case BIT -> {
                    int a = alpha(k, 0.7);
                    int s = p.size + 1;
                    boolean wide = Math.cos(p.spin) > 0;   // tumbling: alternates wide / tall
                    int bw = wide ? s + 1 : s, bh = wide ? s : s + 1;
                    g.setColor(new Color(0x14, 0x10, 0x0C, a));
                    g.fillRect(sx - 1, sy - 1, bw + 1, bh + 1);
                    g.setColor(withAlpha(p.rgb, a));
                    g.fillRect(sx, sy, bw - 1, bh - 1);
                    if (p.z > 3) {
                        g.setColor(new Color(0, 0, 0, a / 4));
                        g.fillRect((int) Math.round(p.x), (int) Math.round(p.y), s, 1);
                    }
                }
                case PUFF -> {
                    double r = p.size * (0.6 + k * 1.6);
                    g.setColor(withAlpha(p.rgb, (int) (150 * (1 - k) * (1 - k))));
                    int d = (int) Math.round(r * 2);
                    g.fillOval((int) Math.round(p.x - r), (int) Math.round(p.y - p.z - r), d, d);
                }
                case SPARK -> {
                    int a = (int) (255 * Math.sin(Math.min(1, k) * Math.PI));
                    g.setColor(withAlpha(p.rgb, a));
                    g.fillRect(sx, sy, 1, 1);
                    if (k > 0.25 && k < 0.75) {
                        g.setColor(withAlpha(p.rgb, a / 2));
                        g.fillRect(sx - 1, sy, 3, 1);
                        g.fillRect(sx, sy - 1, 1, 3);
                    }
                }
                default -> { }
            }
        }
    }

    private static int alpha(double k, double fadeFrom) {
        return (int) (255 * (k < fadeFrom ? 1 : 1 - (k - fadeFrom) / (1 - fadeFrom)));
    }

    private static Color withAlpha(int rgb, int a) {
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.max(0, Math.min(255, a)));
    }
}
