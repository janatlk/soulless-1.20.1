"""The Wailing Reaper boss: hand-built blocky GeckoLib model, painted texture + glowmask, animations,
Blockbench project. Also the boss-adjacent textures: abyss head skull, soul bolt orb, Soul Altar block faces.

Follows art/gen/reaper_concept_2.png: a horned skull with burning eyes, a cage of dark ribs over a glowing
soul core, a ragged scarf-mantle and cape, long skeletal arms with hooked claws, and instead of legs a
pair of great blue feathered wings that keep it levitating.
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
TEX_W = 512
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
    "wing": ramp("07030f", "0d0619", "150926", "1e0c36", "290f48", "34135b", "41186f", "511e86", "66289f", "8037bd"),
    "helm": ramp("0b0a11", "13121b", "1b1a27", "252435", "323148", "45445f", "605f80"),
    "trim": ramp("3a3f55", "565d7a", "7c84a6", "a9b1cf", "d5daf0"),
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
        self.density = 1      # texels per model pixel
        self.feathers = None  # for "plume" planes: the feathers painted onto them

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
CAPE = []   # (x, length, fan angle) - the hanging cape was removed
#: wing beat: 1.6 s per flap (anim_time * BEAT degrees); the ambient loop holds a whole number of beats
BEAT = 225
AMBIENT_LEN = 14.4


def feather_plane(name, b, s, z, feathers, mat="plume", rot=None, pivot_y=None):
    """A thin plane in the wing's x-y plane (span along x on side s, feathers hanging down), sized to fit
    its feathers. Each feather is (root span-u, root y, angle outward from straight down, length, half-width);
    the shapes are painted at 2x resolution and everything outside them is transparent."""
    us, ys = [], []
    for u, y, ang, length, hw in feathers:
        a = math.radians(ang)
        tip = (u + math.sin(a) * length, y - math.cos(a) * length)
        us += [u - hw - 0.5, u + hw + 0.5, tip[0] - hw, tip[0] + hw]
        ys += [y + 0.5, tip[1] - 0.5]
    lo_u, hi_u, lo_y, hi_y = min(us), max(us), min(ys), max(ys)
    c = Cube(name, b, mat, (s * lo_u, lo_y, z - 0.2), (s * hi_u, hi_y, z + 0.2), rot,
             [s * (lo_u + hi_u) / 2, pivot_y if pivot_y is not None else hi_y, z] if rot else None)
    c.density, c.feathers, c.side = 2, feathers, s
    CUBES.append(c)


#: the wing's leading edge in its spread pose (span u outward from the spine, y): shoulder, elbow, wrist, tip.
#: It arches up to the wrist and falls toward the tip, like a real wing, and the feathers follow that perimeter.
WS = 1.3   # wing scale
HOOD_SCALE = 0.82
WING_EDGE = [(5 + (u - 5) * WS, 72 + (y - 72) * WS) for u, y in ((5, 72), (12, 86), (20, 103), (34, 112))]
WING_Z = 10


def lerp2(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def build_wings():
    """Two big feathered wings on the back. Each is a three-joint chain like a real wing (arm, forearm,
    hand) so the beat can bend and lag along the span instead of flapping two flat boards. The leading edge
    arches over shoulder, elbow and wrist; flight feathers hang from it and fan out over the hand so the
    trailing edge is one smooth curve; coverts lie in layers on both faces and the flight feathers are
    cupped back a little, so the wing has volume. Built spread; the ambient animation does all the posing."""
    E = WING_EDGE
    for s, side in ((-1, "left"), (1, "right")):
        arm, fore, hand = f"{side}_wing", f"{side}_wing_fore", f"{side}_wing_hand"
        bone(f"{side}_wing_pose", "chest", (s * E[0][0], E[0][1], WING_Z))   # attack poses, on top of the beat
        bone(arm, f"{side}_wing_pose", (s * E[0][0], E[0][1], WING_Z))
        bone(fore, arm, (s * E[1][0], E[1][1], WING_Z))
        bone(hand, fore, (s * E[2][0], E[2][1], WING_Z))
        # (bone, edge from, edge to, feather count, angle from..to, tip y from..to or lengths)
        segs = (
            (arm, E[0], E[1], 7, (0, 7), (72 - 31 * WS, 72 - 28 * WS), None),
            (fore, E[1], E[2], 9, (8, 26), (72 - 27 * WS, 72 - 12 * WS), None),
            (hand, E[2], E[3], 11, (28, 112), None, [L * WS for L in (40, 41.5, 42, 42, 41, 39, 36.5, 33.5, 30, 26, 21)]),
        )
        hw = 1.75 * WS ** 0.5   # long, narrow, blade-like feathers
        for bn, p0, p1, n, (a0, a1), tips, lengths in segs:
            # three layered rows of long feathers (Maledictus-style): the flight feathers, a row of shorter
            # ones over them and a row of short ones over those, each row's tips jutting out from under the
            # next so the wing reads as a dense, jagged, layered fan rather than a single sheet
            flights, mid, low = [], [], []
            for k in range(n):
                t = (k + 0.4) / n
                ru, ry = lerp2(p0, p1, t)
                ry -= 1.2
                ang = a0 + (a1 - a0) * (k / max(1, n - 1)) ** (1.4 if lengths else 1)
                if lengths:
                    L = lengths[k]
                else:
                    L = (ry - (tips[0] + (tips[1] - tips[0]) * t)) / math.cos(math.radians(ang))
                L *= 0.88 + 0.24 * hash3(k, ru, 5)   # ragged trailing edge
                flights.append((ru, ry, ang, L, hw))
            for k in range(n):
                t = (k + 0.9) / n
                ru, ry = lerp2(p0, p1, min(1, t))
                ang = (a0 + (a1 - a0) * (min(n - 1, k + 0.5) / max(1, n - 1)) ** (1.4 if lengths else 1)) * 0.92
                ref = flights[k][3]
                mid.append((ru, ry - 0.6, ang, ref * (0.6 + 0.1 * hash3(k, ru, 8)), hw * 1.05))
                low.append((ru, ry + 0.2, ang * 0.85, ref * (0.32 + 0.08 * hash3(k, ru, 9)), hw * 1.1))
            top = max(p0[1], p1[1]) + 1
            feather_plane(f"{bn}_flights", bn, s, WING_Z, flights, rot=[-7, 0, 0], pivot_y=top)
            for layer, dz in (("a", -1), ("b", 1)):
                feather_plane(f"{bn}_mid_{layer}", bn, s, WING_Z + dz * 0.55, mid, mat="covert", rot=[-4, 0, 0], pivot_y=top)
                feather_plane(f"{bn}_low_{layer}", bn, s, WING_Z + dz * 1.0, low, mat="covert")
            # the leading edge: a thick rounded roll of small feathers
            d = np.array([s * (p1[0] - p0[0]), p1[1] - p0[1], 0.0])
            th = 3.4 if bn != hand else 2.8
            chain(f"{bn}_edge", bn, "wingedge", (s * p0[0], p0[1] - 0.6, WING_Z),
                  [((d[1], -d[0], 0) if False else d, float(np.linalg.norm(d)) + 0.8, th)])


def build():
    BONES.clear()
    CUBES.clear()
    bone("root", None, (0, 0, 0))
    bone("hover", "root", (0, 38, 0))   # lifted by every wing beat

    # --- no pelvis: the body ends in its spine. The upper lumbar vertebrae are fixed; below them the spine hangs
    # free in four links (spine_tail0..3) that the game swings behind the Reaper's motion (see
    # WailingReaperModel), each link lagging more than the one above.
    bone("body", "hover", (0, 38, 0))
    box("spine_glow", "body", "core", 0, 46, 1.5, 2.4, 9, 2.4)
    for k in range(2):
        box(f"lumbar{k}", "body", "bone", 0, 47.5 + k * 3, 1.5, 4.5, 2, 4)
    parent = "body"
    for k, (top, verts, w) in enumerate(((47, (44, 41), (4.3, 4.0)), (40.5, (37.5, 34.5), (3.4, 3.0)),
                                          (34, (31.2,), (2.4,)))):
        bn = f"spine_tail{k}"
        bone(bn, parent, (0, top, 1.5))
        low = verts[-1]
        box(f"{bn}_glow", bn, "core", 0, low + 0.3, 1.5, 1.5, top - low, 1.5)
        for v, wv in zip(verts, w):
            box(f"{bn}_v{v}", bn, "bone", 0, v, 1.5, wv, 2, wv * 0.85)
            box(f"{bn}_spur{v}", bn, "bone", 0, v + 0.4, 1.5 + wv * 0.55, 1, 1.2, 1.4)   # spinous process
        parent = bn
    chain("spine_tip", "spine_tail2", ["bone", "pale"], (0, 31.4, 1.5), [((0, -1, 0.15), 2.0, 1.4), ((0, -1, 0.25), 1.6, 0.8)])

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
    box("scarf_front", "cowl", "cloth", 0, 73, -6.8, 17, 5, 3)
    box("scarf_back", "cowl", "cloth", 0, 72, 7.3, 18, 6, 3)
    for s in (-1, 1):
        box(f"scarf_side{s}", "cowl", "cloth", s * 8.5, 73, 0.5, 3, 5, 14)
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

    build_wings()

    # --- head: a reaper's deep hood. A cowl of the same ragged violet cloth as the scarf, an overhanging brim
    # and side folds framing a deep opening; inside only darkness and two red embers for eyes (painted with a
    # glow fading into the black); its peak droops down the back.
    bone("head", "chest", (0, 80, 0))
    k = HOOD_SCALE

    def hb(name, mat, cx, y0, cz, sx, sy, sz):   # a hood box, scaled toward (0, 78.5, 0.4)
        box(name, "head", mat, cx * k, 78.5 + (y0 - 78.5) * k, 0.4 + (cz - 0.4) * k, sx * k, sy * k, sz * k)

    hb("hood_top", "cloth", 0, 94.5, 0.4, 11.2, 2.6, 12.4)
    hb("hood_crown", "cloth", 0, 97.1, 0.9, 10.0, 1.2, 10.8)
    hb("hood_back", "cloth", 0, 78.5, 5.8, 11.6, 16.6, 2.0)
    hb("hood_void", "void", 0, 80.0, -0.5, 8.6, 14.4, 8.0)          # the darkness inside
    CUBES[-1].density = 4                                          # fine enough for the eye glow
    hb("hood_brim", "cloth", 0, 92.4, -5.6, 11.4, 2.3, 3.0)        # overhangs the opening
    hb("hood_brim_lip", "cloth", 0, 91.6, -6.9, 8.4, 1.0, 1.0)
    for sd in (-1, 1):
        hb(f"hood_side{sd}", "cloth", sd * 5.3, 78.5, 0.0, 1.8, 16.0, 11.6)
        hb(f"hood_fold{sd}", "cloth", sd * 4.6, 78.8, -6.1, 1.6, 14.2, 2.6)   # the deep front folds
        hb(f"hood_fold_in{sd}", "cloth", sd * 3.9, 82.0, -5.2, 0.8, 10.4, 1.4)
    # the peak, drooping down the back in three folds
    chain("hood_peak", "head", "cloth", (0, 78.5 + (97.6 - 78.5) * k, 0.4 + 3.6 * k),
          [((0, 0.35, 1), 3.2 * k, 4.6 * k), ((0, -0.45, 1), 3.0 * k, 3.4 * k), ((0, -1, 0.45), 3.4 * k, 2.4 * k),
           ((0, -1, 0.15), 2.6 * k, 1.4 * k)])
    bone("jaw", "head", (0, 84, 2))   # kept (empty) so the animations' jaw keys stay valid

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
            elif m in ("plume", "covert"):
                col = paint_plume(c, x, y, f)
                glow[j, i] = True
            elif m == "wingedge":   # rows of tiny scale-like feathers
                t = 0.62 + lt * 0.5 + n * 0.06 - (0.12 if (j + (i // 2)) % 3 == 0 else 0) + (0.15 if top else 0)
                col = pick(R["wing"], t)
                glow[j, i] = True
            elif m == "helm":
                t = 0.42 + lt + n * 0.08 + (0.14 if top else 0) - (0.1 if edge or bottom else 0)
                if hash3(round(x * 2), round(y * 2), round(z * 2), 21) > 0.975:
                    t += 0.28   # rivets
                col = pick(R["helm"], t)
            elif m == "trim":
                col = pick(R["trim"], 0.5 + lt + n * 0.08 + (0.15 if top else 0))
            elif m == "void":
                col = hexc("030205")
                if f == "north":
                    for ex in (-1.9 * HOOD_SCALE, 1.9 * HOOD_SCALE):
                        r = math.hypot((x - ex) / 0.5, (y - (78.5 + (87.6 - 78.5) * HOOD_SCALE)) / 0.3)
                        if r < 1:
                            col = hexc("fff0e6") if r < 0.45 else hexc("ff3a2a")
                            glow[j, i] = True
                        elif r < 2.8:
                            k = (1 - (r - 1) / 1.8) ** 2
                            col = hexc("030205") * (1 - k) + hexc("c00a0a") * k
                            col[3] = 255
                            glow[j, i] = True
            elif m == "redeye":
                col = hexc("ff1a1a") if not side or (0 < i < W - 1 or W <= 2) else hexc("b00000")
                glow[j, i] = True
            elif m == "fang":
                col = pick(R["pale"], 0.85 - 0.5 * (j + 0.5) / H + n * 0.1) if side else pick(R["pale"], 0.5)
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


def paint_plume(c, x, y, f):
    """Overlapping spectral feathers (they glow): each long blade has a bright rim so neighbours read apart,
    a darker core, a faint shaft and a bright point; later feathers lie on top of earlier ones."""
    u = c.side * x
    if f not in ("north", "south"):
        return hexc("000000", 0)   # the planes' paper-thin rims stay invisible
    hit = None
    for k, (ru, ry, ang, length, hw) in enumerate(c.feathers):
        a = math.radians(ang)
        du, dy = u - ru, y - ry
        along = du * math.sin(a) - dy * math.cos(a)
        across = du * math.cos(a) + dy * math.sin(a)
        if not (0 <= along <= length):
            continue
        w = hw * min(1.0, 0.55 + along / 4)
        tip = length - hw * 2.6
        if along > tip:
            w *= (length - along) / (hw * 2.6)   # long blade-like point
        if abs(across) <= w:
            hit = (k, along / length, across, w)
    if hit is None:
        return hexc("000000", 0)
    k, v, across, w = hit
    cov = c.mat == "covert"
    t = (0.5 if cov else 0.46) - 0.18 * v + 0.05 * hash3(k, c.lo[0], 3) - (0.04 if f == "south" else 0)
    edge = w - abs(across)
    if edge < 0.5:
        t = 0.72 + 0.1 * (1 - v)                     # bright rim, like light caught on a spectral blade
    elif edge < 1.0:
        t += 0.14                                    # soft inner glow next to the rim
    elif abs(across) < 0.3 and not cov:
        t += 0.12                                    # shaft
    if v > 0.82:
        t += 0.12                                    # the tips burn brighter
    return pick(R["wing"], t)


def skull_face(col, x, y):
    """Front of the cranium: deep sockets slanting down toward the nose (the eye slits glow inside them)
    and an inverted-triangle nasal hole."""
    for s in (-1, 1):
        a = math.radians(s * 22)
        dx, dy = x - s * 2.9, y - 89.0
        u, v = dx * math.cos(a) + dy * math.sin(a), -dx * math.sin(a) + dy * math.cos(a)
        if (u / 2.4) ** 2 + (v / 1.25) ** 2 < 1.0:
            return SOCKET.copy()
    if 86.6 <= y < 88.4 and abs(x) < (y - 86.6) * 0.55 + 0.35:
        return SOCKET.copy()
    return col


def pack_and_paint():
    faces = []
    for c in CUBES:
        for f in FACES:
            a, b = face_dims(c, f)
            faces.append((c, f, max(1, round(a * c.density)), max(1, round(b * c.density))))
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


def render_preview(base, out, yaws=(0, 35, 90, 180), scale=4, sub=3):
    Wp, Hp = 190 * scale, 150 * scale
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
    amb = {}   # always-on layer: wings, hover, cape (the state animations never touch these bones)
    # every frequency is a multiple of 25 deg/s so the 14.4 s loop wraps seamlessly
    # --- wing beat, after the classic flight-cycle rules (Animation Mentor "How to Animate Birds in Flight",
    # Williams' Animator's Survival Kit): a big sweep from the shoulder, the elbow and the hand following
    # later and later so a wave runs out along the wing (overlap, follow-through); a faster, fully spread
    # power stroke and a slower recovery with the wing partly folded; the body lifting on the power stroke.
    # The wings hang as broad surfaces facing forward, so the stroke must move that face, not slide the wing
    # edge-on in its own plane: each beat starts high and swept back, then drives down AND forward, face
    # first, scooping air (the body rises), and recovers up and back half folded.
    # Beat phase psi: 0 = top of the beat, 180 = bottom. The warp makes the power stroke ~40% of the beat.
    psi = f"({t} * {BEAT} + 20 - 20 * math.cos({t} * {BEAT}))"

    def elev(bias, amp, lag):   # elevation of a wing segment (degrees above the spread pose)
        return f"({bias} + {amp} * math.cos({psi} - {lag}))"

    def sweep(bias, amp, lag):  # how far a segment is swept back (negative = forward)
        return f"({bias} + {amp} * math.cos({psi} - {lag}))"

    root, fore, hand = elev(-4, 26, 0), elev(-3, 28, 35), elev(-2, 30, 70)
    sw_root, sw_fore, sw_hand = sweep(14, 30, 0), sweep(14, 33, 30), sweep(14, 36, 60)
    fold = f"(10 * (1 - math.sin({psi})))"   # 0 mid-power-stroke (spread), 20 mid-recovery (tucked)
    # signs (checked in game): +z lifts the right wing's tip, +y sweeps it back; the left side mirrors
    for side, s in (("left", -1), ("right", 1)):
        z = "" if s > 0 else "-"
        amb[f"{side}_wing"] = {"rotation": {0: [0, f"{s} * {sw_root}", f"{z}{root}"]}}
        amb[f"{side}_wing_fore"] = {"rotation": {0: [0, f"{s} * ({sw_fore} - {sw_root} + {fold} * 0.6)", f"{z}({fore} - {root})"]}}
        amb[f"{side}_wing_hand"] = {"rotation": {0: [0, f"{s} * ({sw_hand} - {sw_fore} + {fold})", f"{z}({hand} - {fore})"]}}
    # the body rises through each downstroke (a little after it, for weight) and settles on the upstroke
    amb["hover"] = {"position": {0: [0, f"-1.6 * math.cos({psi} - 30)", 0]},
                    "rotation": {0: [f"math.cos({psi} - 60) * 1.5", 0, 0]}}
    for k, (_, _, fan) in enumerate(CAPE):
        amb[f"cape{k}"] = {"rotation": {0: [f"7 + math.sin({t} * 100 + {k * 40}) * 5", 0, f"{fan} + math.sin({t} * 75 + {k * 50}) * 2"]}}
        amb[f"cape{k}_low"] = {"rotation": {0: [f"6 + math.sin({t} * 100 + {k * 40 + 70}) * 9", 0, f"math.sin({t} * 100 + {k * 50}) * 4"]}}

    # ------------------------------------------------------------------ state animations ("main" controller)
    # Built on the three-phase attack anatomy (GDKeys "Anatomy of an Attack", gameanim.com "12 principles of
    # animation in video games"): a long, readable ANTICIPATION whose silhouette is unique to each attack;
    # a short, accelerating STRIKE landing exactly on the server tick the attack fires, with the hit pose
    # held a moment; FOLLOW-THROUGH past the hit; then a slow-out RECOVERY to the neutral pose. Torso leads,
    # arms drag behind, the head trails; wings join in through the *_wing_pose bones, on top of the beat.
    # Rotation signs: x+ = limb swings back / chest bows / head looks down / jaw opens; y+ turns toward the
    # model's right; z+ lifts the right arm / right wing outward (left side mirrored).
    # Keyframes are {time: vector} or {time: (vector, easing)}; the easing shapes the move INTO that key.
    def m(frames):   # mirror right-side keys onto the left side (negate y and z)
        out = {}
        for k, v in frames.items():
            vec, ease = v if isinstance(v, tuple) else (v, None)
            neg = [vec[0]] + [(-x if not isinstance(x, str) else f"-({x})") for x in vec[1:]]
            out[k] = (neg, ease) if ease else neg
        return out

    def arms(right_arm, right_fore=None, right_claws=None, right_wing=None):
        d = {"right_arm": {"rotation": right_arm}, "left_arm": {"rotation": m(right_arm)}}
        if right_fore:
            d["right_forearm"] = {"rotation": right_fore}
            d["left_forearm"] = {"rotation": right_fore}
        if right_claws:
            d["right_claws"] = {"rotation": right_claws}
            d["left_claws"] = {"rotation": m(right_claws)}
        if right_wing:
            d["right_wing_pose"] = {"rotation": right_wing}
            d["left_wing_pose"] = {"rotation": m(right_wing)}
        return d

    sway = f"math.sin({t} * 120)"
    idle = {   # 6 s loop, menacing and alive: slow breath, a hunting look around, claws flexing
        "chest": {"rotation": {0: [f"3 + math.sin({t} * 120 + 40) * 2", 0, 0]},
                  "scale": {0: [f"1 + {sway} * 0.015", 1, f"1 + {sway} * 0.015"]}},
        "left_arm": {"rotation": {0: [f"math.sin({t} * 120) * 4", 0, "-7"]}},
        "right_arm": {"rotation": {0: [f"math.sin({t} * 120 + 90) * 4", 0, "7"]}},
        "left_forearm": {"rotation": {0: [f"-10 + math.sin({t} * 120 + 30) * 3", 0, 0]}},
        "right_forearm": {"rotation": {0: [f"-10 + math.sin({t} * 120 + 120) * 3", 0, 0]}},
        "left_claws": {"rotation": {0: [0, 0, f"-math.sin({t} * 180) * 8"]}},
        "right_claws": {"rotation": {0: [0, 0, f"math.sin({t} * 180 + 90) * 8"]}},
        "head": {"rotation": {0: [f"4 + math.sin({t} * 60) * 3", f"math.sin({t} * 60) * 12", f"math.sin({t} * 120) * 2"]}},
        "jaw": {"rotation": {0: [f"4 + math.sin({t} * 120) * 3", 0, 0]}},
    }

    # BOLT, 1.4 s, the bolt leaves the hand at 0.8 s. Anticipation: the right arm rises and cocks far
    # behind the head, the torso coils back and to the right, the left claw reaches out to aim, the wings
    # flare. Strike: an overhand whip, elbow extending last, claws snapping open. Follow-through across the
    # body, then a slow settle.
    bolt = arms(
        {0: [0, 0, 7], 0.3: ([-110, 0, 32], "easeOutSine"), 0.62: ([-172, 12, 22], "easeInOutSine"),
         0.8: ([-62, -8, 4], "easeInCubic"), 0.95: ([-38, -14, -10], "easeOutQuad"), 1.4: ([0, 0, 7], "easeInOutSine")},
        right_wing={0: [0, 0, 0], 0.62: ([0, -8, 16], "easeInOutSine"), 0.8: ([0, -22, 4], "easeInCubic"),
                    1.0: ([0, -16, 0], "easeOutQuad"), 1.4: ([0, 0, 0], "easeInOutSine")})
    bolt["left_arm"]["rotation"] = {0: [0, 0, -7], 0.62: ([-48, 10, -22], "easeInOutSine"),
                                    0.8: ([8, 0, -14], "easeInCubic"), 1.4: ([0, 0, -7], "easeInOutSine")}
    bolt.update({
        "right_forearm": {"rotation": {0: [-10, 0, 0], 0.3: ([-45, 0, 0], "easeOutSine"), 0.62: ([-100, 0, 0], "easeInOutSine"),
                                       0.74: ([-70, 0, 0], "easeInCubic"), 0.8: ([-4, 0, 0], "easeInCubic"),
                                       0.95: ([-14, 0, 0], "easeOutQuad"), 1.4: ([-10, 0, 0], "easeInOutSine")}},
        "left_forearm": {"rotation": {0: [-10, 0, 0], 0.62: ([-28, 0, 0], "easeInOutSine"), 1.4: ([-10, 0, 0], "easeInOutSine")}},
        "right_claws": {"rotation": {0: [0, 0, 0], 0.5: ([0, 0, -22], "easeInOutSine"), 0.8: ([0, 0, 32], "easeOutBack"),
                                     1.1: ([0, 0, 12], "easeOutQuad"), 1.4: ([0, 0, 0], "easeInOutSine")}},
        "left_claws": {"rotation": {0: [0, 0, 0], 0.62: ([0, 0, -25], "easeInOutSine"), 1.4: ([0, 0, 0], "easeInOutSine")}},
        "chest": {"rotation": {0: [0, 0, 0], 0.62: ([-9, 26, -3], "easeInOutSine"), 0.8: ([12, -16, 2], "easeInCubic"),
                               0.95: ([15, -20, 2], "easeOutQuad"), 1.4: ([0, 0, 0], "easeInOutSine")}},
        "head": {"rotation": {0: [0, 0, 0], 0.62: ([-6, -20, 0], "easeInOutSine"), 0.86: ([8, 12, 0], "easeInCubic"),
                              1.4: ([0, 0, 0], "easeInOutSine")}},
        "jaw": {"rotation": {0: [4, 0, 0], 0.62: ([12, 0, 0], "easeInOutSine"), 0.82: ([34, 0, 0], "easeOutBack"),
                             1.05: ([28, 0, 0], "easeOutQuad"), 1.4: ([4, 0, 0], "easeInOutSine")}},
        "body": {"position": {0: [0, 0, 0], 0.62: ([0, 2, 0], "easeInOutSine"), 0.8: ([0, -1.5, 0], "easeInCubic"),
                              1.4: ([0, 0, 0], "easeInOutSine")}},
    })

    # ERUPT, 1.5 s, the eruption opens under the target at 0.7 s; afterwards it is spent (stunned).
    # Anticipation: it hunches low over the ground, wings mantling forward, claws spread and plunging
    # down. Strike: it tears both arms up over its head, chest arching, head thrown back, jaw wide, wings
    # bursting upward, as if ripping the souls out of the earth. Hold, then sag (into the stun).
    erupt = arms(
        {0: [0, 0, 7], 0.4: ([-36, 0, 30], "easeInOutSine"), 0.5: ([-30, 0, 26], "easeInSine"),
         0.72: ([-150, 0, 22], "easeOutCubic"), 0.88: ([-166, 0, 26], "easeOutSine"), 1.15: ([-156, 0, 30], "easeInOutSine"),
         1.5: ([-40, 0, 14], "easeInOutSine")},
        right_fore={0: [-10, 0, 0], 0.4: ([-22, 0, 0], "easeInOutSine"), 0.72: ([-8, 0, 0], "easeOutCubic"),
                    0.88: ([-4, 0, 0], "easeOutSine"), 1.5: ([-14, 0, 0], "easeInOutSine")},
        right_claws={0: [0, 0, 0], 0.4: ([0, 0, 30], "easeInOutSine"), 0.52: ([0, 0, -26], "easeInQuad"),
                     0.76: ([0, 0, 34], "easeOutCubic"), 0.9: ([0, 0, 38], "easeOutSine"), 1.5: ([0, 0, 6], "easeInOutSine")},
        right_wing={0: [0, 0, 0], 0.4: ([0, -32, -18], "easeInOutSine"), 0.74: ([0, 8, 24], "easeOutCubic"),
                    0.9: ([0, 12, 30], "easeOutSine"), 1.5: ([0, 0, -6], "easeInOutSine")})
    erupt.update({
        "body": {"position": {0: [0, 0, 0], 0.4: ([0, -6, 0], "easeOutQuad"), 0.72: ([0, 2.5, 0], "easeOutCubic"),
                              0.9: ([0, 3.5, 0], "easeOutSine"), 1.5: ([0, -2, 0], "easeInOutSine")}},
        "chest": {"rotation": {0: [0, 0, 0], 0.4: ([34, 0, 0], "easeInOutSine"), 0.5: ([38, 0, 0], "easeInSine"),
                               0.72: ([-14, 0, 0], "easeOutCubic"), 0.88: ([-20, 0, 0], "easeOutSine"), 1.5: ([16, 0, 0], "easeInOutSine")}},
        "head": {"rotation": {0: [0, 0, 0], 0.4: ([22, 0, 0], "easeInOutSine"), 0.76: ([-26, 0, 0], "easeOutCubic"),
                              0.92: ([-32, 0, 0], "easeOutSine"), 1.5: ([18, 0, 0], "easeInOutSine")}},
        "jaw": {"rotation": {0: [4, 0, 0], 0.4: ([8, 0, 0], "easeInOutSine"), 0.74: ([36, 0, 0], "easeOutCubic"),
                             0.92: ([42, 0, 0], "easeOutSine"), 1.5: ([16, 0, 0], "easeInOutSine")}},
    })

    def shake(amp, speed=1800):
        return f"math.sin({t} * {speed}) * {amp}"

    # ULT: in half a second the arms are thrown up to the sky (the runes appear at 1.5 s), wings flung wide, head
    # back - then ult_hold keeps them raised, trembling with effort, for as long as the ice rain lasts.
    ult = arms(
        {0: [0, 0, 7], 0.15: ([20, 0, 14], "easeOutSine"), 0.5: ([-172, 0, 18], "easeOutBack"), 1.5: ([-168, 0, 20], "easeInOutSine")},
        right_fore={0: [-10, 0, 0], 0.5: ([-4, 0, 0], "easeOutBack"), 1.5: ([-6, 0, 0], "easeInOutSine")},
        right_claws={0: [0, 0, 0], 0.5: ([0, 0, 38], "easeOutBack"), 1.5: ([0, 0, 34], "easeInOutSine")},
        right_wing={0: [0, 0, 0], 0.5: ([0, 12, 32], "easeOutCubic"), 1.5: ([0, 10, 30], "easeInOutSine")})
    ult.update({
        "chest": {"rotation": {0: [0, 0, 0], 0.15: ([10, 0, 0], "easeOutSine"), 0.5: ([-16, 0, 0], "easeOutBack"), 1.5: ([-14, 0, 0], "easeInOutSine")}},
        "head": {"rotation": {0: [0, 0, 0], 0.5: ([-38, 0, 0], "easeOutBack"), 1.5: ([-34, 0, 0], "easeInOutSine")}},
        "body": {"position": {0: [0, 0, 0], 0.15: ([0, -1.5, 0], "easeOutSine"), 0.5: ([0, 3, 0], "easeOutBack"), 1.5: ([0, 3, 0], "easeInOutSine")}},
    })
    tremble = f"math.sin({t} * 1440) * 2"
    ult_hold = arms(   # 2 s loop (frequencies are multiples of 180 deg/s)
        {0: [f"-168 + {tremble}", 0, f"20 + math.sin({t} * 180) * 3"]},
        right_fore={0: ["-6", 0, 0]},
        right_claws={0: [0, 0, f"34 + math.sin({t} * 360) * 4"]},
        right_wing={0: [0, 10, f"30 + math.sin({t} * 180) * 4"]})
    ult_hold.update({
        "chest": {"rotation": {0: [f"-14 + math.sin({t} * 180) * 2", 0, f"math.sin({t} * 1440) * 1"]}},
        "head": {"rotation": {0: [f"-34 + math.sin({t} * 180 + 60) * 3", 0, 0]}},
        "body": {"position": {0: [0, f"3 + math.sin({t} * 180) * 0.6", 0]}},
    })

    # STUNNED, 4 s loop (all frequencies are multiples of 90 deg/s so it wraps cleanly): spent and reeling,
    # slumped forward and swaying, head lolling, jaw slack, arms dangling, wings drooping.
    stunned = arms(
        {0: [f"12 + math.sin({t} * 90) * 5", 0, f"3 + math.sin({t} * 90 + 45) * 3"]},
        right_fore={0: [f"-4 + math.sin({t} * 180) * 3", 0, 0]},
        right_claws={0: [0, 0, f"10 + math.sin({t} * 270) * 4"]},
        right_wing={0: [0, f"10 + math.sin({t} * 90) * 4", f"-24 + math.sin({t} * 90 + 90) * 4"]})
    stunned.update({
        "body": {"position": {0: [f"math.sin({t} * 90) * 1", f"-5 + math.sin({t} * 180) * 1", 0]}},
        "chest": {"rotation": {0: [f"28 + math.sin({t} * 180) * 3", f"math.sin({t} * 90) * 5", f"math.sin({t} * 90) * 7"]}},
        "head": {"rotation": {0: [f"36 + math.sin({t} * 180 + 60) * 6", f"math.sin({t} * 90 + 30) * 12", f"math.sin({t} * 90 + 120) * 9"]}},
        "jaw": {"rotation": {0: [f"22 + math.sin({t} * 180) * 5", 0, 0]}},
    })

    # EMERGE, 2.5 s: it rises out of the altar curled up (cocooned in its wings, arms crossed, head bowed),
    # slows as it clears the ground, gathers itself, then unfurls in one violent burst and settles.
    emerge = arms(
        {0: [-72, 60, -10], 1.6: ([-74, 64, -12], "easeOutCubic"), 1.85: ([-66, 70, -16], "easeInQuad"),
         2.05: ([-28, 0, 96], "easeOutExpo"), 2.5: ([0, 0, 7], "easeInOutSine")},
        right_fore={0: [-75, 0, 0], 1.85: ([-84, 0, 0], "easeInQuad"), 2.05: ([-6, 0, 0], "easeOutExpo"),
                    2.5: ([-10, 0, 0], "easeInOutSine")},
        right_claws={0: [0, 0, -24], 1.85: ([0, 0, -28], "easeInQuad"), 2.05: ([0, 0, 40], "easeOutBack"),
                     2.5: ([0, 0, 0], "easeInOutSine")},
        right_wing={0: [0, -58, -12], 1.6: ([0, -60, -12], "easeOutCubic"), 1.85: ([0, -66, -14], "easeInQuad"),
                    2.05: ([0, 24, 34], "easeOutExpo"), 2.5: ([0, 0, 0], "easeInOutSine")})
    emerge.update({
        "root": {"position": {0: [0, -70, 0], 1.6: ([0, -4, 0], "easeOutCubic"), 2.05: ([0, 3, 0], "easeOutBack"),
                              2.5: ([0, 0, 0], "easeInOutSine")}},
        "chest": {"rotation": {0: [44, 0, 0], 1.6: ([36, 0, 0], "easeOutCubic"), 1.85: ([42, 0, 0], "easeInQuad"),
                               2.05: ([-22, 0, 0], "easeOutExpo"), 2.5: ([0, 0, 0], "easeInOutSine")}},
        "head": {"rotation": {0: [40, 0, 0], 1.85: ([44, 0, 0], "easeInQuad"), 2.1: ([-34, 0, 0], "easeOutExpo"),
                              2.5: ([0, 0, 0], "easeInOutSine")}},
        "jaw": {"rotation": {0: [4, 0, 0], 1.85: ([2, 0, 0], "easeInQuad"), 2.1: ([42, 0, 0], "easeOutBack"),
                             2.5: ([4, 0, 0], "easeInOutSine")}},
    })

    # DEATH, 3 s (the body is removed at 3.0 s): the killing blow snaps it back with arms and wings flung
    # out; it convulses and clutches at its fading core; then it folds forward and sinks away, wings
    # collapsing, jaw hanging.
    death = arms(
        {0: [0, 0, 7], 0.22: ([-42, 0, 74], "easeOutCubic"), 0.6: ([-55, 20, 60], "easeInOutSine"),
         1.25: ([-80, 52, -4], "easeInOutCubic"), 3.0: ([14, 0, 14], "easeInQuad")},
        right_fore={0: [-10, 0, 0], 0.22: ([-8, 0, 0], "easeOutCubic"), 1.25: ([-92, 0, 0], "easeInOutCubic"),
                    3.0: ([-12, 0, 0], "easeInQuad")},
        right_claws={0: [0, 0, 0], 0.22: ([0, 0, 40], "easeOutBack"), 1.25: ([0, 0, -28], "easeInOutCubic"),
                     3.0: ([0, 0, 10], "easeInQuad")},
        right_wing={0: [0, 0, 0], 0.22: ([0, 18, 34], "easeOutCubic"), 1.25: ([0, 6, 8], "easeInOutCubic"),
                    3.0: ([0, 30, -48], "easeInQuad")})
    death.update({
        "root": {"position": {0: [0, 0, 0], 1.25: ([0, 2, 0], "easeOutSine"), 3.0: ([0, -46, 0], "easeInCubic")}},
        "chest": {"rotation": {0: [0, 0, 0], 0.22: ([-26, 0, 8], "easeOutCubic"), 0.6: ([-14, 0, shake(4, 1600)], "easeInOutSine"),
                               1.25: ([30, 0, -10], "easeInOutCubic"), 3.0: ([70, 0, 16], "easeInQuad")}},
        "head": {"rotation": {0: [0, 0, 0], 0.26: ([-46, 0, 10], "easeOutCubic"), 1.25: ([-18, 16, 0], "easeInOutCubic"),
                              3.0: ([46, 0, 22], "easeInQuad")}},
        "jaw": {"rotation": {0: [4, 0, 0], 0.26: ([42, 0, 0], "easeOutBack"), 3.0: ([50, 0, 0], "easeInQuad")}},
    })

    # every state animation keys the same set of pose bones (zeros where unused), so the 4-tick blends
    # between states never snap a bone back
    pose = {"root": "position", "body": "position", "chest": "rotation", "head": "rotation", "jaw": "rotation",
            "left_arm": "rotation", "right_arm": "rotation", "left_forearm": "rotation", "right_forearm": "rotation",
            "left_claws": "rotation", "right_claws": "rotation", "left_wing_pose": "rotation", "right_wing_pose": "rotation"}
    for anim in (idle, bolt, erupt, ult, ult_hold, stunned, emerge, death):
        for b, ch in pose.items():
            anim.setdefault(b, {}).setdefault(ch, {0: [0, 0, 0]})
    return {
        "animation.wailing_reaper.ambient": (True, AMBIENT_LEN, amb),
        "animation.wailing_reaper.idle": (True, 6.0, idle),
        "animation.wailing_reaper.bolt": (False, 1.4, bolt),
        "animation.wailing_reaper.erupt": (False, 1.5, erupt),
        "animation.wailing_reaper.ult": (False, 1.5, ult),
        "animation.wailing_reaper.ult_hold": (True, 2.0, ult_hold),
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
                        "visible_bounds_width": 10, "visible_bounds_height": 8, "visible_bounds_offset": [0, 3.5, 0]},
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
                    bb[bname][ch] = {}
                    for t, v in kfs.items():
                        vec, ease = v if isinstance(v, tuple) else (v, "easeInOutSine")
                        bb[bname][ch][str(t)] = {"vector": vec, "easing": ease}
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
                    v = v[0] if isinstance(v, tuple) else v
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


ICE = ramp("3a8fd0", "6fc4f0", "a6e4ff", "d8f6ff", "ffffff")


def ice_textures():
    """The ultimate's ice shard: a frost rune for the ground warning (double ring, six snowflake spokes with
    branches, rune ticks between the rings), a soft fill disc that grows inside it, and the crystal's ice."""
    S = 128
    rune = np.zeros((S, S, 4), np.float32)
    fill = np.zeros((S, S, 4), np.float32)
    c = (S - 1) / 2
    for y in range(S):
        for x in range(S):
            dx, dy = (x - c) / c, (y - c) / c
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            if d > 1:
                continue
            fill[y, x] = ICE[2].copy()
            fill[y, x, 3] = 255 * (0.25 + 0.75 * d ** 3)
            v = 0.0
            v = max(v, 1 - abs(d - 0.95) / 0.035)                       # outer ring
            v = max(v, (1 - abs(d - 0.80) / 0.02) * 0.8)                # inner ring
            sector = (a + math.pi) % (math.pi / 3) - math.pi / 6       # six spokes
            perp = abs(math.sin(sector)) * d
            if d < 0.8:
                v = max(v, (1 - perp / 0.025) * (1 - d * 0.5))
                for bd in (0.35, 0.58):                                 # snowflake branches
                    along = d - bd
                    if 0 < along < 0.14:
                        v = max(v, (1 - abs(perp - along * 0.9) / 0.02) * 0.85)
            if 0.82 < d < 0.93 and (int((a + math.pi) / (math.pi / 18)) % 3 == 0):   # rune ticks
                v = max(v, 0.7 if abs(d - 0.875) < 0.03 else 0)
            v = max(0.0, min(1.0, v))
            if v > 0:
                col = ICE[4] if v > 0.8 else ICE[3] if v > 0.5 else ICE[2]
                rune[y, x] = col
                rune[y, x, 3] = 255 * min(1, v * 1.3)
            if d < 0.95 and rune[y, x, 3] == 0:
                rune[y, x] = ICE[1]
                rune[y, x, 3] = 255 * 0.12 * d                         # faint frost haze in the circle
    shard = np.zeros((32, 32, 4), np.float32)
    rnd = random.Random(11)
    for y in range(32):
        for x in range(32):
            t = 0.45 + 0.4 * (1 - y / 31) + (rnd.random() - 0.5) * 0.2
            if (x + y // 3) % 8 == 0:
                t += 0.3                                                # bright streaks along the crystal
            shard[y, x] = pick(ICE, t)
    return rune, fill, shard


def boss_bar_texture():
    """The Reaper's boss bar (256x64): a dark-metal frame with spiked end caps holding red gems and a small
    crown of spikes in the middle (0,0 256x20; the bar slot is x 26..229, y 7..12), the violet fill with a
    notch every 10% (0,24 204x6) and a soft white shimmer that slides along it (0,32 24x6)."""
    img = np.zeros((64, 256, 4), np.float32)
    metal = ramp("0d0a14", "1a1426", "2a2140", "3d3159", "574a7a", "8577a8")
    gem, gem_rim = hexc("ff3434"), hexc("9c0010")

    def put(x, y, c):
        if 0 <= x < 256 and 0 <= y < 64:
            img[y, x] = c

    # plate
    for y in range(3, 17):
        for x in range(18, 238):
            t = 0.55 - (y - 3) / 14 * 0.4 + (0.18 if y == 3 else 0) - (0.2 if y == 16 else 0)
            put(x, y, pick(metal, t))
    for x in range(20, 236, 6):   # engraved studs
        put(x, 4, metal[5])
        put(x, 15, metal[3])
    # end caps: pointed, like spread wing tips
    for side in (0, 1):
        for y in range(0, 20):
            for x in range(0, 28):
                # inside a lozenge pointing outward
                dy = abs(y - 9.5)
                reach = 1 + (27 - 1) * (1 - dy / 9.5) ** 0.6 if dy < 9.5 else 0
                if 27 - x > reach:
                    continue
                t = 0.6 - y / 19 * 0.45 + (0.2 if 27 - x > reach - 1.2 else 0)
                c = pick(metal, t)
                put(x if side == 0 else 255 - x, y, c)
        gx = 12 if side == 0 else 243
        for dy in range(-1, 2):
            for dx in range(-1, 2):
                put(gx + dx, 9 + dy, gem if dx == 0 or dy == 0 else gem_rim)
        put(gx - 1, 8, hexc("ffb0b0"))
    # a crown of three spikes over the middle, and one under it
    for cx, tip in ((119, 0), (128, -2), (137, 0)):   # spike tip rows (the plate starts at y 3)
        for y in range(max(0, tip), 4):
            w = (y - tip) // 2
            for x in range(cx - w - 1, cx + w + 1):
                put(x, y, metal[4] if x < cx else metal[3])
    for y in range(17, 20):
        for x in range(127 - (19 - y), 129 + (19 - y)):
            put(x, y, metal[2])
    for x in (127, 128):
        put(x, 4, gem)
        put(x, 5, gem_rim)
    # bar slot
    for y in range(6, 14):
        for x in range(25, 231):
            put(x, y, hexc("2a1745") if y in (6, 13) or x in (25, 230) else hexc("070310"))
    # fill
    rows = ["e2c6ff", "a35cff", "8236f0", "6624cc", "4a16a3", "320c75"]
    for y in range(6):
        for x in range(204):
            c = hexc(rows[y]) * (0.92 + 0.08 * math.sin(x * 0.3))
            c[3] = 255
            if 1 <= y <= 4 and x > 0 and int(x % 20.4) == 0:
                c = hexc("1a0640")
            img[24 + y, x] = c
    # shimmer
    for y in range(6):
        for x in range(24):
            img[32 + y, x] = [255, 255, 255, 170 * (1 - abs(x - 11.5) / 12)]
    return img


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
    rune, fill, shard = ice_textures()
    (t / "ice_rune.png").write_bytes(png(rune))
    (t / "ice_fill.png").write_bytes(png(fill))
    (t / "ice_shard.png").write_bytes(png(shard))
    gui = ASSETS / "textures/gui"
    gui.mkdir(parents=True, exist_ok=True)
    (gui / "reaper_boss_bar.png").write_bytes(png(boss_bar_texture()))
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
