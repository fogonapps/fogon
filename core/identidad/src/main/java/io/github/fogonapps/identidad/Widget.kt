package io.github.fogonapps.identidad

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/*
 * La tarjeta de los widgets de Fogón (Glance): el papel del tema elegido en la app, una etiqueta, el número grande,
 * una línea de abajo y, a la derecha, el personaje en las tintas del tema. Al tocarla se abre la app.
 * Los widgets no pueden usar las letras de la app: van con la del teléfono.
 */

/** Los colores del tema que eligió la persona, para el widget. */
fun coloresWidget(c: Context, paleta: Paleta): Fogon = fogonDe(paleta, AlmacenApariencia.de(c).estado.value)

/** Las capas del muñeco para teñirlo en el widget: el cuerpo y la cabeza. */
data class CapasPersonaje(val cuerpo: Int, val cabeza: Int)

val CAPAS_FORZUDO = CapasPersonaje(R.drawable.personaje_el_forzudo_cuerpo, R.drawable.personaje_el_forzudo_cabeza)
val CAPAS_AHORRADOR = CapasPersonaje(R.drawable.personaje_el_ahorrador_cuerpo, R.drawable.personaje_el_ahorrador_cabeza)
val CAPAS_CUCHA = CapasPersonaje(R.drawable.personaje_la_cucha_cuerpo, R.drawable.personaje_la_cucha_cabeza)
val CAPAS_CAJON = CapasPersonaje(R.drawable.personaje_el_cajon_cuerpo, R.drawable.personaje_el_cajon_cabeza)

@Composable
fun TarjetaWidget(
    f: Fogon,
    etiqueta: String,
    numero: String,
    pie: String,
    personaje: CapasPersonaje,
    abrir: Action,
    boton: Pair<String, Action>? = null,
) {
    val fondo = androidx.compose.ui.graphics.lerp(f.fondo, if (f.oscuro) Color.White else Color.Black, 0.04f)
    Row(
        GlanceModifier.fillMaxSize().cornerRadius(26.dp).background(ColorProvider(fondo)).clickable(abrir).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Text(etiqueta, style = TextStyle(color = ColorProvider(f.textoSuave), fontSize = 13.sp), maxLines = 1)
            // El número grande se achica si es largo (un saldo con muchas cifras), para que no se corte.
            val tam = when { numero.length <= 5 -> 40.sp; numero.length <= 9 -> 28.sp; else -> 22.sp }
            Text(numero, style = TextStyle(color = ColorProvider(f.numero), fontSize = tam, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(pie, style = TextStyle(color = ColorProvider(f.textoSuave), fontSize = 12.sp), maxLines = 2)
            if (boton != null) {
                Spacer(GlanceModifier.height(8.dp))
                Box(GlanceModifier.cornerRadius(18.dp).background(ColorProvider(f.tinta1)).clickable(boton.second).padding(horizontal = 14.dp, vertical = 7.dp)) {
                    Text(boton.first, style = TextStyle(color = ColorProvider(f.sobreTinta1), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
        Box(GlanceModifier.size(76.dp), contentAlignment = Alignment.Center) {
            Image(ImageProvider(personaje.cuerpo), null, GlanceModifier.size(76.dp), colorFilter = ColorFilter.tint(ColorProvider(f.texto)))
            Image(ImageProvider(personaje.cabeza), null, GlanceModifier.size(76.dp), colorFilter = ColorFilter.tint(ColorProvider(tintaCabeza(f))))
        }
    }
}

/** Pide a Android que ponga el widget [receptor] en la pantalla de inicio (si el teléfono lo deja). */
suspend fun pedirWidget(c: Context, receptor: Class<out androidx.glance.appwidget.GlanceAppWidgetReceiver>): Boolean =
    runCatching { androidx.glance.appwidget.GlanceAppWidgetManager(c).requestPinGlanceAppWidget(receptor) }.getOrDefault(false)
