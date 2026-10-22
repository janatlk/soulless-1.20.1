"""Cuts pixel-art sprites out of an AI-made sheet (a grid of icons on flat magenta) and turns each back into a true
low-resolution sprite: keys the background out, finds the art's own pixel size from the runs of equal colour, samples
one value per art pixel, and pads it into a square 16 or 32 canvas.

    python tools/sprite_from_sheet.py SHEET COLS ROWS OUT_DIR name1 name2 ... [--cut-bottom FRACTION]

Names map to cells left to right, top to bottom; '-' skips a cell.
"""
import sys
from collections import Counter
from pathlib import Path

from PIL import Image


def is_bg(p):
    r, g, b = p[:3]
    return r > 170 and b > 170 and g < 110


def pixel_size(im):
    """The art's pixel size: the commonest length of runs of (near) equal colour, rows and columns."""
    px = im.load()
    w, h = im.size
    runs = Counter()
    for y in range(0, h, 3):
        run, last = 0, None
        for x in range(w):
            p = px[x, y]
            if p[3] == 0:
                last, run = None, 0
                continue
            if last is not None and sum(abs(a - b) for a, b in zip(p[:3], last[:3])) < 24:
                run += 1
            else:
                if run >= 3:
                    runs[run] += 1
                run = 1
            last = p
    if not runs:
        return 8
    # the smallest run length that is common: runs of 2 and 3 art pixels are multiples of it
    best = runs.most_common(6)
    return min(r for r, n in best if n >= best[0][1] * 0.35)


def crush(cell):
    cell = cell.convert("RGBA")
    px = cell.load()
    w, h = cell.size
    for y in range(h):
        for x in range(w):
            if is_bg(px[x, y]):
                px[x, y] = (0, 0, 0, 0)
    box = cell.getbbox()
    if not box:
        return None
    art = cell.crop(box)
    s = pixel_size(art)
    nw, nh = max(1, round(art.width / s)), max(1, round(art.height / s))
    out = Image.new("RGBA", (nw, nh))
    ap, op = art.load(), out.load()
    for y in range(nh):
        for x in range(nw):
            cx, cy = min(art.width - 1, int((x + 0.5) * art.width / nw)), min(art.height - 1, int((y + 0.5) * art.height / nh))
            # the majority colour of the art pixel's middle, so a stray edge pixel does not win
            votes = Counter()
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    p = ap[min(art.width - 1, max(0, cx + dx * s // 4)), min(art.height - 1, max(0, cy + dy * s // 4))]
                    votes[p if p[3] else (0, 0, 0, 0)] += 1
            p = votes.most_common(1)[0][0]
            op[x, y] = p if p[3] > 128 else (0, 0, 0, 0)
    # a square just big enough (one free pixel all round), so the sprite fills the inventory slot like vanilla's
    size = 16 if max(nw, nh) <= 14 else max(nw, nh) + 2
    canvas = Image.new("RGBA", (size, size))
    canvas.paste(out, ((size - out.width) // 2, (size - out.height) // 2))
    return canvas, s, (nw, nh)


def main():
    args = sys.argv[1:]
    cut = 0.0
    if "--cut-bottom" in args:
        i = args.index("--cut-bottom")
        cut = float(args[i + 1])
        del args[i:i + 2]
    sheet, cols, rows, out = Image.open(args[0]), int(args[1]), int(args[2]), Path(args[3])
    names = args[4:]
    out.mkdir(parents=True, exist_ok=True)
    cw, ch = sheet.width / cols, sheet.height / rows
    for k, name in enumerate(names):
        if name == "-":
            continue
        c, r = k % cols, k // cols
        cell = sheet.crop((int(c * cw), int(r * ch), int((c + 1) * cw), int((r + 1) * ch - ch * cut)))
        res = crush(cell)
        if res is None:
            print(name, "empty")
            continue
        img, s, native = res
        img.save(out / f"{name}.png")
        print(name, "pixel", s, "native", native, "->", img.size)


if __name__ == "__main__":
    main()
