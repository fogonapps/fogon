# Guía de El Ahorrador: cómo cambiar cosas

Recetas paso a paso para mantener la app: sacar una versión nueva, cambiar un texto, agregar una categoría, etc. Es para Angel, para quien quiera colaborar y para cualquier IA que trabaje en el código.

- Qué hace la app y su historial de versiones: [`README.md`](README.md).
- Cómo está construida por dentro (archivos, datos, lógica): [`ARQUITECTURA.md`](ARQUITECTURA.md).
- Lo común a todas las apps de Fogón: [`docs/`](../../docs/). La guía más completa, con lo que también vale aquí, es la de [El Forzudo](../forzudo/GUIA.md).

Todos los comandos se corren desde la raíz del repo (`~/Projects/fogon`). En fish no se usan heredocs: los comandos de aquí funcionan tal cual en fish y en bash.

## Dónde está la app

| Lugar | Qué hay |
|-------|---------|
| `~/Projects/fogon` (rama `main`) | El repo de trabajo. **No se sube a internet** (ver la guía de El Forzudo). |
| Rama `publico` → [github.com/fogonapps/fogon](https://github.com/fogonapps/fogon) | Lo público. |
| [GitHub Releases](https://github.com/fogonapps/fogon/releases) | Cada versión con su APK firmado. Etiquetas `ahorrador-v<versión>`. |
| Obtainium | Sigue las versiones de GitHub filtrando por el título "El Ahorrador". |
| `~/Fogon/ElAhorrador-<versión>.apk` | La última versión firmada, que llega al teléfono por Syncthing. |
| `~/.fogon/firma/` | La llave de firma, la misma de todas las apps de Fogón. **Nunca entra al repo.** |

## Sacar una versión nueva

1. **Subir el número** en `apps/ahorrador/build.gradle.kts`: `versionCode` +1 y `versionName`.
2. **Escribir las novedades** en el `README.md` (tabla "Versiones", con cómo se probó) y en `fastlane/metadata/android/{es,en-US}/changelogs/<versionCode>.txt` (máximo 500 caracteres).
3. **Compilar y revisar:**
   ```
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:ahorrador:testDebugUnitTest :apps:ahorrador:lintDebug :apps:ahorrador:assembleRelease
   ```
   Las pruebas tienen que pasar y el lint no debe tener errores. En la 1.0 deja 7 avisos menores conocidos (orden del parámetro `modifier`, la carpeta `mipmap-anydpi-v26`, `localeConfig` y una sugerencia de plural).
4. **Probar en el emulador**, encima de la versión anterior (ver abajo).
5. **Comprobar que es reproducible** (`docs/PUBLICAR.md` → F-Droid → "Cómo comprobarlo", con `:apps:ahorrador`).
6. **Dejar el APK** en `~/Fogon/ElAhorrador-<versión>.apk` (el anterior, a la papelera).
7. **Commit en `main`**, pasarlo a `publico` y crear la versión en GitHub (`docs/PUBLICAR.md` → "Subir una versión nueva"), con la etiqueta `ahorrador-v<versión>` y el título `El Ahorrador <versión>`. **Solo con el visto bueno de Angel.**

## Cambiar textos e idiomas

Igual que en El Forzudo (ver su guía → "Cambiar textos e idiomas"): todo texto va en `src/main/res/values-es/strings.xml` (español) y `src/main/res/values/strings.xml` (inglés), con el mismo nombre en los dos, y `TraduccionTest` revisa que estén completos.

Lo propio de esta app:
- **Plata y fechas** no se escriben a mano: `Formato.pesos(n)` da "$ 842.300" en español y "$842,300" en inglés, y `Formato.fechaCorta` da "21 oct" u "Oct 21". Los meses salen de los arreglos `meses` y `meses_largos`.
- **Un `%` sin datos variables** (por ejemplo "50 % necesidades") va con `formatted="false"`; con datos variables se escribe `%%`.
- **Los nombres de las categorías** (`cat_*`) solo se usan al terminar el tutorial. Cambiarlos no cambia las categorías de quien ya usa la app.
- **Las preguntas frecuentes** (arreglos `preguntas` y `respuestas`) también están en el `README.md`: si cambia una, se cambia en los dos lados.

## Categorías

Están en `Categorias.kt`. Cada una tiene `id` (**no se cambia una vez publicado**: los movimientos lo usan), su nombre en `strings.xml` (`cat_<id>`), su tinta, su ícono (un nombre de `Iconos.porNombre`) y su grupo para el 50/30/20 y el kakeibo (`NECESIDAD`, `GUSTO`, `CULTURA`, `IMPREVISTO`, `AHORRO`). `ingreso = true` la vuelve de ingresos.

**Agregar una:** sumarla a la lista y su nombre a los dos `strings.xml`. Solo la verán quienes empiecen de cero; para los demás habría que agregarla al estado al abrir la app (hoy no se hace).

**Los ids `impuestos` y `otros` no se quitan:** el 4×1000 se anota en `impuestos` y un gasto sin categoría va a `otros`.

## Íconos

`ui/Iconos.kt` dibuja cada ícono como un `Dibujo` de 24 × 24 hecho de rellenos (`relleno("M…")`), trazos gordos (`trazo("M…", 2.6f)`) y huecos que dejan ver el papel (`hueco`, `huecoTrazo`); se pinta con `IconoRiso`, que pone la segunda tinta corrida detrás (ver `docs/IDENTIDAD.md` → Sistema riso → Reglas). Para uno nuevo de categoría o meta, agregarlo a `porNombre` y, si es para metas, a `ICONOS_META` en `ui/Presupuesto.kt`. Los comunes (atrás, más, perfil…) están en `IconosFogon`.

## Sistemas de presupuesto

- **Cuál hay:** el enum `Sistema` en `datos/Modelo.kt`. Su nombre y explicación están en `ui/Tutorial.kt` (`Sistema.nombre`, `Sistema.explicacion`) y en `strings.xml` (`sistema_*`).
- **Cómo calcula cada uno:** `Logica.sobres()` y `Logica.porAsignar()` (ver `ARQUITECTURA.md` → La lógica). Si se cambia, se ajusta `LogicaTest` → `sobresSegunElSistema`.
- **Cómo se ve:** `ui/Presupuesto.kt` (`Sobres`, `Regla`, `Kakeibo`).

## Tarjeta de crédito y 4×1000

- El mínimo y los intereses salen de `Logica.tarjeta()`, con el `%` del mínimo y la tasa mensual de cada cuenta (por defecto 5 % y 2 %). Si se cambia la fórmula, se dice en la pantalla que es aproximado y se ajusta `LogicaTest`.
- El 4×1000 se agrega en `AhorradorViewModel.guardarMovimiento()`, solo al anotar (no al corregir) y solo desde cuentas de tipo banco.

## Avisos de pagos fijos

En `Avisos.kt`: la hora (9:00) está en `programar()` y los días de anticipación en `Logica.porVencer()` (3). Para probar el aviso en el emulador sin esperar:

```
~/Android/Sdk/platform-tools/adb root
~/Android/Sdk/platform-tools/adb shell am broadcast -n io.github.fogonapps.ahorrador/.AvisoPagos
~/Android/Sdk/platform-tools/adb unroot
```

## Colores, letras e ícono

| Qué | Dónde |
|-----|-------|
| Colores de la app (luces verde y amarilla, acento amarillo) y los cinco estilos de la Apariencia | `core/identidad/.../Tintas.kt` (`Paleta.Ahorrador`) y `Apariencia.kt`. Las pantallas nunca usan colores fijos: blanco de los botones = `pildora`/`sobrePildora`, letras de la tarjeta grande = `sobreDestacada`, letras sobre el acento = `paleta.sobreTinta`. Ver `docs/IDENTIDAD.md` → Apariencia. |
| Tinta de cada categoría | `Categorias.kt`. Colores para metas: `COLORES_META` en `ui/Presupuesto.kt`. |
| Ícono de la app | `src/main/res/mipmap-*/ic_launcher_foreground.webp` y `ic_launcher_monochrome.webp`, hechos de `identidad/png/el-ahorrador.png` y `identidad/una-tinta/el-ahorrador.svg`. |
| Ícono de la notificación | `src/main/res/drawable/ic_notificacion.xml` (la chispa). |

## Agregar una pantalla

1. Agregar el caso a `sealed interface Pantalla` en `AhorradorViewModel.kt`.
2. Escribir la función `@Composable` en el archivo de `ui/` que le toque.
3. Conectarla en `ui/App.kt`, dentro del `when (p)`.
4. Abrirla con `vm.abrir(Pantalla.<Nueva>)`. El botón atrás la cierra solo.
5. Explicarla en `ARQUITECTURA.md` (mapa de archivos).

## Probar en el emulador

```
~/Android/Sdk/emulator/emulator -avd fogon-prueba -no-window -no-audio
~/Android/Sdk/platform-tools/adb install -r apps/ahorrador/build/outputs/apk/release/ahorrador-release.apk
~/Android/Sdk/platform-tools/adb logcat -b crash -d
```

- **Instalar siempre encima** de la versión anterior y, al final, `logcat -b crash -d` no debe mostrar nada.
- **El candado** se prueba poniéndole un PIN al emulador: `adb shell locksettings set-pin 1234`; se quita con `adb shell locksettings clear --old 1234`. La pantalla del PIN sale negra en las capturas (Android no deja capturarla).
- **Datos de prueba:** inventados, nunca los de alguien. Se anotan a mano o se importa un respaldo JSON hecho para eso.

## Reglas que no se rompen

- **Nunca cambiar** el `applicationId` (`io.github.fogonapps.ahorrador`) ni los `id` de categorías ya publicados.
- **Sin internet:** la app no pide el permiso `INTERNET`. Si algún día hace falta, es una función opcional y explicada (ver `AGENTS.md`).
- **Nunca subir** la rama `main` a GitHub, ni la llave de firma a ningún lado.
- **Todo cambio queda escrito** en el mismo commit: la fila de versión en el `README.md` y esta guía o `ARQUITECTURA.md` si cambió cómo se hace algo.
