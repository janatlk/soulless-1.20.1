// The Leviathan armour - built inside Blockbench through the MCP (risky_eval), GeckoLib armour rig.
// Units: Blockbench pixels; the wearer faces north (-Z), x+ is the wearer's left. Concept: art/armor/final_B.png -
// a helmet shaped as the Leviathan's open maw (the face in its throat, fangs round it, amber eyes, whiskers), heavy
// layered scales, fin pauldrons, a raised collar, a ridge down the back, boots with small webbed fins at the toes.
(() => {
  if (!Project || Project.name !== 'leviathan_armor') newProject(Formats.geckolib_model);
  Cube.all.slice().forEach(c => c.remove());
  Group.all.slice().forEach(gr => gr.remove(false));
  Texture.all.slice().forEach(t => t.remove(true));
  Project.name = 'leviathan_armor';
  Project.box_uv = false;
  Project.texture_width = 64;
  Project.texture_height = 64;

  // ---------------------------------------------------------------- the atlas: swatches, one texel per pixel
  const cv = document.createElement('canvas');
  cv.width = 64; cv.height = 64;
  const g = cv.getContext('2d');
  const px = (x, y, c) => { g.fillStyle = `rgb(${c[0]},${c[1]},${c[2]})`; g.fillRect(x, y, 1, 1); };
  // scales: purple-black shields in rows half a scale over, lit along the top, a violet seam under each row
  const SC = {o: [16, 12, 24], d: [28, 21, 40], m: [40, 30, 56], l: [56, 42, 76], v: [108, 62, 150]};
  const ROW = ['llllllld', 'mmmmmmmd', 'mmmmmmdo', 'dmmmmddo', 'vddddovv'];
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    const r = Math.floor(y / 5), k = ROW[y % 5][(x + (r % 2) * 4) % 8];
    px(x, y, SC[k]);
  }
  // plates: smoother, larger
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++)
    px(32 + x, y, y % 8 === 0 ? SC.l : y % 8 === 7 ? SC.o : (x + y) % 9 === 0 ? SC.d : SC.m);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) px(48 + x, y, y % 4 === 0 ? [140, 86, 190] : SC.v);   // seam
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) px(32 + x, 16 + y, y < 2 ? [222, 210, 178] : y < 6 ? [196, 182, 146] : [150, 136, 104]); // fang
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) px(40 + x, 16 + y, (x + y) % 3 ? [255, 150, 40] : [255, 205, 90]); // amber
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) px(48 + x, 16 + y, [10, 6, 14]);                               // throat
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) px(56 + x, 16 + y, x % 2 ? [24, 18, 32] : [34, 26, 44]);      // whisker
  // fin membrane: violet with darker rays
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) px(32 + x, 32 + y, x % 4 === 0 ? [70, 36, 104] : y < 3 ? [170, 110, 220] : [128, 70, 182]);
  const S = {SCALE: [0, 0, 32, 32], PLATE: [32, 0, 48, 16], SEAM: [48, 0, 64, 16], FANG: [32, 16, 40, 24], AMBER: [40, 16, 48, 24],
    THROAT: [48, 16, 56, 24], WHISK: [56, 16, 64, 24], FIN: [32, 32, 48, 48]};
  const tex = new Texture({name: 'leviathan_armor'}).fromDataURL(cv.toDataURL()).add(false);

  // ---------------------------------------------------------------- helpers
  const group = (name, parent, origin) => {
    const gr = new Group({name, origin});
    if (parent) gr.addTo(parent);
    gr.init();
    return gr;
  };
  const dims = {north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2]};
  const cube = (name, parent, from, to, skin, rotation, origin) => {
    const c = new Cube({name, from, to, origin: origin || [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2],
      rotation: rotation || [0, 0, 0]}).addTo(parent).init();
    const size = [to[0] - from[0], to[1] - from[1], to[2] - from[2]];
    for (const k of Object.keys(dims)) {
      const r = S[skin || 'SCALE'], rw = r[2] - r[0], rh = r[3] - r[1];
      const w = Math.min(rw, Math.max(1, Math.round(size[dims[k][0]]))), h = Math.min(rh, Math.max(1, Math.round(size[dims[k][1]])));
      c.faces[k].uv = [r[0], r[1], r[0] + w, r[1] + h];
      c.faces[k].texture = tex.uuid;
    }
    return c;
  };
  // the same cube on the wearer's other side
  const mirror = (f, t) => [[-t[0], f[1], f[2]], [-f[0], t[1], t[2]]];
  const both = (name, pr, pl, f, t, skin, rot) => {
    cube(name + '_r', pr, f, t, skin, rot);
    const m = mirror(f, t);
    cube(name + '_l', pl, m[0], m[1], skin, rot && [rot[0], -rot[1], -rot[2]]);
  };

  // ---------------------------------------------------------------- the GeckoLib armour rig
  const bHead = group('bipedHead', null, [0, 24, 0]), head = group('armorHead', bHead, [0, 24, 0]);
  const bBody = group('bipedBody', null, [0, 24, 0]), body = group('armorBody', bBody, [0, 24, 0]);
  const bRA = group('bipedRightArm', null, [-5, 22, 0]), rArm = group('armorRightArm', bRA, [-5, 22, 0]);
  const bLA = group('bipedLeftArm', null, [5, 22, 0]), lArm = group('armorLeftArm', bLA, [5, 22, 0]);
  const bRL = group('bipedRightLeg', null, [-1.9, 12, 0]), rLeg = group('armorRightLeg', bRL, [-1.9, 12, 0]);
  const rBoot = group('armorRightBoot', bRL, [-1.9, 12, 0]);
  const bLL = group('bipedLeftLeg', null, [1.9, 12, 0]), lLeg = group('armorLeftLeg', bLL, [1.9, 12, 0]);
  const lBoot = group('armorLeftBoot', bLL, [1.9, 12, 0]);

  // ---------------------------------------------------------------- the helmet: the Leviathan's maw
  cube('skull', head, [-5, 30, -6], [5, 34.5, 5.5]);                    // the crown, over the head
  cube('snout', head, [-4.5, 30.5, -10.5], [4.5, 33.5, -6]);             // the upper jaw out over the face
  cube('snout_tip', head, [-3.5, 30.5, -12], [3.5, 32.5, -10.5]);
  cube('brow', head, [-5.5, 33, -7], [5.5, 34.5, -4], 'PLATE');
  both('eye', head, head, [-5.6, 32.6, -6.5], [-4.6, 33.6, -5], 'AMBER');
  both('cheek', head, head, [-5.5, 23, -9], [-4.5, 30.5, 5]);            // the sides of the maw
  cube('nape', head, [-5, 22.5, 4.5], [5, 30, 5.6]);
  cube('jaw', head, [-5, 22, -10], [5, 23.5, 4.5]);                      // the lower jaw under the chin
  cube('jaw_tip', head, [-4, 22, -11.5], [4, 23, -10]);
  both('throat', head, head, [-4.5, 23.5, -9], [-4.2, 30.5, 4.5], 'THROAT');
  // fangs: down from the upper jaw, up from the lower, round the front and the sides
  for (let i = 0; i < 4; i++) {
    const x = -3.4 + i * 2.2;
    cube('fang_u' + i, head, [x, 28.5 - (i % 2), -11], [x + 0.8, 30.5, -10.2], 'FANG');
    cube('fang_d' + i, head, [x + 0.6, 23, -10.8], [x + 1.3, 24.8 + (i % 2) * 0.6, -10.1], 'FANG');
  }
  for (let i = 0; i < 4; i++) {
    const z = -8.5 + i * 2.6;
    both('fang_su' + i, head, head, [-4.4, 28.4 + (i % 2) * 0.6, z], [-3.8, 30.5, z + 0.8], 'FANG');
    both('fang_sd' + i, head, head, [-4.4, 23.5, z + 1.2], [-3.8, 25 - (i % 2) * 0.5, z + 2], 'FANG');
  }
  // gill fins down the sides of the nape and a crest along the crown
  both('gill', head, head, [-7, 25, 0], [-5.5, 31, 4], 'FIN', [0, -20, 0]);
  for (let i = 0; i < 3; i++) cube('crest' + i, head, [-0.5, 34.5, -3 + i * 3], [0.5, 36.5 - i * 0.5, -0.5 + i * 3], 'FIN', [-25, 0, 0]);
  // whiskers: from the snout's sides, sweeping up and back, an amber light at each tip
  for (const s of [-1, 1]) {
    const side = s < 0 ? 'r' : 'l';
    cube('whisker_' + side, head, [s * 3.5 - 0.5, 33, -9], [s * 3.5 + 0.5, 34, 3], 'WHISK', [-40, 0, s * -18], [s * 3.5, 33.5, -9]);
    cube('whisker_tip_' + side, head, [s * 3.5 - 0.75, 32.75, 3], [s * 3.5 + 0.75, 34.25, 4.5], 'AMBER', [-40, 0, s * -18], [s * 3.5, 33.5, -9]);
    cube('whisker_s_' + side, head, [s * 4.5 - 0.4, 31, -8], [s * 4.5 + 0.4, 31.8, -1], 'WHISK', [10, s * 25, 0], [s * 4.5, 31.4, -8]);
  }

  // ---------------------------------------------------------------- the chestplate
  cube('chest', body, [-4.6, 12.4, -2.7], [4.6, 24.4, 2.7]);
  for (let i = 0; i < 4; i++) cube('row' + i, body, [-4.8 + i * 0.3, 21.5 - i * 2.8, -3.3], [4.8 - i * 0.3, 23.2 - i * 2.8, -2.7], 'PLATE');
  cube('belt', body, [-4.8, 12, -2.9], [4.8, 13.2, 2.9], 'SEAM');
  cube('collar_f', body, [-5, 23.5, -3.6], [5, 26, -2.6], 'PLATE');
  cube('collar_b', body, [-5, 23.5, 2.6], [5, 26.5, 3.6], 'PLATE');
  both('collar_s', body, body, [-5, 23.5, -3.6], [-4, 26, 3.6], 'PLATE');
  cube('back_plate', body, [-4.2, 13.5, 2.7], [4.2, 23, 3.3], 'PLATE');
  for (let i = 0; i < 4; i++) cube('ridge' + i, body, [-0.5, 21 - i * 2.6, 3.2], [0.5, 23 - i * 2.6, 5.5 - i * 0.4], 'FIN', [-30, 0, 0]);

  // ---------------------------------------------------------------- the arms: fin pauldrons, gauntlets
  const arm = (gr, s) => {
    const X = (a, b) => s < 0 ? [a, b] : [-b, -a];
    const n = s < 0 ? 'r' : 'l';
    let [a, b] = X(-8.5, -3.5);
    cube('sleeve_' + n, gr, [a, 15, -2.5], [b, 24.4, 2.5]);
    [a, b] = X(-9.6, -3);
    cube('pauldron_' + n, gr, [a, 21.5, -3.4], [b, 25.6, 3.4], 'PLATE');
    [a, b] = X(-10.2, -6);
    cube('pauldron2_' + n, gr, [a, 19.5, -3], [b, 22.5, 3]);
    [a, b] = X(-9.8, -9.2);
    cube('pauldron_seam_' + n, gr, [a, 19.2, -3.1], [b, 25.7, 3.1], 'SEAM');
    for (let i = 0; i < 3; i++) {
      [a, b] = X(-9 + i * 1.6, -8.2 + i * 1.6);
      cube('pfin' + i + '_' + n, gr, [a, 25.4, -2 + i * 0.6], [b, 28.6 - i * 0.7, 2.4 - i * 0.2], 'FIN', [0, 0, s * -22]);
    }
    [a, b] = X(-8.8, -3.2);
    cube('gauntlet_' + n, gr, [a, 11.6, -2.8], [b, 16, 2.8], 'PLATE');
    [a, b] = X(-8.9, -3.1);
    cube('gauntlet_seam_' + n, gr, [a, 15.6, -2.9], [b, 16.4, 2.9], 'SEAM');
  };
  arm(rArm, -1);
  arm(lArm, 1);

  // ---------------------------------------------------------------- leggings and boots
  const leg = (gr, boot, s) => {
    const X = (a, b) => s < 0 ? [a, b] : [-b, -a];
    const n = s < 0 ? 'r' : 'l';
    let [a, b] = X(-4.4, 0.5);
    cube('thigh_' + n, gr, [a, 4, -2.5], [b, 12.6, 2.5]);
    [a, b] = X(-4.2, 0.3);
    cube('knee_' + n, gr, [a, 5.5, -3.2], [b, 8.5, -2.5], 'PLATE');
    [a, b] = X(-4.9, -4.3);
    cube('hip_' + n, gr, [a, 7.5, -2.2], [b, 12.4, 2.2], 'PLATE');
    [a, b] = X(-4.6, 0.7);
    cube('boot_' + n, boot, [a, -0.1, -2.8], [b, 4.5, 2.8], 'PLATE');
    cube('boot_top_' + n, boot, [a, 4, -2.9], [b, 4.8, 2.9], 'SEAM');
    cube('toe_' + n, boot, [a, -0.1, -4], [b, 1.6, -2.8]);
    // the small webbed fins at the toes
    for (let i = 0; i < 3; i++) {
      [a, b] = X(-4.3 + i * 1.75, -3.1 + i * 1.75);
      cube('toefin' + i + '_' + n, boot, [a, -0.1, -6], [b, 0.5, -4], 'FIN', [0, (i - 1) * 14 * -s, 0]);
    }
    [a, b] = X(-5.6, -4.6);
    cube('ankle_fin_' + n, boot, [a, 1, 0], [b, 3.6, 3.4], 'FIN', [0, s * 15, 0]);
  };
  leg(rLeg, rBoot, -1);
  leg(lLeg, lBoot, 1);

  Canvas.updateAll();
  return {groups: Group.all.length, cubes: Cube.all.length};
})()
