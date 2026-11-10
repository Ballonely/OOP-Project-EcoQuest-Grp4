package ecoquest;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Level 3: Market. Layout follows the market-square inspiration image:
 * cobbled plaza, stalls with striped awnings, a fountain, four bins at the bottom, a heap in the middle.
 * Boss: the Waste King.
 */
public final class MarketLevel extends Level {
    private enum Goods { BREAD, VEG, FISH, CLOTH, POTS }

    private record Stall(Rectangle box, Color a, Color b, Goods goods) {
    }

    private final java.util.List<Stall> stalls = new java.util.ArrayList<>();
    private static final Rectangle PLAZA = r(98, 68, 128, 90);
    private static final Rectangle CORRIDOR = r(134, 0, 70, 68);
    private static final Rectangle FOUNTAIN = r(146, 38, 28, 26);

    public MarketLevel() {
        super(MapId.MARKET, "Waste King", new Config(40, 5, 300, 26, 14, 3, 7.0, 30));

        // buildings, trees, fences (solid)
        solids.add(r(0, 0, 98, 32));     // tavern
        solids.add(r(100, 0, 24, 26));   // tree
        solids.add(r(206, 0, 40, 24));   // brick house
        solids.add(r(250, 0, 70, 28));   // trees + fence, top right
        solids.add(r(298, 26, 22, 80));  // right building
        solids.add(r(0, 64, 10, 70));    // left building
        solids.add(r(0, 38, 14, 24));    // left tree
        solids.add(r(304, 108, 16, 40)); // right tree
        solids.add(r(0, 150, 12, 30));
        solids.add(r(306, 150, 14, 30));
        solids.add(r(14, 160, 96, 6));   // low fences along the bottom
        solids.add(r(262, 160, 36, 6));
        solids.add(FOUNTAIN);

        // stalls: 320x180 grid -> world coordinates through r()
        addStall(r(20, 48, 30, 32), 0xd8d8d8, 0xc0392b, Goods.VEG, null);
        addStall(r(70, 46, 34, 29), 0xd8d8d8, 0xc0392b, Goods.BREAD, "Bakery");
        addStall(r(106, 40, 30, 34), 0x2f67b8, 0xf0c53a, Goods.POTS, null);
        addStall(r(188, 40, 28, 34), 0x2f67b8, 0xf0c53a, Goods.POTS, null);
        addStall(r(220, 44, 32, 28), 0x2e8b3a, 0xe8f0e0, Goods.VEG, "Greengrocer");
        addStall(r(268, 60, 28, 44), 0x2e8b3a, 0xe8f0e0, Goods.POTS, null);
        addStall(r(58, 112, 36, 32), 0x2f67b8, 0xf0c53a, Goods.BREAD, "Bakery");
        addStall(r(234, 112, 34, 32), 0x2e8b3a, 0xe8f0e0, Goods.FISH, "Fishmonger");
        addStall(r(12, 78, 22, 40), 0xd8d8d8, 0xc0392b, Goods.CLOTH, null);
        // crates and barrels beside the stalls
        solids.add(r(46, 118, 10, 10));
        solids.add(r(96, 116, 8, 10));
        solids.add(r(270, 118, 10, 12));

        // four bins, same order as the inspiration (BIO, REC, TRASH, HAZ)
        int bx = 126;
        for (WasteType t : WasteType.values()) {
            bins.put(t, r(bx, 130, 14, 18));
            bx += 19;
        }

        start = new Point(352, 168);
        pile = new Point(320, 208);

        puddles.add(r(76, 90, 16, 8));
        puddles.add(r(232, 88, 16, 8));
        puddles.add(r(184, 122, 16, 8));
        puddles.add(r(112, 148, 16, 8));

        // moving hand carts (obstacles): they bump you and make you drop what you carry
        carts.add(new Lane(210, 450, 150, 52));
        carts.add(new Lane(30, 600, 316, 66));

        shoppers.add(new Wanderer(180, 172, r(64, 78, 40, 30), 0x8e44ad, 0xd35400));
        shoppers.add(new Wanderer(480, 172, r(206, 78, 56, 30), 0xc0392b, 0x2980b9));
        shoppers.add(new Wanderer(90, 190, r(36, 80, 24, 50), 0x16a085, 0xf39c12));
        shoppers.add(new Wanderer(300, 40, r(138, 4, 66, 28), 0xd35400, 0x27ae60));
        shoppers.add(new Wanderer(250, 170, r(220, 78, 40, 30), 0xe67e22, 0x7f8c8d));

        labels.add(new Label("Tavern", r(49, 8, 20, 10)));
    }

    private void addStall(Rectangle box, int a, int b, Goods goods, String label) {
        stalls.add(new Stall(box, new Color(a), new Color(b), goods));
        solids.add(new Rectangle(box.x, box.y + 12, box.width, box.height - 12));
        if (label != null) labels.add(new Label(label, new Rectangle(box.x, box.y + 2, box.width, 18)));
    }

    @Override
    public String[] briefing() {
        return new String[]{
                "LEVEL 3  MARKET",
                "Boss: the Waste King",
                "",
                "Vendors toss fish scraps, peels and packaging all day.",
                "Sort 40 pieces into the right bins before time runs out.",
                "Every wrong bin costs a heart and feeds the Waste King more trash.",
                "Careful: hand carts bump you and make you drop what you carry."
        };
    }

    // ------------------------------------------------------------------ background

    private static final Color WOOD_D = new Color(0x4a2e1a), WOOD = new Color(0x8f5c34), WOOD_L = new Color(0xc9945a);

    @Override
    public BufferedImage paintBackground() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Tiles.brick(img, new Rectangle(0, 0, W, H), new Color(0xa6523a), new Color(0x6e3226));
        Tiles.cobble(img, PLAZA, new Color(0x97948f), new Color(0x5b5a5f));
        Tiles.cobble(img, CORRIDOR, new Color(0x97948f), new Color(0x5b5a5f));
        Tiles.grass(img, r(250, 0, 70, 30));
        Tiles.grass(img, r(300, 108, 20, 72));
        Tiles.grass(img, r(0, 140, 14, 40));
        Tiles.grass(img, r(0, 36, 14, 28));
        Graphics2D g = img.createGraphics();

        // curb where cobble meets brick, and grime under the stalls
        g.setColor(new Color(0, 0, 0, 55));
        g.fillRect(PLAZA.x, PLAZA.y, PLAZA.width, 3);
        g.fillRect(PLAZA.x, PLAZA.y, 3, PLAZA.height);
        g.fillRect(PLAZA.x + PLAZA.width - 3, PLAZA.y, 3, PLAZA.height);
        g.setColor(new Color(255, 255, 255, 28));
        g.fillRect(PLAZA.x, PLAZA.y + PLAZA.height - 2, PLAZA.width, 2);
        for (Stall s : stalls) {
            g.setColor(new Color(20, 8, 8, 38));
            g.fillOval(s.box.x - 6, s.box.y + s.box.height - 10, s.box.width + 12, 22);
        }
        // leaves and pebbles scattered on the ground
        for (int i = 0; i < 90; i++) {
            int x = Math.floorMod(Tiles.hash(i, 11), W), y = Math.floorMod(Tiles.hash(i, 23), H);
            g.setColor(new Color(i % 3 == 0 ? 0x7a3a2c : i % 3 == 1 ? 0x5f8a3a : 0x6f6f74));
            g.fillRect(x, y, 2, 1);
        }

        // fences along the bottom and by the top-right garden
        for (int x = 14; x < 220; x += 12) fence(g, x, 322);
        for (int x = 524; x < 596; x += 12) fence(g, x, 322);
        for (int x = 512; x < 636; x += 12) fence(g, x, 46);

        // green lawn under the fountain
        Rectangle f = FOUNTAIN;
        BufferedImage lawn = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Tiles.grass(lawn, new Rectangle(f.x - 20, f.y - 8, f.width + 40, f.height + 22));
        java.awt.Shape oval = new java.awt.geom.Ellipse2D.Double(f.x - 20, f.y - 8, f.width + 40, f.height + 22);
        g.setClip(oval);
        g.drawImage(lawn, 0, 0, null);
        g.setClip(null);
        g.setColor(new Color(0x2f5f26));
        g.drawOval(f.x - 20, f.y - 8, f.width + 40, f.height + 22);

        // buildings (back to front)
        Tiles.obj(g, 0, 0, 196, 64, 6, 5, 60, this::tavern);
        Tiles.obj(g, 412, 0, 80, 48, 5, 4, 60, this::brickHouse);
        Tiles.obj(g, 596, 52, 44, 160, -6, 4, 60, (gg, w, h) -> sideBuilding(gg, w, h, true));
        Tiles.obj(g, 0, 128, 20, 140, 6, 4, 60, (gg, w, h) -> sideBuilding(gg, w, h, false));

        // trees
        for (int[] t : new int[][]{{224, 28, 24}, {540, 22, 23}, {604, 30, 25}, {12, 100, 20}, {628, 252, 22}, {10, 334, 22}, {630, 338, 22}}) {
            int rr = t[2];
            Tiles.obj(g, t[0] - rr - 2, t[1] - rr - 4, rr * 2 + 4, rr * 2 + rr + 12, 6, 6, 55,
                    (gg, w, h) -> Tiles.tree(gg, w / 2, rr + 4, rr));
        }

        // lamps, crates, barrels
        lamp(g, 272, 62);
        lamp(g, 408, 62);
        lamp(g, 306, 236);
        crate(g, r(46, 118, 10, 10), true);
        barrel(g, r(96, 116, 8, 10));
        crate(g, r(270, 118, 10, 12), false);
        crate(g, r(110, 20, 12, 10), true);

        // stalls sorted by baseline so they overlap correctly
        stalls.stream().sorted(java.util.Comparator.comparingInt(s -> s.box.y + s.box.height)).forEach(s ->
                Tiles.obj(g, s.box.x, s.box.y, s.box.width, s.box.height, 6, 5, 62, (gg, w, h) -> paintStall(gg, w, h, s)));

        // flower pots beside stall bases and the tavern
        for (int[] p : new int[][]{{34, 148}, {204, 138}, {494, 134}, {104, 282}, {540, 282}, {594, 200}}) pot(g, p[0], p[1]);
        pot(g, 80, 70);
        pot(g, 150, 70);

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
            g.setColor(new Color(0xe3f3fb));
            g.fillRect(p.x + 7, p.y + 3, 6, 1);
        }
        g.dispose();
        Tiles.vignette(img);
        return img;
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

    private static void lamp(Graphics2D g, int x, int y) {
        Tiles.obj(g, x - 6, y, 14, 32, 5, 3, 45, (gg, w, h) -> {
            gg.setColor(new Color(0x2f2f38));
            gg.fillRect(5, 9, 3, 20);
            gg.fillRect(2, 27, 9, 4);
            gg.setColor(new Color(0x4a4a56));
            gg.fillRect(5, 9, 1, 20);
            gg.fillRect(0, 0, 13, 4);
            gg.setColor(new Color(0x3a3a46));
            gg.fillRect(1, 3, 11, 9);
            gg.setColor(new Color(0xffe08a));
            gg.fillRect(3, 4, 7, 6);
            gg.setColor(new Color(0xfff6cf));
            gg.fillRect(4, 5, 3, 3);
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

    // ---- buildings

    private void window(Graphics2D g, int x, int y, boolean shutters, boolean flowers) {
        if (shutters) {
            g.setColor(new Color(0x3f7a4a));
            g.fillRect(x - 6, y - 1, 5, 18);
            g.fillRect(x + 21, y - 1, 5, 18);
            g.setColor(new Color(0x2c5636));
            for (int i = 0; i < 4; i++) {
                g.fillRect(x - 6, y + 2 + i * 4, 5, 1);
                g.fillRect(x + 21, y + 2 + i * 4, 5, 1);
            }
        }
        g.setColor(new Color(0x5a3820));
        g.fillRect(x - 1, y - 2, 22, 20);
        g.setColor(new Color(0x86bde0));
        g.fillRect(x + 1, y, 18, 16);
        g.setColor(new Color(0xd7efff));
        g.fillRect(x + 3, y + 2, 5, 3);
        g.setColor(new Color(0x5a3820));
        g.fillRect(x + 9, y, 2, 16);
        g.fillRect(x + 1, y + 7, 18, 2);
        if (flowers) {
            g.setColor(new Color(0x6b4a2b));
            g.fillRect(x - 2, y + 17, 24, 5);
            g.setColor(new Color(0x3f8a3a));
            g.fillRect(x, y + 14, 20, 4);
            int[] c = {0xe74c3c, 0xffd54f, 0xf06292, 0xffffff};
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(c[i % 4]));
                g.fillRect(x + 1 + i * 3, y + 13 + (i % 2), 2, 2);
            }
        }
    }

    private void tavern(Graphics2D g, int w, int h) {
        // plaster wall with timber frame
        g.setColor(new Color(0xe6d3a8));
        g.fillRect(0, 28, w, h - 28);
        g.setColor(new Color(0xd2bd8f));
        for (int i = 0; i < 40; i++) g.fillRect(Math.floorMod(Tiles.hash(i, 5), w), 32 + Math.floorMod(Tiles.hash(i, 8), h - 40), 3, 1);
        g.setColor(new Color(0x7a4a2a));
        for (int x : new int[]{0, 46, 94, 148, 190}) g.fillRect(x, 28, 5, h - 28);
        g.fillRect(0, 28, w, 4);
        g.fillRect(0, h - 8, w, 3);
        g.setColor(new Color(0x9a6a3e));
        g.fillRect(0, 28, w, 1);
        // stone base
        g.setColor(new Color(0x8c8a84));
        g.fillRect(0, h - 5, w, 5);
        g.setColor(new Color(0x6a6860));
        for (int x = 0; x < w; x += 12) g.fillRect(x, h - 5, 1, 5);
        window(g, 26, 36, true, true);
        window(g, 68, 36, true, true);
        window(g, 152, 36, true, true);
        // door
        g.setColor(new Color(0x3a2210));
        g.fillRoundRect(106, 28, 30, 36, 14, 14);
        g.setColor(new Color(0x7a4a28));
        g.fillRoundRect(109, 31, 24, 33, 12, 12);
        g.setColor(new Color(0x5a3820));
        for (int x = 114; x < 132; x += 6) g.fillRect(x, 34, 1, 30);
        g.setColor(new Color(0xf2c230));
        g.fillRect(127, 48, 3, 3);
        // hanging sign
        g.setColor(new Color(0x3a2210));
        g.fillRect(94, 8, 4, 2);
        g.setColor(new Color(0x7a4a28));
        g.fillRect(96, 10, 44, 20);
        g.setColor(new Color(0xd9b57a));
        g.fillRect(98, 12, 40, 16);
        g.setColor(new Color(0xefd29a));
        g.fillRect(98, 12, 40, 2);
        // roof on top
        Tiles.roof(g, 0, 0, w, 30);
        g.setColor(new Color(0x5a2418));
        g.fillRect(0, 26, w, 4);
        g.setColor(new Color(0x7a3322));
        g.fillRect(0, 24, w, 2);
        // chimney
        g.setColor(new Color(0x8a4636));
        g.fillRect(162, -2, 16, 18);
        g.setColor(new Color(0x6a3326));
        g.fillRect(162, -2, 3, 18);
        g.setColor(new Color(0x4a4a52));
        g.fillRect(160, -4, 20, 4);
    }

    private void brickHouse(Graphics2D g, int w, int h) {
        BufferedImage wall = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Tiles.brick(wall, new Rectangle(0, 0, w, h), new Color(0x9c4c3a), new Color(0x6a3024));
        g.drawImage(wall, 0, 0, null);
        window(g, 12, 14, false, false);
        window(g, 48, 14, false, false);
        g.setColor(new Color(0x3a2a22));
        g.fillRect(0, h - 4, w, 4);
        g.setColor(new Color(0xc9a98a));
        g.fillRect(0, 0, w, 3);
    }

    private void sideBuilding(Graphics2D g, int w, int h, boolean right) {
        int roofH = (int) (h * 0.68);
        BufferedImage stone = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Tiles.brick(stone, new Rectangle(0, 0, w, h), new Color(0xa39e92), new Color(0x6f6a60));
        g.drawImage(stone, 0, 0, null);
        if (right) window(g, 12, roofH + 12, false, false);
        Tiles.roof(g, 0, 0, w, roofH);
        g.setColor(new Color(0x5a2418));
        g.fillRect(0, roofH - 4, w, 4);
        g.setColor(new Color(0x7a2f20));
        g.fillRect(right ? 0 : w - 4, 0, 4, roofH);
        if (right) { // chimney
            g.setColor(new Color(0x8a4636));
            g.fillRect(24, 12, 12, 22);
            g.setColor(new Color(0x4a4a52));
            g.fillRect(22, 10, 16, 4);
        }
    }

    // ---- stalls

    private void paintStall(Graphics2D g, int w, int h, Stall s) {
        int top = Math.max(36, h - 26);
        // dark back of the stall
        g.setColor(new Color(0x3b2519));
        g.fillRect(3, 14, w - 6, top - 12);
        g.setColor(new Color(0x4a2f20));
        for (int x = 8; x < w - 4; x += 9) g.fillRect(x, 16, 1, top - 14);
        // shelves on tall stalls
        for (int sy = 36; sy < top - 12; sy += 18) {
            g.setColor(WOOD_L);
            g.fillRect(4, sy, w - 8, 3);
            g.setColor(WOOD_D);
            g.fillRect(4, sy + 3, w - 8, 1);
            goods(g, s.goods, 6, sy - 10, w - 12);
        }
        // hanging goods under the awning
        for (int x = 8; x < w - 8; x += 13) hanging(g, s.goods, x, 22);
        // posts
        g.setColor(new Color(0x6b4526));
        g.fillRect(1, 18, 4, h - 18);
        g.fillRect(w - 5, 18, 4, h - 18);
        g.setColor(new Color(0x9a6a3e));
        g.fillRect(1, 18, 1, h - 18);
        g.fillRect(w - 5, 18, 1, h - 18);
        // counter
        g.setColor(WOOD);
        g.fillRect(0, top + 6, w, h - top - 6);
        g.setColor(new Color(0x6d4426));
        for (int x = 9; x < w - 2; x += 9) g.fillRect(x, top + 8, 1, h - top - 11);
        g.setColor(WOOD_D);
        g.fillRect(0, h - 3, w, 3);
        g.setColor(WOOD_L);
        g.fillRect(-1, top, w + 2, 6);
        g.setColor(new Color(0xe3b57d));
        g.fillRect(-1, top, w + 2, 1);
        // baskets with produce on the counter
        for (int x = 4; x + 15 <= w - 3; x += 17) {
            goods(g, s.goods, x, top - 14, 15);
            g.setColor(new Color(0xa87a45));
            g.fillRect(x, top - 5, 15, 7);
            g.setColor(new Color(0x7b5430));
            for (int i = 2; i < 15; i += 4) g.fillRect(x + i, top - 4, 1, 6);
            g.fillRect(x, top - 2, 15, 1);
            g.setColor(new Color(0xd1a463));
            g.fillRect(x - 1, top - 6, 17, 2);
        }
        // striped awning with scalloped valance
        int sw = 10, n = 0;
        for (int x = 0; x < w; x += sw, n++) {
            Color c = n % 2 == 0 ? s.a : s.b;
            int ww = Math.min(sw, w - x);
            g.setColor(c);
            g.fillRect(x, 0, ww, 16);
            g.fillArc(x, 10, ww, 14, 180, 180);
            g.setColor(Tiles.shade(c, 0.8));
            g.fillRect(x + ww - 2, 0, 2, 17);
            g.fillArc(x + ww - 2, 12, 2, 10, 180, 180);
            g.setColor(Tiles.shade(c, 1.18));
            g.fillRect(x, 3, 2, 12);
        }
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRect(0, 0, w, 3);
        g.setColor(new Color(255, 255, 255, 70));
        g.fillRect(0, 3, w, 1);
    }

    private void hanging(Graphics2D g, Goods kind, int x, int y) {
        switch (kind) {
            case VEG -> {
                g.setColor(new Color(0x6b4a2b));
                g.fillRect(x + 2, y, 1, 3);
                g.setColor(new Color(0xe0b13a));
                g.fillOval(x, y + 3, 5, 5);
                g.fillOval(x + 1, y + 7, 5, 5);
            }
            case FISH -> {
                g.setColor(new Color(0x6b4a2b));
                g.fillRect(x + 2, y, 1, 3);
                g.setColor(new Color(0xb8a58a));
                g.fillOval(x, y + 3, 5, 10);
            }
            case POTS -> {
                g.setColor(new Color(0x3a2210));
                g.fillRect(x + 2, y, 1, 3);
                g.setColor(new Color(0xffe08a));
                g.fillRect(x, y + 3, 5, 6);
                g.setColor(new Color(0xfff6cf));
                g.fillRect(x + 1, y + 4, 2, 3);
            }
            case CLOTH -> {
                g.setColor(new Color(0xc0392b));
                g.fillRect(x, y, 5, 10);
                g.setColor(new Color(0xf0f0f0));
                g.fillRect(x, y + 3, 5, 2);
            }
            default -> {
                g.setColor(new Color(0x6b4a2b));
                g.fillRect(x + 2, y, 1, 3);
                g.setColor(new Color(0xb9864f));
                g.fillOval(x, y + 3, 6, 5);
            }
        }
    }

    private void goods(Graphics2D g, Goods kind, int x, int y, int w) {
        switch (kind) {
            case BREAD -> {
                for (int i = 0; i + 8 <= w; i += 7) {
                    g.setColor(new Color(0xa4702f));
                    g.fillOval(x + i, y + 5 + (i / 7 % 2), 9, 7);
                    g.setColor(new Color(0xd8a05a));
                    g.fillOval(x + i + 1, y + 5 + (i / 7 % 2), 7, 5);
                    g.setColor(new Color(0xa4702f));
                    g.fillRect(x + i + 3, y + 6 + (i / 7 % 2), 1, 3);
                    g.fillRect(x + i + 5, y + 6 + (i / 7 % 2), 1, 3);
                    g.setColor(new Color(0xf3cf8b));
                    g.fillRect(x + i + 2, y + 5 + (i / 7 % 2), 2, 1);
                }
            }
            case VEG -> {
                int[] cols = {0xd63a3a, 0x4caf50, 0xf39c12, 0x8bc34a, 0xc0392b};
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
                    g.setColor(new Color(0xd04a3a));
                    g.fillRect(x + i + 4, y + 8, 1, 3);
                }
            }
            case CLOTH -> {
                for (int i = 0; i < 3; i++) {
                    g.setColor(i % 2 == 0 ? new Color(0xf0f0f0) : new Color(0xc0392b));
                    g.fillRect(x + 1, y + 6 + i * 3, w - 2, 3);
                    g.setColor(new Color(0, 0, 0, 40));
                    g.fillRect(x + 1, y + 8 + i * 3, w - 2, 1);
                }
            }
            case POTS -> {
                for (int i = 0; i + 8 <= w; i += 8) {
                    boolean blue = (i / 8) % 2 == 0;
                    g.setColor(blue ? new Color(0x3b6ea8) : new Color(0x8a97a1));
                    g.fillOval(x + i, y + 6, 8, 8);
                    g.fillRect(x + i + 2, y + 2, 4, 5);
                    g.setColor(blue ? new Color(0x8fb8e8) : new Color(0xc9d3da));
                    g.fillRect(x + i + 2, y + 8, 1, 4);
                    g.setColor(new Color(0x2a2a30));
                    g.fillRect(x + i + 2, y + 1, 4, 1);
                }
            }
        }
    }

    // ------------------------------------------------------------------ decor that reacts to cleanliness

    private static final int[][] FLOWERS = {
            {110, 70}, {120, 132}, {214, 134}, {222, 70}, {40, 150}, {290, 150}, {142, 58}, {180, 58}};

    @Override
    public void paintDecor(Graphics2D g, double clean, double t) {
        if (clean >= 45) {
            int[] cols = {0xf06292, 0xffd54f, 0xffffff, 0xba68c8};
            for (int i = 0; i < FLOWERS.length; i++) {
                int x = FLOWERS[i][0] * 2, y = FLOWERS[i][1] * 2;
                g.setColor(new Color(0x3c8a34));
                g.fillRect(x + 1, y + 3, 2, 5);
                g.setColor(new Color(cols[i % cols.length]));
                g.fillRect(x - 1, y, 6, 3);
                g.fillRect(x + 1, y - 2, 2, 7);
            }
        }
        if (clean >= 60) { // bunting across the north corridor
            for (int x = 270; x < 410; x += 14) {
                int sag = (int) (Math.sin((x - 270) / 140.0 * Math.PI) * 8);
                g.setColor(new Color(new int[]{0xe74c3c, 0xf1c40f, 0x3498db, 0x2ecc71}[(x / 14) % 4]));
                g.fillPolygon(new int[]{x, x + 10, x + 5}, new int[]{26 + sag, 26 + sag, 36 + sag}, 3);
            }
            g.setColor(new Color(0x2a1d1d));
            g.drawLine(270, 26, 410, 26);
        }
        // fountain water jets, stronger when the city is clean
        int cx = FOUNTAIN.x + FOUNTAIN.width / 2, cy = FOUNTAIN.y + 4;
        int jets = clean >= 80 ? 5 : 3;
        for (int i = 0; i < jets; i++) {
            double ph = (t * 1.6 + i * 0.37) % 1.0;
            double ang = (i - (jets - 1) / 2.0) * 0.45;
            int dx = (int) (Math.sin(ang) * 22 * ph);
            int dy = (int) (-26 * Math.sin(ph * Math.PI) * (1 - Math.abs(ang) * 0.4));
            g.setColor(new Color(0xd8f0ff));
            g.fillRect(cx + dx, cy + dy, 3, 3);
        }
        if (clean >= 80) { // sparkles
            for (int i = 0; i < 6; i++) {
                double ph = (t * 0.8 + i * 0.17) % 1.0;
                if (ph < 0.5) {
                    int x = 150 + Math.floorMod(Tiles.hash(i, 1), 340), y = 90 + Math.floorMod(Tiles.hash(i, 2), 190);
                    g.setColor(new Color(255, 255, 200, (int) (200 * (1 - ph * 2))));
                    g.fillRect(x, y - 3, 2, 8);
                    g.fillRect(x - 3, y, 8, 2);
                }
            }
        }
    }
}
