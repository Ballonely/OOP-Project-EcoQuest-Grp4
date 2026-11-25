package com.ecoquest.level1;

import static com.ecoquest.level1.PixelCanvas.span;

/**
 * Procedural pixel art for the Level 3 boss, the Waste King: a fat royal-purple garbage bag
 * enthroned on a heap of market trash (a fruit crate, fish bones, cans, peels, eggshells), wearing
 * a striped market-awning tarp as a cape, a dented tin-can crown set with bottle caps, a
 * caution-tape sash, a banana-peel moustache and yellow dishwashing gloves, and waving a giant
 * plastic fork as its sceptre.
 *
 * <p>The sprite is repainted every frame from a {@link Pose} of continuous values, so breathing,
 * blinking, the rippling cape, the lagging crown, rage slams, hit flinches and the final collapse
 * all blend smoothly. {@link Pose#size} scales the whole king about its base, so it shrinks
 * steadily as the market is cleaned.
 */
final class WasteKingArt {
    static final int W = 100, H = 104;
    /** Anchor: centre of the heap's base. */
    static final int AX = 50, GROUND = 96;

    // ---- palette ----
    static final int OUT = 0x150E14;
    /** Height of the eyes on the torso, as a fraction of its vertical radius above the centre. */
    private static final double FACE_Y = 0.2;
    private static final int[] BAG = {OUT, 0x251D3A, 0x352C54, 0x4A4070, 0x655B92, 0x8A80B8};
    private static final int[] ARM = {OUT, 0x271F3A, 0x372E52, 0x4B4270, 0x655C8E};
    private static final int BAG_SHINE = 0xC9C3EC, BAG_SHINE_DIM = 0x9890C4;
    private static final int[] HEAP = {0x1A120A, 0x3A2A16, 0x52401F, 0x6B5A2C, 0x877640, 0xA3965A};
    private static final int[] GLOVE = {0x4E3606, 0xB0801A, 0xDDAD36, 0xF4D266, 0xFFF0B0};
    private static final int[] GOLD = {0x4A2E06, 0xA06C14, 0xD9A226, 0xF4CC4C, 0xFFF0A4};
    private static final int CAPE_RED = 0xC8382E, CAPE_RED_DK = 0x86221E, CAPE_RED_HI = 0xEA5E4A;
    private static final int CAPE_WHT = 0xE9E1CF, CAPE_WHT_DK = 0xA9A08E, CAPE_WHT_HI = 0xFFFDF4;
    private static final int SCLERA = 0xFFF6D8, SCLERA_SH = 0xD8C99E, IRIS = 0xF7D23A, IRIS_RAGE = 0xFF3B2A, PUPIL = 0x1B0E08;
    private static final int MOUTH = 0x2A0A10, TONGUE = 0xA63A5A, TONGUE_HI = 0xD8728E, TOOTH = 0xF4EEDA, TOOTH_OLD = 0xD9CC92;
    private static final int PEEL = 0xF2D24A, PEEL_SH = 0xC7A42C, PEEL_TIP = 0x5E3E16;
    private static final int TAPE = 0xF2C230, TAPE_DK = 0x231C10;
    private static final int FORK = 0xF4F4EE, FORK_SH = 0xC4C6C8;
    private static final int SLUDGE = 0x6E8A2A, SLUDGE_DK = 0x4A5E1C, SLUDGE_HI = 0xA8C85A;
    private static final int WOOD = 0xA8743A, WOOD_DK = 0x6E4620, WOOD_HI = 0xD39C5A;
    private static final int BONE = 0xE8E2CC, CAN_R = 0xCF3B30, CAN_B = 0x3B7FD0, SILVER = 0xD6D6DA;
    private static final int CABBAGE = 0x7CC46A, CABBAGE_DK = 0x4E8E44, BOTTLE = 0x8FD0F0, BOTTLE_DK = 0x3E7A9A;
    private static final int GRASS = 0x4E9E3A, GRASS_HI = 0x8ED86A, STINK = 0xB2C86E;
    private static final int[] JEWELS = {0xD63A3A, 0x3B8FD9, 0x45B04E};
    private static final int[] SPECKS = {0xE34B4B, 0x3B8FD9, 0xF2D33B, 0xF4F4F4, 0xC98C4A, 0x7CC46A, 0x9DB8C9, 0x8E44AD};

    /** Every value the painter needs; all continuous so poses blend. */
    static final class Pose {
        double t, size = 1, emerge = 1;
        double breathe, squash, lean;
        /** Arm joint angles (radians, 0 = right, +pi/2 = down) and the fork's tilt from vertical. */
        double armL1 = 2.05, armL2 = 2.6, armR1 = 1.15, armR2 = -0.35, forkAng = 0.12;
        double jaw = 0.15, lookX, lookY, lid, brow;
        int eyes;                 // 0 open, 2 rage, 3 dizzy, 4 squeezed (hit)
        double crownLift, crownTilt;
        boolean crown = true, fork = true, flies = true, stink = true;
        double flash, rage, deflate, sprout, glow = 0.6;
    }

    private final PixelCanvas c;
    private double s = 1;

    WasteKingArt(PixelCanvas c) { this.c = c; }

    // ------------------------------------------------------------ local -> canvas
    private double X(double dx) { return AX + dx * s; }
    private double Y(double dy) { return GROUND + dy * s; }

    private void ball(double x, double y, double rx, double ry, int[] ramp, double rim) { c.ball(X(x), Y(y), rx * s, ry * s, ramp, rim); }
    private void oval(double x, double y, double rx, double ry, int rgb) { c.oval(X(x), Y(y), rx * s, ry * s, rgb); }
    private void cap(double x0, double y0, double x1, double y1, double r, int rgb) { c.capsule(X(x0), Y(y0), X(x1), Y(y1), Math.max(0.5, r * s), rgb); }
    private void line(double x0, double y0, double x1, double y1, int rgb) { c.line(X(x0), Y(y0), X(x1), Y(y1), rgb); }
    private void dot(double x, double y, int rgb) { c.set(X(x), Y(y), rgb); }
    private void dotOn(double x, double y, int rgb) { c.setOnPx(X(x), Y(y), rgb); }

    private void box(double x, double y, double w, double h, int rgb) {
        int x0 = (int) Math.round(X(x)), y0 = (int) Math.round(Y(y));
        int x1 = (int) Math.round(X(x + w)), y1 = (int) Math.round(Y(y + h));
        for (int j = y0; j < Math.max(y0 + 1, y1); j++) for (int i = x0; i < Math.max(x0 + 1, x1); i++) c.set(i, j, rgb);
    }

    // ------------------------------------------------------------ paint

    void paint(Pose p) {
        c.clear();
        s = p.size;
        double t = p.t;
        double heapGrow = PixelCanvas.easeOutBack(span(p.emerge, 0, 0.45));
        double riseOff = (1 - PixelCanvas.easeOut(span(p.emerge, 0.25, 1))) * 46;
        boolean risen = p.emerge >= 1;

        // torso frame
        double def = p.deflate;
        double tx = p.lean * 3;
        double ty = -31 + p.breathe * 0.6 + p.squash * 1.5 + def * 13 + riseOff;
        double rx = 16 + p.squash * 1.2 + def * 3 + p.breathe * 0.25;
        double ry = 15 - p.squash * 1.3 - def * 7 - p.breathe * 0.2;

        sludge(t, heapGrow);
        cape(p, tx, ty, rx, ry);
        if (!risen) {
            leftArm(p, tx, ty, rx, ry);
            rightArm(p, tx, ty, rx, ry);
        }
        torso(p, tx, ty, rx, ry);
        face(p, tx + p.lean * 0.6, ty - ry * FACE_Y);
        if (p.crown) crown(c, X(tx + p.lean * 1.2), Y(ty - ry + 2.5 - p.crownLift), p.crownTilt + p.lean * 0.12, s);
        if (!risen) c.clearBelow((int) Math.round(Y(-3)));
        if (risen) leftArm(p, tx, ty, rx, ry);

        heap(p, heapGrow);
        if (risen) rightArm(p, tx, ty, rx, ry);

        c.outline(OUT);
        if (p.flash > 0) c.tint(0xFFFFFF, Math.min(1, p.flash) * 0.85);
        if (p.rage > 0) c.tint(0xFF2A1A, p.rage * 0.28);

        if (p.eyes == 0 || p.eyes == 2) {
            double pulse = 0.7 + 0.3 * Math.sin(t * 4);
            int col = p.eyes == 2 ? IRIS_RAGE : IRIS;
            int a = (int) (p.glow * pulse * (p.eyes == 2 ? 110 : 55) * (1 - p.lid));
            double fx = tx + p.lean * 0.6, fy = ty - ry * FACE_Y;
            c.ovalA(X(fx - 6.5), Y(fy), 6.5 * s, 5.5 * s, col, a);
            c.ovalA(X(fx + 6.5), Y(fy), 6.5 * s, 5.5 * s, col, a);
        }
        if (p.sprout > 0) sprouts(p);
        if (p.stink) stink(t, heapGrow);
        if (p.flies) flies(t, ty - ry);
    }

    // ------------------------------------------------------------ heap

    private void sludge(double t, double grow) {
        if (grow <= 0.05) return;
        oval(0, -1.5, 31 * grow, 3.8 * grow, SLUDGE_DK);
        oval(-1, -1.8, 28 * grow, 2.7 * grow, SLUDGE);
        for (int i = 0; i < 5; i++) {   // slow ripples
            double k = (t * 0.35 + i * 0.2) % 1;
            double x = (-22 + i * 11) * grow + Math.sin(t + i) * 2;
            int a = (int) (200 * Math.sin(k * Math.PI));
            c.blend(X(x), Y(-1.8 + (i % 2) * 0.8), SLUDGE_HI, a);
            c.blend(X(x + 1), Y(-1.8 + (i % 2) * 0.8), SLUDGE_HI, a / 2);
        }
    }

    private void heap(Pose p, double g) {
        if (g <= 0.03) return;
        double fl = 1 - p.deflate * 0.25;
        ball(-17 * g, -7 * fl * g, 13 * g, 8 * fl * g, HEAP, 0);
        ball(18 * g, -6.5 * fl * g, 12.5 * g, 7.5 * fl * g, HEAP, 0);
        ball(0, -8 * fl * g, 24 * g, 9.5 * fl * g, HEAP, 0);
        if (g < 0.55) return;
        for (int i = 0; i < 40; i++) {                 // speckles of mixed rubbish
            int h1 = Math.floorMod(MarketTiles.hash(i, 31), 1000), h2 = Math.floorMod(MarketTiles.hash(i, 57), 1000);
            double a = Math.PI + h1 / 1000.0 * Math.PI, d = 0.35 + h2 / 1000.0 * 0.6;
            double x = Math.cos(a) * 27 * d * g, y = -6 + Math.sin(a) * 11 * d * fl * g;
            dotOn(x, y, SPECKS[i % SPECKS.length]);
            if (i % 3 == 0) dotOn(x + 1, y, SPECKS[(i + 3) % SPECKS.length]);
        }
        double k = g * fl;
        crate(-23 * g, -15 * k);
        fish(13 * g, -15 * k);
        canLying(5 * g, -5 * k, CAN_R);
        canUpright(-29 * g, -9 * k, CAN_B);
        peel(-10 * g, -4.5 * k);
        bottle(22 * g, -7 * k);
        cabbage(-17 * g, -3 * k);
        eggshell(-1 * g, -3 * k);
        eggshell(27 * g, -3 * k);
        // sludge oozing over the lip
        double t = p.t;
        double[] drips = {-20, 3, 17};
        for (int i = 0; i < drips.length; i++) {
            double len = 1 + (Math.sin(t * 1.3 + i * 2.1) * 0.5 + 0.5) * 3;
            for (int j = 0; j < len; j++) dot(drips[i] * g, -3 + j, SLUDGE);
            dot(drips[i] * g, -3 + (int) len, SLUDGE_HI);
        }
    }

    private void crate(double x, double y) {
        box(x - 1, y - 1, 12, 9, OUT);
        box(x, y, 10, 7, WOOD);
        box(x, y, 10, 1, WOOD_HI);
        line(x, y + 2.5, x + 9.5, y + 2.5, WOOD_DK);
        line(x, y + 5, x + 9.5, y + 5, WOOD_DK);
        line(x + 3.5, y, x + 3.5, y + 6.5, WOOD_DK);
        dot(x + 1, y + 1, 0x3A2A1A);
        dot(x + 8, y + 1, 0x3A2A1A);
        dot(x + 6, y + 3.5, 0xF07A3A);   // a squashed mango left inside
        dot(x + 7, y + 3.5, 0xF5B040);
    }

    private void fish(double x, double y) {
        line(x, y, x + 8, y, BONE);
        for (int i = 1; i < 7; i += 2) { dot(x + i, y - 1, BONE); dot(x + i, y + 1, BONE); }
        c.poly(new double[]{X(x + 8), X(x + 11), X(x + 8)}, new double[]{Y(y - 2), Y(y), Y(y + 2)}, BONE);
        dot(x + 9, y - 0.5, OUT);
        dot(x - 1, y - 1.5, BONE);
        dot(x - 1, y + 1.5, BONE);
    }

    private void canLying(double x, double y, int col) {
        cap(x, y, x + 5, y, 2.1, OUT);
        cap(x, y, x + 5, y, 1.3, col);
        line(x, y - 0.6, x + 4.5, y - 0.6, 0xF27E6E);
        dot(x + 5.5, y, SILVER);
        dot(x - 0.5, y, SILVER);
    }

    private void canUpright(double x, double y, int col) {
        box(x - 1, y - 1, 6, 7, OUT);
        box(x, y, 4, 5, col);
        box(x, y, 4, 1, SILVER);
        box(x, y + 1, 1, 4, 0x7EB2EE);
        dot(x + 3, y + 3, 0x24508A);
    }

    private void peel(double x, double y) {
        cap(x, y, x - 3, y - 2.5, 1.0, PEEL);
        cap(x, y, x + 3.5, y - 2, 1.0, PEEL);
        cap(x, y, x + 0.5, y + 1.5, 1.2, PEEL_SH);
        dot(x - 3.5, y - 3, PEEL_TIP);
        dot(x + 4, y - 2.5, PEEL_TIP);
    }

    private void bottle(double x, double y) {
        cap(x - 3, y, x + 3, y + 0.5, 1.9, BOTTLE_DK);
        cap(x - 3, y, x + 3, y + 0.5, 1.1, BOTTLE);
        line(x - 3, y - 0.8, x + 2, y - 0.5, 0xDDF4FF);
        dot(x + 5, y + 0.6, 0x45B04E);
    }

    private void cabbage(double x, double y) {
        ball(x, y, 3.2, 2.4, new int[]{0x1E3A18, CABBAGE_DK, 0x62A856, CABBAGE, 0xB4E89E}, 1);
        line(x - 1.5, y, x + 1.5, y - 0.5, 0xD8F4C4);
    }

    private void eggshell(double x, double y) {
        oval(x, y, 1.8, 1.1, 0xF2EEE2);
        dot(x - 1, y - 0.5, 0xFFFFFF);
        dot(x + 1, y + 0.4, 0xC8C0AA);
    }

    // ------------------------------------------------------------ cape, torso, face

    /** A striped market-awning tarp hanging from the shoulders; its folds ripple. */
    private void cape(Pose p, double tx, double ty, double rx, double ry) {
        double top = ty - ry * 0.55, bottom = -11 + p.deflate * 3;
        if (bottom <= top + 2) return;
        double t = p.t;
        int y0 = (int) Math.floor(Y(top)), y1 = (int) Math.ceil(Y(bottom + 2.5));
        for (int yy = y0; yy <= y1; yy++) {
            double ly = (yy + 0.5 - GROUND) / s;              // back to local units
            double k = (ly - top) / (bottom - top);
            if (k < 0) continue;
            double half = (rx * 0.82) + (27 - rx * 0.82) * Math.min(1, k) + Math.sin(t * 1.6 + k * 2) * 0.8 * k;
            double cx = tx * (1 - k) + Math.sin(t * 1.2) * 1.2 * k;
            for (int xx = (int) Math.floor(X(cx - half)); xx <= (int) Math.ceil(X(cx + half)); xx++) {
                double lx = (xx + 0.5 - AX) / s;
                double u = (lx - (cx - half)) / (2 * half);
                if (u < 0 || u > 1) continue;
                double hem = bottom + Math.sin(lx * 0.45 + t * 2.4) * 1.3 + Math.sin(lx * 0.9 - t * 1.7) * 0.5;
                if (ly > hem) continue;
                double fold = Math.sin(lx * 0.55 + t * 2.0 + ly * 0.08) * 0.5 + 0.5;
                double edge = Math.min(u, 1 - u);
                double light = fold * 0.75 + (edge < 0.12 ? -0.35 : 0) + (ly > hem - 1 ? -0.3 : 0)
                        + PixelCanvas.dither(xx, yy) * 0.25;
                int band = (int) Math.floor((lx - cx + 40) / 4.0);
                boolean red = (band & 1) == 0;
                int col = light < 0.32 ? (red ? CAPE_RED_DK : CAPE_WHT_DK)
                        : light > 0.85 ? (red ? CAPE_RED_HI : CAPE_WHT_HI)
                        : (red ? CAPE_RED : CAPE_WHT);
                c.set(xx, yy, col);
            }
        }
    }

    private void torso(Pose p, double tx, double ty, double rx, double ry) {
        ball(tx, ty, rx, ry, BAG, 0);
        ball(tx, ty + ry * 0.45, rx * 1.12, ry * 0.7, BAG, 0);
        // bag gloss and creases
        double sh = p.breathe * 0.3;
        dotOn(tx - rx * 0.62, ty - ry * 0.42 + sh, BAG_SHINE);
        dotOn(tx - rx * 0.55, ty - ry * 0.55 + sh, BAG_SHINE);
        dotOn(tx - rx * 0.45, ty - ry * 0.65 + sh, BAG_SHINE);
        dotOn(tx - rx * 0.34, ty - ry * 0.72 + sh, BAG_SHINE_DIM);
        dotOn(tx - rx * 0.72, ty - ry * 0.25 + sh, BAG_SHINE_DIM);
        dotOn(tx - rx * 0.88, ty + ry * 0.5, BAG_SHINE_DIM);
        crease(tx + rx * 0.55, ty - ry * 0.6, tx + rx * 0.75, ty - ry * 0.1);
        crease(tx - rx * 0.6, ty + ry * 0.2, tx - rx * 0.35, ty + ry * 0.55);
        crease(tx + rx * 0.2, ty + ry * 0.95, tx + rx * 0.5, ty + ry * 0.75);
        // caution-tape sash across the belly, with a bottle-cap medal
        double ax = tx - rx * 1.0, ay = ty + ry * 0.28, bx = tx + rx * 1.0, by = ty + ry * 0.9;
        double len = Math.hypot(bx - ax, by - ay), nx = -(by - ay) / len, ny = (bx - ax) / len;
        int steps = (int) Math.ceil(len * s * 2);
        for (int i = 0; i <= steps; i++) {
            double k = i / (double) steps;
            double cx = ax + (bx - ax) * k, cy = ay + (by - ay) * k;
            boolean yellow = ((int) Math.floor(k * len / 2.6) & 1) == 0;
            for (double w = -1.8; w <= 1.8; w += 0.5 / s) {
                int col = Math.abs(w) > 1.4 ? TAPE_DK : yellow ? TAPE : TAPE_DK;
                dotOn(cx + nx * w, cy + ny * w, col);
            }
        }
        double mx = tx - rx * 0.4, my = ay + (by - ay) * 0.3;
        ball(mx, my, 2.6, 2.6, GOLD, 1);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            dot(mx + Math.cos(a) * 2.6, my + Math.sin(a) * 2.6, GOLD[1]);
        }
        dot(mx - 0.8, my - 0.8, GOLD[4]);
    }

    private void crease(double x0, double y0, double x1, double y1) {
        int n = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * s) + 1;
        for (int i = 0; i <= n; i++) {
            double k = i / (double) n;
            dotOn(x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, BAG[1]);
            dotOn(x0 + (x1 - x0) * k - 1, y0 + (y1 - y0) * k, BAG[4]);
        }
    }

    private void face(Pose p, double fx, double fy) {
        double t = p.t;
        // eyes
        for (int side = -1; side <= 1; side += 2) {
            double ex = fx + side * 6.5, ey = fy;
            oval(ex, ey, 5.0, 4.6, OUT);
            switch (p.eyes) {
                case 4 -> {     // squeezed shut: > <
                    oval(ex, ey, 4.2, 3.8, BAG[3]);
                    double d = -side;
                    line(ex - 2.5 * d, ey - 2, ex + 1.5 * d, ey, OUT);
                    line(ex + 1.5 * d, ey, ex - 2.5 * d, ey + 2, OUT);
                }
                case 3 -> {     // dizzy spirals
                    oval(ex, ey, 4.2, 3.8, SCLERA);
                    for (int i = 0; i < 9; i++) {
                        double a = i * 0.8 + t * 9 * side, r = 0.4 + i * 0.36;
                        dot(ex + Math.cos(a) * r, ey + Math.sin(a) * r * 0.9, PUPIL);
                    }
                }
                default -> {
                    oval(ex, ey, 4.2, 3.8, SCLERA);
                    oval(ex, ey + 2.2, 3.6, 1.4, SCLERA_SH);
                    double ix = ex + p.lookX * 1.5, iy = ey + 0.3 + p.lookY * 1.0;
                    int iris = p.eyes == 2 ? IRIS_RAGE : IRIS;
                    oval(ix, iy, 2.3, 2.3, iris);
                    oval(ix, iy, 1.1, 1.3, PUPIL);
                    dot(ix - 1, iy - 1, 0xFFFFFF);
                    if (p.lid > 0.02) {     // eyelid slides down for blinks
                        double lidY = ey - 4.0 + 8.0 * p.lid;
                        for (double yy = ey - 4.2; yy < lidY; yy += 0.5 / s)
                            for (double xx = ex - 4.4; xx <= ex + 4.4; xx += 0.5 / s) {
                                double nx = (xx - ex) / 4.3, ny = (yy - ey) / 3.9;
                                if (nx * nx + ny * ny <= 1) dot(xx, yy, BAG[3]);
                            }
                        line(ex - 3.6, lidY, ex + 3.6, lidY, OUT);
                    }
                }
            }
            // heavy brow, sloping toward the nose as the king gets angry
            double inner = ex - side * 4.5, outer = ex + side * 4.5;
            double drop = 1.2 + p.brow * 2.4;
            cap(outer, ey - 5.8 - p.brow * 0.6, inner, ey - 5.6 + drop - 1.2, 1.5, OUT);
            line(outer, ey - 6.6 - p.brow * 0.6, inner, ey - 6.4 + drop - 1.2, BAG[2]);
        }

        // mouth
        double my = fy + 8.5, jaw = p.jaw;
        oval(0 + fx, my + jaw * 1.8, 8.6, 1.2 + jaw * 4.4, OUT);
        oval(0 + fx, my + jaw * 1.8, 7.8, 0.6 + jaw * 3.8, MOUTH);
        if (jaw > 0.25) {
            oval(fx + 1, my + jaw * 3.6, 4.5, 0.6 + jaw * 1.4, TONGUE);
            dot(fx - 1, my + jaw * 3.4, TONGUE_HI);
        }
        for (int i = 0; i < 6; i++) {   // mismatched teeth, one missing
            if (i == 4) continue;
            double x = fx - 6.2 + i * 2.5;
            int col = i % 3 == 1 ? TOOTH_OLD : TOOTH;
            box(x, my - 0.9 - jaw * 0.2, 1.6, 1.6 + (i % 2) * 0.6, col);
        }
        if (jaw > 0.3)
            for (int i = 0; i < 3; i++) box(fx - 3.6 + i * 3.2, my + jaw * 3.6 + 0.4, 1.5, 1.3, TOOTH);
        // smug upturned corners
        dot(fx - 8.8, my - 1, OUT);
        dot(fx + 8.8, my - 1, OUT);

        // banana-peel moustache (twitches when he talks)
        double tw = Math.sin(t * 3) * 0.3 + jaw * 1.4;
        for (int side = -1; side <= 1; side += 2) {
            double x0 = fx + side * 0.8, y0 = my - 3.2;
            double x1 = fx + side * 6.5, y1 = my - 3.0 + jaw * 0.6;
            double x2 = fx + side * 10.2, y2 = my - 5.8 - tw;
            cap(x0, y0, x1, y1, 1.6, OUT);
            cap(x1, y1, x2, y2, 1.3, OUT);
            cap(x0, y0, x1, y1, 0.95, PEEL);
            cap(x1, y1, x2, y2, 0.7, PEEL);
            line(x0, y0 + 0.6, x1, y1 + 0.6, PEEL_SH);
            dot(x2, y2, PEEL_TIP);
            dot(x0 + side * 3, y0 + 0.2, PEEL_TIP);
        }
    }

    // ------------------------------------------------------------ arms, fork, crown

    private void leftArm(Pose p, double tx, double ty, double rx, double ry) {
        double sx = tx - rx * 0.86, sy = ty - ry * 0.12;
        double ex = sx + Math.cos(p.armL1) * 9.5, ey = sy + Math.sin(p.armL1) * 9.5;
        double hx = ex + Math.cos(p.armL2) * 8.5, hy = ey + Math.sin(p.armL2) * 8.5;
        limb(sx, sy, ex, ey, 3.1);
        limb(ex, ey, hx, hy, 2.8);
        glove(hx, hy, p.armL2);
    }

    private void rightArm(Pose p, double tx, double ty, double rx, double ry) {
        double sx = tx + rx * 0.86, sy = ty - ry * 0.12;
        double ex = sx + Math.cos(p.armR1) * 9.5, ey = sy + Math.sin(p.armR1) * 9.5;
        double hx = ex + Math.cos(p.armR2) * 8.5, hy = ey + Math.sin(p.armR2) * 8.5;
        limb(sx, sy, ex, ey, 3.1);
        limb(ex, ey, hx, hy, 2.8);
        if (p.fork) fork(hx, hy, p.forkAng);
        glove(hx, hy, p.armR2);
    }

    private void limb(double x0, double y0, double x1, double y1, double r) {
        c.limb(X(x0), Y(y0), X(x1), Y(y1), Math.max(1.2, r * s), ARM);
    }

    private void glove(double hx, double hy, double ang) {
        double cx = hx - Math.cos(ang) * 2.2, cy = hy - Math.sin(ang) * 2.2;
        cap(cx, cy, hx - Math.cos(ang) * 0.6, hy - Math.sin(ang) * 0.6, 2.6, GLOVE[1]);   // cuff
        ball(hx + Math.cos(ang) * 0.8, hy + Math.sin(ang) * 0.8, 2.9, 2.6, GLOVE, 1);
        for (int i = -1; i <= 1; i++) {                                               // finger nubs
            double a = ang + i * 0.55;
            dot(hx + Math.cos(a) * 3.4, hy + Math.sin(a) * 3.4, GLOVE[2]);
        }
    }

    /** A giant white plastic fork; {@code ang} tilts it from pointing straight up. */
    private void fork(double hx, double hy, double ang) {
        double dx = Math.sin(ang), dy = -Math.cos(ang), nx = -dy, ny = dx;
        double bx = hx - dx * 7, by = hy - dy * 7;
        double nkx = hx + dx * 15, nky = hy + dy * 15;
        cap(bx, by, nkx, nky, 1.7, OUT);
        cap(nkx, nky, nkx + dx * 3, nky + dy * 3, 2.9, OUT);
        for (int i = -1; i <= 1; i++) {
            double ox = nx * i * 2.0, oy = ny * i * 2.0;
            cap(nkx + dx * 3 + ox, nky + dy * 3 + oy, nkx + dx * 9 + ox, nky + dy * 9 + oy, 1.0, OUT);
        }
        cap(bx, by, nkx, nky, 0.9, FORK);
        cap(nkx, nky, nkx + dx * 3, nky + dy * 3, 2.1, FORK);
        for (int i = -1; i <= 1; i++) {
            double ox = nx * i * 2.0, oy = ny * i * 2.0;
            line(nkx + dx * 3 + ox, nky + dy * 3 + oy, nkx + dx * 8.6 + ox, nky + dy * 8.6 + oy, FORK);
        }
        line(bx + nx * 0.6, by + ny * 0.6, nkx + nx * 0.6, nky + ny * 0.6, FORK_SH);
    }

    /** The dented tin-can crown, drawn with its base centre at canvas point (ox, oy). */
    static void crown(PixelCanvas c, double ox, double oy, double tilt, double sc) {
        double[] bandX = {-9, 9, 9, -9}, bandY = {0, 0, -5.5, -5.5};
        double[] outX = new double[bandX.length], outY = new double[bandY.length];
        for (int i = 0; i < bandX.length; i++) { outX[i] = (bandX[i] + Math.signum(bandX[i])) * sc; outY[i] = (bandY[i] + (bandY[i] == 0 ? 1 : -1)) * sc; }
        // spikes
        for (int i = 0; i < 5; i++) {
            double x = -7.2 + i * 3.6, tip = i == 2 ? -12.5 : i % 2 == 0 ? -10 : -11;
            double[] sx = {(x - 2.6) * sc, x * sc, (x + 2.6) * sc}, sy = {-4.5 * sc, (tip - 1.2) * sc, -4.5 * sc};
            c.polyRot(ox, oy, tilt, sx, sy, OUT);
        }
        c.polyRot(ox, oy, tilt, outX, outY, OUT);
        for (int i = 0; i < 5; i++) {
            double x = -7.2 + i * 3.6, tip = i == 2 ? -12.5 : i % 2 == 0 ? -10 : -11;
            double[] sx = {(x - 1.6) * sc, x * sc, (x + 1.6) * sc}, sy = {-5 * sc, tip * sc, -5 * sc};
            c.polyRot(ox, oy, tilt, sx, sy, GOLD[2]);
            double[] hx = {(x - 1.2) * sc, x * sc, x * sc}, hy = {-5 * sc, tip * sc, -5 * sc};
            c.polyRot(ox, oy, tilt, hx, hy, GOLD[3]);
        }
        double[] fx = new double[4], fy = new double[4];
        for (int i = 0; i < 4; i++) { fx[i] = bandX[i] * sc; fy[i] = bandY[i] * sc; }
        c.polyRot(ox, oy, tilt, fx, fy, GOLD[2]);
        double[] lx = {-9 * sc, 9 * sc, 9 * sc, -9 * sc}, ly = {-5.5 * sc, -5.5 * sc, -4.4 * sc, -4.4 * sc};
        c.polyRot(ox, oy, tilt, lx, ly, GOLD[4]);
        double[] dx = {-9 * sc, 9 * sc, 9 * sc, -9 * sc}, dy = {-1.1 * sc, -1.1 * sc, 0, 0};
        c.polyRot(ox, oy, tilt, dx, dy, GOLD[1]);
        // can ribs, dents and bottle-cap jewels
        double co = Math.cos(tilt), si = Math.sin(tilt);
        for (int i = 0; i < 3; i++) {
            double jx = (-5.5 + i * 5.5) * sc, jy = -2.8 * sc;
            double px = ox + jx * co - jy * si, py = oy + jx * si + jy * co;
            c.oval(px, py, 1.7 * sc, 1.7 * sc, OUT);
            c.oval(px, py, 1.1 * sc, 1.1 * sc, JEWELS[i]);
            c.set(px - 0.5 * sc, py - 0.6 * sc, 0xFFFFFF);
        }
        double[][] dents = {{-7.5, -2}, {3, -1.6}, {7.8, -3.4}};
        for (double[] d : dents) {
            double px = ox + d[0] * sc * co - d[1] * sc * si, py = oy + d[0] * sc * si + d[1] * sc * co;
            c.set(px, py, GOLD[1]);
        }
    }

    // ------------------------------------------------------------ extras

    private void sprouts(Pose p) {
        double[][] spots = {{-18, -12}, {-6, -16}, {7, -15}, {19, -11}, {0, -9}, {-26, -6}, {26, -7}};
        for (int i = 0; i < spots.length; i++) {
            double k = span(p.sprout, i * 0.08, i * 0.08 + 0.5);
            if (k <= 0) continue;
            double x = spots[i][0], y = spots[i][1] * (1 - p.deflate * 0.25);
            int stem = (int) Math.round(k * 4);
            for (int j = 0; j < stem; j++) dot(x, y - j / s, GRASS);
            double top = y - stem / s;
            if (k > 0.45) {
                dot(x - 1 / s, top + 0.5 / s, GRASS_HI);
                dot(x + 1 / s, top, GRASS_HI);
            }
            if (k > 0.8 && i % 2 == 0) {
                int col = i % 4 == 0 ? 0xFFFFFF : 0xF6B3C8;
                dot(x, top - 1 / s, col);
                dot(x - 1 / s, top - 1 / s, col);
                dot(x + 1 / s, top - 1 / s, col);
                dot(x, top - 2 / s, col);
                dot(x, top - 1 / s, 0xF5D33C);
            }
        }
    }

    private void stink(double t, double g) {
        double[] xs = {-20, 21, -4};
        for (int k = 0; k < xs.length; k++) {
            double rise = ((t * 0.6 + k * 0.33) % 1) * 4;
            for (int j = 0; j < 8; j++) {
                double x = xs[k] * g + Math.sin(j * 0.9 + t * 2.6 + k * 2) * 1.3;
                c.blend(X(x), Y(-14 * g - j - rise), STINK, (int) (140 * (1 - j / 8.0)));
            }
        }
    }

    private void flies(double t, double headTop) {
        for (int i = 0; i < 4; i++) {
            double ang = t * (2.2 + i * 0.4) * (i % 2 == 0 ? 1 : -1) + i * 1.7;
            double fx = Math.cos(ang) * (20 + i * 3), fy = headTop - 4 + Math.sin(ang * 2) * 4 + i * 5;
            double px = X(fx), py = Y(fy);
            c.set(px, py, 0x101010);
            if (((int) (t * 22) + i) % 2 == 0) c.blend(px, py - 1, 0xE8F0F8, 210);
            else {
                c.blend(px - 1, py - 1, 0xE8F0F8, 200);
                c.blend(px + 1, py - 1, 0xE8F0F8, 200);
            }
        }
    }
}
