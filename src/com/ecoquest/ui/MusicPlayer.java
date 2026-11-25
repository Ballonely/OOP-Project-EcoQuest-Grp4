package com.ecoquest.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looping background music for the menu screens and every level. Like {@link SoundFx}, every track is
 * synthesized in code (no audio files): each one is rendered once into a seamless loop and
 * streamed on a daemon thread. Switching tracks fades the old one out and the new one in.
 * Music follows the same on/off setting as the sound effects and fails silently when no
 * audio device is available.
 */
public final class MusicPlayer {
    /** One loop per screen group, one per level, and a shared boss theme. */
    public enum Track { LOGIN, HOME, SHOP, RESIDENTIAL, PARK, MARKET, BEACH, CITY, BOSS, TRASH_MONSTER }

    private static final float RATE = 22_050f;
    private static final int CHUNK = 1024;               // samples written per loop pass
    private static final double FADE_SECONDS = 0.6;
    private static final double TARGET_RMS = 1800;       // average loudness, kept under the SFX
    private static final double PEAK_LIMIT = 0.45;       // fraction of full scale

    private static final Object LOCK = new Object();
    private static final Map<Track, short[]> CACHE = new ConcurrentHashMap<>();
    private static volatile Track requested;
    private static Thread worker;

    private MusicPlayer() {
    }

    /** Requests a track; {@code null} fades the music out. Cheap to call every frame. */
    public static void play(Track track) {
        if (requested == track) return;
        synchronized (LOCK) {
            requested = track;
            ensureWorker();
            LOCK.notifyAll();
        }
    }

    public static void stop() {
        play(null);
    }

    /** Wakes the player after the shared sound setting changes. */
    static void onEnabledChanged() {
        synchronized (LOCK) {
            LOCK.notifyAll();
        }
    }

    private static void ensureWorker() {
        if (worker != null) return;
        worker = new Thread(MusicPlayer::run, "MusicPlayer");
        worker.setDaemon(true);
        worker.start();
        // Render the remaining loops in the background so later track switches start instantly.
        Thread warm = new Thread(() -> {
            for (Track t : Track.values()) trackData(t);
        }, "MusicPrerender");
        warm.setDaemon(true);
        warm.setPriority(Thread.MIN_PRIORITY);
        warm.start();
    }

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------
    private static void run() {
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        double fadeStep = 1.0 / (FADE_SECONDS * RATE);
        byte[] bytes = new byte[CHUNK * 2];
        while (true) {
            // Idle until there is something to play.
            synchronized (LOCK) {
                while (requested == null || !SoundFx.isEnabled()) {
                    try {
                        LOCK.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }

            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format, (int) (RATE * 0.2) * 2);
                line.start();
                Track current = null;
                short[] loop = null;
                int pos = 0;
                double gain = 0;

                while (true) {
                    Track want = SoundFx.isEnabled() ? requested : null;
                    if (current == null && want != null) {
                        current = want;
                        loop = trackData(current);
                        pos = 0;
                    }
                    if (current == null) break; // faded out with nothing queued: close the line

                    double target = want == current ? 1 : 0;
                    for (int i = 0; i < CHUNK; i++) {
                        if (gain < target) gain = Math.min(target, gain + fadeStep);
                        else if (gain > target) gain = Math.max(target, gain - fadeStep);
                        int s = (int) Math.round(loop[pos] * gain);
                        bytes[i * 2] = (byte) s;
                        bytes[i * 2 + 1] = (byte) (s >> 8);
                        pos = (pos + 1) % loop.length;
                    }
                    line.write(bytes, 0, bytes.length);

                    if (gain == 0 && want != current) current = null; // switch or stop
                }
                line.drain();
            } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                // No usable audio output: wait for a different request instead of spinning.
                synchronized (LOCK) {
                    Track failed = requested;
                    while (requested == failed) {
                        try {
                            LOCK.wait();
                        } catch (InterruptedException ie) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private static short[] trackData(Track track) {
        return CACHE.computeIfAbsent(track, MusicPlayer::render);
    }

    // ------------------------------------------------------------------
    // Composition
    // ------------------------------------------------------------------
    private static short[] render(Track track) {
        switch (track) {
            case LOGIN: return renderLogin();
            case HOME: return renderHome();
            case RESIDENTIAL: return renderResidential();
            case PARK: return renderPark();
            case MARKET: return renderMarket();
            case BEACH: return renderBeach();
            case CITY: return renderCity();
            case BOSS: return renderBoss();
            case TRASH_MONSTER: return renderTrashMonster();
            case SHOP:
            default: return renderShop();
        }
    }

    /** Level 1: sunny suburban morning, cheerful pop with a skippy off-beat strum. */
    private static short[] renderResidential() {
        Score s = new Score(112, 8, 0);
        // D  G  Bm  A  D  G  A  D
        int[] roots = {38, 43, 47, 45, 38, 43, 45, 38};
        int[][] triads = {{62, 66, 69}, {62, 67, 71}, {62, 66, 71}, {61, 64, 69},
                {62, 66, 69}, {62, 67, 71}, {61, 64, 69}, {62, 66, 69}};
        for (int bar = 0; bar < 8; bar++) {
            double b = bar * 4;
            int r = roots[bar];
            s.note(b, 1, r, Voice.BASS, 0.22);
            s.note(b + 1, 1, r + 12, Voice.BASS, 0.14);
            s.note(b + 2, 1, r, Voice.BASS, 0.20);
            s.note(b + 3, 1, r + 7, Voice.BASS, 0.15);
            for (int n : triads[bar]) {
                for (int k = 0; k < 4; k++) s.note(b + k + 0.5, 0.4, n, Voice.PLUCK, 0.04);
            }
        }
        s.drums("x.......x.......", "..x...x...x...x.", 0.30, 0.05);
        double[][] melody = {
                {0, 0.5, 78}, {0.5, 0.5, 81}, {1, 1, 83}, {2, 1, 81}, {3, 1, 78},
                {4, 1.5, 79}, {5.5, 0.5, 78}, {6, 1, 76}, {7, 1, 74},
                {8, 0.5, 74}, {8.5, 0.5, 78}, {9, 1, 83}, {10, 1.5, 81}, {11.5, 0.5, 78},
                {12, 2, 76}, {14, 1, 73}, {15, 1, 76},
                {16, 0.5, 78}, {16.5, 0.5, 81}, {17, 1, 86}, {18, 1, 83}, {19, 1, 81},
                {20, 1, 83}, {21, 1, 79}, {22, 1, 83}, {23, 1, 86},
                {24, 1.5, 85}, {25.5, 0.5, 83}, {26, 1, 81}, {27, 1, 76},
                {28, 2, 78}, {30, 2, 74},
        };
        s.melody(melody, Voice.LEAD, 0.14);
        return s.finish();
    }

    /** Level 2: pastoral park stroll, airy flute over a soft pad with little bird chirps. */
    private static short[] renderPark() {
        Score s = new Score(96, 8, 0);
        // A  F#m  D  E  A  C#m  D  E
        int[] roots = {45, 42, 38, 40, 45, 37, 38, 40};
        int[][] pads = {{61, 64, 69}, {61, 66, 69}, {62, 66, 69}, {59, 64, 68},
                {61, 64, 69}, {61, 64, 68}, {62, 66, 69}, {59, 64, 68}};
        for (int bar = 0; bar < 8; bar++) {
            double b = bar * 4;
            int r = roots[bar];
            int[] p = pads[bar];
            s.note(b, 2, r, Voice.BASS, 0.18);
            s.note(b + 2, 2, r + 7, Voice.BASS, 0.13);
            for (int n : p) s.note(b, 4, n, Voice.PAD, 0.05);
            int[] arp = {p[0], p[1], p[2], p[1]};
            for (int k = 0; k < 4; k++) s.note(b + k, 1, arp[k] + 12, Voice.PLUCK, 0.035);
        }
        s.drums("................", "..x...x...x...x.", 0, 0.025);
        double[][] melody = {
                {0, 1.5, 76}, {1.5, 0.5, 78}, {2, 2, 81},
                {4, 1, 81}, {5, 1, 78}, {6, 2, 73},
                {8, 1.5, 74}, {9.5, 0.5, 76}, {10, 1, 78}, {11, 1, 81},
                {12, 3, 80}, {15, 1, 76},
                {16, 1.5, 81}, {17.5, 0.5, 83}, {18, 2, 85},
                {20, 1, 83}, {21, 1, 80}, {22, 2, 76},
                {24, 1, 78}, {25, 1, 81}, {26, 1, 86}, {27, 1, 85},
                {28, 2, 83}, {30, 2, 80},
        };
        s.melody(melody, Voice.FLUTE, 0.14);
        double[][] birds = {
                {7.5, 0.25, 93}, {7.75, 0.25, 96}, {14.5, 0.25, 96}, {14.75, 0.25, 93},
                {23.5, 0.25, 93}, {23.75, 0.25, 96}, {31, 0.25, 96}, {31.25, 0.25, 98},
        };
        s.melody(birds, Voice.BELL, 0.045);
        return s.finish();
    }

    /** Level 3: festive town market, bouncy calypso bass, marimba strums and an accordion tune. */
    private static short[] renderMarket() {
        Score s = new Score(126, 8, 0);
        // C  F  G  C  Am  F  G  C
        int[] roots = {36, 41, 43, 36, 45, 41, 43, 36};
        int[][] chords = {{64, 67, 72}, {65, 69, 72}, {62, 67, 71}, {64, 67, 72},
                {64, 69, 72}, {65, 69, 72}, {62, 67, 71}, {64, 67, 72}};
        for (int bar = 0; bar < 8; bar++) {
            double b = bar * 4;
            int r = roots[bar];
            s.note(b, 1.5, r, Voice.BASS, 0.22);
            s.note(b + 1.5, 1.5, r + 7, Voice.BASS, 0.17);
            s.note(b + 3, 1, r + 12, Voice.BASS, 0.15);
            for (int n : chords[bar]) {
                s.note(b + 0.5, 0.4, n, Voice.MARIMBA, 0.05);
                s.note(b + 1.5, 0.4, n, Voice.MARIMBA, 0.04);
                s.note(b + 2, 0.4, n, Voice.MARIMBA, 0.04);
                s.note(b + 3.5, 0.4, n, Voice.MARIMBA, 0.05);
            }
        }
        s.drums("x.....x...x.....", "x.x.x.x.x.x.x.x.", 0.26, 0.03);
        double[][] melody = {
                {0, 0.5, 76}, {0.5, 0.5, 79}, {1, 0.5, 81}, {1.5, 1, 79}, {2.5, 0.5, 76}, {3, 1, 72},
                {4, 0.5, 77}, {4.5, 0.5, 81}, {5, 0.5, 84}, {5.5, 1, 81}, {6.5, 0.5, 77}, {7, 1, 72},
                {8, 0.5, 74}, {8.5, 0.5, 79}, {9, 0.5, 83}, {9.5, 1, 81}, {10.5, 0.5, 79}, {11, 1, 77},
                {12, 1.5, 76}, {13.5, 0.5, 74}, {14, 2, 72},
                {16, 0.5, 81}, {16.5, 0.5, 84}, {17, 0.5, 81}, {17.5, 1, 76}, {18.5, 0.5, 72}, {19, 1, 76},
                {20, 0.5, 77}, {20.5, 0.5, 81}, {21, 0.5, 77}, {21.5, 1, 72}, {22.5, 0.5, 69}, {23, 1, 72},
                {24, 0.5, 74}, {24.5, 0.5, 77}, {25, 0.5, 79}, {25.5, 0.5, 83}, {26, 1, 86}, {27, 1, 83},
                {28, 1.5, 84}, {29.5, 0.5, 79}, {30, 2, 72},
        };
        s.melody(melody, Voice.REED, 0.11);
        return s.finish();
    }

    /** Level 4: laid-back tropical beach, reggae skank and a steel-pan melody. */
    private static short[] renderBeach() {
        Score s = new Score(92, 8, 0);
        // E  A  B  A  E  C#m  A  B
        int[] roots = {40, 45, 47, 45, 40, 37, 45, 47};
        int[][] chords = {{64, 68, 71}, {64, 69, 73}, {63, 66, 71}, {64, 69, 73},
                {64, 68, 71}, {64, 68, 73}, {64, 69, 73}, {63, 66, 71}};
        for (int bar = 0; bar < 8; bar++) {
            double b = bar * 4;
            int r = roots[bar];
            s.note(b, 1.5, r, Voice.BASS, 0.24);
            s.note(b + 2, 0.5, r + 7, Voice.BASS, 0.16);
            s.note(b + 2.5, 1, r + 12, Voice.BASS, 0.14);
            s.note(b + 3.5, 0.5, r + 7, Voice.BASS, 0.14);
            for (int n : chords[bar]) {
                s.note(b + 1, 0.35, n, Voice.PLUCK, 0.05);
                s.note(b + 3, 0.35, n, Voice.PLUCK, 0.05);
            }
        }
        s.drums("........x.......", "..x...x...x...x.", 0.28, 0.035);
        double[][] melody = {
                {0, 0.5, 76}, {0.5, 0.5, 80}, {1, 1, 83}, {2, 0.5, 85}, {2.5, 0.5, 83}, {3, 1, 80},
                {4, 1, 81}, {5, 0.5, 85}, {5.5, 0.5, 81}, {6, 2, 76},
                {8, 0.5, 75}, {8.5, 0.5, 78}, {9, 1, 83}, {10, 0.5, 78}, {10.5, 0.5, 75}, {11, 1, 71},
                {12, 1.5, 73}, {13.5, 0.5, 76}, {14, 2, 81},
                {16, 0.5, 83}, {16.5, 0.5, 85}, {17, 1, 88}, {18, 0.5, 85}, {18.5, 0.5, 83}, {19, 1, 80},
                {20, 1, 80}, {21, 1, 76}, {22, 1, 73}, {23, 1, 76},
                {24, 0.5, 81}, {24.5, 0.5, 80}, {25, 0.5, 78}, {25.5, 0.5, 76}, {26, 2, 73},
                {28, 1.5, 75}, {29.5, 0.5, 78}, {30, 2, 83},
        };
        s.melody(melody, Voice.STEEL, 0.15);
        return s.finish();
    }

    /** Level 5: busy city centre, pulsing octave bass, four-on-the-floor and a synth lead. */
    private static short[] renderCity() {
        Score s = new Score(118, 8, 0);
        // Am  F  C  G  Am  F  G  E
        int[] roots = {45, 41, 48, 43, 45, 41, 43, 40};
        int[][] pads = {{60, 64, 69}, {60, 65, 69}, {60, 64, 67}, {59, 62, 67},
                {60, 64, 69}, {60, 65, 69}, {59, 62, 67}, {59, 64, 68}};
        for (int bar = 0; bar < 8; bar++) {
            double b = bar * 4;
            int r = roots[bar];
            for (int e = 0; e < 8; e++) s.note(b + e * 0.5, 0.45, e % 2 == 0 ? r : r + 12, Voice.BASS, 0.15);
            for (int n : pads[bar]) s.note(b, 4, n, Voice.PAD, 0.045);
        }
        s.drums("x...x...x...x...", "..x...x...x...x.", 0.26, 0.045);
        double[][] melody = {
                {0, 0.75, 76}, {0.75, 0.75, 81}, {1.5, 0.5, 84}, {2, 1, 83}, {3, 1, 81},
                {4, 0.75, 77}, {4.75, 0.75, 81}, {5.5, 0.5, 84}, {6, 2, 81},
                {8, 0.75, 79}, {8.75, 0.75, 84}, {9.5, 0.5, 88}, {10, 1, 86}, {11, 1, 84},
                {12, 2, 83}, {14, 1, 79}, {15, 1, 74},
                {16, 0.5, 81}, {16.5, 0.5, 81}, {17, 0.5, 84}, {17.5, 0.5, 81}, {18, 1, 88}, {19, 1, 86},
                {20, 1, 84}, {21, 1, 81}, {22, 2, 77},
                {24, 0.5, 79}, {24.5, 0.5, 83}, {25, 0.5, 86}, {25.5, 0.5, 83}, {26, 1, 79}, {27, 1, 74},
                {28, 2, 80}, {30, 1, 76}, {31, 1, 83},
        };
        s.melody(melody, Voice.SQUARE, 0.10);
        return s.finish();
    }

    /** Boss fights: tense, driving D minor with a pounding bass and ominous lead. */
    private static short[] renderBoss() {
        Score s = new Score(144, 8, 0);
        // Dm  Bb  C  A  Dm  Bb  Gm  A
        int[] roots = {50, 46, 48, 45, 50, 46, 43, 45};
        int[][] chords = {{62, 65, 69}, {62, 65, 70}, {60, 64, 67}, {61, 64, 69},
                {62, 65, 69}, {62, 65, 70}, {62, 67, 70}, {61, 64, 69}};
        for (int bar = 0; bar < 8; bar++) {
            double b = bar * 4;
            int r = roots[bar];
            for (int e = 0; e < 8; e++) s.note(b + e * 0.5, 0.4, e == 3 || e == 7 ? r + 12 : r, Voice.BASS, 0.17);
            int[] c = chords[bar];
            for (int n : c) s.note(b, 4, n, Voice.PAD, 0.04);
            int[] arp = {c[0] + 12, c[1] + 12, c[2] + 12, c[1] + 12};
            for (int e = 0; e < 8; e++) s.note(b + e * 0.5, 0.4, arp[e % 4], Voice.PLUCK, 0.035);
        }
        s.drums("x.x...x.x.x...x.", "x.x.x.x.x.x.x.x.", 0.30, 0.04);
        double[][] melody = {
                {0, 1.5, 74}, {1.5, 0.5, 77}, {2, 2, 81},
                {4, 1.5, 82}, {5.5, 0.5, 81}, {6, 2, 77},
                {8, 1.5, 79}, {9.5, 0.5, 76}, {10, 1, 72}, {11, 1, 76},
                {12, 3, 73}, {15, 1, 76},
                {16, 1, 81}, {17, 0.5, 86}, {17.5, 0.5, 84}, {18, 2, 81},
                {20, 1, 82}, {21, 1, 81}, {22, 1, 77}, {23, 1, 74},
                {24, 1.5, 79}, {25.5, 0.5, 77}, {26, 2, 74},
                {28, 2, 73}, {30, 1, 76}, {31, 1, 81},
        };
        s.melody(melody, Voice.SQUARE, 0.10);
        return s.finish();
    }

    /** Calm, dreamy C major lullaby: soft pad, music-box arpeggio, sparse melody. */
    private static short[] renderLogin() {
        Score s = new Score(80, 8, 0);
        int[][] chords = {{48, 60, 64, 67}, {45, 57, 60, 64}, {41, 53, 57, 60}, {43, 55, 59, 62}};
        for (int bar = 0; bar < 8; bar++) {
            int[] c = chords[bar % 4];
            double b = bar * 4;
            s.note(b, 2, c[0], Voice.BASS, 0.20);
            s.note(b + 2, 2, c[0] + 7, Voice.BASS, 0.14);
            for (int k = 1; k < 4; k++) s.note(b, 4, c[k], Voice.PAD, 0.07);
            int[] arp = {c[1] + 12, c[2] + 12, c[3] + 12, c[2] + 12};
            for (int e = 0; e < 8; e++) s.note(b + e * 0.5, 0.5, arp[e % 4], Voice.BELL, 0.07);
        }
        double[][] melody = {
                {16, 2, 76}, {18, 1, 74}, {19, 1, 72},
                {20, 3, 69}, {23, 1, 72},
                {24, 2, 77}, {26, 1, 76}, {27, 1, 74},
                {28, 2, 74}, {30, 2, 71},
        };
        s.melody(melody, Voice.BELL, 0.16);
        return s.finish();
    }

    /** Bright, bouncy G major theme for the home menu and level select. */
    private static short[] renderHome() {
        Score s = new Score(108, 8, 0);
        // G  D  Em  C  G  D  C  D
        int[] roots = {43, 38, 40, 36, 43, 38, 36, 38};
        int[][] triads = {{67, 71, 74}, {66, 69, 74}, {67, 71, 76}, {64, 67, 72},
                {67, 71, 74}, {66, 69, 74}, {64, 67, 72}, {66, 69, 74}};
        for (int bar = 0; bar < 8; bar++) {
            double b = bar * 4;
            int r = roots[bar];
            s.note(b, 1, r, Voice.BASS, 0.22);
            s.note(b + 1.5, 0.5, r, Voice.BASS, 0.16);
            s.note(b + 2, 1, r + 7, Voice.BASS, 0.18);
            s.note(b + 3, 1, r + 12, Voice.BASS, 0.14);
            for (int n : triads[bar]) {
                s.note(b + 1, 0.5, n, Voice.PLUCK, 0.06);
                s.note(b + 3, 0.5, n, Voice.PLUCK, 0.06);
            }
        }
        double[][] melody = {
                {0, 1, 74}, {1, 0.5, 71}, {1.5, 0.5, 74}, {2, 1, 79}, {3, 1, 78},
                {4, 1.5, 78}, {5.5, 0.5, 76}, {6, 2, 74},
                {8, 1, 71}, {9, 1, 74}, {10, 1, 76}, {11, 1, 79},
                {12, 3, 76}, {15, 1, 74},
                {16, 1, 74}, {17, 0.5, 79}, {17.5, 0.5, 78}, {18, 1, 79}, {19, 1, 83},
                {20, 2, 81}, {22, 1, 78}, {23, 1, 74},
                {24, 1, 76}, {25, 1, 72}, {26, 1, 76}, {27, 1, 79},
                {28, 2, 78}, {30, 1, 81}, {31, 1, 74},
        };
        s.melody(melody, Voice.LEAD, 0.15);
        return s.finish();
    }

    /** Playful swung F major shop tune: walking bass, marimba chords, music-box melody. */
    private static short[] renderShop() {
        Score s = new Score(116, 8, 0.12);
        // Fmaj7  Dm7  Gm7  C7  (twice)
        int[] roots = {41, 38, 43, 36};
        int[] thirds = {4, 3, 3, 4};
        int[][] chords = {{65, 69, 72, 76}, {65, 69, 72, 74}, {65, 67, 70, 74}, {64, 67, 70, 72}};
        for (int bar = 0; bar < 8; bar++) {
            double b = bar * 4;
            int r = roots[bar % 4];
            int next = roots[(bar + 1) % 4];
            int[] walk = {r, r + thirds[bar % 4], r + 7, next - 1};
            for (int q = 0; q < 4; q++) s.note(b + q, 1, walk[q], Voice.BASS, 0.20);
            for (int n : chords[bar % 4]) {
                s.note(b, 0.5, n, Voice.MARIMBA, 0.06);
                s.note(b + 2.5, 0.5, n, Voice.MARIMBA, 0.05);
            }
        }
        double[][] melody = {
                {0, 0.5, 72}, {0.5, 0.5, 76}, {1, 0.5, 77}, {1.5, 0.5, 81}, {2, 1.5, 79}, {3.5, 0.5, 77},
                {4, 1, 74}, {5, 0.5, 77}, {5.5, 0.5, 81}, {6, 1.5, 81}, {7.5, 0.5, 79},
                {8, 0.5, 79}, {8.5, 0.5, 77}, {9, 0.5, 74}, {9.5, 0.5, 70}, {10, 1, 74}, {11, 1, 77},
                {12, 1.5, 76}, {13.5, 0.5, 79}, {14, 1, 82}, {15, 1, 79},
                {16, 0.5, 84}, {16.5, 0.5, 81}, {17, 0.5, 77}, {17.5, 0.5, 81}, {18, 2, 79},
                {20, 1, 77}, {21, 0.5, 74}, {21.5, 0.5, 77}, {22, 2, 81},
                {24, 0.5, 79}, {24.5, 0.5, 82}, {25, 0.5, 79}, {25.5, 0.5, 77}, {26, 1, 74}, {27, 1, 70},
                {28, 1, 76}, {29, 1, 72}, {30, 2, 70},
        };
        s.melody(melody, Voice.BELL, 0.15);
        return s.finish();
    }

    /**
     * Level 1 Trash Monster: a grimy, stomping E minor riff with a creeping chromatic bass,
     * snare backbeat, trash-can clanks and a gritty bluesy lead.
     */
    private static short[] renderTrashMonster() {
        Score s = new Score(132, 8, 0);
        // Em  Em  C  B  Em  Em  C  B
        int[] roots = {40, 40, 36, 35, 40, 40, 36, 35};
        for (int bar = 0; bar < 8; bar++) {
            double b = bar * 4;
            int r = roots[bar];
            int[] riff = {r, r, r + 12, r, r + 3, r, r + 1, r}; // the +1 is the creepy half-step
            for (int e = 0; e < 8; e++) s.note(b + e * 0.5, 0.42, riff[e], Voice.BASS, 0.17);
            s.note(b, 1.5, r + 12, Voice.GRIT, 0.035);       // power-chord stab
            s.note(b, 1.5, r + 19, Voice.GRIT, 0.03);
            if (bar % 2 == 1) {
                s.note(b + 3.5, 0.25, 84, Voice.CLANK, 0.09);
                s.note(b + 3.75, 0.25, 79, Voice.CLANK, 0.07);
            } else {
                s.note(b + 1.5, 0.25, 84, Voice.CLANK, 0.06);
            }
        }
        s.drums("x..x..x.x..x..x.", "....x.......x...", "x.x.x.x.x.x.x.x.", 0.30, 0.12, 0.03);
        double[][] melody = {
                {0, 0.5, 64}, {0.5, 0.5, 67}, {1, 0.5, 69}, {1.5, 0.5, 70}, {2, 1, 71}, {3, 0.5, 70}, {3.5, 0.5, 69},
                {4, 1.5, 67}, {5.5, 0.5, 64}, {6, 1, 62}, {7, 1, 64},
                {8, 0.5, 67}, {8.5, 0.5, 72}, {9, 1, 71}, {10, 1, 67}, {11, 1, 64},
                {12, 2, 63}, {14, 1, 66}, {15, 1, 71},
                {16, 0.5, 76}, {16.5, 0.5, 74}, {17, 0.5, 71}, {17.5, 0.5, 70}, {18, 1, 69}, {19, 1, 67},
                {20, 0.5, 64}, {20.5, 0.5, 67}, {21, 0.5, 70}, {21.5, 0.5, 71}, {22, 2, 76},
                {24, 1, 79}, {25, 1, 76}, {26, 1, 72}, {27, 1, 71},
                {28, 1, 75}, {29, 1, 71}, {30, 1, 66}, {31, 1, 63},
        };
        s.melody(melody, Voice.GRIT, 0.10);
        return s.finish();
    }

    // ------------------------------------------------------------------
    // Synthesis
    // ------------------------------------------------------------------
    private enum Voice {
        PAD, PLUCK, BELL, BASS, LEAD, MARIMBA, FLUTE, REED, STEEL, SQUARE, GRIT, KICK, SNARE, HAT, CLANK
    }

    /** A fixed-length loop. Notes that ring past the end wrap to the start, so loops are seamless. */
    private static final class Score {
        private final double samplesPerBeat;
        private final double swing;
        private final int bars;
        private final float[] mix;
        private final Random noise = new Random(7); // fixed seed: every render sounds the same

        Score(double bpm, int bars, double swing) {
            this.samplesPerBeat = RATE * 60.0 / bpm;
            this.swing = swing;
            this.bars = bars;
            this.mix = new float[(int) Math.round(samplesPerBeat * bars * 4)];
        }

        void melody(double[][] notes, Voice voice, double gain) {
            for (double[] n : notes) note(n[0], n[1], (int) n[2], voice, gain);
        }

        /** One-bar drum pattern repeated over the loop: 16 sixteenth steps, 'x' = hit. */
        void drums(String kick, String hat, double kickGain, double hatGain) {
            drums(kick, "................", hat, kickGain, 0, hatGain);
        }

        void drums(String kick, String snare, String hat, double kickGain, double snareGain, double hatGain) {
            for (int bar = 0; bar < bars; bar++) {
                for (int step = 0; step < 16; step++) {
                    double beat = bar * 4 + step * 0.25;
                    if (kickGain > 0 && kick.charAt(step) == 'x') note(beat, 0.25, 0, Voice.KICK, kickGain);
                    if (snareGain > 0 && snare.charAt(step) == 'x') note(beat, 0.25, 0, Voice.SNARE, snareGain);
                    if (hatGain > 0 && hat.charAt(step) == 'x') note(beat, 0.1, 0, Voice.HAT, hatGain);
                }
            }
        }

        void note(double beat, double beats, int midi, Voice voice, double gain) {
            double frac = beat - Math.floor(beat);
            if (Math.abs(frac - 0.5) < 1e-6) beat += swing; // swung offbeat eighths
            int start = (int) Math.round(beat * samplesPerBeat);
            double hz = 440 * Math.pow(2, (midi - 69) / 12.0);
            double held = beats * samplesPerBeat / RATE;      // seconds the note is held
            double tail = switch (voice) {
                case BELL, MARIMBA, STEEL, CLANK -> 1.0;
                case KICK, SNARE, HAT -> 0.3;
                default -> 0.25;
            };
            int total = (int) ((held + tail) * RATE);
            for (int i = 0; i < total; i++) {
                double t = i / RATE;
                double env = envelope(voice, t, held);
                if (env < 1e-4 && t > held) break;
                double p = 2 * Math.PI * hz * t;
                double sample;
                switch (voice) {
                    case HAT:
                        sample = noise.nextDouble() * 2 - 1;
                        break;
                    case SNARE: // noise burst over a short drum-head tone
                        sample = 0.7 * (noise.nextDouble() * 2 - 1)
                                + 0.5 * Math.sin(2 * Math.PI * 190 * t) * Math.exp(-t / 0.04);
                        break;
                    case CLANK: // dented trash-can lid: inharmonic metal partials plus a scrape
                        sample = 0.5 * Math.sin(p) + 0.35 * Math.sin(2.76 * p) + 0.2 * Math.sin(5.4 * p)
                                + 0.4 * (noise.nextDouble() * 2 - 1) * Math.exp(-t / 0.015);
                        break;
                    default:
                        sample = wave(voice, p, t);
                }
                int idx = (start + i) % mix.length;
                mix[idx] += (float) (sample * env * gain);
            }
        }

        private static double envelope(Voice voice, double t, double held) {
            double release = t > held ? Math.exp(-(t - held) / 0.06) : 1;
            switch (voice) {
                case PAD:
                    return Math.min(1, t / 0.15) * (t > held ? Math.exp(-(t - held) / 0.25) : 1);
                case PLUCK:
                    return Math.min(1, t / 0.005) * Math.exp(-t / 0.22) * release;
                case BELL:
                    return Math.min(1, t / 0.003) * Math.exp(-t / 0.45);
                case MARIMBA:
                    return Math.min(1, t / 0.002) * Math.exp(-t / 0.18);
                case BASS:
                    return Math.min(1, t / 0.008) * Math.exp(-t / 0.5) * release;
                case FLUTE:
                    return Math.min(1, t / 0.06) * 0.85 * (t > held ? Math.exp(-(t - held) / 0.12) : 1);
                case REED:
                    return Math.min(1, t / 0.015) * 0.8 * release;
                case STEEL:
                    return Math.min(1, t / 0.003) * Math.exp(-t / 0.35);
                case SQUARE:
                    return Math.min(1, t / 0.01) * (0.55 + 0.45 * Math.exp(-t / 0.2)) * release;
                case KICK:
                    return Math.min(1, t / 0.001) * Math.exp(-t / 0.12);
                case HAT:
                    return Math.min(1, t / 0.001) * Math.exp(-t / 0.025);
                case SNARE:
                    return Math.min(1, t / 0.001) * Math.exp(-t / 0.08);
                case CLANK:
                    return Math.min(1, t / 0.001) * Math.exp(-t / 0.12);
                case GRIT:
                    return Math.min(1, t / 0.006) * (0.6 + 0.4 * Math.exp(-t / 0.15)) * release;
                case LEAD:
                default:
                    return Math.min(1, t / 0.02) * (0.6 + 0.4 * Math.exp(-t / 0.3)) * release;
            }
        }

        private static double wave(Voice voice, double p, double t) {
            switch (voice) {
                case PAD:
                    return 0.7 * Math.sin(p) + 0.3 * Math.sin(2 * p);
                case BELL:
                    return Math.sin(p) + 0.4 * Math.sin(2 * p) * Math.exp(-t / 0.08);
                case MARIMBA:
                    return Math.sin(p) + 0.25 * Math.sin(4 * p) * Math.exp(-t / 0.03);
                case BASS:
                    return 0.6 * triangle(p) + 0.4 * Math.sin(p);
                case LEAD:
                    return 0.8 * triangle(p) + 0.2 * Math.sin(2 * p) * (1 + 0.3 * Math.sin(2 * Math.PI * 5 * t));
                case FLUTE: {
                    double v = p + 0.8 * Math.sin(2 * Math.PI * 5.5 * t) * Math.min(1, t / 0.3); // delayed vibrato
                    return 0.9 * Math.sin(v) + 0.1 * Math.sin(2 * v);
                }
                case REED: {
                    double v = p + 0.5 * Math.sin(2 * Math.PI * 6 * t);
                    return 0.55 * pulse(v, 0.3) + 0.45 * triangle(v);
                }
                case STEEL:
                    return Math.sin(p) + 0.45 * Math.sin(2 * p) * Math.exp(-t / 0.2)
                            + 0.2 * Math.sin(3 * p) * Math.exp(-t / 0.08);
                case SQUARE:
                    return 0.6 * pulse(p, 0.5) + 0.4 * triangle(p);
                case GRIT: // two slightly detuned narrow pulses: buzzy and grimy
                    return 0.5 * pulse(p, 0.25) + 0.5 * pulse(p * 1.007, 0.25);
                case KICK: // sine with a fast downward pitch sweep
                    return Math.sin(2 * Math.PI * (50 * t + 4 * (1 - Math.exp(-t / 0.04))));
                case PLUCK:
                default:
                    return triangle(p);
            }
        }

        private static double pulse(double p, double duty) {
            double x = (p / (2 * Math.PI)) % 1.0;
            if (x < 0) x += 1;
            return x < duty ? 1 : -1;
        }

        private static double triangle(double p) {
            double x = (p / (2 * Math.PI)) % 1.0;
            if (x < 0) x += 1;
            return 4 * Math.abs(x - 0.5) - 1;
        }

        /** Softens the mix with a gentle low-pass, then normalizes it to a quiet 16-bit loop. */
        short[] finish() {
            double a = 1 - Math.exp(-2 * Math.PI * 3200 / RATE);
            double y = 0;
            float[] out = new float[mix.length];
            for (int pass = 0; pass < 2; pass++) { // second pass makes the filter state loop cleanly
                for (int i = 0; i < mix.length; i++) {
                    y += a * (mix[i] - y);
                    out[i] = (float) y;
                }
            }
            // Match every track to the same average loudness, capped so peaks never clip.
            double peak = 1e-9;
            double sumSq = 0;
            for (float v : out) {
                peak = Math.max(peak, Math.abs(v));
                sumSq += v * (double) v;
            }
            double rms = Math.max(1e-9, Math.sqrt(sumSq / out.length));
            double scale = Math.min(TARGET_RMS / rms, PEAK_LIMIT * 32767 / peak);
            short[] pcm = new short[out.length];
            for (int i = 0; i < out.length; i++) pcm[i] = (short) Math.round(out[i] * scale);
            return pcm;
        }
    }
}
