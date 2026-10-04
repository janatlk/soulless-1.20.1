"""A per-tick strip of the katana autotest (one row per view, a frame per tick), for spotting jerks.

    uv run --no-project --with pillow tools/katana_strip.py <out.png> <from> <to> [views, e.g. side,front,fp]
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

out, a, b = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
views = sys.argv[4].split(",") if len(sys.argv) > 4 else ["side", "front", "fp"]
cw, ch = 150, 160
sheet = Image.new("RGB", (cw * (b - a + 1), ch * len(views)))
for row, view in enumerate(views):
    for col, t in enumerate(range(a, b + 1)):
        f = Path(f"run/screenshots/autotest_katana_{view}_{t:02d}.png")
        if not f.exists():
            continue
        img = Image.open(f).convert("RGB")
        w, h = img.size
        box = (0, 0, w, h) if view == "fp" else (int(w * 0.3), int(h * 0.05), int(w * 0.7), int(h * 0.75))
        c = img.crop(box).resize((cw, ch))
        ImageDraw.Draw(c).text((3, 3), f"{view} {t}", fill=(255, 255, 0))
        sheet.paste(c, (col * cw, row * ch))
sheet.save(out)
