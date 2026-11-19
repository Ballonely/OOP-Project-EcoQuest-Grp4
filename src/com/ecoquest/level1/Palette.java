package com.ecoquest.level1;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/** Shared colours + a small colour cache so we don't allocate Color objects every frame. */
public final class Palette {
    private Palette() {}

    private static final Map<Integer, Color> CACHE = new HashMap<>();

    /** Get a (cached) opaque colour from 0xRRGGBB. */
    public static Color c(int rgb) {
        return CACHE.computeIfAbsent(rgb, k -> new Color(k));
    }

    public static final int OUTLINE = 0x1E1A17;

    public static final int GRASS = 0x4C9A3A, GRASS_DARK = 0x3E8A32, GRASS_LIGHT = 0x66B24C;
    public static final int ROAD = 0x45413F, ROAD_DARK = 0x393634, ROAD_LIGHT = 0x58534F;
    public static final int SIDEWALK = 0xBDB39B, SIDEWALK_LINE = 0x9F957F, SIDEWALK_LIGHT = 0xCFC7B1;
    public static final int CURB = 0x2F2D2C;
}
