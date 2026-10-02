"""Procedural asset generator for the Soulless mod.

Builds the Soul Forged GeckoLib model (geo + texture + glowmask + animations), the animated
purple "soul fire" block-atlas sprites, the soul ember particle frames and the effect icon.

    uv run --with pillow --with numpy tools/gen_assets.py
"""
import json
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/soulless"
DENSITY = 2  # texture pixels per model unit (2x vanilla detail)
TEX_W = 256


def hexc(h, a=255):
    h = h.lstrip("#")
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a], dtype=np.float32)


def hash2(x, y, seed=0):
    n = math.sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453
    return n - math.floor(n)


# ----------------------------------------------------------------------------- palette
P = {
    "body": [hexc("1c1528"), hexc("251c35"), hexc("2f2443"), hexc("3a2d52")],
    "body_hi": hexc("4b3a68"),
    "vein": hexc("b54cff"),
    "vein_core": hexc("f0c4ff"),
    "edge": [hexc("6f6698"), hexc("8a81b3"), hexc("a69ecb"), hexc("c6bfe6")],
    "glint": hexc("f4f0ff"),
    "fuller_bg": hexc("2a0745"),
    "fuller_mid": hexc("6d17a8"),
    "rune": hexc("e7a3ff"),
    "rune_core": hexc("ffffff"),
    "bone": [hexc("8a7c62"), hexc("b2a486"), hexc("cfc2a3"), hexc("e6dcc2")],
    "metal": [hexc("17131c"), hexc("221d29"), hexc("2f2838"), hexc("453c52")],
    "rivet": hexc("8f84a3"),
    "leather": [hexc("1e0f19"), hexc("2c1724"), hexc("3b2031"), hexc("4a2a3e")],
    "thread": hexc("7d36b0"),
    "gem": [hexc("3d0866"), hexc("6a14a8"), hexc("9a2fe0"), hexc("c46cff"), hexc("f1d2ff")],
    "eye_dark": hexc("140a1c"),
    "eye_glow": hexc("e27bff"),
}

# Soul fire ramp: intensity 0..1 -> RGBA (transparent at 0)
FIRE_RAMP = [
    (0.00, hexc("000000", 0)),
    (0.10, hexc("1a0430", 0)),
    (0.18, hexc("3b0a6b", 200)),
    (0.32, hexc("6a12b8", 255)),
    (0.50, hexc("9a2cf0", 255)),
    (0.68, hexc("c45cff", 255)),
    (0.82, hexc("e9a8ff", 255)),
    (1.00, hexc("fff4ff", 255)),
]


def fire_color(t):
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(FIRE_RAMP, FIRE_RAMP[1:]):
        if t <= t1:
            k = (t - t0) / (t1 - t0) if t1 > t0 else 0
            # step the ramp a little so it stays pixel-art looking
            k = round(k * 3) / 3
            return c0 * (1 - k) + c1 * k
    return FIRE_RAMP[-1][1]


def pick(ramp, t):
    t = max(0.0, min(0.999, t))
    return ramp[int(t * len(ramp))].copy()


# ----------------------------------------------------------------------------- model
class Cube:
    def __init__(self, bone, mat, x0, y0, z0, x1, y1, z1, rot=None, pivot=None, tag=""):
        self.bone, self.mat, self.tag = bone, mat, tag
        self.lo = (min(x0, x1), min(y0, y1), min(z0, z1))
        self.hi = (max(x0, x1), max(y0, y1), max(z0, z1))
        self.rot, self.pivot = rot, pivot
        self.uv = {}

    @property
    def size(self):
        return tuple(h - l for l, h in zip(self.lo, self.hi))


BONES = []  # (name, parent, pivot, extra)
CUBES = []


def bone(name, parent="root", pivot=(0, 0, 0)):
    BONES.append({"name": name, "parent": parent, "pivot": list(pivot)})
    return name


def cube(*a, **k):
    c = Cube(*a, **k)
    CUBES.append(c)
    return c


def build_model():
    BONES.append({"name": "root", "pivot": [0, 0, 0]})
    bone("handle")
    bone("guard", pivot=(0, 6, 0))
    bone("blade", pivot=(0, 9, 0))
    bone("pommel_gem", "handle", pivot=(0, -14.5, 0))
    bone("flames", "blade", pivot=(0, 9, 0))

    # --- handle: long two-hand grip with metal rings
    cube("handle", "leather", -1.5, -12, -1.5, 1.5, 3, 1.5)
    for y in (-12.5, -4.5, 2):
        cube("handle", "metal", -2, y, -2, 2, y + 1, 2, tag="ring")
    # pommel: claw cage holding a soul gem, with a bottom spike
    cube("handle", "metal", -2.5, -13.5, -2.5, 2.5, -12.5, 2.5, tag="ring")
    cube("pommel_gem", "gem", -1.5, -17, -1.5, 1.5, -13, 1.5)
    for sx in (-1, 1):
        for sz in (-1, 1):
            cube("handle", "metal", sx * 2 - 0.5, -17, sz * 2 - 0.5, sx * 2 + 0.5, -13.5, sz * 2 + 0.5,
                 rot=[sz * -12, 0, sx * 12], pivot=[sx * 2, -13.5, sz * 2])
    cube("handle", "metal", -0.5, -19.5, -0.5, 0.5, -17, 0.5)

    # --- guard: skull centre, bone-and-iron wings sweeping up into horns
    cube("guard", "skull", -4, 3, -2.5, 4, 9, 2.5)
    cube("guard", "metal", -4.5, 2.5, -2, 4.5, 3.5, 2)
    for s in (-1, 1):
        r = -s  # mirror rotations
        cube("guard", "metal", s * 4, 4.5, -2, s * 9, 8, 2)
        cube("guard", "bone", s * 8.5, 5, -1.5, s * 13, 8, 1.5, rot=[0, 0, r * -22], pivot=[s * 8.5, 5, 0])
        cube("guard", "bone", s * 12, 7.5, -1, s * 14, 14, 1, rot=[0, 0, r * -12], pivot=[s * 13, 7.5, 0])
        cube("guard", "bone", s * 12.6, 13.5, -0.5, s * 13.6, 16.5, 0.5, rot=[0, 0, r * -24], pivot=[s * 13, 13.5, 0])
        cube("guard", "gem", s * 6, 5.5, -2.3, s * 7.5, 7, 2.3)  # wing eye gems (front + back)
        # lower fang
        cube("guard", "bone", s * 6, 1, -1, s * 7.5, 4.5, 1, rot=[0, 0, r * 15], pivot=[s * 6.75, 4.5, 0])

    # --- blade: dark soul-steel core, bright stepped bevel, glowing rune fuller
    segs = [(9, 14, 5), (14, 26, 4.5), (26, 38, 4), (38, 47, 3.5), (47, 53, 2.5), (53, 57, 1.5), (57, 59, 0.5)]
    for y0, y1, hw in segs:
        cube("blade", "body", -hw, y0, -1, hw, y1, 1)
        if hw >= 1.5:
            cube("blade", "edge", hw, y0, -0.5, hw + 1, y1, 0.5)
            cube("blade", "edge", -hw - 1, y0, -0.5, -hw, y1, 0.5)
        else:
            cube("blade", "edge", -0.5, y1, -0.5, 0.5, y1 + 2, 0.5)
    cube("blade", "fuller", -1, 11, -1.1, 1, 45, 1.1)
    # soul teeth near the base, raking back toward the guard
    for y in (15, 19.5, 24):
        hw = 4.5
        for s in (-1, 1):
            cube("blade", "edge", s * (hw + 1), y, -0.5, s * (hw + 2.5), y + 1.5, 0.5,
                 rot=[0, 0, -s * 35], pivot=[s * (hw + 1), y, 0])

    # --- flames: thin tongues licking off both edges and the tip
    rnd = random.Random(7)
    profile = {y0: hw for y0, y1, hw in segs}
    def half_width(y):
        for y0, y1, hw in segs:
            if y0 <= y < y1:
                return hw + 1
        return 1
    idx = 0
    for s in (-1, 1):
        y = 10.5
        while y < 54:
            hw = half_width(y)
            h = rnd.uniform(3.5, 6.5) * (0.75 if y > 46 else 1)
            w = rnd.choice([1.5, 2, 2])
            x0 = s * (hw - 0.3)
            name = bone(f"flame_{idx}", "flames", pivot=(x0, y, 0))
            cube(name, "flame", x0, y, -0.25 + rnd.uniform(-0.3, 0.3), x0 + s * w, y + h, 0.25 + rnd.uniform(-0.3, 0.3),
                 rot=[0, 0, -s * rnd.uniform(18, 34)], pivot=[x0, y, 0])
            idx += 1
            y += rnd.uniform(3.2, 4.6)
    for k, (dx, h) in enumerate([(-0.75, 5), (0.25, 7), (0.75, 4.5)]):
        name = bone(f"flame_{idx}", "flames", pivot=(dx, 58, 0))
        cube(name, "flame", dx - 0.75, 57.5, -0.25, dx + 0.75, 57.5 + h, 0.25, rot=[0, 0, -dx * 12], pivot=[dx, 57.5, 0])
        idx += 1
    # flames rising off the front/back of the rune channel
    for k, y in enumerate(range(14, 44, 6)):
        for sz in (-1, 1):
            name = bone(f"flame_{idx}", "flames", pivot=(0, y, sz * 1.1))
            cube(name, "flame", -0.75, y, sz * 1.15, 0.75, y + rnd.uniform(2.5, 4), sz * 1.6,
                 rot=[sz * 25, 0, 0], pivot=[0, y, sz * 1.15])
            idx += 1
    return idx


# ----------------------------------------------------------------------------- painting
def face_dims(c, face):
    sx, sy, sz = c.size
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy),
            "up": (sx, sz), "down": (sx, sz)}[face]


def face_world(c, face, i, j, W, H):
    """World-ish coordinates of the centre of pixel (i, j) on a face (i right, j down)."""
    (x0, y0, z0), (x1, y1, z1) = c.lo, c.hi
    u = (i + 0.5) / W
    v = (j + 0.5) / H
    if face in ("north", "south"):
        return x0 + u * (x1 - x0), y1 - v * (y1 - y0), z0 if face == "north" else z1
    if face in ("east", "west"):
        return x1 if face == "east" else x0, y1 - v * (y1 - y0), z0 + u * (z1 - z0)
    return x0 + u * (x1 - x0), y1 if face == "up" else y0, z0 + v * (z1 - z0)


SKULL = [
    "..############..",
    ".##############.",
    "################",
    "##eeee####eeee##",
    "##eooe####eooe##",
    "##eeee####eeee##",
    "#######nn#######",
    ".######nn######.",
    "..#t#t#t#t#t#t..",
    "..############..",
    "...#.#.#.#.#....",
    "................",
]

RUNES = [  # 4x5 glyphs, '#' lit
    ["#..#", "#.#.", "##..", "#.#.", "#..#"],
    [".##.", "#..#", ".##.", "#..#", ".##."],
    ["####", "..#.", ".#..", "#...", "####"],
    ["#..#", "####", "#..#", ".##.", ".#.."],
    ["#.#.", ".#.#", "#.#.", ".#.#", "#.#."],
    [".#..", "###.", ".#.#", ".###", "..#."],
    ["#...", "##..", "#.#.", "#..#", "####"],
]


def paint(c, face, W, H, rnd):
    img = np.zeros((H, W, 4), np.float32)
    glow = np.zeros((H, W), bool)
    m = c.mat
    for j in range(H):
        for i in range(W):
            x, y, z = face_world(c, face, i, j, W, H)
            n = hash2(round(x * 2), round(y * 2) + round(z * 2) * 57, 3)
            if m == "body":
                hw = max(abs(c.lo[0]), abs(c.hi[0]))
                t = 0.15 + 0.6 * (abs(x) / max(hw, 0.1)) + (n - 0.5) * 0.35
                col = pick(P["body"], t)
                if face in ("up", "down", "east", "west"):
                    col = P["body_hi"].copy()
                img[j, i] = col
            elif m == "edge":
                t = 0.35 + (n - 0.5) * 0.4
                if face == "up":
                    t += 0.4
                if face in ("east", "west"):
                    t += 0.25
                col = pick(P["edge"], t)
                if hash2(round(x * 2), round(y * 2), 11) > 0.93:
                    col = P["glint"].copy()
                img[j, i] = col
            elif m == "fuller":
                img[j, i] = P["fuller_bg"]
                glow[j, i] = True
                if face in ("north", "south"):
                    if i == 0 or i == W - 1:
                        img[j, i] = P["fuller_mid"]
                    else:
                        gy = j // 7
                        gj = j % 7
                        glyph = RUNES[(gy * 3 + (1 if face == "south" else 0)) % len(RUNES)]
                        if gj < 5 and 0 <= gj and glyph[gj][(i - 0) % 4] == "#" and W >= 4:
                            img[j, i] = P["rune"] if (i + j) % 3 else P["rune_core"]
            elif m == "metal":
                t = 0.35 + (n - 0.5) * 0.45
                if face == "up":
                    t += 0.3
                if j == 0 or j == H - 1:
                    t += 0.25
                img[j, i] = pick(P["metal"], t)
                if c.tag == "ring" and face in ("north", "south", "east", "west") and i == W // 2 and H >= 2:
                    img[j, i] = P["rivet"]
            elif m == "bone":
                t = 0.45 + (n - 0.5) * 0.5 + (0.2 if face == "up" else 0) - (0.25 if face == "down" else 0)
                if hash2(round(x * 2), round(y * 2), 5) > 0.9:
                    t -= 0.35  # pits
                img[j, i] = pick(P["bone"], t)
            elif m == "leather":
                band = (round(y * 2) + (i if face in ("north", "south") else W - i)) % 6
                t = [0.75, 0.6, 0.45, 0.3, 0.15, 0.05][band] + (n - 0.5) * 0.2
                img[j, i] = pick(P["leather"], t)
                if band == 5 and (round(y * 2) // 6) % 2 == 0:
                    img[j, i] = P["thread"]
            elif m == "gem":
                t = 0.3 + 0.55 * (1 - v_frac(j, H)) + (n - 0.5) * 0.3
                if (i + j) % 5 == 0:
                    t += 0.25
                img[j, i] = pick(P["gem"], t)
                glow[j, i] = True
            elif m == "skull":
                if face in ("north", "south") and W == 16 and H == 12:
                    ch = SKULL[j][i]
                    if ch == "#":
                        img[j, i] = pick(P["bone"], 0.5 + (n - 0.5) * 0.45 + (0.15 if j < 3 else 0))
                    elif ch == "e":
                        img[j, i] = P["eye_dark"]
                    elif ch == "o":
                        img[j, i] = P["eye_glow"] if (i + j) % 2 else P["vein_core"]
                        glow[j, i] = True
                    elif ch in "nt":
                        img[j, i] = P["eye_dark"]
                    else:
                        img[j, i] = pick(P["metal"], 0.3 + (n - 0.5) * 0.3)
                else:
                    t = 0.45 + (n - 0.5) * 0.5 + (0.2 if face == "up" else -0.1)
                    img[j, i] = pick(P["bone"], t)
            elif m == "flame":
                v = 1 - (j + 0.5) / H  # 0 bottom .. 1 top
                edge = abs((i + 0.5) / W - 0.5) * 2
                heat = 1.0 - v * 0.95 - edge * 0.35 + (hash2(i, j, 31) - 0.5) * 0.35
                if face in ("up", "down"):
                    heat = 0.75
                col = fire_color(heat)
                img[j, i] = col
                glow[j, i] = col[3] > 0
    return img, glow


def v_frac(j, H):
    return (j + 0.5) / H


def add_veins(cubes, atlas, glow):
    """Glowing soul cracks running from the fuller out across the blade body (front and back)."""
    rnd = random.Random(42)
    for c in cubes:
        if c.mat != "body":
            continue
        for face in ("north", "south"):
            if face not in c.uv:
                continue
            u, v, W, H = c.uv[face]
            if W < 8:
                continue
            for _ in range(max(1, H // 10)):
                # start near the centre, walk outward
                x = W // 2 + rnd.choice([-3, 3])
                y = rnd.randrange(H)
                dx = 1 if x > W // 2 else -1
                for step in range(rnd.randint(4, W // 2 + 2)):
                    if not (0 <= x < W and 0 <= y < H):
                        break
                    core = step < 3
                    atlas[v + y, u + x] = P["vein_core"] if core else P["vein"]
                    glow[v + y, u + x] = True
                    x += dx
                    if rnd.random() < 0.45:
                        y += rnd.choice([-1, 1])


def pack(cubes):
    rnd = random.Random(1)
    faces = []
    for c in cubes:
        for f in ("north", "south", "east", "west", "up", "down"):
            a, b = face_dims(c, f)
            W, H = max(1, round(a * DENSITY)), max(1, round(b * DENSITY))
            faces.append((c, f, W, H))
    faces.sort(key=lambda t: -t[3])
    x = y = row_h = 0
    for c, f, W, H in faces:
        if x + W > TEX_W:
            x, y, row_h = 0, y + row_h, 0
        c.uv[f] = (x, y, W, H)
        x += W
        row_h = max(row_h, H)
    tex_h = 1 << math.ceil(math.log2(y + row_h))
    atlas = np.zeros((tex_h, TEX_W, 4), np.float32)
    glow = np.zeros((tex_h, TEX_W), bool)
    for c in cubes:
        for f, (u, v, W, H) in c.uv.items():
            img, g = paint(c, f, W, H, rnd)
            atlas[v:v + H, u:u + W] = img
            glow[v:v + H, u:u + W] = g
    add_veins(cubes, atlas, glow)
    return atlas, glow, tex_h


def r(v):
    return round(v, 4)


def write_geo(tex_h):
    bones = []
    by_bone = {}
    for c in CUBES:
        by_bone.setdefault(c.bone, []).append(c)
    for b in BONES:
        out = {"name": b["name"], "pivot": b["pivot"]}
        if b.get("parent"):
            out["parent"] = b["parent"]
        cubes = []
        for c in by_bone.get(b["name"], []):
            cd = {
                "origin": [r(-c.hi[0]), r(c.lo[1]), r(c.lo[2])],  # bedrock geometry mirrors x
                "size": [r(s) for s in c.size],
                "uv": {f: {"uv": [u, v], "uv_size": [W, H]} for f, (u, v, W, H) in c.uv.items()},
            }
            if c.rot:
                cd["pivot"] = [r(-c.pivot[0]), r(c.pivot[1]), r(c.pivot[2])]
                # GeckoLib reads bedrock rotations as (-x, -y, z) of the Java-space rotation
                cd["rotation"] = [r(-c.rot[0]), r(-c.rot[1]), r(c.rot[2])]
            cubes.append(cd)
        if cubes:
            out["cubes"] = cubes
        out["pivot"] = [r(-out["pivot"][0]), r(out["pivot"][1]), r(out["pivot"][2])]
        bones.append(out)
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.soul_forged",
                "texture_width": TEX_W, "texture_height": tex_h,
                "visible_bounds_width": 4, "visible_bounds_height": 6, "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": bones,
        }],
    }
    p = ASSETS / "geo/item/soul_forged.geo.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(geo, indent=1))


def write_animations(n_flames):
    rnd = random.Random(99)
    idle = {}
    slam = {}
    for k in range(n_flames):
        ph = rnd.randint(0, 360)
        sp = rnd.choice([540, 600, 720, 810])
        idle[f"flame_{k}"] = {
            "scale": [f"1 + math.sin(query.anim_time * {sp // 2} + {ph}) * 0.12",
                      f"1 + math.sin(query.anim_time * {sp} + {ph}) * 0.28",
                      "1"],
            "rotation": ["0", "0", f"math.sin(query.anim_time * {sp // 3} + {ph}) * 7"],
        }
        slam[f"flame_{k}"] = {
            "scale": {
                "0.0": {"vector": [1, 1, 1]},
                "0.35": {"vector": [1.3, 1.8, 1], "easing": "easeOutQuad"},
                "0.5": {"vector": [1.6, 2.6, 1], "easing": "easeOutBack"},
                "1.0": {"vector": [1, 1, 1], "easing": "easeInOutSine"},
            },
            "rotation": ["0", "0", f"math.sin(query.anim_time * 1440 + {ph}) * 12"],
        }
    idle["pommel_gem"] = {"rotation": ["0", "query.anim_time * 90", "0"],
                          "position": ["0", "math.sin(query.anim_time * 180) * 0.25", "0"]}
    slam["pommel_gem"] = {"rotation": ["0", "query.anim_time * 720", "0"]}
    anim = {
        "format_version": "1.8.0",
        "animations": {
            "animation.soul_forged.idle": {"loop": True, "animation_length": 4.0, "bones": idle},
            "animation.soul_forged.slam": {"loop": False, "animation_length": 1.0, "bones": slam},
        },
    }
    p = ASSETS / "animations/item/soul_forged.animation.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(anim, indent=1))


def save_rgba(arr, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(path)


# ----------------------------------------------------------------------------- soul fire / particles
def doom_fire(w, h, frames, seed, step=2):
    rnd = random.Random(seed)
    sim_h = h + 6
    g = np.zeros((sim_h, w))
    g[-1, :] = 1.0
    out = []
    total = 60 + frames * step
    for t in range(total):
        for x in range(w):
            for y in range(1, sim_h):
                decay = rnd.random() * 0.11
                wind = rnd.randint(-1, 1)
                nx = (x + wind) % w
                g[y - 1, nx] = max(0.0, g[y, x] - decay)
        # flicker the source a bit
        for x in range(w):
            g[-1, x] = 0.85 + rnd.random() * 0.15
        if t >= 60 and (t - 60) % step == 0:
            out.append(g[sim_h - h - 2:sim_h - 2].copy())
    return out


def fire_frames_to_strip(frames, w, h, taper=True):
    strip = np.zeros((h * len(frames), w, 4), np.float32)
    for k, f in enumerate(frames):
        for y in range(h):
            for x in range(w):
                v = f[y, x]
                if taper:  # narrower toward the sides like vanilla fire
                    v *= 1.0 - (abs(x + 0.5 - w / 2) / (w / 2)) ** 3 * 0.5
                strip[k * h + y, x] = fire_color(v * 1.08)
    return strip


def write_fire():
    for idx, seed in enumerate((3, 17)):
        frames = doom_fire(16, 16, 32, seed)
        strip = fire_frames_to_strip(frames, 16, 16)
        p = ASSETS / f"textures/block/soul_curse_fire_{idx}.png"
        save_rgba(strip, p)
        (p.with_suffix(".png.mcmeta")).write_text(json.dumps({"animation": {"frametime": 1}}))


def write_particles():
    # 8 frames of a shrinking soul ember / flame tongue, 8x8
    for k in range(8):
        img = np.zeros((8, 8, 4), np.float32)
        size = 1.0 - k / 9
        for y in range(8):
            for x in range(8):
                cx, cy = 3.5, 4.5 + (1 - size) * 1.5
                dx = (x - cx) / (3.2 * size)
                dy = (y - cy) / (3.6 * size)
                # teardrop: narrower toward the top
                if dy < 0:
                    dx *= 1 + (-dy) * 0.9
                d = math.sqrt(dx * dx + dy * dy)
                if d < 1:
                    img[y, x] = fire_color((1 - d) * 1.15 * (0.55 + 0.45 * size) + 0.15)
        save_rgba(img, ASSETS / f"textures/particle/soul_ember_{k}.png")


def write_effect_icon():
    frames = doom_fire(18, 18, 1, 23)
    f = frames[0]
    img = np.zeros((18, 18, 4), np.float32)
    for y in range(18):
        for x in range(18):
            v = f[y, x] * (1 - (abs(x + 0.5 - 9) / 9) ** 2)
            img[y, x] = fire_color(v * 1.25)
    # skull-ish eyes in the flame
    for (x, y) in ((6, 11), (7, 11), (10, 11), (11, 11)):
        img[y, x] = P["eye_dark"]
    save_rgba(img, ASSETS / "textures/mob_effect/soul_curse.png")


def main():
    n_flames = build_model()
    atlas, glow, tex_h = pack(CUBES)
    save_rgba(atlas, ASSETS / "textures/item/soul_forged.png")
    gm = atlas.copy()
    gm[~glow] = 0
    save_rgba(gm, ASSETS / "textures/item/soul_forged_glowmask.png")
    write_geo(tex_h)
    write_animations(n_flames)
    write_fire()
    write_particles()
    write_effect_icon()
    print(f"cubes={len(CUBES)} bones={len(BONES)} flames={n_flames} texture=256x{tex_h}")


if __name__ == "__main__":
    main()
