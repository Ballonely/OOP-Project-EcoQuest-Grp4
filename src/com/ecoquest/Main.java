package com.ecoquest;

import com.ecoquest.core.Game;
import com.ecoquest.data.UserDatabase;
import com.ecoquest.data.UserDatabase.Account;
import com.ecoquest.ui.AccountScreen;
import com.ecoquest.util.Constants;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Application entry point: builds the window and starts the game loop. */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UserDatabase database = new UserDatabase();
            JFrame frame = new JFrame(Constants.GAME_TITLE);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.setContentPane(new AccountScreen(database, account -> {
                Game game = new Game(database, account);
                frame.setContentPane(game);
                frame.pack();
                game.requestFocusInWindow();
                game.start();
            }));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
