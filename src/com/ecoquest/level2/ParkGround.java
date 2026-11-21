package com.ecoquest.level2;

import com.ecoquest.level1.Palette;
import com.ecoquest.level1.Prop;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

import static com.ecoquest.level1.Config.WORLD_H;
import static com.ecoquest.level1.Config.WORLD_W;

public class ParkGround extends Prop {
    private static final int[] STONES = { 0x9C7A62, 0x8E6E58, 0xA8866C, 0x84654F };
    private static final int[] PETALS = { 0xF4A6C6, 0xFAD1E0, 0xE77FA8 };

    private final Area paths = new Area();
    private final BufferedImage image = new BufferedImage(WORLD_W, WORLD_H, BufferedImage.TYPE_INT_ARGB);

    public ParkGround(List<Rectangle> pathRects, List<int[]> petalSpots) {
        super(0, 0, WORLD_W, WORLD_H, false);
        for (Rectangle r : pathRects) paths.add(new Area(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 8, 8)));
        Graphics2D g = image.createGraphics();
        Random rn = new Random(7);

        rect(g, 0, 0, WORLD_W, WORLD_H, 0x2A9A45);
        for (int i = 0; i < 260; i++) {
            g.setColor(Palette.c(rn.nextBoolean() ? 0x228C3B : 0x34A84C));
            g.fillOval(rn.nextInt(WORLD_W), rn.nextInt(WORLD_H), 6 + rn.nextInt(14), 4 + rn.nextInt(8));
        }
        for (int i = 0; i < 2600; i++)
            rect(g, rn.nextInt(WORLD_W), rn.nextInt(WORLD_H), 1, 2, rn.nextBoolean() ? 0x1C7F36 : 0x48B858);

        g.setColor(Palette.c(0x5C4436));
        for (Rectangle r : pathRects) g.fillRoundRect(r.x - 1, r.y - 1, r.width + 2, r.height + 2, 10, 10);
        g.setClip(paths);
        rect(g, 0, 0, WORLD_W, WORLD_H, 0x6B5244);
        for (int sy = 0, row = 0; sy < WORLD_H; sy += 5, row++)
            for (int sx = row % 2 * 4 - 4 + rn.nextInt(2); sx < WORLD_W; sx += 7 + rn.nextInt(2)) {
                int c = STONES[rn.nextInt(STONES.length)];
                rect(g, sx + 1, sy, 4, 4, c);
                rect(g, sx, sy + 1, 6, 2, c);
                rect(g, sx + 1, sy, 4, 1, 0xB8967A);
            }
        g.setClip(null);
        for (int i = 0; i < 900; i++) {
            int gx = rn.nextInt(WORLD_W), gy = rn.nextInt(WORLD_H);
            if (paths.contains(gx, gy) && !paths.contains(gx, gy - 3)) rect(g, gx, gy - 1, 1, 3, 0x228C3B);
            else if (paths.contains(gx, gy) && !paths.contains(gx, gy + 3)) rect(g, gx, gy, 1, 2, 0x228C3B);
        }

        for (int[] s : petalSpots)
            for (int i = 0; i < 26; i++) {
                double a = rn.nextDouble() * Math.PI * 2, d = rn.nextDouble() * s[2];
                rect(g, s[0] + (int) (Math.cos(a) * d), s[1] + (int) (Math.sin(a) * d * 0.6), 1 + rn.nextInt(2), 1,
                        PETALS[rn.nextInt(PETALS.length)]);
            }
        g.dispose();
    }

    public boolean onPath(double px, double py) { return paths.contains(px, py); }

    @Override public boolean isGroundDecal() { return true; }

    @Override
    public void draw(Graphics2D g) { g.drawImage(image, 0, 0, null); }
}
