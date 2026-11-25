package com.ecoquest.data;

import com.ecoquest.data.UserDatabase.Account;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Per-account level progress: best cleanliness and stars earned on each of the five levels.
 * Stored next to the account file in ~/.ecoquest/progress.db; guest progress stays in memory.
 *
 * <p>Stars are earned only by clearing a level: 1 star for a clear, 2 when the cleanliness at
 * the end is at least {@value #TWO_STAR_CLEANLINESS}%, 3 when it is at least {@value #THREE_STAR_CLEANLINESS}%.
 */
public final class LevelProgress {
    public static final int LEVELS = 5;
    public static final int MAX_STARS = 3;
    public static final int TWO_STAR_CLEANLINESS = 75;
    public static final int THREE_STAR_CLEANLINESS = 95;
    /** Best value of a level that has never been played. */
    public static final int NOT_PLAYED = -1;

    private final Path file;   // null = never saved (guest)
    private final String key;  // Base64 of the lower-case username
    private final int[] stars = new int[LEVELS];
    private final int[] best = new int[LEVELS];

    private LevelProgress(Path file, String key) {
        this.file = file;
        this.key = key;
        Arrays.fill(best, NOT_PLAYED);
    }

    /** Loads the account's progress; guests (and unreadable files) start empty. */
    public static LevelProgress load(Account account) {
        return load(account, Paths.get(System.getProperty("user.home"), ".ecoquest", "progress.db"));
    }

    public static LevelProgress load(Account account, Path file) {
        if (account == null || account.isGuest()) return new LevelProgress(null, null);
        String key = Base64.getEncoder().encodeToString(
                account.getUsername().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        LevelProgress progress = new LevelProgress(file.toAbsolutePath(), key);
        try {
            if (Files.exists(progress.file)) {
                for (String line : Files.readAllLines(progress.file, StandardCharsets.UTF_8)) {
                    String[] f = line.split("\t", -1);
                    if (f.length != 4 || !f[0].equals(key)) continue;
                    int level = Integer.parseInt(f[1]);
                    if (level < 0 || level >= LEVELS) continue;
                    progress.stars[level] = Math.max(0, Math.min(MAX_STARS, Integer.parseInt(f[2])));
                    progress.best[level] = Math.max(NOT_PLAYED, Math.min(100, Integer.parseInt(f[3])));
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Could not read level progress: " + e.getMessage());
        }
        return progress;
    }

    public static int starsFor(int cleanliness) {
        return cleanliness >= THREE_STAR_CLEANLINESS ? 3 : cleanliness >= TWO_STAR_CLEANLINESS ? 2 : 1;
    }

    public int stars(int level) { return stars[level]; }
    /** Best cleanliness reached on the level, or {@link #NOT_PLAYED}. */
    public int best(int level) { return best[level]; }
    public boolean cleared(int level) { return stars[level] > 0; }

    public int totalStars() {
        int total = 0;
        for (int s : stars) total += s;
        return total;
    }

    /** Records a finished run and saves it (except for guests). */
    public void record(int level, boolean won, int cleanliness) {
        if (level < 0 || level >= LEVELS) return;
        int health = Math.max(0, Math.min(100, cleanliness));
        best[level] = Math.max(best[level], health);
        if (won) stars[level] = Math.max(stars[level], starsFor(health));
        try {
            save();
        } catch (IOException e) {
            System.err.println("Could not save level progress: " + e.getMessage());
        }
    }

    private void save() throws IOException {
        if (file == null) return;
        List<String> lines = new ArrayList<>();
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.startsWith(key + "\t")) lines.add(line);   // keep other accounts
            }
        }
        for (int i = 0; i < LEVELS; i++) {
            if (best[i] != NOT_PLAYED || stars[i] > 0) lines.add(key + "\t" + i + "\t" + stars[i] + "\t" + best[i]);
        }
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "progress-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
