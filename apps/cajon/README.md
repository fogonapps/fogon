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
- **La nota del público** junto a la tuya: la de TMDB (de 0 a 10), Open Library o MusicBrainz (de 0 a 5), con cuántos votaron.
- **Progreso:** páginas (libros) y cada canción de un álbum con su nota y su corazón.
- **Inicio y apartados:** el Inicio dice todo lo del cajón (películas, series y animes vistos, álbumes escuchados, artistas, libros leídos) y cada cosa tiene su apartado: Cine, Música y Libros, con sus cifras del año, lo que está en curso y su cajón con filtros.
- **Buscador** como el de una app de música: arriba Todo, Cine, Música o Libros; abajo el mejor resultado (un artista con su foto) y los grupos, cada uno con "Ver más".
- **Artistas** con su foto (Wikimedia Commons, por Wikidata, con autor y licencia) y su discografía de MusicBrainz para sumar discos con un toque; los artistas de los álbumes se suman solos.
- **Escritores y directores**, como los artistas: escritores de Open Library (con su foto de Wikimedia Commons) y sus libros; directores de TMDB (con su foto) y lo que dirigieron. Van en los apartados de Libros y de Cine.
- **Listas propias** de cualquier tipo, con ranking si se quiere y para reordenar. Las cuatro favoritas en el perfil.
- **Tus cifras:** lo terminado en el año (o siempre), horas de cine y de series, páginas, canciones, entradas del diario, racha, notas por estrellas, meses, géneros y creadores. **Retos** del año por tipo.
- **Compartir** una obra o una lista como imagen (portada, estrellas, reseña).
- **Traer de otras apps:** el historial de Letterboxd (ZIP o CSV) y la biblioteca de Goodreads (CSV), sin repetir lo que ya está.
- **Teléfono y PC:** sincronización opcional con un Gist secreto de la cuenta de GitHub de la persona. La versión de PC leerá el mismo archivo.
- **Widget:** lo terminado este año y lo que está en curso.
- **Respaldo** en JSON y el respaldo de todo Fogón. **Apariencia** como en todas las apps (Grafito, Vinilo, Claro y Contraste). **En español e inglés.**

## Permisos (y por qué)

| Permiso | Para qué |
|---------|----------|
| Internet | Buscar y traer los datos, portadas y fotos (TMDB, Open Library, MusicBrainz, Cover Art Archive, Wikidata y Wikimedia Commons) y, si se activa, sincronizar con GitHub. |
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
| 1.1 | 2026-10-25 | Lo que pidió Angel al probarla: **Inicio con todo el cajón** (películas, series y animes vistos, álbumes escuchados, artistas y libros leídos, cada cifra lleva a su apartado), **apartados de Cine, Música y Libros** en la barra (con sus cifras del año, en curso y su cajón; Música con álbumes y artistas), **buscador nuevo** con Todo, Cine, Música y Libros arriba y resultados por grupos que llegan a la vez (antes la píldora de Libros quedaba escondida), **fotos de los artistas** de Wikimedia Commons por Wikidata, con su crédito en la ficha. Listas y cifras pasan a abrirse desde el Inicio. Arreglado: MusicBrainz a veces responde "ocupado" y ahora se reintenta; los resultados que llegaban tarde quedaban escondidos arriba de la lista; las portadas de Wikimedia necesitaban que la app se identificara. Probada en el emulador, también la versión firmada (foto y crédito de un artista). Publicada el 25-10-2026; reproducible desde la etiqueta pública `cajon-v1.1`. |
| 1.2 | 2026-10-25 | Lo que pidió Angel: **la nota del público** en la ficha ("★ 8,2 de 10 en TMDB · 726 votos") y en las filas de la búsqueda, de TMDB, Open Library (sus calificaciones de lectores) y MusicBrainz; lo agregado antes se completa solo al abrir la ficha. **Sin marcar episodios** (a Angel le sobraba): las series se llevan con su estado; en las cifras, horas de series en vez de episodios. No se agregó ver películas ni series dentro de la app: las fuentes como YTS o los complementos de torrents de Stremio reparten copias sin permiso, y eso pondría en riesgo el repositorio y F-Droid. Probada en el emulador: nota de un libro y de un álbum en vivo, también en la versión firmada; la de TMDB con datos de prueba (sin clave). Publicada el 25-10-2026; reproducible desde la etiqueta pública `cajon-v1.2`. |
| 1.3 | 2026-10-25 | **El muñeco de Fogón** como personaje (lo que pidió Angel): el mismo muñeco en las cuatro apps, hecho en Blender (`identidad/muneco/`), con la cabeza de estrella en el color de la app y el cuerpo en semitono, que toma el color de las letras del tema. El ícono no cambia. Probada en el emulador (tutorial en Grafito y en Claro), también la versión firmada. Publicada el 25-10-2026; reproducible desde su etiqueta pública. |
| 1.4 | 2026-10-25 | Lo que pidió Angel: **escritores** (Open Library, con su foto de Wikimedia Commons y sus libros) y **directores** (TMDB, con su filmografía), como los artistas; en Libros y Cine, junto a las obras. **Libros en tu idioma** y sin tantas ediciones: sin la misma obra repetida, sin las que solo están en otro idioma ni los estudios sobre una obra famosa; **portadas más lindas** (se descartan los escaneos de la página del título y se prefiere la más grande en tu idioma). **La previa abre al instante** (antes un círculo giraba varios segundos mientras llegaban los datos de MusicBrainz). Probada en el emulador (libros y escritores en vivo; directores sin probar en vivo: hace falta una clave de TMDB). |

## Créditos

- Películas, series y animes: [TMDB](https://www.themoviedb.org/), con la clave de cada persona. El Cajón usa TMDB y su API, pero no está respaldada, certificada ni aprobada por TMDB.
- Libros: [Open Library](https://openlibrary.org/) (Internet Archive).
- Música: [MusicBrainz](https://musicbrainz.org/) (núcleo de datos CC0) y carátulas del [Cover Art Archive](https://coverartarchive.org/).
- Fotos de artistas: [Wikimedia Commons](https://commons.wikimedia.org/), encontradas por [Wikidata](https://www.wikidata.org/); cada una con su autor y licencia en la ficha del artista.
- Portadas: Coil, Apache 2.0.
