package com.ecoquest.level2;

import com.ecoquest.level1.Garbage;
import com.ecoquest.level1.GarbageType;
import com.ecoquest.level1.Level;
import com.ecoquest.level1.Player;
import com.ecoquest.level1.Prop;
import com.ecoquest.level1.TrashBin;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static com.ecoquest.level1.Config.*;

public class Level2Park extends Level {
    public static final int GARBAGE_COUNT = 40;
    public static final int TARGET_CLEANLINESS = 80;
    public static final int BEAST_SPAWN_CLEANLINESS = 50;
    public static final int TIME_LIMIT = 180;
    public static final int BEAST_REWARD = 150;

    private static final int[][] PATHS = {
        {245, 0, 28, 288}, {52, 49, 196, 20}, {271, 49, 206, 20}, {448, 49, 28, 194},
        {0, 140, 248, 24}, {24, 221, 452, 23}, {271, 132, 18, 22}
    };
    private static final int[][] GREEN_TREES = {
        {-8, -16, 46}, {136, -6, 62}, {318, -24, 44}, {462, -14, 52}, {114, 120, 56},
        {-10, 214, 56}, {118, 232, 48}, {162, 206, 60}, {470, 218, 52}, {494, 116, 30}
    };
    private static final int[][] CHERRY_TREES = {
        {50, -12, 52}, {-14, 44, 44}, {188, 36, 54}, {270, -18, 52}, {408, -16, 50},
        {482, 44, 44}, {-14, 132, 44}, {34, 128, 56}, {170, 132, 50}, {318, 236, 58}
    };
    private static final int[][] BENCHES = {
        {87, 34}, {34, 116}, {52, 205}, {105, 205}, {310, 34},
        {378, 34}, {399, 205}, {496, 116}, {87, 262}, {399, 262}
    };
    private static final int[][] LAMPS = {
        {124, 8}, {279, 8}, {466, 8}, {5, 90}, {160, 90}, {484, 90},
        {92, 180}, {238, 180}, {436, 168}, {386, 226}
    };
    private static final int[][] BOTS = {
        {60, 52, 228, 52}, {285, 52, 430, 52}, {455, 70, 455, 205},
        {5, 145, 228, 145}, {32, 226, 228, 226}, {285, 226, 430, 226}
    };

    private final List<LitterBot> bots = new ArrayList<>();
    private ParkGround ground;
    private LitterBeast beast;
    private boolean beastDefeated;
    private int pollution, sorted;
    private double hurtCooldown;

    @Override public String getName() { return "Level 2: Public Park"; }

    @Override
    protected void buildTerrain() {
        spawnCol = 16;
        spawnRow = 6;
    }

    @Override
    protected void buildProps() {
        List<Rectangle> paths = new ArrayList<>();
        for (int[] p : PATHS) paths.add(new Rectangle(p[0], p[1], p[2], p[3]));
        List<int[]> petals = new ArrayList<>();
        for (int[] t : CHERRY_TREES) petals.add(new int[] { t[0] + t[2] / 2, t[1] + t[2], t[2] });
        ground = new ParkGround(paths, petals);
        props.add(ground);

        props.add(new Pond(new Rectangle(281, 74, 158, 84), new Rectangle(296, 150, 143, 66)));
        props.add(new Blocker(284, 77, 152, 78));
        props.add(new Blocker(299, 153, 137, 60));

        for (int[] t : GREEN_TREES) props.add(new GreenTree(t[0], t[1], t[2]));
        for (int[] t : CHERRY_TREES) props.add(new CherryTree(t[0], t[1], t[2]));
        for (int[] b : BENCHES) props.add(new Bench(b[0], b[1]));
        for (int[] l : LAMPS) props.add(new ParkLamp(l[0], l[1]));

        for (int[] b : BOTS) {
            LitterBot bot = new LitterBot(b[0], b[1], b[2], b[3]);
            bots.add(bot);
            addNpc(bot);
        }
    }

    @Override
    protected void buildBins() {
        bins.add(new TrashBin(GarbageType.BIODEGRADABLE, 11, 7));
        bins.add(new TrashBin(GarbageType.RECYCLABLE, 12, 7));
        bins.add(new TrashBin(GarbageType.NON_RECYCLABLE, 13, 7));
        bins.add(new TrashBin(GarbageType.HAZARDOUS, 14, 7));
    }

    @Override
    protected void buildGarbage() {
        for (int tries = 0; garbage.size() < GARBAGE_COUNT && tries < 8000; tries++) placeLitter();
    }

    private boolean placeLitter() {
        double gx = 2 + rng.nextInt(WORLD_W - 12), gy = 2 + rng.nextInt(WORLD_H - 12);
        if (!ground.onPath(gx + 4, gy + 4) && rng.nextInt(100) < 55) return false;
        if (Math.hypot(gx - getSpawnX(), gy - getSpawnY()) < 30 || !isFree(new Rectangle2D.Double(gx - 2, gy - 2, 12, 12)))
            return false;
        garbage.add(new Garbage(randomType(), rng.nextInt(2), gx, gy));
        return true;
    }

    private boolean isFree(Rectangle2D box) {
        for (Prop p : props) if ((p.collisionBox() != null || !p.isGroundDecal()) && p.bounds().intersects(box)) return false;
        for (TrashBin b : bins) if (b.bounds().intersects(box)) return false;
        for (Garbage g : garbage) if (g.bounds().intersects(box)) return false;
        return true;
    }

    @Override
    public void update(double dt, Player player) {
        hurtCooldown = Math.max(0, hurtCooldown - dt);
        for (LitterBot b : bots) {
            b.update(dt);
            if (b.readyToDrop()) garbage.add(new Garbage(randomType(), rng.nextInt(2), b.dropX(), b.dropY()));
            if (b.isTouching(player)) hurt(player, "A LitterBot bumped you! -1 HP");
        }

        if (beast == null && cleanliness() >= BEAST_SPAWN_CLEANLINESS) {
            beast = new LitterBeast(446, 218);
            addNpc(beast);
            for (LitterBot b : bots) b.activate();
            announce("The Litter Beast appeared! The LitterBots sped up!");
        }
        if (beast == null || beastDefeated) return;

        beast.update(dt, this);
        if (beast.readyToSpawnLitter()) {
            for (int i = 0; i < 50 && !placeLitter(); i++);
            pollution++;
        }
        if (beast.isTouching(player)) hurt(player, "The Litter Beast hit you! -1 HP");
        if (cleanliness() >= TARGET_CLEANLINESS) {
            beastDefeated = true;
            beast.defeat();
            player.addMoney(BEAST_REWARD);
        }
    }

    private void hurt(Player player, String text) {
        if (hurtCooldown > 0) return;
        hurtCooldown = 2;
        player.hurt(1);
        player.flashRed();
        announce(text);
    }

    @Override
    public void registerCorrectSort() {
        super.registerCorrectSort();
        sorted++;
    }

    @Override
    public int cleanliness() {
        int value = BASE_CLEANLINESS + (int) Math.round((100 - BASE_CLEANLINESS) * (double) sorted / GARBAGE_COUNT) - pollution;
        return Math.max(0, Math.min(100, value));
    }

    @Override public boolean hasTrashMonster() { return false; }
    @Override public int timeLimit() { return TIME_LIMIT; }
    @Override public boolean isCleared(Player player) { return beastDefeated; }
    @Override public String introMessage() { return "Reach 80% park health in 3 minutes! Avoid the LitterBots."; }
    @Override public String clearMessage() { return "Litter Beast defeated! +\u20B1150  Park cleaned! +\u20B1100"; }
}
