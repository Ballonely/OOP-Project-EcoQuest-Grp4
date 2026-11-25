package com.ecoquest.level1;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Pixel-art trash icons drawn like the pickup sheet of the resource pack: chunky 16x16 sprites, a flat base colour,
 * one shade, one highlight, and a dark outline (added by {@link MarketSprites}). Each icon is up to 14x14 characters;
 * every letter is a palette entry, '.' is transparent.
 */
final class MarketIconArt {
    private MarketIconArt() {
    }

    private record Art(String palette, String[] rows) {
    }

    private static final Map<String, Art> ART = new HashMap<>();

    private static void add(String id, String palette, String... rows) {
        ART.put(id, new Art(palette, rows));
    }

    /** The icon for this id on a 16x16 canvas without outline, or null when no art exists. */
    static BufferedImage get(String id) {
        Art a = ART.get(id);
        if (a == null) return null;
        Map<Character, Integer> pal = new HashMap<>();
        String[] t = a.palette.trim().split("\\s+");
        for (int i = 0; i < t.length; i++) {
            if (t[i].length() == 1) { // "w f1ede0"
                pal.put(t[i].charAt(0), 0xff000000 | Integer.parseInt(t[i + 1], 16));
                i++;
            } else { // "k2a1d1d"
                pal.put(t[i].charAt(0), 0xff000000 | Integer.parseInt(t[i].substring(1), 16));
            }
        }
        BufferedImage im = new BufferedImage(MarketSprites.ICON, MarketSprites.ICON, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < a.rows.length && y < 14; y++) {
            String r = a.rows[y];
            for (int x = 0; x < r.length() && x < 14; x++) {
                Integer c = pal.get(r.charAt(x));
                if (c != null) im.setRGB(x + 1, y + 1, c);
            }
        }
        return im;
    }

    static {
        // ---------------------------------------------------------------- BIO
        add("fish_bone", "w f1ede0 s c9c2ac k2a1d1d",
                "..............",
                "....w.w.w.....",
                "....w.w.w.....",
                ".www.w.w.w.w..",
                "wwkwwwwwwwwwww",
                "wwwwwsssssswww",
                ".www.w.w.w.w..",
                "....w.w.w.....",
                "....w.w.w.....");
        add("fish_head", "b 6c8696 B 9db8c9 w d9e6ee e ffffff k 1b1b1b r c0392b s f1ede0",
                "..............",
                "...bbbbbbb....",
                "..bBBBBBBBb...",
                ".bBwwBBBBBbr..",
                "bBBwekBBBBbrr.",
                "bBBBBBBBBBbrs.",
                "bBBBBBBBBBbrs.",
                ".bBBBBBBBbbrs.",
                "..bbBBBBbb.s..",
                "....bbbb......");
        add("meat_scraps", "r 8f2f30 R c2504f f f3dccc s f1ede0",
                "..............",
                "..rrrrrrr.....",
                ".rRRRRRRRr....",
                "rRRffRRRRRr.s.",
                "rRRfRRRRRRrss.",
                "rRRRRRRRRRrss.",
                ".rRRRRRRRRr.s.",
                "..rrRRRRrr....",
                "....rrrr......");
        add("apple_core", "s 6b4a22 r a82f2f R d24a45 c efe2b0 d 4a2e18",
                "......s.......",
                ".....ss.......",
                "..rrrRRrrr....",
                ".rRRRRRRRRr...",
                ".rRRRRRRRRr...",
                "..cccccccc....",
                "...ccdccc.....",
                "....cccc......",
                "...ccdccc.....",
                "..cccccccc....",
                ".rRRRRRRRRr...",
                ".rRRRRRRRRr...",
                "..rrRRRRrr....");
        add("banana_peel", "y e0b81f Y f7dd45 n 6b4a22 d a07a1c",
                "..............",
                "..n........n..",
                "..yy......yy..",
                "..yYy....yYy..",
                "..yYYy..yYYy..",
                "...yYYyyyYYy..",
                "...yYYYYYYYy..",
                "....yYYYYYy...",
                ".....yyyyy....",
                "..d.......d...");
        add("cabbage_leaf", "g 3f8f3a G 7cc46a l c7eaa0",
                "...gggggggg...",
                ".ggGGGGGGGGgg.",
                "gGGGGlGGGGGGGg",
                "gGGlGGlGGlGGGg",
                "gGGGlGGlGGlGGg",
                ".gGGGlllGGGgg.",
                "..gGGGlGGGGg..",
                "...ggGlGgg....",
                ".....gl.......");
        add("rotten_tomato", "r 8e2a22 R c0453a b 4a2a22 w e58a7c g 3f6f2c G 6aa04a",
                "......ggg.....",
                "....gggGgg....",
                "..rrrrgrrrr...",
                ".rRRRRRRRRRr..",
                "rRRwRRRRRRRRr.",
                "rRRRRbbRRRRRr.",
                "rRRRRbbbRRRRr.",
                "rRRRRRRRRRbbr.",
                ".rRRRRRRRRbRr.",
                "..rrRRRRRRrr..",
                "....rrrrrr....");
        add("egg_shell", "w d8cdb4 W f5efe0",
                "..............",
                "....wwww......",
                "...wWWWWw.....",
                "..wWWWWWWw....",
                "..wWWWWWWw....",
                "..wWwWWWWw....",
                "..w.ww.w.w....",
                "..............",
                "...ww.ww......",
                "..wWWWWWWw....",
                "..wWWWWWWw....",
                "...wWWWWw.....",
                "....wwww......");
        add("bread_crust", "b 8a5a2a B c8883d c e8c98a C f6e2b3",
                "...bbbb.bbbb..",
                "..bBBBBbBBBBb.",
                ".bBBBBBBBBBBBb",
                ".bBBBBBBBBBBBb",
                "..bBcccccccBb.",
                "..bBcCCCCCcBb.",
                "..bBcCCCCCcBb.",
                "..bBcCCCCCcBb.",
                "..bBBBBBBBBBb.",
                "...bbbbbbbbb..");

        // ---------------------------------------------------------------- REC
        add("plastic_bottle", "c 2f6fd0 C 6aa0ff w cfeefa b 8fd0ee d 4a9cc4 g 2fa84f G ffffff",
                "......cc......",
                "......CC......",
                ".....wbbd.....",
                "....wbbbbd....",
                "...wbbbbbbd...",
                "...wggggggd...",
                "...wgGGGGgd...",
                "...wggggggd...",
                "...wbbbbbbd...",
                "...wbbbbbbd...",
                "...wbbbbbbd...",
                "...wbbbbbdd...",
                "....dddddd....");
        add("tin_can", "t f2f5f7 S b9c2c8 s 8f9aa2 d 5f6870 R d23a3a w ffffff",
                "..............",
                "...tttttttt...",
                "..tSSSSSSSSt..",
                "..tssssssssd..",
                "..dSSSSSSSSd..",
                "..dRRRRRRRRd..",
                "..dRwwwwwwRd..",
                "...dRRRRRRRd..",
                "...dSSSSSSd...",
                "..dSSSSSSSSd..",
                "..dsSSSSSSsd..",
                "...dddddddd...");
        add("glass_bottle", "g 1f6b34 G 3aa85c L e8e0c0 w bfeccc",
                "......ww......",
                "......gg......",
                ".....gGGg.....",
                ".....gGGg.....",
                ".....gGGg.....",
                "....gGGGGg....",
                "...gGGGGGGg...",
                "...gGwGGGGg...",
                "...gGwGGGGg...",
                "...gGGGGGGg...",
                "...gGLLLLGg...",
                "...gGLLLLGg...",
                "...gGGGGGGg...",
                "....gggggg....");
        add("glass_jar", "m c9a43a M e8c85a c 6aa8be C bfe6f2 w ffffff L e8e0c0",
                "..............",
                "...mmmmmmmm...",
                "...MMMMMMMM...",
                "...mmmmmmmm...",
                "..cCCCCCCCCc..",
                "..cCwCCCCCCc..",
                "..cCwCCCCCCc..",
                "..cCCLLLLCCc..",
                "..cCCLLLLCCc..",
                "..cCCCCCCCCc..",
                "..cCCCCCCCCc..",
                "..cCCCCCCCCc..",
                "...cccccccc...");
        add("cardboard", "l d9a868 L e8c088 b 8a5a2a B c08850 t e8d8a8",
                "..............",
                "..llllllllll..",
                ".lLLLLLLLLLLl.",
                ".bbbbbbbbbbbb.",
                ".bBBBBttBBBBb.",
                ".bBBBBttBBBBb.",
                ".bBBBBttBBBBb.",
                ".bBBBBBBBBBBb.",
                ".bBBBBBBBBBBb.",
                ".bBBBBBBBBBBb.",
                ".bbbbbbbbbbbb.");
        add("newspaper", "g 6a6a6a W e8e8e0 K 2a2a2a L 9a9a9a P 7a8a9a",
                ".gggggggggggg.",
                ".gWWWWWWWWWWg.",
                ".gWKKKKKKKKWg.",
                ".gWWWWWWWWWWg.",
                ".gWPPPWLLLLWg.",
                ".gWPPPWWWWWWg.",
                ".gWPPPWLLLLWg.",
                ".gWWWWWWWWWWg.",
                ".gWLLLLWLLLLg.",
                ".gWWWWWWWWWWg.",
                ".gggggggggggg.");

        // ---------------------------------------------------------------- TRASH
        add("plastic_bag", "h bfc7cf w a9b4be W eef2f5 b d3dce4",
                "...hh....hh...",
                "...h.h..h.h...",
                "...h..hh..h...",
                "..wWWWwwWWWw..",
                ".wWWWWWWWWWWw.",
                ".wWWbWWWWWWWw.",
                ".wWWWWWWWWbWw.",
                ".wWWWWWWWWWWw.",
                "..wWWWWbWWWw..",
                "..wwWWWWWWww..",
                "....wwwwww....");
        add("styro_tray", "w cfd5da W ffffff s aeb7bf S e7ecef d 9aa3ab",
                "..wwwwwwwwww..",
                ".wWWWWWWWWWWw.",
                "wWWsssssssssWw",
                "wWsSSSSSSSSsWw",
                "wWsSSSSSSSSsWw",
                "wWsSSSSSSSSsWw",
                "wWWsssssssssWw",
                ".wddddddddddw.",
                "..dd......dd..");
        add("sachet", "p b8307a P e85aa0 d 7a1f50 Y ffd54f w ffffff",
                "..dddddddddd..",
                "..dPpdPpdPpd..",
                "..pPPPPPPPPp..",
                "..pPwwwwwwPp..",
                "..pPwYYYYwPp..",
                "..pPwwwwwwPp..",
                "..pPPPPPPPPp..",
                "..dPpdPpdPpd..",
                "..dddddddddd..");
        add("straw", "R e03a3a r b02525 w ffffff",
                "............Rr",
                "...........Rr.",
                "..........ww..",
                ".........ww...",
                "........Rr....",
                ".......Rr.....",
                "......ww......",
                ".....ww.......",
                "....Rr........",
                "...Rr.........",
                "..ww..........",
                ".ww...........");
        add("cig_butt", "g 555555 G 888888 w cfcfcf W ffffff o b8641e O e89a3e e e83a20",
                "..............",
                "..............",
                "..............",
                ".gggwwwwooooo.",
                "eGGGwWWWOOOOo.",
                "gGGGwWWWOOOOo.",
                ".gggwwwwooooo.");
        add("tissue", "w c9d0d6 W f7f9fb s dfe5ea",
                "..............",
                "...ww..www....",
                "..wWWwwWWWw...",
                ".wWWWWWWWWWw..",
                ".wWsWWWWWsWWw.",
                "wWWWWsWWWWWWw.",
                "wWWWWWWWsWWWw.",
                ".wWWsWWWWWWw..",
                ".wwWWWWWWWww..",
                "...wwwwwwww...");

        // ---------------------------------------------------------------- HAZ
        add("battery", "b 1f5a2a G 3aa84f Y f2c230 S c0c5ca s d8dde2",
                "......ss......",
                "....bbbbbb....",
                "....bGGGGb....",
                "....bGGGGb....",
                "....bYYYYb....",
                "....bYbbYb....",
                "....bGGGGb....",
                "....bGGGGb....",
                "....bGGGGb....",
                "....bGGGGb....",
                "....bSSSSb....",
                "....bbbbbb....");
        add("bulb", "w bfc8d0 W fff6c8 f e0a020 m 7a7a86 M b0b0bc",
                "....wwwwww....",
                "...wWWWWWWw...",
                "..wWWWWWWWWw..",
                "..wWWfWWfWWw..",
                "..wWWfffWWWw..",
                "..wWWWfWWWWw..",
                "...wWWWWWWw...",
                "....wWWWWw....",
                "....mmmmmm....",
                "....MMMMMM....",
                "....mmmmmm....",
                ".....mmmm.....");
        add("spray_can", "s 8c949c S d0d6dc r 9b1c1c R d63a3a y ffd54f n 3a3a3a N 6a6a6a",
                "......nn......",
                ".....nNNn.....",
                "....ssssss....",
                "....sSSSSs....",
                "....sSSSSs....",
                "....rRRRRr....",
                "....rRyyRr....",
                "....rRyyRr....",
                "....rRRRRr....",
                "....rRRRRr....",
                "....sSSSSs....",
                "....ssssss....");
        add("lighter", "y ffd54f o e8742a O ffb03a k 3a3a3a m 8c949c M d0d6dc b 2f6fd0 B 6aa0ff w ffffff",
                ".....yo.......",
                "....yOOo......",
                "....oOOo......",
                ".....kk.......",
                "....mmmmm.....",
                "....mMMMm.....",
                "....bBBBb.....",
                "....bBwBb.....",
                "....bBwBb.....",
                "....bBBBb.....",
                "....bBBBb.....",
                "....bBBBb.....",
                "....bbbbb.....");
    }
}
