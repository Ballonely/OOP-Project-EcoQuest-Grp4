package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;

/** A colour-coded bin. Throw the carried garbage in the bin that matches its type. */
public class TrashBin extends Entity {
    private final GarbageType type;
    private boolean highlighted;

    public TrashBin(GarbageType type, int tx, int ty) {
        super(tx * Config.TILE + 2, ty * Config.TILE + 1, 12, 14);
        this.type = type;
    }

    public GarbageType getType() { return type; }
    public void setHighlighted(boolean h) { this.highlighted = h; }

    private static int shade(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 255) * f), gr = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (Math.min(255, r) << 16) | (Math.min(255, gr) << 8) | Math.min(255, b);
    }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        int c = type.getColor();
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(X, Y + h - 1, w, 2);

        rect(g, X + 1, Y + 4, w - 2, h - 4, Palette.OUTLINE);
        rect(g, X + 2, Y + 5, w - 4, h - 6, c);
        rect(g, X + 2, Y + 5, 2, h - 6, shade(c, 1.25));          // lit side
        rect(g, X + w - 4, Y + 5, 2, h - 6, shade(c, 0.7));       // shaded side
        rect(g, X, Y + 2, w, 3, Palette.OUTLINE);                  // lid
        rect(g, X + 1, Y + 2, w - 2, 2, shade(c, 0.8));
        rect(g, X + 4, Y, 4, 2, Palette.OUTLINE);                  // handle
        rect(g, X + 4, Y + 8, 4, 4, 0xF2F2F2);                     // label
        rect(g, X + 5, Y + 9, 2, 2, c);

        if (highlighted) {
            int a = 150 + (int) (90 * Math.sin(System.currentTimeMillis() / 120.0));
            g.setColor(new Color(255, 255, 255, Math.max(60, Math.min(255, a))));
            g.drawRect(X - 1, Y - 1, w + 1, h + 1);
        }
    }
}
