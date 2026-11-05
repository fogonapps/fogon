package io.github.fogonapps.cajon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fogonapps.cajon.datos.Artista
import io.github.fogonapps.cajon.datos.ArtistaEncontrado
import io.github.fogonapps.cajon.datos.ClaveMala
import io.github.fogonapps.cajon.datos.ClienteGist
import io.github.fogonapps.cajon.datos.Encontrado
import io.github.fogonapps.cajon.datos.Encuadre
import io.github.fogonapps.cajon.datos.ErrorGitHub
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Importar
import io.github.fogonapps.cajon.datos.JsonCajon
import io.github.fogonapps.cajon.datos.Lista
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.cajon.datos.Fotos
import io.github.fogonapps.cajon.datos.Generos
import io.github.fogonapps.cajon.datos.MusicBrainz
import io.github.fogonapps.cajon.datos.Personas
import io.github.fogonapps.cajon.datos.PersonaDeAfuera
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.RegistroEscuchas
import io.github.fogonapps.cajon.datos.OpenLibrary
import io.github.fogonapps.cajon.datos.Registro
import io.github.fogonapps.cajon.datos.SinRed
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.cajon.datos.Tmdb
import io.github.fogonapps.cajon.datos.Gusto
import io.github.fogonapps.cajon.datos.Senales
import io.github.fogonapps.cajon.datos.Recomendar as Rec
import io.github.fogonapps.cajon.datos.Wikimedia
import io.github.fogonapps.identidad.Idioma
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

/** Las pantallas que se abren encima de las pestañas. */
sealed interface Pantalla {
    /** Buscar algo para agregar, empezando en la [seccion] (ver [Secciones]). */
    /** [elegirPara]: "destacado:<hueco>" o "favorita" si se busca para ponerlo en el perfil (no para ver la previa). */
    data class Buscar(val seccion: String, val elegirPara: String? = null) : Pantalla
    /**
     * Una obra encontrada afuera, antes de agregarla. [personas]: quienes la firman, si la fuente los da (todos los
     * artistas de un álbum); no se guardan en la obra, solo sirven para abrir su ficha sin guardarlos.
     */
    data class Previa(val obra: Obra, val personas: List<ArtistaEncontrado> = emptyList()) : Pantalla
    data class Ficha(val id: String) : Pantalla
    /** Elegir otra portada o subir una imagen propia. */
    /**
     * Elegir otra imagen: [que] = [IMAGEN_PORTADA] o [IMAGEN_FONDO] de una obra ([id] de la obra), o [IMAGEN_FOTO] de
     * una persona ([id] del artista, escritor o director).
     */
    data class CambiarPortada(val id: String, val que: String = CajonViewModel.IMAGEN_PORTADA) : Pantalla
    /** Agregar a mano ([id] null, del [tipo]) o corregir los datos de una obra. */
    data class EditarObra(val id: String?, val tipo: String) : Pantalla
    /** Anotar en el diario: una vez que se vio, leyó o escuchó. */
    data class Registrar(val id: String, val registro: String? = null) : Pantalla
    data class VerArtista(val id: String) : Pantalla
    /** Una persona encontrada afuera (artista, escritor o director), con su ficha completa antes de guardarla. */
    data class PreviaArtista(val artista: ArtistaEncontrado) : Pantalla
    data class VerLista(val id: String) : Pantalla
    data class EditarLista(val id: String?) : Pantalla
    /** Elegir a qué lista va una obra. */
    data class AgregarALista(val obra: String) : Pantalla
    data object Diario : Pantalla
    data object Listas : Pantalla
    /** Las cifras y el perfil (las cuatro favoritas). */
    data object Cifras : Pantalla
    data object Artistas : Pantalla
    data object Retos : Pantalla
    data object Ajustes : Pantalla
    data object Sincronizar : Pantalla
    /** Conectar Spotify, paso a paso. */
    data object ConectarSpotify : Pantalla
    data object AcercaDe : Pantalla
    /** Reportar un fallo, algo que falta o una idea (igual en todas las apps de Fogón). */
    data object Reporte : Pantalla
    /** "Cuéntame tus gustos" y "Tus gustos". */
    data object ElegirGustos : Pantalla
    data object TusGustos : Pantalla
    /** El perfil como biografía cultural, editarlo, elegir un destacado y la portada. */
    data object Perfil : Pantalla
    data object EditarPerfil : Pantalla
    data class ElegirDestacado(val hueco: String) : Pantalla
    data object PortadaPerfil : Pantalla
    /** Ajustar el tamaño y la posición de la foto ([portada] = false) o de la portada del perfil. */
    data class AjustarImagenPerfil(val portada: Boolean) : Pantalla
    data object Manifiesto : Pantalla
    /* La red (Fogón Social) */
    /** Entrar o crear la cuenta, y su configuración. */
    data object CuentaRed : Pantalla
    /** El feed completo: de quienes sigue y explorar. */
    data object Red : Pantalla
    data class PerfilRed(val usuario: String) : Pantalla
    data class PublicacionRed(val id: Long) : Pantalla
    data object AvisosRed : Pantalla
    data object GuardadosRed : Pantalla
    /** Seguidores ([seguidores]) o seguidos de alguien. */
    data class GenteRed(val usuario: String, val seguidores: Boolean) : Pantalla
    data object BloqueadosRed : Pantalla
    /** Publicar unas palabras con o sin foto, sobre [obra] si viene de una ficha. */
    data class Publicar(val obra: String? = null) : Pantalla
    /** Las conversaciones del chat; con [obra], para mandarla a alguien. */
    data class Chats(val obra: String? = null) : Pantalla
    data class Conversacion(val usuario: String) : Pantalla
    data class InfoChat(val usuario: String) : Pantalla
    /** Las cifras de una sección (Cine, Música o Libros). */
    data class CifrasDe(val seccion: String) : Pantalla
    data object ElegirCancion : Pantalla
    data object AjustesAvisos : Pantalla
    data object PalabrasOcultas : Pantalla
    data object EstadoCuenta : Pantalla
    data object CajonEnCuenta : Pantalla
    data object Moderacion : Pantalla
    /** Lo público que lleva una #etiqueta. */
    data class EtiquetaRed(val etiqueta: String) : Pantalla
    data object Apariencia : Pantalla
    data object Licencias : Pantalla
}

enum class Pestana { INICIO, PARA_TI, CINE, MUSICA, LIBROS;
    companion object {
        /** Las pestañas en el orden que eligió la persona (las que falten van al final, en su orden de siempre). */
        fun enOrden(orden: List<String>): List<Pestana> =
            (orden.mapNotNull { n -> entries.firstOrNull { it.name == n } } + entries).distinct()
    }
}

/** Los apartados de la app (y de la búsqueda): cine, música y libros. */
object Secciones {
    const val TODO = "todo"
    /** El apartado Cine (la pestaña): películas, series y animes juntos. En el buscador van por separado. */
    const val CINE = "cine"
    const val PELICULAS = "peliculas"
    const val SERIES = "series"
    const val ANIMES = "animes"
    const val MUSICA = "musica"
    const val LIBROS = "libros"
    /** Personas de la red (Fogón Social), por su @usuario o su nombre. */
    const val PERSONAS = "personas"
    /** Los filtros del buscador (películas, series y animes, y personas de la red). */
    val BUSQUEDA = listOf(TODO, PELICULAS, SERIES, ANIMES, MUSICA, LIBROS, PERSONAS)
    val DE_CINE = setOf(CINE, PELICULAS, SERIES, ANIMES)
    fun de(tipo: String) = when (tipo) { Tipos.LIBRO -> LIBROS; Tipos.ALBUM -> MUSICA; Tipos.SERIE -> SERIES; Tipos.ANIME -> ANIMES; else -> PELICULAS }
    fun tipos(s: String) = when (s) {
        CINE -> listOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME); PELICULAS -> listOf(Tipos.PELICULA); SERIES -> listOf(Tipos.SERIE); ANIMES -> listOf(Tipos.ANIME)
        MUSICA -> listOf(Tipos.ALBUM); LIBROS -> listOf(Tipos.LIBRO); else -> Tipos.TODOS
    }
    /** El buscador no tiene "Cine": abierto desde el apartado Cine, empieza en películas. */
    fun paraBuscar(s: String) = if (s == CINE) PELICULAS else s
    fun dePestana(p: Pestana) = when (p) { Pestana.CINE -> CINE; Pestana.MUSICA -> MUSICA; Pestana.LIBROS -> LIBROS; else -> TODO }
}

/**
 * Lo que muestra la búsqueda: cada fuente busca por su lado y sus resultados llegan por grupos
 * ([obras] por tipo de obra, [artistas] aparte). [cargando] y [errores] van por fuente: "tmdb", "libros", "musica".
 */
data class Busqueda(
    val texto: String = "",
    val seccion: String = Secciones.TODO,
    val obras: Map<String, List<Encontrado>> = emptyMap(),
    val artistas: List<ArtistaEncontrado> = emptyList(),
    val autores: List<ArtistaEncontrado> = emptyList(),
    val directores: List<ArtistaEncontrado> = emptyList(),
    /** Si lo buscado es un género (de los del cajón o de los comunes): se buscan artistas y álbumes de ese género. */
    val genero: String? = null,
    /** Si se busca para poner algo en el perfil (ver [Pantalla.Buscar.elegirPara]). */
    val elegirPara: String? = null,
    /** Personas de la red que coinciden (Fogón Social). */
    val personas: List<io.github.fogonapps.cajon.datos.PersonaSocial> = emptyList(),
    val cargando: Set<String> = emptySet(),
    val errores: Map<String, Int> = emptyMap(),
)

enum class EstadoSync { APAGADA, EN_NUBE, SINCRONIZANDO, LISTA, SIN_CONEXION, ERROR }
data class Sync(val estado: EstadoSync, val usuario: String? = null, val ultima: Long = 0, val mensaje: String? = null)

class CajonViewModel(app: Application) : AndroidViewModel(app) {
    private val cajonApp = app as CajonApp
    private val almacen = cajonApp.almacen
    private val caja = cajonApp.caja
    val L = Logica

    private val _estado = MutableStateFlow<Estado?>(null)
    val estado: StateFlow<Estado?> = _estado

    private val _pila = MutableStateFlow<List<Pantalla>>(emptyList())
    val pila: StateFlow<List<Pantalla>> = _pila
    private val _pestana = MutableStateFlow(Pestana.INICIO)
    val pestana: StateFlow<Pestana> = _pestana

    private val _busqueda = MutableStateFlow(Busqueda())
    val busqueda: StateFlow<Busqueda> = _busqueda

    /** La discografía del artista que se está viendo (de MusicBrainz). */
    /** Cómo va "Buscar portadas" (null si no está corriendo). */
    data class Completando(val hechas: Int, val total: Int, val encontradas: Int)
    private val _completando = MutableStateFlow<Completando?>(null)
    val completando: StateFlow<Completando?> = _completando
    private var trabajoPortadas: Job? = null

    /** Las obras de la persona que se está viendo (discografía, libros o filmografía): null mientras llegan. */
    private val _discografia = MutableStateFlow<Pair<String, List<Encontrado>?>?>(null)
    val discografia: StateFlow<Pair<String, List<Encontrado>?>?> = _discografia

    /** Mientras se traen los datos de una obra encontrada (para mostrar que está cargando). */
    private val _trayendo = MutableStateFlow<String?>(null)
    val trayendo: StateFlow<String?> = _trayendo

    private val _sync = MutableStateFlow(Sync(if (caja.tokenGithub != null) EstadoSync.EN_NUBE else EstadoSync.APAGADA, caja.usuario, caja.ultima))
    val sync: StateFlow<Sync> = _sync

    /**
     * La clave de TMDB a usar: la propia (si la puso antes) o "" = por el servidor de Fogón. Nunca falta, así que todo
     * lo de cine funciona sin pedirle nada a la persona.
     */
    private val claveCine: String? get() = caja.claveTmdb ?: ""

    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes = _mensajes.receiveAsFlow()

    private var guardado: Job? = null
    private var syncJob: Job? = null
    private var buscando: Job? = null

    /** Lo último que esta pantalla escribió en el disco, para distinguirlo de un cambio de afuera. */
    @Volatile private var ultimoGuardado: Estado? = null

    /** La red de El Cajón (Fogón Social), opcional. */
    val red = RedSocial(app, viewModelScope, caja, { _estado.value }, { f -> cambiar(f) }, { avisar(tx(it)) }, { sincronizar() })
    /**
     * Pone en el perfil algo encontrado afuera: si no está en el cajón, se agrega (terminado y con corazón, sin anotar
     * nada en el diario) y sus datos completos llegan después. Vuelve al perfil.
     */
    private fun elegirParaPerfil(r: Encontrado, para: String) {
        val e = _estado.value ?: return
        val id = L.porFuente(e, r.fuente)?.id ?: agregar(Obra("", r.tipo, r.titulo, anio = r.anio, creador = r.creador, portada = r.portada, fuente = r.fuente,
            publica = r.publica, votos = r.votos), Estados.QUIERO, abrirFicha = false).also { if (it.isNotEmpty()) actualizarDatos(it, callado = true) }
        if (id.isEmpty()) return
        cambiarObra(id) { it.copy(estado = Estados.TERMINADO, meGusta = true) }
        ponerEnPerfil(para, id)
    }

    private fun elegirArtistaParaPerfil(a: ArtistaEncontrado, para: String) {
        val e = _estado.value ?: return
        val id = e.artistasVivos.firstOrNull { it.fuente == a.fuente }?.id ?: nuevoId("a").also { nuevo ->
            cambiar { s -> s.copy(artistas = s.artistas + Artista(nuevo, a.nombre, a.fuente, a.pais, a.desde, a.tipo, foto = a.foto, favorito = true, cambio = ahora())) }
        }
        ponerEnPerfil(para, id)
    }

    private fun ponerEnPerfil(para: String, id: String) {
        when {
            para.startsWith("destacado:") -> ponerDestacado(para.removePrefix("destacado:"), id)
            para == "favorita" && id !in (_estado.value?.ajustes?.favoritas ?: emptyList()) -> favorita(id)
        }
        // Cierra el buscador (y la pantalla de elegir, si se vino de ahí) y vuelve al perfil.
        _pila.value = _pila.value.dropLast(1).let { if (it.lastOrNull() is Pantalla.ElegirDestacado) it.dropLast(1) else it }
        avisar(tx(R.string.puesto_en_perfil))
    }

    /** Canciones de afuera para la canción del perfil. */
    private val _canciones = MutableStateFlow<List<io.github.fogonapps.cajon.datos.MusicBrainz.CancionEncontrada>?>(emptyList())
    val canciones: StateFlow<List<io.github.fogonapps.cajon.datos.MusicBrainz.CancionEncontrada>?> = _canciones
    private var buscandoCancion: Job? = null
    fun buscarCancion(q: String) {
        buscandoCancion?.cancel()
        if (q.trim().length < 2) { _canciones.value = emptyList(); return }
        buscandoCancion = viewModelScope.launch {
            delay(400); _canciones.value = null
            _canciones.value = runCatching { io.github.fogonapps.cajon.datos.MusicBrainz.buscarCanciones(q.trim()) }.getOrDefault(emptyList())
        }
    }

    fun palabraOculta(p: String, si: Boolean) = cambiar { e ->
        val limpia = p.trim().lowercase().take(40)
        if (limpia.isEmpty()) e else e.copy(ajustes = e.ajustes.copy(cambio = ahora(),
            palabrasOcultas = if (si) (e.ajustes.palabrasOcultas + limpia).distinct().take(200) else e.ajustes.palabrasOcultas - limpia))
    }

    fun hayLlaveCajon() = caja.llaveCajon != null

    /** Tu perfil, uno solo: con cuenta, como lo ve la red (con lo demás abajo); sin cuenta, el del teléfono. */
    fun abrirMiPerfil() { abrir(Pantalla.Perfil) }

    /** "¿Qué te pareció?": la nota, y si no estaba terminada, queda terminada hoy. */
    /** Los votos de la comunidad a las frases del saludo (se traen al abrir Inicio, como mucho cada 6 horas). */
    private val _frasesComunidad = MutableStateFlow<Map<String, io.github.fogonapps.cajon.datos.Frases.Votos>>(emptyMap())
    val frasesComunidad: StateFlow<Map<String, io.github.fogonapps.cajon.datos.Frases.Votos>> = _frasesComunidad
    private var frasesTraidas = 0L

    private val _frasesDeLaGente = MutableStateFlow<List<io.github.fogonapps.cajon.datos.FraseComunidad>>(emptyList())
    /** Las frases que propuso la gente y aprobó el equipo, en el idioma de la app. */
    val frasesDeLaGente: StateFlow<List<io.github.fogonapps.cajon.datos.FraseComunidad>> = _frasesDeLaGente

    fun cargarFrases() {
        if (System.currentTimeMillis() - frasesTraidas < 6 * 3600_000L) return
        frasesTraidas = System.currentTimeMillis()
        viewModelScope.launch {
            val c = io.github.fogonapps.cajon.datos.ClienteSocial(caja.tokenSocial)
            runCatching { c.frases() }.getOrNull()?.let { m -> _frasesComunidad.value = m.mapValues { io.github.fogonapps.cajon.datos.Frases.Votos(it.value.si, it.value.no) } }
            runCatching { c.frasesComunidad(idioma()) }.getOrNull()?.let { _frasesDeLaGente.value = it }
        }
    }

    /** La frase ya elegida para [franja] (clave y obra), si hay. */
    fun fraseGuardada(franja: String): Pair<String, String?>? =
        caja.fraseGuardada?.split('\t')?.takeIf { it.size == 3 && it[0] == franja }?.let { it[1] to it[2].ifEmpty { null } }

    fun guardarFrase(franja: String, clave: String, obra: String?) { caja.fraseGuardada = "$franja\t$clave\t${obra.orEmpty().replace('\t', ' ')}" }

    /** Manda una frase para que el equipo la revise (con cuenta). */
    fun proponerFrase(texto: String) {
        viewModelScope.launch {
            try {
                io.github.fogonapps.cajon.datos.ClienteSocial(caja.tokenSocial).proponerFrase(texto.trim(), idioma())
                avisar(tx(R.string.frase_propuesta_gracias))
            } catch (x: io.github.fogonapps.cajon.datos.ErrorSocial) {
                avisar(tx(if (x.codigo == 429) R.string.frase_propuesta_muchas else R.string.frase_propuesta_mal))
            } catch (x: java.io.IOException) { avisar(tx(R.string.red_sin_conexion)) }
        }
    }

    /**
     * Lo que la persona dice de una frase: 1 le gusta (o la compartió), -1 no quiere verla más, 0 quita lo que dijo.
     * Se guarda en su cajón y, si tiene cuenta, suma a lo que dice la comunidad (solo el voto, sin nada más).
     */
    fun votarFrase(clave: String, valor: Int) {
        cambiar { e -> e.copy(ajustes = e.ajustes.copy(
            frasesGusta = if (valor == 1) (e.ajustes.frasesGusta + clave).distinct() else e.ajustes.frasesGusta - clave,
            frasesNo = if (valor == -1) (e.ajustes.frasesNo + clave).distinct() else e.ajustes.frasesNo - clave,
            cambio = ahora())) }
        if (caja.tokenSocial != null) viewModelScope.launch { runCatching { io.github.fogonapps.cajon.datos.ClienteSocial(caja.tokenSocial).votarFrase(clave, valor) } }
    }

    fun calificarRapido(id: String, nota: Int) = cambiarObra(id) { o ->
        o.copy(nota = nota, estado = Estados.TERMINADO, terminada = o.terminada ?: hoy().toString(), cambio = ahora())
    }

    fun ponerCancion(c: String) = cambiar { e -> e.copy(perfil = e.perfil.copy(cancion = c, cambio = ahora())) }

    /** El chat de la red, cifrado de extremo a extremo. */
    val chat = Chat(app, viewModelScope, caja, red) { avisar(tx(it)) }

    private var buscandoGente: Job? = null
    /** Buscar personas de la red (para empezar un chat), en [Busqueda.personas]. */
    fun buscarPersonas(q: String) {
        buscandoGente?.cancel()
        if (q.trim().length < 2) { _busqueda.value = _busqueda.value.copy(personas = emptyList()); return }
        buscandoGente = viewModelScope.launch {
            delay(300)
            val gente = runCatching { io.github.fogonapps.cajon.datos.ClienteSocial(caja.tokenSocial).buscar(q.trim()) }.getOrDefault(emptyList())
            _busqueda.value = _busqueda.value.copy(personas = gente)
        }
    }

    init {
        viewModelScope.launch {
            _estado.value = L.normalizar(almacen.data.first())
            sincronizar()
            // La sincronización con el PC (por un Gist de GitHub) ya no está: la cuenta de la red la reemplaza. Se
            // desconecta una vez (el Gist queda en la cuenta de GitHub de la persona; no se borra nada).
            if (caja.tokenGithub != null) { caja.tokenGithub = null; caja.gist = null; _sync.value = Sync(EstadoSync.APAGADA) }
            io.github.fogonapps.cajon.AvisosRed.programar(getApplication())
            red.revisarServidor()
            red.revisarSinLeer()
            red.cargarFeed()
            chat.recibir()
            pasarALatino()
            buscarPortadas()
            // Lo escuchado: lo de Spotify (si está conectado) y lo anotado mientras la app estaba cerrada.
            traerSpotify()
            procesarOidos()
            // Lo que el servicio anota mientras la app está abierta (con "agregar solos", se agrega al momento).
            launch { escuchas.oidos.drop(1).collect {
                if (EscuchaMusica.agregarSolos(getApplication())) procesarOidos()
                // Con la sincronización, lo escuchado también viaja (unos segundos después, como el resto).
                if (caja.llaveCajon != null) { syncJob?.cancel(); syncJob = viewModelScope.launch { delay(15_000); sincronizar() } }
            } }
            // Si otra app de Fogón restauró los datos (respaldo de todo Fogón), se toman.
            almacen.data.drop(1).collect { d -> if (d != ultimoGuardado && d != _estado.value) _estado.value = d }
        }
    }

    fun hoy(): LocalDate = LocalDate.now()
    fun idioma(): String = Idioma.actual(getApplication())
    fun tx(id: Int, vararg a: Any): String = Idioma.envolver(getApplication()).getString(id, *a)
    fun txPlural(id: Int, n: Int, vararg a: Any): String = Idioma.envolver(getApplication()).resources.getQuantityString(id, n, *a)
    private fun avisar(m: String) { _mensajes.trySend(m) }
    /** Un aviso corto abajo (desde una pantalla). */
    fun decir(m: String) = avisar(m)
    private fun nuevoId(prefijo: String) = prefijo + System.currentTimeMillis().toString(36) + Random.nextInt(46656).toString(36)
    private fun ahora() = System.currentTimeMillis()

    /** Todo cambio pasa por aquí: memoria, pantalla, 300 ms después el disco y 4 s después el Gist. */
    private fun cambiar(f: (Estado) -> Estado) {
        val e = _estado.value ?: return
        _estado.value = f(e)
        guardado?.cancel()
        guardado = viewModelScope.launch { delay(300); guardarYa() }
        if (caja.llaveCajon != null) { syncJob?.cancel(); syncJob = viewModelScope.launch { delay(4000); sincronizar() } }
        red.sincronizar()
    }

    suspend fun guardarYa() {
        val e = _estado.value ?: return
        ultimoGuardado = e
        almacen.updateData { e }
        WidgetCurso.actualizar(getApplication())
    }

    /* ----- Navegación ----- */

    fun abrir(p: Pantalla) {
        // Cada búsqueda nueva empieza en blanco, en el tipo pedido.
        if (p is Pantalla.Buscar) { buscando?.cancel(); _busqueda.value = Busqueda(seccion = Secciones.paraBuscar(p.seccion), elegirPara = p.elegirPara) }
        // Buscando para el perfil: tocar un artista lo elige en vez de abrir su ficha.
        if (p is Pantalla.PreviaArtista && _pila.value.lastOrNull() is Pantalla.Buscar) _busqueda.value.elegirPara?.let { elegirArtistaParaPerfil(p.artista, it); return }
        _pila.value = _pila.value + p
    }
    fun cerrar() { _pila.value = _pila.value.dropLast(1) }
    fun reemplazar(p: Pantalla) { _pila.value = _pila.value.dropLast(1) + p }
    fun irA(p: Pestana) { _pila.value = emptyList(); _pestana.value = p }
    fun atras() { if (_pila.value.isNotEmpty()) cerrar() else _pestana.value = Pestana.INICIO }

    /* ----- Tutorial y perfil ----- */

    fun terminarTutorial(nombre: String) = cambiar { e ->
        e.copy(listo = true, ajustes = e.ajustes.copy(nombre = L.recortar(nombre.trim(), 40), cambio = ahora()))
    }

    fun cambiarNombre(nombre: String) = cambiar { e -> e.copy(ajustes = e.ajustes.copy(nombre = L.recortar(nombre.trim(), 40), cambio = ahora())) }

    fun ponerReto(tipo: String, n: Int) = cambiar { e ->
        val retos = if (n <= 0) e.ajustes.retos - tipo else e.ajustes.retos + (tipo to n.coerceAtMost(9999))
        e.copy(ajustes = e.ajustes.copy(retos = retos, cambio = ahora()))
    }

    /** Pone o quita una obra de las cuatro favoritas del perfil. */
    fun favorita(id: String) = cambiar { e ->
        val f = e.ajustes.favoritas
        val nuevas = if (id in f) f - id else (f + id).takeLast(4)
        if (id !in f && f.size >= 4) avisar(tx(R.string.aviso_favoritas_cuatro))
        e.copy(ajustes = e.ajustes.copy(favoritas = nuevas, cambio = ahora()))
    }

    fun cambiarIdioma(codigo: String) = Idioma.elegir(getApplication(), codigo)

    /* ----- Buscar afuera ----- */

    fun cambiarSeccion(seccion: String) {
        _busqueda.value = Busqueda(texto = _busqueda.value.texto, seccion = seccion)
        if (_busqueda.value.texto.isNotBlank()) buscar(_busqueda.value.texto)
    }

    /** Busca en las fuentes de la sección elegida, a la vez; cada una pone sus resultados cuando llegan. */
    fun buscar(texto: String) {
        val t = texto.trim()
        val seccion = _busqueda.value.seccion
        _busqueda.value = _busqueda.value.copy(texto = texto)
        buscando?.cancel()
        if (t.length < 2) { _busqueda.value = Busqueda(texto = texto, seccion = seccion); return }
        buscando = viewModelScope.launch {
            delay(350) // espera a que la persona deje de escribir
            _busqueda.value = Busqueda(texto = texto, seccion = seccion)
            fun fuente(nombre: String, trabajo: suspend () -> Unit) = launch {
                _busqueda.value = _busqueda.value.copy(cargando = _busqueda.value.cargando + nombre)
                try { trabajo() }
                catch (e: ClaveMala) { _busqueda.value = _busqueda.value.copy(errores = _busqueda.value.errores + (nombre to R.string.buscar_tmdb_mala)) }
                catch (e: SinRed) { _busqueda.value = _busqueda.value.copy(errores = _busqueda.value.errores + (nombre to R.string.buscar_sin_red)) }
                finally { _busqueda.value = _busqueda.value.copy(cargando = _busqueda.value.cargando - nombre) }
            }
            // Personas de la red: en "Todo" y en "Personas" (en "Personas", nada más).
            if (seccion == Secciones.TODO || seccion == Secciones.PERSONAS) launch {
                _busqueda.value = _busqueda.value.copy(cargando = _busqueda.value.cargando + "personas")
                val gente = runCatching { io.github.fogonapps.cajon.datos.ClienteSocial(caja.tokenSocial).buscar(t) }.getOrDefault(emptyList())
                _busqueda.value = _busqueda.value.copy(personas = gente, cargando = _busqueda.value.cargando - "personas")
            }
            if (seccion == Secciones.PERSONAS) return@launch
            fun poner(r: List<Encontrado>) {
                val grupos = r.groupBy { it.tipo }
                _busqueda.value = _busqueda.value.copy(obras = _busqueda.value.obras + grupos)
            }
            // ¿Es un género? Entonces se busca lo más visto, leído o escuchado de ese género en cada fuente (desde
            // la 1.15 en todas las secciones; antes, solo en la música). Primero sale lo del cajón que lo lleva.
            val deCine = seccion == Secciones.TODO || seccion in Secciones.DE_CINE
            val generoCine = if (deCine) Generos.cine(t) else null
            val generoLibro = if (seccion == Secciones.TODO || seccion == Secciones.LIBROS) Generos.libro(t) else null
            val musical = seccion == Secciones.TODO || seccion == Secciones.MUSICA
            val genero = if (musical) L.genero(t, generosDelCajon()) else null
            val local = genero ?: _estado.value?.let { e -> L.etiquetasDelCajon(e, Secciones.tipos(seccion).takeIf { seccion != Secciones.TODO }).firstOrNull { L.tituloLimpio(it) == L.tituloLimpio(t) } }
                ?: generoCine?.let { t.trim() } ?: generoLibro?.let { t.trim() }
            if (local != null) _busqueda.value = _busqueda.value.copy(genero = local)
            if (deCine) {
                val clave = claveCine
                if (clave == null) _busqueda.value = _busqueda.value.copy(errores = _busqueda.value.errores + ("tmdb" to R.string.buscar_falta_tmdb))
                else fuente("tmdb") {
                    // Lo de la sección y los directores, a la vez. En "Series" sin los animes; en "Animes", series y
                    // películas animadas japonesas.
                    val tm = Tmdb(clave)
                    // Un género: el de la tabla o, si no está, uno de los nombres de géneros de TMDB ("Acción y aventura").
                    val g = generoCine ?: runCatching { tm.generos(idioma()) }.getOrDefault(emptyMap()).entries
                        .firstOrNull { L.tituloLimpio(it.value) == L.tituloLimpio(t) }?.let { (id, nombre) -> Generos.Cine(listOf(nombre), "$id", "$id") }
                    if (g != null) {
                        if (generoCine == null) _busqueda.value = _busqueda.value.copy(genero = _busqueda.value.genero ?: g.nombres.first())
                        val anime = seccion == Secciones.ANIMES
                        suspend fun de(clase: String) = if (clase == "movie") tm.descubrir(clase, g.peliculas, g.clavePeliculas, anime, idioma())
                            else tm.descubrir(clase, g.series, g.claveSeries, anime, idioma())
                        when (seccion) {
                            Secciones.PELICULAS -> poner(de("movie").filter { it.tipo == Tipos.PELICULA })
                            Secciones.SERIES -> poner(de("tv").filter { it.tipo == Tipos.SERIE })
                            Secciones.ANIMES -> { val series = async { de("tv") }; poner(series.await() + de("movie")) }
                            else -> { val series = async { de("tv") }; poner(de("movie")); poner(series.await()) }
                        }
                        return@fuente
                    }
                    val directores = async { runCatching { tm.buscarDirectores(t, idioma()) }.getOrNull() }
                    when (seccion) {
                        Secciones.PELICULAS -> poner(tm.buscar(t, Tipos.PELICULA, idioma()).filter { it.tipo == Tipos.PELICULA })
                        Secciones.SERIES -> poner(tm.buscar(t, Tipos.SERIE, idioma()).filter { it.tipo == Tipos.SERIE })
                        Secciones.ANIMES -> poner(tm.buscar(t, Tipos.ANIME, idioma()))
                        else -> {
                            val series = async { tm.buscar(t, Tipos.SERIE, idioma()) }
                            poner(tm.buscar(t, Tipos.PELICULA, idioma()))
                            poner(series.await())
                        }
                    }
                    // Los directores, aparte: si fallan, quedan las películas.
                    directores.await()?.let { _busqueda.value = _busqueda.value.copy(directores = it) }
                }
            }
            if (seccion == Secciones.TODO || seccion == Secciones.LIBROS) fuente("libros") {
                if (generoLibro != null) { poner(OpenLibrary.porTema(generoLibro.tema, idioma())); return@fuente }
                poner(OpenLibrary.buscar(t, idioma()))
                // Los escritores, con su foto de Wikimedia Commons (por su id de Open Library en Wikidata).
                val autores = runCatching { OpenLibrary.buscarAutores(t) }.getOrDefault(emptyList())
                _busqueda.value = _busqueda.value.copy(autores = autores)
                val fotos = runCatching { Wikimedia.fotos(autores.map { it.fuente.removePrefix("ol:author:") }, "P648") }.getOrDefault(emptyMap())
                if (fotos.isNotEmpty()) _busqueda.value = _busqueda.value.copy(autores = autores.map { a -> fotos[a.fuente.removePrefix("ol:author:")]?.let { a.copy(foto = it) } ?: a })
            }
            // En la música, un género busca artistas y álbumes con esa etiqueta de MusicBrainz (como en las apps de música).
            if (musical) fuente("musica") {
                // Primero los artistas (con su foto, si la hay) y después los álbumes.
                val etiqueta = genero?.let { L.etiquetaMusicBrainz(it) }
                val artistas = if (etiqueta != null) MusicBrainz.artistasPorGenero(etiqueta) else MusicBrainz.buscarArtistas(t)
                _busqueda.value = _busqueda.value.copy(artistas = artistas)
                val fotos = runCatching { Wikimedia.fotos(artistas.take(8).map { it.mbid }) }.getOrDefault(emptyMap())
                if (fotos.isNotEmpty()) _busqueda.value = _busqueda.value.copy(artistas = artistas.map { a -> fotos[a.mbid]?.let { a.copy(foto = it) } ?: a })
                poner(if (etiqueta != null) MusicBrainz.albumesPorGenero(etiqueta) else MusicBrainz.buscarAlbumes(t))
            }
        }
    }

    /** Trae los datos completos de algo encontrado y lo muestra antes de agregarlo (o abre su ficha si ya está). */
    fun abrirEncontrado(r: Encontrado) {
        val e = _estado.value ?: return
        if (_pila.value.lastOrNull() is Pantalla.Buscar) _busqueda.value.elegirPara?.let { elegirParaPerfil(r, it); return }
        L.porFuente(e, r.fuente)?.let { abrir(Pantalla.Ficha(it.id)); return }
        // Se abre ya, con lo que trajo la búsqueda; lo demás (sinopsis, canciones, géneros) llega después y
        // reemplaza la previa si la persona sigue en ella. Así no hay que esperar a MusicBrainz (1 consulta por segundo).
        abrir(Pantalla.Previa(Obra("", r.tipo, r.titulo, anio = r.anio, creador = r.creador, portada = r.portada, fuente = r.fuente,
            publica = r.publica, votos = r.votos, paginas = if (r.tipo == Tipos.LIBRO) r.detalle.removeSuffix(" p.").toIntOrNull() else null,
            formato = if (r.tipo == Tipos.ALBUM) r.detalle else "")))
        _trayendo.value = r.fuente
        viewModelScope.launch {
            try {
                // De un álbum llegan también todos los que lo firman (con su foto, si Wikidata la tiene).
                val (o, firman) = if (r.fuente.startsWith("mb:")) MusicBrainz.detallesConArtistas(r) else detalles(r) to emptyList()
                val fotos = if (firman.isEmpty()) emptyMap() else runCatching { Wikimedia.fotos(firman.map { it.mbid }) }.getOrDefault(emptyMap())
                val personas = firman.map { a -> fotos[a.mbid]?.let { a.copy(foto = it) } ?: a }
                if ((_pila.value.lastOrNull() as? Pantalla.Previa)?.obra?.fuente == r.fuente) reemplazar(Pantalla.Previa(o, personas))
            } catch (x: ClaveMala) { avisar(tx(R.string.buscar_tmdb_mala))
            } catch (x: Exception) {
                // Sin internet o algo raro: se puede agregar igual con lo que trajo la búsqueda.
                avisar(tx(R.string.aviso_sin_detalles))
            } finally { if (_trayendo.value == r.fuente) _trayendo.value = null }
        }
    }

    private suspend fun detalles(r: Encontrado): Obra = when {
        r.fuente.startsWith("tmdb:") -> Tmdb(claveCine ?: throw ClaveMala()).detalles(r.fuente, r.tipo, idioma())
        r.fuente.startsWith("ol:") -> OpenLibrary.detalles(r, idioma())
        else -> MusicBrainz.detalles(r)
    }

    /**
     * Guarda una obra nueva (de afuera o a mano) con el [estado] elegido. Si se marca como terminada, queda un
     * registro de hoy en el diario. Los álbumes suman su artista a Artistas si no está.
     */
    fun agregar(o: Obra, estado: String, abrirFicha: Boolean = true): String {
        val e0 = _estado.value ?: return ""
        L.porFuente(e0, o.fuente)?.let { if (abrirFicha) reemplazar(Pantalla.Ficha(it.id)); return it.id }
        val id = nuevoId("o")
        val hoy = hoy()
        cambiar { e ->
            var artistas = e.artistas
            var artistaId: String? = null
            if (o.tipo == Tipos.ALBUM && o.creador.isNotBlank()) {
                val fuenteArtista = o.artista?.takeIf { it.startsWith("mb:artist:") } ?: ""
                val ya = e.artistasVivos.firstOrNull { (fuenteArtista.isNotEmpty() && it.fuente == fuenteArtista) || io.github.fogonapps.cajon.datos.normal(it.nombre) == io.github.fogonapps.cajon.datos.normal(o.creador) }
                artistaId = ya?.id ?: nuevoId("a").also { artistas = artistas + Artista(it, o.creador, fuenteArtista, cambio = ahora()) }
            }
            var nueva = L.cambiarEstado(o.copy(id = id, artista = artistaId, agregada = ahora(), cambio = ahora()), estado, hoy)
            if (estado == Estados.TERMINADO) nueva = nueva.copy(registros = listOf(Registro(nuevoId("g"), hoy.toString())))
            e.copy(obras = e.obras + nueva, artistas = artistas)
        }
        avisar(tx(R.string.aviso_agregada, o.titulo))
        if (abrirFicha) reemplazar(Pantalla.Ficha(id))
        // Si se agregó antes de que llegaran los datos completos (la previa se abre al instante), se traen ahora.
        if (o.traida == 0L && o.fuente.isNotEmpty()) actualizarDatos(id, callado = true)
        return id
    }

    /** Cambia una obra y marca la hora del cambio (para sincronizar). */
    fun cambiarObra(id: String, f: (Obra) -> Obra) = cambiar { e ->
        e.copy(obras = e.obras.map { if (it.id == id) f(it).copy(cambio = ahora()) else it })
    }

    fun ponerEstado(id: String, estado: String) = cambiarObra(id) { L.cambiarEstado(it, estado, hoy()) }
    fun ponerNota(id: String, nota: Int) = cambiarObra(id) { it.copy(nota = nota.coerceIn(0, 10)) }
    fun meGusta(id: String) = cambiarObra(id) { it.copy(meGusta = !it.meGusta) }
    fun ponerResena(id: String, texto: String) = cambiarObra(id) { it.copy(resena = texto.take(10000)) }
    fun visibilidadResena(id: String, v: String) = cambiarObra(id) { it.copy(resenaVisibilidad = v) }
    fun ponerNotas(id: String, texto: String) = cambiarObra(id) { it.copy(notas = texto.take(5000)) }

    fun ponerPagina(id: String, pagina: Int) = cambiarObra(id) { o ->
        val p = pagina.coerceIn(0, o.paginas ?: 99999)
        var r = o.copy(pagina = p)
        if (p > 0 && o.estado == Estados.QUIERO) r = L.cambiarEstado(r, Estados.EN_CURSO, hoy())
        if (o.paginas != null && p >= o.paginas && o.estado != Estados.TERMINADO) r = L.cambiarEstado(r, Estados.TERMINADO, hoy())
        r
    }

    fun notaCancion(id: String, i: Int, nota: Int) = cambiarObra(id) { o ->
        o.copy(canciones = o.canciones.mapIndexed { k, c -> if (k == i) c.copy(nota = nota.coerceIn(0, 10)) else c })
    }

    fun cancionFavorita(id: String, i: Int) = cambiarObra(id) { o ->
        o.copy(canciones = o.canciones.mapIndexed { k, c -> if (k == i) c.copy(favorita = !c.favorita) else c })
    }

    /** Guarda los datos escritos a mano (nueva si [o].id está vacío). */
    fun guardarObra(o: Obra) {
        if (o.id.isEmpty()) { agregar(o, Estados.QUIERO); return }
        cambiarObra(o.id) { viejo -> o.copy(registros = viejo.registros, vistos = viejo.vistos) }
        cerrar()
    }

    /** Borra la obra (queda marcada como borrada para que el otro aparato también la borre). */
    /* ----- Selección de varios (mantener presionado en una cuadrícula) ----- */

    private val _seleccion = MutableStateFlow<Set<String>>(emptySet())
    /** Los ids elegidos (obras o personas, nunca mezclados): mientras haya alguno, tocar elige en vez de abrir. */
    val seleccion: StateFlow<Set<String>> = _seleccion

    fun alternarSeleccion(id: String) { _seleccion.value = if (id in _seleccion.value) _seleccion.value - id else _seleccion.value + id }
    fun limpiarSeleccion() { _seleccion.value = emptySet() }

    /** Borra todo lo elegido (obras o personas) de una vez. */
    fun borrarSeleccion() {
        val ids = _seleccion.value
        val t = ahora()
        cambiar { e ->
            e.copy(
                obras = e.obras.map { if (it.id in ids) it.copy(borrada = true, cambio = t) else if (it.artista in ids) it.copy(artista = null, cambio = t) else it },
                artistas = e.artistas.map { if (it.id in ids) it.copy(borrada = true, cambio = t) else it },
                listas = e.listas.map { l -> if (l.obras.any { it in ids }) l.copy(obras = l.obras - ids, cambio = t) else l },
                ajustes = if (e.ajustes.favoritas.any { it in ids }) e.ajustes.copy(favoritas = e.ajustes.favoritas - ids, cambio = t) else e.ajustes,
            )
        }
        avisar(tx(R.string.aviso_borradas, ids.size))
        limpiarSeleccion()
    }

    fun estadoSeleccion(estado: String) { _seleccion.value.forEach { ponerEstado(it, estado) }; limpiarSeleccion() }

    /** Suma lo elegido a una lista (lo que ya estaba, queda). */
    fun seleccionALista(lista: String) {
        val ids = _seleccion.value
        cambiar { e -> e.copy(listas = e.listas.map { l -> if (l.id != lista) l else l.copy(obras = l.obras + ids.filter { it !in l.obras }, cambio = ahora()) }) }
        limpiarSeleccion()
    }

    fun borrarObra(id: String) {
        cambiar { e ->
            e.copy(
                obras = e.obras.map { if (it.id == id) it.copy(borrada = true, cambio = ahora()) else it },
                listas = e.listas.map { l -> if (id in l.obras) l.copy(obras = l.obras - id, cambio = ahora()) else l },
                ajustes = if (id in e.ajustes.favoritas) e.ajustes.copy(favoritas = e.ajustes.favoritas - id, cambio = ahora()) else e.ajustes,
            )
        }
        _pila.value = _pila.value.filterNot { it is Pantalla.Ficha && it.id == id || it is Pantalla.Registrar && it.id == id }
    }

    /** Vuelve a traer los datos de afuera (TMDB pide renovarlos cada 6 meses) sin tocar lo de la persona. */
    fun actualizarDatos(id: String, callado: Boolean = false) {
        viewModelScope.launch {
            // Callado (en segundo plano), un fallo no se avisa: se intenta otra vez más adelante.
            try { traerDatos(id, callado) } catch (x: kotlinx.coroutines.CancellationException) { throw x } catch (_: Exception) { }
        }
    }

    /** Callado, los errores se lanzan (para que quien llama decida); si no, se avisan. */
    private suspend fun traerDatos(id: String, callado: Boolean) {
        val o = _estado.value?.vivas?.firstOrNull { it.id == id } ?: return
        if (o.fuente.isEmpty()) return
        run {
            try {
                val r = io.github.fogonapps.cajon.datos.Encontrado(o.tipo, o.titulo, o.anio, o.creador, o.portada, o.fuente, o.paginas?.let { "$it p." } ?: o.formato)
                val d = detalles(r)
                cambiarObra(id) { v ->
                    // Las etiquetas (géneros) y la sinopsis que ya tenía se respetan: la persona pudo haberlas ajustado; la
                    // portada también, si la eligió ella.
                    // Un autor en otro alfabeto ("Πλάτων") no reemplaza el que se escribió ("Platón").
                    val creador = if (v.creador.isNotBlank() && d.creador.any { it.isLetter() && it.code > 0x24F }) v.creador else d.creador.ifBlank { v.creador }
                    // En libros, el título es el de la traducción que se leyó: si el de la fuente es otro ("Convenience
                    // store woman" para "La dependienta"), se queda el de la persona; si es el mismo, el de la fuente.
                    val titulo = if (v.tipo == Tipos.LIBRO && v.titulo.isNotBlank() && L.tituloLimpio(v.titulo) != L.tituloLimpio(d.titulo)) v.titulo else d.titulo
                    v.copy(titulo = titulo, original = if (titulo != d.titulo && d.original.isBlank()) d.titulo else d.original, anio = d.anio ?: v.anio, creador = creador,
                        generos = v.generos.ifEmpty { d.generos }, sinopsis = v.sinopsis.ifBlank { d.sinopsis },
                        portada = if (v.portadaPropia) v.portada else d.portada.ifBlank { v.portada },
                        fondo = if (v.fondoPropio) v.fondo else d.fondo.ifBlank { v.fondo }, minutos = d.minutos ?: v.minutos, paginas = d.paginas ?: v.paginas,
                        temporadas = d.temporadas.ifEmpty { v.temporadas },
                        canciones = if (d.canciones.isEmpty()) v.canciones else d.canciones.mapIndexed { i, c -> v.canciones.getOrNull(i)?.takeIf { it.titulo == c.titulo }?.let { c.copy(nota = it.nota, favorita = it.favorita) } ?: c },
                        traida = d.traida, publica = d.publica ?: v.publica, votos = if (d.publica != null) d.votos else v.votos)
                }
                if (!callado) avisar(tx(R.string.aviso_datos_al_dia))
            } catch (x: ClaveMala) { if (!callado) avisar(tx(R.string.buscar_tmdb_mala)); if (callado) throw x }
            catch (x: kotlinx.coroutines.CancellationException) { throw x }
            catch (x: Exception) { if (!callado) avisar(tx(R.string.buscar_sin_red)); if (callado) throw x }
        }
    }

    /**
     * Hasta la 1.8 TMDB se pedía en español de España: "¡Olvídate de mí!" en vez de "Eterno resplandor de una mente
     * sin recuerdos", y lo escrito con el título latino ("Coraline y la puerta secreta") no se enlazaba. Una sola vez
     * (con clave de TMDB): lo de cine que no se enlazó se vuelve a buscar, y lo ya enlazado se vuelve a pedir en latino.
     */
    private suspend fun pasarALatino() {
        // 1.9.3: con las partes del título y los autores en otro alfabeto se enlaza más: lo que no se había enlazado
        // (libros, cine, artistas) se vuelve a buscar una vez.
        if (caja.versionEnlace < 1) { caja.portadasRevisadas = emptySet(); caja.versionEnlace = 1 }
        if (caja.versionCine >= 2 || claveCine == null) return
        val e = _estado.value ?: return
        val cine = e.vivas.filter { it.tipo in setOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME) }
        caja.portadasRevisadas = caja.portadasRevisadas - cine.map { it.id }.toSet()
        try {
            cine.filter { it.fuente.startsWith("tmdb:") }.forEach { traerDatos(it.id, callado = true) }
            caja.versionCine = 2
        } catch (_: Exception) { /* sin internet o sin clave: se intenta la próxima vez */ }
    }

    /**
     * Lo que llegó enlazado pero sin los datos de afuera (un respaldo armado afuera, otra app, una sincronización):
     * `fuente` puesta y `traida` = 0. Se traen en segundo plano, de a una (sinopsis, temporadas, páginas…).
     */
    private var trabajoDatos: Job? = null

    private fun completarDatos() {
        if (trabajoDatos?.isActive == true) return
        val clave = claveCine
        val faltan = _estado.value?.vivas.orEmpty().filter { it.fuente.isNotEmpty() && it.traida <= 0L && (clave != null || !it.fuente.startsWith("tmdb:")) }
        if (faltan.isEmpty()) return
        trabajoDatos = viewModelScope.launch {
            for (o in faltan) {
                try { traerDatos(o.id, callado = true) } catch (x: kotlinx.coroutines.CancellationException) { throw x } catch (_: Exception) { break }
            }
        }
    }

    /** Las obras con datos de TMDB de hace más de 6 meses se renuevan al abrirlas (si hay clave e internet). */
    fun renovarSiHaceFalta(o: Obra) {
        val tmdb = o.fuente.startsWith("tmdb:")
        if (tmdb && claveCine == null || o.fuente.isEmpty() || o.traida <= 0) return
        // Lo de TMDB, cada 6 meses; lo que se trajo sin la nota del público, una vez.
        if (tmdb && ahora() - o.traida > SEIS_MESES || o.publica == null && o.traida < ANTES_DE_NOTAS) actualizarDatos(o.id, callado = true)
    }

    /* ----- Portadas que faltan ----- */

    /**
     * Busca afuera lo que llegó sin fuente (a mano o de otra app): películas, series y animes en TMDB (si hay
     * clave) y álbumes en MusicBrainz. Solo enlaza si es la misma obra ([Logica.esLaMisma]); entonces pone la
     * fuente, la portada y la nota del público, sin tocar lo de la persona. Los libros van sin portada.
     * Es automático: corre al abrir la app, al traer algo de otra app o de un respaldo y al poner la clave de TMDB,
     * en silencio; lo ya revisado no se vuelve a buscar ([CajaFuerte.portadasRevisadas]).
     */
    private fun libroIdeal(o: Obra, r: Encontrado) = L.esLaMisma(o, r) && L.tituloLimpio(o.titulo) == L.tituloLimpio(r.titulo) && r.portada.isNotBlank()

    fun buscarPortadas() {
        // Al abrir, al importar o al poner la clave: también los datos de lo que llegó enlazado sin ellos.
        completarDatos()
        if (trabajoPortadas?.isActive == true) return
        val clave = claveCine
        val revisadas = caja.portadasRevisadas.toMutableSet()
        val pendientes = _estado.value?.vivas.orEmpty()
            .filter { it.fuente.isEmpty() && (it.tipo == Tipos.ALBUM || it.tipo == Tipos.LIBRO || clave != null) && it.id !in revisadas }
        // Los artistas guardados sin fuente (a mano o de otra app) se enlazan con MusicBrainz: así traen foto y biografía.
        val artistas = _estado.value?.musicosVivos.orEmpty().filter { it.fuente.isEmpty() && it.id !in revisadas }
        // Escritores (Open Library) y directores (TMDB, con clave) guardados sin fuente: así tienen foto, biografía y obras.
        val personas = _estado.value?.artistasVivos.orEmpty().filter { !Personas.esMusico(it) && it.fuente.isEmpty() && it.id !in revisadas &&
            (it.tipo == Personas.AUTOR || clave != null) }
        if (pendientes.isEmpty() && artistas.isEmpty() && personas.isEmpty()) return
        trabajoPortadas = viewModelScope.launch {
            val total = pendientes.size + artistas.size + personas.size
            var encontradas = 0
            // Un fallo suelto (MusicBrainz "ocupado", Open Library con un error 500 o lenta) no detiene todo ni se da por
            // revisado: se salta y se reintenta la próxima vez; si fallan 5 seguidas, no hay internet y se para.
            var fallos = 0
            fun fallo(x: Throwable): Boolean { if (x is ClaveMala || x is kotlinx.coroutines.CancellationException) throw x; if (++fallos >= 5) throw SinRed(); return true }
            _completando.value = Completando(0, total, 0)
            try {
                pendientes.forEachIndexed { i, o ->
                    val candidatos = runCatching {
                        if (o.tipo == Tipos.ALBUM) MusicBrainz.buscarAlbum(o.titulo, o.creador)
                        else if (o.tipo == Tipos.LIBRO) {
                            val autor = o.creador.substringBefore(",").trim().trimEnd('.')
                            // Lo que llega de una búsqueda por autor es de ese autor, aunque Open Library lo tenga en otro
                            // alfabeto ("太宰 治" para Osamu Dazai, "Лев Толстой" para León Tolstói): se compara como el mismo.
                            suspend fun porAutor(t: String, lengua: String) = OpenLibrary.buscar(t, lengua, autor = autor)
                                .map { if (autor.isNotBlank() && L.otroAlfabeto(it.creador)) it.copy(creador = o.creador) else it }
                            var c = porAutor(o.titulo, idioma())
                            // El título puede estar en el otro idioma (un cajón en español con la app en inglés): si no
                            // aparece con el mismo título y con portada, se busca también en el otro.
                            if (c.none { libroIdeal(o, it) }) c = c + porAutor(o.titulo, if (idioma() == "en") "es" else "en")
                            // Con el título completo puede no salir nada ("Respuesta a la pregunta: ¿Qué es Ilustración?"):
                            // también con cada parte, antes y después de los dos puntos.
                            for (parte in L.fragmentos(o.titulo).drop(1)) {
                                if (c.any { L.esLaMisma(o, it) }) break
                                c = c + porAutor(parte, idioma())
                            }
                            // El autor escrito distinto ("W. R. Burnett" y "William Riley Burnett"): sin filtrar por autor;
                            // la comparación pide igual un apellido en común.
                            if (c.none { L.esLaMisma(o, it) }) c = c + OpenLibrary.buscar(o.titulo, idioma())
                            c
                        }
                        else {
                            val t = Tmdb(clave!!)
                            // Un "anime" puede no ser japonés (TMDB solo los marca así): también se busca como serie y película.
                            val tipos = if (o.tipo == Tipos.ANIME) listOf(Tipos.ANIME, Tipos.SERIE, Tipos.PELICULA) else listOf(o.tipo)
                            val c = tipos.flatMap { t.buscar(o.titulo, it, idioma()) }
                            // Si se escribió con el título de España o en inglés ("Spirited Away", cuyo original está en
                            // japonés), en latino puede no aparecer igual: se busca también con esos (el año igual manda).
                            var todos = c
                            for (otra in if (idioma() == "en") listOf(Tmdb.LATINO) else listOf(Tmdb.ESPANA, Tmdb.INGLES)) {
                                if (todos.any { L.esLaMisma(o, it) }) break
                                todos = todos + tipos.flatMap { t.buscar(o.titulo, it, idioma(), otra) }
                            }
                            // TMDB no encuentra nada si el título trae palabras que no están en ninguno de los suyos ("La La
                            // Land: ciudad de sueños"): también con cada parte ("La La Land"). El año igual sigue mandando.
                            for (parte in L.fragmentos(o.titulo).drop(1)) {
                                if (todos.any { L.esLaMisma(o, it) }) break
                                todos = todos + tipos.flatMap { t.buscar(parte, it, idioma()) }
                            }
                            // Un título común ("La noche", 1961): solo los de ese año.
                            if (o.anio != null && todos.none { L.esLaMisma(o, it) }) todos = todos + tipos.flatMap { t.buscar(o.titulo, it, idioma(), anio = o.anio) }
                            // Si ninguno tiene el mismo título, año y tipo: los títulos alternativos de los de ese año (romaji,
                            // inglés: "Owari no Seraph", "Blue Period"), hasta 4.
                            fun ideal(x: Encontrado) = L.esLaMisma(o, x) && L.mismoTitulo(o, x) && x.anio == o.anio && x.tipo == o.tipo
                            if (todos.none { ideal(it) } && o.anio != null) {
                                val revisar = todos.filter { it.anio != null && kotlin.math.abs(it.anio - o.anio) <= 1 }.map { it.fuente }.distinct().take(4)
                                val alias = revisar.associateWith { runCatching { t.titulosAlternativos(it) }.getOrDefault(emptyList()) }
                                todos = todos.map { x -> alias[x.fuente]?.let { x.copy(alias = it) } ?: x }
                            }
                            todos
                        }
                    }.getOrElse { if (fallo(it)) { _completando.value = Completando(i + 1, total, encontradas); return@forEachIndexed }; emptyList() }
                    fallos = 0
                    // En libros, Open Library tiene obras repetidas de una sola edición y sin portada: primero la del mismo
                    // título y con portada (el orden de la búsqueda ya pone arriba la de más ediciones).
                    val r = candidatos.filter { L.esLaMisma(o, it) && L.porFuente(_estado.value!!, it.fuente) == null }
                        .let { l -> if (o.tipo == Tipos.LIBRO) l.sortedByDescending { (if (libroIdeal(o, it)) 2 else 0) + (if (it.portada.isNotBlank()) 1 else 0) }
                            else l.sortedByDescending { L.puntaje(o, it) } }
                        .firstOrNull()
                    revisadas += o.id
                    if ((i + 1) % 10 == 0) caja.portadasRevisadas = revisadas
                    if (r != null) {
                        encontradas++
                        val portada = if (o.tipo == Tipos.ALBUM) MusicBrainz.caratula(r.fuente.removePrefix("mb:rg:"), 500) else r.portada
                        cambiarObra(o.id) { it.copy(fuente = r.fuente, portada = portada, publica = r.publica ?: it.publica, votos = if (r.publica != null) r.votos else it.votos) }
                        // Y el resto de los datos de afuera (sinopsis, páginas, temporadas, canciones…), sin tocar lo de la persona.
                        actualizarDatos(o.id, callado = true)
                    }
                    _completando.value = Completando(i + 1, total, encontradas)
                }
                artistas.forEachIndexed { i, a ->
                    val r = runCatching { MusicBrainz.buscarArtistas(a.nombre) }
                        .getOrElse { if (fallo(it)) { _completando.value = Completando(pendientes.size + i + 1, total, encontradas); return@forEachIndexed }; emptyList() }
                        .also { fallos = 0 }
                        .firstOrNull { L.mismoArtista(a.nombre, it) && _estado.value!!.artistasVivos.none { x -> x.fuente == it.fuente } }
                    revisadas += a.id
                    if (r != null) {
                        encontradas++
                        cambiarArtista(a.id) { it.copy(fuente = r.fuente, tipo = it.tipo.ifBlank { r.tipo }, pais = it.pais.ifBlank { r.pais }, desde = it.desde ?: r.desde) }
                    }
                    _completando.value = Completando(pendientes.size + i + 1, total, encontradas)
                }
                personas.forEachIndexed { i, a ->
                    val base = pendientes.size + artistas.size
                    val nombre = a.nombre.trimEnd('.')
                    val r = runCatching {
                        if (a.tipo == Personas.AUTOR) {
                            // Primero por Wikidata (da el registro principal de Open Library, con foto y obras); si no,
                            // en Open Library el de más obras con ese nombre o uno de sus otros nombres.
                            Wikimedia.escritorPorNombre(nombre, idioma())?.let { listOf(ArtistaEncontrado("", nombre, "", null, Personas.AUTOR, "", fuente = "ol:author:$it")) }
                                ?: OpenLibrary.buscarAutores(nombre).filter { L.mismoArtista(nombre, it) }
                        } else {
                            // TMDB tiene a muchos con el nombre en su escritura ("新海誠"): si el primero viene así, la
                            // búsqueda lo encontró por su nombre en letras latinas.
                            val l = Tmdb(clave!!).buscarPersonas(a.nombre, idioma())
                            l.filter { L.mismoArtista(nombre, it) }.ifEmpty { l.take(1).filter { L.otroAlfabeto(it.nombre) } }
                        }
                    }.getOrElse { if (fallo(it)) { _completando.value = Completando(base + i + 1, total, encontradas); return@forEachIndexed }; emptyList() }
                        .also { fallos = 0 }
                        .firstOrNull { _estado.value!!.artistasVivos.none { x -> x.fuente == it.fuente } }
                    revisadas += a.id
                    if (r != null) {
                        encontradas++
                        cambiarArtista(a.id) { it.copy(fuente = r.fuente, desde = it.desde ?: r.desde, foto = it.foto.ifBlank { r.foto }) }
                    }
                    _completando.value = Completando(base + i + 1, total, encontradas)
                }
                // Las fotos de los que se enlazaron.
                if (artistas.isNotEmpty() || personas.isNotEmpty()) { fotosPedidas = false; completarFotos() }
            } catch (_: ClaveMala) { // la clave dejó de servir: se avisa al buscar a mano
            } catch (_: SinRed) { // sin internet: sigue la próxima vez
            } finally { caja.portadasRevisadas = revisadas; _completando.value = null }
        }
    }

    /* ----- Diario ----- */

    fun registrar(id: String, r: Registro) {
        val nuevo = if (r.id.isEmpty()) r.copy(id = nuevoId("g")) else r
        cambiarObra(id) { o ->
            val lista = if (o.registros.any { it.id == nuevo.id }) o.registros.map { if (it.id == nuevo.id) nuevo else it } else o.registros + nuevo
            // Registrar algo terminado: la obra queda terminada y, si no tenía nota, toma la del registro.
            var x = o.copy(registros = lista, nota = if (o.nota == 0) nuevo.nota else o.nota)
            if (o.estado != Estados.TERMINADO && (o.tipo == Tipos.PELICULA || o.tipo == Tipos.ALBUM || o.tipo == Tipos.LIBRO))
                x = L.cambiarEstado(x, Estados.TERMINADO, hoy()).copy(terminada = nuevo.fecha)
            x
        }
        cerrar()
        avisar(tx(R.string.aviso_registrado))
    }

    fun borrarRegistro(id: String, registro: String) = cambiarObra(id) { o -> o.copy(registros = o.registros.filter { it.id != registro }) }

    /* ----- Artistas ----- */

    fun guardarArtista(a: ArtistaEncontrado): String {
        val e = _estado.value ?: return ""
        val fuente = a.fuente
        e.artistasVivos.firstOrNull { it.fuente == fuente }?.let { reemplazar(Pantalla.VerArtista(it.id)); return it.id }
        val id = nuevoId("a")
        cambiar { s -> s.copy(artistas = s.artistas + Artista(id, a.nombre, fuente, a.pais, a.desde, a.tipo, foto = a.foto, cambio = ahora())) }
        reemplazar(Pantalla.VerArtista(id))
        return id
    }

    fun nuevoArtista(nombre: String) {
        val id = nuevoId("a")
        cambiar { s -> s.copy(artistas = s.artistas + Artista(id, nombre.trim().take(80), cambio = ahora())) }
        abrir(Pantalla.VerArtista(id))
    }

    fun cambiarArtista(id: String, f: (Artista) -> Artista) = cambiar { e ->
        e.copy(artistas = e.artistas.map { if (it.id == id) f(it).copy(cambio = ahora()) else it })
    }

    fun borrarArtista(id: String) {
        cambiarArtista(id) { it.copy(borrada = true) }
        cambiar { e -> e.copy(obras = e.obras.map { if (it.artista == id) it.copy(artista = null, cambio = ahora()) else it }) }
        cerrar()
    }

    private var fotosPedidas = false

    /** Busca las fotos de los artistas (MusicBrainz) y escritores (Open Library) que todavía no tienen (una vez por sesión). */
    fun completarFotos() {
        if (fotosPedidas) return
        // Las que faltan revisar: sin foto, o con una que llegó sin revisar (de un respaldo o de una versión anterior,
        // que podía ser una carátula). Las que subió la persona no se tocan.
        val faltan = _estado.value?.artistasVivos?.filter { !it.fotoPropia && !it.fotoRevisada && it.fuente.isNotEmpty() } ?: return
        if (faltan.isEmpty()) return
        fotosPedidas = true
        val clave = claveCine
        viewModelScope.launch {
            fun id(a: Artista) = a.fuente.substringAfterLast(":")
            suspend fun porTandas(ids: List<String>, f: suspend (List<String>) -> Map<String, String>) =
                ids.chunked(50).fold(emptyMap<String, String>()) { m, t -> m + runCatching { f(t) }.getOrDefault(emptyMap()) }
            // Cada uno con su propiedad de Wikidata: músicos (MusicBrainz, P434), escritores (Open Library, P648) y
            // directores (TMDB, P4985).
            val grupos = listOf("mb:artist:" to "P434", "ol:author:" to "P648", "tmdb:person:" to "P4985")
            try {
                for ((prefijo, propiedad) in grupos) {
                    val del = faltan.filter { it.fuente.startsWith(prefijo) }
                    if (del.isEmpty()) continue
                    val ids = del.map { id(it) }
                    val deWikidata = porTandas(ids) { Wikimedia.fotos(it, propiedad) }
                    val dePedia = porTandas(ids) { Wikimedia.fotosDeWikipedia(it, idioma(), propiedad) }
                    // De a 4 a la vez: bajar y revisar cada foto toma un momento.
                    del.chunked(4).forEach { tanda ->
                        tanda.map { a ->
                            async {
                                val x = id(a)
                                // Los retratos de TMDB ya son de la persona: se aceptan sin revisar.
                                suspend fun deFuente(): String? = runCatching {
                                    when (prefijo) {
                                        "ol:author:" -> OpenLibrary.autor(x).foto
                                        "tmdb:person:" -> clave?.let { Tmdb(it).persona(x, idioma()).foto }
                                        else -> null
                                    }
                                }.getOrNull()?.takeIf { it.isNotBlank() }
                                val candidatas = listOfNotNull(a.foto.takeIf { it.isNotBlank() }, deWikidata[x], dePedia[x]).distinct()
                                var elegida: String? = candidatas.firstOrNull { "image.tmdb.org" in it } ?: candidatas.firstOrNull { Fotos.buena(it) }
                                if (elegida == null) elegida = deFuente()?.takeIf { "image.tmdb.org" in it || Fotos.buena(it) }
                                a.id to elegida.orEmpty()
                            }
                        }.awaitAll().let { hechas ->
                            val m = hechas.toMap()
                            cambiar { e -> e.copy(artistas = e.artistas.map { a ->
                                m[a.id]?.takeIf { !a.fotoPropia }?.let { f -> a.copy(foto = f, fotoCredito = if (f == a.foto) a.fotoCredito else "", fotoRevisada = true, cambio = ahora()) } ?: a
                            }) }
                        }
                    }
                }
            } catch (x: kotlinx.coroutines.CancellationException) { throw x } catch (_: Exception) { fotosPedidas = false }
        }
    }


    /** El crédito (autor y licencia) de la foto de un artista, si todavía no lo tiene. */
    private val biosPedidas = mutableSetOf<String>()

    /** La biografía breve de un artista, escritor o director (ver [bioDeAfuera]), si todavía no la tiene (una vez por sesión). */
    fun completarBio(id: String) {
        val a = _estado.value?.artistasVivos?.firstOrNull { it.id == id } ?: return
        if (propiedadDe(a.fuente) == null) return
        if (a.bio.isNotBlank() || !biosPedidas.add(id)) return
        viewModelScope.launch {
            val (bio, deFuente) = bioDeAfuera(a.fuente)
            val (texto, url) = bio ?: ("" to "")
            cambiarArtista(id) { v ->
                v.copy(bio = v.bio.ifBlank { texto }, bioFuente = v.bioFuente.ifBlank { url }, desde = v.desde ?: deFuente?.desde,
                    pais = v.pais.ifBlank { deFuente?.pais.orEmpty() }, foto = v.foto.ifBlank { deFuente?.foto.orEmpty() })
            }
        }
    }

    /**
     * Tocar el nombre de un director, escritor o artista en una obra: abre su ficha, sin guardar nada.
     * Si ya está en el cajón, abre la suya; si no, lo busca en su fuente (MusicBrainz, Open
     * Library o TMDB) y abre la ficha de la persona encontrada; si no aparece, abre el buscador con su nombre.
     */
    fun abrirPersona(nombre: String, tipoObra: String) {
        val n = nombre.trim().trimEnd('.')
        if (n.isEmpty() || _buscandoPersona.value != null) return
        val tipo = when (tipoObra) { Tipos.LIBRO -> Personas.AUTOR; Tipos.ALBUM -> TIPO_ARTISTA; else -> Personas.DIRECTOR }
        val e = _estado.value ?: return
        val ya = e.artistasVivos.firstOrNull { L.tituloLimpio(it.nombre.trimEnd('.')) == L.tituloLimpio(n) &&
            (if (tipo == TIPO_ARTISTA) Personas.esMusico(it) else it.tipo == tipo) }
        if (ya != null) { abrir(Pantalla.VerArtista(ya.id)); return }
        val clave = claveCine
        _buscandoPersona.value = n
        viewModelScope.launch {
            val encontrada = runCatching {
                when (tipo) {
                    TIPO_ARTISTA -> MusicBrainz.buscarArtistas(n).firstOrNull { L.mismoArtista(n, it) }
                        ?.let { a -> runCatching { Wikimedia.fotos(listOf(a.mbid))[a.mbid] }.getOrNull()?.let { a.copy(foto = it) } ?: a }
                    Personas.AUTOR -> OpenLibrary.buscarAutores(n).firstOrNull { L.mismoArtista(n, it) }
                    // Sin clave de TMDB no se puede: el buscador lo dice.
                    else -> clave?.let { Tmdb(it).buscarPersonas(n, idioma()).firstOrNull { p -> L.mismoArtista(n, p) } }
                }
            }.getOrNull()
            _buscandoPersona.value = null
            // Ya guardado con esa fuente (con otro nombre): su ficha del cajón.
            val guardado = encontrada?.let { x -> _estado.value?.artistasVivos?.firstOrNull { it.fuente == x.fuente } }
            when {
                guardado != null -> abrir(Pantalla.VerArtista(guardado.id))
                encontrada != null -> abrir(Pantalla.PreviaArtista(encontrada))
                else -> { abrir(Pantalla.Buscar(Secciones.de(tipoObra))); buscar(n) }
            }
        }
    }

    /** El nombre de la persona que se está buscando al tocarla en una obra (para mostrar "Buscando…"), o null. */
    private val _buscandoPersona = MutableStateFlow<String?>(null)
    val buscandoPersona: StateFlow<String?> = _buscandoPersona

    /** Lo que se trae de una persona antes de guardarla: foto (con su crédito), biografía, año y país. */
    data class PersonaPrevia(val foto: String = "", val credito: String = "", val bio: String = "", val bioFuente: String = "",
        val desde: Int? = null, val pais: String = "", val listo: Boolean = false)

    private val _personaPrevia = MutableStateFlow<Pair<String, PersonaPrevia>?>(null)
    /** La foto, la biografía y los datos de la persona que se ve sin guardar (por su fuente). */
    val personaPrevia: StateFlow<Pair<String, PersonaPrevia>?> = _personaPrevia

    /** Trae, sin guardar nada, la foto, la biografía y los datos de una persona encontrada (una vez por persona). */
    fun cargarPersonaPrevia(a: ArtistaEncontrado) {
        if (_personaPrevia.value?.first == a.fuente) return
        _personaPrevia.value = a.fuente to PersonaPrevia(foto = a.foto)
        viewModelScope.launch {
            val propiedad = propiedadDe(a.fuente)
            val x = a.fuente.substringAfterLast(":")
            val (bio, deFuente) = if (propiedad == null) null to null else bioDeAfuera(a.fuente)
            val foto = a.foto.ifBlank {
                propiedad?.let { runCatching { Wikimedia.fotos(listOf(x), it)[x] }.getOrNull() }
                    ?: deFuente?.foto?.takeIf { "image.tmdb.org" in it || Fotos.buena(it) }.orEmpty()
            }
            val credito = if ("wikimedia.org" in foto) runCatching { Wikimedia.credito(foto) }.getOrDefault("") else ""
            if (_personaPrevia.value?.first == a.fuente) _personaPrevia.value = a.fuente to PersonaPrevia(foto, credito,
                bio?.first.orEmpty(), bio?.second.orEmpty(), a.desde ?: deFuente?.desde, a.pais.ifBlank { deFuente?.pais.orEmpty() }, listo = true)
        }
    }

    /** La propiedad de Wikidata de cada fuente de personas: MusicBrainz P434, Open Library P648, TMDB P4985. */
    private fun propiedadDe(fuente: String) = when {
        fuente.startsWith("mb:artist:") -> "P434"
        fuente.startsWith("ol:author:") -> "P648"
        fuente.startsWith("tmdb:person:") -> "P4985"
        else -> null
    }

    /**
     * La biografía de una persona (texto y dirección de donde salió): primero Wikipedia (por Wikidata); si no tiene
     * artículo, la de su fuente (TMDB u Open Library), que además trae el año y el lugar de nacimiento.
     */
    private suspend fun bioDeAfuera(fuente: String): Pair<Pair<String, String>?, PersonaDeAfuera?> {
        val propiedad = propiedadDe(fuente) ?: return null to null
        val x = fuente.substringAfterLast(":")
        val clave = claveCine
        val dePedia = runCatching { Wikimedia.bio(x, idioma(), propiedad) }.getOrNull()
        val deFuente = if (propiedad == "P434") null else runCatching {
            if (propiedad == "P648") OpenLibrary.autor(x) else clave?.let { Tmdb(it).persona(x, idioma()) }
        }.getOrNull()
        return (dePedia ?: deFuente?.takeIf { it.bio.isNotBlank() }?.let { it.bio to it.bioFuente }) to deFuente
    }

    /** Abre el buscador de música con un género (al tocar el género de un artista; el de una obra va a su sección). */
    fun buscarGenero(g: String) = buscarEtiqueta(g, Secciones.MUSICA)

    /** Abre el buscador en la [seccion] con una etiqueta (un género o una etiqueta propia): primero sale lo del cajón que la lleva. */
    fun buscarEtiqueta(t: String, seccion: String) {
        abrir(Pantalla.Buscar(seccion))
        buscar(t)
    }

    fun ponerGenerosObra(id: String, lista: List<String>) = cambiarObra(id) { it.copy(generos = limpiarEtiquetas(lista)) }
    fun ponerEtiquetasObra(id: String, lista: List<String>) = cambiarObra(id) { it.copy(etiquetas = limpiarEtiquetas(lista)) }
    private fun limpiarEtiquetas(l: List<String>) = l.map { it.trim().take(30) }.filter { it.isNotEmpty() }.distinctBy { L.tituloLimpio(it) }.take(20)

    /** Sugerencias para el editor de etiquetas: las del cajón (del mismo apartado primero) y los géneros comunes. */
    fun sugerenciasEtiquetas(tipos: Collection<String>?): List<String> {
        val e = _estado.value ?: return emptyList()
        val musica = tipos == null || Tipos.ALBUM in tipos
        return (L.etiquetasDelCajon(e, tipos) + L.etiquetasDelCajon(e) + (if (musica) L.GENEROS_COMUNES.keys.toList() else GENEROS_CINE_LIBROS))
            .distinctBy { L.tituloLimpio(it) }
    }

    /* ----- Lo que se escucha (cualquier app, Spotify o su historial) ----- */

    private val escuchas get() = (getApplication<android.app.Application>() as CajonApp).escuchas
    /** Los álbumes escuchados (ver [RegistroEscuchas]). */
    val oidos: StateFlow<io.github.fogonapps.cajon.datos.Oidos> get() = escuchas.oidos

    /** Escuchado hace poco y todavía no en el cajón (ni descartado), lo más reciente primero. */
    fun oidosPendientes(o: io.github.fogonapps.cajon.datos.Oidos) = o.albumes.filter { it.obra == null && !it.descartado }.sortedByDescending { it.ultima }

    /**
     * Une lo escuchado con el cajón: lo que ya está queda marcado (y, con 5 canciones o más, pasa a escuchado); con
     * "agregar solos", lo de 5 canciones distintas o más se agrega como escuchado, con su día en el diario.
     */
    fun procesarOidos() {
        val e = _estado.value ?: return
        val solos = EscuchaMusica.agregarSolos(getApplication())
        val albumes = e.vivas.filter { it.tipo == Tipos.ALBUM }.associateBy { RegistroEscuchas.clave(it.creador, it.titulo) }
        val o = escuchas.oidos.value
        val marcar = mutableMapOf<String, String>()
        for (a in o.albumes.filter { it.obra == null && !it.descartado }) {
            val ya = albumes[a.clave]
            if (ya != null) { marcar[a.clave] = ya.id; if (a.canciones.size >= CANCIONES_ESCUCHADO && ya.estado != Estados.TERMINADO) escuchado(ya.id, a.ultima); continue }
            if (solos && a.canciones.size >= CANCIONES_ESCUCHADO) marcar[a.clave] = agregarAlbum(a, callado = true)
        }
        if (marcar.isNotEmpty()) escuchas.cambiar { x -> x.copy(albumes = x.albumes.map { a -> marcar[a.clave]?.let { a.copy(obra = it) } ?: a }) }
        if (solos && marcar.isNotEmpty()) buscarPortadas()
    }

    private fun escuchado(id: String, cuando: Long) {
        val dia = java.time.Instant.ofEpochMilli(cuando).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        cambiarObra(id) { o -> L.cambiarEstado(o, Estados.TERMINADO, hoy()).let { x ->
            if (x.registros.any { it.fecha == dia }) x else x.copy(registros = x.registros + io.github.fogonapps.cajon.datos.Registro(nuevoId("g"), dia)) } }
    }

    /** Agrega un álbum escuchado al cajón (escuchado si sonaron 5 canciones o más; si no, escuchando). */
    private fun agregarAlbum(a: io.github.fogonapps.cajon.datos.AlbumOido, callado: Boolean = false): String {
        val id = nuevoId("o")
        val dia = java.time.Instant.ofEpochMilli(a.ultima).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        val entero = a.canciones.size >= CANCIONES_ESCUCHADO
        val obra = Obra(id, Tipos.ALBUM, a.album, creador = a.artista, estado = if (entero) Estados.TERMINADO else Estados.EN_CURSO,
            agregada = ahora(), cambio = ahora(), registros = if (entero) listOf(io.github.fogonapps.cajon.datos.Registro(nuevoId("g"), dia)) else emptyList())
        cambiar { e -> e.copy(obras = e.obras + obra) }
        if (!callado) avisar(tx(R.string.aviso_agregado_cajon, a.album))
        return id
    }

    fun agregarOido(clave: String) {
        val a = escuchas.oidos.value.albumes.firstOrNull { it.clave == clave } ?: return
        val id = agregarAlbum(a)
        escuchas.cambiar { x -> x.copy(albumes = x.albumes.map { if (it.clave == clave) it.copy(obra = id) else it }) }
        buscarPortadas()
    }

    fun descartarOido(clave: String) = escuchas.cambiar { x -> x.copy(albumes = x.albumes.map { if (it.clave == clave) it.copy(descartado = true) else it }) }

    fun anotarEscuchas(si: Boolean) { EscuchaMusica.activar(getApplication(), si) }
    fun agregarSolos(si: Boolean) { EscuchaMusica.ponerAgregarSolos(getApplication(), si); if (si) procesarOidos() }
    fun anotarDeApp(paquete: String, si: Boolean) = escuchas.cambiar { x -> x.copy(sinAnotar = if (si) x.sinAnotar - paquete else (x.sinAnotar + paquete).distinct()) }

    /* ----- Spotify (opcional) ----- */

    private val _spotify = MutableStateFlow(caja.spotifyRenovar != null)
    /** Si Spotify está conectado. */
    val spotify: StateFlow<Boolean> = _spotify
    fun clienteSpotify(): String = caja.spotifyCliente.orEmpty()

    /**
     * Cómo va la conexión con Spotify, para la pantalla guiada: null (nada), [SPOTIFY_ESPERANDO], o qué falló para
     * decir qué paso revisar: [SPOTIFY_CANCELADO], [SPOTIFY_TIEMPO] (no volvió: casi siempre la dirección del paso 2),
     * [SPOTIFY_CLIENTE] (el Client ID no sirve), [SPOTIFY_PERMISO] (sin Premium o no es la dueña) o [SPOTIFY_RED].
     */
    private val _spotifyPaso = MutableStateFlow<String?>(null)
    val spotifyPaso: StateFlow<String?> = _spotifyPaso
    private var spotifyEspera: kotlinx.coroutines.Job? = null
    @Volatile private var spotifySocket: java.net.ServerSocket? = null

    /** Deja de esperar a Spotify (la persona volvió sin terminar, o quiere empezar otra vez). */
    fun cancelarSpotify() {
        spotifyEspera?.cancel(); spotifyEspera = null
        runCatching { spotifySocket?.close() }; spotifySocket = null
        _spotifyPaso.value = null
    }

    /** Abre el inicio de sesión de Spotify en el navegador ([abrir]) y espera a que vuelva. */
    fun conectarSpotify(cliente: String, abrir: (String) -> Unit) {
        val id = cliente.trim()
        if (!io.github.fogonapps.cajon.datos.Spotify.FORMA_CLIENTE.matches(id)) { _spotifyPaso.value = SPOTIFY_CLIENTE; return }
        caja.spotifyCliente = id
        _spotifyPaso.value = SPOTIFY_ESPERANDO
        runCatching { spotifySocket?.close() }; spotifyEspera?.cancel()
        spotifyEspera = viewModelScope.launch {
            try {
                val s = io.github.fogonapps.cajon.datos.Spotify(id)
                val inicio = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { s.iniciar() }
                abrir(inicio.url)
                spotifySocket = inicio.socket
                val codigo = s.esperarCodigo(inicio) ?: run { _spotifyPaso.value = SPOTIFY_CANCELADO; return@launch }
                val (acceso, renovar) = s.canjear(inicio, codigo)
                s.probar(acceso)
                caja.spotifyRenovar = renovar; _spotify.value = true
                _spotifyPaso.value = null
                avisar(tx(R.string.aviso_spotify_listo))
                cerrar()
                traerSpotify(todo = true)
            } catch (x: kotlinx.coroutines.CancellationException) { throw x
            } catch (x: Exception) {
                // Si la persona canceló (se cerró la espera), no es un error.
                if (isActive) _spotifyPaso.value = when (x) {
                    is java.net.SocketTimeoutException -> SPOTIFY_TIEMPO
                    is ClaveMala -> SPOTIFY_CLIENTE
                    is io.github.fogonapps.cajon.datos.Spotify.SinPermiso -> SPOTIFY_PERMISO
                    else -> SPOTIFY_RED
                }
            }
        }
    }

    fun desconectarSpotify() { caja.spotifyRenovar = null; _spotify.value = false }

    /**
     * Trae de Spotify lo escuchado hace poco (a "Escuchado hace poco") y, con [todo], también los álbumes guardados
     * (como escuchados) y los artistas que sigue, sin repetir lo que ya está en el cajón.
     */
    fun traerSpotify(todo: Boolean = false) {
        val cliente = caja.spotifyCliente ?: return
        val renovarToken = caja.spotifyRenovar ?: return
        viewModelScope.launch {
            try {
                val s = io.github.fogonapps.cajon.datos.Spotify(cliente)
                val (acceso, nuevo) = s.renovar(renovarToken)
                caja.spotifyRenovar = nuevo
                val recientes = s.recientes(acceso, escuchas.oidos.value.spotifyHasta)
                escuchas.sumar(recientes)
                recientes.maxOfOrNull { it.cuando }?.let { m -> escuchas.cambiar { it.copy(spotifyHasta = maxOf(it.spotifyHasta, m)) } }
                if (todo) {
                    val guardados = s.albumesGuardados(acceso)
                    val seguidos = s.artistasSeguidos(acceso)
                    var nuevos = 0
                    cambiar { e ->
                        val ya = e.vivas.filter { it.tipo == Tipos.ALBUM }.map { RegistroEscuchas.clave(it.creador, it.titulo) }.toMutableSet()
                        val obras = guardados.filter { RegistroEscuchas.clave(it.artista, it.titulo).let { k -> ya.add(k) } }.map {
                            nuevos++
                            Obra(nuevoId("o"), Tipos.ALBUM, it.titulo, anio = it.anio, creador = it.artista, estado = Estados.TERMINADO, agregada = ahora(), cambio = ahora())
                        }
                        val nombres = e.musicosVivos.map { L.tituloLimpio(it.nombre) }.toMutableSet()
                        val artistas = seguidos.filter { nombres.add(L.tituloLimpio(it)) }.map { Artista(nuevoId("a"), it, cambio = ahora()) }
                        nuevos += artistas.size
                        e.copy(obras = e.obras + obras, artistas = e.artistas + artistas)
                    }
                    avisar(tx(R.string.aviso_spotify_traido, nuevos))
                    buscarPortadas()
                }
                procesarOidos()
            } catch (_: ClaveMala) { desconectarSpotify(); avisar(tx(R.string.aviso_spotify_no))
            } catch (x: kotlinx.coroutines.CancellationException) { throw x } catch (_: Exception) { if (todo) avisar(tx(R.string.buscar_sin_red)) }
        }
    }

    /* ----- Recomendaciones (Inicio → Para ti) ----- */

    /** Lo que se le recomienda, con el porqué ("Porque te gustó…"); null mientras se arma. */
    data class Recomendaciones(
        val cine: List<Pair<Encontrado, Rec.Por>> = emptyList(),
        val artistas: List<Pair<ArtistaEncontrado, Rec.Por>> = emptyList(),
        val libros: List<Pair<Encontrado, Rec.Por>> = emptyList(),
        val pendientes: List<Obra> = emptyList(),
    ) { val vacias get() = cine.isEmpty() && artistas.isEmpty() && libros.isEmpty() && pendientes.isEmpty() }

    private val _recomendaciones = MutableStateFlow<Recomendaciones?>(null)
    val recomendaciones: StateFlow<Recomendaciones?> = _recomendaciones
    private var recomendando: Job? = null
    private val historial by lazy { io.github.fogonapps.cajon.datos.HistorialParaTi(getApplication()) }

    /** El perfil de gustos, al día con el cajón, lo dicho y lo escuchado (ver [Rec.perfil]). */
    fun perfil(): io.github.fogonapps.cajon.datos.PerfilGustos = _estado.value?.let { Rec.perfil(it, escuchas.oidos.value) } ?: io.github.fogonapps.cajon.datos.PerfilGustos()

    /**
     * Arma "Para ti" con el perfil de gustos ([Rec.perfil]) y lo elige con [Rec.elegir] (exclusiones, historial,
     * variedad y un lugar para explorar). Semillas: lo que le gustó del cajón y lo que dijo que le gusta. Cine: las
     * recomendaciones de TMDB de sus semillas (con clave); si no tiene semillas, lo que más se ve esta semana según
     * sus géneros. Música: artistas parecidos de ListenBrainz. Libros: de sus autores (Open Library). Pendientes: los
     * suyos más parecidos al perfil. Solo las categorías que eligió.
     */
    fun cargarRecomendaciones(otraVez: Boolean = false) {
        if (recomendando?.isActive == true || (!otraVez && _recomendaciones.value != null)) return
        val e = _estado.value ?: return
        val clave = claveCine
        val perfil = Rec.perfil(e, escuchas.oidos.value)
        val vistos = historial.todo()
        // Lo que ya está (también lo borrado): por fuente y por título o nombre.
        val fuentes = (e.obras.map { it.fuente } + e.artistas.map { it.fuente }).filter { it.isNotEmpty() }.toSet()
        val titulos = e.obras.map { L.tituloLimpio(it.titulo) }.toSet()
        val nombres = e.artistas.map { L.tituloLimpio(it.nombre) }.toSet()
        val cineQuiere = listOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME).filter { perfil.quiere(it) }
        _recomendaciones.value = Recomendaciones(pendientes = Rec.pendientes(e, perfil).filter { perfil.quiere(it.tipo) })
        recomendando = viewModelScope.launch {
            val cine = async {
                if (clave == null || cineQuiere.isEmpty()) emptyList() else runCatching {
                    val t = Tmdb(clave)
                    val junta = Rec.Junta<Encontrado> { it.fuente }
                    val semillas = Rec.semillas(e, cineQuiere, 8).filter { it.first.fuente.startsWith("tmdb:") }.map { Triple(it.first.fuente, it.second, it.first.titulo) } +
                        perfil.semillas.filter { it.fuente.startsWith("tmdb:") }.take(6).map { Triple(it.fuente, 0.7, it.nombre) }
                    semillas.distinctBy { it.first }.forEach { (f, p, titulo) -> runCatching { junta.sumar(t.recomendaciones(f, idioma()), p, titulo) } }
                    // Las parecidas que Fogón escribió para lo que eligió de la lista para empezar (con su póster).
                    curadas(e, cineQuiere.toSet()).forEach { (titulo, lista) ->
                        junta.sumar(lista.take(4).map { x -> if (x.portada.isBlank()) x.copy(portada = runCatching { t.poster(x.fuente) }.getOrDefault("")) else x }, 1.0, titulo)
                    }
                    // Sin semillas (o con pocas), lo que más se ve esta semana, según sus géneros.
                    if (semillas.size < 3) runCatching { junta.sumar(t.tendencias(idioma()), 0.0, "") }
                    val c = junta.candidatos({ "obra:" + it.fuente }, { it.generos }) {
                        it.tipo !in cineQuiere || it.fuente in fuentes || L.tituloLimpio(it.titulo) in titulos || it.portada.isBlank()
                    }
                    Rec.elegir(c, perfil, vistos, 18)
                }.getOrDefault(emptyList())
            }
            val musica = async {
                if (!perfil.quiere(Tipos.ALBUM)) emptyList() else runCatching {
                    val oidos = escuchas.oidos.value
                    val junta = Rec.Junta<ArtistaEncontrado> { it.mbid }
                    val semillas = e.musicosVivos.filter { it.fuente.startsWith("mb:artist:") }.map { Triple(it.fuente, Rec.pesoArtista(it, e, oidos), it.nombre) }
                        .filter { it.second > 0 && perfil.pesoPersona(it.third) >= 0 }.sortedByDescending { it.second }.take(6) +
                        perfil.artistasDichos.filter { it.fuente.startsWith("mb:artist:") }.take(6).map { Triple(it.fuente, 0.7, it.nombre) }
                    semillas.distinctBy { it.first }.take(10).forEach { (f, p, nombre) ->
                        runCatching { junta.sumar(io.github.fogonapps.cajon.datos.ListenBrainz.parecidos(f.removePrefix("mb:artist:")), p, nombre) }
                    }
                    val c = junta.candidatos({ Rec.clavePersona(it.nombre) }, persona = { it.nombre }) {
                        "mb:artist:${it.mbid}" in fuentes || L.tituloLimpio(it.nombre) in nombres || L.tituloLimpio(it.nombre) in perfil.artistasDichos.map { d -> L.tituloLimpio(d.nombre) }
                    }
                    val lista = Rec.elegir(c, perfil, vistos, 15)
                    // Con su foto (Wikidata), como en el buscador.
                    val fotos = runCatching { Wikimedia.fotos(lista.map { it.first.mbid }) }.getOrDefault(emptyMap())
                    lista.map { (a, por) -> (fotos[a.mbid]?.let { a.copy(foto = it) } ?: a) to por }
                }.getOrDefault(emptyList())
            }
            val libros = async {
                if (!perfil.quiere(Tipos.LIBRO)) emptyList() else runCatching {
                    val junta = Rec.Junta<Encontrado> { it.fuente }
                    // Autor → (gusto, id de Open Library si lo tiene). Los libros que le gustaron pesan más; los demás
                    // que guardó (no abandonados), un poco: así salen sus escritores aunque no haya calificado nada.
                    val porAutor = LinkedHashMap<String, Pair<Double, String>>()
                    fun sumarAutor(nombre: String, p: Double, ol: String) {
                        val k = L.tituloLimpio(nombre); if (k.isEmpty() || perfil.pesoPersona(nombre) < 0) return
                        val (q, id) = porAutor[k] ?: (0.0 to ol)
                        porAutor[k] = (q + p) to id.ifEmpty { ol }
                    }
                    e.vivas.filter { it.tipo == Tipos.LIBRO && it.estado != Estados.ABANDONADO && it.nota !in 1..4 }.forEach { o ->
                        val p = maxOf(Rec.peso(o, e.ajustes.favoritas), 0.2)
                        val suyos = e.artistasVivos.filter { it.tipo == Personas.AUTOR && it.fuente.startsWith("ol:author:") && L.tituloLimpio(it.nombre) in L.tituloLimpio(o.creador) }
                        if (suyos.isEmpty()) o.creador.split(",").firstOrNull()?.trim()?.let { sumarAutor(it, p, "") }
                        suyos.forEach { sumarAutor(it.nombre, p, it.fuente.removePrefix("ol:author:")) }
                    }
                    perfil.semillas.filter { it.tipo == Tipos.LIBRO && it.creador.isNotBlank() }.forEach { sumarAutor(it.creador.split(",").first().trim(), 1.0, "") }
                    porAutor.entries.sortedByDescending { it.value.first }.take(5).forEach { (k, v) ->
                        val nombre = e.artistasVivos.firstOrNull { L.tituloLimpio(it.nombre) == k }?.nombre
                            ?: perfil.semillas.firstOrNull { L.tituloLimpio(it.creador.split(",").first()) == k }?.creador?.split(",")?.first()?.trim() ?: k
                        // Sin lo que ya tiene de ese autor, aunque esté en otro idioma u otro registro de Open Library.
                        val suyos = e.obras.filter { it.tipo == Tipos.LIBRO && k in L.tituloLimpio(it.creador) }.map { it.titulo } +
                            perfil.semillas.filter { it.tipo == Tipos.LIBRO }.map { it.nombre }
                        runCatching {
                            val obras = if (v.second.isNotEmpty()) OpenLibrary.obrasDe(v.second, idioma()) else OpenLibrary.obrasDeNombre(nombre, idioma())
                            junta.sumar(obras.filter { !Rec.yaLoTiene(it.titulo, suyos) }.take(10), v.first, nombre)
                        }
                    }
                    // Las parecidas que Fogón escribió para los libros que le gustan (pesan más que "otro del mismo autor").
                    curadas(e, setOf(Tipos.LIBRO)).forEach { (titulo, lista) -> junta.sumar(lista, 1.5, titulo) }
                    // Sin los que solo están en otro alfabeto (Open Library no tiene esa obra en el idioma de la app).
                    val c = junta.candidatos({ "obra:" + it.fuente }, persona = { it.creador }) {
                        it.fuente in fuentes || L.tituloLimpio(it.titulo) in titulos || it.portada.isBlank() || L.otroAlfabeto(it.titulo)
                    }
                    Rec.elegir(c, perfil, vistos, 12)
                }.getOrDefault(emptyList())
            }
            val r = Recomendaciones(cine.await(), musica.await(), libros.await(), Rec.pendientes(e, perfil).filter { perfil.quiere(it.tipo) })
            _recomendaciones.value = r
            historial.mostrado(r.cine.map { "obra:" + it.first.fuente } + r.libros.map { "obra:" + it.first.fuente } + r.artistas.map { Rec.clavePersona(it.first.nombre) })
        }
    }

    /**
     * Para recomendar: por cada obra de la lista para empezar que le gusta (la dijo o la tiene sin nota baja), sus
     * parecidas de los [tipos] pedidos, como resultados de búsqueda (título → parecidas).
     */
    private fun curadas(e: Estado, tipos: Set<String>): List<Pair<String, List<Encontrado>>> {
        val porId = iniciales.associateBy { it.id }
        val dichos = e.gustosVivos.filter { it.senal == io.github.fogonapps.cajon.datos.Senales.GUSTA }.map { it.clave }.toSet()
        val tiene = e.vivas.filter { it.nota == 0 || it.nota >= 7 }.map { it.fuente }.filter { it.isNotEmpty() }.toSet()
        return iniciales.filter { it.tipo in tipos && (claveInicial(it) in dichos || it.fuente in tiene) }.map { o ->
            o.titulo to o.parecidos.mapNotNull { porId[it] }.filter { it.tipo in tipos }.map { p -> Encontrado(p.tipo, p.titulo, p.anio, "", p.imagen, p.fuente) }
        }
    }

    /** Abrió algo de "Para ti": cuenta como interés (ya no baja por repetido). */
    fun abiertaRecomendacion(clave: String) { viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { historial.abierta(clave) } }

    /**
     * Lo que la persona dice de algo ([Senales]); null lo quita. Si es negativo, sale ya de "Para ti" (lo que era
     * eso, o de ese género o artista si lo excluyó).
     */
    fun decirGusto(clave: String, nombre: String, senal: String?, tipo: String = "", imagen: String = "", fuente: String = "",
                   creador: String = "", generos: List<String> = emptyList(), desdeParaTi: Boolean = false) {
        cambiar { e ->
            val viejo = e.gustos.firstOrNull { it.clave == clave }
            val nuevo = if (senal == null) viejo?.copy(borrada = true, cambio = ahora())
                else Gusto(clave, nombre.take(120), senal, tipo, imagen, fuente, creador, generos.take(8), ahora())
            if (nuevo == null) e else e.copy(gustos = e.gustos.filter { it.clave != clave } + nuevo)
        }
        if (senal != null && senal != Senales.GUSTA) {
            if (desdeParaTi) avisar(tx(R.string.gusto_anotado))
            val p = perfil()
            _recomendaciones.value = _recomendaciones.value?.let { r ->
                r.copy(cine = r.cine.filter { (x, _) -> "obra:" + x.fuente != clave && !p.excluido(x.generos, "") },
                    artistas = r.artistas.filter { (a, _) -> Rec.clavePersona(a.nombre) != clave && !p.excluido(emptyList(), a.nombre) },
                    libros = r.libros.filter { (x, _) -> "obra:" + x.fuente != clave && !p.excluido(emptyList(), x.creador) },
                    pendientes = r.pendientes.filter { o -> "obra:" + o.fuente != clave && !p.excluido(o.generos + o.etiquetas, o.creador) })
            }
        }
    }

    /** Las categorías que quiere en "Para ti" (vacío = todas). */
    /** Mueve una pestaña de abajo un lugar ([paso] -1 a la izquierda, 1 a la derecha). */
    fun moverPestana(p: Pestana, paso: Int) = cambiar { e ->
        val l = Pestana.enOrden(e.ajustes.ordenPestanas).toMutableList()
        val i = l.indexOf(p); val j = i + paso
        if (j !in l.indices) e else { l.add(j, l.removeAt(i)); e.copy(ajustes = e.ajustes.copy(ordenPestanas = l.map { it.name }, cambio = ahora())) }
    }

    fun elegirCategorias(tipos: List<String>) = cambiar { e -> e.copy(ajustes = e.ajustes.copy(categorias = tipos.distinct(), cambio = ahora())) }

    /** Terminó (o saltó) "Cuéntame tus gustos": se arma "Para ti" de nuevo con lo que dijo. */
    /** Empieza de cero lo que "Para ti" ya mostró (vuelve a salir lo que descansaba). */
    fun borrarHistorialParaTi() { historial.borrar(); cargarRecomendaciones(otraVez = true) }

    fun gustosListos() {
        cambiar { e -> e.copy(ajustes = e.ajustes.copy(gustosListos = true, cambio = ahora())) }
        cargarRecomendaciones(otraVez = true)
    }

    /** Lo que se ofrece para elegir gustos: lo que más se ve (con clave de TMDB), artistas y libros populares. */
    data class ParaElegir(
        val cine: List<Encontrado> = emptyList(),
        val artistas: List<ArtistaEncontrado> = emptyList(),
        val libros: List<Encontrado> = emptyList(),
        val listo: Boolean = false,
    )

    private val _paraElegir = MutableStateFlow(ParaElegir())
    val paraElegir: StateFlow<ParaElegir> = _paraElegir

    fun cargarParaElegir() {
        if (_paraElegir.value.listo) return
        val clave = claveCine
        viewModelScope.launch {
            val cine = async { if (clave == null) emptyList() else runCatching { Tmdb(clave).tendencias(idioma()) }.getOrDefault(emptyList()).filter { it.portada.isNotBlank() } }
            val artistas = async {
                runCatching {
                    val l = io.github.fogonapps.cajon.datos.ListenBrainz.populares(48)
                    val fotos = runCatching { Wikimedia.fotos(l.map { it.mbid }) }.getOrDefault(emptyMap())
                    l.map { a -> fotos[a.mbid]?.let { a.copy(foto = it) } ?: a }
                }.getOrDefault(emptyList())
            }
            val libros = async { runCatching { OpenLibrary.populares(idioma()) }.getOrDefault(emptyList()).filter { it.portada.isNotBlank() } }
            _paraElegir.value = ParaElegir(cine.await(), artistas.await(), libros.await(), listo = true)
        }
    }

    /* ----- Gustos para empezar ----- */

    /** La lista de obras y artistas conocidos para elegir gustos (ver `ObraInicial`). */
    val iniciales: List<io.github.fogonapps.cajon.datos.ObraInicial> by lazy {
        runCatching { io.github.fogonapps.cajon.datos.Iniciales.leer(getApplication<android.app.Application>().assets.open("datos/gustos.json").bufferedReader().use { it.readText() }) }
            .getOrDefault(emptyList())
    }
    private val _imagenesIniciales = MutableStateFlow<Map<String, String>>(emptyMap())
    /** id → imagen que se trajo aparte: el póster de TMDB (con clave) o la foto del artista (Wikimedia). */
    val imagenesIniciales: StateFlow<Map<String, String>> = _imagenesIniciales
    private val inicialesPedidas = mutableSetOf<String>()

    fun cargarImagenesIniciales(ids: List<String>) {
        val porId = iniciales.associateBy { it.id }
        val faltan = ids.filter { it !in inicialesPedidas }.mapNotNull { porId[it] }.filter { it.imagen.isBlank() }
        if (faltan.isEmpty()) return
        inicialesPedidas += faltan.map { it.id }
        val clave = claveCine
        viewModelScope.launch {
            val artistas = faltan.filter { it.tipo == TIPO_ARTISTA }
            if (artistas.isNotEmpty()) runCatching {
                val fotos = Wikimedia.fotos(artistas.map { it.fuente.removePrefix("mb:artist:") })
                _imagenesIniciales.value = _imagenesIniciales.value + artistas.mapNotNull { a -> fotos[a.fuente.removePrefix("mb:artist:")]?.let { a.id to it } }
            }
            if (clave != null) faltan.filter { it.fuente.startsWith("tmdb:") }.forEach { o ->
                runCatching { Tmdb(clave).poster(o.fuente) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { _imagenesIniciales.value = _imagenesIniciales.value + (o.id to it) }
            }
        }
    }

    /**
     * Lo que eligió de la lista para empezar entra al cajón de una vez (las obras como vistas y con "me gusta", los
     * artistas como favoritos), para que Inicio no arranque vacío y las recomendaciones tengan de dónde salir. Lo que
     * ya estaba no se repite; los datos completos (sinopsis, géneros, fotos) llegan después, solos.
     */
    fun agregarIniciales() {
        val e0 = _estado.value ?: return
        val dichos = e0.gustosVivos.filter { it.senal == io.github.fogonapps.cajon.datos.Senales.GUSTA }.map { it.clave }.toSet()
        val elegidas = iniciales.filter { claveInicial(it) in dichos }
        val obras = elegidas.filter { it.tipo != TIPO_ARTISTA && L.porFuente(e0, it.fuente) == null }.map { o ->
            Obra(nuevoId("o"), o.tipo, o.titulo, anio = o.anio, portada = o.imagen.ifBlank { _imagenesIniciales.value[o.id].orEmpty() }, fuente = o.fuente,
                estado = Estados.TERMINADO, meGusta = true, agregada = ahora(), cambio = ahora())
        }
        val artistas = elegidas.filter { it.tipo == TIPO_ARTISTA && e0.artistasVivos.none { a -> a.fuente == it.fuente || io.github.fogonapps.cajon.datos.normal(a.nombre) == io.github.fogonapps.cajon.datos.normal(it.titulo) } }
            .map { a -> Artista(nuevoId("a"), a.titulo, a.fuente, favorito = true, foto = _imagenesIniciales.value[a.id].orEmpty(), cambio = ahora()) }
        if (obras.isEmpty() && artistas.isEmpty()) return
        cambiar { e -> e.copy(obras = e.obras + obras, artistas = e.artistas + artistas) }
        obras.forEach { actualizarDatos(it.id, callado = true) }
        completarFotos()
    }

    fun claveInicial(o: io.github.fogonapps.cajon.datos.ObraInicial) =
        if (o.tipo == TIPO_ARTISTA) io.github.fogonapps.cajon.datos.Recomendar.clavePersona(o.titulo) else "obra:" + o.fuente

    /** Le gusta (o ya no) una de la lista para empezar: queda en sus gustos, como lo demás que dice. */
    fun gustaInicial(o: io.github.fogonapps.cajon.datos.ObraInicial, si: Boolean) {
        val imagen = o.imagen.ifBlank { _imagenesIniciales.value[o.id].orEmpty() }
        decirGusto(claveInicial(o), o.titulo, if (si) io.github.fogonapps.cajon.datos.Senales.GUSTA else null, o.tipo, imagen, o.fuente)
    }

    /* ----- Lo del momento ----- */

    /**
     * Lo del momento, por sección: [obras] con portada (película, serie, anime, album, libro → lista) y [buscados], lo
     * que más se busca hoy en Wikipedia (película, serie, anime → títulos). Se renueva si tiene más de 6 horas.
     */
    data class DelMomento(
        val obras: Map<String, List<Encontrado>> = emptyMap(),
        val buscados: Map<String, List<io.github.fogonapps.cajon.datos.Buscado>> = emptyMap(),
        val artistas: List<ArtistaEncontrado> = emptyList(),
        val conTvMaze: Boolean = false,
        val cuando: Long = 0,
    )

    private val _momento = MutableStateFlow(DelMomento())
    val momento: StateFlow<DelMomento> = _momento
    private var trayendoMomento = false

    fun cargarMomento() {
        if (trayendoMomento || System.currentTimeMillis() - _momento.value.cuando < 6 * 3600_000L) return
        trayendoMomento = true
        val clave = claveCine
        val l = idioma()
        viewModelScope.launch {
            try {
                fun <T> intento(b: suspend () -> List<T>) = async { runCatching { b() }.getOrDefault(emptyList()) }
                val servidor = async { runCatching { io.github.fogonapps.cajon.datos.ClienteSocial().momento(l) }.getOrNull() }
                val peliculas = intento { if (clave == null) emptyList() else Tmdb(clave).tendenciasDe("movie", l) }
                val series = intento { if (clave == null) io.github.fogonapps.cajon.datos.TvMaze.estrenosDeHoy() else Tmdb(clave).tendenciasDe("tv", l).filter { it.tipo == Tipos.SERIE } }
                val anime = intento { if (clave == null) emptyList() else Tmdb(clave).animeAlAire(l) }
                val discos = intento { io.github.fogonapps.cajon.datos.ListenBrainz.discosDeLaSemana() }
                val libros = intento { OpenLibrary.tendencias(l) }
                val artistas = intento {
                    val a = io.github.fogonapps.cajon.datos.ListenBrainz.artistasDeLaSemana(24)
                    val fotos = runCatching { Wikimedia.fotos(a.map { it.mbid }) }.getOrDefault(emptyMap())
                    a.map { x -> fotos[x.mbid]?.let { x.copy(foto = it) } ?: x }
                }
                val s = servidor.await()
                _momento.value = DelMomento(
                    obras = mapOf(Tipos.PELICULA to peliculas.await(), Tipos.SERIE to series.await(), Tipos.ANIME to anime.await(),
                        Tipos.ALBUM to discos.await(), Tipos.LIBRO to libros.await()).mapValues { (_, v) -> v.filter { it.portada.isNotBlank() } },
                    buscados = if (s == null) emptyMap() else mapOf(Tipos.PELICULA to s.peliculas, Tipos.SERIE to s.series, Tipos.ANIME to s.anime),
                    artistas = artistas.await(), conTvMaze = clave == null, cuando = System.currentTimeMillis(),
                )
            } finally { trayendoMomento = false }
        }
    }

    /** Las plataformas que se ofrecen en lo del momento (como las nombra TMDB) y el país de este teléfono. */
    val plataformasCine = listOf("Netflix", "Amazon Prime Video", "Disney Plus", "Max", "Apple TV+")
    val plataformasAnime = listOf("Crunchyroll", "Netflix")
    private fun region() = java.util.Locale.getDefault().country.takeIf { it.length == 2 } ?: "CO"
    private var idsPlataformas: Map<String, Int>? = null
    private val _enPlataforma = MutableStateFlow<Map<String, List<Encontrado>>>(emptyMap())
    /** "plataforma|tipo" → lo de moda ahí. */
    val enPlataforma: StateFlow<Map<String, List<Encontrado>>> = _enPlataforma
    private val plataformaTraida = HashMap<String, Long>()

    /** Trae lo de moda en una plataforma (con la clave de TMDB), como mucho cada 6 horas. */
    fun cargarPlataforma(nombre: String, tipo: String) {
        val clave = claveCine ?: return
        val k = "$nombre|$tipo"
        if (System.currentTimeMillis() - (plataformaTraida[k] ?: 0) < 6 * 3600_000L) return
        plataformaTraida[k] = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching {
                val t = Tmdb(clave)
                val ids = idsPlataformas ?: t.plataformas(region(), idioma()).also { idsPlataformas = it }
                val id = ids.entries.firstOrNull { it.key.equals(nombre, true) }?.value
                    ?: ids.entries.firstOrNull { it.key.lowercase().startsWith(nombre.lowercase()) }?.value ?: return@runCatching
                val lista = when (tipo) {
                    Tipos.PELICULA -> t.popularEn(id, "movie", false, region(), idioma())
                    Tipos.ANIME -> t.popularEn(id, "tv", true, region(), idioma())
                    else -> t.popularEn(id, "tv", false, region(), idioma()).filter { it.tipo == Tipos.SERIE }
                }.filter { it.portada.isNotBlank() }
                _enPlataforma.value = _enPlataforma.value + (k to lista)
            }.onFailure { plataformaTraida.remove(k) }
        }
    }

    /** Tocar algo de lo del momento: si se puede abrir (TMDB, MusicBrainz, Open Library), su previa; si no, se busca por su nombre. */
    fun abrirDelMomento(r: Encontrado) {
        if (r.fuente.startsWith("tvmaze:") || (r.fuente.startsWith("tmdb:") && claveCine == null)) buscarEn(Secciones.de(r.tipo), r.titulo)
        else abrirEncontrado(r)
    }

    /** Busca [texto] en [seccion] (sin salir del buscador). */
    fun buscarEn(seccion: String, texto: String) { cambiarSeccion(seccion); buscar(texto) }

    /* ----- Enlaces a otros servicios ----- */

    private val _enlaces = MutableStateFlow<Map<String, List<io.github.fogonapps.cajon.datos.Enlace>>>(emptyMap())
    /** Fuente → sus enlaces (Spotify, Wikipedia, IMDb…), pedidos una vez por sesión al abrir la ficha. */
    val enlaces: StateFlow<Map<String, List<io.github.fogonapps.cajon.datos.Enlace>>> = _enlaces
    private val enlacesPedidos = mutableSetOf<String>()

    fun cargarEnlaces(fuente: String) {
        if (fuente.isEmpty() || !enlacesPedidos.add(fuente)) return
        viewModelScope.launch {
            val l = runCatching { Wikimedia.enlaces(fuente, idioma()) }.getOrNull()
            if (l == null) { enlacesPedidos.remove(fuente); return@launch }
            _enlaces.value = _enlaces.value + (fuente to l)
        }
    }

    /* ----- Portada propia ----- */

    private val _candidatas = MutableStateFlow<Pair<String, List<String>?>?>(null)
    /** Portadas para elegir de una obra ("Cambiar portada"): null mientras llegan. */
    val candidatas: StateFlow<Pair<String, List<String>?>?> = _candidatas

    fun cargarCandidatas(id: String, que: String = IMAGEN_PORTADA) {
        val clave = id + "|" + que
        if (_candidatas.value?.first == clave) return
        val e = _estado.value ?: return
        val tmdb = claveCine?.let { Tmdb(it) }
        val actual: String
        val traer: suspend () -> List<String>
        if (que == IMAGEN_FOTO) {
            val a = e.artistasVivos.firstOrNull { it.id == id } ?: return
            actual = a.foto
            val x = a.fuente.substringAfterLast(":")
            // De la persona: Wikidata y su categoría de Commons (libres); de directores, TMDB; de escritores, Open Library.
            traer = {
                when {
                    a.fuente.startsWith("mb:artist:") -> Wikimedia.fotosParaElegir(x, "P434")
                    a.fuente.startsWith("ol:author:") -> Wikimedia.fotosParaElegir(x, "P648") + runCatching { OpenLibrary.fotosDeAutor(x) }.getOrDefault(emptyList())
                    a.fuente.startsWith("tmdb:person:") -> (tmdb?.let { runCatching { it.fotosDePersona(x) }.getOrDefault(emptyList()) } ?: emptyList()) + Wikimedia.fotosParaElegir(x, "P4985")
                    else -> emptyList()
                }
            }
        } else {
            val o = e.vivas.firstOrNull { it.id == id } ?: return
            actual = if (que == IMAGEN_FONDO) o.fondo else o.portada
            traer = {
                when {
                    que == IMAGEN_FONDO -> if (o.fuente.startsWith("tmdb:") && tmdb != null) tmdb.fondos(o.fuente, idioma()) else emptyList()
                    o.fuente.startsWith("ol:") -> OpenLibrary.portadasParaElegir(o.fuente)
                    o.fuente.startsWith("tmdb:") && tmdb != null -> tmdb.posteres(o.fuente, idioma())
                    o.fuente.startsWith("mb:rg:") -> MusicBrainz.caratulasDeAlbum(o.fuente.removePrefix("mb:rg:"))
                    else -> emptyList()
                }
            }
        }
        _candidatas.value = clave to null
        viewModelScope.launch {
            val lista = runCatching { traer() }.getOrDefault(emptyList())
            _candidatas.value = clave to (listOfNotNull(actual.takeIf { it.isNotBlank() }) + lista).distinct()
        }
    }

    fun elegirPortada(id: String, url: String) { elegirImagen(id, IMAGEN_PORTADA, url) }

    /** La imagen elegida queda como propia: al renovar los datos o buscar fotos no se cambia. */
    fun elegirImagen(id: String, que: String, url: String) {
        when (que) {
            IMAGEN_FOTO -> cambiarArtista(id) { it.copy(foto = url, fotoCredito = "", fotoPropia = true, fotoRevisada = true) }
            IMAGEN_FONDO -> cambiarObra(id) { it.copy(fondo = url, fondoPropio = true) }
            else -> cambiarObra(id) { it.copy(portada = url, portadaPropia = true) }
        }
        cerrar()
    }

    /** Una imagen del teléfono como portada, fondo o foto: se copia (reducida a 800 px) a la carpeta de la app. */
    fun subirImagen(id: String, que: String, uri: android.net.Uri) {
        viewModelScope.launch {
            val ruta = runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { copiarImagen(uri, id) } }.getOrNull()
            if (ruta == null) { avisar(tx(R.string.aviso_imagen_no)); return@launch }
            elegirImagen(id, que, ruta)
        }
    }

    private fun copiarImagen(uri: android.net.Uri, id: String, tope: Int = 800): String {
        val app = getApplication<android.app.Application>()
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("sin imagen")
        // Primero el tamaño, para no cargar entera una foto de 50 megapíxeles.
        val medidas = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        var muestra = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= tope) muestra *= 2
        val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = muestra }) ?: error("no es imagen")
        val escala = minOf(1f, tope.toFloat() / maxOf(original.width, original.height))
        val chica = android.graphics.Bitmap.createScaledBitmap(original, (original.width * escala).toInt().coerceAtLeast(1), (original.height * escala).toInt().coerceAtLeast(1), true)
        val dir = java.io.File(app.filesDir, "portadas").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("$id-") }?.forEach { it.delete() }
        val f = java.io.File(dir, "$id-${System.currentTimeMillis()}.jpg")
        f.outputStream().use { chica.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
        return "file://" + f.absolutePath
    }

    /* ----- Episodios favoritos ----- */

    private val _episodios = MutableStateFlow<Pair<String, List<Pair<Int, String>>?>?>(null)
    /** Los episodios de una temporada ("id:temporada" → número y nombre), para elegir favoritos. */
    val episodios: StateFlow<Pair<String, List<Pair<Int, String>>?>?> = _episodios

    fun cargarEpisodios(id: String, temporada: Int) {
        val o = _estado.value?.vivas?.firstOrNull { it.id == id } ?: return
        val k = "$id:$temporada"
        if (_episodios.value?.first == k) return
        val clave = claveCine
        val cuantos = o.temporadas.firstOrNull { it.numero == temporada }?.episodios ?: 0
        // Sin TMDB, los números de episodio de la temporada (sin nombre).
        if (!o.fuente.startsWith("tmdb:tv:") || clave == null) { _episodios.value = k to (1..cuantos).map { it to "" }; return }
        _episodios.value = k to null
        viewModelScope.launch {
            _episodios.value = k to (runCatching { Tmdb(clave).episodios(o.fuente, temporada, idioma()) }.getOrNull() ?: (1..cuantos).map { it to "" })
        }
    }

    fun episodioFavorito(id: String, temporada: Int, episodio: Int, nombre: String) = cambiarObra(id) { o ->
        val clave = "${temporada}x$episodio"
        val ya = o.episodiosFavoritos.any { it.substringBefore("|") == clave }
        o.copy(episodiosFavoritos = if (ya) o.episodiosFavoritos.filterNot { it.substringBefore("|") == clave }
            else (o.episodiosFavoritos + (if (nombre.isBlank()) clave else "$clave|$nombre")).sortedWith(compareBy({ it.substringBefore("x").toIntOrNull() ?: 0 }, { it.substringAfter("x").substringBefore("|").toIntOrNull() ?: 0 })))
    }

    /* ----- Enlazar un artista a mano ----- */

    private val _candidatosArtista = MutableStateFlow<Pair<String, List<ArtistaEncontrado>?>?>(null)
    val candidatosArtista: StateFlow<Pair<String, List<ArtistaEncontrado>?>?> = _candidatosArtista

    fun buscarCandidatosArtista(id: String) {
        val a = _estado.value?.artistasVivos?.firstOrNull { it.id == id } ?: return
        _candidatosArtista.value = id to null
        viewModelScope.launch {
            val clave = claveCine
            // Músicos en MusicBrainz; escritores en Open Library; directores en TMDB (con clave).
            val r = runCatching {
                when {
                    a.tipo == Personas.AUTOR -> OpenLibrary.buscarAutores(a.nombre.trimEnd('.'))
                    a.tipo == Personas.DIRECTOR -> clave?.let { Tmdb(it).buscarPersonas(a.nombre, idioma()) } ?: emptyList()
                    else -> MusicBrainz.buscarArtistas(a.nombre)
                }
            }.getOrDefault(emptyList())
            val fotos = runCatching {
                when (a.tipo) {
                    Personas.AUTOR -> Wikimedia.fotos(r.take(8).map { it.fuente.substringAfterLast(":") }, "P648")
                    Personas.DIRECTOR -> emptyMap()
                    else -> Wikimedia.fotos(r.take(8).map { it.mbid })
                }
            }.getOrDefault(emptyMap())
            _candidatosArtista.value = id to r.map { x -> (fotos[x.mbid] ?: fotos[x.fuente.substringAfterLast(":")])?.takeIf { x.foto.isBlank() }?.let { x.copy(foto = it) } ?: x }
        }
    }

    fun enlazarArtista(id: String, r: ArtistaEncontrado) {
        cambiarArtista(id) { it.copy(fuente = r.fuente, tipo = it.tipo.ifBlank { r.tipo }, pais = it.pais.ifBlank { r.pais }, desde = it.desde ?: r.desde,
            foto = if (it.fotoPropia) it.foto else r.foto, fotoRevisada = false) }
        _candidatosArtista.value = null
        fotosPedidas = false; completarFotos()
        _discografia.value = null
        completarBio(id)
    }

    /**
     * Los géneros para tocar en el buscador sin escribir (en todas las secciones): los del cajón de esa
     * sección primero y después los comunes de la sección ([cine], [anime], [musica], [libros], en el idioma de la app).
     * En Animes, los del anime antes que los de cine (sin "Animación", que ya lo son todos); en Todo, una mezcla.
     */
    fun generosParaBuscar(seccion: String, cine: List<String>, anime: List<String>, musica: List<String>, libros: List<String>): List<String> {
        val e = _estado.value
        val delCajon = when (seccion) {
            Secciones.MUSICA -> generosDelCajon()
            Secciones.TODO -> e?.let { L.etiquetasDelCajon(it).take(12) }.orEmpty()
            else -> e?.let { L.etiquetasDelCajon(it, Secciones.tipos(seccion)) }.orEmpty()
        }
        val sinAnimacion = cine.filter { Generos.cine(it)?.peliculas != "16" }
        val comunes = when (seccion) {
            Secciones.PELICULAS, Secciones.SERIES -> cine
            Secciones.ANIMES -> anime + sinAnimacion
            Secciones.MUSICA -> musica
            Secciones.LIBROS -> libros
            // Todo: de a uno de cada lista, para que se vean de todo.
            else -> (0 until maxOf(cine.size, musica.size, libros.size)).flatMap { i -> listOfNotNull(cine.getOrNull(i), musica.getOrNull(i), libros.getOrNull(i)) }
        }
        // Sin repetir el mismo género con otro nombre ("Shoujo" y "Shojo", "Terror" y "Horror").
        fun clave(g: String) = when (seccion) {
            Secciones.MUSICA -> null
            Secciones.LIBROS -> Generos.libro(g)?.let { "l:" + it.tema }
            else -> Generos.cine(g)?.let { "c:" + it.nombres.first() }
        } ?: L.tituloLimpio(g)
        return (delCajon + comunes).filter { it.isNotBlank() }.distinctBy { clave(it) }.take(36)
    }

    /** Los géneros de la música del cajón (artistas y álbumes), los más usados primero. */
    fun generosDelCajon(): List<String> {
        val e = _estado.value ?: return emptyList()
        return (e.musicosVivos.flatMap { it.generos } + e.vivas.filter { it.tipo == Tipos.ALBUM }.flatMap { it.generos })
            .filter { it.isNotBlank() }.groupingBy { it.trim() }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }

    fun ponerGeneros(id: String, generos: List<String>) = cambiarArtista(id) { it.copy(generos = generos.map { g -> g.trim() }.filter { g -> g.isNotEmpty() }.distinctBy { g -> L.tituloLimpio(g) }) }

    fun completarCredito(id: String) {
        val a = _estado.value?.artistasVivos?.firstOrNull { it.id == id } ?: return
        // Solo las de Wikimedia Commons llevan crédito propio (las de TMDB van con su aviso en Licencias).
        if (a.foto.isBlank() || a.fotoCredito.isNotBlank() || "wikimedia.org" !in a.foto) return
        viewModelScope.launch {
            runCatching { Wikimedia.credito(a.foto) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { c -> cambiarArtista(id) { it.copy(fotoCredito = c) } }
        }
    }

    /** Trae las obras de una persona (una vez por persona): discos de MusicBrainz, libros de Open Library o lo que dirigió en TMDB. */
    fun cargarDiscografia(fuente: String) {
        if (_discografia.value?.first == fuente) return
        val clave = claveCine
        val traer: (suspend () -> List<Encontrado>) = when {
            fuente.startsWith("mb:artist:") -> ({ MusicBrainz.discografia(fuente.removePrefix("mb:artist:")) })
            fuente.startsWith("ol:author:") -> ({ OpenLibrary.obrasDe(fuente.removePrefix("ol:author:"), idioma()) })
            fuente.startsWith("tmdb:person:") && clave != null -> ({ Tmdb(clave).filmografia(fuente.removePrefix("tmdb:person:"), idioma()) })
            else -> return
        }
        _discografia.value = fuente to null
        viewModelScope.launch {
            try { _discografia.value = fuente to traer() }
            catch (x: Exception) { _discografia.value = ("error:$fuente") to emptyList() }
        }
    }

    /* ----- El perfil ----- */

    private fun cambiarPerfil(f: (io.github.fogonapps.cajon.datos.Perfil) -> io.github.fogonapps.cajon.datos.Perfil) =
        cambiar { e -> e.copy(perfil = f(e.perfil).copy(cambio = ahora())) }

    /** Nombre, usuario, biografía y enlaces (los que no parecen dirección se descartan). */
    fun guardarIdentidad(nombre: String, usuario: String, bio: String, enlaces: List<String>) {
        cambiar { e -> e.copy(ajustes = e.ajustes.copy(nombre = L.recortar(nombre.trim(), 40), cambio = ahora())) }
        cambiarPerfil { it.copy(usuario = L.usuarioLimpio(usuario), bio = bio.trim().take(200),
            enlaces = enlaces.mapNotNull { x -> L.enlaceLimpio(x) }.distinct().take(5)) }
        avisar(tx(R.string.perfil_guardado))
        cerrar()
    }

    /** La foto o la portada del perfil desde el teléfono (se quedan en este teléfono). */
    fun subirImagenPerfil(portada: Boolean, uri: android.net.Uri) {
        viewModelScope.launch {
            val ruta = runCatching {
                withContext(kotlinx.coroutines.Dispatchers.IO) { copiarImagen(uri, if (portada) "perfil-portada" else "perfil-foto", if (portada) 1400 else 800) }
            }.getOrNull()
            if (ruta == null) { avisar(tx(R.string.aviso_imagen_no)); return@launch }
            cambiarPerfil { if (portada) it.copy(portada = ruta, encuadrePortada = Encuadre()) else it.copy(foto = ruta, encuadreFoto = Encuadre()) }
        }
    }

    /** La portada elegida del cajón (el fondo de una película o serie): una dirección, viaja con la sincronización. */
    fun ponerPortadaPerfil(url: String) { cambiarPerfil { it.copy(portada = url, encuadrePortada = Encuadre()) }; cerrar() }

    fun quitarImagenPerfil(portada: Boolean) = cambiarPerfil { if (portada) it.copy(portada = "", encuadrePortada = Encuadre()) else it.copy(foto = "", encuadreFoto = Encuadre()) }

    /** Pone (o quita, con null) lo que va en un hueco de destacados, con unas palabras si es "me marcó" o "descubrimiento". */
    fun ponerDestacado(hueco: String, id: String?, porQue: String? = null) = cambiarPerfil { p ->
        p.copy(destacados = if (id == null) p.destacados - hueco else p.destacados + (hueco to id),
            porQue = when { id == null -> p.porQue - hueco; porQue != null -> if (porQue.isBlank()) p.porQue - hueco else p.porQue + (hueco to porQue.trim().take(280)); else -> p.porQue })
    }

    /** El tamaño y la posición de la foto (o de la portada) en su marco; guarda y vuelve a editar el perfil. */
    fun encuadrePerfil(portada: Boolean, en: Encuadre) {
        val x = en.limpio()
        cambiarPerfil { if (portada) it.copy(encuadrePortada = x) else it.copy(encuadreFoto = x) }
        cerrar()
    }

    fun visibilidadPerfil(parte: String, v: String) = cambiarPerfil { it.copy(visibilidad = it.visibilidad + (parte to v)) }

    fun alternarCifraPerfil(c: String) = cambiarPerfil { p ->
        val actuales = p.cifras ?: io.github.fogonapps.cajon.datos.PartesPerfil.CIFRAS_DE_SIEMPRE
        p.copy(cifras = if (c in actuales) actuales - c else io.github.fogonapps.cajon.datos.PartesPerfil.CIFRAS_TODAS.filter { it in actuales || it == c })
    }

    /* ----- Listas ----- */

    /** La portada de una lista (una de sus obras) y quién la podría ver. */
    fun portadaLista(id: String, obra: String) = cambiar { e -> e.copy(listas = e.listas.map { if (it.id == id) it.copy(portada = obra, cambio = ahora()) else it }) }
    fun visibilidadLista(id: String, v: String) = cambiar { e -> e.copy(listas = e.listas.map { if (it.id == id) it.copy(visibilidad = v, cambio = ahora()) else it }) }

    fun guardarLista(id: String?, nombre: String, descripcion: String, numerada: Boolean) {
        val n = nombre.trim().take(60); if (n.isEmpty()) return
        if (id == null) {
            val nueva = nuevoId("l")
            cambiar { e -> e.copy(listas = e.listas + Lista(nueva, n, descripcion.trim().take(500), numerada = numerada, cambio = ahora())) }
            reemplazar(Pantalla.VerLista(nueva))
        } else {
            cambiar { e -> e.copy(listas = e.listas.map { if (it.id == id) it.copy(nombre = n, descripcion = descripcion.trim().take(500), numerada = numerada, cambio = ahora()) else it }) }
            cerrar()
        }
    }

    fun alternarEnLista(lista: String, obra: String) = cambiar { e ->
        e.copy(listas = e.listas.map { l -> if (l.id != lista) l else l.copy(obras = if (obra in l.obras) l.obras - obra else l.obras + obra, cambio = ahora()) })
    }

    fun moverEnLista(lista: String, desde: Int, hacia: Int) = cambiar { e ->
        e.copy(listas = e.listas.map { l ->
            if (l.id != lista || desde !in l.obras.indices || hacia !in l.obras.indices) l
            else l.copy(obras = l.obras.toMutableList().apply { add(hacia, removeAt(desde)) }, cambio = ahora())
        })
    }

    fun borrarLista(id: String) {
        cambiar { e -> e.copy(listas = e.listas.map { if (it.id == id) it.copy(borrada = true, cambio = ahora()) else it }) }
        _pila.value = _pila.value.filterNot { it is Pantalla.VerLista && it.id == id || it is Pantalla.EditarLista && it.id == id }
    }

    /* ----- TMDB ----- */


    /* ----- Sincronización con el Gist ----- */

    fun conectar(token: String) {
        val t = token.trim()
        if (t.length < 20) { avisar(tx(R.string.aviso_token_corto)); return }
        avisar(tx(R.string.aviso_conectando))
        viewModelScope.launch {
            try {
                val u = ClienteGist(t).usuario()
                caja.tokenGithub = t; caja.usuario = u
                _sync.value = Sync(EstadoSync.EN_NUBE, usuario = u)
                sincronizar()
            } catch (e: ErrorGitHub) {
                avisar(tx(if (e.codigo == 401) R.string.aviso_token_invalido else R.string.aviso_sin_github))
            } catch (e: Exception) {
                avisar(tx(R.string.aviso_sin_github))
            }
        }
    }

    fun desconectar() {
        caja.desconectarGithub()
        _sync.value = Sync(EstadoSync.APAGADA)
    }

    private var sincronizando = false
    private var sincronizarOtraVez = false

    /** Baja lo del Gist, lo une con lo de aquí y sube el resultado. */
    /**
     * El cajón guardado en la cuenta: trae el que está en el servidor (cifrado), lo abre con la llave de este teléfono,
     * lo une con lo de aquí y sube el resultado cifrado. Si otro teléfono subió algo mientras tanto, vuelve a empezar.
     */
    fun sincronizar() {
        val token = caja.tokenSocial ?: return
        val llave = caja.llaveCajon ?: return
        if (sincronizando) { sincronizarOtraVez = true; return }
        sincronizando = true
        _sync.value = _sync.value.copy(estado = EstadoSync.SINCRONIZANDO, mensaje = null)
        viewModelScope.launch {
            try {
                val c = io.github.fogonapps.cajon.datos.ClienteSocial(token)
                val guardado = c.cajon()
                val remoto = guardado.sobre.takeIf { it.isNotBlank() }?.let { s ->
                    withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { JsonCajon.decodeFromString(Estado.serializer(), io.github.fogonapps.cajon.datos.Nube.abrir(s, llave)) }.getOrNull() }
                }
                if (remoto != null) {
                    // Lo escuchado del otro equipo va a su archivo; el resto se une con el cajón.
                    escuchas.unir(remoto.escuchas)
                    val unido = L.normalizar(L.unir(_estado.value ?: return@launch, remoto.copy(escuchas = null)))
                    if (unido != _estado.value) { _estado.value = unido; guardarYa() }
                }
                val sobre = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    io.github.fogonapps.cajon.datos.Nube.cerrar(JsonCajon.encodeToString(Estado.serializer(), paraAfuera(_estado.value!!)), llave)
                }
                c.guardarCajon(sobre, guardado.cambio)
                caja.ultima = ahora()
                _sync.value = Sync(EstadoSync.LISTA, usuario = caja.usuarioSocial, ultima = caja.ultima)
            } catch (e: io.github.fogonapps.cajon.datos.ErrorSocial) {
                // Otro teléfono subió algo mientras tanto: se trae y se une otra vez.
                if (e.error == "cambio") sincronizarOtraVez = true
                else _sync.value = _sync.value.copy(estado = EstadoSync.ERROR, mensaje = tx(R.string.red_error))
            } catch (e: java.io.IOException) {
                _sync.value = _sync.value.copy(estado = EstadoSync.SIN_CONEXION)
            } finally {
                sincronizando = false
                if (sincronizarOtraVez) { sincronizarOtraVez = false; sincronizar() }
            }
        }
    }

    /* ----- Importar de otras apps y respaldo ----- */

    /**
     * Lo que se compartió desde otra app (Netflix, Spotify, Prime, YouTube…): si trae un enlace de Netflix o de un
     * álbum de Spotify, se busca la obra exacta en Wikidata y se abre; si no, se busca por lo que dice (sin enlaces
     * ni el "Mira … en …" que agregan las apps).
     */
    fun recibirCompartido(texto: String) {
        val t = texto.take(2000)
        val netflix = Regex("netflix\\.com/(?:[a-z-]+/)?(?:title|watch)/(\\d+)").find(t)?.groupValues?.get(1)
        val spotify = Regex("open\\.spotify\\.com/(?:intl-[a-z]+/)?album/([A-Za-z0-9]+)").find(t)?.groupValues?.get(1)
        val citado = Regex("[«“\"]([^»”\"]{2,120})[»”\"]").find(t)?.groupValues?.get(1)
        val limpio = (citado ?: t.replace(Regex("https?://\\S+"), " ")
            .replace(Regex("(?i)\\b(mira|watch|escucha|listen to|check out|échale un vistazo a|ve)\\b"), " ")
            .replace(Regex("(?i)\\b(en|on)\\s+(netflix|spotify|prime video|disney\\+?|max|hbo max|apple tv\\+?|youtube( music)?|crunchyroll)\\b.*"), " "))
            .replace(Regex("\\s+"), " ").trim().trim('!', '.', ':', '-', '|')
        viewModelScope.launch {
            val exacta = runCatching {
                when {
                    netflix != null -> Wikimedia.deLaPlataforma("P1874", netflix, idioma())
                    spotify != null -> Wikimedia.deLaPlataforma("P2205", spotify, idioma())
                    else -> null
                }
            }.getOrNull()
            if (exacta != null && exacta.fuente.isNotBlank() && (!exacta.fuente.startsWith("tmdb:") || claveCine != null)) abrirEncontrado(exacta)
            else {
                abrir(Pantalla.Buscar(exacta?.let { Secciones.de(it.tipo) } ?: Secciones.TODO))
                buscar(exacta?.titulo ?: limpio)
            }
        }
    }

    /** Un archivo de Letterboxd (ZIP o CSV), de Netflix o de Goodreads (CSV). */
    fun importarDeOtraApp(bytes: ByteArray, nombre: String) {
        // El historial de Spotify: sus escuchas van a "Escuchado hace poco" (y se agregan solas si está activado).
        Importar.spotify(bytes, nombre)?.let { h ->
            if (h.sinAlbum) { avisar(tx(R.string.aviso_spotify_basico)); return }
            escuchas.sumar(h.escuchas)
            val albumes = h.escuchas.map { RegistroEscuchas.clave(it.artista, it.album) }.distinct().size
            avisar(tx(R.string.aviso_spotify_historial, h.escuchas.size, albumes))
            procesarOidos()
            return
        }
        val r = Importar.letterboxd(bytes, nombre, ::nuevoId, ahora()) ?: Importar.netflix(bytes.decodeToString(), ::nuevoId, ahora())
            ?: Importar.goodreads(bytes.decodeToString(), ::nuevoId, ahora())
        if (r == null || r.obras.isEmpty()) { avisar(tx(R.string.aviso_no_es_de_otra_app)); return }
        var nuevas = 0
        cambiar { e -> val (x, n) = Importar.sumar(e, r.obras, ahora()); nuevas = n; x }
        avisar(txPlural(R.plurals.aviso_importadas, r.obras.size, r.obras.size, r.deDonde, nuevas))
        buscarPortadas()
    }

    fun exportarJson(): String = JsonCajon.encodeToString(Estado.serializer(), paraAfuera(_estado.value ?: Estado()))

    /** El estado para sacarlo del teléfono (Gist, respaldo): con lo escuchado, que en el teléfono vive aparte. */
    private fun paraAfuera(e: Estado) = e.copy(escuchas = escuchas.oidos.value.takeIf { it.albumes.isNotEmpty() || it.spotifyHasta > 0 })
    fun leerRespaldo(texto: String): Estado? = leerRespaldoCajon(texto)

    fun importar(e: Estado) {
        // El respaldo se une con lo que hay (nada se pierde) y gana lo más reciente; lo escuchado, a su archivo.
        escuchas.unir(e.escuchas)
        cambiar { L.normalizar(L.unir(it, e.copy(escuchas = null))) }
        _pila.value = emptyList(); _pestana.value = Pestana.INICIO
        avisar(tx(R.string.aviso_importado))
        buscarPortadas()
    }

    companion object {
        /** En la búsqueda, además de los tipos de obra, se pueden buscar artistas. */
        const val TIPO_ARTISTA = "artista"
        const val SPOTIFY_ESPERANDO = "esperando"
        const val SPOTIFY_CANCELADO = "cancelado"
        const val SPOTIFY_TIEMPO = "tiempo"
        const val SPOTIFY_CLIENTE = "cliente"
        const val SPOTIFY_PERMISO = "permiso"
        const val SPOTIFY_RED = "red"
        /** Con 5 canciones distintas de un álbum, cuenta como escuchado. */
        const val CANCIONES_ESCUCHADO = 5
        const val IMAGEN_PORTADA = "portada"
        const val IMAGEN_FONDO = "fondo"
        const val IMAGEN_FOTO = "foto"
        /** Sugerencias de géneros para cine y libros (las de la música están en [Logica.GENEROS_COMUNES]). */
        val GENEROS_CINE_LIBROS = listOf("Drama", "Comedia", "Terror", "Thriller", "Suspenso", "Acción", "Aventura", "Ciencia ficción",
            "Fantasía", "Romance", "Animación", "Documental", "Crimen", "Misterio", "Bélica", "Historia", "Biografía", "Musical", "Familiar",
            "Novela", "Cuento", "Poesía", "Ensayo", "Filosofía", "Psicología", "Realismo mágico", "Distopía", "Clásico", "Juvenil", "Shonen", "Seinen")
        private const val SEIS_MESES = 182L * 24 * 3600 * 1000
        /** Desde cuándo se guarda la nota del público: lo traído antes se renueva una vez al abrirlo. */
        private const val ANTES_DE_NOTAS = 1792990800000L
    }
}
