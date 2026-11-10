package ecoquest;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** Reusable pixel-art painters for map backgrounds: ground textures, roofs, trees, outlines and shadows. */
final class Tiles {
    private Tiles() {
    }

    static int hash(int a, int b) {
        int h = a * 73856093 ^ b * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h;
    }

    static Color shade(Color c, double f) {
        return new Color(clamp(c.getRed() * f), clamp(c.getGreen() * f), clamp(c.getBlue() * f));
    }

    static Color adj(Color c, int d) {
        return new Color(clamp(c.getRed() + d), clamp(c.getGreen() + d), clamp(c.getBlue() + d));
    }

    static Color mix(Color a, Color b, double t) {
        return new Color(clamp(a.getRed() * (1 - t) + b.getRed() * t), clamp(a.getGreen() * (1 - t) + b.getGreen() * t),
                clamp(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    private static void put(BufferedImage img, int x, int y, Color c) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, c.getRGB());
    }

    // ------------------------------------------------------------------ ground

    /** Red brick paving with per-brick tint, bevels and grain. */
    static void brick(BufferedImage img, Rectangle r, Color base, Color mortar) {
        double[] tone = {0.86, 0.94, 1.0, 1.05, 1.12, 0.9, 1.0};
        for (int y = r.y; y < r.y + r.height; y++) {
            for (int x = r.x; x < r.x + r.width; x++) {
                int row = Math.floorDiv(y, 8);
                int xo = x + (row & 1) * 8;
                int bx = Math.floorDiv(xo, 16);
                int mx = Math.floorMod(xo, 16), my = Math.floorMod(y, 8);
                if (mx == 0 || my == 0) {
                    put(img, x, y, Math.floorMod(hash(x, y), 5) == 0 ? shade(mortar, 1.25) : mortar);
                    continue;
                }
                double f = tone[Math.floorMod(hash(bx, row), tone.length)];
                if (my == 1 || mx == 1) f *= 1.1;
                if (my == 7 || mx == 15) f *= 0.88;
                Color c = shade(base, f);
                if (Math.floorMod(hash(bx, row * 3), 9) == 0) c = mix(c, new Color(0xc9774f), 0.25); // sun-faded
                put(img, x, y, adj(c, Math.floorMod(hash(x * 3, y * 5), 7) - 3));
            }
        }
    }

    /** Light cobblestones with irregular widths, bevels and the odd mossy stone. */
    static void cobble(BufferedImage img, Rectangle r, Color base, Color mortar) {
        final int rowH = 13;
        for (int row = Math.floorDiv(r.y, rowH); row * rowH < r.y + r.height; row++) {
            int x0 = -(Math.floorMod(hash(row, 1), 18)) - 20;
            int k = 0;
            while (x0 < r.x + r.width) {
                int w = 15 + Math.floorMod(hash(row, k + 7), 9);
                int x1 = x0 + w;
                int id = hash(row, x0);
                double f = new double[]{0.9, 0.96, 1.0, 1.04, 1.08}[Math.floorMod(id, 5)];
                Color stone = shade(base, f);
                if (Math.floorMod(id >> 5, 11) == 0) stone = mix(stone, new Color(0x6f8f55), 0.28);
                for (int y = Math.max(r.y, row * rowH); y < Math.min(r.y + r.height, row * rowH + rowH); y++) {
                    int ly = y - row * rowH;
                    for (int x = Math.max(r.x, x0); x < Math.min(r.x + r.width, x1); x++) {
                        int lx = x - x0;
                        boolean corner = (lx <= 1 && ly <= 1) || (lx >= w - 2 && ly <= 1)
                                || (lx <= 1 && ly >= rowH - 2) || (lx >= w - 2 && ly >= rowH - 2);
                        if (lx == 0 || ly == 0 || corner) {
                            put(img, x, y, mortar);
                            continue;
                        }
                        double g = 1.0;
                        if (ly == 1 || lx == 1) g = 1.12;
                        if (ly == rowH - 1 || lx == w - 1) g = 0.84;
                        put(img, x, y, adj(shade(stone, g), Math.floorMod(hash(x * 7, y * 3), 7) - 3));
                    }
                }
                x0 = x1;
                k++;
            }
        }
    }

    static void grass(BufferedImage img, Rectangle r) {
        Color base = new Color(0x4f9040);
        for (int y = r.y; y < r.y + r.height; y++) {
            for (int x = r.x; x < r.x + r.width; x++) {
                int h = Math.floorMod(hash(x / 2, y / 2), 17);
                Color c = h == 0 ? shade(base, 0.74) : h == 1 ? shade(base, 0.86) : h == 2 ? shade(base, 1.18)
                        : h == 3 ? new Color(0x7cc25a) : base;
                put(img, x, y, c);
            }
        }
    }


    // ------------------------------------------------------------------ object layers (used by the level painters)

    /** Margin around an object's layer so outlines and shadows are not clipped. */
    static final int PAD = 8;
    static final Color OUT = new Color(0x2a1a18);

    interface Painter {
        void paint(Graphics2D g, int w, int h);
    }

    /** Paints an object on its own transparent layer (local coords 0..w, 0..h) with a PAD margin. */
    static BufferedImage layer(int w, int h, Painter p) {
        BufferedImage im = new BufferedImage(w + PAD * 2, h + PAD * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.translate(PAD, PAD);
        p.paint(g, w, h);
        g.dispose();
        return im;
    }

    static BufferedImage outline(BufferedImage im, Color c) {
        int w = im.getWidth(), h = im.getHeight();
        int[] src = im.getRGB(0, 0, w, h, null, 0, w);
        int[] out = src.clone();
        int o = 0xff000000 | c.getRGB();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if ((src[i] >>> 24) > 100) continue;
                if ((x > 0 && (src[i - 1] >>> 24) > 100) || (x < w - 1 && (src[i + 1] >>> 24) > 100)
                        || (y > 0 && (src[i - w] >>> 24) > 100) || (y < h - 1 && (src[i + w] >>> 24) > 100)) {
                    out[i] = o;
                }
            }
        }
        im.setRGB(0, 0, w, h, out, 0, w);
        return im;
    }

    /** Draws a soft drop shadow of the layer (offset sx, sy; pct darkness) and then the layer itself. */
    static void stamp(Graphics2D g, BufferedImage im, int x, int y, int sx, int sy, int pct) {
        int w = im.getWidth(), h = im.getHeight();
        int[] src = im.getRGB(0, 0, w, h, null, 0, w);
        int a = Math.max(0, Math.min(255, pct * 255 / 100));
        int[] sh = new int[w * h];
        for (int i = 0; i < src.length; i++) if ((src[i] >>> 24) > 100) sh[i] = (a << 24);
        BufferedImage shadow = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        shadow.setRGB(0, 0, w, h, sh, 0, w);
        g.drawImage(shadow, x + sx, y + sy, null);
        g.drawImage(im, x, y, null);
    }

    /** One call: paint, outline, shadow and place an object whose top-left is (x, y). */
    static void obj(Graphics2D g, int x, int y, int w, int h, int sx, int sy, int pct, Painter p) {
        BufferedImage im = outline(layer(w, h, p), OUT);
        stamp(g, im, x - PAD, y - PAD, sx, sy, pct);
    }

    /** Darkens the corners a little and warms the centre, like the soft lighting in the reference art. */
    static void vignette(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = (x - w / 2.0) / (w / 2.0), dy = (y - h / 2.0) / (h / 2.0);
                double d = Math.min(1.0, Math.sqrt(dx * dx * 0.7 + dy * dy * 0.9));
                double f = 1.04 - 0.22 * d * d;
                int c = img.getRGB(x, y);
                int r = (int) Math.min(255, (c >> 16 & 255) * (f + 0.015)), g = (int) Math.min(255, (c >> 8 & 255) * f),
                        b = (int) Math.min(255, (c & 255) * (f - 0.015));
                img.setRGB(x, y, 0xff000000 | r << 16 | g << 8 | b);
            }
        }
    }

    // ------------------------------------------------------------------ objects (drawn on transparent layers)

    static BufferedImage layer() {
        return new BufferedImage(Level.W, Level.H, BufferedImage.TYPE_INT_ARGB);
    }

    /** Adds a 1px dark outline around everything painted on the layer. */
    static void outline(BufferedImage im, int rgb) {
        int w = im.getWidth(), h = im.getHeight();
        int[] src = im.getRGB(0, 0, w, h, null, 0, w);
        int[] out = src.clone();
        int o = 0xff000000 | rgb;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if ((src[i] >>> 24) > 100) continue;
                if ((x > 0 && (src[i - 1] >>> 24) > 100) || (x < w - 1 && (src[i + 1] >>> 24) > 100)
                        || (y > 0 && (src[i - w] >>> 24) > 100) || (y < h - 1 && (src[i + w] >>> 24) > 100)) {
                    out[i] = o;
                }
            }
        }
        im.setRGB(0, 0, w, h, out, 0, w);
    }

    /** Casts a soft shadow of the object onto the ground, outlines the object, then draws it. */
    static void place(BufferedImage ground, BufferedImage obj, int sx, int sy, int shadowPct) {
        int w = ground.getWidth(), h = ground.getHeight();
        int[] o = obj.getRGB(0, 0, w, h, null, 0, w);
        int[] g = ground.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((o[y * w + x] >>> 24) < 100) continue;
                int tx = x + sx, ty = y + sy;
                if (tx < 0 || ty < 0 || tx >= w || ty >= h) continue;
                int c = g[ty * w + tx];
                int r = (c >> 16 & 255) * (100 - shadowPct) / 100, gg = (c >> 8 & 255) * (100 - shadowPct) / 100,
                        b = (c & 255) * (100 - shadowPct) / 100;
                g[ty * w + tx] = 0xff000000 | r << 16 | gg << 8 | b;
            }
        }
        ground.setRGB(0, 0, w, h, g, 0, w);
        outline(obj, 0x2a1a18);
        Graphics2D gg = ground.createGraphics();
        gg.drawImage(obj, 0, 0, null);
        gg.dispose();
    }

    /** Darkens the ground in a soft band, used under walls and awnings. */
    static void ambient(BufferedImage ground, Rectangle band, int maxAlpha, boolean fadeDown) {
        for (int y = band.y; y < band.y + band.height; y++) {
            double t = (y - band.y) / (double) band.height;
            double a = (fadeDown ? 1 - t : t) * maxAlpha / 100.0;
            for (int x = band.x; x < band.x + band.width; x++) {
                if (x < 0 || y < 0 || x >= ground.getWidth() || y >= ground.getHeight()) continue;
                int c = ground.getRGB(x, y);
                ground.setRGB(x, y, 0xff000000 | (int) ((c >> 16 & 255) * (1 - a)) << 16
                        | (int) ((c >> 8 & 255) * (1 - a)) << 8 | (int) ((c & 255) * (1 - a)));
            }
        }
    }

    /** Terracotta shingles with rounded tabs, ridge highlight and dark eave. */
    static void roof(Graphics2D g, int x, int y, int w, int h) {
        roof(g, new Rectangle(x, y, w, h));
    }

    static void roof(Graphics2D g, Rectangle r) {
        java.awt.Shape oldClip = g.getClip();
        g.setClip(r);
        Color base = new Color(0xc2502f);
        g.setColor(shade(base, 0.8));
        g.fillRect(r.x, r.y, r.width, r.height);
        int row = 0;
        for (int y = r.y - 4; y < r.y + r.height; y += 7, row++) {
            int off = (row % 2) * 7;
            for (int x = r.x - 14 + off; x < r.x + r.width; x += 14) {
                int h = Math.floorMod(hash(x, y), 5);
                Color c = shade(base, 0.92 + h * 0.04);
                g.setColor(shade(c, 0.7));
                g.fillRoundRect(x, y + 1, 14, 10, 8, 8);
                g.setColor(c);
                g.fillRoundRect(x, y, 14, 9, 8, 8);
                g.setColor(shade(c, 1.2));
                g.fillRect(x + 3, y + 1, 8, 1);
            }
        }
        g.setColor(shade(base, 1.3));
        g.fillRect(r.x, r.y, r.width, 2);
        g.setColor(shade(base, 0.5));
        g.fillRect(r.x, r.y + r.height - 3, r.width, 3);
        g.setClip(oldClip);
    }

    /** A round, textured tree crown with a trunk. Painted on an object layer. */
    static void tree(Graphics2D g, int cx, int cy, int radius) {
        Color dark = new Color(0x245c2e), mid = new Color(0x3b8a3c), light = new Color(0x69b84f), hi = new Color(0x9ad86a);
        // trunk
        g.setColor(new Color(0x5b3b22));
        g.fillRect(cx - 4, cy + radius / 2, 8, radius / 2 + 6);
        g.setColor(new Color(0x7b5232));
        g.fillRect(cx - 4, cy + radius / 2, 3, radius / 2 + 6);
        g.setColor(new Color(0x5b3b22));
        g.fillRect(cx - 7, cy + radius + 4, 4, 3);
        g.fillRect(cx + 3, cy + radius + 4, 4, 3);
        int[][] blobs = {{0, 0, 10}, {-9, 3, 8}, {9, 3, 8}, {-4, -8, 8}, {5, -7, 8}, {0, 7, 8}};
        for (int[] b : blobs) {
            int rr = radius * b[2] / 10;
            g.setColor(dark);
            g.fillOval(cx + b[0] * radius / 14 - rr, cy + b[1] * radius / 14 - rr, rr * 2, rr * 2 - 2);
        }
        for (int[] b : blobs) {
            int rr = radius * b[2] / 10 - 3;
            g.setColor(mid);
            g.fillOval(cx + b[0] * radius / 14 - rr - 1, cy + b[1] * radius / 14 - rr - 2, rr * 2, rr * 2 - 3);
        }
        for (int[] b : blobs) {
            int rr = radius * b[2] / 10 / 2;
            g.setColor(light);
            g.fillOval(cx + b[0] * radius / 14 - rr - 2, cy + b[1] * radius / 14 - rr - 4, rr * 2, rr + 2);
        }
        // leaf specks
        for (int i = 0; i < 46; i++) {
            double a = Math.floorMod(hash(i, cx), 628) / 100.0, d = Math.floorMod(hash(i, cy), 100) / 100.0 * radius * 0.95;
            int x = cx + (int) (Math.cos(a) * d), y = cy + (int) (Math.sin(a) * d * 0.9) - 2;
            g.setColor(i % 3 == 0 ? hi : i % 3 == 1 ? dark : light);
            g.fillRect(x, y, 3, 2);
        }
    }
}
