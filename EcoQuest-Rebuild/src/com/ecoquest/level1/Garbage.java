package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** A piece of litter from the shared beach resource pack. */
public class Garbage extends Entity {
    public static final int SIZE = 8;
    private static final String[][] NAMES = {
        {"animal poo", "seaweed", "banana peel", "apple core", "fish bones", "driftwood"},
        {"plastic bottle", "crushed can", "glass bottle", "cardboard", "can tab", "rope coil"},
        {"plastic bag", "snack wrapper", "takeaway cup", "cigarette butt", "old tire", "lost flip-flop"},
        {"fishing net", "foam container", "glass shards", "used mask", "broken bucket", "trash bag"}
    };
    private static final int[][] VALUES = {
        {1, 2, 2, 2, 3, 2}, {4, 3, 4, 3, 1, 3}, {2, 2, 2, 1, 4, 2}, {6, 2, 3, 4, 3, 5}
    };

    private final GarbageType type;
    private final int variant;
    private final ecoquest.TrashDef marketDefinition;
    private boolean highlighted;
    private boolean coinCollected;
    private boolean bossWaveLitter;

    public Garbage(GarbageType type, int variant, double x, double y) {
        super(x, y, SIZE, SIZE);
        this.type = type;
        this.variant = Math.floorMod(variant, 6);
        this.marketDefinition = null;
    }

    public Garbage(ecoquest.TrashDef definition, double x, double y) {
        super(x, y, SIZE, SIZE);
        this.marketDefinition = definition;
        this.variant = 0;
        this.type = switch (definition.type()) {
            case BIO -> GarbageType.BIODEGRADABLE;
            case REC -> GarbageType.RECYCLABLE;
            case TRASH -> GarbageType.NON_RECYCLABLE;
            case HAZ -> GarbageType.HAZARDOUS;
        };
    }

    public GarbageType getType() { return type; }
    public String getItemName() { return marketDefinition == null ? NAMES[type.ordinal()][variant] : marketDefinition.name(); }
    public String getItemTip() { return marketDefinition == null ? "" : marketDefinition.tip(); }
    public int getCoinValue() { return marketDefinition == null ? VALUES[type.ordinal()][variant] : 2; }
    public int getVariant() { return variant; }
    public boolean isCoinCollected() { return coinCollected; }
    public void markCoinCollected() { coinCollected = true; }
    public boolean isBossWaveLitter() { return bossWaveLitter; }
    public void markBossWaveLitter() { bossWaveLitter = true; }

    public Garbage copyAt(double newX, double newY) {
        Garbage copy = marketDefinition == null
                ? new Garbage(type, variant, newX, newY)
                : new Garbage(marketDefinition, newX, newY);
        if (coinCollected) copy.markCoinCollected();
        if (bossWaveLitter) copy.markBossWaveLitter();
        return copy;
    }

    public void drawIcon(Graphics2D g, int iconX, int iconY, int iconSize) {
        if (marketDefinition != null) {
            g.drawImage(ecoquest.Sprites.trashIcon(marketDefinition), iconX, iconY, iconSize, iconSize, null);
            return;
        }
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

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        if (marketDefinition != null) {
            BufferedImage icon = ecoquest.Sprites.trashIcon(marketDefinition);
            g.drawImage(icon, X - 4, Y - 4, 16, 16, null);
            if (highlighted) {
                int a = 150 + (int) (90 * Math.sin(System.currentTimeMillis() / 120.0));
                g.setColor(new Color(255, 255, 255, Math.max(60, Math.min(255, a))));
                g.drawRect(X - 5, Y - 5, 17, 17);
            }
            return;
        }
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(X + 1, Y + 7, 6, 1);

        switch (type) {
            case BIODEGRADABLE -> drawBio(g, X, Y);
            case RECYCLABLE -> drawRecyclable(g, X, Y);
            case NON_RECYCLABLE -> drawNonRecyclable(g, X, Y);
            case HAZARDOUS -> drawHazard(g, X, Y);
        }

        if (highlighted) {   // pulsing white box = "you can pick this up"
            int a = 150 + (int) (90 * Math.sin(System.currentTimeMillis() / 120.0));
            g.setColor(new Color(255, 255, 255, Math.max(60, Math.min(255, a))));
            g.drawRect(X - 1, Y - 1, SIZE + 1, SIZE + 1);
        }
    }

    private void drawBio(Graphics2D g, int X, int Y) {
        switch (variant) {
            case 0 -> { rect(g, X + 1, Y + 4, 6, 3, 0x8C512C); rect(g, X + 2, Y + 3, 4, 2, 0x9E6034); }
            case 1 -> { rect(g, X + 1, Y + 2, 2, 5, 0x287847); rect(g, X + 3, Y + 1, 2, 6, 0x52A56B); rect(g, X + 5, Y + 3, 2, 4, 0x397E4F); }
            case 2 -> { rect(g, X + 1, Y + 3, 6, 2, 0xE7CF4A); rect(g, X + 2, Y + 2, 3, 1, 0xE7CF4A); rect(g, X + 5, Y + 4, 2, 2, 0xC9B232); }
            case 3 -> { rect(g, X + 2, Y + 1, 4, 2, 0xB33A32); rect(g, X + 3, Y + 3, 2, 2, 0xE8E3B0); rect(g, X + 2, Y + 5, 4, 2, 0xB33A32); }
            case 4 -> { rect(g, X + 1, Y + 3, 6, 2, 0xF0E8C9); rect(g, X + 2, Y + 2, 1, 4, 0xF0E8C9); rect(g, X + 5, Y + 2, 1, 4, 0xF0E8C9); rect(g, X + 3, Y + 1, 1, 6, 0xF0E8C9); }
            default -> { rect(g, X + 1, Y + 3, 6, 3, 0x9A6942); rect(g, X + 2, Y + 2, 4, 1, 0xB48456); rect(g, X + 3, Y + 5, 1, 1, 0x62452F); }
        }
    }

    private void drawRecyclable(Graphics2D g, int X, int Y) {
        switch (variant) {
            case 0 -> { rect(g, X + 2, Y + 1, 4, 6, 0x5AA7E0); rect(g, X + 3, Y, 2, 2, 0x2F6FA8); rect(g, X + 3, Y + 3, 1, 2, 0xBFE3FA); }
            case 1 -> { rect(g, X + 2, Y + 2, 4, 5, 0xCF3B30); rect(g, X + 1, Y + 3, 1, 3, 0xB12F29); rect(g, X + 2, Y + 1, 4, 1, 0xDADADA); }
            case 2 -> { rect(g, X + 2, Y + 2, 4, 5, 0x639B79); rect(g, X + 3, Y + 1, 2, 2, 0x47745B); rect(g, X + 3, Y + 3, 1, 2, 0xBEE4CD); }
            case 3 -> { rect(g, X + 1, Y + 2, 6, 5, 0xB98D5B); rect(g, X + 2, Y + 3, 4, 1, 0xD7B781); rect(g, X + 2, Y + 5, 4, 1, 0x7C5B3B); }
            case 4 -> { rect(g, X + 2, Y + 3, 4, 3, 0xB4C3C8); rect(g, X + 3, Y + 2, 2, 1, 0xE9F0EF); rect(g, X + 4, Y + 4, 1, 1, 0x687980); }
            default -> { rect(g, X + 1, Y + 2, 6, 5, 0x7B5739); rect(g, X + 2, Y + 1, 4, 1, 0xA47B51); rect(g, X + 2, Y + 3, 4, 1, 0xD0AA72); rect(g, X + 3, Y + 4, 2, 2, 0x7B5739); }
        }
    }

    private void drawNonRecyclable(Graphics2D g, int X, int Y) {
        switch (variant) {
            case 0 -> { rect(g, X + 1, Y + 2, 6, 5, 0xC8D0C4); rect(g, X + 2, Y + 1, 4, 1, 0xECF0E8); rect(g, X + 3, Y + 4, 2, 1, 0x8E9C96); }
            case 1 -> { rect(g, X + 1, Y + 2, 6, 4, 0xE9953A); rect(g, X + 2, Y + 3, 2, 1, 0xF6C27A); rect(g, X + 5, Y + 4, 2, 1, 0xB56A1E); }
            case 2 -> { rect(g, X + 2, Y + 2, 4, 5, 0xF1E9D1); rect(g, X + 1, Y + 1, 6, 2, 0xB54539); rect(g, X + 3, Y + 4, 2, 2, 0x6FA5B0); }
            case 3 -> { rect(g, X + 1, Y + 4, 6, 2, 0xC9783A); rect(g, X + 1, Y + 3, 2, 1, 0xE6E0D0); rect(g, X + 5, Y + 3, 2, 1, 0xE6E0D0); }
            case 4 -> { g.setColor(new Color(0x25282D)); g.drawOval(X + 1, Y + 1, 6, 6); g.setColor(Color.DARK_GRAY); g.drawOval(X + 2, Y + 2, 4, 4); }
            default -> { rect(g, X + 1, Y + 4, 6, 2, 0x2A88A1); rect(g, X + 2, Y + 2, 3, 2, 0xF2A342); rect(g, X + 6, Y + 3, 1, 3, 0xE55C42); }
        }
    }

    private void drawHazard(Graphics2D g, int X, int Y) {
        switch (variant) {
            case 0 -> { rect(g, X + 1, Y + 2, 6, 1, 0x695E48); rect(g, X + 2, Y + 3, 1, 3, 0x695E48); rect(g, X + 5, Y + 3, 1, 3, 0x695E48); rect(g, X + 1, Y + 6, 6, 1, 0x695E48); }
            case 1 -> { rect(g, X + 1, Y + 3, 6, 3, 0xEEE9D7); rect(g, X + 2, Y + 2, 4, 1, 0xFFFFFF); rect(g, X + 1, Y + 2, 1, 1, 0xB6B5AA); }
            case 2 -> { rect(g, X + 1, Y + 4, 2, 2, 0x86C2D0); rect(g, X + 4, Y + 2, 2, 4, 0xA6D7DF); rect(g, X + 6, Y + 4, 1, 2, 0x72A9BC); }
            case 3 -> { rect(g, X + 1, Y + 3, 6, 3, 0xA7D9E2); rect(g, X + 2, Y + 2, 1, 1, 0x527E8A); rect(g, X + 5, Y + 2, 1, 1, 0x527E8A); rect(g, X + 1, Y + 6, 1, 1, 0x527E8A); rect(g, X + 6, Y + 6, 1, 1, 0x527E8A); }
            case 4 -> { rect(g, X + 2, Y + 2, 4, 5, 0xD87836); rect(g, X + 1, Y + 3, 1, 3, 0xA34524); rect(g, X + 3, Y + 1, 2, 1, 0xE8A45B); }
            default -> { rect(g, X + 2, Y + 2, 5, 5, 0x596A45); rect(g, X + 1, Y + 3, 1, 3, 0x354532); rect(g, X + 3, Y + 4, 3, 1, 0xD5C752); }
        }
    }
}
