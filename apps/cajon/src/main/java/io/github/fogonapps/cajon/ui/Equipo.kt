package io.github.fogonapps.cajon.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.identidad.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Secretos solo en memoria de composición: no se guardan al recrear la actividad. */
@Composable
fun SeguridadEquipo(vm: CajonViewModel, elevar: Boolean = false) {
    PantallaSegura()
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val ocupada by vm.red.equipoOcupado.collectAsStateWithLifecycle()
    val c = cuenta ?: return
    if (c.rol.isBlank() && c.permisos.isEmpty()) return
    val scope = rememberCoroutineScope()
    val contexto = LocalContext.current
    var inscripcion by remember(c.usuario) { mutableStateOf<InscripcionMfa?>(null) }
    var codigos by remember(c.usuario) { mutableStateOf<List<String>>(emptyList()) }
    var codigo by remember(c.usuario) { mutableStateOf("") }
    var recuperacion by remember { mutableStateOf(false) }
    var sinAplicacion by remember { mutableStateOf(false) }
    fun copiar(texto: String) {
        (contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", texto))
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.equipo_mfa), style = MaterialTheme.typography.titleLarge, color = FogonTema.actual.texto)
        if (codigos.isNotEmpty()) {
            Pista(stringResource(R.string.equipo_guardar_codigos))
            Text(codigos.joinToString("\n"), color = FogonTema.actual.texto)
            BotonSecundario(stringResource(R.string.equipo_copiar), { copiar(codigos.joinToString("\n")) })
            BotonRiso(stringResource(R.string.equipo_guardados), { codigos = emptyList() })
            return@Column
        }
        val i = inscripcion
        if (!c.mfa) {
            Pista(stringResource(R.string.equipo_mfa_explica))
            if (i == null) BotonRiso(stringResource(R.string.equipo_activar), {
                scope.launch { inscripcion = vm.red.operarEquipo { it.activarMfa() } }
            }, habilitado = !ocupada)
            else {
                Text(i.secreto, color = FogonTema.actual.texto)
                BotonSecundario(stringResource(R.string.equipo_copiar), { copiar(i.secreto) })
                BotonSecundario(stringResource(R.string.equipo_abrir_codigos), {
                    sinAplicacion = runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(i.uri))) }.isFailure
                })
                if (sinAplicacion) Pista(stringResource(R.string.equipo_sin_aplicacion))
                CampoEquipo(codigo, { codigo = it }, R.string.equipo_codigo)
                BotonRiso(stringResource(R.string.equipo_confirmar), {
                    scope.launch {
                        vm.red.operarEquipo { cliente ->
                            val respuesta = cliente.confirmarMfa(codigo)
                            codigo = ""; codigos = respuesta.codigos; inscripcion = null
                            vm.red.actualizarEquipo()
                        }
                    }
                }, habilitado = !ocupada && codigo.length == 6)
            }
        } else if (elevar) {
            Pista(stringResource(R.string.equipo_elevar_explica))
            CampoEquipo(codigo, { codigo = it }, if (recuperacion) R.string.equipo_recuperacion else R.string.equipo_codigo)
            OpcionEquipo(stringResource(R.string.equipo_usar_recuperacion), recuperacion) { recuperacion = it; codigo = "" }
            BotonRiso(stringResource(R.string.equipo_elevar), {
                scope.launch { vm.red.operarEquipo {
                    it.elevarMfa(if (recuperacion) FactorMfa(recuperacion = codigo.trim()) else FactorMfa(codigo.trim()))
                    codigo = ""; vm.red.actualizarEquipo()
                } }
            }, habilitado = !ocupada && codigo.isNotBlank())
        } else {
            Pista(stringResource(R.string.equipo_mfa_activa))
            CampoEquipo(codigo, { codigo = it }, R.string.equipo_codigo)
            BotonSecundario(stringResource(R.string.equipo_nuevos_codigos), {
                scope.launch { vm.red.operarEquipo { codigos = it.renovarMfa(codigo).codigos; codigo = "" } }
            }, habilitado = !ocupada && codigo.length == 6)
            val confirmar = LocalConfirmar.current
            BotonSecundario(stringResource(R.string.equipo_desactivar), {
                confirmar(vm.tx(R.string.equipo_desactivar), vm.tx(R.string.equipo_desactivar_aviso)) {
                    scope.launch { vm.red.operarEquipo { it.desactivarMfa(codigo); codigo = ""; vm.red.actualizarEquipo() } }
                }
            }, habilitado = !ocupada && codigo.length == 6, peligro = true)
        }
    }
}

@Composable
private fun CampoEquipo(valor: String, cambiar: (String) -> Unit, etiqueta: Int) {
    OutlinedTextField(valor, { cambiar(it.take(120)) }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(etiqueta)) }, shape = forma(18.dp))
}

private val permisosEquipo = listOf("ver_reportes", "sancionar", "sancion_grave", "cerrar_cuentas", "moderar_comentarios", "gestionar_publicaciones", "gestionar_frases", "gestionar_usuarios", "asignar_equipo", "ver_registro")
private val nombresPermisos = listOf(R.string.equipo_p_reportes, R.string.equipo_p_sancionar, R.string.equipo_p_grave, R.string.equipo_p_cerrar, R.string.equipo_p_comentarios, R.string.equipo_p_publicaciones, R.string.equipo_p_frases, R.string.equipo_p_usuarios, R.string.equipo_p_equipo, R.string.equipo_p_registro)
@Composable
private fun nombrePermiso(permiso: String): String = permisosEquipo.indexOf(permiso).takeIf { it >= 0 }?.let { stringResource(nombresPermisos[it]) } ?: permiso
@Composable
private fun nombreRol(rol: String) = stringResource(when (rol) {
    "principal" -> R.string.equipo_rol_principal
    "administrador" -> R.string.equipo_rol_admin
    "moderador" -> R.string.equipo_rol_moderador
    else -> R.string.equipo_rol_equipo
})

@Composable
fun Equipo(vm: CajonViewModel) {
    val scope = rememberCoroutineScope()
    val ocupada by vm.red.equipoOcupado.collectAsStateWithLifecycle()
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    var miembros by remember { mutableStateOf<List<MiembroEquipo>?>(null) }
    var usuario by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf("equipo") }
    var permiso by remember { mutableStateOf("") }
    var quitar by remember { mutableStateOf(false) }
    val confirmar = LocalConfirmar.current
    LaunchedEffect(Unit) { miembros = vm.red.operarEquipo { it.equipo() } }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        miembros?.forEach { m ->
            Column(Modifier.fillMaxWidth().tarjetaFogon().padding(12.dp)) {
                Text("@${m.usuario} · ${nombreRol(m.rol)}", color = FogonTema.actual.texto)
                Text(m.permisos.map { nombrePermiso(it) }.joinToString(), color = FogonTema.actual.textoSuave)
            }
        }
        CampoEquipo(usuario, { usuario = it.removePrefix("@").trim() }, R.string.equipo_usuario)
        Pista(stringResource(R.string.equipo_asignacion_aviso))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("moderador", "equipo").forEach { r -> Pildora(nombreRol(r), acento = rol == r && permiso.isEmpty()) { rol = r; permiso = "" } }
        }
        permisosEquipo.filter { it in cuenta?.permisos.orEmpty() }.forEach { p ->
            Pildora(nombrePermiso(p), acento = permiso == p) { permiso = p; rol = "" }
        }
        OpcionEquipo(stringResource(R.string.equipo_quitar), quitar) { quitar = it }
        CampoEquipo(codigo, { codigo = it }, R.string.equipo_codigo)
        BotonRiso(stringResource(R.string.equipo_confirmar), {
            confirmar(vm.tx(R.string.equipo_confirmar), vm.tx(R.string.equipo_cambio_confirmar)) {
                scope.launch { vm.red.operarEquipo { cliente ->
                    cliente.cambiarEquipo(CambioEquipo(usuario, rol, permiso, codigo), quitar)
                    codigo = ""; miembros = cliente.equipo()
                } }
            }
        }, habilitado = !ocupada && usuario.isNotBlank() && codigo.length == 6)
    }
}

@Composable
fun RegistroEquipoPantalla(vm: CajonViewModel) {
    val scope = rememberCoroutineScope()
    val ocupada by vm.red.equipoOcupado.collectAsStateWithLifecycle()
    var actor by remember { mutableStateOf("") }
    var accion by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf("" to "") }
    var entradas by remember { mutableStateOf<List<EntradaRegistro>>(emptyList()) }
    var integra by remember { mutableStateOf(true) }
    var mas by remember { mutableStateOf(true) }
    var siguiente by remember { mutableStateOf<Long?>(null) }
    suspend fun cargar(reiniciar: Boolean) {
        val filtros = if (reiniciar) actor.trim() to accion.trim() else filtro
        val lote = vm.red.operarEquipo { it.registro(filtros.first, filtros.second, if (reiniciar) 0 else siguiente ?: 0) } ?: return
        filtro = filtros
        entradas = if (reiniciar) lote.entradas else entradas + lote.entradas
        integra = (reiniciar || integra) && lote.cadenaIntegra
        siguiente = lote.siguiente
        mas = siguiente != null
    }
    LaunchedEffect(Unit) { cargar(true) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Pista(stringResource(R.string.registro_filtros))
        CampoEquipo(actor, { actor = it }, R.string.registro_actor)
        CampoEquipo(accion, { accion = it }, R.string.registro_accion)
        BotonSecundario(stringResource(R.string.registro_filtrar), { scope.launch { cargar(true) } }, habilitado = !ocupada)
        if (!integra) Text(stringResource(R.string.registro_roto), color = MaterialTheme.colorScheme.error)
        entradas.forEach { e ->
            Column(Modifier.fillMaxWidth().tarjetaFogon().padding(12.dp)) {
                Text("${e.actor} · ${e.accion}", color = FogonTema.actual.texto)
                Text(e.objeto, color = FogonTema.actual.texto)
                Text(e.detalle, color = FogonTema.actual.textoSuave)
                Text(DateFormat.getDateTimeInstance().format(Date(e.cuando)), color = FogonTema.actual.textoSuave)
            }
        }
        if (mas) BotonSecundario(stringResource(R.string.registro_mas), { scope.launch { cargar(false) } }, habilitado = !ocupada)
    }
}

@Composable
private fun OpcionEquipo(texto: String, valor: Boolean, cambiar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Switch(valor, cambiar)
        Text(texto, color = FogonTema.actual.texto)
    }
}

/** Mientras se ve el secreto TOTP o los códigos de recuperación, la ventana no sale en capturas ni en recientes. */
@Composable
private fun PantallaSegura() {
    val contexto = LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val ventana = (contexto as? android.app.Activity)?.window
        val antes = ventana?.attributes?.flags?.and(android.view.WindowManager.LayoutParams.FLAG_SECURE) ?: 0
        ventana?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (antes == 0) ventana?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
