"""Synthesized katana sounds: blade whooshes (three variants), the thrust's sharper whistle, the metallic
"shing" of a hit, and the gust of the lunge's dash. Kept soft; the game plays them quietly on top.

    uv run --no-project --with numpy tools/gen_katana_sounds.py        (needs ffmpeg on PATH)
"""
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
SOUNDS = ROOT / "src/main/resources/assets/soulless/sounds"
SR = 44100


def bandpass(x, centre, q=4.0):
    """A resonant two-pole band-pass whose centre frequency may change over time (array)."""
    centre = np.broadcast_to(np.asarray(centre, np.float64), x.shape)
    y = np.zeros_like(x)
    y1 = y2 = 0.0
    for i in range(len(x)):
        w = 2 * np.pi * centre[i] / SR
        r = 1 - w / (2 * q) if w < 2 * q else 0.0
        r = min(max(r, 0.0), 0.9995)
        a1, a2 = 2 * r * np.cos(w), -r * r
        v = (1 - r) * x[i] + a1 * y1 + a2 * y2
        y2, y1 = y1, v
        y[i] = v
    return y


def lowpass(x, cutoff):
    cutoff = np.broadcast_to(np.asarray(cutoff, np.float64), x.shape)
    a = 1 - np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a[i] * (x[i] - acc)
        y[i] = acc
    return y


def write_ogg(sig, name, peak=0.85):
    sig = sig / (np.max(np.abs(sig)) + 1e-9) * peak
    fade = min(len(sig), int(SR * 0.01))
    sig[-fade:] *= np.linspace(1, 0, fade)
    pcm = (sig * 32767).astype(np.int16)
    SOUNDS.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as d:
        wav = Path(d) / f"{name}.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5",
                        str(SOUNDS / f"{name}.ogg")], check=True)


def swing(rng, length=0.30, lo=500, hi=2600, end=900):
    """A blade cutting air: band-passed noise whose pitch rises to the fastest point of the arc and falls away
    (like a Doppler pass), swelling and dying quickly, with a thin airy hiss on top."""
    n = int(SR * length)
    t = np.linspace(0, 1, n)
    peak = 0.55
    shape = np.where(t < peak, (t / peak) ** 1.6, 1 - (t - peak) / (1 - peak))
    centre = np.where(t < peak, lo + (hi - lo) * (t / peak) ** 1.3, hi + (end - hi) * ((t - peak) / (1 - peak)))
    body = bandpass(rng.standard_normal(n), centre, q=3.0)
    h = rng.standard_normal(n)
    hiss = lowpass(h, 6000) - lowpass(h, 2500)                     # a thin band of air, not broadband noise
    e = np.clip(shape, 0, 1) ** 1.5
    out = (body / (np.max(np.abs(body)) + 1e-9) + hiss * 0.05) * e
    return lowpass(lowpass(out, 4500), 4500)                       # soft: no harsh top


def thrust(rng):
    """The thrust: a short, sharp, rising whistle that cuts off - the blade driven straight through the air."""
    s = swing(rng, length=0.22, lo=900, hi=3600, end=2600)
    n = len(s)
    t = np.arange(n) / SR
    ring = sum(np.sin(2 * np.pi * f * t) * a for f, a in ((2950, 0.5), (4410, 0.3))) * np.exp(-t * 18) * np.clip((t - 0.1) / 0.02, 0, 1)
    return s + ring * 0.25


def hit(rng):
    """A clean slice: a bright inharmonic metallic "shing" that rings briefly, over a soft dull thud."""
    n = int(SR * 0.55)
    t = np.arange(n) / SR
    partials = ((2180, 1.0, 9), (3410, 0.7, 11), (5230, 0.45, 14), (7720, 0.3, 18), (1460, 0.35, 7))
    ring = sum(a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * np.exp(-t * d) for f, a, d in partials)
    attack = np.clip(t / 0.003, 0, 1)
    scrape = bandpass(rng.standard_normal(n), np.linspace(5200, 2400, n), q=2.5) * np.exp(-t * 30) * 2.0
    thud = lowpass(rng.standard_normal(n), 350) * np.exp(-t * 35) * 6
    return (ring * attack * 0.6 + scrape + thud)


def dash(rng):
    """The lunge: a short gust of wind sweeping past with a soft scuff of the front foot at the start."""
    n = int(SR * 0.5)
    t = np.linspace(0, 1, n)
    centre = 300 + 1500 * np.sin(np.pi * np.clip(t * 1.2, 0, 1)) ** 2
    gust = bandpass(rng.standard_normal(n), centre, q=1.4)
    e = np.clip(t / 0.08, 0, 1) * (1 - t) ** 2.2
    ts = np.arange(n) / SR
    scuff = lowpass(rng.standard_normal(n), 900) * np.exp(-ts * 40) * 3
    g = gust / (np.max(np.abs(gust)) + 1e-9)
    return lowpass(lowpass(g * e + scuff * 0.4, 3000), 3000)


def blessing(rng):
    """The Kitsune answers: a soft rising pentatonic chime with a shimmer, over a breathy swell."""
    n = int(SR * 1.6)
    t = np.arange(n) / SR
    out = np.zeros(n)
    notes = (659.3, 783.99, 987.77, 1174.66, 1318.5)       # E5 G5 B5 D6 E6
    for i, f in enumerate(notes):
        start = int(SR * 0.09 * i)
        tt = t[: n - start]
        tone = (np.sin(2 * np.pi * f * tt) + 0.35 * np.sin(2 * np.pi * f * 2.01 * tt) + 0.15 * np.sin(2 * np.pi * f * 3.02 * tt))
        out[start:] += tone * np.exp(-tt * 2.6) * np.clip(tt / 0.01, 0, 1) * (0.8 + 0.05 * i)
    shimmer = np.sin(2 * np.pi * 6 * t) * 0.15 + 1
    breath = bandpass(rng.standard_normal(n), np.linspace(900, 2600, n), q=1.2)
    breath = breath / (np.max(np.abs(breath)) + 1e-9) * np.sin(np.pi * np.clip(t / 1.2, 0, 1)) * 0.35
    return lowpass(out * shimmer / len(notes) + breath, 6000)


def main():
    rng = np.random.default_rng(23)
    for i, (lo, hi, end, length) in enumerate(((500, 2600, 900, 0.30), (450, 2300, 800, 0.33), (560, 2900, 1000, 0.28))):
        write_ogg(swing(rng, length, lo, hi, end), f"katana_swing{i + 1}")
    write_ogg(thrust(rng), "katana_thrust")
    write_ogg(hit(rng), "katana_hit", peak=0.8)
    write_ogg(dash(rng), "katana_dash")
    write_ogg(blessing(rng), "kitsune_blessing", peak=0.7)
    print("katana sounds written")


if __name__ == "__main__":
    main()
