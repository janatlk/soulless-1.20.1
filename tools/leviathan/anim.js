// The Leviathan's keyframed animations, built in Blockbench (run through the MCP's risky_eval) and exported to
// animations/entity/leviathan.animation.json. The rest - the swimming wave, the crown's sway and flare, the gaze,
// whiskers and eyelids - moves procedurally in the game, see client.LeviathanModel. Rebuilt from scratch on every run.
//
//   idle - 6 s: an uneven breath (a long slow in-breath, a held moment, a quick out-breath, a small second sigh),
//          the jaw tasting the water, the head never quite still, the tongue lolling
//   roar - 2.6 s: a wind-up, the roar with the skull shaking hard and dying down, then a growl that fades
//   bite - 1.0 s: a cocked head, a lunge with the jaw flung open, a hard snap and a recoil
(() => {
  Animation.all.slice().forEach(a => a.remove(false));
  const bone = n => Group.all.find(g => g.name === n);
  const anim = (name, length, loop) => {
    const a = new Animation({name: `animation.leviathan.${name}`, length, loop: loop ? 'loop' : 'once', snapping: 20});
    a.add(false);
    return a;
  };
  const key = (a, boneName, channel, t, x, y, z) => {
    const b = bone(boneName);
    if (!b) throw new Error('no bone ' + boneName);
    a.getBoneAnimator(b).addKeyframe({channel, time: t, interpolation: 'catmullrom', data_points: [{x, y, z}]});
  };

  // idle: (time, chest swell, jaw, head x, y, z, tongue)
  const idle = anim('idle', 6, true);
  const breath = [[0, 1.00, 6, 0, 0, 0, 0], [0.7, 1.03, -2, -2, 3, 1, 4], [1.6, 1.08, -12, -5, 5, 2, 10],
    [2.3, 1.11, -17, -6, 3, 0, 14], [2.7, 1.11, -15, -6, 1, -1, 12],      // held at the top
    [3.2, 1.03, 4, 2, -3, -2, -2],                                          // the quick out-breath, mouth shut
    [3.9, 1.05, -6, 0, -5, -1, 5], [4.4, 1.02, 2, 1, -2, 1, 0],            // a small second sigh
    [5.2, 1.01, 8, 1, 2, 1, -3], [6, 1.00, 6, 0, 0, 0, 0]];
  for (const [t, sc, j, hx, hy, hz, tg] of breath) {
    [['body_0', 1], ['body_1', 0.7], ['body_2', 0.4], ['body_3', 0.15]].forEach(([b, k]) => {
      const s = 1 + (sc - 1) * k;
      key(idle, b, 'scale', t, s, s, 1);
    });
    key(idle, 'jaw', 'rotation', t, j, 0, 0);
    key(idle, 'head', 'rotation', t, hx, hy, hz);
    key(idle, 'tongue', 'rotation', t, tg, 0, 0);
  }

  // roar
  const roar = anim('roar', 2.6, false);
  for (const [t, j] of [[0, 0], [0.4, 10], [0.65, -52], [0.9, -58], [1.5, -55], [1.9, -40], [2.2, -14], [2.6, 0]])
    key(roar, 'jaw', 'rotation', t, j, 0, 0);
  const head = [[0, 0, 0, 0], [0.4, 14, 0, 0], [0.65, -18, 0, 0]];
  for (let t = 0.75, s = 1; t < 1.9; t += 0.1, s = -s) {   // the shake, dying down
    const a = 5 * (1.9 - t) / 1.15 + 1;
    head.push([Math.round(t * 100) / 100, -16, a * s, a * 0.8 * s]);
  }
  head.push([2.0, -8, 0, 0], [2.6, 0, 0, 0]);
  for (const [t, x, y, z] of head) key(roar, 'head', 'rotation', t, x, y, z);
  for (const [t, x] of [[0, 0], [0.65, -20], [1.0, -8], [1.4, -24], [2.0, 0], [2.6, 0]]) key(roar, 'tongue', 'rotation', t, x, 0, 0);
  for (const [t, v] of [[0, 1], [0.4, 1.14], [0.65, 1.05], [1.0, 1.1], [1.5, 1.07], [2.0, 1.02], [2.6, 1]]) {
    [['body_0', 1], ['body_1', 0.8], ['body_2', 0.55], ['body_3', 0.3]].forEach(([b, k]) => {
      const s = 1 + (v - 1) * k;
      key(roar, b, 'scale', t, s, s, 1);
    });
  }

  // bite
  const bite = anim('bite', 1.0, false);
  for (const [t, j] of [[0, 0], [0.2, 6], [0.38, -50], [0.48, 12], [0.6, 4], [1.0, 0]]) key(bite, 'jaw', 'rotation', t, j, 0, 0);
  for (const [t, x, z] of [[0, 0, 0], [0.2, 10, -6], [0.38, -12, 4], [0.48, 8, 0], [0.7, -2, 0], [1.0, 0, 0]]) key(bite, 'head', 'rotation', t, x, 0, z);

  return Animation.all.map(a => a.name + '/' + a.length);
})()
