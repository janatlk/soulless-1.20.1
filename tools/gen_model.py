"""Soul Forged model generator: one source of truth for the Blockbench project and the GeckoLib runtime files.

Writes
  art/soul_forged.bbmodel                              (open/edit in Blockbench, GeckoLib "Animated Model" format)
  assets/soulless/geo/item/soul_forged.geo.json
  assets/soulless/animations/item/soul_forged.animation.json
  assets/soulless/textures/item/soul_forged.png
  assets/soulless/textures/item/soul_forged_glowmask.png   runes, soul cracks, eyes, gems

The model is ~30 named cubes: a stepped soul-steel blade inside two tapered bevel edges with a glowing rune
fuller, a skull guard between iron wings and bone horns, a long two-hand grip and a caged soul-gem pommel.

    uv run --no-project --with pillow --with numpy tools/gen_model.py
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
DENSITY = 2   # texture pixels per model unit
TEX_W = 128

rng_uuid = random.Random(2026)


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
    "body": [hexc("17111f"), hexc("1f1729"), hexc("281e36"), hexc("312542")],
    "body_side": hexc("3d2e56"),
    "vein": hexc("a43cf0"),
    "vein_core": hexc("eab8ff"),
    "edge": [hexc("6c6496"), hexc("857db0"), hexc("a39bca"), hexc("c8c1e8")],
    "glint": hexc("f6f3ff"),
    "fuller_bg": hexc("26063f"),
    "fuller_mid": hexc("5e1494"),
    "rune": hexc("dc8cff"),
    "rune_core": hexc("ffffff"),
    "bone": [hexc("7e715a"), hexc("a89a7d"), hexc("c9bc9c"), hexc("e3d9bf")],
    "metal": [hexc("141118"), hexc("1f1a25"), hexc("2c2534"), hexc("41384d")],
    "rivet": hexc("8a7f9e"),
    "leather": [hexc("1c0e17"), hexc("2a1622"), hexc("391e2f"), hexc("48283b")],
    "thread": hexc("7a33ad"),
    "gem": [hexc("3a0763"), hexc("6612a3"), hexc("962cde"), hexc("c46cff"), hexc("f1d2ff")],
    "eye_dark": hexc("12081a"),
    "eye_glow": hexc("e27bff"),
}


# ----------------------------------------------------------------------------- geometry
class Cube:
    def __init__(self, name, bone, mat, lo, hi, rot=None, pivot=None, tag=""):
        self.name, self.bone, self.mat, self.tag = name, bone, mat, tag
        self.lo = tuple(min(a, b) for a, b in zip(lo, hi))
        self.hi = tuple(max(a, b) for a, b in zip(lo, hi))
        self.rot = rot
        self.pivot = pivot or [0, 0, 0]
        self.faces = ["north", "south", "east", "west", "up", "down"]
        self.uv = {}

    @property
    def size(self):
        return tuple(h - l for l, h in zip(self.lo, self.hi))


BONES = []   # dicts: name, parent, pivot
CUBES = []


def bone(name, parent, pivot):
    BONES.append({"name": name, "parent": parent, "pivot": list(pivot)})


def cube(*a, **k):
    c = Cube(*a, **k)
    CUBES.append(c)
    return c


BLADE_HW = 3.5     # half-width of the dark core; the bevel edges add EDGE_W on each side
EDGE_W = 1.5
TIP_START = 56     # where the parallel edges end; the point rises (BLADE_HW + EDGE_W) above it


def build():
    BONES.append({"name": "root", "parent": None, "pivot": [0, 0, 0]})
    bone("handle", "root", (0, 0, 0))
    bone("pommel_gem", "handle", (0, -15, 0))
    bone("guard", "root", (0, 6, 0))
    bone("blade", "root", (0, 9, 0))

    # handle: a long two-hand grip, iron bands, a caged soul gem pommel
    cube("grip", "handle", "leather", (-1.5, -12, -1.5), (1.5, 3, 1.5))
    cube("band_top", "handle", "metal", (-2, 2, -2), (2, 3, 2), tag="ring")
    cube("band_mid", "handle", "metal", (-2, -4.5, -2), (2, -3.5, 2), tag="ring")
    cube("collar", "handle", "metal", (-2.5, -13.5, -2.5), (2.5, -12, 2.5), tag="ring")
    cube("soul_gem", "pommel_gem", "gem", (-1.5, -17, -1.5), (1.5, -13.5, 1.5))
    for sx, sz, n in ((-1, -1, "claw_nw"), (1, -1, "claw_ne"), (-1, 1, "claw_sw"), (1, 1, "claw_se")):
        cube(n, "handle", "metal", (sx * 2 - 0.5, -17, sz * 2 - 0.5), (sx * 2 + 0.5, -13.5, sz * 2 + 0.5),
             rot=[-sz * 14, 0, sx * 14], pivot=[sx * 2, -13.5, sz * 2])
    cube("pommel_spike", "handle", "metal", (-0.5, -19.5, -0.5), (0.5, -17, 0.5))

    # guard: a skull between iron wings that sweep up into bone horns
    cube("skull", "guard", "skull", (-4, 3, -2.5), (4, 9, 2.5))
    cube("guard_plate", "guard", "metal", (-4.5, 2.5, -2), (4.5, 3.5, 2))
    for s, side in ((-1, "left"), (1, "right")):
        cube(f"wing_{side}", "guard", "metal", (s * 4, 4.5, -2), (s * 9, 8, 2))
        cube(f"wing_gem_{side}", "guard", "gem", (s * 5.8, 5.4, -2.3), (s * 7.6, 7.2, 2.3))
        cube(f"horn_{side}", "guard", "bone", (s * 8.5, 5, -1.5), (s * 13, 8, 1.5),
             rot=[0, 0, s * 22], pivot=[s * 8.5, 5, 0])
        cube(f"horn_rise_{side}", "guard", "bone", (s * 11.5, 7, -1), (s * 13.5, 13.5, 1),
             rot=[0, 0, -s * 12], pivot=[s * 12.5, 7, 0])
        cube(f"horn_tip_{side}", "guard", "bone", (s * 12, 13, -0.5), (s * 13, 16.5, 0.5),
             rot=[0, 0, -s * 26], pivot=[s * 12.5, 13, 0])

    # blade: a straight soul-steel core between parallel bevel edges, a glowing rune fuller,
    # finished by one clean point: a square turned 45 degrees whose side corners meet the edges
    cube("core", "blade", "body", (-BLADE_HW, 9, -1), (BLADE_HW, TIP_START, 1))
    for s, side in ((-1, "left"), (1, "right")):
        cube(f"edge_{side}", "blade", "edge", (s * BLADE_HW, 9, -0.5), (s * (BLADE_HW + EDGE_W), TIP_START, 0.5))
    half = (BLADE_HW + EDGE_W) / math.sqrt(2)   # half side, so the diagonal equals the blade width
    cube("point", "blade", "point", (-half, TIP_START - half, -0.8), (half, TIP_START + half, 0.8),
         rot=[0, 0, 45], pivot=[0, TIP_START, 0])
    cube("fuller", "blade", "fuller", (-1, 11, -1.1), (1, TIP_START - 4, 1.1))


# ----------------------------------------------------------------------------- texture
def face_dims(c, f):
    sx, sy, sz = c.size
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy),
            "up": (sx, sz), "down": (sx, sz)}[f]


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
RUNES = [["#..#", "#.#.", "##..", "#.#.", "#..#"], [".##.", "#..#", ".##.", "#..#", ".##."],
         ["####", "..#.", ".#..", "#...", "####"], ["#..#", "####", "#..#", ".##.", ".#.."],
         [".#..", "###.", ".#.#", ".###", "..#."], ["#...", "##..", "#.#.", "#..#", "####"]]


def paint(c, f, W, H):
    img = np.zeros((H, W, 4), np.float32)
    glow = np.zeros((H, W), bool)
    m = c.mat
    for j in range(H):
        for i in range(W):
            n = hash2(i + c.lo[0] * 7, j + c.lo[1] * 3, len(c.name))
            v = (j + 0.5) / H
            if m == "body":
                if f in ("north", "south"):
                    t = 0.2 + 0.55 * abs((i + 0.5) / W - 0.5) * 2 + (n - 0.5) * 0.3
                    img[j, i] = pick(P["body"], t)
                else:
                    img[j, i] = P["body_side"]
            elif m == "edge":
                t = 0.4 + (n - 0.5) * 0.35 + (0.35 if f == "up" else 0) + (0.2 if f in ("east", "west") else 0)
                img[j, i] = P["glint"] if hash2(i, j, 9) > 0.95 else pick(P["edge"], t)
            elif m == "point":
                # dark steel inside, a bright bevel rim on the two upper (visible) sides of the diamond
                rim = min(i, j, W - 1 - i, H - 1 - j)
                if f in ("north", "south") and rim < 3:
                    t = 0.45 + (n - 0.5) * 0.3 + (0.3 if rim == 0 else 0)
                    img[j, i] = P["glint"] if hash2(i, j, 9) > 0.95 else pick(P["edge"], t)
                elif f in ("north", "south"):
                    img[j, i] = pick(P["body"], 0.35 + (n - 0.5) * 0.3)
                else:
                    img[j, i] = pick(P["edge"], 0.5 + (n - 0.5) * 0.3)
            elif m == "fuller":
                img[j, i] = P["fuller_bg"]
                glow[j, i] = True
                if f in ("north", "south"):
                    if i in (0, W - 1):
                        img[j, i] = P["fuller_mid"]
                    else:
                        g = RUNES[((j // 7) * 2 + (f == "south")) % len(RUNES)]
                        if j % 7 < 5 and g[j % 7][i % 4] == "#":
                            img[j, i] = P["rune"] if (i + j) % 3 else P["rune_core"]
            elif m == "metal":
                t = 0.35 + (n - 0.5) * 0.4 + (0.3 if f == "up" else 0) + (0.2 if j in (0, H - 1) else 0)
                img[j, i] = pick(P["metal"], t)
                if c.tag == "ring" and f in ("north", "south", "east", "west") and i == W // 2:
                    img[j, i] = P["rivet"]
            elif m == "bone":
                t = 0.45 + (n - 0.5) * 0.45 + (0.2 if f == "up" else -0.2 if f == "down" else 0)
                if hash2(i, j, 5) > 0.9:
                    t -= 0.3
                img[j, i] = pick(P["bone"], t)
            elif m == "leather":
                band = (j + (i if f in ("north", "east") else W - i)) % 6
                img[j, i] = pick(P["leather"], [0.75, 0.6, 0.45, 0.3, 0.15, 0.05][band] + (n - 0.5) * 0.15)
                if band == 5 and (j // 6) % 2 == 0:
                    img[j, i] = P["thread"]
            elif m == "gem":
                t = 0.3 + 0.55 * (1 - v) + (n - 0.5) * 0.25 + (0.25 if (i + j) % 5 == 0 else 0)
                img[j, i] = pick(P["gem"], t)
                glow[j, i] = True
            elif m == "skull":
                if f in ("north", "south") and (W, H) == (16, 12):
                    ch = SKULL[j][i]
                    if ch == "#":
                        img[j, i] = pick(P["bone"], 0.55 + (n - 0.5) * 0.4 + (0.15 if j < 3 else 0))
                    elif ch == "o":
                        img[j, i] = P["eye_glow"] if (i + j) % 2 else P["vein_core"]
                        glow[j, i] = True
                    elif ch in "ent":
                        img[j, i] = P["eye_dark"]
                    else:
                        img[j, i] = pick(P["metal"], 0.3 + (n - 0.5) * 0.3)
                else:
                    img[j, i] = pick(P["bone"], 0.45 + (n - 0.5) * 0.45 + (0.2 if f == "up" else -0.1))
    # glowing soul cracks across the dark blade faces
    if m == "body" and f in ("north", "south") and W >= 6:
        r = random.Random(f"{c.name}/{f}")
        for _ in range(max(1, H // 9)):
            x, y = W // 2 + r.choice([-2, 2]), r.randrange(H)
            dx = 1 if x > W // 2 else -1
            for step in range(r.randint(3, max(4, W // 2))):
                if not (0 <= x < W and 0 <= y < H):
                    break
                img[y, x] = P["vein_core"] if step < 2 else P["vein"]
                glow[y, x] = True
                x += dx
                if r.random() < 0.45:
                    y += r.choice([-1, 1])
    return img, glow


def pack_and_paint():
    faces = []
    for c in CUBES:
        for f in c.faces:
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


def to_png_bytes(arr):
    buf = io.BytesIO()
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(buf, "PNG")
    return buf.getvalue()


# ----------------------------------------------------------------------------- animations (shared data)
def animations():
    """name -> {loop, length, bones: {bone: {channel: {time: [x, y, z]}}}}; values may be molang strings."""
    return {
        "animation.soul_forged.idle": {"loop": True, "length": 4.0, "bones": {
            "pommel_gem": {"rotation": {0: [0, "query.anim_time * 90", 0]},
                           "position": {0: [0, "math.sin(query.anim_time * 180) * 0.3", 0]}},
        }},
        "animation.soul_forged.slam": {"loop": False, "length": 1.6, "bones": {
            "pommel_gem": {"rotation": {0: [0, "query.anim_time * 900", 0]}},
        }},
    }


# ----------------------------------------------------------------------------- writers
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
                # GeckoLib reads bedrock rotations as (-x, -y, z) of the Java-space rotation
                cd["rotation"] = [r(-c.rot[0]), r(-c.rot[1]), r(c.rot[2])]
            cubes.append(cd)
        if cubes:
            out["cubes"] = cubes
        bones.append(out)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.soul_forged", "texture_width": TEX_W, "texture_height": tex_h,
                        "visible_bounds_width": 4, "visible_bounds_height": 6, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}
    p = ASSETS / "geo/item/soul_forged.geo.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(geo, indent=1))


def write_animation_json():
    anims = {}
    for name, a in animations().items():
        bones = {}
        for bname, chans in a["bones"].items():
            bones[bname] = {}
            for ch, kfs in chans.items():
                if list(kfs) == [0]:
                    bones[bname][ch] = list(kfs[0])
                else:
                    bones[bname][ch] = {str(t): {"vector": v, "easing": "easeInOutSine"} for t, v in kfs.items()}
        anims[name] = {"loop": a["loop"], "animation_length": a["length"], "bones": bones}
    p = ASSETS / "animations/item/soul_forged.animation.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=1))


def write_bbmodel(tex_png, tex_h):
    elements, cube_uuid = [], {}
    for c in CUBES:
        cid = uid()
        cube_uuid[id(c)] = cid
        faces = {}
        for f in ("north", "east", "south", "west", "up", "down"):
            u, v, W, H = c.uv[f]
            faces[f] = {"uv": [u, v, u + W, v + H], "texture": 0}
        el = {"name": c.name, "type": "cube", "uuid": cid, "box_uv": False, "rescale": False, "locked": False,
              "from": [r(x) for x in c.lo], "to": [r(x) for x in c.hi], "autouv": 0, "color": 0,
              "origin": [r(x) for x in c.pivot], "faces": faces}
        if c.rot:
            el["rotation"] = [r(x) for x in c.rot]
        elements.append(el)

    group_uuid = {b["name"]: uid() for b in BONES}

    def group(name):
        b = next(x for x in BONES if x["name"] == name)
        children = [cube_uuid[id(c)] for c in CUBES if c.bone == name]
        children += [group(x["name"]) for x in BONES if x["parent"] == name]
        return {"name": name, "origin": b["pivot"], "rotation": [0, 0, 0], "uuid": group_uuid[name],
                "export": True, "isOpen": True, "visibility": True, "children": children}

    anims = []
    for name, a in animations().items():
        animators = {}
        for bname, chans in a["bones"].items():
            kfs = []
            for ch, frames in chans.items():
                for t, v in frames.items():
                    kfs.append({"channel": ch, "data_points": [{"x": str(v[0]), "y": str(v[1]), "z": str(v[2])}],
                                "uuid": uid(), "time": t, "color": -1, "interpolation": "linear"})
            animators[group_uuid[bname]] = {"name": bname, "type": "bone", "keyframes": kfs}
        anims.append({"uuid": uid(), "name": name, "loop": "loop" if a["loop"] else "hold", "override": False,
                      "length": a["length"], "snapping": 20, "selected": False, "anim_time_update": "",
                      "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators})

    bb = {
        "meta": {"format_version": "4.5", "model_format": "animated_entity_model", "box_uv": False},
        "name": "soul_forged", "model_identifier": "soul_forged",
        "visible_box": [4, 6, 1.5], "variable_placeholders": "", "variable_placeholder_buttons": [],
        "timeline_setups": [], "unhandled_root_fields": {},
        "resolution": {"width": TEX_W, "height": tex_h},
        "elements": elements,
        "outliner": [group("root")],
        "textures": [{"path": "", "name": "soul_forged.png", "folder": "item", "namespace": "soulless", "id": "0",
                      "width": TEX_W, "height": tex_h, "uv_width": TEX_W, "uv_height": tex_h, "particle": True,
                      "render_mode": "default", "render_sides": "auto", "visible": True, "internal": True,
                      "saved": False, "uuid": uid(), "source": "data:image/png;base64," + base64.b64encode(tex_png).decode()}],
        "animations": anims,
    }
    p = ROOT / "art/soul_forged.bbmodel"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(bb))


def main():
    build()
    base, gm, tex_h = pack_and_paint()
    tex = ASSETS / "textures/item"
    for old in (tex / "soul_forged").glob("*.png") if (tex / "soul_forged").exists() else []:
        old.unlink()
    if (tex / "soul_forged").exists():
        (tex / "soul_forged").rmdir()
    tex_png = to_png_bytes(base)
    (tex / "soul_forged.png").write_bytes(tex_png)
    (tex / "soul_forged_glowmask.png").write_bytes(to_png_bytes(gm))
    write_geo(tex_h)
    write_animation_json()
    write_bbmodel(tex_png, tex_h)
    print(f"cubes={len(CUBES)} bones={len(BONES)} texture={TEX_W}x{tex_h}")


if __name__ == "__main__":
    main()
