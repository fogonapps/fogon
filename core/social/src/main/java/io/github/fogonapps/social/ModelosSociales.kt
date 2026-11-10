package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.IOException

/** Un error del servidor social: el código HTTP y la palabra que manda ("usuario_tomado", "privada", "despacio"…). */
class ErrorSocial(val codigo: Int, val error: String, val desde: Long = 0) : IOException("HTTP $codigo $error")

/* Lo que devuelve Fogón Social (los mismos campos que el servidor). */

@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.PersonaSocial")
data class PersonaSocial(val usuario: String, val nombre: String = "", val foto: Boolean = false, val privada: Boolean = false)

@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.PublicacionSocial")
data class PublicacionSocial(
    val id: Long, val autor: PersonaSocial, val tipo: String, val cuando: Long, val visibilidad: String, val datos: JsonObject,
    val megusta: Int = 0, val comentarios: Int = 0, val yoMeGusta: Boolean = false, val yoGuardo: Boolean = false, val vistas: Int = 0,
    /** Qué tan bien le fue comparada con lo normal (1 = normal), la misma para todos: la calcula el servidor. */
    val calidad: Double = 1.0, val guardados: Int = 0,
    val utiles: Int = 0, val yoUtil: Boolean = false,
)

@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.ComentarioSocial")
data class ComentarioSocial(val id: Long, val autor: PersonaSocial, val texto: String, val cuando: Long, val puedoBorrar: Boolean = false, val padre: Long? = null, val respuestas: Int = 0, val megusta: Int = 0, val yoMeGusta: Boolean = false, val borrado: Boolean = false)

@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.PerfilSocial")
data class PerfilSocial(
    val persona: PersonaSocial, val bio: String = "", val portada: String = "", val enlaces: List<String> = emptyList(),
    val partes: Map<String, JsonElement> = emptyMap(), val publicaciones: List<PublicacionSocial> = emptyList(),
    /** "yo", "sigo", "pendiente" o "nada". */
    val relacion: String = "nada", val meSigue: Boolean = false,
    val seguidores: Int = 0, val siguiendo: Int = 0, val cuantas: Int = 0, val cerrada: Boolean = false,
    val megusta: Int = 0, val guardadasVisibles: Boolean = false,
)

@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.AvisoSocial")
data class AvisoSocial(val id: Long, val tipo: String, val de: PersonaSocial, val objeto: String = "", val cuando: Long, val leido: Boolean = false)

@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.ParteSubida")
data class ParteSubida(val visibilidad: String, val datos: JsonElement)

@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.PerfilSubido")
data class PerfilSubido(
    val nombre: String = "", val bio: String = "", val portada: String = "", val enlaces: List<String> = emptyList(),
    val partes: Map<String, ParteSubida> = emptyMap(),
)

@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.PublicacionSubida")
data class PublicacionSubida(val clave: String, val visibilidad: String, val cuando: Long, val tipo: String, val datos: JsonObject)

@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.Entrada")
internal data class Entrada(
    val correo: String = "", val codigo: String = "", val usuario: String = "", val nombre: String = "",
    val acepta: String = "", val mayorDeEdad: Boolean = false, val clave: String = "", val app: String? = null,
)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.PoliticaAceptada")
internal data class PoliticaAceptada(val politica: String)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.Correo")
internal data class Correo(val correo: String, val app: String? = null)
/** La sesión que abre el servidor: [nueva] si se acaba de crear la cuenta. */
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.Acceso")
data class Acceso(val usuario: String, val token: String, val nueva: Boolean = false, val borrarEl: Long = 0, val idCuenta: String = "")
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.MiCuenta")
data class MiCuenta(val usuario: String, val nombre: String = "", val correo: String = "", val privada: Boolean = false,
    val conClave: Boolean = false, val apodoDesde: Long = 0, val usuarioDesde: Long = 0, val admin: Boolean = false, val borrarEl: Long = 0,
    val rol: String = "", val permisos: Set<String> = emptySet(), val mfa: Boolean = false, val elevadaHasta: Long = 0, val idCuenta: String = "", val politica: String = "")
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.CasoModeracion")
data class CasoModeracion(val que: String, val objeto: String, val motivos: Map<String, Int> = emptyMap(), val personas: Int = 0, val oculto: Boolean = false,
    val contenido: String = "", val autor: PersonaSocial? = null, val faltasAutor: Int = 0, val estado: String = "visible", val retiradoHasta: Long? = null)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.Decision")
internal data class Decision(val que: String, val objeto: String, val decision: String, val regla: String = "")
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.FaltaSocial")
data class FaltaSocial(val regla: String, val puntos: Int, val cuando: Long, val vence: Long)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.EstadoCuentaSocial")
data class EstadoCuentaSocial(val faltas: List<FaltaSocial> = emptyList(), val restringidaHasta: Long = 0, val suspendidaHasta: Long = 0,
    val cerrada: Boolean = false, val admin: Boolean = false)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.EntradaClave")
internal data class EntradaClave(val correo: String, val clave: String, val app: String? = null)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.Borrado")
internal data class Borrado(val clave: String, val codigo: String, val motivo: String, val motivoTexto: String)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.CambioCorreo")
internal data class CambioCorreo(val nuevo: String, val clave: String = "", val codigo: String = "")
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.CambioClave")
internal data class CambioClave(val nueva: String, val actual: String = "")
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.CambioApodo")
internal data class CambioApodo(val nombre: String)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.SesionSocial")
data class SesionSocial(val id: String, val dispositivo: String = "", val creada: Long = 0, @kotlinx.serialization.SerialName("ultimo_uso") val ultimoUso: Long = 0, val actual: Boolean = false, val app: String = "cajon")

/** Nombre declarado por Android, apto para una cabecera HTTP. */
fun nombreDispositivo(fabricante: String, modelo: String): String {
    fun limpiar(valor: String) = valor.filter { it.code in 32..126 }.trim().replace(Regex("\\s+"), " ")
    val marca = limpiar(fabricante)
    val telefono = limpiar(modelo)
    return (if (telefono.startsWith(marca, ignoreCase = true)) telefono else "$marca $telefono").trim().take(60)
}

data class SesionMostrada(val id: String, val dispositivo: String?, val creada: Long, val ultimoUso: Long, val actual: Boolean)
fun SesionSocial.paraPantalla(): SesionMostrada {
    val origen = when (app) { "forzudo" -> "El Forzudo"; "cucha" -> "La Cucha"; else -> "" }
    val nombre = listOf(origen, dispositivo.trim()).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }
    return SesionMostrada(id, nombre, creada, ultimoUso, actual)
}

fun esCuentaNueva(error: ErrorSocial) = error.codigo == 409 && error.error == "cuenta_nueva"

@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.CambioUsuario")
internal data class CambioUsuario(val usuario: String)
/** Lo que dice el servidor de sí mismo: si puede mandar códigos por correo y la versión de la política. */
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.InfoServidor")
data class InfoServidor(val version: String = "", val correo: String = "false", val politica: String = "")
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.Texto")
internal data class Texto(val texto: String, val padre: Long? = null)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.Reporte")
internal data class Reporte(val que: String, val objeto: String, val motivo: String)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.Privada")
internal data class Privada(val privada: Boolean)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.CambiosPublicaciones")
data class CambiosPublicaciones(val lista: List<PublicacionSubida> = emptyList(), val quitar: List<String> = emptyList())
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.LlavesPublicas")
data class LlavesPublicas(val cifrar: String, val firmar: String)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.SobreNuevo")
internal data class SobreNuevo(val sobre: String, val firma: String)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.Sobre")
data class Sobre(val id: Long, val de: PersonaSocial, val cuando: Long, val sobre: String, val firma: String)


@Serializable
data class RevisionPerfil(val revision: Long)

@Serializable
data class IdentidadPerfil(
    val nombre: String? = null, val bio: String? = null, val portada: String? = null,
    val enlaces: List<String>? = null, val privada: Boolean? = null,
)

@Serializable
data class CambioPerfilApp(
    val revision: Long? = null, val identidad: IdentidadPerfil? = null,
    val partes: Map<String, ParteSubida>? = null,
)
