package com.ecoquest.level2;

import com.ecoquest.level1.PixelCanvas;

import static com.ecoquest.level1.PixelCanvas.lerp;

/**
 * Procedural pixel art for the Level 2 boss, the Litter Beast: a hulking four-legged park pest
 * built from a bulging black garbage bag matted with fallen leaves and cherry petals, a ridge of
 * plastic bottles along its spine, a knotted bag-tie tail, and a crumpled brown paper-bag head
 * with torn, glowing eye holes and a drooling torn-paper jaw.
 *
 * <p>The whole sprite is repainted every frame from a {@link Pose} of continuous values (gait
 * phase, breathing, squash and stretch, jaw, ear lag...), so walking, idling, spewing litter and
 * collapsing all blend into each other smoothly. It faces right; the beast mirrors it.
 */
final class LitterBeastArt {
    static final int W = 66, H = 54;
    /** Anchor: the point between the feet on the ground. */
    static final int AX = 31, GROUND = 47;

    // ---- palette ----
    static final int OUT = 0x0B120C;
    private static final int[] BAG = {OUT, 0x16241A, 0x203424, 0x2C4731, 0x3D6045, 0x588161};
    private static final int[] BAG_FAR = {OUT, 0x101A13, 0x172619, 0x203424, 0x2C4731, 0x3D6045};
    private static final int[] LEG = {OUT, 0x223627, 0x2F4B35, 0x44684C, 0x5E8868};
    private static final int[] LEG_FAR = {OUT, 0x16241A, 0x1E3123, 0x2A432F, 0x3A5A41};
    private static final int SHINE = 0xA6CCAB, SHINE_DIM = 0x6E9877;
    private static final int[] PAPER = {0x3A2410, 0x7A5428, 0x9A6C37, 0xB8894B, 0xD3A866, 0xEBCB8F};
    private static final int[] PAPER_DK = {0x3A2410, 0x5E3F1C, 0x7A5428, 0x956A35, 0xAE8248, 0xC79C5E};
    private static final int HOLE = 0x140906, MOUTH = 0x2A0A0B, TONGUE = 0xA23A55, TONGUE_HI = 0xD8708C;
    private static final int TOOTH = 0xF3EBD2, TOOTH_SH = 0xC2B796;
    private static final int EYE = 0xD8EF3C, EYE_HOT = 0xFFFFD2, EYE_RAGE = 0xFF4A2E, EYE_RAGE_HOT = 0xFFD0B8, PUPIL = 0x1A0A04;
    private static final int SLIME = 0x93D046, SLIME_HI = 0xD5F59A;
    private static final int[] LEAVES = {0x8A5A2B, 0xC7742E, 0xD9B348, 0x7C8C38, 0xA8452A};
    private static final int VEIN = 0x4E3216, GRASS = 0x5FAE3E, GRASS_HI = 0x9AD86A;
    private static final int PETAL = 0xF6B3C8, PETAL_HI = 0xFFE4EE;
    private static final int BOTTLE_OUT = 0x23495F, BOTTLE = 0x76BCE2, BOTTLE_HI = 0xD2F0FF;
    private static final int[] CAPS = {0xD8402E, 0xF0F0F0, 0x45AA4E};
    private static final int CAN = 0xCF3B30, CAN_HI = 0xF27E6E, CAN_DK = 0x8E2018, CAN_TOP = 0xDADADA;
    private static final int PAPER_SCRAP = 0xDDD9CD, PAPER_LINE = 0x8A8A86, PAPER_FOLD = 0xB6B2A6;
    private static final int TIE = 0xE6C64A, TIE_DK = 0xA4862A, CLAW = 0xE8E2CC, FLOWER_C = 0xF5D33C;
    private static final int STINK = 0xA9C96A;

    /** Every value the painter needs, all continuous so poses blend. */
    static final class Pose {
        double t;              // seconds, for looping secondary motion
        double walk;           // gait phase (cycles)
        double walkAmt;        // 0 standing .. 1 full stride
        double breathe;        // -1..1
        double crouch;         // 0..1 lowered and squashed (anticipation)
        double rear;           // 0..1 chest lifted and stretched
        double headDX, headDY;
        double jaw;            // 0..1
        double shake;          // horizontal jitter, px
        double ear;            // ear flop angle offset, radians
        double look;           // -1..1 pupils
        int eyes;              // 0 open, 1 closed, 2 rage, 3 dizzy
        double glow = 0.6;
        double deflate, legsFold, bloom;
        boolean flies = true, stink = true;
    }

    private LitterBeastArt() {}

    static void paint(PixelCanvas c, Pose p) {
        c.clear();
        double t = p.t, cyc = p.walk * 2 * Math.PI, def = p.deflate;
        double bob = Math.abs(Math.sin(cyc)) * 1.4 * p.walkAmt;
        double bx = 28 + p.shake;
        double by = 32 + p.crouch * 3 - p.rear * 1.2 - bob + p.breathe * 0.35 + def * 9;
        double sq = p.breathe * 0.4 + p.crouch * 1.2 - p.rear * 0.8;
        double hRx = 10.5 + sq * 0.5 + def * 4, hRy = 9.5 - sq * 0.6 - def * 5.5;
        double cRx = 10 + sq * 0.5 + def * 4, cRy = 9 - sq * 0.6 - def * 5;
        double hx = bx - 6 - def, hy = by + 0.5;
        double cx = bx + 5 + def, cy = by - 1 - p.rear * 2.5 + def * 0.5;

        // far legs, behind the body
        leg(c, p, hx + 2, hy + hRy - 3.5, 0.5, false, false);
        leg(c, p, cx + 6, cy + cRy - 3.5, 0.0, true, false);
        tail(c, p, hx - hRx + 0.8, hy - 3.5 + def * 2);

        c.ball(hx, hy, hRx, hRy, BAG, 0);
        c.ball(cx, cy, cRx, cRy, BAG, 0);
        bagDetails(c, p, hx, hy, hRx, hRy, cx, cy, cRx, cRy);
        bottles(c, p, hx, hy, hRx, hRy, cx, cy, cRx, cRy, cyc);

        leg(c, p, hx - 2, hy + hRy - 3, 0.0, false, true);
        leg(c, p, cx + 2, cy + cRy - 2.5, 0.5, true, true);

        double headBob = Math.sin(cyc * 2 - 0.9) * 0.9 * p.walkAmt
                + Math.sin(t * 2 * Math.PI / 2.4 - 0.7) * 0.5 * (1 - p.walkAmt);
        double Hx = cx + 10.5 + p.headDX - def * 1.5;
        double Hy = cy - 3.5 + p.headDY - bob * 0.5 + headBob + def * 4;
        head(c, p, Hx, Hy);

        c.outline(OUT);

        if (p.eyes == 0 || p.eyes == 2) {
            double pulse = 0.65 + 0.35 * Math.sin(t * 4.2);
            int col = p.eyes == 2 ? EYE_RAGE : EYE;
            int a = (int) (p.glow * pulse * (p.eyes == 2 ? 120 : 75));
            c.ovalA(Hx + 3.2, Hy - 1.6, 4.2, 3.6, col, a);
            c.ovalA(Hx - 1.2, Hy - 1.9, 3.4, 3.2, col, a * 3 / 4);
        }
        if (p.bloom > 0) flowers(c, p, hx, hy - hRy, cx, cy - cRy);
        if (p.stink) stink(c, t, hx + 2, hy - hRy - 3, cx - 3, cy - cRy - 4);
        if (p.flies) flies(c, t, bx + 3, by - 16);
    }

    // ------------------------------------------------------------ body parts

    private static void leg(PixelCanvas c, Pose p, double hipX, double hipY, double phase, boolean front, boolean near) {
        double a = (p.walk + phase) * 2 * Math.PI;
        double lift = Math.max(0, Math.sin(a)) * 2.6 * p.walkAmt;
        // a lifted foot swings forward (-cos runs back to front while sin > 0), a planted one pushes back
        double fx = hipX + 1 - Math.cos(a) * 3.2 * p.walkAmt + (front ? 1 : -1) * p.crouch * 1.5;
        double fy = GROUND - 1 - lift;
        if (front) fx += p.rear * 1.5;
        fx = lerp(fx, hipX + (front ? 4 : -4), p.legsFold);
        fy = lerp(fy, hipY + 2, p.legsFold);
        int[] ramp = near ? LEG : LEG_FAR;
        double r = near ? 2.9 : 2.5;
        c.limb(hipX, hipY, fx, fy - 1, r, ramp);
        c.ball(fx + 0.8, fy, r + 0.7, 2.0, ramp, 1);
        if (near && p.legsFold < 0.6) {
            for (int i = 0; i < 3; i++) c.set(fx + r + 0.6 - i * 1.6, fy + 1.4, CLAW);
        }
    }

    private static void tail(PixelCanvas c, Pose p, double kx, double ky) {
        double sway = Math.sin(p.t * 3.1) * 0.25 + Math.sin(p.walk * 4 * Math.PI - 1.4) * 0.35 * p.walkAmt;
        for (int i = 0; i < 2; i++) {
            double ang = Math.PI + (i == 0 ? -0.75 : 0.35) + sway * (i == 0 ? 1 : 1.3);
            double len = 4.5 - i * 0.5;
            double ex = kx + Math.cos(ang) * len, ey = ky + Math.sin(ang) * len;
            c.capsule(kx, ky, ex, ey, 1.1, TIE_DK);
            c.line(kx, ky - 0.4, ex, ey - 0.4, TIE);
        }
        c.ball(kx + 0.5, ky, 2.7, 2.3, BAG, 1);
    }

    private static void bagDetails(PixelCanvas c, Pose p, double hx, double hy, double hRx, double hRy,
                                   double cx, double cy, double cRx, double cRy) {
        // folds and wrinkles in the plastic
        c.line(hx + hRx - 3, hy - 5, hx + hRx - 1, hy + 4, BAG[1]);
        foldLine(c, hx - 6, hy + 2, hx - 2, hy + 5);
        foldLine(c, hx + 1, hy - 4, hx + 3, hy - 1);
        foldLine(c, cx + 2, cy + 3, cx + 5, cy + 5.5);
        foldLine(c, cx - 4, cy + 1, cx - 2, cy + 4);
        // glossy shine
        double sh = p.breathe * 0.4;
        int[][] hs = {{-6, -4}, {-5, -5}, {-4, -6}, {-3, -6}};
        for (int[] s : hs) c.setOnPx(hx + s[0] * hRx / 10.5, hy + s[1] * hRy / 9.5 + sh, SHINE);
        c.setOnPx(hx - 6 * hRx / 10.5, hy - 2 * hRy / 9.5, SHINE_DIM);
        int[][] cs = {{-4, -6}, {-3, -7}, {-2, -7}};
        for (int[] s : cs) c.setOnPx(cx + s[0] * cRx / 10, cy + s[1] * cRy / 9 + sh, SHINE);
        c.setOnPx(cx + 1 * cRx / 10, cy - 7 * cRy / 9 + sh, SHINE_DIM);

        // a crushed soda can and a newspaper scrap stuck to the flanks
        double ax = hx - 5, ay = hy - 3 - p.deflate;
        box(c, ax - 1, ay - 1, 5, 6, OUT);
        box(c, ax, ay, 3, 4, CAN);
        box(c, ax, ay, 1, 4, CAN_HI);
        box(c, ax, ay, 3, 1, CAN_TOP);
        c.set(ax + 2, ay + 2, CAN_DK);
        double nx = cx + 1, ny = cy + 2.5;
        box(c, nx, ny, 5, 3, PAPER_SCRAP);
        c.line(nx + 1, ny + 1, nx + 3, ny + 1, PAPER_LINE);
        c.set(nx + 1, ny + 2, PAPER_LINE);
        c.set(nx + 4, ny, PAPER_FOLD);

        // fallen leaves and grass matted on the back
        double[][] leaves = {
            {0, 3.55, 0}, {0, 3.95, 1}, {0, 4.35, 2}, {0, 4.75, 3}, {0, 5.15, 4}, {0, 5.6, 1},
            {1, 3.85, 2}, {1, 4.3, 0}, {1, 4.7, 4}, {1, 5.1, 2}, {1, 5.5, 3}, {1, 5.9, 0}
        };
        for (int i = 0; i < leaves.length; i++) {
            if (p.deflate > 0.15 + (i % 5) * 0.15) continue;   // shed while it collapses
            boolean front = leaves[i][0] == 1;
            double ang = leaves[i][1];
            double bcx = front ? cx : hx, bcy = front ? cy : hy, brx = front ? cRx : hRx, bry = front ? cRy : hRy;
            double lx = bcx + Math.cos(ang) * brx * 0.9, ly = bcy + Math.sin(ang) * bry * 0.9;
            double rot = ang + Math.PI / 2 + Math.sin(p.t * 2 + i) * 0.12;
            c.polyRot(lx, ly, rot, new double[]{-2.4, 0, 2.4, 0}, new double[]{0, -1.3, 0, 1.3}, LEAVES[(int) leaves[i][2]]);
            c.set(lx, ly, VEIN);
        }
        double[] tufts = {4.1, 4.9, 5.5};
        for (int i = 0; i < tufts.length; i++) {
            double bcx = i == 2 ? cx : hx, bcy = i == 2 ? cy : hy, brx = i == 2 ? cRx : hRx, bry = i == 2 ? cRy : hRy;
            double gx = bcx + Math.cos(tufts[i]) * brx, gy = bcy + Math.sin(tufts[i]) * bry;
            double sway = Math.sin(p.t * 2.5 + i * 1.7) * 0.6;
            c.set(gx, gy - 1, GRASS);
            c.set(gx - 1 + sway, gy - 2, GRASS);
            c.set(gx + 1 + sway, gy - 2.4, GRASS_HI);
            c.set(gx + sway * 1.5, gy - 3, GRASS_HI);
        }
        // cherry petals
        double[][] petals = {{hx - 2, hy - hRy + 2}, {cx - 4, cy - cRy + 3}, {cx + 3, cy - cRy + 2}, {hx + 4, hy - hRy + 3}, {hx - 7, hy - 2}};
        for (int i = 0; i < petals.length; i++) {
            c.setOnPx(petals[i][0], petals[i][1], PETAL);
            if (i % 2 == 0) c.setOnPx(petals[i][0] + 1, petals[i][1], PETAL_HI);
        }
    }

    private static void foldLine(PixelCanvas c, double x0, double y0, double x1, double y1) {
        int n = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= n; i++) {
            double k = n == 0 ? 0 : i / (double) n;
            c.setOnPx(x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, BAG[1]);
            c.setOnPx(x0 + (x1 - x0) * k - 1, y0 + (y1 - y0) * k, BAG[4]);
        }
    }

    /** Plastic bottles stuck in the spine like spikes; they wobble a beat behind the body. */
    private static void bottles(PixelCanvas c, Pose p, double hx, double hy, double hRx, double hRy,
                                double cx, double cy, double cRx, double cRy, double cyc) {
        double[][] spots = {{0, 4.35}, {0, 4.95}, {1, 4.45}, {1, 5.05}};
        for (int i = 0; i < spots.length; i++) {
            boolean front = spots[i][0] == 1;
            double ang = spots[i][1];
            double bcx = front ? cx : hx, bcy = front ? cy : hy, brx = front ? cRx : hRx, bry = front ? cRy : hRy;
            double sx = bcx + Math.cos(ang) * brx * 0.82, sy = bcy + Math.sin(ang) * bry * 0.82;
            double wob = Math.sin(p.t * 2.2 - i * 0.9) * 0.08
                    + Math.sin(cyc * 2 - 1.1 - i * 0.5) * 0.14 * p.walkAmt
                    - p.rear * 0.25 + p.crouch * 0.15 + Math.sin(p.t * 40) * 0.05 * Math.min(1, Math.abs(p.shake));
            double dir = ang - 0.35 + wob + p.deflate * 1.3 * (i % 2 == 0 ? 1 : -1);
            double len = (i % 2 == 0 ? 7.5 : 6.2) * (1 - p.deflate * 0.4);
            double ex = sx + Math.cos(dir) * len, ey = sy + Math.sin(dir) * len;
            double nx = sx + Math.cos(dir) * len * 0.6, ny = sy + Math.sin(dir) * len * 0.6;
            c.capsule(sx, sy, nx, ny, 2.1, BOTTLE_OUT);
            c.capsule(nx, ny, ex, ey, 1.4, BOTTLE_OUT);
            c.capsule(sx, sy, nx, ny, 1.2, BOTTLE);
            c.line(nx, ny, ex - Math.cos(dir), ey - Math.sin(dir), BOTTLE);
            c.line(sx - 0.7, sy - 0.6, nx - 0.7, ny - 0.6, BOTTLE_HI);
            c.set(ex, ey, CAPS[i % CAPS.length]);
            c.set(ex - Math.cos(dir) * 0.8, ey - Math.sin(dir) * 0.8, CAPS[i % CAPS.length]);
        }
    }

    private static void head(PixelCanvas c, Pose p, double Hx, double Hy) {
        double jaw = p.jaw;
        double jawY = Hy + 5.2 + jaw * 3.4;
        // mouth interior and tongue
        c.oval(Hx + 4.6, Hy + 4.2 + jaw * 1.7, 4.4, 1.0 + jaw * 2.4, MOUTH);
        if (jaw > 0.2) {
            c.oval(Hx + 3.8, Hy + 5.2 + jaw * 2.5, 2.6, 0.6 + jaw * 1.0, TONGUE);
            c.set(Hx + 3, Hy + 4.8 + jaw * 2.3, TONGUE_HI);
        }
        // lower jaw
        c.ball(Hx + 4, jawY, 5.4, 2.1, PAPER_DK, 1);
        if (jaw > 0.25) for (int i = 0; i < 3; i++) c.set(Hx + 2.2 + i * 2.3, jawY - 2, TOOTH);

        ear(c, p, Hx - 3.5, Hy - 5.6, -0.35 + p.ear * 0.8 - jaw * 0.35, PAPER_DK);
        c.ball(Hx, Hy, 7.8, 7.0, PAPER, 1);
        c.ball(Hx + 5.5, Hy + 1.8 - jaw * 0.4, 4.6, 3.4, PAPER, 1);
        // creases and the torn rim of the bag
        c.line(Hx - 5, Hy - 3, Hx - 2, Hy - 1, PAPER[2]);
        c.line(Hx - 4, Hy + 3, Hx - 1, Hy + 2, PAPER[2]);
        c.line(Hx + 1, Hy - 6, Hx + 2, Hy - 3.5, PAPER[2]);
        c.line(Hx + 6, Hy - 0.5, Hx + 8, Hy + 0.5, PAPER[2]);
        c.set(Hx - 3, Hy - 5, PAPER[5]);
        c.set(Hx - 2, Hy - 5.4, PAPER[5]);
        c.set(Hx - 5, Hy - 2, PAPER[4]);
        for (int i = 0; i < 11; i++) {
            double x = Hx - 5.5 + i, y = Hy - 6.3 + (i % 2) * 0.9;
            c.setOnPx(x, y, PAPER[5]);
            c.setOnPx(x, y + 1, PAPER[1]);
        }
        ear(c, p, Hx + 3, Hy - 5.4, -0.15 + p.ear - jaw * 0.45, PAPER);

        eyes(c, p, Hx, Hy);
        // nostrils
        c.set(Hx + 9, Hy + 0.8, HOLE);
        c.set(Hx + 8, Hy + 1.4, HOLE);
        // jagged torn-paper teeth on the upper lip
        for (int i = 0; i < 5; i++) {
            double x = Hx + 1.4 + i * 1.8, y = Hy + 4.5 - jaw * 0.4;
            c.set(x, y, TOOTH);
            if (jaw > 0.3 || i % 2 == 0) c.set(x, y + 1, i % 2 == 0 ? TOOTH : TOOTH_SH);
        }
        // drool
        double len = 1 + (Math.sin(p.t * 1.7) * 0.5 + 0.5) * 3 + jaw * 2;
        double dx = Hx + 8.3;
        for (int j = 0; j < (int) len; j++) c.set(dx + Math.sin(p.t * 2 + j) * 0.3, jawY - 1 + j, SLIME);
        c.set(dx, jawY - 1 + (int) len, SLIME_HI);
        double d = (p.t * 0.6) % 1;
        if (d > 0.7) c.set(dx, jawY + len + (d - 0.7) / 0.3 * 7, SLIME);
    }

    private static void ear(PixelCanvas c, Pose p, double px, double py, double ang, int[] ramp) {
        c.polyRot(px, py, ang, new double[]{-2.8, 0.2, 3.2}, new double[]{1.2, -7.2, 1.2}, ramp[0]);
        c.polyRot(px, py, ang, new double[]{-1.7, 0.2, 2.1}, new double[]{0.6, -5.6, 0.6}, ramp[3]);
        c.polyRot(px, py, ang, new double[]{-0.4, 0.2, 0.9}, new double[]{0.4, -4.0, 0.4}, ramp[2]);
    }

    private static void eyes(PixelCanvas c, Pose p, double Hx, double Hy) {
        double[][] eyes = {{Hx - 1.2, Hy - 1.9, 1.7, 1.8}, {Hx + 3.2, Hy - 1.6, 2.2, 2.0}};
        boolean rage = p.eyes == 2;
        // brows: furrowed creases, angled down toward the middle when angry
        double tilt = rage ? 1.3 : 0.3;
        c.line(Hx - 3.2, Hy - 4.4 - tilt * 0.4, Hx + 0.4, Hy - 4.0 + tilt * 0.5, rage ? PAPER[0] : PAPER[1]);
        c.line(Hx + 1.6, Hy - 4.0 + tilt * 0.5, Hx + 5.6, Hy - 4.3 - tilt * 0.6, rage ? PAPER[0] : PAPER[1]);
        for (double[] e : eyes) {
            double ex = e[0], ey = e[1], rx = e[2], ry = e[3];
            if (p.eyes == 1) {
                c.line(ex - rx, ey + 0.3, ex + rx, ey + 0.3, HOLE);
                continue;
            }
            c.oval(ex, ey, rx, ry, HOLE);
            if (p.eyes == 3) {
                c.set(ex - 1, ey - 1, EYE_HOT);
                c.set(ex + 1, ey + 1, EYE_HOT);
                c.set(ex + 1, ey - 1, EYE_HOT);
                c.set(ex - 1, ey + 1, EYE_HOT);
                c.set(ex, ey, EYE_HOT);
                continue;
            }
            c.oval(ex, ey, rx - 0.55, ry - 0.55, rage ? EYE_RAGE : EYE);
            c.set(ex - 0.6, ey - 0.8, rage ? EYE_RAGE_HOT : EYE_HOT);
            double lx = ex + p.look * 0.7;
            c.set(lx + 0.4, ey, PUPIL);
            c.set(lx + 0.4, ey + 0.9, PUPIL);
        }
    }

    private static void flowers(PixelCanvas c, Pose p, double hx, double hTop, double cx, double cTop) {
        double[][] spots = {{hx - 6, hTop + 2, 0}, {hx, hTop + 1, 1}, {cx - 1, cTop + 1, 2}, {cx + 6, cTop + 2, 0}, {hx + 4, hTop + 2.5, 1}};
        int[] cols = {0xFFFFFF, PETAL, 0xFFE27A};
        for (int i = 0; i < spots.length; i++) {
            double k = PixelCanvas.span(p.bloom, i * 0.12, i * 0.12 + 0.45);
            if (k <= 0) continue;
            double fx = spots[i][0], base = spots[i][1];
            int stem = (int) Math.round(k * 3);
            for (int j = 0; j < stem; j++) c.set(fx, base - j, GRASS);
            if (k < 0.5) continue;
            double fy = base - stem;
            int col = cols[(int) spots[i][2]];
            c.set(fx - 1, fy, col);
            c.set(fx + 1, fy, col);
            c.set(fx, fy - 1, col);
            if (k > 0.85) c.blend(fx + 2, fy - 2, 0xFFFFFF, 190);   // sparkle
            c.set(fx, fy, FLOWER_C);
            c.set(fx + 1, base - 1, GRASS_HI);
        }
    }

    private static void stink(PixelCanvas c, double t, double x0, double y0, double x1, double y1) {
        for (int k = 0; k < 2; k++) {
            double bx = k == 0 ? x0 : x1, by = k == 0 ? y0 : y1;
            double rise = ((t * 0.7 + k * 0.5) % 1) * 3;
            for (int j = 0; j < 7; j++) {
                double x = bx + Math.sin(j * 0.9 + t * 3 + k * 2) * 1.2;
                c.blend(x, by - j - rise, STINK, (int) (150 * (1 - j / 7.0)));
            }
        }
    }

    private static void flies(PixelCanvas c, double t, double cx, double cy) {
        for (int i = 0; i < 3; i++) {
            double ang = t * (2.4 + i * 0.5) * (i % 2 == 0 ? 1 : -1) + i * 2.1;
            double fx = cx + Math.cos(ang) * (12 + i * 2), fy = cy + Math.sin(ang * 2) * 3 - i;
            c.set(fx, fy, 0x101010);
            if (((int) (t * 22) + i) % 2 == 0) c.blend(fx, fy - 1, 0xE8F0F8, 210);
            else {
                c.blend(fx - 1, fy - 1, 0xE8F0F8, 200);
                c.blend(fx + 1, fy - 1, 0xE8F0F8, 200);
            }
        }
    }

    private static void box(PixelCanvas c, double x, double y, int w, int h, int rgb) {
        int x0 = (int) Math.round(x), y0 = (int) Math.round(y);
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) c.set(x0 + i, y0 + j, rgb);
    }
}
