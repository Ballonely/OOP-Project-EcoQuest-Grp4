package ecoquest;

/** One kind of garbage that can appear on a map. */
public record TrashDef(String id, String name, WasteType type, int weight, String tip) {
}
