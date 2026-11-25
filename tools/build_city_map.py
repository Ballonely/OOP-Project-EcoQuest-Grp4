"""
Builds assets/maps/city_map.png (Level 5) from assets/maps/city_reference.png.

The reference art is 512x288 pixel art upscaled 4x. This script works on the 512x288
art grid (= world pixels, 16 px per tile), rearranges the scenery into a consistent
layout and writes the result back at 4x. Level5City's collision boxes match the
positions used here, so if you move something below, update Level5City too.

Layout rules:
  * Roads, roundabout, crosswalks and the recycling-center bins stay where they were
    (car routes and bins in Level5City depend on them).
  * Every north building's front faces the main street; south buildings face down.
  * Street lamps stand at the same x on both sides of the main street.
  * Trees and planters line up in one column instead of floating on the sidewalk.

Run from the project root:  python tools/build_city_map.py
"""
from PIL import Image
import numpy as np

SRC = "assets/maps/city_reference.png"
OUT = "assets/maps/city_map.png"

SIDEWALK = (179, 178, 168)
SEAM = (151, 151, 141)
HIGHLIGHT = (196, 195, 185)
# sidewalk colour -> the same colour in a building's shadow (sampled from the art)
SHADOW = {SIDEWALK: (140, 140, 131), SEAM: (120, 119, 110), HIGHLIGHT: (153, 153, 143)}
SIDEWALK_COLOURS = [SIDEWALK, SEAM, HIGHLIGHT] + list(SHADOW.values())
ALLEY_COLOURS = [(66, 62, 54), (59, 55, 48), (54, 51, 44), (48, 47, 45)]

# lamp x (world px) used on both sides of the main street
LAMP_XS = [38, 102, 230, 326, 438]


def load():
    big = Image.open(SRC).convert("RGB")
    return np.asarray(big.resize((big.width // 4, big.height // 4), Image.NEAREST)).copy()


def fill_sidewalk(img, tile, x0, y0, x1, y1):
    """Repaves [x0,x1) x [y0,y1) with clean sidewalk tiles aligned to the 16 px grid."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[y, x] = tile[y % 16, x % 16]


def paste(img, src, box, dx, dy, skip=()):
    """Copies src[box] to (box.x+dx, box.y+dy); pixels whose colour is in `skip` are left out."""
    x0, y0, x1, y1 = box
    part = src[y0:y1, x0:x1]
    mask = np.ones(part.shape[:2], bool)
    for c in skip:
        mask &= np.abs(part - np.array(c)).sum(2) > 0
    region = img[y0 + dy:y1 + dy, x0 + dx:x1 + dx]
    region[mask] = part[mask]


def shadow(img, x0, y0, x1, y1, right=5, bottom=4, drop=4):
    """Drop shadow to the right and below a building occupying [x0,x1) x [y0,y1)."""
    h, w = img.shape[:2]
    pixels = [(x, y) for y in range(y0 + drop, min(h, y1 + bottom)) for x in range(x1, min(w, x1 + right))]
    pixels += [(x, y) for y in range(y1, min(h, y1 + bottom)) for x in range(x0 + drop, min(w, x1))]
    for x, y in pixels:
        c = tuple(int(v) for v in img[y, x])
        if c in SHADOW:
            img[y, x] = SHADOW[c]


OUTLINE = (42, 37, 29)
TOWER_X0, TOWER_X1 = 416, 512          # tower footprint (world px), same as the old teal office
TOWER_ROOF_H = 33                      # compact roof: rim, AC units, front lip
TOWER_FLOORS, FLOOR_H = 4, 11          # glass floors stacked between the roof and the storefront
TOWER_BOTTOM = 112                     # front wall ends one tile above the main-street sidewalk
# glass and frame colours taken from the teal storefront at the bottom of the tower
GLASS, GLASS_LIGHT, GLASS_DARK = (151, 197, 188), (213, 224, 217), (112, 170, 161)
FRAME, FRAME_DARK, SLAB = (83, 148, 140), (68, 125, 118), (56, 104, 98)
FRAME_LIGHT = (104, 168, 159)


def tower(img, src, tile):
    """A tall office tower: short roof, four glass floors, the old teal storefront at street level."""
    x0, x1 = TOWER_X0, TOWER_X1
    fill_sidewalk(img, tile, x0, 0, x1, 124)
    roof = np.concatenate([src[0:4], src[14:36], src[50:57]])[:, x0:x1]
    img[0:TOWER_ROOF_H, x0:x1] = roof

    top = TOWER_ROOF_H
    for f in range(TOWER_FLOORS):
        y0 = top + f * FLOOR_H
        img[y0, 418:510] = SLAB                              # floor slab
        img[y0 + 1:y0 + FLOOR_H, 418:510] = FRAME
        img[y0 + FLOOR_H - 1, 418:510] = FRAME_DARK          # sill shadow
        for i in range(6):                                   # six windows per floor
            wx = 424 + i * 14
            img[y0 + 1:y0 + 10, wx:wx + 9] = OUTLINE
            img[y0 + 2:y0 + 9, wx + 1:wx + 8] = GLASS
            img[y0 + 2, wx + 1:wx + 8] = GLASS_LIGHT
            img[y0 + 8, wx + 1:wx + 8] = GLASS_DARK
            for k in range(3):                               # diagonal reflection
                img[y0 + 5 - k, wx + 2 + k] = GLASS_LIGHT
        img[y0:y0 + FLOOR_H, 418] = FRAME_LIGHT             # lit left edge
        img[y0:y0 + FLOOR_H, 508:510] = FRAME_DARK           # shaded right edge
    img[top:top + TOWER_FLOORS * FLOOR_H, 417] = OUTLINE
    img[top:top + TOWER_FLOORS * FLOOR_H, 510] = OUTLINE

    store_top = top + TOWER_FLOORS * FLOOR_H                 # 77
    img[store_top:TOWER_BOTTOM, x0:x1] = src[57:92, x0:x1]   # teal storefront with the door
    shadow(img, 417, 0, 511, TOWER_BOTTOM)


def cafe_tables(img, src, tile):
    """Cafe tables in front of the tower, clear of the street lamp (run after the lamp pass)."""
    paste_object(img, src, tile, (424, 100, 446, 118), 28, 16)   # -> x 452
    paste_object(img, src, tile, (470, 100, 492, 118), 14, 16)   # -> x 484


def paste_object(img, src, tile, box, dx, dy):
    """Copies only the pixels in src[box] that differ from the clean sidewalk tile under them."""
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            if tuple(src[y, x]) != tuple(tile[y % 16, x % 16]):
                img[y + dy, x + dx] = src[y, x]


def main():
    src = load()
    img = src.copy()
    tile = src[112:128, 272:288].copy()   # a clean sidewalk tile (col 17, row 7)

    # ---------- north-west block: park on top, building below facing the main street ----------
    fill_sidewalk(img, tile, 0, 0, 110, 142)
    paste(img, src, (0, 72, 96, 128), 0, -72)        # park (pond + 2 trees) -> y 0..56
    img[54:56, 30:46] = img[54:56, 14:30]            # an old lamp head was inside the park crop
    paste(img, src, (0, 0, 101, 66), 0, 64)          # brick building + its shadow -> y 64..126

    # ---------- dead-end alley -> sidewalk lane, dumpsters side by side at the back ----------
    fill_sidewalk(img, tile, 224, 0, 256, 128)
    shadow(img, 160, 0, 224, 46)                     # small brick building
    shadow(img, 160, 56, 224, 128)                   # bakery
    paste(img, src, (236, 40, 252, 63), -12, -38, ALLEY_COLOURS)   # dumpster -> (224, 2)
    paste(img, src, (236, 40, 252, 63), 4, -38, ALLEY_COLOURS)     # dumpster -> (240, 2)

    # ---------- north-east: tall glass office tower (replaces the low teal office) ----------
    tower(img, src, tile)

    # ---------- lamps: clear the old ones, then same x on both sides ----------
    fill_sidewalk(img, tile, 0, 128, 110, 142)       # north sidewalk, west of the side road
    fill_sidewalk(img, tile, 146, 128, 348, 142)     # north sidewalk, up to the roundabout
    fill_sidewalk(img, tile, 352, 98, 366, 124)      # stray lamp in front of the recycling center
    fill_sidewalk(img, tile, 424, 124, 512, 142)     # north sidewalk, east of the roundabout
    bus = src[176:196, 146:198].copy()               # keep the bus stop while clearing its sidewalk
    fill_sidewalk(img, tile, 0, 178, 146, 192)
    fill_sidewalk(img, tile, 198, 178, 348, 192)
    fill_sidewalk(img, tile, 424, 178, 512, 192)     # south sidewalk, east of the roundabout
    img[176:196, 146:198] = bus
    for x0, x1 in [(146, 160), (256, 270)]:          # old north lamp heads above the cleared strip
        fill_sidewalk(img, tile, x0, 124, x1, 128)
    # old south lamp heads on the curb: copy clean road + curb from a spot beside each one
    for x0, x1, dx in [(26, 44, 48), (198, 214, 32), (328, 344, -48), (436, 450, 32)]:
        img[170:178, x0:x1] = src[170:178, x0 + dx:x1 + dx]
    north_lamp = (150, 126, 156, 142)
    south_lamp = (440, 174, 446, 190)
    for x in LAMP_XS:
        paste(img, src, north_lamp, x - north_lamp[0], 0, SIDEWALK_COLOURS)
        paste(img, src, south_lamp, x - south_lamp[0], 0, SIDEWALK_COLOURS)

    cafe_tables(img, src, tile)

    # ---------- litter painted on the road (real litter spawns in-game) ----------
    img[166:176, 330:346] = src[166:176, 266:282]

    # ---------- garden column east of the laundromat: planter on top, two trees under it ----------
    fill_sidewalk(img, tile, 304, 192, 366, 288)
    shadow(img, 216, 200, 304, 272)                  # laundromat shadow over the repaved strip
    paste(img, src, (304, 204, 348, 216), 8, -4, SIDEWALK_COLOURS)     # flower planter -> y 200
    paste(img, src, (310, 222, 346, 252), 8, -7, SIDEWALK_COLOURS)     # tree, crown -> (320, 216)
    paste(img, src, (328, 252, 362, 280), -12, 0, SIDEWALK_COLOURS)    # tree, crown -> (320, 253)

    Image.fromarray(img.astype(np.uint8)).resize((2048, 1152), Image.NEAREST).save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
