package com.ecoquest.level1;

import java.awt.Graphics2D;

/** Small decorative mailbox. */
public class Mailbox extends Prop {
    private final int color;

    public Mailbox(int px, int py, int color) { super(px, py, 6, 10, false); this.color = color; }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        rect(g, X + 2, Y + 4, 2, 6, 0x5B3A1E);
        rect(g, X, Y, 6, 5, Palette.OUTLINE);
        rect(g, X + 1, Y + 1, 4, 3, color);
        rect(g, X + 5, Y - 1, 1, 3, 0xE8D040);
    }
}
