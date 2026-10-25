"""Separa cada personaje en capas para teñirlas en la app con las tintas del tema.

Uso (desde la raíz del repo): python3 identidad/vidrio/capas/generar_capas.py
Escribe <nombre>_t1.webp (cuerpo, primera tinta), _t2.webp (segunda tinta, la corrida y la moneda) y
_fijo.webp (chispa y detalles, con su color): los personajes en core/identidad/src/main/res/drawable-nodpi y
los muñequitos del tutorial de El Forzudo en apps/forzudo/src/main/res/drawable-nodpi.
Necesita rsvg-convert y Pillow.
"""
import re, subprocess, tempfile, os
from PIL import Image

FORZUDO = {'t1': ['#2D5BFF'], 't2': ['#FF4FA3']}
# origen (svg) → (carpeta de salida, nombre del recurso, colores de cada capa)
PERSONAJES = {
    'identidad/personajes/el-forzudo.svg': ('core/identidad/src/main/res/drawable-nodpi', 'personaje_el_forzudo', FORZUDO),
    'identidad/personajes/la-cucha.svg': ('core/identidad/src/main/res/drawable-nodpi', 'personaje_la_cucha', {'t1': ['#FF7A3D'], 't2': ['#3FBF6A']}),
    'identidad/personajes/el-ahorrador.svg': ('core/identidad/src/main/res/drawable-nodpi', 'personaje_el_ahorrador', {'t1': ['#1FB45A'], 't2': ['#FFC93C']}),
    **{f'identidad/munecos/tut_{n}.svg': ('apps/forzudo/src/main/res/drawable-nodpi', f'munieco_tut_{n}', FORZUDO)
       for n in ['hola', 'anota', 'racha', 'rangos', 'datos']},
}
LADO = 420

def capa(svg, mostrar, ocultar_resto, colores_todos):
    # Deja en blanco los colores de la capa y vuelve transparentes los demás (o los deja si es la capa fija).
    def cambio(m):
        c = m.group(2).upper()
        if c in mostrar:
            return f'{m.group(1)}="#FFFFFF"'
        if ocultar_resto or c in colores_todos:
            return f'{m.group(1)}="transparent"'
        return m.group(0)
    return re.sub(r'(fill|stroke)="(#[0-9A-Fa-f]{6})"', cambio, svg)

for origen, (salida, nombre, c) in PERSONAJES.items():
    svg = open(origen).read()
    todos = set(c['t1'] + c['t2'])
    capas = {'t1': capa(svg, set(c['t1']), True, todos), 't2': capa(svg, set(c['t2']), True, todos), 'fijo': capa(svg, set(), False, todos)}
    for k, s in capas.items():
        with tempfile.TemporaryDirectory() as d:
            subprocess.run(['rsvg-convert', '-w', str(LADO), '-o', f'{d}/c.png'], input=s.encode(), check=True)
            Image.open(f'{d}/c.png').save(f'{salida}/{nombre}_{k}.webp', 'WEBP', lossless=True)
        print(nombre, k)
