package com.ecoquest.level1;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * A stalled car from the Gridlock Golem's traffic jam. A dashed hazard outline marks its spot, then
 * it skids in and parks there as a solid wall for {@value #STAND} s, hazard lights blinking, before
 * it fades away. The golem's charge smashes it and sends it flying.
 */
final class JamCar extends Prop implements TimedProp {
    static final double WARN = 0.9, SLIDE = 0.35, STAND = 7, LEAVE = 0.6;
    static final double LIFE = WARN + SLIDE + STAND + LEAVE;
    private static final double SPRITE_SCALE = 0.25, SLIDE_DISTANCE = 34, WRECK_FLY = 0.8;

    private final BufferedImage sprite;
    private final double cx, cy, heading;
    private double age, wreckAge, flyX, flyY, flyVX, flyVY, spin;
    private boolean solid, wrecked, arrivalReported;

    /** {@code horizontal}: parked along a street running east-west, otherwise north-south. */
    JamCar(double cx, double cy, boolean horizontal, boolean flipped, BufferedImage sprite) {
        super(cx - (horizontal ? 14 : 6.5), cy - (horizontal ? 6.5 : 14), horizontal ? 28 : 13, horizontal ? 13 : 28, false);
        this.cx = cx;
        this.cy = cy;
        this.heading = (horizontal ? 0 : Math.PI / 2) + (flipped ? Math.PI : 0);
        this.sprite = sprite;
    }

    /** The parked footprint, whether or not it is solid yet. */
    static Rectangle2D.Double footprint(double cx, double cy, boolean horizontal) {
        return horizontal ? new Rectangle2D.Double(cx - 14, cy - 6.5, 28, 13) : new Rectangle2D.Double(cx - 6.5, cy - 14, 13, 28);
    }

    @Override public Rectangle2D.Double collisionBox() { return solid && !wrecked ? bounds() : null; }

    @Override public boolean update(double dt) {
        age += dt;
        if (wrecked) {
            wreckAge += dt;
            flyX += flyVX * dt;
            flyY += flyVY * dt;
            double drag = Math.pow(0.15, dt);
            flyVX *= drag;
            flyVY *= drag;
            return wreckAge < WRECK_FLY;
        }
        if (solid && age >= WARN + SLIDE + STAND) solid = false;   // drives off: walk through it as it fades
        return age < LIFE;
    }

    /** True from the moment it has skidded into place until it starts to leave. */
    boolean isParked() { return !wrecked && age >= WARN + SLIDE && age < WARN + SLIDE + STAND; }
    /** True once it has arrived (it can be smashed from then on). */
    boolean hasArrived() { return !wrecked && age >= WARN + SLIDE * 0.5; }
    boolean isSolid() { return solid; }
    void solidify() { if (isParked()) solid = true; }
    boolean isWrecked() { return wrecked; }
    /** True once it has run its course (driven off or flown away) and left the level. */
    boolean isGone() { return wrecked ? wreckAge >= WRECK_FLY : age >= LIFE; }
    /** True once, the moment it skids into its spot. */
    boolean consumeArrival() {
        if (arrivalReported || wrecked || age < WARN + SLIDE * 0.6) return false;
        arrivalReported = true;
        return true;
    }

    /** Smashed by the golem's charge: it flies off spinning along (pushX, pushY). */
    void wreck(double pushX, double pushY) {
        if (wrecked) return;
        wrecked = true;
        solid = false;
        flyVX = pushX;
        flyVY = pushY;
        spin = (pushX + pushY >= 0 ? 1 : -1) * 12;
    }

    @Override public double centerX() { return cx; }
    @Override public double centerY() { return cy; }

    @Override public void draw(Graphics2D g) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (!wrecked && age < WARN) {
            drawWarning(gg);
            gg.dispose();
            return;
        }
        double px = cx, py = cy, rot = heading, alpha = 1;
        if (wrecked) {
            px += flyX;
            py += flyY;
            rot += spin * wreckAge;
            alpha = Math.max(0, 1 - wreckAge / WRECK_FLY);
        } else if (age < WARN + SLIDE) {       // skids in backwards-first from up the street
            double k = PixelCanvas.easeOut((age - WARN) / SLIDE);
            double back = (1 - k) * SLIDE_DISTANCE;
            px -= Math.cos(heading) * back;
            py -= Math.sin(heading) * back;
            rot += (1 - k) * 0.35;
        } else if (age >= WARN + SLIDE + STAND) {
            alpha = Math.max(0, 1 - (age - WARN - SLIDE - STAND) / LEAVE);
        }
        if (alpha < 1) gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        gg.setColor(new Color(0, 0, 0, 60));
        gg.fillOval((int) Math.round(px - 13), (int) Math.round(py - 3), 26, 9);
        gg.translate(px, py);
        gg.rotate(rot);
        gg.scale(SPRITE_SCALE, SPRITE_SCALE);
        gg.drawImage(sprite, -sprite.getWidth() / 2, -sprite.getHeight() / 2, null);
        gg.scale(1 / SPRITE_SCALE, 1 / SPRITE_SCALE);
        if (isParked() && ((int) (age * 3)) % 2 == 0) {      // hazard lights blinking at the corners
            gg.setColor(new Color(0xFFB030));
            gg.fillRect(-14, -6, 2, 2);
            gg.fillRect(-14, 4, 2, 2);
            gg.fillRect(12, -6, 2, 2);
            gg.fillRect(12, 4, 2, 2);
        }
        gg.dispose();
    }

    /** A dashed yellow outline of the car with a warning mark where it is about to skid in. */
    private void drawWarning(Graphics2D g) {
        double k = age / WARN, pulse = 0.5 + 0.5 * Math.sin(age * 20);
        Rectangle2D.Double r = bounds();
        g.setColor(new Color(255, 190, 40, (int) (30 + 60 * k)));
        g.fillRect((int) r.x, (int) r.y, (int) r.width, (int) r.height);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {3, 2}, (float) (age * 12)));
        g.setColor(new Color(255, 210, 60, (int) (140 + 110 * pulse)));
        g.drawRect((int) r.x, (int) r.y, (int) r.width - 1, (int) r.height - 1);
        int mx = (int) Math.round(cx), my = (int) Math.round(cy);
        g.setColor(new Color(40, 30, 10, (int) (160 + 80 * pulse)));
        g.fillRect(mx, my - 3, 1, 4);
        g.fillRect(mx, my + 2, 1, 1);
    }
}
