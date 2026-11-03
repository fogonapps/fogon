package io.github.fogonapps.cucha

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import io.github.fogonapps.identidad.CAPAS_CUCHA
import io.github.fogonapps.identidad.Idioma
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.TarjetaWidget
import io.github.fogonapps.identidad.coloresWidget
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

/** El widget de las calorías: cuántas quedan hoy, la proteína que lleva y un botón para anotar. */
class WidgetCalorias : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CuchaApp
        val e = app.almacen.data.first()
        val hoy = app.logica.totalDia(e.dias[LocalDate.now().toString()])
        val quedan = e.metas.kcal - hoy.kcal.roundToInt()
        val c = Idioma.envolver(context)
        val f = coloresWidget(context, Paleta.Cucha)
        val anotar = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ANOTAR, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        provideContent {
            TarjetaWidget(
                f, c.getString(if (quedan >= 0) R.string.quedan_kcal else R.string.pasaste_kcal), "${kotlin.math.abs(quedan)}",
                c.getString(R.string.widget_proteina, hoy.proteina.roundToInt(), e.metas.proteina),
                CAPAS_CUCHA, actionStartActivity<MainActivity>(),
                boton = c.getString(R.string.anotar) to actionStartActivity(anotar),
            )
        }
    }

    companion object {
        suspend fun actualizar(c: Context) = runCatching { WidgetCalorias().updateAll(c) }
    }
}

class WidgetCaloriasReceptor : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WidgetCalorias()
}
