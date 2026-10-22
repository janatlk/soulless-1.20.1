// Painterly, high-resolution skins for the Leviathan - away from Minecraft's flat pixel look: the atlas is doubled to
// 512 x 512 (the model's UVs stay on the 256 grid, so every face gets twice the detail), and the hide is shaded as a
// lit surface - domed scales with soft light from above-left, wet specular glints, shadowed crevices, colour drifting
// across the body. paintHD(name) repaints TOP, SIDE (photophores) and BELLY; the rest of the atlas is the base doubled.
(() => {
  const N = 512, S = 2;
  const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
  const hash = (x, y) => {
    let h = Math.imul(x * 374761393 + y * 668265263, 1274126177);
    h ^= h >>> 13;
    h = Math.imul(h, 1274126177);
    return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
  };
  const smooth = (x, y, f) => {   // value noise, 0..1
    const X = Math.floor(x * f), Y = Math.floor(y * f), fx = x * f - X, fy = y * f - Y;
    const u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
    const a = hash(X, Y), b = hash(X + 1, Y), c = hash(X, Y + 1), d = hash(X + 1, Y + 1);
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
  };
  const fbm = (x, y) => smooth(x, y, 1 / 40) * 0.55 + smooth(x, y, 1 / 14) * 0.3 + smooth(x, y, 1 / 5) * 0.15;
  const L = [-0.45, -0.7, 0.55];   // light from above-left, toward the viewer
  const LN = Math.hypot(...L);
  const light = (nx, ny, nz) => Math.max(0, (nx * L[0] + ny * L[1] + nz * L[2]) / LN);
  const spec = (nx, ny, nz, p) => {
    const hx = L[0] / LN, hy = L[1] / LN, hz = L[2] / LN + 1, hn = Math.hypot(hx, hy, hz);
    return Math.pow(Math.max(0, (nx * hx + ny * hy + nz * hz) / hn), p);
  };

  // the skins: each returns [r, g, b] for an HD pixel (x, y) of a band, from a cell (Voronoi) field where it needs one
  const cells = (x, y, size, squash) => {
    const gx = x / size, gy = y / (size * squash);
    const cx = Math.floor(gx), cy = Math.floor(gy);
    let f1 = 9, f2 = 9, px = 0, py = 0, id = 0;
    for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++) {
      const ox = cx + i, oy = cy + j;
      const sx = ox + 0.15 + 0.7 * hash(ox, oy * 7), sy = oy + 0.15 + 0.7 * hash(ox * 3, oy);
      const d = Math.hypot(gx - sx, gy - sy);
      if (d < f1) { f2 = f1; f1 = d; px = gx - sx; py = gy - sy; id = hash(ox, oy); } else if (d < f2) f2 = d;
    }
    return {f1, f2, px, py, id};
  };
  const SKINS = {
    // 1. wet organic scales: irregular domed scales, each catching the light, dark wet crevices between
    scales(x, y) {
      const c = cells(x, y, 9, 0.8);
      const edge = c.f2 - c.f1;
      const r = Math.min(1, c.f1 / 0.55);
      const nx = c.px * 1.6, ny = c.py * 1.6, nz = Math.sqrt(Math.max(0.05, 1 - nx * nx - ny * ny));
      const tint = fbm(x, y), dif = light(nx, ny, nz);
      const base = [16 + 18 * tint + 10 * c.id, 40 + 30 * tint + 12 * c.id, 34 + 22 * tint + 8 * c.id];
      const ao = edge < 0.08 ? 0.25 + edge * 6 : 1;
      const k = (0.35 + 0.85 * dif) * ao;
      const sp = spec(nx, ny, nz, 26) * 150 * (1 - r * 0.3);
      return [base[0] * k + sp * 0.7, base[1] * k + sp, base[2] * k + sp * 0.95];
    },
    // 2. eel hide: no scales - smooth glossy skin, soft mottling, fine wrinkles and a long wet sheen
    eel(x, y) {
      const m = fbm(x * 1.3, y * 1.3), w = Math.sin(y * 0.9 + fbm(x, y) * 9) * 0.5 + 0.5;
      const nx = (fbm(x + 2, y) - fbm(x - 2, y)) * 6, ny = (fbm(x, y + 2) - fbm(x, y - 2)) * 6 + (w - 0.5) * 0.25;
      const nz = 1;
      const nn = Math.hypot(nx, ny, nz);
      const dif = light(nx / nn, ny / nn, nz / nn), sp = spec(nx / nn, ny / nn, nz / nn, 40) * 170;
      const spot = smooth(x, y, 1 / 9) > 0.78 ? 0.55 : 1;
      const base = [14 + 20 * m, 38 + 34 * m, 32 + 26 * m];
      const k = (0.4 + 0.75 * dif) * spot * (0.92 + w * 0.08);
      return [base[0] * k + sp * 0.6, base[1] * k + sp, base[2] * k + sp * 0.9];
    },
    // 3. armour: overlapping plates with bevelled edges, scratched and pitted, a dull metallic sheen
    armour(x, y) {
      const H = 26, band = Math.floor(y / H), v = (y % H) / H, u = ((x + band * 21) % 44) / 44;
      const bevelTop = v < 0.12, bevelBot = v > 0.88, seam = u < 0.025;
      let ny = bevelTop ? -0.8 : bevelBot ? 0.8 : (v - 0.5) * 0.3, nx = seam ? -0.8 : (u - 0.5) * 0.2;
      const nz = Math.sqrt(Math.max(0.05, 1 - nx * nx - ny * ny));
      const dif = light(nx, ny, nz), sp = spec(nx, ny, nz, 14) * 90;
      const scratch = Math.abs(Math.sin(x * 0.31 + y * 1.7 + hash(band, 3) * 9)) < 0.03 ? 1.5 : 1;
      const pit = hash(x, y) < 0.01 ? 0.5 : 1;
      const t = fbm(x, y);
      const base = [22 + 14 * t, 44 + 22 * t, 40 + 16 * t];
      const k = (0.35 + 0.8 * dif) * pit * (seam || bevelBot ? 0.55 : 1);
      return [base[0] * k * scratch + sp * 0.8, base[1] * k * scratch + sp, base[2] * k * scratch + sp];
    },
    // ---- black skins
    // D. obsidian glass: big faceted scales, nearly black, sharp hard highlights along their ridges
    obsidian(x, y) {
      const c = cells(x, y, 13, 0.75);
      const edge = c.f2 - c.f1;
      const fx = Math.sign(c.px) * 0.55, fy = Math.sign(c.py) * 0.55;        // facets: flat planes, not a dome
      const nz = Math.sqrt(1 - fx * fx - fy * fy);
      const dif = light(fx, fy, nz), sp = spec(fx, fy, nz, 60) * 210;
      const ridge = Math.abs(c.px) < 0.04 || Math.abs(c.py) < 0.04 ? 30 : 0;
      const k = 6 + 22 * dif + ridge * (dif > 0.5 ? 1 : 0.2);
      const ao = edge < 0.06 ? 0.3 : 1;
      return [(k + sp * 0.6) * ao, (k + 2 + sp * 0.7) * ao, (k + 8 + sp) * ao];
    },
    // E. abyssal leather: thick black hide in deep folds and wrinkles, wet, a faint cold sheen in the creases
    leather(x, y) {
      const fold = Math.sin(y * 0.22 + fbm(x * 0.6, y) * 7) + 0.5 * Math.sin(x * 0.09 + y * 0.05);
      const fine = Math.sin(x * 0.8 + fbm(x, y * 2) * 6) * 0.25;
      const h = fold + fine;
      const ny = Math.cos(y * 0.22 + fbm(x * 0.6, y) * 7) * 0.6, nx = Math.cos(x * 0.8) * 0.15;
      const nz = Math.sqrt(Math.max(0.05, 1 - nx * nx - ny * ny));
      const dif = light(nx, ny, nz), sp = spec(nx, ny, nz, 30) * 120;
      const k = 7 + 20 * dif * (0.6 + 0.4 * (h * 0.5 + 0.5));
      return [k + sp * 0.5, k + 3 + sp * 0.7, k + 9 + sp];
    },
    // F. blade scales: long pointed scales laid back toward the tail, black, each edged with blue rim light
    blades(x, y) {
      const W = 22, H = 9, row = Math.floor(y / H), off = (row % 2) * W / 2;
      const u = ((x + off) % W) / W, v = (y % H) / H;
      const width = 1 - u;                          // the scale narrows to its point
      const inside = Math.abs(v - 0.5) < width * 0.55;
      if (!inside) return [5, 6, 9];
      const ny = (v - 0.5) / Math.max(0.1, width * 0.55) * 0.7, nx = -0.3;
      const nz = Math.sqrt(Math.max(0.05, 1 - nx * nx - ny * ny));
      const dif = light(nx, ny, nz), rim = Math.abs(Math.abs(v - 0.5) - width * 0.55) < 0.06 ? 1 : 0;
      const k = 8 + 24 * dif;
      return [k + rim * 30, k + 3 + rim * 70, k + 10 + rim * 110];
    },
    // G. oil-slick: black scales with an iridescent film - violet, teal and green drifting across them in the light
    oil(x, y) {
      const c = cells(x, y, 8, 0.85);
      const edge = c.f2 - c.f1;
      const nx = c.px * 1.5, ny = c.py * 1.5, nz = Math.sqrt(Math.max(0.05, 1 - nx * nx - ny * ny));
      const dif = light(nx, ny, nz), sp = spec(nx, ny, nz, 18);
      const phase = fbm(x, y) * 6 + nx * 2 + ny * 2;
      const film = [Math.sin(phase) * 0.5 + 0.5, Math.sin(phase + 2.1) * 0.5 + 0.5, Math.sin(phase + 4.2) * 0.5 + 0.5];
      const k = 6 + 14 * dif, ao = edge < 0.07 ? 0.3 : 1;
      return [(k + film[0] * 60 * sp + sp * 40) * ao, (k + film[1] * 60 * sp + sp * 40) * ao, (k + 4 + film[2] * 70 * sp + sp * 50) * ao];
    },
    // H. osteoderms: fine black pebbled skin set with rows of large bony knobs, as a crocodile's back
    knobs(x, y) {
      const big = cells(x, y, 18, 1), small = cells(x * 3.1, y * 3.1, 4, 1);
      const isKnob = big.f1 < 0.36;
      const c = isKnob ? big : small;
      const sc = isKnob ? 2.4 : 1.4;
      const nx = c.px * sc, ny = c.py * sc, nz = Math.sqrt(Math.max(0.05, 1 - nx * nx - ny * ny));
      const dif = light(nx, ny, nz), sp = spec(nx, ny, nz, isKnob ? 20 : 12) * (isKnob ? 120 : 50);
      const groove = !isKnob && small.f2 - small.f1 < 0.1 ? 0.4 : 1;
      const k = (isKnob ? 12 : 8) + (isKnob ? 26 : 14) * dif;
      return [(k + sp * 0.6) * groove, (k + 2 + sp * 0.7) * groove, (k + 7 + sp) * groove];
    },
  };

  window.paintHD = (name) => {
    const tex = Texture.all[0];
    const c = document.createElement('canvas');
    c.width = N; c.height = N;
    const g = c.getContext('2d');
    g.imageSmoothingEnabled = false;
    g.drawImage(tex.img, 0, 0, N, N);
    const img = g.getImageData(0, 0, N, N);
    const put = (x, y, col) => { const i = (y * N + x) * 4; img.data[i] = clamp(col[0]); img.data[i + 1] = clamp(col[1]); img.data[i + 2] = clamp(col[2]); img.data[i + 3] = 255; };
    const f = SKINS[name];
    for (let y = 0; y < 128 * S; y++) for (let x = 0; x < 128 * S; x++) put(x, y, f(x, y));
    // photophores: soft glowing pearls with a halo, along the side band (base y 95-97)
    for (let bx = 3; bx < 128; bx += 8) {
      const cx = bx * S + 1, cy = 96 * S + 1;
      for (let i = -5; i <= 5; i++) for (let j = -5; j <= 5; j++) {
        const d = Math.hypot(i, j);
        if (d > 5) continue;
        const k = Math.max(0, 1 - d / 5);
        const i0 = ((cy + j) * N + cx + i) * 4;
        const glow = d < 1.6 ? 1 : k * k * 0.7;
        img.data[i0] = clamp(img.data[i0] * (1 - glow) + 210 * glow);
        img.data[i0 + 1] = clamp(img.data[i0 + 1] * (1 - glow) + 255 * glow);
        img.data[i0 + 2] = clamp(img.data[i0 + 2] * (1 - glow) + 245 * glow);
      }
    }
    // belly: pale, softly lit transverse plates
    for (let y = 0; y < 64 * S; y++) for (let x = 0; x < 128 * S; x++) {
      const v = (y % 16) / 16, t = fbm(x + 900, y);
      const ny = v < 0.15 ? -0.7 : v > 0.85 ? 0.7 : 0, nz = Math.sqrt(1 - ny * ny);
      const k = 0.45 + 0.7 * light(0, ny, nz);
      const dark = window.__blackBelly;
      put(128 * S + x, y, dark ? [(26 + 14 * t) * k, (28 + 14 * t) * k, (36 + 16 * t) * k] : [(70 + 30 * t) * k, (76 + 30 * t) * k, (52 + 20 * t) * k]);
    }
    if (window.__hdDetails) details(img, put);
    g.putImageData(img, 0, 0);
    tex.fromDataURL(c.toDataURL());
    return name;
  };

  // the small parts redrawn in the same lit, painterly manner (HD coordinates)
  const details = (img, put) => {
    const clear = (x0, y0, w, h) => { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) img.data[(y * N + x) * 4 + 3] = 0; };
    // GUM (416,128 96x96): glossy deep-blue flesh in ridges, wet highlights on every crest
    for (let y = 0; y < 96; y++) for (let x = 0; x < 96; x++) {
      const ph = (y + Math.sin(x * 0.15) * 3) / 10 * Math.PI * 2;
      const ny = Math.cos(ph) * 0.6, nz = Math.sqrt(1 - ny * ny);
      const dif = light(0, ny, nz), sp = spec(0, ny, nz, 30) * 200, t = fbm(x * 2, y * 2);
      const k = 0.35 + 0.8 * dif;
      put(416 + x, 128 + y, [(30 + 30 * t) * k + sp * 0.7, (70 + 40 * t) * k + sp * 0.9, (130 + 50 * t) * k + sp]);
    }
    // EYE (384,160 32x32): a compound eye - four amber domes in a black socket, each with a hot core and a glint
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const cx = x < 16 ? 8 : 24, cy = y < 16 ? 8 : 24;
      const dx = (x + 0.5 - cx) / 6.5, dy = (y + 0.5 - cy) / 6.5, d = Math.hypot(dx, dy);
      if (d > 1) { put(384 + x, 160 + y, [10, 6, 3]); continue; }
      const nz = Math.sqrt(1 - d * d), dif = light(dx, dy, nz), sp = spec(dx, dy, nz, 50);
      const core = 1 - d;
      put(384 + x, 160 + y, [120 + 135 * core + 60 * dif + 255 * sp, 40 + 150 * core * core + 30 * dif + 255 * sp, 8 + 60 * core * core * core + 230 * sp]);
    }
    // TIP (384,192 32x32): a glowing bead, white-hot at its heart
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16, k = Math.max(0, 1 - d);
      put(384 + x, 192 + y, [255, 140 + 115 * k * k, 40 + 190 * k * k * k]);
    }
    // FANGS (128,256 320x32) and FANGS_DN (128,288): rounded ivory fangs, lit from the left, a stained root and a
    // pale translucent point, transparent between
    for (const [y0, up] of [[256, false], [288, true]]) {
      clear(128, y0, 320, 32);
      let x = 130, seed = up ? 5 : 3;
      const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
      while (x < 440) {
        const w = 6 + Math.floor(rnd() * 8), broken = rnd() < 0.12;
        const len = broken ? 8 + Math.floor(rnd() * 6) : 14 + Math.floor(rnd() * 17);
        const lean = (rnd() - 0.5) * 0.25;
        for (let j = 0; j < Math.min(32, len); j++) {
          const f = j / len, half = (w / 2) * (1 - Math.pow(f, 1.3) * 0.92), mid = w / 2 + lean * j;
          for (let i = 0; i < w + 4; i++) {
            const dx = (i + 0.5 - mid) / Math.max(0.5, half);
            if (Math.abs(dx) > 1 || x + i >= 448) continue;
            const nz = Math.sqrt(1 - dx * dx), dif = light(dx * 0.9, 0, nz), sp = spec(dx * 0.9, 0, nz, 20) * 90;
            const root = f < 0.15 ? 0.6 : 1;
            const c = [120 + f * 90, 104 + f * 82, 72 + f * 70];
            put(x + i, up ? y0 + 31 - j : y0 + j, [c[0] * (0.45 + 0.7 * dif) * root + sp, c[1] * (0.45 + 0.7 * dif) * root + sp, c[2] * (0.45 + 0.7 * dif) * root + sp * 0.8]);
          }
        }
        x += w + (rnd() < 0.35 ? 3 + Math.floor(rnd() * 6) : 1);
      }
    }
  };
  return Object.keys(SKINS);
})()
