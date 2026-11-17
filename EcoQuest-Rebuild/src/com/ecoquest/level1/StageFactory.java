package com.ecoquest.level1;

public final class StageFactory {
    private StageFactory() { }
    public static Level create(String name) {
        return switch (name) {
            case "Residential" -> new Level1Residential();
            case "Public Park" -> new Level2Park();
            case "Town Market" -> new MarketStage();
            case "Coastal Beach" -> new Level4Beach();
            case "City Center" -> new Level5CityCenter();
            default -> new Level1Residential();
        };
    }
}
