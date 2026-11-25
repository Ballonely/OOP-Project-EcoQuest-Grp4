package com.ecoquest.level1;

import com.ecoquest.core.InputHandler;
import com.ecoquest.data.CleanerRole;
import com.ecoquest.level2.Level2Park;
import com.ecoquest.ui.ShopManager;
import com.ecoquest.ui.SoundFx;
import com.ecoquest.ui.SoundFx.Sound;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static com.ecoquest.level1.Config.*;

/** Level 1 world, interaction rules and HUD, driven by the main game loop. */
public class GamePanel {
    private static final double BIN_RANGE = 17;
    private static final int BOSS_SPAWN_CLEANLINESS = 75;

    // Level 4 (beach) boss: intro cinematic phases, in seconds
    private static final double BOSS_WARNING_PHASE = 1.55;
    private static final double BOSS_ZOOM_IN_PHASE = 1.4;
    private static final double BOSS_SPLASH_PHASE = 2.35;
    private static final double BOSS_ZOOM_OUT_PHASE = 1.2;
    private static final double BOSS_CINEMATIC_DURATION = 7.45;
    private static final int BOSS_WAVES = 5;
    private static final double BOSS_TIME_LIMIT = 300;
    private static final double BOSS_ATTACK_INTERVAL = 5.5;
    private static final double BOSS_VICTORY_DURATION = 2.4;

    private final BufferedImage frame = new BufferedImage(WORLD_W, WORLD_H, BufferedImage.TYPE_INT_ARGB);

    private Level level;
    private Player player;
    private TrashMonster trashMonster;
    private Garbage nearGarbage;
    private TrashBin nearBin;
    private final CoastalMorphling morphling = new CoastalMorphling();
    /** The Morphling's Tide Surge (waves 3+) and Sludgelings (wave 5); its splash volley lives here. */
    private final MorphlingSkills morphlingSkills = new MorphlingSkills();

    // level bosses with the Level 1 rules (Trash Monster, Litter Beast, Waste King): life = trash left
    private static final double STREET_BOSS_HIT_COOLDOWN = 1.6;
    private TrashBoss trashBoss;
    private boolean trashBossDefeated;
    // intro cinematic for those bosses: warning, zoom in, the boss rises, zoom out
    private static final double INTRO_WARN = 1.2, INTRO_ZOOM_IN = 1.1, INTRO_REVEAL = 2.4, INTRO_ZOOM_OUT = 0.9;
    private static final double INTRO_TOTAL = INTRO_WARN + INTRO_ZOOM_IN + INTRO_REVEAL + INTRO_ZOOM_OUT;
    private static final double INTRO_ZOOM = 2.2;
    /** The boss waiting for its entrance (created, not yet on the field), and the cinematic clock (-1 = none). */
    private TrashBoss introBoss;
    private double introTime = -1, introFocusX, introFocusY;
    // developer account tools, see enableDevTools() / enableDevBossMode()
    private boolean devTools, devAutoBoss, devGuideVisible = true;
    /** Seconds until a beaten boss is brought back in auto-respawn mode (-1 = not pending). */
    private double devRespawnTimer = -1;
    /** A dev summon that had to wait (the player stood on the boss's spot). */
    private boolean devBossPending;
    private static final double DEV_RESPAWN_DELAY = 3.5;
    private double shakeTime, shakeStrength;

    // beach boss state
    private boolean bossStarted, bossFailed, bossDefeated, showBossWarning, bossVictorySequence;
    private int bossWave, bossWaveRemaining, bossAttackPattern;
    private double bossWarningTime, bossTimeLeft, bossVictoryTime, bossAttackCooldown, bossAttackAnimation;
    private final List<double[]> bossAttackTargets = new ArrayList<>();

    // timed levels
    private double timeLeft;
    private boolean timedOut;
    private double stageTime;

    private boolean cleared;
    private boolean gameOver;
    private double bossDamageCooldown;
    private String toast = "";
    private double toastTime;
    private final int startingMoney;
    private final int capacityBonus;
    private final boolean runningShoes;
    private final boolean scanningGloves;
    private final ShopManager shopManager;
    private final Level initialLevel;
    private final CleanerRole role;

    // Litter Picker's Scanner Lite: one scan per level attempt, marks nearby litter for a while.
    private static final double SCAN_RADIUS = 6 * TILE;
    private static final double SCAN_SECONDS = 10;
    private static final double SCAN_WAVE_SECONDS = 0.7;
    private final Set<Garbage> scannedGarbage = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean scannerUsed;
    private double scanTimeLeft;
    private double scanOriginX, scanOriginY;

    public GamePanel() {
        this(0, 0, false, false, null);
    }

    public GamePanel(int startingMoney, int capacityBonus, boolean runningShoes,
            boolean scanningGloves, ShopManager shopManager) {
        this(startingMoney, capacityBonus, runningShoes, scanningGloves, shopManager, null);
    }

    public GamePanel(int startingMoney, int capacityBonus, boolean runningShoes,
            boolean scanningGloves, ShopManager shopManager, Level initialLevel) {
        this(startingMoney, capacityBonus, runningShoes, scanningGloves, shopManager, initialLevel,
                CleanerRole.LITTER_PICKER, false);
    }

    /** Full constructor; {@code role} perks apply only when {@code applyRole} is true. */
    public GamePanel(int startingMoney, int capacityBonus, boolean runningShoes,
            boolean scanningGloves, ShopManager shopManager, Level initialLevel,
            CleanerRole role, boolean applyRole) {
        this.role = applyRole && role != null ? role : null;
        this.startingMoney = Math.max(0, startingMoney);
        this.capacityBonus = Math.max(0, capacityBonus);
        this.runningShoes = runningShoes;
        this.scanningGloves = scanningGloves;
        this.shopManager = shopManager;
        this.initialLevel = initialLevel;
        startLevel();
    }

    public boolean isFinished() { return cleared || gameOver; }
    public boolean didWin() { return cleared; }
    public int getScore() { return player.getScore(); }
    public int getMoney() { return player.getMoney(); }
    public int getCleanliness() { return level.cleanliness(); }
    /** Coins paid for clearing the level: the standard +100 plus any level bonus (e.g. a boss). */
    public int getLevelBonus() { return cleared ? 100 + level.clearBonus() : 0; }
    public String getFailureMessage() {
        if (bossFailed) return "The five beach-cleaning waves were not cleared in time.";
        if (timedOut) return level.timeUpMessage();
        return "You ran out of health!";
    }
    public boolean isBossWarningActive() { return bossStarted && showBossWarning && !bossFailed; }
    public boolean isBossBattleActive() {
        return bossStarted && !showBossWarning && !bossDefeated && !bossFailed && !gameOver;
    }
    /** True while any boss is on the field (beach Morphling, Trash Monster, Litter Beast, Waste King). */
    public boolean isBossMusicActive() {
        if (gameOver || cleared) return false;
        if (bossStarted && !bossDefeated && !bossFailed) return true;
        return introTime >= 0 || (trashBoss != null && !trashBoss.isDying());
    }

    private void startLevel() {
        int carriedMoney = player == null ? startingMoney : player.getMoney();
        level = (initialLevel == null ? new Level1Residential() : initialLevel).load();
        player = new Player(level.getSpawnX(), level.getSpawnY(),
                role != null ? role.getSpriteSheet() : null);
        player.setMoney(carriedMoney);
        player.addCapacity(capacityBonus);
        if (runningShoes) player.increaseSpeed(1.25);
        if (scanningGloves) player.setPickupRange(21);
        if (role != null) {
            player.increaseSpeed(role.getSpeedMultiplier());
            player.setHealthMultiplier(role.getHealthMultiplier());
            player.addCapacity(role.getBagBonus());
            player.addMaxHealth(role.getBonusHearts());
        }
        scannerUsed = false;
        scanTimeLeft = 0;
        scannedGarbage.clear();
        trashMonster = null;
        trashBoss = null;
        trashBossDefeated = false;
        introBoss = null;
        introTime = -1;
        shakeTime = shakeStrength = 0;
        bossDamageCooldown = 0;
        cleared = false;
        devRespawnTimer = -1;
        devBossPending = false;
        gameOver = false;
        bossStarted = bossFailed = bossDefeated = showBossWarning = bossVictorySequence = false;
        bossWave = bossWaveRemaining = bossAttackPattern = 0;
        bossWarningTime = bossTimeLeft = bossVictoryTime = bossAttackCooldown = bossAttackAnimation = 0;
        bossAttackTargets.clear();
        morphlingSkills.reset();
        timeLeft = level.timeLimit();
        timedOut = false;
        stageTime = 0;
        String intro = level.introMessage();
        showToast(intro.isBlank() ? "Clean the street! Sort litter into the matching bins." : intro, 4);
    }

    // ------------------------------------------------------------ update
    public void updateFrame(double dt, InputHandler input) {
        stageTime += dt;
        toastTime = Math.max(0, toastTime - dt);
        shakeTime = Math.max(0, shakeTime - dt);
        if (input.isKeyJustPressed(KeyEvent.VK_R)) {
            startLevel();
            if (devAutoBoss) devSkipToBoss();
            return;
        }
        if (cleared || gameOver) return;
        if (introTime >= 0) {           // boss intro cinematic: the world waits
            updateBossIntro(dt, input);
            return;
        }
        if (devRespawnTimer >= 0) {     // auto-respawn: bring the beaten boss back
            devRespawnTimer -= dt;
            if (devRespawnTimer <= 0) {
                devRestartAtBoss();
                return;
            }
        } else if (devAutoBoss && devBossBeaten()) {
            devRespawnTimer = DEV_RESPAWN_DELAY;
            showToast("DEV: Boss beaten - respawning in " + (int) DEV_RESPAWN_DELAY + "s  (F8 = auto-respawn off)", 3);
        }
        handleDevKeys(input);
        if (gameOver) return;
        bossDamageCooldown = Math.max(0, bossDamageCooldown - dt);
        scanTimeLeft = Math.max(0, scanTimeLeft - dt);

        if (timeLeft > 0) {
            timeLeft = Math.max(0, timeLeft - dt);
            if (timeLeft == 0) {
                timedOut = true;
                gameOver = true;
                showToast(level.timeUpMessage(), 4);
                return;
            }
        }
        level.updateTemporaryProps(dt);
        if (level.applyTemporaryEffects(player)) showToast("Sewage splash! Stunned and slowed.", 1.8);
        if (showBossWarning) {          // intro cinematic: the world is frozen
            updateCoastalBoss(dt);
            return;
        }

        int dx = (isDown(input, KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1 : 0)
                - (isDown(input, KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 1 : 0);
        int dy = (isDown(input, KeyEvent.VK_S, KeyEvent.VK_DOWN) ? 1 : 0)
                - (isDown(input, KeyEvent.VK_W, KeyEvent.VK_UP) ? 1 : 0);
        boolean coastalWater = level instanceof Level4Beach beach && beach.isWaterAt(player.centerX(), player.centerY());
        player.setOnBoat(coastalWater);
        if (player.isOnBoat()) {
            int throttle = isDown(input, KeyEvent.VK_W, KeyEvent.VK_UP) ? 1
                    : isDown(input, KeyEvent.VK_S, KeyEvent.VK_DOWN) ? -1 : 0;
            int turn = isDown(input, KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1
                    : isDown(input, KeyEvent.VK_A, KeyEvent.VK_LEFT) ? -1 : 0;
            player.updateBoat(dt, throttle, turn, level);
        } else player.update(dt, dx, dy, level);
        level.update(dt, player);
        String announcement = level.consumeAnnouncement();
        if (announcement != null) showToast(announcement, 2);
        int bonusMoney = level.consumeBonusMoney();
        if (bonusMoney > 0) player.addMoney(bonusMoney);
        if (player.isDead()) {
            gameOver = true;
            showToast("Player health depleted!", 3);
            return;
        }

        nearGarbage = level.nearestGarbage(player.centerX(), player.centerY(), player.getPickupRange());
        nearBin = level.nearestBin(player.centerX(), player.centerY(), BIN_RANGE);
        for (Garbage g : level.getGarbage()) g.setHighlighted(g == nearGarbage);
        for (TrashBin b : level.getBins()) b.setHighlighted(b == nearBin && !player.bagEmpty());

        if (input.isKeyJustPressed(KeyEvent.VK_Q) || input.isKeyJustPressed(KeyEvent.VK_TAB)) {
            player.cycleSelected();
        }
        if (input.isKeyJustPressed(KeyEvent.VK_H)) useHealingPotion();
        if (input.isKeyJustPressed(KeyEvent.VK_F)) useScanner();

        if (input.isKeyJustPressed(KeyEvent.VK_E) || input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            if (nearBin != null && !player.bagEmpty()) throwInBin(nearBin);
            else if (nearGarbage != null) pickUp(nearGarbage);
        }

        if (devBossPending && !bossPresent()) {
            spawnTrashBoss();
            if (bossPresent()) devBossPending = false;
        }
        if (level.hasTrashBoss()) {
            // safety net: the boss always shows up before the level can be finished
            if (!bossPresent() && !trashBossDefeated && level.garbageLeft() == 0 && player.bagEmpty())
                spawnTrashBoss();
            if (trashBoss != null) {
                updateTrashBoss(dt);
                if (gameOver) return;
            }
        }

        if (trashMonster != null) {
            trashMonster.update(dt, player, level);
            int garbageDrops = trashMonster.consumeGarbageDrops();
            if (garbageDrops > 0) {
                for (int i = 0; i < garbageDrops; i++) {
                    level.dropGarbageFromBoss(trashMonster.centerX(), trashMonster.centerY());
                }
                showToast("The Trash Monster dumped more litter!", 2);
            }

            if (trashMonster.isTouching(player) && bossDamageCooldown <= 0) {
                player.hurt(1);
                player.flashRed();
                bossDamageCooldown = 3;
                showToast("The Trash Monster hit you! -1 life", 2);
                if (player.isDead()) {
                    gameOver = true;
                    showToast("Player health depleted!", 3);
                    return;
                }
            }
        }

        if (level instanceof Level4Beach) {
            updateCoastalBoss(dt);
            if (gameOver) return;
        }

        boolean bossGatePassed = !level.hasTrashBoss() || trashBossDefeated;
        if (level instanceof Level4Beach) bossGatePassed = bossDefeated && !bossVictorySequence;
        boolean goalReached = level.sortTarget() > 0
                ? level.getSortedCorrect() >= level.sortTarget()
                : level.garbageLeft() == 0 && player.bagEmpty() && bossGatePassed;
        if (goalReached) finishLevel();
    }

    private void finishLevel() {
        if (devAutoBoss) {              // auto-respawn mode: the level never ends, the boss comes back
            if (devRespawnTimer < 0) {
                devRespawnTimer = 1.0;
                showToast("DEV: Boss beaten - respawning...  (F8 = auto-respawn off)", 2);
            }
            return;
        }
        cleared = true;
        int reward = 100 + level.clearBonus();      // "complete the entire level +P100"
        player.addMoney(reward);
        String done = !level.clearMessage().isBlank() ? level.clearMessage()
                : level instanceof Level4Beach ? "Beach saved!"
                : level instanceof Level2Park ? "Park cleaned!"
                : level instanceof Level5City ? "City center cleaned!"
                : "Street cleaned!";
        showToast(done + " +\u20B1" + reward, 6);
    }

    // ------------------------------------------------------------ beach boss
    private void updateCoastalBoss(double dt) {
        if (!bossStarted && level.garbageLeft() == 0 && player.bagEmpty()) {
            bossStarted = true;
            showBossWarning = true;
            bossWarningTime = BOSS_CINEMATIC_DURATION;
            showToast("WARNING! A sewage morphling is washing trash ashore!", 4);
        }
        if (!bossStarted || bossFailed || gameOver || cleared) return;
        if (bossVictorySequence) {
            bossVictoryTime = Math.max(0, bossVictoryTime - dt);
            if (bossVictoryTime == 0) bossVictorySequence = false;
            return;
        }
        if (bossDefeated) return;
        if (showBossWarning) {
            bossWarningTime = Math.max(0, bossWarningTime - dt);
            if (bossWarningTime == 0) {
                showBossWarning = false;
                bossTimeLeft = BOSS_TIME_LIMIT;
                bossAttackCooldown = BOSS_ATTACK_INTERVAL;
                ((Level4Beach) level).setBossArena(true);
                startBeachWave();
            }
            return;
        }
        bossAttackAnimation = Math.max(0, bossAttackAnimation - dt);
        if (bossAttackAnimation == 0) bossAttackTargets.clear();
        bossTimeLeft = Math.max(0, bossTimeLeft - dt);
        if (bossTimeLeft <= 0) {
            bossFailed = true;
            gameOver = true;
            showToast("The tide overwhelmed the beach. Try again!", 4);
            return;
        }
        if (morphling.touches(player.centerX(), player.centerY(), stageTime, 30)) player.applySlow(6);
        // phase 1 = waves 1-2 (splash only), phase 2 = waves 3-4 (+ tide surge), phase 3 = wave 5 (+ sludgelings)
        int morphPhase = bossWave >= BOSS_WAVES ? 3 : bossWave >= 3 ? 2 : 1;
        morphlingSkills.update(dt, morphPhase, player, level, morphling.x(stageTime) + 42,
                morphling.y(stageTime) + 17, bossAttackAnimation > 0);
        String skillNote = morphlingSkills.consumeMessage();
        if (skillNote != null) showToast(skillNote, 2);
        bossAttackCooldown -= dt;
        if (bossAttackCooldown <= 0) {
            Level4Beach beach = (Level4Beach) level;
            bossAttackTargets.clear();
            bossAttackTargets.addAll(beach.getBossAttackPattern(bossAttackPattern++));
            for (double[] target : bossAttackTargets) level.addTemporaryProp(new WaterImpact(target[0], target[1]));
            bossAttackAnimation = WaterImpact.WARNING_SECONDS;
            bossAttackCooldown = BOSS_ATTACK_INTERVAL;
        }
        if (bossWave > 0 && bossWaveRemaining == 0 && level.garbageLeft() == 0 && player.bagEmpty()) {
            if (bossWave >= BOSS_WAVES) {
                bossDefeated = true;
                morphlingSkills.clear();
                bossVictorySequence = true;
                bossVictoryTime = BOSS_VICTORY_DURATION;
                showToast("The Sewage Morphling is defeated!", 3);
            } else startBeachWave();
        }
    }

    private void startBeachWave() {
        Level4Beach beach = (Level4Beach) level;
        int waveCount = 10 + bossWave * 3;
        int added = beach.scatterBossWaveTrash(waveCount);
        for (int retry = 0; added < waveCount && retry < 3; retry++)
            added += beach.scatterBossWaveTrash(waveCount - added);
        bossWave++;
        bossWaveRemaining = added;
        String skill = bossWave == 3 ? "  It now calls the TIDE - get out of the foam line!"
                : bossWave == BOSS_WAVES ? "  SLUDGELINGS will crawl to the bins - pop them!" : "";
        showToast("Wave " + bossWave + " of " + BOSS_WAVES + "! " + added + " pieces washed ashore." + skill,
                skill.isEmpty() ? 3 : 4);
    }

    private void pickUp(Garbage g) {
        if (player.bagFull()) { showToast("Bag is full! Empty it in a bin.", 2); return; }
        g.setHighlighted(false);
        player.pickUp(g);
        level.removeGarbage(g);
        showToast("Picked up " + g.getItemName() + " +\u20B12", 1.5);
    }

    private boolean hasScanner() {
        return role != null && role.hasScanner();
    }

    /** Scanner Lite: marks every piece of litter within SCAN_RADIUS of the player, once per level. */
    private void useScanner() {
        if (!hasScanner()) return;
        if (scannerUsed) {
            showToast("Scanner Lite already used on this level.", 1.5);
            return;
        }
        scannerUsed = true;
        scanTimeLeft = SCAN_SECONDS;
        scanOriginX = player.centerX();
        scanOriginY = player.centerY();
        scannedGarbage.clear();
        for (Garbage g : level.getGarbage()) {
            if (Math.hypot(g.centerX() - scanOriginX, g.centerY() - scanOriginY) <= SCAN_RADIUS) {
                scannedGarbage.add(g);
            }
        }
        showToast(scannedGarbage.isEmpty() ? "Scanner Lite: no litter nearby."
                : "Scanner Lite: " + scannedGarbage.size() + " litter found nearby!", 2.5);
    }

    /** World-space scan wave plus a bin-coloured marker on each scanned item. */
    private void drawScanner(Graphics2D g) {
        if (scanTimeLeft <= 0) return;
        double elapsed = SCAN_SECONDS - scanTimeLeft;
        if (elapsed < SCAN_WAVE_SECONDS) {
            double k = elapsed / SCAN_WAVE_SECONDS;
            int r = (int) Math.round(SCAN_RADIUS * k);
            g.setColor(new Color(120, 230, 255, (int) (210 * (1 - k))));
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval((int) scanOriginX - r, (int) scanOriginY - r, r * 2, r * 2);
            g.setStroke(new BasicStroke(1f));
        }
        // Markers blink during the last 3 seconds as a warning that the scan is ending.
        if (scanTimeLeft < 3 && ((int) (scanTimeLeft * 6)) % 2 == 0) return;
        int bob = (int) Math.round(Math.sin(stageTime * 6) * 1.5);
        for (Garbage item : level.getGarbage()) {
            if (!scannedGarbage.contains(item)) continue;
            int x = (int) item.getX(), y = (int) item.getY();
            g.setColor(new Color(item.getType().getColor()).brighter());
            // corner brackets around the item
            int l = x - 2, t = y - 2, rr = x + Garbage.SIZE + 1, b = y + Garbage.SIZE + 1;
            g.fillRect(l, t, 3, 1); g.fillRect(l, t, 1, 3);
            g.fillRect(rr - 2, t, 3, 1); g.fillRect(rr, t, 1, 3);
            g.fillRect(l, b, 3, 1); g.fillRect(l, b - 2, 1, 3);
            g.fillRect(rr - 2, b, 3, 1); g.fillRect(rr, b - 2, 1, 3);
            // bobbing arrow above
            int ax = x + Garbage.SIZE / 2, ay = y - 6 + bob;
            g.fillRect(ax - 2, ay, 5, 1);
            g.fillRect(ax - 1, ay + 1, 3, 1);
            g.fillRect(ax, ay + 2, 1, 1);
        }
    }

    private void useHealingPotion() {
        if (player.getHealth() >= player.getMaxHealth()) {
            showToast("Health is already full.", 1.5);
        } else if (shopManager == null || !shopManager.consumePotion()) {
            showToast("No healing potions available. Buy one in the shop.", 2);
        } else {
            player.heal(1);
            showToast("Used a healing potion. +1 heart (" + potionCount() + " left)", 1.5);
        }
    }

    private void throwInBin(TrashBin bin) {
        if (level instanceof Level4Beach && morphlingSkills.isClogged(bin)) {
            showToast("That bin is clogged with sludge! Wait for it to clear.", 1.5);
            return;
        }
        Garbage carried = player.removeSelectedGarbage();
        GarbageType item = carried.getType();
        if (item == bin.getType()) {
            int reward = item == GarbageType.HAZARDOUS ? 10 : 5;
            player.addMoney(reward);
            player.addScore(10);
            if (carried.isBossWaveLitter() && level instanceof Level4Beach)
                bossWaveRemaining = Math.max(0, bossWaveRemaining - 1);
            else level.registerCorrectSort();
            if (trashBoss != null && !trashBoss.isDying() && !trashBoss.isEmerging())   // its HP = trash left
                showToast("Correct! " + bossTitle() + " -1 HP  +\u20B1" + reward, 1.5);
            else showToast("Correct! +\u20B1" + reward + "  +10 pts", 1.5);
            spawnBossWhenCleanlinessReachesThreshold();
        } else {
            player.addScore(-5);
            player.hurt(1);
            level.dropGarbage(carried, player.centerX(), player.centerY());
            level.onWrongSort(carried, player);
            String tip = carried.getTip();
            if (tip.isBlank()) showToast("Wrong bin! " + item.getLabel() + " goes elsewhere.  -1 HP", 2);
            else showToast("Wrong bin! " + carried.getItemName() + " is " + item.getLabel() + ". " + tip
                    + "  -1 HP", 3.5);
            if (player.isDead()) {
                gameOver = true;
                showToast("Player health depleted!", 3);
            }
        }
    }

    private void spawnBossWhenCleanlinessReachesThreshold() {
        if (level.hasTrashBoss()) {
            if (!bossPresent() && !trashBossDefeated && level.cleanliness() >= BOSS_SPAWN_CLEANLINESS)
                spawnTrashBoss();
            return;
        }
        if (trashMonster != null || !level.allowsTrashMonster()
                || level.cleanliness() < BOSS_SPAWN_CLEANLINESS) return;

        double x = 24 * TILE + (TILE - 32) / 2.0;
        double y = 8 * TILE - 8;
        trashMonster = new TrashMonster(x, y);
        level.addNpc(trashMonster);
        showToast("The Trash Monster appeared!", 3);
    }

    // ------------------------------------------------------------ street (Level 1) boss
    // ------------------------------------------------------------ developer tools
    /** Turns on the developer hotkeys and the on-screen key guide (developer account only). */
    public void enableDevTools() { devTools = true; }

    /**
     * Developer boss-test mode (launched with {@code boss.bat} / {@code --boss}): dev tools and
     * auto-respawn on, and the level skips straight to its boss. R goes straight back into the fight.
     */
    public void enableDevBossMode() {
        devTools = true;
        devAutoBoss = true;
        devSkipToBoss();
    }

    /** Trash-boss levels: the litter is cut down to what is left at 75% clean (its HP), then the boss rises. */
    private void startDevBossFight() {
        List<Garbage> litter = level.getGarbage();
        int keep = (int) Math.ceil(litter.size() * (100 - BOSS_SPAWN_CLEANLINESS) / 100.0);
        while (litter.size() > keep) litter.remove(litter.size() - 1);
        devSummonTrashBoss();
    }

    /** Fast-forwards the current, freshly started level to its boss fight. */
    private void devSkipToBoss() {
        if (level.hasTrashBoss()) {
            startDevBossFight();
            return;
        }
        String boss = level.devSkipToBoss();
        if (boss == null) {
            showToast("DEV: This level has no boss.", 2);
            return;
        }
        spawnBossWhenCleanlinessReachesThreshold();     // levels that use the roaming Trash Monster
        showToast("DEV: " + boss + " incoming!" + (devAutoBoss ? "  [auto-respawn ON]" : "")
                + "  (F1 = developer key guide)", 3);
    }

    /** F9 / auto-respawn: restarts the level (full health, fresh boss) and jumps to the boss. */
    private void devRestartAtBoss() {
        // keep the player where they stand instead of sending them back to the spawn point
        double keepX = player.getX(), keepY = player.getY();
        startLevel();
        if (!level.isBlocked(new java.awt.geom.Rectangle2D.Double(keepX + 3, keepY + Player.H - 5, Player.W - 6, 5))) {
            player.x = keepX;
            player.y = keepY;
        }
        devSkipToBoss();
    }

    /** True once the current level's boss has been beaten (bosses that fall before the level ends). */
    private boolean devBossBeaten() {
        if (level.hasTrashBoss()) return trashBossDefeated;
        if (level instanceof Level4Beach) return bossDefeated && !bossVictorySequence;
        return false;   // the rest are caught when the level would finish
    }

    private void handleDevKeys(InputHandler input) {
        if (!devTools) return;
        if (input.isKeyJustPressed(KeyEvent.VK_F1)) devGuideVisible = !devGuideVisible;
        if (input.isKeyJustPressed(KeyEvent.VK_F2)) {
            player.heal(player.getMaxHealth());
            showToast("DEV: Health refilled.", 1.5);
        }
        if (input.isKeyJustPressed(KeyEvent.VK_F3)) {
            level.getGarbage().clear();
            bossWaveRemaining = 0;
            showToast("DEV: All litter on the map cleared.", 1.5);
        }
        if (input.isKeyJustPressed(KeyEvent.VK_F8)) {
            devAutoBoss = !devAutoBoss;
            if (!devAutoBoss) devRespawnTimer = -1;
            showToast(devAutoBoss ? "DEV: Auto-respawn ON - beaten bosses come back, the level never ends."
                    : "DEV: Auto-respawn OFF.", 3);
        }
        if (input.isKeyJustPressed(KeyEvent.VK_F9)) devRestartAtBoss();
        if (input.isKeyJustPressed(KeyEvent.VK_F10)) {   // the boss's HP is the trash left, so this hits it
            List<Garbage> litter = level.getGarbage();
            if (litter.isEmpty()) showToast("DEV: No litter on the map to remove.", 1.5);
            else {
                litter.remove(litter.size() - 1);
                showToast("DEV: Removed 1 piece of litter.", 1.5);
            }
        }
    }

    /** (Re)summons the level's trash boss with full health. */
    private void devSummonTrashBoss() {
        if (trashBoss != null) level.removeNpc(trashBoss.entity());
        trashBoss = null;
        introBoss = null;
        introTime = -1;
        trashBossDefeated = false;
        spawnTrashBoss();
        if (!bossPresent()) {
            devBossPending = true;          // retried every frame until it can rise
            showToast("DEV: The boss can't rise yet - step off its spot.", 3);
            return;
        }
        showToast("DEV: " + titleCase(introBoss.bossName()) + " summoned." + (devAutoBoss ? "  [auto-respawn ON]" : "")
                + "  (F1 = developer key guide)", 3);
    }

    private static final String[][] DEV_KEYS = {
        { "F1", "Show / hide this guide" },
        { "F2", "Refill health" },
        { "F3", "Clear all litter on the map" },
        { "F8", "Auto-respawn boss on / off" },
        { "F9", "Restart at this level's boss" },
        { "F10", "Remove 1 litter (boss -1 HP)" },
        { "R", "Restart the level" },
        { "P", "Pause" },
    };

    private void drawDevGuide(Graphics2D g) {
        if (!devTools) return;
        int x = 12, y = 132;
        if (!devGuideVisible) {
            panel(g, x, y, 132, 26);
            g.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
            g.setColor(new Color(255, 170, 60));
            g.drawString("DEV  [F1] guide", x + 12, y + 18);
            return;
        }
        int lineH = 17, w = 262, h = 34 + DEV_KEYS.length * lineH;
        panel(g, x, y, w, h);
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 13));
        g.setColor(new Color(255, 170, 60));
        g.drawString(devAutoBoss ? "DEVELOPER TOOLS  - AUTO BOSS" : "DEVELOPER TOOLS", x + 12, y + 20);
        for (int i = 0; i < DEV_KEYS.length; i++) {
            int ly = y + 40 + i * lineH;
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
            g.setColor(new Color(255, 225, 120));
            g.drawString(DEV_KEYS[i][0], x + 14, ly);
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
            g.setColor(Color.WHITE);
            g.drawString(DEV_KEYS[i][1], x + 54, ly);
        }
    }

    /**
     * The level's boss rises (the level decides where). Its life is the trash still left: it
     * rises at 75% clean, so the remaining litter is its HP. Nothing happens when the level
     * puts it off for a frame.
     */
    private void spawnTrashBoss() {
        TrashBoss boss = level.createTrashBoss(player);
        if (boss == null) return;
        introBoss = boss;                   // it rises during the intro cinematic
        introTime = 0;
        Entity e = boss.entity();
        introFocusX = e.centerX();
        introFocusY = e.centerY() - 6;
    }

    private boolean bossPresent() { return trashBoss != null || introBoss != null; }

    /**
     * Runs the boss intro: warning, the camera zooms in on its spot, the boss rises (only it moves),
     * the camera zooms out and the fight starts. E, Space or Enter skips it.
     */
    private void updateBossIntro(double dt, InputHandler input) {
        introTime += dt;
        boolean skip = input.isKeyJustPressed(KeyEvent.VK_E) || input.isKeyJustPressed(KeyEvent.VK_SPACE)
                || input.isKeyJustPressed(KeyEvent.VK_ENTER);
        if (trashBoss == null && (introTime >= INTRO_WARN + INTRO_ZOOM_IN || skip)) revealIntroBoss();
        TrashBoss boss = trashBoss != null ? trashBoss : introBoss;
        boss.syncHp(level.garbageLeft() + player.getBag().size());       // its life is the trash left, from the start
        if (trashBoss != null) trashBoss.update(dt, player, level);   // its entrance animation plays
        if (skip || introTime >= INTRO_TOTAL) {
            introTime = -1;
            introBoss = null;
            showToast(trashBoss.introMessage(), 5);
        }
    }

    private void revealIntroBoss() {
        trashBoss = introBoss;
        level.addNpc(trashBoss.entity());
        addShake(1.6, 2.5);
        SoundFx.play(Sound.BOSS_ROAR);
    }

    /** "TRASH MONSTER" as "Trash Monster", for toasts. */
    private String bossTitle() {
        return trashBoss == null ? "The boss" : titleCase(trashBoss.bossName());
    }

    private static String titleCase(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.toLowerCase().split(" ")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private void updateTrashBoss(double dt) {
        TrashBoss boss = trashBoss;
        boss.update(dt, player, level);
        if (boss.consumeToss()) SoundFx.play(Sound.BOSS_TOSS);

        // bags, produce, shockwaves, bursts from below, collapsing minions...
        boolean splat = false;
        for (TrashBoss.Strike st : boss.consumeStrikes()) {
            if (st.radius() > 0) {
                splat = true;
                addShake(st.stun() > 0 ? 0.35 : 0.15, st.stun() > 0 ? 2.5 : 1.2);
                double feetY = player.getY() + Player.H - 3;
                if (Math.hypot(player.centerX() - st.x(), feetY - st.y()) <= st.radius()) {
                    if (st.damage()) hurtByTrashBoss(st.message(), null);
                    else showToast(st.message(), 2);
                    if (st.slow() > 0) player.applySlow(st.slow());
                    if (st.stun() > 0) player.applyStun(st.stun());
                }
            }
            // whatever lands becomes trash that has to be picked up and sorted (+1 HP each)
            for (int i = 0; i < st.litter(); i++) level.dropLitterAt(st.x(), st.y());
            if (gameOver) return;
        }
        if (splat) SoundFx.play(Sound.BOSS_SPLAT);

        // the monster's life is the trash still left: litter on the map + trash in the bag
        boss.syncHp(level.garbageLeft() + player.getBag().size());
        int hpChange = boss.consumeHpChange();
        if (boss.consumeDefeated()) {
            addShake(0.8, 3);
            SoundFx.play(Sound.BOSS_DEFEAT);
            showToast(boss.defeatMessage(), 3.5);
        } else if (hpChange < 0) {
            SoundFx.play(Sound.BOSS_HIT);
        } else if (hpChange > 0) {
            SoundFx.play(Sound.BOSS_HEAL);
        }

        int phase = boss.consumePhaseChange();
        if (phase > 0) {
            addShake(0.7, 3);
            SoundFx.play(Sound.BOSS_ENRAGE);
            showToast(boss.phaseMessage(phase), 3.5);
        }

        if (boss.isDangerous() && boss.isTouching(player) && bossDamageCooldown <= 0) {
            hurtByTrashBoss("The " + bossTitle() + " hit you! -1 HP", boss.knockBack(player));
            if (gameOver) return;
        }
        if (bossDamageCooldown <= 0) {     // its minions and thrown objects
            TrashBoss.Contact c = boss.hazardContact(player);
            if (c != null) {
                double dx = player.centerX() - c.fromX(), dy = player.centerY() - c.fromY();
                double len = Math.max(0.001, Math.hypot(dx, dy));
                hurtByTrashBoss(c.message(), new double[] { dx / len * 14, dy / len * 14 });
                if (gameOver) return;
            }
        }

        if (boss.isGone()) {
            if (level.garbageLeft() > 0 || !player.bagEmpty())
                showToast("The " + bossTitle() + " is gone! Finish cleaning up.", 3);
            level.removeNpc(boss.entity());
            trashBoss = null;
            trashBossDefeated = true;
        }
    }

    private void hurtByTrashBoss(String message, double[] knock) {
        if (bossDamageCooldown > 0) return;
        SoundFx.play(Sound.BOSS_SMACK);
        player.hurt(1);
        player.flashRed();
        if (knock != null) player.knockBack(knock[0], knock[1], level);
        bossDamageCooldown = STREET_BOSS_HIT_COOLDOWN;
        addShake(0.25, 2);
        showToast(message, 2);
        if (player.isDead()) {
            gameOver = true;
            showToast("Player health depleted!", 3);
        }
    }

    private void addShake(double seconds, double strength) {
        shakeStrength = Math.max(shakeTime > 0 ? shakeStrength : 0, strength);
        shakeTime = Math.max(shakeTime, seconds);
    }

    private void drawTrashBossBar(Graphics2D g, int W) {
        TrashBoss boss = trashBoss;
        if (boss == null || boss.isGone()) return;
        int pw = Math.min(300, W - 40), px = (W - pw) / 2, py = 12;
        panel(g, px, py, pw, 50);
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
        g.setColor(new Color(255, 112, 103));
        g.drawString(boss.bossName(), px + 14, py + 20);
        String phase = boss.isDying() ? "DEFEATED" : boss.isEmerging() ? "AWAKENING" : "PHASE " + boss.getPhase();
        g.setColor(boss.getPhase() == 3 && !boss.isDying() ? new Color(255, 80, 70) : new Color(230, 230, 230));
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        g.drawString(phase, px + pw - 14 - g.getFontMetrics().stringWidth(phase), py + 20);

        // health bar: HP = trash left, drawn against the most it has had this fight
        int hp = boss.getHp(), peak = Math.max(1, boss.getPeakHp());
        int barX = px + 14, barY = py + 28, barW = pw - 28;
        g.setColor(new Color(55, 40, 40));
        g.fillRect(barX, barY, barW, 12);
        boolean hitFlash = boss.getHitFlash() > 0 && ((int) (boss.getHitFlash() * 20)) % 2 == 0;
        boolean healFlash = boss.getHealFlash() > 0 && ((int) (boss.getHealFlash() * 20)) % 2 == 0;
        g.setColor(hitFlash ? Color.WHITE : healFlash ? new Color(255, 120, 90)
                : boss.getPhase() == 3 ? new Color(220, 60, 50)
                : boss.getPhase() == 2 ? new Color(230, 150, 50) : new Color(120, 200, 80));
        g.fillRect(barX, barY, barW * Math.min(hp, peak) / peak, 12);
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
        String label = "HP " + hp + "  (trash left)";
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(label, barX + (barW - g.getFontMetrics().stringWidth(label)) / 2 + 1, barY + 11);
        g.setColor(Color.WHITE);
        g.drawString(label, barX + (barW - g.getFontMetrics().stringWidth(label)) / 2, barY + 10);
    }

    private void showToast(String t, double secs) { toast = t; toastTime = secs; }

    private boolean isDown(InputHandler input, int a, int b) {
        return input.isKeyDown(a) || input.isKeyDown(b);
    }

    // ------------------------------------------------------------ render
    public void renderFrame(Graphics2D g, int width, int height) {
        Graphics2D fg = frame.createGraphics();
        level.draw(fg, player);
        drawScanner(fg);
        if (trashBoss != null) trashBoss.drawEffects(fg);
        if (bossStarted && level instanceof Level4Beach) {
            if (!showBossWarning) morphlingSkills.draw(fg);
            if (showBossWarning) morphling.drawEntrance(fg, stageTime, bossSplashProgress());
            else if (bossVictorySequence)
                morphling.drawDefeated(fg, stageTime, 1 - bossVictoryTime / BOSS_VICTORY_DURATION);
            else if (!bossDefeated) morphling.draw(fg, stageTime, bossAttackAnimation, bossAttackTargets);
        }
        fg.dispose();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (showBossWarning) {
            drawBossCinematicWorld(g, width, height);
            drawBossOverlay(g, width, height);
            return;
        }
        if (introTime >= 0) {
            drawIntroWorld(g, width, height);
            drawIntroOverlay(g, width, height);
            return;
        }
        int shakeX = 0, shakeY = 0;
        if (shakeTime > 0) {
            double s = shakeStrength * Math.min(1, shakeTime / 0.25) * width / WORLD_W;
            shakeX = (int) Math.round(Math.sin(stageTime * 53) * s);
            shakeY = (int) Math.round(Math.cos(stageTime * 41) * s * 0.6);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);
        }
        g.drawImage(frame, shakeX, shakeY, width, height, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        level.drawOverlay(g, width, height);
        drawHud(g, width, height);
    }

    private void panel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(15, 20, 25, 190));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(new Color(255, 255, 255, 60));
        g.drawRoundRect(x, y, w, h, 12, 12);
    }

    private int potionCount() {
        return shopManager == null ? 0 : shopManager.getPotionCount();
    }

    /** Shop gear worn this run plus the potion pouch, under the stats panel in every level. */
    private void drawGearPanel(Graphics2D g, int minWidth) {
        StringBuilder gear = new StringBuilder();
        if (capacityBonus > 0) {
            String bag = shopManager == null ? null : shopManager.getEquippedBagName();
            gear.append(bag == null ? "Bag" : bag.replace(" Trash", "")).append(" +").append(capacityBonus);
        }
        if (runningShoes) gear.append(gear.length() > 0 ? "  •  " : "").append("Shoes +25%");
        if (scanningGloves) gear.append(gear.length() > 0 ? "  •  " : "").append("Gloves");
        String gearText = "Gear: " + (gear.length() > 0 ? gear : "none");

        g.setFont(new Font(Font.DIALOG, Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        int max = shopManager == null ? 5 : shopManager.getMaxPotions();
        String potionLabel = "Potions [H]";
        int w = Math.max(minWidth, Math.max(fm.stringWidth(gearText), fm.stringWidth(potionLabel) + 8 + max * 14) + 24);
        int y = 132;
        panel(g, 12, y, w, 50);
        g.setColor(gear.length() > 0 ? new Color(150, 225, 255) : new Color(170, 170, 175));
        g.drawString(gearText, 24, y + 19);
        g.setColor(Color.WHITE);
        g.drawString(potionLabel, 24, y + 39);
        int px = 24 + fm.stringWidth(potionLabel) + 8;
        for (int i = 0; i < max; i++) {
            boolean full = i < potionCount();
            int x = px + i * 14;
            g.setColor(full ? new Color(220, 235, 238) : new Color(70, 72, 78));
            g.fillRect(x + 3, y + 27, 4, 3);          // neck
            g.fillRect(x, y + 30, 10, 11);            // flask
            if (full) {
                g.setColor(new Color(229, 72, 77));
                g.fillRect(x + 1, y + 33, 8, 7);      // liquid
            }
        }
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 15));
    }

    private void drawHud(Graphics2D g, int W, int H) {
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 15));

        // top-left stats
        String title = "ECO QUEST - " + level.getName();
        FontMetrics statsMetrics = g.getFontMetrics();
        int statsPanelWidth = Math.max(270, statsMetrics.stringWidth(title) + 24);
        statsPanelWidth = Math.min(statsPanelWidth, W - 206);
        panel(g, 12, 12, statsPanelWidth, 112);
        g.setColor(Color.WHITE);
        g.drawString(title, 24, 34);
        g.setColor(new Color(255, 214, 90));
        g.drawString("\u20B1 " + player.getMoney(), 24, 56);
        g.setColor(Color.WHITE);
        g.drawString("Score " + player.getScore(), 130, 56);
        for (int i = 0; i < player.getMaxHealth(); i++) {
            g.setColor(i < player.getHealth() ? new Color(230, 60, 70) : new Color(90, 90, 95));
            g.setFont(new Font(Font.DIALOG, Font.BOLD, 20));
            g.drawString("\u2665", 24 + i * 24, 82);
        }
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 15));
        int pct = level.cleanliness();
        g.setColor(Color.WHITE);
        g.drawString("Cleanliness " + pct + "%", 24, 100);
        g.setColor(new Color(60, 60, 65));
        int cleanBarWidth = statsPanelWidth - 36;
        g.fillRect(24, 106, cleanBarWidth, 9);
        g.setColor(pct < 50 ? new Color(220, 150, 50) : new Color(80, 200, 90));
        g.fillRect(24, 106, cleanBarWidth * pct / 100, 9);

        drawGearPanel(g, statsPanelWidth);

        // top-right garbage counter (+ park timer)
        boolean bossActive = bossStarted && !bossDefeated;
        String counter = bossActive ? "Wave litter: " + bossWaveRemaining
                : level.sortTarget() > 0 ? "Sorted: " + level.getSortedCorrect() + " / " + level.sortTarget()
                : "Litter left: " + level.garbageLeft();
        int counterHeight = timeLeft > 0 ? 58 : 36;
        panel(g, W - 182, 12, 170, counterHeight);
        g.setColor(Color.WHITE);
        g.drawString(counter, W - 168, 36);
        if (timeLeft > 0) {
            g.setColor(timeLeft <= 30 ? new Color(255, 112, 103) : new Color(255, 226, 122));
            g.drawString(String.format("Time %d:%02d", (int) timeLeft / 60, (int) timeLeft % 60), W - 168, 58);
        }

        // Litter Picker's Scanner Lite status
        if (hasScanner()) {
            int sy = 12 + counterHeight + 8;
            panel(g, W - 182, sy, 170, 30);
            String status = scanTimeLeft > 0 ? "SCANNING " + (int) Math.ceil(scanTimeLeft) + "s"
                    : scannerUsed ? "SCANNER: USED" : "SCANNER [F]: READY";
            g.setColor(scanTimeLeft > 0 ? new Color(120, 230, 255)
                    : scannerUsed ? new Color(150, 155, 165) : new Color(140, 235, 150));
            g.drawString(status, W - 168, sy + 21);
        }

        // beach boss wave banner
        if (bossActive) {
            int seconds = (int) Math.ceil(bossTimeLeft);
            String waveText = "WAVE " + Math.max(1, bossWave) + "/" + BOSS_WAVES + "   " + bossWaveRemaining
                    + " LEFT   " + (seconds / 60) + ":" + String.format("%02d", seconds % 60);
            g.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
            int waveWidth = g.getFontMetrics().stringWidth(waveText) + 32;
            panel(g, (W - waveWidth) / 2, 12, waveWidth, 34);
            g.setColor(new Color(255, 112, 103));
            g.drawString(waveText, (W - waveWidth) / 2 + 16, 35);
            g.setFont(new Font(Font.DIALOG, Font.BOLD, 15));
        }

        // bottom-left bag
        panel(g, 12, H - 74, 24 + player.getCapacity() * 36, 62);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        g.drawString("BAG  (Q = switch item)", 24, H - 56);
        for (int i = 0; i < player.getCapacity(); i++) {
            int sx = 24 + i * 36, sy = H - 48;
            Garbage item = i < player.getBag().size() ? player.getBag().get(i) : null;
            g.setColor(item == null ? new Color(50, 55, 60) : new Color(item.getType().getColor()));
            g.fillRect(sx, sy, 30, 26);
            if (item != null) item.drawIcon(g, sx + 6, sy + 4, 18);
            if (i == player.getSelectedIndex() && !player.bagEmpty()) {
                g.setColor(Color.WHITE);
                g.drawRect(sx - 1, sy - 1, 31, 27);
            }
        }

        if (player.getSlowTimer() > 0) {
            g.setFont(new Font(Font.DIALOG, Font.BOLD, 13));
            g.setColor(new Color(255, 170, 90));
            g.drawString((player.isStunned() ? "STUNNED  " : "SLOWED ")
                    + (int) Math.ceil(player.getSlowTimer()) + "s", 24, H - 84);
        }

        // bottom-right controls
        g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        g.setColor(new Color(255, 255, 255, 220));
        String controls = player.isOnBoat()
                ? "BOAT  W/S: throttle   A/D: steer   E: pick up   Q: switch item   R: restart"
                : "WASD / Arrows: move   E: pick up / throw   Q: switch item   R: restart";
        if (hasScanner() && !scannerUsed) controls = "F: scan   " + controls;
        if (potionCount() > 0) controls = "H: potion   " + controls;
        g.drawString(controls, W - g.getFontMetrics().stringWidth(controls) - 14, H - 14);

        // context hint
        drawTrashBossBar(g, W);
        drawDevGuide(g);
        String hint = null;
        if (nearBin != null && !player.bagEmpty())
            hint = "E: Throw " + player.getBag().get(player.getSelectedIndex()).getItemName()
                    + " in the " + nearBin.getType().getLabel() + " bin";
        else if (nearGarbage != null) hint = "E: pick up " + nearGarbage.getItemName();
        if (hint != null) centered(g, hint, W, H - 96, 15);

        if (toastTime > 0) centered(g, toast, W, trashBoss != null ? 104 : 70, 16);

    }

    // ------------------------------------------------------------ beach boss cinematic
    private double bossCinematicElapsed() {
        return Math.max(0, BOSS_CINEMATIC_DURATION - bossWarningTime);
    }

    private double bossCameraZoom() {
        double elapsed = bossCinematicElapsed();
        double zoom = 2.35;
        if (elapsed < BOSS_WARNING_PHASE) return 1.0;
        elapsed -= BOSS_WARNING_PHASE;
        if (elapsed < BOSS_ZOOM_IN_PHASE)
            return 1.0 + (zoom - 1.0) * ease(elapsed / BOSS_ZOOM_IN_PHASE);
        elapsed -= BOSS_ZOOM_IN_PHASE + BOSS_SPLASH_PHASE;
        if (elapsed < BOSS_ZOOM_OUT_PHASE)
            return zoom - (zoom - 1.0) * ease(elapsed / BOSS_ZOOM_OUT_PHASE);
        return 1.0;
    }

    private double bossSplashProgress() {
        double elapsed = bossCinematicElapsed() - BOSS_WARNING_PHASE - BOSS_ZOOM_IN_PHASE;
        return Math.max(0, Math.min(1, elapsed / BOSS_SPLASH_PHASE));
    }

    private double ease(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    private void drawBossCinematicWorld(Graphics2D g, int width, int height) {
        double splash = bossSplashProgress();
        double shake = Math.sin(splash * Math.PI) * 7 * Math.sin(stageTime * 48);
        drawZoomedWorld(g, width, height, bossCameraZoom(), morphling.x(stageTime) + 36, morphling.y(stageTime) + 30, shake, false);
    }

    /**
     * The world frame scaled up by {@code zoom} around a world point. With {@code center} the point
     * glides to the middle of the screen (never past the map edges); otherwise it stays where it was.
     */
    private void drawZoomedWorld(Graphics2D g, int width, int height, double zoom, double focusX, double focusY,
                                 double shake, boolean center) {
        int drawWidth = (int) Math.round(width * zoom);
        int drawHeight = (int) Math.round(height * zoom);
        int drawX, drawY;
        if (center) {
            drawX = (int) Math.round(Math.max(width - drawWidth, Math.min(0, width / 2.0 - focusX / WORLD_W * drawWidth)));
            drawY = (int) Math.round(Math.max(height - drawHeight, Math.min(0, height / 2.0 - focusY / WORLD_H * drawHeight)));
        } else {
            drawX = (int) Math.round(-focusX / WORLD_W * width * (zoom - 1));
            drawY = (int) Math.round(-focusY / WORLD_H * height * (zoom - 1));
        }
        Graphics2D scene = (Graphics2D) g.create();
        scene.translate(shake, -shake * 0.45);
        scene.drawImage(frame, drawX, drawY, drawWidth, drawHeight, null);
        scene.dispose();
    }

    // ------------------------------------------------------------ Level 1-3 boss intro cinematic
    private double introZoom() {
        double t = introTime - INTRO_WARN;
        if (t < 0) return 1.0;
        if (t < INTRO_ZOOM_IN) return 1.0 + (INTRO_ZOOM - 1.0) * ease(t / INTRO_ZOOM_IN);
        t -= INTRO_ZOOM_IN + INTRO_REVEAL;
        if (t < 0) return INTRO_ZOOM;
        return INTRO_ZOOM - (INTRO_ZOOM - 1.0) * ease(t / INTRO_ZOOM_OUT);
    }

    private void drawIntroWorld(Graphics2D g, int width, int height) {
        double reveal = (introTime - INTRO_WARN - INTRO_ZOOM_IN) / INTRO_REVEAL;
        double rumble = introTime < INTRO_WARN + INTRO_ZOOM_IN
                ? 1.5 * Math.min(1, introTime / INTRO_WARN)                                   // the ground trembles
                : reveal < 1 ? 7 * Math.max(0, 1 - reveal * 2.2) : 0;                         // the boss bursts out
        double shake = rumble * Math.sin(stageTime * 48 + introTime * 37);
        drawZoomedWorld(g, width, height, introZoom(), introFocusX, introFocusY, shake, true);
    }

    private void drawIntroOverlay(Graphics2D g, int width, int height) {
        TrashBoss boss = trashBoss != null ? trashBoss : introBoss;
        TrashBoss.Intro intro = boss.intro();
        double t = introTime;
        String title, detail;
        Color titleColor = new Color(255, 76, 68);
        if (t < INTRO_WARN) {
            int pulse = (int) (45 + 55 * Math.abs(Math.sin(t * 8)));
            g.setColor(new Color(145, 8, 13, pulse));
            g.fillRect(0, 0, width, height);
            title = "!!! WARNING !!!";
            detail = intro.warning();
        } else if (t < INTRO_WARN + INTRO_ZOOM_IN) {
            title = intro.stirTitle();
            detail = intro.stirDetail();
        } else if (t < INTRO_WARN + INTRO_ZOOM_IN + INTRO_REVEAL) {
            title = intro.name();
            detail = intro.tagline();
            titleColor = new Color(intro.color());
            double k = (t - INTRO_WARN - INTRO_ZOOM_IN) / 0.35;
            int flash = (int) (Math.max(0, 1 - k) * 110);      // white flash as it bursts out
            if (flash > 0) {
                g.setColor(new Color(255, 250, 230, flash));
                g.fillRect(0, 0, width, height);
            }
        } else {
            title = "ITS LIFE IS THE TRASH LEFT";
            detail = "Sort every piece correctly to beat it!";
        }
        drawCinematicBars(g, width, height, title, detail, titleColor, "E / SPACE: skip");
    }

    /** Letterbox bars with a big title on top and a line of detail at the bottom. */
    private void drawCinematicBars(Graphics2D g, int width, int height, String title, String detail, Color titleColor, String hint) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double scale = Math.max(0.65, Math.min(1.5, Math.min(width / 1024.0, height / 576.0)));
        int barHeight = Math.max(32, (int) Math.round(46 * scale));
        g.setColor(new Color(4, 10, 16, 224));
        g.fillRect(0, 0, width, barHeight);
        g.fillRect(0, height - barHeight, width, barHeight);
        g.setColor(titleColor);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, Math.max(15, (int) Math.round(24 * scale))));
        int titleWidth = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (width - titleWidth) / 2, barHeight - (int) Math.round(13 * scale));
        g.setColor(new Color(245, 248, 239));
        g.setFont(new Font(Font.DIALOG, Font.BOLD, Math.max(10, (int) Math.round(13 * scale))));
        int detailWidth = g.getFontMetrics().stringWidth(detail);
        g.drawString(detail, (width - detailWidth) / 2, height - barHeight + (int) Math.round(28 * scale));
        if (hint != null) {
            g.setColor(new Color(245, 248, 239, 120));
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, Math.max(9, (int) Math.round(10 * scale))));
            int hw = g.getFontMetrics().stringWidth(hint);
            g.drawString(hint, width - hw - (int) Math.round(12 * scale), height - (int) Math.round(8 * scale));
        }
    }

    private void drawBossOverlay(Graphics2D g, int width, int height) {
        double scale = Math.max(0.65, Math.min(1.5, Math.min(width / 1024.0, height / 576.0)));
        double elapsed = bossCinematicElapsed();
        int barHeight = Math.max(32, (int) Math.round(46 * scale));

        String title;
        String detail;
        if (elapsed < BOSS_WARNING_PHASE) {
            int pulse = (int) (45 + 55 * Math.abs(Math.sin(stageTime * 8)));
            g.setColor(new Color(145, 8, 13, pulse));
            g.fillRect(0, barHeight, width, height - barHeight * 2);
            title = "!!! WARNING !!!";
            detail = "A SEWAGE MORPHLING IS APPROACHING";
        } else if (elapsed < BOSS_WARNING_PHASE + BOSS_ZOOM_IN_PHASE) {
            title = "THE TIDE IS RISING";
            detail = "Something is moving beneath the surf...";
        } else if (elapsed < BOSS_WARNING_PHASE + BOSS_ZOOM_IN_PHASE + BOSS_SPLASH_PHASE) {
            title = "THE SEWAGE MORPHLING";
            detail = "IT WASHES TRASH BACK ASHORE!";
            int flash = (int) (Math.max(0, 1 - bossSplashProgress() * 5) * 80);
            if (flash > 0) {
                g.setColor(new Color(154, 247, 239, flash));
                g.fillRect(0, 0, width, height);
            }
        } else if (elapsed >= BOSS_CINEMATIC_DURATION - 0.95) {
            title = "THE BEACH IS CHANGING";
            detail = "A surge is breaking through the shoreline...";
            double flashProgress = (elapsed - (BOSS_CINEMATIC_DURATION - 0.95)) / 0.95;
            double flashAlpha = flashProgress < 0.32 ? flashProgress / 0.32 : (1 - flashProgress) / 0.68;
            g.setColor(new Color(255, 255, 255, (int) (255 * Math.max(0, Math.min(1, flashAlpha)))));
            g.fillRect(0, 0, width, height);
        } else {
            title = "FIVE WAVES. FIVE MINUTES.";
            detail = "Clear every wave to save the beach.";
        }
        drawCinematicBars(g, width, height, title, detail, new Color(255, 76, 68), null);
    }

    private void centered(Graphics2D g, String text, int width, int y, int size) {
        g.setFont(new Font(Font.DIALOG, Font.BOLD, size));
        int tw = g.getFontMetrics().stringWidth(text);
        int x = (width - tw) / 2;
        g.setColor(new Color(15, 20, 25, 200));
        g.fillRoundRect(x - 12, y - size - 2, tw + 24, size + 12, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString(text, x, y);
    }
}
