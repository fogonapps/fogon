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
                s.execute("""CREATE TABLE IF NOT EXISTS lugares(
                    id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, ciudad TEXT NOT NULL,
                    nombre_busqueda TEXT NOT NULL, ciudad_busqueda TEXT NOT NULL, direccion TEXT,
                    lat REAL, lon REAL, cuenta INTEGER REFERENCES cuentas(id) ON DELETE SET NULL,
                    cuando INTEGER NOT NULL, retirado INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(nombre_busqueda, ciudad_busqueda))""")
                s.execute("CREATE INDEX IF NOT EXISTS lugares_creador ON lugares(cuenta, cuando)")
                // La identidad HMAC depende de este número: no puede volver a asignarse tras un borrado.
                // Un asignador separado evita reconstruir cuentas y todas sus referencias existentes.
                s.execute("CREATE TABLE IF NOT EXISTS secuencia_cuentas(id INTEGER PRIMARY KEY AUTOINCREMENT)")
                s.execute("INSERT OR IGNORE INTO secuencia_cuentas(id) SELECT MAX(id) FROM cuentas HAVING COUNT(*) > 0")
                s.execute("""CREATE TABLE IF NOT EXISTS publicaciones(
                    id INTEGER PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    clave TEXT NOT NULL, visibilidad TEXT NOT NULL, cuando INTEGER NOT NULL, datos TEXT NOT NULL,
                    UNIQUE(cuenta, clave))""")
                s.execute("CREATE INDEX IF NOT EXISTS pub_cuando ON publicaciones(cuenta, cuando)")
                s.execute("""CREATE TABLE IF NOT EXISTS retiradas(
                    cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    clave TEXT NOT NULL, cuando INTEGER NOT NULL, PRIMARY KEY(cuenta, clave))""")
                s.execute("""CREATE TABLE IF NOT EXISTS solicitudes(
                    de INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, a INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    cuando INTEGER NOT NULL, PRIMARY KEY(de, a))""")
                s.execute("""CREATE TABLE IF NOT EXISTS bloqueos(
                    de INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, a INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    PRIMARY KEY(de, a))""")
                s.execute("""CREATE TABLE IF NOT EXISTS megusta(
                    pub INTEGER NOT NULL REFERENCES publicaciones(id) ON DELETE CASCADE, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    PRIMARY KEY(pub, cuenta))""")
                // «Útil» en las reseñas: distinto del me gusta, un voto por cuenta.
                s.execute("""CREATE TABLE IF NOT EXISTS utiles(
                    pub INTEGER NOT NULL REFERENCES publicaciones(id) ON DELETE CASCADE, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    cuando INTEGER NOT NULL, PRIMARY KEY(pub, cuenta))""")
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
                s.execute("""CREATE TABLE IF NOT EXISTS videos(
                    nombre TEXT PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    cuando INTEGER NOT NULL, duracion REAL NOT NULL, ancho INTEGER NOT NULL, alto INTEGER NOT NULL)""")
                s.execute("DROP TABLE IF EXISTS solicitudes")
                // El chat, cifrado de extremo a extremo: las llaves públicas de cada cuenta y los mensajes cifrados
                // (el servidor no los puede leer). Se borran al entregarlos o a los 30 días.
                s.execute("""CREATE TABLE IF NOT EXISTS llaves(
                    cuenta INTEGER PRIMARY KEY REFERENCES cuentas(id) ON DELETE CASCADE, cifrar TEXT NOT NULL, firmar TEXT NOT NULL, cuando INTEGER NOT NULL)""")
                s.execute("""CREATE TABLE IF NOT EXISTS mensajes(
                    id INTEGER PRIMARY KEY, de INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    para INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, cuando INTEGER NOT NULL, sobre TEXT NOT NULL, firma TEXT NOT NULL)""")
                s.execute("CREATE INDEX IF NOT EXISTS mensajes_para ON mensajes(para, id)")
                migrarMensajes(this)
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
                // Las historias de 24 horas, por app (como el feed), y quién vio cada una.
                s.execute("""CREATE TABLE IF NOT EXISTS historias(
                    id INTEGER PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, app TEXT NOT NULL, datos TEXT NOT NULL,
                    cuando INTEGER NOT NULL, expira INTEGER NOT NULL, estado TEXT NOT NULL DEFAULT 'visible', oculta INTEGER NOT NULL DEFAULT 0, retirado_hasta INTEGER NOT NULL DEFAULT 0)""")
                s.execute("CREATE INDEX IF NOT EXISTS historias_cuenta ON historias(cuenta, app, expira)")
                s.execute("""CREATE TABLE IF NOT EXISTS historia_vistas(
                    historia INTEGER NOT NULL REFERENCES historias(id) ON DELETE CASCADE, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    cuando INTEGER NOT NULL, PRIMARY KEY(historia, cuenta)) WITHOUT ROWID""")
                // Las frases del saludo de El Cajón que gustan (1) o no (-1): una por cuenta; se van con la cuenta.
                s.execute("""CREATE TABLE IF NOT EXISTS frases(
                    clave TEXT NOT NULL, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, valor INTEGER NOT NULL,
                    cuando INTEGER NOT NULL, PRIMARY KEY(clave, cuenta)) WITHOUT ROWID""")
                // Frases que propone la gente para el saludo: salen para todos solo cuando una persona del equipo las aprueba.
                s.execute("""CREATE TABLE IF NOT EXISTS frases_propuestas(
                    id INTEGER PRIMARY KEY, cuenta INTEGER REFERENCES cuentas(id) ON DELETE SET NULL, texto TEXT NOT NULL, idioma TEXT NOT NULL,
                    estado TEXT NOT NULL DEFAULT 'pendiente', cuando INTEGER NOT NULL)""")
                // Frases de la app que el equipo quitó del saludo (por su clave).
                s.execute("CREATE TABLE IF NOT EXISTS frases_ocultas(clave TEXT PRIMARY KEY)")
                // En qué momento del día sale cada frase de la gente: todo, manana, tarde, noche o madrugada.
                columna(s, "frases_propuestas", "franja TEXT NOT NULL DEFAULT 'todo'")
                // De qué app es cada frase de la gente (las de antes, de El Cajón). Las claves de las frases propias de
                // cada app ya llevan su prefijo (fz_, al_, cu_), así los votos y las ocultas no se mezclan.
                columna(s, "frases_propuestas", "app TEXT NOT NULL DEFAULT 'cajon'")
                columna(s, "cuentas", "privada INTEGER NOT NULL DEFAULT 0")
                // Entrar con el correo: el correo de la cuenta y la prueba de la autorización (Decreto 1377, art. 8).
                columna(s, "cuentas", "correo TEXT")
                // Contraseña (opcional, con correo): PBKDF2-SHA256, sal propia, "iteraciones:sal:huella" en base64.
                columna(s, "cuentas", "clave TEXT")
                // Cambio de usuario: una vez cada 30 días; el usuario viejo queda reservado 14 días.
                columna(s, "cuentas", "usuario_cambio INTEGER NOT NULL DEFAULT 0")
                s.execute("CREATE TABLE IF NOT EXISTS usuarios_viejos(usuario TEXT PRIMARY KEY, cuenta INTEGER NOT NULL, hasta INTEGER NOT NULL)")
                // Moderación: quién administra, lo oculto mientras se revisa, las faltas (vencen a los 90 días),
                // las restricciones y las decisiones (la prueba de cada una).
                columna(s, "cuentas", "admin INTEGER NOT NULL DEFAULT 0")
                columna(s, "cuentas", "restringida_hasta INTEGER NOT NULL DEFAULT 0")
                columna(s, "cuentas", "suspendida_hasta INTEGER NOT NULL DEFAULT 0")
                columna(s, "cuentas", "cerrada INTEGER NOT NULL DEFAULT 0")
                columna(s, "cuentas", "borrar_el INTEGER NOT NULL DEFAULT 0")
                // X-08: la insignia que se ve junto al nombre ("fogon", "verificada" o nada) y la decisión del equipo
                // que manda sobre la regla automática ("fogon", "verificada", "no" o null).
                columna(s, "cuentas", "insignia TEXT NOT NULL DEFAULT ''")
                columna(s, "cuentas", "verificacion TEXT")
                // Verificación en dos pasos: contraseña y, además, un código del correo.
                columna(s, "cuentas", "dos_pasos INTEGER NOT NULL DEFAULT 0")
                // X-10: las direcciones UnifiedPush de los teléfonos (ver Empujes).
                s.execute("CREATE TABLE IF NOT EXISTS empujes(endpoint TEXT PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, app TEXT NOT NULL, cuando INTEGER NOT NULL)")
                s.execute("CREATE INDEX IF NOT EXISTS empujes_cuenta ON empujes(cuenta, app)")
                columna(s, "empujes", "sesion TEXT")
                // Códigos de acceso equivocados por correo (tope diario contra quien intenta adivinarlos).
                s.execute("CREATE TABLE IF NOT EXISTS fallos_codigo(correo TEXT NOT NULL, cuando INTEGER NOT NULL)")
                s.execute("CREATE INDEX IF NOT EXISTS fallos_codigo_correo ON fallos_codigo(correo, cuando)")
                s.execute("CREATE TABLE IF NOT EXISTS motivos_borrado(cuando INTEGER NOT NULL, motivo TEXT NOT NULL, texto TEXT NOT NULL DEFAULT '')")
                s.execute("""CREATE TABLE IF NOT EXISTS cambios_correo(
                    cuenta INTEGER PRIMARY KEY REFERENCES cuentas(id) ON DELETE CASCADE,
                    nuevo TEXT NOT NULL, huella TEXT NOT NULL, expira INTEGER NOT NULL, intentos INTEGER NOT NULL DEFAULT 0)""")
                columna(s, "publicaciones", "oculta INTEGER NOT NULL DEFAULT 0")
                columna(s, "comentarios", "oculto INTEGER NOT NULL DEFAULT 0")
                columna(s, "comentarios", "padre INTEGER NULL REFERENCES comentarios(id) ON DELETE SET NULL")
                columna(s, "comentarios", "borrado INTEGER NOT NULL DEFAULT 0")
                s.execute("CREATE INDEX IF NOT EXISTS comentarios_padre ON comentarios(padre)")
                s.execute("""CREATE TABLE IF NOT EXISTS megusta_comentarios(
                    comentario INTEGER NOT NULL REFERENCES comentarios(id) ON DELETE CASCADE,
                    cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    cuando INTEGER NOT NULL, PRIMARY KEY(comentario, cuenta))""")
                columna(s, "reportes", "estado TEXT NOT NULL DEFAULT 'abierto'")
                // Portadas (C-03): un voto por obra y cuenta entre imágenes de fuentes autorizadas, y las que el equipo bloqueó.
                s.execute("""CREATE TABLE IF NOT EXISTS votos_imagen(
                    obra TEXT NOT NULL, url TEXT NOT NULL, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    cuando INTEGER NOT NULL, PRIMARY KEY(obra, cuenta))""")
                s.execute("CREATE INDEX IF NOT EXISTS votos_imagen_obra ON votos_imagen(obra)")
                s.execute("""CREATE TABLE IF NOT EXISTS imagenes_bloqueadas(
                    url TEXT PRIMARY KEY, motivo TEXT NOT NULL, cuando INTEGER NOT NULL)""")
                // El cajón completo de cada cuenta, cifrado en el teléfono (el servidor no lo puede leer), y su llave
                // envuelta con la contraseña de la persona (tampoco la puede abrir).
                s.execute("""CREATE TABLE IF NOT EXISTS cajones(
                    cuenta INTEGER PRIMARY KEY REFERENCES cuentas(id) ON DELETE CASCADE, sobre TEXT NOT NULL DEFAULT '',
                    llave TEXT NOT NULL DEFAULT '', cambio INTEGER NOT NULL DEFAULT 0)""")
                // D-033: la copia cifrada de las otras apps (cuaderno de entrenos, diario de comidas, cuaderno de plata),
                // con la misma llave de `cajones`. El servidor no la puede leer.
                s.execute("""CREATE TABLE IF NOT EXISTS respaldos(
                    cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, app TEXT NOT NULL,
                    sobre TEXT NOT NULL DEFAULT '', cambio INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(cuenta, app))""")
                s.execute("""CREATE TABLE IF NOT EXISTS faltas(
                    id INTEGER PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    puntos INTEGER NOT NULL, regla TEXT NOT NULL, cuando INTEGER NOT NULL, vence INTEGER NOT NULL)""")
                s.execute("""CREATE TABLE IF NOT EXISTS decisiones(
                    id INTEGER PRIMARY KEY, admin INTEGER NOT NULL, que TEXT NOT NULL, objeto TEXT NOT NULL, decision TEXT NOT NULL,
                    regla TEXT NOT NULL DEFAULT '', cuando INTEGER NOT NULL)""")
                // Moderación: el mensaje del equipo y el tipo de decisión en cada falta; las apelaciones.
                columna(s, "faltas", "mensaje TEXT NOT NULL DEFAULT ''")
                columna(s, "faltas", "decision TEXT NOT NULL DEFAULT ''")
                columna(s, "faltas", "admin INTEGER")
                s.execute("""CREATE TABLE IF NOT EXISTS apelaciones(id INTEGER PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    texto TEXT NOT NULL, cuando INTEGER NOT NULL, estado TEXT NOT NULL DEFAULT 'abierta', respuesta TEXT NOT NULL DEFAULT '',
                    decidida_por INTEGER, decidida_cuando INTEGER)""")
                columna(s, "cuentas", "acepto TEXT NOT NULL DEFAULT ''")
                columna(s, "cuentas", "acepto_cuando INTEGER NOT NULL DEFAULT 0")
                s.execute("CREATE UNIQUE INDEX IF NOT EXISTS cuentas_correo ON cuentas(correo) WHERE correo IS NOT NULL")
                // Una sesión por teléfono (solo la huella del token).
                s.execute("""CREATE TABLE IF NOT EXISTS sesiones(
                    huella TEXT PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, creada INTEGER NOT NULL)""")
                migrarSeguridad(this)
                migrarApps(this)
                transaccion { migrarEquipo(this) }
                s.execute("""CREATE TABLE IF NOT EXISTS intentos_mfa(
                    cuenta INTEGER PRIMARY KEY REFERENCES cuentas(id) ON DELETE CASCADE,
                    fallos INTEGER NOT NULL, desde INTEGER NOT NULL, bloqueada_hasta INTEGER NOT NULL,
                    nivel INTEGER NOT NULL, ultimo_fallo INTEGER NOT NULL)""")
                // Los códigos de acceso que se mandan al correo (solo su huella; duran 10 minutos y aguantan 5 intentos).
                s.execute("""CREATE TABLE IF NOT EXISTS codigos(
                    correo TEXT PRIMARY KEY, huella TEXT NOT NULL, expira INTEGER NOT NULL, intentos INTEGER NOT NULL DEFAULT 0, enviado INTEGER NOT NULL)""")
                transaccion {
                    columna(s, "codigos", "app TEXT NOT NULL DEFAULT 'cajon'")
                    columna(s, "cambios_correo", "app TEXT NOT NULL DEFAULT 'cajon'")
                }
            }
        }
    }

    private fun migrarApps(c: Connection) {
        // SQLite requiere desactivar las referencias fuera de la transacción al reconstruir la unicidad.
        c.createStatement().use { it.execute("PRAGMA foreign_keys=OFF") }
        try {
            transaccion {
                createStatement().use { s ->
                    for (tabla in listOf("publicaciones", "retiradas")) {
                        val columnas = leer("PRAGMA table_info($tabla)") { it.getString("name") }
                        if ("app" !in columnas) {
                            val esquema = leer("SELECT sql FROM sqlite_master WHERE name = ?", tabla) { it.getString(1) }.single()
                                .replace("CREATE TABLE $tabla", "CREATE TABLE ${tabla}_apps", ignoreCase = true)
                                .replace(Regex("UNIQUE\\s*\\(cuenta,\\s*clave\\)"), "UNIQUE(cuenta, app, clave)")
                                .replace(Regex("PRIMARY KEY\\s*\\(cuenta,\\s*clave\\)"), "PRIMARY KEY(cuenta, app, clave)")
                            val apertura = esquema.indexOf('(') + 1
                            s.execute(esquema.substring(0, apertura) + "app TEXT NOT NULL DEFAULT 'cajon', " + esquema.substring(apertura))
                            val nombres = columnas.joinToString(",")
                            s.execute("INSERT INTO ${tabla}_apps($nombres) SELECT $nombres FROM $tabla")
                            s.execute("DROP TABLE $tabla")
                            s.execute("ALTER TABLE ${tabla}_apps RENAME TO $tabla")
                        }
                    }
                    s.execute("CREATE INDEX IF NOT EXISTS pub_cuando ON publicaciones(cuenta, cuando)")
                    s.execute("CREATE INDEX IF NOT EXISTS pub_app_fecha ON publicaciones(app, cuando)")
                    s.execute("CREATE INDEX IF NOT EXISTS pub_cuenta_app_fecha ON publicaciones(cuenta, app, cuando)")
                    s.execute("CREATE INDEX IF NOT EXISTS retiradas_app_fecha ON retiradas(app, cuando)")
                    columna(s, "sesiones", "app TEXT NOT NULL DEFAULT 'cajon'")
                    columna(s, "avisos", "app TEXT")
                    s.execute("UPDATE avisos SET app = COALESCE((SELECT app FROM publicaciones WHERE CAST(id AS TEXT) = avisos.objeto), 'cajon') WHERE app IS NULL AND tipo IN ('publico','megusta','comentario','respuesta')")
                    s.execute("CREATE TABLE IF NOT EXISTS partes_perfil(cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, app TEXT NOT NULL, clave TEXT NOT NULL, visibilidad TEXT NOT NULL, datos TEXT NOT NULL, PRIMARY KEY(cuenta, app, clave))")
                    s.execute("CREATE TABLE IF NOT EXISTS migraciones(nombre TEXT PRIMARY KEY)")
                    if (leer("SELECT 1 FROM migraciones WHERE nombre = 'partes_apps'") { 1 }.isEmpty()) {
                        leer("SELECT id, perfil FROM cuentas") { it.getLong(1) to it.getString(2) }.forEach { (id, json) ->
                            val perfil = JsonSocial.decodeFromString<PerfilSubido>(json)
                            perfil.partes.forEach { (clave, parte) ->
                                cambiar("INSERT INTO partes_perfil VALUES(?, 'cajon', ?, ?, ?)", id, clave, parte.visibilidad, parte.datos.toString())
                            }
                            cambiar("UPDATE cuentas SET perfil = ? WHERE id = ?", JsonSocial.encodeToString(PerfilSubido.serializer(), perfil.copy(partes = emptyMap())), id)
                        }
                        s.execute("INSERT INTO migraciones VALUES('partes_apps')")
                    }
                    check(leer("PRAGMA foreign_key_check") { it.getString(1) }.isEmpty()) { "Referencias inválidas tras migrar apps" }
                }
            }
        } finally { c.createStatement().use { it.execute("PRAGMA foreign_keys=ON") } }
    }

    private fun migrarEquipo(c: Connection) {
        c.createStatement().use { s ->
            val nueva = c.leer("SELECT name FROM sqlite_master WHERE name = 'roles'") { it.getString(1) }.isEmpty()
            s.execute("CREATE TABLE IF NOT EXISTS roles(cuenta INTEGER PRIMARY KEY REFERENCES cuentas(id) ON DELETE CASCADE, rol TEXT NOT NULL, dado_por TEXT NOT NULL, cuando INTEGER NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS permisos(cuenta INTEGER REFERENCES cuentas(id) ON DELETE CASCADE, permiso TEXT NOT NULL, dado_por TEXT NOT NULL, cuando INTEGER NOT NULL, PRIMARY KEY(cuenta, permiso))")
            s.execute("CREATE TABLE IF NOT EXISTS mfa(cuenta INTEGER PRIMARY KEY REFERENCES cuentas(id) ON DELETE CASCADE, secreto_cifrado TEXT NOT NULL, activa INTEGER NOT NULL DEFAULT 0, creada INTEGER NOT NULL, ultimo_paso INTEGER NOT NULL DEFAULT -1)")
            s.execute("CREATE TABLE IF NOT EXISTS codigos_recuperacion(cuenta INTEGER REFERENCES cuentas(id) ON DELETE CASCADE, huella TEXT NOT NULL, usado INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(cuenta, huella))")
            s.execute("CREATE TABLE IF NOT EXISTS registro_equipo(id INTEGER PRIMARY KEY AUTOINCREMENT, actor TEXT NOT NULL, accion TEXT NOT NULL, objeto TEXT NOT NULL, detalle TEXT NOT NULL, sesion TEXT NOT NULL, cuando INTEGER NOT NULL, huella_anterior TEXT NOT NULL, huella TEXT NOT NULL)")
            if (nueva) {
                s.execute("INSERT INTO roles SELECT id, 'administrador', 'servidor', creada FROM cuentas WHERE admin = 1")
                c.leer("SELECT cuenta FROM roles") { it.getLong(1) }.forEach {
                    c.registrarEquipo("servidor", "equipo_migrar", it.toString(), "administrador", "", System.currentTimeMillis())
                }
            }
            for (tabla in listOf("publicaciones", "comentarios")) {
                columna(s, tabla, "estado TEXT NOT NULL DEFAULT 'visible'")
                columna(s, tabla, "retirado_hasta INTEGER NOT NULL DEFAULT 0")
            }
            s.execute("INSERT OR IGNORE INTO retiradas(cuenta, app, clave, cuando) SELECT cuenta, app, clave, MAX(0, retirado_hasta - 7776000000) FROM publicaciones WHERE estado = 'retirado'")
        }
    }

    private fun migrarSeguridad(conexion: Connection) {
        conexion.autoCommit = false
        try {
            conexion.createStatement().use { s ->
                with(conexion) {
                    columna(s, "sesiones", "dispositivo TEXT NOT NULL DEFAULT ''")
                    val sinUso = s.executeQuery("PRAGMA table_info(sesiones)").use { r ->
                        buildList { while (r.next()) add(r.getString("name")) }
                    }.contains("ultimo_uso").not()
                    columna(s, "sesiones", "ultimo_uso INTEGER NOT NULL DEFAULT 0")
                    if (sinUso) s.execute("UPDATE sesiones SET ultimo_uso = creada")
                    columna(s, "sesiones", "elevada_hasta INTEGER NOT NULL DEFAULT 0")
                    s.execute("CREATE INDEX IF NOT EXISTS sesiones_uso ON sesiones(ultimo_uso)")
                    s.execute("""CREATE TABLE IF NOT EXISTS intentos(
                        cuenta INTEGER PRIMARY KEY REFERENCES cuentas(id) ON DELETE CASCADE,
                        fallos INTEGER NOT NULL, desde INTEGER NOT NULL, bloqueada_hasta INTEGER NOT NULL, nivel INTEGER NOT NULL)""")
                    columna(s, "intentos", "ultimo_fallo INTEGER NOT NULL DEFAULT 0")
                    s.execute("""CREATE TABLE IF NOT EXISTS envios_codigo(correo TEXT NOT NULL, cuando INTEGER NOT NULL)""")
                    s.execute("CREATE INDEX IF NOT EXISTS envios_correo ON envios_codigo(correo, cuando)")
                    s.execute("""CREATE TABLE IF NOT EXISTS apodo_cambios(
                        cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, cuando INTEGER NOT NULL)""")
                    s.execute("CREATE INDEX IF NOT EXISTS apodos_cuenta ON apodo_cambios(cuenta, cuando)")
                    // Traslada los tokens anteriores al modelo de sesiones una sola vez.
                    leer("SELECT id, token, creada FROM cuentas WHERE token NOT LIKE 'retirado:%'") {
                        Triple(it.getLong(1), it.getString(2), it.getLong(3))
                    }.forEach { (id, token, creada) ->
                        cambiar("INSERT OR IGNORE INTO sesiones(huella, cuenta, creada, ultimo_uso) VALUES(?,?,?,?)", token, id, creada, creada)
                        cambiar("UPDATE cuentas SET token = ? WHERE id = ?", "retirado:" + huella(tokenNuevo()), id)
                    }
                }
            }
            conexion.commit()
        } catch (e: Exception) {
            conexion.rollback()
            throw e
        } finally {
            conexion.autoCommit = true
        }
    }

    /** Conserva los sobres pendientes y evita reutilizar los números ya confirmados por los teléfonos. */
    private fun migrarMensajes(conexion: Connection) {
        val esquema = conexion.leer("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'mensajes'") { it.getString(1) }.single()
        if (esquema.contains("AUTOINCREMENT", ignoreCase = true)) return
        conexion.autoCommit = false
        try {
            // Los ids borrados ya no se pueden consultar: la época en ms supera los cursores antiguos de los teléfonos.
            val piso = maxOf(System.currentTimeMillis(), conexion.leer("SELECT COALESCE(MAX(id), 0) FROM mensajes") { it.getLong(1) }.single() + 1)
            conexion.createStatement().use { s ->
                s.execute("""CREATE TABLE mensajes_nuevos(
                    id INTEGER PRIMARY KEY AUTOINCREMENT, de INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                    para INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, cuando INTEGER NOT NULL, sobre TEXT NOT NULL, firma TEXT NOT NULL)""")
                // También los pendientes pueden tener ids reutilizados por debajo del cursor del receptor.
                conexion.cambiar("""INSERT INTO mensajes_nuevos
                    SELECT ? + ROW_NUMBER() OVER (ORDER BY id), de, para, cuando, sobre, firma FROM mensajes ORDER BY id""", piso)
                s.execute("DROP TABLE mensajes")
                s.execute("ALTER TABLE mensajes_nuevos RENAME TO mensajes")
                s.execute("CREATE INDEX mensajes_para ON mensajes(para, id)")
            }
            val secuencia = maxOf(piso, conexion.leer("SELECT COALESCE(MAX(id), 0) FROM mensajes") { it.getLong(1) }.single())
            conexion.cambiar("DELETE FROM sqlite_sequence WHERE name = 'mensajes'")
            conexion.cambiar("INSERT INTO sqlite_sequence(name, seq) VALUES('mensajes', ?)", secuencia)
            conexion.commit()
        } catch (e: Exception) {
            conexion.rollback()
            throw e
        } finally {
            conexion.autoCommit = true
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

    /** Una acción y su registro se confirman juntos; permite llamadas anidadas. */
    fun <T> transaccion(f: Connection.() -> T): T = usar {
        if (!autoCommit) return@usar f()
        autoCommit = false
        try { val resultado = f(); commit(); resultado }
        catch (e: Throwable) { rollback(); throw e }
        finally { autoCommit = true }
    }

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
