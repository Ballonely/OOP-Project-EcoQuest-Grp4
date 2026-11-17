package ecoquest;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashSet;
import java.util.Set;

/** Polled keyboard and mouse state for the menus (same idea as the main project's InputHandler). */
final class InputHandler extends MouseAdapter implements KeyListener, MouseMotionListener {
    private final Set<Integer> justPressed = new HashSet<>();
    private final Set<Integer> down = new HashSet<>();
    private volatile int mouseX, mouseY;
    private int clickX, clickY;
    private boolean clicked;

    @Override
    public void keyPressed(KeyEvent e) {
        if (down.add(e.getKeyCode())) justPressed.add(e.getKeyCode());
    }

    @Override
    public void keyReleased(KeyEvent e) {
        down.remove(e.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseMoved(e);
    }

    @Override
    public synchronized void mousePressed(MouseEvent e) {
        mouseX = clickX = e.getX();
        mouseY = clickY = e.getY();
        clicked = true;
    }

    boolean isKeyJustPressed(int code) {
        return justPressed.contains(code);
    }

    /** Call once per tick after everything has polled. */
    void endFrame() {
        justPressed.clear();
    }

    synchronized Point consumeMouseClick() {
        if (!clicked) return null;
        clicked = false;
        return new Point(clickX, clickY);
    }

    int getMouseX() {
        return mouseX;
    }

    int getMouseY() {
        return mouseY;
    }
}
