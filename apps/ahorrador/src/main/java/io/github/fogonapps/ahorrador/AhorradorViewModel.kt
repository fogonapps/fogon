package io.github.fogonapps.ahorrador

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
    data class Anotar(val tipo: TipoMovimiento = TipoMovimiento.GASTO, val editar: String? = null, val aCuenta: String? = null) : Pantalla
    data object Cuentas : Pantalla
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
    data object Perfil : Pantalla
    data object AcercaDe : Pantalla
    data object Manifiesto : Pantalla
    data object Apariencia : Pantalla
    data object Licencias : Pantalla
    data object Preguntas : Pantalla
    data object Reporte : Pantalla
}

enum class Pestana { INICIO, RESUMEN, SOBRES, METAS }

class AhorradorViewModel(app: Application) : AndroidViewModel(app) {
    private val ahorradorApp = app as AhorradorApp
    private val almacen = ahorradorApp.almacen
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
    val candado: StateFlow<Boolean> = _candado

    private var guardado: Job? = null

    init {
        viewModelScope.launch {
            val e = almacen.data.first()
            _estado.value = e
            anotarFijos()
            // Si otra app de Fogón restauró los datos (respaldo de todo Fogón), se toman.
            almacen.data.drop(1).collect { d -> if (d != ultimoGuardado && d != _estado.value) _estado.value = d }
        }
    }

    /** Lo último que esta pantalla escribió en el disco, para distinguirlo de un cambio de afuera. */
    @Volatile private var ultimoGuardado: Estado? = null

    fun hoy(): LocalDate = LocalDate.now()
    fun mesActual(): String = mesDe(hoy())

    /** Un texto de strings.xml en el idioma de la app. */
    fun tx(id: Int, vararg a: Any): String = Idioma.envolver(getApplication()).getString(id, *a)
    fun txPlural(id: Int, n: Int): String = Idioma.envolver(getApplication()).resources.getQuantityString(id, n, n)
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
        WidgetPlata.actualizar(getApplication())
    }

    /* ----- Navegación ----- */

    fun abrir(p: Pantalla) { _pila.value = _pila.value + p }
    fun cerrar() { _pila.value = _pila.value.dropLast(1) }
    fun irA(p: Pestana) { _pila.value = emptyList(); _pestana.value = p }
    fun atras() { if (_pila.value.isNotEmpty()) cerrar() else _pestana.value = Pestana.INICIO }

    /* ----- Tutorial ----- */

    /** Deja la app lista: nombre, sistema, categorías en el idioma de la app y las cuentas con su saldo de hoy. */
    fun empezar(nombre: String, sistema: Sistema, efectivo: Long, banco: Long, billetera: Long, deudaTarjeta: Long?) {
        cambiar { e ->
            val cuentas = buildList {
                add(Cuenta("efectivo", tx(R.string.cuenta_efectivo), TipoCuenta.EFECTIVO, efectivo))
                if (banco > 0) add(Cuenta("banco", tx(R.string.cuenta_banco), TipoCuenta.BANCO, banco))
                if (billetera > 0) add(Cuenta("billetera", tx(R.string.cuenta_billetera), TipoCuenta.BILLETERA, billetera))
                if (deudaTarjeta != null) add(Cuenta("tarjeta", tx(R.string.cuenta_tarjeta), TipoCuenta.CREDITO, -deudaTarjeta,
                    io.github.fogonapps.ahorrador.datos.Credito(cupo = 0, diaCorte = 15, diaPago = 30)))
            }
            e.copy(nombre = nombre.trim().ifEmpty { null }, sistema = sistema, cuentas = cuentas,
                categorias = Categorias.base(getApplication()), listo = true)
        }
    }

    /* ----- Movimientos ----- */

    /**
     * Guarda un gasto, ingreso, traspaso o ahorro. Con el 4×1000 activado, si la plata sale de una
     * cuenta de banco (gasto o traspaso), anota también el impuesto.
     */
    fun guardarMovimiento(m: Movimiento): Boolean {
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
        cambiar { st ->
            val lista = if (nuevo) st.movimientos + mov else st.movimientos.map { if (it.id == mov.id) mov else it }
            st.copy(movimientos = lista + listOfNotNull(impuesto))
        }
        avisar(tx(if (nuevo) R.string.aviso_guardado else R.string.aviso_actualizado))
        return true
    }

    fun borrarMovimiento(id: String) {
        cambiar { it.copy(movimientos = it.movimientos.filter { m -> m.id != id }) }
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
        cambiar { e ->
            if (f.id.isEmpty()) e.copy(fijos = e.fijos + f.copy(id = nuevoId("f")))
            else e.copy(fijos = e.fijos.map { if (it.id == f.id) f else it })
        }
        anotarFijos()
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
            st.copy(movimientos = st.movimientos + pend.map { f ->
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

    /** Anota las filas elegidas en la [cuenta]. Salta las que ya están (misma fecha, monto y nota). */
    fun importarExtracto(filas: List<Pair<io.github.fogonapps.ahorrador.datos.FilaExtracto, String>>, cuenta: String) {
        var n = 0
        cambiar { st ->
            val ya = st.movimientos.map { Triple(it.fecha, it.monto, normalizar(it.nota)) }.toSet()
            val nuevos = filas.filter { (f, _) -> Triple(f.fecha, kotlin.math.abs(f.monto), normalizar(f.descripcion)) !in ya }.map { (f, cat) ->
                Movimiento(nuevoId("m"), f.fecha, if (f.monto < 0) TipoMovimiento.GASTO else TipoMovimiento.INGRESO, kotlin.math.abs(f.monto), cuenta,
                    categoria = cat, nota = f.descripcion.take(60))
            }
            n = nuevos.size
            st.copy(movimientos = st.movimientos + nuevos)
        }
        cerrar()
        avisar(txPlural(R.plurals.extracto_importados, n))
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
        if (guardarMovimiento(Movimiento("", hoy().toString(), TipoMovimiento.AHORRO, monto, cuenta, meta = meta.id, nota = meta.nombre))) {
            val e = _estado.value ?: return
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
        cambiar { e -> e.copy(deudas = e.deudas.map { if (it.id == d.id) it.copy(abonos = it.abonos + Abono(hoy().toString(), monto)) else it }) }
        val e = _estado.value ?: return
        val actual = e.deudas.first { it.id == d.id }
        avisar(if (L.pendiente(actual) == 0L) MARCA_LOGRO + tx(R.string.aviso_deuda_saldada) else tx(R.string.aviso_abono))
    }

    /* ----- Ajustes ----- */

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
