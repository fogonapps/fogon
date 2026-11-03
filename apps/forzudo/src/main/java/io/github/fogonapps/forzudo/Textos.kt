package io.github.fogonapps.forzudo

import android.content.Context
import io.github.fogonapps.forzudo.datos.Vocabulario
import java.util.Locale

/** Pasa los textos de strings.xml que usa la lógica pura al [Vocabulario] (ver su explicación). */
object Textos {
    fun aplicar(c: Context, idioma: String) {
        val r = c.resources
        Vocabulario.actual = Vocabulario(
            meses = r.getStringArray(R.array.meses).toList(),
            mesesLargos = r.getStringArray(R.array.meses_largos).toList(),
            diasLargos = r.getStringArray(R.array.dias_largos).toList(),
            diasCortos = r.getStringArray(R.array.dias_cortos).toList(),
            decimal = r.getString(R.string.voc_decimal).first(),
            numeros = if (idioma == "es") Locale.forLanguageTag("es-CO") else Locale.forLanguageTag(idioma),
            seg = r.getString(R.string.voc_seg),
            reps = r.getString(R.string.voc_reps),
            entreno = r.getString(R.string.voc_entreno),
            meta = r.getString(R.string.voc_meta),
            mismoPeso = r.getString(R.string.voc_mismo_peso),
            subePeso = r.getString(R.string.voc_sube_peso),
            mas10s = r.getString(R.string.voc_mas10s),
            masLento = r.getString(R.string.voc_mas_lento),
            manten = r.getString(R.string.voc_manten),
            masUnaRep = r.getString(R.string.voc_mas_una_rep),
        )
    }
}
