package com.ecoquest.level1;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.ecoquest.level1.Config.WORLD_H;
import static com.ecoquest.level1.Config.WORLD_W;

/**
 * The shared bookkeeping behind a {@link TrashBoss}: HP tied to the trash left, phases (phase 2 at
 * {@code 65%} of the most HP it has had, phase 3 at {@code 25%}; a phase, once reached, stays), hit and
 * heal flashes, the "+1" / "-1" numbers, and garbage bags flying along an arc to a telegraphed
 * spot. A boss owns one of these and forwards the {@link TrashBoss} queries to it.
 */
public final class BossCore {
    /** What a thrown object looks like. */
    public static final int BAG = 0, TOMATO = 1, FISH = 2, MANGO = 3, CABBAGE = 4, BOTTLE = 5, BATTERY = 6, OIL_CAN = 7;
    private static final double SPLAT_SECONDS = 0.45, FLOATER_SECONDS = 1.0, FLASH_SECONDS = 0.45;

    /** Something flying along an arc to a telegraphed spot; it lands as its {@link TrashBoss.Strike}. */
    private static final class Toss {
        final double sx, sy, tx, ty, flight, arc;
        final int kind;
        final TrashBoss.Strike strike;
        double age;

        Toss(double sx, double sy, double tx, double ty, double flight, double arc, int kind, TrashBoss.Strike strike) {
            this.sx = sx; this.sy = sy; this.tx = tx; this.ty = ty;
            this.flight = flight; this.arc = arc; this.kind = kind; this.strike = strike;
        }
    }

    private final List<Toss> tosses = new ArrayList<>();
    private final List<double[]> splats = new ArrayList<>();    // x, y, age, kind
    private final List<TrashBoss.Strike> strikes = new ArrayList<>();   // consumed by the game panel
    private final List<Object[]> floaters = new ArrayList<>();  // text, heal?, age

    /** Share of its peak HP at which it enters phase 2 and phase 3. */
    public static final double PHASE_2_SHARE = 0.65, PHASE_3_SHARE = 0.25;

    private int hp = -1, peakHp = 1, phase = 1;
    private double hitFlash, healFlash;
    private boolean defeatedPending, tossPending, dead;
    private int phaseChangePending, hpChangePending;
    private double lastPlayerX = Double.NaN, lastPlayerY, playerVX, playerVY;

    // ------------------------------------------------------------ HP
    /**
     * Sets HP to the trash still left. Fewer pieces than before = damage, more = healing.
     * Returns true when it just hit 0 (only once, and only when {@code canDie}).
     */
    public boolean syncHp(int trashLeft, boolean canDie) {
        if (dead) return false;
        int next = Math.max(0, trashLeft);
        if (hp >= 0 && next != hp) {
            int diff = next - hp;
            hpChangePending += diff;
            if (diff < 0) hitFlash = FLASH_SECONDS;
            else healFlash = FLASH_SECONDS;
            floaters.add(new Object[] { (diff > 0 ? "+" : "") + diff, diff > 0, 0.0 });
        }
        hp = next;
        peakHp = Math.max(peakHp, hp);
        int reached = hp <= PHASE_3_SHARE * peakHp ? 3 : hp <= PHASE_2_SHARE * peakHp ? 2 : 1;
        if (reached > phase) {          // only ever gets angrier
            phase = reached;
            phaseChangePending = reached;
        }
        if (hp == 0 && canDie) {
            dead = true;
            tosses.clear();
            defeatedPending = true;
            return true;
        }
        return false;
    }

    public int hp() { return Math.max(0, hp); }
    /** False until the first {@link #syncHp} (so "0 HP" is never assumed). */
    public boolean hpKnown() { return hp >= 0; }
    public int peakHp() { return peakHp; }
    public int phase() { return phase; }
    public boolean dead() { return dead; }
    /** HP as a share of the most it has had, 0..1. */
    public double hpShare() { return hp < 0 ? 1 : Math.min(1, hp / (double) Math.max(1, peakHp)); }
    public double hitFlash() { return hitFlash; }
    public double healFlash() { return healFlash; }

    public int consumeHpChange() { int v = hpChangePending; hpChangePending = 0; return v; }
    public int consumePhaseChange() { int v = phaseChangePending; phaseChangePending = 0; return v; }
    public boolean consumeDefeated() { boolean v = defeatedPending; defeatedPending = false; return v; }
    public boolean consumeToss() { boolean v = tossPending; tossPending = false; return v; }
    public List<TrashBoss.Strike> consumeStrikes() {
        List<TrashBoss.Strike> copy = new ArrayList<>(strikes);
        strikes.clear();
        return copy;
    }

    /** Something that hits the ground right now (a shockwave, a burst from below, a minion collapsing). */
    public void strike(TrashBoss.Strike s) { strikes.add(s); }

    /** True while anything is still in the air. */
    public boolean tossesInFlight() { return !tosses.isEmpty(); }

    // ------------------------------------------------------------ tosses
    /** Tracks the player's smoothed velocity so throws can lead them. Call every update. */
    public void update(double dt, Player player) {
        hitFlash = Math.max(0, hitFlash - dt);
        healFlash = Math.max(0, healFlash - dt);
        double px = player.centerX(), py = player.centerY();
        if (!Double.isNaN(lastPlayerX) && dt > 0) {
            double k = Math.min(1, dt * 6);
            playerVX += ((px - lastPlayerX) / dt - playerVX) * k;
            playerVY += ((py - lastPlayerY) / dt - playerVY) * k;
        }
        lastPlayerX = px;
        lastPlayerY = py;
        for (Iterator<Toss> it = tosses.iterator(); it.hasNext(); ) {
            Toss t = it.next();
            t.age += dt;
            if (t.age >= t.flight) {
                strikes.add(t.strike.at(t.tx, t.ty));
                splats.add(new double[] { t.tx, t.ty, 0, t.kind });
                it.remove();
            }
        }
        for (Iterator<double[]> it = splats.iterator(); it.hasNext(); ) {
            double[] s = it.next();
            s[2] += dt;
            if (s[2] >= SPLAT_SECONDS) it.remove();
        }
        for (Iterator<Object[]> it = floaters.iterator(); it.hasNext(); ) {
            Object[] f = it.next();
            f[2] = (double) f[2] + dt;
            if ((double) f[2] >= FLOATER_SECONDS) it.remove();
        }
    }

    /** Throws a garbage bag from (sx, sy) at the player's feet, {@code lead} seconds ahead of where they walk. */
    public void throwAtPlayer(double sx, double sy, Player player, double lead, double flight, double arc) {
        double[] t = leadTarget(player, lead, 0, 0);
        throwAt(sx, sy, t[0], t[1], flight, arc, BAG, TrashBoss.Strike.bag(0, 0));
    }

    /** Where the player's feet will be in {@code lead} seconds, shifted by (ox, oy), kept on the map. */
    public double[] leadTarget(Player player, double lead, double ox, double oy) {
        double r = TrashBoss.TOSS_RADIUS;
        double tx = Math.max(r, Math.min(WORLD_W - r, player.centerX() + playerVX * lead + ox));
        double ty = Math.max(r, Math.min(WORLD_H - r, player.getY() + Player.H - 3 + playerVY * lead + oy));
        return new double[] { tx, ty };
    }

    public void throwAt(double sx, double sy, double tx, double ty, double flight, double arc, int kind, TrashBoss.Strike strike) {
        tosses.add(new Toss(sx, sy, tx, ty, flight, arc, kind, strike));
        tossPending = true;
    }

    // ------------------------------------------------------------ drawing
    /** Bags in flight with their landing markers, impact splats, and HP numbers above (fx, fy). */
    public void drawEffects(Graphics2D g, double fx, double fy) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int R = TrashBoss.TOSS_RADIUS;
        for (double[] s : splats) {
            double k = s[2] / SPLAT_SECONDS;
            int alpha = (int) (200 * (1 - k));
            int r = (int) (R * (0.6 + 0.6 * k));
            int[] juice = splatColors((int) s[3]);
            gg.setColor(withAlpha(juice[0], alpha));
            gg.fillOval((int) s[0] - r, (int) s[1] - r / 2, r * 2, r);
            gg.setColor(withAlpha(juice[1], alpha));
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                gg.fillRect((int) (s[0] + Math.cos(a) * r * 1.1),
                        (int) (s[1] + Math.sin(a) * r * 0.55) - (int) (6 * Math.sin(k * Math.PI)), 2, 2);
            }
        }
        for (Toss t : tosses) {
            double k = Math.min(1, t.age / t.flight);
            int cx = (int) Math.round(t.tx), cy = (int) Math.round(t.ty);
            int mr = (int) Math.round(Math.max(6, t.strike.radius() - 2));
            double pulse = 0.5 + 0.5 * Math.sin(t.age * 18);
            // red marker for things that hurt, amber for things that only slow or stun
            Color fillCol = t.strike.damage() ? new Color(200, 30, 30, (int) (40 + 70 * k)) : new Color(230, 150, 30, (int) (40 + 70 * k));
            Color ringCol = t.strike.damage() ? new Color(255, 60, 50, (int) (150 + 100 * pulse)) : new Color(255, 190, 60, (int) (150 + 100 * pulse));
            gg.setColor(fillCol);
            int fill = (int) (mr * k);
            gg.fillOval(cx - fill, cy - fill / 2, fill * 2, fill);
            gg.setStroke(new BasicStroke(1.2f));
            gg.setColor(ringCol);
            gg.drawOval(cx - mr, cy - mr / 2, mr * 2, mr);
            double bx = t.sx + (t.tx - t.sx) * k;
            double groundY = t.sy + (t.ty - t.sy) * k;
            double by = groundY - Math.sin(k * Math.PI) * t.arc;
            gg.setColor(new Color(0, 0, 0, (int) (40 + 60 * k)));
            int shadow = 3 + (int) (4 * k);
            gg.fillOval((int) bx - shadow, (int) groundY - 1, shadow * 2, 3);
            drawThrown(gg, t.kind, (int) Math.round(bx), (int) Math.round(by), t.age * 9);
        }
        gg.setFont(new Font(Font.DIALOG, Font.BOLD, 9));
        for (int i = 0; i < floaters.size(); i++) {
            Object[] f = floaters.get(i);
            String text = (String) f[0];
            double k = (double) f[2] / FLOATER_SECONDS;
            int alpha = (int) (255 * Math.min(1, (1 - k) * 2));
            int x = (int) fx - gg.getFontMetrics().stringWidth(text) / 2 + (i % 2 == 0 ? -6 : 6);
            int y = (int) (fy - k * 14);
            gg.setColor(new Color(0, 0, 0, alpha / 2));
            gg.drawString(text, x + 1, y + 1);
            gg.setColor((boolean) f[1] ? new Color(255, 120, 90, alpha) : new Color(140, 255, 140, alpha));
            gg.drawString(text, x, y);
        }
        gg.dispose();
    }

    private static void drawThrown(Graphics2D g, int kind, int cx, int cy, double spin) {
        AffineTransform at = g.getTransform();
        switch (kind) {
            case TOMATO -> {
                g.rotate(spin, cx, cy);
                g.setColor(new Color(0x5A1410)); g.fillOval(cx - 4, cy - 4, 8, 8);
                g.setColor(new Color(0xC8402E)); g.fillOval(cx - 3, cy - 3, 6, 6);
                g.setColor(new Color(0xF08060)); g.fillRect(cx - 2, cy - 2, 2, 1);
                g.setColor(new Color(0x6E7A2A)); g.fillRect(cx - 1, cy - 5, 2, 2);   // mouldy stalk
                g.setColor(new Color(0x7E8A3A)); g.fillRect(cx + 1, cy, 2, 2);       // rot spot
            }
            case FISH -> {
                g.rotate(spin * 0.8, cx, cy);
                g.setColor(new Color(0x2A2E34)); g.fillOval(cx - 6, cy - 3, 11, 6);
                g.setColor(new Color(0x9AA8B4)); g.fillOval(cx - 5, cy - 2, 9, 4);
                g.setColor(new Color(0xD8E2EA)); g.fillRect(cx - 4, cy - 2, 5, 1);
                g.setColor(new Color(0x2A2E34)); g.fillPolygon(new int[]{cx + 4, cx + 8, cx + 8}, new int[]{cy, cy - 3, cy + 3}, 3);
                g.setColor(Color.BLACK); g.fillRect(cx - 4, cy - 1, 1, 1);
            }
            case MANGO -> {
                g.rotate(spin, cx, cy);
                g.setColor(new Color(0x5A3A08)); g.fillOval(cx - 4, cy - 5, 8, 10);
                g.setColor(new Color(0xF0A030)); g.fillOval(cx - 3, cy - 4, 6, 8);
                g.setColor(new Color(0xF8D060)); g.fillRect(cx - 2, cy - 3, 2, 2);
                g.setColor(new Color(0x6A4A1A)); g.fillRect(cx, cy + 1, 2, 2);       // bruise
            }
            case BOTTLE -> {                // a plastic bottle spike, spinning end over end
                g.rotate(spin * 1.3, cx, cy);
                g.setColor(new Color(0x23495F)); g.fillRoundRect(cx - 5, cy - 2, 10, 5, 3, 3);
                g.setColor(new Color(0x76BCE2)); g.fillRoundRect(cx - 4, cy - 1, 7, 3, 2, 2);
                g.setColor(new Color(0xD2F0FF)); g.fillRect(cx - 3, cy - 1, 4, 1);
                g.setColor(new Color(0xD8402E)); g.fillRect(cx + 4, cy - 1, 2, 3);   // cap
            }
            case BATTERY -> {               // a leaking car battery, tumbling
                g.rotate(spin * 0.9, cx, cy);
                g.setColor(new Color(0x111214)); g.fillRect(cx - 5, cy - 4, 10, 8);
                g.setColor(new Color(0x2E3134)); g.fillRect(cx - 4, cy - 3, 8, 6);
                g.setColor(new Color(0xE8C440)); g.fillRect(cx - 3, cy, 6, 1);       // label stripe
                g.setColor(new Color(0xD8402E)); g.fillRect(cx - 4, cy - 5, 2, 1);   // + terminal
                g.setColor(new Color(0x111111)); g.fillRect(cx + 2, cy - 5, 2, 1);   // - terminal
                g.setColor(new Color(0x9AD04A)); g.fillRect(cx + 1, cy + 4, 1, 1);   // acid drip
            }
            case OIL_CAN -> {               // a motor-oil can, sloshing out a black trail
                g.rotate(spin * 0.7, cx, cy);
                g.setColor(new Color(0x14100E)); g.fillRect(cx - 4, cy - 5, 8, 10);
                g.setColor(new Color(0x2C6E3A)); g.fillRect(cx - 3, cy - 4, 6, 8);
                g.setColor(new Color(0xE8C440)); g.fillRect(cx - 3, cy - 1, 6, 2);   // label
                g.setColor(new Color(0x8D959A)); g.fillRect(cx + 1, cy - 7, 2, 2);   // spout
                g.setColor(new Color(0x0E0C10)); g.fillRect(cx + 2, cy - 9, 1, 2);   // oil glug
            }
            case CABBAGE -> {
                g.rotate(spin * 0.6, cx, cy);
                g.setColor(new Color(0x1E3A18)); g.fillOval(cx - 5, cy - 4, 10, 9);
                g.setColor(new Color(0x7CC46A)); g.fillOval(cx - 4, cy - 3, 8, 7);
                g.setColor(new Color(0xB4E89E)); g.fillRect(cx - 2, cy - 2, 3, 1);
                g.setColor(new Color(0x4E6E2A)); g.fillRect(cx + 1, cy + 1, 2, 2);
            }
            default -> {
                g.rotate(Math.sin(spin) * 0.5, cx, cy);
                g.setColor(new Color(34, 48, 36));
                g.fillOval(cx - 4, cy - 3, 9, 8);
                g.setColor(new Color(58, 80, 60));
                g.fillOval(cx - 3, cy - 2, 4, 3);
                g.setColor(new Color(220, 200, 90));
                g.fillRect(cx - 1, cy - 5, 3, 2);
            }
        }
        g.setTransform(at);
    }

    /** Splat colours (blob, droplets) per thrown kind. */
    private static int[] splatColors(int kind) {
        return switch (kind) {
            case TOMATO -> new int[] { 0xA8302A, 0xE0604A };
            case FISH -> new int[] { 0x7A8890, 0xB8C8D0 };
            case MANGO -> new int[] { 0xD08A20, 0xF4C050 };
            case CABBAGE -> new int[] { 0x5E9A4A, 0x9AD47A };
            case BOTTLE -> new int[] { 0x4E8FB4, 0xD2F0FF };
            case BATTERY -> new int[] { 0x6E8A2A, 0x9AD04A };
            case OIL_CAN -> new int[] { 0x0E0C10, 0x3A3448 };
            default -> new int[] { 0x5C6E32, 0x96AA50 };
        };
    }

    private static Color withAlpha(int rgb, int a) {
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.max(0, Math.min(255, a)));
    }
}
