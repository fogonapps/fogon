#!/usr/bin/env python3
"""Datos de prueba de El Ahorrador, inventados (nunca los de alguien), para el emulador y las capturas.

Uso (desde apps/ahorrador): python3 herramientas/datos-prueba.py <es|en> <AAAA-MM-DD de hoy> <salida.json>
Escribe un Estado con cuentas, las categorías de la app con su nombre en ese idioma, dos meses de
movimientos, los sobres del mes, metas, pagos fijos y deudas. Para usarlo en el emulador (versión de
prueba): adb push … y `run-as io.github.fogonapps.ahorrador cp … files/datastore/estado.json`.
"""
import datetime as dt
import json
import random
import re
import sys

idioma, hoy, salida = sys.argv[1], dt.date.fromisoformat(sys.argv[2]), sys.argv[3]
res = 'src/main/res/' + ('values-es' if idioma == 'es' else 'values') + '/strings.xml'
textos = dict(re.findall(r'<string name="([^"]+)">([^<]*)</string>', open(res).read()))

# Las mismas categorías de Categorias.kt: id, color, ícono, grupo, ingreso.
BASE = [
    ('casa', 0xFF7B4DFF, 'casa', 'NECESIDAD', False), ('mercado', 0xFFFF7A1A, 'mercado', 'NECESIDAD', False),
    ('servicios', 0xFFFFC93C, 'servicios', 'NECESIDAD', False), ('transporte', 0xFF2D5BFF, 'transporte', 'NECESIDAD', False),
    ('salud', 0xFFE5332A, 'salud', 'NECESIDAD', False), ('comida', 0xFFFF4FA3, 'comida', 'GUSTO', False),
    ('salidas', 0xFF1FB45A, 'salidas', 'GUSTO', False), ('compras', 0xFF00A0E9, 'compras', 'GUSTO', False),
    ('cultura', 0xFF2FC1B2, 'cultura', 'CULTURA', False), ('estudio', 0xFFA8D930, 'estudio', 'CULTURA', False),
    ('imprevistos', 0xFFB8733A, 'imprevisto', 'IMPREVISTO', False), ('deudas', 0xFF9B8F84, 'deudas', 'NECESIDAD', False),
    ('impuestos', 0xFFB5AA9C, 'impuestos', 'NECESIDAD', False), ('otros', 0xFFD6CBB9, 'otros', 'GUSTO', False),
    ('sueldo', 0xFF1FB45A, 'sueldo', 'NECESIDAD', True), ('prima', 0xFFFFC93C, 'prima', 'NECESIDAD', True),
    ('cesantias', 0xFFFF7A1A, 'banco', 'NECESIDAD', True), ('extra', 0xFF00A0E9, 'extra', 'NECESIDAD', True),
]
categorias = [{'id': i, 'nombre': textos['cat_' + i], 'color': c, 'icono': ic, 'grupo': g, 'ingreso': ing} for i, c, ic, g, ing in BASE]
categorias.append({'id': 'mascota', 'nombre': 'Luna' if idioma == 'es' else 'Luna (dog)', 'color': 0xFFB8733A, 'icono': 'mascota', 'grupo': 'NECESIDAD', 'ingreso': False})

es = idioma == 'es'
cuentas = [
    {'id': 'efectivo', 'nombre': textos['cuenta_efectivo'], 'tipo': 'EFECTIVO', 'saldoInicial': 650000},
    {'id': 'banco', 'nombre': 'Banco' if es else 'Bank', 'tipo': 'BANCO', 'saldoInicial': 2400000},
    {'id': 'billetera', 'nombre': 'Billetera' if es else 'Wallet', 'tipo': 'BILLETERA', 'saldoInicial': 250000},
    {'id': 'tarjeta', 'nombre': 'Tarjeta' if es else 'Card', 'tipo': 'CREDITO', 'saldoInicial': 0,
     'credito': {'cupo': 3000000, 'diaCorte': 25, 'diaPago': 10}},
]

rnd = random.Random(7)
movs, n = [], 0
def mov(fecha, tipo, monto, cuenta, categoria=None, nota='', **kw):
    global n
    n += 1
    movs.append({'id': f'm{n}', 'fecha': fecha.isoformat(), 'tipo': tipo, 'monto': monto, 'cuenta': cuenta,
                 'categoria': categoria, 'nota': nota, **kw})

notas = {
    'mercado': ('Mercado de la semana', 'Groceries'), 'comida': ('Almuerzo', 'Lunch'), 'transporte': ('Bus', 'Bus'),
    'salidas': ('Cine', 'Movies'), 'compras': ('Ropa', 'Clothes'), 'mascota': ('Concentrado', 'Dog food'),
}
inicio = (hoy.replace(day=1) - dt.timedelta(days=1)).replace(day=1)
d = inicio
while d <= hoy:
    if d.day in (1, 16):
        mov(d, 'INGRESO', 1850000, 'banco', 'sueldo', 'Quincena' if es else 'Paycheck')
    if d.day == 5:
        mov(d, 'GASTO', 900000, 'banco', 'casa', 'Arriendo' if es else 'Rent', fijo='f1')
    if d.day == 12:
        mov(d, 'GASTO', 182000, 'banco', 'servicios', 'Luz y agua' if es else 'Utilities', fijo='f2')
    if d.weekday() == 5:
        mov(d, 'GASTO', rnd.randrange(95, 160) * 1000, 'banco', 'mercado', notas['mercado'][0 if es else 1])
    if d.weekday() in (1, 3):
        mov(d, 'GASTO', rnd.randrange(14, 32) * 1000, 'efectivo', 'comida', notas['comida'][0 if es else 1])
    if d.weekday() < 5:
        mov(d, 'GASTO', 3200, 'billetera', 'transporte', notas['transporte'][0 if es else 1])
    if d.day in (9, 23):
        mov(d, 'GASTO', 48000, 'tarjeta', 'salidas', notas['salidas'][0 if es else 1])
    if d.day == 18:
        mov(d, 'GASTO', 135000, 'tarjeta', 'compras', notas['compras'][0 if es else 1], cuotas=3)
    if d.day == 3:
        mov(d, 'GASTO', 76000, 'banco', 'mascota', notas['mascota'][0 if es else 1])
    if d.day == 17:
        mov(d, 'AHORRO', 200000, 'banco', meta='t1', nota='Cartagena')
        mov(d, 'AHORRO', 100000, 'banco', meta='t2', nota='Colchón' if es else 'Cushion')
    d += dt.timedelta(days=1)

mes = hoy.strftime('%Y-%m')
asignado = {'casa': 900000, 'mercado': 620000, 'servicios': 200000, 'transporte': 90000, 'comida': 220000,
            'salidas': 120000, 'compras': 150000, 'mascota': 90000, 'salud': 80000}
estado = {
    'v': 1, 'updatedAt': 0, 'nombre': 'Sam', 'sistema': 'SOBRES', 'cuentas': cuentas, 'categorias': categorias,
    'movimientos': movs, 'sobres': {mes: asignado}, 'reparto': [50, 30, 20],
    'fijos': [
        {'id': 'f1', 'nombre': 'Arriendo' if es else 'Rent', 'monto': 900000, 'categoria': 'casa', 'cuenta': 'banco', 'dia': 5},
        {'id': 'f2', 'nombre': 'Luz y agua' if es else 'Utilities', 'monto': 182000, 'categoria': 'servicios', 'cuenta': 'banco', 'dia': 12},
        {'id': 'f3', 'nombre': 'Plan del celular' if es else 'Phone plan', 'monto': 55000, 'categoria': 'servicios', 'cuenta': 'banco', 'dia': 28},
    ],
    'metas': [
        {'id': 't1', 'nombre': 'Viaje a Cartagena' if es else 'Trip to the coast', 'objetivo': 2000000, 'fecha': f'{hoy.year + 1}-03-15', 'color': 0xFFFFC93C, 'icono': 'viaje'},
        {'id': 't2', 'nombre': 'Colchón' if es else 'Emergency fund', 'objetivo': 3000000, 'color': 0xFF1FB45A, 'icono': 'moneda'},
    ],
    'deudas': [
        {'id': 'd1', 'nombre': 'Andrés', 'meDeben': True, 'monto': 120000, 'fecha': (hoy - dt.timedelta(days=12)).isoformat()},
        {'id': 'd2', 'nombre': 'Crédito de la moto' if es else 'Motorbike loan', 'meDeben': False, 'monto': 4800000, 'fecha': f'{hoy.year}-02-01', 'cuotas': 24,
         'abonos': [{'fecha': f'{hoy.year}-{m:02d}-01', 'monto': 200000} for m in range(3, hoy.month + 1)]},
    ],
    'listo': True,
}
json.dump(estado, open(salida, 'w'), ensure_ascii=False)
print(len(movs), 'movimientos')
