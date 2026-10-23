"""The Leviathan Scale icon: a teal-violet scale, dark rim, pale shine (16x16)."""
from PIL import Image
im = Image.new("RGBA", (16, 16))
rim, dark, mid, lite, shine = (20, 28, 48), (30, 70, 90), (40, 130, 140), (90, 190, 185), (200, 240, 230)
vio = (110, 70, 160)
for y in range(16):
    for x in range(16):
        # a shield-shaped scale, pointed at the bottom
        cx, w = 7.5, 6.5 if y < 9 else 6.5 * (15 - y) / 6
        if y < 1 or y > 15 or abs(x - cx) > w: continue
        edge = abs(x - cx) > w - 1 or y == 1 or (y >= 9 and abs(x - cx) > w - 1.4)
        c = rim if edge else (lite if (x - cx) < -2 else mid if (x - cx) < 2 else dark)
        if not edge and (y + x) % 5 == 0: c = vio
        if not edge and 3 <= y <= 6 and 3 <= x <= 4: c = shine
        im.putpixel((x, y), c + (255,))
im.save("src/main/resources/assets/soulless/textures/item/leviathan_scale.png")
