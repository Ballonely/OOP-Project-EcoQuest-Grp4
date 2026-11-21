package com.ecoquest.level2;

import com.ecoquest.level1.Prop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

public class ParkLamp extends Prop {
    public ParkLamp(int px, int py) { super(px, py, 8, 34, true); }

    @Override
    public Rectangle2D.Double collisionBox() { return new Rectangle2D.Double(x + 2, y + h - 4, 4, 4); }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(X - 1, Y + h - 3, 10, 4);
        g.setColor(new Color(255, 227, 138, 45));
        g.fillOval(X - 5, Y - 3, 18, 16);
        rect(g, X + 1, Y + h - 3, 6, 3, 0x22252B);
        rect(g, X + 3, Y + 8, 2, h - 10, 0x2E3138);
        rect(g, X + 3, Y + 8, 1, h - 10, 0x4A4F5A);
        rect(g, X, Y + 2, 8, 7, 0x22252B);
        rect(g, X + 1, Y + 3, 6, 5, 0xFFE38A);
        rect(g, X + 2, Y + 3, 2, 2, 0xFFF6CF);
        rect(g, X + 1, Y, 6, 2, 0x22252B);
        rect(g, X + 3, Y - 1, 2, 1, 0x22252B);
    }
}
