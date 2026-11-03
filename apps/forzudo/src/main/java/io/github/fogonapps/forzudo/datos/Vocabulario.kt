package io.github.fogonapps.forzudo.datos

import java.util.Locale

/**
 * Las palabras que usan la lógica y los formatos (meses, unidades, consejos de cada serie).
 *
 * La lógica es pura y se prueba en la JVM, así que no lee `strings.xml`: usa [actual], que la app
 * llena desde los recursos del idioma en uso (`Textos.aplicar`). Por defecto es [ES], el mismo texto de
 * la versión web, para que la prueba de equivalencia siga comparando igual.
 */
data class Vocabulario(
    val meses: List<String>,
    val mesesLargos: List<String>,
    val diasLargos: List<String>,
    val diasCortos: List<String>,
    val decimal: Char,
    val numeros: Locale,
    val seg: String,
    val reps: String,
    val entreno: String,
    /** "Meta %1$d × %2$s %3$s": series, rango de reps y unidad. */
    val meta: String,
    val mismoPeso: String,
    val subePeso: String,
    val mas10s: String,
    val masLento: String,
    /** "Mantén %1$s kg". */
    val manten: String,
    val masUnaRep: String,
) {
    companion object {
        val ES = Vocabulario(
            meses = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"),
            mesesLargos = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
                "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"),
            diasLargos = listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"),
            diasCortos = listOf("D", "L", "M", "X", "J", "V", "S"),
            decimal = ',',
            numeros = Locale.forLanguageTag("es-CO"),
            seg = "seg",
            reps = "reps",
            entreno = "Entreno",
            meta = "Meta %1\$d × %2\$s %3\$s",
            mismoPeso = "Usa el mismo peso y anota las reps",
            subePeso = "Sube el peso hoy",
            mas10s = "Súbele 10 segundos",
            masLento = "Hazlas más lentas o con peso",
            manten = "Mantén %1\$s kg",
            masUnaRep = "Busca +1 rep por serie",
        )

        @Volatile
        var actual: Vocabulario = ES
    }
}
