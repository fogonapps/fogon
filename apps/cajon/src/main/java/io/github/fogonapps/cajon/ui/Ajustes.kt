package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.clickable
import io.github.fogonapps.identidad.tarjetaFogon
import io.github.fogonapps.identidad.IconoRiso
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
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
import io.github.fogonapps.identidad.SeccionAjustes
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
    val rutaPolitica = stringResource(R.string.ruta_politica)
    val rutaTerminos = stringResource(R.string.ruta_terminos)
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val acciones = LocalAcciones.current
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val c = LocalContext.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val alcance = rememberCoroutineScope()

    Cabecera(stringResource(R.string.ajustes), vm::cerrar)

    // Tu cuenta: lo primero.
    SeccionAjustes(stringResource(R.string.aj_tu_cuenta))
    val u = usuario
    val cuentaRed by vm.red.cuenta.collectAsStateWithLifecycle()
    if (!cuentaRed?.permisos.isNullOrEmpty()) FilaAjuste(Iconos.Bandera, stringResource(R.string.mod_titulo), stringResource(R.string.mod_pista)) { vm.abrir(Pantalla.Moderacion) }
    if (u == null) FilaAjuste(Iconos.Persona, stringResource(R.string.red_crear_cuenta), null) { vm.abrir(Pantalla.CuentaRed) }
    else {
        FilaAjuste(Iconos.Persona, stringResource(R.string.aj_cuenta_red), stringResource(R.string.aj_cuenta_red_pista, u)) { vm.abrir(Pantalla.CuentaRed) }
        FilaAjuste(Iconos.Guardar, stringResource(R.string.parte_guardadas), null) { vm.abrir(Pantalla.GuardadosRed) }
        FilaAjuste(Iconos.Nube, stringResource(R.string.nube_titulo), stringResource(if (vm.hayLlaveCajon()) R.string.nube_pista_activo else R.string.nube_pista)) { vm.abrir(Pantalla.CajonEnCuenta) }
    }
    // Cambiar de cuenta (o agregar otra) va aquí, con lo de la cuenta, y no en el perfil.
    SelectorPerfiles(vm)

    SeccionAjustes(stringResource(R.string.aj_como_usas))
    FilaAjuste(Iconos.Campana, stringResource(R.string.aj_notificaciones), stringResource(R.string.aj_notificaciones_pista)) { vm.abrir(Pantalla.AjustesAvisos) }
    if (u != null) {
        FilaAjuste(Iconos.Cerrar, stringResource(R.string.red_bloqueadas), null) { vm.abrir(Pantalla.BloqueadosRed) }
    }
    FilaAjuste(Iconos.Bandera, stringResource(R.string.aj_palabras_ocultas), stringResource(R.string.aj_palabras_ocultas_pista)) { vm.abrir(Pantalla.PalabrasOcultas) }
    FilaAjuste(Iconos.Corazon, stringResource(R.string.tus_gustos), stringResource(R.string.aj_gustos_pista)) { vm.abrir(Pantalla.TusGustos) }

    SeccionAjustes(stringResource(R.string.aj_tu_cajon))
    Desplegable(Iconos.Disco, stringResource(R.string.musica_que_escuchas), stringResource(R.string.aj_musica_pista)) { MusicaQueEscuchas(vm) }
    // Tus datos son tuyos: una copia en un archivo abierto (JSON), cuando quieras.
    if (acciones != null) FilaAjuste(Iconos.Compartir, stringResource(R.string.descargar_copia), stringResource(R.string.descargar_copia_pista)) { acciones.exportar() }
    Desplegable(Iconos.Repetir, stringResource(R.string.traer_de_otras), stringResource(R.string.aj_traer_pista)) {
        ImportacionLastfm(vm)
        Pista(stringResource(R.string.traer_de_otras_pista), adentro = true)
        if (acciones != null) BotonSecundario(stringResource(R.string.elegir_archivo), acciones.deOtraApp, Modifier.fillMaxWidth())
    }
    // Las portadas de lo que llegó sin fuente se buscan solas; aquí solo se ve cómo va.
    val completando by vm.completando.collectAsStateWithLifecycle()
    completando?.let { k ->
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator({ k.hechas / k.total.toFloat() }, Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
            Text(stringResource(R.string.portadas_avance, k.hechas, k.total, k.encontradas), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
    }

    SeccionAjustes(stringResource(R.string.aj_apariencia_idioma))
    FilaAjuste(Iconos.Imagen, stringResource(RId.string.fogon_ap_titulo), stringResource(R.string.aj_apariencia_pista)) { vm.abrir(Pantalla.Apariencia) }
    Desplegable(Iconos.Comentario, stringResource(R.string.idioma), null) { SelectorIdioma(vm) }
    Desplegable(Iconos.Lista, stringResource(R.string.aj_orden_pestanas), null) { OrdenPestanas(vm, e) }
    FilaAjuste(Iconos.Cajon, stringResource(R.string.widget_poner), null) { alcance.launch { pedirWidget(c, WidgetCursoReceptor::class.java) } }

    SeccionAjustes(stringResource(R.string.fogon))
    FilaAjuste(Iconos.Estrella, stringResource(R.string.manifiesto), null) { vm.abrir(Pantalla.Manifiesto) }
    val guia = io.github.fogonapps.identidad.LocalGuia.current
    FilaAjuste(Iconos.Estrella, stringResource(io.github.fogonapps.identidad.R.string.fogon_guia_ver), stringResource(io.github.fogonapps.identidad.R.string.fogon_guia_ver_pista)) { vm.irA(io.github.fogonapps.cajon.Pestana.INICIO); guia?.empezar() }
    SeccionAjustes(stringResource(R.string.privacidad_grupo))
    FilaAjuste(Iconos.Candado, stringResource(R.string.privacidad_titulo), null) { vm.abrir(Pantalla.Privacidad) }
    FilaAjuste(Iconos.Candado, stringResource(R.string.red_politica), null) { uri.openUri(io.github.fogonapps.cajon.datos.ClienteSocial.SERVIDOR + rutaPolitica) }
    FilaAjuste(Iconos.Lista, stringResource(R.string.red_terminos), null) { uri.openUri(io.github.fogonapps.cajon.datos.ClienteSocial.SERVIDOR + rutaTerminos) }
    FilaAjuste(Iconos.Bandera, stringResource(io.github.fogonapps.identidad.R.string.fogon_reportar_boton), null) { vm.abrir(Pantalla.Reporte) }
    FilaAjuste(Iconos.Cajon, stringResource(R.string.acerca_de), null) { vm.abrir(Pantalla.AcercaDe) }
}

/** El orden de las pestañas de abajo: cada una con flechas para moverla. */
@Composable
private fun OrdenPestanas(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val lista = io.github.fogonapps.cajon.Pestana.visibles(e.ajustes.barra, e.ajustes.ordenPestanas)
    Text(stringResource(R.string.barra_minimo), color = f.textoSuave)
    io.github.fogonapps.cajon.Pestana.entries.forEach { p ->
        val visible = p in lista
        io.github.fogonapps.identidad.Interruptor(stringResource(textoPestana(p)), "", visible,
            habilitado = p != io.github.fogonapps.cajon.Pestana.INICIO && (!visible || lista.size > 3)) { vm.mostrarPestana(p, it) }
    }
    BotonSecundario(stringResource(R.string.barra_restablecer), onClick = { vm.restablecerBarra() })
    lista.forEachIndexed { i, p ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoRiso(iconoPestana(p), null, tamano = 20.dp, tinta = f.acentoTexto)
            Text(stringResource(textoPestana(p)), color = f.texto, modifier = Modifier.weight(1f))
            if (i > 0) BotonIcono(Iconos.Arriba, "${stringResource(R.string.mover_antes)} ${stringResource(textoPestana(p))}") { vm.moverPestana(p, -1) }
            else Spacer(Modifier.size(40.dp))
            if (i < lista.lastIndex) BotonIcono(Iconos.Abajo, "${stringResource(R.string.mover_despues)} ${stringResource(textoPestana(p))}") { vm.moverPestana(p, 1) }
            else Spacer(Modifier.size(40.dp))
        }
    }
}

@Composable
fun FilaAjuste(icono: io.github.fogonapps.identidad.Dibujo, titulo: String, pista: String?, onClick: () -> Unit) =
    io.github.fogonapps.identidad.FilaAjuste(icono, titulo, pista, onClick)

@Composable
fun Desplegable(icono: io.github.fogonapps.identidad.Dibujo, titulo: String, pista: String?, contenido: @Composable () -> Unit) =
    io.github.fogonapps.identidad.Desplegable(icono, titulo, pista, contenido)

/** Reportar un fallo, algo que falta o una idea: por GitHub o por correo, con la versión de la app. */
@Composable
fun Reporte(vm: CajonViewModel) {
    Column {
        Cabecera(stringResource(io.github.fogonapps.identidad.R.string.fogon_reportar_titulo), vm::cerrar)
        io.github.fogonapps.identidad.ReporteFogon("El Cajón", BuildConfig.VERSION_NAME)
        androidx.compose.foundation.layout.Spacer(Modifier.height(40.dp))
    }
}

/**
 * Conectar Spotify paso a paso: crear la app propia en el panel de Spotify, poner la dirección de vuelta, pegar el
 * Client ID y aceptar en el navegador. Si algo falla, dice qué paso revisar ([CajonViewModel.spotifyPaso]).
 */
@Composable
fun ConectarSpotify(vm: CajonViewModel) {
    val f = FogonTema.actual
    val c = LocalContext.current
    val uri = LocalUriHandler.current
    val paso by vm.spotifyPaso.collectAsStateWithLifecycle()
    var cliente by rememberSaveable { mutableStateOf(vm.clienteSpotify()) }
    var copiada by remember { mutableStateOf(false) }
    var noPego by remember { mutableStateOf(false) }
    val portapapeles = remember { c.getSystemService(android.content.ClipboardManager::class.java) }
    val valido = io.github.fogonapps.cajon.datos.Spotify.FORMA_CLIENTE.matches(cliente)
    androidx.activity.compose.BackHandler(paso == CajonViewModel.SPOTIFY_ESPERANDO) { vm.cancelarSpotify() }

    Cabecera(stringResource(R.string.spotify_conectar), { vm.cancelarSpotify(); vm.cerrar() })
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.spotify_explica), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        Pista(stringResource(R.string.spotify_limites), adentro = true)

        PasoSpotify(1, stringResource(R.string.spotify_paso1), stringResource(R.string.spotify_paso1_pista)) {
            BotonSecundario(stringResource(R.string.spotify_crear_app), { runCatching { uri.openUri(io.github.fogonapps.cajon.datos.Spotify.CREAR) } }, Modifier.fillMaxWidth())
        }
        PasoSpotify(2, stringResource(R.string.spotify_paso2), stringResource(R.string.spotify_paso2_pista)) {
            Text(io.github.fogonapps.cajon.datos.Spotify.VUELTA, style = MaterialTheme.typography.bodyLarge, color = f.texto,
                modifier = Modifier.fillMaxWidth().vidrio().padding(12.dp))
            BotonSecundario(stringResource(if (copiada) R.string.spotify_copiada else R.string.spotify_copiar), {
                runCatching { portapapeles?.setPrimaryClip(android.content.ClipData.newPlainText("Redirect URI", io.github.fogonapps.cajon.datos.Spotify.VUELTA)) }
                copiada = true
            }, Modifier.fillMaxWidth())
        }
        PasoSpotify(3, stringResource(R.string.spotify_paso3), stringResource(R.string.spotify_paso3_pista)) {
            CampoTexto(cliente, { cliente = it.trim(); noPego = false }, stringResource(R.string.spotify_cliente), Modifier.fillMaxWidth(), max = 64)
            BotonSecundario(stringResource(R.string.spotify_pegar), {
                val texto = runCatching { portapapeles?.primaryClip?.getItemAt(0)?.coerceToText(c)?.toString() }.getOrNull().orEmpty()
                // Solo se toma si parece un Client ID (por si se copió otra cosa).
                io.github.fogonapps.cajon.datos.Spotify.FORMA_CLIENTE.find(texto)?.let { cliente = it.value; noPego = false } ?: run { noPego = true }
            }, Modifier.fillMaxWidth())
            if (noPego || (cliente.isNotEmpty() && !valido)) Pista(stringResource(R.string.spotify_no_es_cliente), adentro = true)
        }
        PasoSpotify(4, stringResource(R.string.spotify_paso4), stringResource(R.string.spotify_paso4_pista)) {
            if (paso == CajonViewModel.SPOTIFY_ESPERANDO) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = f.tinta1, strokeWidth = 2.dp)
                    Text(stringResource(R.string.spotify_esperando), style = MaterialTheme.typography.bodyMedium, color = f.texto)
                }
                var tarda by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(30_000); tarda = true }
                if (tarda) {
                    Pista(stringResource(R.string.spotify_esperando_pista), adentro = true)
                    Text(io.github.fogonapps.cajon.datos.Spotify.VUELTA, style = MaterialTheme.typography.bodyLarge, color = f.texto,
                        modifier = Modifier.fillMaxWidth().vidrio().padding(12.dp))
                }
                BotonSecundario(stringResource(R.string.cancelar), { vm.cancelarSpotify() }, Modifier.fillMaxWidth())
            } else {
                BotonRiso(stringResource(R.string.spotify_conectar), { vm.conectarSpotify(cliente) { u -> runCatching { uri.openUri(u) } } },
                    Modifier.fillMaxWidth(), habilitado = valido)
            }
            val error = when (paso) {
                CajonViewModel.SPOTIFY_CANCELADO -> R.string.spotify_err_cancelado
                CajonViewModel.SPOTIFY_TIEMPO -> R.string.spotify_err_tiempo
                CajonViewModel.SPOTIFY_CLIENTE -> R.string.spotify_err_cliente
                CajonViewModel.SPOTIFY_PERMISO -> R.string.spotify_err_permiso
                CajonViewModel.SPOTIFY_RED -> R.string.spotify_err_red
                CajonViewModel.SPOTIFY_ERROR -> R.string.spotify_err_vuelta
                else -> null
            }
            if (error != null) Text(stringResource(error, vm.spotifyErrorVuelta), style = MaterialTheme.typography.bodyMedium, color = f.tinta1,
                modifier = Modifier.fillMaxWidth().vidrio().padding(12.dp))
        }
        Pista(stringResource(R.string.spotify_privacidad), adentro = true)
    }
    Espacio()
}

/** Un paso numerado de [ConectarSpotify]. */
@Composable
private fun PasoSpotify(n: Int, titulo: String, pista: String, contenido: @Composable () -> Unit) {
    val f = FogonTema.actual
    Column(Modifier.fillMaxWidth().vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$n", style = MaterialTheme.typography.titleLarge, color = f.tinta1)
            Text(titulo, style = MaterialTheme.typography.titleSmall, color = f.texto)
        }
        Text(pista, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        contenido()
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
    Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Cabecera(stringResource(R.string.acerca_de_corto), vm::cerrar)
        AcercaDeFogon(
            quien = io.github.fogonapps.identidad.Quien.CAJON,
            nombreApp = stringResource(R.string.app_name),
            version = BuildConfig.VERSION_NAME,
            creditos = stringArrayResource(R.array.creditos).toList() + stringResource(R.string.lastfm_credito),
            onVerManifiesto = { vm.abrir(Pantalla.Manifiesto) },
            onVerLicencias = { vm.abrir(Pantalla.Licencias) },
            onAbrirEnlace = { uri.openUri(it) },
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
    // TMDB pide su logo junto al aviso (menos destacado que la marca de la app).
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo_tmdb), "TMDB", Modifier.padding(top = 4.dp).height(16.dp))
        }
        Pista(stringResource(R.string.beta_aviso))
        BotonSecundario(stringResource(R.string.beta_problemas), { uri.openUri("https://github.com/fogonapps/el-cajon#problemas-conocidos") }, Modifier.padding(horizontal = 16.dp))
        Pista(stringResource(R.string.tmdb_aviso))
        BotonSecundario(stringResource(R.string.lastfm_credito), { uri.openUri("https://www.last.fm") }, Modifier.padding(horizontal = 16.dp))
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
            Licencia("AndroidX Media3", stringResource(R.string.lic_media3), "apache-2.0.txt"),
            Licencia("Tink", stringResource(R.string.lic_tink), "apache-2.0.txt"),
        ))
    }
}


/**
 * Lo que se escucha: anotar lo que suena en cualquier app (con el acceso a notificaciones), agregar solos los
 * álbumes, de qué apps, y Spotify (opcional, con la app de Spotify de la persona).
 */
@Composable
fun MusicaQueEscuchas(vm: CajonViewModel) {
    val f = FogonTema.actual
    val c = LocalContext.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val oidos by vm.oidos.collectAsStateWithLifecycle()
    val conectado by vm.spotify.collectAsStateWithLifecycle()
    // Se vuelve a mirar el permiso al volver de los ajustes del teléfono.
    var revisar by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(ciclo) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) revisar++ }
        ciclo.lifecycle.addObserver(o); onDispose { ciclo.lifecycle.removeObserver(o) }
    }
    var anotar by remember { mutableStateOf(io.github.fogonapps.cajon.EscuchaMusica.activado(c)) }
    var solos by remember { mutableStateOf(io.github.fogonapps.cajon.EscuchaMusica.agregarSolos(c)) }
    val permiso = remember(revisar) { io.github.fogonapps.cajon.EscuchaMusica.conPermiso(c) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilaInterruptorCajon(stringResource(R.string.anotar_escuchas), anotar, stringResource(R.string.anotar_escuchas_pista)) { anotar = it; vm.anotarEscuchas(it) }
        }
        if (anotar && !permiso) Column(Modifier.fillMaxWidth().vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dar_acceso_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            BotonRiso(stringResource(R.string.dar_acceso), {
                runCatching { c.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }, Modifier.fillMaxWidth())
        }
        FilaInterruptorCajon(stringResource(R.string.agregar_solos), solos, stringResource(R.string.agregar_solos_pista)) { solos = it; vm.agregarSolos(it) }
        // De qué apps se anota (las que ya sonaron).
        if (oidos.apps.isNotEmpty()) {
            Text(stringResource(R.string.apps_que_suenan), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            oidos.apps.entries.sortedBy { it.value }.forEach { (paquete, nombre) ->
                FilaInterruptorCajon(nombre, paquete !in oidos.sinAnotar) { vm.anotarDeApp(paquete, it) }
            }
        }

        // Spotify: con la app de Spotify de la persona (Premium), opcional; se conecta paso a paso en su pantalla.
        Column(Modifier.fillMaxWidth().vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.spotify_titulo), style = MaterialTheme.typography.titleSmall, color = f.texto)
            if (conectado) {
                Text(stringResource(R.string.spotify_conectado), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                val revisar by vm.spotifyRevisar.collectAsStateWithLifecycle()
                if (revisar) Text(stringResource(R.string.aviso_spotify_permiso), style = MaterialTheme.typography.bodyMedium, color = f.tinta1,
                    modifier = Modifier.fillMaxWidth().vidrio().padding(12.dp))
                BotonRiso(stringResource(R.string.spotify_traer), { vm.traerSpotify(todo = true) }, Modifier.fillMaxWidth())
                BotonSecundario(stringResource(R.string.spotify_desconectar), { vm.desconectarSpotify() }, Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.spotify_resumen), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                BotonRiso(stringResource(R.string.spotify_conectar), { vm.abrir(Pantalla.ConectarSpotify) }, Modifier.fillMaxWidth())
            }
            Text(stringResource(R.string.spotify_historial_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
    }
}

@Composable
private fun ImportacionLastfm(vm: CajonViewModel) {
    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    val resumen by vm.lastfmResumen.collectAsStateWithLifecycle()
    val avance by vm.lastfmAvance.collectAsStateWithLifecycle()
    var usuario by rememberSaveable { mutableStateOf("") }
    var fecha by rememberSaveable { mutableStateOf("") }
    var todo by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.lastfm_titulo), style = MaterialTheme.typography.titleMedium, color = f.texto)
        Pista(stringResource(R.string.lastfm_pista))
        OutlinedTextField(usuario, { usuario = it; vm.editarLastfm() }, label = { Text(stringResource(R.string.lastfm_usuario)) },
            enabled = avance == null, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (avance == null) {
            if (usuario.isNotBlank()) BotonSecundario(stringResource(R.string.lastfm_revisar), { vm.revisarLastfm(usuario) })
            resumen?.let { r ->
                Text(stringResource(R.string.lastfm_resumen, r.escuchas, r.desde), color = f.texto)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(todo, { todo = true }, label = { Text(stringResource(R.string.lastfm_todo)) })
                    FilterChip(!todo, { todo = false }, label = { Text(stringResource(R.string.lastfm_desde)) })
                }
                if (!todo) OutlinedTextField(fecha, { fecha = it }, label = { Text(stringResource(R.string.lastfm_fecha)) }, singleLine = true)
                if (todo || fecha.isNotBlank()) BotonRiso(stringResource(R.string.lastfm_importar), { vm.importarLastfm(if (todo) "" else fecha) })
            }
        } else {
            val a = avance!!
            if (a.paginas > 0) {
                LinearProgressIndicator({ a.pagina.toFloat() / a.paginas }, Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
                Text(stringResource(R.string.lastfm_avance, a.pagina, a.paginas, a.escuchas), color = f.texto)
            } else Text(stringResource(R.string.lastfm_espera), color = f.texto)
            BotonSecundario(stringResource(R.string.lastfm_cancelar), vm::cancelarLastfm)
        }
        BotonSecundario(stringResource(R.string.lastfm_credito), { uri.openUri("https://www.last.fm") })
    }
}
