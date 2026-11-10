package ecoquest;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Rules and state of one level run. No Swing in here, so it can be tested without a window. */
public final class Game {
    public enum State { INTRO, PLAY, PAUSED, WON, LOST }

    public static final class Item {
        public final TrashDef def;
        public double x, y;
        public final double phase;

        Item(TrashDef def, double x, double y, double phase) {
            this.def = def;
            this.x = x;
            this.y = y;
            this.phase = phase;
        }
    }

    public static final class Npc {
        public double x, y, dx, dy, pause, retarget;
        public int facing, frame;
        public final Rectangle zone;
        public final Color hat, tunic;

        Npc(double x, double y, Rectangle zone, Color hat, Color tunic) {
            this.x = x;
            this.y = y;
            this.zone = zone;
            this.hat = hat;
            this.tunic = tunic;
        }
    }

    public static final class Cart {
        public double x;
        public int dir = 1;
        public final Level.Lane lane;

        Cart(Level.Lane lane, double x) {
            this.lane = lane;
            this.x = x;
        }

        public Rectangle2D hit() {
            return new Rectangle2D.Double(x - 18, lane.y() - 14, 36, 14);
        }
    }

    public static final class Pop {
        public final double x;
        public double y, age;
        public final String text;
        public final Color color;

        Pop(double x, double y, String text, Color color) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
        }
    }

    // ---- world
    public final Level level;
    public final List<TrashDef> catalog;
    private final Random rng = new Random();
    public final List<Item> items = new ArrayList<>();
    public final List<Npc> npcs = new ArrayList<>();
    public final List<Cart> carts = new ArrayList<>();
    public final List<Pop> pops = new ArrayList<>();
    public final Deque<TrashDef> carried = new ArrayDeque<>();
    public final Map<WasteType, Double> binFlash = new EnumMap<>(WasteType.class); // + good, - wrong

    // ---- player
    public double px, py;
    public int facing, walkFrame;
    public boolean moving;
    public final boolean[] keys = new boolean[4]; // up, down, left, right
    private double walkClock, stun, cartCooldown;

    // ---- progress
    public State state = State.INTRO;
    public int health, sorted, mistakes, picked, hazSorted;
    public int money, score;
    public int moneyPickup, moneySort, moneyHaz, moneyArea, moneyLevel, moneyBoss, moneyBonus;
    public double cleanliness = 30, timeLeft, time, bossShake;
    public String message = "";
    public Color messageColor = Color.WHITE;
    public double messageTimer;
    public String loseReason = "";
    private double spawnClock;
    private boolean[][] reach; // 4px cells the player can actually walk to

    // ---- what the player can act on right now (for highlights)
    public Item hintItem;
    public WasteType hintBin;

    public Game(Level level) {
        this.level = level;
        this.catalog = TrashCatalog.load(level.map);
        reset();
    }

    public void reset() {
        Level.Config c = level.cfg;
        items.clear();
        npcs.clear();
        carts.clear();
        pops.clear();
        carried.clear();
        for (WasteType t : WasteType.values()) binFlash.put(t, 0.0);
        px = level.start().x;
        py = level.start().y;
        facing = 0;
        health = c.hearts();
        sorted = mistakes = picked = hazSorted = 0;
        money = score = 0;
        moneyPickup = moneySort = moneyHaz = moneyArea = moneyLevel = moneyBoss = moneyBonus = 0;
        cleanliness = 30;
        timeLeft = c.timeLimitSec();
        bossShake = 0;
        spawnClock = 0;
        stun = cartCooldown = 0;
        message = "";
        messageTimer = 0;
        for (Level.Wanderer w : level.shoppers()) {
            npcs.add(new Npc(w.x(), w.y(), w.zone(), new Color(w.hat()), new Color(w.tunic())));
        }
        for (Level.Lane l : level.carts()) carts.add(new Cart(l, l.x1() + rng.nextInt(Math.max(1, l.x2() - l.x1()))));
        buildReach();
        for (int i = 0; i < c.startItems(); i++) spawn(i < c.pileItems());
        state = State.INTRO;
    }

    public Rectangle bossBody() {
        Rectangle2D.Double d = new Rectangle2D.Double(level.pile().x - 26, level.pile().y - 20, 52, 24);
        return d.getBounds();
    }

    public boolean bossAlive() {
        return state != State.WON;
    }

    public double bossSize() {
        return 0.5 + 0.5 * (1.0 - (double) sorted / level.cfg.target());
    }

    // ------------------------------------------------------------------ spawning

    /** Flood fill from the start so trash never spawns in a pocket the player cannot walk into. */
    private void buildReach() {
        int cw = Level.W / 4, ch = Level.H / 4;
        reach = new boolean[cw][ch];
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{level.start().x / 4, level.start().y / 4});
        reach[level.start().x / 4][level.start().y / 4] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= cw || ny >= ch || reach[nx][ny]) continue;
                Rectangle2D f = feet(nx * 4 + 2, ny * 4 + 2);
                if (!level.isFree(f) || bossBody().intersects(f)) continue;
                reach[nx][ny] = true;
                q.add(new int[]{nx, ny});
            }
        }
    }

    private void spawn(boolean nearPile) {
        for (int tries = 0; tries < 80; tries++) {
            double x, y;
            if (nearPile) {
                double a = rng.nextDouble() * Math.PI * 2, rad = 38 + rng.nextDouble() * 26;
                x = level.pile().x + Math.cos(a) * rad * 1.5;
                y = level.pile().y - 4 + Math.sin(a) * rad * 0.8;
            } else {
                x = 20 + rng.nextDouble() * (Level.W - 40);
                y = 20 + rng.nextDouble() * (Level.H - 40);
            }
            if (!spawnOk(x, y, nearPile)) continue;
            items.add(new Item(TrashCatalog.pick(catalog, rng), x, y, rng.nextDouble() * 6.28));
            return;
        }
    }

    private boolean spawnOk(double x, double y, boolean nearPile) {
        Rectangle2D box = new Rectangle2D.Double(x - 8, y - 8, 16, 16);
        if (!level.isFree(box)) return false;
        if (box.intersects(bossBody())) return false;
        int cx = (int) (x / 4), cy = (int) ((y + 8) / 4);
        if (cx < 0 || cy < 0 || cx >= reach.length || cy >= reach[0].length || !reach[cx][cy]) return false;
        for (Rectangle b : level.bins().values()) {
            Rectangle bigger = new Rectangle(b.x - 36, b.y - 28, b.width + 72, b.height + 60);
            if (bigger.intersects(box)) return false; // keep bins easy to reach
        }
        for (Item o : items) {
            if (Math.abs(o.x - x) < (nearPile ? 10 : 20) && Math.abs(o.y - y) < (nearPile ? 8 : 16)) return false;
        }
        return Math.hypot(x - px, y - py) > 30;
    }

    // ------------------------------------------------------------------ input

    public void act() {
        if (state != State.PLAY) return;
        if (stun > 0) return;
        WasteType bin = nearestBin();
        if (bin != null && !carried.isEmpty()) {
            deposit(bin);
            return;
        }
        Item it = nearestItem();
        if (it != null) {
            if (carried.size() >= level.cfg.capacity()) {
                say("Bag full! Sort what you carry at the bins.", new Color(0xffd36b));
            } else {
                items.remove(it);
                carried.addLast(it.def);
                picked++;
                money += 2;
                moneyPickup += 2;
                pop(it.x, it.y - 10, "+2", new Color(0xd8f0ff));
            }
            return;
        }
        if (!carried.isEmpty()) say("Walk to a bin to drop " + carried.peekLast().name() + ".", Color.WHITE);
        else say("Nothing in reach. Look for trash on the ground.", Color.WHITE);
    }

    private void deposit(WasteType bin) {
        TrashDef d = carried.pollLast();
        Rectangle b = level.bins().get(bin);
        double bx = b.getCenterX(), by = b.y - 6;
        if (d.type() == bin) {
            sorted++;
            int earn = 5;
            money += earn;
            moneySort += earn;
            score += 10;
            if (d.type() == WasteType.HAZ) {
                hazSorted++;
                money += 10;
                moneyHaz += 10;
                score += 10;
                pop(bx, by - 10, "+10 hazard", new Color(0xff9a8a));
            }
            pop(bx, by, "+5", new Color(0x9dff9d));
            binFlash.put(bin, 0.4);
            say(d.name() + " belongs in " + bin.label + ". Nice!", new Color(0x9dff9d));
            if (sorted % 10 == 0 && sorted < level.cfg.target()) {
                money += 25;
                moneyArea += 25;
                pop(px, py - 40, "Area cleared +25", new Color(0xffe27a));
            }
            updateClean();
            if (sorted >= level.cfg.target()) win();
        } else {
            mistakes++;
            health--;
            score = Math.max(0, score - 5);
            bossShake = 0.6;
            binFlash.put(bin, -0.5);
            pop(bx, by, "WRONG", new Color(0xff6b6b));
            say(d.name() + " is " + d.type().longName + ", not " + bin.longName + ". " + d.tip(), new Color(0xff9a8a));
            spawn(true);
            spawn(true);
            updateClean();
            if (health <= 0) lose("The Waste King grew too strong. Too many wrong bins.");
        }
    }

    private void updateClean() {
        double c = 30 + 70.0 * sorted / level.cfg.target() - 2.0 * mistakes;
        cleanliness = Math.max(0, Math.min(100, c));
    }

    private void win() {
        moneyLevel = 100;
        moneyBoss = 150;
        moneyBonus = cleanliness >= 95 ? 50 : 0;
        money += moneyLevel + moneyBoss + moneyBonus;
        state = State.WON;
        say("The Waste King is defeated!", new Color(0xffe27a));
    }

    private void lose(String why) {
        loseReason = why;
        state = State.LOST;
    }

    private void say(String text, Color c) {
        message = text;
        messageColor = c;
        messageTimer = 4.5;
    }

    private void pop(double x, double y, String text, Color c) {
        pops.add(new Pop(x, y, text, c));
    }

    // ------------------------------------------------------------------ queries

    private double distToItem(Item it) {
        return Math.hypot(it.x - px, it.y - (py - 8));
    }

    public Item nearestItem() {
        Item best = null;
        double bd = 34;
        for (Item it : items) {
            double d = distToItem(it);
            if (d < bd) {
                bd = d;
                best = it;
            }
        }
        return best;
    }

    public WasteType nearestBin() {
        WasteType best = null;
        double bd = 30;
        double x = px, y = py - 8;
        for (Map.Entry<WasteType, Rectangle> e : level.bins().entrySet()) {
            Rectangle r = e.getValue();
            double dx = Math.max(Math.max(r.x - x, 0), x - r.getMaxX());
            double dy = Math.max(Math.max(r.y - y, 0), y - r.getMaxY());
            double d = Math.hypot(dx, dy);
            if (d < bd) {
                bd = d;
                best = e.getKey();
            }
        }
        return best;
    }

    private Rectangle2D feet(double x, double y) {
        return new Rectangle2D.Double(x - 6, y - 8, 12, 8);
    }

    private boolean blocked(double x, double y) {
        Rectangle2D now = feet(px, py), box = feet(x, y);
        if (!level.isFree(box)) return true;
        if (bossAlive() && bossBody().intersects(box)) return true;
        for (Npc n : npcs) {
            Rectangle2D nb = feet(n.x, n.y);
            if (nb.intersects(box) && !nb.intersects(now)) return true;
        }
        return false;
    }

    private boolean inPuddle() {
        for (Rectangle p : level.puddles()) {
            double nx = (px - p.getCenterX()) / (p.width / 2.0), ny = ((py - 4) - p.getCenterY()) / (p.height / 2.0);
            if (nx * nx + ny * ny <= 1) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ update

    public void update(double dt) {
        time += dt;
        for (Pop p : pops) {
            p.age += dt;
            p.y -= 18 * dt;
        }
        pops.removeIf(p -> p.age > 1.2);
        if (state != State.PLAY) return;

        timeLeft -= dt;
        if (timeLeft <= 0) {
            timeLeft = 0;
            lose("Time is up. The market is still full of trash.");
            return;
        }
        messageTimer = Math.max(0, messageTimer - dt);
        bossShake = Math.max(0, bossShake - dt);
        cartCooldown = Math.max(0, cartCooldown - dt);
        stun = Math.max(0, stun - dt);
        for (WasteType t : WasteType.values()) {
            double f = binFlash.get(t);
            binFlash.put(t, f > 0 ? Math.max(0, f - dt) : Math.min(0, f + dt));
        }

        movePlayer(dt);
        for (Npc n : npcs) moveNpc(n, dt);
        for (Cart c : carts) moveCart(c, dt);
        spawnTick(dt);

        hintItem = carried.size() < level.cfg.capacity() ? nearestItem() : null;
        hintBin = carried.isEmpty() ? null : nearestBin();
    }

    private void movePlayer(double dt) {
        double ax = (keys[3] ? 1 : 0) - (keys[2] ? 1 : 0);
        double ay = (keys[1] ? 1 : 0) - (keys[0] ? 1 : 0);
        moving = (ax != 0 || ay != 0) && stun <= 0;
        if (!moving) {
            walkClock = 0;
            return;
        }
        double len = Math.hypot(ax, ay);
        double speed = 98 * (inPuddle() ? 0.55 : 1.0);
        double dx = ax / len * speed * dt, dy = ay / len * speed * dt;
        if (!blocked(px + dx, py)) px += dx;
        if (!blocked(px, py + dy)) py += dy;
        if (Math.abs(ax) > 0) facing = ax < 0 ? 2 : 3;
        else facing = ay < 0 ? 1 : 0;
        walkClock += dt;
        walkFrame = (int) (walkClock * 8);
    }

    private void moveNpc(Npc n, double dt) {
        n.retarget -= dt;
        if (n.pause > 0) {
            n.pause -= dt;
            n.dx = n.dy = 0;
            return;
        }
        if (n.retarget <= 0 || (n.dx == 0 && n.dy == 0)) {
            n.retarget = 1.5 + rng.nextDouble() * 2.5;
            int d = rng.nextInt(5);
            n.dx = d == 0 ? 1 : d == 1 ? -1 : 0;
            n.dy = d == 2 ? 1 : d == 3 ? -1 : 0;
            if (d == 4) n.pause = 0.8 + rng.nextDouble() * 1.6;
        }
        double nx = n.x + n.dx * 34 * dt, ny = n.y + n.dy * 34 * dt;
        Rectangle2D box = feet(nx, ny);
        boolean ok = n.zone.contains(box) && level.isFree(box) && !box.intersects(feet(px, py).getBounds2D())
                && !(bossAlive() && bossBody().intersects(box));
        if (ok) {
            n.x = nx;
            n.y = ny;
            n.frame = (int) (time * 6);
            if (n.dx != 0) n.facing = n.dx < 0 ? 2 : 3;
            else if (n.dy != 0) n.facing = n.dy < 0 ? 1 : 0;
        } else {
            n.retarget = 0;
            n.dx = n.dy = 0;
            n.pause = 0.2;
        }
    }

    private void moveCart(Cart c, double dt) {
        c.x += c.dir * c.lane.speed() * dt;
        if (c.x > c.lane.x2()) c.dir = -1;
        if (c.x < c.lane.x1()) c.dir = 1;
        if (cartCooldown <= 0 && c.hit().intersects(feet(px, py))) {
            cartCooldown = 1.8;
            stun = 0.5;
            if (!carried.isEmpty()) {
                TrashDef d = carried.pollLast();
                items.add(new Item(d, px + c.dir * 24, py - 4, rng.nextDouble() * 6.28));
                say("A hand cart bumped you! You dropped " + d.name() + ".", new Color(0xffd36b));
            } else {
                say("A hand cart bumped you!", new Color(0xffd36b));
            }
            double ny = py + (py > c.lane.y() - 7 ? 20 : -20);
            if (!blocked(px, ny)) py = ny;
        }
    }

    private void spawnTick(double dt) {
        spawnClock += dt;
        int need = level.cfg.target() - sorted;
        int have = items.size() + carried.size();
        double every = have < need ? 1.0 : level.cfg.spawnEvery();
        if (spawnClock >= every) {
            spawnClock = 0;
            if (have < need || items.size() < level.cfg.maxGround()) spawn(false);
        }
    }

    // ------------------------------------------------------------------ menu-level controls

    public void start() {
        if (state == State.INTRO) {
            state = State.PLAY;
            say("Pick up trash with E or Space. Drop it at the matching bin.", Color.WHITE);
        }
    }

    public void togglePause() {
        if (state == State.PLAY) state = State.PAUSED;
        else if (state == State.PAUSED) state = State.PLAY;
    }

    public void restart() {
        reset();
        state = State.PLAY;
    }
}
