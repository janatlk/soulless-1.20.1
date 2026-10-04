"""Procedural pixel-art item icons and synthesized sound effects for the Soulless mod.

Stand-ins until fal assets are generated (see MODLOG). Run:
    uv run --no-project --with pillow --with numpy tools/gen_icons_sounds.py
Needs ffmpeg on PATH for the .ogg encode.
"""
import math
import random
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/soulless"
ITEM_TEX = ASSETS / "textures/item"


def c(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


OUTLINE = c("120a18")
BLADE = [c("241a33"), c("31244a"), c("40305e")]
EDGE = [c("8d84b8"), c("b3abd8"), c("e4dfff")]
RUNE = [c("9a2cf0"), c("d17cff"), c("fbe9ff")]
BONE = [c("8a7c62"), c("b9ab8c"), c("ddd2b6")]
METAL = [c("1f1a26"), c("332b3d"), c("4c4259")]
LEATHER = [c("2a1623"), c("3e2233"), c("55304a")]
GEM = [c("4a0a7a"), c("8a22d6"), c("c46cff"), c("f3d6ff")]
FLAME = [c("5a12a0", 230), c("9a2cf0"), c("d17cff"), c("f6dcff")]


def outline(img, skip=()):
    h, w = len(img), len(img[0])
    out = [row[:] for row in img]
    for y in range(h):
        for x in range(w):
            if img[y][x] is not None:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and img[ny][nx] is not None and img[ny][nx] not in skip:
                    out[y][x] = OUTLINE
                    break
    return out


def save(img, name, scale=1):
    h, w = len(img), len(img[0])
    arr = np.zeros((h, w, 4), np.uint8)
    for y in range(h):
        for x in range(w):
            if img[y][x] is not None:
                arr[y, x] = img[y][x]
    p = ITEM_TEX / f"{name}.png"
    p.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(arr, "RGBA").save(p)


def grid(n):
    return [[None] * n for _ in range(n)]


# ----------------------------------------------------------------------------- sword icon (32x32)
def diagonal_item(n, length, profile, flames=False, seed=1):
    """Rasterise an item lying on the bottom-left -> top-right diagonal from a 1D profile.

    profile(s, v) -> colour or None, where s is distance from the pommel end along the axis
    and v the signed distance across it (in pixels)."""
    rnd = random.Random(seed)
    img = grid(n)
    flame_px = set()
    ax = (1 / math.sqrt(2), -1 / math.sqrt(2))  # +x, -y on screen
    start = (n / 2 - ax[0] * length / 2, n / 2 - ax[1] * length / 2)
    for y in range(n):
        for x in range(n):
            px, py = x + 0.5 - start[0], y + 0.5 - start[1]
            s = px * ax[0] + py * ax[1]
            v = px * ax[1] - py * ax[0]
            col = profile(s, v, rnd)
            if col is not None:
                img[y][x] = col
                if col in FLAME:
                    flame_px.add((x, y))
    return img, flame_px


# The sword's own icon is rendered from the 3D model by tools/gen_model.py.


# ----------------------------------------------------------------------------- material icons (16x16)
def make_hilt():
    def prof(s, v, rnd):
        av = abs(v)
        if s < 0 or s > 17:
            return None
        if s < 2.4:
            return (GEM[3] if v < 0 else GEM[1]) if av < 1.5 else None
        if s < 3.2:
            return METAL[2] if av < 1.2 else None
        if s < 9:
            return (LEATHER[2 - int(s * 1.5) % 3] if v < 0.3 else LEATHER[0]) if av < 0.9 else None
        if s < 12:
            if av < 1.9:
                if 10.2 < s < 11.2 and 0.3 < av < 1.2:
                    return RUNE[2]
                return BONE[2] if v < 0 else BONE[1]
            if av < 1.9 + (s - 9) * 0.9 and 9.5 < s < 11.6:
                return BONE[1]
            return None
        # broken blade stub with a jagged end
        jag = 14.2 + (1.2 if v > 0.5 else 0.3 if v > -0.5 else 0.8)
        if s < jag and av < 1.6:
            return EDGE[1] if av > 0.9 else BLADE[1]
        return None
    img, _ = diagonal_item(16, 17, prof, seed=2)
    save(outline(img), "ancient_hilt")


def make_ingot():
    rows = [
        "................",
        "................",
        "................",
        "......hhhhhh....",
        "....hhlllllhh...",
        "..hhlllllllllh..",
        ".hllllllllllmmd.",
        ".dmmllllllmmmdd.",
        ".ddmmmmmmmmmddd.",
        ".dddddmmmmmddd..",
        "..ddddddddddd...",
        "....dddddd......",
        "................",
        "................",
        "................",
        "................",
    ]
    pal = {"h": c("e6dfff"), "l": c("b0a6d6"), "m": c("7d72a8"), "d": c("4d4473")}
    rnd = random.Random(5)
    img = grid(16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                col = pal[ch]
                if ch in "lm" and rnd.random() < 0.18:
                    col = c("9a2cf0")  # wraith streaks
                img[y][x] = col
    img = outline(img)
    # wisps of soul mist rising
    for (x, y) in ((5, 1), (6, 0), (9, 2), (10, 1), (12, 3)):
        img[y][x] = c("d17cff", 170)
    save(img, "wraithsteel_ingot")


def make_core():
    img = grid(16)
    cx, cy, r = 7.5, 8.0, 5.2
    rnd = random.Random(9)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx - 0.5, y + 0.5 - cy - 0.5)
            if d < r:
                heat = 1 - d / r + (rnd.random() - 0.5) * 0.25
                img[y][x] = FLAME[max(0, min(3, int(heat * 4.2)))]
    # tongues of flame on top
    for (x, y) in ((6, 2), (8, 1), (9, 2), (7, 3)):
        img[y][x] = FLAME[1]
    img = outline(img, skip=())
    # black iron cage bands
    for y in range(2, 15):
        for x in (4, 11):
            if img[y][x] not in (None, OUTLINE):
                img[y][x] = METAL[1]
    for x in range(3, 13):
        if img[8][x] not in (None, OUTLINE):
            img[8][x] = METAL[2]
    for x in range(5, 11):
        img[14][x] = METAL[1]
        img[2][x] = img[2][x] or None
    save(img, "soulflame_core")


def make_heart():
    rows = [
        "................",
        "................",
        "...aaa....aaa...",
        "..abbba..abbba..",
        ".abhbbbaabbbbba.",
        ".abhbbbbbbbbbba.",
        ".abbbbbbbbbbbba.",
        ".abbbbbbbbbbbba.",
        "..abbbbbbbbbba..",
        "...abbbbbbbba...",
        "....abbbbbba....",
        ".....abbbba.....",
        "......abba......",
        ".......aa.......",
        "................",
        "................",
    ]
    pal = {"a": c("2a0a40"), "b": c("4e1478"), "h": c("b27ad9")}
    img = grid(16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img[y][x] = pal[ch]
    # glowing cracks
    crack = [(7, 4), (7, 5), (8, 6), (8, 7), (7, 8), (9, 8), (10, 9), (6, 9), (5, 7), (10, 5), (11, 6)]
    for i, (x, y) in enumerate(crack):
        img[y][x] = RUNE[2] if i % 3 == 0 else RUNE[1]
    for (x, y) in ((4, 6), (3, 7), (12, 8)):
        img[y][x] = c("1a0526")  # black veins
    save(outline(img), "abyssal_heart")


# ----------------------------------------------------------------------------- sounds
SR = 44100


def lowpass(x, cutoff):
    """One-pole low-pass; cutoff may be an array (time-varying)."""
    cutoff = np.broadcast_to(np.asarray(cutoff, np.float64), x.shape)
    a = 1 - np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a[i] * (x[i] - acc)
        y[i] = acc
    return y


def env(n, attack, release_curve=3.0):
    t = np.linspace(0, 1, n)
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * (1 - t) ** release_curve


def write_ogg(sig, name):
    sig = sig / (np.max(np.abs(sig)) + 1e-9) * 0.92
    pcm = (sig * 32767).astype(np.int16)
    out = ASSETS / f"sounds/{name}.ogg"
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as d:
        wav = Path(d) / f"{name}.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5", str(out)], check=True)


def slam_windup(rng):
    n = int(SR * 0.5)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    sweep = lowpass(noise, np.linspace(300, 4200, n)) * np.linspace(0.2, 1, n) ** 2
    freq = np.linspace(70, 260, n)
    tone = np.sin(2 * np.pi * np.cumsum(freq) / SR) * 0.5 + np.sin(2 * np.pi * np.cumsum(freq * 1.5) / SR) * 0.25
    e = np.clip(t / 0.4, 0, 1) ** 1.5 * np.clip((0.5 - t) / 0.06, 0, 1)
    return (sweep * 1.4 + tone) * e


def slam_impact(rng):
    n = int(SR * 1.6)
    t = np.arange(n) / SR
    boom = np.sin(2 * np.pi * (38 + 40 * np.exp(-t * 10)) * t) * np.exp(-t * 2.6)
    thud = lowpass(rng.standard_normal(n), 900) * np.exp(-t * 9) * 3.5
    crumble = lowpass(rng.standard_normal(n), 2500) * np.exp(-t * 3.2) * 0.8
    crackle = np.zeros(n)
    for _ in range(120):
        i = int(rng.uniform(0.02, 1.2) * SR)
        crackle[i:i + 40] += rng.uniform(-1, 1) * np.exp(-np.arange(min(40, n - i)) / 6)
    shimmer = (np.sin(2 * np.pi * 520 * t) + np.sin(2 * np.pi * 783 * t) * 0.6) * np.exp(-t * 2.2) * 0.12 * np.clip(t / 0.08, 0, 1)
    return boom * 1.4 + thud + crumble + crackle * 0.5 + shimmer


def curse_ignite(rng):
    n = int(SR * 0.45)
    t = np.arange(n) / SR
    whoosh = lowpass(rng.standard_normal(n), 400 + 3000 * np.exp(-t * 8)) * env(n, 0.04, 2.0) * 2
    low = np.sin(2 * np.pi * (110 - 50 * t) * t) * env(n, 0.02, 3.0) * 0.6
    crackle = np.zeros(n)
    for _ in range(25):
        i = int(rng.uniform(0.02, 0.4) * SR)
        crackle[i:i + 25] += rng.uniform(-1, 1) * np.exp(-np.arange(min(25, n - i)) / 4)
    return whoosh + low + crackle * 0.35


def reaper_scream(rng):
    """A wailing, many-voiced scream: detuned falling sawtooth voices through a vocal-ish band, plus breath."""
    n = int(SR * 2.6)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for k, base in enumerate((180, 233, 267, 311, 392)):
        f = base * (1.0 - 0.35 * t / t[-1]) * (1 + 0.012 * np.sin(2 * np.pi * (5 + k) * t))
        ph = np.cumsum(f) / SR
        saw = 2 * (ph % 1) - 1
        out += saw * (0.7 + 0.3 * np.sin(2 * np.pi * 0.7 * t + k))
    voice = lowpass(out, 1800 + 900 * np.sin(2 * np.pi * 1.3 * t))
    breath = lowpass(rng.standard_normal(n), 3000) * 0.4
    e = np.clip(t / 0.25, 0, 1) * np.clip((t[-1] - t) / 0.8, 0, 1)
    return (voice * 0.6 + breath) * e


def bolt_fly(rng):
    """An eerie, unplaceable whoosh: a rising-and-falling filtered noise with a ghostly ring modulator."""
    n = int(SR * 1.2)
    t = np.arange(n) / SR
    sweep = 500 + 2500 * np.sin(np.pi * t / t[-1]) ** 2
    whoosh = lowpass(rng.standard_normal(n), sweep) * 2.5
    ring = np.sin(2 * np.pi * 330 * t) * np.sin(2 * np.pi * 47 * t) * 0.35
    whistle = np.sin(2 * np.pi * np.cumsum(900 + 400 * np.sin(2 * np.pi * 3 * t)) / SR) * 0.12
    return (whoosh + ring + whistle) * np.sin(np.pi * t / t[-1])


def eruption(rng):
    """Burning roar of the eruption: a low rumble swelling into a crackling flare."""
    n = int(SR * 1.8)
    t = np.arange(n) / SR
    swell = np.clip(t / 1.1, 0, 1) ** 2
    rumble = lowpass(rng.standard_normal(n), 250) * 4 * swell
    roar = lowpass(rng.standard_normal(n), 400 + 2600 * swell) * 1.8 * swell
    crackle = np.zeros(n)
    for _ in range(160):
        i = int(rng.uniform(0.3, 1.7) * SR)
        crackle[i:i + 30] += rng.uniform(-1, 1) * np.exp(-np.arange(min(30, n - i)) / 5)
    tail = np.clip((t[-1] - t) / 0.5, 0, 1)
    return (rumble + roar + crackle * 0.6) * tail


def make_stun_icon():
    """18x18 effect icon: three golden sparks circling a dazed swirl."""
    img = grid(18)
    gold, pale = c("e8d25a"), c("fff4b0")
    for k in range(40):  # swirl
        a = k * 0.45
        r = 1.5 + k * 0.13
        x, y = int(9 + math.cos(a) * r), int(9 + math.sin(a) * r * 0.7)
        if 0 <= x < 18 and 0 <= y < 18:
            img[y][x] = c("b8a040")
    for a in (0.3, 2.4, 4.5):  # stars
        sx, sy = int(9 + math.cos(a) * 6.5), int(9 + math.sin(a) * 4.5)
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if 0 <= sx + dx < 18 and 0 <= sy + dy < 18:
                img[sy + dy][sx + dx] = pale if (dx, dy) == (0, 0) else gold
    arr = outline(img)
    out = np.zeros((18, 18, 4), np.uint8)
    for y in range(18):
        for x in range(18):
            if arr[y][x] is not None:
                out[y, x] = arr[y][x]
    p = ASSETS / "textures/mob_effect/stun.png"
    p.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out, "RGBA").save(p)


def main():
    make_stun_icon()
    make_heart()
    rng = np.random.default_rng(11)
    write_ogg(slam_windup(rng), "slam_windup")
    write_ogg(slam_impact(rng), "slam_impact")
    write_ogg(curse_ignite(rng), "curse_ignite")
    write_ogg(reaper_scream(rng), "reaper_scream")
    write_ogg(bolt_fly(rng), "bolt_fly")
    write_ogg(eruption(rng), "eruption")
    print("icons + sounds written")


if __name__ == "__main__":
    main()
