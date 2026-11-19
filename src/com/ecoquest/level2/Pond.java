package com.ecoquest.level2;

import com.ecoquest.level1.Palette;
import com.ecoquest.level1.Prop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

public class Pond extends Prop {
    private static final int[][] PADS = {
        {37, 34, 0}, {106, 32, 0}, {136, 58, 1}, {80, 110, 0}, {112, 100, 0}, {84, 128, 1}, {28, 64, 0}
    };
    private static final double[][] DUCKS = { {54, 20, 0}, {120, 50, 2}, {52, 104, 4}, {98, 78, 1} };

    private final Rectangle[] parts;
    private Area rim, water;

    public Pond(Rectangle... parts) {
        super(bounds(parts).x, bounds(parts).y, bounds(parts).width, bounds(parts).height, false);
        this.parts = parts;
    }

    private static Rectangle bounds(Rectangle[] parts) {
        Rectangle b = new Rectangle(parts[0]);
        for (Rectangle r : parts) b.add(r);
        return b;
    }

    private Area shape(int grow, int arc) {
        Area a = new Area();
        java.util.Random rn = new java.util.Random(3);
        for (Rectangle r : parts) {
            a.add(new Area(new RoundRectangle2D.Double(r.x - grow, r.y - grow, r.width + grow * 2, r.height + grow * 2, arc, arc)));
            for (int i = 10; i < r.width - 10; i += 9) {
                bump(a, r.x + i, r.y + rn.nextInt(3), 4 + rn.nextInt(4) + grow);
                bump(a, r.x + i, r.y + r.height - rn.nextInt(3), 4 + rn.nextInt(4) + grow);
            }
            for (int i = 10; i < r.height - 10; i += 9) {
                bump(a, r.x + rn.nextInt(3), r.y + i, 4 + rn.nextInt(4) + grow);
                bump(a, r.x + r.width - rn.nextInt(3), r.y + i, 4 + rn.nextInt(4) + grow);
            }
        }
        return a;
    }

    private void bump(Area a, int cx, int cy, int r) {
        a.add(new Area(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2)));
    }

    @Override public boolean isGroundDecal() { return true; }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        double t = System.currentTimeMillis() / 1000.0;

        if (rim == null) {
            rim = shape(3, 28);
            water = shape(0, 24);
        }
        g.setColor(Palette.c(0x1E5A3C));
        g.fill(rim);
        g.setColor(Palette.c(0x1BB0AA));
        g.fill(water);

        Shape clip = g.getClip();
        g.clip(water);
        g.setColor(Palette.c(0x4FC3C0));
        g.fillRect(X - 8, Y - 8, w + 16, 7);
        g.setColor(Palette.c(0x15979B));
        g.fillOval(X + w / 4, Y + h / 5, w * 3 / 4, h * 4 / 5);
        g.setColor(Palette.c(0x0D8090));
        g.fillOval(X + w / 3 + 6, Y + h / 3, w / 2, h / 2);
        g.setColor(new Color(255, 255, 255, 90));
        for (int i = 0; i < 9; i++) {
            int off = (int) ((t * 3 + i) % 6);
            g.fillRect(X + 10 + i * 37 % (w - 24) + off, Y + 10 + i * 23 % (h - 20), 4, 1);
        }
        g.setClip(clip);

        for (int[] p : PADS) {
            g.setColor(Palette.c(0x2F7A2F));
            g.fillOval(X + p[0], Y + p[1], 15, 10);
            g.setColor(Palette.c(0x4CAF3F));
            g.fillOval(X + p[0], Y + p[1], 14, 9);
            rect(g, X + p[0] + 7, Y + p[1] + 3, 3, 2, 0x2F7A2F);
            rect(g, X + p[0] + 3, Y + p[1] + 1, 2, 1, 0x7CCB5E);
            if (p[2] == 1) rect(g, X + p[0] + 2, Y + p[1] + 2, 3, 2, 0xF2A7C3);
        }

        for (double[] d : DUCKS) {
            double a = t * 0.5 + d[2];
            int facing = Math.sin(a) < 0 ? 1 : -1;
            int bob = (int) Math.round(Math.sin(t * 3 + d[2]) * 0.6);
            duck(g, (int) (X + d[0] + Math.cos(a) * 8), (int) (Y + d[1] + Math.sin(a) * 4) + bob, facing);
        }
    }

    private void duck(Graphics2D g, int dx, int dy, int facing) {
        g.setColor(new Color(255, 255, 255, 120));
        g.fillRect(facing > 0 ? dx - 4 : dx + 9, dy + 6, 4, 1);
        rect(g, dx, dy + 2, 9, 5, 0x5E4326);
        rect(g, dx + 1, dy + 2, 7, 3, 0x8A6A42);
        rect(g, dx + 2, dy + 3, 4, 1, 0xB89A6E);
        int hx = facing > 0 ? dx + 6 : dx - 1;
        rect(g, hx, dy - 1, 4, 4, 0x2E7D3A);
        rect(g, hx, dy + 2, 4, 1, 0xF2F2F2);
        rect(g, facing > 0 ? hx + 2 : hx + 1, dy, 1, 1, 0x111111);
        rect(g, facing > 0 ? hx + 4 : hx - 2, dy + 1, 2, 1, 0xE8A83A);
    }
}
