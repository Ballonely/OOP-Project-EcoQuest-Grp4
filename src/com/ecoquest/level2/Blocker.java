package com.ecoquest.level2;

import com.ecoquest.level1.Prop;

import java.awt.Graphics2D;

public class Blocker extends Prop {
    public Blocker(int px, int py, int w, int h) { super(px, py, w, h, true); }

    @Override public boolean isGroundDecal() { return true; }

    @Override
    public void draw(Graphics2D g) {}
}
