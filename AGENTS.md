# Instrucciones para IAs que trabajen en Fogón

Fogón son apps de Android libres (GPL-3.0-or-later), hechas con IA, sin anuncios ni rastreo. Lee primero:

1. [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md): cómo está hecho todo.
2. `apps/<app>/ARQUITECTURA.md` (cómo está hecha) y `apps/<app>/GUIA.md` (cómo cambiar cosas y sacar versiones): la app que vas a tocar.
3. [`docs/LEGAL.md`](docs/LEGAL.md) y [`docs/IDENTIDAD.md`](docs/IDENTIDAD.md) antes de agregar datos, imágenes, letras, bibliotecas o nombres.

## Reglas

- **Todo en español:** código (nombres de clases, funciones y variables), comentarios y documentos. Imita el estilo del archivo que estás tocando.
- **Nunca colores fijos en las pantallas** (`Color.White`, `Tintas.Tinta`…): todo sale de `FogonTema.actual`, para que funcionen los cinco estilos de la Apariencia. Esquinas con `forma(…)`, tarjetas con `tarjetaFogon()`, íconos con `IconoRiso` (nunca `Icon` de Material ni íconos de línea fina) y nada de degradados. Ver `docs/IDENTIDAD.md` → ADN visual y Sistema riso.
- **Textos de la app en español e inglés**, siempre en `strings.xml` (`values-es` y `values`) y nunca escritos en el código. Todo texto nuevo va en los dos idiomas; `TraduccionTest` lo comprueba. Ver la `GUIA.md` de la app → "Cambiar textos e idiomas".
- **Nada de Google Play Services, Firebase, analíticas ni SDKs privativos.** Solo bibliotecas libres compatibles con la GPL-3.0. Si agregas una, súmala a la pantalla de licencias (`licenciasFogon()` o la lista de la app) con su texto en `assets/licencias/`.
- **Nada de otros sin licencia clara:** imágenes, datos, letras o textos. Que algo esté en internet o se diga "dominio público" no basta; revisa de dónde salió (ver el caso de las fotos en `docs/LEGAL.md` §6).
- **Sin marcas ajenas** en nombres, íconos ni textos, y sin hablar mal de nadie.
- **Local-first:** los datos se quedan en el teléfono. Internet solo para funciones opcionales y explicadas.
- **No cambies un `applicationId` ya publicado,** ni el nombre del archivo del Gist de El Forzudo (`mi-gym-datos.json`).
- **La llave de firma** está en `~/.fogon/firma/` y nunca entra al repositorio.
- **Antes de dar algo por terminado:** compila, corre las pruebas y el lint, y pruébalo en el emulador `fogon-prueba` si cambia algo visible.
- **Documenta en el mismo commit:** el `README.md` de la app (versiones y estado), su `ARQUITECTURA.md` si cambió cómo funciona, su `GUIA.md` si cambió cómo se hace algo, y `docs/` si cambió algo común. Cada app nueva trae su `ARQUITECTURA.md` y su `GUIA.md`.
- **Publicar (GitHub, F-Droid, releases) solo con el visto bueno de Angel.**

## Comandos

Para El Forzudo (`forzudo`); para las otras apps se cambia por su carpeta (`ahorrador`).

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:forzudo:assembleDebug
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:forzudo:testDebugUnitTest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:forzudo:lintDebug
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:forzudo:assembleRelease
~/Android/Sdk/emulator/emulator -avd fogon-prueba -no-window -no-audio
```
