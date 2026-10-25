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
import io.github.fogonapps.identidad.Idioma
import kotlinx.coroutines.Job
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
    /** Buscar algo para agregar, empezando en [tipo]. */
    data class Buscar(val tipo: String) : Pantalla
    /** Una obra encontrada afuera, antes de agregarla. */
    data class Previa(val obra: Obra) : Pantalla
    data class Ficha(val id: String) : Pantalla
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

enum class Pestana { INICIO, CAJON, LISTAS, TU }

/** Lo que muestra la búsqueda. */
data class Busqueda(
    val texto: String = "",
    val tipo: String = Tipos.PELICULA,
    val cargando: Boolean = false,
    val obras: List<Encontrado> = emptyList(),
    val artistas: List<ArtistaEncontrado> = emptyList(),
    /** Algo salió mal: sin internet, falta la clave de TMDB o la clave no sirve. */
    val error: Int? = null,
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
    private val _discografia = MutableStateFlow<Pair<String, List<Encontrado>>?>(null)
    val discografia: StateFlow<Pair<String, List<Encontrado>>?> = _discografia

    /** Mientras se traen los datos de una obra encontrada (para mostrar que está cargando). */
    private val _trayendo = MutableStateFlow<String?>(null)
    val trayendo: StateFlow<String?> = _trayendo

    private val _sync = MutableStateFlow(Sync(if (caja.tokenGithub != null) EstadoSync.EN_NUBE else EstadoSync.APAGADA, caja.usuario, caja.ultima))
    val sync: StateFlow<Sync> = _sync

    private val _tieneTmdb = MutableStateFlow(caja.claveTmdb != null)
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
        if (p is Pantalla.Buscar) { buscando?.cancel(); _busqueda.value = Busqueda(tipo = p.tipo) }
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

    fun cambiarTipoBusqueda(tipo: String) {
        _busqueda.value = Busqueda(texto = _busqueda.value.texto, tipo = tipo)
        if (_busqueda.value.texto.isNotBlank()) buscar(_busqueda.value.texto)
    }

    fun buscar(texto: String) {
        val t = texto.trim()
        _busqueda.value = _busqueda.value.copy(texto = texto)
        buscando?.cancel()
        if (t.length < 2) { _busqueda.value = _busqueda.value.copy(cargando = false, obras = emptyList(), artistas = emptyList(), error = null); return }
        val tipo = _busqueda.value.tipo
        buscando = viewModelScope.launch {
            delay(450) // espera a que la persona deje de escribir
            _busqueda.value = _busqueda.value.copy(cargando = true, error = null)
            try {
                when (tipo) {
                    Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME -> {
                        val clave = caja.claveTmdb
                        if (clave == null) { _busqueda.value = _busqueda.value.copy(cargando = false, obras = emptyList(), error = R.string.buscar_falta_tmdb); return@launch }
                        _busqueda.value = _busqueda.value.copy(cargando = false, obras = Tmdb(clave).buscar(t, tipo, idioma()))
                    }
                    Tipos.LIBRO -> _busqueda.value = _busqueda.value.copy(cargando = false, obras = OpenLibrary.buscar(t))
                    Tipos.ALBUM -> _busqueda.value = _busqueda.value.copy(cargando = false, obras = MusicBrainz.buscarAlbumes(t))
                    TIPO_ARTISTA -> _busqueda.value = _busqueda.value.copy(cargando = false, artistas = MusicBrainz.buscarArtistas(t))
                }
            } catch (e: ClaveMala) {
                _busqueda.value = _busqueda.value.copy(cargando = false, error = R.string.buscar_tmdb_mala)
            } catch (e: SinRed) {
                _busqueda.value = _busqueda.value.copy(cargando = false, error = R.string.buscar_sin_red)
            }
        }
    }

    /** Trae los datos completos de algo encontrado y lo muestra antes de agregarlo (o abre su ficha si ya está). */
    fun abrirEncontrado(r: Encontrado) {
        val e = _estado.value ?: return
        L.porFuente(e, r.fuente)?.let { abrir(Pantalla.Ficha(it.id)); return }
        if (_trayendo.value != null) return
        _trayendo.value = r.fuente
        viewModelScope.launch {
            try {
                val o = detalles(r)
                abrir(Pantalla.Previa(o))
            } catch (x: ClaveMala) { avisar(tx(R.string.buscar_tmdb_mala))
            } catch (x: Exception) {
                // Sin internet o algo raro: se puede agregar con lo que trajo la búsqueda.
                abrir(Pantalla.Previa(Obra("", r.tipo, r.titulo, anio = r.anio, creador = r.creador, portada = r.portada, fuente = r.fuente)))
                avisar(tx(R.string.aviso_sin_detalles))
            } finally { _trayendo.value = null }
        }
    }

    private suspend fun detalles(r: Encontrado): Obra = when {
        r.fuente.startsWith("tmdb:") -> Tmdb(caja.claveTmdb ?: throw ClaveMala()).detalles(r.fuente, r.tipo, idioma())
        r.fuente.startsWith("ol:") -> OpenLibrary.detalles(r)
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
    fun ponerEtiquetas(id: String, texto: String) = cambiarObra(id) { it.copy(etiquetas = texto.split(",").map { t -> t.trim().take(30) }.filter { t -> t.isNotEmpty() }.distinct().take(12)) }
    fun marcarEpisodio(id: String, t: Int, ep: Int) = cambiarObra(id) { L.marcarEpisodio(it, t, ep, hoy()) }
    fun marcarTemporada(id: String, t: Int) = cambiarObra(id) { L.marcarTemporada(it, t, hoy()) }

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
    fun actualizarDatos(id: String) {
        val o = _estado.value?.vivas?.firstOrNull { it.id == id } ?: return
        if (o.fuente.isEmpty()) return
        viewModelScope.launch {
            try {
                val r = io.github.fogonapps.cajon.datos.Encontrado(o.tipo, o.titulo, o.anio, o.creador, o.portada, o.fuente, o.paginas?.let { "$it p." } ?: o.formato)
                val d = detalles(r)
                cambiarObra(id) { v ->
                    v.copy(titulo = d.titulo, original = d.original, anio = d.anio ?: v.anio, creador = d.creador.ifBlank { v.creador },
                        generos = d.generos.ifEmpty { v.generos }, sinopsis = d.sinopsis.ifBlank { v.sinopsis }, portada = d.portada.ifBlank { v.portada },
                        fondo = d.fondo.ifBlank { v.fondo }, minutos = d.minutos ?: v.minutos, paginas = d.paginas ?: v.paginas,
                        temporadas = d.temporadas.ifEmpty { v.temporadas },
                        canciones = if (d.canciones.isEmpty()) v.canciones else d.canciones.mapIndexed { i, c -> v.canciones.getOrNull(i)?.takeIf { it.titulo == c.titulo }?.let { c.copy(nota = it.nota, favorita = it.favorita) } ?: c },
                        traida = d.traida)
                }
                avisar(tx(R.string.aviso_datos_al_dia))
            } catch (x: ClaveMala) { avisar(tx(R.string.buscar_tmdb_mala)) } catch (x: Exception) { avisar(tx(R.string.buscar_sin_red)) }
        }
    }

    /** Las obras con datos de TMDB de hace más de 6 meses se renuevan al abrirlas (si hay clave e internet). */
    fun renovarSiHaceFalta(o: Obra) {
        if (o.fuente.startsWith("tmdb:") && caja.claveTmdb != null && o.traida > 0 && ahora() - o.traida > SEIS_MESES) actualizarDatos(o.id)
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
        val fuente = "mb:artist:${a.mbid}"
        e.artistasVivos.firstOrNull { it.fuente == fuente }?.let { reemplazar(Pantalla.VerArtista(it.id)); return it.id }
        val id = nuevoId("a")
        cambiar { s -> s.copy(artistas = s.artistas + Artista(id, a.nombre, fuente, a.pais, a.desde, a.tipo, cambio = ahora())) }
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

    /** Trae la discografía de un artista de MusicBrainz (una vez por artista). */
    fun cargarDiscografia(fuente: String) {
        if (!fuente.startsWith("mb:artist:") || _discografia.value?.first == fuente) return
        _discografia.value = fuente to emptyList()
        viewModelScope.launch {
            try { _discografia.value = fuente to MusicBrainz.discografia(fuente.removePrefix("mb:artist:")) }
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
    }

    fun exportarJson(): String = JsonCajon.encodeToString(Estado.serializer(), _estado.value ?: Estado())
    fun leerRespaldo(texto: String): Estado? = leerRespaldoCajon(texto)

    fun importar(e: Estado) {
        // El respaldo se une con lo que hay (nada se pierde) y gana lo más reciente.
        cambiar { L.normalizar(L.unir(it, e)) }
        _pila.value = emptyList(); _pestana.value = Pestana.INICIO
        avisar(tx(R.string.aviso_importado))
    }

    companion object {
        /** En la búsqueda, además de los tipos de obra, se pueden buscar artistas. */
        const val TIPO_ARTISTA = "artista"
        private const val SEIS_MESES = 182L * 24 * 3600 * 1000
    }
}
