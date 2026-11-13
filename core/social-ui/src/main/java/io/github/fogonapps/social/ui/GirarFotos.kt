package io.github.fogonapps.social.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.fogonapps.identidad.*
import io.github.fogonapps.identidad.PildoraSocial as Pildora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Girar fotos antes de usarlas: al elegir una o varias fotos (para publicar, para el perfil, para un mensaje…), se ven
 * y cada una se gira de a 90° con un toque. Lo girado se guarda como una copia en la caché privada de la app; lo que
 * venía después (achicar, subir) sigue igual con esa copia.
 */

/** Lee la foto ya derecha según la cámara (lado mayor de hasta [lado] px). */
private fun leerFoto(ctx: Context, uri: Uri, lado: Int): Bitmap? = runCatching {
    if (android.os.Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
            val escala = minOf(1.0, lado.toDouble() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(maxOf(1, (info.size.width * escala).toInt()), maxOf(1, (info.size.height * escala).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val medidas = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, medidas) }
        var muestra = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= lado) muestra *= 2
        val mapa = ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = muestra }) }
        val giro = runCatching { ctx.contentResolver.openInputStream(uri)?.use { when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90; android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0 } } }.getOrNull() ?: 0
        if (mapa != null && giro != 0) Bitmap.createBitmap(mapa, 0, 0, mapa.width, mapa.height, android.graphics.Matrix().apply { postRotate(giro.toFloat()) }, true) else mapa
    }
}.getOrNull()

/** Una copia de la foto girada [grados] (múltiplo de 90) en la caché privada; la misma [uri] si no se gira. */
suspend fun fotoGirada(ctx: Context, uri: Uri, grados: Int): Uri = withContext(Dispatchers.IO) {
    val g = ((grados % 360) + 360) % 360
    if (g == 0) return@withContext uri
    val foto = leerFoto(ctx, uri, 2560) ?: return@withContext uri
    val girada = Bitmap.createBitmap(foto, 0, 0, foto.width, foto.height, android.graphics.Matrix().apply { postRotate(g.toFloat()) }, true)
    val carpeta = java.io.File(ctx.cacheDir, "fotos-giradas").apply { mkdirs() }
    // Las copias de antes de hoy ya no se usan.
    carpeta.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
    val archivo = java.io.File(carpeta, "foto-${System.nanoTime()}.jpg")
    runCatching { archivo.outputStream().use { girada.compress(Bitmap.CompressFormat.JPEG, 92, it) } }.getOrNull() ?: return@withContext uri
    Uri.fromFile(archivo)
}

/**
 * Pone el paso de girar entre elegir fotos y usarlas: devuelve la función que hay que llamar con lo elegido; al tocar
 * «Listo» llega a [onListo] (con las copias giradas donde hizo falta).
 */
@Composable
fun conGiro(onListo: (List<Uri>) -> Unit): (List<Uri>) -> Unit {
    var pendientes by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val listo by rememberUpdatedState(onListo)
    if (pendientes.isNotEmpty()) GirarFotos(pendientes, { pendientes = emptyList(); listo(it) }) { pendientes = emptyList() }
    return remember { { uris: List<Uri> -> pendientes = uris } }
}

/** La pantalla de girar: cada foto con su botón «Girar»; «Listo» las devuelve (giradas) y «Cancelar» no usa ninguna. */
@Composable
fun GirarFotos(uris: List<Uri>, onListo: (List<Uri>) -> Unit, onCancelar: () -> Unit) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    val giros = remember(uris) { mutableStateListOf(*Array(uris.size) { 0 }) }
    var guardando by remember { mutableStateOf(false) }
    Dialog(onCancelar, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(f.fondo).systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.social_fotos_girar_titulo), style = MaterialTheme.typography.titleMedium, color = f.texto)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                uris.forEachIndexed { i, uri ->
                    var miniatura by remember(uri) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(uri) { miniatura = withContext(Dispatchers.IO) { leerFoto(ctx, uri, 900) } }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(forma(18.dp)).background(f.superficie), contentAlignment = Alignment.Center) {
                            val m = miniatura
                            if (m == null) CircularProgressIndicator(color = f.tinta1)
                            else Image(m.asImageBitmap(), stringResource(R.string.social_fotos_foto_n, i + 1), Modifier.fillMaxSize().graphicsLayer { rotationZ = giros[i].toFloat() },
                                contentScale = ContentScale.Fit)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pildora(stringResource(R.string.social_fotos_girar_izquierda)) { giros[i] = (giros[i] + 270) % 360 }
                            Pildora(stringResource(R.string.social_fotos_girar_derecha)) { giros[i] = (giros[i] + 90) % 360 }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario(stringResource(R.string.social_cancelar), onCancelar, Modifier.weight(1f))
                BotonRiso(stringResource(R.string.social_fotos_listo), {
                    guardando = true
                    alcance.launch { onListo(uris.mapIndexed { i, u -> fotoGirada(ctx, u, giros[i]) }) }
                }, Modifier.weight(1f), habilitado = !guardando)
            }
        }
    }
}
