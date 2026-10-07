"""The Renegade: the sensei's former student turned to forbidden dark magic (STORY_KATANA.md, act 6). A player-sized
GeckoLib model - hood with burning red eyes and a half mask, a dark haori and hakama, a ragged red scarf, a katana in
the right hand and its sheath at the left hip - painted texture + glowmask, and his animations.

Space: one unit = one model pixel (16 to a block), y up, the figure faces -z, +x is his left. A pose maps bones to
{"rot": (rx, ry, rz)} in this space: rx > 0 swings whatever points down or forward toward the front and up (an arm
raised forward, a head looking up); ry > 0 turns the front toward his right (-x); rz > 0 swings a hanging part toward
his left (+x) - so a right arm lifted out sideways is rz < 0. Files get rotations as (-rx, -ry, rz), as GeckoLib reads
them. The katana bone: at rest the blade runs forward (-z) out of the right fist.

    uv run --no-project --with pillow --with numpy tools/gen_renegade.py [--preview out.png]
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/soulless"
TEX_W = 128


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
    "cloth": ramp("0c0a0f", "14111a", "1c1824", "26202f", "302839"),        # the black haori
    "trim": ramp("4a0d14", "68121c", "861a26", "a32432"),                   # crimson trim and scarf
    "hakama": ramp("121216", "1a1a20", "23232b", "2d2d37"),
    "skin": ramp("8d6f5e", "a5826e", "b9937c"),
    "steel": ramp("1a1820", "2a2733", "3c3848", "54506a", "6f6a8a"),
    "wrap": ramp("0d0b10", "1b1520", "2a1f2e"),
}
EYE = hexc("ff2a3a")
EDGE = hexc("ff3b4a")


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


def bone(name, parent, pivot, rest=None):
    BONES[name] = {"name": name, "parent": parent, "pivot": [float(v) for v in pivot], "rest": rest}


def box(name, b, mat, lo, hi):
    CUBES.append(Cube(name, b, mat, lo, hi))


def mirror(lo, hi):
    return (-hi[0], lo[1], lo[2]), (-lo[0], hi[1], hi[2])


# ------------------------------------------------------------------ model (standing, arms down, blade forward)
def build():
    bone("root", None, (0, 0, 0))
    bone("waist", "root", (0, 12, 0))
    box("hips", "waist", "hakama", (-4.5, 9.5, -2.5), (4.5, 13, 2.5))
    bone("saya", "waist", (4.9, 11.6, -1.5), rest=(18, 0, 0))   # the sheath at the left hip, tip dropping behind
    box("saya", "saya", "saya", (4.3, 11, -3.2), (5.5, 12.2, 12.5))
    bone("chest", "waist", (0, 12, 0))
    box("torso", "chest", "haori", (-4, 12, -2), (4, 24, 2))
    box("obi", "chest", "obi", (-4.35, 12, -2.35), (4.35, 14.2, 2.35))
    box("collar", "chest", "scarf", (-4.45, 21.6, -2.45), (4.45, 24.4, 2.45))
    bone("scarf_tail", "chest", (1.8, 23, 2.4))
    box("scarf_tail", "scarf_tail", "scarf", (0.6, 12.5, 2.4), (3.2, 23, 3.1))
    bone("head", "chest", (0, 24, 0))
    box("head", "head", "face", (-4, 24, -4), (4, 32, 4))
    box("hood", "head", "hood", (-4.6, 23.6, -4.6), (4.6, 32.8, 4.6))
    for side, s in (("r", -1), ("l", 1)):
        def m(lo, hi):
            return (lo, hi) if s > 0 else mirror(lo, hi)
        bone(f"arm_{side}", "chest", (6 * s, 22.5, 0))
        box(f"sleeve_{side}", f"arm_{side}", "sleeve", *m((3.6, 15.8, -2.6), (8.6, 24.2, 2.6)))
        bone(f"forearm_{side}", f"arm_{side}", (6 * s, 16.2, 0))
        box(f"forearm_{side}", f"forearm_{side}", "wrap", *m((4.25, 11, -1.75), (7.75, 16.5, 1.75)))
        box(f"hand_{side}", f"forearm_{side}", "skin", *m((4.25, 9.6, -1.75), (7.75, 11, 1.75)))
        bone(f"leg_{side}", "root", (2.2 * s, 12, 0))
        box(f"thigh_{side}", f"leg_{side}", "hakama", *m((-0.2, 5.6, -2.6), (4.6, 12, 2.6)))
        bone(f"shin_{side}", f"leg_{side}", (2.2 * s, 6, 0))
        box(f"shin_{side}", f"shin_{side}", "hakama", *m((0.2, 1.1, -2.1), (4.2, 6.2, 2.1)))
        box(f"foot_{side}", f"shin_{side}", "tabi", *m((0.2, 0, -3), (4.2, 1.2, 2)))
    # the katana, out of the right fist: grip and pommel behind, the guard, then the blade forward
    bone("katana", "forearm_r", (-6, 10.3, 0))
    box("tsuka", "katana", "tsuka", (-6.6, 9.7, -2.2), (-5.4, 10.9, 3.8))
    box("tsuba", "katana", "tsuba", (-7.4, 8.9, -3), (-4.6, 11.7, -2.2))
    bone("blade", "katana", (-6, 10.3, -3))   # its own bone: scaled away while the blade is in the sheath
    box("blade", "blade", "blade", (-6.3, 9.5, -23.5), (-5.7, 11.1, -3))


# ------------------------------------------------------------------ texture
FACES = ("north", "south", "east", "west", "up", "down")
LIGHT = {"up": 0.12, "down": -0.2, "north": 0.0, "south": -0.04, "east": -0.07, "west": -0.07}


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


def paint(c, f, W, H):
    img = np.zeros((H, W, 4), np.float32)
    glow = np.zeros((H, W), bool)
    m = c.mat
    side = f not in ("up", "down")
    for j in range(H):
        for i in range(W):
            x, y, z = face_point(c, f, (i + 0.5) / W, (j + 0.5) / H)
            n = hash3(round(x * 2), round(y * 2), round(z * 2), 1) - 0.5
            lt = LIGHT[f]
            g = False
            if m == "haori":
                col = pick(R["cloth"], 0.5 + lt + n * 0.15)
                if f == "north" and abs(x) < 1.0 + (24 - y) * 0.12:   # the haori's open front, a red under-robe
                    col = pick(R["trim"], 0.3 + lt + n * 0.1)
                if f == "north" and 0.9 < abs(x) - (24 - y) * 0.12 < 1.7:   # crimson lapels
                    col = pick(R["trim"], 0.6 + lt)
            elif m == "sleeve":
                col = pick(R["cloth"], 0.48 + lt + n * 0.15)
                if y < 17.2 and side:
                    col = pick(R["trim"], 0.55 + lt)   # crimson cuffs
            elif m == "obi":
                col = pick(R["trim"], 0.35 + lt + n * 0.1)
                if side and abs(y - 13.1) < 0.3:
                    col = pick(R["cloth"], 0.2)
            elif m == "scarf":
                col = pick(R["trim"], 0.5 + lt + n * 0.2)
                if c.name == "scarf_tail" and f in ("south", "north") and y < 14 + hash3(i, 0, 0, 3) * 2.5:
                    col[3] = 0   # a ragged end
            elif m == "hakama":
                col = pick(R["hakama"], 0.5 + lt + n * 0.12)
                if side and (i % 3 == 0):
                    col = pick(R["hakama"], 0.25 + lt)   # pleats
            elif m == "tabi":
                col = pick(R["wrap"], 0.4 + lt)
            elif m == "wrap":
                col = pick(R["wrap"], 0.5 + lt + n * 0.15)
                if side and (j % 2 == 0):
                    col = pick(R["wrap"], 0.2 + lt)
            elif m == "skin":
                col = pick(R["skin"], 0.5 + lt + n * 0.1)
            elif m == "face":
                col = pick(R["skin"], 0.35 + lt + n * 0.1)   # in the hood's shadow
                if f == "north":
                    ax = abs(x)
                    if 1.2 <= ax <= 3.0 and 28 <= y <= 29.2:
                        col, g = EYE.copy(), True
                    elif y < 27.5:
                        col = pick(R["cloth"], 0.35 + n * 0.1)   # the half mask over mouth and nose
                        if abs(y - 26.5) < 0.3 and ax < 3.2:
                            col = pick(R["trim"], 0.5)
                    elif y > 30.5:
                        col = pick(R["cloth"], 0.15)   # hair under the hood
                else:
                    col = pick(R["cloth"], 0.2 + n * 0.1)
            elif m == "hood":
                col = pick(R["cloth"], 0.42 + lt + n * 0.12)
                if f == "north" and abs(x) < 3.6 and 24.5 < y < 30.8:
                    col[3] = 0   # the opening: the face shows through
                if f == "down":
                    col[3] = 0
                if side and y < 24.4:
                    col = pick(R["trim"], 0.45 + lt)
            elif m == "saya":
                col = pick(R["wrap"], 0.45 + lt + n * 0.1)
                if (z > 10.5 or z < -2.4) and side:
                    col = pick(R["trim"], 0.5 + lt)   # crimson mouth and tip
            elif m == "tsuka":
                col = pick(R["wrap"], 0.35 + lt)
                if side and (round(z * 2) % 3 == 0):
                    col = pick(R["trim"], 0.5 + lt)   # the red diamonds of the wrap
            elif m == "tsuba":
                col = pick(R["steel"], 0.3 + lt + n * 0.2)
            elif m == "blade":
                col = pick(R["steel"], 0.55 + lt + n * 0.1)
                if side and y < 10.0:   # the edge: a thin red glowing line
                    col, g = EDGE.copy(), True
                if f == "north":
                    col = pick(R["steel"], 0.8)
            else:
                col = hexc("ff00ff")
            img[j, i] = col
            glow[j, i] = g and col[3] > 0
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


# ------------------------------------------------------------------ poses
def P(**bones):
    """A pose: bone=(rx, ry, rz) or bone=dict(rot=..., pos=...)."""
    out = {}
    for k, v in bones.items():
        out[k] = v if isinstance(v, dict) else {"rot": v}
    return out


def merge(*poses):
    out = {}
    for p in poses:
        out.update(p)
    return out


# the guard: blade held forward at chest height in both hands, a wide stance, turned a little blade-side
GUARD = P(chest=(0, 12, 0), head=(2, -10, 0),
          arm_r=(38, 0, 12), forearm_r=(32, 0, 0), katana=(28, 0, 0),
          arm_l=(42, 0, -38), forearm_l=(38, 0, 0),
          leg_r=(14, 0, -3), shin_r=(-12, 0, 0), leg_l=(-14, 0, 4), shin_l=(-16, 0, 0),
          waist=dict(pos=(0, -0.8, 0)))
# iai: crouched, twisted, right hand on the hilt at the left hip, the blade along the sheath, left hand on the sheath
IAI_READY = P(waist=dict(rot=(0, -22, 0), pos=(0, -2.6, 0)), chest=(-14, -10, 0), head=(12, 28, 0),
              arm_r=(28, 0, 52), forearm_r=(48, 0, 0), katana=(-12, 168, 0),
              arm_l=(18, 0, 10), forearm_l=(52, 0, 0),
              leg_r=(38, 0, -4), shin_r=(-52, 0, 0), leg_l=(-22, 0, 6), shin_l=(-34, 0, 0))
# the draw cut: the arm sweeps level from the left hip far out to the right, the body unwinding with it
IAI_DRAW = P(waist=dict(rot=(0, 10, 0), pos=(0, -2.2, 0)), chest=(-8, 22, 0), head=(4, -14, 0),
             arm_r=(88, -18, 0), forearm_r=(4, 0, 0), katana=(0, 0, 0),
             arm_l=(10, 0, 32), forearm_l=(28, 0, 0),
             leg_r=(40, 0, -6), shin_r=(-46, 0, 0), leg_l=(-26, 0, 8), shin_l=(-30, 0, 0))
IAI_FOLLOW = P(waist=dict(rot=(0, 28, 0), pos=(0, -2, 0)), chest=(-4, 34, 0), head=(2, -30, 0),
               arm_r=(84, 68, 0), forearm_r=(0, 0, 0), katana=(6, 10, 0),
               arm_l=(6, 0, 44), forearm_l=(20, 0, 0),
               leg_r=(36, 0, -6), shin_r=(-40, 0, 0), leg_l=(-28, 0, 8), shin_l=(-28, 0, 0))
# overhead (jodan): both hands over the head, the blade back over the shoulder
JODAN = P(chest=(4, 6, 0), head=(-2, -6, 0),
          arm_r=(158, 0, 14), forearm_r=(28, 0, 0), katana=(46, 0, 0),
          arm_l=(160, 0, -30), forearm_l=(30, 0, 0),
          leg_r=(16, 0, -3), shin_r=(-14, 0, 0), leg_l=(-14, 0, 4), shin_l=(-18, 0, 0),
          waist=dict(pos=(0, -0.6, 0)))
# a diagonal cut finished low on the left (kesa), and one finished high on the left (gyaku)
KESA_END = P(waist=dict(rot=(0, -16, 0), pos=(0, -1.8, 0)), chest=(-16, -22, 0), head=(6, 18, 0),
             arm_r=(34, -30, 38), forearm_r=(6, 0, 0), katana=(-8, 0, 0),
             arm_l=(30, 0, -12), forearm_l=(36, 0, 0),
             leg_r=(30, 0, -4), shin_r=(-30, 0, 0), leg_l=(-20, 0, 6), shin_l=(-22, 0, 0))
GYAKU_END = P(waist=dict(rot=(0, 18, 0), pos=(0, -1.2, 0)), chest=(6, 26, 0), head=(-2, -20, 0),
              arm_r=(132, 40, 0), forearm_r=(10, 0, 0), katana=(28, 0, 0),
              arm_l=(70, 0, -30), forearm_l=(40, 0, 0),
              leg_r=(-10, 0, -4), shin_r=(-16, 0, 0), leg_l=(22, 0, 6), shin_l=(-26, 0, 0))
# a level cut finished on the right
LEVEL_END = P(waist=dict(rot=(0, 20, 0), pos=(0, -1.4, 0)), chest=(-6, 26, 0), head=(2, -22, 0),
              arm_r=(86, 62, 0), forearm_r=(0, 0, 0), katana=(4, 0, 0),
              arm_l=(30, 0, 40), forearm_l=(20, 0, 0),
              leg_r=(24, 0, -4), shin_r=(-26, 0, 0), leg_l=(-18, 0, 6), shin_l=(-20, 0, 0))
# the thrust: drawn back at the hip, then the lunge
THRUST_BACK = P(waist=dict(rot=(0, -14, 0), pos=(0, -2.4, 0)), chest=(8, -20, 0), head=(-6, 16, 0),
                arm_r=(-18, 0, 8), forearm_r=(84, 0, 0), katana=(-84, 0, 0),
                arm_l=(70, 0, 8), forearm_l=(10, 0, 0),
                leg_r=(-24, 0, -4), shin_r=(-30, 0, 0), leg_l=(38, 0, 6), shin_l=(-40, 0, 0))
THRUST_LUNGE = P(waist=dict(rot=(0, 6, 0), pos=(0, -3, 0)), chest=(-26, 8, 0), head=(20, -6, 0),
                 arm_r=(96, 0, 8), forearm_r=(0, 0, 0), katana=(-4, 0, 0),
                 arm_l=(-30, 0, 20), forearm_l=(20, 0, 0),
                 leg_r=(62, 0, -4), shin_r=(-58, 0, 0), leg_l=(-52, 0, 6), shin_l=(-8, 0, 0))
# the whirl: arms out wide, the blade straight out to the right, legs drawn up
WHIRL = P(waist=dict(pos=(0, -1, 0)), chest=(0, 0, 0), head=(0, 0, 0),
          arm_r=(0, 0, -86), forearm_r=(0, 0, 0), katana=(0, -90, 0),
          arm_l=(0, 0, 80), forearm_l=(0, 0, 0),
          leg_r=(46, 0, -6), shin_r=(-70, 0, 0), leg_l=(30, 0, 6), shin_l=(-60, 0, 0))
CROUCH = P(waist=dict(pos=(0, -3.4, 0)), chest=(-18, 0, 0), head=(14, 0, 0),
           arm_r=(30, 0, 18), forearm_r=(40, 0, 0), katana=(10, 0, 0),
           arm_l=(26, 0, -18), forearm_l=(40, 0, 0),
           leg_r=(52, 0, -4), shin_r=(-80, 0, 0), leg_l=(46, 0, 4), shin_l=(-76, 0, 0))
# calling the blades: the left hand thrown up to the sky, the katana low; then pointing them at the foe
CALL = P(chest=(6, -10, 0), head=(-14, 6, 0),
         arm_r=(14, 0, 22), forearm_r=(20, 0, 0), katana=(-20, 0, 0),
         arm_l=(172, 0, 12), forearm_l=(4, 0, 0),
         leg_r=(10, 0, -4), shin_r=(-8, 0, 0), leg_l=(-10, 0, 4), shin_l=(-10, 0, 0))
POINT = P(chest=(-4, -26, 0), head=(4, 22, 0),
          arm_r=(10, 0, 26), forearm_r=(18, 0, 0), katana=(-20, 0, 0),
          arm_l=(92, 0, -6), forearm_l=(0, 0, 0),
          leg_r=(-10, 0, -4), shin_r=(-10, 0, 0), leg_l=(18, 0, 4), shin_l=(-20, 0, 0),
          waist=dict(rot=(0, -12, 0), pos=(0, -0.8, 0)))
# hanging in the air over the foe, blade raised; the dive's end, kneeling on the cut
HANG = P(chest=(10, 0, 0), head=(-30, 0, 0),
         arm_r=(164, 0, 14), forearm_r=(30, 0, 0), katana=(60, 0, 0),
         arm_l=(166, 0, -30), forearm_l=(32, 0, 0),
         leg_r=(40, 0, -4), shin_r=(-90, 0, 0), leg_l=(10, 0, 4), shin_l=(-70, 0, 0))
DIVE_END = P(waist=dict(pos=(0, -4.6, 0)), chest=(-30, 0, 0), head=(26, 0, 0),
             arm_r=(52, 0, 10), forearm_r=(0, 0, 0), katana=(-34, 0, 0),
             arm_l=(54, 0, -26), forearm_l=(10, 0, 0),
             leg_r=(70, 0, -4), shin_r=(-90, 0, 0), leg_l=(-12, 0, 6), shin_l=(-96, 0, 0))
# thrown back by a parry: chest open, arms flung wide, a stumbling step back
STAGGER = P(waist=dict(rot=(0, 0, 0), pos=(0, -1, 0)), chest=(22, -14, 6), head=(18, 10, 0),
            arm_r=(40, 0, -58), forearm_r=(20, 0, 0), katana=(30, 0, 0),
            arm_l=(30, 0, 66), forearm_l=(26, 0, 0),
            leg_r=(-30, 0, -6), shin_r=(-24, 0, 0), leg_l=(16, 0, 6), shin_l=(-12, 0, 0))
# beaten: down on one knee, the blade's point in the ground, head bowed
DEFEAT = P(waist=dict(pos=(0, -5.6, 0)), chest=(-18, 0, 0), head=(-26, 0, 0),
           arm_r=(48, 0, 6), forearm_r=(10, 0, 0), katana=(-110, 0, 0),
           arm_l=(30, 0, -14), forearm_l=(60, 0, 0),
           leg_r=(84, 0, -4), shin_r=(-90, 0, 0), leg_l=(-8, 0, 6), shin_l=(-100, 0, 0))
STAND = P(arm_r=(10, 0, 4), forearm_r=(14, 0, 0), katana=(-60, 0, 0), arm_l=(4, 0, -4), forearm_l=(8, 0, 0))

# where the blade points in each pose (model space: -z forward, +y up, +x his left); aim_blades() turns the katana
# bone to it through whatever the arm is doing
BLADE = {"guard": (0.05, 0.5, -0.86), "iai_ready": (0.3, -0.45, 0.84), "iai_draw": (-0.35, 0.02, -0.94),
         "iai_follow": (-0.95, 0.08, 0.3), "jodan": (0, 0.62, 0.78), "kesa_end": (0.55, -0.5, -0.66),
         "gyaku_end": (-0.35, 0.8, -0.48), "level_end": (-0.97, 0.02, -0.22), "thrust_back": (0.02, 0.06, -1),
         "thrust_lunge": (0, -0.02, -1), "whirl": (-1, 0.02, 0), "crouch": (0.05, 0.4, -0.9),
         "call": (-0.3, -0.72, -0.6), "point": (-0.3, -0.72, -0.6), "hang": (0, 0.55, 0.83),
         "dive_end": (0, -0.62, -0.78), "stagger": (-0.4, 0.8, 0.45), "defeat": (0.02, -1, -0.08),
         "stand": (-0.18, -0.86, -0.45)}

POSES = {"guard": GUARD, "iai_ready": IAI_READY, "iai_draw": IAI_DRAW, "iai_follow": IAI_FOLLOW, "jodan": JODAN,
         "kesa_end": KESA_END, "gyaku_end": GYAKU_END, "level_end": LEVEL_END, "thrust_back": THRUST_BACK,
         "thrust_lunge": THRUST_LUNGE, "whirl": WHIRL, "crouch": CROUCH, "call": CALL, "point": POINT, "hang": HANG,
         "dive_end": DIVE_END, "stagger": STAGGER, "defeat": DEFEAT, "stand": STAND}


# ------------------------------------------------------------------ animations
def r(v):
    return round(float(v), 3)


def to_file_rot(rot):
    return [r(-rot[0]), r(-rot[1]), r(rot[2])]


def keyframes(frames, ease="easeInOutSine", extra=None):
    """[(time, pose[, easing])] -> bone channels (bones a pose leaves out sit at rest)."""
    bones = sorted(set().union(*(set(f[1]) for f in frames)))
    out = {}
    for b in bones:
        chans = {}
        for ch, key in (("rot", "rotation"), ("pos", "position")):
            if not any(ch in f[1].get(b, {}) for f in frames):
                continue
            chans[key] = {}
            for f in frames:
                t, pose = f[0], f[1]
                e = f[2] if len(f) > 2 else ease
                v = pose.get(b, {}).get(ch, (0, 0, 0))
                vec = to_file_rot(v) if ch == "rot" else [0, r(v[1]), r(v[2])]
                chans[key][str(t)] = {"vector": vec, "easing": e}
        out[b] = chans
    for b, chans in (extra or {}).items():
        out.setdefault(b, {}).update(chans)
    return out


def anims():
    t = "query.anim_time"
    a = {}
    sway = {"scarf_tail": {"rotation": [f"-8 - math.sin({t} * 160) * 6", 0, f"math.sin({t} * 110) * 5"]}}
    # idle: the guard, breathing, the scarf stirring in a wind that is not there
    idle = keyframes([(0.0, GUARD), (1.6, merge(GUARD, P(chest=(-2, 12, 0)))), (3.2, GUARD)], extra=sway)
    a["idle"] = (True, 3.2, idle)
    # stalking: light quick steps sideways round the foe, guard kept up
    walk = keyframes([(0.0, merge(GUARD, P(leg_r=(28, 0, -3), leg_l=(-24, 0, 4), shin_l=(-30, 0, 0)))),
                      (0.3, merge(GUARD, P(leg_r=(-20, 0, -3), leg_l=(26, 0, 4), shin_r=(-30, 0, 0)))),
                      (0.6, merge(GUARD, P(leg_r=(28, 0, -3), leg_l=(-24, 0, 4), shin_l=(-30, 0, 0))))], ease="linear", extra=sway)
    a["walk"] = (True, 0.6, walk)
    # 1. iai: the hand to the hilt, 1.5 s of stillness (he vanishes at its end)...
    sheathe = {"blade": {"scale": {"0.0": {"vector": [1, 1, 1]}, "0.3": {"vector": [1, 1, 1]}, "0.34": {"vector": [0, 0, 0]},
                                   "1.5": {"vector": [0, 0, 0]}}}}
    a["iai_ready"] = ("hold_on_last_frame", 1.5, keyframes([(0.0, GUARD), (0.35, IAI_READY, "easeOutBack"), (1.5, IAI_READY)],
                                                           extra=merge(sway, sheathe)))
    # ...and behind the foe: the draw cut (hits at 0.15 s)
    draw = {"blade": {"scale": {"0.0": {"vector": [0, 0, 0]}, "0.04": {"vector": [1, 1, 1]}}}}
    a["iai_strike"] = (False, 0.9, keyframes([(0.0, IAI_READY), (0.08, IAI_DRAW, "linear"), (0.2, IAI_FOLLOW, "easeOutQuad"),
                                              (0.55, IAI_FOLLOW), (0.9, GUARD)], extra=merge(sway, draw)))
    # 2. three flickering steps: a cut at each (0.35 s each)
    a["step_cut1"] = (False, 0.35, keyframes([(0.0, JODAN), (0.12, KESA_END, "easeOutQuad"), (0.35, KESA_END)]))
    a["step_cut2"] = (False, 0.35, keyframes([(0.0, KESA_END), (0.12, GYAKU_END, "easeOutQuad"), (0.35, GYAKU_END)]))
    a["step_cut3"] = (False, 0.55, keyframes([(0.0, GYAKU_END), (0.12, LEVEL_END, "easeOutQuad"), (0.3, LEVEL_END), (0.55, GUARD)]))
    # 3. the thrust: drawn back 0.6 s, then the lunge held through the dash
    a["thrust"] = ("hold_on_last_frame", 0.8, keyframes([(0.0, GUARD), (0.5, THRUST_BACK), (0.62, THRUST_LUNGE, "easeOutQuad"),
                                                         (0.8, THRUST_LUNGE)], extra=sway))
    # 4. the whirl: a crouch, the leap with two full turns, the landing (hits at 1.15 s)
    spin = {"root": {"rotation": {"0.0": {"vector": [0, 0, 0]}, "0.45": {"vector": [0, 0, 0]},
                                  "0.65": {"vector": [0, 180, 0], "easing": "linear"}, "0.85": {"vector": [0, 360, 0], "easing": "linear"},
                                  "1.0": {"vector": [0, 540, 0], "easing": "linear"}, "1.15": {"vector": [0, 720, 0], "easing": "linear"},
                                  "1.6": {"vector": [0, 720, 0]}}}}
    a["whirl"] = (False, 1.6, keyframes([(0.0, GUARD), (0.4, CROUCH), (0.55, WHIRL, "easeOutQuad"), (1.1, WHIRL),
                                         (1.2, CROUCH, "easeOutQuad"), (1.6, GUARD)], extra=spin))
    # 5. the summoning: the hand to the sky, then three casts (at 1.0, 1.5, 2.0 s)
    cast = [(0.0, GUARD), (0.4, CALL, "easeOutBack"), (0.8, CALL)]
    for k, at in enumerate((1.0, 1.5, 2.0)):
        cast += [(at - 0.1, merge(CALL, P(arm_l=(130, 0, 6)))), (at, POINT, "easeOutQuad")]
    cast += [(2.4, POINT), (2.8, GUARD)]
    a["summon"] = (False, 2.8, keyframes(cast, extra=sway))
    # 6. the combo: kesa, gyaku, then straight down from overhead (hits at 0.35, 0.75, 1.2 s)
    a["combo"] = (False, 1.7, keyframes([(0.0, GUARD), (0.22, JODAN), (0.35, KESA_END, "easeOutQuad"), (0.5, KESA_END),
                                         (0.75, GYAKU_END, "easeOutQuad"), (0.95, JODAN), (1.2, DIVE_END, "easeInQuad"),
                                         (1.4, DIVE_END), (1.7, GUARD)], extra=sway))
    # 7. from the sky: hanging over the foe, blade high (1 s), the dive (hits at 1.2 s), back up
    a["drop"] = (False, 1.9, keyframes([(0.0, HANG), (1.0, HANG), (1.2, DIVE_END, "easeInQuad"), (1.55, DIVE_END), (1.9, GUARD)]))
    # thrown back by a parry
    a["stagger"] = (False, 1.0, keyframes([(0.0, GUARD), (0.12, STAGGER, "easeOutQuad"), (0.7, STAGGER), (1.0, GUARD)]))
    # arriving out of the dark: risen from a crouch, blade drawn
    a["appear"] = (False, 1.2, keyframes([(0.0, CROUCH), (0.5, CROUCH), (1.0, GUARD, "easeOutBack"), (1.2, GUARD)], extra=sway))
    # talking before the fight: standing loose, blade lowered
    a["talk"] = (True, 3.0, keyframes([(0.0, STAND), (1.5, merge(STAND, P(head=(-4, 6, 0)))), (3.0, STAND)], extra=sway))
    # beaten
    a["defeat"] = ("hold_on_last_frame", 1.2, keyframes([(0.0, STAGGER), (0.6, DEFEAT, "easeOutBounce"), (1.2, DEFEAT)]))
    return a


def write_anims():
    out = {}
    for name, (loop, length, bones) in anims().items():
        out[f"animation.renegade.{name}"] = {"loop": loop, "animation_length": length, "bones": bones}
    p = ASSETS / "animations/entity/renegade.animation.json"
    p.write_text(json.dumps({"format_version": "1.8.0", "animations": out}, indent=1))


def write_geo(tex_h):
    by_bone = {}
    for c in CUBES:
        by_bone.setdefault(c.bone, []).append(c)
    bones = []
    for b in BONES.values():
        out = {"name": b["name"], "pivot": [r(-b["pivot"][0]), r(b["pivot"][1]), r(b["pivot"][2])]}
        if b["parent"]:
            out["parent"] = b["parent"]
        if b["rest"]:
            out["rotation"] = to_file_rot(b["rest"])
        cubes = [{"origin": [r(-c.hi[0]), r(c.lo[1]), r(c.lo[2])], "size": [r(s) for s in c.size],
                  "uv": {f: {"uv": [u, v], "uv_size": [W, H]} for f, (u, v, W, H) in c.uv.items()}}
                 for c in by_bone.get(b["name"], [])]
        if cubes:
            out["cubes"] = cubes
        bones.append(out)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.renegade", "texture_width": TEX_W, "texture_height": tex_h,
                        "visible_bounds_width": 4, "visible_bounds_height": 3.5, "visible_bounds_offset": [0, 1.25, 0]},
        "bones": bones}]}
    (ASSETS / "geo/entity/renegade.geo.json").write_text(json.dumps(geo, indent=1))


# ------------------------------------------------------------------ preview (software render of poses)
def rotmat(rx, ry, rz):
    a, b, c = (math.radians(v) for v in (rx, ry, rz))
    Rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    Ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    Rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def posed(points, bname, pose):
    b = bname
    while b:
        tr = pose.get(b, {})
        piv = np.array(BONES[b]["pivot"])
        rot = np.array(tr.get("rot", (0, 0, 0)), float) + np.array(BONES[b]["rest"] or (0, 0, 0), float)
        points = (rotmat(*rot) @ (points - piv).T).T + piv
        if "pos" in tr:
            points = points + np.array(tr["pos"])
        b = BONES[b]["parent"]
    return points


def render(base, pose, yaw, scale=5, sub=2):
    Wp, Hp = 80 * scale, 64 * scale
    img = np.zeros((Hp, Wp, 3), np.float32) + np.array([70, 74, 88], np.float32)
    img[int(Hp * 0.86):] = (52, 70, 52)
    zb = np.full((Hp, Wp), 1e9)
    V = rotmat(12, yaw, 0)
    normals = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
    for c in CUBES:
        for f in FACES:
            u0, v0, W, H = c.uv[f]
            ctr = np.array(face_point(c, f, 0.5, 0.5))
            ends = posed(np.array([ctr, ctr + np.array(normals[f])]), c.bone, pose)
            nrm = V @ (ends[1] - ends[0])
            if nrm[2] > 0.05:
                continue
            shade = 0.6 + 0.3 * max(0, -nrm[2]) + 0.25 * max(0, nrm[1])
            us = (np.arange(W * sub) + 0.5) / (W * sub)
            vs = (np.arange(H * sub) + 0.5) / (H * sub)
            uu, vv = np.meshgrid(us, vs)
            pts = np.array([face_point(c, f, a, b) for a, b in zip(uu.ravel(), vv.ravel())])
            pts = (V @ posed(pts, c.bone, pose).T).T
            tex = base[v0 + np.minimum((vv.ravel() * H).astype(int), H - 1), u0 + np.minimum((uu.ravel() * W).astype(int), W - 1)]
            px = (Wp / 2 - pts[:, 0] * scale).astype(int)
            py = (Hp * 0.86 - pts[:, 1] * scale).astype(int)
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


def aim_blades():
    """Turn each pose's katana bone (rx, ry) so the blade points along BLADE[pose] whatever the arm does."""
    for name, pose in POSES.items():
        if name not in BLADE:
            continue
        pose.pop("katana", None)
        p0 = np.array(BONES["katana"]["pivot"])
        cols = [posed(np.array([p0, p0 + e]), "forearm_r", pose) for e in np.eye(3)]
        M = np.stack([c[1] - c[0] for c in cols], axis=1)   # the forearm's frame in model space
        d = np.array(BLADE[name], float)
        v = M.T @ (d / np.linalg.norm(d))                    # the wanted direction in the katana's parent frame
        a = math.degrees(math.asin(max(-1, min(1, v[1]))))
        b = math.degrees(math.atan2(-v[0], -v[2]))
        pose["katana"] = {"rot": (round(a, 1), round(b, 1), 0)}


def preview(base, out, names=None, yaw=-35):
    names = names or list(POSES)
    tiles = []
    for n in names:
        tile = render(base, POSES[n], yaw)
        tile = Image.fromarray(np.clip(tile, 0, 255).astype(np.uint8))
        from PIL import ImageDraw
        ImageDraw.Draw(tile).text((6, 4), n, fill=(255, 255, 255))
        tiles.append(np.array(tile))
    cols = 5
    rows = []
    for i in range(0, len(tiles), cols):
        row = tiles[i:i + cols]
        while len(row) < cols:
            row.append(np.zeros_like(tiles[0]))
        rows.append(np.concatenate(row, axis=1))
    Image.fromarray(np.concatenate(rows, axis=0)).save(out)


def sheet(base, out):
    """The model for review: the loose stance and the guard, from the front, three-quarter, side and back."""
    from PIL import ImageDraw
    rows = []
    for name in ("stand", "guard"):
        tiles = []
        for yaw, label in ((0, "front"), (-40, "3/4"), (-90, "side"), (180, "back")):
            t = Image.fromarray(np.clip(render(base, POSES[name], yaw, scale=9, sub=3), 0, 255).astype(np.uint8))
            ImageDraw.Draw(t).text((8, 6), f"{name} - {label}", fill=(255, 255, 255))
            tiles.append(np.array(t))
        rows.append(np.concatenate(tiles, axis=1))
    Image.fromarray(np.concatenate(rows, axis=0)).save(out)


def main():
    build()
    aim_blades()
    base, gm, tex_h = pack_and_paint()
    t = ASSETS / "textures/entity"
    Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGBA").save(t / "renegade.png")
    Image.fromarray(np.clip(gm, 0, 255).astype(np.uint8), "RGBA").save(t / "renegade_glowmask.png")
    write_geo(tex_h)
    write_anims()
    if "--sheet" in sys.argv:
        sheet(base, sys.argv[sys.argv.index("--sheet") + 1])
    if "--preview" in sys.argv:
        yaw = float(sys.argv[sys.argv.index("--yaw") + 1]) if "--yaw" in sys.argv else -35
        preview(base, sys.argv[sys.argv.index("--preview") + 1], yaw=yaw)
    print(f"renegade cubes={len(CUBES)} bones={len(BONES)} texture={TEX_W}x{tex_h}")


if __name__ == "__main__":
    main()
