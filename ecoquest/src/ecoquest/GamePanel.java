package ecoquest;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Draws the level (640x360 world scaled up with crisp pixels) plus HUD and menus, and reads the keyboard. */
public final class GamePanel extends JPanel {
    private final Game game;
    private final BufferedImage background;
    private final BufferedImage frame = new BufferedImage(Level.W, Level.H, BufferedImage.TYPE_INT_RGB);
    private long last = System.nanoTime();
    private double u = 2; // screen pixels per world pixel
    private int ox, oy;
    private boolean peso = true;

    public GamePanel(Game game) {
        this.game = game;
        this.background = game.level.paintBackground();
        setPreferredSize(new Dimension(1280, 720));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                key(e.getKeyCode(), true);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                key(e.getKeyCode(), false);
            }
        });
        new Timer(16, e -> {
            long now = System.nanoTime();
            double dt = Math.min(0.05, (now - last) / 1e9);
            last = now;
            game.update(dt);
            repaint();
        }).start();
    }

    private void key(int code, boolean down) {
        switch (code) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> game.keys[0] = down;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> game.keys[1] = down;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> game.keys[2] = down;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> game.keys[3] = down;
            default -> {
            }
        }
        if (!down) return;
        switch (code) {
            case KeyEvent.VK_E, KeyEvent.VK_SPACE -> game.act();
            case KeyEvent.VK_ENTER -> {
                if (game.state == Game.State.INTRO) game.start();
                else if (game.state == Game.State.WON || game.state == Game.State.LOST) game.restart();
            }
            case KeyEvent.VK_P, KeyEvent.VK_ESCAPE -> game.togglePause();
            case KeyEvent.VK_R -> {
                if (game.state != Game.State.INTRO) game.restart();
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics gr) {
        super.paintComponent(gr);
        render(frame.createGraphics());
        Graphics2D g = (Graphics2D) gr;
        int w = getWidth(), h = getHeight();
        u = Math.min(w / (double) Level.W, h / (double) Level.H);
        int sw = (int) (Level.W * u), sh = (int) (Level.H * u);
        ox = (w - sw) / 2;
        oy = (h - sh) / 2;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(frame, ox, oy, sw, sh, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        overlay(g);
    }

    /** Draws everything that lives in the world. Also used by the headless screenshot test. */
    void render(Graphics2D g) {
        g.drawImage(background, 0, 0, null);
        game.level.paintDecor(g, game.cleanliness, game.time);

        // trash on the ground, back to front
        List<Game.Item> ground = new ArrayList<>(game.items);
        ground.sort(Comparator.comparingDouble(i -> i.y));
        for (Game.Item it : ground) {
            int bob = (int) Math.round(Math.sin(game.time * 3 + it.phase));
            g.setColor(new Color(0, 0, 0, 55));
            g.fillOval((int) it.x - 6, (int) it.y + 2, 12, 5);
            g.drawImage(Sprites.trashIcon(it.def), (int) it.x - 8, (int) it.y - 10 + bob, null);
        }
        if (game.hintItem != null && game.state == Game.State.PLAY) {
            int x = (int) game.hintItem.x, y = (int) game.hintItem.y - 18 + (int) (Math.sin(game.time * 8) * 2);
            g.setColor(Color.WHITE);
            g.fillPolygon(new int[]{x - 4, x + 4, x}, new int[]{y, y, y + 5}, 3);
        }

        // bins
        for (var e : game.level.bins().entrySet()) {
            double f = game.binFlash.get(e.getKey());
            Sprites.bin(g, e.getValue(), e.getKey(), e.getKey() == game.hintBin, f > 0 ? 1 : f < 0 ? -1 : 0);
        }

        // y-sorted characters
        record Drawable(double y, Runnable draw) {
        }
        List<Drawable> list = new ArrayList<>();
        for (Game.Npc n : game.npcs) {
            list.add(new Drawable(n.y, () -> {
                Sprites.person(g, (int) n.x, (int) n.y, n.facing, n.frame, n.dx != 0 || n.dy != 0, n.hat, n.tunic, new Color(0xe8b98f));
                if (game.cleanliness >= 60 && game.state == Game.State.PLAY && (int) (game.time + n.x) % 6 < 2) {
                    g.setColor(new Color(0xff6b8a));
                    g.fillRect((int) n.x - 3, (int) n.y - 32, 3, 3);
                    g.fillRect((int) n.x + 1, (int) n.y - 32, 3, 3);
                    g.fillRect((int) n.x - 2, (int) n.y - 29, 5, 2);
                    g.fillRect((int) n.x - 1, (int) n.y - 27, 3, 1);
                }
            }));
        }
        for (Game.Cart c : game.carts) list.add(new Drawable(c.lane.y(), () -> drawCart(g, c)));
        if (game.bossAlive()) {
            list.add(new Drawable(game.level.pile().y, () ->
                    Sprites.wasteKing(g, game.level.pile().x, game.level.pile().y + 6, game.bossSize(), game.bossShake, game.time)));
        }
        list.add(new Drawable(game.py, () -> {
            Sprites.person(g, (int) game.px, (int) game.py, game.facing, game.walkFrame, game.moving,
                    new Color(0x3fa34d), new Color(0x7a9a3a), new Color(0xf0c49a));
            int i = 0;
            for (TrashDef d : game.carried) {
                g.drawImage(Sprites.trashIcon(d), (int) game.px - 8, (int) game.py - 40 - i * 9, null);
                i++;
            }
        }));
        list.sort(Comparator.comparingDouble(Drawable::y));
        for (Drawable d : list) d.draw().run();
        g.dispose();
    }

    private void drawCart(Graphics2D g, Game.Cart c) {
        Sprites.cart(g, (int) c.x, c.lane.y(), c.dir);
    }

    // ------------------------------------------------------------------ HUD (screen space)

    private int px(double worldX) {
        return ox + (int) (worldX * u);
    }

    private int py(double worldY) {
        return oy + (int) (worldY * u);
    }

    private Font font(double size, int style) {
        return new Font(Font.MONOSPACED, style, Math.max(8, (int) (size * u)));
    }

    private void text(Graphics2D g, String s, int x, int y, Color c) {
        g.setColor(new Color(0, 0, 0, 170));
        g.drawString(s, x + 2, y + 2);
        g.setColor(c);
        g.drawString(s, x, y);
    }

    private void centered(Graphics2D g, String s, int cx, int y, Color c) {
        FontMetrics fm = g.getFontMetrics();
        text(g, s, cx - fm.stringWidth(s) / 2, y, c);
    }

    private void overlay(Graphics2D g) {
        Game.State st = game.state;
        peso = font(8, Font.BOLD).canDisplay('\u20b1');

        // world labels on stalls and bins
        g.setFont(font(6, Font.BOLD));
        for (Level.Label l : game.level.labels()) {
            Rectangle a = l.area();
            int tw = g.getFontMetrics().stringWidth(l.text()), cx = px(a.getCenterX()), cy = py(a.y + a.height * 0.62);
            int ph = (int) (9 * u), pw = tw + (int) (8 * u);
            g.setColor(new Color(0x4a2e1a));
            g.fillRoundRect(cx - pw / 2 - 2, cy - ph / 2 - 2, pw + 4, ph + 4, 8, 8);
            g.setColor(new Color(0xf3e3bd));
            g.fillRoundRect(cx - pw / 2, cy - ph / 2, pw, ph, 6, 6);
            g.setColor(new Color(0x3a2616));
            g.drawString(l.text(), cx - tw / 2, cy + g.getFontMetrics().getAscent() / 2 - 1);
        }
        g.setFont(font(6, Font.BOLD));
        for (var e : game.level.bins().entrySet()) {
            Rectangle b = e.getValue();
            centered(g, e.getKey().label, px(b.getCenterX()), py(b.y + b.height * 0.93), Color.WHITE);
        }
        // floating +5 / WRONG
        g.setFont(font(7, Font.BOLD));
        for (Game.Pop p : game.pops) {
            int a = (int) (255 * Math.max(0, 1 - p.age / 1.2));
            centered(g, p.text, px(p.x), py(p.y), new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), Math.max(40, a)));
        }

        hud(g);
        if (st == Game.State.INTRO) intro(g);
        else if (st == Game.State.PAUSED) banner(g, "PAUSED", "P to continue   R to restart");
        else if (st == Game.State.WON) results(g, true);
        else if (st == Game.State.LOST) results(g, false);
    }

    private void bar(Graphics2D g, int x, int y, int w, int h, double frac, Color fill, Color back) {
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(x - 3, y - 3, w + 6, h + 6, 8, 8);
        g.setColor(back);
        g.fillRect(x, y, w, h);
        g.setColor(fill);
        g.fillRect(x, y, (int) (w * Math.max(0, Math.min(1, frac))), h);
        g.setColor(new Color(255, 255, 255, 60));
        g.fillRect(x, y, (int) (w * Math.max(0, Math.min(1, frac))), Math.max(1, h / 3));
    }

    private void heart(Graphics2D g, int x, int y, int s, boolean full) {
        Path2D p = new Path2D.Double();
        p.moveTo(x + s / 2.0, y + s);
        p.curveTo(x - s * 0.35, y + s * 0.45, x + s * 0.15, y - s * 0.3, x + s / 2.0, y + s * 0.28);
        p.curveTo(x + s * 0.85, y - s * 0.3, x + s * 1.35, y + s * 0.45, x + s / 2.0, y + s);
        g.setColor(full ? new Color(0xe5384a) : new Color(0x3a2a2e));
        g.fill(p);
        g.setColor(new Color(0x1b0f12));
        g.setStroke(new BasicStroke(2f));
        g.draw(p);
    }

    private String money(int v) {
        return (peso ? "\u20b1" : "P") + v;
    }

    private void hud(Graphics2D g) {
        Level.Config c = game.level.cfg;
        int pad = (int) (8 * u), top = oy + pad;
        int left = ox + pad, right = ox + (int) (Level.W * u) - pad;

        // hearts
        int hs = (int) (9 * u);
        for (int i = 0; i < c.hearts(); i++) heart(g, left + i * (hs + 6), top, hs, i < game.health);
        g.setFont(font(6, Font.BOLD));
        text(g, "Wrong bins " + game.mistakes + "/" + c.hearts(), left, top + hs + (int) (10 * u), new Color(0xffd0d0));

        // timer + level name
        g.setFont(font(10, Font.BOLD));
        int t = (int) Math.ceil(game.timeLeft);
        String clock = String.format("%d:%02d", t / 60, t % 60);
        centered(g, clock, ox + (int) (Level.W * u / 2), top + (int) (10 * u), t <= 30 ? new Color(0xff6b6b) : Color.WHITE);
        g.setFont(font(5.5, Font.BOLD));
        centered(g, "LEVEL 3  MARKET", ox + (int) (Level.W * u / 2), top + (int) (19 * u), new Color(0xd8e8ff));

        // money
        g.setFont(font(10, Font.BOLD));
        String m = money(game.money);
        text(g, m, right - g.getFontMetrics().stringWidth(m), top + (int) (10 * u), new Color(0xffe27a));
        g.setFont(font(5.5, Font.BOLD));
        String sc = "Score " + game.score;
        text(g, sc, right - g.getFontMetrics().stringWidth(sc), top + (int) (19 * u), Color.WHITE);

        // cleanliness (left) and boss (right)
        int by = top + (int) (30 * u), bw = (int) (150 * u), bh = (int) (8 * u);
        g.setFont(font(5.5, Font.BOLD));
        text(g, "City cleanliness " + (int) game.cleanliness + "%", left, by - 4, Color.WHITE);
        Color cc = game.cleanliness < 45 ? new Color(0xc9803a) : game.cleanliness < 80 ? new Color(0xd6c23a) : new Color(0x56c46a);
        bar(g, left, by, bw, bh, game.cleanliness / 100.0, cc, new Color(0x2a2a30));

        int rx = right - bw;
        String boss = game.level.bossName.toUpperCase() + "  sorted " + game.sorted + "/" + c.target();
        text(g, boss, rx, by - 4, new Color(0xffb0a0));
        bar(g, rx, by, bw, bh, 1.0 - (double) game.sorted / c.target(), new Color(0xc0392b), new Color(0x2a2a30));

        // bottom: message + carried list
        int bottom = oy + (int) (Level.H * u) - pad;
        g.setFont(font(5.5, Font.BOLD));
        StringBuilder sb = new StringBuilder("Carrying " + game.carried.size() + "/" + c.capacity());
        if (!game.carried.isEmpty()) {
            sb.append(":  ");
            List<TrashDef> rev = new ArrayList<>(game.carried);
            java.util.Collections.reverse(rev);
            for (int i = 0; i < rev.size(); i++) sb.append(i == 0 ? "[" + rev.get(i).name() + "]" : ", " + rev.get(i).name());
        }
        text(g, sb.toString(), left, bottom, Color.WHITE);
        String help = "Move WASD/Arrows   Pick up or drop E/Space   Pause P";
        text(g, help, right - g.getFontMetrics().stringWidth(help), bottom, new Color(0xb8c4d8));

        if (game.messageTimer > 0 && game.state == Game.State.PLAY) {
            g.setFont(font(6.5, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(game.message), cx = ox + (int) (Level.W * u / 2), y = bottom - (int) (14 * u);
            g.setColor(new Color(0, 0, 0, 175));
            g.fillRoundRect(cx - tw / 2 - 12, y - fm.getAscent() - 4, tw + 24, fm.getHeight() + 8, 12, 12);
            text(g, game.message, cx - tw / 2, y, game.messageColor);
        }
    }

    private void panel(Graphics2D g, int w, int h) {
        int cx = ox + (int) (Level.W * u / 2), cy = oy + (int) (Level.H * u / 2);
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(ox, oy, (int) (Level.W * u), (int) (Level.H * u));
        g.setColor(new Color(0x1e2230));
        g.fillRoundRect(cx - w / 2, cy - h / 2, w, h, 20, 20);
        g.setColor(new Color(0x56c46a));
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(cx - w / 2, cy - h / 2, w, h, 20, 20);
    }

    private void intro(Graphics2D g) {
        int w = (int) (440 * u), h = (int) (185 * u);
        panel(g, w, h);
        int cx = ox + (int) (Level.W * u / 2), y = oy + (int) (Level.H * u / 2) - h / 2 + (int) (28 * u);
        String[] lines = game.level.briefing();
        for (int i = 0; i < lines.length; i++) {
            g.setFont(font(i == 0 ? 15 : i == 1 ? 9 : 6.2, Font.BOLD));
            Color c = i == 0 ? new Color(0xffe27a) : i == 1 ? new Color(0xff9a8a) : Color.WHITE;
            centered(g, lines[i], cx, y, c);
            y += (int) ((i == 0 ? 20 : i == 1 ? 14 : 12) * u);
        }
        y += (int) (6 * u);
        g.setFont(font(6, Font.BOLD));
        int bx = cx - (int) (110 * u);
        int bw = (int) (46 * u);
        int by = y;
        int i = 0;
        for (WasteType t : WasteType.values()) {
            g.setColor(t.color);
            g.fillRoundRect(bx + i * (bw + 6), by, bw, (int) (11 * u), 8, 8);
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(t.label, bx + i * (bw + 6) + (bw - fm.stringWidth(t.label)) / 2, by + (int) (8 * u));
            i++;
        }
        g.setFont(font(8, Font.BOLD));
        centered(g, "Press ENTER to start", cx, oy + (int) (Level.H * u / 2) + h / 2 - (int) (14 * u),
                (int) (game.time * 2) % 2 == 0 ? Color.WHITE : new Color(0x9dff9d));
    }

    private void banner(Graphics2D g, String title, String sub) {
        panel(g, (int) (240 * u), (int) (90 * u));
        int cx = ox + (int) (Level.W * u / 2), cy = oy + (int) (Level.H * u / 2);
        g.setFont(font(16, Font.BOLD));
        centered(g, title, cx, cy, Color.WHITE);
        g.setFont(font(6.5, Font.BOLD));
        centered(g, sub, cx, cy + (int) (18 * u), new Color(0xb8c4d8));
    }

    private void results(Graphics2D g, boolean won) {
        int w = (int) (300 * u), h = (int) (225 * u);
        panel(g, w, h);
        int cx = ox + (int) (Level.W * u / 2);
        int y = oy + (int) (Level.H * u / 2) - h / 2 + (int) (30 * u);
        g.setFont(font(15, Font.BOLD));
        centered(g, won ? "STAGE CLEAR" : "STAGE FAILED", cx, y, won ? new Color(0xffe27a) : new Color(0xff6b6b));
        y += (int) (14 * u);
        g.setFont(font(6.5, Font.BOLD));
        centered(g, won ? "The Waste King is defeated. The market is clean!" : game.loseReason, cx, y, Color.WHITE);
        y += (int) (16 * u);
        String[][] rows = won ? new String[][]{
                {"Trash picked up", money(game.moneyPickup)},
                {"Correct sorting", money(game.moneySort)},
                {"Hazardous waste", money(game.moneyHaz)},
                {"Areas cleared", money(game.moneyArea)},
                {"Level complete", money(game.moneyLevel)},
                {"Waste King defeated", money(game.moneyBoss)},
                {"Clean finish (95%+)", money(game.moneyBonus)},
                {"TOTAL", money(game.money)}
        } : new String[][]{
                {"Sorted correctly", game.sorted + "/" + game.level.cfg.target()},
                {"Wrong bins", String.valueOf(game.mistakes)},
                {"Cleanliness", (int) game.cleanliness + "%"},
                {"Money earned", money(game.money)}
        };
        int lx = cx - (int) (110 * u), rx = cx + (int) (110 * u);
        for (int i = 0; i < rows.length; i++) {
            boolean total = won && i == rows.length - 1;
            g.setFont(font(total ? 8 : 6.5, Font.BOLD));
            Color c = total ? new Color(0xffe27a) : Color.WHITE;
            text(g, rows[i][0], lx, y, c);
            text(g, rows[i][1], rx - g.getFontMetrics().stringWidth(rows[i][1]), y, c);
            y += (int) (13 * u);
        }
        if (!won) {
            y += (int) (4 * u);
            g.setFont(font(6, Font.BOLD));
            centered(g, "Tip: check the item name, then match it to its bin colour.", cx, y, new Color(0xb8c4d8));
        }
        g.setFont(font(8, Font.BOLD));
        centered(g, "Press ENTER to play again", cx, oy + (int) (Level.H * u / 2) + h / 2 - (int) (14 * u), new Color(0x9dff9d));
    }
}
