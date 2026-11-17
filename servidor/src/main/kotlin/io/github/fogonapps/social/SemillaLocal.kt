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
        val vecinos = listOf(cuenta("muestrauno"), cuenta("muestrados"))
        val ahora = System.currentTimeMillis()
        b.transaccion {
            cambiar("INSERT OR IGNORE INTO sesiones(huella,cuenta,creada,ultimo_uso) VALUES(?,?,?,?)", Base.huella(token), yo, ahora, ahora)
            cambiar("UPDATE cuentas SET acepto=?, acepto_cuando=? WHERE id=?", Social.POLITICA, ahora, yo)
            if (leer("SELECT 1 FROM chispas WHERE cuenta=? AND motivo='semilla_local'", yo) { 1 }.isEmpty())
                cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,50000,'semilla_local',?)", yo, ahora)
            vecinos.forEach { cambiar("INSERT OR IGNORE INTO seguir(de,a,cuando,aceptada) VALUES(?,?,?,1)", yo, it, ahora) }
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
