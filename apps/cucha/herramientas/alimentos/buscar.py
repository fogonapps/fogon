#!/usr/bin/env python3
"""
Busca alimentos en los CSV de USDA FoodData Central, para elegir el número FDC al agregar uno a lista.py.

Uso: buscar.py <carpeta SR Legacy> <carpeta FNDDS> <palabras…>
Muestra, de cada alimento cuya descripción tiene todas las palabras: el número FDC, la base (SR o FNDDS),
las calorías y proteína por 100 g, la descripción y las porciones medidas por USDA (con sus gramos), que
son las que lista.py puede usar con U("…").
"""
import csv, os, sys

def texto_porcion(r):
    """SR Legacy escribe la porción en amount + modifier; FNDDS, en portion_description (y modifier es un código)."""
    return r["portion_description"] or f'{float(r["amount"] or 1):g} {r["modifier"]}'

def cargar(carpeta, base, palabras, hallados):
    for r in csv.DictReader(open(os.path.join(carpeta, "food.csv"), encoding="utf-8")):
        d = r["description"].lower()
        if all(p in d for p in palabras): hallados[int(r["fdc_id"])] = [base, r["description"], {}, []]
    if not hallados: return
    for r in csv.DictReader(open(os.path.join(carpeta, "food_nutrient.csv"), encoding="utf-8")):
        i = int(r["fdc_id"])
        if i in hallados and r["nutrient_id"] in ("1008", "208", "1003", "203"):
            hallados[i][2]["k" if r["nutrient_id"] in ("1008", "208") else "p"] = float(r["amount"])
    for r in csv.DictReader(open(os.path.join(carpeta, "food_portion.csv"), encoding="utf-8")):
        i = int(r["fdc_id"])
        if i in hallados and float(r["gram_weight"] or 0) > 0:
            hallados[i][3].append(f'{texto_porcion(r)} = {float(r["gram_weight"]):g} g')

def main(sr, fndds, palabras):
    palabras = [p.lower() for p in palabras]
    for carpeta, base in ((sr, "SR"), (fndds, "FNDDS")):
        hallados = {}
        cargar(carpeta, base, palabras, hallados)
        for i, (b, desc, v, porciones) in sorted(hallados.items(), key=lambda x: x[1][1]):
            print(f'{i} {b:5} {v.get("k", 0):5.0f} kcal {v.get("p", 0):5.1f} p  {desc}')
            for p in porciones: print("      ", p)

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
