"""Kitsune, the ancient fox spirit of the astral: hand-built blocky GeckoLib model, painted texture + glowmask,
animations. A big white fox (about three blocks long) with one great tail, red markings (a mask around the eyes,
a flame on the brow, the ear insides, the tail tip) and amber eyes.

Space: one unit = one model pixel, 16 to a block; the fox faces -z, +x is its left. Rotations here are in this
"model space" (rx > 0 lifts whatever points forward or down toward the front: nose up, a leg swings forward;
a part pointing backward, like the tail, drops). The geo / animation files get them as (-rx, -ry, rz), the way
GeckoLib reads them.

    uv run --no-project --with pillow --with numpy tools/gen_kitsune.py [--preview out.png]
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/soulless"
TEX_W = 256


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


FUR = ramp("9aa0b4", "b4b9ca", "c9cdda", "d9dce6", "e5e7ee", "eff0f5", "f7f8fb", "ffffff")
RED = ramp("6e0f1c", "8c1424", "a91c2e", "c4283a", "dc3c4a", "ee5a5e")
EAR_IN = ramp("7a2a38", "9a3646", "b84a58")
EYE, PUPIL = hexc("ffc04a"), hexc("2a1206")
NOSE = ramp("1c1418", "2a1f25", "3a2c33")


class Cube:
    def __init__(self, name, bone, mat, lo, hi):
        self.name, self.bone, self.mat = name, bone, mat
        self.lo = tuple(min(a, b) for a, b in zip(lo, hi))
        self.hi = tuple(max(a, b) for a, b in zip(lo, hi))
        self.uv = {}

    @property
    def size(self):
        return tuple(h - l for l, h in zip(self.lo, self.hi))


BONES, CUBES = {}, []


def bone(name, parent, pivot):
    BONES[name] = {"name": name, "parent": parent, "pivot": [float(v) for v in pivot]}


def box(name, b, mat, x0, y0, z0, x1, y1, z1):
    CUBES.append(Cube(name, b, mat, (x0, y0, z0), (x1, y1, z1)))


def mirror_box(name, b, mat, x0, y0, z0, x1, y1, z1):
    """A pair: as given (the fox's left, +x) and mirrored to the right."""
    box(name + "_l", b, mat, x0, y0, z0, x1, y1, z1)
    box(name + "_r", b, mat, -x1, y0, z0, -x0, y1, z1)


# ------------------------------------------------------------------ model (standing rest pose)
def build():
    bone("root", None, (0, 0, 0))
    bone("body", "root", (0, 22, 0))
    box("torso", "body", "fur", -7, 16, -14, 7, 29, 16)
    box("ruff", "body", "ruff", -6, 14, -17, 6, 27, -11)          # the white chest fluff
    box("back_fluff", "body", "fur", -5, 28, -10, 5, 30, 12)       # a soft ridge along the back
    # neck and head
    bone("neck", "body", (0, 26, -12))
    box("neck", "neck", "fur", -4.5, 23, -19, 4.5, 33, -10)
    bone("head", "neck", (0, 31, -18))
    box("skull", "head", "head", -7, 29, -27, 7, 40, -16)
    box("snout", "head", "snout", -3, 29, -33, 3, 34, -27)
    box("nose", "head", "nose", -1, 32.5, -33.6, 1, 34, -33)
    mirror_box("cheek", "head", "ruff", 7, 29, -24, 9, 34, -17)
    for side, sx in (("l", 1), ("r", -1)):
        # a big triangular fox ear, stepped: a wide base, a narrower middle, a red tip
        bone(f"ear_{side}", "head", (4.5 * sx, 40, -21))
        x0, x1 = sorted((1.5 * sx, 7 * sx))
        box(f"ear_{side}", f"ear_{side}", "ear", x0, 40, -22.5, x1, 44, -19.5)
        x0, x1 = sorted((2.5 * sx, 6 * sx))
        box(f"ear_mid_{side}", f"ear_{side}", "ear", x0, 44, -22, x1, 47, -20)
        x0, x1 = sorted((3.5 * sx, 5 * sx))
        box(f"ear_tip_{side}", f"ear_{side}", "ear_tip", x0, 47, -21.5, x1, 49, -20.5)
        # the eyelids: closed at rest (scale 1); the animations open them by squashing them up to nothing
        bone(f"lid_{side}", "head", (5 * sx, 36.2, -27))
        x0, x1 = sorted((3.4 * sx, 6.6 * sx))
        box(f"lid_{side}", f"lid_{side}", "lid", x0, 33.8, -27.3, x1, 36.2, -27)
    # legs (children of the body: lowering the body carries them)
    for side, sx in (("l", 1), ("r", -1)):
        bone(f"leg_f{side}", "body", (4.5 * sx, 18, -10))
        x0, x1 = sorted((2.5 * sx, 6.5 * sx))
        box(f"leg_f{side}", f"leg_f{side}", "leg", x0, 0, -12, x1, 18, -8)
        bone(f"leg_b{side}", "body", (4.5 * sx, 20, 11))
        x0, x1 = sorted((2 * sx, 7 * sx))
        box(f"thigh_{side}", f"leg_b{side}", "fur", x0, 10, 7, x1, 21, 15)
        x0, x1 = sorted((2.5 * sx, 6.5 * sx))
        box(f"shin_{side}", f"leg_b{side}", "leg", x0, 0, 11, x1, 11, 15)
    # the great tail: four links, each pivoting at the end of the one before
    links = [(16, 24, 3.5, 25.5), (24, 35, 5.5, 25.5), (35, 45, 5, 25.5), (45, 51, 3.5, 25.5)]
    parent = "body"
    for k, (z0, z1, half, cy) in enumerate(links):
        bone(f"tail{k}", parent, (0, cy, z0))
        box(f"tail{k}", f"tail{k}", "tail_tip" if k == 3 else ("tail_end" if k == 2 else "tail"),
            -half, cy - half, z0, half, cy + half, z1)
        parent = f"tail{k}"


# ------------------------------------------------------------------ texture
FACES = ("north", "south", "east", "west", "up", "down")
LIGHT = {"up": 0.1, "down": -0.22, "north": 0.0, "south": -0.03, "east": -0.06, "west": -0.06}


def face_dims(c, f):
    sx, sy, sz = c.size
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[f]


def face_point(c, f, u, v):
    (x0, y0, z0), (x1, y1, z1) = c.lo, c.hi
    y = y1 - v * (y1 - y0)
    return {
        "north": (x1 - u * (x1 - x0), y, z0), "south": (x0 + u * (x1 - x0), y, z1),
        "east": (x1, y, z1 - u * (z1 - z0)), "west": (x0, y, z0 + u * (z1 - z0)),
        "up": (x0 + u * (x1 - x0), y1, z0 + v * (z1 - z0)), "down": (x0 + u * (x1 - x0), y0, z0 + v * (z1 - z0)),
    }[f]


def fur(x, y, z, f, base=0.62, streak=True):
    """White fur: soft noise, darker underneath, faint strands running down the sides."""
    n = hash3(round(x), round(y), round(z), 1) - 0.5
    t = base + LIGHT[f] + n * 0.12
    if streak and f not in ("up", "down"):
        t -= 0.06 * (hash3(round(x * 2), 0, round(z * 2), 4) > 0.7)
    t += (y - 20) * 0.006
    return pick(FUR, t)


def head_marks(x, y, z, f):
    """The red mask on the face; (colour, glows) or None."""
    ax = abs(x)
    if f == "north":
        if 3.4 <= ax <= 6.6 and 33.8 <= y <= 36.2:   # the eyes (under the lids)
            return (PUPIL if abs(ax - 5) < 0.6 else EYE), True
        if 2.8 <= ax <= 7 and 36.4 <= y <= 37.6 + (ax - 2.8) * 0.5:   # a red stroke over each eye, rising outward
            return pick(RED, 0.55 + hash3(x, y, 2) * 0.2), True
        if 6.6 <= ax <= 7 and 32.5 <= y <= 36.5:   # trailing down the outer corner
            return pick(RED, 0.5), True
        if ax <= 1.2 and 37.5 <= y <= 39.5 - ax:   # the flame on the brow
            return pick(RED, 0.7), True
    if f == "up" and ax <= 1 and z <= -23:
        return pick(RED, 0.6), True   # the flame runs back over the crown
    return None


def paint(c, f, W, H):
    img = np.zeros((H, W, 4), np.float32)
    glow = np.zeros((H, W), bool)
    m = c.mat
    side = f not in ("up", "down")
    for j in range(H):
        for i in range(W):
            x, y, z = face_point(c, f, (i + 0.5) / W, (j + 0.5) / H)
            g = False
            if m in ("fur", "head", "lid"):
                col = fur(x, y, z, f)
                if m == "head":
                    mk = head_marks(x, y, z, f)
                    if mk:
                        col, g = mk
            elif m == "ruff":   # the chest and cheek fluff: brighter, long strands
                col = fur(x, y, z, f, base=0.8)
                if side and (i + int(hash3(i, 0, 0, 3) * 3)) % 3 == 0:
                    col = fur(x, y, z, f, base=0.66)
            elif m == "snout":
                col = fur(x, y, z, f, base=0.74, streak=False)
            elif m == "nose":
                col = pick(NOSE, 0.5 + LIGHT[f] + (hash3(i, j) - 0.5) * 0.3)
            elif m == "ear":
                col = fur(x, y, z, f)
                if f == "north" and 0 < i < W - 1 and j > 0:   # the inside of the ear
                    col = pick(EAR_IN, 0.3 + j / H * 0.6)
            elif m == "ear_tip":
                col = pick(RED, 0.4 + LIGHT[f])
            elif m == "leg":
                col = fur(x, y, z, f, base=0.58)
                if 3 <= y < 4.5 and side:   # a thin red band above the paw
                    col, g = pick(RED, 0.5), True
                elif y < 3 and side:
                    col = fur(x, y, z, f, base=0.5, streak=False)
            elif m == "tail":
                col = fur(x, y, z, f, base=0.66)
            elif m == "tail_end":   # white running into red toward the tip
                k = (z - 35) / 9
                col = fur(x, y, z, f, base=0.66)
                if hash3(round(x), round(y), round(z), 7) < k * k * 0.9:
                    col, g = pick(RED, 0.45 + k * 0.3), True
            elif m == "tail_tip":
                col, g = pick(RED, 0.5 + (z - 44) / 6 * 0.45 + LIGHT[f]), True
            else:
                col = hexc("ff00ff")
            img[j, i] = col
            glow[j, i] = g
    return img, glow


def pack_and_paint():
    faces = []
    for c in CUBES:
        for f in FACES:
            a, b = face_dims(c, f)
            faces.append((c, f, max(1, math.ceil(a - 1e-6)), max(1, math.ceil(b - 1e-6))))
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


# ------------------------------------------------------------------ poses and animations
# A pose: bone -> {"rot": (rx, ry, rz) model space, "pos": (0, dy, dz), "scale": (sx, sy, sz)}
OPEN = {"scale": (1, 0.05, 1)}   # eyelids squashed up: eyes open

#: how far the body sinks to lie down (model pixels): set per model before the animations are made
DROP = -15


def lying_body():
    return {"body": {"pos": (0, DROP, 0)},
            "leg_fl": {"rot": (84, 0, 0)}, "leg_fr": {"rot": (84, 0, 0)},
            "leg_bl": {"rot": (82, 0, -10)}, "leg_br": {"rot": (82, 0, 10)}}


def sleep_pose():
    p = lying_body()
    p.update({
        "neck": {"rot": (-34, -38, 0)}, "head": {"rot": (-14, -38, -12)},
        "ear_l": {"rot": (-20, 0, 10)}, "ear_r": {"rot": (-20, 0, -10)},
        "tail0": {"rot": (28, 48, 0)}, "tail1": {"rot": (6, 52, 0)}, "tail2": {"rot": (0, 50, 0)}, "tail3": {"rot": (0, 40, 0)},
    })
    return p


def awake_pose():
    p = lying_body()
    p.update({
        "neck": {"rot": (18, 0, 0)}, "head": {"rot": (-6, 0, 0)},
        "lid_l": OPEN, "lid_r": OPEN,
        "tail0": {"rot": (26, 34, 0)}, "tail1": {"rot": (4, 34, 0)}, "tail2": {"rot": (0, 28, 0)}, "tail3": {"rot": (0, 22, 0)},
    })
    return p


def sit_pose():
    """Sitting up: the body tipped back onto the haunches, front legs straight down, hind legs folded under."""
    return {
        "body": {"rot": (35, 0, 0)},
        "leg_fl": {"rot": (-35, 0, 0)}, "leg_fr": {"rot": (-35, 0, 0)},
        "leg_bl": {"rot": (55, 0, -8)}, "leg_br": {"rot": (55, 0, 8)},
        "neck": {"rot": (-20, 0, 0)}, "head": {"rot": (0, 0, 0)},
        "lid_l": OPEN, "lid_r": OPEN,
        "tail0": {"rot": (-35, 0, 0)},
    }


def merge(base, extra):
    out = dict(base)
    out.update(extra)
    return out


def keyframes(frames):
    """[(time, pose)] -> bone channels keyed by time (bones a pose leaves out sit at rest then)."""
    bones = sorted(set().union(*(set(pose) for _, pose in frames)))
    out = {}
    for b in bones:
        chans = {}
        for ch in ("rot", "pos", "scale"):
            if not any(ch in pose.get(b, {}) for _, pose in frames):
                continue
            rest = (1, 1, 1) if ch == "scale" else (0, 0, 0)
            key = {"rot": "rotation", "pos": "position", "scale": "scale"}[ch]
            chans[key] = {}
            for t, pose in frames:
                v = pose.get(b, {}).get(ch, rest)
                chans[key][str(t)] = to_file_rot(v) if ch == "rot" else ([0, r(v[1]), r(v[2])] if ch == "pos" else [r(x) for x in v])
        out[b] = chans
    return out


def stand_pose():
    return {
        "neck": {"rot": (28, 0, 0)}, "head": {"rot": (6, 0, 0)},
        "lid_l": OPEN, "lid_r": OPEN,
        "tail0": {"rot": (-30, 0, 0)}, "tail1": {"rot": (-18, 0, 0)}, "tail2": {"rot": (8, 0, 0)}, "tail3": {"rot": (18, 0, 0)},
    }


def r(v):
    return round(float(v), 3)


def to_file_rot(rot):
    return [r(-rot[0]), r(-rot[1]), r(rot[2])]


def animations():
    """name -> (loop, length, {bone: {channel: value or {time: value}}}) in file (GeckoLib) space."""
    t = "query.anim_time"
    out = {}

    def static(pose, extra=None):
        bones = {}
        for b, tr in pose.items():
            bones[b] = {}
            if "rot" in tr:
                bones[b]["rotation"] = to_file_rot(tr["rot"])
            if "pos" in tr:
                bones[b]["position"] = [0, r(tr["pos"][1]), r(tr["pos"][2])]
            if "scale" in tr:
                bones[b]["scale"] = [r(v) for v in tr["scale"]]
        for b, chans in (extra or {}).items():
            bones.setdefault(b, {}).update(chans)
        return bones

    def breathe(depth, rate):
        return {"body": {"position": [0, f"{DROP} + math.sin({t} * {rate}) * {depth}", 0],
                         "scale": [f"1 + math.sin({t} * {rate}) * 0.012", f"1 + math.sin({t} * {rate}) * 0.018", 1]}}

    # sleeping, curled up: slow breaths, the tail tip twitching now and then
    sp = sleep_pose()
    sleep = static(sp, breathe(0.35, 60))
    tip = sp["tail3"]["rot"]
    sleep["tail3"]["rotation"] = [r(-tip[0]), f"{r(-tip[1])} + math.sin({t} * 30) * 4", 0]
    sleep["ear_l"]["rotation"] = [20, 0, f"10 + math.max(0, math.sin({t} * 45) - 0.92) * 120"]
    out["animation.kitsune.sleep"] = (True, 12.0, sleep)

    # awake, lying with the head up: breathing, the tail sweeping, ears turning, a blink every few seconds
    ap = awake_pose()
    awake = static(ap, breathe(0.25, 90))
    for k, amp in enumerate((6, 9, 12, 14)):
        base = ap[f"tail{k}"]["rot"]
        awake[f"tail{k}"]["rotation"] = [r(-base[0]), f"{r(-base[1])} + math.sin({t} * 40 - {k * 25}) * {amp}", 0]
    awake["ear_l"] = {"rotation": [f"math.sin({t} * 25) * 6", 0, f"math.sin({t} * 50) * 5"]}
    awake["ear_r"] = {"rotation": [f"math.sin({t} * 25 + 40) * 6", 0, f"-math.sin({t} * 50 + 90) * 5"]}
    # a blink: the lids drop for a moment once every 5 s
    blink = f"0.05 + 0.95 * math.clamp((math.sin({t} * 72) - 0.97) * 40, 0, 1)"
    for side in ("l", "r"):
        awake[f"lid_{side}"] = {"scale": [1, blink, 1]}
    out["animation.kitsune.idle"] = (True, 9.0, awake)

    # the reforging: she sits up, bows her head to the sleeper and touches the blade with her nose just as it is
    # made whole (2.5 s = KitsuneEntity.REFORGED_AT), her tail sweeping round; then she lifts her head, tail high
    sit = sit_pose()
    bow = merge(sit, {"neck": {"rot": (-58, 0, 0)}, "head": {"rot": (-22, 0, 0)}, "tail0": {"rot": (-30, 25, 0)}})
    touch = merge(bow, {"tail0": {"rot": (-30, -25, 0)}})
    proud = merge(sit, {"neck": {"rot": (-8, 0, 0)}, "head": {"rot": (22, 0, 0)}, "tail0": {"rot": (-72, 0, 0)}})
    out["animation.kitsune.reforge"] = ("hold_on_last_frame", 4.25,
                                        keyframes([(0.0, ap), (0.9, sit), (1.7, bow), (2.5, touch), (3.3, proud), (4.2, proud)]))
    return out


def write_anims(model="kitsune"):
    anims = {}
    for name, (loop, length, bones) in animations().items():
        bb = {}
        for bname, chans in bones.items():
            bb[bname] = {}
            for ch, val in chans.items():
                if isinstance(val, dict):
                    bb[bname][ch] = {k: {"vector": v, "easing": "easeInOutSine"} for k, v in val.items()}
                else:
                    bb[bname][ch] = val
        anims[name] = {"loop": loop, "animation_length": length, "bones": bb}
    p = ASSETS / f"animations/entity/{model}.animation.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=1))


def write_geo(tex_h):
    by_bone = {}
    for c in CUBES:
        by_bone.setdefault(c.bone, []).append(c)
    bones = []
    for b in BONES.values():
        out = {"name": b["name"], "pivot": [r(-b["pivot"][0]), r(b["pivot"][1]), r(b["pivot"][2])]}
        if b["parent"]:
            out["parent"] = b["parent"]
        cubes = [{"origin": [r(-c.hi[0]), r(c.lo[1]), r(c.lo[2])], "size": [r(s) for s in c.size],
                  "uv": {f: {"uv": [u, v], "uv_size": [W, H]} for f, (u, v, W, H) in c.uv.items()}}
                 for c in by_bone.get(b["name"], [])]
        if cubes:
            out["cubes"] = cubes
        bones.append(out)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.kitsune", "texture_width": TEX_W, "texture_height": tex_h,
                        "visible_bounds_width": 5, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}
    p = ASSETS / "geo/entity/kitsune.geo.json"
    p.write_text(json.dumps(geo, indent=1))


# ------------------------------------------------------------------ preview (software render of a pose)
def rotmat(rx, ry, rz):
    a, b, c = (math.radians(v) for v in (rx, ry, rz))
    Rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    Ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    Rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def posed(points, bname, pose):
    """Carry model-space points of a bone through its pose and its parents' (leaf first)."""
    b = bname
    while b:
        tr = pose.get(b, {})
        piv = np.array(BONES[b]["pivot"])
        if "scale" in tr:
            points = (points - piv) * np.array(tr["scale"]) + piv
        if "rot" in tr:
            points = (rotmat(*tr["rot"]) @ (points - piv).T).T + piv
        if "pos" in tr:
            points = points + np.array(tr["pos"])
        b = BONES[b]["parent"]
    return points


def render(base, pose, yaw, pitch=20, scale=6, sub=3):
    Wp, Hp = 110 * scale, 80 * scale
    img = np.zeros((Hp, Wp, 3), np.float32) + np.array([24, 26, 48], np.float32)
    zb = np.full((Hp, Wp), 1e9)
    V = rotmat(pitch, yaw, 0)
    normals = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
    for c in CUBES:
        for f in FACES:
            u0, v0, W, H = c.uv[f]
            us = (np.arange(W * sub) + 0.5) / (W * sub)
            vs = (np.arange(H * sub) + 0.5) / (H * sub)
            uu, vv = np.meshgrid(us, vs)
            pts = np.array([face_point(c, f, a, b) for a, b in zip(uu.ravel(), vv.ravel())])
            ctr = np.array(face_point(c, f, 0.5, 0.5))
            nrm_end = posed(np.array([ctr, ctr + np.array(normals[f])]), c.bone, pose)
            nrm = V @ (nrm_end[1] - nrm_end[0])
            if nrm[2] > 0.05:
                continue
            shade = 0.6 + 0.3 * max(0, -nrm[2]) + 0.25 * max(0, nrm[1])
            pts = (V @ posed(pts, c.bone, pose).T).T
            tex = base[v0 + np.minimum((vv.ravel() * H).astype(int), H - 1), u0 + np.minimum((uu.ravel() * W).astype(int), W - 1)]
            px = (Wp / 2 - pts[:, 0] * scale).astype(int)
            py = (Hp * 0.8 - pts[:, 1] * scale).astype(int)
            for X, Y, Z, col in zip(px, py, pts[:, 2], tex):
                if col[3] <= 0:
                    continue
                for dx in range(-2, 3):
                    for dy in range(-2, 3):
                        XX, YY = X + dx, Y + dy
                        if 0 <= XX < Wp and 0 <= YY < Hp and Z < zb[YY, XX]:
                            zb[YY, XX] = Z
                            img[YY, XX] = col[:3] * shade
    return img


def preview(base, out):
    rows = []
    for pose in (sleep_pose(), awake_pose(), stand_pose()):
        rows.append(np.concatenate([render(base, pose, yaw) for yaw in (30, 90, 160)], axis=1))
    Image.fromarray(np.clip(np.concatenate(rows, axis=0), 0, 255).astype(np.uint8)).save(out)


# ------------------------------------------------------------------ the plain variant: a vanilla white fox
# The same animations on the vanilla fox's shape and texture (minecraft:textures/entity/fox/snow_fox.png, 48x32, box
# UV), for comparison. Geometry from FoxModel.createBodyLayer, turned into this file's space (y up, ground at 0):
# the body and the tail are boxes standing up, laid down by their bones' rest rotations.
PLAIN_BONES = [
    # name, parent, pivot, rest rotation (model space), cubes [(lo, hi, box uv)]
    ("root", None, (0, 0, 0), None, []),
    ("body", "root", (0, 8, 0), None, []),
    ("body_rot", "body", (0, 8, -6), (-90, 0, 0), [((-3, -7, -9.5), (3, 4, -3.5), (24, 15))]),
    ("neck", "body", (0, 7.5, -3), None, []),
    ("head", "neck", (0, 7.5, -3), None, [((-4, 3.5, -8), (4, 9.5, -2), (1, 5)), ((-2, 3.49, -11), (2, 5.49, -8), (6, 18))]),
    ("ear_r", "head", (-3, 9.5, -6.5), None, [((-4, 9.5, -7), (-2, 11.5, -6), (8, 1))]),
    ("ear_l", "head", (3, 9.5, -6.5), None, [((2, 9.5, -7), (4, 11.5, -6), (15, 1))]),
    ("leg_fl", "body", (2, 6.5, 0), None, [((1, 0, -1), (3, 6, 1), (4, 24))]),
    ("leg_fr", "body", (-2, 6.5, 0), None, [((-3, 0, -1), (-1, 6, 1), (13, 24))]),
    ("leg_bl", "body", (2, 6.5, 7), None, [((1, 0, 6), (3, 6, 8), (4, 24))]),
    ("leg_br", "body", (-2, 6.5, 7), None, [((-3, 0, 6), (-1, 6, 8), (13, 24))]),
    ("tail0", "body", (0, 7, 9), (-87, 0, 0), [((-2, -2, 8), (2, 7, 13), (30, 0))]),
]


def write_plain_geo():
    bones = []
    for name, parent, piv, rest, cubes in PLAIN_BONES:
        out = {"name": name, "pivot": [r(-piv[0]), r(piv[1]), r(piv[2])]}
        if parent:
            out["parent"] = parent
        if rest:
            out["rotation"] = to_file_rot(rest)
        if cubes:
            out["cubes"] = [{"origin": [r(-hi[0]), r(lo[1]), r(lo[2])], "size": [r(h - l) for l, h in zip(lo, hi)], "uv": list(uv)}
                            for lo, hi, uv in cubes]
        bones.append(out)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.kitsune_plain", "texture_width": 48, "texture_height": 32,
                        "visible_bounds_width": 3, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
        "bones": bones}]}
    (ASSETS / "geo/entity/kitsune_plain.geo.json").write_text(json.dumps(geo, indent=1))


def main():
    build()
    base, gm, tex_h = pack_and_paint()
    t = ASSETS / "textures/entity"
    Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGBA").save(t / "kitsune.png")
    Image.fromarray(np.clip(gm, 0, 255).astype(np.uint8), "RGBA").save(t / "kitsune_glowmask.png")
    write_geo(tex_h)
    write_anims()
    # the plain variant: a vanilla fox lies down by sinking just 4 pixels
    global DROP
    DROP = -4
    write_plain_geo()
    write_anims("kitsune_plain")
    DROP = -15
    if "--preview" in sys.argv:
        preview(base, sys.argv[sys.argv.index("--preview") + 1])
    print(f"kitsune cubes={len(CUBES)} bones={len(BONES)} texture={TEX_W}x{tex_h}")


if __name__ == "__main__":
    main()
