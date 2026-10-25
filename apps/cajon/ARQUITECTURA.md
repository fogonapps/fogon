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
| `datos/Modelo.kt` | `Obra`, `Registro`, `Temporada`, `Cancion`, `Artista`, `Lista`, `Ajustes`, `Estado`; `Tipos` y `Estados`. Sin nada de Android: la versión de PC usa el mismo modelo. |
| `datos/Logica.kt` | Unir al sincronizar, filtrar y ordenar, diario, progreso (episodios, páginas), estados con sus fechas, cifras del año y racha. Sin Android. |
| `datos/Fuentes.kt` | Los clientes de TMDB, Open Library y MusicBrainz (con su pausa de un segundo). |
| `datos/Importar.kt` | El lector de CSV y los importadores de Letterboxd (ZIP o CSV) y Goodreads (CSV); `sumar` sin repetir. |
| `datos/Gist.kt` | El cliente del Gist (archivo `el-cajon-datos.json`). |
| `datos/Almacen.kt` | DataStore (`datastore/estado.json`) y `CajaFuerte`: el token de GitHub y la clave de TMDB cifrados con el Keystore (AES-GCM). |
| `ui/App.kt` | El armazón: pestañas (Inicio, Tu cajón, Listas, Tú), la barra flotante con el + y la pila de pantallas. |
| `ui/Inicio.kt` | Lo terminado en el año, en curso (con progreso), el diario y los pendientes, los retos. `Carrusel`. |
| `ui/Cajon.kt` | La biblioteca: filtros, búsqueda, orden y `Cuadricula` de portadas. `Chips`. |
| `ui/Buscar.kt` | Buscar afuera por tipo (o artistas), con lo que ya está marcado. |
| `ui/Ficha.kt` | `Previa` (antes de agregar), `Ficha` (todo lo de una obra: estado, estrellas, progreso, reseña, diario), `Registrar` y `EditarObra`. |
| `ui/Listas.kt` | Listas, ver y ordenar una lista, editarla, agregar a una lista y el `Diario` completo. |
| `ui/Tu.kt` | Las cuatro favoritas, las cifras (por año o siempre), notas, meses, géneros y creadores; `Retos`. |
| `ui/Artistas.kt` | Artistas, la ficha con su discografía y el artista encontrado antes de guardarlo. |
| `ui/Ajustes.kt` | Ajustes, clave de TMDB, sincronización, acerca de y licencias. |
| `ui/Compartir.kt` | El diálogo que convierte una tarjeta en imagen (`rememberGraphicsLayer`) y las tarjetas de obra y lista. |
| `ui/Comunes.kt` | Piezas propias: `Portada` (con el ícono del tipo y el halo de puntos si no hay imagen), `Estrellas`, `ElegirEstrellas` (medias estrellas), `BotonFecha`, nombres de tipos y estados. |
| `ui/Iconos.kt` | Los íconos de línea redonda de la app (claqueta, televisor, ojo, libro, disco, micrófono, cajón…). |

## 2. Los datos

- **Una obra** guarda lo de afuera (título, año, creador, géneros, sinopsis, portada, fuente, minutos o páginas, temporadas, canciones) y lo de la persona (estado, nota en medias estrellas de 0 a 10, me gusta, reseña, notas, etiquetas, episodios vistos como `"1x3"`, página, registros del diario, fechas).
- **La fuente** dice de dónde salió: `tmdb:movie:603`, `tmdb:tv:1396`, `ol:OL274505W`, `mb:rg:<mbid>`, o vacío si es a mano. Sirve para no agregar dos veces lo mismo y para actualizar los datos.
- **Sincronizar:** cada obra, artista y lista lleva `cambio` (ms). `Logica.unir` se queda, por id, con la versión más reciente. Borrar no quita la fila: la marca `borrada`, así el otro aparato también la borra; si después se vuelve a tocar en el otro, gana el cambio más nuevo. Las migraciones son solo aditivas.
- **TMDB pide no guardar sus datos más de 6 meses sin renovarlos:** cada obra guarda `traida` y, al abrir una de TMDB de hace más de 6 meses (con clave e internet), se renuevan sus datos sin tocar los de la persona.

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
| MusicBrainz (`musicbrainz.org/ws/2`) y Cover Art Archive | Álbumes, canciones, géneros, artistas y discografías; carátulas. | Datos CC0. User-Agent con contacto y una consulta por segundo (`Mutex` + pausa). |

## 5. Lo que queda para la versión de PC

`datos/Modelo.kt` y `datos/Logica.kt` no usan nada de Android: la versión de PC (Compose para escritorio) los reutiliza y lee el mismo Gist.
