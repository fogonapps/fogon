package io.github.fogonapps.cucha.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.Pantalla
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.WidgetCaloriasReceptor
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.identidad.*
import kotlinx.coroutines.launch
import io.github.fogonapps.identidad.R as RId

@Composable
fun Ajustes(vm: CuchaViewModel, e: Estado, acciones: Acciones) {
    val confirmar = LocalConfirmar.current
    val forzudo by vm.forzudo.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    Cabecera(stringResource(R.string.ajustes), vm::cerrar)
    FilaAjuste(IconosFogon.Perfil, stringResource(R.string.cuenta_tu_cuenta), stringResource(R.string.cuenta_tu_cuenta_pista)) { vm.abrir(Pantalla.Cuenta) }
    SeccionAjustes(stringResource(R.string.aj_como_usas))
    Desplegable(IconosFogon.Perfil, stringResource(R.string.tus_datos), null) {
        DatosPersona(vm, e.nombre, e.perfil, forzudo?.peso != null) { nombre, perfil ->
            vm.cambiarNombre(nombre)
            vm.guardarPerfil(perfil)
        }
        Pista(stringResource(if (forzudo != null) R.string.forzudo_conectado else R.string.forzudo_no_esta), adentro = true)
    }
    PerfilForzudo(vm)
    FilaAjuste(Iconos.Progreso, stringResource(R.string.tus_metas), stringResource(if (e.metas.manual) R.string.metas_a_mano else R.string.metas_calculadas)) { vm.abrir(Pantalla.Metas) }
    Desplegable(Iconos.Codigo, stringResource(R.string.opciones), null) {
        Interruptor(stringResource(R.string.codigo_barras), stringResource(R.string.codigo_barras_pista), e.codigoBarras, vm::activarCodigoBarras)
    }
    SeccionAjustes(stringResource(R.string.aj_tu_cocina))
    Desplegable(IconosFogon.Repetir, stringResource(R.string.respaldo), stringResource(R.string.respaldo_pista)) {
        FilaAjuste(IconosFogon.Chulo, stringResource(R.string.guardar_respaldo), null, acciones.exportar)
        FilaAjuste(IconosFogon.Repetir, stringResource(R.string.recuperar_respaldo), null) {
            confirmar(vm.tx(R.string.recuperar_pregunta), vm.tx(R.string.recuperar_texto), acciones.importar)
        }
    }
    FilaAjuste(Iconos.Recetas, stringResource(R.string.importar_recetas), stringResource(R.string.importar_recetas_pista), acciones.importarRecetas)
    Desplegable(IconosFogon.Chispa, stringResource(R.string.aj_todo_fogon), null) { RespaldoTodoFogon(confirmar) }
    SeccionAjustes(stringResource(R.string.aj_apariencia_idioma))
    FilaAjuste(IconosFogon.Imagen, stringResource(RId.string.fogon_ap_titulo), null) { vm.abrir(Pantalla.Apariencia) }
    Desplegable(IconosFogon.Comentario, stringResource(R.string.idioma), null) { SelectorIdioma(vm) }
    FilaAjuste(IconosFogon.Inicio, stringResource(R.string.widget_poner), null) { alcance.launch { pedirWidget(contexto, WidgetCaloriasReceptor::class.java) } }
    SeccionAjustes(stringResource(R.string.fogon))
    FilaAjuste(IconosFogon.Estrella, stringResource(R.string.manifiesto), null) { vm.abrir(Pantalla.Manifiesto) }
    SeccionAjustes(stringResource(R.string.aj_privacidad))
    FilaAjuste(IconosFogon.Candado, stringResource(R.string.aj_que_sale), null) { vm.abrir(Pantalla.Transparencia) }
    FilaAjuste(IconosFogon.Bandera, stringResource(RId.string.fogon_reportar_boton), null) { vm.abrir(Pantalla.Reporte) }
    FilaAjuste(Iconos.Sobres, stringResource(R.string.acerca_de), null) { vm.abrir(Pantalla.AcercaDe) }
}

/** Si El Forzudo tiene varios perfiles y ninguno es claramente el de esta persona, se elige a mano (nunca por el número). */
@Composable
private fun PerfilForzudo(vm: CuchaViewModel) {
    val lista by vm.perfilesForzudo.collectAsStateWithLifecycle()
    val enlace by vm.enlaceForzudo.collectAsStateWithLifecycle()
    val perfiles = lista ?: return
    if (perfiles.size < 2 && enlace !is io.github.fogonapps.cucha.datos.EnlaceForzudo.Elegir) return
    val elegido = (enlace as? io.github.fogonapps.cucha.datos.EnlaceForzudo.Perfil)?.id
        ?: if (enlace == io.github.fogonapps.cucha.datos.EnlaceForzudo.Ninguno) io.github.fogonapps.cucha.datos.FORZUDO_NINGUNO else null
    Desplegable(Iconos.Progreso, stringResource(R.string.forzudo_perfil_titulo), perfiles.firstOrNull { it.id == elegido }?.let { nombrePerfil(it) }) {
        Pista(stringResource(R.string.forzudo_perfil_pista), adentro = true)
        Selector(perfiles.map { it.id to nombrePerfil(it) } + (io.github.fogonapps.cucha.datos.FORZUDO_NINGUNO to stringResource(R.string.forzudo_ninguno)),
            elegido, { it?.let(vm::elegirPerfilForzudo) }, Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun nombrePerfil(p: PerfilDeApp) = listOf(p.apodo, p.usuario.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty())
    .filter { it.isNotBlank() }.joinToString(" ").ifBlank { stringResource(R.string.forzudo_perfil_sin_nombre, p.id) }
