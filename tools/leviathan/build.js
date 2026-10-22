// Leviathan of the Abyss - built inside Blockbench through the MCP (risky_eval), GeckoLib format.
// Units: Blockbench pixels (16 = one block). The head points north (-Z), the tail runs south (+Z).
// Rig: root > head (jaw, eyelids, whiskers, head tentacles) and root > body_0 > body_1 > ... > body_11 > tail_fin,
// each body segment the child of the one before, so the game can bend the chain procedurally.
(() => {
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
  hideNoise(0, 0, 128, 64, 20, 6);         // TOP: wet black hide
  hideNoise(0, 64, 128, 64, 22, 6);        // SIDE: hide with two rows of photophores
  for (const row of [78, 98]) {
    for (let x = 2; x < 128; x += 6) {
      fill(x, row + ((x / 6) % 2 ? 1 : 0), 2, 2, '#eaf6ff');
      fill(x + 2, row + 2 + ((x / 6) % 2 ? 1 : 0), 1, 1, '#7d93a8');
    }
  }
  hideNoise(128, 0, 128, 64, 34, 6);       // BELLY: dark slate in plates
  for (let j = 0; j < 64; j += 8) fill(128, j, 128, 1, '#15171d');
  hideNoise(128, 64, 64, 64, 12, 4);       // LID / dark
  fill(192, 64, 16, 16, '#d9cfae'); fill(192, 64, 16, 3, '#f3ecd2'); fill(192, 77, 16, 3, '#a89c7a');   // TEETH
  for (let i = 0; i < 48; i++) for (let j = 0; j < 48; j++) {   // GUM: pale cold flesh
    const k = 118 + Math.round((rnd() - 0.5) * 24);
    fill(208 + i, 64 + j, 1, 1, `rgb(${k - 25},${k + 18},${k + 48})`);
  }
  fill(192, 80, 16, 16, '#ff9d1f'); fill(194, 82, 12, 12, '#ffc24a'); fill(198, 84, 4, 8, '#1a0d00');  // EYE
  fill(192, 96, 16, 16, '#ff9a3a'); fill(194, 98, 12, 12, '#ffd27a');                                 // TIP
  fill(0, 128, 64, 64, '#22070c');                                                                     // THROAT
  const S = {
    TOP: [0, 0, 128, 64], SIDE: [0, 64, 128, 128], BELLY: [128, 0, 256, 64], LID: [128, 64, 192, 128], TEETH: [192, 64, 208, 80],
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
  cube('snout', head, [-17, CY + 2, -88], [17, CY + 18, -58]);
  cube('snout_tip', head, [-12, CY + 3, -96], [12, CY + 14, -86]);
  cube('crest', head, [-8, CY + 26, -52], [8, CY + 30, 0]);
  cube('brow_l', head, [11, CY + 20, -60], [21, CY + 25, -40], null, [16, CY + 22, -50], [0, 0, -12]);
  cube('brow_r', head, [-21, CY + 20, -60], [-11, CY + 25, -40], null, [-16, CY + 22, -50], [0, 0, 12]);
  // the roof of the mouth and the upper teeth
  cube('palate', head, [-16, CY + 1, -92], [16, CY + 4, -8], 'GUM');
  for (let i = 0; i < 14; i++) {
    const z = -94 + i * 6, len = 8 + (i % 3 === 1 ? 4 : 0) - Math.floor(i / 5) * 2;
    for (const s of [-1, 1]) {
      const x = s * (15.5 - Math.min(4, Math.max(0, 3 - i)) - (i > 9 ? 2 : 0));
      cube(`tooth_u_${s > 0 ? 'l' : 'r'}${i}`, head, [x - 1, CY + 2 - len, z], [x + 1, CY + 2, z + 2], 'TEETH');
    }
  }
  // eyes, set in the sides of the skull, each with a lid that the game closes to blink
  for (const s of [-1, 1]) {
    const side = s > 0 ? 'l' : 'r';
    const ex = s > 0 ? 23.5 : -25.5;
    const eye = group(`eye_${side}`, head, [s * 24, CY + 15, -50]);
    cube(`eyeball_${side}`, eye, [ex, CY + 11, -56], [ex + 2, CY + 19, -44], 'EYE');
    const lid = group(`lid_${side}`, head, [s * 24.8, CY + 19.5, -50]);
    cube(`eyelid_${side}`, lid, [s > 0 ? 24 : -26.2, CY + 11, -56.5], [s > 0 ? 26.2 : -24, CY + 19.5, -43.5], 'LID');
  }
  // the lower jaw, hinged at the back of the skull
  const jaw = group('jaw', head, [0, CY + 1, -10], [-16, 0, 0]);
  cube('jaw_bone', jaw, [-18, CY - 10, -92], [18, CY, -10]);
  cube('jaw_chin', jaw, [-12, CY - 13, -94], [12, CY - 5, -72]);
  cube('tongue', jaw, [-14, CY, -86], [14, CY + 1, -12], 'GUM');
  cube('throat', jaw, [-10, CY - 1, -14], [10, CY + 1, -4], 'THROAT');
  for (let i = 0; i < 13; i++) {
    const z = -90 + i * 6, len = 7 + (i % 3 === 0 ? 4 : 0) - Math.floor(i / 5) * 2;
    for (const s of [-1, 1]) {
      const x = s * (15.5 - Math.min(4, Math.max(0, 3 - i)) - (i > 9 ? 2 : 0));
      cube(`tooth_d_${s > 0 ? 'l' : 'r'}${i}`, jaw, [x - 1, CY, z], [x + 1, CY + len, z + 2], 'TEETH');
    }
  }
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
      const len = 44 - k * 4, w = 16 - k * 2;
      cube(`arm_${a}_${k}_c`, seg, [at[0] - w / 2, at[1] - w / 2, at[2]], [at[0] + w / 2, at[1] + w / 2, at[2] + len]);
      if (k === 4) cube(`arm_${a}_tip`, seg, [at[0] - 2, at[1] - 2, at[2] + len], [at[0] + 2, at[1] + 2, at[2] + len + 8], 'TIP');
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
