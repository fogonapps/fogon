package io.github.fogonapps.social

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Una compra tal como la entrega Google Play. */
data class CompraPlay(val producto: String, val token: String, val pendiente: Boolean, val reconocida: Boolean = false)

/** Cómo terminó el flujo de compra de Google Play. */
sealed interface ResultadoPlay {
    data class Compras(val lista: List<CompraPlay>) : ResultadoPlay
    data object Cancelada : ResultadoPlay
    /** El producto ya es tuyo: hay una compra sin cerrar (se revisa y se acredita). */
    data object YaTenia : ResultadoPlay
    data object NoDisponible : ResultadoPlay
    data object Fallo : ResultadoPlay
}

/**
 * Google Play Billing visto desde Fogón. Solo la versión de Play de una app trae una implementación (con la biblioteca de
 * Google); la versión libre no la tiene y no vende por aquí. Todo lo demás (verificar, acreditar, reintentar) está en
 * [TiendaPlay], sin depender de la biblioteca.
 */
interface PasarelaPlay {
    /** El applicationId de la app (Google lo pide para verificar). */
    val paquete: String
    /** Recibe lo que termina el flujo de compra (también compras que llegan solas, como un pago pendiente que se aprobó). */
    fun escuchar(receptor: (ResultadoPlay) -> Unit)
    /** El precio de cada producto, como lo muestra Google en la moneda de la persona. Null si Play no está disponible. */
    suspend fun precios(ids: List<String>): Map<String, String>?
    /** Abre el flujo de compra. [cuenta]: el identificador opaco de la cuenta (obfuscatedAccountId). */
    suspend fun comprar(actividad: android.app.Activity, producto: String, cuenta: String): ResultadoPlay?
    /** Compras que Google tiene sin consumir ni reconocer (o pendientes). */
    suspend fun sinCerrar(): List<CompraPlay>?
    /** Consume (paquetes) o reconoce (pago único) una compra ya acreditada. */
    suspend fun cerrar(token: String, unico: Boolean): Boolean
}

/** Un producto listo para mostrar: lo que da (del servidor) y su precio (de Google). */
data class PaquetePlay(val id: String, val chispas: Int, val regalo: Int, val unico: Boolean, val precio: String)

sealed interface EstadoPlay {
    data object Libre : EstadoPlay
    data object Comprando : EstadoPlay
    /** Google todavía no cobra (pago en efectivo o por aprobar): las chispas llegan cuando se apruebe. */
    data object Pendiente : EstadoPlay
    data class Acreditada(val chispas: Int) : EstadoPlay
    /** [motivo]: "cancelada", "conexion", "no_disponible", "rechazada", "sesion", "ya_tenia" o "error". */
    data class Error(val motivo: String) : EstadoPlay
}

/**
 * Las compras con Google Play: el catálogo lo da el servidor (cuántas chispas trae cada producto) y el precio, Google.
 * Cada compra se manda al servidor, que la confirma con Google y acredita; **solo después** se consume. Si la app se
 * cierra en medio o falla la red, la compra queda sin cerrar y [revisar] la vuelve a mandar (el servidor no acredita
 * dos veces el mismo token).
 */
class TiendaPlay(
    private val scope: CoroutineScope,
    private val pasarela: PasarelaPlay,
    private val cliente: () -> ClienteSocial,
    private val alAcreditar: (saldo: Int) -> Unit,
) {
    private val _paquetes = MutableStateFlow<List<PaquetePlay>?>(null)
    /** Null mientras carga; vacío si no se vende (Play o el servidor apagados). */
    val paquetes: StateFlow<List<PaquetePlay>?> = _paquetes
    private val _estado = MutableStateFlow<EstadoPlay>(EstadoPlay.Libre)
    val estado: StateFlow<EstadoPlay> = _estado
    private var oferta: OfertaPlay? = null
    private val cerrando = Mutex()

    init {
        pasarela.escuchar { r -> scope.launch { recibir(r) } }
    }

    fun cargar() = scope.launch { cargarAhora(); revisarAhora() }

    private suspend fun cargarAhora() {
        try {
            val o = cliente().paquetesPlay()
            oferta = o
            val precios = pasarela.precios(o.productos.map { it.id })
            _paquetes.value = if (precios == null) emptyList()
                else o.productos.mapNotNull { p -> precios[p.id]?.let { PaquetePlay(p.id, p.chispas, p.regalo, p.unico, it) } }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _paquetes.value = emptyList() }
    }

    fun comprar(actividad: android.app.Activity, id: String) = scope.launch {
        if (_estado.value == EstadoPlay.Comprando) return@launch
        val cuenta = oferta?.cuenta ?: run { cargarAhora(); oferta?.cuenta } ?: return@launch run { _estado.value = EstadoPlay.Error("conexion") }
        _estado.value = EstadoPlay.Comprando
        val r = try { pasarela.comprar(actividad, id, cuenta) } catch (e: CancellationException) { throw e } catch (e: Exception) { ResultadoPlay.Fallo }
        // Null: el flujo se abrió y el resultado llega por [PasarelaPlay.escuchar].
        if (r != null) recibir(r)
    }

    /** Vuelve a mandar las compras sin cerrar (al abrir la app y la tienda). */
    fun revisar() = scope.launch { revisarAhora() }

    private suspend fun revisarAhora() {
        val lista = try { pasarela.sinCerrar() } catch (e: CancellationException) { throw e } catch (e: Exception) { null } ?: return
        procesar(lista, avisar = false)
    }

    internal suspend fun recibir(r: ResultadoPlay) {
        when (r) {
            is ResultadoPlay.Compras -> procesar(r.lista, avisar = true)
            ResultadoPlay.Cancelada -> _estado.value = EstadoPlay.Error("cancelada")
            ResultadoPlay.NoDisponible -> _estado.value = EstadoPlay.Error("no_disponible")
            ResultadoPlay.Fallo -> _estado.value = EstadoPlay.Error("error")
            ResultadoPlay.YaTenia -> {
                _estado.value = EstadoPlay.Libre
                revisarAhora()
                // Nada quedó sin acreditar: es el pago único, que ya se hizo antes.
                if (_estado.value == EstadoPlay.Libre) _estado.value = EstadoPlay.Error("ya_tenia")
            }
        }
    }

    private suspend fun procesar(lista: List<CompraPlay>, avisar: Boolean) = cerrando.withLock {
        if (lista.isEmpty()) { if (avisar) _estado.value = EstadoPlay.Libre; return@withLock }
        if (oferta == null) cargarAhora()
        for (c in lista) {
            val unico = oferta?.productos?.firstOrNull { it.id == c.producto }?.unico ?: false
            // Un pago único ya reconocido ya se acreditó antes: no hay nada que hacer.
            if (unico && c.reconocida) continue
            if (c.pendiente) { if (avisar) _estado.value = EstadoPlay.Pendiente; continue }
            val nuevo = try {
                val r = cliente().verificarPlay(pasarela.paquete, c.producto, c.token)
                if (r.estado == "acreditada") {
                    // Solo con el servidor de acuerdo se consume; si consumir falla, la próxima revisión lo reintenta.
                    pasarela.cerrar(c.token, unico)
                    alAcreditar(r.chispas)
                    EstadoPlay.Acreditada(r.chispas)
                } else EstadoPlay.Pendiente
            } catch (e: CancellationException) { throw e }
            catch (e: ErrorSocial) {
                when {
                    e.codigo == 503 -> EstadoPlay.Error("no_disponible")
                    e.codigo == 401 -> EstadoPlay.Error("sesion")
                    e.codigo in 400..499 -> EstadoPlay.Error("rechazada")
                    else -> EstadoPlay.Error("conexion")
                }
            }
            catch (e: Exception) { EstadoPlay.Error("conexion") }
            // Al revisar en silencio solo se cuenta lo que sí se acreditó.
            if (avisar || nuevo is EstadoPlay.Acreditada) _estado.value = nuevo
        }
    }

    /** Se cerró la tienda: vuelve a estar libre (si el flujo de Google seguía abierto, su resultado llega igual). */
    fun visto() { _estado.value = EstadoPlay.Libre }
}
