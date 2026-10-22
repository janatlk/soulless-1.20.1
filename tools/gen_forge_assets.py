"""Pixel textures for the forge content: Wraith Iron ore (stone and deepslate), raw Wraith Iron, the ingot, the
Reaper's Soul and the Soul Anvil. 16x16, drawn from seeded noise so a re-run gives the same pictures.

    python tools/gen_forge_assets.py
"""
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulless/textures"
N = 16


def rng(seed):
    return random.Random(seed)


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, k):
    return tuple(clamp(x * k) for x in c[:3]) + ((c[3],) if len(c) > 3 else (255,))


def stone(seed, base, spread, layers=False):
    r = rng(seed)
    im = Image.new("RGBA", (N, N))
    px = im.load()
    for y in range(N):
        band = (math.sin(y * 1.3 + seed) * 0.05) if layers else 0
        for x in range(N):
            k = 1 + (r.random() - 0.5) * spread + band
            if r.random() < 0.08:
                k *= 0.78
            px[x, y] = shade(base, k)
    return im


# the vein: a pale ghostly blue-white with a cold glow and a dark rim
VEIN = [(30, 38, 52), (120, 190, 210), (190, 240, 245), (240, 255, 255)]


def veins(im, seed, clusters):
    r = rng(seed)
    px = im.load()
    for cx, cy in clusters:
        cells = {(cx, cy)}
        for _ in range(r.randint(3, 5)):
            x, y = r.choice(sorted(cells))
            cells.add((max(1, min(14, x + r.choice((-1, 0, 1)))), max(1, min(14, y + r.choice((-1, 0, 1))))))
        for x, y in cells:   # dark rim first
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + dy) not in cells and 0 <= x + dx < N and 0 <= y + dy < N:
                    px[x + dx, y + dy] = VEIN[0] + (255,)
        for x, y in cells:
            px[x, y] = VEIN[r.choice((1, 2, 2, 3))] + (255,)
    return im


def ore(name, seed, base, spread, layers):
    im = veins(stone(seed, base, spread, layers), seed + 1, [(4, 4), (11, 6), (6, 11), (12, 12)])
    im.save(ROOT / "block" / f"{name}.png")


def shape_item(name, seed, mask, palette, glint=None):
    """mask: 16 strings, '.' empty, digits index palette (0 darkest)."""
    r = rng(seed)
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            c = palette[int(ch)]
            px[x, y] = shade(c, 1 + (r.random() - 0.5) * 0.08)
    if glint:
        for x, y in glint:
            px[x, y] = (255, 255, 255, 255)
    im.save(ROOT / "item" / f"{name}.png")


INGOT = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......000000...",
    ".....00333320...",
    "...00333222210..",
    ".00332222211110.",
    "0333222221111100",
    "0222222111111000",
    "01111111110000..",
    ".000000000......",
    "................",
    "................",
    "................",
]
STEEL = [(36, 44, 58), (92, 112, 130), (150, 178, 196), (205, 236, 245)]

RAW = [
    "................",
    "................",
    "................",
    "......000.......",
    "....00332000....",
    "...0332222100...",
    "..033223221110..",
    "..0322122211110.",
    ".03221112211100.",
    ".02211021111100.",
    ".0211110111000..",
    "..011111110.....",
    "...00000000.....",
    "................",
    "................",
    "................",
]
RAW_PAL = [(30, 36, 48), (78, 92, 108), (126, 160, 178), (190, 236, 244)]

SOUL = [
    "................",
    ".......3........",
    "......33........",
    "......323.......",
    ".....3223.3.....",
    "....32212323....",
    "....321122223...",
    "...3211001223...",
    "...3210000123...",
    "...3210000123...",
    "...3221001223...",
    "....32211223....",
    ".....322223.....",
    "......3333......",
    "................",
    "................",
]
SOUL_PAL = [(250, 255, 255), (170, 240, 250), (90, 170, 220), (60, 40, 110)]


SIGIL = [
    "................",
    ".....000000.....",
    "...0011111100...",
    "..011222222110..",
    "..012233332210..",
    ".01223.33.32210.",
    ".0123333333.210.",
    ".012.33.3333210.",
    ".0123333.333210.",
    ".01233.3333.210.",
    "..012333.33210..",
    "..011222222110..",
    "...0011111100...",
    ".....000000.....",
    "................",
    "................",
]
SIGIL_PAL = [(30, 14, 18), (92, 28, 34), (150, 40, 46), (214, 60, 62)]


VESSEL = [
    "................",
    "......0000......",
    ".....033330.....",
    "......0110......",
    ".....011110.....",
    "....01222210....",
    "...0122222210...",
    "...0124444210...",
    "...0124554210...",
    "...0124554210...",
    "...0122442210...",
    "....01222210....",
    ".....011110.....",
    "....00000000....",
    "................",
    "................",
]
VESSEL_PAL = [(28, 32, 42), (70, 84, 98), (118, 140, 156), (160, 120, 70), (80, 200, 220), (210, 250, 255)]


def anvil():
    dark = (46, 50, 62)
    top = stone(71, dark, 0.18)
    px = top.load()
    for x in range(N):   # a worn rim
        px[x, 0] = shade(dark, 0.7)
        px[x, 15] = shade(dark, 0.7)
    for y in range(N):
        px[0, y] = shade(dark, 0.7)
        px[15, y] = shade(dark, 0.7)
    r = rng(72)
    x, y = 3, 8
    for _ in range(14):   # a glowing crack across the face, where the souls are beaten in
        px[x, y] = VEIN[2] + (255,)
        if 0 < y < 15:
            px[x, y + 1] = VEIN[1] + (255,)
        x = min(13, x + 1)
        y = max(2, min(13, y + r.choice((-1, 0, 0, 1))))
    top.save(ROOT / "block" / "soul_anvil_top.png")
    side = stone(73, dark, 0.16)
    px = side.load()
    for x in range(N):
        px[x, 0] = shade(dark, 1.25)
        px[x, 15] = shade(dark, 0.6)
    for x in range(4, 12):   # a rune band
        px[x, 7] = VEIN[1] + (255,) if x % 2 else VEIN[0] + (255,)
    side.save(ROOT / "block" / "soul_anvil_side.png")
    base = stone(74, shade(dark, 0.8), 0.14)
    base.save(ROOT / "block" / "soul_anvil_base.png")


def main():
    (ROOT / "block").mkdir(parents=True, exist_ok=True)
    ore("wraith_iron_ore", 11, (124, 124, 126), 0.22, False)
    ore("deepslate_wraith_iron_ore", 21, (74, 74, 82), 0.2, True)
    shape_item("wraith_iron_ingot", 31, INGOT, STEEL, glint=[(4, 9), (5, 8)])
    shape_item("raw_wraith_iron", 41, RAW, RAW_PAL)
    shape_item("reaper_soul", 51, SOUL, SOUL_PAL)
    # the cracks across the seal are dark, not holes
    sigil = [r[:r.index("0")] + r[r.index("0"):r.rindex("0") + 1].replace(".", "0") + r[r.rindex("0") + 1:] if "0" in r else r for r in SIGIL]
    shape_item("renegade_sigil", 61, sigil, SIGIL_PAL)
    shape_item("soul_vessel", 81, VESSEL, VESSEL_PAL)
    anvil()


if __name__ == "__main__":
    main()
