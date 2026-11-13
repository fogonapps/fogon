package io.github.fogonapps.cucha.ui

import io.github.fogonapps.identidad.destinoGuia
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.Pantalla
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.NumeroRiso
import io.github.fogonapps.identidad.Personaje
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.haloPuntos
import io.github.fogonapps.identidad.rebote
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import io.github.fogonapps.identidad.R as RId

/** El nombre de una comida ("desayuno" → "Desayuno"). */
@Composable
fun nombreComida(id: String) = stringResource(
    when (id) {
        "desayuno" -> R.string.comida_desayuno
        "media_manana" -> R.string.comida_media_manana
        "almuerzo" -> R.string.comida_almuerzo
        "algo" -> R.string.comida_algo
        else -> R.string.comida_cena
    },
)

/** "Hoy", "Ayer" o la fecha ("sábado 24 de octubre"). */
@Composable
fun nombreDia(f: LocalDate, hoy: LocalDate, idioma: String): String = when (f) {
    hoy -> stringResource(R.string.hoy)
    hoy.minusDays(1) -> stringResource(R.string.ayer)
    else -> f.format(DateTimeFormatter.ofPattern(if (idioma == "en") "EEEE, MMMM d" else "EEEE d 'de' MMMM", Locale.forLanguageTag(idioma)))
        .replaceFirstChar { it.uppercase() }
}

/** Para la cabecera de Hoy: "Hoy", "Ayer" o "Viernes 23" (el mes solo si es otro). */
@Composable
private fun nombreDiaCorto(f: LocalDate, hoy: LocalDate, idioma: String): String = when {
    f == hoy || f == hoy.minusDays(1) -> nombreDia(f, hoy, idioma)
    else -> f.format(DateTimeFormatter.ofPattern(
        if (f.month == hoy.month) "EEEE d" else if (idioma == "en") "MMM d" else "d MMM",
        Locale.forLanguageTag(idioma),
    )).replaceFirstChar { it.uppercase() }
}

@Composable
fun Hoy(vm: CuchaViewModel, e: Estado) {
    val f = FogonTema.actual
    val dia by vm.dia.collectAsStateWithLifecycle()
    val forzudo by vm.forzudo.collectAsStateWithLifecycle()
    val hoy = vm.hoy()
    val idioma = vm.idioma()
    val fecha = dia.toString()
    val d = e.dias[fecha]
    val total = vm.L.totalDia(d)
    val menu = vm.L.menuDe(e, dia)

    // La cara del día de La Cucha (dormida sin nada anotado, sorprendida si te pasaste, feliz cerca de la meta), que
    // va en el círculo del perfil (X-07).
    val quedan = e.metas.kcal - total.kcal.roundToInt()
    val cara = when {
        total.kcal <= 0.0 -> io.github.fogonapps.identidad.Cara.DORMIDO
        quedan < 0 -> io.github.fogonapps.identidad.Cara.SORPRESA
        total.kcal >= e.metas.kcal * 0.6 -> io.github.fogonapps.identidad.Cara.FELIZ
        else -> io.github.fogonapps.identidad.Cara.NORMAL
    }
    // La cabecera común (X-07): tu nombre y la frase; La Cucha, en el círculo del perfil.
    // La frase del saludo, como en El Cajón: las propias (cu_), los votos y el menú al mantenerla pulsada.
    val propias = androidx.compose.ui.res.stringArrayResource(R.array.frases_inicio)
    val base = androidx.compose.runtime.remember(propias.toList()) { propias.mapIndexed { i, t -> io.github.fogonapps.social.FraseBase("cu_${i + 1}", t) } }
    val frase = io.github.fogonapps.social.ui.rememberFraseDelSaludo(vm.frases, base, idioma)
    var menuFrase by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    io.github.fogonapps.perfil.CabeceraInicio(e.nombre.ifBlank { stringResource(R.string.hola) },
        frase?.frase?.texto.orEmpty(),
        onMantener = { if (frase != null) menuFrase = true },
        adorno = { if (frase?.gusta == true) io.github.fogonapps.identidad.IconoRiso(io.github.fogonapps.identidad.IconosSociales.Corazon, stringResource(io.github.fogonapps.social.ui.R.string.social_frase_te_gusta), tamano = 14.dp, tinta = f.acentoTexto) },
        perfil = { androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) { io.github.fogonapps.social.ui.ChispasArriba(interfazCuenta(vm)); androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.destinoGuia("perfil")) {
            io.github.fogonapps.identidad.BotonPerfilMascota(io.github.fogonapps.identidad.Quien.CUCHA, stringResource(R.string.perfil), cara,
                foto = e.fotoPerfil.takeIf { it.isNotBlank() }?.let { { io.github.fogonapps.perfil.FotoPerfilLocal(it, tamano = 48.dp) } }) { vm.abrir(Pantalla.Perfil) }
        }}})
    if (menuFrase && frase != null) io.github.fogonapps.social.ui.MenuFrase(frase.frase.texto, frase.gusta, cerrar = { menuFrase = false },
        onGusta = frase::gustar, onNoMostrar = frase::noMostrar, onCompartido = frase::compartida)

    // El día que ves, con flechas para ir a días anteriores. El nombre va corto ("Viernes 23") y las flechas quedan
    // quietas a la derecha (la de adelante guarda su lugar en Hoy), para tocarlas varias veces sin que se muevan.
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            nombreDiaCorto(dia, hoy, idioma), fontFamily = f.titulos, fontSize = 22.sp, color = f.texto, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Row(Modifier.padding(start = 8.dp).clip(FormaPildora).background(f.superficie).border(f.borde, f.linea, FormaPildora)) {
            BotonIcono(Iconos.Atras, stringResource(R.string.dia_anterior)) { vm.verDia(dia.minusDays(1)) }
            Box(Modifier.alpha(if (dia < hoy) 1f else 0.3f)) {
                BotonIcono(Iconos.Siguiente, stringResource(R.string.dia_siguiente), activo = dia < hoy) { vm.verDia(dia.plusDays(1)) }
            }
        }
    }

    // Las calorías que quedan, en el anillo, con lo comido y la meta al lado; debajo, los macros.
    Column(
        Modifier.aparecer(0).fillMaxWidth().padding(16.dp).vidrio().haloPuntos(f.tinta2.copy(alpha = 0.35f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Anillo((total.kcal / e.metas.kcal.coerceAtLeast(1)).toFloat(), Modifier.size(140.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NumeroRiso("${kotlin.math.abs(quedan)}", 32.sp)
                    Text(stringResource(if (quedan >= 0) R.string.kcal_quedan else R.string.kcal_de_mas), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
                }
            }
            Column(Modifier.weight(1f).padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Dato(stringResource(R.string.comido), "${total.kcal.roundToInt()} kcal")
                Dato(stringResource(R.string.meta), "${e.metas.kcal} kcal")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BarraMacro(stringResource(R.string.proteina), total.proteina, e.metas.proteina, f.tinta1, Modifier.weight(1f), pasarseEsMalo = false)
            BarraMacro(stringResource(R.string.carbos), total.carbos, e.metas.carbos, f.tinta2, Modifier.weight(1f))
            BarraMacro(stringResource(R.string.grasa), total.grasa, e.metas.grasa, f.textoSuave, Modifier.weight(1f))
        }
        if (total.fibra > 0 || total.azucar > 0) Text(
            stringResource(R.string.fibra_azucar_dia, total.fibra.roundToInt(), total.azucar.roundToInt()),
            style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
        )
    }

    // El Forzudo: el entreno de hoy y el peso.
    forzudo?.let { z ->
        FilaVidrio(modifier = Modifier.aparecer(1)) {
            Ficha(f.tinta1, Iconos.Pesa)
            Column(Modifier.weight(1f)) {
                Text(
                    if (z.entrenoHoy != null && dia == hoy) stringResource(R.string.forzudo_hoy, z.entrenoHoy.ifBlank { stringResource(R.string.forzudo_entreno) })
                    else pluralStringResource(R.plurals.forzudo_semana, z.entrenosSemana),
                    style = MaterialTheme.typography.bodyLarge, color = f.texto,
                )
                Text(
                    z.peso?.let { stringResource(R.string.forzudo_peso, cifra(it.kg, idioma)) } ?: stringResource(R.string.forzudo_sin_peso),
                    style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
                )
            }
        }
    }

    // El agua: un vaso por cada 250 ml de la meta.
    Agua(e.metas.aguaMl, d?.aguaMl ?: 0, idioma, Modifier.aparecer(2)) { vm.agua(fecha, it) }

    // Las cinco comidas.
    vm.comidas.forEachIndexed { i, c ->
        TarjetaComida(vm, e, c, fecha, menu[c]?.let { it.id to it.nombre(idioma) }, Modifier.aparecer(3 + i))
    }

    if (d?.anotados.isNullOrEmpty() && dia == hoy) {
        Pista(stringResource(R.string.hoy_vacio), centro = true)
    }
}

@Composable
private fun pluralStringResource(id: Int, n: Int) = androidx.compose.ui.res.pluralStringResource(id, n, n)

/** Un botón redondo con solo un ícono. */
@Composable
fun BotonIcono(icono: io.github.fogonapps.identidad.Dibujo, descripcion: String, grande: Boolean = false, activo: Boolean = true, onClick: () -> Unit) {
    val f = FogonTema.actual
    Box(
        Modifier.rebote(0.9f).size(if (grande) 46.dp else 40.dp).clip(CircleShape)
            .then(if (grande) Modifier.background(f.superficie).border(f.borde, f.linea, CircleShape) else Modifier)
            .clickable(enabled = activo, onClick = onClick).semantics { contentDescription = descripcion; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { IconoRiso(icono, null, tamano = if (grande) 22.dp else 18.dp, tinta = f.texto) }
}

/** Los vasos de agua: se tocan para sumar uno. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Agua(meta: Int, lleva: Int, idioma: String, modifier: Modifier, onCambio: (Int) -> Unit) {
    val f = FogonTema.actual
    val vasos = (meta / 250).coerceIn(1, 16)
    val llenos = lleva / 250
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.agua), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.agua_lleva, cifra(lleva / 1000.0, idioma, 2), cifra(meta / 1000.0, idioma, 2)), fontFamily = f.cifras, color = f.textoSuave)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val sumar = stringResource(R.string.agua_sumar)
            repeat(maxOf(vasos, llenos)) { i ->
                val lleno = i < llenos
                Box(
                    Modifier.rebote(0.85f).size(30.dp).clip(CircleShape)
                        .background(if (lleno) f.tinta2 else f.superficie).border(f.borde, if (lleno) f.tinta2 else f.linea, CircleShape)
                        .clickable { onCambio(if (lleno && i == llenos - 1) -250 else 250) }
                        .semantics { contentDescription = sumar },
                    contentAlignment = Alignment.Center,
                ) { IconoRiso(Iconos.Gota, null, tamano = 14.dp, tinta = if (lleno) f.fondo else f.textoSuave) }
            }
        }
    }
}

/** Una comida del día: su total, lo anotado y los botones para anotar. */
@Composable
private fun TarjetaComida(vm: CuchaViewModel, e: Estado, comida: String, fecha: String, enMenu: Pair<String, String>?, modifier: Modifier) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    val anotados = e.dias[fecha]?.anotados.orEmpty().filter { it.comida == comida }
    val kcal = anotados.sumOf { it.n.kcal }.roundToInt()
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nombreComida(comida), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.weight(1f))
            if (kcal > 0) Text("$kcal kcal", fontFamily = f.cifras, color = f.texto)
        }
        anotados.forEach { a ->
            Row(
                Modifier.fillMaxWidth().clip(io.github.fogonapps.identidad.forma(12.dp))
                    .clickable { vm.abrir(if (a.ref.startsWith("rapido:")) Pantalla.Rapido(comida, fecha, a.id) else Pantalla.Cantidad(a.ref, comida, fecha, editar = a.id)) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1)
                    Text(a.detalle, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
                }
                Text("${a.n.kcal.roundToInt()}", fontFamily = f.cifras, color = f.textoSuave)
            }
        }
        if (anotados.isEmpty() && enMenu != null) {
            // Lo que dice el menú de la semana para esta comida: un toque y se anota una porción.
            Row(
                Modifier.rebote().clip(FormaPildora).background(f.tinta1.copy(alpha = 0.14f))
                    .clickable { vm.abrir(Pantalla.Cantidad("r:${enMenu.first}", comida, fecha)) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconoRiso(Iconos.Plan, null, tamano = 16.dp, tinta = f.acentoTexto)
                Text(stringResource(R.string.en_el_menu, enMenu.second), style = MaterialTheme.typography.labelLarge, color = f.acentoTexto, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pildora(stringResource(R.string.anotar), Iconos.Mas, acento = true) { vm.abrir(Pantalla.Anotar(comida, fecha)) }
            if (anotados.isEmpty() && e.dias[LocalDate.parse(fecha).minusDays(1).toString()]?.anotados?.any { it.comida == comida } == true)
                Pildora(stringResource(R.string.repetir_ayer), Iconos.Repetir) { vm.repetirAyer(comida, fecha) }
        }
    }
}

/** Un botón pequeño en píldora, con ícono. */
@Composable
fun Pildora(texto: String, icono: io.github.fogonapps.identidad.Dibujo? = null, acento: Boolean = false, onClick: () -> Unit) {
    val f = FogonTema.actual
    Row(
        Modifier.rebote().clip(FormaPildora).background(if (acento) f.tinta1 else f.superficie)
            .border(f.borde, if (acento) f.tinta1 else f.linea, FormaPildora)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icono != null) IconoRiso(icono, null, tamano = 16.dp, tinta = if (acento) f.sobreTinta1 else f.texto)
        Text(texto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (acento) f.sobreTinta1 else f.texto)
    }
}
