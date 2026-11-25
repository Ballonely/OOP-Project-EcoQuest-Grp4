package com.ecoquest.level1;

import java.awt.Graphics2D;

final class StaticBlock extends Prop {
    StaticBlock(int x, int y, int w, int h) { super(x, y, w, h, true); }
    @Override public void draw(Graphics2D g) {}
}
