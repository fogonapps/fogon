package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.*
import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fogonapps.ahorrador.datos.Abono
import io.github.fogonapps.ahorrador.datos.Categoria
import io.github.fogonapps.ahorrador.datos.Cuenta
import io.github.fogonapps.ahorrador.datos.Deuda
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Fijo
import io.github.fogonapps.ahorrador.datos.JsonAhorrador
import io.github.fogonapps.ahorrador.datos.Meta
import io.github.fogonapps.ahorrador.datos.Movimiento
import io.github.fogonapps.ahorrador.datos.Sistema
import io.github.fogonapps.ahorrador.datos.TipoCuenta
import io.github.fogonapps.ahorrador.datos.TipoMovimiento
import io.github.fogonapps.ahorrador.datos.gmf
import io.github.fogonapps.ahorrador.datos.mesDe
import io.github.fogonapps.identidad.Idioma
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

/** Las pantallas que se abren encima de las pestañas. */
sealed interface Pantalla {
    data class Anotar(val tipo: TipoMovimiento = TipoMovimiento.GASTO, val editar: String? = null, val aCuenta: String? = null, val banco: String? = null) : Pantalla
    data object Cuentas : Pantalla
    /** Todos los movimientos, con búsqueda; [categoria] y [mes] ("AAAA-MM") son filtros con los que se abre. */
    data class Movimientos(val categoria: String? = null, val mes: String? = null) : Pantalla
    data class Cuenta(val id: String) : Pantalla
    data class EditarCuenta(val id: String?) : Pantalla
    data object Fijos : Pantalla
    data class EditarFijo(val id: String?) : Pantalla
    data object Deudas : Pantalla
    data class EditarDeuda(val id: String?, val meDeben: Boolean = true) : Pantalla
    data class EditarMeta(val id: String?) : Pantalla
    data class EditarSobre(val id: String?) : Pantalla
    data class Cierre(val mes: String) : Pantalla
    data class ImportarExtracto(val filas: List<io.github.fogonapps.ahorrador.datos.FilaExtracto>) : Pantalla
    data object Bancos : Pantalla
    data object Ajustes : Pantalla
    data object EditarPerfil : Pantalla
    data object Perfil : Pantalla
    data object AcercaDe : Pantalla
    data object Manifiesto : Pantalla
    data object Apariencia : Pantalla
    data object Licencias : Pantalla
    data object Preguntas : Pantalla
    data object Reporte : Pantalla
    /** AL-04: anotar varios movimientos escritos o pegados en un texto. */
    data object AnotarVarios : Pantalla
    /** AL-04: «¿Me alcanza?», con un valor ya escrito o vacío. */
    data class MeAlcanza(val monto: Long? = null) : Pantalla
    /** AL-04: cómo le fue a cada sobre en los meses terminados y las metas cumplidas. */
    data object Historial : Pantalla
    /* ----- Cuenta y red (D-032), como La Cucha y El Forzudo ----- */
    data object CuentaRed : Pantalla
    data class Seguridad(val ajuste: io.github.fogonapps.social.ui.AjusteSocial) : Pantalla
    data class Gente(val usuario: String, val seguidores: Boolean) : Pantalla
    data object Transparencia : Pantalla
    /** Avisos, moderación, una publicación, un perfil ajeno o una etiqueta de «Tu gente». */
    data class Social(val destino: String = "red", val id: Long = 0, val usuario: String = "") : Pantalla
    /** Compartir un logro; con [clave], uno ya elegido (o actualizar uno publicado). */
    data class CompartirLogro(val clave: String? = null) : Pantalla
}

enum class Pestana { INICIO, GENTE, RESUMEN, SOBRES, METAS }

/** La red de La Alcancía: rutas /v2/apps/alcancia y correos con su nombre. */
val CONFIGURACION_RED = io.github.fogonapps.social.ConfiguracionSocial("alcancia", "La Alcancía", "LaAlcancia-Fogon")

class AhorradorViewModel(app: Application) : AndroidViewModel(app) {
    /** Las frases del saludo, como en El Cajón: las propias (al_), las de la gente de La Alcancía y los votos. */
    val frases = io.github.fogonapps.social.FrasesSaludo(app.getSharedPreferences("frases", android.content.Context.MODE_PRIVATE), viewModelScope) {
        // Sin cuenta, La Alcancía no se conecta a nada (ni para las frases de la comunidad).
        if (red.usuario.value != null) red.clienteFrases() else null
    }
    private val ahorradorApp = app as AhorradorApp
    val idPerfil = ahorradorApp.perfiles.lista.activo
    private val almacen = ahorradorApp.almacenPerfil(idPerfil)
    val perfiles get() = ahorradorApp.perfiles
    private val sesion = io.github.fogonapps.social.SesionApp(app, ahorradorApp.perfiles, idPerfil, "alcancia")
    /** La cuenta y «Tu gente»: solo logros y metas en porcentaje (D-032), sin chat. */
    val red = io.github.fogonapps.social.RedApp(ahorradorApp.perfiles, sesion, viewModelScope, CONFIGURACION_RED,
        io.github.fogonapps.social.TextosRed(R.string.cuenta_error, R.string.cuenta_perfil_cambio, R.string.cuenta_operacion_lista, R.string.cuenta_otro_perfil),
        { avisar(tx(it)) }, ::cambiarPerfil, { io.github.fogonapps.ahorrador.datos.cabeLogro(it) }, "logros-publicados.json",
        listOf(io.github.fogonapps.ahorrador.datos.PARTE_METAS))
    /** D-033: el cuaderno se guarda solo en la cuenta, cifrado con la contraseña, y vuelve al reinstalar. */
    private val copia = io.github.fogonapps.social.CopiaEnCuenta(red, app.getSharedPreferences("copia-alcancia-p$idPerfil", Context.MODE_PRIVATE), viewModelScope,
        leer = { _estado.value?.let { io.github.fogonapps.social.ui.conPalabrasOcultas(io.github.fogonapps.identidad.conApariencia(JsonAhorrador.encodeToString(Estado.serializer(), it), io.github.fogonapps.identidad.AlmacenApariencia.de(app)), app) } },
        aplicar = { texto ->
            io.github.fogonapps.identidad.aplicarApariencia(texto, io.github.fogonapps.identidad.AlmacenApariencia.de(app))
            io.github.fogonapps.social.ui.aplicarPalabrasOcultas(texto, app)
            val remoto = L.normalizar(JsonAhorrador.decodeFromString(Estado.serializer(), texto))
            _estado.value = almacen.updateData { remoto }
            WidgetPlata.actualizar(getApplication())
        },
        // Vacío por el contenido, no por la bienvenida: al reinstalar, el cuaderno de la cuenta gana al recién empezado.
        vacio = { _estado.value?.let { e -> e.movimientos.isEmpty() && e.metas.isEmpty() && e.fijos.isEmpty() && e.deudas.isEmpty() } ?: true },
        marca = { runCatching { JsonAhorrador.decodeFromString(Estado.serializer(), it).updatedAt }.getOrDefault(0L) })
    val L get() = ahorradorApp.logica
    private val prefsUi = app.getSharedPreferences("ui", Context.MODE_PRIVATE)

    private val _estado = MutableStateFlow<Estado?>(null)
    val estado: StateFlow<Estado?> = _estado

    private val _pila = MutableStateFlow<List<Pantalla>>(emptyList())
    val pila: StateFlow<List<Pantalla>> = _pila
    private val _pestana = MutableStateFlow(Pestana.INICIO)
    val pestana: StateFlow<Pestana> = _pestana

    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes = _mensajes.receiveAsFlow()

    private val _candado = MutableStateFlow(prefsUi.getBoolean("candado", false))
    /* El tema va con la cuenta: al cambiarlo, la copia se vuelve a guardar. */
    init {
        viewModelScope.launch {
            var primero = true
            io.github.fogonapps.identidad.AlmacenApariencia.de(getApplication()).estado.collect { if (primero) primero = false else copia.cambio() }
        }
        viewModelScope.launch {
            var primeraVez = true
            io.github.fogonapps.social.ui.PalabrasOcultasRed.de(getApplication()).lista.collect { if (primeraVez) primeraVez = false else copia.cambio() }
        }
    }

    /* X-10: avisos con la app cerrada: lo que se ve al abrir no vuelve a avisar, y la revisión sigue a la sesión. */
    init {
        red.alContarAvisosRed = { io.github.fogonapps.social.ui.Avisos.visto(getApplication(), idPerfil, avisos = it) }
        viewModelScope.launch { red.usuario.collect { io.github.fogonapps.social.ui.Avisos.programar(getApplication()) } }
    }
    init { io.github.fogonapps.ahorrador.datos.Formato.ocultar = prefsUi.getBoolean("ocultar", false) }
    val candado: StateFlow<Boolean> = _candado

    private var guardado: Job? = null

    init {
        viewModelScope.launch {
            almacen.data.collect { _estado.value = it }
        }
        viewModelScope.launch { almacen.data.first(); ahorradorApp.confirmarCuaderno(); anotarFijos(); copia.sincronizar() }
    }

    fun hoy(): LocalDate = LocalDate.now()
    fun mesActual(): String = mesDe(hoy())

    /** Un texto de strings.xml en el idioma de la app. */
    fun tx(id: Int, vararg a: Any): String = Idioma.envolver(getApplication()).getString(id, *a)
    fun txPlural(id: Int, n: Int): String = Idioma.envolver(getApplication()).resources.getQuantityString(id, n, n)
    private fun avisar(m: String) { _mensajes.trySend(m) }

    private fun nuevoId(prefijo: String) = prefijo + System.currentTimeMillis().toString(36) + Random.nextInt(1296).toString(36)

    /** Pantallas y servicio transforman el mismo estado mediante transacciones de DataStore. */
    private fun cambiar(despues: (Estado) -> Unit = {}, f: (Estado) -> Estado) {
        guardado = viewModelScope.launch {
            val e = almacen.updateData { f(it).copy(updatedAt = System.currentTimeMillis()) }
            _estado.value = e
            despues(e)
            BancoAvisos.actualizar(getApplication(), e)
            WidgetPlata.actualizar(getApplication())
            copia.cambio()
        }
    }

    suspend fun guardarYa() { guardado?.join() }

    fun elegirBanco(banco: Banco, si: Boolean) {
        cambiar { e -> e.copy(notificaciones = e.notificaciones.copy(
            elegidos = if (si) e.notificaciones.elegidos + banco else e.notificaciones.elegidos - banco)) }
    }
    fun bancoSolo(banco: Banco, si: Boolean) {
        cambiar { e -> e.copy(notificaciones = e.notificaciones.copy(
            solos = if (si) e.notificaciones.solos + banco else e.notificaciones.solos - banco)) }
    }
    fun enlazarAviso(huella: String, cuenta: String?) {
        cambiar { e ->
            val a = e.notificaciones.registros.firstOrNull { it.aviso.huella == huella && !it.descartado && it.movimiento == null }?.aviso ?: return@cambiar e
            if (e.cuentaEnlazada(a) != null) return@cambiar e
            val nueva = Cuenta(nuevoId("c"), tx(a.banco.nombreBanco), TipoCuenta.BANCO, banco = a.banco)
            val conCuenta = if (cuenta == null) e.copy(cuentas = e.cuentas + nueva) else e
            val enlazado = enlazarBanco(conCuenta, a, cuenta ?: nueva.id)
            if (a.banco in enlazado.notificaciones.solos) confirmarBanco(enlazado, huella, automatico = true) else enlazado
        }
    }
    fun confirmarAviso(huella: String, editado: Movimiento? = null) { cambiar { confirmarBanco(it, huella, editado) } }
    fun descartarAviso(huella: String) { cambiar { descartarBanco(it, huella) } }
    fun deshacerBanco(id: String) {
        cambiar { e -> e.copy(movimientos = e.movimientos.filter { it.id != id },
            notificaciones = e.notificaciones.copy(registros = e.notificaciones.registros.map {
                if (it.movimiento == id) it.copy(movimiento = null, descartado = true, automatico = false) else it
            })) }
    }
    fun olvidarEnlace(clave: String) { cambiar { it.copy(notificaciones = it.notificaciones.copy(enlaces = it.notificaciones.enlaces - clave)) } }

    /* ----- Navegación ----- */

    fun abrir(p: Pantalla) { _pila.value = _pila.value + p }
    fun cerrar() { _pila.value = _pila.value.dropLast(1) }
    fun irA(p: Pestana) { _pila.value = emptyList(); _pestana.value = p }
    fun atras() { if (_pila.value.isNotEmpty()) cerrar() else _pestana.value = Pestana.INICIO }

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
    /** Termina lo pendiente de este perfil y activa otro; la actividad arma un ViewModel nuevo para él. */
    fun cambiarPerfil(id: String) {
        if (id == idPerfil || cambiandoPerfil) return
        cambiandoPerfil = true
        viewModelScope.launch {
            try {
                red.invalidar()
                guardado?.join()
                val actual = kotlinx.coroutines.currentCoroutineContext()[Job]
                viewModelScope.coroutineContext[Job]?.children?.filter { it != actual }?.toList()?.forEach { it.cancelAndJoin() }
                if (retirarAlCambiar) {
                    if (idPerfil == "1") io.github.fogonapps.ahorrador.datos.migracionPerfiles(getApplication()).confirmarArranque()
                    ahorradorApp.cerrarAlmacen(idPerfil)
                    perfiles.cambiar(id)
                    perfiles.quitar(idPerfil) { perfil ->
                        io.github.fogonapps.social.SesionApp.preferenciasDe("alcancia", perfil).forEach {
                            check(getApplication<Application>().getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit())
                        }
                    }
                }
                ahorradorApp.activarPerfil(id)
                WidgetPlata.actualizar(getApplication())
            } catch (x: java.io.IOException) {
                cambiandoPerfil = false
                avisar(tx(R.string.cuenta_error))
            }
        }
    }

    /* ----- Tutorial ----- */

    /** Deja la app lista: nombre, sistema, categorías en el idioma de la app y las cuentas con su saldo de hoy. */
    fun empezar(nombre: String, sistema: Sistema, efectivo: Long, banco: Long, billetera: Long, deudaTarjeta: Long?, categorias: Set<String>? = null) {
        cambiar { e ->
            val cuentas = buildList {
                add(Cuenta("efectivo", tx(R.string.cuenta_efectivo), TipoCuenta.EFECTIVO, efectivo))
                if (banco > 0) add(Cuenta("banco", tx(R.string.cuenta_banco), TipoCuenta.BANCO, banco))
                if (billetera > 0) add(Cuenta("billetera", tx(R.string.cuenta_billetera), TipoCuenta.BILLETERA, billetera))
                if (deudaTarjeta != null) add(Cuenta("tarjeta", tx(R.string.cuenta_tarjeta), TipoCuenta.CREDITO, -deudaTarjeta,
                    io.github.fogonapps.ahorrador.datos.Credito(cupo = 0, diaCorte = 15, diaPago = 30)))
            }
            e.copy(nombre = nombre.trim().ifEmpty { null }, sistema = sistema, cuentas = cuentas,
                categorias = Categorias.base(getApplication()).filter { categorias == null || it.id in categorias || it.id in Categorias.SIEMPRE }, listo = true)
        }
    }

    /* ----- Movimientos ----- */

    /**
     * Guarda un gasto, ingreso, traspaso o ahorro. Con el 4×1000 activado, si la plata sale de una
     * cuenta de banco (gasto o traspaso), anota también el impuesto.
     */
    fun guardarMovimiento(m: Movimiento, alGuardar: (Estado) -> Unit = {}): Boolean {
        val e = _estado.value ?: return false
        if (m.monto <= 0) { avisar(tx(R.string.aviso_monto)); return false }
        if (m.tipo == TipoMovimiento.TRASPASO && (m.aCuenta == null || m.aCuenta == m.cuenta)) { avisar(tx(R.string.aviso_otra_cuenta)); return false }
        val nuevo = m.id.isEmpty()
        val mov = if (nuevo) m.copy(id = nuevoId("m")) else m
        val origen = e.cuentas.firstOrNull { it.id == mov.cuenta }
        val impuesto = if (nuevo && e.gmf && origen?.tipo == TipoCuenta.BANCO &&
            (mov.tipo == TipoMovimiento.GASTO || mov.tipo == TipoMovimiento.TRASPASO) && gmf(mov.monto) > 0)
            Movimiento(nuevoId("m"), mov.fecha, TipoMovimiento.GASTO, gmf(mov.monto), mov.cuenta, categoria = "impuestos", nota = "4×1000")
        else null
        cambiar(alGuardar) { st ->
            val lista = if (nuevo) st.movimientos + mov else st.movimientos.map { if (it.id == mov.id) mov else it }
            val avisos = st.notificaciones.registros.filter { it.movimiento == mov.id }
            val aprendidas = if (mov.categoria != null) avisos.associate { claveContraparte(it.aviso) to mov.categoria } else emptyMap()
            st.copy(movimientos = lista + listOfNotNull(impuesto), notificaciones = st.notificaciones.copy(categorias = st.notificaciones.categorias + aprendidas))
        }
        avisar(tx(if (nuevo) R.string.aviso_guardado else R.string.aviso_actualizado))
        return true
    }

    /** Guarda de una vez lo leído en «Anotar varios» (con el 4×1000 si toca, como un movimiento suelto). */
    fun guardarVarios(borradores: List<io.github.fogonapps.ahorrador.datos.Borrador>, cuenta: String, fecha: String) {
        val e = _estado.value ?: return
        val buenos = borradores.filter { it.monto > 0 }
        if (buenos.isEmpty()) return
        val origen = e.cuentas.firstOrNull { it.id == cuenta }
        val nuevos = buenos.flatMap { b ->
            val m = Movimiento(nuevoId("m"), fecha, b.tipo, b.monto, cuenta, categoria = b.categoria ?: if (b.tipo == TipoMovimiento.INGRESO) "extra" else "otros", nota = b.nota)
            listOfNotNull(m, if (e.gmf && origen?.tipo == TipoCuenta.BANCO && b.tipo == TipoMovimiento.GASTO && gmf(b.monto) > 0)
                Movimiento(nuevoId("m"), fecha, TipoMovimiento.GASTO, gmf(b.monto), cuenta, categoria = "impuestos", nota = "4×1000") else null)
        }
        cambiar { it.copy(movimientos = it.movimientos + nuevos) }
        avisar(txPlural(R.plurals.varios_guardados, buenos.size))
        cerrar()
    }

    /** El ojo de Inicio: oculta o muestra los montos en toda la app (se recuerda en este teléfono). */
    fun alternarMontos() {
        io.github.fogonapps.ahorrador.datos.Formato.ocultar = !io.github.fogonapps.ahorrador.datos.Formato.ocultar
        prefsUi.edit { putBoolean("ocultar", io.github.fogonapps.ahorrador.datos.Formato.ocultar) }
    }

    fun borrarMovimiento(id: String) {
        cambiar { e -> e.copy(movimientos = e.movimientos.filter { m -> m.id != id },
            notificaciones = e.notificaciones.copy(registros = e.notificaciones.registros.map { if (it.movimiento == id) it.copy(descartado = true) else it })) }
        avisar(tx(R.string.aviso_borrado))
    }

    /** El último gasto, para "igual que ayer". */
    fun ultimoGasto(): Movimiento? = _estado.value?.movimientos?.lastOrNull { it.tipo == TipoMovimiento.GASTO && it.fijo == null && it.nota != "4×1000" }

    /* ----- Cuentas ----- */

    fun guardarCuenta(c: Cuenta) {
        cambiar { e ->
            if (c.id.isEmpty()) e.copy(cuentas = e.cuentas + c.copy(id = nuevoId("c")))
            else e.copy(cuentas = e.cuentas.map { if (it.id == c.id) c else it })
        }
        avisar(tx(R.string.aviso_guardado))
    }

    fun archivarCuenta(id: String) {
        val e = _estado.value ?: return
        if (e.cuentas.count { !it.archivada } <= 1) { avisar(tx(R.string.aviso_una_cuenta)); return }
        cambiar { st -> st.copy(cuentas = st.cuentas.map { if (it.id == id) it.copy(archivada = true) else it }) }
        cerrar()
    }

    /* ----- Sobres ----- */

    fun asignar(mes: String, categoria: String, monto: Long) {
        cambiar { e ->
            val m = e.sobres[mes].orEmpty().toMutableMap()
            if (monto <= 0) m.remove(categoria) else m[categoria] = monto
            e.copy(sobres = e.sobres + (mes to m))
        }
    }

    /** Cubre lo que se pasó un sobre con plata de otro. */
    fun cubrir(mes: String, desde: String, hacia: String, monto: Long) {
        cambiar { e ->
            val m = e.sobres[mes].orEmpty().toMutableMap()
            m[desde] = maxOf(0, (m[desde] ?: 0) - monto)
            m[hacia] = (m[hacia] ?: 0) + monto
            e.copy(sobres = e.sobres + (mes to m))
        }
        avisar(tx(R.string.aviso_cubierto))
    }

    /** Copia los sobres del mes anterior a este mes (lo que no se haya asignado todavía). */
    fun copiarSobresAnteriores(mes: String) {
        val e = _estado.value ?: return
        val antes = e.sobres[io.github.fogonapps.ahorrador.datos.mesAnterior(mes)].orEmpty()
        if (antes.isEmpty()) { avisar(tx(R.string.aviso_sin_sobres_antes)); return }
        cambiar { st -> st.copy(sobres = st.sobres + (mes to (antes + st.sobres[mes].orEmpty()))) }
        avisar(tx(R.string.aviso_sobres_copiados))
    }

    fun cambiarSistema(s: Sistema) { cambiar { it.copy(sistema = s) } }
    fun cambiarReparto(r: List<Int>) { if (r.sum() == 100) cambiar { it.copy(reparto = r) } else avisar(tx(R.string.aviso_reparto)) }

    /* ----- Fijos ----- */

    fun guardarFijo(f: Fijo) {
        cambiar(despues = { anotarFijos() }) { e ->
            if (f.id.isEmpty()) e.copy(fijos = e.fijos + f.copy(id = nuevoId("f")))
            else e.copy(fijos = e.fijos.map { if (it.id == f.id) f else it })
        }
        avisar(tx(R.string.aviso_guardado))
    }

    fun borrarFijo(id: String) { cambiar { it.copy(fijos = it.fijos.filter { f -> f.id != id }) }; cerrar() }

    /** Marca un fijo como pagado este mes: lo anota como gasto. */
    fun pagarFijo(f: Fijo) {
        guardarMovimiento(Movimiento("", hoy().toString(), TipoMovimiento.GASTO, f.monto, f.cuenta, categoria = f.categoria, nota = f.nombre, fijo = f.id))
    }

    /** Anota solos los fijos automáticos que ya vencieron. Corre al abrir la app. */
    fun anotarFijos() {
        val e = _estado.value ?: return
        val pend = L.aAnotar(e, hoy())
        if (pend.isEmpty()) return
        val mes = mesDe(hoy())
        cambiar { st ->
            st.copy(movimientos = st.movimientos + L.aAnotar(st, hoy()).map { f ->
                Movimiento(nuevoId("m"), L.vence(f, mes).toString(), TipoMovimiento.GASTO, f.monto, f.cuenta, categoria = f.categoria, nota = f.nombre, fijo = f.id)
            })
        }
        avisar(txPlural(R.plurals.fijos_anotados, pend.size))
    }

    /* ----- Sobres (las categorías de gasto) ----- */

    fun guardarCategoria(c: Categoria) {
        cambiar { e ->
            if (c.id.isEmpty()) e.copy(categorias = e.categorias + c.copy(id = nuevoId("c")))
            else e.copy(categorias = e.categorias.map { if (it.id == c.id) c else it })
        }
        avisar(tx(R.string.aviso_guardado))
    }

    /** Un sobre con gastos o pagos fijos no se borra: se perderían de qué fueron. */
    fun categoriaEnUso(e: Estado, id: String) = e.movimientos.any { it.categoria == id } || e.fijos.any { it.categoria == id }

    fun borrarCategoria(id: String) {
        cambiar { e -> e.copy(categorias = e.categorias.filter { it.id != id }, sobres = e.sobres.mapValues { (_, m) -> m - id }) }
        cerrar()
    }

    /* ----- Extracto del banco (CSV) ----- */

    /** La categoría que se le sugiere a una fila del extracto: la de un movimiento anterior con la misma nota, o por palabras. */
    fun categoriaSugerida(e: Estado, fila: io.github.fogonapps.ahorrador.datos.FilaExtracto): String {
        val nota = normalizar(fila.descripcion)
        e.movimientos.lastOrNull { it.categoria != null && normalizar(it.nota) == nota }?.let { return it.categoria!! }
        if (fila.monto > 0) return if (PALABRAS_SUELDO.any { nota.contains(it) }) "sueldo" else "extra"
        return PALABRAS.entries.firstOrNull { (_, ps) -> ps.any { nota.contains(it) } }?.key?.takeIf { k -> e.categorias.any { it.id == k } } ?: "otros"
    }

    /** Cruza las filas elegidas por fecha, sentido, monto y cuenta sin consumir una coincidencia dos veces. */
    fun importarExtracto(filas: List<Pair<io.github.fogonapps.ahorrador.datos.FilaExtracto, String>>, cuenta: String) {
        cambiar { st ->
            val resultado = importarFilasExtracto(st, filas, cuenta) { nuevoId("m") }
            val n = resultado.movimientos.count { nuevo -> st.movimientos.none { it.id == nuevo.id } }
            avisar(txPlural(R.plurals.extracto_importados, n))
            resultado
        }
        cerrar()
    }

    /* ----- Reto de ahorro ----- */

    /** La semana del reto en la que va la persona (1 a 52). */
    fun semanaReto(r: io.github.fogonapps.ahorrador.datos.Reto): Int =
        (java.time.temporal.ChronoUnit.WEEKS.between(LocalDate.parse(r.inicio), hoy()).toInt() + 1).coerceIn(1, r.semanas)

    fun empezarReto(base: Long) {
        val meta = Meta(nuevoId("t"), tx(R.string.reto_nombre), io.github.fogonapps.ahorrador.datos.totalReto(base, 52), color = 0xFFF5B547, icono = "moneda")
        cambiar { it.copy(metas = it.metas + meta, reto = io.github.fogonapps.ahorrador.datos.Reto(hoy().toString(), base, meta.id)) }
        avisar(tx(R.string.reto_empezado))
    }

    fun dejarReto() { cambiar { it.copy(reto = null) } }

    /* ----- Recordatorio diario ----- */

    private val _recordatorio = MutableStateFlow(prefsUi.getInt("recordatorio", -1))
    /** La hora del recordatorio diario para anotar los gastos, o -1 si está apagado. */
    val recordatorio: StateFlow<Int> = _recordatorio

    fun ponerRecordatorio(hora: Int) {
        prefsUi.edit().putInt("recordatorio", hora).apply()
        _recordatorio.value = hora
        Avisos.programarRecordatorio(getApplication(), hora)
    }

    /* ----- Metas ----- */

    fun guardarMeta(m: Meta) {
        cambiar { e ->
            if (m.id.isEmpty()) e.copy(metas = e.metas + m.copy(id = nuevoId("t")))
            else e.copy(metas = e.metas.map { if (it.id == m.id) m else it })
        }
        avisar(tx(R.string.aviso_guardado))
    }

    fun borrarMeta(id: String) { cambiar { it.copy(metas = it.metas.filter { m -> m.id != id }) }; cerrar() }

    fun abonarMeta(meta: Meta, monto: Long, cuenta: String) {
        guardarMovimiento(Movimiento("", hoy().toString(), TipoMovimiento.AHORRO, monto, cuenta, meta = meta.id, nota = meta.nombre)) { e ->
            if (L.ahorradoEn(e, meta.id) >= meta.objetivo) avisar(MARCA_LOGRO + tx(R.string.aviso_meta_cumplida, meta.nombre))
        }
    }

    /* ----- Deudas ----- */

    fun guardarDeuda(d: Deuda) {
        cambiar { e ->
            if (d.id.isEmpty()) e.copy(deudas = e.deudas + d.copy(id = nuevoId("d")))
            else e.copy(deudas = e.deudas.map { if (it.id == d.id) d else it })
        }
        avisar(tx(R.string.aviso_guardado))
    }

    fun borrarDeuda(id: String) { cambiar { it.copy(deudas = it.deudas.filter { d -> d.id != id }) }; cerrar() }

    fun abonarDeuda(d: Deuda, monto: Long) {
        if (monto <= 0) { avisar(tx(R.string.aviso_monto)); return }
        cambiar(despues = { e ->
            val actual = e.deudas.firstOrNull { it.id == d.id }
            if (actual != null) avisar(if (L.pendiente(actual) == 0L) MARCA_LOGRO + tx(R.string.aviso_deuda_saldada) else tx(R.string.aviso_abono))
        }) { e -> e.copy(deudas = e.deudas.map { if (it.id == d.id) it.copy(abonos = it.abonos + Abono(hoy().toString(), monto)) else it }) }
    }

    /* ----- Ajustes ----- */

    fun guardarPerfil(nombre: String, bio: String) {
        cambiar { it.copy(nombre = io.github.fogonapps.perfil.recortarNombrePerfil(nombre.trim()).ifEmpty { null }, bio = io.github.fogonapps.perfil.recortarBioPerfil(bio.trim())) }
        cerrar()
    }

    fun imagenPerfil(portada: Boolean, imagen: String) {
        cambiar { if (portada) it.copy(portadaPerfil = imagen) else it.copy(fotoPerfil = imagen) }
    }

    fun ponerNombre(n: String) { cambiar { it.copy(nombre = n.trim().ifEmpty { null }) } }
    fun cambiarGmf(v: Boolean) { cambiar { it.copy(gmf = v) } }
    fun cambiarMetaAhorro(v: Long?) { cambiar { it.copy(metaAhorroMes = v?.takeIf { it > 0 }) } }
    fun notaCierre(mes: String, t: String) { cambiar { it.copy(cierres = it.cierres + (mes to t.take(500))) } }

    fun cambiarCandado(v: Boolean) { prefsUi.edit { putBoolean("candado", v) }; _candado.value = v }

    fun cambiarIdioma(codigo: String) {
        Idioma.elegir(getApplication(), codigo)
        ahorradorApp.aplicarIdioma()
    }

    /* ----- Respaldo ----- */

    fun exportarJson(): String = JsonAhorrador.encodeToString(Estado.serializer(), _estado.value ?: Estado())

    fun leerRespaldo(texto: String): Estado? = leerRespaldoAhorrador(texto)

    fun importar(e: Estado) {
        cambiar { L.normalizar(e) }
        _pila.value = emptyList(); _pestana.value = Pestana.INICIO
        avisar(tx(R.string.aviso_importado))
    }

    /** Todos los movimientos en CSV (fecha, tipo, monto, cuenta, categoría, nota), para una hoja de cálculo. */
    fun exportarCsv(): String {
        val e = _estado.value ?: return ""
        val cuentas = e.cuentas.associate { it.id to it.nombre }
        val cats = e.categorias.associate { it.id to it.nombre }
        fun c(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val filas = e.movimientos.sortedBy { it.fecha }.map { m ->
            listOf(m.fecha, m.tipo.name.lowercase(), m.monto.toString(), c(cuentas[m.cuenta] ?: m.cuenta),
                c(m.aCuenta?.let { cuentas[it] ?: it } ?: ""), c(m.categoria?.let { cats[it] ?: it } ?: ""), c(m.nota)).joinToString(",")
        }
        return (listOf("fecha,tipo,monto,cuenta,a_cuenta,categoria,nota") + filas).joinToString("\n") + "\n"
    }
}

/** Va al principio de los avisos de logro (meta cumplida, deuda saldada), que salen con la chispa. Es invisible. */
const val MARCA_LOGRO = "​"

/** Palabras para sugerir la categoría de un movimiento del extracto (sin marcas: solo palabras comunes). */
private val PALABRAS = mapOf(
    "mercado" to listOf("supermercado", "mercado", "tienda", "fruver", "carnes", "panaderia"),
    "transporte" to listOf("gasolina", "combustible", "peaje", "parqueadero", "taxi", "bus", "transporte"),
    "servicios" to listOf("energia", "acueducto", "gas natural", "internet", "telefonia", "servicio"),
    "salud" to listOf("farmacia", "drogueria", "clinica", "hospital", "eps", "medic"),
    "comida" to listOf("restaurante", "cafe", "comida", "pizza", "hamburgues", "domicilio"),
    "casa" to listOf("arriendo", "administracion", "hogar"),
    "impuestos" to listOf("4x1000", "gmf", "impuesto", "predial", "dian"),
    "deudas" to listOf("cuota", "credito", "prestamo"),
)
private val PALABRAS_SUELDO = listOf("nomina", "salario", "sueldo", "pago empresa")

private fun normalizar(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").trim()
