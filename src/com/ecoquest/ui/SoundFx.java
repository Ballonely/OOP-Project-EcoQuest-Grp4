package com.ecoquest.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.prefs.Preferences;

/**
 * Tiny 8-bit chiptune synthesizer for menu sound effects. Tones are generated in code
 * (no audio files) and played on a background thread so the UI never blocks.
 * Fails silently when no audio device is available.
 */
public final class SoundFx {
    public enum Sound {
        BLIP, TAB, COIN, FANFARE, ERROR,
        // Level 1 Trash Monster boss
        BOSS_ROAR, BOSS_TOSS, BOSS_SPLAT, BOSS_HIT, BOSS_HEAL, BOSS_ENRAGE, BOSS_SMACK, BOSS_DEFEAT
    }

    private static final float RATE = 22_050f;
    private static final String PREF_KEY = "sfxEnabled";
    // A few voices, so a long roar does not hold back the short hit sounds behind it.
    private static final ExecutorService PLAYER = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "SoundFx");
        t.setDaemon(true);
        return t;
    });
    private static volatile boolean enabled = loadEnabled();

    private SoundFx() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        MusicPlayer.onEnabledChanged(); // background music shares this on/off setting
        try {
            Preferences.userNodeForPackage(SoundFx.class).putBoolean(PREF_KEY, on);
        } catch (RuntimeException ignored) {
            // Preference store unavailable; the setting just won't persist.
        }
    }

    private static boolean loadEnabled() {
        try {
            return Preferences.userNodeForPackage(SoundFx.class).getBoolean(PREF_KEY, true);
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static void play(Sound sound) {
        if (!enabled) return;
        PLAYER.execute(() -> {
            byte[] pcm = render(sound);
            AudioFormat format = new AudioFormat(RATE, 8, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format, 4096);
                line.start();
                line.write(pcm, 0, pcm.length);
                line.drain();
            } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                // No usable audio output: stay silent.
            }
        });
    }

    private enum Wave { SQUARE, TRIANGLE, SAW, NOISE }

    private static byte[] render(Sound sound) {
        switch (sound) {
            case BLIP:
                return concat(tone(Wave.SQUARE, 523.25, 523.25, 0.04, 0.10, 0.01),
                        tone(Wave.SQUARE, 659.25, 659.25, 0.04, 0.05, 0.01));
            case TAB:
                return tone(Wave.TRIANGLE, 300, 600, 0.06, 0.20, 0.01);
            case COIN:
                return concat(tone(Wave.SQUARE, 987.77, 987.77, 0.08, 0.12, 0.08),
                        tone(Wave.SQUARE, 1318.51, 1318.51, 0.22, 0.08, 0.001));
            case FANFARE: {
                double[] notes = {440, 554.37, 659.25, 880};
                byte[][] parts = new byte[notes.length][];
                for (int i = 0; i < notes.length; i++) {
                    parts[i] = tone(Wave.SQUARE, notes[i], notes[i], i == notes.length - 1 ? 0.18 : 0.07,
                            0.10, 0.01);
                }
                return concat(parts);
            }
            case BOSS_ROAR: // road cracks open, then a low growling roar
                return concat(tone(Wave.NOISE, 0, 0, 0.12, 0.35, 0.05),
                        mix(tone(Wave.SAW, 120, 55, 0.95, 0.18, 0.02),
                                tone(Wave.SQUARE, 62, 40, 0.95, 0.07, 0.01),
                                tone(Wave.NOISE, 0, 0, 0.95, 0.10, 0.005)));
            case BOSS_TOSS: // garbage bag whooshing through the air
                return mix(tone(Wave.TRIANGLE, 260, 820, 0.22, 0.14, 0.02),
                        tone(Wave.NOISE, 0, 0, 0.22, 0.06, 0.01));
            case BOSS_SPLAT: // bag bursts on the road
                return mix(tone(Wave.SQUARE, 130, 40, 0.16, 0.15, 0.02),
                        tone(Wave.NOISE, 0, 0, 0.24, 0.17, 0.005));
            case BOSS_HIT: // the monster takes damage: crunchy grunt
                return concat(tone(Wave.SAW, 240, 120, 0.09, 0.24, 0.06),
                        tone(Wave.SQUARE, 150, 85, 0.10, 0.12, 0.01));
            case BOSS_HEAL: // it soaks up new trash: bubbly gurgle
                return concat(tone(Wave.TRIANGLE, 180, 360, 0.07, 0.16, 0.06),
                        tone(Wave.TRIANGLE, 220, 440, 0.07, 0.16, 0.06),
                        tone(Wave.TRIANGLE, 260, 560, 0.10, 0.16, 0.01));
            case BOSS_ENRAGE: // phase change: rising then falling double roar
                return concat(mix(tone(Wave.SAW, 85, 170, 0.40, 0.20, 0.10),
                                tone(Wave.NOISE, 0, 0, 0.40, 0.12, 0.05)),
                        mix(tone(Wave.SAW, 170, 70, 0.50, 0.20, 0.01),
                                tone(Wave.NOISE, 0, 0, 0.50, 0.12, 0.005)));
            case BOSS_SMACK: // the monster or a bag hits the player
                return mix(tone(Wave.SQUARE, 320, 140, 0.14, 0.16, 0.02),
                        tone(Wave.NOISE, 0, 0, 0.08, 0.14, 0.01));
            case BOSS_DEFEAT: { // long collapsing groan, then a bright victory sparkle
                byte[] groan = mix(tone(Wave.SAW, 200, 38, 1.20, 0.20, 0.01),
                        tone(Wave.NOISE, 0, 0, 1.20, 0.12, 0.004));
                double[] notes = {659.25, 783.99, 1046.50, 1318.51};
                byte[][] parts = new byte[notes.length + 1][];
                parts[0] = groan;
                for (int i = 0; i < notes.length; i++) {
                    parts[i + 1] = tone(Wave.SQUARE, notes[i], notes[i], i == notes.length - 1 ? 0.22 : 0.07,
                            0.10, 0.01);
                }
                return concat(parts);
            }
            case ERROR:
            default:
                return concat(tone(Wave.SAW, 150, 150, 0.08, 0.15, 0.06),
                        tone(Wave.SAW, 110, 110, 0.12, 0.06, 0.01));
        }
    }

    /** One tone with a linear pitch slide and an exponential volume decay. */
    private static byte[] tone(Wave wave, double fromHz, double toHz, double seconds,
            double startGain, double endGain) {
        int n = (int) (seconds * RATE);
        byte[] out = new byte[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double k = n <= 1 ? 0 : i / (double) (n - 1);
            double hz = fromHz + (toHz - fromHz) * k;
            phase = (phase + hz / RATE) % 1.0;
            double sample;
            switch (wave) {
                case SQUARE: sample = phase < 0.5 ? 1 : -1; break;
                case TRIANGLE: sample = 4 * Math.abs(phase - 0.5) - 1; break;
                case NOISE: sample = ThreadLocalRandom.current().nextDouble() * 2 - 1; break;
                default: sample = 2 * phase - 1; break;
            }
            double gain = startGain * Math.pow(endGain / startGain, k);
            out[i] = (byte) Math.round(sample * gain * 127 * 2.5);
        }
        return out;
    }

    /** Layers sounds on top of each other (they all start together), clamped to 8-bit range. */
    private static byte[] mix(byte[]... layers) {
        int len = 0;
        for (byte[] l : layers) len = Math.max(len, l.length);
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            int sum = 0;
            for (byte[] l : layers) if (i < l.length) sum += l[i];
            out[i] = (byte) Math.max(-127, Math.min(127, sum));
        }
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }
}
