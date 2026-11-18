package io.github.fogonapps.social.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.perfil.menosMovimientoBanner
import io.github.fogonapps.perfil.visibilidadBanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

private const val MAX_PORTADA_ANIMADA = 3_000_000

@Composable
internal fun ElegirPortadaAnimada(vm: InterfazSocial) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var pendiente by remember { mutableStateOf<ByteArray?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) alcance.launch {
            ocupado = true
            error = false
            try {
                pendiente = withContext(Dispatchers.IO) {
                    contexto.contentResolver.openInputStream(uri)?.use { entrada ->
                        val salida = java.io.ByteArrayOutputStream()
                        val bloque = ByteArray(8192)
                        while (true) {
                            val leidos = entrada.read(bloque)
                            if (leidos < 0) break
                            check(salida.size() + leidos <= MAX_PORTADA_ANIMADA)
                            salida.write(bloque, 0, leidos)
                        }
                        salida.toByteArray().also { if (Build.VERSION.SDK_INT >= 28) validarAnimada(it) }
                    } ?: error("imagen")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = true
            } finally {
                ocupado = false
            }
        }
    }
    BotonSecundario(stringResource(R.string.social_portada_elegir_animada),
        { selector.launch(arrayOf("image/gif", "image/webp")) }, habilitado = !ocupado)
    if (error) Text(stringResource(R.string.social_portada_animada_error))
    pendiente?.let { bytes ->
        AlertDialog(
            onDismissRequest = { if (!ocupado) pendiente = null },
            title = { Text(stringResource(R.string.social_portada_elegir_animada)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    DibujarPortadaSubida(bytes, Modifier.fillMaxWidth().aspectRatio(3.4f))
                    if (error) Text(stringResource(R.string.social_portada_animada_error))
                }
            },
            confirmButton = {
                TextButton(enabled = !ocupado, onClick = {
                    ocupado = true
                    error = false
                    alcance.launch {
                        try {
                            vm.red.equiparPortadaAnimada(bytes)
                            pendiente = null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            error = true
                        } finally {
                            ocupado = false
                        }
                    }
                }) { Text(stringResource(R.string.social_temas_usar)) }
            },
            dismissButton = {
                TextButton(enabled = !ocupado, onClick = { pendiente = null }) {
                    Text(stringResource(R.string.social_cancelar))
                }
            },
        )
    }
}

@RequiresApi(28)
private fun validarAnimada(bytes: ByteArray) {
    ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { _, info, _ ->
        check(info.isAnimated && info.size.width in 1..2048 && info.size.height in 1..2048)
    }
}

@Composable
internal fun PortadaSubida(nombre: String, modifier: Modifier, vm: InterfazSocial) {
    val cuenta by vm.red.usuario.collectAsState()
    val bytes by produceState<ByteArray?>(null, nombre, cuenta) {
        value = null
        try {
            value = vm.red.imagenPortada(nombre)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Las portadas retiradas o privadas dejan el fondo del perfil a la vista.
        }
    }
    bytes?.let { DibujarPortadaSubida(it, modifier) }
        ?: androidx.compose.foundation.layout.Box(modifier)
}

@Composable
private fun DibujarPortadaSubida(bytes: ByteArray, modifier: Modifier) {
    val quieta = menosMovimientoBanner()
    if (Build.VERSION.SDK_INT >= 28) DibujarPortadaAndroid(bytes, quieta, modifier)
    else {
        val mapa by produceState<Bitmap?>(null, bytes) {
            value = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        }
        mapa?.let { Image(it.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop) }
    }
}

@RequiresApi(28)
@Composable
private fun DibujarPortadaAndroid(bytes: ByteArray, quieta: Boolean, modifier: Modifier) {
    val contexto = LocalContext.current
    val dibujo by produceState<Drawable?>(null, bytes, quieta) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val fuente = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                if (quieta) android.graphics.drawable.BitmapDrawable(contexto.resources, ImageDecoder.decodeBitmap(fuente))
                else ImageDecoder.decodeDrawable(fuente)
            }.getOrNull()
        }
    }
    var visible by remember { mutableStateOf(false) }
    val ciclo by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        modifier = modifier.visibilidadBanner { visible = it },
        update = { if (it.drawable !== dibujo) it.setImageDrawable(dibujo) },
    )
    val animacion = dibujo as? AnimatedImageDrawable
    val reproducir = !quieta && visible && ciclo.isAtLeast(Lifecycle.State.RESUMED)
    DisposableEffect(animacion, reproducir) {
        if (reproducir) {
            animacion?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            animacion?.start()
        }
        onDispose { animacion?.stop() }
    }
}
