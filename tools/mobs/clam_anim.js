// The Clam Mimic's animations (Blockbench, through the MCP), exported with its model and project.
//   closed    - 4 s: shut, breathing - the upper half lifts a hair and settles; teeth and eye drawn in
//   open      - 0.45 s: springs open on its hinge with an overshoot, teeth out, the eye rising
//   open_idle - 3 s: gaping, the eye on its stalk looking round, the tongue stirring, the pearl pulsing
//   lash      - 0.9 s: the tongue shoots out of the front and drags back
//   bite      - 0.5 s: snaps shut and open again
//   close     - 0.35 s: shuts
//   death     - 1.5 s: falls open slack, the eye droops, the tongue lolls out (held)
(() => {
  const fs = require('fs');
  const ROOT = 'C:/Users/janat/GameMakerProjects/minecraftmod/';
  Animation.all.slice().forEach(a => a.remove(false));
  Group.all.forEach(g => { g.rotation = [0, 0, 0]; });
  Canvas.updateAll();
  const bone = n => Group.all.find(g => g.name === n);
  const anim = (name, length, loop) => {
    const a = new Animation({name: `animation.clam_mimic.${name}`, length, loop: loop === true ? 'loop' : loop || 'once', snapping: 20});
    a.add(false);
    return a;
  };
  const key = (a, b, ch, t, x, y, z) => {
    const g = bone(b);
    if (!g) throw new Error('no bone ' + b);
    a.getBoneAnimator(g).addKeyframe({channel: ch, time: t, interpolation: 'catmullrom', data_points: [{x, y, z}]});
  };
  const TAU = Math.PI * 2;
  const shut = (a, t) => {   // teeth and eye drawn in
    key(a, 'teeth_top', 'scale', t, 1, 0.1, 1);
    key(a, 'teeth_bottom', 'scale', t, 1, 0.1, 1);
    key(a, 'eye', 'scale', t, 1, 0.3, 1);
  };
  const out = (a, t) => {
    key(a, 'teeth_top', 'scale', t, 1, 1, 1);
    key(a, 'teeth_bottom', 'scale', t, 1, 1, 1);
    key(a, 'eye', 'scale', t, 1, 1, 1);
  };

  const closed = anim('closed', 4, true);
  [[0, 0], [1.4, 2.5], [2.2, 2.5], [3.1, 0], [4, 0]].forEach(([t, x]) => key(closed, 'shell_top', 'rotation', t, x, 0, 0));
  shut(closed, 0);
  shut(closed, 4);

  const open = anim('open', 0.45);
  [[0, 0], [0.18, 66], [0.3, 52], [0.45, 56]].forEach(([t, x]) => key(open, 'shell_top', 'rotation', t, x, 0, 0));
  shut(open, 0);
  out(open, 0.25);
  key(open, 'eye', 'rotation', 0, 0, 0, 0);
  key(open, 'eye', 'rotation', 0.3, -18, 0, 0);
  key(open, 'eye', 'rotation', 0.45, -8, 0, 0);

  const idle = anim('open_idle', 3, true);
  for (let s = 0; s <= 6; s++) {
    const t = s * 0.5, w = TAU * t / 3;
    key(idle, 'shell_top', 'rotation', t, 55 + Math.sin(w) * 3, 0, 0);
    key(idle, 'eye', 'rotation', t, -8 + Math.sin(w * 2) * 6, Math.sin(w) * 35, Math.sin(w + 1) * 6);
    key(idle, 'pearl', 'scale', t, 1 + Math.sin(w * 2) * 0.06, 1 + Math.sin(w * 2) * 0.06, 1 + Math.sin(w * 2) * 0.06);
    key(idle, 'tongue_2', 'rotation', t, Math.sin(w) * 6, Math.sin(w + 0.5) * 10, 0);
    key(idle, 'tongue_3', 'rotation', t, Math.sin(w - 0.6) * 8, Math.sin(w - 0.3) * 14, 0);
    key(idle, 'tongue_4', 'rotation', t, Math.sin(w - 1.2) * 12, Math.sin(w - 0.8) * 18, 0);
  }
  out(idle, 0);

  const lash = anim('lash', 0.9);
  key(lash, 'shell_top', 'rotation', 0, 55, 0, 0);
  key(lash, 'shell_top', 'rotation', 0.15, 68, 0, 0);
  key(lash, 'shell_top', 'rotation', 0.9, 55, 0, 0);
  // the whole tongue slides out of the mouth, its tip flicking
  [[0, 0, 0], [0.18, -9, -4], [0.45, -9, 2], [0.9, 0, 0]].forEach(([t, pz, rx]) => {
    key(lash, 'tongue_1', 'position', t, 0, pz ? -0.5 : 0, pz);
    key(lash, 'tongue_1', 'rotation', t, rx, 0, 0);
  });
  [[0, 0], [0.18, -6], [0.3, 14], [0.45, -10], [0.9, 0]].forEach(([t, rx]) => {
    key(lash, 'tongue_3', 'rotation', t, rx, 0, 0);
    key(lash, 'tongue_4', 'rotation', t, rx * 1.6, 0, 0);
  });
  out(lash, 0);

  const bite = anim('bite', 0.5);
  [[0, 55], [0.12, -2], [0.22, 0], [0.5, 55]].forEach(([t, x]) => key(bite, 'shell_top', 'rotation', t, x, 0, 0));
  out(bite, 0);

  const close = anim('close', 0.35);
  [[0, 55], [0.2, -2], [0.35, 0]].forEach(([t, x]) => key(close, 'shell_top', 'rotation', t, x, 0, 0));
  out(close, 0);
  shut(close, 0.3);

  const death = anim('death', 1.5, 'hold');
  [[0, 0], [0.3, 75], [0.6, 62], [1.5, 80]].forEach(([t, x]) => key(death, 'shell_top', 'rotation', t, x, 0, 0));
  [[0, 0, 1], [0.6, 40, 0.9], [1.5, 75, 0.7]].forEach(([t, x, s]) => {
    key(death, 'eye', 'rotation', t, x, 0, x * 0.3);
    key(death, 'pearl', 'scale', t, s, s, s);
  });
  key(death, 'tongue_1', 'position', 0, 0, 0, 0);
  key(death, 'tongue_1', 'position', 1.5, 0, -0.5, -5);
  key(death, 'tongue_2', 'rotation', 1.5, 12, 15, 0);
  key(death, 'tongue_3', 'rotation', 1.5, 18, -10, 0);
  key(death, 'tongue_4', 'rotation', 1.5, 25, 12, 0);
  out(death, 0);

  const o = Animator.buildFile(null, Animation.all.map(a => a.name));
  o.geckolib_format_version = 2;
  fs.writeFileSync(ROOT + 'src/main/resources/assets/soulless/animations/entity/clam_mimic.animation.json', JSON.stringify(o, null, '\t'));
  fs.writeFileSync(ROOT + 'tools/mobs/clam_mimic.bbmodel', Codecs.project.compile());
  return Object.keys(o.animations).join(', ');
})()
