package ecoquest;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Starts Level 3 (Market). Run from the project folder so data/trash/market.csv is found. */
public final class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Game game = new Game(Level.create(MapId.MARKET));
            GamePanel panel = new GamePanel(game);
            JFrame f = new JFrame("Eco Quest | Level 3: Market");
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            panel.requestFocusInWindow();
        });
    }
}
