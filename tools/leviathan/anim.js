// The Leviathan's keyframed animations, built in Blockbench (run through the MCP's risky_eval) and exported to
// animations/entity/leviathan.animation.json. The rest - the swimming wave, the crown's sway and flare, the gaze,
// whiskers and eyelids - moves procedurally in the game, see client.LeviathanModel. Rebuilt from scratch on every run.
//
//   idle - 6 s: an uneven breath (a long slow in-breath, a held moment, a quick out-breath, a small second sigh),
//          the jaw tasting the water, the head never quite still, the tongue lolling
//   roar - 2.6 s: coils back, throws the whole front forward with the crown fanned out, shakes, sinks back
//   bite - 0.9 s: the neck kinks into an S, the whole front strikes, the jaw snaps shut
//   breath - reared up as a dragon breathing (loops): crown spread, tail writhing below
//   death - a last convulsion, then slack, rolling onto its back (held)
//   grab_lunge / grab_thrash - the rise from below at its prey, and the shaking of it in its jaws (loops)
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

  // the body's bones are not keyed: the body takes its shape from its hit boxes' path (the swimming wave is laid on
  // that in the game), so the model and what is hit stay one. Only the tail fin (which has no hit box) sweeps.
  const TAU = Math.PI * 2;
  for (let s = 0; s <= 12; s++) {
    const t = s * 0.5, w = TAU * t / 6;
    key(idle, 'tail_fin', 'rotation', t, Math.sin(w * 2 - 6) * 10, Math.sin(w - 7.2) * 30, 0);
  }
  // the crown: each arm curls from its root to its tip and uncurls, out of step with its neighbours, and the whole
  // crown opens on the in-breath (1.6-2.7 s) and draws in on the out-breath
  for (let a = 0; a < 6; a++) {
    const side = a % 2 === 0 ? 1 : -1, up = a < 2 ? 1 : a < 4 ? 0.3 : -1;
    for (let k = 0; k < 5; k++) {
      for (let s = 0; s <= 12; s++) {
        const t = s * 0.5, w = TAU * t / 6;
        const open = Math.sin(w - 0.9 - k * 0.35) * (8 + 6 * k);
        const curl = Math.sin(w * 2 + a * 1.9 - k * 0.9) * (6 + 5 * k);
        key(idle, `arm_${a}_${k}`, 'rotation', t, curl - open * up * 0.5, (open + Math.sin(w * 3 + a) * 4) * side, 0);
      }
    }
  }

  // a track sampled from a function every `step` seconds over [0, len]
  const sample = (a, b, ch, len, step, fn) => {
    for (let t = 0; t <= len + 1e-6; t += step) {
      const r = Math.round(t * 100) / 100, v = fn(r);
      key(a, b, ch, r, v[0], v[1], v[2]);
    }
  };
  const ease = (t, a, b) => t <= a ? 0 : t >= b ? 1 : (1 - Math.cos(Math.PI * (t - a) / (b - a))) / 2;
  const bell = (t, a, m, b) => t <= a || t >= b ? 0 : t < m ? ease(t, a, m) : 1 - ease(t, m, b);

  // The fight's animations. Two things about this rig shape them:
  //  - the body is a chain from the root back to the tail, and the head hangs off the root, not off the body: a turn
  //    of a front segment swings the whole tail behind it like a lever. So these leave the body alone (the idle's wave
  //    and the game's swimming move it) - all the violence is in the head, jaw and crown;
  //  - each arm is a chain of five links growing back along the body, every link turned on top of the one before:
  //    the root link carries the arm's swing, the rest only a few degrees each of curl. Positive yaw * side swings an
  //    arm outward, away from the body.
  const arm = (an, len, step, f) => {
    for (let a = 0; a < 6; a++) {
      const side = a % 2 === 0 ? 1 : -1, up = a < 2 ? 1 : a < 4 ? 0.3 : -1;
      for (let k = 0; k < 5; k++) sample(an, `arm_${a}_${k}`, 'rotation', len, step, t => {
        const [rootX, rootOut, curlX, curlOut] = f(t, a, k, up);
        return k === 0 ? [rootX, rootOut * side, 0] : [curlX, curlOut * side, 0];
      });
    }
  };

  // roar - 2.6 s: head drawn back, crown clamped to the neck; the head thrown forward with the jaw at full gape, the
  // crown flung out wide like a fan and quivering, the skull shaking; then it snaps shut
  const roar = anim('roar', 2.6, false);
  const wind = t => bell(t, 0, 0.45, 0.65), blast = t => ease(t, 0.5, 0.7) * (1 - ease(t, 2.0, 2.5));
  const shake = t => Math.sin((t - 0.7) * 34) * bell(t, 0.7, 0.9, 2.0);
  sample(roar, 'jaw', 'rotation', 2.6, 0.05, t => [10 * wind(t) - 62 * blast(t) + 5 * Math.sin(t * 26) * blast(t), 0, 0]);
  sample(roar, 'head', 'rotation', 2.6, 0.05, t => [22 * wind(t) - 26 * blast(t), 8 * shake(t), 7 * shake(t)]);
  sample(roar, 'tongue', 'rotation', 2.6, 0.05, t => [-30 * blast(t) + 6 * Math.sin(t * 20) * blast(t), 0, 0]);
  sample(roar, 'body_0', 'scale', 2.6, 0.1, t => { const s = 1 + 0.08 * wind(t) + 0.14 * blast(t); return [s, s, 1]; });
  sample(roar, 'body_1', 'scale', 2.6, 0.1, t => { const s = 1 + 0.05 * wind(t) + 0.09 * blast(t); return [s, s, 1]; });
  arm(roar, 2.6, 0.05, (t, a, k, up) => [
    (8 * wind(t) - 10 * blast(t)) * up + 4 * Math.sin(t * 24 + a) * blast(t),   // the root lifts, and quivers in the roar
    -6 * wind(t) + 34 * blast(t),
    -3 * wind(t) + 3 * Math.sin(t * 20 + a + k) * blast(t),
    4 * blast(t) + 3 * Math.sin(t * 18 + a * 2 + k) * blast(t)]);

  // bite - 0.9 s: the head jerks back and cocks, strikes, the jaw slams shut; the crown snaps out with the strike
  const bite = anim('bite', 0.9, false);
  const cock = t => bell(t, 0, 0.18, 0.28), strike = t => bell(t, 0.22, 0.32, 0.7);
  sample(bite, 'jaw', 'rotation', 0.9, 0.03, t => [8 * cock(t) - 60 * bell(t, 0.16, 0.28, 0.36) + 8 * bell(t, 0.34, 0.4, 0.6), 0, 0]);
  sample(bite, 'head', 'rotation', 0.9, 0.03, t => [20 * cock(t) - 24 * strike(t) + 5 * Math.sin(t * 50) * bell(t, 0.34, 0.4, 0.6), 0, -10 * cock(t) + 6 * strike(t)]);
  sample(bite, 'tongue', 'rotation', 0.9, 0.05, t => [-20 * strike(t), 0, 0]);
  arm(bite, 0.9, 0.05, (t, a, k, up) => [-10 * cock(t) * up + 8 * strike(t) * up, -8 * cock(t) + 28 * strike(t), 2 * cock(t), 5 * strike(t)]);

  // grab_lunge - loops while it rises at its prey: jaw at full gape, head thrown up, the crown flared out round the
  // open mouth like a net, its tips reaching forward
  const lunge = anim('grab_lunge', 1.0, true);
  const lw = t => t * Math.PI * 2;
  sample(lunge, 'jaw', 'rotation', 1.0, 0.05, t => [-64 + 4 * Math.sin(lw(t) * 2), 0, 0]);
  sample(lunge, 'head', 'rotation', 1.0, 0.05, t => [-18 + 3 * Math.sin(lw(t)), 3 * Math.sin(lw(t) * 2), 0]);
  sample(lunge, 'tongue', 'rotation', 1.0, 0.1, t => [-28, 0, 0]);
  arm(lunge, 1.0, 0.05, (t, a, k, up) => [-14 * up + 3 * Math.sin(lw(t) * 2 + a), 40 + 4 * Math.sin(lw(t) * 2 + a), -4 * up, -8 + 3 * Math.sin(lw(t) * 2 + a - k)]);

  // grab_thrash - loops while it holds its prey, a crocodile's death shake: the head wrenched hard to one side, the
  // smallest hold, wrenched back; the crown whipping after it
  const thrash = anim('grab_thrash', 1.2, true);
  const wr = t => { const v = Math.sin(t / 1.2 * Math.PI * 2); return Math.sign(v) * Math.pow(Math.abs(v), 0.5); };
  sample(thrash, 'jaw', 'rotation', 1.2, 0.05, t => [4 + 2 * Math.abs(wr(t)), 0, 0]);
  sample(thrash, 'head', 'rotation', 1.2, 0.04, t => [-6 + 6 * Math.abs(wr(t)), 30 * wr(t), 14 * wr(t)]);
  arm(thrash, 1.2, 0.05, (t, a, k, up) => [6 * Math.sin(t / 1.2 * Math.PI * 4 + a) * up, 14 - 14 * wr(t - 0.08), 3 * Math.sin(t / 1.2 * Math.PI * 4 + a - k), -6 * wr(t - 0.1 - k * 0.05)]);

  // breath - loops while it stands reared up like a dragon breathing at its prey: the jaw wide, the crown spread out
  // like a collar (the body's writhing below is the path's wave, in the game)
  const br = anim('breath', 4.0, true);
  const bw = t => t / 4 * Math.PI * 2;
  sample(br, 'jaw', 'rotation', 4.0, 0.1, t => [-52 + 3 * Math.sin(bw(t) * 8), 0, 0]);
  sample(br, 'tongue', 'rotation', 4.0, 0.1, t => [-22 + 4 * Math.sin(bw(t) * 6), 0, 0]);
  sample(br, 'tail_fin', 'rotation', 4.0, 0.2, t => [0, Math.sin(bw(t) * 2 - 12 * 0.6 - 0.6) * 18, 0]);
  arm(br, 4.0, 0.1, (t, a, k, up) => [-12 * up + 3 * Math.sin(bw(t) * 4 + a), 42 + 5 * Math.sin(bw(t) * 2 + a * 1.4), 2 * Math.sin(bw(t) * 4 + a - k), -4 + 3 * Math.sin(bw(t) * 4 + a - k * 0.7)]);

  // death - 5.5 s, held at the end: a last convulsion (jaw wide, head flung up and shaking, crown flung out), then it
  // goes slack - jaw hanging, tongue lolling, head drooping, the crown falling limp - and rolls over onto its back
  const death = anim('death', 5.5, false);
  death.loop = 'hold';
  const conv = t => bell(t, 0, 0.35, 1.3), slack = t => ease(t, 1.0, 2.2);
  const dshake = t => Math.sin(t * 30) * bell(t, 0.2, 0.5, 1.4);
  sample(death, 'jaw', 'rotation', 5.5, 0.05, t => [-64 * conv(t) - 30 * slack(t) * (1 - conv(t)), 0, 0]);
  sample(death, 'head', 'rotation', 5.5, 0.05, t => [-26 * conv(t) + 14 * slack(t), 10 * dshake(t), 8 * dshake(t)]);
  sample(death, 'tongue', 'rotation', 5.5, 0.1, t => [-30 * Math.max(conv(t), slack(t)), 0, 0]);
  sample(death, 'root', 'rotation', 5.5, 0.1, t => [6 * slack(t), 0, 155 * ease(t, 1.6, 5.2)]);
  sample(death, 'tail_fin', 'rotation', 5.5, 0.1, t => [0, Math.sin(t * 2.2) * 20 * (1 - ease(t, 1, 4)), 0]);
  arm(death, 5.5, 0.1, (t, a, k, up) => [
    -14 * up * conv(t) + 10 * up * slack(t),
    44 * conv(t) + 10 * slack(t) + 6 * Math.sin(t * 25 + a) * bell(t, 0.2, 0.5, 1.2),
    -3 * up * conv(t) + 7 * up * slack(t),
    5 * conv(t) - 2 * slack(t)]);

  return Animation.all.map(a => a.name + '/' + a.length);
})()
