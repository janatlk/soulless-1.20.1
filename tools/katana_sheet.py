"""Contact sheet of the katana autotest (run/screenshots/autotest_katana_<view>_<tick>.png).

    uv run --no-project --with pillow tools/katana_sheet.py <view> <out.png> [ticks, e.g. 0,4,5,6,7,8]
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

view, out = sys.argv[1], sys.argv[2]
ticks = [int(t) for t in sys.argv[3].split(",")] if len(sys.argv) > 3 else list(range(0, 33, 2))
files = [(t, Path(f"run/screenshots/autotest_katana_{view}_{t:02d}.png")) for t in ticks]
files = [(t, f) for t, f in files if f.exists()]
cols = min(6, len(files))
cw, ch = 300, 340
rows = (len(files) + cols - 1) // cols
sheet = Image.new("RGB", (cols * cw, rows * ch))
for k, (t, f) in enumerate(files):
    a = Image.open(f).convert("RGB")
    w, h = a.size
    box = (0, 0, w, h) if view == "fp" else (int(w * 0.28), int(h * 0.02), int(w * 0.72), int(h * 0.98))
    b = a.crop(box).resize((cw, ch))
    ImageDraw.Draw(b).text((4, 4), f"t{t}", fill=(255, 255, 0))
    sheet.paste(b, ((k % cols) * cw, (k // cols) * ch))
sheet.save(out)
print(len(files), "frames")
