package com.ecoquest.level2;

import com.ecoquest.level1.BossCore;
import com.ecoquest.level1.BossParticles;
import com.ecoquest.level1.Config;
import com.ecoquest.level1.Entity;
import com.ecoquest.level1.Level;
import com.ecoquest.level1.PixelCanvas;
import com.ecoquest.level1.Player;
import com.ecoquest.level1.TrashBoss;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Random;

import static com.ecoquest.level1.PixelCanvas.ease;
import static com.ecoquest.level1.PixelCanvas.easeIn;
import static com.ecoquest.level1.PixelCanvas.easeOut;
import static com.ecoquest.level1.PixelCanvas.easeOutBack;
import static com.ecoquest.level1.PixelCanvas.span;

/**
 * The Level 2 boss, following the Level 1 rules ({@link TrashBoss}): its life is the trash left in
 * the park. It bursts out of a pile of leaves, roars, then prowls the park on four legs, pausing to
 * sniff and glare at the player, and chases the player whenever they come within
 * {@value #CHASE_RANGE} px. Its abilities, all telegraphed:
 * <ul>
 *   <li><b>Garbage spew</b> (every {@value #THROW_INTERVAL} s): rears up and spews a garbage bag at a
 *       marked spot (1 HP on a hit, bursts into trash).</li>
 *   <li><b>Pounce</b>: crouches and wiggles while a red line shows its path, then lunges along it
 *       (1 HP and knock-back on contact). Slamming into a tree, bench or the pond leaves it dazed.</li>
 *   <li><b>Leaf burrow</b> (phase 2+): dives into a leaf pile and tunnels after the player as a
 *       rustling mound, then bursts up under a warning ring (1 HP). It comes up dazed for
 *       {@value #DAZE_TIME} s, giving the player time to run.</li>
 *   <li><b>Frenzy</b> (phase 3): pounces twice in a row, more often, and prowls faster.</li>
 * </ul>
 * At 0 HP it shudders, deflates like a punctured bag and flowers sprout where it lay.
 */
public class LitterBeast extends Entity implements TrashBoss {
    private enum State {
        EMERGE, ROAM, SPEW, POUNCE_WIND, POUNCE_LUNGE, POUNCE_LAND, DIVE, TUNNEL, SURFACE, DAZED, DYING, GONE
    }

    private static final double ACCEL = 3.2, STRIDE = 13;
    /** Prowling speed per phase (index = phase - 1). */
    private static final double[] SPEED = {28, 34, 42};
    /** Within this distance (px) it stops wandering and chases the player. */
    public static final double CHASE_RANGE = 130;
    /** Seconds between garbage-bag spews, and before the first one after it rises. */
    public static final double THROW_INTERVAL = 14, FIRST_THROW_DELAY = 4;
    private static final double CONTACT_RANGE = 20;
    private static final double SPEW_TIME = 1.05, SPEW_RELEASE = 0.42;
    // pounce
    private static final double POUNCE_WIND = 0.8, FRENZY_WIND = 0.45, POUNCE_LUNGE = 0.32, POUNCE_LAND = 0.35;
    private static final double POUNCE_RANGE = 120, POUNCE_MIN = 40, POUNCE_LEN = 96;
    private static final double[] POUNCE_EVERY = {7, 6, 4.5};
    // leaf burrow
    private static final double DIVE_TIME = 0.6, TUNNEL_CHASE = 2.2, WARN_TIME = 0.65, SURFACE_TIME = 0.45;
    private static final double BURROW_EVERY = 13, MOUND_SPEED = 52, BURST_RADIUS = 16;
    /** Seconds it stays dazed after a burrow burst (or after slamming into something mid-pounce). */
    public static final double DAZE_TIME = 2.0;
    /** Emerge timeline: leaf pile stirs, the beast rises, then it roars. */
    private static final double PILE_TIME = 0.85, RISE_TIME = 0.6, ROAR_TIME = 1.05;
    private static final double EMERGE_TIME = PILE_TIME + RISE_TIME + ROAR_TIME;
    private static final double DEATH_TIME = 2.3, FADE_TIME = 1.0;
    private static final double TURN_RATE = 8;

    private static final int[] LEAF_COLS = {0x8A5A2B, 0xC7742E, 0xD9B348, 0x7C8C38, 0xA8452A, 0xF6B3C8};
    private static final int[] TRASH_COLS = {0xCF3B30, 0x5AA7E0, 0xE9953A, 0xE7CF4A, 0xEDEDED, 0x7CC46A, 0xB98A4C};
    private static final int[] DUST = {0xC9C2AE, 0xB2AA94, 0xDAD4C2};
    private static final int[] AIR = {0xE8F2E0, 0xD2E6CC};

    private final Random rng = new Random();
    private final PixelCanvas canvas = new PixelCanvas(LitterBeastArt.W, LitterBeastArt.H);
    private final PixelCanvas pile = new PixelCanvas(44, 20);
    private final BossParticles fx = new BossParticles();
    private final LitterBeastArt.Pose pose = new LitterBeastArt.Pose();
    private final BossCore core = new BossCore();

    private State state = State.EMERGE;
    private double time, stateTime;
    private double tx, ty, vx, vy, wait;
    private boolean hasTarget;
    private double walkPhase, walkBlend, flip = -1, look;
    private int facing = -1;
    private double throwTimer = FIRST_THROW_DELAY, pounceTimer = 5, burrowTimer = 4;
    private boolean burstDone;
    private double leafClock;
    /** Pounce: start position and total displacement of the lunge; pounces left in this frenzy chain. */
    private double lungeX, lungeY, lungeDX, lungeDY;
    private int pouncesLeft;
    private double windDuration = POUNCE_WIND;
    private Player target;

    public LitterBeast(double x, double y) { super(x, y, 28, 24); }

    // ------------------------------------------------------------ TrashBoss
    @Override public Entity entity() { return this; }
    @Override public String bossName() { return "LITTER BEAST"; }
    @Override public String introMessage() {
        return "The LITTER BEAST burst out of the leaves! Its life is the trash left - sort every piece to beat it!";
    }
    @Override public String phaseMessage(int phase) {
        return phase == 2 ? "PHASE 2: The Litter Beast is weakening... now it burrows under the leaves! Watch for the ring."
                : "PHASE 3: FRENZY! The Litter Beast pounces twice in a row - finish it!";
    }
    @Override public String defeatMessage() { return "The park is clean - the Litter Beast is defeated!"; }
    @Override public Intro intro() {
        return new Intro("SOMETHING IS RUSTLING IN THE LEAVES", "THE LEAF PILE IS MOVING",
                "Every scrap of park litter is calling it...", "THE LITTER BEAST",
                "IT POUNCES, BURROWS AND SPEWS TRASH!", 0xE9A04A);
    }

    @Override public void syncHp(int trashLeft) {
        if (core.syncHp(trashLeft, state != State.EMERGE)) defeat();
    }
    @Override public int getHp() { return core.hp(); }
    @Override public int getPeakHp() { return core.peakHp(); }
    @Override public int getPhase() { return core.phase(); }
    @Override public boolean isEmerging() { return state == State.EMERGE; }
    @Override public boolean isDying() { return state == State.DYING || state == State.GONE; }
    @Override public boolean isGone() { return state == State.GONE; }
    /** Touching it hurts while it prowls, spews or pounces; not while burrowed, surfacing or dazed. */
    @Override public boolean isDangerous() {
        return switch (state) {
            case ROAM, SPEW, POUNCE_WIND, POUNCE_LUNGE, POUNCE_LAND -> true;
            default -> false;
        };
    }
    @Override public double getHitFlash() { return core.hitFlash(); }
    @Override public double getHealFlash() { return core.healFlash(); }
    @Override public List<Strike> consumeStrikes() { return core.consumeStrikes(); }
    @Override public boolean consumeToss() { return core.consumeToss(); }
    @Override public int consumeHpChange() { return core.consumeHpChange(); }
    @Override public int consumePhaseChange() { return core.consumePhaseChange(); }
    @Override public boolean consumeDefeated() { return core.consumeDefeated(); }

    @Override public boolean isTouching(Player p) {
        return isDangerous() && Math.hypot(centerX() - p.centerX(), centerY() - p.centerY()) < CONTACT_RANGE;
    }

    @Override public double[] knockBack(Player p) {
        double dx = p.centerX() - centerX(), dy = p.centerY() - centerY();
        double len = Math.max(0.001, Math.hypot(dx, dy));
        double force = state == State.POUNCE_LUNGE ? 22 : 16;
        return new double[] { dx / len * force, dy / len * force };
    }

    // ------------------------------------------------------------ behaviour

    @Override
    public void update(double dt, Player player, Level level) {
        time += dt;
        stateTime += dt;
        target = player;
        fx.update(dt);
        core.update(dt, player);
        flip += Math.signum(facing - flip) * Math.min(Math.abs(facing - flip), TURN_RATE * dt);
        double toPlayer = player.centerX() - centerX();
        look += ((Math.max(-1, Math.min(1, toPlayer * facing / 40.0))) - look) * Math.min(1, dt * 5);

        switch (state) {
            case EMERGE -> updateEmerge(dt);
            case ROAM -> updateRoam(dt, level, toPlayer);
            case SPEW -> updateSpew(dt, level);
            case POUNCE_WIND -> updatePounceWind(dt, level);
            case POUNCE_LUNGE -> updateLunge(dt, level);
            case POUNCE_LAND -> {
                brake(dt, level);
                if (stateTime >= POUNCE_LAND) {
                    if (pouncesLeft > 0) startPounce(FRENZY_WIND);
                    else setState(State.ROAM);
                }
            }
            case DIVE -> updateDive();
            case TUNNEL -> updateTunnel(dt, level);
            case SURFACE -> { if (stateTime >= SURFACE_TIME) setState(State.DAZED); }
            case DAZED -> updateDazed(dt);
            case DYING -> updateDying(dt);
            case GONE -> { }
        }
    }

    private void updateEmerge(double dt) {
        double gx = centerX(), gy = y + h;
        if (stateTime < PILE_TIME) {
            leafClock += dt;
            if (leafClock > 0.07) {      // the pile rustles, flicking leaves up
                leafClock = 0;
                fx.add(BossParticles.LEAF, gx + rng.nextGaussian() * 9, gy + rng.nextGaussian() * 2, 4,
                        rng.nextGaussian() * 14, rng.nextGaussian() * 4, 30 + rng.nextDouble() * 25,
                        1.6, LEAF_COLS[rng.nextInt(LEAF_COLS.length)], 2);
            }
        } else if (!burstDone) {         // bursts out
            burstDone = true;
            fx.burst(BossParticles.LEAF, gx, gy, 6, 26, 55, 60, 2.2, LEAF_COLS);
            fx.burst(BossParticles.PUFF, gx, gy, 2, 8, 28, 6, 0.8, DUST);
        }
        if (stateTime >= PILE_TIME + RISE_TIME && stateTime - dt < PILE_TIME + RISE_TIME) {
            fx.burst(BossParticles.PUFF, gx + 14 * flip, gy - 14, 0, 5, 22, 4, 0.6, AIR);   // roar breath
        }
        if (stateTime >= EMERGE_TIME) {
            setState(State.ROAM);
            if (core.hpKnown() && core.hp() == 0) syncHp(0);   // the park was already clean
        }
    }

    private void updateRoam(double dt, Level level, double toPlayer) {
        int phase = core.phase();
        throwTimer -= dt;
        pounceTimer -= dt;
        if (phase >= 2) burrowTimer -= dt;
        double dist = Math.hypot(target.centerX() - centerX(), target.centerY() - centerY());

        if (throwTimer <= 0) {
            throwTimer = THROW_INTERVAL;
            if (Math.abs(toPlayer) > 4) facing = toPlayer > 0 ? 1 : -1;   // turns to face its target
            setState(State.SPEW);
            return;
        }
        if (phase >= 2 && burrowTimer <= 0) {
            burrowTimer = BURROW_EVERY;
            startDive();
            return;
        }
        if (pounceTimer <= 0 && dist < POUNCE_RANGE && dist > POUNCE_MIN) {
            pounceTimer = POUNCE_EVERY[phase - 1];
            pouncesLeft = phase == 3 ? 1 : 0;      // frenzy: a second pounce right after
            startPounce(POUNCE_WIND);
            return;
        }

        // steering: chase the player when close, otherwise wander, slow down on arrival, look around
        double dvx = 0, dvy = 0;
        if (dist < CHASE_RANGE) {
            double dx = target.centerX() - centerX(), dy = target.centerY() - centerY();
            double s = SPEED[phase - 1] * Math.max(0, Math.min(1, (dist - 8) / 14));
            dvx = dx / Math.max(0.001, dist) * s;
            dvy = dy / Math.max(0.001, dist) * s;
            hasTarget = false;
            wait = 0;
        } else if (wait > 0) wait -= dt;
        else if (!hasTarget) pickTarget(level);
        else {
            double dx = tx - x, dy = ty - y, d = Math.hypot(dx, dy);
            if (d < 2) {
                hasTarget = false;
                wait = 0.7 + rng.nextDouble() * 1.6;
            } else {
                double s = SPEED[phase - 1] * Math.min(1, d / 18 + 0.15);
                dvx = dx / d * s;
                dvy = dy / d * s;
            }
        }
        steer(dt, level, dvx, dvy);
        double speed = Math.hypot(vx, vy);
        if (speed < 1.5 && Math.abs(toPlayer) > 24) facing = toPlayer > 0 ? 1 : -1;   // glare at the player
        // kicks up a little dust with heavy steps
        if (speed > 6 && rng.nextDouble() < dt * 3)
            fx.add(BossParticles.PUFF, centerX() - flip * 8, y + h, 0, -vx * 0.3, 0, 3, 0.5, DUST[rng.nextInt(DUST.length)], 2);
    }

    /** Eases the velocity toward (dvx, dvy), moves with collision, and keeps the gait in step. */
    private void steer(double dt, Level level, double dvx, double dvy) {
        double k = Math.min(1, dt * ACCEL);
        vx += (dvx - vx) * k;
        vy += (dvy - vy) * k;
        double nx = x + vx * dt, ny = y + vy * dt;
        if (!blocked(level, nx, ny)) {
            x = nx;
            y = ny;
        } else if (!blocked(level, nx, y)) {       // slide along whatever is in the way
            x = nx;
            vy *= 0.5;
        } else if (!blocked(level, x, ny)) {
            y = ny;
            vx *= 0.5;
        } else {
            hasTarget = false;
            vx *= 0.3;
            vy *= 0.3;
            wait = 0.3;
        }
        double speed = Math.hypot(vx, vy);
        walkPhase += speed * dt / STRIDE;
        double targetBlend = Math.min(1, speed / (SPEED[0] * 0.55));
        walkBlend += (targetBlend - walkBlend) * Math.min(1, dt * 6);
        if (vx > 2) facing = 1;
        else if (vx < -2) facing = -1;
    }

    private void brake(double dt, Level level) { steer(dt, level, 0, 0); }

    /** Collides with its feet only (like the Level 1 boss), so it fits between benches and trees. */
    private boolean blocked(Level level, double nx, double ny) {
        if (nx < 0 || ny < 0 || nx + w > Config.WORLD_W || ny + h > Config.WORLD_H) return true;
        return level.isBlocked(new Rectangle2D.Double(nx + 6, ny + h - 9, w - 12, 9));
    }

    private void updateSpew(double dt, Level level) {
        brake(dt, level);
        double before = stateTime - dt;
        if (before < SPEW_RELEASE && stateTime >= SPEW_RELEASE) {   // the bag flies out of its mouth
            double mx = centerX() + 22 * flip, my = y + h - 2;
            core.throwAtPlayer(mx, y + 4, target, 0.55, 1.05, 46);
            fx.burst(BossParticles.BIT, mx, my, 16, 5, 40, 50, 1.2, TRASH_COLS);
            fx.burst(BossParticles.LEAF, mx, my, 14, 5, 30, 30, 1.3, LEAF_COLS);
            fx.burst(BossParticles.PUFF, mx, my, 12, 4, 20, 8, 0.6, DUST);
        }
        if (stateTime >= SPEW_TIME) setState(State.ROAM);
    }

    // ---- pounce
    private void startPounce(double windTime) {
        if (pouncesLeft > 0 && state == State.POUNCE_LAND) pouncesLeft--;
        setState(State.POUNCE_WIND);
        windDuration = windTime;
        aimPounce();
    }

    /** Points the lunge at the player (it keeps tracking for the first half of the wind-up). */
    private void aimPounce() {
        double dx = target.centerX() - centerX(), dy = target.centerY() - centerY();
        double d = Math.max(0.001, Math.hypot(dx, dy));
        double len = Math.min(POUNCE_LEN, d + 18);
        lungeDX = dx / d * len;
        lungeDY = dy / d * len;
        if (Math.abs(dx) > 3) facing = dx > 0 ? 1 : -1;
    }

    private void updatePounceWind(double dt, Level level) {
        brake(dt, level);
        if (stateTime < windDuration * 0.5) aimPounce();
        if (stateTime >= windDuration) {
            lungeX = x;
            lungeY = y;
            setState(State.POUNCE_LUNGE);
            fx.burst(BossParticles.PUFF, centerX(), y + h, 0, 5, 24, 4, 0.5, DUST);
        }
    }

    private void updateLunge(double dt, Level level) {
        double k = easeOut(stateTime / POUNCE_LUNGE);
        double nx = lungeX + lungeDX * k, ny = lungeY + lungeDY * k;
        // step in small pieces so it cannot pass through a thin bench or tree trunk
        int steps = (int) Math.ceil(Math.max(Math.abs(nx - x), Math.abs(ny - y)) / 3) + 1;
        double sx = (nx - x) / steps, sy = (ny - y) / steps;
        for (int i = 0; i < steps; i++) {
            if (blocked(level, x + sx, y + sy)) {   // slammed into something: dazed
                vx = vy = 0;
                fx.burst(BossParticles.PUFF, centerX() + flip * 12, y + h - 8, 6, 8, 30, 8, 0.6, DUST);
                fx.burst(BossParticles.LEAF, centerX() + flip * 12, y + h - 8, 12, 8, 40, 40, 1.4, LEAF_COLS);
                pouncesLeft = 0;
                setState(State.DAZED);
                return;
            }
            x += sx;
            y += sy;
        }
        vx = lungeDX / POUNCE_LUNGE * (1 - k);
        vy = lungeDY / POUNCE_LUNGE * (1 - k);
        walkPhase += Math.hypot(sx, sy) * steps / STRIDE * 0.6;
        walkBlend = 1;
        if (rng.nextDouble() < dt * 30)
            fx.add(BossParticles.LEAF, centerX() - flip * 10, y + h, 2, -lungeDX * 0.3, -lungeDY * 0.3, 20, 1.0,
                    LEAF_COLS[rng.nextInt(LEAF_COLS.length)], 2);
        if (stateTime >= POUNCE_LUNGE) {
            setState(State.POUNCE_LAND);
            fx.burst(BossParticles.PUFF, centerX(), y + h, 0, 6, 26, 4, 0.5, DUST);
        }
    }

    // ---- leaf burrow
    private void startDive() {
        vx = vy = 0;
        setState(State.DIVE);
        fx.burst(BossParticles.LEAF, centerX(), y + h, 4, 16, 40, 40, 1.6, LEAF_COLS);
    }

    private void updateDive() {
        if (stateTime >= DIVE_TIME) {
            setState(State.TUNNEL);
            fx.burst(BossParticles.PUFF, centerX(), y + h, 1, 6, 20, 4, 0.5, DUST);
        }
    }

    /** The rustling mound follows the player, stops, shows a warning ring, then the beast bursts up. */
    private void updateTunnel(double dt, Level level) {
        double fx0 = centerX(), fy0 = y + h;
        if (stateTime < TUNNEL_CHASE) {
            double dx = target.centerX() - fx0, dy = (target.getY() + Player.H - 3) - fy0;
            double d = Math.hypot(dx, dy);
            if (d > 1) {
                double step = Math.min(d, MOUND_SPEED * dt);
                double mx = dx / d * step, my = dy / d * step;
                if (!blocked(level, x + mx, y)) x += mx;
                if (!blocked(level, x, y + my)) y += my;
                if (Math.abs(mx) > 0.05) facing = mx > 0 ? 1 : -1;
            }
            leafClock += dt;
            if (leafClock > 0.06) {
                leafClock = 0;
                fx.add(BossParticles.LEAF, centerX() + rng.nextGaussian() * 5, y + h, 2, rng.nextGaussian() * 12,
                        rng.nextGaussian() * 4, 18 + rng.nextDouble() * 14, 1.0, LEAF_COLS[rng.nextInt(LEAF_COLS.length)], 2);
            }
        }
        if (stateTime >= TUNNEL_CHASE + WARN_TIME) {   // bursts up under the ring
            double gx = centerX(), gy = y + h;
            core.strike(new Strike(gx, gy, BURST_RADIUS, true, 0, 0, 0, "The Litter Beast burst up under you! -1 HP"));
            fx.burst(BossParticles.LEAF, gx, gy, 6, 30, 60, 70, 2.0, LEAF_COLS);
            fx.burst(BossParticles.PUFF, gx, gy, 2, 10, 34, 6, 0.8, DUST);
            setState(State.SURFACE);
        }
    }

    private void updateDazed(double dt) {
        if ((leafClock += dt) > 0.5) {
            leafClock = 0;
            fx.add(BossParticles.SPARK, centerX() + flip * 10 + rng.nextGaussian() * 4, y + h, 26, 0, 0, 6, 0.6, 0xFFF2A0, 1);
        }
        if (stateTime >= DAZE_TIME) {
            pounceTimer = Math.max(pounceTimer, 2);   // gives the player a head start
            setState(State.ROAM);
        }
    }

    private void pickTarget(Level level) {
        for (int i = 0; i < 40 && !hasTarget; i++) {
            tx = rng.nextInt(Config.WORLD_W - w);
            ty = rng.nextInt(Config.WORLD_H - h);
            hasTarget = !level.isBlocked(new Rectangle2D.Double(tx, ty, w, h)) && Math.hypot(tx - x, ty - y) > 30;
        }
    }

    private void updateDying(double dt) {
        double u = stateTime / DEATH_TIME;
        double gx = centerX(), gy = y + h;
        leafClock += dt;
        if (u > 0.2 && u < 0.75 && leafClock > 0.05) {   // air hisses out, leaves fall off
            leafClock = 0;
            fx.add(BossParticles.LEAF, gx + rng.nextGaussian() * 9, gy - 10, 8 + rng.nextDouble() * 6,
                    rng.nextGaussian() * 20, rng.nextGaussian() * 6, 10 + rng.nextDouble() * 20, 2.0,
                    LEAF_COLS[rng.nextInt(LEAF_COLS.length)], 2);
            fx.add(BossParticles.PUFF, gx - 12 * flip, gy - 12, 0, -flip * 26, -4, 6, 0.6, AIR[rng.nextInt(AIR.length)], 2);
        }
        if (u >= 0.72 && u - dt / DEATH_TIME < 0.72)
            for (int i = 0; i < 12; i++)
                fx.add(BossParticles.SPARK, gx + rng.nextGaussian() * 12, gy - 4, 2 + rng.nextDouble() * 10,
                        rng.nextGaussian() * 3, 0, 8 + rng.nextDouble() * 8, 0.9 + rng.nextDouble() * 0.6,
                        rng.nextBoolean() ? 0xFFFFFF : 0xFFF2A0, 1);
        if (stateTime >= DEATH_TIME + FADE_TIME) setState(State.GONE);
    }

    private void setState(State s) {
        state = s;
        stateTime = 0;
        leafClock = 0;
    }

    private void defeat() {
        if (state == State.DYING || state == State.GONE) return;
        setState(State.DYING);
        vx = vy = 0;
        fx.burst(BossParticles.PUFF, centerX(), y + h - 8, 6, 8, 30, 6, 0.7, AIR);
    }

    // ------------------------------------------------------------ drawing

    private void buildPose() {
        LitterBeastArt.Pose p = pose;
        p.t = time;
        p.walk = walkPhase;
        p.walkAmt = walkBlend;
        p.breathe = Math.sin(time * 2 * Math.PI / 2.4);
        p.crouch = p.rear = p.headDX = p.headDY = p.shake = p.deflate = p.legsFold = p.bloom = 0;
        p.jaw = 0.12 + 0.08 * Math.sin(time * 2 * Math.PI / 2.4 - 0.5);
        p.ear = Math.sin(time * 2.6 - 0.8) * 0.15 * (1 - walkBlend) + Math.sin(walkPhase * 4 * Math.PI - 1.2) * 0.3 * walkBlend;
        p.look = look;
        p.eyes = (time % 3.7) < 0.13 ? 1 : 0;
        p.glow = 0.6;
        p.flies = p.stink = true;
        if (isDangerous() && core.phase() == 3) { p.eyes = 2; p.glow = 1; }
        // idle sniff: little snout twitches while standing
        if (walkBlend < 0.3 && state == State.ROAM)
            p.headDX = Math.max(0, Math.sin(time * 9)) * 0.6 * (Math.sin(time * 0.9) > 0.6 ? 1 : 0);

        switch (state) {
            case EMERGE -> {
                double r = span(stateTime, PILE_TIME + RISE_TIME, EMERGE_TIME);
                if (stateTime < PILE_TIME + RISE_TIME) {
                    p.crouch = 0.8;
                    p.eyes = 2;
                } else {
                    double in = ease(r / 0.2), out = ease((r - 0.8) / 0.2);
                    double amt = in * (1 - out);
                    p.rear = 0.7 * amt;
                    p.jaw = Math.max(p.jaw, amt);
                    p.headDY = -2.5 * amt;
                    p.headDX = 1.5 * amt;
                    p.ear = -0.5 * amt;
                    p.shake = Math.sin(time * 62) * 0.8 * amt;
                    p.eyes = 2;
                    p.glow = 1;
                }
            }
            case SPEW -> {
                double u = stateTime / SPEW_TIME;
                double k1 = ease(span(u, 0, 0.38)), k2 = easeOut(span(u, 0.38, 0.5)), k3 = ease(span(u, 0.56, 1));
                p.crouch = k1 * (1 - k2);
                p.rear = k2 * (1 - k3);
                p.jaw = Math.max(p.jaw, Math.max(0.3 * k1 * (1 - k2), k2 * (1 - k3)));
                p.headDX = -2 * k1 * (1 - k2) + 3.5 * k2 * (1 - k3);
                p.headDY = 2 * k1 * (1 - k2) - 2 * k2 * (1 - k3);
                p.ear += -0.4 * k2 * (1 - k3);
                p.shake = Math.sin(time * 70) * 0.8 * p.rear;
                if (u > 0.15 && u < 0.85) { p.eyes = 2; p.glow = 1; }
            }
            case POUNCE_WIND -> {       // crouch low and wiggle, ears back, glaring
                double k = ease(stateTime / Math.min(0.35, windDuration));
                p.crouch = k;
                p.shake = Math.sin(time * 38) * 0.7 * k;
                p.headDX = -1.5 * k;
                p.headDY = 1.5 * k;
                p.ear = -0.5 * k;
                p.jaw = Math.max(p.jaw, 0.35 * k);
                p.walkAmt = 0;
                p.eyes = 2;
                p.glow = 1;
            }
            case POUNCE_LUNGE -> {      // stretched out mid-leap, jaws wide
                p.rear = 0.6;
                p.headDX = 3.5;
                p.headDY = -1;
                p.jaw = 1;
                p.ear = -0.6;
                p.eyes = 2;
                p.glow = 1;
            }
            case POUNCE_LAND -> {
                double k = 1 - ease(stateTime / POUNCE_LAND);
                p.crouch = 0.7 * k;
                p.jaw = Math.max(p.jaw, 0.5 * k);
            }
            case DIVE -> {
                p.crouch = 1;
                p.headDY = 2;
                p.ear = -0.6;
                p.eyes = 2;
            }
            case SURFACE -> {           // bursts up roaring
                double k = 1 - ease(stateTime / SURFACE_TIME);
                p.rear = 0.7 * k;
                p.jaw = Math.max(p.jaw, k);
                p.headDY = -2 * k;
                p.shake = Math.sin(time * 60) * 0.7 * k;
                p.eyes = 2;
                p.glow = 1;
            }
            case DAZED -> {             // dizzy: swaying, tongue lolling, head drooping
                double in = ease(stateTime / 0.25), out = ease((stateTime - (DAZE_TIME - 0.3)) / 0.3);
                double k = in * (1 - out);
                p.eyes = 3;
                p.jaw = Math.max(p.jaw, 0.55 * k);
                p.headDY = 2.2 * k;
                p.headDX = Math.sin(time * 3.2) * 1.2 * k;
                p.shake = Math.sin(time * 2.6) * 0.8 * k;
                p.ear = 0.5 * k;
                p.walkAmt = 0;
                p.crouch = 0.3 * k;
            }
            case DYING, GONE -> {
                double u = Math.min(1, stateTime / DEATH_TIME);
                double shudder = 1 - span(u, 0.15, 0.3);
                p.shake = Math.sin(time * 75) * 1.1 * shudder;
                p.jaw = 0.8 * shudder + 0.25 * (1 - shudder);
                p.rear = 0.3 * shudder;
                p.eyes = u < 0.08 ? 2 : u < 0.7 ? 3 : 1;
                double d = ease(span(u, 0.25, 0.7));
                p.deflate = d;
                p.legsFold = d;
                p.headDY = d * 3;
                p.headDX = -d * 2;
                p.breathe = 0;
                p.walkAmt = 0;
                p.flies = p.stink = u < 0.3;
                p.bloom = span(u, 0.72, 1);
            }
            default -> { }
        }
    }

    @Override
    public void draw(Graphics2D g) {
        if (state == State.GONE) {
            fx.draw(g);
            return;
        }
        double ax = centerX(), gy = y + h;
        if (state == State.TUNNEL) {        // only a rustling leaf mound shows where it is
            double grow = Math.min(1, stateTime / 0.25) * 0.55;
            paintPile(grow, true);
            double wob = Math.sin(time * 26) * 0.8;
            pile.drawTo(g, ax + wob, gy + 2, pile.w / 2.0, pile.h - 1, 1);
            fx.draw(g);
            return;
        }
        buildPose();
        if (core.hitFlash() > 0) pose.shake += Math.sin(time * 80) * core.hitFlash() * 2;
        LitterBeastArt.paint(canvas, pose);
        if (core.hitFlash() > 0) canvas.tint(0xFFFFFF, core.hitFlash() / 0.45 * 0.75);
        else if (core.healFlash() > 0) canvas.tint(0xFF7050, core.healFlash() / 0.45 * 0.4);
        if (state == State.DYING) canvas.fade(1 - span(stateTime, DEATH_TIME, DEATH_TIME + FADE_TIME));

        if (state == State.EMERGE) drawEmerge(g, ax, gy);
        else if (state == State.DIVE) drawInPile(g, ax, gy, 1 - easeIn(stateTime / DIVE_TIME),
                easeOutBack(stateTime / DIVE_TIME) * 0.8, true);
        else if (state == State.SURFACE) drawInPile(g, ax, gy, easeOut(stateTime / (SURFACE_TIME * 0.6)),
                0.8 * (1 - ease(stateTime / SURFACE_TIME)), false);
        else {
            double shadowW = 30 + pose.deflate * 8 - pose.rear * 4;
            int sa = state == State.DYING ? (int) (70 * (1 - span(stateTime, DEATH_TIME, DEATH_TIME + FADE_TIME))) : 70;
            g.setColor(new Color(0, 0, 0, sa));
            g.fillOval((int) Math.round(ax - shadowW / 2 + 2 * flip), (int) Math.round(gy - 3), (int) shadowW, 6);
            canvas.drawTo(g, ax, gy, LitterBeastArt.AX, LitterBeastArt.GROUND, flip);
            if (state == State.DAZED) drawStars(g, ax + flip * 12, gy - 40);
        }
        fx.draw(g);
    }

    /** Leaf pile stirs and swells, then the beast rises out of it. */
    private void drawEmerge(Graphics2D g, double ax, double gy) {
        double grow = easeOutBack(stateTime / PILE_TIME);
        double rise = easeOut(span(stateTime, PILE_TIME, PILE_TIME + RISE_TIME));
        double shrink = 1 - ease(span(stateTime, PILE_TIME + 0.15, PILE_TIME + RISE_TIME + 0.5));
        drawInPile(g, ax, gy, rise, Math.max(0, grow) * shrink, stateTime < PILE_TIME + 0.05);
    }

    /** The beast sunk into a leaf pile: {@code rise} 0 (hidden) .. 1 (fully up). */
    private void drawInPile(Graphics2D g, double ax, double gy, double rise, double pileSize, boolean rumbling) {
        if (rise > 0) {
            g.setColor(new Color(0, 0, 0, (int) (70 * rise)));
            g.fillOval((int) Math.round(ax - 15 * rise), (int) Math.round(gy - 3), (int) Math.round(30 * rise), 6);
            Graphics2D c = (Graphics2D) g.create();
            c.clipRect((int) Math.round(ax - 60), (int) Math.round(gy - 80), 120, 81);
            double sink = (1 - rise) * (LitterBeastArt.H - 4);
            canvas.drawTo(c, ax, gy + sink, LitterBeastArt.AX, LitterBeastArt.GROUND, flip);
            c.dispose();
        }
        if (pileSize > 0.02) {
            double shake = rumbling ? Math.sin(time * 48) * 1.2 * pileSize : 0;
            paintPile(pileSize, rumbling);
            pile.drawTo(g, ax + shake, gy + 2, pile.w / 2.0, pile.h - 1, 1);
        }
    }

    /** Three little stars circling its head while it is dazed. */
    private void drawStars(Graphics2D g, double cx, double cy) {
        for (int i = 0; i < 3; i++) {
            double a = time * 5 + i * Math.PI * 2 / 3;
            int sx = (int) Math.round(cx + Math.cos(a) * 9), sy = (int) Math.round(cy + Math.sin(a) * 3);
            boolean front = Math.sin(a) > 0;
            g.setColor(front ? new Color(0xFFF07A) : new Color(0xC8B850));
            g.fillRect(sx, sy - 1, 1, 3);
            g.fillRect(sx - 1, sy, 3, 1);
            if (front) {
                g.setColor(Color.WHITE);
                g.fillRect(sx, sy, 1, 1);
            }
        }
    }

    private void paintPile(double size, boolean rumbling) {
        pile.clear();
        if (size <= 0.02) return;
        double cx = pile.w / 2.0, base = pile.h - 3;
        double rx = 17 * size, ry = 8 * size;
        int[] ramp = {0x2E1C0C, 0x5A3A1A, 0x7A4E22, 0x9A6630, 0xB98040, 0xD9A55A};
        pile.ball(cx, base - ry * 0.7, rx, ry, ramp, 0);
        pile.ball(cx - rx * 0.45, base - ry * 0.4, rx * 0.55, ry * 0.75, ramp, 0);
        pile.ball(cx + rx * 0.5, base - ry * 0.35, rx * 0.5, ry * 0.7, ramp, 0);
        for (int i = 0; i < 26; i++) {             // individual leaves on the surface
            int hx = Math.floorMod(i * 73856093 ^ 0x5bd1e995, 1000), hy = Math.floorMod(i * 19349663 ^ 0x27d4eb2d, 1000);
            double lx = cx + (hx / 1000.0 - 0.5) * rx * 1.9, ly = base - (hy / 1000.0) * ry * 1.5;
            if (rumbling) ly += Math.sin(time * 30 + i) * 0.5;
            int col = LEAF_COLS[i % LEAF_COLS.length];
            pile.setOnPx(lx, ly, col);
            pile.setOnPx(lx + 1, ly, col);
        }
        pile.outline(0x1E1208);
    }

    /** Telegraphs (pounce path, burrow warning ring), bags in flight and HP numbers, above everything. */
    @Override
    public void drawEffects(Graphics2D g) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (state == State.POUNCE_WIND) {
            double k = Math.min(1, stateTime / windDuration);
            double pulse = 0.5 + 0.5 * Math.sin(time * 22);
            double sx = centerX(), sy = y + h - 4, ex = sx + lungeDX, ey = sy + lungeDY;
            gg.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gg.setColor(new Color(220, 40, 30, (int) (40 + 50 * k)));
            gg.drawLine((int) sx, (int) sy, (int) ex, (int) ey);
            gg.setStroke(new BasicStroke(1.3f));
            gg.setColor(new Color(255, 70, 50, (int) (130 + 110 * pulse * k)));
            gg.drawLine((int) sx, (int) sy, (int) ex, (int) ey);
            double ang = Math.atan2(ey - sy, ex - sx);             // arrow head
            int[] axs = { (int) ex, (int) (ex - Math.cos(ang - 0.5) * 7), (int) (ex - Math.cos(ang + 0.5) * 7) };
            int[] ays = { (int) ey, (int) (ey - Math.sin(ang - 0.5) * 7), (int) (ey - Math.sin(ang + 0.5) * 7) };
            gg.fillPolygon(axs, ays, 3);
        }
        if (state == State.TUNNEL && stateTime >= TUNNEL_CHASE) {   // where it will burst up
            double k = Math.min(1, (stateTime - TUNNEL_CHASE) / WARN_TIME);
            double pulse = 0.5 + 0.5 * Math.sin(time * 24);
            int cx = (int) Math.round(centerX()), cy = (int) Math.round(y + h);
            int r = (int) Math.round(BURST_RADIUS);
            int fill = (int) (r * k);
            gg.setColor(new Color(200, 30, 30, (int) (40 + 80 * k)));
            gg.fillOval(cx - fill, cy - fill / 2, fill * 2, fill);
            gg.setStroke(new BasicStroke(1.4f));
            gg.setColor(new Color(255, 60, 50, (int) (150 + 100 * pulse)));
            gg.drawOval(cx - r, cy - r / 2, r * 2, r);
        }
        gg.dispose();
        core.drawEffects(g, centerX(), y - 16);
    }
}
