package io.github.fogonapps.forzudo

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import io.github.fogonapps.forzudo.datos.inicioSemana
import io.github.fogonapps.forzudo.datos.iso
import io.github.fogonapps.identidad.CAPAS_FORZUDO
import io.github.fogonapps.identidad.Idioma
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.TarjetaWidget
import io.github.fogonapps.identidad.coloresWidget
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** El widget de la racha: semanas seguidas y cuántos entrenos van esta semana. Al tocarlo, abre la app. */
class WidgetRacha : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ForzudoApp
        val e = app.logica.normalizar(app.almacen.data.first())
        val hoy = LocalDate.now()
        val dias = app.logica.dias(e)
        val racha = app.logica.racha(e, dias, hoy)
        val ws = inicioSemana(iso(hoy))
        val hechos = e.sessions.filter { inicioSemana(it.date) == ws }.map { it.day }.toSet().size
        val c = Idioma.envolver(context)
        val f = coloresWidget(context, Paleta.Forzudo)
        provideContent {
            TarjetaWidget(
                f, c.resources.getQuantityString(R.plurals.semanas_seguidas, racha.actual).replaceFirstChar { it.uppercase() },
                "${racha.actual}", c.getString(R.string.widget_semana, hechos, dias.size),
                CAPAS_FORZUDO, actionStartActivity<MainActivity>(),
            )
        }
    }

    companion object {
        /** Se llama después de guardar: así el widget no se queda atrás. */
        suspend fun actualizar(c: Context) = runCatching { WidgetRacha().updateAll(c) }
    }
}

class WidgetRachaReceptor : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WidgetRacha()
}
