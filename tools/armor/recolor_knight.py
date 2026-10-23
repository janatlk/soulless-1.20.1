"""The Leviathan armour's textures: the copper knight set's pixel art recoloured - copper to the Leviathan's
near-black purple scales, the green patina to its violet glow, each by its brightness, so the drawing stays.

    python tools/armor/recolor_knight.py
"""
import colorsys
from PIL import Image

SRC = 'art/armor/knight/'
OUT = 'src/main/resources/assets/soulless/textures/armor/'
# dark to light
SCALE = [(8, 6, 12), (16, 12, 23), (26, 20, 36), (37, 29, 51), (52, 41, 71), (70, 55, 96)]
GLOW = [(46, 26, 70), (78, 44, 116), (118, 72, 172), (160, 110, 222), (200, 160, 245)]


def ramp(r, t):
    t = min(max(t, 0), 0.999) * (len(r) - 1)
    i = int(t)
    f = t - i
    a, b = r[i], r[min(i + 1, len(r) - 1)]
    return tuple(round(a[k] + (b[k] - a[k]) * f) for k in range(3))


def recolor(name, out):
    im = Image.open(SRC + name + '.png').convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
            green = 0.25 < h < 0.55 and s > 0.2
            # copper spans about 0.25..0.85 in brightness: stretch it over the scale ramp
            c = ramp(GLOW, (lum - 0.3) / 0.6) if green else ramp(SCALE, (lum - 0.22) / 0.62)
            px[x, y] = c + (a,)
    im.save(OUT + out)
    im.resize((im.width * 12, im.height * 12), Image.NEAREST).save(SRC + out.replace('.png', '_x12.png'))


import os
os.makedirs(OUT, exist_ok=True)
recolor('copper_knight_armor_layer_1', 'leviathan_layer_1.png')
recolor('copper_knight_armor_layer_2', 'leviathan_layer_2.png')
recolor('copper_knight', 'leviathan_details.png')
