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
                columna(s, "cuentas", "privada INTEGER NOT NULL DEFAULT 0")
                // Entrar con el correo: el correo de la cuenta y la prueba de la autorización (Decreto 1377, art. 8).
                columna(s, "cuentas", "correo TEXT")
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
