// Assembles the Leviathan armour in Blockbench from the voxelized pieces (tools/armor/vox/*.json, made by
// voxelize.py from image-to-3D meshes of the concept's pieces). Each piece is placed on the player's body and split
// among the GeckoLib armour bones by which side of the body a cube lies on.
(() => {
  const fs = require('fs');
  const dir = 'C:/Users/janat/GameMakerProjects/minecraftmod/tools/armor/vox/';
  if (!Project || Project.name !== 'leviathan_armor') newProject(Formats.geckolib_model);
  Cube.all.slice().forEach(c => c.remove());
  Group.all.slice().forEach(gr => gr.remove(false));
  Texture.all.slice().forEach(t => t.remove(true));
  Project.name = 'leviathan_armor';
  Project.box_uv = false;
  Project.texture_width = 64;
  Project.texture_height = 64;

  const group = (name, parent, origin) => {
    const gr = new Group({name, origin});
    if (parent) gr.addTo(parent);
    gr.init();
    return gr;
  };
  const bHead = group('bipedHead', null, [0, 24, 0]), H = group('armorHead', bHead, [0, 24, 0]);
  const bBody = group('bipedBody', null, [0, 24, 0]), B = group('armorBody', bBody, [0, 24, 0]);
  const bRA = group('bipedRightArm', null, [-5, 22, 0]), RA = group('armorRightArm', bRA, [-5, 22, 0]);
  const bLA = group('bipedLeftArm', null, [5, 22, 0]), LA = group('armorLeftArm', bLA, [5, 22, 0]);
  const bRL = group('bipedRightLeg', null, [-1.9, 12, 0]), RL = group('armorRightLeg', bRL, [-1.9, 12, 0]);
  const RB = group('armorRightBoot', bRL, [-1.9, 12, 0]);
  const bLL = group('bipedLeftLeg', null, [1.9, 12, 0]), LL = group('armorLeftLeg', bLL, [1.9, 12, 0]);
  const LB = group('armorLeftBoot', bLL, [1.9, 12, 0]);

  // where each piece goes: its offset (its bottom-centre lands here) and which bone a cube joins, by its x
  const PIECES = [
    {file: 'helmet', at: [0, 22, -5], bone: () => H},
    {file: 'chest', at: [0, 10.5, 0], bone: x => x < -4.2 ? RA : x > 4.2 ? LA : B},
    {file: 'legs', at: [0, 3, 0], bone: x => x < 0 ? RL : LL},
    {file: 'boots', at: [0, -0.4, -0.6], squeeze: 1.4, bone: x => x < 0 ? RB : LB},
  ];
  // one atlas: each piece's palette a row of 2x2 swatches
  const cv = document.createElement('canvas');
  cv.width = 64; cv.height = 64;
  const g = cv.getContext('2d');
  // a piece not made yet is left out
  for (let i = PIECES.length - 1; i >= 0; i--) if (!fs.existsSync(dir + PIECES[i].file + '.json')) PIECES.splice(i, 1);
  const loaded = PIECES.map((p, row) => {
    const d = JSON.parse(fs.readFileSync(dir + p.file + '.json', 'utf8'));
    d.palette.forEach((c, i) => { g.fillStyle = `rgb(${c[0]},${c[1]},${c[2]})`; g.fillRect((i % 32) * 2, row * 8 + Math.floor(i / 32) * 2, 2, 2); });
    return d;
  });
  const tex = new Texture({name: 'leviathan_armor'}).fromDataURL(cv.toDataURL()).add(false);
  let n = 0;
  PIECES.forEach((p, row) => {
    for (const [x0, y0, z0, x1, y1, z1, l] of loaded[row].cubes) {
      // a pair (boots) drawn apart in the picture is drawn in toward the body's middle
      const sq = p.squeeze ? -Math.sign(x0 + x1) * p.squeeze : 0;
      const from = [x0 + p.at[0] + sq, y0 + p.at[1], z0 + p.at[2]], to = [x1 + p.at[0] + sq, y1 + p.at[1], z1 + p.at[2]];
      const c = new Cube({name: p.file + (n++), from, to, origin: from}).addTo(p.bone((from[0] + to[0]) / 2)).init();
      const u = (l % 32) * 2 + 0.5, v = row * 8 + Math.floor(l / 32) * 2 + 0.5;
      for (const k in c.faces) {
        c.faces[k].uv = [u, v, u + 1, v + 1];
        c.faces[k].texture = tex.uuid;
      }
    }
  });
  Canvas.updateAll();
  return {cubes: Cube.all.length};
})()
