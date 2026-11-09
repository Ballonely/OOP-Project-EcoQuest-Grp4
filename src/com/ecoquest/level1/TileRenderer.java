package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.util.Random;

import static com.ecoquest.level1.Config.TILE;

/** Paints one ground tile with a deterministic pixel texture (same tile always looks the same). */
public final class TileRenderer {
    private TileRenderer() {}

    private static boolean isRoad(TileType[][] t, int r, int c) {
        return r >= 0 && c >= 0 && r < t.length && c < t[0].length && t[r][c] == TileType.ROAD;
    }

    private static void px(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(Palette.c(rgb));
        g.fillRect(x, y, w, h);
    }

    public static void draw(Graphics2D g, TileType[][] tiles, int r, int c) {
        int x = c * TILE, y = r * TILE;
        Random rn = new Random(c * 7919L + r * 104729L);
        TileType type = tiles[r][c];

        switch (type) {
            case GRASS:
            case PATH:
                grass(g, x, y, rn);
                if (type == TileType.PATH) stones(g, x, y, rn);
                break;
            case ROAD:
                px(g, x, y, TILE, TILE, Palette.ROAD);
                for (int i = 0; i < 9; i++)
                    px(g, x + rn.nextInt(TILE), y + rn.nextInt(TILE), 1 + rn.nextInt(2), 1,
                            rn.nextBoolean() ? Palette.ROAD_DARK : Palette.ROAD_LIGHT);
                if (rn.nextInt(7) == 0) {   // little crack
                    int cx = x + rn.nextInt(10), cy = y + rn.nextInt(12);
                    px(g, cx, cy, 4, 1, Palette.ROAD_DARK);
                    px(g, cx + 4, cy + 1, 3, 1, Palette.ROAD_DARK);
                }
                break;
            case SIDEWALK:
                px(g, x, y, TILE, TILE, Palette.SIDEWALK);
                px(g, x, y, TILE, 1, Palette.SIDEWALK_LIGHT);
                px(g, x, y, 1, TILE, Palette.SIDEWALK_LIGHT);
                px(g, x, y + TILE - 1, TILE, 1, Palette.SIDEWALK_LINE);
                px(g, x + TILE - 1, y, 1, TILE, Palette.SIDEWALK_LINE);
                for (int i = 0; i < 4; i++)
                    px(g, x + 1 + rn.nextInt(TILE - 2), y + 1 + rn.nextInt(TILE - 2), 1, 1, Palette.SIDEWALK_LINE);
                // dark curb on every side that touches the road
                if (isRoad(tiles, r + 1, c)) px(g, x, y + TILE - 3, TILE, 3, Palette.CURB);
                if (isRoad(tiles, r - 1, c)) px(g, x, y, TILE, 3, Palette.CURB);
                if (isRoad(tiles, r, c + 1)) px(g, x + TILE - 3, y, 3, TILE, Palette.CURB);
                if (isRoad(tiles, r, c - 1)) px(g, x, y, 3, TILE, Palette.CURB);
                break;
        }
    }

    private static void grass(Graphics2D g, int x, int y, Random rn) {
        px(g, x, y, TILE, TILE, Palette.GRASS);
        if (rn.nextInt(5) == 0) px(g, x + rn.nextInt(8), y + rn.nextInt(8), 8, 6, Palette.GRASS_DARK);
        for (int i = 0; i < 7; i++) {
            int gx = x + rn.nextInt(TILE - 2), gy = y + rn.nextInt(TILE - 3);
            int col = rn.nextBoolean() ? Palette.GRASS_DARK : Palette.GRASS_LIGHT;
            px(g, gx, gy, 1, 2, col);          // grass blade
            if (rn.nextInt(3) == 0) px(g, gx + 1, gy + 1, 1, 1, col);
        }
    }

    private static void stones(Graphics2D g, int x, int y, Random rn) {
        int[][] o = { {1, 1}, {9, 2}, {2, 9}, {10, 10} };
        for (int[] s : o) {
            int sx = x + s[0] + rn.nextInt(2), sy = y + s[1] + rn.nextInt(2);
            px(g, sx, sy, 6, 5, 0x8C7F66);
            px(g, sx, sy, 5, 4, 0xB9AC90);
            px(g, sx, sy, 5, 1, 0xCFC4AA);
        }
    }
}
