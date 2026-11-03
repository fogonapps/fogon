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
enum class PasoAcceso { CORREO, CODIGO, USUARIO }

data class Acceso(val paso: PasoAcceso = PasoAcceso.CORREO, val correo: String = "", val codigo: String = "", val ocupado: Boolean = false)

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
) {
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
    private val _correoCuenta = MutableStateFlow("")
    val correoCuenta: StateFlow<String> = _correoCuenta
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
        if (_usuario.value != null) intentar {
            val c = cliente().miCuenta()
            _privada.value = c.privada; _correoCuenta.value = c.correo
        }
    }

    /** Cuenta nueva sin correo (cuando el servidor no manda códigos). */
    fun crearSinCorreo(usuario: String, nombre: String) = lanzar {
        _acceso.value = _acceso.value.copy(ocupado = true)
        try { entro(ClienteSocial().crearSinCorreo(usuario.trim().lowercase(), nombre.trim(), politica)) }
        finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    fun pedirCodigo(correo: String) = lanzar {
        _acceso.value = _acceso.value.copy(ocupado = true)
        try {
            ClienteSocial().pedirCodigo(correo.trim())
            _acceso.value = Acceso(PasoAcceso.CODIGO, correo.trim())
        } finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** Con el código: entra; si el correo no tenía cuenta, pasa a elegir el usuario. */
    fun entrar(codigo: String) = scope.launch {
        val a = _acceso.value
        _acceso.value = a.copy(ocupado = true)
        try {
            entro(ClienteSocial().entrar(a.correo, codigo.trim()))
        } catch (x: ErrorSocial) {
            if (x.error == "cuenta_nueva") _acceso.value = Acceso(PasoAcceso.USUARIO, a.correo, codigo.trim())
            else intentar { throw x }
        } catch (x: IOException) { decir(R.string.red_sin_conexion) }
        finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    /** Cuenta nueva con el correo ya comprobado. */
    fun crearConCorreo(usuario: String, nombre: String) = lanzar {
        val a = _acceso.value
        _acceso.value = a.copy(ocupado = true)
        try { entro(ClienteSocial().entrar(a.correo, a.codigo, usuario.trim().lowercase(), nombre.trim(), politica)) }
        finally { _acceso.value = _acceso.value.copy(ocupado = false) }
    }

    fun volverAlCorreo() { _acceso.value = Acceso() }

    private fun entro(a: io.github.fogonapps.cajon.datos.Acceso) {
        caja.tokenSocial = a.token
        caja.usuarioSocial = a.usuario
        caja.politicaSocial = politica
        _usuario.value = a.usuario
        _acceso.value = Acceso()
        // El usuario de la red es el del perfil.
        cambiar { e -> e.copy(perfil = e.perfil.copy(usuario = a.usuario, cambio = System.currentTimeMillis())) }
        decir(if (a.nueva) R.string.red_bienvenida else R.string.red_entraste)
        sincronizarYa()
        cargarFeed()
        revisarServidor()
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
        caja.tokenSocial = null; caja.usuarioSocial = null
        _usuario.value = null
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
        val ahora = perfilDe(e) to publicacionesDe(e)
        intentar {
            if (ahora != ultimo) {
                cliente().subirPerfil(ahora.first)
                cliente().subirPublicaciones(ahora.second)
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

    /* ----- Avisos ----- */

    fun cargarAvisos() = lanzar {
        _avisos.value = cliente().avisos()
        _solicitudes.value = cliente().solicitudes()
        cliente().leidos()
        _sinLeer.value = 0
    }

    fun revisarSinLeer() {
        if (_usuario.value == null) return
        scope.launch { runCatching { _sinLeer.value = cliente().sinLeer() } }
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

        /** El perfil que se sube: la identidad y las partes que la persona marcó como públicas o para sus seguidores. */
        fun perfilDe(e: Estado): PerfilSubido {
            val p = e.perfil
            val porId = e.vivas.associateBy { it.id }
            fun parte(nombre: String, datos: kotlinx.serialization.json.JsonElement): Pair<String, ParteSubida>? =
                Visibilidad.de(p.visibilidad[nombre]).takeIf { it != Visibilidad.PRIVADA }?.let { nombre to ParteSubida(it, datos) }
            val partes = listOfNotNull(
                parte(PartesPerfil.FAVORITAS, JsonArray(e.ajustes.favoritas.mapNotNull { porId[it]?.let(::obraBreve) })),
                parte(PartesPerfil.DESTACADOS, JsonObject(p.destacados.mapNotNull { (hueco, id) ->
                    porId[id]?.let { o -> hueco to JsonObject(obraBreve(o) + (p.porQue[hueco]?.let { mapOf("porQue" to JsonPrimitive(it)) } ?: emptyMap())) }
                }.toMap())),
            ).toMap()
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
            return (delDiario + listas + propias).sortedByDescending { it.cuando }.take(2000)
        }
    }
}
