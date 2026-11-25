package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Part of a painted map (a tree crown, palm fronds...) re-drawn in the depth-sorted pass so it
 * covers the player when they walk behind it. It never blocks movement; the trunk has its own
 * small collision box. {@link Level#draw} makes it see-through while it overlaps the player.
 */
public final class SceneryOverlay extends Prop {
    private final BufferedImage image;
    private final int baseY;

    /**
     * @param image  the cut-out art (transparent outside the shape), at world scale
     * @param baseY  world y of the object's foot (e.g. the bottom of the trunk), used for depth
     */
    public SceneryOverlay(BufferedImage image, double x, double y, int baseY) {
        super(x, y, image.getWidth(), image.getHeight(), false);
        this.image = image;
        this.baseY = baseY;
    }

    @Override public Rectangle2D.Double collisionBox() { return null; }
    @Override public int sortY() { return baseY; }
    @Override public void draw(Graphics2D g) { g.drawImage(image, (int) Math.round(x), (int) Math.round(y), null); }
}
