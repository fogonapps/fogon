package io.github.fogonapps.cajon.datos

import io.github.fogonapps.cajon.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

typealias ErrorSocial = io.github.fogonapps.social.ErrorSocial
typealias PersonaSocial = io.github.fogonapps.social.PersonaSocial
typealias PublicacionSocial = io.github.fogonapps.social.PublicacionSocial
typealias ComentarioSocial = io.github.fogonapps.social.ComentarioSocial
typealias PerfilSocial = io.github.fogonapps.social.PerfilSocial
typealias AvisoSocial = io.github.fogonapps.social.AvisoSocial
typealias ParteSubida = io.github.fogonapps.social.ParteSubida
typealias PerfilSubido = io.github.fogonapps.social.PerfilSubido
typealias PublicacionSubida = io.github.fogonapps.social.PublicacionSubida
typealias Acceso = io.github.fogonapps.social.Acceso
typealias MiCuenta = io.github.fogonapps.social.MiCuenta
typealias CasoModeracion = io.github.fogonapps.social.CasoModeracion
typealias FaltaSocial = io.github.fogonapps.social.FaltaSocial
typealias EstadoCuentaSocial = io.github.fogonapps.social.EstadoCuentaSocial
typealias SesionSocial = io.github.fogonapps.social.SesionSocial
typealias SesionMostrada = io.github.fogonapps.social.SesionMostrada
typealias InfoServidor = io.github.fogonapps.social.InfoServidor
typealias CambiosPublicaciones = io.github.fogonapps.social.CambiosPublicaciones
typealias LlavesPublicas = io.github.fogonapps.social.LlavesPublicas
typealias Sobre = io.github.fogonapps.social.Sobre

fun nombreDispositivo(fabricante: String, modelo: String) = io.github.fogonapps.social.nombreDispositivo(fabricante, modelo)
fun SesionSocial.paraPantalla() = SesionMostrada(id, dispositivo.trim().takeIf { it.isNotEmpty() }, creada, ultimoUso, actual)
fun esCuentaNueva(error: ErrorSocial) = io.github.fogonapps.social.esCuentaNueva(error)

@Serializable data class CajonCifrado(val sobre: String = "", val cambio: Long = 0, val llave: String = "")
@Serializable data class Buscado(val titulo: String, val vistas: Long = 0, val wikidata: String = "", val tmdb: String = "")
@Serializable data class DelMomentoServidor(val dia: String = "", val peliculas: List<Buscado> = emptyList(), val series: List<Buscado> = emptyList(), val anime: List<Buscado> = emptyList())
typealias ConteoFrase = io.github.fogonapps.social.ConteoFrase
typealias VotoFrase = io.github.fogonapps.social.VotoFrase
typealias FraseNueva = io.github.fogonapps.social.FraseNueva
typealias FraseComunidad = io.github.fogonapps.social.FraseComunidad
typealias DecisionFrase = io.github.fogonapps.social.DecisionFrase
@Serializable data class NotaNueva(val texto: String, val datos: JsonObject = JsonObject(emptyMap()))
@Serializable data class NotaSocial(val persona: PersonaSocial, val texto: String, val datos: JsonObject = JsonObject(emptyMap()), val cuando: Long)

class ClienteSocial(token: String? = null, permiteRed: () -> Boolean = { true }) :
    io.github.fogonapps.social.ClienteSocial(token, permiteRed, io.github.fogonapps.social.ConfiguracionSocial("cajon", "El Cajón", "ElCajon-Fogon")) {
    suspend fun portadas(fuente: String): PortadasComunidad = leer(PortadasComunidad.serializer(), pedir("/obras/imagen?fuente=${cod(fuente)}"))
    suspend fun votarPortada(fuente: String, url: String?) {
        if (url == null) pedir("/obras/imagen?fuente=${cod(fuente)}", "DELETE")
        else pedir("/obras/imagen", "PUT", kotlinx.serialization.json.buildJsonObject {
            put("fuente", JsonPrimitive(fuente)); put("url", JsonPrimitive(url))
        }.toString().toRequestBody(JSON))
    }

    suspend fun cajon() = leer(CajonCifrado.serializer(), pedir("/cajon"))
    /** Guarda el cajón cifrado; [antes] es la versión que se trajo (si hay otra más nueva, ErrorSocial 409 "cambio"). */
    suspend fun guardarCajon(sobre: String, antes: Long): Long =
        JsonCajon.parseToJsonElement(pedir("/cajon?antes=$antes", "PUT", json(CajonCifrado.serializer(), CajonCifrado(sobre)))!!).jsonObject["cambio"]!!.jsonPrimitive.content.toLong()
    suspend fun guardarLlaveCajon(envuelta: String) { pedir("/cajon/llave", "PUT", json(CajonCifrado.serializer(), CajonCifrado(llave = envuelta))) }
    /** Las reseñas de una obra que puede ver esta persona (las públicas y las de quienes sigue). */
    suspend fun resenas(fuente: String, parte: String = "", orden: String = "utiles", antes: Long? = null) =
        leer(ListSerializer(PublicacionSocial.serializer()), pedir("/resenas?fuente=${cod(fuente)}&parte=${cod(parte)}&orden=${cod(orden)}" + (antes?.let { "&antes=$it" } ?: "")))
    suspend fun momento(idioma: String): DelMomentoServidor = leer(DelMomentoServidor.serializer(), pedir("/momento?idioma=${cod(idioma)}"))


    suspend fun ponerNota(n: NotaNueva) { pedir("/nota", "PUT", json(NotaNueva.serializer(), n)) }
    suspend fun notas() = leer(ListSerializer(NotaSocial.serializer()), pedir("/notas"))
    companion object {
        val cuentasPorBorrar = io.github.fogonapps.social.ClienteSocial.cuentasPorBorrar
        const val SERVIDOR = io.github.fogonapps.social.ClienteSocial.SERVIDOR
        fun enlacePerfil(usuario: String) = io.github.fogonapps.social.ClienteSocial.enlacePerfil(usuario)
        fun enlacePublicacion(id: Long) = io.github.fogonapps.social.ClienteSocial.enlacePublicacion(id)
        fun foto(usuario: String, version: Long = 0) = io.github.fogonapps.social.ClienteSocial.foto(usuario, version)
        fun video(nombre: String) = io.github.fogonapps.social.ClienteSocial.video(nombre)
        fun imagen(nombre: String) = io.github.fogonapps.social.ClienteSocial.imagen(nombre)
        private val JSON = "application/json".toMediaType()
    }
}

/** Campos legibles del contenido reportado, sin acciones ni efectos de la publicación. */
data class ContenidoReporte(
    val titulo: String = "", val portada: String = "", val tipoObra: String = "",
    val anio: String = "", val creador: String = "", val texto: String = "",
    val video: String = "", val poster: String = "", val duracion: Int = 0,
)

fun leerContenidoReporte(contenido: String): ContenidoReporte {
    val elemento = try {
        kotlinx.serialization.json.Json.parseToJsonElement(contenido)
    } catch (_: IllegalArgumentException) {
        return ContenidoReporte(texto = contenido.take(600))
    }
    fun campo(nombre: String): String = ((elemento as? JsonObject)?.get(nombre) as? kotlinx.serialization.json.JsonPrimitive)
        ?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.content.orEmpty()
    return ContenidoReporte(
        titulo = campo("titulo"), portada = campo("portada"), tipoObra = campo("tipoObra"),
        anio = campo("anio"), creador = campo("creador"),
        video = campo("video"), poster = campo("poster"), duracion = campo("duracion").toIntOrNull() ?: 0,
        texto = campo("texto").ifBlank {
            (elemento as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.content.orEmpty()
        }.take(600),
    )
}

val MOTIVOS_REPORTE = listOf(
    "menor_en_riesgo" to R.string.rep_menor, "desnudos" to R.string.rep_desnudos, "acoso" to R.string.rep_acoso, "odio" to R.string.rep_odio,
    "autolesion" to R.string.rep_autolesion, "spam" to R.string.rep_spam, "menor_de_edad" to R.string.rep_menor_de_edad,
    "derechos" to R.string.rep_autor, "falso" to R.string.rep_falso, "otro" to R.string.rep_otro,
)

/** Presentación traducida de una falta y los puntos que suma. */
fun textoFalta(falta: FaltaSocial, traducir: (Int) -> String): String {
    val regla = MOTIVOS_REPORTE.firstOrNull { it.first == falta.regla }?.second ?: when (falta.regla) {
        "evadir_sancion" -> R.string.mod_evadir_sancion
        "amenaza_real" -> R.string.rep_violencia
        else -> R.string.rep_otro
    }
    val puntos = if (falta.puntos == 2) traducir(R.string.mod_grave_puntos)
        else if (falta.puntos == 1) traducir(R.string.mod_un_punto)
        else if (falta.puntos >= 4) traducir(R.string.mod_cierre)
        else String.format(traducir(R.string.mod_puntos), falta.puntos)
    return "${traducir(regla)} · $puntos"
}

/** El servidor une los reportes de mensajes con un separador; cada uno lleva su motivo. */
fun limpiarReporteMensaje(contenido: String): String = contenido.split("\n---\n").joinToString("\n---\n") { reporte ->
    val cabecera = reporte.substringBefore('\n')
    if ('\n' in reporte && MOTIVOS_REPORTE.any { cabecera.startsWith("${it.first}:") })
        reporte.substringAfter('\n')
    else reporte
}

fun quitarCuentaBloqueada(gente: Map<String, List<PersonaSocial>>, usuario: String) = io.github.fogonapps.social.quitarCuentaBloqueada(gente, usuario)
fun quitarPublicacionesBloqueadas(feed: List<PublicacionSocial>?, usuario: String) = io.github.fogonapps.social.quitarPublicacionesBloqueadas(feed, usuario)
