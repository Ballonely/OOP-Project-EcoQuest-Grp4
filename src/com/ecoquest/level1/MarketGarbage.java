package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Town Market litter: a regular {@link Garbage} with its own name, icon and wrong-bin tip. */
final class MarketGarbage extends Garbage {
    /** Market icons are 16px art; drawn at this size so they read clearly on the shared world. */
    private static final int ICON_SIZE = 11;
    private final MarketTrash.Def def;

    MarketGarbage(MarketTrash.Def def, double x, double y) {
        super(def.type(), 0, x, y);
        this.def = def;
    }

    @Override public String getItemName() { return def.name(); }

    @Override public String getTip() { return def.tip(); }

    @Override
    public Garbage copyAt(double newX, double newY) {
        MarketGarbage copy = new MarketGarbage(def, newX, newY);
        if (isBossWaveLitter()) copy.markBossWaveLitter();
        return copy;
    }

    @Override
    public void draw(Graphics2D g) {
        int cx = (int) Math.round(x + SIZE / 2.0), bottom = (int) Math.round(y + SIZE);
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(cx - 5, bottom - 2, 10, 3);
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(MarketSprites.trashIcon(def), cx - ICON_SIZE / 2, bottom - ICON_SIZE, ICON_SIZE, ICON_SIZE, null);
        if (hint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
        if (isHighlighted()) {   // pulsing white box = "you can pick this up"
            int a = 150 + (int) (90 * Math.sin(System.currentTimeMillis() / 120.0));
            g.setColor(new Color(255, 255, 255, Math.max(60, Math.min(255, a))));
            g.drawRect(cx - ICON_SIZE / 2 - 1, bottom - ICON_SIZE - 1, ICON_SIZE + 1, ICON_SIZE + 1);
        }
    }
}
