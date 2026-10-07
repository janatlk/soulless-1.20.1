"""The Renegade's particles (textures + particle json): his crimson embers, the dark smoke of his blinks, red sparks,
the crescent of a slash, the shock ring of a landing, the ash of the dark astral.

    uv run --no-project --with pillow --with numpy tools/gen_renegade_particles.py [--preview out.png]
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/soulless"
TEX = ASSETS / "textures/particle"
PJ = ASSETS / "particles"
rng = np.random.default_rng(7)


def col(h, a=255):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)] + [a], float)


def ramp(t, stops):
    """t in 0..1 over [(t, colour)]."""
    t = min(1.0, max(0.0, t))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            k = (t - t0) / max(1e-9, t1 - t0)
            return c0 * (1 - k) + c1 * k
    return stops[-1][1]


def noise(n, seed, scale=3):
    r = np.random.default_rng(seed)
    g = r.random((scale + 1, scale + 1))
    ys, xs = np.mgrid[0:n, 0:n] / n * scale
    x0, y0 = xs.astype(int), ys.astype(int)
    fx, fy = xs - x0, ys - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = g[y0, x0] * (1 - fx) + g[y0, x0 + 1] * fx
    b = g[y0 + 1, x0] * (1 - fx) + g[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy


FLAME = [(0.0, col("fff1e6")), (0.25, col("ff8a7a")), (0.5, col("ff2a40")), (0.8, col("a3101f")), (1.0, col("4a0610", 0))]


def ember(i, n=8):
    """A crimson flame tongue, burning down."""
    s = 8
    img = np.zeros((s, s, 4))
    k = i / (n - 1)
    h = 6.5 * (1 - 0.6 * k)      # height of the tongue
    w = 2.6 * (1 - 0.45 * k)
    for y in range(s):
        for x in range(s):
            u = (x + 0.5 - s / 2) / w
            v = (s - 1 - y + 0.5 - 0.8) / h          # 0 at the base, 1 at the tip
            if v < -0.15 or v > 1:
                continue
            width = (1 - v) ** 0.7 if v > 0 else 1 + v * 3
            d = abs(u) / max(0.05, width)
            if d > 1:
                continue
            t = 0.15 + 0.6 * d + 0.35 * v + 0.35 * k
            img[y, x] = ramp(t, FLAME)
    return img


def smoke(i, n=8):
    """Inky violet-black smoke: a soft puff that spreads and comes apart."""
    s = 16
    k = i / (n - 1)
    nz = noise(s, 11 + i, 4) * 0.6 + noise(s, 40 + i, 7) * 0.4
    ys, xs = np.mgrid[0:s, 0:s]
    r = np.hypot(xs + 0.5 - s / 2, ys + 0.5 - s / 2) / (s / 2)
    edge = 0.55 + 0.4 * k
    a = np.clip((edge - r) / 0.35 + (nz - 0.5) * 1.2, 0, 1) * (1 - 0.55 * k)
    a *= np.clip(nz * 1.6 - 0.25 * k, 0, 1)
    img = np.zeros((s, s, 4))
    base = col("201028")
    hi = col("5a2a66")
    for c in range(3):
        img[..., c] = base[c] * (1 - nz) + hi[c] * nz
    img[..., 3] = np.clip(a * 1.5, 0, 1) * 240
    return img


def spark(i, n=4):
    """A hot red spark: a bright cross, burning out."""
    s = 8
    img = np.zeros((s, s, 4))
    k = i / (n - 1)
    arm = 4.0 * (1 - 0.5 * k)
    for y in range(s):
        for x in range(s):
            dx, dy = abs(x + 0.5 - s / 2), abs(y + 0.5 - s / 2)
            d = min(dx / arm + dy / 1.1, dy / arm + dx / 1.1)
            if d > 1:
                continue
            img[y, x] = ramp(0.1 + d * 0.6 + k * 0.4, FLAME)
    return img


def slash(i, n=6):
    """The crescent of a cut, flaring and thinning away."""
    s = 32
    img = np.zeros((s, s, 4))
    k = i / (n - 1)
    ys, xs = np.mgrid[0:s, 0:s]
    cx, cy = s * 0.5, s * 0.78
    r = np.hypot(xs + 0.5 - cx, ys + 0.5 - cy)
    ang = np.arctan2(cy - (ys + 0.5), xs + 0.5 - cx)        # 0 right, pi/2 up
    R = s * 0.42
    span = (ang > math.radians(15)) & (ang < math.radians(165))
    along = np.clip((ang - math.radians(15)) / math.radians(150), 0, 1)
    thick = (3.2 * (1 - 0.7 * k)) * np.sin(math.pi * along) ** 0.6 + 0.3
    d = np.abs(r - R) / thick
    m = span & (d < 1)
    t = 0.05 + d * 0.6 + k * 0.45
    for y, x in zip(*np.nonzero(m)):
        img[y, x] = ramp(t[y, x], FLAME)
    return img


def burst(i, n=6):
    """A shock ring thrown out from a blow on the ground."""
    s = 32
    img = np.zeros((s, s, 4))
    k = i / (n - 1)
    ys, xs = np.mgrid[0:s, 0:s]
    r = np.hypot(xs + 0.5 - s / 2, ys + 0.5 - s / 2)
    R = 4 + 11 * k ** 0.7
    th = 3.0 * (1 - 0.6 * k) + 0.6
    d = np.abs(r - R) / th
    nz = noise(s, 90 + i, 6)
    m = d < 1
    inner = r < R
    for y, x in zip(*np.nonzero(m)):
        c = ramp(0.05 + d[y, x] * 0.55 + k * 0.4 + (nz[y, x] - 0.5) * 0.3, FLAME)
        img[y, x] = c
    # a faint glow inside the ring at first
    for y, x in zip(*np.nonzero(inner & ~m)):
        if k < 0.5:
            c = col("ff2a40", 70 * (1 - k * 2))
            img[y, x] = c
    return img


def ash(i):
    """A flake of ash, a hot speck on some."""
    s = 8
    img = np.zeros((s, s, 4))
    r = np.random.default_rng(300 + i)
    pts = {(3, 3), (4, 3), (3, 4)}
    for _ in range(2 + i % 3):
        x, y = list(pts)[r.integers(len(pts))]
        pts.add((min(6, max(1, x + r.integers(-1, 2))), min(6, max(1, y + r.integers(-1, 2)))))
    for x, y in pts:
        g = 40 + r.integers(0, 30)
        img[y, x] = (g, g - 8, g - 4, 230)
    if i % 2 == 0:
        x, y = sorted(pts)[0]
        img[y, x] = col("c4202e")
    return img


SETS = {
    "blood_ember": [ember(i) for i in range(8)],
    "dark_smoke": [smoke(i) for i in range(8)],
    "red_spark": [spark(i) for i in range(4)],
    "crimson_slash": [slash(i) for i in range(6)],
    "crimson_burst": [burst(i) for i in range(6)],
    "ash_flake": [ash(i) for i in range(4)],
}


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    for name, frames in SETS.items():
        names = []
        for i, f in enumerate(frames):
            Image.fromarray(np.clip(f, 0, 255).astype(np.uint8), "RGBA").save(TEX / f"{name}_{i}.png")
            names.append(f"soulless:{name}_{i}")
        (PJ / f"{name}.json").write_text(json.dumps({"textures": names}, indent=2) + "\n")
    if "--preview" in sys.argv:
        rows = []
        for name, frames in SETS.items():
            tiles = []
            for f in frames:
                big = np.kron(np.clip(f, 0, 255), np.ones((64 // f.shape[0], 64 // f.shape[0], 1)))
                bg = np.zeros((64, 64, 3)) + 40
                a = big[..., 3:4] / 255
                tiles.append((bg * (1 - a) + big[..., :3] * a).astype(np.uint8))
            while len(tiles) < 8:
                tiles.append(np.zeros((64, 64, 3), np.uint8))
            rows.append(np.concatenate(tiles, axis=1))
        Image.fromarray(np.concatenate(rows, axis=0)).save(sys.argv[sys.argv.index("--preview") + 1])
    print("particles:", ", ".join(f"{k} x{len(v)}" for k, v in SETS.items()))


if __name__ == "__main__":
    main()
