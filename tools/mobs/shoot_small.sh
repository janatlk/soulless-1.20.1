#!/bin/sh
# Three views of a small model (the clam) open in Blockbench beside its concept: shoot_small.sh CONCEPT.png OUT.png
S=$(cygpath -m "$TMP")
for v in "f:-22,22,-30" "s:-34,14,6" "b:20,26,26"; do
  n=${v%%:*}; p=${v#*:}
  python tools/leviathan/bbmcp.py call set_camera_angle "{\"position\":[$p],\"target\":[0,5,0],\"projection\":\"perspective\"}" >/dev/null
  python tools/leviathan/bbmcp.py call capture_screenshot "{\"_out\":\"$S/shot_$n.png\"}" >/dev/null
done
python -c "
from PIL import Image
out=[]
for n in 'fsb':
  im=Image.open('$S/shot_'+n+'.png').convert('RGBA'); bg=Image.new('RGBA',im.size,(60,60,66,255)); bg.alpha_composite(im); out.append(bg.convert('RGB').resize((im.width*2,im.height*2),Image.NEAREST).crop((60,160,620,660)))
s=Image.new('RGB',(560*3+700,500),(60,60,66))
for i,im in enumerate(out): s.paste(im,(560*i,0))
c=Image.open('$1').convert('RGB'); c.thumbnail((700,500)); s.paste(c,(1680,(500-c.height)//2))
s.save('$2')"
