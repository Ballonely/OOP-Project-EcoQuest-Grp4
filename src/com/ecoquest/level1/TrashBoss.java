package com.ecoquest.level1;

import java.awt.Graphics2D;
import java.util.List;

/**
 * A boss whose life is the trash still left on its level (the Level 1 rules, shared by every
 * level boss):
 * <ul>
 *   <li>It rises once the level is {@code 75%} clean. From then on its HP always equals the litter
 *       on the map plus the trash in the player's bag ({@link #syncHp}): every correct sort takes
 *       1 HP, every new piece of litter heals it 1 HP.</li>
 *   <li>Every few seconds it throws a garbage bag at a telegraphed spot. A hit costs the player
 *       1 HP, and the bag bursts into a new piece of trash. Touching it also costs 1 HP.</li>
 *   <li>Phase 2, then phase 3 (enraged) as it weakens: the Level 1 Trash Monster at
 *       {@value #PHASE_2_HP} and {@value #PHASE_3_HP} HP, the other bosses at 65% and 25% of their
 *       peak HP ({@link BossCore}).</li>
 *   <li>At 0 HP it dies. The level is won once it is gone and the trash is all sorted; the
 *       player loses only when their health runs out.</li>
 * </ul>
 * The game panel applies damage to the player, spawns the litter and feeds the HP in; a boss
 * only reports events.
 */
public interface TrashBoss {
    int PHASE_2_HP = 6, PHASE_3_HP = 3;
    /** Radius of a garbage-bag impact. */
    int TOSS_RADIUS = 13;

    static int phaseFor(int hp) { return hp > PHASE_2_HP ? 1 : hp > PHASE_3_HP ? 2 : 3; }

    /**
     * Something the boss lands on the ground at (x, y): a garbage bag, rotten produce, a shockwave,
     * a minion collapsing... If the player's feet are within {@code radius} it hurts them (1 HP),
     * slows and / or stuns them for the given seconds; then {@code litter} new pieces of trash appear
     * there (each heals the boss 1 HP). {@code radius} 0 only drops litter.
     */
    record Strike(double x, double y, double radius, boolean damage, double slow, double stun, int litter, String message) {
        public Strike at(double nx, double ny) { return new Strike(nx, ny, radius, damage, slow, stun, litter, message); }
        /** The Level 1 garbage bag: 1 HP on a hit, bursts into one piece of trash. */
        public static Strike bag(double x, double y) {
            return new Strike(x, y, TOSS_RADIUS + 2, true, 0, 0, 1, "A garbage bag hit you! -1 HP");
        }
    }

    /**
     * Text for the boss's intro cinematic: the warning line, the build-up while the camera zooms in,
     * its name and tagline for the reveal, and the title colour.
     */
    record Intro(String warning, String stirTitle, String stirDetail, String name, String tagline, int color) {}

    /** A minion or thrown object touching the player: where it hit from (for knock-back) and the message. */
    record Contact(double fromX, double fromY, String message) {}

    /** The boss as a world entity (added to the level's NPCs so it is depth-sorted). */
    Entity entity();
    /** Name for the health bar, e.g. "TRASH MONSTER". */
    String bossName();
    /** Toast shown when it appears. */
    String introMessage();
    /** Text for its intro cinematic. */
    Intro intro();
    /** Toast shown when it enters phase 2 or 3. */
    String phaseMessage(int phase);
    /** Toast shown when it is beaten. */
    String defeatMessage();

    void update(double dt, Player player, Level level);
    void syncHp(int trashLeft);

    int getHp();
    /** Highest HP reached in this fight; the health bar is drawn against it. */
    int getPeakHp();
    int getPhase();
    boolean isEmerging();
    boolean isDying();
    boolean isGone();
    /** True while touching it hurts (not while rising or dying). */
    boolean isDangerous();
    boolean isTouching(Player player);
    /** Knock-back vector to push the player away after a hit. */
    double[] knockBack(Player player);
    double getHitFlash();
    double getHealFlash();

    /** Everything that landed since the last call (see {@link Strike}). */
    List<Strike> consumeStrikes();
    /** A minion or thrown object of the boss touching the player right now (1 HP), or null. */
    default Contact hazardContact(Player player) { return null; }
    /** True once per garbage bag thrown (for its sound effect). */
    boolean consumeToss();
    /** Net HP change since the last call: negative = it took damage, positive = it healed. */
    int consumeHpChange();
    /** The phase just entered (2 or 3, only when it gets angrier), or 0. */
    int consumePhaseChange();
    /** True once, when its HP hits 0 and it starts dying. */
    boolean consumeDefeated();

    /** World-space effects drawn above everything (bags in flight, impacts, HP numbers). */
    void drawEffects(Graphics2D g);
}
