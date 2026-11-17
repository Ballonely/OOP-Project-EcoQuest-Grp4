package com.ecoquest.level1;

import static com.ecoquest.level1.House.Style.BROWN;
import static com.ecoquest.level1.House.Style.SLATE;

public final class Level5CityCenter extends Level {
    @Override public String getName() { return "Level 5: Grand Metropolis City Center"; }

    @Override protected void buildTerrain() {
        fill(TileType.ROAD, 0, 8, 31, 9);
        fill(TileType.ROAD, 8, 0, 9, 17);
        fill(TileType.ROAD, 23, 0, 24, 17);
        spawnCol = 16; spawnRow = 8;
    }

    @Override protected void buildProps() {
        for (int[] a : new int[][]{{1,2,5},{3,12,4},{11,2,5},{12,12,4},{17,2,5},{18,12,4},{26,2,5},{27,12,4}})
            props.add(new House(a[0], a[1], a[2], (a[0] & 1) == 0 ? SLATE : BROWN, true));
        for (int[] t : new int[][]{{1,7},{6,10},{11,7},{20,10},{26,7},{30,10},{16,15}})
            props.add(new Tree(t[0] * Config.TILE, t[1] * Config.TILE));
        for (int[] l : new int[][]{{5,7},{14,7},{22,7},{13,10},{28,10}})
            props.add(new LampPost(l[0] * Config.TILE + 5, l[1] * Config.TILE - 2));
    }

    @Override protected void buildBins() {
        bins.add(new TrashBin(GarbageType.BIODEGRADABLE, 14, 7));
        bins.add(new TrashBin(GarbageType.RECYCLABLE, 15, 7));
        bins.add(new TrashBin(GarbageType.NON_RECYCLABLE, 17, 7));
        bins.add(new TrashBin(GarbageType.HAZARDOUS, 18, 7));
    }

    @Override protected void buildGarbage() { scatterGarbage(40); }
}
