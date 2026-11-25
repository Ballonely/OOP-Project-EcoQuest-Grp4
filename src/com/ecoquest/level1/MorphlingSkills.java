package com.ecoquest.level1;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The Sewage Morphling's two extra skills on the beach (its third is the sewage splash volley,
 * run by {@link GamePanel}). None of them hurt the player:
 * <ul>
 *   <li><b>Tide Surge</b> (phase 2, waves 3-4): a foam strip marks a band of the beach, then a wave
 *   rolls in from the sea across it. Caught in it, the player is swept back toward the land and slowed.</li>
 *   <li><b>Sludgelings</b> (phase 3, wave 5): the Morphling spits slime blobs onto the sand that crawl to
 *   the bins. Walk into one to pop it; one that reaches a bin clogs it for a few seconds.</li>
 * </ul>
 */
final class MorphlingSkills {
    // Tide Surge
    private static final double SURGE_WARN = 1.3, SURGE_SPEED = 230, SURGE_HALF = 30;
    private static final double SURGE_EVERY_P2 = 11, SURGE_EVERY_P3 = 9, SURGE_FIRST = 4;
    private static final double SURGE_CARRY = 0.4, SURGE_PUSH = 170, SURGE_SLOW = 2.0;
    // Sludgelings
    private static final double SLUDGE_EVERY = 13, SLUDGE_FIRST = 3, SLUDGE_SPEED = 18;
    private static final double SLUDGE_FLIGHT = 0.8, SLUDGE_ARC = 34, CLOG_TIME = 6;
    private static final int SLUDGE_PER_CAST = 2, SLUDGE_MAX = 3;

    private static final int[] SLIME = {0x3E6B3A, 0x5C8A45, 0x2C4A2C, 0x86B05A};
    private static final int[] FOAM = {0xE8FFF8, 0xBDEFE6, 0x8FD8D2};

    /** A slime blob: flies from the Morphling's mouth to the sand, then crawls to its bin. */
    private static final class Blob {
        final TrashBin bin;
        final double sx, sy, lx, ly;
        double x, y, z, age;
        boolean landed;

        Blob(TrashBin bin, double sx, double sy, double lx, double ly) {
            this.bin = bin;
            this.sx = sx; this.sy = sy;
            this.lx = lx; this.ly = ly;
            x = sx; y = sy;
        }

        double targetX() { return bin.centerX(); }
        double targetY() { return bin.getY() + bin.bounds().height - 2; }
    }

    private double time;
    private int phase;
    private double surgeCooldown, sludgeCooldown;
    /** When the current surge's warning began (-1 = no surge), and the middle of its band. */
    private double surgeAt = -1, surgeY;
    private boolean surgeHit;
    private double carry;
    private final List<Blob> blobs = new ArrayList<>();
    /** Clogged bins and the time each one clears. */
    private final Map<TrashBin, Double> clogs = new HashMap<>();
    private final BossParticles fx = new BossParticles();
    private String message;

    void reset() {
        time = 0;
        phase = 0;
        surgeCooldown = sludgeCooldown = 0;
        surgeAt = -1;
        surgeHit = false;
        carry = 0;
        blobs.clear();
        clogs.clear();
        fx.clear();
        message = null;
    }

    /** The boss is beaten: blobs burst, bins unclog, the tide stops. */
    void clear() {
        for (Blob b : blobs) pop(b);
        blobs.clear();
        clogs.clear();
        surgeAt = -1;
        carry = 0;
    }

    boolean isClogged(TrashBin bin) { return clogs.containsKey(bin); }

    String consumeMessage() {
        String m = message;
        message = null;
        return m;
    }

    /**
     * @param phase      1 (splash only), 2 (+ Tide Surge) or 3 (+ Sludgelings)
     * @param mouthX     where blobs are spat from
     * @param splashBusy a splash volley is being telegraphed, so a new surge waits a moment
     */
    void update(double dt, int phase, Player player, Level level, double mouthX, double mouthY, boolean splashBusy) {
        time += dt;
        if (phase != this.phase) {               // a new phase: its skill comes soon after
            if (phase >= 2 && this.phase < 2) surgeCooldown = SURGE_FIRST;
            if (phase >= 3 && this.phase < 3) sludgeCooldown = SLUDGE_FIRST;
            this.phase = phase;
        }
        updateSurge(dt, player, level, splashBusy);
        updateSludge(dt, player, level, mouthX, mouthY);
        for (Iterator<Map.Entry<TrashBin, Double>> it = clogs.entrySet().iterator(); it.hasNext(); ) {
            if (time >= it.next().getValue()) it.remove();
        }
        fx.update(dt);
    }

    // ------------------------------------------------------------ Tide Surge
    private void updateSurge(double dt, Player player, Level level, boolean splashBusy) {
        if (carry > 0) {                         // being swept back toward the land
            player.knockBack(-SURGE_PUSH * dt, 0, level);
            carry -= dt;
        }
        if (surgeAt < 0) {
            if (phase < 2) return;
            surgeCooldown -= dt;
            if (surgeCooldown > 0 || splashBusy) return;
            surgeAt = time;
            surgeHit = false;
            double feet = player.getY() + Player.H - 2;
            surgeY = clamp(feet + (fx.rng().nextDouble() * 2 - 1) * 14, 44 + SURGE_HALF, Config.WORLD_H - 6 - SURGE_HALF);
            message = "Tide surge! Step out of the foam line!";
            return;
        }
        double since = time - surgeAt;
        if (since < SURGE_WARN) return;
        double front = frontX();
        if (fx.rng().nextDouble() < dt * 40) {   // spray along the crest
            double y = surgeY + (fx.rng().nextDouble() * 2 - 1) * SURGE_HALF;
            fx.add(BossParticles.SPARK, front + 2, y, 2, -60 - fx.rng().nextDouble() * 40, 0, 30 + fx.rng().nextDouble() * 40,
                    0.5, FOAM[fx.rng().nextInt(FOAM.length)], 1);
        }
        double feet = player.getY() + Player.H - 2, px = player.centerX();
        if (!surgeHit && !player.isOnBoat() && Math.abs(feet - surgeY) <= SURGE_HALF
                && front <= px + 4 && front >= px - 40) {
            surgeHit = true;
            carry = SURGE_CARRY;
            player.applySlow(SURGE_SLOW);
            fx.burst(BossParticles.SPARK, px, feet, 4, 10, 50, 50, 0.6, FOAM);
            message = "The tide surge swept you back!";
        }
        if (front < -70) {
            surgeAt = -1;
            surgeCooldown = phase >= 3 ? SURGE_EVERY_P3 : SURGE_EVERY_P2;
        }
    }

    private double frontX() { return Config.WORLD_W + 10 - (time - surgeAt - SURGE_WARN) * SURGE_SPEED; }

    // ------------------------------------------------------------ Sludgelings
    private void updateSludge(double dt, Player player, Level level, double mouthX, double mouthY) {
        if (phase >= 3) {
            sludgeCooldown -= dt;
            if (sludgeCooldown <= 0 && blobs.size() < SLUDGE_MAX) {
                sludgeCooldown = SLUDGE_EVERY;
                int spat = 0;
                for (int i = 0; i < SLUDGE_PER_CAST && blobs.size() < SLUDGE_MAX; i++) {
                    TrashBin bin = pickBin(level);
                    if (bin == null) break;
                    double[] spot = landingSpot(level, bin);
                    blobs.add(new Blob(bin, mouthX, mouthY, spot[0], spot[1]));
                    spat++;
                }
                if (spat > 0) message = "Sludgelings! Pop them before they clog the bins!";
            }
        }
        double feet = player.getY() + Player.H - 2, px = player.centerX();
        for (Iterator<Blob> it = blobs.iterator(); it.hasNext(); ) {
            Blob b = it.next();
            b.age += dt;
            if (!b.landed) {
                double u = Math.min(1, b.age / SLUDGE_FLIGHT);
                b.x = b.sx + (b.lx - b.sx) * u;
                b.y = b.sy + (b.ly - b.sy) * u;
                b.z = Math.sin(u * Math.PI) * SLUDGE_ARC + (1 - u) * 18;
                if (u >= 1) {
                    b.landed = true;
                    b.z = 0;
                    fx.burst(BossParticles.BIT, b.x, b.y, 1, 6, 30, 30, 0.5, SLIME);
                }
                continue;
            }
            double dx = b.targetX() - b.x, dy = b.targetY() - b.y, d = Math.hypot(dx, dy);
            double step = SLUDGE_SPEED * (0.75 + 0.5 * Math.max(0, Math.sin(b.age * 7)));   // inches along
            if (Math.hypot(px - b.x, feet - 3 - b.y) < 10) {
                pop(b);
                it.remove();
                message = "Popped a sludgeling!";
            } else if (d <= Math.max(5, step * dt)) {
                clogs.put(b.bin, Math.max(clogs.getOrDefault(b.bin, 0.0), time + CLOG_TIME));
                fx.burst(BossParticles.BIT, b.x, b.y, 6, 10, 26, 40, 0.7, SLIME);
                it.remove();
                message = "A sludgeling clogged the " + b.bin.getType().getLabel() + " bin!";
            } else {
                b.x += dx / d * step * dt;
                b.y += dy / d * step * dt;
            }
        }
    }

    /** A bin no blob is already heading for, preferably one that is not clogged. */
    private TrashBin pickBin(Level level) {
        List<TrashBin> free = new ArrayList<>(), any = new ArrayList<>();
        for (TrashBin bin : level.getBins()) {
            boolean targeted = false;
            for (Blob b : blobs) if (b.bin == bin) targeted = true;
            if (targeted) continue;
            any.add(bin);
            if (!clogs.containsKey(bin)) free.add(bin);
        }
        List<TrashBin> from = free.isEmpty() ? any : free;
        return from.isEmpty() ? null : from.get(fx.rng().nextInt(from.size()));
    }

    /** A clear patch of sand 75-105 px from the bin, so the blob has a way to crawl. */
    private double[] landingSpot(Level level, TrashBin bin) {
        double tx = bin.centerX(), ty = bin.getY() + bin.bounds().height - 2;
        for (int attempt = 0; attempt < 80; attempt++) {
            double a = fx.rng().nextDouble() * Math.PI * 2, r = 75 + fx.rng().nextDouble() * 30;
            double x = tx + Math.cos(a) * r, y = ty + Math.sin(a) * r * 0.8;
            if (x < 16 || x > Config.WORLD_W - 16 || y < 52 || y > Config.WORLD_H - 14) continue;
            if (level instanceof Level4Beach beach && beach.isWaterAt(x, y)) continue;
            if (level.isBlocked(new Rectangle2D.Double(x - 5, y - 4, 10, 8))) continue;
            return new double[] {x, y};
        }
        return new double[] {tx + 80, ty};
    }

    private void pop(Blob b) {
        fx.burst(BossParticles.BIT, b.x, b.y, b.z + 3, 12, 40, 50, 0.6, SLIME);
        fx.burst(BossParticles.PUFF, b.x, b.y, b.z + 2, 4, 14, 10, 0.5, new int[] {0x6E8F55, 0x4F6E43});
    }

    // ------------------------------------------------------------ drawing
    void draw(Graphics2D g) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (Map.Entry<TrashBin, Double> clog : clogs.entrySet()) drawClog(gg, clog.getKey(), clog.getValue() - time);
        for (Blob b : blobs) drawBlob(gg, b);
        if (surgeAt >= 0) drawSurge(gg);
        fx.draw(gg);
        gg.dispose();
    }

    private void drawSurge(Graphics2D g) {
        double since = time - surgeAt;
        int top = (int) Math.round(surgeY - SURGE_HALF), height = (int) Math.round(SURGE_HALF * 2);
        if (since < SURGE_WARN) {                // the foam line: where the wave will roll through
            double pulse = 0.5 + 0.5 * Math.sin(since * 14);
            double in = Math.min(1, since / 0.25);
            g.setColor(new Color(90, 190, 220, (int) ((26 + 34 * pulse) * in)));
            g.fillRect(0, top, Config.WORLD_W, height);
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
                    new float[] {5f, 3f}, (float) ((time * 30) % 8)));
            g.setColor(new Color(232, 255, 248, (int) ((150 + 90 * pulse) * in)));
            g.drawLine(0, top, Config.WORLD_W, top);
            g.drawLine(0, top + height, Config.WORLD_W, top + height);
            g.setStroke(new BasicStroke(1.5f));
            int mid = top + height / 2;
            for (int i = 0; i < 9; i++) {        // chevrons drifting toward the land
                double x = (i * 60 - time * 70) % (Config.WORLD_W + 60);
                if (x < -10) x += Config.WORLD_W + 60;
                int cx = (int) Math.round(x);
                g.setColor(new Color(232, 255, 248, (int) ((90 + 80 * pulse) * in)));
                g.drawLine(cx + 6, mid - 6, cx, mid);
                g.drawLine(cx, mid, cx + 6, mid + 6);
            }
            g.setFont(new Font(Font.DIALOG, Font.BOLD, 8));
            String warn = "TIDE!";
            int tw = g.getFontMetrics().stringWidth(warn);
            g.setColor(new Color(20, 40, 60, (int) (170 * in)));
            g.drawString(warn, Config.WORLD_W - tw - 9, top - 2);
            g.setColor(new Color(255, 236, 214, (int) (255 * in)));
            g.drawString(warn, Config.WORLD_W - tw - 10, top - 3);
            return;
        }
        double front = frontX();
        int fx0 = (int) Math.round(front);
        for (int i = 0; i < 12; i++) {           // the water body, thinning out behind the crest
            int x0 = fx0 + 4 + i * 9;
            if (x0 > Config.WORLD_W) break;
            g.setColor(new Color(64, 168, 196, Math.max(0, 150 - i * 12)));
            g.fillRect(x0, top + 2, 9, height - 4);
        }
        g.setColor(new Color(150, 222, 232, 190));
        for (int y = top; y <= top + height; y += 2) {
            int wob = (int) Math.round(Math.sin(y * 0.35 + time * 11) * 3);
            g.fillRect(fx0 + wob + 1, y, 5, 2);
        }
        for (int y = top; y <= top + height; y += 2) {   // white foam crest
            int wob = (int) Math.round(Math.sin(y * 0.35 + time * 11) * 3 + Math.sin(y * 0.9 + time * 17));
            g.setColor((y / 2) % 3 == 0 ? new Color(255, 255, 255, 240) : new Color(220, 250, 244, 230));
            g.fillRect(fx0 + wob - 2, y, 3, 2);
        }
    }

    private void drawBlob(Graphics2D g, Blob b) {
        int x = (int) Math.round(b.x), y = (int) Math.round(b.y);
        if (!b.landed) {                         // where it will land
            double u = Math.min(1, b.age / SLUDGE_FLIGHT);
            g.setColor(new Color(120, 200, 90, (int) (90 + 120 * u)));
            g.drawOval((int) Math.round(b.lx) - 6, (int) Math.round(b.ly) - 3, 12, 6);
        }
        g.setColor(new Color(10, 20, 10, b.landed ? 80 : 50));
        g.fillOval(x - 6, y - 2, 12, 4);
        double wob = Math.sin(b.age * 7);
        int w = 11 + (int) Math.round(wob * 1.5), h = 8 - (int) Math.round(wob);
        int top = y - h - (int) Math.round(b.z) + 1;
        if (b.landed) {                          // slimy trail behind it
            double dx = b.targetX() - b.x, dy = b.targetY() - b.y, d = Math.max(1, Math.hypot(dx, dy));
            g.setColor(new Color(62, 107, 58, 120));
            g.fillOval((int) Math.round(b.x - dx / d * 7) - 2, (int) Math.round(b.y - dy / d * 7) - 1, 4, 2);
            g.fillOval((int) Math.round(b.x - dx / d * 12) - 1, (int) Math.round(b.y - dy / d * 12) - 1, 3, 2);
        }
        g.setColor(new Color(0x2C4A2C));
        g.fillOval(x - w / 2 - 1, top - 1, w + 2, h + 2);
        g.setColor(new Color(0x4E7A40));
        g.fillOval(x - w / 2, top, w, h);
        g.setColor(new Color(0x6E9A50));
        g.fillOval(x - w / 2 + 1, top + 1, w - 4, h - 3);
        g.setColor(new Color(0xA8D07A));
        g.fillRect(x - w / 2 + 2, top + 2, 2, 1);
        int bub = (int) ((b.age * 3) % 3);       // a bubble rising through it
        g.setColor(new Color(150, 200, 110, 180));
        g.fillRect(x + 1, top + h - 3 - bub, 1, 1);
        g.setColor(Color.WHITE);                 // eyes
        g.fillRect(x - 3, top + 2, 2, 2);
        g.fillRect(x + 1, top + 2, 2, 2);
        g.setColor(new Color(0x1A1A1A));
        int look = b.landed && b.targetX() < b.x ? 0 : 1;
        g.fillRect(x - 3 + look, top + 3, 1, 1);
        g.fillRect(x + 1 + look, top + 3, 1, 1);
    }

    private void drawClog(Graphics2D g, TrashBin bin, double left) {
        Rectangle2D.Double r = bin.bounds();
        int x = (int) Math.round(r.x), y = (int) Math.round(r.y), w = (int) r.width;
        double drip = (time * 1.6) % 1;
        g.setColor(new Color(0x2C4A2C));
        g.fillOval(x - 2, y - 4, w + 4, 9);
        g.setColor(new Color(0x4E7A40));
        g.fillOval(x - 1, y - 3, w + 2, 7);
        g.setColor(new Color(0x6E9A50));
        g.fillOval(x + 2, y - 2, w - 6, 3);
        g.setColor(new Color(0x4E7A40));        // drips running down the front
        g.fillRect(x + 2, y + 3, 2, 3 + (int) Math.round(drip * 4));
        g.fillRect(x + w - 5, y + 3, 2, 2 + (int) Math.round(((drip + 0.4) % 1) * 5));
        g.fillRect(x + w / 2, y + 3, 1, 2);
        int bub = (int) ((time * 2) % 2);
        g.setColor(new Color(0xA8D07A));
        g.fillRect(x + w / 2 - 2, y - 3 - bub, 2, 2);
        double share = Math.max(0, Math.min(1, left / CLOG_TIME));   // time until it clears
        g.setColor(new Color(20, 30, 20, 150));
        g.fillRect(x, y - 8, w, 2);
        g.setColor(new Color(0x9BD26A));
        g.fillRect(x, y - 8, (int) Math.round(w * share), 2);
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
