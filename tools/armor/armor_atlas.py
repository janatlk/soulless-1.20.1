"""The Leviathan armour for the game: its two textures and its model's Java, from the Blockbench models.

    python tools/armor/armor_atlas.py

The set (tools/armor/knight_set_src.bbmodel: chest, leggings, boots) is vanilla armour boxes plus detail cubes
(pauldrons, collar, leg plates); the helmet (knight_helmet_src.bbmodel) is detail cubes only. Detail cubes carry
their own per-face UVs on their own textures, which Minecraft's model parts cannot - so each is given a box-UV
patch on the layer's texture (128x128: the vanilla armour layout at the top left, the patches below) with its
faces copied in, and client/LeviathanArmorModel.java is written with the boxes, poses and UV offsets to match.
"""
import json

from PIL import Image

TEX = 'src/main/resources/assets/soulless/textures/armor/'
SIZE = 128

# bone pivots in Blockbench's up-is-+y units, and the bone each Blockbench group feeds
PIVOT = {'body': (0, 24, 0), 'right_arm': (-5, 22, 0), 'left_arm': (5, 22, 0), 'right_leg': (-1.9, 12, 0),
         'left_leg': (1.9, 12, 0), 'head': (0, 24, 0)}
GROUP_BONE = {'torso': 'body', 'left_arm': 'right_arm', 'right_arm': 'left_arm', 'left_leg': 'right_leg',
              'right_leg': 'left_leg'}


def load(path):
    return json.load(open(path, encoding='utf-8'))


def face_img(src, f):
    u1, v1, u2, v2 = f['uv']
    im = src.crop((round(min(u1, u2)), round(min(v1, v2)), round(max(u1, u2)), round(max(v1, v2))))
    if u1 > u2:
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    if v1 > v2:
        im = im.transpose(Image.FLIP_TOP_BOTTOM)
    return im


class Atlas:
    def __init__(self, base):
        self.img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
        self.img.paste(Image.open(TEX + base).convert('RGBA'), (0, 0))
        self.x, self.y, self.row = 0, 32, 0

    def patch(self, w, h, d, faces, src):
        """Box-UV patch for a w x h x d cube; faces copied in. Returns its (u, v)."""
        pw, ph = 2 * d + 2 * w, d + h
        if self.x + pw > SIZE:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        u, v = self.x, self.y
        assert v + ph <= SIZE, 'atlas full'
        self.x += pw
        self.row = max(self.row, ph)
        spots = {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'east': (u, v + d, d, h),
                 'north': (u + d, v + d, w, h), 'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h)}
        for k, (x, y, fw, fh) in spots.items():
            if fw and fh:
                self.img.paste(face_img(src, faces[k]).resize((fw, fh), Image.NEAREST), (x, y))
        return u, v


def detail(c, pivot, shift, atlas, src):
    """One detail cube as Java: a child part posed at its rotation origin, its box relative to that."""
    f = [c['from'][i] + shift[i] for i in range(3)]
    t = [c['to'][i] + shift[i] for i in range(3)]
    o = [(c.get('origin') or c['from'])[i] + shift[i] for i in range(3)]
    w, h, d = (max(1, round(t[i] - f[i])) for i in range(3))
    u, v = atlas.patch(w, h, d, c['faces'], src)
    rx, ry, rz = c.get('rotation') or [0, 0, 0]
    # Minecraft's parts: y downward, relative to the bone's pivot; flipping y turns x and z rotations round
    px, py, pz = o[0] - pivot[0], -(o[1] - pivot[1]), o[2] - pivot[2]
    bx, by, bz = f[0] - o[0], -(t[1] - o[1]), f[2] - o[2]
    pose = f'PartPose.offsetAndRotation({px:.4g}F, {py:.4g}F, {pz:.4g}F, {-rx:.4g}F * D, {ry:.4g}F * D, {-rz:.4g}F * D)'
    return f'CubeListBuilder.create().texOffs({u}, {v}).addBox({bx:.4g}F, {by:.4g}F, {bz:.4g}F, {w}, {h}, {d})', pose


def build():
    set_ = load('tools/armor/knight_set_src.bbmodel')
    helm = load('tools/armor/knight_helmet_src.bbmodel')
    import base64
    import io

    def tex(m, i):
        return Image.open(io.BytesIO(base64.b64decode(m['textures'][i]['source'].split(',')[1]))).convert('RGBA')
    # the recoloured textures stand in for the copper originals, index for index
    set_tex = {0: Image.open(TEX + 'leviathan_details.png').convert('RGBA')}
    helm_tex = Image.open(TEX + 'leviathan_helmet.png').convert('RGBA')

    atlases = {1: Atlas('leviathan_layer_1.png'), 2: Atlas('leviathan_layer_2.png')}
    parts = {1: {}, 2: {}}
    owner = {}
    for g in set_['outliner']:
        grp = next(x for x in set_['groups'] if x['uuid'] == g['uuid'])
        for uid in g['children']:
            owner[uid] = grp['name']
    for c in set_['elements']:
        if c.get('box_uv'):
            continue                                  # the vanilla boxes are written by hand below
        bone = GROUP_BONE[owner[c['uuid']]]
        layer = 2 if bone.endswith('leg') else 1
        parts[layer].setdefault(bone, []).append(detail(c, PIVOT[bone], (0, 0, 0), atlases[layer], set_tex[0]))
    # the helmet: a block model, 16 to the block - its head spans x 4..12, z 3.5..11.5, the crown's underside at
    # y 11.25; moved onto the player's head (whose top is 8 above the neck pivot)
    for c in helm['elements']:
        parts[1].setdefault('head', []).append(detail(c, PIVOT['head'], (-8, 24 + 8 - 11.25, -7.5), atlases[1], helm_tex))
    for k, a in atlases.items():
        a.img.save(TEX + f'leviathan_armor_{k}.png')
    return parts


def java(parts):
    def children(layer, bone):
        out = []
        for i, (cubes, pose) in enumerate(parts[layer].get(bone, [])):
            out.append(f'        {bone_var(bone)}.addOrReplaceChild("d{i}", {cubes}, {pose});')
        return '\n'.join(out)

    def bone_var(b):
        return {'head': 'head', 'body': 'body', 'right_arm': 'rightArm', 'left_arm': 'leftArm',
                'right_leg': 'rightLeg', 'left_leg': 'leftLeg'}[b]
    return f'''package com.soulless.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/** The Leviathan armour's model: vanilla armour boxes with the knight set's details (pauldrons, collar, leg
 * plates, the helmet). Written by tools/armor/armor_atlas.py from the Blockbench models - edit those, not this. */
public class LeviathanArmorModel extends HumanoidModel<LivingEntity> {{
    public static final ModelLayerLocation OUTER = new ModelLayerLocation(com.soulless.Soulless.id("leviathan_armor"), "outer");
    public static final ModelLayerLocation INNER = new ModelLayerLocation(com.soulless.Soulless.id("leviathan_armor"), "inner");
    private static final float D = (float) (Math.PI / 180);

    public LeviathanArmorModel(ModelPart root) {{
        super(root);
    }}

    /** The helmet, chestplate and boots (texture 1). */
    public static LayerDefinition outer() {{
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        PartDefinition root = mesh.getRoot();
        CubeDeformation d = new CubeDeformation(0.75F);
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0, 0, 0));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4, d), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 12, 4, d), PartPose.offset(-5, 2, 0));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1, -2, -2, 4, 12, 4, d), PartPose.offset(5, 2, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4, d), PartPose.offset(-1.9F, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 4, 12, 4, d), PartPose.offset(1.9F, 12, 0));
{children(1, 'head')}
{children(1, 'body')}
{children(1, 'right_arm')}
{children(1, 'left_arm')}
        return LayerDefinition.create(mesh, {SIZE}, {SIZE});
    }}

    /** The leggings (texture 2). */
    public static LayerDefinition inner() {{
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        PartDefinition root = mesh.getRoot();
        CubeDeformation d = new CubeDeformation(0.5F);
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4, d), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5, 2, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5, 2, 0));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4, d), PartPose.offset(-1.9F, 12, 0));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 4, 12, 4, d), PartPose.offset(1.9F, 12, 0));
{children(2, 'right_leg')}
{children(2, 'left_leg')}
        return LayerDefinition.create(mesh, {SIZE}, {SIZE});
    }}
}}
'''


if __name__ == '__main__':
    p = build()
    open('src/main/java/com/soulless/client/LeviathanArmorModel.java', 'w', encoding='utf-8').write(java(p))
    print({k: {b: len(v) for b, v in d.items()} for k, d in p.items()})
