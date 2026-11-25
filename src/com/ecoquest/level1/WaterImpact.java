package com.ecoquest.level1;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/**
 * The Sewage Morphling's geyser: a bubbling, marked spot on the ground, then a column of sea water
 * bursts up out of it. Touching the column while it stands (not yet collapsing) throws the player into
 * the air and stuns them (no damage, no slow). It is depth-sorted so the column rises in front of or behind the player.
 */
final class WaterImpact extends Prop implements TimedProp {
    static final double WARNING_SECONDS = 1.15;
    /** How long the column stands after erupting. */
    static final double GEYSER_SECONDS = 1.0;
    /** The column launches anyone who touches it until it starts to collapse. */
    private static final double LAUNCH_WINDOW = 0.6;
    static final int RADIUS = 20;
    private static final int COLUMN_HEIGHT = 52;
    private double age;

    WaterImpact(double centerX, double centerY) {
        super((int) Math.round(centerX) - RADIUS, (int) Math.round(centerY) - RADIUS,
                RADIUS * 2, RADIUS * 2, false);
    }

    @Override public boolean update(double dt) {
        age += dt;
        return age < WARNING_SECONDS + GEYSER_SECONDS;
    }

    /** True if the player's feet are on the geyser spot (it is a flat ellipse on the ground). */
    private boolean onSpot(Rectangle2D feet) {
        double nearestX = Math.max(feet.getMinX(), Math.min(centerX(), feet.getMaxX()));
        double nearestY = Math.max(feet.getMinY(), Math.min(centerY(), feet.getMaxY()));
        return Math.hypot(centerX() - nearestX, (centerY() - nearestY) * 1.4) <= RADIUS - 2;
    }

    boolean tryHitPlayer(Player player) {
        double since = age - WARNING_SECONDS;
        if (since < 0 || since > LAUNCH_WINDOW || player.isAirborne()) return false;
        Rectangle2D.Double feet = new Rectangle2D.Double(player.getX() + 3, player.getY() + Player.H - 5, Player.W - 6, 5);
        if (!onSpot(feet)) return false;
        player.launchByGeyser();             // also when walking into a column that is still standing
        return true;
    }

    /** Sorted by the spot's middle, so the column covers a player standing behind it. */
    @Override public int sortY() { return (int) Math.round(centerY()); }

    @Override public void draw(Graphics2D g) {
        int cx = (int) Math.round(centerX()), cy = (int) Math.round(centerY());
        int ry = (int) Math.round(RADIUS / 1.4);
        if (age < WARNING_SECONDS) {
            drawWarning(g, cx, cy, ry);
            return;
        }
        double since = age - WARNING_SECONDS, k = since / GEYSER_SECONDS;
        // Wet ground and a splash ring spreading from the base.
        int wetAlpha = (int) (150 * (1 - k));
        g.setColor(new Color(40, 120, 128, wetAlpha));
        g.fillOval(cx - RADIUS + 3, cy - ry + 2, (RADIUS - 3) * 2, (ry - 2) * 2);
        int ring = (int) Math.round(8 + 22 * Math.min(1, since / 0.5));
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(220, 255, 248, (int) (220 * Math.max(0, 1 - since / 0.5))));
        g.drawOval(cx - ring, cy - ring / 3, ring * 2, ring * 2 / 3);
        g.setStroke(new BasicStroke(1f));

        // The column: shoots up fast, wobbles, then collapses.
        double rise = since < 0.15 ? since / 0.15 : since < 0.6 ? 1 : Math.max(0, 1 - (since - 0.6) / 0.4);
        int height = (int) Math.round(COLUMN_HEIGHT * rise * (1 + 0.06 * Math.sin(since * 30)));
        if (height > 2) {
            int halfW = 6 + (int) Math.round(Math.sin(since * 24) * 1.2);
            int top = cy - height;
            g.setColor(new Color(42, 150, 165, 215));
            g.fillRoundRect(cx - halfW - 2, top, (halfW + 2) * 2, height + 2, 8, 8);
            g.setColor(new Color(110, 214, 214, 230));
            g.fillRoundRect(cx - halfW, top + 1, halfW * 2, height, 6, 6);
            g.setColor(new Color(214, 255, 248, 230));
            for (int y = top + 3; y < cy - 2; y += 4) {         // streaks rushing upward
                int off = (int) Math.round(Math.sin(y * 0.7 + since * 40) * (halfW - 3));
                g.fillRect(cx + off, y, 1, 2);
            }
            g.setColor(new Color(240, 255, 252, 240));            // foamy crown
            g.fillOval(cx - halfW - 4, top - 4, (halfW + 4) * 2, 8);
            g.setColor(new Color(170, 236, 230, 230));
            g.fillOval(cx - halfW - 1, top - 2, (halfW + 1) * 2, 4);
        }
        // Droplets thrown out from the top, falling back down.
        int dropAlpha = (int) (230 * Math.max(0, 1 - k));
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10 + 0.4;
            double t = Math.min(1, since * 1.3 + (i % 3) * 0.05);
            int dx = (int) Math.round(Math.cos(a) * (6 + 20 * t));
            int dy = (int) Math.round(-COLUMN_HEIGHT * 0.9 * Math.sin(t * Math.PI) * (0.6 + 0.4 * Math.abs(Math.sin(a))) + Math.sin(a) * 6 * t);
            g.setColor(i % 2 == 0 ? new Color(214, 255, 248, dropAlpha) : new Color(93, 202, 199, dropAlpha));
            g.fillRect(cx + dx, cy + dy, 2, 2);
        }
    }

    /** Bubbling sand inside a red danger ring: a geyser is about to burst here. */
    private void drawWarning(Graphics2D g, int cx, int cy, int ry) {
        double pulse = 0.5 + 0.5 * Math.sin(age * 16);
        double urgency = age / WARNING_SECONDS;
        g.setColor(new Color(40, 110, 118, (int) (40 + 60 * urgency)));
        g.fillOval(cx - RADIUS, cy - ry, RADIUS * 2, ry * 2);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(245, 44, 39, (int) (170 + pulse * 85)));
        g.drawOval(cx - RADIUS, cy - ry, RADIUS * 2, ry * 2);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 6; i++) {                               // bubbles popping up faster and faster
            double phase = (age * (2.5 + urgency * 4) + i * 0.37) % 1;
            int bx = cx + (int) Math.round(Math.cos(i * 2.1) * (RADIUS - 8) * (0.3 + 0.7 * ((i * 0.53) % 1)));
            int by = cy + (int) Math.round(Math.sin(i * 2.1) * (ry - 5) * 0.8);
            int r = 1 + (int) Math.round(phase * 2);
            g.setColor(new Color(200, 250, 244, (int) (220 * (1 - phase))));
            g.drawOval(bx - r, by - r, r * 2, r * 2);
        }
        if (urgency > 0.6) {                                        // first spurts just before it blows
            int spurt = (int) Math.round((urgency - 0.6) / 0.4 * 8 * (0.6 + 0.4 * pulse));
            g.setColor(new Color(150, 236, 230, 220));
            g.fillRect(cx - 1, cy - spurt, 3, spurt);
        }
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 7));
        g.setColor(new Color(255, 232, 216));
        String warning = "MOVE!";
        g.drawString(warning, cx - g.getFontMetrics().stringWidth(warning) / 2, cy - ry - 3);
    }
}
