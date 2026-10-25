#!/usr/bin/env python3
"""Datos de prueba de La Cucha, inventados (nunca los de alguien), para el emulador y las capturas.

Uso (desde apps/cucha): python3 herramientas/datos-prueba.py <es|en> <AAAA-MM-DD de hoy> <salida.json>
Escribe un Estado con un perfil y sus metas, 21 días de comidas anotadas (hoy, hasta el almuerzo), agua,
el peso de un mes, favoritos y el menú de la semana con las recetas de ejemplo. Los valores salen de
assets/datos/ igual que en la app. Para usarlo en el emulador (versión de prueba): adb push … y
`run-as io.github.fogonapps.cucha cp … files/datastore/estado.json`.
"""
import datetime as dt
import json
import random
import re
import sys

idioma, hoy, salida = sys.argv[1], dt.date.fromisoformat(sys.argv[2]), sys.argv[3]
es = idioma == 'es'
res = 'src/main/res/' + ('values-es' if es else 'values') + '/strings.xml'
porcion_txt = re.search(r'<plurals name="porciones_n">.*?quantity="one">%d ([^<]*)<', open(res).read(), re.S).group(1)

alimentos = {a['id']: a for a in json.load(open('src/main/assets/datos/alimentos.json'))}
recetas = {r['id']: r for r in json.load(open('src/main/assets/datos/recetas.json'))}
random.seed(7)


def cifra(x):
    t = f'{x:.1f}'.rstrip('0').rstrip('.')
    return t.replace('.', ',') if es else t


def nutrientes(k, p, c, g, f=1.0):
    return {'kcal': round(k * f, 2), 'proteina': round(p * f, 2), 'carbos': round(c * f, 2), 'grasa': round(g * f, 2)}


def de_alimento(id, cuantas=1, porcion=0):
    """Como Anotar.kt: en una porción casera (o en gramos si no tiene)."""
    a = alimentos[id]
    nombre = a['es'] if es else a['en']
    if a['porciones']:
        p = a['porciones'][porcion]
        gramos = cuantas * p['gramos']
        detalle = f"{cifra(cuantas)} {(p['es'] if es else p['en']).lower()} · {cifra(gramos)} g"
    else:
        gramos = cuantas
        detalle = f'{cifra(gramos)} g'
    return 'a:' + id, nombre, gramos, detalle, nutrientes(a['k'], a['p'], a['c'], a['g'], gramos / 100)


def de_receta(id, porciones=1):
    """Como Logica.porPorcion: la suma de los ingredientes entre las porciones."""
    r = recetas[id]
    tot = [0.0] * 4
    for i in r['ingredientes']:
        a, f = alimentos[i['alimento']], i['gramos'] / 100
        tot = [t + v * f for t, v in zip(tot, (a['k'], a['p'], a['c'], a['g']))]
    n = r.get('porciones', 1)
    return 'r:' + id, (r['es'] if es or not r.get('en') else r['en']), porciones, f'{cifra(porciones)} {porcion_txt}'.lower(), nutrientes(*tot, porciones / n)


# Lo que suele comer: cada comida con unas pocas opciones que se turnan.
MENUS = {
    'desayuno': [
        [('r', 'huevos_pericos_arepa', 1), ('a', 'tinto', 1, 0)],
        [('a', 'arepa', 1, 0), ('a', 'huevo_revuelto', 2, 0), ('a', 'chocolate_caliente', 1, 0)],
        [('r', 'avena_banano', 1)],
        [('r', 'changua', 1), ('a', 'pan_blanco', 1, 0)],
    ],
    'media_manana': [
        [('a', 'banano', 1, 0)], [('a', 'yogur_griego', 1, 0), ('a', 'fresas', 1, 0)], [('a', 'manzana', 1, 0)],
    ],
    'almuerzo': [
        [('r', 'ajiaco', 1)], [('r', 'pollo_sudado', 1), ('a', 'arroz_blanco', 1, 0)],
        [('r', 'frijoles_antioquenos', 1)], [('r', 'lentejas', 1), ('a', 'arroz_blanco', 1, 0), ('a', 'aguacate', 1, 0)],
        [('r', 'arroz_con_pollo', 1)], [('r', 'carne_desmechada', 1), ('a', 'patacon', 2, 0)],
    ],
    'algo': [
        [('a', 'mani', 1, 0)], [('a', 'cuajada', 1, 0), ('a', 'bocadillo', 1, 0)], [('r', 'batido_proteina', 1)],
    ],
    'cena': [
        [('r', 'arepa_huevo_queso', 1)], [('r', 'salpicon', 1)], [('a', 'pechuga', 1, 0), ('a', 'papa', 1, 0)],
        [('r', 'yogur_frutas', 1)],
    ],
}


def anotar(comida, opcion, n):
    for x in opcion:
        ref, nombre, cantidad, detalle, nut = de_receta(x[1], x[2]) if x[0] == 'r' else de_alimento(x[1], x[2], x[3])
        yield {'id': f'n{n}-{ref}', 'comida': comida, 'ref': ref, 'nombre': nombre, 'cantidad': cantidad, 'detalle': detalle, 'n': nut}


dias = {}
for atras in range(20, -1, -1):
    fecha = hoy - dt.timedelta(days=atras)
    if atras in (9, 15):
        continue  # dos días sin anotar, como pasa
    comidas = ['desayuno', 'media_manana', 'almuerzo'] if atras == 0 else list(MENUS)
    if atras and random.random() < 0.3:
        comidas.remove('media_manana')
    anotados = []
    for c in comidas:
        anotados += anotar(c, random.choice(MENUS[c]), f'{atras}{len(anotados)}')
    dias[fecha.isoformat()] = {'anotados': anotados, 'aguaMl': 1250 if atras == 0 else random.choice([1750, 2000, 2250, 2500])}

# Bajando de a poco: 66,4 kg hace un mes, con ruido de báscula.
pesos = [{'fecha': (hoy - dt.timedelta(days=d)).isoformat(), 'kg': round(66.4 - (30 - d) * 0.06 + random.uniform(-0.25, 0.25), 1)}
         for d in range(30, -1, -2)]

semana = {1: 'ajiaco', 2: 'pollo_sudado', 3: 'frijoles_antioquenos', 4: 'lentejas', 5: 'arroz_con_pollo', 6: 'sancocho', 7: 'carne_desmechada'}
desayunos = ['huevos_pericos_arepa', 'avena_banano', 'changua', 'calentado']
plan = {}
for d in range(1, 8):
    plan[f'A|{d}|almuerzo'] = semana[d]
    plan[f'A|{d}|desayuno'] = desayunos[d % 4]
    if d <= 5:
        plan[f'A|{d}|cena'] = ['salpicon', 'arepa_huevo_queso', 'yogur_frutas'][d % 3]

estado = {
    'v': 1, 'updatedAt': int(dt.datetime.combine(hoy, dt.time(13)).timestamp() * 1000), 'listo': True,
    'nombre': 'Caro' if es else 'Sam',
    # Mujer de 28, 165 cm, 64 kg, actividad moderada, bajando 0,25 kg por semana (Mifflin-St Jeor).
    'perfil': {'sexo': 'f', 'edad': 28, 'alturaCm': 165.0, 'pesoKg': pesos[-1]['kg'], 'actividad': 2, 'objetivo': -1, 'ritmo': 0.25},
    'metas': {'kcal': 1850, 'proteina': 128, 'carbos': 219, 'grasa': 51, 'aguaMl': 2250, 'manual': False},
    'dias': dias, 'recetas': [], 'productos': [], 'plan': plan, 'alternar': False,
    'mercadoHecho': [], 'mercadoExtra': ['Café' if es else 'Coffee'], 'pesos': pesos, 'codigoBarras': True,
    'favoritos': ['a:arepa', 'r:ajiaco', 'a:huevo_revuelto', 'a:banano'],
}
json.dump(estado, open(salida, 'w'), ensure_ascii=False, indent=1)
