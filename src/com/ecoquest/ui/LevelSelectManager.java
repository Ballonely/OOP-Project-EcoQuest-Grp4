package com.ecoquest.ui;

import com.ecoquest.core.InputHandler;
import com.ecoquest.data.LevelProgress;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Level select: five level cards centred on a parchment map, joined by an animated trail.
 * Each card shows the level's own art (cut from assets/level_select_map.png), its stars and
 * the best cleanliness reached. The selected card lifts, glows and gets a bouncing arrow.
 */
public class LevelSelectManager {

    public interface LevelSelectListener {
        void onBackToMenu();
        void onStartLevel(String levelName);
    }

    private static final String[] LEVEL_NAMES = {
        "Residential", "Public Park", "Town Market", "Coastal Beach", "City Center"
    };
    private static final String[] TITLES = {
        "RESIDENTIAL STREET", "PUBLIC PARK", "TOWN MARKET", "COASTAL BEACH", "CITY CENTER"
    };
    private static final String[] TAGLINES = {
        "COZY SUBURBAN NEIGHBORHOOD", "THE LUSH PUBLIC PARK", "BUSTLING TOWN MARKETPLACE",
        "SANDY COASTAL BEACH", "GRAND METROPOLIS CITY CENTER"
    };
    /** The art inside each frame of level_select_map.png (frame and base strip excluded). */
    private static final Rectangle[] ART = {
        new Rectangle(80, 115, 300, 210),
        new Rectangle(479, 120, 260, 190),
        new Rectangle(210, 350, 330, 200),
        new Rectangle(610, 405, 520, 150),
        new Rectangle(800, 110, 340, 250)
    };
    /** Card rows, left to right. The trail runs 1-2 (top), 2-3 (down), 3-4 (bottom), 4-5 (up). */
    private static final int[][] ROWS = {{0, 1, 4}, {2, 3}};
    private static final int[] ROW_BASE_HEIGHT = {210, 165};

    // layout (pixels)
    private static final int HEADER = 40, FOOTER = 50, BASE_STRIP = 10, FRAME = 4;
    private static final int COL_GAP = 46, ROW_GAP = 48, TOP_AREA = 132, BOTTOM_AREA = 60, SIDE = 64;

    // parchment palette (matches the original overworld art)
    private static final Color PARCHMENT = new Color(0xEFDCB4);
    private static final Color PARCHMENT_DARK = new Color(0xE2C892);
    private static final Color BORDER = new Color(0xC9A46A);
    private static final Color BORDER_DARK = new Color(0x9C7A46);
    private static final Color INK = new Color(0x48321B);
    private static final Color INK_SOFT = new Color(0x8A6A44);
    private static final Color TRAIL = new Color(0xB48A5A);
    private static final Color TRAIL_LIGHT = new Color(0xF6E7C6);
    private static final Color FRAME_COLOR = new Color(0x1F1A17);
    private static final Color BASE_COLOR = new Color(0x5A3A22);
    private static final Color GOLD = new Color(0xFFC931);
    private static final Color GOLD_DARK = new Color(0xB07A10);
    private static final Color STAR_EMPTY = new Color(0xCDB084);
    private static final Color BADGE = new Color(0x2E9E4F);
    private static final Color CLEAR = new Color(0x3FA34D);

    private static final PixelFont.Style TITLE_STYLE = new PixelFont.Style(new Color(0x8EF08A), new Color(0x2E9E4F),
            new Color(0xC8FFB8), new Color(0x14532D), 2, new Color(0x1B1B1B), true);
    private static final PixelFont.Style INK_STYLE = flat(INK);
    private static final PixelFont.Style INK_SOFT_STYLE = flat(INK_SOFT);
    private static final PixelFont.Style WHITE_STYLE = flat(Color.WHITE);
    private static final PixelFont.Style GOLD_STYLE = flat(GOLD_DARK);
    private static final PixelFont.Style CLEAR_STYLE = flat(CLEAR);

    private static final String[] STAR = {
        "...#...", "...#...", "..###..", "#######", ".#####.", "..###..", ".##.##.", "##...##"};
    private static final double[][] LEAVES = new double[14][];

    static {
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < LEAVES.length; i++) {
            LEAVES[i] = new double[] {r.nextDouble(), r.nextDouble(), 8 + r.nextDouble() * 14, r.nextDouble() * 6.28,
                    r.nextInt(3)};
        }
    }

    private final LevelSelectListener listener;
    private final LevelProgress progress;
    private final BufferedImage[] thumbs = new BufferedImage[LEVEL_NAMES.length];
    private Rectangle[] cards = new Rectangle[LEVEL_NAMES.length];   // thumbnail rects
    private int selectedIndex;
    private int hoveredIndex = -1;
    private int layoutW = -1, layoutH = -1;

    public LevelSelectManager(LevelSelectListener listener, LevelProgress progress) {
        this.listener = listener;
        this.progress = progress;
        BufferedImage map = loadMapImage();
        for (int i = 0; i < thumbs.length; i++) {
            Rectangle a = ART[i];
            thumbs[i] = map.getSubimage(a.x, a.y, a.width, a.height);
        }
    }

    // ------------------------------------------------------------------ input

    public void handleInput(InputHandler input, int width, int height) {
        layout(width, height);
        if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
            listener.onBackToMenu();
            return;
        }
        if (input.isKeyJustPressed(KeyEvent.VK_LEFT)) {
            selectedIndex = (selectedIndex + LEVEL_NAMES.length - 1) % LEVEL_NAMES.length;
        } else if (input.isKeyJustPressed(KeyEvent.VK_RIGHT)) {
            selectedIndex = (selectedIndex + 1) % LEVEL_NAMES.length;
        } else if (input.isKeyJustPressed(KeyEvent.VK_UP) || input.isKeyJustPressed(KeyEvent.VK_DOWN)) {
            selectedIndex = nearestInOtherRow(selectedIndex);
        } else if (input.isKeyJustPressed(KeyEvent.VK_ENTER) || input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            startSelectedLevel();
            return;
        }
        for (int i = 0; i < LEVEL_NAMES.length; i++) {
            if (input.isKeyJustPressed(KeyEvent.VK_1 + i)) selectedIndex = i;
        }

        hoveredIndex = cardAt(input.getMouseX(), input.getMouseY());
        Point click = input.consumeMouseClick();
        int clicked = click == null ? -1 : cardAt(click.x, click.y);
        if (clicked >= 0) {
            selectedIndex = clicked;
            startSelectedLevel();
        }
    }

    private int nearestInOtherRow(int index) {
        int[] other = rowOf(index) == 0 ? ROWS[1] : ROWS[0];
        double cx = cards[index].getCenterX();
        int best = other[0];
        for (int i : other) {
            if (Math.abs(cards[i].getCenterX() - cx) < Math.abs(cards[best].getCenterX() - cx)) best = i;
        }
        return best;
    }

    private static int rowOf(int index) {
        for (int i : ROWS[0]) if (i == index) return 0;
        return 1;
    }

    private int cardAt(int x, int y) {
        for (int i = 0; i < cards.length; i++) {
            if (cards[i] != null && cardBounds(i).contains(x, y)) return i;
        }
        return -1;
    }

    private Rectangle cardBounds(int i) {
        Rectangle t = cards[i];
        return new Rectangle(t.x - FRAME, t.y - HEADER, t.width + FRAME * 2, HEADER + t.height + FOOTER);
    }

    private void startSelectedLevel() {
        listener.onStartLevel(LEVEL_NAMES[selectedIndex]);
    }

    // ------------------------------------------------------------------ layout

    /** Scales the thumbnails so both rows fit, then centres the whole block in the free space. */
    private void layout(int width, int height) {
        if (width == layoutW && height == layoutH) return;
        layoutW = width;
        layoutH = height;
        int availW = width - SIDE * 2;
        int availH = height - TOP_AREA - BOTTOM_AREA;
        double k = 0.95;   // leave breathing room around the cards
        for (int r = 0; r < ROWS.length; r++) {
            double natural = 0;
            for (int i : ROWS[r]) natural += ART[i].width * (double) ROW_BASE_HEIGHT[r] / ART[i].height;
            int gaps = COL_GAP * (ROWS[r].length - 1) + FRAME * 2 * ROWS[r].length;
            k = Math.min(k, (availW - gaps) / natural);
        }
        int fixedH = ROWS.length * (HEADER + FOOTER) + ROW_GAP;
        k = Math.min(k, (availH - fixedH) / (double) (ROW_BASE_HEIGHT[0] + ROW_BASE_HEIGHT[1]));

        int blockH = (int) Math.round((ROW_BASE_HEIGHT[0] + ROW_BASE_HEIGHT[1]) * k) + fixedH;
        int y = TOP_AREA + Math.max(0, (availH - blockH) / 2);
        cards = new Rectangle[LEVEL_NAMES.length];
        for (int r = 0; r < ROWS.length; r++) {
            int rowH = (int) Math.round(ROW_BASE_HEIGHT[r] * k);
            int[] widths = new int[ROWS[r].length];
            int rowW = COL_GAP * (ROWS[r].length - 1);
            for (int j = 0; j < ROWS[r].length; j++) {
                Rectangle a = ART[ROWS[r][j]];
                widths[j] = (int) Math.round(a.width * (double) rowH / a.height);
                rowW += widths[j] + FRAME * 2;
            }
            int x = (width - rowW) / 2 + FRAME;
            for (int j = 0; j < ROWS[r].length; j++) {
                cards[ROWS[r][j]] = new Rectangle(x, y + HEADER, widths[j], rowH);
                x += widths[j] + FRAME * 2 + COL_GAP;
            }
            y += HEADER + rowH + FOOTER + ROW_GAP;
        }
    }

    // ------------------------------------------------------------------ render

    public void render(Graphics2D g, int width, int height) {
        layout(width, height);
        double t = System.currentTimeMillis() / 1000.0;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        drawParchment(g, width, height);
        drawLeaves(g, width, height, t);
        drawHeader(g, width);
        drawTrail(g, t);
        for (int i = 0; i < cards.length; i++) if (i != selectedIndex) drawCard(g, i, t);
        drawCard(g, selectedIndex, t);   // on top, so its lift and glow overlap neighbours
        drawFooter(g, width, height);
    }

    private void drawParchment(Graphics2D g, int w, int h) {
        g.setColor(PARCHMENT);
        g.fillRect(0, 0, w, h);
        // faint paper speckles
        g.setColor(PARCHMENT_DARK);
        for (int i = 0; i < 160; i++) {
            int x = Math.floorMod(i * 7919 + 13, w), y = Math.floorMod(i * 104729 + 71, h);
            g.fillRect(x, y, 2 + i % 3, 2);
        }
        g.setColor(BORDER);
        g.fillRect(0, 0, w, 8);
        g.fillRect(0, h - 8, w, 8);
        g.fillRect(0, 0, 8, h);
        g.fillRect(w - 8, 0, 8, h);
        g.setColor(BORDER_DARK);
        g.drawRect(12, 12, w - 25, h - 25);
    }

    /** A few leaves and petals drifting across the map. */
    private void drawLeaves(Graphics2D g, int w, int h, double t) {
        Color[] colors = {new Color(0x6DBE5A), new Color(0xF49AC1), new Color(0xD9A441)};
        for (double[] leaf : LEAVES) {
            double speed = leaf[2];
            double x = (leaf[0] * w + t * speed) % (w + 40) - 20;
            double y = (leaf[1] * h + t * speed * 0.45 + Math.sin(t * 1.3 + leaf[3]) * 12) % (h + 40) - 20;
            g.setColor(colors[(int) leaf[4]]);
            int flip = Math.sin(t * 3 + leaf[3]) > 0 ? 1 : 0;
            g.fillRect((int) x, (int) y, 4, 2 + flip * 2);
            g.fillRect((int) x + 1, (int) y - 1, 2, 1);
        }
    }

    private void drawHeader(Graphics2D g, int width) {
        String title = "ECO QUEST";
        int scale = 5;
        int tw = PixelFont.width(title, scale, true);
        PixelFont.draw(g, title, (width - tw) / 2, 22, scale, TITLE_STYLE);

        // title glyphs are 7 rows + 2 rows of extrusion + outline
        String subtitle = "SELECT A REGION TO CLEAN";
        PixelFont.draw(g, subtitle, (width - PixelFont.width(subtitle, 2)) / 2, 22 + 10 * scale + 10, 2, INK_STYLE);

        // total stars, top right
        String total = progress.totalStars() + " / " + LevelProgress.LEVELS * LevelProgress.MAX_STARS;
        int boxW = 14 + 16 + 8 + PixelFont.width(total, 2) + 14, boxH = 34;
        int bx = width - 36 - boxW, by = 28;
        g.setColor(BORDER_DARK);
        g.fillRect(bx - 2, by - 2, boxW + 4, boxH + 4);
        g.setColor(PARCHMENT);
        g.fillRect(bx, by, boxW, boxH);
        star(g, bx + 14, by + (boxH - 16) / 2, 2, true);
        PixelFont.draw(g, total, bx + 14 + 16 + 8, by + (boxH - 14) / 2, 2, INK_STYLE);
    }

    /** Elbow-shaped double-line trail between consecutive levels, with dots marching along it. */
    private void drawTrail(Graphics2D g, double t) {
        for (int i = 0; i + 1 < cards.length; i++) {
            int[][] pts = elbow(cards[i], cards[i + 1]);
            g.setStroke(new BasicStroke(10, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            g.setColor(TRAIL);
            polyline(g, pts);
            g.setStroke(new BasicStroke(4, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            g.setColor(TRAIL_LIGHT);
            polyline(g, pts);
            // marching dots, brighter when the segment leads to a cleared level
            g.setColor(progress.cleared(i) ? GOLD_DARK : TRAIL);
            double phase = (t * 28) % 16;
            double walked = 0;
            for (int s = 0; s + 1 < pts.length; s++) {
                double dx = pts[s + 1][0] - pts[s][0], dy = pts[s + 1][1] - pts[s][1];
                double len = Math.hypot(dx, dy);
                for (double d = (16 - (walked % 16) + phase) % 16; d < len; d += 16) {
                    int x = (int) (pts[s][0] + dx / len * d), y = (int) (pts[s][1] + dy / len * d);
                    g.fillRect(x - 1, y - 1, 3, 3);
                }
                walked += len;
            }
        }
        g.setStroke(new BasicStroke(1));
    }

    private static int[][] elbow(Rectangle a, Rectangle b) {
        int ax = (int) a.getCenterX(), ay = (int) a.getCenterY();
        int bx = (int) b.getCenterX(), by = (int) b.getCenterY();
        if (Math.abs(ay - by) < 4) return new int[][] {{ax, ay}, {bx, by}};
        int my = (int) Math.round((Math.max(a.y, b.y) - HEADER + Math.min(a.getMaxY(), b.getMaxY()) + FOOTER) / 2.0);
        return new int[][] {{ax, ay}, {ax, my}, {bx, my}, {bx, by}};
    }

    private static void polyline(Graphics2D g, int[][] pts) {
        for (int i = 0; i + 1 < pts.length; i++) g.drawLine(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]);
    }

    private void drawCard(Graphics2D g, int i, double t) {
        boolean selected = i == selectedIndex, hovered = i == hoveredIndex && !selected;
        int lift = selected ? -6 : hovered ? -2 : 0;
        Rectangle th = new Rectangle(cards[i]);
        th.translate(0, lift);
        Rectangle card = new Rectangle(th.x - FRAME, th.y - HEADER, th.width + FRAME * 2, HEADER + th.height + FOOTER);

        // soft drop shadow (grows when lifted)
        g.setColor(new Color(72, 50, 27, selected ? 70 : 45));
        g.fillRect(card.x + 6, card.y + HEADER + 8 - lift, card.width, th.height + FRAME * 2 + BASE_STRIP);

        // title + tagline on a parchment plate, so the trail never runs through the text
        int titleW = PixelFont.width(TITLES[i], 2), tagW = PixelFont.width(TAGLINES[i], 1);
        int plateW = Math.max(titleW, tagW) + 16;
        g.setColor(PARCHMENT);
        g.fillRect((int) th.getCenterX() - plateW / 2, card.y - 2, plateW, 30);
        PixelFont.draw(g, TITLES[i], (int) th.getCenterX() - titleW / 2, card.y + 1, 2, INK_STYLE);
        PixelFont.draw(g, TAGLINES[i], (int) th.getCenterX() - tagW / 2, card.y + 19, 1, INK_SOFT_STYLE);

        // framed artwork with the brown base strip from the original map
        g.setColor(FRAME_COLOR);
        g.fillRect(th.x - FRAME, th.y - FRAME, th.width + FRAME * 2, th.height + FRAME * 2 + BASE_STRIP);
        g.drawImage(thumbs[i], th.x, th.y, th.width, th.height, null);
        g.setColor(BASE_COLOR);
        g.fillRect(th.x - FRAME, th.y + th.height + FRAME, th.width + FRAME * 2, BASE_STRIP - 2);
        g.setColor(new Color(0x7A5232));
        g.fillRect(th.x - FRAME, th.y + th.height + FRAME, th.width + FRAME * 2, 2);

        // dim the art a little while not focused, so the selected level pops
        if (!selected && !hovered) {
            g.setColor(new Color(239, 220, 180, 40));
            g.fillRect(th.x, th.y, th.width, th.height);
        }

        drawBadge(g, i, th);
        if (progress.cleared(i)) drawClearStamp(g, th);

        // highlight border
        if (selected) {
            int pulse = (int) (155 + 100 * (0.5 + 0.5 * Math.sin(t * 5)));
            g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), pulse));
            g.setStroke(new BasicStroke(4));
            g.drawRect(th.x - FRAME - 4, th.y - FRAME - 4, th.width + FRAME * 2 + 7, th.height + FRAME * 2 + BASE_STRIP + 7);
            g.setStroke(new BasicStroke(1));
            drawArrow(g, (int) th.getCenterX(), card.y - 6 + (int) Math.round(Math.sin(t * 6) * 3));
        } else if (hovered) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(3));
            g.drawRect(th.x - FRAME - 3, th.y - FRAME - 3, th.width + FRAME * 2 + 5, th.height + FRAME * 2 + BASE_STRIP + 5);
            g.setStroke(new BasicStroke(1));
        }

        drawProgress(g, i, th, selected, t);
    }

    private void drawBadge(Graphics2D g, int i, Rectangle th) {
        String label = "LV " + (i + 1);
        int bw = PixelFont.width(label, 2) + 14, bh = 22;
        int bx = th.x + 6, by = th.y + 6;
        g.setColor(FRAME_COLOR);
        g.fillRect(bx - 2, by - 2, bw + 4, bh + 4);
        g.setColor(BADGE);
        g.fillRect(bx, by, bw, bh);
        g.setColor(new Color(0x6BD08A));
        g.fillRect(bx, by, bw, 2);
        PixelFont.draw(g, label, bx + 7, by + 4, 2, WHITE_STYLE);
    }

    private void drawClearStamp(Graphics2D g, Rectangle th) {
        String label = "CLEARED";
        int bw = PixelFont.width(label, 1) + 12, bh = 15;
        int bx = th.x + th.width - bw - 6, by = th.y + 6;
        g.setColor(FRAME_COLOR);
        g.fillRect(bx - 1, by - 1, bw + 2, bh + 2);
        g.setColor(new Color(0xF4FBEA));
        g.fillRect(bx, by, bw, bh);
        PixelFont.draw(g, label, bx + 6, by + 4, 1, CLEAR_STYLE);
    }

    /** Stars on the left; best cleanliness as a bar on the right. */
    private void drawProgress(Graphics2D g, int i, Rectangle th, boolean selected, double t) {
        int y = th.y + th.height + FRAME + BASE_STRIP + 10;
        int stars = progress.stars(i);
        for (int s = 0; s < LevelProgress.MAX_STARS; s++) {
            boolean earned = s < stars;
            int bob = earned && selected ? (int) Math.round(Math.sin(t * 4 + s) * 1.5) : 0;
            star(g, th.x + s * 20, y + bob, 2, earned);
        }

        int best = progress.best(i);
        String text = best == LevelProgress.NOT_PLAYED ? "NEW" : "BEST " + best + "%";
        int barW = Math.min(110, th.width - 3 * 20 - PixelFont.width(text, 1) - 24);
        int barX = th.x + th.width - barW, barY = y + 4;
        g.setColor(PARCHMENT);   // keep the trail from crossing the label
        g.fillRect(barX - 12 - PixelFont.width(text, 1), barY - 3, PixelFont.width(text, 1) + 8, 13);
        PixelFont.draw(g, text, barX - 8 - PixelFont.width(text, 1), barY, 1,
                best == LevelProgress.NOT_PLAYED ? GOLD_STYLE : INK_STYLE);
        if (barW > 10) {
            g.setColor(INK);
            g.fillRect(barX, barY - 1, barW, 9);
            g.setColor(PARCHMENT_DARK);
            g.fillRect(barX + 1, barY, barW - 2, 7);
            int fill = best <= 0 ? 0 : (barW - 2) * best / 100;
            if (fill > 0) {
                g.setColor(best >= LevelProgress.THREE_STAR_CLEANLINESS ? new Color(0x3FA34D)
                        : best >= LevelProgress.TWO_STAR_CLEANLINESS ? new Color(0x8BC34A) : new Color(0xE0A030));
                g.fillRect(barX + 1, barY, fill, 7);
                g.setColor(new Color(255, 255, 255, 90));
                g.fillRect(barX + 1, barY, fill, 2);
            }
        }
    }

    private static void star(Graphics2D g, int x, int y, int scale, boolean earned) {
        for (int r = 0; r < STAR.length; r++) {
            for (int c = 0; c < STAR[r].length(); c++) {
                if (STAR[r].charAt(c) != '#') continue;
                g.setColor(earned ? (r < 3 ? new Color(0xFFE27A) : GOLD) : STAR_EMPTY);
                g.fillRect(x + c * scale, y + r * scale, scale, scale);
            }
        }
    }

    /** Small gold arrow pointing down at the selected card. */
    private static void drawArrow(Graphics2D g, int cx, int tipY) {
        int[][] rows = {{-7, 7}, {-6, 6}, {-5, 5}, {-4, 4}, {-3, 3}, {-2, 2}, {-1, 1}};
        for (int r = 0; r < rows.length; r++) {
            int y = tipY - rows.length + r;
            g.setColor(FRAME_COLOR);
            g.fillRect(cx + rows[r][0] - 1, y - 1, rows[r][1] - rows[r][0] + 3, 3);
        }
        for (int r = 0; r < rows.length; r++) {
            int y = tipY - rows.length + r;
            g.setColor(r < 2 ? new Color(0xFFE27A) : GOLD);
            g.fillRect(cx + rows[r][0], y, rows[r][1] - rows[r][0] + 1, 1);
        }
    }

    private void drawFooter(Graphics2D g, int width, int height) {
        String hint = "CLICK A REGION TO PLAY  •  ARROWS OR 1-5 TO SELECT  •  ENTER TO START  •  ESC TO MENU";
        PixelFont.draw(g, hint, (width - PixelFont.width(hint, 1)) / 2, height - 30, 1, INK_STYLE);
        String rule = "★ CLEAR  ★★ " + LevelProgress.TWO_STAR_CLEANLINESS + "%+ CLEANLINESS  ★★★ "
                + LevelProgress.THREE_STAR_CLEANLINESS + "%+ CLEANLINESS";
        PixelFont.draw(g, rule, (width - PixelFont.width(rule, 1)) / 2, height - 46, 1, INK_SOFT_STYLE);
    }

    private static PixelFont.Style flat(Color c) {
        return new PixelFont.Style(c, c, null, null, 0, null);
    }

    // ------------------------------------------------------------------ assets

    private BufferedImage loadMapImage() {
        try (InputStream resource = getClass().getResourceAsStream("/assets/level_select_map.png")) {
            if (resource != null) {
                BufferedImage image = ImageIO.read(resource);
                if (image != null) return image;
                throw new IllegalStateException("Unsupported level selection map image format.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the level selection map image.", e);
        }

        File[] candidates = {
            new File("assets/level_select_map.png"),
            new File("bin/assets/level_select_map.png")
        };
        for (File candidate : candidates) {
            if (!candidate.isFile()) continue;
            try {
                BufferedImage image = ImageIO.read(candidate);
                if (image != null) return image;
                throw new IllegalStateException("Unsupported image format: " + candidate);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read level selection map: " + candidate, e);
            }
        }

        throw new IllegalStateException("Level selection map not found. Expected "
                + "assets/level_select_map.png under " + System.getProperty("user.dir"));
    }
}
