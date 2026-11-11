package io.github.fogonapps.social

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * El cliente de Fogón Social, el servidor de Fogón (perfiles, seguir, feed, me gusta, comentarios, avisos). Todo es
 * opcional: sin cuenta, la app funciona igual y nada sale del teléfono. [token] es el de la cuenta (en la caja fuerte).
 */
open class ClienteSocial(
    private val token: String? = null,
    private val permiteRed: () -> Boolean = { true },
    val configuracion: ConfiguracionSocial,
    private val http: OkHttpClient = httpPredeterminado,
    private val dispositivo: () -> String = { nombreDispositivo(android.os.Build.MANUFACTURER, android.os.Build.MODEL) },
) {

    private val appProtocolo get() = configuracion.idApp.takeUnless { it == "cajon" }

    private fun rutaApi(ruta: String): String {
        val camino = ruta.substringBefore('?')
        val porApp = camino in setOf("/actividad", "/explorar", "/guardados", "/perfil", "/perfil/revision", "/publicaciones/cambios") ||
            camino.startsWith("/perfiles/") || camino.startsWith("/etiquetas/") || camino == "/avisos" || camino.startsWith("/avisos/") ||
            camino == "/frases/comunidad" || camino == "/frases/propuestas" ||
            camino == "/lugares" || camino.startsWith("/lugares/") || camino == "/respaldo" || camino == "/respaldo/llave"
        return if (appProtocolo != null && porApp) "/v2/apps/${configuracion.idApp}" + ruta else "/v1$ruta"
    }

    protected suspend fun pedir(ruta: String, metodo: String = "GET", cuerpo: okhttp3.RequestBody? = null, cliente: OkHttpClient = http): String? = withContext(Dispatchers.IO) {
        if (token != null && !permiteRed() && !rutaPermitidaSinPolitica(ruta)) throw java.io.IOException("politica_pendiente")
        val req = Request.Builder().url(SERVIDOR + rutaApi(ruta)).method(metodo, cuerpo ?: if (metodo in setOf("POST", "PUT")) ByteArray(0).toRequestBody() else null)
            .header("User-Agent", configuracion.userAgent)
            .apply { if (configuracion.idApp != "cajon") header("X-Fogon-App", configuracion.idApp) }
            .header("X-Fogon-Dispositivo", dispositivo())
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        cliente.newCall(req).execute().use { r ->
            val texto = r.body.string()
            if (!r.isSuccessful) {
                val error = runCatching { JsonSocial.parseToJsonElement(texto).jsonObject["error"]!!.jsonPrimitive.content }.getOrDefault("")
                val desde = runCatching { JsonSocial.parseToJsonElement(texto).jsonObject["desde"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L }.getOrDefault(0L)
                if (r.code == 403 && error == "por_borrar" && token != null) cuentasPorBorrar.emit(token to desde)
                throw ErrorSocial(r.code, error, desde)
            }
            texto.ifEmpty { null }
        }
    }

    protected fun <T> json(s: KSerializer<T>, v: T) = JsonSocial.encodeToString(s, v).toRequestBody(JSON)
    protected fun <T> leer(s: KSerializer<T>, texto: String?) = JsonSocial.decodeFromString(s, texto ?: "null")
    protected fun cod(s: String) = URLEncoder.encode(s, "UTF-8")

    /* ----- Frases del saludo: las de la gente son de cada app; los votos y las ocultas, por clave ----- */

    /** Los votos de la comunidad a las frases del saludo (clave → sí y no). */
    suspend fun frases(): Map<String, ConteoFrase> = leer(kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(), ConteoFrase.serializer()), pedir("/frases"))
    suspend fun frasesComunidad(idioma: String) = leer(kotlinx.serialization.builtins.ListSerializer(FraseComunidad.serializer()), pedir("/frases/comunidad?idioma=${cod(idioma)}"))
    suspend fun proponerFrase(texto: String, idioma: String) { pedir("/frases/propuestas", "POST", json(FraseNueva.serializer(), FraseNueva(texto, idioma, app = configuracion.idApp))) }
    suspend fun frasesOcultas() = leer(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), pedir("/frases/ocultas"))
    suspend fun votarFrase(clave: String, valor: Int) { pedir("/frases/${cod(clave)}", "PUT", json(VotoFrase.serializer(), VotoFrase(valor))) }
    /** El equipo: agregar una frase a una app ([app]; por omisión, la de este cliente), quitarla y decidir las propuestas. */
    suspend fun agregarFrase(texto: String, idioma: String, franja: String, app: String = configuracion.idApp) =
        leer(FraseComunidad.serializer(), pedir("/admin/frases", "PUT", json(FraseNueva.serializer(), FraseNueva(texto, idioma, franja, app))))
    suspend fun quitarFrase(clave: String) { pedir("/admin/frases/${cod(clave)}", "DELETE") }
    suspend fun frasesPendientes() = leer(kotlinx.serialization.builtins.ListSerializer(FraseComunidad.serializer()), pedir("/admin/frases"))
    suspend fun decidirFrase(clave: String, aprobar: Boolean) { pedir("/admin/frases", "POST", json(DecisionFrase.serializer(), DecisionFrase(clave, aprobar))) }

    /* Cuenta */
    suspend fun info(): InfoServidor = leer(InfoServidor.serializer(), pedir("/estado"))
    suspend fun usuarioLibre(usuario: String): Boolean =
        JsonSocial.parseToJsonElement(pedir("/usuarios/${cod(usuario)}/libre")!!).jsonObject["libre"]!!.jsonPrimitive.boolean
    suspend fun pedirCodigo(correo: String) { pedir("/acceso/codigo", "POST", json(Correo.serializer(), Correo(correo, app = appProtocolo))) }
    /** Entra con el código. Para una cuenta nueva el servidor responde "cuenta_nueva" hasta que lleguen [usuario] y la aceptación. */
    suspend fun entrar(correo: String, codigo: String, usuario: String = "", nombre: String = "", politica: String = "", clave: String = ""): Acceso =
        leer(Acceso.serializer(), pedir("/acceso", "POST", json(Entrada.serializer(),
            Entrada(correo, codigo, usuario, nombre, politica, mayorDeEdad = politica.isNotEmpty(), clave = clave, app = appProtocolo))))
    suspend fun vincularCorreo(correo: String, codigo: String) { pedir("/cuenta/correo", "POST", json(Entrada.serializer(), Entrada(correo, codigo))) }
    suspend fun salir() { pedir("/salir", "POST") }
    suspend fun estadoCuenta() = leer(EstadoCuentaSocial.serializer(), pedir("/cuenta/estado"))
    suspend fun activarMfa(factor: FactorMfa = FactorMfa()) = leer(InscripcionMfa.serializer(), pedir("/mfa/activar", "POST", json(FactorMfa.serializer(), factor)))
    suspend fun confirmarMfa(codigo: String) = leer(CodigosMfa.serializer(), pedir("/mfa/confirmar", "POST", json(FactorMfa.serializer(), FactorMfa(codigo))))
    suspend fun elevarMfa(factor: FactorMfa) = leer(ElevacionMfa.serializer(), pedir("/mfa/elevar", "POST", json(FactorMfa.serializer(), factor)))
    suspend fun desactivarMfa(codigo: String) { pedir("/mfa/desactivar", "POST", json(FactorMfa.serializer(), FactorMfa(codigo))) }
    suspend fun renovarMfa(codigo: String) = leer(CodigosMfa.serializer(), pedir("/mfa/recuperacion", "POST", json(FactorMfa.serializer(), FactorMfa(codigo))))
    suspend fun equipo() = leer(ListSerializer(MiembroEquipo.serializer()), pedir("/admin/equipo"))
    suspend fun cambiarEquipo(cambio: CambioEquipo, quitar: Boolean) { pedir("/admin/equipo", if (quitar) "DELETE" else "PUT", json(CambioEquipo.serializer(), cambio)) }
    suspend fun registro(actor: String, accion: String, despues: Long): RegistroEquipo = convertirRegistro(pedir("/admin/registro?actor=${cod(actor)}&accion=${cod(accion)}&despues=$despues") ?: "[]")
    suspend fun restaurar(caso: CasoModeracion) { pedir("/admin/restaurar", "POST", json(ContenidoEquipo.serializer(), ContenidoEquipo(caso.que, caso.objeto))) }
    suspend fun retirarPublicacion(objeto: String, motivo: String = "") { pedir("/admin/publicaciones/retirar", "POST", json(ContenidoEquipo.serializer(), ContenidoEquipo("publicacion", objeto, motivo))) }
    suspend fun retirarComentario(objeto: String, motivo: String = "") { pedir("/admin/comentarios/retirar", "POST", json(ContenidoEquipo.serializer(), ContenidoEquipo("comentario", objeto, motivo))) }
    /** X-08: da o quita la insignia ("verificada", "fogon", "no" o "auto"). */
    suspend fun cambiarInsignia(usuario: String, insignia: String, motivo: String) { pedir("/admin/insignias", "POST", json(CambioInsignia.serializer(), CambioInsignia(usuario, insignia, motivo))) }
    suspend fun levantarSanciones(usuario: String, motivo: String) { pedir("/admin/sanciones/levantar", "POST", json(SancionEquipo.serializer(), SancionEquipo(usuario, motivo))) }
    suspend fun casos() = leer(ListSerializer(CasoModeracion.serializer()), pedir("/admin/casos"))
    suspend fun decidir(que: String, objeto: String, decision: String, regla: String) { pedir("/admin/decidir", "POST", json(Decision.serializer(), Decision(que, objeto, decision, regla))) }
    suspend fun entrarConClave(correo: String, clave: String): Acceso = leer(Acceso.serializer(), pedir("/acceso/clave", "POST", json(EntradaClave.serializer(), EntradaClave(correo, clave, app = appProtocolo))))
    suspend fun ponerClave(nueva: String, actual: String = "") { pedir("/cuenta/clave", "PUT", json(CambioClave.serializer(), CambioClave(nueva, actual))) }
    suspend fun cambiarUsuario(u: String): String =
        JsonSocial.parseToJsonElement(pedir("/cuenta/usuario", "PUT", json(CambioUsuario.serializer(), CambioUsuario(u)))!!).jsonObject["usuario"]!!.jsonPrimitive.content
    suspend fun cambiarApodo(nombre: String) { pedir("/cuenta/apodo", "PUT", json(CambioApodo.serializer(), CambioApodo(nombre))) }
    suspend fun sesiones() = leer(ListSerializer(SesionSocial.serializer()), pedir("/sesiones"))
    suspend fun cerrarSesion(id: String) { pedir("/sesiones/${cod(id)}", "DELETE") }
    suspend fun cerrarOtrasSesiones() { pedir("/sesiones/otras", "DELETE") }
    suspend fun aceptarPolitica(politica: String) { pedir("/cuenta/politica", "POST", json(PoliticaAceptada.serializer(), PoliticaAceptada(politica))) }
    suspend fun miCuenta(): MiCuenta = leer(MiCuenta.serializer(), pedir("/cuenta"))
    suspend fun borrarCuenta(clave: String, codigo: String, motivo: String, texto: String) {
        pedir("/cuenta/borrar", "POST", json(Borrado.serializer(), Borrado(clave, codigo, motivo, texto)))
    }
    suspend fun conservarCuenta() { pedir("/cuenta/conservar", "POST") }
    suspend fun cambiarCorreo(nuevo: String, clave: String, codigo: String) {
        pedir("/cuenta/correo/cambiar", "POST", json(CambioCorreo.serializer(), CambioCorreo(nuevo, clave, codigo)))
    }
    suspend fun confirmarCorreo(nuevo: String, codigo: String) {
        pedir("/cuenta/correo/confirmar", "POST", json(CambioCorreo.serializer(), CambioCorreo(nuevo, codigo = codigo)))
    }
    suspend fun privada(si: Boolean) { pedir("/cuenta", "PUT", json(Privada.serializer(), Privada(si))) }

    /* Perfil */
    suspend fun subirPerfil(p: PerfilSubido) {
        check(configuracion.idApp == "cajon") { "Usar subirPerfilApp con identidad explícita" }
        pedir("/perfil", "PUT", json(PerfilSubido.serializer(), p)) }
    suspend fun revisionPerfil(): Long {
        check(configuracion.idApp != "cajon")
        return leer(RevisionPerfil.serializer(), pedir("/perfil/revision")).revision
    }
    /** La identidad solo se envía tras una edición explícita; nunca se deduce del cuaderno local. */
    suspend fun subirPerfilApp(cambio: CambioPerfilApp): Long {
        check(configuracion.idApp != "cajon")
        require(cambio.identidad == null || cambio.revision != null)
        return leer(RevisionPerfil.serializer(), pedir("/perfil", "PUT", json(CambioPerfilApp.serializer(), cambio))).revision
    }
    suspend fun subirFoto(jpeg: ByteArray) { pedir("/foto", "PUT", jpeg.toRequestBody(JPEG)) }
    suspend fun descargarFoto(usuario: String): ByteArray = withContext(Dispatchers.IO) {
        if (token != null && !permiteRed()) throw java.io.IOException("politica_pendiente")
        val solicitud = Request.Builder().url(foto(usuario))
            .header("X-Fogon-Dispositivo", dispositivo())
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        http.newCall(solicitud).execute().use { r ->
            if (r.code == 401) throw ErrorSocial(r.code, "")
            if (!r.isSuccessful) throw java.io.IOException("foto: ${r.code}")
            r.body.bytes()
        }
    }
    suspend fun descargarImagenPerfil(url: String): ByteArray = withContext(Dispatchers.IO) {
        if (!permiteRed()) throw java.io.IOException("politica_pendiente")
        require(url.startsWith("https://"))
        val solicitud = Request.Builder().url(url).apply {
            // Las portadas globales pueden ser externas: jamás reciben la credencial.
            if (url.startsWith("$SERVIDOR/")) token?.let { header("Authorization", "Bearer $it") }
        }.build()
        http.newCall(solicitud).execute().use { respuesta ->
            if (!respuesta.isSuccessful) throw ErrorSocial(respuesta.code, "imagen")
            val entrada = respuesta.body.byteStream()
            val salida = java.io.ByteArrayOutputStream()
            val bloque = ByteArray(8192)
            while (true) {
                val n = entrada.read(bloque)
                if (n < 0) break
                if (salida.size() + n > 5_000_000) throw java.io.IOException("imagen_grande")
                salida.write(bloque, 0, n)
            }
            salida.toByteArray()
        }
    }
    suspend fun quitarFoto() { pedir("/foto", "DELETE") }
    suspend fun perfil(usuario: String): PerfilSocial = leer(PerfilSocial.serializer(), pedir("/perfiles/${cod(usuario)}"))
    suspend fun buscar(q: String): List<PersonaSocial> = leer(ListSerializer(PersonaSocial.serializer()), pedir("/buscar?q=${cod(q)}"))

    /* Seguir */
    suspend fun seguir(usuario: String): String =
        JsonSocial.parseToJsonElement(pedir("/seguir/${cod(usuario)}", "POST")!!).jsonObject["relacion"]!!.jsonPrimitive.content
    suspend fun dejarDeSeguir(usuario: String) { pedir("/seguir/${cod(usuario)}", "DELETE") }
    suspend fun quitarSeguidor(usuario: String) { pedir("/seguidores/${cod(usuario)}", "DELETE") }
    suspend fun seguidores(usuario: String) = leer(ListSerializer(PersonaSocial.serializer()), pedir("/seguidores/${cod(usuario)}"))
    suspend fun siguiendo(usuario: String) = leer(ListSerializer(PersonaSocial.serializer()), pedir("/siguiendo/${cod(usuario)}"))
    suspend fun solicitudes() = leer(ListSerializer(PersonaSocial.serializer()), pedir("/solicitudes"))
    suspend fun aceptar(usuario: String) { pedir("/solicitudes/${cod(usuario)}", "POST") }
    suspend fun rechazar(usuario: String) { pedir("/solicitudes/${cod(usuario)}", "DELETE") }
    suspend fun bloquear(usuario: String, si: Boolean) { pedir("/bloqueos/${cod(usuario)}", if (si) "POST" else "DELETE") }
    suspend fun bloqueados() = leer(ListSerializer(PersonaSocial.serializer()), pedir("/bloqueos"))

    /* Publicaciones */
    /** Sube publicaciones y quita solo las de [quitar] (lo que falta no se borra: un teléfono vacío no se lleva nada). */
    suspend fun subirCambios(lista: List<PublicacionSubida>, quitar: List<String>) {
        pedir("/publicaciones/cambios", "PUT", json(CambiosPublicaciones.serializer(), CambiosPublicaciones(lista, quitar)))
    }
    suspend fun subirPublicaciones(lista: List<PublicacionSubida>) {
        check(configuracion.idApp == "cajon") { "Otras apps usan cambios incrementales" }
        pedir("/publicaciones", "PUT", json(ListSerializer(PublicacionSubida.serializer()), lista)) }
    suspend fun feed(antes: Long? = null) = leer(ListSerializer(PublicacionSocial.serializer()), pedir("/actividad" + (antes?.let { "?antes=$it" } ?: "")))
    suspend fun util(id: Long, quitar: Boolean) { pedir("/publicaciones/$id/util", if (quitar) "DELETE" else "POST") }
    /** D-033: la copia cifrada de esta app y la llave común de la cuenta (envuelta con la contraseña). */
    suspend fun respaldo() = leer(CopiaCifrada.serializer(), pedir("/respaldo"))
    suspend fun guardarRespaldo(sobre: String, antes: Long): Long =
        JsonSocial.parseToJsonElement(pedir("/respaldo?antes=$antes", "PUT", json(CopiaCifrada.serializer(), CopiaCifrada(sobre))) ?: "{}")
            .jsonObject["cambio"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
    suspend fun guardarLlaveRespaldo(llave: String) { pedir("/respaldo/llave", "PUT", json(CopiaCifrada.serializer(), CopiaCifrada(llave = llave))) }
    /** Lugares de La Cucha: hasta 30 sugerencias por nombre y ciudad (subcadenas, sin tildes ni mayúsculas). */
    suspend fun lugares(nombre: String, ciudad: String) = leer(ListSerializer(Lugar.serializer()),
        pedir("/lugares?nombre=${cod(nombre)}&ciudad=${cod(ciudad)}"))
    /** Corrige la dirección de un lugar (solo quien lo creó o el equipo). */
    suspend fun corregirLugar(id: Long, direccion: String) = leer(Lugar.serializer(),
        pedir("/lugares/$id", "PATCH", json(CorreccionLugar.serializer(), CorreccionLugar(direccion))))
    /** Crea un lugar o devuelve el que ya existe con ese nombre y ciudad. */
    suspend fun crearLugar(nuevo: LugarNuevo) = leer(Lugar.serializer(), pedir("/lugares", "POST", json(LugarNuevo.serializer(), nuevo)))
    suspend fun explorar(antes: Long? = null) = leer(ListSerializer(PublicacionSocial.serializer()), pedir("/explorar" + (antes?.let { "?antes=$it" } ?: "")))
    suspend fun publicacion(id: Long) = leer(PublicacionSocial.serializer(), pedir("/publicaciones/$id"))
    suspend fun guardadasDe(usuario: String) = leer(ListSerializer(PublicacionSocial.serializer()), pedir("/perfiles/${cod(usuario)}/guardadas"))
    suspend fun conEtiqueta(etiqueta: String) = leer(ListSerializer(PublicacionSocial.serializer()), pedir("/etiquetas/${cod(etiqueta.removePrefix("#"))}"))
    suspend fun guardados() = leer(ListSerializer(PublicacionSocial.serializer()), pedir("/guardados"))
    /** Borra en el servidor una publicación propia por su id (aunque este teléfono no la haya subido). */
    suspend fun borrarPublicacion(id: Long) { pedir("/publicaciones/$id", "DELETE") }
    suspend fun meGusta(id: Long, si: Boolean) { pedir("/publicaciones/$id/megusta", if (si) "POST" else "DELETE") }
    suspend fun guardar(id: Long, si: Boolean) { pedir("/publicaciones/$id/guardar", if (si) "POST" else "DELETE") }
    suspend fun comentarios(id: Long) = leer(ListSerializer(ComentarioSocial.serializer()), pedir("/publicaciones/$id/comentarios"))
    suspend fun comentar(id: Long, texto: String, padre: Long? = null) { pedir("/publicaciones/$id/comentarios", "POST", json(Texto.serializer(), Texto(texto, padre))) }
    suspend fun meGustaComentario(id: Long, si: Boolean) { pedir("/comentarios/$id/megusta", if (si) "POST" else "DELETE") }
    suspend fun borrarComentario(id: Long) { pedir("/comentarios/$id", "DELETE") }
    /** Sube una imagen (JPEG ya achicado) y devuelve su nombre, para ponerlo en la publicación ("imagen"). */
    suspend fun subirImagen(jpeg: ByteArray): String =
        JsonSocial.parseToJsonElement(pedir("/imagenes", "PUT", jpeg.toRequestBody(JPEG))!!).jsonObject["nombre"]!!.jsonPrimitive.content

    suspend fun subirVideo(archivo: java.io.File): String =
        withContext(Dispatchers.IO) {
            if (!permiteRed()) throw java.io.IOException("politica_pendiente")
            val peticion = Request.Builder().url(video("").removeSuffix("/")).put(archivo.asRequestBody("video/mp4".toMediaType()))
                .apply { token?.let { header("Authorization", "Bearer $it") } }.build()
            val texto = kotlinx.coroutines.suspendCancellableCoroutine<String> { continua ->
                val llamada = httpVideo.newCall(peticion)
                continua.invokeOnCancellation { llamada.cancel() }
                llamada.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        if (continua.isActive) continua.resumeWith(Result.failure(e))
                    }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        val resultado = runCatching {
                            response.use { r ->
                                val cuerpo = r.body.string()
                                if (!r.isSuccessful) {
                                    val error = runCatching { JsonSocial.parseToJsonElement(cuerpo).jsonObject["error"]!!.jsonPrimitive.content }.getOrDefault("")
                                    throw ErrorSocial(r.code, error)
                                }
                                cuerpo
                            }
                        }
                        if (continua.isActive) continua.resumeWith(resultado)
                    }
                })
            }
            JsonSocial.parseToJsonElement(texto).jsonObject["nombre"]!!.jsonPrimitive.content
        }

    /* Chat: solo pasan sobres cerrados (ver `Chat`) */
    suspend fun ponerLlaves(l: LlavesPublicas) { pedir("/llaves", "PUT", json(LlavesPublicas.serializer(), l)) }
    suspend fun llaves(usuario: String) = leer(LlavesPublicas.serializer(), pedir("/llaves/${cod(usuario)}"))
    suspend fun mandar(usuario: String, sobre: String, firma: String) { pedir("/mensajes/${cod(usuario)}", "POST", json(SobreNuevo.serializer(), SobreNuevo(sobre, firma))) }
    /** Quiénes escribieron (sin abrir ni borrar nada); vacío si el servidor todavía no lo sabe decir. */
    suspend fun remitentesSinLeer(): List<String> = runCatching {
        JsonSocial.parseToJsonElement(pedir("/mensajes/remitentes")!!).jsonObject["de"]!!.jsonArray.map { it.jsonPrimitive.content }
    }.getOrDefault(emptyList())
    suspend fun mensajesSinLeer(): Int = JsonSocial.parseToJsonElement(pedir("/mensajes/sin-leer")!!).jsonObject["n"]!!.jsonPrimitive.int
    suspend fun recibir(desde: Long) = leer(ListSerializer(Sobre.serializer()), pedir("/mensajes?desde=$desde"))
    /** Como [recibir], pero el servidor espera hasta 25 s a que llegue algo: con la app abierta, el mensaje llega al instante. */
    suspend fun esperarMensajes(desde: Long) = leer(ListSerializer(Sobre.serializer()), pedir("/mensajes/esperar?desde=$desde", cliente = httpLargo))

    suspend fun vio(ids: List<Long>) { pedir("/vistas", "POST", json(ListSerializer(Long.serializer()), ids)) }
    suspend fun borrarAvisos(id: Long? = null) { pedir("/avisos" + (id?.let { "/$it" } ?: ""), "DELETE") }

    /* Avisos y reportes */
    suspend fun avisos(antes: Long? = null) = leer(ListSerializer(AvisoSocial.serializer()), pedir("/avisos" + (antes?.let { "?antes=$it" } ?: "")))
    /** X-10: el token de avisos (FCM) de este teléfono para esta app; el servidor le manda un toque vacío. */
    suspend fun registrarEmpuje(token: String) { pedir("/empuje", "PUT", json(EmpujeNuevo.serializer(), EmpujeNuevo(token, configuracion.idApp))) }
    suspend fun quitarEmpuje(token: String) { pedir("/empuje", "DELETE", json(EmpujeNuevo.serializer(), EmpujeNuevo(token, configuracion.idApp))) }
    suspend fun sinLeer(): Int = JsonSocial.parseToJsonElement(pedir("/avisos/sin-leer")!!).jsonObject["n"]!!.jsonPrimitive.int
    suspend fun leidos() { pedir("/avisos/leidos", "POST") }
    /** [que]: "cuenta", "publicacion", "comentario" o "mensaje". */
    suspend fun reportar(que: String, objeto: String, motivo: String) { pedir("/reportes", "POST", json(Reporte.serializer(), Reporte(que, objeto, motivo))) }

    companion object {
        val cuentasPorBorrar = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 16)
        /** El servidor de Fogón (uno solo para todas las apps). */
        const val SERVIDOR = "https://fogonapps.duckdns.org"
        fun enlacePerfil(usuario: String) = "$SERVIDOR/@$usuario"
        fun enlacePublicacion(id: Long) = "$SERVIDOR/p/$id"
        fun foto(usuario: String) = "$SERVIDOR/v1/fotos/$usuario"
        fun video(nombre: String) = "$SERVIDOR/v1/videos/$nombre"
        fun imagen(nombre: String) = "$SERVIDOR/v1/imagenes/$nombre"
        val JSON = "application/json".toMediaType()
        private val JPEG = "image/jpeg".toMediaType()
        private val httpPredeterminado = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        private val httpVideo = httpPredeterminado.newBuilder().writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build()
        /** Para esperar mensajes (el servidor tiene la pregunta abierta hasta 25 s). */
        private val httpLargo = httpPredeterminado.newBuilder().readTimeout(40, TimeUnit.SECONDS).build()
    }
}

