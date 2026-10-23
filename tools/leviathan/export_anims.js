(() => {
  const fs = require('fs');
  const root = 'C:/Users/janat/GameMakerProjects/minecraftmod/';
  const out = Animator.buildFile(null, Animation.all.map(a => a.name));
  out.geckolib_format_version = 2;
  fs.writeFileSync(root + 'src/main/resources/assets/soulless/animations/entity/leviathan.animation.json', JSON.stringify(out, null, '\t'));
  fs.writeFileSync(root + 'tools/leviathan/leviathan.bbmodel', Codecs.project.compile());
  return Object.keys(out.animations).map(k => k + ':' + Object.keys(out.animations[k].bones).length + ' bones, ' + out.animations[k].animation_length).join(' | ');
})()
