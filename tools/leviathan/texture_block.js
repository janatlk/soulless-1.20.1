  const rgb = (r, g2, b) => `rgb(${Math.max(0, Math.min(255, Math.round(r)))},${Math.max(0, Math.min(255, Math.round(g2)))},${Math.max(0, Math.min(255, Math.round(b)))})`;
  // the hide: rounded overlapping scales (5 wide, 4 tall, every other row half a scale over), each darker at its rim,
  // lit across its upper half, with a faint blue-violet sheen that drifts across the hide
  const scales = (x0, y0, w, h, base) => {
    for (let i = 0; i < w; i++) for (let j = 0; j < h; j++) {
      const row = Math.floor(j / 4), off = (row % 2) * 2.5;
      const u = ((i + off) % 5 + 5) % 5, v = j % 4;
      const cu = (u - 2) / 2.5, cv = (v - 1.2) / 2.8;
      const r2 = cu * cu + cv * cv;
      let k = base + (1 - Math.min(1, r2)) * 7 - v * 2;
      if (r2 > 0.9 || v === 3) k -= 8;
      if (v === 0 && u > 0.6 && u < 3.6) k += 5;
      k += (rnd() - 0.5) * 3;
      const sheen = 3 + 3 * Math.sin((x0 + i) * 0.11 + (y0 + j) * 0.07);
      fill(x0 + i, y0 + j, 1, 1, rgb(k, k + 1, k + 4 + sheen));
    }
  };
  scales(0, 0, 128, 64, 20);               // TOP
  scales(0, 64, 128, 64, 22);              // SIDE, with one row of photophores along it
  for (let x = 3; x < 128; x += 8) {
    // a small cold light with a faint halo
    fill(x - 1, 95, 3, 3, rgb(48, 62, 78));
    fill(x, 95, 1, 3, rgb(120, 150, 175));
    fill(x - 1, 96, 3, 1, rgb(120, 150, 175));
    fill(x, 96, 1, 1, '#f2fbff');
  }
  // BELLY: plates of dark slate, each lit along its front edge and shadowed at its back
  for (let i = 0; i < 128; i++) for (let j = 0; j < 64; j++) {
    const v = j % 8;
    let k = 36 - v * 1.6 + (rnd() - 0.5) * 3;
    if (v === 0) k += 10;
    if (v === 7) k -= 12;
    fill(128 + i, j, 1, 1, rgb(k, k + 3, k + 9));
  }
  // LID: the eye's doors - dark plates with seams
  for (let i = 0; i < 64; i++) for (let j = 0; j < 64; j++) {
    const k = 14 + (rnd() - 0.5) * 4 + (i % 4 === 0 ? -5 : 0);
    fill(128 + i, 64 + j, 1, 1, rgb(k, k + 1, k + 5));
  }
  // TEETH: old yellowed fangs - a dark stained root (top of the swatch), a dirty ivory shaft, a paler worn point
  for (let i = 0; i < 16; i++) for (let j = 0; j < 16; j++) {
    const f = j / 15, edge = i === 0 || i === 15 ? -26 : i < 4 ? 10 : 0;
    const stain = Math.sin(i * 1.7 + j * 0.9) > 0.7 ? -18 : 0;
    fill(192 + i, 64 + j, 1, 1, rgb(112 + f * 88 + edge + stain, 96 + f * 80 + edge + stain, 62 + f * 66 + edge + stain));
  }
  // GUM: wet, pale cold flesh with darker veins and glints
  for (let i = 0; i < 48; i++) for (let j = 0; j < 48; j++) {
    const vein = Math.abs(Math.sin(i * 0.35 + Math.sin(j * 0.25) * 2)) < 0.12 ? -26 : 0;
    const k = 120 + (rnd() - 0.5) * 14 + vein + (rnd() < 0.02 ? 50 : 0);
    fill(208 + i, 64 + j, 1, 1, rgb(k - 30, k + 14, k + 44));
  }
  // EYE: a compound eye - a dark socket ring, four amber cells, each glowing from its centre with a catch-light
  fill(192, 80, 16, 16, rgb(28, 14, 6));
  fill(192, 80, 16, 1, rgb(60, 34, 14));
  fill(192, 95, 16, 1, rgb(10, 5, 2));
  for (const [cx, cy] of [[193, 81], [200, 81], [193, 88], [200, 88]]) {
    for (let i = 0; i < 7; i++) for (let j = 0; j < 7; j++) {
      const d = Math.hypot(i - 3, j - 3) / 4.3;
      fill(cx + i, cy + j, 1, 1, rgb(255 - d * 90, 205 - d * 160, 90 - d * 90));
    }
    fill(cx, cy + 6, 7, 1, rgb(150, 60, 0));
    fill(cx + 1, cy + 1, 2, 1, '#fff4d0');
  }
  // TIP: a glowing bead, hot at its heart
  for (let i = 0; i < 16; i++) for (let j = 0; j < 16; j++) {
    const d = Math.hypot(i - 7.5, j - 7.5) / 10.6;
    fill(192 + i, 96 + j, 1, 1, rgb(255, 230 - d * 130, 140 - d * 130));
  }
  // THROAT: the dark wet gullet
  for (let i = 0; i < 64; i++) for (let j = 0; j < 64; j++) {
    const k = 22 + (rnd() - 0.5) * 6 - j * 0.2;
    fill(i, 128 + j, 1, 1, rgb(k + 14, k - 12, k - 6));
  }
