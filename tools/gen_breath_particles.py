"""The Leviathan's breath particles (client.AbyssBreathParticle): a puff of breath in 8 frames, white so the
particle tints it, pixel art in the game's manner - a lumpy ball, densest in its middle, that loosens and frays
frame by frame into wisps.

    python tools/gen_breath_particles.py
"""
import math
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulless/textures/particle"
N, S = 8, 16


def puff(frame, seed):
    r = random.Random(seed)
    k = frame / (N - 1)
    # lumps: a few blobs round the middle, drifting apart as it frays
    blobs = [(8 + (r.random() - 0.5) * (3 + 5 * k), 8 + (r.random() - 0.5) * (3 + 5 * k), 3.2 + r.random() * 2.2 - k * 1.2) for _ in range(5)]
    im = Image.new("RGBA", (S, S))
    px = im.load()
    for y in range(S):
        for x in range(S):
            d = max(0.0, max(1 - math.hypot(x + 0.5 - bx, y + 0.5 - by) / br for bx, by, br in blobs))
            # holes eaten through it as it frays
            noise = r.random()
            d -= k * 0.55 * noise
            if d <= 0.08:
                continue
            # pixel-art steps: a bright heart, a mid body, a dim rim
            v = 255 if d > 0.6 else 205 if d > 0.35 else 150
            a = 255 if d > 0.35 else 170 if d > 0.18 else 90
            px[x, y] = (v, v, v, int(a * (1 - 0.5 * k)))
    return im


for f in range(N):
    puff(f, 7 + f * 3).save(OUT / f"abyss_breath_{f}.png")
print("ok")
