package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Base class of everything that is placed in the world and drawn (props, garbage, bins, player). */
public abstract class Entity {
    protected double x, y;
    protected int w, h;

    protected Entity(double x, double y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public abstract void draw(Graphics2D g);

    public Rectangle2D.Double bounds() { return new Rectangle2D.Double(x, y, w, h); }
    public double centerX() { return x + w / 2.0; }
    public double centerY() { return y + h / 2.0; }
    public double getX() { return x; }
    public double getY() { return y; }

    /** Entities with a larger sortY are drawn later (in front). Gives the top-down depth effect. */
    public int sortY() { return (int) Math.round(y + h); }

    /** Fill a rectangle with a 0xRRGGBB colour. */
    protected static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(Palette.c(rgb));
        g.fillRect(x, y, w, h);
    }
}
