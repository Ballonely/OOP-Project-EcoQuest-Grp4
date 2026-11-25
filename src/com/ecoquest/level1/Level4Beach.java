package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
    /** Cut-out scenery drawn over the player, with a murky copy for the boss arena. */
    private final List<Overlay> overlays = new ArrayList<>();
    private boolean bossArenaActive;

    private record Overlay(SceneryOverlay prop, BufferedImage normal, BufferedImage murky) {}

    @Override public String getName() { return "Level 4: Sandy Coastal Beach"; }
    @Override public String introMessage() {
        return "Clean the beach! Sort the litter into the bins - walk into the sea to take the boat to floating trash.";
    }
    @Override protected void buildTerrain() {
        spawnCol = 13;
        spawnRow = 10;
        bossArenaActive = false;   // a restart begins before the boss fight
        overlays.clear();          // rebuilt with the props
        bossSpotX = 264;           // the boss starts back at its home spot
        bossSpotY = 130;
    }

    /*
     * Collision rule for the whole beach (all numbers are beach_reference.png pixels, measured from
     * the art): anything that stands up blocks only its footprint - palm trunks, umbrella poles,
     * deck chairs, benches, posts, the hut, the net - and tall parts above the footprint (palm
     * fronds, umbrella canopies) are re-drawn in front of the player instead of blocking. Flat things
     * (towels, the volleyball court, shells, the boardwalk and the pier deck) are always walkable.
     */
    @Override protected void buildProps() {
        // Town, grass and cliffs around the beach: never walkable.
        blockImageRect(0, 0, 1515, 196);        // shops and lawn above the boardwalk
        blockImageRect(1515, 0, 135, 172);      // lawn and palms beside the cliff
        blockImageRect(1644, 0, 56, 262);       // cliff rock
        blockImageRect(1700, 0, 220, 350);      // lighthouse rock

        // Boardwalk: the planks are walkable, the benches on them are not.
        for (int benchX : new int[] {268, 603, 958, 1303}) blockImageRect(benchX, 207, 80, 45);

        blockImageRect(714, 262, 140, 58);      // notice board
        blockImageRect(1112, 264, 132, 122);    // lifeguard hut on its stilts
        blockImageRect(1244, 270, 8, 80);       // the hut's flag pole
        blockImageRect(1329, 392, 12, 20);      // surfboards stuck in the sand
        blockImageRect(1379, 400, 16, 22);
        blockImageRect(108, 578, 194, 36);      // volleyball net and posts (the court is open)
        blockImageRect(0, 1112, 98, 72);        // bushes in the bottom-left corner
        blockImageRect(112, 1128, 62, 44);

        // Pier: walk out on the deck; the railings and the end post keep you (and the boat) off it.
        blockImageRect(1350, 418, 377, 18);     // north railing
        blockImageRect(1350, 500, 377, 20);     // south railing
        blockImageRect(1712, 418, 15, 102);     // end of the pier

        // Palms {crownX, crownY, crownW, crownH, trunkX, trunkBottom}: the trunk's foot blocks, the
        // crown is drawn over the player, and no litter is placed under the crown.
        int[][] palms = {
            {0, 256, 152, 88, 64, 369}, {344, 240, 144, 96, 405, 358}, {0, 448, 128, 96, 44, 566},
            {1376, 280, 144, 88, 1439, 392}, {1216, 544, 152, 96, 1281, 659}, {0, 752, 128, 96, 47, 869},
            {1120, 800, 152, 96, 1188, 919}, {808, 960, 144, 96, 869, 1077}, {0, 992, 144, 96, 58, 1107},
            {496, 1040, 144, 96, 558, 1158}, {184, 1056, 144, 96, 247, 1172},
            {1462, 120, 150, 100, 1528, 237}, {1566, 176, 124, 90, 1604, 290}   // beside the cliff
        };
        for (int[] palm : palms) {
            blockImageRect(palm[4] + 2, palm[5] - 24, 20, 26);
            noLitterZones.add(imageRect(palm[0] - 6, palm[1] - 6, palm[2] + 12, palm[3] + 12));
            addCutoutOverlay(palm[0], palm[1], palm[2], palm[5] - palm[1], palm[5], Level4Beach::isPalmColour);
        }
        noLitterZones.add(imageRect(1082, 266, 52, 60));   // small palm peeking out behind the lifeguard hut

        // Umbrellas, by the top-left of the canopy. Every umbrella has the same layout: a 102x63
        // canopy, the pole below its middle, and a deck chair to its right.
        int[][] umbrellas = {
            {189, 362}, {523, 332}, {873, 353}, {1032, 446}, {354, 419},
            {199, 684}, {957, 670}, {367, 765}, {886, 835}, {574, 864}
        };
        for (int[] u : umbrellas) {
            int cx = u[0], cy = u[1];
            blockImageRect(cx + 45, cy + 76, 12, 16);        // pole foot
            blockImageRect(cx + 103, cy + 31, 28, 72);       // deck chair
            addCutoutOverlay(cx - 2, cy - 2, 104, 94, cy + 91, rgb -> !isSandColour(rgb));
        }
    }

    /** Sand and its painted shadows, i.e. ground that is never cut out as scenery. */
    private static boolean isSandColour(int rgb) {
        for (int sand : SAND_COLOURS) if (rgb == sand) return true;
        return false;
    }

    private static boolean isPalmColour(int rgb) {
        for (int palm : PALM_COLOURS) if (rgb == palm) return true;
        return false;
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

    /** Beach litter (bottles, butts, wrappers, straws...) instead of the street items. */
    @Override protected Garbage createGarbage(double x, double y) {
        return new BeachGarbage(BeachGarbage.pickSand(rng), x, y);
    }

    @Override protected boolean isTrashTileAllowed(double x, double y, TileType tile) {
        return y > 48 && y < Config.WORLD_H - 10 && x < waterEdgeAt(y) - 10;
    }

    public boolean isWaterAt(double x, double y) {
        if (x < 0 || x >= Config.WORLD_W || y < 0 || y >= Config.WORLD_H) return false;
        int imageX = Math.min(beach.getWidth() - 1, (int) (x * beach.getWidth() / Config.WORLD_W));
        double imageScale = imageDrawHeight() / (double) beach.getHeight();
        int imageY = (int) ((y - imageTop()) / imageScale);
        if (imageY < 0 || imageY >= beach.getHeight()) return false;
        return seaPixels[imageY][imageX];
    }

    /** Middle of the boss (moves when it burrows); geysers never erupt right under it. */
    private double bossSpotX = 264, bossSpotY = 130;

    public void setBossSpot(double x, double y) {
        bossSpotX = x;
        bossSpotY = y;
    }

    /**
     * Where the Morphling surfaces after burrowing: the top-left of its 80x72 sprite, on dry sand,
     * away from the bins and from where it is now. Often near the player, so they have to move.
     */
    public double[] getBurrowTarget(double playerX, double playerFeetY, double currentX, double currentY) {
        for (int attempt = 0; attempt < 120; attempt++) {
            double fx, fy;
            if (attempt < 60 && rng.nextDouble() < 0.6) {
                double a = rng.nextDouble() * Math.PI * 2, d = 30 + rng.nextDouble() * 60;
                fx = playerX + Math.cos(a) * d;
                fy = playerFeetY + Math.sin(a) * d * 0.7;
            } else {
                fx = 46 + rng.nextDouble() * (Config.WORLD_W - 92);
                fy = 98 + rng.nextDouble() * (Config.WORLD_H - 110);
            }
            double x = fx - 40, y = fy - 62;
            if (x < 6 || x > Config.WORLD_W - 86 || y < 36 || y > Config.WORLD_H - 74) continue;
            if (isWaterAt(fx, fy) || isWaterAt(fx - 30, fy) || isWaterAt(fx + 30, fy)) continue;
            if (Math.hypot(fx - (currentX + 40), fy - (currentY + 62)) < 90) continue;
            if (nearBin(fx, fy) || nearBin(fx, fy - 30)) continue;
            return new double[] {x, y};
        }
        return new double[] {currentX, currentY};
    }

    /**
     * The Morphling's burrow exit centred on the feet point (fx, fy), as its sprite's top-left kept
     * inside the map, or null when that point is in the sea (it can't surface in the water).
     */
    public double[] burrowSpotAt(double fx, double fy) {
        if (isWaterAt(fx, fy)) return null;
        double x = Math.max(6, Math.min(Config.WORLD_W - 86, fx - 40));
        double y = Math.max(36, Math.min(Config.WORLD_H - 74, fy - 62));
        if (isWaterAt(x + 40, y + 62)) return null;
        return new double[] {x, y};
    }

    public void setBossArena(boolean active) {
        bossArenaActive = active;
        for (Overlay overlay : overlays) overlay.prop().setImage(active ? overlay.murky() : overlay.normal());
    }

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
                    ? new BeachGarbage(BeachGarbage.pickFloating(rng), x, y)
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
            if (isBlocked(area) || Math.hypot(px - bossSpotX, py - bossSpotY) < 48) continue;
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
        fillSeaHoles(sea, width, height, queue);
        return sea;
    }

    /**
     * Buoys and other little things painted in the sea count as water too: only land joined to the
     * left or top edge of the map (the beach, the pier, the cliffs) stays land. Otherwise the boat
     * would drop its rider onto a buoy in the middle of the sea.
     */
    private static void fillSeaHoles(boolean[][] sea, int width, int height, int[] queue) {
        boolean[][] land = new boolean[height][width];
        int tail = 0, head = 0;
        for (int y = 0; y < height; y++) if (!sea[y][0]) { land[y][0] = true; queue[tail++] = y * width; }
        for (int x = 1; x < width; x++) if (!sea[0][x]) { land[0][x] = true; queue[tail++] = x; }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (head < tail) {
            int point = queue[head++], x = point % width, y = point / width;
            for (int[] step : steps) {
                int nx = x + step[0], ny = y + step[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height || land[ny][nx] || sea[ny][nx]) continue;
                land[ny][nx] = true;
                queue[tail++] = ny * width + nx;
            }
        }
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) if (!land[y][x]) sea[y][x] = true;
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
            garbage.add(new BeachGarbage(BeachGarbage.pickFloating(rng), x, y));
        }
    }

    /** Palm colours (fronds and trunk) that are cut out to draw over the player. */
    private static final int[] PALM_COLOURS = {0x3E6953, 0x77A273, 0x567F5E, 0x77604C, 0x63564A};

    private static final Color ARENA_TINT = new Color(46, 62, 42, 78);

    private static final int[] SAND_COLOURS = {
        0xF2D68E, 0xEAC47A, 0xD6B169, 0xD6AE66, 0xD7AD73, 0xD4AD67, 0xE8CA83
    };

    /**
     * Re-draws part of the beach art (only the pixels {@code keep} accepts) in front of the player
     * while they stand behind it, i.e. while their feet are above {@code baseImageY}.
     */
    private void addCutoutOverlay(int imageX, int imageY, int imageW, int imageH, int baseImageY,
                                  java.util.function.IntPredicate keep) {
        Rectangle2D.Double area = imageRect(imageX, imageY, imageW, imageH);
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
                if (keep.test(rgb)) art.setRGB(px, py, 0xFF000000 | rgb);
            }
        }
        double base = imageTop() + baseImageY * (Config.WORLD_W / (double) beach.getWidth());
        SceneryOverlay overlay = new SceneryOverlay(art, x, y, (int) Math.round(base));
        overlays.add(new Overlay(overlay, art, tinted(art)));
        props.add(overlay);
    }

    /** A copy of {@code art} with the boss arena's murky tint baked in, so it matches the ground. */
    private static BufferedImage tinted(BufferedImage art) {
        BufferedImage copy = new BufferedImage(art.getWidth(), art.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(art, 0, 0, null);
        g.setComposite(java.awt.AlphaComposite.SrcAtop);
        g.setColor(ARENA_TINT);
        g.fillRect(0, 0, art.getWidth(), art.getHeight());
        g.dispose();
        return copy;
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
        g.setColor(ARENA_TINT);
        g.fillRect(0, 0, Config.WORLD_W, Config.WORLD_H);

        g.dispose();
        pollute(arena);
        return arena;
    }

    /** Sewage slicks and floating scum dotted over the sea, so the boss fight's ocean looks polluted. */
    private void pollute(BufferedImage arena) {
        int w = Config.WORLD_W, h = Config.WORLD_H;
        for (int y = 0; y < h; y++)               // a sickly green cast over all the water
            for (int x = 0; x < w; x++)
                if (isWaterAt(x + 0.5, y + 0.5)) blend(arena, x, y, 0x3E6B3A, 0.22);

        for (int slick = 0, tries = 0; slick < 24 && tries < 2000; tries++) {
            double cx = rng.nextDouble() * w, cy = 40 + rng.nextDouble() * (h - 46);
            if (!isWaterAt(cx, cy) || cx < waterEdgeAt(cy) + 8) continue;
            double rx = 7 + rng.nextDouble() * 15, ry = rx * (0.45 + rng.nextDouble() * 0.2);
            double p1 = rng.nextDouble() * 6.3, p2 = rng.nextDouble() * 6.3;
            for (int y = (int) (cy - ry * 1.4); y <= cy + ry * 1.4; y++) {
                for (int x = (int) (cx - rx * 1.4); x <= cx + rx * 1.4; x++) {
                    if (x < 0 || y < 0 || x >= w || y >= h || !isWaterAt(x + 0.5, y + 0.5)) continue;
                    double dx = (x - cx) / rx, dy = (y - cy) / ry, angle = Math.atan2(dy, dx);
                    double edge = 1 + 0.2 * Math.sin(angle * 3 + p1) + 0.1 * Math.sin(angle * 5 + p2);
                    double d = Math.hypot(dx, dy) / edge;
                    if (d > 1) continue;
                    if (d < 0.6) blend(arena, x, y, 0x2C4A2C, 0.78);          // thick sludge
                    else if (d < 0.85) blend(arena, x, y, 0x4E7A40, 0.72);
                    else blend(arena, x, y, 0x86B05A, 0.6);                   // scummy rim
                }
            }
            for (int bubble = 0; bubble < 4; bubble++) {                       // bubbles in the sludge
                int bx = (int) (cx + (rng.nextDouble() * 2 - 1) * rx * 0.5);
                int by = (int) (cy + (rng.nextDouble() * 2 - 1) * ry * 0.5);
                if (bx >= 0 && by >= 0 && bx < w && by < h) blend(arena, bx, by, 0xA8D07A, 0.85);
            }
            slick++;
        }

        for (int speck = 0, tries = 0; speck < 140 && tries < 4000; tries++) {   // floating scum bits
            int x = rng.nextInt(w), y = rng.nextInt(h);
            if (!isWaterAt(x + 0.5, y + 0.5)) continue;
            int colour = rng.nextInt(3) == 0 ? 0x86B05A : 0x4E7A40;
            blend(arena, x, y, colour, 0.75);
            if (rng.nextBoolean() && x + 1 < w) blend(arena, x + 1, y, colour, 0.6);
            speck++;
        }
    }

    private static void blend(BufferedImage image, int x, int y, int rgb, double amount) {
        int base = image.getRGB(x, y);
        int r = (int) (((base >> 16) & 255) * (1 - amount) + ((rgb >> 16) & 255) * amount);
        int g = (int) (((base >> 8) & 255) * (1 - amount) + ((rgb >> 8) & 255) * amount);
        int b = (int) ((base & 255) * (1 - amount) + (rgb & 255) * amount);
        image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
    }


    private BufferedImage loadBeach() {
        try { return ImageIO.read(new File("assets/maps/beach_reference.png")); }
        catch (Exception e) { throw new IllegalStateException("Beach reference image is missing.", e); }
    }
}
