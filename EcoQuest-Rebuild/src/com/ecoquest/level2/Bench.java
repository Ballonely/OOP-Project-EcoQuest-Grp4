package com.ecoquest.level2;

import com.ecoquest.level1.Prop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

public class Bench extends Prop {
    public Bench(int px, int py) { super(px, py, 30, 15, true); }

    @Override
    public Rectangle2D.Double collisionBox() { return new Rectangle2D.Double(x, y + 7, w, h - 7); }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(X + 1, Y + h - 1, w - 2, 2);
        rect(g, X + 3, Y, 2, h - 3, 0x3B2A1E);
        rect(g, X + w - 5, Y, 2, h - 3, 0x3B2A1E);
        for (int i = 0; i < 2; i++) {
            rect(g, X + 1, Y + i * 4, w - 2, 3, 0x7A4A24);
            rect(g, X + 1, Y + i * 4, w - 2, 1, 0xB07A44);
        }
        rect(g, X, Y + 8, w, 4, 0x8B5A2B);
        rect(g, X, Y + 8, w, 1, 0xC08850);
        rect(g, X, Y + 10, w, 1, 0x6E4220);
        rect(g, X, Y + 11, w, 1, 0x5A3418);
        rect(g, X + 2, Y + 12, 2, 3, 0x2E2A26);
        rect(g, X + w - 4, Y + 12, 2, 3, 0x2E2A26);
    }
}
