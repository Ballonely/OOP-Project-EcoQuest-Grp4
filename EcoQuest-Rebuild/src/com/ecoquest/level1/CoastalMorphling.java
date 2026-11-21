package com.ecoquest.level1;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

final class CoastalMorphling {
    private final BufferedImage sprite;

    CoastalMorphling() {
        BufferedImage loaded = null;
        try { loaded = ImageIO.read(new File("assets/water_morphling.png")); }
        catch (Exception ignored) { }
        sprite = loaded;
    }

    void draw(Graphics2D g, double time) {
        int x = (int) Math.round(x(time)), y = (int) Math.round(y(time));
        drawTideBurst(g, time, x + 36, y + 42);
        g.setColor(new Color(25, 108, 112, 120));
        g.fillOval(x - 4, y + 47, 78, 16);
        if (sprite == null) return;
        Graphics2D copy = (Graphics2D) g.create();
        copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        copy.drawImage(sprite, x, y, 72, 58, null);
        copy.dispose();
    }

    void drawEntrance(Graphics2D g, double time, double progress) {
        double p = Math.max(0, Math.min(1, progress));
        double rise = p * p * (3 - 2 * p);
        int x = (int) Math.round(x(time)), baseY = (int) Math.round(y(time));
        int spriteY = baseY + (int) Math.round((1 - rise) * 38);
        int cx = x + 36, waterY = baseY + 49;
        drawEntranceSplash(g, time, p, cx, waterY);
        g.setColor(new Color(25, 108, 112, (int) (120 * rise)));
        g.fillOval(x - 4, waterY - 2, 78, 18);
        if (sprite == null) return;

        Graphics2D copy = (Graphics2D) g.create();
        copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        copy.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, 0.25 + rise * 0.9)));
        copy.drawImage(sprite, x, spriteY, 72, 58, null);
        copy.dispose();
    }

    boolean touches(double px, double py, double time, double radius) {
        return Math.hypot(px - (x(time) + 36), py - (y(time) + 30)) <= radius;
    }

    double x(double time) { return 410 + Math.sin(time * 0.42) * 7; }
    double y(double time) { return 126 + Math.sin(time * 0.56) * 4; }

    private void drawTideBurst(Graphics2D g, double time, int cx, int cy) {
        for (int i = 0; i < 4; i++) {
            double phase = (time * 22 + i * 18) % 56;
            int radius = 12 + (int) phase;
            int alpha = Math.max(0, 115 - (int) phase * 2);
            g.setColor(new Color(104, 224, 220, alpha));
            g.drawOval(cx - radius, cy - radius / 2, radius * 2, radius);
        }
        for (int i = 0; i < 9; i++) {
            double angle = i * Math.PI * 2 / 9 + time * 0.7;
            double distance = 24 + 14 * Math.sin(time * 2.4 + i);
            int px = cx + (int) (Math.cos(angle) * distance);
            int py = cy + (int) (Math.sin(angle) * distance * 0.5);
            g.setColor(i % 2 == 0 ? new Color(157, 240, 226, 210) : new Color(71, 192, 192, 220));
            g.fillRect(px, py, 2, 2);
        }
    }

    private void drawEntranceSplash(Graphics2D g, double time, double progress, int cx, int cy) {
        double burst = Math.sin(progress * Math.PI);
        for (int i = 0; i < 4; i++) {
            int radius = 12 + (int) (progress * 48) + i * 8;
            int alpha = Math.max(0, (int) ((1 - progress) * 150) - i * 30);
            g.setColor(new Color(133, 238, 226, alpha));
            g.drawOval(cx - radius, cy - radius / 3, radius * 2, Math.max(6, radius * 2 / 3));
        }
        for (int i = 0; i < 18; i++) {
            double angle = i * Math.PI * 2 / 18 + time * 0.35;
            double distance = 8 + progress * 38 + 6 * Math.sin(i * 2.4);
            int px = cx + (int) (Math.cos(angle) * distance);
            int py = cy - (int) (Math.abs(Math.sin(angle)) * burst * 26) + (int) (Math.sin(angle) * distance * 0.3);
            int alpha = (int) (235 * burst);
            g.setColor(i % 3 == 0 ? new Color(203, 255, 242, alpha) : new Color(78, 203, 198, alpha));
            int size = i % 4 == 0 ? 4 : 3;
            g.fillOval(px, py, size, size + 1);
        }
        if (progress < 0.35) {
            int crown = (int) (burst * 30);
            g.setColor(new Color(148, 246, 231, (int) (180 * burst)));
            g.fillOval(cx - 15 - crown, cy - crown / 2, 10, crown + 3);
            g.fillOval(cx + crown + 5, cy - crown / 2, 10, crown + 3);
        }
    }
}
