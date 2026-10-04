"""Sensei villager profession texture (64x64 overlay on the vanilla villager layout) and the 32x32 rolled
Scroll of Parrying icon.

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
    # ---- head (0,0): grey eyebrows, moustache and a long beard on the front face (8..15, rows 8..17)
    for x in (9, 10, 13, 14):
        img[11, x] = noise(rng, GREY_L, 6)
    for x in range(9, 15):
        img[15, x] = noise(rng, GREY, 8)            # moustache (where the nose was)
    for y in range(16, 18):
        for x in range(8, 16):
            img[y, x] = noise(rng, GREY if (x + y) % 3 else GREY_D, 8)
    img[15, 8] = noise(rng, GREY_D, 4)
    img[15, 15] = noise(rng, GREY_D, 4)
    # sideburns down the sides (right 0..7, left 16..23)
    for y in range(12, 18):
        for x in (6, 7, 16, 17):
            img[y, x] = noise(rng, GREY_D if y < 14 else GREY, 8)
    # ---- hat layer (32,0): grey hair at the back and sides, a dark topknot line on top; the hachimaki around
    for y in range(8, 14):
        for x in range(32, 64):
            front = 40 <= x <= 47
            if not front:
                img[y, x] = noise(rng, GREY, 10)
    fill(40, 0, 47, 7, GREY)
    for y in range(1, 7):
        img[y, 43] = noise(rng, GREY_D, 4)
        img[y, 44] = noise(rng, OBI_L, 4)
    for x in range(32, 64):
        img[9, x] = noise(rng, WHITE, 6)
        img[10, x] = noise(rng, WHITE_D, 6)
    # the red sun on the brow, and the knot's tails at the back
    for x, y in ((43, 9), (44, 9), (43, 10), (44, 10)):
        img[y, x] = RED
    for x, y in ((59, 11), (60, 12), (58, 12), (59, 13)):
        img[y, x] = noise(rng, WHITE_D, 4)
    return img


def scroll_icon(size=32):
    """A rolled scroll lying on the diagonal: aged parchment wound round a dark lacquered rod with brass knobs, tied
    in the middle with a red cord and a hanging tag sealed with the sensei's mark."""
    S = size * 8
    img = Image.new("RGBA", (S, S))
    px = img.load()
    a = np.radians(-38)
    ca, sa = np.cos(a), np.sin(a)
    cx, cy = S / 2, S / 2
    L, R = S * 0.36, S * 0.135          # half length, radius of the roll
    for y in range(S):
        for x in range(S):
            dx, dy = x - cx, y - cy
            u = dx * ca + dy * sa           # along the roll
            v = -dx * sa + dy * ca          # across
            col = None
            if abs(u) <= L and abs(v) <= R:
                shade = np.cos(v / R * np.pi / 2)            # round: light on the upper side
                lift = 0.55 + 0.45 * shade + (-v / R) * 0.18
                t = (u / L + 1) / 2
                base = np.array([228, 206, 158]) * (1 - 0.15 * abs(np.sin(t * 9)))   # the wound layers
                band = abs(u) < S * 0.03
                if band:
                    base = np.array([178, 34, 42])
                c = np.clip(base * lift, 0, 255)
                # the spiral edge of the last wrap
                if abs(v - (R * 0.55)) < S * 0.008 and not band:
                    c = c * 0.75
                col = (*[int(q) for q in c], 255)
            # the rod's knobs at both ends
            for end in (-1, 1):
                ku = u - end * (L + S * 0.035)
                if ku * ku / (S * 0.04) ** 2 + v * v / (R * 0.6) ** 2 <= 1:
                    lift = 0.6 + 0.4 * np.cos(v / (R * 0.6) * np.pi / 2)
                    col = (int(200 * lift), int(160 * lift), int(70 * lift), 255)
                elif abs(ku) < S * 0.012 and abs(v) < R * 0.85:
                    col = (40, 26, 24, 255)
            # the cord's tail and the seal tag hanging below the band
            tag_u, tag_v = u - S * 0.02, v - R * 1.9
            if abs(u) < S * 0.012 and R < v < R * 1.5:
                col = (160, 28, 36, 255)
            if abs(tag_u) < S * 0.06 and abs(tag_v) < S * 0.07:
                inner = abs(tag_u) < S * 0.03 and abs(tag_v) < S * 0.035
                col = (196, 36, 44, 255) if not inner else (120, 16, 24, 255)
            if col:
                px[x, y] = col
    small = img.resize((size, size), Image.LANCZOS)
    arr = np.array(small)
    arr[:, :, 3] = np.where(arr[:, :, 3] > 100, 255, 0)
    mask = arr[:, :, 3] > 0
    edge = np.zeros_like(mask)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= np.roll(np.roll(mask, dx, 1), dy, 0)
    edge &= ~mask
    arr[edge] = (40, 24, 18, 255)
    return Image.fromarray(arr, "RGBA")


def main():
    rng = np.random.default_rng(5)
    out = ASSETS / "textures/entity/villager/profession"
    out.mkdir(parents=True, exist_ok=True)
    tex = Image.fromarray(sensei(rng), "RGBA")
    tex.save(out / "sensei.png")
    icon = scroll_icon()
    icon.save(ASSETS / "textures/item/parry_scroll.png")
    if "--preview" in sys.argv:
        p = Image.new("RGBA", (64 * 6 + 32 * 8 + 20, 64 * 6), (70, 70, 80, 255))
        big = tex.resize((384, 384), Image.NEAREST)
        p.paste(big, (0, 0), big)
        ic = icon.resize((256, 256), Image.NEAREST)
        p.paste(ic, (404, 0), ic)
        p.save(sys.argv[sys.argv.index("--preview") + 1])
    print("sensei texture + scroll icon written")


if __name__ == "__main__":
    main()
