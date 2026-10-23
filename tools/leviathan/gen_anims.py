"""Writes the Leviathan's keyframe animations (GeckoLib / Bedrock JSON) straight from code, without Blockbench.

  idle  - 6 s: an uneven breath (a long slow in-breath, a held moment, a quick out-breath, a small second sigh),
          the jaw tasting the water, the head never quite still, the tongue lolling
  roar  - 2.6 s: a wind-up (head and chest drawn back, crown flattened), the roar with the skull shaking and the
          whole front of the body heaving, then a growl that dies away
  bite  - 1.0 s: a cocked head, a lunge with the jaw flung open, a hard snap and a recoil

The neck's rearing and the crown's flare in the roar are procedural too (bones the idle does not key would not be
reset each frame). The procedural motion (swimming wave, crown, whiskers, eyes) lives in client/LeviathanModel.java and adds on top.

    python tools/leviathan/gen_anims.py
"""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/soulless/animations/entity/leviathan.animation.json"


def track(keys):
    return {f"{t:.2f}".rstrip("0").rstrip(".") if t else "0.0": {"post": {"vector": list(v)}, "lerp_mode": "catmullrom"}
            for t, v in keys}


def anim(length, loop, bones):
    out = {"loop": loop, "animation_length": length, "bones": {}}
    for (bone, channel), keys in bones.items():
        out["bones"].setdefault(bone, {})[channel] = track(keys)
    return out


def idle():
    L = 6.0
    # (time, chest swell, jaw, head x, head y, head z, tongue x)
    keys = [(0.0, 1.00, 6, 0, 0, 0, 0), (0.7, 1.03, -2, -2, 3, 1, 4), (1.6, 1.08, -12, -5, 5, 2, 10),
            (2.3, 1.11, -17, -6, 3, 0, 14), (2.7, 1.11, -15, -6, 1, -1, 12),       # held at the top
            (3.2, 1.03, 4, 2, -3, -2, -2),                                          # the quick out-breath, mouth shut
            (3.9, 1.05, -6, 0, -5, -1, 5), (4.4, 1.02, 2, 1, -2, 1, 0),            # a small second sigh
            (5.2, 1.01, 8, 1, 2, 1, -3), (L, 1.00, 6, 0, 0, 0, 0)]
    b = {}
    for bone, k in (("body_0", 1), ("body_1", 0.7), ("body_2", 0.4), ("body_3", 0.15)):
        b[(bone, "scale")] = [(t, (1 + (s - 1) * k, 1 + (s - 1) * k, 1)) for t, s, *_ in keys]
    b[("jaw", "rotation")] = [(t, (j, 0, 0)) for t, _, j, *_ in keys]
    b[("head", "rotation")] = [(t, (hx, hy, hz)) for t, _, _, hx, hy, hz, _ in keys]
    b[("tongue", "rotation")] = [(k[0], (k[6], 0, 0)) for k in keys]
    return anim(L, True, b)


def roar():
    L = 2.6
    jaw = [(0, 0), (0.4, 10), (0.65, -52), (0.9, -58), (1.5, -55), (1.9, -40), (2.2, -14), (L, 0)]
    head = [(0, 0, 0, 0), (0.4, 14, 0, 0), (0.65, -18, 0, 0)]
    t, s = 0.75, 1
    while t < 1.9:                                    # the shake, dying down
        a = 5 * (1.9 - t) / 1.15 + 1
        head.append((t, -16, a * s, a * 0.8 * s))
        t += 0.1
        s = -s
    head += [(2.0, -8, 0, 0), (L, 0, 0, 0)]
    b = {("jaw", "rotation"): [(t, (j, 0, 0)) for t, j in jaw],
         ("head", "rotation"): [(t, (x, y, z)) for t, x, y, z in head],
         ("tongue", "rotation"): [(0, (0, 0, 0)), (0.65, (-20, 0, 0)), (1.0, (-8, 0, 0)), (1.4, (-24, 0, 0)), (2.0, (0, 0, 0)), (L, (0, 0, 0))]}
    swell = [(0, 1), (0.4, 1.14), (0.65, 1.05), (1.0, 1.1), (1.5, 1.07), (2.0, 1.02), (L, 1)]
    for i, (bone, k) in enumerate((("body_0", 1), ("body_1", 0.8), ("body_2", 0.55), ("body_3", 0.3))):
        b[(bone, "scale")] = [(t, (1 + (v - 1) * k, 1 + (v - 1) * k, 1)) for t, v in swell]
    return anim(L, False, b)


def bite():
    L = 1.0
    b = {("jaw", "rotation"): [(0, (0, 0, 0)), (0.2, (6, 0, 0)), (0.38, (-50, 0, 0)), (0.48, (12, 0, 0)), (0.6, (4, 0, 0)), (L, (0, 0, 0))],
         ("head", "rotation"): [(0, (0, 0, 0)), (0.2, (10, 0, -6)), (0.38, (-12, 0, 4)), (0.48, (8, 0, 0)), (0.7, (-2, 0, 0)), (L, (0, 0, 0))]}
    return anim(L, False, b)


def main():
    data = {"format_version": "1.8.0", "animations": {
        "animation.leviathan.idle": idle(),
        "animation.leviathan.roar": roar(),
        "animation.leviathan.bite": bite()}, "geckolib_format_version": 2}
    OUT.write_text(json.dumps(data, indent=1))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
