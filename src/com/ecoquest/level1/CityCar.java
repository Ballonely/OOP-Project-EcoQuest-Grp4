package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * A car driving along a fixed route in Level 5. The route is a polyline in world pixels that
 * starts and ends off-screen; when the car reaches the end it re-enters from the start.
 * The sprite faces east and is drawn rotated to the current heading at 1/4 scale
 * (sprites are cut from the 2048x1152 map, the world is 512x288).
 *
 * Cars never touch each other:
 *  - the roundabout is a shared zone that only one car may use at a time; a car waits at a
 *    stop line before it until the zone is free, and frees it once it is well past the exit;
 *  - every step is checked first, and a car does not move if its body would touch another car.
 */
public final class CityCar extends Entity {
    /** Half length / half width of the car body in world pixels. */
    public static final double HALF_LENGTH = 14, HALF_WIDTH = 6.5;
    private static final double SPRITE_SCALE = 0.25;
    private static final double ACCELERATION = 60;
    /** Gap between the stop line and the roundabout entry, so a waiting car stays clear of the circle. */
    private static final double STOP_GAP = 30;
    /** How far before the stop line a car asks for the roundabout, so it rarely has to stop. */
    private static final double REQUEST_DISTANCE = 24;
    /** How far past the roundabout exit a car must be before the next car may enter. */
    private static final double CLEAR_DISTANCE = 32;
    /** Extra space kept between car bodies. */
    private static final double SAFETY_MARGIN = 3;

    /** The single-occupancy roundabout shared by all cars of a level. */
    public static final class Roundabout {
        private CityCar occupant;
        void clear() { occupant = null; }
    }

    private final double[][] route;
    private final double[] cumulative;
    private final BufferedImage sprite;
    private final double cruiseSpeed;
    private final Roundabout roundabout;
    private final double arcStart, arcEnd;
    private double distance, speed, heading, cx, cy;

    /**
     * @param arcStartIndex index of the route point where the car enters the roundabout
     * @param arcEndIndex   index of the route point where the car leaves it
     */
    public CityCar(double[][] route, int arcStartIndex, int arcEndIndex, Roundabout roundabout,
                   BufferedImage sprite, double cruiseSpeed, double startFraction) {
        super(0, 0, 14, 14);
        this.route = route;
        this.sprite = sprite;
        this.cruiseSpeed = cruiseSpeed;
        this.roundabout = roundabout;
        this.speed = cruiseSpeed;
        cumulative = new double[route.length];
        for (int i = 1; i < route.length; i++)
            cumulative[i] = cumulative[i - 1] + Math.hypot(route[i][0] - route[i - 1][0], route[i][1] - route[i - 1][1]);
        arcStart = cumulative[arcStartIndex];
        arcEnd = cumulative[arcEndIndex];
        distance = cumulative[cumulative.length - 1] * startFraction;
        place(distance);
        cx = pose[0]; cy = pose[1]; heading = pose[2];
        syncBox();
    }

    private final double[] pose = new double[3];

    public void update(double dt, List<CityCar> traffic) {
        double stopLine = arcStart - STOP_GAP;
        boolean holding = roundabout.occupant == this;

        // Leave the roundabout zone once well past the exit.
        if (holding && distance > arcEnd + CLEAR_DISTANCE) {
            roundabout.occupant = null;
            holding = false;
        }
        // Ask for the roundabout shortly before the stop line.
        boolean approaching = distance < arcStart && distance > stopLine - REQUEST_DISTANCE;
        if (!holding && approaching && roundabout.occupant == null) {
            roundabout.occupant = this;
            holding = true;
        }
        boolean mustWait = approaching && !holding;

        double target = mustWait ? 0 : cruiseSpeed;
        if (speed < target) speed = Math.min(target, speed + ACCELERATION * dt);
        else speed = Math.max(target, speed - ACCELERATION * 3 * dt);

        double next = distance + speed * dt;
        if (mustWait && distance <= stopLine) next = Math.min(next, stopLine);   // stop exactly at the line
        if (mustWait && distance > stopLine) next = distance;                      // already past it: hold still

        double total = cumulative[cumulative.length - 1];
        if (next >= total) next -= total;

        // Only move if the new position keeps clear of every other car.
        place(next);
        for (CityCar other : traffic) {
            if (other != this && bodiesTouch(pose[0], pose[1], pose[2], other)) {
                speed = 0;
                return;
            }
        }
        distance = next;
        cx = pose[0]; cy = pose[1]; heading = pose[2];
        syncBox();
    }

    /** Separating-axis test between this car at a candidate pose and another car's current body. */
    private static boolean bodiesTouch(double ax, double ay, double aHeading, CityCar b) {
        double hl = HALF_LENGTH + SAFETY_MARGIN / 2, hw = HALF_WIDTH + SAFETY_MARGIN / 2;
        double[][] axes = {
            {Math.cos(aHeading), Math.sin(aHeading)}, {-Math.sin(aHeading), Math.cos(aHeading)},
            {Math.cos(b.heading), Math.sin(b.heading)}, {-Math.sin(b.heading), Math.cos(b.heading)}
        };
        double dx = b.cx - ax, dy = b.cy - ay;
        for (double[] axis : axes) {
            double gap = Math.abs(dx * axis[0] + dy * axis[1]);
            double ra = hl * Math.abs(axes[0][0] * axis[0] + axes[0][1] * axis[1])
                      + hw * Math.abs(axes[1][0] * axis[0] + axes[1][1] * axis[1]);
            double rb = hl * Math.abs(axes[2][0] * axis[0] + axes[2][1] * axis[1])
                      + hw * Math.abs(axes[3][0] * axis[0] + axes[3][1] * axis[1]);
            if (gap > ra + rb) return false;
        }
        return true;
    }

    /** True when two cars' bodies overlap right now (used by tests). */
    public boolean touching(CityCar other) {
        return bodiesTouch(cx, cy, heading, other);
    }

    /** True when a point (the player's feet) touches the car body. */
    public boolean hits(double px, double py, double margin) {
        double dx = px - cx, dy = py - cy;
        double along = dx * Math.cos(heading) + dy * Math.sin(heading);
        double across = -dx * Math.sin(heading) + dy * Math.cos(heading);
        return Math.abs(along) < HALF_LENGTH + margin && Math.abs(across) < HALF_WIDTH + margin;
    }

    /** Computes the pose (x, y, heading) at a distance along the route into {@link #pose}. */
    private void place(double d) {
        int i = 1;
        while (i < cumulative.length - 1 && cumulative[i] < d) i++;
        double segment = cumulative[i] - cumulative[i - 1];
        double t = segment <= 0 ? 0 : (d - cumulative[i - 1]) / segment;
        double[] a = route[i - 1], b = route[i];
        pose[0] = a[0] + (b[0] - a[0]) * t;
        pose[1] = a[1] + (b[1] - a[1]) * t;
        pose[2] = Math.atan2(b[1] - a[1], b[0] - a[0]);
    }

    private void syncBox() {
        x = cx - w / 2.0;
        y = cy - h / 2.0;
    }

    @Override public double centerX() { return cx; }
    @Override public double centerY() { return cy; }

    @Override
    public void draw(Graphics2D g) {
        Graphics2D carGraphics = (Graphics2D) g.create();
        carGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        carGraphics.translate(cx, cy);
        carGraphics.rotate(heading);
        carGraphics.scale(SPRITE_SCALE, SPRITE_SCALE);
        carGraphics.drawImage(sprite, -sprite.getWidth() / 2, -sprite.getHeight() / 2, null);
        carGraphics.dispose();
    }
}
