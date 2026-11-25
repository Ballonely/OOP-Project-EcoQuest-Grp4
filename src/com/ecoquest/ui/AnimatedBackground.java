package com.ecoquest.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Animated "polluted vs. restored" backdrop for the menu screens.
 *
 * <p>Draws the static artwork ({@code assets/menu_bg_anim.png}, a copy of the
 * menu art with the painted turbine blades erased) and layers procedural
 * animation on top of it: chimney smoke and falling ash on the industrial
 * side; drifting clouds, spinning wind turbines, a glowing sun, birds,
 * cherry-blossom petals, butterflies, glinting solar panels and sparkling
 * ponds with jumping fish on the restored side.
 *
 * <p>All coordinates are in the artwork's native 1376x768 pixel space; the
 * whole scene is scaled to cover the target area with a slow Ken Burns drift.
 * Animation is time-based, so callers only need to call {@link #render}
 * every frame.
 */
public final class AnimatedBackground {

    private static final int BW = 1376;
    private static final int BH = 768;

    // Chimney tops (smoke emitters + warning lights).
    private static final int[][] CHIMNEYS = {{80, 135}, {147, 160}, {196, 172}, {440, 205}, {500, 205}};
    // Turbines: hubX, hubY, bladeLength, rootWidth, speed (rad/s), distant (1) or near (0).
    private static final double[][] TURBINES = {
            {1056, 300, 37, 5, 1.60, 1},
            {1226, 285, 35, 5, 1.45, 1},
            {1152, 231, 70, 9, 1.30, 0},
            {1305, 190, 73, 9, 1.15, 0},
    };
    private static final int PANEL_X = 735, PANEL_Y = 280, PANEL_W = 245, PANEL_H = 75;
    private static final int[][] POND_BOXES = {{690, 580, 400, 135}, {1080, 505, 210, 60}};
    private static final double SUN_X = 1016, SUN_Y = 70;

    private static final class P {
        double x, y, vx, vy, age, life, size, grow, rot, vr, phase, shade;
    }

    private static final class Cloud {
        BufferedImage img;
        double x, y, speed, phase;
    }

    private static final class Bird {
        double dx, dy, phase;
    }

    private final Random rng = new Random(20261121L);
    private final BufferedImage base;
    private final boolean bladesErased;
    private BufferedImage panelMask;
    private BufferedImage glintBuffer;

    private final List<P> smoke = new ArrayList<>();
    private final List<P> ash = new ArrayList<>();
    private final List<P> bubbles = new ArrayList<>();
    private final List<P> sparkles = new ArrayList<>();
    private final List<P> ripples = new ArrayList<>();
    private final List<P> droplets = new ArrayList<>();
    private final List<P> petals = new ArrayList<>();
    private final List<Cloud> clouds = new ArrayList<>();
    private final List<Bird> flock = new ArrayList<>();
    private final double[] chimneyTimers = new double[CHIMNEYS.length];

    private double time;
    private long lastNanos = -1;
    private double ashAcc, bubbleAcc, sparkleAcc, petalAcc;
    private double rippleTimer = 1.0;
    private double flockX, flockY, flockSpeed, flockWait = 2.0;
    private boolean flockActive;
    // Fish jump state.
    private double fishTimer = 3.0, fishAge = -1, fishSx, fishSy, fishDx;

    public AnimatedBackground() {
        BufferedImage img = loadImage("menu_bg_anim.png");
        bladesErased = img != null;
        if (img == null) img = loadImage("menu_bg.jpg");
        base = img == null ? null : toNativeSize(img);
        if (base != null) buildPanelMask();
        for (int i = 0; i < 4; i++) {
            Cloud c = makeCloud();
            c.x = 700 + i * 190 + rng.nextDouble() * 60;
            clouds.add(c);
        }
        // Pre-warm so smoke and petals are already flowing on the first frame.
        for (int i = 0; i < 12 * 30; i++) update(1.0 / 30);
    }

    public boolean isLoaded() {
        return base != null;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    private BufferedImage loadImage(String name) {
        try (InputStream in = AnimatedBackground.class.getResourceAsStream("/assets/" + name)) {
            if (in != null) return ImageIO.read(in);
        } catch (Exception ignored) {
        }
        for (String path : new String[]{"assets/" + name, "bin/assets/" + name}) {
            try {
                File f = new File(path);
                if (f.isFile()) return ImageIO.read(f);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static BufferedImage toNativeSize(BufferedImage src) {
        BufferedImage out = new BufferedImage(BW, BH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, BW, BH, null);
        g.dispose();
        return out;
    }

    private void buildPanelMask() {
        panelMask = new BufferedImage(PANEL_W, PANEL_H, BufferedImage.TYPE_INT_ARGB);
        glintBuffer = new BufferedImage(PANEL_W, PANEL_H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < PANEL_H; y++) {
            for (int x = 0; x < PANEL_W; x++) {
                int rgb = base.getRGB(PANEL_X + x, PANEL_Y + y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                if (b > 140 && b > r + 45 && b > g + 15) panelMask.setRGB(x, y, 0xFFFFFFFF);
            }
        }
    }

    private Cloud makeCloud() {
        int puffs = 3 + rng.nextInt(3);
        int w = 34 + rng.nextInt(26);
        int h = 14;
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = small.createGraphics();
        g.setColor(new Color(214, 240, 252));
        g.fillRoundRect(0, h - 6, w, 6, 6, 6);
        for (int i = 0; i < puffs; i++) {
            int pw = 10 + rng.nextInt(10);
            int px = (int) ((w - pw) * (i / (double) Math.max(1, puffs - 1)));
            int ph = pw - 1;
            g.fillOval(px, h - ph - 1, pw, ph);
        }
        // Bright top highlight: redraw slightly smaller and higher in white.
        g.setComposite(AlphaComposite.SrcAtop);
        g.setColor(new Color(252, 254, 255));
        g.fillRect(0, 0, w, h - 5);
        g.dispose();
        Cloud c = new Cloud();
        c.img = small;
        c.y = 92 + rng.nextDouble() * 55;
        c.speed = 4 + rng.nextDouble() * 4;
        c.phase = rng.nextDouble() * Math.PI * 2;
        c.x = 640 - w * 2.0;
        return c;
    }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    private double rnd(double a, double b) {
        return a + rng.nextDouble() * (b - a);
    }

    private int pixel(int x, int y) {
        if (base == null || x < 0 || y < 0 || x >= BW || y >= BH) return 0;
        return base.getRGB(x, y);
    }

    private boolean isCleanWater(int x, int y) {
        int c = pixel(x, y);
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return b > 190 && r < 115 && g > 130 && g < 205 && b - r > 100;
    }

    private boolean isDirtyWater(int x, int y) {
        int c = pixel(x, y);
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        return max < 105 && max > 35 && Math.abs(r - g) < 30;
    }

    private void update(double dt) {
        time += dt;
        updateSmoke(dt);
        updateAsh(dt);
        updateWater(dt);
        updatePetals(dt);
        updateClouds(dt);
        updateFlock(dt);
    }

    private void updateSmoke(double dt) {
        for (int i = 0; i < CHIMNEYS.length; i++) {
            chimneyTimers[i] -= dt;
            if (chimneyTimers[i] <= 0) {
                chimneyTimers[i] = rnd(0.16, 0.30);
                P p = new P();
                p.x = CHIMNEYS[i][0] + rnd(-3, 3);
                p.y = CHIMNEYS[i][1] - 2;
                p.vx = rnd(4, 10);
                p.vy = rnd(-24, -15);
                p.size = rnd(7, 11);
                p.grow = rnd(4.5, 7.5);
                p.life = rnd(7, 10);
                p.shade = rng.nextDouble();
                smoke.add(p);
            }
        }
        for (Iterator<P> it = smoke.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.age += dt;
            p.vx += 2.2 * dt;
            p.vy *= 1 - 0.08 * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.age >= p.life || p.y < -80) it.remove();
        }
    }

    private void updateAsh(double dt) {
        ashAcc += dt * 7;
        while (ashAcc >= 1) {
            ashAcc -= 1;
            P p = new P();
            p.x = rnd(-20, 620);
            p.y = rnd(-10, 220);
            p.vx = rnd(3, 9);
            p.vy = rnd(7, 15);
            p.life = rnd(8, 13);
            p.phase = rnd(0, Math.PI * 2);
            p.size = rng.nextInt(3) == 0 ? 3 : 2;
            ash.add(p);
        }
        for (Iterator<P> it = ash.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.age += dt;
            p.x += (p.vx + Math.sin(time * 1.3 + p.phase) * 6) * dt;
            p.y += p.vy * dt;
            if (p.age >= p.life || p.y > 600 || p.x > 700) it.remove();
        }
    }

    private void updateWater(double dt) {
        // Toxic bubbles on the polluted canal.
        bubbleAcc += dt * 4;
        while (bubbleAcc >= 1) {
            bubbleAcc -= 1;
            int x = (int) rnd(10, 620), y = (int) rnd(592, 762);
            if (!isDirtyWater(x, y)) continue;
            P p = new P();
            p.x = x;
            p.y = y;
            p.life = rnd(1.2, 2.2);
            p.size = rnd(2, 4);
            bubbles.add(p);
        }
        age(bubbles, dt);

        // Sun sparkles on the clean ponds.
        sparkleAcc += dt * 14;
        while (sparkleAcc >= 1) {
            sparkleAcc -= 1;
            int[] box = POND_BOXES[rng.nextInt(4) == 0 ? 1 : 0];
            int x = box[0] + rng.nextInt(box[2]), y = box[1] + rng.nextInt(box[3]);
            if (!isCleanWater(x, y) || !isCleanWater(x - 4, y) || !isCleanWater(x + 4, y)) continue;
            P p = new P();
            p.x = x;
            p.y = y;
            p.life = rnd(0.6, 1.3);
            p.size = rng.nextInt(3) == 0 ? 1 : 0; // 1 = cross, 0 = dash
            p.vx = rnd(-3, 3);
            sparkles.add(p);
        }
        for (P p : sparkles) p.x += p.vx * dt;
        age(sparkles, dt);

        // Random ripples.
        rippleTimer -= dt;
        if (rippleTimer <= 0) {
            rippleTimer = rnd(1.0, 2.4);
            int[] box = POND_BOXES[rng.nextInt(3) == 0 ? 1 : 0];
            for (int tries = 0; tries < 20; tries++) {
                int x = box[0] + rng.nextInt(box[2]), y = box[1] + rng.nextInt(box[3]);
                if (isCleanWater(x - 12, y) && isCleanWater(x + 12, y)) {
                    addRipple(x, y, 0);
                    addRipple(x, y, -0.35);
                    break;
                }
            }
        }
        age(ripples, dt);

        // Occasional jumping fish in the big pond.
        if (fishAge >= 0) {
            fishAge += dt;
            if (fishAge >= 1.0) {
                splash(fishSx + fishDx, fishSy);
                fishAge = -1;
                fishTimer = rnd(4, 8);
            }
        } else {
            fishTimer -= dt;
            if (fishTimer <= 0) {
                int[] box = POND_BOXES[0];
                for (int tries = 0; tries < 30; tries++) {
                    int x = box[0] + rng.nextInt(box[2]), y = box[1] + 15 + rng.nextInt(box[3] - 15);
                    double dx = rng.nextBoolean() ? 38 : -38;
                    if (isCleanWater(x, y) && isCleanWater((int) (x + dx), y)
                            && isCleanWater(x - 10, y) && isCleanWater((int) (x + dx * 1.3), y)) {
                        fishSx = x;
                        fishSy = y;
                        fishDx = dx;
                        fishAge = 0;
                        splash(x, y);
                        break;
                    }
                }
                if (fishAge < 0) fishTimer = 1;
            }
        }
        for (Iterator<P> it = droplets.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.age += dt;
            p.vy += 160 * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.age >= p.life) it.remove();
        }
    }

    private void addRipple(double x, double y, double delay) {
        P p = new P();
        p.x = x;
        p.y = y;
        p.age = delay;
        p.life = 1.6;
        ripples.add(p);
    }

    private void splash(double x, double y) {
        addRipple(x, y, 0);
        addRipple(x, y, -0.3);
        for (int i = 0; i < 6; i++) {
            P d = new P();
            d.x = x;
            d.y = y;
            d.vx = rnd(-22, 22);
            d.vy = rnd(-60, -30);
            d.life = 0.45;
            droplets.add(d);
        }
    }

    private static void age(List<P> list, double dt) {
        for (Iterator<P> it = list.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.age += dt;
            if (p.age >= p.life) it.remove();
        }
    }

    private void updatePetals(double dt) {
        petalAcc += dt * 5;
        while (petalAcc >= 1) {
            petalAcc -= 1;
            P p = new P();
            if (rng.nextInt(4) == 0) {
                p.x = rnd(1220, 1292);
                p.y = rnd(345, 400);
            } else {
                p.x = rnd(705, 855);
                p.y = rnd(365, 455);
            }
            p.vx = rnd(6, 14);
            p.vy = rnd(9, 17);
            p.rot = rnd(0, Math.PI * 2);
            p.vr = rnd(-4, 4);
            p.phase = rnd(0, Math.PI * 2);
            p.life = rnd(6, 9);
            p.shade = rng.nextDouble();
            petals.add(p);
        }
        for (Iterator<P> it = petals.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.age += dt;
            p.x += (p.vx + Math.sin(time * 1.8 + p.phase) * 14) * dt;
            p.y += (p.vy + Math.cos(time * 2.3 + p.phase) * 4) * dt;
            p.rot += p.vr * dt;
            if (p.age >= p.life || p.x > BW + 10 || p.y > BH + 10) it.remove();
        }
    }

    private void updateClouds(double dt) {
        for (int i = 0; i < clouds.size(); i++) {
            Cloud c = clouds.get(i);
            c.x += c.speed * dt;
            if (c.x > BW + 10) clouds.set(i, makeCloud());
        }
    }

    private void updateFlock(double dt) {
        if (flockActive) {
            flockX += flockSpeed * dt;
            flockY += Math.sin(time * 0.6) * 4 * dt;
            if (flockX > BW + 60) {
                flockActive = false;
                flockWait = rnd(5, 11);
            }
        } else {
            flockWait -= dt;
            if (flockWait <= 0) {
                flockActive = true;
                flockX = 660;
                flockY = rnd(150, 230);
                flockSpeed = rnd(28, 40);
                flock.clear();
                int n = 3 + rng.nextInt(3);
                for (int i = 0; i < n; i++) {
                    Bird b = new Bird();
                    int row = (i + 1) / 2;
                    int side = (i % 2 == 0) ? 1 : -1;
                    b.dx = -row * 13 + rnd(-2, 2);
                    b.dy = row * 8 * side + rnd(-2, 2);
                    b.phase = rnd(0, Math.PI * 2);
                    flock.add(b);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Advances the animation by the wall-clock time since the last call and draws it to cover w x h. */
    public void render(Graphics2D target, int w, int h) {
        long now = System.nanoTime();
        if (lastNanos >= 0) update(Math.min(0.1, (now - lastNanos) / 1_000_000_000.0));
        lastNanos = now;
        if (base == null) return;

        Graphics2D g = (Graphics2D) target.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Cover-fit plus a slow Ken Burns zoom/pan.
        double zoom = 1.0 + 0.03 * (0.5 + 0.5 * Math.sin(time * 0.07));
        double scale = Math.max((double) w / BW, (double) h / BH) * zoom;
        double drawW = BW * scale, drawH = BH * scale;
        double ox = (w - drawW) / 2 + Math.sin(time * 0.045) * (drawW - w) / 2 * 0.8;
        double oy = (h - drawH) / 2 + Math.cos(time * 0.06) * (drawH - h) / 2 * 0.6;
        g.translate(ox, oy);
        g.scale(scale, scale);

        g.drawImage(base, 0, 0, null);
        drawSun(g);
        drawClouds(g);
        if (bladesErased) drawTurbines(g);
        drawFlock(g);
        drawPanelGlint(g);
        drawSmoke(g);
        drawAsh(g);
        drawChimneyLights(g);
        drawWater(g);
        drawPetals(g);
        drawButterflies(g);
        g.dispose();
    }

    private static Color rgba(int r, int g, int b, double a) {
        int alpha = (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
        return new Color(r, g, b, alpha);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private void drawSun(Graphics2D g) {
        double pulse = 0.5 + 0.5 * Math.sin(time * 1.2);
        float radius = (float) (120 + pulse * 14);
        g.setPaint(new RadialGradientPaint((float) SUN_X, (float) SUN_Y, radius,
                new float[]{0f, 0.38f, 1f},
                new Color[]{rgba(255, 252, 215, 0.0), rgba(255, 248, 200, 0.28 + pulse * 0.12), rgba(255, 245, 200, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fillOval((int) (SUN_X - radius), (int) (SUN_Y - radius), (int) (radius * 2), (int) (radius * 2));

        AffineTransform saved = g.getTransform();
        g.translate(SUN_X, SUN_Y);
        g.rotate(time * 0.12);
        for (int i = 0; i < 12; i++) {
            double len = 88 + 10 * Math.sin(time * 1.7 + i * 1.3);
            g.setColor(rgba(255, 250, 205, 0.16 + 0.06 * Math.sin(time * 2 + i)));
            Path2D ray = new Path2D.Double();
            ray.moveTo(-3, -54);
            ray.lineTo(3, -54);
            ray.lineTo(0, -len);
            ray.closePath();
            g.fill(ray);
            g.rotate(Math.PI * 2 / 12);
        }
        g.setTransform(saved);
    }

    private void drawClouds(Graphics2D g) {
        Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (Cloud c : clouds) {
            double a = 0.85 * clamp01((c.x - 640) / 140);
            if (a <= 0) continue;
            g.setComposite(AlphaComposite.SrcOver.derive((float) a));
            int y = (int) Math.round(c.y + Math.sin(time * 0.4 + c.phase) * 2);
            g.drawImage(c.img, (int) Math.round(c.x), y, c.img.getWidth() * 2, c.img.getHeight() * 2, null);
        }
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
    }

    private void drawTurbines(Graphics2D g) {
        for (double[] t : TURBINES) {
            double hx = t[0], hy = t[1], len = t[2], rw = t[3], speed = t[4];
            boolean far = t[5] > 0;
            Color fill = far ? new Color(196, 210, 226) : new Color(240, 240, 232);
            Color shade = far ? new Color(170, 186, 206) : new Color(198, 204, 212);
            Color line = far ? new Color(132, 148, 170) : new Color(96, 108, 124);
            double k = far ? 0.55 : 1.0;

            // Nacelle behind the rotor.
            g.setColor(shade);
            g.fill(new java.awt.geom.RoundRectangle2D.Double(hx - 5 * k, hy - 4 * k, 15 * k, 8 * k, 4 * k, 4 * k));
            g.setColor(line);
            g.setStroke(new BasicStroke((float) (far ? 0.8 : 1.2)));
            g.draw(new java.awt.geom.RoundRectangle2D.Double(hx - 5 * k, hy - 4 * k, 15 * k, 8 * k, 4 * k, 4 * k));

            Path2D blade = new Path2D.Double();
            blade.moveTo(-rw * 0.35, 0);
            blade.lineTo(-rw * 0.5, -len * 0.22);
            blade.lineTo(-rw * 0.12, -len);
            blade.lineTo(rw * 0.18, -len * 0.97);
            blade.lineTo(rw * 0.5, -len * 0.3);
            blade.lineTo(rw * 0.35, 0);
            blade.closePath();
            Path2D bladeShade = new Path2D.Double();
            bladeShade.moveTo(0, 0);
            bladeShade.lineTo(rw * 0.35, 0);
            bladeShade.lineTo(rw * 0.5, -len * 0.3);
            bladeShade.lineTo(rw * 0.18, -len * 0.97);
            bladeShade.lineTo(0, -len * 0.985);
            bladeShade.closePath();

            AffineTransform saved = g.getTransform();
            g.translate(hx, hy);
            g.scale(0.92, 1);
            double angle = time * speed + hx * 0.01;
            for (int i = 0; i < 3; i++) {
                AffineTransform before = g.getTransform();
                g.rotate(angle + i * Math.PI * 2 / 3);
                g.setColor(fill);
                g.fill(blade);
                g.setColor(shade);
                g.fill(bladeShade);
                g.setColor(line);
                g.draw(blade);
                g.setTransform(before);
            }
            double hr = far ? 2.6 : 4.8;
            g.setColor(fill);
            g.fill(new java.awt.geom.Ellipse2D.Double(-hr, -hr, hr * 2, hr * 2));
            g.setColor(line);
            g.draw(new java.awt.geom.Ellipse2D.Double(-hr, -hr, hr * 2, hr * 2));
            g.setTransform(saved);
        }
        g.setStroke(new BasicStroke(1));
    }

    private void drawFlock(Graphics2D g) {
        if (!flockActive) return;
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Bird b : flock) {
            double x = flockX + b.dx, y = flockY + b.dy;
            double a = clamp01((x - 680) / 80);
            if (a <= 0) continue;
            double flap = Math.sin(time * 9 + b.phase);
            double wy = -1 - flap * 3.2;
            g.setColor(rgba(38, 58, 78, a));
            Path2D p = new Path2D.Double();
            p.moveTo(x - 6, y + wy);
            p.quadTo(x - 3, y + wy * 0.3 - 1, x, y);
            p.quadTo(x + 3, y + wy * 0.3 - 1, x + 6, y + wy);
            g.draw(p);
        }
        g.setStroke(new BasicStroke(1));
    }

    private void drawPanelGlint(Graphics2D g) {
        if (panelMask == null) return;
        double period = 6.5;
        double u = (time % period) / 1.4; // sweep takes 1.4s, then rests
        if (u > 1) return;
        Graphics2D gb = glintBuffer.createGraphics();
        gb.setComposite(AlphaComposite.Clear);
        gb.fillRect(0, 0, PANEL_W, PANEL_H);
        gb.setComposite(AlphaComposite.SrcOver);
        gb.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double cx = -PANEL_H + u * (PANEL_W + PANEL_H * 2);
        for (int band = 0; band < 2; band++) {
            double off = band * 22, half = band == 0 ? 9 : 4;
            Path2D strip = new Path2D.Double();
            strip.moveTo(cx - off - half, 0);
            strip.lineTo(cx - off + half, 0);
            strip.lineTo(cx - off + half - PANEL_H * 0.8, PANEL_H);
            strip.lineTo(cx - off - half - PANEL_H * 0.8, PANEL_H);
            strip.closePath();
            gb.setColor(rgba(255, 255, 255, band == 0 ? 0.65 : 0.45));
            gb.fill(strip);
        }
        gb.setComposite(AlphaComposite.DstIn);
        gb.drawImage(panelMask, 0, 0, null);
        gb.dispose();
        g.drawImage(glintBuffer, PANEL_X, PANEL_Y, null);
    }

    private void drawSmoke(Graphics2D g) {
        for (P p : smoke) {
            double t = p.age / p.life;
            double a = 0.62 * clamp01(p.age / 0.4) * (1 - t) * clamp01((780 - p.x) / 170);
            if (a <= 0.01) continue;
            int c = (int) (62 + p.shade * 40);
            g.setColor(rgba(c, c - 3, c - 9, a));
            double s = p.size + p.age * p.grow;
            double x = Math.round((p.x - s / 2) / 2) * 2, y = Math.round((p.y - s / 2) / 2) * 2;
            g.fill(new java.awt.geom.Ellipse2D.Double(x, y, s * 1.15, s));
        }
    }

    private void drawAsh(Graphics2D g) {
        for (P p : ash) {
            double a = 0.7 * clamp01(p.age / 0.8) * clamp01((p.life - p.age) / 1.5) * clamp01((680 - p.x) / 80);
            if (a <= 0.01) continue;
            g.setColor(rgba(52, 50, 47, a));
            g.fillRect((int) p.x, (int) p.y, (int) p.size, (int) p.size - 1 + (int) (Math.sin(time * 5 + p.phase) + 1));
        }
    }

    private void drawChimneyLights(Graphics2D g) {
        for (int i = 0; i < CHIMNEYS.length; i++) {
            double phase = (time + i * 0.37) % 1.8;
            if (phase > 0.3) continue;
            int x = CHIMNEYS[i][0], y = CHIMNEYS[i][1] + 6;
            g.setColor(rgba(255, 60, 40, 0.35));
            g.fillOval(x - 6, y - 6, 12, 12);
            g.setColor(new Color(255, 80, 60));
            g.fillRect(x - 1, y - 1, 3, 3);
        }
    }

    private void drawWater(Graphics2D g) {
        // Toxic bubbles.
        for (P p : bubbles) {
            double t = p.age / p.life;
            if (t < 0.85) {
                double s = 1 + p.size * (t / 0.85);
                g.setColor(rgba(148, 172, 66, 0.75));
                g.fill(new java.awt.geom.Ellipse2D.Double(p.x - s / 2, p.y - s / 2 - t * 3, s, s * 0.85));
                g.setColor(rgba(214, 228, 140, 0.8));
                g.fillRect((int) (p.x - s / 4), (int) (p.y - s / 2 - t * 3), 1, 1);
            } else {
                double r = 2 + p.size * 1.6 * ((t - 0.85) / 0.15);
                g.setColor(rgba(160, 182, 90, (1 - t) / 0.15 * 0.6));
                g.draw(new java.awt.geom.Ellipse2D.Double(p.x - r, p.y - 3 - r * 0.4, r * 2, r * 0.8));
            }
        }

        // Ripples.
        g.setStroke(new BasicStroke(1.2f));
        for (P p : ripples) {
            if (p.age < 0) continue;
            double t = p.age / p.life;
            double rx = 2 + t * 16, ry = rx * 0.35;
            g.setColor(rgba(230, 248, 255, (1 - t) * 0.7));
            g.draw(new java.awt.geom.Ellipse2D.Double(p.x - rx, p.y - ry, rx * 2, ry * 2));
        }
        g.setStroke(new BasicStroke(1));

        // Sparkles.
        for (P p : sparkles) {
            double a = Math.sin(Math.PI * p.age / p.life);
            g.setColor(rgba(255, 255, 255, a * 0.9));
            int x = (int) p.x, y = (int) p.y;
            if (p.size > 0) {
                g.fillRect(x - 2, y, 5, 1);
                g.fillRect(x, y - 2, 1, 5);
            } else {
                g.fillRect(x - 3, y, 7, 1);
            }
        }

        // Jumping fish.
        if (fishAge >= 0) {
            double u = fishAge;
            double x = fishSx + fishDx * u;
            double y = fishSy - 4 * u * (1 - u) * 30;
            double ang = Math.atan2(-30 * 4 * (1 - 2 * u), fishDx);
            AffineTransform saved = g.getTransform();
            g.translate(x, y);
            g.rotate(ang);
            if (fishDx < 0) g.scale(1, -1);
            g.setColor(new Color(118, 196, 206));
            g.fill(new java.awt.geom.Ellipse2D.Double(-6, -2.5, 12, 5));
            Path2D tail = new Path2D.Double();
            tail.moveTo(-5, 0);
            tail.lineTo(-10, -3.5);
            tail.lineTo(-10, 3.5);
            tail.closePath();
            g.fill(tail);
            g.setColor(new Color(46, 92, 112));
            g.draw(new java.awt.geom.Ellipse2D.Double(-6, -2.5, 12, 5));
            g.fillRect(3, -1, 1, 1);
            g.setTransform(saved);
        }
        for (P d : droplets) {
            g.setColor(rgba(230, 248, 255, 1 - d.age / d.life));
            g.fillRect((int) d.x, (int) d.y, 2, 2);
        }
    }

    private void drawPetals(Graphics2D g) {
        for (P p : petals) {
            double a = clamp01(p.age / 0.4) * clamp01((p.life - p.age) / 1.5);
            if (a <= 0.01) continue;
            AffineTransform saved = g.getTransform();
            g.translate(p.x, p.y);
            g.rotate(p.rot);
            double squash = 0.4 + 0.6 * Math.abs(Math.sin(time * 3 + p.phase));
            g.scale(1, squash);
            g.setColor(p.shade > 0.5 ? rgba(255, 178, 210, a) : rgba(250, 150, 190, a));
            g.fillRect(-2, -1, 4, 3);
            g.setColor(rgba(255, 224, 238, a));
            g.fillRect(-2, -1, 2, 1);
            g.setTransform(saved);
        }
    }

    private void drawButterflies(Graphics2D g) {
        double[][] bugs = {
                {1060, 430, 0.0, 255, 214, 60},
                {1180, 445, 2.1, 250, 250, 238},
                {1250, 455, 4.2, 255, 150, 60},
                {900, 470, 1.3, 255, 205, 225},
        };
        for (double[] b : bugs) {
            double p = b[2];
            double x = b[0] + 45 * Math.sin(time * 0.45 + p) + 12 * Math.sin(time * 1.7 + p * 2);
            double y = b[1] + 14 * Math.sin(time * 0.8 + p * 3) + 5 * Math.sin(time * 2.6 + p);
            double ww = 1 + 3 * Math.abs(Math.sin(time * 15 + p * 5));
            int ix = (int) Math.round(x), iy = (int) Math.round(y);
            g.setColor(new Color((int) b[3], (int) b[4], (int) b[5]));
            g.fill(new java.awt.geom.Rectangle2D.Double(ix - ww, iy - 2, ww, 3));
            g.fill(new java.awt.geom.Rectangle2D.Double(ix + 1, iy - 2, ww, 3));
            g.setColor(new Color(50, 40, 30));
            g.fillRect(ix, iy - 2, 1, 4);
        }
    }
}
