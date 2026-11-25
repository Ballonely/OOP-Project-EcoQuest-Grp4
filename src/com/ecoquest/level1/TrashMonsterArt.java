package com.ecoquest.level1;

import java.awt.image.BufferedImage;
import java.util.EnumMap;

/**
 * Procedural pixel art for the Level 1 Trash Monster boss: a hulking, overflowing wheelie bin
 * with a toothy mouth, glowing eyes, garbage-bag arms, worn-out sneakers and a lid that flaps
 * like a hat. Every animation comes from a continuous pose function sampled into many frames,
 * so loops have plenty of in-betweens and stay smooth. Frames are built once and cached.
 * The art faces right; the boss mirrors it when it faces left.
 */
final class TrashMonsterArt {
    static final int W = 56, H = 48;
    static final int NORMAL = 0, WHITE = 1, RED = 2;
    private static final int CX = 28, GROUND = 45;

    enum Anim {
        IDLE(12, 8, true), WALK(12, 14, true), THROW(12, 20, false),
        ROAR(8, 12, true), DEFEAT(16, 12, false);

        final int count;
        final double fps;
        final boolean loop;

        Anim(int count, double fps, boolean loop) {
            this.count = count;
            this.fps = fps;
            this.loop = loop;
        }

        int index(double time) {
            int i = (int) Math.floor(time * fps);
            return loop ? Math.floorMod(i, count) : Math.max(0, Math.min(count - 1, i));
        }

        double duration() { return count / fps; }
    }

    // ---- palette ----
    private static final int OUT = 0x14210F;
    private static final int CAN_HI = 0x93CC7E, CAN_LT = 0x6DA95E, CAN = 0x4E8747, CAN_SH = 0x356331, CAN_DK = 0x234421;
    private static final int RIM = 0xA6D894;
    private static final int LID_HI = 0x80BC70, LID = 0x427A3B, LID_SH = 0x2B5329;
    private static final int BAG = 0x252A30, BAG_LT = 0x3E4750, BAG_HI = 0x7D8B98, BAG_OUT = 0x0B0D0F, BAG_BACK = 0x1A1E22;
    private static final int HELD_BAG = 0x2F4D32, HELD_BAG_HI = 0x5E8A5F, TIE = 0xE6C64A;
    private static final int MOUTH = 0x1C0E0B, MOUTH_DEEP = 0x0E0605;
    private static final int TOOTH = 0xEFE8CA, TOOTH_SH = 0xB8AD88, TONGUE = 0x9E3452, TONGUE_HI = 0xD86E8B, TONGUE_DK = 0x5C1730;
    private static final int EYE = 0xFFDD3C, EYE_LO = 0xD9A52A, EYE_HOT = 0xFFF8C0, PUPIL = 0x2A1405;
    private static final int EYE_RED = 0xFF3D2E, EYE_RED_LO = 0xC21E14, EYE_RED_HOT = 0xFFC2B5;
    private static final int SLIME = 0x9DD649, SLIME_DK = 0x5E9A26;
    private static final int RUST = 0x8A5A2B, RUST_LT = 0xB97C3E;
    private static final int SHOE = 0xDCD5C0, SHOE_SH = 0xA69E86, SOLE = 0x5A3A22, LACE = 0x6E675A, SOCK = 0xC0453A;
    private static final int BONE = 0xEAE6D8, BONE_SH = 0xB9B3A0, FISH_HEAD = 0xA7B2B5;
    private static final int PEEL = 0xF0D24A, PEEL_SH = 0xC7A82C, PEEL_TIP = 0x5A3A1A;

    private static EnumMap<Anim, BufferedImage[][]> cache;

    private TrashMonsterArt() {}

    /** The frame of {@code anim} at {@code time} seconds; variant is NORMAL, WHITE (hit) or RED (rage). */
    static BufferedImage frame(Anim anim, double time, int variant) {
        load();
        return cache.get(anim)[variant][anim.index(time)];
    }

    private static synchronized void load() {
        if (cache != null) return;
        EnumMap<Anim, BufferedImage[][]> built = new EnumMap<>(Anim.class);
        for (Anim a : Anim.values()) {
            BufferedImage[][] v = new BufferedImage[3][a.count];
            for (int i = 0; i < a.count; i++) {
                double t = a.loop ? i / (double) a.count : i / (double) (a.count - 1);
                BufferedImage img = render(pose(a, t, i));
                v[NORMAL][i] = img;
                v[WHITE][i] = tint(img, 255, 255, 255, 0.85);
                v[RED][i] = tint(img, 255, 40, 30, 0.4);
            }
            built.put(a, v);
        }
        cache = built;
    }

    // ------------------------------------------------------------ poses
    private static final class Pose {
        double top = 14, bottom = 37, topHalf = 14, botHalf = 11, lean;
        double lid = 0.1, lidTilt, lidRise;
        double mouth = 0.3, tongue;
        double armF = 0.35, armB = -0.35, bendF = 0.35, bendB = -0.35;
        double legLiftL, legLiftR, legShiftL, legShiftR;
        int eyes;           // 0 open, 1 closed, 2 furious red, 3 dizzy
        double glow;
        boolean bag, legs = true, flies = true, stink = true;
        double phase, drip = -1, spill = -1, sprout = -1;
        int frame;
        int T, B;           // rounded body top / bottom rows
    }

    private static Pose pose(Anim a, double t, int frame) {
        Pose p = new Pose();
        p.phase = t;
        p.frame = frame;
        double s = Math.sin(t * 2 * Math.PI), c = Math.cos(t * 2 * Math.PI);
        switch (a) {
            case IDLE: {
                double breathe = (s + 1) / 2;
                p.top -= breathe * 1.4;
                p.topHalf += (1 - breathe) * 0.5;
                p.lid = 0.05 + breathe * 0.25;
                p.mouth = 0.2 + breathe * 0.2;
                p.armF = 0.35 + s * 0.1;
                p.armB = -0.35 + s * 0.1;
                p.eyes = t >= 0.74 && t < 0.84 ? 1 : 0;
                p.drip = t;
                break;
            }
            case WALK: {
                double bob = Math.abs(s);
                p.top -= bob * 1.6;
                p.bottom -= bob * 1.6;
                p.legLiftL = Math.max(0, s) * 3;
                p.legLiftR = Math.max(0, -s) * 3;
                p.legShiftL = c * 1.5;
                p.legShiftR = -c * 1.5;
                p.armF = 0.35 + s * 0.5;
                p.armB = -0.35 - s * 0.5;
                p.lean = 1 + s * 0.6;
                p.lid = 0.1 + Math.abs(Math.sin(t * 4 * Math.PI)) * 0.2;   // lid clatters every step
                p.mouth = 0.35;
                p.drip = t;
                break;
            }
            case THROW: {
                p.flies = false;
                if (t < 0.42) {
                    double k = ease(t / 0.42);
                    p.armF = lerp(0.35, -2.5, k);
                    p.lean = -1.5 * k;
                    p.lid = lerp(0.15, 0.5, k);
                    p.top -= k;
                    p.bag = true;
                } else if (t < 0.58) {
                    double k = ease((t - 0.42) / 0.16);
                    p.armF = lerp(-2.5, 1.55, k);
                    p.lean = lerp(-1.5, 2.5, k);
                    p.lid = lerp(0.5, 1.0, k);
                    p.bag = k < 0.45;
                } else {
                    double k = ease((t - 0.58) / 0.42);
                    p.armF = lerp(1.55, 0.35, k);
                    p.lean = lerp(2.5, 0, k);
                    p.lid = lerp(1.0, 0.15, k);
                }
                p.bendF = 0.45;
                p.armB = -0.5 - 0.3 * Math.sin(t * Math.PI);
                p.mouth = 0.8;
                break;
            }
            case ROAR: {
                double shake = Math.sin(t * 4 * Math.PI);
                p.top -= 1.5;
                p.lean = shake * 0.6;
                p.armF = 2.55 + shake * 0.12;
                p.armB = -2.55 + shake * 0.12;
                p.bendF = 0.5;
                p.bendB = -0.5;
                p.lid = 0.85 + 0.15 * shake;
                p.mouth = 1;
                p.eyes = 2;
                p.glow = 0.8;
                p.flies = false;
                break;
            }
            case DEFEAT:
                defeatPose(p, t);
                break;
        }
        return p;
    }

    /** Shudders, the lid blasts off, the bin crumples and spills, and a sapling sprouts. */
    private static void defeatPose(Pose p, double t) {
        p.flies = false;
        p.stink = false;
        if (t < 0.25) {
            p.lean = Math.sin(t * 120) * 1.5;
            p.mouth = 1;
            p.eyes = t < 0.15 ? 2 : 3;
            p.glow = 1;
            p.armF = 0.6 + Math.sin(t * 90) * 0.9;
            p.armB = -0.6 - Math.sin(t * 90) * 0.9;
            p.lid = 0.6;
        } else if (t < 0.65) {
            double k = ease((t - 0.25) / 0.4);
            p.top = lerp(14, 31, k);
            p.bottom = lerp(37, 45, k);
            p.topHalf = 14 + k * 3;
            p.botHalf = 11 + k * 5;
            p.lid = 0.6;
            p.lidRise = k * 46;
            p.lidTilt = k * 1.4;
            p.spill = k;
            p.eyes = 3;
            p.mouth = 0.5 * (1 - k) + 0.2;
            p.tongue = k * 0.8;
            p.armF = lerp(0.6, 1.3, k);
            p.armB = lerp(-0.6, -1.3, k);
            p.legs = k < 0.5;
        } else {
            double k = (t - 0.65) / 0.35;
            p.top = 31 + k;
            p.bottom = 45;
            p.topHalf = 17;
            p.botHalf = 16;
            p.lid = 0.6;
            p.lidRise = 999;
            p.spill = 1;
            p.eyes = 1;
            p.mouth = 0.15;
            p.tongue = 0.8;
            p.armF = 1.3;
            p.armB = -1.3;
            p.legs = false;
            p.sprout = Math.max(0, (t - 0.72) / 0.28);
        }
    }

    // ------------------------------------------------------------ rendering
    private static BufferedImage render(Pose p) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        p.T = (int) Math.round(p.top);
        p.B = (int) Math.round(p.bottom);
        if (p.legs) {
            leg(img, p, true);
            leg(img, p, false);
        }
        int shoulderY = p.T + 8;
        arm(img, left(p, shoulderY) + 2, shoulderY, p.armB, p.bendB, true, false);
        body(img, p);
        double lift = p.lid * 6 + p.lidRise;
        if (lift >= 1) opening(img, p);
        overflow(img, p, lift);
        if (p.lidRise < 60) lid(img, p, lift);
        banana(img, p);
        eyes(img, p);
        mouth(img, p);
        arm(img, right(p, shoulderY) - 2, shoulderY, p.armF, p.bendF, false, p.bag);
        if (p.spill >= 0) spill(img, p);
        if (p.sprout > 0) sprout(img, p);
        if (p.stink) stink(img, p, lift);
        if (p.flies) flies(img, p);
        return img;
    }

    private static double k(Pose p, int y) { return (y - p.T) / (double) Math.max(1, p.B - p.T); }
    private static double cxAt(Pose p, int y) { return CX + p.lean * (1 - k(p, y)); }
    private static double halfAt(Pose p, int y) { return p.topHalf + (p.botHalf - p.topHalf) * k(p, y); }
    private static int left(Pose p, int y) { return (int) Math.round(cxAt(p, y) - halfAt(p, y)); }
    private static int right(Pose p, int y) { return (int) Math.round(cxAt(p, y) + halfAt(p, y)); }

    private static int shade(double u) {
        return u < 0.14 ? CAN_HI : u < 0.3 ? CAN_LT : u < 0.64 ? CAN : u < 0.86 ? CAN_SH : CAN_DK;
    }

    private static int darker(int c) {
        if (c == CAN_HI) return CAN_LT;
        if (c == CAN_LT) return CAN;
        if (c == CAN) return CAN_SH;
        return CAN_DK;
    }

    private static int lighter(int c) {
        if (c == CAN_DK) return CAN_SH;
        if (c == CAN_SH) return CAN;
        if (c == CAN) return CAN_LT;
        return CAN_HI;
    }

    /** The bin: shaded, ribbed, with a rim lip, a lower band, a dent, rust and a warning sticker. */
    private static void body(BufferedImage img, Pose p) {
        int T = p.T, B = p.B;
        for (int y = T; y <= B; y++) {
            int l = left(p, y), r = right(p, y), row = y - T;
            int x0 = row <= 2 ? l - 1 : l, x1 = row <= 2 ? r + 1 : r;
            for (int x = x0; x <= x1; x++) {
                double u = (x - l) / (double) Math.max(1, r - l);
                double du = u + (((x + y) & 1) == 0 ? 0.03 : -0.03);   // dithered shading bands
                int c;
                if (x == x0 || x == x1 || y == B || row == 0) c = OUT;
                else if (row == 1) c = u < 0.7 ? RIM : CAN_LT;
                else if (row == 2) c = u < 0.75 ? CAN_LT : CAN;
                else if (row == 3) c = CAN_DK;
                else if (y == B - 3) c = CAN_DK;
                else if (y > B - 3) c = darker(shade(du));
                else {
                    c = shade(du);
                    for (double rib : new double[] { 0.38, 0.6, 0.8 }) {
                        int rx = l + (int) Math.round(rib * (r - l));
                        if (x == rx) c = darker(c);
                        else if (x == rx - 1) c = lighter(c);
                    }
                }
                px(img, x, y, c);
            }
        }
        int span = right(p, T + 4) - left(p, T + 4);
        // rivets under the rim
        rivet(img, left(p, T + 4) + (int) Math.round(span * 0.12), T + 4);
        rivet(img, left(p, T + 4) + (int) Math.round(span * 0.9), T + 4);
        if (B - T < 16) return;   // crumpled: no room for the small details
        // dent
        int dy = T + 16, dx = left(p, dy) + (int) Math.round((right(p, dy) - left(p, dy)) * 0.2);
        if (dy < B - 4) {
            px(img, dx, dy, CAN_SH);
            px(img, dx + 1, dy, CAN_DK);
            px(img, dx + 2, dy, CAN_SH);
            px(img, dx + 1, dy + 1, CAN_HI);
            px(img, dx + 2, dy + 1, CAN_HI);
        }
        // rust
        int ry = B - 6, rx = left(p, ry) + (int) Math.round((right(p, ry) - left(p, ry)) * 0.84);
        px(img, rx, ry, RUST);
        px(img, rx + 1, ry, RUST);
        px(img, rx, ry + 1, RUST);
        px(img, rx - 1, ry + 1, RUST_LT);
        px(img, rx + 1, ry - 1, RUST_LT);
        int r2y = T + 5, r2x = left(p, r2y) + (int) Math.round((right(p, r2y) - left(p, r2y)) * 0.28);
        px(img, r2x, r2y, RUST);
        px(img, r2x + 1, r2y, RUST_LT);
        // peeling hazard sticker
        int sy = B - 8, sx = left(p, sy) + (int) Math.round((right(p, sy) - left(p, sy)) * 0.58);
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 4; j++)
                px(img, sx + i, sy + j, (i + j) % 3 == 0 ? 0x2B2B2B : 0xE6C13D);
        px(img, sx + 4, sy, 0xF6EBB5);
        px(img, sx + 4, sy + 1, 0xC9A630);
    }

    private static void rivet(BufferedImage img, int x, int y) {
        px(img, x, y, CAN_HI);
        px(img, x, y + 1, CAN_DK);
    }

    /** Dark mouth of the bin, visible when the lid is up. */
    private static void opening(BufferedImage img, Pose p) {
        int T = p.T, l = left(p, T), r = right(p, T);
        for (int x = l + 1; x <= r - 1; x++) px(img, x, T - 1, x == l + 1 || x == r - 1 ? OUT : MOUTH_DEEP);
        for (int x = l + 2; x <= r - 2; x++) px(img, x, T - 2, OUT);
    }

    /** Trash poking out of the bin; rises with the lid. Only drawn above the front rim. */
    private static void overflow(BufferedImage img, Pose p, double lift) {
        int T = p.T, l = left(p, T), r = right(p, T);
        int cx = (int) Math.round(cxAt(p, T));
        int rise = (int) Math.round(Math.min(1, p.lid) * 5);
        // fish skeleton sticking out to the right
        int fy = T - 1 - rise / 2;
        for (int x = r - 6; x <= r + 2; x++) {
            clipPx(img, x, fy, BONE, T);
            if (((x - r) & 1) == 0) {
                clipPx(img, x, fy - 1, BONE_SH, T);
                clipPx(img, x, fy + 1, BONE_SH, T);
            }
        }
        clipPx(img, r + 3, fy - 1, FISH_HEAD, T);
        clipPx(img, r + 3, fy, FISH_HEAD, T);
        clipPx(img, r + 3, fy + 1, FISH_HEAD, T);
        clipPx(img, r + 4, fy, FISH_HEAD, T);
        clipPx(img, r + 4, fy - 1, 0x1A1A1A, T);
        clipPx(img, r + 5, fy, BONE_SH, T);
        if (lift < 1) return;
        // soda can
        int canY = T - 2 - rise;
        for (int y = canY; y < T; y++)
            for (int x = cx - 8; x <= cx - 6; x++)
                clipPx(img, x, y, y == canY ? 0xD4D4D4 : x == cx - 8 ? 0xE7675C : 0xC8352B, T);
        // plastic bottle
        int by = T - 3 - rise;
        clipPx(img, cx + 4, by - 1, 0x2F6FB0, T);
        for (int y = by; y < T; y++) {
            clipPx(img, cx + 4, y, 0xD6F0FF, T);
            if (y > by + 1) clipPx(img, cx + 3, y, 0x7CC3E8, T);
            if (y > by + 1) clipPx(img, cx + 5, y, 0x5AA7E0, T);
        }
        // crumpled newspaper
        int ny = T - 1 - rise / 2;
        for (int x = cx - 4; x <= cx; x++) {
            clipPx(img, x, ny, 0xDADACF, T);
            clipPx(img, x, ny + 1, (x & 1) == 0 ? 0x8E8E86 : 0xC9C9BE, T);
        }
    }

    /** Banana peel draped over the left rim, outside the bin. */
    private static void banana(BufferedImage img, Pose p) {
        if (p.B - p.T < 16) return;
        int T = p.T, l = left(p, T);
        px(img, l, T - 1, PEEL);
        px(img, l + 1, T - 1, PEEL_SH);
        px(img, l - 1, T, PEEL);
        px(img, l - 2, T + 1, PEEL);
        px(img, l - 1, T + 1, PEEL_SH);
        px(img, l - 2, T + 2, PEEL);
        px(img, l - 3, T + 3, PEEL_SH);
        px(img, l - 3, T + 4, PEEL_TIP);
        px(img, l, T + 1, PEEL);
        px(img, l, T + 2, PEEL_SH);
    }

    /** The lid: thick shaded slab with a handle, a scratch and a slime drip. */
    private static void lid(BufferedImage img, Pose p, double lift) {
        int T = p.T;
        double lcx = cxAt(p, T) - Math.min(1, p.lid) * 1.5 + p.lidTilt * 2;
        int half = (int) Math.round(halfAt(p, T)) + 2;
        int l = (int) Math.round(lcx - half), r = (int) Math.round(lcx + half);
        double y0 = T - 4 - lift;
        double slope = -p.lidTilt * 0.35;
        for (int x = l; x <= r; x++) {
            int yy = (int) Math.round(y0 + slope * (x - lcx));
            double u = (x - l) / (double) (r - l);
            if (x == l || x == r) {
                px(img, x, yy + 1, OUT);
                px(img, x, yy + 2, OUT);
                px(img, x, yy + 3, OUT);
                continue;
            }
            px(img, x, yy, OUT);
            px(img, x, yy + 1, u < 0.72 ? LID_HI : LID);
            px(img, x, yy + 2, u < 0.18 ? LID_HI : u < 0.7 ? LID : LID_SH);
            px(img, x, yy + 3, u < 0.6 ? LID_SH : OUT);
            px(img, x, yy + 4, OUT);
        }
        int hx = (int) Math.round(lcx);
        int hy = (int) Math.round(y0 + slope * (hx - lcx));
        for (int x = hx - 3; x <= hx + 3; x++) px(img, x, hy - 2, x == hx - 3 || x == hx + 3 ? OUT : LID_SH);
        px(img, hx - 3, hy - 1, OUT);
        px(img, hx + 3, hy - 1, OUT);
        px(img, hx - 2, hy - 2, LID_HI);
        // scratch
        int sx = l + (int) Math.round((r - l) * 0.3);
        int sy = (int) Math.round(y0 + slope * (sx - lcx)) + 2;
        px(img, sx, sy, LID_SH);
        px(img, sx + 1, sy, LID_SH);
        // slime drip off the front edge
        int dx = l + (int) Math.round((r - l) * 0.78);
        int dy = (int) Math.round(y0 + slope * (dx - lcx)) + 5;
        int len = 1 + (p.frame % 4 == 0 ? 0 : 1);
        for (int j = 0; j < len; j++) px(img, dx, dy + j, SLIME);
        px(img, dx, dy + len, SLIME_DK);
    }

    private static void eyes(BufferedImage img, Pose p) {
        int ey = p.T + 6;
        double cx = cxAt(p, ey), half = halfAt(p, ey);
        eye(img, p, (int) Math.round(cx - half * 0.46), ey, 4, true);
        eye(img, p, (int) Math.round(cx + half * 0.22), ey, 3, false);
    }

    private static void eye(BufferedImage img, Pose p, int ex, int ey, int w, boolean leftEye) {
        if (p.eyes == 2 && p.glow > 0) {   // red glow halo
            for (int y = ey - 3; y <= ey + 3; y++)
                for (int x = ex - 3; x <= ex + w + 2; x++)
                    pxa(img, x, y, EYE_RED, (int) (55 * p.glow));
        }
        for (int y = ey - 1; y <= ey + 2; y++)
            for (int x = ex - 1; x <= ex + w; x++) {
                boolean corner = (x == ex - 1 || x == ex + w) && (y == ey - 1 || y == ey + 2);
                if (!corner) px(img, x, y, MOUTH);
            }
        switch (p.eyes) {
            case 1:
                for (int x = ex; x < ex + w; x++) px(img, x, ey + 1, 0x4A3A18);
                break;
            case 3:   // dizzy X
                px(img, ex, ey - 1 + 1, EYE);
                px(img, ex + 2, ey, EYE);
                px(img, ex + 1, ey + 1, EYE);
                px(img, ex, ey + 2, EYE_LO);
                px(img, ex + 2, ey + 2, EYE_LO);
                break;
            default: {
                boolean red = p.eyes == 2;
                for (int x = ex; x < ex + w; x++) {
                    px(img, x, ey, red ? EYE_RED : EYE);
                    px(img, x, ey + 1, red ? EYE_RED_LO : EYE_LO);
                }
                px(img, ex, ey, red ? EYE_RED_HOT : EYE_HOT);
                int pupil = ex + w / 2 + (leftEye ? 0 : 0);
                px(img, pupil, ey, PUPIL);
                px(img, pupil, ey + 1, PUPIL);
            }
        }
        if (p.eyes == 3 || p.eyes == 1) return;
        // angry brow, low on the inner side
        if (leftEye) {
            px(img, ex - 2, ey - 3, OUT);
            px(img, ex - 1, ey - 3, OUT);
            px(img, ex, ey - 2, OUT);
            px(img, ex + 1, ey - 2, OUT);
            px(img, ex + 2, ey - 2, OUT);
            px(img, ex + 3, ey - 1, OUT);
            px(img, ex + 4, ey - 1, OUT);
        } else {
            px(img, ex - 1, ey - 1, OUT);
            px(img, ex, ey - 2, OUT);
            px(img, ex + 1, ey - 2, OUT);
            px(img, ex + 2, ey - 3, OUT);
            px(img, ex + 3, ey - 3, OUT);
        }
    }

    private static void mouth(BufferedImage img, Pose p) {
        int my = p.T + 11;
        double mcx = cxAt(p, my), mh = halfAt(p, my);
        int ml = (int) Math.round(mcx - mh * 0.66), mr = (int) Math.round(mcx + mh * 0.62);
        int h = 1 + (int) Math.round(p.mouth * 7);
        h = Math.max(1, Math.min(h, p.B - my - 2));
        for (int x = ml; x <= mr; x++) {
            px(img, x, my - 1, OUT);
            px(img, x, my + h, OUT);
        }
        for (int y = my; y < my + h; y++) {
            px(img, ml, y, OUT);
            px(img, mr, y, OUT);
            for (int x = ml + 1; x < mr; x++) {
                boolean deep = h >= 4 && y > my && y < my + h - 1 && x > ml + 2 && x < mr - 2;
                px(img, x, y, deep ? MOUTH_DEEP : MOUTH);
            }
        }
        if (my + h + 1 < p.B - 3)   // lower lip catches the light
            for (int x = ml + 1; x < mr; x++) px(img, x, my + h + 1, CAN_LT);
        int tx = (int) Math.round(mcx) + 1;
        if (h >= 4) {
            for (int y = my + h - 2; y < my + h; y++)
                for (int x = tx - 3; x <= tx + 2; x++) px(img, x, y, TONGUE);
            px(img, tx - 1, my + h - 2, TONGUE_HI);
            px(img, tx, my + h - 2, TONGUE_HI);
        }
        // top teeth (one broken), longer fangs when wide open
        for (int x = ml + 1, i = 0; x < mr - 1; x += 3, i++) {
            if (i % 4 == 2) continue;
            px(img, x, my, TOOTH);
            px(img, x + 1, my, TOOTH_SH);
            if (h >= 3) px(img, x, my + 1, TOOTH);
            if (h >= 6 && i % 2 == 0) px(img, x, my + 2, TOOTH_SH);
        }
        if (h >= 4) {
            for (int x = ml + 2, i = 0; x < mr - 1; x += 3, i++) {
                if (i % 3 == 1) continue;
                px(img, x, my + h - 1, TOOTH);
                if (h >= 5) px(img, x, my + h - 2, TOOTH_SH);
            }
        }
        if (p.tongue > 0) {   // tongue lolling out
            int len = (int) Math.round(p.tongue * 5);
            for (int j = 0; j <= len; j++) {
                int y = my + h - 1 + j;
                boolean tip = j == len;
                if (!tip) {
                    px(img, tx - 1, y, TONGUE_DK);
                    px(img, tx + 3, y, TONGUE_DK);
                }
                px(img, tx, y, tip ? TONGUE_DK : TONGUE_HI);
                px(img, tx + 1, y, TONGUE);
                px(img, tx + 2, y, tip ? TONGUE_DK : TONGUE);
            }
        }
        if (p.drip >= 0 && h >= 2) {   // slime drool from the corner
            int dx = ml + 2, len = (int) (p.drip * 7);
            for (int j = 1; j <= len; j++) px(img, dx, my + h + j, j == len ? SLIME_DK : SLIME);
            if (len >= 2) px(img, dx + 1, my + h + len, SLIME_DK);
        }
    }

    /** Garbage-bag arm: shoulder, elbow, tied-off fist; optionally holding a full bag. */
    private static void arm(BufferedImage img, double sx, double sy, double a, double bend, boolean back, boolean bag) {
        double ex = sx + Math.sin(a) * 6, ey = sy + Math.cos(a) * 6;
        double a2 = a + bend;
        double fx = ex + Math.sin(a2) * 5, fy = ey + Math.cos(a2) * 5;
        int base = back ? BAG_BACK : BAG, lt = back ? BAG : BAG_LT;
        capsule(img, sx, sy, ex, ey, 3.2, BAG_OUT);
        capsule(img, ex, ey, fx, fy, 2.9, BAG_OUT);
        disc(img, fx, fy, 3.9, BAG_OUT);
        capsule(img, sx, sy, ex, ey, 2.2, base);
        capsule(img, ex, ey, fx, fy, 1.9, base);
        disc(img, fx, fy, 2.9, base);
        capsule(img, sx - 0.9, sy - 0.6, ex - 0.9, ey - 0.6, 0.6, lt);
        capsule(img, ex - 0.8, ey - 0.6, fx - 0.8, fy - 0.6, 0.5, lt);
        px(img, (int) Math.round(fx - 1), (int) Math.round(fy - 1), back ? BAG_LT : BAG_HI);
        px(img, (int) Math.round(ex), (int) Math.round(ey), BAG_OUT);   // elbow crease
        px(img, (int) Math.round(fx + Math.sin(a2) * 2.5), (int) Math.round(fy + Math.cos(a2) * 2.5), TIE);
        if (!bag) return;
        double bx = fx + Math.sin(a2) * 4.5, by = fy + Math.cos(a2) * 4.5;
        disc(img, bx, by, 5.0, BAG_OUT);
        disc(img, bx, by, 4.0, HELD_BAG);
        disc(img, bx - 1.5, by - 1.5, 1.4, HELD_BAG_HI);
        px(img, (int) Math.round(bx), (int) Math.round(by - 4), TIE);
        px(img, (int) Math.round(bx + 1), (int) Math.round(by - 4), TIE);
        px(img, (int) Math.round(bx + 2), (int) Math.round(by + 1), BAG_OUT);
    }

    /** Stubby bag leg in a worn-out sneaker (one has a hole at the toe). */
    private static void leg(BufferedImage img, Pose p, boolean leftLeg) {
        double hx = cxAt(p, p.B) + (leftLeg ? -6 : 5) + (leftLeg ? p.legShiftL : p.legShiftR);
        int foot = GROUND - (int) Math.round(leftLeg ? p.legLiftL : p.legLiftR);
        int hy = p.B - 2;
        capsule(img, hx, hy, hx, foot - 3, 3.0, BAG_OUT);
        capsule(img, hx, hy, hx, foot - 3, 2.0, leftLeg ? BAG_BACK : BAG);
        capsule(img, hx - 1, hy, hx - 1, foot - 4, 0.4, BAG_LT);
        int sx = (int) Math.round(hx) - 3;
        for (int x = sx + 1; x <= sx + 5; x++) px(img, x, foot - 3, OUT);
        px(img, sx, foot - 2, OUT);
        for (int x = sx + 1; x <= sx + 5; x++) px(img, x, foot - 2, SHOE);
        px(img, sx + 6, foot - 2, OUT);
        px(img, sx + 2, foot - 2, LACE);
        px(img, sx + 4, foot - 2, LACE);
        px(img, sx, foot - 1, OUT);
        for (int x = sx + 1; x <= sx + 6; x++) px(img, x, foot - 1, x == sx + 6 ? SHOE_SH : SHOE);
        px(img, sx + 7, foot - 1, OUT);
        if (leftLeg) px(img, sx + 6, foot - 1, SOCK);
        px(img, sx, foot, OUT);
        for (int x = sx + 1; x <= sx + 6; x++) px(img, x, foot, SOLE);
        px(img, sx + 7, foot, OUT);
        for (int x = sx + 1; x <= sx + 6; x++) px(img, x, foot + 1, OUT);
    }

    private static void spill(BufferedImage img, Pose p) {
        int[][] items = {   // direction, arc height, colour, shade
            { -10, 14, 0xC8352B, 0xD4D4D4 }, { -6, 18, PEEL, PEEL_SH }, { 5, 16, 0x7CC3E8, 0xD6F0FF },
            { 9, 12, BONE, BONE_SH }, { -2, 20, 0xDADACF, 0x8E8E86 }, { 3, 10, 0x8B5A2B, 0xB33A32 },
            { -13, 9, HELD_BAG, HELD_BAG_HI }, { 12, 8, 0xE9953A, 0xF6C27A }
        };
        double k = Math.min(1, p.spill);
        for (int[] it : items) {
            int x = (int) Math.round(CX + it[0] * k * 2.1);
            int y = (int) Math.round(lerp(18, GROUND - 1, k) - Math.sin(k * Math.PI) * it[1]);
            x = Math.max(1, Math.min(W - 3, x));
            px(img, x, y, it[2]);
            px(img, x + 1, y, it[2]);
            px(img, x, y + 1, it[3]);
            px(img, x + 1, y + 1, it[2]);
        }
    }

    /** A little sapling grows out of the beaten bin. */
    private static void sprout(BufferedImage img, Pose p) {
        int bx = (int) Math.round(cxAt(p, p.T)), base = p.T - 1;
        int h = (int) Math.round(Math.min(1, p.sprout) * 6);
        for (int j = 0; j < h; j++) px(img, bx, base - j, 0x3E8E36);
        if (p.sprout < 0.45) return;
        int ty = base - h + 1;
        px(img, bx - 1, ty, 0x6BCB52);
        px(img, bx - 2, ty - 1, 0x6BCB52);
        px(img, bx - 1, ty - 1, 0x9BE07A);
        px(img, bx + 1, ty + 1, 0x6BCB52);
        px(img, bx + 2, ty, 0x6BCB52);
        px(img, bx + 1, ty, 0x9BE07A);
        if (p.sprout > 0.8) pxa(img, bx + 3, ty - 2, 0xFFFFFF, 200);
    }

    private static void stink(BufferedImage img, Pose p, double lift) {
        int topY = (int) Math.round(p.T - 6 - lift);
        int cx = (int) Math.round(cxAt(p, p.T));
        for (int line = 0; line < 2; line++) {
            int base = cx + (line == 0 ? -8 : 7);
            for (int j = 0; j < 7; j++) {
                int x = base + (int) Math.round(Math.sin(j * 0.9 + p.phase * 2 * Math.PI + line * 2) * 1.2);
                pxa(img, x, topY - j, 0xB6DB6E, (int) (150 * (1 - j / 7.0)));
            }
        }
    }

    private static void flies(BufferedImage img, Pose p) {
        for (int i = 0; i < 3; i++) {
            double ang = p.phase * 2 * Math.PI * (i % 2 == 0 ? 1 : -1) + i * 2.1;
            int fx = (int) Math.round(CX + Math.cos(ang) * (15 + i * 2));
            int fy = (int) Math.round(p.T - 7 + Math.sin(ang * 2) * 3 - i);
            px(img, fx, fy, 0x101010);
            if (((p.frame + i) & 1) == 0) pxa(img, fx, fy - 1, 0xE8F0F8, 210);
            else {
                pxa(img, fx - 1, fy - 1, 0xE8F0F8, 210);
                pxa(img, fx + 1, fy - 1, 0xE8F0F8, 210);
            }
        }
    }

    // ------------------------------------------------------------ pixel helpers
    private static void px(BufferedImage img, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < W && y < H) img.setRGB(x, y, 0xFF000000 | rgb);
    }

    private static void px(BufferedImage img, double x, double y, int rgb) {
        px(img, (int) Math.round(x), (int) Math.round(y), rgb);
    }

    /** Pixel that stays hidden behind the front rim (row {@code rimY} and below). */
    private static void clipPx(BufferedImage img, int x, int y, int rgb, int rimY) {
        if (y < rimY) px(img, x, y, rgb);
    }

    private static void pxa(BufferedImage img, int x, int y, int rgb, int alpha) {
        if (x < 0 || y < 0 || x >= W || y >= H || alpha <= 0) return;
        int dst = img.getRGB(x, y);
        int da = dst >>> 24;
        double a = Math.min(255, alpha) / 255.0;
        int outA = (int) Math.round(255 * (a + da / 255.0 * (1 - a)));
        if (outA == 0) return;
        int r = blend((rgb >> 16) & 0xFF, (dst >> 16) & 0xFF, a, da, outA);
        int g = blend((rgb >> 8) & 0xFF, (dst >> 8) & 0xFF, a, da, outA);
        int b = blend(rgb & 0xFF, dst & 0xFF, a, da, outA);
        img.setRGB(x, y, (outA << 24) | (r << 16) | (g << 8) | b);
    }

    private static int blend(int src, int dst, double a, int da, int outA) {
        double v = (src * a + dst * (da / 255.0) * (1 - a)) / (outA / 255.0);
        return Math.max(0, Math.min(255, (int) Math.round(v)));
    }

    private static void capsule(BufferedImage img, double x0, double y0, double x1, double y1, double r, int rgb) {
        int minX = (int) Math.floor(Math.min(x0, x1) - r - 1), maxX = (int) Math.ceil(Math.max(x0, x1) + r + 1);
        int minY = (int) Math.floor(Math.min(y0, y1) - r - 1), maxY = (int) Math.ceil(Math.max(y0, y1) + r + 1);
        double vx = x1 - x0, vy = y1 - y0, len2 = vx * vx + vy * vy;
        for (int y = minY; y <= maxY; y++)
            for (int x = minX; x <= maxX; x++) {
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - x0) * vx + (y - y0) * vy) / len2));
                double dx = x - (x0 + vx * t), dy = y - (y0 + vy * t);
                if (dx * dx + dy * dy <= r * r) px(img, x, y, rgb);
            }
    }

    private static void disc(BufferedImage img, double cx, double cy, double r, int rgb) {
        capsule(img, cx, cy, cx, cy, r, rgb);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static double ease(double v) {
        double t = Math.max(0, Math.min(1, v));
        return t * t * (3 - 2 * t);
    }

    static BufferedImage tint(BufferedImage src, int tr, int tg, int tb, double amount) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = argb >>> 24;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                r = (int) (r + (tr - r) * amount);
                g = (int) (g + (tg - g) * amount);
                b = (int) (b + (tb - b) * amount);
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        return out;
    }
}
