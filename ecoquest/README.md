# Eco Quest: Level 3 (Market)

Java 17+ and Swing only. No libraries, no image files needed.

```
./run.sh          (Windows: run.bat)
```

Keep the `data` folder next to `src`. The game looks for it in the current folder, its parents, and next to the compiled classes.

## Controls

| Key | Action |
| --- | --- |
| WASD / arrows | Move |
| E or Space | Pick up trash, or drop the top item into the bin you stand next to |
| P / Esc | Pause |
| R | Restart |
| Enter | Start / play again |

## How Level 3 plays

- Boss: **Waste King** (the heap in the middle). Sort **40** pieces correctly.
- 5 hearts. A wrong bin costs a heart, shows the item's lesson, and the Waste King throws out 2 more trash pieces.
- 5:00 time limit. You carry up to 3 items; the last one picked up is dropped first.
- Hand carts bump you and make you drop an item. Puddles slow you down.
- Cleanliness starts at 30% and rises with every correct sort. Flowers, bunting, a stronger fountain and happy shoppers appear at 45 / 60 / 80%.
- Money follows your design doc: pickup +2, correct sort +5, hazardous +10 extra, every 10 sorted +25, level +100, boss +150.
  The doc says only "bonus" for a high-cleanliness finish; this build gives +50 at 95% or more (2 mistakes or fewer). Change it in `Game.win()`.

## Why these market items (research)

A wet and dry market study at Pasar Awam Maharani, Muar (Malaysia) found 38.5% chicken and fish waste, 25.2% vegetable and fruit waste and 36.3% inorganic waste.
Philippine public-market reporting says the same thing in words: market waste is mostly biodegradable plus packaging that looks recyclable.
So in `market.csv` the BIO items carry the highest weights, packaging comes next, and hazardous items are rare.
Philippine local flavour: "Sando Bag" (thin plastic bag), snack sachets, styrofoam trays (several cities ban these).

Sources:
- [Pasar Awam Maharani Muar waste composition study (UKM repository)](https://repoemc.ukm.my/handle/123456789/1221)
- [Managing Market Waste for Use in Urban Gardening in Metro Manila (CGIAR)](https://cgiar.org/news-events/news/managing-market-waste-for-use-in-urban-gardening-in-metro-manila)

## How to make a trash base for another map

Each map has its own CSV in `data/trash/`. The file name is the map key from `MapId.java`
(`residential`, `park`, `market`, `beach`, `city_center`).

1. Copy `data/trash/market.csv` to `data/trash/<map key>.csv`.
2. Replace the rows. One line per kind of trash:

   ```
   id,name,type,weight,tip
   fish_bone,Fish Bones,BIO,7,Fish waste rots fast. Compost it in the green bin.
   ```

   | Column | Meaning |
   | --- | --- |
   | `id` | Unique key. Also the sprite name: `assets/sprites/<id>.png` replaces the drawn icon if the file exists |
   | `name` | Shown to the player |
   | `type` | `BIO`, `REC`, `TRASH` or `HAZ` |
   | `weight` | Spawn chance relative to the other rows. Higher = more common on this map |
   | `tip` | One-line lesson shown when the player sorts it wrongly |

3. Decide the weights from what is really found there. Suggested starting points:
   - Residential street: food scraps, plastic bottles, sachets, cardboard, dog waste (TRASH), old batteries.
   - Public park: leaves and fruit peels, drink cans, snack wrappers, cigarette butts, glass bottles.
   - Beach: plastic bottles, fishing line and nets, flip-flops, straws, food wrappers, driftwood and seaweed (BIO).
   - City center: paper cups, newspapers, e-waste, vape pens and batteries (HAZ), takeaway boxes.
4. New ids without a drawing still work: they appear as a blob in their bin colour. To draw one, add a `case` in `Sprites.drawIcon` (14x14 pixels) or drop a PNG into `assets/sprites/`.
5. Build the level layout by copying `MarketLevel.java` (solids, stalls or props, bins, carts, shoppers, boss name and `Config`), then add it to `Level.create(...)`.
   Boss tuning lives in `Level.Config`: target pieces, hearts, time limit, starting items, capacity.

## Files

| File | Job |
| --- | --- |
| `MapId`, `WasteType`, `TrashDef`, `TrashCatalog` | Maps, the four bins, item data, CSV loading and weighted random pick |
| `Level`, `MarketLevel` | Layout, art and tuning of one map |
| `Game` | All rules: pickup, sorting, money, boss, spawning, collisions (no Swing) |
| `GamePanel`, `Main` | Drawing, HUD, menus, keyboard |
| `Sprites`, `Tiles` | Pixel art painted in code |
