package com.ecoquest.level1;

/** The four waste categories from the design document. Each one matches a bin colour. */
public enum GarbageType {
    BIODEGRADABLE("Biodegradable", 0x3F9B3F),
    RECYCLABLE("Recyclable", 0x2F6FD0),
    NON_RECYCLABLE("Non-recyclable", 0x7A7F87),
    HAZARDOUS("Hazardous", 0xD9442F);

    private final String label;
    private final int color;

    GarbageType(String label, int color) { this.label = label; this.color = color; }

    public String getLabel() { return label; }
    public int getColor()    { return color; }
}
