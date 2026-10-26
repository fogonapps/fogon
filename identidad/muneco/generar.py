#!/usr/bin/env python3
"""Genera los personajes y los íconos de las apps a partir del muñeco de Blender (muneco.py).

Uso (desde la raíz del repo): python3 identidad/muneco/generar.py
Necesita Blender (lo corre en segundo plano), Pillow y numpy.

Escribe, para El Forzudo, El Ahorrador, La Cucha y El Cajón:
- core/identidad/src/main/res/drawable-nodpi/personaje_<nombre>_cuerpo.webp: el cuerpo en grises (las caras de los
  bloques: blanco lo que da a la luz, más oscuro lo que no), que la app tiñe con las letras del tema
  (`PersonajeFogon`); y _cabeza.webp: la cabeza (la chispa) en blanco, que se tiñe con el color de la app;
- core/identidad/src/main/res/drawable-nodpi/personaje_<nombre>.webp e identidad/muneco/png/<nombre>.png: el
  personaje armado (cuerpo oscuro, cabeza en el color de la app), por si algo lo muestra sin teñir;
- apps/forzudo/src/main/res/drawable-nodpi/munieco_tut_<paso>_cuerpo.webp y _cabeza.webp: el tutorial;
- el ícono de cada app: apps/<app>/src/main/res/mipmap-*/ic_launcher_foreground.webp (el muñeco oscuro con la
  cabeza en su color, en la zona segura, sobre el fondo blanco de `@color/icono_fondo`) y _monochrome.webp (para los íconos temáticos), y el de la tienda en
  apps/<app>/fastlane/metadata/android/en-US/images/icon.png (512 px, con el fondo).
Los cuatro personajes salen del mismo recorte (misma escala), así juntos se ven del mismo tamaño; los del
tutorial, de otro recorte común entre ellos; en los íconos, cada uno llena su zona segura.
"""
import os, subprocess, tempfile
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

RAIZ = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../.."))
NOMBRES = {"el-forzudo": "el_forzudo", "el-ahorrador": "el_ahorrador", "la-cucha": "la_cucha", "el-cajon": "el_cajon"}
TUTORIAL = {f"tut-{p}": f"munieco_tut_{p}" for p in ("hola", "anota", "racha", "rangos", "datos")}
APP = {"el-forzudo": "forzudo", "el-ahorrador": "ahorrador", "la-cucha": "cucha", "el-cajon": "cajon"}
# La tinta de cada app (la de su tema) y el fondo oscuro de su ícono (el de su tema).
COLOR = {"el-forzudo": "#C4E02E", "el-ahorrador": "#F2384D", "la-cucha": "#FF8A4C", "el-cajon": "#9B7BFF"}
# El ícono va en blanco con el muñeco oscuro (Angel, 25-10-2026); la cabeza en el color de la app, o en su versión
# para papel si ese color no se ve sobre blanco (el lima de El Forzudo se vuelve oliva, como en el tema Claro).
FONDO_ICONO = "#FFFFFF"
CABEZA_ICONO = {"el-forzudo": "#4E6312", "el-ahorrador": "#F2384D", "la-cucha": "#FF8A4C", "el-cajon": "#9B7BFF"}
PAPEL, TINTA = (236, 233, 226), (22, 21, 24)
LADO_APP, LADO_PNG = 600, 1200
DENSIDADES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def alfa(p): return np.asarray(Image.open(p).getchannel("A"), dtype=np.float32) / 255
def imagen(a): return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))


def recortes(r, nombres):
    """Un mismo cuadrado para todos (el del más grande), centrado en cada uno."""
    cajas = {}
    for n in nombres:
        a = np.maximum(alfa(f"{r}/{n}-cuerpo.png"), alfa(f"{r}/{n}-cabeza.png"))
        ys, xs = np.nonzero(a > 0.5); cajas[n] = (xs.min(), ys.min(), xs.max(), ys.max())
    lado = int(max(max(c[2] - c[0], c[3] - c[1]) for c in cajas.values()) * 1.06)
    return {n: ((c[0] + c[2]) // 2 - lado // 2, (c[1] + c[3]) // 2 - lado // 2, lado) for n, c in cajas.items()}


def cortar(im, rc, lado_salida):
    x, y, lado = rc
    return im.crop((x, y, x + lado, y + lado)).resize((lado_salida, lado_salida), Image.LANCZOS)


def capas(r, n, rc, lado):
    """El cuerpo (alfa y tono de 0 a 1: 1 lo más iluminado) y la cabeza, separados por un borde alrededor de la cabeza."""
    cab = np.asarray(cortar(Image.open(f"{r}/{n}-cabeza.png").getchannel("A"), rc, lado), dtype=np.float32) / 255
    cue = np.asarray(cortar(Image.open(f"{r}/{n}-cuerpo.png").getchannel("A"), rc, lado), dtype=np.float32) / 255
    halo = np.asarray(imagen(cab).filter(ImageFilter.MaxFilter(max(3, lado // 85) * 2 + 1)), dtype=np.float32) / 255
    cue = np.clip(cue - halo, 0, 1)
    lum = np.asarray(cortar(Image.open(f"{r}/{n}-luz.png").convert("RGBA"), rc, lado).convert("L"), dtype=np.float32) / 255
    tono = np.clip((lum - 0.3) / 0.62, 0, 1)
    return cue, tono, cab


def gris(cue, tono):
    """La capa del cuerpo para la app: el tono en gris, con el cuerpo como transparencia."""
    g = (tono * 255).astype(np.uint8)
    return Image.merge("RGBA", (Image.fromarray(g), Image.fromarray(g), Image.fromarray(g), imagen(cue)))


def blanco(a):
    im = Image.new("RGBA", a.shape[::-1], (255, 255, 255, 0)); im.putalpha(imagen(a)); return im


def armado(cue, tono, cab, color, cuerpo=TINTA):
    """El personaje teñido como en un tema claro: el cuerpo oscuro con sus caras, la cabeza en su color."""
    lado = cue.shape[0]
    c = np.array(cuerpo, dtype=np.float32); f = np.array(PAPEL, dtype=np.float32)
    rgb = (c + (f - c) * 0.45 * (1 - tono)[..., None]).astype(np.uint8)
    im = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    im.paste(Image.fromarray(rgb).convert("RGBA"), (0, 0), imagen(cue))
    im.paste(Image.new("RGBA", (lado, lado), color), (0, 0), imagen(cab))
    return im


def iconos(r, n, rc):
    """El ícono de la app: el muñeco plano (cuerpo oscuro, cabeza en su color) en la zona segura del adaptativo."""
    app = APP[n]
    cue, _, cab = capas(r, n, rc, 1024)
    figura = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    figura.paste(Image.new("RGBA", (1024, 1024), TINTA + (255,)), (0, 0), imagen(cue))
    figura.paste(Image.new("RGBA", (1024, 1024), CABEZA_ICONO[n]), (0, 0), imagen(cab))
    silueta = blanco(np.maximum(cue, cab))
    for d, lado in DENSIDADES.items():
        carpeta = os.path.join(RAIZ, f"apps/{app}/src/main/res/mipmap-{d}")
        os.makedirs(carpeta, exist_ok=True)
        caja = int(lado * 64 / 108)          # dentro del círculo seguro de 66 dp
        for nombre, im in (("ic_launcher_foreground", figura), ("ic_launcher_monochrome", silueta)):
            lienzo = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
            lienzo.alpha_composite(im.resize((caja, caja), Image.LANCZOS), ((lado - caja) // 2, (lado - caja) // 2))
            lienzo.save(f"{carpeta}/{nombre}.webp", "WEBP", lossless=True)
    # El de la tienda: cuadrado, con su fondo (F-Droid lo recorta solo).
    tienda = Image.new("RGBA", (512, 512), FONDO_ICONO)
    caja = int(512 * 64 / 108)
    tienda.alpha_composite(figura.resize((caja, caja), Image.LANCZOS), ((512 - caja) // 2, (512 - caja) // 2))
    tienda.convert("RGB").save(os.path.join(RAIZ, f"apps/{app}/fastlane/metadata/android/en-US/images/icon.png"))


if __name__ == "__main__":
    salida = os.path.join(RAIZ, "core/identidad/src/main/res/drawable-nodpi")
    tutorial = os.path.join(RAIZ, "apps/forzudo/src/main/res/drawable-nodpi")
    os.makedirs(os.path.join(RAIZ, "identidad/muneco/png"), exist_ok=True)
    with tempfile.TemporaryDirectory() as r:
        subprocess.run(["blender", "-b", "-P", os.path.join(RAIZ, "identidad/muneco/muneco.py"), "--", r],
                       check=True, stdout=subprocess.DEVNULL)
        rc = recortes(r, NOMBRES)
        for n, recurso in NOMBRES.items():
            cue, tono, cab = capas(r, n, rc[n], LADO_APP)
            gris(cue, tono).save(f"{salida}/personaje_{recurso}_cuerpo.webp", "WEBP", lossless=True)
            blanco(cab).save(f"{salida}/personaje_{recurso}_cabeza.webp", "WEBP", lossless=True)
            armado(cue, tono, cab, COLOR[n]).save(f"{salida}/personaje_{recurso}.webp", "WEBP", lossless=True)
            armado(*capas(r, n, rc[n], LADO_PNG), COLOR[n]).save(os.path.join(RAIZ, f"identidad/muneco/png/{n}.png"))
            iconos(r, n, recortes(r, [n])[n])   # en el ícono, cada uno llena su zona segura
            print(n)
        rc = recortes(r, TUTORIAL)
        for n, recurso in TUTORIAL.items():
            cue, tono, cab = capas(r, n, rc[n], LADO_APP)
            gris(cue, tono).save(f"{tutorial}/{recurso}_cuerpo.webp", "WEBP", lossless=True)
            blanco(cab).save(f"{tutorial}/{recurso}_cabeza.webp", "WEBP", lossless=True)
            print(n)
