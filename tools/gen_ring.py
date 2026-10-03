"""Icon of the Soulsmith's Ring (16x16): a gold band seen from slightly above, a violet soul gem set on top.

    uv run --no-project --with pillow --with numpy tools/gen_ring.py
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulless/textures/item/soul_ring.png"


def hexc(h):
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)] + [255], np.uint8)


GOLD = [hexc(h) for h in ("4a3209", "7a5412", "a8751c", "d9a531", "f5d36a", "fff2b0")]
GEM = [hexc(h) for h in ("2a0b55", "4b1a8a", "7a35d6", "a96cff", "e3ccff")]
OUTLINE = hexc("1c1205")

img = np.zeros((16, 16, 4), np.uint8)
cx, cy = 7.5, 9.5
for y in range(16):
    for x in range(16):
        dx, dy = (x - cx) / 6.2, (y - cy) / 4.9
        r = math.hypot(dx, dy)
        rin = math.hypot((x - cx) / 3.6, (y - cy + 0.4) / 2.5)
        if r <= 1 and rin > 1:
            # lit from the upper left; the far (upper) side of the band is in shadow inside
            light = -dx * 0.5 - dy * 0.6
            t = 0.45 + 0.4 * light + (0.15 if r > 0.82 else 0) - (0.2 if rin < 1.25 and dy < 0 else 0)
            img[y, x] = GOLD[max(0, min(5, int(t * 6)))]
        elif r <= 1.12 and rin > 0.9:
            img[y, x] = OUTLINE
# the setting and the gem on top of the band
for y in range(1, 7):
    for x in range(5, 11):
        d = abs(x - 7.5) + abs(y - 3.8)
        if d <= 2.6:
            t = 0.5 + 0.35 * (-(x - 7.5) - (y - 3.8)) / 2.6
            img[y, x] = GEM[max(0, min(4, int(t * 5)))]
        elif d <= 3.3:
            img[y, x] = GOLD[4] if (x + y) % 2 == 0 else GOLD[2]   # prongs
img[3, 6] = GEM[4]
img[2, 7] = GEM[4]
OUT.parent.mkdir(parents=True, exist_ok=True)
Image.fromarray(img, "RGBA").save(OUT)
print("ring icon ->", OUT)
