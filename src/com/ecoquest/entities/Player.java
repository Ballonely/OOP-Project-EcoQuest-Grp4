package com.ecoquest.entities;

import com.ecoquest.core.InputHandler;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** The player-controlled eco-hero: movement, health, score and cleanliness progress. */
public class Player extends GameObject {

    private static final double SPEED = 260; // pixels / second
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

    private final InputHandler input;
    private final int worldWidth;
    private final int worldHeight;
    private final BufferedImage[][] frames = new BufferedImage[FRAME_ROWS][FRAME_COLUMNS];

    private int health = 100;
    private final int maxHealth = 100;
    private int score = 0;
    private int cleanlinessLevel = 35; // percent, mirrors the HUD bar in the reference art
    private int hurtCooldownMs = 0;
    private int animationRow = FRONT_WALK_ROW;
    private double walkFrameTimer = 0;
    private double idleFrameTimer = 0;

    public Player(double x, double y, InputHandler input, int worldWidth, int worldHeight) {
        super(x, y, 56, 56);
        this.input = input;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;

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
            }
        }
    }

    private BufferedImage loadSpriteSheet() {
        File[] candidates = {
            new File(System.getProperty("user.dir"), "assets/spritesheet.png"),
            new File(System.getProperty("user.dir"), "bin/assets/spritesheet.png")
        };

        for (File candidate : candidates) {
            if (candidate.isFile()) {
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
        }

        throw new IllegalStateException("Player spritesheet not found. Expected assets/spritesheet.png "
                + "under " + System.getProperty("user.dir"));
    }

    @Override
    public void update(double deltaTime) {
        velX = 0;
        velY = 0;
        if (input.isKeyDown(KeyEvent.VK_LEFT) || input.isKeyDown(KeyEvent.VK_A)) velX -= SPEED;
        if (input.isKeyDown(KeyEvent.VK_RIGHT) || input.isKeyDown(KeyEvent.VK_D)) velX += SPEED;
        if (input.isKeyDown(KeyEvent.VK_UP) || input.isKeyDown(KeyEvent.VK_W)) velY -= SPEED;
        if (input.isKeyDown(KeyEvent.VK_DOWN) || input.isKeyDown(KeyEvent.VK_S)) velY += SPEED;

        if (velX != 0 || velY != 0) {
            if (Math.abs(velX) > Math.abs(velY)) {
                animationRow = velX < 0 ? LEFT_WALK_ROW : RIGHT_WALK_ROW;
            } else {
                animationRow = velY < 0 ? BACK_WALK_ROW : FRONT_WALK_ROW;
            }
            walkFrameTimer = (walkFrameTimer + deltaTime) % (WALK_FRAME_DURATION * FRAME_COLUMNS);
            idleFrameTimer = 0;
        } else {
            walkFrameTimer = 0;
            idleFrameTimer = (idleFrameTimer + deltaTime) % (IDLE_FRAME_DURATION * 2);
        }

        x += velX * deltaTime;
        y += velY * deltaTime;

        x = Math.max(0, Math.min(worldWidth - width, x));
        y = Math.max(0, Math.min(worldHeight - height, y));

        if (hurtCooldownMs > 0) {
            hurtCooldownMs -= (int) (deltaTime * 1000);
        }
    }

    @Override
    public void render(Graphics2D g) {
        Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(getSpriteForCurrentState(), (int) x, (int) y, width, height, null);

        if (hurtCooldownMs > 0 && (hurtCooldownMs / 100) % 2 == 0) {
            graphics.setComposite(AlphaComposite.SrcOver.derive(0.45f));
            graphics.setColor(Color.WHITE);
            graphics.fillRect((int) x, (int) y, width, height);
        }

        graphics.dispose();
    }

    private BufferedImage getSpriteForCurrentState() {
        if (velX != 0 || velY != 0) {
            int frame = (int) (walkFrameTimer / WALK_FRAME_DURATION);
            return frames[animationRow][frame];
        }

        if (animationRow == FRONT_WALK_ROW) {
            int idleFrame = (int) (idleFrameTimer / IDLE_FRAME_DURATION);
            return frames[IDLE_ROW][idleFrame];
        }

        return frames[animationRow][0];
    }

    public void collect(int points, int cleanlinessGain) {
        score += points;
        cleanlinessLevel = Math.min(100, cleanlinessLevel + cleanlinessGain);
    }

    public void damage(int amount) {
        if (hurtCooldownMs <= 0) {
            health = Math.max(0, health - amount);
            hurtCooldownMs = 900;
        }
    }

    public boolean isDead() { return health <= 0; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public int getScore() { return score; }
    public int getCleanlinessLevel() { return cleanlinessLevel; }
}
