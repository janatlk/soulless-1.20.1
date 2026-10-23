#!/bin/sh
# Three views of the model open in Blockbench beside its concept: shoot.sh CONCEPT.png OUT.png [target_y]
S=$(cygpath -m "$TMP")
TY=${3:-14}
for v in "f:-40,30,-62" "s:-70,26,0" "b:36,30,60"; do
  n=${v%%:*}; p=${v#*:}
  python tools/leviathan/bbmcp.py call set_camera_angle "{\"position\":[$p],\"target\":[0,$TY,0],\"projection\":\"perspective\"}" >/dev/null
  python tools/leviathan/bbmcp.py call capture_screenshot "{\"_out\":\"$S/shot_$n.png\"}" >/dev/null
done
python -c "
from PIL import Image
out=[]
for n in 'fsb':
  im=Image.open('$S/shot_'+n+'.png').convert('RGBA'); bg=Image.new('RGBA',im.size,(60,60,66,255)); bg.alpha_composite(im); out.append(bg.convert('RGB').crop((50,20,290,390)).resize((360,555),Image.NEAREST))
s=Image.new('RGB',(360*3+800,555),(60,60,66))
for i,im in enumerate(out): s.paste(im,(360*i,0))
c=Image.open('$1').convert('RGB'); c.thumbnail((800,555)); s.paste(c,(1080,(555-c.height)//2))
s.save('$2')"
