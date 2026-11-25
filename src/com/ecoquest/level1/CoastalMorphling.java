package com.ecoquest.level1;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * The Level 4 boss, drawn from the single water_morphling.png sprite. The sprite is cut into a
 * body, two arms and the puddle it stands in, so each skill gets its own animation:
 * <ul>
 *   <li>{@link Cast#SPLASH}: winds its right arm back while a water ball grows in its hand, then
 *   hurls it; the drops fly out of the hand and burst up as geysers where they land.</li>
 *   <li>{@link Cast#SURGE}: rises with both arms up and glowing eyes while water swirls around
 *   it, then slams down and sends the tide in.</li>
 *   <li>{@link Cast#SPIT}: puffs up with sludge, then lunges and spits the sludgelings.</li>
 *   <li>{@link Cast#ROAR}: roars when a new wave of trash washes in.</li>
 *   <li>{@link Cast#BURROW}: sinks into the sand and tunnels after the player (a moving mound and a
 *   warning ring that follows them, then locks), bursting out there and slowing anyone it surfaces under.</li>
 * </ul>
 * It also breathes and sways while idle, and flinches when the player sorts its wave litter.
 */
final class CoastalMorphling {
    enum Cast { SPLASH, SURGE, SPIT, ROAR, BURROW }

    /** Seconds the Morphling puffs up before the sludgelings fly out (see {@link MorphlingSkills}). */
    static final double SPIT_WINDUP = 0.5;
    /** Seconds it charges before the tide surge rolls in (matches the surge's foam-line warning). */
    static final double SURGE_CHARGE = 1.3;

    private static final double SURGE_LENGTH = SURGE_CHARGE + 0.55, SPIT_LENGTH = SPIT_WINDUP + 0.5;
    private static final double ROAR_LENGTH = 1.1, FLINCH_LENGTH = 0.32;
    /** Burrow: sinking, tunnelling, bursting out (seconds). */
    static final double BURROW_SINK = 0.7, BURROW_TRAVEL = 1.8, BURROW_EMERGE = 0.6;
    private static final double BURROW_LENGTH = BURROW_SINK + BURROW_TRAVEL + BURROW_EMERGE;
    /** The exit follows the player underground until this long before it bursts out, then locks. */
    static final double BURROW_LOCK = 0.85;
    /** How far below the ground the body goes when burrowed (px). */
    private static final double BURROW_DEPTH = 64;
    /** Where it stands when the fight starts (top-left of the 80x72 sprite). */
    private static final double HOME_X = 224, HOME_Y = 94;
    private static final int[] SAND = {0xEAC47A, 0xD6B169, 0xB8925A, 0x8A6A3A};
    /** Share of the splash cast at which the arm snaps forward and the drops leave the hand. */
    private static final double SPLASH_RELEASE = 0.5;

    // Drawn size in the world, and points on the 512x512 source sprite.
    private static final int DRAW_W = 80, DRAW_H = 72, SRC = 512;
    private static final double SX = DRAW_W / (double) SRC, SY = DRAW_H / (double) SRC;
    private static final int ANCHOR_X = 256, ANCHOR_Y = 400;            // the body's foot: squash / lean pivot
    private static final int R_SHOULDER_X = 346, R_SHOULDER_Y = 292;    // raised arm (viewer's right)
    private static final int L_SHOULDER_X = 166, L_SHOULDER_Y = 262;    // lower arm (viewer's left)
    private static final int HAND_X = 424, HAND_Y = 140;
    private static final int MOUTH_X = 250, MOUTH_Y = 302;
    private static final int[][] EYES = {{192, 231, 32, 15}, {258, 231, 32, 15}};

    private static final int[] SPRAY = {0xCBFFF2, 0x81EFE4, 0x5DCAC7};
    private static final int[] SLUDGE = {0x3E6B3A, 0x5C8A45, 0x86B05A};

    /** One frame's pose, layered from the idle motion, the current cast and a hit flinch. */
    private static final class Pose {
        double squashX = 1, squashY = 1, lean, lift, shake;
        double rightArm, leftArm;        // radians, clockwise on screen
        double glow, mouth, orb, sludge, flash;
        double alpha = 1;
    }

    private final BufferedImage body, rightArm, leftArm, puddle;
    private final BufferedImage bodyWhite, rightArmWhite, leftArmWhite;
    private final BossParticles fx = new BossParticles();

    private Cast cast, queued;
    private double castTime, castLength, flinchTime;
    private boolean released;
    private double releaseX, releaseY;
    private double pendingShake;
    /** Where it stands now (moves when it burrows), and the burrow's start and end. */
    private double baseX = HOME_X, baseY = HOME_Y;
    private double fromX, fromY, toX, toY;
    private boolean emerged;

    CoastalMorphling() {
        BufferedImage loaded = null;
        try { loaded = ImageIO.read(new File("assets/water_morphling.png")); }
        catch (Exception ignored) { }
        if (loaded == null) {
            body = rightArm = leftArm = puddle = bodyWhite = rightArmWhite = leftArmWhite = null;
            return;
        }
        BufferedImage src = new BufferedImage(SRC, SRC, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = src.createGraphics();
        g.drawImage(loaded, 0, 0, SRC, SRC, null);
        g.dispose();
        body = part(src, 0);
        rightArm = part(src, 1);
        leftArm = part(src, 2);
        puddle = part(src, 3);
        bodyWhite = white(body);
        rightArmWhite = white(rightArm);
        leftArmWhite = white(leftArm);
    }

    // ------------------------------------------------------------ sprite parts
    /** 0 = body, 1 = raised arm, 2 = lower arm, 3 = puddle. Arms overlap the body a little at the shoulder. */
    private static BufferedImage part(BufferedImage src, int which) {
        BufferedImage out = new BufferedImage(SRC, SRC, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SRC; y++) {
            for (int x = 0; x < SRC; x++) {
                int argb = src.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                boolean inPuddle = y >= ANCHOR_Y;
                boolean inRight = !inPuddle && isRightArm(x, y);
                boolean inLeft = !inPuddle && isLeftArm(x, y);
                boolean keep = switch (which) {
                    case 0 -> !inPuddle && !inRight && !inLeft;
                    case 1 -> inRight || (!inPuddle && x >= 326 && y >= 244 && y < 312);   // shoulder overlap
                    case 2 -> inLeft || (!inPuddle && x < 180 && y >= 250 && y < 330);
                    default -> inPuddle;
                };
                if (keep) out.setRGB(x, y, argb);
            }
        }
        return out;
    }

    private static boolean isRightArm(int x, int y) {
        return y < 312 && ((x >= 345 && y >= 165) || (x >= 390 && y >= 110));
    }

    private static boolean isLeftArm(int x, int y) { return x < 160 && y >= 225 && y < 385; }

    private static BufferedImage white(BufferedImage part) {
        BufferedImage out = new BufferedImage(SRC, SRC, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SRC; y++)
            for (int x = 0; x < SRC; x++)
                if ((part.getRGB(x, y) >>> 24) != 0) out.setRGB(x, y, 0xFFFFFFFF);
        return out;
    }

    // ------------------------------------------------------------ casting
    /**
     * Starts a skill's animation. A roar never interrupts another skill (it waits for it instead);
     * the others are only started by the game while {@link #isCasting()} is false.
     */
    void startCast(Cast next) {
        if (next == Cast.ROAR && cast != null) {
            queued = Cast.ROAR;
            return;
        }
        cast = next;
        castTime = 0;
        released = false;
        castLength = switch (next) {
            case SPLASH -> WaterImpact.WARNING_SECONDS;
            case SURGE -> SURGE_LENGTH;
            case SPIT -> SPIT_LENGTH;
            case ROAR -> ROAR_LENGTH;
            case BURROW -> BURROW_LENGTH;
        };
    }

    /** Burrows and comes up with its sprite's top-left at (x, y). */
    void startBurrow(double x, double y) {
        fromX = baseX;
        fromY = baseY;
        toX = x;
        toY = y;
        startCast(Cast.BURROW);
    }

    /** While tunnelling it homes in on the player; the warning ring stops moving shortly before it surfaces. */
    boolean isTrackingTarget() {
        return cast == Cast.BURROW && castTime < BURROW_SINK + BURROW_TRAVEL - BURROW_LOCK;
    }

    /** Moves the exit (sprite top-left) while {@link #isTrackingTarget()}. */
    void retarget(double x, double y) {
        if (!isTrackingTarget()) return;
        toX = x;
        toY = y;
    }

    /** True once, right after it bursts out of the sand (for the slow check). */
    boolean consumeEmerged() {
        boolean e = emerged;
        emerged = false;
        return e;
    }

    /** Below the sand: it can't be touched and isn't drawn. */
    boolean isUnderground() {
        return cast == Cast.BURROW && castTime > BURROW_SINK * 0.7 && castTime < BURROW_SINK + BURROW_TRAVEL;
    }

    /** Middle of the spot it will burst out of (feet level), for the hit check. */
    double emergeX() { return toX + 40; }
    double emergeY() { return toY + 62; }

    boolean isCasting() { return cast != null; }

    /** The player sorted a piece of its wave litter: a short recoil and white flash. */
    void flinch() { flinchTime = FLINCH_LENGTH; }

    void reset() {
        baseX = HOME_X;
        baseY = HOME_Y;
        emerged = false;
        cast = queued = null;
        castTime = flinchTime = pendingShake = 0;
        released = false;
        fx.clear();
    }

    /** Screen shake asked for since the last call (0 = none). */
    double consumeShake() {
        double s = pendingShake;
        pendingShake = 0;
        return s;
    }

    void update(double dt, double time) {
        flinchTime = Math.max(0, flinchTime - dt);
        fx.update(dt);
        if (cast == null) {
            if (queued != null) {
                Cast next = queued;
                queued = null;
                startCast(next);
            }
            return;
        }
        double before = castTime / castLength;
        castTime += dt;
        double u = castTime / castLength;
        onCastProgress(time, before, u);
        if (castTime >= castLength) cast = null;
    }

    /** One-off effects at fixed moments of each cast. */
    private void onCastProgress(double time, double before, double u) {
        double x = x(time), y = y(time);
        switch (cast) {
            case SPLASH -> {
                if (before < SPLASH_RELEASE && u >= SPLASH_RELEASE) {
                    Point2D hand = handPoint(time, pose(time));
                    releaseX = hand.getX();
                    releaseY = hand.getY();
                    released = true;
                    fx.burst(BossParticles.SPARK, releaseX, releaseY + 14, 14, 12, 60, 30, 0.5, SPRAY);
                }
            }
            case SURGE -> {
                double slam = SURGE_CHARGE / SURGE_LENGTH;
                if (u < slam && fx.rng().nextDouble() < 0.6) {      // water spiralling up around it
                    double a = fx.rng().nextDouble() * Math.PI * 2;
                    fx.add(BossParticles.SPARK, x + 40 + Math.cos(a) * 30, y + 62 + Math.sin(a) * 8, 2,
                            -Math.sin(a) * 18, 0, 55 + fx.rng().nextDouble() * 35, 0.7,
                            SPRAY[fx.rng().nextInt(SPRAY.length)], 1);
                }
                if (before < slam && u >= slam) {
                    fx.burst(BossParticles.SPARK, x + 40, y + 64, 4, 28, 110, 60, 0.7, SPRAY);
                    fx.burst(BossParticles.PUFF, x + 40, y + 64, 2, 8, 46, 6, 0.6, new int[] {0xBDEFE6, 0x8FD8D2});
                    pendingShake = 2.5;
                }
            }
            case SPIT -> {
                double spit = SPIT_WINDUP / SPIT_LENGTH;
                if (before < spit && u >= spit) {
                    double mx = x + MOUTH_X * SX - 3, my = y + MOUTH_Y * SY;
                    fx.burst(BossParticles.BIT, mx, my + 18, 18, 10, 55, 40, 0.6, SLUDGE);
                    fx.burst(BossParticles.PUFF, mx, my + 18, 18, 3, 16, 8, 0.5, new int[] {0x6E8F55, 0x4F6E43});
                }
            }
            case ROAR -> {
                if (before < 0.18 && u >= 0.18) pendingShake = 1.6;
            }
            case BURROW -> {
                double sinkEnd = BURROW_SINK / BURROW_LENGTH, up = (BURROW_SINK + BURROW_TRAVEL) / BURROW_LENGTH;
                if (u < sinkEnd && fx.rng().nextDouble() < 0.5)                 // sand churning as it sinks
                    fx.add(BossParticles.BIT, x + 40 + (fx.rng().nextDouble() * 2 - 1) * 30, y + 62, 1,
                            (fx.rng().nextDouble() * 2 - 1) * 50, (fx.rng().nextDouble() * 2 - 1) * 14,
                            40 + fx.rng().nextDouble() * 40, 0.6, SAND[fx.rng().nextInt(SAND.length)], 1);
                double t = castTime, moundStart = BURROW_SINK * 0.8, moundEnd = BURROW_SINK + BURROW_TRAVEL;
                if (t > moundStart && t < moundEnd && fx.rng().nextDouble() < 0.5) {   // sand kicked up by the mound
                    double k = smooth((t - moundStart) / (moundEnd - moundStart));
                    double mx = fromX + 40 + (toX - fromX) * k, my = fromY + 62 + (toY - fromY) * k;
                    fx.add(BossParticles.BIT, mx + (fx.rng().nextDouble() * 2 - 1) * 10, my, 2,
                            (fx.rng().nextDouble() * 2 - 1) * 30, 0, 30 + fx.rng().nextDouble() * 30, 0.5,
                            SAND[fx.rng().nextInt(SAND.length)], 1);
                }
                if (before < up && u >= up) {                                    // bursts out at the new spot
                    baseX = toX;
                    baseY = toY;
                    emerged = true;
                    pendingShake = 2.8;
                    fx.burst(BossParticles.BIT, toX + 40, toY + 62, 2, 30, 120, 90, 0.9, SAND);
                    fx.burst(BossParticles.PUFF, toX + 40, toY + 62, 2, 10, 40, 8, 0.8, new int[] {0xE8CA83, 0xD6B169});
                }
            }
        }
    }

    // ------------------------------------------------------------ pose
    private Pose pose(double time) {
        Pose p = new Pose();
        double breath = Math.sin(time * 2.3);
        p.squashY = 1 + 0.025 * breath;
        p.squashX = 1 - 0.02 * breath;
        p.rightArm = 0.07 * Math.sin(time * 1.7);
        p.leftArm = 0.06 * Math.sin(time * 1.4 + 1.2);
        if (cast != null) applyCast(p, time, Math.min(1, castTime / castLength));
        if (flinchTime > 0) {
            double f = flinchTime / FLINCH_LENGTH;
            p.lean += 0.07 * f;
            p.squashX += 0.06 * f;
            p.squashY -= 0.06 * f;
            p.flash = Math.max(p.flash, 0.75 * f);
        }
        return p;
    }

    private void applyCast(Pose p, double time, double u) {
        switch (cast) {
            case SPLASH -> {
                double r = SPLASH_RELEASE;
                if (u < r) {                                        // wind up: arm back, ball grows
                    double w = smooth(u / r);
                    p.rightArm += 0.55 * w;
                    p.leftArm += 0.2 * w;
                    p.lean += 0.07 * w;
                    p.squashX += 0.06 * w;
                    p.squashY -= 0.08 * w;
                    p.orb = w;
                    p.glow = 0.5 * w;
                } else if (u < r + 0.16) {                          // hurl: arm snaps over, body stretches
                    double s = smooth((u - r) / 0.16);
                    p.rightArm += 0.55 - 1.35 * s;
                    p.leftArm += 0.2 - 0.3 * s;
                    p.lean += 0.07 - 0.17 * s;
                    p.squashX += 0.06 - 0.12 * s;
                    p.squashY += -0.08 + 0.2 * s;
                    p.mouth = s;
                    p.glow = 0.5 + 0.5 * s;
                } else {                                            // follow through and settle
                    double s = smooth((u - r - 0.16) / (1 - r - 0.16));
                    p.rightArm += -0.8 * (1 - s);
                    p.leftArm += -0.1 * (1 - s);
                    p.lean += -0.1 * (1 - s);
                    p.squashX += -0.06 * (1 - s);
                    p.squashY += 0.12 * (1 - s);
                    p.mouth = 1 - s;
                    p.glow = 1 - s;
                }
            }
            case SURGE -> {
                double slam = SURGE_CHARGE / SURGE_LENGTH;
                if (u < slam) {                                     // rise and call the tide
                    double r = smooth(Math.min(1, u / (slam * 0.7)));
                    double charge = u / slam;
                    p.lift = 8 * r;
                    p.squashY += 0.13 * r;
                    p.squashX -= 0.07 * r;
                    p.rightArm += -0.4 * r + 0.05 * Math.sin(time * 18);
                    p.leftArm += 1.15 * r + 0.05 * Math.sin(time * 18 + 1);
                    p.glow = r;
                    p.mouth = 0.4 * r;
                    p.shake = charge > 0.6 ? Math.sin(time * 55) * 1.2 * (charge - 0.6) / 0.4 : 0;
                } else if (u < slam + 0.08) {                       // slam down into the water
                    double s = smooth((u - slam) / 0.08);
                    p.lift = 8 - 10 * s;
                    p.squashY += 0.13 - 0.33 * s;
                    p.squashX += -0.07 + 0.3 * s;
                    p.rightArm += -0.4 + 0.95 * s;
                    p.leftArm += 1.15 - 1.5 * s;
                    p.glow = 1;
                    p.mouth = 1;
                } else {                                            // recover
                    double s = smooth((u - slam - 0.08) / (1 - slam - 0.08));
                    p.lift = -2 * (1 - s);
                    p.squashY += -0.2 * (1 - s);
                    p.squashX += 0.23 * (1 - s);
                    p.rightArm += 0.55 * (1 - s);
                    p.leftArm += -0.35 * (1 - s);
                    p.glow = 1 - s;
                    p.mouth = 1 - s;
                }
            }
            case SPIT -> {
                double spit = SPIT_WINDUP / SPIT_LENGTH;
                if (u < spit) {                                     // puff up full of sludge
                    double w = smooth(u / spit);
                    p.lean += 0.08 * w;
                    p.squashX += 0.1 * w + 0.015 * Math.sin(time * 40) * w;
                    p.squashY -= 0.05 * w;
                    p.leftArm += 0.25 * w;
                    p.rightArm += 0.2 * w;
                    p.sludge = w;
                } else if (u < spit + 0.12) {                       // lunge and spit
                    double s = smooth((u - spit) / 0.12);
                    p.lean += 0.08 - 0.22 * s;
                    p.squashX += 0.1 - 0.16 * s;
                    p.squashY += -0.05 + 0.14 * s;
                    p.leftArm += 0.25 - 0.4 * s;
                    p.rightArm += 0.2 - 0.3 * s;
                    p.mouth = s;
                    p.sludge = 1 - s;
                } else {
                    double s = smooth((u - spit - 0.12) / (1 - spit - 0.12));
                    p.lean += -0.14 * (1 - s);
                    p.squashX += -0.06 * (1 - s);
                    p.squashY += 0.09 * (1 - s);
                    p.leftArm += -0.15 * (1 - s);
                    p.rightArm += -0.1 * (1 - s);
                    p.mouth = 1 - s;
                }
            }
            case BURROW -> {
                double sinkEnd = BURROW_SINK / BURROW_LENGTH, up = (BURROW_SINK + BURROW_TRAVEL) / BURROW_LENGTH;
                if (u < sinkEnd) {                                      // wriggles down into the sand
                    double k = smooth(u / sinkEnd);
                    p.lift = -BURROW_DEPTH * k * k;
                    p.squashX += 0.18 * k;
                    p.squashY -= 0.1 * k;
                    p.shake = Math.sin(time * 50) * 1.5 * k;
                    p.rightArm += -0.5 * k;
                    p.leftArm += 0.8 * k;
                    p.glow = k;
                } else if (u < up) {
                    p.lift = -BURROW_DEPTH;                               // hidden underground
                } else {                                                 // shoots up, overshoots, settles
                    double k = (u - up) / (1 - up);
                    double rise = Math.min(1, k / 0.45);
                    p.lift = -BURROW_DEPTH * (1 - smooth(rise)) + (k < 0.6 ? Math.sin(k / 0.6 * Math.PI) * 8 : 0);
                    p.squashY += k < 0.6 ? 0.18 * Math.sin(k / 0.6 * Math.PI) : 0;
                    p.squashX -= k < 0.6 ? 0.1 * Math.sin(k / 0.6 * Math.PI) : 0;
                    p.rightArm += -0.6 * (1 - k);
                    p.leftArm += 1.0 * (1 - k);
                    p.mouth = 1 - k;
                    p.glow = 1 - k;
                }
            }
            case ROAR -> {
                double in = smooth(Math.min(1, u / 0.18)), out = u > 0.8 ? smooth((u - 0.8) / 0.2) : 0;
                double k = in * (1 - out);
                p.lift = 3 * k;
                p.squashY += 0.1 * k;
                p.squashX -= 0.04 * k;
                p.rightArm += -0.3 * k;
                p.leftArm += 0.6 * k;
                p.mouth = k;
                p.glow = k;
                p.shake = Math.sin(time * 60) * 1.0 * k;
            }
        }
    }

    // ------------------------------------------------------------ drawing
    void draw(Graphics2D g, double time) {
        draw(g, time, 0, List.of());
    }

    void draw(Graphics2D g, double time, double attackTime, List<double[]> targets) {
        int x = (int) Math.round(x(time)), y = (int) Math.round(y(time));
        Pose p = pose(time);
        boolean burrowing = cast == Cast.BURROW;
        if (burrowing) drawBurrow(g, time);
        if (!isUnderground()) {
            if (!burrowing) drawTideBurst(g, time, x + 40, y + 55);
            if (cast == Cast.SURGE) drawSurgeCharge(g, time, x + 40, y + 62);
            g.setColor(new Color(25, 108, 112, burrowing ? 70 : 120));
            g.fillOval(x - 7, y + 61, 94, 18);
            Graphics2D c = (Graphics2D) g.create();
            if (burrowing) c.clipRect(0, 0, Config.WORLD_W, y + 66);   // the part below the sand is hidden
            drawPosed(c, x(time), y(time), p);
            c.dispose();
        }
        if (cast == Cast.ROAR) drawRoarRings(g, x + MOUTH_X * SX, y + MOUTH_Y * SY, castTime / castLength);
        if (attackTime > 0 && !targets.isEmpty()) drawLaunchedWater(g, attackTime, targets);
        fx.draw(g);
    }

    void drawEntrance(Graphics2D g, double time, double progress) {
        double p = Math.max(0, Math.min(1, progress));
        double rise = p * p * (3 - 2 * p);
        int x = (int) Math.round(x(time)), baseY = (int) Math.round(y(time));
        int cx = x + 40, waterY = baseY + 62;
        drawEntranceSplash(g, time, p, cx, waterY);
        g.setColor(new Color(25, 108, 112, (int) (120 * rise)));
        g.fillOval(x - 4, waterY - 2, 78, 18);

        Pose pose = new Pose();
        pose.lift = -(1 - rise) * 38;
        pose.squashY = 0.8 + 0.2 * rise + 0.08 * Math.sin(p * Math.PI);
        pose.squashX = 1.12 - 0.12 * rise;
        pose.alpha = Math.min(1, 0.25 + rise * 0.9);
        double roar = p > 0.72 ? Math.sin((p - 0.72) / 0.28 * Math.PI) : 0;   // greets the player
        pose.rightArm = -0.35 * roar;
        pose.leftArm = 0.7 * roar;
        pose.mouth = roar;
        pose.glow = roar;
        pose.shake = Math.sin(time * 60) * roar;
        drawPosed(g, x(time), y(time), pose);
    }

    void drawDefeated(Graphics2D g, double time, double progress) {
        double p = Math.max(0, Math.min(1, progress));
        int x = (int) Math.round(x(time)), y = (int) Math.round(y(time));
        int cx = x + 40, waterY = y + 62 + (int) Math.round(p * 6);
        drawEntranceSplash(g, time * 1.8, 1 - p, cx, waterY);
        g.setColor(new Color(25, 108, 112, (int) (120 * (1 - p))));
        g.fillOval(x - 7, waterY - 2, 94, 18);
        if (p >= 0.95) return;
        Pose pose = new Pose();                                    // melts back into the sea
        pose.lift = -p * 30;
        pose.squashY = 1 - 0.45 * p;
        pose.squashX = 1 + 0.3 * p;
        pose.rightArm = 0.9 * p + 0.15 * Math.sin(time * 9) * (1 - p);
        pose.leftArm = -0.6 * p;
        pose.shake = Math.sin(time * 40) * 1.5 * (1 - p);
        pose.flash = p < 0.2 ? 0.6 * (1 - p / 0.2) : 0;
        pose.alpha = 1 - p;
        drawPosed(g, x(time), y(time), pose);
    }

    /** The sprite parts at the given pose. (x, y) is the top-left of the unposed 80x72 sprite. */
    private void drawPosed(Graphics2D g, double x, double y, Pose p) {
        if (body == null) return;
        Graphics2D c = (Graphics2D) g.create();
        c.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        if (p.alpha < 1) c.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, p.alpha)));

        // The puddle stays on the water; it only spreads when the body squashes.
        Graphics2D base = (Graphics2D) c.create();
        base.translate(x + ANCHOR_X * SX, y + ANCHOR_Y * SY);
        base.scale(0.55 + 0.45 * p.squashX, 1);
        base.translate(-ANCHOR_X * SX, -ANCHOR_Y * SY);
        base.scale(SX, SY);
        base.drawImage(puddle, 0, 0, null);
        base.dispose();

        c.transform(bodyTransform(x, y, p));
        drawArm(c, leftArm, leftArmWhite, L_SHOULDER_X, L_SHOULDER_Y, p.leftArm, p, false);
        drawArm(c, rightArm, rightArmWhite, R_SHOULDER_X, R_SHOULDER_Y, p.rightArm, p, true);
        c.drawImage(body, 0, 0, null);
        drawFace(c, p);
        if (p.flash > 0) {
            Graphics2D w = (Graphics2D) c.create();
            w.setComposite(AlphaComposite.SrcOver.derive((float) (Math.min(1, p.flash) * Math.max(0, p.alpha))));
            w.drawImage(bodyWhite, 0, 0, null);
            w.dispose();
        }
        c.dispose();
    }

    /** World transform that maps sprite pixels (0..512) onto the posed body. */
    private static AffineTransform bodyTransform(double x, double y, Pose p) {
        AffineTransform t = new AffineTransform();
        double ax = x + ANCHOR_X * SX, ay = y + ANCHOR_Y * SY;
        t.translate(ax + p.shake, ay - p.lift);
        t.rotate(p.lean);
        t.scale(p.squashX, p.squashY);
        t.translate(-ax, -ay);
        t.translate(x, y);
        t.scale(SX, SY);
        return t;
    }

    private void drawArm(Graphics2D c, BufferedImage arm, BufferedImage armWhite, int pivotX, int pivotY,
                         double angle, Pose p, boolean hand) {
        Graphics2D a = (Graphics2D) c.create();
        a.rotate(angle, pivotX, pivotY);
        a.drawImage(arm, 0, 0, null);
        if (p.flash > 0) {
            a.setComposite(AlphaComposite.SrcOver.derive((float) (Math.min(1, p.flash) * Math.max(0, p.alpha))));
            a.drawImage(armWhite, 0, 0, null);
            a.setComposite(AlphaComposite.SrcOver);
        }
        if (hand && p.orb > 0) drawOrb(a, p.orb);
        a.dispose();
    }

    /** A ball of sea water gathering in the raised hand (sprite coordinates). */
    private void drawOrb(Graphics2D a, double size) {
        int r = (int) Math.round(10 + 34 * size);
        int cx = HAND_X - 8, cy = HAND_Y - 18;
        a.setColor(new Color(129, 239, 228, (int) (90 * size)));
        a.fillOval(cx - r - 14, cy - r - 14, (r + 14) * 2, (r + 14) * 2);
        a.setColor(new Color(42, 160, 170, (int) (230 * size)));
        a.fillOval(cx - r, cy - r, r * 2, r * 2);
        a.setColor(new Color(129, 239, 228, (int) (240 * size)));
        a.fillOval(cx - r + 7, cy - r + 7, r * 2 - 14, r * 2 - 14);
        a.setColor(new Color(235, 255, 250, (int) (240 * size)));
        a.fillRect(cx - r / 2, cy - r / 2, Math.max(6, r / 3), Math.max(6, r / 3));
    }

    /** Glowing eyes and an opening mouth (sprite coordinates, already posed). */
    private void drawFace(Graphics2D c, Pose p) {
        if (p.sludge > 0) {                                         // cheeks bulging with sludge
            c.setColor(new Color(110, 168, 80, (int) (190 * p.sludge)));
            int r = (int) Math.round(14 + 10 * p.sludge);
            c.fillOval(MOUTH_X - 58 - r / 2, MOUTH_Y - 18, r, r);
            c.fillOval(MOUTH_X + 50 - r / 2, MOUTH_Y - 18, r, r);
        }
        if (p.mouth > 0.02) {
            int w = (int) Math.round(56 + 20 * p.mouth), h = (int) Math.round(8 + 38 * p.mouth);
            c.setColor(new Color(24, 38, 34, (int) (235 * Math.min(1, p.mouth * 2))));
            c.fillOval(MOUTH_X - w / 2, MOUTH_Y - h / 3, w, h);
            Color inner = cast == Cast.SPIT ? new Color(134, 176, 90) : new Color(104, 224, 220);
            c.setColor(new Color(inner.getRed(), inner.getGreen(), inner.getBlue(), (int) (200 * p.mouth)));
            c.fillOval(MOUTH_X - w / 4, MOUTH_Y + h / 8, w / 2, h / 3);
        }
        if (p.glow > 0.02) {
            for (int[] eye : EYES) {
                c.setColor(new Color(140, 255, 240, (int) (80 * p.glow)));
                c.fillRect(eye[0] - 10, eye[1] - 8, eye[2] + 20, eye[3] + 16);
                c.setColor(new Color(225, 255, 250, (int) (235 * p.glow)));
                c.fillRect(eye[0], eye[1], eye[2], eye[3]);
            }
        }
    }

    /** Where the splash drops leave from: the hand at the moment of the throw (world coordinates). */
    private Point2D handPoint(double time, Pose p) {
        AffineTransform t = bodyTransform(x(time), y(time), p);
        t.rotate(p.rightArm, R_SHOULDER_X, R_SHOULDER_Y);
        return t.transform(new Point2D.Double(HAND_X - 8, HAND_Y - 18), null);
    }

    boolean touches(double px, double py, double time, double radius) {
        if (isUnderground()) return false;
        return Math.hypot(px - (x(time) + 40), py - (y(time) + 36)) <= radius;
    }

    double x(double time) { return baseX + Math.sin(time * 0.28) * 5; }
    double y(double time) { return baseY + Math.sin(time * 0.36) * 3; }

    /** The hole it sinks into, the mound that tunnels across, and a warning ring where it will surface. */
    private void drawBurrow(Graphics2D g, double time) {
        double t = castTime, sinkEnd = BURROW_SINK, up = BURROW_SINK + BURROW_TRAVEL;
        int fx0 = (int) Math.round(fromX + 40), fy0 = (int) Math.round(fromY + 62);
        int tx = (int) Math.round(toX + 40), ty = (int) Math.round(toY + 62);
        if (t < up + 0.2) {                                     // dark hole where it went down
            double k = Math.min(1, t / sinkEnd) * Math.max(0, Math.min(1, (up + 0.2 - t) / 0.5));
            g.setColor(new Color(90, 66, 34, (int) (170 * k)));
            g.fillOval(fx0 - 26, fy0 - 7, 52, 14);
            g.setColor(new Color(60, 44, 22, (int) (200 * k)));
            g.fillOval(fx0 - 16, fy0 - 4, 32, 8);
        }
        if (t >= sinkEnd * 0.7) {                               // warning ring at the exit
            double warn = Math.min(1, (t - sinkEnd * 0.7) / 0.4) * (t < up ? 1 : Math.max(0, 1 - (t - up) / 0.3));
            double pulse = 0.5 + 0.5 * Math.sin(time * 16);
            g.setColor(new Color(214, 177, 105, (int) (110 * warn)));
            g.fillOval(tx - 36, ty - 22, 72, 44);
            g.setStroke(new java.awt.BasicStroke(2f));
            boolean locked = !isTrackingTarget();            // yellow while it follows you, red once it locks
            g.setColor(locked ? new Color(245, 60, 44, (int) ((150 + 100 * pulse) * warn))
                    : new Color(255, 214, 90, (int) (170 * warn)));
            g.drawOval(tx - 36, ty - 22, 72, 44);
            g.setStroke(new java.awt.BasicStroke(1f));
            g.setColor(new Color(120, 86, 44, (int) (200 * warn)));     // cracks in the sand
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + 0.3;
                int len = (int) Math.round(10 + 14 * Math.min(1, (t - sinkEnd) / BURROW_TRAVEL));
                g.drawLine(tx, ty, tx + (int) (Math.cos(a) * len), ty + (int) (Math.sin(a) * len * 0.6));
            }
        }
        if (t > sinkEnd * 0.8 && t < up) {                      // the mound tunnelling across
            double k = smooth((t - sinkEnd * 0.8) / (up - sinkEnd * 0.8));
            int mx = (int) Math.round(fx0 + (tx - fx0) * k), my = (int) Math.round(fy0 + (ty - fy0) * k);
            int wob = (int) Math.round(Math.sin(time * 30) * 1.5);
            g.setColor(new Color(0, 0, 0, 50));
            g.fillOval(mx - 15, my - 3, 30, 8);
            g.setColor(new Color(0xB8925A));
            g.fillOval(mx - 14 + wob, my - 9, 28, 12);
            g.setColor(new Color(0xEAC47A));
            g.fillOval(mx - 10 + wob, my - 10, 18, 7);
        }
    }

    /** Where sludgelings are spat from (the mouth, world coordinates). */
    double mouthX(double time) { return x(time) + MOUTH_X * SX - 3; }
    double mouthY(double time) { return y(time) + MOUTH_Y * SY; }

    private void drawTideBurst(Graphics2D g, double time, int cx, int cy) {
        for (int i = 0; i < 4; i++) {
            double phase = (time * 22 + i * 18) % 56;
            int radius = 12 + (int) phase;
            int alpha = Math.max(0, 115 - (int) phase * 2);
            g.setColor(new Color(104, 224, 220, alpha));
            g.drawOval(cx - radius, cy - radius / 2, radius * 2, radius);
        }
        for (int i = 0; i < 9; i++) {
            double angle = i * Math.PI * 2 / 9 + time * 0.7;
            double distance = 24 + 14 * Math.sin(time * 2.4 + i);
            int px = cx + (int) (Math.cos(angle) * distance);
            int py = cy + (int) (Math.sin(angle) * distance * 0.5);
            g.setColor(i % 2 == 0 ? new Color(157, 240, 226, 210) : new Color(71, 192, 192, 220));
            g.fillRect(px, py, 2, 2);
        }
    }

    /** A whirlpool tightening around its base while it calls the tide. */
    private void drawSurgeCharge(Graphics2D g, double time, int cx, int cy) {
        double u = castTime / castLength, slam = SURGE_CHARGE / SURGE_LENGTH;
        if (u >= slam) {                                            // shockwave after the slam
            double k = (u - slam) / (1 - slam);
            int r = (int) Math.round(20 + 70 * k);
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(220, 255, 248, (int) (220 * (1 - k))));
            g.drawOval(cx - r, cy - r / 3, r * 2, r * 2 / 3);
            g.setStroke(new BasicStroke(1f));
            return;
        }
        double charge = u / slam;
        for (int i = 0; i < 3; i++) {
            int r = (int) Math.round(48 - charge * 18 - i * 9);
            double spin = time * (5 + i * 2) + i;
            g.setColor(new Color(150, 240, 230, (int) (120 + 100 * charge) - i * 30));
            g.drawArc(cx - r, cy - r / 3, r * 2, r * 2 / 3, (int) Math.toDegrees(spin) % 360, 120);
            g.drawArc(cx - r, cy - r / 3, r * 2, r * 2 / 3, ((int) Math.toDegrees(spin) + 180) % 360, 120);
        }
    }

    private void drawRoarRings(Graphics2D g, double mx, double my, double u) {
        for (int i = 0; i < 3; i++) {
            double k = u * 1.6 - i * 0.22;
            if (k <= 0 || k >= 1) continue;
            int r = (int) Math.round(8 + 46 * k);
            g.setColor(new Color(200, 255, 245, (int) (170 * (1 - k))));
            g.drawOval((int) Math.round(mx - r), (int) Math.round(my - r * 0.6), r * 2, (int) Math.round(r * 1.2));
        }
    }

    private void drawLaunchedWater(Graphics2D g, double attackTime, List<double[]> targets) {
        double progress = Math.max(0, Math.min(1, 1 - attackTime / WaterImpact.WARNING_SECONDS));
        if (!released || progress < SPLASH_RELEASE) return;        // still in its hand
        double flight = (progress - SPLASH_RELEASE) / (1 - SPLASH_RELEASE);
        int startX = (int) Math.round(releaseX), startY = (int) Math.round(releaseY);
        int count = Math.min(8, targets.size());
        for (int i = 0; i < count; i++) {
            double local = Math.max(0, Math.min(1, flight * 1.2 - i * 0.025));
            if (local <= 0) continue;
            double[] target = targets.get(i);
            double px = startX + (target[0] - startX) * local;
            double py = startY + (target[1] - startY) * local - Math.sin(local * Math.PI) * 42;
            int size = 3 + (int) (2 * Math.sin(local * Math.PI));
            if (local < 1) {
                double tail = Math.max(0, local - 0.12);
                double tx = startX + (target[0] - startX) * tail;
                double ty = startY + (target[1] - startY) * tail - Math.sin(tail * Math.PI) * 42;
                g.setColor(new Color(122, 231, 221, 120));
                g.drawLine((int) tx, (int) ty, (int) px, (int) py);
            }
            g.setColor(i % 2 == 0 ? new Color(202, 255, 241, 235) : new Color(93, 202, 199, 240));
            g.fillOval((int) px - size / 2, (int) py - size / 2, size, size);
            if (local > 0.8) {
                int alpha = (int) (190 * (1 - local));
                g.setColor(new Color(202, 255, 241, Math.max(0, alpha)));
                g.fillOval((int) target[0] - 4, (int) target[1] - 4, 8, 8);
            }
        }
    }

    private void drawEntranceSplash(Graphics2D g, double time, double progress, int cx, int cy) {
        double burst = Math.sin(progress * Math.PI);
        for (int i = 0; i < 4; i++) {
            int radius = 12 + (int) (progress * 48) + i * 8;
            int alpha = Math.max(0, (int) ((1 - progress) * 150) - i * 30);
            g.setColor(new Color(133, 238, 226, alpha));
            g.drawOval(cx - radius, cy - radius / 3, radius * 2, Math.max(6, radius * 2 / 3));
        }
        for (int i = 0; i < 18; i++) {
            double angle = i * Math.PI * 2 / 18 + time * 0.35;
            double distance = 8 + progress * 38 + 6 * Math.sin(i * 2.4);
            int px = cx + (int) (Math.cos(angle) * distance);
            int py = cy - (int) (Math.abs(Math.sin(angle)) * burst * 26) + (int) (Math.sin(angle) * distance * 0.3);
            int alpha = (int) (235 * burst);
            g.setColor(i % 3 == 0 ? new Color(203, 255, 242, alpha) : new Color(78, 203, 198, alpha));
            int size = i % 4 == 0 ? 4 : 3;
            g.fillOval(px, py, size, size + 1);
        }
        if (progress < 0.35) {
            int crown = (int) (burst * 30);
            g.setColor(new Color(148, 246, 231, (int) (180 * burst)));
            g.fillOval(cx - 15 - crown, cy - crown / 2, 10, crown + 3);
            g.fillOval(cx + crown + 5, cy - crown / 2, 10, crown + 3);
        }
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
