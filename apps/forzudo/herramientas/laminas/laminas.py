#!/usr/bin/env python3
"""Láminas riso de los ejercicios de El Forzudo, a partir de las ilustraciones de Everkinetic.

Capas, de abajo hacia arriba: papel con grano, sombra corrida en el color de la zona del
cuerpo, la figura por dentro del mismo papel, el músculo que trabaja con su color, la
línea en tinta y el número del paso (1 inicio, 2 final) en Anton con la chispa.

Uso: python3 laminas.py <carpeta con los SVG de Everkinetic> ../../src/main/assets/laminas [id1,id2,...]

La carpeta lleva también una subcarpeta commons/ con los dibujos de Everkinetic que solo están en
Wikimedia Commons (ver README.md, "De dónde salen los dibujos").

Sin la lista de ids genera todas y escribe también assets/datos/laminas.json.

Necesita rsvg-convert, Pillow y numpy. Ver README.md de esta carpeta.
"""
import io
import math
import os
import re
import subprocess
import sys

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ejercicios import EJERCICIOS  # noqa: E402

PAPEL, TINTA, ROJO = (239, 231, 218), (20, 18, 16), (229, 51, 42)
# Tamaño en la app: suficiente para la pantalla de un teléfono y liviano (ver README, "Peso").
ANCHO_FINAL, CALIDAD = 540, 72
ANTON = os.path.join(os.path.dirname(__file__), '../../../../identidad/fuentes/anton/Anton-Regular.ttf')

# Un color por grupo muscular (tintas riso). Son los mismos ids de musculos.json.
MUSCULOS = {
    'pecho': ('Pecho', '#FF7A1A'),
    'espalda': ('Espalda', '#1FB45A'),
    'hombros': ('Hombros', '#FFC93C'),
    'biceps': ('Bíceps', '#00A0E9'),  # no rosa: el rosa es la sombra del tren superior
    'triceps': ('Tríceps', '#7B4DFF'),
    'cuads': ('Cuádriceps', '#2FC1B2'),
    'femorales': ('Femorales y glúteos', '#E5332A'),
    'pantorrillas': ('Pantorrillas', '#B8733A'),
    'abdomen': ('Abdomen', '#A8D930'),
}

# La sombra corrida cambia según la zona del cuerpo que trabaja el ejercicio.
ZONAS = {
    'superior': ('Tren superior', '#FF4FA3', ('pecho', 'espalda', 'hombros', 'abdomen')),
    'brazos': ('Brazos', '#FFC93C', ('biceps', 'triceps')),
    'piernas': ('Piernas', '#2D5BFF', ('cuads', 'femorales', 'pantorrillas')),
}


def zona_de(musculo):
    return next(z for z, (_, _, ms) in ZONAS.items() if musculo in ms)


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def rasterizar(svg, ancho):
    png = subprocess.run(['rsvg-convert', '-w', str(ancho), '-'], input=svg.encode(), capture_output=True, check=True).stdout
    return Image.open(io.BytesIO(png)).convert('RGBA')


def silueta(lineas, cerrar=7, huecos=()):
    """Todo lo que no es fondo. Engorda la línea para cerrar huecos, llena el fondo desde el borde
    (y desde los puntos de 'huecos', que son fondo encerrado, como entre los brazos y la barra)
    y lo invierte."""
    a = lineas.point(lambda v: 255 if v > 60 else 0)
    gorda = a.filter(ImageFilter.MaxFilter(cerrar * 2 + 1))
    w, h = gorda.size
    lienzo = Image.new('L', (w + 2, h + 2), 0)
    lienzo.paste(gorda, (1, 1))
    for p in [(0, 0)] + [(x + 1, y + 1) for x, y in huecos]:
        if lienzo.getpixel(p) == 0:
            ImageDraw.floodfill(lienzo, p, 128)
    fondo = lienzo.crop((1, 1, w + 1, h + 1)).point(lambda v: 255 if v == 128 else 0)
    sil = ImageChops.invert(fondo).filter(ImageFilter.MinFilter(cerrar * 2 - 1))
    return ImageChops.lighter(sil, a)


def grano(tam, fuerza=14, semilla=7):
    rng = np.random.default_rng(semilla)
    n = rng.normal(0, fuerza, (tam[1], tam[0])).clip(-40, 40)
    base = np.array(PAPEL, dtype=float)[None, None, :] + n[:, :, None]
    return Image.fromarray(base.clip(0, 255).astype('uint8'), 'RGB')


def pintar(base, mascara, color):
    base.paste(Image.new('RGB', base.size, color), (0, 0), mascara)


def chispa(dr, cx, cy, r, color):
    pts = []
    for i in range(16):
        ang = math.pi * i / 8
        rr = r if i % 4 == 0 else (r * 0.18 if i % 2 else r * 0.32)
        pts.append((cx + rr * math.sin(ang), cy - rr * math.cos(ang)))
    dr.polygon(pts, fill=color)


def lamina(svg_txt, manchas, musculo, paso, ancho=900, huecos=()):
    """Una lámina. manchas: elipses (cx, cy, rx, ry, giro) en coordenadas del viewBox del SVG."""
    vb = [float(x) for x in re.search(r'viewBox="([^"]+)"', svg_txt).group(1).split()]
    esc = ancho / vb[2]
    # La línea es el grupo que no es blanco (casi siempre #333; algunos dibujos usan otro gris oscuro).
    # Los dibujos de Everkinetic subidos a Wikimedia Commons no tienen ese grupo: son una sola capa negra.
    grupos = [m.group(2) for m in re.finditer(r'<g fill="(#[0-9a-fA-F]+)">(.*?)</g>', svg_txt, re.S) if m.group(1).lower() not in ('#fff', '#ffffff')]
    lineas = grupos[0] if grupos else re.search(r'<svg[^>]*>(.*)</svg>', svg_txt, re.S).group(1)
    caja = ' '.join(map(str, vb))
    L = rasterizar(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{caja}"><g fill="#000">{lineas}</g></svg>', ancho).split()[3]
    W, H = L.size
    margen = int(0.06 * W)
    tam = (W + 2 * margen, H + 2 * margen)

    def pad(m):
        c = Image.new('L', tam, 0)
        c.paste(m, (margen, margen))
        return c

    L = pad(L)
    S = silueta(L, huecos=[(int(x * esc) + margen, int(y * esc) + margen) for x, y in huecos])
    elipses = ''.join(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" transform="rotate({g} {cx} {cy})"/>' for cx, cy, rx, ry, g in manchas)
    m_svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{caja}"><defs>'
             '<filter id="w" filterUnits="userSpaceOnUse" x="-50" y="-50" width="500" height="500">'
             '<feTurbulence type="fractalNoise" baseFrequency="0.06" numOctaves="2" seed="3"/>'
             '<feDisplacementMap in="SourceGraphic" scale="6"/></filter></defs>'
             f'<g fill="#000" filter="url(#w)">{elipses}</g></svg>')
    M = ImageChops.multiply(pad(rasterizar(m_svg, ancho).split()[3]), S)

    img = grano(tam)
    papel = img.copy()
    sombra = hexrgb(ZONAS[zona_de(musculo)][1])
    corr = int(W * 0.018)
    pintar(img, ImageChops.offset(S, corr, int(corr * 0.85)), sombra)  # sombra corrida
    img.paste(papel, (0, 0), S)                                       # por dentro, el mismo papel
    pintar(img, M, hexrgb(MUSCULOS[musculo][1]))                       # músculo
    pintar(img, L, TINTA)                                              # línea

    dr = ImageDraw.Draw(img)
    letra = ImageFont.truetype(ANTON, int(W * 0.13))
    x0, y0 = int(margen * 0.6), int(margen * 0.5)
    dr.text((x0 + corr, y0 + corr), str(paso), font=letra, fill=sombra)
    dr.text((x0, y0), str(paso), font=letra, fill=TINTA)
    bb = dr.textbbox((x0, y0), str(paso), font=letra)
    chispa(dr, bb[2] + W * 0.035, bb[1] + W * 0.02, W * 0.035, ROJO)
    return img


DATOS = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../src/main/assets/datos/ejercicios.json')

if __name__ == '__main__':
    import json
    origen, salida = sys.argv[1], sys.argv[2]
    solo = set(sys.argv[3].split(',')) if len(sys.argv) > 3 else None
    musculo = {e['id']: e['m'] for e in json.load(open(DATOS))}
    os.makedirs(salida, exist_ok=True)
    for ej, cuadros in EJERCICIOS.items():
        if solo and ej not in solo:
            continue
        for paso, (archivo, manchas, huecos) in enumerate(cuadros, 1):
            img = lamina(open(os.path.join(origen, archivo + '.svg')).read(), manchas, musculo[ej], paso, huecos=huecos)
            img = img.resize((ANCHO_FINAL, round(img.height * ANCHO_FINAL / img.width)), Image.LANCZOS)
            img.save(os.path.join(salida, f'{ej}-{paso}.webp'), quality=CALIDAD, method=6)
    if not solo:
        # Lista de ejercicios con lámina, que lee la app (Biblioteca.laminas).
        with open(os.path.join(os.path.dirname(DATOS), 'laminas.json'), 'w') as f:
            json.dump(sorted(EJERCICIOS), f, ensure_ascii=False, indent=1)
            f.write('\n')
