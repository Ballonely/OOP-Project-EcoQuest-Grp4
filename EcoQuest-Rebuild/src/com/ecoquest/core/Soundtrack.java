package com.ecoquest.core;

import java.io.File;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

public final class Soundtrack {
    private Clip clip;
    private int lastVolume = -1;
    private int pausedFrame = -1;

    public synchronized void update(boolean beachStage, boolean enabled, int volume, boolean gameMuted, boolean paused) {
        if (!beachStage || !enabled) { stop(); return; }
        if (clip == null || !clip.isOpen()) {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new File("assets/music/NaturalMystic.wav"))) {
                clip = AudioSystem.getClip();
                clip.open(in);
                lastVolume = -1;
            } catch (Exception ignored) { stop(); return; }
        }
        int effectiveVolume = gameMuted ? 0 : volume;
        setVolume(effectiveVolume);
        if (paused || effectiveVolume == 0) {
            if (clip.isRunning()) { pausedFrame = clip.getFramePosition(); clip.stop(); }
        } else if (!clip.isRunning()) {
            if (pausedFrame >= 0) clip.setFramePosition(Math.min(pausedFrame, clip.getFrameLength() - 1));
            pausedFrame = -1;
            if (effectiveVolume > 0) clip.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    private void setVolume(int volume) {
        if (clip == null || volume == lastVolume) return;
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float fraction = Math.max(0.00001f, Math.min(1f, volume / 100f));
            float db = volume == 0 ? gain.getMinimum() : (float) (20 * Math.log10(fraction));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
        } catch (IllegalArgumentException ignored) { }
        lastVolume = volume;
    }

    public synchronized void stop() {
        if (clip != null) { clip.stop(); clip.close(); clip = null; }
        pausedFrame = -1;
        lastVolume = -1;
    }

    public synchronized void restart() { stop(); }
}
