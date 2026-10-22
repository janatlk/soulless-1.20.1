// The Leviathan's keyframed animations (the rest - tail, body, head arms, whiskers, eyelids - moves procedurally
// in the game, see client.LeviathanModel). Rebuilt from scratch on every run.
(() => {
  Animation.all.slice().forEach(a => a.remove(false));
  const bone = n => Group.all.find(g => g.name === n);
  const anim = (name, length, loop) => {
    const a = new Animation({name: `animation.leviathan.${name}`, length, loop: loop ? 'loop' : 'once', snapping: 20});
    a.add(false);
    return a;
  };
  const key = (a, boneName, channel, t, x, y, z, interp) => {
    const b = bone(boneName);
    if (!b) throw new Error('no bone ' + boneName);
    const ba = a.getBoneAnimator(b);
    const kf = ba.addKeyframe({channel, time: t, interpolation: interp || 'catmullrom', data_points: [{x, y, z}]});
    return kf;
  };

  // idle: slow breathing - the chest swells, the jaw works a little, the head bobs
  const idle = anim('idle', 4, true);
  for (const [t, s, j, hx] of [[0, 1, 0, 0], [1.6, 1.05, -4, -1.5], [2.4, 1.05, -4, -1.5], [4, 1, 0, 0]]) {
    key(idle, 'body_0', 'scale', t, s, s, 1);
    key(idle, 'body_1', 'scale', t, 1 + (s - 1) * 0.6, 1 + (s - 1) * 0.6, 1);
    key(idle, 'jaw', 'rotation', t, j, 0, 0);
    key(idle, 'head', 'rotation', t, hx, 0, 0);
  }

  // roar: the head draws back, the jaw drops wide, the whole skull shakes, then settles
  const roar = anim('roar', 2.2, false);
  const jawKeys = [[0, 0], [0.35, 6], [0.6, -38], [1.6, -42], [2.0, -6], [2.2, 0]];
  for (const [t, j] of jawKeys) key(roar, 'jaw', 'rotation', t, j, 0, 0);
  const headKeys = [[0, 0, 0, 0], [0.35, 10, 0, 0], [0.6, -14, 0, 0], [0.8, -12, 4, 3], [1.0, -12, -4, -3], [1.2, -12, 4, 3],
    [1.4, -12, -4, -3], [1.6, -12, 0, 0], [2.2, 0, 0, 0]];
  for (const [t, x, y, z] of headKeys) key(roar, 'head', 'rotation', t, x, y, z);
  for (const [t, s] of [[0, 1], [0.6, 1.12], [1.6, 1.12], [2.2, 1]]) key(roar, 'body_0', 'scale', t, s, s, 1);

  // bite: a quick snap forward
  const bite = anim('bite', 0.9, false);
  for (const [t, j] of [[0, 0], [0.25, -40], [0.45, 4], [0.9, 0]]) key(bite, 'jaw', 'rotation', t, j, 0, 0);
  for (const [t, x] of [[0, 0], [0.25, -8], [0.45, 6], [0.9, 0]]) key(bite, 'head', 'rotation', t, x, 0, 0);

  return Animation.all.map(a => a.name);
})()
