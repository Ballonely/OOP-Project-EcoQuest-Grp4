package com.ecoquest.ui;

import com.ecoquest.core.InputHandler;

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
    private Rectangle displayedMap = new Rectangle();

    public LevelSelectManager(LevelSelectListener listener) {
        this.listener = listener;
        this.mapImage = loadMapImage();
    }

    public void handleInput(InputHandler input, int width, int height) {
        if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
            listener.onBackToMenu();
            return;
        }

        if (input.isKeyJustPressed(KeyEvent.VK_LEFT) || input.isKeyJustPressed(KeyEvent.VK_UP)) {
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

    public void render(Graphics2D g, int width, int height) {
        g.setColor(MAP_BACKGROUND);
        g.fillRect(0, 0, width, height);

        displayedMap = mapBounds(width, height);
        g.drawImage(mapImage, displayedMap.x, displayedMap.y,
                displayedMap.width, displayedMap.height, null);

        drawRegionHighlight(g, selectedIndex, SELECTED_COLOR, 4);
        if (hoveredIndex >= 0 && hoveredIndex != selectedIndex) {
            drawRegionHighlight(g, hoveredIndex, HOVER_COLOR, 3);
        }

        g.setColor(new Color(72, 50, 27));
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        String controls = "CLICK A REGION TO PLAY   |   ARROWS + ENTER TO SELECT   |   ESC TO MENU";
        int textX = (width - g.getFontMetrics().stringWidth(controls)) / 2;
        g.drawString(controls, textX, height - 19);
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
        double scale = Math.min((double) width / mapImage.getWidth(),
                (double) (height - 72) / mapImage.getHeight());
        int imageWidth = (int) Math.round(mapImage.getWidth() * scale);
        int imageHeight = (int) Math.round(mapImage.getHeight() * scale);
        return new Rectangle((width - imageWidth) / 2, 0, imageWidth, imageHeight);
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
