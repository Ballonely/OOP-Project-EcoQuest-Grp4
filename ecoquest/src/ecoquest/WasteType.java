package ecoquest;

import java.awt.Color;

/** The four bins used in every level. */
public enum WasteType {
    BIO("BIO", "Biodegradable", new Color(0x3fa34d)),
    REC("REC", "Recyclable", new Color(0x3b8fd9)),
    TRASH("TRASH", "Non-recyclable", new Color(0x4a4a52)),
    HAZ("HAZ", "Hazardous", new Color(0xd63a3a));

    public final String label;
    public final String longName;
    public final Color color;

    WasteType(String label, String longName, Color color) {
        this.label = label;
        this.longName = longName;
        this.color = color;
    }

    public static WasteType parse(String s) {
        for (WasteType t : values()) {
            if (t.name().equalsIgnoreCase(s.trim())) return t;
        }
        throw new IllegalArgumentException("Unknown waste type: " + s);
    }
}
