// Skin variants for the Leviathan, pixel art in Minecraft's manner: paints the TOP (0,0 128x64), SIDE (0,64 128x64,
// with the photophore row at y 95-97) and BELLY (128,0 128x64) regions of the live texture. Usage (risky_eval):
//   eval(fs.readFileSync('.../hide_variants.js', 'utf8')); paintHide('violet');
(() => {
  const VARIANTS = {
    // 1. obsidian armour: black plates with a cold blue edge
    obsidian: {size: [10, 7], pal: [[10, 11, 16], [20, 22, 30], [30, 33, 44], [44, 49, 64], [66, 74, 96]],
      belly: [[52, 56, 68], [40, 44, 55], [34, 37, 47], [22, 24, 31]], lights: [242, 251, 255], stripe: null},
    // 2. abyssal violet: deep purple scales, a teal rim on each
    violet: {size: [8, 6], pal: [[12, 8, 20], [28, 16, 44], [44, 26, 66], [62, 40, 92], [70, 150, 150]],
      belly: [[74, 56, 92], [58, 42, 74], [48, 34, 62], [30, 20, 40]], lights: [170, 255, 240], stripe: null},
    // 3. drowned green: dark sea-green, an olive belly, cyan freckles
    drowned: {size: [8, 6], pal: [[8, 16, 14], [16, 32, 28], [24, 48, 42], [36, 68, 58], [70, 110, 92]],
      belly: [[92, 98, 64], [76, 82, 52], [64, 70, 44], [42, 46, 28]], lights: [150, 255, 235], stripe: null, freckles: [90, 220, 220]},
    // 4. bone plates: dark grey armour plates, big, cracked, with pale worn edges (an ender dragon's kin)
    bone: {size: [14, 10], pal: [[14, 14, 16], [30, 30, 34], [44, 44, 50], [62, 62, 70], [128, 124, 112]],
      belly: [[96, 92, 82], [78, 74, 66], [64, 60, 54], [40, 38, 34]], lights: [240, 240, 220], stripe: null, cracks: true},
    // 5. striped eel: small black scales, a thin glowing stripe down each side and a dark spine band
    eel: {size: [6, 4], pal: [[8, 9, 12], [16, 18, 24], [24, 27, 36], [36, 40, 52], [52, 58, 74]],
      belly: [[60, 64, 76], [48, 52, 62], [40, 43, 52], [26, 28, 34]], lights: [242, 251, 255], stripe: [60, 200, 230]},
  };

  window.paintHide = (name) => {
    const v = VARIANTS[name];
    const tex = Texture.all[0];
    const c = document.createElement('canvas');
    c.width = 256; c.height = 256;
    const g = c.getContext('2d');
    g.drawImage(tex.img, 0, 0);
    let seed = 11;
    const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
    const px = (x, y, col) => { g.fillStyle = `rgb(${col[0]},${col[1]},${col[2]})`; g.fillRect(x, y, 1, 1); };
    const [W, H] = v.size;
    // one scale: lit top edge, body, darker toward the bottom, a dark rim; shapes scale with the size
    const tone = (u, w) => {
      const cu = (u - (W - 1) / 2) / (W / 2), cw = w / (H - 1);
      if (cw >= 0.99 || (Math.abs(cu) > 0.86 && cw > 0.5)) return 0;
      if (w === 0) return Math.abs(cu) < 0.75 ? 4 : 3;
      if (cw < 0.3) return 3;
      if (cw < 0.75) return Math.abs(cu) > 0.8 ? 1 : 2;
      return 1;
    };
    const scales = (x0, y0, w, h) => {
      for (let j = 0; j < h; j++) {
        const row = Math.floor(j / H), off = (row % 2) * Math.floor(W / 2);
        for (let i = 0; i < w; i++) {
          const col = Math.floor((i + off) / W);
          let t = tone((i + off) % W, j % H);
          const vary = (col * 7 + row * 13) % 11 === 0 ? 1 : (col * 5 + row * 3) % 13 === 0 ? -1 : 0;
          if (t > 0 && t < 4) t = Math.max(1, Math.min(3, t + vary));
          px(x0 + i, y0 + j, v.pal[t]);
          if (v.cracks && t === 2 && rnd() < 0.03) px(x0 + i, y0 + j, v.pal[0]);
          if (v.freckles && t >= 2 && rnd() < 0.012) px(x0 + i, y0 + j, v.freckles);
        }
      }
    };
    scales(0, 0, 128, 64);
    scales(0, 64, 128, 64);
    if (v.stripe) {
      for (let i = 0; i < 128; i++) { px(i, 88, v.stripe); px(i, 104, v.stripe); }
      for (let i = 0; i < 128; i++) for (let j = 26; j < 38; j++) if ((i + j) % 3) px(i, j, v.pal[0]);
    }
    for (let x = 3; x < 128; x += 8) {
      px(x - 1, 96, v.pal[3]); px(x + 1, 96, v.pal[3]); px(x, 95, v.pal[3]); px(x, 97, v.pal[3]);
      px(x, 96, v.lights);
    }
    for (let j = 0; j < 64; j++) for (let i = 0; i < 128; i++) {
      const k = j % 8;
      const col = k === 0 ? v.belly[0] : k < 4 ? v.belly[1] : k < 7 ? v.belly[2] : v.belly[3];
      px(128 + i, j, col);
    }
    tex.fromDataURL(c.toDataURL());
    return name;
  };
  return Object.keys(VARIANTS);
})()
