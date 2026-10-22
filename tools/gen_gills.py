"""The Drowned One's Gills (16x16): a pendant of pale gill-flesh on a dark cord - three gill slits glowing sea-cyan.

    python tools/gen_gills.py
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulless/textures/item/drowned_gills.png"
G = [
    "..cc........cc..",
    "...c........c...",
    "...cc......cc...",
    "....c......c....",
    "....cc....cc....",
    ".....cmmmmc.....",
    "....mffffffm....",
    "...mfgsffsgfm...",
    "...mfgsffsgfm...",
    "...mffgsgsffm...",
    "...mffgsgsffm...",
    "....mffffffm....",
    ".....mfwwfm.....",
    "......mffm......",
    ".......mm.......",
    "................",
]
PAL = {"c": (40, 34, 30), "m": (24, 52, 56), "f": (70, 120, 118), "g": (40, 80, 84), "s": (120, 240, 230), "w": (180, 220, 210)}
im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = im.load()
fill = set()
for y, r in enumerate(G):
    assert len(r) == 16
    for x, ch in enumerate(r):
        if ch != ".":
            px[x, y] = PAL[ch] + (255,)
            fill.add((x, y))
for y in range(16):
    for x in range(16):
        if (x, y) not in fill and any((x + a, y + b) in fill for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px[x, y] = (8, 10, 14, 255)
im.save(OUT)
