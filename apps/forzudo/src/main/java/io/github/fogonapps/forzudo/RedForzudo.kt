package io.github.fogonapps.forzudo

import io.github.fogonapps.forzudo.datos.SesionForzudo
import io.github.fogonapps.social.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Cuenta y publicaciones explícitas de entrenos, sin transporte de chat. */
class RedForzudo(
    private val app: ForzudoApp,
    private val almacen: SesionForzudo,
    scope: CoroutineScope,
    private val mensaje: (Int) -> Unit,
    private val cambiarPerfil: (String) -> Unit,
) : ControladorCuentaSocial(scope, almacen.sesion, almacen.usuario) {
    private val configuracion = ConfiguracionSocial("forzudo", "El Forzudo", "ElForzudo-Fogon")
    private var redPermitida = almacen.politicaAceptada == POLITICA_VIGENTE
    val correoDisponible = MutableStateFlow<Boolean?>(null)
    val gracia = MutableStateFlow<Acceso?>(null)
    val error = MutableStateFlow<String?>(null)
    val portadaImagen = MutableStateFlow("")
    val foto = MutableStateFlow("")
    val identidad = MutableStateFlow<PerfilSocial?>(null)
    private val carpeta = app.perfiles.directorio(almacen.perfil)
    private val cache = java.io.File(carpeta, "identidad-social.json")
    private val cacheFoto = java.io.File(carpeta, "foto-social")
    private val cachePortada = java.io.File(carpeta, "portada-social")
    val guardandoPerfil = MutableStateFlow(false)
    private var revision: Long? = null
    private var lecturaIdentidad = 0L
    init {
        scope.launch {
            ClienteSocial.cuentasPorBorrar.collect { (token, fecha) ->
                if (token == almacen.token) gracia.value = Acceso(almacen.usuario.orEmpty(), token, borrarEl = fecha, idCuenta = almacen.idCuenta)
            }
        }
        if (almacen.token != null) {
            identidad.value = runCatching { JsonSocial.decodeFromString(PerfilSocial.serializer(), cache.readText()) }.getOrNull()
            foto.value = runCatching { cacheFoto.readText() }.getOrDefault("")
            portadaImagen.value = runCatching { cachePortada.readText() }.getOrDefault("")
        }
    }
    override fun cliente() = ClienteSocial(almacen.token, { redPermitida }, configuracion)
    override fun clienteAcceso() = ClienteSocial(configuracion = configuracion)
    override suspend fun intentar(avisar: Boolean, marca: SesionCuenta.Marca, trabajo: suspend () -> Unit): Boolean =
        sesion.intentar(marca, trabajo) { x ->
            error.value = x.message
            if (x is ErrorSocial && x.codigo == 401) olvidarCuenta()
            if (avisar) mensaje(if (x is ErrorSocial && x.error == "perfil_revision") R.string.cuenta_perfil_cambio else R.string.cuenta_error)
            if (x is ErrorSocial && x.error == "perfil_revision") cargarIdentidad()
        }
    override fun avisar(evento: EventoSocial) { mensaje(R.string.cuenta_operacion_lista) }
    override fun olvidarCuenta() {
        sesion.invalidar(); almacen.token = null; almacen.usuario = null
        _usuario.value = null; _cuenta.value = null; identidad.value = null; foto.value = ""; portadaImagen.value = ""; revision = null
        listOf(cache, cacheFoto, cachePortada).forEach { it.delete() }
        _feed.value = null; _explorar.value = null; _perfiles.value = emptyMap(); _vistas.value = emptyMap()
        _comentarios.value = emptyMap(); _guardados.value = null; _avisos.value = null; _casos.value = null
        _solicitudes.value = emptyList(); _gente.value = emptyMap(); _listas.value = emptyMap()
        _sinLeer.value = 0; _ocultas.value = emptySet()
        _hojaPolitica.value = false; _politicaPendiente.value = false
    }
    override fun ponerCuenta(cuenta: MiCuenta) {
        if (cuenta.idCuenta.isBlank() || (almacen.idCuenta.isNotBlank() && cuenta.idCuenta != almacen.idCuenta))
            throw IOException("identidad_cuenta")
        almacen.guardarIdCuenta(cuenta.idCuenta)
        almacen.usuario = cuenta.usuario
        _usuario.value = cuenta.usuario; _cuenta.value = cuenta
        _correoCuenta.value = cuenta.correo; _privada.value = cuenta.privada
        app.perfiles.actualizar(almacen.perfil, cuenta.nombre, cuenta.usuario, "")
    }
    override fun alContarAvisos(cantidad: Int) {}
    override fun cargarCasos() = lanzar { _casos.value = cliente().casos() }
    override suspend fun entro(a: Acceso, nombreNuevo: String): Boolean {
        if (a.borrarEl > 0) { gracia.value = a; return false }
        require(a.idCuenta.isNotBlank())
        val existente = app.perfiles.duplicado(almacen.perfil, a.idCuenta)
        if (existente != null) {
            // Nunca fusionar cuadernos ni copiar la sesión a otro perfil.
            runCatching { ClienteSocial(a.token, configuracion = configuracion).salir() }
            cambiarPerfil(existente.id)
            return false
        }
        val vinculada = almacen.idCuenta
        if (vinculada.isNotBlank() && vinculada != a.idCuenta) {
            runCatching { ClienteSocial(a.token, configuracion = configuracion).salir() }
            mensaje(R.string.cuenta_otro_perfil)
            return false
        }
        sesion.invalidar(); almacen.guardarIdCuenta(a.idCuenta)
        almacen.token = a.token; almacen.usuario = a.usuario; _usuario.value = a.usuario
        revisarServidor().join()
        return true
    }
    fun decidirGracia(conservar: Boolean) = scope.launch {
        val a = gracia.value ?: return@launch
        gracia.value = null
        intentar {
            val temporal = ClienteSocial(a.token, configuracion = configuracion)
            if (conservar) { temporal.conservarCuenta(); entro(a.copy(borrarEl = 0)) }
            else { temporal.salir(); if (almacen.token == a.token) olvidarCuenta() }
        }
    }
    override suspend fun llaveConClave(clave: String) {}
    override suspend fun llaveNueva(clave: String) {}
    override fun permitirRed(permitir: Boolean) { redPermitida = permitir }
    override fun guardarPolitica(version: String) { almacen.politicaAceptada = version }
    override fun pausarPublicaciones() {}
    override fun reanudarPublicaciones() { cargarIdentidad() }
    override fun revisarServidor() = scope.launch {
        val marca = sesion.actual()
        val revisionAlEmpezar = revisionPolitica
        intentar {
            val info = clienteAcceso().info()
            if (!sesion.vigente(marca) || revisionAlEmpezar != revisionPolitica) return@intentar
            correoDisponible.value = info.correo == "true"
            politica = info.politica.ifBlank { POLITICA_VIGENTE }
            if (almacen.token == null) return@intentar
            val cuenta = cliente().miCuenta()
            if (!sesion.vigente(marca) || revisionAlEmpezar != revisionPolitica) return@intentar
            ponerCuenta(cuenta)
            _politicaPendiente.value = debeAceptarPolitica(true, cuenta.politica, politica)
            redPermitida = !_politicaPendiente.value
            _hojaPolitica.value = _politicaPendiente.value
            if (redPermitida) { almacen.politicaAceptada = politica; cargarIdentidad() }
        }
    }
    fun cargarIdentidad() = scope.launch {
        val u = usuario.value ?: return@launch
        val lectura = ++lecturaIdentidad
        val marca = sesion.actual()
        intentar {
            val cliente = cliente()
            // Leer la revisión antes del perfil: una edición concurrente obliga a recargar.
            val r = cliente.revisionPerfil()
            val perfil = cliente.perfil(u)
            val imagen = if (perfil.persona.foto) android.util.Base64.encodeToString(cliente.descargarFoto(u), android.util.Base64.NO_WRAP) else ""
            val portada = if (perfil.portada.isNotBlank()) runCatching { android.util.Base64.encodeToString(cliente.descargarImagenPerfil(perfil.portada), android.util.Base64.NO_WRAP) }.getOrDefault("") else ""
            if (sesion.vigente(marca) && lectura == lecturaIdentidad) {
                revision = r; identidad.value = perfil; foto.value = imagen; portadaImagen.value = portada
                io.github.fogonapps.forzudo.datos.escribirAtomico(cache, JsonSocial.encodeToString(PerfilSocial.serializer(), perfil).encodeToByteArray())
                io.github.fogonapps.forzudo.datos.escribirAtomico(cacheFoto, imagen.encodeToByteArray())
                io.github.fogonapps.forzudo.datos.escribirAtomico(cachePortada, portada.encodeToByteArray())
            }
        }
    }
    fun editarIdentidad(nombre: String, bio: String, portada: String, foto: ByteArray?, quitarFoto: Boolean, listo: () -> Unit) = scope.launch {
        if (guardandoPerfil.value) return@launch
        guardandoPerfil.value = true
        try { intentar {
        val marca = sesion.actual()
        val r = revision ?: throw IOException("perfil_sin_cargar")
        val cliente = cliente()
        val urlPortada = when {
            portada == portadaImagen.value -> identidad.value?.portada.orEmpty()
            portada.isBlank() -> ""
            portada.startsWith("https://") -> portada
            else -> ClienteSocial.imagen(cliente.subirImagen(android.util.Base64.decode(portada, android.util.Base64.DEFAULT)))
        }
        if (!sesion.vigente(marca)) return@intentar
        val nueva = cliente.subirPerfilApp(CambioPerfilApp(r, IdentidadPerfil(nombre, bio, urlPortada)))
        if (!sesion.vigente(marca)) return@intentar
        revision = nueva
        if (foto != null) cliente.subirFoto(foto) else if (quitarFoto) cliente.quitarFoto()
        cargarIdentidad()
        listo()
        } } finally { guardandoPerfil.value = false }
    }
    private val publicacionesLocales = java.io.File(carpeta, "entrenos-publicados.json")
    private val instantaneas = runCatching {
        JsonSocial.decodeFromString(kotlinx.serialization.builtins.ListSerializer(PublicacionSubida.serializer()), publicacionesLocales.readText())
    }.getOrDefault(emptyList()).toMutableList()
    fun origen(publicacion: PublicacionSocial): String? = instantaneas.findLast {
        it.cuando == publicacion.cuando && it.datos == kotlinx.serialization.json.JsonObject(publicacion.datos - "tipo")
    }?.clave
    fun publicada(clave: String) = instantaneas.findLast { it.clave == clave }
    val publicando = MutableStateFlow(false)
    fun cabecerasPublicacion() = almacen.token?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
    suspend fun subirFotoPublicacion(bytes: ByteArray): String {
        val marca = sesion.actual()
        val nombre = cliente().subirImagen(bytes)
        check(sesion.vigente(marca))
        return nombre
    }
    suspend fun subirVideoPublicacion(archivo: java.io.File): String {
        val marca = sesion.actual()
        val nombre = cliente().subirVideo(archivo)
        check(sesion.vigente(marca))
        return nombre
    }
    fun publicarEntreno(publicacion: PublicacionSubida, listo: () -> Unit) = scope.launch {
        if (publicando.value) return@launch
        publicando.value = true
        val marca = sesion.actual()
        try { intentar {
            require(io.github.fogonapps.forzudo.datos.cabeInstantaneaEntreno(publicacion.datos))
            // Guardar antes del envío permite recuperar también una respuesta perdida.
            if (publicacion !in instantaneas) instantaneas.add(publicacion)
            io.github.fogonapps.forzudo.datos.escribirAtomico(publicacionesLocales,
                JsonSocial.encodeToString(kotlinx.serialization.builtins.ListSerializer(PublicacionSubida.serializer()), instantaneas.toList()).encodeToByteArray())
            cliente().subirCambios(listOf(publicacion), emptyList())
            if (sesion.vigente(marca)) { cargarIdentidad(); cargarFeed(); listo() }
        } } finally { publicando.value = false }
    }
    fun borrarEntreno(id: Long) = lanzar {
        cliente().borrarPublicacion(id)
        ocultar(id); cargarIdentidad(); cargarFeed()
    }
    fun retirarEntreno(clave: String, id: Long? = null) = lanzar {
        cliente().subirCambios(emptyList(), listOf(clave))
        if (id != null) ocultar(id)
        cargarIdentidad(); cargarFeed()
    }
    fun vistaPublicacion(id: Long) = lanzar { cliente().vio(listOf(id)) }
    fun visibilidad(clave: String) = almacen.visibilidad(clave)
    fun publicarPartes(partes: Map<String, ParteSubida>) = lanzar {
        val marca = sesion.actual()
        cliente().subirPerfilApp(CambioPerfilApp(partes = partes))
        if (!sesion.vigente(marca)) return@lanzar
        listOf("rangos", "medallas").forEach { almacen.guardarVisibilidad(it, partes[it]?.visibilidad ?: "privado") }
        cargarIdentidad()
    }
    fun crear(usuario: String, nombre: String, clave: String, foto: ByteArray?) = lanzar {
        val acceso = acceso.value
        if (entro(clienteAcceso().entrar(acceso.correo, acceso.codigo, usuario, nombre, politica, clave)) && foto != null)
            cliente().subirFoto(foto)
    }
    fun ponerClave(nueva: String, actual: String, listo: () -> Unit) = lanzar { cliente().ponerClave(nueva, actual); revisarServidor(); listo() }
    fun cambiarUsuario(nuevo: String, listo: () -> Unit) = lanzar { cliente().cambiarUsuario(nuevo); revisarServidor(); listo() }
    fun vincular(codigo: String) = lanzar { cliente().vincularCorreo(acceso.value.correo, codigo); revisarServidor() }
    fun salir() = lanzar { cliente().salir(); olvidarCuenta() }
    fun invalidar() { sesion.invalidar() }
}
