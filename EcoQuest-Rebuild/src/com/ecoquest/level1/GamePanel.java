package com.ecoquest.level1;

import com.ecoquest.core.InputHandler;
import com.ecoquest.core.ProgressStore;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.ecoquest.level1.Config.*;

/** Level 1 world, interaction rules and HUD, driven by the main game loop. */
public class GamePanel {
    private static final double BIN_RANGE = 17;
    private static final double HUD_BASE_WIDTH = 1250.0;
    private static final double HUD_BASE_HEIGHT = 780.0;
    private static final double BOSS_WARNING_PHASE = 1.55;
    private static final double BOSS_ZOOM_IN_PHASE = 1.4;
    private static final double BOSS_SPLASH_PHASE = 2.35;
    private static final double BOSS_ZOOM_OUT_PHASE = 1.2;
    private static final double BOSS_CINEMATIC_DURATION = 7.45;
    private final BufferedImage frame = new BufferedImage(WORLD_W, WORLD_H, BufferedImage.TYPE_INT_ARGB);

    private Level level;
    private Player player;
    private final String stageName;
    private final ProgressStore profile;
    private final CoastalMorphling morphling = new CoastalMorphling();
    private Garbage nearGarbage;
    private TrashBin nearBin;

    private boolean cleared;
    private boolean completedBeforeRun;
    private boolean gameOver;
    private String toast = "";
    private double toastTime;
    private double quipCooldown, bubbleTime;
    private String bubbleText = "";
    private final List<Integer> quipOrder = new ArrayList<>();
    private int quipIndex;
    private final Random quipRandom = new Random();
    private static final String[] EMPTY_BAG_QUIPS = {
        "What am I doing??", "I have nothing in my hands.",
        "Man, I'm wasting my time staring into this thing.",
        "I have no trash in me.", "I mean, unless I gotta clean the bin, there's nothing else to do here."
    };
    private boolean bossStarted, bossFailed, bossDefeated, showBossWarning, bossVictorySequence;
    private boolean marketTimedOut, parkTimedOut;
    private int bossWave, bossWaveRemaining, bossAttackPattern;
    private int marketMistakes;
    private double bossWarningTime, bossTimeLeft, bossVictoryTime, bossAttackCooldown, bossAttackAnimation;
    private final List<double[]> bossAttackTargets = new ArrayList<>();
    private double marketTimeLeft;
    private double parkTimeLeft;
    private double stageTime;
    private double displayedStageProgress;

    public GamePanel() { this("Residential", new ProgressStore()); }

    public GamePanel(String stageName, ProgressStore profile) {
        this.stageName = stageName;
        this.profile = profile;
        startLevel();
    }

    public boolean isFinished() { return cleared || gameOver; }
    public boolean didWin() { return cleared; }
    public int getScore() { return player.getScore(); }
    public int getMoney() { return player.getMoney(); }
    public int getStageProgress() {
        if ("Coastal Beach".equals(stageName) && bossStarted && !bossDefeated) return 99;
        return level.getProgressPercent();
    }
    public boolean isBossWarningActive() { return bossStarted && showBossWarning && !bossFailed; }
    public boolean isBossBattleActive() { return bossStarted && !showBossWarning && !bossDefeated && !bossFailed && !gameOver; }
    public boolean wasCompletedBeforeRun() { return completedBeforeRun; }
    public String getFailureMessage() {
        if (bossFailed) return "The five beach-cleaning waves were not cleared in time.";
        if (marketTimedOut) return "Time ran out. The market still needs cleaning.";
        if (parkTimedOut) return "Time ran out. The park still needs cleaning.";
        return "You ran out of hearts!";
    }

    private void startLevel() {
        level = StageFactory.create(stageName).load();
        int savedProgress = profile.getProgress(stageName);
        completedBeforeRun = savedProgress >= 100;
        level.restoreProgress(savedProgress);
        player = new Player(level.getSpawnX(), level.getSpawnY());
        player.equipCleaner(profile.hasSunGrabber(), profile.hasVacuumPack());
        if (level instanceof MarketStage market) {
            int sharedCapacity = profile.hasVacuumPack() ? 10 : profile.hasSunGrabber() ? 7 : market.carryCapacity();
            player.setBagCapacity(sharedCapacity);
        }
        cleared = completedBeforeRun;
        gameOver = false;
        quipCooldown = bubbleTime = 0;
        bubbleText = "";
        bossStarted = bossFailed = bossDefeated = showBossWarning = bossVictorySequence = false;
        marketTimedOut = parkTimedOut = false;
        marketMistakes = 0;
        bossWave = bossWaveRemaining = bossAttackPattern = 0;
        bossWarningTime = bossTimeLeft = bossVictoryTime = 0;
        bossAttackCooldown = bossAttackAnimation = 0;
        bossAttackTargets.clear();
        marketTimeLeft = level instanceof MarketStage market ? market.timeLimitSeconds() : 0;
        parkTimeLeft = level instanceof Level2Park park ? park.timeLimit() : 0;
        stageTime = 0;
        displayedStageProgress = getStageProgress();
        showToast("Clean " + stageName + "! Sort litter into the matching bins.", 4);
    }

    // ------------------------------------------------------------ update
    public void updateFrame(double dt, InputHandler input) {
        stageTime += dt;
        toastTime = Math.max(0, toastTime - dt);
        quipCooldown = Math.max(0, quipCooldown - dt);
        bubbleTime = Math.max(0, bubbleTime - dt);
        if (cleared || gameOver) return;
        animateStageProgress(dt);
        if (level instanceof MarketStage market && marketTimeLeft > 0) {
            marketTimeLeft = Math.max(0, marketTimeLeft - dt);
            if (marketTimeLeft == 0) {
                marketTimedOut = true;
                gameOver = true;
                showToast("Time is up. The market is still full of litter.", 4);
                return;
            }
            market.updateSpawning(dt, player.getBag().size());
            market.updateKing(dt);
        }
        if (level instanceof Level2Park park && parkTimeLeft > 0) {
            parkTimeLeft = Math.max(0, parkTimeLeft - dt);
            if (parkTimeLeft == 0 && !park.isBeastDefeated()) {
                parkTimedOut = true;
                gameOver = true;
                showToast("Time is up. The park is still full of litter.", 4);
                return;
            }
        }
        level.updateTemporaryProps(dt);
        if (level.applyTemporaryEffects(player)) showToast("Sewage splash! Stunned and slowed.", 1.8);
        if (showBossWarning) {
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

        if (level instanceof MarketStage market && market.updateCarts(dt, player)) {
            player.applySlow(0.5);
            Garbage dropped = player.removeLastGarbage();
            if (dropped != null) {
                level.dropGarbage(dropped, player.centerX() + 20, player.centerY());
                showToast("A hand cart bumped you! You dropped " + dropped.getItemName() + ".", 2);
            } else showToast("A hand cart bumped you!", 1.5);
        }

        nearGarbage = level.nearestGarbage(player.centerX(), player.centerY(), player.getPickupRange());
        nearBin = level.nearestBin(player.centerX(), player.centerY(), BIN_RANGE);
        for (Garbage g : level.getGarbage()) g.setHighlighted(g == nearGarbage);
        for (TrashBin b : level.getBins()) b.setHighlighted(b == nearBin && !player.bagEmpty());

        if (input.isKeyJustPressed(KeyEvent.VK_Q) || input.isKeyJustPressed(KeyEvent.VK_TAB)) {
            player.cycleSelected();
        }

        if (input.isKeyJustPressed(KeyEvent.VK_E) || input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            if (nearBin != null && player.bagEmpty()) showEmptyBagQuip();
            else if (nearBin != null) throwInBin(nearBin);
            else if (nearGarbage != null) pickUp(nearGarbage);
        }

        if (level instanceof Level2Park park) {
            park.updatePark(dt, player);
            if (park.consumeBeastReward()) {
                player.addMoney(Level2Park.BEAST_REWARD);
                profile.addCoins(Level2Park.BEAST_REWARD);
                showToast("Litter Beast defeated! +\u20B1" + Level2Park.BEAST_REWARD, 3);
            }
            if (player.isDead()) { gameOver = true; return; }
        }

        if ("Coastal Beach".equals(stageName)) updateCoastalBoss(dt);

        boolean bossGatePassed = !(level instanceof Level2Park park) || park.isBeastDefeated();
        if ("Coastal Beach".equals(stageName)) bossGatePassed = bossDefeated && !bossVictorySequence;
        boolean marketGoalReached = level instanceof MarketStage market && market.isGoalReached();
        if (marketGoalReached || (level.garbageLeft() == 0 && player.bagEmpty() && bossGatePassed)) {
            cleared = true;
            displayedStageProgress = 100;
            int finishReward = 100;
            if (level instanceof MarketStage) {
                finishReward += 150;
                if (marketMistakes <= 2) finishReward += 50;
            }
            player.addMoney(finishReward);
            profile.addCoins(finishReward);
            profile.setProgress(stageName, 100);
            showToast(stageName + " cleaned! +\u20B1" + finishReward, 6);
        }
    }

    private void pickUp(Garbage g) {
        if (player.bagFull()) { showToast("Bag is full! Empty it in a bin.", 2); return; }
        g.setHighlighted(false);
        player.pickUp(g);
        int pickupCoins = g.isCoinCollected() ? 0 : g.getCoinValue();
        if (!g.isCoinCollected()) {
            profile.addCoins(pickupCoins);
            player.addMoney(pickupCoins);
            g.markCoinCollected();
        }
        level.removeGarbage(g);
        showToast("Picked up " + g.getItemName() + " +\u20B1" + pickupCoins, 1.5);
    }

    private void throwInBin(TrashBin bin) {
        Garbage carried = player.removeSelectedGarbage();
        GarbageType item = carried.getType();
        if (item == bin.getType()) {
            boolean market = level instanceof MarketStage;
            int reward = market ? 5 : item == GarbageType.HAZARDOUS ? 10 : 5;
            player.addMoney(reward);
            profile.addCoins(reward);
            player.addScore(10);
            if (carried.isBossWaveLitter() && "Coastal Beach".equals(stageName)) {
                bossWaveRemaining = Math.max(0, bossWaveRemaining - 1);
            } else if (level instanceof MarketStage marketStage) marketStage.registerCorrectSort();
            else level.registerCorrectSort();
            if (market && item == GarbageType.HAZARDOUS) {
                player.addMoney(10);
                profile.addCoins(10);
                reward += 10;
            }
            if (market && level.getSortedCorrect() % 10 == 0 && getStageProgress() < 100) {
                player.addMoney(25);
                profile.addCoins(25);
                reward += 25;
            }
            showToast((market ? carried.getItemName() + " sorted in the " + item.getLabel() + " bin! " : "Correct! ")
                    + "+\u20B1" + reward + "  +10 pts", 2);
        } else {
            player.addScore(-5);
            player.hurt(1);
            if (level instanceof MarketStage market) {
                marketMistakes++;
                market.kingReact();
                market.penalizeWrongSort(2);
                String tip = carried.getItemTip();
                String lesson = tip.isBlank() ? "" : " " + tip;
                showToast(carried.getItemName() + " goes in the " + carried.getType().getLabel()
                        + " bin. " + lesson + " -1 HP; the Waste King adds litter.", 2.8);
            } else {
                level.dropGarbage(carried, player.centerX(), player.centerY());
                showToast("Wrong bin! " + item.getLabel() + " goes elsewhere.  -1 HP", 2);
            }
            if (player.isDead()) {
                gameOver = true;
                showToast("Player health depleted!", 3);
            }
        }
    }

    private void showToast(String t, double secs) { toast = t; toastTime = secs; }

    private void showEmptyBagQuip() {
        if (quipCooldown > 0) return;
        if (quipIndex >= quipOrder.size()) {
            quipOrder.clear();
            for (int i = 0; i < EMPTY_BAG_QUIPS.length; i++) quipOrder.add(i);
            Collections.shuffle(quipOrder, quipRandom);
            quipIndex = 0;
        }
        bubbleText = EMPTY_BAG_QUIPS[quipOrder.get(quipIndex++)];
        bubbleTime = 2.6;
        quipCooldown = 0.75;
    }

    private void updateCoastalBoss(double dt) {
        if (!bossStarted && level.garbageLeft() == 0 && player.bagEmpty()
                && level.getProgressPercent() >= 100) {
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
                bossTimeLeft = 300;
                bossAttackCooldown = 5.5;
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
        bossAttackCooldown -= dt;
        if (bossAttackCooldown <= 0) {
            Level4Beach beach = (Level4Beach) level;
            bossAttackTargets.clear();
            bossAttackTargets.addAll(beach.getBossAttackPattern(bossAttackPattern++));
            for (double[] target : bossAttackTargets) level.addTemporaryProp(new WaterImpact(target[0], target[1]));
            bossAttackAnimation = WaterImpact.WARNING_SECONDS;
            bossAttackCooldown = 5.5;
        }
        if (bossWave > 0 && bossWaveRemaining == 0 && level.garbageLeft() == 0 && player.bagEmpty()) {
            if (bossWave >= 5) {
                bossDefeated = true;
                bossVictorySequence = true;
                bossVictoryTime = 2.4;
                showToast("The Sewage Morphling is defeated!", 3);
            }
            else startBeachWave();
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
        showToast("Wave " + bossWave + " of 5! " + added + " pieces washed ashore.", 3);
    }

    private boolean isDown(InputHandler input, int a, int b) {
        return input.isKeyDown(a) || input.isKeyDown(b);
    }

    // ------------------------------------------------------------ render
    public void renderFrame(Graphics2D g, int width, int height) {
        Graphics2D fg = frame.createGraphics();
        level.draw(fg, player);
        if (bossStarted && "Coastal Beach".equals(stageName)) {
            if (showBossWarning) morphling.drawEntrance(fg, stageTime, bossSplashProgress());
            else if (bossVictorySequence) morphling.drawDefeated(fg, stageTime, 1 - bossVictoryTime / 2.4);
            else if (!bossDefeated) morphling.draw(fg, stageTime, bossAttackAnimation, bossAttackTargets);
        }
        fg.dispose();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (showBossWarning) {
            drawBossCinematicWorld(g, width, height);
            drawBossOverlay(g, width, height);
            return;
        }
        g.drawImage(frame, 0, 0, width, height, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawHud(g, width, height);
        drawSpeechBubble(g, width, height);
    }

    private void panel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(15, 20, 25, 190));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(new Color(255, 255, 255, 60));
        g.drawRoundRect(x, y, w, h, 12, 12);
    }

    private void drawHud(Graphics2D g, int W, int H) {
        double scale = hudScale(W, H);
        int margin = Math.max(8, (int) Math.round(12 * scale));
        int panelY = margin;
        int stagePanelWidth = Math.min((int) Math.round(430 * scale), W - margin * 2);
        int statsPanelWidth = Math.min((int) Math.round(360 * scale),
                Math.max(180, W - stagePanelWidth - margin * 3));
        int panelHeight = Math.max(62, (int) Math.round(94 * scale));
        int stageX = W - margin - stagePanelWidth;

        panel(g, margin, panelY, statsPanelWidth, panelHeight);
        g.setFont(new Font(Font.DIALOG, Font.BOLD, Math.max(10, (int) Math.round(15 * scale))));
        g.setColor(Color.WHITE);
        g.drawString("ECO QUEST", margin + (int) Math.round(12 * scale), panelY + (int) Math.round(22 * scale));

        g.setFont(new Font(Font.DIALOG, Font.BOLD, Math.max(8, (int) Math.round(12 * scale))));
        FontMetrics statsMetrics = g.getFontMetrics();
        String[] stats = {"Cash \u20B1" + player.getMoney(), "Wallet \u20B1" + profile.getCoins(), "Score " + player.getScore()};
        int pad = (int) Math.round(12 * scale);
        int columnWidth = Math.max(1, (statsPanelWidth - pad * 2) / stats.length);
        for (int i = 0; i < stats.length; i++) {
            int textX = margin + pad + i * columnWidth
                    + Math.max(0, (columnWidth - statsMetrics.stringWidth(stats[i])) / 2);
            g.setColor(i < 2 ? new Color(255, 214, 90) : Color.WHITE);
            g.drawString(stats[i], textX, panelY + (int) Math.round(46 * scale));
        }

        g.setFont(new Font(Font.DIALOG, Font.BOLD, Math.max(13, (int) Math.round(20 * scale))));
        for (int i = 0; i < player.getMaxHealth(); i++) {
            g.setColor(i < player.getHealth() ? new Color(230, 60, 70) : new Color(90, 90, 95));
            g.drawString("\u2665", margin + pad + i * (int) Math.round(24 * scale),
                    panelY + (int) Math.round(76 * scale));
        }

        panel(g, stageX, panelY, stagePanelWidth, panelHeight);
        int innerX = stageX + pad;
        int innerWidth = stagePanelWidth - pad * 2;
        g.setFont(new Font(Font.DIALOG, Font.BOLD, Math.max(8, (int) Math.round(14 * scale))));
        g.setColor(Color.WHITE);
        String area = level.getName().replaceFirst("^Level\\s+\\d+:\\s*", "");
        String stageTitle = fitText(g, area + " Stage Completion Bar", innerWidth);
        g.drawString(stageTitle, innerX, panelY + (int) Math.round(21 * scale));

        int barY = panelY + (int) Math.round(31 * scale);
        int barHeight = Math.max(14, (int) Math.round(24 * scale));
        g.setColor(new Color(238, 230, 201));
        g.fillRoundRect(innerX, barY, innerWidth, barHeight, 8, 8);
        int shownProgress = Math.max(0, Math.min(100, (int) Math.round(displayedStageProgress)));
        int fillWidth = (int) Math.round(innerWidth * Math.max(0, Math.min(100, displayedStageProgress)) / 100.0);
        if (fillWidth > 0) {
            g.setColor(progressColor(shownProgress));
            g.fillRoundRect(innerX, barY, fillWidth, barHeight, 8, 8);
        }
        g.setColor(new Color(12, 16, 18));
        g.drawRoundRect(innerX, barY, innerWidth, barHeight, 8, 8);
        String percent = shownProgress + "%";
        g.setFont(new Font(Font.DIALOG, Font.BOLD, Math.max(10, (int) Math.round(15 * scale))));
        FontMetrics percentMetrics = g.getFontMetrics();
        g.setColor(Color.BLACK);
        g.drawString(percent, innerX + (innerWidth - percentMetrics.stringWidth(percent)) / 2,
                barY + (barHeight + percentMetrics.getAscent() - percentMetrics.getDescent()) / 2);

        g.setFont(new Font(Font.DIALOG, Font.BOLD, Math.max(8, (int) Math.round(12 * scale))));
        g.setColor(new Color(255, 250, 229));
        String trashCounter = bossStarted && !bossDefeated
                ? "Wave Trash Left: " + bossWaveRemaining
                : "Trash Left: " + (level.garbageLeft() + player.getBag().size());
        g.drawString(trashCounter, innerX, panelY + (int) Math.round(77 * scale));

        if (bossStarted && !bossDefeated) {
            int seconds = (int) Math.ceil(bossTimeLeft);
            int waveWidth = Math.min((int) Math.round(310 * scale), W - margin * 2);
            int waveHeight = Math.max(28, (int) Math.round(38 * scale));
            int waveY = panelY + panelHeight + margin;
            panel(g, (W - waveWidth) / 2, waveY, waveWidth, waveHeight);
            g.setColor(new Color(255, 112, 103));
            String waveText = "WAVE " + Math.max(1, bossWave) + "/5   " + bossWaveRemaining
                    + " LEFT   " + (seconds / 60) + ":" + String.format("%02d", seconds % 60);
            g.setFont(new Font(Font.DIALOG, Font.BOLD, Math.max(10, (int) Math.round(13 * scale))));
            g.drawString(waveText, (W - g.getFontMetrics().stringWidth(waveText)) / 2,
                    waveY + (waveHeight + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
        }
        if (level instanceof Level2Park && parkTimeLeft > 0) {
            String timer = String.format("%d:%02d", (int) parkTimeLeft / 60, (int) parkTimeLeft % 60);
            g.setColor(new Color(255, 226, 122));
            g.drawString("Time " + timer, stageX + stagePanelWidth - pad - g.getFontMetrics().stringWidth("Time " + timer),
                    panelY + (int) Math.round(77 * scale));
        }
        if (level instanceof MarketStage && marketTimeLeft > 0) {
            String timer = String.format("%d:%02d", (int) marketTimeLeft / 60, (int) marketTimeLeft % 60);
            g.setColor(new Color(255, 226, 122));
            g.drawString("Time " + timer, stageX + stagePanelWidth - pad - g.getFontMetrics().stringWidth("Time " + timer),
                    panelY + (int) Math.round(77 * scale));
        }

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
            panel(g, 12, H - 108, 126, 26);
            g.setColor(new Color(255, 130, 105));
            g.drawString("SLOWED " + (int) Math.ceil(player.getSlowTimer()) + "s", 24, H - 90);
        }

        g.setFont(new Font(Font.DIALOG, Font.PLAIN, Math.max(8, (int) Math.round(12 * scale))));
        g.setColor(new Color(255, 255, 255, 220));
        String controls = player.isOnBoat()
                ? "W/S throttle  A/D steer   E interact   Esc settings"
                : "WASD move   E pick / sort   M market   R restart   Esc settings";
        g.drawString(controls, W - g.getFontMetrics().stringWidth(controls) - (int) Math.round(18 * scale),
                H - (int) Math.round(14 * scale));

        String hint = null;
        if (nearBin != null && !player.bagEmpty())
            hint = "E: Throw " + player.getBag().get(player.getSelectedIndex()).getItemName()
                    + " in the " + nearBin.getType().getLabel() + " bin";
        else if (nearGarbage != null) hint = "E: pick up " + nearGarbage.getItemName();
        if (nearBin != null && player.bagEmpty()) hint = "E: check the bin";
        if (hint != null) centered(g, hint, W, H - (int) Math.round(96 * scale), Math.max(10, (int) Math.round(15 * scale)));
        if (toastTime > 0) centered(g, toast, W, (int) Math.round(70 * scale), Math.max(11, (int) Math.round(16 * scale)));
    }

    private double hudScale(int width, int height) {
        return Math.max(0.65, Math.min(1.5, Math.min(width / HUD_BASE_WIDTH, height / HUD_BASE_HEIGHT)));
    }

    private void animateStageProgress(double dt) {
        double target = Math.max(0, Math.min(100, getStageProgress()));
        displayedStageProgress += (target - displayedStageProgress) * Math.min(1.0, dt * 7.0);
        if (Math.abs(target - displayedStageProgress) < 0.05) displayedStageProgress = target;
    }

    private String fitText(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width) return text;
        String shortened = text;
        while (shortened.length() > 4 && g.getFontMetrics().stringWidth(shortened + "...") > width)
            shortened = shortened.substring(0, shortened.length() - 1);
        return shortened.length() <= 4 ? "..." : shortened + "...";
    }

    private void drawSpeechBubble(Graphics2D g, int width, int height) {
        if (bubbleTime <= 0 || bubbleText.isBlank()) return;
        double sx = width / (double) WORLD_W, sy = height / (double) WORLD_H;
        int scale = Math.max(1, (int) Math.round(width / 1024.0));
        int bw = 300 * scale, bh = 58 * scale;
        int cx = (int) (player.centerX() * sx), by = (int) (player.getY() * sy) - bh - 16 * scale;
        int bx = Math.max(8, Math.min(width - bw - 8, cx - bw / 2));
        by = Math.max(8, by);
        g.setColor(new Color(0, 0, 0, 190)); g.fillRoundRect(bx + 3, by + 3, bw, bh, 14, 14);
        g.setColor(new Color(255, 250, 229)); g.fillRoundRect(bx, by, bw, bh, 14, 14);
        g.setColor(new Color(70, 58, 42)); g.drawRoundRect(bx, by, bw, bh, 14, 14);
        g.fillPolygon(new int[]{cx - 8 * scale, cx + 8 * scale, cx}, new int[]{by + bh - 2, by + bh - 2, by + bh + 10 * scale}, 3);
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 12 * scale)); g.setColor(new Color(45, 48, 42));
        String[] lines = wrap(bubbleText, 39);
        FontMetrics fm = g.getFontMetrics();
        int yy = by + 23 * scale;
        for (int i = 0; i < Math.min(2, lines.length); i++) {
            g.drawString(lines[i], bx + (bw - fm.stringWidth(lines[i])) / 2, yy);
            yy += 18 * scale;
        }
    }

    private String[] wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > maxChars) {
                lines.add(line.toString()); line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines.toArray(new String[0]);
    }

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
        double zoom = bossCameraZoom();
        double focusX = morphling.x(stageTime) + 36;
        double focusY = morphling.y(stageTime) + 30;
        int drawWidth = (int) Math.round(width * zoom);
        int drawHeight = (int) Math.round(height * zoom);
        int drawX = (int) Math.round(-focusX / WORLD_W * width * (zoom - 1));
        int drawY = (int) Math.round(-focusY / WORLD_H * height * (zoom - 1));

        double splash = bossSplashProgress();
        double shake = Math.sin(splash * Math.PI) * 7 * Math.sin(stageTime * 48);
        Graphics2D scene = (Graphics2D) g.create();
        scene.translate(shake, -shake * 0.45);
        scene.drawImage(frame, drawX, drawY, drawWidth, drawHeight, null);
        scene.dispose();
    }

    private void drawBossOverlay(Graphics2D g, int width, int height) {
        if (!showBossWarning) return;
        double scale = hudScale(width, height);
        double elapsed = bossCinematicElapsed();
        int barHeight = Math.max(32, (int) Math.round(46 * scale));
        g.setColor(new Color(4, 10, 16, 224));
        g.fillRect(0, 0, width, barHeight);
        g.fillRect(0, height - barHeight, width, barHeight);

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

        g.setColor(new Color(255, 76, 68));
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, Math.max(15, (int) Math.round(24 * scale))));
        int titleWidth = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (width - titleWidth) / 2, barHeight - (int) Math.round(13 * scale));
        g.setColor(new Color(245, 248, 239));
        g.setFont(new Font(Font.DIALOG, Font.BOLD, Math.max(10, (int) Math.round(13 * scale))));
        int detailWidth = g.getFontMetrics().stringWidth(detail);
        g.drawString(detail, (width - detailWidth) / 2,
                height - barHeight + (int) Math.round(28 * scale));
    }

    private Color progressColor(int percent) {
        if (percent == 0) return Color.BLACK;
        float p = Math.max(0, Math.min(100, percent)) / 100.0f;
        return new Color(Color.HSBtoRGB(p * 0.32f, 0.86f, 0.32f + p * 0.68f));
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
