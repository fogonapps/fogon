package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.BuildConfig
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.EstadoSync
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.WidgetCursoReceptor
import io.github.fogonapps.cajon.datos.ClienteGist
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Tmdb
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
fun Ajustes(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val acciones = LocalAcciones.current
    val sync by vm.sync.collectAsStateWithLifecycle()
    val tmdb by vm.tieneTmdb.collectAsStateWithLifecycle()
    val c = LocalContext.current
    val alcance = rememberCoroutineScope()

    Cabecera(stringResource(R.string.ajustes), vm::cerrar)

    Seccion(stringResource(R.string.tu_nombre))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var nombre by rememberSaveable { mutableStateOf(e.ajustes.nombre) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoTexto(nombre, { nombre = it }, stringResource(R.string.tu_nombre), Modifier.weight(1f))
        }
        if (nombre.trim() != e.ajustes.nombre) BotonSecundario(stringResource(R.string.guardar), { vm.cambiarNombre(nombre) }, Modifier.fillMaxWidth())
    }

    Seccion(stringResource(R.string.buscar_afuera))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilaVidrio({ vm.abrir(Pantalla.ClaveTmdb) }, adentro = true) {
            Ficha(if (tmdb) f.tinta1 else f.linea, Iconos.Pelicula)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.clave_tmdb), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                Text(stringResource(if (tmdb) R.string.clave_tmdb_lista else R.string.clave_tmdb_falta), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        Pista(stringResource(R.string.libros_y_musica_sin_clave), adentro = true)
    }

    Seccion(stringResource(R.string.sync_titulo))
    Column(Modifier.padding(horizontal = 16.dp)) {
        FilaVidrio({ vm.abrir(Pantalla.Sincronizar) }, adentro = true) {
            Ficha(if (sync.estado == EstadoSync.APAGADA) f.linea else f.tinta1, Iconos.Nube)
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (sync.estado == EstadoSync.APAGADA) R.string.sync_apagada else R.string.sync_conectada, sync.usuario ?: ""),
                    style = MaterialTheme.typography.bodyLarge, color = f.texto)
                Text(stringResource(R.string.sync_para_pc), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
    }

    Seccion(stringResource(R.string.opciones))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonRiso(stringResource(RId.string.fogon_ap_titulo), { vm.abrir(Pantalla.Apariencia) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.widget_poner), { alcance.launch { pedirWidget(c, WidgetCursoReceptor::class.java) } }, Modifier.fillMaxWidth())
        Text(stringResource(R.string.idioma), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        SelectorIdioma(vm)
    }

    Seccion(stringResource(R.string.traer_de_otras))
    Pista(stringResource(R.string.traer_de_otras_pista))
    Column(Modifier.padding(16.dp)) {
        if (acciones != null) BotonSecundario(stringResource(R.string.elegir_archivo), acciones.deOtraApp, Modifier.fillMaxWidth())
    }

    Seccion(stringResource(R.string.respaldo))
    Pista(stringResource(R.string.respaldo_pista))
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (acciones != null) {
            BotonSecundario(stringResource(R.string.guardar_respaldo), acciones.exportar, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.recuperar_respaldo), {
                confirmar(vm.tx(R.string.recuperar_pregunta), vm.tx(R.string.recuperar_texto), acciones.importar)
            }, Modifier.fillMaxWidth())
        }
    }
    RespaldoTodoFogon(confirmar, Modifier.padding(bottom = 8.dp))

    Seccion(stringResource(R.string.fogon))
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonSecundario(stringResource(R.string.manifiesto), { vm.abrir(Pantalla.Manifiesto) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.acerca_de), { vm.abrir(Pantalla.AcercaDe) }, Modifier.fillMaxWidth())
    }
}

/** Poner o quitar la clave de TMDB, con los pasos para sacarla. */
@Composable
fun ClaveTmdb(vm: CajonViewModel) {
    val f = FogonTema.actual
    val tiene by vm.tieneTmdb.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var clave by rememberSaveable { mutableStateOf("") }
    Cabecera(stringResource(R.string.clave_tmdb), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tmdb_explica), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        Text(stringResource(R.string.tmdb_pasos), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        BotonSecundario(stringResource(R.string.abrir_tmdb), { uri.openUri(Tmdb.URL_CLAVE) }, Modifier.fillMaxWidth())
        OutlinedTextField(clave, { clave = it.trim().take(400) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.pega_clave)) }, visualTransformation = PasswordVisualTransformation())
        BotonRiso(stringResource(R.string.guardar), { vm.ponerClaveTmdb(clave) }, Modifier.fillMaxWidth(), habilitado = clave.length >= 20)
        if (tiene) BotonSecundario(stringResource(R.string.quitar_clave), { vm.quitarClaveTmdb() }, Modifier.fillMaxWidth(), peligro = true)
        Pista(stringResource(R.string.tmdb_privacidad), adentro = true)
        // TMDB pide su logo junto al aviso (menos destacado que la marca de la app).
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo_tmdb), "TMDB", Modifier.padding(top = 4.dp).height(16.dp))
        Pista(stringResource(R.string.tmdb_aviso), adentro = true)
    }
}

/** Conectar GitHub para sincronizar con el PC (un Gist secreto de la propia cuenta). */
@Composable
fun Sincronizar(vm: CajonViewModel) {
    val f = FogonTema.actual
    val s by vm.sync.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val confirmar = LocalConfirmar.current
    var token by rememberSaveable { mutableStateOf("") }
    Cabecera(stringResource(R.string.sync_titulo), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sync_explica), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        if (s.estado == EstadoSync.APAGADA) {
            Text(stringResource(R.string.sync_pasos), style = MaterialTheme.typography.bodyMedium, color = f.texto)
            BotonSecundario(stringResource(R.string.crear_token), { uri.openUri(ClienteGist.URL_TOKEN) }, Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it.trim().take(300) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.pega_token)) }, visualTransformation = PasswordVisualTransformation())
            BotonRiso(stringResource(R.string.conectar), { vm.conectar(token) }, Modifier.fillMaxWidth(), habilitado = token.length >= 20)
        } else {
            Column(Modifier.fillMaxWidth().vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.sync_conectada, s.usuario ?: ""), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                Text(when (s.estado) {
                    EstadoSync.SINCRONIZANDO -> stringResource(R.string.sync_sincronizando)
                    EstadoSync.SIN_CONEXION -> stringResource(R.string.sync_sin_conexion)
                    EstadoSync.ERROR -> s.mensaje ?: stringResource(R.string.sync_sin_conexion)
                    else -> if (s.ultima > 0) stringResource(R.string.sync_ultima, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(s.ultima))) else ""
                }, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            BotonRiso(stringResource(R.string.sincronizar_ahora), vm::sincronizar, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.desconectar), {
                confirmar(vm.tx(R.string.desconectar_pregunta), vm.tx(R.string.desconectar_texto), vm::desconectar)
            }, Modifier.fillMaxWidth(), peligro = true)
        }
        Pista(stringResource(R.string.sync_privacidad), adentro = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorIdioma(vm: CajonViewModel) {
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
fun AcercaDe(vm: CajonViewModel) {
    val uri = LocalUriHandler.current
    Column {
        Cabecera(stringResource(R.string.acerca_de_corto), vm::cerrar)
        AcercaDeFogon(
            personaje = RId.drawable.personaje_el_cajon,
            nombreApp = stringResource(R.string.app_name),
            nombrePersonaje = stringResource(R.string.app_name),
            version = BuildConfig.VERSION_NAME,
            creditos = stringArrayResource(R.array.creditos).toList(),
            onVerManifiesto = { vm.abrir(Pantalla.Manifiesto) },
            onVerLicencias = { vm.abrir(Pantalla.Licencias) },
            onAbrirEnlace = { uri.openUri(it) },
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
    // TMDB pide su logo junto al aviso (menos destacado que la marca de la app).
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo_tmdb), "TMDB", Modifier.padding(top = 4.dp).height(16.dp))
        }
        Pista(stringResource(R.string.tmdb_aviso))
    }
}

@Composable
fun Licencias(vm: CajonViewModel) {
    Column {
        Cabecera(stringResource(R.string.licencias), vm::cerrar)
        // Las fuentes de datos y la biblioteca de imágenes; lo común lo pone core.
        LicenciasFogon(propias = listOf(
            Licencia("TMDB", stringResource(R.string.lic_tmdb)),
            Licencia("Open Library", stringResource(R.string.lic_open_library)),
            Licencia("MusicBrainz · Cover Art Archive", stringResource(R.string.lic_musicbrainz)),
            Licencia("Wikimedia Commons · Wikidata", stringResource(R.string.lic_wikimedia)),
            Licencia("Coil", stringResource(R.string.lic_coil), "apache-2.0.txt"),
        ))
    }
}
