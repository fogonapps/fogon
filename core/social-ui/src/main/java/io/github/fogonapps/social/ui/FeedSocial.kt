package io.github.fogonapps.social.ui
import io.github.fogonapps.identidad.IconosSociales as Iconos
import io.github.fogonapps.identidad.PistaSocial as Pista
import io.github.fogonapps.identidad.PildoraSocial as Pildora
import androidx.compose.runtime.Composable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.PublicacionSocial

@Composable
fun PestanasFeedSocial(pestana: Int, usuario: String?, cambiarPestana: (Int) -> Unit, textoPublicar: String? = null, publicar: () -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.social_red_pestana_siguiendo, R.string.social_red_explorar).forEachIndexed { i, t ->
            val elegida = pestana == i
            Box(Modifier.clip(FormaPildora).background(if (elegida) f.tinta1 else f.superficie).border(f.borde, if (elegida) f.tinta1 else f.linea, FormaPildora)
                .clickable { cambiarPestana(i) }.padding(horizontal = 16.dp, vertical = 9.dp)) {
                Text(stringResource(t), style = MaterialTheme.typography.labelLarge, color = if (elegida) f.sobreTinta1 else f.texto)
            }
        }
        Box(Modifier.weight(1f))
        if (usuario != null) Pildora(textoPublicar ?: stringResource(R.string.social_red_publicar), Iconos.Mas, acento = true) { publicar() }
    }
}
@Composable
fun <M> ListaFeedSocial(lista: List<Pair<PublicacionSocial, M>>?, pestana: Int, mas: () -> Unit, pie: String, tarjeta: @Composable (PublicacionSocial, M) -> Unit) {
    val f = FogonTema.actual
    when {
        lista == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        lista.isEmpty() -> Pista(stringResource(if (pestana == 0) R.string.social_red_feed_vacio else R.string.social_red_explorar_vacio), Modifier.padding(16.dp))
        else -> {
            lista.forEach { (p, motivo) -> tarjeta(p, motivo) }
            if (pestana == 0 && lista.size >= 40) BotonSecundario(stringResource(R.string.social_red_mas), { mas() }, Modifier.fillMaxWidth().padding(16.dp))
            // Sin scroll infinito: al final, estás al día.
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                io.github.fogonapps.identidad.Chispa(tamano = 22.dp)
                Text(stringResource(R.string.social_red_al_dia), style = MaterialTheme.typography.titleMedium, color = f.texto)
                Text(pie, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, textAlign = TextAlign.Center)
            }
        }
    }
}
