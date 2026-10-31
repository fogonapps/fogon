package io.github.fogonapps.cajon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fogonapps.cajon.datos.Artista
import io.github.fogonapps.cajon.datos.ArtistaEncontrado
import io.github.fogonapps.cajon.datos.ClaveMala
import io.github.fogonapps.cajon.datos.ClienteGist
import io.github.fogonapps.cajon.datos.Encontrado
import io.github.fogonapps.cajon.datos.ErrorGitHub
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Importar
import io.github.fogonapps.cajon.datos.JsonCajon
import io.github.fogonapps.cajon.datos.Lista
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.cajon.datos.MusicBrainz
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.OpenLibrary
import io.github.fogonapps.cajon.datos.Orden
import io.github.fogonapps.cajon.datos.Registro
import io.github.fogonapps.cajon.datos.SinRed
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.cajon.datos.Tmdb
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

/** Las pantallas que se abren encima de las pestañas. */
sealed interface Pantalla {
    /** Buscar algo para agregar, empezando en la [seccion] (ver [Secciones]). */
    data class Buscar(val seccion: String) : Pantalla
    /** Una obra encontrada afuera, antes de agregarla. */
    data class Previa(val obra: Obra) : Pantalla
    data class Ficha(val id: String) : Pantalla
    /** Elegir otra portada o subir una imagen propia. */
    data class CambiarPortada(val id: String) : Pantalla
    /** Agregar a mano ([id] null, del [tipo]) o corregir los datos de una obra. */
    data class EditarObra(val id: String?, val tipo: String) : Pantalla
    /** Anotar en el diario: una vez que se vio, leyó o escuchó. */
    data class Registrar(val id: String, val registro: String? = null) : Pantalla
    data class VerArtista(val id: String) : Pantalla
    /** Un artista encontrado en MusicBrainz, antes de guardarlo. */
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
    data object ClaveTmdb : Pantalla
    data object AcercaDe : Pantalla
    data object Manifiesto : Pantalla
    data object Apariencia : Pantalla
    data object Licencias : Pantalla
}

enum class Pestana { INICIO, CINE, MUSICA, LIBROS }

/** Los apartados de la app (y de la búsqueda): cine, música y libros. */
object Secciones {
    const val TODO = "todo"
    const val CINE = "cine"
    const val MUSICA = "musica"
    const val LIBROS = "libros"
    val TODAS = listOf(TODO, CINE, MUSICA, LIBROS)
    fun de(tipo: String) = when (tipo) { Tipos.LIBRO -> LIBROS; Tipos.ALBUM -> MUSICA; else -> CINE }
    fun tipos(s: String) = when (s) { CINE -> listOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME); MUSICA -> listOf(Tipos.ALBUM); LIBROS -> listOf(Tipos.LIBRO); else -> Tipos.TODOS }
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

    private val _tieneTmdb = MutableStateFlow(caja.claveTmdb != null)
    fun hayClaveTmdb() = caja.claveTmdb != null
    val tieneTmdb: StateFlow<Boolean> = _tieneTmdb

    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes = _mensajes.receiveAsFlow()

    private var guardado: Job? = null
    private var syncJob: Job? = null
    private var buscando: Job? = null

    /** Lo último que esta pantalla escribió en el disco, para distinguirlo de un cambio de afuera. */
    @Volatile private var ultimoGuardado: Estado? = null

    init {
        viewModelScope.launch {
            _estado.value = L.normalizar(almacen.data.first())
            sincronizar()
            buscarPortadas()
            // Si otra app de Fogón restauró los datos (respaldo de todo Fogón), se toman.
            almacen.data.drop(1).collect { d -> if (d != ultimoGuardado && d != _estado.value) _estado.value = d }
        }
    }

    fun hoy(): LocalDate = LocalDate.now()
    fun idioma(): String = Idioma.actual(getApplication())
    fun tx(id: Int, vararg a: Any): String = Idioma.envolver(getApplication()).getString(id, *a)
    fun txPlural(id: Int, n: Int, vararg a: Any): String = Idioma.envolver(getApplication()).resources.getQuantityString(id, n, *a)
    private fun avisar(m: String) { _mensajes.trySend(m) }
    private fun nuevoId(prefijo: String) = prefijo + System.currentTimeMillis().toString(36) + Random.nextInt(46656).toString(36)
    private fun ahora() = System.currentTimeMillis()

    /** Todo cambio pasa por aquí: memoria, pantalla, 300 ms después el disco y 4 s después el Gist. */
    private fun cambiar(f: (Estado) -> Estado) {
        val e = _estado.value ?: return
        _estado.value = f(e)
        guardado?.cancel()
        guardado = viewModelScope.launch { delay(300); guardarYa() }
        if (caja.tokenGithub != null) { syncJob?.cancel(); syncJob = viewModelScope.launch { delay(4000); sincronizar() } }
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
        if (p is Pantalla.Buscar) { buscando?.cancel(); _busqueda.value = Busqueda(seccion = p.seccion) }
        _pila.value = _pila.value + p
    }
    fun cerrar() { _pila.value = _pila.value.dropLast(1) }
    fun reemplazar(p: Pantalla) { _pila.value = _pila.value.dropLast(1) + p }
    fun irA(p: Pestana) { _pila.value = emptyList(); _pestana.value = p }
    fun atras() { if (_pila.value.isNotEmpty()) cerrar() else _pestana.value = Pestana.INICIO }

    /* ----- Tutorial y perfil ----- */

    fun terminarTutorial(nombre: String) = cambiar { e ->
        e.copy(listo = true, ajustes = e.ajustes.copy(nombre = nombre.trim().take(40), cambio = ahora()))
    }

    fun cambiarNombre(nombre: String) = cambiar { e -> e.copy(ajustes = e.ajustes.copy(nombre = nombre.trim().take(40), cambio = ahora())) }

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
            delay(450) // espera a que la persona deje de escribir
            _busqueda.value = Busqueda(texto = texto, seccion = seccion)
            fun fuente(nombre: String, trabajo: suspend () -> Unit) = launch {
                _busqueda.value = _busqueda.value.copy(cargando = _busqueda.value.cargando + nombre)
                try { trabajo() }
                catch (e: ClaveMala) { _busqueda.value = _busqueda.value.copy(errores = _busqueda.value.errores + (nombre to R.string.buscar_tmdb_mala)) }
                catch (e: SinRed) { _busqueda.value = _busqueda.value.copy(errores = _busqueda.value.errores + (nombre to R.string.buscar_sin_red)) }
                finally { _busqueda.value = _busqueda.value.copy(cargando = _busqueda.value.cargando - nombre) }
            }
            fun poner(r: List<Encontrado>) {
                val grupos = r.groupBy { it.tipo }
                _busqueda.value = _busqueda.value.copy(obras = _busqueda.value.obras + grupos)
            }
            if (seccion == Secciones.TODO || seccion == Secciones.CINE) {
                val clave = caja.claveTmdb
                if (clave == null) _busqueda.value = _busqueda.value.copy(errores = _busqueda.value.errores + ("tmdb" to R.string.buscar_falta_tmdb))
                else fuente("tmdb") {
                    val r = Tmdb(clave).buscar(t, Tipos.PELICULA, idioma()) + Tmdb(clave).buscar(t, Tipos.SERIE, idioma())
                    poner(r)
                    // Los directores, aparte: si fallan, quedan las películas.
                    runCatching { Tmdb(clave).buscarDirectores(t, idioma()) }.getOrNull()?.let { _busqueda.value = _busqueda.value.copy(directores = it) }
                }
            }
            if (seccion == Secciones.TODO || seccion == Secciones.LIBROS) fuente("libros") {
                poner(OpenLibrary.buscar(t, idioma()))
                // Los escritores, con su foto de Wikimedia Commons (por su id de Open Library en Wikidata).
                val autores = runCatching { OpenLibrary.buscarAutores(t) }.getOrDefault(emptyList())
                _busqueda.value = _busqueda.value.copy(autores = autores)
                val fotos = runCatching { Wikimedia.fotos(autores.map { it.fuente.removePrefix("ol:author:") }, "P648") }.getOrDefault(emptyMap())
                if (fotos.isNotEmpty()) _busqueda.value = _busqueda.value.copy(autores = autores.map { a -> fotos[a.fuente.removePrefix("ol:author:")]?.let { a.copy(foto = it) } ?: a })
            }
            // ¿Es un género? Entonces se buscan artistas y álbumes de ese género (como en las apps de música).
            // ¿O una etiqueta del cajón (un género de cine o de libros, o una propia)? Primero sale lo del cajón que la lleva.
            val musical = seccion == Secciones.TODO || seccion == Secciones.MUSICA
            val genero = if (musical) L.genero(t, generosDelCajon()) else null
            val local = genero ?: _estado.value?.let { e -> L.etiquetasDelCajon(e, Secciones.tipos(seccion).takeIf { seccion != Secciones.TODO }).firstOrNull { L.tituloLimpio(it) == L.tituloLimpio(t) } }
            if (local != null) _busqueda.value = _busqueda.value.copy(genero = local)
            if (seccion == Secciones.TODO || seccion == Secciones.MUSICA) fuente("musica") {
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
        L.porFuente(e, r.fuente)?.let { abrir(Pantalla.Ficha(it.id)); return }
        // Se abre ya, con lo que trajo la búsqueda; lo demás (sinopsis, canciones, géneros) llega después y
        // reemplaza la previa si la persona sigue en ella. Así no hay que esperar a MusicBrainz (1 consulta por segundo).
        abrir(Pantalla.Previa(Obra("", r.tipo, r.titulo, anio = r.anio, creador = r.creador, portada = r.portada, fuente = r.fuente,
            publica = r.publica, votos = r.votos, paginas = if (r.tipo == Tipos.LIBRO) r.detalle.removeSuffix(" p.").toIntOrNull() else null,
            formato = if (r.tipo == Tipos.ALBUM) r.detalle else "")))
        _trayendo.value = r.fuente
        viewModelScope.launch {
            try {
                val o = detalles(r)
                if ((_pila.value.lastOrNull() as? Pantalla.Previa)?.obra?.fuente == r.fuente) reemplazar(Pantalla.Previa(o))
            } catch (x: ClaveMala) { avisar(tx(R.string.buscar_tmdb_mala))
            } catch (x: Exception) {
                // Sin internet o algo raro: se puede agregar igual con lo que trajo la búsqueda.
                avisar(tx(R.string.aviso_sin_detalles))
            } finally { if (_trayendo.value == r.fuente) _trayendo.value = null }
        }
    }

    private suspend fun detalles(r: Encontrado): Obra = when {
        r.fuente.startsWith("tmdb:") -> Tmdb(caja.claveTmdb ?: throw ClaveMala()).detalles(r.fuente, r.tipo, idioma())
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
        val o = _estado.value?.vivas?.firstOrNull { it.id == id } ?: return
        if (o.fuente.isEmpty()) return
        viewModelScope.launch {
            try {
                val r = io.github.fogonapps.cajon.datos.Encontrado(o.tipo, o.titulo, o.anio, o.creador, o.portada, o.fuente, o.paginas?.let { "$it p." } ?: o.formato)
                val d = detalles(r)
                cambiarObra(id) { v ->
                    // Las etiquetas (géneros) y la sinopsis que ya tenía se respetan: la persona pudo haberlas ajustado; la
                    // portada también, si la eligió ella.
                    v.copy(titulo = d.titulo, original = d.original, anio = d.anio ?: v.anio, creador = d.creador.ifBlank { v.creador },
                        generos = v.generos.ifEmpty { d.generos }, sinopsis = v.sinopsis.ifBlank { d.sinopsis },
                        portada = if (v.portadaPropia) v.portada else d.portada.ifBlank { v.portada },
                        fondo = d.fondo.ifBlank { v.fondo }, minutos = d.minutos ?: v.minutos, paginas = d.paginas ?: v.paginas,
                        temporadas = d.temporadas.ifEmpty { v.temporadas },
                        canciones = if (d.canciones.isEmpty()) v.canciones else d.canciones.mapIndexed { i, c -> v.canciones.getOrNull(i)?.takeIf { it.titulo == c.titulo }?.let { c.copy(nota = it.nota, favorita = it.favorita) } ?: c },
                        traida = d.traida, publica = d.publica ?: v.publica, votos = if (d.publica != null) d.votos else v.votos)
                }
                if (!callado) avisar(tx(R.string.aviso_datos_al_dia))
            } catch (x: ClaveMala) { if (!callado) avisar(tx(R.string.buscar_tmdb_mala)) } catch (x: Exception) { if (!callado) avisar(tx(R.string.buscar_sin_red)) }
        }
    }

    /** Las obras con datos de TMDB de hace más de 6 meses se renuevan al abrirlas (si hay clave e internet). */
    fun renovarSiHaceFalta(o: Obra) {
        val tmdb = o.fuente.startsWith("tmdb:")
        if (tmdb && caja.claveTmdb == null || o.fuente.isEmpty() || o.traida <= 0) return
        // Lo de TMDB, cada 6 meses; lo agregado antes de la 1.2 (sin nota del público), una vez.
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
        if (trabajoPortadas?.isActive == true) return
        val clave = caja.claveTmdb
        val revisadas = caja.portadasRevisadas.toMutableSet()
        val pendientes = _estado.value?.vivas.orEmpty()
            .filter { it.fuente.isEmpty() && (it.tipo == Tipos.ALBUM || it.tipo == Tipos.LIBRO || clave != null) && it.id !in revisadas }
        // Los artistas guardados sin fuente (a mano o de otra app) se enlazan con MusicBrainz: así traen foto y biografía.
        val artistas = _estado.value?.musicosVivos.orEmpty().filter { it.fuente.isEmpty() && it.id !in revisadas }
        if (pendientes.isEmpty() && artistas.isEmpty()) return
        trabajoPortadas = viewModelScope.launch {
            val total = pendientes.size + artistas.size
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
                        else if (o.tipo == Tipos.LIBRO) OpenLibrary.buscar(o.titulo, idioma(), autor = o.creador.substringBefore(",").trim()).let { c ->
                            // El título puede estar en el otro idioma (un cajón en español con la app en inglés): si no
                            // aparece con el mismo título y con portada, se busca también en el otro.
                            if (c.any { libroIdeal(o, it) }) c else c + OpenLibrary.buscar(o.titulo, if (idioma() == "en") "es" else "en", autor = o.creador.substringBefore(",").trim())
                        }
                        else {
                            val t = Tmdb(clave!!)
                            // Un "anime" puede no ser japonés (TMDB solo los marca así): también se busca como serie y película.
                            val tipos = if (o.tipo == Tipos.ANIME) listOf(Tipos.ANIME, Tipos.SERIE, Tipos.PELICULA) else listOf(o.tipo)
                            tipos.flatMap { t.buscar(o.titulo, it, idioma()) }
                        }
                    }.getOrElse { if (fallo(it)) { _completando.value = Completando(i + 1, total, encontradas); return@forEachIndexed }; emptyList() }
                    fallos = 0
                    // En libros, Open Library tiene obras repetidas de una sola edición y sin portada: primero la del mismo
                    // título y con portada (el orden de la búsqueda ya pone arriba la de más ediciones).
                    val r = candidatos.filter { L.esLaMisma(o, it) && L.porFuente(_estado.value!!, it.fuente) == null }
                        .let { l -> if (o.tipo == Tipos.LIBRO) l.sortedByDescending { (if (libroIdeal(o, it)) 2 else 0) + (if (it.portada.isNotBlank()) 1 else 0) } else l }
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
                        .firstOrNull { L.tituloLimpio(it.nombre) == L.tituloLimpio(a.nombre) && _estado.value!!.artistasVivos.none { x -> x.fuente == it.fuente } }
                    revisadas += a.id
                    if (r != null) {
                        encontradas++
                        cambiarArtista(a.id) { it.copy(fuente = r.fuente, tipo = it.tipo.ifBlank { r.tipo }, pais = it.pais.ifBlank { r.pais }, desde = it.desde ?: r.desde) }
                    }
                    _completando.value = Completando(pendientes.size + i + 1, total, encontradas)
                }
                // Las fotos de los que se enlazaron.
                if (artistas.isNotEmpty()) { fotosPedidas = false; completarFotos() }
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
        val faltan = _estado.value?.artistasVivos?.filter { it.foto.isBlank() && (it.fuente.startsWith("mb:artist:") || it.fuente.startsWith("ol:author:")) } ?: return
        if (faltan.isEmpty()) return
        fotosPedidas = true
        viewModelScope.launch {
            val fotos = runCatching {
                Wikimedia.fotos(faltan.map { it.fuente.removePrefix("mb:artist:") }) +
                    Wikimedia.fotos(faltan.map { it.fuente.removePrefix("ol:author:") }, "P648")
            }.getOrNull() ?: run { fotosPedidas = false; return@launch }
            fun id(a: Artista) = a.fuente.removePrefix("mb:artist:").removePrefix("ol:author:")
            if (fotos.isNotEmpty()) cambiar { e -> e.copy(artistas = e.artistas.map { a ->
                fotos[id(a)]?.takeIf { a.foto.isBlank() }?.let { a.copy(foto = it, cambio = ahora()) } ?: a
            }) }
        }
    }

    /** El crédito (autor y licencia) de la foto de un artista, si todavía no lo tiene. */
    private val biosPedidas = mutableSetOf<String>()

    /** La biografía breve de un artista de MusicBrainz (de Wikipedia), si todavía no la tiene (una vez por sesión). */
    fun completarBio(id: String) {
        val a = _estado.value?.artistasVivos?.firstOrNull { it.id == id } ?: return
        if (a.bio.isNotBlank() || !a.fuente.startsWith("mb:artist:") || !biosPedidas.add(id)) return
        viewModelScope.launch {
            runCatching { Wikimedia.bio(a.fuente.removePrefix("mb:artist:"), idioma()) }.getOrNull()?.let { (texto, url) ->
                cambiarArtista(id) { it.copy(bio = texto, bioFuente = url) }
            }
        }
    }

    /** Abre el buscador de música con un género (al tocar una etiqueta). */
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

    /* ----- Portada propia ----- */

    private val _candidatas = MutableStateFlow<Pair<String, List<String>?>?>(null)
    /** Portadas para elegir de una obra ("Cambiar portada"): null mientras llegan. */
    val candidatas: StateFlow<Pair<String, List<String>?>?> = _candidatas

    fun cargarCandidatas(id: String) {
        val o = _estado.value?.vivas?.firstOrNull { it.id == id } ?: return
        if (_candidatas.value?.first == id) return
        _candidatas.value = id to null
        viewModelScope.launch {
            val lista = runCatching {
                when {
                    o.fuente.startsWith("ol:") -> OpenLibrary.portadasParaElegir(o.fuente)
                    o.fuente.startsWith("tmdb:") && caja.claveTmdb != null -> Tmdb(caja.claveTmdb!!).posteres(o.fuente, idioma())
                    else -> emptyList()
                }
            }.getOrDefault(emptyList())
            _candidatas.value = id to (listOfNotNull(o.portada.takeIf { it.isNotBlank() }) + lista).distinct()
        }
    }

    fun elegirPortada(id: String, url: String) { cambiarObra(id) { it.copy(portada = url, portadaPropia = true) }; cerrar() }

    /** Una imagen del teléfono como portada: se copia (reducida a 800 px) a la carpeta de la app. */
    fun subirPortada(id: String, uri: android.net.Uri) {
        viewModelScope.launch {
            val ruta = runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { copiarImagen(uri, id) } }.getOrNull()
            if (ruta == null) { avisar(tx(R.string.aviso_imagen_no)); return@launch }
            elegirPortada(id, ruta)
        }
    }

    private fun copiarImagen(uri: android.net.Uri, id: String): String {
        val app = getApplication<android.app.Application>()
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("sin imagen")
        // Primero el tamaño, para no cargar entera una foto de 50 megapíxeles.
        val medidas = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        var muestra = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= 800) muestra *= 2
        val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = muestra }) ?: error("no es imagen")
        val escala = minOf(1f, 800f / maxOf(original.width, original.height))
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
        val clave = caja.claveTmdb
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
            val r = runCatching { MusicBrainz.buscarArtistas(a.nombre) }.getOrDefault(emptyList())
            val fotos = runCatching { Wikimedia.fotos(r.take(8).map { it.mbid }) }.getOrDefault(emptyMap())
            _candidatosArtista.value = id to r.map { x -> fotos[x.mbid]?.let { x.copy(foto = it) } ?: x }
        }
    }

    fun enlazarArtista(id: String, r: ArtistaEncontrado) {
        cambiarArtista(id) { it.copy(fuente = r.fuente, tipo = it.tipo.ifBlank { r.tipo }, pais = it.pais.ifBlank { r.pais }, desde = it.desde ?: r.desde, foto = it.foto.ifBlank { r.foto }) }
        _candidatosArtista.value = null
        _discografia.value = null
        completarBio(id)
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
        val clave = caja.claveTmdb
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

    /* ----- Listas ----- */

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

    fun ponerClaveTmdb(clave: String) {
        val c = clave.trim()
        if (c.length < 20) { avisar(tx(R.string.aviso_clave_corta)); return }
        avisar(tx(R.string.aviso_probando_clave))
        viewModelScope.launch {
            try {
                Tmdb(c).probar()
                caja.claveTmdb = c; _tieneTmdb.value = true
                buscarPortadas()   // ahora también las películas, series y animes
                avisar(tx(R.string.aviso_clave_lista))
                cerrar()
                if (_busqueda.value.texto.isNotBlank()) buscar(_busqueda.value.texto)
            } catch (e: ClaveMala) { avisar(tx(R.string.buscar_tmdb_mala)) } catch (e: Exception) { avisar(tx(R.string.buscar_sin_red)) }
        }
    }

    fun quitarClaveTmdb() { caja.claveTmdb = null; _tieneTmdb.value = false }

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
    fun sincronizar() {
        val token = caja.tokenGithub ?: return
        if (sincronizando) { sincronizarOtraVez = true; return }
        sincronizando = true
        _sync.value = _sync.value.copy(estado = EstadoSync.SINCRONIZANDO, mensaje = null)
        viewModelScope.launch {
            try {
                val c = ClienteGist(token)
                val local = _estado.value ?: return@launch
                val gist = caja.gist ?: c.buscarOCrear(JsonCajon.encodeToString(Estado.serializer(), local)).also { caja.gist = it }
                val remoto = c.leer(gist)?.let { runCatching { JsonCajon.decodeFromString(Estado.serializer(), it) }.getOrNull() }
                if (remoto != null) {
                    val unido = L.normalizar(L.unir(_estado.value ?: local, remoto))
                    if (unido != _estado.value) { _estado.value = unido; guardarYa() }
                }
                c.escribir(gist, JsonCajon.encodeToString(Estado.serializer(), _estado.value!!))
                caja.ultima = ahora()
                _sync.value = Sync(EstadoSync.LISTA, usuario = caja.usuario, ultima = caja.ultima)
            } catch (e: ErrorGitHub) {
                if (e.codigo == 404) caja.gist = null
                _sync.value = _sync.value.copy(
                    estado = EstadoSync.ERROR,
                    mensaje = when (e.codigo) {
                        401 -> tx(R.string.sync_token_vencido)
                        403, 404 -> tx(R.string.sync_sin_permiso)
                        else -> tx(R.string.sync_sin_conexion)
                    },
                )
            } catch (e: java.io.IOException) {
                _sync.value = _sync.value.copy(estado = EstadoSync.SIN_CONEXION)
            } finally {
                sincronizando = false
                if (sincronizarOtraVez) { sincronizarOtraVez = false; sincronizar() }
            }
        }
    }

    /* ----- Importar de otras apps y respaldo ----- */

    /** Un archivo de Letterboxd (ZIP o CSV) o de Goodreads (CSV). */
    fun importarDeOtraApp(bytes: ByteArray, nombre: String) {
        val r = Importar.letterboxd(bytes, nombre, ::nuevoId, ahora()) ?: Importar.goodreads(bytes.decodeToString(), ::nuevoId, ahora())
        if (r == null || r.obras.isEmpty()) { avisar(tx(R.string.aviso_no_es_de_otra_app)); return }
        var nuevas = 0
        cambiar { e -> val (x, n) = Importar.sumar(e, r.obras, ahora()); nuevas = n; x }
        avisar(txPlural(R.plurals.aviso_importadas, r.obras.size, r.obras.size, r.deDonde, nuevas))
        buscarPortadas()
    }

    fun exportarJson(): String = JsonCajon.encodeToString(Estado.serializer(), _estado.value ?: Estado())
    fun leerRespaldo(texto: String): Estado? = leerRespaldoCajon(texto)

    fun importar(e: Estado) {
        // El respaldo se une con lo que hay (nada se pierde) y gana lo más reciente.
        cambiar { L.normalizar(L.unir(it, e)) }
        _pila.value = emptyList(); _pestana.value = Pestana.INICIO
        avisar(tx(R.string.aviso_importado))
        buscarPortadas()
    }

    companion object {
        /** En la búsqueda, además de los tipos de obra, se pueden buscar artistas. */
        const val TIPO_ARTISTA = "artista"
        /** Sugerencias de géneros para cine y libros (las de la música están en [Logica.GENEROS_COMUNES]). */
        val GENEROS_CINE_LIBROS = listOf("Drama", "Comedia", "Terror", "Thriller", "Suspenso", "Acción", "Aventura", "Ciencia ficción",
            "Fantasía", "Romance", "Animación", "Documental", "Crimen", "Misterio", "Bélica", "Historia", "Biografía", "Musical", "Familiar",
            "Novela", "Cuento", "Poesía", "Ensayo", "Filosofía", "Psicología", "Realismo mágico", "Distopía", "Clásico", "Juvenil", "Shonen", "Seinen")
        private const val SEIS_MESES = 182L * 24 * 3600 * 1000
        /** Cuándo empezó a guardarse la nota del público (1.2): lo traído antes se renueva una vez al abrirlo. */
        private const val ANTES_DE_NOTAS = 1792990800000L
    }
}
