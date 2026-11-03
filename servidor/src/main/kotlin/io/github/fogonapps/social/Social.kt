package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Quién puede ver algo (los mismos valores que `Visibilidad` en El Cajón; lo privado nunca llega). */
object Vis {
    const val AMIGOS = "amigos"
    const val PUBLICA = "publica"
    val SUBIBLES = setOf(AMIGOS, PUBLICA)
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

/** Una publicación: una entrada del diario, una reseña o una lista que la persona marcó como pública o para amigos. */
@Serializable
data class PublicacionSubida(
    /** El id que le da la app (para actualizarla o quitarla al volver a subir). */
    val clave: String,
    val visibilidad: String,
    val cuando: Long,
    /** "diario", "resena", "lista"… */
    val tipo: String,
    val datos: JsonObject,
)

@Serializable
data class Persona(val usuario: String, val nombre: String, val foto: Boolean)

@Serializable
data class Publicacion(
    val id: Long, val autor: Persona, val tipo: String, val cuando: Long, val visibilidad: String, val datos: JsonObject,
    val megusta: Int, val comentarios: Int, val yoMeGusta: Boolean, val yoGuardo: Boolean,
)

@Serializable
data class Comentario(val id: Long, val autor: Persona, val texto: String, val cuando: Long, val puedoBorrar: Boolean)

@Serializable
data class PerfilVisto(
    val persona: Persona, val bio: String, val portada: String, val enlaces: List<String>,
    /** Solo las partes que este lector puede ver. */
    val partes: Map<String, JsonElement>,
    val publicaciones: List<Publicacion>,
    /** "yo", "amigos", "enviada" (pidió amistad), "recibida" (le pidieron) o "nada". */
    val relacion: String,
)

@Serializable
data class Amigos(val amigos: List<Persona>, val recibidas: List<Persona>, val enviadas: List<Persona>)

@Serializable data class NuevaCuenta(val usuario: String, val nombre: String = "")
@Serializable data class CuentaCreada(val usuario: String, val token: String)
@Serializable data class NuevoComentario(val texto: String)
@Serializable data class NuevoReporte(val que: String, val objeto: String, val motivo: String)
@Serializable data class Error(val error: String)

class Problema(val codigo: Int, val mensaje: String) : Exception(mensaje)

/** Todo lo que hace el servidor, sin HTTP (así se prueba directo). [ahora] se puede cambiar en las pruebas. */
class Social(private val b: Base, private val dirFotos: java.io.File, private val ahora: () -> Long = System::currentTimeMillis) {

    /* ----- Cuentas ----- */

    fun crearCuenta(n: NuevaCuenta): CuentaCreada {
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

    /** La cuenta de un token, o null. */
    fun cuentaDe(token: String?): Long? {
        if (token.isNullOrBlank()) return null
        return b.usar { leer("SELECT id FROM cuentas WHERE token = ?", Base.huella(token)) { it.getLong(1) }.firstOrNull() }
    }

    /** Borra la cuenta y todo lo suyo: perfil, publicaciones, amistades, me gusta, comentarios, guardados y su foto. */
    fun borrarCuenta(yo: Long) {
        b.usar { cambiar("DELETE FROM cuentas WHERE id = ?", yo) }
        java.io.File(dirFotos, "$yo.jpg").delete()
    }

    private fun idDe(usuario: String): Long =
        b.usar { leer("SELECT id FROM cuentas WHERE usuario = ?", usuario.lowercase()) { it.getLong(1) }.firstOrNull() } ?: throw Problema(404, "no_existe")

    private fun persona(id: Long): Persona = b.usar {
        leer("SELECT usuario, nombre, foto FROM cuentas WHERE id = ?", id) { Persona(it.getString(1), it.getString(2), it.getInt(3) == 1) }.first()
    }

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
        if (bytes.size > 400_000) throw Problema(413, "muy_grande")
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) throw Problema(415, "no_es_jpeg")
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
        if (lector != null && bloqueado(id, lector)) throw Problema(404, "no_existe")
        val p = b.usar { leer("SELECT perfil FROM cuentas WHERE id = ?", id) { it.getString(1) }.first() }
            .let { runCatching { JsonSocial.decodeFromString(PerfilSubido.serializer(), it) }.getOrDefault(PerfilSubido()) }
        val relacion = relacion(lector, id)
        val ve = { v: String -> v == Vis.PUBLICA || relacion == "yo" || relacion == "amigos" }
        val pubs = filas(lector, "p.cuenta = ? ORDER BY p.cuando DESC LIMIT 60", id).map { it.second }.filter { ve(it.visibilidad) }
        return PerfilVisto(persona(id), p.bio, p.portada, p.enlaces, p.partes.filterValues { ve(it.visibilidad) }.mapValues { it.value.datos }, pubs, relacion)
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
                    val datos = JsonSocial.encodeToString(JsonObject.serializer(), JsonObject(p.datos + ("tipo" to kotlinx.serialization.json.JsonPrimitive(p.tipo.take(20)))))
                    if (datos.length > 20_000) return@forEach
                    cambiar("""INSERT INTO publicaciones(cuenta, clave, visibilidad, cuando, datos) VALUES(?,?,?,?,?)
                        ON CONFLICT(cuenta, clave) DO UPDATE SET visibilidad = excluded.visibilidad, cuando = excluded.cuando, datos = excluded.datos""",
                        yo, p.clave.take(80), p.visibilidad, p.cuando, datos)
                }
                commit()
            } catch (x: Exception) { rollback(); throw x } finally { autoCommit = true }
        }
    }

    /**
     * Las publicaciones que cumplen [donde] (SQL sobre `p`), con su autor, cuántos me gusta y comentarios tienen y si
     * [lector] les dio me gusta o las guardó: todo en una sola consulta (antes eran varias por publicación).
     * Devuelve también el id del autor, para filtrar por visibilidad.
     */
    private fun filas(lector: Long?, donde: String, vararg args: Any?): List<Pair<Long, Publicacion>> = b.usar {
        leer("""SELECT p.id, p.cuenta, p.visibilidad, p.cuando, p.datos, c.usuario, c.nombre, c.foto,
                    (SELECT COUNT(*) FROM megusta m WHERE m.pub = p.id),
                    (SELECT COUNT(*) FROM comentarios k WHERE k.pub = p.id),
                    EXISTS(SELECT 1 FROM megusta m WHERE m.pub = p.id AND m.cuenta = ?),
                    EXISTS(SELECT 1 FROM guardados g WHERE g.pub = p.id AND g.cuenta = ?)
                FROM publicaciones p JOIN cuentas c ON c.id = p.cuenta WHERE $donde""", lector, lector, *args) { r ->
            val datos = JsonSocial.parseToJsonElement(r.getString(5)) as JsonObject
            r.getLong(2) to Publicacion(r.getLong(1), Persona(r.getString(6), r.getString(7), r.getInt(8) == 1),
                (datos["tipo"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "", r.getLong(4), r.getString(3), datos,
                r.getInt(9), r.getInt(10), r.getInt(11) == 1, r.getInt(12) == 1)
        }
    }

    /** Una publicación como la ve [lector], o null si no la puede ver. */
    fun publicacion(lector: Long?, id: Long): Publicacion? =
        filas(lector, "p.id = ?", id).firstOrNull()?.takeIf { (autor, p) -> puedeVer(lector, autor, p.visibilidad) }?.second

    /** Lo último de sus amigos (y lo suyo), del más reciente al más viejo; [antes] para seguir bajando. */
    fun actividad(yo: Long, antes: Long?, n: Int = 40): List<Publicacion> =
        // Sus amigos ven lo de amigos y lo público; al bloquear se acaba la amistad, así que no hace falta más filtro.
        filas(yo, """(p.cuenta = ? OR p.cuenta IN (
                SELECT s.a FROM solicitudes s JOIN solicitudes r ON r.de = s.a AND r.a = s.de WHERE s.de = ?))
                AND p.cuando < ? ORDER BY p.cuando DESC LIMIT ?""", yo, yo, antes ?: Long.MAX_VALUE, n.coerceIn(1, 100)).map { it.second }

    fun meGusta(yo: Long, pub: Long, si: Boolean) {
        publicacion(yo, pub) ?: throw Problema(404, "no_existe")
        b.usar { if (si) cambiar("INSERT OR IGNORE INTO megusta(pub, cuenta) VALUES(?,?)", pub, yo) else cambiar("DELETE FROM megusta WHERE pub = ? AND cuenta = ?", pub, yo) }
    }

    fun guardar(yo: Long, pub: Long, si: Boolean) {
        publicacion(yo, pub) ?: throw Problema(404, "no_existe")
        b.usar { if (si) cambiar("INSERT OR IGNORE INTO guardados(pub, cuenta, cuando) VALUES(?,?,?)", pub, yo, ahora()) else cambiar("DELETE FROM guardados WHERE pub = ? AND cuenta = ?", pub, yo) }
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
        if (bloqueado(idDe(p.autor.usuario), yo)) throw Problema(404, "no_existe")
        return b.usar { insertar("INSERT INTO comentarios(pub, cuenta, texto, cuando) VALUES(?,?,?,?)", pub, yo, t, ahora()) }
    }

    /** Borra un comentario: quien lo escribió o el dueño de la publicación. */
    fun borrarComentario(yo: Long, id: Long) {
        val (autor, dueno) = b.usar {
            leer("SELECT c.cuenta, p.cuenta FROM comentarios c JOIN publicaciones p ON p.id = c.pub WHERE c.id = ?", id) { it.getLong(1) to it.getLong(2) }.firstOrNull()
        } ?: throw Problema(404, "no_existe")
        if (yo != autor && yo != dueno) throw Problema(403, "no_es_tuyo")
        b.usar { cambiar("DELETE FROM comentarios WHERE id = ?", id) }
    }

    /* ----- Amigos y bloqueos ----- */

    /** Pide amistad (o la acepta, si el otro ya la había pedido). */
    fun pedirAmistad(yo: Long, usuario: String) {
        val otro = idDe(usuario)
        if (otro == yo) throw Problema(400, "eres_tu")
        if (bloqueado(otro, yo) || bloqueado(yo, otro)) throw Problema(404, "no_existe")
        b.usar { cambiar("INSERT OR IGNORE INTO solicitudes(de, a, cuando) VALUES(?,?,?)", yo, otro, ahora()) }
    }

    /** Deja de ser amigos, cancela lo que pidió o rechaza lo que le pidieron. */
    fun quitarAmistad(yo: Long, usuario: String) {
        val otro = idDe(usuario)
        b.usar { cambiar("DELETE FROM solicitudes WHERE (de = ? AND a = ?) OR (de = ? AND a = ?)", yo, otro, otro, yo) }
    }

    fun amigos(yo: Long): Amigos {
        val mias = b.usar { leer("SELECT a FROM solicitudes WHERE de = ?", yo) { it.getLong(1) } }.toSet()
        val suyas = b.usar { leer("SELECT de FROM solicitudes WHERE a = ?", yo) { it.getLong(1) } }.toSet()
        return Amigos((mias intersect suyas).map(::persona), (suyas - mias).map(::persona), (mias - suyas).map(::persona))
    }

    /** Bloquear: se acaba la amistad, no se ven ni se escriben. */
    fun bloquear(yo: Long, usuario: String, si: Boolean) {
        val otro = idDe(usuario)
        if (otro == yo) throw Problema(400, "eres_tu")
        b.usar {
            if (si) {
                cambiar("INSERT OR IGNORE INTO bloqueos(de, a) VALUES(?,?)", yo, otro)
                cambiar("DELETE FROM solicitudes WHERE (de = ? AND a = ?) OR (de = ? AND a = ?)", yo, otro, otro, yo)
            } else cambiar("DELETE FROM bloqueos WHERE de = ? AND a = ?", yo, otro)
        }
    }

    fun buscar(lector: Long?, q: String): List<Persona> {
        val t = q.trim().lowercase().take(40)
        if (t.length < 2) return emptyList()
        val patron = "%" + t.replace("%", "").replace("_", "\\_") + "%"
        return b.usar {
            leer("SELECT id FROM cuentas WHERE usuario LIKE ? ESCAPE '\\' OR lower(nombre) LIKE ? ESCAPE '\\' ORDER BY usuario LIMIT 30", patron, patron) { it.getLong(1) }
        }.filter { lector == null || (!bloqueado(it, lector) && !bloqueado(lector, it)) }.map(::persona)
    }

    /* ----- Reportes ----- */

    fun reportar(yo: Long?, r: NuevoReporte) {
        if (r.que !in setOf("cuenta", "publicacion", "comentario")) throw Problema(400, "que")
        b.usar { insertar("INSERT INTO reportes(de, que, objeto, motivo, cuando) VALUES(?,?,?,?,?)", yo, r.que, r.objeto.take(80), r.motivo.trim().take(1000), ahora()) }
    }

    /* ----- Reglas ----- */

    private fun sonAmigos(x: Long, y: Long) = b.usar {
        leer("SELECT 1 FROM solicitudes s JOIN solicitudes r ON r.de = s.a AND r.a = s.de WHERE s.de = ? AND s.a = ?", x, y) { 1 }.isNotEmpty()
    }

    /** [quien] bloqueó a [aQuien]. */
    private fun bloqueado(quien: Long, aQuien: Long) = b.usar { leer("SELECT 1 FROM bloqueos WHERE de = ? AND a = ?", quien, aQuien) { 1 }.isNotEmpty() }

    private fun puedeVer(lector: Long?, autor: Long, vis: String): Boolean = when {
        lector == autor -> true
        lector != null && (bloqueado(autor, lector) || bloqueado(lector, autor)) -> false
        vis == Vis.PUBLICA -> true
        vis == Vis.AMIGOS -> lector != null && sonAmigos(autor, lector)
        else -> false
    }

    private fun relacion(lector: Long?, id: Long): String {
        if (lector == null) return "nada"
        if (lector == id) return "yo"
        val pedi = b.usar { leer("SELECT 1 FROM solicitudes WHERE de = ? AND a = ?", lector, id) { 1 }.isNotEmpty() }
        val pidio = b.usar { leer("SELECT 1 FROM solicitudes WHERE de = ? AND a = ?", id, lector) { 1 }.isNotEmpty() }
        return when { pedi && pidio -> "amigos"; pedi -> "enviada"; pidio -> "recibida"; else -> "nada" }
    }

    companion object {
        /** Igual que en El Cajón (`Logica.usuarioLimpio`): 3 a 30 de letras, números, punto y guion bajo. */
        val USUARIO = Regex("[a-z0-9._]{3,30}")
        val RESERVADOS = setOf("admin", "fogon", "elcajon", "cajon", "soporte", "api", "root", "moderacion")
    }
}
