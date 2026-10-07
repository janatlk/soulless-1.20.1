"""The Broken Legendary Katana's icon, made from the katana's own (tools/gen_katana.py writes soul_katana_icon.png):
the blade snapped a little above the middle, the upper piece knocked aside, the soul glow gone out of the steel.

    uv run --no-project --with pillow --with numpy tools/gen_broken_katana.py
"""
from pathlib import Path

import numpy as np
from PIL import Image

TEX = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulless/textures/item"


def main():
    src = np.array(Image.open(TEX / "soul_katana_icon.png").convert("RGBA")).astype(np.int32)
    h, w, _ = src.shape
    out = np.zeros_like(src)
    for y in range(h):
        for x in range(w):
            px = src[y, x]
            if px[3] == 0:
                continue
            s = x + (h - 1 - y)          # position along the blade (hilt bottom left ~5, point top right ~58)
            r, g, b = px[:3]
            if s > 20 and b > r + 20 and b > g + 20:   # violet glow in the steel: dead grey now
                v = (r + g + b) // 3
                px = np.array([v - 6, v - 6, v + 4, px[3]])
            if 35 <= s <= 37 + (x % 2):   # the jagged break
                continue
            if s > 37:                    # the upper piece, knocked down and aside
                ny, nx = y + 3, x + 1
                if 0 <= ny < h and 0 <= nx < w:
                    out[ny, nx] = px
                continue
            out[y, x] = px
    Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA").save(TEX / "broken_katana.png")
    print("broken_katana.png written")


if __name__ == "__main__":
    main()
