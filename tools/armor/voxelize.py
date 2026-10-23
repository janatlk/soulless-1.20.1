"""Turn a textured GLB (from image-to-3D) into Minecraft-style cubes for a GeckoLib armour bone.

    python tools/armor/voxelize.py MESH.glb OUT.json --height H --cell C [--yaw DEG] [--flip]

The mesh is turned so its widest side lies along x (its principal axes on the ground), scaled so it stands H px
tall, its surface sampled densely, the samples' texture colours averaged into cells of C px and the colours brought
down to a small palette; runs of cells of one colour along x are merged into one cube. Out: JSON
{"cubes": [[x0,y0,z0,x1,y1,z1,palette_index], ...], "palette": [[r,g,b], ...]}, in px, the mesh's bottom at y 0,
centred on x and z, its front toward -z (Blockbench's north). No dependencies beyond numpy and Pillow.
"""
import argparse
import io
import json
import struct

import numpy as np
from PIL import Image

COMP = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def load_glb(path):
    data = open(path, "rb").read()
    _, _, _ = struct.unpack_from("<III", data, 0)
    off, gltf, binc = 12, None, None
    while off < len(data):
        ln, kind = struct.unpack_from("<II", data, off)
        chunk = data[off + 8: off + 8 + ln]
        if kind == 0x4E4F534A:
            gltf = json.loads(chunk)
        elif kind == 0x004E4942:
            binc = chunk
        off += 8 + ln

    def view(i):
        bv = gltf["bufferViews"][i]
        return binc[bv.get("byteOffset", 0): bv.get("byteOffset", 0) + bv["byteLength"]]

    def acc(i):
        a = gltf["accessors"][i]
        raw = view(a["bufferView"])
        n, k = a["count"], NCOMP[a["type"]]
        dt = np.dtype(COMP[a["componentType"]])
        stride = gltf["bufferViews"][a["bufferView"]].get("byteStride") or dt.itemsize * k
        start = a.get("byteOffset", 0)
        arr = np.ndarray((n, k), dt, raw, start, (stride, dt.itemsize)).copy()
        if a.get("normalized") and dt.kind in "iu":
            arr = arr.astype(np.float32) / np.iinfo(dt).max
        return arr

    def node_mats():
        """Each mesh's world matrix (the node tree walked from the scene's roots)."""
        out = []
        def mat(nd):
            if "matrix" in nd:
                return np.array(nd["matrix"], np.float64).reshape(4, 4).T
            m = np.eye(4)
            if "scale" in nd:
                m = np.diag(list(nd["scale"]) + [1]) @ m
            if "rotation" in nd:
                x, y, z, w = nd["rotation"]
                r = np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                              [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                              [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
                rm = np.eye(4); rm[:3, :3] = r
                m = rm @ m
            if "translation" in nd:
                t = np.eye(4); t[:3, 3] = nd["translation"]
                m = t @ m
            return m
        def walk(i, parent):
            nd = gltf["nodes"][i]
            w = parent @ mat(nd)
            if "mesh" in nd:
                out.append((nd["mesh"], w))
            for c in nd.get("children", []):
                walk(c, w)
        scene = gltf["scenes"][gltf.get("scene", 0)]
        for r in scene["nodes"]:
            walk(r, np.eye(4))
        return out

    images = {}
    def image_for(prim):
        mi = prim.get("material")
        if mi is None:
            return None
        pbr = gltf["materials"][mi].get("pbrMetallicRoughness", {})
        t = pbr.get("baseColorTexture")
        if t is None:
            return None
        src = gltf["textures"][t["index"]]["source"]
        if src not in images:
            im = gltf["images"][src]
            images[src] = np.asarray(Image.open(io.BytesIO(view(im["bufferView"]))).convert("RGB"), np.float32)
        return images[src]

    tris = []
    for mi, w in node_mats():
        for prim in gltf["meshes"][mi]["primitives"]:
            a = prim["attributes"]
            pos = acc(a["POSITION"]).astype(np.float64)
            pos = (np.c_[pos, np.ones(len(pos))] @ w.T)[:, :3]
            uv = acc(a["TEXCOORD_0"]) if "TEXCOORD_0" in a else None
            idx = acc(prim["indices"]).reshape(-1) if "indices" in prim else np.arange(len(pos))
            tris.append((pos, uv, idx.reshape(-1, 3), image_for(prim)))
    return tris


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("glb")
    ap.add_argument("out")
    ap.add_argument("--height", type=float, required=True)
    ap.add_argument("--cell", type=float, default=0.5)
    ap.add_argument("--yaw", type=float, default=0, help="extra turn about y after aligning, degrees")
    ap.add_argument("--flip", action="store_true", help="turn it round (front and back swapped)")
    ap.add_argument("--colors", type=int, default=28)
    ap.add_argument("--no-align", action="store_true")
    a = ap.parse_args()

    rng = np.random.default_rng(1)
    pts, cols = [], []
    allpos = np.concatenate([t[0] for t in load_glb(a.glb)])
    lo, hi = allpos.min(0), allpos.max(0)
    span = (hi - lo)[1]
    for pos, uv, tri, img in load_glb(a.glb):
        v0, v1, v2 = pos[tri[:, 0]], pos[tri[:, 1]], pos[tri[:, 2]]
        area = 0.5 * np.linalg.norm(np.cross(v1 - v0, v2 - v0), axis=1)
        # samples per triangle: enough for ~6 per output cell's area
        cell_world = a.cell * span / a.height
        n = np.ceil(area / (cell_world ** 2) * 6).astype(int).clip(1, 400)
        ti = np.repeat(np.arange(len(tri)), n)
        r1, r2 = rng.random(len(ti)), rng.random(len(ti))
        s = np.sqrt(r1)
        w0, w1, w2 = 1 - s, s * (1 - r2), s * r2
        p = v0[ti] * w0[:, None] + v1[ti] * w1[:, None] + v2[ti] * w2[:, None]
        if uv is not None and img is not None:
            u = uv[tri[ti, 0]] * w0[:, None] + uv[tri[ti, 1]] * w1[:, None] + uv[tri[ti, 2]] * w2[:, None]
            h, w = img.shape[:2]
            x = (np.mod(u[:, 0], 1) * (w - 1)).astype(int)
            y = (np.mod(u[:, 1], 1) * (h - 1)).astype(int)
            c = img[y, x]
        else:
            c = np.full((len(p), 3), 128.0)
        pts.append(p)
        cols.append(c)
    P = np.concatenate(pts)
    C = np.concatenate(cols)

    # lay it out: bottom on y 0, the principal axes on the ground along x and z, scaled to the height
    P -= P.mean(0)
    if not a.no_align:
        xz = P[:, [0, 2]]
        ev, evec = np.linalg.eigh(np.cov(xz.T))
        major = evec[:, np.argmax(ev)]
        ang = np.arctan2(major[1], major[0])
        ca, sa = np.cos(-ang), np.sin(-ang)
        x, z = P[:, 0] * ca - P[:, 2] * sa, P[:, 0] * sa + P[:, 2] * ca
        P[:, 0], P[:, 2] = x, z
    if a.yaw:
        r = np.radians(a.yaw)
        x, z = P[:, 0] * np.cos(r) - P[:, 2] * np.sin(r), P[:, 0] * np.sin(r) + P[:, 2] * np.cos(r)
        P[:, 0], P[:, 2] = x, z
    if a.flip:
        P[:, 0] *= -1
        P[:, 2] *= -1
    P *= a.height / (P[:, 1].max() - P[:, 1].min())
    P[:, 1] -= P[:, 1].min()
    P[:, 0] -= (P[:, 0].max() + P[:, 0].min()) / 2
    P[:, 2] -= (P[:, 2].max() + P[:, 2].min()) / 2

    # cells: the mean colour of what fell in each
    key = np.floor(P / a.cell).astype(int)
    uniq, inv, cnt = np.unique(key, axis=0, return_inverse=True, return_counts=True)
    inv = inv.reshape(-1)
    acc = np.zeros((len(uniq), 3))
    np.add.at(acc, inv, C)
    mean = acc / cnt[:, None]
    keep = cnt >= 2
    uniq, mean = uniq[keep], mean[keep]

    # a small palette (k-means), so faces share swatches and runs merge
    k = min(a.colors, len(mean))
    cent = mean[rng.choice(len(mean), k, replace=False)]
    for _ in range(25):
        d = ((mean[:, None, :] - cent[None]) ** 2).sum(2)
        lab = d.argmin(1)
        for j in range(k):
            m = lab == j
            if m.any():
                cent[j] = mean[m].mean(0)
    lab = ((mean[:, None, :] - cent[None]) ** 2).sum(2).argmin(1)

    # merge runs along x of one colour
    cells = {tuple(c): l for c, l in zip(uniq.tolist(), lab.tolist())}
    done, cubes = set(), []
    for (x, y, z), l in sorted(cells.items()):
        if (x, y, z) in done:
            continue
        x1 = x
        while (x1 + 1, y, z) in cells and cells[(x1 + 1, y, z)] == l and (x1 + 1, y, z) not in done:
            x1 += 1
        for xx in range(x, x1 + 1):
            done.add((xx, y, z))
        c = a.cell
        cubes.append([round(x * c, 3), round(y * c, 3), round(z * c, 3), round((x1 + 1) * c, 3), round((y + 1) * c, 3), round((z + 1) * c, 3), int(l)])
    json.dump({"cubes": cubes, "palette": [[int(v) for v in p] for p in cent.round()]}, open(a.out, "w"))
    ext = P.max(0) - P.min(0)
    print(f"{len(cubes)} cubes from {len(uniq)} cells; extent x{ext[0]:.1f} y{ext[1]:.1f} z{ext[2]:.1f} px")


if __name__ == "__main__":
    main()
