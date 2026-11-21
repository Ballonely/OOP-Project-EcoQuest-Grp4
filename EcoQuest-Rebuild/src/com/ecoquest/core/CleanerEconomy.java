package com.ecoquest.core;

public final class CleanerEconomy {
    public static final int SUN_GRABBER_COST = 250;
    private CleanerEconomy() { }

    public static int vacuumPackCost() {
        int stageTrash = 36 + 36 + 40;
        double averagePickup = 0.35 * 3 + 0.30 * 2 + 0.25 * (13 / 6.0) + 0.10 * (23 / 6.0);
        double averageSort = 0.90 * 5 + 0.10 * 10;
        return (int) Math.round(stageTrash * (averagePickup + averageSort) + 3 * 100);
    }
}
