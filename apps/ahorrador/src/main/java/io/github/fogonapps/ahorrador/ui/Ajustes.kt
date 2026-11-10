package io.github.fogonapps.ahorrador.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.BuildConfig
import io.github.fogonapps.ahorrador.Pantalla
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Sistema
import io.github.fogonapps.identidad.FilaAjuste
import io.github.fogonapps.identidad.Desplegable
import io.github.fogonapps.identidad.SeccionAjustes
import io.github.fogonapps.identidad.RespaldoTodoFogon
import io.github.fogonapps.identidad.AcercaDeFogon
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Idioma
import io.github.fogonapps.identidad.LicenciasFogon
import io.github.fogonapps.identidad.R as RId

/* =================== Ajustes =================== */

@Composable
fun Ajustes(
    vm: AhorradorViewModel,
    e: Estado,
    onExportar: () -> Unit,
    onExportarCsv: () -> Unit,
    onImportar: () -> Unit,
    candadoDisponible: Boolean,
    onImportarExtracto: () -> Unit = {},
) {
    val confirmar = LocalConfirmar.current
    val candado by vm.candado.collectAsState()
    var reparto by remember { mutableStateOf(false) }

    Cabecera(stringResource(R.string.ajustes), vm::cerrar)
    val guia = io.github.fogonapps.identidad.LocalGuia.current
    FilaAjuste(io.github.fogonapps.identidad.IconosFogon.Perfil, stringResource(R.string.cuenta_tu_cuenta), stringResource(R.string.cuenta_tu_cuenta_pista)) { vm.abrir(Pantalla.CuentaRed) }

    SeccionAjustes(stringResource(R.string.aj_como_usas))
    Desplegable(Iconos.Resumen, stringResource(R.string.como_te_organizas), stringResource(R.string.aj_sistema_pista)) {
        Sistema.entries.forEach { s -> TarjetaSistema(s, s == e.sistema) { vm.cambiarSistema(s) } }
        if (e.sistema == Sistema.REGLA_50_30_20) BotonSecundario(stringResource(R.string.cambiar_reparto), { reparto = true }, Modifier.fillMaxWidth())
    }
    FilaAjuste(Iconos.Moneda, stringResource(R.string.cuentas), null) { vm.abrir(Pantalla.Cuentas) }
    FilaAjuste(Iconos.Repetir, stringResource(R.string.pagos_fijos), null) { vm.abrir(Pantalla.Fijos) }
    FilaAjuste(Iconos.Pasar, stringResource(R.string.deudas_titulo), null) { vm.abrir(Pantalla.Deudas) }
    Desplegable(Iconos.Campana, stringResource(R.string.recordatorio), stringResource(R.string.recordatorio_pista)) { AjusteRecordatorio(vm) }
    Desplegable(Iconos.Gasto, stringResource(R.string.gmf), stringResource(R.string.gmf_pista)) {
        io.github.fogonapps.identidad.Interruptor(stringResource(R.string.gmf), stringResource(R.string.gmf_pista), e.gmf, onCambio = vm::cambiarGmf)
    }

    SeccionAjustes(stringResource(R.string.aj_tu_alcancia))
    Desplegable(Iconos.Repetir, stringResource(R.string.respaldo), stringResource(R.string.respaldo_pista)) {
        BotonSecundario(stringResource(R.string.guardar_respaldo), onExportar, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.recuperar_respaldo), {
            confirmar(vm.tx(R.string.recuperar_pregunta), vm.tx(R.string.recuperar_texto), onImportar)
        }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.exportar_csv), onExportarCsv, Modifier.fillMaxWidth())
    }
    FilaAjuste(Iconos.Ingreso, stringResource(R.string.extracto_importar), stringResource(R.string.aj_importar_pista), onImportarExtracto)
    FilaAjuste(Iconos.Campana, stringResource(R.string.banco_titulo), stringResource(R.string.banco_local)) { vm.abrir(Pantalla.Bancos) }

    Desplegable(io.github.fogonapps.identidad.IconosFogon.Chispa, stringResource(R.string.todo_fogon), null) { RespaldoTodoFogon(confirmar) }

    SeccionAjustes(stringResource(R.string.aj_apariencia_idioma))
    FilaAjuste(Iconos.Imagen, stringResource(RId.string.fogon_ap_titulo), stringResource(R.string.aj_apariencia_pista)) { vm.abrir(Pantalla.Apariencia) }
    Desplegable(Iconos.Comentario, stringResource(R.string.idioma), null) { SelectorIdioma(vm) }
    val contexto = LocalContext.current
    val alcanceWidget = androidx.compose.runtime.rememberCoroutineScope()
    FilaAjuste(Iconos.Inicio, stringResource(R.string.widget_poner), null) {
        alcanceWidget.launch { io.github.fogonapps.identidad.pedirWidget(contexto, io.github.fogonapps.ahorrador.WidgetPlataReceptor::class.java) }
    }

    SeccionAjustes(stringResource(R.string.fogon))
    FilaAjuste(Iconos.Estrella, stringResource(R.string.manifiesto), null) { vm.abrir(Pantalla.Manifiesto) }
    FilaAjuste(Iconos.Comentario, stringResource(R.string.preguntas_frecuentes), null) { vm.abrir(Pantalla.Preguntas) }
    FilaAjuste(Iconos.Estrella, stringResource(io.github.fogonapps.identidad.R.string.fogon_guia_ver), stringResource(io.github.fogonapps.identidad.R.string.fogon_guia_ver_pista)) {
        vm.irA(io.github.fogonapps.ahorrador.Pestana.INICIO); guia?.empezar()
    }
    SeccionAjustes(stringResource(R.string.aj_privacidad))
    FilaAjuste(Iconos.Candado, stringResource(R.string.aj_que_sale), null) { vm.abrir(Pantalla.Transparencia) }
    if (candadoDisponible) Desplegable(Iconos.Candado, stringResource(R.string.candado), stringResource(R.string.candado_pista)) {
        io.github.fogonapps.identidad.Interruptor(stringResource(R.string.candado), stringResource(R.string.candado_pista), candado, onCambio = vm::cambiarCandado)
    }
    FilaAjuste(Iconos.Bandera, stringResource(RId.string.fogon_reportar_boton), null) { vm.abrir(Pantalla.Reporte) }
    FilaAjuste(Iconos.Sobres, stringResource(R.string.acerca_de), null) { vm.abrir(Pantalla.AcercaDe) }
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
            quien = io.github.fogonapps.identidad.Quien.AHORRADOR,
            nombreApp = stringResource(R.string.app_name),
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
    Column {
        Cabecera(stringResource(io.github.fogonapps.identidad.R.string.fogon_reportar_titulo), vm::cerrar)
        io.github.fogonapps.identidad.ReporteFogon("La Alcancía", BuildConfig.VERSION_NAME)
        Spacer(Modifier.height(40.dp))
    }
}
