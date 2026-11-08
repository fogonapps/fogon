package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable data class Factor(val codigo: String = "", val recuperacion: String = "")
@Serializable data class Inscripcion(val secreto: String, val uri: String)
@Serializable data class Recuperacion(val codigos: List<String>)
@Serializable data class Elevacion(val elevadaHasta: Long)
@Serializable data class CambioEquipo(val usuario: String, val rol: String = "", val permiso: String = "", val codigo: String = "")
@Serializable data class Miembro(val cuenta: Long, val usuario: String, val rol: String, val permisos: Set<String>)
@Serializable data class EntradaRegistro(val id: Long, val actor: String, val accion: String, val objeto: String, val detalle: String, val sesion: String, val cuando: Long, val huellaAnterior: String, val huella: String)
@Serializable data class RegistroEquipo(val entradas: List<EntradaRegistro>, val cadenaIntegra: Boolean, val siguiente: Long?)
@Serializable data class ContenidoEquipo(val que: String, val objeto: String, val motivo: String = "")
@Serializable data class SancionEquipo(val usuario: String, val motivo: String)

/** RFC 6238: contador de 64 bits, truncamiento dinámico y HMAC-SHA1. */
object Totp {
    private const val ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    fun base32(bytes: ByteArray): String = buildString {
        var bits = 0; var acumulado = 0
        for (b in bytes) {
            acumulado = (acumulado shl 8) or (b.toInt() and 255); bits += 8
            while (bits >= 5) { bits -= 5; append(ALFABETO[(acumulado ushr bits) and 31]) }
        }
        if (bits > 0) append(ALFABETO[(acumulado shl (5 - bits)) and 31])
    }
    fun decodificar(texto: String): ByteArray {
        var bits = 0; var acumulado = 0
        return buildList<Byte> {
            for (c in texto) {
                val v = ALFABETO.indexOf(c); require(v >= 0)
                acumulado = (acumulado shl 5) or v; bits += 5
                if (bits >= 8) { bits -= 8; add((acumulado ushr bits).toByte()) }
            }
        }.toByteArray()
    }
    fun codigo(secreto: ByteArray, paso: Long, digitos: Int = 6): String {
        require(digitos in 6..8)
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secreto, "HmacSHA1")) }
        val h = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array())
        val i = h.last().toInt() and 15
        val n = ByteBuffer.wrap(h, i, 4).int and Int.MAX_VALUE
        val modulo = if (digitos == 8) 100_000_000 else if (digitos == 7) 10_000_000 else 1_000_000
        return (n % modulo).toString().padStart(digitos, '0')
    }
}

/** Autorización y segundo factor. Todas las operaciones comparten el cerrojo de la conexión. */
class Equipo(private val b: Base, datos: File, private val ahora: () -> Long, private val cartero: Cartero, principal: String?, configurarPrincipal: Boolean = true) {
    companion object {
        const val DURACION = 30L * 86400_000
        val TODOS = setOf("ver_reportes", "sancionar", "sancion_grave", "cerrar_cuentas", "moderar_comentarios", "gestionar_publicaciones", "gestionar_frases", "gestionar_usuarios", "asignar_equipo", "ver_registro")
        val MODERADOR = setOf("ver_reportes", "sancionar", "sancion_grave", "moderar_comentarios", "gestionar_publicaciones")
    }
    private val llave: ByteArray
    init {
        datos.mkdirs()
        val ruta = File(datos, "llave-mfa").toPath()
        if (!Files.exists(ruta)) {
            try {
                Files.createFile(ruta, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                Files.write(ruta, ByteArray(32).also { SecureRandom().nextBytes(it) })
            } catch (e: java.nio.file.FileAlreadyExistsException) { /* Otro arranque la creó. */ }
        }
        llave = Files.readAllBytes(ruta)
        require(llave.size == 32) { "llave-mfa inválida" }
        Files.setPosixFilePermissions(ruta, PosixFilePermissions.fromString("rw-------"))
        if (configurarPrincipal) b.transaccion {
            // Una PRINCIPAL que no existe (un error al escribirla) no puede dejar el servicio caído: se avisa y se sigue sin ella.
            val id = if (principal.isNullOrBlank()) null else leer("SELECT id FROM cuentas WHERE usuario = ?", principal) { it.getLong(1) }.singleOrNull()
                .also { if (it == null) System.err.println("PRINCIPAL=$principal no corresponde a ninguna cuenta: se sigue sin cuenta principal.") }
            leer("SELECT cuenta FROM roles WHERE rol = 'principal' AND cuenta != ?", id ?: -1L) { it.getLong(1) }.forEach {
                cambiar("UPDATE roles SET rol = 'administrador', dado_por = 'servidor', cuando = ? WHERE cuenta = ?", ahora(), it)
                registrar("servidor", "principal_retirar", it.toString(), "Configuración local")
            }
            if (id != null && rol(id) != "principal") {
                cambiar("INSERT INTO roles VALUES(?, 'principal', 'servidor', ?) ON CONFLICT(cuenta) DO UPDATE SET rol = 'principal', dado_por = 'servidor', cuando = excluded.cuando", id, ahora())
                registrar("servidor", "principal_dar", id.toString(), "Configuración local")
            }
        }
    }
    fun rol(yo: Long): String = b.usar { leer("SELECT rol FROM roles WHERE cuenta = ?", yo) { it.getString(1) }.firstOrNull() ?: "" }
    fun permisos(yo: Long): Set<String> = when (rol(yo)) {
        "principal", "administrador" -> TODOS
        "moderador" -> MODERADOR
        else -> emptySet()
    } + b.usar { leer("SELECT permiso FROM permisos WHERE cuenta = ?", yo) { it.getString(1) }.toSet() }
    fun nivel(yo: Long) = when (rol(yo)) { "principal" -> 4; "administrador" -> 3; "moderador" -> 2; "equipo" -> 1; else -> if (permisos(yo).isEmpty()) 0 else 1 }
    fun activa(yo: Long) = b.usar { leer("SELECT activa FROM mfa WHERE cuenta = ?", yo) { it.getInt(1) == 1 }.firstOrNull() ?: false }
    fun elevada(yo: Long, token: String?): Long = b.usar { leer("SELECT elevada_hasta FROM sesiones WHERE cuenta = ? AND huella = ?", yo, Base.huella(token.orEmpty())) { it.getLong(1) }.firstOrNull() ?: 0 }
    fun requiere(yo: Long, permiso: String, token: String?) {
        if (permiso !in permisos(yo)) throw Problema(403, "sin_permiso")
        if (!activa(yo)) throw Problema(403, "mfa_requerida")
        if (elevada(yo, token) <= ahora()) throw Problema(403, "elevacion_requerida")
    }
    fun inferior(actor: Long, objeto: Long) {
        if (actor == objeto || nivel(objeto) >= nivel(actor)) throw Problema(403, "nivel_protegido")
    }
    private fun cifrar(bytes: ByteArray): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(llave, "AES"), GCMParameterSpec(128, iv))
        return Base64.getEncoder().encodeToString(iv + c.doFinal(bytes))
    }
    private fun descifrar(texto: String): ByteArray {
        val bytes = Base64.getDecoder().decode(texto)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(llave, "AES"), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return c.doFinal(bytes.copyOfRange(12, bytes.size))
    }
    private data class IntentoMfa(val fallos: Int, val desde: Long, val hasta: Long, val nivel: Int, val ultimo: Long)
    private fun intentoMfa(yo: Long): IntentoMfa? = b.usar {
        leer("SELECT fallos, desde, bloqueada_hasta, nivel, ultimo_fallo FROM intentos_mfa WHERE cuenta = ?", yo) {
            IntentoMfa(it.getInt(1), it.getLong(2), it.getLong(3), it.getInt(4), it.getLong(5))
        }.singleOrNull()?.takeIf { ahora() - it.ultimo < 86400_000L }
    }
    private fun revisarIntentos(yo: Long) {
        if ((intentoMfa(yo)?.hasta ?: 0) > ahora()) throw Problema(429, "mfa_bloqueada")
    }
    /** Registra el fallo después del rollback, sin soltar el cerrojo entre comprobación y escritura. */
    private fun <T> operacionMfa(yo: Long, f: java.sql.Connection.() -> T): T = b.usar {
        try { b.transaccion(f) }
        catch (e: Problema) {
            if (e.mensaje in setOf("codigo_mfa", "recuperacion")) {
                val anterior = intentoMfa(yo)
                val fallos = if (anterior != null && ahora() - anterior.desde < 15 * 60_000 && anterior.hasta == 0L) anterior.fallos + 1 else 1
                val nivel = anterior?.nivel ?: 0
                val espera = if (fallos >= 5) listOf(1, 5, 15, 60)[nivel.coerceAtMost(3)] * 60_000L else 0L
                cambiar("INSERT OR REPLACE INTO intentos_mfa VALUES(?,?,?,?,?,?)", yo, fallos,
                    if (fallos > 1) anterior!!.desde else ahora(), if (espera == 0L) 0 else ahora() + espera,
                    if (espera == 0L) nivel else (nivel + 1).coerceAtMost(4), ahora())
            }
            throw e
        }
    }
    fun activar(yo: Long, f: Factor, token: String?): Inscripcion = operacionMfa(yo) {
        if (activa(yo)) {
            if (f.recuperacion.isNotBlank()) consumirRecuperacion(yo, f.recuperacion) else validar(yo, f.codigo)
            restablecer(yo, yo.toString(), token)
        }
        val secreto = ByteArray(20).also { SecureRandom().nextBytes(it) }
        cambiar("INSERT INTO mfa VALUES(?,?,0,?,-1) ON CONFLICT(cuenta) DO UPDATE SET secreto_cifrado = excluded.secreto_cifrado, creada = excluded.creada, ultimo_paso = -1", yo, cifrar(secreto), ahora())
        val usuario = leer("SELECT usuario FROM cuentas WHERE id = ?", yo) { it.getString(1) }.single()
        registrar(yo.toString(), "mfa_preparar", yo.toString(), "", token)
        val base32 = Totp.base32(secreto)
        Inscripcion(base32, "otpauth://totp/Fogón:$usuario?secret=$base32&issuer=Fogón")
    }
    /** El cerrojo de la conexión impide repetir un paso entre sesiones concurrentes. */
    private fun validar(yo: Long, codigo: String, pendiente: Boolean = false) = b.usar {
        revisarIntentos(yo)
        if (!Regex("[0-9]{6}").matches(codigo)) throw Problema(400, "codigo_mfa")
        val fila = leer("SELECT secreto_cifrado, ultimo_paso, activa FROM mfa WHERE cuenta = ?", yo) { Triple(it.getString(1), it.getLong(2), it.getInt(3)) }.singleOrNull() ?: throw Problema(403, "mfa_requerida")
        if (!pendiente && fila.third != 1) throw Problema(403, "mfa_requerida")
        val secreto = descifrar(fila.first)
        val paso = (ahora() / 30_000 - 1..ahora() / 30_000 + 1).firstOrNull { it > fila.second && Totp.codigo(secreto, it) == codigo } ?: throw Problema(400, "codigo_mfa")
        cambiar("UPDATE mfa SET ultimo_paso = ? WHERE cuenta = ?", paso, yo)
        cambiar("DELETE FROM intentos_mfa WHERE cuenta = ?", yo)
        Unit
    }
    fun confirmar(yo: Long, f: Factor, token: String?): Recuperacion = operacionMfa(yo) {
        if (activa(yo)) throw Problema(409, "mfa_activa")
        validar(yo, f.codigo, pendiente = true)
        cambiar("UPDATE mfa SET activa = 1 WHERE cuenta = ?", yo)
        registrar(yo.toString(), "mfa_activar", yo.toString(), "", token)
        avisar(yo, "MFA activada")
        generarRecuperacion(yo)
    }
    private fun generarRecuperacion(yo: Long): Recuperacion = b.usar {
        cambiar("DELETE FROM codigos_recuperacion WHERE cuenta = ?", yo)
        val codigos = List(10) { codigoRecuperacion() }
        codigos.forEach { cambiar("INSERT INTO codigos_recuperacion VALUES(?,?,0)", yo, Base.huella(normalizarRecuperacion(it))) }
        Recuperacion(codigos)
    }
    fun regenerar(yo: Long, f: Factor, token: String?): Recuperacion = operacionMfa(yo) {
        validar(yo, f.codigo)
        registrar(yo.toString(), "mfa_recuperacion", yo.toString(), "", token)
        avisar(yo, "Códigos de recuperación renovados")
        generarRecuperacion(yo)
    }
    private fun consumirRecuperacion(yo: Long, codigo: String) = b.usar {
        revisarIntentos(yo)
        // Los códigos de antes (22 caracteres con mayúsculas y minúsculas) se comparan tal cual; los nuevos, normalizados.
        val usado = cambiar("UPDATE codigos_recuperacion SET usado = 1 WHERE cuenta = ? AND huella IN (?, ?) AND usado = 0",
            yo, Base.huella(codigo.trim()), Base.huella(normalizarRecuperacion(codigo)))
        if (usado != 1) throw Problema(400, "recuperacion")
        cambiar("DELETE FROM intentos_mfa WHERE cuenta = ?", yo)
    }
    fun elevar(yo: Long, f: Factor, token: String?): Elevacion = operacionMfa(yo) {
        if (!activa(yo)) throw Problema(403, "mfa_requerida")
        if (f.recuperacion.isNotBlank() && f.codigo.isBlank()) {
            consumirRecuperacion(yo, f.recuperacion)
        } else validar(yo, f.codigo)
        val hasta = ahora() + DURACION
        if (cambiar("UPDATE sesiones SET elevada_hasta = ? WHERE cuenta = ? AND huella = ?", hasta, yo, Base.huella(token.orEmpty())) != 1) throw Problema(401, "sin_cuenta")
        registrar(yo.toString(), "mfa_elevar", yo.toString(), "", token)
        Elevacion(hasta)
    }
    fun desactivar(yo: Long, f: Factor, token: String?) = operacionMfa(yo) {
        validar(yo, f.codigo)
        restablecer(yo, yo.toString(), token)
    }
    private fun restablecer(yo: Long, actor: String, token: String?) = b.usar {
        cambiar("DELETE FROM mfa WHERE cuenta = ?", yo)
        cambiar("DELETE FROM codigos_recuperacion WHERE cuenta = ?", yo)
        cambiar("UPDATE sesiones SET elevada_hasta = 0 WHERE cuenta = ?", yo)
        registrar(actor, "mfa_restablecer", yo.toString(), "", token)
        avisar(yo, "MFA restablecida: debes inscribirla de nuevo")
    }
    fun listar(yo: Long, token: String?): List<Miembro> {
        requiere(yo, "asignar_equipo", token)
        return b.usar { leer("SELECT c.id, c.usuario, r.rol FROM roles r JOIN cuentas c ON c.id = r.cuenta") { Miembro(it.getLong(1), it.getString(2), it.getString(3), permisos(it.getLong(1))) } }
    }
    fun cambiarEquipo(yo: Long, d: CambioEquipo, quitar: Boolean, token: String?) = operacionMfa(yo) {
        requiere(yo, "asignar_equipo", token)
        val destino = cuenta(d.usuario)
        if (destino == yo) throw Problema(403, "equipo_propio")
        inferior(yo, destino)
        if (rol(destino) == "principal" || d.rol == "principal") throw Problema(403, "cuenta_protegida")
        if ((rol(destino) == "administrador" || d.rol == "administrador") && rol(yo) != "principal") throw Problema(403, "solo_principal")
        if ((d.rol.isBlank()) == (d.permiso.isBlank())) throw Problema(400, "equipo")
        if (d.rol.isNotBlank()) {
            if (d.rol !in setOf("administrador", "moderador", "equipo")) throw Problema(400, "rol")
            val concedidos = when (d.rol) { "administrador" -> TODOS; "moderador" -> MODERADOR; else -> emptySet() }
            if (!permisos(yo).containsAll(concedidos)) throw Problema(403, "sin_permiso")
        } else if (d.permiso !in TODOS || d.permiso !in permisos(yo)) throw Problema(403, "sin_permiso")
        validar(yo, d.codigo)
        if (d.rol.isNotBlank()) {
            if (quitar) {
                cambiar("DELETE FROM roles WHERE cuenta = ? AND rol = ?", destino, d.rol)
                if (leer("SELECT 1 FROM permisos WHERE cuenta = ?", destino) { 1 }.isNotEmpty())
                    cambiar("INSERT OR IGNORE INTO roles VALUES(?, 'equipo', ?, ?)", destino, yo.toString(), ahora())
            }
            else cambiar("INSERT INTO roles VALUES(?,?,?,?) ON CONFLICT(cuenta) DO UPDATE SET rol = excluded.rol, dado_por = excluded.dado_por, cuando = excluded.cuando", destino, d.rol, yo.toString(), ahora())
        } else {
            if (quitar) cambiar("DELETE FROM permisos WHERE cuenta = ? AND permiso = ?", destino, d.permiso)
            else {
                cambiar("INSERT OR IGNORE INTO roles VALUES(?, 'equipo', ?, ?)", destino, yo.toString(), ahora())
                cambiar("INSERT OR REPLACE INTO permisos VALUES(?,?,?,?)", destino, d.permiso, yo.toString(), ahora())
            }
        }
        registrar(yo.toString(), if (quitar) "equipo_quitar" else "equipo_dar", destino.toString(), d.rol + d.permiso, token)
        avisar(destino, "Tus roles o permisos cambiaron")
        leer("SELECT cuenta FROM roles WHERE rol = 'principal'") { it.getLong(1) }.forEach { avisar(it, "Roles o permisos del equipo cambiaron") }
    }
    private fun cuenta(usuario: String): Long = b.usar { leer("SELECT id FROM cuentas WHERE usuario = ?", usuario) { it.getLong(1) }.singleOrNull() ?: throw Problema(404, "no_existe") }
    fun registrar(actor: String, accion: String, objeto: String, detalle: String, token: String? = null) = b.usar {
        registrarEquipo(actor, accion, objeto, detalle, token?.let { Base.huella(it) }.orEmpty(), ahora())
    }
    private fun filas(actor: String = "", accion: String = "", despues: Long = 0, limite: Int = 100) = b.usar {
        leer("SELECT * FROM registro_equipo WHERE id > ? AND (? = '' OR actor = ?) AND (? = '' OR accion = ?) ORDER BY id LIMIT ?", despues, actor, actor, accion, accion, limite) {
            EntradaRegistro(it.getLong("id"), it.getString("actor"), it.getString("accion"), it.getString("objeto"), it.getString("detalle"), it.getString("sesion"), it.getLong("cuando"), it.getString("huella_anterior"), it.getString("huella"))
        }
    }
    fun registro(yo: Long, token: String?, actor: String, accion: String, despues: Long) : List<EntradaRegistro> {
        requiere(yo, "ver_registro", token)
        return filas(actor, accion, despues)
    }
    fun registroPaginado(yo: Long, token: String?, actor: String, accion: String, despues: Long): RegistroEquipo = b.transaccion {
        val entradas = registro(yo, token, actor, accion, despues)
        RegistroEquipo(entradas, verificarCadena(), entradas.lastOrNull()?.id?.takeIf { entradas.size == 100 })
    }
    fun verificarCadena(): Boolean = b.usar {
        var anterior = ""; var cursor = 0L
        while (true) {
            val lote = filas(despues = cursor)
            if (lote.isEmpty()) break
            for (e in lote) {
                if (e.huellaAnterior != anterior || e.huella != huellaRegistro(e)) return@usar false
                anterior = e.huella; cursor = e.id
            }
        }
        true
    }
    fun ordenLocal(args: Array<String>) = b.transaccion {
        require(args.size in 2..3) { "Uso: restablecer-mfa <usuario> | cambiar-correo <usuario> <correo>" }
        val yo = cuenta(args[1])
        when (args[0]) {
            "restablecer-mfa" -> { require(args.size == 2); restablecer(yo, "servidor", null) }
            "cambiar-correo" -> {
                require(args.size == 3)
                val nuevo = args[2].trim().lowercase()
                require(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(nuevo) && nuevo.length <= 120) { "Correo inválido" }
                if (leer("SELECT id FROM cuentas WHERE correo = ? AND id != ?", nuevo, yo) { it.getLong(1) }.isNotEmpty()) throw Problema(409, "correo_tomado")
                avisar(yo, "El servidor cambiará el correo de tu cuenta")
                cambiar("UPDATE cuentas SET correo = ? WHERE id = ?", nuevo, yo)
                cambiar("DELETE FROM cambios_correo WHERE cuenta = ?", yo)
                cambiar("DELETE FROM sesiones WHERE cuenta = ?", yo)
                registrar("servidor", "correo_cambiar", yo.toString(), "Cambio local")
                avisar(yo, "El servidor cambió el correo de tu cuenta")
            }
            else -> error("Orden local desconocida")
        }
    }
    private fun avisar(yo: Long, asunto: String) {
        val correo = b.usar { leer("SELECT correo FROM cuentas WHERE id = ?", yo) { it.getString(1) }.singleOrNull() }
        if (!correo.isNullOrBlank()) try { cartero.enviar(correo, asunto, "$asunto. Si no lo esperabas, contacta con Fogón.") }
        catch (_: Exception) { System.err.println("No se pudo enviar el aviso del equipo") }
    }
}

internal fun huellaRegistro(e: EntradaRegistro) = Base.huella(Json.encodeToString(listOf(e.id.toString(), e.actor, e.accion, e.objeto, e.detalle, e.sesion, e.cuando.toString(), e.huellaAnterior)))

internal fun java.sql.Connection.registrarEquipo(actor: String, accion: String, objeto: String, detalle: String, sesion: String, cuando: Long) {
    val anterior = leer("SELECT huella FROM registro_equipo ORDER BY id DESC LIMIT 1") { it.getString(1) }.firstOrNull().orEmpty()
    val id = leer("SELECT COALESCE(MAX(id),0)+1 FROM registro_equipo") { it.getLong(1) }.single()
    val e = EntradaRegistro(id, actor, accion, objeto, detalle, sesion, cuando, anterior, "")
    cambiar("INSERT INTO registro_equipo VALUES(?,?,?,?,?,?,?,?,?)", e.id, e.actor, e.accion, e.objeto, e.detalle, e.sesion, e.cuando, e.huellaAnterior, huellaRegistro(e))
}

/** Sin letras que se confunden (I, L, O, U): 16 de 32 símbolos son 80 bits, de sobra para un código de un solo uso con límite de intentos. */
private const val ALFABETO_RECUPERACION = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

internal fun codigoRecuperacion(azar: java.security.SecureRandom = java.security.SecureRandom()): String =
    (1..16).map { ALFABETO_RECUPERACION[azar.nextInt(32)] }.chunked(4).joinToString("-") { it.joinToString("") }

/** Acepta minúsculas, espacios y guiones, y lee O como 0 e I o L como 1. */
internal fun normalizarRecuperacion(codigo: String): String = codigo.uppercase().filter { it.isLetterOrDigit() }
    .replace('O', '0').replace('I', '1').replace('L', '1')
