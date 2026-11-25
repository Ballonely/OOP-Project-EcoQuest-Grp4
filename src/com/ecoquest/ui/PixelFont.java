package com.ecoquest.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Chunky 5x7 pixel-art font drawn in code, plus a few small eco icons.
 *
 * <p>Text is rendered once into a cached image with an optional outline, a
 * 2.5D extrusion (the glyph repeated down and to the right in a darker
 * colour) and a vertical face gradient, so drawing it every frame is a single
 * image blit. No font files are needed.
 */
public final class PixelFont {

    private PixelFont() {
    }

    /** Glyph rows, 5 columns each; '#' is a filled pixel. */
    private static final Map<Character, String[]> GLYPHS = new HashMap<>();

    static {
        g('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        g('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        g('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        g('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        g('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        g('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####");
        g('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('I', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####");
        g('J', "..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##..");
        g('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        g('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        g('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        g('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#");
        g('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        g('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", ".###.", "...##");
        g('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        g('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        g('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        g('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        g('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#");
        g('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        g('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        g('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
        g('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
        g('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
        g('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
        g('3', "####.", "....#", "....#", ".###.", "....#", "....#", "####.");
        g('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        g('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        g('6', ".###.", "#....", "#....", "####.", "#...#", "#...#", ".###.");
        g('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
        g('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        g('9', ".###.", "#...#", "#...#", ".####", "....#", "....#", ".###.");
        g(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....");
        g('&', ".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#");
        g('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....");
        g('.', ".....", ".....", ".....", ".....", ".....", ".##..", ".##..");
        g(',', ".....", ".....", ".....", ".....", ".##..", "..#..", ".#...");
        g(':', ".....", ".##..", ".##..", ".....", ".##..", ".##..", ".....");
        g('!', "..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#..");
        g('?', ".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#..");
        g('\'', "..#..", "..#..", ".#...", ".....", ".....", ".....", ".....");
        g('/', "....#", "....#", "...#.", "..#..", ".#...", "#....", "#....");
        g('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....");
        g('▶', "#....", "##...", "###..", "####.", "###..", "##...", "#....");
        g('★', "..#..", "..#..", "#####", ".###.", ".###.", "##.##", "#...#");
        g('•', ".....", ".....", ".###.", ".###.", ".###.", ".....", ".....");
        g('₱', "####.", "#...#", "#####", "####.", "#....", "#....", "#....");
        g('(', "...#.", "..#..", ".#...", ".#...", ".#...", "..#..", "...#.");
        g(')', ".#...", "..#..", "...#.", "...#.", "...#.", "..#..", ".#...");
        g('[', ".###.", ".#...", ".#...", ".#...", ".#...", ".#...", ".###.");
        g(']', ".###.", "...#.", "...#.", "...#.", "...#.", "...#.", ".###.");
        g('%', "##...", "##..#", "...#.", "..#..", ".#...", "#..##", "...##");
        g('_', ".....", ".....", ".....", ".....", ".....", ".....", "#####");
        g('@', ".###.", "#...#", "#.###", "#.#.#", "#.###", "#....", ".###.");
        g('>', "#....", ".#...", "..#..", "...#.", "..#..", ".#...", "#....");
        g('<', "....#", "...#.", "..#..", ".#...", "..#..", "...#.", "....#");
        g('=', ".....", ".....", "#####", ".....", "#####", ".....", ".....");
        g('●', ".....", ".###.", "#####", "#####", "#####", ".###.", ".....");
        g('♪', "..###", "..#.#", "..#..", "..#..", ".##..", "###..", ".#...");
    }

    private static void g(char c, String... rows) {
        GLYPHS.put(c, rows);
    }

    public static final int GLYPH_W = 5;
    public static final int GLYPH_H = 7;
    private static final int ADVANCE = GLYPH_W + 1;

    /** Look of a piece of text. */
    public static final class Style {
        final Color top;
        final Color bottom;
        final Color highlight;  // top edge of each stroke, may be null
        final Color extrude;    // 2.5D depth colour, may be null
        final int depth;        // extrusion depth in font pixels
        final Color outline;    // may be null
        final boolean bold;     // doubles vertical stems (one extra column per glyph)

        public Style(Color top, Color bottom, Color highlight, Color extrude, int depth, Color outline) {
            this(top, bottom, highlight, extrude, depth, outline, false);
        }

        public Style(Color top, Color bottom, Color highlight, Color extrude, int depth, Color outline, boolean bold) {
            this.bold = bold;
            this.top = top;
            this.bottom = bottom;
            this.highlight = highlight;
            this.extrude = extrude;
            this.depth = depth;
            this.outline = outline;
        }
    }

    /** Width in screen pixels of {@code s} at {@code scale}, without outline/depth padding. */
    public static int width(String s, int scale) {
        return width(s, scale, false);
    }

    public static int width(String s, int scale, boolean bold) {
        int adv = ADVANCE + (bold ? 1 : 0);
        return s.isEmpty() ? 0 : (s.length() * adv - 1) * scale;
    }

    /** Padding (font pixels) added on the left/top of a rendered image. */
    public static int pad(Style st) {
        return st.outline != null ? 1 : 0;
    }

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    /**
     * Renders {@code s} into a cached image. The glyph area starts at
     * ({@link #pad} * scale, {@link #pad} * scale) inside the image.
     */
    public static BufferedImage render(String s, int scale, Style st) {
        String key = s + '|' + scale + '|' + System.identityHashCode(st);
        BufferedImage cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int p = pad(st);
        int adv = ADVANCE + (st.bold ? 1 : 0);
        int gw = Math.max(1, s.length() * adv - 1);
        int uw = gw + p * 2 + st.depth;
        int uh = GLYPH_H + p * 2 + st.depth;
        boolean[][] face = new boolean[uh][uw];
        for (int i = 0; i < s.length(); i++) {
            String[] rows = GLYPHS.getOrDefault(Character.toUpperCase(s.charAt(i)), GLYPHS.get('?'));
            for (int r = 0; r < GLYPH_H; r++) {
                for (int c = 0; c < GLYPH_W; c++) {
                    if (rows[r].charAt(c) == '#') {
                        face[r + p][i * adv + c + p] = true;
                        if (st.bold) {
                            face[r + p][i * adv + c + p + 1] = true;
                        }
                    }
                }
            }
        }
        boolean[][] body = new boolean[uh][uw]; // face plus extrusion
        for (int y = 0; y < uh; y++) {
            for (int x = 0; x < uw; x++) {
                if (face[y][x]) {
                    for (int d = 0; d <= st.depth; d++) {
                        int ex = x + (d + 1) / 2, ey = y + d;
                        if (ey < uh && ex < uw) {
                            body[ey][ex] = true;
                        }
                    }
                }
            }
        }
        BufferedImage img = new BufferedImage(uw * scale, uh * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        if (st.outline != null) {
            g.setColor(st.outline);
            for (int y = 0; y < uh; y++) {
                for (int x = 0; x < uw; x++) {
                    if (!body[y][x] && near(body, x, y)) {
                        g.fillRect(x * scale, y * scale, scale, scale);
                    }
                }
            }
        }
        if (st.extrude != null) {
            g.setColor(st.extrude);
            for (int y = 0; y < uh; y++) {
                for (int x = 0; x < uw; x++) {
                    if (body[y][x] && !face[y][x]) {
                        g.fillRect(x * scale, y * scale, scale, scale);
                    }
                }
            }
        }
        for (int y = 0; y < uh; y++) {
            int row = y - p;
            Color c = mix(st.top, st.bottom, row / (double) (GLYPH_H - 1));
            for (int x = 0; x < uw; x++) {
                if (!face[y][x]) {
                    continue;
                }
                boolean edge = row == 0;
                g.setColor(edge && st.highlight != null ? st.highlight : c);
                g.fillRect(x * scale, y * scale, scale, scale);
            }
        }
        g.dispose();
        CACHE.put(key, img);
        return img;
    }

    /** Draws text with its glyph area's top-left at (x, y). */
    public static void draw(Graphics2D g, String s, int x, int y, int scale, Style st) {
        int p = pad(st) * scale;
        g.drawImage(render(s, scale, st), x - p, y - p, null);
    }

    private static boolean near(boolean[][] m, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int yy = y + dy, xx = x + dx;
                if (yy >= 0 && yy < m.length && xx >= 0 && xx < m[0].length && m[yy][xx]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Color mix(Color a, Color b, double k) {
        k = Math.max(0, Math.min(1, k));
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }

    // ------------------------------------------------------------------ icons

    /** Small pixel icon: rows of palette keys, '.' is transparent. */
    public static final class Icon {
        final String[] rows;
        final Map<Character, Color> palette;

        Icon(Map<Character, Color> palette, String... rows) {
            this.rows = rows;
            this.palette = palette;
        }

        public int w() {
            return rows[0].length();
        }

        public int h() {
            return rows.length;
        }
    }

    private static Map<Character, Color> pal(Object... kv) {
        Map<Character, Color> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Character) kv[i], (Color) kv[i + 1]);
        }
        return m;
    }

    private static final Map<Character, Color> GREEN = pal(
            'o', new Color(18, 44, 26), 'l', new Color(160, 236, 100), 'g', new Color(86, 186, 70),
            'd', new Color(40, 126, 56), 's', new Color(64, 140, 52), 'b', new Color(120, 82, 50),
            'B', new Color(90, 60, 36));

    /** Two-leaf sprout in a mound of soil. */
    public static final Icon SPROUT = new Icon(GREEN,
            ".oo.oo.",
            "ollsddo",
            "olgsgdo",
            ".oosoo.",
            "...s...",
            "..bsb..",
            ".bBBBb.");

    /** Single leaf with a stem, pointing up-right. */
    public static final Icon LEAF = new Icon(GREEN,
            "...oooo",
            ".oollgo",
            "olggggo",
            "oggddo.",
            "sooo...");

    /** Four-point sparkle. */
    public static final Icon SPARKLE = new Icon(pal(
            'w', new Color(255, 255, 245), 'y', new Color(255, 236, 140), 'o', new Color(110, 66, 16)),
            "...o...",
            "..oyo..",
            ".oywyo.",
            "oywwwyo",
            ".oywyo.",
            "..oyo..",
            "...o...");

    /** Draws an icon with its top-left at (x, y). */
    private static final String[] STAR_ROWS = {
            "....h....",
            "...hyy...",
            "hhyyyyyyd",
            ".yyyyyyd.",
            "..yyyyd..",
            "..yyyyd..",
            ".yyd.yyd.",
            ".yd...yd.",
    };
    /** Earned star (results screen). */
    public static final Icon STAR = new Icon(pal(
            'h', new Color(255, 246, 170), 'y', new Color(255, 204, 40), 'd', new Color(214, 140, 20)), STAR_ROWS);
    /** Star not earned yet. */
    public static final Icon STAR_EMPTY = new Icon(pal(
            'h', new Color(92, 104, 122), 'y', new Color(66, 77, 94), 'd', new Color(50, 59, 73)), STAR_ROWS);
    /** Drop shadow drawn one pixel down-right of a star. */
    public static final Icon STAR_SHADOW = new Icon(pal(
            'h', new Color(8, 12, 18), 'y', new Color(8, 12, 18), 'd', new Color(8, 12, 18)), STAR_ROWS);

    public static void drawIcon(Graphics2D g, Icon icon, int x, int y, int scale) {
        for (int r = 0; r < icon.rows.length; r++) {
            String row = icon.rows[r];
            for (int c = 0; c < row.length(); c++) {
                Color col = icon.palette.get(row.charAt(c));
                if (col != null) {
                    g.setColor(col);
                    g.fillRect(x + c * scale, y + r * scale, scale, scale);
                }
            }
        }
    }
}
