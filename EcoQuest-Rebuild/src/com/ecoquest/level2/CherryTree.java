package com.ecoquest.level2;

import java.awt.Graphics2D;
import java.util.Random;

public class CherryTree extends ParkTree {
    private static final int[] PALETTE = { 0x9A4466, 0xD97FA3, 0xF0A6C4, 0xF9C4D8, 0xFFE6EF };

    public CherryTree(int px, int py, int size) { super(px, py, size); }

    @Override
    protected int[] palette() { return PALETTE; }

    @Override
    protected void decorate(Graphics2D g, Random rn, int canopyH) {
        for (int i = 0; i < w / 3; i++)
            rect(g, 4 + rn.nextInt(w - 8), 3 + rn.nextInt(canopyH - 10), 1, 1, 0xFFFFFF);
    }
}
