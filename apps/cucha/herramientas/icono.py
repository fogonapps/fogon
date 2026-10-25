#!/usr/bin/env python3
"""Ícono de La Cucha (adaptativo): el personaje de identidad/png/la-cucha.png en el centro seguro (66 de 108 dp)
y su silueta en una tinta para el ícono monocromo de Android 13. Uso: python3 icono.py (desde cualquier carpeta)."""
import os
from PIL import Image
RAIZ = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../.."))
fig = Image.open(os.path.join(RAIZ, "identidad/png/la-cucha.png")).convert("RGBA")
fig = fig.crop(fig.getbbox())
for nombre, lado in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
    caja = int(lado * 62 / 108)  # dentro del círculo seguro de 66 dp, con un poco de aire
    f = fig.copy(); f.thumbnail((caja, caja), Image.LANCZOS)
    lienzo = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    lienzo.alpha_composite(f, ((lado - f.width) // 2, (lado - f.height) // 2))
    carpeta = os.path.join(RAIZ, f"apps/cucha/src/main/res/mipmap-{nombre}")
    os.makedirs(carpeta, exist_ok=True)
    lienzo.save(os.path.join(carpeta, "ic_launcher_foreground.webp"), lossless=True)
    mono = Image.new("RGBA", lienzo.size, (255, 255, 255, 0)); mono.putalpha(lienzo.getchannel("A"))
    mono.save(os.path.join(carpeta, "ic_launcher_monochrome.webp"), lossless=True)
print("listo")
