# Guía de El Cajón: cómo cambiar cosas

Recetas paso a paso para mantener la app. Es para Angel, para quien quiera colaborar y para cualquier IA que trabaje en el código.

- Qué hace la app y sus versiones: [`README.md`](README.md).
- Cómo está construida por dentro: [`ARQUITECTURA.md`](ARQUITECTURA.md).
- Lo común a todas las apps de Fogón: [`docs/`](../../docs/). La guía más completa es la de [El Forzudo](../forzudo/GUIA.md).

Todos los comandos se corren desde la raíz del repo (`~/Projects/fogon`) y funcionan tal cual en fish y en bash.

## Sacar una versión nueva

1. **Subir el número** en `apps/cajon/build.gradle.kts`: `versionCode` +1 y `versionName`.
2. **Escribir las novedades** en el `README.md` (tabla "Versiones", con cómo se probó) y en `fastlane/metadata/android/<es|en-US>/changelogs/<versionCode>.txt`.
3. **Compilar y revisar:**
   ```
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:cajon:testDebugUnitTest :apps:cajon:lintDebug :apps:cajon:assembleRelease
   ```
4. **Probar en el emulador**, también la versión firmada (minificada), y **comprobar que es reproducible** (`docs/PUBLICAR.md`).
5. **Publicar** (`docs/PUBLICAR.md`): repo de descargas `fogonapps/el-cajon` (su README es `docs/descargas/el-cajon.md`), etiqueta `cajon-v<versión>`, y actualizar la receta de F-Droid (`fdroid/io.github.fogonapps.cajon.yml`).

## Datos de prueba y capturas

- **Datos de prueba:** inventados. `python3 herramientas/datos-prueba.py <es|en> <fecha de hoy> <salida.json>` (desde `apps/cajon`) arma un cajón con obras de dominio público y otras inventadas, diario, la nota del público (inventada), páginas, canciones calificadas, dos listas, favoritas y retos. **Sin portadas**: las de verdad tienen dueño y no van en las capturas. En la versión de prueba se copia con `adb push` y `run-as io.github.fogonapps.cajon cp … files/datastore/estado.json`; el idioma va en `shared_prefs/ui.xml` (`idioma`) y el tema en `shared_prefs/apariencia.xml` (`version` 4 y `estilo`: `GRAFITO`, `MARCA` para Vinilo, `CLARO` o `CONTRASTE`).
- **Capturas de F-Droid:** con el modo demo de Android, siete por idioma en `fastlane/metadata/android/<es|en-US>/images/phoneScreenshots/`: Inicio en Grafito y en Vinilo, Cine, un álbum con sus canciones, una serie con la nota del público, Música en Vinilo y Libros en Claro. Sin búsquedas (mostrarían portadas y fotos de otros).

## Agregar una fuente de datos

Cada fuente es un objeto en `datos/Fuentes.kt` que devuelve `Encontrado` al buscar y una `Obra` con los detalles. Antes de sumar una: revisar su licencia y sus condiciones (clave, límites, crédito) y anotarlas en `ARQUITECTURA.md` → Fuentes de datos y en `docs/LEGAL.md`. La fuente nueva lleva su prefijo en `Obra.fuente` (no se cambia después).

## Cambiar el modelo

- Solo campos nuevos con valor por defecto (los respaldos y el Gist viejos tienen que seguir sirviendo).
- Todo cambio de una obra, artista o lista tiene que actualizar su `cambio` (lo hacen `cambiarObra`, `cambiarArtista` y las funciones de listas); si no, la sincronización no lo sube.
- Borrar = `borrada = true`, nunca quitar la fila.
- Si cambia algo del modelo, la versión de PC tiene que cambiar igual.

## Cambiar textos e idiomas

Como en las otras apps: `src/main/res/values-es/strings.xml` y `values/strings.xml`, siempre los dos. `TraduccionTest` lo comprueba.

## Reglas que no se rompen

- **Nunca cambiar** el `applicationId` (`io.github.fogonapps.cajon`), los ids de tipos y estados (`pelicula`, `quiero`…), los prefijos de fuente ni el nombre del archivo del Gist (`el-cajon-datos.json`).
- **La reseña es de la persona:** la app nunca la llena.
- **Las claves** no van en el respaldo, el Gist ni los registros.
