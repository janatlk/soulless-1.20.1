"""Film-strip contact sheet of a preview animation (run/screenshots/autotest_strip_*.png, one frame per 3 ticks).

    uv run --no-project --with pillow tools/strip_sheet.py <out.png> [frames] [cols]
"""
import glob
import sys

from PIL import Image, ImageDraw

out = sys.argv[1]
n = int(sys.argv[2]) if len(sys.argv) > 2 else 24
cols = int(sys.argv[3]) if len(sys.argv) > 3 else 8
files = sorted(glob.glob("run/screenshots/autotest_strip_*.png"))[:n]
cw, ch = 320, 380
rows = (len(files) + cols - 1) // cols
sheet = Image.new("RGB", (cols * cw, rows * ch))
for k, f in enumerate(files):
    a = Image.open(f).convert("RGB")
    w, h = a.size
    b = a.crop((int(w * 0.27), 0, int(w * 0.73), int(h * 0.9))).resize((cw, ch))
    ImageDraw.Draw(b).text((4, 4), f"{k * 0.15:.2f}s", fill=(255, 255, 0))
    sheet.paste(b, ((k % cols) * cw, (k // cols) * ch))
sheet.save(out)
print(len(files), "frames")
