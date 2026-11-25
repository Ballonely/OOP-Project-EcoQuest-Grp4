package com.ecoquest.level1;

import java.awt.Graphics2D;

/** Flat concrete driveway from a garage door down to the sidewalk. Walkable decoration. */
public class Driveway extends Prop {

    public Driveway(int px, int py, int w, int h) { super(px, py, w, h, false); }

    @Override public boolean isGroundDecal() { return true; }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        rect(g, X, Y, w, h, Palette.SIDEWALK_LINE);
        rect(g, X + 1, Y, w - 2, h, Palette.SIDEWALK);
        rect(g, X + 1, Y, 1, h, Palette.SIDEWALK_LIGHT);
        for (int yy = Y + 8; yy < Y + h; yy += 8) rect(g, X + 1, yy, w - 2, 1, Palette.SIDEWALK_LINE);
        rect(g, X + w / 2 - 3, Y + h / 2 - 1, 5, 2, 0xA59B85);   // faint oil stain
    }
}
