package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Level 5: City Center. The ground is the illustrated city map (assets/maps/city_map.png, built
 * from city_reference.png by tools/build_city_map.py;
 * 2048x1152, drawn at 1/4 scale so it fills the 512x288 world). Buildings, trees, stalls
 * and the fountain are invisible collision blocks placed over the art, and the four sorting
 * bins stand on the bins painted in front of the recycling center. Cars drive the roads
 * (see {@link CityCar}) and cost the player 1 HP on contact.
 * All litter in this level uses the city-only items (Garbage variants 2-5).
 * At 75% clean the Gridlock Golem (see {@link GridlockGolem}) climbs out of a pothole on the main
 * street; its life is the trash left, like every level boss.
 */
public final class Level5City extends Level {
    private static final int LITTER_COUNT = 56;
    private static final String MAP_PATH = "assets/maps/city_map.png";

    /**
     * Solid areas in source-image pixels (4 image px = 1 world px): {x, y, width, height}.
     * They match the layout baked by tools/build_city_map.py; move both together.
     */
    private static final int[][] SOLID_AREAS = {
        {26, 40, 161, 107},      // park pond (top-left)
        {0, 256, 387, 251},      // brick building under the park, door on the main street
        {640, 0, 256, 224},      // small brick building (and the shadow gap behind the bakery)
        {640, 225, 256, 287},    // bakery
        {896, 8, 128, 88},       // two dumpsters at the back of the service lane
        {1024, 0, 384, 367},     // recycling center
        {1664, 0, 384, 448},     // tall glass office tower (teal storefront at street level)
        {1814, 479, 75, 38},     // cafe tables in front of the tower
        {1942, 479, 75, 38},
        {29, 797, 166, 130},     // market stalls
        {220, 797, 167, 130},
        {412, 797, 167, 130},
        {604, 797, 166, 130},
        {70, 1014, 116, 66},     // plaza benches
        {310, 1014, 115, 66},
        {551, 1014, 115, 66},
        {607, 712, 161, 48},     // bus stop
        {865, 799, 350, 288},    // laundromat
        {1664, 817, 384, 287},   // bottom-right brick house
        {1487, 594, 100, 98},    // roundabout fountain
    };

    /** Street lamps stand at these world x positions on both sides of the main street. */
    private static final int[] LAMP_XS = { 38, 102, 230, 326, 438 };
    private static final int NORTH_LAMP_Y = 126, SOUTH_LAMP_Y = 174, LAMP_H = 16;

    /**
     * Round trees painted on the map, top-left of each crown in source-image pixels. Every crown
     * is 96x84 with a 16x20 trunk under its middle. Only the trunk blocks; the crown is re-drawn
     * over the player (see {@link SceneryOverlay}) so you can walk behind the leaves.
     */
    private static final int[][] TREE_CROWNS = {
        {216, 4}, {264, 108},     // park
        {736, 988},               // plaza
        {1280, 864}, {1280, 1012} // garden column next to the laundromat
    };
    private static final int CROWN_W = 96, CROWN_H = 84, TRUNK_W = 16, TRUNK_H = 20;

    // ----- traffic (world pixels) -----
    private static final String CARS_PATH = "assets/maps/city_cars.png";
    private static final int CAR_SPRITE_COUNT = 5;
    private static final double ROUNDABOUT_X = 384, ROUNDABOUT_Y = 160, ROUNDABOUT_R = 25;
    private static final double EAST_LANE_Y = 169, WEST_LANE_Y = 152;      // main road
    private static final double SOUTH_LANE_X = 376, NORTH_LANE_X = 392;    // right road
    private static final double HURT_COOLDOWN = 2;
    /** Every route has one entry point, then a 140-degree arc sampled every 10 degrees. */
    private static final int ARC_START_INDEX = 1, ARC_STEPS = 14;

    private final BufferedImage city = loadCity();
    /** The map at world size, so tree overlays use exactly the pixels drawn on the ground. */
    private final BufferedImage cityWorld = scaleToWorld(city);
    private final BufferedImage[] carSprites = loadCarSprites();
    private final java.util.List<CityCar> cars = new java.util.ArrayList<>();
    private final CityCar.Roundabout roundabout = new CityCar.Roundabout();
    private double hurtCooldown;

    @Override public String getName() { return "Level 5: City Center"; }

    @Override public String introMessage() {
        return "The city center is buried in litter! Sort it at the bins by the recycling center. Watch out for cars... and the rumbling under the road!";
    }
    @Override public String clearMessage() { return "Gridlock Golem defeated! The city center is clean!"; }

    // ----- boss: the Gridlock Golem -----
    /** Potholes on the main street (world feet points) the golem can climb out of. */
    private static final double[][] BOSS_POTHOLES = { {200, 150}, {468, 150}, {50, 167} };
    private static final int BOSS_REWARD = 200, SPOTLESS_BONUS = 50;

    @Override public boolean hasTrashBoss() { return true; }
    @Override public boolean allowsTrashMonster() { return false; }
    /** Gridlock Golem +200, and +50 more for a spotless city. */
    @Override public int clearBonus() { return BOSS_REWARD + (cleanliness() >= 95 ? SPOTLESS_BONUS : 0); }

    /**
     * The golem climbs out of the pothole farthest from the player. Waits (returns null) while the
     * player stands right on it.
     */
    @Override public TrashBoss createTrashBoss(Player player) {
        double px = player.centerX(), py = player.getY() + Player.H;
        double[] best = BOSS_POTHOLES[0];
        for (double[] spot : BOSS_POTHOLES)
            if (Math.hypot(spot[0] - px, spot[1] - py) > Math.hypot(best[0] - px, best[1] - py)) best = spot;
        if (Math.hypot(best[0] - px, best[1] - py) < 28) return null;
        return new GridlockGolem(best[0], best[1]);
    }

    private boolean rushHour, trafficJam;

    /** Phase 3 of the boss fight: every car drives faster. Restarting the level resets the traffic. */
    public void setRushHour(boolean on) {
        rushHour = on;
        applyTrafficSpeed();
    }

    /** The boss's traffic jam: every car on the map stops where it is until the jam clears. */
    public void setTrafficJam(boolean on) {
        trafficJam = on;
        applyTrafficSpeed();
    }

    private void applyTrafficSpeed() {
        for (CityCar car : cars) car.setSpeedScale(trafficJam ? 0 : rushHour ? 1.6 : 1);
    }

    /**
     * The boss charging through (x, y): every car within {@code radius} is smashed and flung along
     * (dirX, dirY). Returns how many were hit.
     */
    public int wreckCarsNear(double x, double y, double radius, double dirX, double dirY) {
        int hit = 0;
        for (CityCar car : cars) {
            if (car.isWrecked() || Math.hypot(car.centerX() - x, car.centerY() - y) > radius) continue;
            double side = (car.centerX() - x) * -dirY + (car.centerY() - y) * dirX >= 0 ? 1 : -1;
            car.wreck(dirX * 140 - dirY * side * 90, dirY * 140 + dirX * side * 90);
            hit++;
        }
        return hit;
    }

    /** One of the city car sprites (east-facing, 4 image px per world px), for the boss's jam cars. */
    public BufferedImage carSprite(int index) { return carSprites[Math.floorMod(index, carSprites.length)]; }

    @Override protected void buildTerrain() { spawnCol = 16; spawnRow = 9; }

    @Override protected void buildProps() {
        for (int[] area : SOLID_AREAS) blockImageRect(area[0], area[1], area[2], area[3]);
        for (int x : LAMP_XS) {   // only the lamp's foot blocks, like in the other levels
            props.add(new StaticBlock(x + 1, NORTH_LAMP_Y + LAMP_H - 3, 4, 3));
            props.add(new StaticBlock(x + 1, SOUTH_LAMP_Y + LAMP_H - 3, 4, 3));
        }
        noLitterZones.add(new java.awt.geom.Rectangle2D.Double(312, 198, 44, 18));   // flower planter
        addTrees();
        spawnTraffic();
    }

    /**
     * Right-hand traffic: eastbound in the lower lane, westbound in the upper lane, and every
     * route that meets the roundabout circles it counter-clockwise around the fountain.
     */
    private void spawnTraffic() {
        cars.clear();
        rushHour = trafficJam = false;
        roundabout.clear();
        hurtCooldown = 0;
        double[][] east = route(new double[][] {{-40, EAST_LANE_Y}}, 160, 20, new double[][] {{560, EAST_LANE_Y}});
        double[][] west = route(new double[][] {{560, WEST_LANE_Y}}, -20, -160, new double[][] {{-40, WEST_LANE_Y}});
        double[][] south = route(new double[][] {{SOUTH_LANE_X, -40}}, -110, -250, new double[][] {{SOUTH_LANE_X, 330}});
        double[][] north = route(new double[][] {{NORTH_LANE_X, 330}}, 70, -70, new double[][] {{NORTH_LANE_X, -40}});

        // One car per route; start points are on the straights, away from the roundabout.
        addCar(east, 0, 42, 0.10);
        addCar(west, 2, 42, 0.02);
        addCar(south, 1, 36, 0.05);
        addCar(north, 4, 36, 0.75);
    }

    private void addCar(double[][] path, int sprite, double speed, double startFraction) {
        CityCar car = new CityCar(path, ARC_START_INDEX, ARC_START_INDEX + ARC_STEPS, roundabout,
                carSprites[sprite % carSprites.length], speed, startFraction);
        cars.add(car);
        addNpc(car);
    }

    /** Straight entry, a counter-clockwise arc around the fountain from one angle to another, straight exit. */
    private static double[][] route(double[][] entry, double fromDeg, double toDeg, double[][] exit) {
        java.util.List<double[]> points = new java.util.ArrayList<>(java.util.Arrays.asList(entry));
        int steps = (int) Math.ceil(Math.abs(toDeg - fromDeg) / 10.0);
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / steps);
            points.add(new double[] {ROUNDABOUT_X + Math.cos(angle) * ROUNDABOUT_R,
                    ROUNDABOUT_Y + Math.sin(angle) * ROUNDABOUT_R});
        }
        points.addAll(java.util.Arrays.asList(exit));
        return points.toArray(new double[0][]);
    }

    @Override public void update(double dt, Player player) {
        hurtCooldown = Math.max(0, hurtCooldown - dt);
        for (CityCar car : cars) car.update(dt, cars);
        if (hurtCooldown > 0) return;
        double feetX = player.centerX(), feetY = player.getY() + Player.H - 3;
        for (CityCar car : cars) {
            if (car.hits(feetX, feetY, 2)) {
                hurtCooldown = HURT_COOLDOWN;
                player.hurt(1);
                player.flashRed();
                announce("Hit by a car! -1 HP. Watch the traffic!");
                return;
            }
        }
    }

    /** Placed exactly on the green / blue / grey / red bins painted on the map. */
    @Override protected void buildBins() {
        bins.add(new TrashBin(GarbageType.BIODEGRADABLE, 17, 6));
        bins.add(new TrashBin(GarbageType.RECYCLABLE, 18, 6));
        bins.add(new TrashBin(GarbageType.NON_RECYCLABLE, 19, 6));
        bins.add(new TrashBin(GarbageType.HAZARDOUS, 20, 6));
    }

    @Override protected void buildGarbage() { scatterGarbage(LITTER_COUNT); }

    /** Cities have more hazardous waste (e-waste, paint, motor oil) than the earlier levels. */
    @Override protected GarbageType randomType() {
        int roll = rng.nextInt(100);
        if (roll < 30) return GarbageType.RECYCLABLE;
        if (roll < 52) return GarbageType.BIODEGRADABLE;
        if (roll < 84) return GarbageType.NON_RECYCLABLE;
        return GarbageType.HAZARDOUS;
    }

    @Override protected int randomVariant(GarbageType type) {
        return Garbage.FIRST_CITY_VARIANT + rng.nextInt(Garbage.VARIANT_COUNT - Garbage.FIRST_CITY_VARIANT);
    }

    @Override protected boolean isTrashTileAllowed(double x, double y, TileType tile) {
        return x >= 4 && y >= 4 && x <= Config.WORLD_W - 12 && y <= Config.WORLD_H - 12;
    }

    @Override protected void drawGround(Graphics2D g) {
        g.drawImage(cityWorld, 0, 0, null);
    }

    /** Trunk collision (with 2 image px to spare on each side) plus a crown overlay for each tree. */
    private void addTrees() {
        double s = Config.WORLD_W / (double) city.getWidth();
        for (int[] crown : TREE_CROWNS) {
            int trunkX = crown[0] + (CROWN_W - TRUNK_W) / 2, trunkY = crown[1] + CROWN_H;
            blockImageRect(trunkX - 2, trunkY, TRUNK_W + 4, TRUNK_H);

            int x = (int) Math.floor(crown[0] * s), y = (int) Math.floor(crown[1] * s);
            int w = (int) Math.ceil(CROWN_W * s) + 1, h = (int) Math.ceil((CROWN_H + TRUNK_H) * s) + 1;
            Ellipse2D crownShape = new Ellipse2D.Double(crown[0] * s - x - 0.5, crown[1] * s - y - 0.5,
                    CROWN_W * s + 1, CROWN_H * s + 1);
            double tx0 = trunkX * s - x - 0.5, tx1 = (trunkX + TRUNK_W) * s - x + 0.5;
            BufferedImage art = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    boolean trunk = px + 0.5 >= tx0 && px + 0.5 <= tx1 && py + 0.5 >= CROWN_H * s - 1;
                    if ((crownShape.contains(px + 0.5, py + 0.5) || trunk) && x + px < Config.WORLD_W
                            && y + py < Config.WORLD_H) {
                        art.setRGB(px, py, cityWorld.getRGB(x + px, y + py));
                    }
                }
            }
            props.add(new SceneryOverlay(art, x, y, (int) Math.round((trunkY + TRUNK_H) * s)));
        }
    }

    private static BufferedImage scaleToWorld(BufferedImage src) {
        BufferedImage out = new BufferedImage(Config.WORLD_W, Config.WORLD_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, Config.WORLD_W, Config.WORLD_H, null);
        g.dispose();
        return out;
    }

    private void blockImageRect(int imageX, int imageY, int imageW, int imageH) {
        double sx = Config.WORLD_W / (double) city.getWidth();
        double sy = Config.WORLD_H / (double) city.getHeight();
        int left = Math.max(0, (int) Math.floor(imageX * sx));
        int top = Math.max(0, (int) Math.floor(imageY * sy));
        int right = Math.min(Config.WORLD_W, (int) Math.ceil((imageX + imageW) * sx));
        int bottom = Math.min(Config.WORLD_H, (int) Math.ceil((imageY + imageH) * sy));
        if (right > left && bottom > top) props.add(new StaticBlock(left, top, right - left, bottom - top));
    }

    /** city_cars.png holds east-facing 112x56 car sprites stacked vertically. */
    private static BufferedImage[] loadCarSprites() {
        try {
            BufferedImage sheet = ImageIO.read(new File(CARS_PATH));
            if (sheet == null) throw new IllegalStateException("Unsupported image format: " + CARS_PATH);
            int height = sheet.getHeight() / CAR_SPRITE_COUNT;
            BufferedImage[] sprites = new BufferedImage[CAR_SPRITE_COUNT];
            for (int i = 0; i < sprites.length; i++)
                sprites[i] = sheet.getSubimage(0, i * height, sheet.getWidth(), height);
            return sprites;
        } catch (Exception e) {
            throw new IllegalStateException("City car sprites are missing: " + CARS_PATH, e);
        }
    }

    private static BufferedImage loadCity() {
        try {
            BufferedImage image = ImageIO.read(new File(MAP_PATH));
            if (image == null) throw new IllegalStateException("Unsupported image format: " + MAP_PATH);
            return image;
        } catch (Exception e) {
            throw new IllegalStateException("City map image is missing: " + MAP_PATH, e);
        }
    }
}
