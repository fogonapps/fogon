# Arquitectura de El Cajón

Cómo está hecha la app por dentro. Lo común a todas las apps de Fogón (tema, componentes, movimiento, respaldo de todo Fogón) está en [`docs/ARQUITECTURA.md`](../../docs/ARQUITECTURA.md).

## 1. Las piezas

| Archivo | Qué hace |
|---------|----------|
| `CajonApp.kt` | El almacén (DataStore), la caja fuerte de las claves y el cargador de imágenes de Coil (caché de portadas de 150 MB en `cache/portadas`). |
| `MainActivity.kt` | La única pantalla de Android: tema, respaldo, importar de otras apps (selector de archivos) y compartir una imagen (FileProvider, `cache/compartir`). |
| `CajonViewModel.kt` | El estado, la navegación (pestaña + pila de pantallas), la búsqueda, todas las acciones y la sincronización. Todo cambio pasa por `cambiar { }`: memoria, disco a los 300 ms y Gist a los 4 s. |
| `Datos.kt` | `DatosCajon`, el proveedor para el respaldo de todo Fogón. |
| `Widget.kt` | `WidgetCurso` (Glance): lo terminado en el año y lo que está en curso. |
| `datos/Modelo.kt` | `Obra`, `Registro`, `Temporada`, `Cancion`, `Artista` (también escritores y directores: `Personas`), `Lista`, `Ajustes`, `Estado`; `Tipos` y `Estados`. Sin nada de Android: la versión de PC usa el mismo modelo. |
| `datos/Logica.kt` | Unir al sincronizar, filtrar y ordenar, diario, progreso (páginas), estados con sus fechas, cifras del año y racha. Sin Android. |
| `datos/Fuentes.kt` | Los clientes de TMDB (también directores y su filmografía), Open Library (también escritores y sus obras), MusicBrainz (con su pausa de un segundo y reintentos si dice "ocupado") y Wikimedia (fotos de artistas y escritores). |
| `datos/Importar.kt` | El lector de CSV y los importadores de Letterboxd (ZIP o CSV) y Goodreads (CSV); `sumar` sin repetir. |
| `datos/Gist.kt` | El cliente del Gist (archivo `el-cajon-datos.json`). |
| `datos/Almacen.kt` | DataStore (`datastore/estado.json`) y `CajaFuerte`: el token de GitHub y la clave de TMDB cifrados con el Keystore (AES-GCM). |
| `ui/App.kt` | El armazón: pestañas (Inicio, Cine, Música, Libros), la barra flotante con el + (buscar) y la pila de pantallas (Listas y Cifras se abren desde el Inicio). |
| `ui/Inicio.kt` | Lo terminado en el año, todo el cajón por tipo (cada cifra lleva a su apartado), en curso, el diario, los pendientes, las listas y los retos. `Carrusel`. |
| `ui/Cajon.kt` | `Apartado` (Cine, Música o Libros): cifras del año, en curso, su cajón con filtros, búsqueda y orden; en Música, álbumes o artistas; en Libros, libros o escritores; en Cine, por tipo o directores, con su foto. `Cuadricula`, `Chips`. |
| `ui/Buscar.kt` | El buscador: Todo, Cine, Música o Libros arriba; el mejor resultado y los grupos que llegan de cada fuente. `FotoArtista`. |
| `ui/Ficha.kt` | `Previa` (antes de agregar), `Ficha` (todo lo de una obra: estado, estrellas, progreso, reseña, diario), `Registrar` y `EditarObra`. |
| `ui/Listas.kt` | Listas, ver y ordenar una lista, editarla, agregar a una lista y el `Diario` completo. |
| `ui/Tu.kt` | Las cuatro favoritas, las cifras (por año o siempre), notas, meses, géneros y creadores; `Retos`. |
| `ui/Artistas.kt` | Artistas; la ficha de una persona (artista, escritor o director) con su discografía, sus libros o su filmografía; la persona encontrada antes de guardarla. |
| `ui/Ajustes.kt` | Ajustes, clave de TMDB, sincronización, acerca de y licencias. |
| `ui/Compartir.kt` | El diálogo que convierte una tarjeta en imagen (`rememberGraphicsLayer`) y las tarjetas de obra y lista. |
| `ui/Comunes.kt` | Piezas propias: `Portada` (con el ícono del tipo y el halo de puntos si no hay imagen), `Estrellas`, `ElegirEstrellas` (medias estrellas), `BotonFecha`, nombres de tipos y estados. |
| `ui/Iconos.kt` | Los íconos de línea redonda de la app (claqueta, televisor, ojo, libro, disco, micrófono, cajón…). |

## 2. Los datos

- **Una obra** guarda lo de afuera (título, año, creador, géneros, sinopsis, portada, fuente, minutos o páginas, temporadas, canciones, la nota del público `publica` con sus `votos`) y lo de la persona (estado, nota en medias estrellas de 0 a 10, me gusta, reseña, notas, etiquetas, página, registros del diario, fechas).
- **La fuente** dice de dónde salió: `tmdb:movie:603`, `tmdb:tv:1396`, `ol:OL274505W`, `mb:rg:<mbid>`, o vacío si es a mano. Sirve para no agregar dos veces lo mismo y para actualizar los datos.
- **Buscar:** `buscar` lanza una tarea por fuente (TMDB, Open Library, MusicBrainz) y cada una pone sus resultados en `Busqueda.obras` (por tipo), `artistas`, `autores` o `directores` al llegar; `cargando` y `errores` van por fuente. La lista vuelve arriba cuando llega algo nuevo, si la persona no ha bajado.
- **Sincronizar:** cada obra, artista y lista lleva `cambio` (ms). `Logica.unir` se queda, por id, con la versión más reciente. Borrar no quita la fila: la marca `borrada`, así el otro aparato también la borra; si después se vuelve a tocar en el otro, gana el cambio más nuevo. Las migraciones son solo aditivas.
- **TMDB pide no guardar sus datos más de 6 meses sin renovarlos:** cada obra guarda `traida` y, al abrir una de TMDB de hace más de 6 meses (con clave e internet), se renuevan sus datos sin tocar los de la persona.
- **Personas:** escritores y directores se guardan como `Artista`, con `tipo` = `Personas.AUTOR` o `Personas.DIRECTOR` y su fuente (`ol:author:OL…A`, `tmdb:person:<id>`); `Estado.musicosVivos` son solo los de la música. Lo suyo en el cajón: los álbumes unidos por `artista`; de escritores y directores, lo que lleva su nombre en `creador`.
- **Libros (1.4):** la búsqueda pide `lang` para traer de cada obra su mejor edición en el idioma de la app (el título sale de ahí si se parece al de la obra: hay ediciones mal marcadas). `OpenLibrary.ordenar` quita la misma obra repetida (mismo título y autor), las que solo existen en otro idioma y los estudios y guías de otros autores sobre una obra muy editada; lo de más ediciones sube sin perder la relevancia. **Portada:** en la lista, la de la obra; en la ficha, `mejorPortada` mira hasta 10 (ediciones en el idioma y portadas de la obra) en `covers.openlibrary.org/b/id/<id>.json`: descarta los escaneos de la página del título (`source_url` de archive.org) y se queda con la más grande, primero las del idioma.
- **La previa abre al instante** (1.4) con lo de la búsqueda, y lo demás llega después (una línea fina arriba mientras tanto); si se agrega antes, los datos se traen al guardarla.
- **La nota del público** se guarda en la escala de su fuente: TMDB `vote_average` (0 a 10), Open Library `works/<id>/ratings.json` y MusicBrainz `rating` (0 a 5). Sin votos queda vacía. Lo traído antes de la 1.2 (`ANTES_DE_NOTAS`) se renueva una vez al abrir su ficha, en silencio.
- **Episodios:** desde la 1.2 no se marcan (Angel, 25-10-2026: innecesario). `vistos` sigue en el modelo para no perder lo de respaldos viejos, pero la app ya no lo usa.

## 3. Cómo fluye

Agregar un álbum:

1. El + abre `Buscar`; en Álbumes, `MusicBrainz.buscarAlbumes` busca (ordenado por relevancia y número de ediciones).
2. Al tocar uno, `abrirEncontrado` trae los detalles (géneros, canciones de la edición original más vieja) y abre `Previa`.
3. "Escuchado" → `agregar`: guarda la obra, suma el artista a Artistas si no estaba y deja un registro de hoy en el diario. Se abre su `Ficha`.

## 4. Fuentes de datos

| Fuente | Qué trae | Condiciones |
|--------|----------|-------------|
| TMDB (`api.themoviedb.org/3`) | Películas, series y animes en el idioma de la app; si no hay sinopsis, en inglés. Anime = animación (género 16) en japonés. | Licencia no comercial gratuita, clave personal: cada persona pone la suya (v3 "API Key" o v4 "Read Access Token"). Logo y aviso en Acerca de y en la pantalla de la clave. |
| Open Library (`openlibrary.org`) | Libros: título, autor, año, páginas, temas (sin los de bibliotecas), descripción y portada. | Datos abiertos del Internet Archive, sin clave. |
| MusicBrainz (`musicbrainz.org/ws/2`) y Cover Art Archive | Álbumes, canciones, géneros, artistas y discografías; carátulas. | Datos CC0. User-Agent con contacto y una consulta por segundo (`Mutex` + pausa); si responde 503 o 429, se reintenta con más pausa. |
| Wikidata (`query.wikidata.org/sparql`) y Wikimedia Commons | La foto de cada artista y escritor: Wikidata une el id de MusicBrainz (P434) o de Open Library (P648) con su imagen (P18); Commons da el autor y la licencia (`extmetadata`). | Datos de Wikidata CC0; cada foto con su licencia libre (CC BY, CC BY-SA, dominio público…), que pide el crédito: va en la ficha del artista. User-Agent con contacto (también en Coil). |

## 5. Lo que queda para la versión de PC

`datos/Modelo.kt` y `datos/Logica.kt` no usan nada de Android: la versión de PC (Compose para escritorio) los reutiliza y lee el mismo Gist.
