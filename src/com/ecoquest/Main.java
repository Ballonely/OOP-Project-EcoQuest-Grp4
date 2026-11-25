package com.ecoquest;

import com.ecoquest.core.Game;
import com.ecoquest.data.UserDatabase;
import com.ecoquest.data.UserDatabase.Account;
import com.ecoquest.ui.AccountScreen;
import com.ecoquest.ui.MusicPlayer;
import com.ecoquest.util.Constants;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;

/** Application entry point: builds the window and starts the game loop. */
public class Main {

    /** Window bounds to restore when leaving full screen. */
    private static Rectangle windowedBounds;
    private static boolean fullScreen;

    public static void main(String[] args) {
        // Developer shortcut: "--boss [level]" (or boss.bat [level]) skips login and menus and starts
        // that level's boss fight right away, as the built-in developer account (nothing is saved).
        // Level: 1-5 or street, park, market, beach, city; Level 1 when left out.
        java.util.List<String> argList = java.util.Arrays.asList(args);
        int bossArg = argList.indexOf("--boss");
        boolean devBoss = bossArg >= 0;
        String devLevel = devBoss && bossArg + 1 < args.length && !args[bossArg + 1].startsWith("--")
                ? args[bossArg + 1] : null;
        SwingUtilities.invokeLater(() -> {
            UserDatabase database = new UserDatabase();
            JFrame frame = new JFrame(Constants.GAME_TITLE
                    + (devBoss ? "  [DEV: boss test - " + Game.devLevelName(devLevel) + "]" : ""));
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            // Resizable so the window can be maximized; the game scales to fit.
            frame.setResizable(true);
            frame.setMinimumSize(new Dimension(Constants.WINDOW_WIDTH / 2, Constants.WINDOW_HEIGHT / 2));
            if (devBoss) {
                Game game = new Game(database, Account.developer());
                game.startDevBossFight(devLevel);
                frame.setContentPane(game);
                SwingUtilities.invokeLater(game::requestFocusInWindow);
                game.start();
            } else {
                MusicPlayer.play(MusicPlayer.Track.LOGIN);
                frame.setContentPane(new AccountScreen(database, account -> {
                    Game game = new Game(database, account);
                    frame.setContentPane(game);
                    frame.revalidate();
                    game.requestFocusInWindow();
                    game.start();
                }));
            }

            // F11 or Alt+Enter toggles full screen from any screen.
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
                if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                boolean toggle = e.getKeyCode() == KeyEvent.VK_F11
                        || (e.getKeyCode() == KeyEvent.VK_ENTER && e.isAltDown());
                if (!toggle) return false;
                toggleFullScreen(frame);
                return true;
            });

            // The maximize button goes straight to borderless full screen.
            frame.addWindowStateListener(e -> {
                boolean maximized = (e.getNewState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
                if (maximized && !fullScreen) SwingUtilities.invokeLater(() -> toggleFullScreen(frame));
            });
            // Remember the last normal window size so leaving full screen restores it.
            frame.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { rememberBounds(frame); }
                @Override public void componentMoved(ComponentEvent e) { rememberBounds(frame); }
            });

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static void rememberBounds(JFrame frame) {
        if (!fullScreen && frame.getExtendedState() == JFrame.NORMAL) windowedBounds = frame.getBounds();
    }

    /**
     * Switches between a normal window and borderless full screen. A maximized
     * undecorated frame is used instead of exclusive full-screen mode because it
     * plays nicer with Alt+Tab and Swing dialogs on Windows.
     */
    private static void toggleFullScreen(JFrame frame) {
        fullScreen = !fullScreen;
        java.awt.Component focusOwner = frame.getMostRecentFocusOwner();
        frame.dispose(); // decoration can only change while the frame is not displayable
        if (fullScreen) {
            frame.setUndecorated(true);
            frame.setBounds(frame.getGraphicsConfiguration().getBounds());
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            frame.setUndecorated(false);
            frame.setExtendedState(JFrame.NORMAL);
            if (windowedBounds != null) frame.setBounds(windowedBounds);
        }
        frame.setVisible(true);
        if (focusOwner != null) focusOwner.requestFocusInWindow();
        else frame.getContentPane().requestFocusInWindow();
    }
}
