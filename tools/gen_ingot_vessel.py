"""Wraith Iron ingot (a vanilla-like bar seen from above, cold blue steel with a soul-cyan sheen) and the Soul Vessel
(a dark iron urn with a gold band and a glass belly full of souls). 16x16, hand-placed shapes.

    python tools/gen_ingot_vessel.py
"""
from pathlib import Path

from PIL import Image

ITEM = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulless/textures/item"


def inside(px, py, poly):
    n, c = len(poly), False
    for i in range(n):
        x0, y0 = poly[i]
        x1, y1 = poly[(i + 1) % n]
        if (y0 > py) != (y1 > py) and px < (x1 - x0) * (py - y0) / (y1 - y0) + x0:
            c = not c
    return c


def ingot():
    # an isometric bar running from lower left to upper right: the top, the long front side and the left end
    top = [(1.0, 10.0), (10.5, 5.25), (15.0, 7.5), (5.5, 12.25)]
    front = [(5.5, 12.25), (15.0, 7.5), (15.0, 9.75), (5.5, 14.5)]
    end = [(1.0, 10.0), (5.5, 12.25), (5.5, 14.5), (1.0, 12.25)]
    TOP, TOP_HI, FRONT, FRONT_LO, END, LINE = (168, 196, 212), (222, 242, 248), (104, 132, 152), (84, 106, 126), (74, 92, 112), (28, 34, 48)
    SHEEN, SHEEN_HI = (110, 226, 238), (196, 252, 255)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    face = {}
    for y in range(16):
        for x in range(16):
            c = (x + 0.5, y + 0.5)
            if inside(*c, top):
                face[(x, y)] = "top"
            elif inside(*c, front):
                face[(x, y)] = "front"
            elif inside(*c, end):
                face[(x, y)] = "end"
    for (x, y), f in face.items():
        if f == "top":
            # lighter toward the far edge, a bright streak along the middle
            col = TOP
            if (x + 2 * y) in (22, 23):
                col = TOP_HI
            px[x, y] = col + (255,)
        elif f == "front":
            px[x, y] = (FRONT if (x + 2 * y) % 7 else FRONT_LO) + (255,)
        else:
            px[x, y] = END + (255,)
    # the soul-cyan sheen on the edge where the top meets the front, brighter toward the right end
    for (x, y), f in face.items():
        if f == "top" and face.get((x, y + 1)) == "front":
            px[x, y] = (SHEEN_HI if x > 10 else SHEEN) + (255,)
    # outline: every empty pixel touching the bar
    for y in range(16):
        for x in range(16):
            if (x, y) in face:
                continue
            if any((x + dx, y + dy) in face for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = LINE + (255,)
    # a glint
    px[11, 6] = (255, 255, 255, 255)
    im.save(ITEM / "wraith_iron_ingot.png")


VESSEL = [
    "................",
    "......0000......",
    ".....0a77a0.....",
    "....0b888870....",
    ".....0bb870.....",
    "......0870......",
    ".....0b88870....",
    "...00b8cdc870...",
    "..0b8cdeedc870..",
    "..0b8cdeedc870..",
    "..0b8ccddcc870..",
    "...0aaaaaaaa0...",
    "....0b888870....",
    ".....0b8870.....",
    "....00000000....",
    "................",
]
PAL = {
    "0": (20, 22, 32), "7": (56, 62, 76), "8": (92, 100, 118), "b": (158, 168, 184),
    "a": (196, 160, 82), "c": (28, 86, 108), "d": (84, 212, 232), "e": (226, 255, 255),
}


def vessel():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(VESSEL):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = PAL[ch] + (255,)
    im.save(ITEM / "soul_vessel.png")


if __name__ == "__main__":
    ingot()
    vessel()
