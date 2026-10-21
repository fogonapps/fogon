#!/usr/bin/env python3
"""Genera src/main/assets/datos/estandares.json con el modelo de fuerza de Fogón.

Uso: python3 herramientas/estandares.py src/main/assets/datos/estandares.json

El modelo está explicado en ../ESTANDARES.md. Resumen:
- Tres ejercicios base (banca, sentadilla, peso muerto) con umbrales en múltiplos
  del peso corporal. Platino y Diamante salen de OpenPowerlifting (dominio público,
  ver percentiles-opl.py); Hierro a Oro son criterio de Fogón.
- El resto de ejercicios = ejercicio base × una razón estimada por Fogón.
"""
import json
import sys

PESO_REF = 78  # kg; la app escala los umbrales en proporción a tu peso

# Umbrales para entrar a Bronce, Plata, Oro, Platino y Diamante (× peso corporal).
BASE = {
    'bench':    [0.50, 0.75, 1.00, 1.25, 1.75],  # Platino ≈ p14, Diamante ≈ p74 en OPL
    'squat':    [0.75, 1.25, 1.50, 2.00, 2.50],  # Platino ≈ p23, Diamante ≈ p69
    'deadlift': [1.00, 1.25, 1.75, 2.25, 3.00],  # Platino ≈ p14, Diamante ≈ p76
}

# Máquinas y poleas: la razón queda igual o por encima de la del ejercicio libre
# parecido, porque en máquina se mueve más peso; así una máquina nunca da mejor
# rango que el ejercicio libre a la misma persona.
# id: (base, razón, banderas). db = peso de UNA mancuerna, m = máquina o polea,
# ap = el ejercicio usa la tabla de otro parecido. Confianza: A alta, M media, B baja.
DERIVADOS = {
    # Empuje: base banca
    'incline-bench':   ('bench', 0.80, {}),           # A
    'ohp':             ('bench', 0.62, {}),           # A
    'db-bench':        ('bench', 0.37, {'db': 1}),    # M
    'press-inclinado': ('bench', 0.31, {'db': 1}),    # M
    'db-shoulder':     ('bench', 0.26, {'db': 1}),    # M
    'chest-press':     ('bench', 1.10, {'m': 1}),     # B
    'aperturas':       ('bench', 0.75, {'m': 1}),     # B
    'press-hombro':    ('bench', 0.80, {'m': 1}),     # B
    'laterales':       ('bench', 0.13, {'db': 1}),    # M
    'pushdown':        ('bench', 0.55, {'m': 1}),     # B
    'triceps-cabeza':  ('bench', 0.45, {'m': 1, 'ap': 1}),  # B
    'kickback':        ('bench', 0.11, {'db': 1}),    # B
    # Tirón: base banca (no hay datos abiertos de jalón ni remo; la banca es el
    # ejercicio de tren superior con más datos)
    'jalon-amplio':    ('bench', 1.00, {'m': 1}),     # B
    'jalon-estrecho':  ('bench', 1.05, {'m': 1, 'ap': 1}),  # B
    'remo-maquina':    ('bench', 1.00, {'m': 1}),     # B
    'remo-mancuerna':  ('bench', 0.40, {'db': 1}),    # M
    'face-pull':       ('bench', 0.45, {'m': 1}),     # B
    'curl-mancuerna':  ('bench', 0.19, {'db': 1}),    # M
    'curl-martillo':   ('bench', 0.21, {'db': 1, 'ap': 1}),  # M
    'incline-curl':    ('bench', 0.15, {'db': 1}),    # M
    'curl-polea':      ('bench', 0.45, {'m': 1}),     # B
    # Pierna: base sentadilla
    'smith':           ('squat', 1.05, {'m': 1}),     # M
    'hack':            ('squat', 1.20, {'m': 1}),     # B
    'prensa':          ('squat', 2.00, {'m': 1}),     # B
    'goblet':          ('squat', 0.30, {}),           # M (una sola mancuerna)
    'extension':       ('squat', 0.75, {'m': 1}),     # B
    'curl-femoral':    ('squat', 0.55, {'m': 1}),     # B
    'seated-curl':     ('squat', 0.60, {'m': 1}),     # B
    'pantorrilla':     ('squat', 1.20, {'m': 1}),     # B
    'seated-calf':     ('squat', 0.75, {'m': 1}),     # B
    'crunch-polea':    ('squat', 0.60, {'m': 1}),     # B
    # Bisagra: base peso muerto
    'rdl-barra':       ('deadlift', 0.75, {'ap': 1}), # M
    'rdl-mancuernas':  ('deadlift', 0.30, {'db': 1}), # M
}


def redondear(kg):
    return round(kg * 2) / 2  # medio kilo


def generar():
    std = {k: {'t': [redondear(x * PESO_REF) for x in v]} for k, v in BASE.items()}
    for k, (base, razon, banderas) in DERIVADOS.items():
        std[k] = {'t': [redondear(x * PESO_REF * razon) for x in BASE[base]], **banderas}
    return {'refBw': PESO_REF, 'std': std}


if __name__ == '__main__':
    with open(sys.argv[1], 'w') as f:
        json.dump(generar(), f, ensure_ascii=False, indent=1)
        f.write('\n')
