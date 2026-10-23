// The Leviathan armour - built inside Blockbench through the MCP (risky_eval), GeckoLib armour rig.
// Units: Blockbench pixels; the wearer faces north (-Z), x+ is the wearer's left. Concept: art/armor/final_B.png.
// Near-black purple scales; the helmet a reptile's head worn over the wearer's (a long stepped snout, the open maw with
// needle teeth, a jagged frill back from the jaw's hinge, amber eyes on the snout's sides, two long whiskers); a
// chevron-scaled chest with a tan clasp; huge stepped wing-like pauldrons; layered gauntlets and leggings with a tan
// undersuit showing; boots with four long webbed fins at the toes and a fin at each heel. Violet only in thin seams
// and the boots' membranes.
(() => {
  if (!Project || Project.name !== 'leviathan_armor') newProject(Formats.geckolib_model);
  Cube.all.slice().forEach(c => c.remove());
  Group.all.slice().forEach(gr => gr.remove(false));
  Texture.all.slice().forEach(t => t.remove(true));
  Project.name = 'leviathan_armor';
  Project.box_uv = false;
  Project.texture_width = 128;
  Project.texture_height = 128;

  // ---------------------------------------------------------------- the atlas
  const cv = document.createElement('canvas');
  cv.width = 128; cv.height = 128;
  const g = cv.getContext('2d');
  let seed = 11;
  const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const px = (x, y, c) => { g.fillStyle = `rgb(${c[0]},${c[1]},${c[2]})`; g.fillRect(x, y, 1, 1); };
  const P = {o: [10, 8, 14], d: [19, 15, 26], m: [29, 23, 40], l: [42, 34, 57], h: [60, 48, 80], s: [74, 52, 104], c: [120, 200, 210]};
  // SCALE: rounded scales in rows half a scale over - lit top, dark rim; a rare cold speck
  const SC = ['ollllllo', 'dmmmmmmd', 'dmmmmmmd', 'odmmmmdo', 'oodddooo'];
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    const r = Math.floor(y / 5), k = SC[y % 5][(x + (r % 2) * 4) % 8];
    const v = ((Math.floor((x + (r % 2) * 4) / 8) * 5 + r * 3) % 7 === 0 && k === 'm') ? P.l : P[k];
    px(x, y, rnd() < 0.006 ? P.c : v);
  }
  // CHEV: chevrons opening downward - rows of plates in a V, a seam under each
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    const dx = Math.abs(x - 15.5), yy = (y + Math.floor(dx * 0.6)) % 6;
    px(32 + x, y, yy === 0 ? P.h : yy === 1 ? P.l : yy === 5 ? (dx < 3 ? P.s : P.o) : yy === 4 ? P.d : P.m);
  }
  // PLATE: a banded plate - lit lip, body, a violet-dark seam under it
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const yy = y % 8;
    px(64 + x, y, yy === 0 ? P.h : yy === 1 ? P.l : yy === 7 ? P.s : yy === 6 ? P.o : (x + y * 3) % 11 === 0 ? P.d : P.m);
  }
  for (let y = 0; y < 8; y++) for (let x = 0; x < 16; x++) px(80 + x, y, y === 0 ? [112, 78, 150] : y < 6 ? [76, 50, 108] : [48, 32, 70]);   // SEAM
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) px(80 + x, 8 + y, y < 2 ? [226, 214, 182] : y < 5 ? [198, 184, 148] : [150, 136, 104]); // FANG
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) px(88 + x, 8 + y, y < 3 ? [255, 176, 60] : [228, 120, 24]);                         // AMBER
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) px(96 + x, y, [6, 4, 8]);                                                           // THROAT
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) px(104 + x, y, x < 3 ? P.l : P.d);                                                // WHISK
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) px(112 + x, y, y === 0 ? [196, 170, 120] : [168, 138, 92]);                       // TAN
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) px(96 + x, 8 + y, y < 2 ? [176, 120, 226] : x % 5 === 0 ? [88, 50, 130] : [136, 84, 190]); // MEMB
  const S = {SCALE: [0, 0, 32, 32], CHEV: [32, 0, 64, 32], PLATE: [64, 0, 80, 16], SEAM: [80, 0, 96, 8], FANG: [80, 8, 88, 16],
    AMBER: [88, 8, 96, 16], THROAT: [96, 0, 104, 8], WHISK: [104, 0, 112, 8], TAN: [112, 0, 120, 8], MEMB: [96, 8, 112, 24]};
  const tex = new Texture({name: 'leviathan_armor'}).fromDataURL(cv.toDataURL()).add(false);

  // ---------------------------------------------------------------- helpers
  const group = (name, parent, origin) => {
    const gr = new Group({name, origin});
    if (parent) gr.addTo(parent);
    gr.init();
    return gr;
  };
  const dims = {north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2]};
  let n = 0;
  // skin: one swatch, or {face: swatch, _: default}
  const cube = (parent, from, to, skin, rotation, origin) => {
    const c = new Cube({name: 'c' + (n++), from, to, origin: origin || [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2],
      rotation: rotation || [0, 0, 0]}).addTo(parent).init();
    const size = [to[0] - from[0], to[1] - from[1], to[2] - from[2]];
    for (const k of Object.keys(dims)) {
      const sw = typeof skin === 'object' && skin ? (skin[k] || skin._) : (skin || 'SCALE');
      const r = S[sw], rw = r[2] - r[0], rh = r[3] - r[1];
      const w = Math.min(rw, Math.max(1, Math.round(size[dims[k][0]]))), h = Math.min(rh, Math.max(1, Math.round(size[dims[k][1]])));
      const ox = r[0] + Math.floor((rw - w) * rnd()), oy = r[1] + Math.floor((rh - h) * rnd());
      c.faces[k].uv = [ox, oy, ox + w, oy + h];
      c.faces[k].texture = tex.uuid;
    }
    return c;
  };
  // the same cube on both sides: f/t given for the wearer's right (x < 0)
  const pair = (pr, pl, f, t, skin, rot, org) => {
    cube(pr, f, t, skin, rot, org);
    cube(pl, [-t[0], f[1], f[2]], [-f[0], t[1], t[2]], skin, rot && [rot[0], -rot[1], -rot[2]], org && [-org[0], org[1], org[2]]);
  };
  // a whisker: small cubes along a cubic Bezier, an amber light at the end
  const whisker = (gr, p0, p1, p2, p3, w) => {
    const at = t => [0, 1, 2].map(i => (1 - t) ** 3 * p0[i] + 3 * (1 - t) ** 2 * t * p1[i] + 3 * (1 - t) * t * t * p2[i] + t ** 3 * p3[i]);
    for (let i = 0; i <= 28; i++) {
      const q = at(i / 28), r = w * (1 - i / 60) / 2;
      cube(gr, [q[0] - r, q[1] - r, q[2] - r], [q[0] + r, q[1] + r, q[2] + r], 'WHISK');
    }
    cube(gr, [p3[0] - 0.7, p3[1] - 0.9, p3[2] - 0.7], [p3[0] + 0.7, p3[1] + 0.5, p3[2] + 0.7], 'AMBER');
  };

  // ---------------------------------------------------------------- the GeckoLib armour rig
  const bHead = group('bipedHead', null, [0, 24, 0]), H = group('armorHead', bHead, [0, 24, 0]);
  const bBody = group('bipedBody', null, [0, 24, 0]), B = group('armorBody', bBody, [0, 24, 0]);
  const bRA = group('bipedRightArm', null, [-5, 22, 0]), RA = group('armorRightArm', bRA, [-5, 22, 0]);
  const bLA = group('bipedLeftArm', null, [5, 22, 0]), LA = group('armorLeftArm', bLA, [5, 22, 0]);
  const bRL = group('bipedRightLeg', null, [-1.9, 12, 0]), RL = group('armorRightLeg', bRL, [-1.9, 12, 0]);
  const RB = group('armorRightBoot', bRL, [-1.9, 12, 0]);
  const bLL = group('bipedLeftLeg', null, [1.9, 12, 0]), LL = group('armorLeftLeg', bLL, [1.9, 12, 0]);
  const LB = group('armorLeftBoot', bLL, [1.9, 12, 0]);

  // ---------------------------------------------------------------- the helmet
  // the crown and the back of the head
  cube(H, [-4.8, 31.5, -4.8], [4.8, 34, 4.8]);
  cube(H, [-4.8, 24.5, 3.6], [4.8, 31.5, 4.8]);
  cube(H, [-4.9, 24, 3.4], [4.9, 25, 5], 'SEAM');                       // the violet band round the nape
  // the upper jaw: a long snout in steps, lower and longer toward the front
  cube(H, [-4.6, 29.5, -11], [4.6, 31.5, -4]);
  cube(H, [-4.2, 31.5, -10], [4.2, 32.6, -4]);
  cube(H, [-3.6, 32.6, -8], [3.6, 33.6, -2]);
  cube(H, [-3, 30, -12.4], [3, 31.6, -11]);                              // the snout's blunt tip
  cube(H, [-2.2, 31.6, -11.6], [-1, 32.1, -10.4], 'PLATE');               // nostril ridges
  cube(H, [1, 31.6, -11.6], [2.2, 32.1, -10.4], 'PLATE');
  pair(H, H, [-5.1, 31.6, -4.6], [-3.8, 34.6, -1], 'PLATE', [0, 0, 12]);  // brow ridges, swept up at the back
  pair(H, H, [-4.9, 31.3, -8.2], [-4.55, 32.4, -6.6], 'AMBER');          // the eyes on the snout's sides
  // the cheeks: walls from the hinge forward, the maw open between the jaws
  pair(H, H, [-5, 24.5, -6], [-4.2, 31.5, 3.6]);
  pair(H, H, [-4.9, 24.5, -6], [-4.6, 29.5, 3.6], 'THROAT');
  cube(H, [-4.6, 29.4, -11], [4.6, 29.6, -4], 'THROAT');                 // the roof of the mouth
  // the lower jaw: under the chin, long and a touch raised at its tip
  cube(H, [-4.8, 22.6, -10.5], [4.8, 24.6, 3.6]);
  cube(H, [-3.6, 23.6, -12], [3.6, 25, -10.5]);
  cube(H, [-4.6, 24.5, -10.5], [4.6, 24.7, -4], 'THROAT');               // the tongue's floor
  // needle teeth: a dense row down from the upper jaw, up from the lower, round the front and down the sides
  for (let i = 0; i < 7; i++) {
    const x = -3 + i;
    cube(H, [x - 0.25, 27.6 + (i % 2) * 0.7, -12.2], [x + 0.25, 30, -11.7], 'FANG');
    cube(H, [x + 0.25, 24.9, -11.9], [x + 0.75, 26.6 - (i % 2) * 0.6, -11.4], 'FANG');
  }
  for (let i = 0; i < 6; i++) {
    const z = -10.6 + i * 1.15;
    pair(H, H, [-4.5, 27.7 + (i % 2) * 0.8, z], [-4, 29.5, z + 0.45], 'FANG');
    pair(H, H, [-4.5, 24.6, z + 0.55], [-4, 26.4 - (i % 2) * 0.7, z + 1], 'FANG');
  }
  // the frill: jagged spikes back from the jaw's hinge
  for (let i = 0; i < 4; i++) {
    pair(H, H, [-5.4, 24.6 + i * 1.7, 2], [-4.6, 25.6 + i * 1.7, 6.5 - i * 0.8], i % 2 ? 'PLATE' : 'SCALE', [i * 8 - 12, -22, 0], [-5, 25 + i * 1.7, 2]);
  }
  for (let i = 0; i < 3; i++) cube(H, [-0.6, 33.6, -1 + i * 2.4], [0.6, 35.2 - i * 0.3, 1 + i * 2.4], 'PLATE', [-28, 0, 0]);   // crest
  // whiskers from the crown: one rises and arcs out and down, the other rises and droops back, an amber light at each end
  whisker(H, [-1.6, 34, -2.5], [-3, 42, -3], [-8, 46, 2], [-11, 39, 3], 0.9);
  whisker(H, [1.6, 34, -2.5], [2.5, 40, -4], [7, 44, 1], [9.5, 39, 6], 0.9);

  // ---------------------------------------------------------------- the chestplate
  cube(B, [-4.6, 12.3, -2.7], [4.6, 24.3, 2.7], {north: 'CHEV', _: 'SCALE'});
  // chevron plates down the chest: each row two plates meeting in a V
  for (let i = 0; i < 4; i++) {
    const y = 21.4 - i * 2.6;
    // flat steps toward the middle, a little lower at each - the V drawn in plates
    pair(B, B, [-4.7, y + 0.6, -3.2], [-2.6, y + 1.8, -2.7], 'PLATE');
    pair(B, B, [-2.6, y + 0.2, -3.3], [-0.9, y + 1.4, -2.7], 'PLATE');
    cube(B, [-0.9, y - 0.2, -3.4], [0.9, y + 1, -2.7], 'PLATE');
  }
  cube(B, [-0.8, 22.6, -3.6], [0.8, 23.8, -3], 'TAN');                   // the clasp under the collar
  cube(B, [-4.8, 12, -2.9], [4.8, 13, 2.9], 'SEAM');                     // the belt
  // the collar: low at the front, high round the back
  cube(B, [-4.9, 23.8, -3.4], [4.9, 25, -2.5], 'PLATE');
  pair(B, B, [-4.9, 23.8, -3.4], [-3.8, 26, 3.6], 'PLATE');
  cube(B, [-4.9, 23.8, 2.5], [4.9, 27, 3.6], 'PLATE');
  // the back: plates and a jagged spine down it
  cube(B, [-4.3, 13.4, 2.6], [4.3, 23.4, 3.2], 'PLATE');
  for (let i = 0; i < 5; i++) cube(B, [-0.55, 21.5 - i * 2, 3], [0.55, 23.3 - i * 2, 5.2 - i * 0.3], 'SCALE', [-32, 0, 0], [0, 22 - i * 2, 3]);

  // ---------------------------------------------------------------- the arms: wing-like pauldrons, gauntlets
  const arm = (gr, s) => {
    const X = (f, t) => s < 0 ? [f, t] : [[-t[0], f[1], f[2]], [-f[0], t[1], t[2]]];
    const R = r => s < 0 ? r : [r[0], -r[1], -r[2]];
    const O = o => s < 0 ? o : [-o[0], o[1], o[2]];
    const c = (f, t, skin, rot, org) => { const [a, b] = X(f, t); cube(gr, a, b, skin, rot && R(rot), org && O(org)); };
    c([-8.5, 13, -2.5], [-3.5, 24.3, 2.5]);                              // the sleeve
    c([-8.7, 17, -2.7], [-3.3, 18, 2.7], 'TAN');                         // the undersuit at the elbow
    // the pauldron: three plates stacked, each wider, higher and tilted further out - a wing
    const plates = [[-10.2, 20.4, -3.7, -3.2, 24.8, 3.7, 8], [-13, 22.2, -3.4, -5, 25.8, 3.4, 20], [-15, 24, -3, -7, 27, 3, 32]];
    for (const [x0, y0, z0, x1, y1, z1, a] of plates) {
      c([x0, y0, z0], [x1, y1, z1], {up: 'SCALE', _: 'PLATE'}, [0, 0, -a], [-3.5, y0, 0]);
      c([x0 - 0.1, y0 - 0.1, z0 - 0.1], [x1 + 0.1, y0 + 0.6, z1 + 0.1], 'SEAM', [0, 0, -a], [-3.5, y0, 0]);
    }
    // jagged points along the wing's outer edge
    for (let i = 0; i < 3; i++) c([-17, 24.2 + i * 0.4, -2.6 + i * 1.9], [-14.8, 25.4 + i * 0.4, -1.5 + i * 1.9], 'PLATE', [0, 0, -32], [-3.5, 24, 0]);
    // the gauntlet: two bulky bands
    c([-9, 12.6, -2.9], [-3, 16.8, 2.9], 'PLATE');
    c([-9.2, 15.6, -3.1], [-2.8, 17, 3.1], 'PLATE');
    c([-9.1, 12.2, -3], [-2.9, 12.8, 3], 'SEAM');
    c([-8.6, 11.6, -2.6], [-3.4, 12.2, 2.6], 'TAN');                      // the cuff at the wrist
  };
  arm(RA, -1);
  arm(LA, 1);

  // ---------------------------------------------------------------- leggings and boots
  const leg = (gr, boot, s) => {
    const X = (f, t) => s < 0 ? [f, t] : [[-t[0], f[1], f[2]], [-f[0], t[1], t[2]]];
    const R = r => s < 0 ? r : [r[0], -r[1], -r[2]];
    const O = o => s < 0 ? o : [-o[0], o[1], o[2]];
    const c = (g2, f, t, skin, rot, org) => { const [a, b] = X(f, t); cube(g2, a, b, skin, rot && R(rot), org && O(org)); };
    c(gr, [-4.4, 4.5, -2.5], [0.5, 12.5, 2.5]);
    for (let i = 0; i < 3; i++) c(gr, [-4.6, 10.6 - i * 2.4, -2.7], [0.7, 12 - i * 2.4, 2.7], 'PLATE');   // layered bands
    c(gr, [-4.3, 5, -3.4], [0.4, 8, -2.5], {north: 'SCALE', _: 'PLATE'});                                // the knee guard
    c(gr, [-5, 6.5, -2.2], [-4.4, 12.2, 2.2], 'PLATE');                                                     // the hip plate
    // the boot
    c(boot, [-4.7, -0.1, -3], [0.9, 5.2, 3], {up: 'PLATE', _: 'SCALE'});
    c(boot, [-4.9, 4.4, -3.2], [1.1, 5.4, 3.2], 'PLATE');
    c(boot, [-4.7, -0.1, -4.4], [0.9, 2, -3], 'PLATE');                                                     // the toe cap
    // four long webbed fins at the toes, splayed: dark ribs over a violet membrane
    c(boot, [-4.6, -0.1, -9], [0.8, 0.35, -4.2], 'MEMB');
    for (let i = 0; i < 4; i++) {
      const x = -4.4 + i * 1.65;
      c(boot, [x, 0.2, -9.6 + Math.abs(i - 1.5) * 0.5], [x + 0.7, 0.9, -4.2], 'PLATE', [0, (i - 1.5) * -7, 0], [x + 0.35, 0.5, -4.2]);
    }
    // a fin at the heel, swept back
    c(boot, [-5.1, 0.4, 1], [-4.7, 3.6, 4.6], 'MEMB', [0, -28, 0], [-4.8, 2, 1]);
    c(boot, [-5.2, 3.2, 1], [-4.6, 3.8, 4.8], 'PLATE', [0, -28, 0], [-4.8, 2, 1]);
  };
  leg(RL, RB, -1);
  leg(LL, LB, 1);

  Canvas.updateAll();
  return {groups: Group.all.length, cubes: Cube.all.length};
})()
