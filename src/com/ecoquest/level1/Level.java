package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static com.ecoquest.level1.Config.*;

/**
 * Base class for every level (Template Method pattern).
 * A new level (Park, Market, Beach...) only extends this and fills in the build* methods.
 */
public abstract class Level {
    public static final int BASE_CLEANLINESS = 30;   // city starts at 30%

    protected final TileType[][] tiles = new TileType[ROWS][COLS];
    protected final List<Prop> props = new ArrayList<>();
    protected final List<Garbage> garbage = new ArrayList<>();
    protected final List<TrashBin> bins = new ArrayList<>();
    private final List<Entity> npcs = new ArrayList<>();
    protected final Random rng = new Random(7);        // fixed seed -> same litter every run
    protected int spawnCol, spawnRow;

    private BufferedImage ground;
    private int totalGarbage, sortedCorrect;

    // ----- what each level must provide -----
    public abstract String getName();
    protected abstract void buildTerrain();   // roads, grass...
    protected abstract void buildProps();     // houses, trees, cars...
    protected abstract void buildBins();
    protected abstract void buildGarbage();

    /** Builds everything. Call once after creating the level. */
    public final Level load() {
        for (TileType[] row : tiles) java.util.Arrays.fill(row, TileType.GRASS);
        buildTerrain();
        addSidewalks();
        buildProps();
        buildBins();
        buildGarbage();
        totalGarbage = garbage.size();
        renderGround();
        return this;
    }

    // ----- terrain helpers -----
    protected void fill(TileType t, int c0, int r0, int c1, int r1) {
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++)
                if (r >= 0 && c >= 0 && r < ROWS && c < COLS) tiles[r][c] = t;
    }

    protected void setTile(TileType t, int c, int r) { fill(t, c, r, c, r); }

    /** Any grass tile touching a road becomes sidewalk. */
    private void addSidewalks() {
        boolean[][] mark = new boolean[ROWS][COLS];
        int[][] d = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                if (tiles[r][c] != TileType.GRASS) continue;
                for (int[] k : d) {
                    int rr = r + k[0], cc = c + k[1];
                    if (rr >= 0 && cc >= 0 && rr < ROWS && cc < COLS && tiles[rr][cc] == TileType.ROAD) mark[r][c] = true;
                }
            }
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) if (mark[r][c]) tiles[r][c] = TileType.SIDEWALK;
    }

    private void renderGround() {
        ground = new BufferedImage(WORLD_W, WORLD_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = ground.createGraphics();
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) TileRenderer.draw(g, tiles, r, c);
        g.dispose();
    }

    // ----- garbage scattering -----
    protected GarbageType randomType() {
        int roll = rng.nextInt(100);
        if (roll < 35) return GarbageType.RECYCLABLE;
        if (roll < 65) return GarbageType.BIODEGRADABLE;
        if (roll < 90) return GarbageType.NON_RECYCLABLE;
        return GarbageType.HAZARDOUS;
    }

    private boolean overlapsScenery(Rectangle2D r) {
        for (Prop p : props) if (!(p instanceof Puddle) && !(p instanceof FlowerBed) && p.bounds().intersects(r)) return true;
        for (TrashBin b : bins) if (b.bounds().intersects(r)) return true;
        for (Garbage g : garbage) if (g.bounds().intersects(r)) return true;
        return false;
    }

    /** Randomly drops {@code count} pieces: mostly on the road and sidewalks, some on lawns. */
    protected void scatterGarbage(int count) {
        double sx = spawnCol * TILE, sy = spawnRow * TILE;
        for (int tries = 0; garbage.size() < count && tries < 8000; tries++) {
            int c = rng.nextInt(COLS), r = rng.nextInt(ROWS), roll = rng.nextInt(100);
            TileType t = tiles[r][c];
            boolean ok = roll < 45 ? t == TileType.ROAD
                       : roll < 80 ? t == TileType.SIDEWALK
                       : (t == TileType.GRASS || t == TileType.PATH);
            if (!ok) continue;
            double gx = c * TILE + 2 + rng.nextInt(TILE - 8), gy = r * TILE + 2 + rng.nextInt(TILE - 8);
            if (Math.hypot(gx - sx, gy - sy) < 30) continue;
            if (overlapsScenery(new Rectangle2D.Double(gx - 2, gy - 2, 12, 12))) continue;
            garbage.add(new Garbage(randomType(), rng.nextInt(2), gx, gy));
        }
    }

    // ----- gameplay queries -----
    public boolean isBlocked(Rectangle2D box) {
        if (box.getMinX() < 0 || box.getMinY() < 0 || box.getMaxX() > WORLD_W || box.getMaxY() > WORLD_H) return true;
        for (Prop p : props) {
            Rectangle2D cb = p.collisionBox();
            if (cb != null && cb.intersects(box)) return true;
        }
        return false;
    }

    public double speedFactorAt(Rectangle2D box) {
        double f = 1.0;
        for (Prop p : props) if (p.speedFactor() < f && p.bounds().intersects(box)) f = p.speedFactor();
        return f;
    }

    public Garbage nearestGarbage(double cx, double cy, double range) {
        Garbage best = null; double bd = range;
        for (Garbage g : garbage) {
            double d = Math.hypot(g.centerX() - cx, g.centerY() - cy);
            if (d <= bd) { bd = d; best = g; }
        }
        return best;
    }

    public TrashBin nearestBin(double cx, double cy, double range) {
        TrashBin best = null; double bd = range;
        for (TrashBin b : bins) {
            double d = Math.hypot(b.centerX() - cx, b.centerY() - cy);
            if (d <= bd) { bd = d; best = b; }
        }
        return best;
    }

    public void removeGarbage(Garbage g) { garbage.remove(g); }

    public void addNpc(Entity npc) { npcs.add(npc); }

    /** A wrongly sorted item falls back onto the ground near the player. */
    public void dropGarbage(GarbageType t, double x, double y) {
        garbage.add(new Garbage(t, rng.nextInt(2), x - 4 + rng.nextInt(7) - 3, y + 2));
    }

    /** The Trash Monster drops new litter near its position. */
    public void dropGarbageFromBoss(double x, double y) {
        double gx = Math.max(0, Math.min(WORLD_W - Garbage.SIZE,
                x - Garbage.SIZE / 2.0 + rng.nextInt(25) - 12));
        double gy = Math.max(0, Math.min(WORLD_H - Garbage.SIZE,
                y + 20 + rng.nextInt(13) - 6));
        garbage.add(new Garbage(randomType(), rng.nextInt(2), gx, gy));
    }

    public void registerCorrectSort() { sortedCorrect++; }

    /** City cleanliness in percent: starts at 30, reaches 100 when everything is sorted. */
    public int cleanliness() {
        if (totalGarbage == 0) return 100;
        int progress = (int) Math.round((100 - BASE_CLEANLINESS) * (double) sortedCorrect / totalGarbage);
        return Math.min(100, BASE_CLEANLINESS + progress);
    }

    public int garbageLeft()       { return garbage.size(); }
    public int getSpawnX()         { return spawnCol * TILE + (TILE - Player.W) / 2; }
    public int getSpawnY()         { return spawnRow * TILE + (TILE - Player.H) / 2; }
    public List<Garbage> getGarbage() { return garbage; }
    public List<TrashBin> getBins()   { return bins; }

    // ----- drawing -----
    public void draw(Graphics2D g, Player player) {
        g.drawImage(ground, 0, 0, null);

        for (Prop p : props) if (p.isGroundDecal()) p.draw(g);
        for (Garbage gb : garbage) gb.draw(g);

        List<Entity> sorted = new ArrayList<>();
        for (Prop p : props) if (!p.isGroundDecal()) sorted.add(p);
        sorted.addAll(bins);
        sorted.addAll(npcs);
        sorted.add(player);
        sorted.sort(Comparator.comparingInt(Entity::sortY));   // lower on screen = drawn in front
        for (Entity e : sorted) e.draw(g);

        // dusty haze that fades as the city gets cleaner
        int alpha = (int) ((100 - cleanliness()) / 100.0 * 55);
        g.setColor(new Color(110, 100, 60, alpha));
        g.fillRect(0, 0, WORLD_W, WORLD_H);
    }
}
