import json, sys, os
OUT = sys.argv[1]
FUENTES = '<link rel="preconnect" href="https://fonts.googleapis.com"><link href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,400;12..96,500;12..96,600;12..96,700&amp;display=swap" rel="stylesheet">'
PJ = {
 'fz_gimnasio': '/_blob/8310618479cb87470fd935e333cdc37a', 'fz_grafito': '/_blob/019a57fb7559bbd0927e96950d105d5e', 'fz_claro': '/_blob/1bcd78260ba9929e1d5b4709e18ca214',
 'ah_vino': '/_blob/c4e612629839b8024fab68d93cdb23ec', 'ah_grafito': '/_blob/c13e6566bd050d20b03bbecf4f22aa89', 'ah_claro': '/_blob/a8cf295e958059ae8430454ad10a3cda',
}
TEMAS = {
 'gimnasio': dict(nombre='Gimnasio', fondo='#0C0D09', brillo='rgba(140,170,30,0.30)', vidrio='rgba(255,255,255,0.055)', borde='rgba(255,255,255,0.10)', texto='#F1F3EA', suave='#A3A893', acento='#CDEB3A', sobre='#141609', t2='#5C7A12', chip='rgba(205,235,58,0.14)', punto='#CDEB3A', ingreso='#7FD99B', oscuro=True),
 'vino': dict(nombre='Vino', fondo='#0D0809', brillo='rgba(170,24,48,0.42)', vidrio='rgba(255,255,255,0.055)', borde='rgba(255,255,255,0.10)', texto='#F6EFEF', suave='#B8A6A8', acento='#F2384D', sobre='#FFFFFF', t2='#F5B547', chip='rgba(242,56,77,0.16)', punto='#F2384D', ingreso='#6FD69A', oscuro=True),
 'grafito': dict(nombre='Grafito', fondo='#0E0E10', brillo='rgba(255,255,255,0.10)', vidrio='rgba(255,255,255,0.06)', borde='rgba(255,255,255,0.11)', texto='#F2F2F3', suave='#A0A0A7', acento='#EDEDEF', sobre='#141416', t2='#5E5E66', chip='rgba(255,255,255,0.08)', punto='#EDEDEF', ingreso='#7FD99B', oscuro=True),
 'claro_fz': dict(nombre='Claro', fondo='#F3F2EE', brillo='rgba(198,226,54,0.35)', vidrio='rgba(255,255,255,0.72)', borde='rgba(25,24,26,0.08)', texto='#19181A', suave='#625F58', acento='#19181A', sobre='#F3F2EE', t2='#C6E236', chip='rgba(25,24,26,0.06)', punto='#19181A', ingreso='#1E7A45', oscuro=False),
 'claro_ah': dict(nombre='Claro', fondo='#F3F2EE', brillo='rgba(242,56,77,0.16)', vidrio='rgba(255,255,255,0.72)', borde='rgba(25,24,26,0.08)', texto='#19181A', suave='#625F58', acento='#19181A', sobre='#F3F2EE', t2='#F2384D', chip='rgba(25,24,26,0.06)', punto='#19181A', ingreso='#1E7A45', oscuro=False),
}
I = {  # íconos de línea redonda, 24×24
 'inicio': '<path d="M4 11 12 4.5l8 6.5v8.5a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z"/>',
 'pesa': '<path d="M6.5 7v10M17.5 7v10M3.5 9.5v5M20.5 9.5v5M6.5 12h11"/>',
 'medalla': '<path d="M8.5 3.5 12 9l3.5-5.5"/><circle cx="12" cy="14.5" r="5.5"/><path d="m12 12.2.8 1.6 1.7.2-1.3 1.2.4 1.7-1.6-.9-1.6.9.4-1.7-1.3-1.2 1.7-.2z"/>',
 'perfil': '<circle cx="12" cy="8" r="3.8"/><path d="M4.8 20c.8-3.8 3.6-6 7.2-6s6.4 2.2 7.2 6"/>',
 'mas': '<path d="M12 5v14M5 12h14"/>',
 'chulo': '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
 'llama': '<path d="M12.4 3c.6 3.6 5.6 5.4 5.6 10.4a6 6 0 0 1-12 0c0-2.6 1.4-4.2 2.6-5.2-.1 2.2.9 3.6 2.2 3.8-1.2-3.4-.2-6.4 1.6-9z"/>',
 'reloj': '<circle cx="12" cy="13" r="7.5"/><path d="M12 9.5v4l2.6 1.6M9.6 3h4.8"/>',
 'series': '<path d="M5 7h10M5 12h14M5 17h8"/>',
 'flecha': '<path d="M7 17 17 7M9 7h8v8"/>',
 'entra': '<path d="M17 7 7 17M15 17H7V9"/>',
 'pasar': '<path d="M5 9h13l-3-3M19 15H6l3 3"/>',
 'moneda': '<circle cx="12" cy="12" r="8.4"/><path d="M14.4 9.3c-.6-.8-1.5-1.2-2.4-1.2-1.4 0-2.3.8-2.3 1.8 0 2.6 4.8 1.4 4.8 4.1 0 1.1-1 1.9-2.5 1.9-1 0-1.9-.4-2.5-1.2M12 6.6v1.5M12 15.9v1.5"/>',
 'sobre': '<path d="M3.5 6.5h17v11h-17z"/><path d="m4 7.2 8 5.6 8-5.6"/>',
 'barras': '<path d="M5.5 19v-8M12 19V5M18.5 19v-5"/>',
 'carrito': '<path d="M3 5h3l2 10h10l2-7H7"/><circle cx="9" cy="19" r="1.3"/><circle cx="17" cy="19" r="1.3"/>',
 'casa': '<path d="M4 11 12 4.5l8 6.5V20H4z"/>',
 'rayo': '<path d="M13.4 2.6 4.6 13.4h6.2l-1 8 8.8-10.8h-6.2z"/>',
 'bus': '<path d="M5 16.4V8a3 3 0 0 1 3-3h8a3 3 0 0 1 3 3v8.4zM7 8h10v4H7zM8 16.4v3M16 16.4v3"/>',
 'comida': '<path d="M3.6 10.6h16.8c0 4.6-3.2 8-8.4 8s-8.4-3.4-8.4-8zM9 7c0-1.5 1-2.5 3-2.5s3 1 3 2.5"/>',
 'sueldo': '<path d="M3.4 7h17.2v12.4H3.4zM9 7V5h6v2M3.4 12h17.2"/>',
 'ojo': '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="2.8"/>',
}
def ico(n, c, s=22, w=1.9):
    return f'<svg aria-hidden="true" width="{s}" height="{s}" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round">{I[n]}</svg>'
def puntos(id_, color, w=300, h=220, paso=11, r=1.6):
    # Halo de trama: puntos que se apagan hacia el borde (la trama riso, ahora como luz).
    return (f'<svg aria-hidden="true" width="{w}" height="{h}" viewBox="0 0 {w} {h}" style="position: absolute; pointer-events: none">'
            f'<defs><pattern id="p{id_}" width="{paso}" height="{paso}" patternUnits="userSpaceOnUse"><circle cx="{paso/2}" cy="{paso/2}" r="{r}" fill="{color}"/></pattern>'
            f'<radialGradient id="g{id_}"><stop offset="0" stop-color="#fff" stop-opacity="0.75"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>'
            f'<mask id="m{id_}"><rect width="{w}" height="{h}" fill="url(#g{id_})"/></mask></defs>'
            f'<rect width="{w}" height="{h}" fill="url(#p{id_})" mask="url(#m{id_})"/></svg>')
def pagina(titulo, w, h, cuerpo, fondo, texto):
    return f'''<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>{titulo}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
{FUENTES}
<style>
body{{margin:0}}
@font-face{{font-family:'Puntos';src:url(/_blob/3714409fd767843817b0ae21468d14fa) format('woff2');font-weight:700;font-display:block}}
a{{color:inherit}}a:hover{{color:inherit}}
</style>
</helmet>
<div style="width: {w}px; height: {h}px; box-sizing: border-box; position: relative; overflow: hidden; background: {fondo}; color: {texto}; font-family: 'Bricolage Grotesque', system-ui, sans-serif">
{cuerpo}
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{w},"height":{h}}}}}'>
class Component extends DCLogic {{
renderVals() {{ return {{}}; }}
}}
</script>
</body>
</html>
'''
def vidrio(t, r=26, extra=''):
    return f'background: {t["vidrio"]}; border: 1px solid {t["borde"]}; border-radius: {r}px; backdrop-filter: blur(18px); {extra}'
def brillo(t, x='50%', y='-10%', tam='420px 320px'):
    return f'<div aria-hidden="true" style="position: absolute; inset: 0; background: radial-gradient({tam} at {x} {y}, {t["brillo"]}, transparent 70%)"></div>'
def barra_nav(t, items, activo):
    bs = []
    for i,(ic,et) in enumerate(items):
        if i == activo:
            bs.append(f'<button aria-label="{et}" style="height: 48px; padding: 0 16px 0 12px; display: flex; align-items: center; gap: 8px; border: none; border-radius: 24px; background: {t["acento"]}; color: {t["sobre"]}; font: 600 14px \'Bricolage Grotesque\', sans-serif">{ico(ic, t["sobre"], 20)}{et}</button>')
        else:
            bs.append(f'<button aria-label="{et}" style="width: 48px; height: 48px; display: grid; place-items: center; border: none; border-radius: 24px; background: transparent">{ico(ic, t["suave"], 22)}</button>')
    return f'<nav style="position: absolute; left: 20px; right: 20px; bottom: 22px; height: 64px; padding: 0 8px; display: flex; align-items: center; justify-content: space-between; {vidrio(t, 32)} background: {"rgba(30,30,30,0.72)" if t["oscuro"] else "rgba(255,255,255,0.86)"}">{"".join(bs)}</nav>'
def chip(t, ic, s=44, color=None):
    c = color or t['texto']
    return f'<div style="width: {s}px; height: {s}px; flex: none; display: grid; place-items: center; border-radius: 50%; background: {t["chip"]}; border: 1px solid {t["borde"]}">{ico(ic, c, int(s*0.48))}</div>'

def forzudo_inicio(t, pj):
    dias = [('Lun','20',True),('Mar','21',True),('Mié','22',False),('Jue','23',True),('Vie','24','hoy'),('Sáb','25',False),('Dom','26',False)]
    tiras = []
    for d,n,e in dias:
        if e is True:
            est = f'background: {t["acento"]}; color: {t["sobre"]}; border: 1px solid {t["acento"]}'
            marca = ico('chulo', t['sobre'], 14, 2.6)
        elif e == 'hoy':
            est = f'background: {t["vidrio"]}; color: {t["texto"]}; border: 1.5px solid {t["acento"]}'
            marca = f'<span style="width: 6px; height: 6px; border-radius: 3px; background: {t["acento"]}; display: block"></span>'
        else:
            est = f'background: {t["vidrio"]}; color: {t["suave"]}; border: 1px solid {t["borde"]}'
            marca = f'<span style="width: 5px; height: 5px; border-radius: 3px; background: {t["borde"]}; display: block"></span>'
        tiras.append(f'<div style="flex: 1; height: 72px; border-radius: 22px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; {est}"><div style="height: 14px; display: grid; place-items: center">{marca}</div><span style="font-size: 12px">{d}</span><span style="font-size: 15px; font-weight: 600">{n}</span></div>')
    puntitos = ''.join(f'<span style="width: 8px; height: 8px; border-radius: 4px; background: {t["acento"] if i < 3 else t["borde"]}; display: block"></span>' for i in range(5))
    return pagina('El Forzudo · Inicio', 390, 844, f'''
{brillo(t)}
<div style="position: relative; padding: 52px 20px 0; display: flex; flex-direction: column; gap: 18px">
<header style="display: flex; align-items: center; gap: 12px">
<div style="width: 48px; height: 48px; border-radius: 50%; {vidrio(t, 24)} display: grid; place-items: center; overflow: hidden"><img src="{pj}" alt="El Forzudo" style="width: 40px; height: 40px"></div>
<div style="flex: 1"><p style="margin: 0; font-size: 13px; color: {t["suave"]}">Semana 43, viernes 24</p><h1 style="margin: 0; font-size: 24px; font-weight: 600; letter-spacing: -0.02em">Hola, Angel</h1></div>
<button aria-label="Empezar entreno" style="width: 48px; height: 48px; border-radius: 50%; border: 1px solid {t["borde"]}; background: {t["vidrio"]}; display: grid; place-items: center">{ico('mas', t['texto'], 22)}</button>
</header>
<div style="display: flex; gap: 6px">{''.join(tiras)}</div>
<section style="position: relative; height: 230px; overflow: hidden; {vidrio(t, 30)}">
<div style="position: absolute; left: -40px; top: -10px">{puntos('r'+t['nombre'], t['punto'], 300, 250, 10, 1.5)}</div>
<div style="position: absolute; left: 22px; top: 20px; display: flex; flex-direction: column; gap: 2px">
<p style="margin: 0; font-size: 14px; color: {t["suave"]}">Semanas seguidas</p>
<p style="margin: 0; font-family: Puntos, monospace; font-weight: 700; font-size: 112px; line-height: 1; color: {t["acento"]}">12</p>
<p style="margin: 10px 0 0; display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border-radius: 16px; background: {t["chip"]}; font-size: 13px; width: fit-content">{ico('llama', t['texto'], 15)} Mejor racha: 15</p>
</div>
<img src="{pj}" alt="" style="position: absolute; right: 6px; bottom: 6px; width: 170px; height: 170px">
</section>
<div style="display: flex; gap: 12px">
<section style="flex: 1; padding: 16px; {vidrio(t, 24)} display: flex; flex-direction: column; gap: 10px"><p style="margin: 0; font-size: 13px; color: {t["suave"]}">Esta semana</p><p style="margin: 0; font-size: 26px; font-weight: 600">3 de 5</p><div style="display: flex; gap: 5px">{puntitos}</div></section>
<section style="flex: 1; padding: 16px; {vidrio(t, 24)} display: flex; flex-direction: column; gap: 10px"><p style="margin: 0; font-size: 13px; color: {t["suave"]}">Volumen</p><p style="margin: 0; font-size: 26px; font-weight: 600">8.420<span style="font-size: 15px; color: {t["suave"]}"> kg</span></p><p style="margin: 0; font-size: 12px; color: {t["suave"]}">+6 % que la anterior</p></section>
</div>
<section style="padding: 16px; {vidrio(t, 24)} display: flex; align-items: center; gap: 14px">
{chip(t, 'pesa', 48)}
<div style="flex: 1"><p style="margin: 0; font-size: 13px; color: {t["suave"]}">Hoy toca</p><p style="margin: 0; font-size: 18px; font-weight: 600">Torso A</p><p style="margin: 0; font-size: 12px; color: {t["suave"]}">7 ejercicios, unos 55 min</p></div>
<button style="height: 44px; padding: 0 18px; border: none; border-radius: 22px; background: {t["acento"]}; color: {t["sobre"]}; font: 600 15px 'Bricolage Grotesque', sans-serif">Empezar</button>
</section>
</div>
{barra_nav(t, [('inicio','Inicio'),('pesa','Entrenar'),('medalla','Rangos'),('perfil','Perfil')], 0)}
''', t['fondo'], t['texto'])

def forzudo_fin(t, pj):
    stats = [('reloj','1 h 12','Duración'),('pesa','7','Ejercicios'),('series','21','Series'),('llama','3','Récords')]
    st = ''.join(f'<div style="flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px">{ico(a, t["suave"], 18)}<span style="font-size: 18px; font-weight: 600">{b}</span><span style="font-size: 11px; color: {t["suave"]}">{c}</span></div>' for a,b,c in stats)
    grupos = [('Pecho',1.0,9),('Hombros',0.66,6),('Tríceps',0.55,5),('Espalda',0.33,3)]
    gr = ''.join(f'<div style="display: flex; align-items: center; gap: 12px"><span style="width: 72px; font-size: 14px">{n}</span><div style="flex: 1; height: 10px; border-radius: 5px; background: {t["chip"]}"><div style="width: {int(p*100)}%; height: 10px; border-radius: 5px; background: {t["acento"]}"></div></div><span style="width: 52px; text-align: right; font-size: 13px; color: {t["suave"]}">{s} series</span></div>' for n,p,s in grupos)
    recs = ''.join(f'<div style="flex: none; width: 104px; padding: 12px; {vidrio(t, 20)} display: flex; flex-direction: column; gap: 4px"><span style="font-size: 12px; color: {t["suave"]}">{a}</span><span style="font-size: 18px; font-weight: 600">{b}</span><span style="font-size: 11px; color: {t["acento"]}">{c}</span></div>' for a,b,c in [('Press banca','62,5 kg','+2,5 kg'),('Remo','55 kg','+5 kg'),('Fondos','12 reps','+2')])
    return pagina('El Forzudo · Entreno terminado', 390, 844, f'''
{brillo(t, '75%', '0%', '360px 360px')}
<div style="position: relative; padding: 52px 20px 0; display: flex; flex-direction: column; gap: 16px">
<section style="position: relative; height: 220px; overflow: hidden; {vidrio(t, 30)}">
<div style="position: absolute; right: -30px; top: -20px">{puntos('f'+t['nombre'], t['punto'], 240, 260, 10, 1.4)}</div>
<div style="position: absolute; left: 22px; top: 22px; width: 190px; display: flex; flex-direction: column; gap: 8px">
<h1 style="margin: 0; font-size: 30px; font-weight: 600; line-height: 1.05; letter-spacing: -0.02em">Entreno terminado</h1>
<p style="margin: 0; font-size: 14px; color: {t["suave"]}">Torso A, viernes 24. Tres récords nuevos.</p>
</div>
<p style="position: absolute; left: 22px; bottom: 18px; margin: 0; font-family: Puntos, monospace; font-weight: 700; font-size: 52px; line-height: 1; color: {t["acento"]}">3.405<span style="font-family: 'Bricolage Grotesque', sans-serif; font-size: 16px; font-weight: 500; color: {t["suave"]}"> kg</span></p>
<img src="{pj}" alt="" style="position: absolute; right: 8px; bottom: 10px; width: 150px; height: 150px">
</section>
<section style="padding: 16px 8px; {vidrio(t, 24)} display: flex">{st}</section>
<p style="margin: 6px 4px 0; font-size: 16px; font-weight: 600">Récords de hoy</p>
<div style="display: flex; gap: 10px">{recs}</div>
<section style="padding: 18px; {vidrio(t, 24)} display: flex; flex-direction: column; gap: 14px">
<p style="margin: 0; font-size: 16px; font-weight: 600">Lo que trabajaste</p>
{gr}
</section>
</div>
''', t['fondo'], t['texto'])

def ahorrador_inicio(t, pj, nombre_tema):
    acc = [('flecha','Gasto',True),('entra','Ingreso',False),('pasar','Pasar',False),('moneda','Apartar',False)]
    ab = ''.join(f'<div style="display: flex; flex-direction: column; align-items: center; gap: 8px"><button aria-label="{n}" style="width: 56px; height: 56px; border-radius: 50%; border: 1px solid {t["acento"] if p else t["borde"]}; background: {t["acento"] if p else t["chip"]}; display: grid; place-items: center">{ico(i, t["sobre"] if p else t["texto"], 22)}</button><span style="font-size: 12px">{n}</span></div>' for i,n,p in acc)
    movs = [('carrito','Mercado','Hoy, efectivo','−86.400',False),('sueldo','Sueldo','Quincena, banco','+1.850.000',True),('bus','Transporte','Ayer, billetera','−7.200',False),('comida','Comida afuera','Miércoles, banco','−32.000',False)]
    mv = ''.join(f'<div style="display: flex; align-items: center; gap: 12px; padding: 10px 0">{chip(t, i, 42, t["ingreso"] if ing else t["texto"])}<div style="flex: 1"><p style="margin: 0; font-size: 15px; font-weight: 500">{n}</p><p style="margin: 0; font-size: 12px; color: {t["suave"]}">{d}</p></div><span style="font-size: 15px; font-weight: 600; color: {t["ingreso"] if ing else t["texto"]}">{v[0]}${v[1:]}</span></div>' for i,n,d,v,ing in movs)
    return pagina('El Ahorrador · Inicio', 390, 844, f'''
{brillo(t, '50%', '-5%', '480px 360px')}
<div style="position: relative; padding: 52px 20px 0; display: flex; flex-direction: column; gap: 16px">
<header style="display: flex; align-items: center; gap: 12px">
<div style="width: 48px; height: 48px; border-radius: 50%; {vidrio(t, 24)} display: grid; place-items: center; overflow: hidden"><img src="{pj}" alt="El Ahorrador" style="width: 40px; height: 40px"></div>
<div style="flex: 1"><p style="margin: 0; font-size: 13px; color: {t["suave"]}">Octubre</p><h1 style="margin: 0; font-size: 22px; font-weight: 600; letter-spacing: -0.02em">Hola, Angel</h1></div>
<button aria-label="Ocultar saldos" style="width: 48px; height: 48px; border-radius: 50%; border: 1px solid {t["borde"]}; background: {t["vidrio"]}; display: grid; place-items: center">{ico('ojo', t['texto'], 20)}</button>
</header>
<section style="position: relative; text-align: center; padding: 8px 0 4px">
<div style="position: absolute; left: 45px; top: -30px">{puntos('a'+nombre_tema, t['punto'], 300, 170, 10, 1.3)}</div>
<p style="position: relative; margin: 0; font-size: 14px; color: {t["suave"]}">Tienes en total</p>
<p style="position: relative; margin: 4px 0 0; font-family: Puntos, monospace; font-weight: 700; font-size: 50px; line-height: 1.05">$2.017.400</p>
<p style="position: relative; margin: 6px 0 0; font-size: 13px; color: {t["suave"]}">Debes $240.000 en la tarjeta</p>
</section>
<section style="padding: 18px 14px; {vidrio(t, 28)} display: flex; justify-content: space-around">{ab}</section>
<section style="position: relative; overflow: hidden; padding: 16px 18px; {vidrio(t, 24)} display: flex; align-items: center; gap: 14px">
<div style="flex: 1; display: flex; flex-direction: column; gap: 8px">
<p style="margin: 0; font-size: 13px; color: {t["suave"]}">Meta: viaje a Cartagena</p>
<p style="margin: 0; font-size: 18px; font-weight: 600; white-space: nowrap">$1.240.000 <span style="font-size: 13px; font-weight: 400; color: {t["suave"]}">de $2.000.000</span></p>
<div style="height: 8px; border-radius: 4px; background: {t["chip"]}"><div style="width: 62%; height: 8px; border-radius: 4px; background: {t["t2"]}"></div></div>
<p style="margin: 0; font-size: 12px; color: {t["suave"]}">Guarda $95.000 por quincena y llegas en marzo</p>
</div>
<img src="{pj}" alt="" style="width: 96px; height: 96px; flex: none">
</section>
<section style="padding: 6px 16px; {vidrio(t, 24)}">
<div style="display: flex; align-items: baseline; justify-content: space-between; padding-top: 10px"><p style="margin: 0; font-size: 16px; font-weight: 600">Movimientos</p><a href="#" style="text-decoration: none; font-size: 13px; color: {t["acento"] if t["oscuro"] else t["texto"]}">Ver todos</a></div>
{mv}
</section>
</div>
{barra_nav(t, [('inicio','Inicio'),('barras','Resumen'),('mas','Anotar'),('sobre','Sobres'),('moneda','Metas')], 0)}
''', t['fondo'], t['texto'])

def ahorrador_sobres(t, pj):
    sob = [('casa','Arriendo',900000,900000),('carrito','Mercado',600000,412000),('rayo','Servicios',220000,180000),('bus','Transporte',150000,61000),('comida','Comida afuera',160000,171000)]
    def fmt(n): return f'{n:,}'.replace(',', '.')
    filas = []
    for i,n,a,g in sob:
        q = a - g; pas = q < 0
        col = '#FF6B6B' if pas else t['t2']
        filas.append(f'<div style="padding: 14px 16px; {vidrio(t, 22)} display: flex; flex-direction: column; gap: 10px"><div style="display: flex; align-items: center; gap: 12px">{chip(t, i, 40)}<div style="flex: 1"><p style="margin: 0; font-size: 15px; font-weight: 500">{n}</p><p style="margin: 0; font-size: 12px; color: {t["suave"]}">Gastado ${fmt(g)} de ${fmt(a)}</p></div><div style="text-align: right"><p style="margin: 0; font-size: 11px; color: {col if pas else t["suave"]}">{"Te pasaste" if pas else "Queda"}</p><p style="margin: 0; font-size: 16px; font-weight: 600; color: {col if pas else t["texto"]}">${fmt(abs(q))}</p></div></div><div style="height: 6px; border-radius: 3px; background: {t["chip"]}"><div style="width: {min(100, int(g*100/a))}%; height: 6px; border-radius: 3px; background: {col}"></div></div></div>')
    return pagina('El Ahorrador · Sobres', 390, 844, f'''
{brillo(t, '20%', '-5%', '420px 320px')}
<div style="position: relative; padding: 52px 20px 0; display: flex; flex-direction: column; gap: 12px">
<h1 style="margin: 0; font-size: 26px; font-weight: 600; letter-spacing: -0.02em">Sobres de octubre</h1>
<section style="position: relative; overflow: hidden; padding: 18px; border-radius: 28px; background: {t["acento"]}; color: {t["sobre"]}">
<p style="margin: 0; font-size: 14px; opacity: 0.85">Por asignar</p>
<p style="margin: 2px 0 0; font-family: Puntos, monospace; font-weight: 700; font-size: 44px; line-height: 1.05">$340.000</p>
<p style="margin: 6px 0 0; font-size: 13px; opacity: 0.85">Toca un sobre para ponerle plata</p>
</section>
{''.join(filas)}
</div>
{barra_nav(t, [('inicio','Inicio'),('barras','Resumen'),('mas','Anotar'),('sobre','Sobres'),('moneda','Metas')], 3)}
''', t['fondo'], t['texto'])

def sistema():
    def muestra(nom, cols, oscuro):
        celdas = ''.join(f'<div style="flex: 1; display: flex; flex-direction: column; gap: 6px"><div style="height: 64px; border-radius: 16px; background: {c}; border: 1px solid rgba(0,0,0,0.08)"></div><span style="font-size: 12px; color: #4a4744">{n}<br><b style="font-weight: 600; color: #1b1a1c">{c}</b></span></div>' for n,c in cols)
        return f'<div style="display: flex; flex-direction: column; gap: 8px"><p style="margin: 0; font-size: 15px; font-weight: 600">{nom}</p><div style="display: flex; gap: 8px">{celdas}</div></div>'
    g = TEMAS['gimnasio']; v = TEMAS['vino']; gr = TEMAS['grafito']; c = TEMAS['claro_fz']
    pjs = ''.join(f'<div style="display: flex; flex-direction: column; align-items: center; gap: 6px"><div style="width: 112px; height: 112px; border-radius: 24px; background: {b}; display: grid; place-items: center"><img src="{PJ[k]}" alt="" style="width: 96px; height: 96px"></div><span style="font-size: 12px; color: #4a4744">{n}</span></div>' for k,b,n in [('fz_gimnasio','#0C0D09','Gimnasio'),('fz_grafito','#0E0E10','Grafito'),('fz_claro','#F3F2EE','Claro'),('ah_vino','#0D0809','Vino'),('ah_grafito','#0E0E10','Grafito'),('ah_claro','#F3F2EE','Claro')])
    iconos = ''.join(f'<div style="width: 52px; height: 52px; border-radius: 50%; background: rgba(205,235,58,0.14); border: 1px solid rgba(255,255,255,0.10); display: grid; place-items: center">{ico(n, "#F1F3EA", 24)}</div>' for n in ['inicio','pesa','medalla','perfil','llama','reloj','sobre','moneda','carrito','rayo','bus','comida'])
    return pagina('Vidrio y puntos · sistema', 1240, 1220, f'''
<div style="padding: 56px 64px; display: grid; grid-template-columns: 1fr 1fr; gap: 48px 64px; color: #1b1a1c; background: #F3F2EE; height: 100%; box-sizing: border-box">
<div style="grid-column: 1 / 3; display: flex; flex-direction: column; gap: 10px; max-width: 760px">
<h1 style="margin: 0; font-size: 48px; font-weight: 600; letter-spacing: -0.03em; line-height: 1">Vidrio y puntos</h1>
<p style="margin: 0; font-size: 18px; line-height: 1.5; color: #4a4744">Tarjetas de vidrio sobre una sola luz suave, una letra para todo y los números grandes hechos de puntos: la trama riso de Fogón convertida en luz. Los personajes toman las tintas de cada tema.</p>
</div>
<div style="display: flex; flex-direction: column; gap: 22px">
<h2 style="margin: 0; font-size: 22px; font-weight: 600">Temas</h2>
{muestra('El Forzudo · Gimnasio', [('Papel', g['fondo']), ('Tinta', g['acento']), ('Segunda', g['t2']), ('Letras', g['texto']), ('Suaves', g['suave'])], True)}
{muestra('El Ahorrador · Vino', [('Papel', v['fondo']), ('Tinta', v['acento']), ('Segunda', v['t2']), ('Letras', v['texto']), ('Suaves', v['suave'])], True)}
{muestra('Grafito (las dos)', [('Papel', gr['fondo']), ('Tinta', gr['acento']), ('Segunda', gr['t2']), ('Letras', gr['texto']), ('Suaves', gr['suave'])], True)}
{muestra('Claro (las dos, por defecto)', [('Papel', c['fondo']), ('Tinta', c['acento']), ('Segunda', '#C6E236 / #F2384D'), ('Letras', c['texto']), ('Suaves', c['suave'])], False)}
</div>
<div style="display: flex; flex-direction: column; gap: 22px">
<h2 style="margin: 0; font-size: 22px; font-weight: 600">Letra</h2>
<div style="display: flex; flex-direction: column; gap: 4px"><span style="font-size: 13px; color: #4a4744">Bricolage Grotesque, para todo</span><span style="font-size: 34px; font-weight: 600; letter-spacing: -0.02em">Semanas seguidas</span><span style="font-size: 16px; color: #4a4744">Guarda $95.000 por quincena y llegas en marzo.</span></div>
<div style="display: flex; flex-direction: column; gap: 4px"><span style="font-size: 13px; color: #4a4744">Doto, solo para el número grande de cada pantalla</span><span style="font-family: Puntos, monospace; font-weight: 700; font-size: 84px; line-height: 1">12 · $2.017.400</span></div>
<h2 style="margin: 10px 0 0; font-size: 22px; font-weight: 600">Íconos</h2>
<p style="margin: 0; font-size: 15px; color: #4a4744">Línea redonda de 1,9, siempre dentro de una ficha de vidrio redonda, del color de las letras. Ninguno relleno, ninguno blanco sobre negro.</p>
<div style="display: flex; flex-wrap: wrap; gap: 10px; padding: 18px; border-radius: 24px; background: #0C0D09">{iconos}</div>
<h2 style="margin: 10px 0 0; font-size: 22px; font-weight: 600">Personajes con las tintas del tema</h2>
<div style="display: flex; flex-wrap: wrap; gap: 12px">{pjs}</div>
</div>
</div>
''', '#F3F2EE', '#1b1a1c')

archivos = {
 'V3Sistema.dc.html': sistema(),
 'V3FzGimnasio.dc.html': forzudo_inicio(TEMAS['gimnasio'], PJ['fz_gimnasio']),
 'V3FzFin.dc.html': forzudo_fin(TEMAS['gimnasio'], PJ['fz_gimnasio']),
 'V3FzGrafito.dc.html': forzudo_inicio(TEMAS['grafito'], PJ['fz_grafito']),
 'V3FzClaro.dc.html': forzudo_inicio(TEMAS['claro_fz'], PJ['fz_claro']),
 'V3AhVino.dc.html': ahorrador_inicio(TEMAS['vino'], PJ['ah_vino'], 'vino'),
 'V3AhSobres.dc.html': ahorrador_sobres(TEMAS['vino'], PJ['ah_vino']),
 'V3AhGrafito.dc.html': ahorrador_inicio(TEMAS['grafito'], PJ['ah_grafito'], 'grafito'),
 'V3AhClaro.dc.html': ahorrador_inicio(TEMAS['claro_ah'], PJ['ah_claro'], 'claro'),
}
for n,c in archivos.items():
    open(os.path.join(OUT, n), 'w').write(c)
print(list(archivos))
