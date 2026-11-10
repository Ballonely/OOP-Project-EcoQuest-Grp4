package ecoquest;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** In-game HUD, intro card, pause overlay and win/fail screen, drawn in the main project's UI style. */
final class HudPainter {
    private static final Color BAR_BG = new Color(10, 15, 20);
    private final boolean peso = Theme.FONT_HUD.canDisplay('₱');

    String money(int v) {
        return (peso ? "₱" : "P") + v;
    }

    // ------------------------------------------------------------------ HUD

    void hud(Graphics2D g, Game game, int w, int h) {
        Level.Config c = game.level.cfg;
        g.setStroke(new BasicStroke(1));

        // top-left: score + money
        panel(g, 20, 16, 220, 36);
        g.setFont(Theme.FONT_HUD);
        g.setColor(Theme.GOLD);
        g.drawString("SCORE", 34, 40);
        g.setColor(Color.WHITE);
        g.drawString(String.valueOf(game.score), 120, 40);

        panel(g, 20, 58, 220, 36);
        g.setColor(Theme.GOLD);
        g.drawString("MONEY", 34, 82);
        g.setColor(Color.WHITE);
        g.drawString(money(game.money), 120, 82);

        // top-center: area + timer
        int t = (int) Math.ceil(game.timeLeft);
        panel(g, (w - 260) / 2, 16, 260, 54);
        g.setFont(Theme.FONT_SMALL);
        g.setColor(Theme.TEXT_LIGHT);
        centered(g, "AREA: LV 3 TOWN MARKET", w, 36);
        g.setFont(Theme.FONT_MENU);
        g.setColor(t <= 30 ? Theme.HEALTH_RED : Color.WHITE);
        centered(g, String.format("%d:%02d", t / 60, t % 60), w, 60);

        // top-right: HP bar + boss bar
        int barW = 220, barH = 22, barX = w - barW - 20;
        statBar(g, barX, 16, barW, barH, game.health / (double) c.hearts(), Theme.HEALTH_RED,
                "HP " + game.health + "/" + c.hearts());
        statBar(g, barX, 58, barW, barH, 1.0 - (double) game.sorted / c.target(), new Color(217, 119, 6),
                game.level.bossName.toUpperCase() + " " + game.sorted + "/" + c.target());

        // bottom-left: cleanliness
        int by = h - 52;
        panel(g, 20, by, 380, 36);
        g.setFont(Theme.FONT_SMALL);
        g.setColor(Theme.TEXT_DIM);
        g.drawString("CLEANLINESS LEVEL:", 34, by + 23);
        g.setColor(BAR_BG);
        g.fillRect(190, by + 11, 140, 14);
        g.setColor(Theme.TEAL);
        g.fillRect(190, by + 11, (int) (140 * Math.max(0, Math.min(100, game.cleanliness)) / 100.0), 14);
        g.setColor(Theme.PANEL_BORDER);
        g.drawRect(190, by + 11, 140, 14);
        g.setColor(Color.WHITE);
        g.drawString((int) game.cleanliness + "%", 338, by + 23);

        // bottom-right: controls
        String help = "[WASD] MOVE  •  [E] PICK/DROP  •  [P] PAUSE";
        int hw = g.getFontMetrics().stringWidth(help) + 30;
        int hx = w - hw - 20;
        panel(g, hx, by, hw, 36);
        g.setColor(Theme.TEXT_DIM);
        g.drawString(help, hx + 15, by + 23);

        // bottom-center: carried items
        int maxW = hx - 410 - 10;
        String carry = carryText(game, c.capacity(), g, maxW - 30);
        int cw = g.getFontMetrics().stringWidth(carry) + 30;
        panel(g, 410 + (maxW - cw) / 2, by, cw, 36);
        g.setColor(Theme.TEXT_LIGHT);
        g.drawString(carry, 410 + (maxW - cw) / 2 + 15, by + 23);

        // message toast
        if (game.messageTimer > 0 && game.state == Game.State.PLAY) {
            g.setFont(Theme.FONT_HUD);
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(game.message) + 40, ty = by - 52;
            panel(g, (w - tw) / 2, ty, tw, 34);
            g.setColor(game.messageColor);
            g.drawString(game.message, (w - fm.stringWidth(game.message)) / 2, ty + 22);
        }
    }

    private String carryText(Game game, int capacity, Graphics2D g, int maxWidth) {
        StringBuilder sb = new StringBuilder("CARRYING " + game.carried.size() + "/" + capacity);
        if (!game.carried.isEmpty()) {
            List<TrashDef> rev = new ArrayList<>(game.carried);
            Collections.reverse(rev);
            sb.append(":  ");
            for (int i = 0; i < rev.size(); i++) sb.append(i == 0 ? "[" + rev.get(i).name() + "]" : ", " + rev.get(i).name());
        }
        String s = sb.toString();
        FontMetrics fm = g.getFontMetrics();
        while (s.length() > 12 && fm.stringWidth(s) > maxWidth) s = s.substring(0, s.length() - 4) + "...";
        return s;
    }

    private void statBar(Graphics2D g, int x, int y, int w, int h, double frac, Color fill, String label) {
        panel(g, x - 10, y - 6, w + 20, h + 12);
        g.setColor(BAR_BG);
        g.fillRect(x, y, w, h);
        g.setColor(fill);
        g.fillRect(x, y, (int) (w * Math.max(0, Math.min(1, frac))), h);
        g.setColor(Theme.PANEL_BORDER);
        g.drawRect(x, y, w, h);
        g.setColor(Color.WHITE);
        g.setFont(Theme.FONT_SMALL);
        g.drawString(label, x + 6, y + 16);
    }

    // ------------------------------------------------------------------ intro + pause

    void intro(Graphics2D g, Game game, int w, int h) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, w, h);
        int pw = 760, ph = 350, px = (w - pw) / 2, py = (h - ph) / 2;
        panel(g, px, py, pw, ph);
        g.setColor(Theme.TEAL);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(px, py, pw, ph, 4, 4);
        g.setStroke(new BasicStroke(1));

        String[] lines = game.level.briefing();
        int y = py + 66;
        g.setFont(Theme.FONT_TITLE.deriveFont(40f));
        g.setColor(Theme.GOLD);
        centered(g, lines[0], w, y);
        y += 34;
        g.setFont(Theme.FONT_MENU);
        g.setColor(new Color(255, 154, 138));
        centered(g, lines[1], w, y);
        y += 30;
        g.setFont(Theme.FONT_HUD.deriveFont(Font.PLAIN, 15f));
        g.setColor(Theme.TEXT_LIGHT);
        for (int i = 2; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            centered(g, lines[i], w, y);
            y += 24;
        }

        // bin legend
        g.setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD));
        int bw = 120, gap = 14, total = WasteType.values().length * bw + (WasteType.values().length - 1) * gap;
        int bx = (w - total) / 2, byy = py + ph - 90;
        int i = 0;
        for (WasteType t : WasteType.values()) {
            int x = bx + i++ * (bw + gap);
            g.setColor(t.color);
            g.fillRoundRect(x, byy, bw, 28, 4, 4);
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(t.label, x + (bw - fm.stringWidth(t.label)) / 2, byy + 19);
        }

        g.setFont(Theme.FONT_MENU);
        g.setColor((int) (game.time * 2) % 2 == 0 ? Color.WHITE : Theme.TEAL);
        centered(g, "PRESS [ENTER] TO START", w, py + ph - 28);
    }

    void pause(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, w, h);
        g.setFont(Theme.FONT_TITLE.deriveFont(48f));
        g.setColor(Theme.TEAL);
        centered(g, "PAUSED", w, h / 2 - 20);
        g.setFont(Theme.FONT_MENU);
        g.setColor(Theme.TEXT_LIGHT);
        centered(g, "PRESS [P] TO RESUME  •  [R] RESTART  •  [ESC] MAIN MENU", w, h / 2 + 30);
    }

    // ------------------------------------------------------------------ win / fail

    void outcome(Graphics2D g, int w, int h, boolean won, String levelName, int finalScore, int moneyEarned,
                 int levelBonus, int cityHealth, String message, String tip, int selectedAction) {
        double scale = Math.min(w / 1024.0, h / 576.0);
        Graphics2D o = (Graphics2D) g.create();
        o.setColor(new Color(0, 0, 0, 190));
        o.fillRect(0, 0, w, h);
        o.translate((w - 1024 * scale) / 2, (h - 576 * scale) / 2);
        o.scale(scale, scale);

        int px = 216, py = 72, pw = 592, ph = 432;
        Color border = won ? new Color(255, 205, 48) : new Color(255, 82, 99);
        Color panel = won ? new Color(27, 73, 52) : new Color(67, 37, 43);
        o.setColor(new Color(0, 0, 0, 150));
        o.fillRect(px + 6, py + 6, pw, ph);
        o.setColor(border);
        o.fillRect(px, py, pw, ph);
        o.setColor(new Color(20, 23, 24));
        o.fillRect(px + 5, py + 5, pw - 10, ph - 10);
        o.setColor(panel);
        o.fillRect(px + 10, py + 10, pw - 20, ph - 20);
        o.setColor(border);
        o.fillRect(px + 18, py + 18, pw - 36, 3);

        o.setFont(new Font(Font.MONOSPACED, Font.BOLD, 58));
        o.setColor(new Color(20, 20, 20));
        centered(o, won ? "YOU WIN!" : "FAILED", 1024, 163);
        o.setColor(won ? new Color(255, 205, 48) : new Color(255, 85, 100));
        centered(o, won ? "YOU WIN!" : "FAILED", 1024, 157);

        if (won) {
            o.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 43));
            o.setColor(new Color(255, 204, 56));
            centered(o, "★  ★  ★", 1024, 231);
            statRow(o, "Level", levelName, 272);
            statRow(o, "Score", String.valueOf(finalScore), 303);
            statRow(o, "Money earned", money(moneyEarned), 333);
            statRow(o, "Level bonus", "+" + money(levelBonus), 363);
            statRow(o, "City Health", cityHealth + "%", 393);
            healthBar(o, cityHealth, 281, 400, 462);
            buttons(o, new String[]{"NEXT LEVEL", "RETRY", "MENU"}, selectedAction, 258, 435);
        } else {
            o.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
            o.setColor(Color.WHITE);
            centered(o, message, 1024, 207);
            statRow(o, "Level", levelName, 257);
            statRow(o, "Score", String.valueOf(finalScore), 287);
            statRow(o, "City Health", cityHealth + "%", 317);
            healthBar(o, cityHealth, 281, 325, 462);

            o.setColor(new Color(48, 25, 31));
            o.fillRect(272, 360, 480, 45);
            o.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
            o.setColor(new Color(255, 205, 48));
            o.drawString("TIP", 283, 378);
            o.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            o.setColor(new Color(238, 230, 227));
            o.drawString(tip, 283, 397);
            buttons(o, new String[]{"TRY AGAIN", "MENU"}, selectedAction, 345, 435);
        }

        o.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        o.setColor(new Color(205, 211, 199));
        centered(o, "< > SELECT    ENTER CONFIRM    R RETRY    ESC MENU", 1024, 494);
        o.dispose();
    }

    private void statRow(Graphics2D g, String label, String value, int y) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        g.setColor(new Color(228, 233, 223));
        g.drawString(label, 282, y);
        int labelEnd = 282 + g.getFontMetrics().stringWidth(label);
        int valueX = 742 - g.getFontMetrics().stringWidth(value);
        g.setColor(new Color(155, 176, 157));
        for (int x = labelEnd + 12; x < valueX - 10; x += 8) g.fillRect(x, y - 5, 2, 2);
        g.setColor(label.equals("City Health") ? Color.WHITE : new Color(228, 233, 223));
        g.drawString(value, valueX, y);
    }

    private void healthBar(Graphics2D g, int cityHealth, int x, int y, int width) {
        g.setColor(new Color(37, 43, 48));
        g.fillRect(x, y, width, 15);
        g.setColor(new Color(38, 42, 43));
        g.drawRect(x, y, width, 15);
        g.setColor(new Color(66, 196, 101));
        g.fillRect(x + 2, y + 2, (width - 3) * Math.max(0, Math.min(100, cityHealth)) / 100, 11);
    }

    private void buttons(Graphics2D g, String[] labels, int selected, int x, int y) {
        int bw = labels.length == 3 ? 154 : 158, gap = labels.length == 3 ? 22 : 24;
        for (int i = 0; i < labels.length; i++) {
            int bx = x + i * (bw + gap);
            g.setColor(new Color(12, 14, 17));
            g.fillRect(bx - 3, y - 3, bw + 6, 43);
            g.setColor(i == selected ? Color.WHITE : new Color(115, 119, 124));
            g.drawRect(bx - 3, y - 3, bw + 6, 43);
            g.setColor(buttonColor(labels[i]));
            g.fillRect(bx, y, bw, 37);
            g.setColor(new Color(30, 30, 30));
            g.drawRect(bx, y, bw, 37);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(labels[i], bx + (bw - fm.stringWidth(labels[i])) / 2, y + 24);
            if (i == selected) {
                g.setColor(Color.WHITE);
                g.fillPolygon(new int[]{bx - 13, bx - 4, bx - 13}, new int[]{y + 13, y + 19, y + 25}, 3);
            }
        }
    }

    private Color buttonColor(String label) {
        return switch (label) {
            case "NEXT LEVEL" -> new Color(39, 186, 98);
            case "RETRY" -> new Color(45, 99, 190);
            case "TRY AGAIN" -> new Color(239, 79, 76);
            default -> new Color(94, 99, 109);
        };
    }

    private static void centered(Graphics2D g, String text, int w, int y) {
        g.drawString(text, (w - g.getFontMetrics().stringWidth(text)) / 2, y);
    }

    private static void panel(Graphics2D g, int x, int y, int w, int h) {
        MenuScreen.panel(g, x, y, w, h);
    }
}
