package com.ecoquest.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.DataBufferInt;
import java.awt.image.Kernel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Fully procedural, seamlessly looping 2.5D pixel-art backdrop: "the healing wave".
 *
 * <p>A side-scrolling panorama drifts left to right across a wavy restoration
 * front. Everything left of the front is drawn polluted (smog, factories,
 * smoking chimneys, a gas flare, murky water full of trash, littered asphalt);
 * everything right of it is drawn restored (blue sky, light shafts, wind
 * turbines, solar farms, cottages, blossom trees, clean water with ducks and
 * fish, flower meadows). As the scenery scrolls through the front it "heals".
 *
 * <p>2.5D depth cues: eight parallax planes, a slowly swaying camera that
 * moves near planes more than far ones, atmospheric haze on distant planes,
 * box/cylinder shading with cast shadows, perspective-scaled ground detail,
 * live water reflections and a blurred (depth-of-field) foreground.
 *
 * <p>No image assets are needed. The scene is painted once at construction
 * into tileable strips at an internal resolution of 640x360, then composited
 * each frame. Every motion is periodic in {@link #LOOP_SECONDS}, so the
 * animation loops perfectly. Same API as {@link AnimatedBackground}.
 */
public final class EcoLoopBackground {

    /** Length of one perfect loop, in seconds. */
    public static final double LOOP_SECONDS = 180.0;

    private static final int W = 640;          // internal pixel-art width
    private static final int H = 360;          // internal pixel-art height
    private static final int S = 2;            // frame buffer scale (smooth sub-pixel motion)
    private static final int BW = W * S;
    private static final int BH = H * S;
    private static final int SW = 1280;        // parallax strip width (tiles horizontally)
    private static final double T = LOOP_SECONDS;
    private static final double TAU = Math.PI * 2;

    private static final int WATER_TOP = 268;
    private static final int WATER_BOT = 306;

    // Depth of each plane: how strongly it follows the camera sway.
    private static final double D_CLOUD = 0.08;
    private static final double D_FAR = 0.15;
    private static final double D_FAR2 = 0.30;
    private static final double D_MID = 0.50;
    private static final double D_WATER = 0.62;
    private static final double D_NEAR = 0.85;
    private static final double D_FG = 1.20;

    // Strip widths each plane scrolls per loop (integers keep the loop seamless).
    private static final int SH_CLOUD = 2;
    private static final int SH_FAR = 1;
    private static final int SH_FAR2 = 2;
    private static final int SH_MID = 3;
    private static final int SH_WATER = 4;
    private static final int SH_NEAR = 5;
    private static final int SH_FG = 7;

    private static final Color HAZE_CLEAN = new Color(196, 228, 250);
    private static final Color HAZE_DIRTY = new Color(178, 156, 112);

    private static final int TILE = 32;        // strip tile width (buffer px) for empty-space skipping
    private static final Color GRADE_DIRTY = new Color(120, 96, 50, 34);

    /**
     * Premultiplied ARGB pixels composited by hand. Blitting BufferedImages through
     * Java2D degrades badly on Windows once Direct3D starts caching the source images,
     * so every per-frame image copy in this class goes through {@link #blend} instead.
     */
    private static class Pixels {
        final int[] px;
        final int w;
        final int h;

        Pixels(BufferedImage img) {
            w = img.getWidth();
            h = img.getHeight();
            px = new int[w * h];
            BufferedImage pre = img;
            if (img.getType() != BufferedImage.TYPE_INT_ARGB_PRE) {
                pre = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D g = pre.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
            }
            pre.getRaster().getDataElements(0, 0, w, h, px);
        }
    }

    /** A frame-buffer-scaled horizontal band of one parallax plane. */
    private static final class Strip extends Pixels {
        final int y0;
        final double depth;
        final int shift;
        final int[] tileTop; // first non-empty row per tile, -1 if the tile is empty
        final int[] tileBot; // one past the last non-empty row per tile

        Strip(BufferedImage img, int y0, double depth, int shift) {
            super(img);
            this.y0 = y0;
            this.depth = depth;
            this.shift = shift;
            int tiles = w / TILE;
            tileTop = new int[tiles];
            tileBot = new int[tiles];
            Arrays.fill(tileTop, -1);
            for (int y = 0; y < h; y++) {
                for (int ti = 0; ti < tiles; ti++) {
                    for (int x = ti * TILE; x < (ti + 1) * TILE; x++) {
                        if ((px[y * w + x] >>> 24) != 0) {
                            if (tileTop[ti] < 0) {
                                tileTop[ti] = y;
                            }
                            tileBot[ti] = y + 1;
                            break;
                        }
                    }
                }
            }
        }
    }

    private final int[] skyClean;
    private final int[] skyDirty;
    private final byte[] vignetteA;          // darkening alpha per frame-buffer pixel
    private final int[][] vignetteSpans;     // per row: {x0, x1, x2, x3} ranges with alpha > 0
    private final Pixels sunSprite;
    private final Pixels smogSunSprite;
    private final Pixels[] puffSprites = new Pixels[16]; // smoke puff by age
    private final Pixels[] wispSprites = new Pixels[7];
    private int clipX0, clipX1;
    private int[] dst;                       // pixels of the buffer being drawn this pass

    private final Strip cloudsClean, cloudsDirty;
    private final Strip farClean, farDirty;
    private final Strip far2Clean, far2Dirty;
    private final Strip midClean, midDirty;
    private final Strip waterBaseClean, waterBaseDirty;
    private final Strip waterTopClean, waterTopDirty;
    private final Strip nearClean, nearDirty;
    private final Strip fgClean, fgDirty;

    private final List<double[]> chimneys = new ArrayList<>(); // {x, topY} far2 coords
    private final List<double[]> flares = new ArrayList<>();   // {x, topY} far2 coords
    private final List<double[]> turbines = new ArrayList<>(); // {hubX, hubY, len, revs} far2 coords
    private final List<double[]> panels = new ArrayList<>();   // {x, y} far2 coords
    private final List<double[]> pipes = new ArrayList<>();    // {x, y} mid coords (sludge outflows)
    private final List<double[]> trash = new ArrayList<>();    // {x, y, type} water coords

    private final BufferedImage frame = noAccel(new BufferedImage(BW, BH, BufferedImage.TYPE_INT_RGB));
    private final BufferedImage cleanBuf = noAccel(new BufferedImage(BW, BH, BufferedImage.TYPE_INT_RGB));
    private final int[] framePx = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
    private final int[] cleanPx = ((DataBufferInt) cleanBuf.getRaster().getDataBuffer()).getData();
    /**
     * Copy of the frame that Java2D is allowed to track. {@code frame} and {@code cleanBuf}
     * expose their pixel arrays, which makes them untrackable: drawing them directly builds
     * new surface data every call and slows down over time, so only this image is drawn.
     */
    private final BufferedImage present = new BufferedImage(BW, BH, BufferedImage.TYPE_INT_RGB);
    private final long startNanos = System.nanoTime();

    // per-frame state
    private double t;
    private double cam;

    public EcoLoopBackground() {
        skyClean = new Pixels(makeSky(new Color(48, 122, 220), new Color(110, 178, 242), new Color(204, 234, 252), null)).px;
        skyDirty = new Pixels(makeSky(new Color(64, 60, 58), new Color(118, 104, 84), new Color(192, 164, 112), GRADE_DIRTY)).px;
        Pixels vig = new Pixels(makeVignette());
        vignetteA = new byte[BW * BH];
        vignetteSpans = new int[BH][];
        for (int y = 0; y < BH; y++) {
            int first = -1, last = -1;
            for (int x = 0; x < BW; x++) {
                int a = vig.px[y * BW + x] >>> 24;
                vignetteA[y * BW + x] = (byte) a;
            }
            // alpha grows outward from the centre, so each row is clear in one middle span
            int x = 0;
            while (x < BW && vignetteA[y * BW + x] != 0) {
                x++;
            }
            first = x;
            x = BW - 1;
            while (x >= 0 && vignetteA[y * BW + x] != 0) {
                x--;
            }
            last = x + 1;
            vignetteSpans[y] = first >= last ? new int[] {0, BW, BW, BW} : new int[] {0, first, last, BW};
        }
        sunSprite = new Pixels(makeSunSprite());
        smogSunSprite = new Pixels(makeSmogSunSprite());
        for (int i = 0; i < puffSprites.length; i++) {
            double a = i / (double) (puffSprites.length - 1);
            puffSprites[i] = new Pixels(makePuff(3 + a * 17, 88 + (int) (a * 34)));
        }
        for (int i = 0; i < wispSprites.length; i++) {
            wispSprites[i] = new Pixels(makeWisp(60 + hash(i + 8020) * 70));
        }

        cloudsClean = strip(0, 150, D_CLOUD, SH_CLOUD, this::paintCloudsClean, null, false, false);
        cloudsDirty = strip(0, 150, D_CLOUD, SH_CLOUD, this::paintCloudsDirty, null, false, true);
        farClean = strip(110, 262, D_FAR, SH_FAR, this::paintFarClean, alpha(HAZE_CLEAN, 80), false, false);
        farDirty = strip(80, 262, D_FAR, SH_FAR, this::paintFarDirty, alpha(HAZE_DIRTY, 130), false, true);
        far2Clean = strip(60, 272, D_FAR2, SH_FAR2, this::paintFar2Clean, alpha(HAZE_CLEAN, 45), false, false);
        far2Dirty = strip(40, 272, D_FAR2, SH_FAR2, this::paintFar2Dirty, alpha(HAZE_DIRTY, 60), false, true);
        midClean = strip(160, 276, D_MID, SH_MID, this::paintMidClean, null, false, false);
        midDirty = strip(160, 276, D_MID, SH_MID, this::paintMidDirty, null, false, true);
        waterBaseClean = strip(WATER_TOP, WATER_BOT + 6, D_WATER, SH_WATER, this::paintWaterBaseClean, null, false, false);
        waterBaseDirty = strip(WATER_TOP, WATER_BOT + 6, D_WATER, SH_WATER, this::paintWaterBaseDirty, null, false, true);
        waterTopClean = strip(WATER_TOP - 14, WATER_BOT + 6, D_WATER, SH_WATER, this::paintWaterTopClean, null, false, false);
        waterTopDirty = strip(WATER_TOP - 4, WATER_BOT + 6, D_WATER, SH_WATER, this::paintWaterTopDirty, null, false, true);
        nearClean = strip(294, H + 12, D_NEAR, SH_NEAR, this::paintNearClean, null, false, false);
        nearDirty = strip(294, H + 12, D_NEAR, SH_NEAR, this::paintNearDirty, null, false, true);
        fgClean = strip(290, H + 16, D_FG, SH_FG, this::paintFgClean, null, true, false);
        fgDirty = strip(290, H + 16, D_FG, SH_FG, this::paintFgDirty, null, true, true);
    }

    /** Always true: the scene is procedural and needs no files. */
    public boolean isLoaded() {
        return true;
    }

    /** Draws the current animation frame scaled to cover {@code w x h}. */
    public void render(Graphics2D target, int w, int h) {
        double now = ((System.nanoTime() - startNanos) / 1e9) % T;
        renderFrame(now);
        double scale = Math.max((double) w / W, (double) h / H);
        int dw = (int) Math.ceil(W * scale);
        int dh = (int) Math.ceil(H * scale);
        Graphics2D g = (Graphics2D) target.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        present.getRaster().setDataElements(0, 0, BW, BH, framePx);
        g.drawImage(present, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        g.dispose();
    }

    /** Renders the frame at loop time {@code time} (seconds) into the internal buffer. */
    void renderFrame(double time) {
        this.t = time;
        this.cam = 5.0 * Math.sin(TAU * 2 * time / T) + 2.0 * Math.sin(TAU * 5 * time / T + 0.7);

        double minF = Double.MAX_VALUE, maxF = -Double.MAX_VALUE;
        for (int y = 0; y <= H; y += 2) {
            double fx = frontX(y, time);
            minF = Math.min(minF, fx);
            maxF = Math.max(maxF, fx);
        }

        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        clipX0 = 0;
        clipX1 = Math.min(BW, (int) Math.ceil((maxF + 3) * S));
        g.setClip(clipX0, 0, clipX1 - clipX0, BH);
        dst = framePx;
        drawWorld(g, false);
        g.dispose();

        Graphics2D c = cleanBuf.createGraphics();
        c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        clipX0 = Math.max(0, (int) Math.floor((minF - 3) * S));
        clipX1 = BW;
        c.setClip(clipX0, 0, clipX1 - clipX0, BH);
        dst = cleanPx;
        drawWorld(c, true);
        c.dispose();

        // splice the restored world in to the right of the wavy front
        for (int yb = 0; yb < BH; yb++) {
            int fx = (int) Math.round(frontX((yb + 0.5) / S, time) * S);
            fx = Math.max(0, Math.min(BW, fx));
            System.arraycopy(cleanPx, yb * BW + fx, framePx, yb * BW + fx, BW - fx);
        }

        g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        drawFront(g);
        g.dispose();
        applyVignette();
    }

    /** Exposes the raw frame for offline previews. */
    BufferedImage frameBuffer() {
        return frame;
    }

    // =====================================================================
    // Frame composition
    // =====================================================================

    private void drawWorld(Graphics2D g, boolean clean) {
        if (clean) {
            copySky(skyClean);
            drawSun(g);
            drawStrip(g, cloudsClean);
            drawStrip(g, farClean);
            drawStrip(g, far2Clean);
            drawPanelGlints(g);
            drawTurbines(g);
            drawGodRays(g);
            drawBirds(g);
            drawStrip(g, midClean);
            drawWater(g, waterBaseClean, waterTopClean, midClean, 0.45f);
            drawDucks(g);
            drawFish(g);
            drawWaterSparkles(g);
            drawDragonflies(g);
            drawStrip(g, nearClean);
            drawButterflies(g);
            drawStrip(g, fgClean);
            drawPetals(g);
            drawPollen(g);
        } else {
            copySky(skyDirty);
            drawSmogSun(g);
            drawStrip(g, cloudsDirty);
            drawStrip(g, farDirty);
            drawStrip(g, far2Dirty);
            drawFlares(g);
            drawSmoke(g);
            drawStrip(g, midDirty);
            drawWater(g, waterBaseDirty, waterTopDirty, midDirty, 0.25f);
            drawSludge(g);
            drawFloatingTrash(g);
            drawToxicBubbles(g);
            drawStrip(g, nearDirty);
            drawStrip(g, fgDirty);
            drawAsh(g);
            drawSmogWisps(g);
        }
    }

    private int stripOx(int shift) {
        int full = SW * S;
        int ox = (int) Math.round(t / T * shift * SW * S) % full;
        return ox < 0 ? ox + full : ox;
    }

    private void drawStrip(Graphics2D g, Strip s) {
        int full = SW * S;
        int ox = stripOx(s.shift);
        int y = (int) Math.round((s.y0 + cam * s.depth) * S);
        for (int copy = 0; copy < 2; copy++) {
            int base = copy == 0 ? ox - full : ox;
            for (int ti = 0; ti < s.tileTop.length; ti++) {
                int sx = ti * TILE;
                int dx = base + sx;
                int top = s.tileTop[ti];
                if (top < 0 || dx + TILE <= clipX0 || dx >= clipX1) {
                    continue;
                }
                int x0 = Math.max(dx, clipX0), x1 = Math.min(dx + TILE, clipX1);
                for (int r = top; r < s.tileBot[ti]; r++) {
                    blendRow(s.px, r * s.w + (x0 - base), y + r, x0, x1, 256);
                }
            }
        }
    }

    /** Water: base, a rippling mirror image of the far bank, then surface detail. */
    private void drawWater(Graphics2D g, Strip base, Strip top, Strip mirror, float reflect) {
        drawStrip(g, base);
        int full = SW * S;
        int ox = stripOx(mirror.shift);
        int wTop = (int) Math.round((WATER_TOP + cam * D_WATER) * S);
        int rows = (WATER_BOT - WATER_TOP) * S;
        int k256 = Math.round(reflect * 256);
        for (int k = 0; k < rows; k++) {
            double kin = k / (double) S;
            int srcY = (int) Math.round((WATER_TOP - 0.5 - kin * 1.3 - mirror.y0) * S);
            if (srcY < 0) {
                break;
            }
            double amp = 0.6 + kin * 0.07;
            int dx = (int) Math.round(Math.sin(TAU * (kin / 4.0 + 12 * t / T)) * amp * S);
            int yb = wTop + k;
            // source column for screen x is (x - ox - dx) mod full
            int shiftX = Math.floorMod(ox + dx, full);
            int x = clipX0;
            while (x < clipX1) {
                int sx = Math.floorMod(x - shiftX, full);
                int run = Math.min(clipX1 - x, full - sx);
                blendRow(mirror.px, srcY * mirror.w + sx, yb, x, x + run, k256);
                x += run;
            }
        }
        drawStrip(g, top);
    }

    /**
     * Blends one row of premultiplied source pixels over the opaque destination.
     * {@code src[srcIdx]} lands at ({@code x0}, {@code y}); {@code k256} is an extra opacity (256 = 1).
     */
    private void blendRow(int[] src, int srcIdx, int y, int x0, int x1, int k256) {
        if (y < 0 || y >= BH) {
            return;
        }
        int[] d = dst;
        int di = y * BW + x0;
        int si = srcIdx;
        for (int x = x0; x < x1; x++, di++, si++) {
            int p = src[si];
            int a = p >>> 24;
            if (a == 0) {
                continue;
            }
            if (k256 < 256) {
                a = (a * k256) >> 8;
                if (a == 0) {
                    continue;
                }
                p = (((p >> 16 & 255) * k256 >> 8) << 16) | (((p >> 8 & 255) * k256 >> 8) << 8) | ((p & 255) * k256 >> 8);
            } else if (a == 255) {
                d[di] = p;
                continue;
            }
            int q = d[di];
            int inv = 255 - a;
            int r = (p >> 16 & 255) + (((q >> 16 & 255) * inv * 257 + 32896) >> 16);
            int gg = (p >> 8 & 255) + (((q >> 8 & 255) * inv * 257 + 32896) >> 16);
            int b = (p & 255) + (((q & 255) * inv * 257 + 32896) >> 16);
            d[di] = 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, gg) << 8) | Math.min(255, b);
        }
    }

    /** Blends a sprite centred on the internal point (cx, cy) with extra opacity {@code k}. */
    private void blitSprite(Pixels sp, double cx, double cy, double k) {
        int k256 = (int) Math.round(Math.max(0, Math.min(1, k)) * 256);
        if (k256 == 0) {
            return;
        }
        int x = (int) Math.round(cx * S) - sp.w / 2;
        int y = (int) Math.round(cy * S) - sp.h / 2;
        int x0 = Math.max(x, clipX0), x1 = Math.min(x + sp.w, clipX1);
        if (x0 >= x1) {
            return;
        }
        for (int r = 0; r < sp.h; r++) {
            blendRow(sp.px, r * sp.w + (x0 - x), y + r, x0, x1, k256);
        }
    }

    private void copySky(int[] sky) {
        for (int y = 0; y < BH; y++) {
            System.arraycopy(sky, y * BW + clipX0, dst, y * BW + clipX0, clipX1 - clipX0);
        }
    }

    private void applyVignette() {
        int[] d = framePx;
        for (int y = 0; y < BH; y++) {
            int[] sp = vignetteSpans[y];
            for (int seg = 0; seg < 2; seg++) {
                int x0 = sp[seg * 2], x1 = sp[seg * 2 + 1];
                for (int i = y * BW + x0, e = y * BW + x1; i < e; i++) {
                    int inv = 255 - (vignetteA[i] & 255);
                    int q = d[i];
                    d[i] = 0xFF000000
                            | ((((q >> 16 & 255) * inv * 257 + 32896) >> 16) << 16)
                            | ((((q >> 8 & 255) * inv * 257 + 32896) >> 16) << 8)
                            | (((q & 255) * inv * 257 + 32896) >> 16);
                }
            }
        }
    }

    /** Screen x (internal px) of a strip-space x for a plane scrolling {@code shift} widths per loop. */
    private double scr(double stripX, int shift) {
        double x = (stripX + t / T * shift * SW) % SW;
        if (x < 0) {
            x += SW;
        }
        if (x > W + 150) {
            x -= SW;
        }
        return x;
    }

    private double oy(double depth) {
        return cam * depth;
    }

    // ----------------------------------------------------------- restoration front

    private static double frontX(double y, double t) {
        return W * 0.5
                + 20 * Math.sin(TAU * (y / 140.0 + 2 * t / T))
                + 8 * Math.sin(TAU * (y / 52.0 - 5 * t / T))
                + 16 * Math.sin(TAU * t / T);
    }

    private static final Color SEAM_OUTER = new Color(170, 255, 170, 40);
    private static final Color SEAM_MID = new Color(210, 255, 200, 80);
    private static final Color SEAM_CORE = new Color(250, 255, 240, 150);
    private static final Color FLECK_CLEAN = new Color(150, 225, 120, 190);
    private static final Color FLECK_DIRTY = new Color(110, 96, 74, 170);

    private Path2D.Double seamBand(double halfWidth) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo((frontX(0, t) - halfWidth) * S, 0);
        for (int y = 2; y <= H; y += 2) {
            p.lineTo((frontX(y, t) - halfWidth) * S, y * S);
        }
        for (int y = H; y >= 0; y -= 2) {
            p.lineTo((frontX(y, t) + halfWidth) * S, y * S);
        }
        p.closePath();
        return p;
    }

    private void drawFront(Graphics2D g) {
        g.setColor(SEAM_OUTER);
        g.fill(seamBand(3));
        g.setColor(SEAM_MID);
        g.fill(seamBand(1.5));
        g.setColor(SEAM_CORE);
        g.fill(seamBand(0.5));
        int tick = (int) Math.floor(t * 8);
        for (int y = 0; y < H; y += 2) {
            double fy = frontX(y, t);
            for (int k = -6; k <= 6; k++) {
                if (k == 0) {
                    continue;
                }
                double hsh = hash(y * 131 + k * 17 + tick * 7919);
                if (hsh < 0.16) {
                    g.setColor(k > 0 ? FLECK_CLEAN : FLECK_DIRTY);
                    rect(g, fy + k * 2.2, y, 1, 1);
                }
            }
        }
        for (int i = 0; i < 110; i++) {
            double a = frac(t / (T / 36) + hash(i));
            double y0 = hash(i + 500) * (H + 40);
            double y = y0 - a * 50;
            double x = frontX(y, t) + (hash(i + 900) - 0.5) * 26 + a * 10;
            int alpha = (int) (255 * Math.sin(Math.PI * a));
            Color c = switch (i % 5) {
                case 0 -> new Color(255, 255, 225, alpha);
                case 1 -> new Color(170, 240, 120, alpha);
                case 2 -> new Color(255, 232, 120, alpha);
                case 3 -> new Color(120, 220, 170, alpha);
                default -> new Color(255, 190, 215, alpha);
            };
            g.setColor(c);
            if (i % 7 == 0) { // tumbling leaf
                boolean flip = Math.sin(TAU * (a * 4 + hash(i + 3))) > 0;
                g.setColor(new Color(110, 200, 90, alpha));
                rect(g, x, y, flip ? 3 : 1, flip ? 1 : 3);
                rect(g, x + 1, y + (flip ? -1 : 1), 1, 1);
            } else if (i % 5 == 0) { // twinkle
                rect(g, x - 1.5, y, 4, 1);
                rect(g, x, y - 1.5, 1, 4);
            } else {
                double sz = (i % 3 == 0) ? 1.5 : 1;
                rect(g, x, y, sz, sz);
            }
        }
    }

    // ----------------------------------------------------------- sky actors (clean)

    private void drawSun(Graphics2D g) {
        double cx = 540, cy = 64 + oy(0.03);
        float pulse = (float) (0.85 + 0.15 * Math.sin(TAU * 45 * t / T));
        blitSprite(sunSprite, cx, cy, pulse);
        g.setColor(new Color(255, 240, 150, 160));
        for (int i = 0; i < 16; i++) {
            double ang = TAU * (i / 16.0 + 3 * t / T);
            int len = (i % 2 == 0) ? 12 : 7;
            for (int d = 26; d < 26 + len; d += 2) {
                rect(g, cx + Math.cos(ang) * d - 0.5, cy + Math.sin(ang) * d - 0.5, 1.5, 1.5);
            }
        }
        oval(g, cx, cy, 21, new Color(255, 214, 70));
        oval(g, cx, cy, 19, new Color(255, 228, 92));
        oval(g, cx - 4, cy - 4, 11, new Color(255, 242, 150));
        oval(g, cx - 6, cy - 6, 4, new Color(255, 252, 215));
    }

    private void drawGodRays(Graphics2D g) {
        double cx = 540 * S, cy = (64 + oy(0.03)) * S;
        for (int i = 0; i < 5; i++) {
            double base = Math.PI * 0.64 + i * 0.16 + 0.035 * Math.sin(TAU * (2 * t / T) + i * 1.3);
            double half = 0.018 + 0.016 * hash(i + 40);
            int alpha = (int) (16 + 12 * (0.5 + 0.5 * Math.sin(TAU * (3 * t / T) + i)));
            double len = 360 * S;
            Path2D.Double p = new Path2D.Double();
            p.moveTo(cx, cy);
            p.lineTo(cx + Math.cos(base - half) * len, cy + Math.sin(base - half) * len);
            p.lineTo(cx + Math.cos(base + half) * len, cy + Math.sin(base + half) * len);
            p.closePath();
            g.setColor(new Color(255, 250, 215, alpha));
            g.fill(p);
        }
    }

    private void drawBirds(Graphics2D g) {
        g.setColor(new Color(38, 48, 70));
        for (int flock = 0; flock < 2; flock++) {
            double a = frac(t / (T / 4) + flock * 0.5);
            double bx = -60 + (W + 120) * a;
            double by = 56 + flock * 34 + Math.sin(TAU * (8 * t / T + flock)) * 6 + oy(0.1);
            for (int i = 0; i < 7; i++) {
                int row = (i + 1) / 2;
                int side = (i % 2 == 0) ? 1 : -1;
                double x = bx - row * 10;
                double y = by + side * row * 6;
                double flap = Math.sin(TAU * (360 * t / T + i * 0.3));
                double wy = flap > 0 ? -2 : 1;
                rect(g, x, y, 1.5, 1);
                rect(g, x - 2.5, y + wy * 0.5, 2.5, 1);
                rect(g, x + 1.5, y + wy * 0.5, 2.5, 1);
                rect(g, x - 4, y + wy, 1.5, 1);
                rect(g, x + 4, y + wy, 1.5, 1);
            }
        }
    }

    private void drawTurbines(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.scale(S, S);
        double dy = oy(D_FAR2);
        for (double[] tb : turbines) {
            double x = scr(tb[0], SH_FAR2);
            if (x < -60 || x > W + 60) {
                continue;
            }
            double y = tb[1] + dy, len = tb[2];
            double base = TAU * tb[3] * t / T;
            for (int b = 0; b < 3; b++) {
                double ang = base + b * TAU / 3;
                double ca = Math.cos(ang), sa = Math.sin(ang);
                double nx = -sa, ny = ca;
                double w0 = Math.max(1.2, len * 0.075);
                Path2D.Double blade = new Path2D.Double();
                blade.moveTo(x + nx * w0, y + ny * w0);
                blade.lineTo(x + ca * len * 0.35 + nx * w0 * 1.2, y + sa * len * 0.35 + ny * w0 * 1.2);
                blade.lineTo(x + ca * len, y + sa * len);
                blade.lineTo(x - nx * w0 * 0.3, y - ny * w0 * 0.3);
                blade.closePath();
                g2.setColor(new Color(250, 252, 252));
                g2.fill(blade);
                Path2D.Double edge = new Path2D.Double();
                edge.moveTo(x - nx * w0 * 0.3, y - ny * w0 * 0.3);
                edge.lineTo(x + ca * len, y + sa * len);
                edge.lineTo(x + ca * len * 0.5 - nx * w0 * 0.1, y + sa * len * 0.5 - ny * w0 * 0.1);
                edge.closePath();
                g2.setColor(new Color(196, 206, 220));
                g2.fill(edge);
            }
            g2.setColor(new Color(214, 220, 228));
            g2.fillOval((int) Math.round(x - 1.5), (int) Math.round(y - 1.5), 3, 3);
            g2.setColor(new Color(255, 255, 255));
            g2.fillRect((int) Math.round(x - 1), (int) Math.round(y - 1), 1, 1);
        }
        g2.dispose();
    }

    private void drawPanelGlints(Graphics2D g) {
        double dy = oy(D_FAR2);
        for (double[] p : panels) {
            double x = scr(p[0], SH_FAR2);
            if (x < -20 || x > W + 20) {
                continue;
            }
            double ph = frac(6 * t / T + p[0] / SW * 3);
            if (ph < 0.05) {
                double k = ph / 0.05;
                g.setColor(new Color(255, 255, 255, (int) (220 * Math.sin(Math.PI * k))));
                double gx = x + k * 14;
                double y = p[1] + dy;
                rect(g, gx + 2, y, 2, 1);
                rect(g, gx + 1, y + 1, 2, 1);
                rect(g, gx, y + 2, 2, 1);
                rect(g, gx - 1, y + 3, 2, 1);
            }
        }
    }

    // ----------------------------------------------------------- water actors (clean)

    private void drawDucks(Graphics2D g) {
        double dy = oy(D_WATER);
        for (int i = 0; i < 3; i++) {
            double baseX = 380 + i * 70 + 30 * Math.sin(TAU * (t / T * 2 + i * 0.3));
            double x = baseX;
            double y = WATER_TOP + 12 + i * 7 + dy + Math.sin(TAU * (30 * t / T + i)) * 0.6;
            boolean right = Math.cos(TAU * (t / T * 2 + i * 0.3)) > 0;
            int dir = right ? 1 : -1;
            // wake
            g.setColor(new Color(220, 242, 255, 140));
            for (int w = 1; w <= 3; w++) {
                rect(g, x - dir * (6 + w * 4) - w, y + 2 + w * 0.6, 2 + w, 0.5);
            }
            // reflection
            g.setColor(new Color(40, 90, 150, 110));
            rect(g, x - 4, y + 3, 9, 2);
            // body
            g.setColor(new Color(132, 98, 70));
            rect(g, x - 4, y - 1, 9, 3);
            g.setColor(new Color(224, 218, 206));
            rect(g, x - dir * 2 - 1, y - 1, 3, 1);
            g.setColor(new Color(90, 70, 52));
            rect(g, x - dir * 4 - (right ? 1 : 0), y - 2, 2, 2);
            // head
            g.setColor(new Color(38, 120, 70));
            rect(g, x + dir * 3 - 1, y - 4, 3, 3);
            g.setColor(new Color(250, 250, 250));
            rect(g, x + dir * 3 - 1, y - 1.5, 3, 0.5);
            g.setColor(new Color(240, 190, 60));
            rect(g, x + dir * 5 - (right ? 0 : 1), y - 3, 2, 1);
        }
    }

    private void drawFish(Graphics2D g) {
        double dy = oy(D_WATER);
        for (int i = 0; i < 5; i++) {
            double cyc = T / (14 + i * 3);
            double a = frac(t / cyc + hash(i + 80));
            double fx = 400 + i * 46 + hash(i + 90) * 18;
            double waterY = WATER_TOP + 9 + (i % 3) * 9 + dy;
            if (a < 0.12) {
                double k = a / 0.12;
                double x = fx + (k - 0.5) * 22;
                double y = waterY - Math.sin(Math.PI * k) * 18;
                g.setColor(new Color(250, 140, 70));
                rect(g, x - 3, y - 1, 6, 3);
                g.setColor(new Color(255, 206, 130));
                rect(g, x - 2, y - 1, 3, 1);
                g.setColor(new Color(220, 100, 56));
                rect(g, x - 2, y + 1, 4, 1);
                double tail = k < 0.5 ? -4.5 : 3;
                rect(g, x + tail, y - 2, 1.5, 4);
                g.setColor(new Color(30, 30, 30));
                rect(g, x + (k < 0.5 ? 1.5 : -2), y - 0.5, 0.5, 0.5);
            } else if (a < 0.26) {
                double k = (a - 0.12) / 0.14;
                int alpha = (int) (220 * (1 - k));
                g.setColor(new Color(232, 248, 255, alpha));
                double rr = 3 + k * 12;
                rect(g, fx + 11 - rr, waterY, rr * 2, 1);
                rect(g, fx + 11 - rr * 0.6, waterY + 1.5, rr * 1.2, 0.5);
                rect(g, fx - 11 - rr * 0.6, waterY + 1, rr * 1.2, 0.5);
                if (k < 0.45) {
                    rect(g, fx + 9, waterY - 3 - k * 8, 1, 1);
                    rect(g, fx + 13, waterY - 2 - k * 9, 1, 1);
                    rect(g, fx + 11, waterY - 4 - k * 10, 1, 1);
                }
            }
        }
    }

    private void drawWaterSparkles(Graphics2D g) {
        double dy = oy(D_WATER);
        for (int i = 0; i < 60; i++) {
            double ph = t / (T / 60) + hash(i + 1200);
            double a = frac(ph);
            if (a > 0.5) {
                continue;
            }
            int gen = Math.floorMod((int) Math.floor(ph), 60);
            double x = W * 0.3 + hash(i * 61 + gen + 1210) * W * 0.7;
            double y = WATER_TOP + 3 + hash(i + 1220) * (WATER_BOT - WATER_TOP - 6) + dy;
            int alpha = (int) (240 * Math.sin(Math.PI * a * 2));
            g.setColor(new Color(255, 255, 255, alpha));
            rect(g, x, y, 2, 0.5);
            if (i % 4 == 0) {
                rect(g, x + 0.5, y - 1, 1, 2.5);
            }
        }
    }

    private void drawDragonflies(Graphics2D g) {
        for (int i = 0; i < 2; i++) {
            double cx = 430 + i * 110;
            double x = cx + 40 * Math.sin(TAU * (3 * t / T + i * 0.4)) + 8 * Math.sin(TAU * (23 * t / T + i));
            double y = WATER_TOP - 8 + 6 * Math.sin(TAU * (5 * t / T + i * 0.7)) + 3 * Math.sin(TAU * (31 * t / T)) + oy(D_WATER);
            boolean up = Math.sin(TAU * (900 * t / T + i)) > 0;
            g.setColor(new Color(220, 240, 255, 150));
            rect(g, x - 3, y + (up ? -1.5 : 0), 3, 1);
            rect(g, x + 1, y + (up ? -1.5 : 0), 3, 1);
            g.setColor(new Color(40, 150, 170));
            rect(g, x - 1, y, 6, 1);
            g.setColor(new Color(30, 90, 120));
            rect(g, x - 1.5, y - 0.5, 1.5, 1.5);
        }
    }

    private void drawButterflies(Graphics2D g) {
        Color[] cols = {new Color(255, 210, 60), new Color(250, 250, 255), new Color(130, 190, 255),
            new Color(255, 150, 90), new Color(230, 140, 255)};
        double dy = oy(D_NEAR);
        for (int i = 0; i < 5; i++) {
            double cx = 380 + i * 52;
            double cy = 318 + (i % 2) * 14 + dy;
            double x = cx + 32 * Math.sin(TAU * ((2 + i % 2) * t / T + hash(i + 50)));
            double y = cy + 10 * Math.sin(TAU * ((4 + 2 * (i % 2)) * t / T + hash(i + 60)));
            boolean open = Math.sin(TAU * (540 * t / T + i * 0.25)) > 0;
            g.setColor(new Color(0, 40, 0, 50));
            rect(g, x - 2, cy + 14, 5, 1); // shadow on the grass
            g.setColor(cols[i]);
            if (open) {
                rect(g, x - 3, y - 2, 2.5, 2.5);
                rect(g, x + 1, y - 2, 2.5, 2.5);
                rect(g, x - 2, y + 0.5, 1.5, 1.5);
                rect(g, x + 1, y + 0.5, 1.5, 1.5);
            } else {
                rect(g, x - 1, y - 3, 1, 3);
                rect(g, x + 1, y - 3, 1, 3);
            }
            g.setColor(new Color(50, 40, 40));
            rect(g, x, y - 2, 1, 3.5);
        }
    }

    private void drawPetals(Graphics2D g) {
        for (int i = 0; i < 48; i++) {
            double a = frac(t / (T / 12) + hash(i + 300));
            double x = W * 0.3 + hash(i + 310) * W * 0.7 + a * 60 + Math.sin(TAU * (a * 3 + hash(i + 320))) * 8;
            double y = 120 + a * 250 - 14 * hash(i + 330);
            boolean flip = Math.sin(TAU * (a * 7 + hash(i))) > 0;
            g.setColor(i % 3 == 0 ? new Color(255, 238, 245) : new Color(250, 168, 200));
            rect(g, x, y, flip ? 2 : 1, flip ? 1 : 2);
        }
    }

    private void drawPollen(Graphics2D g) {
        for (int i = 0; i < 40; i++) {
            double a = frac(t / (T / 6) + hash(i + 4300));
            double x = W * 0.35 + hash(i + 4310) * W * 0.65 + 14 * Math.sin(TAU * (a * 2 + hash(i + 4320)));
            double y = 150 + hash(i + 4330) * 190 - a * 30;
            int alpha = (int) (200 * Math.sin(Math.PI * a));
            g.setColor(new Color(255, 250, 200, alpha / 3));
            rect(g, x - 1, y - 1, 3, 3);
            g.setColor(new Color(255, 255, 230, alpha));
            rect(g, x, y, 1, 1);
        }
    }

    // ----------------------------------------------------------- polluted actors

    private void drawSmogSun(Graphics2D g) {
        double cx = 130, cy = 72 + oy(0.03);
        float pulse = (float) (0.85 + 0.15 * Math.sin(TAU * 12 * t / T));
        blitSprite(smogSunSprite, cx, cy, pulse);
    }

    private void drawSmoke(Graphics2D g) {
        double dy = oy(D_FAR2);
        for (int c = 0; c < chimneys.size(); c++) {
            double[] ch = chimneys.get(c);
            double baseX = scr(ch[0], SH_FAR2);
            if (baseX < -100 || baseX > W + 20) {
                continue;
            }
            double top = ch[1] + dy;
            if (frac(t / (T / 90) + hash(c) * 0.5) < 0.5) {
                g.setColor(new Color(255, 60, 40));
                rect(g, baseX - 0.5, top - 3, 1.5, 1.5);
                g.setColor(new Color(255, 80, 60, 70));
                rect(g, baseX - 2, top - 4.5, 4.5, 4.5);
            }
            int puffs = 12;
            for (int j = puffs - 1; j >= 0; j--) {
                double a = frac(t / (T / 45) + (double) j / puffs + hash(c + 40));
                double x = baseX + a * 50 + Math.sin(TAU * (a * 1.5 + hash(c))) * 4;
                double y = top - 3 - a * 80;
                float alpha = (float) (0.82 * (1 - a) * Math.min(1, a * 6 + 0.3));
                if (alpha <= 0.01f) {
                    continue;
                }
                blitSprite(puffSprites[(int) Math.round(a * (puffSprites.length - 1))], x, y, alpha);
            }
        }
    }

    private void drawFlares(Graphics2D g) {
        double dy = oy(D_FAR2);
        int tick = (int) Math.floor(t * 12);
        for (int i = 0; i < flares.size(); i++) {
            double[] f = flares.get(i);
            double x = scr(f[0], SH_FAR2);
            if (x < -20 || x > W + 20) {
                continue;
            }
            double y = f[1] + dy;
            double h = 9 + 3 * Math.sin(TAU * 60 * t / T) + hash(tick * 13 + i) * 4;
            oval(g, x, y - h * 0.5, h * 0.9, new Color(255, 140, 40, 40));
            g.setColor(new Color(240, 90, 30));
            rect(g, x - 2, y - h * 0.7, 4, h * 0.7);
            g.setColor(new Color(255, 170, 50));
            rect(g, x - 1.5, y - h * 0.55, 3, h * 0.55);
            rect(g, x - 0.5 + (hash(tick + i * 7) - 0.5) * 2, y - h, 1, h * 0.4);
            g.setColor(new Color(255, 240, 160));
            rect(g, x - 0.5, y - h * 0.35, 1, h * 0.35);
        }
    }

    private void drawSludge(Graphics2D g) {
        double dy = oy(D_MID);
        for (int i = 0; i < pipes.size(); i++) {
            double[] p = pipes.get(i);
            double x = scr(p[0], SH_MID);
            if (x < -20 || x > W + 20) {
                continue;
            }
            double y = p[1] + dy;
            double bottom = WATER_TOP + oy(D_WATER) + 1;
            g.setColor(new Color(118, 128, 52));
            rect(g, x - 1, y, 3, bottom - y);
            g.setColor(new Color(150, 160, 70));
            rect(g, x - 0.5, y, 1, bottom - y);
            for (int k = 0; k < 3; k++) { // falling globs
                double a = frac(t / (T / 180) + k / 3.0 + hash(i + 70));
                g.setColor(new Color(96, 108, 40));
                rect(g, x - 1.5, y + a * (bottom - y), 4, 1.5);
            }
            double pulse = 1 + 0.3 * Math.sin(TAU * 40 * t / T + i);
            g.setColor(new Color(140, 140, 80, 170));
            rect(g, x - 6 * pulse, bottom, 13 * pulse, 1.5);
            g.setColor(new Color(170, 165, 110, 140));
            rect(g, x - 3 * pulse, bottom + 1.5, 7 * pulse, 1);
        }
    }

    private void drawFloatingTrash(Graphics2D g) {
        double dy = oy(D_WATER);
        for (int i = 0; i < trash.size(); i++) {
            double[] tr = trash.get(i);
            double x = scr(tr[0], SH_WATER);
            if (x < -12 || x > W + 12) {
                continue;
            }
            double y = tr[1] + dy + Math.sin(TAU * (12 * t / T + hash(i + 2000))) * 0.8;
            g.setColor(new Color(150, 110, 170, 60));
            rect(g, x - 3, y + 2, 14, 1);
            switch ((int) tr[2]) {
                case 0 -> { // bottle
                    g.setColor(new Color(140, 196, 160));
                    rect(g, x, y, 7, 2.5);
                    g.setColor(new Color(200, 236, 210));
                    rect(g, x + 1, y, 4, 0.5);
                    g.setColor(new Color(60, 110, 200));
                    rect(g, x + 7, y + 0.5, 1.5, 1.5);
                }
                case 1 -> { // plastic bag
                    g.setColor(new Color(214, 214, 204));
                    rect(g, x, y - 1, 6, 3.5);
                    g.setColor(new Color(240, 240, 232));
                    rect(g, x + 1, y - 1, 3, 1);
                    rect(g, x + 1, y - 2.5, 1, 1.5);
                    rect(g, x + 4, y - 2.5, 1, 1.5);
                }
                case 2 -> { // can
                    g.setColor(new Color(196, 48, 42));
                    rect(g, x, y, 5, 2.5);
                    g.setColor(new Color(220, 220, 220));
                    rect(g, x, y, 1, 2.5);
                    g.setColor(new Color(250, 250, 250));
                    rect(g, x + 2, y + 1, 2, 0.5);
                }
                case 3 -> { // tire
                    g.setColor(new Color(28, 28, 30));
                    rect(g, x, y - 1, 10, 3.5);
                    g.setColor(new Color(70, 70, 72));
                    rect(g, x + 1, y - 1, 8, 0.5);
                    g.setColor(new Color(60, 66, 48));
                    rect(g, x + 3.5, y, 3, 1);
                }
                default -> { // dead fish
                    g.setColor(new Color(170, 176, 170));
                    rect(g, x, y, 7, 2);
                    rect(g, x + 7, y - 0.5, 2, 3);
                    g.setColor(new Color(40, 40, 40));
                    rect(g, x + 1, y, 1, 1);
                }
            }
        }
    }

    private void drawToxicBubbles(Graphics2D g) {
        double dy = oy(D_WATER);
        for (int i = 0; i < 18; i++) {
            double ph = t / (T / 45) + hash(i + 5000);
            double a = frac(ph);
            int gen = Math.floorMod((int) Math.floor(ph), 45);
            double x = hash(i * 47 + gen + 5010) * W * 0.62;
            double y = WATER_TOP + 4 + hash(i * 53 + gen + 5020) * (WATER_BOT - WATER_TOP - 8) + dy;
            if (a < 0.7) {
                double r = 0.8 + a * 2.2;
                g.setColor(new Color(150, 170, 80, 200));
                rect(g, x - r, y - r, r * 2, r * 2);
                g.setColor(new Color(210, 220, 150, 220));
                rect(g, x - r * 0.5, y - r * 0.8, 1, 1);
            } else {
                double k = (a - 0.7) / 0.3;
                g.setColor(new Color(170, 180, 110, (int) (200 * (1 - k))));
                rect(g, x - 3 - k * 4, y, 1, 1);
                rect(g, x + 2 + k * 4, y, 1, 1);
                rect(g, x, y - 2 - k * 4, 1, 1);
            }
        }
    }

    private void drawAsh(Graphics2D g) {
        for (int i = 0; i < 80; i++) {
            double a = frac(t / (T / 14) + hash(i + 7000));
            double x = hash(i + 7100) * W * 0.75 + Math.sin(TAU * (a * 2 + hash(i))) * 6 + a * 24;
            double y = a * (H + 10) - 5;
            g.setColor(i % 3 == 0 ? new Color(58, 56, 52, 170) : new Color(156, 146, 128, 150));
            rect(g, x, y, i % 5 == 0 ? 1.5 : 1, 1);
        }
    }

    private void drawSmogWisps(Graphics2D g) {
        for (int i = 0; i < wispSprites.length; i++) {
            double a = frac(t / (T / 2) + hash(i + 8000));
            double x = -160 + a * (W * 0.75 + 220);
            double y = 140 + hash(i + 8010) * 150;
            blitSprite(wispSprites[i], x, y, 1.0);
        }
    }

    // =====================================================================
    // One-time painting
    // =====================================================================

    private interface Painter {
        void paint(Graphics2D g, Random r);
    }

    private Strip strip(int y0, int y1, double depth, int shift, Painter painter, Color haze, boolean blur, boolean dirty) {
        BufferedImage base = new BufferedImage(SW, y1 + 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = base.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        painter.paint(g, new Random(0xEC0L + y0 * 31L + y1 * 7L + shift));
        g.setComposite(AlphaComposite.SrcAtop);
        if (haze != null) {
            g.setColor(haze);
            g.fillRect(0, 0, SW, y1 + 4);
        }
        if (dirty) { // smog colour grade, baked in once instead of filled every frame
            g.setColor(GRADE_DIRTY);
            g.fillRect(0, 0, SW, y1 + 4);
        }
        g.dispose();
        int hh = y1 - y0;
        BufferedImage scaled = new BufferedImage(SW * S, hh * S, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D sg = scaled.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        sg.drawImage(base, 0, 0, SW * S, hh * S, 0, y0, SW, y1, null);
        sg.dispose();
        if (blur) {
            float[] k = new float[25];
            Arrays.fill(k, 1f / 25f);
            BufferedImage out = new BufferedImage(SW * S, hh * S, BufferedImage.TYPE_INT_ARGB_PRE);
            new ConvolveOp(new Kernel(5, 5, k), ConvolveOp.EDGE_NO_OP, null).filter(scaled, out);
            scaled = out;
        }
        return new Strip(noAccel(scaled), y0, depth, shift);
    }

    /** Runs {@code p} three times, shifted by -SW, 0, +SW, so shapes tile seamlessly. */
    private static void wrapped(Graphics2D g, Consumer<Graphics2D> p) {
        for (int k = -1; k <= 1; k++) {
            Graphics2D c = (Graphics2D) g.create();
            c.translate(k * SW, 0);
            p.accept(c);
            c.dispose();
        }
    }

    /** Periodic terrain offset: sum of sines whose frequencies tile in SW. */
    private static double wave(double x, double[][] terms) {
        double v = 0;
        for (double[] tm : terms) {
            v += tm[1] * Math.sin(TAU * tm[0] * x / SW + tm[2]);
        }
        return v;
    }

    private static int terrainY(double base, double[][] terms, double x) {
        return (int) Math.round(base + wave(x, terms));
    }

    /**
     * Fills rolling terrain with slope lighting: slopes facing right (towards
     * {@code lightRight}) get the lit colour, others the shadow colour.
     */
    private static void fillTerrain(Graphics2D g, double base, double[][] terms, int bottom,
                                    Color rim, Color lit, Color shadow, Color body, Color deep, boolean lightRight) {
        for (int x = 0; x < SW; x++) {
            int y = terrainY(base, terms, x);
            int yn = terrainY(base, terms, x + 2);
            boolean facesRight = yn >= y;
            boolean isLit = facesRight == lightRight;
            int span = bottom - y;
            g.setColor(deep);
            g.fillRect(x, y, 1, span);
            g.setColor(body);
            g.fillRect(x, y, 1, Math.max(1, (int) (span * 0.55)));
            g.setColor(isLit ? lit : shadow);
            g.fillRect(x, y, 1, Math.min(span, 4 + Math.abs(yn - y) * 2));
            g.setColor(rim);
            g.fillRect(x, y, 1, 1);
        }
    }

    // ----------------------------------------------------------- sky + post

    private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

    private static BufferedImage makeSky(Color top, Color mid, Color hor, Color grade) {
        if (grade != null) {
            double k = grade.getAlpha() / 255.0;
            top = mix(top, grade, k);
            mid = mix(mid, grade, k);
            hor = mix(hor, grade, k);
        }
        BufferedImage small = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int bands = 18;
        for (int y = 0; y < H; y++) {
            double v = Math.min(1, y / 250.0);
            double band = v * bands;
            int b0 = (int) Math.floor(band);
            double f = band - b0;
            Color c0 = skyAt(top, mid, hor, b0 / (double) bands);
            Color c1 = skyAt(top, mid, hor, Math.min(1, (b0 + 1) / (double) bands));
            for (int x = 0; x < W; x++) {
                boolean use1 = f * 16 > BAYER[y & 3][x & 3];
                small.setRGB(x, y, (use1 ? c1 : c0).getRGB());
            }
        }
        BufferedImage big = new BufferedImage(BW, BH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(small, 0, 0, BW, BH, null);
        g.dispose();
        return noAccel(big);
    }

    private static Color skyAt(Color top, Color mid, Color hor, double v) {
        return v < 0.55 ? mix(top, mid, v / 0.55) : mix(mid, hor, (v - 0.55) / 0.45);
    }

    private static BufferedImage makeVignette() {
        BufferedImage v = new BufferedImage(BW, BH, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = v.createGraphics();
        g.setPaint(new RadialGradientPaint(BW / 2f, BH * 0.45f, BW * 0.72f,
                new float[] {0.5f, 0.85f, 1f},
                new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 40), new Color(0, 0, 0, 95)}));
        g.fillRect(0, 0, BW, BH);
        g.dispose();
        return noAccel(v);
    }

    // ----------------------------------------------------------- sprites

    private static BufferedImage sprite(int size) {
        return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
    }

    /** Draws a circle centred in a sprite, in internal units. */
    private static void spriteDisc(Graphics2D g, double c, double cx, double cy, double r, Color col) {
        g.setColor(col);
        int d = (int) Math.round(r * 2 * S);
        g.fillOval((int) Math.round(c + (cx - r) * S), (int) Math.round(c + (cy - r) * S), d, d);
    }

    private static BufferedImage makeSunSprite() {
        int size = 54 * 2 * S;
        BufferedImage img = sprite(size);
        Graphics2D g = img.createGraphics();
        double c = size / 2.0;
        spriteDisc(g, c, 0, 0, 52, new Color(255, 252, 215, 26));
        spriteDisc(g, c, 0, 0, 40, new Color(255, 248, 190, 44));
        spriteDisc(g, c, 0, 0, 30, new Color(255, 242, 160, 70));
        spriteDisc(g, c, 0, 0, 21, new Color(255, 214, 70));
        spriteDisc(g, c, 0, 0, 19, new Color(255, 228, 92));
        spriteDisc(g, c, -4, -4, 11, new Color(255, 242, 150));
        spriteDisc(g, c, -6, -6, 4, new Color(255, 252, 215));
        g.dispose();
        return noAccel(img);
    }

    private static BufferedImage makeSmogSunSprite() {
        int size = 42 * 2 * S;
        BufferedImage img = sprite(size);
        Graphics2D g = img.createGraphics();
        double c = size / 2.0;
        spriteDisc(g, c, 0, 0, 40, new Color(220, 150, 80, 30));
        spriteDisc(g, c, 0, 0, 26, new Color(225, 155, 85, 50));
        spriteDisc(g, c, 0, 0, 15, new Color(232, 168, 100, 140));
        g.dispose();
        return noAccel(img);
    }

    /** Three-tone smoke puff lit from the upper left (the smog sun). */
    private static BufferedImage makePuff(double r, int shade) {
        int size = (int) Math.ceil((r * 1.4) * 2 * S) + 4;
        BufferedImage img = sprite(size);
        Graphics2D g = img.createGraphics();
        double c = size / 2.0;
        spriteDisc(g, c, r * 0.15, r * 0.2, r, new Color(shade - 22, shade - 26, shade - 30));
        spriteDisc(g, c, 0, 0, r * 0.9, new Color(shade, shade - 4, shade - 10));
        spriteDisc(g, c, -r * 0.3, -r * 0.3, r * 0.5, new Color(shade + 26, shade + 20, shade + 10));
        g.dispose();
        return noAccel(img);
    }

    private static BufferedImage makeWisp(double rw) {
        int w = (int) Math.ceil(rw * 2 * S) + 2;
        int h = (int) Math.ceil(rw * 0.36 * S) + 2;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(150, 134, 104, 26));
        g.fillOval(0, 0, w - 2, h - 2);
        g.setColor(new Color(170, 154, 120, 20));
        g.fillOval((int) (rw * 0.4 * S), (int) (rw * 0.06 * S), (int) (rw * 1.2 * S), (int) (rw * 0.2 * S));
        g.dispose();
        return noAccel(img);
    }

    // ----------------------------------------------------------- clouds

    private void paintCloudsClean(Graphics2D g, Random r) {
        for (int i = 0; i < 10; i++) { // thin high streaks
            int x = r.nextInt(SW), y = 14 + r.nextInt(40), len = 30 + r.nextInt(60);
            wrapped(g, c -> {
                c.setColor(new Color(255, 255, 255, 70));
                c.fillRect(x, y, len, 1);
                c.fillRect(x + 8, y + 2, len - 16, 1);
            });
        }
        for (int i = 0; i < 11; i++) {
            double cx = i * (SW / 11.0) + r.nextInt(70);
            double cy = 34 + r.nextInt(80);
            double sc = 0.7 + (cy - 34) / 80.0 * 0.6;
            double w = (44 + r.nextInt(60)) * sc;
            int n = 4 + r.nextInt(4);
            double[][] p = new double[n][3];
            for (int k = 0; k < n; k++) {
                double u = (k + 0.5) / n;
                p[k][2] = (6 + r.nextInt(6) + 8 * Math.sin(Math.PI * u)) * sc;
                p[k][0] = cx - w / 2 + u * w + r.nextInt(7) - 3;
                p[k][1] = cy - p[k][2] * 0.55 - r.nextInt(4);
            }
            wrapped(g, c -> {
                c.setColor(new Color(176, 196, 228));
                c.fillRect((int) (cx - w / 2), (int) cy, (int) w, 4);
                for (double[] b : p) {
                    disc(c, b[0] + 1, b[1] + 3, b[2]);
                }
                c.setColor(new Color(216, 228, 246));
                c.fillRect((int) (cx - w / 2), (int) cy - 2, (int) w, 4);
                for (double[] b : p) {
                    disc(c, b[0], b[1] + 1, b[2]);
                }
                c.setColor(new Color(244, 248, 255));
                for (double[] b : p) {
                    disc(c, b[0] + b[2] * 0.15, b[1] - b[2] * 0.1, b[2] * 0.8);
                }
                c.setColor(Color.WHITE);
                for (double[] b : p) {
                    disc(c, b[0] + b[2] * 0.35, b[1] - b[2] * 0.35, b[2] * 0.4);
                }
            });
        }
    }

    private void paintCloudsDirty(Graphics2D g, Random r) {
        for (int y = 0; y < 90; y++) {
            g.setColor(new Color(62, 58, 54, (int) (170 * (1 - y / 90.0))));
            g.fillRect(0, y, SW, 1);
        }
        for (int i = 0; i < 34; i++) {
            double cx = i * (SW / 34.0) + r.nextInt(30);
            double cy = 8 + r.nextInt(110);
            int rad = 16 + r.nextInt(22);
            int shade = 64 + r.nextInt(40);
            long seed = r.nextLong();
            wrapped(g, c -> {
                Random rr = new Random(seed);
                c.setColor(new Color(shade - 16, shade - 20, shade - 24, 220));
                c.fillOval((int) (cx - rad * 1.6), (int) (cy - rad * 0.5 + 4), (int) (rad * 3.2), (int) (rad * 1.2));
                c.setColor(new Color(shade + 4, shade, shade - 8, 215));
                c.fillOval((int) (cx - rad * 1.3), (int) (cy - rad * 0.65), (int) (rad * 2.6), (int) (rad * 1.15));
                c.setColor(new Color(shade + 26, shade + 20, shade + 8, 200));
                c.fillOval((int) (cx - rad * 0.9), (int) (cy - rad * 0.7), (int) (rad * 1.3), (int) (rad * 0.55));
                c.setColor(new Color(shade, shade - 4, shade - 10, 180));
                for (int k = 0; k < 40; k++) {
                    double a = rr.nextDouble() * TAU;
                    c.fillRect((int) (cx + Math.cos(a) * rad * 1.6), (int) (cy + 2 + Math.sin(a) * rad * 0.62), 1, 1);
                }
            });
        }
    }

    // ----------------------------------------------------------- far plane

    private void paintFarClean(Graphics2D g, Random r) {
        for (int x = 0; x < SW; x++) {
            int y = (int) Math.round(ridge(x));
            double slope = (ridge(x + 3) - ridge(x - 3)) / 6.0; // >0: face turns towards the sun (right)
            double light = Math.max(-1, Math.min(1, slope * 2.2));
            for (int yy = y; yy < 262; yy++) {
                double depth = (yy - y) / 45.0;              // light fades down the face
                double k = light * Math.max(0.15, 1 - depth);
                Color base = mix(new Color(116, 142, 186), new Color(150, 176, 206), (yy - 150) / 110.0);
                Color c = k >= 0 ? mix(base, new Color(184, 206, 234), k) : mix(base, new Color(86, 108, 156), -k);
                double n = hash(x * 7 + yy * 1031);
                if (n < 0.12 && yy > y + 3) {      // rocky texture: darker flecks on shaded faces
                    c = shade(c, k < 0 ? 0.88 : 0.95);
                } else if (n > 0.95 && k > 0.2) {
                    c = shade(c, 1.06);
                }
                g.setColor(c);
                g.fillRect(x, yy, 1, 1);
            }
            if (y < 178) {
                int snow = (int) ((178 - y) * 0.6) + 2;
                g.setColor(light > -0.1 ? new Color(246, 250, 255) : new Color(204, 216, 238));
                g.fillRect(x, y, 1, snow);
                g.setColor(new Color(220, 230, 246));
                g.fillRect(x, y + snow, 1, 1);
            }
        }
        double[][] hills = {{3, 9, 0.2}, {7, 5, 1.0}, {15, 2, 2.0}};
        fillTerrain(g, 234, hills, 262, new Color(170, 210, 180), new Color(150, 196, 162),
                new Color(124, 172, 146), new Color(130, 180, 150), new Color(116, 164, 140), true);
        for (int i = 0; i < 120; i++) { // distant woods
            int x = r.nextInt(SW);
            int y = terrainY(234, hills, x) + 2 + r.nextInt(14);
            wrapped(g, c -> {
                c.setColor(new Color(96, 146, 120));
                c.fillRect(x, y, 3, 2);
                c.fillRect(x + 1, y - 1, 1, 1);
            });
        }
    }

    /** Mountain skyline: pointed peaks, rounded valleys; periodic in SW. */
    private static double ridge(double x) {
        return 214 - 30 * peak(2, 0.3, x) - 22 * peak(5, 1.1, x) - 9 * peak(11, 0.2, x) - 3 * peak(23, 2.0, x);
    }

    private static double peak(int freq, double phase, double x) {
        double s = (Math.sin(TAU * freq * x / SW + phase) + 1) / 2;
        return s * s * s;
    }

    private void paintFarDirty(Graphics2D g, Random r) {
        double[][] hills = {{2, 6, 0.4}, {6, 3, 1.3}};
        fillTerrain(g, 238, hills, 262, new Color(124, 112, 92), new Color(112, 102, 86),
                new Color(100, 92, 78), new Color(104, 96, 82), new Color(96, 88, 74), false);
        int x = 0;
        while (x < SW) {
            int bw = 12 + r.nextInt(20);
            int bh = 22 + r.nextInt(80);
            int shade = 96 + r.nextInt(26);
            int fx = x;
            boolean antenna = bh > 60 && r.nextBoolean();
            boolean stepped = r.nextInt(3) == 0;
            long seed = r.nextLong();
            wrapped(g, c -> {
                Random rr = new Random(seed);
                box(c, fx, 246, bw, bh, 4, new Color(shade, shade - 8, shade - 18),
                        new Color(shade - 16, shade - 22, shade - 30), new Color(shade + 10, shade + 2, shade - 8));
                if (stepped) {
                    box(c, fx + bw / 4, 246 - bh, bw / 2, 10, 3, new Color(shade, shade - 8, shade - 18),
                            new Color(shade - 16, shade - 22, shade - 30), new Color(shade + 10, shade + 2, shade - 8));
                }
                if (antenna) {
                    c.setColor(new Color(shade - 20, shade - 26, shade - 34));
                    c.fillRect(fx + bw / 2, 246 - bh - 16, 1, 16);
                }
                for (int wy = 246 - bh + 4; wy < 244; wy += 4) {
                    for (int wx = fx + 2; wx < fx + bw - 2; wx += 3) {
                        if (rr.nextInt(9) == 0) {
                            c.setColor(new Color(170, 146, 90));
                            c.fillRect(wx, wy, 1, 2);
                        } else if (rr.nextInt(3) == 0) {
                            c.setColor(new Color(shade - 22, shade - 28, shade - 34));
                            c.fillRect(wx, wy, 1, 2);
                        }
                    }
                }
            });
            x += bw - 3 + r.nextInt(5);
        }
    }

    // ----------------------------------------------------------- second plane

    private void paintFar2Clean(Graphics2D g, Random r) {
        double[][] hills = {{2, 12, 0.0}, {5, 7, 1.0}, {11, 3, 2.3}};
        fillTerrain(g, 242, hills, 272, new Color(176, 228, 132), new Color(140, 208, 108),
                new Color(100, 170, 86), new Color(116, 188, 94), new Color(98, 166, 80), true);
        // patchwork crop fields
        for (int f = 0; f < 10; f++) {
            int fx = r.nextInt(SW);
            int fw = 30 + r.nextInt(50);
            Color a = r.nextBoolean() ? new Color(196, 214, 104) : new Color(150, 200, 96);
            Color b = new Color(a.getRed() - 24, a.getGreen() - 18, a.getBlue() - 10);
            wrapped(g, c -> {
                for (int xx = fx; xx < fx + fw; xx++) {
                    int yy = terrainY(242, hills, xx) + 5;
                    for (int k = 0; k < 10; k++) {
                        c.setColor(k % 2 == 0 ? a : b);
                        c.fillRect(xx, yy + k, 1, 1);
                    }
                }
            });
        }
        // tree clumps
        for (int i = 0; i < 70; i++) {
            int x = r.nextInt(SW);
            int y = terrainY(242, hills, x) + 1 + r.nextInt(16);
            int sz = 2 + r.nextInt(3);
            wrapped(g, c -> {
                c.setColor(new Color(40, 100, 60, 90));
                c.fillOval(x - sz - 1, y + sz - 1, sz * 2, 2);
                c.setColor(new Color(52, 130, 62));
                c.fillOval(x - sz, y - sz, sz * 2, sz * 2);
                c.setColor(new Color(92, 170, 80));
                c.fillRect(x, y - sz + 1, Math.max(1, sz - 1), 1);
            });
        }
        // wind farm + solar farms + cottages
        for (int i = 0; i < 9; i++) {
            int tx = i * (SW / 9) + 20 + r.nextInt(70);
            int hy = terrainY(242, hills, tx);
            int th = 40 + r.nextInt(26);
            double len = th * 0.42;
            turbines.add(new double[] {tx + 0.5, hy - th, len, 36 + r.nextInt(30)});
            wrapped(g, c -> {
                for (int yy = 0; yy < th + 2; yy++) {
                    double u = yy / (double) th;
                    int w = u < 0.5 ? 2 : 3;
                    c.setColor(new Color(214, 222, 230));
                    c.fillRect(tx - w / 2, hy - th + yy, w, 1);
                    c.setColor(new Color(252, 253, 255));
                    c.fillRect(tx - w / 2 + w - 1, hy - th + yy, 1, 1);
                }
                c.setColor(new Color(236, 240, 244));
                c.fillRect(tx - 3, hy - th - 1, 5, 3);
                c.setColor(new Color(200, 208, 218));
                c.fillRect(tx - 3, hy - th + 1, 5, 1);
                c.setColor(new Color(60, 110, 60, 90));
                c.fillRect(tx - 4, hy, 6, 1);
            });
            if (i % 2 == 0) {
                int px0 = tx + 26 + r.nextInt(16);
                int count = 4 + r.nextInt(3);
                for (int k = 0; k < count; k++) {
                    int px = px0 + k * 15;
                    int py = terrainY(242, hills, px + 7) - 6;
                    panels.add(new double[] {px, py});
                    wrapped(g, c -> drawSolarPanel(c, px, py));
                }
            } else {
                int hx = tx + 30 + r.nextInt(20);
                int hb = terrainY(242, hills, hx + 6) + 2;
                long seed = r.nextLong();
                wrapped(g, c -> cottage(c, new Random(seed), hx, hb, 12, 8, 4));
            }
        }
    }

    private static void drawSolarPanel(Graphics2D c, int px, int py) {
        c.setColor(new Color(40, 90, 40, 90));
        c.fillRect(px - 1, py + 9, 16, 2);
        c.setColor(new Color(120, 126, 132));
        c.fillRect(px + 3, py + 5, 1, 5);
        c.fillRect(px + 11, py + 4, 1, 6);
        c.setColor(new Color(30, 54, 128));
        for (int yy = 0; yy < 7; yy++) {
            c.fillRect(px + (6 - yy) / 2, py + yy, 13, 1);
        }
        c.setColor(new Color(70, 110, 196));
        c.fillRect(px + 3, py, 13, 1);
        c.fillRect(px + 2, py + 2, 13, 1);
        c.fillRect(px + 1, py + 4, 13, 1);
        c.setColor(new Color(130, 170, 236));
        c.fillRect(px + 7, py, 1, 7);
        c.fillRect(px + 11, py, 1, 7);
        c.setColor(new Color(214, 220, 226));
        c.fillRect(px, py + 7, 14, 1);
    }

    private void paintFar2Dirty(Graphics2D g, Random r) {
        double[][] ground = {{3, 2.5, 0.4}, {8, 1.5, 1.1}};
        fillTerrain(g, 248, ground, 272, new Color(110, 100, 84), new Color(92, 84, 72),
                new Color(80, 74, 64), new Color(84, 78, 68), new Color(74, 68, 60), false);
        // pylons with sagging wires
        int pylons = 7;
        int[] px = new int[pylons];
        for (int i = 0; i < pylons; i++) {
            px[i] = i * (SW / pylons) + 60;
        }
        wrapped(g, c -> {
            for (int i = 0; i < pylons; i++) {
                pylon(c, px[i], terrainY(248, ground, px[i]), 52);
            }
            c.setColor(new Color(50, 48, 46));
            for (int i = 0; i < pylons; i++) {
                int x0 = px[i], x1 = (i + 1 < pylons) ? px[i + 1] : px[0] + SW;
                for (int wire = -1; wire <= 1; wire += 2) {
                    int ay0 = terrainY(248, ground, x0) - 48;
                    int ay1 = terrainY(248, ground, x1) - 48;
                    for (int xx = x0; xx <= x1; xx++) {
                        double u = (xx - x0) / (double) (x1 - x0);
                        int yy = (int) Math.round(ay0 + (ay1 - ay0) * u + 14 * 4 * u * (1 - u));
                        c.fillRect(xx + wire * 7, yy, 1, 1);
                    }
                }
            }
        });
        for (int f = 0; f < 6; f++) {
            int fx = f * (SW / 6) + r.nextInt(50);
            int fw = 62 + r.nextInt(30);
            int fh = 34 + r.nextInt(18);
            int base = 250;
            int stacks = 2 + r.nextInt(2);
            int[][] st = new int[stacks][3];
            for (int s = 0; s < stacks; s++) {
                st[s][0] = fx + 8 + s * (fw / stacks) + r.nextInt(6);
                st[s][1] = 8 + r.nextInt(3);
                st[s][2] = 60 + r.nextInt(50);
                chimneys.add(new double[] {st[s][0] + st[s][1] / 2.0, base - fh - st[s][2] - 2});
            }
            boolean tower = f % 2 == 0;
            boolean flare = f % 3 == 1;
            boolean tanks = !tower;
            int tx = fx + fw + 12;
            if (flare) {
                flares.add(new double[] {fx - 6.5, base - 92});
            }
            long seed = r.nextLong();
            wrapped(g, c -> {
                Random rr = new Random(seed);
                if (tower) {
                    coolingTower(c, tx, base, 44);
                }
                if (tanks) {
                    cylinder(c, tx, base - 18, 22, 18, new Color(150, 140, 120), 0.3);
                    cylinder(c, tx + 26, base - 14, 18, 14, new Color(140, 126, 104), 0.3);
                    c.setColor(new Color(140, 80, 50));
                    c.fillRect(tx + 4, base - 14, 1, 8);
                    c.fillRect(tx + 30, base - 10, 1, 6);
                }
                if (flare) {
                    cylinder(c, fx - 8, base - 92, 3, 92, new Color(90, 90, 96), 0.3);
                }
                for (int[] s : st) {
                    cylinder(c, s[0], base - fh - s[2], s[1], s[2] + fh, new Color(70, 70, 78), 0.3);
                    c.setColor(new Color(170, 86, 52));
                    c.fillRect(s[0], base - fh - s[2] + 7, s[1], 3);
                    c.fillRect(s[0], base - fh - s[2] + 15, s[1], 2);
                    c.setColor(new Color(30, 30, 34));
                    c.fillRect(s[0] - 1, base - fh - s[2], s[1] + 2, 2);
                }
                box(c, fx, base, fw, fh, 9, new Color(78, 80, 88), new Color(56, 58, 64), new Color(98, 100, 106));
                // saw-tooth roof
                for (int sx = fx; sx < fx + fw - 9; sx += 11) {
                    for (int yy = 0; yy < 7; yy++) {
                        c.setColor(yy == 0 ? new Color(120, 122, 128) : new Color(66, 68, 74));
                        c.fillRect(sx + yy, base - fh - 7 + yy, 11 - yy, 1);
                    }
                    c.setColor(new Color(150, 140, 90));
                    c.fillRect(sx + 9, base - fh - 6, 1, 6);
                }
                for (int wy = base - fh + 6; wy < base - 8; wy += 9) {
                    for (int wx = fx + 4; wx < fx + fw - 5; wx += 8) {
                        boolean lit = rr.nextInt(7) == 0;
                        c.setColor(lit ? new Color(220, 176, 90) : new Color(34, 36, 40));
                        c.fillRect(wx, wy, 4, 4);
                        c.setColor(new Color(100, 102, 110));
                        c.fillRect(wx, wy + 4, 4, 1);
                    }
                }
                c.setColor(new Color(110, 110, 104));
                c.fillRect(fx + fw / 2 - 7, base - 12, 14, 12);
                c.setColor(new Color(84, 84, 80));
                for (int dy = base - 11; dy < base; dy += 2) {
                    c.fillRect(fx + fw / 2 - 7, dy, 14, 1);
                }
                // pipe run to the side
                c.setColor(new Color(112, 100, 86));
                c.fillRect(fx + fw + 9, base - 22, 14, 3);
                c.setColor(new Color(140, 128, 110));
                c.fillRect(fx + fw + 9, base - 22, 14, 1);
            });
        }
    }

    private static void pylon(Graphics2D c, int x, int base, int h) {
        c.setColor(new Color(64, 62, 60));
        for (int yy = 0; yy < h; yy++) {
            double u = yy / (double) h;
            int half = (int) Math.round(1 + 7 * u);
            c.fillRect(x - half, base - h + yy, 1, 1);
            c.fillRect(x + half, base - h + yy, 1, 1);
            if (yy % 8 == 0) {
                c.fillRect(x - half, base - h + yy, half * 2 + 1, 1);
            }
            if (yy % 8 < 8) { // X bracing
                int k = yy % 8;
                int span = half * 2;
                c.fillRect(x - half + k * span / 8, base - h + yy, 1, 1);
                c.fillRect(x + half - k * span / 8, base - h + yy, 1, 1);
            }
        }
        c.fillRect(x - 8, base - h + 4, 17, 1);
        c.fillRect(x - 6, base - h + 10, 13, 1);
    }

    private static void coolingTower(Graphics2D c, int x, int base, int h) {
        for (int yy = 0; yy < h; yy++) {
            double u = yy / (double) h;
            int half = (int) Math.round(12 + 9 * Math.pow((u - 0.38) / 0.62, 2) * (u > 0.38 ? 1.0 : 0.55));
            for (int xx = -half; xx < half; xx++) {
                double s = (xx + half) / (double) (half * 2);
                double f = 0.78 + 0.32 * (1 - Math.abs(s - 0.3) * 1.6);
                c.setColor(shade(new Color(140, 134, 122), f));
                c.fillRect(x + 22 + xx, base - h + yy, 1, 1);
            }
        }
        c.setColor(new Color(96, 92, 86));
        c.fillRect(x + 10, base - h, 24, 2);
        c.setColor(new Color(166, 160, 148));
        c.fillRect(x + 10, base - h, 24, 1);
        c.setColor(new Color(120, 104, 84));
        c.fillRect(x + 14, base - h + 6, 16, 2);
    }

    // ----------------------------------------------------------- mid plane

    private static final double[][] MID_HILLS = {{3, 7, 0.3}, {7, 4, 1.4}, {17, 2, 0.2}};

    private void paintMidClean(Graphics2D g, Random r) {
        fillTerrain(g, 249, MID_HILLS, 263, new Color(170, 230, 120), new Color(122, 206, 90),
                new Color(82, 166, 70), new Color(94, 182, 74), new Color(80, 162, 64), true);
        bankFace(g, r, true);
        for (int i = 0; i < 380; i++) { // meadow flowers
            int x = r.nextInt(SW);
            int y = terrainY(249, MID_HILLS, x) + 2 + r.nextInt(10);
            int k = r.nextInt(5);
            g.setColor(k == 0 ? new Color(250, 230, 80) : k == 1 ? new Color(255, 255, 245)
                    : k == 2 ? new Color(250, 160, 200) : k == 3 ? new Color(180, 150, 255) : new Color(66, 146, 58));
            g.fillRect(x, y, 1, 1);
        }
        for (int i = 0; i < 3; i++) { // cottages
            int hx = i * (SW / 3) + 150 + r.nextInt(60);
            int hb = terrainY(249, MID_HILLS, hx + 12) + 4;
            long seed = r.nextLong();
            wrapped(g, c -> cottage(c, new Random(seed), hx, hb, 24, 15, 8));
        }
        for (int i = 0; i < 4; i++) { // wooden fences
            int fx = r.nextInt(SW);
            int len = 30 + r.nextInt(30);
            wrapped(g, c -> {
                for (int xx = fx; xx < fx + len; xx++) {
                    int yy = terrainY(249, MID_HILLS, xx) + 6;
                    c.setColor(new Color(150, 110, 72));
                    c.fillRect(xx, yy - 5, 1, 1);
                    c.fillRect(xx, yy - 2, 1, 1);
                    if ((xx - fx) % 6 == 0) {
                        c.setColor(new Color(120, 84, 56));
                        c.fillRect(xx, yy - 7, 2, 7);
                        c.setColor(new Color(176, 134, 92));
                        c.fillRect(xx + 1, yy - 7, 1, 7);
                    }
                }
            });
        }
        for (int i = 0; i < 34; i++) { // trees
            int tx = i * (SW / 34) + r.nextInt(24);
            int base = terrainY(249, MID_HILLS, tx) + 4;
            int kind = r.nextInt(6); // 0-1 round, 2-3 blossom, 4 pine, 5 bush
            int size = 7 + r.nextInt(7);
            long seed = r.nextLong();
            wrapped(g, c -> tree(c, new Random(seed), tx, base, size, kind));
        }
    }

    private void paintMidDirty(Graphics2D g, Random r) {
        fillTerrain(g, 250, MID_HILLS, 263, new Color(150, 134, 100), new Color(128, 114, 88),
                new Color(98, 88, 68), new Color(110, 98, 78), new Color(94, 84, 66), false);
        for (int i = 0; i < 500; i++) {
            int x = r.nextInt(SW);
            int y = terrainY(250, MID_HILLS, x) + 2 + r.nextInt(10);
            g.setColor(r.nextBoolean() ? new Color(84, 76, 60) : new Color(130, 120, 96));
            g.fillRect(x, y, 2, 1);
        }
        bankFace(g, r, false);
        // utility poles + wires
        int poles = 9;
        int[] px = new int[poles];
        for (int i = 0; i < poles; i++) {
            px[i] = i * (SW / poles) + 30;
        }
        wrapped(g, c -> {
            for (int i = 0; i < poles; i++) {
                int b = terrainY(250, MID_HILLS, px[i]) + 4;
                c.setColor(new Color(80, 62, 48));
                c.fillRect(px[i], b - 40, 2, 40);
                c.setColor(new Color(104, 82, 62));
                c.fillRect(px[i], b - 40, 1, 40);
                c.setColor(new Color(70, 54, 42));
                c.fillRect(px[i] - 6, b - 36, 14, 2);
                c.setColor(new Color(170, 170, 160));
                c.fillRect(px[i] - 6, b - 37, 1, 1);
                c.fillRect(px[i] + 7, b - 37, 1, 1);
            }
            c.setColor(new Color(40, 38, 36));
            for (int i = 0; i < poles; i++) {
                int x0 = px[i], x1 = (i + 1 < poles) ? px[i + 1] : px[0] + SW;
                int y0 = terrainY(250, MID_HILLS, x0) + 4 - 36;
                int y1 = terrainY(250, MID_HILLS, x1) + 4 - 36;
                for (int w = 0; w < 2; w++) {
                    for (int xx = x0; xx <= x1; xx++) {
                        double u = (xx - x0) / (double) (x1 - x0);
                        int yy = (int) Math.round(y0 + (y1 - y0) * u + (8 + w * 3) * 4 * u * (1 - u));
                        c.fillRect(xx + (w == 0 ? -6 : 7), yy - 1, 1, 1);
                    }
                }
            }
        });
        for (int i = 0; i < 5; i++) { // warehouses
            int bx = i * (SW / 5) + r.nextInt(90);
            int bw = 46 + r.nextInt(26);
            int bh = 30 + r.nextInt(20);
            int base = terrainY(250, MID_HILLS, bx + bw / 2.0) + 6;
            long seed = r.nextLong();
            wrapped(g, c -> warehouse(c, new Random(seed), bx, base, bw, bh));
        }
        for (int i = 0; i < 16; i++) { // dead trees
            int tx = r.nextInt(SW);
            int base = terrainY(250, MID_HILLS, tx) + 4;
            int th = 12 + r.nextInt(12);
            long seed = r.nextLong();
            wrapped(g, c -> {
                c.setColor(new Color(30, 24, 20, 80));
                c.fillRect(tx - 1, base, 8, 1);
                deadBranch(c, new Random(seed), tx, base, -Math.PI / 2, th, 4);
            });
        }
        for (int i = 0; i < 10; i++) { // junk heaps
            int jx = r.nextInt(SW);
            int base = terrainY(250, MID_HILLS, jx) + 7;
            long seed = r.nextLong();
            wrapped(g, c -> {
                Random rr = new Random(seed);
                c.setColor(new Color(70, 66, 60));
                c.fillOval(jx - 7, base - 5, 14, 7);
                for (int k = 0; k < 7; k++) {
                    int kc = rr.nextInt(4);
                    c.setColor(kc == 0 ? new Color(170, 60, 50) : kc == 1 ? new Color(200, 200, 190)
                            : kc == 2 ? new Color(60, 90, 140) : new Color(40, 40, 40));
                    c.fillRect(jx - 6 + rr.nextInt(11), base - 5 + rr.nextInt(4), 2, 1);
                }
            });
        }
    }

    /** Vertical bank face where the far shore meets the water: earth (clean) or concrete (dirty). */
    private void bankFace(Graphics2D g, Random r, boolean clean) {
        int top = 262, bottom = 276;
        if (clean) {
            g.setColor(new Color(112, 80, 54));
            g.fillRect(0, top, SW, bottom - top);
            g.setColor(new Color(132, 96, 64));
            g.fillRect(0, top, SW, 2);
            g.setColor(new Color(92, 64, 42));
            g.fillRect(0, top + 5, SW, bottom - top - 5);
            for (int i = 0; i < 260; i++) { // stones
                int x = r.nextInt(SW), y = top + 1 + r.nextInt(6);
                g.setColor(new Color(156, 146, 128));
                g.fillRect(x, y, 2, 1);
                g.setColor(new Color(110, 102, 90));
                g.fillRect(x, y + 1, 2, 1);
            }
            for (int x = 0; x < SW; x++) { // grass overhang
                int len = (int) (hash(x * 3 + 11) * 3);
                g.setColor(new Color(86, 170, 70));
                g.fillRect(x, top, 1, len);
            }
        } else {
            g.setColor(new Color(124, 120, 110));
            g.fillRect(0, top, SW, bottom - top);
            g.setColor(new Color(150, 146, 134));
            g.fillRect(0, top, SW, 1);
            g.setColor(new Color(96, 94, 88));
            g.fillRect(0, top + 3, SW, 1);
            for (int x = 0; x < SW; x += 12) {
                g.fillRect(x, top + 1, 1, 2);
                g.fillRect(x + 6, top + 4, 1, bottom - top - 4);
            }
            for (int i = 0; i < 90; i++) { // stains
                int x = r.nextInt(SW);
                g.setColor(new Color(84, 90, 60, 170));
                g.fillRect(x, top + 2, 1, 2 + r.nextInt(8));
            }
            for (int i = 0; i < 6; i++) { // drain pipes
                int x = i * (SW / 6) + 40 + r.nextInt(80);
                pipes.add(new double[] {x + 0.5, top + 3.5});
                wrapped(g, c -> {
                    c.setColor(new Color(60, 58, 54));
                    c.fillOval(x - 3, top + 1, 7, 5);
                    c.setColor(new Color(150, 146, 138));
                    c.drawOval(x - 3, top + 1, 7, 5);
                    c.setColor(new Color(24, 24, 22));
                    c.fillRect(x - 1, top + 3, 3, 2);
                });
            }
        }
    }

    private static void cottage(Graphics2D c, Random r, int x, int base, int w, int h, int dep) {
        int dx = dep, dy = dep / 2;
        int top = base - h;
        int rh = Math.max(4, w / 3);
        int ridgeX = x + w / 2;
        // cast shadow (sun is to the right)
        c.setColor(new Color(30, 80, 30, 80));
        c.fillRect(x - dep / 2, base - 1, w + dep / 2, 2);
        // side wall
        c.setColor(new Color(204, 190, 160));
        poly(c, x + w, top, x + w + dx, top - dy, x + w + dx, base - dy, x + w, base);
        // roof slopes
        c.setColor(new Color(150, 60, 52));
        poly(c, ridgeX, top - rh, ridgeX + dx, top - rh - dy, x - 1 + dx, top - dy, x - 1, top);
        c.setColor(new Color(196, 84, 66));
        poly(c, ridgeX, top - rh, ridgeX + dx, top - rh - dy, x + w + 1 + dx, top - dy, x + w + 1, top);
        c.setColor(new Color(220, 110, 86));
        c.drawLine(ridgeX, top - rh, ridgeX + dx, top - rh - dy);
        // solar panel on the lit slope
        if (w >= 16) {
            c.setColor(new Color(36, 64, 140));
            poly(c, ridgeX + 2, top - rh + 2, ridgeX + 2 + dx - 2, top - rh + 2 - dy + 1,
                    x + w - 1 + dx - 2, top - dy, x + w - 1, top - 1);
            c.setColor(new Color(110, 150, 220));
            c.drawLine(ridgeX + 3, top - rh + 3, x + w - 2, top - 1);
        }
        // front wall + gable
        c.setColor(new Color(240, 230, 204));
        c.fillRect(x, top, w, h);
        poly(c, x, top, ridgeX, top - rh + 1, x + w, top);
        c.setColor(new Color(216, 204, 176));
        c.fillRect(x, base - 2, w, 2);
        // door & windows
        int doorX = x + w / 2 - Math.max(1, w / 10);
        c.setColor(new Color(120, 76, 48));
        c.fillRect(doorX, base - Math.max(4, h / 2), Math.max(2, w / 6), Math.max(4, h / 2));
        if (w >= 16) {
            for (int k = 0; k < 2; k++) {
                int wx = (k == 0) ? x + 2 : x + w - 6;
                c.setColor(new Color(120, 190, 230));
                c.fillRect(wx, top + 3, 4, 4);
                c.setColor(new Color(220, 245, 255));
                c.fillRect(wx, top + 3, 2, 2);
                c.setColor(new Color(150, 110, 80));
                c.fillRect(wx - 1, top + 7, 6, 1);
                c.setColor(new Color(240, 120, 150));
                c.fillRect(wx, top + 6, 1, 1);
                c.fillRect(wx + 2, top + 6, 1, 1);
            }
        } else {
            c.setColor(new Color(120, 190, 230));
            c.fillRect(x + 1, top + 2, 2, 2);
        }
        // chimney
        c.setColor(new Color(160, 96, 80));
        c.fillRect(x + w / 4, top - rh, 3, rh - 1);
    }

    private static void warehouse(Graphics2D c, Random r, int x, int base, int w, int h) {
        c.setColor(new Color(30, 26, 20, 90));
        c.fillRect(x + 4, base - 1, w + 8, 2);
        box(c, x, base, w, h, 9, new Color(108, 116, 92), new Color(80, 88, 70), new Color(128, 134, 112));
        c.setColor(new Color(96, 104, 82));
        for (int s = x + 2; s < x + w; s += 3) {
            c.fillRect(s, base - h, 1, h);
        }
        c.setColor(new Color(150, 92, 58));
        for (int s = x + 3; s < x + w - 3; s += 7 + r.nextInt(6)) {
            c.fillRect(s, base - h + 1, 2, 4 + r.nextInt(10));
        }
        for (int wy = base - h + 6; wy < base - 14; wy += 9) {
            for (int wx = x + 4; wx < x + w - 8; wx += 9) {
                c.setColor(new Color(36, 42, 42));
                c.fillRect(wx, wy, 6, 5);
                if (r.nextInt(3) == 0) { // broken pane
                    c.setColor(new Color(150, 170, 160));
                    c.fillRect(wx, wy, 2, 1);
                    c.fillRect(wx, wy + 1, 1, 2);
                } else {
                    c.setColor(new Color(70, 84, 82));
                    c.fillRect(wx, wy, 6, 1);
                }
            }
        }
        int dx = x + (r.nextBoolean() ? 4 : w - 20);
        c.setColor(new Color(122, 122, 116));
        c.fillRect(dx, base - 13, 15, 13);
        c.setColor(new Color(96, 96, 92));
        for (int yy = base - 12; yy < base; yy += 2) {
            c.fillRect(dx, yy, 15, 1);
        }
        // graffiti
        Color[] tags = {new Color(220, 80, 140), new Color(80, 200, 220), new Color(240, 200, 60)};
        c.setColor(tags[r.nextInt(tags.length)]);
        int gx = x + w / 2, gy = base - 8;
        for (int k = 0; k < 9; k++) {
            c.fillRect(gx + k, gy + (int) Math.round(Math.sin(k * 1.3) * 2), 1, 2);
        }
        // roof vents
        c.setColor(new Color(90, 94, 86));
        c.fillRect(x + w / 3, base - h - 6, 4, 3);
        c.fillRect(x + 2 * w / 3, base - h - 6, 4, 3);
    }

    private static void deadBranch(Graphics2D c, Random r, double x, double y, double ang, double len, int depth) {
        double x2 = x + Math.cos(ang) * len, y2 = y + Math.sin(ang) * len;
        c.setColor(depth >= 3 ? new Color(66, 52, 42) : new Color(84, 68, 56));
        c.drawLine((int) x, (int) y, (int) x2, (int) y2);
        if (depth >= 3) {
            c.drawLine((int) x + 1, (int) y, (int) x2 + 1, (int) y2);
        }
        if (depth > 0) {
            deadBranch(c, r, x2, y2, ang - 0.45 - r.nextDouble() * 0.3, len * 0.62, depth - 1);
            deadBranch(c, r, x2, y2, ang + 0.4 + r.nextDouble() * 0.3, len * 0.58, depth - 1);
        }
    }

    private static void tree(Graphics2D c, Random r, int x, int base, int size, int kind) {
        // cast shadow falls left (sun on the right)
        c.setColor(new Color(20, 70, 20, 80));
        c.fillOval(x - size - size / 2, base - 2, size * 2, 4);
        if (kind == 4) { // pine
            int h = size * 3;
            c.setColor(new Color(96, 66, 46));
            c.fillRect(x - 1, base - 4, 3, 4);
            int tiers = 4;
            for (int ti = 0; ti < tiers; ti++) {
                int ty = base - 4 - h + ti * h / (tiers + 1);
                int th = h / tiers + 4;
                int half = 3 + ti * size / 5;
                c.setColor(new Color(28, 92, 58));
                poly(c, x, ty, x - half, ty + th, x + half + 1, ty + th);
                c.setColor(new Color(50, 128, 74));
                poly(c, x + 1, ty + 1, x + 1, ty + th, x + half + 1, ty + th);
                c.setColor(new Color(22, 74, 46));
                c.fillRect(x - half, ty + th - 1, half * 2 + 1, 1);
            }
            return;
        }
        if (kind == 5) { // bush
            c.setColor(new Color(40, 118, 52));
            c.fillOval(x - size, base - size, size * 2, size + 2);
            c.setColor(new Color(70, 156, 66));
            c.fillOval(x - size + 3, base - size + 1, size + 2, size / 2 + 2);
            c.setColor(new Color(120, 196, 92));
            c.fillOval(x, base - size + 1, size / 2, 2);
            return;
        }
        boolean blossom = kind == 2 || kind == 3;
        int trunkH = size + 1;
        c.setColor(new Color(96, 64, 44));
        c.fillRect(x - 1, base - trunkH, 3, trunkH);
        c.setColor(new Color(140, 98, 64));
        c.fillRect(x + 1, base - trunkH, 1, trunkH);
        c.fillRect(x + 2, base - trunkH + 2, 2, 1);
        Color dark = blossom ? new Color(200, 106, 150) : new Color(36, 112, 52);
        Color mid = blossom ? new Color(238, 152, 188) : new Color(62, 150, 62);
        Color light = blossom ? new Color(255, 200, 220) : new Color(108, 190, 84);
        Color glint = blossom ? new Color(255, 236, 244) : new Color(160, 222, 110);
        int cy = base - trunkH - size + 3;
        int n = 5;
        int[][] cl = new int[n][3];
        for (int k = 0; k < n; k++) {
            double a = k / (double) n * TAU;
            cl[k][2] = (int) (size * (0.5 + r.nextDouble() * 0.2));
            cl[k][0] = x + (int) (Math.cos(a) * size * 0.45);
            cl[k][1] = cy + (int) (Math.sin(a) * size * 0.32);
        }
        c.setColor(dark);
        for (int[] b : cl) {
            disc(c, b[0], b[1] + 1, b[2]);
        }
        disc(c, x, cy, size * 0.7);
        c.setColor(mid);
        for (int[] b : cl) {
            disc(c, b[0] + 1, b[1] - 1, b[2] * 0.8);
        }
        c.setColor(light);
        for (int[] b : cl) {
            disc(c, b[0] + b[2] * 0.35 + 1, b[1] - b[2] * 0.35, b[2] * 0.42);
        }
        c.setColor(glint);
        for (int k = 0; k < size; k++) {
            c.fillRect(x + r.nextInt(size) - size / 4, cy - r.nextInt(size) + 2, 1, 1);
        }
        c.setColor(dark);
        for (int k = 0; k < size; k++) { // leaf texture
            c.fillRect(x - size + r.nextInt(size * 2), cy - size / 2 + r.nextInt(size), 1, 1);
        }
    }

    // ----------------------------------------------------------- water strips

    private void paintWaterBaseClean(Graphics2D g, Random r) {
        for (int y = WATER_TOP; y < WATER_BOT + 6; y++) {
            double k = Math.min(1, (y - WATER_TOP) / (double) (WATER_BOT - WATER_TOP));
            g.setColor(mix(new Color(70, 150, 214), new Color(40, 104, 178), k));
            g.fillRect(0, y, SW, 1);
        }
    }

    private void paintWaterBaseDirty(Graphics2D g, Random r) {
        for (int y = WATER_TOP; y < WATER_BOT + 6; y++) {
            double k = Math.min(1, (y - WATER_TOP) / (double) (WATER_BOT - WATER_TOP));
            g.setColor(mix(new Color(80, 86, 58), new Color(48, 52, 38), k));
            g.fillRect(0, y, SW, 1);
        }
    }

    private void paintWaterTopClean(Graphics2D g, Random r) {
        // shoreline foam
        for (int x = 0; x < SW; x++) {
            if (hash(x + 77) < 0.7) {
                g.setColor(new Color(226, 244, 255, 200));
                g.fillRect(x, WATER_TOP, 1, 1);
            }
        }
        // perspective ripples: short & thin near the far bank, long near the viewer
        for (int i = 0; i < 260; i++) {
            int y = WATER_TOP + 2 + (int) (Math.pow(r.nextDouble(), 0.8) * (WATER_BOT - WATER_TOP - 2));
            double f = (y - WATER_TOP) / (double) (WATER_BOT - WATER_TOP);
            int x = r.nextInt(SW);
            int len = 2 + (int) (f * 12) + r.nextInt(4);
            Color c = r.nextInt(3) == 0 ? new Color(214, 240, 255, 200) : new Color(140, 204, 244, 170);
            wrapped(g, gg -> {
                gg.setColor(c);
                gg.fillRect(x, y, len, 1);
            });
        }
        // reeds & cattails by the far bank
        for (int i = 0; i < 12; i++) {
            int x = r.nextInt(SW);
            long seed = r.nextLong();
            wrapped(g, c -> {
                Random rr = new Random(seed);
                for (int k = 0; k < 6; k++) {
                    int rx = x + k * 2 + rr.nextInt(2);
                    int rh = 6 + rr.nextInt(9);
                    c.setColor(new Color(60, 140, 60));
                    c.fillRect(rx, WATER_TOP + 3 - rh, 1, rh);
                    if (k % 2 == 0) {
                        c.setColor(new Color(130, 84, 50));
                        c.fillRect(rx, WATER_TOP + 3 - rh, 1, 3);
                    }
                }
            });
        }
        // lily pads (3D-ish: dark rim, lit top, pink flowers)
        for (int i = 0; i < 22; i++) {
            int x = r.nextInt(SW);
            int y = WATER_TOP + 6 + r.nextInt(26);
            double f = (y - WATER_TOP) / (double) (WATER_BOT - WATER_TOP);
            int pw = 7 + (int) (f * 6);
            boolean flower = r.nextInt(3) == 0;
            wrapped(g, c -> {
                c.setColor(new Color(30, 80, 110, 120));
                c.fillOval(x, y + 1, pw, 3);
                c.setColor(new Color(40, 120, 60));
                c.fillOval(x, y, pw, 3);
                c.setColor(new Color(84, 172, 84));
                c.fillOval(x + 1, y, pw - 3, 2);
                c.setColor(new Color(70, 150, 214));
                c.fillRect(x + pw / 2, y, 1, 2);
                if (flower) {
                    c.setColor(new Color(255, 180, 214));
                    c.fillRect(x + 2, y - 2, 3, 2);
                    c.setColor(new Color(255, 230, 240));
                    c.fillRect(x + 3, y - 3, 1, 1);
                    c.setColor(new Color(255, 236, 120));
                    c.fillRect(x + 3, y - 1, 1, 1);
                }
            });
        }
    }

    private void paintWaterTopDirty(Graphics2D g, Random r) {
        // scum line along the bank
        for (int x = 0; x < SW; x++) {
            int len = 1 + (int) (hash(x + 99) * 3);
            g.setColor(new Color(176, 170, 128, 200));
            g.fillRect(x, WATER_TOP, 1, len);
        }
        // oil sheen streaks
        Color[] sheen = {new Color(150, 100, 170, 170), new Color(80, 160, 150, 160), new Color(200, 180, 90, 150),
            new Color(110, 116, 76, 200)};
        for (int i = 0; i < 220; i++) {
            int y = WATER_TOP + 3 + r.nextInt(WATER_BOT - WATER_TOP - 2);
            double f = (y - WATER_TOP) / (double) (WATER_BOT - WATER_TOP);
            int x = r.nextInt(SW);
            int len = 3 + (int) (f * 12) + r.nextInt(5);
            int ci = r.nextInt(sheen.length);
            wrapped(g, c -> {
                c.setColor(sheen[ci]);
                c.fillRect(x, y, len, 1);
                if (ci < 3) {
                    c.setColor(sheen[(ci + 1) % 3]);
                    c.fillRect(x + 1, y + 1, Math.max(1, len - 2), 1);
                }
            });
        }
        // foam blobs
        for (int i = 0; i < 26; i++) {
            int x = r.nextInt(SW), y = WATER_TOP + 2 + r.nextInt(8), w = 4 + r.nextInt(8);
            wrapped(g, c -> {
                c.setColor(new Color(200, 196, 160, 200));
                c.fillOval(x, y, w, 2);
            });
        }
        for (int i = 0; i < 30; i++) {
            trash.add(new double[] {i * (SW / 30.0) + r.nextInt(25), WATER_TOP + 6 + r.nextInt(26), r.nextInt(5)});
        }
    }

    // ----------------------------------------------------------- near plane

    private static final double[][] NEAR_EDGE = {{4, 2.5, 0.0}, {9, 1.5, 1.7}};
    private static final int NEAR_BASE = 303;

    private static int nearEdge(double x) {
        return terrainY(NEAR_BASE, NEAR_EDGE, x);
    }

    /** 0 at the far edge of the near ground, 1 at the bottom of the screen. */
    private static double depthF(int y) {
        return Math.max(0, Math.min(1, (y - NEAR_BASE) / (double) (H + 12 - NEAR_BASE)));
    }

    private void paintNearClean(Graphics2D g, Random r) {
        for (int x = 0; x < SW; x++) {
            int y = nearEdge(x);
            for (int yy = y; yy < H + 12; yy += 2) {
                g.setColor(mix(new Color(128, 206, 98), new Color(66, 158, 54), depthF(yy)));
                g.fillRect(x, yy, 1, 2);
            }
            g.setColor(new Color(212, 192, 136));
            g.fillRect(x, y, 1, 1);
            g.setColor(new Color(160, 222, 110));
            g.fillRect(x, y + 1, 1, 1);
        }
        // perspective mowing bands
        for (int j = 1; j < 9; j++) {
            int y = NEAR_BASE + 3 + (int) (Math.pow(j / 9.0, 1.7) * 66);
            g.setColor(new Color(60, 140, 50, 50));
            g.fillRect(0, y, SW, 1 + j / 4);
        }
        for (int i = 0; i < 2200; i++) { // grass tufts, larger nearer the viewer
            int x = r.nextInt(SW);
            int y = NEAR_BASE + 3 + (int) (r.nextDouble() * 70);
            double f = depthF(y);
            int th = 1 + (int) Math.round(f * 4);
            boolean light = r.nextInt(3) == 0;
            wrapped(g, c -> {
                c.setColor(light ? new Color(150, 220, 104) : new Color(48, 128, 46));
                c.fillRect(x, y - th, 1, th);
                if (th > 2) {
                    c.fillRect(x + 1, y - th + 1, 1, th - 1);
                }
            });
        }
        for (int i = 0; i < 90; i++) { // flowers
            int x = r.nextInt(SW);
            int y = NEAR_BASE + 6 + r.nextInt(62);
            int sz = depthF(y) > 0.55 ? 2 : 1;
            int k = r.nextInt(4);
            Color petal = k == 0 ? new Color(255, 224, 70) : k == 1 ? new Color(255, 255, 250)
                    : k == 2 ? new Color(250, 140, 190) : new Color(176, 146, 255);
            wrapped(g, c -> {
                c.setColor(new Color(40, 110, 40));
                c.fillRect(x + sz, y + sz, 1, 2 + sz * 2);
                c.setColor(petal);
                c.fillRect(x, y + sz / 2, sz * 2 + 1, sz);
                c.fillRect(x + sz / 2, y - sz / 2, sz, sz * 2 + 1);
                c.setColor(new Color(250, 180, 50));
                c.fillRect(x + sz / 2 + (sz > 1 ? 0 : 0), y + sz / 2, sz, sz);
            });
        }
        for (int i = 0; i < 14; i++) { // rocks
            int x = r.nextInt(SW);
            int y = NEAR_BASE + 10 + r.nextInt(52);
            double f = depthF(y);
            int rw = 5 + (int) (f * 12), rh = 3 + (int) (f * 6);
            wrapped(g, c -> {
                c.setColor(new Color(30, 70, 30, 90));
                c.fillOval(x - rw / 3, y + rh - 2, rw, 3);
                c.setColor(new Color(116, 118, 116));
                c.fillOval(x, y, rw, rh);
                c.setColor(new Color(160, 162, 158));
                c.fillOval(x + rw / 3, y, rw * 2 / 3, rh * 2 / 3);
                c.setColor(new Color(196, 198, 192));
                c.fillRect(x + rw / 2, y + 1, Math.max(1, rw / 4), 1);
                c.setColor(new Color(90, 150, 70));
                c.fillRect(x + 1, y + rh - 2, rw / 3, 1); // moss
            });
        }
        for (int i = 0; i < 8; i++) { // mushrooms
            int x = r.nextInt(SW);
            int y = NEAR_BASE + 14 + r.nextInt(46);
            int s = 1 + (int) (depthF(y) * 2);
            wrapped(g, c -> {
                c.setColor(new Color(240, 232, 214));
                c.fillRect(x + s, y, s, s * 2);
                c.setColor(new Color(214, 50, 46));
                c.fillRect(x, y - s, s * 3, s + 1);
                c.setColor(new Color(255, 255, 255));
                c.fillRect(x + 1, y - s, 1, 1);
            });
        }
    }

    private void paintNearDirty(Graphics2D g, Random r) {
        for (int x = 0; x < SW; x++) {
            int y = nearEdge(x);
            for (int yy = y; yy < H + 12; yy += 2) {
                g.setColor(mix(new Color(122, 116, 104), new Color(80, 76, 68), depthF(yy)));
                g.fillRect(x, yy, 1, 2);
            }
            g.setColor(new Color(150, 144, 130));
            g.fillRect(x, y, 1, 2);
            g.setColor(new Color(64, 60, 54));
            g.fillRect(x, y + 2, 1, 1);
        }
        for (int i = 0; i < 1600; i++) { // grit, larger nearer the viewer
            int x = r.nextInt(SW);
            int y = NEAR_BASE + 4 + r.nextInt(68);
            int s = depthF(y) > 0.6 ? 2 : 1;
            g.setColor(r.nextBoolean() ? new Color(70, 66, 60) : new Color(130, 124, 112));
            g.fillRect(x, y, s, 1);
        }
        for (int i = 0; i < 18; i++) { // cracks
            int x0 = r.nextInt(SW);
            int y0 = NEAR_BASE + 8 + r.nextInt(58);
            int w = depthF(y0) > 0.5 ? 2 : 1;
            int[] dx = new int[10];
            int[] dy = new int[10];
            for (int k = 0; k < 10; k++) {
                dx[k] = 2 + r.nextInt(4);
                dy[k] = r.nextInt(3) - 1;
            }
            wrapped(g, c -> {
                c.setColor(new Color(52, 48, 44));
                int cx = x0, cy = y0;
                for (int k = 0; k < 10; k++) {
                    c.fillRect(cx, cy, dx[k], w);
                    cx += dx[k];
                    cy += dy[k];
                }
            });
        }
        for (int i = 0; i < 12; i++) { // oily puddles reflecting the smog
            int x = r.nextInt(SW);
            int y = NEAR_BASE + 10 + r.nextInt(52);
            double f = depthF(y);
            int w = 14 + (int) (f * 24), h = 3 + (int) (f * 5);
            wrapped(g, c -> {
                c.setColor(new Color(56, 58, 56));
                c.fillOval(x, y, w, h);
                c.setColor(new Color(150, 134, 104));
                c.fillOval(x + 2, y + 1, w - 4, Math.max(1, h - 2));
                c.setColor(new Color(140, 100, 160));
                c.fillRect(x + 4, y + h / 2, w / 3, 1);
                c.setColor(new Color(90, 160, 140));
                c.fillRect(x + w / 2, y + h / 2 + 1, w / 4, 1);
            });
        }
        for (int i = 0; i < 34; i++) { // litter
            int x = i * (SW / 34) + r.nextInt(24);
            int y = NEAR_BASE + 12 + r.nextInt(54);
            int kind = r.nextInt(6);
            double sc = 0.7 + depthF(y) * 0.9;
            wrapped(g, c -> litter(c, x, y, kind, sc));
        }
    }

    private static void litter(Graphics2D c, int x, int y, int kind, double sc) {
        int s = (int) Math.round(10 * sc);
        c.setColor(new Color(20, 18, 16, 90)); // cast shadow (dim light from the left)
        c.fillOval(x + s / 4, y - 1, s + 3, Math.max(2, s / 3));
        switch (kind) {
            case 0, 1 -> { // trash bag
                Color dark = kind == 0 ? new Color(30, 34, 32) : new Color(50, 70, 46);
                Color midc = kind == 0 ? new Color(52, 58, 54) : new Color(74, 98, 66);
                Color hi = kind == 0 ? new Color(104, 112, 108) : new Color(120, 146, 106);
                c.setColor(dark);
                c.fillOval(x, y - s, s + 2, s);
                c.setColor(midc);
                c.fillOval(x + 1, y - s, s - 2, s - 3);
                c.setColor(hi);
                c.fillRect(x + s / 4, y - s + 2, Math.max(1, s / 5), Math.max(1, s / 6));
                c.setColor(dark);
                c.fillRect(x + s / 2, y - s - 2, 2, 3);
                c.fillRect(x + s / 2 - 1, y - s - 3, 1, 1);
                c.fillRect(x + s / 2 + 2, y - s - 3, 1, 1);
            }
            case 2 -> { // toxic barrel (cylinder)
                int bw = Math.max(5, s * 2 / 3), bh = s;
                cylinder(c, x, y - bh, bw, bh, new Color(60, 120, 64), 0.3);
                c.setColor(new Color(230, 200, 40));
                c.fillRect(x, y - bh / 2 - 1, bw, 2);
                c.setColor(new Color(30, 30, 30));
                c.fillRect(x + bw / 2 - 1, y - bh / 2 - 1, 2, 2);
                c.setColor(new Color(150, 220, 60, 200)); // leak
                c.fillOval(x + bw - 1, y - 2, s, 3);
            }
            case 3 -> { // tire
                c.setColor(new Color(24, 24, 26));
                c.fillOval(x, y - s / 2 - 1, s + 4, s / 2 + 2);
                c.setColor(new Color(56, 56, 58));
                c.fillOval(x + 1, y - s / 2 - 1, s + 1, s / 3);
                c.setColor(new Color(90, 86, 78));
                c.fillOval(x + s / 3 + 1, y - s / 3, s / 2, Math.max(1, s / 6));
            }
            case 4 -> { // crushed can + bottle
                c.setColor(new Color(196, 46, 40));
                c.fillRect(x, y - 3, Math.max(3, s / 2), 3);
                c.setColor(new Color(220, 220, 220));
                c.fillRect(x, y - 3, 1, 3);
                c.setColor(new Color(90, 160, 100));
                c.fillRect(x + s / 2 + 2, y - 2, Math.max(3, s / 2), 2);
                c.setColor(new Color(170, 220, 180));
                c.fillRect(x + s / 2 + 3, y - 2, 2, 1);
            }
            default -> { // soggy cardboard
                c.setColor(new Color(150, 120, 80));
                poly(c, x, y - 2, x + s / 3, y - s / 2, x + s + 2, y - s / 2 + 1, x + s, y);
                c.setColor(new Color(120, 94, 62));
                c.drawLine(x + 2, y - 2, x + s - 1, y - s / 2 + 2);
            }
        }
    }

    // ----------------------------------------------------------- blurred foreground

    private void paintFgClean(Graphics2D g, Random r) {
        for (int cl = 0; cl < 14; cl++) {
            int cx = cl * (SW / 14) + r.nextInt(40);
            long seed = r.nextLong();
            wrapped(g, c -> {
                Random rr = new Random(seed);
                int blades = 14 + rr.nextInt(12);
                for (int b = 0; b < blades; b++) {
                    int bx = cx + rr.nextInt(40) - 20;
                    int bh = 22 + rr.nextInt(42);
                    double lean = (rr.nextDouble() - 0.5) * 0.9;
                    int k = rr.nextInt(3);
                    c.setColor(k == 0 ? new Color(30, 82, 40) : k == 1 ? new Color(44, 106, 50) : new Color(64, 132, 60));
                    for (int yy = 0; yy < bh; yy++) {
                        double u = yy / (double) bh;
                        int w = u < 0.6 ? 2 : 1;
                        int x = bx + (int) Math.round(lean * u * u * bh * 0.5);
                        c.fillRect(x, H + 16 - yy, w, 1);
                    }
                }
                if (rr.nextInt(2) == 0) { // tall daisy
                    int fx = cx + rr.nextInt(20) - 10;
                    int fh = 44 + rr.nextInt(18);
                    c.setColor(new Color(40, 100, 44));
                    c.fillRect(fx, H + 16 - fh, 2, fh);
                    c.setColor(new Color(250, 250, 240));
                    c.fillOval(fx - 5, H + 16 - fh - 5, 12, 10);
                    c.setColor(new Color(250, 200, 60));
                    c.fillOval(fx - 1, H + 16 - fh - 2, 4, 4);
                }
            });
        }
    }

    private void paintFgDirty(Graphics2D g, Random r) {
        for (int cl = 0; cl < 12; cl++) {
            int cx = cl * (SW / 12) + r.nextInt(50);
            long seed = r.nextLong();
            wrapped(g, c -> {
                Random rr = new Random(seed);
                int kind = rr.nextInt(3);
                if (kind == 0) { // rubble mound with rebar
                    int w = 40 + rr.nextInt(30), h = 14 + rr.nextInt(10);
                    c.setColor(new Color(62, 60, 56));
                    c.fillOval(cx - w / 2, H + 16 - h, w, h * 2);
                    c.setColor(new Color(92, 88, 82));
                    for (int k = 0; k < 8; k++) {
                        c.fillRect(cx - w / 3 + rr.nextInt(w * 2 / 3), H + 16 - h + rr.nextInt(h), 4 + rr.nextInt(4), 3);
                    }
                    c.setColor(new Color(110, 70, 50));
                    for (int k = 0; k < 3; k++) {
                        int rx = cx - 10 + rr.nextInt(20);
                        c.drawLine(rx, H + 16 - h + 2, rx + rr.nextInt(12) - 6, H + 16 - h - 12 - rr.nextInt(10));
                    }
                } else if (kind == 1) { // broken fence posts
                    for (int k = 0; k < 3; k++) {
                        int px = cx + k * 14;
                        int ph = 26 + rr.nextInt(24);
                        c.setColor(new Color(76, 60, 46));
                        c.fillRect(px, H + 16 - ph, 4, ph);
                        c.setColor(new Color(100, 82, 62));
                        c.fillRect(px, H + 16 - ph, 1, ph);
                    }
                    c.setColor(new Color(80, 80, 80));
                    c.drawLine(cx, H - 10, cx + 30, H - 6);
                    c.drawLine(cx, H - 22, cx + 30, H - 20);
                } else { // dead weeds
                    for (int b = 0; b < 14; b++) {
                        int bx = cx + rr.nextInt(30) - 15;
                        int bh = 14 + rr.nextInt(30);
                        double lean = (rr.nextDouble() - 0.5) * 1.2;
                        c.setColor(rr.nextBoolean() ? new Color(110, 94, 66) : new Color(84, 72, 52));
                        for (int yy = 0; yy < bh; yy++) {
                            double u = yy / (double) bh;
                            c.fillRect(bx + (int) Math.round(lean * u * u * bh * 0.5), H + 16 - yy, 1, 1);
                        }
                    }
                }
            });
        }
    }

    // =====================================================================
    // Shape helpers
    // =====================================================================

    /** Box with a lit top face and a receding right side face (2.5D). */
    private static void box(Graphics2D g, int x, int base, int w, int h, int dep, Color front, Color side, Color top) {
        int dx = dep, dy = dep / 2;
        g.setColor(side);
        poly(g, x + w, base - h, x + w + dx, base - h - dy, x + w + dx, base - dy, x + w, base);
        g.setColor(top);
        poly(g, x, base - h, x + dx, base - h - dy, x + w + dx, base - h - dy, x + w, base - h);
        g.setColor(front);
        g.fillRect(x, base - h, w, h);
    }

    /** Vertical cylinder shaded across its width; {@code lightU} is where the highlight sits (0..1). */
    private static void cylinder(Graphics2D g, int x, int top, int w, int h, Color base, double lightU) {
        for (int i = 0; i < w; i++) {
            double u = (i + 0.5) / w;
            double f = 0.62 + 0.55 * Math.max(0, 1 - Math.abs(u - lightU) * 1.7);
            g.setColor(shade(base, f));
            g.fillRect(x + i, top, 1, h);
        }
        int eh = Math.max(2, w / 3);
        g.setColor(shade(base, 1.18));
        g.fillOval(x, top - eh / 2, w, eh);
        g.setColor(shade(base, 0.7));
        g.fillOval(x + 1, top - eh / 2 + 1, Math.max(1, w - 2), Math.max(1, eh - 2));
    }

    private static void poly(Graphics2D g, int... xy) {
        int n = xy.length / 2;
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = xy[i * 2];
            ys[i] = xy[i * 2 + 1];
        }
        g.fillPolygon(xs, ys, n);
    }

    private static void disc(Graphics2D g, double cx, double cy, double r) {
        int d = Math.max(1, (int) Math.round(r * 2));
        g.fillOval((int) Math.round(cx - r), (int) Math.round(cy - r), d, d);
    }

    // =====================================================================
    // Misc helpers
    // =====================================================================

    /**
     * Keeps an image in system memory. Without this, Direct3D caches frequently drawn
     * images in VRAM and every blit into the software frame buffer becomes a slow readback.
     */
    private static BufferedImage noAccel(BufferedImage img) {
        img.setAccelerationPriority(0f);
        return img;
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static Color mix(Color a, Color b, double k) {
        k = Math.max(0, Math.min(1, k));
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }

    private static Color shade(Color c, double f) {
        return new Color(clamp((int) (c.getRed() * f)), clamp((int) (c.getGreen() * f)), clamp((int) (c.getBlue() * f)));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Fills an internal-space rectangle snapped to the frame buffer's pixel grid. */
    private static void rect(Graphics2D g, double x, double y, double w, double h) {
        int px = (int) Math.floor(x * S);
        int py = (int) Math.floor(y * S);
        g.fillRect(px, py, Math.max(1, (int) Math.round(w * S)), Math.max(1, (int) Math.round(h * S)));
    }

    /** Fills an internal-space circle of radius {@code r} centred on (cx, cy). */
    private static void oval(Graphics2D g, double cx, double cy, double r, Color c) {
        g.setColor(c);
        int d = (int) Math.round(r * 2 * S);
        g.fillOval((int) Math.round((cx - r) * S), (int) Math.round((cy - r) * S), d, d);
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    /** Deterministic hash in [0, 1). */
    private static double hash(int n) {
        long x = n * 0x9E3779B97F4A7C15L;
        x ^= x >>> 31;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 29;
        return (x >>> 11) * 0x1.0p-53;
    }
}
