package io.github.fogonapps.social

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Seguridad de cuenta; el adaptador mantiene el cierre y la recuperación de sus datos privados. */
abstract class ControladorCuentaSocial(
    scope: CoroutineScope,
    sesion: SesionCuenta,
    usuarioInicial: String?,
) : ControladorSocial(scope, sesion, usuarioInicial) {
    protected val _privada = MutableStateFlow(false)
    val privada: StateFlow<Boolean> = _privada
    protected val _correoPendiente = MutableStateFlow("")
    val correoPendiente: StateFlow<String> = _correoPendiente
    protected val _seguridadOcupada = MutableStateFlow(false)
    val seguridadOcupada: StateFlow<Boolean> = _seguridadOcupada
    protected val _correoCuenta = MutableStateFlow("")
    val correoCuenta: StateFlow<String> = _correoCuenta

    protected fun seguridad(trabajo: suspend () -> Unit) = scope.launch {
        if (_seguridadOcupada.value) return@launch
        _seguridadOcupada.value = true
        try { intentar(trabajo = trabajo) } finally { _seguridadOcupada.value = false }
    }

    fun pedirCodigoSeguridad() = seguridad { cliente().pedirCodigo(_correoCuenta.value) }

    fun cambiarCorreo(nuevo: String, clave: String, codigo: String) = seguridad {
        val marca = sesion.actual()
        cliente().cambiarCorreo(nuevo, clave, codigo)
        if (sesion.vigente(marca)) _correoPendiente.value = nuevo.trim().lowercase()
    }

    fun cancelarCambioCorreo() { if (!_seguridadOcupada.value) _correoPendiente.value = "" }

    fun confirmarCorreo(codigo: String) = seguridad {
        val marca = sesion.actual()
        val cliente = cliente()
        val nuevo = _correoPendiente.value
        cliente.confirmarCorreo(nuevo, codigo)
        if (!sesion.vigente(marca)) return@seguridad
        _correoCuenta.value = nuevo
        _correoPendiente.value = ""
        val cuenta = cliente.miCuenta()
        if (!sesion.vigente(marca)) return@seguridad
        ponerCuenta(cuenta)
        avisar(EventoSocial.CORREO_VINCULADO)
    }

    fun borrarCuenta(clave: String, codigo: String, motivo: String, texto: String) = seguridad {
        val marca = sesion.actual()
        val cliente = cliente()
        cliente.borrarCuenta(clave, codigo, motivo, texto)
        runCatching { cliente.salir() }
        if (sesion.vigente(marca)) { olvidarCuenta(); avisar(EventoSocial.CUENTA_BORRADA) }
    }

    fun cambiarPrivada(si: Boolean) = lanzar { cliente().privada(si); _privada.value = si }
    protected abstract fun clienteAcceso(): ClienteSocial
    protected abstract suspend fun entro(a: io.github.fogonapps.social.Acceso, nombreNuevo: String = ""): Boolean
    protected abstract suspend fun llaveConClave(clave: String)
    protected abstract suspend fun llaveNueva(clave: String)
    protected open fun ahoraAcceso(): Long = android.os.SystemClock.elapsedRealtime()

    protected val _acceso = MutableStateFlow(EstadoAcceso())
    val acceso: StateFlow<EstadoAcceso> = _acceso

    /** El mismo envío para cualquier correo, sin consultar si tiene cuenta. */
    fun revisarCorreo(correo: String) {
        _acceso.value = EstadoAcceso(correo = correo.trim().lowercase())
        pedirCodigo(MotivoCodigo.NUEVA)
    }

    fun usarClave(correo: String) { _acceso.value = EstadoAcceso(PasoAcceso.CLAVE, correo.trim().lowercase()) }

    private val _reenviarDesde = MutableStateFlow(0L)
    val reenviarDesde: StateFlow<Long> = _reenviarDesde
    fun reenviarCodigo() = lanzar {
        if (_acceso.value.ocupado || ahoraAcceso() < _reenviarDesde.value) return@lanzar
        _acceso.value = _acceso.value.copy(ocupado = true)
        try {
            clienteAcceso().pedirCodigo(_acceso.value.correo)
            _reenviarDesde.value = ahoraAcceso() + 60_000
        } finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** Para ponerle correo a una cuenta hecha sin él: manda el código a [correo]. */
    fun pedirCodigoVincular(correo: String) = lanzar {
        _acceso.value = _acceso.value.copy(ocupado = true)
        try {
            clienteAcceso().pedirCodigo(correo.trim())
            _reenviarDesde.value = ahoraAcceso() + 60_000
            _acceso.value = EstadoAcceso(PasoAcceso.CODIGO, correo.trim())
        } finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** Manda el código al correo ([motivo]: registrarse u olvidó la contraseña). */
    fun pedirCodigo(motivo: MotivoCodigo) = lanzar {
        val c = _acceso.value.correo
        _acceso.value = _acceso.value.copy(ocupado = true)
        try {
            clienteAcceso().pedirCodigo(c)
            _reenviarDesde.value = ahoraAcceso() + 60_000
            _acceso.value = EstadoAcceso(PasoAcceso.CODIGO, c, motivo = motivo)
        } finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** Completa la entrada antes de pedir a la app que recupere sus datos privados. */
    fun entrar(codigo: String, claveNueva: String = "") = scope.launch {
        var marca = sesion.actual()
        val a = _acceso.value
        _acceso.value = a.copy(ocupado = true)
        try {
            if (a.motivo == MotivoCodigo.NUEVA) {
                val entrada = clienteAcceso().entrar(a.correo, codigo.trim())
                // Sin la contraseña, los datos guardados en la cuenta no se pueden abrir ni seguir guardando:
                // se pide (o se crea) aquí mismo, antes de terminar de entrar, para que no quede nada por arreglar.
                val conClave = if (entrada.nueva || entrada.borrarEl > 0) null
                    else try { clienteConToken(entrada.token).miCuenta().conClave } catch (x: IOException) { null } catch (x: ErrorSocial) { null }
                if (conClave == null || !sesion.vigente(marca)) entro(entrada)
                else {
                    entradaPendiente = entrada
                    _acceso.value = EstadoAcceso(if (conClave) PasoAcceso.CLAVE_DATOS else PasoAcceso.CREAR_CLAVE, a.correo)
                }
            } else {
                if (entro(clienteAcceso().entrar(a.correo, codigo.trim(), clave = claveNueva))) {
                    marca = sesion.actual()
                    llaveNueva(claveNueva)
                }
            }
        } catch (x: ErrorSocial) {
            if (sesion.vigente(marca) && esCuentaNueva(x))
                _acceso.value = EstadoAcceso(PasoAcceso.USUARIO, a.correo, codigo.trim())
            else intentar(marca = marca) { throw x }
        }
        catch (x: IOException) { if (sesion.vigente(marca)) avisar(EventoSocial.SIN_CONEXION) }
        finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    fun volverAlCorreo() { soltarEntradaPendiente(); claveEnEspera = null; _acceso.value = EstadoAcceso() }

    /** «¿Olvidaste tu contraseña?» desde la pantalla de entrar: código al correo y contraseña nueva. */
    fun olvideClave(correo: String) {
        _acceso.value = EstadoAcceso(correo = correo.trim().lowercase())
        pedirCodigo(MotivoCodigo.OLVIDO)
    }

    /** La sesión abierta con el código mientras se pide la contraseña; no se usa hasta tenerla. */
    private var entradaPendiente: Acceso? = null
    protected open fun clienteConToken(token: String): ClienteSocial = ClienteSocial(token, configuracion = clienteAcceso().configuracion)

    private suspend fun cerrarEntradaPendiente() {
        val e = entradaPendiente ?: return
        entradaPendiente = null
        try { clienteConToken(e.token).salir() } catch (x: IOException) {} catch (x: ErrorSocial) {}
    }
    private fun soltarEntradaPendiente() { if (entradaPendiente != null) scope.launch { cerrarEntradaPendiente() } }

    /** Después del código, la contraseña de la cuenta: entra con ella (el servidor la comprueba) y abre los datos. */
    fun entrarConClaveDatos(clave: String) = lanzar {
        val correo = _acceso.value.correo
        _acceso.value = _acceso.value.copy(ocupado = true)
        try {
            val entrada = clienteAcceso().entrarConClave(correo, clave)
            cerrarEntradaPendiente()
            if (entro(entrada)) runCatching { intentar(avisar = false) { llaveConClave(clave) } }
        } finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** Después del código, una cuenta sin contraseña la crea: desde ahí sus datos se guardan cifrados con ella. */
    fun crearClaveDatos(clave: String) = lanzar {
        val entrada = entradaPendiente ?: return@lanzar
        _acceso.value = _acceso.value.copy(ocupado = true)
        try {
            clienteConToken(entrada.token).ponerClave(clave)
            entradaPendiente = null
            if (entro(entrada)) runCatching { intentar(avisar = false) { llaveConClave(clave) } }
        } finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** Olvidó la contraseña después del código: se cierra esa sesión y se recupera con otro código y una contraseña nueva. */
    fun olvideClaveDatos() { soltarEntradaPendiente(); pedirCodigo(MotivoCodigo.OLVIDO) }

    /** La contraseña mientras se espera el código de la verificación en dos pasos (solo en memoria). */
    private var claveEnEspera: String? = null

    /**
     * La pantalla de sanción: se intentó entrar con una cuenta cerrada. Guarda en memoria el correo y la contraseña
     * (solo mientras está abierta) para mostrar el motivo y apelar; «Entrar con otra cuenta» la cierra.
     */
    data class Sancion(val correo: String, val clave: String, val estado: EstadoSancionSocial?)
    private val _sancion = MutableStateFlow<Sancion?>(null)
    val sancion: StateFlow<Sancion?> = _sancion
    private suspend fun abrirSancion(correo: String, clave: String) {
        val estado = runCatching { clienteAcceso().estadoSancion(correo, clave) }.getOrNull()
        _sancion.value = Sancion(correo, clave, estado)
    }
    fun apelarSancion(texto: String) = lanzar {
        val s = _sancion.value ?: return@lanzar
        clienteAcceso().apelarConClave(s.correo, s.clave, texto.trim())
        _sancion.value = s.copy(estado = runCatching { clienteAcceso().estadoSancion(s.correo, s.clave) }.getOrNull() ?: s.estado)
    }
    fun cerrarSancion() { _sancion.value = null; claveEnEspera = null; _acceso.value = EstadoAcceso() }

    fun entrarConClave(correo: String, clave: String, codigo: String = "") = lanzar {
        val c = correo.trim().lowercase()
        _acceso.value = _acceso.value.copy(correo = c, ocupado = true)
        try {
            val entrada = try { clienteAcceso().entrarConClave(c, clave, codigo) } catch (x: ErrorSocial) {
                if (x.codigo == 409 && x.error == "codigo_requerido") {
                    claveEnEspera = clave
                    _reenviarDesde.value = ahoraAcceso() + 60_000
                    _acceso.value = EstadoAcceso(PasoAcceso.CODIGO_ENTRAR, c)
                    return@lanzar
                }
                if (x.codigo == 403 && x.error == "cerrada") { abrirSancion(c, clave); return@lanzar }
                throw x
            }
            claveEnEspera = null
            if (entro(entrada)) runCatching { intentar(avisar = false) { llaveConClave(clave) } }
        } finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** Segundo paso: el código del correo, con la contraseña que ya se comprobó. */
    fun entrarConCodigoDosPasos(codigo: String) {
        val clave = claveEnEspera ?: run { volverAlCorreo(); return }
        entrarConClave(_acceso.value.correo, clave, codigo)
    }

    /** Vuelve a pedir el código de la verificación en dos pasos (el servidor lo manda al reintentar sin código). */
    fun reenviarCodigoDosPasos() = lanzar {
        val clave = claveEnEspera ?: return@lanzar
        if (_acceso.value.ocupado || ahoraAcceso() < _reenviarDesde.value) return@lanzar
        _acceso.value = _acceso.value.copy(ocupado = true)
        try { clienteAcceso().entrarConClave(_acceso.value.correo, clave) } catch (x: ErrorSocial) { if (!(x.codigo == 409 && x.error == "codigo_requerido")) throw x }
        finally { _reenviarDesde.value = ahoraAcceso() + 60_000; _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** Prende o apaga la verificación en dos pasos (con la contraseña). */
    fun ponerDosPasos(activo: Boolean, clave: String) = seguridad {
        cliente().ponerDosPasos(activo, clave)
        ponerCuenta(cliente().miCuenta())
    }

    var politica = POLITICA_VIGENTE
        protected set
    abstract fun revisarServidor(): kotlinx.coroutines.Job
    protected abstract fun permitirRed(permitir: Boolean)
    protected abstract fun guardarPolitica(version: String)
    protected abstract fun pausarPublicaciones()
    protected abstract fun reanudarPublicaciones()

    protected var revisionPolitica = 0L
    protected val _politicaPendiente = MutableStateFlow(false)
    val politicaPendiente: StateFlow<Boolean> = _politicaPendiente
    protected val _hojaPolitica = MutableStateFlow(false)
    val hojaPolitica: StateFlow<Boolean> = _hojaPolitica
    private var ultimaRevisionAlAbrir = 0L
    /**
     * Al volver a la app. Cambiar la apariencia recrea la pantalla (y vuelve a llegar ON_START): sin este límite,
     * revisar el servidor en cada inicio entraba en un bucle que colgaba el teléfono.
     */
    fun alAbrir(ahora: Long = android.os.SystemClock.elapsedRealtime()) {
        if (_politicaPendiente.value) _hojaPolitica.value = true
        if (!debeRevisarAlAbrir(ultimaRevisionAlAbrir, ahora)) return
        ultimaRevisionAlAbrir = ahora
        revisarServidor()
    }
    fun revisarPolitica() { _hojaPolitica.value = true }
    fun posponerPolitica() {
        permitirRed(false)
        _hojaPolitica.value = false
        pausarPublicaciones()
    }
    fun aceptarPolitica() = scope.launch {
        val marca = sesion.actual()
        val version = politica
        intentar {
            cliente().aceptarPolitica(version)
            if (!sesion.vigente(marca)) return@intentar
            revisionPolitica++
            guardarPolitica(version)
            permitirRed(true)
            _politicaPendiente.value = false; _hojaPolitica.value = false
            reanudarPublicaciones()
        }
    }

}
