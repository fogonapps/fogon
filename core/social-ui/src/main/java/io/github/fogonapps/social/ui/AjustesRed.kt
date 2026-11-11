package io.github.fogonapps.social.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.ClienteSocial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.Normalizer

/*
 * Lo de la red que El Cajón tiene en sus Ajustes, igual en las otras apps (D-028): notificaciones, cuentas bloqueadas,
 * palabras ocultas, y la política y los términos en Privacidad.
 */

/** Las palabras que la persona esconde del feed y los comentarios (solo en este teléfono). */
class PalabrasOcultasRed private constructor(c: Context) {
    private val prefs = c.getSharedPreferences("palabras_ocultas", Context.MODE_PRIVATE)
    private val _lista = MutableStateFlow(prefs.getString("lista", "").orEmpty().split('\n').filter { it.isNotBlank() })
    val lista: StateFlow<List<String>> = _lista
    fun poner(palabra: String, si: Boolean) {
        val limpia = palabra.trim().take(40)
        if (limpia.isEmpty()) return
        val nueva = if (si) (_lista.value + limpia).distinctBy { limpiar(it) }.take(200) else _lista.value.filter { it != limpia }
        prefs.edit().putString("lista", nueva.joinToString("\n")).apply()
        _lista.value = nueva
    }
    /** Para la copia de la cuenta: reemplaza la lista con la que llegó. */
    fun reemplazar(lista: List<String>) {
        val nueva = lista.map { it.trim().take(40) }.filter { it.isNotEmpty() }.distinct().take(200)
        prefs.edit().putString("lista", nueva.joinToString("\n")).apply()
        _lista.value = nueva
    }
    companion object {
        @Volatile private var una: PalabrasOcultasRed? = null
        fun de(c: Context) = una ?: synchronized(this) { una ?: PalabrasOcultasRed(c.applicationContext).also { una = it } }
        private fun limpiar(t: String) = Normalizer.normalize(t.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()
        /** Si [texto] lleva alguna de [palabras] (sin mayúsculas ni tildes). */
        fun lleva(palabras: List<String>, texto: String): Boolean {
            if (palabras.isEmpty()) return false
            val t = limpiar(texto)
            return palabras.any { p -> limpiar(p).let { it.isNotBlank() && it in t } }
        }
        /** El texto de una publicación donde se buscan las palabras. */
        fun textoDe(p: io.github.fogonapps.social.PublicacionSocial): String =
            listOf("texto", "titulo", "nombre", "plato", "meta").joinToString(" ") { (p.datos[it] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty() }
    }
}

/** Las palabras ocultas de este teléfono, para el contrato [InterfazSocial.palabrasOcultas]. */
@Composable
fun palabrasOcultasRed(): List<String> = PalabrasOcultasRed.de(LocalContext.current).lista.collectAsState().value

/**
 * Las filas de la red para «Cómo usas la app»: notificaciones (con la app cerrada), cuentas bloqueadas y palabras
 * ocultas. Solo con cuenta.
 */
@Composable
fun FilasRedEnAjustes(vm: InterfazSocial) {
    val usuario by vm.red.usuario.collectAsState()
    if (usuario == null) return
    Desplegable(IconosFogon.Campana, stringResource(R.string.ajustes_red_notificaciones), null) { AvisosEnCuenta() }
    FilaAjuste(IconosSociales.Bandera, stringResource(R.string.social_red_bloqueadas), null) { vm.navegacion.ajustes(AjusteSocial.BLOQUEADOS) }
    Desplegable(IconosSociales.Bandera, stringResource(R.string.ajustes_red_palabras), stringResource(R.string.ajustes_red_palabras_pista)) { EditorPalabrasOcultas() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorPalabrasOcultas() {
    val f = FogonTema.actual
    val palabras = PalabrasOcultasRed.de(LocalContext.current)
    val lista by palabras.lista.collectAsState()
    var nueva by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PistaSocial(stringResource(R.string.ajustes_red_palabras_explica))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(nueva, { nueva = it.take(40) }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.ajustes_red_palabras_agregar)) },
                singleLine = true, shape = forma(22.dp))
            BotonIconoSocial(IconosSociales.Mas, stringResource(R.string.ajustes_red_palabras_agregar), lleno = true, activo = nueva.isNotBlank()) { palabras.poner(nueva, true); nueva = "" }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lista.forEach { p -> PildoraSocial("$p  ✕") { palabras.poner(p, false) } }
        }
        if (lista.isEmpty()) Text(stringResource(R.string.ajustes_red_palabras_vacio), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
}

/** Política y términos de Fogón Social, para la sección «Privacidad» de los Ajustes. */
@Composable
fun FilasPrivacidadRed() {
    val uri = LocalUriHandler.current
    val politica = stringResource(R.string.social_ruta_politica)
    val terminos = stringResource(R.string.social_ruta_terminos)
    FilaAjuste(IconosFogon.Candado, stringResource(R.string.social_red_politica), null) { uri.openUri(ClienteSocial.SERVIDOR + politica) }
    FilaAjuste(IconosFogon.Lista, stringResource(R.string.social_red_terminos), null) { uri.openUri(ClienteSocial.SERVIDOR + terminos) }
}

/** La copia de la cuenta (JSON de la app) con las palabras ocultas adentro, en «palabrasOcultasCopia». */
fun conPalabrasOcultas(json: String, c: Context): String = runCatching {
    org.json.JSONObject(json).put("palabrasOcultasCopia", org.json.JSONArray(PalabrasOcultasRed.de(c).lista.value)).toString()
}.getOrDefault(json)

/** Si la copia trae palabras ocultas, quedan en este teléfono. */
fun aplicarPalabrasOcultas(json: String, c: Context) {
    val arr = runCatching { org.json.JSONObject(json).optJSONArray("palabrasOcultasCopia") }.getOrNull() ?: return
    PalabrasOcultasRed.de(c).reemplazar((0 until arr.length()).map { arr.optString(it) })
}
