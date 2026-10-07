"""Quest items of the sensei's line: the Training Katana (the Legendary Katana's own model and icon, recoloured as a
plain wooden bokken - ash-pale wood, a dark cord wrap) and the Scroll of the Family History (a rolled, aged scroll).

    uv run --no-project --with pillow --with numpy tools/gen_quest_items.py
"""
import colorsys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/soulless"
WOOD = [(92, 64, 40), (128, 92, 58), (164, 124, 82), (196, 160, 112), (222, 192, 146), (240, 218, 178)]
CORD = [(28, 20, 16), (46, 32, 24), (66, 46, 34)]


def ramp(colors, t):
    t = min(0.999, max(0.0, t))
    return colors[int(t * len(colors))]


def to_wood(path_in, path_out):
    """Steel and violet glow become pale wood by brightness; the dark grip becomes a dark cord wrap."""
    src = np.array(Image.open(path_in).convert("RGBA"))
    out = src.copy()
    h, w, _ = src.shape
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[y, x]
            if a == 0:
                continue
            hh, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if l < 0.16:          # outlines and the darkest grip stay dark: a cord wrap
                c = ramp(CORD, l / 0.16)
            else:
                grain = ((x * 7 + y * 3) % 11) / 11 * 0.08
                c = ramp(WOOD, (l - 0.16) / 0.7 + grain - 0.04)
            out[y, x] = (*c, a)
    Image.fromarray(out, "RGBA").save(path_out)


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
    tex = ASSETS / "textures/item"
    to_wood(tex / "soul_katana.png", tex / "training_katana.png")
    to_wood(tex / "soul_katana_icon.png", tex / "training_katana_icon.png")
    scroll_icon().save(tex / "family_scroll.png")
    print("training katana + family scroll written")


if __name__ == "__main__":
    main()
