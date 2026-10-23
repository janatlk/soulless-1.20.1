"""The Reaper's Soul's animated icon: a hooded skull wrapped in cold soul-fire with a curling tail, cut from an AI
sheet (art/icons/sheet_reaper_soul_v2.png, bottom left) and animated here:
  - the fire's light rises through it in waves, and its outer tongues flicker in and out;
  - the loose sparks round it twinkle and drift up.
The still (cleaned of the sheet's magenta fringe) is kept in art/icons/still/reaper_soul.png.

    python tools/reaper_soul_icon.py
"""
import colorsys
import math
import random
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SHEET = ROOT / "art/icons/sheet_reaper_soul_v2.png"
STILL = ROOT / "art/icons/still/reaper_soul.png"
OUT = ROOT / "src/main/resources/assets/soulless/textures/item/reaper_soul.png"
FRAMES, TICKS = 12, 2
SPARK = (196, 244, 255, 255)


def hsv(p):
    return colorsys.rgb_to_hsv(p[0] / 255, p[1] / 255, p[2] / 255)


def cut():
    with tempfile.TemporaryDirectory() as d:
        subprocess.run([sys.executable, str(ROOT / "tools/sprite_from_sheet.py"), str(SHEET), "2", "2", d, "-", "-", "reaper_soul", "-"], check=True)
        im = Image.open(Path(d) / "reaper_soul.png").convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            p = px[x, y]
            if not p[3]:
                continue
            h, s, v = hsv(p)
            if 0.66 < h < 0.95 and s > 0.3:      # the magenta's fringe, and the sheet's violet sparks
                px[x, y] = SPARK if v > 0.85 and sum(p[:3]) > 600 else (0, 0, 0, 0)
    # lone pixels left by the fringe: drop those with no drawn neighbour
    for y in range(im.height):
        for x in range(im.width):
            if px[x, y][3] and px[x, y] != SPARK and not any(
                    0 <= x + dx < im.width and 0 <= y + dy < im.height and px[x + dx, y + dy][3]
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = (0, 0, 0, 0)
    return im


def fire(p):
    """Soul-fire: the bright teals and cyans (not the hood's dark blues or the skull's pale grey)."""
    h, s, v = hsv(p)
    return p[3] and 0.4 < h < 0.6 and s > 0.35 and v > 0.4


def frame(src, i, rnd):
    w, h = src.size
    sp = src.load()
    im = src.copy()
    px = im.load()
    t = i / FRAMES
    for y in range(h):
        for x in range(w):
            p = sp[x, y]
            if fire(p):
                # light rising up through the flame
                k = 0.86 + 0.24 * (0.5 + 0.5 * math.sin(math.tau * (t * 2) + y * 0.55 - x * 0.12))
                px[x, y] = (min(255, int(p[0] * k)), min(255, int(p[1] * k)), min(255, int(p[2] * k)), 255)
                # an outer tongue (open air above it) flickers out now and then
                if y > 0 and not sp[x, y - 1][3]:
                    rnd.seed(x * 97 + y * 31 + i * 7)
                    if rnd.random() < 0.22:
                        px[x, y] = (0, 0, 0, 0)
            elif p == SPARK:
                px[x, y] = (0, 0, 0, 0)
    # the sparks: each twinkles on its own clock and drifts up two pixels over the loop
    sparks = [(x, y) for y in range(h) for x in range(w) if sp[x, y] == SPARK]
    for n, (x, y) in enumerate(sparks):
        ph = (t + n * 0.29) % 1
        yy = y - int(ph * 3)
        if 0 <= yy < h and not px[x, yy][3] and ph < 0.8:
            a = int(255 * math.sin(ph / 0.8 * math.pi))
            px[x, yy] = SPARK[:3] + (max(140, a),)
    return im


def main():
    STILL.parent.mkdir(parents=True, exist_ok=True)
    still = cut()
    still.save(STILL)
    rnd = random.Random()
    w, h = still.size
    strip = Image.new("RGBA", (w, h * FRAMES))
    for i in range(FRAMES):
        strip.paste(frame(still, i, rnd), (0, i * h))
    strip.save(OUT)
    OUT.with_name(OUT.name + ".mcmeta").write_text('{"animation": {"frametime": %d, "interpolate": false}}\n' % TICKS)
    print("reaper_soul", w, "x", h, FRAMES, "frames")


if __name__ == "__main__":
    main()
