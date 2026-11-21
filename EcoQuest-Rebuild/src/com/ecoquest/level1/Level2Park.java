package com.ecoquest.level1;

public final class Level2Park extends Level {
    @Override public String getName() { return "Level 2: Lush Public Park"; }

    @Override protected void buildTerrain() {
        fill(TileType.PATH, 0, 8, 31, 9);
        fill(TileType.PATH, 15, 0, 16, 17);
        spawnCol = 15; spawnRow = 8;
    }

    @Override protected void buildProps() {
        for (int[] t : new int[][]{{2,2},{6,3},{10,2},{20,2},{26,3},{29,10},{23,14},{18,12},{5,13},{1,8},{8,8},{24,8},{30,8}})
            props.add(new Tree(t[0] * Config.TILE, t[1] * Config.TILE));
        props.add(new Puddle(45, 45, 56, 26));
        props.add(new Puddle(392, 172, 58, 25));
        for (int x : new int[]{70, 176, 300, 420}) props.add(new ParkBench(x, 170));
        props.add(new FlowerBed(42, 200, 62));
        props.add(new FlowerBed(404, 66, 56));
    }

    @Override protected void buildBins() {
        bins.add(new TrashBin(GarbageType.BIODEGRADABLE, 13, 7));
        bins.add(new TrashBin(GarbageType.RECYCLABLE, 14, 7));
        bins.add(new TrashBin(GarbageType.NON_RECYCLABLE, 17, 7));
        bins.add(new TrashBin(GarbageType.HAZARDOUS, 18, 7));
    }

    @Override protected void buildGarbage() { scatterGarbage(36); }
}
