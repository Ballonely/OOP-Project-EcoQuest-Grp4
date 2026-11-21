package ecoquest;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Illustrated overworld. Only the levels listed in {@code playable} can be started. */
final class LevelSelectScreen {
    interface Listener {
        void onBackToMenu();

        void onStartLevel(int index);
    }

    static final String[] LEVEL_NAMES = {"Residential", "Public Park", "Town Market", "Coastal Beach", "City Center"};

    private static final Rectangle[] REGIONS = {
            new Rectangle(76, 111, 308, 232),
            new Rectangle(475, 116, 268, 212),
            new Rectangle(206, 347, 337, 220),
            new Rectangle(606, 401, 528, 172),
            new Rectangle(796, 106, 348, 273)
    };
    private static final boolean[] PLAYABLE = {false, false, true, false, false};

    private static final Color MAP_BACKGROUND = new Color(0xEFDCB4);
    private static final Color SELECTED = new Color(255, 201, 49);

    private final Listener listener;
    private final BufferedImage map = load();
    private int selected = 2;
    private int hovered = -1;
    private Rectangle shown = new Rectangle();
    private String notice = "";
    private double noticeTimer;

    LevelSelectScreen(Listener listener) {
        this.listener = listener;
    }

    private static BufferedImage load() {
        try {
            return ImageIO.read(Res.find("assets/level_select_map.png").toFile());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    void setSelected(int i) {
        selected = i;
    }

    void showNotice(String text) {
        notice = text;
        noticeTimer = 2.5;
    }

    void update(double dt) {
        noticeTimer = Math.max(0, noticeTimer - dt);
    }

    void handleInput(InputHandler in, int w, int h) {
        if (in.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
            listener.onBackToMenu();
            return;
        }
        if (in.isKeyJustPressed(KeyEvent.VK_LEFT) || in.isKeyJustPressed(KeyEvent.VK_UP)) {
            selected = (selected + LEVEL_NAMES.length - 1) % LEVEL_NAMES.length;
        } else if (in.isKeyJustPressed(KeyEvent.VK_RIGHT) || in.isKeyJustPressed(KeyEvent.VK_DOWN)) {
            selected = (selected + 1) % LEVEL_NAMES.length;
        } else if (in.isKeyJustPressed(KeyEvent.VK_ENTER) || in.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            listener.onStartLevel(selected);
            return;
        }
        hovered = regionAt(in.getMouseX(), in.getMouseY(), w, h);
        Point click = in.consumeMouseClick();
        int clicked = click == null ? -1 : regionAt(click.x, click.y, w, h);
        if (clicked >= 0) {
            selected = clicked;
            listener.onStartLevel(selected);
        }
    }

    void render(Graphics2D g, int w, int h) {
        g.setColor(MAP_BACKGROUND);
        g.fillRect(0, 0, w, h);
        shown = mapBounds(w, h);
        if (map != null) g.drawImage(map, shown.x, shown.y, shown.width, shown.height, null);

        for (int i = 0; i < REGIONS.length; i++) {
            if (PLAYABLE[i]) continue;
            Rectangle r = scale(REGIONS[i]);
            g.setColor(new Color(20, 20, 30, 120));
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setFont(new Font("Monospaced", Font.BOLD, 16));
            FontMetrics fm = g.getFontMetrics();
            String t = "COMING SOON";
            int tw = fm.stringWidth(t) + 24;
            MenuScreen.panel(g, r.x + (r.width - tw) / 2, r.y + r.height / 2 - 16, tw, 32);
            g.setColor(Theme.GOLD);
            g.drawString(t, r.x + (r.width - tw) / 2 + 12, r.y + r.height / 2 + 6);
        }

        highlight(g, selected, SELECTED, 4);
        if (hovered >= 0 && hovered != selected) highlight(g, hovered, Color.WHITE, 3);

        if (noticeTimer > 0) {
            g.setFont(Theme.FONT_HUD);
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(notice) + 30;
            MenuScreen.panel(g, (w - tw) / 2, h - 70, tw, 30);
            g.setColor(Theme.GOLD);
            g.drawString(notice, (w - fm.stringWidth(notice)) / 2, h - 50);
        }

        g.setColor(new Color(72, 50, 27));
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        String controls = "CLICK A REGION TO PLAY   |   ARROWS + ENTER TO SELECT   |   ESC TO MENU";
        g.drawString(controls, (w - g.getFontMetrics().stringWidth(controls)) / 2, h - 19);
    }

    private void highlight(Graphics2D g, int i, Color c, int stroke) {
        Rectangle r = scale(REGIONS[i]);
        g.setColor(c);
        g.setStroke(new BasicStroke(stroke));
        g.drawRect(r.x, r.y, r.width, r.height);
        g.setStroke(new BasicStroke(1));
    }

    private int regionAt(int x, int y, int w, int h) {
        if (map == null) return -1;
        Rectangle b = mapBounds(w, h);
        if (!b.contains(x, y)) return -1;
        int ix = (int) ((x - b.x) / (b.getWidth() / map.getWidth()));
        int iy = (int) ((y - b.y) / (b.getHeight() / map.getHeight()));
        for (int i = 0; i < REGIONS.length; i++) if (REGIONS[i].contains(ix, iy)) return i;
        return -1;
    }

    private Rectangle scale(Rectangle r) {
        double sx = shown.getWidth() / (map == null ? 1252 : map.getWidth());
        double sy = shown.getHeight() / (map == null ? 616 : map.getHeight());
        return new Rectangle(shown.x + (int) Math.round(r.x * sx), shown.y + (int) Math.round(r.y * sy),
                (int) Math.round(r.width * sx), (int) Math.round(r.height * sy));
    }

    private Rectangle mapBounds(int w, int h) {
        int mw = map == null ? 1252 : map.getWidth(), mh = map == null ? 616 : map.getHeight();
        double s = Math.min((double) w / mw, (double) (h - 72) / mh);
        int iw = (int) Math.round(mw * s), ih = (int) Math.round(mh * s);
        return new Rectangle((w - iw) / 2, 0, iw, ih);
    }

    static boolean isPlayable(int i) {
        return PLAYABLE[i];
    }
}
