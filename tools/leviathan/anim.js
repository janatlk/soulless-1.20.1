// The Leviathan's keyframed animations, built in Blockbench (run through the MCP's risky_eval) and exported to
// animations/entity/leviathan.animation.json. The rest - the swimming wave, the crown's sway and flare, the gaze,
// whiskers and eyelids - moves procedurally in the game, see client.LeviathanModel. Rebuilt from scratch on every run.
//
//   idle - 6 s: an uneven breath (a long slow in-breath, a held moment, a quick out-breath, a small second sigh),
//          the jaw tasting the water, the head never quite still, the tongue lolling
//   roar - 2.6 s: coils back, throws the whole front forward with the crown fanned out, shakes, sinks back
//   bite - 1.0 s: the neck kinks into an S, the whole front strikes, the jaw snaps shut
//   breath - reared up as a dragon breathing (loops): crown spread, tail writhing below
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

  // the whole body coils in a slow S that rolls back to the tail; every body bone is keyed (so the game adds its
  // swimming on top of these instead of replacing them)
  const TAU = Math.PI * 2;
  for (let i = 0; i < 12; i++) {
    const f = (i + 1) / 12;
    for (let s = 0; s <= 12; s++) {
      const t = s * 0.5, w = TAU * t / 6;
      key(idle, 'body_' + i, 'rotation', t, Math.sin(w * 2 - i * 0.7 + 1) * (2 + 4 * f), Math.sin(w - i * 0.55) * (5 + 9 * f), Math.sin(w - i * 0.55 - 1.2) * 4 * f);
    }
  }
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

  // roar - 2.6 s: it coils back on itself (neck drawn up and back, crown pressed flat), throws the whole front of the
  // body forward with the jaw flung wide, the skull shaking and the crown flared like a fan, then sinks back growling
  const roar = anim('roar', 2.6, false);
  const wind = t => bell(t, 0, 0.45, 0.7), blast = t => ease(t, 0.5, 0.75) * (1 - ease(t, 1.8, 2.6));
  const shake = t => t < 0.7 || t > 1.9 ? 0 : Math.sin((t - 0.7) * 38) * (1.9 - t) / 1.2;
  sample(roar, 'jaw', 'rotation', 2.6, 0.05, t => [8 * wind(t) - 58 * blast(t) + 6 * Math.sin(t * 30) * blast(t), 0, 0]);
  sample(roar, 'head', 'rotation', 2.6, 0.05, t => [16 * wind(t) - 22 * blast(t), 9 * shake(t), 7 * shake(t)]);
  sample(roar, 'tongue', 'rotation', 2.6, 0.1, t => [-26 * blast(t) + 8 * Math.sin(t * 9) * blast(t), 0, 0]);
  for (let i = 0; i < 12; i++) {
    const k = Math.max(0, 1 - i / 6), lag = i * 0.04;
    sample(roar, 'body_' + i, 'rotation', 2.6, 0.1, t => [(14 * wind(t - lag) - 12 * blast(t - lag)) * k,
      Math.sin(t * 4 - i * 0.6) * (3 + i * 0.8) * (1 - k * 0.5), Math.sin(t * 4 - i * 0.6 - 1) * 3]);
  }
  sample(roar, 'body_0', 'scale', 2.6, 0.1, t => { const s = 1 + 0.06 * wind(t) + 0.14 * blast(t); return [s, s, 1]; });
  sample(roar, 'body_1', 'scale', 2.6, 0.1, t => { const s = 1 + 0.04 * wind(t) + 0.09 * blast(t); return [s, s, 1]; });
  sample(roar, 'tail_fin', 'rotation', 2.6, 0.1, t => [0, Math.sin(t * 7) * (10 + 25 * blast(t)), 0]);
  for (let a = 0; a < 6; a++) {
    const side = a % 2 === 0 ? 1 : -1, up = a < 2 ? 1 : a < 4 ? 0.3 : -1;
    for (let k = 0; k < 5; k++) {
      sample(roar, `arm_${a}_${k}`, 'rotation', 2.6, 0.1, t => [
        (12 * wind(t) - 14 * blast(t)) * up + Math.sin(t * 11 + a + k) * 5 * blast(t),
        (-14 * wind(t) + (30 - k * 4) * blast(t)) * side, 0]);
    }
  }

  // bite - 1.0 s: the neck kinks back into an S, the head cocks, then the whole front strikes forward, the jaw
  // flung open and slammed shut, the crown swept back by the lunge; a shudder after
  const bite = anim('bite', 1.0, false);
  const cock = t => bell(t, 0, 0.22, 0.36), strike = t => bell(t, 0.26, 0.4, 0.7);
  sample(bite, 'jaw', 'rotation', 1.0, 0.04, t => [6 * cock(t) - 55 * bell(t, 0.22, 0.36, 0.46) + 10 * bell(t, 0.44, 0.5, 0.7), 0, 0]);
  sample(bite, 'head', 'rotation', 1.0, 0.04, t => [14 * cock(t) - 16 * strike(t) + 4 * Math.sin(t * 40) * bell(t, 0.46, 0.55, 0.9), 0, -8 * cock(t) + 5 * strike(t)]);
  for (let i = 0; i < 6; i++) {
    const k = 1 - i / 6, sgn = i % 2 === 0 ? 1 : -1;
    sample(bite, 'body_' + i, 'rotation', 1.0, 0.04, t => [(10 * cock(t) - 12 * strike(t)) * k, sgn * 10 * cock(t) * k, 0]);
  }
  for (let a = 0; a < 6; a++) {
    const side = a % 2 === 0 ? 1 : -1, up = a < 2 ? 1 : a < 4 ? 0.3 : -1;
    for (let k = 0; k < 5; k++) sample(bite, `arm_${a}_${k}`, 'rotation', 1.0, 0.08, t => [(18 + k * 4) * strike(t) * up, -(16 + k * 3) * strike(t) * side, 0]);
  }

  // grab_lunge - loops while it rises at its prey from below: jaw at full gape, head thrown up, crown swept back
  // flat along the neck, the body straight and rippling hard
  const lunge = anim('grab_lunge', 1.0, true);
  sample(lunge, 'jaw', 'rotation', 1.0, 0.1, t => [-62 + 4 * Math.sin(t * Math.PI * 4), 0, 0]);
  sample(lunge, 'head', 'rotation', 1.0, 0.1, t => [-14 + 3 * Math.sin(t * Math.PI * 2), 3 * Math.sin(t * Math.PI * 4), 0]);
  sample(lunge, 'tongue', 'rotation', 1.0, 0.1, t => [-28, 0, 0]);
  for (let i = 0; i < 12; i++) sample(lunge, 'body_' + i, 'rotation', 1.0, 0.1, t => [0, Math.sin(t * Math.PI * 4 - i * 0.7) * (2 + i * 1.2), 0]);
  sample(lunge, 'tail_fin', 'rotation', 1.0, 0.1, t => [0, Math.sin(t * Math.PI * 4 - 8.4) * 35, 0]);
  for (let a = 0; a < 6; a++) {
    const side = a % 2 === 0 ? 1 : -1, up = a < 2 ? 1 : a < 4 ? 0.3 : -1;
    for (let k = 0; k < 5; k++) sample(lunge, `arm_${a}_${k}`, 'rotation', 1.0, 0.1, t => [(26 + k * 3) * up, (-22 - k * 3 + 3 * Math.sin(t * Math.PI * 4 + a)) * side, 0]);
  }

  // grab_thrash - loops while it holds its prey: the jaw clamped, the head wrenched hard left and right, the neck
  // whipping the other way, the crown flailing
  const thrash = anim('grab_thrash', 1.0, true);
  const sw = t => Math.sin(t * Math.PI * 4);   // two wrenches a second each way
  sample(thrash, 'jaw', 'rotation', 1.0, 0.05, t => [4 + 3 * Math.abs(sw(t)), 0, 0]);
  sample(thrash, 'head', 'rotation', 1.0, 0.05, t => [-6 + 5 * Math.abs(sw(t)), 38 * sw(t), 16 * sw(t)]);
  for (let i = 0; i < 8; i++) {
    const k = 1 - i / 8;
    sample(thrash, 'body_' + i, 'rotation', 1.0, 0.05, t => [3 * Math.sin(t * Math.PI * 8 - i), -22 * k * Math.sin(t * Math.PI * 4 - i * 0.5), 10 * k * sw(t)]);
  }
  sample(thrash, 'tail_fin', 'rotation', 1.0, 0.1, t => [0, 30 * Math.sin(t * Math.PI * 4 - 3), 0]);
  for (let a = 0; a < 6; a++) {
    const side = a % 2 === 0 ? 1 : -1;
    for (let k = 0; k < 5; k++) sample(thrash, `arm_${a}_${k}`, 'rotation', 1.0, 0.05, t => [14 * Math.sin(t * Math.PI * 6 + a * 1.3 - k * 0.8), (20 * Math.sin(t * Math.PI * 4 + a - k * 0.6)) * side, 0]);
  }

  // breath - loops while it stands reared up like a dragon breathing at its prey: the neck held, the jaw wide and
  // trembling, the crown spread wide like a collar, and the body below writhing in a wave that never stops
  const br = anim('breath', 2.0, true);
  const w2 = t => t * Math.PI;   // one wave a second... two a loop
  sample(br, 'jaw', 'rotation', 2.0, 0.1, t => [-50 + 3 * Math.sin(t * 30), 0, 0]);
  sample(br, 'tongue', 'rotation', 2.0, 0.1, t => [-22 + 4 * Math.sin(t * 12), 0, 0]);
  for (let i = 0; i < 12; i++) {
    const f = Math.max(0, (i - 2) / 9);
    sample(br, 'body_' + i, 'rotation', 2.0, 0.1, t => [Math.sin(w2(t) * 2 - i * 0.8 + 1) * 6 * f, Math.sin(w2(t) * 2 - i * 0.7) * (2 + 16 * f), Math.sin(w2(t) * 2 - i * 0.7 - 1) * 5 * f]);
  }
  sample(br, 'tail_fin', 'rotation', 2.0, 0.1, t => [Math.sin(w2(t) * 2 - 9) * 10, Math.sin(w2(t) * 2 - 8.4) * 40, 0]);
  for (let a = 0; a < 6; a++) {
    const side = a % 2 === 0 ? 1 : -1, up = a < 2 ? 1 : a < 4 ? 0.3 : -1;
    for (let k = 0; k < 5; k++) sample(br, `arm_${a}_${k}`, 'rotation', 2.0, 0.1, t => [
      -up * (14 + k * 3) + Math.sin(w2(t) * 2 + a - k * 0.7) * 4, side * (38 - k * 5 + Math.sin(w2(t) * 3 + a * 1.4 - k) * 5), 0]);
  }

  return Animation.all.map(a => a.name + '/' + a.length);
})()
