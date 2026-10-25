#!/usr/bin/env python3
"""
Arma assets/datos/alimentos.json de La Cucha con los valores oficiales de USDA FoodData Central (CC0).

Uso: generar.py <carpeta con los CSV de SR Legacy> <carpeta con los CSV de FNDDS>
  Descargas (https://fdc.nal.usda.gov/download-datasets):
  FoodData_Central_sr_legacy_food_csv_2018-04.zip y FoodData_Central_survey_food_csv_2024-10-31.zip

Por cada alimento de lista.py saca, por 100 g: calorías, proteína, carbohidratos, grasa y fibra.
"""
import csv, json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lista import ALIMENTOS

# SR Legacy usa los números nuevos de nutriente; FNDDS, los viejos.
NUTRIENTES = {"1008": "k", "1003": "p", "1005": "c", "1004": "g", "1079": "fi",
              "208": "k", "203": "p", "205": "c", "204": "g", "291": "fi"}

def leer(carpeta, ids, nombres, valores):
    for r in csv.DictReader(open(os.path.join(carpeta, "food.csv"), encoding="utf-8")):
        if int(r["fdc_id"]) in ids: nombres[int(r["fdc_id"])] = r["description"]
    for r in csv.DictReader(open(os.path.join(carpeta, "food_nutrient.csv"), encoding="utf-8")):
        i = int(r["fdc_id"]); k = NUTRIENTES.get(r["nutrient_id"])
        if i in ids and k: valores.setdefault(i, {})[k] = float(r["amount"])

def main(sr, fndds):
    ids = {x["fdc"] for x in ALIMENTOS}
    nombres, valores = {}, {}
    leer(sr, ids, nombres, valores); leer(fndds, ids, nombres, valores)
    faltan = ids - nombres.keys()
    assert not faltan, f"No están en USDA: {faltan}"
    vistos = set(); salida = []
    for x in ALIMENTOS:
        assert x["id"] not in vistos, x["id"]; vistos.add(x["id"])
        v = valores[x["fdc"]]
        assert all(k in v for k in "kpcg"), (x["id"], v)
        salida.append({
            "id": x["id"], "es": x["es"], "en": x["en"], "grupo": x["grupo"],
            "fdc": x["fdc"], "usda": nombres[x["fdc"]], "aprox": x["aprox"],
            "k": round(v["k"], 1), "p": round(v["p"], 2), "c": round(v["c"], 2), "g": round(v["g"], 2), "fi": round(v.get("fi", 0.0), 2),
            "porciones": [{"es": es, "en": en, "gramos": g} for es, en, g in x["porciones"]],
        })
    destino = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../src/main/assets/datos/alimentos.json")
    with open(destino, "w", encoding="utf-8") as f:
        json.dump(salida, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    print(f"{len(salida)} alimentos → {os.path.normpath(destino)}")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
