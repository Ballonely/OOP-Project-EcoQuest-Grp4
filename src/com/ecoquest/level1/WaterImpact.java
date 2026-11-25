package com.ecoquest.level1;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Telegraphs a sewage splash, then leaves a brief muddy slow zone. */
final class WaterImpact extends Prop implements TimedProp {
    static final double WARNING_SECONDS = 1.15;
    static final double PUDDLE_SECONDS = 3.0;
    static final int RADIUS = 24;
    private double age;
    private boolean hitApplied;

    WaterImpact(double centerX, double centerY) {
        super((int) Math.round(centerX) - RADIUS, (int) Math.round(centerY) - RADIUS,
                RADIUS * 2, RADIUS * 2, false);
    }

    @Override public boolean update(double dt) {
        age += dt;
        return age < WARNING_SECONDS + PUDDLE_SECONDS;
    }

    @Override public double speedFactor() { return age < WARNING_SECONDS ? 1.0 : 0.42; }

    boolean overlapsSlowZone(Rectangle2D box) {
        if (age < WARNING_SECONDS) return false;
        double nearestX = Math.max(box.getMinX(), Math.min(centerX(), box.getMaxX()));
        double nearestY = Math.max(box.getMinY(), Math.min(centerY(), box.getMaxY()));
        return Math.hypot(centerX() - nearestX, centerY() - nearestY) <= RADIUS;
    }

    boolean tryHitPlayer(Player player) {
        if (hitApplied || age < WARNING_SECONDS) return false;
        Rectangle2D.Double body = new Rectangle2D.Double(player.getX() + 3, player.getY() + 2, 10, 14);
        if (!overlapsSlowZone(body)) return false;
        hitApplied = true;
        player.applySewageSplash();
        return true;
    }

    @Override public boolean isGroundDecal() { return true; }

    @Override public void draw(Graphics2D g) {
        int cx = (int) Math.round(centerX()), cy = (int) Math.round(centerY());
        if (age < WARNING_SECONDS) {
            double pulse = 0.5 + 0.5 * Math.sin(age * 16);
            g.setColor(new Color(139, 20, 23, (int) (32 + pulse * 50)));
            g.fillOval(cx - RADIUS, cy - RADIUS, RADIUS * 2, RADIUS * 2);
            g.setStroke(new BasicStroke(2.5f));
            g.setColor(new Color(245, 44, 39, (int) (170 + pulse * 85)));
            g.drawOval(cx - RADIUS, cy - RADIUS, RADIUS * 2, RADIUS * 2);
            int inner = 8 + (int) (5 * pulse);
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(cx - inner, cy - inner, inner * 2, inner * 2);
            g.setFont(new Font(Font.DIALOG, Font.BOLD, 7));
            g.setColor(new Color(255, 232, 216));
            String warning = "MOVE!";
            g.drawString(warning, cx - g.getFontMetrics().stringWidth(warning) / 2, cy - RADIUS - 3);
            return;
        }

        double puddleAge = age - WARNING_SECONDS;
        double fade = Math.min(1, Math.min(puddleAge / 0.12, (PUDDLE_SECONDS - puddleAge) / 0.32));
        int alpha = (int) (190 * Math.max(0, fade));
        g.setColor(new Color(64, 48, 28, alpha));
        g.fillOval(cx - RADIUS + 2, cy - RADIUS + 2, RADIUS * 2 - 4, RADIUS * 2 - 4);
        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(118, 81, 43, Math.max(0, alpha - 25)));
        g.drawOval(cx - RADIUS + 2, cy - RADIUS + 2, RADIUS * 2 - 4, RADIUS * 2 - 4);
        g.setColor(new Color(163, 124, 66, Math.max(0, alpha - 70)));
        for (int i = 0; i < 5; i++) {
            int bx = cx - 15 + (int) ((i * 11 + puddleAge * (10 + i * 2)) % 30);
            int by = cy - 14 + (i * 13 % 28);
            g.drawOval(bx, by, 3 + i % 3, 2 + i % 2);
        }
        if (puddleAge < 0.45) {
            double splash = 1 - puddleAge / 0.45;
            g.setColor(new Color(188, 223, 190, (int) (210 * splash)));
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI * 2 / 12;
                int distance = RADIUS - 2 + (int) (8 * (1 - splash));
                int px = cx + (int) (Math.cos(angle) * distance);
                int py = cy + (int) (Math.sin(angle) * distance);
                g.fillOval(px, py, 3, 4);
            }
        }
    }
}
