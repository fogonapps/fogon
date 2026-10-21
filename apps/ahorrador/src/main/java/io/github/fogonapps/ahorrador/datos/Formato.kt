package io.github.fogonapps.ahorrador.datos

import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * Cómo se escribe la plata y las fechas. [idioma] lo pone la app al arrancar y al cambiar de idioma;
 * por defecto es español de Colombia ("$ 842.300", "21 oct").
 */
object Formato {
    @Volatile var idioma: String = "es"
    @Volatile var mesesCortos: List<String> = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    @Volatile var mesesLargos: List<String> = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
        "septiembre", "octubre", "noviembre", "diciembre")

    private fun numeros(): NumberFormat =
        NumberFormat.getIntegerInstance(if (idioma == "es") Locale.forLanguageTag("es-CO") else Locale.forLanguageTag("en-US"))
            .apply { isGroupingUsed = true }

    /** "$ 842.300" en español, "$842,300" en inglés. Los negativos llevan "−" delante. */
    fun pesos(n: Long): String {
        val s = numeros().format(abs(n))
        val signo = if (n < 0) "− " else ""
        return if (idioma == "es") "$signo$ $s" else "$signo$$s"
    }

    /** Solo el número con separadores, sin el signo pesos. */
    fun numero(n: Long): String = numeros().format(n)

    fun fechaCorta(fecha: String): String = fechaCorta(LocalDate.parse(fecha))
    fun fechaCorta(d: LocalDate): String =
        if (idioma == "es") "${d.dayOfMonth} ${mesesCortos[d.monthValue - 1]}" else "${mesesCortos[d.monthValue - 1]} ${d.dayOfMonth}"

    /** El mes como va en medio de una frase: "octubre" en español, "October" en inglés. */
    fun mes(mes: String): String = mesesLargos[mes.takeLast(2).toInt() - 1]

    /** El mes como título, con mayúscula: "Octubre". */
    fun nombreMes(mes: String): String = mesesLargos[mes.takeLast(2).toInt() - 1].replaceFirstChar { it.uppercase() }
}
