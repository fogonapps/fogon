package io.github.fogonapps.forzudo.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
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

private data class Paso(@DrawableRes val dibujo: Int, @StringRes val titulo: Int, @StringRes val texto: Int)

private val pasos = listOf(
    Paso(R.drawable.munieco_tut_hola, R.string.tut_hola, R.string.tut_hola_texto),
    Paso(R.drawable.munieco_tut_anota, R.string.tut_anota, R.string.tut_anota_texto),
    Paso(R.drawable.munieco_tut_racha, R.string.tut_racha, R.string.tut_racha_texto),
    Paso(R.drawable.munieco_tut_rangos, R.string.tut_rangos, R.string.tut_rangos_texto),
    Paso(R.drawable.munieco_tut_hola, R.string.tut_cuerpo, R.string.tut_cuerpo_texto),
    Paso(R.drawable.munieco_tut_datos, R.string.tut_datos, R.string.tut_datos_texto),
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
                TextButton(terminar) { Text(stringResource(R.string.saltar), fontWeight = FontWeight.Bold, color = f.acentoTexto) }
            }
            HorizontalPager(estado, Modifier.weight(1f)) { i ->
                val p = pasos[i]
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Image(painterResource(p.dibujo), null, Modifier.size(220.dp))
                    Text(stringResource(p.titulo), style = MaterialTheme.typography.headlineMedium, color = f.texto, textAlign = TextAlign.Center)
                    Text(stringResource(p.texto), style = MaterialTheme.typography.bodyLarge, color = f.textoSuave, textAlign = TextAlign.Center)
                    if (i == 0) OutlinedTextField(nombre, { nombre = it.take(30) }, singleLine = true, label = { Text(stringResource(R.string.tut_nombre)) }, modifier = Modifier.fillMaxWidth())
                }
            }
            val paso = stringResource(R.string.tut_paso, estado.currentPage + 1, pasos.size)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics { contentDescription = paso },
                horizontalArrangement = Arrangement.Center,
            ) {
                pasos.indices.forEach { i ->
                    Box(Modifier.padding(4.dp).size(if (i == estado.currentPage) 12.dp else 8.dp).clip(CircleShape)
                        .background(if (i == estado.currentPage) f.paleta.tinta else f.linea))
                }
            }
            val ultimo = estado.currentPage == pasos.lastIndex
            BotonRiso(
                stringResource(if (ultimo) R.string.tut_a_entrenar else R.string.siguiente),
                { if (ultimo) terminar() else alcance.launch { estado.animateScrollToPage(estado.currentPage + 1) } },
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

