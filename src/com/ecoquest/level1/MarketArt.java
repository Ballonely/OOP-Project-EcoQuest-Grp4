package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static com.ecoquest.level1.Config.COLS;
import static com.ecoquest.level1.Config.ROWS;
import static com.ecoquest.level1.Config.TILE;

/**
 * Layout and art of the Town Market (Level 3), in world pixels (512x288, 16 px tiles) like every
 * other level, so it stays crisp pixel art on the same grid and with the same paving, grass,
 * trees, lamps and puddles as the Residential Street.
 * <pre>
 *   rows 0-1     street along the top (sidewalk with curb below it)
 *   cols 14-17   cobbled lane from the street down into the plaza, under a "Town Market" arch
 *   rows 6-12    cobbled market plaza (cols 6-25); hand carts roll across it
 *   around it    paved square: 4 stalls above, 2 on each side, 4 below, all facing the plaza,
 *                lamps evenly spaced along both plaza sides with banderitas strung between them,
 *                sacks, crates, baskets and barrels stacked beside the stalls, grass beds with
 *                trees in the corners, delivery cars parked on the street
 *   row 13       the four sorting bins on the plaza's south edge, the fountain below them
 * </pre>
 * Everything is mirrored left/right around the lane.
 */
final class MarketArt {
    static final int W = COLS * TILE, H = ROWS * TILE;

    record Label(String text, Rectangle area) {
    }

    /** A hand cart that travels back and forth along y between x1 and x2 (world px, px/s). */
    record Lane(int x1, int x2, int y, double speed) {
    }

    private enum Goods { VEG, FRUIT, PORK, BEEF, POULTRY, FISH }

    private record Stall(Rectangle box, Color a, Color b, Goods goods) {
    }

    final java.util.List<Rectangle> solids = new java.util.ArrayList<>();
    final java.util.List<Label> labels = new java.util.ArrayList<>();
    final java.util.List<Lane> carts = new java.util.ArrayList<>();
    /** Painted things the player should not find litter on: stalls, pots, crates, barrels, fountain. */
    final java.util.List<Rectangle> scenery = new java.util.ArrayList<>();
    /** Top-left corners of the shared {@link Tree} props. */
    final java.util.List<Point> trees = new java.util.ArrayList<>();
    /** Positions of the shared {@link LampPost} props. */
    final java.util.List<Point> lamps = new java.util.ArrayList<>();
    /** Shared {@link Puddle} props (they slow the player). */
    final java.util.List<Rectangle> puddles = new java.util.ArrayList<>();
    /** Tile of each bin, in the BIO, REC, TRASH, HAZ order. */
    final java.util.List<Point> binTiles = new java.util.ArrayList<>();
    /** Player start (feet) and the Waste King's heap (base centre), world px. */
    final Point start = new Point(184, 184);
    final Point pile = new Point(344, 172);

    private final java.util.List<Stall> stalls = new java.util.ArrayList<>();
    private final TileType[][] tiles = new TileType[ROWS][COLS];

    static final Rectangle PLAZA = new Rectangle(96, 96, 320, 112);
    static final Rectangle LANE = new Rectangle(224, 32, 64, 64);
    static final Rectangle FOUNTAIN = new Rectangle(242, 238, 28, 26);
    private static final int SW = 48, SH = 40;   // stall footprint (3 tiles x 2.5 tiles)
    /** Grass beds in the four corners (tile aligned). */
    private static final Rectangle[] GRASS_BEDS = {
            new Rectangle(0, 48, 80, 48), new Rectangle(432, 48, 80, 48),
            new Rectangle(0, 208, 80, 80), new Rectangle(432, 208, 80, 80)};
    private static final int[][] TREES = {   // left side; mirrored for the right
            {6, 52}, {46, 62}, {6, 214}, {48, 224}, {16, 252}, {52, 258}};
    private static final int[][] LAMPS = {
            {86, 96}, {420, 96}, {86, 152}, {420, 152}, {86, 208}, {420, 208}};   // evenly along both plaza sides
    /** Lamp rows that carry a string of banderitas across the plaza. */
    static final int[] BUNTING_ROWS = {96, 152};
    static final int BUNTING_X0 = 91, BUNTING_X1 = 423;
    /** Entrance arch over the lane: two posts on the sidewalk, a beam and a sign over the street. */
    static final int ARCH_X0 = 210, ARCH_X1 = 302, ARCH_TOP = 4, ARCH_BASE = 46;
    private static final int[][] POTS = {
            {88, 74}, {212, 74}, {288, 74}, {412, 74}, {88, 254}, {212, 254}, {288, 254}, {412, 254}};
    private enum Ware { CRATE, SACK, BASKET, BARREL }

    private record WareBox(Ware ware, Rectangle box, int tint) {
    }

    /** Market goods stacked beside the stalls, left side (mirrored for the right): ware, x, y, tint. */
    private static final Object[][] WARES = {
            {Ware.SACK, 2, 102, 0}, {Ware.CRATE, 3, 124, 0xd63a3a}, {Ware.BASKET, 2, 144, 0xfb8c00},
            {Ware.BARREL, 4, 162, 0}, {Ware.CRATE, 3, 186, 0x4caf50},           // strip between the corner beds
            {Ware.CRATE, 84, 56, 0xfdd835}, {Ware.SACK, 88, 232, 0},             // beside the top and bottom stalls
            {Ware.BASKET, 194, 36, 0xe53935}, {Ware.CRATE, 82, 34, 0x8bc34a}};   // by the street
    private final java.util.List<WareBox> wares = new java.util.ArrayList<>();

    MarketArt() {
        // twelve stalls: 4 above the plaza, 2 on each side, 4 below
        int[][] pos = {
                {104, 48}, {160, 48}, {304, 48}, {360, 48},
                {32, 104}, {32, 160}, {432, 104}, {432, 160},
                {104, 228}, {160, 228}, {304, 228}, {360, 228}};
        Goods[] kinds = {
                Goods.VEG, Goods.FRUIT, Goods.PORK, Goods.BEEF,
                Goods.POULTRY, Goods.FISH, Goods.FISH, Goods.POULTRY,
                Goods.BEEF, Goods.PORK, Goods.FRUIT, Goods.VEG};
        // indexed by Goods.ordinal(): VEG, FRUIT, PORK, BEEF, POULTRY, FISH
        String[] names = {"Vegetables", "Fruits", "Pork", "Beef", "Poultry", "Fish"};
        int[][] awn = {{0x2e8b3a, 0xe8f0e0}, {0xd8d8d8, 0xe67e22}, {0xd8d8d8, 0xe08a9a},
                {0x7a1f2b, 0xe8e0d0}, {0xf0c53a, 0xd8d8d8}, {0x2f67b8, 0xe8f0e0}};
        for (int i = 0; i < pos.length; i++) {
            int k = kinds[i].ordinal();
            addStall(new Rectangle(pos[i][0], pos[i][1], SW, SH), awn[k][0], awn[k][1], kinds[i], names[k]);
        }

        solids.add(FOUNTAIN);
        scenery.add(new Rectangle(FOUNTAIN.x - 6, FOUNTAIN.y - 8, FOUNTAIN.width + 12, FOUNTAIN.height + 12));

        for (int[] t : TREES) {
            trees.add(new Point(t[0], t[1]));
            trees.add(new Point(W - 24 - t[0], t[1]));
        }
        for (int[] l : LAMPS) lamps.add(new Point(l[0], l[1]));
        for (Object[] wv : WARES) {
            Ware ware = (Ware) wv[0];
            int x = (int) wv[1], y = (int) wv[2], tint = (int) wv[3];
            int w = ware == Ware.BARREL ? 8 : 10, h = ware == Ware.BASKET ? 7 : ware == Ware.CRATE ? 8 : 10;
            for (Rectangle box : new Rectangle[] {new Rectangle(x, y, w, h), new Rectangle(W - w - x, y, w, h)}) {
                wares.add(new WareBox(ware, box, tint));
                solids.add(box);
                scenery.add(new Rectangle(box.x - 1, box.y - 5, box.width + 2, box.height + 6));
            }
        }
        // entrance arch: only the two post feet block; the sign gets a crisp label like the stalls
        solids.add(new Rectangle(ARCH_X0, ARCH_BASE - 5, 6, 5));
        solids.add(new Rectangle(ARCH_X1 - 6, ARCH_BASE - 5, 6, 5));
        scenery.add(new Rectangle(ARCH_X0 - 2, 0, ARCH_X1 - ARCH_X0 + 4, ARCH_BASE + 2));
        labels.add(new Label("Town Market", new Rectangle(ARCH_X0 + 12, ARCH_TOP + 8, ARCH_X1 - ARCH_X0 - 24, 12)));
        for (int[] p : POTS) {
            solids.add(new Rectangle(p[0] + 2, p[1] + 8, 8, 6));
            scenery.add(new Rectangle(p[0], p[1], 12, 14));
        }

        for (int c = 14; c <= 17; c++) binTiles.add(new Point(c, 13));

        // hand carts: full width along the top and bottom of the plaza; the middle lane stays
        // left of the Waste King
        carts.add(new Lane(112, 400, 118, 28));
        carts.add(new Lane(112, 400, 200, 36));
        carts.add(new Lane(112, 250, 160, 23));

        puddles.add(new Rectangle(136, 130, 20, 8));
        puddles.add(new Rectangle(232, 182, 20, 8));
        puddles.add(new Rectangle(296, 110, 18, 8));
        puddles.add(new Rectangle(384, 184, 18, 8));
        puddles.add(new Rectangle(248, 62, 18, 8));

        for (TileType[] row : tiles) java.util.Arrays.fill(row, TileType.SIDEWALK);
        for (int c = 0; c < COLS; c++) {
            tiles[0][c] = TileType.ROAD;
            tiles[1][c] = TileType.ROAD;
        }
        for (Rectangle bed : GRASS_BEDS)
            for (int r = bed.y / TILE; r < (bed.y + bed.height) / TILE; r++)
                for (int c = bed.x / TILE; c < (bed.x + bed.width) / TILE; c++) tiles[r][c] = TileType.GRASS;
    }

    private void addStall(Rectangle box, int a, int b, Goods goods, String label) {
        stalls.add(new Stall(box, new Color(a), new Color(b), goods));
        solids.add(new Rectangle(box.x, box.y + 10, box.width, box.height - 10));
        scenery.add(new Rectangle(box.x - 2, box.y, box.width + 4, box.height + 4));
        if (label != null) labels.add(new Label(label, new Rectangle(box.x, box.y, box.width, 14)));
    }

    // ------------------------------------------------------------------ background

    private static final Color WOOD_D = new Color(0x4a2e1a), WOOD = new Color(0x8f5c34), WOOD_L = new Color(0xc9945a);
    private static final Color COBBLE = new Color(0x97948f), COBBLE_MORTAR = new Color(0x5b5a5f);

    BufferedImage paintBackground() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D tg = img.createGraphics();
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) TileRenderer.draw(tg, tiles, r, c);   // same street/paving/grass as Level 1
        tg.dispose();
        MarketTiles.cobble(img, PLAZA, COBBLE, COBBLE_MORTAR);
        MarketTiles.cobble(img, LANE, COBBLE, COBBLE_MORTAR);
        Graphics2D g = img.createGraphics();

        // curb where the lane meets the street (as on every sidewalk along a road), then a
        // dark edge around the cobbles
        g.setColor(new Color(Palette.CURB));
        g.fillRect(LANE.x, LANE.y, LANE.width, 3);
        g.setColor(new Color(0x2f2d2c));
        int l = LANE.x - 1, r = LANE.x + LANE.width, pl = PLAZA.x - 1, pr = PLAZA.x + PLAZA.width;
        int pt = PLAZA.y - 1, pb = PLAZA.y + PLAZA.height;
        g.drawLine(l, LANE.y, l, pt);
        g.drawLine(r, LANE.y, r, pt);
        g.drawLine(pl, pt, l, pt);
        g.drawLine(r, pt, pr, pt);
        g.drawLine(pl, pt, pl, pb);
        g.drawLine(pr, pt, pr, pb);
        g.drawLine(pl, pb, pr, pb);

        for (Stall s : stalls) {
            g.setColor(new Color(20, 8, 8, 38));
            g.fillOval(s.box.x - 4, s.box.y + s.box.height - 6, s.box.width + 8, 14);
        }

        // round lawn under the fountain
        Rectangle f = FOUNTAIN;
        BufferedImage lawn = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        MarketTiles.grass(lawn, new Rectangle(f.x - 6, f.y - 3, f.width + 12, f.height + 8));
        java.awt.Shape oval = new java.awt.geom.Ellipse2D.Double(f.x - 6, f.y - 3, f.width + 12, f.height + 8);
        g.setClip(oval);
        g.drawImage(lawn, 0, 0, null);
        g.setClip(null);
        g.setColor(new Color(0x2f5f26));
        g.drawOval(f.x - 6, f.y - 3, f.width + 12, f.height + 8);

        wares.stream().sorted(java.util.Comparator.comparingInt(gd -> gd.box.y)).forEach(gd -> {
            switch (gd.ware) {
                case CRATE -> crate(g, gd.box, gd.tint);
                case SACK -> sack(g, gd.box);
                case BASKET -> basket(g, gd.box, gd.tint);
                case BARREL -> barrel(g, gd.box);
            }
        });

        // stalls sorted by baseline so they overlap correctly
        stalls.stream().sorted(java.util.Comparator.comparingInt(s -> s.box.y + s.box.height)).forEach(s ->
                MarketTiles.obj(g, s.box.x, s.box.y, s.box.width, s.box.height, 4, 3, 58, (gg, w, h) -> paintStall(gg, w, h, s)));

        for (int[] p : POTS) pot(g, p[0], p[1]);   // flower pots beside the stalls
        MarketTiles.obj(g, f.x - 4, f.y - 6, f.width + 8, f.height + 10, 5, 5, 45, this::fountain);
        g.dispose();
        return img;
    }

    // ---- small props



    private static void crate(Graphics2D g, Rectangle r, int produce) {
        MarketTiles.obj(g, r.x, r.y, r.width, r.height, 4, 3, 50, (gg, w, h) -> {
            gg.setColor(WOOD);
            gg.fillRect(0, 0, w, h);
            gg.setColor(WOOD_L);
            gg.fillRect(0, 0, w, 3);
            gg.setColor(WOOD_D);
            for (int x = 6; x < w; x += 8) gg.fillRect(x, 3, 1, h - 3);
            gg.fillRect(0, h - 3, w, 3);
            gg.fillRect(0, h / 2, w, 1);
            if (produce != 0) {   // produce heaped on top
                Color c = new Color(produce);
                for (int i = 0; i + 3 <= w; i += 3) {
                    gg.setColor(MarketTiles.shade(c, i % 2 == 0 ? 1.0 : 0.82));
                    gg.fillOval(i, -3, 4, 4);
                }
                gg.setColor(new Color(255, 255, 255, 120));
                gg.fillRect(1, -2, 1, 1);
            }
        });
    }

    private static void barrel(Graphics2D g, Rectangle r) {
        MarketTiles.obj(g, r.x, r.y, r.width, r.height, 4, 3, 50, (gg, w, h) -> {
            gg.setColor(new Color(0x7a4a28));
            gg.fillRoundRect(0, 0, w, h, 8, 8);
            gg.setColor(new Color(0xa56b3a));
            gg.fillRect(3, 2, 3, h - 4);
            gg.setColor(new Color(0x4a2e1a));
            gg.fillRect(w - 5, 2, 3, h - 4);
            gg.setColor(new Color(0x3a3a46));
            gg.fillRect(0, h / 4, w, 2);
            gg.fillRect(0, h * 3 / 4 - 1, w, 2);
            gg.setColor(new Color(0x5a3820));
            gg.fillOval(2, -1, w - 4, 5);
        });
    }

    /** A burlap sack of rice, tied at the top. */
    private static void sack(Graphics2D g, Rectangle r) {
        MarketTiles.obj(g, r.x, r.y, r.width, r.height, 3, 2, 45, (gg, w, h) -> {
            gg.setColor(new Color(0xb89a6a));
            gg.fillRoundRect(0, 2, w, h - 2, 5, 5);
            gg.setColor(new Color(0xd4b98a));
            gg.fillRect(2, 3, 2, h - 5);
            gg.setColor(new Color(0x8c7148));
            gg.fillRect(w - 3, 4, 2, h - 6);
            gg.fillRect(w / 2 - 2, 0, 4, 3);       // tied neck
            gg.setColor(new Color(0xf3ead6));
            gg.fillRect(w / 2 - 1, h / 2, 2, 2);   // rice stamp
        });
    }

    /** A low woven basket heaped with fruit. */
    private static void basket(Graphics2D g, Rectangle r, int fruit) {
        MarketTiles.obj(g, r.x, r.y, r.width, r.height, 3, 2, 45, (gg, w, h) -> {
            Color c = new Color(fruit);
            for (int i = 0; i + 3 <= w; i += 3) {
                gg.setColor(MarketTiles.shade(c, i % 2 == 0 ? 1.0 : 0.8));
                gg.fillOval(i, -2, 4, 4);
            }
            gg.setColor(new Color(0xa8743c));
            gg.fillRect(0, 1, w, h - 1);
            gg.setColor(new Color(0x7a5228));
            for (int x = 1; x < w; x += 2) gg.fillRect(x, 2, 1, h - 3);
            gg.setColor(new Color(0xc99a5c));
            gg.fillRect(0, 1, w, 1);
        });
    }

    /** Wooden entrance arch with a sign board (its text is drawn as a crisp overlay label). */
    static void arch(Graphics2D g) {
        int x0 = ARCH_X0, x1 = ARCH_X1, top = ARCH_TOP, base = ARCH_BASE;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x0 - 1, base - 3, 9, 4);
        g.fillOval(x1 - 8, base - 3, 9, 4);
        for (int px : new int[] {x0, x1 - 6}) {   // posts
            g.setColor(MarketTiles.OUT);
            g.fillRect(px, top + 4, 6, base - top - 4);
            g.setColor(WOOD);
            g.fillRect(px + 1, top + 4, 4, base - top - 5);
            g.setColor(WOOD_L);
            g.fillRect(px + 1, top + 4, 1, base - top - 5);
        }
        g.setColor(MarketTiles.OUT);              // beam
        g.fillRect(x0 - 4, top, x1 - x0 + 8, 6);
        g.setColor(WOOD);
        g.fillRect(x0 - 3, top + 1, x1 - x0 + 6, 4);
        g.setColor(WOOD_L);
        g.fillRect(x0 - 3, top + 1, x1 - x0 + 6, 1);
        g.setColor(MarketTiles.OUT);              // sign board hanging under the beam
        g.fillRect(x0 + 10, top + 6, x1 - x0 - 20, 16);
        g.setColor(new Color(0xf3e3bd));
        g.fillRect(x0 + 11, top + 7, x1 - x0 - 22, 14);
        int[] flags = {0xe74c3c, 0xf1c40f, 0x3498db, 0x2ecc71};
        for (int i = 0, x = x0 - 2; x < x1; x += 8, i++) {   // little flags along the beam
            g.setColor(new Color(flags[i % flags.length]));
            g.fillPolygon(new int[] {x, x + 6, x + 3}, new int[] {top, top, top - 4}, 3);
        }
    }

    /** A sagging string of banderitas between two lamp heads. */
    static void bunting(Graphics2D g, int x0, int x1, int y) {
        int[] flags = {0xe74c3c, 0xf1c40f, 0x3498db, 0x2ecc71, 0xffffff, 0xe67e22};
        int prevX = x0, prevY = y;
        g.setColor(new Color(0x2a1d1d));
        for (int x = x0; x <= x1; x += 2) {
            int yy = y + (int) Math.round(Math.sin((x - x0) / (double) (x1 - x0) * Math.PI) * 10);
            g.drawLine(prevX, prevY, x, yy);
            prevX = x;
            prevY = yy;
        }
        for (int i = 0, x = x0 + 4; x < x1 - 4; x += 7, i++) {
            int yy = y + (int) Math.round(Math.sin((x + 2 - x0) / (double) (x1 - x0) * Math.PI) * 10);
            Color c = new Color(flags[i % flags.length]);
            g.setColor(c);
            g.fillPolygon(new int[] {x, x + 5, x + 2}, new int[] {yy, yy, yy + 5}, 3);
            g.setColor(MarketTiles.shade(c, 0.75));
            g.drawLine(x + 4, yy, x + 2, yy + 4);
        }
    }

    private static void pot(Graphics2D g, int x, int y) {
        MarketTiles.obj(g, x, y, 12, 14, 3, 2, 45, (gg, w, h) -> {
            gg.setColor(new Color(0x3f8a3a));
            gg.fillOval(0, 0, 12, 9);
            gg.setColor(new Color(0x62b04d));
            gg.fillOval(2, 0, 6, 5);
            gg.setColor(new Color(0xf06292));
            gg.fillRect(2, 2, 2, 2);
            gg.setColor(new Color(0xffd54f));
            gg.fillRect(7, 1, 2, 2);
            gg.setColor(new Color(0xb8613a));
            gg.fillRect(2, 8, 8, 6);
            gg.setColor(new Color(0xd98a5c));
            gg.fillRect(2, 8, 8, 2);
        });
    }

    private void fountain(Graphics2D g, int w, int h) {
        int cx = w / 2;
        // octagonal-ish stone basin
        g.setColor(new Color(0x6c6e78));
        g.fillOval(0, 8, w, h - 6);
        g.setColor(new Color(0xb9bbc4));
        g.fillOval(0, 4, w, h - 8);
        g.setColor(new Color(0xd9dbe1));
        g.fillOval(2, 5, w - 4, 6);
        g.setColor(new Color(0x3f93cf));
        g.fillOval(5, 10, w - 10, h - 20);
        g.setColor(new Color(0x7cc3ee));
        g.fillOval(9, 13, w - 22, h - 28);
        g.setColor(new Color(0xcaeaff));
        g.fillOval(14, 15, 12, 4);
        // tiers
        g.setColor(new Color(0x8b8d96));
        g.fillRect(cx - 4, 6, 8, 20);
        g.setColor(new Color(0xc9cbd3));
        g.fillRect(cx - 4, 6, 3, 20);
        g.setColor(new Color(0x9fa1ab));
        g.fillOval(cx - 12, 4, 24, 9);
        g.setColor(new Color(0xd9dbe1));
        g.fillOval(cx - 12, 2, 24, 8);
        g.setColor(new Color(0x7cc3ee));
        g.fillOval(cx - 9, 4, 18, 5);
        g.setColor(new Color(0x8b8d96));
        g.fillRect(cx - 2, -4, 4, 8);
        g.setColor(new Color(0xd9dbe1));
        g.fillOval(cx - 5, -6, 10, 5);
    }

    // ---- stalls

    /** Compact stall (44 x 40): striped awning, goods on a wooden counter, a few hanging items. */
    private void paintStall(Graphics2D g, int w, int h, Stall s) {
        int top = h - 14;
        g.setColor(new Color(0x3b2519));
        g.fillRect(2, 8, w - 4, top - 6);
        g.setColor(new Color(0x4a2f20));
        for (int x = 6; x < w - 3; x += 6) g.fillRect(x, 9, 1, top - 8);
        if (s.goods != Goods.VEG && s.goods != Goods.FRUIT) {
            for (int x = 6; x + 6 <= w - 4; x += 11) hanging(g, s.goods, x, 8);
        }
        goods(g, s.goods, 3, top - 13, w - 6);
        // posts
        g.setColor(new Color(0x6b4526));
        g.fillRect(0, 9, 3, h - 9);
        g.fillRect(w - 3, 9, 3, h - 9);
        g.setColor(new Color(0x9a6a3e));
        g.fillRect(0, 9, 1, h - 9);
        g.fillRect(w - 3, 9, 1, h - 9);
        // counter
        g.setColor(WOOD);
        g.fillRect(0, top + 3, w, h - top - 3);
        g.setColor(new Color(0x6d4426));
        for (int x = 6; x < w - 1; x += 6) g.fillRect(x, top + 4, 1, h - top - 6);
        g.setColor(WOOD_D);
        g.fillRect(0, h - 2, w, 2);
        g.setColor(WOOD_L);
        g.fillRect(-1, top, w + 2, 3);
        g.setColor(new Color(0xe3b57d));
        g.fillRect(-1, top, w + 2, 1);
        // striped awning with a scalloped valance
        int sw = 6, n = 0;
        for (int x = 0; x < w; x += sw, n++) {
            Color c = n % 2 == 0 ? s.a : s.b;
            int ww = Math.min(sw, w - x);
            g.setColor(c);
            g.fillRect(x, 0, ww, 9);
            g.fillArc(x, 5, ww, 8, 180, 180);
            g.setColor(MarketTiles.shade(c, 0.8));
            g.fillRect(x + ww - 1, 0, 1, 10);
            g.setColor(MarketTiles.shade(c, 1.18));
            g.fillRect(x, 2, 1, 7);
        }
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRect(0, 0, w, 2);
        g.setColor(new Color(255, 255, 255, 70));
        g.fillRect(0, 2, w, 1);
    }

    private void hanging(Graphics2D g, Goods kind, int x, int y) {
        g.setColor(new Color(0x6b4a2b));
        g.fillRect(x + 2, y, 1, 3);
        switch (kind) {
            case VEG -> {
                g.setColor(new Color(0x7cb342));
                g.fillOval(x, y + 3, 5, 5);
                g.setColor(new Color(0xe0b13a));
                g.fillOval(x + 1, y + 7, 5, 5);
            }
            case FRUIT -> {
                g.setColor(new Color(0xe53935));
                g.fillOval(x, y + 3, 5, 5);
                g.setColor(new Color(0xfb8c00));
                g.fillOval(x + 1, y + 7, 5, 5);
            }
            case PORK -> {
                g.setColor(new Color(0xe58c8c));
                g.fillOval(x, y + 3, 6, 9);
                g.setColor(new Color(0xfbe3e0));
                g.fillRect(x + 1, y + 4, 2, 2);
            }
            case BEEF -> {
                g.setColor(new Color(0x8e1f2b));
                g.fillOval(x, y + 3, 6, 9);
                g.setColor(new Color(0xf1d9d4));
                g.fillRect(x + 2, y + 5, 1, 4);
            }
            case POULTRY -> {
                g.setColor(new Color(0xf0c58a));
                g.fillOval(x, y + 3, 6, 8);
                g.setColor(new Color(0xd9a063));
                g.fillRect(x + 1, y + 10, 1, 3);
                g.fillRect(x + 4, y + 10, 1, 3);
            }
            case FISH -> {
                g.setColor(new Color(0xb8a58a));
                g.fillOval(x, y + 3, 5, 10);
            }
        }
    }

    private void goods(Graphics2D g, Goods kind, int x, int y, int w) {
        switch (kind) {
            case VEG -> piles(g, x, y, w, new int[]{0x4caf50, 0x8bc34a, 0xf39c12, 0xd63a3a, 0x2e7d32});
            case FRUIT -> piles(g, x, y, w, new int[]{0xe53935, 0xfb8c00, 0xfdd835, 0x7cb342, 0x8e24aa});
            case PORK -> cuts(g, x, y, w, new Color(0xf2a1a1), new Color(0xfbe3e0));
            case BEEF -> cuts(g, x, y, w, new Color(0xa31f2b), new Color(0xe9c9c9));
            case POULTRY -> {
                for (int i = 0; i + 11 <= w; i += 12) {
                    g.setColor(new Color(0xc98f52));
                    g.fillOval(x + i, y + 5, 11, 8);
                    g.setColor(new Color(0xf3d3a0));
                    g.fillOval(x + i + 1, y + 5, 9, 6);
                    g.setColor(new Color(0xe3b074));
                    g.fillOval(x + i + 3, y + 7, 5, 3);
                    g.setColor(new Color(0xd9a063));
                    g.fillRect(x + i + 2, y + 12, 2, 2);
                    g.fillRect(x + i + 7, y + 12, 2, 2);
                }
            }
            case FISH -> {
                g.setColor(new Color(0xdcecf5));
                g.fillRect(x, y + 11, w, 3);
                for (int i = 0; i + 11 <= w; i += 12) {
                    g.setColor(new Color(0x7f9db4));
                    g.fillOval(x + i, y + 6, 10, 6);
                    g.setColor(new Color(0xdfe9f0));
                    g.fillOval(x + i + 1, y + 9, 8, 3);
                    g.setColor(new Color(0x7f9db4));
                    g.fillPolygon(new int[]{x + i + 9, x + i + 12, x + i + 12}, new int[]{y + 9, y + 6, y + 12}, 3);
                    g.setColor(Color.WHITE);
                    g.fillRect(x + i + 2, y + 8, 2, 2);
                    g.setColor(Color.BLACK);
                    g.fillRect(x + i + 2, y + 8, 1, 1);
                }
            }
        }
    }

    /** Two rows of round produce in the given colours (vegetables and fruit). */
    private void piles(Graphics2D g, int x, int y, int w, int[] cols) {
        for (int row = 0; row < 2; row++) {
            for (int i = row * 3; i + 6 <= w - row * 3; i += 6) {
                int c = cols[Math.floorMod(MarketTiles.hash(x + i, y + row), cols.length)];
                int yy = y + 10 - row * 5;
                g.setColor(MarketTiles.shade(new Color(c), 0.7));
                g.fillOval(x + i, yy, 7, 6);
                g.setColor(new Color(c));
                g.fillOval(x + i, yy - 1, 6, 5);
                g.setColor(new Color(255, 255, 255, 140));
                g.fillRect(x + i + 1, yy, 1, 1);
            }
        }
    }

    /** Slabs of raw meat with a fat edge (pork and beef). */
    private void cuts(Graphics2D g, int x, int y, int w, Color meat, Color fat) {
        g.setColor(new Color(0xf5f5f0));
        g.fillRect(x, y + 12, w, 2);
        for (int i = 0; i + 11 <= w; i += 12) {
            g.setColor(MarketTiles.shade(meat, 0.75));
            g.fillRoundRect(x + i, y + 5, 11, 8, 4, 4);
            g.setColor(meat);
            g.fillRoundRect(x + i, y + 4, 10, 7, 4, 4);
            g.setColor(fat);
            g.fillRect(x + i + 1, y + 4, 8, 2);
            g.fillRect(x + i + 4, y + 7, 3, 1);
        }
    }

    // ------------------------------------------------------------------ decor that reacts to cleanliness

    private static final int[][] FLOWERS = {
            {102, 100}, {150, 100}, {200, 100}, {310, 100}, {360, 100}, {408, 100},
            {102, 202}, {150, 202}, {360, 202}, {408, 202}};

    void paintDecor(Graphics2D g, double clean, double t) {
        if (clean >= 45) {
            int[] cols = {0xf06292, 0xffd54f, 0xffffff, 0xba68c8};
            for (int i = 0; i < FLOWERS.length; i++) {
                int x = FLOWERS[i][0], y = FLOWERS[i][1];
                g.setColor(new Color(0x3c8a34));
                g.fillRect(x + 1, y + 2, 1, 3);
                g.setColor(new Color(cols[i % cols.length]));
                g.fillRect(x, y, 3, 2);
                g.fillRect(x + 1, y - 1, 1, 4);
            }
        }
        // fountain water jets, stronger when the city is clean
        int cx = FOUNTAIN.x + FOUNTAIN.width / 2, cy = FOUNTAIN.y + 4;
        int jets = clean >= 80 ? 5 : 3;
        for (int i = 0; i < jets; i++) {
            double ph = (t * 1.6 + i * 0.37) % 1.0;
            double ang = (i - (jets - 1) / 2.0) * 0.45;
            int dx = (int) (Math.sin(ang) * 12 * ph);
            int dy = (int) (-14 * Math.sin(ph * Math.PI) * (1 - Math.abs(ang) * 0.4));
            g.setColor(new Color(0xd8f0ff));
            g.fillRect(cx + dx, cy + dy, 2, 2);
        }
        if (clean >= 80) { // sparkles over the plaza
            for (int i = 0; i < 12; i++) {
                double ph = (t * 0.8 + i * 0.17) % 1.0;
                if (ph < 0.5) {
                    int x = PLAZA.x + 4 + Math.floorMod(MarketTiles.hash(i, 1), PLAZA.width - 8);
                    int y = PLAZA.y + 4 + Math.floorMod(MarketTiles.hash(i, 2), PLAZA.height - 8);
                    g.setColor(new Color(255, 255, 200, (int) (200 * (1 - ph * 2))));
                    g.fillRect(x, y - 2, 1, 5);
                    g.fillRect(x - 2, y, 5, 1);
                }
            }
        }
    }
}
