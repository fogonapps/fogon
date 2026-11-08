package io.github.fogonapps.social

import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
data class Persona(val usuario: String, val nombre: String, val foto: Boolean, val privada: Boolean = false)

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
data class Aviso(val id: Long, val tipo: String, val de: Persona, val objeto: String, val cuando: Long, val leido: Boolean)

@Serializable data class NuevaCuenta(val usuario: String, val nombre: String = "")
@Serializable data class PedirCodigo(val correo: String)
/** Entrar con el código del correo. Para una cuenta nueva van también el usuario, el nombre y la aceptación. */
@Serializable data class Entrada(
    val correo: String = "", val codigo: String = "", val usuario: String = "", val nombre: String = "",
    /** La versión de la política y los términos que aceptó ([Social.POLITICA]); vacía si no es cuenta nueva. */
    val acepta: String = "",
    /** Dijo que tiene 18 años o más. */
    val mayorDeEdad: Boolean = false,
    /** Con el código, la contraseña nueva: al crear la cuenta, si la olvidó o si la cuenta todavía no tenía. */
    val clave: String = "",
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
)
@Serializable data class Aceptacion(val politica: String)
@Serializable data class VotoImagen(val fuente: String, val url: String)
@Serializable data class BloqueoImagen(val url: String, val motivo: String = "")
/** La imagen de la comunidad para una obra (si la hay), los votos por URL y lo bloqueado entre ellas. */
@Serializable data class ImagenObra(val comunidad: String? = null, val votos: Map<String, Int> = emptyMap(), val miVoto: String? = null, val bloqueadas: List<String> = emptyList())
@Serializable data class EntradaClave(val correo: String, val clave: String)
/** El cajón cifrado ([sobre], base64) y la versión en que va ([cambio]); [llave]: la llave envuelta con la contraseña. */
@Serializable data class CajonCifrado(val sobre: String = "", val cambio: Long = 0, val llave: String = "")
@Serializable data class Borrado(val clave: String = "", val codigo: String = "", val motivo: String = "", val motivoTexto: String = "")
@Serializable data class CambioCorreo(val nuevo: String, val clave: String = "", val codigo: String = "")
@Serializable data class CambioClave(val nueva: String, val actual: String = "")
@Serializable data class Sesion(val id: String, val dispositivo: String, val creada: Long, val ultimo_uso: Long, val actual: Boolean)
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
@Serializable data class FraseNueva(val texto: String, val idioma: String, val franja: String = "todo")
/** Una frase de la comunidad: su clave para votar ("c" + id), el texto y quién la propuso (solo para moderar). */
@Serializable data class FraseComunidad(val clave: String, val texto: String, val idioma: String, val de: String = "", val franja: String = "todo")
@Serializable data class DecisionFrase(val clave: String, val aprobar: Boolean)
@Serializable data class ConteoFrase(val si: Int, val no: Int)
@Serializable data class NotaNueva(val texto: String, val datos: JsonObject = JsonObject(emptyMap()))
@Serializable data class Nota(val persona: Persona, val texto: String, val datos: JsonObject, val cuando: Long)
/** Las llaves públicas de una cuenta (keysets de Tink en JSON, en base64): para cifrar y para comprobar la firma. */
@Serializable data class Llaves(val cifrar: String, val firmar: String)
/** Un mensaje cifrado para alguien: [sobre] es el texto cifrado (base64) y [firma], la firma de quien lo manda. */
@Serializable data class SobreNuevo(val sobre: String, val firma: String)
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
) {

    val equipo = Equipo(b, dirFotos.parentFile ?: dirFotos, ahora, cartero, principal)

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
    fun pedirCodigo(correoDado: String) { enviarCodigo(correoDado) }

    private fun enviarCodigo(correoDado: String, cuentaCambio: Long? = null) {
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
                cambiar("INSERT OR REPLACE INTO cambios_correo VALUES(?,?,?,?,0)", cuentaCambio, correo, Base.huella("$correo:$codigo"), ahora() + 10 * 60_000)
            } else cambiar("""INSERT INTO codigos(correo, huella, expira, intentos, enviado) VALUES(?,?,?,0,?)
                ON CONFLICT(correo) DO UPDATE SET huella = excluded.huella, expira = excluded.expira, intentos = 0, enviado = excluded.enviado""",
                correo, Base.huella("$correo:$codigo"), ahora() + 10 * 60_000, ahora())
        }
        val proposito = if (cuentaCambio == null) "entrar a El Cajón" else "confirmar tu nuevo correo de El Cajón"
        cartero.enviar(correo, "Tu código de El Cajón: $codigo",
            "Tu código para $proposito es:\n\n$codigo\n\nVence en 10 minutos. Si no lo pediste, ignora este correo.\n\nEl Cajón · Fogón")
    }

    /**
     * Entra con el código. Si el correo ya tiene cuenta, abre una sesión nueva. Si no, hace falta elegir el usuario,
     * aceptar la política y los términos ([POLITICA]) y tener 18 años o más; mientras no lleguen, responde
     * "cuenta_nueva" sin gastar el código (la app pregunta y vuelve a llamar).
     */
    @Synchronized
    fun entrar(e: Entrada, dispositivo: String = ""): Acceso {
        if (e.clave.isNotEmpty()) Claves.validar(e.clave)
        val correo = e.correo.trim().lowercase()
        codigoBueno(correo, e.codigo)
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
        val token = sesionNueva(id, dispositivo, cerrarAnteriores = e.clave.isNotEmpty())
        if (e.clave.isNotEmpty()) {
            avisarClave(id)
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
        if (Base.huella("$correo:${codigo.trim()}") != huella) {
            b.usar { cambiar("UPDATE codigos SET intentos = intentos + 1 WHERE correo = ?", correo) }
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

    private fun sesionNueva(cuenta: Long, dispositivoDado: String = "", cerrarAnteriores: Boolean = false): String {
        if (b.usar { leer("SELECT cerrada FROM cuentas WHERE id = ?", cuenta) { it.getInt(1) == 1 }.first() }) throw Problema(403, "cerrada")
        val dispositivo = dispositivoDado.filter { !it.isISOControl() }.take(60)
        val nueva = dispositivo.isNotBlank() && b.usar {
            leer("SELECT 1 FROM sesiones WHERE cuenta = ? AND dispositivo = ? AND ultimo_uso > ?", cuenta, dispositivo, ahora() - CADUCIDAD) { 1 }.isEmpty()
        }
        val token = Base.tokenNuevo()
        b.usar {
            cambiar("INSERT INTO sesiones(huella, cuenta, creada, dispositivo, ultimo_uso) VALUES(?,?,?,?,?)", Base.huella(token), cuenta, ahora(), dispositivo, ahora())
            if (cerrarAnteriores) cerrarOtras(cuenta, token)
        }
        if (nueva) avisar(cuenta, "Se entró a tu cuenta desde $dispositivo",
            "Entrada desde $dispositivo el ${fechaLegible(ahora())}. Si no fuiste tú, abre El Cajón, Tu cuenta de la red → Dónde has entrado y cierra esa sesión.")
        return token
    }

    private fun idSesion(huella: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(secreto, "HmacSHA256"))
        return mac.doFinal(("sesion:" + huella).toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    }

    fun sesiones(yo: Long, token: String?): List<Sesion> = b.usar {
        leer("SELECT huella, dispositivo, creada, ultimo_uso FROM sesiones WHERE cuenta = ? AND ultimo_uso > ? ORDER BY creada", yo, ahora() - CADUCIDAD) {
            Sesion(idSesion(it.getString(1)), it.getString(2), it.getLong(3), it.getLong(4), it.getString(1) == Base.huella(token.orEmpty()))
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

    private fun avisarClave(yo: Long) = avisar(yo, "Tu contraseña de El Cajón cambió",
        "Tu contraseña de El Cajón cambió. Si no fuiste tú, entra con un código de tu correo, cambia la contraseña y cierra las demás sesiones desde Tu cuenta de la red.")

    @Synchronized
    fun limpiarSeguridad() {
        b.usar { leer("SELECT id FROM cuentas WHERE borrar_el > 0 AND borrar_el <= ? AND id NOT IN (SELECT cuenta FROM roles WHERE rol IN ('principal','administrador'))", ahora()) { it.getLong(1) } }.forEach(::borrarCuenta)
        b.usar {
            val imagenesVencidas = leer("SELECT DISTINCT i.nombre FROM imagenes i JOIN publicaciones p ON p.cuenta = i.cuenta AND instr(p.datos, i.nombre) > 0 WHERE p.estado = 'retirado' AND p.retirado_hasta <= ?", ahora()) { it.getString(1) }
            cambiar("DELETE FROM comentarios WHERE estado = 'retirado' AND retirado_hasta <= ?", ahora())
            cambiar("DELETE FROM publicaciones WHERE estado = 'retirado' AND retirado_hasta <= ? AND NOT EXISTS(SELECT 1 FROM comentarios c WHERE c.pub = publicaciones.id AND c.estado != 'visible')", ahora())
            imagenesVencidas.forEach { nombre ->
                if (leer("SELECT 1 FROM publicaciones WHERE instr(datos, ?) > 0", nombre) { 1 }.isEmpty()) {
                    java.io.File(dirImagenes, "$nombre.jpg").delete()
                    cambiar("DELETE FROM imagenes WHERE nombre = ?", nombre)
                }
            }
            cambiar("DELETE FROM sesiones WHERE ultimo_uso <= ?", ahora() - CADUCIDAD)
            cambiar("DELETE FROM intentos WHERE ultimo_fallo <= ? AND bloqueada_hasta <= ?", ahora() - DIA, ahora())
            cambiar("DELETE FROM envios_codigo WHERE cuando <= ?", ahora() - HORA)
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
        leer("SELECT usuario, nombre, coalesce(correo, ''), privada, clave IS NOT NULL, usuario_cambio, admin, acepto FROM cuentas WHERE id = ?", yo) {
            MiCuenta(it.getString(1), it.getString(2), it.getString(3), it.getInt(4) == 1, it.getInt(5) == 1, (if (it.getLong(6) == 0L) 0 else it.getLong(6) + 30 * DIA).takeIf { d -> d > ahora() } ?: 0, equipo.rol(yo).isNotBlank(), apodoDesde(yo), borrarEl(yo), equipo.rol(yo), equipo.permisos(yo), equipo.activa(yo), equipo.elevada(yo, token), idCuenta(yo), it.getString(8))
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
        val correo = e.correo.trim().lowercase()
        val (id, usuario, guardada) = b.usar {
            leer("SELECT id, usuario, clave FROM cuentas WHERE correo = ?", correo) { Triple(it.getLong(1), it.getString(2), it.getString(3)) }.firstOrNull()
        } ?: throw Problema(400, "clave_mala")
        val intento = b.usar { leer("SELECT fallos, desde, bloqueada_hasta, nivel, ultimo_fallo FROM intentos WHERE cuenta = ?", id) {
            Intento(it.getInt(1), it.getLong(2), it.getLong(3), it.getInt(4), it.getLong(5))
        }.firstOrNull() }?.takeIf { ahora() - it.ultimoFallo < DIA }
        if (intento != null && intento.bloqueadaHasta > ahora()) throw Problema(400, "clave_mala")
        if (guardada == null || !Claves.comprobar(e.clave, guardada)) {
            val fallos = if (intento != null && ahora() - intento.desde < 15 * 60_000 && intento.bloqueadaHasta == 0L) intento.fallos + 1 else 1
            val nivel = intento?.nivel ?: 0
            val espera = if (fallos >= 5) listOf(1, 5, 15, 60)[nivel.coerceAtMost(3)] * 60_000L else 0L
            val desde = if (fallos > 1) intento!!.desde else ahora()
            b.usar { cambiar("INSERT OR REPLACE INTO intentos(cuenta, fallos, desde, bloqueada_hasta, nivel, ultimo_fallo) VALUES(?,?,?,?,?,?)", id, fallos, desde, if (espera == 0L) 0 else ahora() + espera, if (espera == 0L) nivel else (nivel + 1).coerceAtMost(4), ahora()) }
            throw Problema(400, "clave_mala")
        }
        b.usar { cambiar("DELETE FROM intentos WHERE cuenta = ?", id) }
        return Acceso(usuario, sesionNueva(id, dispositivo), false, borrarEl(id), idCuenta(id))
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
        avisarClave(yo)
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
            cambiar("UPDATE cuentas SET usuario = ?, usuario_cambio = ?, cambio = ? WHERE id = ?", nuevo, ahora(), ahora(), yo)
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
            b.usar { cambiar("UPDATE cuentas SET privada = ?, cambio = ? WHERE id = ?", if (privada) 1 else 0, ahora(), yo) }
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
    fun cambiarCorreo(yo: Long, e: CambioCorreo) {
        if (miCuenta(yo).correo.isBlank()) throw Problema(400, "sin_correo")
        val nuevo = e.nuevo.trim().lowercase()
        // Valida la dirección y aplica los mismos límites antes de consumir la credencial.
        if (!CORREO.matches(nuevo) || nuevo.length > 120 || nuevo == miCuenta(yo).correo) throw Problema(400, "correo")
        comprobarCredencial(yo, e.clave, e.codigo, permiteCodigo = true)
        enviarCodigo(nuevo, cuentaCambio = yo)
        avisar(yo, "Solicitud de cambio de correo", "Se pidió cambiar el correo de tu cuenta. Si no fuiste tú, entra y cambia tu contraseña.")
    }

    @Synchronized
    fun confirmarCorreo(yo: Long, e: CambioCorreo) {
        val nuevo = e.nuevo.trim().lowercase()
        val pendiente = b.usar { leer("SELECT nuevo, huella, expira, intentos FROM cambios_correo WHERE cuenta = ?", yo) {
            listOf(it.getString(1), it.getString(2), it.getLong(3), it.getInt(4))
        }.firstOrNull() } ?: throw Problema(400, "sin_codigo")
        if (ahora() >= pendiente[2] as Long || pendiente[3] as Int >= 5) throw Problema(400, "codigo_vencido")
        if (nuevo != pendiente[0] || Base.huella("$nuevo:${e.codigo.trim()}") != pendiente[1]) {
            b.usar { cambiar("UPDATE cambios_correo SET intentos = intentos + 1 WHERE cuenta = ?", yo) }
            throw Problema(400, "codigo")
        }
        if (b.usar { leer("SELECT 1 FROM cuentas WHERE correo = ? AND id <> ?", nuevo, yo) { 1 }.isNotEmpty() }) throw Problema(409, "correo_tomado")
        val anterior = miCuenta(yo).correo
        b.usar {
            cambiar("UPDATE cuentas SET correo = ? WHERE id = ?", nuevo, yo)
            cambiar("DELETE FROM cambios_correo WHERE cuenta = ?", yo)
        }
        enviarAviso(anterior, "Tu correo de El Cajón cambió", "El correo de tu cuenta cambió. Si no fuiste tú, contacta con Fogón.")
        avisar(yo, "Tu correo de El Cajón cambió", "Esta es la nueva dirección de tu cuenta de El Cajón.")
    }

    /** Borra la cuenta y todo lo suyo: perfil, publicaciones, a quién sigue, me gusta, comentarios, avisos, imágenes y foto. */
    internal fun borrarCuenta(yo: Long) {
        if (equipo.rol(yo) == "principal") throw Problema(403, "cuenta_protegida")
        // Las cascadas de SQLite tampoco deben destruir evidencia pendiente o preservada.
        if (b.usar { leer("SELECT 1 FROM publicaciones p WHERE (p.cuenta = ? AND p.estado != 'visible') OR EXISTS(SELECT 1 FROM comentarios c WHERE c.pub = p.id AND c.estado != 'visible' AND (c.cuenta = ? OR p.cuenta = ?)) LIMIT 1", yo, yo, yo) { 1 }.isNotEmpty() }) return
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
    }

    private fun idDe(usuario: String): Long =
        b.usar { leer("SELECT id FROM cuentas WHERE usuario = ? AND cerrada = 0 AND borrar_el = 0", usuario.lowercase()) { it.getLong(1) }.firstOrNull() } ?: throw Problema(404, "no_existe")

    private fun persona(id: Long): Persona = b.usar {
        leer("SELECT usuario, nombre, foto, privada FROM cuentas WHERE id = ?", id) { Persona(it.getString(1), it.getString(2), it.getInt(3) == 1, it.getInt(4) == 1) }.first()
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
        val perfil = b.usar { leer("SELECT perfil FROM cuentas WHERE id = ?", yo) { JsonSocial.decodeFromString<PerfilSubido>(it.getString(1)) }.first() }
        subirPerfil(yo, perfil.copy(nombre = nombre))
    }

    @Synchronized
    fun subirPerfil(yo: Long, p: PerfilSubido) {
        val anterior = miCuenta(yo).nombre
        val nombre = p.nombre.trim().take(40)
        val cambia = nombre != anterior && apodoDesde(yo) <= ahora()
        val limpio = p.copy(
            nombre = if (cambia) nombre else anterior, bio = p.bio.trim().take(200), portada = p.portada.take(500).takeIf { it.startsWith("https://") } ?: "",
            enlaces = p.enlaces.filter { it.startsWith("https://") || it.startsWith("http://") }.map { it.take(200) }.take(5),
            partes = p.partes.filterValues { it.visibilidad in Vis.SUBIBLES }.entries.take(20).associate { it.key.take(30) to it.value },
        )
        val json = JsonSocial.encodeToString(PerfilSubido.serializer(), limpio)
        if (json.length > 200_000) throw Problema(413, "muy_grande")
        b.usar {
            if (cambia) cambiar("INSERT INTO apodo_cambios VALUES(?,?)", yo, ahora())
            cambiar("UPDATE cuentas SET nombre = ?, perfil = ?, cambio = ? WHERE id = ?", limpio.nombre, json, ahora(), yo)
        }
    }

    /** La foto: un JPEG de hasta 400 KB (la app la achica antes). */
    fun subirFoto(yo: Long, bytes: ByteArray) {
        esJpeg(bytes, 400_000)
        dirFotos.mkdirs()
        java.io.File(dirFotos, "$yo.jpg").writeBytes(bytes)
        b.usar { cambiar("UPDATE cuentas SET foto = 1 WHERE id = ?", yo) }
    }

    fun quitarFoto(yo: Long) {
        java.io.File(dirFotos, "$yo.jpg").delete()
        b.usar { cambiar("UPDATE cuentas SET foto = 0 WHERE id = ?", yo) }
    }

    /** La foto de alguien (si quien mira no está bloqueado). */
    fun foto(lector: Long?, usuario: String): ByteArray {
        val id = idDe(usuario)
        if (lector != null && bloqueado(id, lector)) throw Problema(404, "no_existe")
        return java.io.File(dirFotos, "$id.jpg").takeIf { it.exists() }?.readBytes() ?: throw Problema(404, "sin_foto")
    }

    fun verPerfil(lector: Long?, usuario: String): PerfilVisto {
        val id = idDe(usuario)
        if (lector != null && (bloqueado(id, lector) || bloqueado(lector, id))) throw Problema(404, "no_existe")
        val p = b.usar { leer("SELECT perfil FROM cuentas WHERE id = ?", id) { it.getString(1) }.first() }
            .let { runCatching { JsonSocial.decodeFromString(PerfilSubido.serializer(), it) }.getOrDefault(PerfilSubido()) }
        val relacion = relacion(lector, id)
        val cerrada = esPrivada(id) && relacion != "yo" && relacion != "sigo"
        val ve = { v: String -> !cerrada && (v == Vis.PUBLICA || relacion == "yo" || relacion == "sigo") }
        val pubs = if (cerrada) emptyList() else filas(lector, "p.cuenta = ? AND (p.oculta = 0 OR p.cuenta = ?) ORDER BY p.cuando DESC LIMIT 60", id, lector ?: -1).map { it.second }.filter { ve(it.visibilidad) }
        val (seguidores, siguiendo, cuantas) = b.usar {
            Triple(
                leer("SELECT COUNT(*) FROM seguir WHERE de IN (SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0) AND a = ? AND aceptada = 1", id) { it.getInt(1) }.first(),
                leer("SELECT COUNT(*) FROM seguir WHERE a IN (SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0) AND de = ? AND aceptada = 1", id) { it.getInt(1) }.first(),
                leer("SELECT COUNT(*) FROM publicaciones WHERE cuenta = ?", id) { it.getInt(1) }.first(),
            )
        }
        val meSigue = lector != null && sigue(id, lector)
        val megusta = b.usar { leer("SELECT COUNT(*) FROM megusta m JOIN publicaciones p ON p.id = m.pub WHERE p.cuenta = ?", id) { it.getInt(1) }.first() }
        val guardadas = relacion == "yo" || p.partes["guardadas"]?.let { ve(it.visibilidad) } == true
        return PerfilVisto(
            persona(id), p.bio, p.portada, p.enlaces, if (cerrada) emptyMap() else p.partes.filterValues { ve(it.visibilidad) }.mapValues { it.value.datos },
            pubs, relacion, meSigue, seguidores, siguiendo, cuantas, cerrada, megusta, guardadas,
        )
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
        val estados = b.usar { leer("SELECT estado FROM publicaciones WHERE estado != 'visible' AND cuenta = (SELECT cuenta FROM imagenes WHERE nombre = ?) AND instr(datos, ?) > 0", nombre, nombre) { it.getString(1) } }
        if ("preservado" in estados) throw Problema(404, "no_existe")
        if ("retirado" in estados) {
            if (lector == null || "ver_reportes" !in equipo.permisos(lector)) throw Problema(404, "no_existe")
            equipo.requiere(lector, "ver_reportes", token)
        }
        if (!Regex("[A-Za-z0-9_-]{22}").matches(nombre)) throw Problema(404, "no_existe")
        if (b.usar { leer("SELECT cuenta FROM imagenes WHERE nombre = ?", nombre) { it.getLong(1) }.firstOrNull() }?.let(::visible) != true) throw Problema(404, "no_existe")
        return java.io.File(dirImagenes, "$nombre.jpg").takeIf { it.exists() }?.readBytes() ?: throw Problema(404, "no_existe")
    }

    /* ----- Publicaciones ----- */

    /**
     * Sube todas las publicaciones de la persona: lo que ya estaba (por [PublicacionSubida.clave]) se actualiza sin
     * perder sus me gusta ni comentarios, lo nuevo se agrega y lo que ya no viene (lo volvió privado o lo borró) se quita.
     */
    /**
     * Sube publicaciones y quita solo las que el teléfono dice ([quitar], por clave). A diferencia de
     * [subirPublicaciones], lo que falta en la lista no se borra: un teléfono nuevo o vacío no se lleva lo publicado.
     */
    fun subirCambios(yo: Long, c: CambiosPublicaciones) {
        val actuales = b.usar { leer("SELECT clave FROM publicaciones WHERE cuenta = ?", yo) { it.getString(1) } }.toSet()
        val quitar = c.quitar.map { it.take(80) }.toSet()
        val lista = c.lista.filter { it.clave.take(80) !in quitar }
        // Lo que ya estaba y no se pidió quitar, se queda: se vuelve a mandar tal cual (subirPublicaciones borra lo que no viene).
        val quedan = actuales - quitar - lista.map { it.clave.take(80) }.toSet()
        val viejas = if (quedan.isEmpty()) emptyList() else b.usar {
            leer("SELECT clave, visibilidad, cuando, datos FROM publicaciones WHERE cuenta = ?", yo) { r ->
                if (r.getString(1) !in quedan) null else {
                    val d = JsonSocial.parseToJsonElement(r.getString(4)) as JsonObject
                    PublicacionSubida(r.getString(1), r.getString(2), r.getLong(3), (d["tipo"] as? JsonPrimitive)?.content ?: "", JsonObject(d - "tipo"))
                }
            }.filterNotNull()
        }
        subirPublicaciones(yo, lista + viejas)
    }

    fun subirPublicaciones(yo: Long, lista: List<PublicacionSubida>) {
        if (lista.size > 2000) throw Problema(413, "muy_grande")
        val buenas = lista.filter { it.visibilidad in Vis.SUBIBLES && it.clave.isNotBlank() }.distinctBy { it.clave }
        var nuevaParaAvisar: String? = null
        b.usar {
            autoCommit = false
            try {
                val bloqueadas = leer("SELECT clave FROM retiradas WHERE cuenta = ?", yo) { it.getString(1) }.toSet()
                val buenas = buenas.filter { it.clave.take(80) !in bloqueadas }
                val claves = buenas.map { it.clave.take(80) }.toSet()
                val yaEstaban = leer("SELECT clave FROM publicaciones WHERE cuenta = ?", yo) { it.getString(1) }.toSet()
                nuevaParaAvisar = buenas.filter { it.clave.take(80) !in yaEstaban && ahora() - it.cuando < 24 * 3600_000L }.maxByOrNull { it.cuando }?.clave?.take(80)
                leer("SELECT id, clave FROM publicaciones WHERE cuenta = ?", yo) { it.getLong(1) to it.getString(2) }
                    .filter { it.second !in claves }.forEach {
                        cambiar("DELETE FROM publicaciones WHERE id = ? AND estado = 'visible' AND NOT EXISTS(SELECT 1 FROM comentarios c WHERE c.pub = publicaciones.id AND c.estado != 'visible')", it.first)
                        // Si queda como contenedora de evidencia, deja de ser pública hasta su purga.
                        cambiar("UPDATE publicaciones SET estado = 'retirado', retirado_hasta = 0 WHERE id = ? AND estado = 'visible'", it.first)
                    }
                val mias = leer("SELECT nombre FROM imagenes WHERE cuenta = ?", yo) { it.getString(1) }.toSet()
                buenas.forEach { p ->
                    val imagenes = p.datos["imagenes"]
                    if (imagenes != null && (imagenes !is kotlinx.serialization.json.JsonArray || imagenes.size > 10 || imagenes.any { it !is JsonPrimitive || !it.isString || it.content !in mias })) throw Problema(400, "imagenes")
                    if (p.tipo == "resena" && !resenaValida(p.datos, mias)) return@forEach
                    val datos = JsonSocial.encodeToString(JsonObject.serializer(), JsonObject(p.datos + ("tipo" to JsonPrimitive(p.tipo.take(20)))))
                    if (datos.length > 20_000) return@forEach
                    cambiar("""INSERT INTO publicaciones(cuenta, clave, visibilidad, cuando, datos) VALUES(?,?,?,?,?)
                        ON CONFLICT(cuenta, clave) DO UPDATE SET visibilidad = excluded.visibilidad, cuando = excluded.cuando, datos = excluded.datos WHERE publicaciones.estado = 'visible'""",
                        yo, p.clave.take(80), p.visibilidad, p.cuando, datos)
                }
                commit()
            } catch (x: Exception) { rollback(); throw x } finally { autoCommit = true }
        }
        nuevaParaAvisar?.let { clave -> avisarSeguidores(yo, clave) }
    }

    /** Avisa a sus seguidores que publicó algo (una vez cada 6 horas por autor, para no llenar de avisos). */
    private fun avisarSeguidores(yo: Long, clave: String) {
        val id = b.usar { leer("SELECT id FROM publicaciones WHERE cuenta = ? AND clave = ?", yo, clave) { it.getLong(1) }.firstOrNull() } ?: return
        val reciente = b.usar { leer("SELECT 1 FROM avisos WHERE de = ? AND tipo = 'publico' AND cuando > ? LIMIT 1", yo, ahora() - 6 * 3600_000L) { 1 }.isNotEmpty() }
        if (reciente) return
        b.usar { leer("SELECT de FROM seguir WHERE a = ? AND aceptada = 1", yo) { it.getLong(1) } }.forEach { avisar(it, yo, "publico", id.toString()) }
    }

    fun mensajesSinLeer(yo: Long): Int = b.usar { leer("SELECT COUNT(*) FROM mensajes WHERE de IN (SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0) AND para = ?", yo) { it.getInt(1) }.first() }

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
                    EXISTS(SELECT 1 FROM utiles u WHERE u.pub = p.id AND u.cuenta = ?)
                FROM publicaciones p JOIN cuentas c ON c.id = p.cuenta WHERE p.estado = 'visible' AND c.borrar_el = 0 AND c.cerrada = 0 AND $donde""", lector, lector, lector, *args) { r ->
            val datos = JsonSocial.parseToJsonElement(r.getString(5)) as JsonObject
            r.getLong(2) to Publicacion(r.getLong(1), Persona(r.getString(6), r.getString(7), r.getInt(8) == 1, r.getInt(9) == 1),
                (datos["tipo"] as? JsonPrimitive)?.content ?: "", r.getLong(4), r.getString(3), datos,
                r.getInt(10), r.getInt(11), r.getInt(13) == 1, r.getInt(14) == 1, r.getInt(12), guardados = r.getInt(15),
                utiles = r.getInt(16), yoUtil = r.getInt(17) == 1)
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
        val lista = filas(lector, """json_extract(p.datos, '$.fuente') = ? AND json_extract(p.datos, '$.tipo') = 'resena'$porParte AND p.oculta = 0 AND c.cerrada = 0 AND c.borrar_el = 0
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
    fun borrarPublicacion(yo: Long, id: Long) {
        b.usar {
            autoCommit = false
            try {
                val clave = leer("SELECT clave FROM publicaciones WHERE id = ? AND cuenta = ? AND estado = 'visible'", id, yo) { it.getString(1) }.firstOrNull()
                    ?: throw Problema(404, "no_existe")
                cambiar("INSERT OR IGNORE INTO retiradas(cuenta, clave, cuando) VALUES(?,?,?)", yo, clave, ahora())
                cambiar("DELETE FROM publicaciones WHERE id = ? AND NOT EXISTS(SELECT 1 FROM comentarios c WHERE c.pub = publicaciones.id AND c.estado != 'visible')", id)
                cambiar("UPDATE publicaciones SET estado = 'retirado', retirado_hasta = 0 WHERE id = ? AND estado = 'visible'", id)
                commit()
            } catch (x: Exception) { rollback(); throw x } finally { autoCommit = true }
        }
    }

    /** Marca o quita «útil» en una reseña ajena que [yo] puede ver. */
    fun util(yo: Long, pub: Long, si: Boolean) {
        val p = publicacion(yo, pub) ?: throw Problema(404, "no_existe")
        if (p.tipo != "resena") throw Problema(400, "no_es_resena")
        if (idDe(p.autor.usuario) == yo) throw Problema(400, "propia")
        b.usar { if (si) cambiar("INSERT OR IGNORE INTO utiles(pub, cuenta, cuando) VALUES(?,?,?)", pub, yo, ahora()) else cambiar("DELETE FROM utiles WHERE pub = ? AND cuenta = ?", pub, yo) }
    }


    /** El feed: lo último de quienes sigue (y lo suyo), del más reciente al más viejo; [antes] para seguir bajando. */
    fun actividad(yo: Long, antes: Long?, n: Int = 40): List<Publicacion> =
        filas(yo, """(p.cuenta = ? OR (p.oculta = 0 AND p.cuenta IN (SELECT a FROM seguir WHERE de = ? AND aceptada = 1)))
                AND p.cuando < ? ORDER BY p.cuando DESC LIMIT ?""", yo, yo, antes ?: Long.MAX_VALUE, n.coerceIn(1, 100)).map { it.second }

    /**
     * Para descubrir: candidatos iguales para todos (el servidor no perfila a nadie; el teléfono los ordena según los
     * gustos de la persona, que nunca salen de él). Lo público de cuentas públicas que todavía no sigue: 70 % lo más
     * reciente de la semana, 20 % lo mejor de lo demás (cota de Wilson) y 10 % lo nuevo con pocas
     * vistas, para que tenga su oportunidad. Cada una con su [Publicacion.calidad].
     */
    fun explorar(lector: Long?, antes: Long?, n: Int = 200): List<Publicacion> {
        val ahora = ahora()
        // Las 1000 más recientes: la semana y lo nuevo salen de ahí, y en una red chiquita también lo más viejo completa.
        val todas = filas(lector, """p.visibilidad = 'publica' AND c.privada = 0 AND p.oculta = 0 AND c.cerrada = 0 AND c.borrar_el = 0 AND p.cuando < ?
                AND (? IS NULL OR (p.cuenta <> ? AND p.cuenta NOT IN (SELECT a FROM seguir WHERE de = ?)
                    AND p.cuenta NOT IN (SELECT a FROM bloqueos WHERE de = ?) AND p.cuenta NOT IN (SELECT de FROM bloqueos WHERE a = ?)))
                ORDER BY p.cuando DESC LIMIT 1000""", antes ?: Long.MAX_VALUE, lector, lector, lector, lector, lector)
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
    fun guardadasDe(lector: Long?, usuario: String): List<Publicacion> {
        val id = idDe(usuario)
        if (!verPerfil(lector, usuario).guardadasVisibles) throw Problema(403, "privada")
        return guardados(id).filter { pub -> publicacion(lector, pub.id) != null }
    }

    /** Lo público que lleva la etiqueta #[etiqueta] (en sus palabras), lo más reciente primero. */
    fun conEtiqueta(lector: Long?, etiqueta: String, antes: Long?): List<Publicacion> {
        val e = etiqueta.trim().removePrefix("#").lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(40)
        if (e.length < 2) return emptyList()
        return filas(lector, """p.visibilidad = 'publica' AND c.privada = 0 AND p.cuando < ? AND lower(p.datos) LIKE ? ESCAPE '\'
                ORDER BY p.cuando DESC LIMIT 60""", antes ?: Long.MAX_VALUE, "%#" + e.replace("_", "\\_") + "%")
            .filter { (autor, _) -> lector == null || (!bloqueado(autor, lector) && !bloqueado(lector, autor)) }.map { it.second }
    }

    fun guardados(yo: Long): List<Publicacion> =
        filas(yo, "p.id IN (SELECT pub FROM guardados WHERE cuenta = ?) ORDER BY (SELECT cuando FROM guardados WHERE pub = p.id AND cuenta = ?) DESC LIMIT 200", yo, yo)
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
        if (t.isEmpty()) { b.usar { cambiar("DELETE FROM notas WHERE cuenta = ?", yo) }; return }
        val datos = JsonSocial.encodeToString(JsonObject.serializer(), n.datos)
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
    fun proponerFrase(yo: Long, f: FraseNueva) {
        val t = f.texto.trim().replace(Regex("\\s+"), " ")
        if (t.codePointCount(0, t.length) !in 6..90 || Regex("(?i)https?://|www\\.|@\\w").containsMatchIn(t) || f.idioma !in setOf("es", "en")) throw Problema(400, "frase")
        b.usar {
            val hoy = leer("SELECT COUNT(*) FROM frases_propuestas WHERE cuenta = ? AND cuando > ?", yo, ahora() - 86_400_000L) { it.getInt(1) }.first()
            if (hoy >= 3) throw Problema(429, "despacio")
            cambiar("INSERT INTO frases_propuestas(cuenta, texto, idioma, cuando) VALUES(?,?,?,?)", yo, t, f.idioma, ahora())
        }
    }

    /** Las frases aprobadas de un idioma. */
    fun frasesComunidad(idioma: String): List<FraseComunidad> = b.usar {
        leer("SELECT id, texto, idioma, franja FROM frases_propuestas WHERE estado = 'aprobada' AND idioma = ? ORDER BY id DESC LIMIT 300", idioma) {
            FraseComunidad("c${it.getLong(1)}", it.getString(2), it.getString(3), franja = it.getString(4))
        }
    }

    /** Las que esperan moderación (solo el equipo). */
    fun frasesPendientes(yo: Long, token: String? = null): List<FraseComunidad> {
        equipo.requiere(yo, "gestionar_frases", token)
        return b.usar {
            leer("SELECT f.id, f.texto, f.idioma, COALESCE(c.usuario, '') FROM frases_propuestas f LEFT JOIN cuentas c ON c.id = f.cuenta WHERE f.estado = 'pendiente' ORDER BY f.id LIMIT 100") {
                FraseComunidad("c${it.getLong(1)}", it.getString(2), it.getString(3), it.getString(4))
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
        val id = b.usar { insertar("INSERT INTO frases_propuestas(cuenta, texto, idioma, estado, cuando, franja) VALUES(?,?,?,'aprobada',?,?)", yo, t, f.idioma, ahora(), franja) }
        equipo.registrar(yo.toString(), "frase_agregar", "c$id", t, token)
        return FraseComunidad("c$id", t, f.idioma, franja = franja)
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
        val id = b.usar { insertar("INSERT INTO mensajes(de, para, cuando, sobre, firma) VALUES(?,?,?,?,?)", yo, otro, ahora(), s.sobre, s.firma) }
        timbre(otro).value = id   // quien espera (la app abierta) se entera al instante
        return id
    }

    /** Un timbre por cuenta: suena cuando le llega un mensaje (lo escucha [esperarMensajes]). */
    private val timbres = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.flow.MutableStateFlow<Long>>()
    private fun timbre(cuenta: Long) = timbres.getOrPut(cuenta) { kotlinx.coroutines.flow.MutableStateFlow(0L) }

    /**
     * Como [recibir], pero si no hay nada nuevo espera hasta [espera] milisegundos a que llegue algo (la app abierta
     * deja esta pregunta hecha y el mensaje le llega al instante, sin servicios de terceros).
     */
    suspend fun esperarMensajes(yo: Long, desde: Long, espera: Long = 25_000): List<Sobre> {
        val antes = timbre(yo).value   // se mira antes de revisar: lo que llegue después cambia el número
        val ya = recibir(yo, desde)
        if (ya.isNotEmpty()) return ya
        kotlinx.coroutines.withTimeoutOrNull(espera) { timbre(yo).first { it != antes } }
        return recibir(yo, desde)
    }

    /** Los sobres que le llegaron después de [desde]; los anteriores (ya recibidos) y los de más de 30 días se borran. */
    fun recibir(yo: Long, desde: Long): List<Sobre> {
        b.usar {
            cambiar("DELETE FROM mensajes WHERE para = ? AND id <= ?", yo, desde)
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
        b.usar { cambiar("INSERT INTO avisos(para, de, tipo, objeto, cuando) VALUES(?,?,?,?,?)", para, de, tipo, objeto, ahora()) }
    }

    fun avisos(yo: Long, antes: Long?): List<Aviso> = b.usar {
        leer("SELECT id, de, tipo, objeto, cuando, leido FROM avisos WHERE para = ? AND cuando < ? ORDER BY cuando DESC LIMIT 60", yo, antes ?: Long.MAX_VALUE) {
            arrayOf(it.getLong(1), it.getLong(2), it.getString(3), it.getString(4), it.getLong(5), it.getInt(6) == 1)
        }
    }.filter { !bloqueado(yo, it[1] as Long) }
        .filter { visible(it[1] as Long) }.map { Aviso(it[0] as Long, it[2] as String, persona(it[1] as Long), it[3] as String, it[4] as Long, it[5] as Boolean) }

    fun sinLeer(yo: Long): Int = b.usar { leer("SELECT COUNT(*) FROM avisos WHERE de IN (SELECT id FROM cuentas WHERE borrar_el = 0 AND cerrada = 0) AND para = ? AND leido = 0", yo) { it.getInt(1) }.first() }

    fun leidos(yo: Long) { b.usar { cambiar("UPDATE avisos SET leido = 1 WHERE para = ?", yo) } }

    /** Borra un aviso ([id]) o todos (null). */
    fun borrarAvisos(yo: Long, id: Long?) {
        b.usar { if (id == null) cambiar("DELETE FROM avisos WHERE para = ?", yo) else cambiar("DELETE FROM avisos WHERE para = ? AND id = ?", yo, id) }
    }

    /* ----- Reportes y moderación ----- */

    /**
     * Un reporte. [NuevoReporte.motivo] empieza por uno de [Moderacion.MOTIVOS] ("acoso: …"). Si lo reportan
     * [Moderacion.UMBRAL] cuentas distintas que cuentan (más de 7 días y sin sanción), o una sola por un menor en riesgo,
     * se oculta mientras se revisa (su autor lo sigue viendo).
     */
    fun reportar(yo: Long?, r: NuevoReporte) {
        if (r.que !in setOf("cuenta", "publicacion", "comentario", "mensaje", "imagen")) throw Problema(400, "que")
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
                "publicacion" -> cambiar("UPDATE publicaciones SET oculta = ? WHERE id = ?", if (si) 1 else 0, id)
                "comentario" -> cambiar("UPDATE comentarios SET oculto = ? WHERE id = ?", if (si) 1 else 0, id)
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
            val tabla = when (que) { "publicacion" -> "publicaciones"; "comentario" -> "comentarios"; else -> null }
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
            "comentario" -> leer("SELECT texto, cuenta, oculto FROM comentarios WHERE id = ? AND estado != 'preservado'", id) { Triple(it.getString(1), it.getLong(2), it.getInt(3) == 1) }.firstOrNull()
            "cuenta" -> leer("SELECT perfil, id FROM cuentas WHERE usuario = ?", objeto) { Triple(it.getString(1), it.getLong(2), false) }.firstOrNull()
            "imagen" -> Triple(objeto, null, leer("SELECT 1 FROM imagenes_bloqueadas WHERE url = ?", objeto) { 1 }.isNotEmpty())
            // Un mensaje va cifrado: lo único que se ve es lo que mandó quien lo reportó (con su permiso).
            else -> Triple(motivos.joinToString("\n---\n"), leer("SELECT id FROM cuentas WHERE usuario = ?", objeto.substringBefore(':')) { it.getLong(1) }.firstOrNull(), false)
        } ?: Triple("(ya no está)", null, false)
    }

    /**
     * Lo que decide quien modera: "sin_falta" (vuelve a verse), "falta" (se quita y suma 1), "falta_grave" (suma 2)
     * o "cerrar" (se quita y se cierra la cuenta). A la persona le llega un aviso con la regla.
     */
    fun decidir(yo: Long, d: Decision, token: String? = null): Unit = b.transaccion { decidirInterno(yo, d, token) }

    private fun decidirInterno(yo: Long, d: Decision, token: String? = null) {
        equipo.requiere(yo, "sancionar", token)
        if (d.que !in setOf("cuenta", "publicacion", "comentario", "mensaje")) throw Problema(400, "que")
        if (d.decision !in setOf("sin_falta", "falta", "falta_grave", "cerrar")) throw Problema(400, "decision")
        val (_, autor, _) = verReportado(d.que, d.objeto, emptyList())
        if (autor != null) equipo.inferior(yo, autor)
        if (b.usar { leer("SELECT 1 FROM reportes WHERE que = ? AND objeto = ? AND de = ?", d.que, d.objeto, yo) { 1 }.isNotEmpty() }) throw Problema(403, "reporte_propio")
        val consumidos = b.usar {
            cambiar("UPDATE reportes SET estado = ? WHERE que = ? AND objeto = ? AND estado = 'abierto'", if (d.decision == "sin_falta") "sin_falta" else "confirmado", d.que, d.objeto)
        }
        if (consumidos == 0) throw Problema(409, "ya_resuelto")
        if (d.decision == "falta_grave") equipo.requiere(yo, "sancion_grave", token)
        if (d.decision != "sin_falta" && (d.decision == "cerrar" || d.regla in Moderacion.CIERRE_DIRECTO || (autor != null && puntosVigentes(autor) + (if (d.decision == "falta_grave") 2 else 1) >= 4))) equipo.requiere(yo, "cerrar_cuentas", token)
        equipo.registrar(yo.toString(), "reporte_decidir", d.que + ":" + d.objeto, d.decision + ":" + d.regla, token)

        b.usar {
            cambiar("INSERT INTO decisiones(admin, que, objeto, decision, regla, cuando) VALUES(?,?,?,?,?,?)", yo, d.que, d.objeto, d.decision, d.regla.take(100), ahora())
        }
        if (d.decision == "sin_falta") { ocultarReportado(d.que, d.objeto, false); return }
        equipo.registrar(yo.toString(), "contenido_retirar", d.que + ":" + d.objeto, d.regla, token)
        // Se quita lo reportado.
        val id = d.objeto.toLongOrNull()
        b.usar {
            val retirados = when (d.que) {
                "publicacion" -> cambiar("UPDATE publicaciones SET estado = 'retirado', retirado_hasta = ? WHERE id = ? AND estado = 'visible'", ahora() + 90 * DIA, id)
                "comentario" -> cambiar("UPDATE comentarios SET estado = 'retirado', retirado_hasta = ? WHERE id = ? AND estado = 'visible'", ahora() + 90 * DIA, id)
                else -> 1
            }
            if (retirados == 0) throw Problema(409, "ya_resuelto")
            if (d.que == "publicacion") marcarRetirada(id)
        }
        autor ?: return
        val puntos = when { d.decision == "cerrar" || d.regla in Moderacion.CIERRE_DIRECTO -> 99; d.decision == "falta_grave" -> 2; else -> 1 }
        b.usar { cambiar("INSERT INTO faltas(cuenta, puntos, regla, cuando, vence) VALUES(?,?,?,?,?)", autor, puntos, d.regla.ifBlank { d.que }, ahora(), ahora() + Moderacion.VENCE) }
        aplicarSancion(autor, yo, d.regla)
        equipo.registrar(yo.toString(), "sancion_dar", autor.toString(), d.regla, token)
    }

    private fun marcarRetirada(id: Long?) = b.usar {
        cambiar("INSERT OR IGNORE INTO retiradas(cuenta, clave, cuando) SELECT cuenta, clave, ? FROM publicaciones WHERE id = ? AND estado = 'retirado'", ahora(), id)
    }

    fun retirar(yo: Long, d: ContenidoEquipo, token: String?) = b.transaccion {
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
            if (d.que == "publicacion") cambiar("DELETE FROM retiradas WHERE (cuenta, clave) IN (SELECT cuenta, clave FROM publicaciones WHERE id = ? AND estado = 'retirado')", d.objeto.toLongOrNull())
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

    private fun puntosVigentes(cuenta: Long) = b.usar { leer("SELECT coalesce(sum(puntos), 0) FROM faltas WHERE cuenta = ? AND vence > ?", cuenta, ahora()) { it.getInt(1) }.first() }

    private fun aplicarSancion(cuenta: Long, admin: Long, regla: String) {
        val p = puntosVigentes(cuenta)
        b.usar {
            when {
                p >= 4 -> cambiar("UPDATE cuentas SET cerrada = 1 WHERE id = ?", cuenta)
                p == 3 -> cambiar("UPDATE cuentas SET suspendida_hasta = ? WHERE id = ?", ahora() + 7L * 24 * 3600_000, cuenta)
                p == 2 -> cambiar("UPDATE cuentas SET restringida_hasta = ? WHERE id = ?", ahora() + 24 * 3600_000L, cuenta)
            }
        }
        avisar(cuenta, admin, "sancion", regla)
    }

    /** Las faltas vigentes y las restricciones de la cuenta ("Estado de tu cuenta"). */
    fun estadoCuenta(yo: Long): EstadoCuenta {
        val faltas = b.usar { leer("SELECT regla, puntos, cuando, vence FROM faltas WHERE cuenta = ? AND vence > ? ORDER BY cuando DESC", yo, ahora()) { Falta(it.getString(1), it.getInt(2), it.getLong(3), it.getLong(4)) } }
        return b.usar { leer("SELECT restringida_hasta, suspendida_hasta, cerrada, admin FROM cuentas WHERE id = ?", yo) {
            EstadoCuenta(faltas, it.getLong(1), it.getLong(2), it.getInt(3) == 1, equipo.rol(yo).isNotBlank()) }.first() }
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
        const val POLITICA = "2026-11-08"
        /** Los votos que necesita una alternativa (y más de la mitad) para ser la imagen de la comunidad. */
        const val VOTOS_COMUNIDAD = 5
        val DOMINIOS_IMAGEN = setOf("image.tmdb.org", "covers.openlibrary.org", "coverartarchive.org", "upload.wikimedia.org")
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
