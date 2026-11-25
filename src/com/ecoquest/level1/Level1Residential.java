package com.ecoquest.level1;

import java.util.ArrayList;
import java.util.List;

import static com.ecoquest.level1.Config.COLS;
import static com.ecoquest.level1.Config.TILE;
import static com.ecoquest.level1.Config.WORLD_W;
import static com.ecoquest.level1.House.Style.BROWN;
import static com.ecoquest.level1.House.Style.SLATE;

/**
 * Level 1 - Residential Street (boss: Trash Monster).
 *
 * Layout (32 x 18 tiles). Every house faces a street, and every lot follows the same pattern:
 * <pre>
 *   row  0      back fence, tree crowns behind it
 *   rows 2-5    north houses           (front yard row 6, sidewalk row 7)
 *   rows 8-9    main street            (spawn + sorting bins)
 *   row  10     sidewalk behind the south houses
 *   rows 11-14  south houses           (front yard row 15, sidewalk row 16)
 *   row  17     lower street (continues off the map)
 *   cols 8-9 and 24-25: cross streets from top to bottom
 * </pre>
 * Each front yard has: a stone path from the door to the sidewalk, a mailbox beside it,
 * a concrete driveway in front of the garage (if any) and a low hedge filling the rest of
 * the lot frontage. Lamps line the main street on both sides at the same columns.
 */
public class Level1Residential extends Level {
    public static final int GARBAGE_COUNT = 36;

    private static final int NORTH_ROW = 2;    // top tile row of the north houses
    private static final int SOUTH_ROW = 11;   // top tile row of the south houses
    private static final int[] MAILBOX_COLORS = { 0x2F6FD0, 0xC0392B };
    private int mailboxCount;

    /** Coins for defeating the Trash Monster, paid on top of the standard level-clear reward. */
    public static final int BOSS_REWARD = 150;

    @Override public String getName() { return "Level 1: Residential Street"; }
    @Override public int clearBonus() { return BOSS_REWARD; }
    @Override public String introMessage() {
        return "Clean the street! Sort litter into the matching bins. Something stirs under the road...";
    }
    @Override public String clearMessage() { return "Trash Monster defeated! Street cleaned!"; }
    @Override public boolean hasTrashBoss() { return true; }

    /** The Trash Monster bursts out of the main street at the crossing farthest from the player. */
    @Override public TrashBoss createTrashBoss(Player player) {
        int col = player.centerX() < WORLD_W / 2.0 ? 24 : 8;
        return new TrashMonsterBoss(col * TILE - 12, 8 * TILE - 26);   // feet centred on the crossing
    }

    @Override
    protected void buildTerrain() {
        fill(TileType.ROAD, 0, 8, 31, 9);     // main street
        fill(TileType.ROAD, 0, 17, 31, 17);   // lower street (top half visible)
        fill(TileType.ROAD, 8, 0, 9, 17);     // west cross street
        fill(TileType.ROAD, 24, 0, 25, 17);   // east cross street (avenue)
        spawnCol = 16; spawnRow = 8;
    }

    @Override
    protected void buildProps() {
        mailboxCount = 0;

        // ---- back fence along the top of each block, back-yard trees in front of it ----
        props.add(new Hedge(0, 0, 7, true));
        props.add(new Hedge(11, 0, 12, true));
        props.add(new Hedge(27, 0, 5, true));
        int[] backTrees = { -4, 84, 200, 300, 342, 488 };   // crowns end above the roofs
        for (int x : backTrees) props.add(new Tree(x, -8));

        // ---- north side lots (house tile col, width, style, garage, lot cols) ----
        lot(1,  NORTH_ROW, 4, SLATE, false, 0, 6);
        lot(11, NORTH_ROW, 5, BROWN, true, 11, 16);
        lot(18, NORTH_ROW, 4, SLATE, false, 17, 22);
        lot(27, NORTH_ROW, 5, BROWN, true, 27, 31);

        // ---- south side lots ----
        lot(1,  SOUTH_ROW, 5, BROWN, true, 0, 6);
        lot(11, SOUTH_ROW, 4, SLATE, false, 11, 16);
        lot(17, SOUTH_ROW, 5, BROWN, true, 17, 22);
        lot(27, SOUTH_ROW, 5, SLATE, true, 27, 31);

        // ---- trees in the side yards between houses (never over a roof) ----
        props.add(new Tree(86, NORTH_ROW * TILE + 6));
        props.add(new Tree(260, NORTH_ROW * TILE + 6));
        props.add(new Tree(246, SOUTH_ROW * TILE + 6));

        // ---- cars parked along the curbs ----
        props.add(new Car(40, 8 * TILE + 1, true, 0x6B7280, 0x8D95A3));             // main street, north curb
        props.add(new Car(440, 9 * TILE + 1, true, 0x565C68, 0x727A88));            // main street, south curb
        props.add(new Car(9 * TILE + 1, 12 * TILE + 2, false, 0x8A3B32, 0xA9554A)); // west cross street
        props.add(new Car(24 * TILE + 1, 2 * TILE + 4, false, 0x6B7280, 0x8D95A3)); // avenue
        props.add(new Car(196, 17 * TILE + 1, true, 0x2F5D8A, 0x4A7BAA));           // lower street

        // ---- street lamps: same columns on both sides of the main street, plus the lower street ----
        int[] mainLampCols = { 5, 16, 22, 27 };
        for (int c : mainLampCols) {
            lamp(c, 7);
            lamp(c, 10);
        }
        int[] lowerLampCols = { 6, 15, 22, 27 };
        for (int c : lowerLampCols) lamp(c, 16);

        // ---- puddles on the road slow the player ----
        props.add(new Puddle(88, 148, 22, 9));
        props.add(new Puddle(292, 132, 22, 9));
        props.add(new Puddle(132, 40, 20, 9));
        props.add(new Puddle(396, 216, 20, 9));
    }

    /**
     * One house lot: the house, flower beds, a path from the door to the sidewalk, a mailbox,
     * a driveway in front of the garage and a hedge along the rest of the frontage.
     * The front yard is the tile row just above the sidewalk (house row + 4).
     */
    private void lot(int tx, int ty, int tilesW, House.Style style, boolean garage, int lotC0, int lotC1) {
        House h = new House(tx, ty, tilesW, style, garage);
        props.add(h);
        props.add(new FlowerBed(tx * TILE + 3, h.baseY() + 1, 16));
        props.add(new FlowerBed(h.doorPixelX() + 10, h.baseY() + 1, 14));

        int yardRow = ty + 4;
        int sidewalkY = (yardRow + 1) * TILE;
        int doorCol = h.doorTileCol();
        setTile(TileType.PATH, doorCol, yardRow);
        props.add(new Mailbox((doorCol + 1) * TILE + 2, yardRow * TILE + 3,
                MAILBOX_COLORS[mailboxCount++ % MAILBOX_COLORS.length]));

        int driveL = Integer.MAX_VALUE, driveR = Integer.MIN_VALUE;
        if (garage) {
            driveL = h.garagePixelX() - 2;
            driveR = driveL + h.garagePixelW() + 4;
            props.add(new Driveway(driveL, h.baseY(), driveR - driveL, sidewalkY - h.baseY()));
        }

        // hedge on every frontage tile that is not the path, the mailbox or the driveway
        List<Integer> hedgeCols = new ArrayList<>();
        for (int c = lotC0; c <= lotC1 && c < COLS; c++) {
            boolean onDrive = c * TILE < driveR && (c + 1) * TILE > driveL;
            if (c != doorCol && c != doorCol + 1 && !onDrive) hedgeCols.add(c);
        }
        for (int i = 0; i < hedgeCols.size(); ) {
            int start = hedgeCols.get(i), len = 1;
            while (i + len < hedgeCols.size() && hedgeCols.get(i + len) == start + len) len++;
            props.add(new Hedge(start, yardRow, len, false));
            i += len;
        }
    }

    private void lamp(int col, int row) {
        props.add(new LampPost(col * TILE + 5, row * TILE - 2));
    }

    @Override
    protected void buildBins() {
        // one bin of each type on the sidewalk, right above the player's start
        bins.add(new TrashBin(GarbageType.BIODEGRADABLE, 17, 7));
        bins.add(new TrashBin(GarbageType.RECYCLABLE, 18, 7));
        bins.add(new TrashBin(GarbageType.NON_RECYCLABLE, 19, 7));
        bins.add(new TrashBin(GarbageType.HAZARDOUS, 20, 7));
    }

    @Override
    protected void buildGarbage() { scatterGarbage(GARBAGE_COUNT); }
}
