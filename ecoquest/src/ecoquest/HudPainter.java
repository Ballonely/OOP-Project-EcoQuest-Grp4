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

    // ------------------------------------------------------------------ HUD (same layout as the main project's Level 1)

    private static void glass(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(15, 20, 25, 190));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(new Color(255, 255, 255, 60));
        g.drawRoundRect(x, y, w, h, 12, 12);
    }

    private static void centeredBox(Graphics2D g, String text, int w, int y, int size, Color color) {
        g.setFont(new Font(Font.DIALOG, Font.BOLD, size));
        int tw = g.getFontMetrics().stringWidth(text), x = (w - tw) / 2;
        g.setColor(new Color(15, 20, 25, 200));
        g.fillRoundRect(x - 12, y - size - 2, tw + 24, size + 12, 10, 10);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    void hud(Graphics2D g, Game game, int w, int h) {
        Level.Config c = game.level.cfg;
        g.setStroke(new BasicStroke(1));
        Font bold = new Font(Font.DIALOG, Font.BOLD, 15);
        g.setFont(bold);

        // top-left: title, money, score, hearts, city health
        String title = "ECO QUEST - Level 3: Town Market";
        int pw = Math.min(Math.max(270, g.getFontMetrics().stringWidth(title) + 24), w - 206);
        glass(g, 12, 12, pw, 112);
        g.setColor(Color.WHITE);
        g.drawString(title, 24, 34);
        g.setColor(new Color(255, 214, 90));
        g.drawString("\u20B1 " + game.money, 24, 56);
        g.setColor(Color.WHITE);
        g.drawString("Score " + game.score, 130, 56);
        for (int i = 0; i < c.hearts(); i++) {
            g.setColor(i < game.health ? new Color(230, 60, 70) : new Color(90, 90, 95));
            g.setFont(new Font(Font.DIALOG, Font.BOLD, 20));
            g.drawString("\u2665", 24 + i * 24, 82);
        }
        g.setFont(bold);
        int pct = (int) Math.max(0, Math.min(100, game.cleanliness));
        g.setColor(Color.WHITE);
        g.drawString("City Health " + pct + "%", 24, 100);
        g.setColor(new Color(60, 60, 65));
        int barW = pw - 36;
        g.fillRect(24, 106, barW, 9);
        g.setColor(pct < 50 ? new Color(220, 150, 50) : new Color(80, 200, 90));
        g.fillRect(24, 106, barW * pct / 100, 9);

        // top-center: boss life (the opposite of City Health)
        int bossPct = 100 - pct;
        int bw = 340, bx = (w - bw) / 2;
        glass(g, bx, 12, bw, 56);
        g.setColor(Color.WHITE);
        g.drawString("THE " + game.level.bossName.toUpperCase(), bx + 14, 34);
        String hp = "HP " + bossPct + "%";
        g.drawString(hp, bx + bw - 14 - g.getFontMetrics().stringWidth(hp), 34);
        g.setColor(new Color(60, 60, 65));
        g.fillRect(bx + 14, 43, bw - 28, 12);
        g.setColor(new Color(220, 60, 70));
        g.fillRect(bx + 14, 43, (bw - 28) * bossPct / 100, 12);

        // top-right: litter + timer
        int t = (int) Math.ceil(game.timeLeft);
        glass(g, w - 182, 12, 170, 58);
        g.setColor(Color.WHITE);
        g.drawString("Litter left: " + game.items.size(), w - 168, 34);
        g.setColor(t <= 30 ? new Color(255, 110, 110) : Color.WHITE);
        g.drawString(String.format("Time %d:%02d", t / 60, t % 60), w - 168, 58);

        // bottom-left: bag (the item on the right is the next one thrown)
        int cap = c.capacity();
        glass(g, 12, h - 74, 24 + cap * 36, 62);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        g.drawString("BAG  (E = pick up / throw)", 24, h - 56);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        List<TrashDef> bag = new ArrayList<>(game.carried);
        for (int i = 0; i < cap; i++) {
            int sx = 24 + i * 36, sy = h - 48;
            TrashDef d = i < bag.size() ? bag.get(i) : null;
            g.setColor(d == null ? new Color(50, 55, 60) : d.type().color);
            g.fillRect(sx, sy, 30, 26);
            if (d != null) {
                g.drawImage(Sprites.trashIcon(d), sx + 3, sy + 1, 24, 24, null);
                if (i == bag.size() - 1) {
                    g.setColor(Color.WHITE);
                    g.drawRect(sx - 1, sy - 1, 31, 27);
                }
            }
        }

        // bottom-right: controls
        g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        g.setColor(new Color(255, 255, 255, 220));
        String help = "WASD / Arrows: move   E: pick up / throw   P: pause   R: restart";
        g.drawString(help, w - g.getFontMetrics().stringWidth(help) - 12, h - 14);

        // context hint + message
        String hint = null;
        if (game.hintBin != null && !game.carried.isEmpty()) {
            hint = "E: Throw " + game.carried.peekLast().name() + " in the " + game.hintBin.label + " bin";
        } else if (game.hintItem != null) {
            hint = "E: pick up " + game.hintItem.def.name();
        }
        if (hint != null && game.state == Game.State.PLAY) centeredBox(g, hint, w, h - 96, 15, Color.WHITE);
        if (game.messageTimer > 0 && game.state == Game.State.PLAY) centeredBox(g, game.message, w, 100, 16, game.messageColor);
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
