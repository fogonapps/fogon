"""Pantallas de la propuesta de La Cucha (app de alimentación) para el lienzo de diseño.

Usa las piezas del estudio Vidrio y puntos (identidad/vidrio/lienzo/generar.py).
Uso: python3 identidad/cucha/generar_propuesta.py <carpeta project/ del lienzo>
"""
import os, sys
AQUI = os.path.dirname(os.path.abspath(__file__))
base = open(os.path.join(AQUI, '../vidrio/lienzo/generar.py')).read()
exec(base[:base.index('archivos = {')])

PJ_COCINA = '/_blob/d54a52336eb8187cbca281c5ab4dbb2e'
PJ_GRAFITO = '/_blob/6137cf67a9acc67896ac8879bc2fcd25'
I.update({
    'olla': '<path d="M4 10h16v6a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4zM2 10h20M9 6c0-1 1-2 1-3M14 6c0-1 1-2 1-3"/>',
    'libro': '<path d="M4 5.5A1.5 1.5 0 0 1 5.5 4H12v16H5.5A1.5 1.5 0 0 1 4 18.5zM12 4h6.5A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5H12"/>',
    'codigo': '<path d="M4 7V5h3M17 5h3v2M20 17v2h-3M7 19H4v-2M8 9v6M11 9v6M14 9v6M17 9v6"/>',
    'buscar': '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4 4"/>',
    'sol': '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4"/>',
    'luna': '<path d="M19 14.5A7.5 7.5 0 1 1 9.5 5a6 6 0 0 0 9.5 9.5z"/>',
    'cafe': '<path d="M5 9h11v5a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4zM16 10.5h1.5a2.5 2.5 0 0 1 0 5H16M8 5c0-1 1-1.5 1-2.5M12 5c0-1 1-1.5 1-2.5"/>',
    'peso': '<path d="M5 8h14l-1.5 11h-11zM12 8V5M9 13.5 12 11"/>',
    'cerrar': '<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>',
})
COCINA = dict(nombre='Cocina', fondo='#100B08', brillo='rgba(180,80,30,0.38)', vidrio='rgba(255,255,255,0.055)', borde='rgba(255,255,255,0.10)',
              texto='#F6F0EA', suave='#B9ADA3', acento='#FF8A4C', sobre='#1A0E06', t2='#3FBF6A', chip='rgba(255,138,76,0.15)', punto='#FF8A4C',
              ingreso='#7FD99B', oscuro=True)
GRAF = dict(TEMAS['grafito'])

def barra_macro(t, nombre, act, meta, color):
    p = min(100, int(act * 100 / meta))
    return (f'<div style="flex: 1; display: flex; flex-direction: column; gap: 6px"><span style="font-size: 12px; color: {t["suave"]}">{nombre}</span>'
            f'<div style="height: 6px; border-radius: 3px; background: {t["chip"]}"><div style="width: {p}%; height: 6px; border-radius: 3px; background: {color}"></div></div>'
            f'<span style="font-size: 13px; font-weight: 600">{act}<span style="color: {t["suave"]}; font-weight: 400"> / {meta} g</span></span></div>')

def inicio(t, pj):
    comidas = [('cafe', 'Desayuno', 'Arepa con huevo, café con leche', 520), ('sol', 'Almuerzo', 'Bandeja: arroz, fríjoles, carne, maduro', 980), ('luna', 'Comida', 'Todavía nada', 0)]
    filas = ''.join(f'<div style="display: flex; align-items: center; gap: 12px; padding: 10px 0">{chip(t, i, 42)}<div style="flex: 1"><p style="margin: 0; font-size: 15px; font-weight: 500">{n}</p><p style="margin: 0; font-size: 12px; color: {t["suave"]}">{d}</p></div>'
                    + (f'<span style="font-size: 15px; font-weight: 600">{k} kcal</span>' if k else f'<button aria-label="Anotar {n}" style="width: 36px; height: 36px; border-radius: 50%; border: 1px solid {t["borde"]}; background: {t["chip"]}; display: grid; place-items: center">{ico("mas", t["texto"], 18)}</button>')
                    + '</div>' for i, n, d, k in comidas)
    return pagina('La Cucha · Inicio', 390, 844, f'''
{brillo(t)}
<div style="position: relative; padding: 52px 20px 0; display: flex; flex-direction: column; gap: 14px">
<header style="display: flex; align-items: center; gap: 12px">
<div style="flex: 1"><p style="margin: 0; font-size: 13px; color: {t["suave"]}">Sábado 25 de octubre</p><h1 style="margin: 0; font-size: 24px; font-weight: 600; letter-spacing: -0.02em">Hola, Angel</h1></div>
<button aria-label="Escanear código de barras" style="width: 48px; height: 48px; border-radius: 50%; border: 1px solid {t["borde"]}; background: {t["vidrio"]}; display: grid; place-items: center">{ico('codigo', t['texto'], 22)}</button>
</header>
<section style="position: relative; height: 230px; overflow: hidden; {vidrio(t, 30)}">
<div style="position: absolute; left: -30px; top: -10px">{puntos('cu'+t['nombre'], t['punto'], 300, 250, 10, 1.5)}</div>
<div style="position: absolute; left: 22px; top: 20px; display: flex; flex-direction: column; gap: 2px">
<p style="margin: 0; font-size: 14px; color: {t["suave"]}">Te quedan hoy</p>
<p style="margin: 0; font-family: Puntos, monospace; font-weight: 700; font-size: 80px; line-height: 1; color: {t["acento"]}">780</p>
<p style="margin: 0; font-size: 14px; color: {t["suave"]}">kcal de 2.280</p>
<p style="margin: 10px 0 0; display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border-radius: 16px; background: {t["chip"]}; font-size: 13px; width: fit-content">{ico('pesa', t['texto'], 15)} Entrenaste: +320 kcal</p>
</div>
<img src="{pj}" alt="" style="position: absolute; right: 0; bottom: 4px; width: 170px; height: 170px">
</section>
<section style="padding: 16px; {vidrio(t, 24)} display: flex; gap: 14px">
{barra_macro(t, 'Proteína', 96, 150, t['acento'])}{barra_macro(t, 'Carbohidratos', 182, 250, t['t2'])}{barra_macro(t, 'Grasa', 48, 70, '#F5B547')}
</section>
<section style="padding: 4px 16px; {vidrio(t, 24)}">{filas}</section>
</div>
{barra_nav(t, [('inicio','Hoy'),('olla','Recetas'),('mas','Anotar'),('barras','Progreso'),('perfil','Perfil')], 0)}
''', t['fondo'], t['texto'])

def anotar(t):
    rec = [('Arepa de maíz blanco', '1 unidad mediana · 180 kcal'), ('Huevo frito', '1 unidad · 90 kcal'), ('Café con leche', '1 pocillo · 70 kcal'), ('Banano', '1 unidad · 105 kcal')]
    filas = ''.join(f'<div style="display: flex; align-items: center; gap: 12px; padding: 12px 0; border-top: 1px solid {t["borde"]}"><div style="flex: 1"><p style="margin: 0; font-size: 15px; font-weight: 500">{n}</p><p style="margin: 0; font-size: 12px; color: {t["suave"]}">{d}</p></div><button aria-label="Agregar {n}" style="width: 36px; height: 36px; border-radius: 50%; border: none; background: {t["acento"]}; display: grid; place-items: center">{ico("mas", t["sobre"], 18)}</button></div>' for n, d in rec)
    return pagina('La Cucha · Anotar', 390, 844, f'''
{brillo(t, '80%', '-5%', '380px 300px')}
<div style="position: relative; padding: 52px 20px 0; display: flex; flex-direction: column; gap: 14px">
<div style="display: flex; align-items: center; gap: 12px"><button aria-label="Cerrar" style="width: 44px; height: 44px; border-radius: 50%; border: 1px solid {t["borde"]}; background: {t["vidrio"]}; display: grid; place-items: center">{ico('cerrar', t['texto'], 18)}</button><h1 style="margin: 0; font-size: 22px; font-weight: 600">Desayuno</h1></div>
<label style="display: flex; align-items: center; gap: 10px; padding: 0 16px; height: 52px; border-radius: 26px; {vidrio(t, 26)}">{ico('buscar', t['suave'], 20)}<input aria-label="Buscar alimento" placeholder="Buscar: arepa, fríjoles, pandebono…" style="flex: 1; border: none; background: transparent; color: {t["texto"]}; font: 15px 'Bricolage Grotesque', sans-serif; outline: none"></label>
<div style="display: flex; gap: 10px">
<button style="flex: 1; height: 88px; border-radius: 24px; {vidrio(t, 24)} color: {t["texto"]}; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; font: 500 13px 'Bricolage Grotesque', sans-serif">{ico('codigo', t['acento'], 26)}Escanear</button>
<button style="flex: 1; height: 88px; border-radius: 24px; {vidrio(t, 24)} color: {t["texto"]}; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; font: 500 13px 'Bricolage Grotesque', sans-serif">{ico('olla', t['acento'], 26)}Una receta</button>
<button style="flex: 1; height: 88px; border-radius: 24px; {vidrio(t, 24)} color: {t["texto"]}; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; font: 500 13px 'Bricolage Grotesque', sans-serif">{ico('reloj', t['acento'], 26)}Como ayer</button>
</div>
<section style="padding: 4px 16px 4px; {vidrio(t, 24)}"><p style="margin: 12px 0 2px; font-size: 16px; font-weight: 600">Lo que más comes</p>{filas}</section>
<p style="margin: 0; font-size: 12px; color: {t["suave"]}">Los alimentos vienen de la base del USDA (dominio público) con nombres de aquí. Los productos escaneados, de Open Food Facts.</p>
</div>
''', t['fondo'], t['texto'])

def recetas(t, pj):
    rs = [('Ajiaco santafereño', '4 porciones · 45 min', 520, 34), ('Sancocho de pollo', '6 porciones · 1 h 20', 410, 30), ('Arepas de huevo', '2 porciones · 25 min', 380, 16)]
    cartas = ''.join(f'<div style="padding: 16px; {vidrio(t, 24)} display: flex; align-items: center; gap: 14px">{chip(t, "olla", 52, t["acento"])}<div style="flex: 1"><p style="margin: 0; font-size: 16px; font-weight: 600">{n}</p><p style="margin: 0; font-size: 12px; color: {t["suave"]}">{d}</p></div><div style="text-align: right"><p style="margin: 0; font-size: 15px; font-weight: 600">{k} kcal</p><p style="margin: 0; font-size: 12px; color: {t["suave"]}">{p} g proteína</p></div></div>' for n, d, k, p in rs)
    ing = ['600 g de papa criolla', '400 g de papa pastusa', '2 pechugas de pollo', '2 mazorcas', 'Guascas al gusto']
    return pagina('La Cucha · Recetas', 390, 844, f'''
{brillo(t, '30%', '-5%', '420px 320px')}
<div style="position: relative; padding: 52px 20px 0; display: flex; flex-direction: column; gap: 12px">
<h1 style="margin: 0; font-size: 26px; font-weight: 600; letter-spacing: -0.02em">Recetas</h1>
<section style="position: relative; overflow: hidden; height: 200px; {vidrio(t, 30)}">
<div style="position: absolute; left: 20px; top: 18px; width: 200px; display: flex; flex-direction: column; gap: 6px">
<p style="margin: 0; font-size: 13px; color: {t["suave"]}">Tus recetas, con sus calorías por porción</p>
<p style="margin: 0; font-size: 22px; font-weight: 600; line-height: 1.15">Anota la receta una vez y úsala cuando la cocines</p>
<button style="margin-top: 6px; width: fit-content; height: 40px; padding: 0 16px; border: none; border-radius: 20px; background: {t["acento"]}; color: {t["sobre"]}; font: 600 14px 'Bricolage Grotesque', sans-serif">Nueva receta</button>
</div>
<img src="{pj}" alt="" style="position: absolute; right: -6px; bottom: 0; width: 150px; height: 150px">
</section>
{cartas}
<section style="padding: 16px 18px; {vidrio(t, 24)} display: flex; flex-direction: column; gap: 8px">
<p style="margin: 0; font-size: 16px; font-weight: 600">Ajiaco santafereño · 1 porción</p>
<div style="display: flex; gap: 14px">{barra_macro(t, 'Proteína', 34, 150, t['acento'])}{barra_macro(t, 'Carbos', 62, 250, t['t2'])}{barra_macro(t, 'Grasa', 12, 70, '#F5B547')}</div>
<p style="margin: 4px 0 0; font-size: 13px; color: {t["suave"]}">{' · '.join(ing)}</p>
</section>
</div>
{barra_nav(t, [('inicio','Hoy'),('olla','Recetas'),('mas','Anotar'),('barras','Progreso'),('perfil','Perfil')], 1)}
''', t['fondo'], t['texto'])

def progreso(t):
    pesos = [80.4, 80.1, 79.9, 79.8, 79.6, 79.5, 79.3, 79.4, 79.1, 78.9]
    xs = [20 + i * 34 for i in range(len(pesos))]
    ys = [130 - (p - 78.5) * 55 for p in pesos]
    linea = ' '.join(f'{x},{y:.0f}' for x, y in zip(xs, ys))
    dias = ''.join(f'<div style="flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px"><div style="width: 100%; height: 90px; display: flex; align-items: flex-end"><div style="width: 100%; height: {h}%; border-radius: 8px; background: {t["acento"] if h <= 100 else "#FF6B6B"}"></div></div><span style="font-size: 11px; color: {t["suave"]}">{d}</span></div>' for d, h in [('L', 92), ('M', 101), ('X', 88), ('J', 97), ('V', 104), ('S', 70), ('D', 0)])
    return pagina('La Cucha · Progreso', 390, 844, f'''
{brillo(t, '60%', '-5%', '420px 320px')}
<div style="position: relative; padding: 52px 20px 0; display: flex; flex-direction: column; gap: 14px">
<h1 style="margin: 0; font-size: 26px; font-weight: 600; letter-spacing: -0.02em">Progreso</h1>
<section style="padding: 18px; {vidrio(t, 28)} display: flex; flex-direction: column; gap: 6px">
<p style="margin: 0; font-size: 13px; color: {t["suave"]}">Peso (de El Forzudo)</p>
<p style="margin: 0; font-family: Puntos, monospace; font-weight: 700; font-size: 52px; line-height: 1; color: {t["acento"]}">78,9<span style="font-family: 'Bricolage Grotesque', sans-serif; font-size: 16px; font-weight: 500; color: {t["suave"]}"> kg</span></p>
<p style="margin: 0; font-size: 13px; color: {t["suave"]}">−1,5 kg en 9 semanas · meta: bajar 0,25 kg por semana</p>
<svg aria-hidden="true" width="340" height="150" viewBox="0 0 340 150"><polyline points="{linea}" fill="none" stroke="{t["acento"]}" stroke-width="2.5" stroke-linejoin="round"/>{''.join(f'<circle cx="{x}" cy="{y:.0f}" r="3.5" fill="{t["acento"]}"/>' for x, y in zip(xs, ys))}</svg>
</section>
<section style="padding: 16px; {vidrio(t, 24)} display: flex; flex-direction: column; gap: 10px">
<p style="margin: 0; font-size: 16px; font-weight: 600">Esta semana</p>
<div style="display: flex; gap: 8px">{dias}</div>
<p style="margin: 0; font-size: 12px; color: {t["suave"]}">La barra llena es tu meta del día; en rojo, los días que te pasaste.</p>
</section>
<section style="padding: 16px; {vidrio(t, 24)} display: flex; align-items: center; gap: 14px">{chip(t, 'peso', 48)}<div style="flex: 1"><p style="margin: 0; font-size: 15px; font-weight: 600">Tu meta: 2.280 kcal</p><p style="margin: 0; font-size: 12px; color: {t["suave"]}">Calculada con tu peso, estatura, edad y actividad, para bajar despacio. Los días que entrenas suma lo que gastaste.</p></div></section>
</div>
{barra_nav(t, [('inicio','Hoy'),('olla','Recetas'),('mas','Anotar'),('barras','Progreso'),('perfil','Perfil')], 3)}
''', t['fondo'], t['texto'])

salida = sys.argv[1]
archivos = {
    'CuInicio.dc.html': inicio(COCINA, PJ_COCINA),
    'CuAnotar.dc.html': anotar(COCINA),
    'CuRecetas.dc.html': recetas(COCINA, PJ_COCINA),
    'CuProgreso.dc.html': progreso(COCINA),
    'CuGrafito.dc.html': inicio(GRAF, PJ_GRAFITO),
}
for n, c in archivos.items():
    open(os.path.join(salida, n), 'w').write(c)
print(list(archivos))
