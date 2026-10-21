package io.github.fogonapps.forzudo.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.granoPapel
import io.github.fogonapps.forzudo.GymViewModel
import io.github.fogonapps.forzudo.R
import kotlinx.coroutines.launch

private data class Paso(@DrawableRes val dibujo: Int, val titulo: String, val texto: String)

private val pasos = listOf(
    Paso(R.drawable.munieco_tut_hola, "¡Hola! Soy El Forzudo",
        "Tu cuaderno de entrenos. Sin anuncios, sin rastreo y gratis de verdad. Tus datos se quedan en tu teléfono."),
    Paso(R.drawable.munieco_tut_anota, "Entrena y anota",
        "En Entrenar escoge tu rutina y toca Empezar. Escribe el peso y las repeticiones de cada serie y márcala con ✓. Al marcarla arranca el descanso y te aviso cuando termine. Si tocas lo que hiciste la vez anterior, se copia solo."),
    Paso(R.drawable.munieco_tut_racha, "Tu racha es de verdad",
        "Una semana cuenta cuando completas todas las rutinas de tu programa. No se puede marcar a mano: la racha la ganas entrenando."),
    Paso(R.drawable.munieco_tut_rangos, "Sube de rango",
        "Registra tu peso corporal en Perfil y verás tu rango en cada músculo, de Hierro a Diamante, con la próxima meta para subir. Los récords nuevos salen con la chispa."),
    Paso(R.drawable.munieco_tut_hola, "Escucha a tu cuerpo",
        "Te ayudo a llevar la cuenta, pero no soy un médico ni un entrenador. Si tienes una lesión o una condición de salud, consulta a un profesional antes de empezar. Si sientes dolor, mareo o falta de aire, para."),
    Paso(R.drawable.munieco_tut_datos, "Tus datos son tuyos",
        "Puedes exportar un respaldo cuando quieras. Si conectas tu GitHub (opcional), tus entrenos se guardan solos en un archivo privado de tu cuenta."),
)

@Composable
fun Tutorial(vm: GymViewModel, nombreActual: String?) {
    val f = FogonTema.actual
    val estado = rememberPagerState { pasos.size }
    val alcance = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(nombreActual ?: "") }
    val terminar = {
        if (nombre.isNotBlank() && nombre.trim() != nombreActual) vm.ponerNombre(nombre)
        vm.terminarTutorial()
    }
    Box(Modifier.fillMaxSize().background(f.fondo).granoPapel(f.oscuro).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(terminar) { Text("Saltar", fontWeight = FontWeight.Bold, color = f.acentoTexto) }
            }
            HorizontalPager(estado, Modifier.weight(1f)) { i ->
                val p = pasos[i]
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Image(painterResource(p.dibujo), null, Modifier.size(220.dp))
                    Text(p.titulo, style = MaterialTheme.typography.headlineMedium, color = f.texto, textAlign = TextAlign.Center)
                    Text(p.texto, style = MaterialTheme.typography.bodyLarge, color = f.textoSuave, textAlign = TextAlign.Center)
                    if (i == 0) OutlinedTextField(nombre, { nombre = it.take(30) }, singleLine = true, label = { Text("¿Cómo te llamas?") }, modifier = Modifier.fillMaxWidth())
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics { contentDescription = "Paso ${estado.currentPage + 1} de ${pasos.size}" },
                horizontalArrangement = Arrangement.Center,
            ) {
                pasos.indices.forEach { i ->
                    Box(Modifier.padding(4.dp).size(if (i == estado.currentPage) 12.dp else 8.dp).clip(CircleShape)
                        .background(if (i == estado.currentPage) f.paleta.tinta else f.linea))
                }
            }
            val ultimo = estado.currentPage == pasos.lastIndex
            BotonRiso(
                if (ultimo) "¡A entrenar!" else "Siguiente",
                { if (ultimo) terminar() else alcance.launch { estado.animateScrollToPage(estado.currentPage + 1) } },
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

