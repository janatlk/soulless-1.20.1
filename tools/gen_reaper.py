"""The Wailing Reaper boss: hand-built blocky GeckoLib model, painted texture + glowmask, animations,
Blockbench project. Also the boss-adjacent textures: abyss head skull, soul bolt orb, Soul Altar block faces.

Follows art/gen/reaper_concept_2.png: a horned skull with burning eyes, a cage of dark ribs over a glowing
soul core, a ragged scarf-mantle and cape, long skeletal arms with hooked claws, and instead of legs a
swirling column of violet soul flame that pools on the ground. Little ghost souls orbit it.
About 7 blocks tall. Every cube is placed by hand (no mesh conversion); one texel = one model pixel.

    uv run --no-project --with pillow --with numpy tools/gen_reaper.py [--preview out.png]
"""
import base64
import io
import json
import math
import random
import sys
import uuid
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/soulless"
TEX_W = 256
rng_uuid = random.Random(77)


def uid():
    return str(uuid.UUID(int=rng_uuid.getrandbits(128)))


def hexc(h, a=255):
    h = h.lstrip("#")
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a], dtype=np.float32)


def hash3(x, y, z=0.0, seed=0):
    n = math.sin(x * 127.1 + y * 311.7 + z * 74.7 + seed * 19.3) * 43758.5453
    return n - math.floor(n)


def ramp(*hs):
    return [hexc(h) for h in hs]


def pick(r, t):
    t = max(0.0, min(0.999, t))
    return r[int(t * len(r))].copy()


R = {
    "bone": ramp("120d19", "1d1527", "292036", "372b48", "48395e", "5f4d7b", "7c6a9c"),
    "pale": ramp("4f4566", "6f6390", "968ab6", "bdb3d9", "e0d9f2"),
    "horn": ramp("0a070e", "130e19", "1d1626", "292036", "3a2e4c", "52446a"),
    "cloth": ramp("0f0b1c", "17112b", "21183d", "2c2052", "3a2a6b", "4c378a", "6347b0"),
    "flame": ramp("26104a", "3b167a", "5420ad", "7232d6", "9150f0", "b07dff", "d2b2ff", "efe2ff"),
    "core": ramp("5a1aa8", "7f2fe0", "a35cff", "c690ff", "e4c8ff", "fbf3ff"),
    "ghost": ramp("8f7cc4", "ad9de0", "cbbff2", "e6ddff"),
}
EYE, EYE_RIM, SOCKET = hexc("fff0ff"), hexc("ff86ee"), hexc("060309")
CORE_TINT = hexc("8a3ff0")


class Cube:
    def __init__(self, name, bone, mat, lo, hi, rot=None, pivot=None):
        self.name, self.bone, self.mat = name, bone, mat
        self.lo = tuple(min(a, b) for a, b in zip(lo, hi))
        self.hi = tuple(max(a, b) for a, b in zip(lo, hi))
        self.rot, self.pivot = rot, list(pivot or [0, 0, 0])
        self.uv = {}

    @property
    def size(self):
        return tuple(h - l for l, h in zip(self.lo, self.hi))


BONES, CUBES = [], []


def bone(name, parent, pivot):
    BONES.append({"name": name, "parent": parent, "pivot": [float(v) for v in pivot]})


def box(name, b, mat, cx, cy, cz, sx, sy, sz, rot=None, pivot=None):
    """Cube by centre x/z and bottom y."""
    CUBES.append(Cube(name, b, mat, (cx - sx / 2, cy, cz - sz / 2), (cx + sx / 2, cy + sy, cz + sz / 2), rot, pivot))


def rot_for(d):
    """Cube rotation [x, 0, z] (applied x first, then z) that turns +y into direction d."""
    d = np.asarray(d, float)
    d /= np.linalg.norm(d)
    return [math.degrees(math.asin(max(-1, min(1, d[2])))), 0, math.degrees(math.atan2(-d[0], d[1]))]


def chain(name, b, mats, start, segs):
    """A bent limb: each segment (direction, length, thickness) continues from the end of the previous one."""
    p = np.array(start, float)
    for k, (d, length, th) in enumerate(segs):
        d = np.asarray(d, float) / np.linalg.norm(d)
        mat = mats[k] if isinstance(mats, (list, tuple)) else mats
        lap = th * 0.35   # overlap the joints so bends show no gaps
        CUBES.append(Cube(f"{name}{k}", b, mat, (p[0] - th / 2, p[1] - lap, p[2] - th / 2),
                          (p[0] + th / 2, p[1] + length, p[2] + th / 2), rot_for(d), list(p)))
        p = p + d * length
    return p


# ------------------------------------------------------------------ model (faces -z = front)
FLAME = [  # (bone, parent, pivot y, centre x, bottom y, height, width, depth)
    ("flame_d", "root", 0, 0.0, 3, 8, 26, 21),          # planted on the ground; the layers above swirl
    ("flame_c", "flame_d", 10, -0.6, 10, 10, 21, 17),
    ("flame_b", "flame_c", 19, 0.8, 19, 10, 16, 13),
    ("flame_a", "flame_b", 28, 0.0, 28, 11, 12, 10),
]
CAPE = [(-12, 44, -9), (-6, 52, -4), (0, 56, 0), (6, 50, 4), (12, 42, 8)]   # (x, length, fan angle)
WISPS = 4
TONGUES = []   # (bone name, phase) for the flicker animation


def build():
    BONES.clear()
    CUBES.clear()
    TONGUES.clear()
    bone("root", None, (0, 0, 0))

    # --- soul-flame lower body: a twisting column that widens and pools on the ground
    for name, parent, py, cx, y0, h, w, d in FLAME:
        bone(name, parent, (0, py, 0))
        box(f"{name}_body", name, "flame", cx, y0, 0.5, w, h, d)
    box("flame_pool", "flame_d", "flame", 0, 0, 0.5, 32, 3, 26)
    rnd = random.Random(3)
    n = 0
    for name, parent, py, cx, y0, h, w, d in FLAME:
        count = 3 if name != "flame_d" else 5
        for k in range(count):
            a = 2 * math.pi * (k + rnd.random() * 0.5) / count
            rx, rz = math.cos(a) * (w / 2 - 1), math.sin(a) * (d / 2 - 1)
            tb = f"tongue{n}"
            base_y = y0 + h * (0.35 + rnd.random() * 0.4)
            bone(tb, name, (cx + rx, base_y, 0.5 + rz))
            th = 2.5 if name in ("flame_c", "flame_d") else 2.0
            out = np.array([math.cos(a), 0, math.sin(a)])
            chain(tb, tb, ["flame", "flametip"], (cx + rx, base_y, 0.5 + rz),
                  [(out * 0.55 + [0, 1, 0], 4 + rnd.random() * 3, th), (out * 0.15 + [0, 1, 0], 3 + rnd.random() * 3, th * 0.6)])
            TONGUES.append((tb, rnd.random() * 360))
            n += 1
    for k in range(8):   # flame curling outward along the ground
        a = 2 * math.pi * (k + 0.5) / 8
        out = np.array([math.cos(a), 0, math.sin(a)])
        p0 = np.array([0, 0.5, 0.5]) + out * [14, 0, 11]
        chain(f"curl{k}_", "flame_d", ["flame", "flame", "flametip"], p0,
              [(out + [0, 0.12, 0], 6, 2.6), (out * 0.6 + [0, 1, 0], 4, 2.0), (-out * 0.6 + [0, 1, 0], 3, 1.4)])

    # --- pelvis, wrapped in a sash with ragged hanging cloth
    bone("body", "root", (0, 38, 0))
    box("pelvis", "body", "bone", 0, 36, 0.5, 15, 5, 9)
    box("sash", "body", "cloth", 0, 37, 0.5, 17, 4, 11)
    box("loin_front", "body", "cloth", -1, 21, -5.6, 8, 18, 1)
    box("loin_back", "body", "cloth", 0, 19, 6.6, 11, 20, 1)
    box("spine_glow", "body", "core", 0, 40, 1.5, 2.4, 15, 2.4)
    for k in range(4):
        box(f"lumbar{k}", "body", "bone", 0, 41.5 + k * 3, 1.5, 4.5, 2, 4)

    # --- chest: a cage of separate ribs over a glowing soul core
    bone("chest", "body", (0, 54, 0))
    box("core", "chest", "core", 0, 55, 0.5, 13, 17, 8)
    box("spine", "chest", "bone", 0, 53, 5.5, 4, 22, 2.5)
    box("sternum", "chest", "bone", 0, 59, -6.3, 3, 14, 1.8)
    for k, (y, hw) in enumerate(((70, 11.5), (66, 12), (62, 11.5), (58, 10))):
        for s in (-1, 1):
            side = "l" if s < 0 else "r"
            f0, f1 = s * 1.5, s * (hw - 1.5)
            box(f"rib{k}{side}_f", "chest", "bone", (f0 + f1) / 2, y, -5.6, abs(f1 - f0), 2, 2)
            box(f"rib{k}{side}_s", "chest", "bone", s * (hw - 1), y, 0, 2, 2, 11.5)
            box(f"rib{k}{side}_b", "chest", "bone", s * (hw / 2), y, 5.3, hw - 1.5, 2, 1.6)
    box("cage_top", "chest", "bone", 0, 72, 0.5, 21, 2.5, 12)
    for s in (-1, 1):
        box(f"clavicle{s}", "chest", "bone", s * 9, 73.5, -3.5, 15, 2, 2.5)
        box(f"scapula{s}", "chest", "bone", s * 6, 61, 6, 7, 11, 1.5)
    box("neck", "chest", "bone", 0, 74, 0.5, 4, 9, 4)

    # --- scarf around the neck and a mantle over the shoulders
    bone("cowl", "chest", (0, 76, 0))
    box("scarf_front", "cowl", "cloth", 0, 73, -6.8, 17, 7, 3)
    box("scarf_back", "cowl", "cloth", 0, 72, 7.3, 18, 8, 3)
    for s in (-1, 1):
        box(f"scarf_side{s}", "cowl", "cloth", s * 8.5, 73, 0.5, 3, 7, 14)
        box(f"mantle{s}", "cowl", "cloth", s * 12, 72, 0.5, 14, 4.5, 16, rot=[0, 0, -s * 16], pivot=[s * 6, 76, 0.5])
    box("scarf_drape", "cowl", "cloth", -4.5, 58, -7.6, 6, 16, 1.4, rot=[0, 0, -10], pivot=[-4.5, 74, -7.6])

    # --- tattered cape: five strips, each in two parts so it can ripple
    for k, (x, length, _) in enumerate(CAPE):
        up, low = f"cape{k}", f"cape{k}_low"
        half = length * 0.5
        bone(up, "chest", (x, 76, 8))
        box(f"{up}_c", up, "cloth", x, 76 - half, 8, 6.2, half + 0.5, 1.2)
        bone(low, up, (x, 76 - half, 8))
        box(f"{low}_c", low, "cloth", x, 76 - length, 8, 6.2, half, 1.2)

    # --- head: skull with deep burning sockets, cheekbones, teeth, a hanging jaw and two great horns
    bone("head", "chest", (0, 80, 0))
    box("cranium", "head", "skull", 0, 87, 0, 12, 9.5, 12)
    box("maxilla", "head", "bone", 0, 84, -1, 9, 3.5, 9.5)
    box("brow", "head", "bone", 0, 91.5, -6.6, 12.5, 1.8, 1.6)
    for s in (-1, 1):
        box(f"eye{s}", "head", "eye", s * 2.8, 88.6, -6.15, 2.4, 1.6, 0.5)
        box(f"cheek{s}", "head", "bone", s * 4.6, 85.5, -5.6, 2.6, 2.4, 2.2)
        box(f"eyeflame{s}", "head", "flametip", s * 2.8, 93, -6.6, 1.2, 3, 1)
        chain(f"horn{s}_", "head", "horn", (s * 4.6, 95, 1.5),
              [((s * 0.85, 0.55, 0.15), 5, 3.6), ((s * 0.4, 0.9, 0.2), 4, 3.1), ((s * 0.0, 1, 0.1), 3.5, 2.6),
               ((-s * 0.5, 0.85, 0), 3, 2.0), ((-s * 0.9, 0.45, -0.1), 2.5, 1.4)])
    box("teeth_up", "head", "teeth", 0, 84, -5.85, 7, 1.6, 0.6)
    bone("jaw", "head", (0, 84, 2))
    box("jaw", "jaw", "bone", 0, 81, -1.3, 9, 3, 9)
    box("teeth_low", "jaw", "teeth", 0, 83.4, -5.6, 7, 1.2, 0.6)

    # --- long skeletal arms: shoulder, humerus, elbow, two forearm bones, wrist, palm, hooked claws
    for s, side in ((-1, "left"), (1, "right")):
        x = s * 15.5
        bone(f"{side}_arm", "chest", (s * 15, 73, 0.5))
        box(f"{side}_shoulder", f"{side}_arm", "bone", s * 15, 68.5, 0.5, 6, 6, 6)
        box(f"{side}_flap", f"{side}_arm", "cloth", s * 15.8, 63, 0.5, 8, 9, 8.5)
        box(f"{side}_humerus", f"{side}_arm", "bone", x, 53, 0.5, 3.5, 17, 3.5)
        bone(f"{side}_forearm", f"{side}_arm", (x, 54, 0.5))
        box(f"{side}_elbow", f"{side}_forearm", "bone", x, 51.5, 0.5, 4.6, 4, 4.6)
        box(f"{side}_radius", f"{side}_forearm", "bone", x, 39, -0.9, 2.1, 14, 2.1)
        box(f"{side}_ulna", f"{side}_forearm", "bone", x, 39, 1.9, 2.1, 14, 2.1)
        box(f"{side}_vein", f"{side}_forearm", "core", x, 41, 0.5, 1, 9, 1)
        box(f"{side}_wrist", f"{side}_forearm", "bone", x, 36.5, 0.5, 4, 3.5, 4.8)
        bone(f"{side}_hand", f"{side}_forearm", (x, 37, 0.5))
        box(f"{side}_palm", f"{side}_hand", "bone", x, 31, 0.5, 3, 6.5, 6)
        bone(f"{side}_claws", f"{side}_hand", (x, 31.5, 0.5))
        for j, z in enumerate((-2.0, -0.4, 1.2, 2.7)):
            chain(f"{side}_finger{j}_", f"{side}_claws", ["bone", "bone", "pale"], (x, 32, z),
                  [((s * 0.12, -1, 0), 5.5 - abs(j - 1.5) * 0.6, 1.5), ((-s * 0.3, -1, 0), 4.5, 1.25), ((-s * 0.85, -0.55, 0), 3, 1.0)])
        chain(f"{side}_thumb", f"{side}_claws", ["bone", "pale"], (x - s * 1, 34, -2.6),
              [((-s * 0.5, -0.8, -0.35), 4, 1.4), ((-s * 0.85, -0.45, 0), 3, 1.0)])

    # --- ghost souls circling the shoulders
    bone("wisps", "root", (0, 70, 0))
    for i in range(WISPS):
        a = 2 * math.pi * i / WISPS + 0.6
        wx, wz, wy = math.cos(a) * 21, math.sin(a) * 21, 66 + (i % 2) * 12
        wb = f"wisp{i}"
        bone(wb, "wisps", (wx, wy, wz))
        box(f"{wb}_head", wb, "ghost", wx, wy, wz, 4, 4, 4)
        box(f"{wb}_body", wb, "ghost", wx, wy - 3, wz, 3, 3, 3)
        box(f"{wb}_tail", wb, "ghost", wx, wy - 5.5, wz, 2, 2.5, 2)
        box(f"{wb}_tip", wb, "ghost", wx, wy - 7, wz, 1, 1.5, 1)


# ------------------------------------------------------------------ texture
FACES = ("north", "south", "east", "west", "up", "down")
LIGHT = {"up": 0.16, "down": -0.2, "north": 0.0, "south": -0.04, "east": -0.07, "west": -0.07}


def face_dims(c, f):
    sx, sy, sz = c.size
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[f]


def face_point(c, f, u, v):
    """Model-space point of face f at texture coords u (across) / v (down), both 0..1, before rotation."""
    (x0, y0, z0), (x1, y1, z1) = c.lo, c.hi
    y = y1 - v * (y1 - y0)
    return {
        "north": (x1 - u * (x1 - x0), y, z0), "south": (x0 + u * (x1 - x0), y, z1),
        "east": (x1, y, z1 - u * (z1 - z0)), "west": (x0, y, z0 + u * (z1 - z0)),
        "up": (x0 + u * (x1 - x0), y1, z0 + v * (z1 - z0)), "down": (x0 + u * (x1 - x0), y0, z0 + v * (z1 - z0)),
    }[f]


def paint(c, f, W, H):
    img = np.zeros((H, W, 4), np.float32)
    glow = np.zeros((H, W), bool)
    m = c.mat
    side = f not in ("up", "down")
    lt = LIGHT[f]
    seed = len(c.name) * 7 + int(c.lo[0] * 3)
    # ragged hem: each column of a cloth face ends at its own length
    cut = [int(hash3(i, c.lo[0], c.lo[2], seed) * min(5, H * 0.3)) if side and H > 5 else 0 for i in range(W)]
    for j in range(H):
        for i in range(W):
            x, y, z = face_point(c, f, (i + 0.5) / W, (j + 0.5) / H)
            n = hash3(round(x), round(y), round(z), 1) - 0.5
            edge = side and (i == 0 or i == W - 1) and W > 2
            top, bottom = side and j == 0, side and j == H - 1
            col = None
            if m in ("bone", "skull"):
                t = 0.42 + lt + n * 0.14 + (0.06 if m == "skull" else 0) + (0.12 if top else 0) - (0.1 if edge or bottom else 0)
                col = pick(R["bone"], t)
                if hash3(x, y, z, 9) > 0.965:
                    col = pick(R["bone"], t - 0.2)   # cracks
                d = math.dist((x, y, z), (0, 63, 0.5))
                if d < 15:   # the soul core lights the bones around it
                    k = (1 - d / 15) * 0.45
                    col = col * (1 - k) + CORE_TINT * k
                if m == "skull" and f == "north":
                    col = skull_face(col, x, y)
            elif m == "pale":
                col = pick(R["pale"], 0.35 + lt + n * 0.15 + 0.4 * (j + 0.5) / H)
            elif m == "horn":
                t = 0.4 + lt + n * 0.12 + (0.14 if top else 0) - (0.12 if edge else 0)
                if j % 3 == 2 and side:
                    t -= 0.16   # ridges
                col = pick(R["horn"], t)
            elif m == "teeth":
                col = pick(R["pale"], 0.7 + n * 0.2) if i % 2 == 0 else SOCKET.copy()
            elif m == "cloth":
                fold = 0.13 * math.sin(x * 1.25 + z * 1.25) + 0.06 * math.sin(x * 0.4 - y * 0.2)
                t = 0.42 + lt + fold + n * 0.1 + (0.1 if top else 0) - (0.08 if edge else 0)
                col = pick(R["cloth"], t)
                if side and H > 5:
                    if j >= H - cut[i]:
                        col = hexc("000000", 0)
                    elif j >= H - cut[i] - 1:
                        col = pick(R["cloth"], 0.95)   # lighter frayed edge
            elif m in ("flame", "flametip"):
                # broad twisting ribbons of light and dark, like the concept's swirl
                sw = math.sin((x + z) * 0.32 + y * 0.28 + 0.5 * math.sin(y * 0.15))
                band = round(sw * 2) / 2
                t = 0.48 + 0.2 * band + n * 0.05 + lt * 0.4 + 0.12 * max(0, 1 - y / 14)
                if m == "flametip":
                    t = 0.55 + 0.35 * (1 - (j + 0.5) / H) + n * 0.1
                    if side and j == 0 and (i == 0 or i == W - 1) and W > 1:
                        col = hexc("000000", 0)
                if col is None:
                    col = pick(R["flame"], t)
                glow[j, i] = True
            elif m == "core":
                d = abs((i + 0.5) / W - 0.5) + abs((j + 0.5) / H - 0.5)
                col = pick(R["core"], 0.75 - d * 0.6 + n * 0.25)
                glow[j, i] = True
            elif m == "eye":
                col = EYE.copy() if (side and 0 < i < W - 1) or not side else EYE_RIM.copy()
                glow[j, i] = True
            elif m == "ghost":
                col = pick(R["ghost"], 0.55 + lt + n * 0.2 + 0.2 * (1 - (j + 0.5) / H))
                col[3] = 175
                glow[j, i] = True
            img[j, i] = col
            if col[3] == 0:
                glow[j, i] = False
    return img, glow


def skull_face(col, x, y):
    """Front of the cranium: two slanted sockets (the eye cubes sit inside them) and a nasal hole."""
    for s in (-1, 1):
        dx, dy = (x - s * 2.8) / 2.4, (y - 89.3) / 1.6
        dy += dx * s * 0.35
        if dx * dx + dy * dy < 1.0:
            return SOCKET.copy()
    if abs(x) < 1.0 and 87 <= y < 88.2:
        return SOCKET.copy()
    return col


def pack_and_paint():
    faces = []
    for c in CUBES:
        for f in FACES:
            a, b = face_dims(c, f)
            faces.append((c, f, max(1, round(a)), max(1, round(b))))
    faces.sort(key=lambda t: (-t[3], -t[2]))
    x = y = row_h = 0
    for c, f, W, H in faces:
        if x + W > TEX_W:
            x, y, row_h = 0, y + row_h, 0
        c.uv[f] = (x, y, W, H)
        x += W
        row_h = max(row_h, H)
    tex_h = 1 << math.ceil(math.log2(y + row_h))
    base = np.zeros((tex_h, TEX_W, 4), np.float32)
    glow = np.zeros((tex_h, TEX_W), bool)
    for c, f, W, H in faces:
        u, v, _, _ = c.uv[f]
        img, g = paint(c, f, W, H)
        base[v:v + H, u:u + W] = img
        glow[v:v + H, u:u + W] = g
    gm = base.copy()
    gm[~glow] = 0
    return base, gm, tex_h


def png(arr):
    buf = io.BytesIO()
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(buf, "PNG")
    return buf.getvalue()


# ------------------------------------------------------------------ preview (software render, rest pose)
def rotmat(rx, ry, rz):
    a, b, c = (math.radians(v) for v in (rx, ry, rz))
    Rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    Ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    Rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def render_preview(base, out, yaws=(0, 35, 90, 180), scale=5, sub=3):
    Wp, Hp = 80 * scale, 122 * scale
    sheets = []
    normals = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
    for yaw in yaws:
        img = np.zeros((Hp, Wp, 3), np.float32) + np.array([110, 150, 200], np.float32)
        img[int(Hp - 4 * scale):] = (70, 110, 60)
        zb = np.full((Hp, Wp), 1e9)
        V = rotmat(0, yaw, 0)
        for c in CUBES:
            Rm = rotmat(*(c.rot or (0, 0, 0)))
            piv = np.array(c.pivot, float)
            for f in FACES:
                u0, v0, W, H = c.uv[f]
                nrm = V @ Rm @ np.array(normals[f], float)
                if nrm[2] > 0.05:   # facing away (camera looks toward +z)
                    continue
                shade = 0.62 + 0.38 * max(0, -nrm[2]) * 0.7 + 0.25 * max(0, nrm[1])
                us = (np.arange(W * sub) + 0.5) / (W * sub)
                vs = (np.arange(H * sub) + 0.5) / (H * sub)
                uu, vv = np.meshgrid(us, vs)
                pts = np.array([face_point(c, f, a, b) for a, b in zip(uu.ravel(), vv.ravel())])
                pts = (Rm @ (pts - piv).T).T + piv
                pts = (V @ pts.T).T
                tex = base[v0 + np.minimum((vv.ravel() * H).astype(int), H - 1), u0 + np.minimum((uu.ravel() * W).astype(int), W - 1)]
                ok = tex[:, 3] > 0
                px = (Wp / 2 + pts[:, 0] * scale * -1).astype(int)   # camera at -z looking +z: model +x is screen left
                py = (Hp - 4 * scale - pts[:, 1] * scale).astype(int)
                for (X, Y, Z, col, o) in zip(px, py, pts[:, 2], tex, ok):
                    if not o or not (0 <= X < Wp and 0 <= Y < Hp):
                        continue
                    for dx in range(-1, 2):
                        for dy in range(-1, 2):
                            XX, YY = X + dx, Y + dy
                            if 0 <= XX < Wp and 0 <= YY < Hp and Z < zb[YY, XX]:
                                zb[YY, XX] = Z
                                a = col[3] / 255
                                img[YY, XX] = col[:3] * shade * a + img[YY, XX] * (1 - a) if a < 1 else col[:3] * shade
        sheets.append(img)
    Image.fromarray(np.clip(np.concatenate(sheets, axis=1), 0, 255).astype(np.uint8)).save(out)


# ------------------------------------------------------------------ animations
def animations():
    t = "query.anim_time"
    amb = {}   # always-on layer: flames, cape, wisps, eye flames (the state animations never touch these bones)
    for k, (name, *_rest) in enumerate(FLAME):
        if name == "flame_d":
            continue
        # a planted swirl: each layer twists a little about the vertical and breathes; nothing bends sideways
        # (bending the nested layers made the column wriggle like a worm)
        sgn = 1 if k % 2 == 0 else -1
        amb[name] = {"rotation": {0: [0, f"{sgn} * math.sin({t} * 50 + {k * 70}) * 4", 0]},
                     "scale": {0: [f"1 + math.sin({t} * 160 + {k * 80}) * 0.025", 1, f"1 + math.sin({t} * 160 + {k * 80}) * 0.025"]}}
    for tb, ph in TONGUES:
        amb[tb] = {"scale": {0: [1, f"1 + math.sin({t} * 420 + {ph:.0f}) * 0.2", 1]}}
    for k, (_, _, fan) in enumerate(CAPE):
        amb[f"cape{k}"] = {"rotation": {0: [f"7 + math.sin({t} * 100 + {k * 40}) * 5", 0, f"{fan} + math.sin({t} * 80 + {k * 50}) * 2"]}}
        amb[f"cape{k}_low"] = {"rotation": {0: [f"6 + math.sin({t} * 100 + {k * 40 + 70}) * 9", 0, f"math.sin({t} * 90 + {k * 50}) * 4"]}}
    amb["wisps"] = {"rotation": {0: [0, f"{t} * 40", 0]}}
    for i in range(WISPS):
        amb[f"wisp{i}"] = {"position": {0: [0, f"math.sin({t} * 150 + {i * 90}) * 3", 0]},
                           "rotation": {0: [f"math.sin({t} * 200 + {i * 90}) * 10", f"-{t} * 40", 0]}}
    for s in ("left", "right"):
        amb[f"{s}_claws"] = {"rotation": {0: [0, 0, f"math.sin({t} * 70 + {0 if s == 'left' else 90}) * 6"]}}

    sway = f"math.sin({t} * 120)"
    idle = {
        "body": {"position": {0: [0, f"{sway} * 1.0", 0]}},
        "chest": {"rotation": {0: [f"math.sin({t} * 120 + 40) * 2", 0, 0]}, "scale": {0: [f"1 + {sway} * 0.015", 1, f"1 + {sway} * 0.015"]}},
        "left_arm": {"rotation": {0: [f"math.sin({t} * 120) * 4", 0, "-7"]}},
        "right_arm": {"rotation": {0: [f"math.sin({t} * 120 + 90) * 4", 0, "7"]}},
        "left_forearm": {"rotation": {0: [f"-8 + math.sin({t} * 120 + 30) * 3", 0, 0]}},
        "right_forearm": {"rotation": {0: [f"-8 + math.sin({t} * 120 + 120) * 3", 0, 0]}},
        "head": {"rotation": {0: [f"math.sin({t} * 60) * 3", f"math.sin({t} * 35) * 6", 0]}},
        "jaw": {"rotation": {0: [f"4 + math.sin({t} * 90) * 3", 0, 0]}},
    }
    bolt = {  # 1.4 s: right hand winds back and hurls forward
        "right_arm": {"rotation": {0: [0, 0, 7], 0.5: [40, 0, 30], 0.8: [-110, 0, 10], 1.4: [0, 0, 7]}},
        "right_forearm": {"rotation": {0: [-8, 0, 0], 0.5: [-60, 0, 0], 0.8: [-10, 0, 0], 1.4: [-8, 0, 0]}},
        "right_claws": {"rotation": {0: [0, 0, 0], 0.5: [0, 0, 25], 0.8: [0, 0, -30], 1.4: [0, 0, 0]}},
        "chest": {"rotation": {0: [0, 0, 0], 0.5: [0, -20, 0], 0.8: [8, 15, 0], 1.4: [0, 0, 0]}},
        "jaw": {"rotation": {0: [4, 0, 0], 0.7: [25, 0, 0], 1.0: [25, 0, 0], 1.4: [4, 0, 0]}},
    }
    erupt = {  # 1.5 s: both arms heave upward as if lifting the ground
        "left_arm": {"rotation": {0: [0, 0, -7], 0.4: [30, 0, -15], 0.7: [-160, 0, -20], 1.2: [-160, 0, -20], 1.5: [0, 0, -7]}},
        "right_arm": {"rotation": {0: [0, 0, 7], 0.4: [30, 0, 15], 0.7: [-160, 0, 20], 1.2: [-160, 0, 20], 1.5: [0, 0, 7]}},
        "chest": {"rotation": {0: [0, 0, 0], 0.4: [15, 0, 0], 0.7: [-12, 0, 0], 1.5: [0, 0, 0]}},
        "head": {"rotation": {0: [0, 0, 0], 0.7: [-20, 0, 0], 1.5: [0, 0, 0]}},
        "jaw": {"rotation": {0: [4, 0, 0], 0.7: [30, 0, 0], 1.2: [30, 0, 0], 1.5: [4, 0, 0]}},
    }
    ult = {  # 3.5 s: hugs itself, trembling, then throws the arms wide and screams
        "left_arm": {"rotation": {0: [0, 0, -7], 0.5: [-70, -60, 10], 2.8: [-70, -65, 10], 3.05: [-20, 0, -95], 3.5: [-20, 0, -95]}},
        "right_arm": {"rotation": {0: [0, 0, 7], 0.5: [-70, 60, -10], 2.8: [-70, 65, -10], 3.05: [-20, 0, 95], 3.5: [-20, 0, 95]}},
        "left_claws": {"rotation": {0: [0, 0, 0], 2.8: [0, 0, 0], 3.05: [0, 0, -35], 3.5: [0, 0, -35]}},
        "right_claws": {"rotation": {0: [0, 0, 0], 2.8: [0, 0, 0], 3.05: [0, 0, 35], 3.5: [0, 0, 35]}},
        "chest": {"rotation": {0: [0, 0, 0], 0.5: [20, 0, f"math.sin({t} * 2000) * 2"], 2.8: [25, 0, 0], 3.05: [-25, 0, 0], 3.5: [-20, 0, 0]}},
        "head": {"rotation": {0: [0, 0, 0], 0.5: [30, 0, 0], 2.8: [35, 0, 0], 3.05: [-35, 0, 0], 3.5: [-30, 0, 0]}},
        "jaw": {"rotation": {0: [4, 0, 0], 2.9: [4, 0, 0], 3.05: [40, 0, 0], 3.5: [35, 0, 0]}},
    }
    stunned = {  # slumped, swaying
        "chest": {"rotation": {0: [30, 0, f"math.sin({t} * 90) * 6"]}},
        "head": {"rotation": {0: [35, 0, f"math.sin({t} * 90 + 40) * 10"]}},
        "jaw": {"rotation": {0: [20, 0, 0]}},
        "left_arm": {"rotation": {0: [10, 0, "-2"]}},
        "right_arm": {"rotation": {0: [10, 0, "2"]}},
        "root": {"position": {0: [0, -6, 0]}},
    }
    emerge = {  # 2.5 s: rises out of the altar's souls, arms lifting
        "root": {"position": {0: [0, -70, 0], 2.0: [0, 0, 0], 2.5: [0, 0, 0]}},
        "left_arm": {"rotation": {0: [-150, 0, -20], 2.0: [-150, 0, -20], 2.5: [0, 0, -7]}},
        "right_arm": {"rotation": {0: [-150, 0, 20], 2.0: [-150, 0, 20], 2.5: [0, 0, 7]}},
        "head": {"rotation": {0: [-30, 0, 0], 2.5: [0, 0, 0]}},
        "jaw": {"rotation": {0: [30, 0, 0], 2.0: [30, 0, 0], 2.5: [4, 0, 0]}},
    }
    death = {  # 3 s: collapses and sinks away
        "root": {"position": {0: [0, 0, 0], 3.0: [0, -60, 0]}},
        "chest": {"rotation": {0: [0, 0, 0], 1.0: [40, 0, 10], 3.0: [60, 0, 20]}},
        "head": {"rotation": {0: [0, 0, 0], 1.0: [-40, 0, 0], 3.0: [30, 0, 0]}},
        "jaw": {"rotation": {0: [0, 0, 0], 0.5: [40, 0, 0], 3.0: [40, 0, 0]}},
        "left_arm": {"rotation": {0: [0, 0, -7], 1.0: [-100, 0, -40], 3.0: [10, 0, -10]}},
        "right_arm": {"rotation": {0: [0, 0, 7], 1.0: [-100, 0, 40], 3.0: [10, 0, 10]}},
    }
    return {
        "animation.wailing_reaper.ambient": (True, 36.0, amb),
        "animation.wailing_reaper.idle": (True, 6.0, idle),
        "animation.wailing_reaper.bolt": (False, 1.4, bolt),
        "animation.wailing_reaper.erupt": (False, 1.5, erupt),
        "animation.wailing_reaper.ult": (False, 3.5, ult),
        "animation.wailing_reaper.stunned": (True, 4.0, stunned),
        "animation.wailing_reaper.emerge": (False, 2.5, emerge),
        "animation.wailing_reaper.death": (False, 3.0, death),
    }


def r(v):
    return round(float(v), 4)


def write_geo(tex_h):
    by_bone = {}
    for c in CUBES:
        by_bone.setdefault(c.bone, []).append(c)
    bones = []
    for b in BONES:
        out = {"name": b["name"], "pivot": [r(-b["pivot"][0]), r(b["pivot"][1]), r(b["pivot"][2])]}
        if b["parent"]:
            out["parent"] = b["parent"]
        cubes = []
        for c in by_bone.get(b["name"], []):
            cd = {"origin": [r(-c.hi[0]), r(c.lo[1]), r(c.lo[2])], "size": [r(s) for s in c.size],
                  "uv": {f: {"uv": [u, v], "uv_size": [W, H]} for f, (u, v, W, H) in c.uv.items()}}
            if c.rot:
                cd["pivot"] = [r(-c.pivot[0]), r(c.pivot[1]), r(c.pivot[2])]
                cd["rotation"] = [r(-c.rot[0]), r(-c.rot[1]), r(c.rot[2])]
            cubes.append(cd)
        if cubes:
            out["cubes"] = cubes
        bones.append(out)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.wailing_reaper", "texture_width": TEX_W, "texture_height": tex_h,
                        "visible_bounds_width": 6, "visible_bounds_height": 8, "visible_bounds_offset": [0, 3.5, 0]},
        "bones": bones}]}
    p = ASSETS / "geo/entity/wailing_reaper.geo.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(geo, indent=1))


def write_anims():
    out = {}
    for name, (loop, length, bones) in animations().items():
        bb = {}
        for bname, chans in bones.items():
            bb[bname] = {}
            for ch, kfs in chans.items():
                if list(kfs) == [0]:
                    bb[bname][ch] = [str(x) if isinstance(x, str) else x for x in kfs[0]]
                else:
                    bb[bname][ch] = {str(t): {"vector": v, "easing": "easeInOutSine"} for t, v in kfs.items()}
        out[name] = {"loop": loop, "animation_length": length, "bones": bb}
    p = ASSETS / "animations/entity/wailing_reaper.animation.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps({"format_version": "1.8.0", "animations": out}, indent=1))


def write_bbmodel(tex_png, tex_h):
    elements, cid = [], {}
    for c in CUBES:
        i = uid()
        cid[id(c)] = i
        el = {"name": c.name, "type": "cube", "uuid": i, "box_uv": False, "from": [r(x) for x in c.lo], "to": [r(x) for x in c.hi],
              "origin": [r(x) for x in c.pivot], "faces": {f: {"uv": [u, v, u + W, v + H], "texture": 0} for f, (u, v, W, H) in c.uv.items()}}
        if c.rot:
            el["rotation"] = [r(x) for x in c.rot]
        elements.append(el)
    gid = {b["name"]: uid() for b in BONES}

    def group(name):
        b = next(x for x in BONES if x["name"] == name)
        ch = [cid[id(c)] for c in CUBES if c.bone == name] + [group(x["name"]) for x in BONES if x["parent"] == name]
        return {"name": name, "origin": b["pivot"], "rotation": [0, 0, 0], "uuid": gid[name], "export": True, "isOpen": True,
                "visibility": True, "children": ch}

    anims = []
    for name, (loop, length, bones) in animations().items():
        animators = {}
        for bname, chans in bones.items():
            kfs = []
            for ch, frames in chans.items():
                for t, v in frames.items():
                    kfs.append({"channel": ch, "data_points": [{"x": str(v[0]), "y": str(v[1]), "z": str(v[2])}],
                                "uuid": uid(), "time": t, "color": -1, "interpolation": "linear"})
            animators[gid[bname]] = {"name": bname, "type": "bone", "keyframes": kfs}
        anims.append({"uuid": uid(), "name": name, "loop": "loop" if loop else "hold", "override": False, "length": length,
                      "snapping": 20, "selected": False, "animators": animators})
    bb = {"meta": {"format_version": "4.5", "model_format": "animated_entity_model", "box_uv": False},
          "name": "wailing_reaper", "model_identifier": "wailing_reaper", "visible_box": [6, 8, 3.5],
          "resolution": {"width": TEX_W, "height": tex_h}, "elements": elements, "outliner": [group("root")],
          "textures": [{"path": "", "name": "wailing_reaper.png", "folder": "entity", "namespace": "soulless", "id": "0",
                        "width": TEX_W, "height": tex_h, "uv_width": TEX_W, "uv_height": tex_h, "particle": True,
                        "render_mode": "default", "visible": True, "internal": True, "saved": False, "uuid": uid(),
                        "source": "data:image/png;base64," + base64.b64encode(tex_png).decode()}],
          "animations": anims}
    (ROOT / "art").mkdir(exist_ok=True)
    (ROOT / "art/wailing_reaper.bbmodel").write_text(json.dumps(bb))


# ------------------------------------------------------------------ extra textures
P = {"bone": R["bone"][1:6], "pale": R["pale"], "glow": R["core"][:4], "eye": EYE, "socket": SOCKET}


def abyss_head_texture():
    """64x64 wither-skull layout (head box 8x8x8 at uv 0,35): a dark violet skull with burning eyes."""
    img = np.zeros((64, 64, 4), np.float32)
    rnd = random.Random(5)
    for y in range(35, 51):
        for x in range(0, 32):
            img[y, x] = pick(P["bone"], 0.35 + rnd.random() * 0.4)
    for (ex, ey) in ((10, 46), (13, 46)):
        img[ey, ex] = P["eye"]
        img[ey, ex + 1] = P["glow"][2]
        img[ey + 1, ex] = P["glow"][1]
        img[ey + 1, ex + 1] = P["glow"][2]
    for x in range(10, 15):
        img[49, x] = P["socket"] if x % 2 == 0 else P["pale"][1]
    return img


FIRE = [(0.0, hexc("000000", 0)), (0.15, hexc("3a0a66", 150)), (0.35, hexc("6a14b8", 220)),
        (0.55, hexc("9a2cf0", 255)), (0.75, hexc("c46cff", 255)), (0.9, hexc("ecc0ff", 255)), (1.0, hexc("fff4ff", 255))]


def fire(t):
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(FIRE, FIRE[1:]):
        if t <= t1:
            k = round(((t - t0) / (t1 - t0)) * 3) / 3
            return c0 * (1 - k) + c1 * k
    return FIRE[-1][1]


def bolt_texture():
    img = np.zeros((16, 16, 4), np.float32)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.5
            if d < 1:
                img[y, x] = fire(1.05 - d)
    return img


def altar_textures():
    """Soul Altar: obsidian sides with glowing runes; the top has a socket (empty / holding the heart)."""
    rnd = random.Random(9)
    obs = [hexc("0d0814"), hexc("140c1e"), hexc("1c1229"), hexc("251836")]
    glow = [hexc("6a14a8"), hexc("9a2cf0"), hexc("c46cff"), hexc("efc9ff")]

    def base():
        img = np.zeros((16, 16, 4), np.float32)
        for y in range(16):
            for x in range(16):
                img[y, x] = pick(obs, 0.2 + rnd.random() * 0.6)
                if rnd.random() > 0.93:
                    img[y, x] = hexc("3b2457")
        return img

    side = base()
    for y in (4, 7, 10):
        for x in range(4, 12):
            if (x + y) % 3:
                side[y, x] = glow[1]
    for x in range(16):
        side[0, x] = hexc("2e1d43")
        side[15, x] = hexc("07040b")
    top = base()
    for y in range(4, 12):
        for x in range(4, 12):
            top[y, x] = SOCKET if 5 <= x <= 10 and 5 <= y <= 10 else hexc("3b2457")
    filled = top.copy()
    for y in range(5, 11):
        for x in range(5, 11):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 3:
                filled[y, x] = glow[3] if d < 1.2 else glow[2] if d < 2.2 else glow[1]
    bottom = base()
    return side, top, filled, bottom


def main():
    build()
    base, gm, tex_h = pack_and_paint()
    if "--preview" in sys.argv:
        render_preview(base, sys.argv[sys.argv.index("--preview") + 1])
    t = ASSETS / "textures/entity"
    t.mkdir(parents=True, exist_ok=True)
    tex_png = png(base)
    (t / "wailing_reaper.png").write_bytes(tex_png)
    (t / "wailing_reaper_glowmask.png").write_bytes(png(gm))
    (t / "abyss_head.png").write_bytes(png(abyss_head_texture()))
    (t / "soul_bolt.png").write_bytes(png(bolt_texture()))
    b = ASSETS / "textures/block"
    side, top, filled, bottom = altar_textures()
    (b / "soul_altar_side.png").write_bytes(png(side))
    (b / "soul_altar_top.png").write_bytes(png(top))
    (b / "soul_altar_top_filled.png").write_bytes(png(filled))
    (b / "soul_altar_bottom.png").write_bytes(png(bottom))
    write_geo(tex_h)
    write_anims()
    write_bbmodel(tex_png, tex_h)
    print(f"reaper cubes={len(CUBES)} bones={len(BONES)} texture={TEX_W}x{tex_h}")


if __name__ == "__main__":
    main()
