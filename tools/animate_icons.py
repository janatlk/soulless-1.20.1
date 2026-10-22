"""Animates item icons in Minecraft's way: each texture becomes a vertical strip of frames with a .png.mcmeta beside it
(interpolated). The still icon (kept in art/icons/still/) is the first frame; the others light it differently:

  glow    - the glowing parts (bright, cold-coloured pixels) breathe brighter and dimmer
  sheen   - a band of light sweeps across the metal
  twinkle - glints flare up one after another
  flicker - a flame: the glow flickers unevenly and the top of the flame sways (the Reaper's Soul now has real frames: tools/flame_frames.py)

    python tools/animate_icons.py
"""
import colorsys
import math
import random
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ITEMS = ROOT / "src/main/resources/assets/soulless/textures/item"
STILL = ROOT / "art/icons/still"

# icon: (effects, frames, ticks per frame)
ANIM = {
    "wraith_iron_ingot": (["sheen", "glow"], 16, 2),
    "raw_wraith_iron": (["twinkle"], 12, 3),
    "soul_vessel": (["glow", "twinkle"], 16, 3),
    "renegade_sigil": (["ember"], 16, 3),
    "eye_of_lament": (["glow", "sheen"], 16, 3),
    "eye_of_drowned": (["glow", "sheen"], 16, 3),
    "drowned_gills": (["glow"], 16, 3),
}


def glowing(p):
    r, g, b, a = p
    if a < 128:
        return False
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return v > 0.45 and s > 0.35 and (0.4 < h < 0.75)   # cyan, teal, soul blue


def scale(p, k):
    r, g, b, a = p
    return (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), a)


def lighten(p, add):
    r, g, b, a = p
    return (min(255, r + add), min(255, g + add), min(255, b + add), a)


def frame(src, effects, i, n, rnd):
    im = src.copy()
    px, sp = im.load(), src.load()
    w, h = im.size
    t = i / n
    if "glow" in effects:
        k = 0.78 + 0.3 * (0.5 + 0.5 * math.sin(t * math.tau))
        for y in range(h):
            for x in range(w):
                if glowing(sp[x, y]):
                    px[x, y] = scale(sp[x, y], k)
    if "sheen" in effects:
        band = -w + t * (w * 3)   # a diagonal band crossing once per loop
        for y in range(h):
            for x in range(w):
                p = px[x, y]
                if p[3] < 128 or sum(p[:3]) < 90:
                    continue
                d = abs((x + y) - band)
                if d < 2.5:
                    px[x, y] = lighten(p, int(55 * (1 - d / 2.5)))
    if "twinkle" in effects:
        pts = [(x, y) for y in range(h) for x in range(w) if glowing(sp[x, y])]
        rnd.seed(7)
        rnd.shuffle(pts)
        for k, (x, y) in enumerate(pts[: max(1, len(pts) // 4)]):
            ph = (t * 3 + k * 0.37) % 1
            if ph < 0.25:
                px[x, y] = lighten(sp[x, y], int(120 * math.sin(ph / 0.25 * math.pi)))
    if "flicker" in effects:
        rnd.seed(i * 13 + 1)
        k = 0.8 + 0.35 * rnd.random()
        sway = round(math.sin(t * math.tau))
        out = Image.new("RGBA", (w, h))
        op = out.load()
        top = h // 2
        for y in range(h):
            for x in range(w):
                sx = x - (sway if y < top else 0)
                if 0 <= sx < w:
                    p = sp[sx, y]
                    op[x, y] = scale(p, k) if glowing(p) else p
        im = out
    if "ember" in effects:
        k = 0.85 + 0.25 * (0.5 + 0.5 * math.sin(t * math.tau))
        for y in range(h):
            for x in range(w):
                r, g, b, a = sp[x, y]
                if a > 128 and r > g + 40 and r > b + 40:
                    px[x, y] = (min(255, int(r * k)), min(255, int(g * (0.9 + 0.1 * k))), b, a)
    return im


def main():
    STILL.mkdir(parents=True, exist_ok=True)
    rnd = random.Random()
    for name, (effects, n, ticks) in ANIM.items():
        tex = ITEMS / f"{name}.png"
        still = STILL / f"{name}.png"
        if not still.exists():
            shutil.copy(tex, still)   # the first run keeps the still icon; later runs animate from it
        src = Image.open(still).convert("RGBA")
        w, h = src.size
        strip = Image.new("RGBA", (w, h * n))
        for i in range(n):
            strip.paste(frame(src, effects, i, n, rnd), (0, i * h))
        strip.save(tex)
        (ITEMS / f"{name}.png.mcmeta").write_text('{"animation": {"frametime": %d, "interpolate": true}}\n' % ticks)
        print(name, n, "frames")


if __name__ == "__main__":
    main()
