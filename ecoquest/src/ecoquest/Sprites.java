package ecoquest;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Pixel art drawn in code, so the game runs with zero image files.
 * Want real art? Drop assets/sprites/&lt;id&gt;.png and it replaces the drawn icon automatically.
 */
public final class Sprites {
    private Sprites() {
    }

    public static final int ICON = 16; // 14x14 art + 1px outline margin
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final Color OUTLINE = new Color(0x2a1d1d);

    // ------------------------------------------------------------------ trash icons

    public static BufferedImage trashIcon(TrashDef d) {
        return CACHE.computeIfAbsent(d.id(), k -> {
            BufferedImage custom = loadCustom(k);
            return custom != null ? custom : outline(drawIcon(d));
        });
    }

    private static BufferedImage loadCustom(String id) {
        File f = Res.find("assets/sprites/" + id + ".png").toFile();
        if (!f.exists()) return null;
        try {
            BufferedImage src = ImageIO.read(f);
            if (src == null) return null;
            BufferedImage out = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < ICON; y++) {
                for (int x = 0; x < ICON; x++) {
                    out.setRGB(x, y, src.getRGB(x * src.getWidth() / ICON, y * src.getHeight() / ICON));
                }
            }
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    private static BufferedImage outline(BufferedImage in) {
        BufferedImage out = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < ICON; y++) {
            for (int x = 0; x < ICON; x++) {
                int argb = in.getRGB(x, y);
                if ((argb >>> 24) != 0) {
                    out.setRGB(x, y, argb);
                } else if (opaque(in, x - 1, y) || opaque(in, x + 1, y) || opaque(in, x, y - 1) || opaque(in, x, y + 1)) {
                    out.setRGB(x, y, OUTLINE.getRGB());
                }
            }
        }
        return out;
    }

    private static boolean opaque(BufferedImage im, int x, int y) {
        return x >= 0 && y >= 0 && x < ICON && y < ICON && (im.getRGB(x, y) >>> 24) != 0;
    }

    private static void px(Graphics2D g, int c, int x, int y, int w, int h) {
        g.setColor(new Color(c));
        g.fillRect(x + 1, y + 1, w, h); // +1 keeps room for the outline
    }

    private static void oval(Graphics2D g, int c, int x, int y, int w, int h) {
        g.setColor(new Color(c));
        g.fillOval(x + 1, y + 1, w, h);
    }

    private static BufferedImage drawIcon(TrashDef d) {
        BufferedImage im = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        switch (d.id()) {
            // BIO
            case "fish_bone" -> {
                px(g, 0xe9e5d6, 3, 6, 9, 2);
                px(g, 0xe9e5d6, 4, 3, 1, 8);
                px(g, 0xe9e5d6, 6, 3, 1, 8);
                px(g, 0xe9e5d6, 8, 4, 1, 6);
                px(g, 0xe9e5d6, 0, 4, 3, 6);
                px(g, 0x2a1d1d, 1, 6, 1, 1);
                px(g, 0xe9e5d6, 11, 3, 3, 8);
            }
            case "fish_head" -> {
                oval(g, 0x9db8c9, 1, 3, 11, 8);
                px(g, 0xd9e6ee, 3, 4, 6, 2);
                px(g, 0xffffff, 3, 5, 2, 2);
                px(g, 0x1b1b1b, 4, 5, 1, 1);
                px(g, 0xc0392b, 8, 4, 2, 6);
                px(g, 0x6c8696, 1, 8, 3, 1);
            }
            case "meat_scraps" -> {
                oval(g, 0xc2504f, 1, 3, 12, 8);
                px(g, 0xe28a86, 3, 5, 5, 2);
                px(g, 0xf3dccc, 8, 6, 3, 3);
                px(g, 0x8f2f30, 2, 9, 8, 1);
            }
            case "apple_core" -> {
                oval(g, 0xc23b3b, 1, 1, 12, 5);
                oval(g, 0xc23b3b, 1, 8, 12, 5);
                px(g, 0xe8d9a0, 5, 4, 4, 6);
                px(g, 0x6b4a22, 7, 0, 1, 3);
                px(g, 0x3b2a18, 6, 6, 1, 1);
            }
            case "banana_peel" -> {
                px(g, 0xf2d33b, 2, 8, 10, 3);
                px(g, 0xf2d33b, 1, 4, 3, 5);
                px(g, 0xf2d33b, 10, 4, 3, 5);
                px(g, 0xc9a521, 4, 10, 6, 1);
                px(g, 0x6b4a22, 1, 3, 2, 2);
                px(g, 0x6b4a22, 11, 3, 2, 2);
            }
            case "cabbage_leaf" -> {
                oval(g, 0x7cc46a, 0, 2, 14, 10);
                px(g, 0xc7eaa0, 2, 6, 10, 1);
                px(g, 0xc7eaa0, 5, 4, 1, 5);
                px(g, 0xc7eaa0, 9, 4, 1, 5);
                px(g, 0x58a04a, 1, 10, 6, 1);
            }
            case "rotten_tomato" -> {
                oval(g, 0xa63a2e, 1, 2, 12, 10);
                px(g, 0x4a2a22, 4, 6, 2, 2);
                px(g, 0x4a2a22, 8, 8, 3, 2);
                px(g, 0x4b7a33, 5, 1, 4, 2);
                px(g, 0xd0604f, 3, 4, 2, 1);
            }
            case "egg_shell" -> {
                oval(g, 0xf2eadb, 2, 3, 10, 9);
                g.setColor(new Color(0, 0, 0, 0));
                g.setComposite(java.awt.AlphaComposite.Clear);
                for (int x = 2; x < 12; x += 2) g.fillRect(x + 1, 3, 1, 3);
                g.fillRect(4, 3, 6, 1);
                g.setComposite(java.awt.AlphaComposite.SrcOver);
                px(g, 0xd9cdb6, 3, 10, 8, 1);
                px(g, 0xf7f0a8, 5, 7, 4, 3);
            }
            case "bread_crust" -> {
                px(g, 0xc98c4a, 1, 4, 12, 7);
                px(g, 0xe2b56e, 2, 4, 10, 2);
                px(g, 0x9a6530, 1, 10, 12, 1);
                px(g, 0x7aa65a, 8, 7, 2, 2);
                px(g, 0x7aa65a, 4, 8, 1, 1);
            }
            // REC
            case "plastic_bottle" -> {
                px(g, 0x2f77c8, 6, 0, 3, 2);
                px(g, 0xcfe9f6, 6, 2, 3, 2);
                px(g, 0xbfe3f5, 4, 4, 7, 10);
                px(g, 0xffffff, 4, 7, 7, 3);
                px(g, 0x2f77c8, 4, 8, 7, 1);
                px(g, 0x8cc5e3, 10, 5, 1, 8);
            }
            case "tin_can" -> {
                px(g, 0xc6ccd2, 3, 3, 9, 10);
                px(g, 0xe7ecef, 3, 2, 9, 2);
                px(g, 0x8d959c, 3, 6, 9, 1);
                px(g, 0x8d959c, 3, 10, 9, 1);
                px(g, 0xf3f6f8, 4, 4, 1, 8);
            }
            case "glass_bottle" -> {
                px(g, 0x3f8f5a, 6, 0, 3, 5);
                px(g, 0x3f8f5a, 4, 5, 7, 9);
                px(g, 0x8fd6a6, 5, 6, 1, 6);
                px(g, 0x2a6a40, 9, 6, 1, 7);
            }
            case "glass_jar" -> {
                px(g, 0xd9a22e, 4, 1, 7, 3);
                px(g, 0xcfe9e6, 3, 4, 9, 9);
                px(g, 0xffffff, 4, 5, 1, 6);
                px(g, 0x9fc9c4, 10, 5, 1, 7);
            }
            case "cardboard" -> {
                px(g, 0xb9864f, 1, 3, 12, 10);
                px(g, 0xd9b88a, 6, 3, 2, 10);
                px(g, 0x8f6535, 1, 12, 12, 1);
                px(g, 0xd3a572, 1, 3, 12, 1);
            }
            case "newspaper" -> {
                px(g, 0xe6e1d0, 1, 1, 12, 12);
                px(g, 0x555555, 2, 2, 10, 2);
                px(g, 0x8a8a8a, 2, 5, 10, 1);
                px(g, 0x8a8a8a, 2, 7, 10, 1);
                px(g, 0x8a8a8a, 2, 9, 7, 1);
                px(g, 0xb5b0a0, 10, 9, 2, 3);
            }
            // TRASH
            case "plastic_bag" -> {
                px(g, 0xf4f4f4, 3, 4, 9, 10);
                px(g, 0xdedede, 4, 1, 2, 4);
                px(g, 0xdedede, 9, 1, 2, 4);
                px(g, 0xcfcfcf, 5, 7, 1, 5);
                px(g, 0xcfcfcf, 9, 8, 1, 4);
            }
            case "styro_tray" -> {
                px(g, 0xf6f6f2, 0, 5, 14, 6);
                px(g, 0xdcdcd4, 2, 6, 10, 3);
                px(g, 0xc4c4bb, 0, 10, 14, 1);
            }
            case "sachet" -> {
                px(g, 0xd24b3c, 3, 2, 9, 11);
                px(g, 0xa83224, 3, 2, 9, 1);
                px(g, 0xa83224, 3, 12, 9, 1);
                px(g, 0xf7e4a0, 5, 6, 5, 3);
            }
            case "straw" -> {
                for (int i = 0; i < 10; i++) px(g, i % 2 == 0 ? 0xe34b4b : 0xffffff, 2 + i, 11 - i, 2, 2);
            }
            case "cig_butt" -> {
                px(g, 0xd9923a, 2, 6, 4, 3);
                px(g, 0xf2efe6, 6, 6, 6, 3);
                px(g, 0x777777, 12, 6, 1, 3);
            }
            case "tissue" -> {
                oval(g, 0xf4f4f6, 2, 3, 10, 9);
                px(g, 0xd6d6dc, 4, 6, 2, 1);
                px(g, 0xd6d6dc, 7, 8, 3, 1);
                px(g, 0xd6d6dc, 5, 10, 2, 1);
            }
            // HAZ
            case "battery" -> {
                px(g, 0x3b3f46, 3, 3, 8, 10);
                px(g, 0xc6ccd2, 5, 1, 4, 2);
                px(g, 0xe0b13a, 3, 6, 8, 3);
                px(g, 0x1b1b1b, 6, 7, 2, 1);
                px(g, 0x7ac143, 9, 12, 2, 2);
            }
            case "bulb" -> {
                oval(g, 0xf4efc0, 2, 0, 10, 9);
                px(g, 0x9aa0a6, 5, 9, 4, 4);
                px(g, 0x6e747a, 5, 10, 4, 1);
                px(g, 0xffffff, 4, 2, 2, 2);
                px(g, 0xd9d4a0, 10, 8, 3, 1);
            }
            case "spray_can" -> {
                px(g, 0xd9503f, 4, 4, 7, 10);
                px(g, 0xffffff, 4, 7, 7, 3);
                px(g, 0xd9503f, 6, 8, 3, 1);
                px(g, 0x4a4f57, 4, 1, 7, 3);
                px(g, 0xdddddd, 6, 0, 3, 1);
            }
            case "lighter" -> {
                px(g, 0x2f7fd0, 4, 5, 7, 9);
                px(g, 0xc6ccd2, 4, 3, 7, 3);
                px(g, 0xf28c28, 6, 0, 3, 3);
                px(g, 0xf7d24a, 7, 1, 1, 2);
            }
            default -> { // any new item without a drawing: a blob in its bin colour
                oval(g, d.type().color.getRGB() & 0xffffff, 1, 2, 12, 10);
                oval(g, Tiles.shade(d.type().color, 1.35).getRGB() & 0xffffff, 3, 4, 5, 3);
            }
        }
        g.dispose();
        return im;
    }

    // ------------------------------------------------------------------ people

    private static final Map<String, BufferedImage> PEOPLE = new HashMap<>();
    private static final Color[] HAIR = {new Color(0x4a3020), new Color(0x2b2018), new Color(0x7a4a22), new Color(0x8a8a8a)};

    /** 16x24 character, feet at (x, y). facing: 0 down, 1 up, 2 left, 3 right. style: 0 hero cap, 1 hair, 2 chef, 3 straw hat. */
    public static void person(Graphics2D g, int x, int y, int facing, int frame, boolean moving,
                              Color hat, Color tunic, Color skin) {
        person(g, x, y, facing, frame, moving, hat, tunic, skin, 0);
    }

    public static void person(Graphics2D g, int x, int y, int facing, int frame, boolean moving,
                              Color hat, Color tunic, Color skin, int style) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - 8, y - 4, 16, 7);
        int f = moving ? frame % 4 : 0;
        String key = facing + "|" + f + "|" + hat.getRGB() + "|" + tunic.getRGB() + "|" + skin.getRGB() + "|" + style;
        BufferedImage im = PEOPLE.computeIfAbsent(key, k -> personImage(facing, f, hat, tunic, skin, style));
        g.drawImage(im, x - 9, y - 25, null);
    }

    private static void rect(Graphics2D g, Color c, int x, int y, int w, int h) {
        g.setColor(c);
        g.fillRect(x, y, w, h);
    }

    private static BufferedImage personImage(int facing, int f, Color hat, Color tunic, Color skin, int style) {
        BufferedImage im = new BufferedImage(18, 26, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.translate(1, 1);
        Color hair = HAIR[Math.floorMod(tunic.getRGB() + hat.getRGB(), HAIR.length)];
        Color pants = Tiles.shade(tunic, 0.55), boots = new Color(0x4a2f1c);
        int bob = (f == 1 || f == 3) ? 1 : 0;
        int l1 = f == 1 ? 2 : 0, l2 = f == 3 ? 2 : 0;
        // legs
        rect(g, pants, 4, 19 - l1, 3, 4);
        rect(g, pants, 9, 19 - l2, 3, 4);
        rect(g, boots, 4, 22 - l1, 3, 2);
        rect(g, boots, 9, 22 - l2, 3, 2);
        // torso + arms
        rect(g, tunic, 3, 11 - bob, 10, 9);
        rect(g, Tiles.shade(tunic, 1.18), 3, 11 - bob, 10, 2);
        rect(g, Tiles.shade(tunic, 0.78), 9, 13 - bob, 4, 7);
        rect(g, new Color(0x5a3a22), 3, 16 - bob, 10, 2); // belt
        rect(g, new Color(0xf2c230), 7, 16 - bob, 2, 2);
        if (style == 2) rect(g, Color.WHITE, 4, 12 - bob, 8, 8); // apron
        rect(g, Tiles.shade(tunic, 0.9), 1, 12 - bob, 2, 5);
        rect(g, Tiles.shade(tunic, 0.9), 13, 12 - bob, 2, 5);
        rect(g, skin, 1, 17 - bob, 2, 2);
        rect(g, skin, 13, 17 - bob, 2, 2);
        // head
        rect(g, skin, 4, 6 - bob, 8, 7);
        rect(g, Tiles.shade(skin, 0.88), 4, 12 - bob, 8, 1);
        if (facing == 1) {
            rect(g, hair, 4, 6 - bob, 8, 7);
        } else {
            rect(g, hair, 3, 6 - bob, 1, 5);
            rect(g, hair, 12, 6 - bob, 1, 5);
            Color eye = new Color(0x241818);
            if (facing == 0) {
                rect(g, eye, 5, 9 - bob, 2, 2);
                rect(g, eye, 9, 9 - bob, 2, 2);
                rect(g, new Color(0xe89080), 4, 11 - bob, 1, 1);
                rect(g, new Color(0xe89080), 11, 11 - bob, 1, 1);
                rect(g, Tiles.shade(skin, 0.7), 7, 12 - bob, 2, 1);
            } else {
                int ex = facing == 2 ? 5 : 9;
                rect(g, eye, ex, 9 - bob, 2, 2);
                rect(g, Tiles.shade(skin, 0.8), facing == 2 ? 4 : 11, 10 - bob, 1, 1);
            }
        }
        // headwear
        switch (style) {
            case 1 -> {
                rect(g, hair, 3, 4 - bob, 10, 4);
                rect(g, Tiles.shade(hair, 1.3), 5, 4 - bob, 4, 1);
                rect(g, hat, 3, 7 - bob, 10, 1); // headband
            }
            case 2 -> {
                rect(g, Color.WHITE, 4, 1 - bob, 8, 6);
                rect(g, Color.WHITE, 3, 2 - bob, 10, 3);
                rect(g, new Color(0xd8d8e0), 4, 6 - bob, 8, 1);
                rect(g, new Color(0xd8d8e0), 10, 2 - bob, 2, 4);
            }
            case 3 -> {
                rect(g, new Color(0xe3c26a), 1, 5 - bob, 14, 2);
                rect(g, new Color(0xf0d587), 4, 2 - bob, 8, 4);
                rect(g, new Color(0xb4452f), 4, 5 - bob, 8, 1);
                rect(g, new Color(0xb89348), 1, 6 - bob, 14, 1);
            }
            default -> { // pointed adventurer cap
                rect(g, hat, 3, 3 - bob, 10, 4);
                rect(g, hat, 5, 1 - bob, 6, 3);
                int tipX = facing == 3 ? 1 : facing == 2 ? 12 : facing == 0 ? 11 : 2;
                rect(g, hat, tipX, 1 - bob, 3, 3);
                rect(g, hat, tipX + (tipX > 8 ? 1 : -1), 3 - bob, 2, 2);
                rect(g, Tiles.shade(hat, 1.3), 5, 1 - bob, 4, 1);
                rect(g, Tiles.shade(hat, 0.7), 3, 6 - bob, 10, 1);
            }
        }
        g.dispose();
        Tiles.outline(im, 0x2a1a18);
        return im;
    }

    // ------------------------------------------------------------------ bins

    private static final Map<String, BufferedImage> BINS = new HashMap<>();

    public static void bin(Graphics2D g, Rectangle r, WasteType t, boolean open, int flash) {
        g.setColor(new Color(0, 0, 0, 80));
        g.fillOval(r.x - 3, r.y + r.height - 6, r.width + 8, 10);
        String key = t + "|" + r.width + "|" + r.height + "|" + open + "|" + flash;
        BufferedImage im = BINS.computeIfAbsent(key, k -> binImage(r.width, r.height, t, open, flash));
        g.drawImage(im, r.x - 4, r.y - 10, null);
    }

    private static BufferedImage binImage(int w, int h, WasteType t, boolean open, int flash) {
        BufferedImage im = new BufferedImage(w + 8, h + 14, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.translate(4, 10);
        Color body = t.color;
        if (t == WasteType.BIO) body = new Color(0x3aa653);
        if (t == WasteType.REC) body = new Color(0x3f8fe0);
        if (t == WasteType.TRASH) body = new Color(0x4b4d58);
        if (t == WasteType.HAZ) body = new Color(0xd93a3a);
        if (flash > 0) body = Tiles.shade(body, 1.45);
        else if (flash < 0) body = new Color(0xff6a6a);
        // body (slightly tapered)
        java.awt.Polygon p = new java.awt.Polygon(new int[]{0, w, w - 3, 3}, new int[]{8, 8, h, h}, 4);
        g.setColor(Tiles.shade(body, 0.72));
        g.fillPolygon(p);
        g.setColor(body);
        g.fillPolygon(new java.awt.Polygon(new int[]{0, w - 7, w - 9, 3}, new int[]{8, 8, h, h}, 4));
        g.setColor(Tiles.shade(body, 1.3));
        g.fillRect(2, 11, 2, h - 17);
        g.setColor(new Color(0, 0, 0, 40));
        for (int x = 9; x < w - 6; x += 7) g.fillRect(x, 12, 1, h - 20);
        // wheels
        rect(g, new Color(0x222228), 3, h - 1, 5, 3);
        rect(g, new Color(0x222228), w - 8, h - 1, 5, 3);
        // opening
        rect(g, new Color(0x15151a), 1, 8, w - 2, 3);
        // lid
        int ly = open ? -6 : 1;
        rect(g, Tiles.shade(body, 0.55), -2, ly + 6, w + 4, 4);
        rect(g, Tiles.shade(body, 0.95), -2, ly + 1, w + 4, 6);
        rect(g, Tiles.shade(body, 1.3), -2, ly + 1, w + 4, 1);
        rect(g, Tiles.shade(body, 1.35), w / 2 - 5, ly - 2, 10, 3);
        // symbol plate
        int cx = w / 2 - 1, cy = (int) (h * 0.42);
        g.setColor(Color.WHITE);
        switch (t) {
            case BIO -> {
                g.fillOval(cx - 5, cy - 4, 10, 8);
                g.setColor(body);
                g.drawLine(cx - 4, cy + 3, cx + 3, cy - 3);
            }
            case REC -> {
                g.setStroke(new java.awt.BasicStroke(2f));
                g.drawPolygon(new int[]{cx, cx - 6, cx + 6}, new int[]{cy - 5, cy + 5, cy + 5}, 3);
                g.setColor(body);
                g.fillRect(cx - 1, cy + 3, 3, 3);
                g.fillRect(cx - 5, cy - 1, 2, 2);
                g.fillRect(cx + 4, cy - 1, 2, 2);
            }
            case TRASH -> {
                g.fillRect(cx - 4, cy - 3, 9, 9);
                g.fillRect(cx - 5, cy - 5, 11, 2);
                g.setColor(Tiles.shade(body, 0.8));
                g.fillRect(cx - 2, cy - 1, 1, 5);
                g.fillRect(cx + 1, cy - 1, 1, 5);
            }
            case HAZ -> {
                g.setColor(new Color(0xf7d23a));
                g.fillPolygon(new int[]{cx, cx - 7, cx + 7}, new int[]{cy - 6, cy + 6, cy + 6}, 3);
                g.setColor(new Color(0x2a1a18));
                g.fillRect(cx - 1, cy - 2, 2, 5);
                g.fillRect(cx - 1, cy + 4, 2, 1);
            }
        }
        g.dispose();
        Tiles.outline(im, open ? 0xffffff : 0x2a1a18);
        return im;
    }


    // ------------------------------------------------------------------ hand cart

    private static final Map<Integer, BufferedImage> CARTS = new HashMap<>();

    /** Wooden hand cart loaded with produce. dir > 0 means moving right. (x, y) is the middle of the wheels' ground line. */
    public static void cart(Graphics2D g, int x, int y, int dir) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(x - 21, y - 4, 42, 9);
        BufferedImage im = CARTS.computeIfAbsent(dir, d -> cartImage(d));
        g.drawImage(im, x - 22, y - 26, null);
    }

    private static BufferedImage cartImage(int dir) {
        BufferedImage im = new BufferedImage(44, 28, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        int f = dir > 0 ? 0 : 1;
        // handle
        rect(g, new Color(0x5a3820), f == 0 ? 33 : 1, 14, 10, 2);
        // bed
        rect(g, new Color(0x5a3820), 5, 12, 32, 9);
        rect(g, new Color(0x8f5c34), 5, 12, 32, 7);
        rect(g, new Color(0xc9945a), 5, 12, 32, 2);
        for (int i = 0; i < 4; i++) rect(g, new Color(0x5a3820), 12 + i * 8, 12, 1, 8);
        // produce
        int[] cols = {0xe74c3c, 0xf1c40f, 0x2ecc71, 0xe67e22, 0xe74c3c};
        for (int i = 0; i < 5; i++) {
            rect(g, new Color(0x2a1a18), 6 + i * 6, 5 + (i % 2), 7, 8);
            rect(g, new Color(cols[i]), 7 + i * 6, 6 + (i % 2), 5, 6);
            rect(g, Tiles.shade(new Color(cols[i]), 1.35), 7 + i * 6, 6 + (i % 2), 3, 2);
        }
        // wheels
        for (int wx : new int[]{9, 28}) {
            rect(g, new Color(0x2a1a18), wx, 17, 9, 9);
            rect(g, new Color(0x4a4a56), wx + 1, 18, 7, 7);
            rect(g, new Color(0x9a9aa6), wx + 3, 20, 3, 3);
        }
        g.dispose();
        Tiles.outline(im, Tiles.OUT);
        return im;
    }

    // ------------------------------------------------------------------ boss

    private static final Map<String, BufferedImage> KING = new HashMap<>();

    /** The Waste King: a crowned mound of garbage that shrinks as it is cleaned up. */
    public static void wasteKing(Graphics2D g, int cx, int baseY, double size, double shake, double t) {
        boolean angry = shake > 0.05;
        BufferedImage im = KING.computeIfAbsent(String.valueOf(angry), k -> kingImage(angry));
        int w = (int) (im.getWidth() * size), h = (int) (im.getHeight() * size);
        int sx = (int) (Math.sin(t * 60) * shake * 3);
        int bob = (int) Math.round(Math.sin(t * 2.2) * 1.5);
        g.setColor(new Color(0, 0, 0, 80));
        g.fillOval(cx - w / 2 - 2, baseY - 10, w + 4, 18);
        g.drawImage(im, cx - w / 2 + sx, baseY - h + bob, w, h, null);
    }

    private static BufferedImage kingImage(boolean angry) {
        BufferedImage im = new BufferedImage(90, 76, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        // mound
        g.setColor(new Color(0x3d4a28));
        g.fillOval(2, 26, 86, 48);
        g.setColor(new Color(0x56693a));
        g.fillOval(6, 24, 78, 42);
        g.setColor(new Color(0x6f8548));
        g.fillOval(14, 24, 52, 24);
        // trash bits poking out
        int[] cols = {0xe34b4b, 0x3b8fd9, 0xf2d33b, 0xf4f4f4, 0xc98c4a, 0x7cc46a, 0xb9864f, 0x9db8c9, 0x8e44ad};
        for (int i = 0; i < 34; i++) {
            double a = Math.floorMod(Tiles.hash(i, 5), 628) / 100.0;
            double d = 0.55 + Math.floorMod(Tiles.hash(i, 8), 45) / 100.0;
            int x = 45 + (int) (Math.cos(a) * 40 * d), y = 52 + (int) (Math.sin(a) * 20 * d);
            if (y < 30) continue;
            int c = cols[i % cols.length];
            int bw = 4 + Math.floorMod(Tiles.hash(i, 2), 5), bh = 3 + Math.floorMod(Tiles.hash(i, 4), 3);
            g.setColor(new Color(0x2a1a18));
            g.fillRect(x - 1, y - 1, bw + 2, bh + 2);
            g.setColor(new Color(c));
            g.fillRect(x, y, bw, bh);
            g.setColor(Tiles.shade(new Color(c), 1.3));
            g.fillRect(x, y, bw, 1);
        }
        // eyes
        g.setColor(new Color(0x2a1a18));
        g.fillOval(20, 30, 21, 17);
        g.fillOval(49, 30, 21, 17);
        g.setColor(new Color(0xfff4c0));
        g.fillOval(22, 32, 17, 13);
        g.fillOval(51, 32, 17, 13);
        g.setColor(angry ? new Color(0xe03030) : new Color(0x222222));
        g.fillRect(28, 35, 6, 7);
        g.fillRect(56, 35, 6, 7);
        g.setColor(Color.WHITE);
        g.fillRect(29, 36, 2, 2);
        g.fillRect(57, 36, 2, 2);
        // brows
        g.setColor(new Color(0x2a1a18));
        for (int i = 0; i < 4; i++) {
            g.fillRect(21 + i * 5, 28 + (angry ? 3 - i : i / 2), 6, 3);
            g.fillRect(69 - i * 5 - 5, 28 + (angry ? 3 - i : i / 2), 6, 3);
        }
        // mouth
        g.setColor(new Color(0x2a1a18));
        g.fillRoundRect(28, 52, 34, angry ? 15 : 11, 8, 8);
        g.setColor(new Color(0x8a1f2c));
        g.fillRect(31, 58, 28, angry ? 7 : 3);
        g.setColor(Color.WHITE);
        for (int i = 0; i < 5; i++) g.fillRect(30 + i * 6, 53, 4, 4);
        // crown
        g.setColor(new Color(0x2a1a18));
        g.fillRect(25, 11, 40, 14);
        for (int i = 0; i < 4; i++) g.fillPolygon(new int[]{25 + i * 12, 31 + i * 12, 37 + i * 12}, new int[]{12, 1, 12}, 3);
        g.setColor(new Color(0xf2c230));
        g.fillRect(27, 13, 36, 10);
        for (int i = 0; i < 4; i++) g.fillPolygon(new int[]{27 + i * 12, 31 + i * 12, 35 + i * 12}, new int[]{13, 4, 13}, 3);
        g.setColor(new Color(0xfff0a0));
        g.fillRect(27, 13, 36, 2);
        g.setColor(new Color(0xd63a3a));
        g.fillRect(43, 16, 5, 5);
        g.setColor(new Color(0x3b8fd9));
        g.fillRect(32, 17, 4, 4);
        g.fillRect(54, 17, 4, 4);
        g.dispose();
        return im;
    }
}
