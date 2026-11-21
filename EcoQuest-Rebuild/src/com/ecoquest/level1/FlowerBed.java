package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.util.Random;

/** Flat decoration of little flowers. */
public class FlowerBed extends Prop {
    private static final int[] COLORS = { 0xE8405A, 0xF2D13A, 0xF5F0F0, 0xE070D0, 0xF08A30 };
    private final int[] dots;

    public FlowerBed(int px, int py, int w) {
        super(px, py, w, 8, false);
        Random r = new Random(px * 131L + py);
        dots = new int[(w / 3) * 3];
        for (int i = 0; i < dots.length; i += 3) {
            dots[i] = r.nextInt(w - 1);
            dots[i + 1] = r.nextInt(7);
            dots[i + 2] = COLORS[r.nextInt(COLORS.length)];
        }
    }

    @Override public boolean isGroundDecal() { return true; }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        rect(g, X, Y + 2, w, 6, 0x2F7A33);
        for (int i = 0; i < dots.length; i += 3) {
            rect(g, X + dots[i], Y + dots[i + 1], 2, 2, dots[i + 2]);
            rect(g, X + dots[i], Y + dots[i + 1] + 2, 1, 1, 0x1E5A26);
        }
    }
}
