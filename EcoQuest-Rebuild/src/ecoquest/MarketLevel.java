package ecoquest;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Level 3: Market. Laid out like the Residential level: narrow 2-tile roads with a 1-tile sidewalk on each side,
 * and a market stall wherever the Residential level has a house. The four bins stand in a row on the north sidewalk.
 * Boss: the Waste King. Only the player walks here.
 */
public final class MarketLevel extends Level {
    private enum Goods { VEG, FRUIT, PORK, BEEF, POULTRY, FISH }

    private record Stall(Rectangle box, Color a, Color b, Goods goods) {
    }

    private final java.util.List<Stall> stalls = new java.util.ArrayList<>();
    private final java.util.List<int[]> trees = new java.util.ArrayList<>();

    // 50 x 28 tiles of 16 px: a much bigger world than before, with small stalls (like the beach map: big map, small
    // buildings). A corridor comes in from the top and opens into one big plaza (the blue outline in the layout
    // sketch). A 1-tile sidewalk runs around the road, and the stalls stand outside it.
    private static final Rectangle PLAZA = new Rectangle(128, 80, 544, 240);
    private static final Rectangle CORRIDOR = new Rectangle(352, 0, 96, 80);
    private static final Rectangle FOUNTAIN = new Rectangle(386, 352, 28, 26);
    private static final int SW = 44, SH = 40; // stall footprint

    public MarketLevel() {
        super(MapId.MARKET, "Waste King", new Config(40, 5, 360, 34, 14, 3, 5.0, 40));

        // sixteen small stalls around the plaza: top row, both sides, bottom row
        int[][] pos = {
                {160, 14}, {224, 14}, {288, 14}, {468, 14}, {532, 14}, {596, 14},
                {56, 100}, {56, 190}, {700, 100}, {700, 190},
                {136, 348}, {208, 348}, {280, 348}, {476, 348}, {548, 348}, {620, 348}};
        Goods[] kinds = {
                Goods.VEG, Goods.FRUIT, Goods.POULTRY, Goods.PORK, Goods.BEEF, Goods.FISH,
                Goods.PORK, Goods.VEG, Goods.BEEF, Goods.FRUIT,
                Goods.FISH, Goods.POULTRY, Goods.FRUIT, Goods.VEG, Goods.PORK, Goods.POULTRY};
        // indexed by Goods.ordinal(): VEG, FRUIT, PORK, BEEF, POULTRY, FISH
        String[] names = {"Vegetables", "Fruits", "Pork", "Beef", "Poultry", "Fish"};
        int[][] awn = {{0x2e8b3a, 0xe8f0e0}, {0xd8d8d8, 0xe67e22}, {0xd8d8d8, 0xe08a9a},
                {0x7a1f2b, 0xe8e0d0}, {0xf0c53a, 0xd8d8d8}, {0x2f67b8, 0xe8f0e0}};
        for (int i = 0; i < pos.length; i++) {
            int k = kinds[i].ordinal();
            addStall(new Rectangle(pos[i][0], pos[i][1], SW, SH), awn[k][0], awn[k][1], kinds[i], names[k]);
        }

        solids.add(FOUNTAIN);

        // trees: x, y, radius
        for (int[] t : new int[][]{{26, 30, 11}, {70, 62, 10}, {774, 30, 11}, {730, 62, 10},
                {26, 420, 11}, {84, 400, 10}, {774, 420, 11}, {716, 400, 10}, {28, 160, 10}, {772, 160, 10},
                {28, 300, 10}, {772, 300, 10}}) {
            trees.add(t);
            solids.add(new Rectangle(t[0] - t[2] / 2, t[1], t[2], t[2] + 3));
        }
        // crates and barrels beside the side stalls
        solids.add(new Rectangle(46, 150, 8, 8));
        solids.add(new Rectangle(46, 240, 8, 10));
        solids.add(new Rectangle(752, 150, 8, 8));
        solids.add(new Rectangle(752, 240, 8, 10));

        // four small bins in a row on the south sidewalk, above the fountain (BIO, REC, TRASH, HAZ)
        int bx = 368;
        for (WasteType t : WasteType.values()) {
            bins.put(t, new Rectangle(bx, 322, 12, 14));
            bx += 16;
        }

        start = new Point(230, 160);
        pile = new Point(540, 196);

        // moving hand carts: they bump you and make you drop what you carry
        carts.add(new Lane(140, 660, 116, 44));
        carts.add(new Lane(140, 660, 298, 56));
        carts.add(new Lane(140, 400, 210, 36));

        puddles.add(new Rectangle(190, 140, 14, 7));
        puddles.add(new Rectangle(330, 256, 14, 7));
        puddles.add(new Rectangle(470, 130, 14, 7));
        puddles.add(new Rectangle(600, 270, 14, 7));
        puddles.add(new Rectangle(380, 30, 14, 7));
        puddles.add(new Rectangle(250, 190, 14, 7));
        puddles.add(new Rectangle(600, 150, 14, 7));
    }

    private void addStall(Rectangle box, int a, int b, Goods goods, String label) {
        stalls.add(new Stall(box, new Color(a), new Color(b), goods));
        solids.add(new Rectangle(box.x, box.y + 10, box.width, box.height - 10));
        if (label != null) labels.add(new Label(label, new Rectangle(box.x, box.y, box.width, 14)));
    }

    @Override
    public String[] briefing() {
        return new String[]{
                "LEVEL 3  MARKET",
                "Boss: the Waste King",
                "",
                "Vendors toss fish scraps, peels and packaging all day.",
                "Every correct bin raises City Health. Reach 100% to defeat him before time runs out.",
                "Every wrong bin costs a heart, lowers City Health and feeds him more trash.",
                "Careful: hand carts bump you and make you drop what you carry."
        };
    }

    // ------------------------------------------------------------------ background

    private static final Color WOOD_D = new Color(0x4a2e1a), WOOD = new Color(0x8f5c34), WOOD_L = new Color(0xc9945a);
    private static final Color COBBLE = new Color(0x97948f), COBBLE_MORTAR = new Color(0x5b5a5f);

    @Override
    public BufferedImage paintBackground() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Tiles.brick(img, new Rectangle(0, 0, W, H), new Color(0xa6523a), new Color(0x6e3226));
        Tiles.grass(img, new Rectangle(0, 0, 52, 78));
        Tiles.grass(img, new Rectangle(748, 0, 52, 78));
        Tiles.grass(img, new Rectangle(0, 372, 112, 76));
        Tiles.grass(img, new Rectangle(688, 372, 112, 76));
        Tiles.grass(img, new Rectangle(0, 140, 40, 40));
        Tiles.grass(img, new Rectangle(760, 140, 40, 40));
        Tiles.grass(img, new Rectangle(0, 284, 40, 40));
        Tiles.grass(img, new Rectangle(760, 284, 40, 40));
        Graphics2D sg = img.createGraphics();

        // sidewalk: the road outline grown by one tile, then the road over it
        sidewalk(sg, new Rectangle(PLAZA.x - 16, PLAZA.y - 16, PLAZA.width + 32, PLAZA.height + 32));
        sidewalk(sg, new Rectangle(CORRIDOR.x - 16, 0, CORRIDOR.width + 32, CORRIDOR.height));
        sg.dispose();
        Tiles.cobble(img, PLAZA, COBBLE, COBBLE_MORTAR);
        Tiles.cobble(img, CORRIDOR, COBBLE, COBBLE_MORTAR);
        Graphics2D g = img.createGraphics();

        // dark curb line along the road outline (the blue line in the sketch)
        g.setColor(new Color(0x2f2d2c));
        g.drawLine(CORRIDOR.x - 1, 0, CORRIDOR.x - 1, PLAZA.y - 1);
        g.drawLine(CORRIDOR.x + CORRIDOR.width, 0, CORRIDOR.x + CORRIDOR.width, PLAZA.y - 1);
        g.drawLine(PLAZA.x - 1, PLAZA.y - 1, CORRIDOR.x - 1, PLAZA.y - 1);
        g.drawLine(CORRIDOR.x + CORRIDOR.width, PLAZA.y - 1, PLAZA.x + PLAZA.width, PLAZA.y - 1);
        g.drawLine(PLAZA.x - 1, PLAZA.y - 1, PLAZA.x - 1, PLAZA.y + PLAZA.height);
        g.drawLine(PLAZA.x + PLAZA.width, PLAZA.y - 1, PLAZA.x + PLAZA.width, PLAZA.y + PLAZA.height);
        g.drawLine(PLAZA.x - 1, PLAZA.y + PLAZA.height, PLAZA.x + PLAZA.width, PLAZA.y + PLAZA.height);

        for (Stall s : stalls) {
            g.setColor(new Color(20, 8, 8, 38));
            g.fillOval(s.box.x - 4, s.box.y + s.box.height - 6, s.box.width + 8, 14);
        }
        // leaves and pebbles scattered on the ground
        for (int i = 0; i < 190; i++) {
            int x = Math.floorMod(Tiles.hash(i, 11), W), y = Math.floorMod(Tiles.hash(i, 23), H);
            g.setColor(new Color(i % 3 == 0 ? 0x7a3a2c : i % 3 == 1 ? 0x5f8a3a : 0x6f6f74));
            g.fillRect(x, y, 2, 1);
        }

        // green lawn under the fountain
        Rectangle f = FOUNTAIN;
        BufferedImage lawn = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Tiles.grass(lawn, new Rectangle(f.x - 6, f.y - 3, f.width + 12, f.height + 8));
        java.awt.Shape oval = new java.awt.geom.Ellipse2D.Double(f.x - 6, f.y - 3, f.width + 12, f.height + 8);
        g.setClip(oval);
        g.drawImage(lawn, 0, 0, null);
        g.setClip(null);
        g.setColor(new Color(0x2f5f26));
        g.drawOval(f.x - 6, f.y - 3, f.width + 12, f.height + 8);

        // trees
        for (int[] t : trees) {
            int rr = t[2];
            Tiles.obj(g, t[0] - rr - 2, t[1] - rr - 4, rr * 2 + 4, rr * 2 + rr + 12, 6, 6, 55,
                    (gg, w, h) -> Tiles.tree(gg, w / 2, rr + 4, rr));
        }

        // lamps on the sidewalk corners, crates, barrels
        for (int[] l : new int[][]{{119, 88}, {679, 88}, {119, 180}, {679, 180}, {119, 272}, {679, 272},
                {343, 30}, {455, 30}, {250, 306}, {552, 306}}) {
            lamp(g, l[0], l[1]);
        }
        crate(g, new Rectangle(46, 150, 8, 8), true);
        barrel(g, new Rectangle(46, 240, 8, 10));
        crate(g, new Rectangle(752, 150, 8, 8), false);
        barrel(g, new Rectangle(752, 240, 8, 10));

        // stalls sorted by baseline so they overlap correctly
        stalls.stream().sorted(java.util.Comparator.comparingInt(s -> s.box.y + s.box.height)).forEach(s ->
                Tiles.obj(g, s.box.x, s.box.y, s.box.width, s.box.height, 4, 3, 58, (gg, w, h) -> paintStall(gg, w, h, s)));

        // flower pots beside the stall bases
        for (int[] p : new int[][]{{146, 44}, {208, 44}, {272, 44}, {516, 44}, {580, 44}, {644, 44}, {122, 366}, {326, 366}, {460, 366}, {670, 366}}) {
            pot(g, p[0], p[1]);
        }

        // fountain
        Tiles.obj(g, f.x - 4, f.y - 6, f.width + 8, f.height + 10, 5, 5, 45, this::fountain);

        // puddles
        for (Rectangle p : puddles) {
            g.setColor(new Color(0x3a566b));
            g.fillOval(p.x - 1, p.y - 1, p.width + 2, p.height + 2);
            g.setColor(new Color(0x5f93b8));
            g.fillOval(p.x + 1, p.y, p.width - 2, p.height - 2);
            g.setColor(new Color(0x8cc0e0));
            g.fillOval(p.x + 3, p.y + 1, p.width - 8, p.height - 5);
        }
        g.dispose();
        Tiles.vignette(img);
        return img;
    }

    /** Flat paving tiles with light and dark edges, like the Residential level's sidewalks. */
    private static void sidewalk(Graphics2D g, Rectangle r) {
        for (int y = r.y; y < r.y + r.height; y += 16) {
            for (int x = r.x; x < r.x + r.width; x += 16) {
                g.setColor(new Color(0xbdb39b));
                g.fillRect(x, y, 16, 16);
                g.setColor(new Color(0xcfc7b1));
                g.fillRect(x, y, 16, 1);
                g.fillRect(x, y, 1, 16);
                g.setColor(new Color(0x9f957f));
                g.fillRect(x, y + 15, 16, 1);
                g.fillRect(x + 15, y, 1, 16);
            }
        }
    }

    // ---- small props

    private static void fence(Graphics2D g, int x, int y) {
        g.setColor(Tiles.OUT);
        g.fillRect(x - 1, y - 1, 5, 18);
        g.fillRect(x, y + 2, 13, 4);
        g.fillRect(x, y + 9, 13, 4);
        g.setColor(new Color(0x4a4a56));
        g.fillRect(x, y, 3, 16);
        g.fillRect(x + 3, y + 3, 9, 2);
        g.fillRect(x + 3, y + 10, 9, 2);
        g.setColor(new Color(0x7a7a88));
        g.fillRect(x, y, 1, 16);
        g.fillRect(x, y - 2, 3, 2);
    }

    /** Slim lamp post like the Residential level's: a thin pole with a small lit head. Base at (x, y + 22). */
    private static void lamp(Graphics2D g, int x, int y) {
        Tiles.obj(g, x - 3, y + 8, 8, 24, 3, 2, 45, (gg, w, h) -> {
            gg.setColor(new Color(0x2f2f38));
            gg.fillRect(3, 6, 2, 16);
            gg.fillRect(1, 20, 6, 3);
            gg.setColor(new Color(0x1e1a17));
            gg.fillRect(0, 0, 8, 7);
            gg.setColor(new Color(0xf2c230));
            gg.fillRect(1, 1, 6, 5);
            gg.setColor(new Color(0xfff2a8));
            gg.fillRect(2, 2, 2, 2);
        });
    }

    private static void crate(Graphics2D g, Rectangle r, boolean produce) {
        Tiles.obj(g, r.x, r.y, r.width, r.height, 4, 3, 50, (gg, w, h) -> {
            gg.setColor(WOOD);
            gg.fillRect(0, 0, w, h);
            gg.setColor(WOOD_L);
            gg.fillRect(0, 0, w, 3);
            gg.setColor(WOOD_D);
            for (int x = 6; x < w; x += 8) gg.fillRect(x, 3, 1, h - 3);
            gg.fillRect(0, h - 3, w, 3);
            gg.fillRect(0, h / 2, w, 1);
            if (produce) {
                gg.setColor(new Color(0xd63a3a));
                gg.fillOval(2, -3, 6, 6);
                gg.setColor(new Color(0x4caf50));
                gg.fillOval(8, -3, 6, 6);
            }
        });
    }

    private static void barrel(Graphics2D g, Rectangle r) {
        Tiles.obj(g, r.x, r.y, r.width, r.height, 4, 3, 50, (gg, w, h) -> {
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

    private static void pot(Graphics2D g, int x, int y) {
        Tiles.obj(g, x, y, 12, 14, 3, 2, 45, (gg, w, h) -> {
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
            g.setColor(Tiles.shade(c, 0.8));
            g.fillRect(x + ww - 1, 0, 1, 10);
            g.setColor(Tiles.shade(c, 1.18));
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
                int c = cols[Math.floorMod(Tiles.hash(x + i, y + row), cols.length)];
                int yy = y + 10 - row * 5;
                g.setColor(Tiles.shade(new Color(c), 0.7));
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
            g.setColor(Tiles.shade(meat, 0.75));
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
            {136, 92}, {250, 92}, {320, 92}, {480, 92}, {650, 92}, {136, 300}, {250, 300}, {540, 300}, {650, 300}, {300, 326}};

    @Override
    public void paintDecor(Graphics2D g, double clean, double t) {
        if (clean >= 45) {
            int[] cols = {0xf06292, 0xffd54f, 0xffffff, 0xba68c8};
            for (int i = 0; i < FLOWERS.length; i++) {
                int x = FLOWERS[i][0], y = FLOWERS[i][1];
                g.setColor(new Color(0x3c8a34));
                g.fillRect(x + 1, y + 3, 2, 5);
                g.setColor(new Color(cols[i % cols.length]));
                g.fillRect(x - 1, y, 6, 3);
                g.fillRect(x + 1, y - 2, 2, 7);
            }
        }
        if (clean >= 60) { // bunting across the main lane
            for (int x = 356; x < 444; x += 12) {
                int sag = (int) (Math.sin((x - 356) / 88.0 * Math.PI) * 5);
                g.setColor(new Color(new int[]{0xe74c3c, 0xf1c40f, 0x3498db, 0x2ecc71}[(x / 14) % 4]));
                g.fillPolygon(new int[]{x, x + 10, x + 5}, new int[]{22 + sag, 22 + sag, 30 + sag}, 3);
            }
            g.setColor(new Color(0x2a1d1d));
            g.drawLine(356, 22, 444, 22);
        }
        // fountain water jets, stronger when the city is clean
        int cx = FOUNTAIN.x + FOUNTAIN.width / 2, cy = FOUNTAIN.y + 4;
        int jets = clean >= 80 ? 5 : 3;
        for (int i = 0; i < jets; i++) {
            double ph = (t * 1.6 + i * 0.37) % 1.0;
            double ang = (i - (jets - 1) / 2.0) * 0.45;
            int dx = (int) (Math.sin(ang) * 16 * ph);
            int dy = (int) (-20 * Math.sin(ph * Math.PI) * (1 - Math.abs(ang) * 0.4));
            g.setColor(new Color(0xd8f0ff));
            g.fillRect(cx + dx, cy + dy, 3, 3);
        }
        if (clean >= 80) { // sparkles
            for (int i = 0; i < 12; i++) {
                double ph = (t * 0.8 + i * 0.17) % 1.0;
                if (ph < 0.5) {
                    int x = 132 + Math.floorMod(Tiles.hash(i, 1), 536), y = 84 + Math.floorMod(Tiles.hash(i, 2), 230);
                    g.setColor(new Color(255, 255, 200, (int) (200 * (1 - ph * 2))));
                    g.fillRect(x, y - 3, 2, 8);
                    g.fillRect(x - 3, y, 8, 2);
                }
            }
        }
    }
}
