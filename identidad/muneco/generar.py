#!/usr/bin/env python3
"""Genera los personajes de las apps a partir del muñeco de Blender (muneco.py).

Uso (desde la raíz del repo): python3 identidad/muneco/generar.py
Necesita Blender (lo corre en segundo plano), rsvg no; Pillow y numpy.

Escribe, para El Forzudo, El Ahorrador, La Cucha y El Cajón:
- core/identidad/src/main/res/drawable-nodpi/personaje_<nombre>_cuerpo.webp y _cabeza.webp: las dos capas en
  blanco, para teñirlas en la app (el cuerpo con el color de las letras del tema, la cabeza con su tinta);
- core/identidad/src/main/res/drawable-nodpi/personaje_<nombre>.webp: el personaje armado (cuerpo oscuro y la
  cabeza en el color de la app), por si algo lo muestra sin teñir;
- identidad/muneco/png/<nombre>.png: lo mismo en grande, con fondo transparente;
- apps/forzudo/src/main/res/drawable-nodpi/munieco_tut_<paso>_cuerpo.webp y _cabeza.webp: los muñequitos del
  tutorial de El Forzudo (el mismo muñeco), en dos capas.
Los cuatro personajes salen del mismo recorte (misma escala), así juntos se ven del mismo tamaño; los del
tutorial, de otro recorte común entre ellos.
"""
import os, subprocess, tempfile
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

RAIZ = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../.."))
NOMBRES = {"el-forzudo": "el_forzudo", "el-ahorrador": "el_ahorrador", "la-cucha": "la_cucha", "el-cajon": "el_cajon"}
TUTORIAL = {f"tut-{p}": f"munieco_tut_{p}" for p in ("hola", "anota", "racha", "rangos", "datos")}
# La tinta de cada app (la de su tema), para el personaje armado.
COLOR = {"el-forzudo": "#C4E02E", "el-ahorrador": "#F2384D", "la-cucha": "#FF8A4C", "el-cajon": "#9B7BFF"}
TINTA = (22, 21, 24)
APP, GRANDE = 600, 1200      # lado de los recursos de la app y de los PNG de identidad
PASO = 15                    # separación de los puntos del semitono en el PNG grande (en la app, la mitad)


def alfa(p): return np.asarray(Image.open(p).getchannel("A"), dtype=np.float32) / 255
def imagen(a): return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))


def recortes(r, nombres):
    """Un mismo cuadrado para todos (el del más grande), centrado en cada uno."""
    cajas = {}
    for n in nombres:
        a = np.maximum(alfa(f"{r}/cuerpo-{n}.png"), alfa(f"{r}/cabeza-{n}.png"))
        ys, xs = np.nonzero(a > 0.5); cajas[n] = (xs.min(), ys.min(), xs.max(), ys.max())
    lado = int(max(max(c[2] - c[0], c[3] - c[1]) for c in cajas.values()) * 1.08)
    return {n: ((c[0] + c[2]) // 2 - lado // 2, (c[1] + c[3]) // 2 - lado // 2, lado) for n, c in cajas.items()}


def cortar(a, rc, lado_salida):
    x, y, lado = rc
    return np.asarray(imagen(a).crop((x, y, x + lado, y + lado)).resize((lado_salida, lado_salida), Image.LANCZOS), dtype=np.float32) / 255


def semitono(a, paso):
    """Puntos en filas alternadas; cada punto tan grande como lleno está el cuerpo ahí."""
    suave = np.asarray(imagen(a).filter(ImageFilter.GaussianBlur(paso * 0.4)), dtype=np.float32) / 255
    lado = a.shape[0]
    im = Image.new("L", (lado, lado), 0); d = ImageDraw.Draw(im)
    for y in range(paso // 2, lado, paso):
        for x in range(paso // 2 + (paso // 2 if (y // paso) % 2 else 0), lado, paso):
            v = suave[y, x]
            if v > 0.08:
                r = paso * 0.5 * v ** 0.7
                d.ellipse((x - r, y - r, x + r, y + r), fill=255)
    return np.asarray(im, dtype=np.float32) / 255


def separar(cuerpo, cabeza, borde):
    """Quita del cuerpo un borde alrededor de la cabeza, para que la chispa se lea suelta."""
    halo = np.asarray(imagen(cabeza).filter(ImageFilter.MaxFilter(borde * 2 + 1)), dtype=np.float32) / 255
    return np.clip(cuerpo - halo, 0, 1)


def capas(r, n, rc, lado):
    cab = cortar(alfa(f"{r}/cabeza-{n}.png"), rc, lado)
    cue = semitono(separar(cortar(alfa(f"{r}/cuerpo-{n}.png"), rc, lado), cab, max(3, lado // 85)), max(5, PASO * lado // GRANDE))
    return cue, cab


def armado(cue, cab, color):
    lado = cue.shape[0]
    im = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    im.paste(Image.new("RGBA", (lado, lado), TINTA + (255,)), (0, 0), imagen(cue))
    im.paste(Image.new("RGBA", (lado, lado), color), (0, 0), imagen(cab))
    return im


def blanco(a):
    im = Image.new("RGBA", a.shape[::-1], (255, 255, 255, 0)); im.putalpha(imagen(a)); return im


if __name__ == "__main__":
    salida = os.path.join(RAIZ, "core/identidad/src/main/res/drawable-nodpi")
    os.makedirs(os.path.join(RAIZ, "identidad/muneco/png"), exist_ok=True)
    with tempfile.TemporaryDirectory() as r:
        subprocess.run(["blender", "-b", "-P", os.path.join(RAIZ, "identidad/muneco/muneco.py"), "--", r],
                       check=True, stdout=subprocess.DEVNULL)
        rc = recortes(r, NOMBRES)
        for n, recurso in NOMBRES.items():
            cue, cab = capas(r, n, rc[n], APP)
            blanco(cue).save(f"{salida}/personaje_{recurso}_cuerpo.webp", "WEBP", lossless=True)
            blanco(cab).save(f"{salida}/personaje_{recurso}_cabeza.webp", "WEBP", lossless=True)
            armado(cue, cab, COLOR[n]).save(f"{salida}/personaje_{recurso}.webp", "WEBP", lossless=True)
            armado(*capas(r, n, rc[n], GRANDE), COLOR[n]).save(os.path.join(RAIZ, f"identidad/muneco/png/{n}.png"))
            print(n)
        rc = recortes(r, TUTORIAL)
        tutorial = os.path.join(RAIZ, "apps/forzudo/src/main/res/drawable-nodpi")
        for n, recurso in TUTORIAL.items():
            cue, cab = capas(r, n, rc[n], APP)
            blanco(cue).save(f"{tutorial}/{recurso}_cuerpo.webp", "WEBP", lossless=True)
            blanco(cab).save(f"{tutorial}/{recurso}_cabeza.webp", "WEBP", lossless=True)
            print(n)
