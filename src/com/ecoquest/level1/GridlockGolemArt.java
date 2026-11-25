package com.ecoquest.level1;

import static com.ecoquest.level1.PixelCanvas.lerp;

/**
 * Procedural pixel art for the Level 5 boss, the Gridlock Golem: a hulking city scrap-golem whose
 * torso is a crushed, rusty red hatchback (cracked windshield chest, chrome-grille mouth with an
 * engine glowing inside, mismatched headlights), with stacked-tyre legs and tyre shoulder pads,
 * two exhaust pipes on its back that belch smog, tangled e-waste cable arms (a car battery for one
 * fist, a red jumper-cable clamp for the other) and, for a head, a broken traffic light wearing a
 * traffic cone.
 *
 * <p>The lit lamp of the traffic light is its eye and its tell: green while it roams, yellow while
 * it winds up an attack, red as the attack goes off. The sprite is repainted every frame from a
 * {@link Pose} so every animation blends smoothly. It faces right; the boss mirrors it.
 */
final class GridlockGolemArt {
    static final int W = 60, H = 66;
    /** Anchor: the point between the feet on the ground. */
    static final int AX = 30, GROUND = 62;

    /** Which lamp of the traffic-light head is lit. */
    static final int LIGHT_OFF = -1, GREEN = 0, YELLOW = 1, RED = 2;

    // ---- palette ----
    static final int OUT = 0x14100E;
    private static final int[] PAINT = {OUT, 0x5A1E1A, 0x8A2E24, 0xB04434, 0xCF6A4E};
    private static final int RUST = 0x8A4E22, RUST_DK = 0x5E3416, DENT = 0x4A1612;
    private static final int GLASS = 0x6FA8C4, GLASS_HI = 0xCDEBF5, GLASS_DK = 0x3E6E88, CRACK = 0xE8F6FA;
    private static final int[] CHROME = {OUT, 0x5C6266, 0x8D959A, 0xBFC6CA, 0xEEF2F4};
    private static final int[] RUBBER = {OUT, 0x1C1C1E, 0x2B2B2E, 0x3E3E42, 0x58585E};
    private static final int[] RUBBER_FAR = {OUT, 0x151517, 0x1F1F22, 0x2D2D31, 0x3E3E43};
    private static final int HUB = 0x9AA2A6, HUB_DK = 0x60686C;
    private static final int[] CABLE = {OUT, 0x1E2224, 0x2E3438, 0x465056};
    private static final int[] WIRES = {0xD8402E, 0xE8C440, 0x3C7EDC, 0x48A848};
    private static final int[] PIPE = {OUT, 0x4A4E50, 0x6E7476, 0x979EA0, 0xC4CACC};
    private static final int SOOT = 0x221E1A;
    private static final int HOUSING = 0x232B26, HOUSING_HI = 0x3C4A40, BACKPLATE = 0xE0A82E, BACKPLATE_DK = 0xA87618;
    private static final int[] LAMP_ON = {0x5CFF7A, 0xFFC83A, 0xFF3B2E};
    private static final int[] LAMP_HOT = {0xE0FFE6, 0xFFF6CC, 0xFFD6CC};
    private static final int[] LAMP_DIM = {0x1E4A2A, 0x5A4618, 0x5A1A16};
    private static final int CONE = 0xF07A1E, CONE_DK = 0xB4520E, CONE_STRIPE = 0xF4F4F0;
    private static final int BATTERY = 0x26282A, BATTERY_TOP = 0x3A3E40, TERM_RED = 0xD8402E, TERM_BLK = 0x111111, LABEL = 0xE8C440;
    private static final int CLAMP = 0xD8402E, CLAMP_DK = 0x8E2018, CLAMP_HI = 0xF27E6E;
    private static final int HEADLIGHT = 0xFFF2A8, HEADLIGHT_HOT = 0xFFFFFF, HEADLIGHT_DIM = 0x8A8458;
    private static final int MOUTH = 0x1A0C08, EMBER = 0xFF7A1E, EMBER_HI = 0xFFD060;
    private static final int PLATE = 0xF0EEE4, PLATE_TXT = 0x2A3A6A;
    private static final int FLAME = 0xFF9A2E, FLAME_HI = 0xFFE070;

    /** Every value the painter needs, all continuous so poses blend. */
    static final class Pose {
        double t;              // seconds, for looping secondary motion
        double walk;           // gait phase (cycles)
        double walkAmt;        // 0 standing .. 1 full stride
        double breathe;        // -1..1
        double crouch;         // 0..1 squatting (anticipation)
        double lean;           // 0..1 leaning forward (charging)
        double armRaise;       // 0..1 throwing arm (clamp) lifted overhead
        double armRaiseL;      // 0..1 battery arm lifted (roar)
        double jaw;            // 0..1 grille mouth open
        double shake;          // horizontal jitter, px
        double headDX, headDY;
        int light = GREEN;     // lit lamp, see LIGHT_OFF / GREEN / YELLOW / RED
        double glow = 0.7;     // brightness of the lit lamp's glow
        double rev;            // 0..1 exhaust flames out of the pipes
        double collapse;       // 0..1 falling apart (death)
    }

    private GridlockGolemArt() {}

    static void paint(PixelCanvas c, Pose p) {
        c.clear();
        double cyc = p.walk * 2 * Math.PI, col = p.collapse;
        double bob = Math.abs(Math.sin(cyc)) * 1.5 * p.walkAmt;
        double tx = AX + p.shake + p.lean * 3;
        double ty = GROUND - 26 + p.crouch * 4 - bob + p.breathe * 0.5 + col * 13;

        // ---- legs: two stacked tyres each, the far one first ----
        double liftNear = Math.max(0, Math.sin(cyc)) * 3 * p.walkAmt;
        double liftFar = Math.max(0, -Math.sin(cyc)) * 3 * p.walkAmt;
        double stride = Math.cos(cyc) * 2.5 * p.walkAmt;
        tyreLeg(c, AX + 7 - stride + p.lean, GROUND - liftFar, ty, RUBBER_FAR, col);

        // ---- exhaust pipes behind the torso ----
        for (int side = -1; side <= 1; side += 2) {
            double px = tx + side * 10, top = ty - 24 + col * 6 + (side > 0 ? 1 : 0);
            c.limb(px, ty - 6, px + side * 1.5, top, 2.4, PIPE);
            c.oval(px + side * 1.5, top, 2.6, 1.3, SOOT);
            c.set(px + side * 1.5 - 1, top - 1, PIPE[4]);
        }

        // ---- battery arm (back side), behind the torso ----
        double shLX = tx - 16, shLY = ty - 6;
        double handLX = lerp(tx - 20 + Math.sin(p.t * 1.7) * 0.8, tx - 15, p.armRaiseL);
        double handLY = lerp(ty + 10 + col * 4, ty - 24, p.armRaiseL);
        double elbLX = lerp(tx - 22, tx - 24, p.armRaiseL), elbLY = lerp(ty + 2, ty - 10, p.armRaiseL);
        cableArm(c, shLX, shLY, elbLX, elbLY, handLX, handLY);
        battery(c, handLX, handLY + 2);

        tyreLeg(c, AX - 7 + stride + p.lean * 0.5, GROUND - liftNear, ty, RUBBER, col);

        // ---- torso: a crushed hatchback seen from the front ----
        double[] bxs = {tx - 10, tx + 10, tx + 14, tx + 15, tx + 13, tx - 13, tx - 15, tx - 14};
        double[] bys = {ty - 11, ty - 11, ty - 3, ty + 9, ty + 12, ty + 12, ty + 9, ty - 3};
        c.poly(bxs, bys, PAINT[2]);
        // light from the upper left, shadow under the belly
        c.poly(new double[] {tx - 10, tx + 2, tx - 3, tx - 14}, new double[] {ty - 11, ty - 11, ty - 3, ty - 3}, PAINT[3]);
        c.poly(new double[] {tx - 14, tx - 11, tx - 13, tx - 15}, new double[] {ty - 3, ty - 3, ty + 9, ty + 9}, PAINT[3]);
        c.line(tx - 9, ty - 10, tx - 1, ty - 10, PAINT[4]);
        c.poly(new double[] {tx - 15, tx + 15, tx + 13, tx - 13}, new double[] {ty + 9, ty + 9, ty + 12, ty + 12}, PAINT[1]);
        c.poly(new double[] {tx + 11, tx + 14, tx + 15, tx + 12}, new double[] {ty - 3, ty - 3, ty + 9, ty + 9}, PAINT[1]);
        // rust patches and dents
        for (int i = 0; i < 14; i++) {
            double rx = tx - 13 + hash(i, 1) * 26, ry = ty - 2 + hash(i, 2) * 11;
            c.setOnPx(rx, ry, i % 3 == 0 ? RUST_DK : RUST);
            if (i % 2 == 0) c.setOnPx(rx + 1, ry, RUST);
        }
        c.line(tx + 6, ty - 1, tx + 10, ty + 2, DENT);
        c.line(tx - 12, ty + 5, tx - 10, ty + 7, DENT);

        // windshield chest, cracked
        c.poly(new double[] {tx - 9, tx + 9, tx + 12, tx - 12}, new double[] {ty - 9.5, ty - 9.5, ty - 3.5, ty - 3.5}, GLASS);
        c.poly(new double[] {tx + 3, tx + 9, tx + 12, tx + 6}, new double[] {ty - 9.5, ty - 9.5, ty - 3.5, ty - 3.5}, GLASS_DK);
        c.line(tx - 7, ty - 5, tx - 4, ty - 9, GLASS_HI);
        c.line(tx - 5, ty - 5, tx - 3, ty - 8, GLASS_HI);
        c.line(tx + 1, ty - 7, tx + 4, ty - 9, CRACK);
        c.line(tx + 1, ty - 7, tx + 3, ty - 4, CRACK);
        c.line(tx + 1, ty - 7, tx - 2, ty - 6, CRACK);

        // headlights: the left one is cracked and dim
        double hl = 0.5 + 0.5 * p.glow;
        c.oval(tx - 11.5, ty + 3, 2.4, 2, CHROME[2]);
        c.oval(tx - 11.5, ty + 3, 1.6, 1.3, HEADLIGHT_DIM);
        c.set(tx - 12, ty + 2, CRACK);
        c.oval(tx + 11.5, ty + 3, 2.4, 2, CHROME[2]);
        c.oval(tx + 11.5, ty + 3, 1.6, 1.3, hl > 0.6 ? HEADLIGHT : HEADLIGHT_DIM);
        c.set(tx + 11, ty + 2, HEADLIGHT_HOT);

        // grille mouth: chrome teeth, the engine glowing inside when it opens
        double open = p.jaw * 3.5;
        double gTop = ty + 1, gBot = ty + 6 + open;
        for (double y = gTop; y <= gBot; y++) for (double x = tx - 7; x <= tx + 7; x++) c.set(x, y, MOUTH);
        if (open > 0.6) {
            c.ovalA(tx, (gTop + gBot) / 2 + 0.5, 5.5, Math.max(1, open * 0.6), EMBER, 200);
            c.ovalA(tx, (gTop + gBot) / 2 + 0.5, 3, Math.max(0.8, open * 0.35), EMBER_HI, 220);
        }
        for (double x = tx - 7; x <= tx + 7; x += 2) {          // upper and lower teeth
            c.set(x, gTop, CHROME[3]);
            c.set(x, gTop + 1, CHROME[2]);
            c.set(x, gBot, CHROME[2]);
            c.set(x, gBot - 1, CHROME[1]);
        }
        c.line(tx - 8, gTop - 1, tx + 8, gTop - 1, CHROME[3]);
        // bumper and a crooked number plate
        double bumpY = Math.max(gBot + 1, ty + 9);
        c.line(tx - 14, bumpY, tx + 14, bumpY, CHROME[3]);
        c.line(tx - 13, bumpY + 1, tx + 13, bumpY + 1, CHROME[1]);
        c.polyRot(tx + 1, bumpY + 3, 0.12, new double[] {-4, 4, 4, -4}, new double[] {-1.5, -1.5, 1.5, 1.5}, PLATE);
        c.set(tx - 1, bumpY + 3, PLATE_TXT);
        c.set(tx + 1, bumpY + 3, PLATE_TXT);
        c.set(tx + 3, bumpY + 3.4, PLATE_TXT);

        // ---- tyre shoulder pads ----
        tyre(c, tx - 15, ty - 7, 4.6, 4, RUBBER);
        tyre(c, tx + 15, ty - 7, 4.6, 4, RUBBER);

        // ---- neck pole with dangling wires, and the traffic-light head ----
        double hx = tx + p.lean * 2.5 + p.headDX, hb = ty - 12 + p.headDY + col * 5;
        c.limb(tx, ty - 9, hx, hb + 1, 1.6, PIPE);
        c.line(hx - 1, hb, hx - 3, hb + 4 + Math.sin(p.t * 3) * 0.6, WIRES[0]);
        c.line(hx + 1, hb, hx + 3, hb + 3 + Math.sin(p.t * 3 + 1) * 0.6, WIRES[1]);
        double tilt = col * 0.5 + p.headDX * 0.04;
        double headTop = hb - 18;
        c.polyRot(hx, hb, tilt, new double[] {-6.5, 6.5, 6.5, -6.5}, new double[] {-16, -16, -2, -2}, BACKPLATE);
        c.polyRot(hx, hb, tilt, new double[] {4.5, 6.5, 6.5, 4.5}, new double[] {-16, -16, -2, -2}, BACKPLATE_DK);
        c.polyRot(hx, hb, tilt, new double[] {-4.5, 4.5, 4.5, -4.5}, new double[] {-18, -18, 0, 0}, HOUSING);
        c.polyRot(hx, hb, tilt, new double[] {-4.5, -3.5, -3.5, -4.5}, new double[] {-18, -18, 0, 0}, HOUSING_HI);
        // traffic cone hat, knocked askew
        c.polyRot(hx + 1, headTop, tilt - 0.25, new double[] {-4, 4, 0.6, -0.6}, new double[] {0, 0, -7, -7}, CONE);
        c.polyRot(hx + 1, headTop, tilt - 0.25, new double[] {1.5, 4, 0.6, 0.4}, new double[] {0, 0, -7, -7}, CONE_DK);
        c.polyRot(hx + 1, headTop, tilt - 0.25, new double[] {-2.6, 2.6, 2.2, -2.2}, new double[] {-2.5, -2.5, -4, -4}, CONE_STRIPE);
        c.polyRot(hx + 1, headTop, tilt - 0.25, new double[] {-5, 5, 5, -5}, new double[] {0, 0, 1, 1}, CONE_DK);

        // three lamps with visors; the lit one is its eye
        double s = Math.sin(tilt), co = Math.cos(tilt);
        double[] lampY = {-3.8, -9, -14.2};                      // green at the bottom, red on top
        int lit = p.light;
        for (int i = 0; i < 3; i++) {
            double ly = lampY[i], lx = 0;
            double cx = hx + lx * co - ly * s, cy = hb + lx * s + ly * co;
            int which = i;                                       // lampY is indexed GREEN, YELLOW, RED
            boolean on = lit == which;
            c.oval(cx, cy, 2.6, 2.4, OUT);
            c.oval(cx, cy, 2.0, 1.8, on ? LAMP_ON[which] : LAMP_DIM[which]);
            if (on) {
                c.set(cx - 0.6, cy - 0.6, LAMP_HOT[which]);
                c.set(cx + 0.4, cy - 0.6, LAMP_HOT[which]);
            }
            // visor: slanted down toward the face, an angry brow on the lit lamp
            double v = on ? 1.2 : 0.4;
            c.line(cx - 3, cy - 2.6 - v * 0.5, cx + 3, cy - 2.6 + v * 0.8, HOUSING_HI);
        }

        c.outline(OUT);

        // ---- clamp arm (front side, throws), over the torso ----
        double shRX = tx + 16, shRY = ty - 6;
        double handRX = lerp(tx + 20 + Math.sin(p.t * 1.7 + 1.4) * 0.8, tx + 12, p.armRaise);
        double handRY = lerp(ty + 9 + col * 4, ty - 26, p.armRaise);
        double elbRX = lerp(tx + 22, tx + 24, p.armRaise), elbRY = lerp(ty + 2, ty - 12, p.armRaise);
        cableArm(c, shRX, shRY, elbRX, elbRY, handRX, handRY);
        clamp(c, handRX, handRY + 1.5, p.armRaise > 0.5 ? 0.8 : 0.2 + 0.15 * Math.sin(p.t * 4));

        // ---- glows, painted after the outline so they stay soft ----
        if (lit != LIGHT_OFF) {
            int which = lit;
            double ly = lampY[which];
            double cx = hx - ly * s, cy = hb + ly * co;
            c.ovalA(cx, cy, 6 + 2 * p.glow, 5 + 2 * p.glow, LAMP_ON[which], (int) (70 + 80 * p.glow));
        }
        if (hl > 0.6) c.ovalA(tx + 12, ty + 3.5, 4, 3, HEADLIGHT, 70);
        if (p.rev > 0.02) {                                      // exhaust flames out of both pipes
            for (int side = -1; side <= 1; side += 2) {
                double px = tx + side * 11.5, top = ty - 25 + (side > 0 ? 1 : 0);
                double len = 3 + 4 * p.rev + Math.sin(p.t * 40 + side) * 1.2 * p.rev;
                c.ovalA(px, top - len * 0.5, 2.6, len * 0.6, FLAME, (int) (220 * p.rev));
                c.ovalA(px, top - len * 0.35, 1.4, len * 0.35, FLAME_HI, (int) (240 * p.rev));
            }
        }
    }

    /** Two stacked tyres with a cable thigh up to the torso. {@code col} squashes them as it falls apart. */
    private static void tyreLeg(PixelCanvas c, double x, double footY, double ty, int[] ramp, double col) {
        double sq = 1 - col * 0.45;
        c.limb(x, ty + 8, x, footY - 9 * sq, 2.4, CABLE);
        tyre(c, x, footY - 9 * sq, 5, 3.4 * sq, ramp);
        tyre(c, x, footY - 3.4 * sq, 5.5, 3.6 * sq, ramp);
    }

    private static void tyre(PixelCanvas c, double cx, double cy, double rx, double ry, int[] ramp) {
        c.ball(cx, cy, rx, ry, ramp, 1);
        c.oval(cx, cy, rx * 0.45, ry * 0.45, HUB_DK);
        c.oval(cx - 0.3, cy - 0.3, rx * 0.28, ry * 0.28, HUB);
        for (int i = 0; i < 6; i++) {                            // tread marks round the rim
            double a = i * Math.PI / 3 + 0.3;
            c.setOnPx(cx + Math.cos(a) * (rx - 1.2), cy + Math.sin(a) * (ry - 1), ramp[1]);
        }
    }

    /** A thick bundle of black cable with coloured wires twisting along it. */
    private static void cableArm(PixelCanvas c, double sx, double sy, double ex, double ey, double hx, double hy) {
        c.limb(sx, sy, ex, ey, 2.4, CABLE);
        c.limb(ex, ey, hx, hy, 2.2, CABLE);
        for (int w = 0; w < 3; w++) {
            int rgb = WIRES[w];
            for (int i = 0; i <= 8; i++) {
                double k = i / 8.0, wob = Math.sin(k * Math.PI * 3 + w * 2.1) * 1.2;
                double ax = lerp(sx, ex, k), ay = lerp(sy, ey, k);
                c.setOnPx(ax + wob, ay - wob * 0.4, rgb);
                double bx = lerp(ex, hx, k), by = lerp(ey, hy, k);
                c.setOnPx(bx - wob, by + wob * 0.4, rgb);
            }
        }
    }

    /** The left fist: a leaking car battery. */
    private static void battery(PixelCanvas c, double cx, double cy) {
        for (double y = cy - 3; y <= cy + 3; y++) for (double x = cx - 4; x <= cx + 4; x++) c.set(x, y, BATTERY);
        c.line(cx - 4, cy - 3, cx + 4, cy - 3, BATTERY_TOP);
        c.line(cx - 3, cy, cx + 3, cy, LABEL);
        c.set(cx - 3, cy - 4, TERM_RED);
        c.set(cx - 2, cy - 4, TERM_RED);
        c.set(cx + 2, cy - 4, TERM_BLK);
        c.set(cx + 3, cy - 4, TERM_BLK);
        c.set(cx + 1, cy + 4, 0x9AD04A);                          // acid drip
    }

    /** The right fist: a red jumper-cable clamp, jaws {@code open} 0..1. */
    private static void clamp(PixelCanvas c, double cx, double cy, double open) {
        double o = open * 2.5;
        c.capsule(cx - 2, cy - 1, cx + 4, cy - 1 - o, 1.4, CLAMP_DK);
        c.capsule(cx - 2, cy - 1, cx + 4, cy - 1.4 - o, 0.8, CLAMP);
        c.set(cx + 1, cy - 2 - o * 0.6, CLAMP_HI);
        c.capsule(cx - 2, cy + 1, cx + 4, cy + 1 + o, 1.4, CLAMP_DK);
        c.capsule(cx - 2, cy + 1, cx + 4, cy + 1 + o, 0.8, CLAMP);
        c.set(cx + 5, cy - 1 - o, CHROME[3]);                      // copper teeth
        c.set(cx + 5, cy + 1 + o, CHROME[3]);
        c.oval(cx - 2, cy, 1.6, 1.6, CHROME[2]);
    }

    /** Stable pseudo-random 0..1 for decoration placement. */
    private static double hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }
}
