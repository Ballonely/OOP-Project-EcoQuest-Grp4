package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

/**
 * A piece of litter lying on the ground. Each category has six little sprites (variants):
 * 0-1 are the original street items, 2-5 are the city items used by Level 5.
 */
public class Garbage extends Entity {
    public static final int SIZE = 8;
    public static final int VARIANT_COUNT = 6;
    /** First variant index of the city-only items (pizza slice, newspaper, cigarette butt...). */
    public static final int FIRST_CITY_VARIANT = 2;

    private final GarbageType type;
    private final int variant;
    private boolean highlighted;
    private boolean bossWaveLitter;

    public Garbage(GarbageType type, int variant, double x, double y) {
        super(x, y, SIZE, SIZE);
        this.type = type;
        // Out-of-range values fall back to the two classic sprites, matching the old "% 2" behaviour.
        this.variant = variant >= 0 && variant < VARIANT_COUNT ? variant : Math.floorMod(variant, 2);
    }

    public GarbageType getType() { return type; }
    public String getItemName() {
        switch (type) {
            case BIODEGRADABLE:
                return new String[] {"banana peel", "apple core", "pizza slice", "chicken bone", "coffee grounds",
                        "half-eaten burger"}[variant];
            case RECYCLABLE:
                return new String[] {"can", "bottle", "newspaper", "cardboard box", "glass bottle",
                        "milk carton"}[variant];
            case NON_RECYCLABLE:
                return new String[] {"wrapper", "chip bag", "cigarette butt", "foam food box", "face mask",
                        "paper coffee cup"}[variant];
            case HAZARDOUS:
                return new String[] {"battery", "toxic drum", "spray paint can", "broken phone", "motor oil jug",
                        "vape pen"}[variant];
            default: throw new IllegalStateException("Unsupported garbage type: " + type);
        }
    }

    /** Litter washed ashore by the Level 4 boss; it counts toward the current wave, not stage progress. */
    public boolean isBossWaveLitter() { return bossWaveLitter; }
    public void markBossWaveLitter() { bossWaveLitter = true; }

    /** Same item at a new position, keeping its boss-wave flag. */
    public Garbage copyAt(double newX, double newY) {
        Garbage copy = new Garbage(type, variant, newX, newY);
        if (bossWaveLitter) copy.markBossWaveLitter();
        return copy;
    }

    public void drawIcon(Graphics2D g, int iconX, int iconY, int iconSize) {
        double scale = iconSize / (double) SIZE;
        Graphics2D iconGraphics = (Graphics2D) g.create();
        AffineTransform transform = new AffineTransform();
        transform.translate(iconX - x * scale, iconY - y * scale);
        transform.scale(scale, scale);
        iconGraphics.transform(transform);
        draw(iconGraphics);
        iconGraphics.dispose();
    }

    public void setHighlighted(boolean h) { this.highlighted = h; }
    protected boolean isHighlighted() { return highlighted; }

    /** Short lesson shown when this item goes in the wrong bin; empty when there is none. */
    public String getTip() { return ""; }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(X + 1, Y + 7, 6, 1);

        if (variant >= FIRST_CITY_VARIANT) drawCityItem(g, X, Y);
        else switch (type) {
            case BIODEGRADABLE:
                if (variant == 0) {                       // banana peel
                    rect(g, X + 1, Y + 3, 6, 2, 0xE7CF4A);
                    rect(g, X + 2, Y + 2, 3, 1, 0xE7CF4A);
                    rect(g, X + 5, Y + 4, 2, 2, 0xC9B232);
                    rect(g, X, Y + 3, 1, 1, 0x6B4A1E);
                } else {                                   // apple core
                    rect(g, X + 2, Y + 1, 4, 2, 0xB33A32);
                    rect(g, X + 3, Y + 3, 2, 2, 0xE8E3B0);
                    rect(g, X + 2, Y + 5, 4, 2, 0xB33A32);
                    rect(g, X + 4, Y, 1, 1, 0x5B3A1E);
                }
                break;
            case RECYCLABLE:
                if (variant == 0) {                       // can
                    rect(g, X + 2, Y + 1, 4, 6, 0xCF3B30);
                    rect(g, X + 2, Y + 1, 4, 1, 0xDADADA);
                    rect(g, X + 3, Y + 3, 2, 2, 0xF2F2F2);
                } else {                                   // plastic bottle
                    rect(g, X + 1, Y + 3, 5, 3, 0x5AA7E0);
                    rect(g, X + 6, Y + 4, 2, 1, 0x2F6FA8);
                    rect(g, X + 2, Y + 3, 2, 1, 0xBFE3FA);
                }
                break;
            case NON_RECYCLABLE:
                if (variant == 0) {                       // crumpled wrapper
                    rect(g, X + 1, Y + 2, 6, 4, 0xE9953A);
                    rect(g, X + 2, Y + 3, 2, 1, 0xF6C27A);
                    rect(g, X + 5, Y + 4, 2, 1, 0xB56A1E);
                } else {                                   // chip bag
                    rect(g, X + 2, Y + 1, 4, 6, 0xD9A441);
                    rect(g, X + 3, Y + 3, 2, 2, 0xB33A32);
                    rect(g, X + 2, Y + 1, 4, 1, 0x9A7428);
                }
                break;
            case HAZARDOUS:
                if (variant == 0) {                       // battery
                    rect(g, X + 1, Y + 2, 6, 4, 0x3A3A3A);
                    rect(g, X + 1, Y + 2, 2, 4, 0xE6C229);
                    rect(g, X + 7, Y + 3, 1, 2, 0xAAAAAA);
                } else {                                   // toxic drum
                    rect(g, X + 2, Y + 1, 4, 6, 0x6FBF3A);
                    rect(g, X + 2, Y + 3, 4, 1, 0x222222);
                    rect(g, X + 2, Y + 5, 4, 1, 0x222222);
                }
                break;
        }

        if (highlighted) {   // pulsing white box = "you can pick this up"
            int a = 150 + (int) (90 * Math.sin(System.currentTimeMillis() / 120.0));
            g.setColor(new Color(255, 255, 255, Math.max(60, Math.min(255, a))));
            g.drawRect(X - 1, Y - 1, SIZE + 1, SIZE + 1);
        }
    }

    /** 8x8 sprites for the Level 5 city litter (variants 2-5). */
    private void drawCityItem(Graphics2D g, int X, int Y) {
        switch (type) {
            case BIODEGRADABLE:
                if (variant == 2) {                       // pizza slice
                    rect(g, X + 1, Y + 1, 6, 1, 0xB9772F);          // crust
                    rect(g, X + 1, Y + 2, 6, 2, 0xF0C35A);
                    rect(g, X + 2, Y + 4, 4, 1, 0xF0C35A);
                    rect(g, X + 3, Y + 5, 2, 1, 0xF0C35A);
                    rect(g, X + 2, Y + 2, 1, 1, 0xC23B2C);          // pepperoni
                    rect(g, X + 5, Y + 3, 1, 1, 0xC23B2C);
                } else if (variant == 3) {                // chicken bone
                    rect(g, X + 2, Y + 3, 4, 2, 0xEDE6CF);
                    rect(g, X + 1, Y + 2, 2, 2, 0xEDE6CF);
                    rect(g, X + 1, Y + 4, 2, 2, 0xEDE6CF);
                    rect(g, X + 6, Y + 2, 1, 4, 0xEDE6CF);
                    rect(g, X + 3, Y + 4, 2, 1, 0xBDB59A);
                } else if (variant == 5) {                // half-eaten burger
                    rect(g, X + 1, Y + 2, 4, 2, 0xD99A48);          // top bun, bite missing on the right
                    rect(g, X + 2, Y + 1, 2, 1, 0xD99A48);
                    rect(g, X + 2, Y + 2, 1, 1, 0xF6E7B0);          // sesame seed
                    rect(g, X + 1, Y + 4, 5, 1, 0x6BB33E);          // lettuce
                    rect(g, X + 1, Y + 5, 5, 1, 0x6A3A22);          // patty
                    rect(g, X + 1, Y + 6, 6, 1, 0xC98838);          // bottom bun
                } else {                                   // spilled coffee grounds
                    rect(g, X + 1, Y + 3, 6, 3, 0x4A2F1B);
                    rect(g, X + 2, Y + 2, 3, 1, 0x4A2F1B);
                    rect(g, X + 2, Y + 4, 1, 1, 0x7A5233);
                    rect(g, X + 5, Y + 3, 1, 1, 0x7A5233);
                }
                break;
            case RECYCLABLE:
                if (variant == 2) {                       // folded newspaper
                    rect(g, X + 1, Y + 2, 6, 5, 0xE4E2DA);
                    rect(g, X + 2, Y + 3, 4, 1, 0x55585E);
                    rect(g, X + 2, Y + 5, 2, 1, 0x8C9096);
                    rect(g, X + 5, Y + 5, 1, 1, 0x8C9096);
                    rect(g, X + 1, Y + 6, 6, 1, 0xB9B6AC);
                } else if (variant == 3) {                // flattened cardboard box
                    rect(g, X + 1, Y + 1, 6, 6, 0xB98A52);
                    rect(g, X + 1, Y + 1, 6, 1, 0x8F6538);
                    rect(g, X + 4, Y + 2, 1, 5, 0x8F6538);
                    rect(g, X + 2, Y + 3, 1, 1, 0x4A3A28);
                } else if (variant == 5) {                // milk carton
                    rect(g, X + 2, Y + 3, 4, 4, 0xF2F2EE);
                    rect(g, X + 2, Y + 2, 4, 1, 0xC9D9E8);          // gabled top
                    rect(g, X + 3, Y + 1, 2, 1, 0xC9D9E8);
                    rect(g, X + 2, Y + 4, 4, 1, 0x3C7BC8);          // blue stripe
                    rect(g, X + 5, Y + 3, 1, 4, 0xC8C8C2);          // shaded side
                } else {                                   // green glass bottle
                    rect(g, X + 1, Y + 3, 5, 3, 0x2E8B4E);
                    rect(g, X + 6, Y + 4, 2, 1, 0x1F5E34);
                    rect(g, X + 2, Y + 3, 2, 1, 0x9BD6A8);
                    rect(g, X + 3, Y + 4, 1, 1, 0xE9E3C2);          // label
                }
                break;
            case NON_RECYCLABLE:
                if (variant == 2) {                       // cigarette butt
                    rect(g, X + 1, Y + 4, 4, 1, 0xF2F0E8);
                    rect(g, X + 5, Y + 4, 2, 1, 0xD98A3A);          // filter
                    rect(g, X, Y + 4, 1, 1, 0x555555);              // ash
                    rect(g, X + 2, Y + 3, 1, 1, 0x9A9A9A);
                } else if (variant == 3) {                // styrofoam takeout box
                    rect(g, X + 1, Y + 2, 6, 4, 0xF4F4EE);
                    rect(g, X + 1, Y + 2, 6, 1, 0xC9C9C0);
                    rect(g, X + 1, Y + 4, 6, 1, 0xC9C9C0);          // hinge
                    rect(g, X + 3, Y + 5, 2, 1, 0xD8B04A);          // leftover sauce
                } else if (variant == 5) {                // paper coffee cup (plastic-lined, not recyclable)
                    rect(g, X + 2, Y + 1, 4, 1, 0x3A3A3A);          // lid
                    rect(g, X + 2, Y + 2, 4, 5, 0xF4EFE4);
                    rect(g, X + 3, Y + 3, 2, 2, 0x2E7D4F);          // cafe logo
                    rect(g, X + 2, Y + 5, 4, 1, 0xA0703F);          // cardboard sleeve
                    rect(g, X + 5, Y + 2, 1, 5, 0xCFC8B8);
                } else {                                   // disposable face mask
                    rect(g, X + 2, Y + 2, 4, 4, 0x8EC8E8);
                    rect(g, X + 2, Y + 3, 4, 1, 0x6FA9CC);
                    rect(g, X + 1, Y + 2, 1, 1, 0xF2F2F2);          // ear loops
                    rect(g, X + 6, Y + 2, 1, 1, 0xF2F2F2);
                    rect(g, X + 1, Y + 5, 1, 1, 0xF2F2F2);
                    rect(g, X + 6, Y + 5, 1, 1, 0xF2F2F2);
                }
                break;
            case HAZARDOUS:
                if (variant == 2) {                       // spray paint can
                    rect(g, X + 2, Y + 2, 4, 5, 0xC8458F);
                    rect(g, X + 3, Y + 1, 2, 1, 0xDADADA);          // nozzle cap
                    rect(g, X + 2, Y + 4, 4, 1, 0x2B2B2B);
                    rect(g, X + 3, Y + 3, 1, 1, 0xF0A0CC);
                } else if (variant == 3) {                // broken phone (e-waste)
                    rect(g, X + 2, Y + 1, 4, 6, 0x222428);
                    rect(g, X + 3, Y + 2, 2, 4, 0x3C6E9A);          // screen
                    rect(g, X + 3, Y + 3, 1, 1, 0xE8F4FF);          // crack
                    rect(g, X + 4, Y + 4, 1, 1, 0xE8F4FF);
                } else if (variant == 5) {                // disposable vape pen (lithium battery)
                    rect(g, X + 1, Y + 3, 5, 2, 0x5B4FA8);
                    rect(g, X + 6, Y + 3, 1, 2, 0x222222);          // mouthpiece
                    rect(g, X + 1, Y + 3, 1, 2, 0x8FE3F0);          // LED tip
                    rect(g, X + 3, Y + 3, 2, 1, 0x9C92E0);          // shine
                } else {                                   // used motor oil jug
                    rect(g, X + 1, Y + 2, 5, 5, 0x2A2A2A);
                    rect(g, X + 6, Y + 2, 1, 2, 0x2A2A2A);          // handle
                    rect(g, X + 2, Y + 1, 2, 1, 0xE6C229);          // cap
                    rect(g, X + 2, Y + 4, 3, 1, 0xE6C229);          // label stripe
                    rect(g, X + 1, Y + 7, 3, 1, 0x1A1410);          // oil drip
                }
                break;
        }
    }
}
