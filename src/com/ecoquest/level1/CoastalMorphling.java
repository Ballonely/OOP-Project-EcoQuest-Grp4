package com.ecoquest.level1;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

final class CoastalMorphling {
    private final BufferedImage sprite;

    CoastalMorphling() {
        BufferedImage loaded = null;
        try { loaded = ImageIO.read(new File("assets/water_morphling.png")); }
        catch (Exception ignored) { }
        sprite = loaded;
    }

    void draw(Graphics2D g, double time) {
        draw(g, time, 0, List.of());
    }

    void draw(Graphics2D g, double time, double attackTime, List<double[]> targets) {
        int x = (int) Math.round(x(time)), y = (int) Math.round(y(time));
        drawTideBurst(g, time, x + 40, y + 55);
        g.setColor(new Color(25, 108, 112, 120));
        g.fillOval(x - 7, y + 61, 94, 18);
        if (sprite == null) return;
        Graphics2D copy = (Graphics2D) g.create();
        copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        copy.drawImage(sprite, x, y, 80, 72, null);
        copy.dispose();
        if (attackTime > 0 && !targets.isEmpty()) drawLaunchedWater(g, x + 42, y + 17, attackTime, targets);
    }

    void drawEntrance(Graphics2D g, double time, double progress) {
        double p = Math.max(0, Math.min(1, progress));
        double rise = p * p * (3 - 2 * p);
        int x = (int) Math.round(x(time)), baseY = (int) Math.round(y(time));
        int spriteY = baseY + (int) Math.round((1 - rise) * 38);
        int cx = x + 40, waterY = baseY + 62;
        drawEntranceSplash(g, time, p, cx, waterY);
        g.setColor(new Color(25, 108, 112, (int) (120 * rise)));
        g.fillOval(x - 4, waterY - 2, 78, 18);
        if (sprite == null) return;

        Graphics2D copy = (Graphics2D) g.create();
        copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        copy.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, 0.25 + rise * 0.9)));
        copy.drawImage(sprite, x, spriteY, 80, 72, null);
        copy.dispose();
    }

    void drawDefeated(Graphics2D g, double time, double progress) {
        double p = Math.max(0, Math.min(1, progress));
        int x = (int) Math.round(x(time)), y = (int) Math.round(y(time) + p * 45);
        int cx = x + 40, waterY = y + 62;
        drawEntranceSplash(g, time * 1.8, 1 - p, cx, waterY);
        g.setColor(new Color(25, 108, 112, (int) (120 * (1 - p))));
        g.fillOval(x - 7, waterY - 2, 94, 18);
        if (sprite == null || p >= 0.95) return;
        Graphics2D copy = (Graphics2D) g.create();
        copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        copy.setComposite(AlphaComposite.SrcOver.derive((float) (1 - p)));
        copy.drawImage(sprite, x, y, 80, 72, null);
        copy.dispose();
    }

    boolean touches(double px, double py, double time, double radius) {
        return Math.hypot(px - (x(time) + 40), py - (y(time) + 36)) <= radius;
    }

    double x(double time) { return 224 + Math.sin(time * 0.28) * 5; }
    double y(double time) { return 94 + Math.sin(time * 0.36) * 3; }

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

    private void drawLaunchedWater(Graphics2D g, int startX, int startY, double attackTime,
                                   List<double[]> targets) {
        double progress = Math.max(0, Math.min(1, 1 - attackTime / WaterImpact.WARNING_SECONDS));
        g.setColor(new Color(129, 239, 228, (int) (150 * (1 - progress * 0.35))));
        g.fillOval(startX - 9, startY - 7, 18, 15);
        int count = Math.min(8, targets.size());
        for (int i = 0; i < count; i++) {
            double local = Math.max(0, Math.min(1, progress * 1.24 - i * 0.035));
            if (progress * 1.24 < i * 0.035) continue;
            double[] target = targets.get(i);
            double px = startX + (target[0] - startX) * local;
            double py = startY + (target[1] - startY) * local - Math.sin(local * Math.PI) * 42;
            int size = 3 + (int) (2 * Math.sin(local * Math.PI));
            g.setColor(new Color(122, 231, 221, 100));
            g.drawLine(startX, startY, (int) px, (int) py);
            g.setColor(i % 2 == 0 ? new Color(202, 255, 241, 235) : new Color(93, 202, 199, 240));
            g.fillOval((int) px - size / 2, (int) py - size / 2, size, size);
            if (local > 0.8) {
                int alpha = (int) (190 * (1 - local));
                g.setColor(new Color(202, 255, 241, Math.max(0, alpha)));
                g.fillOval((int) target[0] - 4, (int) target[1] - 4, 8, 8);
            }
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
