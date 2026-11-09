package com.ecoquest.core;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.Point;
import java.util.HashSet;
import java.util.Set;

/**
 * Centralized input state. Listeners only ever push raw events into this
 * class; every other system polls it, which keeps input handling decoupled
 * from game logic and avoids per-keystroke event-thread lag.
 */
public class InputHandler extends MouseAdapter implements KeyListener, MouseMotionListener {

    private final Set<Integer> pressedKeys = new HashSet<>();
    private final Set<Integer> justPressedKeys = new HashSet<>();

    private volatile int mouseX;
    private volatile int mouseY;
    private int clickX;
    private int clickY;
    private boolean mouseClicked;
    private volatile boolean mousePressed;

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (!pressedKeys.contains(code)) {
            justPressedKeys.add(code);
        }
        pressedKeys.add(code);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not used: movement and menu nav rely on keyPressed/keyReleased polling.
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public synchronized void mousePressed(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        clickX = e.getX();
        clickY = e.getY();
        mousePressed = true;
        mouseClicked = true;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        mousePressed = false;
    }

    public boolean isKeyDown(int keyCode) {
        return pressedKeys.contains(keyCode);
    }

    /** True only on the single update tick after the key went down. */
    public boolean isKeyJustPressed(int keyCode) {
        return justPressedKeys.contains(keyCode);
    }

    /** Must be called once per update tick, after all systems have polled input. */
    public void endFrame() {
        justPressedKeys.clear();
    }

    /** Returns and consumes the next mouse click, retaining its original coordinates. */
    public synchronized Point consumeMouseClick() {
        if (!mouseClicked) return null;
        mouseClicked = false;
        return new Point(clickX, clickY);
    }

    public int getMouseX() { return mouseX; }
    public int getMouseY() { return mouseY; }
    public boolean isMousePressed() { return mousePressed; }
}
