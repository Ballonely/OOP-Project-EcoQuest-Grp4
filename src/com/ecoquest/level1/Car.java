package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

/** A parked car (top-down). An obstacle the player has to walk around. */
public class Car extends Prop {
    private static final int LONG = 28, SHORT = 14;
    private final boolean horizontal;
    private final int body, bodyLight;

    public Car(int px, int py, boolean horizontal, int body, int bodyLight) {
        super(px, py, horizontal ? LONG : SHORT, horizontal ? SHORT : LONG, true);
        this.horizontal = horizontal;
        this.body = body;
        this.bodyLight = bodyLight;
    }

    @Override
    public void draw(Graphics2D g) {
        AffineTransform old = g.getTransform();
        if (horizontal) {
            g.translate(x, y + h);
            g.rotate(-Math.PI / 2);
        } else {
            g.translate(x, y);
        }
        // drawn as a vertical car in local coordinates (14 x 28)
        rect(g, 0, 4, 2, 5, 0x111111);  rect(g, 12, 4, 2, 5, 0x111111);      // wheels
        rect(g, 0, 19, 2, 5, 0x111111); rect(g, 12, 19, 2, 5, 0x111111);
        rect(g, 1, 0, 12, 28, Palette.OUTLINE);
        rect(g, 2, 1, 10, 26, body);
        rect(g, 3, 2, 8, 1, bodyLight);
        rect(g, 3, 5, 8, 6, 0x24303F);                                         // windshield
        rect(g, 3, 5, 8, 1, 0x4B6179);
        rect(g, 3, 11, 8, 8, bodyLight);                                       // roof
        rect(g, 3, 19, 8, 4, 0x24303F);                                        // rear window
        rect(g, 2, 1, 2, 2, 0xFFE9A0);  rect(g, 10, 1, 2, 2, 0xFFE9A0);      // headlights
        rect(g, 2, 26, 2, 1, 0xD03030); rect(g, 10, 26, 2, 1, 0xD03030);     // tail lights
        g.setTransform(old);
    }
}
