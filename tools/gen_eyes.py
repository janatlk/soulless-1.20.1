"""The seeking eyes (16x16): Eye of Lament (soul cyan on violet) and Eye of the Drowned (sea green).

    python tools/gen_eyes.py
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulless/textures/item"
EYE = [
    "................",
    "................",
    ".....oooooo.....",
    "...oo......oo...",
    "..o..iiiiii..o..",
    ".o..iiiiiiii..o.",
    ".o.iiippppiii.o.",
    "o..iipwwppiii..o",
    "o..iipwpppiii..o",
    ".o.iiippppiii.o.",
    ".o..iiiiiiii..o.",
    "..o..iiiiii..o..",
    "...oo......oo...",
    ".....oooooo.....",
    "................",
    "................",
]
EYES = {
    "eye_of_lament": {"o": (96, 226, 240), "i": (64, 44, 96), "p": (150, 230, 250), "w": (250, 255, 255)},
    "eye_of_drowned": {"o": (60, 170, 150), "i": (20, 70, 80), "p": (110, 240, 210), "w": (240, 255, 250)},
}

for name, pal in EYES.items():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    f = set()
    for y, r in enumerate(EYE):
        for x, c in enumerate(r):
            if c != ".":
                px[x, y] = pal[c] + (255,)
                f.add((x, y))
    # the dark white of the eye between the rim and the iris
    for y in range(16):
        row = [x for x in range(16) if (x, y) in f]
        if len(row) > 1:
            for x in range(row[0], row[-1] + 1):
                if (x, y) not in f:
                    px[x, y] = (18, 20, 30, 255)
                    f.add((x, y))
    for y in range(16):
        for x in range(16):
            if (x, y) not in f and any((x + a, y + b) in f for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = (10, 10, 16, 255)
    im.save(OUT / f"{name}.png")
