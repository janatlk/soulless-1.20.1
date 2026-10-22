"""Builds the Reaper's Soul's animated icon from an AI sheet of flame frames in one row (art/icons/sheet_reaper_flame.png):
keys out the magenta, finds one box that holds the flame in every frame (so the frames stay aligned), samples each
frame on the same pixel grid and stacks them into Minecraft's frame strip with a .mcmeta.

    python tools/flame_frames.py [FRAMES]
"""
import sys
from collections import Counter
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from sprite_from_sheet import is_bg, pixel_size  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SHEET = ROOT / "art/icons/sheet_reaper_flame.png"
OUT = ROOT / "src/main/resources/assets/soulless/textures/item/reaper_soul.png"
n = int(sys.argv[1]) if len(sys.argv) > 1 else 9

sheet = Image.open(SHEET).convert("RGBA")
px = sheet.load()
for y in range(sheet.height):
    for x in range(sheet.width):
        if is_bg(px[x, y]):
            px[x, y] = (0, 0, 0, 0)
cw = sheet.width / n
cells = [sheet.crop((int(i * cw), 0, int((i + 1) * cw), sheet.height)) for i in range(n)]
boxes = [c.getbbox() for c in cells]
box = (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))
s = pixel_size(cells[0].crop(boxes[0]))
w, h = box[2] - box[0], box[3] - box[1]
nw, nh = round(w / s), round(h / s)
size = max(nw, nh) + 2
strip = Image.new("RGBA", (size, size * n))
for i, c in enumerate(cells):
    art = c.crop(box)
    ap = art.load()
    fr = Image.new("RGBA", (size, size))
    fp = fr.load()
    ox, oy = (size - nw) // 2, size - nh - 1   # the flame stands on the bottom
    for y in range(nh):
        for x in range(nw):
            cx, cy = min(w - 1, int((x + 0.5) * w / nw)), min(h - 1, int((y + 0.5) * h / nh))
            votes = Counter()
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    p = ap[min(w - 1, max(0, cx + dx * s // 4)), min(h - 1, max(0, cy + dy * s // 4))]
                    votes[p if p[3] > 128 else (0, 0, 0, 0)] += 1
            p = votes.most_common(1)[0][0]
            if p[3] <= 128 and ap[cx, cy][3] > 128:
                p = ap[cx, cy]
            if p[3] > 128:
                fp[ox + x, oy + y] = p
    strip.paste(fr, (0, i * size))
strip.save(OUT)
OUT.with_name(OUT.name + ".mcmeta").write_text('{"animation": {"frametime": 2, "interpolate": false}}\n')
print("pixel", s, "frame", (nw, nh), "->", size, "x", n)
