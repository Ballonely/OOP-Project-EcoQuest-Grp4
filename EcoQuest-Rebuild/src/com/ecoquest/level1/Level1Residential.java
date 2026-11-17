package com.ecoquest.level1;

import static com.ecoquest.level1.Config.TILE;
import static com.ecoquest.level1.House.Style.BROWN;
import static com.ecoquest.level1.House.Style.SLATE;

/**
 * Level 1 - Residential Street (boss: Trash Monster).
 *
 * Layout (32 x 18 tiles), based on the reference image:
 *   - a long main street (rows 8-9)
 *   - an avenue going up/down on the right (cols 24-25)
 *   - a side lane running straight from the top to the bottom
 *   - houses on both sides, cars in driveways, hedges, lamps, mailboxes, puddles
 */
public class Level1Residential extends Level {
    public static final int GARBAGE_COUNT = 36;

    @Override public String getName() { return "Level 1: Residential Street"; }

    @Override
    protected void buildTerrain() {
        fill(TileType.ROAD, 0, 8, 31, 9);     // main street
        fill(TileType.ROAD, 24, 0, 25, 17);   // right avenue
        fill(TileType.ROAD, 8, 0, 9, 17);     // straight side lane
        spawnCol = 16; spawnRow = 8;
    }

    @Override
    protected void buildProps() {
        // ---- front row, north side ----
        house(2, 2, 4, SLATE, false);
        house(12, 2, 3, BROWN, false);
        house(15, 2, 5, SLATE, true);
        house(27, 2, 5, BROWN, true);

        // ---- back row (only the bottom peeks into the map) ----
        int[][] back = { {0, 3}, {4, 3}, {12, 3}, {16, 4}, {20, 3}, {27, 3} };
        for (int i = 0; i < back.length; i++)
            props.add(new House(back[i][0], -2, back[i][1], i % 2 == 0 ? SLATE : BROWN, false));

        // ---- south side ----
        house(2, 12, 4, BROWN, true);
        house(11, 12, 4, SLATE, false);
        house(17, 12, 4, SLATE, false);
        house(27, 12, 5, BROWN, true);

        // ---- stone paths from the front doors to the sidewalk ----
        for (Prop p : props)
            if (p instanceof House && p.getY() == 2 * TILE) setTile(TileType.PATH, ((House) p).doorTileCol(), 6);

        // ---- trees ----
        int[][] trees = {
            {0, 3}, {6, 2}, {20, 4}, {21, 1}, {22, 5}, {30, 6},       // north
            {3, 0}, {11, 0}, {13, 0}, {22, 0}, {29, 0},                // back row
            {0, 12}, {6, 14}, {15, 15}, {16, 11},                     // south
            {22, 14}, {29, 16}, {12, 16}, {4, 16}
        };
        for (int[] t : trees) props.add(new Tree(t[0] * TILE, t[1] * TILE));

        // ---- cars (obstacles) ----
        props.add(new Car(97, 4 * TILE, false, 0x6B7280, 0x8D95A3));
        props.add(new Car(21 * TILE + 1, 3 * TILE + 8, false, 0x565C68, 0x727A88));
        props.add(new Car(2 * TILE, 16 * TILE, false, 0x6B7280, 0x8D95A3));
        props.add(new Car(28 * TILE, 16 * TILE, false, 0x565C68, 0x727A88));

        // ---- hedges and fences ----
        props.add(new Hedge(4, 6, 2, false));
        props.add(new Hedge(10, 6, 2, false));
        props.add(new Hedge(17, 6, 3, false));
        props.add(new Hedge(29, 6, 3, true));
        props.add(new Hedge(1, 11, 4, false));
        props.add(new Hedge(12, 11, 3, false));

        // ---- street furniture ----
        int[][] lamps = { {5, 7}, {14, 7}, {22, 7}, {13, 10}, {22, 10}, {28, 10}, {10, 4} };
        for (int[] l : lamps) props.add(new LampPost(l[0] * TILE + 5, l[1] * TILE - 2));
        props.add(new Mailbox(2 * TILE + 5, 7 * TILE + 4, 0x2F6FD0));
        props.add(new Mailbox(12 * TILE + 5, 7 * TILE + 4, 0xC0392B));
        props.add(new Mailbox(21 * TILE + 5, 10 * TILE + 4, 0x2F6FD0));
        props.add(new Mailbox(30 * TILE + 5, 10 * TILE + 4, 0xC0392B));

        // ---- puddles slow the player ----
        props.add(new Puddle(100, 134, 24, 10));
        props.add(new Puddle(206, 144, 22, 9));
        props.add(new Puddle(389, 40, 20, 9));
        props.add(new Puddle(390, 210, 20, 9));
    }

    /** Adds a house plus a flower bed under it. */
    private void house(int tx, int ty, int tilesW, House.Style s, boolean garage) {
        House h = new House(tx, ty, tilesW, s, garage);
        props.add(h);
        props.add(new FlowerBed(tx * TILE + 3, h.baseY() + 1, 16));
        props.add(new FlowerBed(h.doorPixelX() + 10, h.baseY() + 1, 14));
    }

    @Override
    protected void buildBins() {
        // one bin of each type on the sidewalk, right below the player's start
        bins.add(new TrashBin(GarbageType.BIODEGRADABLE, 17, 7));
        bins.add(new TrashBin(GarbageType.RECYCLABLE, 18, 7));
        bins.add(new TrashBin(GarbageType.NON_RECYCLABLE, 19, 7));
        bins.add(new TrashBin(GarbageType.HAZARDOUS, 20, 7));
    }

    @Override
    protected void buildGarbage() { scatterGarbage(GARBAGE_COUNT); }
}
