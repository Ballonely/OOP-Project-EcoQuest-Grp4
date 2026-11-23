package com.ecoquest.core;

import java.io.File;

public final class Soundtrack {
    private enum Track { BEACH, WARNING, BOSS }

    private Mp3Playback playback;
    private Track track;
    private boolean warningCueFinished;

    public synchronized void update(boolean beachStage, boolean enabled, int volume,
                                    boolean gameMuted, boolean paused,
                                    boolean bossWarning, boolean bossBattle) {
        if (!beachStage || !enabled) { stop(); return; }
        if (!bossWarning) warningCueFinished = false;
        Track desired = bossWarning ? Track.WARNING : bossBattle ? Track.BOSS : Track.BEACH;
        if (desired == Track.WARNING && track == Track.WARNING && playback != null && playback.isFinished())
            warningCueFinished = true;
        if (desired == Track.WARNING && warningCueFinished) desired = Track.BOSS;
        if (track != desired || playback == null) {
            if (playback != null) playback.stop();
            track = desired;
            String file = switch (desired) {
                case WARNING -> "BeachWarning.mp3";
                case BOSS -> "BeachBoss.mp3";
                case BEACH -> "NaturalMystic.mp3";
            };
            playback = new Mp3Playback(new File("assets/music/" + file), desired != Track.WARNING);
            playback.setVolume(volume);
            playback.setPaused(paused || gameMuted || volume == 0);
            playback.start();
        } else {
            playback.setVolume(volume);
            playback.setPaused(paused || gameMuted || volume == 0);
        }
    }

    public synchronized void stop() {
        if (playback != null) playback.stop();
        playback = null;
        track = null;
        warningCueFinished = false;
    }

    public synchronized void restart() { stop(); }
}
