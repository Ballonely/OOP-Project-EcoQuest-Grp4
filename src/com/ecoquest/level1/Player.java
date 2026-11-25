package com.ecoquest.level1;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The city cleaner: movement, collision, bag (inventory) and stats (money, score, health). */
public class Player extends Entity {
    private static final int FRAME_SIZE = 16;
    private static final int FRAME_COLUMNS = 4;
    private static final int FRAME_ROWS = 5;
    private static final int IDLE_ROW = 0;
    private static final int FRONT_WALK_ROW = 1;
    private static final int LEFT_WALK_ROW = 2;
    private static final int RIGHT_WALK_ROW = 3;
    private static final int BACK_WALK_ROW = 4;
    private static final double WALK_FRAME_DURATION = 0.12;
    private static final double IDLE_FRAME_DURATION = 0.65;
    private static final double HIT_FLASH_DURATION = 0.8;
    private static final double HIT_FLASH_INTERVAL = 0.1;

    public static final int W = FRAME_SIZE, H = FRAME_SIZE;
    private static final String DEFAULT_SHEET = "spritesheet.png";

    private double speed = 62;
    private int capacity = 5;
    private double pickupRange = 13;
    private int maxHealth = 5, health = 5;
    private int money, score;
    private static final int FIRST_BLOCKED_POINT = 5;
    private int blockEvery;      // 0 = no damage absorption
    private int damagePoints;    // total damage points received

    private final List<Garbage> bag = new ArrayList<>();
    private int selected;
    private boolean onBoat;
    private double boatHeading, boatSpeed;
    private double slowTimer;
    /** A much stronger slow (e.g. caught by the burrowing Morphling). */
    private double heavySlowTimer;
    private double stunTimer, mudTimer;
    /** Time left airborne after a geyser launch, and that launch's full length. */
    private double airTimer, airDuration;
    private static final double GEYSER_AIR_TIME = 0.75, GEYSER_STUN_AFTER = 0.55, GEYSER_HEIGHT = 26;

    private final BufferedImage[][] frames = new BufferedImage[FRAME_ROWS][FRAME_COLUMNS];
    private final BufferedImage[][] redFrames = new BufferedImage[FRAME_ROWS][FRAME_COLUMNS];
    private final BufferedImage[][] mudFrames = new BufferedImage[FRAME_ROWS][FRAME_COLUMNS];
    private int animationRow = FRONT_WALK_ROW;
    private double walkFrameTimer;
    private double idleFrameTimer;
    private double hitFlashTimer;

    public Player(double x, double y) {
        this(x, y, DEFAULT_SHEET);
    }

    /** @param sheetName spritesheet file under assets/, e.g. a cleaner role's outfit */
    public Player(double x, double y, String sheetName) {
        super(x, y, W, H);
        BufferedImage sheet = loadSpriteSheet(sheetName == null ? DEFAULT_SHEET : sheetName);
        if (sheet.getWidth() != FRAME_SIZE * FRAME_COLUMNS
                || sheet.getHeight() != FRAME_SIZE * FRAME_ROWS) {
            throw new IllegalStateException("Expected " + sheetName + " to be 64x80 pixels, but found "
                    + sheet.getWidth() + "x" + sheet.getHeight());
        }

        for (int row = 0; row < FRAME_ROWS; row++) {
            for (int column = 0; column < FRAME_COLUMNS; column++) {
                frames[row][column] = sheet.getSubimage(
                        column * FRAME_SIZE, row * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);
                redFrames[row][column] = tintRed(frames[row][column]);
                mudFrames[row][column] = tintMud(frames[row][column]);
            }
        }
    }

    private BufferedImage tintRed(BufferedImage source) {
        BufferedImage tinted = new BufferedImage(FRAME_SIZE, FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < FRAME_SIZE; y++) {
            for (int x = 0; x < FRAME_SIZE; x++) {
                int argb = source.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) continue;
                int red = Math.min(255, ((argb >>> 16) & 0xFF) + 100);
                int green = ((argb >>> 8) & 0xFF) / 4;
                int blue = (argb & 0xFF) / 4;
                tinted.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return tinted;
    }

    private BufferedImage tintMud(BufferedImage source) {
        BufferedImage tinted = new BufferedImage(FRAME_SIZE, FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < FRAME_SIZE; y++) {
            for (int x = 0; x < FRAME_SIZE; x++) {
                int argb = source.getRGB(x, y), alpha = argb >>> 24;
                if (alpha == 0) continue;
                int red = (int) (((argb >>> 16) & 0xFF) * 0.42 + 112 * 0.58);
                int green = (int) (((argb >>> 8) & 0xFF) * 0.42 + 68 * 0.58);
                int blue = (int) ((argb & 0xFF) * 0.42 + 35 * 0.58);
                tinted.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return tinted;
    }

    private static BufferedImage loadSpriteSheet(String sheetName) {
        File[] candidates = {
            new File(System.getProperty("user.dir"), "assets/" + sheetName),
            new File(System.getProperty("user.dir"), "bin/assets/" + sheetName)
        };

        for (File candidate : candidates) {
            if (!candidate.isFile()) continue;
            try {
                BufferedImage sheet = ImageIO.read(candidate);
                if (sheet == null) {
                    throw new IllegalStateException("Unsupported image format: " + candidate);
                }
                return sheet;
            } catch (IOException e) {
                throw new IllegalStateException("Could not read player spritesheet: " + candidate, e);
            }
        }

        if (!DEFAULT_SHEET.equals(sheetName)) {
            System.err.println("Role spritesheet " + sheetName + " not found; using " + DEFAULT_SHEET);
            return loadSpriteSheet(DEFAULT_SHEET);
        }
        throw new IllegalStateException("Player spritesheet not found. Expected assets/" + sheetName + " "
                + "under " + System.getProperty("user.dir"));
    }

    // ---------- movement ----------
    private Rectangle2D.Double feet(double nx, double ny) {
        return new Rectangle2D.Double(nx + 3, ny + H - 5, W - 6, 5);
    }

    public void update(double dt, int dx, int dy, Level level) {
        advanceEffects(dt);
        hitFlashTimer = Math.max(0, hitFlashTimer - dt);
        if (stunTimer > 0) {
            walkFrameTimer = 0;
            idleFrameTimer = (idleFrameTimer + dt) % (IDLE_FRAME_DURATION * 2);
            return;
        }
        if (dx != 0 || dy != 0) {
            if (Math.abs(dx) > Math.abs(dy)) {
                animationRow = dx < 0 ? LEFT_WALK_ROW : RIGHT_WALK_ROW;
            } else {
                animationRow = dy < 0 ? BACK_WALK_ROW : FRONT_WALK_ROW;
            }
            walkFrameTimer = (walkFrameTimer + dt) % (WALK_FRAME_DURATION * FRAME_COLUMNS);
            idleFrameTimer = 0;
        } else {
            walkFrameTimer = 0;
            idleFrameTimer = (idleFrameTimer + dt) % (IDLE_FRAME_DURATION * 2);
            return;
        }

        double len = Math.sqrt(dx * dx + dy * dy);
        double v = speed * movementFactor() * level.speedFactorAt(feet(x, y)) * dt;
        double mx = dx / len * v, my = dy / len * v;

        if (!level.isBlocked(feet(x + mx, y))) x += mx;
        if (!level.isBlocked(feet(x, y + my))) y += my;
    }

    // ---------- coastal effects (Level 4 boat, slow and geyser launch) ----------
    public void setOnBoat(boolean enabled) {
        if (enabled && !onBoat) {           // the boat points the way the player was walking
            onBoat = true;
            boatSpeed = 0;
            boatHeading = switch (animationRow) {
                case LEFT_WALK_ROW -> Math.PI;
                case BACK_WALK_ROW -> -Math.PI / 2;
                case FRONT_WALK_ROW -> Math.PI / 2;
                default -> 0;
            };
        } else if (!enabled && onBoat) { onBoat = false; boatSpeed = 0; }
    }

    public boolean isOnBoat() { return onBoat; }

    public void updateBoat(double dt, int throttle, int turn, Level level) {
        advanceEffects(dt);
        hitFlashTimer = Math.max(0, hitFlashTimer - dt);
        if (stunTimer > 0) {
            boatSpeed = 0;
            walkFrameTimer = 0;
            idleFrameTimer = (idleFrameTimer + dt) % (IDLE_FRAME_DURATION * 2);
            return;
        }
        boatHeading += turn * (1.6 + 0.6 * Math.min(1, Math.abs(boatSpeed) / 52.0)) * dt;
        if (throttle > 0) boatSpeed = Math.min(52 * movementFactor(), boatSpeed + 34 * movementFactor() * dt);
        else if (throttle < 0) boatSpeed = Math.max(-18 * movementFactor(), boatSpeed - 25 * movementFactor() * dt);
        else if (boatSpeed > 0) boatSpeed = Math.max(0, boatSpeed - 13 * dt);
        else boatSpeed = Math.min(0, boatSpeed + 13 * dt);

        double mx = Math.cos(boatHeading) * boatSpeed * dt;
        double my = Math.sin(boatHeading) * boatSpeed * dt;
        // Against a railing, rock or the map edge the boat slides along it instead of sticking.
        if (!level.isBlocked(feet(x + mx, y + my))) { x += mx; y += my; }
        else {
            boolean slid = false;
            if (Math.abs(mx) > 0.01 && !level.isBlocked(feet(x + mx, y))) { x += mx; slid = true; }
            if (Math.abs(my) > 0.01 && !level.isBlocked(feet(x, y + my))) { y += my; slid = true; }
            if (!slid) boatSpeed *= 0.2;
        }
        if (Math.abs(mx) + Math.abs(my) > 0.1) {
            animationRow = Math.abs(mx) > Math.abs(my) ? (mx < 0 ? LEFT_WALK_ROW : RIGHT_WALK_ROW)
                    : (my < 0 ? BACK_WALK_ROW : FRONT_WALK_ROW);
            walkFrameTimer = (walkFrameTimer + dt) % (WALK_FRAME_DURATION * FRAME_COLUMNS);
            idleFrameTimer = 0;
        } else {
            walkFrameTimer = 0;
            idleFrameTimer = (idleFrameTimer + dt) % (IDLE_FRAME_DURATION * 2);
        }
    }

    public void applySlow(double seconds) { slowTimer = Math.max(slowTimer, seconds); }
    /** Slows the player to a crawl (a quarter of normal speed) for {@code seconds}. */
    public void applyHeavySlow(double seconds) { heavySlowTimer = Math.max(heavySlowTimer, seconds); }
    /** Freezes the player in place briefly (e.g. bumped by a market hand cart). */
    public void applyStun(double seconds) { stunTimer = Math.max(stunTimer, seconds); }
    /** A geyser throws the player into the air; they can't move until a moment after landing. */
    public void launchByGeyser() {
        airDuration = airTimer = GEYSER_AIR_TIME;
        stunTimer = Math.max(stunTimer, GEYSER_AIR_TIME + GEYSER_STUN_AFTER);
        boatSpeed = 0;
    }

    public boolean isAirborne() { return airTimer > 0; }

    /** Height above the ground (px) during a geyser launch. */
    private double airHeight() {
        if (airTimer <= 0) return 0;
        return Math.sin(Math.PI * (1 - airTimer / airDuration)) * GEYSER_HEIGHT;
    }
    /** Pushes the player by (dx, dy) in small steps, stopping at anything solid (e.g. a boss hit). */
    public void knockBack(double dx, double dy, Level level) {
        int steps = 8;
        for (int i = 0; i < steps; i++) {
            double sx = dx / steps, sy = dy / steps;
            if (!level.isBlocked(feet(x + sx, y))) x += sx;
            if (!level.isBlocked(feet(x, y + sy))) y += sy;
        }
    }
    public double getSlowTimer() { return Math.max(slowTimer, heavySlowTimer); }
    public boolean isStunned() { return stunTimer > 0; }
    private void advanceEffects(double dt) {
        slowTimer = Math.max(0, slowTimer - dt);
        heavySlowTimer = Math.max(0, heavySlowTimer - dt);
        stunTimer = Math.max(0, stunTimer - dt);
        airTimer = Math.max(0, airTimer - dt);
        mudTimer = Math.max(0, mudTimer - dt);
    }
    private double movementFactor() { return heavySlowTimer > 0 ? 0.25 : slowTimer > 0 ? 0.55 : 1.0; }

    // ---------- bag ----------
    public boolean bagFull()    { return bag.size() >= capacity; }
    public boolean bagEmpty()   { return bag.isEmpty(); }
    public int getCapacity()    { return capacity; }
    public double getPickupRange() { return pickupRange; }
    public List<Garbage> getBag() { return bag; }
    public int getSelectedIndex() { return selected; }

    public void pickUp(Garbage item) { bag.add(item); selected = bag.size() - 1; money += 2; }
    public void addCapacity(int amount) { capacity += Math.max(0, amount); }
    public void increaseSpeed(double multiplier) { speed *= multiplier; }
    /**
     * Gives effective extra health (e.g. 1.10 = +10%) by ignoring a share of damage, since hearts
     * are whole numbers. At +10% one damage point in every 11 is shrugged off; the first one is
     * the 5th point taken, so the bonus helps even without healing.
     */
    /** Raises max health and current health together (Volunteer's extra heart). */
    public void addMaxHealth(int amount) {
        if (amount <= 0) return;
        maxHealth += amount;
        health += amount;
    }

    public void setHealthMultiplier(double multiplier) {
        blockEvery = multiplier > 1.0 ? (int) Math.round(multiplier / (multiplier - 1.0)) : 0;
    }
    public void setPickupRange(double range) { pickupRange = Math.max(pickupRange, range); }

    public GarbageType selectedItem() { return bag.isEmpty() ? null : bag.get(selected).getType(); }

    public void cycleSelected() { if (!bag.isEmpty()) selected = (selected + 1) % bag.size(); }

    public GarbageType removeSelected() {
        return removeSelectedGarbage().getType();
    }

    public Garbage removeSelectedGarbage() {
        Garbage item = bag.remove(selected);
        selected = bag.isEmpty() ? 0 : Math.min(selected, bag.size() - 1);
        return item;
    }

    // ---------- stats ----------
    public int getMoney()     { return money; }
    public int getScore()     { return score; }
    public int getHealth()    { return health; }
    public int getMaxHealth() { return maxHealth; }
    public void addMoney(int m) { money += m; }
    public void setMoney(int amount) { money = Math.max(0, amount); }
    public void addScore(int s) { score = Math.max(0, score + s); }
    public void hurt(int dmg) {
        int applied = 0;
        for (int i = 0; i < dmg; i++) {
            damagePoints++;
            boolean blocked = blockEvery > 0 && damagePoints % blockEvery == FIRST_BLOCKED_POINT % blockEvery;
            if (!blocked) applied++;
        }
        health = Math.max(0, health - applied);
    }
    public boolean heal(int amount) {
        if (amount <= 0 || health >= maxHealth) return false;
        health = Math.min(maxHealth, health + amount);
        return true;
    }
    public void flashRed()      { hitFlashTimer = HIT_FLASH_DURATION; }
    public boolean isDead()     { return health <= 0; }

    // ---------- drawing ----------
    @Override public int sortY() { return (int) Math.round(y + H); }

    @Override
    public void draw(Graphics2D g) {
        if (onBoat) drawBoat(g);
        BufferedImage sprite = getSpriteForCurrentState();
        if (mudTimer > 0) sprite = getCurrentSprite(mudFrames);
        if (hitFlashTimer > 0) {
            int frame = (int) (hitFlashTimer / HIT_FLASH_INTERVAL);
            if (frame % 2 == 0) sprite = getRedSpriteForCurrentState();
        }
        int lift = (int) Math.round(airHeight());
        if (lift > 0) {                     // shadow stays on the ground while the player flies
            int shadow = Math.max(4, 12 - lift / 3);
            g.setColor(new Color(0, 0, 0, 80));
            g.fillOval((int) Math.round(x + W / 2.0 - shadow / 2.0), (int) Math.round(y + H - 3), shadow, 3);
        }
        Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(sprite, (int) Math.round(x), (int) Math.round(y) - lift, null);
        graphics.dispose();
        if (stunTimer > 0 && lift == 0) drawDizzyStars(g);
    }

    /** Little stars circling the head while stunned. */
    private void drawDizzyStars(Graphics2D g) {
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < 3; i++) {
            double a = t * 6 + i * Math.PI * 2 / 3;
            int sx = (int) Math.round(x + W / 2.0 + Math.cos(a) * 6), sy = (int) Math.round(y - 1 + Math.sin(a) * 2);
            g.setColor(i == 0 ? new Color(255, 236, 120) : new Color(255, 250, 210));
            g.fillRect(sx, sy, 1, 1);
            g.fillRect(sx - 1, sy, 3, 1);
            g.fillRect(sx, sy - 1, 1, 3);
        }
    }

    private void drawBoat(Graphics2D g) {
        Graphics2D boat = (Graphics2D) g.create();
        boat.translate(x + W / 2.0, y + H / 2.0 + 6);
        boat.rotate(boatHeading);
        boat.setColor(new Color(0, 0, 0, 75)); boat.fillOval(-17, -4, 36, 17);
        boat.setColor(new Color(105, 62, 37));
        boat.fillPolygon(new Polygon(new int[]{-18, 12, 19, 12, -18}, new int[]{-7, -7, 0, 7, 7}, 5));
        boat.setColor(new Color(194, 132, 72));
        boat.fillPolygon(new Polygon(new int[]{-12, 10, 14, 10, -12}, new int[]{-4, -4, 0, 4, 4}, 5));
        boat.setColor(new Color(235, 204, 142)); boat.drawLine(-9, 0, 9, 0);
        boat.dispose();
    }

    private BufferedImage getSpriteForCurrentState() {
        return getCurrentSprite(frames);
    }

    private BufferedImage getRedSpriteForCurrentState() {
        return getCurrentSprite(redFrames);
    }

    private BufferedImage getCurrentSprite(BufferedImage[][] spriteFrames) {
        if (walkFrameTimer > 0) {
            int frame = (int) (walkFrameTimer / WALK_FRAME_DURATION);
            return spriteFrames[animationRow][frame];
        }

        if (animationRow == FRONT_WALK_ROW) {
            int idleFrame = (int) (idleFrameTimer / IDLE_FRAME_DURATION);
            return spriteFrames[IDLE_ROW][idleFrame];
        }

        return spriteFrames[animationRow][0];
    }
}
