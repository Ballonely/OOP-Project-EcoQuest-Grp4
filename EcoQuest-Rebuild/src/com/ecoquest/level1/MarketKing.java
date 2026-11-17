package com.ecoquest.level1;

import java.awt.Graphics2D;

final class MarketKing extends Entity {
    private double progress;
    private double shake;
    private double time;

    MarketKing(int centerX, int baseY, double initialProgress) {
        super(centerX - 24, baseY - 40, 48, 40);
        this.progress = initialProgress;
    }

    void update(double dt, double stageProgress) {
        time += dt;
        shake = Math.max(0, shake - dt);
        progress = Math.max(0, Math.min(100, stageProgress));
    }

    void react() { shake = 0.6; }

    @Override public void draw(Graphics2D g) {
        double size = 0.5 + 0.5 * (1 - progress / 100.0);
        ecoquest.Sprites.wasteKing(g, (int) Math.round(centerX()),
                (int) Math.round(y + h), size, shake, time);
    }
}
