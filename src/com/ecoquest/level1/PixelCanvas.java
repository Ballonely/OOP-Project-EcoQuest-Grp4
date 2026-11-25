package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * A small ARGB pixel buffer for boss sprites that are painted procedurally every frame.
 *
 * <p>Because the art is rebuilt from continuous pose values each frame (instead of being picked
 * from a handful of stored frames), animations blend into each other with no visible steps.
 * Shapes are shaded with a light from the upper left and ordered dithering, so round parts read
 * as volumes even at world-pixel size.
 */
public final class PixelCanvas {
    /** 4x4 Bayer matrix, scaled to -0.5..0.5, for ordered dithering between ramp colours. */
    private static final double[] BAYER = {
        0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5
    };
    private static final double LX = -0.55, LY = -0.68, LZ = 0.48;

    public final int w, h;
    private final BufferedImage image;
    private final int[] px;

    public PixelCanvas(int w, int h) {
        this.w = w;
        this.h = h;
        image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        px = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    public BufferedImage image() { return image; }

    public void clear() { java.util.Arrays.fill(px, 0); }

    // ------------------------------------------------------------ single pixels

    public void set(int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < w && y < h) px[y * w + x] = 0xFF000000 | rgb;
    }

    public void set(double x, double y, int rgb) { set((int) Math.round(x), (int) Math.round(y), rgb); }

    public boolean opaque(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && (px[y * w + x] >>> 24) > 0;
    }

    /** Alpha-blends {@code rgb} over the pixel; {@code alpha} is 0..255. */
    public void blend(int x, int y, int rgb, int alpha) {
        if (x < 0 || y < 0 || x >= w || y >= h || alpha <= 0) return;
        if (alpha >= 255) { px[y * w + x] = 0xFF000000 | rgb; return; }
        int dst = px[y * w + x];
        int da = dst >>> 24;
        double a = alpha / 255.0, d = da / 255.0 * (1 - a);
        double outA = a + d;
        if (outA <= 0) return;
        int r = (int) Math.round((((rgb >> 16) & 0xFF) * a + ((dst >> 16) & 0xFF) * d) / outA);
        int g = (int) Math.round((((rgb >> 8) & 0xFF) * a + ((dst >> 8) & 0xFF) * d) / outA);
        int b = (int) Math.round(((rgb & 0xFF) * a + (dst & 0xFF) * d) / outA);
        px[y * w + x] = ((int) Math.round(outA * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    public void blend(double x, double y, int rgb, int alpha) {
        blend((int) Math.round(x), (int) Math.round(y), rgb, alpha);
    }

    /** Only paints where something is already drawn (details that must stay on a body). */
    public void setOn(int x, int y, int rgb) {
        if (opaque(x, y)) px[y * w + x] = 0xFF000000 | rgb;
    }

    public void setOnPx(double x, double y, int rgb) { setOn((int) Math.round(x), (int) Math.round(y), rgb); }

    public static double dither(int x, int y) { return BAYER[(y & 3) * 4 + (x & 3)] / 16.0 - 0.47; }

    // ------------------------------------------------------------ shapes

    /**
     * A lit ellipsoid. {@code ramp[0]} is the outline colour, {@code ramp[1..]} go from shadow to
     * highlight. {@code rim} is the outline thickness in pixels (0 for none).
     */
    public void ball(double cx, double cy, double rx, double ry, int[] ramp, double rim) {
        if (rx <= 0.2 || ry <= 0.2) return;
        int x0 = (int) Math.floor(cx - rx - 1), x1 = (int) Math.ceil(cx + rx + 1);
        int y0 = (int) Math.floor(cy - ry - 1), y1 = (int) Math.ceil(cy + ry + 1);
        double edge = rim <= 0 ? 2 : 1 - rim / Math.min(rx, ry);
        int tones = ramp.length - 1;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry, d2 = nx * nx + ny * ny;
                if (d2 > 1) continue;
                if (Math.sqrt(d2) > edge) { set(x, y, ramp[0]); continue; }
                double nz = Math.sqrt(1 - d2);
                double lit = nx * LX + ny * LY + nz * LZ;          // -1..1
                double v = (lit * 0.62 + 0.42) * tones + dither(x, y) * 0.9;
                int i = 1 + Math.max(0, Math.min(tones - 1, (int) Math.floor(v)));
                set(x, y, ramp[i]);
            }
        }
    }

    /** A flat filled ellipse. */
    public void oval(double cx, double cy, double rx, double ry, int rgb) {
        int x0 = (int) Math.floor(cx - rx - 1), x1 = (int) Math.ceil(cx + rx + 1);
        int y0 = (int) Math.floor(cy - ry - 1), y1 = (int) Math.ceil(cy + ry + 1);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                double nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry;
                if (nx * nx + ny * ny <= 1) set(x, y, rgb);
            }
    }

    /** A translucent ellipse (glows, shadows). */
    public void ovalA(double cx, double cy, double rx, double ry, int rgb, int alpha) {
        int x0 = (int) Math.floor(cx - rx - 1), x1 = (int) Math.ceil(cx + rx + 1);
        int y0 = (int) Math.floor(cy - ry - 1), y1 = (int) Math.ceil(cy + ry + 1);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                double nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry, d2 = nx * nx + ny * ny;
                if (d2 <= 1) blend(x, y, rgb, (int) (alpha * (1 - d2)));
            }
    }

    /** A round-ended stroke. */
    public void capsule(double x0, double y0, double x1, double y1, double r, int rgb) {
        int minX = (int) Math.floor(Math.min(x0, x1) - r - 1), maxX = (int) Math.ceil(Math.max(x0, x1) + r + 1);
        int minY = (int) Math.floor(Math.min(y0, y1) - r - 1), maxY = (int) Math.ceil(Math.max(y0, y1) + r + 1);
        double vx = x1 - x0, vy = y1 - y0, len2 = vx * vx + vy * vy;
        for (int y = minY; y <= maxY; y++)
            for (int x = minX; x <= maxX; x++) {
                double cxp = x + 0.5, cyp = y + 0.5;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((cxp - x0) * vx + (cyp - y0) * vy) / len2));
                double dx = cxp - (x0 + vx * t), dy = cyp - (y0 + vy * t);
                if (dx * dx + dy * dy <= r * r) set(x, y, rgb);
            }
    }

    /** A shaded limb: outline, base tone, and a highlight stripe on the lit side. */
    public void limb(double x0, double y0, double x1, double y1, double r, int[] ramp) {
        capsule(x0, y0, x1, y1, r, ramp[0]);
        capsule(x0, y0, x1, y1, r - 1, ramp[2]);
        capsule(x0 + 0.5, y0 + 0.6, x1 + 0.5, y1 + 0.6, Math.max(0.6, r - 1.6), ramp[1]);
        capsule(x0 - 0.5, y0 - 0.4, x1 - 0.5, y1 - 0.4, Math.max(0.5, r - 2.1), ramp[Math.min(3, ramp.length - 1)]);
    }

    /** A one-pixel line. */
    public void line(double x0, double y0, double x1, double y1, int rgb) {
        int n = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : i / (double) n;
            set(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, rgb);
        }
    }

    /** A filled polygon (even-odd rule, sampled at pixel centres). */
    public void poly(double[] xs, double[] ys, int rgb) {
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (int i = 0; i < xs.length; i++) {
            minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]);
        }
        for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY); y++)
            for (int x = (int) Math.floor(minX); x <= (int) Math.ceil(maxX); x++) {
                double cx = x + 0.5, cy = y + 0.5;
                boolean in = false;
                for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
                    if ((ys[i] > cy) != (ys[j] > cy)
                            && cx < (xs[j] - xs[i]) * (cy - ys[i]) / (ys[j] - ys[i]) + xs[i]) in = !in;
                }
                if (in) set(x, y, rgb);
            }
    }

    /** Polygon rotated by {@code ang} radians around (ox, oy); points are relative to that pivot. */
    public void polyRot(double ox, double oy, double ang, double[] xs, double[] ys, int rgb) {
        double c = Math.cos(ang), s = Math.sin(ang);
        double[] rx = new double[xs.length], ry = new double[ys.length];
        for (int i = 0; i < xs.length; i++) {
            rx[i] = ox + xs[i] * c - ys[i] * s;
            ry[i] = oy + xs[i] * s + ys[i] * c;
        }
        poly(rx, ry, rgb);
    }

    // ------------------------------------------------------------ whole-buffer passes

    /** Draws a one-pixel outline around the silhouette (transparent pixels touching opaque ones). */
    public void outline(int rgb) {
        int[] copy = px.clone();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if ((copy[y * w + x] >>> 24) != 0) continue;
                boolean edge = (x > 0 && (copy[y * w + x - 1] >>> 24) > 160)
                        || (x < w - 1 && (copy[y * w + x + 1] >>> 24) > 160)
                        || (y > 0 && (copy[(y - 1) * w + x] >>> 24) > 160)
                        || (y < h - 1 && (copy[(y + 1) * w + x] >>> 24) > 160);
                if (edge) px[y * w + x] = 0xFF000000 | rgb;
            }
    }

    /** Pulls every opaque pixel toward a colour by {@code amount} (0..1): hit flashes, rage glow. */
    public void tint(int rgb, double amount) {
        if (amount <= 0.003) return;
        int tr = (rgb >> 16) & 0xFF, tg = (rgb >> 8) & 0xFF, tb = rgb & 0xFF;
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            if (a == 0) continue;
            int r = (px[i] >> 16) & 0xFF, g = (px[i] >> 8) & 0xFF, b = px[i] & 0xFF;
            r += (int) ((tr - r) * amount);
            g += (int) ((tg - g) * amount);
            b += (int) ((tb - b) * amount);
            px[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    /** Erases every row from {@code y} down (parts still hidden underground). */
    public void clearBelow(int y) {
        if (y < 0) y = 0;
        if (y < h) java.util.Arrays.fill(px, y * w, px.length, 0);
    }

    /** Multiplies the alpha of every pixel (fade out). */
    public void fade(double amount) {
        if (amount >= 0.999) return;
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            if (a == 0) continue;
            px[i] = ((int) (a * Math.max(0, amount)) << 24) | (px[i] & 0xFFFFFF);
        }
    }

    /**
     * Blits the canvas with its anchor (ax, ay) at world (x, y). {@code flip} runs 1 (facing right)
     * through 0 to -1 (mirrored), so turning around squeezes the sprite like a card flip.
     */
    public void drawTo(Graphics2D g, double x, double y, double ax, double ay, double flip) {
        int dw = (int) Math.round(w * flip);
        if (dw == 0) dw = flip >= 0 ? 1 : -1;
        int left = (int) Math.round(x - ax * flip);
        g.drawImage(image, left, (int) Math.round(y - ay), dw, h, null);
    }

    // ------------------------------------------------------------ easing

    public static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    /** Smoothstep. */
    public static double ease(double v) { double t = clamp01(v); return t * t * (3 - 2 * t); }
    public static double easeOut(double v) { double t = clamp01(v); return 1 - (1 - t) * (1 - t) * (1 - t); }
    public static double easeIn(double v) { double t = clamp01(v); return t * t * t; }
    /** Overshoots past 1 and settles (springy landings). */
    public static double easeOutBack(double v) {
        double t = clamp01(v), c = 1.9;
        return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2);
    }
    /** 0 outside [a, b], ramps 0..1 inside. */
    public static double span(double t, double a, double b) { return clamp01((t - a) / (b - a)); }
}
