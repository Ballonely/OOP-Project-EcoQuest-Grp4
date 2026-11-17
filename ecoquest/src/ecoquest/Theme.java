package ecoquest;

import java.awt.Color;
import java.awt.Font;

/** UI colours, fonts and window size. Same values as the main project's util/Constants. */
final class Theme {
    private Theme() {
    }

    static final int WINDOW_WIDTH = 1280;
    static final int WINDOW_HEIGHT = 720;
    static final String GAME_TITLE = "ECO QUEST";
    static final String VERSION = "v1.0.4 ARCADE";

    static final Color BG_DARK = new Color(15, 23, 32);
    static final Color PANEL_NAVY = new Color(30, 41, 59, 235);
    static final Color PANEL_BORDER = new Color(71, 85, 105);
    static final Color TEAL = new Color(45, 212, 165);
    static final Color TEAL_DARK = new Color(21, 128, 90);
    static final Color GOLD = new Color(250, 204, 21);
    static final Color CREAM = new Color(253, 245, 219);
    static final Color TEXT_LIGHT = new Color(226, 232, 240);
    static final Color TEXT_DIM = new Color(148, 163, 184);
    static final Color HEALTH_RED = new Color(239, 68, 68);
    static final Color SKY_BLUE = new Color(96, 190, 230);

    static final Font FONT_TITLE = new Font("Monospaced", Font.BOLD, 64);
    static final Font FONT_MENU = new Font("Monospaced", Font.BOLD, 20);
    static final Font FONT_HUD = new Font("Monospaced", Font.BOLD, 16);
    static final Font FONT_SMALL = new Font("Monospaced", Font.PLAIN, 13);
    static final Font FONT_TAGLINE = new Font("Monospaced", Font.BOLD, 16);
}
