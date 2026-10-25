package io.github.fogonapps.cucha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cucha.BuildConfig
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.Pantalla
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.WidgetCaloriasReceptor
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.cucha.datos.Metas
import io.github.fogonapps.cucha.datos.Perfil
import io.github.fogonapps.identidad.AcercaDeFogon
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Idioma
import io.github.fogonapps.identidad.Licencia
import io.github.fogonapps.identidad.LicenciasFogon
import io.github.fogonapps.identidad.RespaldoTodoFogon
import io.github.fogonapps.identidad.pedirWidget
import kotlinx.coroutines.launch
import io.github.fogonapps.identidad.R as RId

@Composable
fun Perfil(vm: CuchaViewModel, e: Estado, acciones: Acciones) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val forzudo by vm.forzudo.collectAsStateWithLifecycle()
    val c = LocalContext.current
    val alcance = rememberCoroutineScope()

    Cabecera(stringResource(R.string.perfil), vm::cerrar)

    Seccion(stringResource(R.string.tus_datos))
    DatosPersona(vm, e.nombre, e.perfil, pesoDeForzudo = forzudo?.peso != null) { nombre, p -> vm.cambiarNombre(nombre); vm.guardarPerfil(p) }

    Seccion(stringResource(R.string.tus_metas))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dato(stringResource(R.string.calorias), "${e.metas.kcal}", Modifier.weight(1f))
            Dato(stringResource(R.string.proteina), "${e.metas.proteina} g", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dato(stringResource(R.string.carbos), "${e.metas.carbos} g", Modifier.weight(1f))
            Dato(stringResource(R.string.grasa), "${e.metas.grasa} g", Modifier.weight(1f))
        }
        Text(stringResource(if (e.metas.manual) R.string.metas_a_mano else R.string.metas_calculadas), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        BotonSecundario(stringResource(R.string.cambiar_metas), { vm.abrir(Pantalla.Metas) }, Modifier.fillMaxWidth())
    }

    Seccion(stringResource(R.string.forzudo))
    Pista(stringResource(if (forzudo != null) R.string.forzudo_conectado else R.string.forzudo_no_esta))

    Seccion(stringResource(R.string.opciones))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonRiso(stringResource(RId.string.fogon_ap_titulo), { vm.abrir(Pantalla.Apariencia) }, Modifier.fillMaxWidth())
        Interruptor(stringResource(R.string.codigo_barras), stringResource(R.string.codigo_barras_pista), e.codigoBarras, vm::activarCodigoBarras)
        BotonSecundario(stringResource(R.string.widget_poner), { alcance.launch { pedirWidget(c, WidgetCaloriasReceptor::class.java) } }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.importar_recetas), acciones.importarRecetas, Modifier.fillMaxWidth())
        Pista(stringResource(R.string.importar_recetas_pista), adentro = true)
        Text(stringResource(R.string.idioma), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        SelectorIdioma(vm)
    }

    Seccion(stringResource(R.string.respaldo))
    Pista(stringResource(R.string.respaldo_pista))
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonSecundario(stringResource(R.string.guardar_respaldo), acciones.exportar, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.recuperar_respaldo), {
            confirmar(vm.tx(R.string.recuperar_pregunta), vm.tx(R.string.recuperar_texto), acciones.importar)
        }, Modifier.fillMaxWidth())
    }
    RespaldoTodoFogon(confirmar, Modifier.padding(bottom = 8.dp))

    Seccion(stringResource(R.string.fogon))
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonSecundario(stringResource(R.string.manifiesto), { vm.abrir(Pantalla.Manifiesto) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.acerca_de), { vm.abrir(Pantalla.AcercaDe) }, Modifier.fillMaxWidth())
    }
}

/** Nombre, sexo, edad, altura, peso, actividad y objetivo. Se usa en el tutorial y en Perfil. */
@Composable
fun DatosPersona(vm: CuchaViewModel, nombreInicial: String, p: Perfil, pesoDeForzudo: Boolean, textoBoton: String? = null, onGuardar: (String, Perfil) -> Unit) {
    val idioma = vm.idioma()
    var nombre by rememberSaveable { mutableStateOf(nombreInicial) }
    var sexo by rememberSaveable { mutableStateOf(p.sexo) }
    var edad by rememberSaveable { mutableStateOf(p.edad.toString()) }
    var altura by rememberSaveable { mutableStateOf(cifra(p.alturaCm, idioma)) }
    var peso by rememberSaveable { mutableStateOf(cifra(p.pesoKg, idioma)) }
    var actividad by rememberSaveable { mutableStateOf(p.actividad) }
    var objetivo by rememberSaveable { mutableStateOf(p.objetivo) }
    var ritmo by rememberSaveable { mutableStateOf(p.ritmo) }
    val f = FogonTema.actual
    val nuevo = Perfil(sexo, edad.toIntOrNull()?.coerceIn(12, 100) ?: p.edad, numero(altura)?.coerceIn(100.0, 230.0) ?: p.alturaCm,
        numero(peso)?.coerceIn(25.0, 350.0) ?: p.pesoKg, actividad, objetivo, ritmo)

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CampoTexto(nombre, { nombre = it }, stringResource(R.string.tu_nombre))
        Selector(listOf("m" to stringResource(R.string.hombre), "f" to stringResource(R.string.mujer)), sexo, { sexo = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoNumero(edad, { edad = it }, stringResource(R.string.edad), Modifier.weight(1f))
            CampoNumero(altura, { altura = it }, stringResource(R.string.altura_cm), Modifier.weight(1f), decimales = true)
            CampoNumero(peso, { peso = it }, stringResource(R.string.peso_kg), Modifier.weight(1f), decimales = true)
        }
        if (pesoDeForzudo) Text(stringResource(R.string.peso_lo_trae_forzudo), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        Text(stringResource(R.string.actividad), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        Selector(stringArrayResource(R.array.actividades).mapIndexed { i, t -> i to t }, actividad, { actividad = it })
        Text(stringResource(R.string.objetivo), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        Selector(listOf(-1 to stringResource(R.string.bajar), 0 to stringResource(R.string.mantener), 1 to stringResource(R.string.subir)), objetivo, { objetivo = it })
        if (objetivo != 0) Selector(listOf(0.25 to stringResource(R.string.ritmo_kg, cifra(0.25, idioma, 2)), 0.5 to stringResource(R.string.ritmo_kg, cifra(0.5, idioma))), ritmo, { ritmo = it })
        val metas = vm.L.metasDe(nuevo)
        Text(stringResource(R.string.metas_previa, metas.kcal, metas.proteina), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        BotonRiso(textoBoton ?: stringResource(R.string.guardar), { onGuardar(nombre, nuevo) }, Modifier.fillMaxWidth())
    }
}

/** Las metas a mano (por ejemplo, las de un plan con nutricionista), o volver a las calculadas. */
@Composable
fun EditarMetas(vm: CuchaViewModel, e: Estado) {
    val m = e.metas
    var kcal by rememberSaveable { mutableStateOf(m.kcal.toString()) }
    var prot by rememberSaveable { mutableStateOf(m.proteina.toString()) }
    var carb by rememberSaveable { mutableStateOf(m.carbos.toString()) }
    var gras by rememberSaveable { mutableStateOf(m.grasa.toString()) }
    var agua by rememberSaveable { mutableStateOf(m.aguaMl.toString()) }
    val f = FogonTema.actual

    Cabecera(stringResource(R.string.tus_metas), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Pista(stringResource(R.string.metas_pista), adentro = true)
        CampoNumero(kcal, { kcal = it }, stringResource(R.string.calorias), Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoNumero(prot, { prot = it }, stringResource(R.string.proteina_campo), Modifier.weight(1f))
            CampoNumero(carb, { carb = it }, stringResource(R.string.carbos_campo), Modifier.weight(1f))
            CampoNumero(gras, { gras = it }, stringResource(R.string.grasa_campo), Modifier.weight(1f))
        }
        val suma = (prot.toIntOrNull() ?: 0) * 4 + (carb.toIntOrNull() ?: 0) * 4 + (gras.toIntOrNull() ?: 0) * 9
        Text(stringResource(R.string.metas_suma, suma), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        CampoNumero(agua, { agua = it }, stringResource(R.string.agua_ml), Modifier.fillMaxWidth())
        BotonRiso(stringResource(R.string.guardar), {
            vm.guardarMetas(Metas(
                (kcal.toIntOrNull() ?: m.kcal).coerceIn(800, 8000), (prot.toIntOrNull() ?: m.proteina).coerceIn(0, 600),
                (carb.toIntOrNull() ?: m.carbos).coerceIn(0, 1500), (gras.toIntOrNull() ?: m.grasa).coerceIn(0, 500),
                (agua.toIntOrNull() ?: m.aguaMl).coerceIn(500, 8000), manual = true,
            ))
            vm.cerrar()
        }, Modifier.fillMaxWidth())
        if (m.manual) BotonSecundario(stringResource(R.string.metas_volver_calculo), { vm.metasAutomaticas(); vm.cerrar() }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Interruptor(titulo: String, pista: String, valor: Boolean, onCambio: (Boolean) -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().vidrio().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto)
            Text(pista, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        Switch(valor, onCambio)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorIdioma(vm: CuchaViewModel) {
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

@Composable
fun AcercaDe(vm: CuchaViewModel) {
    val uri = LocalUriHandler.current
    Column {
        Cabecera(stringResource(R.string.acerca_de_corto), vm::cerrar)
        AcercaDeFogon(
            personaje = RId.drawable.personaje_la_cucha,
            nombreApp = stringResource(R.string.app_name),
            nombrePersonaje = stringResource(R.string.app_name),
            version = BuildConfig.VERSION_NAME,
            creditos = stringArrayResource(R.array.creditos).toList(),
            onVerManifiesto = { vm.abrir(Pantalla.Manifiesto) },
            onVerLicencias = { vm.abrir(Pantalla.Licencias) },
            onAbrirEnlace = { uri.openUri(it) },
            avisoSalud = stringResource(R.string.aviso_salud),
            avisoTitulo = stringResource(R.string.aviso_salud_titulo),
        )
    }
}

@Composable
fun Licencias(vm: CuchaViewModel) {
    Column {
        Cabecera(stringResource(R.string.licencias), vm::cerrar)
        // Los datos de alimentos (USDA, Open Food Facts) y el lector de códigos de barras; lo común lo pone core.
        LicenciasFogon(propias = listOf(
            Licencia("USDA FoodData Central", stringResource(R.string.lic_usda)),
            Licencia("Open Food Facts", stringResource(R.string.lic_off)),
            Licencia("ZXing · zxing-android-embedded", stringResource(R.string.lic_zxing), "apache-2.0.txt"),
        ))
    }
}
