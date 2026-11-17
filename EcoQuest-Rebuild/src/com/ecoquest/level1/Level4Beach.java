package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class Level4Beach extends Level {
    private final BufferedImage beach = loadBeach();
    private final boolean[][] seaPixels = buildSeaMask();
    private final int[] waterEdge = buildWaterEdge();

    @Override public String getName() { return "Level 4: Sandy Coastal Beach"; }
    @Override protected void buildTerrain() { spawnCol = 10; spawnRow = 10; }

    @Override protected void buildProps() {
        blockImageRect(0, 0, 750, 128);
        blockImageRect(865, 0, 99, 126);
        blockImageRect(360, 130, 70, 36);
        blockImageRect(554, 126, 78, 63);
        blockImageRect(678, 212, 190, 54);
        blockImageRect(58, 270, 98, 46);

        int[][] palms = {{26, 139}, {190, 138}, {790, 91}, {820, 135}, {28, 273},
                {27, 399}, {28, 518}, {123, 562}, {280, 562}, {438, 493}, {592, 425}, {642, 291}};
        for (int[] palm : palms) blockImageRect(palm[0], palm[1], 12, 25);

        int[][] umbrellas = {{96, 180, 68, 49}, {263, 166, 76, 55}, {437, 176, 74, 55},
                {517, 217, 73, 63}, {98, 340, 75, 60}, {478, 330, 76, 60},
                {183, 411, 76, 59}, {446, 414, 76, 62}};
        for (int[] umbrella : umbrellas) blockImageRect(umbrella[0], umbrella[1], umbrella[2], umbrella[3]);
    }

    @Override protected void buildBins() {
        bins.add(new TrashBin(GarbageType.BIODEGRADABLE, 14, 8));
        bins.add(new TrashBin(GarbageType.RECYCLABLE, 15, 8));
        bins.add(new TrashBin(GarbageType.NON_RECYCLABLE, 16, 8));
        bins.add(new TrashBin(GarbageType.HAZARDOUS, 17, 8));
    }

    @Override protected void buildGarbage() {
        scatterGarbage(120);
        scatterWaterLitter(12);
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

    public int getSandyEdgeX(double y) { return Math.max(8, waterEdgeAt(y) - 14); }

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
        for (int i = 0; i < count; i++) {
            int y = waterRows.get(rng.nextInt(waterRows.size()));
            int edge = waterEdgeAt(y);
            int x = edge + 10 + rng.nextInt(Math.max(1, Config.WORLD_W - edge - 22));
            if (!isWaterAt(x, y)) { i--; continue; }
            garbage.add(new Garbage(GarbageType.BIODEGRADABLE, i % 2 == 0 ? 1 : 5, x, y));
        }
    }

    private void blockImageRect(int imageX, int imageY, int imageW, int imageH) {
        double sx = Config.WORLD_W / (double) beach.getWidth();
        double sy = imageDrawHeight() / (double) beach.getHeight();
        int x = (int) Math.floor(imageX * sx);
        int y = (int) Math.floor(imageTop() + imageY * sy);
        int w = Math.max(1, (int) Math.ceil(imageW * sx));
        int h = Math.max(1, (int) Math.ceil(imageH * sy));
        int top = Math.max(0, y), bottom = Math.min(Config.WORLD_H, y + h);
        if (bottom > top) props.add(new StaticBlock(Math.max(0, x), top,
                Math.min(w, Config.WORLD_W - Math.max(0, x)), bottom - top));
    }

    @Override protected void drawGround(Graphics2D g) {
        g.drawImage(beach, 0, imageTop(), Config.WORLD_W, imageDrawHeight(), null);
    }

    private int imageDrawHeight() { return (int) Math.ceil(beach.getHeight() * (Config.WORLD_W / (double) beach.getWidth())); }
    private int imageTop() { return (Config.WORLD_H - imageDrawHeight()) / 2; }

    private BufferedImage loadBeach() {
        try { return ImageIO.read(new File("assets/maps/beach_reference.png")); }
        catch (Exception e) { throw new IllegalStateException("Beach reference image is missing.", e); }
    }
}
