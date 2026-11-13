package io.github.fogonapps.social

import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Quién puede ver algo (los mismos valores que `Visibilidad` en El Cajón; lo privado nunca llega). [SEGUIDORES] se
 * guarda como "amigos" para que las versiones de la app que ya lo mandan sigan sirviendo.
 */
object Vis {
    const val SEGUIDORES = "amigos"
    const val PUBLICA = "publica"
    val SUBIBLES = setOf(SEGUIDORES, PUBLICA)
}

val JsonSocial = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

/** Una parte del perfil con quién la ve (destacados, favoritas, cifras, evolución…): su contenido lo arma la app. */
@Serializable
data class Parte(val visibilidad: String, val datos: JsonElement)

/** El perfil que sube la app: la identidad (que ve todo el que no esté bloqueado) y las partes con su visibilidad. */
@Serializable
data class PerfilSubido(
    val nombre: String = "",
    val bio: String = "",
    val portada: String = "",
    val enlaces: List<String> = emptyList(),
    val partes: Map<String, Parte> = emptyMap(),
)

/** Una publicación: una entrada del diario, una reseña, una lista o una publicación con foto. */
@Serializable
data class PublicacionSubida(
    /** El id que le da la app (para actualizarla o quitarla al volver a subir). */
    val clave: String,
    val visibilidad: String,
    val cuando: Long,
    /** "diario", "resena", "lista", "foto"… */
    val tipo: String,
    val datos: JsonObject,
)

@Serializable
data class Persona(
    val usuario: String, val nombre: String, val foto: Boolean, val privada: Boolean = false,
    /** "fogon" (cuenta oficial), "verificada" o "" (ver [Social.revisarInsignias]). */
    val insignia: String = "",
    /** El fondo de su perfil (de la tienda); "" = el de siempre. */
    val tema: String = "",
    /** El marco de su foto (de la tienda); "" = sin marco. */
    val marco: String = "",
    /** La versión de su foto (cuándo la cambió): va en la dirección de la foto para que nadie se quede con una vieja. */
    val fotoV: Long = 0,
)

@Serializable
data class Publicacion(
    val id: Long, val autor: Persona, val tipo: String, val cuando: Long, val visibilidad: String, val datos: JsonObject,
    val megusta: Int, val comentarios: Int, val yoMeGusta: Boolean, val yoGuardo: Boolean, val vistas: Int = 0,
    /** Qué tan bien le fue comparada con lo normal (1 = normal; de 0 a 3), igual para todos: ver [Social.explorar]. */
    val calidad: Double = 1.0,
    val guardados: Int = 0,
    /** Votos de «útil» (solo en reseñas) y si quien mira la votó. */
    val utiles: Int = 0,
    val yoUtil: Boolean = false,
    val app: String = "cajon",
)

@Serializable
data class Comentario(val id: Long, val autor: Persona, val texto: String, val cuando: Long, val puedoBorrar: Boolean, val padre: Long? = null, val respuestas: Int = 0, val megusta: Int = 0, val yoMeGusta: Boolean = false, val borrado: Boolean = false)

@Serializable
data class PerfilVisto(
    val persona: Persona, val bio: String, val portada: String, val enlaces: List<String>,
    /** Solo las partes que este lector puede ver (nada si la cuenta es privada y no la sigue). */
    val partes: Map<String, JsonElement>,
    val publicaciones: List<Publicacion>,
    /** "yo", "sigo", "pendiente" (pidió seguir una cuenta privada) o "nada". */
    val relacion: String,
    /** La persona sigue a quien mira. */
    val meSigue: Boolean,
    val seguidores: Int,
    val siguiendo: Int,
    val cuantas: Int,
    /** Es privada y quien mira no la sigue: solo ve el nombre, la foto y las cifras. */
    val cerrada: Boolean,
    /** Cuántos me gusta recibieron sus publicaciones. */
    val megusta: Int = 0,
    /** Si quien mira puede ver sus guardadas (lo decide la persona en su perfil, parte "guardadas"). */
    val guardadasVisibles: Boolean = false,
)

@Serializable
data class Aviso(val id: Long, val tipo: String, val de: Persona, val objeto: String, val cuando: Long, val leido: Boolean, val app: String? = null)

@Serializable data class NuevaCuenta(val usuario: String, val nombre: String = "")
@Serializable data class PedirCodigo(val correo: String, val app: String = "cajon")
/** Entrar con el código del correo. Para una cuenta nueva van también el usuario, el nombre y la aceptación. */
@Serializable data class Entrada(
    val correo: String = "", val codigo: String = "", val usuario: String = "", val nombre: String = "",
    /** La versión de la política y los términos que aceptó ([Social.POLITICA]); vacía si no es cuenta nueva. */
    val acepta: String = "",
    /** Dijo que tiene 18 años o más. */
    val mayorDeEdad: Boolean = false,
    /** Con el código, la contraseña nueva: al crear la cuenta, si la olvidó o si la cuenta todavía no tenía. */
    val clave: String = "",
    val app: String? = null,
)
/** Si un correo ya tiene cuenta y si tiene contraseña (para decirle a la persona si se registra o entra). */
@Serializable data class EstadoCorreo(val existe: Boolean, val conClave: Boolean)
@Serializable data class Acceso(val usuario: String, val token: String, val nueva: Boolean, val borrarEl: Long, val idCuenta: String = "")
@Serializable data class MiCuenta(
    val usuario: String, val nombre: String, val correo: String, val privada: Boolean,
    val conClave: Boolean = false,
    /** Desde cuándo puede volver a cambiar el usuario (0 = ya). */
    val usuarioDesde: Long = 0,
    val admin: Boolean = false,
    val apodoDesde: Long,
    val borrarEl: Long = 0,
    val rol: String, val permisos: Set<String>,
    val mfa: Boolean, val elevadaHasta: Long, val idCuenta: String = "",
    /** La versión de la política que aceptó; si no es [Social.POLITICA], la app pide aceptar la nueva. */
    val politica: String = "",
    /** Su insignia ("fogon", "verificada" o ""), para verla en su propio perfil. */
    val insignia: String = "",
    /** Pide además un código del correo al entrar con la contraseña. */
    val dosPasos: Boolean = false,
)
@Serializable data class Aceptacion(val politica: String)
@Serializable data class VotoImagen(val fuente: String, val url: String)
@Serializable data class BloqueoImagen(val url: String, val motivo: String = "")
/** La imagen de la comunidad para una obra (si la hay), los votos por URL y lo bloqueado entre ellas. */
@Serializable data class ImagenObra(val comunidad: String? = null, val votos: Map<String, Int> = emptyMap(), val miVoto: String? = null, val bloqueadas: List<String> = emptyList())
@Serializable data class EntradaClave(val correo: String, val clave: String, val app: String = "cajon",
    /** El código del correo, si la cuenta activó la verificación en dos pasos. */
    val codigo: String = "")
@Serializable data class DosPasos(val activo: Boolean, val clave: String)
/** El cajón cifrado ([sobre], base64) y la versión en que va ([cambio]); [llave]: la llave envuelta con la contraseña. */
@Serializable data class CajonCifrado(val sobre: String = "", val cambio: Long = 0, val llave: String = "")
@Serializable data class Borrado(val clave: String = "", val codigo: String = "", val motivo: String = "", val motivoTexto: String = "")
@Serializable data class CambioCorreo(val nuevo: String, val clave: String = "", val codigo: String = "")
@Serializable data class CambioClave(val nueva: String, val actual: String = "")
@Serializable data class Sesion(val id: String, val dispositivo: String, val creada: Long, val ultimo_uso: Long, val actual: Boolean, val app: String = "cajon")
@Serializable data class CambioApodo(val nombre: String)
@Serializable data class CambioUsuario(val usuario: String)
@Serializable data class CuentaCreada(val usuario: String, val token: String)
@Serializable data class Ajustes(val privada: Boolean? = null)
@Serializable data class NuevoComentario(val texto: String, val padre: Long? = null)
@Serializable data class NuevoReporte(val que: String, val objeto: String, val motivo: String)
@Serializable data class ImagenSubida(val nombre: String)
/** Una nota de 24 horas: unas palabras y, si quiere, una obra (como en la publicación: titulo, portada…). */
@Serializable data class CambiosPublicaciones(val lista: List<PublicacionSubida> = emptyList(), val quitar: List<String> = emptyList())
@Serializable data class VotoFrase(val valor: Int)
@Serializable data class FraseNueva(val texto: String, val idioma: String, val franja: String = "todo", val app: String = "cajon")
/** Una frase de la comunidad: su clave para votar ("c" + id), el texto y quién la propuso (solo para moderar). */
@Serializable data class FraseComunidad(val clave: String, val texto: String, val idioma: String, val de: String = "", val franja: String = "todo", val app: String = "cajon")
@Serializable data class DecisionFrase(val clave: String, val aprobar: Boolean)
@Serializable data class ConteoFrase(val si: Int, val no: Int)
@Serializable data class NotaNueva(val texto: String, val datos: JsonObject = JsonObject(emptyMap()))
/** Una historia: [datos] puede llevar "imagen" (una subida propia), "texto" (hasta 200), "fondo" (0 a 7) y "adjunto" ([Adjuntos]). */
@Serializable data class HistoriaNueva(val datos: JsonObject)
@Serializable data class Historia(val id: Long, val datos: JsonObject, val cuando: Long, val vista: Boolean, val meGusta: Boolean = false, val mejores: Boolean = false,
    /** La encuesta: cuántos votos tiene cada opción y la opción que votó quien mira (-1 si no votó). */
    val votos: List<Int> = emptyList(), val miVoto: Int = -1, val destacada: String = "")
@Serializable data class Voto(val opcion: Int)
@Serializable data class RespuestaHistoria(val persona: Persona, val texto: String, val cuando: Long)
@Serializable data class TextoRespuesta(val texto: String)
@Serializable data class Destacar(val titulo: String)
@Serializable data class GrupoDestacadas(val titulo: String, val historias: List<Historia>)
@Serializable data class GrupoHistorias(val persona: Persona, val historias: List<Historia>, val silenciada: Boolean = false)
@Serializable data class ListaUsuarios(val usuarios: List<String>)
@Serializable data class Cumple(val fecha: String)
/** Lo de la tienda de la cuenta: lo que tiene, lo puesto (fondo y marco) y sus chispas. */
@Serializable data class TemasCuenta(val reclamados: List<String>, val puesto: String, val marco: String = "", val chispas: Int = 0, val diario: Boolean = false)
@Serializable data class TemaElegido(val tema: String)

/**
 * La tienda del perfil: fondos (escenas con su paleta) y marcos de foto, que se compran con chispas. Las chispas se
 * ganan usando las apps (bienvenida y una por día) y solo sirven aquí: no se cambian por dinero ni se pasan a otros.
 */
object TemasPerfil {
    val FONDOS = mapOf("nieve" to 1200, "carmesi" to 1200, "niebla" to 1000, "ojos" to 900, "atardecer" to 1000, "ciudad" to 1500, "selva" to 1000, "cosmos" to 1500)
    val MARCOS = mapOf("doble" to 400, "llamas" to 800, "pixel" to 600, "estrellas" to 700, "espinas" to 800, "luna" to 600)
    fun precio(item: String): Int? = FONDOS[item] ?: MARCOS[item.removePrefix("marco:")]?.takeIf { item.startsWith("marco:") }
    const val BIENVENIDA = 1500
    const val DIARIO = 50
}
@Serializable data class CumpleAmigo(val persona: Persona, val fecha: String)
/** Una lista en común: quién la armó, quiénes están y cuántas obras tiene. */
@Serializable data class ListaComun(val id: Long, val nombre: String, val creador: Persona, val miembros: List<Persona>, val obras: Int, val cambio: Long)
@Serializable data class NuevaListaComun(val nombre: String, val miembros: List<String> = emptyList())
/** Una obra de una lista en común: quién la agregó, sus datos (título, tipo, portada, fuente) y los votos. */
@Serializable data class ObraListaComun(val id: Long, val quien: Persona?, val datos: JsonObject, val votos: Int, val yoVoto: Boolean, val cuando: Long)
@Serializable data class DetalleListaComun(val lista: ListaComun, val obras: List<ObraListaComun>)
@Serializable data class VistaHistoria(val persona: Persona, val cuando: Long, val meGusta: Boolean = false)
@Serializable data class Nota(val persona: Persona, val texto: String, val datos: JsonObject, val cuando: Long)
/** Las llaves públicas de una cuenta (keysets de Tink en JSON, en base64): para cifrar y para comprobar la firma. */
@Serializable data class Llaves(val cifrar: String, val firmar: String)
/** Un mensaje cifrado para alguien: [sobre] es el texto cifrado (base64) y [firma], la firma de quien lo manda. */
@Serializable data class SobreNuevo(val sobre: String, val firma: String, val silencioso: Boolean = false)
@Serializable data class Sobre(val id: Long, val de: Persona, val cuando: Long, val sobre: String, val firma: String)
@Serializable data class Error(val error: String)

class Problema(val codigo: Int, val mensaje: String, val desde: Long = 0) : Exception(mensaje)

/** Todo lo que hace el servidor, sin HTTP (así se prueba directo). [ahora] se puede cambiar en las pruebas. */
class Social(
    private val b: Base,
    private val dirFotos: java.io.File,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val cartero: Cartero = Cartero { _, _, _ -> throw Problema(503, "sin_correo") },
    principal: String? = null,
    mensajero: Mensajero? = null,
) {

    val empujes = Empujes(b, ahora, mensajero)

    val equipo = Equipo(b, dirFotos.parentFile ?: dirFotos, ahora, cartero, principal)

    private val dirVideos get() = java.io.File(dirFotos.parentFile, "videos")
    private val dirImagenes get() = java.io.File(dirFotos, "i")

    /** El secreto de los identificadores opacos y las huellas de las vistas: al azar, una vez, en la carpeta de datos (no sale del servidor). */
    private val secreto: ByteArray by lazy {
        val f = java.io.File(dirFotos.parentFile ?: dirFotos, "secreto")
        if (f.exists()) f.readBytes().also { check(it.size >= 32) { "El secreto del servidor está incompleto; debe recuperarse del respaldo" } }
        else ByteArray(32).also { java.security.SecureRandom().nextBytes(it); f.parentFile?.mkdirs(); f.writeBytes(it); f.setReadable(false, false); f.setReadable(true, true) }
    }

    /** Identidad pública estable; el prefijo separa este uso de sesiones, vistas y otras huellas. */
    private fun idCuenta(id: Long): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(secreto, "HmacSHA256"))
        return java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal("cuenta:$id".toByteArray(Charsets.UTF_8))).take(22)
    }

    /* ----- Cuentas ----- */

    /** Si [correoDado] tiene cuenta y contraseña. */
    fun estadoCorreo(correoDado: String): EstadoCorreo {
        val correo = correoDado.trim().lowercase()
        if (!CORREO.matches(correo) || correo.length > 120) throw Problema(400, "correo")
        return b.usar { leer("SELECT clave IS NOT NULL FROM cuentas WHERE correo = ?", correo) { EstadoCorreo(true, it.getInt(1) == 1) }.firstOrNull() }
            ?: EstadoCorreo(false, false)
    }

    /** Manda un código de 6 números al correo (uno por minuto como mucho). Sirve para entrar o para crear la cuenta. */
    @Synchronized
    fun pedirCodigo(correoDado: String, app: String = "cajon") { enviarCodigo(correoDado, app = app) }

    private fun enviarCodigo(correoDado: String, cuentaCambio: Long? = null, app: String = "cajon") {
        val nombreApp = AppsSocial.nombre(app)
        val correo = correoDado.trim().lowercase()
        if (!CORREO.matches(correo) || correo.length > 120) throw Problema(400, "correo")
        val antes = b.usar { leer("SELECT MAX(cuando) FROM (SELECT cuando FROM envios_codigo WHERE correo = ? UNION ALL SELECT enviado AS cuando FROM codigos WHERE correo = ?)", correo, correo) { it.getLong(1).takeUnless { _ -> it.wasNull() } }.firstOrNull() }
        if (antes != null && ahora() - antes < 60_000) throw Problema(429, "despacio")
        if (b.usar { leer("SELECT COUNT(*) FROM envios_codigo WHERE correo = ? AND cuando > ?", correo, ahora() - HORA) { it.getInt(1) }.first() } >= 5)
            throw Problema(429, "despacio")
        val huellaAnterior = b.usar { leer("SELECT huella FROM codigos WHERE correo = ?", correo) { it.getString(1) }.firstOrNull() }
        var codigo: String
        do { codigo = (0 until 6).map { azar.nextInt(10) }.joinToString("") }
        while (Base.huella("$correo:$codigo") == huellaAnterior)
        b.usar {
            cambiar("INSERT INTO envios_codigo VALUES(?,?)", correo, ahora())
            if (cuentaCambio != null) {
                cambiar("INSERT OR REPLACE INTO cambios_correo(cuenta,nuevo,huella,expira,intentos,app) VALUES(?,?,?,?,0,?)", cuentaCambio, correo, Base.huella("$correo:$codigo"), ahora() + 10 * 60_000, app)
            } else cambiar("""INSERT INTO codigos(correo, huella, expira, intentos, enviado, app) VALUES(?,?,?,0,?,?)
                ON CONFLICT(correo) DO UPDATE SET huella = excluded.huella, expira = excluded.expira, intentos = 0, enviado = excluded.enviado, app = excluded.app""",
                correo, Base.huella("$correo:$codigo"), ahora() + 10 * 60_000, ahora(), app)
        }
        val proposito = if (cuentaCambio == null) "entrar a $nombreApp" else "confirmar tu nuevo correo de $nombreApp"
        cartero.enviar(correo, "Tu código de $nombreApp: $codigo",
            "Tu código para $proposito es:\n\n$codigo\n\nVence en 10 minutos. Si no lo pediste, ignora este correo.\n\n$nombreApp · Fogón Social")
    }

    /**
     * Entra con el código. Si el correo ya tiene cuenta, abre una sesión nueva. Si no, hace falta elegir el usuario,
     * aceptar la política y los términos ([POLITICA]) y tener 18 años o más; mientras no lleguen, responde
     * "cuenta_nueva" sin gastar el código (la app pregunta y vuelve a llamar).
     */
    @Synchronized
    fun entrar(e: Entrada, dispositivo: String = ""): Acceso {
        e.app?.let(AppsSocial::validar)
        if (e.clave.isNotEmpty()) Claves.validar(e.clave)
        val correo = e.correo.trim().lowercase()
        codigoBueno(correo, e.codigo)
        val app = e.app ?: b.usar { leer("SELECT app FROM codigos WHERE correo = ?", correo) { it.getString(1) }.single() }
        val existente = b.usar { leer("SELECT id, usuario FROM cuentas WHERE correo = ?", correo) { it.getLong(1) to it.getString(2) }.firstOrNull() }
        val (id, usuario, nueva) = if (existente != null) Triple(existente.first, existente.second, false) else {
            if (e.usuario.isBlank()) throw Problema(409, "cuenta_nueva")
            if (e.acepta != POLITICA) throw Problema(400, "acepta")
            if (!e.mayorDeEdad) throw Problema(400, "edad")
            val u = e.usuario.trim().lowercase()
            if (!USUARIO.matches(u)) throw Problema(400, "usuario")
            if (!usuarioLibre(u)) throw Problema(409, "usuario_tomado")
            val nuevo = b.usar {
                insertar("INSERT INTO cuentas(id, usuario, token, nombre, creada, cambio, correo, acepto, acepto_cuando) VALUES(?,?,?,?,?,?,?,?,?)",
                    insertar("INSERT INTO secuencia_cuentas DEFAULT VALUES"), u, "retirado:" + Base.huella(Base.tokenNuevo()), e.nombre.trim().take(40), ahora(), ahora(), correo, POLITICA, ahora())
            }
            Triple(nuevo, u, true)
        }
        // Con el código comprobado se puede poner la contraseña (nueva cuenta, la olvidó o no tenía).
        if (e.clave.isNotEmpty()) {
            b.usar { cambiar("UPDATE cuentas SET clave = ? WHERE id = ?", Claves.huella(e.clave), id) }
        }
        b.usar { cambiar("DELETE FROM codigos WHERE correo = ?", correo) }
        val token = sesionNueva(id, dispositivo, cerrarAnteriores = e.clave.isNotEmpty(), app = app)
        if (e.clave.isNotEmpty()) {
            avisarClave(id, app)
        }
        b.usar { cambiar("DELETE FROM intentos WHERE cuenta = ?", id) }
        return Acceso(usuario, token, nueva, borrarEl(id), idCuenta(id))
    }

    /** Revisa el código de [correo] (sin gastarlo); cada intento malo cuenta, y a los 5 se vence. */
    private fun codigoBueno(correo: String, codigo: String) {
        val (huella, expira, intentos) = b.usar {
            leer("SELECT huella, expira, intentos FROM codigos WHERE correo = ?", correo) { Triple(it.getString(1), it.getLong(2), it.getInt(3)) }.firstOrNull()
        } ?: throw Problema(400, "sin_codigo")
        if (ahora() > expira || intentos >= 5) throw Problema(400, "codigo_vencido")
        // Además de 5 intentos por código, [FALLOS_CODIGO_DIA] códigos equivocados por correo cada 24 horas: pidiendo
        // códigos nuevos sin parar se podía adivinar uno con paciencia.
        if (b.usar { leer("SELECT COUNT(*) FROM fallos_codigo WHERE correo = ? AND cuando > ?", correo, ahora() - 24 * HORA) { it.getInt(1) }.first() } >= FALLOS_CODIGO_DIA)
            throw Problema(429, "despacio")
        if (!java.security.MessageDigest.isEqual(Base.huella("$correo:${codigo.trim()}").toByteArray(), huella.toByteArray())) {
            b.usar {
                cambiar("INSERT INTO fallos_codigo VALUES(?,?)", correo, ahora())
                cambiar("UPDATE codigos SET intentos = intentos + 1 WHERE correo = ?", correo)
            }
            throw Problema(400, "codigo")
        }
    }

    /** Le pone a la cuenta el correo de [e] (con su código), para poder entrar desde otro teléfono. */
    @Synchronized
    fun vincularCorreo(yo: Long, e: Entrada) {
        if (miCuenta(yo).correo.isNotBlank()) throw Problema(400, "correo")
        val correo = e.correo.trim().lowercase()
        codigoBueno(correo, e.codigo)
        if (b.usar { leer("SELECT 1 FROM cuentas WHERE correo = ? AND id <> ?", correo, yo) { 1 }.isNotEmpty() }) throw Problema(409, "correo_tomado")
        b.usar {
            cambiar("UPDATE cuentas SET correo = ? WHERE id = ?", correo, yo)
            cambiar("DELETE FROM codigos WHERE correo = ?", correo)
        }
    }

    private fun sesionNueva(cuenta: Long, dispositivoDado: String = "", cerrarAnteriores: Boolean = false, app: String = "cajon"): String {
        val nombreApp = AppsSocial.nombre(app)
        if (b.usar { leer("SELECT cerrada FROM cuentas WHERE id = ?", cuenta) { it.getInt(1) == 1 }.first() }) throw Problema(403, "cerrada")
        val dispositivo = dispositivoDado.filter { !it.isISOControl() }.take(60)
        val nueva = dispositivo.isNotBlank() && b.usar {
            leer("SELECT 1 FROM sesiones WHERE cuenta = ? AND dispositivo = ? AND app = ? AND ultimo_uso > ?", cuenta, dispositivo, app, ahora() - CADUCIDAD) { 1 }.isEmpty()
        }
        val token = Base.tokenNuevo()
        b.usar {
            cambiar("INSERT INTO sesiones(huella, cuenta, creada, dispositivo, ultimo_uso, app) VALUES(?,?,?,?,?,?)", Base.huella(token), cuenta, ahora(), dispositivo, ahora(), app)
            if (cerrarAnteriores) cerrarOtras(cuenta, token)
        }
        if (nueva) avisar(cuenta, if (app == "cajon") "Se entró a tu cuenta desde $dispositivo" else "Se entró a tu cuenta de Fogón Social desde $nombreApp: $dispositivo",
            "Entrada desde $dispositivo el ${fechaLegible(ahora())}. Si no fuiste tú, abre $nombreApp, Tu cuenta de la red → Dónde has entrado y cierra esa sesión.")
        return token
    }

    fun appSesion(token: String?): String = b.usar {
        leer("SELECT app FROM sesiones WHERE huella = ?", Base.huella(token.orEmpty())) { it.getString(1) }.firstOrNull() ?: "cajon"
    }

    private fun idSesion(huella: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(secreto, "HmacSHA256"))
        return mac.doFinal(("sesion:" + huella).toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    }

    fun sesiones(yo: Long, token: String?): List<Sesion> = b.usar {
        leer("SELECT huella, dispositivo, creada, ultimo_uso, app FROM sesiones WHERE cuenta = ? AND ultimo_uso > ? ORDER BY creada", yo, ahora() - CADUCIDAD) {
            Sesion(idSesion(it.getString(1)), it.getString(2), it.getLong(3), it.getLong(4), it.getString(1) == Base.huella(token.orEmpty()), it.getString(5))
        }
    }

    fun cerrarSesion(yo: Long, id: String) {
        b.usar {
            leer("SELECT huella FROM sesiones WHERE cuenta = ?", yo) { it.getString(1) }
                .firstOrNull { idSesion(it) == id }?.let { cambiar("DELETE FROM sesiones WHERE cuenta = ? AND huella = ?", yo, it) }
        }
    }

    @Synchronized
    fun cerrarOtras(yo: Long, token: String?) {
        b.usar {
            cambiar("DELETE FROM sesiones WHERE cuenta = ? AND huella <> ?", yo, Base.huella(token.orEmpty()))
        }
    }

    private fun avisar(yo: Long, asunto: String, texto: String) {
        val correo = b.usar { leer("SELECT correo FROM cuentas WHERE id = ?", yo) { it.getString(1) }.firstOrNull() }
        if (!correo.isNullOrBlank()) enviarAviso(correo, asunto, texto)
    }

    private fun enviarAviso(correo: String, asunto: String, texto: String) {
        // Un fallo SMTP no revierte un cambio de seguridad ya aplicado.
        try { cartero.enviar(correo, asunto, texto) } catch (_: Exception) {
            System.err.println("No se pudo enviar un aviso de seguridad")
        }
    }

    private fun avisarClave(yo: Long, app: String? = null) = avisar(yo, "Tu contraseña de ${app?.let(AppsSocial::nombre) ?: "Fogón Social"} cambió",
        "Tu contraseña de Fogón Social cambió${app?.let { " desde " + AppsSocial.nombre(it) }.orEmpty()}. Si no fuiste tú, entra con un código de tu correo, cambia la contraseña y cierra las demás sesiones desde Tu cuenta de la red.")

    @Synchronized
    fun limpiarSeguridad() {
        limpiarHistorias()
        b.usar { leer("SELECT id FROM cuentas WHERE borrar_el > 0 AND borrar_el <= ? AND id NOT IN (SELECT cuenta FROM roles WHERE rol IN ('principal','administrador'))", ahora()) { it.getLong(1) } }.forEach(::borrarCuenta)
        b.usar {
            val videosVencidos = leer("SELECT DISTINCT v.nombre FROM videos v JOIN publicaciones p ON p.cuenta = v.cuenta AND instr(p.datos, v.nombre) > 0 WHERE p.estado = 'retirado' AND p.retirado_hasta <= ?", ahora()) { it.getString(1) }
            val imagenesVencidas = leer("SELECT DISTINCT i.nombre FROM imagenes i JOIN publicaciones p ON p.cuenta = i.cuenta AND instr(p.datos, i.nombre) > 0 WHERE p.estado = 'retirado' AND p.retirado_hasta <= ?", ahora()) { it.getString(1) }
            cambiar("DELETE FROM comentarios WHERE estado = 'retirado' AND retirado_hasta <= ?", ahora())
            cambiar("DELETE FROM publicaciones WHERE estado = 'retirado' AND retirado_hasta <= ? AND NOT EXISTS(SELECT 1 FROM comentarios c WHERE c.pub = publicaciones.id AND c.estado != 'visible')", ahora())
            imagenesVencidas.forEach { nombre ->
                if (leer("SELECT 1 FROM publicaciones WHERE instr(datos, ?) > 0", nombre) { 1 }.isEmpty()) {
                    java.io.File(dirImagenes, "$nombre.jpg").delete()
                    cambiar("DELETE FROM imagenes WHERE nombre = ?", nombre)
                }
            }
            videosVencidos.forEach { nombre ->
                if (leer("SELECT 1 FROM publicaciones WHERE instr(datos, ?) > 0", nombre) { 1 }.isEmpty()) {
                    java.io.File(dirVideos, "$nombre.mp4").delete()
                    cambiar("DELETE FROM videos WHERE nombre = ?", nombre)
                }
            }
            purgarVideosSinPublicacion()
            cambiar("DELETE FROM sesiones WHERE ultimo_uso <= ?", ahora() - CADUCIDAD)
            cambiar("DELETE FROM intentos WHERE ultimo_fallo <= ? AND bloqueada_hasta <= ?", ahora() - DIA, ahora())
            cambiar("DELETE FROM envios_codigo WHERE cuando <= ?", ahora() - HORA)
            cambiar("DELETE FROM fallos_codigo WHERE cuando <= ?", ahora() - 24 * HORA)
            cambiar("DELETE FROM apodo_cambios WHERE cuando <= ?", ahora() - 14 * DIA)
            // La política promete un año para reportes, decisiones y faltas vencidas; lo preservado para las
            // autoridades y lo que sigue abierto no se toca.
            val anio = ahora() - 365 * DIA
            cambiar("""DELETE FROM reportes WHERE estado != 'abierto' AND cuando <= ?
                AND NOT (que = 'publicacion' AND EXISTS(SELECT 1 FROM publicaciones p WHERE CAST(p.id AS TEXT) = reportes.objeto AND p.estado = 'preservado'))
                AND NOT (que = 'comentario' AND EXISTS(SELECT 1 FROM comentarios c WHERE CAST(c.id AS TEXT) = reportes.objeto AND c.estado = 'preservado'))""", anio)
            cambiar("""DELETE FROM decisiones WHERE cuando <= ?
                AND NOT (que = 'publicacion' AND EXISTS(SELECT 1 FROM publicaciones p WHERE CAST(p.id AS TEXT) = decisiones.objeto AND p.estado = 'preservado'))
                AND NOT (que = 'comentario' AND EXISTS(SELECT 1 FROM comentarios c WHERE CAST(c.id AS TEXT) = decisiones.objeto AND c.estado = 'preservado'))""", anio)
            cambiar("DELETE FROM faltas WHERE vence <= ?", anio)
        }
    }

    private val HORA = 3600_000L
    private val DIA = 24 * HORA
    private val CADUCIDAD = 90 * DIA

    /** Cierra la sesión de este teléfono. */
    fun salir(token: String?) {
        if (token.isNullOrBlank()) return
        b.usar { cambiar("DELETE FROM sesiones WHERE huella = ?", Base.huella(token)) }
    }

    fun miCuenta(yo: Long, token: String? = null): MiCuenta = b.usar {
        leer("SELECT usuario, nombre, coalesce(correo, ''), privada, clave IS NOT NULL, usuario_cambio, admin, acepto, insignia, dos_pasos FROM cuentas WHERE id = ?", yo) {
            MiCuenta(it.getString(1), it.getString(2), it.getString(3), it.getInt(4) == 1, it.getInt(5) == 1, (if (it.getLong(6) == 0L) 0 else it.getLong(6) + 30 * DIA).takeIf { d -> d > ahora() } ?: 0, equipo.rol(yo).isNotBlank(), apodoDesde(yo), borrarEl(yo), equipo.rol(yo), equipo.permisos(yo), equipo.activa(yo), equipo.elevada(yo, token), idCuenta(yo), it.getString(8), it.getString(9), it.getInt(10) == 1)
        }.first()
    }

    /** Guarda la prueba de que aceptó la versión vigente (Ley 1581): solo se acepta la que rige hoy. */
    fun aceptarPolitica(yo: Long, politica: String) {
        if (politica != POLITICA) throw Problema(400, "politica")
        b.usar { cambiar("UPDATE cuentas SET acepto = ?, acepto_cuando = ? WHERE id = ?", POLITICA, ahora(), yo) }
    }

    /* ----- Portadas (C-03) ----- */

    /** Solo imágenes de fuentes autorizadas: nadie sube portadas de terceros al servidor (D-024). */
    internal fun urlImagenValida(url: String): Boolean {
        if (url.length > 300 || !url.startsWith("https://")) return false
        val host = url.removePrefix("https://").substringBefore('/').lowercase()
        return host in DOMINIOS_IMAGEN
    }

    private fun fuenteValida(fuente: String) = fuente.length in 3..80 && Regex("[a-z]+(:[A-Za-z0-9._/-]+)+").matches(fuente)

    fun imagenObra(lector: Long?, fuente: String): ImagenObra {
        if (!fuenteValida(fuente)) throw Problema(400, "fuente")
        return b.usar {
            val bloqueadas = leer("SELECT b.url FROM imagenes_bloqueadas b WHERE b.url IN (SELECT url FROM votos_imagen WHERE obra = ?)", fuente) { it.getString(1) }
            val votos = leer("SELECT url, COUNT(*) FROM votos_imagen WHERE obra = ? AND cuenta IN (SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0) GROUP BY url", fuente) { it.getString(1) to it.getInt(2) }
                .filter { it.first !in bloqueadas }.toMap()
            val total = votos.values.sum()
            val comunidad = votos.entries.maxByOrNull { it.value }?.takeIf { it.value >= VOTOS_COMUNIDAD && it.value * 2 > total }?.key
            val mio = lector?.let { yo -> leer("SELECT url FROM votos_imagen WHERE obra = ? AND cuenta = ?", fuente, yo) { it.getString(1) }.firstOrNull() }
            ImagenObra(comunidad, votos, mio, bloqueadas)
        }
    }

    fun votarImagen(yo: Long, v: VotoImagen) {
        if (!fuenteValida(v.fuente)) throw Problema(400, "fuente")
        if (!urlImagenValida(v.url)) throw Problema(400, "url")
        b.usar {
            if (leer("SELECT 1 FROM imagenes_bloqueadas WHERE url = ?", v.url) { 1 }.isNotEmpty()) throw Problema(400, "bloqueada")
            cambiar("INSERT OR REPLACE INTO votos_imagen(obra, url, cuenta, cuando) VALUES(?,?,?,?)", v.fuente, v.url, yo, ahora())
        }
    }

    fun quitarVotoImagen(yo: Long, fuente: String) {
        b.usar { cambiar("DELETE FROM votos_imagen WHERE obra = ? AND cuenta = ?", fuente, yo) }
    }

    /** El equipo bloquea (o vuelve a permitir) una imagen como predeterminada y cierra sus reportes abiertos. */
    fun bloquearImagen(yo: Long, d: BloqueoImagen, si: Boolean, token: String? = null) = b.transaccion {
        if (!urlImagenValida(d.url)) throw Problema(400, "url")
        if (si) cambiar("INSERT OR REPLACE INTO imagenes_bloqueadas(url, motivo, cuando) VALUES(?,?,?)", d.url, d.motivo.take(100).ifBlank { "equipo" }, ahora())
        else cambiar("DELETE FROM imagenes_bloqueadas WHERE url = ?", d.url)
        cambiar("UPDATE reportes SET estado = 'resuelto' WHERE que = 'imagen' AND objeto = ? AND estado = 'abierto'", d.url)
        equipo.registrar(yo.toString(), if (si) "imagen_bloquear" else "imagen_permitir", d.url, d.motivo.take(100), token)
    }

    /* ----- Contraseña ----- */

    private data class Intento(val fallos: Int, val desde: Long, val bloqueadaHasta: Long, val nivel: Int, val ultimoFallo: Long)

    /** Entrar con correo y contraseña. Se limitan los fallos por cuenta además del límite HTTP por dirección. */
    @Synchronized
    fun entrarConClave(e: EntradaClave, dispositivo: String = ""): Acceso {
        AppsSocial.validar(e.app)
        val correo = e.correo.trim().lowercase()
        val (id, usuario) = cuentaPorClave(correo, e.clave)
        // Verificación en dos pasos (la activa la persona): con la contraseña buena, falta el código del correo.
        if (b.usar { leer("SELECT dos_pasos FROM cuentas WHERE id = ?", id) { it.getInt(1) == 1 }.first() }) {
            if (e.codigo.isBlank()) {
                // Si ya se mandó uno hace menos de un minuto, sirve ese.
                try { enviarCodigo(correo, app = e.app) } catch (x: Problema) { if (x.codigo != 429) throw x }
                throw Problema(409, "codigo_requerido")
            }
            codigoBueno(correo, e.codigo)
            b.usar { cambiar("DELETE FROM codigos WHERE correo = ?", correo) }
        }
        return Acceso(usuario, sesionNueva(id, dispositivo, app = e.app), false, borrarEl(id), idCuenta(id))
    }

    /** La cuenta de [correo] si [clave] es su contraseña; limita los fallos por cuenta (también sirve a la pantalla de sanción). */
    @Synchronized
    private fun cuentaPorClave(correo: String, clave: String): Pair<Long, String> {
        val (id, usuario, guardada) = b.usar {
            leer("SELECT id, usuario, clave FROM cuentas WHERE correo = ?", correo) { Triple(it.getLong(1), it.getString(2), it.getString(3)) }.firstOrNull()
        } ?: throw Problema(400, "clave_mala")
        val intento = b.usar { leer("SELECT fallos, desde, bloqueada_hasta, nivel, ultimo_fallo FROM intentos WHERE cuenta = ?", id) {
            Intento(it.getInt(1), it.getLong(2), it.getLong(3), it.getInt(4), it.getLong(5))
        }.firstOrNull() }?.takeIf { ahora() - it.ultimoFallo < DIA }
        if (intento != null && intento.bloqueadaHasta > ahora()) throw Problema(400, "clave_mala")
        if (guardada == null || !Claves.comprobar(clave, guardada)) {
            val fallos = if (intento != null && ahora() - intento.desde < 15 * 60_000 && intento.bloqueadaHasta == 0L) intento.fallos + 1 else 1
            val nivel = intento?.nivel ?: 0
            val espera = if (fallos >= 5) listOf(1, 5, 15, 60)[nivel.coerceAtMost(3)] * 60_000L else 0L
            val desde = if (fallos > 1) intento!!.desde else ahora()
            b.usar { cambiar("INSERT OR REPLACE INTO intentos(cuenta, fallos, desde, bloqueada_hasta, nivel, ultimo_fallo) VALUES(?,?,?,?,?,?)", id, fallos, desde, if (espera == 0L) 0 else ahora() + espera, if (espera == 0L) nivel else (nivel + 1).coerceAtMost(4), ahora()) }
            throw Problema(400, "clave_mala")
        }
        b.usar { cambiar("DELETE FROM intentos WHERE cuenta = ?", id) }
        return id to usuario
    }

    /** Prende o apaga la verificación en dos pasos (pide la contraseña). Hace falta tener correo y contraseña. */
    fun ponerDosPasos(yo: Long, d: DosPasos) {
        val guardada = b.usar { leer("SELECT clave FROM cuentas WHERE id = ? AND correo IS NOT NULL", yo) { it.getString(1) }.firstOrNull() }
            ?: throw Problema(400, "sin_clave")
        if (!Claves.comprobar(d.clave, guardada)) throw Problema(400, "clave_mala")
        b.usar { cambiar("UPDATE cuentas SET dos_pasos = ? WHERE id = ?", if (d.activo) 1 else 0, yo) }
        avisar(yo, if (d.activo) "Activaste la verificación en dos pasos" else "Apagaste la verificación en dos pasos",
            if (d.activo) "Desde ahora, al entrar con tu contraseña también te pediremos un código que llega a este correo."
            else "Desde ahora basta tu contraseña para entrar. Si no fuiste tú, cambia la contraseña y cierra las demás sesiones.")
    }

    /** Pone o cambia la contraseña (pide la actual si ya había; sin ella, sirve entrar con el código del correo). */
    @Synchronized
    fun ponerClave(yo: Long, c: CambioClave, conCodigo: Boolean = false, token: String? = null) {
        Claves.validar(c.nueva)
        val (correo, guardada) = b.usar { leer("SELECT correo, clave FROM cuentas WHERE id = ?", yo) { it.getString(1) to it.getString(2) }.first() }
        if (correo == null) throw Problema(400, "sin_correo")
        if (guardada != null && !conCodigo && !Claves.comprobar(c.actual, guardada)) throw Problema(400, "clave_mala")
        b.usar {
            cambiar("UPDATE cuentas SET clave = ? WHERE id = ?", Claves.huella(c.nueva), yo)
            cambiar("DELETE FROM intentos WHERE cuenta = ?", yo)
            cerrarOtras(yo, token)
        }
        avisarClave(yo, appSesion(token))
    }

    /* ----- El cajón guardado en la cuenta (cifrado de extremo a extremo) ----- */

    fun cajon(yo: Long): CajonCifrado =
        b.usar { leer("SELECT sobre, cambio, llave FROM cajones WHERE cuenta = ?", yo) { CajonCifrado(it.getString(1), it.getLong(2), it.getString(3)) }.firstOrNull() } ?: CajonCifrado()

    /**
     * Guarda el cajón cifrado. [CajonCifrado.cambio] es la versión que el teléfono tenía: si en el servidor hay una más
     * nueva (otro teléfono subió algo), responde "cambio" y el teléfono primero la trae y la une.
     */
    fun guardarCajon(yo: Long, c: CajonCifrado, antes: Long): Long {
        if (c.sobre.length > 12_000_000) throw Problema(413, "muy_grande")
        val actual = cajon(yo).cambio
        if (actual != antes) throw Problema(409, "cambio")
        val nuevo = maxOf(ahora(), actual + 1)
        b.usar { cambiar("INSERT INTO cajones(cuenta, sobre, cambio) VALUES(?,?,?) ON CONFLICT(cuenta) DO UPDATE SET sobre = excluded.sobre, cambio = excluded.cambio", yo, c.sobre, nuevo) }
        return nuevo
    }

    /** D-033: la copia cifrada de una app que no es El Cajón, con la llave común de la cuenta (envuelta con la contraseña). */
    fun respaldo(yo: Long, app: String): CajonCifrado {
        require(app != "cajon")
        val llave = cajon(yo).llave
        return b.usar { leer("SELECT sobre, cambio FROM respaldos WHERE cuenta = ? AND app = ?", yo, app) { CajonCifrado(it.getString(1), it.getLong(2), llave) }.firstOrNull() }
            ?: CajonCifrado(llave = llave)
    }

    /** Guarda la copia cifrada de una app; como con el cajón, [antes] es la versión que tenía el teléfono (si no coincide, 409). */
    fun guardarRespaldo(yo: Long, app: String, c: CajonCifrado, antes: Long): Long {
        require(app != "cajon")
        if (c.sobre.length > 12_000_000) throw Problema(413, "muy_grande")
        val actual = respaldo(yo, app).cambio
        if (actual != antes) throw Problema(409, "cambio")
        val nuevo = maxOf(ahora(), actual + 1)
        b.usar { cambiar("INSERT INTO respaldos(cuenta, app, sobre, cambio) VALUES(?,?,?,?) ON CONFLICT(cuenta, app) DO UPDATE SET sobre = excluded.sobre, cambio = excluded.cambio", yo, app, c.sobre, nuevo) }
        return nuevo
    }

    /** La llave del cajón, envuelta con la contraseña (al crearla o cambiarla, el teléfono la vuelve a envolver). */
    fun guardarLlave(yo: Long, llave: String) {
        if (llave.length > 4000) throw Problema(413, "muy_grande")
        b.usar { cambiar("INSERT INTO cajones(cuenta, llave) VALUES(?,?) ON CONFLICT(cuenta) DO UPDATE SET llave = excluded.llave", yo, llave) }
    }

    /* ----- Cambiar el usuario ----- */

    /** Cambia el @usuario: una vez cada 30 días; el viejo queda reservado 14 días para la misma persona. */
    @Synchronized
    fun cambiarUsuario(yo: Long, c: CambioUsuario): String {
        if (equipo.rol(yo) == "principal") throw Problema(403, "cuenta_protegida")
        val nuevo = c.usuario.trim().lowercase()
        if (!USUARIO.matches(nuevo) || nuevo in RESERVADOS) throw Problema(400, "usuario")
        val (actual, ultimo) = b.usar { leer("SELECT usuario, usuario_cambio FROM cuentas WHERE id = ?", yo) { it.getString(1) to it.getLong(2) }.first() }
        if (nuevo == actual) return actual
        if (ultimo != 0L && ahora() - ultimo < 30 * DIA) throw Problema(429, "usuario_espera")
        b.usar { cambiar("DELETE FROM usuarios_viejos WHERE hasta < ?", ahora()) }
        val reservado = b.usar { leer("SELECT cuenta FROM usuarios_viejos WHERE usuario = ?", nuevo) { it.getLong(1) }.firstOrNull() }
        if (reservado != null && reservado != yo || b.usar { leer("SELECT 1 FROM cuentas WHERE usuario = ?", nuevo) { 1 }.isNotEmpty() }) throw Problema(409, "usuario_tomado")
        b.usar {
            cambiar("INSERT OR REPLACE INTO usuarios_viejos(usuario, cuenta, hasta) VALUES(?,?,?)", actual, yo, ahora() + 2 * SEMANA)
            cambiar("DELETE FROM usuarios_viejos WHERE usuario = ?", nuevo)
            cambiar("UPDATE cuentas SET usuario = ?, usuario_cambio = ?, cambio = MAX(cambio + 1, ?) WHERE id = ?", nuevo, ahora(), ahora(), yo)
        }
        return nuevo
    }

    /** Crea una cuenta sin correo (solo para las pruebas: la app entra siempre con [entrar]). */
    internal fun crearCuenta(n: NuevaCuenta): CuentaCreada {
        val usuario = n.usuario.trim().lowercase()
        if (!USUARIO.matches(usuario)) throw Problema(400, "usuario")
        if (usuario in RESERVADOS) throw Problema(409, "usuario_tomado")
        val token = Base.tokenNuevo()
        b.usar {
            if (leer("SELECT 1 FROM cuentas WHERE usuario = ?", usuario) { 1 }.isNotEmpty()) throw Problema(409, "usuario_tomado")
            val id = insertar("INSERT INTO cuentas(id, usuario, token, nombre, creada, cambio) VALUES(?,?,?,?,?,?)",
                insertar("INSERT INTO secuencia_cuentas DEFAULT VALUES"), usuario, "retirado:" + Base.huella(Base.tokenNuevo()), n.nombre.trim().take(40), ahora(), ahora())
            cambiar("INSERT INTO sesiones(huella, cuenta, creada, ultimo_uso) VALUES(?,?,?,?)", Base.huella(token), id, ahora(), ahora())
        }
        return CuentaCreada(usuario, token)
    }

    /** Si el usuario está libre (para decirlo mientras la persona lo escribe). */
    fun usuarioLibre(usuario: String): Boolean {
        val u = usuario.trim().lowercase()
        if (!USUARIO.matches(u) || u in RESERVADOS) return false
        return b.usar { leer("SELECT 1 FROM cuentas WHERE usuario = ?", u) { 1 }.isEmpty() &&
            leer("SELECT 1 FROM usuarios_viejos WHERE usuario = ? AND hasta > ?", u, ahora()) { 1 }.isEmpty() }
    }

    /** La cuenta de un token, o null. */
    fun cuentaDe(token: String?): Long? {
        if (token.isNullOrBlank()) return null
        val h = Base.huella(token)
        return b.usar {
            val sesion = leer("SELECT cuenta, ultimo_uso FROM sesiones WHERE huella = ?", h) { it.getLong(1) to it.getLong(2) }.firstOrNull()
            if (sesion != null && ahora() - sesion.second >= CADUCIDAD) {
                cambiar("DELETE FROM sesiones WHERE huella = ?", h)
                return@usar null
            }
            if (sesion != null && ahora() - sesion.second >= HORA)
                cambiar("UPDATE sesiones SET ultimo_uso = ? WHERE huella = ?", ahora(), h)
            sesion?.first
        }
    }

    fun ajustar(yo: Long, a: Ajustes) {
        a.privada?.let { privada ->
            b.usar { cambiar("UPDATE cuentas SET privada = ?, cambio = MAX(cambio + 1, ?) WHERE id = ?", if (privada) 1 else 0, ahora(), yo) }
            // Al volverse pública, las solicitudes que esperaban quedan aceptadas.
            if (!privada) b.usar { cambiar("UPDATE seguir SET aceptada = 1 WHERE a = ?", yo) }
        }
    }

    fun borrarEl(yo: Long): Long = b.usar {
        leer("SELECT borrar_el FROM cuentas WHERE id = ?", yo) { it.getLong(1) }.firstOrNull() ?: 0
    }

    private fun visible(yo: Long): Boolean = b.usar {
        leer("SELECT 1 FROM cuentas WHERE id = ? AND cerrada = 0 AND borrar_el = 0", yo) { 1 }.isNotEmpty()
    }

    fun revisarBorrado(yo: Long) {
        val fecha = borrarEl(yo)
        if (fecha > 0) throw Problema(403, "por_borrar", fecha)
    }

    private fun comprobarCredencial(yo: Long, clave: String, codigo: String, permiteCodigo: Boolean) {
        val (correo, guardada) = b.usar { leer("SELECT correo, clave FROM cuentas WHERE id = ?", yo) { it.getString(1) to it.getString(2) }.first() }
        if (guardada != null && clave.isNotEmpty()) {
            if (!Claves.comprobar(clave, guardada)) throw Problema(400, "clave_mala")
        } else {
            if (guardada != null && !permiteCodigo) throw Problema(400, "clave_mala")
            if (correo.isNullOrBlank()) throw Problema(400, "sin_correo")
            codigoBueno(correo, codigo)
            b.usar { cambiar("DELETE FROM codigos WHERE correo = ?", correo) }
        }
    }

    @Synchronized
    fun programarBorrado(yo: Long, token: String?, e: Borrado? = null) {
        if (equipo.nivel(yo) >= 3) throw Problema(403, "cuenta_protegida")
        if (e != null) {
            if (e.motivo !in setOf("", "no_uso", "privacidad", "problemas", "otro") || e.motivoTexto.length > 300) throw Problema(400, "motivo")
            comprobarCredencial(yo, e.clave, e.codigo, permiteCodigo = false)
        }
        if (borrarEl(yo) > 0) return
        val fecha = ahora() + 7 * DIA
        b.usar {
            cambiar("UPDATE cuentas SET borrar_el = ? WHERE id = ?", fecha, yo)
            if (e != null && (e.motivo.isNotBlank() || e.motivoTexto.isNotBlank()))
                cambiar("INSERT INTO motivos_borrado VALUES(?,?,?)", ahora(), e.motivo, e.motivoTexto.trim())
            cerrarOtras(yo, token)
        }
        avisar(yo, "Tu cuenta se borrará", "Tu cuenta se borrará el ${fechaLegible(fecha)}; si cambias de idea, entra antes de esa fecha.")
    }

    @Synchronized
    fun conservarCuenta(yo: Long) {
        if (borrarEl(yo) == 0L) return
        b.usar { cambiar("UPDATE cuentas SET borrar_el = 0 WHERE id = ?", yo) }
        avisar(yo, "Tu cuenta ya no se borrará", "Tu cuenta ya no se borrará.")
    }

    @Synchronized
    fun cambiarCorreo(yo: Long, e: CambioCorreo, app: String = "cajon") {
        AppsSocial.validar(app)
        if (miCuenta(yo).correo.isBlank()) throw Problema(400, "sin_correo")
        val nuevo = e.nuevo.trim().lowercase()
        // Valida la dirección y aplica los mismos límites antes de consumir la credencial.
        if (!CORREO.matches(nuevo) || nuevo.length > 120 || nuevo == miCuenta(yo).correo) throw Problema(400, "correo")
        comprobarCredencial(yo, e.clave, e.codigo, permiteCodigo = true)
        enviarCodigo(nuevo, cuentaCambio = yo, app = app)
        avisar(yo, "Solicitud de cambio de correo", "Se pidió cambiar el correo de tu cuenta. Si no fuiste tú, entra y cambia tu contraseña.")
    }

    @Synchronized
    fun confirmarCorreo(yo: Long, e: CambioCorreo) {
        val nuevo = e.nuevo.trim().lowercase()
        val pendiente = b.usar { leer("SELECT nuevo, huella, expira, intentos, app FROM cambios_correo WHERE cuenta = ?", yo) {
            listOf(it.getString(1), it.getString(2), it.getLong(3), it.getInt(4), it.getString(5))
        }.firstOrNull() } ?: throw Problema(400, "sin_codigo")
        if (ahora() >= pendiente[2] as Long || pendiente[3] as Int >= 5) throw Problema(400, "codigo_vencido")
        if (nuevo != pendiente[0] || Base.huella("$nuevo:${e.codigo.trim()}") != pendiente[1]) {
            b.usar { cambiar("UPDATE cambios_correo SET intentos = intentos + 1 WHERE cuenta = ?", yo) }
            throw Problema(400, "codigo")
        }
        if (b.usar { leer("SELECT 1 FROM cuentas WHERE correo = ? AND id <> ?", nuevo, yo) { 1 }.isNotEmpty() }) throw Problema(409, "correo_tomado")
        val anterior = miCuenta(yo).correo
        val nombreApp = AppsSocial.nombre(pendiente[4] as String)
        b.usar {
            cambiar("UPDATE cuentas SET correo = ? WHERE id = ?", nuevo, yo)
            cambiar("DELETE FROM cambios_correo WHERE cuenta = ?", yo)
        }
        enviarAviso(anterior, "Tu correo de $nombreApp cambió", "El correo de tu cuenta cambió. Si no fuiste tú, contacta con Fogón.")
        avisar(yo, "Tu correo de $nombreApp cambió", "Esta es la nueva dirección de tu cuenta de Fogón Social.")
    }

    /** Borra la cuenta y todo lo suyo: perfil, publicaciones, a quién sigue, me gusta, comentarios, avisos, imágenes y foto. */
    internal fun borrarCuenta(yo: Long) {
        if (equipo.rol(yo) == "principal") throw Problema(403, "cuenta_protegida")
        // Las cascadas de SQLite tampoco deben destruir evidencia pendiente o preservada.
        if (b.usar { leer("SELECT 1 FROM publicaciones p WHERE (p.cuenta = ? AND p.estado != 'visible') OR EXISTS(SELECT 1 FROM comentarios c WHERE c.pub = p.id AND c.estado != 'visible' AND (c.cuenta = ? OR p.cuenta = ?)) LIMIT 1", yo, yo, yo) { 1 }.isNotEmpty() }) return
        val videos = b.usar { leer("SELECT nombre FROM videos WHERE cuenta = ?", yo) { it.getString(1) } }
        val imagenes = b.usar { leer("SELECT nombre FROM imagenes WHERE cuenta = ?", yo) { it.getString(1) } }
        b.usar {
            leer("SELECT correo FROM cuentas WHERE id = ?", yo) { it.getString(1) }.firstOrNull()?.let { cambiar("DELETE FROM codigos WHERE correo = ?", it) }
            cambiar("UPDATE usuarios_viejos SET cuenta = -1 WHERE cuenta = ?", yo)
            leer("SELECT usuario FROM cuentas WHERE id = ?", yo) { it.getString(1) }.firstOrNull()?.let {
                cambiar("INSERT OR REPLACE INTO usuarios_viejos VALUES(?,?,?)", it, -1, ahora() + 30 * DIA)
            }
            cambiar("DELETE FROM cuentas WHERE id = ?", yo)
        }
        java.io.File(dirFotos, "$yo.jpg").delete()
        imagenes.forEach { java.io.File(dirImagenes, "$it.jpg").delete() }
        videos.forEach { java.io.File(dirVideos, "$it.mp4").delete() }
    }

    private fun idDe(usuario: String): Long =
        b.usar { leer("SELECT id FROM cuentas WHERE usuario = ? AND cerrada = 0 AND borrar_el = 0", usuario.lowercase()) { it.getLong(1) }.firstOrNull() } ?: throw Problema(404, "no_existe")

    private fun persona(id: Long): Persona = b.usar {
        leer("SELECT usuario, nombre, foto, privada, insignia, tema, marco FROM cuentas WHERE id = ?", id) { Persona(it.getString(1), it.getString(2), it.getInt(3) == 1, it.getInt(4) == 1, it.getString(5), it.getString(6), it.getString(7)) }.first()
            .let { p -> if (p.foto) p.copy(fotoV = java.io.File(dirFotos, "$id.jpg").lastModified() / 1000) else p }
    }

    private fun esPrivada(id: Long) = b.usar { leer("SELECT privada FROM cuentas WHERE id = ?", id) { it.getInt(1) == 1 }.firstOrNull() } ?: false

    /* ----- Perfil y foto ----- */

    private fun apodoDesde(yo: Long): Long = b.usar {
        val fechas = leer("SELECT cuando FROM apodo_cambios WHERE cuenta = ? AND cuando > ? ORDER BY cuando", yo, ahora() - 14 * DIA) { it.getLong(1) }
        if (fechas.size < 2) 0 else fechas[fechas.size - 2] + 14 * DIA
    }

    @Synchronized
    fun cambiarApodo(yo: Long, c: CambioApodo) {
        val espera = apodoDesde(yo)
        val anterior = miCuenta(yo).nombre
        val nombre = c.nombre.trim().take(40)
        if (nombre == anterior) return
        if (espera > ahora()) throw Problema(429, "apodo_espera", espera)
        b.transaccion {
            val perfil = leer("SELECT perfil FROM cuentas WHERE id = ?", yo) { JsonSocial.decodeFromString<PerfilSubido>(it.getString(1)) }.single()
            cambiar("INSERT INTO apodo_cambios VALUES(?,?)", yo, ahora())
            cambiar("UPDATE cuentas SET nombre = ?, perfil = ?, cambio = MAX(cambio + 1, ?) WHERE id = ?",
                nombre, JsonSocial.encodeToString(PerfilSubido.serializer(), perfil.copy(nombre = nombre)), ahora(), yo)
        }
    }

    @Synchronized
    fun subirPerfil(yo: Long, p: PerfilSubido, app: String = "cajon") {
        AppsSocial.validar(app)
        val anterior = miCuenta(yo).nombre
        val nombre = p.nombre.trim().take(40)
        val cambia = nombre != anterior && apodoDesde(yo) <= ahora()
        val limpio = p.copy(
            nombre = if (cambia) nombre else anterior, bio = p.bio.trim().take(200), portada = p.portada.takeIf(::portadaSegura) ?: "",
            enlaces = p.enlaces.filter { it.startsWith("https://") || it.startsWith("http://") }.map { it.take(200) }.take(5),
            partes = p.partes.filterValues { it.visibilidad in Vis.SUBIBLES }.entries.take(20).associate { it.key.take(30) to it.value },
        )
        val json = JsonSocial.encodeToString(PerfilSubido.serializer(), limpio.copy(partes = emptyMap()))
        if (JsonSocial.encodeToString(PerfilSubido.serializer(), limpio).length > 200_000) throw Problema(413, "muy_grande")
        b.transaccion {
            cambiar("DELETE FROM partes_perfil WHERE cuenta = ? AND app = ?", yo, app)
            limpio.partes.forEach { (clave, parte) -> cambiar("INSERT INTO partes_perfil VALUES(?,?,?,?,?)", yo, app, clave, parte.visibilidad, parte.datos.toString()) }
            if (cambia) cambiar("INSERT INTO apodo_cambios VALUES(?,?)", yo, ahora())
            cambiar("UPDATE cuentas SET nombre = ?, perfil = ?, cambio = MAX(cambio + 1, ?) WHERE id = ?", limpio.nombre, json, ahora(), yo)
        }
    }

    fun revisionPerfil(yo: Long): Long = b.usar { leer("SELECT cambio FROM cuentas WHERE id = ?", yo) { it.getLong(1) }.single() }

    @Synchronized
    fun subirPerfilApp(yo: Long, app: String, cambio: PerfilApp) = b.transaccion {
        AppsSocial.validar(app)
        val anterior = leer("SELECT perfil FROM cuentas WHERE id = ?", yo) { JsonSocial.decodeFromString<PerfilSubido>(it.getString(1)) }.single()
        val identidad = cambio.identidad
        if (identidad != null && cambio.revision != revisionPerfil(yo)) throw Problema(409, "perfil_revision")
        val existentes = leer("SELECT clave, visibilidad, datos FROM partes_perfil WHERE cuenta = ? AND app = ?", yo, app) {
            it.getString(1) to Parte(it.getString(2), JsonSocial.parseToJsonElement(it.getString(3)))
        }.toMap()
        // Fusionar cambia solo lo enviado; reemplazar conserva la canción del perfil (la manda aparte cada app).
        val partes = when {
            cambio.partes == null -> existentes
            cambio.fusionar -> existentes + cambio.partes - cambio.quitar.toSet()
            else -> cambio.partes + existentes.filterKeys { it == "cancion" && it !in cambio.partes }
        }
        if (partes.size > 20 || partes.any { it.key.isBlank() || it.key.length > 30 || it.value.visibilidad !in Vis.SUBIBLES }) throw Problema(400, "partes")
        subirPerfil(yo, anterior.copy(nombre = identidad?.nombre ?: miCuenta(yo).nombre,
            bio = identidad?.bio ?: anterior.bio, portada = identidad?.portada ?: anterior.portada,
            enlaces = identidad?.enlaces ?: anterior.enlaces, partes = partes), app)
        identidad?.privada?.let { ajustar(yo, Ajustes(it)) }
    }

    /** La foto: un JPEG de hasta 400 KB (la app la achica antes). */
    fun subirFoto(yo: Long, bytes: ByteArray) {
        esJpeg(bytes, 400_000)
        dirFotos.mkdirs()
        java.io.File(dirFotos, "$yo.jpg").writeBytes(bytes)
        b.usar { cambiar("UPDATE cuentas SET foto = 1, cambio = cambio + 1 WHERE id = ?", yo) }
    }

    fun quitarFoto(yo: Long) {
        java.io.File(dirFotos, "$yo.jpg").delete()
        b.usar { cambiar("UPDATE cuentas SET foto = 0, cambio = cambio + 1 WHERE id = ?", yo) }
    }

    /** La foto de alguien (si quien mira no está bloqueado). */
    fun foto(lector: Long?, usuario: String): ByteArray {
        val id = idDe(usuario)
        if (lector != null && bloqueado(id, lector)) throw Problema(404, "no_existe")
        return java.io.File(dirFotos, "$id.jpg").takeIf { it.exists() }?.readBytes() ?: throw Problema(404, "sin_foto")
    }

    fun verPerfil(lector: Long?, usuario: String, app: String = "cajon"): PerfilVisto {
        AppsSocial.validar(app)
        val id = idDe(usuario)
        if (lector != null && (bloqueado(id, lector) || bloqueado(lector, id))) throw Problema(404, "no_existe")
        val p = b.usar { leer("SELECT perfil FROM cuentas WHERE id = ?", id) { it.getString(1) }.first() }
            .let { runCatching { JsonSocial.decodeFromString(PerfilSubido.serializer(), it) }.getOrDefault(PerfilSubido()) }
            .copy(partes = b.usar { leer("SELECT clave, visibilidad, datos FROM partes_perfil WHERE cuenta = ? AND app = ?", id, app) {
                it.getString(1) to Parte(it.getString(2), JsonSocial.parseToJsonElement(it.getString(3)))
            }.toMap() })
        val relacion = relacion(lector, id)
        val cerrada = esPrivada(id) && relacion != "yo" && relacion != "sigo"
        val ve = { v: String -> !cerrada && (v == Vis.PUBLICA || relacion == "yo" || relacion == "sigo") }
        val pubs = if (cerrada) emptyList() else filas(lector, "p.app = ? AND p.cuenta = ? AND (p.oculta = 0 OR p.cuenta = ?) ORDER BY p.fijada DESC, p.cuando DESC LIMIT 60", app, id, lector ?: -1).map { it.second }.filter { ve(it.visibilidad) }
        val (seguidores, siguiendo, cuantas) = b.usar {
            Triple(
                leer("SELECT COUNT(*) FROM seguir WHERE de IN (SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0) AND a = ? AND aceptada = 1", id) { it.getInt(1) }.first(),
                leer("SELECT COUNT(*) FROM seguir WHERE a IN (SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0) AND de = ? AND aceptada = 1", id) { it.getInt(1) }.first(),
                leer("SELECT COUNT(*) FROM publicaciones WHERE cuenta = ? AND app = ?", id, app) { it.getInt(1) }.first(),
            )
        }
        val meSigue = lector != null && sigue(id, lector)
        val megusta = b.usar { leer("SELECT COUNT(*) FROM megusta m JOIN publicaciones p ON p.id = m.pub WHERE p.cuenta = ? AND p.app = ?", id, app) { it.getInt(1) }.first() }
        val guardadas = relacion == "yo" || p.partes["guardadas"]?.let { ve(it.visibilidad) } == true
        return PerfilVisto(
            persona(id), p.bio, p.portada, p.enlaces, if (cerrada) emptyMap() else p.partes.filterValues { ve(it.visibilidad) }.mapValues { it.value.datos },
            pubs, relacion, meSigue, seguidores, siguiendo, cuantas, cerrada, megusta, guardadas,
        )
    }

    fun verPerfilGlobal(lector: Long?, usuario: String): PerfilVisto {
        val perfiles = listOf("cajon", "forzudo", "cucha", "alcancia").map { verPerfil(lector, usuario, it) }
        return perfiles.first().copy(publicaciones = perfiles.flatMap { it.publicaciones }.sortedByDescending { it.cuando }.take(60),
            cuantas = perfiles.sumOf { it.cuantas }, megusta = perfiles.sumOf { it.megusta })
    }

    /* ----- Imágenes de las publicaciones ----- */

    /** Guarda una imagen (JPEG de hasta 900 KB; la app la achica antes) y devuelve su nombre, para ponerlo en la publicación. */
    fun subirImagen(yo: Long, bytes: ByteArray): ImagenSubida {
        esJpeg(bytes, 900_000)
        val cuantas = b.usar { leer("SELECT COUNT(*) FROM imagenes WHERE cuenta = ? AND cuando > ?", yo, ahora() - 24 * 3600_000L) { it.getInt(1) }.first() }
        if (cuantas >= 100) throw Problema(429, "despacio")
        val nombre = Base.tokenNuevo().take(22)
        dirImagenes.mkdirs()
        java.io.File(dirImagenes, "$nombre.jpg").writeBytes(bytes)
        b.usar { cambiar("INSERT INTO imagenes(nombre, cuenta, cuando) VALUES(?,?,?)", nombre, yo, ahora()) }
        return ImagenSubida(nombre)
    }

    /** Una imagen por su nombre (que no se adivina: solo la tiene quien vio la publicación). */
    fun imagen(nombre: String, lector: Long? = null, token: String? = null): ByteArray {
        if (!Regex("[A-Za-z0-9_-]{22}").matches(nombre)) throw Problema(404, "no_existe")
        val estados = b.usar { leer("SELECT estado FROM publicaciones WHERE estado != 'visible' AND cuenta = (SELECT cuenta FROM imagenes WHERE nombre = ?) AND instr(datos, ?) > 0", nombre, nombre) { it.getString(1) } +
            leer("SELECT estado FROM historias WHERE estado != 'visible' AND cuenta = (SELECT cuenta FROM imagenes WHERE nombre = ?) AND instr(datos, ?) > 0", nombre, nombre) { it.getString(1) } }
        if ("preservado" in estados) throw Problema(404, "no_existe")
        if ("retirado" in estados) {
            if (lector == null || "ver_reportes" !in equipo.permisos(lector)) throw Problema(404, "no_existe")
            equipo.requiere(lector, "ver_reportes", token)
        }
        if (!Regex("[A-Za-z0-9_-]{22}").matches(nombre)) throw Problema(404, "no_existe")
        if (b.usar { leer("SELECT cuenta FROM imagenes WHERE nombre = ?", nombre) { it.getLong(1) }.firstOrNull() }?.let(::visible) != true) throw Problema(404, "no_existe")
        return java.io.File(dirImagenes, "$nombre.jpg").takeIf { it.exists() }?.readBytes() ?: throw Problema(404, "no_existe")
    }

    @Synchronized
    fun subirVideo(yo: Long, bytes: ByteArray): ImagenSubida {
        val datos = revisarVideo(bytes)
        // Cupo diario: sin él, una sola cuenta podía llenar el disco del servidor.
        val hoy = b.usar { leer("SELECT COUNT(*) FROM videos WHERE cuenta = ? AND cuando > ?", yo, ahora() - 24 * 3600_000L) { it.getInt(1) }.first() }
        if (hoy >= MAX_VIDEOS_DIA) throw Problema(429, "despacio")
        val nombre = Base.tokenNuevo().take(22)
        dirVideos.mkdirs()
        val archivo = java.io.File(dirVideos, "$nombre.mp4")
        try {
            archivo.writeBytes(bytes)
            b.usar { cambiar("INSERT INTO videos(nombre, cuenta, cuando, duracion, ancho, alto) VALUES(?,?,?,?,?,?)", nombre, yo, ahora(), datos.duracion, datos.ancho, datos.alto) }
        } catch (e: Exception) { archivo.delete(); throw e }
        return ImagenSubida(nombre)
    }

    /** La portada y el vídeo comparten el estado de moderación de su publicación. */
    @Synchronized
    fun video(nombre: String, lector: Long?, token: String?): java.io.File {
        if (!Regex("[A-Za-z0-9_-]{22}").matches(nombre)) throw Problema(404, "no_existe")
        val autor = b.usar { leer("SELECT cuenta FROM videos WHERE nombre = ?", nombre) { it.getLong(1) }.firstOrNull() }
            ?: throw Problema(404, "no_existe")
        if (!visible(autor)) throw Problema(404, "no_existe")
        val publicaciones = b.usar { leer("SELECT estado, visibilidad, oculta FROM publicaciones WHERE cuenta = ? AND instr(datos, ?) > 0", autor, nombre) { Triple(it.getString(1), it.getString(2), it.getInt(3) != 0) } }
        if (publicaciones.any { it.first == "preservado" }) throw Problema(404, "no_existe")
        // El video de una historia vigente (o destacada): lo ven quien lo subió y quienes lo siguen.
        val enHistoria = b.usar { leer("SELECT 1 FROM historias WHERE cuenta = ? AND instr(datos, ?) > 0 AND estado = 'visible' AND oculta = 0 AND (expira > ? OR destacada != '')", autor, nombre, ahora()) { 1 }.isNotEmpty() }
        if (publicaciones.isEmpty() && enHistoria && (lector == autor || (lector != null && sigue(lector, autor) && !bloqueado(autor, lector))))
            return java.io.File(dirVideos, "$nombre.mp4").takeIf { it.isFile } ?: throw Problema(404, "no_existe")
        if (publicaciones.any { it.first == "retirado" || (it.third && lector != autor) }) {
            if (lector == null || "ver_reportes" !in equipo.permisos(lector)) throw Problema(404, "no_existe")
            equipo.requiere(lector, "ver_reportes", token)
        } else if (lector != autor && publicaciones.none { it.first == "visible" && puedeVer(lector, autor, it.second) }) throw Problema(404, "no_existe")
        return java.io.File(dirVideos, "$nombre.mp4").takeIf { it.isFile } ?: throw Problema(404, "no_existe")
    }

    private fun purgarVideosSinPublicacion() = b.usar {
        leer("SELECT nombre FROM videos WHERE NOT EXISTS(SELECT 1 FROM publicaciones p WHERE p.cuenta = videos.cuenta AND instr(p.datos, videos.nombre) > 0) AND NOT EXISTS(SELECT 1 FROM historias h WHERE h.cuenta = videos.cuenta AND instr(h.datos, videos.nombre) > 0) AND cuando <= ?", ahora() - DIA) { it.getString(1) }.forEach {
            if (java.io.File(dirVideos, "$it.mp4").delete() || !java.io.File(dirVideos, "$it.mp4").exists()) cambiar("DELETE FROM videos WHERE nombre = ?", it)
        }
    }

    /* ----- Publicaciones ----- */

    /**
     * Sube todas las publicaciones de la persona: lo que ya estaba (por [PublicacionSubida.clave]) se actualiza sin
     * perder sus me gusta ni comentarios, lo nuevo se agrega y lo que ya no viene (lo volvió privado o lo borró) se quita.
     */
    fun verLugar(id: Long): Lugar? = b.usar { lugar(id) }

    fun buscarLugares(nombre: String, ciudad: String): List<Lugar> = b.usar {
        val n = normalizarLugar(nombre); val c = normalizarLugar(ciudad)
        if (n.length > 120 || c.length > 80) throw Problema(400, "lugar_esquema")
        leer("SELECT id FROM lugares WHERE retirado = 0 AND instr(nombre_busqueda, ?) > 0 AND instr(ciudad_busqueda, ?) > 0 ORDER BY nombre_busqueda, id LIMIT 30", n, c) { it.getLong(1) }.mapNotNull { lugar(it) }
    }

    fun crearLugar(yo: Long, datos: JsonObject): Lugar = b.transaccion {
        val d = validarLugar(datos)
        val nombre = d.getValue("nombre").jsonPrimitive.content
        val ciudad = d.getValue("ciudad").jsonPrimitive.content
        val existente = leer("SELECT id, retirado FROM lugares WHERE nombre_busqueda = ? AND ciudad_busqueda = ?", normalizarLugar(nombre), normalizarLugar(ciudad)) { it.getLong(1) to it.getInt(2) }.singleOrNull()
        if (existente != null) return@transaccion lugar(existente.first) ?: throw Problema(409, "lugar_retirado")
        if (leer("SELECT COUNT(*) FROM lugares WHERE cuenta = ? AND cuando >= ?", yo, ahora() - 24 * 3600_000L) { it.getInt(1) }.single() >= 10) throw Problema(429, "despacio")
        val id = insertar("INSERT INTO lugares(nombre,ciudad,nombre_busqueda,ciudad_busqueda,direccion,lat,lon,cuenta,cuando) VALUES(?,?,?,?,?,?,?,?,?)", nombre, ciudad, normalizarLugar(nombre), normalizarLugar(ciudad), d["direccion"]?.jsonPrimitive?.content, d["lat"]?.jsonPrimitive?.doubleOrNull, d["lon"]?.jsonPrimitive?.doubleOrNull, yo, ahora())
        lugar(id)!!
    }

    fun corregirLugar(yo: Long, id: Long, datos: JsonObject, token: String?): Lugar = b.transaccion {
        val d = validarLugar(datos, true)
        val actual = lugar(id) ?: throw Problema(404, "no_existe")
        if (actual.cuenta != yo) {
            equipo.requiere(yo, "gestionar_publicaciones", token)
            actual.cuenta?.let { equipo.inferior(yo, it) }
            equipo.registrar(yo.toString(), "lugar_corregir", id.toString(), "", token)
        }
        cambiar("UPDATE lugares SET direccion = ?, lat = ?, lon = ? WHERE id = ?", d["direccion"]?.jsonPrimitive?.content ?: actual.direccion,
            d["lat"]?.jsonPrimitive?.doubleOrNull ?: actual.lat, d["lon"]?.jsonPrimitive?.doubleOrNull ?: actual.lon, id)
        lugar(id)!!
    }

    /**
     * Sube publicaciones y quita solo las que el teléfono dice ([quitar], por clave). A diferencia de
     * [subirPublicaciones], lo que falta en la lista no se borra: un teléfono nuevo o vacío no se lleva lo publicado.
     */
    fun subirCambios(yo: Long, c: CambiosPublicaciones, app: String = "cajon", estricto: Boolean = app != "cajon") {
        AppsSocial.validar(app)
        if (c.lista.size > 2000 || c.quitar.size > 2000) throw Problema(413, "muy_grande")
        if (estricto) validarLoteApp(app, c)
        val quitar = (c.quitar + c.lista.filter { it.visibilidad !in Vis.SUBIBLES }.map { it.clave }).map { it.take(80) }.toSet()
        escribirPublicaciones(yo, c.lista.filter { it.clave.take(80) !in quitar }, app, quitar, estricto)
    }

    @Synchronized
    fun subirPublicaciones(yo: Long, lista: List<PublicacionSubida>) = escribirPublicaciones(yo, lista, "cajon", null)

    private fun escribirPublicaciones(yo: Long, lista: List<PublicacionSubida>, app: String, quitar: Set<String>?, estricto: Boolean = false) {
        AppsSocial.validar(app)
        if (app == "cajon" && lista.any { it.tipo in setOf("entreno", "plato", "receta", "resena_restaurante") }) throw Problema(400, "tipo_app")
        if (lista.size > 2000) throw Problema(413, "muy_grande")
        val buenas = lista.filter { it.visibilidad in Vis.SUBIBLES && it.clave.isNotBlank() }.distinctBy { it.clave }
        b.transaccion {
            val bloqueadas = leer("SELECT clave FROM retiradas WHERE cuenta = ? AND app = ?", yo, app) { it.getString(1) }.toSet()
            val buenas = buenas.filter { it.clave.take(80) !in bloqueadas }
            val claves = buenas.map { it.clave.take(80) }.toSet()
            val yaEstaban = leer("SELECT clave FROM publicaciones WHERE cuenta = ? AND app = ?", yo, app) { it.getString(1) }.toSet()
            val nuevaParaAvisar = buenas.filter { it.clave.take(80) !in yaEstaban && ahora() - it.cuando < 24 * 3600_000L }.maxByOrNull { it.cuando }?.clave?.take(80)
            leer("SELECT id, clave FROM publicaciones WHERE cuenta = ? AND app = ?", yo, app) { it.getLong(1) to it.getString(2) }
                .filter { if (quitar == null) it.second !in claves else it.second in quitar }.forEach {
                    cambiar("DELETE FROM publicaciones WHERE id = ? AND estado = 'visible' AND NOT EXISTS(SELECT 1 FROM comentarios c WHERE c.pub = publicaciones.id AND c.estado != 'visible')", it.first)
                    // Si queda como contenedora de evidencia, deja de ser pública hasta su purga.
                    cambiar("UPDATE publicaciones SET estado = 'retirado', retirado_hasta = 0 WHERE id = ? AND estado = 'visible'", it.first)
                }
            val mias = leer("SELECT nombre FROM imagenes WHERE cuenta = ?", yo) { it.getString(1) }.toSet()
            val videosMios = leer("SELECT nombre FROM videos WHERE cuenta = ?", yo) { it.getString(1) }.toSet()
            buenas.forEach { p ->
                if (app == "cucha" && "lugar" in p.datos && lugar(p.datos.getValue("lugar").jsonPrimitive.long) == null) throw Problema(400, "lugar_no_disponible")
                val video = p.datos["video"]
                val poster = p.datos["poster"]
                if (video != null) {
                    if (video !is JsonPrimitive || !video.isString || video.content !in videosMios || p.tipo !in setOf("foto", "entreno", "plato", "receta", "resena_restaurante", "logro")) throw Problema(400, "video_formato")
                    if (poster !is JsonPrimitive || !poster.isString || poster.content !in mias) throw Problema(400, "poster")
                    val fotos = p.datos["imagenes"] as? kotlinx.serialization.json.JsonArray
                    val fotosResena = p.datos["fotos"] as? kotlinx.serialization.json.JsonArray
                    if (!fotos.isNullOrEmpty() || !fotosResena.isNullOrEmpty() || (p.datos["imagen"] as? JsonPrimitive)?.content?.isNotBlank() == true) throw Problema(400, "video_y_fotos")
                } else if (poster != null) throw Problema(400, "poster")
                val imagenes = p.datos["imagenes"]
                if (imagenes != null && (imagenes !is kotlinx.serialization.json.JsonArray || imagenes.size > 10 || imagenes.any { it !is JsonPrimitive || !it.isString || it.content !in mias })) throw Problema(400, "imagenes")
                if (p.tipo == "resena" && !resenaValida(p.datos, mias)) { if (estricto) throw Problema(400, "resena") else return@forEach }
                val datos = JsonSocial.encodeToString(JsonObject.serializer(), JsonObject(sinPortadasAjenas(p.datos) + ("tipo" to JsonPrimitive(p.tipo.take(20)))))
                if (datos.length > (if (app == "cucha") 100_000 else 20_000)) { if (estricto) throw Problema(400, "publicacion_grande") else return@forEach }
                cambiar("""INSERT INTO publicaciones(cuenta, app, clave, visibilidad, cuando, datos) VALUES(?,?,?,?,?,?)
                    ON CONFLICT(cuenta, app, clave) DO UPDATE SET visibilidad = excluded.visibilidad, cuando = excluded.cuando, datos = excluded.datos WHERE publicaciones.estado = 'visible'""",
                    yo, app, p.clave.take(80), p.visibilidad, p.cuando, datos)
            }
            nuevaParaAvisar?.let { clave -> avisarSeguidores(yo, clave, app) }
        }
    }

    /** Avisa a sus seguidores que publicó algo (una vez cada 6 horas por autor, para no llenar de avisos). */
    private fun avisarSeguidores(yo: Long, clave: String, app: String) {
        val id = b.usar { leer("SELECT id FROM publicaciones WHERE cuenta = ? AND clave = ? AND app = ?", yo, clave, app) { it.getLong(1) }.firstOrNull() } ?: return
        val reciente = b.usar { leer("SELECT 1 FROM avisos WHERE de = ? AND tipo = 'publico' AND app = ? AND cuando > ? LIMIT 1", yo, app, ahora() - 6 * 3600_000L) { 1 }.isNotEmpty() }
        if (reciente) return
        b.usar { leer("SELECT de FROM seguir WHERE a = ? AND aceptada = 1", yo) { it.getLong(1) } }.forEach { avisar(it, yo, "publico", id.toString()) }
    }

    /** Quiénes le escribieron y todavía no los recibió (los más recientes primero, hasta 5): para la notificación, sin abrir nada. */
    fun remitentesSinLeer(yo: Long, sesion: String = ""): List<Persona> = b.usar {
        val desde = leer("SELECT ultimo FROM chat_cursores WHERE huella = ?", sesion) { it.getLong(1) }.firstOrNull() ?: 0
        leer("SELECT de, MAX(id) FROM mensajes WHERE para = ? AND id > ? AND silencioso = 0 GROUP BY de ORDER BY MAX(id) DESC LIMIT 20", yo, desde) { it.getLong(1) }
    }.filter { !bloqueado(yo, it) && visible(it) }.take(5).map { persona(it) }

    /** Los mensajes que esta sesión ([sesion]) todavía no recibió. */
    fun mensajesSinLeer(yo: Long, sesion: String = ""): Int = b.usar {
        // Preguntar ya cuenta como usar el chat: desde aquí, lo que llegue espera también a esta sesión.
        if (sesion.isNotEmpty()) cambiar("INSERT OR IGNORE INTO chat_cursores(huella, cuenta, ultimo, cuando) VALUES(?,?,0,?)", sesion, yo, ahora())
        val desde = leer("SELECT ultimo FROM chat_cursores WHERE huella = ?", sesion) { it.getLong(1) }.firstOrNull() ?: 0
        leer("SELECT COUNT(*) FROM mensajes WHERE de IN (SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0) AND para = ? AND id > ? AND silencioso = 0", yo, desde) { it.getInt(1) }.first()
    }

    /** Las publicaciones que cumplen [donde] (SQL sobre `p`), con su autor y sus cifras, en una sola consulta; con el id del autor. */
    private fun filas(lector: Long?, donde: String, vararg args: Any?): List<Pair<Long, Publicacion>> = b.usar {
        leer("""SELECT p.id, p.cuenta, p.visibilidad, p.cuando, p.datos, c.usuario, c.nombre, c.foto, c.privada,
                    (SELECT COUNT(*) FROM megusta m WHERE m.pub = p.id),
                    (SELECT COUNT(*) FROM comentarios k WHERE k.estado = 'visible' AND k.borrado = 0 AND k.pub = p.id AND k.cuenta IN (SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0)),
                    p.n_vistas,
                    EXISTS(SELECT 1 FROM megusta m WHERE m.pub = p.id AND m.cuenta = ?),
                    EXISTS(SELECT 1 FROM guardados g WHERE g.pub = p.id AND g.cuenta = ?),
                    (SELECT COUNT(*) FROM guardados g WHERE g.pub = p.id AND g.cuenta <> p.cuenta),
                    (SELECT COUNT(*) FROM utiles u WHERE u.pub = p.id AND u.cuenta IN (SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0)),
                    EXISTS(SELECT 1 FROM utiles u WHERE u.pub = p.id AND u.cuenta = ?), p.app, c.insignia
                FROM publicaciones p JOIN cuentas c ON c.id = p.cuenta WHERE p.estado = 'visible' AND c.borrar_el = 0 AND c.cerrada = 0 AND $donde""", lector, lector, lector, *args) { r ->
            var datos = JsonSocial.parseToJsonElement(r.getString(5)) as JsonObject
            if (r.getString(18) == "cucha" && "lugar" in datos) {
                val ficha = lugar(datos.getValue("lugar").jsonPrimitive.long)
                datos = JsonObject(datos + ("lugarDetalle" to (ficha?.let { JsonSocial.encodeToJsonElement(Lugar.serializer(), it) } ?: JsonNull)))
            }
            r.getLong(2) to Publicacion(r.getLong(1), Persona(r.getString(6), r.getString(7), r.getInt(8) == 1, r.getInt(9) == 1, r.getString(19)),
                (datos["tipo"] as? JsonPrimitive)?.content ?: "", r.getLong(4), r.getString(3), datos,
                r.getInt(10), r.getInt(11), r.getInt(13) == 1, r.getInt(14) == 1, r.getInt(12), guardados = r.getInt(15),
                utiles = r.getInt(16), yoUtil = r.getInt(17) == 1, app = r.getString(18))
        }
    }

    /** Una publicación como la ve [lector], o null si no la puede ver. */
    fun publicacion(lector: Long?, id: Long): Publicacion? =
        filas(lector, "p.id = ? AND (p.oculta = 0 OR p.cuenta = ?)", id, lector ?: -1).firstOrNull()?.takeIf { (autor, p) -> puedeVer(lector, autor, p.visibilidad) }?.second

    /**
     * Las reseñas de una obra (por su [fuente], "tmdb:movie:603"): las públicas de todos y, con sesión, las que deja ver
     * a quien sigue; sin las de cuentas bloqueadas ni ocultas. Las más nuevas primero.
     */
    fun resenas(lector: Long?, fuente: String, parte: String = "", orden: String = "", antes: Long? = null): List<Publicacion> {
        if (fuente.isBlank() || fuente.length > 120 || parte.length > 80) return emptyList()
        val porParte = if (parte.isBlank()) "" else " AND json_extract(p.datos, '$.parte') = ?"
        val args = mutableListOf<Any?>(fuente).apply { if (parte.isNotBlank()) add(parte) }
        val lista = filas(lector, """p.app = 'cajon' AND json_extract(p.datos, '$.fuente') = ? AND json_extract(p.datos, '$.tipo') = 'resena'$porParte AND p.oculta = 0 AND c.cerrada = 0 AND c.borrar_el = 0
                AND (? IS NULL OR (p.cuenta NOT IN (SELECT a FROM bloqueos WHERE de = ?) AND p.cuenta NOT IN (SELECT de FROM bloqueos WHERE a = ?)))
                AND p.cuando < ? ORDER BY p.cuando DESC LIMIT 400""", *args.toTypedArray(), lector, lector, lector, antes ?: Long.MAX_VALUE)
            .filter { (autor, p) -> puedeVer(lector, autor, p.visibilidad) }.map { it.second }
        // «utiles»: las que más gente encontró útiles primero (cota de Wilson sobre las vistas); sin orden, las más nuevas.
        return if (orden == "utiles") lista.sortedWith(compareByDescending<Publicacion> { wilson(it.utiles, maxOf(it.vistas, it.utiles)) }.thenByDescending { it.cuando }).take(50)
        else lista.take(50)
    }

    /**
     * El autor borra una publicación suya por su id, aunque este teléfono no la haya subido. Si guarda comentarios
     * retenidos por moderación, deja de ser pública (como al quitarla por sincronización). Su clave queda en
     * `retiradas` para que una sincronización vieja de otro teléfono no la vuelva a crear.
     */
    @Synchronized
    fun borrarPublicacion(yo: Long, id: Long) {
        val videos = b.usar { leer("SELECT v.nombre FROM videos v JOIN publicaciones p ON p.cuenta = v.cuenta AND instr(p.datos, v.nombre) > 0 WHERE p.id = ? AND p.cuenta = ?", id, yo) { it.getString(1) } }
        b.usar {
            autoCommit = false
            try {
                if (leer("SELECT 1 FROM publicaciones WHERE id = ? AND cuenta = ? AND estado = 'visible'", id, yo) { 1 }.isEmpty()) throw Problema(404, "no_existe")
                cambiar("INSERT OR IGNORE INTO retiradas(cuenta, app, clave, cuando) SELECT cuenta, app, clave, ? FROM publicaciones WHERE id = ?", ahora(), id)
                cambiar("DELETE FROM publicaciones WHERE id = ? AND NOT EXISTS(SELECT 1 FROM comentarios c WHERE c.pub = publicaciones.id AND c.estado != 'visible')", id)
                cambiar("UPDATE publicaciones SET estado = 'retirado', retirado_hasta = 0 WHERE id = ? AND estado = 'visible'", id)
                commit()
            } catch (x: Exception) { rollback(); throw x } finally { autoCommit = true }
        }
        b.usar {
            videos.forEach { nombre ->
                if (leer("SELECT 1 FROM publicaciones WHERE instr(datos, ?) > 0", nombre) { 1 }.isEmpty()) {
                    val archivo = java.io.File(dirVideos, "$nombre.mp4")
                    if (archivo.delete() || !archivo.exists()) cambiar("DELETE FROM videos WHERE nombre = ?", nombre)
                }
            }
        }
    }

    /** Marca o quita «útil» en una reseña ajena que [yo] puede ver. */
    fun util(yo: Long, pub: Long, si: Boolean) {
        val p = publicacion(yo, pub) ?: throw Problema(404, "no_existe")
        if (p.tipo !in setOf("resena", "resena_restaurante")) throw Problema(400, "no_es_resena")
        if (idDe(p.autor.usuario) == yo) throw Problema(400, "propia")
        b.usar { if (si) cambiar("INSERT OR IGNORE INTO utiles(pub, cuenta, cuando) VALUES(?,?,?)", pub, yo, ahora()) else cambiar("DELETE FROM utiles WHERE pub = ? AND cuenta = ?", pub, yo) }
    }


    /** El feed: lo último de quienes sigue (y lo suyo), del más reciente al más viejo; [antes] para seguir bajando. */
    fun actividad(yo: Long, antes: Long?, n: Int = 40, app: String = "cajon"): List<Publicacion> =
        filas(yo, """p.app = ? AND (p.cuenta = ? OR (p.oculta = 0 AND p.cuenta IN (SELECT a FROM seguir WHERE de = ? AND aceptada = 1)))
                AND p.cuando < ? ORDER BY p.cuando DESC LIMIT ?""", AppsSocial.validar(app), yo, yo, antes ?: Long.MAX_VALUE, n.coerceIn(1, 100)).map { it.second }

    /**
     * Para descubrir: candidatos iguales para todos (el servidor no perfila a nadie; el teléfono los ordena según los
     * gustos de la persona, que nunca salen de él). Lo público de cuentas públicas que todavía no sigue: 70 % lo más
     * reciente de la semana, 20 % lo mejor de lo demás (cota de Wilson) y 10 % lo nuevo con pocas
     * vistas, para que tenga su oportunidad. Cada una con su [Publicacion.calidad].
     */
    fun explorar(lector: Long?, antes: Long?, n: Int = 200, app: String = "cajon"): List<Publicacion> {
        val ahora = ahora()
        // Las 1000 más recientes: la semana y lo nuevo salen de ahí, y en una red chiquita también lo más viejo completa.
        val todas = filas(lector, """p.app = ? AND p.visibilidad = 'publica' AND c.privada = 0 AND p.oculta = 0 AND c.cerrada = 0 AND c.borrar_el = 0 AND p.cuando < ?
                AND (? IS NULL OR (p.cuenta <> ? AND p.cuenta NOT IN (SELECT a FROM seguir WHERE de = ?)
                    AND p.cuenta NOT IN (SELECT a FROM bloqueos WHERE de = ?) AND p.cuenta NOT IN (SELECT de FROM bloqueos WHERE a = ?)))
                ORDER BY p.cuando DESC LIMIT 1000""", AppsSocial.validar(app), antes ?: Long.MAX_VALUE, lector, lector, lector, lector, lector)
            .map { it.second.copy(calidad = calidad(it.second)) }
        val tope = n.coerceIn(1, 200)
        val semana = todas.filter { ahora - it.cuando < 7L * 24 * 3600_000 }.take((tope * 0.7).toInt())
        val mejores = (todas - semana.toSet()).sortedByDescending { wilson(it.megusta + it.guardados, it.vistas) }.take((tope * 0.2).toInt())
        val nuevas = (todas - semana.toSet() - mejores.toSet()).filter { it.vistas < 30 && ahora - it.cuando < 72 * 3600_000L }.take(tope - semana.size - mejores.size)
        val elegidas = (semana + mejores + nuevas).distinctBy { it.id }
        // Si falta para completar, lo más reciente que quede.
        return (elegidas + (todas - elegidas.toSet())).take(tope).sortedByDescending { it.cuando }
    }

    /**
     * Qué tan bien le fue a una publicación, comparada con lo normal: tasas por vista con promedio bayesiano (como si
     * llevara [M] vistas normales, para que dos me gusta en tres vistas no la pongan arriba de todo). Guardar y comentar
     * valen más que un me gusta (sirvió, no solo enganchó).
     */
    private fun calidad(p: Publicacion): Double {
        fun t(n: Int, previa: Double) = (n + M * previa) / (p.vistas.coerceAtLeast(n) + M)
        val q = t(p.megusta, PREVIA_MEGUSTA) + 2 * t(p.guardados, PREVIA_GUARDADO) + 3 * t(p.comentarios, PREVIA_COMENTARIO)
        return (q / (PREVIA_MEGUSTA + 2 * PREVIA_GUARDADO + 3 * PREVIA_COMENTARIO)).coerceIn(0.0, 3.0)
    }

    /** Cota inferior de Wilson (95 %) de la tasa [si] de [n]. */
    private fun wilson(si: Int, n: Int): Double {
        if (n <= 0) return 0.0
        val z = 1.96; val p = (si.toDouble() / n).coerceAtMost(1.0)
        return (p + z * z / (2 * n) - z * kotlin.math.sqrt((p * (1 - p) + z * z / (4 * n)) / n)) / (1 + z * z / n)
    }

    fun meGusta(yo: Long, pub: Long, si: Boolean) {
        val p = publicacion(yo, pub) ?: throw Problema(404, "no_existe")
        val nuevo = b.usar { if (si) cambiar("INSERT OR IGNORE INTO megusta(pub, cuenta) VALUES(?,?)", pub, yo) else cambiar("DELETE FROM megusta WHERE pub = ? AND cuenta = ?", pub, yo) }
        if (si && nuevo > 0) avisar(idDe(p.autor.usuario), yo, "megusta", pub.toString())
    }

    fun guardar(yo: Long, pub: Long, si: Boolean) {
        publicacion(yo, pub) ?: throw Problema(404, "no_existe")
        b.usar { if (si) cambiar("INSERT OR IGNORE INTO guardados(pub, cuenta, cuando) VALUES(?,?,?)", pub, yo, ahora()) else cambiar("DELETE FROM guardados WHERE pub = ? AND cuenta = ?", pub, yo) }
    }

    /** Las guardadas de [usuario], si las muestra a quien mira ([PerfilVisto.guardadasVisibles]). */
    fun guardadasDe(lector: Long?, usuario: String, app: String = "cajon"): List<Publicacion> {
        val id = idDe(usuario)
        if (!verPerfil(lector, usuario, app).guardadasVisibles) throw Problema(403, "privada")
        return guardados(id, app).filter { pub -> publicacion(lector, pub.id) != null }
    }

    /** Lo público que lleva la etiqueta #[etiqueta] (en sus palabras), lo más reciente primero. */
    fun conEtiqueta(lector: Long?, etiqueta: String, antes: Long?, app: String = "cajon"): List<Publicacion> {
        val e = etiqueta.trim().removePrefix("#").lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(40)
        if (e.length < 2) return emptyList()
        return filas(lector, """p.app = ? AND p.oculta = 0 AND p.visibilidad = 'publica' AND c.privada = 0 AND p.cuando < ? AND lower(p.datos) LIKE ? ESCAPE '\'
                ORDER BY p.cuando DESC LIMIT 60""", AppsSocial.validar(app), antes ?: Long.MAX_VALUE, "%#" + e.replace("_", "\\_") + "%")
            .filter { (autor, _) -> lector == null || (!bloqueado(autor, lector) && !bloqueado(lector, autor)) }.map { it.second }
    }

    fun guardados(yo: Long, app: String = "cajon"): List<Publicacion> =
        filas(yo, "p.app = ? AND p.id IN (SELECT pub FROM guardados WHERE cuenta = ?) ORDER BY (SELECT cuando FROM guardados WHERE pub = p.id AND cuenta = ?) DESC LIMIT 200", AppsSocial.validar(app), yo, yo)
            .filter { (autor, p) -> puedeVer(yo, autor, p.visibilidad) }.map { it.second }

    fun comentarios(lector: Long?, pub: Long): List<Comentario> {
        val p = publicacion(lector, pub) ?: throw Problema(404, "no_existe")
        val autorPub = idDe(p.autor.usuario)
        val lista = b.usar { leer("""SELECT id, cuenta, texto, cuando, padre, borrado,
            (SELECT COUNT(*) FROM megusta_comentarios m WHERE m.comentario = comentarios.id),
            (SELECT COUNT(*) FROM megusta_comentarios m WHERE m.comentario = comentarios.id AND m.cuenta = ?)
            FROM comentarios WHERE estado = 'visible' AND cuenta IN (SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0)
            AND pub = ? AND (oculto = 0 OR cuenta = ?) ORDER BY cuando, id""", lector ?: -1, pub, lector ?: -1) {
                val autor = it.getLong(2)
                autor to Comentario(it.getLong(1), if (it.getInt(6) == 1) Persona("", "", false) else persona(autor),
                    if (it.getInt(6) == 1) "" else it.getString(3), it.getLong(4), lector == autor || lector == autorPub,
                    it.getLong(5).let { valor -> if (it.wasNull()) null else valor }, megusta = it.getInt(7), yoMeGusta = it.getInt(8) > 0, borrado = it.getInt(6) == 1)
            } }.filter { lector == null || (!bloqueado(lector, it.first) && !bloqueado(it.first, lector)) }.map { it.second }
        val ids = lista.map { it.id }.toSet()
        val visibles = lista.filter { it.padre == null || it.padre in ids }
        return visibles.filter { !it.borrado || visibles.any { r -> r.padre == it.id } }
            .map { c -> c.copy(respuestas = visibles.count { it.padre == c.id }) }
    }

    fun meGustaComentario(yo: Long, id: Long, si: Boolean) {
        val pub = b.usar { leer("SELECT pub FROM comentarios WHERE id = ?", id) { it.getLong(1) }.firstOrNull() }
            ?: throw Problema(404, "no_existe")
        if (comentarios(yo, pub).none { it.id == id && !it.borrado }) throw Problema(404, "no_existe")
        b.usar {
            if (si) cambiar("INSERT OR IGNORE INTO megusta_comentarios(comentario, cuenta, cuando) VALUES(?,?,?)", id, yo, ahora())
            else cambiar("DELETE FROM megusta_comentarios WHERE comentario = ? AND cuenta = ?", id, yo)
        }
    }

    fun comentar(yo: Long, pub: Long, texto: String, padre: Long? = null): Long {
        val t = texto.trim().take(1000)
        if (t.isEmpty()) throw Problema(400, "vacio")
        val (creada, hora) = b.usar { leer("SELECT creada FROM cuentas WHERE id = ?", yo) { it.getLong(1) }.first() } to
            b.usar { leer("SELECT COUNT(*) FROM comentarios WHERE cuenta = ? AND cuando > ?", yo, ahora() - 3600_000L) { it.getInt(1) }.first() }
        if (hora >= (if (ahora() - creada < 7L * 24 * 3600_000) 10 else 60)) throw Problema(429, "despacio")
        val p = publicacion(yo, pub) ?: throw Problema(404, "no_existe")
        val autor = idDe(p.autor.usuario)
        if (bloqueado(autor, yo)) throw Problema(404, "no_existe")
        val respondido = padre?.let { id -> comentarios(yo, pub).firstOrNull { it.id == id && !it.borrado } ?: throw Problema(400, "padre") }
        val raiz = respondido?.let { it.padre ?: it.id }
        val id = b.usar { insertar("INSERT INTO comentarios(pub, cuenta, texto, cuando, padre) VALUES(?,?,?,?,?)", pub, yo, t, ahora(), raiz) }
        val respondidoA = respondido?.let { idDe(it.autor.usuario) }
        respondidoA?.let { avisar(it, yo, "respuesta", pub.toString()) }
        // Quien recibe el aviso de respuesta no recibe además el de comentario.
        if (respondidoA != autor) avisar(autor, yo, "comentario", pub.toString())
        return id
    }

    /** Borra un comentario: quien lo escribió o el dueño de la publicación. */
    fun borrarComentario(yo: Long, id: Long) {
        val (autor, dueno) = b.usar {
            leer("SELECT c.cuenta, p.cuenta FROM comentarios c JOIN publicaciones p ON p.id = c.pub WHERE c.id = ?", id) { it.getLong(1) to it.getLong(2) }.firstOrNull()
        } ?: throw Problema(404, "no_existe")
        if (yo != autor && yo != dueno) throw Problema(403, "no_es_tuyo")
        b.usar {
            val conserva = leer("SELECT 1 FROM comentarios WHERE padre = ? LIMIT 1", id) { 1 }.isNotEmpty()
            if (conserva) cambiar("UPDATE comentarios SET borrado = 1, texto = '' WHERE id = ? AND estado = 'visible'", id)
            else cambiar("DELETE FROM comentarios WHERE id = ? AND estado = 'visible'", id)
        }
    }

    /* ----- Seguir ----- */

    /** Seguir a alguien: si la cuenta es privada queda pendiente hasta que la apruebe. Devuelve la relación nueva. */
    fun seguir(yo: Long, usuario: String): String {
        val otro = idDe(usuario)
        if (otro == yo) throw Problema(400, "eres_tu")
        if (bloqueado(otro, yo) || bloqueado(yo, otro)) throw Problema(404, "no_existe")
        val privada = esPrivada(otro)
        val nuevo = b.usar { cambiar("INSERT OR IGNORE INTO seguir(de, a, cuando, aceptada) VALUES(?,?,?,?)", yo, otro, ahora(), if (privada) 0 else 1) }
        if (nuevo > 0) avisar(otro, yo, if (privada) "solicitud" else "seguir")
        return relacion(yo, otro)
    }

    /** Dejar de seguir (o cancelar la solicitud). */
    fun dejarDeSeguir(yo: Long, usuario: String) {
        val otro = idDe(usuario)
        b.usar { cambiar("DELETE FROM seguir WHERE de = ? AND a = ?", yo, otro) }
    }

    /** Quitar a un seguidor (o rechazar su solicitud). */
    fun quitarSeguidor(yo: Long, usuario: String) {
        val otro = idDe(usuario)
        b.usar { cambiar("DELETE FROM seguir WHERE de = ? AND a = ?", otro, yo) }
    }

    fun aceptar(yo: Long, usuario: String) {
        val otro = idDe(usuario)
        if (b.usar { cambiar("UPDATE seguir SET aceptada = 1 WHERE de = ? AND a = ? AND aceptada = 0", otro, yo) } > 0) avisar(otro, yo, "aceptada")
    }

    fun solicitudes(yo: Long): List<Persona> =
        b.usar { leer("SELECT de FROM seguir WHERE a = ? AND aceptada = 0 ORDER BY cuando DESC", yo) { it.getLong(1) } }.filter(::visible).map(::persona)

    /** Seguidores o seguidos de alguien; de una cuenta privada solo los ve quien la sigue. */
    fun lista(lector: Long?, usuario: String, seguidores: Boolean): List<Persona> {
        val id = idDe(usuario)
        if (esPrivada(id) && lector != id && (lector == null || !sigue(lector, id))) throw Problema(403, "privada")
        val sql = if (seguidores) "SELECT de FROM seguir WHERE a = ? AND aceptada = 1 ORDER BY cuando DESC LIMIT 500"
        else "SELECT a FROM seguir WHERE de = ? AND aceptada = 1 ORDER BY cuando DESC LIMIT 500"
        return b.usar { leer(sql, id) { it.getLong(1) } }.filter { lector == null || (!bloqueado(it, lector) && !bloqueado(lector, it)) }.filter(::visible).map(::persona)
    }

    /** Bloquear: deja de seguir en los dos sentidos, no se ven ni se escriben. */
    fun bloquear(yo: Long, usuario: String, si: Boolean) {
        val otro = idDe(usuario)
        if (otro == yo) throw Problema(400, "eres_tu")
        b.usar {
            if (si) {
                cambiar("INSERT OR IGNORE INTO bloqueos(de, a) VALUES(?,?)", yo, otro)
                cambiar("DELETE FROM seguir WHERE (de = ? AND a = ?) OR (de = ? AND a = ?)", yo, otro, otro, yo)
            } else cambiar("DELETE FROM bloqueos WHERE de = ? AND a = ?", yo, otro)
        }
    }

    fun bloqueados(yo: Long): List<Persona> = b.usar { leer("SELECT a FROM bloqueos WHERE de = ?", yo) { it.getLong(1) } }.filter(::visible).map(::persona)

    fun buscar(lector: Long?, q: String): List<Persona> {
        val t = q.trim().removePrefix("@").lowercase().take(40)
        if (t.length < 2) return emptyList()
        val patron = "%" + t.replace("%", "").replace("_", "\\_") + "%"
        return b.usar {
            leer("""SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0 AND (usuario LIKE ? ESCAPE '\' OR lower(nombre) LIKE ? ESCAPE '\')
                    ORDER BY (usuario = ?) DESC, (usuario LIKE ?) DESC, usuario LIMIT 30""", patron, patron, t, "$t%") { it.getLong(1) }
        }.filter { lector == null || (!bloqueado(it, lector) && !bloqueado(lector, it)) }.filter(::visible).map(::persona)
    }

    /**
     * Cuenta que [yo] vio estas publicaciones: una vez por cuenta (por una huella HMAC que se borra a los 14 días, sin
     * guardar quién vio qué), sin contar al autor ni lo que no puede ver.
     */
    fun vio(yo: Long, ids: List<Long>) {
        b.usar { cambiar("DELETE FROM vistas WHERE cuando < ?", ahora() - 14L * 24 * 3600_000) }
        ids.distinct().take(100).forEach { id ->
            val p = filas(yo, "p.id = ?", id).firstOrNull() ?: return@forEach
            if (p.first == yo || !puedeVer(yo, p.first, p.second.visibilidad)) return@forEach
            val nueva = b.usar { cambiar("INSERT OR IGNORE INTO vistas(huella, pub, cuando) VALUES(?,?,?)", huellaVista(yo, id), id, ahora()) }
            if (nueva > 0) b.usar { cambiar("UPDATE publicaciones SET n_vistas = n_vistas + 1 WHERE id = ?", id) }
        }
    }

    private fun huellaVista(cuenta: Long, pub: Long): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256").apply { init(javax.crypto.spec.SecretKeySpec(secreto, "HmacSHA256")) }
        return mac.doFinal("$cuenta:$pub".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /* ----- Notas de 24 horas ----- */

    fun ponerNota(yo: Long, n: NotaNueva) {
        val t = n.texto.trim().take(60)
        val adjunto = Adjuntos.limpio(n.datos["adjunto"] as? JsonObject)
        val adjunto2 = Adjuntos.limpio(n.datos["adjunto2"] as? JsonObject)
        // Sin palabras ni adjunto, la nota se quita; con solo un adjunto (una canción, por ejemplo), vale.
        if (t.isEmpty() && adjunto == null) { b.usar { cambiar("DELETE FROM notas WHERE cuenta = ?", yo) }; return }
        val base = sinPortadasAjenas(JsonObject(n.datos - "adjunto" - "adjunto2"))
        val datos = JsonSocial.encodeToString(JsonObject.serializer(), JsonObject(base + listOfNotNull(adjunto?.let { "adjunto" to it }, adjunto2?.let { "adjunto2" to it })))
        if (datos.length > 4000) throw Problema(413, "muy_grande")
        b.usar { cambiar("INSERT INTO notas(cuenta, texto, datos, cuando) VALUES(?,?,?,?) ON CONFLICT(cuenta) DO UPDATE SET texto = excluded.texto, datos = excluded.datos, cuando = excluded.cuando", yo, t, datos, ahora()) }
    }

    /** Las notas vigentes (24 horas) de quienes sigue y la suya; las de cuentas públicas que lo siguen también. */
    fun notas(yo: Long): List<Nota> = b.usar {
        leer("""SELECT n.cuenta, n.texto, n.datos, n.cuando FROM notas n WHERE n.cuando > ? AND (n.cuenta = ?
                OR n.cuenta IN (SELECT a FROM seguir WHERE de = ? AND aceptada = 1)) ORDER BY (n.cuenta = ?) DESC, n.cuando DESC LIMIT 60""",
            ahora() - 24 * 3600_000L, yo, yo, yo) { arrayOf(it.getLong(1), it.getString(2), it.getString(3), it.getLong(4)) }
    }.filter { visible(it[0] as Long) && !bloqueado(yo, it[0] as Long) }
        .map { Nota(persona(it[0] as Long), it[1] as String, JsonSocial.parseToJsonElement(it[2] as String) as JsonObject, it[3] as Long) }

    /* ----- Historias (24 horas, como en Instagram) ----- */

    /** Sube una historia a [app]: una foto propia, un texto o un adjunto (al menos uno). Hasta 30 al día. */
    fun subirHistoria(yo: Long, app: String, h: HistoriaNueva): Historia {
        AppsSocial.validar(app)
        val d = h.datos
        val texto = (d["texto"] as? JsonPrimitive)?.contentOrNull?.filter { it == '\n' || !it.isISOControl() }?.trim()?.take(200).orEmpty()
        val imagen = (d["imagen"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        // Un video corto propio (subido como los de las publicaciones).
        val video = (d["video"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (video.isNotEmpty() && b.usar { leer("SELECT 1 FROM videos WHERE nombre = ? AND cuenta = ?", video, yo) { 1 }.isEmpty() }) throw Problema(400, "video")
        val adjunto = Adjuntos.limpio(d["adjunto"] as? JsonObject)
        // Un segundo adjunto: la canción y además una obra (o al revés).
        val adjunto2 = Adjuntos.limpio(d["adjunto2"] as? JsonObject)
        val fondo = (d["fondo"] as? JsonPrimitive)?.intOrNull?.coerceIn(0, 7) ?: 0
        // Hecha en el editor: foto, textos, stickers y dibujo ya van dentro de la imagen (el texto queda aparte, para leerlo).
        val compuesta = (d["compuesta"] as? JsonPrimitive)?.booleanOrNull == true && imagen.isNotEmpty()
        // Solo para tus mejores amigos (como en Instagram).
        val mejores = (d["mejores"] as? JsonPrimitive)?.booleanOrNull == true
        // Solo para amigos (quienes se siguen contigo).
        val soloAmigos = !mejores && (d["amigos"] as? JsonPrimitive)?.booleanOrNull == true
        // Una pregunta abierta («Recomiéndame un libro») y las menciones (@), que reciben aviso.
        val pregunta = (d["pregunta"] as? JsonPrimitive)?.contentOrNull?.filter { !it.isISOControl() }?.trim()?.take(80).orEmpty()
        val menciones = (d["menciones"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase()?.trim()?.takeIf { u -> USUARIO.matches(u) } }.distinct().take(5)
        // Una encuesta (como en Instagram): una pregunta y de 2 a 4 opciones cortas.
        val encuesta = (d["encuesta"] as? JsonObject)?.let { e ->
            val pregunta = (e["pregunta"] as? JsonPrimitive)?.contentOrNull?.filter { !it.isISOControl() }?.trim()?.take(80).orEmpty()
            val opciones = (e["opciones"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.filter { c -> !c.isISOControl() }?.trim()?.take(40)?.takeIf { o -> o.isNotBlank() } }.take(4)
            if (opciones.size >= 2) buildJsonObject { put("pregunta", pregunta); put("opciones", JsonArray(opciones.map(::JsonPrimitive))) } else null
        }
        if (imagen.isNotEmpty() && b.usar { leer("SELECT 1 FROM imagenes WHERE nombre = ? AND cuenta = ?", imagen, yo) { 1 }.isEmpty() }) throw Problema(400, "imagen")
        if (imagen.isEmpty() && video.isEmpty() && texto.isEmpty() && adjunto == null && (d["encuesta"] as? JsonObject) == null && (d["pregunta"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()) throw Problema(400, "vacia")
        if (b.usar { leer("SELECT COUNT(*) FROM historias WHERE cuenta = ? AND cuando > ?", yo, ahora() - DIA) { it.getInt(1) }.first() } >= 30) throw Problema(429, "despacio")
        val datos = buildJsonObject {
            if (imagen.isNotEmpty()) put("imagen", imagen)
            if (video.isNotEmpty()) put("video", video)
            if (texto.isNotEmpty()) put("texto", texto)
            put("fondo", fondo)
            if (compuesta) put("compuesta", true)
            adjunto?.let { put("adjunto", it) }
            adjunto2?.let { put("adjunto2", it) }
            encuesta?.let { put("encuesta", it) }
            if (pregunta.isNotEmpty()) put("pregunta", pregunta)
            if (menciones.isNotEmpty()) put("menciones", JsonArray(menciones.map(::JsonPrimitive)))
        }
        val id = b.usar { insertar("INSERT INTO historias(cuenta, app, datos, cuando, expira, mejores, amigos) VALUES(?,?,?,?,?,?,?)", yo, app, datos.toString(), ahora(), ahora() + DIA, if (mejores) 1 else 0, if (soloAmigos) 1 else 0) }
        // Las menciones reciben aviso (si siguen existiendo y no hay bloqueo).
        menciones.forEach { u -> b.usar { leer("SELECT id FROM cuentas WHERE usuario = ? AND cerrada = 0 AND borrar_el = 0", u) { it.getLong(1) }.firstOrNull() }
            ?.takeIf { it != yo && !bloqueado(it, yo) && !bloqueado(yo, it) }?.let { avisar(it, yo, "historia_mencion", id.toString()) } }
        return Historia(id, datos, ahora(), true, mejores = mejores)
    }

    /** Las historias vigentes de [app]: las propias primero, después las de quienes sigue (las no vistas antes). */
    fun historias(yo: Long, app: String): List<GrupoHistorias> {
        AppsSocial.validar(app)
        val filas = b.usar { leer("""SELECT h.id, h.cuenta, h.datos, h.cuando, EXISTS(SELECT 1 FROM historia_vistas v WHERE v.historia = h.id AND v.cuenta = ?),
                EXISTS(SELECT 1 FROM historia_vistas v WHERE v.historia = h.id AND v.cuenta = ? AND v.megusta = 1), h.mejores
            FROM historias h WHERE h.app = ? AND h.expira > ? AND h.estado = 'visible' AND (h.oculta = 0 OR h.cuenta = ?)
              AND (h.cuenta = ? OR h.cuenta IN (SELECT a FROM seguir WHERE de = ? AND aceptada = 1))
              AND (h.mejores = 0 OR h.cuenta = ? OR EXISTS(SELECT 1 FROM mejores_amigos m WHERE m.cuenta = h.cuenta AND m.amigo = ?))
              AND (h.amigos = 0 OR h.cuenta = ? OR EXISTS(SELECT 1 FROM seguir t WHERE t.de = h.cuenta AND t.a = ? AND t.aceptada = 1))
            ORDER BY h.cuando LIMIT 600""", yo, yo, app, ahora(), yo, yo, yo, yo, yo, yo, yo) {
            arrayOf<Any>(it.getLong(1), it.getLong(2), it.getString(3), it.getLong(4), it.getInt(5) == 1, it.getInt(6) == 1, it.getInt(7) == 1)
        } }
        return filas.groupBy { it[1] as Long }.filterKeys { it == yo || (visible(it) && !bloqueado(yo, it) && !bloqueado(it, yo)) }
            .map { (cuenta, hs) -> cuenta to hs.map { Historia(it[0] as Long, JsonSocial.parseToJsonElement(it[2] as String).jsonObject, it[3] as Long, it[1] == yo || it[4] as Boolean, it[5] as Boolean, it[6] as Boolean) } }
            .let { l -> val silenciadas = silenciadasDe(yo); l.map { Triple(it.first, it.second, it.first in silenciadas) } }
            // Las silenciadas van al final (se pueden abrir igual), como en Instagram.
            .sortedWith(compareByDescending<Triple<Long, List<Historia>, Boolean>> { it.first == yo }.thenBy { it.third }.thenBy { p -> p.second.all { it.vista } }.thenByDescending { p -> p.second.maxOf { it.cuando } })
            .take(60).map { (cuenta, hs, silenciada) -> GrupoHistorias(persona(cuenta), hs.map { conVotos(it, yo, cuenta == yo) }, silenciada) }
    }

    /** Los votos de la encuesta de una historia (si tiene): para quien votó o para su autor. */
    private fun conVotos(h: Historia, yo: Long, propia: Boolean): Historia {
        val opciones = ((h.datos["encuesta"] as? JsonObject)?.get("opciones") as? JsonArray)?.size ?: return h
        val mio = b.usar { leer("SELECT opcion FROM historia_votos WHERE historia = ? AND cuenta = ?", h.id, yo) { it.getInt(1) }.firstOrNull() } ?: -1
        if (mio < 0 && !propia) return h
        val conteo = b.usar { leer("SELECT opcion, COUNT(*) FROM historia_votos WHERE historia = ? GROUP BY opcion", h.id) { it.getInt(1) to it.getInt(2) }.toMap() }
        return h.copy(votos = (0 until opciones).map { conteo[it] ?: 0 }, miVoto = mio)
    }

    /** Votar en la encuesta de una historia (una vez; se puede cambiar). Devuelve la historia con los votos. */
    fun votarHistoria(yo: Long, id: Long, v: Voto): Historia {
        verHistoria(yo, id)
        val (datos, cuando, autor) = b.usar { leer("SELECT datos, cuando, cuenta FROM historias WHERE id = ?", id) { Triple(JsonSocial.parseToJsonElement(it.getString(1)).jsonObject, it.getLong(2), it.getLong(3)) }.first() }
        val opciones = ((datos["encuesta"] as? JsonObject)?.get("opciones") as? JsonArray)?.size ?: throw Problema(400, "sin_encuesta")
        if (v.opcion !in 0 until opciones) throw Problema(400, "opcion")
        val nuevo = b.usar { leer("SELECT 1 FROM historia_votos WHERE historia = ? AND cuenta = ?", id, yo) { 1 }.isEmpty() }
        b.usar { cambiar("INSERT INTO historia_votos(historia, cuenta, opcion) VALUES(?,?,?) ON CONFLICT(historia, cuenta) DO UPDATE SET opcion = excluded.opcion", id, yo, v.opcion) }
        if (nuevo) avisar(autor, yo, "historia_voto", id.toString())
        return conVotos(Historia(id, datos, cuando, true), yo, autor == yo)
    }

    /** Responder la pregunta de una historia: solo la lee quien la subió (le llega un aviso). Hasta 3 por persona. */
    fun responderPregunta(yo: Long, id: Long, r: TextoRespuesta) {
        verHistoria(yo, id)
        val (datos, autor) = b.usar { leer("SELECT datos, cuenta FROM historias WHERE id = ?", id) { JsonSocial.parseToJsonElement(it.getString(1)).jsonObject to it.getLong(2) }.first() }
        if ((datos["pregunta"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()) throw Problema(400, "sin_pregunta")
        val texto = r.texto.filter { !it.isISOControl() }.trim().take(200)
        if (texto.isEmpty()) throw Problema(400, "texto")
        if (b.usar { leer("SELECT COUNT(*) FROM historia_respuestas WHERE historia = ? AND cuenta = ?", id, yo) { it.getInt(1) }.first() } >= 3) throw Problema(429, "despacio")
        b.usar { cambiar("INSERT INTO historia_respuestas(historia, cuenta, texto, cuando) VALUES(?,?,?,?)", id, yo, texto, ahora()) }
        avisar(autor, yo, "historia_respuesta", id.toString())
    }

    fun respuestasPregunta(yo: Long, id: Long): List<RespuestaHistoria> {
        if (b.usar { leer("SELECT 1 FROM historias WHERE id = ? AND cuenta = ?", id, yo) { 1 }.isEmpty() }) throw Problema(404, "no_existe")
        return b.usar { leer("SELECT cuenta, texto, cuando FROM historia_respuestas WHERE historia = ? ORDER BY cuando DESC LIMIT 500", id) { Triple(it.getLong(1), it.getString(2), it.getLong(3)) } }
            .filter { visible(it.first) && !bloqueado(yo, it.first) }.map { RespuestaHistoria(persona(it.first), it.second, it.third) }
    }

    /** Fijar (o soltar) una publicación propia arriba del perfil de su app; solo una fijada por app. */
    fun fijarPublicacion(yo: Long, id: Long, si: Boolean) = b.transaccion {
        val app = leer("SELECT app FROM publicaciones WHERE id = ? AND cuenta = ? AND estado = 'visible'", id, yo) { it.getString(1) }.firstOrNull() ?: throw Problema(404, "no_existe")
        cambiar("UPDATE publicaciones SET fijada = 0 WHERE cuenta = ? AND app = ?", yo, app)
        if (si) cambiar("UPDATE publicaciones SET fijada = 1 WHERE id = ?", id)
    }

    /** Guardar (o sacar, con título vacío) una historia propia en las destacadas del perfil: ahí no vence. */
    fun destacarHistoria(yo: Long, id: Long, d: Destacar) {
        val titulo = d.titulo.filter { !it.isISOControl() }.trim().take(30)
        if (b.usar { cambiar("UPDATE historias SET destacada = ? WHERE id = ? AND cuenta = ? AND estado = 'visible'", titulo, id, yo) } == 0) throw Problema(404, "no_existe")
    }

    /** Tu archivo: tus historias de los últimos 90 días en [app] (también las que ya vencieron), la más nueva primero. */
    fun archivoHistorias(yo: Long, app: String): List<Historia> = b.usar {
        leer("SELECT id, datos, cuando, destacada FROM historias WHERE cuenta = ? AND app = ? AND estado = 'visible' ORDER BY cuando DESC LIMIT 300", yo, AppsSocial.validar(app)) {
            Historia(it.getLong(1), JsonSocial.parseToJsonElement(it.getString(2)).jsonObject, it.getLong(3), true, destacada = it.getString(4))
        }
    }

    /** Elegir la historia que hace de portada de su grupo de destacadas. */
    fun portadaDestacada(yo: Long, id: Long) = b.transaccion {
        val titulo = leer("SELECT destacada FROM historias WHERE id = ? AND cuenta = ? AND destacada != ''", id, yo) { it.getString(1) }.firstOrNull() ?: throw Problema(404, "no_existe")
        cambiar("UPDATE historias SET portada = (id = ?) WHERE cuenta = ? AND destacada = ?", id, yo, titulo)
    }

    /** Las destacadas de un perfil en [app], por grupo; solo si quien mira puede ver ese perfil. */
    fun destacadas(lector: Long?, usuario: String, app: String): List<GrupoDestacadas> {
        AppsSocial.validar(app)
        val id = b.usar { leer("SELECT id FROM cuentas WHERE usuario = ? AND cerrada = 0 AND borrar_el = 0", usuario.lowercase()) { it.getLong(1) }.firstOrNull() } ?: return emptyList()
        if (lector != id && (lector != null && (bloqueado(id, lector) || bloqueado(lector, id)) || esPrivada(id) && (lector == null || !sigue(lector, id)))) return emptyList()
        return b.usar { leer("SELECT id, datos, cuando, destacada FROM historias WHERE cuenta = ? AND app = ? AND destacada != '' AND estado = 'visible' AND oculta = 0 AND mejores = 0 ORDER BY portada DESC, cuando", id, app) {
            Historia(it.getLong(1), JsonSocial.parseToJsonElement(it.getString(2)).jsonObject, it.getLong(3), true, destacada = it.getString(4))
        } }.groupBy { it.destacada }.map { (t, l) -> GrupoDestacadas(t, l) }
    }

    private fun silenciadasDe(yo: Long): Set<Long> = b.usar { leer("SELECT silenciada FROM historias_silenciadas WHERE cuenta = ?", yo) { it.getLong(1) }.toSet() }

    /** Silenciar (o volver a ver) las historias de alguien sin dejar de seguirlo. */
    fun silenciarHistorias(yo: Long, usuario: String, si: Boolean) {
        val otro = b.usar { leer("SELECT id FROM cuentas WHERE usuario = ?", usuario.lowercase()) { it.getLong(1) }.firstOrNull() } ?: throw Problema(404, "no_existe")
        b.usar { if (si) cambiar("INSERT OR IGNORE INTO historias_silenciadas(cuenta, silenciada) VALUES(?,?)", yo, otro) else cambiar("DELETE FROM historias_silenciadas WHERE cuenta = ? AND silenciada = ?", yo, otro) }
    }

    /** Tus amigos: quienes se siguen contigo mutuamente (los primeros para escribirles). */
    fun amigos(yo: Long): List<Persona> = b.usar {
        leer("""SELECT s.a FROM seguir s WHERE s.de = ? AND s.aceptada = 1 AND EXISTS(SELECT 1 FROM seguir t WHERE t.de = s.a AND t.a = ? AND t.aceptada = 1) LIMIT 500""", yo, yo) { it.getLong(1) }
    }.filter { visible(it) && !bloqueado(yo, it) && !bloqueado(it, yo) }.map(::persona).sortedBy { it.nombre.ifBlank { it.usuario }.lowercase() }

    /** Las chispas de la cuenta: la suma de sus movimientos (la bienvenida se da la primera vez). */
    fun chispas(yo: Long): Int = b.transaccion {
        if (leer("SELECT 1 FROM chispas WHERE cuenta = ? AND motivo = 'bienvenida'", yo) { 1 }.isEmpty())
            cambiar("INSERT INTO chispas(cuenta, cantidad, motivo, cuando) VALUES(?,?,?,?)", yo, TemasPerfil.BIENVENIDA, "bienvenida", ahora())
        leer("SELECT COALESCE(SUM(cantidad), 0) FROM chispas WHERE cuenta = ?", yo) { it.getInt(1) }.first()
    }
    private fun diaDe(t: Long) = t / (24 * 3600_000L)
    private fun diarioCobrado(yo: Long) = b.usar { leer("SELECT 1 FROM chispas WHERE cuenta = ? AND motivo = ?", yo, "diario:${diaDe(ahora())}") { 1 }.isNotEmpty() }

    fun temas(yo: Long): TemasCuenta {
        val saldo = chispas(yo)
        val (tema, marco) = b.usar { leer("SELECT tema, marco FROM cuentas WHERE id = ?", yo) { it.getString(1) to it.getString(2) }.first() }
        return TemasCuenta(b.usar { leer("SELECT tema FROM temas WHERE cuenta = ? ORDER BY cuando", yo) { it.getString(1) } }, tema, marco, saldo, !diarioCobrado(yo))
    }

    /** Las chispas del día (una vez por día). */
    fun cobrarDiario(yo: Long) {
        chispas(yo)
        b.usar { cambiar("INSERT OR IGNORE INTO chispas(cuenta, cantidad, motivo, cuando) SELECT ?, ?, ?, ? WHERE NOT EXISTS(SELECT 1 FROM chispas WHERE cuenta = ? AND motivo = ?)",
            yo, TemasPerfil.DIARIO, "diario:${diaDe(ahora())}", ahora(), yo, "diario:${diaDe(ahora())}") }
    }

    /** Comprar algo de la tienda con chispas (si alcanzan y no lo tiene ya). */
    fun reclamarTema(yo: Long, tema: String) {
        val precio = TemasPerfil.precio(tema) ?: throw Problema(404, "no_existe")
        chispas(yo)
        b.transaccion {
            if (leer("SELECT 1 FROM temas WHERE cuenta = ? AND tema = ?", yo, tema) { 1 }.isNotEmpty()) return@transaccion
            val saldo = leer("SELECT COALESCE(SUM(cantidad), 0) FROM chispas WHERE cuenta = ?", yo) { it.getInt(1) }.first()
            if (saldo < precio) throw Problema(402, "sin_chispas")
            cambiar("INSERT INTO chispas(cuenta, cantidad, motivo, cuando) VALUES(?,?,?,?)", yo, -precio, "compra:$tema", ahora())
            cambiar("INSERT INTO temas(cuenta, tema, cuando) VALUES(?,?,?)", yo, tema, ahora())
        }
    }

    /** Ponerse un fondo o un marco que se tiene ("" quita el fondo; "marco:" quita el marco). */
    fun ponerTema(yo: Long, tema: String) {
        val esMarco = tema.startsWith("marco:")
        if (tema.isNotEmpty() && tema != "marco:" && b.usar { leer("SELECT 1 FROM temas WHERE cuenta = ? AND tema = ?", yo, tema) { 1 }.isEmpty() }) throw Problema(403, "sin_tema")
        b.usar { cambiar(if (esMarco) "UPDATE cuentas SET marco = ? WHERE id = ?" else "UPDATE cuentas SET tema = ? WHERE id = ?", if (esMarco) tema.removePrefix("marco:") else tema, yo) }
    }

    /** Pone (o quita, con "") el cumpleaños: solo día y mes ("MM-DD"). */
    fun ponerCumple(yo: Long, c: Cumple) {
        val f = c.fecha.trim()
        if (f.isNotEmpty()) {
            if (!Regex("(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])").matches(f)) throw Problema(400, "fecha")
            runCatching { java.time.MonthDay.parse("--$f") }.getOrElse { throw Problema(400, "fecha") }
        }
        b.usar { cambiar("UPDATE cuentas SET cumple = ? WHERE id = ?", f, yo) }
    }
    fun miCumple(yo: Long): String = b.usar { leer("SELECT cumple FROM cuentas WHERE id = ?", yo) { it.getString(1) }.first() }

    /** Los cumpleaños de tus amigos (solo quienes lo pusieron): para avisarte el día. */
    fun cumplesAmigos(yo: Long): List<CumpleAmigo> {
        val ids = amigos(yo).map { it.usuario }.toSet()
        if (ids.isEmpty()) return emptyList()
        return b.usar { leer("SELECT usuario, cumple FROM cuentas WHERE cumple != '' AND usuario IN (${ids.joinToString(",") { "?" }})", *ids.toTypedArray()) { it.getString(1) to it.getString(2) } }
            .mapNotNull { (u, f) -> amigos(yo).firstOrNull { it.usuario == u }?.let { CumpleAmigo(it, f) } }
    }

    /** Tus mejores amigos (solo entre tus amigos); ellos ven tus historias «solo mejores amigos». */
    fun mejoresAmigos(yo: Long): List<String> = b.usar { leer("SELECT c.usuario FROM mejores_amigos m JOIN cuentas c ON c.id = m.amigo WHERE m.cuenta = ?", yo) { it.getString(1) } }

    fun ponerMejoresAmigos(yo: Long, l: ListaUsuarios) {
        val amigos = amigos(yo).map { it.usuario }.toSet()
        val elegidos = l.usuarios.map { it.lowercase() }.filter { it in amigos }.distinct().take(200)
        b.transaccion {
            cambiar("DELETE FROM mejores_amigos WHERE cuenta = ?", yo)
            elegidos.forEach { u -> cambiar("INSERT INTO mejores_amigos(cuenta, amigo) SELECT ?, id FROM cuentas WHERE usuario = ?", yo, u) }
        }
    }

    /* ----- Listas en común ----- */

    private fun esMiembro(yo: Long, lista: Long) = b.usar { leer("SELECT 1 FROM lista_miembros WHERE lista = ? AND cuenta = ?", lista, yo) { 1 }.isNotEmpty() }

    private fun listaComun(id: Long): ListaComun = b.usar {
        val (nombre, creador, cambio) = leer("SELECT nombre, creador, cambio FROM listas_comunes WHERE id = ?", id) { Triple(it.getString(1), it.getLong(2), it.getLong(3)) }.firstOrNull()
            ?: throw Problema(404, "no_existe")
        val miembros = leer("SELECT cuenta FROM lista_miembros WHERE lista = ? ORDER BY cuenta", id) { it.getLong(1) }
        val obras = leer("SELECT COUNT(*) FROM lista_items WHERE lista = ?", id) { it.getInt(1) }.first()
        ListaComun(id, nombre, persona(creador), miembros.map { persona(it) }, obras, cambio)
    }

    /** Las listas en común donde estás, las que cambiaron hace menos primero. */
    fun listasComunes(yo: Long): List<ListaComun> =
        b.usar { leer("SELECT l.id FROM listas_comunes l JOIN lista_miembros m ON m.lista = l.id WHERE m.cuenta = ? ORDER BY l.cambio DESC", yo) { it.getLong(1) } }.map { listaComun(it) }

    /** Arma una lista con amigos (solo amigos: quienes se siguen mutuamente; hasta 20 personas y 30 listas por cuenta). */
    fun crearListaComun(yo: Long, n: NuevaListaComun): ListaComun {
        val nombre = n.nombre.trim().take(60).ifBlank { throw Problema(400, "sin_nombre") }
        if (b.usar { leer("SELECT COUNT(*) FROM lista_miembros WHERE cuenta = ?", yo) { it.getInt(1) }.first() } >= 30) throw Problema(429, "muchas_listas")
        val amigos = amigos(yo).map { it.usuario }.toSet()
        val miembros = n.miembros.map { it.lowercase() }.filter { it in amigos }.distinct().take(19)
        val id = b.transaccion {
            cambiar("INSERT INTO listas_comunes(creador, nombre, creada, cambio) VALUES(?,?,?,?)", yo, nombre, ahora(), ahora())
            val id = leer("SELECT last_insert_rowid()") { it.getLong(1) }.first()
            cambiar("INSERT INTO lista_miembros VALUES(?,?)", id, yo)
            miembros.forEach { u -> cambiar("INSERT OR IGNORE INTO lista_miembros(lista, cuenta) SELECT ?, id FROM cuentas WHERE usuario = ?", id, u) }
            id
        }
        return listaComun(id)
    }

    fun detalleListaComun(yo: Long, id: Long): DetalleListaComun {
        if (!esMiembro(yo, id)) throw Problema(404, "no_existe")
        val obras = b.usar {
            leer("""SELECT i.id, i.cuenta, i.datos, i.creado, (SELECT COUNT(*) FROM lista_votos v WHERE v.item = i.id),
                    EXISTS(SELECT 1 FROM lista_votos v WHERE v.item = i.id AND v.cuenta = ?) FROM lista_items i WHERE i.lista = ? ORDER BY i.creado""", yo, id) {
                listOf(it.getLong(1), if (it.getObject(2) == null) null else it.getLong(2), it.getString(3), it.getLong(4), it.getInt(5), it.getInt(6) == 1)
            }
        }.map { r -> ObraListaComun(r[0] as Long, (r[1] as Long?)?.let { persona(it) }, JsonSocial.parseToJsonElement(r[2] as String) as JsonObject, r[4] as Int, r[5] as Boolean, r[3] as Long) }
        // Primero las más votadas: así se ve qué quieren todos.
        return DetalleListaComun(listaComun(id), obras.sortedWith(compareByDescending<ObraListaComun> { it.votos }.thenBy { it.cuando }))
    }

    /** Solo lo que hace falta para mostrar y abrir una obra (nada de texto libre largo). */
    private fun datosObraLista(d: JsonObject): JsonObject {
        fun t(k: String, max: Int) = (d[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content?.take(max).orEmpty()
        val titulo = t("titulo", 200).ifBlank { throw Problema(400, "sin_titulo") }
        return kotlinx.serialization.json.buildJsonObject {
            put("titulo", kotlinx.serialization.json.JsonPrimitive(titulo))
            put("tipoObra", kotlinx.serialization.json.JsonPrimitive(t("tipoObra", 20).ifBlank { "pelicula" }))
            t("portada", 500).takeIf { it.startsWith("https://") }?.let { put("portada", kotlinx.serialization.json.JsonPrimitive(it)) }
            t("fuente", 120).takeIf { it.isNotBlank() }?.let { put("fuente", kotlinx.serialization.json.JsonPrimitive(it)) }
            t("creador", 120).takeIf { it.isNotBlank() }?.let { put("creador", kotlinx.serialization.json.JsonPrimitive(it)) }
            (d["anio"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()?.let { put("anio", kotlinx.serialization.json.JsonPrimitive(it)) }
        }
    }

    /** Agrega una obra (una sola vez por lista; hasta 300). */
    fun agregarAListaComun(yo: Long, id: Long, datos: JsonObject) {
        if (!esMiembro(yo, id)) throw Problema(404, "no_existe")
        val d = datosObraLista(datos)
        val clave = (d["fuente"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: ("${(d["tipoObra"] as kotlinx.serialization.json.JsonPrimitive).content}|${(d["titulo"] as kotlinx.serialization.json.JsonPrimitive).content.lowercase().trim()}")
        b.transaccion {
            if (leer("SELECT COUNT(*) FROM lista_items WHERE lista = ?", id) { it.getInt(1) }.first() >= 300) throw Problema(413, "lista_llena")
            cambiar("INSERT OR IGNORE INTO lista_items(lista, cuenta, clave, datos, creado) VALUES(?,?,?,?,?)", id, yo, clave, d.toString(), ahora())
            cambiar("UPDATE listas_comunes SET cambio = ? WHERE id = ?", ahora(), id)
        }
    }

    /** Quitar una obra: quien la agregó o quien armó la lista. */
    fun quitarDeListaComun(yo: Long, id: Long, item: Long) {
        if (!esMiembro(yo, id)) throw Problema(404, "no_existe")
        b.transaccion {
            val (quien, creador) = leer("SELECT i.cuenta, l.creador FROM lista_items i JOIN listas_comunes l ON l.id = i.lista WHERE i.id = ? AND i.lista = ?", item, id) {
                (if (it.getObject(1) == null) null else it.getLong(1)) to it.getLong(2) }.firstOrNull() ?: throw Problema(404, "no_existe")
            if (quien != yo && creador != yo) throw Problema(403, "no_permitido")
            cambiar("DELETE FROM lista_items WHERE id = ?", item)
            cambiar("UPDATE listas_comunes SET cambio = ? WHERE id = ?", ahora(), id)
        }
    }

    fun votarListaComun(yo: Long, id: Long, item: Long, si: Boolean) {
        if (!esMiembro(yo, id)) throw Problema(404, "no_existe")
        b.usar {
            if (leer("SELECT 1 FROM lista_items WHERE id = ? AND lista = ?", item, id) { 1 }.isEmpty()) throw Problema(404, "no_existe")
            if (si) cambiar("INSERT OR IGNORE INTO lista_votos VALUES(?,?)", item, yo) else cambiar("DELETE FROM lista_votos WHERE item = ? AND cuenta = ?", item, yo)
        }
    }

    /** Sumar a un amigo (cualquiera de la lista puede; solo amigos de quien suma). */
    fun sumarAListaComun(yo: Long, id: Long, usuario: String) {
        if (!esMiembro(yo, id)) throw Problema(404, "no_existe")
        if (amigos(yo).none { it.usuario == usuario.lowercase() }) throw Problema(403, "solo_amigos")
        b.transaccion {
            if (leer("SELECT COUNT(*) FROM lista_miembros WHERE lista = ?", id) { it.getInt(1) }.first() >= 20) throw Problema(413, "lista_llena")
            cambiar("INSERT OR IGNORE INTO lista_miembros VALUES(?,?)", id, idDe(usuario))
        }
    }

    /** Salir de una lista; si quien sale la armó (o no queda nadie), la lista se borra para todos. */
    fun salirDeListaComun(yo: Long, id: Long) {
        if (!esMiembro(yo, id)) throw Problema(404, "no_existe")
        b.transaccion {
            val creador = leer("SELECT creador FROM listas_comunes WHERE id = ?", id) { it.getLong(1) }.first()
            cambiar("DELETE FROM lista_miembros WHERE lista = ? AND cuenta = ?", id, yo)
            if (creador == yo || leer("SELECT COUNT(*) FROM lista_miembros WHERE lista = ?", id) { it.getInt(1) }.first() == 0) cambiar("DELETE FROM listas_comunes WHERE id = ?", id)
        }
    }

    /** Las historias vigentes de una persona en [app] (para abrirlas desde su perfil). */
    fun historiasDe(yo: Long, usuario: String, app: String): GrupoHistorias? = historias(yo, app).firstOrNull { it.persona.usuario == usuario.lowercase() }

    fun verHistoria(yo: Long, id: Long) {
        val autor = b.usar { leer("SELECT cuenta FROM historias WHERE id = ? AND expira > ? AND estado = 'visible'", id, ahora()) { it.getLong(1) }.firstOrNull() } ?: throw Problema(404, "no_existe")
        if (autor == yo) return
        if (!sigue(yo, autor) || bloqueado(autor, yo)) throw Problema(404, "no_existe")
        b.usar { cambiar("INSERT OR IGNORE INTO historia_vistas(historia, cuenta, cuando) VALUES(?,?,?)", id, yo, ahora()) }
    }

    /** Quién vio una historia propia. */
    fun vistasHistoria(yo: Long, id: Long): List<VistaHistoria> {
        if (b.usar { leer("SELECT 1 FROM historias WHERE id = ? AND cuenta = ?", id, yo) { 1 }.isEmpty() }) throw Problema(404, "no_existe")
        return b.usar { leer("SELECT cuenta, cuando, megusta FROM historia_vistas WHERE historia = ? ORDER BY megusta DESC, cuando DESC LIMIT 500", id) { Triple(it.getLong(1), it.getLong(2), it.getInt(3) == 1) } }
            .filter { visible(it.first) }.map { VistaHistoria(persona(it.first), it.second, it.third) }
    }

    /** Me gusta en una historia (como en Instagram): le llega un aviso a quien la subió y lo ve en quién la vio. */
    fun meGustaHistoria(yo: Long, id: Long, si: Boolean) {
        verHistoria(yo, id)
        val autor = b.usar { leer("SELECT cuenta FROM historias WHERE id = ?", id) { it.getLong(1) }.first() }
        if (autor == yo) return
        val antes = b.usar { leer("SELECT megusta FROM historia_vistas WHERE historia = ? AND cuenta = ?", id, yo) { it.getInt(1) == 1 }.firstOrNull() } ?: false
        b.usar { cambiar("UPDATE historia_vistas SET megusta = ? WHERE historia = ? AND cuenta = ?", if (si) 1 else 0, id, yo) }
        if (si && !antes) avisar(autor, yo, "historia_megusta", id.toString())
    }

    fun borrarHistoria(yo: Long, id: Long) {
        val (estado, imagen) = b.usar { leer("SELECT estado, datos FROM historias WHERE id = ? AND cuenta = ?", id, yo) {
            it.getString(1) to ((JsonSocial.parseToJsonElement(it.getString(2)).jsonObject["imagen"] as? JsonPrimitive)?.contentOrNull)
        }.firstOrNull() } ?: throw Problema(404, "no_existe")
        // Lo retirado por moderación se conserva su plazo; la persona deja de verlo igual.
        if (estado != "visible") return
        b.usar { cambiar("DELETE FROM historias WHERE id = ?", id) }
        imagen?.let(::borrarImagenSuelta)
    }

    /** Borra una imagen que ya no usa ninguna publicación ni historia. */
    private fun borrarImagenSuelta(nombre: String) = b.usar {
        if (leer("SELECT 1 FROM publicaciones WHERE instr(datos, ?) > 0", nombre) { 1 }.isEmpty() &&
            leer("SELECT 1 FROM historias WHERE instr(datos, ?) > 0", nombre) { 1 }.isEmpty()) {
            java.io.File(dirImagenes, "$nombre.jpg").delete()
            cambiar("DELETE FROM imagenes WHERE nombre = ?", nombre)
        }
    }

    /** Las historias vencidas se borran a los dos días (un día de margen para reportes); las retiradas, al vencer su plazo. */
    fun limpiarHistorias() {
        // Vencidas, quedan 90 días en el archivo de quien las subió (solo esa persona las ve) y después se borran.
        val viejas = b.usar { leer("SELECT id, datos FROM historias WHERE (estado = 'visible' AND destacada = '' AND expira < ?) OR (estado = 'retirado' AND retirado_hasta <= ?)", ahora() - 90 * DIA, ahora()) {
            it.getLong(1) to ((JsonSocial.parseToJsonElement(it.getString(2)).jsonObject["imagen"] as? JsonPrimitive)?.contentOrNull)
        } }
        viejas.forEach { (id, imagen) -> b.usar { cambiar("DELETE FROM historias WHERE id = ?", id) }; imagen?.let(::borrarImagenSuelta) }
    }

    /* ----- Frases del saludo: la comunidad dice cuáles le gustan ----- */

    /** Le gusta (1), no le gusta (-1) o quita su voto (0) a una frase. Solo claves cortas de letras, números y _. */
    fun votarFrase(yo: Long, clave: String, v: VotoFrase) {
        if (!Regex("[a-z0-9_]{1,40}").matches(clave) || v.valor !in -1..1) throw Problema(400, "frase")
        b.usar {
            if (v.valor == 0) cambiar("DELETE FROM frases WHERE clave = ? AND cuenta = ?", clave, yo)
            else cambiar("INSERT INTO frases(clave, cuenta, valor, cuando) VALUES(?,?,?,?) ON CONFLICT(clave, cuenta) DO UPDATE SET valor = excluded.valor, cuando = excluded.cuando",
                clave, yo, v.valor, ahora())
        }
    }

    /** Propone una frase (hasta 3 al día por cuenta, de 6 a 90 letras, sin enlaces). */
    fun proponerFrase(yo: Long, f: FraseNueva, app: String = "cajon") {
        val t = f.texto.trim().replace(Regex("\\s+"), " ")
        if (t.codePointCount(0, t.length) !in 6..90 || Regex("(?i)https?://|www\\.|@\\w").containsMatchIn(t) || f.idioma !in setOf("es", "en")) throw Problema(400, "frase")
        b.usar {
            val hoy = leer("SELECT COUNT(*) FROM frases_propuestas WHERE cuenta = ? AND cuando > ?", yo, ahora() - 86_400_000L) { it.getInt(1) }.first()
            if (hoy >= 3) throw Problema(429, "despacio")
            cambiar("INSERT INTO frases_propuestas(cuenta, texto, idioma, cuando, app) VALUES(?,?,?,?,?)", yo, t, f.idioma, ahora(), AppsSocial.validar(app))
        }
    }

    /** Las frases aprobadas de un idioma. */
    fun frasesComunidad(idioma: String, app: String = "cajon"): List<FraseComunidad> = b.usar {
        leer("SELECT id, texto, idioma, franja, app FROM frases_propuestas WHERE estado = 'aprobada' AND idioma = ? AND app = ? ORDER BY id DESC LIMIT 300", idioma, AppsSocial.validar(app)) {
            FraseComunidad("c${it.getLong(1)}", it.getString(2), it.getString(3), franja = it.getString(4), app = it.getString(5))
        }
    }

    /** Las que esperan moderación (solo el equipo). */
    fun frasesPendientes(yo: Long, token: String? = null): List<FraseComunidad> {
        equipo.requiere(yo, "gestionar_frases", token)
        return b.usar {
            leer("SELECT f.id, f.texto, f.idioma, COALESCE(c.usuario, ''), f.app FROM frases_propuestas f LEFT JOIN cuentas c ON c.id = f.cuenta WHERE f.estado = 'pendiente' ORDER BY f.id LIMIT 100") {
                FraseComunidad("c${it.getLong(1)}", it.getString(2), it.getString(3), it.getString(4), app = it.getString(5))
            }
        }
    }

    private val FRANJAS = setOf("todo", "manana", "tarde", "noche", "madrugada")

    /** Las que el equipo quitó (las de la app por su clave; las de la gente quedan rechazadas). */
    fun frasesOcultas(): List<String> = b.usar { leer("SELECT clave FROM frases_ocultas") { it.getString(1) } }

    /** El equipo agrega una frase directamente (sale para todos). */
    fun agregarFrase(yo: Long, f: FraseNueva, token: String? = null): FraseComunidad = b.transaccion { agregarFraseInterno(yo, f, token) }

    private fun agregarFraseInterno(yo: Long, f: FraseNueva, token: String? = null): FraseComunidad {
        equipo.requiere(yo, "gestionar_frases", token)
        val t = f.texto.trim().replace(Regex("\\s+"), " ")
        if (t.codePointCount(0, t.length) !in 4..90 || f.idioma !in setOf("es", "en")) throw Problema(400, "frase")
        val franja = f.franja.takeIf { it in FRANJAS } ?: "todo"
        val app = AppsSocial.validar(f.app)
        val id = b.usar { insertar("INSERT INTO frases_propuestas(cuenta, texto, idioma, estado, cuando, franja, app) VALUES(?,?,?,'aprobada',?,?,?)", yo, t, f.idioma, ahora(), franja, app) }
        equipo.registrar(yo.toString(), "frase_agregar", "c$id", t, token)
        return FraseComunidad("c$id", t, f.idioma, franja = franja, app = app)
    }

    /** El equipo quita una frase: de la gente (queda rechazada) o de la app (por su clave, la app deja de mostrarla). */
    fun quitarFrase(yo: Long, clave: String, token: String? = null): Unit = b.transaccion { quitarFraseInterno(yo, clave, token) }

    private fun quitarFraseInterno(yo: Long, clave: String, token: String? = null) {
        equipo.requiere(yo, "gestionar_frases", token)
        equipo.registrar(yo.toString(), "frase_quitar", clave, "", token)
        if (!Regex("[a-z0-9_]{1,40}").matches(clave)) throw Problema(400, "frase")
        b.usar {
            clave.removePrefix("c").toLongOrNull()?.takeIf { clave.startsWith("c") }?.let { cambiar("UPDATE frases_propuestas SET estado = 'rechazada' WHERE id = ?", it) }
                ?: cambiar("INSERT OR IGNORE INTO frases_ocultas(clave) VALUES(?)", clave)
        }
    }

    fun decidirFrase(yo: Long, d: DecisionFrase, token: String? = null): Unit = b.transaccion { decidirFraseInterno(yo, d, token) }

    private fun decidirFraseInterno(yo: Long, d: DecisionFrase, token: String? = null) {
        equipo.requiere(yo, "gestionar_frases", token)
        equipo.registrar(yo.toString(), "frase_decidir", d.clave, if (d.aprobar) "aprobar" else "rechazar", token)
        val id = d.clave.removePrefix("c").toLongOrNull() ?: throw Problema(400, "frase")
        b.usar { cambiar("UPDATE frases_propuestas SET estado = ? WHERE id = ?", if (d.aprobar) "aprobada" else "rechazada", id) }
    }

    /** Cuántas personas dijeron que sí y que no a cada frase (solo los totales, sin quién). */
    fun frases(): Map<String, ConteoFrase> = b.usar {
        leer("SELECT clave, SUM(valor = 1), SUM(valor = -1) FROM frases GROUP BY clave LIMIT 500") { it.getString(1) to ConteoFrase(it.getInt(2), it.getInt(3)) }
    }.toMap()

    /* ----- Chat (cifrado de extremo a extremo: aquí solo pasan sobres cerrados) ----- */

    /** Las llaves privadas del chat, cifradas en el teléfono con la llave de la contraseña ("" si no hay). */
    fun llavesChat(yo: Long): CajonCifrado = CajonCifrado(llave = b.usar { leer("SELECT sobre FROM llaves_chat WHERE cuenta = ?", yo) { it.getString(1) }.firstOrNull() }.orEmpty())

    fun ponerLlavesChat(yo: Long, c: CajonCifrado) {
        if (c.llave.isBlank() || c.llave.length > 20_000) throw Problema(400, "llaves")
        b.usar { cambiar("INSERT INTO llaves_chat(cuenta, sobre, cuando) VALUES(?,?,?) ON CONFLICT(cuenta) DO UPDATE SET sobre = excluded.sobre, cuando = excluded.cuando", yo, c.llave, ahora()) }
    }

    fun ponerLlaves(yo: Long, l: Llaves) {
        if (l.cifrar.length !in 20..4000 || l.firmar.length !in 20..4000) throw Problema(400, "llaves")
        b.usar { cambiar("INSERT INTO llaves(cuenta, cifrar, firmar, cuando) VALUES(?,?,?,?) ON CONFLICT(cuenta) DO UPDATE SET cifrar = excluded.cifrar, firmar = excluded.firmar, cuando = excluded.cuando",
            yo, l.cifrar, l.firmar, ahora()) }
    }

    fun llaves(lector: Long?, usuario: String): Llaves {
        val id = idDe(usuario)
        if (lector != null && (bloqueado(id, lector) || bloqueado(lector, id))) throw Problema(404, "no_existe")
        return b.usar { leer("SELECT cifrar, firmar FROM llaves WHERE cuenta = ?", id) { Llaves(it.getString(1), it.getString(2)) }.firstOrNull() } ?: throw Problema(404, "sin_llaves")
    }

    /** Deja un sobre para [usuario] (si no hay bloqueo); 300 por hora como mucho. */
    fun mandar(yo: Long, usuario: String, s: SobreNuevo): Long {
        val otro = idDe(usuario)
        if (otro == yo) throw Problema(400, "eres_tu")
        if (bloqueado(otro, yo) || bloqueado(yo, otro)) throw Problema(404, "no_existe")
        if (s.sobre.length > 90_000 || s.firma.length > 2000 || s.sobre.isBlank()) throw Problema(413, "muy_grande")
        val n = b.usar { leer("SELECT COUNT(*) FROM mensajes WHERE de = ? AND cuando > ?", yo, ahora() - 3600_000L) { it.getInt(1) }.first() }
        if (n >= 300) throw Problema(429, "despacio")
        // A quien no te sigue, hasta 20 por hora (frena el acoso y el spam a desconocidos).
        if (!sigue(otro, yo) && b.usar { leer("SELECT COUNT(*) FROM mensajes WHERE de = ? AND para = ? AND cuando > ? AND silencioso = 0", yo, otro, ahora() - 3600_000L) { it.getInt(1) }.first() } >= 20)
            throw Problema(429, "despacio")
        val id = b.usar { insertar("INSERT INTO mensajes(de, para, cuando, sobre, firma, silencioso) VALUES(?,?,?,?,?,?)", yo, otro, ahora(), s.sobre, s.firma, if (s.silencioso) 1 else 0) }
        timbre(otro).value = id   // quien espera (la app abierta) se entera al instante
        if (!s.silencioso) empujes.tocar(otro)       // y con la app cerrada, su teléfono recibe un toque (FCM)
        return id
    }

    /** Un timbre por cuenta: suena cuando le llega un mensaje (lo escucha [esperarMensajes]). */
    private val timbres = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.flow.MutableStateFlow<Long>>()
    private fun timbre(cuenta: Long) = timbres.getOrPut(cuenta) { kotlinx.coroutines.flow.MutableStateFlow(0L) }

    /**
     * Como [recibir], pero si no hay nada nuevo espera hasta [espera] milisegundos a que llegue algo (la app abierta
     * deja esta pregunta hecha y el mensaje le llega al instante, sin servicios de terceros).
     */
    suspend fun esperarMensajes(yo: Long, desde: Long, espera: Long = 25_000, sesion: String = ""): List<Sobre> {
        val antes = timbre(yo).value   // se mira antes de revisar: lo que llegue después cambia el número
        val ya = recibir(yo, desde, sesion)
        if (ya.isNotEmpty()) return ya
        kotlinx.coroutines.withTimeoutOrNull(espera) { timbre(yo).first { it != antes } }
        return recibir(yo, desde, sesion)
    }

    /**
     * Los sobres que le llegaron después de [desde]. Cada sesión ([sesion], la huella del token: una app en un
     * teléfono) recuerda hasta dónde recibió; un sobre se borra cuando todas las sesiones que usan el chat lo
     * recibieron (así llega a las cuatro apps) o a los 30 días.
     */
    fun recibir(yo: Long, desde: Long, sesion: String = ""): List<Sobre> {
        b.usar {
            if (sesion.isNotEmpty()) cambiar("""INSERT INTO chat_cursores(huella, cuenta, ultimo, cuando) VALUES(?,?,?,?)
                ON CONFLICT(huella) DO UPDATE SET ultimo = max(ultimo, excluded.ultimo), cuando = excluded.cuando""", sesion, yo, desde, ahora())
            cambiar("DELETE FROM chat_cursores WHERE cuando < ? OR huella NOT IN (SELECT huella FROM sesiones)", ahora() - 30L * 24 * 3600_000)
            val minimo = leer("SELECT min(ultimo) FROM chat_cursores WHERE cuenta = ?", yo) { it.getObject(1) as? Number }.firstOrNull()?.toLong() ?: desde
            cambiar("DELETE FROM mensajes WHERE para = ? AND id <= ?", yo, minOf(minimo, desde))
            cambiar("DELETE FROM mensajes WHERE cuando < ?", ahora() - 30L * 24 * 3600_000)
        }
        return b.usar { leer("SELECT id, de, cuando, sobre, firma FROM mensajes WHERE para = ? AND id > ? ORDER BY id LIMIT 200", yo, desde) {
            arrayOf(it.getLong(1), it.getLong(2), it.getLong(3), it.getString(4), it.getString(5)) } }
            .filter { !bloqueado(yo, it[1] as Long) }
            .filter { visible(it[1] as Long) }.map { Sobre(it[0] as Long, persona(it[1] as Long), it[2] as Long, it[3] as String, it[4] as String) }
    }

    /* ----- Avisos ----- */

    private fun avisar(para: Long, de: Long, tipo: String, objeto: String = "") {
        if (para == de) return
        val app = b.usar {
            val app = if (tipo in setOf("publico", "megusta", "comentario", "respuesta")) leer("SELECT app FROM publicaciones WHERE id = ?", objeto.toLongOrNull()) { it.getString(1) }.firstOrNull() else null
            cambiar("INSERT INTO avisos(para, de, tipo, objeto, cuando, app) VALUES(?,?,?,?,?,?)", para, de, tipo, objeto, ahora(), app)
            app
        }
        empujes.tocar(para, app)
    }

    fun avisos(yo: Long, antes: Long?, app: String = "cajon"): List<Aviso> = b.usar {
        leer("SELECT id, de, tipo, objeto, cuando, leido, app FROM avisos WHERE para = ? AND (app IS NULL OR app = ?) AND cuando < ? ORDER BY cuando DESC LIMIT 60", yo, AppsSocial.validar(app), antes ?: Long.MAX_VALUE) {
            arrayOf(it.getLong(1), it.getLong(2), it.getString(3), it.getString(4), it.getLong(5), it.getInt(6) == 1, it.getString(7))
        }
    }.filter { it[2] == "sancion" || (!bloqueado(yo, it[1] as Long) && visible(it[1] as Long)) }
        .map { Aviso(it[0] as Long, it[2] as String, if (it[2] == "sancion") personaFogon() else persona(it[1] as Long), it[3] as String, it[4] as Long, it[5] as Boolean, it[6] as String?) }

    /**
     * Las sanciones llegan a nombre de Fogón (la cuenta principal), nunca de quien moderó: así nadie del equipo
     * queda expuesto a represalias. La tabla guarda quién decidió para el registro del equipo.
     */
    private fun personaFogon(): Persona =
        b.usar { leer("SELECT cuenta FROM roles WHERE rol = 'principal' LIMIT 1") { it.getLong(1) }.firstOrNull() }?.let(::persona)
            ?: Persona("fogon", "Fogón", false)

    fun sinLeer(yo: Long, app: String = "cajon"): Int = b.usar { leer("SELECT COUNT(*) FROM avisos WHERE de IN (SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0) AND para = ? AND (app IS NULL OR app = ?) AND leido = 0", yo, AppsSocial.validar(app)) { it.getInt(1) }.first() }

    fun leidos(yo: Long, app: String = "cajon") { b.usar { cambiar("UPDATE avisos SET leido = 1 WHERE para = ? AND (app IS NULL OR app = ?)", yo, AppsSocial.validar(app)) } }

    /** Borra un aviso ([id]) o todos (null). */
    fun borrarAvisos(yo: Long, id: Long?, app: String = "cajon") {
        b.usar { if (id == null) cambiar("DELETE FROM avisos WHERE para = ? AND (app IS NULL OR app = ?)", yo, AppsSocial.validar(app)) else cambiar("DELETE FROM avisos WHERE para = ? AND id = ? AND (app IS NULL OR app = ?)", yo, id, AppsSocial.validar(app)) }
    }

    /* ----- Reportes y moderación ----- */

    /**
     * Un reporte. [NuevoReporte.motivo] empieza por uno de [Moderacion.MOTIVOS] ("acoso: …"). Si lo reportan
     * [Moderacion.UMBRAL] cuentas distintas que cuentan (más de 7 días y sin sanción), o una sola por un menor en riesgo,
     * se oculta mientras se revisa (su autor lo sigue viendo).
     */
    fun reportar(yo: Long?, r: NuevoReporte) {
        if (r.que !in setOf("cuenta", "publicacion", "comentario", "mensaje", "imagen", "lugar", "historia")) throw Problema(400, "que")
        if (r.que == "lugar" && verLugar(r.objeto.toLongOrNull() ?: throw Problema(400, "objeto")) == null) throw Problema(404, "no_existe")
        if (r.que == "imagen" && !urlImagenValida(r.objeto)) throw Problema(400, "url")
        if (yo != null && b.usar { leer("SELECT COUNT(*) FROM reportes WHERE de = ? AND cuando > ?", yo, ahora() - 24 * 3600_000L) { it.getInt(1) }.first() } >= 10)
            throw Problema(429, "despacio")
        val motivo = r.motivo.trim().take(2000)
        val objeto = r.objeto.take(if (r.que == "imagen") 300 else 80)
        b.usar { insertar("INSERT INTO reportes(de, que, objeto, motivo, cuando) VALUES(?,?,?,?,?)", yo, r.que, objeto, motivo, ahora()) }
        val grave = motivo.startsWith(Moderacion.MENOR_EN_RIESGO)
        val cuentan = b.usar {
            leer("""SELECT COUNT(DISTINCT r.de) FROM reportes r JOIN cuentas c ON c.id = r.de
                    WHERE r.que = ? AND r.objeto = ? AND r.estado = 'abierto' AND c.creada < ? AND c.restringida_hasta < ? AND c.suspendida_hasta < ?""",
                r.que, objeto, ahora() - 7L * 24 * 3600_000, ahora(), ahora()) { it.getInt(1) }.first()
        }
        if (grave || cuentan >= Moderacion.UMBRAL) ocultarReportado(r.que, objeto, true)
    }

    private fun ocultarReportado(que: String, objeto: String, si: Boolean) {
        if (que == "imagen") {
            // Una imagen muy reportada deja de ser la predeterminada mientras el equipo la revisa.
            b.usar {
                if (si) cambiar("INSERT OR IGNORE INTO imagenes_bloqueadas(url, motivo, cuando) VALUES(?,?,?)", objeto, "reportada", ahora())
                else cambiar("DELETE FROM imagenes_bloqueadas WHERE url = ? AND motivo = 'reportada'", objeto)
            }
            return
        }
        val id = objeto.toLongOrNull() ?: return
        b.usar {
            when (que) {
                "lugar" -> cambiar("UPDATE lugares SET retirado = ? WHERE id = ?", if (si) 1 else 0, id)
                "publicacion" -> cambiar("UPDATE publicaciones SET oculta = ? WHERE id = ?", if (si) 1 else 0, id)
                "comentario" -> cambiar("UPDATE comentarios SET oculto = ? WHERE id = ?", if (si) 1 else 0, id)
                "historia" -> cambiar("UPDATE historias SET oculta = ? WHERE id = ?", if (si) 1 else 0, id)
            }
        }
    }


    /** La cola de revisión (equipo autorizado): lo de menores primero, después lo que más gente reportó. */
    fun casos(yo: Long, token: String? = null): List<Caso> = b.transaccion { casosInterno(yo, token) }

    private fun casosInterno(yo: Long, token: String? = null): List<Caso> {
        equipo.requiere(yo, "ver_reportes", token)
        val filas = b.usar { leer("SELECT que, objeto, motivo, de FROM reportes r WHERE estado = 'abierto' ORDER BY cuando", ) { arrayOf(it.getString(1), it.getString(2), it.getString(3), it.getLong(4)) } }
        return filas.groupBy { it[0] as String to it[1] as String }.map { (k, rs) ->
            val (que, objeto) = k
            val motivos = rs.groupingBy { (it[2] as String).substringBefore(':').trim().takeIf { m -> m in Moderacion.MOTIVOS } ?: "otro" }.eachCount()
            val (contenido, autor, oculto) = verReportado(que, objeto, rs.map { it[2] as String })
            if (autor != null && equipo.rol(autor) == "principal" && autor != yo) return@map null
            if (autor != null && equipo.rol(autor) == "principal") equipo.registrar(yo.toString(), "principal_reporte_abrir", objeto, "", token)
            if (que == "mensaje") equipo.registrar(yo.toString(), "mensaje_abrir", objeto, "", token)
            val tabla = when (que) { "publicacion" -> "publicaciones"; "comentario" -> "comentarios"; "historia" -> "historias"; else -> null }
            val estadoContenido = tabla?.let { b.usar {
                leer("SELECT estado, retirado_hasta FROM $tabla WHERE id = ?", objeto.toLongOrNull()) {
                    it.getString(1) to it.getLong(2)
                }.firstOrNull()
            } }
            val estado = estadoContenido?.first ?: "visible"
            Caso(que, objeto, motivos, rs.map { it[3] }.distinct().size, oculto, contenido, autor?.let(::persona), autor?.let(::puntosVigentes) ?: 0, estado, estadoContenido?.second?.takeIf { estado == "retirado" })
        }.filterNotNull().sortedWith(compareByDescending<Caso> { Moderacion.MENOR_EN_RIESGO in it.motivos }.thenByDescending { it.personas })
    }

    private fun verReportado(que: String, objeto: String, motivos: List<String>): Triple<String, Long?, Boolean> = b.usar {
        val id = objeto.toLongOrNull()
        when (que) {
            "publicacion" -> leer("SELECT datos, cuenta, oculta FROM publicaciones WHERE id = ? AND estado != 'preservado'", id) { Triple(it.getString(1), it.getLong(2), it.getInt(3) == 1) }.firstOrNull()
            "historia" -> leer("SELECT datos, cuenta, oculta FROM historias WHERE id = ? AND estado != 'preservado'", id) { Triple(it.getString(1), it.getLong(2), it.getInt(3) == 1) }.firstOrNull()
            "comentario" -> leer("SELECT texto, cuenta, oculto FROM comentarios WHERE id = ? AND estado != 'preservado'", id) { Triple(it.getString(1), it.getLong(2), it.getInt(3) == 1) }.firstOrNull()
            "cuenta" -> leer("SELECT perfil, id FROM cuentas WHERE usuario = ?", objeto) { Triple(it.getString(1), it.getLong(2), false) }.firstOrNull()
            "lugar" -> leer("SELECT nombre || ' · ' || ciudad, cuenta, retirado FROM lugares WHERE id = ?", id) { Triple(it.getString(1), (it.getObject(2) as? Number)?.toLong(), it.getInt(3) == 1) }.firstOrNull()
            "imagen" -> Triple(objeto, null, leer("SELECT 1 FROM imagenes_bloqueadas WHERE url = ?", objeto) { 1 }.isNotEmpty())
            // Un mensaje va cifrado: lo único que se ve es lo que mandó quien lo reportó (con su permiso).
            else -> Triple(motivos.joinToString("\n---\n"), leer("SELECT id FROM cuentas WHERE usuario = ?", objeto.substringBefore(':')) { it.getLong(1) }.firstOrNull(), false)
        } ?: Triple("(ya no está)", null, false)
    }

    /**
     * Lo que decide quien modera: "sin_falta" (vuelve a verse), "advertencia" (se quita, sin puntos), "falta" (se quita
     * y suma 1), "falta_grave" (suma 2) o "cerrar" (se quita y se cierra la cuenta). A la persona le llega un aviso y un
     * correo con la regla, el mensaje del equipo y cómo apelar.
     */
    fun decidir(yo: Long, d: Decision, token: String? = null): Unit = b.transaccion { decidirInterno(yo, d, token) }

    private fun decidirInterno(yo: Long, d: Decision, token: String? = null) {
        equipo.requiere(yo, "sancionar", token)
        if (d.que !in setOf("cuenta", "publicacion", "comentario", "mensaje", "lugar", "historia")) throw Problema(400, "que")
        if (d.decision != "sin_falta" && d.decision !in Moderacion.DECISIONES) throw Problema(400, "decision")
        if (d.decision == "advertencia" && d.regla in Moderacion.CIERRE_DIRECTO) throw Problema(400, "decision")
        val (_, autor, _) = verReportado(d.que, d.objeto, emptyList())
        if (autor != null) equipo.inferior(yo, autor)
        if (b.usar { leer("SELECT 1 FROM reportes WHERE que = ? AND objeto = ? AND de = ?", d.que, d.objeto, yo) { 1 }.isNotEmpty() }) throw Problema(403, "reporte_propio")
        val consumidos = b.usar {
            cambiar("UPDATE reportes SET estado = ? WHERE que = ? AND objeto = ? AND estado = 'abierto'", if (d.decision == "sin_falta") "sin_falta" else "confirmado", d.que, d.objeto)
        }
        if (consumidos == 0) throw Problema(409, "ya_resuelto")
        if (d.decision == "falta_grave") equipo.requiere(yo, "sancion_grave", token)
        if (d.decision !in setOf("sin_falta", "advertencia") && (d.decision == "cerrar" || d.regla in Moderacion.CIERRE_DIRECTO || (autor != null && puntosVigentes(autor) + (if (d.decision == "falta_grave") 2 else 1) >= 4))) equipo.requiere(yo, "cerrar_cuentas", token)
        equipo.registrar(yo.toString(), "reporte_decidir", d.que + ":" + d.objeto, d.decision + ":" + d.regla, token)

        b.usar {
            cambiar("INSERT INTO decisiones(admin, que, objeto, decision, regla, cuando) VALUES(?,?,?,?,?,?)", yo, d.que, d.objeto, d.decision, d.regla.take(100), ahora())
        }
        if (d.decision == "sin_falta") { ocultarReportado(d.que, d.objeto, false); return }
        equipo.registrar(yo.toString(), "contenido_retirar", d.que + ":" + d.objeto, d.regla, token)
        // Se quita lo reportado. Lo que pone a un menor en riesgo se conserva oculto un año para las autoridades
        // (el plazo de referencia de la REPORT Act de 2024); lo demás, 90 días.
        val id = d.objeto.toLongOrNull()
        val guardar = if (d.regla == Moderacion.MENOR_EN_RIESGO) Moderacion.CONSERVAR_GRAVE else 90 * DIA
        b.usar {
            val retirados = when (d.que) {
                "lugar" -> cambiar("UPDATE lugares SET retirado = 1 WHERE id = ?", id)
                "publicacion" -> cambiar("UPDATE publicaciones SET estado = 'retirado', retirado_hasta = ? WHERE id = ? AND estado = 'visible'", ahora() + guardar, id)
                "comentario" -> cambiar("UPDATE comentarios SET estado = 'retirado', retirado_hasta = ? WHERE id = ? AND estado = 'visible'", ahora() + guardar, id)
                "historia" -> cambiar("UPDATE historias SET estado = 'retirado', retirado_hasta = ? WHERE id = ? AND estado = 'visible'", ahora() + guardar, id)
                else -> 1
            }
            if (retirados == 0) throw Problema(409, "ya_resuelto")
            if (d.que == "publicacion") marcarRetirada(id)
        }
        autor ?: return
        darFalta(autor, yo, d.decision, d.regla, d.mensaje, "contenido", token)
    }

    /** Suma la falta (una advertencia vale 0), aplica la sanción que toque y le avisa a la persona en la app y por correo. */
    private fun darFalta(autor: Long, yo: Long, decision: String, reglaDada: String, mensajeDado: String, origen: String, token: String?) {
        val regla = reglaDada.ifBlank { "otro" }.take(100)
        val mensaje = Moderacion.mensajeLimpio(mensajeDado)
        val puntos = when { decision == "cerrar" || regla in Moderacion.CIERRE_DIRECTO -> 99; decision == "falta_grave" -> 2; decision == "advertencia" -> 0; else -> 1 }
        b.usar { cambiar("INSERT INTO faltas(cuenta, puntos, regla, cuando, vence, mensaje, decision, admin) VALUES(?,?,?,?,?,?,?,?)",
            autor, puntos, regla, ahora(), ahora() + Moderacion.VENCE, mensaje, decision, yo) }
        aplicarSancion(autor, yo, regla, mensaje, decision, origen)
        equipo.registrar(yo.toString(), if (decision == "advertencia") "sancion_advertir" else "sancion_dar", autor.toString(), "$decision:$regla", token)
    }

    /** Sancionar una cuenta sin reporte de por medio (por ejemplo, lo que se ve en su perfil). Pide un mensaje. */
    fun sancionarCuenta(yo: Long, d: SancionDirecta, token: String? = null): Unit = b.transaccion {
        equipo.requiere(yo, "sancionar", token)
        if (d.decision !in Moderacion.DECISIONES || d.regla !in Moderacion.REGLAS) throw Problema(400, "decision")
        if (d.decision == "advertencia" && d.regla in Moderacion.CIERRE_DIRECTO) throw Problema(400, "decision")
        if (Moderacion.mensajeLimpio(d.mensaje).isBlank()) throw Problema(400, "mensaje")
        val destino = leer("SELECT id FROM cuentas WHERE usuario = ?", d.usuario.lowercase()) { it.getLong(1) }.singleOrNull() ?: throw Problema(404, "no_existe")
        equipo.inferior(yo, destino)
        if (d.decision == "falta_grave") equipo.requiere(yo, "sancion_grave", token)
        if (d.decision != "advertencia" && (d.decision == "cerrar" || d.regla in Moderacion.CIERRE_DIRECTO || puntosVigentes(destino) + (if (d.decision == "falta_grave") 2 else 1) >= 4)) equipo.requiere(yo, "cerrar_cuentas", token)
        darFalta(destino, yo, d.decision, d.regla, d.mensaje, "cuenta", token)
    }

    /** Las sanciones de los últimos 90 días (equipo), la más nueva primero: para revisarlas o levantarlas. */
    fun sancionesRecientes(yo: Long, token: String? = null): List<SancionReciente> {
        equipo.requiere(yo, "sancionar", token)
        return b.usar { leer("""SELECT c.usuario, c.nombre, f.regla, f.mensaje, f.decision, f.cuando, c.restringida_hasta, c.suspendida_hasta, c.cerrada,
                (SELECT coalesce(sum(g.puntos), 0) FROM faltas g WHERE g.cuenta = c.id AND g.vence > ?)
            FROM faltas f JOIN cuentas c ON c.id = f.cuenta WHERE f.cuando > ? ORDER BY f.cuando DESC LIMIT 200""", ahora(), ahora() - Moderacion.VENCE) {
            SancionReciente(it.getString(1), it.getString(2).orEmpty(), it.getString(3), it.getString(4), it.getString(5).ifBlank { "falta" },
                it.getLong(6), it.getLong(7), it.getLong(8), it.getInt(9) == 1, it.getInt(10))
        } }
    }

    /* ----- Apelaciones ----- */

    private fun apelacion(cuenta: Long): Apelacion? = b.usar {
        leer("SELECT a.id, c.usuario, a.texto, a.cuando, a.estado, a.respuesta, c.suspendida_hasta, c.cerrada FROM apelaciones a JOIN cuentas c ON c.id = a.cuenta WHERE a.cuenta = ? ORDER BY a.cuando DESC LIMIT 1", cuenta) {
            Apelacion(it.getLong(1), it.getString(2), it.getString(3), it.getLong(4), it.getString(5), it.getString(6), suspendidaHasta = it.getLong(7), cerrada = it.getInt(8) == 1)
        }.firstOrNull()
    }

    /**
     * La persona sancionada pide que se revise su última sanción (dentro de 30 días). Una apelación por sanción:
     * si ya apeló después de la última falta, no puede volver a hacerlo.
     */
    fun apelar(cuenta: Long, textoDado: String): Apelacion = b.transaccion {
        val texto = textoDado.filter { it == '\n' || !it.isISOControl() }.trim().take(1000)
        if (texto.length < 10) throw Problema(400, "texto")
        val ultima = leer("SELECT max(cuando) FROM faltas WHERE cuenta = ?", cuenta) { it.getLong(1) }.firstOrNull() ?: 0
        if (ultima == 0L || ultima < ahora() - Moderacion.PLAZO_APELAR) throw Problema(400, "sin_sancion")
        if (leer("SELECT 1 FROM apelaciones WHERE cuenta = ? AND cuando >= ?", cuenta, ultima) { 1 }.isNotEmpty()) throw Problema(409, "ya_apelaste")
        cambiar("INSERT INTO apelaciones(cuenta, texto, cuando) VALUES(?,?,?)", cuenta, texto, ahora())
        apelacion(cuenta)!!
    }

    /** Con la cuenta cerrada no hay sesión: se apela con correo y contraseña. */
    fun apelarConClave(n: NuevaApelacion): Apelacion = apelar(cuentaPorClave(n.correo.trim().lowercase(), n.clave).first, n.texto)

    /** El estado de la sanción para la pantalla que ve quien entra con una cuenta cerrada (correo y contraseña). */
    fun estadoSancion(a: AccesoSancion): EstadoSancion {
        val (id, _) = cuentaPorClave(a.correo.trim().lowercase(), a.clave)
        val estado = estadoCuenta(id)
        return EstadoSancion(estado.copy(admin = false), estado.apelacion)
    }

    /** Las apelaciones abiertas (equipo), la más vieja primero, con las faltas de cada cuenta. */
    fun apelaciones(yo: Long, token: String? = null): List<Apelacion> {
        equipo.requiere(yo, "sancionar", token)
        val filas = b.usar { leer("SELECT a.id, a.cuenta, c.usuario, a.texto, a.cuando, c.suspendida_hasta, c.cerrada FROM apelaciones a JOIN cuentas c ON c.id = a.cuenta WHERE a.estado = 'abierta' ORDER BY a.cuando LIMIT 100") {
            arrayOf<Any>(it.getLong(1), it.getLong(2), it.getString(3), it.getString(4), it.getLong(5), it.getLong(6), it.getInt(7) == 1)
        } }
        return filas.map { f -> Apelacion(f[0] as Long, f[2] as String, f[3] as String, f[4] as Long, "abierta", "", faltasDe(f[1] as Long, true), f[5] as Long, f[6] as Boolean) }
    }

    /**
     * El equipo acepta (se borra la última sanción: su falta, la suspensión o el cierre) o rechaza una apelación, con
     * una respuesta que le llega a la persona por correo.
     */
    fun decidirApelacion(yo: Long, d: DecisionApelacion, token: String? = null): Unit = b.transaccion {
        equipo.requiere(yo, if (d.aceptar) "gestionar_usuarios" else "sancionar", token)
        val respuesta = Moderacion.mensajeLimpio(d.respuesta)
        if (respuesta.isBlank()) throw Problema(400, "respuesta")
        val cuenta = leer("SELECT cuenta FROM apelaciones WHERE id = ? AND estado = 'abierta'", d.id) { it.getLong(1) }.singleOrNull() ?: throw Problema(409, "ya_resuelto")
        equipo.inferior(yo, cuenta)
        if (d.aceptar) {
            cambiar("DELETE FROM faltas WHERE id = (SELECT id FROM faltas WHERE cuenta = ? ORDER BY cuando DESC, id DESC LIMIT 1)", cuenta)
            cambiar("UPDATE cuentas SET restringida_hasta = 0, suspendida_hasta = 0, cerrada = 0 WHERE id = ?", cuenta)
        }
        cambiar("UPDATE apelaciones SET estado = ?, respuesta = ?, decidida_por = ?, decidida_cuando = ? WHERE id = ?",
            if (d.aceptar) "aceptada" else "rechazada", respuesta, yo, ahora(), d.id)
        equipo.registrar(yo.toString(), if (d.aceptar) "apelacion_aceptar" else "apelacion_rechazar", cuenta.toString(), respuesta, token)
        avisar(cuenta, if (d.aceptar) "Aceptamos tu apelación en Fogón Social" else "Revisamos tu apelación en Fogón Social",
            (if (d.aceptar) "Revisamos tu apelación y teníamos un error: quitamos la sanción y la falta. Ya puedes usar la red como siempre."
            else "Revisamos tu apelación y la sanción se mantiene.") + "\n\nRespuesta del equipo: $respuesta\n\nNormas: ${Moderacion.NORMAS}\n\n— Fogón")
    }

    private fun marcarRetirada(id: Long?) = b.usar {
        cambiar("INSERT OR IGNORE INTO retiradas(cuenta, app, clave, cuando) SELECT cuenta, app, clave, ? FROM publicaciones WHERE id = ? AND estado = 'retirado'", ahora(), id)
    }

    fun retirar(yo: Long, d: ContenidoEquipo, token: String?) = b.transaccion {
        if (d.que == "lugar") {
            equipo.requiere(yo, "gestionar_publicaciones", token)
            val autor = leer("SELECT cuenta FROM lugares WHERE id = ?", d.objeto.toLongOrNull()) { (it.getObject(1) as? Number)?.toLong() }.singleOrNull()
            autor?.let { equipo.inferior(yo, it) }
            if (cambiar("UPDATE lugares SET retirado = 1 WHERE id = ?", d.objeto.toLongOrNull()) == 0) throw Problema(404, "no_existe")
            equipo.registrar(yo.toString(), "lugar_retirar", d.objeto, d.motivo, token)
            return@transaccion
        }
        val tabla = when (d.que) { "publicacion" -> "publicaciones"; "comentario" -> "comentarios"; else -> throw Problema(400, "objeto") }
        equipo.requiere(yo, if (d.que == "comentario") "moderar_comentarios" else "gestionar_publicaciones", token)
        val autor = leer("SELECT cuenta FROM $tabla WHERE id = ?", d.objeto.toLongOrNull()) { it.getLong(1) }.singleOrNull() ?: throw Problema(404, "no_existe")
        equipo.inferior(yo, autor)
        cambiar("UPDATE $tabla SET estado = 'retirado', retirado_hasta = ? WHERE id = ? AND estado = 'visible'", ahora() + 90 * DIA, d.objeto.toLongOrNull())
        if (d.que == "publicacion") marcarRetirada(d.objeto.toLongOrNull())
        equipo.registrar(yo.toString(), "contenido_retirar", d.que + ":" + d.objeto, d.motivo.take(500), token)
    }

    fun restaurar(yo: Long, d: ContenidoEquipo, token: String? = null): Unit = b.transaccion { restaurarInterno(yo, d, token) }

    private fun restaurarInterno(yo: Long, d: ContenidoEquipo, token: String?) {
        equipo.requiere(yo, "gestionar_publicaciones", token)
        val tabla = when (d.que) { "publicacion" -> "publicaciones"; "comentario" -> "comentarios"; else -> throw Problema(400, "objeto") }
        b.usar {
            val autor = leer("SELECT cuenta FROM $tabla WHERE id = ?", d.objeto.toLongOrNull()) { it.getLong(1) }.singleOrNull()
            if (autor != null) equipo.inferior(yo, autor)
            if (d.que == "publicacion") cambiar("DELETE FROM retiradas WHERE (cuenta, app, clave) IN (SELECT cuenta, app, clave FROM publicaciones WHERE id = ? AND estado = 'retirado')", d.objeto.toLongOrNull())
            cambiar("UPDATE $tabla SET estado = 'visible', retirado_hasta = 0 WHERE id = ? AND estado = 'retirado'", d.objeto.toLongOrNull())
            ocultarReportado(d.que, d.objeto, false)
            equipo.registrar(yo.toString(), "contenido_restaurar", d.que + ":" + d.objeto, d.motivo, token)
        }
    }

    fun levantarSancion(yo: Long, d: SancionEquipo, token: String? = null): Unit = b.transaccion { levantarSancionInterno(yo, d, token) }

    private fun levantarSancionInterno(yo: Long, d: SancionEquipo, token: String?) {
        equipo.requiere(yo, "gestionar_usuarios", token)
        if (d.motivo.isBlank() || d.motivo.length > 500) throw Problema(400, "motivo")
        val destino = b.usar { leer("SELECT id FROM cuentas WHERE usuario = ?", d.usuario) { it.getLong(1) }.singleOrNull() } ?: throw Problema(404, "no_existe")
        equipo.inferior(yo, destino)
        b.usar {
            cambiar("DELETE FROM faltas WHERE cuenta = ?", destino)
            cambiar("UPDATE cuentas SET restringida_hasta = 0, suspendida_hasta = 0, cerrada = 0 WHERE id = ?", destino)
            equipo.registrar(yo.toString(), "sancion_levantar", destino.toString(), d.motivo, token)
        }
    }

    /* ----- Insignias (X-08) ----- */

    /**
     * Recalcula la insignia de todas las cuentas. La cuenta principal es la oficial de Fogón ("fogon"); después
     * manda la decisión del equipo ([verificar]); sin decisión, una cuenta queda "verificada" sola si cumple
     * [Verificacion]: antigüedad, correo, foto, sin faltas ni restricciones vigentes y suficientes seguidores que
     * cuentan (cuentas con correo, activas y con antigüedad, para que no sirvan las cuentas hechas en serie).
     */
    fun revisarInsignias(): Unit = b.transaccion {
        val t = ahora()
        cambiar("""UPDATE cuentas SET insignia = CASE
            WHEN id IN (SELECT cuenta FROM roles WHERE rol = 'principal') THEN 'fogon'
            WHEN verificacion IN ('fogon', 'verificada') THEN verificacion
            WHEN verificacion = 'no' THEN ''
            WHEN creada <= ? AND correo IS NOT NULL AND foto = 1 AND cerrada = 0 AND borrar_el = 0
                AND restringida_hasta <= ? AND suspendida_hasta <= ?
                AND NOT EXISTS(SELECT 1 FROM faltas f WHERE f.cuenta = cuentas.id AND f.vence > ?)
                AND (SELECT COUNT(*) FROM seguir s JOIN cuentas o ON o.id = s.de WHERE s.a = cuentas.id AND s.aceptada = 1
                    AND o.borrar_el = 0 AND o.cerrada = 0 AND o.correo IS NOT NULL AND o.creada <= ?) >= ?
                THEN 'verificada'
            ELSE '' END""",
            t - Verificacion.ANTIGUEDAD, t, t, t, t - Verificacion.ANTIGUEDAD_SEGUIDOR, Verificacion.SEGUIDORES)
    }

    /**
     * El equipo da o quita la insignia de [CambioInsignia.usuario]: "verificada", "no" (nunca, aunque cumpla la
     * regla) o "auto" (vuelve a la regla). "fogon" (cuenta oficial) solo la da la cuenta principal.
     */
    fun verificar(yo: Long, d: CambioInsignia, token: String? = null): Unit = b.transaccion {
        equipo.requiere(yo, "gestionar_usuarios", token)
        if (d.insignia !in setOf("fogon", "verificada", "no", "auto")) throw Problema(400, "insignia")
        if (d.motivo.isBlank() || d.motivo.length > 500) throw Problema(400, "motivo")
        if (d.insignia == "fogon" && equipo.rol(yo) != "principal") throw Problema(403, "solo_principal")
        val destino = b.usar { leer("SELECT id FROM cuentas WHERE usuario = ?", d.usuario.lowercase()) { it.getLong(1) }.singleOrNull() } ?: throw Problema(404, "no_existe")
        b.usar {
            cambiar("UPDATE cuentas SET verificacion = ? WHERE id = ?", d.insignia.takeIf { it != "auto" }, destino)
            equipo.registrar(yo.toString(), "insignia_" + d.insignia, destino.toString(), d.motivo, token)
        }
        revisarInsignias()
    }

    private fun puntosVigentes(cuenta: Long) = b.usar { leer("SELECT coalesce(sum(puntos), 0) FROM faltas WHERE cuenta = ? AND vence > ?", cuenta, ahora()) { it.getInt(1) }.first() }

    private fun aplicarSancion(cuenta: Long, admin: Long, regla: String, mensaje: String = "", decision: String = "falta", origen: String = "contenido") {
        val p = puntosVigentes(cuenta)
        if (decision != "advertencia") b.usar {
            when {
                p >= 4 -> cambiar("UPDATE cuentas SET cerrada = 1 WHERE id = ?", cuenta)
                p == 3 -> cambiar("UPDATE cuentas SET suspendida_hasta = ? WHERE id = ?", ahora() + 7L * 24 * 3600_000, cuenta)
                p == 2 -> cambiar("UPDATE cuentas SET restringida_hasta = ? WHERE id = ?", ahora() + 24 * 3600_000L, cuenta)
            }
        }
        avisar(cuenta, admin, "sancion", regla)
        correoSancion(cuenta, regla, mensaje, decision, origen, p)
    }

    /**
     * El correo de una advertencia o sanción, como hacen las redes grandes: qué pasó, qué regla, el mensaje del
     * equipo, qué cambia y cómo apelar. Lo manda la cuenta de Fogón; nunca nombra a quien moderó.
     */
    private fun correoSancion(cuenta: Long, regla: String, mensaje: String, decision: String, origen: String, puntos: Int) {
        val (usuario, restr, susp, cerrada) = b.usar { leer("SELECT usuario, restringida_hasta, suspendida_hasta, cerrada FROM cuentas WHERE id = ?", cuenta) {
            listOf(it.getString(1), it.getLong(2), it.getLong(3), it.getInt(4) == 1) }.first() }
        val (asunto, consecuencia) = when {
            cerrada == true -> "Cerramos tu cuenta de Fogón Social" to "Tu cuenta quedó cerrada y ya no puedes entrar a la red. Lo que tienes en las apps sigue en tu teléfono."
            (susp as Long) > ahora() -> "Suspendimos tu cuenta de Fogón Social" to "Tu cuenta queda suspendida hasta el ${fechaLegible(susp)}: hasta entonces no puedes usar la red."
            (restr as Long) > ahora() -> "Tu cuenta de Fogón Social tiene una restricción" to "Hasta el ${fechaLegible(restr)} no puedes publicar, comentar ni mandar mensajes."
            decision == "advertencia" -> "Una advertencia de Fogón Social" to "Esta vez es solo una advertencia y no suma faltas. Si se repite, sí contará como falta."
            else -> "Quitamos algo que publicaste en Fogón Social" to "Tienes $puntos ${if (puntos == 1) "falta vigente" else "faltas vigentes"}. Con 2 faltas, 24 horas sin publicar; con 3, 7 días de suspensión; con 4, la cuenta se cierra. Las faltas vencen a los 90 días."
        }
        val que = if (origen == "cuenta") "Revisamos tu cuenta y encontramos algo que va contra las normas de Fogón Social."
            else "Revisamos un reporte sobre algo que publicaste y lo quitamos porque va contra las normas de Fogón Social."
        val texto = buildString {
            append("Hola, @$usuario:\n\n")
            append("$que\n\n")
            append("Motivo: ${Moderacion.nombreRegla(regla)}.\n")
            if (mensaje.isNotBlank()) append("Mensaje del equipo: $mensaje\n")
            append("\n$consecuencia\n\n")
            append("Puedes ver los detalles en la app, en Tu cuenta → Estado de la cuenta.\n")
            append("Las normas: ${Moderacion.NORMAS}\n\n")
            append("¿Crees que nos equivocamos? Puedes apelar durante los próximos 30 días desde la app: en Estado de la cuenta, toca «Apelar». ")
            append("Si tu cuenta está suspendida o cerrada, la app te lo ofrece al entrar. Una persona del equipo lee cada apelación y te responde a este correo.\n\n")
            append("— Fogón")
        }
        avisar(cuenta, asunto, texto)
    }

    private fun faltasDe(cuenta: Long, todas: Boolean = false): List<Falta> = b.usar {
        leer("SELECT regla, puntos, cuando, vence, mensaje, decision FROM faltas WHERE cuenta = ? AND vence > ? ORDER BY cuando DESC", cuenta, if (todas) 0 else ahora()) {
            Falta(it.getString(1), it.getInt(2), it.getLong(3), it.getLong(4), it.getString(5), it.getString(6).ifBlank { "falta" }) }
    }

    /** Las faltas vigentes y las restricciones de la cuenta ("Estado de tu cuenta"). */
    fun estadoCuenta(yo: Long): EstadoCuenta {
        val faltas = faltasDe(yo)
        val apelacion = apelacion(yo)
        return b.usar { leer("SELECT restringida_hasta, suspendida_hasta, cerrada, admin FROM cuentas WHERE id = ?", yo) {
            EstadoCuenta(faltas, it.getLong(1), it.getLong(2), it.getInt(3) == 1, equipo.rol(yo).isNotBlank(), apelacion) }.first() }
    }

    /** Si la cuenta puede usar la red: cerrada o suspendida, no ([escribir]: tampoco si está restringida). */
    fun revisarSancion(yo: Long, escribir: Boolean) {
        val (restr, susp, cerrada) = b.usar { leer("SELECT restringida_hasta, suspendida_hasta, cerrada FROM cuentas WHERE id = ?", yo) { Triple(it.getLong(1), it.getLong(2), it.getInt(3) == 1) }.firstOrNull() } ?: return
        if (cerrada) throw Problema(403, "cerrada")
        if (susp > ahora()) throw Problema(403, "suspendida")
        if (escribir && restr > ahora()) throw Problema(403, "restringida")
    }

    /* ----- Reglas ----- */

    /** [x] sigue a [y] (aceptado). */
    private fun sigue(x: Long, y: Long) = b.usar { leer("SELECT 1 FROM seguir WHERE de = ? AND a = ? AND aceptada = 1", x, y) { 1 }.isNotEmpty() }

    /** [quien] bloqueó a [aQuien]. */
    private fun bloqueado(quien: Long, aQuien: Long) = b.usar { leer("SELECT 1 FROM bloqueos WHERE de = ? AND a = ?", quien, aQuien) { 1 }.isNotEmpty() }

    private fun puedeVer(lector: Long?, autor: Long, vis: String): Boolean = when {
        lector == autor -> true
        lector != null && (bloqueado(autor, lector) || bloqueado(lector, autor)) -> false
        vis == Vis.PUBLICA && !esPrivada(autor) -> true
        vis in Vis.SUBIBLES -> lector != null && sigue(lector, autor)
        else -> false
    }

    private fun relacion(lector: Long?, id: Long): String {
        if (lector == null) return "nada"
        if (lector == id) return "yo"
        val fila = b.usar { leer("SELECT aceptada FROM seguir WHERE de = ? AND a = ?", lector, id) { it.getInt(1) }.firstOrNull() }
        return when (fila) { 1 -> "sigo"; 0 -> "pendiente"; else -> "nada" }
    }

    private fun esJpeg(bytes: ByteArray, max: Int) {
        if (bytes.size > max) throw Problema(413, "muy_grande")
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) throw Problema(415, "no_es_jpeg")
    }

    companion object {
        /** La versión de la política de privacidad y los términos: si cambian, se sube y la app vuelve a pedir que se acepten. */
        const val FALLOS_CODIGO_DIA = 15
        const val MAX_VIDEOS_DIA = 20
        const val POLITICA = "2026-11-11"
        /** Los votos que necesita una alternativa (y más de la mitad) para ser la imagen de la comunidad. */
        const val VOTOS_COMUNIDAD = 5
        val DOMINIOS_IMAGEN = setOf("image.tmdb.org", "covers.openlibrary.org", "coverartarchive.org", "upload.wikimedia.org")
        /** Además, para portadas de publicaciones, notas y perfiles: TVmaze, Spotify y las imágenes subidas a este servidor. */
        val DOMINIOS_PORTADA = DOMINIOS_IMAGEN + setOf("static.tvmaze.com", "i.scdn.co", "fogonapps.duckdns.org")
        /** Una portada solo puede venir de esos dominios: una dirección cualquiera delataría la IP de quien la vea. */
        fun portadaSegura(url: String): Boolean = url.length <= 500 && url.startsWith("https://") &&
            url.removePrefix("https://").substringBefore('/').substringBefore('?').lowercase() in DOMINIOS_PORTADA
        /** Quita de [datos] las portadas («portada», «fondo») que no vienen de un dominio permitido. */
        fun sinPortadasAjenas(datos: JsonObject): JsonObject =
            JsonObject(datos.filter { (k, v) -> k !in setOf("portada", "fondo") || (v as? JsonPrimitive)?.content.orEmpty().let { it.isEmpty() || !it.startsWith("http") || portadaSegura(it) } })
        /** Lo normal al empezar, sin datos (hipótesis: recalcular con los contadores cuando haya gente). */
        private const val PREVIA_MEGUSTA = 0.08
        private const val PREVIA_GUARDADO = 0.02
        private const val PREVIA_COMENTARIO = 0.02
        private const val M = 20.0
        private const val SEMANA = 7L * 24 * 3600_000
        private val azar = java.security.SecureRandom()
        val CORREO = Regex("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")
        /** Igual que en El Cajón (`Logica.usuarioLimpio`): 3 a 30 de letras, números, punto y guion bajo. */
        val USUARIO = Regex("[a-z0-9._]{3,30}")
        val RESERVADOS = setOf("admin", "elcajon", "cajon", "soporte", "api", "root", "moderacion", "explorar", "p", "privacidad", "terminos")
    }
}

/** Una fecha para los correos, en español y en UTC: «14 de noviembre de 2026, 14:37 (UTC)». */
internal fun fechaLegible(ms: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm '(UTC)'", java.util.Locale.forLanguageTag("es"))
        .withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(ms))

/** Una reseña: texto de hasta 5000 caracteres, nota de 0 a 10 si la hay y hasta 4 fotos, todas subidas por la misma cuenta. */
internal fun resenaValida(datos: Map<String, kotlinx.serialization.json.JsonElement>, imagenesPropias: Set<String>): Boolean {
    val texto = (datos["texto"] as? JsonPrimitive)?.content.orEmpty()
    if (texto.length > 5000) return false
    val nota = (datos["nota"] as? JsonPrimitive)?.content?.toIntOrNull()
    if (datos["nota"] != null && (nota == null || nota !in 0..10)) return false
    val fotos = (datos["fotos"] as? kotlinx.serialization.json.JsonArray)?.map { (it as? JsonPrimitive)?.content.orEmpty() } ?: emptyList()
    return fotos.size <= 4 && fotos.all { it in imagenesPropias }
}
