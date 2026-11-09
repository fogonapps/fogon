package io.github.fogonapps.ahorrador.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.BancoAvisos
import io.github.fogonapps.ahorrador.EscuchaBanco
import io.github.fogonapps.ahorrador.Pantalla
import io.github.fogonapps.ahorrador.nombreBanco
import io.github.fogonapps.ahorrador.datos.*
import io.github.fogonapps.identidad.SeccionAjustes
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.tarjetaFogon

@Composable
fun Bancos(vm: AhorradorViewModel, e: Estado) {
    val c = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    var activo by remember { mutableStateOf(BancoAvisos.desde(c) != 0L) }
    var permiso by remember { mutableStateOf(BancoAvisos.conPermiso(c)) }
    val preferencias = remember(c) { c.getSharedPreferences("banco", android.content.Context.MODE_PRIVATE) }
    var revision by remember { mutableIntStateOf(0) }
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(preferencias) {
        val escucha = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        preferencias.registerOnSharedPreferenceChangeListener(escucha)
        onDispose { preferencias.unregisterOnSharedPreferenceChangeListener(escucha) }
    }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); ahora = System.currentTimeMillis() } }
    val avisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento -> if (evento == Lifecycle.Event.ON_RESUME) permiso = BancoAvisos.conPermiso(c) }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }
    Cabecera(stringResource(R.string.banco_titulo), vm::cerrar)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.banco_explicacion), color = FogonTema.actual.texto, style = MaterialTheme.typography.bodyMedium)
        io.github.fogonapps.identidad.Interruptor(stringResource(R.string.banco_activar), stringResource(R.string.banco_local), activo) {
            activo = it; BancoAvisos.activar(c, it)
            BancoAvisos.actualizar(c, e)
        }
        Text(stringResource(if (permiso) R.string.banco_permiso_si else R.string.banco_permiso_no), color = FogonTema.actual.textoSuave)
        BotonSecundario(stringResource(R.string.banco_permiso), {
            c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", ComponentName(c, EscuchaBanco::class.java).flattenToString()))
        }, Modifier.fillMaxWidth())
        if (Build.VERSION.SDK_INT >= 33) BotonSecundario(stringResource(R.string.banco_permitir_avisos), {
            avisos.launch(Manifest.permission.POST_NOTIFICATIONS)
        }, Modifier.fillMaxWidth())
        if (!permiso && Build.VERSION.SDK_INT >= 33) Text(stringResource(R.string.banco_restringidos), color = FogonTema.actual.textoSuave)
    }
    SeccionAjustes(stringResource(R.string.banco_estado))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val conectado = remember(revision, permiso) { permiso && BancoAvisos.conectado }
        Text(stringResource(if (conectado) R.string.banco_conectado else R.string.banco_desconectado), color = FogonTema.actual.texto)
        val conexion = remember(revision) { preferencias.getLong("conexion", 0) }
        if (conexion > 0) Text(stringResource(R.string.banco_cambio_conexion,
            android.text.format.DateUtils.getRelativeTimeSpanString(conexion, ahora, android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()), color = FogonTema.actual.textoSuave)
        if (!conectado && permiso) BotonSecundario(stringResource(R.string.banco_reconectar), {
            android.service.notification.NotificationListenerService.requestRebind(ComponentName(c, EscuchaBanco::class.java))
        }, Modifier.fillMaxWidth())
        listOf(Banco.BANCOLOMBIA, Banco.NU).forEach { banco ->
            val ultima = remember(revision, banco) { preferencias.getLong("ultima:$banco", 0) }
            val vistas = remember(revision, banco) { preferencias.getLong("vistas:$banco", 0) }
            val entendidas = remember(revision, banco) { preferencias.getLong("entendidas:$banco", 0) }
            Text(stringResource(banco.nombreBanco), color = FogonTema.actual.texto)
            Text(stringResource(R.string.banco_contadores, vistas, entendidas,
                if (ultima == 0L) stringResource(R.string.banco_nunca) else android.text.format.DateUtils.getRelativeTimeSpanString(ultima, ahora, android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()), color = FogonTema.actual.textoSuave)
        }
    }
    SeccionAjustes(stringResource(R.string.banco_elegir))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Banco.BANCOLOMBIA, Banco.NU).forEach { banco ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                io.github.fogonapps.identidad.Interruptor(stringResource(banco.nombreBanco), stringResource(if (banco == Banco.BANCOLOMBIA) R.string.banco_sms else R.string.banco_app), banco in e.notificaciones.elegidos) { vm.elegirBanco(banco, it) }
                if (banco in e.notificaciones.elegidos) io.github.fogonapps.identidad.Interruptor(stringResource(R.string.banco_solo), stringResource(R.string.banco_solo_pista), banco in e.notificaciones.solos) { vm.bancoSolo(banco, it) }
            }
        }
        Pista(stringResource(R.string.banco_futuros), Modifier)
    }
    SeccionAjustes(stringResource(R.string.banco_enlaces))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        e.notificaciones.enlaces.forEach { (clave, id) ->
            val cuenta = e.cuentas.firstOrNull { it.id == id }
            Text(stringResource(R.string.banco_enlace, clave.substringBefore('|'), clave.substringAfter('|'), cuenta?.nombre.orEmpty()), color = FogonTema.actual.texto)
            BotonSecundario(stringResource(R.string.banco_desvincular), { vm.olvidarEnlace(clave) }, Modifier.fillMaxWidth())
        }
    }
    SeccionAjustes(stringResource(R.string.banco_pendientes))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val pendientes = e.notificaciones.registros.filter { !it.descartado && it.movimiento == null }
        if (pendientes.isEmpty()) Pista(stringResource(R.string.banco_sin_pendientes), Modifier)
        pendientes.forEach { r ->
            val a = r.aviso
            val propuesta = e.propuestaBanco(a.huella)
            Column(Modifier.fillMaxWidth().tarjetaFogon().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.banco_aviso, stringResource((propuesta?.tipo ?: a.tipo).nombre), Formato.pesos(a.monto), stringResource(a.banco.nombreBanco)), color = FogonTema.actual.texto)
                Text(stringResource(R.string.banco_detalle, a.momento.replace('T', ' '), a.cuenta, a.texto), color = FogonTema.actual.textoSuave)
                if (propuesta == null) {
                    Text(stringResource(R.string.banco_enlazar), color = FogonTema.actual.texto)
                    cuentasPosibles(e, a).forEach { cuenta ->
                        BotonSecundario(stringResource(R.string.banco_usar_cuenta, cuenta.nombre), { vm.enlazarAviso(a.huella, cuenta.id) }, Modifier.fillMaxWidth())
                    }
                    BotonRiso(stringResource(R.string.banco_crear, stringResource(a.banco.nombreBanco)), { vm.enlazarAviso(a.huella, null) }, Modifier.fillMaxWidth())
                } else {
                    if (propuesta.tipo == TipoMovimiento.TRASPASO) {
                        val origen = e.cuentas.first { it.id == propuesta.cuenta }.nombre
                        val destino = e.cuentas.first { it.id == propuesta.aCuenta }.nombre
                        Text(stringResource(R.string.banco_traspaso, origen, destino), color = FogonTema.actual.texto)
                    }
                    Text(e.categorias.firstOrNull { it.id == propuesta.categoria }?.nombre ?: stringResource(R.string.banco_por_revisar), color = FogonTema.actual.textoSuave)
                    BotonRiso(stringResource(R.string.anotar), { vm.confirmarAviso(a.huella) }, Modifier.fillMaxWidth())
                    BotonSecundario(stringResource(R.string.editar), { vm.abrir(Pantalla.Anotar(banco = a.huella)) }, Modifier.fillMaxWidth())
                }
                BotonSecundario(stringResource(R.string.banco_no), { vm.descartarAviso(a.huella) }, Modifier.fillMaxWidth())
            }
        }
    }
    SeccionAjustes(stringResource(R.string.banco_anotado_solo))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val solos = e.notificaciones.registros.filter { it.automatico && it.movimiento != null }.mapNotNull { it.movimiento }.toSet()
        if (solos.isEmpty()) Pista(stringResource(R.string.banco_sin_solos), Modifier)
        e.movimientos.filter { it.id in solos }.asReversed().forEach { m ->
            Column(Modifier.fillMaxWidth().tarjetaFogon().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.banco_resumen, stringResource(m.tipo.nombre), Formato.pesos(m.monto), m.fecha, m.nota), color = FogonTema.actual.texto)
                BotonSecundario(stringResource(R.string.editar), { vm.abrir(Pantalla.Anotar(m.tipo, m.id)) }, Modifier.fillMaxWidth())
                BotonSecundario(stringResource(R.string.banco_deshacer), { vm.deshacerBanco(m.id) }, Modifier.fillMaxWidth())
            }
        }
    }
    Espacio(40.dp)
}
