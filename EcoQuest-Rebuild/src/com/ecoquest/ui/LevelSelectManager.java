package com.ecoquest.ui;

import com.ecoquest.core.InputHandler;
import com.ecoquest.util.Constants;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** Displays the illustrated overworld and starts the region selected by the player. */
public class LevelSelectManager {

    public interface LevelSelectListener {
        void onBackToMenu();
        void onStartLevel(String levelName);
    }

    private static final String[] LEVEL_NAMES = {
        "Residential", "Public Park", "Town Market", "Coastal Beach", "City Center"
    };

    private static final Rectangle[] REGION_BOUNDS = {
        new Rectangle(76, 111, 308, 232),
        new Rectangle(475, 116, 268, 212),
        new Rectangle(206, 347, 337, 220),
        new Rectangle(606, 401, 528, 172),
        new Rectangle(796, 106, 348, 273)
    };

    private static final Color MAP_BACKGROUND = new Color(0xEFDCB4);
    private static final Color SELECTED_COLOR = new Color(255, 201, 49);
    private static final Color HOVER_COLOR = Color.WHITE;

    private final LevelSelectListener listener;
    private final BufferedImage mapImage;
    private int selectedIndex;
    private int hoveredIndex = -1;
    private boolean fullscreenMode;
    private final int[] progress = new int[LEVEL_NAMES.length];
    private Rectangle displayedMap = new Rectangle();

    public LevelSelectManager(LevelSelectListener listener) {
        this.listener = listener;
        this.mapImage = loadMapImage();
    }

    public void handleInput(InputHandler input, int width, int height, boolean fullscreen) {
        fullscreenMode = fullscreen;
        if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
            listener.onBackToMenu();
            return;
        }

        if (input.isKeyJustPressed(KeyEvent.VK_1) || input.isKeyJustPressed(KeyEvent.VK_2)
                || input.isKeyJustPressed(KeyEvent.VK_3) || input.isKeyJustPressed(KeyEvent.VK_4)
                || input.isKeyJustPressed(KeyEvent.VK_5)) {
            selectedIndex = input.isKeyJustPressed(KeyEvent.VK_1) ? 0 : input.isKeyJustPressed(KeyEvent.VK_2) ? 1
                    : input.isKeyJustPressed(KeyEvent.VK_3) ? 2 : input.isKeyJustPressed(KeyEvent.VK_4) ? 3 : 4;
            startSelectedLevel();
            return;
        } else if (input.isKeyJustPressed(KeyEvent.VK_LEFT) || input.isKeyJustPressed(KeyEvent.VK_UP)) {
            selectedIndex = (selectedIndex + LEVEL_NAMES.length - 1) % LEVEL_NAMES.length;
        } else if (input.isKeyJustPressed(KeyEvent.VK_RIGHT)
                || input.isKeyJustPressed(KeyEvent.VK_DOWN)) {
            selectedIndex = (selectedIndex + 1) % LEVEL_NAMES.length;
        } else if (input.isKeyJustPressed(KeyEvent.VK_ENTER)
                || input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            startSelectedLevel();
            return;
        }

        hoveredIndex = regionAt(input.getMouseX(), input.getMouseY(), width, height);
        Point click = input.consumeMouseClick();
        int clickedIndex = click == null ? -1 : regionAt(click.x, click.y, width, height);
        if (clickedIndex >= 0) {
            selectedIndex = clickedIndex;
            startSelectedLevel();
        }
    }

    public void render(Graphics2D g, int width, int height) { render(g, width, height, false); }

    public void render(Graphics2D g, int width, int height, boolean fullscreen) {
        fullscreenMode = fullscreen;
        g.setColor(MAP_BACKGROUND);
        g.fillRect(0, 0, width, height);

        displayedMap = mapBounds(width, height);
        g.drawImage(mapImage, displayedMap.x, displayedMap.y,
                displayedMap.width, displayedMap.height, null);

        drawRegionHighlight(g, selectedIndex, SELECTED_COLOR, 4);
        if (hoveredIndex >= 0 && hoveredIndex != selectedIndex) {
            drawRegionHighlight(g, hoveredIndex, HOVER_COLOR, 3);
        }
        for (int i = 0; i < REGION_BOUNDS.length; i++) drawProgress(g, scaleRegion(REGION_BOUNDS[i]), progress[i]);

        if (hoveredIndex >= 0) {
            String tip = LEVEL_NAMES[hoveredIndex] + "  |  " + progress[hoveredIndex] + "% clean  |  click to enter";
            double scale = mapScale();
            g.setFont(new Font("Monospaced", Font.BOLD, Math.max(10, (int) Math.round(12 * scale))));
            int tw = g.getFontMetrics().stringWidth(tip) + 24;
            int boxHeight = (int) Math.round(28 * scale), bottomGap = (int) Math.round(62 * scale);
            g.setColor(new Color(10, 15, 20, 215)); g.fillRoundRect((width - tw) / 2, height - bottomGap, tw, boxHeight, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(tip, (width - g.getFontMetrics().stringWidth(tip)) / 2,
                    height - (int) Math.round(43 * scale));
        }

        double scale = mapScale();
        g.setColor(new Color(72, 50, 27));
        g.setFont(new Font("Monospaced", Font.BOLD, Math.max(10, (int) Math.round(13 * scale))));
        String controls = "CLICK OR HOVER A REGION   |   1-5 OR ARROWS + ENTER   |   ESC SETTINGS";
        int textX = (width - g.getFontMetrics().stringWidth(controls)) / 2;
        g.drawString(controls, textX, height - (int) Math.round(19 * scale));
    }

    public void setProgress(int index, int percent) { progress[index] = Math.max(0, Math.min(100, percent)); }

    private void drawProgress(Graphics2D g, Rectangle region, int percent) {
        double scale = mapScale();
        int w = Math.min((int) Math.round(116 * scale), Math.max((int) Math.round(78 * scale), region.width / 2));
        int barHeight = Math.max(4, (int) Math.round(6 * scale));
        int x = region.x + (region.width - w) / 2, y = region.y + region.height - (int) Math.round(22 * scale);
        g.setColor(new Color(10, 15, 20, 205));
        g.fillRoundRect(x - (int) Math.round(5 * scale), y - (int) Math.round(17 * scale),
                w + (int) Math.round(10 * scale), (int) Math.round(27 * scale), 6, 6);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, Math.max(8, (int) Math.round(10 * scale))));
        g.drawString(percent + "% CLEAN", x, y - (int) Math.round(7 * scale));
        g.setColor(Color.BLACK); g.fillRect(x, y, w, barHeight);
        g.setColor(progressColor(percent)); g.fillRect(x, y, (int) (w * percent / 100.0), barHeight);
    }

    private Color progressColor(int percent) {
        if (percent == 0) return Color.BLACK;
        float p = Math.max(0, Math.min(100, percent)) / 100.0f;
        return new Color(Color.HSBtoRGB(p * 0.32f, 0.86f, 0.32f + p * 0.68f));
    }

    private void drawRegionHighlight(Graphics2D g, int index, Color color, int strokeWidth) {
        Rectangle region = scaleRegion(REGION_BOUNDS[index]);
        g.setColor(color);
        g.setStroke(new BasicStroke(strokeWidth));
        g.drawRect(region.x, region.y, region.width, region.height);
    }

    private int regionAt(int x, int y, int width, int height) {
        Rectangle bounds = mapBounds(width, height);
        if (!bounds.contains(x, y)) return -1;

        double scaleX = bounds.getWidth() / mapImage.getWidth();
        double scaleY = bounds.getHeight() / mapImage.getHeight();
        int imageX = (int) ((x - bounds.x) / scaleX);
        int imageY = (int) ((y - bounds.y) / scaleY);
        for (int i = 0; i < REGION_BOUNDS.length; i++) {
            if (REGION_BOUNDS[i].contains(imageX, imageY)) return i;
        }
        return -1;
    }

    private Rectangle scaleRegion(Rectangle region) {
        double scaleX = displayedMap.getWidth() / (double) mapImage.getWidth();
        double scaleY = displayedMap.getHeight() / (double) mapImage.getHeight();
        return new Rectangle(
                displayedMap.x + (int) Math.round(region.x * scaleX),
                displayedMap.y + (int) Math.round(region.y * scaleY),
                (int) Math.round(region.width * scaleX),
                (int) Math.round(region.height * scaleY));
    }

    private Rectangle mapBounds(int width, int height) {
        boolean largeViewport = fullscreenMode || width > Constants.WINDOW_WIDTH + 48
                || height > Constants.WINDOW_HEIGHT + 48;
        double scale = Math.min(largeViewport ? 1.5 : 1.0, Math.min((double) width / mapImage.getWidth(),
                (double) (height - 72) / mapImage.getHeight()));
        int imageWidth = (int) Math.round(mapImage.getWidth() * scale);
        int imageHeight = (int) Math.round(mapImage.getHeight() * scale);
        return new Rectangle((width - imageWidth) / 2, 0, imageWidth, imageHeight);
    }

    private double mapScale() {
        return mapImage == null || mapImage.getWidth() == 0 ? 1.0
                : displayedMap.getWidth() / (double) mapImage.getWidth();
    }

    private void startSelectedLevel() {
        listener.onStartLevel(LEVEL_NAMES[selectedIndex]);
    }

    private BufferedImage loadMapImage() {
        try (InputStream resource = getClass().getResourceAsStream("/assets/level_select_map.png")) {
            if (resource != null) {
                BufferedImage image = ImageIO.read(resource);
                if (image != null) return image;
                throw new IllegalStateException("Unsupported level selection map image format.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the level selection map image.", e);
        }

        File[] candidates = {
            new File("assets/level_select_map.png"),
            new File("bin/assets/level_select_map.png")
        };
        for (File candidate : candidates) {
            if (!candidate.isFile()) continue;
            try {
                BufferedImage image = ImageIO.read(candidate);
                if (image != null) return image;
                throw new IllegalStateException("Unsupported image format: " + candidate);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read level selection map: " + candidate, e);
            }
        }

        throw new IllegalStateException("Level selection map not found. Expected "
                + "assets/level_select_map.png under " + System.getProperty("user.dir"));
    }
}
