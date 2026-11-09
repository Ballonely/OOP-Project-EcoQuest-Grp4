package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;

/** A piece of litter lying on the ground. Each category has two little sprites (variants). */
public class Garbage extends Entity {
    public static final int SIZE = 8;

    private final GarbageType type;
    private final int variant;
    private boolean highlighted;

    public Garbage(GarbageType type, int variant, double x, double y) {
        super(x, y, SIZE, SIZE);
        this.type = type;
        this.variant = variant % 2;
    }

    public GarbageType getType() { return type; }
    public void setHighlighted(boolean h) { this.highlighted = h; }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(X + 1, Y + 7, 6, 1);

        switch (type) {
            case BIODEGRADABLE:
                if (variant == 0) {                       // banana peel
                    rect(g, X + 1, Y + 3, 6, 2, 0xE7CF4A);
                    rect(g, X + 2, Y + 2, 3, 1, 0xE7CF4A);
                    rect(g, X + 5, Y + 4, 2, 2, 0xC9B232);
                    rect(g, X, Y + 3, 1, 1, 0x6B4A1E);
                } else {                                   // apple core
                    rect(g, X + 2, Y + 1, 4, 2, 0xB33A32);
                    rect(g, X + 3, Y + 3, 2, 2, 0xE8E3B0);
                    rect(g, X + 2, Y + 5, 4, 2, 0xB33A32);
                    rect(g, X + 4, Y, 1, 1, 0x5B3A1E);
                }
                break;
            case RECYCLABLE:
                if (variant == 0) {                       // can
                    rect(g, X + 2, Y + 1, 4, 6, 0xCF3B30);
                    rect(g, X + 2, Y + 1, 4, 1, 0xDADADA);
                    rect(g, X + 3, Y + 3, 2, 2, 0xF2F2F2);
                } else {                                   // plastic bottle
                    rect(g, X + 1, Y + 3, 5, 3, 0x5AA7E0);
                    rect(g, X + 6, Y + 4, 2, 1, 0x2F6FA8);
                    rect(g, X + 2, Y + 3, 2, 1, 0xBFE3FA);
                }
                break;
            case NON_RECYCLABLE:
                if (variant == 0) {                       // crumpled wrapper
                    rect(g, X + 1, Y + 2, 6, 4, 0xE9953A);
                    rect(g, X + 2, Y + 3, 2, 1, 0xF6C27A);
                    rect(g, X + 5, Y + 4, 2, 1, 0xB56A1E);
                } else {                                   // chip bag
                    rect(g, X + 2, Y + 1, 4, 6, 0xD9A441);
                    rect(g, X + 3, Y + 3, 2, 2, 0xB33A32);
                    rect(g, X + 2, Y + 1, 4, 1, 0x9A7428);
                }
                break;
            case HAZARDOUS:
                if (variant == 0) {                       // battery
                    rect(g, X + 1, Y + 2, 6, 4, 0x3A3A3A);
                    rect(g, X + 1, Y + 2, 2, 4, 0xE6C229);
                    rect(g, X + 7, Y + 3, 1, 2, 0xAAAAAA);
                } else {                                   // toxic drum
                    rect(g, X + 2, Y + 1, 4, 6, 0x6FBF3A);
                    rect(g, X + 2, Y + 3, 4, 1, 0x222222);
                    rect(g, X + 2, Y + 5, 4, 1, 0x222222);
                }
                break;
        }

        if (highlighted) {   // pulsing white box = "you can pick this up"
            int a = 150 + (int) (90 * Math.sin(System.currentTimeMillis() / 120.0));
            g.setColor(new Color(255, 255, 255, Math.max(60, Math.min(255, a))));
            g.drawRect(X - 1, Y - 1, SIZE + 1, SIZE + 1);
        }
    }
}
