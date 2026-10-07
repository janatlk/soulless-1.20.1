"""The Renegade's choreography: each animation as targets over time (tools/renegade_motion.py solves and bakes them).

Timings follow RenegadeEntity: where a blow lands, when he leaves the ground, when the blades fly. Times in seconds
from the animation's start.

Angles (degrees, model space - see gen_renegade): rx > 0 lifts a hanging part forward and up - so for the waist and
chest, which point up, rx > 0 leans back and rx < 0 bows forward; for the head rx > 0 looks up. ry > 0 turns the front
toward his right (-x); rz > 0 swings toward his left (+x) - a right arm out sideways is rz < 0, a left one rz > 0.
Directions (blade, edge, poles, knees) are model-space vectors: -z forward, +y up, +x his left.
"""
import math

from renegade_motion import P, add, Track, wave, e_io, NEUTRAL  # noqa: F401


def bump(t, t0, t1):
    """0 -> 1 -> 0 smoothly over [t0, t1]."""
    if t <= t0 or t >= t1:
        return 0.0
    return math.sin(math.pi * (t - t0) / (t1 - t0)) ** 2


def ramp(t, t0, t1):
    if t <= t0:
        return 0.0
    if t >= t1:
        return 1.0
    return e_io((t - t0) / (t1 - t0))


# ------------------------------------------------------------------ stances
# chudan: the point at the foe's throat, both hands on the grip, right foot forward, knees soft
GUARD = P(hips=(0.3, -1.5, 0.4), waist=(-3, -10, 0), chest=(-3, 18, 0), head=(4, -8, 0),
          arm_r=(24, 0, 14), fore_r=(50, 0, 0), arm_l=(40, 0, -30), fore_l=(40, 0, 0),
          blade=(0.03, 0.34, -0.94), edge=(0, -1, -0.4), grip=1.0,
          foot_r=(-2.8, 0, -4.4), foot_l=(3.2, 0, 4.4), knee_r=(-0.3, 0, -1), knee_l=(0.6, 0, -1),
          pole_l=(0.7, -0.6, 0.3))
# a lower, wider version for the end of a lunge
LUNGE_FEET = dict(foot_r=(-3.2, 0, -7.0), foot_l=(3.4, 0, 6.0), knee_r=(-0.3, -0.2, -1), knee_l=(0.4, -0.8, -0.5))
# overhead (jodan): both hands above the forehead, the blade back and up
JODAN = P(GUARD, hips=(0.2, -1.0, 0.6), waist=(-2, -8, 0), chest=(-6, 12, 0), head=(-2, -6, 0),
          arm_r=(160, 0, 12), fore_r=(30, 0, 0), arm_l=(160, 0, -26), fore_l=(30, 0, 0),
          blade=(0.0, 0.62, 0.78), edge=(0, 0.4, -1))
# hasso: hands by the right cheek, blade standing up and back - the cocked position for a diagonal cut
HASSO = P(GUARD, hips=(0.0, -1.6, 0.4), waist=(0, -16, 0), chest=(-4, -6, 0), head=(2, 14, 0),
          arm_r=(96, 18, -30), fore_r=(70, 0, 0), arm_l=(70, 0, -10), fore_l=(60, 0, 0),
          blade=(-0.3, 0.8, 0.5), edge=(0.3, 0.2, -1))
# the sheath held, the hand on the hilt: the iai, ready
IAI_SET = P(GUARD, hips=(0.6, -4.2, 1.6), waist=(-10, -26, 0), chest=(-22, -34, 8), head=(14, 52, -8),
            arm_r=(20, 0, 30), fore_r=(40, 0, 0), arm_l=(20, 0, 0), fore_l=(40, 0, 0),
            grip=0.0, saya_r=1.0, saya_l=1.0, sheath=0.0, saya_tilt=-20.0, pole_r=(-0.3, -0.5, 0.8), pole_l=(0.8, -0.5, 0.3),
            foot_r=(-3.2, 0, -6.2), foot_l=(3.4, 0, 5.4), knee_r=(-0.4, -0.1, -1), knee_l=(0.5, -0.6, -0.6))
# standing loose for talking: the blade resting on the right shoulder, the left hand on the obi, weight on the left leg
SHOULDER = P(hips=(0.8, -0.3, 0.2), waist=(0, -6, -3), chest=(-4, 10, 3), head=(6, -4, -3),
             arm_r=(52, 10, -12), fore_r=(112, 0, 0), arm_l=(-6, 0, 14), fore_l=(70, 0, 0),
             blade=(-0.22, 0.52, 0.82), edge=(-0.4, 1, 0), grip=0.0,
             foot_r=(-3.0, 0, -1.4), foot_l=(2.6, 0, 1.0), knee_r=(-0.4, 0, -1), knee_l=(0.3, 0, -1))


# ------------------------------------------------------------------ idle and moving
def idle(t):
    br, sw = wave(t, 2.0), wave(t, 4.0)
    p = add(GUARD, hips=(0.45 * sw, -0.3 * (br + 1) * 0.5, 0), chest=(-1.4 * br, 2.0 * sw, 0.6 * sw),
            head=(1.0 * br, -2.0 * sw, -0.8 * sw), arm_r=(1.5 * br, 0, 0), waist=(0, 1.2 * sw, 0))
    # the point slowly circling, and once a loop the grip re-taken: a small dip and lift of the point
    a = 2 * math.pi * t / 4.0
    dip = bump(t, 2.5, 3.1)
    p["blade"] = (p["blade"][0] + 0.035 * math.cos(a), p["blade"][1] + 0.03 * math.sin(2 * a) - 0.14 * dip,
                  p["blade"][2])
    return add(p, fore_r=(-6 * dip, 0, 0))


def gait(t, T, move, stride, order, lift=1.1):
    """Foot targets for a light shuffle (okuri-ashi: the leading foot steps, the other follows; feet never cross).
    `move`: his direction of travel (model space, x/z). Each foot is planted between its steps and slides back
    relative to the body as the body moves on. Returns (feet dict, hips offset)."""
    phi = (t / T) % 1.0
    windows = {order[0]: (0.04, 0.36), order[1]: (0.52, 0.84)}
    marks = {"r": GUARD["foot_r"], "l": GUARD["foot_l"]}
    out, hips_y, shift = {}, 0.0, 0.0
    for side, (w0, w1) in windows.items():
        if phi < w0:
            prog, q = 0.0, None
        elif phi > w1:
            prog, q = 1.0, None
        else:
            q = (phi - w0) / (w1 - w0)
            prog = e_io(q)
        centre = (w0 + w1) / 2 - 0.5
        off = stride * ((phi - prog) - centre)
        m = marks[side]
        y = lift * math.sin(math.pi * q) if q is not None else 0.0
        out["foot_" + side] = (m[0] - move[0] * off, y, m[2] - move[1] * off)
        if q is not None:
            hips_y += 0.35 * math.sin(math.pi * q)
            shift += (-1 if side == "l" else 1) * 0.5 * math.sin(math.pi * q)   # weight over the planted foot
    return out, (shift, hips_y, 0.0)


def mover(T, move, stride, order, speed):
    def fn(t):
        feet, (sx, sy, _) = gait(t, T, move, stride, order)
        ph = 2 * math.pi * t / T
        p = add(GUARD, hips=(sx, sy - 0.5, 0), chest=(1.5 * math.sin(2 * ph), 3 * math.sin(ph), 0),
                head=(-1.2 * math.sin(2 * ph), -3 * math.sin(ph), 0), waist=(0, -3 * math.sin(ph), 0))
        p.update(feet)
        b = p["blade"]
        p["blade"] = (b[0] + 0.03 * math.sin(ph), b[1] + 0.05 * math.sin(2 * ph + 0.8), b[2])
        p["wind"] = (-move[0] * speed, 0, -move[1] * speed)
        return p
    return fn


# ------------------------------------------------------------------ talking, arriving, beaten
def talk(t):
    """2.5 s: a line every 2.5 s (RenegadeEncounter) - a gesture of the left hand with each, the head with the words."""
    br = wave(t, 2.5)
    g = bump(t, 0.15, 1.9)
    p = add(SHOULDER, chest=(-1.2 * br, 3 * g, 0), head=(2.5 * g - 4 * bump(t, 0.3, 0.9), -6 * g, 3 * bump(t, 0.9, 1.6)),
            hips=(0, -0.2 * br, 0), waist=(0, 2 * g, 0))
    # the left hand: off the obi, an open hand toward the foe, back
    p["arm_l"] = tuple(a + (b - a) * g for a, b in zip(SHOULDER["arm_l"], (72, 24, 34)))
    p["fore_l"] = tuple(a + (b - a) * g for a, b in zip(SHOULDER["fore_l"], (18, 0, 0)))
    # the blade on the shoulder rocks with the breath
    b = p["blade"]
    p["blade"] = (b[0], b[1] + 0.03 * br, b[2])
    return p


APPEAR = Track([
    (0.0, P(IAI_SET, hips=(0.6, -5.4, 1.8), chest=(-26, -6, 0), head=(-10, 26, 0),
            foot_r=(-3.0, 0, -5.0), foot_l=(3.2, 0, 6.6), knee_l=(0.3, -1, -0.2))),          # kneeling, head bowed
    (0.35, P(IAI_SET, hips=(0.6, -5.2, 1.8), chest=(-20, -6, 0), head=(8, 26, 0),
             foot_r=(-3.0, 0, -5.0), foot_l=(3.2, 0, 6.6), knee_l=(0.3, -1, -0.2))),          # the head comes up
    (0.55, P(IAI_SET, hips=(0.4, -3.0, 1.2), head=(6, 22, 0)), "in"),                         # rising
    (0.62, P(GUARD, hips=(0.2, -2.2, 0.6), waist=(0, 18, 0), chest=(-6, 26, 0), head=(2, -20, 0),
             arm_r=(80, -60, 0), fore_r=(6, 0, 0), arm_l=(20, 0, 30), fore_l=(30, 0, 0),
             blade=(-0.55, 0.1, -0.83), grip=0.0, saya_r=0.0, saya_l=0.0, sheath=1.0), "out3"),  # drawn, a sweep out
    (0.85, P(GUARD, hips=(0.4, -1.2, 0.4), waist=(0, 8, 0), chest=(-4, 14, 0), head=(4, -10, 0),
             arm_r=(70, -30, -30), fore_r=(40, 0, 0), arm_l=(10, 0, 20), fore_l=(40, 0, 0),
             blade=(-0.7, 0.62, 0.35), grip=0.0, sheath=1.0), "io"),                         # swung up and round
    (1.25, P(SHOULDER, sheath=1.0), "out3"),                                                   # onto the shoulder
])


def defeat(t):
    keys = DEFEAT_TRACK(t)
    return keys


# beaten: down on the right knee, the left forearm across the raised left knee, the right hand by the thigh still
# holding the blade - laid out low before him, its point just off the ground - the head hanging
DEFEAT_POSE = P(hips=(0.6, -6.2, 1.6), waist=(-14, 4, 4), chest=(-16, -4, 0), head=(-26, 6, 6),
                arm_r=(30, -6, -8), fore_r=(42, 0, 0), arm_l=(30, 0, -2), fore_l=(76, 0, 0),
                hand_r=(-5.6, 6.8, -3.4), reach_r=1.0, hand_l=(2.6, 8.4, -4.4), reach_l=1.0,
                pole_r=(-0.8, 0, 0.6), pole_l=(0.9, 0.2, 0.4),
                blade=(-0.42, -0.06, -0.9), edge=(0.2, -1, 0), grip=0.0,
                foot_r=(-3.0, 0, 4.6), foot_l=(3.0, 0, -3.8), knee_r=(-0.1, -1, -0.3), knee_l=(0.4, 0, -1))
DEFEAT_TRACK = Track([
    (0.0, GUARD),
    (0.1, P(GUARD, hips=(0.2, -1.2, 2.4), waist=(12, 6, 0), chest=(18, -10, 6), head=(24, 10, 0),
            arm_r=(60, 0, -40), fore_r=(20, 0, 0), arm_l=(30, 0, 50), fore_l=(30, 0, 0),
            blade=(-0.5, 0.75, 0.4), grip=0.0, foot_l=(3.2, 0, 5.4)), "out3"),                 # struck: thrown back
    (0.45, P(DEFEAT_POSE, hips=(0.6, -4.0, 1.6), head=(-6, 6, 0)), "in"),                      # the legs go
    (0.62, P(DEFEAT_POSE, hips=(0.6, -6.9, 1.6)), "out3"),                                     # down on the knee, hard
    (1.2, DEFEAT_POSE, "io"),
])


def defeat_breath(t):
    """Kneeling, beaten: slow heavy breaths."""
    b = wave(t, 2.4)
    return add(DEFEAT_POSE, hips=(0, -0.35 * b, 0), chest=(-3 * b, 0, 0), head=(-2 * b, 0, 0), arm_l=(2 * b, 0, 0))


# ------------------------------------------------------------------ 1. iai
IAI_READY = Track([
    (0.0, GUARD),
    (0.12, add(GUARD, hips=(0, 0.6, 0), blade=(0.25, 0.2, 0.0)), "io"),                       # a breath in
    (0.24, P(GUARD, hips=(0.4, -2.6, 1.0), waist=(-6, -16, 0), chest=(-12, -16, 4), arm_r=(30, -20, 30),
             fore_r=(40, 0, 0), blade=(0.85, 0.12, -0.5), edge=(0, 1, 0), grip=0.0, saya_l=1.0), "io"),   # point to the mouth
    (0.32, P(IAI_SET, sheath=1.0, saya_r=0.8, hips=(0.6, -3.4, 1.4)), "in"),                 # the blade home...
    (0.36, P(IAI_SET, sheath=0.0), "out"),                                                     # ...click
    (0.6, P(IAI_SET, hips=(0.6, -4.4, 1.7)), "outback"),
    (1.2, P(IAI_SET, hips=(0.6, -4.6, 1.8), chest=(-15, -6, 0)), "io"),
    (1.5, P(IAI_SET, hips=(0.6, -4.8, 1.9), chest=(-16, -7, 0)), "io"),                       # the coil, tighter
])
IAI_CUT_END = P(GUARD, hips=(0.4, -4.4, 0.4), waist=(-8, 26, 0), chest=(-10, 30, 0), head=(4, -36, 0),
                arm_r=(84, 64, 0), fore_r=(2, 0, 0), arm_l=(10, 0, 50), fore_l=(24, 0, 0),
                blade=(-0.94, 0.12, 0.32), edge=(0, 0, 1), grip=0.0, **LUNGE_FEET)
IAI_STRIKE = Track([
    (0.0, P(IAI_SET, hips=(0.6, -4.8, 1.9), chest=(-16, -7, 0))),
    (0.06, P(IAI_SET, hips=(0.5, -4.4, 1.2), waist=(-8, -10, 0), chest=(-12, 4, 0), arm_r=(50, -10, 20),
             sheath=1.0, saya_r=0.0, blade=(0.6, -0.15, -0.78), saya_l=1.0, grip=0.0, **LUNGE_FEET), "in3"),
    (0.12, P(IAI_CUT_END, waist=(-8, 6, 0), chest=(-10, 12, 0), arm_r=(88, 10, 0), blade=(0.0, 0.06, -1.0),
             saya_l=0.6), "lin"),                                                              # the cut: level, through
    (0.2, P(IAI_CUT_END, saya_l=0.0), "out3"),
    (0.42, add(IAI_CUT_END, blade=(0, 0.05, 0.08)), "io"),                                     # held: zanshin
    (0.52, P(IAI_CUT_END, arm_r=(60, 40, -20), fore_r=(40, 0, 0), blade=(-0.6, 0.6, 0.2)), "io"),   # the blade up...
    (0.6, P(IAI_CUT_END, arm_r=(40, 30, -30), fore_r=(10, 0, 0), blade=(-0.55, -0.35, -0.75)), "snap"),  # ...chiburi
    (0.9, P(GUARD), "io"),
])

# ------------------------------------------------------------------ 2. three steps
LOW_LEFT = P(GUARD, hips=(0.6, -3.8, 0.8), waist=(-8, -22, 0), chest=(-18, -26, 0), head=(8, 26, 0),
             arm_r=(36, -30, 40), fore_r=(8, 0, 0), arm_l=(30, 0, -14), fore_l=(36, 0, 0),
             blade=(0.62, -0.32, -0.72), edge=(0.4, -1, 0), **LUNGE_FEET)
LEVEL_RIGHT = P(GUARD, hips=(0.2, -3.4, 0.4), waist=(-6, 22, 0), chest=(-8, 30, 0), head=(4, -30, 0),
                arm_r=(86, 64, 0), fore_r=(0, 0, 0), arm_l=(30, 0, 40), fore_l=(30, 0, 0),
                blade=(-0.96, 0.08, -0.26), edge=(0, 0, 1), grip=0.0, **LUNGE_FEET)
LOW_RIGHT = P(LEVEL_RIGHT, hips=(0.2, -3.8, 0.6), chest=(-16, 22, 0), arm_r=(40, 40, -20), fore_r=(10, 0, 0),
              blade=(-0.6, -0.36, -0.7), edge=(-0.4, -1, 0))
HIGH_LEFT = P(GUARD, hips=(0.2, -2.6, 0.2), waist=(-4, -14, 0), chest=(4, -26, 0), head=(-4, 22, 0),
              arm_r=(150, -40, 20), fore_r=(20, 0, 0), arm_l=(150, 0, -10), fore_l=(30, 0, 0),
              blade=(0.5, 0.72, -0.48), edge=(0.5, 0.6, 0), grip=1.0, **LUNGE_FEET)
STEP1 = Track([
    (0.0, P(HASSO, **LUNGE_FEET, hips=(0.0, -2.6, 0.6))),
    (0.04, P(HASSO, **LUNGE_FEET, hips=(0.0, -2.8, 0.6), blade=(-0.2, 0.9, 0.4)), "io"),
    (0.14, LOW_LEFT, "snap"),
    (0.4, add(LOW_LEFT, hips=(0, 0.3, 0), blade=(-0.04, 0.06, 0)), "io"),
])
STEP2 = Track([
    (0.0, add(LOW_LEFT, blade=(0.15, 0.25, 0.2))),
    (0.14, LEVEL_RIGHT, "snap"),
    (0.4, add(LEVEL_RIGHT, blade=(0, -0.04, 0.06)), "io"),
])
STEP3 = Track([
    (0.0, LOW_RIGHT),
    (0.15, HIGH_LEFT, "snap"),
    (0.42, add(HIGH_LEFT, blade=(0.04, 0.04, 0.0)), "io"),                                    # zanshin
    (0.58, P(HIGH_LEFT, arm_r=(90, 20, -20), fore_r=(60, 0, 0), blade=(-0.4, 0.75, 0.5), edge=(0, 1, 0)), "io"),
    (0.66, P(LOW_RIGHT, blade=(-0.62, -0.32, -0.72), hips=(0.2, -3.2, 0.6)), "snap"),        # chiburi
    (1.2, GUARD, "io"),
])

# ------------------------------------------------------------------ 3. the thrust
THRUST_COIL = P(GUARD, hips=(0.6, -3.6, 2.2), waist=(-4, -22, 0), chest=(4, -18, 0), head=(-2, 32, 0),
                arm_r=(-14, -10, 14), fore_r=(96, 0, 0), arm_l=(70, 10, 10), fore_l=(14, 0, 0),
                blade=(0.04, 0.02, -1.0), edge=(0.2, -1, 0), grip=0.0,
                foot_r=(-2.8, 0, -5.0), foot_l=(3.4, 0, 4.4), knee_l=(0.6, -0.4, -1))
THRUST_OUT = P(GUARD, hips=(0.2, -4.6, -1.6), waist=(-20, 4, 0), chest=(-16, 6, 0), head=(30, -8, 0),
               arm_r=(100, 4, 6), fore_r=(0, 0, 0), arm_l=(-50, 0, 30), fore_l=(20, 0, 0),
               blade=(0.0, -0.06, -1.0), edge=(0.2, -1, 0), grip=0.0,
               foot_r=(-3.0, 1.0, -9.5), foot_l=(3.0, 0.6, 8.0), knee_r=(-0.2, 0.2, -1), knee_l=(0.3, -1, 0))


def thrust(t):
    p = THRUST_T(t)
    # the gathering trembles, just before it lets go
    tr = bump(t, 0.5, 0.9) * math.sin(t * 90)
    p = add(p, blade=(0.012 * tr, 0.012 * tr, 0))
    if 0.9 <= t <= 1.35:
        p["wind"] = (0, 0, 280)
    return p


THRUST_T = Track([
    (0.0, GUARD),
    (0.75, THRUST_COIL, "io"),
    (0.9, add(THRUST_COIL, hips=(0, -0.3, 0.4)), "io"),
    (0.96, THRUST_OUT, "out3"),
    (1.3, add(THRUST_OUT, hips=(0, 0.2, 0)), "io"),
    (1.42, P(THRUST_OUT, hips=(0.2, -4.0, -1.0), waist=(-10, 4, 0), head=(14, -8, 0), foot_r=(-3.0, 0, -8.0),
             foot_l=(3.0, 0, 6.5)), "out3"),                                                   # the brake: both feet down
    (1.9, GUARD, "io"),
])

# ------------------------------------------------------------------ 4. the whirl
WHIRL_SET = P(GUARD, hips=(0.4, -5.0, 1.2), waist=(-14, 30, 0), chest=(-10, 30, 0), head=(10, -50, 0),
              arm_r=(40, 80, -10), fore_r=(10, 0, 0), arm_l=(20, 0, 60), fore_l=(30, 0, 0),
              blade=(-0.6, 0.05, 0.8), edge=(0, 0, 1), grip=0.0,
              foot_r=(-3.4, 0, -3.6), foot_l=(3.4, 0, 3.8), knee_r=(-0.5, 0, -1), knee_l=(0.5, 0, -1))
WHIRL_AIR = P(GUARD, hips=(0, 1.0, 0), waist=(0, 0, 0), chest=(0, 0, 0), head=(0, 0, 0),
              arm_r=(10, 0, -84), fore_r=(0, 0, 0), arm_l=(50, 0, 30), fore_l=(100, 0, 0),
              blade=(-1.0, 0.04, -0.1), edge=(0, 0, -1), grip=0.0, plant_r=0.0, plant_l=0.0,
              leg_r=(60, 0, -6), shin_r=(-96, 0, 0), leg_l=(34, 0, 8), shin_l=(-80, 0, 0))
WHIRL_LAND = P(GUARD, hips=(0.2, -4.4, 0.8), waist=(-14, -10, 0), chest=(-18, -18, 0), head=(14, 20, 0),
               arm_r=(50, -40, 30), fore_r=(6, 0, 0), arm_l=(10, 0, 60), fore_l=(20, 0, 0),
               blade=(0.7, 0.05, -0.7), edge=(0.6, 0, 0.3), grip=0.0,
               foot_r=(-4.0, 0, -4.4), foot_l=(4.0, 0, 4.6), knee_r=(-0.6, 0, -1), knee_l=(0.6, 0, -1))
WHIRL_T = Track([
    (0.0, GUARD),
    (0.38, WHIRL_SET, "io"),
    (0.45, P(WHIRL_SET, hips=(0.2, -1.0, 0.4), leg_r=(10, 0, 0), leg_l=(-6, 0, 0)), "in"),     # the spring up
    (0.58, WHIRL_AIR, "out"),
    (1.05, add(WHIRL_AIR, leg_r=(-30, 0, 0), shin_r=(50, 0, 0), leg_l=(-20, 0, 0), shin_l=(40, 0, 0),
               blade=(0, -0.1, 0)), "io"),                                                     # legs reach for the floor
    (1.15, WHIRL_LAND, "out3"),
    (1.3, add(WHIRL_LAND, hips=(0, 0.6, 0)), "io"),
    (1.6, GUARD, "io"),
])


def whirl(t):
    p = WHIRL_T(t)
    # two turns in the air, fast in the middle; the arms and blade trail the turn, the head spots ahead
    s = max(0.0, min(1.0, (t - 0.47) / (1.13 - 0.47)))
    turn = 720 * (s * s * (3 - 2 * s))
    rate = 720 * 6 * s * (1 - s) / 0.66                     # deg/s
    p["root"] = (0, turn, 0)
    lag = -0.012 * rate
    p = add(p, chest=(0, lag, 0), head=(0, -lag * 1.4, 0))
    if 0.45 < t < 1.15:
        p["wind"] = (0, -120 * (0.8 - (t - 0.45) / 0.7 * 1.6), 0)
    return p


# ------------------------------------------------------------------ 5. the blades
CALL = P(GUARD, hips=(0.2, -0.6, 0.4), waist=(5, -6, 0), chest=(10, -10, -4), head=(24, 6, 0),
         arm_r=(16, 0, 26), fore_r=(24, 0, 0), arm_l=(174, 0, 14), fore_l=(6, 0, 0),
         blade=(-0.45, -0.25, -0.86), edge=(0, -1, 0), grip=0.0,
         foot_r=(-3.0, 0, -2.8), foot_l=(3.2, 0, 3.0))
POINT = P(CALL, hips=(0.2, -2.0, 0.2), waist=(-6, -22, 0), chest=(-6, -22, 0), head=(6, 26, 0),
          arm_l=(94, 0, -8), fore_l=(0, 0, 0), foot_r=(-3.2, 0, 3.4), foot_l=(3.4, 0, -3.6))
SUMMON_KEYS = [(0.0, GUARD), (0.42, CALL, "outback"), (0.5, add(CALL, chest=(-3, 0, 0), head=(-4, 0, 0)), "io")]
for _at in (1.0, 1.5, 2.0):
    SUMMON_KEYS += [(_at - 0.14, P(POINT, arm_l=(150, 0, 0), fore_l=(30, 0, 0), chest=(6, -14, 0)), "io"),
                    (_at, P(POINT), "snap")]
SUMMON_KEYS += [(2.35, add(POINT, arm_l=(-6, 0, 0)), "io"), (2.8, GUARD, "io")]
SUMMON_T = Track(SUMMON_KEYS)


def summon(t):
    p = SUMMON_T(t)
    # the raised hand trembles with the weight of what it holds
    tr = (bump(t, 0.45, 0.9) + 0.5 * bump(t, 0.9, 2.1)) * math.sin(t * 70)
    return add(p, arm_l=(1.6 * tr, 0, 1.2 * tr), chest=(0.5 * tr, 0, 0))


# ------------------------------------------------------------------ 6. the combo
JODAN_HIGH = P(JODAN, hips=(0.2, -0.4, 0.8), waist=(4, -8, 0), chest=(8, 12, 0), head=(-4, -6, 0),
               arm_r=(170, 0, 12), arm_l=(170, 0, -24), blade=(0.0, 0.3, 0.95))
DOWN_CUT = P(GUARD, hips=(0.2, -5.0, -0.2), waist=(-18, -8, 0), chest=(-14, 12, 0), head=(18, -6, 0),
             arm_r=(52, 0, 12), fore_r=(6, 0, 0), arm_l=(54, 0, -24), fore_l=(10, 0, 0),
             blade=(0.0, -0.28, -0.96), edge=(0, -1, 0), **LUNGE_FEET)
COMBO = Track([
    (0.0, GUARD),
    (0.22, P(JODAN, **LUNGE_FEET, hips=(0.2, -1.6, 0.8)), "io"),
    (0.35, LOW_LEFT, "snap"),                                                          # kesa
    (0.5, add(LOW_LEFT, blade=(-0.05, 0.08, 0)), "io"),
    (0.75, P(HIGH_LEFT, arm_r=(140, 50, -10), blade=(-0.45, 0.75, -0.5), head=(-4, -20, 0), chest=(4, 24, 0),
             waist=(-4, 14, 0)), "snap"),                                              # the rising cut back across
    (0.95, P(JODAN_HIGH, **LUNGE_FEET, hips=(0.2, -2.2, 1.2)), "io"),                 # up, on his toes...
    (1.2, DOWN_CUT, "in3"),                                                            # ...and down
    (1.42, add(DOWN_CUT, hips=(0, -0.3, 0), blade=(0, 0.04, 0)), "out"),
    (1.7, GUARD, "io"),
])

# ------------------------------------------------------------------ 7. from the sky
HANG = P(JODAN_HIGH, hips=(0, 0.5, 0), waist=(-10, 0, 0), chest=(-8, 0, 0), head=(-30, 0, 0),
         plant_r=0.0, plant_l=0.0, leg_r=(50, 0, -6), shin_r=(-100, 0, 0), leg_l=(16, 0, 8), shin_l=(-70, 0, 0),
         blade=(0.0, 0.5, 0.86))
DIVE_LAND = P(DOWN_CUT, hips=(0.4, -5.3, 0.4), waist=(-26, 0, 0), chest=(-20, 0, 0), head=(22, 0, 0),
              foot_r=(-3.0, 0, -5.2), foot_l=(3.2, 0, 5.4), knee_l=(0.3, -1, -0.2),
              blade=(0.0, -0.18, -0.98))
DROP_T = Track([
    (0.0, HANG),
    (0.8, add(HANG, hips=(0, 0.6, 0), arm_r=(6, 0, 0), arm_l=(6, 0, 0), blade=(0, -0.1, 0.1)), "io"),
    (0.9, add(HANG, leg_r=(-30, 0, 0), shin_r=(60, 0, 0), leg_l=(-10, 0, 0), shin_l=(40, 0, 0)), "io"),
    (1.06, P(DIVE_LAND, plant_r=0.0, plant_l=0.0, leg_r=(20, 0, -4), shin_r=(-10, 0, 0), leg_l=(-10, 0, 4),
             shin_l=(-10, 0, 0), hips=(0.4, -2.0, 0.4)), "in"),
    (1.12, DIVE_LAND, "out3"),
    (1.5, add(DIVE_LAND, hips=(0, 0.4, 0), head=(-6, 0, 0)), "io"),
    (1.9, GUARD, "io"),
])


def drop(t):
    p = DROP_T(t)
    if t < 0.9:
        p = add(p, hips=(0, 0.5 * wave(t, 0.9), 0))
        p["wind"] = (0, 30, 0)
    elif t < 1.1:
        p["wind"] = (0, 500, 0)
    return p


# ------------------------------------------------------------------ thrown back by a parry (2 s: the stun)
STAGGER_T = Track([
    (0.0, GUARD),
    (0.1, P(GUARD, hips=(-0.4, -1.2, 2.8), waist=(16, 10, -6), chest=(22, -16, 8), head=(30, 14, 8),
            arm_r=(70, 0, -70), fore_r=(20, 0, 0), arm_l=(40, 0, 70), fore_l=(30, 0, 0),
            blade=(-0.6, 0.75, 0.3), edge=(0, 1, 0), grip=0.0, foot_l=(3.4, 1.6, 6.0)), "out3"),
    (0.3, P(GUARD, hips=(0.2, -2.8, 2.6), waist=(6, 10, -4), chest=(10, -14, 6), head=(10, 14, 4),
            arm_r=(50, 0, -50), fore_r=(30, 0, 0), arm_l=(30, 0, 50), fore_l=(40, 0, 0),
            blade=(-0.6, 0.5, -0.6), grip=0.0, foot_l=(3.4, 0, 7.4), foot_r=(-2.8, 1.2, -1.0)), "io"),
    (0.5, P(GUARD, hips=(0.2, -3.2, 1.6), waist=(-10, 0, 0), chest=(-18, 4, 0), head=(-14, 0, 0),
            arm_r=(30, 0, -10), fore_r=(30, 0, 0), arm_l=(20, 0, 20), fore_l=(50, 0, 0),
            blade=(-0.3, -0.25, -0.92), grip=0.0, foot_l=(3.4, 0, 7.4), foot_r=(-2.8, 0, 2.0)), "io"),   # bent over
    (1.3, P(GUARD, hips=(0.2, -3.0, 1.6), waist=(-8, 0, 0), chest=(-12, 4, 0), head=(-8, 0, 0),
            arm_r=(32, 0, -10), fore_r=(30, 0, 0), arm_l=(30, 0, 10), fore_l=(70, 0, 0),
            blade=(-0.3, -0.22, -0.92), grip=0.0, foot_l=(3.4, 0, 7.4), foot_r=(-2.8, 0, 2.0)), "io"),
    (1.6, P(GUARD, foot_l=(3.2, 0, 4.4)), "io"),
    (2.0, GUARD, "io"),
])


def stagger(t):
    p = STAGGER_T(t)
    # shaking the daze off
    sh = bump(t, 0.6, 1.3) * math.sin((t - 0.6) * 2 * math.pi * 3.2)
    return add(p, head=(0, 10 * sh, 4 * sh))


# ------------------------------------------------------------------ the list: name -> (loop, length, fn, snap)
WALK_SPEED = 18.0   # px/s the walk is made for (RenegadeEntity scales it to his real speed)


def moves():
    return {
        "idle": (True, 4.0, idle, 0.8),
        "walk": (True, 0.6, mover(0.6, (0, -1), 10.0, ("r", "l"), WALK_SPEED), 1.0),
        "walk_back": (True, 0.6, mover(0.6, (0, 1), 10.0, ("l", "r"), WALK_SPEED), 1.0),
        "strafe_r": (True, 0.6, mover(0.6, (-1, 0), 9.0, ("r", "l"), WALK_SPEED), 1.0),
        "strafe_l": (True, 0.6, mover(0.6, (1, 0), 9.0, ("l", "r"), WALK_SPEED), 1.0),
        "talk": (True, 2.5, talk, 0.9),
        "appear": (False, 1.25, APPEAR, 1.1),
        "iai_ready": ("hold_on_last_frame", 1.5, IAI_READY, 1.2),
        "iai_strike": (False, 0.9, IAI_STRIKE, 1.7),
        "step_cut1": (False, 0.4, STEP1, 1.7),
        "step_cut2": (False, 0.4, STEP2, 1.7),
        "step_cut3": (False, 1.2, STEP3, 1.7),
        "thrust": ("hold_on_last_frame", 1.9, thrust, 1.5),
        "whirl": (False, 1.6, whirl, 1.4),
        "summon": (False, 2.8, summon, 1.3),
        "combo": (False, 1.7, COMBO, 1.6),
        "drop": (False, 1.9, drop, 1.5),
        "stagger": (False, 2.0, stagger, 1.3),
        "defeat": ("hold_on_last_frame", 1.2, defeat, 1.2),
        "defeat_breath": (True, 2.4, defeat_breath, 0.8),
    }
