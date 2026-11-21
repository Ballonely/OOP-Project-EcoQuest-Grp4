package com.ecoquest.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ProgressStore {
    private final Properties data = new Properties();
    private final Path file = Path.of("save.properties");

    public ProgressStore() {
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) { data.load(in); }
            catch (IOException ignored) { }
        }
    }

    public synchronized int getCoins() { return integer("coins", 0); }
    public synchronized boolean hasSunGrabber() { return Boolean.parseBoolean(data.getProperty("cleaner.grabber", "false")); }
    public synchronized boolean hasVacuumPack() { return Boolean.parseBoolean(data.getProperty("cleaner.vacuum", "false")); }
    public synchronized boolean isMusicEnabled() { return Boolean.parseBoolean(data.getProperty("music", "true")); }
    public synchronized boolean isGameMuted() { return Boolean.parseBoolean(data.getProperty("game.muted", "false")); }
    public synchronized int getMusicVolume() { return Math.max(0, Math.min(100, integer("music.volume", 75))); }

    public synchronized int getProgress(String stage) { return Math.max(0, Math.min(100, integer("progress." + key(stage), 0))); }

    public synchronized int getAverageProgress() {
        int sum = 0;
        for (String stage : new String[]{"Residential", "Public Park", "Town Market", "Coastal Beach", "City Center"}) sum += getProgress(stage);
        return sum / 5;
    }

    public synchronized void addCoins(int amount) {
        data.setProperty("coins", Integer.toString(Math.max(0, getCoins() + amount)));
        save();
    }

    public synchronized void setProgress(String stage, int percent) {
        String k = "progress." + key(stage);
        int value = Math.max(0, Math.min(100, percent));
        if (integer(k, -1) == value) return;
        data.setProperty(k, Integer.toString(value));
        save();
    }

    public synchronized void resetProgress(String stage) { setProgress(stage, 0); }
    public synchronized void setMusicEnabled(boolean enabled) { data.setProperty("music", Boolean.toString(enabled)); save(); }
    public synchronized void setGameMuted(boolean muted) { data.setProperty("game.muted", Boolean.toString(muted)); save(); }
    public synchronized void setMusicVolume(int volume) {
        int value = Math.max(0, Math.min(100, volume));
        if (getMusicVolume() == value) return;
        data.setProperty("music.volume", Integer.toString(value));
        save();
    }

    public synchronized String buyCleaner(int tier) {
        boolean vacuum = tier == 1;
        if (vacuum ? hasVacuumPack() : hasSunGrabber()) return "Already owned.";
        int cost = vacuum ? CleanerEconomy.vacuumPackCost() : CleanerEconomy.SUN_GRABBER_COST;
        if (getCoins() < cost) return "Need " + (cost - getCoins()) + " more coins.";
        data.setProperty("coins", Integer.toString(getCoins() - cost));
        data.setProperty(vacuum ? "cleaner.vacuum" : "cleaner.grabber", "true");
        save();
        return vacuum ? "Sun Grabber + Vacuum Pack equipped across all stages." : "Sun Grabber equipped across all stages.";
    }

    private int integer(String k, int fallback) {
        try { return Integer.parseInt(data.getProperty(k, Integer.toString(fallback))); }
        catch (NumberFormatException e) { return fallback; }
    }

    private String key(String stage) { return stage.toLowerCase().replace(' ', '_'); }

    private void save() {
        try (OutputStream out = Files.newOutputStream(file)) { data.store(out, "EcoQuest shared progress"); }
        catch (IOException ignored) { }
    }
}
