package com.ecoquest;

import com.ecoquest.core.Game;
import com.ecoquest.util.Constants;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Application entry point: builds the window and starts the game loop. */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(Constants.GAME_TITLE);
            Game game = new Game();

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            game.requestFocusInWindow();
            game.start();
        });
    }
}
