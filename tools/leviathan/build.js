// Leviathan of the Abyss - built inside Blockbench through the MCP (risky_eval), GeckoLib format.
// Units: Blockbench pixels (16 = one block). The head points north (-Z), the tail runs south (+Z).
// Rig: root > head (jaw, eyelids, whiskers, head tentacles) and root > body_0 > body_1 > ... > body_11 > tail_fin,
// each body segment the child of the one before, so the game can bend the chain procedurally.
(() => {
  if (Modes.options.edit && !Modes.edit) Modes.options.edit.select();
  Animation.all.slice().forEach(a => a.remove(false));
  Cube.all.slice().forEach(c => c.remove());
  Group.all.slice().forEach(gr => gr.remove(false));
  Texture.all.slice().forEach(t => t.remove(true));
  Undo.initEdit({outliner: true, elements: [], textures: []});
  Project.box_uv = false;
  Project.texture_width = 256;
  Project.texture_height = 256;

  // ---------------------------------------------------------------- the texture atlas (swatches, mapped one texel per pixel)
  const cv = document.createElement('canvas');
  cv.width = 256; cv.height = 256;
  const g = cv.getContext('2d');
  let seed = 7;
  const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const fill = (x, y, w, h, c) => { g.fillStyle = c; g.fillRect(x, y, w, h); };
  const hideNoise = (x, y, w, h, base, spread) => {
    for (let i = 0; i < w; i++) for (let j = 0; j < h; j++) {
      const k = base + Math.round((rnd() - 0.5) * spread);
      fill(x + i, y + j, 1, 1, `rgb(${k},${k + 2},${k + 7})`);
    }
  };
  const rgb = (r, g2, b) => `rgb(${Math.max(0, Math.min(255, Math.round(r)))},${Math.max(0, Math.min(255, Math.round(g2)))},${Math.max(0, Math.min(255, Math.round(b)))})`;
  // the hide, pixel art in Minecraft's manner: a five-tone palette, scales drawn as rounded shields - lit along the
  // top, shaded to a dark rim - laid in rows half a scale over; now and then a scale a shade lighter or darker, so the
  // hide does not read as a grid
  const HIDE = {o: [12, 13, 19], d: [22, 24, 32], m: [32, 35, 46], l: [45, 49, 63], h: [62, 68, 86]};
  const SCALE = ['ohhhhhho', 'dllllllm', 'dmmmmmmd', 'dmmmmmmd', 'odmmmmdo', 'oodddooo'];
  const shades = ['o', 'd', 'm', 'l', 'h'];
  const scalesPx = (x0, y0, w, h) => {
    for (let j = 0; j < h; j++) {
      const row = Math.floor(j / 6), off = (row % 2) * 4;
      for (let i = 0; i < w; i++) {
        const col = Math.floor((i + off) / 8);
        const k = SCALE[j % 6][(i + off) % 8];
        // each scale's own small shift in tone, from where it sits
        const vary = ((col * 7 + row * 13) % 11 === 0) ? 1 : ((col * 5 + row * 3) % 13 === 0) ? -1 : 0;
        const idx = Math.max(0, Math.min(4, shades.indexOf(k) + (k === 'o' ? 0 : vary)));
        const c = HIDE[shades[idx]];
        fill(x0 + i, y0 + j, 1, 1, rgb(c[0], c[1], c[2]));
      }
    }
  };
  scalesPx(0, 0, 128, 64);                 // TOP
  scalesPx(0, 64, 128, 64);                // SIDE, with one row of photophores along it
  // BELLY: broad plates, Minecraft-flat - a lit lip, a body, a shadowed seam
  const BELLY = [[56, 60, 72], [44, 48, 59], [40, 43, 54], [40, 43, 54], [35, 38, 48], [24, 26, 34]];
  for (let j = 0; j < 64; j++) for (let i = 0; i < 128; i++) {
    const c = BELLY[j % 6];
    const notch = (i + Math.floor(j / 6) * 5) % 16 === 0 && j % 6 > 0 ? -8 : 0;
    fill(128 + i, j, 1, 1, rgb(c[0] + notch, c[1] + notch, c[2] + notch));
  }
  for (let x = 3; x < 128; x += 8) {
    // a small cold light with a faint halo
    fill(x - 1, 95, 3, 3, rgb(48, 62, 78));
    fill(x, 95, 1, 3, rgb(120, 150, 175));
    fill(x - 1, 96, 3, 1, rgb(120, 150, 175));
    fill(x, 96, 1, 1, '#f2fbff');
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
  // GUM: the inside of the maw - dark red-violet flesh in ridges (as a palate's), each ridge lit along its crest,
  // deep wet grooves between, glints of slime
  for (let i = 0; i < 48; i++) for (let j = 0; j < 48; j++) {
    const ridge = (j + Math.round(Math.sin(i * 0.3) * 1.5)) % 6;
    let r = 74, gg = 22, b = 40;
    if (ridge === 0) { r = 122; gg = 44; b = 62; }
    else if (ridge === 1) { r = 96; gg = 32; b = 50; }
    else if (ridge === 4) { r = 44; gg = 12; b = 26; }
    else if (ridge === 5) { r = 30; gg = 8; b = 18; }
    const n = (rnd() - 0.5) * 10;
    const glint = rnd() < 0.015 ? 60 : 0;
    fill(208 + i, 64 + j, 1, 1, rgb(r + n + glint, gg + n + glint, b + n + glint));
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
  // FANGS: two strips of flat, hand-drawn fangs for the 2D tooth rows - uneven widths and lengths, some broken
  // short, a few gaps; a stained root, an old-ivory shaft, a paler point, a dark edge. FANGS_DN is the same set
  // turned point-up for the lower jaw
  const fangStrip = (x0, y0, up) => {
    let x = x0 + 1;
    while (x < x0 + 158) {
      const w = 3 + Math.floor(rnd() * 4), broken = rnd() < 0.12;
      const len = broken ? 4 + Math.floor(rnd() * 3) : 7 + Math.floor(rnd() * 9);
      for (let j = 0; j < len; j++) {
        const f = j / len, half = (w / 2) * (1 - f * 0.85);
        for (let i = 0; i < w; i++) {
          const dx = i + 0.5 - w / 2;
          if (Math.abs(dx) > half + 0.25) continue;
          const edge = Math.abs(dx) > half - 0.75;
          const base = [118 + f * 92, 104 + f * 82, 70 + f * 70];
          const k = edge ? -34 : (i === 0 ? 12 : 0) + (j < 2 ? -26 : 0);
          fill(x + i, up ? y0 + 15 - j : y0 + j, 1, 1, rgb(base[0] + k, base[1] + k, base[2] + k));
        }
      }
      x += w + (rnd() < 0.35 ? 2 + Math.floor(rnd() * 4) : 1);
    }
  };
  fangStrip(64, 128, false);
  fangStrip(64, 144, true);
  const S = {
    FANGS: [64, 128, 224, 144], FANGS_DN: [64, 144, 224, 160],
    TOP: [0, 0, 128, 64], SIDE: [0, 64, 128, 128], BELLY: [128, 0, 256, 64], LID: [128, 64, 192, 128], TEETH: [192, 64, 208, 80], TEETH_UP: [192, 80, 208, 64],
    GUM: [208, 64, 256, 112], EYE: [192, 80, 208, 96], TIP: [192, 96, 208, 112], THROAT: [0, 128, 64, 192],
  };
  const tex = new Texture({name: 'leviathan'}).fromDataURL(cv.toDataURL()).add(false);

  // ---------------------------------------------------------------- helpers
  const group = (name, parent, origin, rotation) => {
    const gr = new Group({name, origin, rotation: rotation || [0, 0, 0]});
    if (parent) gr.addTo(parent);
    gr.init();
    return gr;
  };
  const hide = {up: 'TOP', down: 'BELLY', east: 'SIDE', west: 'SIDE', north: 'TOP', south: 'TOP'};
  const cube = (name, parent, from, to, skin, origin, rotation) => {
    const c = new Cube({name, from, to, origin: origin || from, rotation: rotation || [0, 0, 0]}).addTo(parent).init();
    const size = [to[0] - from[0], to[1] - from[1], to[2] - from[2]];
    const dims = {north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2]};
    for (const k of ['north', 'south', 'east', 'west', 'up', 'down']) {
      const sw = typeof skin === 'string' ? skin : (skin || hide)[k];
      const r = S[sw], rw = r[2] - r[0], rh = r[3] - r[1];
      if (rw <= 16) {
        c.faces[k].uv = r.slice();
      } else {
        const w = Math.min(rw, Math.max(1, size[dims[k][0]])), h = Math.min(rh, Math.max(1, size[dims[k][1]]));
        const ox = r[0] + Math.floor((rw - w) * rnd()), oy = r[1] + (sw === 'SIDE' ? Math.max(0, Math.floor((rh - h) / 2)) : Math.floor((rh - h) * rnd()));
        c.faces[k].uv = [ox, oy, ox + w, oy + h];
      }
      c.faces[k].texture = tex.uuid;
    }
    return c;
  };
  const CY = 24;   // the centre line's height

  const root = group('root', null, [0, CY, 0]);

  // ---------------------------------------------------------------- the head
  const head = group('head', root, [0, CY, -2]);
  cube('cranium', head, [-24, CY + 4, -60], [24, CY + 26, 4]);
  cube('cranium_back', head, [-21, CY - 8, -24], [21, CY + 6, 6]);
  cube('snout', head, [-18, CY + 4, -96], [18, CY + 20, -58]);
  cube('snout_ridge', head, [-12, CY + 20, -92], [12, CY + 24, -56]);
  cube('snout_tip', head, [-13, CY + 5, -106], [13, CY + 16, -94]);
  cube('crest', head, [-8, CY + 26, -52], [8, CY + 30, 0]);
  cube('brow_l', head, [11, CY + 22, -62], [24, CY + 27, -18], null, [17, CY + 24, -40], [0, 0, -14]);
  cube('brow_r', head, [-24, CY + 22, -62], [-11, CY + 27, -18], null, [-17, CY + 24, -40], [0, 0, 14]);
  cube('palate', head, [-17, CY + 2, -104], [17, CY + 5, -8], 'GUM');
  // the upper teeth: flat rows of fangs hung along the lips, just outside the lower jaw so the mouth can shut over it
  for (const sx of [-1, 1]) cube(`fangs_u_${sx > 0 ? 'l' : 'r'}`, head, [sx * 18.3, CY - 12, -104], [sx * 18.3, CY + 4, -12], 'FANGS');
  cube('fangs_u_front', head, [-13, CY - 11, -106.3], [13, CY + 5, -106.3], 'FANGS');
  // eyes: three a side along the skull, each smaller than the one before. Each shuts like a sliding door: two leaves
  // meet in the middle from the front and the back (the game squashes them away to open, stretches them to blink)
  const eyes = [[-48, 8, 18], [-39, 6, 17.5], [-32, 4, 17]];
  eyes.forEach(([z, size, y], n) => {
    for (const s of [-1, 1]) {
      const side = (s > 0 ? 'l' : 'r') + n;
      const ex = s > 0 ? 23.5 : -25.5, y0 = CY + y - size / 2, y1 = CY + y + size / 2;
      const eye = group(`eye_${side}`, head, [s * 24, CY + y, z]);
      cube(`eyeball_${side}`, eye, [ex, y0, z - size / 2], [ex + 2, y1, z + size / 2], 'EYE');
      const x0 = s > 0 ? 25.5 : -26.5, x1 = s > 0 ? 26.5 : -25.5;
      const front = group(`door_${side}_f`, head, [s * 26, CY + y, z - size / 2 - 0.3]);
      cube(`door_${side}_f_c`, front, [x0, y0 - 0.3, z - size / 2 - 0.3], [x1, y1 + 0.3, z], 'LID');
      const back = group(`door_${side}_b`, head, [s * 26, CY + y, z + size / 2 + 0.3]);
      cube(`door_${side}_b_c`, back, [x0, y0 - 0.3, z], [x1, y1 + 0.3, z + size / 2 + 0.3], 'LID');
    }
  });
  // the lower jaw, hinged at the back of the skull, hanging wide open at rest
  const jaw = group('jaw', head, [0, CY + 2, -10], [-10, 0, 0]);
  cube('jaw_bone', jaw, [-18, CY - 8, -104], [18, CY + 2, -10]);
  cube('jaw_chin', jaw, [-13, CY - 11, -106], [13, CY - 4, -84]);
  cube('jaw_floor', jaw, [-16, CY + 2, -102], [16, CY + 3, -12], 'GUM');
  cube('throat', jaw, [-10, CY, -16], [10, CY + 3, -4], 'THROAT');
  // the lower teeth: flat rows standing up inside the lips of the jaw
  for (const sx of [-1, 1]) cube(`fangs_d_${sx > 0 ? 'l' : 'r'}`, jaw, [sx * 17.4, CY - 2, -102], [sx * 17.4, CY + 14, -12], 'FANGS_DN');
  cube('fangs_d_front', jaw, [-12, CY - 2, -105.6], [12, CY + 14, -105.6], 'FANGS_DN');
  // whiskers: thin feelers from the snout, three segments, glowing tips
  const whiskers = [[1, CY + 8, -78, -40, -30], [-1, CY + 8, -78, -40, 30], [1, CY + 4, -70, 10, -55], [-1, CY + 4, -70, 10, 55]];
  whiskers.forEach(([s, y, z, pitch, yaw], w) => {
    let parent = head, at = [s * 15, y, z];
    for (let k = 0; k < 3; k++) {
      const seg = group(`whisker_${w}_${k}`, parent, at.slice(), k === 0 ? [pitch, yaw, 0] : [8, s * 6, 0]);
      const len = 16 - k * 3, t = 1.6 - k * 0.3;
      cube(`whisker_${w}_${k}_c`, seg, [at[0] - t / 2, at[1] - t / 2, at[2] - len], [at[0] + t / 2, at[1] + t / 2, at[2]]);
      if (k === 2) cube(`whisker_${w}_tip`, seg, [at[0] - 1, at[1] - 1, at[2] - len - 2], [at[0] + 1, at[1] + 1, at[2] - len], 'TIP');
      parent = seg;
      at = [at[0], at[1], at[2] - len];
    }
  });
  // two feelers on top of the skull, rising and curling back over the body
  for (const s of [-1, 1]) {
    const w = s > 0 ? 4 : 5;
    let parent = head, at = [s * 7, CY + 30, -40];
    for (let k = 0; k < 4; k++) {
      const seg = group(`whisker_${w}_${k}`, parent, at.slice(), k === 0 ? [-62, s * 10, 0] : [16, s * 3, 0]);
      const len = 22 - k * 3, t = 2 - k * 0.3;
      cube(`whisker_${w}_${k}_c`, seg, [at[0] - t / 2, at[1] - t / 2, at[2]], [at[0] + t / 2, at[1] + t / 2, at[2] + len]);
      if (k === 3) cube(`whisker_${w}_tip`, seg, [at[0] - 1.2, at[1] - 1.2, at[2] + len], [at[0] + 1.2, at[1] + 1.2, at[2] + len + 2.4], 'TIP');
      parent = seg;
      at = [at[0], at[1], at[2] + len];
    }
  }
  // the head tentacles: long segmented arms sweeping back from the skull, as a crown
  const arms = [
    {x: 13, y: 16, yaw: 44, pitch: -20}, {x: -13, y: 16, yaw: -44, pitch: -20},
    {x: 20, y: 6, yaw: 66, pitch: -6}, {x: -20, y: 6, yaw: -66, pitch: -6},
    {x: 15, y: -6, yaw: 52, pitch: 24}, {x: -15, y: -6, yaw: -52, pitch: 24},
  ];
  arms.forEach((arm, a) => {
    let parent = head;
    let at = [arm.x, CY + 6 + arm.y, -16];
    const sgn = Math.sign(arm.x);
    for (let k = 0; k < 5; k++) {
      // each link bends a little back toward the tail, so the crown sweeps backward
      const seg = group(`arm_${a}_${k}`, parent, at.slice(), k === 0 ? [arm.pitch, arm.yaw, 0] : [arm.pitch > 0 ? -5 : 4, -sgn * 9, 0]);
      const len = 58 - k * 5, w = 21 - k * 2.6;
      cube(`arm_${a}_${k}_c`, seg, [at[0] - w / 2, at[1] - w / 2, at[2]], [at[0] + w / 2, at[1] + w / 2, at[2] + len]);
      if (k === 4) cube(`arm_${a}_tip`, seg, [at[0] - 2.5, at[1] - 2.5, at[2] + len], [at[0] + 2.5, at[1] + 2.5, at[2] + len + 10], 'TIP');
      parent = seg;
      at = [at[0], at[1], at[2] + len];
    }
  });

  // ---------------------------------------------------------------- the body: a chain of segments to the tail
  const SEGS = 12, LEN = 44;
  let parent = root;
  for (let i = 0; i < SEGS; i++) {
    const z = 2 + i * LEN;
    const f = i / (SEGS - 1);
    const w = 46 - f * 34, hgt = 40 - f * 30;
    const seg = group(`body_${i}`, parent, [0, CY + 6, z]);
    cube(`body_${i}_c`, seg, [-w / 2, CY + 6 - hgt / 2, z], [w / 2, CY + 6 + hgt / 2, z + LEN + 1]);
    // a ridge of spines down the back
    const sh = 6 - f * 4;
    cube(`spine_${i}`, seg, [-1.5, CY + 6 + hgt / 2, z + 8], [1.5, CY + 6 + hgt / 2 + sh, z + 16], null, [0, CY + 6 + hgt / 2, z + 12], [-20, 0, 0]);
    // little side fins on every third segment
    if (i % 3 === 1) {
      for (const s of [-1, 1]) {
        cube(`fin_${i}_${s > 0 ? 'l' : 'r'}`, seg, [s > 0 ? w / 2 : -w / 2 - 10, CY + 4, z + 10], [s > 0 ? w / 2 + 10 : -w / 2, CY + 5, z + 26],
          null, [s * w / 2, CY + 4, z + 18], [0, s * 25, s * -10]);
      }
    }
    parent = seg;
  }
  const tail = group('tail_fin', parent, [0, CY + 6, 2 + SEGS * LEN]);
  cube('tail_fin_c', tail, [-14, CY + 5, 2 + SEGS * LEN], [14, CY + 7, 2 + SEGS * LEN + 26]);
  cube('tail_fin_tip', tail, [-6, CY + 5.5, 2 + SEGS * LEN + 26], [6, CY + 6.5, 2 + SEGS * LEN + 34]);

  Canvas.updateAll();
  Undo.finishEdit('Build the Leviathan');
  return {groups: Group.all.length, cubes: Cube.all.length};
})()
