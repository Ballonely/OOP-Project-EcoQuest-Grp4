package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Market content from the Level 3 build, adapted to the shared stage runner. */
public final class MarketStage extends Level {
    private final ecoquest.MarketLevel reference = new ecoquest.MarketLevel();
    private final BufferedImage art = reference.paintBackground();
    private final List<ecoquest.TrashDef> litter = ecoquest.TrashCatalog.load(ecoquest.MapId.MARKET);
    private final List<MarketCart> carts = new ArrayList<>();
    private MarketKing wasteKing;
    private double cartCooldown;

    @Override public String getName() { return "Level 3: Town Marketplace"; }

    @Override protected void buildTerrain() {
        double scale = Config.WORLD_W / (double) ecoquest.Level.W;
        spawnCol = (int) Math.round(reference.start().x * scale / Config.TILE);
        spawnRow = (int) Math.round(reference.start().y * scale / Config.TILE);
    }

    @Override protected void buildProps() {
        for (Rectangle solid : reference.solids()) {
            Rectangle r = scale(solid);
            props.add(new StaticBlock(r.x, r.y, r.width, r.height));
        }
        for (Rectangle puddle : reference.puddles()) {
            Rectangle r = scale(puddle);
            props.add(new Prop(r.x, r.y, r.width, r.height, false) {
                @Override public double speedFactor() { return 0.55; }
                @Override public boolean isGroundDecal() { return true; }
                @Override public void draw(Graphics2D g) { }
            });
        }

        double sx = Config.WORLD_W / (double) ecoquest.Level.W;
        double sy = Config.WORLD_H / (double) ecoquest.Level.H;
        var pile = reference.pile();
        int bossX = (int) Math.round(pile.x * sx);
        int bossY = (int) Math.round(pile.y * sy);
        props.add(new StaticBlock(bossX - 21, bossY - 15, 42, 20));
        wasteKing = new MarketKing(bossX, bossY, 0);
        addNpc(wasteKing);

        int i = 0;
        for (ecoquest.Level.Lane lane : reference.carts()) {
            MarketCart cart = new MarketCart(
                    lane.x1() * sx, lane.x2() * sx, lane.y() * sy, lane.speed() * sx,
                    (i + 1.0) / (reference.carts().size() + 1.0), i++ % 2 == 0 ? 1 : -1);
            carts.add(cart);
            addNpc(cart);
        }
    }

    @Override protected void buildBins() {
        double sx = Config.WORLD_W / (double) ecoquest.Level.W;
        double sy = Config.WORLD_H / (double) ecoquest.Level.H;
        for (var entry : reference.bins().entrySet()) {
            Rectangle r = entry.getValue();
            int x = (int) Math.round(r.getCenterX() * sx);
            int y = (int) Math.round(r.getCenterY() * sy);
            GarbageType type = switch (entry.getKey()) {
                case BIO -> GarbageType.BIODEGRADABLE;
                case REC -> GarbageType.RECYCLABLE;
                case TRASH -> GarbageType.NON_RECYCLABLE;
                case HAZ -> GarbageType.HAZARDOUS;
            };
            bins.add(new TrashBin(type, x / Config.TILE, y / Config.TILE));
        }
    }

    @Override protected void buildGarbage() {
        scatterGarbage(reference.cfg.startItems() - reference.cfg.pileItems());
        var pile = reference.pile();
        double sx = Config.WORLD_W / (double) ecoquest.Level.W;
        double sy = Config.WORLD_H / (double) ecoquest.Level.H;
        addRandomLitterNear(pile.x * sx, pile.y * sy, reference.cfg.pileItems(), 38 * sx);
        scatterGarbage(reference.cfg.target());
    }

    @Override protected Garbage createGarbage(double x, double y) {
        return new Garbage(ecoquest.TrashCatalog.pick(litter, rng), x, y);
    }

    @Override protected boolean isTrashTileAllowed(double x, double y, TileType tile) {
        double sx = Config.WORLD_W / (double) ecoquest.Level.W;
        double sy = Config.WORLD_H / (double) ecoquest.Level.H;
        double mapX = x / sx, mapY = y / sy;
        return new Rectangle(96, 64, 320, 160).contains(mapX, mapY)
                || new Rectangle(192, 0, 144, 80).contains(mapX, mapY);
    }

    @Override protected void drawGround(Graphics2D g) {
        g.drawImage(art, 0, 0, Config.WORLD_W, Config.WORLD_H, null);
        Graphics2D decor = (Graphics2D) g.create();
        decor.scale(Config.WORLD_W / (double) ecoquest.Level.W,
                Config.WORLD_H / (double) ecoquest.Level.H);
        double authoredCleanliness = 30 + getProgressPercent() * 0.70;
        reference.paintDecor(decor, authoredCleanliness, System.nanoTime() / 1_000_000_000.0);
        decor.dispose();
    }

    public boolean updateCarts(double dt, Player player) {
        cartCooldown = Math.max(0, cartCooldown - dt);
        for (MarketCart cart : carts) cart.update(dt);
        if (cartCooldown > 0) return false;
        Rectangle2D feet = new Rectangle2D.Double(player.getX() + 3, player.getY() + 11, 10, 5);
        for (MarketCart cart : carts) {
            if (cart.hitBounds().intersects(feet)) {
                cartCooldown = 1.8;
                return true;
            }
        }
        return false;
    }

    public void updateKing(double dt) {
        if (wasteKing != null) wasteKing.update(dt, getProgressPercent());
    }

    public void kingReact() {
        if (wasteKing != null) wasteKing.react();
    }

    public int timeLimitSeconds() { return reference.cfg.timeLimitSec(); }

    private Rectangle scale(Rectangle r) {
        double sx = Config.WORLD_W / (double) ecoquest.Level.W;
        double sy = Config.WORLD_H / (double) ecoquest.Level.H;
        return new Rectangle((int) Math.round(r.x * sx), (int) Math.round(r.y * sy),
                Math.max(1, (int) Math.round(r.width * sx)),
                Math.max(1, (int) Math.round(r.height * sy)));
    }
}
