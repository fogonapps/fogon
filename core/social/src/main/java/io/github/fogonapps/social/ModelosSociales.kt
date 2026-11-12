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
data class PersonaSocial(val usuario: String, val nombre: String = "", val foto: Boolean = false, val privada: Boolean = false, val insignia: String = "")

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
    val rol: String = "", val permisos: Set<String> = emptySet(), val mfa: Boolean = false, val elevadaHasta: Long = 0, val idCuenta: String = "", val politica: String = "",
    val insignia: String = "", val dosPasos: Boolean = false)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.CasoModeracion")
data class CasoModeracion(val que: String, val objeto: String, val motivos: Map<String, Int> = emptyMap(), val personas: Int = 0, val oculto: Boolean = false,
    val contenido: String = "", val autor: PersonaSocial? = null, val faltasAutor: Int = 0, val estado: String = "visible", val retiradoHasta: Long? = null)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.Decision")
internal data class Decision(val que: String, val objeto: String, val decision: String, val regla: String = "", val mensaje: String = "")
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.FaltaSocial")
data class FaltaSocial(val regla: String, val puntos: Int, val cuando: Long, val vence: Long,
    /** Lo que el equipo le escribió a la persona; [decision]: "advertencia", "falta", "falta_grave" o "cerrar". */
    val mensaje: String = "", val decision: String = "falta")
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.EstadoCuentaSocial")
data class EstadoCuentaSocial(val faltas: List<FaltaSocial> = emptyList(), val restringidaHasta: Long = 0, val suspendidaHasta: Long = 0,
    val cerrada: Boolean = false, val admin: Boolean = false, val apelacion: ApelacionSocial? = null) {
    /** Se puede apelar la última sanción en los 30 días siguientes, una vez. */
    fun puedeApelar(ahora: Long): Boolean {
        val ultima = faltas.maxOfOrNull { it.cuando } ?: return false
        return ultima > ahora - 30L * 24 * 3600_000 && (apelacion == null || apelacion.cuando < ultima)
    }
}
/** Una apelación: [estado] "abierta", "aceptada" o "rechazada", con la [respuesta] del equipo. */
@Serializable
data class ApelacionSocial(val id: Long, val usuario: String = "", val texto: String = "", val cuando: Long = 0, val estado: String = "abierta",
    val respuesta: String = "", val faltas: List<FaltaSocial> = emptyList(), val suspendidaHasta: Long = 0, val cerrada: Boolean = false)
@Serializable
data class SancionReciente(val usuario: String, val nombre: String = "", val regla: String, val mensaje: String = "", val decision: String = "falta",
    val cuando: Long, val restringidaHasta: Long = 0, val suspendidaHasta: Long = 0, val cerrada: Boolean = false, val puntos: Int = 0)
@Serializable internal data class SancionDirecta(val usuario: String, val decision: String, val regla: String, val mensaje: String)
@Serializable internal data class NuevaApelacion(val texto: String, val correo: String = "", val clave: String = "")
@Serializable internal data class DecisionApelacion(val id: Long, val aceptar: Boolean, val respuesta: String)
@Serializable internal data class AccesoSancion(val correo: String, val clave: String)
@Serializable data class EstadoSancionSocial(val estado: EstadoCuentaSocial, val apelacion: ApelacionSocial? = null)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.EntradaClave")
internal data class EntradaClave(val correo: String, val clave: String, val app: String? = null, val codigo: String = "")
@Serializable
internal data class DosPasos(val activo: Boolean, val clave: String)
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
    val origen = when (app) { "forzudo" -> "El Forzudo"; "cucha" -> "La Cucha"; "alcancia" -> "La Alcancía"; else -> "" }
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

/* ----- Frases del saludo (comunes a todas las apps) ----- */

/** Cuántas personas dijeron que sí y que no a una frase (solo los totales). */
@kotlinx.serialization.Serializable data class ConteoFrase(val si: Int = 0, val no: Int = 0)
@kotlinx.serialization.Serializable data class VotoFrase(val valor: Int)
/** Una frase nueva: propuesta por la gente o agregada por el equipo, para la app [app]. */
@kotlinx.serialization.Serializable data class FraseNueva(val texto: String, val idioma: String, val franja: String = "todo", val app: String = "cajon")
/** Una frase de la gente o del equipo, con su app de origen. */
@kotlinx.serialization.Serializable data class FraseComunidad(val clave: String, val texto: String, val idioma: String = "", val de: String = "", val franja: String = "todo", val app: String = "cajon")
@kotlinx.serialization.Serializable data class DecisionFrase(val clave: String, val aprobar: Boolean)

/** Un lugar (restaurante) de La Cucha. Las coordenadas, si las hay, nunca se muestran en las tarjetas. */
@Serializable
data class Lugar(val id: Long, val nombre: String, val ciudad: String, val direccion: String? = null,
                 val lat: Double? = null, val lon: Double? = null, val cuenta: Long? = null, val cuando: Long = 0)

/** Lo que se manda para crear un lugar: [lat] y [lon] van juntas o no van. */
@Serializable
data class LugarNuevo(val nombre: String, val ciudad: String, val direccion: String? = null, val lat: Double? = null, val lon: Double? = null)

/** D-033: un sobre cifrado (el servidor no lo puede abrir), su versión y la llave común envuelta con la contraseña. */
@Serializable
data class CopiaCifrada(val sobre: String = "", val cambio: Long = 0, val llave: String = "")

/** Lo que se corrige de un lugar: la dirección escrita. */
@Serializable
data class CorreccionLugar(val direccion: String)

@Serializable
data class EmpujeNuevo(val token: String, val app: String)

@Serializable
data class CambioInsignia(val usuario: String, val insignia: String, val motivo: String)

/* ----- Historias y notas (24 horas) ----- */
@Serializable data class HistoriaSocial(val id: Long, val datos: JsonObject, val cuando: Long, val vista: Boolean = false, val meGusta: Boolean = false, val mejores: Boolean = false,
    val votos: List<Int> = emptyList(), val miVoto: Int = -1, val destacada: String = "")
@Serializable data class GrupoDestacadasSocial(val titulo: String, val historias: List<HistoriaSocial> = emptyList())
@Serializable internal data class VotoHistoria(val opcion: Int)
@Serializable internal data class DestacarHistoria(val titulo: String)
@Serializable data class GrupoHistoriasSocial(val persona: PersonaSocial, val historias: List<HistoriaSocial> = emptyList(), val silenciada: Boolean = false) {
    val todasVistas get() = historias.all { it.vista }
}
@Serializable data class VistaHistoriaSocial(val persona: PersonaSocial, val cuando: Long, val meGusta: Boolean = false)
@Serializable internal data class HistoriaNueva(val datos: JsonObject)
@Serializable internal data class ListaUsuarios(val usuarios: List<String> = emptyList())
/** La nota de una persona: unas palabras (hasta 60) y, si quiere, un adjunto (canción, obra, entreno, plato, meta…). */
@Serializable data class NotaGente(val persona: PersonaSocial, val texto: String, val datos: JsonObject = JsonObject(emptyMap()), val cuando: Long = 0)
@Serializable internal data class NotaNuevaGente(val texto: String, val datos: JsonObject = JsonObject(emptyMap()))
/** Una canción con su avance de 30 segundos (Apple) y el enlace a Apple Music. */
@Serializable data class CancionSocial(val titulo: String, val artista: String, val portada: String = "", val audio: String = "", val enlace: String = "", val album: String = "")
