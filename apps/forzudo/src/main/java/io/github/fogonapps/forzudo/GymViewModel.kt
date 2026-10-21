package io.github.fogonapps.forzudo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fogonapps.forzudo.datos.ClienteGist
import io.github.fogonapps.forzudo.datos.Dia
import io.github.fogonapps.forzudo.datos.EntrenoActivo
import io.github.fogonapps.forzudo.datos.ErrorGitHub
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.forzudo.datos.Item
import io.github.fogonapps.forzudo.datos.JsonGym
import io.github.fogonapps.forzudo.datos.Logica
import io.github.fogonapps.forzudo.datos.PesoCorporal
import io.github.fogonapps.forzudo.datos.Rango
import io.github.fogonapps.forzudo.datos.Rutina
import io.github.fogonapps.forzudo.datos.Serie
import io.github.fogonapps.forzudo.datos.Sesion
import io.github.fogonapps.forzudo.datos.fk
import io.github.fogonapps.forzudo.datos.iso
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import kotlin.math.floor

/** Pantallas que se abren encima de las pestañas. */
sealed interface Pantalla {
    data class Detalle(val id: String) : Pantalla
    data class Musculo(val id: String) : Pantalla
    data object Programas : Pantalla
    data class Editor(val rid: String) : Pantalla
    data class Cambiar(val id: String?, val modo: ModoCambio, val rid: String? = null, val idx: Int = -1) : Pantalla
    data object Vivo : Pantalla
    data class Resumen(val r: ResumenEntreno) : Pantalla
    data class Zoom(val id: String, val i: Int) : Pantalla
    data object AcercaDe : Pantalla
    data object Manifiesto : Pantalla
    data object Licencias : Pantalla
    data object Preguntas : Pantalla
}

enum class ModoCambio { EDITAR, AGREGAR, VIVO, VIVO_AGREGAR }
enum class Pestana { INICIO, ENTRENAR, RANGOS, PERFIL }

data class SubioRango(val musculo: String, val rango: Rango)

data class ResumenEntreno(
    val titulo: String, val fecha: String, val ms: Long, val volumen: Double, val series: Int,
    val subidas: List<SubioRango>, val records: List<String>,
)

data class Descanso(val fin: Long, val total: Int)

enum class EstadoSync { APAGADA, EN_NUBE, SINCRONIZANDO, LISTA, SIN_CONEXION, ERROR }
data class Sync(val estado: EstadoSync, val mensaje: String? = null, val usuario: String? = null, val ultima: Long = 0)

class GymViewModel(app: Application) : AndroidViewModel(app) {
    private val gymApp = app as ForzudoApp
    val bib = gymApp.biblioteca
    val L = Logica(bib)
    private val caja = gymApp.caja

    private val _estado = MutableStateFlow<Estado?>(null)
    val estado: StateFlow<Estado?> = _estado.asStateFlow()

    private val _pila = MutableStateFlow<List<Pantalla>>(emptyList())
    val pila: StateFlow<List<Pantalla>> = _pila.asStateFlow()
    private val _pestana = MutableStateFlow(Pestana.INICIO)
    val pestana: StateFlow<Pestana> = _pestana.asStateFlow()

    private val _descanso = MutableStateFlow<Descanso?>(null)
    val descanso: StateFlow<Descanso?> = _descanso.asStateFlow()

    private val _sync = MutableStateFlow(Sync(if (caja.token != null) EstadoSync.EN_NUBE else EstadoSync.APAGADA, usuario = caja.usuario, ultima = caja.ultima))
    val sync: StateFlow<Sync> = _sync.asStateFlow()

    /* ----- Tutorial (se guarda solo en este teléfono) ----- */
    private val prefsUi = app.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
    private val _tutorial = MutableStateFlow(!prefsUi.getBoolean("tutorial_visto", false))
    val tutorial: StateFlow<Boolean> = _tutorial.asStateFlow()
    fun verTutorial() { _tutorial.value = true }
    fun terminarTutorial() { prefsUi.edit().putBoolean("tutorial_visto", true).apply(); _tutorial.value = false }

    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes = _mensajes.receiveAsFlow()

    private val escribiendo = Mutex()
    private var guardarJob: Job? = null
    private var syncJob: Job? = null
    private var descansoJob: Job? = null
    private var sincronizando = false
    private var sincronizarOtraVez = false

    init {
        viewModelScope.launch {
            _estado.value = L.normalizar(gymApp.almacen.data.first())
            sincronizar()
        }
    }

    fun hoy(): LocalDate = LocalDate.now()
    fun dias(): List<Dia> = _estado.value?.let { L.dias(it) } ?: emptyList()
    private fun avisar(m: String) { _mensajes.trySend(m) }

    /* ----- Guardado ----- */

    private fun cambiar(f: (Estado) -> Estado) {
        val e = _estado.value ?: return
        _estado.value = f(e).copy(updatedAt = System.currentTimeMillis())
        guardarJob?.cancel()
        guardarJob = viewModelScope.launch { delay(300); guardarYa() }
        syncJob?.cancel()
        syncJob = viewModelScope.launch { delay(4000); sincronizar() }
    }

    /** Escribe en disco ahora (también se llama al salir de la app). */
    suspend fun guardarYa() {
        val e = _estado.value ?: return
        escribiendo.withLock { gymApp.almacen.updateData { e } }
    }

    /** Al salir de la app: el ViewModel sigue vivo, así que basta con lanzar la escritura. */
    fun guardarAlSalir() { guardarJob?.cancel(); viewModelScope.launch { guardarYa() } }

    /* ----- Navegación ----- */

    fun irA(p: Pestana) { _pestana.value = p }
    fun abrir(p: Pantalla) { _pila.value = _pila.value + p }
    fun cerrar() { _pila.value = _pila.value.dropLast(1) }
    fun reemplazar(p: Pantalla) { _pila.value = _pila.value.dropLast(1) + p }

    /** Botón atrás: true si se manejó dentro de la app. */
    fun atras(): Boolean {
        if (_pila.value.isNotEmpty()) { cerrar(); return true }
        if (_pestana.value != Pestana.INICIO) { _pestana.value = Pestana.INICIO; return true }
        return false
    }

    /* ----- Perfil ----- */

    fun ponerNombre(n: String) = cambiar { it.copy(nombre = n.trim().take(30).ifEmpty { null }) }

    fun registrarPeso(texto: String): Boolean {
        val v = io.github.fogonapps.forzudo.datos.numero(texto)
        if (v == null || v < 25 || v > 250) { avisar("Escribe tu peso en kg, por ejemplo 55,5"); return false }
        val d = iso(hoy())
        cambiar { e -> e.copy(body = e.body.filter { it.date != d } + PesoCorporal(d, v)) }
        avisar("Peso guardado")
        return true
    }

    /* ----- Inicio ----- */

    fun borrarSesion(id: String) = cambiar { e -> e.copy(sessions = e.sessions.filter { it.id != id }, deleted = e.deleted + id) }

    fun fechaSesion(id: String, fecha: String) {
        cambiar { e -> e.copy(sessions = e.sessions.map { if (it.id == id) it.copy(date = fecha) else it }) }
        avisar("Fecha actualizada")
    }

    /* ----- Programas y editor ----- */

    fun aplicarPrograma(key: String) {
        val p = bib.programas.firstOrNull { it.key == key } ?: return
        if (_estado.value?.active != null) { avisar("Termina o descarta el entreno en curso primero"); return }
        cambiar { e ->
            val conTitulos = L.normalizar(e.copy(routines = e.routines))
            conTitulos.copy(routines = p.routines, program = key, progEdited = false)
        }
        _pila.value = emptyList(); _pestana.value = Pestana.ENTRENAR
        avisar("Programa cambiado: ${p.name}")
    }

    private fun cambiarRutina(rid: String, f: (Rutina) -> Rutina) = cambiar { e ->
        e.copy(routines = e.routines.orEmpty().map { if (it.id == rid) f(it) else it }, progEdited = true)
    }

    fun rutina(rid: String): Rutina? = _estado.value?.routines?.firstOrNull { it.id == rid }

    fun tituloRutina(rid: String, t: String) = cambiarRutina(rid) { it.copy(title = t.trim().ifEmpty { "Rutina" }.take(40)) }
    fun diaRutina(rid: String, dow: Int?) = cambiarRutina(rid) { it.copy(dow = dow) }

    fun campoItem(rid: String, i: Int, campo: String, valor: Int) {
        if (valor <= 0) return
        cambiarRutina(rid) { r ->
            val it0 = bib.cfg(r.items[i])
            var it = r.items[i]
            it = when (campo) {
                "sets" -> it.copy(sets = valor)
                "lo" -> it.copy(lo = valor, hi = if ((it.hi ?: it0.hi) < valor) valor else it.hi)
                "hi" -> it.copy(hi = if (valor < (it.lo ?: it0.lo)) (it.lo ?: it0.lo) else valor)
                "rest" -> it.copy(rest = valor)
                else -> it
            }
            r.copy(items = r.items.toMutableList().also { l -> l[i] = it })
        }
    }

    fun moverItem(rid: String, i: Int, dir: Int) = cambiarRutina(rid) { r ->
        val j = i + dir
        if (j < 0 || j >= r.items.size) r
        else r.copy(items = r.items.toMutableList().also { l -> val t = l[i]; l[i] = l[j]; l[j] = t })
    }

    fun quitarItem(rid: String, i: Int) = cambiarRutina(rid) { r -> r.copy(items = r.items.filterIndexed { k, _ -> k != i }) }

    fun nuevaRutina() {
        val r = Rutina("r${System.currentTimeMillis()}", "Nueva rutina", null, null, emptyList())
        cambiar { e -> e.copy(routines = e.routines.orEmpty() + r, progEdited = true) }
        abrir(Pantalla.Editor(r.id))
    }

    fun eliminarRutina(rid: String) {
        val e = _estado.value ?: return
        if (e.active?.day == rid) { avisar("Termina el entreno de esta rutina primero"); return }
        if (e.routines.orEmpty().size <= 1) { avisar("Debes tener al menos una rutina"); return }
        cambiar { it.copy(routines = it.routines.orEmpty().filter { r -> r.id != rid }, progEdited = true) }
        _pila.value = _pila.value.filter { it !is Pantalla.Editor }
    }

    /* ----- Cambiar o agregar ejercicio ----- */

    fun elegir(p: Pantalla.Cambiar, nid: String, guardarEnRutina: Boolean) {
        val nombre = bib.ex.getValue(nid).n
        when (p.modo) {
            ModoCambio.EDITAR -> {
                cambiarRutina(p.rid!!) { r ->
                    r.copy(items = r.items.toMutableList().also { l -> l[p.idx] = Item(nid, l[p.idx].sets ?: 3) })
                }
                cerrar(); avisar("Cambiado por $nombre")
            }
            ModoCambio.AGREGAR -> {
                if (rutina(p.rid!!)?.items?.any { it.id == nid } == true) { avisar("Ya está en la rutina"); return }
                cambiarRutina(p.rid) { r -> r.copy(items = r.items + Item(nid, 3)) }
                cerrar(); avisar("Agregado: $nombre")
            }
            ModoCambio.VIVO, ModoCambio.VIVO_AGREGAR -> {
                val a = _estado.value?.active ?: return
                if (a.items.any { it.id == nid }) { avisar("Ese ejercicio ya está en el entreno"); return }
                cambiar { e ->
                    val act = e.active!!
                    if (p.modo == ModoCambio.VIVO_AGREGAR) {
                        e.copy(active = act.copy(items = act.items + Item(nid, 3), entries = act.entries + (nid to seriesNuevas(3))))
                    } else {
                        val id = p.id!!
                        val idx = act.items.indexOfFirst { it.id == id }
                        val hecho = act.entries[id].orEmpty().any { it.done }
                        val it = Item(nid, act.items[idx].sets ?: 3)
                        val items = act.items.toMutableList()
                        val entries = act.entries.toMutableMap()
                        if (hecho) items.add(idx + 1, it) else { items[idx] = it; entries.remove(id) }
                        entries[nid] = seriesNuevas(it.sets ?: 3)
                        var rutinas = e.routines
                        var editado = e.progEdited
                        if (guardarEnRutina) {
                            rutinas = rutinas.orEmpty().map { r ->
                                if (r.id != act.day) r else {
                                    val j = r.items.indexOfFirst { x -> x.id == id }
                                    if (j < 0) r else r.copy(items = r.items.toMutableList().also { l -> l[j] = Item(nid, l[j].sets ?: 3) })
                                }
                            }
                            editado = true
                        }
                        e.copy(active = act.copy(items = items, entries = entries), routines = rutinas, progEdited = editado)
                    }
                }
                cerrar()
                avisar(if (p.modo == ModoCambio.VIVO_AGREGAR) "Agregado: $nombre" else "Cambiado por $nombre")
            }
        }
    }

    /* ----- Entreno en vivo ----- */

    private fun seriesNuevas(n: Int) = List(n) { Serie() }

    fun empezar(did: String) {
        val d = dias().firstOrNull { it.id == did } ?: return
        val items = d.rutina.items
        cambiar { e ->
            e.copy(active = EntrenoActivo(
                id = "s-${System.currentTimeMillis()}", date = iso(hoy()), day = did, title = d.title,
                start = System.currentTimeMillis(), items = items,
                entries = items.associate { it.id to seriesNuevas(it.sets ?: 3) },
            ))
        }
        abrir(Pantalla.Vivo)
    }

    /** Series ya marcadas que se perderían al pasar a otra rutina. */
    fun seriesQueSePierden(did: String): Int {
        val a = _estado.value?.active ?: return 0
        val nd = dias().firstOrNull { it.id == did } ?: return 0
        return a.entries.count { (k, l) -> nd.rutina.items.none { it.id == k } && l.any { it.done } }
    }

    fun cambiarRutinaActiva(did: String) {
        val a = _estado.value?.active ?: return
        if (a.day == did) return
        val nd = dias().firstOrNull { it.id == did } ?: return
        cambiar { e ->
            val act = e.active!!
            val items = nd.rutina.items
            e.copy(active = act.copy(day = did, title = nd.title, items = items,
                entries = items.associate { it.id to (act.entries[it.id] ?: seriesNuevas(it.sets ?: 3)) }))
        }
    }

    fun fechaActiva(f: String) = cambiar { e -> e.copy(active = e.active?.copy(date = f)) }

    private fun cambiarSerie(id: String, i: Int, f: (Serie) -> Serie) = cambiar { e ->
        val a = e.active ?: return@cambiar e
        val l = a.entries[id]?.toMutableList() ?: return@cambiar e
        if (i !in l.indices) return@cambiar e
        l[i] = f(l[i])
        e.copy(active = a.copy(entries = a.entries + (id to l)))
    }

    fun escribir(id: String, i: Int, campo: String, texto: String) {
        val v = io.github.fogonapps.forzudo.datos.numero(texto)
        cambiarSerie(id, i) { if (campo == "kg") it.copy(kg = v) else it.copy(reps = v) }
    }

    fun copiarAnterior(id: String, i: Int) {
        val e = _estado.value ?: return
        val p = L.anterior(e, id)?.getOrNull(i) ?: return
        cambiarSerie(id, i) { s -> s.copy(kg = p.kg ?: s.kg, reps = p.reps ?: s.reps) }
    }

    fun agregarSerie(id: String) = cambiar { e ->
        val a = e.active ?: return@cambiar e
        val l = a.entries[id].orEmpty()
        e.copy(active = a.copy(entries = a.entries + (id to (l + Serie(kg = l.lastOrNull()?.kg)))))
    }

    /** Marca o desmarca una serie. Igual que toggleSet() en la web. */
    fun marcar(id: String, i: Int) {
        val e = _estado.value ?: return
        val a = e.active ?: return
        val s = a.entries[id]?.getOrNull(i) ?: return
        val c = bib.cfg(a.items.firstOrNull { it.id == id } ?: Item(id))
        if (s.done) { cambiarSerie(id, i) { it.copy(done = false) }; return }
        val previa = L.anterior(e, id)?.getOrNull(i)
        var kg = s.kg ?: previa?.kg
        var reps = s.reps ?: previa?.reps
        if (reps == null) { avisar("Escribe las " + if (c.e.kind == "s") "segundos" else "repeticiones"); return }
        if (c.e.kind == "w" && kg == null) { avisar("Escribe el peso en kg"); return }
        if (c.e.kind != "w") kg = s.kg
        val mk = L.maxKg(e, id); val mr = L.maxReps(e, id)
        if (c.e.kind == "w" && mk != null && kg != null && kg > mk) avisar("Nuevo récord: ${fk(kg)} kg")
        else if (c.e.kind != "w" && mr != null && reps > mr) avisar("Nuevo récord: ${fk(reps)} ${L.unidad(c.e)}")
        cambiar { st ->
            val act = st.active!!
            val l = act.entries.getValue(id).toMutableList()
            l[i] = l[i].copy(kg = kg, reps = reps, done = true)
            if (i + 1 < l.size && l[i + 1].kg == null && kg != null) l[i + 1] = l[i + 1].copy(kg = kg)
            st.copy(active = act.copy(entries = act.entries + (id to l)))
        }
        empezarDescanso(c.rest)
        Avisos.vibrar(getApplication(), 30)
    }

    fun terminar() {
        val e = _estado.value ?: return
        val a = e.active ?: return
        val entries = a.entries.mapValues { (_, l) -> l.filter { it.done }.map { Serie(it.kg, it.reps, true) } }.filterValues { it.isNotEmpty() }
        if (entries.isEmpty()) { avisar("Marca al menos una serie con ✓ para guardar"); return }
        val records = entries.mapNotNull { (k, l) ->
            val ej = bib.ex[k] ?: return@mapNotNull null
            if (ej.kind != "w") return@mapNotNull null
            val m = L.maxKg(e, k); val top = l.maxOf { it.kg ?: 0.0 }
            if (m != null && top > m) "${ej.n}: ${fk(top)} kg" else null
        }
        val ms = System.currentTimeMillis() - a.start
        val antes = bib.musculos.associate { m -> m.id to L.rangoMusculo(e, m)?.let { floor(it.rango.s * 3 + 1e-9) } }
        val titulo = a.title ?: L.tituloRutina(Sesion(a.id, a.date, a.day), L.dias(e))
        val nuevo = e.copy(sessions = e.sessions + Sesion(a.id, a.date, a.day, titulo, ms, entries), active = null)
        val subidas = bib.musculos.mapNotNull { m ->
            val r = L.rangoMusculo(nuevo, m) ?: return@mapNotNull null
            val b = antes[m.id]
            if (b == null || floor(r.rango.s * 3 + 1e-9) > b) SubioRango(m.n, r.rango) else null
        }
        cambiar { nuevo }
        pararDescanso()
        _pila.value = listOf(Pantalla.Resumen(ResumenEntreno(titulo, a.date, ms, L.volumen(entries), L.series(entries), subidas, records)))
        _pestana.value = Pestana.INICIO
    }

    fun descartar() {
        cambiar { it.copy(active = null) }
        pararDescanso()
        _pila.value = _pila.value.filter { it !is Pantalla.Vivo }
    }

    /* ----- Descanso ----- */

    fun empezarDescanso(seg: Int) {
        val d = Descanso(System.currentTimeMillis() + seg * 1000L, seg)
        _descanso.value = d
        programarFinDescanso(d)
    }

    fun ajustarDescanso(seg: Int) {
        val d = _descanso.value ?: return
        val n = Descanso(d.fin + seg * 1000L, maxOf(1, d.total + seg))
        _descanso.value = n
        programarFinDescanso(n)
    }

    private fun programarFinDescanso(d: Descanso) {
        Avisos.programar(getApplication(), d.fin)
        descansoJob?.cancel()
        descansoJob = viewModelScope.launch {
            delay(maxOf(0, d.fin - System.currentTimeMillis()))
            _descanso.value = null
            avisar("Descanso terminado")
        }
    }

    fun pararDescanso() {
        descansoJob?.cancel(); _descanso.value = null
        Avisos.cancelar(getApplication())
    }

    /* ----- Respaldo ----- */

    fun textoRespaldo(): String = _estado.value?.let { JsonGym.encodeToString(Estado.serializer(), L.paraNube(it)) } ?: ""

    /** Lee un respaldo. Devuelve el estado si es válido, para confirmar antes de reemplazar. */
    fun leerRespaldo(texto: String): Estado? = runCatching {
        JsonGym.decodeFromString(Estado.serializer(), texto)
    }.getOrNull()?.takeIf { it.sessions.isNotEmpty() || it.routines != null || it.body.isNotEmpty() }

    fun importar(d: Estado) {
        val activo = _estado.value?.active
        val nombre = _estado.value?.nombre
        cambiar { L.normalizar(d).copy(active = activo, nombre = d.nombre ?: nombre) }
        avisar("Datos importados")
    }

    /* ----- Sincronización con GitHub ----- */

    fun conectar(token: String) {
        val t = token.trim()
        if (t.length < 20) { avisar("Pega el token completo"); return }
        avisar("Conectando…")
        viewModelScope.launch {
            try {
                val u = ClienteGist(t).usuario()
                caja.token = t; caja.usuario = u
                _sync.value = Sync(EstadoSync.EN_NUBE, usuario = u)
                sincronizar()
            } catch (e: ErrorGitHub) {
                avisar(if (e.codigo == 401) "Ese token no es válido" else "No se pudo conectar con GitHub")
            } catch (e: Exception) {
                avisar("No se pudo conectar con GitHub")
            }
        }
    }

    fun desconectar() {
        caja.borrarTodo(); caja.token = null
        _sync.value = Sync(EstadoSync.APAGADA)
    }

    fun sincronizar() {
        val token = caja.token ?: return
        if (sincronizando) { sincronizarOtraVez = true; return }
        sincronizando = true
        _sync.value = _sync.value.copy(estado = EstadoSync.SINCRONIZANDO, mensaje = null)
        viewModelScope.launch {
            try {
                val c = ClienteGist(token)
                val local = _estado.value ?: return@launch
                val gist = caja.gist ?: c.buscarOCrear(JsonGym.encodeToString(Estado.serializer(), L.paraNube(local))).also { caja.gist = it }
                val remoto = c.leer(gist)?.let { runCatching { JsonGym.decodeFromString(Estado.serializer(), it) }.getOrNull() }
                if (remoto != null) {
                    val actual = _estado.value ?: local
                    _estado.value = L.normalizar(L.unir(actual, remoto))
                    guardarYa()
                }
                c.escribir(gist, JsonGym.encodeToString(Estado.serializer(), L.paraNube(_estado.value!!)))
                caja.ultima = System.currentTimeMillis()
                _sync.value = Sync(EstadoSync.LISTA, usuario = caja.usuario, ultima = caja.ultima)
            } catch (e: ErrorGitHub) {
                if (e.codigo == 404) caja.gist = null
                _sync.value = _sync.value.copy(
                    estado = EstadoSync.ERROR,
                    mensaje = when (e.codigo) {
                        401 -> "El token no es válido o ya venció."
                        403, 404 -> "El token no tiene permiso para gists."
                        else -> "No se pudo conectar con GitHub."
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
}
