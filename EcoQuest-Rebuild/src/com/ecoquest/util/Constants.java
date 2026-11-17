package com.ecoquest.util;

import java.awt.Color;
import java.awt.Font;

/**
 * Centralized, immutable configuration: window sizing, the color palette
 * reverse-engineered from the reference "Eco Quest" UI mockup, and the
 * fonts used across menus and HUD.
 */
public final class Constants {

    private Constants() { /* static holder, no instances */ }

    // ----- Window / loop -----
    public static final int WINDOW_WIDTH = 1250;
    public static final int WINDOW_HEIGHT = 780;
    public static final int FPS = 60;
    public static final String GAME_TITLE = "ECO QUEST";
    public static final String VERSION = "v1.0.4 ARCADE";

    // ----- Palette (sampled from reference art) -----
    public static final Color COLOR_BG_DARK      = new Color(15, 23, 32);
    public static final Color COLOR_PANEL_NAVY   = new Color(30, 41, 59, 235);
    public static final Color COLOR_PANEL_BORDER = new Color(71, 85, 105);
    public static final Color COLOR_TEAL         = new Color(45, 212, 165);
    public static final Color COLOR_TEAL_DARK    = new Color(21, 128, 90);
    public static final Color COLOR_GOLD         = new Color(250, 204, 21);
    public static final Color COLOR_CREAM        = new Color(253, 245, 219);
    public static final Color COLOR_TEXT_LIGHT   = new Color(226, 232, 240);
    public static final Color COLOR_TEXT_DIM     = new Color(148, 163, 184);
    public static final Color COLOR_HEALTH_RED   = new Color(239, 68, 68);
    public static final Color COLOR_SKY_BLUE     = new Color(96, 190, 230);

    // ----- Typography -----
    public static final Font FONT_TITLE   = new Font("Monospaced", Font.BOLD, 64);
    public static final Font FONT_MENU    = new Font("Monospaced", Font.BOLD, 20);
    public static final Font FONT_HUD     = new Font("Monospaced", Font.BOLD, 16);
    public static final Font FONT_SMALL   = new Font("Monospaced", Font.PLAIN, 13);
    public static final Font FONT_TAGLINE = new Font("Monospaced", Font.BOLD, 16);
}
