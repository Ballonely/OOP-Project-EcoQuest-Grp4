package com.ecoquest.level1;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/** Trash Monster NPC; periodically drops litter using its supplied spawn animation. */
public class TrashMonster extends Entity {
    private static final int FRAME_SIZE = 32;
    private static final int FRAME_COLUMNS = 4;
    private static final int FRAME_ROWS = 3;
    private static final int IDLE_ROW = 0;
    private static final int SPAWN_ROW = 1;
    private static final double IDLE_FPS = 5;
    private static final double SPAWN_FPS = 6;
    private static final double TRASH_THROW_INTERVAL = 3;
    private static final double CHASE_RANGE = 120;
    private static final double ROAM_SPEED = 24;
    private static final double CHASE_SPEED = 42;

    private final BufferedImage[][] frames = new BufferedImage[FRAME_ROWS][FRAME_COLUMNS];
    private final Random random = new Random();
    private boolean spawning;
    private int garbageDropsPending;
    private int spawnFrame;
    private double animationTimer;
    private double idleTimer;
    private double throwTimer = TRASH_THROW_INTERVAL;
    private double roamTargetX;
    private double roamTargetY;
    private boolean hasRoamTarget;

    public TrashMonster(double x, double y) {
        super(x, y, FRAME_SIZE, FRAME_SIZE);

        BufferedImage sheet = loadSpriteSheet();
        if (sheet.getWidth() != FRAME_SIZE * FRAME_COLUMNS
                || sheet.getHeight() != FRAME_SIZE * FRAME_ROWS) {
            throw new IllegalStateException("Expected trash_monster_spritesheet.png to be 128x96 pixels, but found "
                    + sheet.getWidth() + "x" + sheet.getHeight());
        }

        for (int row = 0; row < FRAME_ROWS; row++) {
            for (int column = 0; column < FRAME_COLUMNS; column++) {
                frames[row][column] = sheet.getSubimage(
                        column * FRAME_SIZE, row * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);
            }
        }
    }

    private BufferedImage loadSpriteSheet() {
        File[] candidates = {
            new File(System.getProperty("user.dir"), "assets/trash_monster_spritesheet.png"),
            new File(System.getProperty("user.dir"), "bin/assets/trash_monster_spritesheet.png")
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
                throw new IllegalStateException("Could not read Trash Monster spritesheet: " + candidate, e);
            }
        }

        throw new IllegalStateException("Trash Monster spritesheet not found. Expected "
                + "assets/trash_monster_spritesheet.png under " + System.getProperty("user.dir"));
    }

    public void update(double deltaTime, Player player, Level level) {
        idleTimer += deltaTime;
        throwTimer -= deltaTime;
        move(deltaTime, player, level);

        if (spawning) {
            animationTimer += deltaTime;
            double frameDuration = 1.0 / SPAWN_FPS;
            while (animationTimer >= frameDuration && spawning) {
                animationTimer -= frameDuration;
                spawnFrame++;
                if (spawnFrame == 2) {
                    garbageDropsPending += 2;
                } else if (spawnFrame >= FRAME_COLUMNS) {
                    spawning = false;
                    spawnFrame = 0;
                }
            }
            return;
        }

        if (throwTimer <= 0) {
            spawning = true;
            spawnFrame = 0;
            animationTimer = 0;
            throwTimer += TRASH_THROW_INTERVAL;
        }
    }

    private void move(double deltaTime, Player player, Level level) {
        double dx = player.centerX() - centerX();
        double dy = player.centerY() - centerY();
        double distance = Math.hypot(dx, dy);
        double moveX;
        double moveY;

        if (distance <= CHASE_RANGE && distance > 0) {
            double step = Math.min(CHASE_SPEED * deltaTime, Math.max(0, distance - 20));
            moveX = dx / distance * step;
            moveY = dy / distance * step;
            hasRoamTarget = false;
            moveBy(moveX, moveY, level);
        } else {
            if (!hasRoamTarget && !chooseRoamTarget(level)) return;
            dx = roamTargetX - x;
            dy = roamTargetY - y;
            distance = Math.hypot(dx, dy);
            if (distance < 2) {
                hasRoamTarget = false;
                return;
            }
            double step = Math.min(ROAM_SPEED * deltaTime, distance);
            moveX = dx / distance * step;
            moveY = dy / distance * step;
            if (!moveBy(moveX, moveY, level)) hasRoamTarget = false;
        }
    }

    private boolean chooseRoamTarget(Level level) {
        for (int attempt = 0; attempt < 64; attempt++) {
            double targetX = random.nextInt(Config.WORLD_W - w + 1);
            double targetY = random.nextInt(Config.WORLD_H - h + 1);
            if (Math.hypot(targetX - x, targetY - y) < 48) continue;
            if (level.isBlocked(boundsAt(targetX, targetY))) continue;
            roamTargetX = targetX;
            roamTargetY = targetY;
            hasRoamTarget = true;
            return true;
        }
        return false;
    }

    private boolean moveBy(double moveX, double moveY, Level level) {
        boolean moved = false;
        if (!level.isBlocked(boundsAt(x + moveX, y))) {
            x += moveX;
            moved = true;
        }
        if (!level.isBlocked(boundsAt(x, y + moveY))) {
            y += moveY;
            moved = true;
        }
        return moved;
    }

    private Rectangle2D.Double boundsAt(double nextX, double nextY) {
        return new Rectangle2D.Double(nextX, nextY, w, h);
    }

    public int consumeGarbageDrops() {
        int drops = garbageDropsPending;
        garbageDropsPending = 0;
        return drops;
    }

    @Override
    public void draw(Graphics2D g) {
        BufferedImage frame = spawning
                ? frames[SPAWN_ROW][spawnFrame]
                : frames[IDLE_ROW][(int) (idleTimer * IDLE_FPS) % FRAME_COLUMNS];
        Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(frame, (int) Math.round(x), (int) Math.round(y), null);
        graphics.dispose();
    }
}
