package io.github.fogonapps.identidad

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** El manifiesto de Fogón, igual en todas las apps y sin necesitar internet. */
@Composable
fun ManifiestoFogon(modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val parrafo: @Composable (String) -> Unit = {
        Text(it, style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.padding(horizontal = 20.dp))
    }
    val titulo: @Composable (String) -> Unit = {
        Text(
            it.uppercase(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 22.sp, lineHeight = 26.sp), color = f.texto,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).semantics { heading() },
        )
    }
    Column(modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            painterResource(if (f.oscuro) R.drawable.fogon_logotipo_claro else R.drawable.fogon_logotipo), "Fogón",
            Modifier.align(Alignment.CenterHorizontally).size(width = 220.dp, height = 100.dp),
        )
        Text(
            "Tu atención vale más que un anuncio.".uppercase(),
            style = MaterialTheme.typography.displayMedium, color = f.acentoTexto, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Text(
            "Si las herramientas de hoy nos permiten hacer nuestras propias apps, ¿por qué seguir regalando nuestra información y nuestra atención a apps llenas de anuncios y micropagos?",
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), color = f.textoSuave,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
        )
        parrafo("Durante años, tener una app en el teléfono significó aceptar un trato que nadie firmó de verdad: la app es \"gratis\", pero la pagamos con nuestros datos, nuestro tiempo y nuestra paciencia.")
        parrafo("En el fogón de la casa nunca se cocinó para vender. Se cocinaba para los de la casa, con lo que había, y siempre alcanzaba para uno más. Fogón hace lo mismo con el software: apps cocinadas en casa, con ayuda de la inteligencia artificial, para resolver necesidades reales. No buscan ganar dinero, y se comparten con quien llegue.")
        titulo("No es la primera vez")
        parrafo("La minga: vecinos que trabajan gratis por algo de todos. La rueca de Gandhi: hilar en casa en vez de comprar. Los pioneros de Rochdale: 28 trabajadores que abrieron su propia tienda. Linux: \"solo un pasatiempo\" que hoy mueve internet. Wikipedia: la enciclopedia más grande, sin un solo anuncio.")
        parrafo("Gente común, herramientas sencillas, trabajo compartido y nadie contra nadie. Hoy, la herramienta sencilla es la inteligencia artificial.")
        titulo("Sin enemigos")
        parrafo("Fogón no está contra nadie. No copiamos marcas, no atacamos empresas, no rompemos reglas. Solo encendemos el fogón y mostramos, con hechos, que existe otra manera.")
        titulo("Lo que prometemos")
        listOf(
            "Nada de anuncios.",
            "Nada de micropagos ni suscripciones: la app está completa desde el primer día.",
            "Tus datos son tuyos: se guardan en tu teléfono y los puedes exportar cuando quieras.",
            "Sin internet, si no hace falta.",
            "Solo los permisos indispensables, y explicados.",
            "Código abierto (GPL-3.0).",
            "Hecha con IA, y lo decimos. Una persona la revisa y responde por ella.",
        ).forEach { Logro(it, Modifier.padding(horizontal = 20.dp)) }
        Text(
            "Siempre alcanza para uno más.", style = MaterialTheme.typography.headlineSmall, color = f.texto,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}
