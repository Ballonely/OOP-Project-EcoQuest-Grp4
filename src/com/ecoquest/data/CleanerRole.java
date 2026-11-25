package com.ecoquest.data;

import java.util.Locale;

/**
 * Starter role picked at registration. Each role grants one permanent +10% perk and one
 * unique skill: Waste Collector +1 bag slot, Litter Picker the Scanner Lite, Volunteer +1 heart.
 */
public enum CleanerRole {
    WASTE_COLLECTOR("Waste Collector", "+1 Bag Slot", "+10% Coins", 1.00, 1.10, 1.00,
            "spritesheet.png", 1, false, 0),
    LITTER_PICKER("Litter Picker", "Scanner Lite [F]", "+10% Speed", 1.10, 1.00, 1.00,
            "spritesheet_litter_picker.png", 0, true, 0),
    VOLUNTEER("Volunteer", "+1 Heart", "+10% Health", 1.00, 1.00, 1.10,
            "spritesheet_volunteer.png", 0, false, 1);

    private final String displayName;
    private final String gear;
    private final String perk;
    private final double speedMultiplier;
    private final double coinMultiplier;
    private final double healthMultiplier;
    private final String spriteSheet;
    private final int bagBonus;
    private final boolean scanner;
    private final int bonusHearts;

    CleanerRole(String displayName, String gear, String perk, double speedMultiplier,
            double coinMultiplier, double healthMultiplier, String spriteSheet,
            int bagBonus, boolean scanner, int bonusHearts) {
        this.spriteSheet = spriteSheet;
        this.bagBonus = bagBonus;
        this.scanner = scanner;
        this.bonusHearts = bonusHearts;
        this.displayName = displayName;
        this.gear = gear;
        this.perk = perk;
        this.speedMultiplier = speedMultiplier;
        this.coinMultiplier = coinMultiplier;
        this.healthMultiplier = healthMultiplier;
    }

    public String getDisplayName() { return displayName; }
    /** Short label of the role's unique skill, shown on the role card. */
    public String getGear() { return gear; }
    /** Extra bag (inventory) slots. */
    public int getBagBonus() { return bagBonus; }
    /** Whether the role carries the once-per-level Scanner Lite. */
    public boolean hasScanner() { return scanner; }
    /** Extra hearts on top of the default five. */
    public int getBonusHearts() { return bonusHearts; }
    public String getPerk() { return perk; }
    public double getSpeedMultiplier() { return speedMultiplier; }
    public double getCoinMultiplier() { return coinMultiplier; }
    /** Effective health multiplier; applied as damage absorption since hearts are whole numbers. */
    public double getHealthMultiplier() { return healthMultiplier; }
    /** Player spritesheet file name under assets/ (64x80, 4 columns x 5 rows of 16px frames). */
    public String getSpriteSheet() { return spriteSheet; }

    /** Coins after the role's coin bonus, applied only to coins earned during a run. */
    public int applyCoinBonus(int coinsEarned) {
        if (coinsEarned <= 0 || coinMultiplier <= 1.0) return coinsEarned;
        return (int) Math.round(coinsEarned * coinMultiplier);
    }

    static CleanerRole parse(String value) {
        if (value == null || value.isEmpty()) return LITTER_PICKER;
        switch (value.toUpperCase(Locale.ROOT)) {
            // Role names saved by the first version of the role picker.
            case "SWEEPER": return LITTER_PICKER;
            case "RANGER": return WASTE_COLLECTOR;
            case "SCOUT": return VOLUNTEER;
            default: return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }
}
