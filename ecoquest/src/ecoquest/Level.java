package ecoquest;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Everything that makes one map different: layout, bins, boss rules and which trash base it uses.
 * To add a map: extend this class, fill the fields in the constructor, add a case in {@link #create}.
 * The garbage itself comes from data/trash/&lt;map key&gt;.csv (see TrashCatalog).
 */
public abstract class Level {
    public static final int W = 640, H = 360;

    /** Tuning knobs for one level. */
    public record Config(int target, int hearts, int timeLimitSec, int startItems, int pileItems,
                         int capacity, double spawnEvery, int maxGround) {
    }

    public record Label(String text, Rectangle area) {
    }

    /** A moving obstacle that travels back and forth along y between x1 and x2. */
    public record Lane(int x1, int x2, int y, double speed) {
    }

    public record Wanderer(int x, int y, Rectangle zone, int hat, int tunic) {
    }

    public final MapId map;
    public final String bossName;
    public final Config cfg;

    protected final List<Rectangle> solids = new ArrayList<>();
    protected final List<Rectangle> puddles = new ArrayList<>();
    protected final List<Label> labels = new ArrayList<>();
    protected final List<Lane> carts = new ArrayList<>();
    protected final List<Wanderer> shoppers = new ArrayList<>();
    protected final Map<WasteType, Rectangle> bins = new EnumMap<>(WasteType.class);
    protected Point start = new Point(W / 2, H / 2);
    protected Point pile = new Point(W / 2, H / 2);

    protected Level(MapId map, String bossName, Config cfg) {
        this.map = map;
        this.bossName = bossName;
        this.cfg = cfg;
    }

    public static Level create(MapId id) {
        return switch (id) {
            case MARKET -> new MarketLevel();
            default -> throw new UnsupportedOperationException(id.title + " is not built yet. Copy MarketLevel to start it.");
        };
    }

    // ---- art
    public abstract BufferedImage paintBackground();

    /** Things that change with cleanliness (flowers, bunting, sparkles). */
    public void paintDecor(Graphics2D g, double cleanliness, double time) {
    }

    /** Short intro text shown before the level starts. */
    public abstract String[] briefing();

    // ---- layout queries
    public List<Rectangle> solids() {
        return solids;
    }

    public List<Rectangle> puddles() {
        return puddles;
    }

    public List<Label> labels() {
        return labels;
    }

    public List<Lane> carts() {
        return carts;
    }

    public List<Wanderer> shoppers() {
        return shoppers;
    }

    public Map<WasteType, Rectangle> bins() {
        return bins;
    }

    public Point start() {
        return start;
    }

    public Point pile() {
        return pile;
    }

    /** True if the box is inside the map and touches no solid. */
    public boolean isFree(Rectangle2D box) {
        if (box.getMinX() < 6 || box.getMinY() < 6 || box.getMaxX() > W - 6 || box.getMaxY() > H - 2) return false;
        for (Rectangle s : solids) if (s.intersects(box)) return false;
        for (Rectangle b : bins.values()) if (b.intersects(box)) return false;
        return true;
    }

    /** Small helper so layouts can be written on a 320x180 grid and land on the 640x360 world. */
    protected static Rectangle r(int x, int y, int w, int h) {
        return new Rectangle(x * 2, y * 2, w * 2, h * 2);
    }
}
