package io.github.fogonapps.cajon

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.identidad.CAPAS_CAJON
import io.github.fogonapps.identidad.Idioma
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.TarjetaWidget
import io.github.fogonapps.identidad.coloresWidget
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** El widget: cuánto lleva terminado este año y lo último que está viendo, leyendo o escuchando. */
class WidgetCurso : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CajonApp
        val e = app.almacen.data.first()
        val anio = LocalDate.now().year
        val total = Logica.cifras(e, anio).porTipo.values.sum()
        val enCurso = e.vivas.filter { it.estado == Estados.EN_CURSO }.maxByOrNull { it.cambio }
        val c = Idioma.envolver(context)
        val f = coloresWidget(context, Paleta.Cajon)
        provideContent {
            TarjetaWidget(
                f, c.getString(R.string.widget_este_anio, anio), "$total",
                enCurso?.let { c.getString(R.string.widget_en_curso, it.titulo) } ?: c.getString(R.string.widget_nada_en_curso),
                CAPAS_CAJON, actionStartActivity<MainActivity>(),
            )
        }
    }

    companion object {
        suspend fun actualizar(c: Context) = runCatching { WidgetCurso().updateAll(c) }
    }
}

class WidgetCursoReceptor : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WidgetCurso()
}
