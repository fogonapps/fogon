# Guía de La Cucha: cómo cambiar cosas

Recetas paso a paso para mantener la app. Es para Angel, para quien quiera colaborar y para cualquier IA que trabaje en el código.

- Qué hace la app y sus versiones: [`README.md`](README.md).
- Cómo está construida por dentro: [`ARQUITECTURA.md`](ARQUITECTURA.md).
- Lo común a todas las apps de Fogón: [`docs/`](../../docs/). La guía más completa es la de [El Forzudo](../forzudo/GUIA.md).

Todos los comandos se corren desde la raíz del repo (`~/Projects/fogon`) y funcionan tal cual en fish y en bash.

## Sacar una versión nueva

1. **Subir el número** en `apps/cucha/build.gradle.kts`: `versionCode` +1 y `versionName`.
2. **Escribir las novedades** en el `README.md` (tabla "Versiones", con cómo se probó).
3. **Compilar y revisar:**
   ```
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:cucha:testDebugUnitTest :apps:cucha:lintDebug :apps:cucha:assembleRelease
   ```
   El lint no debe tener errores (deja avisos menores conocidos, como en las otras apps).
4. **Probar en el emulador** y **comprobar que es reproducible** (`docs/PUBLICAR.md`).
5. **Publicar solo con el visto bueno de Angel** (`docs/PUBLICAR.md`). Las versiones van al repo de descargas `fogonapps/la-cucha` (su README es `docs/descargas/la-cucha.md`), con la etiqueta `cucha-v<versión>`. Agregar el changelog de la versión en `fastlane/metadata/android/<es|en-US>/changelogs/` y actualizar la receta de F-Droid (`fdroid/io.github.fogonapps.cucha.yml`).

## Datos de prueba y capturas

- **Datos de prueba:** inventados, nunca los de alguien. `python3 herramientas/datos-prueba.py <es|en> <fecha de hoy> <salida.json>` (desde `apps/cucha`) arma un estado con perfil y metas, tres semanas de comidas (hoy, hasta el almuerzo), agua, el peso de un mes, favoritos y el menú de la semana. En la versión de prueba se copia con `adb push` y `run-as io.github.fogonapps.cucha cp … files/datastore/estado.json`; el idioma va en `shared_prefs/ui.xml` (clave `idioma`) y el tema en `shared_prefs/apariencia.xml` (`version` 4 y `estilo`: `GRAFITO`, `MARCA` para Cocina, `CLARO` o `CONTRASTE`).
- **Capturas de F-Droid:** como en El Forzudo (`apps/forzudo/GUIA.md` → Emulador), con el modo demo de Android. Van en `fastlane/metadata/android/<es|en-US>/images/phoneScreenshots/`, siete por idioma: Hoy en Grafito y en Cocina, Anotar una arepa en Cocina, la receta del ajiaco, el menú, la lista de mercado en Claro y el progreso. Si se instala una El Forzudo vieja (sin el proveedor de datos), La Cucha usa el peso de sus propios datos.

## Agregar o corregir un alimento

1. Buscar el alimento en USDA FoodData Central: en [fdc.nal.usda.gov](https://fdc.nal.usda.gov/) o en los CSV descargados (ver abajo). Sirven **SR Legacy** y **FNDDS (Survey)**: los dos son de dominio público. Anotar su número FDC.
2. Agregarlo a `herramientas/alimentos/lista.py` con `a(id, español, inglés, grupo, fdc, porciones…)`. Si no está tal cual y se usa el más parecido, `aprox=True` (la app lo avisa). **Un id publicado no se cambia:** lo anotado lo usa.
3. Descargar los CSV a una carpeta fuera del repo y generar:
   ```
   curl -LO https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip
   curl -LO https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_csv_2024-10-31.zip
   unzip -q FoodData_Central_sr_legacy_food_csv_2018-04.zip -d sr
   unzip -q FoodData_Central_survey_food_csv_2024-10-31.zip -d fndds
   python3 apps/cucha/herramientas/alimentos/generar.py sr/FoodData_Central_sr_legacy_food_csv_2018-04 fndds/FoodData_Central_survey_food_csv_2024-10-31
   ```
   El script nunca inventa valores: si un número FDC no existe o le falta un macro, se detiene.
4. Correr `LogicaTest` (revisa ids únicos y que las calorías cuadren con los macros).

Grupos: `granos`, `tuberculos`, `legumbres`, `carnes`, `pescados`, `lacteos`, `frutas`, `verduras`, `grasas`, `dulces`, `hechas`, `bebidas`. El orden de la lista de mercado sale de `Logica.mercado`.

## Agregar una receta de ejemplo

En `src/main/assets/datos/recetas.json`: `id` (no se cambia después), `es`, `en`, `comidas` (de `desayuno`, `media_manana`, `almuerzo`, `algo`, `cena`), `porciones`, `minutos`, `ingredientes` (id de la base y gramos **de toda la olla**) y `pasosEs` / `pasosEn`. Las calorías se calculan solas. Que sean recetas escritas para la app (no copiadas de un libro o una página).

## Importar recetas propias (privadas)

Perfil → "Importar recetas (JSON)" acepta una lista con el mismo formato. Sin ingredientes, los valores de cada porción van en `porPorcion`:

```
[{"id": "mi_avena", "es": "Mi avena", "comidas": ["desayuno"], "porciones": 1, "minutos": 10,
  "porPorcion": {"kcal": 450, "proteina": 20, "carbos": 0, "grasa": 0}, "nota": "…"}]
```

Las recetas personales de cada quien **no van al repo**: se guardan en un JSON aparte, fuera del repositorio, y se importan solo en el teléfono.

## Cambiar textos e idiomas

Igual que en las otras apps: `src/main/res/values-es/strings.xml` y `values/strings.xml`, con los mismos nombres; `TraduccionTest` lo revisa. Los nombres de los alimentos y recetas de ejemplo no están ahí sino en sus JSON (`es` y `en`).

## Las metas

La fórmula está en `Logica.metasDe` (ver `ARQUITECTURA.md` → La lógica). Si se cambia, se ajusta `LogicaTest` → `metasConMifflinStJeor` y el texto `tut_metas_nota`.

## Open Food Facts

- Solo se usa si la persona activa "Buscar por código de barras". Se manda únicamente el número.
- Su licencia (ODbL) pide dar crédito: cada producto escaneado lo muestra con el enlace al producto, y está en Licencias. Si se muestran fotos, son CC BY-SA y también piden crédito (hoy no se muestran).

## Colores, letras e ícono

| Qué | Dónde |
|-----|-------|
| Tema Cocina (naranja y verde) | `core/identidad/.../Tintas.kt` (`Paleta.Cucha`). Las pantallas nunca usan colores fijos. |
| Personaje | `identidad/personajes/la-cucha.svg`, sus capas `personaje_la_cucha_*` en `core/identidad`. |
| Ícono de la app | `src/main/res/mipmap-*/ic_launcher_*.webp`, hechos con `python3 apps/cucha/herramientas/icono.py`. |
