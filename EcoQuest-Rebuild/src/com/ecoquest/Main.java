package com.ecoquest;

import com.ecoquest.core.Game;
import com.ecoquest.util.Constants;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/** Application entry point: builds the window and starts the game loop. */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(Constants.GAME_TITLE);
            Game game = new Game();

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(true);
            frame.setMinimumSize(new Dimension(640, 360));
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            Rectangle[] windowedBounds = {frame.getBounds()};
            boolean[] fullscreen = {false};
            GraphicsDevice screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            game.setFullscreenToggle(() -> SwingUtilities.invokeLater(() -> {
                if (!fullscreen[0]) {
                    windowedBounds[0] = frame.getBounds();
                    frame.dispose();
                    frame.setUndecorated(true);
                    screen.setFullScreenWindow(frame);
                    frame.setVisible(true);
                    fullscreen[0] = true;
                    game.setFullscreenMode(true);
                } else {
                    screen.setFullScreenWindow(null);
                    frame.dispose();
                    frame.setUndecorated(false);
                    frame.setBounds(windowedBounds[0]);
                    frame.setVisible(true);
                    fullscreen[0] = false;
                    game.setFullscreenMode(false);
                }
                game.requestFocusInWindow();
            }));
            frame.setVisible(true);

            game.requestFocusInWindow();
            game.start();
        });
    }
}
