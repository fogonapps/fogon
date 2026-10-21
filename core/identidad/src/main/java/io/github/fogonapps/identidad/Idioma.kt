package io.github.fogonapps.identidad

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Idioma de las apps de Fogón. Por defecto siguen el del teléfono; en Perfil se puede fijar otro.
 *
 * Los textos viven en `res/values/strings.xml` (inglés, el que Android usa si el teléfono está en un
 * idioma que la app no tiene) y `res/values-es/strings.xml` (español). Para agregar un idioma:
 * `res/values-<código>/strings.xml`, sumarlo a [DISPONIBLES] y traducir los datos de cada app.
 */
object Idioma {
    /** Código y nombre (en su propio idioma) de los idiomas que tienen las apps. */
    val DISPONIBLES = listOf("es" to "Español", "en" to "English")

    private const val PREFS = "ui"
    private const val CLAVE = "idioma"

    /** El elegido en la app, o "" si sigue al teléfono. */
    fun elegido(c: Context): String = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CLAVE, "") ?: ""

    fun elegir(c: Context, codigo: String) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CLAVE, codigo).commit()
    }

    /** El que se está usando: el elegido, o el del teléfono si está disponible, o inglés. */
    fun actual(c: Context): String {
        val e = elegido(c)
        if (e.isNotEmpty()) return e
        val sistema = Locale.getDefault().language
        return if (DISPONIBLES.any { it.first == sistema }) sistema else "en"
    }

    /** Un contexto con el idioma elegido aplicado (para la Activity y para los textos fuera de pantalla). */
    fun envolver(c: Context): Context {
        val e = elegido(c)
        if (e.isEmpty()) return c
        val conf = Configuration(c.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(e))
        return c.createConfigurationContext(conf)
    }
}
