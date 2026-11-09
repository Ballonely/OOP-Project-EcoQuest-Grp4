package com.ecoquest.level1;

import java.awt.Graphics2D;

/** A garden hedge or a wooden fence segment, {@code tiles} long. Solid. */
public class Hedge extends Prop {
    private final boolean wooden;

    public Hedge(int tx, int ty, int tiles, boolean wooden) {
        super(tx * Config.TILE, ty * Config.TILE + (wooden ? 4 : 5), tiles * Config.TILE, wooden ? 10 : 9, true);
        this.wooden = wooden;
    }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        if (wooden) {
            rect(g, X, Y + 3, w, 2, 0x4A2E17);
            rect(g, X, Y + 7, w, 2, 0x4A2E17);
            for (int i = 0; i < w; i += 4) {
                rect(g, X + i, Y, 3, h, 0x6F4825);
                rect(g, X + i, Y, 3, 1, 0x8C6038);
            }
        } else {
            rect(g, X, Y, w, h, 0x1F5A27);
            rect(g, X, Y, w, 6, 0x2F7D33);
            rect(g, X, Y, w, 2, 0x4A9E44);
            for (int i = 2; i < w; i += 5) {
                rect(g, X + i, Y + 2 + (i % 3), 2, 1, 0x5DB550);
                rect(g, X + i + 2, Y + 5, 1, 1, 0x1A4A20);
            }
        }
    }
}
