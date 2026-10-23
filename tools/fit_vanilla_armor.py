"""Armour icons sized like vanilla's: each piece cut from an AI sheet (one row: helmet, chestplate, leggings, boots)
and resampled into the box the netherite piece fills on its 16x16 icon (read from the game's own jar, which is only
measured, never copied), keeping its proportions, centred in that box.

    python tools/fit_vanilla_armor.py SHEET OUT_DIR PREFIX
"""
import io
import sys
import zipfile
from collections import Counter
from pathlib import Path

from PIL import Image

JAR = Path.home() / '.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar'
PIECES = ['helmet', 'chestplate', 'leggings', 'boots']


def is_bg(p):
    r, g, b = p[:3]
    return r > 170 and b > 170 and g < 110


def vanilla_boxes():
    z = zipfile.ZipFile(JAR)
    return {n: Image.open(io.BytesIO(z.read(f'assets/minecraft/textures/item/netherite_{n}.png'))).convert('RGBA').getbbox()
            for n in PIECES}


def cut(sheet):
    """The pieces, left to right: split where whole columns are background, a split pair (boots) joined again."""
    rgb = sheet.convert('RGB')
    px = rgb.load()
    filled = [any(not is_bg(px[x, y]) and sum(px[x, y]) > 30 for y in range(0, rgb.height, 4)) for x in range(rgb.width)]
    runs, start = [], None
    for x, f in enumerate(filled + [False]):
        if f and start is None:
            start = x
        elif not f and start is not None:
            if x - start > rgb.width // 80:
                runs.append((start, x))
            start = None
    while len(runs) > len(PIECES):
        gaps = [runs[i + 1][0] - runs[i][1] for i in range(len(runs) - 1)]
        i = gaps.index(min(gaps))
        runs[i:i + 2] = [(runs[i][0], runs[i + 1][1])]
    out = []
    for a, b in runs:
        cell = sheet.crop((a, 0, b, sheet.height)).convert('RGBA')
        cp = cell.load()
        for y in range(cell.height):
            for x in range(cell.width):
                if is_bg(cp[x, y]):
                    cp[x, y] = (0, 0, 0, 0)
        out.append(cell.crop(cell.getbbox()))
    return out


def resample(art, w, h):
    """art down to w x h: each pixel the commonest colour near the middle of its patch."""
    out = Image.new('RGBA', (w, h))
    ap, op = art.load(), out.load()
    sx, sy = art.width / w, art.height / h
    for y in range(h):
        for x in range(w):
            votes = Counter()
            for fy in (0.3, 0.5, 0.7):
                for fx in (0.3, 0.5, 0.7):
                    p = ap[min(art.width - 1, int((x + fx) * sx)), min(art.height - 1, int((y + fy) * sy))]
                    votes[p if p[3] > 128 else (0, 0, 0, 0)] += 1
            op[x, y] = votes.most_common(1)[0][0]
    return out


def main():
    sheet, out_dir, prefix = Image.open(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
    out_dir.mkdir(parents=True, exist_ok=True)
    boxes = vanilla_boxes()
    for name, art in zip(PIECES, cut(sheet)):
        x0, y0, x1, y1 = boxes[name]
        bw, bh = x1 - x0, y1 - y0
        k = min(bw / art.width, bh / art.height)
        w, h = max(1, round(art.width * k)), max(1, round(art.height * k))
        icon = Image.new('RGBA', (16, 16))
        icon.paste(resample(art, w, h), (x0 + (bw - w) // 2, y0 + (bh - h) // 2))
        # the sheet's magenta, bled into the edges, made the outline's dark
        ip = icon.load()
        for y in range(16):
            for x in range(16):
                r, g, b, a = ip[x, y]
                if a and abs(r - b) < 45 and r > 130 and g < r - 60:
                    ip[x, y] = (18, 12, 26, a)
        icon.save(out_dir / f'{prefix}_{name}.png')
        print(name, 'box', (bw, bh), '->', (w, h))


if __name__ == '__main__':
    main()
