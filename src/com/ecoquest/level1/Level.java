package com.ecoquest.level1;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
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
    public static final int BASE_CLEANLINESS = 0;    // every level starts at 0% clean
    /** Opacity of anything drawn in front of the player while it overlaps them. */
    private static final float SEE_THROUGH_ALPHA = 0.45f;
    private static final int CLEANLINESS_LOSS_PER_BOSS_TRASH = 2;

    protected final TileType[][] tiles = new TileType[ROWS][COLS];
    protected final List<Prop> props = new ArrayList<>();
    protected final List<Garbage> garbage = new ArrayList<>();
    protected final List<TrashBin> bins = new ArrayList<>();
    private final List<Entity> npcs = new ArrayList<>();
    private final List<Prop> temporaryProps = new ArrayList<>();
    protected final Random rng = new Random();
    protected int spawnCol, spawnRow;
    /**
     * Painted scenery without collision (awnings, palm fronds, pond rims, a boss sprite...).
     * Litter never spawns here because it would look unreachable or be hidden.
     */
    protected final List<Rectangle2D> noLitterZones = new ArrayList<>();

    /** Litter must be this close to a spot the player can stand on (pickup range is 13). */
    private static final double REACH_MARGIN = 11;
    private static final int REACH_STEP = 2;
    /**
     * Extra room (px) the player's feet need on each side for a corridor to count as walkable
     * when placing litter, so litter never sits in pockets you can only enter pixel-perfectly.
     */
    private static final int CORRIDOR_PAD = 3;
    private boolean[][] reach;   // player top-left positions reachable from the spawn, on a 2px grid
    private List<Rectangle2D> solidBoxes;   // collision boxes, for the line-of-reach test

    private BufferedImage ground;
    private int totalGarbage, sortedCorrect, bossPollution, bonusMoney;
    private String pendingAnnouncement;

    // ----- what each level must provide -----
    public abstract String getName();
    protected abstract void buildTerrain();   // roads, grass...
    protected abstract void buildProps();     // houses, trees, cars...
    protected abstract void buildBins();
    protected abstract void buildGarbage();

    /** Builds everything. Call once after creating the level. */
    public final Level load() {
        for (TileType[] row : tiles) java.util.Arrays.fill(row, TileType.GRASS);
        props.clear();
        garbage.clear();
        bins.clear();
        npcs.clear();
        temporaryProps.clear();
        noLitterZones.clear();
        reach = null;
        solidBoxes = null;
        pendingAnnouncement = null;
        sortedCorrect = 0;
        bossPollution = 0;
        bonusMoney = 0;
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

    /** Which sprite a new piece of the given type uses. Level 5 overrides this to spawn city litter. */
    protected int randomVariant(GarbageType type) {
        return rng.nextInt(2);
    }

    protected boolean isTrashTileAllowed(double x, double y, TileType tile) {
        return true;
    }

    protected Garbage createGarbage(double x, double y) {
        GarbageType type = randomType();
        return new Garbage(type, randomVariant(type), x, y);
    }

    private boolean overlapsScenery(Rectangle2D r) {
        for (Prop p : props) {
            if (p.isGroundDecal() && p.collisionBox() == null) continue;   // paths, puddles, flower beds
            if (p.bounds().intersects(r)) return true;
        }
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
            if (!ok || !isTrashTileAllowed(c * TILE + 2, r * TILE + 2, t)) continue;
            double gx = c * TILE + 2 + rng.nextInt(TILE - 8), gy = r * TILE + 2 + rng.nextInt(TILE - 8);
            if (Math.hypot(gx - sx, gy - sy) < 30) continue;
            if (overlapsScenery(new Rectangle2D.Double(gx - 2, gy - 2, 12, 12))) continue;
            if (!isLitterSpotOk(gx, gy)) continue;
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
            if (p.speedFactor() < f && p.bounds().intersects(box)) f = p.speedFactor();
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

    public void addNpc(Entity npc) { npcs.add(npc); }
    public void removeNpc(Entity npc) { npcs.remove(npc); }

    /** Short-lived props (e.g. the Level 4 boss geysers) that remove themselves when done. */
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

    /** Applies temporary hazards to the player; true when the player was hit this frame. */
    public boolean applyTemporaryEffects(Player player) {
        boolean hit = false;
        for (Prop prop : temporaryProps) {
            if (prop instanceof WaterImpact impact && impact.tryHitPlayer(player)) hit = true;
        }
        return hit;
    }

    /** Drops up to {@code amount} new pieces within {@code radius} of a point. Returns how many landed. */
    public int addRandomLitterNear(double centerX, double centerY, int amount, double radius) {
        int before = garbage.size();
        for (int attempt = 0; attempt < Math.max(40, amount * 45) && garbage.size() - before < amount; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(rng.nextDouble()) * radius;
            double x = Math.max(4, Math.min(WORLD_W - Garbage.SIZE - 4, centerX + Math.cos(angle) * distance));
            double y = Math.max(4, Math.min(WORLD_H - Garbage.SIZE - 4, centerY + Math.sin(angle) * distance));
            int col = Math.min(COLS - 1, (int) (x / TILE));
            int row = Math.min(ROWS - 1, (int) (y / TILE));
            if (!isTrashTileAllowed(x, y, tiles[row][col])) continue;
            if (overlapsScenery(new Rectangle2D.Double(x - 2, y - 2, 12, 12)) || tooNearBin(x, y)) continue;
            if (!isLitterSpotOk(x, y)) continue;
            garbage.add(createGarbage(x, y));
        }
        return garbage.size() - before;
    }

    private boolean tooNearBin(double x, double y) {
        for (TrashBin bin : bins) if (Math.hypot(bin.centerX() - x, bin.centerY() - y) < 24) return true;
        return false;
    }

    /** A wrongly sorted item falls back onto the ground near the player. */
    public void dropGarbage(GarbageType t, double x, double y) {
        double[] spot = litterSpotNear(x - 4 + rng.nextInt(7) - 3, y + 2, 48);
        garbage.add(new Garbage(t, randomVariant(t), spot[0], spot[1]));
    }

    /** Same as above, but keeps the exact item (and its boss-wave flag). */
    public void dropGarbage(Garbage item, double x, double y) {
        double[] spot = litterSpotNear(x - 4 + rng.nextInt(7) - 3, y + 2, 48);
        garbage.add(item.copyAt(spot[0], spot[1]));
    }

    /**
     * A boss projectile lands: one new piece of litter appears centred on (cx, cy), or at the
     * nearest spot the player can reach. Always places it.
     */
    public Garbage dropLitterAt(double cx, double cy) {
        double[] spot = litterSpotNear(cx - Garbage.SIZE / 2.0, cy - Garbage.SIZE / 2.0, 60);
        Garbage item = createGarbage(spot[0], spot[1]);
        garbage.add(item);
        return item;
    }

    /** The Trash Monster drops new litter near its position. */
    public void dropGarbageFromBoss(double x, double y) {
        bossPollution += CLEANLINESS_LOSS_PER_BOSS_TRASH;
        double gx = Math.max(0, Math.min(WORLD_W - Garbage.SIZE,
                x - Garbage.SIZE / 2.0 + rng.nextInt(25) - 12));
        double gy = Math.max(0, Math.min(WORLD_H - Garbage.SIZE,
                y + 20 + rng.nextInt(13) - 6));
        double[] spot = litterSpotNear(gx, gy, 60);
        garbage.add(createGarbage(spot[0], spot[1]));
    }

    // ----- where litter may lie -----

    /**
     * True when a piece of litter with its top-left at (x, y) can lie here: inside the world, not
     * in or under any scenery (solid or painted), and within pickup range of a spot the player
     * can actually walk to from the spawn point.
     */
    public boolean isLitterSpotOk(double x, double y) {
        if (x < 2 || y < 2 || x > WORLD_W - Garbage.SIZE - 2 || y > WORLD_H - Garbage.SIZE - 2) return false;
        Rectangle2D box = new Rectangle2D.Double(x - 2, y - 2, Garbage.SIZE + 4, Garbage.SIZE + 4);
        if (isBlocked(box)) return false;
        for (Prop p : props) {
            if (p.isGroundDecal() && p.collisionBox() == null) continue;   // paths, puddles, flower beds
            if (p.bounds().intersects(box)) return false;                  // would be hidden or on top of it
        }
        for (Rectangle2D zone : noLitterZones) if (zone.intersects(box)) return false;
        return isCollectible(x + Garbage.SIZE / 2.0, y + Garbage.SIZE / 2.0);
    }

    /** The nearest good litter spot to (x, y) within {@code maxRadius}, or (x, y) itself if none. */
    protected double[] litterSpotNear(double x, double y, double maxRadius) {
        if (isLitterSpotOk(x, y)) return new double[] {x, y};
        for (double r = 3; r <= maxRadius; r += 3) {
            int samples = Math.max(8, (int) (r * 0.8));
            double start = rng.nextDouble() * Math.PI * 2;
            for (int i = 0; i < samples; i++) {
                double a = start + i * Math.PI * 2 / samples;
                double cx = x + Math.cos(a) * r, cy = y + Math.sin(a) * r;
                if (isLitterSpotOk(cx, cy)) return new double[] {cx, cy};
            }
        }
        return new double[] {x, y};
    }

    /**
     * True if the player can comfortably walk to a spot within pickup range of the world point
     * (cx, cy) and reach it without a fence, wall or other blocker in between.
     */
    private boolean isCollectible(double cx, double cy) {
        if (reach == null) buildReach();
        int x0 = (int) Math.floor((cx - REACH_MARGIN - Player.W / 2.0) / REACH_STEP);
        int x1 = (int) Math.ceil((cx + REACH_MARGIN - Player.W / 2.0) / REACH_STEP);
        int y0 = (int) Math.floor((cy - REACH_MARGIN - Player.H / 2.0) / REACH_STEP);
        int y1 = (int) Math.ceil((cy + REACH_MARGIN - Player.H / 2.0) / REACH_STEP);
        for (int gx = Math.max(0, x0); gx <= Math.min(reach.length - 1, x1); gx++) {
            for (int gy = Math.max(0, y0); gy <= Math.min(reach[0].length - 1, y1); gy++) {
                if (!reach[gx][gy]) continue;
                double pcx = gx * REACH_STEP + Player.W / 2.0, pcy = gy * REACH_STEP + Player.H / 2.0;
                if (Math.hypot(pcx - cx, pcy - cy) > REACH_MARGIN) continue;
                if (clearLine(pcx, pcy + 5, cx, cy)) return true;   // from the player's feet
            }
        }
        return false;
    }

    private boolean clearLine(double x0, double y0, double x1, double y1) {
        Line2D line = new Line2D.Double(x0, y0, x1, y1);
        for (Rectangle2D box : solidBoxes) if (line.intersects(box)) return false;
        return true;
    }

    /**
     * Flood fill of where the player can walk from the spawn, using the player's feet box with
     * {@link #CORRIDOR_PAD} spare room. Falls back to the exact feet box if the padded one cannot
     * leave the spawn area (so a tight start never leaves a level without litter).
     */
    private void buildReach() {
        solidBoxes = new ArrayList<>();
        for (Prop p : props) {
            Rectangle2D box = p.collisionBox();
            if (box != null) solidBoxes.add(box);
        }
        int cells = floodReach(CORRIDOR_PAD);
        if (cells < 400) floodReach(0);
    }

    private int floodReach(int pad) {
        int cw = WORLD_W / REACH_STEP + 1, ch = WORLD_H / REACH_STEP + 1;
        reach = new boolean[cw][ch];
        int count = 1;
        int sx = Math.max(0, Math.min(cw - 1, getSpawnX() / REACH_STEP));
        int sy = Math.max(0, Math.min(ch - 1, getSpawnY() / REACH_STEP));
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        reach[sx][sy] = true;
        queue.add(new int[] {sx, sy});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= cw || ny >= ch || reach[nx][ny]) continue;
                double px = nx * REACH_STEP, py = ny * REACH_STEP;
                Rectangle2D feet = new Rectangle2D.Double(px + 3 - pad, py + Player.H - 5 - (pad > 0 ? 1 : 0),
                        Player.W - 6 + pad * 2, 5 + (pad > 0 ? 2 : 0));
                if (isBlocked(feet)) continue;
                reach[nx][ny] = true;
                count++;
                queue.add(new int[] {nx, ny});
            }
        }
        return count;
    }

    public void registerCorrectSort() { sortedCorrect++; }
    public int getSortedCorrect() { return sortedCorrect; }

    /** Levels post short messages here; the game panel shows them as toasts. */
    protected void announce(String text) { pendingAnnouncement = text; }
    public String consumeAnnouncement() {
        String text = pendingAnnouncement;
        pendingAnnouncement = null;
        return text;
    }
    public void update(double dt, Player player) {}
    public boolean hasTrashMonster() { return false; }
    /** False for levels with their own boss (e.g. the Town Market's Waste King). */
    public boolean allowsTrashMonster() { return true; }
    public int timeLimit() { return 0; }
    /** Shown when {@link #timeLimit()} runs out. */
    public String timeUpMessage() { return "Time ran out!"; }

    /**
     * When above 0 the level is won by sorting this many pieces correctly (litter keeps
     * spawning), instead of by clearing every piece on the map.
     */
    public int sortTarget() { return 0; }

    // ----- developer tools -----
    /** Sets the correct-sort count directly (developer boss skip). */
    protected void devSetSortedCorrect(int n) { sortedCorrect = Math.max(0, n); }

    /**
     * Developer shortcut: fast-forwards a freshly loaded level to the moment its boss appears.
     * Returns the boss's name, or null when the level has none. Default: the roaming Trash
     * Monster, which shows up at 75% clean, so the level is sorted up to that point.
     */
    public String devSkipToBoss() {
        if (!allowsTrashMonster() || totalGarbage == 0) return null;
        int need = (int) Math.ceil(totalGarbage * 0.75);
        devSetSortedCorrect(need);
        while (garbage.size() > totalGarbage - need && !garbage.isEmpty()) garbage.remove(garbage.size() - 1);
        return "Trash Monster";
    }

    /** True for levels whose boss follows the Level 1 rules: its life is the trash left ({@link TrashBoss}). */
    public boolean hasTrashBoss() { return false; }
    /**
     * Creates that boss once the level is 75% clean (the game panel adds and runs it). Return null
     * to put it off for a frame (e.g. while the player stands on its spot).
     */
    public TrashBoss createTrashBoss(Player player) { return null; }

    /** Extra coins on top of the standard level-clear reward (e.g. defeating a boss). */
    public int clearBonus() { return 0; }

    /** Called after the player puts {@code item} in the wrong bin (the item is dropped back). */
    public void onWrongSort(Garbage item, Player player) {}

    /** Levels award one-off coins here (e.g. "area cleared"); the game panel pays them out. */
    protected void addBonusMoney(int amount) { bonusMoney += Math.max(0, amount); }
    public int consumeBonusMoney() {
        int amount = bonusMoney;
        bonusMoney = 0;
        return amount;
    }

    /** Screen-space extras drawn over the scaled world (crisp text such as shop signs). */
    public void drawOverlay(Graphics2D g, int screenW, int screenH) {}
    public boolean isCleared(Player player) { return cleanliness() >= 100; }
    public String introMessage() { return ""; }
    public String clearMessage() { return ""; }

    /** Cleanliness in percent: starts at 0, reaches 100 when everything is sorted. */
    public int cleanliness() {
        if (totalGarbage == 0) return Math.max(0, 100 - bossPollution);
        int progress = (int) Math.round((100 - BASE_CLEANLINESS) * (double) sortedCorrect / totalGarbage);
        return Math.max(0, Math.min(100, BASE_CLEANLINESS + progress - bossPollution));
    }

    public int garbageLeft()       { return garbage.size(); }
    public int getSpawnX()         { return spawnCol * TILE + (TILE - Player.W) / 2; }
    public int getSpawnY()         { return spawnRow * TILE + (TILE - Player.H) / 2; }
    public List<Garbage> getGarbage() { return garbage; }
    public List<TrashBin> getBins()   { return bins; }

    // ----- drawing -----
    public void draw(Graphics2D g, Player player) {
        if (ground != null) g.drawImage(ground, 0, 0, null);
        drawGround(g);

        for (Prop p : props) if (p.isGroundDecal()) p.draw(g);
        for (Garbage gb : garbage) gb.draw(g);

        List<Entity> sorted = new ArrayList<>();
        for (Prop p : props) if (!p.isGroundDecal()) sorted.add(p);
        sorted.addAll(bins);
        sorted.addAll(npcs);
        sorted.add(player);
        sorted.sort(Comparator.comparingInt(Entity::sortY));   // lower on screen = drawn in front
        // Anything drawn after (in front of) the player that overlaps them turns see-through,
        // so walking behind a tree crown, a roof, a lamp or a passing cart never hides the player.
        Rectangle2D body = new Rectangle2D.Double(player.getX() + 3, player.getY() + 2, Player.W - 6, Player.H - 3);
        boolean playerDrawn = false;
        for (Entity e : sorted) {
            if (e == player) {
                playerDrawn = true;
                e.draw(g);
            } else if (playerDrawn && e.bounds().intersects(body)) {
                Composite previous = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, SEE_THROUGH_ALPHA));
                e.draw(g);
                g.setComposite(previous);
            } else {
                e.draw(g);
            }
        }

        // dusty haze that fades as the city gets cleaner
        int alpha = (int) ((100 - cleanliness()) / 100.0 * 55);
        g.setColor(new Color(110, 100, 60, alpha));
        g.fillRect(0, 0, WORLD_W, WORLD_H);
    }

    protected void drawGround(Graphics2D g) {}
}
