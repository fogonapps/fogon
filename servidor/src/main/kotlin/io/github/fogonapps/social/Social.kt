package io.github.fogonapps.social

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
)

@Serializable
data class Comentario(val id: Long, val autor: Persona, val texto: String, val cuando: Long, val puedoBorrar: Boolean)

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
)
@Serializable data class Acceso(val usuario: String, val token: String, val nueva: Boolean)
@Serializable data class MiCuenta(val usuario: String, val nombre: String, val correo: String, val privada: Boolean)
@Serializable data class CuentaCreada(val usuario: String, val token: String)
@Serializable data class Ajustes(val privada: Boolean? = null)
@Serializable data class NuevoComentario(val texto: String)
@Serializable data class NuevoReporte(val que: String, val objeto: String, val motivo: String)
@Serializable data class ImagenSubida(val nombre: String)
/** Una nota de 24 horas: unas palabras y, si quiere, una obra (como en la publicación: titulo, portada…). */
@Serializable data class NotaNueva(val texto: String, val datos: JsonObject = JsonObject(emptyMap()))
@Serializable data class Nota(val persona: Persona, val texto: String, val datos: JsonObject, val cuando: Long)
/** Las llaves públicas de una cuenta (keysets de Tink en JSON, en base64): para cifrar y para comprobar la firma. */
@Serializable data class Llaves(val cifrar: String, val firmar: String)
/** Un mensaje cifrado para alguien: [sobre] es el texto cifrado (base64) y [firma], la firma de quien lo manda. */
@Serializable data class SobreNuevo(val sobre: String, val firma: String)
@Serializable data class Sobre(val id: Long, val de: Persona, val cuando: Long, val sobre: String, val firma: String)
@Serializable data class Error(val error: String)

class Problema(val codigo: Int, val mensaje: String) : Exception(mensaje)

/** Todo lo que hace el servidor, sin HTTP (así se prueba directo). [ahora] se puede cambiar en las pruebas. */
class Social(
    private val b: Base,
    private val dirFotos: java.io.File,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val cartero: Cartero = Cartero { _, _, _ -> throw Problema(503, "sin_correo") },
) {

    private val dirImagenes get() = java.io.File(dirFotos, "i")

    /** El secreto de las huellas de las vistas: al azar, una vez, en la carpeta de datos (no sale del servidor). */
    private val secreto: ByteArray by lazy {
        val f = java.io.File(dirFotos.parentFile ?: dirFotos, "secreto")
        if (f.exists() && f.length() >= 32) f.readBytes()
        else ByteArray(32).also { java.security.SecureRandom().nextBytes(it); f.parentFile?.mkdirs(); f.writeBytes(it); f.setReadable(false, false); f.setReadable(true, true) }
    }

    /* ----- Cuentas ----- */

    /** Manda un código de 6 números al correo (uno por minuto como mucho). Sirve para entrar o para crear la cuenta. */
    fun pedirCodigo(correoDado: String) {
        val correo = correoDado.trim().lowercase()
        if (!CORREO.matches(correo) || correo.length > 120) throw Problema(400, "correo")
        val antes = b.usar { leer("SELECT enviado FROM codigos WHERE correo = ?", correo) { it.getLong(1) }.firstOrNull() }
        if (antes != null && ahora() - antes < 60_000) throw Problema(429, "despacio")
        val codigo = (0 until 6).map { azar.nextInt(10) }.joinToString("")
        b.usar {
            cambiar("""INSERT INTO codigos(correo, huella, expira, intentos, enviado) VALUES(?,?,?,0,?)
                ON CONFLICT(correo) DO UPDATE SET huella = excluded.huella, expira = excluded.expira, intentos = 0, enviado = excluded.enviado""",
                correo, Base.huella("$correo:$codigo"), ahora() + 10 * 60_000, ahora())
        }
        cartero.enviar(correo, "Tu código de El Cajón: $codigo",
            "Tu código para entrar a El Cajón es:\n\n$codigo\n\nVence en 10 minutos. Si no lo pediste, ignora este correo.\n\nEl Cajón · Fogón")
    }

    /**
     * Entra con el código. Si el correo ya tiene cuenta, abre una sesión nueva. Si no, hace falta elegir el usuario,
     * aceptar la política y los términos ([POLITICA]) y tener 18 años o más; mientras no lleguen, responde
     * "cuenta_nueva" sin gastar el código (la app pregunta y vuelve a llamar).
     */
    fun entrar(e: Entrada): Acceso {
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
                insertar("INSERT INTO cuentas(usuario, token, nombre, creada, cambio, correo, acepto, acepto_cuando) VALUES(?,?,?,?,?,?,?,?)",
                    u, Base.huella(Base.tokenNuevo()), e.nombre.trim().take(40), ahora(), ahora(), correo, POLITICA, ahora())
            }
            Triple(nuevo, u, true)
        }
        b.usar { cambiar("DELETE FROM codigos WHERE correo = ?", correo) }
        return Acceso(usuario, sesionNueva(id), nueva)
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

    /**
     * Crea la cuenta sin correo, cuando el servidor no puede mandar códigos: queda en este teléfono (con su token);
     * después se le puede vincular un correo ([vincularCorreo]) para recuperarla en otro.
     */
    fun crearSinCorreo(e: Entrada): Acceso {
        if (e.acepta != POLITICA) throw Problema(400, "acepta")
        if (!e.mayorDeEdad) throw Problema(400, "edad")
        val u = e.usuario.trim().lowercase()
        if (!USUARIO.matches(u)) throw Problema(400, "usuario")
        if (!usuarioLibre(u)) throw Problema(409, "usuario_tomado")
        val id = b.usar {
            insertar("INSERT INTO cuentas(usuario, token, nombre, creada, cambio, acepto, acepto_cuando) VALUES(?,?,?,?,?,?,?)",
                u, Base.huella(Base.tokenNuevo()), e.nombre.trim().take(40), ahora(), ahora(), POLITICA, ahora())
        }
        return Acceso(u, sesionNueva(id), true)
    }

    /** Le pone a la cuenta el correo de [e] (con su código), para poder entrar desde otro teléfono. */
    fun vincularCorreo(yo: Long, e: Entrada) {
        val correo = e.correo.trim().lowercase()
        codigoBueno(correo, e.codigo)
        if (b.usar { leer("SELECT 1 FROM cuentas WHERE correo = ? AND id <> ?", correo, yo) { 1 }.isNotEmpty() }) throw Problema(409, "correo_tomado")
        b.usar {
            cambiar("UPDATE cuentas SET correo = ? WHERE id = ?", correo, yo)
            cambiar("DELETE FROM codigos WHERE correo = ?", correo)
        }
    }

    private fun sesionNueva(cuenta: Long): String {
        val token = Base.tokenNuevo()
        b.usar { cambiar("INSERT INTO sesiones(huella, cuenta, creada) VALUES(?,?,?)", Base.huella(token), cuenta, ahora()) }
        return token
    }

    /** Cierra la sesión de este teléfono. */
    fun salir(token: String?) {
        if (token.isNullOrBlank()) return
        b.usar { cambiar("DELETE FROM sesiones WHERE huella = ?", Base.huella(token)) }
    }

    fun miCuenta(yo: Long): MiCuenta = b.usar {
        leer("SELECT usuario, nombre, coalesce(correo, ''), privada FROM cuentas WHERE id = ?", yo) { MiCuenta(it.getString(1), it.getString(2), it.getString(3), it.getInt(4) == 1) }.first()
    }

    /** Crea una cuenta sin correo (solo para las pruebas: la app entra siempre con [entrar]). */
    internal fun crearCuenta(n: NuevaCuenta): CuentaCreada {
        val usuario = n.usuario.trim().lowercase()
        if (!USUARIO.matches(usuario)) throw Problema(400, "usuario")
        if (usuario in RESERVADOS) throw Problema(409, "usuario_tomado")
        val token = Base.tokenNuevo()
        b.usar {
            if (leer("SELECT 1 FROM cuentas WHERE usuario = ?", usuario) { 1 }.isNotEmpty()) throw Problema(409, "usuario_tomado")
            insertar("INSERT INTO cuentas(usuario, token, nombre, creada, cambio) VALUES(?,?,?,?,?)",
                usuario, Base.huella(token), n.nombre.trim().take(40), ahora(), ahora())
        }
        return CuentaCreada(usuario, token)
    }

    /** Si el usuario está libre (para decirlo mientras la persona lo escribe). */
    fun usuarioLibre(usuario: String): Boolean {
        val u = usuario.trim().lowercase()
        if (!USUARIO.matches(u) || u in RESERVADOS) return false
        return b.usar { leer("SELECT 1 FROM cuentas WHERE usuario = ?", u) { 1 }.isEmpty() }
    }

    /** La cuenta de un token, o null. */
    fun cuentaDe(token: String?): Long? {
        if (token.isNullOrBlank()) return null
        val h = Base.huella(token)
        return b.usar {
            leer("SELECT cuenta FROM sesiones WHERE huella = ?", h) { it.getLong(1) }.firstOrNull()
                ?: leer("SELECT id FROM cuentas WHERE token = ?", h) { it.getLong(1) }.firstOrNull()
        }
    }

    fun ajustar(yo: Long, a: Ajustes) {
        a.privada?.let { privada ->
            b.usar { cambiar("UPDATE cuentas SET privada = ?, cambio = ? WHERE id = ?", if (privada) 1 else 0, ahora(), yo) }
            // Al volverse pública, las solicitudes que esperaban quedan aceptadas.
            if (!privada) b.usar { cambiar("UPDATE seguir SET aceptada = 1 WHERE a = ?", yo) }
        }
    }

    /** Borra la cuenta y todo lo suyo: perfil, publicaciones, a quién sigue, me gusta, comentarios, avisos, imágenes y foto. */
    fun borrarCuenta(yo: Long) {
        val imagenes = b.usar { leer("SELECT nombre FROM imagenes WHERE cuenta = ?", yo) { it.getString(1) } }
        b.usar {
            leer("SELECT correo FROM cuentas WHERE id = ?", yo) { it.getString(1) }.firstOrNull()?.let { cambiar("DELETE FROM codigos WHERE correo = ?", it) }
            cambiar("DELETE FROM cuentas WHERE id = ?", yo)
        }
        java.io.File(dirFotos, "$yo.jpg").delete()
        imagenes.forEach { java.io.File(dirImagenes, "$it.jpg").delete() }
    }

    private fun idDe(usuario: String): Long =
        b.usar { leer("SELECT id FROM cuentas WHERE usuario = ?", usuario.lowercase()) { it.getLong(1) }.firstOrNull() } ?: throw Problema(404, "no_existe")

    private fun persona(id: Long): Persona = b.usar {
        leer("SELECT usuario, nombre, foto, privada FROM cuentas WHERE id = ?", id) { Persona(it.getString(1), it.getString(2), it.getInt(3) == 1, it.getInt(4) == 1) }.first()
    }

    private fun esPrivada(id: Long) = b.usar { leer("SELECT privada FROM cuentas WHERE id = ?", id) { it.getInt(1) == 1 }.firstOrNull() } ?: false

    /* ----- Perfil y foto ----- */

    fun subirPerfil(yo: Long, p: PerfilSubido) {
        val limpio = p.copy(
            nombre = p.nombre.trim().take(40), bio = p.bio.trim().take(200), portada = p.portada.take(500).takeIf { it.startsWith("https://") } ?: "",
            enlaces = p.enlaces.filter { it.startsWith("https://") || it.startsWith("http://") }.map { it.take(200) }.take(5),
            partes = p.partes.filterValues { it.visibilidad in Vis.SUBIBLES }.entries.take(20).associate { it.key.take(30) to it.value },
        )
        val json = JsonSocial.encodeToString(PerfilSubido.serializer(), limpio)
        if (json.length > 200_000) throw Problema(413, "muy_grande")
        b.usar { cambiar("UPDATE cuentas SET nombre = ?, perfil = ?, cambio = ? WHERE id = ?", limpio.nombre, json, ahora(), yo) }
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
        val pubs = if (cerrada) emptyList() else filas(lector, "p.cuenta = ? ORDER BY p.cuando DESC LIMIT 60", id).map { it.second }.filter { ve(it.visibilidad) }
        val (seguidores, siguiendo, cuantas) = b.usar {
            Triple(
                leer("SELECT COUNT(*) FROM seguir WHERE a = ? AND aceptada = 1", id) { it.getInt(1) }.first(),
                leer("SELECT COUNT(*) FROM seguir WHERE de = ? AND aceptada = 1", id) { it.getInt(1) }.first(),
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
    fun imagen(nombre: String): ByteArray {
        if (!Regex("[A-Za-z0-9_-]{22}").matches(nombre)) throw Problema(404, "no_existe")
        return java.io.File(dirImagenes, "$nombre.jpg").takeIf { it.exists() }?.readBytes() ?: throw Problema(404, "no_existe")
    }

    /* ----- Publicaciones ----- */

    /**
     * Sube todas las publicaciones de la persona: lo que ya estaba (por [PublicacionSubida.clave]) se actualiza sin
     * perder sus me gusta ni comentarios, lo nuevo se agrega y lo que ya no viene (lo volvió privado o lo borró) se quita.
     */
    fun subirPublicaciones(yo: Long, lista: List<PublicacionSubida>) {
        if (lista.size > 2000) throw Problema(413, "muy_grande")
        val buenas = lista.filter { it.visibilidad in Vis.SUBIBLES && it.clave.isNotBlank() }.distinctBy { it.clave }
        b.usar {
            autoCommit = false
            try {
                val claves = buenas.map { it.clave.take(80) }.toSet()
                leer("SELECT id, clave FROM publicaciones WHERE cuenta = ?", yo) { it.getLong(1) to it.getString(2) }
                    .filter { it.second !in claves }.forEach { cambiar("DELETE FROM publicaciones WHERE id = ?", it.first) }
                buenas.forEach { p ->
                    val datos = JsonSocial.encodeToString(JsonObject.serializer(), JsonObject(p.datos + ("tipo" to JsonPrimitive(p.tipo.take(20)))))
                    if (datos.length > 20_000) return@forEach
                    cambiar("""INSERT INTO publicaciones(cuenta, clave, visibilidad, cuando, datos) VALUES(?,?,?,?,?)
                        ON CONFLICT(cuenta, clave) DO UPDATE SET visibilidad = excluded.visibilidad, cuando = excluded.cuando, datos = excluded.datos""",
                        yo, p.clave.take(80), p.visibilidad, p.cuando, datos)
                }
                commit()
            } catch (x: Exception) { rollback(); throw x } finally { autoCommit = true }
        }
    }

    /** Las publicaciones que cumplen [donde] (SQL sobre `p`), con su autor y sus cifras, en una sola consulta; con el id del autor. */
    private fun filas(lector: Long?, donde: String, vararg args: Any?): List<Pair<Long, Publicacion>> = b.usar {
        leer("""SELECT p.id, p.cuenta, p.visibilidad, p.cuando, p.datos, c.usuario, c.nombre, c.foto, c.privada,
                    (SELECT COUNT(*) FROM megusta m WHERE m.pub = p.id),
                    (SELECT COUNT(*) FROM comentarios k WHERE k.pub = p.id),
                    p.n_vistas,
                    EXISTS(SELECT 1 FROM megusta m WHERE m.pub = p.id AND m.cuenta = ?),
                    EXISTS(SELECT 1 FROM guardados g WHERE g.pub = p.id AND g.cuenta = ?),
                    (SELECT COUNT(*) FROM guardados g WHERE g.pub = p.id AND g.cuenta <> p.cuenta)
                FROM publicaciones p JOIN cuentas c ON c.id = p.cuenta WHERE $donde""", lector, lector, *args) { r ->
            val datos = JsonSocial.parseToJsonElement(r.getString(5)) as JsonObject
            r.getLong(2) to Publicacion(r.getLong(1), Persona(r.getString(6), r.getString(7), r.getInt(8) == 1, r.getInt(9) == 1),
                (datos["tipo"] as? JsonPrimitive)?.content ?: "", r.getLong(4), r.getString(3), datos,
                r.getInt(10), r.getInt(11), r.getInt(13) == 1, r.getInt(14) == 1, r.getInt(12), guardados = r.getInt(15))
        }
    }

    /** Una publicación como la ve [lector], o null si no la puede ver. */
    fun publicacion(lector: Long?, id: Long): Publicacion? =
        filas(lector, "p.id = ?", id).firstOrNull()?.takeIf { (autor, p) -> puedeVer(lector, autor, p.visibilidad) }?.second

    /** El feed: lo último de quienes sigue (y lo suyo), del más reciente al más viejo; [antes] para seguir bajando. */
    fun actividad(yo: Long, antes: Long?, n: Int = 40): List<Publicacion> =
        filas(yo, """(p.cuenta = ? OR p.cuenta IN (SELECT a FROM seguir WHERE de = ? AND aceptada = 1))
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
        val todas = filas(lector, """p.visibilidad = 'publica' AND c.privada = 0 AND p.cuando < ?
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
        return b.usar { leer("SELECT id, cuenta, texto, cuando FROM comentarios WHERE pub = ? ORDER BY cuando", pub) { arrayOf(it.getLong(1), it.getLong(2), it.getString(3), it.getLong(4)) } }
            .filter { lector == null || !bloqueado(lector, it[1] as Long) }
            .map { Comentario(it[0] as Long, persona(it[1] as Long), it[2] as String, it[3] as Long, lector == it[1] || lector == autorPub) }
    }

    fun comentar(yo: Long, pub: Long, texto: String): Long {
        val t = texto.trim().take(1000)
        if (t.isEmpty()) throw Problema(400, "vacio")
        val p = publicacion(yo, pub) ?: throw Problema(404, "no_existe")
        val autor = idDe(p.autor.usuario)
        if (bloqueado(autor, yo)) throw Problema(404, "no_existe")
        val id = b.usar { insertar("INSERT INTO comentarios(pub, cuenta, texto, cuando) VALUES(?,?,?,?)", pub, yo, t, ahora()) }
        avisar(autor, yo, "comentario", pub.toString())
        return id
    }

    /** Borra un comentario: quien lo escribió o el dueño de la publicación. */
    fun borrarComentario(yo: Long, id: Long) {
        val (autor, dueno) = b.usar {
            leer("SELECT c.cuenta, p.cuenta FROM comentarios c JOIN publicaciones p ON p.id = c.pub WHERE c.id = ?", id) { it.getLong(1) to it.getLong(2) }.firstOrNull()
        } ?: throw Problema(404, "no_existe")
        if (yo != autor && yo != dueno) throw Problema(403, "no_es_tuyo")
        b.usar { cambiar("DELETE FROM comentarios WHERE id = ?", id) }
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
        b.usar { leer("SELECT de FROM seguir WHERE a = ? AND aceptada = 0 ORDER BY cuando DESC", yo) { it.getLong(1) } }.map(::persona)

    /** Seguidores o seguidos de alguien; de una cuenta privada solo los ve quien la sigue. */
    fun lista(lector: Long?, usuario: String, seguidores: Boolean): List<Persona> {
        val id = idDe(usuario)
        if (esPrivada(id) && lector != id && (lector == null || !sigue(lector, id))) throw Problema(403, "privada")
        val sql = if (seguidores) "SELECT de FROM seguir WHERE a = ? AND aceptada = 1 ORDER BY cuando DESC LIMIT 500"
        else "SELECT a FROM seguir WHERE de = ? AND aceptada = 1 ORDER BY cuando DESC LIMIT 500"
        return b.usar { leer(sql, id) { it.getLong(1) } }.filter { lector == null || (!bloqueado(it, lector) && !bloqueado(lector, it)) }.map(::persona)
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

    fun bloqueados(yo: Long): List<Persona> = b.usar { leer("SELECT a FROM bloqueos WHERE de = ?", yo) { it.getLong(1) } }.map(::persona)

    fun buscar(lector: Long?, q: String): List<Persona> {
        val t = q.trim().removePrefix("@").lowercase().take(40)
        if (t.length < 2) return emptyList()
        val patron = "%" + t.replace("%", "").replace("_", "\\_") + "%"
        return b.usar {
            leer("""SELECT id FROM cuentas WHERE usuario LIKE ? ESCAPE '\' OR lower(nombre) LIKE ? ESCAPE '\'
                    ORDER BY (usuario = ?) DESC, (usuario LIKE ?) DESC, usuario LIMIT 30""", patron, patron, t, "$t%") { it.getLong(1) }
        }.filter { lector == null || (!bloqueado(it, lector) && !bloqueado(lector, it)) }.map(::persona)
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
    }.filter { !bloqueado(yo, it[0] as Long) }
        .map { Nota(persona(it[0] as Long), it[1] as String, JsonSocial.parseToJsonElement(it[2] as String) as JsonObject, it[3] as Long) }

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
        return b.usar { insertar("INSERT INTO mensajes(de, para, cuando, sobre, firma) VALUES(?,?,?,?,?)", yo, otro, ahora(), s.sobre, s.firma) }
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
            .map { Sobre(it[0] as Long, persona(it[1] as Long), it[2] as Long, it[3] as String, it[4] as String) }
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
        .map { Aviso(it[0] as Long, it[2] as String, persona(it[1] as Long), it[3] as String, it[4] as Long, it[5] as Boolean) }

    fun sinLeer(yo: Long): Int = b.usar { leer("SELECT COUNT(*) FROM avisos WHERE para = ? AND leido = 0", yo) { it.getInt(1) }.first() }

    fun leidos(yo: Long) { b.usar { cambiar("UPDATE avisos SET leido = 1 WHERE para = ?", yo) } }

    /** Borra un aviso ([id]) o todos (null). */
    fun borrarAvisos(yo: Long, id: Long?) {
        b.usar { if (id == null) cambiar("DELETE FROM avisos WHERE para = ?", yo) else cambiar("DELETE FROM avisos WHERE para = ? AND id = ?", yo, id) }
    }

    /* ----- Reportes ----- */

    fun reportar(yo: Long?, r: NuevoReporte) {
        if (r.que !in setOf("cuenta", "publicacion", "comentario", "mensaje")) throw Problema(400, "que")
        b.usar { insertar("INSERT INTO reportes(de, que, objeto, motivo, cuando) VALUES(?,?,?,?,?)", yo, r.que, r.objeto.take(80), r.motivo.trim().take(1000), ahora()) }
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
        const val POLITICA = "2026-11-03"
        /** Lo normal al empezar, sin datos (hipótesis: recalcular con los contadores cuando haya gente). */
        private const val PREVIA_MEGUSTA = 0.08
        private const val PREVIA_GUARDADO = 0.02
        private const val PREVIA_COMENTARIO = 0.02
        private const val M = 20.0
        private val azar = java.security.SecureRandom()
        val CORREO = Regex("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")
        /** Igual que en El Cajón (`Logica.usuarioLimpio`): 3 a 30 de letras, números, punto y guion bajo. */
        val USUARIO = Regex("[a-z0-9._]{3,30}")
        val RESERVADOS = setOf("admin", "fogon", "elcajon", "cajon", "soporte", "api", "root", "moderacion", "explorar", "p", "privacidad", "terminos")
    }
}
