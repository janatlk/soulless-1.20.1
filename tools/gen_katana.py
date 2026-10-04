"""Katana model generator (GeckoLib item): geometry, texture, Blockbench project and an offline preview.

The blade is a thin plate whose curved outline (sori), point (kissaki) and temper line (hamon) are painted at
high texture resolution, so the curve is smooth rather than stepped; a chain of thin boxes along its back
(mune) gives it real thickness. Then the gold collar (habaki), a round iron guard (tsuba) between washers
(seppa), a silk-wrapped grip (tsuka-ito over white ray skin) with gold ornaments (menuki) and a pommel cap
(kashira). Blade along +y, edge toward +x, the grip below the origin.

    uv run --no-project --with pillow --with numpy tools/gen_katana.py [--preview out.png]
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
NAME = "soul_katana"
TEX_W = 256
rng_uuid = random.Random(4242)


def uid():
    return str(uuid.UUID(int=rng_uuid.getrandbits(128)))


def hexc(h, a=255):
    h = h.lstrip("#")
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a], dtype=np.float32)


def mix(a, b, k):
    k = max(0.0, min(1.0, k))
    return a * (1 - k) + b * k


def hash2(x, y, seed=0):
    n = math.sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453
    return n - math.floor(n)


# ------------------------------------------------------------------ the blade's shape
BLADE_Y0, BLADE_Y1 = 2.6, 46.0          # from the collar to the point
KISSAKI = 4.6                            # length of the point section
SORI = 4.1                               # how far the back bows (toward -x) over the blade
W_BASE, W_TIP = 2.5, 1.95                # blade width (mune to edge)
AXIS_X = 0.1                             # the centre line of the collar, guard and grip: the blade sits on it


def mune_x(y):
    """x of the blade's back (mune) at height y: a gentle curve bowing toward -x, strongest near the tip."""
    t = max(0.0, (y - BLADE_Y0) / (BLADE_Y1 - BLADE_Y0))
    return AXIS_X - W_BASE / 2 - SORI * t ** 1.7


def width(y):
    t = max(0.0, (y - BLADE_Y0) / (BLADE_Y1 - BLADE_Y0))
    w = W_BASE + (W_TIP - W_BASE) * t
    ky = BLADE_Y1 - KISSAKI
    if y > ky:   # the point: the edge sweeps up in an arc to meet the straight back
        k = (y - ky) / KISSAKI
        w *= math.sqrt(max(0.0, 1 - k * k)) * (1 - 0.15 * k) + 0.0
    return w


def hamon(y):
    """Fraction of the width (from the back) where the hardened edge begins: a soft wavy line."""
    return 0.66 + 0.05 * math.sin(y * 1.25) + 0.025 * math.sin(y * 3.4 + 1.0)


# ------------------------------------------------------------------ geometry
class Cube:
    def __init__(self, name, bone, mat, lo, hi, rot=None, pivot=None, density=4):
        self.name, self.bone, self.mat = name, bone, mat
        self.lo = tuple(min(a, b) for a, b in zip(lo, hi))
        self.hi = tuple(max(a, b) for a, b in zip(lo, hi))
        self.rot, self.pivot, self.density = rot, list(pivot or [0, 0, 0]), density
        self.uv = {}

    @property
    def size(self):
        return tuple(h - l for l, h in zip(self.lo, self.hi))


CUBES, BONES = [], []


def box(name, mat, cx, cy, cz, sx, sy, sz, **k):
    CUBES.append(Cube(name, "katana", mat, (cx - sx / 2, cy, cz - sz / 2), (cx + sx / 2, cy + sy, cz + sz / 2), **k))


def build():
    BONES.append({"name": "root", "parent": None, "pivot": [0, 0, 0]})
    BONES.append({"name": "katana", "parent": "root", "pivot": [0, 0, 0]})
    # the blade plate: its outline is painted (see paint_blade)
    lo_x = mune_x(BLADE_Y1) - 0.3
    hi_x = mune_x(BLADE_Y0) + W_BASE + 0.3
    CUBES.append(Cube("blade", "katana", "blade", (lo_x, BLADE_Y0, -0.18), (hi_x, BLADE_Y1 + 0.2, 0.18), density=8))
    # the back (mune): thin boxes following the curve, a little thicker than the plate
    n = 16
    for i in range(n):
        y0 = BLADE_Y0 + (BLADE_Y1 - BLADE_Y0 - 0.2) * i / n
        y1 = BLADE_Y0 + (BLADE_Y1 - BLADE_Y0 - 0.2) * (i + 1) / n
        x0, x1 = mune_x(y0), mune_x(y1)
        ang = math.degrees(math.atan2(-(x1 - x0), y1 - y0))   # tilt so the box follows the back
        th = 0.62 - 0.3 * (i / n)
        CUBES.append(Cube(f"mune{i}", "katana", "mune", (x0, y0 - 0.05, -th / 2), (x0 + 0.42, y1 + 0.05, th / 2),
                          rot=[0, 0, ang], pivot=[x0, y0, 0]))
    # habaki: the gold collar
    box("habaki", "gold", AXIS_X, 1.0, 0, W_BASE + 0.6, 2.2, 1.15)
    # seppa washers and the tsuba (a rounded guard made of three overlapping plates)
    box("seppa_top", "gold", 0.1, 0.75, 0, 3.6, 0.25, 1.6)
    box("tsuba_a", "tsuba", 0.1, 0.0, 0, 7.2, 0.75, 5.2)
    box("tsuba_b", "tsuba", 0.1, 0.0, 0, 5.2, 0.75, 7.2)
    box("tsuba_c", "tsuba", 0.1, 0.0, 0, 6.4, 0.75, 6.4, rot=[0, 45, 0], pivot=[0.1, 0, 0])
    box("seppa_low", "gold", 0.1, -0.25, 0, 3.6, 0.25, 1.6)
    # fuchi (collar of the grip), the wrapped grip, menuki ornaments under the wrap, kashira pommel cap
    box("fuchi", "lacquer", 0.1, -1.4, 0, 2.9, 1.15, 2.0)
    box("tsuka", "tsuka", 0.1, -12.6, 0, 2.6, 11.2, 1.75)
    box("tsuka_round", "tsuka", 0.1, -12.6, 0, 2.1, 11.2, 2.15)
    for s in (-1, 1):
        box(f"menuki{s}", "gold", 0.1 + s * 0.2, -6.4 + s * 1.2, s * 1.0, 1.2, 1.8, 0.3)
    box("kashira", "lacquer", 0.1, -13.7, 0, 2.8, 1.1, 2.1)
    box("kashira_cap", "gold", 0.1, -13.95, 0, 2.2, 0.3, 1.6)


# ------------------------------------------------------------------ texture
FACES = ("north", "south", "east", "west", "up", "down")


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


STEEL = {
    "mune": hexc("454b57"), "shinogi": hexc("5c6370"), "shinogi_ji": hexc("7d8491"), "ji": hexc("939aa7"),
    "nioi": hexc("eef2f7"), "ha": hexc("cfd5de"), "edge": hexc("f8fafc"), "yokote": hexc("aab1bd"),
}
NONE = hexc("000000", 0)


def paint_blade(x, y, f):
    if f not in ("north", "south"):
        return NONE
    m = mune_x(y)
    w = width(y)
    if y < BLADE_Y0 or y > BLADE_Y1 or w <= 0.02 or x < m or x > m + w:
        return NONE
    d = (x - m) / w
    grain = (hash2(round(x * 8), round(y * 1.5), 3) - 0.5) * 0.06   # brushed along the length
    if d < 0.07:
        col = STEEL["mune"]
    elif abs(d - 0.32) < 0.02:
        col = STEEL["shinogi"]                                          # the ridge line
    elif d < 0.32:
        col = STEEL["shinogi_ji"]
    else:
        h = hamon(y)
        if d < h - 0.025:
            col = STEEL["ji"]
        elif d < h + 0.02:
            col = STEEL["nioi"]                                         # the bright misty temper line
        elif d < 0.93:
            col = mix(STEEL["ha"], STEEL["edge"], (d - h) / (0.93 - h) * 0.4)
        else:
            col = STEEL["edge"]
    col = col * (1 + grain)
    col[3] = 255
    if f == "south":
        col = col * np.array([0.93, 0.93, 0.95, 1])
    return col


def paint(c, f, W, H):
    img = np.zeros((H, W, 4), np.float32)
    for j in range(H):
        for i in range(W):
            x, y, z = face_point(c, f, (i + 0.5) / W, (j + 0.5) / H)
            side = f not in ("up", "down")
            edge = side and (i == 0 or i == W - 1)
            n = hash2(i + c.lo[0] * 9, j + c.lo[1] * 7, len(c.name)) - 0.5
            m = c.mat
            if m == "blade":
                col = paint_blade(x, y, f)
            elif m == "mune":
                col = STEEL["mune"] * (1.05 + n * 0.05)
                col[3] = 255
            elif m == "gold":
                col = mix(hexc("8a6420"), hexc("e8c46a"), 0.55 + 0.35 * (1 - (j + 0.5) / H) + n * 0.15)
                if edge:
                    col = col * 0.8
                col[3] = 255
            elif m == "tsuba":
                r = math.hypot(x - 0.1, z)
                col = mix(hexc("15161b"), hexc("2a2b33"), 0.5 + n * 0.4)
                if f in ("up", "down") and r > 3.0:
                    col = mix(hexc("4a3a1c"), hexc("8a6a2c"), 0.5 + n * 0.3)   # a dark gold rim
                if f in ("up", "down") and 1.0 < r < 1.4 and hash2(round(math.atan2(z, x - 0.1) * 6), 0, 7) > 0.5:
                    col = hexc("3a3b45")                                       # pierced-work pattern
                col[3] = 255
            elif m == "lacquer":
                col = mix(hexc("0e0b12"), hexc("231c2c"), 0.5 + n * 0.3 + (0.25 if f == "up" else 0))
                if side and (j == 0 or j == H - 1):
                    col = hexc("a8823a")                                       # gold trim
                col[3] = 255
            elif m == "tsuka":
                col = paint_tsuka(c, x, y, z, f, n)
            else:
                col = hexc("ff00ff")
            img[j, i] = col
    return img


def paint_tsuka(c, x, y, z, f, n):
    """Silk wrap (tsuka-ito): bands crossing over and under each other down the grip, leaving a diamond window
    of white ray skin (samegawa) in the middle of each face between the crossings."""
    if f in ("up", "down"):
        return hexc("1e1426")
    if f in ("north", "south"):
        u = (x - c.lo[0]) / c.size[0]
    else:
        u = (z - c.lo[2]) / c.size[2]
    period = 2.1
    v = ((y - c.lo[1]) % period) / period
    du, dv = abs(u - 0.5) * 2, abs(v - 0.5) * 2
    if du * 1.15 + dv < 0.62:
        k = 1 - (du * 1.15 + dv) / 0.62
        col = mix(hexc("cfc7b5"), hexc("f4efe3"), 0.4 + 0.5 * k + n * 0.25)     # samegawa: pale nodules
        if hash2(round(x * 8), round(y * 8), 5) > 0.8:
            col = col * 0.9
    else:
        # the bands: lit on the crown of each pass, a dark crease where one dives under the other
        over = (u < 0.5) == (v < 0.5)
        crown = 1 - abs(((u + v) % 1.0) - 0.5) * 2 if over else 1 - abs(((u - v) % 1.0) - 0.5) * 2
        col = mix(hexc("1a0f29"), hexc("47306b"), 0.25 + 0.55 * crown + n * 0.08)
        if abs(du - dv) < 0.06 or du * 1.15 + dv < 0.7:
            col = hexc("0f0818")                                                # crease / window rim
    col = col.copy()
    col[3] = 255
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
    for c, f, W, H in faces:
        u, v, _, _ = c.uv[f]
        base[v:v + H, u:u + W] = paint(c, f, W, H)
    return base, tex_h


def png(arr):
    buf = io.BytesIO()
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(buf, "PNG")
    return buf.getvalue()


# ------------------------------------------------------------------ writers
def r(v):
    return round(float(v), 4)


def write_geo(tex_h):
    bones = []
    for b in BONES:
        out = {"name": b["name"], "pivot": [r(-b["pivot"][0]), r(b["pivot"][1]), r(b["pivot"][2])]}
        if b["parent"]:
            out["parent"] = b["parent"]
        cubes = []
        for c in CUBES:
            if c.bone != b["name"]:
                continue
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
        "description": {"identifier": f"geometry.{NAME}", "texture_width": TEX_W, "texture_height": tex_h,
                        "visible_bounds_width": 3, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}
    p = ASSETS / f"geo/item/{NAME}.geo.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(geo, indent=1))


def write_bbmodel(tex_png, tex_h):
    elements, ids = [], []
    for c in CUBES:
        i = uid()
        ids.append(i)
        el = {"name": c.name, "type": "cube", "uuid": i, "box_uv": False, "from": [r(x) for x in c.lo], "to": [r(x) for x in c.hi],
              "origin": [r(x) for x in c.pivot], "faces": {f: {"uv": [u, v, u + W, v + H], "texture": 0} for f, (u, v, W, H) in c.uv.items()}}
        if c.rot:
            el["rotation"] = [r(x) for x in c.rot]
        elements.append(el)
    bb = {"meta": {"format_version": "4.5", "model_format": "animated_entity_model", "box_uv": False},
          "name": NAME, "model_identifier": NAME, "resolution": {"width": TEX_W, "height": tex_h}, "elements": elements,
          "outliner": [{"name": "root", "origin": [0, 0, 0], "uuid": uid(), "children": [
              {"name": "katana", "origin": [0, 0, 0], "uuid": uid(), "children": ids}]}],
          "textures": [{"path": "", "name": f"{NAME}.png", "folder": "item", "namespace": "soulless", "id": "0",
                        "width": TEX_W, "height": tex_h, "uv_width": TEX_W, "uv_height": tex_h, "particle": True,
                        "render_mode": "default", "visible": True, "internal": True, "saved": False, "uuid": uid(),
                        "source": "data:image/png;base64," + base64.b64encode(tex_png).decode()}]}
    (ROOT / "art").mkdir(exist_ok=True)
    (ROOT / f"art/{NAME}.bbmodel").write_text(json.dumps(bb))


# ------------------------------------------------------------------ preview
def rotmat(rx, ry, rz):
    a, b, c = (math.radians(v) for v in (rx, ry, rz))
    Rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    Ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    Rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def render(base, views, scale=14, sub=3):
    """Orthographic software render: each view is (yaw, pitch); returns an image per view, side by side."""
    normals = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
    light = np.array([0.4, 0.8, -0.45])
    light /= np.linalg.norm(light)
    Wp, Hp = 22 * scale, 64 * scale
    out = []
    for yaw, pitch in views:
        img = np.zeros((Hp, Wp, 3), np.float32) + np.array([38, 36, 44], np.float32)
        zb = np.full((Hp, Wp), 1e9)
        V = rotmat(pitch, yaw, 0)
        for c in CUBES:
            Rm = rotmat(*(c.rot or (0, 0, 0)))
            piv = np.array(c.pivot, float)
            for f in FACES:
                u0, v0, W, H = c.uv[f]
                nrm = V @ Rm @ np.array(normals[f], float)
                shade = 0.55 + 0.45 * max(0.0, float(np.dot(nrm, light) if nrm[2] < 0 else -np.dot(nrm, light) * 0.6))
                us = (np.arange(W * sub) + 0.5) / (W * sub)
                vs = (np.arange(H * sub) + 0.5) / (H * sub)
                uu, vv = np.meshgrid(us, vs)
                pts = np.array([face_point(c, f, a, b) for a, b in zip(uu.ravel(), vv.ravel())])
                pts = (Rm @ (pts - piv).T).T + piv
                pts = (V @ pts.T).T
                tex = base[v0 + np.minimum((vv.ravel() * H).astype(int), H - 1), u0 + np.minimum((uu.ravel() * W).astype(int), W - 1)]
                ok = tex[:, 3] > 0
                px = (Wp / 2 - pts[:, 0] * scale).astype(int)
                py = (Hp - (pts[:, 1] + 16) * scale).astype(int)
                for X, Y, Z, col, o in zip(px, py, pts[:, 2], tex, ok):
                    if not o:
                        continue
                    for dx in (0, 1):
                        for dy in (0, 1):
                            XX, YY = X + dx, Y + dy
                            if 0 <= XX < Wp and 0 <= YY < Hp and Z < zb[YY, XX]:
                                zb[YY, XX] = Z
                                img[YY, XX] = col[:3] * shade
        out.append(img)
    return Image.fromarray(np.clip(np.concatenate(out, axis=1), 0, 255).astype(np.uint8))


def icon(base=None, size=32):
    """Inventory icon, pixel art in Soul Forged's icon palette (lavender steel, violet glow, bone guard, dark
    violet grip, a soul gem in the pommel, near-black outline): the katana on the diagonal, point to the top
    right, the blade bowed so its edge is the convex side."""
    OUT = (18, 10, 24)
    BACK, BODY, LIGHT, SHINE = (94, 86, 130), (133, 125, 176), (163, 155, 202), (200, 193, 232)
    GLOW, GLOW2, GLOW3 = (94, 20, 148), (150, 44, 222), (196, 108, 255)
    BONE, BONE_D, GOLD = (201, 188, 156), (168, 154, 125), (227, 217, 191)
    GRIP, GRIP_L, GRIP_D = (40, 30, 54), (57, 30, 47), (28, 14, 23)
    GEM, GEM_L = (164, 60, 240), (234, 184, 255)

    p0, p1 = np.array([3.6, 28.4]), np.array([28.6, 3.4])    # pommel, tip (y down)
    L = np.linalg.norm(p1 - p0)
    u = (p1 - p0) / L
    n = np.array([u[1], -u[0]])                                 # perpendicular toward the bottom right: the edge
    if n[0] < 0:
        n = -n
    T_GUARD, T_BLADE = 0.25, 0.30
    samples = np.linspace(0, 1, 600)

    def centre(t):
        s = np.clip((t - T_BLADE) / (1 - T_BLADE), 0, 1)
        return p0 + u * (t * L) - n * (2.6 * s ** 1.6)          # the blade bows away from the edge

    pts = np.array([centre(t) for t in samples])
    img = np.zeros((size, size, 4), np.uint8)
    for y in range(size):
        for x in range(size):
            c = np.array([x + 0.5, y + 0.5])
            i = int(np.argmin(((pts - c) ** 2).sum(axis=1)))
            t = samples[i]
            tang = pts[min(i + 1, len(pts) - 1)] - pts[max(i - 1, 0)]
            tang /= np.linalg.norm(tang)
            nn = np.array([tang[1], -tang[0]])
            if nn[0] < 0:
                nn = -nn
            d = float(np.dot(c - pts[i], nn))                   # + toward the edge
            along = float(np.dot(c - pts[i], tang))
            col = None
            if t <= 0.0 and along < -0.9:
                col = None
            elif t < 0.035:                                     # pommel cap with its soul gem
                if abs(d) < 1.6:
                    col = GEM_L if d < -0.3 and along < 0 else GEM
            elif t < T_GUARD - 0.02:                            # wrapped grip: diamond windows
                if abs(d) < 1.45:
                    k = int(t * L * 1.1)
                    if abs(d) < 0.55:
                        col = GLOW if k % 2 == 0 else GRIP_L
                    else:
                        col = GRIP if (k + (d > 0)) % 2 == 0 else GRIP_D
            elif t < T_GUARD + 0.035:                           # tsuba: a bar across the blade line
                if abs(d) < 3.4 and abs(along) < 1.25 and not (abs(d) > 2.6 and abs(along) > 0.7):
                    col = BONE if d < 0 else BONE_D
            elif t < T_BLADE:                                   # habaki collar
                if abs(d) < 1.5:
                    col = GOLD
            elif t <= 1.0:
                s = (t - T_BLADE) / (1 - T_BLADE)
                half = 1.55 - 0.75 * max(0.0, (s - 0.82) / 0.18)     # the point narrows over the last part
                tip = s > 0.985
                if abs(d) < half and not (tip and along > 0.2):
                    k = int(t * L)
                    if d < -0.55:
                        col = BACK                                   # the back (mune)
                    elif d > 0.55:
                        col = SHINE if (k % 5) else LIGHT            # the polished edge
                    else:                                            # the hamon: a violet glow along the temper line
                        col = (GLOW3 if k % 4 == 0 else GLOW2) if 0.15 < s < 0.92 else BODY
                    if s > 0.93:
                        col = SHINE
            if col is not None:
                img[y, x] = (*col, 255)
    mask = img[:, :, 3] > 0
    edge = np.zeros_like(mask)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= np.roll(np.roll(mask, dx, 1), dy, 0)
    edge &= ~mask
    img[edge] = (*OUT, 255)
    return Image.fromarray(img, "RGBA")


def main():
    build()
    base, tex_h = pack_and_paint()
    t = ASSETS / "textures/item"
    tex_png = png(base)
    (t / f"{NAME}.png").write_bytes(tex_png)
    write_geo(tex_h)
    write_bbmodel(tex_png, tex_h)
    ic = icon(base)
    ic.save(t / f"{NAME}_icon.png")
    if "--icon-preview" in sys.argv:
        ic.resize((256, 256), Image.NEAREST).save(sys.argv[sys.argv.index("--icon-preview") + 1])
    if "--preview" in sys.argv:
        render(base, [(0, 0), (40, 15), (90, 0), (180, 0)]).save(sys.argv[sys.argv.index("--preview") + 1])
    print(f"{NAME}: cubes={len(CUBES)} texture={TEX_W}x{tex_h}")


if __name__ == "__main__":
    main()
