package io.github.fogonapps.social.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class TextosMercadoTest {
    @Test fun pildorasEnIngles() = comprobarPildoras()

    @Test
    @Config(qualifiers = "es")
    fun pildorasEnEspanol() = comprobarPildoras()

    private fun comprobarPildoras() {
        val recursos = RuntimeEnvironment.getApplication().resources
        val idioma = recursos.configuration.locales[0]
        for (id in listOf(
            R.string.social_editor_crear, R.string.social_mercado_publicar,
            R.string.social_tienda_banners, R.string.social_tienda_marcos,
            R.string.social_mercado_oficial, R.string.social_mercado_nuevo,
            R.string.social_mercado_vendidos, R.string.social_mercado_catalogo,
            R.string.social_mercado_adquiridos, R.string.social_mercado_mios,
            R.string.social_mercado_revisar, R.string.social_mercado_usar,
            R.string.social_mercado_reintentar, R.string.social_mercado_mas,
            R.string.social_mercado_reportar, R.string.social_mercado_aprobar,
            R.string.social_mercado_rechazar, R.string.social_mercado_retirar,
            R.string.social_mercado_quitar_derechos, R.string.social_mercado_elegir,
            R.string.social_mercado_pagar, R.string.social_regalar,
        )) {
            val texto = recursos.getString(id)
            assertEquals(recursos.getResourceEntryName(id), texto.lowercase(idioma), texto)
        }
    }
}
