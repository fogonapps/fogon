package io.github.fogonapps.cucha

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fogonapps.cucha.datos.Anotado
import io.github.fogonapps.cucha.datos.COMIDAS
import io.github.fogonapps.cucha.datos.Comible
import io.github.fogonapps.cucha.datos.DeForzudo
import io.github.fogonapps.cucha.datos.Dia
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.cucha.datos.Ingrediente
import io.github.fogonapps.cucha.datos.JsonCucha
import io.github.fogonapps.cucha.datos.Metas
import io.github.fogonapps.cucha.datos.Nutrientes
import io.github.fogonapps.cucha.datos.OpenFoodFacts
import io.github.fogonapps.cucha.datos.Perfil
import io.github.fogonapps.cucha.datos.Peso
import io.github.fogonapps.cucha.datos.Producto
import io.github.fogonapps.cucha.datos.Receta
import io.github.fogonapps.cucha.datos.leerForzudo
import io.github.fogonapps.identidad.APPS_FOGON
import io.github.fogonapps.identidad.Idioma
import io.github.fogonapps.identidad.RespaldoFogon
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/** Las pantallas que se abren encima de las pestañas. */
sealed interface Pantalla {
    /** Buscar qué anotar en [comida] del día [fecha]. */
    data class Anotar(val comida: String, val fecha: String) : Pantalla
    /** Cuánto se comió de [ref]; con [editar], se corrige algo ya anotado. */
    data class Cantidad(val ref: String, val comida: String, val fecha: String, val editar: String? = null) : Pantalla
    data class VerReceta(val id: String) : Pantalla
    /** [ingredientes] y [comida]: para una receta nueva armada con lo anotado en una comida. */
    data class EditarReceta(val id: String?, val ingredientes: List<Ingrediente> = emptyList(), val comida: String? = null) : Pantalla
    /** Un producto nuevo o para corregir; [producto] viene de Open Food Facts (o con solo el código). */
    data class EditarProducto(val producto: Producto?, val comida: String, val fecha: String) : Pantalla
    /** Anotar solo calorías (y macros si se saben), sin buscar un alimento; con [editar], corregirlo. */
    data class Rapido(val comida: String, val fecha: String, val editar: String? = null) : Pantalla
    data object Mercado : Pantalla
    data object Metas : Pantalla
    data object Ajustes : Pantalla
    data object EditarPerfil : Pantalla
    data object Perfil : Pantalla
    data object AcercaDe : Pantalla
    /** Reportar un fallo, algo que falta o una idea (igual en todas las apps de Fogón). */
    data object Reporte : Pantalla
    data object Manifiesto : Pantalla
    data object Apariencia : Pantalla
    data object Licencias : Pantalla
    /* ----- Cuenta y red (CU-01), como en El Forzudo ----- */
    data object Cuenta : Pantalla
    data class Seguridad(val ajuste: io.github.fogonapps.social.ui.AjusteSocial) : Pantalla
    data class Gente(val usuario: String, val seguidores: Boolean) : Pantalla
    data object Transparencia : Pantalla
    /** Avisos, moderación, una publicación, un perfil ajeno o una etiqueta de «Tu gente». */
    data class Social(val destino: String = "red", val id: Long = 0, val usuario: String = "") : Pantalla
    /** Compartir un plato: desde algo anotado ([anotado] en [fecha]) o en blanco. */
    data class CompartirPlato(val fecha: String? = null, val anotado: String? = null) : Pantalla
    data class CompartirReceta(val id: String) : Pantalla
    /** Reseñar un restaurante; con [clave], actualizar una reseña ya publicada. */
    data class ResenarRestaurante(val clave: String? = null) : Pantalla
}

enum class Pestana { HOY, GENTE, RECETAS, PLAN, PROGRESO }

/** La red de La Cucha: rutas /v2/apps/cucha y correos con su nombre. */
val CONFIGURACION_RED = io.github.fogonapps.social.ConfiguracionSocial("cucha", "La Cucha", "LaCucha-Fogon")

class CuchaViewModel(app: Application) : AndroidViewModel(app) {
    /** Las frases del saludo, como en El Cajón: las propias (cu_) y, con red, las de la gente de La Cucha. */
    val frases = io.github.fogonapps.social.FrasesSaludo(app.getSharedPreferences("frases", android.content.Context.MODE_PRIVATE), viewModelScope) { red.clienteFrases() }
    private val cuchaApp = app as CuchaApp
    val idPerfil = cuchaApp.perfiles.lista.activo
    private val almacen = cuchaApp.almacenPerfil(idPerfil)
    val perfiles get() = cuchaApp.perfiles
    private val sesion = io.github.fogonapps.social.SesionApp(app, cuchaApp.perfiles, idPerfil, "cucha")
    /** La cuenta y «Tu gente» (sin chat, D-030). */
    val red = io.github.fogonapps.social.RedApp(cuchaApp.perfiles, sesion, viewModelScope, CONFIGURACION_RED,
        io.github.fogonapps.social.TextosRed(R.string.cuenta_error, R.string.cuenta_perfil_cambio, R.string.cuenta_operacion_lista, R.string.cuenta_otro_perfil),
        { avisar(tx(it)) }, ::cambiarPerfil, { io.github.fogonapps.cucha.datos.cabePublicacionCucha(it) }, "publicaciones-cucha.json",
        listOf(io.github.fogonapps.cucha.datos.PARTE_PLATOS, io.github.fogonapps.cucha.datos.PARTE_RESTAURANTES))
    /** D-033: el diario se guarda solo en la cuenta, cifrado con la contraseña, y vuelve al reinstalar. */
    private val copia = io.github.fogonapps.social.CopiaEnCuenta(red, app.getSharedPreferences("copia-cucha-p$idPerfil", android.content.Context.MODE_PRIVATE), viewModelScope,
        leer = { _estado.value?.let { e -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            io.github.fogonapps.cucha.datos.conFotos(JsonCucha.encodeToString(Estado.serializer(), e), e.fotosRecetas) } } },
        aplicar = { texto ->
            // Las fotos de las recetas vienen adentro: se escriben en este teléfono y reemplazan las rutas de otro.
            val (json, fotos) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                io.github.fogonapps.cucha.datos.sacarFotos(texto, java.io.File(perfiles.directorio(idPerfil), "recetas"))
            }
            val leido = JsonCucha.decodeFromString(Estado.serializer(), json)
            val conFotos = leido.copy(fotosRecetas = leido.fotosRecetas.filterValues { ruta ->
                runCatching { java.io.File(java.net.URI(ruta)).exists() }.getOrDefault(false) } + fotos)
            val remoto = L.normalizar(conFotos)
            _estado.value = remoto; ultimoGuardado = remoto
            almacen.updateData { remoto }
            WidgetCalorias.actualizar(getApplication())
        },
        // Vacío por el contenido, no por la bienvenida: al reinstalar, el diario de la cuenta gana al recién empezado.
        vacio = { _estado.value?.let { e -> e.dias.values.none { it.anotados.isNotEmpty() } && e.recetas.none { it.propia } && e.productos.isEmpty() && e.pesos.isEmpty() } ?: true },
        marca = { runCatching { JsonCucha.decodeFromString(Estado.serializer(), it).updatedAt }.getOrDefault(0L) })
    val L get() = cuchaApp.logica

    private val _estado = MutableStateFlow<Estado?>(null)
    val estado: StateFlow<Estado?> = _estado

    private val _pila = MutableStateFlow<List<Pantalla>>(emptyList())
    val pila: StateFlow<List<Pantalla>> = _pila
    private val _pestana = MutableStateFlow(Pestana.HOY)
    val pestana: StateFlow<Pestana> = _pestana

    /** El día que se ve en Hoy (se puede ir a días anteriores). */
    private val _dia = MutableStateFlow(LocalDate.now())
    val dia: StateFlow<LocalDate> = _dia

    /** Lo que se lee de El Forzudo, si está instalado (se vuelve a leer al volver a la app). */
    private val _forzudo = MutableStateFlow<DeForzudo?>(null)
    val forzudo: StateFlow<DeForzudo?> = _forzudo

    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes = _mensajes.receiveAsFlow()

    private var guardado: Job? = null
    private val off by lazy { OpenFoodFacts() }

    /* X-10: avisos con la app cerrada: lo que se ve al abrir no vuelve a avisar, y la revisión sigue a la sesión. */
    init {
        red.alContarAvisosRed = { io.github.fogonapps.social.ui.Avisos.visto(getApplication(), idPerfil, avisos = it) }
        viewModelScope.launch { red.usuario.collect { io.github.fogonapps.social.ui.Avisos.programar(getApplication()) } }
    }
    init {
        viewModelScope.launch {
            _estado.value = L.normalizar(almacen.data.first())
            cuchaApp.confirmarCuaderno()
            copia.sincronizar()
            // Si otra app de Fogón restauró los datos (respaldo de todo Fogón), se toman.
            almacen.data.drop(1).collect { d -> if (d != ultimoGuardado && d != _estado.value) _estado.value = d }
        }
    }

    /** Lo último que esta pantalla escribió en el disco, para distinguirlo de un cambio de afuera. */
    @Volatile private var ultimoGuardado: Estado? = null

    fun hoy(): LocalDate = LocalDate.now()
    fun idioma(): String = Idioma.actual(getApplication())
    fun tx(id: Int, vararg a: Any): String = Idioma.envolver(getApplication()).getString(id, *a)
    private fun avisar(m: String) { _mensajes.trySend(m) }
    private fun nuevoId(prefijo: String) = prefijo + System.currentTimeMillis().toString(36) + Random.nextInt(1296).toString(36)

    /** Todo cambio pasa por aquí: memoria, pantalla y, 300 ms después, el disco. */
    private fun cambiar(f: (Estado) -> Estado) {
        val e = _estado.value ?: return
        _estado.value = f(e).copy(updatedAt = System.currentTimeMillis())
        guardado?.cancel()
        guardado = viewModelScope.launch { delay(300); guardarYa() }
    }

    /** Al salir de la app; durante un cambio de perfil lo guarda el propio cambio. */
    fun guardarAlSalir() { if (cambiandoPerfil) return; guardado?.cancel(); viewModelScope.launch { guardarYa() } }

    suspend fun guardarYa() {
        val e = _estado.value ?: return
        ultimoGuardado = e
        almacen.updateData { e }
        WidgetCalorias.actualizar(getApplication())
        copia.cambio()
    }

    /* ----- Navegación ----- */

    fun abrir(p: Pantalla) { _pila.value = _pila.value + p }
    fun cerrar() { _pila.value = _pila.value.dropLast(1) }
    fun reemplazar(p: Pantalla) { _pila.value = _pila.value.dropLast(1) + p }
    fun irA(p: Pestana) { _pila.value = emptyList(); _pestana.value = p }
    fun atras() { if (_pila.value.isNotEmpty()) cerrar() else _pestana.value = Pestana.HOY }
    fun verDia(f: LocalDate) { _dia.value = if (f.isAfter(hoy())) hoy() else f }

    /** La comida que toca según la hora (para el botón de anotar). */
    fun comidaAhora(): String = LocalTime.now().hour.let { h ->
        when { h < 10 -> "desayuno"; h < 12 -> "media_manana"; h < 15 -> "almuerzo"; h < 18 -> "algo"; else -> "cena" }
    }

    fun anotarAhora() = abrir(Pantalla.Anotar(comidaAhora(), _dia.value.toString()))

    /* ----- Perfiles del teléfono ----- */

    private var cambiandoPerfil = false
    fun agregarPerfil() {
        val nuevo = perfiles.agregar() ?: return
        cambiarPerfil(nuevo.id)
    }
    private var retirarAlCambiar = false
    fun retirarPerfil() {
        val otro = perfiles.lista.perfiles.firstOrNull { it.id != idPerfil } ?: perfiles.agregar() ?: return
        retirarAlCambiar = true
        cambiarPerfil(otro.id)
    }
    /** Guarda, detiene lo de este perfil y activa otro; la actividad arma un ViewModel nuevo para él. */
    fun cambiarPerfil(id: String) {
        if (id == idPerfil || cambiandoPerfil) return
        cambiandoPerfil = true
        viewModelScope.launch {
            try {
                red.invalidar()
                val actual = kotlinx.coroutines.currentCoroutineContext()[Job]
                viewModelScope.coroutineContext[Job]?.children?.filter { it != actual }?.toList()?.forEach { it.cancelAndJoin() }
                guardarYa()
                if (retirarAlCambiar) {
                    if (idPerfil == "1") io.github.fogonapps.cucha.datos.migracionPerfiles(getApplication()).confirmarArranque()
                    cuchaApp.cerrarAlmacen(idPerfil)
                    perfiles.cambiar(id)
                    perfiles.quitar(idPerfil) { perfil ->
                        io.github.fogonapps.social.SesionApp.preferenciasDe("cucha", perfil).forEach {
                            check(getApplication<Application>().getSharedPreferences(it, android.content.Context.MODE_PRIVATE).edit().clear().commit())
                        }
                    }
                }
                cuchaApp.activarPerfil(id)
                WidgetCalorias.actualizar(getApplication())
            } catch (x: java.io.IOException) {
                cambiandoPerfil = false
                avisar(tx(R.string.cuenta_error))
            }
        }
    }

    /* ----- El Forzudo ----- */

    /** Los perfiles de El Forzudo, para elegir a mano cuál es el de esta persona (null: no tiene o no está). */
    private val _perfilesForzudo = MutableStateFlow<List<io.github.fogonapps.identidad.PerfilDeApp>?>(null)
    val perfilesForzudo: StateFlow<List<io.github.fogonapps.identidad.PerfilDeApp>?> = _perfilesForzudo
    private val _enlaceForzudo = MutableStateFlow<io.github.fogonapps.cucha.datos.EnlaceForzudo>(io.github.fogonapps.cucha.datos.EnlaceForzudo.Ninguno)
    val enlaceForzudo: StateFlow<io.github.fogonapps.cucha.datos.EnlaceForzudo> = _enlaceForzudo

    /** Datos de la red que se recuerdan en este perfil (por ejemplo, el lugar de una publicación propia). */
    fun datoRed(clave: String): String? = sesion.dato(clave)
    fun guardarDatoRed(clave: String, valor: String?) = sesion.guardarDato(clave, valor)

    /** La persona elige qué perfil de El Forzudo es el suyo (o [io.github.fogonapps.cucha.datos.FORZUDO_NINGUNO]). */
    fun elegirPerfilForzudo(id: String) { sesion.guardarDato("forzudo", id); leerForzudo() }

    /** Lee el peso y los entrenos de El Forzudo (si está), del perfil enlazado. Si hay un peso nuevo y las metas son automáticas, las recalcula. */
    fun leerForzudo() {
        viewModelScope.launch {
            val c = getApplication<Application>()
            val app = APPS_FOGON.first { it.clave == "forzudo" }
            val lista = RespaldoFogon.perfilesDe(c, app)
            _perfilesForzudo.value = lista
            val enlace = io.github.fogonapps.cucha.datos.enlaceForzudo(lista, sesion.idCuenta, sesion.dato("forzudo"), perfiles.lista.perfiles.size)
            _enlaceForzudo.value = enlace
            val texto = when (enlace) {
                io.github.fogonapps.cucha.datos.EnlaceForzudo.Unico -> RespaldoFogon.leerDe(c, app)
                is io.github.fogonapps.cucha.datos.EnlaceForzudo.Perfil -> RespaldoFogon.leerPerfilDe(c, app, enlace.id)
                else -> null
            }
            val d = texto?.let { leerForzudo(it, hoy()) }
            _forzudo.value = d
            val e = _estado.value ?: return@launch
            val peso = d?.peso?.kg ?: return@launch
            if (e.listo && peso != e.perfil.pesoKg) cambiar { x ->
                val p = x.perfil.copy(pesoKg = peso)
                x.copy(perfil = p, metas = if (x.metas.manual) x.metas else L.metasDe(p, x.metas))
            }
        }
    }

    /* ----- Tutorial y metas ----- */

    fun empezar(nombre: String, perfil: Perfil, metas: Metas?) = cambiar { e ->
        e.copy(nombre = nombre.trim(), perfil = perfil, metas = metas ?: L.metasDe(perfil, e.metas), listo = true)
    }

    fun guardarPerfil(p: Perfil) = cambiar { e -> e.copy(perfil = p, metas = if (e.metas.manual) e.metas else L.metasDe(p, e.metas)) }
    fun guardarMetas(m: Metas) = cambiar { it.copy(metas = m) }
    fun metasAutomaticas() = cambiar { e -> e.copy(metas = L.metasDe(e.perfil, e.metas)) }
    fun guardarPresentacion(nombre: String, bio: String) = cambiar { it.copy(nombre = io.github.fogonapps.perfil.recortarNombrePerfil(nombre.trim()), bio = io.github.fogonapps.perfil.recortarBioPerfil(bio.trim())) }

    fun imagenPerfil(portada: Boolean, imagen: String) = cambiar {
        if (portada) it.copy(portadaPerfil = imagen) else it.copy(fotoPerfil = imagen)
    }

    fun cambiarNombre(n: String) = cambiar { it.copy(nombre = n.trim()) }
    fun cambiarIdioma(codigo: String) = Idioma.elegir(getApplication(), codigo)

    /** El peso, cuando no está El Forzudo. */
    fun anotarPeso(kg: Double) = cambiar { e ->
        val f = hoy().toString()
        val p = e.perfil.copy(pesoKg = kg)
        e.copy(pesos = e.pesos.filter { it.fecha != f } + Peso(f, kg), perfil = p, metas = if (e.metas.manual) e.metas else L.metasDe(p, e.metas))
    }

    /* ----- Anotar ----- */

    /** Valores de [cantidad] de algo (gramos para alimentos y productos; porciones para recetas). */
    fun valores(c: Comible, cantidad: Double): Nutrientes = when (c) {
        is Comible.DeBase -> L.deAlimento(c.a, cantidad)
        is Comible.DeProducto -> L.deProducto(c.p, cantidad)
        is Comible.DeReceta -> L.porPorcion(c.r) * cantidad
    }

    fun anotar(c: Comible, cantidad: Double, detalle: String, comida: String, fecha: String, editar: String? = null) {
        if (cantidad <= 0) return
        val nuevo = Anotado(editar ?: nuevoId("n"), comida, c.ref, c.nombre(idioma()), cantidad, detalle, valores(c, cantidad))
        cambiar { e ->
            val d = e.dias[fecha] ?: Dia()
            val lista = if (editar != null) d.anotados.map { if (it.id == editar) nuevo else it } else d.anotados + nuevo
            e.copy(dias = e.dias + (fecha to d.copy(anotados = lista)))
        }
        // Si vino de la búsqueda, se vuelve a ella para sumar otro ingrediente (huevos, y después champiñones…);
        // si no (el menú de Hoy, corregir algo), se vuelve hasta Hoy.
        val busqueda = _pila.value.indexOfLast { it is Pantalla.Anotar }
        _pila.value = if (editar == null && busqueda >= 0) _pila.value.take(busqueda + 1)
            else _pila.value.filterNot { it is Pantalla.Anotar || it is Pantalla.Cantidad || it is Pantalla.EditarProducto }
        if (editar == null) avisar(tx(R.string.aviso_anotado, nuevo.nombre))
    }

    /** Lo anotado rápido lleva la referencia "rapido:<id>": no viene de la base, sus valores van en el mismo anotado. */
    fun anotarRapido(nombre: String, n: Nutrientes, comida: String, fecha: String, editar: String? = null) {
        if (n.kcal <= 0) return
        val id = editar ?: nuevoId("n")
        val nuevo = Anotado(id, comida, "rapido:$id", nombre.trim().ifBlank { tx(R.string.anotado_rapido) }, 1.0, tx(R.string.anotado_rapido_detalle), n)
        cambiar { e ->
            val d = e.dias[fecha] ?: Dia()
            val lista = if (editar != null) d.anotados.map { if (it.id == editar) nuevo else it } else d.anotados + nuevo
            e.copy(dias = e.dias + (fecha to d.copy(anotados = lista)))
        }
        _pila.value = _pila.value.filterNot { it is Pantalla.Anotar || it is Pantalla.Rapido }
        if (editar == null) avisar(tx(R.string.aviso_anotado, nuevo.nombre))
    }

    fun borrarAnotado(fecha: String, id: String) = cambiar { e ->
        val d = e.dias[fecha] ?: return@cambiar e
        e.copy(dias = e.dias + (fecha to d.copy(anotados = d.anotados.filter { it.id != id })))
    }

    /** Copia lo de una comida de ayer a la misma comida de [fecha]. */
    fun repetirAyer(comida: String, fecha: String) = cambiar { e ->
        val ayer = LocalDate.parse(fecha).minusDays(1).toString()
        val copia = e.dias[ayer]?.anotados.orEmpty().filter { it.comida == comida }.map { it.copy(id = nuevoId("n")) }
        if (copia.isEmpty()) { avisar(tx(R.string.aviso_ayer_vacio)); return@cambiar e }
        val d = e.dias[fecha] ?: Dia()
        e.copy(dias = e.dias + (fecha to d.copy(anotados = d.anotados + copia)))
    }

    fun agua(fecha: String, ml: Int) = cambiar { e ->
        val d = e.dias[fecha] ?: Dia()
        e.copy(dias = e.dias + (fecha to d.copy(aguaMl = (d.aguaMl + ml).coerceIn(0, 10000))))
    }

    fun favorito(ref: String) = cambiar { e -> e.copy(favoritos = if (ref in e.favoritos) e.favoritos - ref else listOf(ref) + e.favoritos) }

    /* ----- Productos y código de barras ----- */

    fun activarCodigoBarras(si: Boolean) = cambiar { it.copy(codigoBarras = si) }

    /** Busca el código en Open Food Facts y abre el producto para revisarlo antes de anotarlo. */
    fun buscarCodigo(codigo: String, comida: String, fecha: String) {
        val e = _estado.value ?: return
        e.productos.firstOrNull { it.codigo == codigo }?.let { p ->
            abrir(Pantalla.Cantidad("p:${p.id}", comida, fecha)); return
        }
        avisar(tx(R.string.aviso_buscando))
        viewModelScope.launch {
            val p = try { off.buscar(codigo) } catch (_: OpenFoodFacts.SinRed) { avisar(tx(R.string.aviso_sin_red)); return@launch }
            if (p == null) avisar(tx(R.string.aviso_no_encontrado))
            abrir(Pantalla.EditarProducto(p ?: Producto("", "", Nutrientes(), codigo = codigo), comida, fecha))
        }
    }

    /** Guarda un producto propio y abre la cantidad para anotarlo. */
    fun guardarProducto(p: Producto, comida: String, fecha: String) {
        val id = p.id.ifEmpty { nuevoId("p") }
        val guardado = p.copy(id = id, nombre = p.nombre.trim())
        cambiar { e -> e.copy(productos = e.productos.filter { it.id != id } + guardado) }
        reemplazar(Pantalla.Cantidad("p:$id", comida, fecha))
    }

    /* ----- Recetas ----- */

    fun guardarReceta(r: Receta) {
        val id = r.id.ifEmpty { nuevoId("r") }
        cambiar { e -> e.copy(recetas = e.recetas.filter { it.id != id } + r.copy(id = id, propia = true)) }
        reemplazar(Pantalla.VerReceta(id))
    }

    /** Borra una receta propia (las de ejemplo no se borran; si se editaron, vuelven a ser las originales). */
    /** Una foto del teléfono para una receta: se copia achicada (1000 px) a la carpeta de la app. */
    fun ponerFotoReceta(id: String, uri: android.net.Uri) {
        viewModelScope.launch {
            val ruta = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { copiarFoto(uri, id) } }.getOrNull()
            if (ruta == null) { avisar(tx(R.string.aviso_foto_no)); return@launch }
            val vieja = _estado.value?.fotosRecetas?.get(id)
            cambiar { e -> e.copy(fotosRecetas = e.fotosRecetas + (id to ruta)) }
            vieja?.let { runCatching { java.io.File(java.net.URI(it)).delete() } }
        }
    }

    fun quitarFotoReceta(id: String) {
        val vieja = _estado.value?.fotosRecetas?.get(id) ?: return
        cambiar { e -> e.copy(fotosRecetas = e.fotosRecetas - id) }
        runCatching { java.io.File(java.net.URI(vieja)).delete() }
    }

    private fun copiarFoto(uri: android.net.Uri, id: String): String {
        val app = getApplication<Application>()
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("sin imagen")
        val medidas = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        var muestra = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= 1000) muestra *= 2
        val b = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = muestra }) ?: error("no se lee")
        val carpeta = java.io.File(perfiles.directorio(idPerfil), "recetas").apply { mkdirs() }
        val archivo = java.io.File(carpeta, "$id-${System.currentTimeMillis()}.jpg")
        archivo.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        return archivo.toURI().toString()
    }

    fun borrarReceta(id: String) {
        cambiar { e -> e.copy(recetas = e.recetas.filter { it.id != id }, plan = e.plan.filterValues { it != id || L.base.receta(id) != null }) }
        cerrar()
    }

    /**
     * Importa recetas de un archivo JSON (una lista de recetas con el formato de assets/datos/recetas.json).
     * Sirve para pasar recetas propias de otro lado; se suman a las que ya hay.
     */
    fun importarRecetas(texto: String): Int {
        val lista = runCatching { JsonCucha.decodeFromString<List<Receta>>(texto) }.getOrNull()?.filter { it.es.isNotBlank() }
        if (lista.isNullOrEmpty()) { avisar(tx(R.string.aviso_no_son_recetas)); return 0 }
        cambiar { e ->
            val ids = e.recetas.map { it.id }.toSet()
            e.copy(recetas = e.recetas + lista.map { r -> r.copy(id = if (r.id.isBlank() || r.id in ids) nuevoId("r") else r.id, propia = true) })
        }
        avisar(Idioma.envolver(getApplication()).resources.getQuantityString(R.plurals.aviso_recetas_importadas, lista.size, lista.size))
        return lista.size
    }

    /* ----- Plan y mercado ----- */

    fun ponerEnPlan(letra: String, dia: Int, comida: String, receta: String?) = cambiar { e ->
        val k = L.clavePlan(letra, dia, comida)
        e.copy(plan = if (receta == null) e.plan - k else e.plan + (k to receta))
    }

    fun alternar(si: Boolean) = cambiar { it.copy(alternar = si) }

    fun marcarMercado(nombre: String) = cambiar { e ->
        e.copy(mercadoHecho = if (nombre in e.mercadoHecho) e.mercadoHecho - nombre else e.mercadoHecho + nombre)
    }

    fun agregarMercado(texto: String) {
        val t = texto.trim().take(60); if (t.isEmpty()) return
        cambiar { it.copy(mercadoExtra = it.mercadoExtra + t) }
    }

    fun quitarMercado(texto: String) = cambiar { it.copy(mercadoExtra = it.mercadoExtra - texto, mercadoHecho = it.mercadoHecho - texto) }
    fun limpiarMercado() = cambiar { it.copy(mercadoHecho = emptyList()) }

    /* ----- Respaldo ----- */

    fun exportarJson(): String = JsonCucha.encodeToString(Estado.serializer(), _estado.value ?: Estado())
    fun leerRespaldo(texto: String): Estado? = leerRespaldoCucha(texto)

    fun importar(e: Estado) {
        cambiar { L.normalizar(e) }
        _pila.value = emptyList(); _pestana.value = Pestana.HOY
        avisar(tx(R.string.aviso_importado))
    }

    /** Las comidas del día en orden, para las pantallas. */
    val comidas get() = COMIDAS
}
