package com.ecoquest.level1;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Random;

/**
 * Level 4 litter: the things most often picked up in beach cleanups (cigarette butts, bottles,
 * wrappers, straws, bags, foam, fishing gear, flip-flops...), each with its own 8x8 sprite and a
 * wrong-bin tip. Items marked {@code floats} are the ones that also drift in the sea.
 */
final class BeachGarbage extends Garbage {
    enum Item {
        // Biodegradable: beach picnic leftovers
        COCONUT("coconut husk", GarbageType.BIODEGRADABLE, 5, true,
                "Coconut husks are plant matter - they rot back into the soil."),
        WATERMELON("watermelon rind", GarbageType.BIODEGRADABLE, 4, false,
                "Fruit rinds are food waste and compost naturally."),
        FISH_BONES("fish bones", GarbageType.BIODEGRADABLE, 3, false,
                "Leftover grilled fish is food waste - it breaks down naturally."),
        CORN_COB("corn cob", GarbageType.BIODEGRADABLE, 3, false,
                "A corn cob is plant food waste and composts."),
        // Recyclable
        WATER_BOTTLE("plastic water bottle", GarbageType.RECYCLABLE, 9, true,
                "PET bottles can be recycled into new bottles and clothes."),
        SODA_CAN("soda can", GarbageType.RECYCLABLE, 6, false,
                "Aluminium cans can be recycled again and again."),
        GLASS_BOTTLE("glass bottle", GarbageType.RECYCLABLE, 4, false,
                "Glass is fully recyclable - and broken glass cuts bare feet on the sand."),
        SUNSCREEN_BOTTLE("empty sunscreen bottle", GarbageType.RECYCLABLE, 3, false,
                "An empty lotion bottle is hard plastic that can be recycled."),
        // Non-recyclable: the classic beach litter
        CIGARETTE_BUTT("cigarette butt", GarbageType.NON_RECYCLABLE, 12, false,
                "Filters are plastic, never biodegrade and are the #1 litter on beaches."),
        WRAPPER("snack wrapper", GarbageType.NON_RECYCLABLE, 8, false,
                "Foil-lined wrappers mix plastic and metal, so they can't be recycled."),
        STRAW("plastic straw", GarbageType.NON_RECYCLABLE, 6, false,
                "Straws are too small and light to be recycled - they end up in the sea."),
        PLASTIC_BAG("plastic bag", GarbageType.NON_RECYCLABLE, 6, true,
                "Thin plastic bags jam recycling machines; turtles mistake them for jellyfish."),
        FOAM_CUP("foam cup", GarbageType.NON_RECYCLABLE, 5, true,
                "Styrofoam crumbles into tiny pieces and is not recycled."),
        FLIP_FLOP("lost flip-flop", GarbageType.NON_RECYCLABLE, 3, true,
                "Rubber sandals are mixed materials that can't be recycled."),
        FISHING_NET("tangled fishing net", GarbageType.NON_RECYCLABLE, 3, true,
                "Old nets and fishing line are not recyclable and trap sea animals."),
        // Hazardous
        BATTERY("used battery", GarbageType.HAZARDOUS, 3, false,
                "Batteries leak toxic metals - they need special disposal."),
        LIGHTER("disposable lighter", GarbageType.HAZARDOUS, 3, false,
                "Lighters still hold flammable gas - treat them as hazardous."),
        GLOW_STICK("used glow stick", GarbageType.HAZARDOUS, 2, true,
                "Glow sticks are full of chemicals that harm sea life."),
        AEROSOL("sunscreen spray can", GarbageType.HAZARDOUS, 2, false,
                "Pressurised spray cans can explode - they are hazardous waste.");

        final String label;
        final GarbageType type;
        final int weight;
        final boolean floats;
        final String tip;

        Item(String label, GarbageType type, int weight, boolean floats, String tip) {
            this.label = label;
            this.type = type;
            this.weight = weight;
            this.floats = floats;
            this.tip = tip;
        }
    }

    private final Item item;

    BeachGarbage(Item item, double x, double y) {
        super(item.type, 0, x, y);
        this.item = item;
    }

    /** Weighted random item lying on the sand. */
    static Item pickSand(Random rng) { return pick(rng, false); }

    /** Weighted random item that floats, for litter out on the water. */
    static Item pickFloating(Random rng) { return pick(rng, true); }

    private static Item pick(Random rng, boolean floatingOnly) {
        int total = 0;
        for (Item i : Item.values()) if (!floatingOnly || i.floats) total += i.weight;
        int roll = rng.nextInt(total);
        for (Item i : Item.values()) {
            if (floatingOnly && !i.floats) continue;
            roll -= i.weight;
            if (roll < 0) return i;
        }
        return Item.WATER_BOTTLE;
    }

    @Override public String getItemName() { return item.label; }

    @Override public String getTip() { return item.tip; }

    @Override
    public Garbage copyAt(double newX, double newY) {
        BeachGarbage copy = new BeachGarbage(item, newX, newY);
        if (isBossWaveLitter()) copy.markBossWaveLitter();
        return copy;
    }

    @Override
    public void draw(Graphics2D g) {
        int X = (int) x, Y = (int) y;
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(X + 1, Y + 7, 6, 1);
        drawItem(g, X, Y);
        if (isHighlighted()) {   // pulsing white box = "you can pick this up"
            int a = 150 + (int) (90 * Math.sin(System.currentTimeMillis() / 120.0));
            g.setColor(new Color(255, 255, 255, Math.max(60, Math.min(255, a))));
            g.drawRect(X - 1, Y - 1, SIZE + 1, SIZE + 1);
        }
    }

    /** 8x8 sprites, same style as the street litter. */
    private void drawItem(Graphics2D g, int X, int Y) {
        switch (item) {
            case COCONUT -> {                              // half a husk, white flesh showing
                rect(g, X + 1, Y + 2, 6, 4, 0x7A4E2A);
                rect(g, X + 2, Y + 1, 4, 1, 0x7A4E2A);
                rect(g, X + 2, Y + 6, 4, 1, 0x7A4E2A);
                rect(g, X + 2, Y + 2, 3, 3, 0xF3EBD8);
                rect(g, X + 6, Y + 3, 1, 1, 0x553417);
                rect(g, X + 5, Y + 5, 1, 1, 0x553417);
            }
            case WATERMELON -> {                           // a rind wedge
                rect(g, X + 1, Y + 5, 6, 1, 0x2F7A35);
                rect(g, X + 1, Y + 4, 6, 1, 0xB5D98A);
                rect(g, X + 2, Y + 3, 4, 1, 0xE0505A);
                rect(g, X + 3, Y + 2, 2, 1, 0xE0505A);
                rect(g, X + 3, Y + 3, 1, 1, 0x222222);
            }
            case FISH_BONES -> {                           // head, spine, ribs and tail
                rect(g, X + 1, Y + 4, 5, 1, 0xEDE6CF);
                rect(g, X + 2, Y + 3, 1, 3, 0xEDE6CF);
                rect(g, X + 4, Y + 3, 1, 3, 0xEDE6CF);
                rect(g, X + 6, Y + 3, 2, 3, 0xD8D0B8);
                rect(g, X + 6, Y + 4, 1, 1, 0x333333);
                rect(g, X, Y + 3, 1, 3, 0xEDE6CF);
            }
            case CORN_COB -> {                             // gnawed cob with a bit of husk
                rect(g, X + 2, Y + 1, 3, 5, 0xE8C84A);
                rect(g, X + 3, Y + 2, 1, 1, 0xC9A532);
                rect(g, X + 2, Y + 4, 1, 1, 0xC9A532);
                rect(g, X + 4, Y + 3, 1, 1, 0xF6E7A0);
                rect(g, X + 4, Y + 5, 2, 2, 0x9DB86A);
                rect(g, X + 1, Y + 6, 2, 1, 0x9DB86A);
            }
            case WATER_BOTTLE -> {                         // clear PET bottle, blue cap and label
                rect(g, X + 1, Y + 3, 5, 3, 0xCDEBFA);
                rect(g, X + 1, Y + 4, 5, 1, 0x2F8FD8);
                rect(g, X + 6, Y + 4, 2, 1, 0x2D5FC4);
                rect(g, X + 2, Y + 3, 2, 1, 0xFFFFFF);
            }
            case SODA_CAN -> {                             // crushed green can
                rect(g, X + 2, Y + 2, 4, 5, 0x3FA34D);
                rect(g, X + 2, Y + 2, 4, 1, 0xD5D5D5);
                rect(g, X + 2, Y + 6, 4, 1, 0xD5D5D5);
                rect(g, X + 3, Y + 4, 2, 1, 0xF2F2F2);
                rect(g, X + 4, Y + 3, 1, 1, 0x2B7A36);
            }
            case GLASS_BOTTLE -> {                         // brown glass bottle
                rect(g, X + 1, Y + 3, 5, 3, 0x8A5A24);
                rect(g, X + 6, Y + 4, 2, 1, 0x6E4519);
                rect(g, X + 2, Y + 4, 2, 1, 0xE9DDB0);
                rect(g, X + 2, Y + 3, 1, 1, 0xC79A5E);
            }
            case SUNSCREEN_BOTTLE -> {                     // orange lotion bottle, white flip cap
                rect(g, X + 2, Y + 1, 4, 5, 0xF29B38);
                rect(g, X + 2, Y + 6, 4, 1, 0xFFFFFF);
                rect(g, X + 3, Y + 2, 2, 2, 0xFFE15A);
                rect(g, X + 5, Y + 1, 1, 5, 0xD07C22);
            }
            case CIGARETTE_BUTT -> {
                rect(g, X + 1, Y + 4, 4, 1, 0xF2F0E8);
                rect(g, X + 5, Y + 4, 2, 1, 0xD98A3A);     // filter
                rect(g, X, Y + 4, 1, 1, 0x555555);         // ash
                rect(g, X + 2, Y + 3, 1, 1, 0x9A9A9A);
            }
            case WRAPPER -> {                              // shiny foil snack wrapper, crimped ends
                rect(g, X + 1, Y + 2, 6, 4, 0x7B3FC0);
                rect(g, X, Y + 3, 1, 2, 0x5E2C99);
                rect(g, X + 7, Y + 3, 1, 2, 0x5E2C99);
                rect(g, X + 2, Y + 3, 2, 1, 0xC9A8F0);
                rect(g, X + 4, Y + 4, 2, 1, 0xF2D24A);
            }
            case STRAW -> {                                // striped straw lying diagonally
                for (int i = 0; i < 6; i++)
                    rect(g, X + 1 + i, Y + 6 - i, 1, 1, i % 2 == 0 ? 0xF2F2F2 : 0xE04848);
                rect(g, X + 6, Y, 1, 1, 0xF2F2F2);
            }
            case PLASTIC_BAG -> {                          // crumpled white bag with handles
                rect(g, X + 1, Y + 2, 6, 4, 0xF4F6F8);
                rect(g, X + 1, Y + 1, 1, 1, 0xDDE3E8);
                rect(g, X + 6, Y + 1, 1, 1, 0xDDE3E8);
                rect(g, X + 3, Y + 3, 2, 1, 0xC8D0D8);
                rect(g, X + 2, Y + 5, 3, 1, 0xC8D0D8);
            }
            case FOAM_CUP -> {                             // white styrofoam cup
                rect(g, X + 2, Y + 2, 4, 1, 0xDCDCD2);
                rect(g, X + 2, Y + 3, 4, 3, 0xFAFAF5);
                rect(g, X + 3, Y + 6, 2, 1, 0xFAFAF5);
                rect(g, X + 5, Y + 3, 1, 3, 0xD8D8CF);
            }
            case FLIP_FLOP -> {                            // blue sandal with a white strap
                rect(g, X + 2, Y + 1, 4, 6, 0x2E9BD6);
                rect(g, X + 3, Y, 2, 1, 0x2E9BD6);
                rect(g, X + 2, Y + 2, 1, 2, 0xF2F2F2);
                rect(g, X + 5, Y + 2, 1, 2, 0xF2F2F2);
                rect(g, X + 3, Y + 1, 2, 1, 0xF2F2F2);
                rect(g, X + 3, Y + 5, 2, 2, 0x1F78AC);
            }
            case FISHING_NET -> {                          // green net tangle with an orange float
                rect(g, X + 1, Y + 2, 6, 1, 0x5FA88F);
                rect(g, X + 1, Y + 5, 6, 1, 0x5FA88F);
                rect(g, X + 1, Y + 2, 1, 4, 0x5FA88F);
                rect(g, X + 4, Y + 2, 1, 4, 0x5FA88F);
                rect(g, X + 2, Y + 3, 1, 1, 0x3E7D69);
                rect(g, X + 5, Y + 3, 2, 2, 0xE8642C);
            }
            case BATTERY -> {
                rect(g, X + 1, Y + 2, 6, 4, 0x3A3A3A);
                rect(g, X + 1, Y + 2, 2, 4, 0xE6C229);
                rect(g, X + 7, Y + 3, 1, 2, 0xAAAAAA);
            }
            case LIGHTER -> {                              // red lighter, metal top
                rect(g, X + 2, Y + 2, 3, 5, 0xE04848);
                rect(g, X + 2, Y + 1, 3, 1, 0xB8B8B8);
                rect(g, X + 4, Y, 1, 1, 0x555555);
                rect(g, X + 3, Y + 3, 1, 2, 0xF08080);
            }
            case GLOW_STICK -> {                           // neon tube with dark caps
                for (int i = 0; i < 5; i++) rect(g, X + 2 + i, Y + 6 - i, 1, 1, 0x9CFF57);
                rect(g, X + 1, Y + 6, 1, 1, 0x2B2B2B);
                rect(g, X + 7, Y + 1, 1, 1, 0x2B2B2B);
                rect(g, X + 3, Y + 5, 1, 1, 0xE4FFC8);
            }
            case AEROSOL -> {                              // white spray can, orange cap and band
                rect(g, X + 2, Y + 2, 4, 5, 0xF5F5F0);
                rect(g, X + 3, Y + 1, 2, 1, 0xF29B38);
                rect(g, X + 2, Y + 4, 4, 1, 0xF29B38);
                rect(g, X + 4, Y + 5, 1, 1, 0xD9442F);
                rect(g, X + 5, Y + 2, 1, 5, 0xD6D6CF);
            }
        }
    }
}
