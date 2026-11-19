package com.ecoquest.level1;

import java.awt.Graphics2D;

/** A suburban house (roof on top, wall + door + windows below). Fully solid. */
public class House extends Prop {

    public enum Style {
        SLATE(0x4B5568, 0x3A4252, 0x687489),
        BROWN(0x80502D, 0x5F3A21, 0x9C6A3C);

        final int roof, dark, light;
        Style(int roof, int dark, int light) { this.roof = roof; this.dark = dark; this.light = light; }
    }

    public static final int HEIGHT = 56;
    private static final int ROOF_H = 34;
    private static final int GARAGE_W = 22;

    private final Style style;
    private final boolean garage;

    /** @param tx,ty top-left tile (ty may be negative to peek in from the top)   @param tilesW width in tiles */
    public House(int tx, int ty, int tilesW, Style style, boolean garage) {
        super(tx * Config.TILE, ty * Config.TILE, tilesW * Config.TILE, HEIGHT, true);
        this.style = style;
        this.garage = garage;
    }

    private int livingW() { return w - 4 - (garage ? GARAGE_W : 0); }
    private int doorX()   { return (int) x + 2 + livingW() / 2 - 3; }

    /** Tile column of the front door (used to lay a stone path to the street). */
    public int doorTileCol() { return doorX() / Config.TILE; }
    public int baseY()       { return (int) y + h; }
    public int doorPixelX()  { return doorX(); }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y, W = w;
        int O = Palette.OUTLINE;

        // ---- roof ----
        rect(g, X, Y, W, ROOF_H, O);
        rect(g, X + 1, Y + 1, W - 2, ROOF_H - 2, style.roof);
        for (int yy = Y + 6; yy < Y + ROOF_H - 2; yy += 4) rect(g, X + 1, yy, W - 2, 1, style.dark);
        for (int row = 0; row < 7; row++)
            for (int xx = X + 3 + (row % 2) * 4; xx < X + W - 2; xx += 8)
                rect(g, xx, Y + 2 + row * 4, 1, 3, style.dark);
        rect(g, X + 1, Y + 1, W - 2, 2, style.light);          // lit top edge
        rect(g, X + 1, Y + ROOF_H - 3, W - 2, 2, style.dark);  // eave shadow

        // ---- dormer (little gable on the roof) ----
        int dx = X + (garage ? 10 : W / 2 - 7), dy = Y + 14;
        for (int k = 0; k < 6; k++) rect(g, dx + 6 - (1 + k), dy - 6 + k, 2 + 2 * k, 1, style.light);
        rect(g, dx, dy, 14, 9, O);
        rect(g, dx + 1, dy + 1, 12, 7, 0xC9B48A);
        rect(g, dx + 4, dy + 2, 6, 5, 0x6AA0C8);
        rect(g, dx + 6, dy + 2, 1, 5, 0xE8E2D0);

        // ---- chimney ----
        int cx = X + W - (garage ? 34 : 14);
        rect(g, cx, Y - 5, 8, 11, O);
        rect(g, cx + 1, Y - 4, 6, 9, 0x8A4B3C);
        rect(g, cx + 1, Y - 4, 6, 2, 0x5E5E66);

        // ---- wall ----
        int wy = Y + ROOF_H, wh = h - ROOF_H;
        rect(g, X + 2, wy, W - 4, wh, O);
        rect(g, X + 3, wy, W - 6, wh - 1, 0xCDB98F);
        rect(g, X + 3, wy, W - 6, 3, 0x9E8B66);                 // shadow under the eave
        rect(g, X + 3, Y + h - 4, W - 6, 3, 0x8A7A5C);          // foundation

        // windows
        int lw = livingW();
        window(g, X + 7, wy + 6);
        if (lw >= 40) window(g, X + 2 + lw - 13, wy + 6);

        // door + step
        int d = doorX();
        rect(g, d - 1, wy + 5, 8, 15, O);
        rect(g, d, wy + 6, 6, 14, 0x7A4A28);
        rect(g, d + 1, wy + 7, 4, 5, 0x965F36);
        rect(g, d + 4, wy + 13, 1, 1, 0xF0D060);
        rect(g, d - 2, Y + h - 2, 10, 2, 0xD8D0BA);

        // garage
        if (garage) {
            int gx = X + W - 2 - GARAGE_W + 3;
            rect(g, gx - 1, wy + 5, GARAGE_W - 4, 15, O);
            rect(g, gx, wy + 6, GARAGE_W - 6, 14, 0x8E939C);
            for (int i = 0; i < 4; i++) rect(g, gx, wy + 8 + i * 3, GARAGE_W - 6, 1, 0x6E737C);
        }
    }

    private void window(Graphics2D g, int wx, int wy) {
        rect(g, wx, wy, 9, 9, Palette.OUTLINE);
        rect(g, wx + 1, wy + 1, 7, 7, 0x6AA0C8);
        rect(g, wx + 1, wy + 1, 7, 2, 0x9CC6E0);
        rect(g, wx + 4, wy + 1, 1, 7, 0xE8E2D0);
        rect(g, wx + 1, wy + 4, 7, 1, 0xE8E2D0);
        rect(g, wx - 1, wy + 9, 11, 1, 0x7A4A28);               // sill
    }
}
