package io.github.fogonapps.ahorrador

import android.content.Context
import io.github.fogonapps.ahorrador.datos.Categoria
import io.github.fogonapps.ahorrador.datos.Grupo
import io.github.fogonapps.identidad.Idioma

/**
 * Las categorías con las que empieza la app, cada una con su tinta riso (como los músculos en
 * El Forzudo) y su grupo para el 50/30/20 y el kakeibo. Se crean en el idioma de la app; después
 * son datos de la persona y no cambian si cambia el idioma.
 */
object Categorias {
    private data class Base(val id: String, val nombre: Int, val color: Long, val icono: String, val grupo: Grupo, val ingreso: Boolean = false)

    private val BASE = listOf(
        Base("casa", R.string.cat_casa, 0xFF7B4DFF, "casa", Grupo.NECESIDAD),
        Base("mercado", R.string.cat_mercado, 0xFFFF7A1A, "mercado", Grupo.NECESIDAD),
        Base("servicios", R.string.cat_servicios, 0xFFFFC93C, "servicios", Grupo.NECESIDAD),
        Base("transporte", R.string.cat_transporte, 0xFF2D5BFF, "transporte", Grupo.NECESIDAD),
        Base("salud", R.string.cat_salud, 0xFFE5332A, "salud", Grupo.NECESIDAD),
        Base("comida", R.string.cat_comida, 0xFFFF4FA3, "comida", Grupo.GUSTO),
        Base("salidas", R.string.cat_salidas, 0xFF1FB45A, "salidas", Grupo.GUSTO),
        Base("compras", R.string.cat_compras, 0xFF00A0E9, "compras", Grupo.GUSTO),
        Base("cultura", R.string.cat_cultura, 0xFF2FC1B2, "cultura", Grupo.CULTURA),
        Base("estudio", R.string.cat_estudio, 0xFFA8D930, "estudio", Grupo.CULTURA),
        Base("imprevistos", R.string.cat_imprevistos, 0xFFB8733A, "imprevisto", Grupo.IMPREVISTO),
        Base("deudas", R.string.cat_deudas, 0xFF9B8F84, "deudas", Grupo.NECESIDAD),
        Base("impuestos", R.string.cat_impuestos, 0xFFB5AA9C, "impuestos", Grupo.NECESIDAD),
        Base("otros", R.string.cat_otros, 0xFFD6CBB9, "otros", Grupo.GUSTO),
        Base("sueldo", R.string.cat_sueldo, 0xFF1FB45A, "sueldo", Grupo.NECESIDAD, ingreso = true),
        Base("prima", R.string.cat_prima, 0xFFFFC93C, "prima", Grupo.NECESIDAD, ingreso = true),
        Base("cesantias", R.string.cat_cesantias, 0xFFFF7A1A, "banco", Grupo.NECESIDAD, ingreso = true),
        Base("extra", R.string.cat_extra, 0xFF00A0E9, "extra", Grupo.NECESIDAD, ingreso = true),
    )

    fun base(c: Context): List<Categoria> {
        val r = Idioma.envolver(c).resources
        return BASE.map { Categoria(it.id, r.getString(it.nombre), it.color, it.icono, it.grupo, it.ingreso) }
    }
}
