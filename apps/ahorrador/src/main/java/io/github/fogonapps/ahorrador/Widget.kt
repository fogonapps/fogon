package io.github.fogonapps.ahorrador

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.ahorrador.datos.mesDe
import io.github.fogonapps.identidad.CAPAS_AHORRADOR
import io.github.fogonapps.identidad.Idioma
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.TarjetaWidget
import io.github.fogonapps.identidad.coloresWidget
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** El widget de la plata: lo que tienes, lo que salió este mes y un botón para anotar un gasto. */
class WidgetPlata : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as AhorradorApp
        val e = app.almacen.data.first()
        val mes = mesDe(LocalDate.now())
        val c = Idioma.envolver(context)
        val f = coloresWidget(context, Paleta.Ahorrador)
        val anotar = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ANOTAR, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        provideContent {
            TarjetaWidget(
                f, c.getString(R.string.tienes), Formato.pesos(app.logica.patrimonio(e)),
                c.getString(R.string.widget_salio, Formato.pesos(app.logica.gastos(e, mes))),
                CAPAS_AHORRADOR, actionStartActivity<MainActivity>(),
                boton = c.getString(R.string.anotar) to actionStartActivity(anotar),
            )
        }
    }

    companion object {
        suspend fun actualizar(c: Context) = runCatching { WidgetPlata().updateAll(c) }
    }
}

class WidgetPlataReceptor : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WidgetPlata()
}
