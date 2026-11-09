package com.ecoquest.level1;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
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

    private double speed = 62;                 // pixels / second (upgradeable later)
    private int capacity = 5;                  // bag size       (upgradeable later)
    private int maxHealth = 5, health = 5;
    private int money, score;

    private final List<Garbage> bag = new ArrayList<>();
    private int selected;

    private final BufferedImage[][] frames = new BufferedImage[FRAME_ROWS][FRAME_COLUMNS];
    private final BufferedImage[][] redFrames = new BufferedImage[FRAME_ROWS][FRAME_COLUMNS];
    private int animationRow = FRONT_WALK_ROW;
    private double walkFrameTimer;
    private double idleFrameTimer;
    private double hitFlashTimer;

    public Player(double x, double y) {
        super(x, y, W, H);
        BufferedImage sheet = loadSpriteSheet();
        if (sheet.getWidth() != FRAME_SIZE * FRAME_COLUMNS
                || sheet.getHeight() != FRAME_SIZE * FRAME_ROWS) {
            throw new IllegalStateException("Expected spritesheet.png to be 64x80 pixels, but found "
                    + sheet.getWidth() + "x" + sheet.getHeight());
        }

        for (int row = 0; row < FRAME_ROWS; row++) {
            for (int column = 0; column < FRAME_COLUMNS; column++) {
                frames[row][column] = sheet.getSubimage(
                        column * FRAME_SIZE, row * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);
                redFrames[row][column] = tintRed(frames[row][column]);
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

    private BufferedImage loadSpriteSheet() {
        File[] candidates = {
            new File(System.getProperty("user.dir"), "assets/spritesheet.png"),
            new File(System.getProperty("user.dir"), "bin/assets/spritesheet.png")
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

        throw new IllegalStateException("Player spritesheet not found. Expected assets/spritesheet.png "
                + "under " + System.getProperty("user.dir"));
    }

    // ---------- movement ----------
    private Rectangle2D.Double feet(double nx, double ny) {
        return new Rectangle2D.Double(nx + 3, ny + H - 5, W - 6, 5);
    }

    public void update(double dt, int dx, int dy, Level level) {
        hitFlashTimer = Math.max(0, hitFlashTimer - dt);
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
        double v = speed * level.speedFactorAt(feet(x, y)) * dt;
        double mx = dx / len * v, my = dy / len * v;

        if (!level.isBlocked(feet(x + mx, y))) x += mx;
        if (!level.isBlocked(feet(x, y + my))) y += my;

    }

    // ---------- bag ----------
    public boolean bagFull()    { return bag.size() >= capacity; }
    public boolean bagEmpty()   { return bag.isEmpty(); }
    public int getCapacity()    { return capacity; }
    public List<Garbage> getBag() { return bag; }
    public int getSelectedIndex() { return selected; }

    public void pickUp(Garbage item) { bag.add(item); selected = bag.size() - 1; money += 2; }

    public GarbageType selectedItem() { return bag.isEmpty() ? null : bag.get(selected).getType(); }

    public void cycleSelected() { if (!bag.isEmpty()) selected = (selected + 1) % bag.size(); }

    public GarbageType removeSelected() {
        GarbageType t = bag.remove(selected).getType();
        selected = bag.isEmpty() ? 0 : Math.min(selected, bag.size() - 1);
        return t;
    }

    // ---------- stats ----------
    public int getMoney()     { return money; }
    public int getScore()     { return score; }
    public int getHealth()    { return health; }
    public int getMaxHealth() { return maxHealth; }
    public void addMoney(int m) { money += m; }
    public void addScore(int s) { score = Math.max(0, score + s); }
    public void hurt(int dmg)   { health = Math.max(0, health - dmg); }
    public void flashRed()      { hitFlashTimer = HIT_FLASH_DURATION; }
    public boolean isDead()     { return health <= 0; }

    // ---------- drawing ----------
    @Override public int sortY() { return (int) Math.round(y + H); }

    @Override
    public void draw(Graphics2D g) {
        BufferedImage sprite = getSpriteForCurrentState();
        if (hitFlashTimer > 0) {
            int frame = (int) (hitFlashTimer / HIT_FLASH_INTERVAL);
            if (frame % 2 == 0) sprite = getRedSpriteForCurrentState();
        }
        Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(sprite, (int) Math.round(x), (int) Math.round(y), null);
        graphics.dispose();
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
