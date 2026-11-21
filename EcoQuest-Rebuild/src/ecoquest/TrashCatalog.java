package ecoquest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Loads the trash base of a map from data/trash/&lt;map key&gt;.csv.
 * To give a new map its own garbage, add a CSV file. No Java changes needed.
 */
public final class TrashCatalog {
    private TrashCatalog() {
    }

    public static List<TrashDef> load(MapId map) {
        Path file = Res.find("data/trash/" + map.key + ".csv");
        if (!Files.exists(file)) {
            throw new IllegalStateException("Missing trash base: " + file.toAbsolutePath()
                    + "\nKeep the data folder next to src (project folder).");
        }
        List<TrashDef> out = new ArrayList<>();
        try {
            int lineNo = 0;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split(",", 5);
                if (p.length < 4) {
                    throw new IllegalStateException(file + ":" + lineNo + " needs id,name,type,weight[,tip]");
                }
                String tip = p.length == 5 ? p[4].trim() : "";
                out.add(new TrashDef(p[0].trim(), p[1].trim(), WasteType.parse(p[2]),
                        Math.max(1, Integer.parseInt(p[3].trim())), tip));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
        if (out.isEmpty()) throw new IllegalStateException(file + " has no items");
        return out;
    }

    /** Weighted random pick: items with a higher weight show up more often. */
    public static TrashDef pick(List<TrashDef> defs, Random rng) {
        int total = 0;
        for (TrashDef d : defs) total += d.weight();
        int roll = rng.nextInt(total);
        for (TrashDef d : defs) {
            roll -= d.weight();
            if (roll < 0) return d;
        }
        return defs.get(defs.size() - 1);
    }
}
