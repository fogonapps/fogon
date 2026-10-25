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
    data object Mercado : Pantalla
    data object Metas : Pantalla
    data object Perfil : Pantalla
    data object AcercaDe : Pantalla
    data object Manifiesto : Pantalla
    data object Apariencia : Pantalla
    data object Licencias : Pantalla
}

enum class Pestana { HOY, RECETAS, PLAN, PROGRESO }

class CuchaViewModel(app: Application) : AndroidViewModel(app) {
    private val cuchaApp = app as CuchaApp
    private val almacen = cuchaApp.almacen
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

    init {
        viewModelScope.launch {
            _estado.value = L.normalizar(almacen.data.first())
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

    suspend fun guardarYa() {
        val e = _estado.value ?: return
        ultimoGuardado = e
        almacen.updateData { e }
        WidgetCalorias.actualizar(getApplication())
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

    /* ----- El Forzudo ----- */

    /** Lee el peso y los entrenos de El Forzudo (si está). Si hay un peso nuevo y las metas son automáticas, las recalcula. */
    fun leerForzudo() {
        viewModelScope.launch {
            val c = getApplication<Application>()
            val texto = RespaldoFogon.leerDe(c, APPS_FOGON.first { it.clave == "forzudo" })
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
