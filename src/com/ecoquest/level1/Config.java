package com.ecoquest.level1;

/** Global constants. The world is drawn at low resolution, then scaled up for the pixel-art look. */
public final class Config {
    private Config() {}

    public static final int TILE = 16;              // size of one tile in "pixel-art" pixels
    public static final int COLS = 32;              // map width  in tiles
    public static final int ROWS = 18;              // map height in tiles
    public static final int WORLD_W = COLS * TILE;  // 512
    public static final int WORLD_H = ROWS * TILE;  // 288
    public static final int SCALE = 2;              // window = 1024 x 576
    public static final int FPS = 60;
}
