package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static com.ecoquest.level1.Config.*;

/**
 * Level 3: Town Market, running on the shared engine like every other level.
 *
 * <p>The art and layout live in {@link MarketArt}, in world pixels on the shared 16 px tile grid,
 * using the same paving, trees, lamps and puddles as the other levels. The hand carts and the
 * Waste King ({@link WasteKingArt}) are drawn in code. Rules (the Level 1 boss rules, see
 * {@link TrashBoss}):
 * <ul>
 *   <li>Litter keeps spawning while you work, scattered over the whole market, until the Waste
 *       King shows up at 75% clean (cleanliness counts correct sorts against {@value #TARGET}).
 *       It rises in the plaza and dumps {@value #KING_DUMP} pieces around its heap.</li>
 *   <li>From then on its life is the trash left: every correct sort takes 1 HP, and it throws
 *       garbage bags (faster in phase 2 and 3) that cost 1 HP on a hit and burst into new trash.</li>
 *   <li>The market is saved once the Waste King is beaten and every piece is sorted. There is no
 *       time limit; you lose only when your health runs out.</li>
 *   <li>Every wrong bin drops one extra piece of trash (around the Waste King once it is out).</li>
 *   <li>Every 10 correct sorts clears an area for +25 coins; defeating the Waste King pays +150
 *       (+50 more when the market is at least 95% clean).</li>
 *   <li>Hand carts roll along three lanes: they stun you briefly and knock your selected item out of the bag.</li>
 *   <li>Puddles slow you down.</li>
 * </ul>
 */
public final class Level3Market extends Level {
    /** Layout space to world (both 512x288, so 1). */
    static final double S = (double) WORLD_W / MarketArt.W;
    /** Size of the cart and Waste King sprites (drawn for an 800 px wide map) in the world. */
    static final double SPRITE = 0.64;

    /** Correct sorts that count as a 100% clean market. */
    private static final int TARGET = 40;
    private static final int START_ITEMS = 34;
    /** Pieces the Waste King dumps around its heap when it rises. */
    private static final int KING_DUMP = 6;
    /** Waste King timings (seconds): rising out of the heap, a slam / throw, a hit flinch, the defeat, the fade. */
    private static final double KING_RISE = 1.5, KING_ANGRY = 1.1, KING_HURT = 0.55, KING_DEFEAT = 2.6, KING_FADE = 0.8;
    /** Seconds between the Waste King's rotten-produce barrages, per phase (index = phase - 1). */
    private static final double[] KING_THROW_EVERY = {10, 7.5, 5.5};
    private static final int MAX_GROUND = 40;
    private static final double SPAWN_EVERY = 5.0;
    private static final double CART_COOLDOWN = 1.8, CART_STUN = 0.5;

    private final MarketArt art = new MarketArt();
    private final BufferedImage background = scaleToWorld(art.paintBackground());
    private final List<Cart> carts = new ArrayList<>();
    private int mistakes;
    private double time, spawnClock, cartCooldown;
    /** Null until the market is 75% clean and the game panel summons it. */
    private WasteKing king;

    @Override public String getName() { return "Level 3: Town Market"; }

    @Override public String introMessage() {
        return "Town Market: sort the litter into the bins! Watch out for hand carts... "
                + "and for the Waste King once the market is 75% clean.";
    }

    @Override public String clearMessage() { return "The Waste King is defeated! Market saved!"; }
    @Override public boolean allowsTrashMonster() { return false; }
    @Override public boolean hasTrashBoss() { return true; }

    /**
     * The Waste King rises in the plaza and dumps a heap of trash. Waits (returns null) while the
     * player stands on its spot.
     */
    @Override public TrashBoss createTrashBoss(Player player) {
        if (king != null) return king;
        if (kingZone().intersects(playerFeet(player, player.getY()))) return null;
        StaticBlock body = block(kingBody());
        props.add(body);
        king = new WasteKing(body);
        for (int i = 0; i < KING_DUMP; i++) spawn(true, player.centerX(), player.centerY());
        return king;
    }

    /** Litter from boss bags and drops looks like market trash. */
    @Override protected Garbage createGarbage(double x, double y) {
        return new MarketGarbage(MarketTrash.pick(rng), x, y);
    }

    /** Waste King +150, and +50 more for a spotless market. */
    @Override public int clearBonus() { return 150 + (cleanliness() >= 95 ? 50 : 0); }

    @Override public int getSpawnX() { return (int) Math.round(art.start.x * S - Player.W / 2.0); }
    @Override public int getSpawnY() { return (int) Math.round(art.start.y * S - Player.H); }

    // ------------------------------------------------------------------ build

    @Override protected void buildTerrain() {
        spawnCol = getSpawnX() / TILE;
        spawnRow = getSpawnY() / TILE;
        mistakes = 0;
        time = spawnClock = cartCooldown = 0;
        king = null;
    }

    @Override protected void buildProps() {
        for (Rectangle r : art.solids) props.add(block(r));
        for (Rectangle r : art.scenery) noLitterZones.add(toWorld(r));
        // keep the Waste King's spot free of litter, so nothing is hidden or stuck when it rises
        noLitterZones.add(kingZone());
        for (Rectangle p : art.puddles) props.add(new Puddle(p.x, p.y, p.width, p.height));
        for (java.awt.Point t : art.trees) props.add(new Tree(t.x, t.y));
        for (java.awt.Point l : art.lamps) props.add(new LampPost(l.x, l.y));

        carts.clear();
        for (MarketArt.Lane lane : art.carts) {
            carts.add(new Cart(lane, lane.x1() + rng.nextInt(Math.max(1, lane.x2() - lane.x1()))));
        }
        for (Cart c : carts) addNpc(c);

        // market dressing: entrance arch, banderitas over the plaza, delivery cars on the street
        addNpc(new Arch());
        for (int row : MarketArt.BUNTING_ROWS) addNpc(new Bunting(row));
        props.add(new Car(40, TILE + 1, true, 0xE4E4E0, 0xFFFFFF));
        props.add(new Car(444, TILE + 1, true, 0x3E7D4A, 0x5A9E66));
    }

    /** One bin per tile on the plaza's south edge, in the BIO, REC, TRASH, HAZ order. */
    @Override protected void buildBins() {
        GarbageType[] order = {GarbageType.BIODEGRADABLE, GarbageType.RECYCLABLE,
                GarbageType.NON_RECYCLABLE, GarbageType.HAZARDOUS};
        for (int i = 0; i < order.length; i++) {
            java.awt.Point t = art.binTiles.get(i);
            bins.add(new TrashBin(order[i], t.x, t.y));
        }
    }

    @Override protected void buildGarbage() {
        double px = getSpawnX() + Player.W / 2.0, py = getSpawnY() + Player.H / 2.0;
        for (int i = 0; i < START_ITEMS; i++) spawn(false, px, py);
    }

    // ------------------------------------------------------------------ rules

    @Override public int cleanliness() {
        double c = BASE_CLEANLINESS + (100.0 - BASE_CLEANLINESS) * getSortedCorrect() / TARGET - 2.0 * mistakes;
        return (int) Math.max(0, Math.min(100, Math.round(c)));
    }

    @Override public void registerCorrectSort() {
        super.registerCorrectSort();
        int sorted = getSortedCorrect();
        if (sorted % 10 == 0 && sorted < TARGET) {
            addBonusMoney(25);
            announce("Area cleared! +₱25");
        }
    }

    /** A mistake drops one more piece; once the Waste King is out, it shakes and throws it. */
    @Override public void onWrongSort(Garbage item, Player player) {
        mistakes++;
        if (king != null) king.enrage();
        spawn(king != null, player.centerX(), player.centerY());
    }

    @Override public void update(double dt, Player player) {
        time += dt;
        cartCooldown = Math.max(0, cartCooldown - dt);
        for (Cart c : carts) c.update(dt, player);
        if (king == null) spawnTick(dt, player);   // once the Waste King is out, its life is the trash left
    }

    /** Keeps enough litter around to reach the target, plus a trickle up to MAX_GROUND pieces. */
    private void spawnTick(double dt, Player player) {
        spawnClock += dt;
        int need = TARGET - getSortedCorrect();
        int have = garbage.size() + player.getBag().size();
        double every = have < need ? 1.0 : SPAWN_EVERY;
        if (spawnClock < every) return;
        spawnClock = 0;
        if (have < need || garbage.size() < MAX_GROUND) spawn(false, player.centerX(), player.centerY());
    }

    // ------------------------------------------------------------------ spawning

    /** Candidates tried per scattered piece; the one farthest from other litter wins. */
    private static final int SCATTER_CANDIDATES = 12;

    /**
     * Drops one piece: near the Waste King's heap, or scattered over the whole market (below the
     * street) at the best of several random spots, so litter spreads out evenly instead of clumping.
     */
    private void spawn(boolean nearPile, double playerX, double playerY) {
        double bestX = 0, bestY = 0, bestGap = -1;
        int found = 0;
        for (int tries = 0; tries < 160 && found < (nearPile ? 1 : SCATTER_CANDIDATES); tries++) {
            double cx, cy; // centre of the new piece, world pixels
            if (nearPile) {
                double a = rng.nextDouble() * Math.PI * 2, rad = (38 + rng.nextDouble() * 26) * SPRITE;
                cx = art.pile.x + Math.cos(a) * rad * 1.5;
                cy = art.pile.y - 3 + Math.sin(a) * rad * 0.8;
            } else {
                cx = 8 + rng.nextDouble() * (WORLD_W - 16);
                cy = 2 * TILE + 6 + rng.nextDouble() * (WORLD_H - 2 * TILE - 14);
            }
            double x = cx - Garbage.SIZE / 2.0, y = cy - Garbage.SIZE / 2.0;
            if (!spawnOk(x, y, nearPile, playerX, playerY)) continue;
            found++;
            double gap = nearestLitter(x, y);
            if (gap > bestGap) {
                bestGap = gap;
                bestX = x;
                bestY = y;
            }
        }
        if (found > 0) garbage.add(new MarketGarbage(MarketTrash.pick(rng), bestX, bestY));
    }

    private double nearestLitter(double x, double y) {
        double best = Double.MAX_VALUE;
        for (Garbage o : garbage) best = Math.min(best, Math.hypot(o.getX() - x, o.getY() - y));
        return best;
    }

    private boolean spawnOk(double x, double y, boolean nearPile, double playerX, double playerY) {
        Rectangle2D box = new Rectangle2D.Double(x - 2, y - 2, Garbage.SIZE + 4, Garbage.SIZE + 4);
        if (!isLitterSpotOk(x, y)) return false;   // solid, painted scenery, or out of the player's reach
        for (TrashBin b : bins) {   // keep the bins easy to reach
            Rectangle2D near = new Rectangle2D.Double(b.getX() - 22, b.getY() - 16, 12 + 44, 14 + 36);
            if (near.intersects(box)) return false;
        }
        double gapX = nearPile ? 8 : 16, gapY = nearPile ? 6 : 13;
        for (Garbage o : garbage) {
            if (Math.abs(o.getX() - x) < gapX && Math.abs(o.getY() - y) < gapY) return false;
        }
        return Math.hypot(x + Garbage.SIZE / 2.0 - playerX, y + Garbage.SIZE / 2.0 - playerY) > 24;
    }

    // ------------------------------------------------------------------ geometry helpers

    /** Where the Waste King sprite is drawn (90x76 at full size). */
    private Rectangle2D.Double kingZone() {
        return new Rectangle2D.Double(art.pile.x - 30, art.pile.y - 46, 60, 55);
    }

    private Rectangle kingBody() {
        return new Rectangle(art.pile.x - 17, art.pile.y - 13, 33, 15);
    }

    private static Rectangle2D.Double toWorld(Rectangle r) {
        return new Rectangle2D.Double(r.x * S, r.y * S, r.width * S, r.height * S);
    }

    private static StaticBlock block(Rectangle r) {
        int left = (int) Math.floor(r.x * S), top = (int) Math.floor(r.y * S);
        int right = (int) Math.ceil((r.x + r.width) * S), bottom = (int) Math.ceil((r.y + r.height) * S);
        return new StaticBlock(left, top, right - left, bottom - top);
    }

    private static Rectangle2D playerFeet(Player p, double y) {
        return new Rectangle2D.Double(p.getX() + 3, y + Player.H - 5, Player.W - 6, 5);
    }

    private static BufferedImage scaleToWorld(BufferedImage src) {
        BufferedImage out = new BufferedImage(WORLD_W, WORLD_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, WORLD_W, WORLD_H, null);
        g.dispose();
        return out;
    }

    /** Graphics with its origin at a sprite's anchor (world px), scaled to {@link #SPRITE}. */
    private static Graphics2D spriteGraphics(Graphics2D g, double x, double y) {
        Graphics2D c = (Graphics2D) g.create();
        c.translate(x, y);
        c.scale(SPRITE, SPRITE);
        return c;
    }

    /** Graphics scaled so layout-space art code can draw straight into the world frame. */
    private static Graphics2D marketGraphics(Graphics2D g) {
        Graphics2D c = (Graphics2D) g.create();
        c.scale(S, S);
        return c;
    }

    // ------------------------------------------------------------------ drawing

    @Override protected void drawGround(Graphics2D g) {
        g.drawImage(background, 0, 0, null);
        Graphics2D c = marketGraphics(g);
        art.paintDecor(c, cleanliness(), time);
        c.dispose();
    }

    /** Stall signs ("Fish", "Pork"...) drawn with a crisp screen font over the scaled world. */
    @Override public void drawOverlay(Graphics2D g, int screenW, int screenH) {
        double ux = screenW / (double) MarketArt.W, uy = screenH / (double) MarketArt.H;
        double u = Math.min(ux, uy);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, Math.max(7, (int) Math.round(4.6 * u))));
        FontMetrics fm = g.getFontMetrics();
        for (MarketArt.Label label : art.labels) {
            Rectangle a = label.area();
            int tw = fm.stringWidth(label.text());
            int cx = (int) (a.getCenterX() * ux), cy = (int) ((a.y + a.height * 0.62) * uy);
            int ph = Math.max(11, (int) (6.5 * u)), pw = tw + Math.max(6, (int) (3 * u));
            g.setColor(new Color(0x4a2e1a));
            g.fillRoundRect(cx - pw / 2 - 2, cy - ph / 2 - 2, pw + 4, ph + 4, 8, 8);
            g.setColor(new Color(0xf3e3bd));
            g.fillRoundRect(cx - pw / 2, cy - ph / 2, pw, ph, 6, 6);
            g.setColor(new Color(0x3a2616));
            g.drawString(label.text(), cx - tw / 2, cy + fm.getAscent() / 2 - 1);
        }
    }

    // ------------------------------------------------------------------ actors

    /** The wooden "Town Market" arch over the lane; you walk under its sign. */
    private static final class Arch extends Entity {
        Arch() {
            super(MarketArt.ARCH_X0 - 4, 0, MarketArt.ARCH_X1 - MarketArt.ARCH_X0 + 8, MarketArt.ARCH_BASE);
        }

        @Override public void draw(Graphics2D g) { MarketArt.arch(g); }
    }

    /** A string of banderitas between two lamps, hanging above everything in the plaza. */
    private static final class Bunting extends Entity {
        private final int row;

        Bunting(int row) {
            super(MarketArt.BUNTING_X0, row, MarketArt.BUNTING_X1 - MarketArt.BUNTING_X0, 16);
            this.row = row;
        }

        @Override public int sortY() { return Integer.MAX_VALUE / 2; }
        @Override public void draw(Graphics2D g) {
            MarketArt.bunting(g, MarketArt.BUNTING_X0, MarketArt.BUNTING_X1, row + 2);
        }
    }

    /** A hand cart rolling back and forth along its lane. */
    private final class Cart extends Entity {
        private final MarketArt.Lane lane;
        private double cx;
        private int dir = 1;

        Cart(MarketArt.Lane lane, double startX) {
            super(0, 0, (int) (36 * SPRITE), (int) (14 * SPRITE));
            this.lane = lane;
            this.cx = startX;
            sync();
        }

        void update(double dt, Player player) {
            cx += dir * lane.speed() * dt;
            if (cx > lane.x2()) dir = -1;
            if (cx < lane.x1()) dir = 1;
            sync();
            if (cartCooldown > 0 || !bounds().intersects(playerFeet(player, player.getY()))) return;

            cartCooldown = CART_COOLDOWN;
            player.applyStun(CART_STUN);
            if (!player.bagEmpty()) {
                Garbage dropped = player.removeSelectedGarbage();
                dropGarbage(dropped, player.centerX() + dir * 16, player.getY() + Player.H - 6);
                announce("A hand cart bumped you! You dropped " + dropped.getItemName() + ".");
            } else {
                announce("A hand cart bumped you!");
            }
            // push the player off the lane, away from the cart
            double laneY = lane.y();
            double ny = player.getY() + (player.getY() + Player.H > laneY - 5 ? 16 : -16);
            if (!isBlocked(playerFeet(player, ny))) player.y = ny;
        }

        private void sync() {
            x = cx - 18 * SPRITE;
            y = lane.y() - 14 * SPRITE;
        }

        @Override public int sortY() { return lane.y(); }

        @Override public void draw(Graphics2D g) {
            Graphics2D c = spriteGraphics(g, cx, lane.y());
            MarketSprites.cart(c, 0, 0, dir);
            c.dispose();
        }
    }

    /**
     * The Waste King, following the Level 1 rules ({@link TrashBoss}): its life is the trash left in
     * the market. It bursts up out of its heap in the plaza and lands with a springy squash, then
     * breathes, blinks, follows you with its eyes, waves now and then, and its cape and crown sway.
     * Touching its heap hurts. Its abilities, all telegraphed:
     * <ul>
     *   <li><b>Rotten produce barrage</b>: flings 2 / 3 / 4 (by phase) rotten tomatoes, fish, mangoes
     *       and cabbages off its fork at marked spots. A hit does no damage and leaves no litter; it
     *       only slows you.</li>
     *   <li><b>Fork shockwave</b> (on every wrong sort, and every {@value #SHOCK_EVERY} s from phase 2):
     *       slams its fork and a shockwave ring spreads {@value #SHOCK_RADIUS} px. It does no damage
     *       but stuns you if it catches you.</li>
     *   <li><b>Royal decree: can soldiers</b> (phase 2+): raises its fork and summons crushed-can
     *       soldiers that march at you (1 HP on contact) and fall apart after a while.</li>
     *   <li><b>Royal warp + crown boomerang</b> (phase 3): sinks into its heap, rises again right
     *       next to you and hurls its crown, which curves out past you and back (1 HP on a hit); the
     *       king stays bald and furious until it returns, then warps back to its throne.</li>
     * </ul>
     * Every point of damage makes it flinch, flash and lose a chunk of rubbish as it springs down a
     * size (its size follows its HP). At 0 HP its crown flies off, it deflates into the heap,
     * seedlings sprout and it fades away.
     */
    private final class WasteKing extends Entity implements TrashBoss {
        static final double SHOCK_EVERY = 12, SHOCK_RADIUS = 92;
        private static final double SHOCK_TIME = 0.65, SHOCK_STUN = 1.0, PRODUCE_SLOW = 2.5;
        private static final double SUMMON_TIME = 0.9, SUMMON_AT = 0.55, SUMMON_EVERY = 14;
        /** Crown boomerang: wind-up (its path is shown as a dashed line), out-and-back flight time, cooldown. */
        private static final double CROWN_WIND = 0.9, CROWN_FLIGHT = 2.8, CROWN_EVERY = 9;
        /** The thrown crown is drawn this much bigger than the one on its head. */
        private static final double CROWN_SCALE = 1.45, CROWN_HIT = 13;
        /** Royal warp: seconds to sink into the heap, and to rise again somewhere else. */
        private static final double WARP_SINK = 0.45, WARP_RISE = 0.55;
        private final int[] produceKinds = {BossCore.TOMATO, BossCore.FISH, BossCore.MANGO, BossCore.CABBAGE};

        /** Its own clock (shadows the market's), so it can rise during the intro while the market is frozen. */
        private double time;
        private final double bornAt = 0;
        private final PixelCanvas canvas = new PixelCanvas(WasteKingArt.W, WasteKingArt.H);
        private final WasteKingArt painter = new WasteKingArt(canvas);
        private final WasteKingArt.Pose pose = new WasteKingArt.Pose();
        private final BossParticles fx = new BossParticles();
        private final BossCore core = new BossCore();
        private final PixelCanvas crownCanvas = new PixelCanvas(46, 46);
        private final List<CanSoldier> soldiers = new ArrayList<>();
        /** Displayed size, sprung toward the size its HP calls for, so each change bounces a little. */
        private double size, sizeVel;
        private double angryAt = -99, hurtAt = -99, defeatAt = -1, waveAt = -99, nextWave = 6;
        private double throwTimer = 3, shockTimer = 5, soldierTimer = 3, crownTimer = 3;
        private double lookX, lookY, steamClock;
        private boolean landed, slammed, throwing;
        private Player target;
        // fork shockwave (-1 = none)
        private double shockAge = -1, shockR;
        // royal decree
        private double summonAt = -99;
        private boolean summoned;
        // where the heap stands (the throne in the plaza, or next to the player during a warp)
        private double kx = art.pile.x, ky = art.pile.y;
        private final StaticBlock body;
        private boolean bodyPending;
        // royal warp: started at (-99 = none), where to, and whether it is the trip back
        private double warpAt = -99, warpX, warpY;
        private boolean warpBack, warpMoved, crownAfterWarp;
        // crown boomerang (-1 = on its head)
        private double crownThrowAt = -99, crownAge = -1, cbX, cbY, cbZ;
        private double cbSX, cbSY, cbTX, cbTY, cbCX, cbCY, cbC2X, cbC2Y;
        private boolean crownHit;
        // the crown knocked off on defeat
        private boolean crownFlying;
        private double crX, crY, crZ, crVX, crVZ, crRot, crSpin;

        private final int[] dust = {0xC9BFA6, 0xB0A68C, 0xDCD3BE};
        private final int[] rubbish = {0xE34B4B, 0x3B8FD9, 0xF2D33B, 0xF4F4F4, 0xC98C4A, 0x7CC46A, 0xA8743A, 0x8E44AD};
        private final int[] steam = {0xF2EEE8, 0xDCD6CE};

        WasteKing(StaticBlock body) {
            super(art.pile.x - 26 * SPRITE, art.pile.y - 20 * SPRITE, (int) (52 * SPRITE), (int) (24 * SPRITE));
            this.body = body;
            size = targetSize();
            double gx = baseX(), gy = baseY();
            fx.burst(BossParticles.PUFF, gx, gy, 2, 12, 40, 6, 0.9, dust);
            fx.burst(BossParticles.BIT, gx, gy, 4, 14, 55, 80, 1.4, rubbish);
        }

        private double baseX() { return kx; }
        private double baseY() { return ky + 6 * SPRITE; }
        private boolean warping() { return time - warpAt < WARP_SINK + WARP_RISE; }
        /** 1 = standing, 0 = sunk into the ground (during a warp). */
        private double warpEmerge() {
            double t = time - warpAt;
            if (t < 0 || t >= WARP_SINK + WARP_RISE) return 1;
            return t < WARP_SINK ? 1 - PixelCanvas.easeIn(t / WARP_SINK) : (t - WARP_SINK) / WARP_RISE;
        }
        private double age() { return time - bornAt; }
        /** ~0.88 of the art size at full HP, down to ~0.59 near the end. */
        private double targetSize() { return 0.59 + 0.29 * core.hpShare(); }
        private double deathTime() { return defeatAt < 0 ? -1 : time - defeatAt; }
        private boolean crownOut() { return crownAge >= 0; }
        /** True while an ability animation is playing, so they never overlap. */
        private boolean busy() {
            return time - angryAt < KING_ANGRY || time - summonAt < SUMMON_TIME || time - crownThrowAt < CROWN_WIND + 0.3
                    || warping() || crownOut() || crownAfterWarp;
        }
        private double headX() { return baseX() + pose.lean * 3 * size; }

        // ------------------------------------------------------------ TrashBoss
        @Override public Entity entity() { return this; }
        @Override public String bossName() { return "WASTE KING"; }
        @Override public String introMessage() {
            return "The WASTE KING rose from its heap and dumped more trash! Its life is the trash left - sort every piece to beat it!";
        }
        @Override public String phaseMessage(int phase) {
            return phase == 2 ? "PHASE 2: The Waste King summons can soldiers and slams the ground - keep your distance!"
                    : "PHASE 3: ENRAGED! The Waste King warps next to you and hurls its crown - finish it!";
        }
        @Override public String defeatMessage() { return "The market is clean - the Waste King is defeated!"; }
        @Override public Intro intro() {
            return new Intro("THE MARKET'S RULER HAS AWOKEN", "THE TRASH HEAP IS SHAKING",
                    "All hail... the king of garbage?", "THE WASTE KING",
                    "BOW BEFORE HIS ROTTEN ROYAL DECREE!", 0xF4CC4C);
        }

        @Override public void syncHp(int trashLeft) {
            int before = core.hp();
            boolean risen = age() >= KING_RISE;
            if (core.syncHp(trashLeft, risen)) defeat();
            else if (core.hp() < before && risen) hurt();
        }
        @Override public int getHp() { return core.hp(); }
        @Override public int getPeakHp() { return core.peakHp(); }
        @Override public int getPhase() { return core.phase(); }
        @Override public boolean isEmerging() { return age() < KING_RISE; }
        @Override public boolean isDying() { return defeatAt >= 0; }
        @Override public boolean isGone() { return defeatAt >= 0 && deathTime() >= KING_DEFEAT + KING_FADE; }
        @Override public boolean isDangerous() { return !isEmerging() && defeatAt < 0 && !warping(); }
        @Override public double getHitFlash() { return core.hitFlash(); }
        @Override public double getHealFlash() { return core.healFlash(); }
        @Override public List<Strike> consumeStrikes() { return core.consumeStrikes(); }
        @Override public boolean consumeToss() { return core.consumeToss(); }
        @Override public int consumeHpChange() { return core.consumeHpChange(); }
        @Override public int consumePhaseChange() { return core.consumePhaseChange(); }
        @Override public boolean consumeDefeated() { return core.consumeDefeated(); }

        /** Touching the heap hurts (the heap itself is solid, so this reaches just past its edge). */
        @Override public boolean isTouching(Player p) {
            if (!isDangerous()) return false;
            double dx = (p.centerX() - baseX()) / (26 * size + 6), dy = (p.getY() + Player.H - 2 - (baseY() - 6)) / (12 * size + 6);
            return dx * dx + dy * dy <= 1;
        }

        @Override public double[] knockBack(Player p) {
            double dx = p.centerX() - baseX(), dy = p.centerY() - (baseY() - 8);
            double len = Math.max(0.001, Math.hypot(dx, dy));
            return new double[] { dx / len * 16, dy / len * 16 };
        }

        /** Can soldiers and the flying crown hurt on contact. */
        @Override public Contact hazardContact(Player p) {
            double px = p.centerX(), py = p.getY() + Player.H - 3;
            for (CanSoldier s : soldiers)
                if (s.dangerous() && Math.hypot(s.fx - px, s.fy - py) < 8)
                    return new Contact(s.fx, s.fy, "A can soldier poked you with its spear! -1 HP");
            if (crownOut() && !crownHit && Math.hypot(cbX - px, cbY - py) < CROWN_HIT) {
                crownHit = true;
                return new Contact(cbX, cbY, "The Waste King's crown smacked you! -1 HP");
            }
            return null;
        }

        // ------------------------------------------------------------ reactions
        private void hurt() {
            hurtAt = time;
            double gx = baseX(), gy = baseY();
            java.util.Random r = fx.rng();
            for (int i = 0; i < 3; i++) {      // a chunk of rubbish pops off the heap
                double a = r.nextDouble() * Math.PI * 2;
                fx.add(BossParticles.BIT, gx + Math.cos(a) * 14 * size, gy - 4, 10 * size, Math.cos(a) * 45, Math.sin(a) * 20,
                        70 + r.nextDouble() * 40, 1.3, rubbish[r.nextInt(rubbish.length)], 2);
            }
            for (int i = 0; i < 6; i++)
                fx.add(BossParticles.SPARK, gx + r.nextGaussian() * 12 * size, gy - 30 * size, r.nextDouble() * 10,
                        r.nextGaussian() * 6, 0, 10, 0.5, 0xFFFFFF, 1);
        }

        /** A wrong sort: it rages and slams its fork, sending out a shockwave. */
        void enrage() {
            if (!isDangerous() || time - angryAt < KING_ANGRY || crownAfterWarp) return;
            angryAt = time;
            slammed = false;
            throwing = false;
        }

        private void defeat() {
            if (defeatAt >= 0) return;
            defeatAt = time;
            throwing = false;
            shockAge = -1;
            warpAt = -99;                           // falls wherever it stands
            crownAfterWarp = false;
            for (CanSoldier s : soldiers) s.poof();
        }

        // ------------------------------------------------------------ update
        @Override public void update(double dt, Player player, Level level) {
            time += dt;
            target = player;
            fx.update(dt);
            core.update(dt, player);
            updateSoldiers(dt, level);
            updateBody(player);
            double a = age();
            // springy size
            double k = 140, damp = 13;
            sizeVel += ((targetSize() - size) * k - sizeVel * damp) * dt;
            size += sizeVel * dt;
            // eyes follow the player
            double tlx = Math.max(-1, Math.min(1, (player.centerX() - baseX()) / 60.0));
            double tly = Math.max(-1, Math.min(1, (player.centerY() - (baseY() - 30 * size)) / 45.0));
            lookX += (tlx - lookX) * Math.min(1, dt * 6);
            lookY += (tly - lookY) * Math.min(1, dt * 6);

            if (!landed && a >= KING_RISE) {        // thud
                landed = true;
                fx.burst(BossParticles.PUFF, baseX(), baseY(), 1, 10, 36, 4, 0.7, dust);
                if (core.hpKnown() && core.hp() == 0) syncHp(0);   // the market was already clean
            }
            if (defeatAt >= 0) {
                updateDefeat(dt);
                return;
            }
            updateShockwave(dt);
            updateWarp();
            updateCrown(dt);
            if (isEmerging()) return;

            int phase = core.phase();
            throwTimer -= dt;
            if (phase >= 2) { shockTimer -= dt; soldierTimer -= dt; }
            if (phase >= 3 && !crownOut()) crownTimer -= dt;
            if (!busy()) {                          // pick the next ability
                if (throwTimer <= 0) {
                    throwTimer = KING_THROW_EVERY[phase - 1];
                    angryAt = time;
                    slammed = false;
                    throwing = true;
                } else if (phase >= 3 && crownTimer <= 0 && !crownOut()) {
                    crownTimer = CROWN_EVERY;
                    double[] spot = warpSpotNear(target, level);
                    if (spot != null) startWarp(spot[0], spot[1], false);   // rises next to you, then throws
                    else startCrownThrow();
                } else if (phase >= 2 && soldierTimer <= 0) {
                    soldierTimer = SUMMON_EVERY;
                    summonAt = time;
                    summoned = false;
                } else if (phase >= 2 && shockTimer <= 0) {
                    shockTimer = SHOCK_EVERY;
                    enrage();
                }
            }

            double gx = baseX(), gy = baseY();
            double ang = time - angryAt;
            if (ang >= 0 && ang < KING_ANGRY) {
                steamClock += dt;
                if (steamClock > 0.07 && ang < 0.9 && !throwing) {   // steam out of the crown
                    steamClock = 0;
                    java.util.Random r = fx.rng();
                    fx.add(BossParticles.PUFF, gx + r.nextGaussian() * 6 * size, gy, 52 * size, r.nextGaussian() * 8, 0,
                            22 + r.nextDouble() * 10, 0.7, steam[r.nextInt(steam.length)], 2);
                }
                if (!slammed && ang >= KING_ANGRY * 0.45) {
                    slammed = true;
                    if (throwing) {                 // flings rotten produce off its fork
                        throwProduce(phase);
                        throwing = false;
                    } else {                        // the fork hits the ground: shockwave
                        double tipX = gx + 34 * size;
                        fx.burst(BossParticles.PUFF, tipX, gy - 2, 1, 8, 30, 4, 0.6, dust);
                        fx.burst(BossParticles.BIT, tipX, gy - 2, 2, 7, 50, 70, 1.2, rubbish);
                        shockAge = 0;
                        shockR = 0;
                    }
                }
            }
            if (!summoned && time - summonAt >= SUMMON_AT && time - summonAt < SUMMON_TIME) {
                summoned = true;
                summonSoldiers(phase == 3 ? 3 : 2, level);
            }
            if (a > nextWave && !busy()) {          // a royal wave now and then
                waveAt = time;
                nextWave = a + 7 + fx.rng().nextDouble() * 5;
            }
        }

        private void throwProduce(int phase) {
            int count = phase + 1;                  // 2, 3, 4
            java.util.Random r = fx.rng();
            double sx = baseX() + 20 * size, sy = baseY() - 58 * size;
            for (int i = 0; i < count; i++) {
                double ox = i == 0 ? 0 : r.nextGaussian() * 24, oy = i == 0 ? 0 : r.nextGaussian() * 14;
                double[] t = core.leadTarget(target, 0.55, ox, oy);
                Strike splat = new Strike(0, 0, 13, false, PRODUCE_SLOW, 0, 0, "Splat! Rotten produce - you're slowed!");
                core.throwAt(sx, sy, t[0], t[1], 1.0 + i * 0.14, 34 + i * 6, produceKinds[r.nextInt(produceKinds.length)], splat);
            }
        }

        /** The shockwave ring spreads; it stuns the player (no damage) when its front passes their feet. */
        private void updateShockwave(double dt) {
            if (shockAge < 0) return;
            double prev = shockR;
            shockAge += dt;
            shockR = SHOCK_RADIUS * PixelCanvas.easeOut(shockAge / SHOCK_TIME);
            double px = target.centerX(), py = target.getY() + Player.H - 3;
            double d = Math.hypot(px - baseX(), (py - (baseY() - 2)) * 2);   // the ring is a flat ellipse
            if (d > prev && d <= shockR && d > 14 * size)
                core.strike(new Strike(px, py, 4, false, 0, SHOCK_STUN, 0, "The Fork Shockwave stunned you!"));
            java.util.Random r = fx.rng();
            if (r.nextDouble() < dt * 40) {         // dust kicked up along the ring
                double a = r.nextDouble() * Math.PI * 2;
                fx.add(BossParticles.PUFF, baseX() + Math.cos(a) * shockR, baseY() - 2 + Math.sin(a) * shockR * 0.5, 0,
                        Math.cos(a) * 10, Math.sin(a) * 5, 6, 0.5, dust[r.nextInt(dust.length)], 2);
            }
            if (shockAge >= SHOCK_TIME) shockAge = -1;
        }

        private void summonSoldiers(int count, Level level) {
            java.util.Random r = fx.rng();
            for (int i = 0; i < count; i++) {
                double a = Math.PI * (0.15 + 0.7 * (count == 1 ? 0.5 : i / (double) (count - 1)));   // across the front of the heap
                double sx = baseX() + Math.cos(a) * 30 * size, sy = baseY() + 4 + Math.sin(a) * 8;
                CanSoldier s = new CanSoldier(sx, sy, i + r.nextInt(3), core.phase() == 3 ? 46 : 40);
                soldiers.add(s);
                level.addNpc(s);
                fx.burst(BossParticles.PUFF, sx, sy, 2, 5, 20, 6, 0.5, dust);
                fx.burst(BossParticles.BIT, sx, sy, 4, 4, 30, 40, 0.9, rubbish);
            }
        }

        private void updateSoldiers(double dt, Level level) {
            for (java.util.Iterator<CanSoldier> it = soldiers.iterator(); it.hasNext(); ) {
                CanSoldier s = it.next();
                s.update(dt, target, level);
                if (s.collapsedNow())               // falls apart (no litter: it is the king's, not the market's)
                    fx.burst(BossParticles.BIT, s.fx, s.fy, 4, 4, 25, 30, 0.8, rubbish);
                if (s.finished()) {
                    level.removeNpc(s);
                    it.remove();
                }
            }
        }

        /** Crown boomerang: wind-up, then it curves out past the player and back to the king's head. */
        private void updateCrown(double dt) {
            double since = time - crownThrowAt;
            if (!crownOut() && since >= CROWN_WIND && since - dt < CROWN_WIND) {   // let go, along the path shown
                crownAge = 0;
                crownHit = false;
            }
            if (!crownOut()) return;
            crownAge += dt;
            double u = Math.min(1, crownAge / CROWN_FLIGHT);
            double[] p = u < 0.5 ? bezier(cbSX, cbSY, cbCX, cbCY, cbTX, cbTY, PixelCanvas.easeOut(u * 2))
                    : bezier(cbTX, cbTY, cbC2X, cbC2Y, headX(), baseY() + 6, PixelCanvas.easeIn((u - 0.5) * 2));
            cbX = p[0];
            cbY = p[1];
            cbZ = 10 + 46 * size * Math.abs(1 - 2 * u) * Math.abs(1 - 2 * u);    // low near the player, high at the head
            if (fx.rng().nextDouble() < dt * 25)
                fx.add(BossParticles.SPARK, cbX, cbY, cbZ, 0, 0, 0, 0.35, 0xFFE070, 1);
            if (u >= 1) {                           // back on its head
                crownAge = -1;
                if (crownAfterWarp) {
                    crownAfterWarp = false;
                    startWarp(art.pile.x, art.pile.y, true);   // home to the throne
                }
            }
        }

        // ---- royal warp
        private void startWarp(double tx, double ty, boolean back) {
            warpAt = time;
            warpX = tx;
            warpY = ty;
            warpBack = back;
            warpMoved = false;
            fx.burst(BossParticles.PUFF, baseX(), baseY(), 2, 10, 30, 6, 0.7, dust);
            fx.burst(BossParticles.BIT, baseX(), baseY(), 4, 8, 40, 50, 1.0, rubbish);
        }

        /** Sinks, moves the heap (and its solid body) while underground, rises; then throws the crown. */
        private void updateWarp() {
            double t = time - warpAt;
            if (t < 0 || warpAt < -90) return;
            if (!warpMoved && t >= WARP_SINK) {
                warpMoved = true;
                kx = warpX;
                ky = warpY;
                x = kx - 26 * SPRITE;
                y = ky - 20 * SPRITE;
                body.x = -1000;                     // solid again once it has risen and you are clear
                bodyPending = true;
                fx.burst(BossParticles.PUFF, baseX(), baseY(), 2, 12, 36, 6, 0.8, dust);
                fx.burst(BossParticles.BIT, baseX(), baseY(), 4, 10, 50, 70, 1.2, rubbish);
            }
            if (warpMoved && t >= WARP_SINK + WARP_RISE) {
                warpAt = -99;
                if (!warpBack) {
                    crownAfterWarp = true;
                    startCrownThrow();
                }
            }
        }

        /** Re-enables the heap's solid body once it stands and the player is not inside it. */
        private void updateBody(Player p) {
            if (!bodyPending || warping()) return;
            Rectangle2D.Double foot = new Rectangle2D.Double(kx - 17, ky - 13, 33, 15);
            if (foot.intersects(playerFeet(p, p.getY()))) return;
            body.x = (int) Math.floor(kx - 17);
            body.y = (int) Math.floor(ky - 13);
            bodyPending = false;
        }

        /** A clear spot 46-64 px from the player for the heap to rise on, or null. */
        private double[] warpSpotNear(Player p, Level level) {
            java.util.Random r = fx.rng();
            double px = p.centerX(), py = p.getY() + Player.H - 3;
            double ox = body.x, oy = body.y;
            body.x = -1000;                         // its own body must not block the search
            try {
                for (int i = 0; i < 32; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = 46 + r.nextDouble() * 18;
                    double cx = px + Math.cos(a) * d, cy = py + Math.sin(a) * d * 0.7;
                    if (cx < 34 || cx > WORLD_W - 34 || cy < 70 || cy > WORLD_H - 14) continue;
                    Rectangle2D.Double foot = new Rectangle2D.Double(cx - 21, cy - 17, 41, 23);
                    if (level.isBlocked(foot) || foot.intersects(playerFeet(p, p.getY()))) continue;
                    boolean clear = true;
                    for (Garbage g : garbage) if (foot.intersects(g.bounds())) { clear = false; break; }
                    for (TrashBin b : bins) if (Math.hypot(b.centerX() - cx, b.centerY() - cy) < 36) { clear = false; break; }
                    if (clear) return new double[] { cx, cy };
                }
                return null;
            } finally {
                body.x = ox;
                body.y = oy;
            }
        }

        /**
         * Starts the crown wind-up and plans its path right away, aimed at where the player stands
         * now, so the dashed line shows exactly where it will fly while there is time to move.
         */
        private void startCrownThrow() {
            crownThrowAt = time;
            cbSX = headX();
            cbSY = baseY() + 6;
            double px = target.centerX(), py = target.getY() + Player.H - 3;
            double dx = px - cbSX, dy = py - cbSY, d = Math.max(1, Math.hypot(dx, dy));
            cbTX = Math.max(8, Math.min(WORLD_W - 8, px + dx / d * 34));
            cbTY = Math.max(8, Math.min(WORLD_H - 8, py + dy / d * 34));
            // the way out passes right through where the player stood
            cbCX = 2 * px - (cbSX + cbTX) / 2;
            cbCY = 2 * py - (cbSY + cbTY) / 2;
            double nx = -dy / d, ny = dx / d, bend = 50;
            cbC2X = (cbSX + cbTX) / 2 - nx * bend;  // and it swings back on a wide curve
            cbC2Y = (cbSY + cbTY) / 2 - ny * bend;
        }

        /** The crown's planned path as a dashed line: out in red, the way back fainter. */
        private void drawCrownPath(Graphics2D g) {
            double since = time - crownThrowAt;
            boolean winding = !crownOut() && since >= 0 && since < CROWN_WIND;
            if (!winding && !crownOut()) return;
            double u = crownOut() ? Math.min(1, crownAge / CROWN_FLIGHT) : 0;
            double fade = crownOut() ? 0.55 : Math.min(1, since / 0.2);
            double pulse = 0.65 + 0.35 * Math.sin(time * 20);
            Graphics2D gg = (Graphics2D) g.create();
            gg.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            gg.setStroke(new java.awt.BasicStroke(1.4f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND,
                    1f, new float[] { 4f, 3f }, (float) (time * 12)));
            int n = 24;
            if (u < 0.5) {                          // the way out (only the part still ahead of the crown)
                java.awt.geom.Path2D.Double out = new java.awt.geom.Path2D.Double();
                double from = u * 2;
                for (int i = 0; i <= n; i++) {
                    double t = from + (1 - from) * i / (double) n;
                    double[] q = bezier(cbSX, cbSY, cbCX, cbCY, cbTX, cbTY, t);
                    if (i == 0) out.moveTo(q[0], q[1]); else out.lineTo(q[0], q[1]);
                }
                gg.setColor(new Color(255, 70, 50, (int) (220 * fade * pulse)));
                gg.draw(out);
            }
            java.awt.geom.Path2D.Double back = new java.awt.geom.Path2D.Double();
            double from = u < 0.5 ? 0 : (u - 0.5) * 2;
            for (int i = 0; i <= n; i++) {
                double t = from + (1 - from) * i / (double) n;
                double[] q = bezier(cbTX, cbTY, cbC2X, cbC2Y, headX(), baseY() + 6, t);
                if (i == 0) back.moveTo(q[0], q[1]); else back.lineTo(q[0], q[1]);
            }
            gg.setColor(new Color(255, 190, 80, (int) (140 * fade)));
            gg.draw(back);
            if (winding) {                          // marks the far end of the throw
                gg.setStroke(new java.awt.BasicStroke(1.2f));
                gg.setColor(new Color(255, 70, 50, (int) (200 * fade * pulse)));
                gg.drawOval((int) Math.round(cbTX - 6), (int) Math.round(cbTY - 3), 12, 6);
            }
            gg.dispose();
        }

        private double[] bezier(double x0, double y0, double cx, double cy, double x1, double y1, double t) {
            double m = 1 - t;
            return new double[] { m * m * x0 + 2 * m * t * cx + t * t * x1, m * m * y0 + 2 * m * t * cy + t * t * y1 };
        }

        private void updateDefeat(double dt) {
            double d = deathTime() / KING_DEFEAT;
            double gx = baseX(), gy = baseY();
            java.util.Random r = fx.rng();
            if (!crownFlying && d >= 0.14) {
                crownFlying = true;
                boolean thrown = crownOut();        // a crown in mid-flight just drops from where it is
                crX = thrown ? cbX : gx;
                crY = thrown ? cbY : gy;
                crZ = thrown ? cbZ : 56 * size;
                crownAge = -1;
                crVX = (r.nextBoolean() ? 1 : -1) * (30 + r.nextDouble() * 15);
                crVZ = 95;
                crSpin = Math.signum(crVX) * 9;
            }
            if (crownFlying) {
                crVZ -= 230 * dt;
                crX += crVX * dt;
                crZ += crVZ * dt;
                crRot += crSpin * dt;
                if (crZ <= 0) {
                    crZ = 0;
                    if (crVZ < -50) { crVZ = -crVZ * 0.35; crVX *= 0.5; crSpin *= 0.4; }
                    else { crVZ = 0; crVX = 0; crSpin = 0; crRot = Math.round(crRot / Math.PI) * Math.PI; }
                }
            }
            if (d > 0.2 && d < 0.7 && r.nextDouble() < dt * 14)   // air hissing out of the bag
                fx.add(BossParticles.PUFF, gx + r.nextGaussian() * 10 * size, gy - 4, 26 * size * (1 - d), r.nextGaussian() * 18, 0,
                        8, 0.7, steam[r.nextInt(steam.length)], 2);
            if (d >= 0.7 && d - dt / KING_DEFEAT < 0.7)
                for (int i = 0; i < 16; i++)
                    fx.add(BossParticles.SPARK, gx + r.nextGaussian() * 18 * size, gy - 4, 2 + r.nextDouble() * 18,
                            r.nextGaussian() * 4, 0, 6 + r.nextDouble() * 10, 0.8 + r.nextDouble() * 0.8,
                            r.nextBoolean() ? 0xFFFFFF : 0xFFF2A0, 1);
        }

        // ------------------------------------------------------------ drawing
        private void buildPose() {
            WasteKingArt.Pose p = pose;
            double t = time, a = age();
            double br = Math.sin(t * 2 * Math.PI / 2.8);
            p.t = t;
            p.size = Math.max(0.2, size);
            p.emerge = Math.min(PixelCanvas.clamp01(a / KING_RISE), warpEmerge());
            p.breathe = br;
            p.squash = 0;
            p.lean = Math.sin(t * 0.9) * 0.15;
            p.armL1 = 2.05 + br * 0.04;
            p.armL2 = 2.6 + br * 0.05;
            p.armR1 = 1.15 + Math.sin(t * 1.3) * 0.05;
            p.armR2 = -0.35 + Math.sin(t * 1.3 - 0.3) * 0.05;
            p.forkAng = 0.12 + Math.sin(t * 1.3 - 0.5) * 0.06;
            p.jaw = 0.12 + 0.05 * Math.sin(t * 2 * Math.PI / 2.8 - 0.6);
            double bl = t % 4.1;
            p.lid = bl < 0.08 ? bl / 0.08 : bl < 0.17 ? 1 - (bl - 0.08) / 0.09 : 0;
            p.eyes = 0;
            p.brow = 0.25;
            p.lookX = lookX;
            p.lookY = lookY;
            p.crownLift = Math.max(0, Math.sin(t * 2 * Math.PI / 2.8 - 1.0)) * 0.7;
            p.crownTilt = Math.sin(t * 1.1) * 0.05;
            p.crown = p.fork = p.flies = p.stink = true;
            p.flash = p.rage = p.deflate = p.sprout = 0;
            p.glow = 0.6;
            if (core.phase() == 3 && isDangerous()) {     // enraged: red-eyed, simmering
                p.eyes = 2;
                p.brow = 0.8;
                p.rage = 0.25 + 0.1 * Math.sin(t * 6);
                p.glow = 1;
            }

            if (a < KING_RISE || warping()) {         // rising (or warping): roaring, arms up
                p.eyes = 2;
                p.brow = 1;
                p.jaw = 0.85;
                p.armL1 = -2.3;
                p.armL2 = -1.9 + Math.sin(t * 26) * 0.2;
                p.armR1 = -1.0;
                p.armR2 = -1.5;
                p.forkAng = 0;
                p.glow = 1;
            } else if (a < KING_RISE + 0.7) {         // springy landing, crown pops up
                double r = a - KING_RISE, k = PixelCanvas.ease(r / 0.45);
                p.squash = Math.sin(r * Math.PI / 0.22) * 1.8 * Math.exp(-r * 5.5);
                p.crownLift += 7 * Math.sin(Math.min(1, r / 0.45) * Math.PI);
                p.crownTilt += 0.25 * Math.sin(r * 14) * Math.exp(-r * 4);
                p.armL1 = PixelCanvas.lerp(-2.3, p.armL1, k);
                p.armL2 = PixelCanvas.lerp(-1.9, p.armL2, k);
                p.armR1 = PixelCanvas.lerp(-1.0, p.armR1, k);
                p.armR2 = PixelCanvas.lerp(-1.5, p.armR2, k);
                p.forkAng = PixelCanvas.lerp(0, p.forkAng, k);
                p.jaw = PixelCanvas.lerp(0.85, p.jaw, k);
            }

            double w = (t - waveAt) / 1.6;                // royal wave
            if (w >= 0 && w < 1) {
                double k = PixelCanvas.ease(w / 0.2) * (1 - PixelCanvas.ease((w - 0.8) / 0.2));
                p.armL1 = PixelCanvas.lerp(p.armL1, -2.25, k);
                p.armL2 = PixelCanvas.lerp(p.armL2, -1.75 + Math.sin(t * 9) * 0.45, k);
                p.jaw = PixelCanvas.lerp(p.jaw, 0.35, k);
                p.lean -= 0.2 * k;
            }

            double sm = (t - summonAt) / SUMMON_TIME;     // royal decree: fork raised high, pointing at you
            if (sm >= 0 && sm < 1) {
                double k = PixelCanvas.ease(sm / 0.25) * (1 - PixelCanvas.ease((sm - 0.75) / 0.25));
                p.armR1 = PixelCanvas.lerp(p.armR1, -1.3, k);
                p.armR2 = PixelCanvas.lerp(p.armR2, -1.65, k);
                p.forkAng = PixelCanvas.lerp(p.forkAng, Math.sin(t * 18) * 0.12, k);
                p.armL1 = PixelCanvas.lerp(p.armL1, -0.9, k);
                p.armL2 = PixelCanvas.lerp(p.armL2, -0.5, k);
                p.jaw = Math.max(p.jaw, 0.7 * k);
                p.brow = Math.max(p.brow, 0.7 * k);
                p.crownLift += 2 * k;
                p.eyes = 2;
                p.glow = 1;
            }

            double cw = (t - crownThrowAt);               // crown boomerang: grabs it and hurls it
            if (cw >= 0 && cw < CROWN_WIND + 0.3) {
                double up = PixelCanvas.ease(cw / CROWN_WIND), fling = PixelCanvas.ease((cw - CROWN_WIND) / 0.15);
                double back = PixelCanvas.ease((cw - CROWN_WIND - 0.15) / 0.15);
                double reach = up * (1 - fling), throwArm = fling * (1 - back);
                p.armL1 = PixelCanvas.lerp(PixelCanvas.lerp(p.armL1, -1.95, reach), -0.7, throwArm);
                p.armL2 = PixelCanvas.lerp(PixelCanvas.lerp(p.armL2, -1.0, reach), -0.4, throwArm);
                p.crownLift += 3 * reach;
                p.crownTilt += 0.4 * reach;
                p.lean += -0.3 * reach + 0.4 * throwArm;
                p.jaw = Math.max(p.jaw, 0.6 * (reach + throwArm));
            }
            if (crownOut()) {                             // bald and furious until it comes back
                p.crown = false;
                p.brow = 1;
                p.eyes = 2;
                p.rage = Math.max(p.rage, 0.35);
                p.jaw = Math.max(p.jaw, 0.4);
            }

            double an = (t - angryAt) / KING_ANGRY;       // produce throw / fork slam: wind up, swing, recover
            if (an >= 0 && an < 1) {
                double wind = PixelCanvas.ease(an / 0.42), hit = PixelCanvas.easeIn(PixelCanvas.span(an, 0.42, 0.5));
                double back = PixelCanvas.ease(PixelCanvas.span(an, 0.6, 1));
                double up = wind * (1 - hit), down = hit * (1 - back), rest = 1 - up - down;
                p.armR1 = p.armR1 * rest + -1.25 * up + 0.6 * down;
                p.armR2 = p.armR2 * rest + -1.6 * up + 0.9 * down;
                p.forkAng = p.forkAng * rest + -0.25 * up + 2.2 * down;
                p.lean = p.lean * rest + -0.4 * up + 0.6 * down;
                p.squash = -1.0 * up + 1.8 * down * (1 - PixelCanvas.span(an, 0.5, 0.75) * 0.6);
                double fury = Math.sin(Math.min(1, an / 0.9) * Math.PI);
                if (!crownOut()) {
                    p.armL1 = PixelCanvas.lerp(p.armL1, -2.3, fury);
                    p.armL2 = PixelCanvas.lerp(p.armL2, -1.9 + Math.sin(t * 30) * 0.22, fury);
                }
                p.jaw = Math.max(p.jaw, 0.75 * fury);
                p.brow = Math.max(p.brow, fury);
                p.rage = Math.max(p.rage, 0.7 * fury * (0.75 + 0.25 * Math.sin(t * 25)));
                p.crownLift += 3 * down;
                if (an < 0.92) { p.eyes = 2; p.glow = 1; }
            }

            double h = (t - hurtAt) / KING_HURT;          // hit flinch
            if (h >= 0 && h < 1) {
                p.squash += 2.2 * Math.exp(-h * 5) * Math.cos(h * 14);
                p.lean += -0.5 * (1 - h) * Math.sin(h * 18);
                p.flash = Math.max(0, 1 - h * 3);
                p.jaw = Math.max(p.jaw, 0.45 * (1 - h));
                p.brow = 0;
                p.crownLift += 4 * Math.sin(Math.min(1, h * 2) * Math.PI);
                p.crownTilt += 0.3 * (1 - h) * Math.sin(h * 20);
                if (h < 0.6) p.eyes = 4;
            }
            if (core.healFlash() > 0) p.rage = Math.max(p.rage, core.healFlash() / 0.45 * 0.4);   // gulps up new trash

            if (defeatAt >= 0) {                          // collapse
                double d = Math.min(1, deathTime() / KING_DEFEAT);
                double shudder = 1 - PixelCanvas.span(d, 0.12, 0.25);
                double fall = PixelCanvas.ease(PixelCanvas.span(d, 0.2, 0.7));
                p.lean = Math.sin(t * 60) * 0.45 * shudder;
                p.flash = shudder * (Math.sin(t * 40) > 0 ? 0.5 : 0);
                p.rage = 0;
                p.eyes = d < 0.1 ? 4 : d < 0.72 ? 3 : 0;
                p.lid = d < 0.72 ? 0 : PixelCanvas.ease(PixelCanvas.span(d, 0.72, 0.8));
                p.brow = 0;
                p.crown = !crownFlying && !crownOut();
                p.deflate = fall;
                p.jaw = PixelCanvas.lerp(0.8, 0.2, fall);
                p.armL1 = PixelCanvas.lerp(-2.0, 2.6, fall);
                p.armL2 = PixelCanvas.lerp(-1.7, 2.9, fall);
                p.armR1 = PixelCanvas.lerp(-1.0, 0.45, fall);
                p.armR2 = PixelCanvas.lerp(-1.4, 0.95, fall);
                p.forkAng = PixelCanvas.lerp(0, 2.0, PixelCanvas.easeIn(PixelCanvas.span(d, 0.2, 0.45)));
                p.fork = d < 0.45;
                p.breathe = 0;
                p.squash = 0;
                p.flies = p.stink = d < 0.35;
                p.sprout = PixelCanvas.span(d, 0.7, 1);
                p.crownLift = 0;
            }
        }

        @Override public int sortY() { return (int) Math.round(ky); }

        @Override public void draw(Graphics2D g) {
            if (!isGone()) {
                buildPose();
                painter.paint(pose);
                if (defeatAt >= 0) canvas.fade(1 - PixelCanvas.span(deathTime(), KING_DEFEAT, KING_DEFEAT + KING_FADE));
                canvas.drawTo(g, baseX(), baseY(), WasteKingArt.AX, WasteKingArt.GROUND, 1);
            }
            fx.draw(g);
            if (crownFlying && !isGone()) {
                drawCrown(g, crX, crY, crZ, crRot, 1 - PixelCanvas.span(deathTime(), KING_DEFEAT, KING_DEFEAT + KING_FADE), 1);
            }
        }

        private void drawCrown(Graphics2D g, double x, double y, double z, double rot, double alpha, double scale) {
            crownCanvas.clear();
            WasteKingArt.crown(crownCanvas, crownCanvas.w / 2.0, crownCanvas.h / 2.0 + 4, rot, size * scale);
            crownCanvas.outline(WasteKingArt.OUT);
            crownCanvas.fade(alpha);
            int sw = (int) Math.round(12 * scale);
            g.setColor(new Color(0, 0, 0, (int) (60 * alpha)));
            g.fillOval((int) Math.round(x - sw / 2.0), (int) Math.round(y - 2), sw, 4);
            crownCanvas.drawTo(g, x, y - z, crownCanvas.w / 2.0, crownCanvas.h / 2.0 + 4, 1);
        }

        /** Shockwave ring, the crown in flight, and the shared effects, above everything. */
        @Override public void drawEffects(Graphics2D g) {
            if (shockAge >= 0) {
                Graphics2D gg = (Graphics2D) g.create();
                gg.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                double k = shockAge / SHOCK_TIME;
                int r = (int) Math.round(shockR), cx = (int) Math.round(baseX()), cy = (int) Math.round(baseY() - 2);
                gg.setStroke(new java.awt.BasicStroke(4f));
                gg.setColor(new Color(230, 170, 60, (int) (150 * (1 - k))));
                gg.drawOval(cx - r, cy - r / 2, r * 2, r);
                gg.setStroke(new java.awt.BasicStroke(1.2f));
                gg.setColor(new Color(255, 245, 200, (int) (230 * (1 - k))));
                gg.drawOval(cx - r, cy - r / 2, r * 2, r);
                gg.dispose();
            }
            if (defeatAt < 0) drawCrownPath(g);
            if (crownOut()) drawCrown(g, cbX, cbY, cbZ, time * 9, 1, CROWN_SCALE);
            core.drawEffects(g, baseX(), baseY() - 62 * size);
        }
    }

    /**
     * A can soldier summoned by the Waste King: a crushed soda can with a bottle-cap helmet and a
     * toothpick spear, waddling after the player (1 HP on contact). After a while it falls apart
     * into a piece of litter.
     */
    private final class CanSoldier extends Entity {
        private static final double LIFE = 10.0, POP = 0.3, COLLAPSE = 0.35;
        private final PixelCanvas sprite = new PixelCanvas(16, 18);
        private final int body, bodyHi, bodyDk;
        private final double speed;
        /** Feet position. */
        double fx, fy;
        private double age, walk;
        private int facing = 1;
        private boolean collapsed, collapseReported, poofed;

        CanSoldier(double fx, double fy, int variant, double speed) {
            super(fx - 4, fy - 12, 8, 12);
            this.fx = fx;
            this.fy = fy;
            this.speed = speed;
            int[][] cols = {{0xCF3B30, 0xF27E6E, 0x8E2018}, {0x3B7FD0, 0x7EB2EE, 0x24508A}, {0x3E9A4A, 0x7CC88A, 0x22602A}};
            int[] c = cols[Math.floorMod(variant, cols.length)];
            body = c[0];
            bodyHi = c[1];
            bodyDk = c[2];
        }

        boolean dangerous() { return !poofed && age >= POP && age < LIFE; }
        boolean collapsedNow() {
            if (collapsed && !collapseReported && !poofed) { collapseReported = true; return true; }
            return false;
        }
        boolean finished() { return poofed ? age >= 0.3 : age >= LIFE + COLLAPSE; }
        /** The king fell: it just vanishes in a puff (no litter). */
        void poof() { if (!poofed) { poofed = true; age = 0; } }

        void update(double dt, Player player, Level level) {
            age += dt;
            if (poofed) return;
            if (age >= LIFE) { collapsed = true; return; }
            if (age < POP) return;
            double tx = player.centerX(), ty = player.getY() + Player.H - 3;
            double dx = tx - fx, dy = ty - fy, d = Math.hypot(dx, dy);
            if (d < 2) return;
            double step = speed * dt;
            double mx = dx / d * step, my = dy / d * step;
            if (!level.isBlocked(new Rectangle2D.Double(fx + mx - 3, fy - 3, 6, 3))) fx += mx;
            if (!level.isBlocked(new Rectangle2D.Double(fx - 3, fy + my - 3, 6, 3))) fy += my;
            if (Math.abs(mx) > 0.02) facing = mx > 0 ? 1 : -1;
            walk += step / 5;
            x = fx - 4;
            y = fy - 12;
        }

        @Override public int sortY() { return (int) Math.round(fy); }

        @Override public void draw(Graphics2D g) {
            if (poofed) {
                double k = Math.min(1, age / 0.3);
                g.setColor(new Color(240, 236, 228, (int) (200 * (1 - k))));
                int r = (int) (3 + 6 * k);
                g.fillOval((int) fx - r, (int) fy - 6 - r, r * 2, r * 2);
                return;
            }
            double pop = PixelCanvas.easeOutBack(age / POP);
            double squash = age >= LIFE ? 1 - PixelCanvas.ease((age - LIFE) / COLLAPSE) : 1;
            paint();
            int w = sprite.w, h = (int) Math.max(1, Math.round(sprite.h * pop * squash));
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval((int) Math.round(fx - 4), (int) Math.round(fy - 1), 8, 3);
            int left = (int) Math.round(fx - (facing > 0 ? 7 : -8));
            g.drawImage(sprite.image(), left, (int) Math.round(fy - h + 1), facing * w, h, null);
        }

        private void paint() {
            PixelCanvas c = sprite;
            c.clear();
            double bob = Math.abs(Math.sin(walk * Math.PI)) * 1;
            double base = 16, top = 5 - bob;
            // legs
            double lA = Math.sin(walk * Math.PI) * 1.5;
            c.line(6, base - 3, 6 + lA, base, 0x2A2A2A);
            c.line(9, base - 3, 9 - lA, base, 0x2A2A2A);
            // crushed can body
            for (int yy = (int) top + 2; yy <= base - 3; yy++) {
                double k = (yy - top) / (base - top);
                int inset = (k > 0.35 && k < 0.6) ? 1 : 0;          // the crushed waist
                for (int xx = 4 + inset; xx <= 11 - inset; xx++) {
                    int col = xx <= 5 + inset ? bodyHi : xx >= 10 - inset ? bodyDk : body;
                    c.set(xx, yy, col);
                }
            }
            c.line(5, top + 2, 10, top + 2, 0xD6D6DA);               // silver rim
            c.set(8, top + 7, 0xF4F4F4);                             // label stripe
            c.set(9, top + 7, 0xF4F4F4);
            // bottle-cap helmet
            c.oval(7.5, top + 1, 3.6, 1.6, 0xF2C230);
            c.set(6, top, 0xFFF0A0);
            // eyes
            c.set(6, top + 4, 0xFFFFFF);
            c.set(9, top + 4, 0xFFFFFF);
            c.set(7, top + 4, 0x111111);
            c.set(10, top + 4, 0x111111);
            // toothpick spear
            double sp = Math.sin(walk * Math.PI * 2) * 0.6;
            c.line(12, top + 9, 13.5, top - 2 + sp, 0xD9B070);
            c.set(13.5, top - 3 + sp, 0xEEEEEE);
            c.set(11, top + 8, 0xF4D266);                            // tiny glove
            c.outline(0x150E14);
        }
    }
}
