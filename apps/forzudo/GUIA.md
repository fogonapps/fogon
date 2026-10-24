# Guía de El Forzudo: cómo cambiar cosas

Recetas paso a paso para mantener la app: sacar una versión nueva, cambiar un texto, agregar un ejercicio o un dibujo, etc. Es para Angel, para quien quiera colaborar y para cualquier IA que trabaje en el código.

- Qué hace la app y su historial de versiones: [`README.md`](README.md).
- Cómo está construida por dentro (archivos, datos, lógica): [`ARQUITECTURA.md`](ARQUITECTURA.md).
- Lo común a todas las apps de Fogón: [`docs/`](../../docs/).

Todos los comandos se corren desde la raíz del repo (`~/Projects/fogon`), salvo que se diga otra cosa. En fish no se usan heredocs: los comandos de aquí funcionan tal cual en fish y en bash.

## Dónde está la app

| Lugar | Qué hay |
|-------|---------|
| `~/Projects/fogon` (rama `main`) | El repo de trabajo, con todo el historial. **No se sube a internet**: tiene commits viejos con material sin licencia libre. |
| Rama `publico` → [github.com/fogonapps/fogon](https://github.com/fogonapps/fogon) | Lo público. Historial propio, empezó limpio el 21-10-2026. |
| [fogonapps/el-forzudo](https://github.com/fogonapps/el-forzudo/releases) | El repo de descargas: cada versión con su APK firmado (etiquetas `v<versión>`). El código de cada versión lleva la etiqueta `forzudo-v<versión>` en `fogonapps/fogon`. Hasta la 1.8, las versiones estaban en `fogonapps/fogon/releases`. |
| Obtainium (en el S24) | Sigue `https://github.com/fogonapps/el-forzudo`, sin filtros. |
| F-Droid | Todavía no. Se pide desde el 11-01-2027 (ver más abajo). |
| `~/Fogon/ElForzudo-<versión>.apk` | La última versión firmada, que llega al teléfono por Syncthing. |
| `~/.fogon/firma/` | La llave de firma. **Nunca entra al repo.** Si se pierde, las actualizaciones dejan de instalarse encima. |

## Sacar una versión nueva

Cada cambio que llega al teléfono es una versión nueva. Hay que seguir el orden.

1. **Subir el número** en `apps/forzudo/build.gradle.kts`: `versionCode` +1 (entero, nunca se repite) y `versionName` (por ejemplo, de `1.4.3` a `1.4.4`, o a `1.5` si el cambio es grande).
2. **Escribir las novedades** en tres lugares:
   - `apps/forzudo/README.md` → tabla "Versiones": una fila con la fecha, qué cambió y cómo se probó;
   - `fastlane/metadata/android/es/changelogs/<versionCode>.txt`, en español, máximo 500 caracteres;
   - `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, lo mismo en inglés.
3. **Compilar y revisar:**
   ```
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:forzudo:testDebugUnitTest :apps:forzudo:lintDebug :apps:forzudo:assembleRelease
   ```
   Las pruebas tienen que pasar, y el lint no debe tener errores. Hoy deja 11 avisos y 1 sugerencia conocidos, todos menores.
4. **Probar en el emulador**, instalándola encima de la versión anterior (ver "Probar en el emulador").
5. **Comprobar que es reproducible** (ver `docs/PUBLICAR.md` → F-Droid → "Cómo comprobarlo"). Si no coincide, F-Droid no la va a publicar.
6. **Dejar el APK:** mandar el anterior de `~/Fogon` a la papelera y copiar el nuevo como `~/Fogon/ElForzudo-<versión>.apk`.
7. **Commit en `main`.**
8. **Pasar a lo público y crear la versión en GitHub** (paso a paso en `docs/PUBLICAR.md` → "Subir una versión nueva"). Antes, revisar `git diff --stat publico main`: no debe haber secretos, datos personales ni material sin licencia libre. Las notas de la versión llevan el SHA-256 del APK y el del certificado.
9. **Actualizar la receta de F-Droid** (`fdroid/io.github.fogonapps.forzudo.yml`): `versionName`, `versionCode`, `commit` (el hash completo del commit público de esa versión), `CurrentVersion` y `CurrentVersionCode`. Commit y pasarlo también a `publico`.

Obtainium avisa solo en el teléfono cuando aparece la versión en GitHub.

## Cambiar textos e idiomas

La app está en **español e inglés**. Ningún texto que se vea en pantalla va escrito en el código: todos están en los archivos de idioma.

| Qué | Dónde |
|-----|-------|
| Textos de la app | `src/main/res/values-es/strings.xml` (español) y `src/main/res/values/strings.xml` (inglés, el que Android usa si el teléfono está en otro idioma). Cada texto tiene un nombre (`<string name="tut_hola">`) y se cambia **en los dos archivos**. |
| Textos comunes a todas las apps (Manifiesto, Acerca de, Licencias) | `core/identidad/src/main/res/values{,-es}/strings.xml`. Todos empiezan con `fogon_`, para no chocar con los de una app. |
| Preguntas frecuentes | Arreglos `preguntas` y `respuestas` de `strings.xml`. **También en el `README.md`** → "Preguntas frecuentes": si cambia una, se cambia en los dos lados. |
| Nombre y técnica de cada ejercicio, equipos, músculos, rangos y programas | En español en `src/main/assets/datos/*.json`; en inglés, en `src/main/assets/datos/en/textos.json`. |
| Palabras de la lógica (meses, "reps", consejos de cada serie) | Textos `voc_*` y arreglos `meses`, `dias_*` de `strings.xml`. `Textos.kt` los pasa al `Vocabulario` que usa `Logica.kt`. |
| Descripción en F-Droid | `fastlane/metadata/android/{es,en-US}/` (`title.txt`, `short_description.txt` de máximo 80 caracteres y sin punto final, `full_description.txt`). |

**Reglas de los textos:**
- **Los datos variables** van como `%1$s` (texto) o `%1$d` (número), en el mismo orden de los dos idiomas.
- **Lo que depende de la cantidad** ("1 entreno", "3 entrenos") va como `<plurals>`. En español lleva `one`, `many` y `other`.
- **Apóstrofes y comillas** se escriben `\'` y `\"`. En inglés se usa `’`, que no necesita escaparse.
- **En pantalla** se leen con `stringResource(R.string.x)` (o `pluralStringResource`, `stringArrayResource`). Dentro de una acción (por ejemplo, al tocar un botón) se usa `vm.tx(R.string.x)`, nunca `LocalContext.current.getString`, porque no se entera del cambio de idioma y lint lo marca como error.
- **La prueba `TraduccionTest`** falla si un texto está en un idioma y no en el otro, si las dos versiones tienen distintos datos variables, o si a un ejercicio le falta la traducción.

**Agregar un idioma** (por ejemplo, portugués):
1. Crear `values-pt/strings.xml` en la app y en `core/identidad`, con todos los textos.
2. Crear `assets/datos/pt/textos.json` con la misma forma que el de inglés.
3. Agregarlo a `Idioma.DISPONIBLES` (`core/identidad/.../Idioma.kt`).
4. Agregar en `Textos.kt` cómo se escriben los números en ese idioma (si no, usa los del código del idioma).
5. Ampliar `TraduccionTest` para que revise el idioma nuevo, y agregar su ficha en `fastlane/metadata/android/<código>/`.

El código de la app está en `src/main/java/io/github/fogonapps/forzudo/`.

## Ejercicios

Están en `src/main/assets/datos/ejercicios.json`. Cada ejercicio:

| Campo | Qué es | Ejemplo |
|-------|--------|---------|
| `id` | Identificador. **No se cambia una vez publicado**: los entrenos guardados lo usan. | `"press-banca"` |
| `n` | Nombre que se ve. | `"Press banca"` |
| `g` | Grupo de la lista: `pecho`, `espalda`, `hombro`, `brazo`, `pierna` o `abdomen`. | `"pecho"` |
| `m` | Músculo para rangos y colores (un id de `musculos.json`). | `"pecho"` |
| `f` | Familia: los de la misma familia salen como "lo mismo, de otra forma" al cambiar un ejercicio. | `"press-plano"` |
| `eq` | Equipo, como se lee en la app. | `"Barra"` |
| `lo`, `hi` | Rango de repeticiones (o segundos) por defecto. | `8`, `12` |
| `rest` | Descanso por defecto, en segundos. | `150` |
| `cue` | La técnica en una o dos frases. | |
| `kind` | `w` con peso; `bw` peso corporal; `s` por tiempo (segundos). | `"w"` |
| `std` | Clave en la tabla de fuerza (`estandares.json`), o `null` si no cuenta para rangos. | `"bench"` |

**Agregar uno:**
1. Agregar la entrada en `ejercicios.json`, y su nombre y técnica en inglés en `datos/en/textos.json` (la prueba `TraduccionTest` falla si falta).
2. Si cuenta para rangos, agregar su clave a `herramientas/estandares.py` (en `DERIVADOS`: ejercicio base, razón y si es mancuerna o máquina) y regenerar la tabla:
   ```
   python3 apps/forzudo/herramientas/estandares.py apps/forzudo/src/main/assets/datos/estandares.json
   ```
   Anotar la razón y de dónde salió en `ESTANDARES.md`.
3. Si hay dibujo libre, agregar su lámina (siguiente sección). Si no, la app muestra el ícono del ejercicio.
4. Sacar una versión nueva.

## Láminas (dibujos de los ejercicios)

El generador y el estilo están en `herramientas/laminas/` (ver su `README.md`). Resumen:

1. **Buscar un dibujo libre:**
   - en el repositorio de Everkinetic (`github.com/everkinetic/data`, carpeta `dist/svg`);
   - o en Wikimedia Commons, categoría *Weight training diagrams* (ahí hay dibujos de Everkinetic que no están en su repositorio).

   Revisar la licencia de cada uno. Nada sin licencia clara (ver `docs/LEGAL.md` §6).
2. **Agregarlo a `herramientas/laminas/ejercicios.py`:**
   - `par('<número>', manchas_inicio, manchas_final)` para los del repositorio;
   - `commons('<nombre en Commons>', manchas_1, manchas_2)` para los de Commons. El archivo va en `commons/` dentro de la carpeta de SVG.

   Las manchas son elipses `(x, y, ancho, alto, giro)` en coordenadas del dibujo. Para ubicarlas, dibujar una cuadrícula encima del SVG y probar.
3. **Generar** sin lista de ejercicios, para que también se actualice `assets/datos/laminas.json`:
   ```
   python3 apps/forzudo/herramientas/laminas/laminas.py <carpeta dist/svg> apps/forzudo/src/main/assets/laminas
   ```
   Con una lista (`... press-banca,bulgara`) genera solo esas y no toca `laminas.json`: sirve para probar.
4. **Mirar cada lámina** antes de seguir: que la mancha caiga en el músculo y que no se rellene algo que no es cuerpo.
5. Si la licencia es otra (como la CC BY-SA 3.0 de Commons), agregar el crédito en Licencias (`ui/Pantallas.kt`), en el `README.md` → Créditos y en `docs/LEGAL.md`.
6. Sacar una versión nueva.

**Lo que ya se intentó y no funcionó:** las figuras de mujer con IA (18-10-2026) y los 8 ejercicios que faltan con IA (21-10-2026). Angel los rechazó porque no quedaban al nivel de Everkinetic. Esos 8 tienen su ícono hasta que haya un dibujo libre (la lista está al principio de `ejercicios.py`).

## Programas y rutinas

Están en `src/main/assets/datos/programas.json`. Cada programa tiene `key` (no se cambia una vez publicado), `name`, `level`, `goal`, `desc` y `routines`. Cada rutina:
- `id`, `title`;
- `dow`: el día de la semana sugerido: 0 = domingo, 1 = lunes … 6 = sábado (como `getDay()` de la web);
- `light`: `true` si es un día suave;
- `items`: los ejercicios, como `{"id": "<id del ejercicio>", "sets": 3}`. También pueden llevar `lo`, `hi` y `rest` para cambiar los valores por defecto del ejercicio.

Si alguien ya eligió el programa y lo editó, sus cambios mandan (quedan en su `Estado`). Cambiar el JSON afecta a quien lo elija de nuevo.

## Rangos de fuerza

Se cambian en `herramientas/estandares.py` y se regeneran con el comando de la sección "Ejercicios". Todo cambio se explica en `ESTANDARES.md`, porque mueve los rangos de todos. Los colores y nombres de los rangos (Hierro → Diamante) están en `assets/datos/rangos.json`.

## Colores, letras, ícono y muñequitos

| Qué | Dónde |
|-----|-------|
| Tintas de la app (Gimnasio: lima y oliva) y los cuatro temas de la Apariencia | `core/identidad/.../Tintas.kt` (`Paleta.Forzudo`) y `Apariencia.kt` (`colorBase` de cada tema). Las pantallas nunca usan colores fijos: todo sale de `FogonTema.actual`. Ver `docs/IDENTIDAD.md` → Vidrio y puntos. |
| Colores de cada músculo en las láminas | `herramientas/laminas/laminas.py` (`MUSCULOS`, `ZONAS`) y su `README.md`. |
| Ícono de la app | `src/main/res/mipmap-*/ic_launcher_foreground.webp` y `ic_launcher_monochrome.webp`; el fondo es el color `papel` de `res/values/colores.xml`. |
| Íconos (también los de la barra de pestañas) | `ui/Iconos.kt`: un `Dibujo` de 24 × 24 hecho de rellenos, trazos gordos y huecos, pintado con `IconoRiso` (dos tintas, la segunda corrida). Los comunes están en `IconosFogon` (`core/identidad`). |
| Muñequitos del tutorial y de Rangos | `src/main/res/drawable-nodpi/munieco_*.webp`. Los del tutorial llevan borde de calcomanía (papel y sombra de tinta); los originales sin borde están en `identidad/`. Para ponerle el borde a uno nuevo: agrandar el canal alfa de la figura unos 16 px con un filtro de máximo (PIL `ImageFilter.MaxFilter`), pintarlo de papel `#EFE7DA`, poner debajo la misma forma en tinta corrida 8 px, y la figura encima. |
| Ícono de F-Droid | `fastlane/metadata/android/en-US/images/icon.png` (512 × 512, el mismo ícono sobre el papel). |

## Agregar una pantalla

1. Agregar el caso a `sealed interface Pantalla` en `GymViewModel.kt`.
2. Escribir la función `@Composable` (normalmente en `ui/Pantallas.kt`).
3. Conectarla en `ui/App.kt`, dentro del `when (p)`.
4. Abrirla con `vm.abrir(Pantalla.<Nueva>)`. El botón atrás la cierra solo.
5. Explicarla en `ARQUITECTURA.md` (mapa de archivos).

## Agregar una biblioteca

1. La versión va en `gradle/libs.versions.toml` y la dependencia en `apps/forzudo/build.gradle.kts`.
2. Solo bibliotecas libres compatibles con la GPL-3.0, sin Google Play Services, Firebase ni analíticas.
3. Sumarla a la pantalla de Licencias, con su texto en `assets/licencias/`.
4. Volver a comprobar que la versión sale reproducible.

## Probar en el emulador

```
~/Android/Sdk/emulator/emulator -avd fogon-prueba -no-window -no-audio
~/Android/Sdk/platform-tools/adb install -r apps/forzudo/build/outputs/apk/release/forzudo-release.apk
~/Android/Sdk/platform-tools/adb logcat -b crash -d
```

- **Instalar siempre encima** de la versión anterior: así se prueba que no se borran los datos.
- **Si el emulador se cae** (el proceso termina con código 139 y su registro dice `Failed to find ColorBuffer`): arrancarlo en frío y con gráfico por software, `emulator -avd fogon-prueba -no-snapshot -gpu swiftshader_indirect`. El 21-10-2026 se caía al dibujar degradados radiales grandes junto con el grano; desde el sistema riso no hay degradados en la interfaz (la trama son círculos sueltos). Hay un segundo emulador limpio, `fogon-prueba2`, por si el primero se daña.
- **Probar un tema sin tocar la pantalla:** escribir `shared_prefs/apariencia.xml` con `run-as` (`<string name="estilo">MARCA</string>`, `<int name="version" value="4" />`, y por tema `MARCA.FONDO`, `MARCA.TINTA_1`, `MARCA.TINTA_2` (int ARGB), `MARCA.transparencia`, `MARCA.adorno` (`LUZ` o `NADA`)). Sin `version` = 4, la app borra los ajustes de cada tema al abrir (la migración de Vidrio y puntos). Temas: `GRAFITO`, `MARCA`, `CLARO`, `CONTRASTE`.
- **Al final, `logcat -b crash -d` no debe mostrar nada.**
- **Datos de prueba sin usar los de nadie:** `node herramientas/datos-prueba.js src/main/assets/datos <salida.json>`, desde `apps/forzudo`, da un estado con unos 100 entrenos inventados. Se toma lo que está bajo `"estado"` (sin `active`) y se importa desde Perfil → Importar.
- **Capturas limpias** (para F-Droid): con el modo demo de Android (`settings put global sysui_demo_allowed 1` y `am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000`), y `adb exec-out screencap -p`. Van en `fastlane/metadata/android/<idioma>/images/phoneScreenshots/` (`es` con la app en español, `en-US` en inglés) como JPEG, porque en PNG el grano del papel pesa varios MB.

## Probar en el teléfono

- **Por Obtainium:** después de publicar la versión en GitHub, aparece como actualización.
- **Por cable:** `adb install -r <apk>`, con la depuración USB activada.
- **Pendiente:** probar la sincronización con el Gist usando el token real de Angel.

## F-Droid

Todo está preparado: la compilación reproducible, la ficha en `fastlane/` y la receta en `fdroid/`. Detalle en `docs/PUBLICAR.md` → F-Droid.

- **Cuándo:** desde el **11-01-2027**, porque F-Droid pide unos 3 meses de desarrollo activo.
- **Qué falta:** una cuenta de GitLab de Angel; después, un fork de `fdroiddata`, copiar la receta a `metadata/` y abrir el merge request.
- **Después de entrar:** cada versión nueva en GitHub con su etiqueta `forzudo-v<versión>` la detecta F-Droid, la compila y, si sale idéntica, publica nuestro APK.

## Reglas que no se rompen

- **Nunca cambiar** el `applicationId` (`io.github.fogonapps.forzudo`), los `id` de ejercicios y programas ya publicados, ni el nombre del archivo del Gist (`mi-gym-datos.json`). Cualquiera de esos cambios hace perder datos a la gente.
- **Nunca subir** la rama `main` a GitHub, ni la llave de firma a ningún lado.
- **Todo cambio queda escrito** en el mismo commit: la fila de versión en el `README.md` y esta guía o `ARQUITECTURA.md` si cambió cómo se hace algo.
