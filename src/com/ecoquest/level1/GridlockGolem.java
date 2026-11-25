package com.ecoquest.level1;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static com.ecoquest.level1.PixelCanvas.ease;
import static com.ecoquest.level1.PixelCanvas.easeOut;
import static com.ecoquest.level1.PixelCanvas.span;

/**
 * The Level 5 boss, following the Level 1 rules ({@link TrashBoss}): its life is the trash left in
 * the city center. A scrap golem built from a crushed car, tyres, exhaust pipes and e-waste, with a
 * broken traffic light for a head. It climbs out of a pothole on the main street and stomps after the
 * player. Its traffic light is its tell: <b>green</b> while it walks, <b>yellow</b> while it winds up
 * an attack, <b>red</b> as the attack goes off. Its skills, all telegraphed:
 * <ul>
 *   <li><b>Battery Barrage</b>: lifts its clamp arm and lobs leaking car batteries at red-ringed
 *       spots around the player, one after another (1 HP on a hit; the first leaks into a new piece
 *       of hazardous litter).</li>
 *   <li><b>Oil Slick</b>: hurls a motor-oil can at an amber-ringed spot. It splashes a slick that
 *       stays for {@value #SLICK_SECONDS} s and slows anyone standing in it.</li>
 *   <li><b>Rush Hour Charge</b>: revs its exhaust while a red lane shows its path (the lane stops
 *       following the player partway through), then charges down the lane like a runaway car, all
 *       the way across the map (1 HP and a big knock-back). It smashes through everything in its
 *       path: buildings, jam cars and traffic (cars are flung away), and it bowls litter aside. It
 *       slams to a stop at the end of the lane and is dazed for {@value #DAZE_TIME} s, a safe window.</li>
 *   <li><b>Honk Shockwave</b>: when the player is close, a yellow ring marks its reach, then it
 *       blasts its horn; anyone inside the ring is stunned for {@value #HONK_STUN} s.</li>
 *   <li><b>Traffic Jam</b>: dashed outlines mark spots around the player, then stalled cars skid in
 *       and park there as walls for {@value JamCar#STAND} s (1 HP if one skids into you), and all the
 *       city traffic stops. Its next charge comes soon after - straight through the jam.</li>
 *   <li><b>Smog Belch</b> (phase 2+): its pipes belch smog clouds that drift after the player
 *       (a bit slower than the player walks);
 *       breathing inside one costs 1 HP.</li>
 *   <li><b>Gridlock</b> (phase 3): calls rush hour - the city's cars speed up - and it charges
 *       twice in a row, more often.</li>
 * </ul>
 * At 0 HP it shudders, its light flickers through every colour and settles on green as it falls
 * apart into scrap.
 */
public final class GridlockGolem extends Entity implements TrashBoss {
    private enum State { EMERGE, ROAM, BARRAGE, OIL, REV, CHARGE, CRASH, HONK, JAM, SMOG, DYING, GONE }

    private static final double ACCEL = 3.0, STRIDE = 15, TURN_RATE = 7;
    /** Walking speed per phase (index = phase - 1). */
    private static final double[] SPEED = {22, 27, 33};
    /** Within this distance (px) it stops wandering and walks at the player. */
    public static final double CHASE_RANGE = 150;
    private static final double CONTACT_RANGE = 18, CHARGE_CONTACT = 21;

    // battery barrage: wind-up, gap between batteries, recovery, batteries per phase
    private static final double BARRAGE_WIND = 0.65, BARRAGE_GAP = 0.16, BARRAGE_RECOVER = 0.35;
    private static final int[] BATTERIES = {4, 5, 6};
    private static final double[] BARRAGE_EVERY = {9, 7.5, 6};
    private static final double BATTERY_FLIGHT = 1.05;
    // oil slick
    private static final double OIL_WIND = 0.5, OIL_TIME = 0.9, OIL_FLIGHT = 1.0;
    private static final double[] OIL_EVERY = {11, 9.5, 8};
    public static final double SLICK_SECONDS = 8;
    // rush hour charge: rev (lane follows the player until AIM_SHARE of it), speed; the lane runs to the map edge
    private static final double[] REV_TIME = {1.1, 1.0, 0.75};
    private static final double AIM_SHARE = 0.6, CHARGE_SPEED = 200, FRENZY_SPEED = 235;
    private static final double LANE_MIN = 40, LANE_WIDTH = 22, SMASH_EVERY = 0.12;
    private static final double[] CHARGE_EVERY = {8, 7, 5.5};
    /** Seconds it stays dazed after slamming to a stop (shorter between the two charges of phase 3). */
    public static final double DAZE_TIME = 1.8, CHAIN_DAZE = 0.6;
    // honk shockwave
    private static final double HONK_WIND = 0.7, HONK_RECOVER = 0.5, HONK_RADIUS = 58, HONK_WAVE = 0.45;
    public static final double HONK_STUN = 0.9;
    private static final double[] HONK_EVERY = {6, 5, 4};
    // traffic jam: when the cars appear during its pose, and how long the pose takes
    private static final double JAM_TIME = 1.0, JAM_SPAWN = 0.35;
    private static final double[] JAM_EVERY = {16, 14, 11};
    /** Jam car spots around the player's feet: x, y, horizontal (1) or not (0). */
    private static final double[][] JAM_SPOTS = { {0, -31, 1}, {0, 32, 1}, {-37, 0, 0}, {37, 0, 0} };
    // smog belch
    private static final double SMOG_TIME = 1.2, SMOG_LIFE = 9, SMOG_GROW = 1.2, SMOG_SAFE = 0.8;
    private static final double[] SMOG_EVERY = {0, 12, 9};
    private static final double[] SMOG_SPEED = {0, 22, 28};
    // emerge: the pothole rumbles, it climbs out, then roars (light cycling green-yellow-red)
    private static final double HOLE_TIME = 0.85, RISE_TIME = 0.8, ROAR_TIME = 1.1;
    private static final double EMERGE_TIME = HOLE_TIME + RISE_TIME + ROAR_TIME;
    private static final double DEATH_TIME = 2.6, FADE_TIME = 1.0;

    private static final int[] DUST = {0x9A968C, 0x7E7A72, 0xB4B0A6};
    private static final int[] SMOKE = {0x3A3632, 0x4E4A44, 0x2A2724};
    private static final int[] SCRAP = {0xB04434, 0x8A2E24, 0x8D959A, 0x2B2B2E, 0xE8C440, 0x6FA8C4};
    private static final int[] SPARKS = {0xFFE070, 0xFFF6CC, 0xFF9A2E};
    /** Brick, concrete, glass and roof bits knocked loose as it smashes through buildings. */
    private static final int[] RUBBLE = {0xA8442E, 0x7A3424, 0xB4B0A6, 0x8A8680, 0x6FA8C4, 0x5C6266};

    /** A cloud of smog drifting after the player. */
    private static final class Smog {
        double x, y, age;
        final double seed;
        Smog(double x, double y, double seed) { this.x = x; this.y = y; this.seed = seed; }
        double radius() { return 7 + 10 * ease(age / SMOG_GROW); }
        double fade() { return Math.min(1, age / 0.4) * Math.min(1, (SMOG_LIFE - age) / 1.2); }
    }

    private final Random rng = new Random();
    private final PixelCanvas canvas = new PixelCanvas(GridlockGolemArt.W, GridlockGolemArt.H);
    private final BossParticles fx = new BossParticles();
    private final GridlockGolemArt.Pose pose = new GridlockGolemArt.Pose();
    private final BossCore core = new BossCore();
    private final List<Smog> smog = new ArrayList<>();
    /** Oil cans in the air: landing x, y and seconds until they land (a slick appears there). */
    private final List<double[]> cans = new ArrayList<>();
    private final List<JamCar> jamCars = new ArrayList<>();

    private State state = State.EMERGE;
    private double time, stateTime;
    private double tx, ty, vx, vy, wait;
    private boolean hasTarget;
    private double walkPhase, walkBlend, flip = -1;
    private int facing = -1;
    private double barrageTimer = 3.5, oilTimer = 6, chargeTimer = 5, smogTimer = 3, honkTimer = 4, jamTimer = 12;
    /** Seconds since the last honk went off (-1 = no wave showing), seconds of traffic jam left. */
    private double honkAge = -1, jamClock;
    private boolean honked, jamSpawned;
    private double smashClock, crashTime = DAZE_TIME;
    private int thrown;
    private boolean released, smogDone, burst;
    private double puffClock;
    /** Charge: direction, lane length, distance driven so far. */
    private double dirX = 1, dirY, laneLen, driven;
    private int chargesLeft;
    private boolean rushHour;
    private Player target;
    private Level level;

    /** {@code feetX, feetY}: the middle of the pothole it climbs out of. */
    public GridlockGolem(double feetX, double feetY) {
        super(feetX - 15, feetY - 16, 30, 16);
    }

    // ------------------------------------------------------------ TrashBoss
    @Override public Entity entity() { return this; }
    @Override public String bossName() { return "GRIDLOCK GOLEM"; }
    @Override public String introMessage() {
        return "The GRIDLOCK GOLEM climbed out of the sewer! Its life is the trash left - sort it all. Yellow light = get ready, RED = run!";
    }
    @Override public String phaseMessage(int phase) {
        return phase == 2 ? "PHASE 2: Its exhaust pipes are belching SMOG - don't stand in the clouds!"
                : "PHASE 3: GRIDLOCK! Rush hour - the cars speed up and it charges twice in a row!";
    }
    @Override public String defeatMessage() { return "The light turns green - the Gridlock Golem falls apart! The city can breathe again."; }
    @Override public Intro intro() {
        return new Intro("THE TRAFFIC IS GRINDING TO A HALT", "SOMETHING IS CLIMBING OUT OF THE SEWER",
                "Scrap cars, smog and e-waste are fusing together...", "THE GRIDLOCK GOLEM",
                "WATCH ITS LIGHT: YELLOW MEANS GET READY, RED MEANS RUN!", 0xF2B233);
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
    /** Touching it hurts except while it climbs out, lies crashed, or falls apart. */
    @Override public boolean isDangerous() {
        return switch (state) {
            case ROAM, BARRAGE, OIL, REV, CHARGE, HONK, JAM, SMOG -> true;
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
        double range = state == State.CHARGE ? CHARGE_CONTACT : CONTACT_RANGE;
        return isDangerous() && Math.hypot(centerX() - p.centerX(), (y + h - 6) - p.centerY()) < range;
    }

    @Override public double[] knockBack(Player p) {
        if (state == State.CHARGE) {   // flung sideways out of the lane, and forward
            double side = (p.centerX() - centerX()) * -dirY + (p.centerY() - centerY()) * dirX >= 0 ? 1 : -1;
            return new double[] { dirX * 18 - dirY * side * 16, dirY * 18 + dirX * side * 16 };
        }
        double dx = p.centerX() - centerX(), dy = p.centerY() - centerY();
        double len = Math.max(0.001, Math.hypot(dx, dy));
        return new double[] { dx / len * 16, dy / len * 16 };
    }

    /** Breathing inside a grown smog cloud. */
    @Override public Contact hazardContact(Player p) {
        double px = p.centerX(), py = p.getY() + Player.H - 4;
        for (Smog s : smog) {
            if (s.age < SMOG_SAFE || s.age > SMOG_LIFE - 1) continue;
            double r = s.radius() * 0.72;
            if (Math.hypot(px - s.x, (py - s.y) * 1.3) < r)
                return new Contact(s.x, s.y, "You breathed in thick smog! -1 HP");
        }
        return null;
    }

    // ------------------------------------------------------------ behaviour

    @Override
    public void update(double dt, Player player, Level level) {
        time += dt;
        stateTime += dt;
        target = player;
        this.level = level;
        fx.update(dt);
        core.update(dt, player);
        flip += Math.signum(facing - flip) * Math.min(Math.abs(facing - flip), TURN_RATE * dt);
        updateSmog(dt);
        updateCans(dt, level);
        updateJamCars(dt, player);
        if (honkAge >= 0 && (honkAge += dt) > HONK_WAVE) honkAge = -1;
        if (!rushHour && core.phase() == 3 && !isDying() && state != State.EMERGE) setRushHour(true);

        // exhaust pipes puff smoke all the time, harder when revving or angry
        puffClock += dt;
        double puffEvery = state == State.REV ? 0.05 : core.phase() == 3 ? 0.18 : 0.32;
        if (!isDying() && state != State.EMERGE && puffClock > puffEvery) {
            puffClock = 0;
            int side = rng.nextBoolean() ? 1 : -1;
            fx.add(BossParticles.PUFF, centerX() + (side * 11 + 3 * pose.lean) * flip, y + h, 50 + rng.nextDouble() * 3,
                    rng.nextGaussian() * 3, -2, 10 + rng.nextDouble() * 6, 1.2, SMOKE[rng.nextInt(SMOKE.length)], 3);
        }

        switch (state) {
            case EMERGE -> updateEmerge(dt);
            case ROAM -> updateRoam(dt, level);
            case BARRAGE -> updateBarrage(dt, level);
            case OIL -> updateOil(dt, level);
            case REV -> updateRev(dt, level);
            case CHARGE -> updateCharge(dt, level);
            case CRASH -> updateCrash(dt, level);
            case HONK -> updateHonk(dt, level);
            case JAM -> updateJam(dt, level);
            case SMOG -> updateSmogBelch(dt, level);
            case DYING -> updateDying(dt);
            case GONE -> { }
        }
    }

    private void updateEmerge(double dt) {
        double gx = centerX(), gy = y + h;
        if (stateTime < HOLE_TIME) {
            puffClock += dt;
            if (puffClock > 0.08) {      // steam and grit hiss out of the pothole
                puffClock = 0;
                fx.add(BossParticles.PUFF, gx + rng.nextGaussian() * 8, gy, 1, rng.nextGaussian() * 5, -2,
                        14 + rng.nextDouble() * 10, 0.9, DUST[rng.nextInt(DUST.length)], 3);
            }
        } else if (!burst) {             // the road cracks open, asphalt chunks fly
            burst = true;
            fx.burst(BossParticles.BIT, gx, gy, 4, 18, 40, 70, 1.6, new int[] {0x3A3632, 0x4E4A44, 0x6A665E});
            fx.burst(BossParticles.PUFF, gx, gy, 2, 10, 30, 8, 0.9, DUST);
        }
        if (stateTime >= HOLE_TIME + RISE_TIME && stateTime - dt < HOLE_TIME + RISE_TIME)
            fx.burst(BossParticles.PUFF, gx, y - 30, 30, 6, 26, 6, 0.8, SMOKE);   // roar: smoke out of the pipes
        if (stateTime >= EMERGE_TIME) {
            setState(State.ROAM);
            if (core.hpKnown() && core.hp() == 0) syncHp(0);   // the city was already clean
        }
    }

    private void updateRoam(double dt, Level level) {
        int phase = core.phase();
        barrageTimer -= dt;
        oilTimer -= dt;
        chargeTimer -= dt;
        honkTimer -= dt;
        jamTimer -= dt;
        if (phase >= 2) smogTimer -= dt;
        double toPlayer = target.centerX() - centerX();
        double dist = Math.hypot(toPlayer, target.centerY() - centerY());
        double feetDist = Math.hypot(toPlayer, (target.getY() + Player.H - 3) - (y + h - 3));

        if (honkTimer <= 0 && feetDist < HONK_RADIUS - 8) {
            honkTimer = HONK_EVERY[phase - 1];
            honked = false;
            faceTarget();
            setState(State.HONK);
            return;
        }
        if (chargeTimer <= 0 && dist > LANE_MIN) {
            chargeTimer = CHARGE_EVERY[phase - 1];
            chargesLeft = phase == 3 ? 1 : 0;    // gridlock: a second charge right after
            startRev(level);
            return;
        }
        if (jamTimer <= 0 && level instanceof Level5City) {
            jamTimer = JAM_EVERY[phase - 1];
            jamSpawned = false;
            faceTarget();
            setState(State.JAM);
            return;
        }
        if (barrageTimer <= 0) {
            barrageTimer = BARRAGE_EVERY[phase - 1];
            faceTarget();
            thrown = 0;
            setState(State.BARRAGE);
            return;
        }
        if (oilTimer <= 0) {
            oilTimer = OIL_EVERY[phase - 1];
            faceTarget();
            released = false;
            setState(State.OIL);
            return;
        }
        if (phase >= 2 && smogTimer <= 0) {
            smogTimer = SMOG_EVERY[phase - 1];
            smogDone = false;
            setState(State.SMOG);
            return;
        }

        // steering: walk at the player when close, otherwise wander, pause and glare
        double dvx = 0, dvy = 0;
        if (dist < CHASE_RANGE) {
            double dx = target.centerX() - centerX(), dy = (target.getY() + Player.H) - (y + h);
            double d = Math.max(0.001, Math.hypot(dx, dy));
            double s = SPEED[phase - 1] * Math.max(0, Math.min(1, (d - 10) / 16));
            dvx = dx / d * s;
            dvy = dy / d * s;
            hasTarget = false;
            wait = 0;
        } else if (wait > 0) wait -= dt;
        else if (!hasTarget) pickTarget(level);
        else {
            double dx = tx - x, dy = ty - y, d = Math.hypot(dx, dy);
            if (d < 2) {
                hasTarget = false;
                wait = 0.6 + rng.nextDouble() * 1.4;
            } else {
                double s = SPEED[phase - 1] * Math.min(1, d / 18 + 0.15);
                dvx = dx / d * s;
                dvy = dy / d * s;
            }
        }
        steer(dt, level, dvx, dvy);
        if (Math.hypot(vx, vy) < 1.5 && Math.abs(toPlayer) > 20) facing = toPlayer > 0 ? 1 : -1;
        stomp(dt);
    }

    /** Heavy footsteps kick up dust. */
    private void stomp(double dt) {
        double speed = Math.hypot(vx, vy);
        if (speed > 6 && rng.nextDouble() < dt * 4)
            fx.add(BossParticles.PUFF, centerX() + rng.nextGaussian() * 6, y + h, 0, -vx * 0.3, 0, 3, 0.5,
                    DUST[rng.nextInt(DUST.length)], 2);
    }

    private void faceTarget() {
        double toPlayer = target.centerX() - centerX();
        if (Math.abs(toPlayer) > 4) facing = toPlayer > 0 ? 1 : -1;
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
        } else if (!blocked(level, nx, y)) {
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
        walkBlend += (Math.min(1, speed / (SPEED[0] * 0.55)) - walkBlend) * Math.min(1, dt * 6);
        if (vx > 2) facing = 1;
        else if (vx < -2) facing = -1;
    }

    private void brake(double dt, Level level) { steer(dt, level, 0, 0); }

    /** Collides with its feet only, so it fits down the streets and between the lamps. */
    private boolean blocked(Level level, double nx, double ny) {
        if (nx < 0 || ny < 0 || nx + w > Config.WORLD_W || ny + h > Config.WORLD_H) return true;
        return level.isBlocked(new Rectangle2D.Double(nx + 5, ny + h - 8, w - 10, 8));
    }

    private void pickTarget(Level level) {
        for (int i = 0; i < 40 && !hasTarget; i++) {
            tx = rng.nextInt(Config.WORLD_W - w);
            ty = rng.nextInt(Config.WORLD_H - h);
            hasTarget = !blocked(level, tx, ty) && Math.hypot(tx - x, ty - y) > 30;
        }
    }

    // ---- battery barrage
    /** Yellow light while it lifts the clamp arm; red as the batteries fly, one after another. */
    private void updateBarrage(double dt, Level level) {
        brake(dt, level);
        int count = BATTERIES[core.phase() - 1];
        while (thrown < count && stateTime >= BARRAGE_WIND + thrown * BARRAGE_GAP) {
            double sx = centerX() + 12 * flip, sy = y + h - 46;
            double[] t = thrown == 0 ? core.leadTarget(target, 0.55, 0, 0)
                    : core.leadTarget(target, 0.3, rng.nextGaussian() * 28, rng.nextGaussian() * 18);
            Strike hit = new Strike(0, 0, TOSS_RADIUS + 2, true, 0, 0, thrown == 0 ? 1 : 0,
                    "A leaking battery hit you! -1 HP");
            core.throwAt(sx, sy, t[0], t[1], BATTERY_FLIGHT + thrown * 0.05, 64, BossCore.BATTERY, hit);
            fx.burst(BossParticles.SPARK, sx, y + h, 46, 4, 20, 10, 0.4, SPARKS);
            thrown++;
        }
        if (stateTime >= BARRAGE_WIND + count * BARRAGE_GAP + BARRAGE_RECOVER) setState(State.ROAM);
    }

    // ---- oil slick
    private void updateOil(double dt, Level level) {
        brake(dt, level);
        if (!released && stateTime >= OIL_WIND) {
            released = true;
            int cansNow = core.phase() == 3 ? 2 : 1;
            for (int i = 0; i < cansNow; i++) {
                double[] t = i == 0 ? core.leadTarget(target, 0.7, 0, 0)
                        : core.leadTarget(target, 0.4, rng.nextGaussian() * 34, rng.nextGaussian() * 20);
                double flight = OIL_FLIGHT + i * 0.12;
                Strike splash = new Strike(0, 0, OilSlick.RX, false, 0, 0, 0,
                        "Splashed with motor oil! The slick slows you down.");
                core.throwAt(centerX() + 12 * flip, y + h - 46, t[0], t[1], flight, 58, BossCore.OIL_CAN, splash);
                cans.add(new double[] {t[0], t[1], flight});
            }
        }
        if (stateTime >= OIL_TIME) setState(State.ROAM);
    }

    /** Each oil can that lands leaves a slick on the road. */
    private void updateCans(double dt, Level level) {
        for (Iterator<double[]> it = cans.iterator(); it.hasNext(); ) {
            double[] c = it.next();
            c[2] -= dt;
            if (c[2] <= 0) {
                it.remove();
                if (!isDying()) level.addTemporaryProp(new OilSlick(c[0], c[1]));
            }
        }
    }

    // ---- rush hour charge
    private void startRev(Level level) {
        vx = vy = 0;
        aimCharge(level);
        setState(State.REV);
    }

    /**
     * Points the lane at the player and runs it to the edge of the map. It smashes through whatever
     * is in between, so the lane ends at the farthest spot it can stand on before the edge.
     */
    private void aimCharge(Level level) {
        double dx = target.centerX() - centerX(), dy = (target.getY() + Player.H - 3) - (y + h - 4);
        double d = Math.max(0.001, Math.hypot(dx, dy));
        dirX = dx / d;
        dirY = dy / d;
        if (Math.abs(dirX) > 0.1) facing = dirX > 0 ? 1 : -1;
        laneLen = 0;
        for (double s = 2; !outsideMap(x + dirX * s, y + dirY * s); s += 2)
            if (!blocked(level, x + dirX * s, y + dirY * s)) laneLen = s;
    }

    private boolean outsideMap(double nx, double ny) {
        return nx < 0 || ny < 0 || nx + w > Config.WORLD_W || ny + h > Config.WORLD_H;
    }

    private void updateRev(double dt, Level level) {
        double rev = REV_TIME[core.phase() - 1];
        if (stateTime < rev * AIM_SHARE) aimCharge(level);
        // wheels spin, grit sprays out behind
        if (rng.nextDouble() < dt * 20)
            fx.add(BossParticles.BIT, centerX() - dirX * 10, y + h, 1, -dirX * 50 + rng.nextGaussian() * 12,
                    -dirY * 30 + rng.nextGaussian() * 8, 30, 0.7, DUST[rng.nextInt(DUST.length)], 1);
        if (stateTime >= rev) {
            driven = 0;
            smashClock = 0;
            setState(State.CHARGE);
        }
    }

    /** Drives straight down the lane, through everything, and slams to a stop at its end. */
    private void updateCharge(double dt, Level level) {
        double speed = core.phase() == 3 ? FRENZY_SPEED : CHARGE_SPEED;
        double step = Math.min(speed * dt, laneLen - driven);
        x += dirX * step;
        y += dirY * step;
        driven += step;
        vx = dirX * speed;
        vy = dirY * speed;
        walkPhase += speed * dt / (STRIDE * 1.6);
        walkBlend = 1;
        smash(dt, level);
        if (rng.nextDouble() < dt * 30)          // smoke trail behind it
            fx.add(BossParticles.PUFF, centerX() - dirX * 12, y + h - 2, 2, -dirX * 10, -dirY * 6, 4, 0.6,
                    SMOKE[rng.nextInt(SMOKE.length)], 3);
        if (driven >= laneLen - 0.5) crash();
    }

    /** Everything it plows into while charging: buildings and props, jam cars, traffic, litter. */
    private void smash(double dt, Level level) {
        double fxp = centerX(), fyp = y + h - 4;
        Rectangle2D.Double body = new Rectangle2D.Double(x + 2, y + h - 12, w - 4, 14);
        smashClock -= dt;
        if (level.isBlocked(new Rectangle2D.Double(x + 5, y + h - 8, w - 10, 8)) && smashClock <= 0) {
            smashClock = SMASH_EVERY;            // ploughing through a wall: rubble everywhere
            core.strike(new Strike(fxp + dirX * 12, fyp, 1, false, 0, 0, 0, "The Gridlock Golem smashes through!"));
            fx.burst(BossParticles.BIT, fxp + dirX * 12, fyp, 10 + rng.nextDouble() * 20, 8, 55, 60, 1.3, RUBBLE);
            fx.burst(BossParticles.PUFF, fxp + dirX * 10, fyp, 6, 4, 22, 6, 0.8, DUST);
        }
        for (JamCar car : jamCars) {
            if (!car.hasArrived() || !car.bounds().intersects(body)) continue;
            double side = (car.centerX() - fxp) * -dirY + (car.centerY() - fyp) * dirX >= 0 ? 1 : -1;
            car.wreck(dirX * 160 - dirY * side * 100, dirY * 160 + dirX * side * 100);
            carSmashFx(car.centerX(), car.centerY());
        }
        if (level instanceof Level5City city && city.wreckCarsNear(fxp, fyp, 20, dirX, dirY) > 0)
            carSmashFx(fxp + dirX * 14, fyp + dirY * 6);
        for (Garbage item : level.getGarbage()) {   // litter is bowled aside, not destroyed
            double gx = item.centerX(), gy = item.centerY();
            if (Math.hypot(gx - fxp, (gy - fyp) * 1.3) > 16) continue;
            double side = (gx - fxp) * -dirY + (gy - fyp) * dirX >= 0 ? 1 : -1;
            double[] spot = level.litterSpotNear(item.getX() - dirY * side * 28 + dirX * 12,
                    item.getY() + dirX * side * 28 + dirY * 12, 40);
            fx.add(BossParticles.BIT, gx, gy, 2, (spot[0] - item.getX()) * 3, (spot[1] - item.getY()) * 3, 50, 0.5,
                    SCRAP[rng.nextInt(SCRAP.length)], 1);
            item.x = spot[0];
            item.y = spot[1];
        }
    }

    private void carSmashFx(double cx, double cy) {
        core.strike(new Strike(cx, cy, 1, false, 0, 0, 0, "CRUNCH!"));
        fx.burst(BossParticles.SPARK, cx, cy, 8, 12, 45, 20, 0.6, SPARKS);
        fx.burst(BossParticles.BIT, cx, cy, 6, 12, 60, 70, 1.3, SCRAP);
    }

    /** Slams to a stop at the end of the lane: a shockwave, sparks and scrap, then it is dazed. */
    private void crash() {
        vx = vy = 0;
        double gx = centerX() + dirX * 12, gy = y + h - 2 + dirY * 4;
        core.strike(new Strike(gx, gy, 22, false, 0, 0, 0, "The Golem slammed to a stop! It's dazed - get clear!"));
        fx.burst(BossParticles.SPARK, gx, gy, 20, 14, 40, 20, 0.6, SPARKS);
        fx.burst(BossParticles.BIT, gx, gy, 18, 10, 40, 60, 1.2, SCRAP);
        fx.burst(BossParticles.PUFF, gx, gy, 6, 8, 26, 6, 0.8, DUST);
        crashTime = chargesLeft > 0 ? CHAIN_DAZE : DAZE_TIME;
        setState(State.CRASH);
    }

    private void updateCrash(double dt, Level level) {
        puffClock += dt;
        if (puffClock > 0.25) {
            puffClock = 0;
            fx.add(BossParticles.SPARK, centerX() + rng.nextGaussian() * 8, y + h, 20 + rng.nextDouble() * 20,
                    rng.nextGaussian() * 6, 0, 8, 0.5, SPARKS[rng.nextInt(SPARKS.length)], 1);
        }
        if (stateTime >= crashTime) {
            if (chargesLeft > 0) {               // gridlock: straight into the second charge
                chargesLeft--;
                startRev(level);
                return;
            }
            chargeTimer = Math.max(chargeTimer, 2.5);   // a head start for the player
            setState(State.ROAM);
        }
    }

    // ---- honk shockwave
    /** Yellow ring while the grille opens wide, then the horn blasts: everyone inside is stunned. */
    private void updateHonk(double dt, Level level) {
        brake(dt, level);
        if (!honked && stateTime >= HONK_WIND) {
            honked = true;
            honkAge = 0;
            core.strike(new Strike(centerX(), y + h - 3, HONK_RADIUS, false, 0, HONK_STUN, 0,
                    "HONK!! The shockwave left you dizzy!"));
            fx.burst(BossParticles.PUFF, centerX(), y + h, 4, 16, 70, 4, 0.6, DUST);
            fx.burst(BossParticles.SPARK, centerX() + 6 * flip, y + h, 22, 10, 40, 10, 0.4, SPARKS);
        }
        if (stateTime >= HONK_WIND + HONK_RECOVER) setState(State.ROAM);
    }

    // ---- traffic jam
    /** Raises both arms and calls the traffic: stalled cars skid in around the player and the city stops. */
    private void updateJam(double dt, Level level) {
        brake(dt, level);
        if (!jamSpawned && stateTime >= JAM_SPAWN) {
            jamSpawned = true;
            spawnJam(level);
        }
        if (stateTime >= JAM_TIME) {
            setState(State.ROAM);
            chargeTimer = Math.min(chargeTimer, 1.5);   // ... and then it comes through the jam
        }
    }

    private void spawnJam(Level level) {
        if (!(level instanceof Level5City city)) return;
        double px = target.centerX(), py = target.getY() + Player.H - 3;
        Rectangle2D.Double golemFeet = new Rectangle2D.Double(x, y + h - 10, w, 12);
        int placed = 0;
        for (double[] spot : JAM_SPOTS) {
            double cx = px + spot[0], cy = py + spot[1];
            boolean horizontal = spot[2] > 0;
            Rectangle2D.Double area = JamCar.footprint(cx, cy, horizontal);
            if (level.isBlocked(area) || area.intersects(golemFeet)) continue;   // a wall is already there
            boolean onBin = false;
            for (TrashBin bin : level.getBins()) if (bin.bounds().intersects(area)) onBin = true;
            if (onBin) continue;
            JamCar car = new JamCar(cx, cy, horizontal, rng.nextBoolean(), city.carSprite(rng.nextInt(5)));
            level.addTemporaryProp(car);
            jamCars.add(car);
            placed++;
        }
        if (placed > 0) {
            city.setTrafficJam(true);
            jamClock = JamCar.WARN + JamCar.SLIDE + JamCar.STAND;
        }
    }

    /** Jam cars turn solid once parked (never on top of the player); the traffic moves again after. */
    private void updateJamCars(double dt, Player player) {
        Rectangle2D.Double feet = new Rectangle2D.Double(player.getX() + 3, player.getY() + Player.H - 5, Player.W - 6, 5);
        for (Iterator<JamCar> it = jamCars.iterator(); it.hasNext(); ) {
            JamCar car = it.next();
            if (car.isGone()) { it.remove(); continue; }
            if (car.consumeArrival()) {
                core.strike(new Strike(car.centerX(), car.centerY(), 12, true, 0, 0, 0, "A stalled car skidded into you! -1 HP"));
                fx.burst(BossParticles.PUFF, car.centerX(), car.centerY() + 4, 1, 6, 24, 4, 0.6, DUST);
            }
            if (car.isParked() && !car.isSolid() && !car.bounds().intersects(feet)) car.solidify();
        }
        if (jamClock > 0 && (jamClock -= dt) <= 0) setTrafficJam(false);
    }

    private void setTrafficJam(boolean on) {
        if (level instanceof Level5City city) city.setTrafficJam(on);
    }

    // ---- smog belch
    private void updateSmogBelch(double dt, Level level) {
        brake(dt, level);
        if (stateTime < SMOG_TIME * 0.6 && rng.nextDouble() < dt * 25)
            fx.add(BossParticles.PUFF, centerX() + (rng.nextBoolean() ? 11 : -11) * flip, y + h, 50,
                    rng.nextGaussian() * 6, rng.nextGaussian() * 3, 14, 0.9, SMOKE[rng.nextInt(SMOKE.length)], 4);
        if (!smogDone && stateTime >= SMOG_TIME * 0.6) {
            smogDone = true;
            int n = core.phase() == 3 ? 3 : 2;
            for (int i = 0; i < n; i++) {
                double a = (i - (n - 1) / 2.0) * 1.1 + (target.centerX() < centerX() ? Math.PI : 0);
                smog.add(new Smog(centerX() + Math.cos(a) * 22, y + h + Math.sin(a) * 14, rng.nextDouble() * 10));
            }
            fx.burst(BossParticles.PUFF, centerX(), y + h, 48, 10, 30, 10, 1.0, SMOKE);
        }
        if (stateTime >= SMOG_TIME) setState(State.ROAM);
    }

    private void updateSmog(double dt) {
        double speed = SMOG_SPEED[Math.max(1, core.phase()) - 1];
        if (speed == 0) speed = SMOG_SPEED[1];
        for (Iterator<Smog> it = smog.iterator(); it.hasNext(); ) {
            Smog s = it.next();
            s.age += dt;
            if (s.age >= SMOG_LIFE) { it.remove(); continue; }
            if (target != null && s.age > SMOG_SAFE * 0.5) {
                double dx = target.centerX() - s.x, dy = (target.getY() + Player.H - 4) - s.y;
                double d = Math.max(0.001, Math.hypot(dx, dy));
                s.x += (dx / d * speed + Math.sin(time * 0.7 + s.seed) * 4) * dt;
                s.y += (dy / d * speed + Math.cos(time * 0.6 + s.seed) * 3) * dt;
            }
        }
    }

    // ---- gridlock (phase 3)
    private void setRushHour(boolean on) {
        rushHour = on;
        if (level instanceof Level5City city) city.setRushHour(on);
    }

    // ---- death
    private void defeat() {
        if (state == State.DYING || state == State.GONE) return;
        setState(State.DYING);
        vx = vy = 0;
        cans.clear();
        if (rushHour) setRushHour(false);
        if (jamClock > 0) {
            jamClock = 0;
            setTrafficJam(false);
        }
        for (Smog s : smog) s.age = Math.max(s.age, SMOG_LIFE - 1.2);   // the smog clears
        fx.burst(BossParticles.SPARK, centerX(), y + h, 30, 12, 30, 20, 0.7, SPARKS);
    }

    private void updateDying(double dt) {
        double u = stateTime / DEATH_TIME;
        puffClock += dt;
        if (u > 0.25 && u < 0.8 && puffClock > 0.07) {    // bolts, glass and scrap fall off
            puffClock = 0;
            fx.add(BossParticles.BIT, centerX() + rng.nextGaussian() * 10, y + h, 10 + rng.nextDouble() * 30,
                    rng.nextGaussian() * 30, rng.nextGaussian() * 10, 20 + rng.nextDouble() * 30, 1.6,
                    SCRAP[rng.nextInt(SCRAP.length)], 1 + rng.nextInt(2));
            if (rng.nextBoolean())
                fx.add(BossParticles.SPARK, centerX() + rng.nextGaussian() * 8, y + h, 20 + rng.nextDouble() * 25,
                        rng.nextGaussian() * 8, 0, 6, 0.5, SPARKS[rng.nextInt(SPARKS.length)], 1);
        }
        if (u >= 0.82 && u - dt / DEATH_TIME < 0.82) {   // the last puff of smog leaves it: clean air
            fx.burst(BossParticles.PUFF, centerX(), y + h, 20, 12, 30, 12, 1.2, new int[] {0xDDE8E0, 0xC8DCCC});
            for (int i = 0; i < 12; i++)
                fx.add(BossParticles.SPARK, centerX() + rng.nextGaussian() * 12, y + h - 4, 2 + rng.nextDouble() * 12,
                        rng.nextGaussian() * 3, 0, 8 + rng.nextDouble() * 8, 0.9 + rng.nextDouble() * 0.6,
                        rng.nextBoolean() ? 0xFFFFFF : 0x9AF0A8, 1);
        }
        if (stateTime >= DEATH_TIME + FADE_TIME) setState(State.GONE);
    }

    private void setState(State s) {
        state = s;
        stateTime = 0;
        puffClock = 0;
    }

    // ------------------------------------------------------------ drawing

    private void buildPose() {
        GridlockGolemArt.Pose p = pose;
        p.t = time;
        p.walk = walkPhase;
        p.walkAmt = walkBlend;
        p.breathe = Math.sin(time * 2 * Math.PI / 2.8);
        p.crouch = p.lean = p.armRaise = p.armRaiseL = p.shake = p.headDX = p.headDY = p.rev = p.collapse = 0;
        p.jaw = 0.1 + 0.08 * Math.sin(time * 2 * Math.PI / 2.8 - 0.5);
        p.glow = 0.7;
        // calm = green; in phase 3 the broken signal blinks yellow while it walks
        p.light = core.phase() == 3 && ((int) (time * 3)) % 2 == 0 ? GridlockGolemArt.YELLOW : GridlockGolemArt.GREEN;
        if (walkBlend < 0.3 && state == State.ROAM) p.headDX = Math.sin(time * 0.9) * 0.8;   // looks around

        switch (state) {
            case EMERGE -> {
                double r = span(stateTime, HOLE_TIME + RISE_TIME, EMERGE_TIME);
                if (stateTime < HOLE_TIME + RISE_TIME) {
                    p.crouch = 0.6;
                    p.armRaise = p.armRaiseL = 0.6;           // hauling itself out
                    p.light = GridlockGolemArt.LIGHT_OFF;
                } else {
                    double amt = ease(r / 0.2) * (1 - ease((r - 0.85) / 0.15));
                    p.armRaise = p.armRaiseL = amt;
                    p.jaw = Math.max(p.jaw, amt);
                    p.headDY = -1.5 * amt;
                    p.shake = Math.sin(time * 60) * 0.8 * amt;
                    p.rev = amt * 0.7;
                    p.light = r < 0.33 ? GridlockGolemArt.GREEN : r < 0.66 ? GridlockGolemArt.YELLOW : GridlockGolemArt.RED;
                    p.glow = 1;
                }
            }
            case BARRAGE -> {
                double wind = ease(stateTime / BARRAGE_WIND);
                double flick = stateTime > BARRAGE_WIND ? Math.abs(Math.sin((stateTime - BARRAGE_WIND) / BARRAGE_GAP * Math.PI)) : 0;
                double end = BARRAGE_WIND + BATTERIES[core.phase() - 1] * BARRAGE_GAP;
                double rec = ease(span(stateTime, end, end + BARRAGE_RECOVER));
                p.armRaise = (wind - 0.35 * flick) * (1 - rec);
                p.crouch = 0.3 * wind * (1 - rec);
                p.jaw = Math.max(p.jaw, 0.4 * wind * (1 - rec));
                p.light = stateTime < BARRAGE_WIND ? GridlockGolemArt.YELLOW : GridlockGolemArt.RED;
                p.glow = 1;
            }
            case OIL -> {
                double wind = ease(stateTime / OIL_WIND), rec = ease(span(stateTime, OIL_WIND + 0.1, OIL_TIME));
                p.armRaise = wind * (1 - rec);
                p.headDX = -1 * wind * (1 - rec);
                p.light = stateTime < OIL_WIND ? GridlockGolemArt.YELLOW : GridlockGolemArt.RED;
                p.glow = 1;
            }
            case REV -> {
                double rev = REV_TIME[core.phase() - 1];
                double k = ease(stateTime / Math.min(0.3, rev));
                p.crouch = 0.8 * k;
                p.lean = 0.6 * k;
                p.shake = Math.sin(time * 70) * 0.7 * k;
                p.rev = k * (0.6 + 0.4 * Math.sin(time * 30));
                p.jaw = Math.max(p.jaw, 0.6 * k);
                p.light = stateTime < rev * AIM_SHARE ? GridlockGolemArt.YELLOW : GridlockGolemArt.RED;
                p.glow = 1;
            }
            case CHARGE -> {
                p.lean = 1;
                p.crouch = 0.3;
                p.rev = 1;
                p.jaw = 0.8;
                p.armRaise = 0.2;
                p.light = GridlockGolemArt.RED;
                p.glow = 1;
            }
            case CRASH -> {             // dizzy: head lolling, light sputtering
                double in = ease(stateTime / 0.2), out = ease((stateTime - (crashTime - 0.3)) / 0.3);
                double k = in * (1 - out);
                p.crouch = 0.5 * k;
                p.headDX = Math.sin(time * 3.2) * 1.6 * k;
                p.headDY = 2 * k;
                p.shake = Math.sin(time * 2.6) * 0.8 * k;
                p.jaw = Math.max(p.jaw, 0.5 * k);
                p.light = rng.nextDouble() < 0.12 ? GridlockGolemArt.YELLOW : GridlockGolemArt.LIGHT_OFF;
                p.walkAmt = 0;
            }
            case HONK -> {              // squats, grille opening wider and wider, then the blast
                double wind = ease(stateTime / HONK_WIND);
                double blast = honked ? 1 - ease((stateTime - HONK_WIND) / HONK_RECOVER) : 0;
                p.crouch = 0.6 * wind * (1 - blast) + 0.1 * blast;
                p.jaw = Math.max(p.jaw, honked ? blast : wind * 0.8);
                p.headDY = honked ? -2 * blast : 1.2 * wind;
                p.armRaise = p.armRaiseL = 0.5 * blast;
                p.shake = Math.sin(time * 70) * (honked ? 1.2 * blast : 0.4 * wind);
                p.light = honked ? GridlockGolemArt.RED : GridlockGolemArt.YELLOW;
                p.glow = 1;
            }
            case JAM -> {               // waves the traffic in with both arms, like a traffic warden gone wrong
                double k = ease(stateTime / 0.25) * (1 - ease(span(stateTime, JAM_TIME * 0.7, JAM_TIME)));
                p.armRaise = k * (0.75 + 0.25 * Math.sin(time * 14));
                p.armRaiseL = k * (0.75 + 0.25 * Math.sin(time * 14 + Math.PI));
                p.jaw = Math.max(p.jaw, 0.5 * k);
                p.light = stateTime < JAM_SPAWN ? GridlockGolemArt.YELLOW : GridlockGolemArt.RED;
                p.glow = 1;
            }
            case SMOG -> {
                double k = ease(stateTime / 0.3) * (1 - ease(span(stateTime, SMOG_TIME * 0.7, SMOG_TIME)));
                p.crouch = 0.4 * k;
                p.headDY = 1.5 * k;
                p.jaw = Math.max(p.jaw, 0.9 * k);
                p.rev = 0.4 * k;
                p.shake = Math.sin(time * 45) * 0.5 * k;
                p.light = stateTime < SMOG_TIME * 0.6 ? GridlockGolemArt.YELLOW : GridlockGolemArt.RED;
                p.glow = 1;
            }
            case DYING, GONE -> {
                double u = Math.min(1, stateTime / DEATH_TIME);
                double shudder = 1 - span(u, 0.2, 0.4);
                p.shake = Math.sin(time * 75) * 1.1 * shudder;
                p.jaw = 0.8 * shudder;
                p.collapse = ease(span(u, 0.3, 0.8));
                p.walkAmt = 0;
                p.breathe = 0;
                // the light flickers through every colour, then settles on green: go
                p.light = u < 0.75 ? ((int) (time * 14)) % 3 : GridlockGolemArt.GREEN;
                p.glow = u < 0.75 ? 0.6 : 1;
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
        buildPose();
        if (core.hitFlash() > 0) pose.shake += Math.sin(time * 80) * core.hitFlash() * 2;
        GridlockGolemArt.paint(canvas, pose);
        if (core.hitFlash() > 0) canvas.tint(0xFFFFFF, core.hitFlash() / 0.45 * 0.75);
        else if (core.healFlash() > 0) canvas.tint(0xFF7050, core.healFlash() / 0.45 * 0.4);
        if (state == State.DYING) canvas.fade(1 - span(stateTime, DEATH_TIME, DEATH_TIME + FADE_TIME));

        if (state == State.EMERGE) {
            drawEmerge(g, ax, gy);
        } else {
            int sa = state == State.DYING ? (int) (80 * (1 - span(stateTime, DEATH_TIME, DEATH_TIME + FADE_TIME))) : 80;
            g.setColor(new Color(0, 0, 0, sa));
            g.fillOval((int) Math.round(ax - 17), (int) Math.round(gy - 3), 34, 6);
            canvas.drawTo(g, ax, gy, GridlockGolemArt.AX, GridlockGolemArt.GROUND, flip);
            if (state == State.CRASH) drawStars(g, ax, gy - 60);
        }
        fx.draw(g);
    }

    /** The pothole cracks open and the golem hauls itself up out of the sewer. */
    private void drawEmerge(Graphics2D g, double ax, double gy) {
        double open = easeOut(stateTime / HOLE_TIME);
        double close = 1 - ease(span(stateTime, HOLE_TIME + RISE_TIME, EMERGE_TIME));
        double hole = open * close;
        int hx = (int) Math.round(ax), hy = (int) Math.round(gy);
        if (hole > 0.02) {
            int rx = (int) Math.round(18 * hole), ry = (int) Math.round(6 * hole);
            g.setColor(new Color(0x2A2724));
            g.fillOval(hx - rx - 2, hy - ry - 1, rx * 2 + 4, ry * 2 + 2);   // cracked asphalt rim
            g.setColor(new Color(0x0A0807));
            g.fillOval(hx - rx, hy - ry, rx * 2, ry * 2);
            g.setColor(new Color(0x5E5A52));                                 // crack lines out of the rim
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + 0.4;
                g.drawLine(hx + (int) (Math.cos(a) * rx), hy + (int) (Math.sin(a) * ry),
                        hx + (int) (Math.cos(a) * (rx + 4)), hy + (int) (Math.sin(a) * (ry + 2)));
            }
        }
        double rise = easeOut(span(stateTime, HOLE_TIME, HOLE_TIME + RISE_TIME));
        if (rise > 0) {
            Graphics2D c = (Graphics2D) g.create();
            c.clipRect(hx - 60, hy - 120, 120, 121);
            double sink = (1 - rise) * (GridlockGolemArt.H - 4);
            if (stateTime < HOLE_TIME + RISE_TIME) sink += Math.sin(time * 40) * 0.6;   // clambering
            canvas.drawTo(c, ax, gy + sink, GridlockGolemArt.AX, GridlockGolemArt.GROUND, flip);
            c.dispose();
        }
    }

    /** Little stars and a spark circling its head while it is dazed. */
    private void drawStars(Graphics2D g, double cx, double cy) {
        for (int i = 0; i < 3; i++) {
            double a = time * 5 + i * Math.PI * 2 / 3;
            int sx = (int) Math.round(cx + Math.cos(a) * 10), sy = (int) Math.round(cy + Math.sin(a) * 3);
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

    /** Charge lane, smog clouds, batteries and cans in flight, HP numbers: above everything. */
    @Override
    public void drawEffects(Graphics2D g) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (state == State.REV) drawLane(gg);
        if (state == State.HONK && !honked) drawHonkWarning(gg);
        if (honkAge >= 0) drawHonkWave(gg);
        for (Smog s : smog) drawSmog(gg, s);
        gg.dispose();
        core.drawEffects(g, centerX(), y - 52);
    }

    /** The red lane it will charge down, with chevrons; it pulses faster once locked. */
    private void drawLane(Graphics2D g) {
        double rev = REV_TIME[core.phase() - 1];
        boolean locked = stateTime >= rev * AIM_SHARE;
        double k = Math.min(1, stateTime / rev);
        double pulse = 0.5 + 0.5 * Math.sin(time * (locked ? 26 : 12));
        double sx = centerX(), sy = y + h - 4;
        double len = laneLen + 14;
        AffineTransform at = g.getTransform();
        g.translate(sx, sy);
        g.rotate(Math.atan2(dirY, dirX));
        int hw = (int) (LANE_WIDTH / 2);
        g.setColor(new Color(200, 30, 30, (int) (30 + 60 * k)));
        g.fillRect(0, -hw, (int) (len * k), hw * 2);
        g.setStroke(new BasicStroke(locked ? 1.4f : 1f));
        g.setColor(new Color(255, 60, 50, (int) (120 + 120 * pulse)));
        g.drawRect(0, -hw, (int) len, hw * 2);
        g.setColor(new Color(255, 220, 120, (int) (100 + 120 * pulse)));
        for (int cx = 14; cx < len - 4; cx += 14) {                   // chevrons pointing down the lane
            g.drawLine(cx - 3, -4, cx + 1, 0);
            g.drawLine(cx + 1, 0, cx - 3, 4);
        }
        g.setColor(new Color(255, 200, 60, (int) (140 + 100 * pulse)));   // where it slams to a stop
        int ex = (int) len;
        g.drawLine(ex - 3, -3, ex + 3, 3);
        g.drawLine(ex - 3, 3, ex + 3, -3);
        g.setTransform(at);
    }

    /** The horn's reach: a yellow ring that fills in as the blast builds up. */
    private void drawHonkWarning(Graphics2D g) {
        double k = Math.min(1, stateTime / HONK_WIND), pulse = 0.5 + 0.5 * Math.sin(time * 22);
        int cx = (int) Math.round(centerX()), cy = (int) Math.round(y + h - 3);
        int r = (int) HONK_RADIUS, fill = (int) (r * k);
        g.setColor(new Color(255, 190, 40, (int) (25 + 55 * k)));
        g.fillOval(cx - fill, cy - fill / 2, fill * 2, fill);
        g.setStroke(new BasicStroke(1.3f));
        g.setColor(new Color(255, 210, 60, (int) (130 + 120 * pulse)));
        g.drawOval(cx - r, cy - r / 2, r * 2, r);
    }

    /** The blast: three sound rings racing out to the edge of its reach. */
    private void drawHonkWave(Graphics2D g) {
        int cx = (int) Math.round(centerX()), cy = (int) Math.round(y + h - 3);
        for (int i = 0; i < 3; i++) {
            double k = Math.min(1, honkAge / HONK_WAVE * (1 - i * 0.18));
            int r = (int) (HONK_RADIUS * easeOut(k));
            g.setStroke(new BasicStroke(2f - i * 0.5f));
            g.setColor(new Color(255, 245, 210, (int) (220 * (1 - k))));
            g.drawOval(cx - r, cy - r / 2, r * 2, r);
        }
    }

    private void drawSmog(Graphics2D g, Smog s) {
        double f = s.fade(), r = s.radius();
        boolean harmful = s.age >= SMOG_SAFE && s.age <= SMOG_LIFE - 1;
        for (int i = 0; i < 6; i++) {                                 // lumpy, slowly churning puffs
            double a = i * Math.PI / 3 + time * 0.4 + s.seed;
            double px = s.x + Math.cos(a) * r * 0.45, py = s.y - 4 + Math.sin(a) * r * 0.25;
            double pr = r * (0.55 + 0.1 * Math.sin(time * 1.3 + i + s.seed));
            g.setColor(new Color(0x5E, 0x5A, 0x4E, (int) (70 * f)));
            g.fillOval((int) Math.round(px - pr), (int) Math.round(py - pr * 0.7), (int) Math.round(pr * 2), (int) Math.round(pr * 1.4));
        }
        g.setColor(new Color(0x3A, 0x36, 0x30, (int) ((harmful ? 110 : 60) * f)));
        g.fillOval((int) Math.round(s.x - r * 0.6), (int) Math.round(s.y - 4 - r * 0.4), (int) Math.round(r * 1.2), (int) Math.round(r * 0.8));
        if (harmful) {                                                // a sickly tint marks the dangerous core
            g.setColor(new Color(0x9A, 0x8A, 0x3A, (int) (40 * f)));
            g.fillOval((int) Math.round(s.x - r * 0.5), (int) Math.round(s.y - 4 - r * 0.3), (int) Math.round(r), (int) Math.round(r * 0.6));
        }
    }

    // ------------------------------------------------------------ oil slick

    /** A slick of motor oil on the road: slows anyone who walks through it, then soaks away. */
    static final class OilSlick extends Prop implements TimedProp {
        static final int RX = 16, RY = 7;
        private double age;
        private final long seed;

        OilSlick(double cx, double cy) {
            super((int) Math.round(cx) - RX, (int) Math.round(cy) - RY, RX * 2, RY * 2, false);
            seed = Double.doubleToLongBits(cx * 31 + cy);
        }

        @Override public boolean update(double dt) {
            age += dt;
            return age < SLICK_SECONDS;
        }

        @Override public double speedFactor() { return 0.45; }
        @Override public boolean isGroundDecal() { return true; }

        @Override public void draw(Graphics2D g) {
            double grow = easeOut(age / 0.25), f = Math.min(1, (SLICK_SECONDS - age) / 1.2);
            int a = (int) (220 * f);
            if (a <= 0) return;
            int cx = (int) Math.round(centerX()), cy = (int) Math.round(centerY());
            int rx = (int) Math.round(RX * grow), ry = (int) Math.round(RY * grow);
            Random r = new Random(seed);
            g.setColor(new Color(0x0E, 0x0C, 0x10, a));
            g.fillOval(cx - rx, cy - ry, rx * 2, ry * 2);
            for (int i = 0; i < 4; i++) {                              // splashy lobes
                int lx = cx + (int) ((r.nextDouble() - 0.5) * rx * 1.4), ly = cy + (int) ((r.nextDouble() - 0.5) * ry);
                int lr = (int) (rx * (0.3 + r.nextDouble() * 0.25));
                g.fillOval(lx - lr, ly - lr / 2, lr * 2, lr);
            }
            g.setColor(new Color(0x26, 0x22, 0x2C, a));
            g.fillOval(cx - rx + 3, cy - ry + 2, Math.max(1, rx * 2 - 6), Math.max(1, ry * 2 - 4));
            // rainbow sheen
            int[] sheen = {0x8A5AC8, 0x4EA8C8, 0x6AC86A, 0xD8C04A};
            for (int i = 0; i < 4; i++) {
                int c = sheen[i];
                g.setColor(new Color((c >> 16) & 255, (c >> 8) & 255, c & 255, (int) (110 * f)));
                int sx = cx - rx / 2 + i * 3 + (int) (Math.sin(age * 1.5 + i) * 1.5);
                g.fillRect(sx, cy - ry / 3 + (i % 2), 3, 1);
            }
        }
    }
}
