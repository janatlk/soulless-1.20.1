"""The Renegade's animations, baked frame by frame (tools/gen_renegade.py calls bake()).

An animation is a function of time giving *targets*: spine and arm angles, where the blade points, where each foot
stands, whether the left hand holds the grip or the sheath, whether the right hand is at the sheath's mouth (blade
home). The targets run through springs - each part its own stiffness, so the hips lead, the shoulders and arms follow,
the blade whips through last, overshoots a hair and settles. Then each frame is solved:
  - the legs reach their feet to the ground (two-bone IK, knees where told), so a lowered body bends the knees and a
    planted foot never slides or sinks;
  - the katana turns so the blade points where wanted, its edge leading the cut (or as told when still);
  - the left hand closes on the grip behind the right fist, or on the sheath; the right hand finds the hilt at the
    sheath's mouth while the blade is home;
  - the loose ends - headband tails, scarf, the sheath - swing after the body as damped pendulums, in the wind of his
    own movement.
Every bone gets a key each 1/20 s (linear between), so what the game plays is exactly what was solved.

Space as in gen_renegade: one unit = one model pixel, y up, he faces -z, +x is his left; ground at y = 0.
"""
import math

import numpy as np

G = None   # the gen_renegade module (bake() sets it)

FPS = 20
SUB = 12                      # spring substeps per frame
DT = 1.0 / (FPS * SUB)


# ------------------------------------------------------------------ small maths
def unit(v):
    v = np.asarray(v, float)
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def lerp(a, b, k):
    return np.asarray(a, float) * (1 - k) + np.asarray(b, float) * k


def rotmat(rx, ry, rz):
    return G.rotmat(rx, ry, rz)


def _near(sol, prev):
    return tuple(v + 360.0 * round((p - v) / 360.0) for v, p in zip(sol, prev))


def euler(R, prev=None):
    """(rx, ry, rz) with R = Rz Ry Rx; of the two solutions the one nearest prev (no flips, no wraps)."""
    b = math.degrees(math.asin(clamp(-R[2, 0], -1, 1)))
    a = math.degrees(math.atan2(R[2, 1], R[2, 2]))
    c = math.degrees(math.atan2(R[1, 0], R[0, 0]))
    sols = [(a, b, c), (a + 180, 180 - b, c + 180)]
    if prev is None:
        return sols[0]
    cands = [_near(s, prev) for s in sols]
    return min(cands, key=lambda s: sum((s[i] - prev[i]) ** 2 for i in range(3)))


# easing: x in 0..1
def e_lin(x):
    return x


def e_io(x):
    return 0.5 - 0.5 * math.cos(math.pi * x)


def e_in(x):
    return x * x


def e_out(x):
    return 1 - (1 - x) ** 2


def e_in3(x):
    return x ** 3


def e_out3(x):
    return 1 - (1 - x) ** 3


def e_snap(x):
    """quintic in-out: a slow start, a violent middle, a hard stop - a cut."""
    return 16 * x ** 5 if x < 0.5 else 1 - (-2 * x + 2) ** 5 / 2


def e_outback(x, s=1.6):
    x -= 1
    return x * x * ((s + 1) * x + s) + 1


def e_inback(x, s=1.4):
    return x * x * ((s + 1) * x - s)


EASE = {"lin": e_lin, "io": e_io, "in": e_in, "out": e_out, "in3": e_in3, "out3": e_out3, "snap": e_snap,
        "outback": e_outback, "inback": e_inback, "step": lambda x: 1.0 if x >= 1 else 0.0}


# ------------------------------------------------------------------ targets
NEUTRAL = dict(
    root=(0, 0, 0), hips=(0, 0, 0), waist=(0, 0, 0), chest=(0, 0, 0), head=(0, 0, 0),
    arm_r=(0, 0, 0), fore_r=(0, 0, 0), arm_l=(0, 0, 0), fore_l=(0, 0, 0),
    leg_r=(0, 0, 0), shin_r=(0, 0, 0), leg_l=(0, 0, 0), shin_l=(0, 0, 0),
    blade=(0, -0.5, -0.85), edge=(0, -1, 0), grip=0.0, saya_r=0.0, saya_l=0.0,
    foot_r=(-2.2, 0, 0), foot_l=(2.2, 0, 0), plant_r=1.0, plant_l=1.0,
    knee_r=(-0.25, 0, -1), knee_l=(0.25, 0, -1), pole_l=(0.6, -0.6, 0.5), pole_r=(-0.6, -0.6, 0.5),
    sheath=1.0, wind=(0, 0, 0), saya_tilt=0.0,
    hand_r=(-6, 10, -2), reach_r=0.0, hand_l=(6, 10, -2), reach_l=0.0)

VEC = {k for k, v in NEUTRAL.items() if isinstance(v, tuple)}
UNSPRUNG = {"sheath", "wind", "root"}
# (frequency Hz, damping ratio): the core stiff, the extremities looser, so they lag and overshoot
SPRING = {"hips": (9, 0.85), "waist": (9, 0.8), "chest": (7.5, 0.62), "head": (6, 0.55),
          "arm_r": (9, 0.62), "fore_r": (10, 0.6), "arm_l": (7.5, 0.58), "fore_l": (8.5, 0.58),
          "leg_r": (10, 0.8), "shin_r": (10, 0.8), "leg_l": (10, 0.8), "shin_l": (10, 0.8),
          "blade": (10, 0.6), "edge": (9, 0.9), "grip": (14, 1.0), "saya_r": (16, 1.0), "saya_l": (12, 1.0),
          "foot_r": (22, 1.0), "foot_l": (22, 1.0), "plant_r": (18, 1.0), "plant_l": (18, 1.0),
          "saya_tilt": (8, 0.9), "hand_r": (9, 0.8), "hand_l": (9, 0.8), "reach_r": (10, 1.0), "reach_l": (10, 1.0), "knee_r": (8, 1.0), "knee_l": (8, 1.0), "pole_l": (8, 1.0), "pole_r": (8, 1.0)}


def P(base=None, **kw):
    """A target pose: NEUTRAL (or base) with these channels changed."""
    out = dict(base or NEUTRAL)
    out.update(kw)
    return out


def add(pose, **kw):
    """pose with these channels added to (vectors component-wise)."""
    out = dict(pose)
    for k, v in kw.items():
        if k in VEC:
            out[k] = tuple(a + b for a, b in zip(out[k], v))
        else:
            out[k] = out[k] + v
    return out


class Track:
    """Key poses at times, eased in between: [(t, pose, ease into this key)]. Blade and edge directions are
    blended and renormalised (put a key midway for wide arcs)."""

    def __init__(self, keys):
        self.keys = [(k[0], k[1], k[2] if len(k) > 2 else "io") for k in keys]

    def __call__(self, t):
        ks = self.keys
        if t <= ks[0][0]:
            return dict(ks[0][1])
        for (t0, p0, _), (t1, p1, e) in zip(ks, ks[1:]):
            if t <= t1:
                x = EASE[e]((t - t0) / max(1e-9, t1 - t0))
                return blend(p0, p1, x)
        return dict(ks[-1][1])


def blend(p0, p1, x):
    out = {}
    for k in NEUTRAL:
        a, b = p0[k], p1[k]
        if k == "sheath":
            out[k] = b if x >= 1 else a
        elif k in VEC:
            v = tuple(float(u) for u in lerp(a, b, x))
            if k in ("blade", "edge"):
                v = tuple(unit(v))
            out[k] = v
        else:
            out[k] = a * (1 - x) + b * x
    return out


def wave(t, period, phase=0.0):
    return math.sin(2 * math.pi * (t / period + phase))


# ------------------------------------------------------------------ the rig
def local(name, tr):
    B = G.BONES[name]
    rot = np.add(tr.get("rot", (0, 0, 0)), B["rest"] or (0, 0, 0))
    R = rotmat(*rot)
    piv = np.array(B["pivot"], float)
    M = np.eye(4)
    M[:3, :3] = R
    M[:3, 3] = piv - R @ piv + np.array(tr.get("pos", (0, 0, 0)), float)
    return M


def mats(pose):
    out = {}
    for name, B in G.BONES.items():
        L = local(name, pose.get(name, {}))
        out[name] = out[B["parent"]] @ L if B["parent"] else L
    return out


def tp(M, p):
    return M[:3, :3] @ np.asarray(p, float) + M[:3, 3]


def two_bone(Mparent, pivot, target, L1, L2, bend, pole, prev):
    """Upper bone euler and hinge angle (degrees about x, sign `bend`) reaching `target` (model space) from the bone
    pivoted at `pivot`, the middle joint pointing toward `pole`. Returns (upper, hinge, miss)."""
    Rp = Mparent[:3, :3]
    root = tp(Mparent, pivot)
    d = Rp.T @ (np.asarray(target, float) - root)
    pole = Rp.T @ np.asarray(pole, float)
    Dw = float(np.linalg.norm(d))
    D = clamp(Dw, abs(L1 - L2) + 0.05, L1 + L2 - 0.02)
    beta = math.pi - math.acos(clamp((L1 * L1 + L2 * L2 - D * D) / (2 * L1 * L2), -1, 1))
    hinge = math.degrees(beta) * bend
    joint = np.array([0, -L1, 0.0])
    end = joint + rotmat(hinge, 0, 0) @ np.array([0, -L2, 0.0])
    a1 = unit(end)
    a2 = unit(joint - a1 * (joint @ a1))
    b1 = unit(d)
    b2 = pole - b1 * (pole @ b1)
    if np.linalg.norm(b2) < 1e-6:
        b2 = np.array([0, 0, -1.0]) - b1 * (-b1[2])
    b2 = unit(b2)
    R = np.stack([b1, b2, np.cross(b1, b2)], 1) @ np.stack([a1, a2, np.cross(a1, a2)], 1).T
    return euler(R, prev), hinge, max(0.0, Dw - D)


def katana_rot(Mfore, blade, edge, prev):
    """The katana bone's euler turning its blade (-z) along `blade` and its edge (-y) toward `edge` (model space)."""
    Rp = Mfore[:3, :3]
    d = unit(Rp.T @ unit(blade))
    e = Rp.T @ np.asarray(edge, float)
    e = e - d * (e @ d)
    if np.linalg.norm(e) < 1e-6:
        e = np.array([0, -1.0, 0]) - d * (-d[1])
    e = unit(e)
    A = np.stack([[0, 0, -1.0], [0, -1.0, 0], np.cross([0, 0, -1.0], [0, -1.0, 0])], 1)
    B = np.stack([d, e, np.cross(d, e)], 1)
    return euler(B @ A.T, prev)


LIMB = {  # upper bone, lower bone, parent, upper length, lower length, hinge sign
    "r": ("arm_r", "forearm_r", "chest", 6.3, 5.9, 1),
    "l": ("arm_l", "forearm_l", "chest", 6.3, 5.9, 1),
    "legr": ("leg_r", "shin_r", "waist", 6.0, 6.0, -1),
    "legl": ("leg_l", "shin_l", "waist", 6.0, 6.0, -1),
}


def grip_point():
    return np.array([G.FIST[0], G.FIST[1], G.FIST[2] + 2.9])


def saya_frame(M):
    """The sheath's mouth and its axis (toward its tip), model space."""
    Ms = M["saya"]
    mouth = tp(Ms, (4.4, 12.4, -4.4))
    axis = unit(Ms[:3, :3] @ np.array([0, 0, 1.0]))
    return mouth, axis


# ------------------------------------------------------------------ the solve
class Solver:
    def __init__(self):
        self.prev = {}
        self.assist = np.zeros(2)
        self.lean = np.zeros(2)
        self.reports = []

    def frame(self, tg):
        """Targets (already sprung) -> bone transforms {bone: {"rot":..., "pos":...}} plus a report."""
        prev = self.prev
        pose = {
            "root": {"rot": tg["root"]},
            "waist": {"rot": tg["waist"], "pos": tg["hips"]},
            "chest": {"rot": tg["chest"]},
            "head": {"rot": tg["head"]},
            "arm_r": {"rot": tg["arm_r"]}, "forearm_r": {"rot": tg["fore_r"]},
            "arm_l": {"rot": tg["arm_l"]}, "forearm_l": {"rot": tg["fore_l"]},
            "leg_r": {"rot": tg["leg_r"]}, "shin_r": {"rot": tg["shin_r"]},
            "leg_l": {"rot": tg["leg_l"]}, "shin_l": {"rot": tg["shin_l"]},
            "saya": {"rot": (tg["saya_tilt"], 0, 0)},
        }
        rep = {}
        M = mats(pose)
        # legs: feet to their marks
        for side, key in (("r", "legr"), ("l", "legl")):
            w = clamp(tg["plant_" + side])
            if w <= 0.001:
                continue
            up, lo, par, L1, L2, sg = LIMB[key]
            u, h, miss = two_bone(M[par], G.BONES[up]["pivot"], tg["foot_" + side], L1, L2, sg,
                                  tg["knee_" + side], prev.get(up))
            pose[up] = {"rot": tuple(lerp(tg["leg_" + side], u, w))}
            pose[lo] = {"rot": tuple(lerp(tg["shin_" + side], (h, 0, 0), w))}
            rep["foot_miss_" + side] = miss * w
        M = mats(pose)
        # the right hand to the hilt at the sheath's mouth while the blade is home
        mouth, axis = saya_frame(M)
        sr = clamp(tg["saya_r"])
        if sr > 0.001:
            target = mouth - axis * 2.4 + np.array([0, 0.3, 0])
            c0 = np.array(tg["chest"], float)

            def reach(off):
                pose["chest"] = {"rot": tuple(c0 + np.array([off[0], off[1], 0]) * sr)}
                Mc = mats(pose)
                return two_bone(Mc["chest"], G.BONES["arm_r"]["pivot"], target, 6.3, 5.9, 1, tg["pole_r"], prev.get("arm_r"))

            u, h, miss = reach(self.lean)
            if miss > 0.05:
                # out of reach: lean and turn the chest further toward the hilt (warm from the last frame)
                a, step = self.lean.copy(), 4.0
                for _ in range(30):
                    better = False
                    for dv in ((-step, 0), (step, 0), (0, -step), (0, step)):
                        cand = a + np.array(dv)
                        if abs(cand[0]) > 25 or abs(cand[1]) > 30:
                            continue
                        m2 = reach(cand)[2]
                        if m2 + 0.001 * (cand @ cand) < miss + 0.001 * (a @ a) - 1e-4:
                            a, miss, better = cand, m2, True
                    if not better:
                        step *= 0.5
                        if step < 0.25:
                            break
                self.lean = a
                u, h, miss = reach(a)
            else:
                self.lean *= 0.9
            pose["arm_r"] = {"rot": tuple(lerp(tg["arm_r"], u, sr))}
            pose["forearm_r"] = {"rot": tuple(lerp(tg["fore_r"], (h, 0, 0), sr))}
            rep["saya_miss"] = miss * sr
        # a hand put somewhere on purpose (on a knee, on a thigh)
        for side in ("r", "l"):
            w = clamp(tg["reach_" + side])
            if w > 0.001:
                Mh = mats(pose)
                u, h, miss = two_bone(Mh["chest"], G.BONES["arm_" + side]["pivot"], tg["hand_" + side], 6.3, 5.9, 1,
                                      tg["pole_" + side], prev.get("arm_" + side))
                pose["arm_" + side] = {"rot": tuple(lerp(pose["arm_" + side]["rot"], u, w))}
                pose["forearm_" + side] = {"rot": tuple(lerp(pose["forearm_" + side]["rot"], (h, 0, 0), w))}
                rep["hand_miss_" + side] = miss * w
        blade = unit(lerp(unit(tg["blade"]), axis, sr))
        edge = unit(lerp(unit(tg["edge"]), M["saya"][:3, :3] @ np.array([0, 1.0, 0]), sr))
        if tg["sheath"] >= 0.5 and min(tg["plant_r"], tg["plant_l"]) > 0.5:
            # never into the ground: a point that would go below it is lifted to skim over it
            hand = tp(mats(pose)["forearm_r"], G.FIST)
            L = blade_length()
            floor = 2.4
            if hand[1] + blade[1] * L < floor:
                y = clamp((floor - hand[1]) / L, -1, 0.95)
                h = np.array([blade[0], 0, blade[2]])
                h = unit(h) if np.linalg.norm(h) > 1e-6 else np.array([0, 0, -1.0])
                blade = h * math.sqrt(1 - y * y) + np.array([0, y, 0])
        # both hands: if the grip is out of the left hand's reach, the right arm comes in and the chest turns a little
        gw = clamp(tg["grip"])
        base_r, base_c = np.array(pose["arm_r"]["rot"], float), np.array(pose["chest"]["rot"], float)

        def solve_left(assist):
            pose["arm_r"]["rot"] = (base_r[0], base_r[1], base_r[2] + assist[0] * gw)
            pose["chest"]["rot"] = (base_c[0], base_c[1] + assist[1] * gw, base_c[2])
            Mx = mats(pose)
            k = katana_rot(Mx["forearm_r"], blade, edge, prev.get("katana"))
            pose["katana"] = {"rot": k}
            Mx = mats(pose)
            sl = clamp(tg["saya_l"])
            m2, ax2 = saya_frame(Mx)
            tgt_grip = tp(Mx["katana"], grip_point())
            tgt_saya = m2 + ax2 * 1.6 + np.array([0.4, -0.2, 0])
            w = max(gw, sl)
            if w <= 0.001:
                return 0.0, Mx, None
            target = (tgt_grip * gw + tgt_saya * sl) / max(1e-6, gw + sl)
            u, h, miss = two_bone(Mx["chest"], G.BONES["arm_l"]["pivot"], target, 6.3, 5.9, 1, tg["pole_l"], prev.get("arm_l"))
            return miss, Mx, (u, h, w)

        miss, Mx, sol = solve_left(self.assist if gw > 0.001 else np.zeros(2))
        if gw > 0.3 and miss > 0.05:
            # a few steps of a small search (warm from the last frame: the correction moves smoothly)
            a = self.assist.copy()
            step = 4.0
            for _ in range(40):
                improved = False
                for dv in ((step, 0), (-step, 0), (0, step), (0, -step)):
                    cand = a + np.array(dv)
                    if abs(cand[0]) > 45 or abs(cand[1]) > 30:
                        continue
                    m2, _, _ = solve_left(cand)
                    if m2 + 0.002 * (cand @ cand) < miss + 0.002 * (a @ a) - 1e-4:
                        a, miss, improved = cand, m2, True
                if not improved:
                    step *= 0.5
                    if step < 0.25:
                        break
            self.assist = a
            miss, Mx, sol = solve_left(a)
        elif gw <= 0.3:
            self.assist *= 0.8
        if sol:
            u, h, w = sol
            if gw > 0.001 and miss > 0.4:
                w *= clamp(1 - (miss - 0.4) / 1.2)
            pose["arm_l"] = {"rot": tuple(lerp(tg["arm_l"], u, w))}
            pose["forearm_l"] = {"rot": tuple(lerp(tg["fore_l"], (h, 0, 0), w))}
            rep["grip_miss"] = miss * w
        pose["blade"] = {"scale": (1, 1, 1) if tg["sheath"] >= 0.5 else (0, 0, 0)}
        for b in ("arm_r", "forearm_r", "arm_l", "forearm_l", "leg_r", "shin_r", "leg_l", "shin_l", "katana", "chest"):
            prev[b] = tuple(pose[b]["rot"])
        self.reports.append(rep)
        return pose


# ------------------------------------------------------------------ loose ends
TAILS = {  # bone: (rest direction of the rod in the bone's own frame, length, stiffness, damping, gravity, max swing,
           #        keep it from swinging forward into the body)
    "band_tail": ((0, -1, 0), 4.2, 60.0, 6.0, 160.0, 95.0, True),
    "scarf_tail": ((0, -1, 0), 10.0, 24.0, 5.0, 160.0, 70.0, True),
    "saya": ((0, 0, 1), 20.0, 260.0, 18.0, 60.0, 40.0, False),
}


def simulate_tails(frames, winds, loop):
    """Each loose end is a weight on a rod from its pivot: gravity, a pull back to its rest direction, air drag in the
    wind of his movement; the rod's direction becomes the bone's turn (relative to its parent)."""
    out = {}
    n = len(frames)
    Ms = [mats(f) for f in frames]
    for b, (rest, L, k, c, g, maxdeg, behind) in TAILS.items():
        par = G.BONES[b]["parent"]
        piv = np.array(G.BONES[b]["pivot"], float)
        rest_rot = tuple(G.BONES[b]["rest"] or (0, 0, 0))
        Rr = rotmat(*rest_rot)
        r0 = unit(Rr @ unit(rest))                 # the rod at rest, in the parent's frame
        P = [tp(m[par], piv) for m in Ms]
        Rpar = [m[par][:3, :3] for m in Ms]
        tip = P[0] + Rpar[0] @ r0 * L
        vel = np.zeros(3)
        sub = 8
        dt = 1.0 / (FPS * sub)
        res = []
        for p in range(3 if loop else 1):
            for i in range(n):
                j = (i + 1) % n if loop else min(i + 1, n - 1)
                for s_ in range(sub):
                    x = s_ / sub
                    piv_w = P[i] * (1 - x) + P[j] * x
                    want = piv_w + Rpar[i] @ r0 * L
                    acc = np.array([0, -g, 0]) + k * (want - tip) + c * (np.asarray(winds[i], float) - vel)
                    vel = vel + acc * dt
                    tip = tip + vel * dt
                    r = tip - piv_w
                    rn = np.linalg.norm(r)
                    r = r / rn if rn > 1e-9 else Rpar[i] @ r0
                    tip = piv_w + r * L
                    if tip[1] < 1.8:   # the ground pushes it up (the sheath's tip as he crouches)
                        tip[1] = 1.8
                        r = unit(tip - piv_w)
                        tip = piv_w + r * L
                        vel[1] = max(0.0, vel[1])
                    vel = vel - r * (vel @ r)
                if p == (2 if loop else 0):
                    u = Rpar[i].T @ unit(tip - P[i])
                    if behind and u[2] < 0:
                        u = unit(np.array([u[0], u[1], 0.0]))
                    ang = math.degrees(math.acos(clamp(float(r0 @ u), -1, 1)))
                    if ang > maxdeg:
                        u = unit(slerp(r0, u, maxdeg / ang))
                    e = euler(rot_between(r0, u) @ Rr, (np.add(res[-1], rest_rot) if res else rest_rot))
                    res.append(tuple(np.subtract(e, rest_rot)))
        out[b] = res
    return out


def slerp(a, b, x):
    a, b = unit(a), unit(b)
    om = math.acos(clamp(float(a @ b), -1, 1))
    if om < 1e-6:
        return b
    return (math.sin((1 - x) * om) * a + math.sin(x * om) * b) / math.sin(om)


def rot_between(a, b):
    a, b = unit(a), unit(b)
    v = np.cross(a, b)
    s = np.linalg.norm(v)
    c = float(a @ b)
    if s < 1e-9:
        return np.eye(3)
    K = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + K + K @ K * ((1 - c) / (s * s))


# ------------------------------------------------------------------ springs and baking
def spring_run(series, f, z, loop):
    """series: (N, k) targets at DT steps -> the spring's path (N, k)."""
    w = 2 * math.pi * f
    x = series[0].copy()
    v = np.zeros_like(x)
    out = np.empty_like(series)
    n = max(1, int(math.ceil(w * DT / 0.15)))   # small enough steps to stay stable however stiff
    h = DT / n
    for p in range(3 if loop else 1):
        for i, tgt in enumerate(series):
            for _ in range(n):
                v += (w * w * (tgt - x) - 2 * z * w * v) * h
                x = x + v * h
            if p == (2 if loop else 0):
                out[i] = x
    return out


def bake_one(name, fn, length, loop, snap=1.0, spring_over=None):
    """Sample the target function densely, spring it, solve each frame. Returns (frames: [pose], report)."""
    n = int(round(length * FPS * SUB))
    ts = [i * DT for i in range(n + (0 if loop else 1))]
    raw = [fn(t % length if loop else t) for t in ts]
    sprung = {}
    for k in NEUTRAL:
        arr = np.array([np.atleast_1d(np.asarray(r[k], float)) for r in raw])
        if k in UNSPRUNG or k not in SPRING:
            sprung[k] = arr
            continue
        f, z = SPRING[k]
        if spring_over and k in spring_over:
            f, z = spring_over[k]
        else:
            f *= snap
        sprung[k] = spring_run(arr, f, z, loop)
    # the edge leads: while the blade sweeps, its edge faces the way it moves
    bl = sprung["blade"] / np.maximum(1e-9, np.linalg.norm(sprung["blade"], axis=1, keepdims=True))
    vel = np.gradient(bl, DT, axis=0)
    vel = vel - bl * np.sum(vel * bl, axis=1, keepdims=True)
    sp = np.linalg.norm(vel, axis=1, keepdims=True)
    lead = vel / np.maximum(1e-9, sp)
    wgt = np.clip((sp - 2.5) / 6.0, 0, 1)
    hint = sprung["edge"] / np.maximum(1e-9, np.linalg.norm(sprung["edge"], axis=1, keepdims=True))
    ed = hint * (1 - wgt) + lead * wgt
    sprung["edge"] = spring_run(ed, 14, 1.0, loop)
    solver = Solver()
    frames, winds, tgts = [], [], []
    count = n // SUB + (0 if loop else 1)
    # one warm-up pass for loops so the IK's "nearest solution" memory starts from the loop's own end
    passes = [0, 1] if loop else [1]
    for p in passes:
        frames, winds, tgts = [], [], []
        for fi in range(count):
            i = fi * SUB
            tg = {}
            for k in NEUTRAL:
                v = sprung[k][i]
                tg[k] = tuple(float(a) for a in v) if k in VEC else float(v[0])
            tg["blade"] = tuple(unit(tg["blade"]))
            frames.append(solver.frame(tg))
            winds.append(tg["wind"])
            tgts.append(tg)
    tails = simulate_tails(frames, winds, loop)
    for i, f in enumerate(frames):
        for b in TAILS:
            sw = 1.0
            if b == "saya":
                sw = 1.0 - max(tgts[i]["saya_r"], tgts[i]["saya_l"])
            f[b] = {"rot": tuple(np.asarray(tails[b][i]) * sw + (np.array([tgts[i]["saya_tilt"], 0, 0]) if b == "saya" else 0))}
    rp = report(name, frames, tgts)
    reps = solver.reports[-len(frames):]
    for k in ("grip_miss", "saya_miss", "foot_miss_r", "foot_miss_l", "hand_miss_r", "hand_miss_l"):
        vals = [r.get(k, 0.0) for r in reps]
        rp[k] = (max(vals), vals.index(max(vals)) / FPS)
    return frames, rp


# ------------------------------------------------------------------ checks
_BLADE_PTS = None


def blade_points():
    """Corners of the blade's cubes, per bone, for the ground check."""
    global _BLADE_PTS
    if _BLADE_PTS is None:
        pts = {}
        for c in G.CUBES:
            if c.bone not in ("blade", "katana_model", "saya"):
                continue
            cr = rotmat(*c.rot) if c.rot else np.eye(3)
            cp = np.array(c.pivot, float)
            for x in (c.lo[0], c.hi[0]):
                for y in (c.lo[1], c.hi[1]):
                    for z in (c.lo[2], c.hi[2]):
                        pts.setdefault(c.bone, []).append(cr @ (np.array([x, y, z]) - cp) + cp)
        _BLADE_PTS = {b: np.array(v) for b, v in pts.items()}
    return _BLADE_PTS


_BLADE_L = None


def blade_length():
    """From the fist to the point, with a little to spare for the curve."""
    global _BLADE_L
    if _BLADE_L is None:
        M = mats({})
        _BLADE_L = float(np.linalg.norm(blade_tip(M) - tp(M["forearm_r"], G.FIST))) + 0.6
    return _BLADE_L


def blade_low(M, sheathed):
    lows = []
    for b, pts in blade_points().items():
        if b == "blade" and sheathed:
            continue
        w = (M[b][:3, :3] @ pts.T).T + M[b][:3, 3]
        lows.append(w[:, 1].min())
    return min(lows)


def blade_tip(M):
    pts = blade_points()["blade"]
    w = (M["blade"][:3, :3] @ pts.T).T + M["blade"][:3, 3]
    hand = M["katana"][:3, 3] + M["katana"][:3, :3] @ np.array(G.FIST)
    return w[np.argmax(np.linalg.norm(w - hand, axis=1))]


def report(name, frames, tgts):
    lows, feet, grips, tips = [], [], [], []
    for f, tg in zip(frames, tgts):
        M = mats(f)
        airborne = max(tg["plant_r"], tg["plant_l"]) < 0.5
        lows.append(99.0 if airborne else blade_low(M, tg["sheath"] < 0.5))
        fl = []
        for s in ("r", "l"):
            fl.append(tp(M["shin_" + s], (2.2 if s == "l" else -2.2, 0, 0))[1])
            fl.append(tp(M["shin_" + s], (2.2 if s == "l" else -2.2, 6, 0))[1])   # the knee
        feet.append(min(fl))
        tips.append(blade_tip(M))
    speeds = [0.0] + [float(np.linalg.norm(tips[i] - tips[i - 1]) * FPS) for i in range(1, len(tips))]
    peaks = [i for i in range(1, len(speeds) - 1) if speeds[i] >= speeds[i - 1] and speeds[i] >= speeds[i + 1] and speeds[i] > 120]
    return {"blade_low": min(lows), "blade_low_at": lows.index(min(lows)) / FPS, "feet_low": min(feet),
            "peaks": [(round(i / FPS, 2), int(speeds[i])) for i in peaks]}


# ------------------------------------------------------------------ writing
def r3(v):
    return round(float(v), 3)


def write(anims, path):
    """{name: (loop, length, frames)} -> GeckoLib animation json, a key per bone per frame."""
    import json
    out = {}
    for name, (loop, length, frames) in anims.items():
        bones = {}
        keys = list(frames)
        if loop is True:
            keys = keys + [keys[0]]
        for i, f in enumerate(keys):
            t = str(round(min(length, i / FPS), 4))
            for b, tr in f.items():
                ch = bones.setdefault(b, {})
                if "rot" in tr:
                    rx, ry, rz = tr["rot"]
                    ch.setdefault("rotation", {})[t] = {"vector": [r3(-rx), r3(-ry), r3(rz)], "easing": "linear"}
                if "pos" in tr:
                    x, y, z = tr["pos"]
                    ch.setdefault("position", {})[t] = {"vector": [r3(-x), r3(y), r3(z)], "easing": "linear"}
                if "scale" in tr:
                    ch.setdefault("scale", {})[t] = {"vector": [r3(v) for v in tr["scale"]], "easing": "linear"}
        # the blade appears or vanishes at once: a key just before each change holds the old scale
        sc = bones.get("blade", {}).get("scale")
        if sc:
            ts = sorted(sc, key=float)
            fixed = {}
            for a, b in zip(ts, ts[1:]):
                fixed[a] = sc[a]
                if sc[a]["vector"] != sc[b]["vector"]:
                    fixed[str(round(float(b) - 0.01, 4))] = dict(sc[a])
            fixed[ts[-1]] = sc[ts[-1]]
            bones["blade"]["scale"] = dict(sorted(fixed.items(), key=lambda kv: float(kv[0])))
        out[f"animation.renegade.{name}"] = {"loop": loop, "animation_length": length, "bones": bones}
    path.write_text(json.dumps({"format_version": "1.8.0", "animations": out}, separators=(",", ":")))


# ------------------------------------------------------------------ a quick software render, for checking
class Render:
    def __init__(self, base, scale=4, sub=2):
        self.scale = scale
        self.items = []   # per bone: local points, colours, local normals
        normals = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
        ua = {"north": 0, "south": 0, "up": 0, "down": 0, "east": 2, "west": 2}
        va = {"north": 1, "south": 1, "east": 1, "west": 1, "up": 2, "down": 2}
        per = {}
        for c in G.CUBES:
            cr = rotmat(*c.rot) if c.rot else np.eye(3)
            cp = np.array(c.pivot, float)
            for f in G.FACES:
                u0, v0, W, H = c.uv[f]
                nu = max(2, int(math.ceil(c.size[ua[f]] * sub * scale / 2)))
                nv = max(2, int(math.ceil(c.size[va[f]] * sub * scale / 2)))
                us = (np.arange(nu) + 0.5) / nu
                vs = (np.arange(nv) + 0.5) / nv
                uu, vv = np.meshgrid(us, vs)
                pts = np.array([G.face_point(c, f, a, b) for a, b in zip(uu.ravel(), vv.ravel())])
                pts = (cr @ (pts - cp).T).T + cp
                tex = base[v0 + np.minimum((vv.ravel() * H).astype(int), H - 1), u0 + np.minimum((uu.ravel() * W).astype(int), W - 1)]
                keep = tex[:, 3] > 0
                n = np.tile(cr @ np.array(normals[f], float), (int(keep.sum()), 1))
                d = per.setdefault(c.bone, [[], [], []])
                d[0].append(pts[keep])
                d[1].append(tex[keep, :3])
                d[2].append(n)
        for b, (p, col, n) in per.items():
            self.items.append((b, np.concatenate(p), np.concatenate(col), np.concatenate(n)))

    def frame(self, pose, yaw=-35, size=(88, 62)):
        s = self.scale
        Wp, Hp = int(size[0] * s), int(size[1] * s)
        img = np.zeros((Hp, Wp, 3), np.float32) + np.array([62, 66, 80], np.float32)
        gy = int(Hp * 0.86)
        img[gy:] = (40, 42, 52)
        V = rotmat(10, yaw, 0)
        M = mats(pose)
        allp, allc, alls = [], [], []
        for b, pts, col, nrm in self.items:
            if b == "blade" and pose.get("blade", {}).get("scale", (1, 1, 1))[0] < 0.5:
                continue
            R = M[b][:3, :3]
            w = (R @ pts.T).T + M[b][:3, 3]
            n = (V @ (R @ nrm.T)).T
            vis = n[:, 2] < 0.1
            allp.append((V @ w[vis].T).T)
            allc.append(col[vis])
            alls.append(0.62 + 0.3 * np.clip(-n[vis, 2], 0, 1) + 0.22 * np.clip(n[vis, 1], 0, 1))
        v = np.concatenate(allp)
        col = np.concatenate(allc) * np.concatenate(alls)[:, None]
        px = np.round(Wp / 2 + v[:, 0] * s).astype(int)
        py = np.round(gy - v[:, 1] * s).astype(int)
        z = v[:, 2]
        offs = [(dx, dy) for dx in range(-1, 2) for dy in range(-1, 2)]
        X = np.concatenate([px + dx for dx, dy in offs])
        Y = np.concatenate([py + dy for dx, dy in offs])
        Z = np.tile(z, len(offs))
        C = np.tile(col, (len(offs), 1))
        ok = (X >= 0) & (X < Wp) & (Y >= 0) & (Y < Hp)
        X, Y, Z, C = X[ok], Y[ok], Z[ok], C[ok]
        idx = Y * Wp + X
        order = np.lexsort((Z, idx))
        idx, C = idx[order], C[order]
        first = np.ones(len(idx), bool)
        first[1:] = idx[1:] != idx[:-1]
        flat = img.reshape(-1, 3)
        flat[idx[first]] = C[first]
        return np.clip(img, 0, 255).astype(np.uint8)
