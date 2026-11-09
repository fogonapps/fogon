package io.github.fogonapps.cajon.datos

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset

/** Opinión independiente de la nota de la obra y del registro del diario. */
@Serializable
data class Resena(
    val id: String,
    val obra: String,
    val parte: String = "",
    val texto: String = "",
    val nota: Int? = null,
    val spoiler: Boolean = false,
    val fotos: List<String> = emptyList(),
    val visibilidad: String = Visibilidad.PRIVADA,
    val fecha: String = "",
    val registro: String? = null,
    val cambio: Long = 0,
    val borrada: Boolean = false,
)

object Resenas {
    /** Convierte copias antiguas antes de retirar los campos heredados de la escritura. */
    fun paraGuardar(e: Estado): Estado = migrar(e).let { migrado ->
        migrado.copy(obras = migrado.obras.map { o ->
            o.copy(resena = "", resenaVisibilidad = "", registros = o.registros.map { it.copy(texto = "") })
        })
    }

    fun obra(e: Estado, id: String): Obra? = e.vivas.firstOrNull { it.id == id } ?: e.artistasVivos.firstOrNull { it.id == id }?.let {
        Obra(it.id, if (it.tipo == Personas.AUTOR) "autor" else "artista", it.nombre, fuente = it.fuente, portada = it.foto)
    }

    /** Los ids existentes, incluso borrados, impiden volver a importar el respaldo antiguo. */
    fun migrar(e: Estado, yaMigradas: Set<String> = emptySet()): Estado {
        val ids = (e.resenas.map { it.id } + yaMigradas).toMutableSet()
        val nuevas = e.obras.flatMap { o ->
            listOfNotNull(o.resena.takeIf { it.isNotBlank() && ids.add("o:${o.id}") }?.let {
                Resena("o:${o.id}", o.id, texto = it, nota = o.nota.takeIf { n -> n > 0 },
                    visibilidad = Visibilidad.de(o.resenaVisibilidad), fecha = fecha(o.cambio), cambio = o.cambio, borrada = o.borrada)
            }) + o.registros.mapNotNull { r ->
                r.texto.takeIf { it.isNotBlank() && ids.add("r:${o.id}:${r.id}") }?.let {
                    Resena("r:${o.id}:${r.id}", o.id, texto = it, nota = r.nota.takeIf { n -> n > 0 },
                        visibilidad = Visibilidad.de(r.visibilidad.ifBlank { e.perfil.visibilidad[PartesPerfil.DIARIO].orEmpty() }),
                        fecha = r.fecha, registro = r.id, cambio = o.cambio, borrada = o.borrada)
                }
            }
        }
        return e.copy(resenas = e.resenas + nuevas)
    }

    private fun fecha(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()

    fun valida(r: Resena): Boolean = r.obra.isNotBlank() && r.texto.isNotBlank() && r.texto.length <= 5000 &&
        (r.nota == null || r.nota in 0..10) && r.fotos.size <= 4 && r.fotos.all { it.isNotBlank() } && r.parte.length <= 80

    /** Opciones cerradas que existen en el catálogo; capítulos y páginas se escriben aparte. */
    fun partes(o: Obra): List<String> = listOf("") + when {
        Tipos.porEpisodios(o.tipo) -> o.temporadas.flatMap { t ->
            listOf("temp:${t.numero}") + (1..t.episodios).map { "ep:S${t.numero}E$it" }
        }
        o.tipo == Tipos.ALBUM -> o.canciones.filter { it.mbid.isNotBlank() }.map { "cancion:${it.mbid}" }
        else -> emptyList()
    }

    fun parteValida(o: Obra, parte: String): Boolean = parte in partes(o) ||
        (o.tipo == Tipos.LIBRO && (parte.startsWith("cap:") || parte.startsWith("pag:")) &&
            parte.substringAfter(':').isNotBlank() && parte.substringAfter(':').length <= 40) ||
        ((parte.startsWith("artista:") || parte.startsWith("autor:")) && parte.substringAfter(':').isNotBlank() && parte.length <= 80)

    fun etiqueta(parte: String, o: Obra? = null, ingles: Boolean = false): String = when {
        parte.isEmpty() -> ""
        parte.startsWith("ep:") -> Regex("ep:S(\\d+)E(\\d+)").matchEntire(parte)?.let {
            "${if (ingles) "S" else "T"}${it.groupValues[1]} · E${it.groupValues[2]}"
        } ?: parte
        parte.startsWith("temp:") -> "${if (ingles) "S" else "T"}${parte.substringAfter(':')}"
        parte.startsWith("cap:") -> "${if (ingles) "Ch." else "Cap."} ${parte.substringAfter(':')}"
        parte.startsWith("pag:") -> "${if (ingles) "p." else "Pág."} ${parte.substringAfter(':')}"
        parte.startsWith("cancion:") -> o?.canciones?.firstOrNull { it.mbid == parte.substringAfter(':') }?.titulo ?: parte.substringAfter(':')
        else -> if (o != null && parte in listOf("artista:${o.fuente}", "autor:${o.fuente}")) o.titulo else parte.substringAfter(':')
    }
}
