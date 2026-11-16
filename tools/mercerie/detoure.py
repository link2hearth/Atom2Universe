"""Détoure un objet photographié sur fond clair uni et l'exporte en WebP carré.

Usage : python detoure.py <source.png> <sortie.webp> <taille> [--trous] [--fin] [--sol] [--metal] [--disque]

Le fond est retiré depuis les bords : on avance tant que l'image reste claire, sans couleur et
sans relief (une ombre douce), et on s'arrête au contour de l'objet. Cela marche aussi pour un
objet gris sur fond blanc ou blanc sur fond gris ; un objet blanc sur fond blanc, lui, ne se
détoure pas proprement.
  --trous : retire aussi le blanc enfermé dans une boucle (brin de laine).
  --fin   : garde les détails fins et clairs (tiges d'épingles).
  --sol   : retire l'ombre grise posée sous l'objet.
  --metal : objet fin et brillant (épingle) : tout ce qui n'est pas blanc est gardé.
  --disque : objet rond et clair sur fond gris (pompon) : découpe le disque qu'il occupe.
"""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

args = [a for a in sys.argv[1:] if not a.startswith("--")]
flags = {a for a in sys.argv[1:] if a.startswith("--")}
src, out, size = args[0], args[1], int(args[2])

im = Image.open(src).convert("RGB")
a = np.asarray(im).astype(np.float32)
mx = a.max(axis=2)
mn = a.min(axis=2)
sat = mx - mn
# Le fond n'est pas toujours blanc pur : on mesure sa clarté sur le tour de l'image.
edge = np.concatenate([mn[0], mn[-1], mn[:, 0], mn[:, -1]])
white = float(np.median(edge))

if "--disque" in flags:
    # Objet rond et clair sur fond gris (pompon) : on repère sa partie claire, qui donne sa
    # largeur et son sommet, et on découpe le disque correspondant, au bord un peu flou.
    bright = Image.fromarray(((mn > white + 14) * 255).astype(np.uint8), "L").filter(ImageFilter.MinFilter(7))
    x0, y0, x1, y1 = bright.getbbox()
    rad = (x1 - x0) / 2.0 * 0.985
    cx, cy = (x0 + x1) / 2.0, y0 + (x1 - x0) / 2.0
    yy, xx = np.mgrid[0:a.shape[0], 0:a.shape[1]]
    al = np.clip((rad - np.hypot(xx - cx, yy - cy)) / 5.0, 0.0, 1.0).astype(np.float32)
elif "--metal" in flags:
    al = np.clip((white - 22 - mn) / 30.0, 0.0, 1.0)
    alpha = Image.fromarray((al * 255).astype(np.uint8), "L")
    # Referme les reflets blancs à l'intérieur du fil.
    alpha = alpha.filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.MinFilter(9)).filter(ImageFilter.GaussianBlur(0.7))
    al = np.asarray(alpha).astype(np.float32) / 255.0
else:
    lum = np.asarray(im.convert("L").filter(ImageFilter.GaussianBlur(1.6))).astype(np.float32)
    gx = np.zeros_like(lum)
    gy = np.zeros_like(lum)
    gx[:, 1:-1] = lum[:, 2:] - lum[:, :-2]
    gy[1:-1, :] = lum[2:, :] - lum[:-2, :]
    grad = np.hypot(gx, gy)
    # Le fond n'est jamais plus clair que lui-même : un objet blanc sur fond gris reste entier.
    dim = mn < white + 10
    pure = (np.abs(mn - white) < 6) & (sat < 8)
    soft = (sat < 26) & (mn > white - 110) & dim & (grad < 5.0)
    cand = pure | soft
    mask = Image.fromarray((cand * 255).astype(np.uint8), "L").copy()
    w, h = mask.size
    for x in range(0, w, 8):
        for y in (0, h - 1):
            if mask.getpixel((x, y)) == 255:
                ImageDraw.floodfill(mask, (x, y), 128)
    for y in range(0, h, 8):
        for x in (0, w - 1):
            if mask.getpixel((x, y)) == 255:
                ImageDraw.floodfill(mask, (x, y), 128)
    bg = np.asarray(mask) == 128
    if "--trous" in flags:
        bg = bg | pure
    # Le remplissage s'arrête un peu avant le contour : on le pousse de quelques pixels.
    grown = Image.fromarray((bg * 255).astype(np.uint8), "L").filter(ImageFilter.MaxFilter(5))
    near = (np.asarray(grown) > 0) & (sat < 26) & (mn > white - 70) & dim
    if "--fin" not in flags:
        bg = bg | near
    if "--sol" in flags:
        # Ombre portée au sol : tout ce qui est gris clair dans le bas de l'objet.
        rows = np.where((~bg).any(axis=1))[0]
        floor = rows[0] + int((rows[-1] - rows[0]) * 0.86)
        low = np.zeros_like(bg)
        low[floor:, :] = True
        bg = bg | (low & (sat < 16) & (mn > white - 90))
    alpha = Image.fromarray(((~bg) * 255).astype(np.uint8), "L")
    # Retire les miettes isolées, puis adoucit le bord.
    alpha = alpha.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(1.3))
    al = np.asarray(alpha).astype(np.float32) / 255.0
    al = np.clip((al - 0.3) / 0.6, 0.0, 1.0)

safe = np.maximum(al, 0.1)[..., None]
rgb = np.clip((a - (1.0 - al[..., None]) * white) / safe, 0, 255)
rgba = np.dstack([rgb, al * 255.0]).astype(np.uint8)
res = Image.fromarray(rgba, "RGBA")
box = res.getchannel("A").point(lambda v: 255 if v > 20 else 0).getbbox()
res = res.crop(box)
side = int(max(res.size) * 1.04)
sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
sq.paste(res, ((side - res.width) // 2, (side - res.height) // 2))
sq = sq.resize((size, size), Image.LANCZOS)
sq.save(out, "WEBP", quality=95, method=3)
chk = Image.open(out)
print("ok", out, chk.size, chk.mode, "objet", box)
