package io.github.fogonapps.social.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.social.prepararImagen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Selección sin permisos y encuadre en la misma proporción que la portada. */
@Composable
internal fun PortadaPropia(vm: InterfazSocial) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var mapa by remember { mutableStateOf<Bitmap?>(null) }
    var x by remember { mutableFloatStateOf(0.5f) }
    var y by remember { mutableFloatStateOf(0.5f) }
    var anchoVista by remember { mutableIntStateOf(1) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) alcance.launch {
            ocupado = true; error = false
            try {
                mapa = withContext(Dispatchers.IO) {
                    val bytes = prepararImagen(contexto, uri) ?: error("imagen")
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("imagen")
                }
                x = 0.5f; y = 0.5f
            } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
            finally { ocupado = false }
        }
    }
    BotonSecundario(stringResource(R.string.social_portada_usar_foto), {
        selector.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }, habilitado = !ocupado)
    if (error) Text(stringResource(R.string.social_portada_error))
    mapa?.let { original ->
        val recorte = remember(original, x, y) { recortarPortada(original, x, y) }
        AlertDialog(onDismissRequest = { if (!ocupado) mapa = null },
            title = { Text(stringResource(R.string.social_portada_encuadrar)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(recorte.asImageBitmap(), stringResource(R.string.social_portada_arrastrar), Modifier.fillMaxWidth().aspectRatio(3.4f)
                        .onSizeChanged { anchoVista = it.width.coerceAtLeast(1) }
                        .pointerInput(original) {
                            detectDragGestures { cambio, delta ->
                                cambio.consume()
                                val escala = maxOf(anchoVista.toFloat() / original.width, anchoVista / 3.4f / original.height)
                                val sobraX = original.width - anchoVista / escala
                                val sobraY = original.height - anchoVista / 3.4f / escala
                                if (sobraX > 0) x = (x - delta.x / escala / sobraX).coerceIn(0f, 1f)
                                if (sobraY > 0) y = (y - delta.y / escala / sobraY).coerceIn(0f, 1f)
                            }
                        })
                    Text(stringResource(R.string.social_portada_arrastrar))
                    Slider(x, { x = it }, enabled = !ocupado && original.width.toFloat() / original.height > 3.4f)
                    Slider(y, { y = it }, enabled = !ocupado && original.width.toFloat() / original.height < 3.4f)
                    if (error) Text(stringResource(R.string.social_portada_error))
                    if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(enabled = !ocupado, onClick = {
                ocupado = true; error = false
                alcance.launch {
                    try {
                        val jpeg = withContext(Dispatchers.Default) {
                            var calidad = 88
                            var bytes: ByteArray
                            do {
                                bytes = java.io.ByteArrayOutputStream().also { recorte.compress(Bitmap.CompressFormat.JPEG, calidad, it) }.toByteArray()
                                calidad -= 12
                            } while (bytes.size > 900_000 && calidad > 30)
                            check(bytes.size <= 900_000)
                            bytes
                        }
                        vm.red.equiparPortadaPropia(jpeg)
                        mapa = null
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                    finally { ocupado = false }
                }
            }) { Text(stringResource(R.string.social_temas_usar)) } },
            dismissButton = { TextButton(enabled = !ocupado, onClick = { mapa = null }) { Text(stringResource(R.string.social_cancelar)) } })
    }
}

internal fun recortarPortada(mapa: Bitmap, x: Float, y: Float): Bitmap {
    val ancho = minOf(mapa.width, (mapa.height * 3.4f).toInt()).coerceAtLeast(1)
    val alto = minOf(mapa.height, (ancho / 3.4f).toInt()).coerceAtLeast(1)
    return Bitmap.createBitmap(mapa, ((mapa.width - ancho) * x.coerceIn(0f, 1f)).toInt(),
        ((mapa.height - alto) * y.coerceIn(0f, 1f)).toInt(), ancho, alto)
}
