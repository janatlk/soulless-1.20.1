"""Turns the CC0 reptile skin (art/leviathan/source, RooMan93 on OpenGameArt) into the Leviathan's hide: grey scales
deepened to near-black with a cold blue sheen, scaled down so a scale is a few texels. Writes raw RGBA for build.js
(tools/leviathan/hide_rgba.bin: 128 x 192 - top, side and belly bands of 64 rows each).

    python tools/leviathan/prep_hide.py
"""
from pathlib import Path

from PIL import Image, ImageEnhance, ImageOps

HERE = Path(__file__).resolve().parent
src = Image.open(HERE.parent.parent / "art/leviathan/source/lizard_skin_CC0_RooMan93.png").convert("RGB")
grey = ImageOps.grayscale(src)
grey = ImageEnhance.Contrast(grey).enhance(1.6)


def band(img, size, lo, hi, tint):
    small = img.resize(size, Image.LANCZOS)
    out = Image.new("RGBA", size)
    px, sp = out.load(), small.load()
    for y in range(size[1]):
        for x in range(size[0]):
            v = sp[x, y] / 255
            k = lo + (hi - lo) * v ** 1.5
            out.putpixel((x, y), (int(k * tint[0]), int(k * tint[1]), int(k * tint[2]), 255))
    return out


top = band(grey.crop((0, 0, 512, 256)), (128, 64), 8, 62, (0.9, 0.95, 1.15))
side = band(grey.crop((0, 256, 512, 512)), (128, 64), 10, 66, (0.9, 0.95, 1.15))
belly = band(grey.crop((128, 0, 384, 128)), (128, 64), 24, 80, (0.85, 0.9, 1.05))
atlas = Image.new("RGBA", (128, 192))
atlas.paste(top, (0, 0))
atlas.paste(side, (0, 64))
atlas.paste(belly, (0, 128))
(HERE / "hide_rgba.bin").write_bytes(atlas.tobytes())
atlas.resize((512, 768), Image.NEAREST).save(HERE.parent.parent / "art/leviathan/hide_preview.png")
