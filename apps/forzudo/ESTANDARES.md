# Estándares de fuerza de El Forzudo

Los rangos de El Forzudo (Hierro → Diamante) salen de una tabla propia de Fogón. Desde la versión 1.2 (cuando aún se llamaba Mi Gym) no usa datos de ningún sitio comercial.

## De dónde salen los números

**1. Tres ejercicios base: press banca, sentadilla y peso muerto.**

Para cada uno hay 5 umbrales, en múltiplos de tu peso corporal. Al pasar cada umbral entras al rango siguiente:

| Ejercicio | Bronce | Plata | Oro | Platino | Diamante |
|-----------|-------:|------:|----:|--------:|---------:|
| Press banca | 0,5× | 0,75× | 1× | 1,25× | 1,75× |
| Sentadilla | 0,75× | 1,25× | 1,5× | 2× | 2,5× |
| Peso muerto | 1× | 1,25× | 1,75× | 2,25× | 3× |

- **Platino y Diamante** salen de [OpenPowerlifting](https://www.openpowerlifting.org), la base abierta de resultados de competencias de powerlifting. Sus datos son de **dominio público**.
  - Se tomaron hombres sin equipo (*raw*), de 74 a 82 kg y de 18 a 45 años, con la mejor marca de cada persona: unas 60 000 a 80 000 personas por ejercicio.
  - **Platino** ≈ el percentil 20 de los competidores, redondeado a 0,25×. Es el nivel de quien empieza a competir.
  - **Diamante** ≈ el percentil 75: más fuerte que unos 3 de cada 4 competidores.
- **Hierro a Oro** son criterio de Fogón. La gente que compite ya entrena en serio, así que esos datos no sirven para los primeros rangos. Oro queda en ~¾ de Platino, Plata en ~⅗ y Bronce en ~⅖, redondeados a múltiplos fáciles de recordar.

Lo que resultó con los datos del 17-10-2026 (`herramientas/percentiles-opl.py`):

| Ejercicio | Personas | p20 | p75 | Platino en OPL | Diamante en OPL |
|-----------|---------:|----:|----:|---------------:|----------------:|
| Press banca | 81 364 | 1,32× | 1,76× | p14 | p74 |
| Sentadilla | 59 760 | 1,96× | 2,58× | p23 | p69 |
| Peso muerto | 67 695 | 2,34× | 2,99× | p14 | p76 |

**2. Los demás ejercicios.** Cada uno usa la tabla de un ejercicio base multiplicada por una razón. Por ejemplo: press inclinado con barra = 0,80 × banca.

- Las razones son **estimaciones de Fogón**, no mediciones. No existen datos abiertos de mancuernas ni de máquinas.
- Cada razón tiene una confianza (alta, media o baja) anotada en `herramientas/estandares.py`.
- En máquinas y poleas la confianza siempre es baja, porque el peso marcado cambia según la máquina. Por eso la app los muestra como "(aproximado)".
- Los ejercicios de tirón (jalón, remo, curl) usan la banca como base, porque es el ejercicio de tren superior con más datos.
- **Regla de las máquinas:** su razón queda igual o por encima de la del ejercicio libre parecido, porque en una máquina se mueve más peso. Así una máquina nunca da mejor rango que el ejercicio libre.

**Prueba de coherencia (18-10-2026).** Se tomó alguien de 78 kg en Oro en los tres básicos (banca 80, sentadilla 120, peso muerto 140) y sus marcas típicas en el resto. Resultado: Oro en 30 ejercicios y Plata en 6, ninguno por encima de Oro. Con la primera versión de las razones, las máquinas daban rangos muy altos, y por eso se subieron.

Los datos de `herramientas/datos-prueba.js` **no** sirven para calibrar: tienen pesos inventados (por ejemplo, curl con mancuerna de 164 kg). Solo comprueban que la lógica es igual a la web.

**3. Ajuste a tu peso.** La tabla está hecha para 78 kg y la app la escala en proporción a tu peso corporal.

## Qué ya no dice la app

Antes decía "más fuerte que ~X % de los hombres que entrenan". Ya no lo dice, porque no hay datos abiertos de la gente que va al gimnasio para respaldarlo. Ahora muestra el rango y cuánto te falta para el siguiente.

## Cómo regenerar la tabla

```
# 1. Bajar los datos (unos 170 MB). Se usa el artefacto de gitlab.com
#    porque openpowerlifting.gitlab.io está bloqueado por NextDNS en el PC de Angel.
curl -L -o opl.zip 'https://gitlab.com/api/v4/projects/23692378/jobs/artifacts/main/raw/files/openpowerlifting-latest.zip?job=data'
unzip opl.zip

# 2. Ver los percentiles (para revisar los múltiplos de BASE)
python3 herramientas/percentiles-opl.py openpowerlifting-*/openpowerlifting-*.csv

# 3. Escribir la tabla que usa la app
python3 herramientas/estandares.py src/main/assets/datos/estandares.json
```

`herramientas/extraer.js`, que trae los datos de la versión web, ya **no** escribe `estandares.json`. Así la tabla vieja no puede volver por accidente.

## Créditos

> Esta app usa datos del proyecto OpenPowerlifting, https://www.openpowerlifting.org. Puedes descargar una copia de los datos en https://gitlab.com/openpowerlifting/opl-data.

OpenPowerlifting no exige atribución, pero la pide como cortesía, así que la ponemos.
