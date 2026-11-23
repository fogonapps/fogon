package io.github.fogonapps.social

/**
 * Respuestas guardadas un rato ([vida]) para no repetir pedidos a otro servicio. Acotada por cantidad ([maxEntradas])
 * y por tamaño ([maxCaracteres], que cuenta la llave y el cuerpo: en una búsqueda la llave lleva el texto buscado y
 * pesa más que una respuesta vacía). Lo vencido se borra al leer, al guardar y en cada [barrer], así que nada de lo
 * guardado (tampoco el texto de una búsqueda) vive más que [vida] más el tiempo entre dos barridos.
 *
 * Las entradas van en el orden en que se guardaron, que es también el orden en que vencen: barrer y hacer lugar
 * solo mira las más viejas.
 */
internal class CacheTemporal(
    private val vida: Long,
    private val maxEntradas: Int,
    private val maxCaracteres: Long,
    private val ahora: () -> Long = System::currentTimeMillis,
) {
    private class Entrada(val cuando: Long, val respuesta: Cine.Respuesta, val peso: Long)

    private val entradas = LinkedHashMap<String, Entrada>()
    private var ocupado = 0L

    val cantidad: Int get() = synchronized(this) { entradas.size }
    val caracteres: Long get() = synchronized(this) { ocupado }

    fun leer(llave: String): Cine.Respuesta? { synchronized(this) {
        val e = entradas[llave] ?: return null
        if (ahora() - e.cuando < vida) return e.respuesta
        quitar(llave)
        return null
    } }

    /** Guarda [r] si cabe sola en la caché; para hacerle lugar se van las más viejas. */
    fun guardar(llave: String, r: Cine.Respuesta) { synchronized(this) {
        val peso = llave.length.toLong() + r.cuerpo.length
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
}
