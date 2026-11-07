package io.github.fogonapps.cajon.datos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Modelo de datos de El Cajón. Todo se guarda en un JSON (datastore/estado.json) y es el mismo que va al
 * Gist para sincronizar con el PC.
 * Reglas:
 *  - Las migraciones son solo aditivas (campos nuevos con valor por defecto).
 *  - Cada obra, artista y lista lleva [cambio] (milisegundos): al sincronizar gana el más reciente.
 *  - Borrar no quita la fila: la deja con `borrada = true`, para que el otro aparato también la borre.
 *  - Nada de lógica de Android aquí: el PC usa este mismo archivo.
 */

/** Los tipos de obra. Son los ids que se guardan: no se cambian. */
object Tipos {
    const val PELICULA = "pelicula"
    const val SERIE = "serie"
    const val ANIME = "anime"
    const val LIBRO = "libro"
    const val ALBUM = "album"
    val TODOS = listOf(PELICULA, SERIE, ANIME, LIBRO, ALBUM)
    /** Los que se siguen por episodios. */
    fun porEpisodios(t: String) = t == SERIE || t == ANIME
}

/** Dónde está la persona con una obra. */
object Estados {
    /** Por ver, por leer, por escuchar (la lista de pendientes). */
    const val QUIERO = "quiero"
    const val EN_CURSO = "en_curso"
    const val TERMINADO = "terminado"
    const val ABANDONADO = "abandonado"
    val TODOS = listOf(QUIERO, EN_CURSO, TERMINADO, ABANDONADO)
}

/** Una temporada de una serie o un anime: cuántos episodios tiene. */
@Serializable
data class Temporada(val numero: Int, val episodios: Int, val nombre: String = "")

/** Una canción de un álbum, con lo que la persona opina de ella. */
@Serializable
data class Cancion(
    val titulo: String,
    /** Duración en segundos (0 si no se sabe). */
    val segundos: Int = 0,
    /** Media estrellas: 0 = sin nota, 1 = ½ … 10 = 5 estrellas. */
    val nota: Int = 0,
    val favorita: Boolean = false,
)

/**
 * Una vez que se vio, leyó o escuchó algo: el diario. Una obra puede tener varios (volver a verla).
 * [fecha] es AAAA-MM-DD.
 */
@Serializable
data class Registro(
    val id: String,
    val fecha: String,
    val nota: Int = 0,
    /** Ya la había visto/leído/escuchado antes. */
    val otraVez: Boolean = false,
    val texto: String = "",
    /** Quién lo podría ver (ver [Visibilidad]); vacío = lo que diga el diario en el perfil. */
    val visibilidad: String = "",
)

/**
 * Una película, serie, anime, libro o álbum.
 * Los datos "de afuera" (título, sinopsis, portada…) vienen de TMDB, Open Library o MusicBrainz, o se
 * escriben a mano. Lo "de la persona" (estado, nota, reseña, diario, progreso) es solo suyo.
 */
@Serializable
data class Obra(
    val id: String,
    val tipo: String,
    val titulo: String,
    val original: String = "",
    val anio: Int? = null,
    /** Director, creador, autor o artista. */
    val creador: String = "",
    val generos: List<String> = emptyList(),
    val sinopsis: String = "",
    /** URL de la portada (póster, carátula) y del fondo horizontal (solo cine). */
    val portada: String = "",
    val fondo: String = "",
    /** De dónde salió: "tmdb:movie:603", "tmdb:tv:1396", "ol:OL274505W", "mb:rg:<mbid>"; vacío si es a mano. */
    val fuente: String = "",
    /** Minutos (películas), páginas (libros). */
    val minutos: Int? = null,
    val paginas: Int? = null,
    val temporadas: List<Temporada> = emptyList(),
    val canciones: List<Cancion> = emptyList(),
    /** Álbum, EP, sencillo, en vivo… (como lo dice MusicBrainz o la persona). */
    val formato: String = "",
    /** El id del artista en El Cajón (álbumes), si está. */
    val artista: String? = null,
    /** La nota del público en su fuente (TMDB de 0 a 10; Open Library y MusicBrainz de 0 a 5) y cuántos votaron. */
    val publica: Double? = null,
    val votos: Int = 0,

    // Lo de la persona
    val estado: String = Estados.QUIERO,
    val nota: Int = 0,
    val meGusta: Boolean = false,
    /** La reseña o el análisis: solo de la persona, nunca lo escribe la app. */
    val resena: String = "",
    /** Quién ve la reseña (con su nota) en la red: vacío = privada, ver `Visibilidad`. */
    val resenaVisibilidad: String = "",
    /** Episodios favoritos de una serie o un anime: "1x5" o "1x5|Nombre del episodio". */
    val episodiosFavoritos: List<String> = emptyList(),
    /** La portada la eligió la persona (otra edición, otro póster o una imagen suya): al renovar los datos no se cambia. */
    val portadaPropia: Boolean = false,
    /** El fondo lo eligió la persona: al renovar los datos no se cambia. */
    val fondoPropio: Boolean = false,
    /** Datos curiosos, notas sueltas. */
    val notas: String = "",
    val etiquetas: List<String> = emptyList(),
    /** Episodios vistos, como "1x3" (temporada x episodio). */
    val vistos: List<String> = emptyList(),
    /** Página por la que va (libros). */
    val pagina: Int = 0,
    val registros: List<Registro> = emptyList(),
    /** Cuándo empezó y terminó (AAAA-MM-DD), para libros y series. */
    val empezada: String? = null,
    val terminada: String? = null,

    val agregada: Long = 0,
    val cambio: Long = 0,
    /** Cuándo se trajeron los datos de afuera (TMDB pide no guardarlos más de 6 meses sin renovarlos). */
    val traida: Long = 0,
    val borrada: Boolean = false,
)

/** Escritores y directores se guardan con los artistas, con su tipo; los de MusicBrainz llevan el suyo (Person, Group…). */
object Personas {
    const val AUTOR = "autor"
    const val DIRECTOR = "director"
    fun esMusico(a: Artista) = a.tipo != AUTOR && a.tipo != DIRECTOR
}

/** Un artista o banda (de MusicBrainz o a mano), con lo que la persona sabe y opina. */
@Serializable
data class Artista(
    val id: String,
    val nombre: String,
    val fuente: String = "",
    val pais: String = "",
    /** Año en que empezó o nació. */
    val desde: Int? = null,
    val tipo: String = "",
    val generos: List<String> = emptyList(),
    val notas: String = "",
    val nota: Int = 0,
    val favorito: Boolean = false,
    /** La foto (de Wikimedia Commons, por Wikidata) y su crédito: autor y licencia, que se muestran con ella. */
    val foto: String = "",
    val fotoCredito: String = "",
    /** La foto la subió la persona (`file://`, solo en este teléfono): no se reemplaza con la de afuera. */
    val fotoPropia: Boolean = false,
    /**
     * Ya se buscó y se revisó su foto: tiene una buena (con cara y de buen tamaño, ver `Fotos`) o
     * ninguna de las fuentes la tiene. No se vuelve a buscar sola; "Cambiar la foto" deja elegir.
     */
    val fotoRevisada: Boolean = false,
    /** Una biografía breve (la introducción de su artículo de Wikipedia, por Wikidata) y la dirección del artículo. */
    val bio: String = "",
    val bioFuente: String = "",
    val cambio: Long = 0,
    val borrada: Boolean = false,
)

/** Una lista propia ("Ghibli en orden", "Mis 10 de terror"), con obras de cualquier tipo. */
@Serializable
data class Lista(
    val id: String,
    val nombre: String,
    val descripcion: String = "",
    val obras: List<String> = emptyList(),
    /** Si el orden importa (ranking): se muestran con número. */
    val numerada: Boolean = false,
    /** La portada de la lista: el id de una de sus obras; vacío = la primera. */
    val portada: String = "",
    /** Quién la podría ver ([Visibilidad]); vacío = privada. */
    val visibilidad: String = "",
    val cambio: Long = 0,
    val borrada: Boolean = false,
)

/**
 * Lo que la persona dijo de sus gustos, para "Para ti": que algo le gusta, o lo contrario con su
 * matiz. Se guarda uno por [clave] y se sincroniza como las obras (gana el [cambio] más reciente; quitarlo lo deja
 * `borrada`). Lo que la app aprende sola no se guarda aquí: se calcula de lo del cajón (ver `Recomendar.perfil`);
 * aquí solo queda si la persona dijo que eso aprendido no es así ([Senales.QUITADO]).
 */
@Serializable
data class Gusto(
    /** "genero:<nombre limpio>", "artista:<fuente o nombre limpio>", "autor:…", "obra:<fuente>" o "categoria:<tipo>". */
    val clave: String,
    /** Cómo se muestra ("Terror", "Radiohead", "Dune"). */
    val nombre: String,
    /** Una de [Senales]. */
    val senal: String,
    /** De obras y artistas: su tipo (`Tipos`, o "artista") y su imagen, para mostrarlos en "Tus gustos". */
    val tipo: String = "",
    val imagen: String = "",
    /** De dónde salió ("mb:artist:<mbid>", "tmdb:movie:603", "ol:OL…W"), para pedir lo parecido. */
    val fuente: String = "",
    /** De obras: su autor o director, para recomendar lo suyo. */
    val creador: String = "",
    /** De obras: sus géneros (si no le gusta, lo parecido baja un poco). */
    val generos: List<String> = emptyList(),
    val cambio: Long = 0,
    val borrada: Boolean = false,
)

/**
 * Lo que la persona puede decir de algo. De más suave a más fuerte en lo negativo: [NO_INTERESA] (no ahora, pesa
 * poco), [NO_RECOMENDAR] (eso no, pero no dice nada de sus gustos), [NO_GUSTA] (baja lo parecido) y [EXCLUIR]
 * (nunca nada de eso: un género, artista o autor entero). [QUITADO]: algo que la app aprendió y la persona dijo que
 * no es así.
 */
object Senales {
    const val GUSTA = "gusta"
    const val NO_INTERESA = "no_interesa"
    const val NO_RECOMENDAR = "no_recomendar"
    const val NO_GUSTA = "no_gusta"
    const val EXCLUIR = "excluir"
    const val QUITADO = "quitado"
    val NEGATIVAS = listOf(NO_INTERESA, NO_RECOMENDAR, NO_GUSTA, EXCLUIR)
}

/**
 * Quién podría ver cada parte del perfil. Todavía no hay perfiles públicos:
 * se guarda desde ya para que, cuando existan, cada cosa salga solo como la persona dijo. Sin decir, privada.
 */
object Visibilidad {
    const val PRIVADA = "privada"
    const val AMIGOS = "amigos"
    const val PUBLICA = "publica"
    val TODAS = listOf(PRIVADA, AMIGOS, PUBLICA)
    fun de(v: String?) = v?.takeIf { it in TODAS } ?: PRIVADA
}

/**
 * Una publicación hecha a mano para la red (una foto, unas palabras, sobre una obra o no). Vive en el cajón como todo
 * lo demás (se respalda y se sincroniza) y se sube a Fogón Social con su [visibilidad]; borrarla la deja `borrada`.
 */
@Serializable
data class PublicacionPropia(
    val clave: String,
    val cuando: Long,
    val texto: String = "",
    /** El id de la obra de la que habla, si es de una. */
    val obra: String = "",
    /** El nombre de la imagen en Fogón Social (ya subida), si lleva foto. */
    val imagen: String = "",
    /** [Visibilidad.PUBLICA] o [Visibilidad.AMIGOS] (los seguidores). */
    val visibilidad: String = Visibilidad.PUBLICA,
    /** Cuenta algo del final: en la red sale tapado hasta tocarlo. */
    val spoiler: Boolean = false,
    val cambio: Long = 0,
    val borrada: Boolean = false,
)

/** Los huecos de "destacados" del perfil (los ids que se guardan: no se cambian). */
object Destacados {
    const val PELICULA = "pelicula"
    const val SERIE = "serie"
    const val ANIME = "anime"
    const val ALBUM = "album"
    const val LIBRO = "libro"
    const val ARTISTA = "artista"
    /** "La obra que me marcó" y "mi descubrimiento del momento": cualquier obra, con unas palabras. */
    const val MARCO = "marco"
    const val DESCUBRIMIENTO = "descubrimiento"
    val FAVORITOS = listOf(PELICULA, SERIE, ANIME, ALBUM, LIBRO, ARTISTA)
    val TODOS = FAVORITOS + listOf(MARCO, DESCUBRIMIENTO)
}

/** Las partes del perfil a las que se les elige quién las ve, y las cifras que se pueden mostrar. */
object PartesPerfil {
    const val DESTACADOS = "destacados"
    const val FAVORITAS = "favoritas"
    const val CIFRAS = "cifras"
    const val EVOLUCION = "evolucion"
    const val DIARIO = "diario"
    /** En la red: los artistas que más le gustan. */
    const val MUSICA = "musica"
    /** En la red: los géneros que dijo que le gustan. */
    const val GUSTOS = "gustos"
    /** En la red: lo que guardó de otras personas. */
    const val GUARDADAS = "guardadas"
    val TODAS = listOf(DESTACADOS, FAVORITAS, MUSICA, GUSTOS, CIFRAS, EVOLUCION, DIARIO, GUARDADAS)
    /** Cómo quedan al crear la cuenta en la red, si la persona no eligió otra cosa: lo que hace que el perfil tenga sentido, a la vista. */
    val AL_CREAR_CUENTA = mapOf(DESTACADOS to "publica", FAVORITAS to "publica", MUSICA to "publica", GUSTOS to "publica", CIFRAS to "publica")

    const val C_TERMINADAS = "terminadas"
    const val C_HORAS_CINE = "horas_cine"
    const val C_HORAS_SERIES = "horas_series"
    const val C_PAGINAS = "paginas"
    const val C_CANCIONES = "canciones"
    const val C_DIARIO = "diario"
    const val C_RACHA = "racha"
    const val C_NOTA = "nota"
    val CIFRAS_TODAS = listOf(C_TERMINADAS, C_HORAS_CINE, C_HORAS_SERIES, C_PAGINAS, C_CANCIONES, C_DIARIO, C_RACHA, C_NOTA)
    val CIFRAS_DE_SIEMPRE = listOf(C_TERMINADAS, C_HORAS_CINE, C_PAGINAS, C_CANCIONES)
}

/**
 * El perfil como biografía cultural: quién es la persona a través de lo que le gusta. Se sincroniza
 * entero (gana el [cambio] más reciente). La foto y la portada subidas son `file://` y se quedan en ese teléfono;
 * una portada elegida del cajón (el fondo de una película o serie) es una dirección y sí viaja.
 */
@Serializable
data class Perfil(
    /** Nombre de usuario, sin "@": letras, números, punto y guion bajo. */
    val usuario: String = "",
    val bio: String = "",
    val foto: String = "",
    val portada: String = "",
    val enlaces: List<String> = emptyList(),
    /** Hueco de [Destacados] → id de una obra (o de un artista en [Destacados.ARTISTA]). */
    val destacados: Map<String, String> = emptyMap(),
    /** Unas palabras de por qué, para [Destacados.MARCO] y [Destacados.DESCUBRIMIENTO]. */
    val porQue: Map<String, String> = emptyMap(),
    /** Las cifras que muestra ([PartesPerfil].C_…); null = [PartesPerfil.CIFRAS_DE_SIEMPRE]. */
    val cifras: List<String>? = null,
    /** Parte de [PartesPerfil] → [Visibilidad]. */
    val visibilidad: Map<String, String> = emptyMap(),
    /**
     * La letra elegida del nombre, de antes. Ya no se usa: la persona pega su nombre con la letra
     * que quiera (caracteres de Unicode copiados de afuera). Se deja para leer respaldos viejos sin error.
     */
    val letraNombre: String = "",
    /** Cómo se ve la foto dentro de su círculo y la portada dentro de su franja. */
    val encuadreFoto: Encuadre = Encuadre(),
    val encuadrePortada: Encuadre = Encuadre(),
    /**
     * La canción del perfil: "v2|título|artista|álbum|carátula" (elegida de internet) o, de antes,
     * "<id del álbum en el cajón>|<título>"; vacío = ninguna. Ver `RedSocial.cancionDe`.
     */
    val cancion: String = "",
    val cambio: Long = 0,
)

/**
 * El encuadre de una imagen en su marco: [zoom] desde 1 (la imagen llena el marco, recortada al centro) hasta
 * [ZOOM_MAX], y [x], [y] de -1 (el borde izquierdo o de arriba de la imagen) a 1 (el derecho o el de abajo).
 */
@Serializable
data class Encuadre(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    /** Con los valores dentro de su rango (lo que llega de otra versión o de un respaldo editado a mano). */
    fun limpio() = Encuadre(zoom.takeIf { it.isFinite() }?.coerceIn(1f, ZOOM_MAX) ?: 1f,
        x.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f, y.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f)

    companion object { const val ZOOM_MAX = 4f }
}

/** Lo que va en el perfil y los retos del año. */
@Serializable
data class Ajustes(
    val nombre: String = "",
    /** Las cuatro favoritas del perfil (ids de obras). */
    val favoritas: List<String> = emptyList(),
    /** Reto del año: cuántas obras de cada tipo ("libro" → 24). */
    val retos: Map<String, Int> = emptyMap(),
    /** Lo que quiere en "Para ti" (tipos de `Tipos`; vacío = todo), elegido en "Tus gustos". */
    val categorias: List<String> = emptyList(),
    /** Ya pasó por "Cuéntame tus gustos" (o lo saltó): no se le vuelve a ofrecer en Inicio. */
    val gustosListos: Boolean = false,
    /** Palabras que la persona no quiere ver en la red (publicaciones y comentarios que las lleven se esconden). */
    val palabrasOcultas: List<String> = emptyList(),
    /** El orden de las pestañas de abajo (nombres de `Pestana`; vacío = el de siempre). */
    val ordenPestanas: List<String> = emptyList(),
    /** Las frases del saludo que le gustan (o compartió) y las que no quiere ver (claves, ver `Frases`). */
    val frasesGusta: List<String> = emptyList(),
    val frasesNo: List<String> = emptyList(),
    val cambio: Long = 0,
)

@Serializable
data class Estado(
    val v: Int = 1,
    /** Ya pasó por el tutorial. */
    val listo: Boolean = false,
    val ajustes: Ajustes = Ajustes(),
    val apariencia: AparienciaCuenta? = null,
    val obras: List<Obra> = emptyList(),
    val artistas: List<Artista> = emptyList(),
    val listas: List<Lista> = emptyList(),
    /**
     * Lo escuchado (ver `RegistroEscuchas`), solo cuando el estado sale del teléfono (Gist, respaldo, respaldo de
     * todo Fogón): en el teléfono vive en su propio archivo y aquí va null. Al llegar se une con lo local.
     */
    val escuchas: Oidos? = null,
    /** Lo que dijo de sus gustos, ver [Gusto]. */
    val gustos: List<Gusto> = emptyList(),
    /** El perfil, ver [Perfil]. */
    val perfil: Perfil = Perfil(),
    /** Lo que la persona publicó a mano en la red (con foto o texto), ver [PublicacionPropia]. */
    val publicaciones: List<PublicacionPropia> = emptyList(),
) {
    /** Lo que se ve (sin lo borrado). */
    val vivas get() = obras.filter { !it.borrada }
    val artistasVivos get() = artistas.filter { !it.borrada }
    /** Solo los de la música (sin escritores ni directores). */
    val musicosVivos get() = artistasVivos.filter { Personas.esMusico(it) }
    val listasVivas get() = listas.filter { !it.borrada }
    val gustosVivos get() = gustos.filter { !it.borrada }
}

val JsonCajon = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}
