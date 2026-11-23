package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static com.ecoquest.level1.Config.*;

/**
 * Base class for every level (Template Method pattern).
 * A new level (Park, Market, Beach...) only extends this and fills in the build* methods.
 */
public abstract class Level {
    protected final TileType[][] tiles = new TileType[ROWS][COLS];
    protected final List<Prop> props = new ArrayList<>();
    protected final List<Garbage> garbage = new ArrayList<>();
    protected final List<TrashBin> bins = new ArrayList<>();
    private final List<Entity> npcs = new ArrayList<>();
    private final List<Prop> temporaryProps = new ArrayList<>();
    protected final Random rng = new Random();
    protected int spawnCol, spawnRow;

    private BufferedImage ground;
    private int totalGarbage, sortedCorrect, savedProgressFloor;

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

    protected void drawGround(Graphics2D g) { g.drawImage(ground, 0, 0, null); }

    protected boolean isTrashTileAllowed(double x, double y, TileType tile) {
        return tile == TileType.ROAD || tile == TileType.SIDEWALK || tile == TileType.GRASS || tile == TileType.PATH;
    }

    // ----- garbage scattering -----
    protected GarbageType randomType() {
        int roll = rng.nextInt(100);
        if (roll < 35) return GarbageType.RECYCLABLE;
        if (roll < 65) return GarbageType.BIODEGRADABLE;
        if (roll < 90) return GarbageType.NON_RECYCLABLE;
        return GarbageType.HAZARDOUS;
    }

    protected Garbage createGarbage(double x, double y) {
        return new Garbage(randomType(), rng.nextInt(6), x, y);
    }

    private boolean overlapsScenery(Rectangle2D r) {
        for (Prop p : props) if (!p.isGroundDecal() && !(p instanceof Puddle) && !(p instanceof FlowerBed) && p.bounds().intersects(r)) return true;
        for (TrashBin b : bins) if (b.bounds().intersects(r)) return true;
        for (Garbage g : garbage) if (g.bounds().intersects(r)) return true;
        return false;
    }

    /** Randomly drops {@code count} pieces: mostly on the road and sidewalks, some on lawns. */
    protected void scatterGarbage(int count) {
        List<int[]> cells = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            double x = c * TILE + TILE / 2.0, y = r * TILE + TILE / 2.0;
            if (isTrashTileAllowed(x, y, tiles[r][c])) cells.add(new int[]{c, r});
        }
        java.util.Collections.shuffle(cells, rng);
        double sx = spawnCol * TILE, sy = spawnRow * TILE;
        for (int[] cell : cells) {
            if (garbage.size() >= count) break;
            double gx = cell[0] * TILE + 3 + rng.nextInt(TILE - 9);
            double gy = cell[1] * TILE + 3 + rng.nextInt(TILE - 9);
            if (Math.hypot(gx - sx, gy - sy) < 30) continue;
            if (overlapsScenery(new Rectangle2D.Double(gx - 2, gy - 2, 12, 12)) || tooNearBin(gx, gy)) continue;
            garbage.add(createGarbage(gx, gy));
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
        for (Prop p : props) {
            boolean overlaps = p instanceof WaterImpact impact
                    ? impact.overlapsSlowZone(box) : p.bounds().intersects(box);
            if (p.speedFactor() < f && overlaps) f = p.speedFactor();
        }
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

    public int addRandomLitterWave(int amount) {
        int progressBefore = getProgressPercent();
        int groundBefore = garbage.size();
        scatterGarbage(groundBefore + Math.max(0, amount));
        int added = garbage.size() - groundBefore;
        totalGarbage += added;
        savedProgressFloor = Math.max(savedProgressFloor, progressBefore);
        return added;
    }

    public int addRandomLitterNear(double centerX, double centerY, int amount, double radius) {
        int progressBefore = getProgressPercent();
        int before = garbage.size();
        for (int attempt = 0; attempt < Math.max(40, amount * 45) && garbage.size() - before < amount; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(rng.nextDouble()) * radius;
            double x = Math.max(4, Math.min(WORLD_W - Garbage.SIZE - 4,
                    centerX + Math.cos(angle) * distance));
            double y = Math.max(50, Math.min(WORLD_H - Garbage.SIZE - 8,
                    centerY + Math.sin(angle) * distance));
            int col = Math.min(COLS - 1, (int) (x / TILE));
            int row = Math.min(ROWS - 1, (int) (y / TILE));
            if (!isTrashTileAllowed(x, y, tiles[row][col])) continue;
            if (overlapsScenery(new Rectangle2D.Double(x - 2, y - 2, 12, 12)) || tooNearBin(x, y)) continue;
            garbage.add(createGarbage(x, y));
        }
        int added = garbage.size() - before;
        totalGarbage += added;
        savedProgressFloor = Math.max(savedProgressFloor, progressBefore);
        return added;
    }

    public int getSortedCorrect() { return sortedCorrect; }

    public void addNpc(Entity npc) { npcs.add(npc); }

    public void addTemporaryProp(Prop prop) {
        props.add(prop);
        temporaryProps.add(prop);
    }

    public void updateTemporaryProps(double dt) {
        Iterator<Prop> iterator = temporaryProps.iterator();
        while (iterator.hasNext()) {
            Prop prop = iterator.next();
            if (prop instanceof TimedProp timed && !timed.update(dt)) {
                props.remove(prop);
                iterator.remove();
            }
        }
    }

    public boolean applyTemporaryEffects(Player player) {
        boolean hit = false;
        for (Prop prop : temporaryProps) {
            if (prop instanceof WaterImpact impact && impact.tryHitPlayer(player)) hit = true;
        }
        return hit;
    }

    /** A wrongly sorted item falls back onto the ground near the player. */
    public void dropGarbage(GarbageType t, double x, double y) {
        double[] safe = safeDropPoint(x, y);
        garbage.add(new Garbage(t, rng.nextInt(2), safe[0], safe[1]));
    }

    public void dropGarbage(Garbage item, double x, double y) {
        double[] safe = safeDropPoint(x, y);
        garbage.add(item.copyAt(safe[0], safe[1]));
    }

    private boolean tooNearBin(double x, double y) {
        for (TrashBin bin : bins) if (Math.hypot(bin.centerX() - x, bin.centerY() - y) < 48) return true;
        return false;
    }

    private double[] safeDropPoint(double originX, double originY) {
        for (int attempt = 0; attempt < 500; attempt++) {
            double x, y;
            if (attempt < 140) {
                double angle = rng.nextDouble() * Math.PI * 2;
                double distance = 50 + rng.nextDouble() * 65;
                x = originX + Math.cos(angle) * distance;
                y = originY + Math.sin(angle) * distance;
            } else {
                x = rng.nextDouble() * (WORLD_W - Garbage.SIZE - 8) + 4;
                y = rng.nextDouble() * (WORLD_H - Garbage.SIZE - 8) + 4;
            }
            x = Math.max(4, Math.min(WORLD_W - Garbage.SIZE - 4, x));
            y = Math.max(4, Math.min(WORLD_H - Garbage.SIZE - 4, y));
            int col = Math.min(COLS - 1, (int) (x / TILE)), row = Math.min(ROWS - 1, (int) (y / TILE));
            if (isTrashTileAllowed(x, y, tiles[row][col]) && !tooNearBin(x, y)
                    && !overlapsScenery(new Rectangle2D.Double(x - 2, y - 2, 12, 12))) return new double[]{x, y};
        }
        return new double[]{Math.max(4, Math.min(WORLD_W - Garbage.SIZE - 4, originX)),
                Math.max(4, Math.min(WORLD_H - Garbage.SIZE - 4, originY))};
    }

    /** The Trash Monster drops new litter near its position. */
    public void dropGarbageFromBoss(double x, double y) {
        int progressBefore = getProgressPercent();
        double gx = Math.max(0, Math.min(WORLD_W - Garbage.SIZE,
                x - Garbage.SIZE / 2.0 + rng.nextInt(25) - 12));
        double gy = Math.max(0, Math.min(WORLD_H - Garbage.SIZE,
                y + 20 + rng.nextInt(13) - 6));
        double[] safe = safeDropPoint(gx, gy);
        garbage.add(createGarbage(safe[0], safe[1]));
        totalGarbage++;
        savedProgressFloor = Math.max(savedProgressFloor, progressBefore);
    }

    public void registerCorrectSort() { sortedCorrect++; }

    public void restoreProgress(int percent) {
        savedProgressFloor = Math.max(0, Math.min(100, percent));
        sortedCorrect = Math.max(0, Math.min(totalGarbage, (int) Math.round(totalGarbage * savedProgressFloor / 100.0)));
        while (garbage.size() > totalGarbage - sortedCorrect) garbage.remove(garbage.size() - 1);
    }

    public int getProgressPercent() {
        return totalGarbage == 0 ? savedProgressFloor
                : Math.max(savedProgressFloor, Math.min(100, (int) Math.round(100.0 * sortedCorrect / totalGarbage)));
    }

    public int garbageLeft()       { return garbage.size(); }
    public int getSpawnX()         { return spawnCol * TILE + (TILE - Player.W) / 2; }
    public int getSpawnY()         { return spawnRow * TILE + (TILE - Player.H) / 2; }
    public List<Garbage> getGarbage() { return garbage; }
    public List<TrashBin> getBins()   { return bins; }

    // ----- drawing -----
    public void draw(Graphics2D g, Player player) {
        drawGround(g);

        for (Prop p : props) if (p.isGroundDecal()) p.draw(g);
        for (Garbage gb : garbage) gb.draw(g);

        List<Entity> sorted = new ArrayList<>();
        for (Prop p : props) if (!p.isGroundDecal()) sorted.add(p);
        sorted.addAll(bins);
        sorted.addAll(npcs);
        sorted.add(player);
        sorted.sort(Comparator.comparingInt(Entity::sortY));   // lower on screen = drawn in front
        for (Entity e : sorted) e.draw(g);

        // The haze fades as this area's cleanup progresses.
        int alpha = (int) ((100 - getProgressPercent()) / 100.0 * 38);
        g.setColor(new Color(110, 100, 60, alpha));
        g.fillRect(0, 0, WORLD_W, WORLD_H);
    }
}
