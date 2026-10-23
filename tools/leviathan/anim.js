// The Leviathan's keyframed animations, built in Blockbench (run through the MCP's risky_eval) and exported to
// animations/entity/leviathan.animation.json. The rest - the swimming wave, the crown's sway and flare, the gaze,
// whiskers and eyelids - moves procedurally in the game, see client.LeviathanModel. Rebuilt from scratch on every run.
//
//   idle - 6 s: an uneven breath (a long slow in-breath, a held moment, a quick out-breath, a small second sigh),
//          the jaw tasting the water, the head never quite still, the tongue lolling
//   roar - 2.6 s: coils back, throws the whole front forward with the crown fanned out, shakes, sinks back
//   bite - 1.2 s: the neck kinks into an S, the whole front strikes, the jaw snaps shut
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

  // The fight's animations move only the front of the body - head, crown and the first few segments. The tail keeps
  // the idle's slow wave (and the game's swimming), so nothing jumps when one of them starts or stops. The one-shots
  // start and end at rest.
  const FRONT = 5;

  // roar - 2.6 s: it draws its head back and up, crown pressed flat; then throws the front of its body forward with
  // the jaw flung wide and the crown fanned out, the skull shuddering; then sinks back
  const roar = anim('roar', 2.6, false);
  const wind = t => bell(t, 0, 0.5, 0.8), blast = t => ease(t, 0.55, 0.85) * (1 - ease(t, 1.8, 2.6));
  const shake = t => Math.sin((t - 0.85) * 22) * bell(t, 0.85, 1.1, 1.9);   // about three and a half shudders
  sample(roar, 'jaw', 'rotation', 2.6, 0.05, t => [8 * wind(t) - 56 * blast(t) + 3 * Math.sin(t * 18) * blast(t), 0, 0]);
  sample(roar, 'head', 'rotation', 2.6, 0.05, t => [16 * wind(t) - 20 * blast(t), 5 * shake(t), 4 * shake(t)]);
  sample(roar, 'tongue', 'rotation', 2.6, 0.1, t => [-24 * blast(t), 0, 0]);
  for (let i = 0; i < FRONT; i++) {
    const k = 1 - i / FRONT, lag = i * 0.06;
    sample(roar, 'body_' + i, 'rotation', 2.6, 0.1, t => [(12 * wind(t - lag) - 10 * blast(t - lag)) * k, 0, 0]);
  }
  sample(roar, 'body_0', 'scale', 2.6, 0.1, t => { const s = 1 + 0.06 * wind(t) + 0.12 * blast(t); return [s, s, 1]; });
  sample(roar, 'body_1', 'scale', 2.6, 0.1, t => { const s = 1 + 0.04 * wind(t) + 0.08 * blast(t); return [s, s, 1]; });
  for (let a = 0; a < 6; a++) {
    const side = a % 2 === 0 ? 1 : -1, up = a < 2 ? 1 : a < 4 ? 0.3 : -1;
    for (let k = 0; k < 5; k++) {
      sample(roar, `arm_${a}_${k}`, 'rotation', 2.6, 0.1, t => [
        (10 * wind(t) - 12 * blast(t)) * up + Math.sin(t * 3 + a + k * 0.6) * 3 * blast(t),
        (-12 * wind(t - k * 0.04) + (28 - k * 4) * blast(t - k * 0.05)) * side, 0]);
    }
  }

  // bite - 1.2 s: the neck draws back into a curve, the head cocks; the front strikes, jaw flung open and slammed
  // shut, crown swept back; then it settles
  const bite = anim('bite', 1.2, false);
  const cock = t => bell(t, 0, 0.3, 0.45), strike = t => bell(t, 0.35, 0.5, 0.95);
  sample(bite, 'jaw', 'rotation', 1.2, 0.04, t => [6 * cock(t) - 52 * bell(t, 0.3, 0.45, 0.56) + 6 * bell(t, 0.54, 0.62, 0.9), 0, 0]);
  sample(bite, 'head', 'rotation', 1.2, 0.04, t => [12 * cock(t) - 14 * strike(t), 0, -6 * cock(t) + 3 * strike(t)]);
  for (let i = 0; i < FRONT; i++) {
    const k = 1 - i / FRONT;
    sample(bite, 'body_' + i, 'rotation', 1.2, 0.06, t => [(9 * cock(t - i * 0.03) - 10 * strike(t - i * 0.04)) * k, 0, 0]);
  }
  for (let a = 0; a < 6; a++) {
    const side = a % 2 === 0 ? 1 : -1, up = a < 2 ? 1 : a < 4 ? 0.3 : -1;
    for (let k = 0; k < 5; k++) sample(bite, `arm_${a}_${k}`, 'rotation', 1.2, 0.08, t => [(14 + k * 3) * strike(t - k * 0.03) * up, -(12 + k * 3) * strike(t - k * 0.03) * side, 0]);
  }

  // grab_lunge - loops while it rises at its prey: jaw at full gape, head thrown up, crown swept back along the neck
  const lunge = anim('grab_lunge', 2.0, true);
  const lw = t => t * Math.PI;   // one slow sway a loop
  sample(lunge, 'jaw', 'rotation', 2.0, 0.1, t => [-60 + 2 * Math.sin(lw(t) * 2), 0, 0]);
  sample(lunge, 'head', 'rotation', 2.0, 0.1, t => [-14 + 2 * Math.sin(lw(t)), 2 * Math.sin(lw(t)), 0]);
  sample(lunge, 'tongue', 'rotation', 2.0, 0.2, t => [-26, 0, 0]);
  for (let a = 0; a < 6; a++) {
    const side = a % 2 === 0 ? 1 : -1, up = a < 2 ? 1 : a < 4 ? 0.3 : -1;
    for (let k = 0; k < 5; k++) sample(lunge, `arm_${a}_${k}`, 'rotation', 2.0, 0.1, t => [(22 + k * 3) * up, (-20 - k * 3 + 2 * Math.sin(lw(t) * 2 + a - k * 0.5)) * side, 0]);
  }

  // grab_thrash - loops while it holds its prey, as a crocodile shakes its catch: a heavy wrench to one side, a
  // moment held, a wrench to the other - the neck following a beat late, the crown dragged after
  const thrash = anim('grab_thrash', 2.4, true);
  // the wrench: eased from side to side, lingering at each end (a squared sine), once each way a loop
  const wr = t => { const v = Math.sin(t / 2.4 * Math.PI * 2); return Math.sign(v) * Math.pow(Math.abs(v), 0.55); };
  sample(thrash, 'jaw', 'rotation', 2.4, 0.1, t => [3, 0, 0]);
  sample(thrash, 'head', 'rotation', 2.4, 0.06, t => [-4 + 3 * Math.abs(wr(t)), 22 * wr(t), 9 * wr(t)]);
  for (let i = 0; i < 4; i++) {
    const k = 1 - i / 4;
    sample(thrash, 'body_' + i, 'rotation', 2.4, 0.08, t => [0, -10 * k * wr(t - 0.12 - i * 0.1), 4 * k * wr(t - 0.12 - i * 0.1)]);
  }
  for (let a = 0; a < 6; a++) {
    const side = a % 2 === 0 ? 1 : -1;
    for (let k = 0; k < 5; k++) sample(thrash, `arm_${a}_${k}`, 'rotation', 2.4, 0.1, t => [4 * Math.sin(t / 2.4 * Math.PI * 4 + a), (10 + k * 3) * side - (6 + k * 4) * wr(t - 0.2 - k * 0.07), 0]);
  }

  // breath - loops while it stands reared up like a dragon breathing at its prey: the neck held, the jaw wide, the
  // crown spread wide like a collar, and the body below writhing in one slow wave that runs down to the tail fin
  const br = anim('breath', 4.0, true);
  const bw = t => t / 4 * Math.PI * 2;   // one wave a loop, travelling down the body
  sample(br, 'jaw', 'rotation', 4.0, 0.1, t => [-48 + 2 * Math.sin(bw(t) * 4), 0, 0]);
  sample(br, 'tongue', 'rotation', 4.0, 0.2, t => [-20 + 3 * Math.sin(bw(t) * 2), 0, 0]);
  for (let i = 0; i < 12; i++) {
    const f = Math.max(0, (i - 2) / 9);
    sample(br, 'body_' + i, 'rotation', 4.0, 0.2, t => [Math.sin(bw(t) * 2 - i * 0.5 + 1) * 3 * f, Math.sin(bw(t) * 2 - i * 0.5) * (1 + 9 * f), Math.sin(bw(t) * 2 - i * 0.5 - 1) * 2 * f]);
  }
  sample(br, 'tail_fin', 'rotation', 4.0, 0.2, t => [0, Math.sin(bw(t) * 2 - 12 * 0.5 - 0.6) * 16, 0]);
  for (let a = 0; a < 6; a++) {
    const side = a % 2 === 0 ? 1 : -1, up = a < 2 ? 1 : a < 4 ? 0.3 : -1;
    for (let k = 0; k < 5; k++) sample(br, `arm_${a}_${k}`, 'rotation', 4.0, 0.2, t => [
      -up * (12 + k * 3) + Math.sin(bw(t) * 2 + a - k * 0.6) * 3, side * (34 - k * 4 + Math.sin(bw(t) * 2 + a * 1.4 - k * 0.6) * 4), 0]);
  }

  return Animation.all.map(a => a.name + '/' + a.length);
})()
