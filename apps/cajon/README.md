# El Cajón · Fogón

<p align="center"><img src="../../identidad/png/el-cajon.png" width="160" alt="El Cajón"></p>

Todo lo que ves, lees y escuchas en un mismo lugar: películas, series, animes, libros, álbumes y artistas, con tu nota, tu reseña y tu diario. Sin anuncios ni rastreo. El personaje carga su cajón de discos, como los que se esculcan en una tienda de vinilos; el nombre lo eligió Angel ("suena más colombiano").

- Cómo está hecha por dentro: [`ARQUITECTURA.md`](ARQUITECTURA.md).
- Cómo cambiar cosas y sacar versiones nuevas: [`GUIA.md`](GUIA.md).
- Descargar: [fogonapps/el-cajon](https://github.com/fogonapps/el-cajon/releases/latest) (con [Obtainium](https://github.com/ImranR98/Obtainium) llegan solas las actualizaciones: pegar `https://github.com/fogonapps/el-cajon`). F-Droid, más adelante.

## Qué hace

Lo que antes se anotaba a mano en tablas de cine, lectura y música, más lo de las apps de diario de cine, libros y discos, en una sola app.

- **Todo en un cajón:** películas, series, animes, libros y álbumes, cada uno con portada, año, director/autor/artista, géneros y sinopsis. Filtros por tipo y estado, búsqueda y orden (recientes, nota, año, A–Z).
- **Buscar y agregar:** libros en Open Library y discos y artistas en MusicBrainz (sin clave); películas, series y animes en TMDB con la clave gratuita de cada persona. Todo se puede agregar o corregir a mano.
- **Estados:** por ver/leer/escuchar, viendo/leyendo/escuchando, visto/leído/escuchado, abandonado. Con las fechas de empezar y terminar.
- **Nota en estrellas** de media en media, me gusta, reseña (solo de la persona: la app nunca la escribe), datos curiosos y etiquetas.
- **Diario:** cada vez que se ve, lee o escucha algo, con fecha, nota de ese día, si fue otra vez y unas palabras.
- **Progreso:** episodios por temporada (series y animes), páginas (libros) y cada canción de un álbum con su nota y su corazón.
- **Artistas** con su discografía de MusicBrainz para sumar discos con un toque; los artistas de los álbumes se suman solos.
- **Listas propias** de cualquier tipo, con ranking si se quiere y para reordenar. Las cuatro favoritas en el perfil.
- **Tus cifras:** lo terminado en el año (o siempre), horas de cine, episodios, páginas, canciones, entradas del diario, racha, notas por estrellas, meses, géneros y creadores. **Retos** del año por tipo.
- **Compartir** una obra o una lista como imagen (portada, estrellas, reseña).
- **Traer de otras apps:** el historial de Letterboxd (ZIP o CSV) y la biblioteca de Goodreads (CSV), sin repetir lo que ya está.
- **Teléfono y PC:** sincronización opcional con un Gist secreto de la cuenta de GitHub de la persona. La versión de PC leerá el mismo archivo.
- **Widget:** lo terminado este año y lo que está en curso.
- **Respaldo** en JSON y el respaldo de todo Fogón. **Apariencia** como en todas las apps (Grafito, Vinilo, Claro y Contraste). **En español e inglés.**

## Permisos (y por qué)

| Permiso | Para qué |
|---------|----------|
| Internet | Buscar y traer los datos y portadas (TMDB, Open Library, MusicBrainz, Cover Art Archive) y, si se activa, sincronizar con GitHub. |
| Datos de las apps de Fogón | Que otra app de Fogón (firmada con la misma llave) guarde o recupere los datos de El Cajón en el respaldo de todo Fogón. Ninguna otra app puede usarlo. |

- **Lo que sale del teléfono:** el texto que se busca (a la fuente elegida) y, con la sincronización, el cajón entero a un Gist secreto de la propia cuenta. Nada más.
- **Las claves** (TMDB y el token de GitHub) quedan cifradas con el Keystore de Android y no van en el respaldo ni en el Gist.
- **Sin copias automáticas a la nube de Google.**

## Compilar

Requiere JDK 21 y el SDK de Android (`local.properties` con `sdk.dir`).

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:cajon:assembleDebug
```

Para la versión firmada, ver [`docs/PUBLICAR.md`](../../docs/PUBLICAR.md).

## Versiones

| Versión | Fecha | Qué cambió |
|---------|-------|------------|
| 1.0 | 2026-10-25 | Primera versión: películas, series, animes, libros, álbumes y artistas; búsqueda en TMDB (con la clave de cada persona), Open Library y MusicBrainz; estados, estrellas, me gusta, reseña, diario, episodios, páginas y canciones calificadas; artistas con discografía; listas; cuatro favoritas; cifras del año y retos; compartir como imagen; importar de Letterboxd y Goodreads; sincronización con un Gist; widget; respaldo; español e inglés. Probada en el emulador (Android 16), también la versión firmada: tutorial, buscar y agregar un libro y un álbum, calificar, episodios de una serie a mano, listas, compartir, ajustes. 13 pruebas de la lógica (unir al sincronizar, borrados, episodios, cifras, CSV, Letterboxd, Goodreads) y la de traducción. La búsqueda en TMDB no se pudo probar en vivo: hace falta una clave. Publicada el 25-10-2026; reproducible desde la etiqueta pública `cajon-v1.0`. |

## Créditos

- Películas, series y animes: [TMDB](https://www.themoviedb.org/), con la clave de cada persona. El Cajón usa TMDB y su API, pero no está respaldada, certificada ni aprobada por TMDB.
- Libros: [Open Library](https://openlibrary.org/) (Internet Archive).
- Música: [MusicBrainz](https://musicbrainz.org/) (núcleo de datos CC0) y carátulas del [Cover Art Archive](https://coverartarchive.org/).
- Portadas: Coil, Apache 2.0.
