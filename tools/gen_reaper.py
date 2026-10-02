"""The Wailing Reaper boss: blocky GeckoLib model, texture + glowmask, animations, Blockbench project.
Also the boss-adjacent textures: abyss head skull, soul bolt orb, Soul Altar block faces.

An original soul demon (gaunt, long-armed, horned, cloaked, its lower body a column of violet soul flame),
about 6.5 blocks tall.

    uv run --no-project --with pillow --with numpy tools/gen_reaper.py
"""
import base64
import io
import json
import math
import random
import uuid
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/soulless"
DENSITY = 1
TEX_W = 256
rng_uuid = random.Random(77)


def uid():
    return str(uuid.UUID(int=rng_uuid.getrandbits(128)))


def hexc(h, a=255):
    h = h.lstrip("#")
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a], dtype=np.float32)


def hash2(x, y, seed=0):
    n = math.sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453
    return n - math.floor(n)


def pick(ramp, t):
    t = max(0.0, min(0.999, t))
    return ramp[int(t * len(ramp))].copy()


P = {
    "bone": [hexc("120c18"), hexc("1c1426"), hexc("281d36"), hexc("362848"), hexc("46345c")],
    "pale": [hexc("6e6488"), hexc("9488b2"), hexc("bdb2d8"), hexc("e2dbf5")],
    "cloak": [hexc("0e0814"), hexc("170d22"), hexc("221432"), hexc("2e1b44")],
    "cloak_edge": hexc("6a1fb0"),
    "glow": [hexc("6a14a8"), hexc("9a2cf0"), hexc("c46cff"), hexc("efc9ff")],
    "eye": hexc("f6e2ff"),
    "socket": hexc("07040a"),
}
FIRE = [(0.0, hexc("000000", 0)), (0.15, hexc("3a0a66", 150)), (0.35, hexc("6a14b8", 220)),
        (0.55, hexc("9a2cf0", 255)), (0.75, hexc("c46cff", 255)), (0.9, hexc("ecc0ff", 255)), (1.0, hexc("fff4ff", 255))]


def fire(t):
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(FIRE, FIRE[1:]):
        if t <= t1:
            k = round(((t - t0) / (t1 - t0)) * 3) / 3
            return c0 * (1 - k) + c1 * k
    return FIRE[-1][1]


class Cube:
    def __init__(self, name, bone, mat, lo, hi, rot=None, pivot=None, inflate=0):
        self.name, self.bone, self.mat = name, bone, mat
        self.lo = tuple(min(a, b) for a, b in zip(lo, hi))
        self.hi = tuple(max(a, b) for a, b in zip(lo, hi))
        self.rot, self.pivot = rot, pivot or [0, 0, 0]
        self.uv = {}

    @property
    def size(self):
        return tuple(h - l for l, h in zip(self.lo, self.hi))


BONES, CUBES = [], []


def bone(name, parent, pivot):
    BONES.append({"name": name, "parent": parent, "pivot": list(pivot)})


def cube(*a, **k):
    CUBES.append(Cube(*a, **k))


def box(name, b, mat, cx, cy, cz, sx, sy, sz, **k):
    """Cube by centre x/z and bottom y."""
    cube(name, b, mat, (cx - sx / 2, cy, cz - sz / 2), (cx + sx / 2, cy + sy, cz + sz / 2), **k)


# The model faces -z (Blockbench "north" = front).
def build():
    BONES.append({"name": "root", "parent": None, "pivot": [0, 0, 0]})
    bone("tail", "root", (0, 36, 0))
    bone("tail_mid", "tail", (0, 22, 0))
    bone("tail_tip", "tail_mid", (0, 10, 0))
    bone("body", "root", (0, 36, 0))
    bone("chest", "body", (0, 54, 0))
    bone("head", "chest", (0, 72, 0))
    bone("jaw", "head", (0, 77, -3))
    bone("cape", "chest", (0, 70, 5))
    for s, side in ((-1, "left"), (1, "right")):
        bone(f"{side}_arm", "chest", (s * 12, 68, 0))
        bone(f"{side}_forearm", f"{side}_arm", (s * 13, 47, 0))
        bone(f"{side}_hand", f"{side}_forearm", (s * 13.5, 26, 0))

    # soul-flame lower body, narrowing to a wisp
    box("tail_top", "tail", "flame", 0, 24, 0, 14, 12, 10)
    box("tail_core", "tail", "glowcore", 0, 26, 0, 8, 10, 6)
    box("tail_mid", "tail_mid", "flame", 0, 12, 0, 10, 12, 8)
    box("tail_tip", "tail_tip", "flame", 0, 2, 0, 6, 10, 5)
    box("tail_wisp", "tail_tip", "flame", 0, -4, 0, 3, 6, 3)

    # pelvis, spine and a cage of dark bone ribs over a glowing soul core
    box("pelvis", "body", "bone", 0, 36, 0, 14, 7, 9)
    box("spine", "body", "bone", 0, 43, 1, 6, 11, 5)
    box("core", "chest", "glowcore", 0, 55, 0, 12, 13, 8)
    box("ribcage", "chest", "ribs", 0, 54, 0, 20, 16, 12)
    box("collar", "chest", "bone", 0, 69, 0, 22, 3, 12)
    for s, side in ((-1, "left"), (1, "right")):
        box(f"pauldron_{side}", "chest", "bone", s * 12, 66, 0, 8, 6, 10)
        box(f"pauldron_spike_{side}", "chest", "pale", s * 14, 71, 0, 3, 7, 3, rot=[0, 0, -s * 25], pivot=[s * 14, 71, 0])
        box(f"wisp_{side}", "chest", "glowcore", s * 13, 72, 2, 2, 2, 2)
    box("neck", "chest", "bone", 0, 70, 0.5, 5, 5, 5)

    # skull with glowing sockets, a hanging jaw, two great horns sweeping back and up
    box("skull", "head", "skull", 0, 76, -1, 12, 11, 12)
    box("brow", "head", "bone", 0, 83, -6.5, 13, 2, 2)
    box("jaw", "jaw", "bone", 0, 73, -2, 10, 4, 10)
    for s, side in ((-1, "left"), (1, "right")):
        box(f"horn1_{side}", "head", "bone", s * 7, 83, 0, 3, 7, 3, rot=[25, 0, -s * 30], pivot=[s * 6, 83, 0])
        box(f"horn2_{side}", "head", "pale", s * 10.5, 89, 3, 2.5, 8, 2.5, rot=[45, 0, -s * 15], pivot=[s * 9.5, 88, 2])
        box(f"horn3_{side}", "head", "pale", s * 11.5, 95, 9, 1.5, 6, 1.5, rot=[70, 0, -s * 5], pivot=[s * 11, 94, 7])

    # tattered cape hanging from the shoulders
    for i, (x, length) in enumerate(((-8, 44), (-3, 50), (2, 46), (7, 40))):
        box(f"cape_{i}", "cape", "cloak", x + 2.5, 70 - length, 6, 5, length, 1)

    # long, thin arms ending in clawed hands (hang to the knees, Shadow-Fiend-like proportions)
    for s, side in ((-1, "left"), (1, "right")):
        box(f"upper_{side}", f"{side}_arm", "bone", s * 13, 47, 0, 4, 21, 4)
        box(f"band_{side}", f"{side}_arm", "glowcore", s * 13, 55, 0, 4.4, 1, 4.4)
        box(f"fore_{side}", f"{side}_forearm", "bone", s * 13.5, 26, 0, 3.5, 21, 3.5)
        box(f"fore_glow_{side}", f"{side}_forearm", "glowcore", s * 13.5, 30, -1.9, 1.5, 12, 0.5)
        box(f"palm_{side}", f"{side}_hand", "bone", s * 13.5, 20, 0, 6, 6, 3)
        for j, dz in enumerate((-1.2, 0, 1.2)):
            box(f"claw{j}_{side}", f"{side}_hand", "pale", s * 13.5 + s * (j - 1) * 1.6, 11, dz, 1.2, 9, 1.2,
                rot=[0, 0, s * (j - 1) * 6], pivot=[s * 13.5, 20, 0])


# ------------------------------------------------------------------ texture
def face_dims(c, f):
    sx, sy, sz = c.size
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[f]


def paint(c, f, W, H):
    img = np.zeros((H, W, 4), np.float32)
    glow = np.zeros((H, W), bool)
    m = c.mat
    for j in range(H):
        for i in range(W):
            n = hash2(i + c.lo[0] * 3, j + c.lo[1] * 5, len(c.name))
            v = (j + 0.5) / H
            if m == "bone":
                t = 0.35 + (n - 0.5) * 0.4 + (0.25 if f == "up" else -0.15 if f == "down" else 0) + (0.15 if j == 0 else 0)
                img[j, i] = pick(P["bone"], t)
                if hash2(i, j, 13) > 0.94:
                    img[j, i] = P["glow"][1]
                    glow[j, i] = True
            elif m == "pale":
                img[j, i] = pick(P["pale"], 0.3 + 0.6 * (1 - v) + (n - 0.5) * 0.3)
            elif m == "ribs":
                if f in ("north", "south", "east", "west") and (j % 4 in (2, 3)):
                    img[j, i] = [0, 0, 0, 0]   # see-through gaps between the ribs show the soul core
                else:
                    img[j, i] = pick(P["bone"], 0.45 + (n - 0.5) * 0.3)
                if f == "north" and abs(i - W / 2) < 1:
                    img[j, i] = pick(P["bone"], 0.8)   # sternum
            elif m == "glowcore":
                img[j, i] = pick(P["glow"], 0.35 + 0.5 * n)
                glow[j, i] = True
            elif m == "cloak":
                t = 0.4 + (n - 0.5) * 0.3 - v * 0.2
                img[j, i] = pick(P["cloak"], t)
                if j >= H - 2 or (j > H - 6 and hash2(i, 3, 1) > 0.5):
                    img[j, i] = P["cloak_edge"] if j < H - 1 else [0, 0, 0, 0]   # ragged glowing hem
                    glow[j, i] = img[j, i][3] > 0
            elif m == "flame":
                heat = 0.95 - v * 0.15 - abs((i + 0.5) / W - 0.5) * 0.5 + (n - 0.5) * 0.35
                if f == "up":
                    heat = 0.6
                img[j, i] = fire(heat)
                glow[j, i] = img[j, i][3] > 0
            elif m == "skull":
                img[j, i] = pick(P["bone"], 0.5 + (n - 0.5) * 0.3 + (0.2 if f == "up" else 0))
                if f == "north":
                    # two slanted, burning eye sockets and a nasal slit
                    for ex in (W * 0.28, W * 0.72):
                        dx, dy = (i + 0.5 - ex) / (W * 0.15), (j + 0.5 - H * 0.42) / (H * 0.13)
                        dy += (dx if ex < W / 2 else -dx) * 0.4
                        if dx * dx + dy * dy < 1:
                            img[j, i] = P["eye"] if dx * dx + dy * dy < 0.25 else P["glow"][2]
                            glow[j, i] = True
                    if abs(i + 0.5 - W / 2) < 1 and H * 0.6 < j < H * 0.75:
                        img[j, i] = P["socket"]
    return img, glow


def pack_and_paint():
    faces = []
    for c in CUBES:
        for f in ("north", "south", "east", "west", "up", "down"):
            a, b = face_dims(c, f)
            faces.append((c, f, max(1, round(a * DENSITY)), max(1, round(b * DENSITY))))
    faces.sort(key=lambda t: -t[3])
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


# ------------------------------------------------------------------ animations
def S(expr):
    return expr


def animations():
    sway = "math.sin(query.anim_time * 120)"
    idle = {
        "root": {"position": {0: [0, f"{sway} * 0.8", 0]}},
        "tail": {"rotation": {0: [f"math.sin(query.anim_time * 140) * 6", 0, f"math.cos(query.anim_time * 110) * 5"]}},
        "tail_mid": {"rotation": {0: [f"math.sin(query.anim_time * 140 + 60) * 9", 0, f"math.cos(query.anim_time * 110 + 60) * 8"]}},
        "tail_tip": {"rotation": {0: [f"math.sin(query.anim_time * 140 + 120) * 12", 0, f"math.cos(query.anim_time * 110 + 120) * 12"]}},
        "cape": {"rotation": {0: [f"8 + math.sin(query.anim_time * 90) * 4", 0, 0]}},
        "left_arm": {"rotation": {0: [f"math.sin(query.anim_time * 120) * 4", 0, "-6"]}},
        "right_arm": {"rotation": {0: [f"math.sin(query.anim_time * 120 + 90) * 4", 0, "6"]}},
        "head": {"rotation": {0: [f"math.sin(query.anim_time * 60) * 3", 0, 0]}},
    }
    bolt = {  # 1.4 s: right hand winds back and hurls forward
        "right_arm": {"rotation": {0: [0, 0, 6], 0.5: [40, 0, 30], 0.8: [-110, 0, 10], 1.4: [0, 0, 6]}},
        "right_forearm": {"rotation": {0: [0, 0, 0], 0.5: [-60, 0, 0], 0.8: [-10, 0, 0], 1.4: [0, 0, 0]}},
        "chest": {"rotation": {0: [0, 0, 0], 0.5: [0, -20, 0], 0.8: [8, 15, 0], 1.4: [0, 0, 0]}},
    }
    erupt = {  # 1.5 s: both arms heave upward as if lifting the ground
        "left_arm": {"rotation": {0: [0, 0, -6], 0.4: [30, 0, -15], 0.7: [-160, 0, -20], 1.2: [-160, 0, -20], 1.5: [0, 0, -6]}},
        "right_arm": {"rotation": {0: [0, 0, 6], 0.4: [30, 0, 15], 0.7: [-160, 0, 20], 1.2: [-160, 0, 20], 1.5: [0, 0, 6]}},
        "chest": {"rotation": {0: [0, 0, 0], 0.4: [15, 0, 0], 0.7: [-12, 0, 0], 1.5: [0, 0, 0]}},
        "head": {"rotation": {0: [0, 0, 0], 0.7: [-20, 0, 0], 1.5: [0, 0, 0]}},
    }
    ult = {  # 3.5 s: hugs itself, trembling, then throws the arms wide and screams
        "left_arm": {"rotation": {0: [0, 0, -6], 0.5: [-70, -60, 10], 2.8: [-70, -65, 10], 3.05: [-20, 0, -95], 3.5: [-20, 0, -95]}},
        "right_arm": {"rotation": {0: [0, 0, 6], 0.5: [-70, 60, -10], 2.8: [-70, 65, -10], 3.05: [-20, 0, 95], 3.5: [-20, 0, 95]}},
        "chest": {"rotation": {0: [0, 0, 0], 0.5: [20, 0, "math.sin(query.anim_time * 2000) * 2"], 2.8: [25, 0, 0], 3.05: [-25, 0, 0], 3.5: [-20, 0, 0]}},
        "head": {"rotation": {0: [0, 0, 0], 0.5: [30, 0, 0], 2.8: [35, 0, 0], 3.05: [-35, 0, 0], 3.5: [-30, 0, 0]}},
        "jaw": {"rotation": {0: [0, 0, 0], 2.9: [0, 0, 0], 3.05: [35, 0, 0], 3.5: [30, 0, 0]}},
    }
    stunned = {  # slumped, swaying
        "chest": {"rotation": {0: [30, 0, f"math.sin(query.anim_time * 90) * 6"]}},
        "head": {"rotation": {0: [35, 0, f"math.sin(query.anim_time * 90 + 40) * 10"]}},
        "left_arm": {"rotation": {0: [10, 0, "-2"]}},
        "right_arm": {"rotation": {0: [10, 0, "2"]}},
        "root": {"position": {0: [0, -6, 0]}},
    }
    emerge = {  # 2.5 s: rises out of the altar's souls, arms lifting
        "root": {"position": {0: [0, -70, 0], 2.0: [0, 0, 0], 2.5: [0, 0, 0]}},
        "left_arm": {"rotation": {0: [-150, 0, -20], 2.0: [-150, 0, -20], 2.5: [0, 0, -6]}},
        "right_arm": {"rotation": {0: [-150, 0, 20], 2.0: [-150, 0, 20], 2.5: [0, 0, 6]}},
        "head": {"rotation": {0: [-30, 0, 0], 2.5: [0, 0, 0]}},
    }
    death = {  # 3 s: collapses and sinks away
        "root": {"position": {0: [0, 0, 0], 3.0: [0, -60, 0]}},
        "chest": {"rotation": {0: [0, 0, 0], 1.0: [40, 0, 10], 3.0: [60, 0, 20]}},
        "head": {"rotation": {0: [0, 0, 0], 1.0: [-40, 0, 0], 3.0: [30, 0, 0]}},
        "jaw": {"rotation": {0: [0, 0, 0], 0.5: [35, 0, 0], 3.0: [35, 0, 0]}},
        "left_arm": {"rotation": {0: [0, 0, -6], 1.0: [-100, 0, -40], 3.0: [10, 0, -10]}},
        "right_arm": {"rotation": {0: [0, 0, 6], 1.0: [-100, 0, 40], 3.0: [10, 0, 10]}},
    }
    return {
        "animation.wailing_reaper.idle": (True, 6.0, idle),
        "animation.wailing_reaper.bolt": (False, 1.4, bolt),
        "animation.wailing_reaper.erupt": (False, 1.5, erupt),
        "animation.wailing_reaper.ult": (False, 3.5, ult),
        "animation.wailing_reaper.stunned": (True, 4.0, stunned),
        "animation.wailing_reaper.emerge": (False, 2.5, emerge),
        "animation.wailing_reaper.death": (False, 3.0, death),
    }


def r(v):
    return round(v, 4)


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
                        "visible_bounds_width": 5, "visible_bounds_height": 8, "visible_bounds_offset": [0, 3.5, 0]},
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
          "name": "wailing_reaper", "model_identifier": "wailing_reaper", "visible_box": [5, 8, 3.5],
          "resolution": {"width": TEX_W, "height": tex_h}, "elements": elements, "outliner": [group("root")],
          "textures": [{"path": "", "name": "wailing_reaper.png", "folder": "entity", "namespace": "soulless", "id": "0",
                        "width": TEX_W, "height": tex_h, "uv_width": TEX_W, "uv_height": tex_h, "particle": True,
                        "render_mode": "default", "visible": True, "internal": True, "saved": False, "uuid": uid(),
                        "source": "data:image/png;base64," + base64.b64encode(tex_png).decode()}],
          "animations": anims}
    (ROOT / "art").mkdir(exist_ok=True)
    (ROOT / "art/wailing_reaper.bbmodel").write_text(json.dumps(bb))


# ------------------------------------------------------------------ extra textures
def abyss_head_texture():
    """64x64 wither-skull layout (head box 8x8x8 at uv 0,35): a dark violet skull with burning eyes."""
    img = np.zeros((64, 64, 4), np.float32)
    rnd = random.Random(5)
    for y in range(35, 51):
        for x in range(0, 32):
            img[y, x] = pick(P["bone"], 0.35 + rnd.random() * 0.4)
    # front face is at (8,43)-(16,51)
    for (ex, ey) in ((10, 46), (13, 46)):
        img[ey, ex] = P["eye"]
        img[ey, ex + 1] = P["glow"][2]
        img[ey + 1, ex] = P["glow"][1]
        img[ey + 1, ex + 1] = P["glow"][2]
    for x in range(10, 15):
        img[49, x] = P["socket"] if x % 2 == 0 else P["pale"][1]
    return img


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
                side[y, x] = P["glow"][1]
    for x in range(16):
        side[0, x] = hexc("2e1d43")
        side[15, x] = hexc("07040b")
    top = base()
    for y in range(4, 12):
        for x in range(4, 12):
            top[y, x] = P["socket"] if 5 <= x <= 10 and 5 <= y <= 10 else hexc("3b2457")
    filled = top.copy()
    for y in range(5, 11):
        for x in range(5, 11):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 3:
                filled[y, x] = P["glow"][3] if d < 1.2 else P["glow"][2] if d < 2.2 else P["glow"][1]
    bottom = base()
    return side, top, filled, bottom


def main():
    build()
    base, gm, tex_h = pack_and_paint()
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
