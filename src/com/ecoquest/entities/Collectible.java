package com.ecoquest.entities;

import com.ecoquest.util.Constants;

import java.awt.Graphics2D;

/** A collectible "eco token" that raises score and cleanliness level. */
public class Collectible extends GameObject {

    private final int points = 50;
    private final int cleanlinessGain = 4;
    private double bobPhase;

    public Collectible(double x, double y) {
        super(x, y, 22, 22);
    }

    @Override
    public void update(double deltaTime) {
        bobPhase += deltaTime * 4;
    }

    @Override
    public void render(Graphics2D g) {
        int bob = (int) (Math.sin(bobPhase) * 3);
        g.setColor(Constants.COLOR_GOLD);
        g.fillOval((int) x, (int) y + bob, width, height);
        g.setColor(Constants.COLOR_TEAL_DARK);
        g.drawOval((int) x, (int) y + bob, width, height);
    }

    public int getPoints() { return points; }
    public int getCleanlinessGain() { return cleanlinessGain; }
}
