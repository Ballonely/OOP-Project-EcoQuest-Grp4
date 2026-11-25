package com.ecoquest.level1;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Town Market trash base, loaded from data/trash/market.csv
 * (format: id,name,type,weight[,tip]; type is BIO | REC | TRASH | HAZ).
 * To change what litters the market, edit the CSV. No Java changes needed.
 */
final class MarketTrash {
    /** One kind of market garbage. {@code tip} is the lesson shown after a wrong bin. */
    record Def(String id, String name, GarbageType type, int weight, String tip) {
    }

    private static final String CSV = "data/trash/market.csv";
    private static List<Def> catalog;

    private MarketTrash() {
    }

    static synchronized List<Def> catalog() {
        if (catalog == null) catalog = load();
        return catalog;
    }

    /** Weighted random pick: items with a higher weight show up more often. */
    static Def pick(Random rng) {
        List<Def> defs = catalog();
        int total = 0;
        for (Def d : defs) total += d.weight();
        int roll = rng.nextInt(total);
        for (Def d : defs) {
            roll -= d.weight();
            if (roll < 0) return d;
        }
        return defs.get(defs.size() - 1);
    }

    /** Finds a data/asset file next to the project or the compiled classes in bin/. */
    static File find(String relative) {
        String dir = System.getProperty("user.dir");
        for (File f : new File[] {new File(dir, relative), new File(dir, "bin/" + relative)}) {
            if (f.exists()) return f;
        }
        return new File(dir, relative);
    }

    private static List<Def> load() {
        File file = find(CSV);
        if (!file.isFile()) {
            throw new IllegalStateException("Missing Town Market trash base: " + file.getAbsolutePath());
        }
        List<Def> out = new ArrayList<>();
        try {
            int lineNo = 0;
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                lineNo++;
                if (line.startsWith("﻿")) line = line.substring(1);
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split(",", 5);
                if (p.length < 4) {
                    throw new IllegalStateException(file + ":" + lineNo + " needs id,name,type,weight[,tip]");
                }
                String tip = p.length == 5 ? p[4].trim() : "";
                out.add(new Def(p[0].trim(), p[1].trim(), parseType(p[2]),
                        Math.max(1, Integer.parseInt(p[3].trim())), tip));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
        if (out.isEmpty()) throw new IllegalStateException(file + " has no items");
        return out;
    }

    private static GarbageType parseType(String s) {
        switch (s.trim().toUpperCase()) {
            case "BIO": return GarbageType.BIODEGRADABLE;
            case "REC": return GarbageType.RECYCLABLE;
            case "TRASH": return GarbageType.NON_RECYCLABLE;
            case "HAZ": return GarbageType.HAZARDOUS;
            default: throw new IllegalArgumentException("Unknown waste type: " + s);
        }
    }
}
