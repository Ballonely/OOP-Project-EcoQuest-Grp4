package com.ecoquest.level1;

import com.ecoquest.level1.TrashMonsterArt.Anim;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.ecoquest.level1.Config.*;

/**
 * Level 1 boss: the Trash Monster. Its life is tied to the trash on the street.
 * <ul>
 *   <li>Its HP always equals the trash still left: litter on the map plus trash in the player's
 *       bag ({@link #syncHp}). Every piece sorted into the correct bin takes 1 HP; when the
 *       trash is gone, it dies.</li>
 *   <li>It slowly follows the player and every {@value #THROW_INTERVAL} seconds throws a garbage
 *       bag at a telegraphed spot. The bag bursts into new trash, so each throw heals it 1 HP.</li>
 *   <li>Phase 2 ({@value #PHASE_2_HP} HP or less): walks faster.
 *       Phase 3 ({@value #PHASE_3_HP} HP or less): enraged, leaves a slowing toxic slime trail.</li>
 * </ul>
 * The game panel applies damage to the player, spawns the litter and feeds the HP in; this class
 * only reports events.
 */
public class TrashMonsterBoss extends Entity implements TrashBoss {
    /** Seconds between garbage-bag throws (counted from the start of one throw to the next). */
    public static final double THROW_INTERVAL = 10;

    private enum State { EMERGE, CHASE, TOSS, DYING, GONE }

    private static final int SPRITE_W = TrashMonsterArt.W, SPRITE_H = TrashMonsterArt.H;

    /** Seconds after rising before the first throw. */
    private static final double FIRST_THROW_DELAY = 3;
    private static final double EMERGE_SECONDS = 1.6;
    private static final double TOSS_ANIM_SECONDS = 0.6;
    private static final double TOSS_RELEASE_AT = 0.3;
    private static final double TOSS_FLIGHT_SECONDS = 1.05;
    private static final double SPLAT_SECONDS = 0.45;
    private static final double FLOATER_SECONDS = 1.0;
    private static final double CONTACT_RANGE = 18;
    private static final double SLIME_INTERVAL = 0.45;
    private static final double DEATH_FADE_SECONDS = 0.7;
    private static final double DEATH_SECONDS = Anim.DEFEAT.duration() + DEATH_FADE_SECONDS;
    private static final double LEAD_SECONDS = 0.55;

    // per-phase tuning, index = phase - 1
    private static final double[] CHASE_SPEED = { 28, 34, 42 };

    private final List<Toss> tosses = new ArrayList<>();
    private final List<double[]> splats = new ArrayList<>();      // x, y, age
    private final List<double[]> landed = new ArrayList<>();      // x, y (consumed by the game panel)
    private final List<Floater> floaters = new ArrayList<>();     // "+1" / "-1" above its head

    private State state = State.EMERGE;
    private int hp = -1, peakHp = 1;
    private int reportedPhase = 1;
    private double stateTime, animTime, hitFlash, healFlash;
    private double tossTimer = FIRST_THROW_DELAY, slimeTimer;
    private boolean tossReleased, moving, facingLeft, defeatedPending, tossPending;
    private int phaseChangePending, hpChangePending;
    private double lastPlayerX = Double.NaN, lastPlayerY, playerVX, playerVY;

    public TrashMonsterBoss(double x, double y) {
        super(x, y, SPRITE_W, SPRITE_H);
        TrashMonsterArt.frame(Anim.IDLE, 0, TrashMonsterArt.NORMAL);   // builds the frame cache up front
    }

    // ------------------------------------------------------------ TrashBoss
    @Override public Entity entity() { return this; }
    @Override public String bossName() { return "TRASH MONSTER"; }
    @Override public String introMessage() {
        return "The TRASH MONSTER burst out of the road! Its life is the trash left - sort every piece to beat it!";
    }
    @Override public String phaseMessage(int phase) {
        return phase == 2 ? "PHASE 2: The Trash Monster is weakening... and getting faster!"
                : "PHASE 3: ENRAGED! It leaves a toxic slime trail - finish it!";
    }
    @Override public String defeatMessage() { return "The street is clean - the Trash Monster is defeated!"; }
    @Override public Intro intro() {
        return new Intro("SOMETHING IS STIRRING UNDER THE ROAD", "THE ROAD IS CRACKING",
                "All the trash on this street is coming alive...", "THE TRASH MONSTER",
                "IT HURLS GARBAGE BAGS - DODGE THE RED RINGS!", 0x8FD46A);
    }

    // ------------------------------------------------------------ queries
    public int getHp() { return Math.max(0, hp); }
    /** Highest HP reached in this fight; the health bar is drawn against it. */
    public int getPeakHp() { return peakHp; }
    public int getPhase() { return phaseFor(getHp()); }
    private static int phaseFor(int hp) { return hp > PHASE_2_HP ? 1 : hp > PHASE_3_HP ? 2 : 3; }
    public boolean isGone() { return state == State.GONE; }
    public boolean isDying() { return state == State.DYING || state == State.GONE; }
    public boolean isEmerging() { return state == State.EMERGE; }
    public double getHitFlash() { return hitFlash; }
    public double getHealFlash() { return healFlash; }
    /** True while touching the monster hurts (not while rising or dying). */
    public boolean isDangerous() { return state == State.CHASE || state == State.TOSS; }

    /** Distance from the monster's body to the player's center. */
    public double distanceTo(Player player) {
        return Math.hypot(bodyX() - player.centerX(), bodyY() - player.centerY());
    }

    public boolean isTouching(Player player) { return distanceTo(player) <= CONTACT_RANGE; }

    /** Knock-back vector to push the player away after a hit. */
    public double[] knockBack(Player player) {
        double force = 16;
        double dx = player.centerX() - bodyX(), dy = player.centerY() - bodyY();
        double len = Math.max(0.001, Math.hypot(dx, dy));
        return new double[] { dx / len * force, dy / len * force };
    }

    // ------------------------------------------------------------ events
    @Override
    public List<Strike> consumeStrikes() {
        List<Strike> out = new ArrayList<>();
        for (double[] spot : landed) out.add(Strike.bag(spot[0], spot[1]));
        landed.clear();
        return out;
    }
    /** True once per garbage bag thrown (for its sound effect). */
    public boolean consumeToss() { boolean v = tossPending; tossPending = false; return v; }
    /** Net HP change since the last call: negative = it took damage, positive = it healed. */
    public int consumeHpChange() { int v = hpChangePending; hpChangePending = 0; return v; }
    /** The phase just entered (2 or 3, only when it gets angrier), or 0. */
    public int consumePhaseChange() { int v = phaseChangePending; phaseChangePending = 0; return v; }
    /** True once, when the last piece of trash is gone and the monster starts dying. */
    public boolean consumeDefeated() { boolean v = defeatedPending; defeatedPending = false; return v; }

    // ------------------------------------------------------------ life = trash left
    /**
     * Sets HP to the amount of trash still left (map litter + trash in the player's bag).
     * Fewer pieces than before = damage, more = healing. At 0 (once it has risen) it dies.
     */
    public void syncHp(int trashLeft) {
        if (isDying()) return;
        int next = Math.max(0, trashLeft);
        if (hp >= 0 && next != hp) {
            int diff = next - hp;
            hpChangePending += diff;
            if (diff < 0) hitFlash = 0.45;
            else healFlash = 0.45;
            floaters.add(new Floater((diff > 0 ? "+" : "") + diff, diff > 0));
        }
        hp = next;
        peakHp = Math.max(peakHp, hp);
        int phase = phaseFor(hp);
        if (phase > reportedPhase) phaseChangePending = phase;
        reportedPhase = phase;
        if (hp == 0 && state != State.EMERGE) {
            tosses.clear();
            defeatedPending = true;
            enter(State.DYING);
        }
    }

    // ------------------------------------------------------------ update
    public void update(double dt, Player player, Level level) {
        stateTime += dt;
        animTime += dt;
        hitFlash = Math.max(0, hitFlash - dt);
        healFlash = Math.max(0, healFlash - dt);
        trackPlayer(dt, player);
        updateTosses(dt);
        int p = getPhase() - 1;

        switch (state) {
            case EMERGE:
                if (stateTime >= EMERGE_SECONDS) {
                    enter(State.CHASE);
                    if (hp == 0) syncHp(0);   // the street was already clean
                }
                break;
            case CHASE:
                tossTimer -= dt;
                chase(dt, player, level, CHASE_SPEED[p]);
                if (p == 2) dropSlime(dt, level);
                if (tossTimer <= 0) {
                    tossTimer = THROW_INTERVAL - TOSS_ANIM_SECONDS;   // next throw 10 s after this one
                    tossReleased = false;
                    moving = false;
                    if (Math.abs(player.centerX() - bodyX()) > 4) facingLeft = player.centerX() < bodyX();
                    enter(State.TOSS);
                }
                break;
            case TOSS:
                if (!tossReleased && stateTime >= TOSS_RELEASE_AT) {
                    launchBag(player);
                    tossReleased = true;
                }
                if (stateTime >= TOSS_ANIM_SECONDS) enter(State.CHASE);
                break;
            case DYING:
                if (stateTime >= DEATH_SECONDS) enter(State.GONE);
                break;
            case GONE:
                break;
        }
        for (Iterator<double[]> it = splats.iterator(); it.hasNext(); ) {
            double[] s = it.next();
            s[2] += dt;
            if (s[2] >= SPLAT_SECONDS) it.remove();
        }
        for (Iterator<Floater> it = floaters.iterator(); it.hasNext(); ) {
            Floater f = it.next();
            f.age += dt;
            if (f.age >= FLOATER_SECONDS) it.remove();
        }
    }

    private void enter(State next) {
        state = next;
        stateTime = 0;
    }

    private void trackPlayer(double dt, Player player) {
        double px = player.centerX(), py = player.centerY();
        if (!Double.isNaN(lastPlayerX) && dt > 0) {
            double k = Math.min(1, dt * 6);   // smoothed velocity, for leading throws
            playerVX += ((px - lastPlayerX) / dt - playerVX) * k;
            playerVY += ((py - lastPlayerY) / dt - playerVY) * k;
        }
        lastPlayerX = px;
        lastPlayerY = py;
    }

    private void chase(double dt, Player player, Level level, double speed) {
        double dx = player.centerX() - bodyX(), dy = player.centerY() - bodyY();
        double distance = Math.hypot(dx, dy);
        moving = false;
        if (Math.abs(dx) > 4) facingLeft = dx < 0;
        if (distance < 1) return;
        double step = Math.min(speed * dt, Math.max(0, distance - 10));
        double mx = dx / distance * step, my = dy / distance * step;
        if (!level.isBlocked(feetAt(x + mx, y))) { x += mx; moving |= Math.abs(mx) > 0.01; }
        if (!level.isBlocked(feetAt(x, y + my))) { y += my; moving |= Math.abs(my) > 0.01; }
    }

    private void dropSlime(double dt, Level level) {
        slimeTimer -= dt;
        if (slimeTimer > 0) return;
        slimeTimer = SLIME_INTERVAL;
        level.addTemporaryProp(new ToxicSlime(bodyX(), y + SPRITE_H - 4));
    }

    /** One garbage bag, aimed a little ahead of where the player is walking. */
    private void launchBag(Player player) {
        double tx = clamp(player.centerX() + playerVX * LEAD_SECONDS, TOSS_RADIUS, WORLD_W - TOSS_RADIUS);
        double ty = clamp(player.y + Player.H - 3 + playerVY * LEAD_SECONDS, TOSS_RADIUS, WORLD_H - TOSS_RADIUS);
        tosses.add(new Toss(bodyX() + (facingLeft ? -10 : 10), y + 12, tx, ty, TOSS_FLIGHT_SECONDS));
        tossPending = true;
    }

    private void updateTosses(double dt) {
        for (Iterator<Toss> it = tosses.iterator(); it.hasNext(); ) {
            Toss t = it.next();
            t.age += dt;
            if (t.age >= t.flight) {
                landed.add(new double[] { t.tx, t.ty });
                splats.add(new double[] { t.tx, t.ty, 0 });
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------ geometry
    private double bodyX() { return x + SPRITE_W / 2.0; }
    private double bodyY() { return y + 28; }

    /** Small collision box at the monster's feet, so it fits down the two-tile streets. */
    private Rectangle2D.Double feetAt(double nx, double ny) {
        return new Rectangle2D.Double(nx + 20, ny + 38, 16, 8);
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    // ------------------------------------------------------------ drawing
    @Override
    public void draw(Graphics2D g) {
        if (state == State.GONE) return;
        Graphics2D gg = (Graphics2D) g.create();
        gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int dx = (int) Math.round(x), dy = (int) Math.round(y);

        // soft ground shadow
        gg.setColor(new Color(0, 0, 0, 85));
        gg.fillOval(dx + 13, dy + SPRITE_H - 6, SPRITE_W - 26, 6);

        if (state == State.EMERGE) drawEmerge(gg, dx, dy);
        else if (state == State.DYING) drawDying(gg, dx, dy);
        else blit(gg, currentFrame(), dx, dy);
        gg.dispose();
    }

    /** Draws a frame, mirrored when the monster faces left. */
    private void blit(Graphics2D g, BufferedImage frame, int dx, int dy) {
        if (facingLeft) g.drawImage(frame, dx + SPRITE_W, dy, -SPRITE_W, SPRITE_H, null);
        else g.drawImage(frame, dx, dy, null);
    }

    private BufferedImage currentFrame() {
        Anim anim;
        double time;
        if (state == State.TOSS) {
            anim = Anim.THROW;
            time = stateTime / TOSS_ANIM_SECONDS * Anim.THROW.duration();
        } else {
            anim = moving ? Anim.WALK : Anim.IDLE;
            time = animTime * (getPhase() == 3 ? 1.35 : 1);
        }
        int variant = TrashMonsterArt.NORMAL;
        if (hitFlash > 0 && ((int) (hitFlash * 20)) % 2 == 0) variant = TrashMonsterArt.WHITE;
        else if (getPhase() == 3 && ((int) (animTime * 4)) % 2 == 0) variant = TrashMonsterArt.RED;
        return TrashMonsterArt.frame(anim, time, variant);
    }

    private void drawEmerge(Graphics2D g, int dx, int dy) {
        double k = Math.min(1, stateTime / EMERGE_SECONDS);
        int footY = dy + SPRITE_H - 3;
        // cracked hole in the asphalt
        int holeW = (int) (12 + 24 * Math.min(1, k * 2.5));
        g.setColor(new Color(30, 26, 22));
        g.fillOval(dx + SPRITE_W / 2 - holeW / 2, footY - 4, holeW, 8);
        g.setColor(new Color(80, 70, 52));
        g.drawOval(dx + SPRITE_W / 2 - holeW / 2, footY - 4, holeW, 8);
        g.drawLine(dx + SPRITE_W / 2 - holeW / 2 - 4, footY, dx + SPRITE_W / 2 - holeW / 2, footY - 1);
        g.drawLine(dx + SPRITE_W / 2 + holeW / 2, footY + 1, dx + SPRITE_W / 2 + holeW / 2 + 5, footY + 2);
        // rising body, cut off at the hole
        double rise = ease(Math.min(1, k * 1.15));
        int visible = (int) Math.round(SPRITE_H * rise);
        if (visible > 0) {
            BufferedImage frame = TrashMonsterArt.frame(Anim.ROAR, animTime, TrashMonsterArt.NORMAL);
            BufferedImage part = frame.getSubimage(0, 0, SPRITE_W, Math.min(SPRITE_H, visible));
            int top = dy + SPRITE_H - visible;
            if (facingLeft) g.drawImage(part, dx + SPRITE_W, top, -SPRITE_W, part.getHeight(), null);
            else g.drawImage(part, dx, top, null);
        }
        // flying debris
        g.setColor(new Color(110, 96, 70));
        for (int i = 0; i < 10; i++) {
            double t = (animTime * 1.6 + i / 10.0) % 1;
            int px = dx + SPRITE_W / 2 + (int) ((i - 4.5) * 4 * t);
            int py = footY - (int) (Math.sin(t * Math.PI) * 18);
            g.fillRect(px, py, 2, 2);
        }
    }

    private void drawDying(Graphics2D g, int dx, int dy) {
        double animEnd = Anim.DEFEAT.duration();
        float alpha = stateTime < animEnd ? 1f
                : (float) Math.max(0, 1 - (stateTime - animEnd) / DEATH_FADE_SECONDS);
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        int variant = stateTime < 0.3 && ((int) (stateTime * 16)) % 2 == 0
                ? TrashMonsterArt.WHITE : TrashMonsterArt.NORMAL;
        blit(g, TrashMonsterArt.frame(Anim.DEFEAT, stateTime, variant), dx, dy);
        g.setComposite(old);
        // sparkles: the trash breaks apart into clean air
        g.setColor(new Color(170, 255, 190, (int) (220 * alpha)));
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5 + stateTime * 2;
            double r = 8 + stateTime * 20;
            g.fillRect(dx + SPRITE_W / 2 + (int) (Math.cos(a) * r), dy + 30 + (int) (Math.sin(a) * r * 0.6), 2, 2);
        }
    }

    private static double ease(double v) {
        double t = Math.max(0, Math.min(1, v));
        return t * t * (3 - 2 * t);
    }

    /**
     * World-space effects drawn above everything: falling garbage bags with their landing
     * markers, impact splats and the "+1" / "-1" HP numbers.
     */
    public void drawEffects(Graphics2D g) {
        if (state == State.GONE) return;
        Graphics2D gg = (Graphics2D) g.create();
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (double[] s : splats) {
            double k = s[2] / SPLAT_SECONDS;
            int alpha = (int) (200 * (1 - k));
            int r = (int) (TOSS_RADIUS * (0.6 + 0.6 * k));
            gg.setColor(new Color(92, 110, 50, alpha));
            gg.fillOval((int) s[0] - r, (int) s[1] - r / 2, r * 2, r);
            gg.setColor(new Color(150, 170, 80, alpha));
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                gg.fillRect((int) (s[0] + Math.cos(a) * r * 1.1), (int) (s[1] + Math.sin(a) * r * 0.55) - (int) (6 * Math.sin(k * Math.PI)), 2, 2);
            }
        }

        for (Toss t : tosses) {
            double k = Math.min(1, t.age / t.flight);
            int cx = (int) Math.round(t.tx), cy = (int) Math.round(t.ty);
            // target marker: fills up as the bag falls
            double pulse = 0.5 + 0.5 * Math.sin(t.age * 18);
            gg.setColor(new Color(200, 30, 30, (int) (40 + 70 * k)));
            int fill = (int) (TOSS_RADIUS * k);
            gg.fillOval(cx - fill, cy - fill / 2, fill * 2, fill);
            gg.setStroke(new BasicStroke(1.2f));
            gg.setColor(new Color(255, 60, 50, (int) (150 + 100 * pulse)));
            gg.drawOval(cx - TOSS_RADIUS, cy - TOSS_RADIUS / 2, TOSS_RADIUS * 2, TOSS_RADIUS);
            // bag in flight along an arc
            double bx = t.sx + (t.tx - t.sx) * k;
            double groundY = t.sy + (t.ty - t.sy) * k;
            double by = groundY - Math.sin(k * Math.PI) * 46;
            gg.setColor(new Color(0, 0, 0, (int) (40 + 60 * k)));
            int shadow = 3 + (int) (4 * k);
            gg.fillOval((int) bx - shadow, (int) groundY - 1, shadow * 2, 3);
            drawBag(gg, (int) Math.round(bx), (int) Math.round(by), t.age * 9);
        }

        gg.setFont(new Font(Font.DIALOG, Font.BOLD, 9));
        for (int i = 0; i < floaters.size(); i++) {
            Floater f = floaters.get(i);
            double k = f.age / FLOATER_SECONDS;
            int alpha = (int) (255 * Math.min(1, (1 - k) * 2));
            int fx = (int) bodyX() - gg.getFontMetrics().stringWidth(f.text) / 2 + (i % 2 == 0 ? -6 : 6);
            int fy = (int) (y + 10 - k * 14);
            gg.setColor(new Color(0, 0, 0, alpha / 2));
            gg.drawString(f.text, fx + 1, fy + 1);
            gg.setColor(f.heal ? new Color(255, 120, 90, alpha) : new Color(140, 255, 140, alpha));
            gg.drawString(f.text, fx, fy);
        }
        gg.dispose();
    }

    private static void drawBag(Graphics2D g, int cx, int cy, double spin) {
        AffineTransform at = g.getTransform();
        g.rotate(Math.sin(spin) * 0.5, cx, cy);
        g.setColor(new Color(34, 48, 36));
        g.fillOval(cx - 4, cy - 3, 9, 8);
        g.setColor(new Color(58, 80, 60));
        g.fillOval(cx - 3, cy - 2, 4, 3);
        g.setColor(new Color(220, 200, 90));
        g.fillRect(cx - 1, cy - 5, 3, 2);
        g.setTransform(at);
    }

    /** A garbage bag in flight. */
    private static final class Toss {
        final double sx, sy, tx, ty, flight;
        double age;

        Toss(double sx, double sy, double tx, double ty, double flight) {
            this.sx = sx; this.sy = sy; this.tx = tx; this.ty = ty; this.flight = flight;
        }
    }

    /** "+1" (it healed, shown red: bad for the player) or "-1" (damage, shown green). */
    private static final class Floater {
        final String text;
        final boolean heal;
        double age;

        Floater(String text, boolean heal) {
            this.text = text;
            this.heal = heal;
        }
    }
}
