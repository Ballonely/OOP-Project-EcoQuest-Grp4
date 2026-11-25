package com.ecoquest.level1;

import com.ecoquest.ui.PixelFont;
import com.ecoquest.util.Constants;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Drawing kit for the in-game HUD and pause menu, in the same pixel style as the main menu:
 * navy plates with a slate border (teal when highlighted), the blocky {@link PixelFont}, and
 * small pixel icons (hearts, coins, leaves, flasks).
 */
final class HudArt {
    private HudArt() {}

    static final Color NAVY = Constants.COLOR_PANEL_NAVY;
    static final Color NAVY_DEEP = new Color(15, 23, 32, 235);
    static final Color BORDER = Constants.COLOR_PANEL_BORDER;
    static final Color TEAL = Constants.COLOR_TEAL;
    static final Color TEAL_DARK = Constants.COLOR_TEAL_DARK;
    static final Color GOLD = Constants.COLOR_GOLD;
    static final Color CREAM = Constants.COLOR_CREAM;
    static final Color RED = new Color(255, 112, 103);

    private static final Color SHADOW = new Color(10, 16, 26);
    static final PixelFont.Style WHITE = flat(new Color(236, 240, 228), true);
    static final PixelFont.Style LIGHT = flat(Constants.COLOR_TEXT_LIGHT, false);
    static final PixelFont.Style DIM = flat(Constants.COLOR_TEXT_DIM, false);
    static final PixelFont.Style GOLD_TEXT = flat(Constants.COLOR_GOLD, true);
    static final PixelFont.Style TEAL_TEXT = flat(Constants.COLOR_TEAL, true);
    static final PixelFont.Style RED_TEXT = flat(RED, true);
    static final PixelFont.Style DARK_TEXT = new PixelFont.Style(new Color(24, 32, 44), new Color(24, 32, 44), null, null, 0, null, true);
    /** Big title, same as the main menu's "QUEST". */
    static final PixelFont.Style TITLE = new PixelFont.Style(
            new Color(255, 238, 118), new Color(244, 146, 36), new Color(255, 252, 200),
            new Color(140, 72, 22), 2, new Color(40, 20, 8), true);
    /** Main-menu button labels. */
    static final PixelFont.Style BUTTON = new PixelFont.Style(
            new Color(236, 240, 228), new Color(196, 214, 200), null, new Color(10, 16, 26), 1, null);
    static final PixelFont.Style BUTTON_SELECTED = new PixelFont.Style(
            new Color(255, 255, 240), new Color(214, 255, 196), null, new Color(8, 60, 44), 1, null);
    /** Dark green text for cream plates, like the main menu tagline. */
    static final PixelFont.Style TAGLINE = new PixelFont.Style(
            new Color(52, 96, 40), new Color(40, 74, 30), null, new Color(196, 182, 130), 1, null);

    private static PixelFont.Style flat(Color c, boolean bold) {
        return new PixelFont.Style(c, c, null, SHADOW, 1, null, bold);
    }

    /** Characters the pixel font can't draw are swapped or dropped. */
    static String clean(String s) {
        StringBuilder out = new StringBuilder();
        for (char ch : s.replace("₱", "P").replace("…", "...").toCharArray()) {
            char up = Character.toUpperCase(ch);
            if ((up >= 'A' && up <= 'Z') || (up >= '0' && up <= '9') || " &-.,:!?/+()[]%_@<>=".indexOf(up) >= 0)
                out.append(up);
        }
        return out.toString();
    }

    /**
     * Draws pixel text. Half scales (e.g. 1.5) are rendered at double size and drawn at half size
     * with nearest-neighbour sampling, which keeps the blocky look at in-between sizes.
     */
    static void text(Graphics2D g, String s, int x, int y, double scale, PixelFont.Style st) {
        int whole = (int) scale;
        if (whole == scale) {
            PixelFont.draw(g, clean(s), x, y, whole, st);
            return;
        }
        int doubled = (int) Math.round(scale * 2);
        BufferedImage img = PixelFont.render(clean(s), doubled, st);
        int pad = PixelFont.pad(st) * doubled / 2;
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, x - pad, y - pad, img.getWidth() / 2, img.getHeight() / 2, null);
        if (hint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
    }

    static int width(String s, double scale, PixelFont.Style st) {
        boolean bold = st == WHITE || st == GOLD_TEXT || st == TEAL_TEXT || st == RED_TEXT || st == DARK_TEXT || st == TITLE;
        int doubled = (int) Math.round(scale * 2);
        return PixelFont.width(clean(s), doubled, bold) / 2;
    }

    static int textH(double scale) { return (int) Math.round(PixelFont.GLYPH_H * scale); }

    /** Navy plate with a 2px border, like the main menu's panels and buttons. */
    static void plate(Graphics2D g, int x, int y, int w, int h, boolean highlighted) {
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(x + 2, y + 3, w, h, 6, 6);
        g.setColor(highlighted ? TEAL_DARK : NAVY);
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(new Color(255, 255, 255, 18));
        g.fillRect(x + 3, y + 2, w - 6, 2);
        g.setStroke(new BasicStroke(2));
        g.setColor(highlighted ? TEAL : BORDER);
        g.drawRoundRect(x, y, w, h, 6, 6);
        g.setStroke(new BasicStroke(1));
    }

    /** Cream plate with a teal border, like the main menu's tagline. */
    static void creamPlate(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRect(x + 2, y + 3, w, h);
        g.setColor(CREAM);
        g.fillRect(x, y, w, h);
        g.setColor(new Color(214, 204, 160));
        g.fillRect(x + 2, y + h - 5, w - 4, 3);
        g.setStroke(new BasicStroke(2));
        g.setColor(TEAL_DARK);
        g.drawRect(x, y, w, h);
        g.setStroke(new BasicStroke(1));
    }

    /** Key cap, e.g. [E], in pixel font. Returns its width. */
    static int key(Graphics2D g, String key, int x, int y) {
        return key(g, key, x, y, 2);
    }

    static int keyWidth(String key, double scale) {
        return Math.max((int) Math.round(11 * scale), width(key, scale, DARK_TEXT) + (int) Math.round(6 * scale));
    }

    static int key(Graphics2D g, String key, int x, int y, double scale) {
        int w = keyWidth(key, scale), h = (int) Math.round(11 * scale);
        g.setColor(new Color(12, 18, 28));
        g.fillRect(x, y + 2, w, h);
        g.setColor(new Color(214, 224, 234));
        g.fillRect(x, y, w, h - 1);
        g.setColor(new Color(160, 174, 192));
        g.fillRect(x, y + h - 4, w, 3);
        text(g, key, x + (w - width(key, scale, DARK_TEXT)) / 2, y + (int) Math.round(2 * scale), scale, DARK_TEXT);
        return w;
    }

    // ------------------------------------------------------------ icons (pixel grids)
    private static void grid(Graphics2D g, String[] rows, Color[] palette, String keys, int x, int y, int px) {
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length(); c++) {
                int k = keys.indexOf(rows[r].charAt(c));
                if (k < 0) continue;
                g.setColor(palette[k]);
                g.fillRect(x + c * px, y + r * px, px, px);
            }
    }

    private static final String[] HEART = {
        ".oo.oo.", "ohhrrro", "ohrrrro", "orrrrro", ".orrro.", "..oro..", "...o..."};

    static void heart(Graphics2D g, int x, int y, int px, boolean full) {
        Color[] pal = full
                ? new Color[] {new Color(40, 10, 16), new Color(236, 64, 80), new Color(255, 170, 178)}
                : new Color[] {new Color(20, 26, 36), new Color(64, 74, 90), new Color(84, 96, 114)};
        grid(g, HEART, pal, "orh", x, y, px);
    }

    private static final String[] COIN = {
        ".ooo.", "oyyyo", "oylyo", "oyyyo", "oyyyo", ".ooo."};

    static void coin(Graphics2D g, int x, int y, int px) {
        grid(g, COIN, new Color[] {new Color(150, 96, 16), new Color(250, 204, 21), new Color(255, 244, 170)},
                "oyl", x, y, px);
    }

    private static final String[] STAR = {
        "..o..", ".oyo.", "oyyyo", ".oyo.", "o.o.o"};

    static void star(Graphics2D g, int x, int y, int px) {
        grid(g, STAR, new Color[] {new Color(140, 72, 22), new Color(255, 238, 118)}, "oy", x, y, px);
    }

    private static final String[] FLASK = {
        ".ooo.", ".olo.", "oolloo", "orrrro", "orrrro", ".oooo."};

    static void flask(Graphics2D g, int x, int y, int px, boolean full) {
        Color[] pal = full
                ? new Color[] {new Color(30, 20, 26), new Color(220, 236, 240), new Color(229, 72, 77)}
                : new Color[] {new Color(20, 26, 36), new Color(64, 74, 90), new Color(52, 60, 74)};
        grid(g, FLASK, pal, "olr", x, y, px);
    }

    private static final String[] TRASH = {
        ".oooo.", "oggggo", ".olgo.", ".oglo.", ".olgo.", ".oooo."};

    static void trash(Graphics2D g, int x, int y, int px) {
        grid(g, TRASH, new Color[] {new Color(16, 40, 30), new Color(64, 170, 100), new Color(150, 230, 160)},
                "ogl", x, y, px);
    }

    static void leaf(Graphics2D g, int x, int y, int scale) {
        PixelFont.drawIcon(g, PixelFont.LEAF, x, y, scale);
    }

    static void sprout(Graphics2D g, int x, int y, int scale) {
        PixelFont.drawIcon(g, PixelFont.SPROUT, x, y, scale);
    }
}
