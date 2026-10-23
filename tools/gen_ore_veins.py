"""The Wraith Iron ore: thin cold veins on their own (a transparent overlay), laid over the game's own stone and
deepslate by the block models (models/block/*wraith_iron_ore.json), so the ore matches the rock round it exactly.

    python tools/gen_ore_veins.py
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulless/textures/block/wraith_iron_ore_veins.png"
COL = {"o": (30, 50, 62, 255), "d": (52, 98, 122, 255), "m": (84, 156, 186, 255), "l": (156, 222, 242, 255), "h": (236, 255, 255, 255)}
VEINS = [
    "................",
    "..dm............",
    "..hlm.......od..",
    "..oml......dlm..",
    "....mo....hlmo..",
    ".....o...dlm....",
    "......dm..o.....",
    ".....mlh........",
    "....dmll........",
    "....oml.....dm..",
    "......o....hlmo.",
    ".dm.......dmlo..",
    "hlmo......o.....",
    "omld.....dlm....",
    "..o.....dmlhm...",
    ".........omo....",
]


def main():
    im = Image.new("RGBA", (16, 16))
    for y, row in enumerate(VEINS):
        for x, c in enumerate(row):
            if c in COL:
                im.putpixel((x, y), COL[c])
    im.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
