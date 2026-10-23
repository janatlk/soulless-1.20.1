"""The katana's inventory icon (32x32: a long, slightly curved blade, a round guard, a wrapped grip). (It used to make
the Wraith Iron ore textures too - ore() is kept; the ores are now tools/gen_ore_veins.py.)

    python tools/gen_katana_icon_ores.py
"""
import math
import random
from pathlib import Path

from PIL import Image

TEX = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulless/textures"


def katana():
    N = 32
    a, b = (2.5, 29.5), (30.0, 2.0)
    d = (b[0] - a[0], b[1] - a[1])
    L = math.hypot(*d)
    dx, dy = d[0] / L, d[1] / L
    up = (dy, -dx)   # perpendicular toward the upper left: the spine side
    if up[0] + up[1] > 0:
        up = (-up[0], -up[1])
    pts = []
    for i in range(801):
        t = i / 800
        bow = math.sin(math.pi * max(0, (t - 0.3) / 0.7)) * 0.9 if t > 0.3 else 0
        pts.append((t, a[0] + d[0] * t + up[0] * bow, a[1] + d[1] * t + up[1] * bow))

    def width(t):
        if t < 0.03: return 1.3           # the pommel cap
        if t < 0.25: return 1.1           # the grip
        if t < 0.285: return 2.7          # the guard
        if t < 0.31: return 1.2           # the collar
        return 1.05 - 0.6 * max(0, (t - 0.86) / 0.14)   # the blade, to a point

    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = im.load()
    part = {}
    for y in range(N):
        for x in range(N):
            cx, cy = x + 0.5, y + 0.5
            best, bt, side = 1e9, 0, 0
            for t, x0, y0 in pts:
                dd = (cx - x0) ** 2 + (cy - y0) ** 2
                if dd < best:
                    best, bt = dd, t
                    side = (cx - x0) * up[0] + (cy - y0) * up[1]
            dist = math.sqrt(best)
            if bt > 0.995 and dist > 0.5:
                continue
            if dist <= width(bt):
                part[(x, y)] = (bt, side, dist / max(0.01, width(bt)))
    for (x, y), (t, side, rel) in part.items():
        if t < 0.03:
            c = (206, 170, 84)
        elif t < 0.25:
            # the wrap: dark cord crossing over gold-silk diamonds
            k = (t * 40 + (0.5 if side > 0 else 0)) % 1
            c = (52, 28, 62) if k < 0.55 else (196, 158, 80)
        elif t < 0.285:
            c = (214, 178, 92) if rel > 0.7 else (70, 56, 44)
        elif t < 0.31:
            c = (224, 190, 100)
        else:
            if side > 0.3:
                c = (112, 68, 176)       # the spine
            elif side < -0.3:
                c = (238, 224, 255)      # the cutting edge
            else:
                c = (178, 132, 236)
            if 0.55 < t < 0.6 and side < 0.2:
                c = (255, 255, 255)      # a glint along the edge
        px[x, y] = c + (255,)
    for y in range(N):
        for x in range(N):
            if (x, y) not in part and any((x + i, y + j) in part for i, j in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = (20, 12, 30, 255)
    im.save(TEX / "item" / "soul_katana_icon.png")


def noise(seed, n=16, cell=4):
    r = random.Random(seed)
    g = [[r.random() for _ in range(n // cell + 2)] for _ in range(n // cell + 2)]

    def at(x, y):
        gx, gy = x / cell, y / cell
        x0, y0 = int(gx), int(gy)
        fx, fy = gx - x0, gy - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        top = g[y0][x0] * (1 - fx) + g[y0][x0 + 1] * fx
        bot = g[y0 + 1][x0] * (1 - fx) + g[y0 + 1][x0 + 1] * fx
        return top * (1 - fy) + bot * fy
    return at


def stone(seed, tones, layered):
    r = random.Random(seed)
    big, small = noise(seed, cell=4), noise(seed + 7, cell=2)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            v = big(x, y) * 0.6 + small(x, y) * 0.4
            if layered:
                v = v * 0.55 + (0.5 + 0.5 * math.sin(y * 1.9 + big(x, 0) * 2)) * 0.45
            v += (r.random() - 0.5) * 0.12
            i = max(0, min(len(tones) - 1, int(v * len(tones))))
            px[x, y] = tones[i] + (255,)
    return im


CLUSTERS = [  # crystal clusters, as vanilla ores lay theirs
    [(3, 2), (4, 2), (2, 3), (3, 3), (4, 3), (3, 4)],
    [(10, 3), (11, 3), (11, 4), (12, 4), (10, 4), (11, 5)],
    [(6, 7), (7, 7), (6, 8), (7, 8), (8, 8), (7, 9)],
    [(2, 11), (3, 11), (3, 12), (4, 12), (2, 12)],
    [(11, 10), (12, 10), (12, 11), (13, 11), (11, 11), (12, 12), (13, 12)],
]
HI, MID, LO, RIM = (232, 252, 255), (128, 206, 226), (58, 124, 152), (30, 40, 52)


def ore(name, base):
    im = base.copy()
    px = im.load()
    for cl in CLUSTERS:
        s = set(cl)
        for x, y in cl:   # a dark rim below and right, as light falls from the top left
            for i, j in ((1, 0), (0, 1), (1, 1)):
                q = (x + i, y + j)
                if q not in s and 0 <= q[0] < 16 and 0 <= q[1] < 16:
                    px[q] = RIM + (255,)
        for x, y in cl:
            lit = (x - 1, y) not in s or (x, y - 1) not in s
            dark = (x + 1, y) not in s and (x, y + 1) not in s
            px[x, y] = (HI if lit and not dark else LO if dark else MID) + (255,)
    im.save(TEX / "block" / f"{name}.png")


if __name__ == "__main__":
    katana()
    # the ores are no longer made here: veins over the game's own stone (tools/gen_ore_veins.py)
