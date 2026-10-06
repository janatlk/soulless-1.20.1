"""Sensei texture (64x64 overlay on the vanilla villager layout).

    uv run --no-project --with pillow --with numpy tools/gen_sensei.py [--preview out.png]

The sensei: an indigo keikogi with a white inner collar, a black obi, charcoal hakama, a white hachimaki with a red
sun on the brow, grey eyebrows, moustache and a long grey beard, grey hair with a dark topknot line. (The big
villager nose is hidden in code: client.SenseiRender.)
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/soulless"


def hexc(h, a=255):
    h = h.lstrip("#")
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a], np.uint8)


def noise(rng, base, amount=10):
    c = base.astype(int).copy()
    c[:3] = np.clip(c[:3] + rng.integers(-amount, amount + 1), 0, 255)
    return c.astype(np.uint8)


INDIGO, INDIGO_D, INDIGO_L = hexc("2b3352"), hexc("1d2238"), hexc("3a4570")
WHITE, WHITE_D = hexc("e9e6dc"), hexc("c9c4b5")
OBI, OBI_L = hexc("1a1414"), hexc("3a2c26")
HAKAMA, HAKAMA_D, HAKAMA_L = hexc("2a2a2e"), hexc("1c1c20"), hexc("3a3a40")
GREY, GREY_D, GREY_L = hexc("a9a9ad"), hexc("7d7d84"), hexc("d4d4d8")
RED = hexc("b3202a")


def sensei(rng):
    img = np.zeros((64, 64, 4), np.uint8)

    def fill(x0, y0, x1, y1, col, amt=8):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                img[y, x] = noise(rng, col, amt)

    # ---- body (box 8x12x6 at 16,20): faces rows 26..37; top 22..29 / bottom 30..37 at rows 20..25
    fill(22, 20, 29, 25, INDIGO)                   # shoulders (top)
    fill(30, 20, 37, 25, OBI)                      # bottom
    fill(16, 26, 43, 37, INDIGO)                   # all four sides
    # front (22..29): white inner collar in a V, the gi's crossed lapels darker, the obi at the waist
    for y in range(26, 33):
        d = y - 26
        for x in range(22, 30):
            if abs((x - 25.5)) < 3.6 - d * 0.5:
                img[y, x] = noise(rng, WHITE, 6)
            elif abs(x - 25.5) < 4.6 - d * 0.5:
                img[y, x] = noise(rng, INDIGO_L, 6)
    for x in range(16, 44):
        for y in (34, 35):
            img[y, x] = noise(rng, OBI, 5)
        img[36, x] = noise(rng, OBI_L, 5)
    # ---- arms (crossed: box at 44,22 and the middle at 40,38): wide indigo sleeves, white cuff lines
    fill(44, 22, 51, 25, INDIGO)
    fill(44, 26, 59, 33, INDIGO)
    for x in range(44, 60):
        img[33, x] = noise(rng, INDIGO_D, 4)
    fill(48, 38, 55, 41, INDIGO)
    fill(40, 42, 63, 45, INDIGO)
    for x in range(40, 64):
        img[45, x] = noise(rng, INDIGO_D, 4)
    # ---- legs (box 4x12x4 at 0,22): hakama
    fill(4, 22, 11, 25, HAKAMA)
    fill(0, 26, 15, 37, HAKAMA)
    for x in range(0, 16, 2):
        for y in range(26, 37):
            img[y, x] = noise(rng, HAKAMA_D, 4)    # pleats
    # ---- robe (box 8x20x6 at 0,38): the gi down to the hips, the obi, then the pleated hakama to the feet
    fill(6, 38, 13, 43, INDIGO)
    fill(0, 44, 27, 63, HAKAMA)
    for y in range(44, 52):
        for x in range(0, 28):
            img[y, x] = noise(rng, INDIGO, 8)
    for x in range(0, 28):
        img[48, x] = noise(rng, OBI, 4)
        img[49, x] = noise(rng, OBI, 4)
        img[50, x] = noise(rng, OBI_L, 4)
    for x in range(0, 28, 3):
        for y in range(51, 64):
            img[y, x] = noise(rng, HAKAMA_D, 3)
    for x in range(1, 28, 3):
        for y in range(51, 64):
            img[y, x] = noise(rng, HAKAMA_L, 3)
    # front of the gi: the V collar again, over the robe's front face (6..13)
    for y in range(44, 48):
        d = y - 44
        for x in range(6, 14):
            if abs(x - 9.5) < 2.4 - d * 0.6:
                img[y, x] = noise(rng, WHITE, 5)
    # ---- head (0,0): an open face - grey eyebrows, a thin moustache at the corners of the mouth and a short grey
    # goatee on the chin; grey at the temples
    for x in (9, 10, 13, 14):
        img[11, x] = noise(rng, GREY_L, 6)
    for x in (9, 10, 13, 14):
        img[15, x] = noise(rng, GREY, 6)            # moustache
    for x in (11, 12):
        img[16, x] = noise(rng, GREY, 6)
    for x in (10, 11, 12, 13):
        img[17, x] = noise(rng, GREY_D if x in (10, 13) else GREY, 6)   # goatee
    for y in range(9, 13):
        for x in (7, 16):
            img[y, x] = noise(rng, GREY, 8)         # temples
    # ---- hat layer (32,0): grey hair round the back and sides, a dark topknot line on top, a thin white
    # hachimaki high on the brow, its knot and tails at the back (the face itself is left open)
    for y in range(8, 14):
        for x in list(range(32, 40)) + list(range(48, 64)):
            img[y, x] = noise(rng, GREY, 10)
    fill(40, 0, 47, 7, GREY)
    for y in range(1, 7):
        img[y, 43] = noise(rng, GREY_D, 4)
        img[y, 44] = noise(rng, OBI_L, 4)
    for x in range(32, 64):
        img[8, x] = noise(rng, WHITE, 5)
    for x, y in ((59, 9), (60, 10), (58, 10), (59, 11), (61, 11)):
        img[y, x] = noise(rng, WHITE_D, 4)
    img[8, 59] = RED
    return img


def main():
    rng = np.random.default_rng(5)
    out = ASSETS / "textures/entity/npc"
    out.mkdir(parents=True, exist_ok=True)
    tex = Image.fromarray(sensei(rng), "RGBA")
    tex.save(out / "sensei.png")
    if "--preview" in sys.argv:
        p = Image.new("RGBA", (64 * 6 + 32 * 8 + 20, 64 * 6), (70, 70, 80, 255))
        big = tex.resize((384, 384), Image.NEAREST)
        p.paste(big, (0, 0), big)
        p.save(sys.argv[sys.argv.index("--preview") + 1])
    print("sensei texture written")


if __name__ == "__main__":
    main()
