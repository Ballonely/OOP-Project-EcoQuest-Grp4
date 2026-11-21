package com.ecoquest.level2;

public class GreenTree extends ParkTree {
    private static final int[] PALETTE = { 0x0F4A28, 0x16703A, 0x23893E, 0x3EA84A, 0x6CCB5A };

    public GreenTree(int px, int py, int size) { super(px, py, size); }

    @Override
    protected int[] palette() { return PALETTE; }
}
