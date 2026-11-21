package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** A round tree. Only the trunk blocks the player, so you can walk "behind" the leaves. */
public class Tree extends Prop {
    private final int shade;

    public Tree(int px, int py) {
        super(px, py, 24, 28, true);
        this.shade = Math.abs(px * 31 + py * 17) % 3;
    }

    @Override
    public Rectangle2D.Double collisionBox() { return new Rectangle2D.Double(x + 9, y + h - 7, 6, 6); }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        g.setColor(new Color(0, 0, 0, 55));
        g.fillOval(X + 2, Y + h - 8, 20, 7);
        rect(g, X + 10, Y + h - 11, 4, 10, 0x5B3A1E);

        int[][] pal = { {0x1C4A22, 0x2F7A35, 0x3D9A3F, 0x5CBA4C},
                        {0x1B4426, 0x2B7536, 0x389540, 0x58B554},
                        {0x214F20, 0x35802F, 0x45A046, 0x68C050} };
        int[] p = pal[shade];
        g.setColor(Palette.c(p[0])); g.fillOval(X, Y, 24, 22);
        g.setColor(Palette.c(p[1])); g.fillOval(X + 1, Y + 1, 22, 20);
        g.setColor(Palette.c(p[2])); g.fillOval(X + 2, Y + 1, 17, 15);
        g.setColor(Palette.c(p[3])); g.fillOval(X + 5, Y + 3, 9, 6);
        rect(g, X + 15, Y + 14, 3, 2, p[0]);
        rect(g, X + 6, Y + 12, 2, 2, p[1]);
    }
}
