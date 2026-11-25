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
    private static final int BOSS_WAVES = 3;
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
    /** The Morphling's Tide Surge (waves 2-3) and Sludgelings (wave 3); its geyser volley lives here. */
    private final MorphlingSkills morphlingSkills = new MorphlingSkills();
    /** In-game menu (guide + settings), opened by the HUD menu button or Esc. */
    private final PauseMenu menu = new PauseMenu();
    private final Rectangle menuButton = new Rectangle();
    private boolean quitRequested;
    private int viewW = com.ecoquest.util.Constants.WINDOW_WIDTH, viewH = com.ecoquest.util.Constants.WINDOW_HEIGHT;
    private InputHandler lastInput;
    /** Bottom of the stats card, so the developer panel can sit under it. */
    private int statsBottom = 70;

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

    /** True once after the player picks "Main menu" in the in-game menu. */
    public boolean consumeQuitRequest() {
        boolean quit = quitRequested;
        quitRequested = false;
        return quit;
    }
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
        morphling.reset();
        timeLeft = level.timeLimit();
        timedOut = false;
        stageTime = 0;
        String intro = level.introMessage();
        showToast(intro.isBlank() ? "Clean up! Sort the litter into the matching bins." : intro, 4);
    }

    // ------------------------------------------------------------ update
    public void updateFrame(double dt, InputHandler input) {
        lastInput = input;
        Point click = input.consumeMouseClick();
        if (!cleared && !gameOver) {
            if (!menu.isOpen() && (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)
                    || (click != null && menuButton.contains(click)))) {
                menu.open();
                return;
            }
            if (menu.isOpen()) {                    // the level is frozen while the menu is open
                switch (menu.update(dt, input, click, viewW, viewH)) {
                    case RESTART -> {
                        startLevel();
                        if (devAutoBoss) devSkipToBoss();
                    }
                    case QUIT -> quitRequested = true;
                    default -> { }
                }
                return;
            }
        }
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
        if (level.applyTemporaryEffects(player)) {
            SoundFx.play(Sound.BOSS_SPLAT);
            addShake(0.2, 1.5);
            showToast("A geyser launched you! Stunned.", 1.8);
        }
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
        morphling.update(dt, stageTime);
        double morphShake = morphling.consumeShake();
        if (morphShake > 0) addShake(0.35, morphShake);
        if (morphling.consumeEmerged()) {                 // burst out of the sand: slows, no damage
            double ex = morphling.emergeX(), ey = morphling.emergeY();
            ((Level4Beach) level).setBossSpot(ex, ey - 30);
            double dx = (player.centerX() - ex) / 32, dy = (player.getY() + Player.H - 2 - ey) / 20;
            if (!player.isOnBoat() && dx * dx + dy * dy <= 1) {
                player.applyHeavySlow(4.5);                // crawl at a quarter speed
                SoundFx.play(Sound.BOSS_SPLAT);
                showToast("The Morphling burst out under you! Heavily slowed.", 2);
            }
        }
        // Each wave adds a skill: wave 1 = geysers, wave 2 = + tide surge, wave 3 = + sludgelings
        int morphPhase = Math.max(1, Math.min(3, bossWave));
        // One skill at a time, so every attack gets its own wind-up animation.
        morphlingSkills.update(dt, morphPhase, player, level, morphling.mouthX(stageTime),
                morphling.mouthY(stageTime), bossAttackAnimation > 0 || morphling.isCasting());
        CoastalMorphling.Cast skillCast = morphlingSkills.consumeCast();
        Level4Beach beachLevel = (Level4Beach) level;
        double feetY = player.getY() + Player.H - 2;
        if (skillCast == CoastalMorphling.Cast.BURROW) {   // tunnels straight at the player
            double[] spot = beachLevel.burrowSpotAt(player.centerX(), feetY);
            if (spot == null) spot = beachLevel.getBurrowTarget(player.centerX(), feetY,
                    morphling.x(stageTime), morphling.y(stageTime));
            morphling.startBurrow(spot[0], spot[1]);
        } else if (skillCast != null) morphling.startCast(skillCast);
        if (morphling.isTrackingTarget()) {               // keeps homing in until the ring locks
            double[] spot = beachLevel.burrowSpotAt(player.centerX(), feetY);
            if (spot != null) morphling.retarget(spot[0], spot[1]);
        }
        String skillNote = morphlingSkills.consumeMessage();
        if (skillNote != null) showToast(skillNote, 2);
        if (morphlingSkills.consumePlayerHit()) {          // the tide surge hits for 1 life
            SoundFx.play(Sound.BOSS_SMACK);
            player.flashRed();
            addShake(0.3, 2.5);
            if (player.isDead()) {
                gameOver = true;
                showToast("Player health depleted!", 3);
                return;
            }
        }
        bossAttackCooldown -= dt;
        if (bossAttackCooldown <= 0 && !morphling.isCasting()) {
            Level4Beach beach = (Level4Beach) level;
            morphling.startCast(CoastalMorphling.Cast.SPLASH);
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
        morphling.startCast(CoastalMorphling.Cast.ROAR);
        String skill = bossWave == 1 ? "  GEYSERS burst from the red circles - step out before they blow!"
                : bossWave == 2 ? "  It now calls the TIDE - get out of the foam line!"
                : "  SLUDGELINGS will crawl to the bins - pop them!";
        showToast("Wave " + bossWave + " of " + BOSS_WAVES + "! " + added + " pieces washed ashore." + skill, 4);
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
            if (carried.isBossWaveLitter() && level instanceof Level4Beach) {
                bossWaveRemaining = Math.max(0, bossWaveRemaining - 1);
                morphling.flinch();
            }
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
        int x = 14, y = statsBottom + 10;
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
        if (!PauseMenu.screenShake()) return;
        shakeStrength = Math.max(shakeTime > 0 ? shakeStrength : 0, strength);
        shakeTime = Math.max(shakeTime, seconds);
    }

    private void drawTrashBossBar(Graphics2D g, int W, int py) {
        TrashBoss boss = trashBoss;
        if (boss == null || boss.isGone()) return;
        int pw = Math.min(300, W - 40), px = (W - pw) / 2;
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
        viewW = width;
        viewH = height;
        renderScene(g, width, height);
        if (menu.isOpen()) menu.render(g, width, height, lastInput, level.getName(), gearSummary());
    }

    private void renderScene(Graphics2D g, int width, int height) {
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
        if (shakeTime > 0 && PauseMenu.screenShake()) {
            double s = shakeStrength * Math.min(1, shakeTime / 0.25) * width / WORLD_W;
            shakeX = (int) Math.round(Math.sin(stageTime * 53) * s);
            shakeY = (int) Math.round(Math.cos(stageTime * 41) * s * 0.6);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);
        }
        g.drawImage(frame, shakeX, shakeY, width, height, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        level.drawOverlay(g, width, height);
        if (!menu.isOpen()) drawHud(g, width, height);   // the pause menu has the screen to itself
    }

    /** A HUD plate (pixel style, same as the main menu). */
    private void panel(Graphics2D g, int x, int y, int w, int h) {
        HudArt.plate(g, x, y, w, h, false);
    }

    private int potionCount() {
        return shopManager == null ? 0 : shopManager.getPotionCount();
    }

    private static final int HUD_MARGIN = 12, CHIP_H = 30, LEVEL_BANNER_SECONDS = 4;
    /** HUD text size: 1.5x the pixel font, so the text stays small but crisp. */
    private static final double TXT = 1.5;

    /**
     * The HUD is split into small chips along the edges so the middle of the map stays clear:
     * lives and coins top-left, cleanliness top-centre, litter count and the menu button top-right,
     * the bag bottom-centre, key hints bottom-right.
     */
    private void drawHud(Graphics2D g, int W, int H) {
        Graphics2D hud = (Graphics2D) g.create();
        hud.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        statsBottom = drawLivesAndCoins(hud);
        drawTopRight(hud, W);
        int centerBottom = drawTopCenter(hud, W);
        int hotbarTop = drawHotbar(hud, W, H);
        drawStatusPill(hud, H);
        drawKeyHints(hud, W, H);
        drawDevGuide(hud);

        if (nearBin != null && !player.bagEmpty())
            drawActionHint(hud, "E", "Throw " + player.getBag().get(player.getSelectedIndex()).getItemName()
                    + " in " + nearBin.getType().getLabel(), W, hotbarTop - 34);
        else if (nearGarbage != null) drawActionHint(hud, "E", "Pick up " + nearGarbage.getItemName(), W, hotbarTop - 34);

        int toastY = centerBottom + 8;
        if (stageTime < LEVEL_BANNER_SECONDS && introTime < 0) toastY = drawLevelBanner(hud, W, toastY) + 8;
        if (toastTime > 0) drawToast(hud, toast, W, toastY);
        hud.dispose();
    }

    /** Top-left: coin and score chips, with the lives chip under them. Returns the bottom. */
    private int drawLivesAndCoins(Graphics2D g) {
        int x = HUD_MARGIN, y = HUD_MARGIN;
        String money = String.valueOf(player.getMoney());
        int mw = 34 + HudArt.width(money, TXT, HudArt.GOLD_TEXT);
        panel(g, x, y, mw, 26);
        HudArt.coin(g, x + 9, y + 7, 2);
        HudArt.text(g, money, x + 24, y + 8, TXT, HudArt.GOLD_TEXT);

        String score = String.valueOf(player.getScore());
        int sx = x + mw + 6, sw = 34 + HudArt.width(score, TXT, HudArt.WHITE);
        panel(g, sx, y, sw, 26);
        HudArt.star(g, sx + 9, y + 8, 2);
        HudArt.text(g, score, sx + 24, y + 8, TXT, HudArt.WHITE);

        int row = y + 26 + 6, hearts = player.getMaxHealth();
        int w = 14 + hearts * 18 - 4;
        panel(g, x, row, w, 26);
        for (int i = 0; i < hearts; i++) HudArt.heart(g, x + 7 + i * 18, row + 6, 2, i < player.getHealth());
        return row + 26;
    }

    private String gearSummary() {
        StringBuilder gear = new StringBuilder();
        if (capacityBonus > 0) {
            String bag = shopManager == null ? null : shopManager.getEquippedBagName();
            gear.append(bag == null ? "Bag" : bag.replace(" Trash", "")).append(" +").append(capacityBonus);
        }
        if (runningShoes) gear.append(gear.length() > 0 ? ", " : "").append("Shoes");
        if (scanningGloves) gear.append(gear.length() > 0 ? ", " : "").append("Gloves");
        if (shopManager != null)
            gear.append(gear.length() > 0 ? ", " : "").append("Potions ").append(potionCount())
                    .append("/").append(shopManager.getMaxPotions());
        return gear.length() > 0 ? gear.toString() : "none";
    }

    /** Top-right: the menu button, the litter count, and the timer / scanner chips under it. */
    private void drawTopRight(Graphics2D g, int W) {
        int size = 34, bx = W - HUD_MARGIN - size, by = HUD_MARGIN;
        menuButton.setBounds(bx, by, size, size);
        boolean hover = lastInput != null && menuButton.contains(lastInput.getMouseX(), lastInput.getMouseY());
        HudArt.plate(g, bx, by, size, size, hover);
        for (int i = 0; i < 3; i++) {
            g.setColor(new Color(10, 16, 26));
            g.fillRect(bx + 9, by + 10 + i * 6 + 1, 16, 3);
            g.setColor(hover ? new Color(255, 255, 240) : new Color(226, 232, 240));
            g.fillRect(bx + 9, by + 10 + i * 6, 16, 3);
        }

        boolean bossActive = bossStarted && !bossDefeated;
        String value = bossActive ? String.valueOf(bossWaveRemaining)
                : level.sortTarget() > 0 ? level.getSortedCorrect() + "/" + level.sortTarget()
                : String.valueOf(level.garbageLeft());
        String label = level.sortTarget() > 0 && !bossActive ? "Sorted" : "Litter";   // boss waves count litter too
        int vw = HudArt.width(value, 2, HudArt.WHITE), lw = HudArt.width(label, TXT, HudArt.DIM);
        int cw = 32 + vw + 8 + lw, cx = bx - 6 - cw;
        panel(g, cx, by, cw, size);
        HudArt.trash(g, cx + 9, by + 11, 2);
        HudArt.text(g, value, cx + 26, by + 10, 2, HudArt.WHITE);
        HudArt.text(g, label, cx + 26 + vw + 8, by + 13, TXT, HudArt.DIM);

        int y = by + size + 6;
        if (timeLeft > 0) {
            String time = String.format("%d:%02d", (int) timeLeft / 60, (int) timeLeft % 60);
            int tw = 20 + HudArt.width("Time " + time, TXT, HudArt.WHITE);
            panel(g, W - HUD_MARGIN - tw, y, tw, 24);
            HudArt.text(g, "Time " + time, W - HUD_MARGIN - tw + 10, y + 7, TXT,
                    timeLeft <= 30 ? HudArt.RED_TEXT : HudArt.GOLD_TEXT);
            y += 30;
        }
        if (hasScanner()) {                                   // Litter Picker's Scanner Lite
            String status = scanTimeLeft > 0 ? "Scanning " + (int) Math.ceil(scanTimeLeft) + "s"
                    : scannerUsed ? "Scanner used" : "Scanner [F]";
            int sw = 20 + HudArt.width(status, TXT, HudArt.WHITE);
            panel(g, W - HUD_MARGIN - sw, y, sw, 24);
            HudArt.text(g, status, W - HUD_MARGIN - sw + 10, y + 7, TXT,
                    scannerUsed && scanTimeLeft <= 0 ? HudArt.DIM : HudArt.TEAL_TEXT);
        }
    }

    /** Top-centre: the cleanliness meter, then the beach wave banner or a boss bar. Returns the bottom. */
    private int drawTopCenter(Graphics2D g, int W) {
        int w = 250, x = (W - w) / 2, y = HUD_MARGIN;
        int pct = level.cleanliness();
        panel(g, x, y, w, CHIP_H);
        HudArt.leaf(g, x + 10, y + 10, 2);
        HudArt.text(g, "Clean", x + 30, y + 11, TXT, HudArt.WHITE);
        String pctText = pct + "%";
        int pw = HudArt.width(pctText, TXT, HudArt.WHITE);
        HudArt.text(g, pctText, x + w - 10 - pw, y + 11, TXT, HudArt.WHITE);
        int barX = x + 30 + HudArt.width("Clean", TXT, HudArt.WHITE) + 10, barW = x + w - 18 - pw - barX, barY = y + 10;
        g.setColor(new Color(10, 16, 26));
        g.fillRect(barX, barY, barW, 11);
        int fill = (barW - 4) * pct / 100;
        if (fill > 0) {
            g.setColor(pct < 50 ? new Color(214, 140, 50) : new Color(64, 170, 100));
            g.fillRect(barX + 2, barY + 2, fill, 7);
            g.setColor(pct < 50 ? new Color(250, 196, 100) : new Color(150, 230, 160));
            g.fillRect(barX + 2, barY + 2, fill, 2);
        }
        int bottom = y + CHIP_H;

        if (bossStarted && !bossDefeated) {
            int seconds = (int) Math.ceil(bossTimeLeft);
            String wave = "Wave " + Math.max(1, bossWave) + "/" + BOSS_WAVES;
            String rest = bossWaveRemaining + " left  " + (seconds / 60) + ":" + String.format("%02d", seconds % 60);
            int ww = HudArt.width(wave, TXT, HudArt.RED_TEXT), rw = HudArt.width(rest, TXT, HudArt.WHITE);
            int bw = ww + rw + 36, bx = (W - bw) / 2, by = bottom + 6;
            panel(g, bx, by, bw, 24);
            HudArt.text(g, wave, bx + 12, by + 7, TXT, HudArt.RED_TEXT);
            HudArt.text(g, rest, bx + 24 + ww, by + 7, TXT, seconds <= 30 ? HudArt.RED_TEXT : HudArt.WHITE);
            bottom = by + 24;
        } else if (trashBoss != null && !trashBoss.isGone()) {
            drawTrashBossBar(g, W, bottom + 6);
            bottom += 6 + 50;
        }
        return bottom;
    }

    /** The level's name on a cream plate (like the main menu tagline) for the first seconds. */
    private int drawLevelBanner(Graphics2D g, int W, int y) {
        String name = level.getName();
        int w = HudArt.width(name, TXT, HudArt.TAGLINE) + 50, x = (W - w) / 2;
        float fade = (float) Math.min(1, (LEVEL_BANNER_SECONDS - stageTime) / 0.6);
        Graphics2D b = (Graphics2D) g.create();
        b.setComposite(AlphaComposite.SrcOver.derive(Math.max(0, fade)));
        HudArt.creamPlate(b, x, y, w, 24);
        HudArt.leaf(b, x + 10, y + 6, 2);
        HudArt.text(b, name, x + 32, y + 7, TXT, HudArt.TAGLINE);
        b.dispose();
        return y + 24;
    }

    /** Bottom-centre: the bag slots, with the potion pouch to its left. Returns the bag's top. */
    private int drawHotbar(Graphics2D g, int W, int H) {
        int cap = player.getCapacity(), slot = 38, gap = 5, pad = 8;
        int w = Math.max(200, pad * 2 + cap * slot + (cap - 1) * gap), h = slot + 28;
        int x = (W - w) / 2, y = H - HUD_MARGIN - h;
        panel(g, x, y, w, h);
        String count = "Bag " + player.getBag().size() + "/" + cap;
        HudArt.text(g, count, x + pad, y + 8, TXT, HudArt.TEAL_TEXT);
        if (!player.bagEmpty()) {
            String name = player.getBag().get(player.getSelectedIndex()).getItemName();
            int room = w - pad * 2 - HudArt.width(count, TXT, HudArt.TEAL_TEXT) - 12;
            while (name.length() > 3 && HudArt.width(name, TXT, HudArt.LIGHT) > room) name = name.substring(0, name.length() - 1);
            HudArt.text(g, name, x + w - pad - HudArt.width(name, TXT, HudArt.LIGHT), y + 8, TXT, HudArt.LIGHT);
        }
        for (int i = 0; i < cap; i++) {
            int sx = x + (w - (cap * slot + (cap - 1) * gap)) / 2 + i * (slot + gap), sy = y + 22;
            Garbage item = i < player.getBag().size() ? player.getBag().get(i) : null;
            boolean sel = item != null && i == player.getSelectedIndex();
            g.setColor(new Color(10, 16, 26));
            g.fillRect(sx, sy, slot, slot);
            if (item != null) {
                Color type = new Color(item.getType().getColor());
                g.setColor(new Color(type.getRed(), type.getGreen(), type.getBlue(), 70));
                g.fillRect(sx + 2, sy + 2, slot - 4, slot - 4);
                item.drawIcon(g, sx + 8, sy + 6, slot - 16);
                g.setColor(type);
                g.fillRect(sx + 2, sy + slot - 6, slot - 4, 4);
            }
            g.setStroke(new BasicStroke(2));
            g.setColor(sel ? HudArt.TEAL : HudArt.BORDER);
            g.drawRect(sx, sy, slot, slot);
            g.setStroke(new BasicStroke(1));
            if (sel) {
                g.setColor(HudArt.TEAL);
                g.fillRect(sx + slot / 2 - 4, sy - 4, 8, 3);
            }
        }
        if (shopManager != null) {                            // potion pouch [H]
            int max = shopManager.getMaxPotions(), pw = 34 + max * 15, px = x - 8 - pw, py = y + h - 34;
            panel(g, px, py, pw, 34);
            HudArt.key(g, "H", px + 8, py + 9, TXT);
            for (int i = 0; i < max; i++) HudArt.flask(g, px + 30 + i * 15, py + 11, 2, i < potionCount());
        }
        return y;
    }

    /** Bottom-left: stunned / slowed timer. */
    private void drawStatusPill(Graphics2D g, int H) {
        if (player.getSlowTimer() <= 0 && !player.isStunned()) return;
        String text = player.isStunned() ? "Stunned!" : "Slowed " + (int) Math.ceil(player.getSlowTimer()) + "s";
        int w = 22 + HudArt.width(text, TXT, HudArt.RED_TEXT), y = H - HUD_MARGIN - 26;
        panel(g, HUD_MARGIN, y, w, 26);
        HudArt.text(g, text, HUD_MARGIN + 11, y + 8, TXT, player.isStunned() ? HudArt.GOLD_TEXT : HudArt.RED_TEXT);
    }

    /** Bottom-right: a short control reminder (can be turned off in Settings). */
    private void drawKeyHints(Graphics2D g, int W, int H) {
        if (!PauseMenu.keyHints()) return;
        String[][] keys = player.isOnBoat()
                ? new String[][] {{"W/S", "Go"}, {"A/D", "Steer"}, {"Esc", "Menu"}}
                : new String[][] {{"E", "Use"}, {"Q", "Switch"}, {"Esc", "Menu"}};
        int x = W - HUD_MARGIN, y = H - HUD_MARGIN - 18;
        for (int i = keys.length - 1; i >= 0; i--) {
            x -= HudArt.width(keys[i][1], TXT, HudArt.LIGHT);
            HudArt.text(g, keys[i][1], x, y + 4, TXT, HudArt.LIGHT);
            x -= HudArt.keyWidth(keys[i][0], TXT) + 5;
            HudArt.key(g, keys[i][0], x, y, TXT);
            x -= 12;
        }
    }

    /** "[E] Pick up plastic water bottle" above the bag. */
    private void drawActionHint(Graphics2D g, String key, String text, int W, int y) {
        int tw = HudArt.width(text, TXT, HudArt.WHITE), w = tw + 44, x = (W - w) / 2;
        panel(g, x, y, w, 26);
        HudArt.key(g, key, x + 7, y + 5, TXT);
        HudArt.text(g, text, x + 32, y + 8, TXT, HudArt.WHITE);
    }

    /** Messages, centred under the top chips and wrapped to fit. */
    private void drawToast(Graphics2D g, String text, int W, int y) {
        int maxW = 560;
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            String tryLine = line.length() == 0 ? word : line + " " + word;
            if (HudArt.width(tryLine, TXT, HudArt.WHITE) > maxW - 28 && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else line = new StringBuilder(tryLine);
        }
        if (line.length() > 0) lines.add(line.toString());
        int w = 0;
        for (String l : lines) w = Math.max(w, HudArt.width(l, TXT, HudArt.WHITE));
        w += 28;
        int h = 14 + lines.size() * 15, x = (W - w) / 2;
        Graphics2D t = (Graphics2D) g.create();
        t.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, toastTime / 0.3)));
        panel(t, x, y, w, h);
        for (int i = 0; i < lines.size(); i++)
            HudArt.text(t, lines.get(i), x + (w - HudArt.width(lines.get(i), TXT, HudArt.WHITE)) / 2, y + 9 + i * 15, TXT, HudArt.WHITE);
        t.dispose();
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
            title = "THREE WAVES. FIVE MINUTES.";
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
