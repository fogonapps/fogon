package io.github.fogonapps.ahorrador.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.BuildConfig
import io.github.fogonapps.ahorrador.Pantalla
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Sistema
import io.github.fogonapps.identidad.AcercaDeFogon
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.CORREO_FOGON
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Idioma
import io.github.fogonapps.identidad.LicenciasFogon
import io.github.fogonapps.identidad.URL_CODIGO
import io.github.fogonapps.identidad.R as RId
import java.net.URLEncoder

/* =================== Perfil y ajustes =================== */

@Composable
fun Perfil(
    vm: AhorradorViewModel,
    e: Estado,
    onExportar: () -> Unit,
    onExportarCsv: () -> Unit,
    onImportar: () -> Unit,
    candadoDisponible: Boolean,
) {
    val confirmar = LocalConfirmar.current
    val candado by vm.candado.collectAsState()
    var nombre by rememberSaveable { mutableStateOf(e.nombre.orEmpty()) }
    var reparto by remember { mutableStateOf(false) }

    Cabecera(stringResource(R.string.perfil), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp)) {
        CampoTexto(nombre, { nombre = it; vm.ponerNombre(it) }, stringResource(R.string.tut_tu_nombre))
    }

    Seccion(stringResource(R.string.como_te_organizas))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Sistema.entries.forEach { s -> TarjetaSistema(s, s == e.sistema) { vm.cambiarSistema(s) } }
        if (e.sistema == Sistema.REGLA_50_30_20) BotonSecundario(stringResource(R.string.cambiar_reparto), { reparto = true }, Modifier.fillMaxWidth())
    }

    Seccion(stringResource(R.string.tu_plata))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonSecundario(stringResource(R.string.cuentas), { vm.abrir(Pantalla.Cuentas) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.pagos_fijos), { vm.abrir(Pantalla.Fijos) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.deudas_titulo), { vm.abrir(Pantalla.Deudas) }, Modifier.fillMaxWidth())
    }

    Seccion(stringResource(R.string.opciones))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonRiso(stringResource(RId.string.fogon_ap_titulo), { vm.abrir(Pantalla.Apariencia) }, Modifier.fillMaxWidth())
        Interruptor(stringResource(R.string.gmf), stringResource(R.string.gmf_pista), e.gmf, vm::cambiarGmf)
        if (candadoDisponible) Interruptor(stringResource(R.string.candado), stringResource(R.string.candado_pista), candado, vm::cambiarCandado)
        Text(stringResource(R.string.idioma), style = MaterialTheme.typography.labelLarge, color = FogonTema.actual.textoSuave)
        SelectorIdioma(vm)
    }

    Seccion(stringResource(R.string.respaldo))
    Pista(stringResource(R.string.respaldo_pista))
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonSecundario(stringResource(R.string.guardar_respaldo), onExportar, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.recuperar_respaldo), {
            confirmar(vm.tx(R.string.recuperar_pregunta), vm.tx(R.string.recuperar_texto), onImportar)
        }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.exportar_csv), onExportarCsv, Modifier.fillMaxWidth())
    }

    Seccion(stringResource(R.string.fogon))
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonSecundario(stringResource(R.string.manifiesto), { vm.abrir(Pantalla.Manifiesto) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.preguntas_frecuentes), { vm.abrir(Pantalla.Preguntas) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.reportar_boton), { vm.abrir(Pantalla.Reporte) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.acerca_de), { vm.abrir(Pantalla.AcercaDe) }, Modifier.fillMaxWidth())
    }
    Espacio(40.dp)

    if (reparto) DialogoReparto(e.reparto, vm::cambiarReparto) { reparto = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorIdioma(vm: AhorradorViewModel) {
    val ctx = LocalContext.current
    val elegido = remember { Idioma.elegido(ctx) }
    val opciones = listOf("" to stringResource(R.string.idioma_telefono)) + Idioma.DISPONIBLES
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { (codigo, nombre) ->
            FilterChip(codigo == elegido, {
                if (codigo != elegido) {
                    vm.cambiarIdioma(codigo)
                    (ctx as? android.app.Activity)?.recreate()
                }
            }, { Text(nombre) })
        }
    }
}

/* =================== Acerca de, licencias y preguntas =================== */

@Composable
fun AcercaDe(vm: AhorradorViewModel) {
    val uri = LocalUriHandler.current
    Column {
        Cabecera(stringResource(R.string.acerca_de_corto), vm::cerrar)
        AcercaDeFogon(
            personaje = RId.drawable.personaje_el_ahorrador,
            nombreApp = stringResource(R.string.app_name),
            nombrePersonaje = stringResource(R.string.app_name),
            version = BuildConfig.VERSION_NAME,
            creditos = stringArrayResource(R.array.creditos).toList(),
            onVerManifiesto = { vm.abrir(Pantalla.Manifiesto) },
            onVerLicencias = { vm.abrir(Pantalla.Licencias) },
            onAbrirEnlace = { uri.openUri(it) },
            avisoSalud = stringResource(R.string.aviso_plata),
            avisoTitulo = stringResource(R.string.aviso_plata_titulo),
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun Licencias(vm: AhorradorViewModel) {
    Column {
        Cabecera(stringResource(R.string.licencias), vm::cerrar)
        // El Ahorrador no usa internet ni datos de terceros: solo lleva lo común de Fogón.
        LicenciasFogon(propias = emptyList(), conRed = false)
        Spacer(Modifier.height(40.dp))
    }
}

/* Las preguntas y respuestas son los arreglos preguntas y respuestas de strings.xml: las mismas del README de la app. Si cambia una, cambian las dos. */

@Composable
fun Preguntas(vm: AhorradorViewModel) {
    val f = FogonTema.actual
    Column {
        Cabecera(stringResource(R.string.preguntas_frecuentes), vm::cerrar)
        stringArrayResource(R.array.preguntas).zip(stringArrayResource(R.array.respuestas)).forEach { (p, r) ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = f.texto)
                Text(r, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/* =================== Reportar un fallo o sugerir algo =================== */

/**
 * La app no tiene servidor ni internet: el mensaje sale por GitHub (un reporte público en el repo) o
 * por el correo de Fogón, con la versión de la app y de Android agregadas. Nada de la plata de la persona.
 */
@Composable
fun Reporte(vm: AhorradorViewModel) {
    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    var fallo by rememberSaveable { mutableStateOf(true) }
    var texto by rememberSaveable { mutableStateOf("") }
    val datos = "El Ahorrador ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE}"
    val titulo = (if (fallo) "[Bug] " else "[Idea] ") + "El Ahorrador: " + texto.trim().lineSequence().firstOrNull().orEmpty().take(60)
    val cuerpo = texto.trim() + "\n\n---\n" + datos
    fun cod(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    Column {
        Cabecera(stringResource(R.string.reportar_titulo), vm::cerrar)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.reportar_pista), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(fallo, { fallo = true }, { Text(stringResource(R.string.algo_falla)) })
                FilterChip(!fallo, { fallo = false }, { Text(stringResource(R.string.tengo_idea)) })
            }
            OutlinedTextField(
                texto, { if (it.length <= 3000) texto = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
                label = { Text(stringResource(if (fallo) R.string.que_paso else R.string.tu_idea)) },
            )
            Text(stringResource(R.string.se_agrega, datos), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            BotonRiso(stringResource(R.string.enviar_github), { uri.openUri("$URL_CODIGO/issues/new?title=${cod(titulo)}&body=${cod(cuerpo)}") },
                Modifier.fillMaxWidth(), habilitado = texto.isNotBlank() && URL_CODIGO != null)
            Text(stringResource(R.string.enviar_github_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            CORREO_FOGON?.let { correo ->
                BotonSecundario(stringResource(R.string.enviar_correo), { uri.openUri("mailto:$correo?subject=${cod(titulo)}&body=${cod(cuerpo)}") },
                    Modifier.fillMaxWidth(), habilitado = texto.isNotBlank())
                Text(stringResource(R.string.enviar_correo_pista, correo), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
