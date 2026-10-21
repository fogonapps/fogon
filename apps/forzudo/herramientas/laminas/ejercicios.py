"""Qué ilustración de Everkinetic usa cada ejercicio de El Forzudo y dónde va la mancha del músculo.

Cada entrada: id del ejercicio (de assets/datos/ejercicios.json) → par(...):
- ek: número de la ilustración en Everkinetic (dist/svg/<ek>-relaxation.svg y -tension.svg);
- rel / ten: manchas de músculo en cada dibujo, elipses (cx, cy, rx, ry, giro) en coordenadas
  del viewBox de ese SVG;
- orden: 'rt' si el paso 1 es el dibujo "relaxation"; 'tr' si el paso 1 es "tension"
  (Everkinetic no siempre llama así a la posición de inicio);
- huecos_rel / huecos_ten: puntos de fondo encerrado que no se deben rellenar.

El grupo muscular (y con él el color y la zona) sale de ejercicios.json.
Las coordenadas se pusieron mirando cada SVG con una cuadrícula encima (ver README.md).

Ejercicios sin dibujo (Everkinetic no tiene uno que sirva, ni en su repositorio ni en Wikimedia
Commons; revisado el 21-10-2026): elevaciones laterales en polea, face pull, sentadilla goblet, sentadilla
hack (el dibujo no se entiende), curl femoral sentado (el dibujo es de frente: no se ven los
femorales ni el rodillo), hip thrust, plancha y elevaciones colgado.
La sentadilla búlgara usa la sentadilla a una pierna con barra (0147): el pie de atrás va en un
banco, igual que la búlgara; cambia solo la carga (barra en vez de mancuernas).
"""


def commons(nombre, uno, dos):
    """Dibujo de Everkinetic que está en Wikimedia Commons pero no en su repositorio:
    commons/<nombre con _>_1.svg (paso 1) y _2.svg (paso 2)."""
    base = 'commons/' + nombre.replace(' ', '_')
    return [(base + '_1', list(uno), []), (base + '_2', list(dos), [])]


def par(ek, rel, ten, orden='rt', huecos_rel=(), huecos_ten=()):
    r = (f'{ek}-relaxation', list(rel), list(huecos_rel))
    t = (f'{ek}-tension', list(ten), list(huecos_ten))
    return [r, t] if orden == 'rt' else [t, r]


EJERCICIOS = {
    # Pecho
    'press-banca': par('0042', [(138, 140, 24, 12, -18)], [(132, 138, 24, 12, -18)], huecos_ten=[(113, 89)]),
    'press-banca-ligero': par('0042', [(138, 140, 24, 12, -18)], [(132, 138, 24, 12, -18)], huecos_ten=[(113, 89)]),
    'press-banca-mancuernas': par('0055', [(110, 105, 20, 10, -10)], [(92, 108, 18, 9, -15)]),
    'aperturas': commons('Butterfly machine', [(212, 266, 32, 20, -8), (277, 262, 32, 20, 8)], [(198, 285, 30, 18, -8), (258, 280, 30, 18, 8)]),
    'press-pecho-maquina': par('0066', [(125, 72, 20, 10, 0)], [(138, 70, 16, 9, 0)]),
    'press-inclinado': par('0061', [(135, 105, 20, 9, -20)], [(135, 105, 18, 9, -20)]),
    'press-inclinado-barra': par('0043', [(145, 108, 18, 9, -20)], [(135, 102, 18, 9, -20)]),
    'aperturas-mancuernas': par('0056', [(130, 110, 20, 9, -15)], [(125, 112, 18, 9, -15)]),
    'cruce-poleas': par('0048', [(130, 105, 18, 8, 10)], [(125, 110, 18, 9, 0)]),
    'flexiones': par('0077', [(75, 125, 18, 8, 0)], [(70, 95, 18, 8, 10)], orden='tr'),
    'fondos': par('0054', [(70, 125, 22, 9, 0)], [(70, 70, 25, 10, 0)], orden='tr'),
    # Espalda
    'jalon-amplio': par('0093', [(40, 130, 9, 22, 10), (88, 130, 9, 22, -10)], [(45, 145, 10, 22, 10), (95, 140, 10, 22, -10)]),
    'jalon-estrecho': par('0095', [(40, 130, 9, 22, 10), (88, 130, 9, 22, -10)], [(45, 145, 10, 22, 10), (95, 140, 10, 22, -10)]),
    'jalon-v': par('0096', [(40, 130, 9, 22, 10), (88, 130, 9, 22, -10)], [(45, 145, 10, 22, 10), (95, 140, 10, 22, -10)]),
    'dominadas': par('0087', [(70, 120, 9, 22, 10), (100, 120, 9, 22, -10)], [(68, 85, 10, 20, 10), (100, 85, 10, 20, -10)]),
    'remo-mancuerna': par('0024', [(130, 65, 26, 8, -5)], [(130, 65, 26, 8, -5)]),
    'remo-maquina': par('0025', [(62, 85, 8, 16, 0)], [(55, 85, 8, 16, 0)]),
    'remo-barra': par('0026', [(140, 65, 10, 18, -30)], [(135, 60, 10, 18, -30)]),
    'remo-t': par('0029', [(105, 65, 10, 20, -25)], [(110, 55, 10, 18, -30)]),
    'pullover-polea': par('0092', [(115, 100, 10, 18, 0)], [(115, 100, 10, 18, 0)]),
    # Hombros
    'press-hombro-mancuernas': par('0038', [(75, 112, 10, 9, 0), (135, 112, 10, 9, 0)], [(65, 115, 10, 9, 0), (135, 115, 10, 9, 0)]),
    'press-hombro': commons('Seated shoulder press machine', [(198, 290, 22, 24, 0), (402, 284, 22, 24, 0)], [(240, 282, 20, 24, 0), (392, 278, 20, 24, 0)]),
    'press-militar': par('0004', [(88, 108, 9, 8, 0), (132, 108, 9, 8, 0)], [(82, 80, 8, 8, 0), (118, 78, 8, 8, 0)]),
    'laterales': par('0018', [(92, 82, 8, 8, 0), (143, 82, 8, 8, 0)], [(103, 88, 8, 8, 0), (155, 88, 8, 8, 0)]),
    'pajaros': par('0032', [(120, 70, 10, 8, 0), (175, 60, 10, 8, 0)], [(105, 75, 10, 8, 0), (140, 70, 10, 8, 0)]),
    'posterior-polea': par('0035', [(82, 62, 9, 8, 0), (145, 62, 9, 8, 0)], [(95, 58, 9, 8, 0), (140, 58, 9, 8, 0)]),
    # Bíceps
    'curl-mancuerna': par('0224', [(72, 100, 7, 18, 4), (130, 104, 7, 18, -6)], [(78, 96, 9, 15, 0), (121, 96, 9, 14, 0)]),
    'curl-polea': par('0212', [(75, 115, 7, 15, 0), (125, 110, 7, 15, 0)], [(70, 95, 8, 12, 0), (120, 95, 8, 12, 0)]),
    'curl-barra': par('0211', [(80, 115, 7, 15, 0), (135, 110, 7, 15, 0)], [(80, 105, 8, 12, 0), (135, 105, 8, 12, 0)]),
    'curl-predicador': par('0239', [(75, 110, 8, 14, 10), (125, 110, 8, 14, -10)], [(70, 95, 8, 12, 0), (125, 95, 8, 12, 0)]),
    'curl-inclinado': par('0214', [(100, 110, 7, 14, 0), (145, 100, 7, 14, 0)], [(115, 85, 8, 12, 0), (145, 85, 8, 12, 0)]),
    'curl-martillo': par('0227', [(65, 110, 7, 15, 0), (130, 110, 7, 15, 0)], [(68, 95, 8, 12, 0), (122, 95, 8, 12, 0)]),
    # Tríceps
    'triceps-polea': par('0205', [(55, 105, 6, 14, 0), (120, 105, 6, 14, 0)], [(55, 105, 6, 14, 0), (118, 105, 6, 14, 0)]),
    'triceps-cuerda': par('0206', [(50, 95, 6, 14, 0), (110, 95, 6, 14, 0)], [(55, 95, 6, 14, 0), (115, 95, 6, 14, 0)]),
    'triceps-cabeza': par('0198', [(70, 75, 7, 15, 0), (125, 75, 7, 15, 0)], [(80, 65, 7, 14, 0), (118, 60, 7, 14, 0)]),
    'press-frances': par('0183', [(150, 100, 7, 15, 0)], [(150, 95, 7, 15, 0)]),
    'patada-triceps': par('0204', [(95, 80, 12, 6, 0)], [(80, 68, 14, 6, -5)]),
    'press-cerrado': par('0049', [(165, 115, 12, 6, 20)], [(150, 90, 6, 14, 0)]),
    'fondos-banco': par('0162', [(205, 120, 6, 14, 0)], [(210, 100, 6, 14, 0)], orden='tr'),
    # Cuádriceps
    'sentadilla': par('0122', [(76, 225, 19, 13, 25), (114, 222, 19, 13, -10)], [(88, 222, 12, 28, 0), (112, 222, 12, 28, 0)], orden='tr'),
    'smith': par('0124', [(115, 205, 14, 10, 15), (165, 205, 12, 9, -20)], [(97, 205, 9, 22, 0), (120, 205, 8, 20, 0)], orden='tr'),
    'prensa': par('0127', [(150, 110, 20, 10, -20)], [(140, 100, 25, 8, -30)]),
    'extension': par('0142', [(80, 160, 10, 18, 0), (120, 160, 10, 18, 0)], [(70, 170, 12, 15, 0), (115, 170, 12, 15, 0)]),
    'zancadas': par('0121', [(110, 170, 18, 10, 0), (55, 190, 10, 15, 0)], [(110, 185, 18, 10, 0), (60, 200, 10, 15, 0)]),
    'bulgara': par('0147', [(111, 166, 8, 12, -10)], [(89, 160, 9, 15, 2)], orden='tr'),
    'zancadas-mancuernas': par('0115', [(60, 170, 10, 22, 0), (95, 175, 10, 22, 0)], [(130, 190, 18, 10, 0), (75, 210, 10, 15, 0)]),
    # Femorales y glúteos
    'curl-femoral': par('0117', [(205, 100, 24, 7, 15)], [(185, 95, 22, 7, 5)]),
    'rdl-mancuernas': par('0107', [(120, 180, 8, 22, -5)], [(108, 175, 7, 22, 0)], orden='tr'),
    'rdl-barra': par('0118', [(62, 180, 8, 22, 5)], [(75, 190, 8, 20, 0), (105, 190, 8, 20, 0)], orden='tr'),
    'peso-muerto': par('0099', [(115, 190, 8, 22, -5), (125, 140, 10, 10, 0)], [(98, 190, 7, 22, 0)]),
    'puente-gluteo': par('0109', [(135, 95, 10, 8, 0)], [(125, 70, 10, 8, -20)]),
    # Pantorrillas
    'pantorrilla': par('0282', [(62, 210, 8, 20, 0), (92, 210, 8, 20, 0)], [(62, 205, 8, 20, 0), (92, 205, 8, 20, 0)]),
    'pantorrilla-sentado': par('0279', [(60, 175, 7, 18, 0), (118, 175, 7, 18, 0)], [(60, 172, 7, 18, 0), (118, 172, 7, 18, 0)]),
    # Abdomen
    'crunch-polea': par('0288', [(80, 100, 12, 18, 0)], [(90, 110, 10, 14, 30)]),
    'crunch': par('0291', [(95, 85, 18, 7, 0)], [(95, 80, 16, 7, -10)]),
    'rueda': par('0286', [(165, 118, 8, 14, 0)], [(145, 108, 24, 7, 0)]),
    'elev-piernas': par('0021', [(180, 130, 18, 7, -15)], [(175, 135, 18, 7, 0)]),
}
