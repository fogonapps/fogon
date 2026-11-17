package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class Escaparate(val tipo: String, val datos: JsonObject)
@Serializable data class FotoEscaparate(val publicacion: Long, val nombre: String)
@Serializable data class CupoEscaparates(val nivel: Int, val gratis: Int, val comprados: Int, val total: Int, val insignias: List<String> = emptyList())

/** Bloques ordenados de la identidad global; cada app aporta instantáneas explícitas. */
class Escaparates(private val social: Social, private val b: Base, private val ahora: () -> Long) {
    companion object {
        val TIPOS = setOf("galeria", "insignias", "texto", "numero", "cajon:favoritas", "cajon:resena", "cajon:lista", "cajon:albumes",
            "forzudo:records", "forzudo:racha", "forzudo:entreno", "cucha:receta", "cucha:racha", "alcancia:meta")
        // Solo identificadores existentes, distintos: ni la insignia de verificación ni las compradas dan nivel.
        val INSIGNIAS = setOf("cine", "series", "anime", "lector", "discos", "constancia", "critico", "explorador", "reto")
        fun calcularCupo(insignias: Int, extras: Int): CupoEscaparates {
            val nivel = insignias.coerceAtLeast(0)
            val gratis = minOf(6, 2 + nivel / 3)
            val comprados = extras.coerceIn(0, 3)
            return CupoEscaparates(nivel, gratis, comprados, gratis + comprados)
        }
    }

    fun cupo(yo: Long): CupoEscaparates = b.usar {
        val ganadas = leer("SELECT datos FROM partes_perfil WHERE cuenta = ? AND app = 'cajon' AND clave = 'insignias'", yo) {
            (JsonSocial.parseToJsonElement(it.getString(1)) as? JsonArray).orEmpty().mapNotNull { v -> (v as? JsonPrimitive)?.content }.toSet().intersect(INSIGNIAS).sorted()
        }.firstOrNull() ?: emptyList()
        val extras = leer("SELECT tema FROM temas WHERE cuenta = ?", yo) { it.getString(1) }.count { it in TemasPerfil.EXTRAS }
        calcularCupo(ganadas.size, extras).copy(insignias = ganadas)
    }

    fun fotos(yo: Long): List<FotoEscaparate> = b.usar {
        leer("SELECT id, datos FROM publicaciones WHERE cuenta = ? AND estado = 'visible' AND oculta = 0 ORDER BY cuando DESC LIMIT 500", yo) {
            it.getLong(1) to JsonSocial.parseToJsonElement(it.getString(2)).jsonObject
        }
    }.flatMap { (id, d) ->
        val lista = (d["imagenes"] ?: d["fotos"]) as? JsonArray
        val nombres = lista?.mapNotNull { (it as? JsonPrimitive)?.content } ?: listOfNotNull((d["imagen"] as? JsonPrimitive)?.content)
        nombres.map { FotoEscaparate(id, it) }
    }.filter { fotoValida(yo, yo, buildJsonObject { put("publicacion", it.publicacion); put("nombre", it.nombre) }) }

    fun guardar(yo: Long, lista: List<Escaparate>) {
        val antes = fotosPropias(b.usar { leer("SELECT datos FROM escaparates WHERE cuenta = ? AND tipo = 'galeria'", yo) { it.getString(1) } })
        b.transaccion {
            if (lista.size > cupo(yo).total) throw Problema(400, "escaparates_cupo")
            lista.forEach { validar(yo, it) }
            cambiar("DELETE FROM escaparates WHERE cuenta = ?", yo)
            lista.forEachIndexed { posicion, e -> cambiar("INSERT INTO escaparates VALUES(?,?,?,?,?)", yo, posicion, e.tipo, e.datos.toString(), ahora()) }
        }
        // Una foto subida para el escaparate que ya no está en él (ni en nada más) se borra.
        val ahoraFotos = fotosPropias(lista.filter { it.tipo == "galeria" }.map { it.datos.toString() })
        (antes - ahoraFotos).forEach { social.borrarImagenSuelta(it) }
    }

    private fun fotosPropias(datos: List<String>): Set<String> = datos.flatMap { texto ->
        (Json.parseToJsonElement(texto).jsonObject["fotos"] as? JsonArray).orEmpty().mapNotNull { f ->
            (f as? JsonObject)?.takeIf { "publicacion" !in it }?.get("nombre")?.jsonPrimitive?.content
        }
    }.toSet()

    fun ver(lector: Long?, usuario: String): List<Escaparate> {
        val perfil = social.verPerfil(lector, usuario)
        if (perfil.cerrada) return emptyList()
        val yo = b.usar { leer("SELECT id FROM cuentas WHERE usuario = ?", perfil.persona.usuario) { it.getLong(1) }.single() }
        return b.usar { leer("SELECT tipo, datos FROM escaparates WHERE cuenta = ? ORDER BY posicion", yo) {
            Escaparate(it.getString(1), JsonSocial.parseToJsonElement(it.getString(2)).jsonObject)
        } }.map { e ->
            if (e.tipo != "galeria") e else e.copy(datos = buildJsonObject {
                put("fotos", JsonArray(e.datos.getValue("fotos").jsonArray.filter { foto -> fotoValida(yo, lector, foto.jsonObject) }))
            })
        }
    }

    /** Revalida origen, moderación y visibilidad al guardar y en cada lectura. */
    private fun fotoValida(yo: Long, lector: Long?, foto: JsonObject): Boolean {
        val id = (foto["publicacion"] as? JsonPrimitive)?.longOrNull
        if (id == null && "publicacion" in foto) return false
        val nombre = (foto["nombre"] as? JsonPrimitive)?.content ?: return false
        if (id == null) return b.usar { leer("SELECT 1 FROM imagenes WHERE nombre = ? AND cuenta = ?", nombre, yo) { 1 }.isNotEmpty() }
        val vigente = b.usar { leer("SELECT 1 FROM publicaciones p JOIN imagenes i ON i.cuenta = p.cuenta WHERE p.id = ? AND p.cuenta = ? AND p.estado = 'visible' AND p.oculta = 0 AND i.nombre = ?", id, yo, nombre) { 1 }.isNotEmpty() }
        if (!vigente) return false
        val p = social.publicacion(lector, id) ?: return false
        val d = p.datos
        val fotos = (d["imagenes"] ?: d["fotos"]) as? JsonArray
        return if (fotos != null) fotos.any { (it as? JsonPrimitive)?.content == nombre } else (d["imagen"] as? JsonPrimitive)?.content == nombre
    }

    /** Solo las fotos propias explícitas heredan el acceso al perfil. */
    fun permiteImagen(nombre: String, lector: Long?): Boolean {
        val usuarios = b.usar { leer("SELECT DISTINCT c.usuario FROM escaparates e JOIN cuentas c ON c.id = e.cuenta JOIN imagenes i ON i.cuenta = e.cuenta WHERE e.tipo = 'galeria' AND i.nombre = ?", nombre) { it.getString(1) } }
        return usuarios.any { usuario ->
            try { ver(lector, usuario).any { e -> e.tipo == "galeria" && e.datos["fotos"]!!.jsonArray.any {
                val f = it.jsonObject
                "publicacion" !in f && f["nombre"]?.jsonPrimitive?.content == nombre
            } } } catch (_: Problema) { false }
        }
    }

    private fun validar(yo: Long, e: Escaparate) {
        fun exige(si: Boolean) { if (!si) throw Problema(400, "escaparate_datos") }
        if (e.tipo !in TIPOS) throw Problema(400, "escaparate_tipo")
        exige(e.datos.toString().length <= 12_000)
        val d = e.datos
        fun texto(clave: String, max: Int, requerido: Boolean = true) {
            val v = d[clave] as? JsonPrimitive
            exige(if (v == null) !requerido else v.isString && v.content.codePointCount(0, v.content.length) <= max)
        }
        when (e.tipo) {
            "galeria" -> {
                exige(d.keys == setOf("fotos"))
                val fotos = d["fotos"] as? JsonArray
                exige(fotos != null && fotos.size <= 6 && fotos.distinct().size == fotos.size)
                fotos!!.forEach { f ->
                    val foto = f as? JsonObject
                    exige(foto != null && (foto.keys == setOf("publicacion", "nombre") || foto.keys == setOf("nombre")))
                    if (!fotoValida(yo, yo, foto!!)) throw Problema(400, "escaparate_foto")
                }
            }
            "texto" -> { exige(d.keys == setOf("texto")); texto("texto", 300) }
            "numero", "forzudo:racha", "cucha:racha", "alcancia:meta" -> {
                exige(d.keys == setOf("numero", "etiqueta")); texto("etiqueta", 80)
                val numero = (d["numero"] as? JsonPrimitive)?.doubleOrNull
                exige(numero != null && numero.isFinite() && numero >= 0 && numero <= 999999999)
                if (e.tipo == "alcancia:meta") {
                    exige(numero!! <= 100)
                    // No admite títulos, descripciones ni ningún campo que pueda transportar montos.
                    exige((d["etiqueta"] as? JsonPrimitive)?.content == "%")
                }
            }
            "insignias" -> {
                exige(d.keys == setOf("insignias"))
                val ids = d["insignias"] as? JsonArray
                exige(ids != null && ids.size <= INSIGNIAS.size && ids.all { (it as? JsonPrimitive)?.content in INSIGNIAS })
            }
            else -> {
                exige(d.keys == setOf("elementos"))
                val elementos = d["elementos"] as? JsonArray
                val limite = when (e.tipo) { "cajon:favoritas" -> 4; "cajon:albumes", "forzudo:records" -> 6; else -> 1 }
                exige(elementos != null && elementos.size <= limite)
                elementos!!.forEach { elemento ->
                    val v = elemento as? JsonObject
                    exige(v != null && v.keys.all { it in setOf("titulo", "texto", "portada") })
                    v!!.forEach { (clave, valor) ->
                        val t = valor as? JsonPrimitive
                        exige(t != null && t.isString && t.content.length <= if (clave == "texto") 300 else 500)
                        if (clave == "portada") exige(t!!.content.isEmpty() || t.content.startsWith("https://"))
                    }
                }
            }
        }
    }
}
