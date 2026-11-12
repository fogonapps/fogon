package io.github.fogonapps.social

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * La cuenta y las publicaciones explícitas de una app sin chat (El Forzudo, La Cucha): identidad global,
 * perfil, instantáneas publicadas y partes del perfil. La app solo da su configuración, sus textos y
 * cómo validar lo que publica.
 */
open class RedApp(
    private val perfilesTelefono: Perfiles,
    protected val almacen: SesionApp,
    scope: CoroutineScope,
    private val configuracion: ConfiguracionSocial,
    private val textos: TextosRed,
    private val mensaje: (Int) -> Unit,
    private val cambiarPerfil: (String) -> Unit,
    /** Si los datos de una publicación caben y son válidos (se revisa antes de guardar y enviar). */
    private val validar: (kotlinx.serialization.json.JsonObject) -> Boolean,
    archivoPublicaciones: String,
    /** Las partes del perfil con «Quién lo ve» que se recuerdan en el teléfono. */
    private val partesVisibles: List<String> = emptyList(),
) : ControladorCuentaSocial(scope, almacen.sesion, almacen.usuario) {
    private var redPermitida = almacen.politicaAceptada == POLITICA_VIGENTE
    val correoDisponible = MutableStateFlow<Boolean?>(null)
    val gracia = MutableStateFlow<Acceso?>(null)
    val error = MutableStateFlow<String?>(null)
    val portadaImagen = MutableStateFlow("")
    val foto = MutableStateFlow("")
    val identidad = MutableStateFlow<PerfilSocial?>(null)
    private val carpeta = perfilesTelefono.directorio(almacen.perfil)
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
    /** Para las frases del saludo: leer funciona sin cuenta; votar y proponer usan la sesión si la hay. */
    fun clienteFrases(): ClienteSocial = cliente()
    override suspend fun intentar(avisar: Boolean, marca: SesionCuenta.Marca, trabajo: suspend () -> Unit): Boolean =
        sesion.intentar(marca, trabajo) { x ->
            error.value = x.message
            if (x is ErrorSocial && x.codigo == 401) olvidarCuenta()
            if (avisar) mensaje(if (x is ErrorSocial && x.error == "perfil_revision") textos.perfilCambio else textos.error)
            if (x is ErrorSocial && x.error == "perfil_revision") cargarIdentidad()
        }
    override fun avisar(evento: EventoSocial) { mensaje(textos.operacionLista) }
    override fun olvidarCuenta() {
        sesion.invalidar(); almacen.token = null; almacen.usuario = null; almacen.llaveCopia = null
        copiaLista.value = false; copiaBloqueada.value = false
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
        perfilesTelefono.actualizar(almacen.perfil, cuenta.nombre, cuenta.usuario, "")
    }
    /** Lo que hace la app al contar los avisos sin leer (por ejemplo, darlos por vistos para la revisión con la app cerrada). */
    var alContarAvisosRed: (Int) -> Unit = {}
    override fun alContarAvisos(cantidad: Int) { alContarAvisosRed(cantidad) }
    override fun cargarCasos() = lanzar { _casos.value = cliente().casos() }
    override suspend fun entro(a: Acceso, nombreNuevo: String): Boolean {
        if (a.borrarEl > 0) { gracia.value = a; return false }
        require(a.idCuenta.isNotBlank())
        val existente = perfilesTelefono.duplicado(almacen.perfil, a.idCuenta)
        if (existente != null) {
            // Nunca fusionar cuadernos ni copiar la sesión a otro perfil.
            runCatching { ClienteSocial(a.token, configuracion = configuracion).salir() }
            cambiarPerfil(existente.id)
            return false
        }
        val vinculada = almacen.idCuenta
        if (vinculada.isNotBlank() && vinculada != a.idCuenta) {
            runCatching { ClienteSocial(a.token, configuracion = configuracion).salir() }
            mensaje(textos.otroPerfil)
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
    /* ----- D-033: lo de la app guardado en la cuenta, cifrado con la contraseña ----- */

    /** La llave común está en este teléfono: la copia se guarda y se trae sola. */
    val copiaLista = MutableStateFlow(almacen.llaveCopia != null)
    /** La cuenta ya tiene llave en el servidor pero este teléfono no la abrió (se entró con el código del correo). */
    val copiaBloqueada = MutableStateFlow(false)
    /** Cuándo se guardó la última copia en la cuenta (ms), o 0. */
    val ultimaCopia = MutableStateFlow(0L)
    /** Lo llama la app para guardar o traer su copia cuando la llave queda lista. */
    var alAbrirCopia: () -> Unit = {}

    fun llaveCopia(): String? = almacen.llaveCopia
    suspend fun descargarCopia(): CopiaCifrada = cliente().respaldo()
    suspend fun subirCopia(sobre: String, antes: Long): Long = cliente().guardarRespaldo(sobre, antes)

    private fun ponerLlave(llave: String) {
        almacen.llaveCopia = llave
        copiaLista.value = true; copiaBloqueada.value = false
        alAbrirCopia()
    }

    /** Con la contraseña: abre la llave que ya tiene la cuenta o, si no tiene, la crea y la sube envuelta. */
    override suspend fun llaveConClave(clave: String) {
        val marca = sesion.actual()
        val cliente = cliente()
        val enServidor = cliente.respaldo().llave
        if (!sesion.vigente(marca)) return
        val llave = if (enServidor.isNotBlank()) withContext(Dispatchers.Default) { NubeCifrada.desenvolver(enServidor, clave) }
        else (almacen.llaveCopia ?: NubeCifrada.llaveNueva()).also {
            val envuelta = withContext(Dispatchers.Default) { NubeCifrada.envolver(it, clave) }
            if (!sesion.vigente(marca)) return
            cliente.guardarLlaveRespaldo(envuelta)
        }
        if (sesion.vigente(marca)) ponerLlave(llave)
    }

    /**
     * Contraseña nueva (cambiada o recuperada con el correo): la llave de este teléfono se vuelve a envolver con ella.
     * Si este teléfono no la tenía, se crea otra: lo guardado con la contraseña olvidada ya no se puede abrir.
     */
    override suspend fun llaveNueva(clave: String) {
        val marca = sesion.actual()
        val llave = almacen.llaveCopia ?: NubeCifrada.llaveNueva()
        val envuelta = withContext(Dispatchers.Default) { NubeCifrada.envolver(llave, clave) }
        if (!sesion.vigente(marca)) return
        cliente().guardarLlaveRespaldo(envuelta)
        if (sesion.vigente(marca)) ponerLlave(llave)
    }

    /** «Escribe tu contraseña para traer y guardar tus datos»: abre la llave en este teléfono. */
    fun desbloquearCopia(clave: String, malaClave: () -> Unit) = scope.launch {
        try { intentar(avisar = false) { llaveConClave(clave) } } catch (x: kotlinx.coroutines.CancellationException) { throw x }
        if (!copiaLista.value) malaClave()
    }

    /** Revisa si la cuenta tiene llave y este teléfono no (entonces hay que pedir la contraseña). */
    fun revisarCopia() = lanzar {
        if (almacen.token == null || almacen.llaveCopia != null) return@lanzar
        copiaBloqueada.value = cliente().respaldo().llave.isNotBlank()
    }
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
            if (redPermitida) { almacen.politicaAceptada = politica; cargarIdentidad(); revisarCopia(); alAbrirCopia() }
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
                escribirAtomico(cache, JsonSocial.encodeToString(PerfilSocial.serializer(), perfil).encodeToByteArray())
                escribirAtomico(cacheFoto, imagen.encodeToByteArray())
                escribirAtomico(cachePortada, portada.encodeToByteArray())
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
    private val publicacionesLocales = java.io.File(carpeta, archivoPublicaciones)
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
    fun publicar(publicacion: PublicacionSubida, listo: () -> Unit) = scope.launch {
        if (publicando.value) return@launch
        publicando.value = true
        val marca = sesion.actual()
        try { intentar {
            require(validar(publicacion.datos))
            // Guardar antes del envío permite recuperar también una respuesta perdida.
            if (publicacion !in instantaneas) instantaneas.add(publicacion)
            escribirAtomico(publicacionesLocales,
                JsonSocial.encodeToString(kotlinx.serialization.builtins.ListSerializer(PublicacionSubida.serializer()), instantaneas.toList()).encodeToByteArray())
            cliente().subirCambios(listOf(publicacion), emptyList())
            if (sesion.vigente(marca)) { cargarIdentidad(); cargarFeed(); listo() }
        } } finally { publicando.value = false }
    }
    fun borrar(id: Long) = lanzar {
        cliente().borrarPublicacion(id)
        ocultar(id); cargarIdentidad(); cargarFeed()
    }
    fun retirar(clave: String, id: Long? = null) = lanzar {
        cliente().subirCambios(emptyList(), listOf(clave))
        if (id != null) ocultar(id)
        cargarIdentidad(); cargarFeed()
    }
    fun vistaPublicacion(id: Long) = lanzar { cliente().vio(listOf(id)) }
    /** «Útil» en una reseña ajena (La Cucha); la pantalla lo marca al momento. */
    fun util(id: Long, si: Boolean) = lanzar { cliente().util(id, quitar = !si) }
    /** Sugerencias de lugares que ya existen con ese nombre y ciudad. */
    suspend fun buscarLugares(nombre: String, ciudad: String): List<Lugar> = cliente().lugares(nombre, ciudad)
    /** Corrige la dirección de un lugar; [listo] recibe false si no se pudo (por ejemplo, no lo creó esta cuenta). */
    fun corregirLugar(id: Long, direccion: String, listo: (Boolean) -> Unit) = scope.launch {
        val bien = runCatching { cliente().corregirLugar(id, direccion.trim().take(200)) }.isSuccess
        if (bien) { cargarFeed(); cargarIdentidad() }
        listo(bien)
    }
    /** Crea el lugar o devuelve el que ya existe; lanza si no se pudo (sin red, límite diario…). */
    suspend fun crearLugar(nuevo: LugarNuevo): Lugar {
        val marca = sesion.actual()
        val lugar = cliente().crearLugar(nuevo)
        check(sesion.vigente(marca))
        return lugar
    }
    fun visibilidad(clave: String) = almacen.visibilidad(clave)

    /** El chat cifrado de este perfil (el mismo de El Cajón: mismas llaves de la cuenta, compartidas cifradas). */
    private var chatCreado: ChatSocial? = null
    fun chat(contexto: android.content.Context, decir: (String) -> Unit): ChatSocial = chatCreado ?: ChatSocial(contexto.applicationContext, scope,
        java.io.File(carpeta, "chats.json"), object : AlmacenChat {
            override var chatLlaves: String?
                get() = almacen.chatLlaves
                set(v) { almacen.chatLlaves = v }
            override val llaveCuenta: String? get() = almacen.llaveCopia
            override val permisoRed = MutableStateFlow(true)
        }, ::cliente, usuario, decir = decir).also { chatCreado = it }
    /** La canción del perfil de esta app (con su avance de Apple): se cambia sola, sin tocar las demás partes. */
    fun ponerCancionPerfil(c: CancionSocial?) = lanzar {
        val datos = c?.let { kotlinx.serialization.json.buildJsonObject {
            put("cancion", kotlinx.serialization.json.JsonPrimitive(it.titulo)); put("creador", kotlinx.serialization.json.JsonPrimitive(it.artista))
            put("titulo", kotlinx.serialization.json.JsonPrimitive(it.album)); put("portada", kotlinx.serialization.json.JsonPrimitive(it.portada))
            put("audio", kotlinx.serialization.json.JsonPrimitive(it.audio)); put("enlace", kotlinx.serialization.json.JsonPrimitive(it.enlace))
        } }
        cliente().subirPerfilApp(CambioPerfilApp(partes = datos?.let { mapOf("cancion" to ParteSubida("publica", it)) } ?: emptyMap(),
            fusionar = true, quitar = if (c == null) listOf("cancion") else emptyList()))
        cargarIdentidad()
    }
    fun publicarPartes(partes: Map<String, ParteSubida>) = lanzar {
        val marca = sesion.actual()
        cliente().subirPerfilApp(CambioPerfilApp(partes = partes))
        if (!sesion.vigente(marca)) return@lanzar
        partesVisibles.forEach { almacen.guardarVisibilidad(it, partes[it]?.visibilidad ?: "privado") }
        cargarIdentidad()
    }
    fun crear(usuario: String, nombre: String, clave: String, foto: ByteArray?) = lanzar {
        val acceso = acceso.value
        if (entro(clienteAcceso().entrar(acceso.correo, acceso.codigo, usuario, nombre, politica, clave))) {
            // Con la contraseña de la cuenta nueva, lo de la app se guarda en la cuenta desde ya (D-033).
            if (clave.isNotBlank()) runCatching { llaveConClave(clave) }
            if (foto != null) cliente().subirFoto(foto)
        }
    }
    fun ponerClave(nueva: String, actual: String, listo: () -> Unit) = lanzar {
        cliente().ponerClave(nueva, actual)
        // Si la cuenta ya tenía llave y este teléfono no, primero se abre con la contraseña actual.
        if (almacen.llaveCopia == null && actual.isNotBlank()) runCatching { llaveConClave(actual) }
        llaveNueva(nueva)
        revisarServidor(); listo()
    }
    fun cambiarUsuario(nuevo: String, listo: () -> Unit) = lanzar { cliente().cambiarUsuario(nuevo); revisarServidor(); listo() }
    fun vincular(codigo: String) = lanzar { cliente().vincularCorreo(acceso.value.correo, codigo); revisarServidor() }
    fun salir() = lanzar { cliente().salir(); olvidarCuenta() }
    fun invalidar() { sesion.invalidar() }
}

/** Los avisos de la red en los textos de la app (core/social no tiene recursos propios). */
data class TextosRed(val error: Int, val perfilCambio: Int, val operacionLista: Int, val otroPerfil: Int)
