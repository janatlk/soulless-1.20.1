// The Lampkeeper's animations (Blockbench, through the MCP), exported to animations/entity/lampkeeper.animation.json
// with its model (geo) and the .bbmodel. Rebuilt from scratch on every run.
//   idle  - 4 s: drifting up and down, the bell pulsing as a jellyfish's, tendrils waving one after another, the
//           lantern swinging on its chain, the hood turning a little
//   swim  - 1.6 s: leaning into the water, the bell beating hard, tendrils trailing and lashing
//   attack - 0.8 s: rears back and rakes with its free claw
//   blow  - 1.4 s: lifts the lantern to its hood and blows it out
//   death - 2 s: the bell collapses, it folds forward and sinks, the lantern drops on its chain (held)
(() => {
  const fs = require('fs');
  const ROOT = 'C:/Users/janat/GameMakerProjects/minecraftmod/';
  Animation.all.slice().forEach(a => a.remove(false));
  const bone = n => Group.all.find(g => g.name === n);
  const anim = (name, length, loop) => {
    const a = new Animation({name: `animation.lampkeeper.${name}`, length, loop: loop === true ? 'loop' : loop || 'once', snapping: 20});
    a.add(false);
    return a;
  };
  const key = (a, b, channel, t, x, y, z) => {
    const g = bone(b);
    if (!g) throw new Error('no bone ' + b);
    a.getBoneAnimator(g).addKeyframe({channel, time: t, interpolation: 'catmullrom', data_points: [{x, y, z}]});
  };
  const TAU = Math.PI * 2;
  // sample a channel over a looping animation
  const wave = (a, b, ch, len, steps, f) => {
    for (let s = 0; s <= steps; s++) {
      const t = len * s / steps, w = TAU * t / len;
      const [x, y, z] = f(w, t);
      key(a, b, ch, +t.toFixed(3), x, y, z);
    }
  };
  const TEN = 10;

  // ---- idle
  const idle = anim('idle', 4, true);
  wave(idle, 'root', 'position', 4, 8, w => [0, Math.sin(w) * 0.8, 0]);
  wave(idle, 'bell', 'scale', 4, 16, w => {
    const p = Math.sin(w * 2);          // two beats a cycle
    return [1 + 0.07 * p, 1 - 0.09 * p, 1 + 0.07 * p];
  });
  for (let i = 0; i < TEN; i++) {
    wave(idle, `tendril_${i}_a`, 'rotation', 4, 8, w => [Math.sin(w * 2 - i * 0.6) * 9, 0, Math.cos(w - i * 0.9) * 6]);
    wave(idle, `tendril_${i}_b`, 'rotation', 4, 8, w => [Math.sin(w * 2 - i * 0.6 - 0.9) * 14, 0, Math.cos(w - i * 0.9 - 0.9) * 9]);
  }
  wave(idle, 'body', 'rotation', 4, 4, w => [Math.sin(w) * 2, 0, Math.sin(w + 1) * 1.5]);
  wave(idle, 'hood', 'rotation', 4, 4, w => [Math.sin(w + 0.5) * 3, Math.sin(w * 0.5) * 12, 0]);
  wave(idle, 'arm_r', 'rotation', 4, 4, w => [Math.sin(w + 0.3) * 4, 0, -14 + Math.sin(w) * 2]);
  wave(idle, 'arm_l', 'rotation', 4, 4, w => [Math.sin(w + 1.3) * 6, 0, 14 + Math.sin(w + 1) * 3]);
  wave(idle, 'chain', 'rotation', 4, 8, w => [25 + Math.sin(w + 0.7) * 6, 0, -6 + Math.sin(w * 2) * 8]);
  wave(idle, 'lantern', 'rotation', 4, 4, w => [0, Math.sin(w) * 20, 0]);

  // ---- swim
  const swim = anim('swim', 1.6, true);
  wave(swim, 'body', 'rotation', 1.6, 4, w => [18 + Math.sin(w) * 3, 0, 0]);
  wave(swim, 'bell', 'rotation', 1.6, 4, w => [22 + Math.sin(w) * 3, 0, 0]);
  wave(swim, 'bell', 'scale', 1.6, 8, w => {
    const p = Math.sin(w);
    return [1 + 0.14 * p, 1 - 0.16 * p, 1 + 0.14 * p];
  });
  for (let i = 0; i < TEN; i++) {
    wave(swim, `tendril_${i}_a`, 'rotation', 1.6, 8, w => [-35 - Math.sin(w - i * 0.4) * 16, 0, Math.sin(w * 2 - i) * 6]);
    wave(swim, `tendril_${i}_b`, 'rotation', 1.6, 8, w => [-20 - Math.sin(w - i * 0.4 - 1) * 24, 0, Math.sin(w * 2 - i - 1) * 8]);
  }
  wave(swim, 'arm_r', 'rotation', 1.6, 4, w => [20 + Math.sin(w) * 6, 0, -14]);
  wave(swim, 'arm_l', 'rotation', 1.6, 4, w => [30 + Math.sin(w + 1) * 8, 0, 14]);
  wave(swim, 'chain', 'rotation', 1.6, 4, w => [-45 - Math.sin(w) * 8, 0, -6]);
  wave(swim, 'hood', 'rotation', 1.6, 4, w => [-12, 0, 0]);

  // ---- attack: rear back, then rake down with the free claw
  const atk = anim('attack', 0.8);
  [[0, 0, 0, 0, 0], [0.25, -14, -150, -40, -6], [0.42, 22, 30, 20, 10], [0.8, 0, 0, 0, 0]].forEach(([t, bx, ax, fx, hx]) => {
    key(atk, 'body', 'rotation', t, bx, 0, 0);
    key(atk, 'arm_l', 'rotation', t, ax, 0, 14 + (ax ? 10 : 0));
    key(atk, 'forearm_l', 'rotation', t, -10 + fx, 0, -8);
    key(atk, 'hood', 'rotation', t, hx, 0, 0);
  });
  key(atk, 'root', 'position', 0, 0, 0, 0);
  key(atk, 'root', 'position', 0.42, 0, 0, -3);
  key(atk, 'root', 'position', 0.8, 0, 0, 0);

  // ---- blow: the lantern raised to the hood, a breath, lowered dark
  const blow = anim('blow', 1.4);
  [[0, -14, -25, 25], [0.45, -95, -55, 115], [0.75, -100, -60, 120], [1.4, -14, -25, 25]].forEach(([t, ax, fx, cx]) => {
    key(blow, 'arm_r', 'rotation', t, ax, 0, -14);
    key(blow, 'forearm_r', 'rotation', t, fx, 0, 8);
    key(blow, 'chain', 'rotation', t, cx, 0, -6);
  });
  [[0, 0], [0.5, 6], [0.62, -10], [0.8, 4], [1.4, 0]].forEach(([t, hx]) => key(blow, 'hood', 'rotation', t, hx, 0, 0));
  key(blow, 'eyes', 'scale', 0, 1, 1, 1);
  key(blow, 'eyes', 'scale', 0.62, 1.3, 0.5, 1);
  key(blow, 'eyes', 'scale', 0.85, 1, 1, 1);

  // ---- death
  const death = anim('death', 2, 'hold');
  [[0, 0, 1, 0], [0.3, -20, 1.2, 0], [1.0, 50, 0.5, -4], [2, 75, 0.25, -9]].forEach(([t, bx, sy, py]) => {
    key(death, 'body', 'rotation', t, bx, 0, bx * 0.15);
    key(death, 'bell', 'scale', t, 1 + (1 - sy) * 0.3, sy, 1 + (1 - sy) * 0.3);
    key(death, 'root', 'position', t, 0, py, 0);
  });
  for (let i = 0; i < TEN; i++) {
    key(death, `tendril_${i}_a`, 'rotation', 0, 0, 0, 0);
    key(death, `tendril_${i}_a`, 'rotation', 0.4, (i % 2 ? 1 : -1) * 30, 0, (i % 3 - 1) * 20);
    key(death, `tendril_${i}_a`, 'rotation', 2, (i % 2 ? 1 : -1) * 10, 0, (i % 3 - 1) * 35);
  }
  [[0, -14], [0.5, 30], [2, 60]].forEach(([t, ax]) => key(death, 'arm_r', 'rotation', t, ax, 0, -30));
  [[0, 14], [0.6, 40], [2, 70]].forEach(([t, ax]) => key(death, 'arm_l', 'rotation', t, ax, 0, 30));
  key(death, 'chain', 'rotation', 0, 25, 0, -6);
  key(death, 'chain', 'rotation', 2, -40, 0, 30);

  // ---- export: geo, animations, the project
  const out = Animator.buildFile(null, Animation.all.map(a => a.name));
  out.geckolib_format_version = 2;
  fs.writeFileSync(ROOT + 'src/main/resources/assets/soulless/animations/entity/lampkeeper.animation.json', JSON.stringify(out, null, '\t'));
  fs.writeFileSync(ROOT + 'tools/mobs/lampkeeper.bbmodel', Codecs.project.compile());
  return Object.keys(out.animations).join(', ');
})()
