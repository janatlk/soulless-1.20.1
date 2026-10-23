// Builds the Leviathan armour in Blockbench from tools/armor/armor_cubes.json and its atlas (both made by
// gen_armor.py): the GeckoLib armour rig, each cube with its faces' UVs on the atlas.
(() => {
  const fs = require('fs');
  const root = 'C:/Users/janat/GameMakerProjects/minecraftmod/';
  const d = JSON.parse(fs.readFileSync(root + 'tools/armor/armor_cubes.json', 'utf8'));
  if (!Project || Project.name !== 'leviathan_armor') newProject(Formats.geckolib_model);
  Cube.all.slice().forEach(c => c.remove());
  Group.all.slice().forEach(gr => gr.remove(false));
  Texture.all.slice().forEach(t => t.remove(true));
  Project.name = 'leviathan_armor';
  Project.box_uv = false;
  Project.texture_width = d.size[0];
  Project.texture_height = d.size[1];
  const png = fs.readFileSync(root + 'src/main/resources/assets/soulless/textures/armor/leviathan_armor.png').toString('base64');
  const tex = new Texture({name: 'leviathan_armor'}).fromDataURL('data:image/png;base64,' + png).add(false);

  const group = (name, parent, origin) => {
    const gr = new Group({name, origin});
    if (parent) gr.addTo(parent);
    gr.init();
    return gr;
  };
  const bones = {};
  const rig = [['bipedHead', [0, 24, 0], ['armorHead']], ['bipedBody', [0, 24, 0], ['armorBody']],
    ['bipedRightArm', [-5, 22, 0], ['armorRightArm']], ['bipedLeftArm', [5, 22, 0], ['armorLeftArm']],
    ['bipedRightLeg', [-1.9, 12, 0], ['armorRightLeg', 'armorRightBoot']], ['bipedLeftLeg', [1.9, 12, 0], ['armorLeftLeg', 'armorLeftBoot']]];
  for (const [name, o, kids] of rig) {
    const b = group(name, null, o);
    for (const k of kids) bones[k] = group(k, b, o);
  }
  let n = 0;
  for (const c of d.cubes) {
    const cu = new Cube({name: 'c' + (n++), from: c.from, to: c.to, rotation: c.rot || [0, 0, 0],
      origin: c.origin || c.from.map((v, i) => (v + c.to[i]) / 2)}).addTo(bones[c.bone]).init();
    for (const k in cu.faces) {
      cu.faces[k].uv = c.uv[k];
      cu.faces[k].texture = tex.uuid;
    }
  }
  Canvas.updateAll();
  return {cubes: Cube.all.length};
})()
