package io.github.fogonapps.social

import java.io.File
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.serialization.json.*

/** Nunca se invoca por HTTP ni durante el arranque normal. */
internal fun sembrarSocialLocal(carpeta: File): String {
    val datos = carpeta.canonicalFile
    require(datos.name == "social-pruebas" && datos.parentFile.name == "Fogon-local") {
        "La semilla exige Fogon-local/social-pruebas fuera del repositorio"
    }
    val repo = generateSequence(File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
        .firstOrNull { File(it, ".git").exists() } ?: error("Ejecuta la semilla desde el repositorio")
    require(!datos.toPath().startsWith(repo.toPath())) { "DATOS debe estar fuera del repositorio" }
    val marca = File(datos, ".social-pruebas")
    require(marca.exists() || !File(datos, "social.db").exists()) { "No se siembra una base existente sin marca de pruebas" }
    datos.mkdirs()
    marca.writeText("Solo datos ficticios de Fogón Social")
    val token = "fogon-local-prueba-sin-validez-en-produccion"
    val b = Base(File(datos, "social.db").path)
    try {
        val social = Social(b, File(datos, "fotos"), principal = null)
        fun cuenta(usuario: String): Long {
            if (social.usuarioLibre(usuario)) social.crearCuenta(NuevaCuenta(usuario, "Cuenta ficticia $usuario"))
            return b.usar { leer("SELECT id FROM cuentas WHERE usuario=?", usuario) { it.getLong(1) }.single() }
        }
        val yo = cuenta("prueba")
        val vecinos = listOf(cuenta("muestrauno"), cuenta("muestrados"), cuenta("muestratres"))
        val ahora = System.currentTimeMillis()
        b.transaccion {
            cambiar("INSERT OR IGNORE INTO sesiones(huella,cuenta,creada,ultimo_uso) VALUES(?,?,?,?)", Base.huella(token), yo, ahora, ahora)
            cambiar("UPDATE cuentas SET acepto=?, acepto_cuando=? WHERE id=?", Social.POLITICA, ahora, yo)
            if (leer("SELECT 1 FROM chispas WHERE cuenta=? AND motivo='semilla_local'", yo) { 1 }.isEmpty())
                cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,50000,'semilla_local',?)", yo, ahora)
            // Sin volver a seguir a quien se bloqueó en una corrida anterior.
            vecinos.forEach { cambiar("""INSERT OR IGNORE INTO seguir(de,a,cuando,aceptada) SELECT ?,?,?,1
                WHERE NOT EXISTS(SELECT 1 FROM bloqueos WHERE (de=? AND a=?) OR (de=? AND a=?))""", yo, it, ahora, yo, it, it, yo) }
        }
        social.subirPerfil(yo, PerfilSubido(nombre = "Cuenta ficticia de prueba", partes = mapOf("insignias" to Parte(Vis.PUBLICA,
            JsonArray(listOf("series", "anime", "lector").map(::JsonPrimitive))))))
        social.escaparates.guardar(yo, listOf(Escaparate("cajon:albumes", buildJsonObject {
            put("elementos", buildJsonArray {
                add(buildJsonObject { put("titulo", "Disco de muestra"); put("texto", "Artista ficticio"); put("portada", "") })
                add(buildJsonObject { put("titulo", "Otro disco de muestra"); put("texto", "Grupo ficticio"); put("portada", "") })
            })
        })))
        // Imágenes geométricas propias, sin descargar obras ni contactar catálogos.
        listOf("banner", "marco").forEachIndexed { indice, tipo ->
            val existe = b.usar { leer("SELECT 1 FROM mercado WHERE clave=?", "semilla_$tipo") { 1 }.isNotEmpty() }
            if (!existe) {
                val imagen = BufferedImage(512, 512, if (tipo == "marco") BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
                imagen.createGraphics().apply {
                    color = Color(40 + indice * 50, 80, 120)
                    if (tipo == "marco") { stroke = java.awt.BasicStroke(24f); drawRect(12, 12, 488, 488) }
                    else fillRect(0, 0, 512, 512)
                    if (tipo != "marco") { color = Color(180, 160, 100); fillOval(100, 100, 312, 312) }; dispose()
                }
                val bytes = ByteArrayOutputStream().also { ImageIO.write(imagen, if (tipo == "marco") "png" else "jpg", it) }.toByteArray()
                val nombre = social.subirImagen(vecinos[0], bytes).nombre
                b.usar { cambiar("""INSERT INTO mercado(cuenta,clave,imagen,nombre,tipo,precio,oficial,estado,motivo,cuando)
                    VALUES(?,?,?,?,?,100,0,'aprobado','',?)""", vecinos[0], "semilla_$tipo", nombre, "Muestra local $tipo", tipo, ahora) }
            }
        }
        // Dos perfiles públicos de muestra, para probar el perfil ajeno: repisas de fotos (una y dos), texto,
        // número e insignias. Las fotos son colores lisos generados aquí.
        fun fotoLisa(cuenta: Long, rgb: Int): String {
            val imagen = BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB)
            imagen.createGraphics().apply { color = Color(rgb); fillRect(0, 0, 640, 480); dispose() }
            val bytes = ByteArrayOutputStream().also { ImageIO.write(imagen, "jpg", it) }.toByteArray()
            return social.subirImagen(cuenta, bytes).nombre
        }
        fun galeria(vararg nombres: String) = Escaparate("galeria", buildJsonObject {
            put("fotos", JsonArray(nombres.map { n -> buildJsonObject { put("nombre", n) } }))
        })
        fun insignias(vararg ids: String) = Parte(Vis.PUBLICA, JsonArray(ids.map(::JsonPrimitive)))
        listOf(
            Triple(vecinos[0], "Muestra uno", "Lectora de domingo y series de misterio."),
            Triple(vecinos[1], "Muestra dos", "Cine, discos y una libreta de recetas."),
        ).forEachIndexed { i, (id, nombre, bio) ->
            social.subirPerfil(id, PerfilSubido(nombre = nombre, bio = bio,
                partes = mapOf("insignias" to if (i == 0) insignias("lector", "series", "constancia") else insignias("cine", "discos", "critico", "explorador"))))
            if (b.usar { leer("SELECT 1 FROM escaparates WHERE cuenta=?", id) { 1 }.isEmpty() }) {
                val repisas = if (i == 0) listOf(galeria(fotoLisa(id, 0x3A7CA5)),
                    Escaparate("texto", buildJsonObject { put("texto", "Un capítulo más y me duermo.") }))
                else listOf(galeria(fotoLisa(id, 0xD9822B), fotoLisa(id, 0x5B8E55)),
                    Escaparate("numero", buildJsonObject { put("numero", 42); put("etiqueta", "películas este año") }))
                social.escaparates.guardar(id, repisas)
            }
        }
        // Para probar «En común contigo»: lo que publicó cada vecino (reseñas y diario de obras de dominio público, con las
        // mismas fuentes ficticias que la colección de prueba de la app) y los artistas que escucha. Muestra dos coincide
        // en varias (y opina distinto en una); Muestra uno, en una sola: «todavía no hay suficiente».
        fun obra(clave: String, tipo: String, titulo: String, tipoObra: String, fuente: String, estrellas: Double, cuando: Long) =
            PublicacionSubida(clave, Vis.PUBLICA, cuando, tipo, buildJsonObject {
                put("titulo", titulo); put("tipoObra", tipoObra); put("portada", ""); put("fuente", fuente); put("estrellas", estrellas)
                if (tipo == "resena") { put("nota", (estrellas * 2).toInt()); put("texto", "Reseña de muestra."); put("parte", ""); put("spoiler", false) }
                put("fecha", java.time.LocalDate.now().toString())
            })
        social.subirPublicaciones(vecinos[1], listOf(
            obra("s1", "resena", "Nosferatu", "pelicula", "tmdb:movie:00", 4.5, ahora - 1_000),
            obra("s2", "diario", "Metrópolis", "pelicula", "tmdb:movie:00000", 5.0, ahora - 2_000),
            obra("s3", "resena", "El maquinista de La General", "pelicula", "tmdb:movie:0000000", 4.0, ahora - 3_000),
            obra("s4", "diario", "Viaje a la Luna", "pelicula", "tmdb:movie:000000000", 1.5, ahora - 4_000),
            obra("s5", "resena", "Arias de Caruso", "album", "mb:rg:0000000000000000000", 4.5, ahora - 5_000),
            obra("s6", "resena", "Amanecer", "pelicula", "tmdb:movie:631", 5.0, ahora - 6_000),
            obra("s7", "diario", "El acorazado Potemkin", "pelicula", "tmdb:movie:643", 4.5, ahora - 7_000),
        ))
        social.subirPublicaciones(vecinos[0], listOf(obra("u1", "diario", "Frankenstein", "libro", "ol:OL00000000000000", 4.0, ahora - 1_000)))
        social.subirPerfil(vecinos[1], PerfilSubido(nombre = "Muestra dos", bio = "Cine, discos y una libreta de recetas.", partes = mapOf(
            "insignias" to insignias("cine", "discos", "critico", "explorador"),
            "musica" to Parte(Vis.PUBLICA, JsonArray(listOf(buildJsonObject {
                put("nombre", "Enrico Caruso"); put("foto", ""); put("fuente", "mb:artist:e4d389bb-fe8f-472d-8ffd-c2cb02a7b2b7") }))))))
        // Muestra tres no publica nada: solo su colección (la lista corta), como quien usa Fabs sin compartir.
        social.subirPerfil(vecinos[2], PerfilSubido(nombre = "Muestra tres", bio = "No publico, solo anoto."))
        runCatching { social.subirColeccion(vecinos[2], Coleccion(listOf(
            ObraColeccion("tmdb:movie:00", "pelicula", "Nosferatu", 10, true),
            ObraColeccion("tmdb:movie:00000", "pelicula", "Metrópolis", 9, true),
            ObraColeccion("tmdb:movie:00000000000", "pelicula", "El gabinete del doctor Caligari", 8, true),
            ObraColeccion("ol:OL00000000000000", "libro", "Frankenstein", 9, true),
            ObraColeccion("ol:OL14860424W", "libro", "Rayuela", 7),
            ObraColeccion("mb:rg:0000000000000000000", "album", "Arias de Caruso", 0),
            ObraColeccion("ol:OL27448W", "libro", "El señor de los anillos", 10, true),
            ObraColeccion("tmdb:movie:3082", "pelicula", "Tiempos modernos", 9, true),
        ))) } // Una vez por minuto: al volver a sembrar enseguida, queda la de antes.
        // Como el adaptador Android: una portada solo local se publica sin imagen.
        val portada = ""
        val albumes = buildJsonObject {
            put("elementos", buildJsonArray {
                add(buildJsonObject { put("titulo", "Disco de muestra"); put("texto", "Artista ficticio"); put("portada", portada) })
                add(buildJsonObject { put("titulo", "Disco sin portada"); put("texto", "Grupo ficticio"); put("portada", "") })
            })
        }
        b.usar { cambiar("UPDATE escaparates SET datos=? WHERE cuenta=? AND tipo='cajon:albumes'", albumes.toString(), yo) }
    } finally { b.cerrar() }
    println("Sesión ficticia local: $token")
    return token
}
