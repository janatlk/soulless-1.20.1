// The Lampkeeper - built in Blockbench through the MCP (risky_eval), GeckoLib entity. Concept: art/mobs/lamp_2.png.
// Made the way a modeller would: every face has its own patch on a 128x128 sheet, painted for what it is (a lit top
// edge, folds, the rune stitching, the hood's rim and the dark inside it, the hem fading into the soul's glow), and
// the torn hem is real geometry - a ring of flaps of different lengths - not only a painted edge.
// Faces north (-Z); its tendrils' tips near y 0. Bones: root > body (mantle, robe, hem flaps, arms, hood > eyes),
// bell > tendril_<i>_a > tendril_<i>_b; arm_r > forearm_r > chain > lantern; arm_l > forearm_l.
(() => {
  const fs = require('fs');
  const OUT = 'C:/Users/janat/GameMakerProjects/minecraftmod/tools/mobs/';
  if (!Project || Project.name !== 'lampkeeper') newProject(Formats.geckolib_model);
  Animation.all.slice().forEach(a => a.remove(false));
  Cube.all.slice().forEach(c => c.remove());
  Group.all.slice().forEach(gr => gr.remove(false));
  Texture.all.slice().forEach(t => t.remove(true));
  Project.name = 'lampkeeper';
  Project.box_uv = false;
  const W = 256;
  Project.texture_width = W;
  Project.texture_height = W;

  const cv = document.createElement('canvas');
  cv.width = W; cv.height = W;
  const g = cv.getContext('2d');
  const gm = document.createElement('canvas');   // the glow mask
  gm.width = W; gm.height = W;
  const gg = gm.getContext('2d');
  const rgba = (c, a) => `rgba(${c[0]},${c[1]},${c[2]},${a === undefined ? 1 : a})`;
  const px = (x, y, c, a, glow) => {
    g.fillStyle = rgba(c, a); g.fillRect(x, y, 1, 1);
    if (glow) { gg.fillStyle = rgba(c, glow); gg.fillRect(x, y, 1, 1); }
  };
  const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));

  // ---- the palette (from the concept)
  const CLOTH = {hi: [104, 126, 160], lit: [86, 106, 138], base: [70, 88, 118], shade: [54, 68, 94], deep: [38, 48, 68]};
  const RUNE = [146, 104, 214], RUNE_DK = [104, 72, 160];
  const GLOW = [120, 236, 226], GLOW_MID = [70, 170, 176];
  const BONE = {hi: [226, 220, 194], base: [204, 196, 166], shade: [164, 156, 128]};
  const IRON = {hi: [96, 102, 112], base: [56, 60, 70], dk: [34, 36, 42]};

  // ---- face painters: (x0, y0, w, h, face, opt) - each face painted for itself
  const P = {
    // cloth: folds running down, the top edge catching the light, the lowest row in shadow
    cloth: (x0, y0, w, h, f, opt) => {
      const o = opt || {};
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const col = (x + (o.shift || 0)) % 7;
        let c = col === 0 ? mix(CLOTH.base, CLOTH.shade, 0.7) : col === 3 || col === 4 ? mix(CLOTH.base, CLOTH.lit, 0.6) : CLOTH.base;
        // the cloth darkens toward its lower part, as the light comes from above
        if (f !== 'up' && f !== 'down') c = mix(c, CLOTH.shade, 0.35 * y / Math.max(1, h - 1));
        // worn: here and there a thread darker or a patch faded
        const n = ((x0 + x) * 73 + (y0 + y) * 151) % 37;
        if (n === 0) c = mix(c, CLOTH.shade, 0.6); else if (n === 5) c = mix(c, CLOTH.lit, 0.5);
        if (f === 'up') c = (x + y) % 3 === 0 ? CLOTH.lit : CLOTH.hi;
        else if (y === 0 && !o.noLip) c = CLOTH.hi;
        else if (y === h - 1) c = CLOTH.deep;
        // shadow under what hangs over it (the mantle, the hood)
        if (o.aoTop && f !== 'up' && f !== 'down' && y < o.aoTop) c = mix(c, CLOTH.deep, 0.55 * (1 - y / o.aoTop));
        if (o.glow && f !== 'up' && f !== 'down') {
          const t = (y / Math.max(1, h - 1) - 0.6) / 0.4;
          if (t > 0) { c = mix(c, t > 0.6 ? GLOW : GLOW_MID, Math.min(1, t * 1.2)); gg.fillStyle = rgba(c, t * 0.8); gg.fillRect(x0 + x, y0 + y, 1, 1); }
        }
        if (o.edge && f === 'north' && x === (o.edge > 0 ? w - 1 : 0) && y % 2 === 1 && y > 1 && y < h * 0.7) c = RUNE;
        if (f === 'down') c = CLOTH.deep;
        // a frayed lower edge: the last rows torn in uneven points
        if (o.fray && f !== 'up' && f !== 'down') {
          const tear = 1 + ((x * 5 + (o.shift || 0) * 3) % 3);
          if (y >= h - tear) continue;
        }
        px(x0 + x, y0 + y, c);
      }
      // the rune stitching: a dotted band a little below the top, and a glyph in the middle of wide faces
      const o2 = opt || {};
      if (o2.runes && f !== 'up' && f !== 'down' && h > 4) {
        if (f === 'south' && o2.arc) {
          for (let x = 1; x < w - 1; x++) {
            const dy = Math.round(Math.pow(Math.abs(x - (w - 1) / 2) / ((w - 1) / 2), 1.5) * -2.5) + 4;
            if (x % 3 !== 2) px(x0 + x, y0 + dy, RUNE);
          }
        } else if ((f === 'east' || f === 'west') && o2.side) {
          for (let y = 2; y < h * 0.6; y += 2) px(x0 + (w >> 1), y0 + y, RUNE_DK);
        }
      }
    },
    // the hem: cloth thinning down into the soul's light, its tip torn
    hem: (x0, y0, w, h) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const t = y / Math.max(1, h - 1);
        let c, a = 1, glow = 0;
        // the flaps hang below the cloak's glowing lower edge: they start in the glow and burn brighter down
        c = mix(x % 2 ? GLOW_MID : mix(GLOW_MID, CLOTH.shade, 0.3), GLOW, t);
        a = 0.95 - t * 0.1;
        glow = 0.5 + t * 0.5;
        if (y === h - 1 && x % 2 === 1) continue;   // a ragged tip
        px(x0 + x, y0 + y, c, a, glow);
      }
    },
    // the hood's front: a cloth rim round a hollow dark, two cold eyes in it
    hoodFront: (x0, y0, w, h) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const rim = x === 0 || x === w - 1 || y === 0;
        const inner = x === 1 || x === w - 2 || y === 1;
        const inner2 = (x === 2 || x === w - 3 || y === 2) && !rim && !inner;
        let c = rim ? CLOTH.hi : inner ? CLOTH.deep : inner2 ? [20, 24, 34] : mix([12, 14, 20], [4, 5, 8], y / h);
        if (y === h - 1 && !rim) c = CLOTH.shade;   // the cloth meets under the chin
        px(x0 + x, y0 + y, c);
      }
      const ey = y0 + Math.round(h * 0.45), ex = x0 + Math.round(w / 2);
      for (const dx of [-3, 1]) {
        px(ex + dx, ey, [210, 255, 255], 1, 1);
        px(ex + dx + 1, ey, [120, 240, 245], 1, 1);
        px(ex + dx, ey + 1, [60, 190, 200], 1, 0.6);
        px(ex + dx + 1, ey + 1, [60, 190, 200], 1, 0.6);
      }
    },
    // the robe open at the front: dark, the soul's light rising in it from below
    dark: (x0, y0, w, h) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const t = Math.pow(y / Math.max(1, h - 1), 1.6);
        px(x0 + x, y0 + y, mix([12, 16, 24], [90, 220, 215], t), 1, t * 0.9);
      }
    },
    bone: (x0, y0, w, h) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        let c = x === 0 ? BONE.hi : x === w - 1 ? BONE.shade : BONE.base;
        if (y % 4 === 3) c = BONE.shade;   // knuckles and joints
        px(x0 + x, y0 + y, c);
      }
    },
    jelly: (x0, y0, w, h, f) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const side = f !== 'up' && f !== 'down';
        const rim = side && y === 0, ring = side && y % 3 === 2;
        const c = rim ? [190, 255, 250] : ring ? [140, 245, 238] : [80, 210, 210];
        px(x0 + x, y0 + y, c, rim ? 0.95 : ring ? 0.75 : 0.5, rim ? 1 : 0.6);
      }
    },
    tendril: (x0, y0, w, h, f, opt) => {
      const k0 = opt ? opt.k0 : 0, k1 = opt ? opt.k1 : 1;
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const t = k0 + (k1 - k0) * y / Math.max(1, h - 1);
        const c = mix([150, 245, 240], [50, 150, 160], t);
        px(x0 + x, y0 + y, x === 0 ? mix(c, [255, 255, 255], 0.2) : c, 0.95 - t * 0.35, 0.8 - t * 0.5);
      }
    },
    iron: (x0, y0, w, h) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) px(x0 + x, y0 + y, y === 0 || x === 0 ? IRON.hi : y === h - 1 ? IRON.dk : IRON.base); },
    glass: (x0, y0, w, h) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const d = Math.hypot(x - (w - 1) / 2, y - (h - 1) * 0.6) / Math.max(w, h) * 2;
        px(x0 + x, y0 + y, mix([230, 255, 255], [60, 190, 210], Math.min(1, d)), 1, 1);
      }
    },
    chain: (x0, y0, w, h) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) px(x0 + x, y0 + y, y % 2 ? IRON.dk : IRON.hi); },
  };

  // ---- the sheet: each face its own patch (packed in rows), painted as it is placed
  let cx = 0, cy = 0, rowH = 0;
  const patch = (w, h) => {
    if (cx + w > W) { cx = 0; cy += rowH; rowH = 0; }
    const at = [cx, cy];
    cx += w; rowH = Math.max(rowH, h);
    return at;
  };
  const dims = {north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2]};
  const cubes = [];
  // paint: a painter name, or {face: painter, _: default}; opt goes to the painter
  const cube = (parent, from, to, paint, opt, rotation, origin) => {
    const c = new Cube({name: 'c' + cubes.length, from, to, rotation: rotation || [0, 0, 0],
      origin: origin || from.map((v, i) => (v + to[i]) / 2)}).addTo(parent).init();
    const size = [to[0] - from[0], to[1] - from[1], to[2] - from[2]];
    for (const k of Object.keys(dims)) {
      const w = Math.max(1, Math.ceil(size[dims[k][0]])), h = Math.max(1, Math.ceil(size[dims[k][1]]));
      const [ux, uy] = patch(w, h);
      const name = typeof paint === 'string' ? paint : (paint[k] || paint._);
      P[name](ux, uy, w, h, k, opt);
      c.faces[k].uv = [ux, uy, ux + w, uy + h];
    }
    cubes.push(c);
    return c;
  };
  const group = (name, parent, origin, rotation) => {
    const gr = new Group({name, origin, rotation: rotation || [0, 0, 0]});
    if (parent) gr.addTo(parent);
    gr.init();
    return gr;
  };

  // ---- the rig
  const root = group('root', null, [0, 0, 0]);
  const body = group('body', root, [0, 15, 0]);
  // the robe: a narrow chest under a broad mantle, widening down to the hem
  // the core inside the cloak: dark, lit from below by the soul
  cube(body, [-3.5, 11, -2.5], [3.5, 22, 2.5], 'dark');
  // the cloak's panels: two at the front, parted over the core; the back; the sides
  cube(body, [-4.6, 9, -3.6], [-1.5, 22.5, -2.5], 'cloth', {aoTop: 3, shift: 0, fray: true, glow: true, edge: 1});
  cube(body, [1.5, 9, -3.6], [4.6, 22.5, -2.5], 'cloth', {aoTop: 3, shift: 2, fray: true, glow: true, edge: -1});
  cube(body, [-4.6, 9, 2.5], [4.6, 22.5, 3.6], 'cloth', {runes: true, arc: true, aoTop: 3, shift: 1, fray: true, glow: true});
  cube(body, [-4.6, 9, -2.5], [-3.6, 22.5, 2.5], 'cloth', {aoTop: 3, shift: 3, fray: true, glow: true});
  cube(body, [3.6, 9, -2.5], [4.6, 22.5, 2.5], 'cloth', {aoTop: 3, shift: 1, fray: true, glow: true});
  // the mantle, stepped round the shoulders
  cube(body, [-5.4, 21.5, -3.9], [5.4, 23.5, 3.9], 'cloth', {shift: 1});
  cube(body, [-4.6, 23.5, -3.2], [4.6, 24.5, 3.2], 'cloth', {shift: 2});
  // the torn hem: flaps of different lengths round the skirt, each fading into the glow
  [[-5, -4.1, 2.4, 5], [-2.6, -4.1, 2.2, 3.5], [-0.4, -4.1, 2.0, 6], [1.6, -4.1, 1.8, 4], [3.4, -4.1, 1.6, 5.5],
    [-5, 4.0, 2.4, 4.5], [-2.6, 4.0, 2.4, 6], [-0.2, 4.0, 2.2, 3.5], [2.0, 4.0, 3.0, 5]]
    .forEach(([x, z, w, len]) => cube(body, [x, 10 - len, z], [x + w, 10.5, z + 0.6], 'hem'));
  [[-5.1, -3, 2.2, 5], [-5.1, -0.6, 2.2, 3.5], [-5.1, 1.8, 2.2, 5.5], [4.5, -3, 2.2, 4], [4.5, -0.6, 2.4, 6], [4.5, 1.9, 2, 4.5]]
    .forEach(([x, z, w, len]) => cube(body, [x, 10 - len, z], [x + 0.6, 10.5, z + w], 'hem'));
  // the hood, deep and a little oversized, its point falling back
  const hood = group('hood', body, [0, 24, 0]);
  cube(hood, [-4.9, 24, -3.6], [4.9, 26, 4.8], 'cloth', {shift: 2, noLip: true});                 // the collar
  cube(hood, [-4.5, 25.5, -4.4], [4.5, 33, 4.5], {north: 'hoodFront', _: 'cloth'}, {shift: 1, runes: true, side: true});    // the cowl
  cube(hood, [-4.8, 32, -5.4], [4.8, 33.4, -4.3], 'cloth', {shift: 3});                             // the brim
  cube(hood, [-5, 26, -5.1], [-4.4, 32.2, -2.5], 'cloth', {shift: 0, fray: true});                  // the cheeks, draping
  cube(hood, [4.4, 26, -5.1], [5, 32.2, -2.5], 'cloth', {shift: 2, fray: true});
  cube(hood, [-3.8, 33, -3.8], [3.8, 34.2, 4], 'cloth', {shift: 1});                                // the crown
  cube(hood, [-4.5, 32.4, -4.4], [-3.8, 33.4, 4.5], 'cloth', {shift: 2});                           // its rounded shoulders
  cube(hood, [3.8, 32.4, -4.4], [4.5, 33.4, 4.5], 'cloth', {shift: 2});
  cube(hood, [-2.6, 34.2, -2.4], [2.6, 34.9, 3], 'cloth', {shift: 2});
  cube(hood, [-1.6, 31.5, 4.4], [1.6, 34, 7.2], 'cloth', null, [-32, 0, 0], [0, 32.5, 4.4]);       // the point, falling back
  cube(hood, [-0.9, 30, 6.6], [0.9, 32, 8.4], 'cloth', {fray: true}, [-50, 0, 0], [0, 31.5, 6.8]);
  group('eyes', hood, [0, 28.5, -4.5]);   // the eyes are painted on the hood; this bone only squints them

  // the bell, a translucent dome inside the hem, and the tendrils
  const bell = group('bell', root, [0, 9, 0]);
  cube(bell, [-4.5, 6, -3.5], [4.5, 10, 3.5], 'jelly');
  cube(bell, [-3.5, 5, -2.5], [3.5, 6, 2.5], 'jelly');
  const T = [[-3.5, -2.5], [-1.2, -3], [1.2, -3], [3.5, -2.5], [-3.5, 2.5], [-1.2, 3], [1.2, 3], [3.5, 2.5], [-1.8, 0], [1.8, 0]];
  T.forEach(([x, z], i) => {
    const la = 4 + (i * 5) % 3, lb = 5 + (i * 7) % 4;
    const a = group(`tendril_${i}_a`, bell, [x, 5.5, z]);
    cube(a, [x - 0.45, 5.5 - la, z - 0.45], [x + 0.45, 5.5, z + 0.45], 'tendril', {k0: 0, k1: 0.45});
    const b = group(`tendril_${i}_b`, a, [x, 5.5 - la, z]);
    cube(b, [x - 0.3, 5.5 - la - lb, z - 0.3], [x + 0.3, 5.5 - la, z + 0.3], 'tendril', {k0: 0.45, k1: 1});
  });

  // the arms: a hanging sleeve, a thin bone arm out of it, a bony hand with long fingers
  for (const s of [-1, 1]) {
    const side = s < 0 ? 'r' : 'l', X = s * 6.2;
    const arm = group('arm_' + side, body, [s * 5.2, 23.5, 0], [0, 0, s * 12]);
    cube(arm, [X - 1.7, 19, -1.9], [X + 1.7, 24, 1.9], 'cloth', {shift: 3, aoTop: 2});     // the sleeve
    cube(arm, [X - 1.9, 16, -2.1], [X + 1.9, 19.2, 2.1], 'cloth', {shift: 1, fray: true}); // its wide, torn mouth
    cube(arm, [X - 1.2, 16.5, -1.4], [X + 1.2, 18.6, 1.4], 'dark');                          // the dark inside it
    cube(arm, [X - 0.6, 15.5, -0.6], [X + 0.6, 19.5, 0.6], 'bone');
    const fore = group('forearm_' + side, arm, [X, 15.5, 0], [s < 0 ? -28 : -10, 0, -s * 10]);
    cube(fore, [X - 0.5, 11, -0.5], [X + 0.5, 15.5, 0.5], 'bone');
    cube(fore, [X - 0.9, 9.8, -0.9], [X + 0.9, 11, 0.9], 'bone');                // the palm
    for (const f of [-0.6, 0, 0.6]) cube(fore, [X + f - 0.2, 8.4, -0.7], [X + f + 0.2, 9.8, -0.3], 'bone');   // the fingers
    if (s < 0) {
      const chain = group('chain', fore, [X, 9.6, 0], [28, 0, -8]);
      cube(chain, [X - 0.3, 6, -0.3], [X + 0.3, 9.6, 0.3], 'chain');
      const lantern = group('lantern', chain, [X, 6, 0]);
      cube(lantern, [X - 0.6, 5.8, -0.2], [X + 0.6, 6.6, 0.2], 'iron');                 // the ring
      cube(lantern, [X - 2.2, 4.6, -2.2], [X + 2.2, 5.3, 2.2], 'iron');                 // the cap
      cube(lantern, [X - 1.3, 5.3, -1.3], [X + 1.3, 5.9, 1.3], 'iron');
      cube(lantern, [X - 1.7, 0.6, -1.7], [X + 1.7, 4.6, 1.7], 'glass');               // the soul flame behind glass
      for (const [a, b] of [[-1, -1], [1, -1], [-1, 1], [1, 1]])                         // the frame's posts
        cube(lantern, [X + a * 1.85 - 0.3, 0.6, b * 1.85 - 0.3], [X + a * 1.85 + 0.3, 4.6, b * 1.85 + 0.3], 'iron');
      cube(lantern, [X - 2.2, -0.2, -2.2], [X + 2.2, 0.6, 2.2], 'iron');               // the base
    }
  }

  const tex = new Texture({name: 'lampkeeper'}).fromDataURL(cv.toDataURL()).add(false);
  cubes.forEach(c => { for (const k in c.faces) c.faces[k].texture = tex.uuid; });
  Canvas.updateAll();
  fs.writeFileSync(OUT + 'lampkeeper.png', Buffer.from(cv.toDataURL().split(',')[1], 'base64'));
  fs.writeFileSync(OUT + 'lampkeeper_glowmask.png', Buffer.from(gm.toDataURL().split(',')[1], 'base64'));
  return {cubes: Cube.all.length, groups: Group.all.length, sheetRows: cy + rowH};
})()
