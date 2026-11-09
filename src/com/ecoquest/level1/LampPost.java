package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Street lamp. Thin solid base. */
public class LampPost extends Prop {
    public LampPost(int px, int py) { super(px, py, 6, 16, true); }

    @Override
    public Rectangle2D.Double collisionBox() { return new Rectangle2D.Double(x + 2, y + h - 4, 3, 3); }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        rect(g, X + 2, Y + 3, 2, 12, 0x2E3138);
        rect(g, X + 1, Y + 14, 4, 2, 0x2E3138);
        rect(g, X, Y, 6, 4, 0x2E3138);
        rect(g, X + 1, Y + 1, 4, 2, 0xFFE58A);
    }
}
