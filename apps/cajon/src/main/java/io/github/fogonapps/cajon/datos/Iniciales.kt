package io.github.fogonapps.cajon.datos

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Una obra o artista conocido para elegir gustos (`assets/datos/gustos.json`): la lista la arma Fogón, con ids de
 * Wikidata, TMDB, Open Library y MusicBrainz; [imagen] es una portada de su fuente (TVmaze u Open Library) cuando se
 * puede mostrar sin clave. [parecidos] son ids de la misma lista que salen al lado cuando se elige esta.
 */
@Serializable
data class ObraInicial(
    val id: String, val tipo: String, val titulo: String, val anio: Int? = null, val fuente: String = "",
    val imagen: String = "", val parecidos: List<String> = emptyList(), val tituloEn: String = titulo,
)

object Iniciales {
    fun enIdioma(obra: ObraInicial, idioma: String): ObraInicial =
        if (idioma == "en") obra.copy(titulo = obra.tituloEn) else obra

    fun leer(json: String): List<ObraInicial> = JsonCajon.decodeFromString(ListSerializer(ObraInicial.serializer()), json)

    /** Las que se muestran al empezar una fila: [n] al azar, las que más veces salen como parecidas primero (las más conocidas). */
    fun semilla(todas: List<ObraInicial>, n: Int, azar: kotlin.random.Random): List<String> {
        val citas = todas.flatMap { it.parecidos }.groupingBy { it }.eachCount()
        // El azar se sortea una vez por obra: un orden que cambia en cada comparación rompe el ordenamiento.
        return todas.map { it to (citas[it.id] ?: 0) + azar.nextDouble() * 2 }.sortedByDescending { it.second }.take(n).map { it.first.id }
    }

    /** Al elegir [id]: sus parecidas que todavía no están, justo después de ella (la fila se corre a la derecha). */
    fun alElegir(mostradas: List<String>, id: String, todas: Map<String, ObraInicial>, cuantas: Int = 3): List<String> {
        // Sus parecidas que no estén; si ya están todas (filas cortas, como el anime), otras del mismo tipo.
        val nuevas = todas[id]?.parecidos.orEmpty().filter { it !in mostradas && it in todas }.take(cuantas)
            .ifEmpty { todas.keys.filter { it !in mostradas }.shuffled().take(2) }
        val i = mostradas.indexOf(id)
        return if (i < 0) mostradas + nuevas else mostradas.take(i + 1) + nuevas + mostradas.drop(i + 1)
    }

    /** Recargar: se quedan las elegidas y el resto se cambia por otras que no se hayan mostrado. */
    fun recargar(mostradas: List<String>, elegidas: Set<String>, todas: List<ObraInicial>, n: Int, azar: kotlin.random.Random): List<String> {
        val quedan = mostradas.filter { it in elegidas }
        val nuevas = todas.map { it.id }.filter { it !in mostradas }.shuffled(azar).take(n)
        return quedan + nuevas.ifEmpty { todas.map { it.id }.filter { it !in quedan }.shuffled(azar).take(n) }
    }
}
