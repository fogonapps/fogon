#!/usr/bin/env python3
"""Percentiles de fuerza relativa (kg levantados / peso corporal) en OpenPowerlifting.

Uso: python3 herramientas/percentiles-opl.py openpowerlifting-AAAA-MM-DD-xxxx.csv

Datos: https://gitlab.com/openpowerlifting/opl-csv (dominio público). Filtro: hombres,
sin equipo (Raw), 74-82 kg de peso corporal, 18-45 años (o edad sin dato), la mejor
marca de cada persona. Sirve para comprobar los múltiplos de BASE en estandares.py.
"""
import csv
import sys

COLUMNAS = {'squat': 'Best3SquatKg', 'bench': 'Best3BenchKg', 'deadlift': 'Best3DeadliftKg'}

mejor = {}
with open(sys.argv[1], newline='') as f:
    for r in csv.DictReader(f):
        if r['Sex'] != 'M' or r['Equipment'] != 'Raw':
            continue
        try:
            bw = float(r['BodyweightKg'])
        except ValueError:
            continue
        if not 74 <= bw <= 82 or (r['Age'] and not 18 <= float(r['Age']) <= 45):
            continue
        for k, c in COLUMNAS.items():
            try:
                x = float(r[c]) / bw
            except ValueError:
                continue
            if x > 0 and x > mejor.get((r['Name'], k), 0):
                mejor[(r['Name'], k)] = x

for k in COLUMNAS:
    xs = sorted(x for (_, kk), x in mejor.items() if kk == k)
    q = lambda p: xs[int(p / 100 * (len(xs) - 1))]
    debajo = lambda m: round(100 * sum(1 for x in xs if x < m) / len(xs))
    print(f'{k}: {len(xs)} personas · p20 = {q(20):.2f}× · p75 = {q(75):.2f}× · '
          + ' '.join(f'{m}×→p{debajo(m)}' for m in (1.0, 1.25, 1.5, 1.75, 2.0, 2.25, 2.5, 3.0)))
