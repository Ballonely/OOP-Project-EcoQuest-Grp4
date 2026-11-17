package com.ecoquest.level1;

import java.awt.Graphics2D;

final class ParkBench extends Prop {
    ParkBench(int x, int y) { super(x, y, 34, 18, true); }
    @Override public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        g.setColor(new java.awt.Color(0, 0, 0, 55)); g.fillOval(X + 2, Y + 13, 30, 5);
        rect(g, X + 2, Y + 3, 30, 5, 0x6D4026);
        rect(g, X + 3, Y + 2, 28, 3, 0xA76935);
        rect(g, X + 4, Y + 9, 27, 4, 0x80502D);
        rect(g, X + 6, Y + 11, 3, 6, 0x493326);
        rect(g, X + 27, Y + 11, 3, 6, 0x493326);
    }
}
