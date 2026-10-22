// Skin patterns for the Leviathan in the drowned-green palette: the same five tones, different textures.
// paintPattern(name) paints TOP (0,0 128x64), SIDE (0,64 128x64, photophores at y 95-97) and BELLY (128,0 128x64).
(() => {
  const PAL = [[8, 16, 14], [16, 32, 28], [24, 48, 42], [36, 68, 58], [70, 110, 92]];
  const FRECKLE = [90, 220, 220], LIGHT = [150, 255, 235];
  const BELLY = [[92, 98, 64], [76, 82, 52], [64, 70, 44], [42, 46, 28]];

  // each pattern: (x, y, rnd) -> tone 0..4, or [r, g, b] for an accent
  const PATTERNS = {
    // 1. honeycomb: hexagonal scales with dark grooves, a lit upper rim
    hex(x, y) {
      const R = 4, row = Math.floor(y / (R * 1.5)), off = (row % 2) * R;
      const cx = Math.floor((x + off) / (R * 2)) * R * 2 - off + R, cy = row * R * 1.5 + R * 0.75;
      const dx = Math.abs(x + 0.5 - cx), dy = y + 0.5 - cy;
      const edge = Math.max(dx * 0.87 + Math.abs(dy) * 0.5, Math.abs(dy)) / R;
      if (edge > 0.92) return 0;
      if (dy < -R * 0.45) return 4;
      if (dy < 0) return 3;
      return edge > 0.7 ? 1 : 2;
    },
    // 2. diamonds: a snake's lattice of rhombus scales on the diagonal, each with a keel down its middle
    diamond(x, y) {
      const u = ((x + y) % 8 + 8) % 8, v = ((x - y) % 8 + 8) % 8;
      if (u === 0 || v === 0) return 0;
      if (u === 4 && v > 2 && v < 7) return 4;
      if (u < 3 && v < 3) return 3;
      return u + v > 10 ? 1 : 2;
    },
    // 3. fish scales: overlapping half-moons, each a bright arc over a shadowed belly
    fish(x, y) {
      const W = 8, H = 5, row = Math.floor(y / H), off = (row % 2) * W / 2;
      const u = ((x + off) % W + W) % W - W / 2 + 0.5, v = y % H;
      const r = Math.hypot(u, v + 1.5);
      if (r > W / 2 + 0.2) return 1;
      if (r > W / 2 - 0.8) return 4;
      if (r > W / 2 - 1.8) return 3;
      return v > 2 ? 1 : 2;
    },
    // 4. scutes: a crocodile's armour - rectangular plates in rows, each with a raised knob and a dark seam
    scute(x, y) {
      const W = 10, H = 7, row = Math.floor(y / H), off = (row % 2) * 5;
      const u = ((x + off) % W + W) % W, v = y % H;
      if (u === 0 || v === H - 1) return 0;
      if (u === W - 1 || v === H - 2) return 1;
      if ((u === 4 || u === 5) && (v === 2 || v === 3)) return v === 2 ? 4 : 3;
      return v === 0 ? 3 : 2;
    },
    // 5. leather: no scales - a mottled hide like a deep-sea fish's, with warts and glowing freckles
    leather(x, y, rnd) {
      const n = Math.sin(x * 0.45 + Math.sin(y * 0.3) * 2) + Math.sin(y * 0.55 + Math.cos(x * 0.2) * 1.5);
      if (rnd() < 0.012) return FRECKLE;
      const wart = (x * 13 + y * 7) % 23 === 0;
      if (wart) return 4;
      if ((x * 13 + y * 7) % 23 === 1 || (x * 13 + y * 7 - 23) % 23 === 0) return 0;
      return n > 1.1 ? 3 : n > 0 ? 2 : n > -1.1 ? 1 : 0;
    },
    // 6. encrusted: scales grown over with barnacle rings and pale crusts, as something lying long on the sea floor
    barnacle(x, y, rnd) {
      const row = Math.floor(y / 6), off = (row % 2) * 4, u = (x + off) % 8, v = y % 6;
      let t = v === 0 ? 3 : v === 5 || u === 0 ? 0 : v < 3 ? 2 : 1;
      const cx = (x * 7 + y * 3) % 29, cy = (x * 3 + y * 11) % 31;
      const b = Math.hypot(((x + 3) % 13) - 6, ((y + 5) % 11) - 5);
      if ((Math.floor(x / 13) * 5 + Math.floor(y / 11) * 3) % 4 === 0 && b < 3.2) t = b < 1.2 ? 0 : b < 2.2 ? 4 : 3;
      return t;
    },
    // 7. ridged: no scales - deep grooves running the length of the body, ridge tops lit, notched every so often
    ridge(x, y) {
      const v = y % 5;
      if (v === 0) return 0;
      if (v === 1) return (x + Math.floor(y / 5) * 3) % 11 === 0 ? 1 : 4;
      if (v === 2) return 3;
      return v === 3 ? 2 : 1;
    },
    // 8. riveted plates: broad bands of armour like hull plating, each band lapped over the next, a stud at each end
    plate(x, y) {
      const H = 9, v = y % H, band = Math.floor(y / H), u = (x + band * 7) % 24;
      if (v === H - 1) return 0;
      if (v === 0) return 4;
      if (u === 0) return 0;
      if (u === 1) return 3;
      if ((u === 4 || u === 20) && v === 4) return 4;
      if ((u === 4 || u === 20) && v === 5) return 1;
      return v < 3 ? 3 : 2;
    },
    // 9. glowing veins: cracked dark hide with thin cyan light seeping through the cracks
    veins(x, y, rnd) {
      const a = Math.sin(x * 0.35 + Math.sin(y * 0.5) * 2.2), b = Math.sin(y * 0.42 + Math.sin(x * 0.27) * 2.6);
      if (Math.abs(a) < 0.07 || Math.abs(b) < 0.06) return FRECKLE;
      if (Math.abs(a) < 0.16 || Math.abs(b) < 0.14) return 0;
      const n = a * b;
      return n > 0.4 ? 3 : n > -0.1 ? 2 : 1;
    },
    // 10. graded scales: big scales on the back shrinking to fine ones toward the belly, as a real serpent's
    graded(x, y) {
      const band = Math.floor((y % 64) / 16);
      const W = [10, 8, 6, 4][band], H = [7, 6, 4, 3][band];
      const yy = (y % 64) - band * 16, row = Math.floor(yy / H), off = (row % 2) * Math.floor(W / 2);
      const u = (x + off) % W, v = yy % H;
      if (v === H - 1 || (u === 0 && v > H / 2)) return 0;
      if (v === 0) return u > 0 && u < W - 1 ? 4 : 3;
      return v < H / 2 ? 3 : 2;
    },
  };

  window.paintPattern = (name) => {
    const tex = Texture.all[0];
    const c = document.createElement('canvas');
    c.width = 256; c.height = 256;
    const g = c.getContext('2d');
    g.drawImage(tex.img, 0, 0);
    let seed = 17;
    const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
    const px = (x, y, col) => { g.fillStyle = `rgb(${col[0]},${col[1]},${col[2]})`; g.fillRect(x, y, 1, 1); };
    const f = PATTERNS[name];
    for (const y0 of [0, 64]) {
      for (let j = 0; j < 64; j++) for (let i = 0; i < 128; i++) {
        const t = f(i, j + y0, rnd);
        px(i, j + y0, Array.isArray(t) ? t : PAL[t]);
        if (!Array.isArray(t) && name !== 'leather' && name !== 'veins' && t >= 2 && rnd() < 0.006) px(i, j + y0, FRECKLE);
      }
    }
    for (let x = 3; x < 128; x += 8) {
      px(x - 1, 96, PAL[3]); px(x + 1, 96, PAL[3]); px(x, 95, PAL[3]); px(x, 97, PAL[3]);
      px(x, 96, LIGHT);
    }
    for (let j = 0; j < 64; j++) for (let i = 0; i < 128; i++) {
      const k = j % 8;
      px(128 + i, j, k === 0 ? BELLY[0] : k < 4 ? BELLY[1] : k < 7 ? BELLY[2] : BELLY[3]);
    }
    tex.fromDataURL(c.toDataURL());
    return name;
  };
  return Object.keys(PATTERNS);
})()
