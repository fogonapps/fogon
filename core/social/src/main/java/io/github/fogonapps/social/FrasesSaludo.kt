package io.github.fogonapps.social

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Una frase propia de la app: su clave (con el prefijo de la app, como `fz_1`), el texto y su franja del día. */
data class FraseBase(val clave: String, val texto: String, val franja: String = "todo")

/** La frase que sale en el saludo: su clave y el texto ya armado. */
data class FraseElegida(val clave: String, val texto: String)

/** La franja del día de una hora: madrugada (0 a 4), mañana (5 a 11), tarde (12 a 18) o noche (19 a 23). */
fun franjaDelDia(hora: Int) = when (hora) { in 0..4 -> "madrugada"; in 5..11 -> "manana"; in 12..18 -> "tarde"; else -> "noche" }

/**
 * Elige la frase del saludo como El Cajón: las propias de la app y las de la gente (las de esta franja pesan más que
 * las de todo el día), mezcladas con los votos de la comunidad y lo que dijo la persona ([Frases.elegir]). Las de
 * noche también salen de madrugada.
 */
fun elegirFraseSaludo(
    base: List<FraseBase>, deLaGente: List<FraseComunidad>, votos: Map<String, Frases.Votos>,
    gustan: Set<String>, noGustan: Set<String>, dia: Int, hora: Int, vuelta: Int = 0,
): FraseElegida? {
    val franja = franjaDelDia(hora)
    fun pesa(f: String) = when { f == "todo" -> 1.0; f == franja || (f == "noche" && franja == "madrugada") -> 1.3; else -> 0.0 }
    val textos = base.associate { it.clave to it.texto } + deLaGente.associate { it.clave to it.texto }
    val candidatas = (base.map { it.clave to pesa(it.franja) } + deLaGente.map { it.clave to pesa(it.franja) }).toMap()
    val clave = Frases.elegir(candidatas, votos, gustan, noGustan, (dia * 24L + hora) * 7919 + vuelta)
        ?: base.firstOrNull { it.clave !in noGustan }?.clave ?: return null
    return FraseElegida(clave, textos.getValue(clave))
}

/**
 * Las frases del saludo de una app (fuera de El Cajón, que tiene su propia versión con obras): carga los votos, las
 * de la gente de esta app y las que el equipo quitó; guarda lo que le gusta o no a la persona en este teléfono y
 * manda su voto si tiene cuenta. La frase elegida se queda toda la franja del día.
 */
class FrasesSaludo(private val prefs: SharedPreferences, private val alcance: CoroutineScope, private val cliente: () -> ClienteSocial?) {
    private val _votos = MutableStateFlow<Map<String, Frases.Votos>>(emptyMap())
    val votos: StateFlow<Map<String, Frases.Votos>> = _votos
    private val _deLaGente = MutableStateFlow<List<FraseComunidad>>(emptyList())
    val deLaGente: StateFlow<List<FraseComunidad>> = _deLaGente
    private val _ocultas = MutableStateFlow<Set<String>>(emptySet())
    val ocultas: StateFlow<Set<String>> = _ocultas
    private val _gustan = MutableStateFlow(prefs.getStringSet("gustan", emptySet()).orEmpty().toSet())
    val gustan: StateFlow<Set<String>> = _gustan
    private val _noGustan = MutableStateFlow(prefs.getStringSet("no_gustan", emptySet()).orEmpty().toSet())
    val noGustan: StateFlow<Set<String>> = _noGustan
    private var cargadas = 0L

    /** Trae de la red lo de la comunidad (como mucho cada hora, salvo [forzar]). Sin red ni cuenta, quedan las propias. */
    fun cargar(idioma: String, forzar: Boolean = false) {
        val c = cliente() ?: return
        if (!forzar && System.currentTimeMillis() - cargadas < 3_600_000L) return
        cargadas = System.currentTimeMillis()
        alcance.launch {
            runCatching { c.frases() }.getOrNull()?.let { m -> _votos.value = m.mapValues { Frases.Votos(it.value.si, it.value.no) } }
            runCatching { c.frasesComunidad(idioma) }.getOrNull()?.let { _deLaGente.value = it }
            runCatching { c.frasesOcultas() }.getOrNull()?.let { _ocultas.value = it.toSet() }
        }
    }

    /** Le gusta (1), no le gusta (-1) o quita su voto (0). Lo que no le gusta no vuelve a salir. */
    fun votar(clave: String, valor: Int) {
        _gustan.value = if (valor == 1) _gustan.value + clave else _gustan.value - clave
        _noGustan.value = if (valor == -1) _noGustan.value + clave else _noGustan.value - clave
        prefs.edit().putStringSet("gustan", _gustan.value).putStringSet("no_gustan", _noGustan.value).apply()
        cliente()?.let { c -> alcance.launch { runCatching { c.votarFrase(clave, valor) } } }
    }

    /** Propone una frase (necesita cuenta): el equipo la revisa y, si va, sale para todos en esta app. */
    fun proponer(texto: String, idioma: String, listo: (Boolean) -> Unit = {}) {
        val c = cliente() ?: return listo(false)
        alcance.launch { listo(runCatching { c.proponerFrase(texto.trim(), idioma) }.isSuccess) }
    }

    /** La frase guardada para esta franja ([franja] = fecha y franja), si sigue valiendo. */
    fun guardada(franja: String): String? = prefs.getString("guardada", null)?.split('\t')?.takeIf { it.size == 2 && it[0] == franja }?.get(1)
    fun guardar(franja: String, clave: String) { prefs.edit().putString("guardada", "$franja\t$clave").apply() }
}
