package io.github.fogonapps.social.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.ClienteSocial
import io.github.fogonapps.social.ErrorSocial
import kotlinx.coroutines.launch

@Composable
internal fun AportesEquipo(vm: InterfazSocial) {
    val ocupada by vm.red.equipoOcupado.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var usuario by rememberSaveable { mutableStateOf("") }
    var pesos by rememberSaveable { mutableStateOf("") }
    var referencia by rememberSaveable { mutableStateOf("") }
    var nota by rememberSaveable { mutableStateOf("") }
    var referenciaAnular by rememberSaveable { mutableStateOf("") }
    var resultado by remember { mutableStateOf<Int?>(null) }

    fun confirmar(accion: Int, pregunta: String, exito: Int, noExiste: Int, operar: suspend (ClienteSocial) -> Unit) {
        vm.confirmar(vm.tx(accion), pregunta) {
            scope.launch {
                resultado = vm.red.operarEquipo { cliente ->
                    try {
                        operar(cliente)
                        exito
                    } catch (error: ErrorSocial) {
                        when (error.error) {
                            "aporte_duplicado" -> R.string.aporte_duplicado
                            "no_existe" -> noExiste
                            "solo_principal" -> R.string.aporte_solo_principal
                            "mfa_requerida" -> R.string.aporte_mfa
                            "elevacion_requerida" -> R.string.aporte_elevacion
                            else -> throw error
                        }
                    }
                }
            }
        }
    }

    val monto = pesos.trim().toIntOrNull()?.takeIf { it > 0 }
    val contexto = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Titulo(stringResource(R.string.aporte_registrar))
        OutlinedTextField(usuario, { usuario = it; resultado = null }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.social_equipo_usuario)) }, shape = forma(18.dp))
        OutlinedTextField(pesos, { pesos = it; resultado = null }, Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stringResource(R.string.aporte_monto)) }, shape = forma(18.dp))
        OutlinedTextField(referencia, { referencia = it; resultado = null }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.aporte_referencia)) }, shape = forma(18.dp))
        OutlinedTextField(nota, { nota = it; resultado = null }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.aporte_nota)) }, shape = forma(18.dp))
        BotonRiso(stringResource(R.string.aporte_registrar), {
            val destinatario = usuario.trim().removePrefix("@")
            val valor = monto ?: return@BotonRiso
            val ref = referencia.trim()
            val comentario = nota.trim().takeIf { it.isNotEmpty() }
            confirmar(R.string.aporte_registrar, contexto.getString(R.string.aporte_confirmar_registro, valor, destinatario, ref),
                R.string.aporte_registrado, R.string.aporte_usuario_inexistente) {
                it.registrarAporte(destinatario, valor, ref, comentario)
            }
        }, habilitado = !ocupada && usuario.trim().removePrefix("@").isNotBlank() && monto != null && referencia.isNotBlank())

        Titulo(stringResource(R.string.aporte_anular))
        OutlinedTextField(referenciaAnular, { referenciaAnular = it; resultado = null }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.aporte_referencia)) }, shape = forma(18.dp))
        BotonSecundario(stringResource(R.string.aporte_anular), {
            val ref = referenciaAnular.trim()
            confirmar(R.string.aporte_anular, contexto.getString(R.string.aporte_confirmar_anulacion, ref),
                R.string.aporte_anulado, R.string.aporte_inexistente) {
                it.anularAporte(ref)
            }
        }, habilitado = !ocupada && referenciaAnular.isNotBlank(), peligro = true)
        resultado?.let { Text(stringResource(it), color = FogonTema.actual.texto) }
    }
}
