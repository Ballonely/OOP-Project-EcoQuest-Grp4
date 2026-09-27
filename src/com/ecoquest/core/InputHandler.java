package com.ecoquest.core;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
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

    private int mouseX;
    private int mouseY;
    private boolean mouseClicked;
    private boolean mousePressed;

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
    public void mousePressed(MouseEvent e) {
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
        mouseClicked = false;
    }

    public int getMouseX() { return mouseX; }
    public int getMouseY() { return mouseY; }
    public boolean isMouseClicked() { return mouseClicked; }
    public boolean isMousePressed() { return mousePressed; }
}
