package ecoquest;

/**
 * One entry per level. {@code key} is the file name of that map's trash base:
 * data/trash/&lt;key&gt;.csv
 */
public enum MapId {
    RESIDENTIAL("residential", "Residential Street"),
    PARK("park", "Public Park"),
    MARKET("market", "Market"),
    BEACH("beach", "Beach"),
    CITY_CENTER("city_center", "City Center");

    public final String key;
    public final String title;

    MapId(String key, String title) {
        this.key = key;
        this.title = title;
    }
}
