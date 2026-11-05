package io.github.fogonapps.cajon

import android.app.Application
import io.github.fogonapps.cajon.datos.AvisoSocial
import io.github.fogonapps.cajon.datos.CajaFuerte
import io.github.fogonapps.cajon.datos.ClienteSocial
import io.github.fogonapps.cajon.datos.ComentarioSocial
import io.github.fogonapps.cajon.datos.ErrorSocial
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.PartesPerfil
import io.github.fogonapps.cajon.datos.ParteSubida
import io.github.fogonapps.cajon.datos.PerfilSocial
import io.github.fogonapps.cajon.datos.PerfilSubido
import io.github.fogonapps.cajon.datos.PersonaSocial
import io.github.fogonapps.cajon.datos.PublicacionPropia
import io.github.fogonapps.cajon.datos.PublicacionSocial
import io.github.fogonapps.cajon.datos.PublicacionSubida
import io.github.fogonapps.cajon.datos.Visibilidad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

/** Cómo va entrar a la red: pedir el correo, poner el código, elegir el usuario (cuenta nueva). */
/**
 * Los pasos para entrar: el correo; si no tiene cuenta, decírselo (y registrarse); si tiene contraseña, la contraseña;
 * el código (para registrarse, si olvidó la contraseña o si la cuenta todavía no tenía); y, si es nueva, el usuario.
 */
enum class PasoAcceso { CORREO, NO_EXISTE, CLAVE, CODIGO, USUARIO }

/** Para qué es el código: cuenta nueva, olvidó la contraseña o la cuenta no tenía (en esos dos se pone una nueva). */
enum class MotivoCodigo { NUEVA, OLVIDO, SIN_CLAVE }

data class Acceso(
    val paso: PasoAcceso = PasoAcceso.CORREO, val correo: String = "", val codigo: String = "", val ocupado: Boolean = false,
    val motivo: MotivoCodigo = MotivoCodigo.NUEVA,
)

/**
 * La red de El Cajón (Fogón Social): la cuenta, el feed, los perfiles de otras personas, seguir, me gusta,
 * comentarios, guardados, avisos y lo que se publica. Todo es opcional: sin cuenta, nada de esto sale del teléfono.
 * Lo del cajón que la persona marcó como público o para sus seguidores se sube solo ([sincronizar]).
 */
class RedSocial(
    private val app: Application,
    private val scope: CoroutineScope,
    private val caja: CajaFuerte,
    private val estado: () -> Estado?,
    private val cambiar: ((Estado) -> Estado) -> Unit,
    private val decir: (Int) -> Unit,
    /** Sincronizar el cajón guardado en la cuenta (lo hace el ViewModel). */
    private val sincronizarCajon: () -> Unit = {},
) {
    /** El cajón guardado en la cuenta necesita la contraseña para abrirse en este teléfono (entró con el código). */
    private val _cajonBloqueado = MutableStateFlow(false)
    val cajonBloqueado: StateFlow<Boolean> = _cajonBloqueado

    /**
     * Arma o abre la llave del cajón con la contraseña: si el servidor ya tiene una llave envuelta, la abre; si no,
     * crea una (o usa la de este teléfono) y la sube envuelta. Luego sincroniza.
     */
    private suspend fun llaveConClave(clave: String) {
        val enServidor = cliente().cajon().llave
        val llave = if (enServidor.isNotBlank()) withContext(kotlinx.coroutines.Dispatchers.Default) { io.github.fogonapps.cajon.datos.Nube.desenvolver(enServidor, clave) }
        else (caja.llaveCajon ?: io.github.fogonapps.cajon.datos.Nube.llaveNueva()).also {
            cliente().guardarLlaveCajon(withContext(kotlinx.coroutines.Dispatchers.Default) { io.github.fogonapps.cajon.datos.Nube.envolver(it, clave) })
        }
        caja.llaveCajon = llave
        _cajonBloqueado.value = false
        sincronizarCajon()
    }

    /** Con la contraseña, abre en este teléfono el cajón guardado en la cuenta. */
    fun desbloquearCajon(clave: String) = scope.launch {
        try { intentar { llaveConClave(clave) } } catch (x: Exception) { decir(R.string.red_clave_mala) }
    }

    /** Después de entrar: si la cuenta ya tiene el cajón guardado y este teléfono no tiene la llave, pide la contraseña. */
    private suspend fun revisarCajon() {
        if (caja.llaveCajon != null) { sincronizarCajon(); return }
        _cajonBloqueado.value = runCatching { cliente().cajon().llave.isNotBlank() }.getOrDefault(false)
    }
    private fun cliente() = ClienteSocial(caja.tokenSocial)

    private val _usuario = MutableStateFlow(caja.usuarioSocial.takeIf { caja.tokenSocial != null })
    /** El @usuario de la cuenta en este teléfono, o null si no hay cuenta. */
    val usuario: StateFlow<String?> = _usuario

    /** Si el servidor puede mandar códigos por correo (null mientras no se sabe), y la versión de la política. */
    private val _correo = MutableStateFlow<Boolean?>(null)
    val correoDisponible: StateFlow<Boolean?> = _correo
    var politica = POLITICA_CONOCIDA; private set

    private val _acceso = MutableStateFlow(Acceso())
    val acceso: StateFlow<Acceso> = _acceso

    private val _feed = MutableStateFlow<List<PublicacionSocial>?>(null)
    val feed: StateFlow<List<PublicacionSocial>?> = _feed
    private val _explorar = MutableStateFlow<List<PublicacionSocial>?>(null)
    val explorar: StateFlow<List<PublicacionSocial>?> = _explorar
    private val _perfiles = MutableStateFlow<Map<String, PerfilSocial>>(emptyMap())
    val perfiles: StateFlow<Map<String, PerfilSocial>> = _perfiles
    private val _vistas = MutableStateFlow<Map<Long, PublicacionSocial>>(emptyMap())
    val vistas: StateFlow<Map<Long, PublicacionSocial>> = _vistas
    private val _comentarios = MutableStateFlow<Map<Long, List<ComentarioSocial>>>(emptyMap())
    val comentarios: StateFlow<Map<Long, List<ComentarioSocial>>> = _comentarios
    private val _avisos = MutableStateFlow<List<AvisoSocial>?>(null)
    val avisos: StateFlow<List<AvisoSocial>?> = _avisos
    private val _solicitudes = MutableStateFlow<List<PersonaSocial>>(emptyList())
    val solicitudes: StateFlow<List<PersonaSocial>> = _solicitudes
    private val _sinLeer = MutableStateFlow(0)
    val sinLeer: StateFlow<Int> = _sinLeer
    private val _guardados = MutableStateFlow<List<PublicacionSocial>?>(null)
    val guardados: StateFlow<List<PublicacionSocial>?> = _guardados
    /** "seguidores:<usuario>" o "siguiendo:<usuario>" (o "bloqueados") → la lista. */
    private val _gente = MutableStateFlow<Map<String, List<PersonaSocial>>>(emptyMap())
    val gente: StateFlow<Map<String, List<PersonaSocial>>> = _gente
    private val _privada = MutableStateFlow(false)
    val privada: StateFlow<Boolean> = _privada
    private val _cuenta = MutableStateFlow<io.github.fogonapps.cajon.datos.MiCuenta?>(null)
    /** Lo de la cuenta en el servidor: correo, si tiene contraseña, desde cuándo puede cambiar el usuario. */
    val cuenta: StateFlow<io.github.fogonapps.cajon.datos.MiCuenta?> = _cuenta
    private val _correoCuenta = MutableStateFlow("")
    val correoCuenta: StateFlow<String> = _correoCuenta
    private val _notas = MutableStateFlow<List<io.github.fogonapps.cajon.datos.NotaSocial>>(emptyList())
    /** Las notas de 24 horas de tu gente (la tuya primero). */
    val notas: StateFlow<List<io.github.fogonapps.cajon.datos.NotaSocial>> = _notas
    private val _publicando = MutableStateFlow(false)
    val publicando: StateFlow<Boolean> = _publicando

    /** Hace [trabajo] y, si falla, lo dice con un aviso corto. Devuelve si salió bien. */
    private suspend fun intentar(trabajo: suspend () -> Unit): Boolean = try { trabajo(); true } catch (x: ErrorSocial) {
        if (x.codigo == 401) { olvidarCuenta(); decir(R.string.red_sesion_vencida) }
        else decir(when (x.error) {
            "usuario_tomado" -> R.string.red_usuario_tomado
            "usuario" -> R.string.red_usuario_malo
            "correo" -> R.string.red_correo_malo
            "codigo" -> R.string.red_codigo_malo
            "codigo_vencido", "sin_codigo" -> R.string.red_codigo_vencido
            "correo_tomado" -> R.string.red_correo_tomado
            "despacio" -> R.string.red_despacio
            "clave_mala" -> R.string.red_clave_mala
            "clave_corta" -> R.string.red_clave_corta
            "usuario_espera" -> R.string.red_usuario_espera
            "suspendida", "cerrada" -> R.string.red_suspendida
            "restringida" -> R.string.red_restringida
            "privada" -> R.string.red_es_privada
            "no_existe" -> R.string.red_no_existe
            else -> R.string.red_error
        })
        false
    } catch (x: IOException) { decir(R.string.red_sin_conexion); false }

    private fun lanzar(trabajo: suspend () -> Unit) = scope.launch { intentar(trabajo) }

    /* ----- La cuenta ----- */

    fun revisarServidor() = scope.launch {
        runCatching { ClienteSocial().info() }.getOrNull()?.let { i ->
            _correo.value = i.correo == "true"
            if (i.politica.isNotBlank()) politica = i.politica
        }
        // Cuentas creadas antes de que existieran estas partes: lo que hace que el perfil tenga sentido queda a la vista, una sola vez
        // (lo que la persona ya eligió se respeta).
        if (_usuario.value != null && !caja.partesRedPuestas) {
            cambiar { e -> e.copy(perfil = e.perfil.copy(visibilidad = PartesPerfil.AL_CREAR_CUENTA + e.perfil.visibilidad, cambio = System.currentTimeMillis())) }
            caja.partesRedPuestas = true
        }
        if (_usuario.value != null) scope.launch { revisarCajon() }
        if (_usuario.value != null) intentar {
            val c = cliente().miCuenta()
            _privada.value = c.privada; _correoCuenta.value = c.correo; _cuenta.value = c
        }
    }

    /** Cuenta nueva sin correo (cuando el servidor no manda códigos). */
    fun crearSinCorreo(usuario: String, nombre: String) = lanzar {
        _acceso.value = _acceso.value.copy(ocupado = true)
        try { entro(ClienteSocial().crearSinCorreo(usuario.trim().lowercase(), nombre.trim(), politica)) }
        finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** El primer paso: con el correo se sabe si se registra, si entra con su contraseña o si tiene que crear una. */
    fun revisarCorreo(correo: String) = lanzar {
        val c = correo.trim().lowercase()
        _acceso.value = Acceso(correo = c, ocupado = true)
        try {
            val e = ClienteSocial().estadoCorreo(c)
            when {
                !e.existe -> _acceso.value = Acceso(PasoAcceso.NO_EXISTE, c)
                e.conClave -> _acceso.value = Acceso(PasoAcceso.CLAVE, c)
                else -> { ClienteSocial().pedirCodigo(c); _acceso.value = Acceso(PasoAcceso.CODIGO, c, motivo = MotivoCodigo.SIN_CLAVE) }
            }
        } finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** Para ponerle correo a una cuenta hecha sin él: manda el código a [correo]. */
    fun pedirCodigoVincular(correo: String) = lanzar {
        ClienteSocial().pedirCodigo(correo.trim())
        _acceso.value = Acceso(PasoAcceso.CODIGO, correo.trim())
    }

    /** Manda el código al correo ([motivo]: registrarse u olvidó la contraseña). */
    fun pedirCodigo(motivo: MotivoCodigo) = lanzar {
        val c = _acceso.value.correo
        _acceso.value = _acceso.value.copy(ocupado = true)
        try {
            ClienteSocial().pedirCodigo(c)
            _acceso.value = Acceso(PasoAcceso.CODIGO, c, motivo = motivo)
        } finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /**
     * Con el código: si es cuenta nueva, pasa a elegir el usuario; si olvidó la contraseña o no tenía, entra con la
     * [claveNueva] y con ella guarda la llave del cajón (si este teléfono no tenía la llave, lo guardado antes en la
     * cuenta se reemplaza con lo de este teléfono, porque sin la contraseña vieja no se puede abrir).
     */
    fun entrar(codigo: String, claveNueva: String = "") = scope.launch {
        val a = _acceso.value
        _acceso.value = a.copy(ocupado = true)
        try {
            if (a.motivo == MotivoCodigo.NUEVA) {
                try { entro(ClienteSocial().entrar(a.correo, codigo.trim())) }
                catch (x: ErrorSocial) { if (x.error == "cuenta_nueva") _acceso.value = Acceso(PasoAcceso.USUARIO, a.correo, codigo.trim()) else throw x }
            } else {
                entro(ClienteSocial().entrar(a.correo, codigo.trim(), clave = claveNueva))
                llaveNueva(claveNueva)
            }
        } catch (x: ErrorSocial) { intentar { throw x } }
        catch (x: IOException) { decir(R.string.red_sin_conexion) }
        finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** La llave del cajón envuelta con una contraseña recién puesta (la de este teléfono, o una nueva). */
    private suspend fun llaveNueva(clave: String) {
        val llave = caja.llaveCajon ?: io.github.fogonapps.cajon.datos.Nube.llaveNueva()
        cliente().guardarLlaveCajon(withContext(kotlinx.coroutines.Dispatchers.Default) { io.github.fogonapps.cajon.datos.Nube.envolver(llave, clave) })
        caja.llaveCajon = llave
        _cajonBloqueado.value = false
        sincronizarCajon()
    }

    /** Cuenta nueva con el correo ya comprobado y su contraseña (con ella el cajón se guarda en la cuenta desde ya). */
    fun crearConCorreo(usuario: String, nombre: String, clave: String) = lanzar {
        val a = _acceso.value
        _acceso.value = a.copy(ocupado = true)
        try {
            entro(ClienteSocial().entrar(a.correo, a.codigo, usuario.trim().lowercase(), nombre.trim(), politica, clave))
            llaveNueva(clave)
        } finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    fun volverAlCorreo() { _acceso.value = Acceso() }

    fun entrarConClave(correo: String, clave: String) = lanzar {
        _acceso.value = _acceso.value.copy(ocupado = true)
        try {
            entro(ClienteSocial().entrarConClave(correo.trim(), clave))
            runCatching { llaveConClave(clave) }   // con la contraseña a mano, el cajón de la cuenta se abre solo
        } finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** Crea o cambia la contraseña; con ella, la llave del cajón se vuelve a envolver (y si no había, se crea). */
    fun ponerClave(nueva: String, actual: String, listo: () -> Unit) = lanzar {
        val llaveAntes = caja.llaveCajon ?: actual.takeIf { it.isNotBlank() }?.let { a ->
            runCatching { cliente().cajon().llave.takeIf { it.isNotBlank() }?.let { io.github.fogonapps.cajon.datos.Nube.desenvolver(it, a) } }.getOrNull()
        }
        cliente().ponerClave(nueva, actual)
        val llave = llaveAntes ?: io.github.fogonapps.cajon.datos.Nube.llaveNueva()
        cliente().guardarLlaveCajon(withContext(kotlinx.coroutines.Dispatchers.Default) { io.github.fogonapps.cajon.datos.Nube.envolver(llave, nueva) })
        caja.llaveCajon = llave
        _cajonBloqueado.value = false
        decir(R.string.red_clave_lista); revisarServidor(); sincronizarCajon(); listo()
    }

    fun cambiarUsuario(nuevo: String, listo: () -> Unit) = lanzar {
        val u = cliente().cambiarUsuario(nuevo.trim().lowercase())
        caja.usuarioSocial = u; _usuario.value = u
        cambiar { e -> e.copy(perfil = e.perfil.copy(usuario = u, cambio = System.currentTimeMillis())) }
        decir(R.string.red_usuario_cambiado); revisarServidor(); listo()
    }

    private fun entro(a: io.github.fogonapps.cajon.datos.Acceso) {
        caja.tokenSocial = a.token
        caja.usuarioSocial = a.usuario
        caja.politicaSocial = politica
        caja.partesRedPuestas = true
        _usuario.value = a.usuario
        _acceso.value = Acceso()
        AvisosRed.programar(app)
        // El usuario de la red es el del perfil; y lo que hace que el perfil tenga sentido queda a la vista (si no eligió otra cosa).
        cambiar { e -> e.copy(perfil = e.perfil.copy(usuario = a.usuario, visibilidad = PartesPerfil.AL_CREAR_CUENTA + e.perfil.visibilidad, cambio = System.currentTimeMillis())) }
        decir(if (a.nueva) R.string.red_bienvenida else R.string.red_entraste)
        sincronizarYa()
        cargarFeed()
        revisarServidor()
        scope.launch { revisarCajon() }
    }

    /** Ponerle correo a una cuenta hecha sin él: primero [pedirCodigo] y luego esto con el código. */
    fun vincularCorreo(codigo: String) = lanzar {
        cliente().vincularCorreo(_acceso.value.correo, codigo.trim())
        _correoCuenta.value = _acceso.value.correo
        _acceso.value = Acceso()
        decir(R.string.red_correo_vinculado)
    }

    fun salir() = scope.launch { runCatching { cliente().salir() }; olvidarCuenta() }

    fun borrarCuenta() = lanzar { cliente().borrarCuenta(); olvidarCuenta(); decir(R.string.red_cuenta_borrada) }

    private fun olvidarCuenta() {
        caja.tokenSocial = null; caja.usuarioSocial = null; caja.publicacionesSubidas = emptySet(); caja.llaveCajon = null; _cajonBloqueado.value = false
        _usuario.value = null
        AvisosRed.programar(app)
        _feed.value = null; _avisos.value = null; _guardados.value = null; _sinLeer.value = 0
        _perfiles.value = emptyMap(); _gente.value = emptyMap(); _correoCuenta.value = ""
    }

    fun cambiarPrivada(si: Boolean) = lanzar { cliente().privada(si); _privada.value = si }

    /* ----- Subir lo del cajón ----- */

    private var subida: Job? = null
    /** Lo último que se subió, para no subir lo mismo cada vez que cambia algo que no se comparte. */
    private var ultimo: Pair<PerfilSubido, List<PublicacionSubida>>? = null
    private var ultimaFoto: String? = null

    /** Después de un cambio en el cajón: sube lo compartido (perfil y publicaciones) a los pocos segundos. */
    fun sincronizar() {
        if (_usuario.value == null) return
        subida?.cancel()
        subida = scope.launch { delay(5000); subirAhora() }
    }

    fun sincronizarYa() { subida?.cancel(); subida = scope.launch { subirAhora() } }

    private suspend fun subirAhora() {
        val e = estado() ?: return
        if (_usuario.value == null) return
        intentar {
            val ahora = perfilDe(e).copy(portada = portadaEnLaRed(e.perfil.portada)) to publicacionesDe(e)
            if (ahora != ultimo) {
                cliente().subirPerfil(ahora.first)
                // Solo se quita de la red lo que este teléfono había subido y ya no está; nunca lo que no conoce.
                val claves = ahora.second.map { it.clave }.toSet()
                cliente().subirCambios(ahora.second, (caja.publicacionesSubidas - claves).toList())
                caja.publicacionesSubidas = claves
                ultimo = ahora
            }
            val foto = e.perfil.foto
            if (foto != ultimaFoto) {
                if (foto.isBlank()) cliente().quitarFoto()
                else jpeg(foto, 512, 400_000)?.let { cliente().subirFoto(it) }
                ultimaFoto = foto
            }
        }
    }

    /**
     * La portada como la ve la red: si es una imagen del teléfono, se sube una vez (JPEG de hasta 1600 px) y queda su
     * dirección en el servidor; si ya es de internet, esa misma.
     */
    private suspend fun portadaEnLaRed(portada: String): String {
        if (portada.isBlank() || portada.startsWith("https://")) return portada.takeIf { it.startsWith("https://") }.orEmpty()
        caja.portadaSubida?.split('|')?.takeIf { it.size == 2 && it[0] == portada }?.let { return it[1] }
        val jpg = jpeg(portada, 1600, 900_000) ?: return ""
        val url = ClienteSocial.imagen(cliente().subirImagen(jpg))
        caja.portadaSubida = "$portada|$url"
        return url
    }

    /* ----- Feed, perfiles y publicaciones ----- */

    fun cargarFeed() {
        if (_usuario.value == null) return
        lanzar { _feed.value = cliente().feed() }
    }

    fun masFeed() = lanzar {
        val ya = _feed.value ?: return@lanzar
        val mas = cliente().feed(ya.lastOrNull()?.cuando)
        _feed.value = (ya + mas).distinctBy { it.id }
    }

    fun cargarExplorar() = lanzar { _explorar.value = cliente().explorar() }

    /** Los @usuarios que se buscaron y no existen (para decirlo en vez de quedarse cargando). */
    private val _noEstan = MutableStateFlow<Set<String>>(emptySet())
    val noEstan: StateFlow<Set<String>> = _noEstan

    fun verPerfil(usuario: String) = scope.launch {
        try { _perfiles.value = _perfiles.value + (usuario to cliente().perfil(usuario)) }
        catch (x: ErrorSocial) { if (x.codigo == 404) _noEstan.value = _noEstan.value + usuario else intentar { throw x } }
        catch (x: IOException) { decir(R.string.red_sin_conexion) }
    }

    fun cargarPublicacion(id: Long) = lanzar {
        _vistas.value = _vistas.value + (id to cliente().publicacion(id))
        _comentarios.value = _comentarios.value + (id to cliente().comentarios(id))
    }

    /** Cambia una publicación en todas las listas donde esté (para que el corazón responda al instante). */
    private fun tocar(id: Long, f: (PublicacionSocial) -> PublicacionSocial) {
        fun l(x: List<PublicacionSocial>?) = x?.map { if (it.id == id) f(it) else it }
        _feed.value = l(_feed.value); _explorar.value = l(_explorar.value); _guardados.value = l(_guardados.value)
        _vistas.value = _vistas.value.mapValues { if (it.key == id) f(it.value) else it.value }
        _perfiles.value = _perfiles.value.mapValues { (_, p) -> p.copy(publicaciones = l(p.publicaciones)!!) }
    }

    fun buscarPublicacion(id: Long): PublicacionSocial? =
        _vistas.value[id] ?: (_feed.value.orEmpty() + _explorar.value.orEmpty() + _guardados.value.orEmpty() + _perfiles.value.values.flatMap { it.publicaciones }).firstOrNull { it.id == id }

    fun meGusta(p: PublicacionSocial) {
        val si = !p.yoMeGusta
        tocar(p.id) { it.copy(yoMeGusta = si, megusta = it.megusta + if (si) 1 else -1) }
        scope.launch { if (!intentar { cliente().meGusta(p.id, si) }) tocar(p.id) { it.copy(yoMeGusta = !si, megusta = it.megusta + if (si) -1 else 1) } }
    }

    fun guardar(p: PublicacionSocial) {
        val si = !p.yoGuardo
        tocar(p.id) { it.copy(yoGuardo = si) }
        scope.launch {
            if (intentar { cliente().guardar(p.id, si) }) { if (si) decir(R.string.red_guardada) }
            else tocar(p.id) { it.copy(yoGuardo = !si) }
        }
    }

    fun cargarGuardados() = lanzar { _guardados.value = cliente().guardados() }

    /** Las guardadas que alguien muestra en su perfil, y lo que lleva una #etiqueta (clave → lista). */
    private val _listas = MutableStateFlow<Map<String, List<PublicacionSocial>>>(emptyMap())
    val listas: StateFlow<Map<String, List<PublicacionSocial>>> = _listas
    fun cargarGuardadasDe(usuario: String) = lanzar { _listas.value = _listas.value + ("guardadas:$usuario" to cliente().guardadasDe(usuario)) }
    /** Las reseñas de la gente sobre una obra (por su fuente), en [listas] con la clave "resenas:<fuente>". */
    fun cargarResenas(fuente: String) = lanzar { _listas.value = _listas.value + ("resenas:$fuente" to ClienteSocial(caja.tokenSocial).resenas(fuente)) }
    fun cargarEtiqueta(etiqueta: String) = lanzar { _listas.value = _listas.value + ("#$etiqueta" to cliente().conEtiqueta(etiqueta)) }

    fun comentar(id: Long, texto: String) = lanzar {
        cliente().comentar(id, texto.trim())
        _comentarios.value = _comentarios.value + (id to cliente().comentarios(id))
        tocar(id) { it.copy(comentarios = it.comentarios + 1) }
    }

    fun borrarComentario(pub: Long, id: Long) = lanzar {
        cliente().borrarComentario(id)
        _comentarios.value = _comentarios.value + (pub to cliente().comentarios(pub))
        tocar(pub) { it.copy(comentarios = (it.comentarios - 1).coerceAtLeast(0)) }
    }

    /* ----- Seguir ----- */

    fun seguir(usuario: String) = lanzar {
        val r = cliente().seguir(usuario)
        decir(if (r == "pendiente") R.string.red_solicitud_enviada else R.string.red_ahora_sigues)
        _perfiles.value = _perfiles.value + (usuario to cliente().perfil(usuario))
        cargarFeed()
    }

    fun dejarDeSeguir(usuario: String) = lanzar {
        cliente().dejarDeSeguir(usuario)
        _perfiles.value = _perfiles.value + (usuario to cliente().perfil(usuario))
        cargarFeed()
    }

    fun aceptar(usuario: String) = lanzar { cliente().aceptar(usuario); _solicitudes.value = cliente().solicitudes() }
    fun rechazar(usuario: String) = lanzar { cliente().rechazar(usuario); _solicitudes.value = cliente().solicitudes() }

    fun quitarSeguidor(usuario: String) = lanzar {
        cliente().quitarSeguidor(usuario)
        _usuario.value?.let { yo -> _gente.value = _gente.value + ("seguidores:$yo" to cliente().seguidores(yo)) }
    }

    fun cargarGente(usuario: String, seguidores: Boolean) = lanzar {
        val clave = (if (seguidores) "seguidores:" else "siguiendo:") + usuario
        _gente.value = _gente.value + (clave to if (seguidores) cliente().seguidores(usuario) else cliente().siguiendo(usuario))
    }

    fun bloquear(usuario: String, si: Boolean) = lanzar {
        cliente().bloquear(usuario, si)
        decir(if (si) R.string.red_bloqueado else R.string.red_desbloqueado)
        _perfiles.value = _perfiles.value - usuario
        _gente.value = _gente.value + ("bloqueados" to cliente().bloqueados())
        cargarFeed()
    }

    fun cargarBloqueados() = lanzar { _gente.value = _gente.value + ("bloqueados" to cliente().bloqueados()) }

    fun reportar(que: String, objeto: String, motivo: String) = lanzar { cliente().reportar(que, objeto, motivo); decir(R.string.red_reportado) }

    /* ----- Lo que la persona dice de lo que ve (todo en el teléfono, como sus gustos) ----- */

    private val _ocultas = MutableStateFlow<Set<Long>>(emptySet())
    /** Publicaciones que escondió (o reportó) en esta sesión: dejan de verse al momento. */
    val ocultas: StateFlow<Set<Long>> = _ocultas
    fun ocultar(id: Long) { _ocultas.value = _ocultas.value + id }

    /** "Me interesa" / "No me interesa": se guarda como un gusto de la obra o del creador de la publicación. */
    fun interesa(p: PublicacionSocial, si: Boolean) {
        val fuente = (p.datos["fuente"] as? JsonPrimitive)?.content.orEmpty()
        val creador = (p.datos["creador"] as? JsonPrimitive)?.content.orEmpty()
        val titulo = (p.datos["titulo"] as? JsonPrimitive)?.content.orEmpty()
        val (clave, nombre) = when {
            fuente.isNotBlank() -> "obra:$fuente" to titulo
            creador.isNotBlank() -> "artista:${creador.lowercase()}" to creador
            else -> "red:${p.autor.usuario}" to "@${p.autor.usuario}"
        }
        val senal = if (si) io.github.fogonapps.cajon.datos.Senales.GUSTA else io.github.fogonapps.cajon.datos.Senales.NO_INTERESA
        cambiar { e -> e.copy(gustos = e.gustos.filter { it.clave != clave } + io.github.fogonapps.cajon.datos.Gusto(clave, nombre, senal, cambio = System.currentTimeMillis())) }
        if (!si) ocultar(p.id)
        decir(if (si) R.string.menu_anotado_interesa else R.string.menu_anotado_no)
    }

    /** Silenciar a alguien: sus publicaciones no salen en Explorar ni en el feed (sin que se entere). */
    fun silenciar(usuario: String) {
        cambiar { e -> e.copy(gustos = e.gustos.filter { it.clave != "red:$usuario" } +
            io.github.fogonapps.cajon.datos.Gusto("red:$usuario", "@$usuario", io.github.fogonapps.cajon.datos.Senales.EXCLUIR, cambio = System.currentTimeMillis())) }
        decir(R.string.menu_silenciado)
    }

    /* ----- Vistas y notas ----- */

    private val porContar = java.util.Collections.synchronizedSet(LinkedHashSet<Long>())
    private var contando: Job? = null
    /** Una publicación que se vio en pantalla: se juntan y se mandan juntas a los 3 segundos. */
    fun vista(id: Long) {
        if (_usuario.value == null || !porContar.add(id)) return
        contando?.cancel()
        contando = scope.launch { delay(3000); val ids = synchronized(porContar) { porContar.toList() }; runCatching { cliente().vio(ids) } }
    }

    fun cargarNotas() { if (_usuario.value != null) lanzar { _notas.value = cliente().notas() } }

    /** Pone (o quita, si está vacía) tu nota de 24 horas; con [obra], va con su portada. */
    fun ponerNota(texto: String, obra: Obra? = null) = lanzar {
        cliente().ponerNota(io.github.fogonapps.cajon.datos.NotaNueva(texto.trim().take(60), obra?.let(::obraBreve) ?: JsonObject(emptyMap())))
        _notas.value = cliente().notas()
    }

    /** Quita de la red una entrada del diario o una lista (la vuelve privada en el cajón). */
    fun quitarDeLaRed(clave: String) {
        when {
            clave.startsWith("r:") -> { val (_, obra, reg) = clave.split(":", limit = 3)
                cambiar { e -> e.copy(obras = e.obras.map { o -> if (o.id != obra) o else o.copy(cambio = System.currentTimeMillis(),
                    registros = o.registros.map { if (it.id == reg) it.copy(visibilidad = Visibilidad.PRIVADA) else it }) }) } }
            clave.startsWith("l:") -> { val id = clave.removePrefix("l:")
                cambiar { e -> e.copy(listas = e.listas.map { if (it.id == id) it.copy(visibilidad = Visibilidad.PRIVADA, cambio = System.currentTimeMillis()) else it }) } }
            clave.startsWith("f:") -> { borrarPropia(clave.removePrefix("f:")); return }
        }
        sincronizarYa(); cargarFeed(); decir(R.string.red_quitada)
    }

    /* ----- Moderación ----- */

    private val _estadoCuenta = MutableStateFlow<io.github.fogonapps.cajon.datos.EstadoCuentaSocial?>(null)
    val estadoCuenta: StateFlow<io.github.fogonapps.cajon.datos.EstadoCuentaSocial?> = _estadoCuenta
    fun cargarEstadoCuenta() = lanzar { _estadoCuenta.value = cliente().estadoCuenta() }

    private val _casos = MutableStateFlow<List<io.github.fogonapps.cajon.datos.CasoModeracion>?>(null)
    val casos: StateFlow<List<io.github.fogonapps.cajon.datos.CasoModeracion>?> = _casos
    fun cargarCasos() = lanzar { _casos.value = cliente().casos(); _frasesPendientes.value = cliente().frasesPendientes() }
    private val _frasesPendientes = MutableStateFlow<List<io.github.fogonapps.cajon.datos.FraseComunidad>>(emptyList())
    /** Las frases que propuso la gente y esperan que alguien del equipo las apruebe. */
    val frasesPendientes: StateFlow<List<io.github.fogonapps.cajon.datos.FraseComunidad>> = _frasesPendientes
    fun decidirFrase(clave: String, aprobar: Boolean) = lanzar {
        cliente().decidirFrase(clave, aprobar); _frasesPendientes.value = _frasesPendientes.value.filter { it.clave != clave }
    }
    fun decidir(c: io.github.fogonapps.cajon.datos.CasoModeracion, decision: String) = lanzar {
        val regla = c.motivos.maxByOrNull { it.value }?.key.orEmpty()
        cliente().decidir(c.que, c.objeto, decision, regla)
        _casos.value = _casos.value?.filterNot { it.que == c.que && it.objeto == c.objeto }
    }

    /* ----- Avisos ----- */

    fun cargarAvisos() = lanzar {
        _avisos.value = cliente().avisos()
        _solicitudes.value = cliente().solicitudes()
        cliente().leidos()
        _sinLeer.value = 0
    }

    fun borrarAviso(id: Long?) = lanzar {
        cliente().borrarAvisos(id)
        _avisos.value = if (id == null) emptyList() else _avisos.value?.filter { it.id != id }
    }

    fun revisarSinLeer() {
        if (_usuario.value == null) return
        scope.launch { runCatching { _sinLeer.value = cliente().sinLeer(); AvisosRed.vistoAvisos(app, _sinLeer.value) } }
    }

    /* ----- Publicar a mano ----- */

    /** Publica unas palabras (y una foto, si hay) sobre una obra o sobre nada. La foto se achica y se sube primero. */
    fun publicar(texto: String, obra: String?, foto: android.net.Uri?, visibilidad: String, spoiler: Boolean, listo: () -> Unit) = scope.launch {
        _publicando.value = true
        try {
            val ok = intentar {
                val imagen = foto?.let { u -> jpeg(u.toString(), 1600, 900_000)?.let { cliente().subirImagen(it) } ?: throw IOException("sin imagen") } ?: ""
                val ahora = System.currentTimeMillis()
                val p = PublicacionPropia("p${ahora.toString(36)}", ahora, texto.trim().take(2000), obra.orEmpty(), imagen, visibilidad, spoiler, ahora)
                cambiar { e -> e.copy(publicaciones = e.publicaciones + p) }
                subirAhora()
            }
            if (ok) { decir(R.string.red_publicada); cargarFeed(); listo() }
        } finally { _publicando.value = false }
    }

    fun borrarPropia(clave: String) {
        cambiar { e -> e.copy(publicaciones = e.publicaciones.map { if (it.clave == clave) it.copy(borrada = true, cambio = System.currentTimeMillis()) else it }) }
        sincronizarYa(); cargarFeed()
    }

    /* ----- Lo que se sube ----- */

    /** Una foto (de un archivo o del selector) achicada a [lado] px y en JPEG de menos de [max] bytes. */
    private suspend fun jpeg(uri: String, lado: Int, max: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = app.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { it.readBytes() } ?: return@runCatching null
            val medidas = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
            var muestra = 1
            while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= lado) muestra *= 2
            val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return@runCatching null
            val escala = minOf(1f, lado.toFloat() / maxOf(original.width, original.height))
            val chica = android.graphics.Bitmap.createScaledBitmap(original, (original.width * escala).toInt().coerceAtLeast(1), (original.height * escala).toInt().coerceAtLeast(1), true)
            var calidad = 88
            var salida: ByteArray
            do {
                salida = java.io.ByteArrayOutputStream().also { chica.compress(android.graphics.Bitmap.CompressFormat.JPEG, calidad, it) }.toByteArray()
                calidad -= 12
            } while (salida.size > max && calidad > 30)
            salida.takeIf { it.size <= max }
        }.getOrNull()
    }

    companion object {
        /** La versión de la política que conoce esta versión de la app (el servidor dice la vigente). */
        const val POLITICA_CONOCIDA = "2026-11-03"

        private fun https(s: String) = s.takeIf { it.startsWith("https://") }.orEmpty()

        private fun obraBreve(o: Obra) = buildJsonObject {
            put("titulo", o.titulo); put("tipoObra", o.tipo); put("portada", https(o.portada))
            o.anio?.let { put("anio", it) }
            if (o.creador.isNotBlank()) put("creador", o.creador)
            if (o.fuente.isNotBlank()) put("fuente", o.fuente)
        }

        /** Por qué sale una publicación en Explorar (lo que se muestra en la tarjeta). */
        sealed interface Motivo {
            data class TienesLaObra(val titulo: String) : Motivo
            data class TeGustaElCreador(val creador: String) : Motivo
            data class TusEtiquetas(val etiqueta: String) : Motivo
            data object Nueva : Motivo
        }

        private fun texto(p: PublicacionSocial, k: String) = (p.datos[k] as? JsonPrimitive)?.content.orEmpty()
        private fun etiquetas(s: String) = Regex("#([\\p{L}\\p{N}_]{2,40})").findAll(s).map { it.groupValues[1].lowercase() }.toSet()

        /** Lo que el teléfono sabe de la persona para ordenar (se calcula una vez por lista; nada de esto sale del teléfono). */
        private class Gusto(e: Estado) {
            val obras = e.vivas.filter { it.fuente.isNotBlank() }.associateBy { it.fuente }
            val creadores = (e.vivas.filter { it.meGusta || it.nota >= 7 || it.estado == io.github.fogonapps.cajon.datos.Estados.TERMINADO }.map { it.creador } +
                e.artistasVivos.filter { it.favorito || it.nota >= 7 }.map { it.nombre }).map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
            val dichos = e.gustos.filter { !it.borrada }
            val generos = dichos.filter { it.clave.startsWith("genero:") && it.senal == io.github.fogonapps.cajon.datos.Senales.GUSTA }
                .map { it.clave.removePrefix("genero:").lowercase().filter { c -> c.isLetterOrDigit() } }.toSet()
            val fuera = dichos.filter { it.senal in setOf(io.github.fogonapps.cajon.datos.Senales.EXCLUIR, io.github.fogonapps.cajon.datos.Senales.NO_INTERESA,
                io.github.fogonapps.cajon.datos.Senales.NO_RECOMENDAR, io.github.fogonapps.cajon.datos.Senales.NO_GUSTA) }.map { it.clave.lowercase() }.toSet()
            val susEtiquetas = e.publicaciones.flatMap { etiquetas(it.texto) }.toSet() +
                e.vivas.flatMap { o -> o.registros.flatMap { etiquetas(it.texto) } }.toSet()
        }

        /** La afinidad (0 a 1) y su motivo principal. */
        private fun afinidad(p: PublicacionSocial, g: Gusto): Pair<Double, Motivo?> {
            var bruta = 0.0; var motivo: Motivo? = null
            g.obras[texto(p, "fuente")]?.let { o ->
                bruta += when { o.nota in 1..4 || o.estado == io.github.fogonapps.cajon.datos.Estados.ABANDONADO -> -3.0; o.meGusta || o.nota >= 8 -> 3.0; else -> 1.0 }
                if (bruta > 0) motivo = Motivo.TienesLaObra(o.titulo)
            }
            val creador = texto(p, "creador").lowercase().trim()
            if (creador.isNotBlank() && creador in g.creadores) { bruta += 2; if (motivo == null) motivo = Motivo.TeGustaElCreador(texto(p, "creador")) }
            val suyas = etiquetas(texto(p, "texto"))
            val comunes = suyas.filter { it in g.susEtiquetas || it in g.generos }
            if (comunes.isNotEmpty()) { bruta += minOf(2.0, comunes.size.toDouble()); if (motivo == null) motivo = Motivo.TusEtiquetas(comunes.first()) }
            return (bruta / 6).coerceIn(0.0, 1.0) to motivo
        }

        private fun excluida(p: PublicacionSocial, g: Gusto) =
            "obra:${texto(p, "fuente")}".lowercase() in g.fuera || "artista:${texto(p, "creador")}".lowercase() in g.fuera ||
                "red:${p.autor.usuario}".lowercase() in g.fuera

        /** Lo que no se muestra en ninguna lista: silenciado, escondido, o con una palabra oculta. */
        fun seMuestra(p: PublicacionSocial, e: Estado, ocultas: Set<Long>): Boolean {
            if (p.id in ocultas) return false
            if (e.gustos.any { !it.borrada && it.clave == "red:${p.autor.usuario}" && it.senal == io.github.fogonapps.cajon.datos.Senales.EXCLUIR }) return false
            return !io.github.fogonapps.cajon.ui.lleva(e.ajustes.palabrasOcultas, texto(p, "texto") + " " + texto(p, "titulo"))
        }

        /**
         * Explorar, ordenado en el teléfono: puntaje = afinidad con lo
         * tuyo + 0,6 × calidad (la del servidor, igual para todos) + 0,4 × frescura (la mitad cada 3 días). Cada cinco,
         * una nueva con pocas vistas, para que tenga su oportunidad; nunca dos seguidas de la misma persona ni de la
         * misma obra, y máximo dos por persona cada veinte. Lo que marcaste que no te interesa no sale.
         */
        fun ordenarPorGustos(lista: List<PublicacionSocial>, e: Estado, ahora: Long = System.currentTimeMillis()): List<Pair<PublicacionSocial, Motivo?>> {
            val g = Gusto(e)
            val puntuadas = lista.filter { !excluida(it, g) && !io.github.fogonapps.cajon.ui.lleva(e.ajustes.palabrasOcultas, texto(it, "texto") + " " + texto(it, "titulo")) }.map { p ->
                val (a, motivo) = afinidad(p, g)
                val horas = ((ahora - p.cuando) / 3_600_000.0).coerceAtLeast(0.0)
                val s = a + 0.6 * (p.calidad.coerceIn(0.0, 2.0) / 2) + 0.4 * Math.pow(2.0, -horas / 72)
                Triple(p, s, motivo)
            }
            val esNueva = { p: PublicacionSocial -> p.vistas < 30 && ahora - p.cuando < 72 * 3_600_000L }
            val nuevas = puntuadas.filter { esNueva(it.first) }.sortedByDescending { it.second }.toMutableList()
            val resto = puntuadas.filterNot { esNueva(it.first) }.sortedByDescending { it.second }.toMutableList()
            val salida = mutableListOf<Pair<PublicacionSocial, Motivo?>>()
            val porAutor = HashMap<String, Int>()
            fun puede(p: PublicacionSocial): Boolean {
                val ultima = salida.lastOrNull()?.first
                if (ultima != null && (ultima.autor.usuario == p.autor.usuario || texto(ultima, "fuente").let { it.isNotBlank() && it == texto(p, "fuente") })) return false
                return (porAutor[p.autor.usuario] ?: 0) < 2 + salida.size / 20 * 2
            }
            while (nuevas.isNotEmpty() || resto.isNotEmpty()) {
                val tocaNueva = (salida.size + 1) % 5 == 0 && nuevas.isNotEmpty()
                val fuente = if (tocaNueva || resto.isEmpty()) nuevas else resto
                val i = fuente.indexOfFirst { puede(it.first) }.takeIf { it >= 0 } ?: 0   // si nada cumple, la mejor
                val (p, _, motivo) = fuente.removeAt(i)
                salida += p to (if (tocaNueva && motivo == null) Motivo.Nueva else motivo)
                porAutor[p.autor.usuario] = (porAutor[p.autor.usuario] ?: 0) + 1
            }
            return salida
        }

        /** La canción del perfil (de cualquiera de los dos formatos de [Perfil.cancion]). */
        data class CancionPerfil(val titulo: String, val artista: String, val album: String, val portada: String)

        fun cancionDe(e: Estado): CancionPerfil? {
            val c = e.perfil.cancion
            if (c.startsWith("v2|")) return c.split("|").let { p -> if (p.size >= 5 && p[1].isNotBlank()) CancionPerfil(p[1], p[2], p[3], p[4]) else null }
            val (id, titulo) = c.split("|", limit = 2).takeIf { it.size == 2 } ?: return null
            return e.vivas.firstOrNull { it.id == id }?.let { CancionPerfil(titulo, it.creador, it.titulo, it.portada) }
        }

        /** El perfil que se sube: la identidad y las partes que la persona marcó como públicas o para sus seguidores. */
        /** El valor de una cifra del perfil ([PartesPerfil].C_…), igual para tu perfil y para lo que ve la red. */
        fun valorCifra(c: String, e: Estado, hoy: LocalDate): String {
            val delAnio = io.github.fogonapps.cajon.datos.Logica.cifras(e, hoy.year)
            val siempre = io.github.fogonapps.cajon.datos.Logica.cifras(e, null)
            return when (c) {
                PartesPerfil.C_TERMINADAS -> "${delAnio.porTipo.values.sum()}"
                PartesPerfil.C_HORAS_CINE -> "${siempre.minutosCine / 60}"
                PartesPerfil.C_HORAS_SERIES -> "${siempre.minutosSeries / 60}"
                PartesPerfil.C_PAGINAS -> "${siempre.paginas}"
                PartesPerfil.C_CANCIONES -> "${siempre.canciones}"
                PartesPerfil.C_DIARIO -> "${siempre.registros}"
                PartesPerfil.C_RACHA -> "${io.github.fogonapps.cajon.datos.Logica.racha(e, hoy)}"
                else -> siempre.notaMedia?.let { "%.1f".format(it) } ?: "–"
            }
        }

        fun perfilDe(e: Estado): PerfilSubido {
            val p = e.perfil
            val porId = e.vivas.associateBy { it.id }
            fun parte(nombre: String, datos: kotlinx.serialization.json.JsonElement): Pair<String, ParteSubida>? =
                Visibilidad.de(p.visibilidad[nombre]).takeIf { it != Visibilidad.PRIVADA }?.let { nombre to ParteSubida(it, datos) }
            // Los artistas que más le gustan (corazón, nota y escuchas) y los géneros que dijo.
            val artistas = e.musicosVivos.sortedWith(compareByDescending<io.github.fogonapps.cajon.datos.Artista> { it.favorito }.thenByDescending { it.nota }).take(12)
            val generos = e.gustos.filter { !it.borrada && it.clave.startsWith("genero:") && it.senal == io.github.fogonapps.cajon.datos.Senales.GUSTA }.map { it.clave.removePrefix("genero:") }.take(20)
            val terminadas = e.vivas.filter { it.estado == io.github.fogonapps.cajon.datos.Estados.TERMINADO }.groupingBy { it.tipo }.eachCount()
            val partes = listOfNotNull(
                parte(PartesPerfil.MUSICA, JsonArray(artistas.map { a -> buildJsonObject { put("nombre", a.nombre); put("foto", https(a.foto)) } })).takeIf { artistas.isNotEmpty() },
                parte(PartesPerfil.GUSTOS, JsonArray(generos.map { JsonPrimitive(it) })).takeIf { generos.isNotEmpty() },
                parte(PartesPerfil.CIFRAS, JsonObject(terminadas.mapValues { JsonPrimitive(it.value) } + mapOf(
                    "anio" to JsonPrimitive(java.time.LocalDate.now().year),
                    "valores" to JsonObject((p.cifras ?: PartesPerfil.CIFRAS_DE_SIEMPRE).associateWith { JsonPrimitive(valorCifra(it, e, java.time.LocalDate.now())) }),
                ))),
                parte(PartesPerfil.GUARDADAS, JsonPrimitive("")),
                parte(PartesPerfil.FAVORITAS, JsonArray(e.ajustes.favoritas.mapNotNull { porId[it]?.let(::obraBreve) })),
                parte(PartesPerfil.DESTACADOS, JsonObject(p.destacados.mapNotNull { (hueco, id) ->
                    porId[id]?.let { o -> hueco to JsonObject(obraBreve(o) + (p.porQue[hueco]?.let { mapOf("porQue" to JsonPrimitive(it)) } ?: emptyMap())) }
                }.toMap())),
            ).toMap() + listOfNotNull(cancionDe(e)?.let { c ->
                "cancion" to ParteSubida(Visibilidad.PUBLICA, buildJsonObject {
                    put("cancion", c.titulo); put("creador", c.artista); put("titulo", c.album); put("portada", https(c.portada))
                })
            })
            return PerfilSubido(e.ajustes.nombre.take(40), p.bio.take(200), https(p.portada), p.enlaces.filter { it.startsWith("http") }.take(5), partes)
        }

        /** Lo que se publica: el diario y las reseñas compartidos, las listas compartidas y lo publicado a mano. */
        fun publicacionesDe(e: Estado): List<PublicacionSubida> {
            val diario = Visibilidad.de(e.perfil.visibilidad[PartesPerfil.DIARIO])
            val porId = e.vivas.associateBy { it.id }
            val delDiario = e.vivas.flatMap { o ->
                o.registros.mapNotNull { r ->
                    val vis = if (r.visibilidad.isBlank()) diario else Visibilidad.de(r.visibilidad)
                    if (vis == Visibilidad.PRIVADA) return@mapNotNull null
                    val cuando = runCatching { LocalDate.parse(r.fecha).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrDefault(o.cambio)
                    PublicacionSubida("r:${o.id}:${r.id}", vis, cuando, if (r.texto.isNotBlank()) "resena" else "diario",
                        JsonObject(obraBreve(o) + buildJsonObject {
                            if (r.nota > 0) put("estrellas", r.nota / 2.0)
                            if (r.texto.isNotBlank()) put("texto", r.texto.take(4000))
                            if (r.otraVez) put("otraVez", true)
                            put("fecha", r.fecha)
                        }))
                }
            }
            // La reseña de cada obra (con su nota), si la persona no la dejó privada.
            val resenas = e.vivas.filter { it.resena.isNotBlank() && Visibilidad.de(it.resenaVisibilidad) != Visibilidad.PRIVADA }.map { o ->
                PublicacionSubida("o:${o.id}", Visibilidad.de(o.resenaVisibilidad), o.cambio, "resena", JsonObject(obraBreve(o) + buildJsonObject {
                    if (o.nota > 0) put("estrellas", o.nota / 2.0)
                    put("texto", o.resena.take(4000))
                }))
            }
            val listas = e.listas.filter { !it.borrada }.mapNotNull { l ->
                val vis = Visibilidad.de(l.visibilidad).takeIf { it != Visibilidad.PRIVADA } ?: return@mapNotNull null
                val obras = l.obras.mapNotNull { porId[it] }
                PublicacionSubida("l:${l.id}", vis, l.cambio, "lista", buildJsonObject {
                    put("titulo", l.nombre); if (l.descripcion.isNotBlank()) put("texto", l.descripcion.take(2000))
                    put("cuantas", obras.size)
                    put("portadas", JsonArray(obras.map { https(it.portada) }.filter { it.isNotEmpty() }.take(4).map { JsonPrimitive(it) }))
                })
            }
            val propias = e.publicaciones.filter { !it.borrada && it.visibilidad != Visibilidad.PRIVADA }.map { p ->
                PublicacionSubida("f:${p.clave}", Visibilidad.de(p.visibilidad), p.cuando, "foto", JsonObject(
                    (porId[p.obra]?.let(::obraBreve) ?: buildJsonObject { }) + buildJsonObject {
                        if (p.texto.isNotBlank()) put("texto", p.texto)
                        if (p.imagen.isNotBlank()) put("imagen", p.imagen)
                        if (p.spoiler) put("spoiler", true)
                        put("propia", p.clave)
                    }))
            }
            // Con su clave adentro, para poder quitarla de la red desde la publicación.
            return (delDiario + resenas + listas + propias).sortedByDescending { it.cuando }.take(2000)
                .map { it.copy(datos = JsonObject(it.datos + ("clave" to JsonPrimitive(it.clave)))) }
        }
    }
}
