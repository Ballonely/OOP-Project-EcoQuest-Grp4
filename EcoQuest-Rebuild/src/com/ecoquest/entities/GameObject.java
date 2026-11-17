package com.ecoquest.entities;

import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Base type for everything that lives in the game world. Holds the
 * attributes every entity needs (position, size, velocity, alive flag)
 * and defines the update/render contract every subclass must implement.
 */
public abstract class GameObject {

    protected double x;
    protected double y;
    protected double velX;
    protected double velY;
    protected int width;
    protected int height;
    protected boolean alive = true;

    protected GameObject(double x, double y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Advance this object's state by deltaTime seconds. */
    public abstract void update(double deltaTime);

    /** Draw this object using the supplied graphics context. */
    public abstract void render(Graphics2D g);

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, width, height);
    }

    public boolean intersects(GameObject other) {
        return getBounds().intersects(other.getBounds());
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }
}
