package io.github.fogonapps.cajon.datos

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset

/** Opinión de una obra, una parte o una entrada del diario. */
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
    /** Filtro de las reseñas de la gente por su nota (de 0 a 10): buenas desde 3,5 estrellas, malas hasta 2. */
    const val FILTRO_TODAS = "todas"
    const val FILTRO_BUENAS = "buenas"
    const val FILTRO_MALAS = "malas"

    /** Las reseñas sin nota solo aparecen con "todas". */
    fun pasaFiltroNota(nota: Int?, filtro: String): Boolean = when (filtro) {
        FILTRO_BUENAS -> nota != null && nota >= 7
        FILTRO_MALAS -> nota != null && nota <= 4
        else -> true
    }

    fun esDeObraEntera(e: Estado, r: Resena): Boolean =
        r.parte.isEmpty() && r.registro == null && e.vivas.any {
            it.id == r.obra && it.tipo !in listOf("autor", "artista")
        }

    fun deObra(e: Estado, obra: String): Resena? = deDestino(e, obra)

    fun deDestino(e: Estado, obra: String, parte: String = "", registro: String? = null): Resena? = e.resenas
        .filter { !it.borrada && it.obra == obra && if (registro != null) it.registro == registro
            else it.registro == null && it.parte == parte }
        .maxByOrNull { it.cambio }

    private fun claveLocalCancion(o: Obra, indice: Int): String {
        val titulo = o.canciones[indice].titulo
        val clave = java.util.UUID.nameUUIDFromBytes(titulo.toByteArray(Charsets.UTF_8))
        val repeticion = o.canciones.take(indice).count { it.titulo == titulo }
        return "local:$clave:$repeticion"
    }

    /** El título y su repetición conservan el vínculo al completar o reordenar el catálogo. */
    fun parteCancion(o: Obra, indice: Int): String =
        "cancion:" + o.canciones[indice].mbid.ifBlank { claveLocalCancion(o, indice) }

    fun indiceCancion(o: Obra, parte: String): Int? {
        if (!parte.startsWith("cancion:")) return null
        val clave = parte.removePrefix("cancion:")
        return o.canciones.indices.firstOrNull { i ->
            clave == claveLocalCancion(o, i) || (o.canciones[i].mbid.isNotBlank() && clave == o.canciones[i].mbid)
        }
    }

    private fun idNuevo(e: Estado, obra: String, parte: String, registro: String?, cambio: Long): String =
        "n:" + java.util.UUID.nameUUIDFromBytes("$obra|$parte|$registro|$cambio|${e.resenas.size}".toByteArray(Charsets.UTF_8))

    fun deCancion(e: Estado, o: Obra, indice: Int): Resena? = e.resenas
        .filter { !it.borrada && it.obra == o.id && it.registro == null && indiceCancion(o, it.parte) == indice }
        .maxByOrNull { it.cambio }

    fun actualizarNotasCanciones(e: Estado, o: Obra): Obra = o.copy(canciones = o.canciones.mapIndexed { i, c ->
        deCancion(e, o, i)?.let { c.copy(nota = it.nota ?: 0) } ?: c
    })

    private fun mismoDestino(e: Estado, a: Resena, b: Resena): Boolean {
        if (a.obra != b.obra) return false
        if (a.registro != null || b.registro != null) return a.registro != null && a.registro == b.registro
        val o = e.vivas.firstOrNull { it.id == a.obra }
        val indice = o?.let { indiceCancion(it, a.parte) }
        return if (indice != null) indice == indiceCancion(o, b.parte) else a.parte == b.parte
    }

    /** Los campos conservados en la obra y el diario reflejan la reseña del mismo destino. */
    private fun reflejar(e: Estado, r: Resena, cambio: Long): Estado = e.copy(obras = e.obras.map { o ->
        if (o.id != r.obra || o.borrada) o else {
            val nota = r.nota ?: 0
            when {
                r.registro != null -> o.copy(cambio = cambio, registros = o.registros.map {
                    if (it.id == r.registro) it.copy(nota = nota, visibilidad = r.visibilidad) else it
                })
                esDeObraEntera(e, r) -> o.copy(nota = nota, cambio = cambio)
                else -> indiceCancion(o, r.parte)?.let { indice -> o.copy(cambio = cambio,
                    canciones = o.canciones.mapIndexed { i, c -> if (i == indice) c.copy(nota = nota) else c }) } ?: o
            }
        }
    })

    fun aplicarNota(e: Estado, obra: String, nota: Int, cambio: Long): Estado =
        calificar(e, obra, nota, cambio)

    fun calificar(e: Estado, obra: String, nota: Int, cambio: Long, parte: String = "", registro: String? = null): Estado {
        val valor = nota.coerceIn(0, 10).takeIf { it > 0 }
        val o = e.vivas.firstOrNull { it.id == obra } ?: return e
        val existente = if (registro == null) indiceCancion(o, parte)?.let { deCancion(e, o, it) }
            ?: deDestino(e, obra, parte) else deDestino(e, obra, registro = registro)
        val r = (existente ?: Resena(idNuevo(e, obra, parte, registro, cambio), obra, parte = parte,
            registro = registro, fecha = registro?.let { id -> o.registros.firstOrNull { it.id == id }?.fecha } ?: fecha(cambio)))
            .copy(nota = valor, cambio = cambio)
        if (valor == null && r.texto.isBlank()) {
            return if (existente != null) borrar(e, r.id, cambio) else reflejar(e, r, cambio)
        }
        return guardar(e, r, cambio)
    }

    fun borrar(e: Estado, id: String, cambio: Long): Estado {
        val r = e.resenas.firstOrNull { it.id == id && !it.borrada }
        val nuevo = e.copy(resenas = e.resenas.map { if (it.id == id) it.copy(borrada = true, cambio = cambio) else it },
            borradoresResena = e.borradoresResena.map { if (it.id == id) it.copy(borrada = true, cambio = cambio) else it })
        return if (r != null) reflejar(nuevo, r.copy(nota = null, visibilidad = Visibilidad.PRIVADA), cambio) else nuevo
    }

    fun guardar(e: Estado, r: Resena, cambio: Long): Estado {
        val existente = e.resenas.filter { !it.borrada && mismoDestino(e, it, r) }.maxByOrNull { it.cambio }
        val id = if (e.resenas.none { it.id == r.id }) existente?.id ?: r.id else r.id
        val guardada = r.copy(id = id, nota = r.nota?.takeIf { it > 0 }, cambio = cambio)
        val resenas = e.resenas.map {
            when {
                it.id == id -> guardada
                !it.borrada && mismoDestino(e, it, guardada) -> it.copy(nota = guardada.nota, cambio = cambio,
                    visibilidad = if (guardada.registro != null) guardada.visibilidad else it.visibilidad)
                else -> it
            }
        }.let { if (e.resenas.any { it.id == id }) it else it + guardada }
        val nuevo = e.copy(resenas = resenas, borradoresResena = e.borradoresResena.map {
            if (it.id == r.id) it.copy(borrada = true, cambio = cambio) else it
        })
        return reflejar(nuevo, guardada, cambio)
    }

    fun registrar(e: Estado, obra: String, entrada: Registro, texto: String, cambio: Long): Estado {
        val visibilidad = Visibilidad.de(entrada.visibilidad.ifBlank { Visibilidad.PRIVADA })
        val r = entrada.copy(nota = entrada.nota.coerceIn(0, 10), visibilidad = visibilidad)
        val nuevo = e.copy(obras = e.obras.map { o -> if (o.id != obra) o else o.copy(cambio = cambio,
            registros = o.registros.filterNot { it.id == r.id } + r) })
        val resena = deDestino(nuevo, obra, registro = r.id)
        if (texto.isBlank() && r.nota == 0) return if (resena != null) borrar(nuevo, resena.id, cambio) else nuevo
        return guardar(nuevo, (resena ?: Resena(idNuevo(nuevo, obra, "", r.id, cambio), obra, registro = r.id))
            .copy(texto = texto, nota = r.nota.takeIf { it > 0 }, visibilidad = visibilidad, fecha = r.fecha), cambio)
    }

    /** Solo se incorporan notas privadas a una reseña que seguirá siendo privada. */
    fun migrarNotas(e: Estado): Estado {
        var nuevo = e
        for (o in e.vivas.filter { it.notas.isNotBlank() }) {
            val enteras = nuevo.resenas.filter { !it.borrada && esDeObraEntera(nuevo, it) && it.obra == o.id }
            if (enteras.any { Visibilidad.de(it.visibilidad) != Visibilidad.PRIVADA }) continue
            val r = deObra(nuevo, o.id) ?: Resena("n:notas:${o.id}", o.id, nota = o.nota.takeIf { it > 0 }, fecha = fecha(o.cambio))
            val cambio = maxOf(o.cambio, r.cambio) + 1
            nuevo = guardar(nuevo, r.copy(texto = listOf(r.texto, o.notas).filter { it.isNotBlank() }.joinToString("\n\n")), cambio)
            nuevo = nuevo.copy(obras = nuevo.obras.map { if (it.id == o.id) it.copy(notas = "", cambio = cambio) else it })
        }
        return nuevo
    }

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
        return migrarNotas(e.copy(resenas = e.resenas + nuevas))
    }

    private fun fecha(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()

    fun valida(r: Resena): Boolean = r.obra.isNotBlank() &&
        (r.texto.isNotBlank() || (r.nota ?: 0) > 0) && r.texto.length <= 5000 &&
        (r.nota == null || r.nota in 0..10) && r.fotos.size <= 4 && r.fotos.all { it.isNotBlank() } && r.parte.length <= 80

    /** Opciones cerradas que existen en el catálogo; capítulos y páginas se escriben aparte. */
    fun partes(o: Obra): List<String> = listOf("") + when {
        Tipos.porEpisodios(o.tipo) -> o.temporadas.flatMap { t ->
            listOf("temp:${t.numero}") + (1..t.episodios).map { "ep:S${t.numero}E$it" }
        }
        o.tipo == Tipos.ALBUM -> o.canciones.indices.map { parteCancion(o, it) }
        else -> emptyList()
    }

    fun parteValida(o: Obra, parte: String): Boolean = parte in partes(o) ||
        (o.tipo == Tipos.ALBUM && indiceCancion(o, parte) != null) ||
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
        parte.startsWith("cancion:") -> o?.let { indiceCancion(it, parte)?.let { i -> o.canciones[i].titulo } } ?: parte.substringAfter(':')
        else -> if (o != null && parte in listOf("artista:${o.fuente}", "autor:${o.fuente}")) o.titulo else parte.substringAfter(':')
    }
}
