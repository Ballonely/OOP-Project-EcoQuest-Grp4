package com.ecoquest.level1;

import java.awt.geom.Rectangle2D;

/** Scenery: houses, trees, cars... Some block the player (solid), some only slow or decorate. */
public abstract class Prop extends Entity {
    private final boolean solid;

    protected Prop(double x, double y, int w, int h, boolean solid) {
        super(x, y, w, h);
        this.solid = solid;
    }

    /** Area that blocks the player, or null if the player can walk through. */
    public Rectangle2D.Double collisionBox() { return solid ? bounds() : null; }

    /** 1.0 = normal speed. Puddles return less. */
    public double speedFactor() { return 1.0; }

    /** Flat things (flowers, puddles) are painted under everything else. */
    public boolean isGroundDecal() { return false; }
}
