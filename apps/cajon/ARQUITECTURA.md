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
- **Libros (1.4):** la búsqueda pide `lang` para traer de cada obra su mejor edición en el idioma de la app (el título sale de ahí si se parece al de la obra: hay ediciones mal marcadas). `OpenLibrary.ordenar` quita la misma obra repetida (mismo título y autor), las que solo existen en otro idioma y los estudios y guías de otros autores sobre una obra muy editada; lo de más ediciones sube sin perder la relevancia. **Portadas** (de nuevo desde la 1.8; en la 1.4.1–1.7 iban sin portada): la de la obra en la lista y, en la ficha, la mejor de sus ediciones en el idioma de la app (`mejorPortada`: descarta escaneos); si no gusta, "Cambiar la portada".
- **Buscar portadas** (1.6; automático desde la 1.6.1: al abrir la app, al importar y al poner la clave de TMDB, en silencio, con el avance en Ajustes; lo ya revisado sin éxito queda en `CajaFuerte.portadasRevisadas` y se vuelve a intentar a los 30 días): `buscarPortadas` recorre lo vivo sin `fuente`: libros con `OpenLibrary.buscar` (título + primer autor; desde la 1.8), álbumes con `MusicBrainz.buscarAlbum` (consulta `releasegroup:"…" AND artist:"…"`), cine con `Tmdb.buscar` si hay clave (los animes también como serie y película). Solo enlaza si `Logica.esLaMisma` y si esa fuente no está ya en el cajón; pone `fuente`, `portada` y la nota del público. Se detiene con el mismo botón. Las carátulas de Cover Art Archive a veces fallan de su lado (500): la dirección queda guardada y aparece cuando vuelven.
- **Géneros (1.7):** en artistas y álbumes son etiquetas (`Pildora`): tocar una llama a `buscarGenero`, que abre el buscador de Música con ese texto. `buscar` reconoce un género (`Logica.genero`: los del cajón, `generosDelCajon`, o `Logica.GENEROS_COMUNES`) y entonces muestra primero lo del cajón con ese género y busca en MusicBrainz por etiqueta (`artistasPorGenero`, `albumesPorGenero`, con `etiquetaMusicBrainz` para pasar el nombre en español a la etiqueta en inglés). Sin escribir nada, el buscador muestra los géneros para tocar.
- **Biografía (1.7):** `Artista.bio` y `bioFuente`: la introducción del artículo de Wikipedia del artista (Wikidata por su id de MusicBrainz, P434 → artículo en el idioma de la app o en inglés → `api/rest_v1/page/summary`). `completarBio` la trae al abrir la ficha, una vez por sesión. Va con su crédito (CC BY-SA 4.0).
- **Artistas sin fuente (1.7):** `buscarPortadas` también enlaza los artistas de la música que no tienen fuente: busca su nombre en MusicBrainz y solo enlaza si es el mismo (`tituloLimpio`); después trae sus fotos.
- **La previa abre al instante** (1.4) con lo de la búsqueda, y lo demás llega después (una línea fina arriba mientras tanto); si se agrega antes, los datos se traen al guardarla.
- **La nota del público** se guarda en la escala de su fuente: TMDB `vote_average` (0 a 10), Open Library `works/<id>/ratings.json` y MusicBrainz `rating` (0 a 5). Sin votos queda vacía. Lo traído antes de la 1.2 (`ANTES_DE_NOTAS`) se renueva una vez al abrir su ficha, en silencio.
- **Lo oficial no se edita (1.8):** si la obra tiene `fuente`, la ficha no deja corregir título, año, creador ni sinopsis (vienen de la fuente y se renuevan); solo cambiar la portada. Lo agregado a mano sigue con "Corregir datos" (sin géneros ni portada: eso va en la ficha). Al enlazar algo traído de un respaldo, `actualizarDatos` trae todo lo de afuera, pero respeta los géneros y la sinopsis que ya tenía, y la portada si es propia (`portadaPropia`).
- **Cambiar la portada (1.8):** `Pantalla.CambiarPortada`. `cargarCandidatas` trae para elegir las portadas de la obra y de sus ediciones (`OpenLibrary.portadasParaElegir`), los pósteres de TMDB (`Tmdb.posteres`, con clave) o la carátula del álbum. "Subir una imagen" usa el selector de fotos del sistema (`Acciones.elegirImagen`, `PickVisualMedia`, sin permisos); `copiarImagen` la achica a 800 px y la guarda en `files/portadas/<id>-<hora>.jpg` (`file://…`). Esa imagen es de ese teléfono: en otro aparato (Gist, respaldo) se ve la ficha sin portada.
- **Etiquetas (1.8):** géneros y etiquetas propias, en obras de todo tipo y en artistas, con el mismo `EditorEtiquetas` (`ui/Etiquetas.kt`): píldoras; "Editar" deja quitar con un toque y agregar escribiendo, con sugerencias de las que ya hay en el cajón (`sugerenciasEtiquetas`, `Logica.etiquetasDelCajon`) y géneros comunes. Nada de comas. Tocar una abre el buscador del apartado: si el texto es una etiqueta del cajón, primero sale lo que la lleva (`Logica.tieneEtiqueta`); en Música, además, MusicBrainz por etiqueta.
- **Episodios favoritos (1.8):** `Obra.episodiosFavoritos` (`"1x5"` o `"1x5|Nombre"`). En series y animes con temporadas: se elige la temporada y se tocan los episodios (`Tmdb.episodios` trae los nombres si es de TMDB y hay clave; si no, por número).
- **Enlazar un artista a mano (1.8):** si un músico no se enlazó solo, su ficha deja buscarlo en MusicBrainz y elegir cuál es (`buscarCandidatosArtista`, `enlazarArtista`); así llegan la foto, la biografía y la discografía.
- **Sin clave de TMDB (1.8):** Cine y la ficha de lo que no tiene fuente dicen que hace falta la clave, con el botón para ponerla.
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
