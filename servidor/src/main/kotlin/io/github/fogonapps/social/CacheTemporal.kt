package io.github.fogonapps.social

/**
 * Respuestas guardadas un rato ([vida]) para no repetir pedidos a otro servicio. Acotada por cantidad ([maxEntradas])
 * y por tamaño ([maxCaracteres], que cuenta la llave y lo que [medir] dice que pesa el valor: en una búsqueda la llave
 * lleva el texto buscado y pesa más que una respuesta vacía). Lo vencido se borra al leer, al guardar y en cada [barrer], así que nada de lo
 * guardado (tampoco el texto de una búsqueda) vive más que [vida] más el tiempo entre dos barridos.
 *
 * Las entradas van en el orden en que se guardaron, que es también el orden en que vencen: barrer y hacer lugar
 * solo mira las más viejas.
 */
internal class CacheTemporal<T>(
    private val vida: Long,
    private val maxEntradas: Int,
    private val maxCaracteres: Long,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val medir: (T) -> Long,
) {
    private class Entrada<T>(val cuando: Long, val valor: T, val peso: Long)

    private val entradas = LinkedHashMap<String, Entrada<T>>()
    private var ocupado = 0L

    val cantidad: Int get() = synchronized(this) { entradas.size }
    val caracteres: Long get() = synchronized(this) { ocupado }

    fun leer(llave: String): T? { synchronized(this) {
        val e = entradas[llave] ?: return null
        if (ahora() - e.cuando < vida) return e.valor
        quitar(llave)
        return null
    } }

    /** Guarda [r] si cabe sola en la caché; para hacerle lugar se van las más viejas. */
    fun guardar(llave: String, r: T) { synchronized(this) {
        val peso = llave.length.toLong() + medir(r)
        quitar(llave)
        barrer()
        if (peso > maxCaracteres) return
        val viejas = entradas.keys.iterator()
        while (viejas.hasNext() && (entradas.size >= maxEntradas || ocupado + peso > maxCaracteres)) {
            ocupado -= entradas.getValue(viejas.next()).peso
            viejas.remove()
        }
        entradas[llave] = Entrada(ahora(), r, peso)
        ocupado += peso
    } }

    /** Borra lo vencido. */
    fun barrer() { synchronized(this) {
        val limite = ahora() - vida
        val viejas = entradas.values.iterator()
        while (viejas.hasNext()) {
            val e = viejas.next()
            if (e.cuando > limite) break
            ocupado -= e.peso
            viejas.remove()
        }
    } }

    private fun quitar(llave: String) {
        entradas.remove(llave)?.let { ocupado -= it.peso }
    }

    companion object {
        /** Para las respuestas de TMDB e IGDB: pesa el cuerpo. */
        fun respuestas(vida: Long, maxEntradas: Int, maxCaracteres: Long, ahora: () -> Long = System::currentTimeMillis) =
            CacheTemporal<Cine.Respuesta>(vida, maxEntradas, maxCaracteres, ahora) { it.cuerpo.length.toLong() }
    }
}
