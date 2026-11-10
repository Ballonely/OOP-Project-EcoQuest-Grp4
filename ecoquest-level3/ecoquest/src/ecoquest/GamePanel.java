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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Draws the level (640x360 world scaled up with crisp pixels) plus HUD and menus, and reads the keyboard. */
public final class GamePanel extends JPanel implements MenuScreen.Listener, LevelSelectScreen.Listener {
    private enum Screen { MENU, SELECT, PLAYING }

    private Game game;
    private BufferedImage background;
    private final BufferedImage frame = new BufferedImage(Level.W, Level.H, BufferedImage.TYPE_INT_RGB);
    private long last = System.nanoTime();
    private double u = 2; // screen pixels per world pixel
    private int ox, oy;

    private final InputHandler input = new InputHandler();
    private final HudPainter hudPainter = new HudPainter();
    private final MenuScreen menu;
    private final LevelSelectScreen select;
    private Screen screen = Screen.MENU;
    private int hiScore = 98450;
    private int resultSelection;
    private Game.State lastState = Game.State.INTRO;

    public GamePanel() {
        setPreferredSize(new Dimension(Theme.WINDOW_WIDTH, Theme.WINDOW_HEIGHT));
        setBackground(Theme.BG_DARK);
        setFocusable(true);
        menu = new MenuScreen(this, hiScore);
        select = new LevelSelectScreen(this);
        addKeyListener(input);
        addMouseListener(input);
        addMouseMotionListener(input);
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
            tick(dt);
            input.endFrame();
            repaint();
        }).start();
    }

    /** Movement and pick-up/drop go straight to the level rules; everything else is polled in {@link #tick}. */
    private void key(int code, boolean down) {
        if (game == null) return;
        switch (code) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> game.keys[0] = down;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> game.keys[1] = down;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> game.keys[2] = down;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> game.keys[3] = down;
            default -> {
            }
        }
        if (down && screen == Screen.PLAYING && (code == KeyEvent.VK_E || code == KeyEvent.VK_SPACE)) game.act();
    }

    // ------------------------------------------------------------------ screens

    private void tick(double dt) {
        switch (screen) {
            case MENU -> {
                menu.update(dt);
                menu.handleInput(input, getWidth());
            }
            case SELECT -> {
                select.update(dt);
                select.handleInput(input, getWidth(), getHeight());
            }
            case PLAYING -> tickPlaying(dt);
        }
    }

    private void tickPlaying(double dt) {
        switch (game.state) {
            case INTRO -> {
                if (input.isKeyJustPressed(KeyEvent.VK_ENTER) || input.isKeyJustPressed(KeyEvent.VK_SPACE)) game.start();
                if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) screen = Screen.SELECT;
            }
            case PLAY -> {
                if (input.isKeyJustPressed(KeyEvent.VK_P) || input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) game.togglePause();
                else if (input.isKeyJustPressed(KeyEvent.VK_R)) game.restart();
            }
            case PAUSED -> {
                if (input.isKeyJustPressed(KeyEvent.VK_P)) game.togglePause();
                else if (input.isKeyJustPressed(KeyEvent.VK_R)) game.restart();
                else if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) screen = Screen.MENU;
            }
            case WON, LOST -> resultInput();
        }
        game.update(dt);
        if (game.state != lastState) {
            if (game.state == Game.State.WON || game.state == Game.State.LOST) {
                resultSelection = 0;
                hiScore = Math.max(hiScore, game.score);
                menu.setHiScore(hiScore);
            }
            lastState = game.state;
        }
    }

    private void resultInput() {
        boolean won = game.state == Game.State.WON;
        int count = won ? 3 : 2;
        if (input.isKeyJustPressed(KeyEvent.VK_LEFT) || input.isKeyJustPressed(KeyEvent.VK_UP)) {
            resultSelection = (resultSelection + count - 1) % count;
        } else if (input.isKeyJustPressed(KeyEvent.VK_RIGHT) || input.isKeyJustPressed(KeyEvent.VK_DOWN)) {
            resultSelection = (resultSelection + 1) % count;
        } else if (input.isKeyJustPressed(KeyEvent.VK_R)) {
            game.restart();
        } else if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
            screen = Screen.MENU;
        } else if (input.isKeyJustPressed(KeyEvent.VK_ENTER) || input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            if (won && resultSelection == 0) {
                select.setSelected(3);
                select.showNotice("LEVEL 4: COASTAL BEACH IS COMING SOON");
                screen = Screen.SELECT;
            } else if (resultSelection == (won ? 1 : 0)) {
                game.restart();
            } else {
                screen = Screen.MENU;
            }
        }
    }

    private void startMarket() {
        game = new Game(Level.create(MapId.MARKET));
        background = game.level.paintBackground();
        lastState = game.state;
        screen = Screen.PLAYING;
    }

    @Override
    public void onStartGame() {
        screen = Screen.SELECT;
    }

    @Override
    public void onOpenShop() {
        menu.showNotice("SHOP: COMING SOON");
    }

    @Override
    public void onOpenSettings() {
        menu.showNotice("SETTINGS & AUDIO: COMING SOON");
    }

    @Override
    public void onQuit() {
        System.exit(0);
    }

    @Override
    public void onBackToMenu() {
        screen = Screen.MENU;
    }

    @Override
    public void onStartLevel(int index) {
        if (LevelSelectScreen.isPlayable(index)) startMarket();
        else select.showNotice("LEVEL " + (index + 1) + ": " + LevelSelectScreen.LEVEL_NAMES[index].toUpperCase() + " IS COMING SOON");
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics gr) {
        super.paintComponent(gr);
        Graphics2D g = (Graphics2D) gr;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        if (screen == Screen.MENU) {
            menu.render(g, w, h);
            return;
        }
        if (screen == Screen.SELECT) {
            select.render(g, w, h);
            return;
        }
        render(frame.createGraphics());
        u = Math.min(w / (double) Level.W, h / (double) Level.H);
        int sw = (int) (Level.W * u), sh = (int) (Level.H * u);
        ox = (w - sw) / 2;
        oy = (h - sh) / 2;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(frame, ox, oy, sw, sh, null);
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

        g.setStroke(new BasicStroke(1));
        int w = getWidth(), h = getHeight();
        if (st == Game.State.INTRO) {
            hudPainter.intro(g, game, w, h);
            return;
        }
        hudPainter.hud(g, game, w, h);
        if (st == Game.State.PAUSED) hudPainter.pause(g, w, h);
        else if (st == Game.State.WON || st == Game.State.LOST) outcome(g, w, h, st == Game.State.WON);
    }

    private void outcome(Graphics2D g, int w, int h, boolean won) {
        int bonus = game.moneyLevel + game.moneyBoss + game.moneyBonus;
        hudPainter.outcome(g, w, h, won, "Lv 3: Town Market", game.score, game.money - bonus, bonus,
                (int) game.cleanliness, game.loseReason, "Check the item name, then match it to its bin colour.",
                resultSelection);
    }
}
