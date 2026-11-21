package com.ecoquest.level2;

import com.ecoquest.level1.Palette;
import com.ecoquest.level1.Prop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

public abstract class ParkTree extends Prop {
    private static final int PAD = 4;
    private BufferedImage sprite;

    protected ParkTree(int px, int py, int size) { super(px, py, size, (int) (size * 1.1), true); }

    protected abstract int[] palette();

    protected void decorate(Graphics2D g, Random rn, int canopyH) {}

    @Override
    public Rectangle2D.Double collisionBox() { return new Rectangle2D.Double(x + w / 2.0 - 4, y + h - 7, 8, 6); }

    @Override
    public void draw(Graphics2D g) {
        if (sprite == null) sprite = render();
        g.drawImage(sprite, (int) x - PAD, (int) y - PAD, null);
    }

    private BufferedImage render() {
        BufferedImage img = new BufferedImage(w + PAD * 2, h + PAD * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.translate(PAD, PAD);
        int s = w, ch = (int) (s * 0.82), tw = Math.max(5, s / 5), tx = s / 2 - tw / 2;
        int[] p = palette();
        Random rn = new Random(w * 7919L + (long) x * 31 + (long) y * 17);

        g.setColor(new Color(10, 60, 25, 90));
        g.fillOval(s / 10, h - 10, s * 4 / 5, 11);
        rect(g, tx, ch - 12, tw, h - ch + 10, 0x6E4436);
        rect(g, tx, ch - 12, 2, h - ch + 10, 0x4A2B22);
        rect(g, tx + tw - 2, ch - 12, 1, h - ch + 10, 0x8A5A44);
        rect(g, tx - 2, h - 4, tw + 4, 3, 0x5A3529);
        rect(g, tx - 3, h - 2, 3, 2, 0x5A3529);
        rect(g, tx + tw, h - 2, 3, 2, 0x5A3529);

        double r = s * 0.14;
        g.setColor(Palette.c(p[0]));
        g.fillOval(2, 2, s - 4, ch - 3);
        g.setColor(Palette.c(p[1]));
        g.fillOval(3, 3, s - 6, ch - 5);
        java.util.List<double[]> puffs = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + rn.nextDouble() * 0.2;
            puffs.add(new double[] { s / 2.0 + Math.cos(a) * (s / 2.0 - r - 1), ch / 2.0 + Math.sin(a) * (ch / 2.0 - r - 1) });
        }
        for (int i = 0; i < 14; i++) {
            double a = rn.nextDouble() * Math.PI * 2, d = Math.sqrt(rn.nextDouble()) * 0.75;
            puffs.add(new double[] { s / 2.0 + Math.cos(a) * d * (s / 2.0 - r), ch / 2.0 + Math.sin(a) * d * (ch / 2.0 - r) });
        }
        puffs.sort((u, v) -> Double.compare(u[1], v[1]));
        for (double[] c : puffs) {
            double rr = r * (0.9 + rn.nextDouble() * 0.3), level = c[1] / ch;
            int t = level < 0.38 ? 3 : level < 0.66 ? 2 : 1;
            oval(g, c[0] + 1, c[1] + 1.5, rr + 1, p[0]);
            oval(g, c[0], c[1], rr, p[t]);
            oval(g, c[0] - rr * 0.3, c[1] - rr * 0.3, rr * 0.55, p[Math.min(4, t + 1)]);
        }
        for (int i = 0; i < s; i++) {
            int dx = rn.nextInt(s), dy = rn.nextInt(ch);
            if (Math.pow((dx - s / 2.0) / (s / 2.0), 2) + Math.pow((dy - ch / 2.0) / (ch / 2.0), 2) < 0.7)
                rect(g, dx, dy, 1, 1, dy < ch / 2 ? p[4] : p[1]);
        }
        decorate(g, rn, ch);
        g.dispose();
        return img;
    }

    private void oval(Graphics2D g, double cx, double cy, double r, int rgb) {
        g.setColor(Palette.c(rgb));
        g.fillOval((int) Math.round(cx - r), (int) Math.round(cy - r), (int) Math.round(r * 2), (int) Math.round(r * 2));
    }
}
