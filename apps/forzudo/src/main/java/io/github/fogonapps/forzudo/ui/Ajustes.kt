package io.github.fogonapps.forzudo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fogonapps.forzudo.*
import io.github.fogonapps.forzudo.R
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.identidad.*
import kotlinx.coroutines.launch

@Composable
fun Ajustes(vm: GymViewModel, e: Estado, sync: Sync, onExportar: () -> Unit, onImportar: () -> Unit) {
    val confirmar = LocalConfirmar.current
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    Cabecera(stringResource(R.string.ajustes), vm::cerrar)
    SeccionAjustes(stringResource(io.github.fogonapps.social.ui.R.string.social_red_tu_cuenta))
    FilaAjuste(IconosFogon.Perfil, stringResource(io.github.fogonapps.social.ui.R.string.social_red_tu_cuenta), null) { vm.abrir(Pantalla.Cuenta) }
    SeccionAjustes(stringResource(R.string.ajustes_uso))
    io.github.fogonapps.social.ui.FilasRedEnAjustes(interfazCuenta(vm))
    Desplegable(IconosFogon.Reloj, stringResource(R.string.ajustes_entrenamiento), null) {
        Text(stringResource(R.string.ajustes_entrenamiento_pista), color = FogonTema.actual.texto)
        BotonSecundario(stringResource(R.string.pestana_entrenar), { vm.cerrar(); vm.cerrar(); vm.irA(Pestana.ENTRENAR) })
    }
    FilaAjuste(IconosFogon.Campana, stringResource(R.string.ajustes_avisos), null) {
        contexto.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, contexto.packageName))
    }
    FilaAjuste(IconosFogon.Lista, stringResource(R.string.programa), null) { vm.abrir(Pantalla.Programas) }
    FilaAjuste(IconosFogon.Comentario, stringResource(R.string.ver_tutorial), null) { vm.verTutorial() }
    val guia = io.github.fogonapps.identidad.LocalGuia.current
    FilaAjuste(IconosFogon.Chispa, stringResource(io.github.fogonapps.identidad.R.string.fogon_guia_ver), stringResource(io.github.fogonapps.identidad.R.string.fogon_guia_ver_pista)) { vm.irA(Pestana.INICIO); guia?.empezar() }
    SeccionAjustes(stringResource(R.string.ajustes_cuaderno))
    Desplegable(IconosFogon.Repetir, stringResource(R.string.sync_titulo), null) { CajaSync(vm, sync, confirmar) }
    Desplegable(IconosFogon.Chulo, stringResource(R.string.respaldo_titulo), stringResource(R.string.pista_respaldo)) {
        FilaAjuste(IconosFogon.Lista, stringResource(R.string.exportar), null, onExportar)
        FilaAjuste(IconosFogon.Lista, stringResource(R.string.importar), null, onImportar)
        RespaldoTodoFogon(confirmar)
    }
    SeccionAjustes(stringResource(R.string.ajustes_apariencia_idioma))
    FilaAjuste(IconosFogon.Imagen, stringResource(io.github.fogonapps.identidad.R.string.fogon_ap_titulo), null) { vm.abrir(Pantalla.Apariencia) }
    Desplegable(IconosFogon.Comentario, stringResource(R.string.idioma), null) { SelectorIdioma(vm) }
    FilaAjuste(IconosFogon.Inicio, stringResource(R.string.widget_poner), null) {
        alcance.launch { pedirWidget(contexto, WidgetRachaReceptor::class.java) }
    }
    SeccionAjustes(stringResource(R.string.ajustes_fogon))
    FilaAjuste(IconosFogon.Estrella, stringResource(R.string.manifiesto_fogon), null) { vm.abrir(Pantalla.Manifiesto) }
    FilaAjuste(IconosFogon.Comentario, stringResource(R.string.preguntas_frecuentes), null) { vm.abrir(Pantalla.Preguntas) }
    SeccionAjustes(stringResource(R.string.ajustes_privacidad))
    io.github.fogonapps.social.ui.FilasPrivacidadRed()
    FilaAjuste(IconosFogon.Candado, stringResource(R.string.ajustes_sale), null) { vm.abrir(Pantalla.Transparencia) }
    FilaAjuste(IconosFogon.Bandera, stringResource(io.github.fogonapps.identidad.R.string.fogon_reportar_boton), null) { vm.abrir(Pantalla.Reporte) }
    FilaAjuste(IconoSobres, stringResource(R.string.acerca_de), null) { vm.abrir(Pantalla.AcercaDe) }
    Spacer(Modifier.height(24.dp))
}

// El catálogo común aún no incluye sobres ni engranaje.
internal val IconoSobres = dibujo("sobres", trazo("M3 6h18v14H3zM3 6l9 7 9-7"))
internal val IconoAjustes = dibujo("ajustes", trazo("M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1zM12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z"))
