"""The Leviathan armour: vanilla armour shapes (the helmet, chest, sleeves, leggings and boots a size over the body)
with a texture after the concept (art/armor/final_B.png), plus a few details on top: the Leviathan's snout and jaw
over the helmet with fangs and whiskers, wing pauldrons, a back ridge, knee plates, webbed toe fins.

    python tools/armor/gen_armor.py

Writes the atlas (src/main/resources/assets/soulless/textures/armor/leviathan_armor.png, 2 texels a pixel) and
tools/armor/armor_cubes.json, which tools/armor/build_armor.js turns into the GeckoLib model in Blockbench.
"""
import json
import random

from PIL import Image

T = 2                      # texels per model pixel
W = H = 256
rnd = random.Random(5)

O, D, M, L, HI = (9, 7, 13), (18, 14, 25), (28, 22, 38), (40, 32, 54), (58, 46, 78)
V, VB = (78, 50, 110), (132, 88, 182)
TAN, TAND = (172, 142, 98), (128, 102, 68)
FANG, FANGD = (222, 210, 178), (160, 146, 112)
AMB, AMBD = (255, 170, 50), (200, 100, 20)
CY = (120, 205, 215)


# ------------------------------------------------------------------ face painters: (w, h) texels -> rows of colours
def blank(w, h, c):
    return [[c] * w for _ in range(h)]


def scales(w, h, seed=0):
    """Overlapping scales in rows half a scale over: a lit top, a dark rim, now and then one a shade off."""
    r = random.Random(seed)
    img = blank(w, h, M)
    sw, sh = 6, 4
    for y in range(h):
        row = y // sh
        for x in range(w):
            xx = (x + (row % 2) * (sw // 2)) % sw
            yy = y % sh
            c = HI if yy == 0 and 0 < xx < sw - 1 else L if yy == 1 and 0 < xx < sw - 1 else D if yy == sh - 1 else M
            if xx == 0 or (yy == sh - 1 and xx in (1, sw - 1)):
                c = O
            img[y][x] = c
    for _ in range(w * h // 40):
        x, y = r.randrange(w), r.randrange(h)
        if img[y][x] == M:
            img[y][x] = L if r.random() < 0.5 else D
    if r.random() < 0.7 and w > 6:
        img[r.randrange(h)][r.randrange(w)] = CY
    return img


def chevrons(w, h):
    """Rows of plates in a V down the chest, a violet seam under each, darker toward the sides."""
    img = blank(w, h, M)
    for y in range(h):
        for x in range(w):
            dx = abs(x - (w - 1) / 2)
            yy = int(y + dx * 0.55) % 6
            c = HI if yy == 0 else L if yy == 1 else V if yy == 5 and dx < w * 0.3 else O if yy == 5 else D if yy == 4 else M
            if dx > w / 2 - 2 and c == M:
                c = D
            img[y][x] = c
    return img


def plates(w, h, band=5):
    """Horizontal bands: a lit lip, the plate, a dark seam with violet in it."""
    img = blank(w, h, M)
    for y in range(h):
        yy = y % band
        for x in range(w):
            c = HI if yy == 0 else L if yy == 1 else O if yy == band - 1 else M
            if yy == band - 1 and (x // 3) % 3 == 1:
                c = V
            img[y][x] = c
    return img


def frame(img, top=None):
    """A dark rim round a face, a lit edge along its top - so the shapes read."""
    h, w = len(img), len(img[0])
    for x in range(w):
        img[h - 1][x] = O
        if top:
            img[0][x] = top
    for y in range(h):
        img[y][0] = O
        img[y][w - 1] = O
    return img


def helmet_front(w, h):
    """Under the snout: the open maw - a black throat framed by fangs, the face in the dark of it."""
    img = scales(w, h, 3)
    m0, m1 = 2, w - 3
    for y in range(int(h * 0.15), h - 2):
        for x in range(m0, m1):
            img[y][x] = (6, 4, 8) if (x + y) % 7 else (12, 8, 16)
    for x in range(m0, m1):
        if x % 2 == 0:
            for k in range(3 if x % 4 == 0 else 2):
                img[int(h * 0.15) + k][x] = FANG if k < 2 else FANGD
                img[h - 3 - k][x] = FANG if k < 2 else FANGD
    return frame(img)


def helmet_side(w, h):
    img = scales(w, h, 4)
    # gill slits toward the back
    for i in range(3):
        x = w - 5 - i * 3
        for y in range(h // 3, h - 3):
            img[y][x] = O
            img[y][x - 1] = V if y % 3 else D
    return frame(img, HI)


def snout_side(w, h):
    img = scales(w, h, 5)
    # the eye toward the back of the snout, an amber slit with a dark brow over it
    ex, ey = w - 6, 2
    for x in range(ex - 1, ex + 4):
        img[ey - 1][x] = O
    for x in range(ex, ex + 3):
        img[ey][x] = AMB
        img[ey + 1][x] = AMBD
    # the jaw line: a row of fang tips along the bottom
    for x in range(1, w - 1):
        img[h - 1][x] = FANG if x % 2 else O
    return frame(img, HI)


def snout_front(w, h):
    img = scales(w, h, 6)
    for x in (w // 2 - 3, w // 2 + 2):
        img[2][x] = O
        img[2][x + 1] = O
    for x in range(1, w - 1):
        img[h - 1][x] = FANG if x % 2 else O
    return frame(img, HI)


def jaw_top(w, h):
    """The lower jaw's floor: a dark tongue, fangs along the edges."""
    img = blank(w, h, (14, 9, 18))
    for y in range(h):
        img[y][0] = img[y][w - 1] = FANG if y % 2 else FANGD
    for x in range(w):
        img[0][x] = FANG if x % 2 else FANGD
    return img


def fang(w, h):
    img = blank(w, h, FANG)
    for y in range(h):
        img[y][w - 1] = FANGD
    img[h - 1] = [FANGD] * w
    return img


def membrane(w, h):
    """A fin: violet webbing between dark rays."""
    img = blank(w, h, VB)
    for y in range(h):
        for x in range(w):
            if x % 4 == 0:
                img[y][x] = O
            elif y == 0:
                img[y][x] = (170, 120, 220)
            elif y > h - 2:
                img[y][x] = V
    return img


def dark_fin(w, h):
    img = scales(w, h, 9)
    for y in range(h):
        for x in range(w):
            if (x + y) % 5 == 0:
                img[y][x] = O
    return frame(img, HI)


PAINT = {
    'scales': lambda w, h: frame(scales(w, h, w * 31 + h), HI), 'scales_up': lambda w, h: frame(scales(w, h, w + h * 7)),
    'chev': lambda w, h: frame(chevrons(w, h)), 'plates': lambda w, h: frame(plates(w, h)),
    'helm_front': helmet_front, 'helm_side': helmet_side, 'snout_side': snout_side, 'snout_front': snout_front,
    'jaw_top': jaw_top, 'fang': fang, 'memb': membrane, 'dfin': dark_fin,
    'tan': lambda w, h: frame([[TAN if y else (196, 170, 120) for _ in range(w)] for y in range(h)]),
    'amber': lambda w, h: [[AMB if y < h / 2 else AMBD for _ in range(w)] for y in range(h)],
    'whisk': lambda w, h: [[L if x == 0 else D for x in range(w)] for _ in range(h)],
    'throat': lambda w, h: blank(w, h, (6, 4, 8)),
    'seam': lambda w, h: [[(112, 76, 150) if y == 0 else V if y < h - 1 else (46, 30, 66) for _ in range(w)] for y in range(h)],
}

# ------------------------------------------------------------------ the model
cubes = []      # {bone, from, to, faces: {dir: style}, rot?, origin?}
SH = {'scales', 'fang', 'whisk', 'amber', 'memb', 'seam', 'throat'}


def cube(bone, f, t, style='scales', rot=None, origin=None, **faces):
    fs = {d: faces.get(d, style if d != 'up' or style != 'scales' else 'scales_up') for d in ('north', 'south', 'east', 'west', 'up', 'down')}
    cubes.append({'bone': bone, 'from': f, 'to': t, 'faces': fs, 'rot': rot, 'origin': origin})


def mirror(bone_r, bone_l, f, t, style='scales', rot=None, origin=None, **faces):
    cube(bone_r, f, t, style, rot, origin, **faces)
    sw = dict(faces)
    if 'east' in faces or 'west' in faces:
        sw['east'], sw['west'] = faces.get('west', style), faces.get('east', style)
    cube(bone_l, [-t[0], f[1], f[2]], [-f[0], t[1], t[2]], style, rot and [rot[0], -rot[1], -rot[2]],
         origin and [-origin[0], origin[1], origin[2]], **sw)


HB = 'armorHead'
# the helmet: the vanilla shell a size over the head, the maw at its front
cube(HB, [-5, 23, -5], [5, 33, 5], north='helm_front', east='helm_side', west='helm_side', down='throat')
cube(HB, [-5.2, 23, 4], [5.2, 24.2, 5.4], 'seam')
# the snout over the brow: the Leviathan's upper jaw, out in front of the face
cube(HB, [-4.5, 29.5, -10.5], [4.5, 33, -5], north='snout_front', east='snout_side', west='snout_side', down='jaw_top')
cube(HB, [-3.5, 33, -9], [3.5, 34.2, -3], 'scales')
mirror(HB, HB, [-5.4, 32, -6], [-4.2, 34.6, 0], 'dfin', [0, 0, 14], [-4.8, 32, -3])        # brow ridges
# the lower jaw under the chin
cube(HB, [-4.5, 21.6, -10], [4.5, 23.4, -5], up='jaw_top', north='snout_front', east='scales', west='scales')
# fangs from the snout down and from the jaw up, round the front and the sides
for i in range(5):
    x = -3.6 + i * 1.8
    cube(HB, [x, 27.6 + (i % 2) * 0.6, -10.4], [x + 0.6, 29.5, -9.8], 'fang')
    cube(HB, [x + 0.6, 23.4, -9.9], [x + 1.2, 25 - (i % 2) * 0.5, -9.3], 'fang')
for i in range(3):
    z = -9 + i * 1.5
    mirror(HB, HB, [-4.3, 27.8 + (i % 2) * 0.5, z], [-3.7, 29.5, z + 0.6], 'fang')
    mirror(HB, HB, [-4.3, 23.4, z + 0.7], [-3.7, 24.8, z + 1.3], 'fang')
# the frill back from the jaw's hinge and a crest along the crown
for i in range(3):
    mirror(HB, HB, [-5.8, 24 + i * 2.4, 1], [-5, 25.4 + i * 2.4, 6 - i], 'dfin', [0, -25 + i * 6, 0], [-5.2, 24.6 + i * 2.4, 1])
for i in range(3):
    cube(HB, [-0.5, 33, -1.5 + i * 2.6], [0.5, 35 - i * 0.4, 0.5 + i * 2.6], 'dfin', [-30, 0, 0], [0, 33, -0.5 + i * 2.6])


def whisker(p0, p1, p2, p3, w=0.8):
    at = lambda t: [(1 - t) ** 3 * p0[i] + 3 * (1 - t) ** 2 * t * p1[i] + 3 * (1 - t) * t * t * p2[i] + t ** 3 * p3[i] for i in range(3)]
    for i in range(22):
        q = at(i / 22)
        r = w / 2
        cube(HB, [q[0] - r, q[1] - r, q[2] - r], [q[0] + r, q[1] + r, q[2] + r], 'whisk')
    cube(HB, [p3[0] - 0.7, p3[1] - 0.9, p3[2] - 0.7], [p3[0] + 0.7, p3[1] + 0.5, p3[2] + 0.7], 'amber')


whisker([-2, 34, -3], [-3, 41, -3], [-8, 44, 1], [-10, 38, 3])
whisker([2, 34, -3], [3, 40, -2], [6, 42, 3], [7.5, 38.5, 7])

BB = 'armorBody'
cube(BB, [-5, 11, -3], [5, 25, 3], north='chev')
cube(BB, [-5.2, 11, -3.2], [5.2, 12.2, 3.2], 'seam')                                     # the belt
cube(BB, [-5.2, 24, -3.6], [5.2, 26.4, -2.6], 'plates')                                  # the collar
cube(BB, [-5.2, 24, 2.6], [5.2, 27.4, 3.8], 'plates')
mirror(BB, BB, [-5.2, 24, -3.6], [-4, 26.8, 3.8], 'plates')
cube(BB, [-0.8, 22.6, -3.6], [0.8, 24, -3], 'tan')                                       # the clasp
for i in range(5):
    cube(BB, [-0.6, 22 - i * 2.2, 2.8], [0.6, 24 - i * 2.2, 5.4 - i * 0.3], 'dfin', [-35, 0, 0], [0, 22.5 - i * 2.2, 3])


def arm(bone, s):
    m = (lambda f, t: (f, t)) if s < 0 else (lambda f, t: ([-t[0], f[1], f[2]], [-f[0], t[1], t[2]]))
    R = (lambda r: r) if s < 0 else (lambda r: [r[0], -r[1], -r[2]])
    Og = (lambda o: o) if s < 0 else (lambda o: [-o[0], o[1], o[2]])

    def c(f, t, style='scales', rot=None, org=None, **faces):
        f, t = m(f, t)
        if s > 0 and ('east' in faces or 'west' in faces):
            faces['east'], faces['west'] = faces.get('west', style), faces.get('east', style)
        cube(bone, f, t, style, rot and R(rot), org and Og(org), **faces)
    c([-9, 11, -3], [-3, 25, 3])
    c([-9.2, 17, -3.2], [-2.8, 18.2, 3.2], 'tan')                                         # the undersuit at the elbow
    # the pauldron: three plates, each wider, higher and tilted further out - a wing
    for x0, y0, x1, y1, z, a in ((-10.4, 20.5, -3, 25.4, 3.8, 8), (-12.6, 22.4, -5, 26.4, 3.4, 20), (-14.6, 24.2, -7.4, 27.6, 3, 34)):
        c([x0, y0, -z], [x1, y1, z], 'plates', [0, 0, -a], [-3.5, y0, 0], up='scales_up')
    for i in range(3):
        c([-16.4, 24.6 + i * 0.5, -2.6 + i * 2], [-14.2, 25.6 + i * 0.5, -1.4 + i * 2], 'dfin', [0, 0, -34], [-3.5, 24.2, 0])
    c([-9.6, 11.6, -3.4], [-2.4, 16.4, 3.4], 'plates')                                    # the gauntlet
    c([-9.2, 11, -3.1], [-2.8, 11.6, 3.1], 'tan')                                         # the cuff


arm('armorRightArm', -1)
arm('armorLeftArm', 1)


def leg(bone, boot, s):
    m = (lambda f, t: (f, t)) if s < 0 else (lambda f, t: ([-t[0], f[1], f[2]], [-f[0], t[1], t[2]]))
    R = (lambda r: r) if s < 0 else (lambda r: [r[0], -r[1], -r[2]])
    Og = (lambda o: o) if s < 0 else (lambda o: [-o[0], o[1], o[2]])

    def c(b, f, t, style='scales', rot=None, org=None, **faces):
        f, t = m(f, t)
        cube(b, f, t, style, rot and R(rot), org and Og(org), **faces)
    c(bone, [-4.4, 3, -2.5], [0.6, 12.5, 2.5], 'plates', north='scales')
    c(bone, [-4.6, 5, -3.4], [0.8, 8.4, -2.5], 'plates')                                  # the knee plate
    c(bone, [-5.1, 7, -2.4], [-4.4, 12.2, 2.4], 'plates')                                 # the hip plate
    c(boot, [-4.9, -0.2, -3], [1.1, 5.6, 3])
    c(boot, [-5.1, 4.6, -3.2], [1.3, 5.8, 3.2], 'seam')
    c(boot, [-4.9, -0.2, -4.2], [1.1, 1.8, -3], 'plates')                                 # the toe cap
    # four webbed fins at the toes: violet webbing, dark ribs over it
    c(boot, [-4.8, -0.2, -8.4], [1, 0.3, -4.2], 'memb')
    for i in range(4):
        x = -4.6 + i * 1.75
        c(boot, [x, 0.3, -9 + abs(i - 1.5) * 0.5], [x + 0.6, 0.9, -4.2], 'dfin', [0, (i - 1.5) * -6, 0], [x + 0.3, 0.5, -4.2])
    c(boot, [-5.6, 0.4, 0.4], [-5, 4, 4.6], 'memb', [0, -22, 0], [-5.2, 2, 0.4])          # the heel fin


leg('armorRightLeg', 'armorRightBoot', -1)
leg('armorLeftLeg', 'armorLeftBoot', 1)

# ------------------------------------------------------------------ the atlas: each face its own patch (shared ones once)
DIMS = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}
atlas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
px = atlas.load()
cx = cy = rowh = 0
shared = {}


def place(style, w, h):
    global cx, cy, rowh
    key = (style, w, h)
    if style in SH and key in shared:
        return shared[key]
    if cx + w > W:
        cx, cy, rowh = 0, cy + rowh, 0
    if cy + h > H:
        raise SystemExit('atlas full')
    img = PAINT[style](w, h)
    for y in range(h):
        for x in range(w):
            px[cx + x, cy + y] = img[y][x] + (255,)
    uv = [cx, cy, cx + w, cy + h]
    shared[key] = uv
    cx += w
    rowh = max(rowh, h)
    return uv


for c in sorted(cubes, key=lambda c: -(c['to'][1] - c['from'][1])):
    size = [c['to'][i] - c['from'][i] for i in range(3)]
    c['uv'] = {}
    for d, style in c['faces'].items():
        w = max(1, round(size[DIMS[d][0]] * T))
        h = max(1, round(size[DIMS[d][1]] * T))
        c['uv'][d] = place(style, w, h)

out = 'src/main/resources/assets/soulless/textures/armor/leviathan_armor.png'
import os
os.makedirs(os.path.dirname(out), exist_ok=True)
atlas.save(out)
json.dump({'size': [W, H], 'cubes': cubes}, open('tools/armor/armor_cubes.json', 'w'))
print(len(cubes), 'cubes; atlas used to row', cy + rowh)
