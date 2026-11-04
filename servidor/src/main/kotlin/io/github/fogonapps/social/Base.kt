package io.github.fogonapps.social

import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.Base64

/**
 * La base de datos de Fogón Social: un archivo SQLite. Guarda solo lo que cada persona decidió compartir desde El
 * Cajón (su perfil filtrado y sus publicaciones públicas o para sus seguidores), a quién sigue, me gusta,
 * comentarios, guardados, avisos, imágenes de sus publicaciones, bloqueos y reportes. Nada de lo privado ni de lo que la app aprende de sus gustos llega aquí.
 * Una sola conexión, con todo pasando por [usar] (SQLite escribe de a uno).
 */
class Base(ruta: String) {
    private val con: Connection = DriverManager.getConnection("jdbc:sqlite:$ruta")

    init {
        usar {
            createStatement().use { s ->
                s.execute("PRAGMA journal_mode=WAL")
                s.execute("PRAGMA foreign_keys=ON")
                s.execute("""CREATE TABLE IF NOT EXISTS cuentas(
                    id INTEGER PRIMARY KEY, usuario TEXT UNIQUE NOT NULL, token TEXT UNIQUE NOT NULL,
                    nombre TEXT NOT NULL DEFAULT '', perfil TEXT NOT NULL DEFAULT '{}', foto INTEGER NOT NULL DEFAULT 0,
                    creada INTEGER NOT NULL, cambio INTEGER NOT NULL)""")
                s.execute("""CREATE TABLE IF NOT EXISTS publicaciones(
                    id INTEGER PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    clave TEXT NOT NULL, visibilidad TEXT NOT NULL, cuando INTEGER NOT NULL, datos TEXT NOT NULL,
                    UNIQUE(cuenta, clave))""")
                s.execute("CREATE INDEX IF NOT EXISTS pub_cuando ON publicaciones(cuenta, cuando)")
                s.execute("""CREATE TABLE IF NOT EXISTS solicitudes(
                    de INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, a INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    cuando INTEGER NOT NULL, PRIMARY KEY(de, a))""")
                s.execute("""CREATE TABLE IF NOT EXISTS bloqueos(
                    de INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, a INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    PRIMARY KEY(de, a))""")
                s.execute("""CREATE TABLE IF NOT EXISTS megusta(
                    pub INTEGER NOT NULL REFERENCES publicaciones(id) ON DELETE CASCADE, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    PRIMARY KEY(pub, cuenta))""")
                s.execute("""CREATE TABLE IF NOT EXISTS guardados(
                    pub INTEGER NOT NULL REFERENCES publicaciones(id) ON DELETE CASCADE, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    cuando INTEGER NOT NULL, PRIMARY KEY(pub, cuenta))""")
                s.execute("""CREATE TABLE IF NOT EXISTS comentarios(
                    id INTEGER PRIMARY KEY, pub INTEGER NOT NULL REFERENCES publicaciones(id) ON DELETE CASCADE,
                    cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, texto TEXT NOT NULL, cuando INTEGER NOT NULL)""")
                s.execute("CREATE INDEX IF NOT EXISTS comentarios_pub ON comentarios(pub)")
                s.execute("CREATE INDEX IF NOT EXISTS guardados_cuenta ON guardados(cuenta, cuando)")
                s.execute("CREATE INDEX IF NOT EXISTS solicitudes_a ON solicitudes(a)")
                s.execute("""CREATE TABLE IF NOT EXISTS reportes(
                    id INTEGER PRIMARY KEY, de INTEGER, que TEXT NOT NULL, objeto TEXT NOT NULL, motivo TEXT NOT NULL, cuando INTEGER NOT NULL)""")
                // Seguir, como en Instagram: [aceptada] = 0 mientras una cuenta privada no lo aprueba.
                s.execute("""CREATE TABLE IF NOT EXISTS seguir(
                    de INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, a INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    cuando INTEGER NOT NULL, aceptada INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(de, a))""")
                s.execute("CREATE INDEX IF NOT EXISTS seguir_a ON seguir(a, aceptada)")
                // Lo que le avisa a cada persona: me gusta, comentarios, seguidores nuevos, solicitudes y solicitudes aceptadas.
                s.execute("""CREATE TABLE IF NOT EXISTS avisos(
                    id INTEGER PRIMARY KEY, para INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    de INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, tipo TEXT NOT NULL, objeto TEXT NOT NULL DEFAULT '',
                    cuando INTEGER NOT NULL, leido INTEGER NOT NULL DEFAULT 0)""")
                s.execute("CREATE INDEX IF NOT EXISTS avisos_para ON avisos(para, cuando)")
                // Las imágenes de las publicaciones: el nombre es al azar (no se adivina) y el archivo va en fotos/i/.
                s.execute("""CREATE TABLE IF NOT EXISTS imagenes(
                    nombre TEXT PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, cuando INTEGER NOT NULL)""")
                s.execute("DROP TABLE IF EXISTS solicitudes")
                // El chat, cifrado de extremo a extremo: las llaves públicas de cada cuenta y los mensajes cifrados
                // (el servidor no los puede leer). Se borran al entregarlos o a los 30 días.
                s.execute("""CREATE TABLE IF NOT EXISTS llaves(
                    cuenta INTEGER PRIMARY KEY REFERENCES cuentas(id) ON DELETE CASCADE, cifrar TEXT NOT NULL, firmar TEXT NOT NULL, cuando INTEGER NOT NULL)""")
                s.execute("""CREATE TABLE IF NOT EXISTS mensajes(
                    id INTEGER PRIMARY KEY, de INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    para INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, cuando INTEGER NOT NULL, sobre TEXT NOT NULL, firma TEXT NOT NULL)""")
                s.execute("CREATE INDEX IF NOT EXISTS mensajes_para ON mensajes(para, id)")
                // Las vistas: un contador por publicación, y para no contar dos veces la misma cuenta, solo una huella
                // (HMAC con un secreto del servidor) que se borra a los 14 días. No queda un historial de quién vio qué.
                columna(s, "publicaciones", "n_vistas INTEGER NOT NULL DEFAULT 0")
                val viejas = s.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'vistas'").use { it.next() } &&
                    s.executeQuery("PRAGMA table_info(vistas)").use { r -> buildList { while (r.next()) add(r.getString("name")) } }.contains("cuenta")
                if (viejas) {
                    s.execute("UPDATE publicaciones SET n_vistas = (SELECT COUNT(*) FROM vistas v WHERE v.pub = publicaciones.id)")
                    s.execute("DROP TABLE vistas")
                }
                s.execute("""CREATE TABLE IF NOT EXISTS vistas(
                    huella TEXT PRIMARY KEY, pub INTEGER NOT NULL REFERENCES publicaciones(id) ON DELETE CASCADE, cuando INTEGER NOT NULL) WITHOUT ROWID""")
                s.execute("CREATE INDEX IF NOT EXISTS vistas_cuando ON vistas(cuando)")
                // La nota de cada persona: unas palabras (y una obra, si quiere) que duran 24 horas.
                s.execute("""CREATE TABLE IF NOT EXISTS notas(
                    cuenta INTEGER PRIMARY KEY REFERENCES cuentas(id) ON DELETE CASCADE, texto TEXT NOT NULL, datos TEXT NOT NULL DEFAULT '{}', cuando INTEGER NOT NULL)""")
                // Las frases del saludo de El Cajón que gustan (1) o no (-1): una por cuenta; se van con la cuenta.
                s.execute("""CREATE TABLE IF NOT EXISTS frases(
                    clave TEXT NOT NULL, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, valor INTEGER NOT NULL,
                    cuando INTEGER NOT NULL, PRIMARY KEY(clave, cuenta)) WITHOUT ROWID""")
                // Frases que propone la gente para el saludo: salen para todos solo cuando una persona del equipo las aprueba.
                s.execute("""CREATE TABLE IF NOT EXISTS frases_propuestas(
                    id INTEGER PRIMARY KEY, cuenta INTEGER REFERENCES cuentas(id) ON DELETE SET NULL, texto TEXT NOT NULL, idioma TEXT NOT NULL,
                    estado TEXT NOT NULL DEFAULT 'pendiente', cuando INTEGER NOT NULL)""")
                columna(s, "cuentas", "privada INTEGER NOT NULL DEFAULT 0")
                // Entrar con el correo: el correo de la cuenta y la prueba de la autorización (Decreto 1377, art. 8).
                columna(s, "cuentas", "correo TEXT")
                // Contraseña (opcional, con correo): PBKDF2-SHA256, sal propia, "iteraciones:sal:huella" en base64.
                columna(s, "cuentas", "clave TEXT")
                // Cambio de usuario: una vez cada 7 días; el usuario viejo queda reservado 14 días.
                columna(s, "cuentas", "usuario_cambio INTEGER NOT NULL DEFAULT 0")
                s.execute("CREATE TABLE IF NOT EXISTS usuarios_viejos(usuario TEXT PRIMARY KEY, cuenta INTEGER NOT NULL, hasta INTEGER NOT NULL)")
                // Moderación: quién administra, lo oculto mientras se revisa, las faltas (vencen a los 90 días),
                // las restricciones y las decisiones (la prueba de cada una).
                columna(s, "cuentas", "admin INTEGER NOT NULL DEFAULT 0")
                columna(s, "cuentas", "restringida_hasta INTEGER NOT NULL DEFAULT 0")
                columna(s, "cuentas", "suspendida_hasta INTEGER NOT NULL DEFAULT 0")
                columna(s, "cuentas", "cerrada INTEGER NOT NULL DEFAULT 0")
                columna(s, "publicaciones", "oculta INTEGER NOT NULL DEFAULT 0")
                columna(s, "comentarios", "oculto INTEGER NOT NULL DEFAULT 0")
                columna(s, "reportes", "estado TEXT NOT NULL DEFAULT 'abierto'")
                // El cajón completo de cada cuenta, cifrado en el teléfono (el servidor no lo puede leer), y su llave
                // envuelta con la contraseña de la persona (tampoco la puede abrir).
                s.execute("""CREATE TABLE IF NOT EXISTS cajones(
                    cuenta INTEGER PRIMARY KEY REFERENCES cuentas(id) ON DELETE CASCADE, sobre TEXT NOT NULL DEFAULT '',
                    llave TEXT NOT NULL DEFAULT '', cambio INTEGER NOT NULL DEFAULT 0)""")
                s.execute("""CREATE TABLE IF NOT EXISTS faltas(
                    id INTEGER PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    puntos INTEGER NOT NULL, regla TEXT NOT NULL, cuando INTEGER NOT NULL, vence INTEGER NOT NULL)""")
                s.execute("""CREATE TABLE IF NOT EXISTS decisiones(
                    id INTEGER PRIMARY KEY, admin INTEGER NOT NULL, que TEXT NOT NULL, objeto TEXT NOT NULL, decision TEXT NOT NULL,
                    regla TEXT NOT NULL DEFAULT '', cuando INTEGER NOT NULL)""")
                columna(s, "cuentas", "acepto TEXT NOT NULL DEFAULT ''")
                columna(s, "cuentas", "acepto_cuando INTEGER NOT NULL DEFAULT 0")
                s.execute("CREATE UNIQUE INDEX IF NOT EXISTS cuentas_correo ON cuentas(correo) WHERE correo IS NOT NULL")
                // Una sesión por teléfono (solo la huella del token).
                s.execute("""CREATE TABLE IF NOT EXISTS sesiones(
                    huella TEXT PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, creada INTEGER NOT NULL)""")
                // Los códigos de acceso que se mandan al correo (solo su huella; duran 10 minutos y aguantan 5 intentos).
                s.execute("""CREATE TABLE IF NOT EXISTS codigos(
                    correo TEXT PRIMARY KEY, huella TEXT NOT NULL, expira INTEGER NOT NULL, intentos INTEGER NOT NULL DEFAULT 0, enviado INTEGER NOT NULL)""")
            }
        }
    }

    /** Agrega una columna a una tabla que ya existía (de una versión anterior); si ya está, no hace nada. */
    private fun columna(s: java.sql.Statement, tabla: String, definicion: String) {
        val nombre = definicion.substringBefore(' ')
        val hay = s.executeQuery("PRAGMA table_info($tabla)").use { r -> buildList { while (r.next()) add(r.getString("name")) } }
        if (nombre !in hay) s.execute("ALTER TABLE $tabla ADD COLUMN $definicion")
    }

    @Synchronized
    fun <T> usar(f: Connection.() -> T): T = con.f()

    fun cerrar() = con.close()

    companion object {
        private val azar = SecureRandom()

        /** Un token nuevo (32 bytes al azar). En la base solo se guarda su huella ([huella]). */
        fun tokenNuevo(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { azar.nextBytes(it) })

        fun huella(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** Atajos de JDBC. */
fun Connection.cambiar(sql: String, vararg p: Any?): Int = prepareStatement(sql).use { s ->
    p.forEachIndexed { i, v -> s.setObject(i + 1, v) }
    s.executeUpdate()
}

fun <T> Connection.leer(sql: String, vararg p: Any?, fila: (ResultSet) -> T): List<T> = prepareStatement(sql).use { s ->
    p.forEachIndexed { i, v -> s.setObject(i + 1, v) }
    s.executeQuery().use { r -> buildList { while (r.next()) add(fila(r)) } }
}

fun Connection.insertar(sql: String, vararg p: Any?): Long = prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { s ->
    p.forEachIndexed { i, v -> s.setObject(i + 1, v) }
    s.executeUpdate()
    s.generatedKeys.use { if (it.next()) it.getLong(1) else -1 }
}
