// The Clam Mimic - built in Blockbench through the MCP (risky_eval), GeckoLib entity. Concept: art/mobs/clam_2.png.
// A giant clam lurking by the catacombs' chests. Every face painted for itself on a 256x256 sheet: the shell's
// rounded halves in stepped layers with concentric growth bands and the fan ribs, the thick cream lip, barnacles,
// coral and weed on the top; inside, dark violet flesh, rings of cream teeth, a glowing pearl eye on a stalk and a
// thick ridged tongue. Faces north (-Z, its mouth); the hinge at the back.
// Bones: root > shell_bottom (flesh, teeth_bottom, eye > pearl, tongue_1 > tongue_2 > tongue_3 > tongue_4),
//        root > shell_top (pivot on the hinge; teeth_top, barnacles, coral, weed).
(() => {
  const fs = require('fs');
  const OUT = 'C:/Users/janat/GameMakerProjects/minecraftmod/tools/mobs/';
  if (!Project || Project.name !== 'clam_mimic') newProject(Formats.geckolib_model);
  Animation.all.slice().forEach(a => a.remove(false));
  Cube.all.slice().forEach(c => c.remove());
  Group.all.slice().forEach(gr => gr.remove(false));
  Texture.all.slice().forEach(t => t.remove(true));
  Project.name = 'clam_mimic';
  Project.box_uv = false;
  const W = 256;
  Project.texture_width = W;
  Project.texture_height = W;

  const cv = document.createElement('canvas'); cv.width = W; cv.height = W;
  const g = cv.getContext('2d');
  const gm = document.createElement('canvas'); gm.width = W; gm.height = W;
  const gg = gm.getContext('2d');
  const rgba = (c, a) => `rgba(${c[0]},${c[1]},${c[2]},${a === undefined ? 1 : a})`;
  const px = (x, y, c, a, glow) => {
    g.fillStyle = rgba(c, a); g.fillRect(x, y, 1, 1);
    if (glow) { gg.fillStyle = rgba(c, glow); gg.fillRect(x, y, 1, 1); }
  };
  const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
  const hash = (x, y) => ((x * 73856093) ^ (y * 19349663)) >>> 0;

  const SHELL = {dk: [36, 48, 58], base: [54, 70, 84], lit: [74, 92, 106], hi: [98, 118, 130]};
  const CREAM = {hi: [236, 228, 204], base: [216, 206, 178], sh: [168, 158, 132]};
  const FLESH = {dk: [34, 12, 40], base: [62, 24, 70], lit: [96, 42, 106]};
  const TONGUE = {sh: [92, 36, 98], base: [124, 56, 132], hi: [160, 88, 168]};

  const P = {
    // the shell: concentric growth bands across, the fan ribs down from the hinge, a little grime
    shell: (x0, y0, w, h, f, o) => {
      o = o || {};
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const band = (y + (o.band || 0)) % 3;
        let c = band === 0 ? SHELL.lit : band === 1 ? SHELL.base : SHELL.dk;
        if (f === 'up') {
          // seen from above: rings round the top and ribs fanning out from the back
          const cx = (w - 1) / 2, d = Math.round(Math.hypot(x - cx, (y - h) * 1.1));
          c = d % 4 === 0 ? mix(SHELL.base, SHELL.dk, 0.6) : d % 4 === 2 ? mix(SHELL.base, SHELL.lit, 0.6) : SHELL.base;
          if (Math.abs(x - cx) < 1 || Math.abs(Math.abs(x - cx) - w * 0.25) < 0.6) c = mix(c, SHELL.hi, 0.5);
        } else if (f === 'north' || f === 'south') {
          // the ribs on the front and back faces: a lighter column now and then
          if (x % 5 === 2) c = mix(c, SHELL.hi, 0.35);
        }
        if (hash(x0 + x, y0 + y) % 71 === 0) c = mix(c, [70, 90, 60], 0.45);   // grime, a little green, here and there
        if (f !== 'up' && f !== 'down' && o.cream !== undefined && y === (o.cream < 0 ? h - 1 : o.cream)) c = CREAM.base;
        if (f === 'down') c = SHELL.dk;
        px(x0 + x, y0 + y, c);
      }
    },
    // the lip: thick cream, lit on top, a dark line under it
    lip: (x0, y0, w, h, f) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        let c = y === 0 ? CREAM.hi : y === h - 1 ? CREAM.sh : CREAM.base;
        if (f === 'up' || f === 'down') c = (x + y) % 4 === 0 ? CREAM.sh : CREAM.base;
        if (hash(x0 + x, y0 + y) % 41 === 0) c = CREAM.sh;
        px(x0 + x, y0 + y, c);
      }
    },
    seam: (x0, y0, w, h) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) px(x0 + x, y0 + y, y === 0 ? [96, 40, 100] : [52, 18, 58]); },
    flesh: (x0, y0, w, h, f) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const d = Math.hypot(x - (w - 1) / 2, y - (h - 1) / 2) / Math.max(w, h) * 2;
        let c = mix(FLESH.lit, FLESH.dk, Math.min(1, d * 1.2));
        if ((x + 2 * y) % 7 === 0) c = mix(c, FLESH.dk, 0.4);   // wet folds
        px(x0 + x, y0 + y, c);
      }
    },
    tooth: (x0, y0, w, h) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) px(x0 + x, y0 + y, x === 0 ? CREAM.hi : y === h - 1 ? CREAM.sh : CREAM.base); },
    tongue: (x0, y0, w, h, f) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        let c = (f === 'up') ? (y % 3 === 0 ? TONGUE.sh : x === (w >> 1) ? TONGUE.sh : TONGUE.hi) : (y === 0 ? TONGUE.hi : TONGUE.base);
        if (f === 'down') c = TONGUE.sh;
        px(x0 + x, y0 + y, c);
      }
    },
    stalk: (x0, y0, w, h) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) px(x0 + x, y0 + y, x === 0 ? TONGUE.hi : TONGUE.sh); },
    pearl: (x0, y0, w, h, f) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const d = Math.hypot(x - (w - 1) * 0.35, y - (h - 1) * 0.35) / Math.max(w, h);
        px(x0 + x, y0 + y, mix([250, 255, 255], [120, 200, 205], Math.min(1, d * 1.6)), 1, 1);
      }
      if (f === 'north' && w >= 3) { px(x0 + (w >> 1), y0 + (h >> 1), [20, 50, 60], 1, 0.4); }   // its pupil, looking out
    },
    barnacle: (x0, y0, w, h, f) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        let c = (x + y) % 3 === 0 ? CREAM.sh : CREAM.base;
        if (f === 'up' && x > 0 && y > 0 && x < w - 1 && y < h - 1) c = [40, 36, 34];   // its open mouth
        px(x0 + x, y0 + y, c);
      }
    },
    coral: (x0, y0, w, h) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) px(x0 + x, y0 + y, y === 0 ? [255, 140, 140] : (x + y) % 2 ? [220, 88, 96] : [190, 64, 78]); },
    weed: (x0, y0, w, h) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) px(x0 + x, y0 + y, (x + y) % 2 ? [90, 130, 56] : [66, 104, 44], y === 0 && x % 2 ? 0 : 1); },
  };

  let cx = 0, cy = 0, rowH = 0;
  const patch = (w, h) => {
    if (cx + w > W) { cx = 0; cy += rowH; rowH = 0; }
    const at = [cx, cy];
    cx += w; rowH = Math.max(rowH, h);
    return at;
  };
  const dims = {north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2]};
  const cubes = [];
  const fang = (parent, x, z, y0, len, up) => {
    const b = len * 0.55;
    if (up) {
      cube(parent, [x - 0.4, y0, z - 0.35], [x + 0.4, y0 + b, z + 0.35], 'tooth');
      cube(parent, [x - 0.2, y0 + b, z - 0.18], [x + 0.2, y0 + len, z + 0.18], 'tooth');
    } else {
      cube(parent, [x - 0.4, y0 - b, z - 0.35], [x + 0.4, y0, z + 0.35], 'tooth');
      cube(parent, [x - 0.2, y0 - len, z - 0.18], [x + 0.2, y0 - b, z + 0.18], 'tooth');
    }
  };
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
  // a rounded slab: a long cross of two boxes and the corners cut, so the outline reads as an oval
  const slab = (parent, y0, y1, hx, hz, cut, paint, opt) => {
    cube(parent, [-hx, y0, -hz + cut], [hx, y1, hz - cut], paint, opt);
    cube(parent, [-hx + cut, y0, -hz], [hx - cut, y1, -hz + cut], paint, opt);
    cube(parent, [-hx + cut, y0, hz - cut], [hx - cut, y1, hz], paint, opt);
  };

  const root = group('root', null, [0, 0, 0]);
  // ---- the lower half: stepped up to its lip
  const bot = group('shell_bottom', root, [0, 0, 0]);
  [[0, 0.8, 5.6, 4.6, 1.6], [0.8, 1.6, 7, 6, 1.8], [1.6, 2.6, 8.1, 7.1, 2], [2.6, 3.7, 8.8, 7.8, 2.2], [3.7, 4.5, 9.2, 8.2, 2.3]]
    .forEach(([y0, y1, hx, hz, cut], i) => slab(bot, y0, y1, hx, hz, cut, 'shell', {band: i}));
  slab(bot, 4.5, 5.2, 9.5, 8.5, 2.4, 'lip');
  slab(bot, 5.0, 5.3, 8, 7, 2.6, 'flesh');                     // the flesh it holds
  // the lower teeth (a bone of their own: they sink when it shuts)
  const tb = group('teeth_bottom', bot, [0, 5.2, 0]);
  for (let i = 0; i < 8; i++) {
    const x = -5.4 + i * 1.55;
    fang(tb, x, -7.55, 5.1, 1.3 + (i % 2) * 0.4, true);
  }
  for (const s of [-1, 1]) for (let i = 0; i < 4; i++) {
    const z = -4.2 + i * 2.6;
    fang(tb, s * 7.6, z, 5.1, 1.1 + (i % 2) * 0.3, true);
  }
  // the pearl eye on its stalk, rising from the flesh (it lies low while the shell is shut)
  const eye = group('eye', bot, [0, 5.2, -1]);
  cube(eye, [-0.5, 5.2, -1.5], [0.5, 8.4, -0.5], 'stalk');
  const pearl = group('pearl', eye, [0, 9.2, -1]);
  cube(pearl, [-1.4, 8.2, -2.4], [1.4, 11, 0.4], 'pearl');
  // the tongue: four ridged segments lying in the flesh, ready to shoot out of the front
  let tz = 3, prev = bot;
  for (let i = 1; i <= 4; i++) {
    const t = group('tongue_' + i, prev, [0, 5.3, tz]);
    const w = 3 - i * 0.4;
    cube(t, [-w / 2, 5.2, tz - 2.6], [w / 2, 6.3 - i * 0.12, tz], 'tongue');
    prev = t;
    tz -= 2.6;
  }

  // ---- the upper half: on the hinge at the back
  const top = group('shell_top', root, [0, 5.4, 8]);
  slab(top, 5.2, 5.45, 9.3, 8.3, 2.3, 'seam');                         // the purple line where the halves meet
  slab(top, 5.4, 6.1, 9.5, 8.5, 2.4, 'lip');
  [[6.1, 7.2, 9.1, 8.1, 2.3], [7.2, 8.3, 8.6, 7.6, 2.2], [8.3, 9.3, 7.9, 6.9, 2.1], [9.3, 10.2, 7, 6, 2], [10.2, 11, 5.9, 4.9, 1.8],
    [11, 11.7, 4.6, 3.8, 1.5], [11.7, 12.2, 3.1, 2.5, 1]]
    .forEach(([y0, y1, hx, hz, cut], i) => slab(top, y0, y1, hx, hz, cut, 'shell', {band: i}));
  slab(top, 5.3, 5.5, 8, 7, 2.6, 'flesh');                       // its underside, flesh
  const tt = group('teeth_top', top, [0, 5.4, 0]);                      // the upper teeth, hanging
  for (let i = 0; i < 9; i++) {
    const x = -6.2 + i * 1.55;
    fang(tt, x, -7.55, 5.5, 1.4 + (i % 2) * 0.4, false);
  }
  for (const s of [-1, 1]) for (let i = 0; i < 4; i++) {
    const z = -3.6 + i * 2.6;
    fang(tt, s * 7.6, z, 5.5, 1.2 + (i % 2) * 0.3, false);
  }
  // what grows on it: barnacles, a tuft of coral, weed
  [[-4.8, 10.2, -3.2, 1.6], [3.2, 11.0, 1.5, 1.4], [6.4, 8.3, -4.2, 1.3], [-7.2, 8.3, 3.2, 1.5], [0.8, 12.2, -1.0, 1.0], [-2.2, 11.0, 3.0, 1.1]]
    .forEach(([x, y, z, s]) => cube(top, [x - s / 2, y, z - s / 2], [x + s / 2, y + s * 0.8, z + s / 2], 'barnacle'));
  [[-5.6, 9.3, -4.8], [-4.8, 9.3, -4.2], [-6.2, 9.3, -4.2]].forEach(([x, y, z], i) =>
    cube(top, [x - 0.3, y, z - 0.3], [x + 0.3, y + 2.2 + i * 0.4, z + 0.3], 'coral', null, [i * 12 - 10, 0, (i - 1) * 18], [x, y, z]));
  cube(top, [-6.0, 11.3, -4.6], [-4.8, 11.8, -4.3], 'coral');
  [[7.6, 8.3, 2.5], [8.0, 8.3, 3.4], [6.6, 9.3, 3.0]].forEach(([x, y, z], i) =>
    cube(top, [x - 0.4, y, z - 0.1], [x + 0.4, y + 2.4 - i * 0.3, z + 0.1], 'weed', null, [0, i * 30, (i - 1) * 20], [x, y, z]));

  const tex = new Texture({name: 'clam_mimic'}).fromDataURL(cv.toDataURL()).add(false);
  cubes.forEach(c => { for (const k in c.faces) c.faces[k].texture = tex.uuid; });
  Canvas.updateAll();
  fs.writeFileSync(OUT + 'clam_mimic.png', Buffer.from(cv.toDataURL().split(',')[1], 'base64'));
  fs.writeFileSync(OUT + 'clam_mimic_glowmask.png', Buffer.from(gm.toDataURL().split(',')[1], 'base64'));
  return {cubes: Cube.all.length, groups: Group.all.length, sheetRows: cy + rowH};
})()
