"""Contact sheet of the latest dev tuning screenshots (run/screenshots/autotest_tune_*.png).

    uv run --no-project --with pillow tools/tune_sheet.py <out.png>
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

shots = sorted((Path(__file__).resolve().parent.parent / "run/screenshots").glob("autotest_tune_*.png"))
ims, ok = [], []
for p in shots:
    try:
        ims.append(Image.open(p).convert("RGB").resize((640, 360)))
        ok.append(p)
    except Exception:
        pass  # being written right now
shots = ok
sheet = Image.new("RGB", (1920, 360 * ((len(ims) + 2) // 3)))
draw = ImageDraw.Draw(sheet)
for i, (p, im) in enumerate(zip(shots, ims)):
    x, y = (i % 3) * 640, (i // 3) * 360
    sheet.paste(im, (x, y))
    draw.text((x + 8, y + 8), p.stem.replace("autotest_tune_", ""), fill=(255, 255, 0))
sheet.save(sys.argv[1])
