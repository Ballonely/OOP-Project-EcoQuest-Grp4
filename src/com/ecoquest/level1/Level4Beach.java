package com.ecoquest.level1;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class Level4Beach extends Level {
    private static final int SAND_LITTER_COUNT = 64;
    private static final int WATER_LITTER_COUNT = 6;
    private final BufferedImage beach = loadBeach();
    private final boolean[][] seaPixels = buildSeaMask();
    private final int[] waterEdge = buildWaterEdge();
    private final BufferedImage bossArena = createBossArena();
    private boolean bossArenaActive;

    @Override public String getName() { return "Level 4: Sandy Coastal Beach"; }
    @Override protected void buildTerrain() {
        spawnCol = 13;
        spawnRow = 10;
        bossArenaActive = false;   // a restart begins before the boss fight
    }

    @Override protected void buildProps() {
        blockImageRect(0, 0, 1515, 196);
        blockImageRect(1700, 0, 220, 350);
        blockImageRect(700, 264, 155, 77);
        blockImageRect(1090, 258, 190, 154);
        blockImageRect(1345, 418, 405, 115);
        blockImageRect(54, 505, 264, 166);

        // Palm crowns on the sand, in beach-image pixels {x, y, w, h}, measured from the frond
        // colours of beach_reference.png. Each palm gets a trunk collision under the middle of
        // its crown, and its crown is kept free of litter (it would look like it is in the tree).
        // The palms along the top boardwalk sit inside the blocked strip above.
        int[][] palmCrowns = {
            {0, 256, 152, 88}, {344, 240, 144, 96}, {0, 448, 128, 96}, {1376, 280, 144, 88},
            {1216, 544, 152, 96}, {0, 752, 128, 96}, {1120, 800, 152, 96}, {808, 960, 144, 96},
            {0, 992, 144, 96}, {496, 1040, 144, 96}, {184, 1056, 144, 96}
        };
        for (int[] crown : palmCrowns) {
            int trunkX = crown[0] + crown[2] / 2 - 12, trunkTop = crown[1] + crown[3] - 14;
            blockImageRect(trunkX, trunkTop, 24, 48);
            noLitterZones.add(imageRect(crown[0] - 6, crown[1] - 6, crown[2] + 12, crown[3] + 12));
            addPalmOverlay(crown, trunkTop + 48);
        }
        noLitterZones.add(imageRect(1082, 266, 52, 60));   // small palm peeking out behind the lifeguard hut

        // Umbrella + deck chair, measured on beach_reference.png (all ten umbrellas on the sand).
        int[][] umbrellas = {
            {186, 356, 134, 110}, {522, 328, 136, 110}, {875, 350, 132, 104}, {1035, 445, 130, 100},
            {355, 418, 130, 106}, {196, 680, 136, 100}, {957, 670, 134, 102}, {365, 765, 134, 102},
            {885, 835, 135, 104}, {572, 865, 132, 100}
        };
        for (int[] umbrella : umbrellas) blockImageRect(umbrella[0], umbrella[1], umbrella[2], umbrella[3]);
    }

    @Override protected void buildBins() {
        bins.add(new TrashBin(GarbageType.BIODEGRADABLE, 9, 8));
        bins.add(new TrashBin(GarbageType.RECYCLABLE, 10, 8));
        bins.add(new TrashBin(GarbageType.NON_RECYCLABLE, 11, 8));
        bins.add(new TrashBin(GarbageType.HAZARDOUS, 12, 8));
    }

    @Override protected void buildGarbage() {
        scatterGarbage(SAND_LITTER_COUNT);
        scatterWaterLitter(WATER_LITTER_COUNT);
    }

    @Override protected boolean isTrashTileAllowed(double x, double y, TileType tile) {
        if (y <= 48 || y >= Config.WORLD_H - 10 || x >= waterEdgeAt(y) - 10) return false;
        return !bossArenaActive || !isInSewageChannel(x, y);
    }

    public boolean isWaterAt(double x, double y) {
        if (x < 0 || x >= Config.WORLD_W || y < 0 || y >= Config.WORLD_H) return false;
        int imageX = Math.min(beach.getWidth() - 1, (int) (x * beach.getWidth() / Config.WORLD_W));
        double imageScale = imageDrawHeight() / (double) beach.getHeight();
        int imageY = (int) ((y - imageTop()) / imageScale);
        if (imageY < 0 || imageY >= beach.getHeight()) return false;
        return seaPixels[imageY][imageX];
    }

    public void setBossArena(boolean active) { bossArenaActive = active; }

    /** Developer shortcut: an empty beach starts the Sewage Morphling's intro right away. */
    @Override public String devSkipToBoss() {
        garbage.clear();
        return "Sewage Morphling";
    }

    public double[] getBossWaveCenter() {
        double y = 184 + rng.nextDouble() * 78;
        double edge = waterEdgeAt(y);
        double x = edge - 22 - rng.nextDouble() * 34;
        return new double[] {Math.max(24, Math.min(Config.WORLD_W - 30, x)), y};
    }

    public double[] getBossAttackTarget(double playerX, double playerY) {
        for (int attempt = 0; attempt < 60; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double distance = 34 + rng.nextDouble() * 72;
            double x = Math.max(48, Math.min(waterEdgeAt(playerY) - 30,
                    playerX + Math.cos(angle) * distance));
            double y = Math.max(58, Math.min(Config.WORLD_H - 38, playerY + Math.sin(angle) * distance));
            if (!isWaterAt(x, y) && !isBlocked(new java.awt.geom.Rectangle2D.Double(x - 20, y - 20, 40, 40)))
                return new double[] {x, y};
        }
        return getBossWaveCenter();
    }

    public int scatterBossWaveTrash(int amount) {
        int added = 0;
        int waterTarget = amount == 0 ? 0 : Math.max(1, amount / 4);
        int waterAdded = 0, landAdded = 0;
        for (int attempt = 0; attempt < Math.max(500, amount * 500) && added < amount; attempt++) {
            double y = 56 + rng.nextDouble() * (Config.WORLD_H - 74);
            int edge = waterEdgeAt(y);
            boolean water = waterAdded < waterTarget
                    && (landAdded >= amount - waterTarget || rng.nextDouble() < 0.28);
            if (water && edge >= Config.WORLD_W - 24) continue;
            double x;
            if (water) {
                double start = edge + 9;
                x = start + rng.nextDouble() * Math.max(1, Config.WORLD_W - start - 12);
                if (!isWaterAt(x, y)) continue;
            } else {
                if (edge < 38) continue;
                x = 10 + rng.nextDouble() * (edge - 34);
                if (isWaterAt(x, y) || !isTrashTileAllowed(x, y, TileType.GRASS)) continue;
            }

            Rectangle2D.Double itemBounds = new Rectangle2D.Double(x - 2, y - 2, Garbage.SIZE + 4, Garbage.SIZE + 4);
            if (isBlocked(itemBounds) || nearBin(x, y) || nearExistingTrash(x, y, 13)) continue;
            if (!isLitterSpotOk(x, y)) continue;

            Garbage item = water
                    ? new Garbage(GarbageType.BIODEGRADABLE, 1, x, y)
                    : createGarbage(x, y);
            item.markBossWaveLitter();
            garbage.add(item);
            added++;
            if (water) waterAdded++;
            else landAdded++;
        }
        return added;
    }

    public List<double[]> getBossAttackPattern(int volley) {
        List<double[]> targets = new ArrayList<>();
        double[] columns = {40, 112, 184, 256, 328, 400, 472};
        double[] rows = {92, 164, 236};
        int phase = Math.floorMod(volley, 2);
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < columns.length; column++) {
                if ((row + column + phase) % 2 != 0) continue;
                double x = columns[column] + rng.nextDouble() * 10 - 5;
                double y = rows[row] + rng.nextDouble() * 8 - 4;
                double[] safe = findImpactPoint(x, y, targets);
                if (safe != null) targets.add(safe);
            }
        }
        for (int row = 0; row < rows.length && targets.size() < 8; row++) {
            for (int column = 0; column < columns.length && targets.size() < 8; column++) {
                if ((row + column + phase) % 2 == 0) continue;
                double[] safe = findImpactPoint(columns[column], rows[row], targets);
                if (safe != null) targets.add(safe);
            }
        }
        for (int attempt = 0; attempt < 120 && targets.size() < 8; attempt++) {
            double x = WaterImpact.RADIUS + 4 + rng.nextDouble() * (Config.WORLD_W - 2 * WaterImpact.RADIUS - 8);
            double y = WaterImpact.RADIUS + 4 + rng.nextDouble() * (Config.WORLD_H - 2 * WaterImpact.RADIUS - 8);
            double[] safe = findImpactPoint(x, y, targets);
            if (safe != null) targets.add(safe);
        }
        return targets;
    }

    private double[] findImpactPoint(double x, double y, List<double[]> existing) {
        int radius = WaterImpact.RADIUS;
        double[][] offsets = {
            {0, 0}, {0, 34}, {0, -34}, {-34, 0}, {34, 0},
            {-30, 30}, {30, 30}, {-30, -30}, {30, -30},
            {0, 56}, {0, -56}, {-58, 0}, {58, 0}
        };
        for (double[] offset : offsets) {
            double px = x + offset[0], py = y + offset[1];
            if (px < radius + 3 || px > Config.WORLD_W - radius - 3
                    || py < radius + 3 || py > Config.WORLD_H - radius - 3) continue;
            Rectangle2D.Double area = new Rectangle2D.Double(px - radius, py - radius, radius * 2, radius * 2);
            if (isBlocked(area) || Math.hypot(px - 264, py - 130) < 48) continue;
            boolean crowded = false;
            for (double[] point : existing) if (Math.hypot(px - point[0], py - point[1]) < radius * 2 + 9) crowded = true;
            if (!crowded) return new double[] {px, py};
        }
        return null;
    }

    private boolean nearBin(double x, double y) {
        for (TrashBin bin : bins) if (Math.hypot(bin.centerX() - x, bin.centerY() - y) < 46) return true;
        return false;
    }

    private boolean nearExistingTrash(double x, double y, double distance) {
        for (Garbage item : garbage) if (Math.hypot(item.centerX() - x, item.centerY() - y) < distance) return true;
        return false;
    }

    @Override protected void drawGround(Graphics2D g) {
        // Same cropped mapping as the water mask, collision blocks and boss arena.
        if (bossArenaActive) g.drawImage(bossArena, 0, 0, Config.WORLD_W, Config.WORLD_H, null);
        else g.drawImage(beach, 0, imageTop(), Config.WORLD_W, imageDrawHeight(), null);
    }

    private boolean[][] buildSeaMask() {
        int width = beach.getWidth(), height = beach.getHeight();
        boolean[][] sea = new boolean[height][width];
        int[] queue = new int[width * height];
        int tail = 0, head = 0;
        for (int y = 0; y < height; y++) {
            for (int x = Math.max(0, width - 4); x < width; x++) {
                if (isSeaTone(beach.getRGB(x, y))) {
                    sea[y][x] = true;
                    queue[tail++] = y * width + x;
                }
            }
        }
        while (head < tail) {
            int point = queue[head++], x = point % width, y = point / width;
            if (x > 0) tail = addSeaPixel(x - 1, y, width, sea, queue, tail);
            if (x + 1 < width) tail = addSeaPixel(x + 1, y, width, sea, queue, tail);
            if (y > 0) tail = addSeaPixel(x, y - 1, width, sea, queue, tail);
            if (y + 1 < height) tail = addSeaPixel(x, y + 1, width, sea, queue, tail);
        }
        return sea;
    }

    private int addSeaPixel(int x, int y, int width, boolean[][] sea, int[] queue, int tail) {
        if (!sea[y][x] && isSeaTone(beach.getRGB(x, y))) {
            sea[y][x] = true;
            queue[tail++] = y * width + x;
        }
        return tail;
    }

    private boolean isSeaTone(int rgb) {
        int red = (rgb >> 16) & 255, green = (rgb >> 8) & 255, blue = rgb & 255;
        boolean deep = red < 95 && green >= 100 && green <= 195 && blue >= 105 && blue <= 195
                && Math.abs(green - blue) <= 40;
        boolean surf = red >= 95 && red <= 195 && green >= 185 && green <= 235
                && blue >= 170 && blue <= 215 && blue >= red + 10;
        return deep || surf;
    }

    private int[] buildWaterEdge() {
        int[] edge = new int[Config.WORLD_H];
        for (int y = 0; y < edge.length; y++) {
            edge[y] = Config.WORLD_W;
            for (int x = 0; x < Config.WORLD_W; x++) {
                if (isWaterAt(x + 0.5, y + 0.5)) { edge[y] = x; break; }
            }
        }
        return edge;
    }

    private int waterEdgeAt(double y) {
        return waterEdge[Math.max(0, Math.min(waterEdge.length - 1, (int) y))];
    }

    private void scatterWaterLitter(int count) {
        List<Integer> waterRows = new ArrayList<>();
        for (int y = 58; y < Config.WORLD_H - 8; y++)
            if (waterEdgeAt(y) < Config.WORLD_W - 14) waterRows.add(y);
        if (waterRows.isEmpty()) return;
        int misses = 0;
        for (int i = 0; i < count; i++) {
            int y = waterRows.get(rng.nextInt(waterRows.size()));
            int edge = waterEdgeAt(y);
            int span = Math.max(1, Config.WORLD_W - edge - 22);
            int x = edge + 10 + rng.nextInt(span);
            if (!isWaterAt(x, y) || !isLitterSpotOk(x, y)) {
                if (++misses > 4000) return;
                i--;
                continue;
            }
            garbage.add(new Garbage(GarbageType.BIODEGRADABLE, 1, x, y));
        }
    }

    private boolean isInSewageChannel(double x, double y) {
        double[][] points = {{270, 132}, {286, 148}, {278, 170}, {300, 190},
                {333, 200}, {365, 190}, {389, 164}, {408, 137}, {450, 119}};
        for (int i = 0; i < points.length - 1; i++) {
            double ax = points[i][0], ay = points[i][1];
            double dx = points[i + 1][0] - ax, dy = points[i + 1][1] - ay;
            double t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
            double distance = Math.hypot(x - (ax + dx * t), y - (ay + dy * t));
            if (distance < (i < 2 ? 36 : 28)) return true;
        }
        return false;
    }

    /** Palm colours (fronds and trunk) that are cut out to draw over the player. */
    private static final int[] PALM_COLOURS = {0x3E6953, 0x77A273, 0x567F5E, 0x77604C, 0x63564A};

    /** Re-draws a palm's fronds and trunk (only palm-coloured pixels) in front of the player. */
    private void addPalmOverlay(int[] crown, int trunkBottomImageY) {
        Rectangle2D.Double area = imageRect(crown[0], crown[1], crown[2], trunkBottomImageY - crown[1]);
        int x = (int) Math.floor(area.x), y = (int) Math.floor(area.y);
        int w = (int) Math.ceil(area.width) + 1, h = (int) Math.ceil(area.height) + 1;
        double imagePerWorld = beach.getWidth() / (double) Config.WORLD_W;
        BufferedImage art = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int ix = (int) ((x + px + 0.5) * imagePerWorld);
                int iy = (int) ((y + py + 0.5 - imageTop()) * imagePerWorld);
                if (ix < 0 || iy < 0 || ix >= beach.getWidth() || iy >= beach.getHeight()) continue;
                int rgb = beach.getRGB(ix, iy) & 0xFFFFFF;
                for (int palm : PALM_COLOURS) {
                    if (rgb == palm) {
                        art.setRGB(px, py, 0xFF000000 | rgb);
                        break;
                    }
                }
            }
        }
        double trunkBottom = imageTop() + trunkBottomImageY * (Config.WORLD_W / (double) beach.getWidth());
        props.add(new SceneryOverlay(art, x, y, (int) Math.round(trunkBottom)));
    }

    /** A rectangle in beach-image pixels converted to world pixels. */
    private Rectangle2D.Double imageRect(int imageX, int imageY, int imageW, int imageH) {
        double scale = Config.WORLD_W / (double) beach.getWidth();
        return new Rectangle2D.Double(imageX * scale, imageTop() + imageY * scale, imageW * scale, imageH * scale);
    }

    private void blockImageRect(int imageX, int imageY, int imageW, int imageH) {
        double scale = Config.WORLD_W / (double) beach.getWidth();
        int x = (int) Math.floor(imageX * scale);
        int y = (int) Math.floor(imageTop() + imageY * scale);
        int w = Math.max(1, (int) Math.ceil(imageW * scale));
        int h = Math.max(1, (int) Math.ceil(imageH * scale));
        int left = Math.max(0, x), top = Math.max(0, y);
        int right = Math.min(Config.WORLD_W, x + w), bottom = Math.min(Config.WORLD_H, y + h);
        if (right > left && bottom > top) props.add(new StaticBlock(left, top, right - left, bottom - top));
    }

    private int imageDrawHeight() {
        return (int) Math.ceil(beach.getHeight() * (Config.WORLD_W / (double) beach.getWidth()));
    }

    private int imageTop() { return (Config.WORLD_H - imageDrawHeight()) / 2; }

    private BufferedImage createBossArena() {
        BufferedImage arena = new BufferedImage(Config.WORLD_W, Config.WORLD_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = arena.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(beach, 0, imageTop(), Config.WORLD_W, imageDrawHeight(), null);
        g.setColor(new Color(46, 62, 42, 78));
        g.fillRect(0, 0, Config.WORLD_W, Config.WORLD_H);

        Path2D channel = new Path2D.Double();
        channel.moveTo(268, 132);
        channel.curveTo(307, 144, 256, 165, 284, 180);
        channel.curveTo(309, 195, 327, 211, 360, 192);
        channel.curveTo(391, 176, 388, 146, 417, 127);
        channel.curveTo(448, 107, 480, 111, 522, 94);
        g.setStroke(new BasicStroke(70, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(113, 135, 83, 220));
        g.draw(channel);
        g.setStroke(new BasicStroke(61, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(43, 79, 63, 244));
        g.draw(channel);
        g.setStroke(new BasicStroke(42, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(34, 63, 53, 245));
        g.draw(channel);

        g.setColor(new Color(39, 73, 55, 246));
        g.fillOval(224, 102, 89, 70);
        g.setColor(new Color(139, 164, 93, 215));
        g.setStroke(new BasicStroke(3));
        g.drawOval(224, 102, 89, 70);
        g.setColor(new Color(141, 179, 130, 155));
        for (int i = 0; i < 11; i++) {
            int x = 245 + i * 17;
            int y = 134 + (i * 29 % 78);
            g.drawLine(x, y, x + 8, y - 1);
        }
        g.dispose();
        return arena;
    }

    private BufferedImage loadBeach() {
        try { return ImageIO.read(new File("assets/maps/beach_reference.png")); }
        catch (Exception e) { throw new IllegalStateException("Beach reference image is missing.", e); }
    }
}
