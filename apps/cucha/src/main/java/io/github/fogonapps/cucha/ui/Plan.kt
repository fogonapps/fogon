package io.github.fogonapps.cucha.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.Pantalla
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.aparecer
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Lo que se está eligiendo en el menú: semana, día (1 = lunes) y comida. */
private data class Casilla(val letra: String, val dia: Int, val comida: String)

/** La pestaña del menú: qué receta va en cada comida de la semana (A, o A y B que se turnan). */
@Composable
fun Plan(vm: CuchaViewModel, e: Estado) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    val letraHoy = vm.L.letra(vm.hoy(), e.alternar)
    var elegida by rememberSaveable { mutableStateOf(letraHoy) }
    val letra = if (e.alternar) elegida else "A"
    var eligiendo by androidx.compose.runtime.remember { mutableStateOf<Casilla?>(null) }

    Seccion(stringResource(R.string.menu_semana), Modifier.padding(top = 12.dp)) {
        Pildora(stringResource(R.string.mercado), Iconos.Mercado, acento = true) { vm.abrir(Pantalla.Mercado) }
    }
    Pista(stringResource(R.string.menu_pista))
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).vidrio().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.semanas_ab), style = MaterialTheme.typography.bodyLarge, color = f.texto)
            Text(stringResource(R.string.semanas_ab_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        Switch(e.alternar, vm::alternar)
    }
    if (e.alternar) Selector(
        listOf("A" to stringResource(R.string.semana_letra, "A"), "B" to stringResource(R.string.semana_letra, "B")), letra, { elegida = it },
        Modifier.padding(horizontal = 16.dp),
    )
    if (e.alternar) Pista(stringResource(R.string.esta_semana_es, letraHoy))

    val hoy = vm.hoy().dayOfWeek.value
    DayOfWeek.entries.forEachIndexed { i, dow ->
        Column(Modifier.aparecer(i).fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dow.getDisplayName(TextStyle.FULL, Locale.forLanguageTag(idioma)).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium, color = f.texto)
                if (dow.value == hoy && letra == letraHoy) Text(stringResource(R.string.hoy), style = MaterialTheme.typography.labelMedium, color = f.acentoTexto)
            }
            vm.comidas.forEach { c ->
                val receta = e.plan[vm.L.clavePlan(letra, dow.value, c)]?.let { vm.L.receta(e, it) }
                Row(
                    Modifier.fillMaxWidth().clip(io.github.fogonapps.identidad.forma(10.dp)).clickable { eligiendo = Casilla(letra, dow.value, c) }
                        .heightIn(min = 40.dp).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(nombreComida(c), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.weight(0.42f))
                    Text(receta?.nombre(idioma) ?: "—", style = MaterialTheme.typography.bodyLarge, color = if (receta != null) f.texto else f.textoSuave,
                        modifier = Modifier.weight(0.58f), maxLines = 2)
                }
            }
        }
    }

    eligiendo?.let { cas ->
        val opciones = vm.L.recetas(e).sortedWith(compareBy({ cas.comida !in it.comidas }, { it.nombre(idioma) }))
        AlertDialog(
            onDismissRequest = { eligiendo = null },
            title = { Text(nombreComida(cas.comida)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    opciones.forEach { r ->
                        Text(r.nombre(idioma), style = MaterialTheme.typography.bodyLarge, color = f.texto,
                            modifier = Modifier.fillMaxWidth().clickable { vm.ponerEnPlan(cas.letra, cas.dia, cas.comida, r.id); eligiendo = null }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = { TextButton({ vm.ponerEnPlan(cas.letra, cas.dia, cas.comida, null); eligiendo = null }) { Text(stringResource(R.string.dejar_vacio)) } },
            dismissButton = { TextButton({ eligiendo = null }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

/** "350 g" o "1,2 kg". */
fun peso(g: Double, idioma: String) = if (g >= 1000) "${cifra(g / 1000, idioma)} kg" else "${Math.round(g)} g"

/** La lista de mercado: los ingredientes del menú de la semana y lo agregado a mano, para ir tachando. */
@Composable
fun Mercado(vm: CuchaViewModel, e: Estado) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    val c = LocalContext.current
    var proxima by rememberSaveable { mutableStateOf(false) }
    var nuevo by rememberSaveable { mutableStateOf("") }
    val lunes = vm.L.lunes(vm.hoy()).plusWeeks(if (proxima) 1 else 0)
    val res = LocalContext.current.resources
    // Lo que se compra por unidades (huevos, bananos…) dice también cuántas son, redondeando hacia arriba.
    val lista = vm.L.mercado(e, lunes).map { (a, g) ->
        val unidad = a.porciones.firstOrNull { it.es == "Unidad" }?.gramos
        val n = unidad?.let { Math.ceil(g / it - 0.1).toInt() }?.takeIf { it >= 1 }
        a.nombre(idioma) to (peso(g, idioma) + (n?.let { " · " + res.getQuantityString(R.plurals.unidades_aprox, it, it) } ?: ""))
    }
    val todo = lista + e.mercadoExtra.map { it to "" }

    Cabecera(stringResource(R.string.mercado), vm::cerrar)
    Selector(listOf(false to stringResource(R.string.esta_semana), true to stringResource(R.string.proxima_semana)), proxima, { proxima = it }, Modifier.padding(horizontal = 16.dp))
    Pista(stringResource(R.string.mercado_pista))
    if (lista.isEmpty()) Pista(stringResource(R.string.mercado_vacio))
    todo.forEachIndexed { i, (nombre, cuanto) ->
        val hecho = nombre in e.mercadoHecho
        FilaVidrio({ vm.marcarMercado(nombre) }, Modifier.aparecer(i.coerceAtMost(12))) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (hecho) f.tinta1 else f.superficie).border(f.borde, if (hecho) f.tinta1 else f.linea, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (hecho) IconoRiso(Iconos.Chulo, null, tamano = 14.dp, tinta = f.sobreTinta1) }
            Text(nombre, style = MaterialTheme.typography.bodyLarge, color = if (hecho) f.textoSuave else f.texto, modifier = Modifier.weight(1f),
                textDecoration = if (hecho) TextDecoration.LineThrough else null)
            if (cuanto.isNotEmpty()) Text(cuanto, fontFamily = f.cifras, color = f.textoSuave)
            else BotonIcono(Iconos.Cerrar, stringResource(R.string.quitar)) { vm.quitarMercado(nombre) }
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CampoTexto(nuevo, { nuevo = it }, stringResource(R.string.agregar_algo), max = 60)
        BotonSecundario(stringResource(R.string.agregar), { vm.agregarMercado(nuevo); nuevo = "" }, Modifier.fillMaxWidth(), habilitado = nuevo.isNotBlank())
        val titulo = stringResource(R.string.mercado)
        BotonRiso(stringResource(R.string.compartir_lista), {
            val texto = titulo + "\n" + todo.filter { it.first !in e.mercadoHecho }.joinToString("\n") { (n, q) -> "• $n" + if (q.isNotEmpty()) " ($q)" else "" }
            c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), titulo))
        }, Modifier.fillMaxWidth(), habilitado = todo.isNotEmpty())
        if (e.mercadoHecho.isNotEmpty()) BotonSecundario(stringResource(R.string.desmarcar_todo), vm::limpiarMercado, Modifier.fillMaxWidth())
    }
}
